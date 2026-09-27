package fr.lkdm.homelink.energy.network;

import fr.lkdm.homecore.api.energy.*;
import fr.lkdm.homelink.energy.energy.EnergyTransfer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;

/**
 * One connected set of cables and the energy ports around it. Cables hold no energy: the
 * network only moves HE between ports, every tick, in three steps.
 * <ol>
 *   <li>current production to consumers;</li>
 *   <li>remaining production to storage;</li>
 *   <li>storage to the consumers still asking.</li>
 * </ol>
 * Storage never feeds storage. Ports are served in a stable order (by position), and the
 * first source tried rotates with the game tick so that the load is shared without randomness.
 */
public final class EnergyNetwork {
    /** Ticks averaged by the network flows. */
    public static final int RATE_WINDOW = 20;

    /** One port next to a cable, resolved each tick (null while its chunk is unloaded). */
    public record Endpoint(BlockPos pos, Supplier<EnergyPort> port) { }

    private final int id;
    private final Set<BlockPos> cables;
    private final List<Endpoint> endpoints;
    private final boolean tooLarge;
    private final List<EnergyPort> producers = new ArrayList<>();
    private final List<EnergyPort> storages = new ArrayList<>();
    private final List<EnergyPort> consumers = new ArrayList<>();
    private long windowProduced;
    private long windowConsumed;
    private long windowCharged;
    private long windowDischarged;
    private double production;
    private final long[] sourceWindow = new long[fr.lkdm.homelink.energy.energy.EnergySourceType.values().length];
    private final double[] sourceProduction = new double[sourceWindow.length];
    private long[] producerBefore = new long[0];
    private double consumption;
    private double charge;
    private double discharge;
    private long stored;
    private long capacity;
    private int producerCount;
    private int storageCount;
    private int consumerCount;
    private long ticks;
    private long lastGameTime = Long.MIN_VALUE;

    public EnergyNetwork(int id, Set<BlockPos> cables, List<Endpoint> endpoints, boolean tooLarge) {
        this.id = id;
        this.cables = Set.copyOf(cables);
        this.endpoints = List.copyOf(endpoints);
        this.tooLarge = tooLarge;
    }

    /** Runs one tick of distribution. */
    public void tick(long gameTime) {
        if (tooLarge || lastGameTime == gameTime) return;
        lastGameTime = gameTime;
        producers.clear();
        storages.clear();
        consumers.clear();
        long storedNow = 0, capacityNow = 0;
        for (Endpoint endpoint : endpoints) {
            EnergyPort port = endpoint.port().get();
            if (port == null) continue;
            switch (port.role()) {
                case PRODUCER -> { if (port.type().canSend()) producers.add(port); }
                case STORAGE -> {
                    storages.add(port);
                    storedNow = add(storedNow, port.stored());
                    capacityNow = add(capacityNow, port.capacity());
                }
                case CONSUMER -> { if (port.type().canReceive()) consumers.add(port); }
            }
        }
        if (producerBefore.length < producers.size()) producerBefore = new long[producers.size()];
        for (int i=0;i<producers.size();i++) producerBefore[i]=producers.get(i).stored();
        long produced = spread(producers, consumers, gameTime);
        long consumed = produced;
        long charged = spread(producers, storages, gameTime);
        produced = add(produced, charged);
        for (int i=0;i<producers.size();i++) {
            EnergyPort source=producers.get(i);
            int category=fr.lkdm.homelink.energy.energy.HePort.sourceOf(source).ordinal();
            sourceWindow[category]=add(sourceWindow[category],Math.max(0,producerBefore[i]-source.stored()));
        }
        long discharged = spread(storages, consumers, gameTime);
        consumed = add(consumed, discharged);

        windowProduced = add(windowProduced, produced);
        windowConsumed = add(windowConsumed, consumed);
        windowCharged = add(windowCharged, charged);
        windowDischarged = add(windowDischarged, discharged);
        stored = 0;
        for (EnergyPort storage : storages) stored = add(stored, storage.stored());
        capacity = capacityNow;
        producerCount = producers.size();
        storageCount = storages.size();
        consumerCount = consumers.size();
        if (++ticks % RATE_WINDOW == 0) {
            production = windowProduced / (double) RATE_WINDOW;
            for (int i=0;i<sourceWindow.length;i++) { sourceProduction[i]=sourceWindow[i]/(double)RATE_WINDOW; sourceWindow[i]=0; }
            consumption = windowConsumed / (double) RATE_WINDOW;
            charge = windowCharged / (double) RATE_WINDOW;
            discharge = windowDischarged / (double) RATE_WINDOW;
            windowProduced = windowConsumed = windowCharged = windowDischarged = 0;
        }
    }

    /**
     * Shares what the sources can give among the sinks, a few even rounds at most.
     *
     * @return HE moved, extracted from sources and inserted into sinks
     */
    static long spread(List<EnergyPort> sources, List<EnergyPort> sinks, long gameTime) {
        if (sources.isEmpty() || sinks.isEmpty()) return 0;
        long moved = 0;
        int start = (int) Math.floorMod(gameTime, (long) sources.size());
        int sinkStart = (int) Math.floorMod(gameTime, (long) sinks.size());
        for (int round = 0; round < 4; round++) {
            long supply = 0;
            for (EnergyPort source : sources) supply = add(supply, Math.max(0, source.available()));
            if (supply <= 0) break;
            int open = 0;
            for (EnergyPort sink : sinks) if (sink.requested() > 0) open++;
            if (open == 0) break;
            long share = Math.max(1, supply / open);
            long roundMoved = 0;
            int sourceIndex = 0;
            for (int j = 0; j < sinks.size(); j++) {
                EnergyPort sink = sinks.get((sinkStart + j) % sinks.size());
                long want = Math.min(share, sink.requested());
                while (sourceIndex < sources.size() && want > 0) {
                    EnergyPort source = sources.get((start + sourceIndex) % sources.size());
                    if (source == sink || source.role() == EnergyRole.STORAGE && sink.role() == EnergyRole.STORAGE || source.available() <= 0) { sourceIndex++; continue; }
                    long step = EnergyTransfer.move(source, sink, want);
                    want -= step;
                    roundMoved = add(roundMoved, step);
                    if (sink.requested() <= 0) break;
                    if (step == 0 || source.available() <= 0) sourceIndex++;
                }
            }
            moved = add(moved, roundMoved);
            if (roundMoved == 0) break;
        }
        return moved;
    }

    private static long add(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    /** @return identifier, unique within a level until the next rebuild */
    public int id() { return id; }
    /** @return cable positions */
    public Set<BlockPos> cables() { return cables; }
    /** @return ports next to the cables */
    public List<Endpoint> endpoints() { return endpoints; }
    /** @return cables plus machines */
    public int nodeCount() { return cables.size() + endpoints.size(); }
    /** @return whether the network exceeds the node limit and is stopped */
    public boolean tooLarge() { return tooLarge; }
    /** @return HE/t taken from producers, averaged */
    public double production() { return production; }
    /** HE/t delivered by this category; these components sum to production(), never add them again. */
    public double production(fr.lkdm.homelink.energy.energy.EnergySourceType source) { return sourceProduction[source.ordinal()]; }
    /** @return HE/t delivered to consumers, averaged */
    public double consumption() { return consumption; }
    /** @return HE/t added to storage, averaged */
    public double charge() { return charge; }
    /** @return HE/t taken from storage, averaged */
    public double discharge() { return discharge; }
    /** @return production minus consumption, HE/t */
    public double netFlow() { return production - consumption; }
    /** @return HE in the network's storage after the last tick */
    public long stored() { return stored; }
    /** @return total storage capacity */
    public long capacity() { return capacity; }
    /** @return loaded producers seen last tick */
    public int producerCount() { return producerCount; }
    /** @return loaded storage ports seen last tick */
    public int storageCount() { return storageCount; }
    /** @return loaded consumers seen last tick */
    public int consumerCount() { return consumerCount; }
}
