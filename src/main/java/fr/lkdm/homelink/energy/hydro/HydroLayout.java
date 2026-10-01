package fr.lkdm.homelink.energy.hydro;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Local frame shared by placement, hydraulic routing, HE capabilities, models, renderer and tests.
 *
 * <p>A cell is (column, layer, row): column grows toward {@code facing.getClockWise()}, layer grows
 * upward, row grows toward {@code facing.getOpposite()}. The master is (0, 0, 0). In the north-facing
 * model space, column is +X, layer is +Y and row is +Z; the front is the Z = 0 side.</p>
 *
 * <ul>
 *   <li>Pump: FACING is the intake, toward the water. Hydraulic outlet = top (UP) of the master cell.
 *       No HE port.</li>
 *   <li>Turbine 2x2x2: FACING is the front (screen, louvers, discharge). Hydro inlet = top (UP) of
 *       cell (0, 1, 1), the rear upper-left cell. HE output = clockwise side of cell (1, 0, 1), the rear
 *       lower-right cell, marked by a copper connector. Discharge = the two cells in front of
 *       (0, 0, 0) and (1, 0, 0). Rear grille = back face of row 1; it is never a port.</li>
 * </ul>
 */
public final class HydroLayout {
    public static final int TURBINE_SIZE = 2;
    public static final int[] TURBINE_INLET = {0, 1, 1};
    public static final int[] TURBINE_OUTPUT = {1, 0, 1};
    public static final int[] PUMP_OUTLET = {0, 0, 0};

    private HydroLayout() { }

    /** @return world position of a local cell */
    public static BlockPos cell(BlockPos origin, Direction facing, int column, int layer, int row) {
        return origin.relative(facing.getClockWise(), column).relative(facing.getOpposite(), row).above(layer);
    }

    public static BlockPos cell(BlockPos origin, Direction facing, int[] local) {
        return cell(origin, facing, local[0], local[1], local[2]);
    }

    /** @return master position of a part */
    public static BlockPos origin(BlockPos part, Direction facing, int column, int layer, int row) {
        return part.relative(facing.getClockWise(), -column).relative(facing.getOpposite(), -row).below(layer);
    }

    public static BlockPos turbineInlet(BlockPos origin, Direction facing) { return cell(origin, facing, TURBINE_INLET); }
    public static Direction turbineInletFace() { return Direction.UP; }
    public static BlockPos turbineOutput(BlockPos origin, Direction facing) { return cell(origin, facing, TURBINE_OUTPUT); }
    public static Direction turbineOutputFace(Direction facing) { return facing.getClockWise(); }
    public static BlockPos pumpOutlet(BlockPos origin) { return origin; }
    public static Direction pumpOutletFace() { return Direction.UP; }

    /** @return the two cells that must stay clear in front of the lower discharge opening */
    public static List<BlockPos> turbineDischarge(BlockPos origin, Direction facing) {
        return List.of(origin.relative(facing), origin.relative(facing.getClockWise()).relative(facing));
    }
}
