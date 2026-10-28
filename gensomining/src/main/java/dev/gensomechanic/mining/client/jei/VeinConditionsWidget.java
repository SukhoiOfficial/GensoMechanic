package dev.gensomechanic.mining.client.jei;

import dev.gensomechanic.mining.content.vein.VeinCondition;
import java.util.*;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Four fixed rows; overflow cycles in-place, while hovering freezes only that row. */
public final class VeinConditionsWidget implements IRecipeWidget, IJeiInputHandler {
    private static final int WIDTH = 165, ROW_HEIGHT = 18;
    private static final String[] KEYS = {"dimension_allow", "dimension_deny", "biome_allow", "biome_deny"};
    private final List<List<VeinCondition>> conditions;
    private final long[] elapsed = new long[4];
    private long lastTime = Util.getMillis();
    private record Hit(int row, VeinCondition entry, int x, int width) {}
    private final List<Hit> hits = new ArrayList<>();
    private Hit hovered;
    private int scroll;
    public VeinConditionsWidget(List<List<VeinCondition>> conditions) { this.conditions = conditions; }
    @Override public ScreenPosition getPosition() { return new ScreenPosition(6, 25); }
    @Override public ScreenRectangle getArea() { return new ScreenRectangle(6, 25, WIDTH, ROW_HEIGHT * 4); }
    @Override public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        long now = Util.getMillis(), delta = Math.clamp(now - lastTime, 0, 250); lastTime = now;
        hits.clear();
        for (int row = 0; row < 4; row++) {
            boolean overRow = mouseX >= 0 && mouseX < WIDTH && mouseY >= row * ROW_HEIGHT && mouseY < (row + 1) * ROW_HEIGHT;
            if (!overRow) elapsed[row] += delta;
            graphics.drawString(font, text(KEYS[row]), 0, row * ROW_HEIGHT, 0x555555, false);
            var entries = conditions.get(row);
            if (entries.isEmpty()) {
                // Schema 4 transmits actual bound dimensions: an empty list means no generation profile.
                graphics.drawString(font, text(row == 2 ? "any" : "none"), 4, row * ROW_HEIGHT + 9, 0x777777, false); continue;
            }
            List<List<VeinCondition>> groups = new ArrayList<>(); List<VeinCondition> group = new ArrayList<>(); int used = 0;
            for (var entry : entries) {
                int width = Math.min(WIDTH - 4, font.width(label(entry, row).getString()));
                if (!group.isEmpty() && used + 8 + width > WIDTH - 4) { groups.add(group); group = new ArrayList<>(); used = 0; }
                if (!group.isEmpty()) used += 8;
                group.add(entry); used += width;
            }
            groups.add(group);
            int x = 4;
            for (var entry : groups.get((int) (elapsed[row] / 2000 % groups.size()))) {
                String value = label(entry, row).getString(); int width = Math.min(WIDTH - 4, font.width(value));
                String shown = font.width(value) > WIDTH - 4 ? font.plainSubstrByWidth(value, WIDTH - 13) + "…" : value;
                graphics.drawString(font, shown, x, row * ROW_HEIGHT + 9, 0x777777, false);
                hits.add(new Hit(row, entry, x, width)); x += width + 8;
            }
        }
        Hit next = find(mouseX, mouseY);
        if (!Objects.equals(next, hovered)) scroll = 0;
        hovered = next;
    }
    private Hit find(double x, double y) {
        return hits.stream().filter(h -> x >= h.x && x < h.x + h.width && y >= h.row * ROW_HEIGHT + 9 && y < h.row * ROW_HEIGHT + 18).findFirst().orElse(null);
    }
    @Override public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        Hit hit = find(mouseX, mouseY); if (hit == null) return;
        tooltip.add(label(hit.entry, hit.row));
        tooltip.add(Component.literal(hit.entry.value()).withStyle(ChatFormatting.DARK_GRAY));
        if (!hit.entry.isTag()) return;
        var members = hit.entry.members();
        if (members.isEmpty()) { tooltip.add(text("empty_tag").withStyle(ChatFormatting.GRAY)); return; }
        int limit = visibleMembers(); scroll = Math.clamp(scroll, 0, Math.max(0, members.size() - limit));
        for (var id : members.subList(scroll, Math.min(members.size(), scroll + limit)))
            tooltip.add(biomeName(id).copy().withStyle(ChatFormatting.GRAY));
        if (members.size() > limit) tooltip.add(text("scroll", scroll + 1, Math.min(members.size(), scroll + limit), members.size()).withStyle(ChatFormatting.DARK_GRAY));
    }
    private static int visibleMembers() { return Math.max(1, (Minecraft.getInstance().getWindow().getGuiScaledHeight() - 70) / 10); }
    /** Background allow lists use the filter's normal empty-means-any semantics. */
    static void appendFilterSummary(List<Component> lines,List<List<VeinCondition>> conditions) {
        for(int row=0;row<4;row++) {
            lines.add(text(KEYS[row]).withStyle(ChatFormatting.GRAY));
            var entries=conditions.get(row);
            if(entries.isEmpty()) {
                lines.add(Component.literal("  ").append(text(row%2==0 ? "any" : "none")).withStyle(ChatFormatting.DARK_GRAY));
                continue;
            }
            for(var entry:entries) {
                lines.add(Component.literal("  ").append(label(entry,row)).withStyle(ChatFormatting.DARK_GRAY));
                if(entry.members().isEmpty()) lines.add(Component.literal("    ").append(text("empty_tag")).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
    @Override public boolean handleMouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (hovered == null || !hovered.entry.isTag() || hovered.entry.members().size() <= visibleMembers() || dy == 0) return false;
        scroll = Math.clamp(scroll + (dy > 0 ? -3 : 3), 0, hovered.entry.members().size() - visibleMembers()); return true;
    }
    private static Component label(VeinCondition entry, int row) {
        if (entry.isTag()) return Component.literal(entry.value());
        var id = ResourceLocation.parse(entry.value());
        return row >= 2 ? biomeName(id) : translatedOrId("dimension.", id);
    }
    private static Component biomeName(ResourceLocation id) { return translatedOrId("biome.", id); }
    private static Component translatedOrId(String prefix, ResourceLocation id) {
        String key = prefix + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(id.toString());
    }
    private static net.minecraft.network.chat.MutableComponent text(String suffix, Object... args) {
        return Component.translatable("jei.gensomining.generation." + suffix, args);
    }
}
