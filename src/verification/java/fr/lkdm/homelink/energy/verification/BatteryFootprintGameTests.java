package fr.lkdm.homelink.energy.verification;

import static fr.lkdm.homelink.energy.verification.EnergyValidation.*;
import com.mojang.authlib.GameProfile;
import fr.lkdm.homelink.energy.block.*;
import fr.lkdm.homelink.energy.blockentity.BatteryBlockEntity;
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
public final class BatteryFootprintGameTests {
    @GameTest(template="empty", batch="battery_base", timeoutTicks=80, skyAccess=true)
    public static void footprintsAndRotationsShareOneController(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(2,2,2));
        for (var block : new BatteryBlock[]{EnergyRegistries.BATTERY_1.get(),EnergyRegistries.BATTERY_2.get(),EnergyRegistries.BATTERY_3.get()}) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var state = block.defaultBlockState().setValue(BatteryBlock.FACING, facing);
                level.setBlockAndUpdate(origin, state);
                block.setPlacedBy(level, origin, state, null, ItemStack.EMPTY);
                var controller = level.getBlockEntity(origin);
                int entities = 0;
                check(h, block.complete(level, origin, state), "Incomplete array " + block.tier() + " / " + facing);
                int expected=switch(block.tier()) { case I -> 1; case II -> 2; case III -> 4; };
                check(h, block.positions(origin,state).size() == expected, "Wrong footprint");
                for (BlockPos tile : block.positions(origin,state)) {
                    if (level.getBlockEntity(tile) != null) entities++;
                    check(h, BatteryBlock.controller(level,tile,level.getBlockState(tile)) == controller,"Different controller");
                    check(h, level.getCapability(HeCapabilities.PORT,tile,Direction.DOWN) == ((BatteryBlockEntity)controller).port(Direction.DOWN),"Port not shared");
                }
                check(h, entities == 1,"More than one ticking controller");
                level.removeBlock(origin,false);
                for (BlockPos tile : block.positions(origin,state)) check(h,level.getBlockState(tile).isAir(),"Orphan tile");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty", batch="battery_base", timeoutTicks=40)
    public static void placementRejectsBlockedFootprintWithoutOverwriting(GameTestHelper h) {
        var level = h.getLevel();
        var player = FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"array_placement"));
        player.setYRot(0);
        var block = EnergyRegistries.BATTERY_3.get();
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
    @GameTest(template="empty", batch="battery_base", timeoutTicks=80)
    public static void breakingSlaveDropsOneBatteryAndRemovesArray(GameTestHelper h) {
        var origin = new BlockPos(2,2,2);
        var block = EnergyRegistries.BATTERY_3.get();
        BatteryBlockEntity panel = place(h,block,origin);
        charge(h,panel,700);
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
    @GameTest(template="empty",batch="battery_base",timeoutTicks=40)
    public static void allCellsShareTheTransferBudget(GameTestHelper h) {
        BatteryBlockEntity battery=place(h,EnergyRegistries.BATTERY_3.get(),new BlockPos(2,2,2));
        var block=(BatteryBlock)battery.getBlockState().getBlock();long inserted=0,extracted=0;
        for(var pos:block.positions(battery.getBlockPos(),battery.getBlockState())) {
            var port=h.getLevel().getCapability(HeCapabilities.PORT,pos,Direction.UP);
            inserted+=port.insert(1000,false);
        }
        check(h,inserted==128 && battery.stored()==128,"Input budget multiplied by footprint");
        for(var pos:block.positions(battery.getBlockPos(),battery.getBlockState())) {
            var port=h.getLevel().getCapability(HeCapabilities.PORT,pos,Direction.DOWN);
            extracted+=port.extract(1000,false);
        }
        check(h,extracted==128 && battery.stored()==0,"Output budget or stored energy duplicated");
        h.succeed();
    }

    @GameTest(template="empty",batch="battery_base_network",timeoutTicks=120)
    public static void passiveCellsCountAsOneBatteryInTheNetwork(GameTestHelper h) {
        world(h,18000,false,false);
        BatteryBlockEntity battery=place(h,EnergyRegistries.BATTERY_3.get(),new BlockPos(2,2,2));
        charge(h,battery,5000);
        for(int x=2;x<=5;x++) {
            h.setBlock(new BlockPos(x,0,3),Blocks.STONE);
            h.setBlock(new BlockPos(x,1,3),EnergyRegistries.COPPER_ENERGY_CABLE.get().defaultBlockState()
                    .setValue(CopperEnergyCableBlock.FACES.get(Direction.UP),true));
        }
        TestConsumer consumer=place(h,EnergyValidation.CONSUMER.get(),new BlockPos(6,1,3));consumer.demand=5;
        h.runAfterDelay(80,()->{
            var networks=EnergyNetworks.get(h.getLevel()).networksOfMachine(battery.getBlockPos());
            check(h,networks.size()==1 && networks.getFirst().storageCount()==1,"Passive cells counted as several batteries");
            check(h,networks.getFirst().capacity()==320000,"Network capacity duplicated");
            check(h,consumer.received>0 && battery.stored()+consumer.received==5000,"Transfer through passive cells lost or duplicated HE");
            h.succeed();
        });
    }

    @GameTest(template="empty",batch="battery_base_migration",timeoutTicks=120)
    public static void legacyBatteryKeepsEnergyAndIdentityWithoutOverwriting(GameTestHelper h) {
        var level=h.getLevel();var block=EnergyRegistries.BATTERY_3.get();var pos=h.absolutePos(new BlockPos(2,2,2));
        level.setBlockAndUpdate(pos,block.defaultBlockState());
        var battery=(BatteryBlockEntity)level.getBlockEntity(pos);var id=battery.deviceId();
        var tag=battery.saveWithFullMetadata(level.registryAccess());tag.remove("base_version");tag.putLong("energy",123456);
        battery.loadWithComponents(tag,level.registryAccess());
        var obstacle=pos.east().south();level.setBlockAndUpdate(obstacle,Blocks.DIAMOND_BLOCK.defaultBlockState());
        h.runAfterDelay(30,()->{
            check(h,level.getBlockState(obstacle).is(Blocks.DIAMOND_BLOCK) && level.getBlockState(pos.east()).isAir(),"Legacy expansion overwrote or partially placed");
            check(h,!battery.baseComplete() && battery.port(null).extract(500,false)==0 && battery.port(null).insert(500,false)==0,"Incomplete battery transferred energy");
            check(h,battery.stored()==123456 && battery.deviceId().equals(id),"Legacy save lost charge/UUID");
            level.removeBlock(obstacle,false);
            h.runAfterDelay(30,()->{
                check(h,battery.baseComplete() && battery.stored()==123456 && battery.deviceId().equals(id),"Expansion changed charge/identity");
                var saved=battery.saveWithFullMetadata(level.registryAccess());
                check(h,saved.getInt("base_version")==1,"Migration not persisted");
                var copy=(BatteryBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos,battery.getBlockState(),saved,level.registryAccess());
                check(h,copy!=null && copy.stored()==123456 && copy.deviceId().equals(id),"Reload lost charge/UUID");
                battery.onChunkUnloaded();
                check(h,battery.port(null).extract(1,false)==0 && battery.port(null).insert(1,false)==0,"Unloaded battery still transfers");
                battery.onLoad();
                check(h,battery.port(null).extract(1000,false)==128 && battery.stored()==123328,"Reload duplicated or lost transfer budget");
                h.succeed();
            });
        });
    }
}
