package fr.lkdm.homelink.energy.block;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** Block item with the rated values of its panel or battery. It never carries energy. */
public final class EnergyBlockItem extends BlockItem {
    public EnergyBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (getBlock() instanceof SolarPanelBlock panel) {
            tooltip.add(Component.translatable("tooltip.homelink_energy.footprint", panel.tier().width(), panel.tier().depth()).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.homelink_energy.solar_maximum",
                    Formats.energy(panel.tier().energyPerCycle())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.homelink_energy.solar_conditions").withStyle(ChatFormatting.DARK_GRAY));
        } else if (getBlock() instanceof WindTurbineBlock turbine) {
            tooltip.add(Component.translatable("tooltip.homelink_energy.footprint", turbine.tier().width(), turbine.tier().depth()).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.homelink_energy.wind_nominal", Formats.energy(turbine.tier().nominal())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.homelink_energy.wind_day_night").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.homelink_energy.wind_conditions").withStyle(ChatFormatting.DARK_GRAY));
        } else if (getBlock() instanceof BatteryBlock battery) {
            tooltip.add(Component.translatable("tooltip.homelink_energy.footprint", battery.tier().width(), battery.tier().depth()).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.homelink_energy.battery_capacity",
                    Formats.energy(battery.tier().capacity())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.homelink_energy.battery_rate",
                    Formats.energy(battery.tier().transferRate())).withStyle(ChatFormatting.GRAY));
        } else if (getBlock() instanceof CopperEnergyCableBlock) {
            tooltip.add(Component.translatable("tooltip.homelink_energy.surface_cable").withStyle(ChatFormatting.GRAY));
        }
    }
}
