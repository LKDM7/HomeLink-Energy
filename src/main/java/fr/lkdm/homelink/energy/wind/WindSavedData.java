package fr.lkdm.homelink.energy.wind;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Server dimension storage. No static level references and no client persistence. */
public final class WindSavedData extends SavedData {
    private final WindState state;
    private WindParameters parameters = WindParameters.DEFAULT;
    public WindSavedData(long seed) { state = new WindState(seed); }
    public static WindSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(
                () -> new WindSavedData(level.getSeed() ^ level.dimension().location().hashCode()),
                (tag, provider) -> load(tag)), "homelink_energy_wind");
    }
    public void tick() {
        if (state.ticks() % 20 == 0) parameters = fr.lkdm.homelink.energy.config.WindConfig.parameters();
        state.tick(parameters); setDirty();
    }
    public WindState state() { return state; }
    public WindParameters parameters() { return parameters; }
    public static WindSavedData load(CompoundTag tag) {
        var data = new WindSavedData(tag.getLong("random"));
        data.state.restore(tag.getDouble("current"), tag.getDouble("target"), tag.getLong("ticks"), tag.getLong("next"), tag.getLong("random"));
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("current", state.currentStrength()); tag.putDouble("target", state.targetStrength());
        tag.putLong("ticks", state.ticks()); tag.putLong("next", state.nextTargetChange()); tag.putLong("random", state.randomState());
        return tag;
    }
}
