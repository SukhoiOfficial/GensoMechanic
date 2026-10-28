package dev.gensomechanic.mining.client.compat.xaero;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaero.map.WorldMapSession;
import xaero.map.highlight.ChunkHighlighter;

public final class WorldMapOverlay extends ChunkHighlighter {
    public WorldMapOverlay() { super(true); }
    @Override public int calculateRegionHash(ResourceKey<Level> d, int x, int z) {
        var v = ScannerMapBridge.view(d == null ? null : d.location()); return v.hashes().getOrDefault(ChunkPos.asLong(x,z),0);
    }
    @Override public boolean regionHasHighlights(ResourceKey<Level> d, int x, int z) { return ScannerMapBridge.view(d == null ? null : d.location()).region(d, x, z); }
    @Override public boolean chunkIsHighlit(ResourceKey<Level> d, int x, int z) { return ScannerMapBridge.view(d == null ? null : d.location()).at(d, x, z) != null; }
    @Override protected int[] getColors(ResourceKey<Level> d, int x, int z) {
        var v = ScannerMapBridge.view(d == null ? null : d.location());
        return v.colors(d, x, z);
    }
    @Override public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> d, int x, int z) {
        var mark = ScannerMapBridge.view(d == null ? null : d.location()).at(d, x, z); return mark == null ? null : mark.name();
    }
    @Override public Component getChunkHighlightBluntTooltip(ResourceKey<Level> d, int x, int z) { return null; }
    @Override public void addMinimapBlockHighlightTooltips(List<Component> lines, ResourceKey<Level> d, int x, int z, int width) {
        var mark = ScannerMapBridge.view(d == null ? null : d.location()).at(d, x >> 4, z >> 4); if (mark != null) lines.add(mark.name());
    }
    public static void invalidate(ScannerMapBridge.View before, ScannerMapBridge.View after) {
        var session = WorldMapSession.getCurrentSession();
        if (session == null || session.getMapProcessor() == null || session.getMapProcessor().getMapWorld() == null) return;
        for (var v : List.of(before, after)) {
            if (v.dimension() == null) continue;
            var dimension = session.getMapProcessor().getMapWorld().getDimension(ResourceKey.create(Registries.DIMENSION, v.dimension()));
            if (dimension == null) continue;
            var handler = dimension.getHighlightHandler();
            synchronized (handler) {
                for (long region : v.regions()) if(!Objects.equals(before.hashes().get(region),after.hashes().get(region))) handler.clearCachedHash(ChunkPos.getX(region), ChunkPos.getZ(region));
            }
        }
    }
}
