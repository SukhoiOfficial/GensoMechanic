package dev.gensomechanic.metallurgy;

import com.simibubi.create.foundation.data.CreateRegistrate;
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
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GensoMetallurgy.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.gensometallurgy.main"))
            .icon(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE::asStack)
            .displayItems((parameters, output) -> orderedItems().forEach(output::accept)).build());
    private static final List<String> ORDER = List.of("industrial_blast_furnace", "casting_table",
            "crushed_andesite_alloy", "andesite_alloy_dust", "coal_dust", "molten_brass_bucket",
            "molten_copper_bucket", "molten_gold_bucket", "molten_iron_bucket", "molten_netherite_bucket", "molten_zinc_bucket");

    public static List<Item> orderedItems() {
        return GensoMetallurgy.registrate().getAll(Registries.ITEM).stream()
                .filter(e -> CreateRegistrate.isInCreativeTab(e, MAIN))
                .sorted(Comparator.comparingInt(e -> { int i = ORDER.indexOf(e.getId().getPath()); return i < 0 ? Integer.MAX_VALUE : i; }))
                .map(e -> e.get()).filter(GensoFluids::visible).toList();
    }
    public static void register(IEventBus bus) { TABS.register(bus); }
    private GensoCreativeModeTabs() {}
}
