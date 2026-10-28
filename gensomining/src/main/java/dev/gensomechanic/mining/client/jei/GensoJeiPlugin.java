package dev.gensomechanic.mining.client.jei;

import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.client.ScannerClient;
import dev.gensomechanic.mining.content.miner.*;
import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class GensoJeiPlugin implements IModPlugin {
    public static final RecipeType<dev.gensomechanic.mining.network.MiningNetwork.Entry> VEIN_GENERATION = new RecipeType<>(GensoMining.asResource("vein_generation"), dev.gensomechanic.mining.network.MiningNetwork.Entry.class);
    public static final RecipeType<MiningDisplay> MINING = new RecipeType<>(GensoMining.asResource("mining"), MiningDisplay.class);
    @Override public ResourceLocation getPluginUid() { return GensoMining.asResource("jei"); }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) { registration.addRecipeCategories(new VeinGenerationCategory(registration.getJeiHelpers().getGuiHelper()), new MiningCategory(registration.getJeiHelpers().getGuiHelper())); }
    private static mezz.jei.api.runtime.IJeiRuntime runtime;
    private static List<VeinIngredient> ingredients = List.of();
    private static List<dev.gensomechanic.mining.network.MiningNetwork.Entry> generation = List.of();
    private static List<MiningDisplay> mining = List.of();
    // JEI hides recipes by object identity. Reuse page instances across catalogue refreshes.
    private static final Map<MiningDisplay, MiningDisplay> knownMining = new HashMap<>();
    private static final Map<dev.gensomechanic.mining.network.MiningNetwork.Entry, dev.gensomechanic.mining.network.MiningNetwork.Entry> knownGeneration = new HashMap<>();
    private static <T> List<T> canonical(List<T> values, Map<T, T> known) {
        return values.stream().map(value -> known.getOrDefault(value, value)).toList();
    }
    private static <T> List<T> remember(List<T> values, Map<T, T> known) {
        return values.stream().filter(value -> known.putIfAbsent(value, value) == null).toList();
    }
    private static boolean listening;
    public GensoJeiPlugin() {
        ScannerClient.catalogueChanged = GensoJeiPlugin::refresh;
        if (!listening) {
            listening = true;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RecipesUpdatedEvent event) -> refresh());
        }
    }
    @Override public void registerIngredients(IModIngredientRegistration registration) {
        ingredients = ScannerClient.miningEntries().stream().map(e -> new VeinIngredient(e.id())).toList();
        registration.register(VeinIngredient.TYPE, ingredients, new VeinIngredient.Helper(), new VeinIngredient.Renderer(), VeinIngredient.CODEC);
    }
    private static List<MiningDisplay> miningPages() {
        var connection = Minecraft.getInstance().getConnection(); if (connection == null) return List.of();
        var recipes = connection.getRecipeManager().getAllRecipesFor(MiningRecipes.TYPE.get());
        Map<ResourceLocation, Long> counts = new HashMap<>(); recipes.forEach(r -> counts.merge(r.value().veinId(), 1L, Long::sum));
        return recipes.stream().filter(r -> counts.get(r.value().veinId()) == 1 && ScannerClient.miningEntry(r.value().veinId()) != null)
                .flatMap(r -> MiningDisplay.pages(r).stream()).toList();
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        var connection = Minecraft.getInstance().getConnection(); if (connection == null) return;
        knownGeneration.clear(); knownMining.clear();
        generation = ScannerClient.allEntries(); mining = miningPages();
        remember(generation, knownGeneration); remember(mining, knownMining);
        registration.addRecipes(VEIN_GENERATION, generation); registration.addRecipes(MINING, mining);
    }
    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime value) { runtime = value;
        if (!dev.gensomechanic.mining.content.vein.ExplorationArchiveItem.mapsAvailable()) {
            var hidden=value.getIngredientManager().getAllIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK).stream().filter(s->s.is(MiningContent.ARCHIVE.get())).toList();
            if(!hidden.isEmpty())value.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,hidden);
        }
        refresh(); }
    @Override public void onRuntimeUnavailable() { runtime = null; ingredients = List.of(); generation = List.of(); mining = List.of(); knownGeneration.clear(); knownMining.clear(); }
    private static void refresh() {
        if (runtime == null) return;
        var next = canonical(ScannerClient.allEntries(), knownGeneration); var pages = canonical(miningPages(), knownMining);
        var nextIngredients = ScannerClient.miningEntries().stream().map(e -> new VeinIngredient(e.id())).toList();
        if (next.equals(generation) && pages.equals(mining) && nextIngredients.equals(ingredients)) return;
        var manager = runtime.getRecipeManager(); var ingredientManager = runtime.getIngredientManager();
        manager.hideRecipes(VEIN_GENERATION, generation); manager.hideRecipes(MINING, mining);
        if (!ingredients.isEmpty()) ingredientManager.removeIngredientsAtRuntime(VeinIngredient.TYPE, ingredients);
        ingredients = nextIngredients; generation = next; mining = pages;
        if (!ingredients.isEmpty()) ingredientManager.addIngredientsAtRuntime(VeinIngredient.TYPE, ingredients);
        manager.addRecipes(VEIN_GENERATION, remember(generation, knownGeneration)); manager.addRecipes(MINING, remember(mining, knownMining));
        manager.unhideRecipes(VEIN_GENERATION, generation); manager.unhideRecipes(MINING, mining);
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) { registration.addRecipeCatalyst(MiningContent.MECHANICAL_MINER.asStack(), MINING); }
}
