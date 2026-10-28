package dev.gensomechanic.mining.content.miner;

import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.content.vein.VeinManager;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;

public final class MiningRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, GensoMining.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, GensoMining.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS = DeferredRegister.create(NeoForgeRegistries.CONDITION_SERIALIZERS, GensoMining.MOD_ID);
    static { CONDITIONS.register("fluid_tag_empty", () -> FluidTagEmptyCondition.CODEC); }
    public static final DeferredHolder<RecipeType<?>, RecipeType<MiningRecipe>> TYPE = TYPES.register("mining", () -> new RecipeType<>() {
        @Override public String toString() { return "gensomining:mining"; }
    });
    public static final DeferredHolder<RecipeSerializer<?>, MiningRecipe.Serializer> SERIALIZER = SERIALIZERS.register("mining", MiningRecipe.Serializer::new);
    private static final Map<ReloadableServerResources, Map<ResourceLocation, RecipeHolder<MiningRecipe>>> INDICES = new WeakHashMap<>();
    public static Map<ResourceLocation, RecipeHolder<MiningRecipe>> forServer(MinecraftServer server) {
        return INDICES.computeIfAbsent(server.getServerResources().managers(), resources -> {
            Map<ResourceLocation, List<RecipeHolder<MiningRecipe>>> groups = new TreeMap<>();
            for (var holder : server.getRecipeManager().getAllRecipesFor(TYPE.get()))
                groups.computeIfAbsent(holder.value().veinId(), id -> new ArrayList<>()).add(holder);
            var definitions = VeinManager.definitions(server);
            Set<ResourceLocation> backgrounds = new HashSet<>();
            VeinManager.backgrounds(server).forEach(background -> backgrounds.add(background.id()));
            Map<ResourceLocation, RecipeHolder<MiningRecipe>> result = new HashMap<>();
            groups.forEach((vein, recipes) -> {
                boolean known = definitions.containsKey(vein) || backgrounds.contains(vein);
                if (known && recipes.size() == 1) result.put(vein, recipes.getFirst());
                else GensoMining.LOGGER.error("Disabled mining recipes {} for {}: {}", recipes.stream().map(RecipeHolder::id).toList(), vein,
                        known ? "multiple recipes bind the same mining source" : "unknown vein_id");
            });
            return Map.copyOf(result);
        });
    }
    public static void register(IEventBus bus) { TYPES.register(bus); SERIALIZERS.register(bus); CONDITIONS.register(bus); MiningLang.register(); }
    private MiningRecipes() {}
}
