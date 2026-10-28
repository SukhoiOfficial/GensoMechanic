package dev.gensomechanic.mining.client;

import dev.gensomechanic.mining.content.miner.MiningContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Called only by the mining client entry point; viewers remain optional. */
public final class MiningExplorationClient {
    public static void register(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ScannerOverlayConfig.SPEC, "gensomining-client.toml");
        ScannerClient.register();
        NeoForge.EVENT_BUS.addListener(MiningExplorationClient::tooltip);
    }

    private static void tooltip(ItemTooltipEvent event) {
        if (event.getItemStack().is(MiningContent.SCANNER.get()) || event.getItemStack().is(MiningContent.ARCHIVE.get()))
            ScannerClient.tooltip(event.getItemStack(), event.getToolTip());
    }

    private MiningExplorationClient() {}
}
