package fr.lkdm.homelink.energy.homelink;

import java.util.ArrayList;
import java.util.List;

/**
 * Battery alarms with hysteresis. Each alarm fires once when its condition is reached and re-arms
 * only after the charge has clearly moved away: low fires under the threshold and re-arms at the
 * re-arm level; empty re-arms above 5 %; full re-arms under 95 %. The first observation after
 * loading only records the state, so reloading a world never repeats an alarm.
 */
public final class BatteryAlarms {
    public enum Alarm { LOW, EMPTY, FULL }

    public static final double EMPTY_REARM = 5;
    public static final double FULL_REARM = 95;

    private boolean observed;
    private boolean lowArmed;
    private boolean emptyArmed;
    private boolean fullArmed;

    /**
     * @param stored stored energy
     * @param capacity battery capacity
     * @param lowThreshold low alarm below this percentage
     * @param lowRearm percentage at which the low alarm re-arms
     * @return alarms raised by this observation
     */
    public List<Alarm> observe(long stored, long capacity, int lowThreshold, int lowRearm) {
        double percent = capacity <= 0 ? 0 : stored * 100.0 / capacity;
        List<Alarm> raised = new ArrayList<>(1);
        if (!observed) {
            observed = true;
            lowArmed = percent >= lowThreshold;
            emptyArmed = stored > 0;
            fullArmed = stored < capacity;
            return raised;
        }
        if (lowArmed && percent < lowThreshold) {
            lowArmed = false;
            raised.add(Alarm.LOW);
        } else if (!lowArmed && percent >= Math.max(lowRearm, lowThreshold)) {
            lowArmed = true;
        }
        if (emptyArmed && stored <= 0) {
            emptyArmed = false;
            raised.add(Alarm.EMPTY);
        } else if (!emptyArmed && percent >= EMPTY_REARM) {
            emptyArmed = true;
        }
        if (fullArmed && capacity > 0 && stored >= capacity) {
            fullArmed = false;
            raised.add(Alarm.FULL);
        } else if (!fullArmed && percent <= FULL_REARM) {
            fullArmed = true;
        }
        return raised;
    }
}
