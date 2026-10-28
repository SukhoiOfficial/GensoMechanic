package dev.gensomechanic.mining.content.material;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.ItemEntry;
import dev.gensomechanic.mining.GensoCreativeModeTabs;
import dev.gensomechanic.mining.GensoMining;
import net.minecraft.world.item.Item;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Mining materials; bauxite remains distinct from Create's optional raw aluminum chain. */
public final class MiningMaterials {
    private static final CreateRegistrate R = GensoMining.registrate();
    static { R.setCreativeTab(GensoCreativeModeTabs.MAIN); }

    public static final ItemEntry<Item> BAUXITE = R.item("bauxite", Item::new)
            .lang("Bauxite").register();
    public static final ItemEntry<Item> CRUSHED_BAUXITE = R.item("crushed_bauxite", Item::new)
            .lang("Crushed Bauxite").register();
    public static final BlockEntry<Block> BAUXITE_BLOCK = R.block("bauxite_block", Block::new)
            .initialProperties(() -> Blocks.RAW_COPPER_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .lang("Block of Bauxite").simpleItem().register();

    public static final ItemEntry<Item> SULFUR = R.item("sulfur", Item::new)
            .lang("Sulfur").register();
    public static final ItemEntry<Item> SULFUR_DUST = R.item("sulfur_dust", Item::new)
            .lang("Sulfur Dust").register();
    public static final BlockEntry<Block> SULFUR_BLOCK = R.block("sulfur_block", Block::new)
            .initialProperties(() -> Blocks.RAW_COPPER_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .lang("Block of Sulfur").simpleItem().register();

    public static final ItemEntry<Item> NITRE = R.item("nitre", Item::new)
            .lang("Nitre").register();
    public static final ItemEntry<Item> NITRE_DUST = R.item("nitre_dust", Item::new)
            .lang("Nitre Dust").register();
    public static final BlockEntry<Block> NITRE_BLOCK = R.block("nitre_block", Block::new)
            .initialProperties(() -> Blocks.RAW_COPPER_BLOCK)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL)
            .lang("Block of Nitre").simpleItem().register();

    public static void register() {}
    private MiningMaterials() {}
}
