package fr.lkdm.homelink.energy.blockentity;

import fr.lkdm.homecore.api.energy.*;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homelink.energy.homelink.EnergyDevice;
import fr.lkdm.homelink.energy.homelink.SolarDevice;
import java.util.function.Consumer;
import fr.lkdm.homelink.energy.network.EnergyNetworks;
import fr.lkdm.homelink.energy.block.SolarPanelBlock;
import fr.lkdm.homelink.energy.block.SolarTier;
import fr.lkdm.homelink.energy.config.EnergyConfig;
import fr.lkdm.homelink.energy.energy.EnergyTransfer;
import fr.lkdm.homelink.energy.energy.SolarCurve;
import fr.lkdm.homelink.energy.energy.SolarPanelCore;
import fr.lkdm.homelink.energy.energy.SolarStatus;
import fr.lkdm.homelink.energy.energy.Weather;
import fr.lkdm.homelink.energy.energy.HePort;
import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homelink.energy.menu.MenuData;
import fr.lkdm.homelink.energy.menu.SolarPanelMenu;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

/**
 * Solar panel of any tier. Production runs on the server, one executed tick at a time; the
 * panel exports its buffer through its bottom face into a local HE port below it.
 */
public final class SolarPanelBlockEntity extends EnergyDeviceBlockEntity {
    /** Ticks between two refreshes of the values shown in an open screen. */
    private static final int DISPLAY_INTERVAL = 10;

    private final SolarPanelCore core = new SolarPanelCore(defaultBuffer(), this::gameTime);
    private final HePort port = new Port();
    private final MenuData display = new MenuData(SolarPanelMenu.VALUES);
    private boolean skyVisible;
    private boolean skyDirty = true;
    private long nextSkyCheck;
    private long exported;
    private long ticks;
    private long lastTick = Long.MIN_VALUE;
    private long tickNanos;
    private long measuredTicks;
    @Nullable private BlockCapabilityCache<EnergyPort, Direction> below;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyRegistries.SOLAR_PANEL.get(), pos, state);
    }

    private static long defaultBuffer() {
        return EnergyConfig.loaded() ? EnergyConfig.SOLAR_BUFFER_CAPACITY.get() : 1_000L;
    }

    private long gameTime() {
        return level == null ? 0 : level.getGameTime();
    }

    /** @return panel tier, from its block */
    public SolarTier tier() {
        return getBlockState().getBlock() instanceof SolarPanelBlock block ? block.tier() : SolarTier.I;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarPanelBlockEntity panel) {
        if (level instanceof ServerLevel server && panel.available()) panel.tick(server);
    }

    private void tick(ServerLevel server) {
        long now = server.getGameTime();
        if (now == lastTick) return;
        lastTick = now;
        long started = Boolean.getBoolean("energy.measureTicks") ? System.nanoTime() : 0;
        long previousEnergy = core.buffer().stored();
        double previousFraction = core.generator().fraction();
        long previousGenerated = core.generated(), previousLost = core.lost(), previousDay = core.day();
        core.buffer().setCapacity(defaultBuffer());
        if (skyDirty || now >= nextSkyCheck) {
            skyVisible = computeSkyVisible(server);
            skyDirty = false;
            nextSkyCheck = now + EnergyConfig.SKY_CHECK_INTERVAL.get();
        }
        boolean overworld = server.dimension() == Level.OVERWORLD;
        double efficiency = efficiency(Weather.of(server.isRaining(), server.isThundering()));
        boolean complete = arrayComplete();
        core.tick(tier().energyPerCycle(), overworld, skyVisible && complete, server.getDayTime(), efficiency);
        if (complete) exportBelow(server);
        if (core.buffer().stored() != previousEnergy || core.generator().fraction() != previousFraction
                || core.generated() != previousGenerated || core.lost() != previousLost || core.day() != previousDay) setChanged();
        if (ticks++ % DISPLAY_INTERVAL == 0) refreshDisplay();
        tickDevice(server);
        if (started != 0) { tickNanos += System.nanoTime() - started; measuredTicks++; }
    }

    /** Development measurements, disabled unless energy.measureTicks is explicitly enabled. */
    public long measuredNanos() { return tickNanos; }
    public long measuredTicks() { return measuredTicks; }

    /** Weather efficiency from the server configuration. */
    public static double efficiency(Weather weather) {
        return switch (weather) {
            case CLEAR -> EnergyConfig.CLEAR_WEATHER_EFFICIENCY.get();
            case RAIN -> EnergyConfig.RAIN_EFFICIENCY.get();
            case THUNDER -> EnergyConfig.THUNDER_EFFICIENCY.get();
        };
    }

    /**
     * Strict exposure: no non-air block at all above the panel, glass and other panels included.
     * Uses the world surface heightmap, so the column is never scanned.
     */
    private boolean computeSkyVisible(ServerLevel server) {
        if (!(getBlockState().getBlock() instanceof SolarPanelBlock block) || !block.complete(server, worldPosition, getBlockState())) return false;
        for (BlockPos tile : block.positions(worldPosition, getBlockState())) {
            if (server.getHeight(Heightmap.Types.WORLD_SURFACE, tile.getX(), tile.getZ()) > tile.getY() + 1) return false;
        }
        return true;
    }

    private boolean arrayComplete() {
        return level != null && getBlockState().getBlock() instanceof SolarPanelBlock block
                && block.complete(level, worldPosition, getBlockState());
    }

    private void exportBelow(ServerLevel server) {
        if (core.buffer().stored() <= 0 || !server.getChunkSource().hasChunk(worldPosition.getX() >> 4, worldPosition.getZ() >> 4)) return;
        if (below == null) {
            below = BlockCapabilityCache.create(HeCapabilities.PORT, server, worldPosition.below(), Direction.UP, () -> !isRemoved(), () -> { });
        }
        EnergyPort target = below.getCapability();
        if (target != null) EnergyTransfer.move(port, target, Long.MAX_VALUE);
    }

    /** Forces a sky check on the next tick, after a block update next to the panel. */
    public void invalidateSky() { skyDirty = true; }

    /**
     * Output port on bottom and sides; the active top surface is not a port.
     *
     * @param side queried face, or null for the block itself
     * @return null on the top, output port otherwise
     */
    @Nullable
    public HePort port(@Nullable Direction side) {
        return side == Direction.UP ? null : port;
    }

    private final class Port implements HePort {
        @Override public EnergyRole role() { return EnergyRole.PRODUCER; }
        @Override public fr.lkdm.homelink.energy.energy.EnergySourceType sourceType() { return fr.lkdm.homelink.energy.energy.EnergySourceType.SOLAR; }
        @Override public EnergyPortType type() { return EnergyPortType.OUTPUT; }
        @Override public long stored() { return core.buffer().stored(); }
        @Override public long capacity() { return core.buffer().capacity(); }
        @Override public long insert(long amount, boolean simulate) { return 0; }
        @Override public long extract(long amount, boolean simulate) {
            if (!(level instanceof ServerLevel) || !SolarPanelBlockEntity.this.available() || !arrayComplete()) return 0;
            long extracted = core.buffer().extract(amount, simulate);
            if (!simulate && extracted > 0) {
                exported = exported > Long.MAX_VALUE - extracted ? Long.MAX_VALUE : exported + extracted;
                setChanged();
            }
            return extracted;
        }
    }

    private void refreshDisplay() {
        display.put(SolarPanelMenu.NETWORK, networkConnection().ordinal());
        display.put(SolarPanelMenu.STATUS, core.status().ordinal());
        display.put(SolarPanelMenu.TIER, tier().level());
        display.put(SolarPanelMenu.SKY, skyVisible ? 1 : 0);
        display.put(SolarPanelMenu.EFFICIENCY, (int) Math.round(core.efficiency() * 100));
        display.put(SolarPanelMenu.PER_CYCLE, (int) Math.min(Integer.MAX_VALUE, tier().energyPerCycle()));
        display.put(SolarPanelMenu.RATE_MILLI, (int) Math.round(core.potentialRate() * 1000));
        display.put(SolarPanelMenu.BUFFER, (int) Math.min(Integer.MAX_VALUE, core.buffer().stored()));
        display.put(SolarPanelMenu.BUFFER_CAPACITY, (int) Math.min(Integer.MAX_VALUE, core.buffer().capacity()));
        display.put(SolarPanelMenu.TODAY, (int) Math.min(Integer.MAX_VALUE, core.generatedToday()));
    }

    /** @return connection of this block to a cable network */
    public EnergyNetworks.Connection networkConnection() {
        return level instanceof ServerLevel server ? EnergyNetworks.connection(server, worldPosition) : EnergyNetworks.Connection.NONE;
    }

    @Override
    public EnergyDevice createDevice(Consumer<DeviceEvent> events) { return new SolarDevice(this, events); }

    @Override
    public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        refreshDisplay();
        return new SolarPanelMenu(id, inventory, this, display);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        skyDirty = true;
        invalidateArrayPorts();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        below = null;
        invalidateArrayPorts();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        below = null;
        invalidateArrayPorts();
    }

    private void invalidateArrayPorts() {
        if (!(level instanceof ServerLevel server) || !(getBlockState().getBlock() instanceof SolarPanelBlock block)) return;
        for (BlockPos tile : block.positions(worldPosition, getBlockState())) {
            if (server.isLoaded(tile)) server.invalidateCapabilities(tile);
        }
        EnergyNetworks.existing(server).ifPresent(networks -> networks.markDirty(worldPosition));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("energy", core.buffer().stored());
        tag.putDouble("fraction", core.generator().fraction());
        tag.putLong("generated", core.generated());
        tag.putLong("lost", core.lost());
        tag.putLong("generated_today", core.generatedToday());
        tag.putLong("day", core.day());
        tag.putLong("exported", exported);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        core.buffer().setCapacity(defaultBuffer());
        core.buffer().setStored(tag.getLong("energy"));
        core.generator().setFraction(tag.getDouble("fraction"));
        core.restore(tag.getLong("generated"), tag.getLong("lost"), tag.getLong("generated_today"),
                tag.contains("day") ? tag.getLong("day") : Long.MIN_VALUE);
        exported = Math.max(0, tag.getLong("exported"));
    }

    // Read-only views for screens, HomeCore devices and tests.

    /** @return production state of the last tick */
    public SolarPanelCore core() { return core; }

    /** @return whether the cached exposure check sees the sky */
    public boolean skyVisible() { return skyVisible; }

    /** @return HE handed to other ports since placement (transfers, not production) */
    public long exported() { return exported; }

    /** @return rated HE per full clear cycle of this tier */
    public long nominalPerCycle() { return tier().energyPerCycle(); }

    /** @return potential share of the rated output right now, from 0 to 100 */
    public double generationPercentage() {
        double peak = tier().energyPerCycle() * SolarCurve.weight(SolarCurve.DAYLIGHT_TICKS / 2);
        return peak <= 0 ? 0 : Math.min(100, core.potentialRate() / peak * 100);
    }

    /** @return current production state */
    public SolarStatus status() { return core.status(); }
}
