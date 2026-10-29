package dev.gensomechanic.metallurgy.client.metallurgy;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import dev.gensomechanic.metallurgy.GensoMetallurgy;

public final class MetallurgySpriteShifts {
    public static final CTSpriteShiftEntry INDUSTRIAL_BLAST_FURNACE_CASING = rectangle("casing");
    public static final CTSpriteShiftEntry INDUSTRIAL_BLAST_FURNACE_TOP = rectangle("top");
    public static final CTSpriteShiftEntry INDUSTRIAL_BLAST_FURNACE_INNER = rectangle("inner");

    private MetallurgySpriteShifts() {
    }

    private static CTSpriteShiftEntry rectangle(String name) {
        return get(AllCTTypes.RECTANGLE, name);
    }

    private static CTSpriteShiftEntry get(com.simibubi.create.foundation.block.connected.CTType type, String name) {
        String path = "block/industrial_blast_furnace/" + name;
        return CTSpriteShifter.getCT(type,
            GensoMetallurgy.asResource(path),
            GensoMetallurgy.asResource(path + "_connected"));
    }

    public static void register() {
    }
}
