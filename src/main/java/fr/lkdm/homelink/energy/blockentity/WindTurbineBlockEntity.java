package fr.lkdm.homelink.energy.blockentity;

import fr.lkdm.homecore.api.energy.*;
import fr.lkdm.homelink.energy.block.*;
import fr.lkdm.homelink.energy.config.WindConfig;
import fr.lkdm.homelink.energy.energy.*;
import fr.lkdm.homelink.energy.homelink.*;
import fr.lkdm.homelink.energy.menu.*;
import fr.lkdm.homelink.energy.network.EnergyNetworks;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import fr.lkdm.homelink.energy.wind.*;
import fr.lkdm.homecore.api.event.DeviceEvent;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

/** A loaded server turbine uses the existing HE buffer, port, cable graph and HomeCore lifecycle. */
public final class WindTurbineBlockEntity extends EnergyDeviceBlockEntity {
    private final WindTurbineCore core=new WindTurbineCore(WindConfig.buffer(),()->level==null?0:level.getGameTime());
    private final HePort port=new Port();
    private final MenuData display=new MenuData(WindTurbineMenu.VALUES);
    private RotorArea watched;
    private RotorArea cachedArea;
    private Direction cachedFacing;
    private boolean dirty=true, rotorClear, skyVisible;
    private BlockPos obstruction;
    private long lastTick=Long.MIN_VALUE,nextCheck,scans,updates,measuredTicks,tickNanos;
    private double wind,weatherMultiplier=1,altitudeMultiplier=1;
    private WindState.Trend trend=WindState.Trend.STABLE;
    private Weather weather=Weather.CLEAR;
    private float syncedSpeed;
    private BlockCapabilityCache<EnergyPort,Direction> below;
    private WindSavedData windData;
    private boolean legacyBase;
    public boolean baseComplete() {
        return level != null && ((WindTurbineBlock)getBlockState().getBlock()).complete(level,worldPosition,getBlockState());
    }
    public WindTurbineBlockEntity(BlockPos pos,BlockState state) { super(EnergyRegistries.WIND_TURBINE.get(),pos,state); }
    public WindTier tier() { return ((WindTurbineBlock)getBlockState().getBlock()).tier(); }
    public RotorArea area() {
        Direction facing=getBlockState().getValue(WindTurbineBlock.FACING);
        if(cachedArea==null || cachedFacing!=facing) { cachedFacing=facing; cachedArea=RotorArea.of(worldPosition,facing,tier()); }
        return cachedArea;
    }
    public WindTurbineCore core() { return core; }
    public boolean rotorClear() { return rotorClear; }
    public boolean skyVisible() { return skyVisible; }
    public BlockPos obstruction() { return obstruction; }
    public double wind() { return wind; }
    public WindState.Trend trend() { return trend; }
    public Weather weather() { return weather; }
    public double weatherMultiplier() { return weatherMultiplier; }
    public double altitudeMultiplier() { return altitudeMultiplier; }
    public int hubY() { return worldPosition.getY()+tier().hubOffset(); }
    public long scans() { return scans; }
    public long updates() { return updates; }
    public long measuredTicks() { return measuredTicks; }
    public long tickNanos() { return tickNanos; }
    public float targetRotorSpeed() { return syncedSpeed; }
    public void invalidateClearance() { dirty=true; }
    public EnergyNetworks.Connection networkConnection() {
        return level instanceof ServerLevel s ? EnergyNetworks.connection(s,worldPosition) : EnergyNetworks.Connection.NONE;
    }
    public static void serverTick(Level level,BlockPos pos,BlockState state,WindTurbineBlockEntity turbine) {
        if(level instanceof ServerLevel server && turbine.available()) turbine.tick(server);
    }
    private void tick(ServerLevel server) {
        long now=server.getGameTime(); if(lastTick==now) return; lastTick=now;
        if(legacyBase && Math.floorMod(now+worldPosition.hashCode(),20)==0
                && ((WindTurbineBlock)getBlockState().getBlock()).expandLegacyBase(server,worldPosition,getBlockState())) {
            legacyBase=false; invalidatePorts(); setChanged();
        }
        long start=Boolean.getBoolean("energy.measureTicks")?System.nanoTime():0;
        var currentArea=area();
        if(!currentArea.equals(watched)) {
            if(watched!=null) WindClearance.remove(server,this,watched);
            watched=currentArea; WindClearance.add(server,this); dirty=true;
        }
        if(windData==null) windData=WindSavedData.get(server);
        WindSavedData data=windData; WindParameters p=data.parameters();
        if(dirty || now>=nextCheck) {
            scan(server,currentArea); dirty=false;
            // Offset the first periodic check to avoid synchronized scan bursts after chunk load.
            nextCheck=now+p.checkInterval()+(scans==1?Math.floorMod(worldPosition.hashCode(),p.checkInterval()):0);
        }
        WindStatus old=core.status();
        wind=data.state().currentStrength(); trend=data.state().trend();
        weather=Weather.of(server.isRaining(),server.isThundering()); weatherMultiplier=p.weather(weather); altitudeMultiplier=p.altitude(hubY());
        core.buffer().setCapacity(WindConfig.buffer());
        core.tick(tier().nominal(),data.state().ticks(),wind,weatherMultiplier,hubY(),server.dimension()==Level.OVERWORLD,rotorClear,skyVisible,baseComplete(),p);
        if(core.buffer().stored()>0) {
            if(below==null) below=BlockCapabilityCache.create(HeCapabilities.PORT,server,worldPosition.below(),Direction.UP,()->available(),()->{});
            var target=below.getCapability(); if(target!=null) EnergyTransfer.move(port,target,Long.MAX_VALUE);
        }
        // Fractions and the executed-period counter are durable, even without a whole HE this tick.
        setChanged();
        tickDevice(server);
        if(old!=core.status()) device().ifPresent(EnergyDevice::refresh);
        if(Math.floorMod(now+worldPosition.hashCode(),10)==0) refreshDisplay();
        float target=core.status()==WindStatus.GENERATING ? (float)core.efficiency() : 0;
        if((Math.floorMod(now+worldPosition.hashCode(),20)==0 && Math.abs(target-syncedSpeed)>.005f) || old!=core.status()) {
            syncedSpeed=target; server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2); updates++;
        }
        if(start!=0) { tickNanos+=System.nanoTime()-start; measuredTicks++; }
    }
    private void scan(ServerLevel server,RotorArea area) {
        scans++; obstruction=null; rotorClear=true;
        for(BlockPos pos:BlockPos.betweenClosed(area.min(),area.max())) {
            if(server.isOutsideBuildHeight(pos) || !server.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4)) { rotorClear=false; break; }
            if(!server.getBlockState(pos).isAir()) { rotorClear=false; obstruction=pos.immutable(); break; }
        }
        BlockPos hub=area.hub();
        skyVisible=!server.isOutsideBuildHeight(hub) && server.getChunkSource().hasChunk(hub.getX()>>4,hub.getZ()>>4)
                && server.getHeight(Heightmap.Types.WORLD_SURFACE,hub.getX(),hub.getZ())<=hub.getY();
    }
    @Nullable public HePort port(@Nullable Direction side) { return side==Direction.UP?null:port; }
    private final class Port implements HePort {
        @Override public EnergyRole role() { return EnergyRole.PRODUCER; }
        @Override public EnergyPortType type() { return EnergyPortType.OUTPUT; }
        @Override public EnergySourceType sourceType() { return EnergySourceType.WIND; }
        @Override public long stored() { return core.buffer().stored(); }
        @Override public long capacity() { return core.buffer().capacity(); }
        @Override public long insert(long amount,boolean simulate) { return 0; }
        @Override public long extract(long amount,boolean simulate) {
            if(!(level instanceof ServerLevel) || !WindTurbineBlockEntity.this.available() || !baseComplete()) return 0;
            long n=core.buffer().extract(amount,simulate); if(!simulate && n>0) setChanged(); return n;
        }
    }
    private void refreshDisplay() {
        display.put(WindTurbineMenu.STATUS,core.status().ordinal()); display.put(WindTurbineMenu.TIER,tier().level());
        display.put(WindTurbineMenu.WIND,(int)Math.round(wind*1000)); display.put(WindTurbineMenu.TREND,trend.ordinal());
        display.put(WindTurbineMenu.WEATHER,weather.ordinal()); display.put(WindTurbineMenu.WEATHER_MULT,(int)Math.round(weatherMultiplier*100));
        display.put(WindTurbineMenu.ALTITUDE,hubY()); display.put(WindTurbineMenu.HEIGHT_MULT,(int)Math.round(altitudeMultiplier*100));
        display.put(WindTurbineMenu.EFFICIENCY,(int)Math.round(core.efficiency()*100)); display.put(WindTurbineMenu.ROTOR,rotorClear?1:0);
        display.put(WindTurbineMenu.SKY,skyVisible?1:0); display.put(WindTurbineMenu.RATE,(int)Math.round(core.rate()*1000));
        display.put(WindTurbineMenu.PERIOD,(int)Math.min(Integer.MAX_VALUE,core.generatedPeriod()));
        display.put(WindTurbineMenu.BUFFER,(int)core.buffer().stored()); display.put(WindTurbineMenu.CAPACITY,(int)core.buffer().capacity());
        display.put(WindTurbineMenu.NETWORK,networkConnection().ordinal()); display.put(WindTurbineMenu.NOMINAL,(int)tier().nominal());
        display.put(WindTurbineMenu.OBSTRUCTION,obstruction==null?0:1);
        if(obstruction!=null) { display.put(WindTurbineMenu.OB_X,obstruction.getX()); display.put(WindTurbineMenu.OB_Y,obstruction.getY()); display.put(WindTurbineMenu.OB_Z,obstruction.getZ()); }
    }
    @Override public EnergyDevice createDevice(Consumer<DeviceEvent> events) { return new WindDevice(this,events); }
    @Override public Component getDisplayName() { return name(); }
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player) { refreshDisplay(); return new WindTurbineMenu(id,inv,this,display); }
    @Override public void onLoad() { super.onLoad(); dirty=true; lastTick=Long.MIN_VALUE; invalidatePorts(); }
    private void release() {
        if(level instanceof ServerLevel s && watched!=null) WindClearance.remove(s,this,watched);
        watched=null; below=null; windData=null; invalidatePorts();
    }
    private void invalidatePorts() {
        if(level instanceof ServerLevel s) {
            var block=(WindTurbineBlock)getBlockState().getBlock();
            for(var tile:block.positions(worldPosition,getBlockState())) if(s.isLoaded(tile)) s.invalidateCapabilities(tile);
            EnergyNetworks.existing(s).ifPresent(n->n.markDirty(worldPosition));
        }
    }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); release(); }
    @Override public void setRemoved() { super.setRemoved(); release(); }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        if(!legacyBase) tag.putInt("base_version",1);
        tag.putLong("energy",core.buffer().stored()); tag.putDouble("fraction",core.fraction().fraction());
        tag.putLong("generated",core.generated()); tag.putLong("lost",core.lost()); tag.putLong("generated_period",core.generatedPeriod());
        tag.putLong("period",core.period()); tag.putBoolean("running",core.running());
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries); core.buffer().setCapacity(WindConfig.buffer()); core.buffer().setStored(tag.getLong("energy"));
        legacyBase=!tag.contains("base_version");
        core.fraction().setFraction(tag.getDouble("fraction"));
        core.restore(tag.getLong("generated"),tag.getLong("lost"),tag.getLong("generated_period"),tag.getLong("period"),tag.getBoolean("running")); dirty=true;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag=new CompoundTag(); tag.putFloat("rotor_speed",syncedSpeed); return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries) {
        float speed=tag.getFloat("rotor_speed"); syncedSpeed=Float.isFinite(speed)?Math.max(0,Math.min(10,speed)):0;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(),registries);
    }
}
