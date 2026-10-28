package dev.gensomechanic.mining.content.vein;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server generation budgets, loaded before datapacks via COMMON config; clients do not influence the server. Captured per reload. */
public final class VeinGenerationConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.IntValue CHUNKS = BUILDER.defineInRange("maxVeinChunks", 1024, 1, 4096);
    private static final ModConfigSpec.IntValue RADIUS = BUILDER.defineInRange("maxRadiusChunks", 64, 0, 128);
    private static final ModConfigSpec.IntValue CELLS = BUILDER.defineInRange("maxCachedMembers", 262144, 4096, 1048576);
    private static final ModConfigSpec.IntValue AREA = BUILDER.defineInRange("maxShapeArea", 65536, 9, 1048576);
    private static final ModConfigSpec.IntValue NEIGHBORS = BUILDER.defineInRange("maxNeighborCandidateCells", 65536, 1024, 1048576);
    private static final ModConfigSpec.LongValue WORK = BUILDER.defineInRange("maxPlanningWork", 64000000L, 1000L, 1000000000L);
    public static final ModConfigSpec CONFIG = BUILDER.build();
    public record Limits(int maxChunks,int maxRadius,int cells,int area,int neighbors,long work) {}
    private static int value(ModConfigSpec.IntValue v, int fallback) { return CONFIG.isLoaded() ? v.get() : fallback; }
    public static Limits limits() {
        return new Limits(value(CHUNKS,1024),value(RADIUS,64),value(CELLS,262144),value(AREA,65536),
            value(NEIGHBORS,65536),CONFIG.isLoaded()?WORK.get():64000000L);
    }
    private VeinGenerationConfig() {}
}
