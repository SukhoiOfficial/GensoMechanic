package dev.gensomechanic.metallurgy.client;

import dev.gensomechanic.metallurgy.GensoMetallurgy;
import dev.gensomechanic.metallurgy.client.metallurgy.MetallurgyClient;
import dev.gensomechanic.metallurgy.client.metallurgy.MetallurgyModels;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Client entry point; optional viewer plugins are discovered by their own loaders. */
@Mod(value = GensoMetallurgy.MOD_ID, dist = Dist.CLIENT)
public final class GensoMetallurgyClient {
    public GensoMetallurgyClient(IEventBus bus) {
        // PartialModels must exist before Flywheel's additional-model registration event.
        MetallurgyModels.init();
        bus.addListener(MetallurgyClient::renderers);
        bus.addListener(MetallurgyClient::extensions);
        bus.addListener(MetallurgyClient::setup);
    }
}
