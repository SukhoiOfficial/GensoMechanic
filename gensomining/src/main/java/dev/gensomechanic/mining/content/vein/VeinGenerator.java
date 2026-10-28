package dev.gensomechanic.mining.content.vein;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Independent mining district algorithm 2. Each member remains a separate mining instance. */
public final class VeinGenerator {
    // Stable candidate positions; behavior changes are versioned in the layout signature.
    private static final long SITE_SEED_SALT=0x47454E534D000001L;
    final VeinGenerationConfig.Limits limits;
    private final Map<ResourceLocation,VeinDefinition> definitions;
    private final Map<ResourceLocation,Dimension> dimensions;
    private final String signature;
    public interface Environment {
        long seed();
        ResourceLocation dimension();
        ResourceLocation biomeAt(ChunkPos chunk,int y);
    }
    public record Identity(ResourceLocation dimension,ResourceLocation type,ChunkPos centre) {}
    public record DistrictIdentity(ResourceLocation category,ChunkPos centre,long seed,int members) {}
    public record Occurrence(VeinDefinition definition,int capacity,Identity identity,List<ChunkPos> chunks,String signature,DistrictIdentity district) {
        public Occurrence {chunks=chunks.stream().distinct().sorted(Comparator.comparingInt((ChunkPos p)->p.x).thenComparingInt(p->p.z)).toList();}
        public Occurrence(VeinDefinition definition,int capacity,Identity identity,List<ChunkPos> chunks,String signature){this(definition,capacity,identity,chunks,signature,null);}
    }
    public record Dimension(VeinWorldDefinition profile,Map<ResourceLocation,VeinDefinition> definitions,
                            String signature,String capacitySignature,int reach,int gap) {}
    public VeinGenerator(Collection<VeinDefinition> definitions,Collection<VeinWorldDefinition> profiles) {
        this(definitions,profiles,VeinGenerationConfig.limits());
    }
    public VeinGenerator(Collection<VeinDefinition> definitions,Collection<VeinWorldDefinition> profiles,VeinGenerationConfig.Limits limits) {
        this.limits=limits;
        if(definitions.size()>4096||profiles.size()>256)throw new IllegalArgumentException("Vein catalog exceeds definition/profile budget");
        var all=new TreeMap<ResourceLocation,VeinDefinition>(Comparator.comparing(Object::toString));
        for(var d:definitions)if(all.put(d.id(),d)!=null)throw new IllegalArgumentException("Duplicate vein: "+d.id());
        for(var d:definitions)if(d.variantOf()!=null) {
            var root=all.get(d.variantOf());
            if(root==null||root.variantOf()!=null)throw new IllegalArgumentException("variant_of must name an existing root: "+d.id());
        }
        this.definitions=Collections.unmodifiableMap(all);
        Map<ResourceLocation,Dimension> compiled=new HashMap<>();
        for(var source:profiles)for(var id:source.dimensions()) {
            if(compiled.containsKey(id))throw new IllegalArgumentException("Multiple vein_worlds profiles claim dimension "+id);
            var profile=source.bind(id,all);
            var active=new TreeMap<ResourceLocation,VeinDefinition>(Comparator.comparing(Object::toString));
            profile.resources().forEach(r->r.variants().forEach(v->active.put(v.id(),all.get(v.id()))));
            int reach=0;
            StringBuilder layout=new StringBuilder("district-2|").append(profile.layoutSemantics()),capacity=new StringBuilder();
            for(var d:active.values()) {
                if(d.maxChunks()>limits.maxChunks()||d.shape().radius()>limits.maxRadius())throw new IllegalArgumentException("Vein exceeds configured planning limits: "+d.id());
                long side=2L*d.shape().radius()+3;
                if(side*side>limits.area()||side*side*d.maxChunks()>limits.work())throw new IllegalArgumentException("Vein shape exceeds area/work budget: "+d.id());
                reach=Math.max(reach,d.shape().radius());
                layout.append('|').append(d.id()).append(':').append(d.layoutSemantics());
                capacity.append('|').append(d.id()).append(':').append(d.capacities());
            }
            reach+=profile.districts().radius()+1;
            if((2L*reach+3)*(2L*reach+3)>limits.area())throw new IllegalArgumentException("District/shape envelope exceeds maxShapeArea: "+source.id());
            int max=active.values().stream().mapToInt(VeinDefinition::maxChunks).max().orElse(1);
            var p=profile.placement();
            int gap=Math.max(profile.districts().gap(),active.values().stream().mapToInt(d->d.shape().preferredGap()).max().orElse(0));
            long bucketSide=(long)Math.ceil((4d*reach+2*gap+6)/p.bucket())+2;
            long candidateCells=bucketSide*bucketSide*p.rounds();
            long memberWork=(long)profile.maxDistrictMembers()*max*(2L*gap+1)*(2L*gap+1);
            if(candidateCells>limits.neighbors()||candidateCells*memberWork>limits.work())
                throw new IllegalArgumentException("District exceeds neighbor/member work budget: "+source.id());
            compiled.put(id,new Dimension(profile,Collections.unmodifiableMap(active),digest(layout.toString()),digest(capacity.toString()),reach,gap));
        }
        dimensions=Map.copyOf(compiled);
        if(dimensions.size()>256)throw new IllegalArgumentException("More than 256 configured dimensions");
        // Catalog refresh identity is deliberately distinct from each world's layout identity.
        StringBuilder catalog=new StringBuilder();
        all.values().forEach(d->catalog.append(d.id()).append(d.name()).append(d.icon()).append(d.mapColor()).append(d.variantOf()));
        compiled.keySet().stream().sorted(Comparator.comparing(Object::toString)).forEach(id->catalog.append(id).append(compiled.get(id).signature()).append(compiled.get(id).capacitySignature()));
        signature=digest(catalog.toString());
    }
    public Map<ResourceLocation,VeinDefinition> definitions(){return definitions;}
    public Dimension dimension(ResourceLocation dimension){return dimensions.get(dimension);}
    public String signature(){return signature;}
    public String signature(ResourceLocation dimension){var d=dimensions.get(dimension);return d==null?digest("district-2|empty|"+dimension):d.signature();}
    public Map<ResourceLocation,String> layoutSignatures(){Map<ResourceLocation,String> result=new HashMap<>();dimensions.forEach((id,d)->result.put(id,d.signature()));return Map.copyOf(result);}
    public Optional<Occurrence> find(Environment environment,ChunkPos chunk){return new VeinPlanner(this,environment).find(chunk);}
    static long typeSeed(long worldSeed,ResourceLocation dimension,ResourceLocation type){return mix(worldSeed^mix(hash(dimension))^Long.rotateLeft(hash(type),23)^SITE_SEED_SALT);}
    static long hash(ResourceLocation id){long value=0xcbf29ce484222325L;for(byte b:id.toString().getBytes(StandardCharsets.UTF_8)){value^=b&0xffL;value*=0x100000001b3L;}return value;}
    static String digest(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static double unit(long value){return (value>>>11)*0x1.0p-53;}
    static long mix(long value){value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;return value^(value>>>31);}
}
