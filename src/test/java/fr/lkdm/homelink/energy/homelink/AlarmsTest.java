package fr.lkdm.homelink.energy.homelink;

import static org.junit.jupiter.api.Assertions.*;

import fr.lkdm.homelink.energy.energy.SolarStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlarmsTest {
    private static final long CAPACITY = 100_000;

    private static List<BatteryAlarms.Alarm> at(BatteryAlarms alarms, double percent) {
        return alarms.observe(Math.round(CAPACITY * percent / 100), CAPACITY, 15, 30);
    }

    @Test void lowFiresOnceAndRearmsAtThirtyPercent() {
        var alarms = new BatteryAlarms();
        assertTrue(at(alarms, 50).isEmpty(), "First observation only records");
        assertTrue(at(alarms, 16).isEmpty());
        assertEquals(List.of(BatteryAlarms.Alarm.LOW), at(alarms, 14));
        assertTrue(at(alarms, 13).isEmpty(), "No second BATTERY_LOW");
        assertTrue(at(alarms, 20).isEmpty(), "20 % does not re-arm");
        assertTrue(at(alarms, 14).isEmpty(), "Still disarmed");
        assertTrue(at(alarms, 30).isEmpty(), "Re-armed at 30 %");
        assertEquals(List.of(BatteryAlarms.Alarm.LOW), at(alarms, 14));
    }

    @Test void emptyAndFullHaveHysteresis() {
        var alarms = new BatteryAlarms();
        at(alarms, 50);
        assertEquals(List.of(BatteryAlarms.Alarm.FULL), at(alarms, 100));
        assertTrue(at(alarms, 99).isEmpty());
        assertTrue(at(alarms, 100).isEmpty(), "Topping up by 1 % must not fire again");
        at(alarms, 90);
        assertEquals(List.of(BatteryAlarms.Alarm.FULL), at(alarms, 100));
        assertEquals(List.of(BatteryAlarms.Alarm.LOW, BatteryAlarms.Alarm.EMPTY), at(alarms, 0));
        assertTrue(at(alarms, 1).isEmpty());
        assertTrue(at(alarms, 0).isEmpty(), "Empty again without recharge");
    }

    @Test void reloadingAnEmptyBatteryDoesNotRepeatTheAlarm() {
        var alarms = new BatteryAlarms();
        assertTrue(at(alarms, 0).isEmpty());
        assertTrue(at(alarms, 0).isEmpty());
    }

    @Test void sunriseAndSunsetAreNotEvents() {
        var alarms = new SolarAlarms();
        long t = 0;
        assertTrue(alarms.observe(SolarStatus.NIGHT, t).isEmpty());
        assertTrue(alarms.observe(SolarStatus.GENERATING, t += 5_000).isEmpty(), "Sunrise");
        assertTrue(alarms.observe(SolarStatus.NIGHT, t += 5_000).isEmpty(), "Sunset");
    }

    @Test void obstructionIsReportedWithCooldown() {
        var alarms = new SolarAlarms();
        long t = 0;
        alarms.observe(SolarStatus.GENERATING, t);
        assertEquals(List.of(SolarAlarms.Alarm.STOPPED, SolarAlarms.Alarm.SKY_BLOCKED), alarms.observe(SolarStatus.SKY_BLOCKED, t += 20));
        assertEquals(List.of(SolarAlarms.Alarm.STARTED), alarms.observe(SolarStatus.GENERATING, t += 20));
        assertTrue(alarms.observe(SolarStatus.SKY_BLOCKED, t += 20).isEmpty(), "Flicker within the cooldown");
        assertTrue(alarms.observe(SolarStatus.GENERATING, t += 20).isEmpty());
        assertEquals(List.of(SolarAlarms.Alarm.STOPPED, SolarAlarms.Alarm.SKY_BLOCKED), alarms.observe(SolarStatus.SKY_BLOCKED, t + SolarAlarms.COOLDOWN));
    }
}
