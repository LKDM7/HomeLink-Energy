package fr.lkdm.homelink.energy.blockentity;

import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homelink.energy.homelink.EnergyDevice;
import fr.lkdm.homelink.energy.homelink.EnergyHomeCore;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Energy block exposed to HomeCore as a device: stable identity, owner, optional HomeNetwork and
 * the live device registered while the block is loaded (HomeCore never scans the world).
 */
public abstract class EnergyDeviceBlockEntity extends BlockEntity implements MenuProvider {
    private static final int DEVICE_REFRESH = 20;
    private boolean available = true;
    protected boolean available() { return available && !isRemoved(); }
    @Override public void onLoad() { super.onLoad(); available = true; }

    private UUID deviceId = UUID.randomUUID();
    @Nullable private UUID owner;
    @Nullable private UUID homeNetwork;
    private String homeNetworkName = "";
    @Nullable private EnergyDevice device;
    private int deviceRetry;

    protected EnergyDeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Creates the HomeCore view of this block. */
    public abstract EnergyDevice createDevice(Consumer<DeviceEvent> events);

    /** Registers the device when needed and refreshes it about once per second. Server thread. */
    protected void tickDevice(ServerLevel server) {
        if (device == null && !isRemoved() && deviceRetry-- <= 0) {
            device = EnergyHomeCore.register(server, this).orElse(null);
            if (device == null) deviceRetry = 100;
        }
        if (device != null && Math.floorMod(server.getGameTime() + worldPosition.hashCode(), DEVICE_REFRESH) == 0) device.refresh();
    }

    private void releaseDevice() {
        if (device != null && level instanceof ServerLevel server) EnergyHomeCore.unregister(server, device);
        device = null;
    }

    /** The block was destroyed, not unloaded: leave its HomeNetwork. */
    public void destroyed() {
        if (level instanceof ServerLevel server) EnergyHomeCore.forgetOnRemoval(server, this);
        releaseDevice();
    }

    @Override
    public void setRemoved() {
        available = false;
        releaseDevice();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        available = false;
        releaseDevice();
        super.onChunkUnloaded();
    }

    /** @return live HomeCore device, while registered */
    public Optional<EnergyDevice> device() { return Optional.ofNullable(device); }

    /** @return stable HomeCore identity */
    public UUID deviceId() { return deviceId; }

    /** A copied block collided with a live device: become a distinct device, outside any network. */
    public void resetIdentityAfterCollision() {
        deviceId = UUID.randomUUID();
        clearHomeNetwork();
    }

    /** @return player who placed the block, if known */
    public Optional<UUID> owner() { return Optional.ofNullable(owner); }

    /** @param owner player who placed the block */
    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** @return HomeNetwork the device belongs to */
    public Optional<UUID> homeNetwork() { return Optional.ofNullable(homeNetwork); }

    /** @return name of that network when it was joined */
    public String homeNetworkName() { return homeNetworkName; }

    public void setHomeNetwork(UUID network, String name) {
        homeNetwork = network;
        homeNetworkName = name;
        setChanged();
    }

    public void clearHomeNetwork() {
        homeNetwork = null;
        homeNetworkName = "";
        setChanged();
    }

    /** Only operators may place a block carrying saved block data, so items never copy energy. */
    @Override
    public boolean onlyOpCanSetNbt() { return true; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("device_id", deviceId);
        if (owner != null) tag.putUUID("owner", owner);
        if (homeNetwork != null) {
            tag.putUUID("home_network", homeNetwork);
            tag.putString("home_network_name", homeNetworkName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("device_id")) deviceId = tag.getUUID("device_id");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        homeNetwork = tag.hasUUID("home_network") ? tag.getUUID("home_network") : null;
        homeNetworkName = homeNetwork == null ? "" : tag.getString("home_network_name");
    }
}
