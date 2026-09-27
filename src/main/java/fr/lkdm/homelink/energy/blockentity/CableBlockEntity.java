package fr.lkdm.homelink.energy.blockentity;

import fr.lkdm.homelink.energy.network.EnergyNetworks;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Presence marker of a loaded cable: it never ticks and holds no energy. It tells the level's
 * energy networks when the cable appears (placed, chunk loaded) or disappears (broken, chunk
 * unloaded), which is what triggers a graph rebuild.
 */
public final class CableBlockEntity extends BlockEntity {
    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyRegistries.CABLE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) EnergyNetworks.get(server).addCable(worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) EnergyNetworks.existing(server).ifPresent(networks -> networks.removeCable(worldPosition));
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) EnergyNetworks.existing(server).ifPresent(networks -> networks.removeCable(worldPosition));
    }
}
