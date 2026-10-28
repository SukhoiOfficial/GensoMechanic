package dev.gensomechanic.mining.content.miner;

import com.mojang.serialization.*;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** A 1.21 recipe with no grid input; optional consumables belong to cycle selection. */
public final class MiningRecipe implements Recipe<RecipeInput> {
    public static final int MAX_OUTPUTS = 9;
    public record Definition(ResourceLocation vein, Optional<ResourceLocation> display, double stress, int duration,
                             List<MiningModifiers.Output> outputs, MiningModifiers.Choices modifiers, List<Ingredient> inputs) {}
    private static final MapCodec<Definition> DEFINITION = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("vein_id").forGetter(Definition::vein),
            ResourceLocation.CODEC.optionalFieldOf("display_block").forGetter(Definition::display),
            MiningModifiers.number(0, 16384).fieldOf("stress").forGetter(Definition::stress),
            MiningModifiers.INTEGER.validate(n -> n >= 1 && n <= 72000000 ? DataResult.success(n)
                    : DataResult.error(() -> "processing_time must be 1..72000000"))
                    .fieldOf("processing_time").forGetter(Definition::duration),
            MiningModifiers.OUTPUT.listOf(1, 9).fieldOf("results").forGetter(Definition::outputs),
            MiningModifiers.Choices.CODEC.optionalFieldOf("modifiers", MiningModifiers.Choices.EMPTY).forGetter(Definition::modifiers),
            Ingredient.CODEC.listOf().validate(l -> l.isEmpty() ? DataResult.success(l)
                    : DataResult.error(() -> "Mining has no mandatory ingredients"))
                    .optionalFieldOf("ingredients", List.of()).forGetter(Definition::inputs),
            HeatCondition.CODEC.validate(heat -> heat == HeatCondition.NONE ? DataResult.success(heat)
                    : DataResult.error(() -> "Mining does not support heat requirements"))
                    .optionalFieldOf("heat_requirement", HeatCondition.NONE).forGetter(d -> HeatCondition.NONE)
    ).apply(i, (vein, display, stress, duration, outputs, modifiers, inputs, heat) -> new Definition(vein, display, stress, duration, outputs, modifiers, inputs)));
    public static final MapCodec<MiningRecipe> CODEC = DEFINITION.flatXmap(d -> {
        try { return DataResult.success(new MiningRecipe(d)); }
        catch (IllegalArgumentException e) { return DataResult.error(e::getMessage); }
    }, r -> DataResult.success(r.definition));
    private final Definition definition;
    private final MiningModifiers modifiers;
    public MiningRecipe(Definition definition) {
        this.definition = definition;
        definition.display.ifPresent(id -> {
            if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("Unknown display_block " + id);
        });
        modifiers = new MiningModifiers(definition.outputs, definition.modifiers, definition.duration, definition.stress);
    }
    public ResourceLocation veinId() { return definition.vein; }
    public ResourceLocation displayBlock() { return definition.display.orElse(null); }
    public MiningModifiers modifiers() { return modifiers; }
    public int duration() { return definition.duration; }
    public float stress() { return (float) definition.stress; }
    public String fingerprint(HolderLookup.Provider registries) {
        var json = CODEC.codec().encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), this).getOrThrow().getAsJsonObject();
        json.remove("display_block"); return json.toString();
    }
    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return definition.outputs.getFirst().stack(); }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return MiningRecipes.SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return MiningRecipes.TYPE.get(); }
    public static final class Serializer implements RecipeSerializer<MiningRecipe> {
        public MapCodec<MiningRecipe> codec() { return CODEC; }
        public StreamCodec<RegistryFriendlyByteBuf, MiningRecipe> streamCodec() { return ByteBufCodecs.fromCodecWithRegistries(CODEC.codec()); }
    }
}
