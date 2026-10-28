package dev.gensomechanic.mining;

import com.simibubi.create.foundation.data.CreateRegistrate;
import dev.gensomechanic.mining.content.miner.MiningContent;
import dev.gensomechanic.mining.content.vein.ExplorationArchiveItem;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GensoCreativeModeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GensoMining.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gensomining.main"))
                    .icon(MiningContent.MECHANICAL_MINER::asStack)
                    .displayItems((parameters, output) -> orderedItems().forEach(output::accept))
                    .build());
    private static final List<String> ITEM_ORDER = List.of("mechanical_miner", "scanner", "exploration_archive",
            "crude_explosive", "nitro_explosive", "bauxite", "crushed_bauxite", "bauxite_block",
            "sulfur", "sulfur_dust", "sulfur_block", "nitre", "nitre_dust", "nitre_block");

    public static List<Item> orderedItems() {
        return GensoMining.registrate().getAll(Registries.ITEM).stream()
                .filter(entry -> CreateRegistrate.isInCreativeTab(entry, MAIN))
                .sorted(Comparator.comparingInt(entry -> {
                    int index = ITEM_ORDER.indexOf(entry.getId().getPath());
                    return index < 0 ? Integer.MAX_VALUE : index;
                }))
                .map(entry -> entry.get())
                .filter(item -> item != MiningContent.ARCHIVE.get() || ExplorationArchiveItem.mapsAvailable())
                .toList();
    }

    public static void register(IEventBus bus) { TABS.register(bus); }
    private GensoCreativeModeTabs() {}
}
