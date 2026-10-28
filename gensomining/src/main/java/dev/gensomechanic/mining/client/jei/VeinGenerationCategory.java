package dev.gensomechanic.mining.client.jei;

import dev.gensomechanic.mining.content.miner.MiningContent;
import dev.gensomechanic.mining.network.MiningNetwork;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class VeinGenerationCategory implements IRecipeCategory<MiningNetwork.Entry> {
    public static final int WIDTH = 177, HEIGHT = 100; // CreateJEI: crushing.emptyBackground(177, 100)
    private final IDrawable icon;
    public VeinGenerationCategory(IGuiHelper gui) { icon = gui.createDrawableItemStack(MiningContent.SCANNER.asStack()); }
    @Override public RecipeType<MiningNetwork.Entry> getRecipeType() { return GensoJeiPlugin.VEIN_GENERATION; }
    @Override public Component getTitle() { return Component.translatable("jei.gensomining.vein_generation"); }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, MiningNetwork.Entry vein, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.OUTPUT, 6, 5)
            .setBackground(com.simibubi.create.compat.jei.category.CreateRecipeCategory.getRenderedSlot(), -1, -1)
            .addIngredient(VeinIngredient.TYPE, new VeinIngredient(vein.id()))
            .addRichTooltipCallback((slot,tooltip)->vein.details().forEach(tooltip::add));
    }
    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, MiningNetwork.Entry vein, IFocusGroup focuses) {
        var widget = new VeinConditionsWidget(vein.conditions());
        builder.addWidget(widget); builder.addInputHandler(widget);
    }
    @Override public void draw(MiningNetwork.Entry vein, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, font.plainSubstrByWidth(vein.name().getString(), WIDTH - 34), 28, 9, 0x555555, false);
    }
    @Override public void getTooltip(mezz.jei.api.gui.builder.ITooltipBuilder tooltip,MiningNetwork.Entry vein,IRecipeSlotsView slots,double mouseX,double mouseY) {
        if(mouseX>=28&&mouseX<WIDTH&&mouseY>=4&&mouseY<22)vein.details().forEach(tooltip::add);
    }
}
