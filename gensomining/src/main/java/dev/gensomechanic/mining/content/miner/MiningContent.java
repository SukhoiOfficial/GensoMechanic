package dev.gensomechanic.mining.content.miner;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.*;
import dev.gensomechanic.mining.*;
import dev.gensomechanic.mining.content.vein.GeologicalScannerItem;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;

public final class MiningContent {
    private static final CreateRegistrate R = GensoMining.registrate();
    static { R.setCreativeTab(GensoCreativeModeTabs.MAIN); }
    public static final BlockEntry<MechanicalMinerBlock> MECHANICAL_MINER = R.block("mechanical_miner", MechanicalMinerBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(5, 6).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL, BlockTags.create(com.simibubi.create.Create.asResource("non_movable")))
            .onRegister(b -> BlockStressValues.IMPACTS.register(b, () -> 0d))
            .blockstate((c, p) -> p.horizontalBlock(c.get(), p.models().getExistingFile(p.modLoc("block/mechanical_miner/block"))))
            .lang("Mechanical Miner").item(MechanicalMinerItem::new).model((c, p) -> {}).build().register();
    public static final BlockEntry<MechanicalMinerPartBlock> MINER_PART = R.block("mechanical_miner_part", MechanicalMinerPartBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.strength(5, 6).requiresCorrectToolForDrops().noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL, BlockTags.create(com.simibubi.create.Create.asResource("non_movable")))
            .blockstate((c, p) -> p.simpleBlock(c.get(), p.models().getExistingFile(p.modLoc("block/mechanical_miner/empty"))))
            .lang("Mechanical Miner").register();
    public static final BlockEntry<MinerPortBlock> MINER_INPUT = port("mechanical_miner_input");
    public static final BlockEntry<MinerPortBlock> MINER_OUTPUT = port("mechanical_miner_output");
    private static BlockEntry<MinerPortBlock> port(String name) {
        return R.block(name, MinerPortBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(p -> p.strength(5, 6).requiresCorrectToolForDrops().noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK))
                .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_IRON_TOOL, BlockTags.create(com.simibubi.create.Create.asResource("non_movable")))
                .blockstate((c, p) -> p.simpleBlock(c.get(), p.models().getExistingFile(p.modLoc("block/mechanical_miner/empty"))))
                .lang("Mechanical Miner").register();
    }
    public static final BlockEntityEntry<MechanicalMinerBlockEntity> MINER_ENTITY = R.blockEntity("mechanical_miner", MechanicalMinerBlockEntity::new)
            .validBlocks(MECHANICAL_MINER).register();
    public static final BlockEntityEntry<MinerPortBlockEntity> PORT_ENTITY = R.blockEntity("mechanical_miner_port", MinerPortBlockEntity::new)
            .validBlocks(MINER_INPUT, MINER_OUTPUT).register();
    public static final ItemEntry<GeologicalScannerItem> SCANNER = R.item("scanner", GeologicalScannerItem::new)
            .properties(p -> p.stacksTo(1)).lang("Geological Scanner").model((c, p) -> {}).register();
    public static final ItemEntry<dev.gensomechanic.mining.content.vein.ExplorationArchiveItem> ARCHIVE = R.item("exploration_archive", dev.gensomechanic.mining.content.vein.ExplorationArchiveItem::new)
            .properties(p -> p.stacksTo(1)).lang("Exploration Archive")
            .model((c, p) -> p.generated(c, p.modLoc("item/scanner_documents"))).register();
    public static void register() {}
    private MiningContent() {}
}
