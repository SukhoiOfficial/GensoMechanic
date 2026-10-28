package dev.gensomechanic.mining.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import dev.gensomechanic.mining.content.vein.ScanSelection;
import javax.annotation.Nullable;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import dev.gensomechanic.mining.GensoMining;

import dev.gensomechanic.mining.network.MiningNetwork;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A single native Create scroll selector over the user's unmodified GUI texture. */
public final class ScannerScreen extends AbstractSimiScreen {
    public static final int PANEL_WIDTH = 187, PANEL_HEIGHT = 102;
    private static final ResourceLocation BACKGROUND = GensoMining.asResource("textures/gui/scanner_ui.png");
    private final UUID session;
    private ScanSelection selected;
    private final boolean archive;
    private List<ScanSelection> choices;
    private boolean submitted;
    private List<MiningNetwork.Entry> entries;
    private ScrollInput selector;

    public ScannerScreen(UUID session, ScanSelection selected, boolean archive) { this.session = session; this.selected = selected; this.archive = archive; }
    @Override protected void init() {
        setWindowSize(PANEL_WIDTH, PANEL_HEIGHT);
        super.init();
        entries = ScannerClient.entries();
        List<Component> options = new ArrayList<>();
        choices = ScanSelection.options(entries.stream().map(MiningNetwork.Entry::id).toList(), archive);
        choices.forEach(choice -> options.add(ScannerClient.selectionName(choice)));
        int index = choices.indexOf(selected);
        if (index < 0) { index = 0; selected = choices.getFirst(); }
        selector = new SelectionScrollInput(guiLeft + 43, guiTop + 37, 112, 18)
                .forOptions(options).titled(Component.translatable(archive ? "scanner.gensomining.archive_records" : "scanner.gensomining.select_target"))
                .withShiftStep(1).setState(index).calling(value -> selected = choices.get(value));
        addRenderableWidget(selector);
        var confirm = new IconButton(guiLeft + 154, guiTop + 78, AllIcons.I_CONFIRM);
        confirm.withCallback(this::onClose); addRenderableWidget(confirm);
    }
    public void catalogueChanged() { if (minecraft != null) init(minecraft, width, height); }
    @Override protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.blit(BACKGROUND, guiLeft, guiTop, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 256, 256);
        var title = Component.translatable(archive ? "item.gensomining.exploration_archive" : "item.gensomining.scanner");
        graphics.drawString(font, title, guiLeft + (178 - font.width(title)) / 2, guiTop + 4, 0x592424, false);
        var entry = ScannerClient.entry(selected.type().orElse(null));
        Component name = ScannerClient.selectionName(selected);
        String text = name.getString();
        if (font.width(text) > 98) text = font.plainSubstrByWidth(text, 98 - font.width("…")) + "…";
        graphics.drawString(font, text, guiLeft + 48, guiTop + 42, 0xFFFFFF, true);
        if (selected.all()) graphics.renderItem(new ItemStack(archive ? dev.gensomechanic.mining.content.miner.MiningContent.ARCHIVE.get() : dev.gensomechanic.mining.content.miner.MiningContent.SCANNER.get()), guiLeft + 22, guiTop + 38);
        if (entry != null) graphics.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(entry.icon())), guiLeft + 22, guiTop + 38);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() {
        super.removed();
        if (!submitted && minecraft != null && minecraft.getConnection() != null) {
            submitted = true; net.neoforged.neoforge.network.PacketDistributor.sendToServer(new MiningNetwork.Configure(session, selected));
        }
    }
}
