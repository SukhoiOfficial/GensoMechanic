package dev.gensomechanic.mining.content.vein;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import dev.gensomechanic.mining.network.MiningNetwork;
import dev.gensomechanic.mining.content.miner.MiningNumbers;

/** Public configuration only: no world seed, noise values, centers or undiscovered instances. */
final class VeinPublicMetadata {
    static MiningNetwork.Entry entry(VeinGenerator generator,VeinDefinition definition) {
        List<Component> lines=new ArrayList<>();
        double total=definition.sizes().stream().mapToDouble(VeinDefinition.SizeBand::weight).sum();
        lines.add(text("area",definition.sizes().stream().map(b->b.min()+"–"+b.max()+" ("+number(100*b.weight()/total)+"%)").collect(java.util.stream.Collectors.joining(", "))));
        List<VeinCondition> dimensions=new ArrayList<>();
        for(var id:generator.layoutSignatures().keySet().stream().sorted(Comparator.comparing(Object::toString)).toList()) {
            var compiled=generator.dimension(id);if(!compiled.definitions().containsKey(definition.id()))continue;
            dimensions.add(new VeinCondition(id.toString(),List.of(id)));var p=compiled.profile();
            var resource=p.resources().stream().filter(r->r.category().equals(definition.searchId())).findFirst().orElseThrow();
            var variant=resource.variants().stream().filter(v->v.id().equals(definition.id())).findFirst().orElseThrow();
            lines.add(text("profile_weights",id.toString(),number(resource.weight()),number(variant.weight())));
            var denied=p.environment().displayDeny();if(!denied.isEmpty())lines.add(text("profile_deny",denied.stream().map(VeinCondition::value).collect(java.util.stream.Collectors.joining(", "))));
            var allowed=p.environment().displayAllow();if(!allowed.isEmpty())lines.add(text("profile_allow",allowed.stream().map(VeinCondition::value).collect(java.util.stream.Collectors.joining(", "))));
            if(p.sampling().heights().size()>1)lines.add(text("sampling",p.sampling().heights().stream().map(v->v.y()+":"+number(v.weight())).collect(java.util.stream.Collectors.joining(", "))));
            for(var rule:p.rules()) {
                double multiplier=rule.multipliers().getOrDefault(definition.searchId(),1d);
                double weight=rule.variants().getOrDefault(definition.searchId(),Map.of()).getOrDefault(definition.id(),variant.weight());
                if(multiplier==1&&weight==variant.weight())continue;
                lines.add(text("regional_weight",rule.display().stream().map(VeinCondition::value).collect(java.util.stream.Collectors.joining(", ")),number(multiplier),number(weight)));
            }
        }
        return new MiningNetwork.Entry(definition.id(),definition.name(),definition.icon(),definition.variantOf(),
            List.of(dimensions,definition.dimensions().displayDeny(),definition.biomes().displayAllow(),definition.biomes().displayDeny()),definition.mapColor(),lines);
    }
    private static String number(double n){return MiningNumbers.format(n);}
    private static Component text(String key,Object...args){return Component.translatable("jei.gensomining.generation."+key,args);}
    private VeinPublicMetadata(){}
}
