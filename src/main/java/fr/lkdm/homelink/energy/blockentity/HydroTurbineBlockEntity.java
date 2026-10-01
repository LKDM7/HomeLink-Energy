package fr.lkdm.homelink.energy.blockentity;

import fr.lkdm.homecore.api.energy.EnergyPort;
import fr.lkdm.homecore.api.energy.EnergyPortType;
import fr.lkdm.homecore.api.energy.EnergyRole;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homelink.energy.block.HydroTurbineBlock;
import fr.lkdm.homelink.energy.config.HydroConfig;
import fr.lkdm.homelink.energy.energy.EnergySourceType;
import fr.lkdm.homelink.energy.energy.EnergyTransfer;
import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homelink.energy.energy.HePort;
import fr.lkdm.homelink.energy.homelink.EnergyDevice;
import fr.lkdm.homelink.energy.homelink.HydroTurbineDevice;
import fr.lkdm.homelink.energy.hydro.*;
import fr.lkdm.homelink.energy.menu.HydroTurbineMenu;
import fr.lkdm.homelink.energy.menu.MenuData;
import fr.lkdm.homelink.energy.network.EnergyNetworks;
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
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

/**
 * Master of the 2x2x2 Hydro Turbine: the only place where flow becomes HE. One buffer, one fractional
 * accumulator, one HE output port shared by its single output face; the discharge in front is visual only.
 */
public final class HydroTurbineBlockEntity extends EnergyDeviceBlockEntity implements HydroMachineEntity {
    public static final int SCHEMA = 1;
    private static final int RATE_WINDOW = 20;
    /** Client hook installed by the client setup; the common code never references client classes. */
    public static Consumer<HydroTurbineBlockEntity> clientTicker = turbine -> { };
    private final HydroTurbineCore core = new HydroTurbineCore(HydroParameters.DEFAULT.turbineBuffer(), () -> level == null ? 0 : level.getGameTime());
    private final HePort port = new Port();
    private final MenuData display = new MenuData(HydroTurbineMenu.VALUES);
    private HydroStatus.Turbine status = HydroStatus.Turbine.NETWORK_PENDING;
    private HydroAllocation allocation = HydroAllocation.NONE;
    private boolean complete, dimension, outletClear, outletDirty = true, hydraulic, direct;
    @Nullable private BlockPos obstruction;
    private int pumps;
    private long lastTick = Long.MIN_VALUE, nextOutletCheck, lastSync, outletScans, windowExported;
    private double used, deliveredRate;
    @Nullable private BlockCapabilityCache<EnergyPort, Direction> output;
    // Client view, written only from server updates.
    private float clientFlow;
    private int clientStatus = HydroStatus.Turbine.NETWORK_PENDING.ordinal();

    public HydroTurbineBlockEntity(BlockPos pos, BlockState state) { super(EnergyRegistries.HYDRO_TURBINE.get(), pos, state); }

    public HydroTurbineCore core() { return core; }
    public HydroStatus.Turbine status() { return status; }
    public HydroAllocation allocation() { return allocation; }
    public double usedFlow() { return used; }
    public int pumpCount() { return pumps; }
    public boolean outletClear() { return outletClear; }
    public boolean hydraulicConnected() { return hydraulic; }
    /** @return whether an HE port (battery, machine) touches the output face directly, without cables */
    public boolean directOutput() { return direct; }
    @Nullable public BlockPos obstruction() { return obstruction; }
    public double deliveredRate() { return deliveredRate; }
    public long outletScans() { return outletScans; }
    public float clientFlow() { return clientFlow; }
    public HydroStatus.Turbine clientStatus() { return HydroStatus.Turbine.values()[Math.clamp(clientStatus, 0, HydroStatus.Turbine.values().length - 1)]; }
    public Direction facing() { return getBlockState().getValue(HydroTurbineBlock.FACING); }
    @Override public void surroundingsChanged() { outletDirty = true; }

    public EnergyNetworks.Connection networkConnection() {
        return level instanceof ServerLevel s ? EnergyNetworks.connection(s, worldPosition) : EnergyNetworks.Connection.NONE;
    }

    public boolean structureComplete() {
        return level != null && ((HydroTurbineBlock) getBlockState().getBlock()).complete(level, worldPosition, getBlockState());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HydroTurbineBlockEntity turbine) {
        if (level instanceof ServerLevel server && turbine.available()) turbine.tick(server);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, HydroTurbineBlockEntity turbine) { clientTicker.accept(turbine); }

    private void tick(ServerLevel server) {
        long now = server.getGameTime();
        if (lastTick == now) return;
        lastTick = now;
        HydroParameters p = HydroConfig.parameters();
        core.buffer().setCapacity(p.turbineBuffer());
        complete = structureComplete();
        dimension = p.allows(server.dimension().location().toString());
        if (outletDirty || now >= nextOutletCheck) {
            checkOutlet(server);
            outletDirty = false;
            nextOutletCheck = now + 100 + (outletScans == 1 ? Math.floorMod(worldPosition.hashCode(), 100) : 0);
        }
        HydroCircuit circuit = HydroNetworks.get(server).circuit(worldPosition).orElse(null);
        HydroStatus.Circuit validity = circuit == null ? null : circuit.status(p.maxPumps());
        pumps = circuit == null ? 0 : circuit.pumps().size();
        hydraulic = circuit != null && !circuit.pipes().isEmpty();
        boolean ready = p.enabled() && powered() && complete && dimension && outletClear;
        allocation = validity == HydroStatus.Circuit.VALID
                ? circuit.allocate(now, this::pumpFlow, ready, p.turbineMaxFlow()) : HydroAllocation.NONE;
        used = ready ? allocation.used() : 0;
        core.tick(now, used, p.turbineMaxFlow(), p.heReference());
        HydroStatus.Turbine old = status;
        status = !p.enabled() ? HydroStatus.Turbine.CONFIG_DISABLED : !powered() ? HydroStatus.Turbine.DISABLED
                : !complete ? HydroStatus.Turbine.INCOMPLETE_STRUCTURE : !dimension ? HydroStatus.Turbine.UNSUPPORTED_DIMENSION
                : circuit == null ? HydroStatus.Turbine.NETWORK_PENDING
                : switch (validity) {
                    case TOO_LARGE -> HydroStatus.Turbine.NETWORK_TOO_LARGE;
                    case INCOMPLETE -> HydroStatus.Turbine.NETWORK_INCOMPLETE;
                    case MULTIPLE_TURBINES -> HydroStatus.Turbine.MULTIPLE_TURBINES;
                    case TOO_MANY_PUMPS -> HydroStatus.Turbine.TOO_MANY_PUMPS;
                    case VALID -> pumps == 0 ? HydroStatus.Turbine.NO_PUMP : !outletClear ? HydroStatus.Turbine.OUTLET_BLOCKED
                            : allocation.available() <= 0 ? HydroStatus.Turbine.NO_FLOW
                            : core.buffer().isFull() ? HydroStatus.Turbine.BUFFER_FULL : HydroStatus.Turbine.GENERATING;
                };
        if (output == null) {
            Direction face = HydroLayout.turbineOutputFace(facing());
            output = BlockCapabilityCache.create(HeCapabilities.PORT, server, HydroLayout.turbineOutput(worldPosition, facing()).relative(face),
                    face.getOpposite(), this::available, () -> { });
        }
        EnergyPort target = output.getCapability();
        direct = target != null;
        if (target != null && core.buffer().stored() > 0) EnergyTransfer.move(port, target, Long.MAX_VALUE);
        if (now % RATE_WINDOW == 0) {
            deliveredRate = windowExported / (double) RATE_WINDOW;
            windowExported = 0;
        }
        // Buffer, fraction and period counters are durable even without a whole HE this tick.
        setChanged();
        tickDevice(server);
        if (old != status) device().ifPresent(EnergyDevice::refresh);
        if (Math.floorMod(now + worldPosition.hashCode(), 10) == 0) refreshDisplay();
        float flow = (float) (p.turbineMaxFlow() <= 0 ? 0 : used / p.turbineMaxFlow());
        if (old != status || (now - lastSync >= 10 && Math.abs(flow - clientFlow) > 0.02f)) {
            clientFlow = flow;
            clientStatus = status.ordinal();
            lastSync = now;
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private double pumpFlow(BlockPos pump) {
        return level != null && level.isLoaded(pump) && level.getBlockEntity(pump) instanceof HydroPumpBlockEntity entity ? entity.availableFlow() : 0;
    }

    /** The two cells in front of the lower discharge must hold air or water; an unloaded cell blocks. */
    private void checkOutlet(ServerLevel server) {
        outletScans++;
        obstruction = null;
        outletClear = true;
        for (BlockPos pos : HydroLayout.turbineDischarge(worldPosition, facing())) {
            if (!server.isLoaded(pos)) { outletClear = false; continue; }
            BlockState state = server.getBlockState(pos);
            if (!state.isAir() && !state.is(Blocks.WATER)) {
                outletClear = false;
                if (obstruction == null) obstruction = pos.immutable();
            }
        }
    }

    /** @return the shared HE port on the output face only; every other face exposes nothing */
    @Nullable public HePort port(BlockState part, @Nullable Direction side) {
        return HydroTurbineBlock.isOutput(part, side) ? port : null;
    }

    private final class Port implements HePort {
        @Override public EnergyRole role() { return EnergyRole.PRODUCER; }
        @Override public EnergyPortType type() { return EnergyPortType.OUTPUT; }
        @Override public EnergySourceType sourceType() { return EnergySourceType.HYDRO; }
        @Override public long stored() { return core.buffer().stored(); }
        @Override public long capacity() { return core.buffer().capacity(); }
        @Override public long insert(long amount, boolean simulate) { return 0; }
        @Override public long extract(long amount, boolean simulate) {
            // Structure checked once per tick by the master; cables query the port several times per tick.
            if (!(level instanceof ServerLevel) || !HydroTurbineBlockEntity.this.available() || !complete) return 0;
            long extracted = core.buffer().extract(amount, simulate);
            if (!simulate && extracted > 0) {
                core.exported(extracted);
                windowExported += extracted;
                setChanged();
            }
            return extracted;
        }
    }

    private void refreshDisplay() {
        HydroParameters p = HydroConfig.parameters();
        display.put(HydroTurbineMenu.STATUS, status.ordinal());
        display.put(HydroTurbineMenu.POWERED, powered() ? 1 : 0);
        display.put(HydroTurbineMenu.AVAILABLE_FLOW, (int) Math.round(allocation.available() * 1000));
        display.put(HydroTurbineMenu.USED_FLOW, (int) Math.round(used * 1000));
        display.put(HydroTurbineMenu.MAX_FLOW, (int) Math.round(p.turbineMaxFlow() * 1000));
        display.put(HydroTurbineMenu.PUMPS, pumps);
        display.put(HydroTurbineMenu.HYDRAULIC, hydraulic ? 1 : 0);
        display.put(HydroTurbineMenu.OUTLET, outletClear ? 1 : 0);
        display.put(HydroTurbineMenu.POTENTIAL, (int) Math.round(core.potentialRate() * 10_000));
        display.put(HydroTurbineMenu.DELIVERED, (int) Math.round(deliveredRate * 10_000));
        display.put(HydroTurbineMenu.PERIOD, (int) Math.min(Integer.MAX_VALUE, core.generatedPeriod()));
        display.put(HydroTurbineMenu.OBSERVED, (int) core.observedTicks());
        display.put(HydroTurbineMenu.BUFFER, (int) core.buffer().stored());
        display.put(HydroTurbineMenu.CAPACITY, (int) core.buffer().capacity());
        display.put(HydroTurbineMenu.LOST, (int) Math.min(Integer.MAX_VALUE, core.lost()));
        display.put(HydroTurbineMenu.NETWORK, networkConnection().ordinal());
        display.put(HydroTurbineMenu.LIMITED, allocation.limited() ? 1 : 0);
        display.put(HydroTurbineMenu.DIRECT, direct ? 1 : 0);
        display.put(HydroTurbineMenu.OBSTRUCTION, obstruction == null ? 0 : 1);
        if (obstruction != null) {
            display.put(HydroTurbineMenu.OB_X, obstruction.getX()); display.put(HydroTurbineMenu.OB_Y, obstruction.getY()); display.put(HydroTurbineMenu.OB_Z, obstruction.getZ());
        }
    }

    @Override public EnergyDevice createDevice(Consumer<DeviceEvent> events) { return new HydroTurbineDevice(this, events); }
    @Override public Component getDisplayName() { return name(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        refreshDisplay();
        return new HydroTurbineMenu(id, inventory, this, display);
    }

    @Override public void onLoad() { super.onLoad(); outletDirty = true; lastTick = Long.MIN_VALUE; invalidatePorts(); }
    private void release() { output = null; invalidatePorts(); }
    private void invalidatePorts() {
        if (level instanceof ServerLevel server) {
            var block = (HydroTurbineBlock) getBlockState().getBlock();
            for (var tile : block.positions(worldPosition, getBlockState())) if (server.isLoaded(tile)) server.invalidateCapabilities(tile);
            EnergyNetworks.existing(server).ifPresent(networks -> networks.markDirty(worldPosition));
        }
    }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); release(); }
    @Override public void setRemoved() { super.setRemoved(); release(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("hydro_schema", SCHEMA);
        tag.putLong("energy", core.buffer().stored());
        tag.putDouble("fraction", core.fraction().fraction());
        tag.putLong("generated", core.generated());
        tag.putLong("lost", core.lost());
        tag.putLong("exported", core.exported());
        tag.putLong("generated_period", core.generatedPeriod());
        tag.putLong("observed_ticks", core.observedTicks());
        tag.putLong("period", core.period());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        core.buffer().setCapacity(HydroConfig.parameters().turbineBuffer());
        core.buffer().setStored(tag.getLong("energy"));
        core.fraction().setFraction(tag.getDouble("fraction"));
        core.restore(tag.getLong("generated"), tag.getLong("lost"), tag.getLong("exported"), tag.getLong("generated_period"),
                tag.getLong("observed_ticks"), tag.contains("period") ? tag.getLong("period") : Long.MIN_VALUE);
        outletDirty = true;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putFloat("flow", clientFlow);
        tag.putInt("status", status.ordinal());
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        float flow = tag.getFloat("flow");
        clientFlow = Float.isFinite(flow) ? Math.max(0, Math.min(1, flow)) : 0;
        clientStatus = tag.getInt("status");
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }
}
