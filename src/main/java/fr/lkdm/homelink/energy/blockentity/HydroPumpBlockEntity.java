package fr.lkdm.homelink.energy.blockentity;

import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homelink.energy.block.HydroPumpBlock;
import fr.lkdm.homelink.energy.config.HydroConfig;
import fr.lkdm.homelink.energy.homelink.EnergyDevice;
import fr.lkdm.homelink.energy.homelink.HydroPumpDevice;
import fr.lkdm.homelink.energy.hydro.*;
import fr.lkdm.homelink.energy.menu.HydroPumpMenu;
import fr.lkdm.homelink.energy.menu.MenuData;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Master of a Hydro Pump. It consumes no HE and no fuel and never removes water: it reports the flow its
 * local water window allows. Only the circuit's turbine converts flow into HE.
 */
public final class HydroPumpBlockEntity extends EnergyDeviceBlockEntity implements HydroMachineEntity {
    public static final int SCHEMA = 1;
    /** Client hook installed by the client setup; the common code never references client classes. */
    public static Consumer<HydroPumpBlockEntity> clientTicker = pump -> { };
    private final MenuData display = new MenuData(HydroPumpMenu.VALUES);
    private HydroStatus.Pump status = HydroStatus.Pump.NETWORK_PENDING;
    private WaterWindow.Result water = new WaterWindow.Result(0, 0, true);
    @Nullable private WaterWindow window;
    private boolean waterDirty = true, complete, dimension, hydraulic, clientPumping;
    private long lastTick = Long.MIN_VALUE, nextCheck, scans;
    private double available, allocated;
    @Nullable private BlockPos turbine;

    public HydroPumpBlockEntity(BlockPos pos, BlockState state) { super(EnergyRegistries.HYDRO_PUMP.get(), pos, state); }

    public HydroPumpTier tier() { return ((HydroPumpBlock) getBlockState().getBlock()).tier(); }
    public HydroStatus.Pump status() { return status; }
    public WaterWindow.Result water() { return water; }
    @Nullable public WaterWindow window() { return window; }
    /** @return DH/t this pump offers its circuit */
    public double availableFlow() { return available; }
    /** @return DH/t the turbine really uses from this pump */
    public double allocatedFlow() { return allocated; }
    public boolean hydraulicConnected() { return hydraulic; }
    @Nullable public BlockPos turbine() { return turbine; }
    public long scans() { return scans; }
    public boolean clientPumping() { return clientPumping; }
    @Override public void surroundingsChanged() { waterDirty = true; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HydroPumpBlockEntity pump) {
        if (level instanceof ServerLevel server && pump.available()) pump.tick(server);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, HydroPumpBlockEntity pump) { clientTicker.accept(pump); }

    private void tick(ServerLevel server) {
        long now = server.getGameTime();
        if (lastTick == now) return;
        lastTick = now;
        HydroParameters p = HydroConfig.parameters();
        var block = (HydroPumpBlock) getBlockState().getBlock();
        complete = block.complete(server, worldPosition, getBlockState());
        dimension = p.allows(server.dimension().location().toString());
        Direction facing = getBlockState().getValue(HydroPumpBlock.FACING);
        WaterWindow wanted = WaterWindow.of(worldPosition, facing, tier().width(), p.windowWidth(tier()), p.windowDistance(tier()), p.windowDepth(tier()));
        if (window == null || !window.min().equals(wanted.min()) || !window.max().equals(wanted.max())) {
            if (window != null) HydroWatch.remove(server, this, window);
            window = wanted;
            HydroWatch.add(server, this, window);
            waterDirty = true;
        }
        if (waterDirty || now >= nextCheck) {
            scan(server);
            waterDirty = false;
            // Spread periodic checks so that pumps loaded together do not scan on the same tick.
            nextCheck = now + p.waterCheckInterval() + (scans == 1 ? Math.floorMod(worldPosition.hashCode(), p.waterCheckInterval()) : 0);
        }
        boolean waterOk = !water.unknown() && water.availability() >= p.minimumAvailability();
        available = p.enabled() && powered() && complete && dimension && waterOk ? p.pumpFlow(tier()) * water.availability() : 0;
        HydroCircuit circuit = HydroNetworks.get(server).circuit(worldPosition).orElse(null);
        allocated = circuit == null ? 0 : circuit.share(worldPosition, now);
        hydraulic = circuit != null && !circuit.pipes().isEmpty();
        turbine = circuit != null && circuit.turbines().size() == 1 ? circuit.turbines().iterator().next() : null;
        HydroStatus.Pump old = status;
        status = !p.enabled() ? HydroStatus.Pump.CONFIG_DISABLED : !powered() ? HydroStatus.Pump.DISABLED
                : !complete ? HydroStatus.Pump.INCOMPLETE_STRUCTURE : !dimension ? HydroStatus.Pump.UNSUPPORTED_DIMENSION
                : water.unknown() ? HydroStatus.Pump.WATER_UNKNOWN : water.sources() == 0 ? HydroStatus.Pump.NO_WATER
                : !waterOk ? HydroStatus.Pump.WATER_INSUFFICIENT : circuit == null ? HydroStatus.Pump.NETWORK_PENDING
                : circuit.status(p.maxPumps()) != HydroStatus.Circuit.VALID ? HydroStatus.Pump.NETWORK_INVALID
                : circuit.turbines().isEmpty() ? HydroStatus.Pump.NO_TURBINE
                : allocated > 0 ? HydroStatus.Pump.PUMPING : HydroStatus.Pump.STANDBY;
        tickDevice(server);
        if (old != status) {
            device().ifPresent(EnergyDevice::refresh);
            if ((old == HydroStatus.Pump.PUMPING) != (status == HydroStatus.Pump.PUMPING)) {
                setChanged();
                server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            }
        }
        if (Math.floorMod(now + worldPosition.hashCode(), 10) == 0) refreshDisplay();
    }

    private void scan(ServerLevel server) {
        scans++;
        water = window.scan(pos -> {
            if (server.isOutsideBuildHeight(pos)) return WaterWindow.Cell.OTHER;
            if (!server.isLoaded(pos)) return WaterWindow.Cell.UNLOADED;
            BlockState state = server.getBlockState(pos);
            // Real source blocks only: waterlogged blocks, bubble columns and flowing water do not count.
            return state.is(Blocks.WATER) && state.getFluidState().isSource() ? WaterWindow.Cell.SOURCE : WaterWindow.Cell.OTHER;
        });
    }

    private void refreshDisplay() {
        display.put(HydroPumpMenu.STATUS, status.ordinal());
        display.put(HydroPumpMenu.TIER, tier().level());
        display.put(HydroPumpMenu.POWERED, powered() ? 1 : 0);
        display.put(HydroPumpMenu.AVAILABILITY, (int) Math.round(water.availability() * 1000));
        display.put(HydroPumpMenu.SOURCES, water.sources());
        display.put(HydroPumpMenu.REQUIRED, water.cells());
        display.put(HydroPumpMenu.AVAILABLE_FLOW, (int) Math.round(available * 1000));
        display.put(HydroPumpMenu.ALLOCATED_FLOW, (int) Math.round(allocated * 1000));
        display.put(HydroPumpMenu.MAX_FLOW, (int) Math.round(HydroConfig.parameters().pumpFlow(tier()) * 1000));
        display.put(HydroPumpMenu.HYDRAULIC, hydraulic ? 1 : 0);
        display.put(HydroPumpMenu.HAS_TURBINE, turbine != null ? 1 : 0);
        if (turbine != null) { display.put(HydroPumpMenu.TURBINE_X, turbine.getX()); display.put(HydroPumpMenu.TURBINE_Y, turbine.getY()); display.put(HydroPumpMenu.TURBINE_Z, turbine.getZ()); }
        if (window != null) {
            display.put(HydroPumpMenu.MIN_X, window.min().getX()); display.put(HydroPumpMenu.MIN_Y, window.min().getY()); display.put(HydroPumpMenu.MIN_Z, window.min().getZ());
            display.put(HydroPumpMenu.MAX_X, window.max().getX()); display.put(HydroPumpMenu.MAX_Y, window.max().getY()); display.put(HydroPumpMenu.MAX_Z, window.max().getZ());
        }
    }

    @Override public EnergyDevice createDevice(Consumer<DeviceEvent> events) { return new HydroPumpDevice(this, events); }
    @Override public Component getDisplayName() { return name(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        refreshDisplay();
        return new HydroPumpMenu(id, inventory, this, display);
    }

    @Override public void onLoad() { super.onLoad(); waterDirty = true; lastTick = Long.MIN_VALUE; }
    private void release() {
        if (level instanceof ServerLevel server && window != null) HydroWatch.remove(server, this, window);
        window = null;
    }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); release(); }
    @Override public void setRemoved() { super.setRemoved(); release(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("hydro_schema", SCHEMA);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putBoolean("pumping", status == HydroStatus.Pump.PUMPING);
        return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) { clientPumping = tag.getBoolean("pumping"); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }
}
