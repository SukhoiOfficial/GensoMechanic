package dev.gensomechanic.mining.client;

import com.simibubi.create.foundation.utility.CreateLang;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.content.miner.*;
import net.createmod.catnip.outliner.Outliner;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(value = GensoMining.MOD_ID, dist = Dist.CLIENT)
public final class MiningClient {
    public MiningClient(IEventBus bus, ModContainer container) {
        GensoPartialModels.init();
        bus.addListener(MiningClient::setup);
        bus.addListener(MiningClient::renderers);
        NeoForge.EVENT_BUS.addListener(MiningClient::tooltip);
        MiningExplorationClient.register(bus, container);
    }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MiningContent.MINER_ENTITY.get(), MechanicalMinerRenderer::new);
        event.registerEntityRenderer(dev.gensomechanic.mining.content.explosive.ExplosiveContent.PRIMED_EXPLOSIVE.get(),
                net.minecraft.client.renderer.entity.TntRenderer::new);
    }
    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SimpleBlockEntityVisualizer.builder(MiningContent.MINER_ENTITY.get()).factory(MinerVisual::new).neverSkipVanillaRender().apply();
            MechanicalMinerBlockEntity.registerClientEffects(MinerEffects::tick);
            MechanicalMinerItem.registerPlacementFailureHandler(context -> {
                var pos = MechanicalMinerItem.placementController(context);
                Outliner.getInstance().showAABB(java.util.List.of("gensomining_miner", pos), MinerStructure.bounds(pos)).colored(0xFFff5d6c);
                if (context.getPlayer() != null) CreateLang.translate("large_water_wheel.not_enough_space").color(0xFFff5d6c).sendStatus(context.getPlayer());
            });
        });
        GensoMining.LOGGER.info("Genso Mining client initialized");
    }
    private static void tooltip(ItemTooltipEvent event) {
        if (event.getItemStack().is(MiningContent.MECHANICAL_MINER.asItem())) MiningStressTooltip.append(event.getToolTip());
    }
}
