package fr.lkdm.homelink.energy.wind;

/** New states go last: screens receive the ordinal. */
public enum WindStatus { DISABLED, UNSUPPORTED_DIMENSION, ROTOR_OBSTRUCTED, SKY_BLOCKED, NO_WIND, BUFFER_FULL, GENERATING, INCOMPLETE_BASE, SWITCHED_OFF }
