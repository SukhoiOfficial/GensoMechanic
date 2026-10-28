package dev.gensomechanic.mining.content.vein;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.*;
import net.neoforged.fml.ModList;

/** Per-query-environment sampling. Never changes the world's biome source or shared climate index. */
final class VeinBiomes implements VeinGenerator.Environment {
    private final long seed;
    private final ResourceLocation dimension;
    private final BiomeSource source;
    private final Climate.Sampler sampler;
    private final StableBiomeIndex<Holder<Biome>> stable;
    private final int minY,maxY;

    VeinBiomes(ServerLevel level) {
        this(level.getSeed(),level.dimension().location(),level.getChunkSource().getGenerator().getBiomeSource(),
            level.getChunkSource().randomState().sampler(),level.registryAccess(),level.getMinBuildHeight(),level.getMaxBuildHeight());
    }

    VeinBiomes(long seed,ResourceLocation dimension,BiomeSource source,Climate.Sampler sampler,
               HolderLookup.Provider registries,int minY,int maxY) {
        this.seed=seed;this.dimension=dimension;this.source=source;this.sampler=sampler;this.minY=minY;this.maxY=maxY;
        // Read the actual preset or direct parameter list through vanilla's public codec.
        // TerraBlender injects positional region selection into the vanilla class itself;
        // its serialized parameter list does not represent that sampling behavior.
        // Custom sources and TerraBlender retain their public sampling entry point.
        if(source.getClass()==MultiNoiseBiomeSource.class && !ModList.get().isLoaded("terrablender")) {
            var ops=RegistryOps.create(JsonOps.INSTANCE,registries);
            var encoded=MultiNoiseBiomeSource.CODEC.codec().encodeStart(ops,(MultiNoiseBiomeSource)source).getOrThrow().getAsJsonObject();
            var parameters=encoded.has("preset")
                ? MultiNoiseBiomeSourceParameterList.CODEC.parse(ops,encoded.get("preset")).getOrThrow().value().parameters()
                : MultiNoiseBiomeSource.DIRECT_CODEC.codec().parse(ops,encoded).getOrThrow();
            stable=new StableBiomeIndex<>(parameters.values());
        } else stable=null;
    }

    public long seed(){return seed;}
    public ResourceLocation dimension(){return dimension;}
    public ResourceLocation biomeAt(ChunkPos chunk,int y) {
        int x=QuartPos.fromBlock(chunk.getMiddleBlockX()),z=QuartPos.fromBlock(chunk.getMiddleBlockZ());
        int sampleY=QuartPos.fromBlock(Mth.clamp(y,minY,maxY-1));
        return (stable==null?source.getNoiseBiome(x,sampleY,z,sampler):stable.findValue(sampler.sample(x,sampleY,z)))
            .unwrapKey().orElseThrow().location();
    }
}
