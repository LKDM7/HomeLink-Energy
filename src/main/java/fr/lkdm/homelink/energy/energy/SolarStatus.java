package fr.lkdm.homelink.energy.energy;

/** Production state of a solar panel, from the most to the least important cause; new states go last because screens receive the ordinal. */
public enum SolarStatus {
    /** Production disabled by configuration (zero HE per cycle). */ DISABLED,
    /** V1 panels only work in the Overworld. */ UNSUPPORTED_DIMENSION,
    /** A non-air block is above the panel. */ SKY_BLOCKED,
    /** The sun is down. */ NIGHT,
    /** The buffer is full: new production is lost. */ BUFFER_FULL,
    /** Producing. */ GENERATING,
    /** Switched off by a player from a dashboard. */ SWITCHED_OFF;

    /** @return whether the panel can produce in this state */
    public boolean producing() { return this == GENERATING; }
}
