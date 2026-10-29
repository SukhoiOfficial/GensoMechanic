package dev.gensomechanic.metallurgy.client.jei;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.compat.jei.DoubleItemIcon;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.ItemIcon;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import dev.gensomechanic.metallurgy.GensoMetallurgy;
import dev.gensomechanic.metallurgy.client.jei.category.BlastFurnaceCategory;
import dev.gensomechanic.metallurgy.client.jei.category.CastingCategory;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.BlastFurnaceSmeltingRecipe;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.BlastFurnaceFusingRecipe;
import dev.gensomechanic.metallurgy.content.casting.CastingRecipe;
import dev.gensomechanic.metallurgy.MetallurgyBlocks;
import dev.gensomechanic.metallurgy.GensoFluids;
import dev.gensomechanic.metallurgy.GensoFluids.MoltenMetal;
import dev.gensomechanic.metallurgy.content.metallurgy.MetallurgyRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@JeiPlugin
public final class MetallurgyJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_ID = GensoMetallurgy.asResource("metallurgy_jei");
    private static IJeiRuntime jeiRuntime;
    private static boolean tagListenerRegistered;
    private static EnumSet<MoltenMetal> visibleMoltenMetals = EnumSet.allOf(MoltenMetal.class);

    public static final RecipeType<RecipeHolder<BasinRecipe>> BLAST_FURNACE_SMELTING =
        RecipeType.createRecipeHolderType(GensoMetallurgy.asResource("blast_furnace_smelting"));
    public static final RecipeType<RecipeHolder<BasinRecipe>> BLAST_FURNACE_FUSING =
        RecipeType.createRecipeHolderType(GensoMetallurgy.asResource("blast_furnace_fusing"));
    public static final RecipeType<RecipeHolder<BasinRecipe>> CASTING =
        RecipeType.createRecipeHolderType(GensoMetallurgy.asResource("casting"));

    private BlastFurnaceCategory blastFurnaceSmeltingCategory;
    private BlastFurnaceCategory blastFurnaceFusingCategory;
    private CastingCategory castingCategory;

    private void createCategories() {
        blastFurnaceSmeltingCategory = new BlastFurnaceCategory(
            new CreateRecipeCategory.Info<>(
                BLAST_FURNACE_SMELTING,
                Component.translatable("gensometallurgy.recipe.blast_furnace_smelting"),
                new EmptyBackground(177, 103),
                new ItemIcon(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE::asStack),
                MetallurgyJeiPlugin::getBlastFurnaceSmeltingRecipes,
                List.of(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE::asStack)
            )
        );
        blastFurnaceFusingCategory = new BlastFurnaceCategory(
            new CreateRecipeCategory.Info<>(
                BLAST_FURNACE_FUSING,
                Component.translatable("gensometallurgy.recipe.blast_furnace_fusing"),
                new EmptyBackground(177, 103),
                new ItemIcon(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE::asStack),
                MetallurgyJeiPlugin::getBlastFurnaceFusingRecipes,
                List.of(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE::asStack)
            )
        );
        castingCategory = new CastingCategory(
            new CreateRecipeCategory.Info<>(
                CASTING,
                Component.translatable("gensometallurgy.recipe.casting"),
                new EmptyBackground(177, 53),
                new ItemIcon(MetallurgyBlocks.CASTING_TABLE::asStack),
                MetallurgyJeiPlugin::getCastingRecipes,
                List.of(MetallurgyBlocks.CASTING_TABLE::asStack)
            )
        );
    }

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        // Plugin discovery can happen before our mod constructor; defer block references until registries are ready.
        createCategories();
        registration.addRecipeCategories(blastFurnaceSmeltingCategory, blastFurnaceFusingCategory, castingCategory);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        blastFurnaceSmeltingCategory.registerRecipes(registration);
        blastFurnaceFusingCategory.registerRecipes(registration);
        castingCategory.registerRecipes(registration);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        blastFurnaceSmeltingCategory.registerCatalysts(registration);
        blastFurnaceFusingCategory.registerCatalysts(registration);
        castingCategory.registerCatalysts(registration);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jeiRuntime = runtime;
        if (!tagListenerRegistered) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.TagsUpdatedEvent event) -> {
                    if (event.getUpdateCause() == net.neoforged.neoforge.event.TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED)
                        Minecraft.getInstance().execute(MetallurgyJeiPlugin::refreshMoltenMetalVisibility);
                });
            tagListenerRegistered = true;
        }
        // JEI begins with every registered item and fluid visible. The first refresh
        // removes entries whose ingot tag is empty; later refreshes apply tag changes.
        visibleMoltenMetals = EnumSet.allOf(MoltenMetal.class);
        refreshMoltenMetalVisibility();
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = null;
        visibleMoltenMetals = EnumSet.allOf(MoltenMetal.class);
    }

    public static void refreshMoltenMetalVisibility() {
        if (jeiRuntime == null)
            return;

        EnumSet<MoltenMetal> enabled = EnumSet.noneOf(MoltenMetal.class);
        for (MoltenMetal metal : MoltenMetal.values()) {
            if (metal.isAvailable())
                enabled.add(metal);
        }

        EnumSet<MoltenMetal> added = EnumSet.copyOf(enabled);
        added.removeAll(visibleMoltenMetals);
        EnumSet<MoltenMetal> removed = EnumSet.copyOf(visibleMoltenMetals);
        removed.removeAll(enabled);

        IIngredientManager ingredients = jeiRuntime.getIngredientManager();
        if (!added.isEmpty()) {
            ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, bucketStacks(added));
            ingredients.addIngredientsAtRuntime(NeoForgeTypes.FLUID_STACK, fluidStacks(added));
        }
        if (!removed.isEmpty()) {
            ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, bucketStacks(removed));
            ingredients.removeIngredientsAtRuntime(NeoForgeTypes.FLUID_STACK, fluidStacks(removed));
        }

        visibleMoltenMetals = enabled;
    }

    private static List<ItemStack> bucketStacks(Set<MoltenMetal> metals) {
        return metals.stream()
            .map(GensoFluids::get)
            .flatMap(java.util.Optional::stream)
            .flatMap(fluid -> fluid.getBucket().stream())
            .map(ItemStack::new)
            .toList();
    }

    private static List<FluidStack> fluidStacks(Set<MoltenMetal> metals) {
        return metals.stream()
            .map(GensoFluids::get)
            .flatMap(java.util.Optional::stream)
            .map(fluid -> new FluidStack((net.minecraft.world.level.material.Fluid) fluid.getSource(), FluidType.BUCKET_VOLUME))
            .toList();
    }

    private static List<RecipeHolder<BasinRecipe>> getBlastFurnaceSmeltingRecipes() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null)
            return List.of();

        net.minecraft.world.item.crafting.RecipeType<BlastFurnaceSmeltingRecipe> recipeType =
            MetallurgyRecipeTypes.BLAST_FURNACE_SMELTING.getType();
        return connection.getRecipeManager().getAllRecipesFor(recipeType).stream().map(h -> new RecipeHolder<BasinRecipe>(h.id(), h.value())).toList();
    }

    private static List<RecipeHolder<BasinRecipe>> getBlastFurnaceFusingRecipes() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null)
            return List.of();

        net.minecraft.world.item.crafting.RecipeType<BlastFurnaceFusingRecipe> recipeType =
            MetallurgyRecipeTypes.BLAST_FURNACE_FUSING.getType();
        return connection.getRecipeManager().getAllRecipesFor(recipeType).stream().map(h -> new RecipeHolder<BasinRecipe>(h.id(), h.value())).toList();
    }

    private static List<RecipeHolder<BasinRecipe>> getCastingRecipes() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null)
            return List.of();

        net.minecraft.world.item.crafting.RecipeType<CastingRecipe> recipeType =
            MetallurgyRecipeTypes.CASTING.getType();
        return connection.getRecipeManager().getAllRecipesFor(recipeType).stream().map(h -> new RecipeHolder<BasinRecipe>(h.id(), h.value())).toList();
    }

}
