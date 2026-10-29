// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.client.jei.category;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;

final class ProcessingTimeRenderer {
    private static final int DEFAULT_PROCESSING_TIME = 200;
    private static final int RIGHT_EDGE_X = 85;
    private static final int Y = 55;
    private static final int COLOR = 0xA0A0A0;

    private ProcessingTimeRenderer() {
    }

    static void draw(ProcessingRecipe<?, ?> recipe, GuiGraphics graphics) {
        draw(recipe, graphics, RIGHT_EDGE_X, Y, false);
    }

    static void drawAboveArrow(ProcessingRecipe<?, ?> recipe, GuiGraphics graphics, int arrowX, int arrowY) {
        draw(recipe, graphics, arrowX + 21, arrowY - 11, true);
    }

    private static void draw(ProcessingRecipe<?, ?> recipe, GuiGraphics graphics, int anchorX, int y, boolean centered) {
        int duration = recipe.getProcessingDuration();
        if (duration <= 0)
            duration = DEFAULT_PROCESSING_TIME;

        String seconds = BigDecimal.valueOf(duration)
            .divide(BigDecimal.valueOf(20))
            .stripTrailingZeros()
            .toPlainString();
        Component text = Component.translatable("create.display_source.station_summary.seconds", seconds);
        Font font = Minecraft.getInstance().font;
        int x = centered ? anchorX - font.width(text) / 2 : anchorX - font.width(text);
        graphics.drawString(font, text, x, y, COLOR, false);
    }
}
