package fr.lkdm.homelink.energy.energy;

import java.util.function.LongSupplier;

/**
 * Bounded HE store: {@code 0 <= stored <= capacity} at all times, integer amounts only,
 * no overflow. Optional per-tick limits cap what enters and what leaves during one game tick,
 * measured with the owner's clock. Every accepted or extracted amount is returned exactly.
 */
public final class EnergyStore {
    private long stored;
    private long capacity;
    private long maxInPerTick = Long.MAX_VALUE;
    private long maxOutPerTick = Long.MAX_VALUE;
    private final LongSupplier clock;
    private long windowTick = Long.MIN_VALUE;
    private long inThisTick;
    private long outThisTick;

    /**
     * @param capacity maximum stored energy
     * @param clock current game tick, used for the per-tick limits
     */
    public EnergyStore(long capacity, LongSupplier clock) {
        this.capacity = Math.max(0, capacity);
        this.clock = clock;
    }

    /** @return stored HE */
    public long stored() { return stored; }

    /** @return capacity in HE */
    public long capacity() { return capacity; }

    /** @return free space in HE */
    public long space() { return capacity - stored; }

    /** @return whether no more energy fits */
    public boolean isFull() { return stored >= capacity; }

    /**
     * Changes the capacity, for instance after a configuration reload; excess energy is removed.
     *
     * @param value new capacity, clamped to zero or more
     */
    public void setCapacity(long value) {
        capacity = Math.max(0, value);
        stored = Math.min(stored, capacity);
    }

    /**
     * Sets both per-tick limits.
     *
     * @param maxIn most HE accepted per tick
     * @param maxOut most HE extracted per tick
     */
    public void setRates(long maxIn, long maxOut) {
        maxInPerTick = Math.max(0, maxIn);
        maxOutPerTick = Math.max(0, maxOut);
    }

    /** @return most HE accepted per tick */
    public long maxInPerTick() { return maxInPerTick; }

    /** @return most HE extracted per tick */
    public long maxOutPerTick() { return maxOutPerTick; }

    /**
     * Restores saved energy, clamped to {@code [0, capacity]}.
     *
     * @param value saved energy
     */
    public void setStored(long value) {
        stored = Math.max(0, Math.min(value, capacity));
    }

    private void roll() {
        long now = clock.getAsLong();
        if (now != windowTick) {
            windowTick = now;
            inThisTick = 0;
            outThisTick = 0;
        }
    }

    /**
     * Adds energy, within the free space and the per-tick input limit.
     *
     * @param amount offered energy
     * @param simulate whether to only compute the accepted amount
     * @return accepted energy, from zero to {@code amount}
     */
    public long receive(long amount, boolean simulate) {
        if (amount <= 0) return 0;
        roll();
        long accepted = Math.min(amount, Math.min(space(), maxInPerTick - inThisTick));
        if (accepted <= 0) return 0;
        if (!simulate) {
            stored += accepted;
            inThisTick += accepted;
        }
        return accepted;
    }

    /**
     * Removes energy, within the stored amount and the per-tick output limit.
     *
     * @param amount requested energy
     * @param simulate whether to only compute the extracted amount
     * @return extracted energy, from zero to {@code amount}
     */
    public long extract(long amount, boolean simulate) {
        if (amount <= 0) return 0;
        roll();
        long extracted = Math.min(amount, Math.min(stored, maxOutPerTick - outThisTick));
        if (extracted <= 0) return 0;
        if (!simulate) {
            stored -= extracted;
            outThisTick += extracted;
        }
        return extracted;
    }
}
