package dev.gensomechanic.metallurgy.client.metallurgy;

import dev.gensomechanic.metallurgy.GensoMetallurgy;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

public final class MetallurgyModels {
    public static final PartialModel INDUSTRIAL_BLAST_FURNACE_GAUGE =
        block("industrial_blast_furnace/gauge/gauge");
    public static final PartialModel INDUSTRIAL_BLAST_FURNACE_GAUGE_DIAL =
        block("industrial_blast_furnace/gauge/gauge_dial");

    private MetallurgyModels() {
    }

    private static PartialModel block(String path) {
        return PartialModel.of(GensoMetallurgy.asResource("block/" + path));
    }

    public static void init() {
        // Loads the static partial models early enough for the model bake cycle.
    }
}
