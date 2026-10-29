package dev.gensomechanic.metallurgy.content.metallurgy;

import com.mojang.serialization.*;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import dev.gensomechanic.metallurgy.GensoMetallurgy;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.*;
import dev.gensomechanic.metallurgy.content.casting.CastingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import java.util.function.Function;

public enum MetallurgyRecipeTypes implements IRecipeTypeInfo {
    BLAST_FURNACE_SMELTING(BlastFurnaceSmeltingRecipe::new),
    BLAST_FURNACE_FUSING(BlastFurnaceFusingRecipe::new),
    CASTING(CastingRecipe::new);
    private final ResourceLocation id;
    private final DeferredHolder<RecipeSerializer<?>, ?> serializer;
    private final DeferredHolder<RecipeType<?>, ?> type;
    MetallurgyRecipeTypes(Function<MetallurgyRecipeParams, ? extends BasinRecipe> factory) {
        String name = name().toLowerCase(java.util.Locale.ROOT); id = GensoMetallurgy.asResource(name);
        serializer = Registers.SERIALIZERS.register(name, () -> new Serializer<>(factory));
        type = Registers.TYPES.register(name, () -> new RecipeType<>() {});
    }
    @Override public ResourceLocation getId() { return id; }
    @Override @SuppressWarnings("unchecked") public <T extends RecipeSerializer<?>> T getSerializer() { return (T) serializer.get(); }
    @Override @SuppressWarnings("unchecked") public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() { return (RecipeType<R>) type.get(); }
    public static void register(IEventBus bus) { Registers.SERIALIZERS.register(bus); Registers.TYPES.register(bus); MetallurgyLang.register(); }
    private static final class Registers {
        static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, GensoMetallurgy.MOD_ID);
        static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, GensoMetallurgy.MOD_ID);
    }
    public static final class Serializer<R extends BasinRecipe> implements RecipeSerializer<R> {
        private final MapCodec<R> codec;
        public Serializer(Function<MetallurgyRecipeParams, R> factory) {
            codec = MetallurgyRecipeParams.CODEC.flatXmap(p -> {
                R recipe = factory.apply(p); var errors = recipe.validate();
                return errors.isEmpty() ? DataResult.success(recipe) : DataResult.error(() -> String.join("; ", errors));
            }, r -> DataResult.success((MetallurgyRecipeParams) r.getParams()));
        }
        @Override public MapCodec<R> codec() { return codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, R> streamCodec() { return ByteBufCodecs.fromCodecWithRegistries(codec.codec()); }
    }
}
