package fr.lkdm.homelink.energy.menu;

import net.minecraft.world.inventory.ContainerData;

/**
 * Display values of a screen. Vanilla container data travels as 16-bit values, so each 32-bit
 * value uses two slots. The server writes a snapshot; the client only reads what it receives.
 */
public final class MenuData implements ContainerData {
    private final int[] slots;

    /** @param values number of 32-bit values */
    public MenuData(int values) {
        slots = new int[values * 2];
    }

    /**
     * Stores one 32-bit value (server side).
     *
     * @param index value index
     * @param value new value
     */
    public void put(int index, int value) {
        slots[index * 2] = value & 0xFFFF;
        slots[index * 2 + 1] = value >>> 16;
    }

    /**
     * Reads one 32-bit value.
     *
     * @param data synchronized container data
     * @param index value index
     * @return the value
     */
    public static int value(ContainerData data, int index) {
        return (data.get(index * 2 + 1) & 0xFFFF) << 16 | data.get(index * 2) & 0xFFFF;
    }

    @Override public int get(int index) { return slots[index]; }
    @Override public void set(int index, int value) { slots[index] = value & 0xFFFF; }
    @Override public int getCount() { return slots.length; }
}
