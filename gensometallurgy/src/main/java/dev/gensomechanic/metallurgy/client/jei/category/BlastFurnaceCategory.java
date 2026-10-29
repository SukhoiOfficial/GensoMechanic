// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.client.jei.category;

import com.simibubi.create.compat.jei.category.BasinCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedBlazeBurner;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import dev.gensomechanic.metallurgy.client.jei.category.animations.AnimatedIndustrialBlastFurnace;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class BlastFurnaceCategory extends BasinCategory {
    private final AnimatedIndustrialBlastFurnace furnace = new AnimatedIndustrialBlastFurnace();
    private final AnimatedBlazeBurner heater = new AnimatedBlazeBurner();

    public BlastFurnaceCategory(Info<BasinRecipe> info) {
        super(info, true);
    }

    @Override
    public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder, BasinRecipe recipe,
                          mezz.jei.api.recipe.IFocusGroup focuses) {
        var ingredients = com.simibubi.create.foundation.item.ItemHelper.condenseIngredients(recipe.getIngredients());
        int inputCount = ingredients.size() + recipe.getFluidIngredients().size();
        int outputCount = recipe.getRollableResults().size() + recipe.getFluidResults().size();
        if (inputCount <= 9 && outputCount <= 6) {
            super.setRecipe(builder, recipe, focuses);
            return;
        }
        int index = 0;
        for (var pair : ingredients) {
            var stacks = java.util.Arrays.stream(pair.getFirst().getItems()).map(stack -> {
                var copy = stack.copy(); copy.setCount(pair.getSecond().intValue()); return copy;
            }).toList();
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT,
                    inputX(inputCount, index), inputY(index++))
                .setBackground(getRenderedSlot(), -1, -1).addItemStacks(stacks);
        }
        for (var fluid : recipe.getFluidIngredients()) {
            addFluidSlot(builder, inputX(inputCount, index), inputY(index), fluid); index++;
        }
        index = 0;
        for (var result : recipe.getRollableResults()) {
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                    outputX(outputCount, index), outputY(index++))
                .setBackground(getRenderedSlot(result), -1, -1).addItemStack(result.getStack())
                .addRichTooltipCallback(addStochasticTooltip(result));
        }
        for (var fluid : recipe.getFluidResults()) {
            addFluidSlot(builder, outputX(outputCount, index), outputY(index), fluid); index++;
        }
        var heat = recipe.getRequiredHeat();
        if (!heat.testBlazeBurner(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.NONE))
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.RENDER_ONLY, 134, 81)
                .addItemStack(com.simibubi.create.AllBlocks.BLAZE_BURNER.asStack());
        if (!heat.testBlazeBurner(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED))
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.CATALYST, 153, 81)
                .addItemStack(com.simibubi.create.AllItems.BLAZE_CAKE.asStack());
    }

    private static int inputX(int count, int index) { return 17 + (count < 3 ? (3 - count) * 19 / 2 : 0) + index % 3 * 19; }
    private static int inputY(int index) { return 51 - index % 9 / 3 * 19; }
    private static int outputX(int count, int index) { return 142 - (count % 2 != 0 && index == count - 1 ? 0 : index % 2 == 0 ? 10 : -9); }
    private static int outputY(int index) { return 51 - index % 6 / 2 * 19; }

    @Override
    public void createRecipeExtras(mezz.jei.api.gui.widgets.IRecipeExtrasBuilder builder,
            net.minecraft.world.item.crafting.RecipeHolder<BasinRecipe> recipe,
            mezz.jei.api.recipe.IFocusGroup focuses) {
        var inputs = builder.getRecipeSlots().getSlots(mezz.jei.api.recipe.RecipeIngredientRole.INPUT);
        var outputs = builder.getRecipeSlots().getSlots(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT);
        if (inputs.size() > 9) builder.addScrollGridWidget(inputs, 3, 3).setPosition(16, 12);
        if (outputs.size() > 6) builder.addScrollGridWidget(outputs, 2, 3).setPosition(130, 12);
    }

    @Override
    public void draw(BasinRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        super.draw(recipe, recipeSlotsView, graphics, mouseX, mouseY);

        HeatCondition requiredHeat = recipe.getRequiredHeat();
        if (requiredHeat != HeatCondition.NONE) {
            heater.withHeat(requiredHeat.visualizeAsBlazeBurner())
                .draw(graphics, getBackground().getWidth() / 2 + 3, 55);
        }

        furnace.withRecipe(recipe)
            .draw(graphics, getBackground().getWidth() / 2 + 3, 34);
        ProcessingTimeRenderer.draw(recipe, graphics);
    }
}
