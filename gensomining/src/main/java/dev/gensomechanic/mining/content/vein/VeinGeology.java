package dev.gensomechanic.mining.content.vein;

import net.minecraft.resources.ResourceLocation;
import static dev.gensomechanic.mining.content.vein.VeinGenerator.*;

/** Smooth continuous fields. A category's seed is independent of catalog order and neighboring types. */
public final class VeinGeology {
    public static double richness(long seed,ResourceLocation dimension,ResourceLocation category,
                                  VeinWorldDefinition.Geology settings,double x,double z) {
        if(!settings.enabled())return .5;
        long channel=typeSeed(seed,dimension,category);
        double dx=(noise(channel^0x5857415250L,x/settings.warpScale(),z/settings.warpScale())*2-1)*settings.warpOffset();
        double dz=(noise(channel^0x5A57415250L,x/settings.warpScale(),z/settings.warpScale())*2-1)*settings.warpOffset();
        x=(x+dx)/settings.scale();z=(z+dz)/settings.scale();
        return (1-settings.coarseWeight())*noise(channel,x,z)+settings.coarseWeight()*noise(channel^0x434F41525345L,x/settings.coarseScale(),z/settings.coarseScale());
    }
    private static double noise(long seed,double x,double z) {
        long ix=(long)Math.floor(x),iz=(long)Math.floor(z);double fx=fade(x-ix),fz=fade(z-iz);
        return lerp(fz,lerp(fx,value(seed,ix,iz),value(seed,ix+1,iz)),lerp(fx,value(seed,ix,iz+1),value(seed,ix+1,iz+1)));
    }
    private static double value(long seed,long x,long z){return unit(mix(seed^mix(x*0x9E3779B97F4A7C15L)^mix(z*0xD1B54A32D192ED03L)));}
    private static double fade(double t){return t*t*t*(t*(t*6-15)+10);}
    private static double lerp(double t,double a,double b){return a+t*(b-a);}
    private VeinGeology(){}
}
