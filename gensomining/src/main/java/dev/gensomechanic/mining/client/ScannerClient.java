package dev.gensomechanic.mining.client;

import dev.gensomechanic.mining.network.MiningNetwork;
import dev.gensomechanic.mining.content.vein.ScanWavePattern;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

public final class ScannerClient {
    public static final ScannerOverlayState OVERLAY = new ScannerOverlayState(System::nanoTime);
    private static List<MiningNetwork.Entry> entries = List.of();
    private static List<MiningNetwork.Entry> allEntries = List.of();
    private static List<MiningNetwork.Entry> backgroundEntries = List.of(), miningEntries = List.of();
    public static Runnable catalogueChanged = () -> {};
    private static Map<ResourceLocation, MiningNetwork.Entry> byId = Map.of();
    private static Map<ResourceLocation, MiningNetwork.Entry> miningById = Map.of();
    private static final List<Wave> waves = new ArrayList<>();
    private record Wave(ClientLevel level, Vec3 origin, long start) {}
    public static void register() {
        MiningNetwork.catalogueReceiver = packet -> {
            OVERLAY.catalogue(packet);
            acceptCatalogue(packet);
            ArchiveClient.catalogue(packet); dev.gensomechanic.mining.client.compat.xaero.ScannerMapBridge.refresh();
            catalogueChanged.run();
            if (Minecraft.getInstance().screen instanceof ScannerScreen screen) screen.catalogueChanged();
        };
        MiningNetwork.overlayReceiver = packet -> {
            var level = Minecraft.getInstance().level;
            OVERLAY.accept(packet, level == null ? null : level.dimension().location());
            dev.gensomechanic.mining.client.compat.xaero.ScannerMapBridge.refresh();
        };
        MiningNetwork.surveyReceiver = packet -> {
            var level=Minecraft.getInstance().level;
            OVERLAY.command(packet,level==null?null:level.dimension().location());
        };
        MiningNetwork.openReceiver = packet -> Minecraft.getInstance().setScreen(new ScannerScreen(packet.session(), packet.selection(), packet.archive()));
        MiningNetwork.waveReceiver = packet -> {
            var level = Minecraft.getInstance().level;
            if (level != null && waves.size() < 64) waves.add(new Wave(level, packet.origin(), level.getGameTime()));
        };
        ArchiveClient.register();
        NeoForge.EVENT_BUS.addListener(ScannerClient::tick);
        NeoForge.EVENT_BUS.addListener(ScannerClient::logout);
    }
    public static List<MiningNetwork.Entry> allEntries() { return allEntries; }
    public static List<MiningNetwork.Entry> entries() { return entries; }
    public static MiningNetwork.Entry entry(ResourceLocation id) { return id == null ? null : byId.get(id); }
    public static List<MiningNetwork.Entry> backgroundEntries() { return backgroundEntries; }
    public static List<MiningNetwork.Entry> miningEntries() { return miningEntries; }
    public static MiningNetwork.Entry miningEntry(ResourceLocation id) { return id == null ? null : miningById.get(id); }
    public static boolean isBackground(ResourceLocation id) { return id != null && !byId.containsKey(id) && miningById.containsKey(id); }
    private static void acceptCatalogue(MiningNetwork.Catalog packet) {
        allEntries=packet.entries(); backgroundEntries=packet.backgrounds();
        entries=allEntries.stream().filter(entry->entry.variantOf()==null).toList();
        miningEntries=java.util.stream.Stream.concat(allEntries.stream(),backgroundEntries.stream()).toList();
        Map<ResourceLocation,MiningNetwork.Entry> next=new HashMap<>();
        allEntries.forEach(entry->next.put(entry.id(),entry)); byId=Map.copyOf(next);
        backgroundEntries.forEach(entry->next.put(entry.id(),entry)); miningById=Map.copyOf(next);
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) { acceptCatalogue(new MiningNetwork.Catalog(List.of())); catalogueChanged.run(); waves.clear(); OVERLAY.clear(); ArchiveClient.clear(); dev.gensomechanic.mining.client.compat.xaero.ScannerMapBridge.refresh(); }
    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        ArchiveClient.tick();
        OVERLAY.tick(mc.level == null ? null : mc.level.dimension().location(), mc.player != null && mc.player.isAlive(), ScannerOverlayConfig.DURATION.get());
        dev.gensomechanic.mining.client.compat.xaero.ScannerMapBridge.refresh();
        if (mc.isPaused()) return;
        waves.removeIf(w -> w.level != mc.level || w.level.getGameTime() - w.start >= ScanWavePattern.DURATION);
        for (Wave w : waves) ScanWavePattern.points((int) (w.level.getGameTime() - w.start), offset -> {
            var p = w.origin.add(offset);
            w.level.addParticle(new DustParticleOptions(new Vector3f(1, 0, 0), 1), p.x, p.y, p.z, 0, 0, 0);
        });
    }
    public static net.minecraft.network.chat.Component selectionName(dev.gensomechanic.mining.content.vein.ScanSelection selection) {
        if (selection.all()) return net.minecraft.network.chat.Component.translatable("scanner.gensomining.all_targets");
        var selected = entry(selection.type().orElse(null));
        if (selected != null && selected.variantOf() != null) selected = entry(selected.variantOf());
        return selected == null ? net.minecraft.network.chat.Component.translatable("scanner.gensomining.no_selection") : selected.name();
    }
    public static void tooltip(net.minecraft.world.item.ItemStack stack, List<net.minecraft.network.chat.Component> tooltip) {
        boolean archive = stack.is(dev.gensomechanic.mining.content.miner.MiningContent.ARCHIVE.get());
        var selection = archive ? dev.gensomechanic.mining.content.vein.ScannerTarget.archive(stack) : dev.gensomechanic.mining.content.vein.ScannerTarget.selection(stack);
        tooltip.add(net.minecraft.network.chat.Component.translatable(archive ? "scanner.gensomining.archive_target" : "scanner.gensomining.target", selectionName(selection)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    private ScannerClient() {}
}
