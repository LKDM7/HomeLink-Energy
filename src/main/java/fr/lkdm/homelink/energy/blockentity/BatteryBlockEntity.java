package fr.lkdm.homelink.energy.blockentity;

import fr.lkdm.homecore.api.energy.*;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homelink.energy.homelink.BatteryDevice;
import fr.lkdm.homelink.energy.homelink.EnergyDevice;
import java.util.function.Consumer;
import fr.lkdm.homelink.energy.network.EnergyNetworks;
import fr.lkdm.homelink.energy.block.BatteryBlock;
import fr.lkdm.homelink.energy.block.BatteryTier;
import fr.lkdm.homelink.energy.energy.EnergyStore;
import fr.lkdm.homelink.energy.menu.BatteryMenu;
import fr.lkdm.homelink.energy.energy.HePort;
import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homelink.energy.menu.MenuData;
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
import org.jetbrains.annotations.Nullable;

/**
 * Battery of any tier: starts empty, stores what it receives, returns it on request and never
 * produces, recharges or leaks energy by itself. Every face exposes receipt and extraction,
 * within the tier's transfer limit per tick for each direction.
 */
public final class BatteryBlockEntity extends EnergyDeviceBlockEntity {
    /** Ticks averaged by the input and output flows. */
    public static final int RATE_WINDOW = 20;

    private final EnergyStore store;
    private final HePort port = new Port();
    private final MenuData display = new MenuData(BatteryMenu.VALUES);
    private long windowIn;
    private long windowOut;
    private double inputRate;
    private double outputRate;
    private long ticks;
    private boolean legacyBase;
    public boolean baseComplete() {
        return level != null && ((BatteryBlock)getBlockState().getBlock()).complete(level,worldPosition,getBlockState());
    }

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(EnergyRegistries.BATTERY.get(), pos, state);
        BatteryTier tier = tierOf(state);
        store = new EnergyStore(tier.capacity(), () -> level == null ? 0 : level.getGameTime());
        store.setRates(tier.transferRate(), tier.transferRate());
    }

    private static BatteryTier tierOf(BlockState state) {
        return state.getBlock() instanceof BatteryBlock block ? block.tier() : BatteryTier.I;
    }

    /** @return battery tier, from its block */
    public BatteryTier tier() { return tierOf(getBlockState()); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BatteryBlockEntity battery) {
        if (level instanceof ServerLevel server && battery.available()) battery.tick(server);
    }

    private void tick(ServerLevel server) {
        if(legacyBase && Math.floorMod(server.getGameTime()+worldPosition.hashCode(),20)==0
                && ((BatteryBlock)getBlockState().getBlock()).expandLegacyBase(server,worldPosition,getBlockState())) {
            legacyBase=false; invalidatePorts(); setChanged();
        }
        tickDevice(server);
        BatteryTier tier = tier();
        store.setCapacity(tier.capacity());
        store.setRates(tier.transferRate(), tier.transferRate());
        if (++ticks % RATE_WINDOW == 0) {
            inputRate = windowIn / (double) RATE_WINDOW;
            outputRate = windowOut / (double) RATE_WINDOW;
            windowIn = 0;
            windowOut = 0;
            refreshDisplay();
        }
    }

    /**
     * Energy port on every face, with one shared per-tick input/output budget.
     *
     * @param side queried face, or null
     * @return the port
     */
    public HePort port(@Nullable Direction side) { return port; }

    private final class Port implements HePort {
        @Override public EnergyRole role() { return EnergyRole.STORAGE; }
        @Override public EnergyPortType type() { return EnergyPortType.BOTH; }
        @Override public long stored() { return store.stored(); }
        @Override public long capacity() { return store.capacity(); }
        @Override public long insert(long amount, boolean simulate) {
            if (!(level instanceof ServerLevel) || !BatteryBlockEntity.this.available() || !baseComplete()) return 0;
            long accepted = store.receive(amount, simulate);
            if (!simulate && accepted > 0) {
                windowIn += accepted;
                setChanged();
            }
            return accepted;
        }
        @Override public long extract(long amount, boolean simulate) {
            if (!(level instanceof ServerLevel) || !BatteryBlockEntity.this.available() || !baseComplete()) return 0;
            long extracted = store.extract(amount, simulate);
            if (!simulate && extracted > 0) {
                windowOut += extracted;
                setChanged();
            }
            return extracted;
        }
    }

    private void refreshDisplay() {
        display.put(BatteryMenu.COMPLETE,baseComplete()?1:0);
        display.put(BatteryMenu.NETWORK, networkConnection().ordinal());
        display.put(BatteryMenu.TIER, tier().level());
        display.put(BatteryMenu.STORED, (int) Math.min(Integer.MAX_VALUE, store.stored()));
        display.put(BatteryMenu.CAPACITY, (int) Math.min(Integer.MAX_VALUE, store.capacity()));
        display.put(BatteryMenu.INPUT_CENTI, (int) Math.round(inputRate * 100));
        display.put(BatteryMenu.OUTPUT_CENTI, (int) Math.round(outputRate * 100));
        display.put(BatteryMenu.RATE_LIMIT, (int) Math.min(Integer.MAX_VALUE, store.maxInPerTick()));
    }

    /** @return connection of this block to a cable network */
    public EnergyNetworks.Connection networkConnection() {
        return level instanceof ServerLevel server ? EnergyNetworks.connection(server, worldPosition) : EnergyNetworks.Connection.NONE;
    }

    @Override
    public EnergyDevice createDevice(Consumer<DeviceEvent> events) { return new BatteryDevice(this, events); }

    @Override
    public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        refreshDisplay();
        return new BatteryMenu(id, inventory, this, display);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if(!legacyBase) tag.putInt("base_version",1);
        tag.putLong("energy", store.stored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        legacyBase=!tag.contains("base_version");
        store.setCapacity(tier().capacity());
        store.setStored(tag.getLong("energy"));
    }

    // Read-only views for screens, HomeCore devices and tests.

    private void invalidatePorts() {
        if(level instanceof ServerLevel server) {
            var block=(BatteryBlock)getBlockState().getBlock();
            for(var tile:block.positions(worldPosition,getBlockState())) if(server.isLoaded(tile)) server.invalidateCapabilities(tile);
            EnergyNetworks.existing(server).ifPresent(networks->networks.markDirty(worldPosition));
        }
    }
    @Override public void onLoad() { super.onLoad(); invalidatePorts(); }
    @Override public void setRemoved() { super.setRemoved(); invalidatePorts(); }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); invalidatePorts(); }

    /** @return stored HE */
    public long stored() { return store.stored(); }

    /** @return capacity in HE */
    public long capacity() { return store.capacity(); }

    /** @return charge percentage, from 0 to 100 */
    public double percentage() { return store.capacity() <= 0 ? 0 : store.stored() * 100.0 / store.capacity(); }

    /** @return average HE/t received over the last window */
    public double inputRate() { return inputRate; }

    /** @return average HE/t delivered over the last window */
    public double outputRate() { return outputRate; }
}
