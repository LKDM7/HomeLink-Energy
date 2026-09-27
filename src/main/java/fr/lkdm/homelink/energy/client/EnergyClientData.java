package fr.lkdm.homelink.energy.client;

import fr.lkdm.homelink.energy.network.EnergyPayloads;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** HomeNetwork choices received for open screens (display only; the server decides). */
public final class EnergyClientData {
    private static final Map<BlockPos, EnergyPayloads.Choices> CHOICES = new HashMap<>();

    private EnergyClientData() { }

    public static void setChoices(EnergyPayloads.Choices choices) {
        if (CHOICES.size() > 64) CHOICES.clear();
        CHOICES.put(choices.pos().immutable(), choices);
    }

    public static EnergyPayloads.Choices choices(BlockPos pos) {
        return CHOICES.getOrDefault(pos, new EnergyPayloads.Choices(pos, "", java.util.List.of()));
    }
}
