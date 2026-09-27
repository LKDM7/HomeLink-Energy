package fr.lkdm.homelink.energy.verification;

import static fr.lkdm.homelink.energy.verification.EnergyValidation.*;
import com.mojang.authlib.GameProfile;
import fr.lkdm.homelink.energy.block.*;
import fr.lkdm.homelink.energy.blockentity.WindTurbineBlockEntity;
import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homelink.energy.network.EnergyNetworks;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

/** Real-world regressions for the array footprint and surface electrical topology. */
@GameTestHolder(EnergyValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WindFootprintGameTests {
    @GameTest(template="empty", batch="wind_base", timeoutTicks=80, skyAccess=true)
    public static void footprintsAndRotationsShareOneController(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(2,2,2));
        for (var block : new WindTurbineBlock[]{EnergyRegistries.WIND_TURBINE_1.get(),EnergyRegistries.WIND_TURBINE_2.get(),EnergyRegistries.WIND_TURBINE_3.get()}) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var state = block.defaultBlockState().setValue(WindTurbineBlock.FACING, facing);
                level.setBlockAndUpdate(origin, state);
                block.setPlacedBy(level, origin, state, null, ItemStack.EMPTY);
                var controller = level.getBlockEntity(origin);
                int entities = 0;
                check(h, block.complete(level, origin, state), "Incomplete array " + block.tier() + " / " + facing);
                int expected=switch(block.tier()) { case I -> 1; case II -> 2; case III -> 4; };
                check(h, block.positions(origin,state).size() == expected, "Wrong footprint");
                for (BlockPos tile : block.positions(origin,state)) {
                    if (level.getBlockEntity(tile) != null) entities++;
                    check(h, WindTurbineBlock.controller(level,tile,level.getBlockState(tile)) == controller,"Different controller");
                    check(h, level.getCapability(HeCapabilities.PORT,tile,Direction.DOWN) == ((WindTurbineBlockEntity)controller).port(Direction.DOWN),"Port not shared");
                }
                check(h, entities == 1,"More than one ticking controller");
                var center=Vec3.atCenterOf(origin)
                        .add(Vec3.atLowerCornerOf(facing.getClockWise().getNormal()).scale((block.tier().width()-1)/2.))
                        .add(Vec3.atLowerCornerOf(facing.getOpposite().getNormal()).scale((block.tier().depth()-1)/2.))
                        .add(0,block.tier().hubOffset(),0)
                        .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(1.1));
                var area=((WindTurbineBlockEntity)controller).area().bounds();
                double radius=(2*block.tier().radius()+1)/3.*1.4;
                for(int angle=0;angle<360;angle+=10)for(double depth:new double[]{-.375,.25}) {
                    double radians=Math.toRadians(angle);
                    var point=center.add(Vec3.atLowerCornerOf(facing.getClockWise().getNormal()).scale(Math.cos(radians)*radius))
                            .add(0,Math.sin(radians)*radius,0).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(depth));
                    check(h,area.contains(point),"Centered rotor extends beyond obstruction envelope: "+block.tier()+" / "+facing);
                }
                level.removeBlock(origin,false);
                for (BlockPos tile : block.positions(origin,state)) check(h,level.getBlockState(tile).isAir(),"Orphan tile");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty", batch="wind_base", timeoutTicks=40)
    public static void placementRejectsBlockedFootprintWithoutOverwriting(GameTestHelper h) {
        var level = h.getLevel();
        var player = FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"array_placement"));
        player.setYRot(0);
        var block = EnergyRegistries.WIND_TURBINE_3.get();
        var origin = h.absolutePos(new BlockPos(2,2,2));
        level.setBlockAndUpdate(origin.below(),Blocks.STONE.defaultBlockState());
        var stack = new ItemStack(block);
        var context = new BlockPlaceContext(level,player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(origin.below()).add(0,0.5,0),Direction.UP,origin.below(),false));
        check(h,block.getStateForPlacement(context)!=null,"Clear 2x2 placement rejected");
        var obstructed = origin.east().south();
        level.setBlockAndUpdate(obstructed,Blocks.DIAMOND_BLOCK.defaultBlockState());
        check(h,block.getStateForPlacement(context)==null,"Occupied footprint accepted");
        check(h,!((net.minecraft.world.item.BlockItem)stack.getItem()).place(context).consumesAction(),"Placement overwrote an obstruction");
        check(h,level.getBlockState(origin).isAir() && level.getBlockState(obstructed).is(Blocks.DIAMOND_BLOCK),"Partial placement");
        h.succeed();
    }
    @GameTest(template="empty", batch="wind_base", timeoutTicks=80)
    public static void breakingSlaveDropsOnePanelAndRemovesArray(GameTestHelper h) {
        var origin = new BlockPos(2,2,2);
        var block = EnergyRegistries.WIND_TURBINE_3.get();
        WindTurbineBlockEntity panel = place(h,block,origin);
        panel.core().buffer().setStored(700);
        var state=panel.getBlockState();
        var positions=block.positions(panel.getBlockPos(),state);
        h.getLevel().destroyBlock(positions.getLast(),true);
        int count=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(panel.getBlockPos()).inflate(3)).stream()
                .filter(e->e.getItem().is(block.asItem())).mapToInt(e->e.getItem().getCount()).sum();
        check(h,count==1,"Expected exactly one array item, got " + count);
        for(var p:positions) check(h,h.getLevel().getBlockState(p).isAir(),"Tile left after breaking slave");
        check(h,panel.port(null).extract(700,false)==0,"Destroyed controller retained a usable port");
        h.succeed();
    }
    @GameTest(template="empty",batch="wind_base_network",timeoutTicks=140,skyAccess=true)
    public static void passiveCellsShareOneNetworkProducer(GameTestHelper h) {
        world(h,18000,false,false);
        fr.lkdm.homelink.energy.wind.WindSavedData.get(h.getLevel()).state().setStrength(1,10000);
        WindTurbineBlockEntity turbine=place(h,EnergyRegistries.WIND_TURBINE_3.get(),new BlockPos(2,2,2));
        // Both attachments are below the passive rear row, never below the controller.
        for(int x=2;x<=5;x++) {
            h.setBlock(new BlockPos(x,0,3),Blocks.STONE);
            h.setBlock(new BlockPos(x,1,3),EnergyRegistries.COPPER_ENERGY_CABLE.get().defaultBlockState()
                    .setValue(CopperEnergyCableBlock.FACES.get(Direction.UP),true));
        }
        fr.lkdm.homelink.energy.blockentity.BatteryBlockEntity battery=place(h,EnergyRegistries.BATTERY_3.get(),new BlockPos(6,1,3));
        h.runAfterDelay(80,()->{
            var networks=EnergyNetworks.get(h.getLevel()).networksOfMachine(turbine.getBlockPos());
            check(h,networks.size()==1 && networks.getFirst().producerCount()==1,"Passive cells counted as separate producers");
            check(h,battery.stored()>0,"Passive-cell cable connection did not transfer");
            check(h,turbine.core().generated()==battery.stored()+turbine.core().buffer().stored(),"Duplicated HE through base cells");
            h.succeed();
        });
    }

    @GameTest(template="empty",batch="wind_base_migration",timeoutTicks=140,skyAccess=true)
    public static void oldSingleBlockSaveExpandsWithoutOverwritingNeighbors(GameTestHelper h) {
        world(h,18000,false,false);
        fr.lkdm.homelink.energy.wind.WindSavedData.get(h.getLevel()).state().setStrength(1,10000);
        var level=h.getLevel();var block=EnergyRegistries.WIND_TURBINE_3.get();var pos=h.absolutePos(new BlockPos(2,2,2));
        level.setBlockAndUpdate(pos,block.defaultBlockState());
        var turbine=(WindTurbineBlockEntity)level.getBlockEntity(pos);
        turbine.core().buffer().setStored(321);var id=turbine.deviceId();
        var saved=turbine.saveWithFullMetadata(level.registryAccess());saved.remove("base_version");
        turbine.loadWithComponents(saved,level.registryAccess());
        var obstacle=pos.east().south();level.setBlockAndUpdate(obstacle,Blocks.DIAMOND_BLOCK.defaultBlockState());
        h.runAfterDelay(30,()->{
            check(h,level.getBlockState(obstacle).is(Blocks.DIAMOND_BLOCK),"Migration overwrote an occupied cell");
            check(h,level.getBlockState(pos.east()).isAir(),"Migration partially placed a blocked base");
            check(h,turbine.core().status()==fr.lkdm.homelink.energy.wind.WindStatus.INCOMPLETE_BASE && turbine.core().generated()==0,"Incomplete base produced HE");
            check(h,turbine.core().buffer().stored()==321 && turbine.deviceId().equals(id),"Migration lost persistent state");
            level.removeBlock(obstacle,false);
            h.runAfterDelay(30,()->{
                check(h,turbine.baseComplete() && turbine.core().generated()>0,"Legacy base did not resume after clearing its footprint");
                check(h,turbine.deviceId().equals(id),"Legacy migration replaced the device UUID");
                check(h,turbine.saveWithFullMetadata(level.registryAccess()).getInt("base_version")==1,"Migration version was not saved");
                var copy=(WindTurbineBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos,turbine.getBlockState(),turbine.saveWithFullMetadata(level.registryAccess()),level.registryAccess());
                check(h,copy!=null && copy.deviceId().equals(id) && copy.core().buffer().stored()==turbine.core().buffer().stored(),"New footprint save/reload lost energy or identity");
                h.succeed();
            });
        });
    }
}
