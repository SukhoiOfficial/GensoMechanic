package dev.gensomechanic.mining.content.vein;

import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.function.Function;

public record VeinScan(ChunkPos centre, Optional<VeinGenerator.Occurrence> current, List<Trace> traces) {
    public static final int RANGE = 80;
    public enum Bearing {
        NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST;

        public String translationKey() { return "direction.gensomining." + name().toLowerCase(Locale.ROOT); }
        public static Bearing fromDelta(double x, double z) {
            int index = (int) Math.floor(Math.atan2(x, -z) / (Math.PI / 4) + .5);
            return values()[Math.floorMod(index, 8)];
        }
    }
    public record Trace(ChunkPos chunk, VeinGenerator.Occurrence vein, Bearing bearing, double distanceSquared) {}

    public VeinScan {
        traces = List.copyOf(traces);
    }

    public record Bounds(int minX,int minZ,int maxX,int maxZ) {}
    public static Bounds bounds(double x,double z,int radius) {
        // Include the chunk whose far edge is tangent to the circle at an exact chunk boundary.
        return new Bounds((int)Math.floor((x-radius)/16)-1,(int)Math.floor((z-radius)/16)-1,
            (int)Math.floor((x+radius)/16),(int)Math.floor((z+radius)/16));
    }
    public static boolean intersects(double x, double z, int radius, ChunkPos chunk) {
        double dx=Math.max(chunk.getMinBlockX()-x, Math.max(0,x-(chunk.getMinBlockX()+16.0)));
        double dz=Math.max(chunk.getMinBlockZ()-z, Math.max(0,z-(chunk.getMinBlockZ()+16.0)));
        return dx*dx+dz*dz <= (double)radius*radius;
    }
    public static VeinScan scan(double x, double z, ResourceLocation target,
                                Function<ChunkPos, Optional<VeinGenerator.Occurrence>> query) {
        return scan(x, z, ScanSelection.of(target), query);
    }
    public static VeinScan scan(double x, double z, ScanSelection selection,
                                Function<ChunkPos, Optional<VeinGenerator.Occurrence>> query) {
        ChunkPos centre = new ChunkPos(Math.floorDiv(Mth.floor(x), 16), Math.floorDiv(Mth.floor(z), 16));
        Optional<VeinGenerator.Occurrence> current = query.apply(centre);
        Map<VeinGenerator.Identity, Trace> hits = new HashMap<>();
        Comparator<Trace> order = Comparator.comparingDouble(Trace::distanceSquared)
                .thenComparingInt(t -> t.chunk().x).thenComparingInt(t -> t.chunk().z);
        for (int dz = -RANGE / 16 - 1; dz <= RANGE / 16 + 1; dz++) for (int dx = -RANGE / 16 - 1; dx <= RANGE / 16 + 1; dx++) {
            if (dx == 0 && dz == 0) continue;
            ChunkPos chunk = new ChunkPos(centre.x + dx, centre.z + dz);
            double offsetX = chunk.getMiddleBlockX() - x;
            double offsetZ = chunk.getMiddleBlockZ() - z;
            double distanceSquared = offsetX * offsetX + offsetZ * offsetZ;
            if (!intersects(x, z, RANGE, chunk)) continue;
            query.apply(chunk).filter(vein -> selection.matches(vein.definition()))
                .filter(vein -> current.isEmpty() || !vein.identity().equals(current.get().identity()))
                .ifPresent(vein -> hits.merge(vein.identity(), new Trace(chunk, vein, Bearing.fromDelta(offsetX, offsetZ), distanceSquared),
                    (a, b) -> order.compare(a, b) <= 0 ? a : b));
        }
        List<Trace> traces = new ArrayList<>(hits.values());
        traces.sort(order);
        return new VeinScan(centre, current, traces);
    }
}
