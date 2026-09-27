package fr.lkdm.homelink.energy.homelink;

import fr.lkdm.homelink.energy.energy.SolarStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Solar panel events on meaningful transitions only. Sunrise and sunset are normal and never
 * reported: a panel "stops generating" when something other than the night stops it (sky blocked,
 * full buffer, dimension, configuration) and "starts generating" when it recovers from such a
 * cause. Each event also has a cooldown, so a flickering cause cannot flood the event bus.
 */
public final class SolarAlarms {
    public enum Alarm { STARTED, STOPPED, SKY_BLOCKED }

    /** Minimum game ticks between two events of the same kind for one panel. */
    public static final long COOLDOWN = 1_200;

    private boolean observed;
    private SolarStatus last;
    private final Map<Alarm, Long> lastSent = new EnumMap<>(Alarm.class);

    /**
     * @param status current state
     * @param gameTime current game tick
     * @return events raised by this observation
     */
    public List<Alarm> observe(SolarStatus status, long gameTime) {
        List<Alarm> raised = new ArrayList<>(2);
        if (!observed) {
            observed = true;
            last = status;
            return raised;
        }
        if (status != last) {
            boolean wasGenerating = last == SolarStatus.GENERATING;
            boolean generating = status == SolarStatus.GENERATING;
            if (!wasGenerating && generating && last != SolarStatus.NIGHT) offer(raised, Alarm.STARTED, gameTime);
            if (wasGenerating && !generating && status != SolarStatus.NIGHT) offer(raised, Alarm.STOPPED, gameTime);
            if (status == SolarStatus.SKY_BLOCKED) offer(raised, Alarm.SKY_BLOCKED, gameTime);
            last = status;
        }
        return raised;
    }

    private void offer(List<Alarm> raised, Alarm alarm, long gameTime) {
        Long previous = lastSent.get(alarm);
        if (previous != null && gameTime - previous < COOLDOWN) return;
        lastSent.put(alarm, gameTime);
        raised.add(alarm);
    }
}
