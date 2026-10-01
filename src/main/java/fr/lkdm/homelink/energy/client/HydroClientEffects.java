package fr.lkdm.homelink.energy.client;

import fr.lkdm.homelink.energy.blockentity.HydroPumpBlockEntity;
import fr.lkdm.homelink.energy.blockentity.HydroTurbineBlockEntity;
import fr.lkdm.homelink.energy.config.HydroClientConfig;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only sounds and discharge particles, driven by the synchronized state. Loops follow their
 * machine and stop when it disappears, goes out of range or stops; start and stop cues play once per
 * transition, never on the first observation and never every tick.
 */
public final class HydroClientEffects {
    private static final double SOUND_RANGE = 24, PARTICLE_RANGE = 32;
    /** The rotor fan is a close-range detail: silent beyond ten blocks. */
    private static final double FAN_RANGE = 10;
    private static final Map<BlockEntity, Loop> PUMP_LOOPS = new WeakHashMap<>();
    private static final Map<BlockEntity, Loop[]> TURBINE_LOOPS = new WeakHashMap<>();
    private static final Map<BlockEntity, Boolean> PRODUCING = new WeakHashMap<>();

    private HydroClientEffects() { }

    public static void install() {
        HydroPumpBlockEntity.clientTicker = HydroClientEffects::pump;
        HydroTurbineBlockEntity.clientTicker = HydroClientEffects::turbine;
    }

    private static double distanceSqr(Vec3 point) {
        var player = Minecraft.getInstance().player;
        return player == null ? Double.MAX_VALUE : player.distanceToSqr(point);
    }

    private static double distanceSqr(BlockEntity entity) {
        var player = Minecraft.getInstance().player;
        return player == null ? Double.MAX_VALUE : player.distanceToSqr(Vec3.atCenterOf(entity.getBlockPos()));
    }

    static void pump(HydroPumpBlockEntity pump) {
        boolean wanted = pump.clientPumping() && HydroClientConfig.sounds() && distanceSqr(pump) < SOUND_RANGE * SOUND_RANGE;
        Loop loop = PUMP_LOOPS.get(pump);
        if (wanted && (loop == null || loop.isStopped())) {
            loop = new Loop(EnergyRegistries.HYDRO_PUMP_SOUND.get(), pump, () -> pump.clientPumping() ? 1 : 0, 0.35f, 0.9f);
            PUMP_LOOPS.put(pump, loop);
            Minecraft.getInstance().getSoundManager().play(loop);
        }
    }

    static void turbine(HydroTurbineBlockEntity turbine) {
        float flow = turbine.clientFlow();
        boolean producing = flow > 0;
        Boolean before = PRODUCING.put(turbine, producing);
        double distance = distanceSqr(turbine);
        boolean audible = HydroClientConfig.sounds() && distance < SOUND_RANGE * SOUND_RANGE;
        if (before != null && before != producing && audible) {
            SoundEvent cue = (producing ? EnergyRegistries.HYDRO_START_SOUND : EnergyRegistries.HYDRO_STOP_SOUND).get();
            Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(cue, SoundSource.BLOCKS, 0.4f * HydroClientConfig.volume(),
                    1f, SoundInstance.createUnseededRandom(), turbine.getBlockPos()));
        }
        Loop[] loops = TURBINE_LOOPS.computeIfAbsent(turbine, key -> new Loop[3]);
        if (producing && audible) {
            if (loops[0] == null || loops[0].isStopped()) {
                loops[0] = new Loop(EnergyRegistries.HYDRO_TURBINE_SOUND.get(), turbine, turbine::clientFlow, 0.45f, 0.6f);
                Minecraft.getInstance().getSoundManager().play(loops[0]);
            }
            if (loops[1] == null || loops[1].isStopped()) {
                loops[1] = new Loop(EnergyRegistries.HYDRO_DISCHARGE_SOUND.get(), turbine, turbine::clientFlow, 0.5f, 1f);
                Minecraft.getInstance().getSoundManager().play(loops[1]);
            }
            Vec3 rotor = local(turbine.getBlockPos(), turbine.facing(), 1.0, 1.0, 1.55);
            if ((loops[2] == null || loops[2].isStopped()) && distanceSqr(rotor) < FAN_RANGE * FAN_RANGE) {
                // Fan whir at the rotor: slow spin-up and coast-down, pitch rising with the rotor speed, 10 % quieter than first tuned.
                loops[2] = new Loop(EnergyRegistries.HYDRO_FAN_SOUND.get(), turbine, turbine::clientFlow, 0.198f, 0.75f, 0.03f, 0.6f, rotor, FAN_RANGE);
                Minecraft.getInstance().getSoundManager().play(loops[2]);
            }
        }
        if (producing && HydroClientConfig.particles() && distance < PARTICLE_RANGE * PARTICLE_RANGE) discharge(turbine, flow);
    }

    /** A few splash particles where the visual sheet falls; bubbles when the discharge cell holds water. */
    private static void discharge(HydroTurbineBlockEntity turbine, float flow) {
        var level = turbine.getLevel();
        if (level == null) return;
        var random = level.random;
        if (random.nextFloat() > 0.3f + 0.5f * flow) return;
        Direction facing = turbine.facing();
        BlockPos origin = turbine.getBlockPos();
        double lateral = 0.15 + random.nextDouble() * 1.7;
        Vec3 point = local(origin, facing, lateral, 0.12, -0.75);
        boolean underwater = level.getBlockState(origin.relative(facing)).is(Blocks.WATER);
        double vx = facing.getStepX() * 0.08, vz = facing.getStepZ() * 0.08;
        level.addParticle(underwater ? ParticleTypes.BUBBLE : ParticleTypes.SPLASH, point.x, point.y, point.z, vx, 0.04, vz);
        if (!underwater && random.nextFloat() < flow) {
            Vec3 lip = local(origin, facing, lateral, 0.32, -0.08);
            level.addParticle(ParticleTypes.FALLING_WATER, lip.x, lip.y, lip.z, 0, 0, 0);
        }
    }

    /**
     * @param x blocks along the columns from the master's left edge
     * @param y blocks above the master's floor
     * @param z blocks toward the rear from the master's front face (negative = in front)
     * @return world point in the turbine frame
     */
    static Vec3 local(BlockPos origin, Direction facing, double x, double y, double z) {
        Direction right = facing.getClockWise(), back = facing.getOpposite();
        return Vec3.atBottomCenterOf(origin)
                .add(right.getStepX() * (x - 0.5), y, right.getStepZ() * (x - 0.5))
                .add(back.getStepX() * (z - 0.5), 0, back.getStepZ() * (z - 0.5));
    }

    /** Positional loop that follows its machine and fades with the synchronized intensity. */
    static final class Loop extends AbstractTickableSoundInstance {
        private final BlockEntity source;
        private final DoubleSupplier intensity;
        private final float base, basePitch, rate, pitchSpan;
        /** Audible distance in blocks with a linear fade to silence, or 0 for vanilla attenuation. */
        private final double range;
        private float fade;

        Loop(SoundEvent event, BlockEntity source, DoubleSupplier intensity, float base, float pitch) {
            this(event, source, intensity, base, pitch, 0.1f, 0, Vec3.atCenterOf(source.getBlockPos()), 0);
        }

        /**
         * @param rate share of the gap to the target intensity closed each tick (smaller = slower spin-up)
         * @param pitchSpan extra pitch at full intensity
         * @param position where the sound comes from
         */
        Loop(SoundEvent event, BlockEntity source, DoubleSupplier intensity, float base, float pitch, float rate, float pitchSpan, Vec3 position, double range) {
            super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.source = source;
            this.intensity = intensity;
            this.base = base;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.pitch = pitch;
            this.basePitch = pitch;
            this.rate = rate;
            this.pitchSpan = pitchSpan;
            this.range = range;
            // A custom range replaces vanilla attenuation, whose quietest reach is 16 blocks.
            if (range > 0) this.attenuation = Attenuation.NONE;
            this.x = position.x;
            this.y = position.y;
            this.z = position.z;
        }

        @Override public void tick() {
            double target = Mth.clamp(intensity.getAsDouble(), 0, 1);
            fade += (float) ((target - fade) * rate);
            boolean gone = source.isRemoved() || source.getLevel() != Minecraft.getInstance().level
                    || distanceSqr(source) > SOUND_RANGE * SOUND_RANGE || !HydroClientConfig.sounds();
            if (gone || (target <= 0 && fade < 0.02f)) { stop(); return; }
            double falloff = 1;
            if (range > 0) {
                double distance = Math.sqrt(distanceSqr(new Vec3(x, y, z)));
                if (distance >= range) { stop(); return; }
                falloff = 1 - distance / range;
            }
            volume = Math.max(0.001f, (float) (base * fade * HydroClientConfig.volume() * falloff));
            pitch = basePitch + pitchSpan * fade;
        }

        @Override public boolean canStartSilent() { return true; }
    }
}
