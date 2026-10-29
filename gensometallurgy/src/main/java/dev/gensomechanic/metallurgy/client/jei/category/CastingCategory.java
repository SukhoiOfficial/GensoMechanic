// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.client.jei.category;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import dev.gensomechanic.metallurgy.client.jei.category.animations.AnimatedCastingTable;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public final class CastingCategory extends CreateRecipeCategory<BasinRecipe> {
    private final AnimatedCastingTable castingTable = new AnimatedCastingTable();

    public CastingCategory(Info<BasinRecipe> info) {
        super(info);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BasinRecipe recipe, IFocusGroup focuses) {
        if (!recipe.getFluidIngredients().isEmpty())
            addFluidSlot(builder, 15, 9, recipe.getFluidIngredients().get(0));

        List<ProcessingOutput> results = recipe.getRollableResults();
        boolean single = results.size() == 1;
        for (int i = 0; i < results.size(); i++) {
            ProcessingOutput output = results.get(i);
            int xOffset = i % 2 == 0 ? 0 : 19;
            int yOffset = (i / 2) * -19;
            builder.addSlot(RecipeIngredientRole.OUTPUT, single ? 139 : 133 + xOffset, 27 + yOffset)
                .setBackground(getRenderedSlot(output), -1, -1)
                .addItemStack(output.getStack())
                .addRichTooltipCallback(addStochasticTooltip(output));
        }
    }

    @Override
    public void draw(BasinRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        AllGuiTextures.JEI_ARROW.render(graphics, 85, 32);
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 43, 4);
        castingTable.withFluid(findDisplayedInputFluid(recipeSlotsView))
            .draw(graphics, 48, 27);
        ProcessingTimeRenderer.drawAboveArrow(recipe, graphics, 85, 32);
    }

    private static FluidStack findDisplayedInputFluid(IRecipeSlotsView recipeSlotsView) {
        return recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT)
            .stream()
            .map(slot -> slot.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK))
            .flatMap(java.util.Optional::stream)
            .findFirst()
            .map(FluidStack::copy)
            .orElse(FluidStack.EMPTY);
    }
}
