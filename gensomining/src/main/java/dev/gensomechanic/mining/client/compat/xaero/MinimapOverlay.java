package dev.gensomechanic.mining.client.compat.xaero;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

public final class MinimapOverlay extends ChunkHighlighter {
    public MinimapOverlay() { super(true); }
    @Override public boolean regionHasHighlights(ResourceKey<Level> d, int x, int z) { return ScannerMapBridge.view(d == null ? null : d.location()).region(d, x, z); }
    @Override public boolean chunkIsHighlit(ResourceKey<Level> d, int x, int z) { return ScannerMapBridge.view(d == null ? null : d.location()).at(d, x, z) != null; }
    @Override protected int[] getColors(ResourceKey<Level> d, int x, int z) {
        var v = ScannerMapBridge.view(d == null ? null : d.location());
        return v.colors(d, x, z);
    }
    @Override public void addChunkHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> d, int x, int z, int width) {
        var mark = ScannerMapBridge.view(d == null ? null : d.location()).at(d, x, z); if (mark != null) compiler.addLine(mark.name());
    }
    public static void invalidate() {
        var session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null || session.getProcessor() == null) return;
        var handler = session.getProcessor().getMinimapWriter().getDimensionHighlightHandler();
        if (handler != null) handler.requestRefresh();
    }
}
