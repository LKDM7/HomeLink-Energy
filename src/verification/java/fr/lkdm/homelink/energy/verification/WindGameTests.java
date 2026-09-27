package fr.lkdm.homelink.energy.verification;

import fr.lkdm.homecore.api.energy.*;
import static fr.lkdm.homelink.energy.verification.EnergyValidation.*;
import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.block.*;
import fr.lkdm.homelink.energy.blockentity.*;
import fr.lkdm.homelink.energy.energy.*;
import fr.lkdm.homelink.energy.wind.*;
import fr.lkdm.homelink.energy.network.*;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.event.DeviceEvent;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(EnergyValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WindGameTests {
    private static void fixed(GameTestHelper h) {
        world(h,18000,false,false); WindSavedData.get(h.getLevel()).state().setStrength(1,100000);
    }
    private static void tick(GameTestHelper h,WindTurbineBlockEntity t) { WindTurbineBlockEntity.serverTick(h.getLevel(),t.getBlockPos(),t.getBlockState(),t); }

    @GameTest(template="empty",batch="wind_dimensions",skyAccess=true,timeoutTicks=40)
    public static void unsupportedDimensionsAndSavedDataDisk(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer();
        for(var dimension:List.of(net.minecraft.world.level.Level.NETHER,net.minecraft.world.level.Level.END)) {
            var level=server.getLevel(dimension); var pos=new BlockPos(8,180,8);
            level.setBlockAndUpdate(pos,EnergyRegistries.WIND_TURBINE_1.get().defaultBlockState());
            var t=(WindTurbineBlockEntity)level.getBlockEntity(pos);
            WindSavedData.get(level).state().setStrength(1,100);
            WindTurbineBlockEntity.serverTick(level,pos,t.getBlockState(),t);
            check(h,t.core().status()==WindStatus.UNSUPPORTED_DIMENSION && t.core().generated()==0,"Unsupported dimension produced");
            level.removeBlock(pos,false);
        }
        var directory=java.nio.file.Files.createTempDirectory("homelink-wind-saveddata-");
        var factory=new net.minecraft.world.level.saveddata.SavedData.Factory<WindSavedData>(() -> new WindSavedData(99),(tag,provider)->WindSavedData.load(tag));
        var first=new net.minecraft.world.level.storage.DimensionDataStorage(directory.toFile(),server.getFixerUpper(),h.getLevel().registryAccess());
        var original=first.computeIfAbsent(factory,"wind"); for(int i=0;i<777;i++) original.tick(); first.save();
        net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        var second=new net.minecraft.world.level.storage.DimensionDataStorage(directory.toFile(),server.getFixerUpper(),h.getLevel().registryAccess());
        var loaded=second.get(factory,"wind");
        check(h,loaded!=null && loaded!=original && loaded.state().ticks()==original.state().ticks(),"WindState disk reload failed");
        check(h,loaded.state().currentStrength()==original.state().currentStrength() && loaded.state().randomState()==original.state().randomState(),"Wind trajectory lost on restart");
        net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        java.nio.file.Files.deleteIfExists(directory.resolve("wind.dat")); java.nio.file.Files.deleteIfExists(directory);
        h.succeed();
    }

    @GameTest(template="load",batch="wind_cap",skyAccess=true,timeoutTicks=80)
    public static void actualWeatherAndMetricsAboveHundredPercent(GameTestHelper h) {
        fixed(h); world(h,18000,true,true);
        WindTurbineBlockEntity t=place(h,EnergyRegistries.WIND_TURBINE_3.get(),new BlockPos(8,190-h.absolutePos(BlockPos.ZERO).getY(),8));
        h.runAfterDelay(25,()->{
            check(h,t.weatherMultiplier()==1.6 && t.altitudeMultiplier()==1.25 && t.core().efficiency()==1.5,"Storm cap calculation");
            check(h,t.core().rate()==1.75,"Wind III at cap must produce 1.75 HE/t");
            var metric=t.device().orElseThrow().metrics().stream().filter(m->m.id().equals(HomeLinkEnergy.id("effective_efficiency"))).findFirst().orElseThrow();
            check(h,metric.value().equals(150.),"HomeCore clamped >100% efficiency");
            h.getLevel().removeBlock(t.getBlockPos(),false); h.succeed();
        });
    }

    @GameTest(template="load",batch="wind_clearance",skyAccess=true,timeoutTicks=400)
    public static void allRotorsSkyFacingAndTransitionEvents(GameTestHelper h) {
        fixed(h);
        var blocks=List.of(EnergyRegistries.WIND_TURBINE_1.get(),EnergyRegistries.WIND_TURBINE_2.get(),EnergyRegistries.WIND_TURBINE_3.get());
        var turbines=new ArrayList<WindTurbineBlockEntity>(); var events=new ArrayList<DeviceEvent>();
        var subscription=DashboardAPI.events(h.getLevel().getServer()).subscribe(events::add);
        for(int i=0;i<3;i++) turbines.add(place(h,blocks.get(i),new BlockPos(5+i*9,2,8)));
        h.runAfterDelay(25,()->{
            for(var t:turbines) {
                check(h,t.core().status()==WindStatus.GENERATING,"Rotor must run at night: "+t.core().status());
                check(h,t.device().isPresent(),"HomeCore device missing");
                check(h,t.device().get().deviceType().equals(HomeLinkEnergy.id("wind_turbine")),"One device type for all tiers");
                check(h,t.port(Direction.UP)==null && t.port(Direction.DOWN).type()==EnergyPortType.OUTPUT,"Port orientation");
                h.getLevel().setBlockAndUpdate(t.area().min(),Blocks.STONE.defaultBlockState());
            }
            h.runAfterDelay(25,()->{
                for(var t:turbines) {
                    check(h,t.core().status()==WindStatus.ROTOR_OBSTRUCTED && t.core().rate()==0,"Obstruction did not stop production");
                    check(h,t.area().min().equals(t.obstruction()),"First obstruction position");
                }
                long alerts=events.stream().filter(e->e.type().equals(HomeLinkEnergy.id("wind_rotor_obstructed"))).count();
                check(h,alerts==3,"Expected one obstruction transition per turbine, got "+alerts);
                h.runAfterDelay(60,()->{
                    check(h,events.stream().filter(e->e.type().equals(HomeLinkEnergy.id("wind_rotor_obstructed"))).count()==alerts,"Repeated obstruction alerts");
                    for(var t:turbines) {
                        h.getLevel().removeBlock(t.area().min(),false);
                    }
                    h.runAfterDelay(25,()->{
                        for(var t:turbines) {
                            check(h,t.core().status()==WindStatus.GENERATING,"Removed obstruction did not resume");
                            var roof=t.area().hub().above(t.tier().radius()+2);
                            h.getLevel().setBlockAndUpdate(roof,Blocks.STONE.defaultBlockState());
                        }
                        check(h,events.stream().filter(e->e.type().equals(HomeLinkEnergy.id("wind_rotor_cleared"))).count()==3,"Missing cleared events");
                        h.runAfterDelay(25,()->{
                            for(var t:turbines) {
                                check(h,t.core().status()==WindStatus.SKY_BLOCKED && t.rotorClear(),"Sky gate must stop a clear rotor");
                                var roof=t.area().hub().above(t.tier().radius()+2); h.getLevel().removeBlock(roof,false);
                                var block=(WindTurbineBlock)t.getBlockState().getBlock(); var pos=t.getBlockPos();
                                var rotated=block.defaultBlockState().setValue(WindTurbineBlock.FACING,Direction.EAST);
                                h.getLevel().removeBlock(pos,false);
                                h.getLevel().setBlockAndUpdate(pos,rotated);
                                block.setPlacedBy(h.getLevel(),pos,rotated,null,net.minecraft.world.item.ItemStack.EMPTY);
                                turbines.set(turbines.indexOf(t),(WindTurbineBlockEntity)h.getLevel().getBlockEntity(pos));
                            }
                            h.runAfterDelay(25,()->{
                                try {
                                    for(var t:turbines) check(h,t.core().status()==WindStatus.GENERATING,"Facing change did not invalidate cache");
                                    h.succeed();
                                } finally { subscription.close(); }
                            });
                        });
                    });
                });
            });
        });
    }
    @GameTest(template="load",batch="wind_network",skyAccess=true,timeoutTicks=140)
    public static void mixedCableNetworkConservesEnergy(GameTestHelper h) {
        fixed(h); world(h,6000,false,false);
        WindTurbineBlockEntity wind=place(h,EnergyRegistries.WIND_TURBINE_3.get(),new BlockPos(3,2,6));
        SolarPanelBlockEntity solar=place(h,EnergyRegistries.SOLAR_PANEL_3.get(),new BlockPos(7,2,6));
        for(int x=3;x<=11;x++) {
            h.setBlock(new BlockPos(x,0,6),Blocks.STONE);
            h.setBlock(new BlockPos(x,1,6),EnergyRegistries.COPPER_ENERGY_CABLE.get().defaultBlockState().setValue(CopperEnergyCableBlock.FACES.get(Direction.UP),true));
        }
        BatteryBlockEntity battery=place(h,EnergyRegistries.BATTERY_3.get(),new BlockPos(12,1,6));
        h.runAfterDelay(80,()->{
            check(h,wind.core().generated()>0 && solar.core().generated()>0 && battery.stored()>0,"Missing mixed production/transfer");
            check(h,wind.core().generated()+solar.core().generated()==battery.stored()+wind.core().buffer().stored()+solar.core().buffer().stored(),"Mixed cable conservation");
            var networks=EnergyNetworks.get(h.getLevel()).networksOfMachine(wind.getBlockPos());
            check(h,networks.size()==1 && networks.getFirst().producerCount()==2,"Separate or missing producer networks");
            var n=networks.getFirst();
            check(h,n.production(EnergySourceType.WIND)>0 && n.production(EnergySourceType.SOLAR)>0,"Categories missing");
            check(h,Math.abs(n.production()-n.production(EnergySourceType.WIND)-n.production(EnergySourceType.SOLAR))<1e-9,"Double counting");
            h.succeed();
        });
    }
    @GameTest(template="empty",batch="wind_persistence",skyAccess=true,timeoutTicks=150)
    public static void savedIdentityBufferFractionsAndDuplicateTick(GameTestHelper h) {
        fixed(h);
        WindTurbineBlockEntity t=place(h,EnergyRegistries.WIND_TURBINE_1.get(),new BlockPos(4,2,4));
        h.runAfterDelay(25,()->{
            tick(h,t); long made=t.core().generated(); double fraction=t.core().fraction().fraction();
            for(int i=0;i<100;i++) tick(h,t);
            check(h,made==t.core().generated() && fraction==t.core().fraction().fraction(),"Duplicate tick generation");
            var registry=h.getLevel().registryAccess(); var saved=t.saveWithFullMetadata(registry);
            var copy=(WindTurbineBlockEntity)BlockEntity.loadStatic(t.getBlockPos(),t.getBlockState(),saved,registry);
            check(h,copy.deviceId().equals(t.deviceId()),"UUID did not round-trip");
            check(h,copy.core().buffer().stored()==t.core().buffer().stored() && copy.core().fraction().fraction()==fraction,"Energy/fraction round-trip");
            var wind=WindSavedData.get(h.getLevel()); var reloaded=WindSavedData.load(wind.save(new net.minecraft.nbt.CompoundTag(),registry));
            check(h,reloaded.state().currentStrength()==wind.state().currentStrength() && reloaded.state().randomState()==wind.state().randomState(),"Global wind round-trip");
            t.onChunkUnloaded(); var stale=t.port(null); check(h,stale.extract(100,false)==0,"Stale unloaded output");
            h.getLevel().setDayTime(24000L*100);
            h.runAfterDelay(30,()->{
                tick(h,t); check(h,t.core().generated()==made,"Unloaded turbine generated");
                t.onLoad(); tick(h,t); check(h,t.core().generated()-made<=1,"Time command/reload catch-up");
                h.succeed();
            });
        });
    }
    @GameTest(template="empty",batch="wind_real_unload",skyAccess=true,timeoutTicks=2400)
    public static void actualChunkDiskReload(GameTestHelper h) {
        fixed(h); var level=h.getLevel();
        var remote=new BlockPos(3208,120,3208); var chunk=new net.minecraft.world.level.ChunkPos(remote);
        level.setChunkForced(chunk.x,chunk.z,true); // Test fixture only.
        level.setBlockAndUpdate(remote,EnergyRegistries.WIND_TURBINE_3.get().defaultBlockState());
        EnergyRegistries.WIND_TURBINE_3.get().setPlacedBy(level,remote,level.getBlockState(remote),null,net.minecraft.world.item.ItemStack.EMPTY);
        var t=(WindTurbineBlockEntity)level.getBlockEntity(remote);
        h.startSequence().thenWaitUntil(()->check(h,t.core().generated()>0,"Waiting for turbine chunk ticks"))
                .thenExecute(()->{
            level.setChunkForced(chunk.x,chunk.z,false);
            h.runAfterDelay(400,()->{
                check(h,!level.getChunkSource().hasChunk(chunk.x,chunk.z),"Remote chunk did not unload");
                long made=t.core().generated(),stored=t.core().buffer().stored(); double fraction=t.core().fraction().fraction(); var id=t.deviceId();
                h.runAfterDelay(60,()->{
                    check(h,t.core().generated()==made,"Unloaded turbine produced");
                    level.setChunkForced(chunk.x,chunk.z,true);
                    var next=(WindTurbineBlockEntity)level.getBlockEntity(remote);
                    try {
                        check(h,next!=null && next!=t,"Disk must create a new turbine");
                        check(h,next.deviceId().equals(id) && next.core().generated()==made && next.core().buffer().stored()==stored && next.core().fraction().fraction()==fraction,"Disk persistence failed");
                        h.succeed();
                    } finally { level.removeBlock(remote,false); level.setChunkForced(chunk.x,chunk.z,false); }
                });
            });
        });
    }
    @GameTest(template="load",batch="wind_load",skyAccess=true,timeoutTicks=700)
    public static void hundredActiveTurbines(GameTestHelper h) {
        fixed(h); var turbines=new ArrayList<WindTurbineBlockEntity>(); var batteries=new ArrayList<BatteryBlockEntity>();
        for(int x=1;x<=10;x++) for(int z=1;z<=10;z++) {
            batteries.add(place(h,EnergyRegistries.BATTERY_1.get(),new BlockPos(x*3,1,z*3)));
            turbines.add(place(h,EnergyRegistries.WIND_TURBINE_1.get(),new BlockPos(x*3,2,z*3)));
        }
        h.runAfterDelay(600,()->{
            long ticks=0,nanos=0,scans=0,updates=0,total=0;
            for(int i=0;i<100;i++) {
                var t=turbines.get(i); var b=batteries.get(i);
                check(h,t.core().status()==WindStatus.GENERATING && b.stored()>30,"Inactive turbine "+i+" status="+t.core().status());
                check(h,t.core().generated()==b.stored()+t.core().buffer().stored(),"Load conservation");
                ticks+=t.measuredTicks(); nanos+=t.tickNanos(); scans+=t.scans(); updates+=t.updates(); total+=b.stored();
            }
            check(h,ticks>=59000,"Not enough real server ticks"); check(h,scans<1500,"Clearance scanning too often: "+scans);
            check(h,updates<4000,"Too many rotor updates: "+updates);
            HomeLinkEnergy.LOGGER.info("WIND_LOAD_OK turbines=100 batteries=100 ticks={} cpuMs={} meanUsPerTurbineTick={} clearanceScans={} rotorUpdates={} storedHE={}",ticks,nanos/1e6,nanos/1e3/ticks,scans,updates,total);
            h.succeed();
        });
    }
}
