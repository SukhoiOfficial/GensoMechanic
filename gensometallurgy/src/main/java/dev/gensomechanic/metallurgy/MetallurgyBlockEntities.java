// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlockEntity;
import dev.gensomechanic.metallurgy.content.casting.CastingTableBlockEntity;
public final class MetallurgyBlockEntities {
    public static final BlockEntityEntry<IndustrialBlastFurnaceBlockEntity> INDUSTRIAL_BLAST_FURNACE = GensoMetallurgy.registrate()
        .blockEntity("industrial_blast_furnace", IndustrialBlastFurnaceBlockEntity::new)
        .validBlocks(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE).register();
    public static final BlockEntityEntry<CastingTableBlockEntity> CASTING_TABLE = GensoMetallurgy.registrate()
        .blockEntity("casting_table", CastingTableBlockEntity::new)
        .validBlocks(MetallurgyBlocks.CASTING_TABLE).register();
    private MetallurgyBlockEntities() {}
    public static void register() {}
}
