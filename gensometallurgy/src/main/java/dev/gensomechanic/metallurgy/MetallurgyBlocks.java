// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntry;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.*;
import dev.gensomechanic.metallurgy.content.casting.CastingTableBlock;
import dev.gensomechanic.metallurgy.client.metallurgy.IndustrialBlastFurnaceModel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
public final class MetallurgyBlocks {
    // JEI and other integrations may load this class before the mod constructor runs.
    private static final CreateRegistrate REGISTRATE = GensoMetallurgy.registrate()
        .setCreativeTab(GensoCreativeModeTabs.MAIN);

    public static final BlockEntry<IndustrialBlastFurnaceBlock> INDUSTRIAL_BLAST_FURNACE =
        REGISTRATE
            .block("industrial_blast_furnace", IndustrialBlastFurnaceBlock::new)
            .initialProperties(() -> Blocks.ANDESITE)
            .properties(properties -> properties.noOcclusion().strength(3.5f, 6.0f))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .blockstate(new IndustrialBlastFurnaceGenerator()::generate)
            .onRegister(CreateRegistrate.blockModel(() -> IndustrialBlastFurnaceModel.factory()))
            .loot((loot, block) -> loot.dropSelf(block))
            .item(IndustrialBlastFurnaceItem::new)
            .properties(Item.Properties::fireResistant)
            .model(AssetLookup.customBlockItemModel("_", "block_single"))
            .build()
            .register();

    public static final BlockEntry<CastingTableBlock> CASTING_TABLE =
        REGISTRATE
            .block("casting_table", CastingTableBlock::new)
            .initialProperties(() -> Blocks.ANDESITE)
            .properties(properties -> properties.noOcclusion().strength(3.5f, 6.0f))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .blockstate((context, provider) -> provider.horizontalBlock(context.getEntry(),
                provider.models().getExistingFile(provider.modLoc("block/casting_table"))))
            .loot((loot, block) -> loot.dropSelf(block))
            .simpleItem()
            .register();

    private MetallurgyBlocks() {}
    public static void register() {}
}
