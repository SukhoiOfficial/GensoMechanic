package dev.gensomechanic.mining.content.vein;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import static dev.gensomechanic.mining.content.vein.VeinGenerator.*;

/** Registry-backed filters and data-defined district categories, with no hard-coded ore IDs. */
final class VeinResources {
    record Sample(ResourceLocation biome,double weight) {}
    record Category(VeinWorldDefinition.Resource resource,double richness) {}
    record Selection(VeinDefinition definition,int size,int capacity) {}
    private record Column(long position,int y) {}
    private record Choice(Category category,double base,double regional) {}
    private record ConfiguredVariant(VeinWorldDefinition.Resource resource,VeinWorldDefinition.Variant variant) {}
    private final Map<ResourceLocation,ConfiguredVariant> variants=new HashMap<>();
    private final Dimension dimension;
    private final Environment environment;
    private final Runnable checkpoint,completed;
    private final VeinCache<Column,ResourceLocation> cache;
    VeinResources(Dimension d,Environment e,Runnable check,Runnable complete,int limit){dimension=d;environment=e;checkpoint=check;completed=complete;cache=new VeinCache<>(limit,v->1);
        for(var resource:d.profile().resources())for(var variant:resource.variants())variants.put(variant.id(),new ConfiguredVariant(resource,variant));}
    private List<Sample> column(ChunkPos p) {
        List<Sample> out=new ArrayList<>();
        for(var h:dimension.profile().sampling().heights()) {
            var key=new Column(p.toLong(),h.y());var biome=cache.get(key);
            if(biome==null){checkpoint.run();biome=environment.biomeAt(p,h.y());cache.put(key,biome);completed.run();}
            out.add(new Sample(biome,h.weight()));
        }
        return out;
    }
    private static double fraction(List<Sample> column,VeinDefinition.Filter f){double sum=0;for(var s:column)if(f.accepts(s.biome))sum+=s.weight;return sum;}
    boolean environment(ChunkPos p){return fraction(column(p),dimension.profile().environment())+1e-12>=dimension.profile().biomeFraction();}
    /** District centre decides the count; multi-height samples vote by weight, ties use profile order. */
    VeinWorldDefinition.VeinCount districtCount(ChunkPos p) {
        var profile=dimension.profile();Map<VeinWorldDefinition.BiomeRule,Double> shares=new HashMap<>();double fallback=0;
        for(var sample:column(p)) {
            var rule=profile.rule(sample.biome());
            if(rule==null || rule.districtCount()==null)fallback+=sample.weight();
            else shares.merge(rule,sample.weight(),Double::sum);
        }
        VeinWorldDefinition.VeinCount selected=profile.districtCount();double best=-1;
        for(var rule:profile.rules()) {
            double share=shares.getOrDefault(rule,0d);
            if(share>0 && share>best){selected=rule.districtCount();best=share;}
        }
        return fallback>best?profile.districtCount():selected;
    }
    boolean accepts(VeinDefinition d,ChunkPos p){return memberBits(d,p)==3;}
    /** A zero effective weight disables this variant at the actual member column, independent of shape thresholds. */
    boolean permits(VeinDefinition d,ChunkPos p) {
        var configured=variants.get(d.id());
        return configured!=null&&variantWeight(configured.resource(),configured.variant(),column(p))>0;
    }
    int memberBits(VeinDefinition d,ChunkPos p) {
        var c=column(p);var profile=dimension.profile();
        return (fraction(c,profile.environment())+1e-12>=profile.biomeFraction()?1:0)
            |(fraction(c,d.biomes())+1e-12>=d.biomeFraction()?2:0);
    }
    Category category(VeinSites.Site site) {
        var profile=dimension.profile();var center=column(site.pos());
        if(fraction(center,profile.environment())+1e-12<profile.biomeFraction())return null;
        int r=profile.districts().environmentRadius();List<Sample> samples=new ArrayList<>();
        int[] offsets=r==0?new int[]{0}:new int[]{-r,0,r};
        for(int dx:offsets)for(int dz:offsets)for(var sample:column(new ChunkPos(site.pos().x+dx,site.pos().z+dz)))
            if(profile.environment().accepts(sample.biome))samples.add(sample);
        double sampleWeight=samples.stream().mapToDouble(Sample::weight).sum();
        List<Choice> choices=new ArrayList<>();double baseSum=0,regionalSum=0;
        for(var resource:profile.resources()) {
            if(resource.weight()<=0||resource.variants().stream().noneMatch(v->variantWeight(resource,v,center)>0&&fraction(center,dimension.definitions().get(v.id()).biomes())+1e-12>=dimension.definitions().get(v.id()).biomeFraction()))continue;
            double logWeight=0;boolean excluded=false;
            for(var sample:samples) {
                var rule=profile.rule(sample.biome);if(rule==null||rule.influence()==0)continue;
                double multiplier=rule.multipliers().getOrDefault(resource.category(),1d);
                if(multiplier==0){excluded=true;break;}
                logWeight+=sample.weight/sampleWeight*rule.influence()*Math.log(multiplier);
            }
            if(excluded)continue;
            double richness=VeinGeology.richness(environment.seed(),environment.dimension(),resource.category(),profile.geology(),site.x(),site.z());
            double base=resource.weight()*Math.exp(logWeight);
            double regional=base*(profile.geology().enabled()?Math.exp(resource.geologyStrength()*(richness-.5)):1);
            choices.add(new Choice(new Category(resource,richness),base,regional));baseSum+=base;regionalSum+=regional;
        }
        if(choices.isEmpty())return null;
        double roll=unit(mix(site.seed()^0x5245534F55524345L)),floor=profile.geology().floor();
        for(var choice:choices){roll-=floor*choice.base/baseSum+(1-floor)*choice.regional/regionalSum;if(roll<0)return choice.category;}
        return choices.getLast().category;
    }
    Selection member(Category category,ChunkPos center,long seed) {
        var profile=dimension.profile();var column=column(center);var resource=category.resource;
        List<VeinDefinition> candidates=new ArrayList<>();List<Double> weights=new ArrayList<>();double total=0;
        for(var variant:resource.variants()) {
            var d=dimension.definitions().get(variant.id());
            if(fraction(column,d.biomes())+1e-12<d.biomeFraction())continue;
            double weight=variantWeight(resource,variant,column);
            if(weight>0){candidates.add(d);weights.add(weight);total+=weight;}
        }
        if(candidates.isEmpty())return null;
        double roll=unit(mix(seed^345))*total;var d=candidates.getLast();
        for(int i=0;i<candidates.size();i++){roll-=weights.get(i);if(roll<0){d=candidates.get(i);break;}}
        long type=mix(seed^hash(d.id()));
        return new Selection(d,d.chooseSize(type^567,category.richness,profile.geology().sizeCoupling()),d.chooseCapacity(type^987));
    }
    private double variantWeight(VeinWorldDefinition.Resource resource,VeinWorldDefinition.Variant variant,List<Sample> samples) {
        double weight=0;
        for(var sample:samples) {
            var rule=dimension.profile().rule(sample.biome);var overrides=rule==null?null:rule.variants().get(resource.category());
            weight+=sample.weight*(overrides==null?variant.weight():overrides.getOrDefault(variant.id(),variant.weight()));
        }
        return weight;
    }
    void clear(){cache.clear();}
    int peak(){return cache.peak();}
}
