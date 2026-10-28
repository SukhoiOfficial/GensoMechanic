package dev.gensomechanic.mining.content.vein;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/** Prepare every recipe/stress change first, then take one shared activity snapshot per level tick. */
public final class VeinCapacityTracker {
    public record Key(boolean background, net.minecraft.resources.ResourceLocation dimension,
                      net.minecraft.resources.ResourceLocation type, ChunkPos centre) {
        public static Key vein(VeinGenerator.Identity identity) {
            return new Key(false, identity.dimension(), identity.type(), identity.centre());
        }
    }
    private record Controller(Runnable prepare, BooleanSupplier eligible, DoubleSupplier cost, java.util.function.Supplier<Key> identity) {}
    private final Map<BlockPos, Controller> controllers = new HashMap<>();
    private Map<Key, Integer> counts = Map.of();
    private Map<Key, Double> occupied = Map.of();
    private Set<BlockPos> active = Set.of();
    private long snapshotTick = Long.MIN_VALUE;

    public void register(BlockPos controller, Runnable prepare, BooleanSupplier canActuallyMine, DoubleSupplier cost, java.util.function.Supplier<Key> identity) {
        controllers.put(controller.immutable(), new Controller(Objects.requireNonNull(prepare), Objects.requireNonNull(canActuallyMine), Objects.requireNonNull(cost), Objects.requireNonNull(identity)));
    }
    public void unregister(BlockPos controller) { controllers.remove(controller); }
    public void invalidate() { snapshotTick = Long.MIN_VALUE; counts = Map.of(); occupied = Map.of(); active = Set.of(); }
    public void unload(ChunkPos chunk) {
        controllers.keySet().removeIf(pos -> new ChunkPos(pos).equals(chunk));
    }

    public void beginTick(long tick, Predicate<BlockPos> isLoaded) {
        if (snapshotTick == tick) return;
        snapshotTick = tick;
        Map<Key, Integer> next = new HashMap<>();
        Map<Key, Double> nextOccupied = new HashMap<>();
        controllers.entrySet().removeIf(entry -> !isLoaded.test(entry.getKey()));
        controllers.values().forEach(controller -> controller.prepare.run());
        Set<BlockPos> nextActive = new HashSet<>();
        controllers.forEach((pos, controller) -> {
            if (controller.eligible.getAsBoolean()) {
                Key identity = controller.identity.get();
                if (identity == null) return;
                next.merge(identity, 1, Integer::sum);
                double cost = controller.cost.getAsDouble();
                if (!Double.isFinite(cost) || cost <= 0) throw new IllegalStateException("Invalid mining capacity cost");
                nextOccupied.merge(identity, cost, Double::sum);
                nextActive.add(pos);
            }
        });
        counts = Map.copyOf(next);
        occupied = Map.copyOf(nextOccupied);
        active = Set.copyOf(nextActive);
    }
    public boolean activeThisTick(BlockPos pos, long tick) { return snapshotTick == tick && active.contains(pos); }


    public double occupiedCapacity(Key identity) { return occupied.getOrDefault(identity, 0.0); }
    public int activeMiners(Key identity) { return counts.getOrDefault(identity, 0); }

    public static double efficiencyLimit(int capacity, double activeMiners) {
        if (capacity < 1 || !Double.isFinite(activeMiners) || activeMiners < 0) throw new IllegalArgumentException("Invalid capacity or active count");
        return activeMiners == 0 ? 1.0 : Math.min(1.0, (double) capacity / activeMiners);
    }
}
