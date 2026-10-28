package dev.gensomechanic.mining.content.vein;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import static dev.gensomechanic.mining.content.vein.VeinData.*;

/** Schema 1 describes one resource; placement belongs exclusively to a dimension profile. */
public record VeinDefinition(ResourceLocation id, Component name, ResourceLocation icon,
        Filter dimensions, Filter biomes, double biomeFraction, double memberFraction,
        List<Capacity> capacities, int totalWeight, ResourceLocation variantOf, int mapColor,
        List<SizeBand> sizes, Shape shape) {
    public record SizeBand(int min, int max, double weight) {}
    public record Capacity(int miners, int weight) {}
    public record Shape(int radius, int minWidth, double maxAspect, double edgeJitter, int preferredGap) {
        public static Shape defaults(int max) {
            int width=max<4 ? 1 : 2;
            return new Shape(autoRadius(max,width,2.5),width,2.5,.25,0);
        }
        public static int autoRadius(int max,int width,double aspect) {
            int longest=1;
            for (int size=1;size<=max;size++) for (int w=Math.min(width,size);w<=Math.sqrt(size);w++) {
                int length=(size+w-1)/w;
                if (length<=aspect*w || size<width*width) longest=Math.max(longest,length);
            }
            return longest/2+(max>4 ? 1 : 0);
        }
    }
    public record Filter(boolean unrestricted, Set<ResourceLocation> allow, Set<ResourceLocation> deny,
                         List<VeinCondition> displayAllow, List<VeinCondition> displayDeny) {
        public static final Filter ALL=new Filter(true,Set.of(),Set.of());
        public Filter(boolean unrestricted,Set<ResourceLocation> allow,Set<ResourceLocation> deny) {
            this(unrestricted,allow,deny,List.of(),List.of());
        }
        public Filter {
            allow=Set.copyOf(allow);deny=Set.copyOf(deny);
            displayAllow=List.copyOf(displayAllow);displayDeny=List.copyOf(displayDeny);
        }
        public boolean accepts(ResourceLocation value) { return !deny.contains(value) && (unrestricted || allow.contains(value)); }
        public String semantics() {
            return unrestricted+":"+allow.stream().map(Object::toString).sorted().toList()+":"+deny.stream().map(Object::toString).sorted().toList();
        }
    }
    public VeinDefinition {
        name=name.copy();capacities=List.copyOf(capacities);sizes=List.copyOf(sizes);
        if (sizes.isEmpty() || capacities.isEmpty()) throw new IllegalArgumentException("Empty sizes or capacities");
    }
    @Override public Component name() { return name.copy(); }
    public ResourceLocation searchId() { return variantOf==null ? id : variantOf; }
    public int minChunks() { return sizes.stream().mapToInt(SizeBand::min).min().orElseThrow(); }
    public int maxChunks() { return sizes.stream().mapToInt(SizeBand::max).max().orElseThrow(); }
    public int chooseSize(long random,double richness,double coupling) {
        double total=sizes.stream().mapToDouble(SizeBand::weight).sum();
        double roll=Math.pow(VeinGenerator.unit(VeinGenerator.mix(random)),1+coupling*(.5-richness))*total;
        SizeBand selected=sizes.getLast();
        for (var band:sizes) { roll-=band.weight();if(roll<0){selected=band;break;} }
        return selected.min()+(int)(VeinGenerator.unit(VeinGenerator.mix(random^79))*(selected.max()-selected.min()+1));
    }
    public int chooseCapacity(long random) {
        int roll=(int)(VeinGenerator.unit(VeinGenerator.mix(random))*totalWeight);
        for (var capacity:capacities) { roll-=capacity.weight();if(roll<0)return capacity.miners(); }
        return capacities.getLast().miners();
    }
    String layoutSemantics() {
        return id+"|"+biomes.semantics()+"|"+biomeFraction+"|"+memberFraction+"|"+sizes+"|"+shape+"|"+variantOf;
    }
    public static VeinDefinition parse(ResourceLocation id,JsonObject json,Registry<Biome> registry,
            Map<ResourceLocation,Collection<Holder<Biome>>> tags,net.minecraft.core.HolderLookup.Provider registries) {
        keys(json,"schema_version","name","icon","generation","capacity","variant_of","map_color","requires_mod");
        if (integer(json,"schema_version",0,0,Integer.MAX_VALUE)!=1)
            throw invalid("Expected gensomining vein schema_version 1");
        if (required(json,"name").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4096)
            throw invalid("name exceeds 4096-byte metadata budget");
        Component name=net.minecraft.network.chat.ComponentSerialization.CODEC.parse(
            net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,registries),json.get("name")).getOrThrow(JsonParseException::new);
        ResourceLocation icon=VeinData.id(required(json,"icon"));
        if (!BuiltInRegistries.ITEM.containsKey(icon)) throw invalid("Unknown icon item: "+icon);
        String color=string(required(json,"map_color"));
        if (!color.matches("#[0-9a-fA-F]{6}")) throw invalid("map_color must be #RRGGBB");
        var generation=object(json,"generation");
        keys(generation,"dimensions","biomes","min_biome_fraction","min_member_fraction","size_chunks","shape");
        List<SizeBand> sizes=new ArrayList<>();
        for(var element:required(generation,"size_chunks").getAsJsonArray()) {
            var band=element.getAsJsonObject();keys(band,"min","max","weight");
            int min=integer(band,"min",-1,1,VeinGenerationConfig.limits().maxChunks());
            int max=integer(band,"max",-1,min,VeinGenerationConfig.limits().maxChunks());
            double weight=number(band,"weight",1,Double.MIN_NORMAL,1e6);
            if(min<1||max<min)throw invalid("size_chunks needs min and max");
            if(!sizes.isEmpty()&&min<=sizes.getLast().max())throw invalid("Size bands must be ascending and non-overlapping");
            sizes.add(new SizeBand(min,max,weight));
        }
        if(sizes.isEmpty()||sizes.size()>64)throw invalid("size_chunks needs 1..64 bands");
        int min=sizes.getFirst().min(),max=sizes.getLast().max();
        var s=optionalObject(generation,"shape");
        keys(s,"radius_chunks","min_width_chunks","max_aspect_ratio","edge_jitter","preferred_gap_chunks");
        int width=integer(s,"min_width_chunks",min<4?1:2,1,64);
        double aspect=number(s,"max_aspect_ratio",2.5,1,16),jitter=number(s,"edge_jitter",.25,0,1);
        int radius=integer(s,"radius_chunks",Shape.autoRadius(max,width,aspect),0,VeinGenerationConfig.limits().maxRadius());
        if((long)width*width>min||radius>VeinGenerationConfig.limits().maxRadius())throw invalid("Shape width/radius exceeds size or budget");
        for(var band:sizes)for(int size=band.min();size<=band.max();size++) {
            boolean fits=false;
            for(int w=width;w<=Math.sqrt(size);w++) {
                int length=(size+w-1)/w;
                if(length<=aspect*w&&length<=2*radius+1){fits=true;break;}
            }
            if(!fits)throw invalid("Shape cannot accommodate size "+size);
        }
        var shape=new Shape(radius,width,aspect,jitter,integer(s,"preferred_gap_chunks",0,0,32));
        List<Capacity> capacities=new ArrayList<>();int total=0;
        for(var element:required(json,"capacity").getAsJsonArray()) {
            var cap=element.getAsJsonObject();keys(cap,"miners","weight");
            int miners=integer(cap,"miners",0,1,1_000_000),weight=integer(cap,"weight",1,1,1_000_000);
            if(miners<1)throw invalid("capacity.miners is required");
            total=Math.addExact(total,weight);capacities.add(new Capacity(miners,weight));
        }
        if(capacities.isEmpty()||capacities.size()>256)throw invalid("capacity needs 1..256 entries");
        var context=new Context(registry,tags);
        return new VeinDefinition(id,name,icon,
            filter(optionalObject(generation,"dimensions"),context,true),filter(optionalObject(generation,"biomes"),context,false),
            number(generation,"min_biome_fraction",1,Double.MIN_NORMAL,1),number(generation,"min_member_fraction",1,Double.MIN_NORMAL,1),
            capacities,total,json.has("variant_of")?VeinData.id(json.get("variant_of")):null,Integer.parseInt(color.substring(1),16),sizes,shape);
    }
}


