package dev.gensomechanic.mining.client.jei;

import static com.simibubi.create.compat.jei.category.CreateRecipeCategory.*;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import dev.gensomechanic.mining.content.miner.MiningContent;
import dev.gensomechanic.mining.content.miner.MiningNumbers;
import dev.gensomechanic.mining.content.miner.MiningModifiers;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.Arrays;
import java.util.List;

public final class MiningCategory implements mezz.jei.api.recipe.category.IRecipeCategory<MiningDisplay> {
    public static final int WIDTH = 210, HEIGHT = 150;
    public static int outputX(int i) { return 148 + i % 3 * 19; }
    public static int outputY(int i) { return 43 + i / 3 * 19; }
    private static int outputY(int i, int count) { return 62 - ((count + 2) / 3 - 1) * 19 / 2 + i / 3 * 19; }
    private final mezz.jei.api.gui.drawable.IDrawable icon;
    public MiningCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(MiningContent.MECHANICAL_MINER.asStack());
    }
    @Override public mezz.jei.api.recipe.RecipeType<MiningDisplay> getRecipeType() { return GensoJeiPlugin.MINING; }
    @Override public Component getTitle() { return Component.translatable("jei.gensomining.mining"); }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public mezz.jei.api.gui.drawable.IDrawable getIcon() { return icon; }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, MiningDisplay page, IFocusGroup focuses) {
        var recipe = page.recipe();
        builder.addSlot(RecipeIngredientRole.CATALYST, 6, 5).setBackground(getRenderedSlot(), -1, -1)
                .addIngredient(VeinIngredient.TYPE, new VeinIngredient(recipe.veinId()))
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.clear(); tooltip.add(page.veinName());
                    tooltip.add(Component.literal(recipe.veinId().toString()).withStyle(ChatFormatting.DARK_GRAY));
                    if(dev.gensomechanic.mining.client.ScannerClient.isBackground(recipe.veinId()))
                        VeinIngredient.backgroundConditions(recipe.veinId()).forEach(tooltip::add);
                    else tooltip.add(Component.translatable("jei.gensomining.vein_condition").withStyle(ChatFormatting.GOLD));
                });
        var modifier = page.modifier();
        if (modifier != null) {
            var slot = modifier.fluid() != null ? addFluidSlot(builder, 6, 60, modifier.fluid())
                    : builder.addSlot(RecipeIngredientRole.INPUT, 6, 60).setBackground(getRenderedSlot(), -1, -1)
                    .addItemStacks(Arrays.stream(modifier.ingredient().getItems()).map(stack -> stack.copyWithCount(modifier.count())).toList());
            slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.gensomining.consumed_per_cycle").withStyle(ChatFormatting.GRAY)));
        }
        var outputs = page.orderedOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            var entry = outputs.get(i); var output = entry.output(); int index = entry.originalIndex();
            builder.addSlot(RecipeIngredientRole.OUTPUT, outputX(i), outputY(i, outputs.size()))
                    .setBackground(getRenderedSlot((float) output.chance()), -1, -1).addItemStack(output.stack())
                    .addRichTooltipCallback((view, tooltip) -> {
                        if (output.chance() != 1) tooltip.add(Component.translatable("create.recipe.processing.chance",
                                MiningNumbers.format(output.chance() * 100)).withStyle(ChatFormatting.GOLD));
                        if (output.chance() != 1 && output.stack().getCount() > 1 && output.stackRoll())
                            tooltip.add(Component.translatable("jei.gensomining.roll.stack").withStyle(ChatFormatting.GRAY));
                        if (index < recipe.modifiers().baseOutputs().size()) {
                            double original = recipe.modifiers().baseOutputs().get(index).chance();
                            if (original != output.chance()) tooltip.add(Component.translatable("jei.gensomining.original_chance",
                                    MiningNumbers.format(original * 100), signed((output.chance() - original) * 100)).withStyle(ChatFormatting.GRAY));
                        } else tooltip.add(Component.translatable("jei.gensomining.additional_output").withStyle(ChatFormatting.GRAY));
                    });
        }
    }
    @Override public void draw(MiningDisplay page, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, font.plainSubstrByWidth(page.veinName().getString(), WIDTH - 32), 28, 9, 0x555555, false);
        graphics.drawString(font, Component.translatable("jei.gensomining.page." + page.kind()), 6, 27, 0x555555, false);
        graphics.drawString(font, Component.translatable("jei.gensomining.outputs"), 148, 27, 0x555555, false);
        AllGuiTextures.JEI_SHADOW.render(graphics, 35, 88);
        AllGuiTextures.JEI_ARROW.render(graphics, 98, 66);
        AnimatedMiner.draw(page, graphics, 35, 34);
        drawFitted(graphics, parameter("time", page.operation().duration(), page.recipe().duration(), true), 108);
        drawFitted(graphics, parameter("stress", page.operation().stress(), page.recipe().stress(), true), 122);
        drawFitted(graphics, parameter("capacity", page.operation().capacityCost(), 1, false), 136);
    }
    public static Component parameter(String key, double value, double base, boolean percentage) {
        var text = Component.translatable("jei.gensomining.parameter." + key, MiningNumbers.format(value));
        if (Math.abs(value - base) > 0.00001) {
            String delta = percentage && base != 0 ? signed((value / base - 1) * 100) + "%" : signed(value - base);
            text.append(Component.translatable("jei.gensomining.change", delta));
        }
        return text;
    }
    private static String signed(double value) { return (value > 0 ? "+" : "") + MiningNumbers.format(value); }
    private void drawFitted(GuiGraphics graphics, Component text, int y) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1f, (WIDTH - 12f) / Math.max(1, font.width(text)));
        graphics.pose().pushPose(); graphics.pose().translate(6, y, 0); graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, 0x555555, false); graphics.pose().popPose();
    }
}
