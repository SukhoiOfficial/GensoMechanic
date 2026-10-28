package dev.gensomechanic.mining.content.miner;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Reads the pending reload's fluid tags, not the previous world's bound registry tags. */
public record FluidTagEmptyCondition(TagKey<Fluid> tag) implements ICondition {
    public static final MapCodec<FluidTagEmptyCondition> CODEC = TagKey.codec(Registries.FLUID)
            .xmap(FluidTagEmptyCondition::new, FluidTagEmptyCondition::tag).fieldOf("tag");

    @Override public boolean test(IContext context) { return context.getTag(tag).isEmpty(); }
    @Override public MapCodec<? extends ICondition> codec() { return CODEC; }
}
