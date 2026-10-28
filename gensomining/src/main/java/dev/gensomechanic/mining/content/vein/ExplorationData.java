package dev.gensomechanic.mining.content.vein;

import java.util.*;
import dev.gensomechanic.mining.network.ScannerOverlayPayload.Mark;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** One world-owned file per player. Never stores discoveries in the transferable item. */
public final class ExplorationData extends SavedData {
    public record Region(ResourceLocation dimension, int x, int z) {}
    public static final class Discovery {
        final VeinGenerator.Identity identity;
        final List<ChunkPos> chunks;
        final String generation;
        private String checked = "";
        private boolean valid;
        Discovery(VeinGenerator.Identity identity, List<ChunkPos> chunks, String generation) {
            this.identity=identity; this.chunks=List.copyOf(chunks); this.generation=generation;
        }
        public VeinGenerator.Identity identity() { return identity; }
        public List<ChunkPos> chunks() { return chunks; }
        public boolean validate(MinecraftServer server, String current) {
            var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,identity.dimension()));
            if (level == null || !VeinManager.definitions(server).containsKey(identity.type())) return false;
            current=VeinManager.signature(level);
            if (checked.equals(current)) return valid;
            checked=current;
            return valid=generation.equals(current);
        }
        public List<Mark> marks(MinecraftServer server) {
            var d=VeinManager.definitions(server).get(identity.type());
            return d == null ? List.of() : chunks.stream().map(c -> new Mark(c,identity.type(),d.name(),identity.centre(),d.mapColor())).toList();
        }
    }
    private final Map<VeinGenerator.Identity,Discovery> discoveries=new LinkedHashMap<>();
    private final Map<Region,List<VeinGenerator.Identity>> regions=new HashMap<>();
    private final Map<ResourceLocation,List<Long>> directory=new HashMap<>();
    private long revision;
    public static ExplorationData get(ServerPlayer player) {
        return player.server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ExplorationData::new,ExplorationData::load),
            "gensomining_exploration_"+player.getUUID());
    }
    public long revision() { return revision; }
    public int size() { return discoveries.size(); }
    public List<VeinGenerator.Identity> in(Region region) { return regions.getOrDefault(region,List.of()); }
    public Discovery get(VeinGenerator.Identity id) { return discoveries.get(id); }
    public List<Long> regionKeys(ResourceLocation dimension) {
        return directory.computeIfAbsent(dimension,d -> regions.keySet().stream().filter(r -> r.dimension.equals(d))
            .map(r -> ChunkPos.asLong(r.x,r.z)).sorted().toList());
    }
    public void discover(VeinScan scan, String generation) {
        scan.current().ifPresent(v -> discover(v,generation));
        scan.traces().forEach(t -> discover(t.vein(),generation));
    }
    public void discover(VeinGenerator.Occurrence vein, String generation) {
        var old=discoveries.get(vein.identity());
        if (old != null && old.generation.equals(generation) && old.chunks.equals(vein.chunks())) return;
        if (old != null) unindex(old);
        var value=new Discovery(vein.identity(),vein.chunks(),generation); discoveries.put(value.identity,value); index(value);
        directory.remove(value.identity.dimension()); revision++; setDirty();
    }
    private Set<Region> regions(Discovery value) {
        Set<Region> result=new HashSet<>(); value.chunks.forEach(c -> result.add(new Region(value.identity.dimension(),c.x>>5,c.z>>5))); return result;
    }
    private void index(Discovery value) { regions(value).forEach(r -> regions.computeIfAbsent(r,k -> new ArrayList<>()).add(value.identity)); }
    private void unindex(Discovery value) { regions(value).forEach(r -> { var list=regions.get(r); if(list!=null) { list.remove(value.identity); if(list.isEmpty()) regions.remove(r); } }); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("Version",1); tag.putLong("Revision",revision); var list=new ListTag();
        discoveries.values().forEach(d -> {
            var v=new CompoundTag(); v.putString("Dimension",d.identity.dimension().toString()); v.putString("Type",d.identity.type().toString());
            v.putLong("Centre",d.identity.centre().toLong()); v.putString("Generation",d.generation);
            v.putLongArray("Chunks",d.chunks.stream().mapToLong(ChunkPos::toLong).toArray()); list.add(v);
        }); tag.put("Discoveries",list); return tag;
    }
    public static ExplorationData load(CompoundTag tag, HolderLookup.Provider lookup) {
        var data=new ExplorationData(); data.revision=tag.getLong("Revision");
        if(tag.getInt("Version")!=1)return data;
        for (var element:tag.getList("Discoveries",Tag.TAG_COMPOUND)) {
            var v=(CompoundTag)element; var dimension=ResourceLocation.tryParse(v.getString("Dimension")); var type=ResourceLocation.tryParse(v.getString("Type"));
            long[] cells=v.getLongArray("Chunks");
            if(dimension==null || type==null || cells.length==0 || cells.length>4096) continue;
            var id=new VeinGenerator.Identity(dimension,type,new ChunkPos(v.getLong("Centre")));
            var d=new Discovery(id,Arrays.stream(cells).mapToObj(ChunkPos::new).distinct().sorted(Comparator.comparingInt((ChunkPos c)->c.x).thenComparingInt(c->c.z)).toList(),v.getString("Generation"));
            if(data.discoveries.putIfAbsent(id,d)==null) data.index(d);
        } return data;
    }
}
