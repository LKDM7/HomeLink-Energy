package fr.lkdm.homelink.energy.energy;

/** Weather seen by a solar panel. A thunderstorm replaces rain instead of adding to it. */
public enum Weather {
    CLEAR, RAIN, THUNDER;

    /**
     * Weather of a world from its global rain and thunder flags.
     *
     * @param raining whether it rains
     * @param thundering whether a thunderstorm is active
     * @return the weather used for the efficiency
     */
    public static Weather of(boolean raining, boolean thundering) {
        if (thundering) return THUNDER;
        return raining ? RAIN : CLEAR;
    }
}
