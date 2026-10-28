package dev.gensomechanic.mining.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.gensomechanic.mining.GensoMining;

public final class GensoPartialModels {
    public static final PartialModel SHAFT = part("shaft"), DRILL = part("drill"), FAN = part("fan"),
            FAN_VISUAL = part("fan_visual"), INPUT_TRAY = part("input_tray"), OUTPUT_TRAY = part("output_tray"),
            INPUT_FUNNEL_CLOSED = part("input_funnel_closed"), INPUT_FUNNEL_OPEN = part("input_funnel_open"),
            OUTPUT_FUNNEL_CLOSED = part("output_funnel_closed"), OUTPUT_FUNNEL_OPEN = part("output_funnel_open");
    private static PartialModel part(String name) { return PartialModel.of(GensoMining.asResource("block/mechanical_miner/" + name)); }
    public static void init() {}
    public static boolean ready() { return SHAFT.get() != null && DRILL.get() != null && FAN.get() != null
            && INPUT_TRAY.get() != null && OUTPUT_TRAY.get() != null && INPUT_FUNNEL_CLOSED.get() != null
            && INPUT_FUNNEL_OPEN.get() != null && OUTPUT_FUNNEL_CLOSED.get() != null && OUTPUT_FUNNEL_OPEN.get() != null; }
    private GensoPartialModels() {}
}
