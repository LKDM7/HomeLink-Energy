package fr.lkdm.homelink.energy.verification;

import static fr.lkdm.homelink.energy.verification.EnergyValidation.*;
import com.mojang.authlib.GameProfile;
import fr.lkdm.homelink.energy.block.*;
import fr.lkdm.homelink.energy.blockentity.SolarPanelBlockEntity;
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
public final class SurfaceGameTests {
    private static BlockState face(Direction direction) {
        var state = EnergyRegistries.COPPER_ENERGY_CABLE.get().defaultBlockState();
        for (Direction d : Direction.values()) state = state.setValue(CopperEnergyCableBlock.FACES.get(d), d == direction);
        return state;
    }
    @GameTest(template="empty", batch="surface", timeoutTicks=80, skyAccess=true)
    public static void footprintsAndRotationsShareOneController(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(2,2,2));
        for (var block : new SolarPanelBlock[]{EnergyRegistries.SOLAR_PANEL_1.get(),EnergyRegistries.SOLAR_PANEL_2.get(),EnergyRegistries.SOLAR_PANEL_3.get()}) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var state = block.defaultBlockState().setValue(SolarPanelBlock.FACING, facing);
                level.setBlockAndUpdate(origin, state);
                block.setPlacedBy(level, origin, state, null, ItemStack.EMPTY);
                var controller = level.getBlockEntity(origin);
                int entities = 0;
                check(h, block.complete(level, origin, state), "Incomplete array " + block.tier() + " / " + facing);
                check(h, block.positions(origin,state).size() == block.tier().width()*block.tier().depth(), "Wrong footprint");
                for (BlockPos tile : block.positions(origin,state)) {
                    if (level.getBlockEntity(tile) != null) entities++;
                    check(h, SolarPanelBlock.controller(level,tile,level.getBlockState(tile)) == controller,"Different controller");
                    check(h, level.getCapability(HeCapabilities.PORT,tile,Direction.DOWN) == ((SolarPanelBlockEntity)controller).port(Direction.DOWN),"Port not shared");
                }
                check(h, entities == 1,"More than one ticking controller");
                level.removeBlock(origin,false);
                for (BlockPos tile : block.positions(origin,state)) check(h,level.getBlockState(tile).isAir(),"Orphan tile");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty", batch="surface", timeoutTicks=40)
    public static void placementRejectsBlockedFootprintWithoutOverwriting(GameTestHelper h) {
        var level = h.getLevel();
        var player = FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"array_placement"));
        player.setYRot(0);
        var block = EnergyRegistries.SOLAR_PANEL_3.get();
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
    @GameTest(template="empty", batch="surface", timeoutTicks=80)
    public static void breakingSlaveDropsOnePanelAndRemovesArray(GameTestHelper h) {
        var origin = new BlockPos(2,2,2);
        var block = EnergyRegistries.SOLAR_PANEL_3.get();
        SolarPanelBlockEntity panel = place(h,block,origin);
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
    @GameTest(template="empty", batch="surface_sun", timeoutTicks=160, skyAccess=true)
    public static void shadeOverPassiveTileStopsTheEntireArray(GameTestHelper h) {
        world(h,6000,false,false);
        SolarPanelBlockEntity panel=place(h,EnergyRegistries.SOLAR_PANEL_3.get(),new BlockPos(2,2,2));
        BlockPos shade=new BlockPos(3,4,3);
        h.setBlock(shade,Blocks.GLASS);
        h.runAfterDelay(45,()->{
            check(h,panel.core().generated()==0,"Array generated through shade over passive tile");
            h.setBlock(shade,Blocks.AIR);
            h.runAfterDelay(50,()->{check(h,panel.core().generated()>0,"Array did not resume");h.succeed();});
        });
    }
    @GameTest(template="empty", batch="surface", timeoutTicks=80)
    public static void cablePlacementAcceptsAllSixSupportFaces(GameTestHelper h) {
        var level=h.getLevel();
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"wire_placement"));
        BlockPos support=h.absolutePos(new BlockPos(3,3,3));
        level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());
        var block=EnergyRegistries.COPPER_ENERGY_CABLE.get();
        for(Direction outward:Direction.values()) {
            var stack=new ItemStack(block);
            var hit=new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(outward.getNormal()).scale(0.5)),outward,support,false);
            var context=new BlockPlaceContext(level,player,InteractionHand.MAIN_HAND,stack,hit);
            var state=block.getStateForPlacement(context);
            check(h,state!=null && CopperEnergyCableBlock.hasFace(state,outward.getOpposite()),"Wrong attachment for " + outward);
            check(h,((net.minecraft.world.item.BlockItem)stack.getItem()).place(context).consumesAction(),"Placement failed " + outward);
            check(h,CopperEnergyCableBlock.hasFace(level.getBlockState(support.relative(outward)),outward.getOpposite()),"Face absent " + outward);
        }
        level.removeBlock(support,false);
        for(Direction outward:Direction.values()) check(h,level.getBlockState(support.relative(outward)).isAir(),"Unsupported trace remained");
        h.succeed();
    }
    @GameTest(template="empty", batch="surface", timeoutTicks=100)
    public static void wallTraceTransfersEnergyAndBreaksWithSupport(GameTestHelper h) {
        world(h,18000,false,false);
        var battery=(fr.lkdm.homelink.energy.blockentity.BatteryBlockEntity)place(h,EnergyRegistries.BATTERY_1.get(),new BlockPos(1,1,2));
        charge(h,battery,1000);
        TestConsumer consumer=place(h,EnergyValidation.CONSUMER.get(),new BlockPos(1,5,2));
        for(int y=2;y<=4;y++) { h.setBlock(new BlockPos(2,y,2),Blocks.STONE); h.setBlock(new BlockPos(1,y,2),face(Direction.EAST)); }
        h.runAfterDelay(12,()->{
            check(h,consumer.received>0 && consumer.received+battery.stored()==1000,"Wall transfer/conservation failed");
            h.setBlock(new BlockPos(2,3,2),Blocks.AIR);
            check(h,h.getBlockState(new BlockPos(1,3,2)).isAir(),"Unsupported wire did not break");
            h.runAfterDelay(2,()->{
                long received=consumer.received;
                h.runAfterDelay(8,()->{check(h,consumer.received==received,"Energy crossed removed wall trace");h.succeed();});
            });
        });
    }
    @GameTest(template="empty", batch="surface", timeoutTicks=80)
    public static void tracesWrapOutsideCornerAndJoinInsideCorner(GameTestHelper h) {
        var support=new BlockPos(3,2,3);
        h.setBlock(support,Blocks.STONE);
        var top=support.above(); var west=support.west();
        h.setBlock(top,face(Direction.DOWN)); h.setBlock(west,face(Direction.EAST));
        var level=h.getLevel();
        check(h,CopperEnergyCableBlock.neighbors(level,h.absolutePos(top)).contains(h.absolutePos(west)),"Outside corner disconnected");
        check(h,CopperEnergyCableBlock.neighbors(level,h.absolutePos(west)).contains(h.absolutePos(top)),"Outside corner asymmetric");
        var inside=new BlockPos(1,2,1);
        h.setBlock(inside.below(),Blocks.STONE); h.setBlock(inside.east(),Blocks.STONE);
        h.setBlock(inside,face(Direction.DOWN).setValue(CopperEnergyCableBlock.FACES.get(Direction.EAST),true));
        h.setBlock(inside.above().east(),Blocks.STONE); h.setBlock(inside.above(),face(Direction.EAST));
        h.setBlock(inside.west().below(),Blocks.STONE); h.setBlock(inside.west(),face(Direction.DOWN));
        h.runAfterDelay(3,()->{
            var networks=EnergyNetworks.get(level);
            check(h,networks.networkOfCable(h.absolutePos(inside.above())).equals(networks.networkOfCable(h.absolutePos(inside.west()))),"Inside corner not one network");
            h.succeed();
        });
    }
    @GameTest(template="empty", batch="surface", timeoutTicks=40)
    public static void adjacentParallelSurfacesDoNotShortTogether(GameTestHelper h) {
        var a=new BlockPos(2,2,2); var b=a.east();
        h.setBlock(a.below(),Blocks.STONE); h.setBlock(b.above(),Blocks.STONE);
        h.setBlock(a,face(Direction.DOWN));h.setBlock(b,face(Direction.UP));
        check(h,!CopperEnergyCableBlock.neighbors(h.getLevel(),h.absolutePos(a)).contains(h.absolutePos(b)),"Disconnected floor and ceiling shorted");
        h.succeed();
    }

    @GameTest(template="empty", batch="surface", timeoutTicks=40)
    public static void addingAndRemovingFacesConservesCableItems(GameTestHelper h) {
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(3,3,3));
        level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos.east(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos,face(Direction.DOWN));
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"wire_corner"));
        var stack=new ItemStack(EnergyRegistries.COPPER_ENERGY_CABLE.get(),2);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos.east()).add(-0.5,0,0),Direction.WEST,pos.east(),false);
        var context=new BlockPlaceContext(level,player,InteractionHand.MAIN_HAND,stack,hit);
        check(h,((net.minecraft.world.item.BlockItem)stack.getItem()).place(context).consumesAction(),"Could not add wall face to floor trace");
        check(h,CopperEnergyCableBlock.faceCount(level.getBlockState(pos))==2,"Adding face replaced old face");
        check(h,stack.getCount()==1,"Adding a face must consume one item");
        level.removeBlock(pos.east(),false);
        check(h,CopperEnergyCableBlock.faceCount(level.getBlockState(pos))==1,"Removing wall lost floor trace");
        level.removeBlock(pos.below(),false);
        int dropped=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).stream()
                .filter(e->e.getItem().is(EnergyRegistries.COPPER_ENERGY_CABLE_ITEM.get())).mapToInt(e->e.getItem().getCount()).sum();
        check(h,dropped==2,"Two faces must return exactly two items, got " + dropped);
        h.succeed();
    }

    @GameTest(template="empty", batch="surface", timeoutTicks=50)
    public static void secondaryPortsRejoinAfterControllerReload(GameTestHelper h) {
        world(h,18000,false,false);
        var block=EnergyRegistries.SOLAR_PANEL_3.get();
        SolarPanelBlockEntity panel=place(h,block,new BlockPos(2,2,2));
        var tile=panel.getBlockPos().east();
        var cache=net.neoforged.neoforge.capabilities.BlockCapabilityCache.create(HeCapabilities.PORT,h.getLevel(),tile,Direction.DOWN);
        var port=cache.getCapability();
        panel.core().buffer().setStored(500);
        var saved=panel.saveWithFullMetadata(h.getLevel().registryAccess());
        panel.onChunkUnloaded();
        check(h,port.extract(10,false)==0,"Unloaded controller retained a live slave port");
        var restored=new SolarPanelBlockEntity(panel.getBlockPos(),panel.getBlockState());
        restored.loadWithComponents(saved,h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(restored);
        restored.onLoad();
        check(h,cache.getCapability()!=port && cache.getCapability()==restored.port(Direction.DOWN),"Slave cache still references old controller");
        check(h,cache.getCapability().extract(10,false)==10 && restored.core().buffer().stored()==490,"Reloaded slave does not export");
        h.succeed();
    }
}
