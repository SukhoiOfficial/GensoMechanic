package dev.gensomechanic.metallurgy;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import dev.gensomechanic.metallurgy.content.casting.CastingTableBlockEntity;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlockEntity;
import dev.gensomechanic.metallurgy.content.metallurgy.MetallurgyRecipeTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(GensoMetallurgy.MOD_ID)
public final class GensoMetallurgy {
    public static final String MOD_ID = "gensometallurgy";
    public static final String NAME = "Genso Metallurgy";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID)
            .defaultCreativeTab((ResourceKey<CreativeModeTab>) null);

    public GensoMetallurgy(IEventBus bus, ModContainer container) {
        REGISTRATE.registerEventListeners(bus);
        GensoCreativeModeTabs.register(bus);
        REGISTRATE.setCreativeTab(GensoCreativeModeTabs.MAIN);
        MetallurgyBlocks.register();
        MetallurgyBlockEntities.register();
        MetallurgyItems.register();
        GensoFluids.register(bus);
        MetallurgyRecipeTypes.register(bus);
        bus.addListener(IndustrialBlastFurnaceBlockEntity::registerCapabilities);
        bus.addListener(CastingTableBlockEntity::registerCapabilities);
        REGISTRATE.addRawLang("itemGroup.gensometallurgy.main", "Genso Mechanic: Metallurgy");
        LOGGER.info("{} {} initialized", MOD_ID, container.getModInfo().getVersion());
    }

    public static CreateRegistrate registrate() { return REGISTRATE; }
    public static ResourceLocation asResource(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
}
