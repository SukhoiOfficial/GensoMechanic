package dev.gensomechanic.mining.content.vein;

import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** One world/dimension and one immutable definition snapshot. Server thread only; never persisted. */
public final class VeinQuery {
    private VeinGenerator generator;
    private final VeinGenerator.Environment environment;
    private final VeinPlanner planner;
    private int memberWeight, peakMembers, peakEntries;
    private final Map<Long, Optional<VeinGenerator.Occurrence>> cache;

    public VeinQuery(VeinGenerator generator, VeinGenerator.Environment environment, int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("Cache capacity must be positive");
        this.generator = generator;
        this.environment = environment;
        this.planner = new VeinPlanner(generator, environment);
        cache = new LinkedHashMap<>(16, .75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Optional<VeinGenerator.Occurrence>> eldest) {
                if (size() <= maxEntries) return false;
                memberWeight -= eldest.getValue().map(v -> v.chunks().size()).orElse(1); return true;
            }
        };
    }

    /** Empty outer Optional means deterministic planning is still in progress, not an empty chunk. */
    public Optional<Optional<VeinGenerator.Occurrence>> tryFind(ChunkPos chunk,int nodeBudget) {
        var cached=peek(chunk);if(cached.isPresent())return cached;
        var result=planner.tryFind(chunk,Math.max(1,nodeBudget));
        result=result.map(this::metadata);result.ifPresent(value->remember(chunk,value));
        return result;
    }
    public Optional<Optional<VeinGenerator.Occurrence>> tryFindUntil(ChunkPos chunk,long deadline) {
        var cached=peek(chunk);if(cached.isPresent())return cached;
        var result=planner.tryFindUntil(chunk,deadline);
        result=result.map(this::metadata);
        result.ifPresent(value->remember(chunk,value));return result;
    }
    public Optional<VeinGenerator.Occurrence> find(ChunkPos chunk) {
        var cached = peek(chunk); if (cached.isPresent()) return cached.get();
        var result = metadata(planner.find(chunk));
        remember(chunk,result);return result;
    }
    private void remember(ChunkPos chunk,Optional<VeinGenerator.Occurrence> result) {
        int weight = result.map(v -> v.chunks().size()).orElse(1);
        while (!cache.isEmpty() && memberWeight + weight > 32768) {
            var iterator = cache.entrySet().iterator();
            memberWeight -= iterator.next().getValue().map(v -> v.chunks().size()).orElse(1); iterator.remove();
        }
        memberWeight += weight; cache.put(chunk.toLong(), result);
        peakMembers=Math.max(peakMembers,memberWeight);peakEntries=Math.max(peakEntries,cache.size());
    }
    public int cachedChunks() { return cache.size(); }
    Map<String,Integer> cachePeaks(){var result=planner.cachePeaks();result.put("query_entries",peakEntries);result.put("query_member_references",peakMembers);return result;}
    public Optional<Optional<VeinGenerator.Occurrence>> peek(ChunkPos chunk) {
        var value=cache.get(chunk.toLong());return value==null?Optional.empty():Optional.of(metadata(value));
    }
    void refreshMetadata(VeinGenerator generator){this.generator=generator;}
    public Optional<VeinGenerator.Occurrence> metadata(Optional<VeinGenerator.Occurrence> value) {
        return value.map(v->{var definition=generator.definitions().get(v.definition().id());
            return definition==v.definition()?v:new VeinGenerator.Occurrence(definition,v.capacity(),v.identity(),v.chunks(),v.signature(),v.district());});
    }
    public void clear() { cache.clear(); memberWeight = 0; planner.clear(); }
}
