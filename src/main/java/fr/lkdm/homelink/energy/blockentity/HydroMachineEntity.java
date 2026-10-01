package fr.lkdm.homelink.energy.blockentity;

/** Master of a Hydro multiblock: told when a block next to one of its cells changed. */
public interface HydroMachineEntity {
    void surroundingsChanged();
}
