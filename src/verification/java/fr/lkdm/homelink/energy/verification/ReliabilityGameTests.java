package fr.lkdm.homelink.energy.verification;

import static fr.lkdm.homelink.energy.verification.EnergyValidation.*;
import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.blockentity.BatteryBlockEntity;
import fr.lkdm.homelink.energy.blockentity.SolarPanelBlockEntity;
import fr.lkdm.homelink.energy.energy.SolarStatus;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;

@GameTestHolder(EnergyValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReliabilityGameTests {
    @GameTest(template="empty", batch="reliability", timeoutTicks=150)
    public static void stalePortsAndDuplicateTicksCannotCreateEnergy(GameTestHelper h) {
        world(h, 6000, false, false);
        BatteryBlockEntity battery = place(h, EnergyRegistries.BATTERY_1.get(), new BlockPos(1,1,1));
        SolarPanelBlockEntity panel = place(h, EnergyRegistries.SOLAR_PANEL_1.get(), new BlockPos(1,2,1));
        h.runAfterDelay(20, () -> {
            SolarPanelBlockEntity.serverTick(h.getLevel(), panel.getBlockPos(), panel.getBlockState(), panel);
            long made = panel.core().generated();
            double fraction = panel.core().generator().fraction();
            for (int i=0;i<100;i++) SolarPanelBlockEntity.serverTick(h.getLevel(), panel.getBlockPos(), panel.getBlockState(), panel);
            check(h, made == panel.core().generated() && fraction == panel.core().generator().fraction(), "Duplicate tick produced energy");
            var stale = battery.port(Direction.UP);
            h.setBlock(new BlockPos(1,1,1), Blocks.AIR);
            check(h, stale.insert(1, false)==0 && stale.extract(1,false)==0, "Removed block retained a live port");
            h.runAfterDelay(30, () -> {
                check(h, panel.core().buffer().stored()>0, "Absent battery must leave energy in buffer");
                BatteryBlockEntity next = place(h, EnergyRegistries.BATTERY_1.get(), new BlockPos(1,1,1));
                h.runAfterDelay(30, () -> {
                    check(h, next.stored()>0, "Cached receiver did not invalidate after replacement");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template="empty", batch="unload", timeoutTicks=150)
    public static void unloadLifecycleAndNbtRoundTripHaveNoCatchup(GameTestHelper h) {
        world(h, 6000, false, false);
        var pos = new BlockPos(1,2,1);
        SolarPanelBlockEntity panel = place(h, EnergyRegistries.SOLAR_PANEL_1.get(), pos);
        h.runAfterDelay(20, () -> {
            var registry = h.getLevel().registryAccess();
            var saved = panel.saveWithFullMetadata(registry);
            long made = panel.core().generated();
            panel.onChunkUnloaded();
            h.setBlock(pos, Blocks.AIR);
            h.getLevel().setDayTime(6000 + 24000L*10);
            for (int i=0;i<100;i++) SolarPanelBlockEntity.serverTick(h.getLevel(), panel.getBlockPos(), panel.getBlockState(), panel);
            check(h, made==panel.core().generated(), "Unloaded instance produced");
            h.runAfterDelay(20, () -> {
                h.setBlock(pos, EnergyRegistries.SOLAR_PANEL_1.get());
                var restored = (SolarPanelBlockEntity) BlockEntity.loadStatic(h.absolutePos(pos), panel.getBlockState(), saved, registry);
                h.getLevel().setBlockEntity(restored);
                h.runAfterDelay(2, () -> {
                    check(h, restored.core().generated()-made<=1, "Reload caught up skipped days");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template="empty", batch="recipes", timeoutTicks=40)
    public static void recipesAssembleExactlyOneEmptyMachine(GameTestHelper h) {
        for (String id : new String[]{"solar_panel_1","solar_panel_2","solar_panel_3","wind_turbine_1","wind_turbine_2","wind_turbine_3","battery_1","battery_2","battery_3","copper_energy_cable"}) {
            var holder = h.getLevel().getRecipeManager().byKey(HomeLinkEnergy.id(id)).orElseThrow();
            var recipe = (ShapedRecipe) holder.value();
            var ingredients = new ArrayList<ItemStack>();
            recipe.getIngredients().forEach(ingredient -> ingredients.add(ingredient.getItems()[0].copy()));
            var input = CraftingInput.of(3,3,ingredients);
            check(h, h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).isPresent(), "Uncraftable " + id);
            var result = recipe.assemble(input,h.getLevel().registryAccess());
            check(h, result.getCount()==(id.equals("copper_energy_cable")?8:1) && !result.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA), "Recipe output/upgrade energy");
        }
        for (String id : new String[]{"homelink_circuit_board","homelink_microprocessor","homelink_communication_module","homelink_control_module"}) {
            check(h, net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("homecore",id)), "Missing real HomeCore component " + id);
        }
        h.succeed();
    }

    @GameTest(template="empty", batch="real_unload", timeoutTicks=2400)
    public static void realChunkUnloadAndDiskReloadNeverCatchUp(GameTestHelper h) {
        world(h,6000,false,false);
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(1600,100,1600));
        var chunk=new net.minecraft.world.level.ChunkPos(pos);
        level.setChunkForced(chunk.x,chunk.z,true); // Test fixture only; production never forces chunks.
        level.setBlockAndUpdate(pos,EnergyRegistries.SOLAR_PANEL_1.get().defaultBlockState());
        var panel=(SolarPanelBlockEntity)level.getBlockEntity(pos);
        h.startSequence().thenWaitUntil(() -> check(h,panel.core().generated()>0,"Waiting for remote chunk to start ticking; status=" + panel.status()))
                .thenExecute(()->{
            level.setChunkForced(chunk.x,chunk.z,false);
            h.runAfterDelay(400,()->{
                check(h,!level.getChunkSource().hasChunk(chunk.x,chunk.z),"Chunk did not actually unload");
                long frozen=panel.core().generated();
                double fraction=panel.core().generator().fraction();
                level.setDayTime(6000+24000L*50);
                h.runAfterDelay(100,()->{
                    check(h,panel.core().generated()==frozen,"Unloaded chunk generated energy");
                    level.setChunkForced(chunk.x,chunk.z,true);
                    var reloaded=(SolarPanelBlockEntity)level.getBlockEntity(pos);
                    try {
                        check(h,reloaded!=null && reloaded!=panel,"Expected a new block entity from saved chunk");
                        check(h,reloaded.core().generated()==frozen && reloaded.core().generator().fraction()==fraction,"Disk reload changed counters/fraction");
                        SolarPanelBlockEntity.serverTick(level,pos,reloaded.getBlockState(),reloaded);
                        check(h,reloaded.core().generated()-frozen<=1,"Reload produced skipped days");
                        h.succeed();
                    } finally {
                        level.removeBlock(pos,false);
                        level.setChunkForced(chunk.x,chunk.z,false);
                    }
                });
            });
        });
    }

    @GameTest(template="load", batch="load", skyAccess=true, timeoutTicks=700)
    public static void hundredPanelsWithRealServerTicks(GameTestHelper h) {
        world(h,6000,false,false);
        var panels = new ArrayList<SolarPanelBlockEntity>();
        var batteries = new ArrayList<BatteryBlockEntity>();
        for (int x=1;x<=10;x++) for (int z=1;z<=10;z++) {
            batteries.add(place(h,EnergyRegistries.BATTERY_1.get(),new BlockPos(x*3,1,z*3)));
            panels.add(place(h,EnergyRegistries.SOLAR_PANEL_3.get(),new BlockPos(x*3,2,z*3)));
        }
        long started=System.nanoTime();
        h.runAfterDelay(600, () -> {
            long ticks=0,nanos=0,total=0;
            for (int i=0;i<100;i++) {
                var p=panels.get(i); var b=batteries.get(i);
                check(h,p.status()==SolarStatus.GENERATING && b.stored()>1400,"Inactive load fixture " + i + " status=" + p.status() + " stored=" + b.stored() + " ticks=" + p.measuredTicks() + " pos=" + p.getBlockPos() + " height=" + h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,p.getBlockPos().getX(),p.getBlockPos().getZ()));
                check(h,p.core().generated()==b.stored()+p.core().buffer().stored(),"Load test conservation " + i);
                ticks+=p.measuredTicks(); nanos+=p.measuredNanos(); total+=b.stored();
            }
            check(h,ticks>=59000,"Insufficient measured ticks: "+ticks);
            HomeLinkEnergy.LOGGER.info("ENERGY_LOAD_OK panels=100 batteries=100 frozenNoon=true clear=true elapsedMs={} panelTicks={} panelCpuMs={} meanMicrosecondsPerPanelTick={} batteryHE={}",
                (System.nanoTime()-started)/1e6,ticks,nanos/1e6,nanos/1e3/ticks,total);
            h.succeed();
        });
    }
}
