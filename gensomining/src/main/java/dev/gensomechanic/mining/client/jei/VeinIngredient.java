package dev.gensomechanic.mining.client.jei;

import com.mojang.serialization.Codec;
import dev.gensomechanic.mining.client.ScannerClient;
import mezz.jei.api.ingredients.*;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.ArrayList;
import java.util.List;

/** JEI-only identity: never an ItemStack alias for the block used as its icon. */
public record VeinIngredient(ResourceLocation id, Component displayName, ResourceLocation icon) {
    public VeinIngredient { displayName = displayName.copy(); }
    public VeinIngredient(ResourceLocation id) { this(id, snapshotName(id), snapshotIcon(id)); }
    private static Component snapshotName(ResourceLocation id) {
        var entry = ScannerClient.miningEntry(id);
        return entry == null ? Component.literal(id.toString()) : entry.name();
    }
    private static ResourceLocation snapshotIcon(ResourceLocation id) {
        var entry = ScannerClient.miningEntry(id);
        return entry == null ? ResourceLocation.withDefaultNamespace("compass") : entry.icon();
    }
    @Override public Component displayName() { return displayName.copy(); }
    public static final IIngredientType<VeinIngredient> TYPE = () -> VeinIngredient.class;
    public static final Codec<VeinIngredient> CODEC = ResourceLocation.CODEC.xmap(VeinIngredient::new, VeinIngredient::id);
    public Component name() {
        return displayName.copy();
    }
    public static final class Helper implements IIngredientHelper<VeinIngredient> {
        @Override public IIngredientType<VeinIngredient> getIngredientType() { return TYPE; }
        @Override public String getDisplayName(VeinIngredient vein) { return vein.name().getString(); }
        @Override public String getUniqueId(VeinIngredient vein, UidContext context) { return vein.id.toString(); }
        @Override public Object getUid(VeinIngredient vein, UidContext context) { return vein.id; }
        @Override public Object getGroupingUid(VeinIngredient vein) { return vein.id; }
        @Override public ResourceLocation getResourceLocation(VeinIngredient vein) { return vein.id; }
        @Override public VeinIngredient copyIngredient(VeinIngredient vein) { return vein; }
        @Override public boolean isIngredientOnServer(VeinIngredient vein) { return ScannerClient.miningEntry(vein.id) != null; }
        @Override public String getErrorInfo(VeinIngredient vein) { return String.valueOf(vein); }
    }
    public static final class Renderer implements IIngredientRenderer<VeinIngredient> {
        @Override public void render(GuiGraphics graphics, VeinIngredient vein) {
            graphics.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(vein.icon())), 0, 0);
        }
        @Override public List<Component> getTooltip(VeinIngredient vein, TooltipFlag flag) {
            List<Component> lines=new ArrayList<>(); lines.add(vein.name());
            if(flag.isAdvanced()) lines.add(Component.literal(vein.id.toString()).withStyle(ChatFormatting.DARK_GRAY));
            lines.addAll(backgroundConditions(vein.id()));
            return List.copyOf(lines);
        }
    }
    public static List<Component> backgroundConditions(ResourceLocation id) {
        if(!ScannerClient.isBackground(id)) return List.of();
        var entry=ScannerClient.miningEntry(id);
        List<Component> lines=new ArrayList<>();
        lines.add(Component.translatable("jei.gensomining.rock_background").withStyle(ChatFormatting.GOLD));
        entry.details().forEach(line->lines.add(line.copy().withStyle(ChatFormatting.GRAY)));
        VeinConditionsWidget.appendFilterSummary(lines,entry.conditions());
        return List.copyOf(lines);
    }
}
