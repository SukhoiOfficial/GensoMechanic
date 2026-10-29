// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.foundation;

import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** Shared formatting helpers for Create's engineer-goggle overlay. */
public final class GoggleTooltipHelper {
    public static final int PROGRESS_BAR_LENGTH = 20;

    private GoggleTooltipHelper() {
    }

    public static void add(List<Component> tooltip, Component component) {
        CreateLang.builder().add(component).forGoggles(tooltip);
    }

    public static void add(List<Component> tooltip, Component component, int indent) {
        CreateLang.builder().add(component).forGoggles(tooltip, indent);
    }

    public static MutableComponent progressBar(int remainingTicks, int totalTicks) {
        return progressBar(remainingTicks, totalTicks, ChatFormatting.GREEN.getColor());
    }

    public static MutableComponent progressBar(int remainingTicks, int totalTicks, int completedColor) {
        int completedSegments = 0;
        if (totalTicks > 0) {
            float progress = 1 - Math.max(0, remainingTicks) / (float) totalTicks;
            completedSegments = Math.max(0,
                Math.min(PROGRESS_BAR_LENGTH, Math.round(progress * PROGRESS_BAR_LENGTH)));
        }

        MutableComponent bar = Component.empty();
        if (completedSegments > 0) {
            bar.append(Component.literal("|".repeat(completedSegments))
                .withStyle(style -> style.withColor(completedColor)));
        }
        if (completedSegments < PROGRESS_BAR_LENGTH) {
            bar.append(Component.literal("|".repeat(PROGRESS_BAR_LENGTH - completedSegments))
                .withStyle(ChatFormatting.GRAY));
        }
        return bar;
    }

    public static MutableComponent unavailableMark() {
        return Component.literal("×").withStyle(ChatFormatting.RED);
    }
}
