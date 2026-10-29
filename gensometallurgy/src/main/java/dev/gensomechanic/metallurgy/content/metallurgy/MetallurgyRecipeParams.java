package dev.gensomechanic.metallurgy.content.metallurgy;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.*;
import com.simibubi.create.foundation.codec.CreateCodecs;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import java.util.*;

/** Modern Create parameters with a lazy, registry-independent declaration for tagged outputs. */
public final class MetallurgyRecipeParams extends ProcessingRecipeParams {
    public static final Codec<TagOutput> TAG_OUTPUT = RecordCodecBuilder.create(i -> i.group(
        ResourceLocation.CODEC.fieldOf("tag").forGetter(o -> o.tag.location()),
        Codec.intRange(1, 99).optionalFieldOf("count", 1).forGetter(o -> o.count),
        Codec.floatRange(0, 1).optionalFieldOf("chance", 1f).forGetter(TagOutput::getChance)
    ).apply(i, TagOutput::new));
    public static final Codec<ProcessingOutput> OUTPUT = Codec.either(TAG_OUTPUT, ProcessingOutput.CODEC_NEW)
        .xmap(e -> e.map(o -> o, o -> o), o -> o instanceof TagOutput t ? Either.left(t) : Either.right(o));
    public static final MapCodec<MetallurgyRecipeParams> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.either(CreateCodecs.FLAT_SIZED_FLUID_INGREDIENT_WITH_TYPE, Ingredient.CODEC).listOf().fieldOf("ingredients")
            .forGetter(MetallurgyRecipeParams::inputDeclarations),
        Codec.either(FluidStack.CODEC, OUTPUT).listOf().fieldOf("results")
            .forGetter(MetallurgyRecipeParams::outputDeclarations),
        Codec.intRange(1, 72000000).optionalFieldOf("processing_time", 200).forGetter(p -> p.processingDuration),
        HeatCondition.CODEC.optionalFieldOf("heat_requirement", HeatCondition.NONE).forGetter(p -> p.requiredHeat)
    ).apply(i, MetallurgyRecipeParams::new));
    private MetallurgyRecipeParams(List<Either<SizedFluidIngredient, Ingredient>> inputs,
            List<Either<FluidStack, ProcessingOutput>> outputs, int duration, HeatCondition heat) {
        inputs.forEach(e -> e.ifLeft(fluidIngredients::add).ifRight(ingredients::add));
        outputs.forEach(e -> e.ifLeft(fluidResults::add).ifRight(results::add));
        processingDuration = duration; requiredHeat = heat;
    }
    private List<Either<SizedFluidIngredient, Ingredient>> inputDeclarations() { return ingredients(); }
    private List<Either<FluidStack, ProcessingOutput>> outputDeclarations() { return results(); }
    public static final class TagOutput extends ProcessingOutput {
        private final TagKey<Item> tag;
        private final int count;
        private TagOutput(ResourceLocation id, int count, float chance) {
            super(ItemStack.EMPTY, chance); tag = TagKey.create(Registries.ITEM, id); this.count = count;
        }
        @Override public ItemStack getStack() {
            return BuiltInRegistries.ITEM.getTag(tag).flatMap(t -> t.stream().findFirst())
                .map(h -> new ItemStack(h.value(), count)).orElse(ItemStack.EMPTY);
        }
        @Override public ItemStack rollOutput(RandomSource random) {
            ItemStack result = getStack();
            if (getChance() <= 0 || result.isEmpty()) return ItemStack.EMPTY;
            int n = 0;
            for (int i = 0; i < count; i++) if (getChance() >= 1 || random.nextFloat() < getChance()) n++;
            result.setCount(n); return result;
        }
    }
}
