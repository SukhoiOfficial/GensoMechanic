package dev.gensomechanic.mining.content.miner;

import dev.gensomechanic.mining.GensoMining;

final class MiningLang {
    static void register() {
        var registrate = GensoMining.registrate();
        registrate.addRawLang("tooltip.gensomining.variable_stress", "Varies by recipe");
        registrate.addRawLang("miner.gensomining.status.incomplete", "Structure incomplete or not fully loaded");
        registrate.addRawLang("miner.gensomining.status.no_recipe", "No matching mining recipe");
        registrate.addRawLang("miner.gensomining.status.stopped", "Awaiting rotation");
        registrate.addRawLang("miner.gensomining.status.overstressed", "Overstressed");
        registrate.addRawLang("miner.gensomining.status.output_full", "Output full");
        registrate.addRawLang("miner.gensomining.status.mining", "Mining");
        registrate.addRawLang("miner.gensomining.vein", "Vein: %s");
        registrate.addRawLang("miner.gensomining.progress", "Progress: %s | Efficiency: %s");
        registrate.addRawLang("jei.gensomining.mining", "Mining");
        registrate.addRawLang("jei.gensomining.vein_condition", "Requires this vein in the controller's chunk");
        registrate.addRawLang("jei.gensomining.rock_background", "Rock background");
        registrate.addRawLang("rock_background.gensomining.rock_layer", "Rock Layer");
        registrate.addRawLang("miner.gensomining.rock_background", "Rock background: %s");
        registrate.addRawLang("jei.gensomining.background.no_vein", "Requires a chunk with no mineral vein");
        registrate.addRawLang("jei.gensomining.background.height", "Controller height: %s to %s");
        registrate.addRawLang("jei.gensomining.background.priority", "Priority: %s");
        registrate.addRawLang("jei.gensomining.background.capacity", "Shared capacity per chunk: %s");
        registrate.addRawLang("jei.gensomining.outputs", "Outputs");
        registrate.addRawLang("jei.gensomining.base_time", "Base time: %s ticks (16 RPM)");
        registrate.addRawLang("jei.gensomining.stress", "Stress impact: %s SU/RPM");
        registrate.addRawLang("miner.gensomining.capacity_cost", "Capacity occupied: %s");
        registrate.addRawLang("miner.gensomining.modifiers", "Active modifiers: %s");
        registrate.addRawLang("miner.gensomining.modifiers.lubricant", "Lubricant");
        registrate.addRawLang("miner.gensomining.modifiers.explosive", "Explosive");
        registrate.addRawLang("miner.gensomining.modifiers.both", "Lubricant + Explosive");
        registrate.addRawLang("miner.gensomining.explosive_stock", "Explosives: %s ×%s");
        registrate.addRawLang("jei.gensomining.page.base", "Base recipe");
        registrate.addRawLang("jei.gensomining.page.lubricant", "Lubricant modifier");
        registrate.addRawLang("jei.gensomining.page.explosive", "Explosive modifier");
        registrate.addRawLang("jei.gensomining.parameter.time", "Mining time: %s ticks");
        registrate.addRawLang("jei.gensomining.parameter.stress", "Stress: %s SU/RPM");
        registrate.addRawLang("jei.gensomining.parameter.capacity", "Capacity occupied: %s");
        registrate.addRawLang("jei.gensomining.change", " (%s)");
        registrate.addRawLang("jei.gensomining.consumed_per_cycle", "Consumed when each mining cycle starts");
        registrate.addRawLang("jei.gensomining.roll.stack", "Chance applies to the entire stack");
        registrate.addRawLang("jei.gensomining.original_chance", "Base: %s%%; change: %s percentage points");
        registrate.addRawLang("jei.gensomining.additional_output", "Additional output");
    }
    private MiningLang() {}
}
