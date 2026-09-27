package fr.lkdm.homelink.energy.verification;

import static fr.lkdm.homelink.energy.verification.EnergyValidation.check;
import static fr.lkdm.homelink.energy.verification.EnergyValidation.place;
import static fr.lkdm.homelink.energy.verification.EnergyValidation.world;

import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.blockentity.BatteryBlockEntity;
import fr.lkdm.homelink.energy.blockentity.SolarPanelBlockEntity;
import fr.lkdm.homelink.energy.energy.SolarGenerator;
import fr.lkdm.homelink.energy.energy.SolarStatus;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Part 1: production in the real world, direct panel to battery transfer, persistence and safety. */
@GameTestHolder(EnergyValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SolarGameTests {
    private static final BlockPos BATTERY = new BlockPos(1, 1, 1);
    private static final BlockPos PANEL = new BlockPos(1, 2, 1);

    private static void noonClear(GameTestHelper helper) { world(helper, 6_000, false, false); }

    /** Every generated HE is either in the panel buffer or in the battery below. */
    private static void conserved(GameTestHelper helper, SolarPanelBlockEntity panel, BatteryBlockEntity battery) {
        check(helper, panel.core().generated() == panel.core().buffer().stored() + battery.stored(),
                "Energy not conserved: generated " + panel.core().generated() + ", buffer " + panel.core().buffer().stored() + ", battery " + battery.stored());
        check(helper, panel.exported() == battery.stored(), "Exported " + panel.exported() + " but the battery holds " + battery.stored());
    }

    @GameTest(template = "empty", batch = "noon_a", timeoutTicks = 200)
    public static void panelChargesTheBatteryBelow(GameTestHelper helper) {
        noonClear(helper);
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_1.get(), BATTERY);
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_3.get(), PANEL);
        check(helper, battery.stored() == 0, "A new battery starts empty");
        check(helper, helper.getLevel().getCapability(HeCapabilities.PORT, helper.absolutePos(PANEL), Direction.UP) == null, "The solar surface is not a port");
        check(helper, helper.getLevel().getCapability(HeCapabilities.PORT, helper.absolutePos(PANEL), Direction.DOWN) != null, "No ENERGY_OUTPUT below the panel");
        helper.runAfterDelay(100, () -> {
            double rate = SolarGenerator.potential(20_000, 6_000, 1.0);
            check(helper, panel.status() == SolarStatus.GENERATING, "Status " + panel.status());
            check(helper, battery.stored() > 0, "The battery was not charged");
            long generated = panel.core().generated();
            check(helper, generated >= Math.floor(rate * 95) && generated <= Math.ceil(rate * 101), "Solar III made " + generated + " HE in ~100 ticks at " + rate + " HE/t");
            conserved(helper, panel, battery);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "noon_a", timeoutTicks = 300)
    public static void tiersFollowTheirRatedOutput(GameTestHelper helper) {
        noonClear(helper);
        SolarPanelBlockEntity[] panels = new SolarPanelBlockEntity[3];
        BatteryBlockEntity[] batteries = new BatteryBlockEntity[3];
        for (int i = 0; i < 3; i++) {
            batteries[i] = place(helper, EnergyRegistries.BATTERY_1.get(), new BlockPos(i * 2, 1, 0));
            panels[i] = place(helper, (i == 0 ? EnergyRegistries.SOLAR_PANEL_1 : i == 1 ? EnergyRegistries.SOLAR_PANEL_2 : EnergyRegistries.SOLAR_PANEL_3).get(), new BlockPos(i * 2, 2, 0));
        }
        helper.runAfterDelay(240, () -> {
            long[] made = {panels[0].core().generated(), panels[1].core().generated(), panels[2].core().generated()};
            check(helper, made[0] > 0, "Solar Panel I never completed a whole HE: fractions lost");
            double ratio2 = made[1] / (double) made[0], ratio3 = made[2] / (double) made[0];
            check(helper, Math.abs(ratio2 - 4) < 0.35 && Math.abs(ratio3 - 10) < 0.6, "Ratios " + ratio2 + " and " + ratio3 + " (expected 4 and 10) from " + made[0] + "/" + made[1] + "/" + made[2]);
            for (int i = 0; i < 3; i++) conserved(helper, panels[i], batteries[i]);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "noon_a", timeoutTicks = 200)
    public static void glassBlocksTheSkyUntilRemoved(GameTestHelper helper) {
        noonClear(helper);
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_3.get(), PANEL);
        BlockPos glass = PANEL.above(4);
        helper.setBlock(glass, Blocks.GLASS);
        helper.runAfterDelay(50, () -> {
            check(helper, panel.status() == SolarStatus.SKY_BLOCKED, "Glass four blocks above must block: " + panel.status());
            check(helper, panel.core().generated() == 0, "Produced under glass");
            helper.setBlock(glass, Blocks.AIR);
            helper.runAfterDelay(60, () -> {
                check(helper, panel.status() == SolarStatus.GENERATING, "Periodic check did not see the open sky: " + panel.status());
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = "noon_a", timeoutTicks = 200)
    public static void onlyTheTopPanelOfAStackProduces(GameTestHelper helper) {
        noonClear(helper);
        SolarPanelBlockEntity lower = place(helper, EnergyRegistries.SOLAR_PANEL_3.get(), new BlockPos(1, 1, 1));
        SolarPanelBlockEntity upper = place(helper, EnergyRegistries.SOLAR_PANEL_3.get(), new BlockPos(1, 2, 1));
        helper.runAfterDelay(60, () -> {
            check(helper, lower.status() == SolarStatus.SKY_BLOCKED && lower.core().generated() == 0, "Covered panel produced: " + lower.status());
            check(helper, upper.status().producing() || upper.status() == SolarStatus.BUFFER_FULL, "Top panel: " + upper.status());
            check(helper, upper.core().generated() > 0, "Top panel produced nothing");
            check(helper, lower.core().buffer().stored() == 0, "A panel is not a battery: the lower panel must not accept energy");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "noon_a", timeoutTicks = 200)
    public static void fullBatteryKeepsTheBufferAndBoundsIt(GameTestHelper helper) {
        noonClear(helper);
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_1.get(), BATTERY);
        EnergyValidation.charge(helper, battery, battery.capacity());
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_3.get(), PANEL);
        panel.core().buffer().setStored(panel.core().buffer().capacity() - 5);
        helper.runAfterDelay(40, () -> {
            check(helper, battery.stored() == battery.capacity(), "Battery over capacity: " + battery.stored());
            check(helper, panel.core().buffer().stored() == panel.core().buffer().capacity(), "Buffer should be full");
            check(helper, panel.status() == SolarStatus.BUFFER_FULL, "Status " + panel.status());
            check(helper, panel.core().lost() > 0, "Excess production must be discarded, not kept");
            check(helper, panel.core().generator().fraction() < 1, "Hidden reserve");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "night", timeoutTicks = 120)
    public static void nightProducesNothing(GameTestHelper helper) {
        world(helper, 18_000, false, false);
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_1.get(), BATTERY);
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_3.get(), PANEL);
        helper.runAfterDelay(60, () -> {
            check(helper, panel.status() == SolarStatus.NIGHT, "Status " + panel.status());
            check(helper, panel.core().generated() == 0 && battery.stored() == 0, "Produced at night");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "rain", timeoutTicks = 120)
    public static void rainGivesFortyPercent(GameTestHelper helper) {
        world(helper, 6_000, true, false);
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_3.get(), PANEL);
        helper.runAfterDelay(20, () -> {
            double clear = SolarGenerator.potential(20_000, 6_000, 1.0);
            check(helper, Math.abs(panel.core().efficiency() - 0.40) < 1e-9, "Rain efficiency " + panel.core().efficiency());
            check(helper, Math.abs(panel.core().potentialRate() - clear * 0.40) < 1e-9, "Rain rate " + panel.core().potentialRate());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "thunder", timeoutTicks = 120)
    public static void thunderGivesFifteenPercent(GameTestHelper helper) {
        world(helper, 6_000, true, true);
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_3.get(), PANEL);
        helper.runAfterDelay(20, () -> {
            double clear = SolarGenerator.potential(20_000, 6_000, 1.0);
            check(helper, Math.abs(panel.core().efficiency() - 0.15) < 1e-9, "Thunder replaces rain, efficiency " + panel.core().efficiency());
            check(helper, Math.abs(panel.core().potentialRate() - clear * 0.15) < 1e-9, "Thunder rate " + panel.core().potentialRate());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "jump", timeoutTicks = 120)
    public static void timeJumpsAreNotCaughtUp(GameTestHelper helper) {
        world(helper, 6_000, false, false);
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_3.get(), PANEL);
        helper.runAfterDelay(20, () -> {
            long before = panel.core().generated();
            // Three whole days pass at once (sleep, /time add): only executed ticks may produce.
            helper.getLevel().setDayTime(helper.getLevel().getDayTime() + 3 * 24_000L);
            helper.runAfterDelay(2, () -> {
                long gained = panel.core().generated() - before;
                check(helper, gained <= 6, "Skipped days were produced retroactively: +" + gained + " HE");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = "dimension", timeoutTicks = 120)
    public static void otherDimensionsProduceNothing(GameTestHelper helper) {
        var end = helper.getLevel().getServer().getLevel(Level.END);
        check(helper, end != null, "No End dimension");
        BlockPos pos = new BlockPos(8, 120, 8);
        ChunkPos chunk = new ChunkPos(pos);
        end.setChunkForced(chunk.x, chunk.z, true);
        end.setDayTime(6_000);
        end.setBlockAndUpdate(pos, EnergyRegistries.SOLAR_PANEL_3.get().defaultBlockState());
        helper.runAfterDelay(1, () -> {
            try {
                check(helper, end.getBlockEntity(pos) instanceof SolarPanelBlockEntity, "Panel not placed in the End");
                SolarPanelBlockEntity panel = (SolarPanelBlockEntity) end.getBlockEntity(pos);
                // Runs the real server tick of the panel in the End, independently of chunk ticking.
                for (int i = 0; i < 200; i++) SolarPanelBlockEntity.serverTick(end, pos, panel.getBlockState(), panel);
                check(helper, panel.status() == SolarStatus.UNSUPPORTED_DIMENSION, "End status " + panel.status());
                check(helper, panel.core().generated() == 0, "Produced in the End");
                helper.succeed();
            } finally {
                end.removeBlock(pos, false);
                end.setChunkForced(chunk.x, chunk.z, false);
            }
        });
    }

    @GameTest(template = "empty", batch = "persistence", timeoutTicks = 100)
    public static void saveAndReloadKeepExactlyTheSameEnergy(GameTestHelper helper) {
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_1.get(), BATTERY);
        EnergyValidation.charge(helper, battery, 10_000);
        long stored = battery.stored();
        check(helper, stored == 10_000, "Charged " + stored);
        var registries = helper.getLevel().registryAccess();
        var tag = battery.saveWithFullMetadata(registries);
        BlockEntity reloaded = BlockEntity.loadStatic(helper.absolutePos(BATTERY), battery.getBlockState(), tag, registries);
        check(helper, reloaded instanceof BatteryBlockEntity copy && copy.stored() == stored, "Reload changed the energy");
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_1.get(), PANEL);
        panel.core().buffer().setStored(321);
        panel.core().generator().setFraction(0.75);
        BlockEntity panelCopy = BlockEntity.loadStatic(helper.absolutePos(PANEL), panel.getBlockState(), panel.saveWithFullMetadata(registries), registries);
        check(helper, panelCopy instanceof SolarPanelBlockEntity copy && copy.core().buffer().stored() == 321
                && Math.abs(copy.core().generator().fraction() - 0.75) < 1e-12, "Panel buffer or fraction not saved");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "persistence", timeoutTicks = 100)
    public static void breakingLosesTheEnergyAndNeverDuplicatesIt(GameTestHelper helper) {
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_1.get(), BATTERY);
        battery.port(null).insert(8, false);
        check(helper, battery.stored() == 8, "Battery I accepts at most 8 HE per tick");
        EnergyValidation.charge(helper, battery, 30_000);
        helper.getLevel().destroyBlock(helper.absolutePos(BATTERY), true);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BATTERY)).inflate(2));
        check(helper, drops.size() == 1 && drops.getFirst().getItem().is(EnergyRegistries.BATTERY_1_ITEM.get()), "Expected exactly one Battery I drop");
        ItemStack drop = drops.getFirst().getItem();
        check(helper, !drop.has(DataComponents.BLOCK_ENTITY_DATA) && !drop.has(DataComponents.CUSTOM_DATA), "The dropped item carries data");
        drops.forEach(ItemEntity::discard);
        BatteryBlockEntity replaced = place(helper, EnergyRegistries.BATTERY_1.get(), BATTERY);
        check(helper, replaced.stored() == 0, "A replaced battery must be empty");
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_2.get(), PANEL);
        panel.core().buffer().setStored(500);
        helper.getLevel().destroyBlock(helper.absolutePos(PANEL), true);
        SolarPanelBlockEntity again = place(helper, EnergyRegistries.SOLAR_PANEL_2.get(), PANEL);
        check(helper, again.core().buffer().stored() == 0 && again.core().generated() == 0, "A replaced panel must start empty");
        check(helper, panel.isRemoved(), "Old panel still active");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "recipes", timeoutTicks = 40)
    public static void recipesFollowTheProgression(GameTestHelper helper) {
        check(helper, uses(helper, "solar_panel_1", "homecore:homelink_circuit_board"), "Panel I needs a Circuit Board");
        check(helper, uses(helper, "solar_panel_2", "homelink_energy:solar_panel_1"), "Panel II needs Panel I");
        check(helper, uses(helper, "solar_panel_2", "homecore:homelink_microprocessor") && uses(helper, "solar_panel_2", "homecore:homelink_communication_module"), "Panel II components");
        check(helper, uses(helper, "solar_panel_3", "homelink_energy:solar_panel_2") && uses(helper, "solar_panel_3", "minecraft:diamond"), "Panel III needs Panel II and diamonds");
        check(helper, uses(helper, "solar_panel_3", "homecore:homelink_control_module"), "Panel III needs a Control Module");
        check(helper, uses(helper, "battery_1", "homecore:homelink_circuit_board"), "Battery I needs a Circuit Board");
        check(helper, !uses(helper, "solar_panel_1", "minecraft:coal"), "No coal anywhere");
        helper.succeed();
    }

    private static boolean uses(GameTestHelper helper, String recipe, String item) {
        Recipe<?> value = helper.getLevel().getRecipeManager().byKey(HomeLinkEnergy.id(recipe)).orElseThrow().value();
        ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(item)));
        return value.getIngredients().stream().anyMatch(ingredient -> ingredient.test(stack));
    }
}
