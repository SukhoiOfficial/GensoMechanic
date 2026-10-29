package dev.gensomechanic.metallurgy;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class MetallurgyItems {
    private static final CreateRegistrate R = GensoMetallurgy.registrate();
    static { R.setCreativeTab(GensoCreativeModeTabs.MAIN); }

    public static final ItemEntry<Item> CRUSHED_ANDESITE_ALLOY = R.item("crushed_andesite_alloy", Item::new)
            .lang("Crushed Andesite Alloy").register();
    public static final ItemEntry<Item> ANDESITE_ALLOY_DUST = R.item("andesite_alloy_dust", Item::new)
            .lang("Andesite Alloy Dust").tag(commonTag("dusts"), commonTag("dusts/andesite_alloy")).register();
    public static final ItemEntry<Item> COAL_DUST = R.item("coal_dust", Item::new)
            .lang("Coal Dust").tag(commonTag("dusts"), commonTag("dusts/coal")).burnTime(1600).register();

    private static TagKey<Item> commonTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    public static void register() {}
    private MetallurgyItems() {}
}
