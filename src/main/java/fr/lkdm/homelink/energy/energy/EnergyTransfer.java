package fr.lkdm.homelink.energy.energy;

import fr.lkdm.homecore.api.energy.*;
/**
 * Exact transfer between two conforming HE ports: simulate the offer, simulate the acceptance,
 * extract that amount, then insert exactly what was extracted. The amount leaving the source is
 * the amount entering the target; a port that breaks the contract is reported.
 */
public final class EnergyTransfer {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private EnergyTransfer() { }

    /**
     * Moves up to {@code limit} HE from {@code from} to {@code to}.
     *
     * @param from source port
     * @param to target port
     * @param limit most HE to move
     * @return HE moved, extracted from the source and inserted into the target
     */
    public static long move(EnergyPort from, EnergyPort to, long limit) {
        if (limit <= 0 || from == to || ACTIVE.get() || !from.type().canSend() || !to.type().canReceive()) return 0;
        ACTIVE.set(true);
        try {
            long offered = checked(from.extract(limit, true), limit);
            if (offered == 0) return 0;
            long accepted = checked(to.insert(offered, true), offered);
            if (accepted == 0) return 0;
            long extracted = checked(from.extract(accepted, false), accepted);
            if (extracted == 0) return 0;
            long inserted = checked(to.insert(extracted, false), extracted);
            if (inserted != extracted) throw new IllegalStateException("HE port violated simulation contract");
            return inserted;
        } finally { ACTIVE.remove(); }
    }

    private static long checked(long amount, long requested) {
        if (amount < 0 || amount > requested) throw new IllegalStateException("Invalid HE port amount");
        return amount;
    }
}
