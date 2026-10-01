package fr.lkdm.homelink.energy.energy;

/**
 * Categories for transport telemetry; unrelated to units or separate networks. Ordinals are never
 * saved, but new categories still go last so that arrays indexed by ordinal keep their meaning.
 */
public enum EnergySourceType { SOLAR, WIND, OTHER, HYDRO }
