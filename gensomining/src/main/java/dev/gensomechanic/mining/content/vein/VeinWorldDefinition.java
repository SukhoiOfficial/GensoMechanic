package dev.gensomechanic.mining.content.vein;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import static dev.gensomechanic.mining.content.vein.VeinData.*;

/** Compiled schema 1 dimension profile. All biome selectors refer to this reload's registry snapshot. */
public record VeinWorldDefinition(ResourceLocation id, Set<ResourceLocation> dimensions,
        Placement placement, Districts districts, Geology geology, Sampling sampling, VeinDefinition.Filter environment,
        double biomeFraction, double memberFraction, List<Resource> resources, List<BiomeRule> rules) {
    public record Placement(int bucket, double proposalMean, int rounds, double distance, double jitter) {}
    public record Districts(int min, int max, double patchFraction, double step, int radius,
                            int gap, double linkDistance, int attempts, int environmentRadius) {}
    public record Geology(boolean enabled, double scale, double strength, double floor, double coarseScale,
                          double coarseWeight, double warpScale, double warpOffset, double sizeCoupling) {}
    public record Height(int y, double weight) {}
    public record Sampling(List<Height> heights) {
        public Sampling { heights=List.copyOf(heights); }
    }
    public record Variant(ResourceLocation id, double weight) {}
    public record VeinCount(int min, int max) {
        public VeinCount { if(min<2 || max<min || max>32)throw invalid("district vein count must be 2..32 with min <= max"); }
    }
    public record Resource(ResourceLocation category, int priority, double weight, double geologyStrength, List<Variant> variants) {
        public Resource { variants=List.copyOf(variants); }
    }
    public record BiomeRule(String id, int priority, double influence, Set<ResourceLocation> biomes,
                            List<VeinCondition> display, Map<ResourceLocation,Double> multipliers,
                            Map<ResourceLocation,Map<ResourceLocation,Double>> variants, VeinCount districtCount) {
        public BiomeRule { biomes=Set.copyOf(biomes);display=List.copyOf(display);multipliers=sorted(multipliers);variants=sorted(variants); }
        public BiomeRule(String id,int priority,double influence,Set<ResourceLocation> biomes,List<VeinCondition> display,
                         Map<ResourceLocation,Double> multipliers,Map<ResourceLocation,Map<ResourceLocation,Double>> variants) {
            this(id,priority,influence,biomes,display,multipliers,variants,null);
        }
    }
    public VeinWorldDefinition {
        dimensions=Set.copyOf(dimensions);resources=List.copyOf(resources);rules=List.copyOf(rules);
    }
    private static <T> Map<ResourceLocation,T> sorted(Map<ResourceLocation,T> map) {
        var out=new TreeMap<ResourceLocation,T>(Comparator.comparing(ResourceLocation::toString));out.putAll(map);
        return Collections.unmodifiableMap(out);
    }
    public BiomeRule rule(ResourceLocation biome) {
        for(var rule:rules)if(rule.biomes().contains(biome))return rule;
        return null;
    }
    public VeinCount districtCount() { return new VeinCount(districts.min(),districts.max()); }
    public int maxDistrictMembers() {
        return Math.max(districts.max(),rules.stream().filter(r->r.districtCount()!=null).mapToInt(r->r.districtCount().max()).max().orElse(0));
    }
    /** Only applicable variants participate in bounds and signatures of this actual dimension. */
    public VeinWorldDefinition bind(ResourceLocation dimension,Map<ResourceLocation,VeinDefinition> definitions) {
        List<Resource> active=new ArrayList<>();
        Set<ResourceLocation> categories=new HashSet<>(),variantIds=new HashSet<>();
        for(var r:resources) {
            var variants=r.variants().stream().filter(v->definitions.get(v.id()).dimensions().accepts(dimension)).toList();
            if(variants.isEmpty())continue;
            active.add(new Resource(r.category(),r.priority(),r.weight(),r.geologyStrength(),variants));
            categories.add(r.category());variants.forEach(v->variantIds.add(v.id()));
        }
        List<BiomeRule> selected=new ArrayList<>();
        for(var r:rules) {
            Map<ResourceLocation,Double> weights=new HashMap<>();
            r.multipliers().forEach((k,v)->{if(categories.contains(k))weights.put(k,v);});
            Map<ResourceLocation,Map<ResourceLocation,Double>> variants=new HashMap<>();
            r.variants().forEach((category,values)->{
                if(categories.contains(category)) {
                    Map<ResourceLocation,Double> keep=new HashMap<>();
                    values.forEach((k,v)->{if(variantIds.contains(k))keep.put(k,v);});
                    variants.put(category,sorted(keep));
                }
            });
            selected.add(new BiomeRule(r.id(),r.priority(),r.influence(),r.biomes(),r.display(),weights,variants,r.districtCount()));
        }
        return new VeinWorldDefinition(id,Set.of(dimension),placement,districts,geology,sampling,environment,biomeFraction,memberFraction,active,selected);
    }
    String layoutSemantics() {
        StringBuilder s=new StringBuilder().append(id).append('|').append(dimensions.stream().map(Object::toString).sorted().toList())
            .append('|').append(placement).append('|').append(districts).append('|').append(geology).append('|').append(sampling)
            .append('|').append(environment.semantics()).append('|').append(biomeFraction).append('|').append(memberFraction);
        for(var r:resources)s.append('|').append(r);
        for(var r:rules){s.append('|').append(r.id()).append(':').append(r.priority()).append(':').append(r.influence()).append(':')
            .append(r.biomes().stream().map(Object::toString).sorted().toList()).append(':').append(r.multipliers()).append(':').append(r.variants());
            if(r.districtCount()!=null)s.append(":district_count=").append(r.districtCount());}
        return s.toString();
    }
    public static VeinWorldDefinition parse(ResourceLocation id,JsonObject json,Context context,
                                             Map<ResourceLocation,VeinDefinition> definitions) {
        keys(json,"schema_version","dimensions","placement","districts","geology","environment","resources","biome_profiles","requires_mod");
        if(integer(json,"schema_version",0,0,100)!=1)throw invalid("Expected vein_worlds schema_version 1");
        Set<ResourceLocation> dimensions=new HashSet<>();
        for(var value:required(json,"dimensions").getAsJsonArray())if(!dimensions.add(VeinData.id(value)))throw invalid("Duplicate dimension");
        if(dimensions.isEmpty()||dimensions.size()>256)throw invalid("dimensions needs 1..256 IDs");
        var p=optionalObject(json,"placement");
        keys(p,"bucket_chunks","proposals_per_bucket","rounds","district_distance_chunks","distance_variation");
        var placement=new Placement(integer(p,"bucket_chunks",8,4,32),number(p,"proposals_per_bucket",.7,0,4),
            integer(p,"rounds",4,1,8),number(p,"district_distance_chunks",8.5,2,64),number(p,"distance_variation",.1,0,.5));
        var d=optionalObject(json,"districts");
        keys(d,"min_veins","max_veins","patch_fraction","member_step_chunks","radius_chunks","edge_gap_chunks","link_distance_chunks","placement_attempts","environment_radius_chunks");
        int minimum=integer(d,"min_veins",3,2,32);
        var districts=new Districts(minimum,integer(d,"max_veins",8,minimum,32),number(d,"patch_fraction",.65,0,1),
            number(d,"member_step_chunks",5.8,2,64),integer(d,"radius_chunks",26,4,128),
            integer(d,"edge_gap_chunks",1,1,8),number(d,"link_distance_chunks",12,2,128),
            integer(d,"placement_attempts",36,1,128),integer(d,"environment_radius_chunks",6,0,64));
        var g=optionalObject(json,"geology");keys(g,"enabled","region_scale_chunks","strength","baseline_share","coarse_component","domain_warp","size_coupling");
        var coarse=optionalObject(g,"coarse_component");keys(coarse,"scale_multiplier","weight");
        var warp=optionalObject(g,"domain_warp");keys(warp,"scale_chunks","max_offset_chunks");
        var geology=new Geology(bool(g,"enabled",true),number(g,"region_scale_chunks",64,1,65536),
            number(g,"strength",2,0,20),number(g,"baseline_share",.2,0,1),
            number(coarse,"scale_multiplier",2,1,16),number(coarse,"weight",.28,0,1),
            number(warp,"scale_chunks",113,1,65536),number(warp,"max_offset_chunks",15,0,4096),number(g,"size_coupling",.30,0,1));
        var e=optionalObject(json,"environment");keys(e,"biome_sampling","biomes","min_biome_fraction","min_member_fraction");
        var sampling=parseSampling(optionalObject(e,"biome_sampling"));
        var environment=filter(optionalObject(e,"biomes"),context,false);
        List<Resource> resources=new ArrayList<>();Set<ResourceLocation> categories=new HashSet<>(),usedVariants=new HashSet<>();
        for(var element:required(json,"resources").getAsJsonArray()) {
            var r=element.getAsJsonObject();keys(r,"category","priority","weight","geology_strength","variants","requires_mod");
            if(!enabled(r))continue;
            var category=VeinData.id(required(r,"category"));
            var root=definitions.get(category);
            if(root==null||root.variantOf()!=null||!categories.add(category))throw invalid("Invalid or duplicate root category: "+category);
            List<Variant> variants=new ArrayList<>();
            var values=object(r,"variants");
            for(var key:values.keySet().stream().sorted().toList()) {
                var variant=ResourceLocation.tryParse(key);if(variant==null)throw invalid("Invalid variant ID: "+key);
                var v=values.get(key);double weight;boolean optional=false;
                if(v.isJsonObject()) {var o=v.getAsJsonObject();keys(o,"weight","required");weight=number(o,"weight",1,0,1e6);optional=!bool(o,"required",true);}
                else {var o=new JsonObject();o.add("weight",v);weight=number(o,"weight",1,0,1e6);}
                var definition=definitions.get(variant);
                if(definition==null&&optional)continue;
                if(definition==null||!definition.searchId().equals(category)||!usedVariants.add(variant))
                    throw invalid("Missing, duplicate or wrong-root variant: "+variant);
                variants.add(new Variant(variant,weight));
            }
            if(variants.isEmpty()||variants.stream().noneMatch(v->v.weight()>0))throw invalid("Category has no positive variant weight: "+category);
            resources.add(new Resource(category,integer(r,"priority",0,-1024,1024),number(r,"weight",1,0,1e6),
                number(r,"geology_strength",geology.strength(),0,20),variants));
        }
        if(resources.isEmpty()||resources.size()>256||resources.stream().noneMatch(r->r.weight()>0))throw invalid("resources needs 1..256 categories and positive total weight");
        resources.sort(Comparator.comparing(r->r.category().toString()));
        List<BiomeRule> rules=new ArrayList<>();Set<String> ruleIds=new HashSet<>();
        var profiles=json.has("biome_profiles")?json.getAsJsonArray("biome_profiles"):new JsonArray();
        if(profiles.size()>256)throw invalid("Too many biome profiles");
        for(var element:profiles) {
            var r=element.getAsJsonObject();keys(r,"id","priority","influence","biomes","category_multipliers","variant_weights","districts","requires_mod");
            if(!enabled(r))continue;
            String name=string(required(r,"id"));
            if(!name.matches("[a-z0-9_./-]{1,128}")||!ruleIds.add(name))throw invalid("Invalid or duplicate biome profile ID");
            var conditions=conditions(required(r,"biomes").getAsJsonArray(),context,false);
            var multipliers=weights(optionalObject(r,"category_multipliers"),categories);
            Map<ResourceLocation,Map<ResourceLocation,Double>> variantOverrides=new HashMap<>();
            var overrides=optionalObject(r,"variant_weights");
            for(var key:overrides.keySet()) {
                var category=ResourceLocation.tryParse(key);
                if(category==null||!categories.contains(category))throw invalid("Unknown category in variant override: "+key);
                var resource=resources.stream().filter(v->v.category().equals(category)).findFirst().orElseThrow();
                var allowed=new HashSet<ResourceLocation>();resource.variants().forEach(v->allowed.add(v.id()));
                variantOverrides.put(category,weights(overrides.getAsJsonObject(key),allowed));
            }
            VeinCount count=null;
            if(r.has("districts")) {
                var range=object(r,"districts");keys(range,"min_veins","max_veins");
                int min=integer(range,"min_veins",districts.min(),2,32);
                count=new VeinCount(min,integer(range,"max_veins",districts.max(),min,32));
            }
            rules.add(new BiomeRule(name,integer(r,"priority",0,-1024,1024),number(r,"influence",1,0,8),members(conditions),conditions,multipliers,variantOverrides,count));
        }
        rules.sort(Comparator.comparingInt(BiomeRule::priority).reversed().thenComparing(BiomeRule::id));
        for(int i=0;i<rules.size();i++)for(int j=i+1;j<rules.size();j++)
            if(rules.get(i).priority()==rules.get(j).priority()&&!Collections.disjoint(rules.get(i).biomes(),rules.get(j).biomes()))
                throw invalid("Equal-priority biome profiles overlap: "+rules.get(i).id()+" / "+rules.get(j).id());
        return new VeinWorldDefinition(id,dimensions,placement,districts,geology,sampling,environment,
            number(e,"min_biome_fraction",1,Double.MIN_NORMAL,1),number(e,"min_member_fraction",1,Double.MIN_NORMAL,1),resources,rules);
    }
    private static Map<ResourceLocation,Double> weights(JsonObject json,Set<ResourceLocation> allowed) {
        Map<ResourceLocation,Double> out=new HashMap<>();
        for(var key:json.keySet()) {
            var id=ResourceLocation.tryParse(key);
            if(id==null||!allowed.contains(id))throw invalid("Unknown resource in weight map: "+key);
            out.put(id,number(json,key,1,0,1e6));
        }
        return sorted(out);
    }
    private static Sampling parseSampling(JsonObject json) {
        keys(json,"mode","y","samples","range","aggregation");
        String mode=json.has("mode")?string(json.get("mode")):"single_height";
        if(json.has("aggregation")&&!string(json.get("aggregation")).equals("weighted_mean"))throw invalid("Only weighted_mean aggregation is supported");
        List<Height> heights=new ArrayList<>();
        if(mode.equals("single_height")) {
            if(json.has("samples")||json.has("range"))throw invalid("single_height accepts only y");
            heights.add(new Height(integer(json,"y",64,-2032,2031),1));
        } else if(mode.equals("multi_height")) {
            if(json.has("y")||json.has("samples")==json.has("range"))throw invalid("multi_height requires exactly one of samples or range");
            if(json.has("samples"))for(var element:json.getAsJsonArray("samples")) {
                var s=element.getAsJsonObject();keys(s,"y","weight");required(s,"y");
                heights.add(new Height(integer(s,"y",64,-2032,2031),number(s,"weight",1,Double.MIN_NORMAL,1e6)));
            } else {
                var r=object(json,"range");keys(r,"min_y","max_y","step");required(r,"min_y");required(r,"max_y");
                int min=integer(r,"min_y",0,-2032,2031),max=integer(r,"max_y",0,min,2031),step=integer(r,"step",16,1,4064);
                for(int y=min;y<=max;y+=step)heights.add(new Height(y,1));
            }
        } else throw invalid("Unknown biome sampling mode: "+mode);
        if(heights.isEmpty()||heights.size()>32)throw invalid("biome_sampling needs 1..32 unique heights");
        if(heights.stream().map(Height::y).distinct().count()!=heights.size())throw invalid("Duplicate biome sampling heights");
        heights.sort(Comparator.comparingInt(Height::y));double sum=heights.stream().mapToDouble(Height::weight).sum();
        return new Sampling(heights.stream().map(h->new Height(h.y(),h.weight()/sum)).toList());
    }
}


