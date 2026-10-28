package dev.gensomechanic.mining.client.compat.xaero;

import dev.gensomechanic.mining.client.*;
import dev.gensomechanic.mining.network.ScannerOverlayPayload.Mark;
import dev.gensomechanic.mining.network.ScannerOverlayPayload.Key;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** Immutable publication shared by both renderers and map texture workers. */
public final class ScannerMapBridge {
    public record Label(double x, double z, Component name, List<ChunkPos> members) {}
    public record View(ResourceLocation dimension, Map<Long, Mark> marks, Set<Long> regions, List<Label> labels,
                       int opacity, long revision, Map<Long,List<Label>> labelRegions, Map<Long,Integer> hashes) {
        public Mark at(ResourceKey<Level> dimension, int x, int z) {
            return dimension != null && dimension.location().equals(this.dimension) ? marks.get(ChunkPos.asLong(x, z)) : null;
        }
        public boolean region(ResourceKey<Level> dimension, int x, int z) {
            return dimension != null && dimension.location().equals(this.dimension) && regions.contains(ChunkPos.asLong(x, z));
        }
        public int[] colors(ResourceKey<Level> dimension, int x, int z) {
            Mark mark = at(dimension, x, z); if (mark == null) return null;
            int fill = xaeroColor(mark.color(), Math.round(opacity * 2.55f)), border = xaeroColor(mark.color(), 255);
            return new int[] {fill, edge(mark, x, z-1, fill, border), edge(mark, x+1, z, fill, border),
                edge(mark, x, z+1, fill, border), edge(mark, x-1, z, fill, border)};
        }
        private int edge(Mark mark, int x, int z, int fill, int border) {
            Mark other = marks.get(ChunkPos.asLong(x, z));
            return other != null && mark.key().equals(other.key()) ? fill : border;
        }
    }
    private static final View EMPTY = new View(null, Map.of(), Set.of(), List.of(), 25, 0, Map.of(), Map.of());
    private static volatile View view = EMPTY;
    private static volatile Map<ResourceLocation,View> dimensions = Map.of();
    public static View view(ResourceLocation dimension) { return dimension == null ? EMPTY : dimensions.getOrDefault(dimension,EMPTY); }
    private static long lastArchiveRevision = -1;
    private static long lastRevision = -1;
    private static int lastOpacity;
    private static boolean lastEnabled;
    public static View view() { return view; }
    public static void refresh() {
        var snapshot = ScannerClient.OVERLAY.snapshot();
        boolean enabled = ScannerOverlayConfig.ENABLED.get(); int opacity = ScannerOverlayConfig.OPACITY.get();
        long archiveRevision=ArchiveClient.revision();
        if (snapshot.revision() == lastRevision && archiveRevision==lastArchiveRevision && enabled == lastEnabled && opacity == lastOpacity) return;
        lastRevision = snapshot.revision(); lastArchiveRevision=archiveRevision; lastEnabled = enabled; lastOpacity = opacity;
        Map<ResourceLocation,Map<Long,Mark>> combined=new HashMap<>();
        if(enabled) {
            ArchiveClient.visible().forEach((d,marks)->marks.forEach(m->combined.computeIfAbsent(d,k->new LinkedHashMap<>()).put(m.chunk().toLong(),m)));
            ScannerClient.OVERLAY.snapshots().forEach((d,s)->s.marks().forEach(m->combined.computeIfAbsent(d,k->new LinkedHashMap<>()).put(m.chunk().toLong(),m)));
        }
        Map<ResourceLocation,View> next=new HashMap<>();
        combined.forEach((d,marks)-> {
            Set<Long> regions=new HashSet<>(); marks.values().forEach(m->regions.add(ChunkPos.asLong(m.chunk().x>>5,m.chunk().z>>5)));
            var labels=labels(marks); Map<Long,List<Label>> index=new HashMap<>();
            labels.forEach(label->label.members().stream().map(c->ChunkPos.asLong(c.x>>5,c.z>>5)).distinct().forEach(r->index.computeIfAbsent(r,k->new ArrayList<>()).add(label)));
            index.replaceAll((r,list)->List.copyOf(list));
            Map<Long,Integer> hashes=new HashMap<>();
            marks.values().forEach(m->hashes.merge(ChunkPos.asLong(m.chunk().x>>5,m.chunk().z>>5),m.hashCode(),Integer::sum));
            hashes.replaceAll((r,h)->31*h+opacity);
            next.put(d,new View(d,Collections.unmodifiableMap(marks),Set.copyOf(regions),labels,opacity,view(d).revision()+1,Map.copyOf(index),Map.copyOf(hashes)));
        });
        var old=dimensions; dimensions=Map.copyOf(next); view=view(snapshot.dimension());
        if (ModList.get().isLoaded("xaeroworldmap")) {
            Set<ResourceLocation> changed=new HashSet<>(old.keySet()); changed.addAll(next.keySet());
            changed.forEach(d->WorldMapOverlay.invalidate(old.getOrDefault(d,EMPTY),next.getOrDefault(d,EMPTY)));
        }
        if (ModList.get().isLoaded("xaerominimap")) MinimapOverlay.invalidate();
    }
    /** Xaero highlighters use BBGGRRAA, unlike Minecraft's AARRGGBB. */
    public static int xaeroColor(int rgb, int alpha) {
        return (rgb & 255) << 24 | (rgb & 0xFF00) << 8 | (rgb >>> 16 & 255) << 8 | alpha;
    }
    private static List<Label> labels(Map<Long, Mark> marks) {
        Map<Key,List<Mark>> groups = new LinkedHashMap<>();
        marks.values().forEach(m -> groups.computeIfAbsent(m.key(), k -> new ArrayList<>()).add(m));
        List<Label> result = new ArrayList<>();
        for (var group : groups.values()) {
            double cx = group.stream().mapToDouble(m -> m.chunk().x).average().orElseThrow();
            double cz = group.stream().mapToDouble(m -> m.chunk().z).average().orElseThrow();
            List<ChunkPos> members = group.stream().map(Mark::chunk).sorted(Comparator.comparingDouble((ChunkPos p) ->
                (p.x-cx)*(p.x-cx)+(p.z-cz)*(p.z-cz)).thenComparingInt(p -> p.x).thenComparingInt(p -> p.z)).toList();
            var anchor = members.getFirst();
            result.add(new Label(anchor.getMinBlockX()+8, anchor.getMinBlockZ()+8, group.getFirst().name(), members));
        }
        return List.copyOf(result);
    }
    private ScannerMapBridge() {}
}
