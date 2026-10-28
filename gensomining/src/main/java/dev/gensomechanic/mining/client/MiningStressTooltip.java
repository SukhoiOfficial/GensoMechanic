package dev.gensomechanic.mining.client;

import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class MiningStressTooltip {
    public static void append(List<Component> tooltip) {
        if (!StressImpact.isEnabled()) return;
        tooltip.add(Component.empty());
        CreateLang.translate("tooltip.stressImpact").style(ChatFormatting.GRAY).addTo(tooltip);
        tooltip.add(Component.literal(com.simibubi.create.foundation.item.TooltipHelper.makeProgressBar(3, 2)).append(Component.translatable("tooltip.gensomining.variable_stress"))
                .withStyle(StressImpact.MEDIUM.getAbsoluteColor()));
    }
    private MiningStressTooltip() {}
}
