package fr.lkdm.homelink.energy.energy;

import fr.lkdm.homecore.api.energy.EnergyPort;
import fr.lkdm.homecore.api.energy.EnergyPortType;
import fr.lkdm.homecore.api.energy.EnergyRole;

/**
 * HomeLink Energy's own ports: the shared HomeCore {@link EnergyPort} contract plus the
 * source attribution used by network statistics. Machines of other mods implement
 * {@link EnergyPort} directly and are counted as {@link EnergySourceType#OTHER}.
 * Server thread only; see {@link EnergyPort} for the transfer contract.
 */
public interface HePort extends EnergyPort {
    default EnergySourceType sourceType() { return EnergySourceType.OTHER; }
    @Override default EnergyRole role() { return EnergyRole.STORAGE; }
    @Override default EnergyPortType type() { return EnergyPortType.BOTH; }

    /** @return the source attribution of any port, {@link EnergySourceType#OTHER} for foreign ones */
    static EnergySourceType sourceOf(EnergyPort port) {
        return port instanceof HePort he ? he.sourceType() : EnergySourceType.OTHER;
    }
}
