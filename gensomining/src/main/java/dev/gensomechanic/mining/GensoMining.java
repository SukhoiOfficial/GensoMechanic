package dev.gensomechanic.mining;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import dev.gensomechanic.mining.content.miner.MinerPortBlockEntity;
import dev.gensomechanic.mining.content.miner.MiningContent;
import dev.gensomechanic.mining.content.miner.MiningRecipes;
import dev.gensomechanic.mining.content.vein.ArchiveService;
import dev.gensomechanic.mining.content.vein.ScannerLang;
import dev.gensomechanic.mining.content.vein.ScannerService;
import dev.gensomechanic.mining.content.vein.ScannerTarget;
import dev.gensomechanic.mining.content.vein.VeinGenerationConfig;
import dev.gensomechanic.mining.network.MiningNetwork;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(GensoMining.MOD_ID)
public final class GensoMining {
    public static final String MOD_ID = "gensomining";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID)
            .defaultCreativeTab((ResourceKey<CreativeModeTab>) null);

    public GensoMining(IEventBus modEventBus, ModContainer container) {
        REGISTRATE.registerEventListeners(modEventBus);
        GensoCreativeModeTabs.register(modEventBus);
        REGISTRATE.setCreativeTab(GensoCreativeModeTabs.MAIN);
        MiningContent.register();
        dev.gensomechanic.mining.content.explosive.ExplosiveContent.register();
        dev.gensomechanic.mining.content.material.MiningMaterials.register();
        modEventBus.addListener(dev.gensomechanic.mining.content.explosive.ExplosiveContent::setup);
        GensoParticles.register(modEventBus);
        MiningRecipes.register(modEventBus);
        ScannerTarget.register(modEventBus);
        ScannerLang.register();
        modEventBus.addListener(MinerPortBlockEntity::registerCapabilities);
        modEventBus.addListener(MiningNetwork::register);
        container.registerConfig(ModConfig.Type.SERVER, ScannerService.CONFIG);
        container.registerConfig(ModConfig.Type.COMMON, VeinGenerationConfig.CONFIG, "gensomining-veins.toml");
        container.registerConfig(ModConfig.Type.COMMON, ArchiveService.CONFIG, "gensomining-exploration.toml");
        REGISTRATE.addRawLang("itemGroup.gensomining.main", "Genso Mechanic: Mining");
        LOGGER.info("{} {} initialized", MOD_ID, container.getModInfo().getVersion());
    }

    public static CreateRegistrate registrate() { return REGISTRATE; }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
