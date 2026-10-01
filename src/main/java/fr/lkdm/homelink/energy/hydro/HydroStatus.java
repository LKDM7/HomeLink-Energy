package fr.lkdm.homelink.energy.hydro;

/** Displayed states. Screens receive ordinals: new values go last. */
public final class HydroStatus {
    private HydroStatus() { }

    public enum Pump {
        DISABLED, INCOMPLETE_STRUCTURE, UNSUPPORTED_DIMENSION, NO_WATER, WATER_INSUFFICIENT, WATER_UNKNOWN,
        NO_TURBINE, NETWORK_PENDING, NETWORK_INVALID, STANDBY, PUMPING, CONFIG_DISABLED;

        /** @return whether the water condition allows pumping */
        public boolean waterOk() { return this != NO_WATER && this != WATER_INSUFFICIENT && this != WATER_UNKNOWN; }
    }

    public enum Turbine {
        DISABLED, INCOMPLETE_STRUCTURE, UNSUPPORTED_DIMENSION, NO_PUMP, NO_FLOW, NETWORK_PENDING, NETWORK_INCOMPLETE,
        TOO_MANY_PUMPS, MULTIPLE_TURBINES, NETWORK_TOO_LARGE, OUTLET_BLOCKED, GENERATING, BUFFER_FULL, CONFIG_DISABLED;

        /** @return whether the turbine is converting flow */
        public boolean producing() { return this == GENERATING || this == BUFFER_FULL; }

        /** @return whether the cause is the hydraulic circuit */
        public boolean circuitFault() {
            return this == NETWORK_INCOMPLETE || this == TOO_MANY_PUMPS || this == MULTIPLE_TURBINES || this == NETWORK_TOO_LARGE;
        }
    }

    /** Validity of a built circuit; pump and turbine counts are read separately. */
    public enum Circuit { VALID, INCOMPLETE, TOO_LARGE, TOO_MANY_PUMPS, MULTIPLE_TURBINES }
}
