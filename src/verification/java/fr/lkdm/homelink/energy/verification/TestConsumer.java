package fr.lkdm.homelink.energy.verification;

import fr.lkdm.homecore.api.energy.*;
import fr.lkdm.homelink.energy.energy.HePort;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Development-only ENERGY_INPUT consumer: asks for up to {@link #demand} HE each tick and counts
 * what it really receives. It stands for a machine such as a Quarry in the network tests.
 */
public final class TestConsumer extends BlockEntity {
    long demand = 10;
    long received;
    private long tick = Long.MIN_VALUE;
    private long thisTick;

    public TestConsumer(BlockPos pos, BlockState state) {
        super(EnergyValidation.CONSUMER_ENTITY.get(), pos, state);
    }

    final HePort port = new HePort() {
        @Override public EnergyRole role() { return EnergyRole.CONSUMER; }
        @Override public EnergyPortType type() { return EnergyPortType.INPUT; }
        @Override public long stored() { return 0; }
        @Override public long capacity() { return demand; }
        @Override public long insert(long amount, boolean simulate) {
            long now = level == null ? 0 : level.getGameTime();
            if (now != tick) { tick = now; thisTick = 0; }
            long accepted = Math.max(0, Math.min(amount, demand - thisTick));
            if (!simulate) { thisTick += accepted; received += accepted; }
            return accepted;
        }
        @Override public long extract(long amount, boolean simulate) { return 0; }
    };
}
