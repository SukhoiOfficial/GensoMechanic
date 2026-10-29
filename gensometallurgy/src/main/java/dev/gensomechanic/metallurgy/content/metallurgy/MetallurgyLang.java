package dev.gensomechanic.metallurgy.content.metallurgy;

import dev.gensomechanic.metallurgy.GensoMetallurgy;

final class MetallurgyLang {
    static void register() {
        var r = GensoMetallurgy.registrate();
        r.addRawLang("fluid.gensometallurgy.molten_brass", "Molten Brass");
        r.addRawLang("fluid.gensometallurgy.molten_copper", "Molten Copper");
        r.addRawLang("fluid.gensometallurgy.molten_gold", "Molten Gold");
        r.addRawLang("fluid.gensometallurgy.molten_iron", "Molten Iron");
        r.addRawLang("fluid.gensometallurgy.molten_netherite", "Molten Netherite");
        r.addRawLang("fluid.gensometallurgy.molten_zinc", "Molten Zinc");
        r.addRawLang("gensometallurgy.recipe.blast_furnace_smelting", "Blast Furnace Smelting");
        r.addRawLang("gensometallurgy.recipe.blast_furnace_fusing", "Blast Furnace Fusing");
        r.addRawLang("gensometallurgy.recipe.casting", "Casting");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_structure.header", "Building an Industrial Blast Furnace");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_structure.text_1", "The Industrial Blast Furnace is a multiblock structure for melting and processing metals.");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_structure.text_2", "Its footprint can be at most 3x3 blocks...");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_structure.text_3", "...and its height cannot exceed three times the side length of its base.");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_structure.text_4", "Every heat source beneath it must reach the required heat level before a matching recipe can be processed.");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_structure.text_5", "Use a Wrench to open the observation windows.");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_item_input.header", "Supplying Items to an Industrial Blast Furnace");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_item_input.text_1", "Ingredients must be supplied through the top of the furnace...");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_item_input.text_2", "...or dropped directly in from above.");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_processing.header", "Processing Metals in an Industrial Blast Furnace");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_processing.text_1", "Some recipes require a supply of gas.");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_processing.text_2", "Wait for processing...");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_processing.text_3", "Molten metals are stored inside the furnace.");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_processing.text_4", "Metals that meet the recipe requirements can be fused into an alloy.");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_output.header", "Casting Metals from an Industrial Blast Furnace");
        r.addRawLang("gensometallurgy.ponder.industrial_blast_furnace_output.text_1", "Use Casting Tables to cast molten metal into ingots.");
        r.addRawLang("gensometallurgy.goggles.blast_furnace.status", "Blast Furnace Status: %s");
        r.addRawLang("gensometallurgy.goggles.blast_furnace.item_container", "Item Container Info: %s");
        r.addRawLang("gensometallurgy.goggles.blast_furnace.item_amount", "%s/%s");
        r.addRawLang("gensometallurgy.goggles.blast_furnace.more_items", "and %s more...");
        r.addRawLang("gensometallurgy.goggles.blast_furnace.fluid_container", "Fluid Container Info: %s");
        r.addRawLang("gensometallurgy.goggles.blast_furnace.fluid_amount", "%smB/%smB");
        r.addRawLang("gensometallurgy.goggles.blast_furnace.more_fluids", "and %s more...");
        r.addRawLang("gensometallurgy.goggles.heat.unheated", "Unheated");
        r.addRawLang("gensometallurgy.goggles.heat.low_heated", "Low Heated");
        r.addRawLang("gensometallurgy.goggles.heat.heated", "Heated");
        r.addRawLang("gensometallurgy.goggles.heat.superheated", "Superheated");
        r.addRawLang("gensometallurgy.goggles.heat.special", "Special Heat");
        r.addRawLang("gensometallurgy.goggles.heat.mixed_special", "Mixed / Special Heat");
        r.addRawLang("gensometallurgy.goggles.casting_table.status", "Casting Table Status");
        r.addRawLang("gensometallurgy.goggles.casting_table.progress", "Casting Progress... %s");
        r.addRawLang("gensometallurgy.goggles.casting_table.fan", "Fan acceleration active");
        r.addRawLang("gensometallurgy.goggles.empty", "Empty");
    }
    private MetallurgyLang() {}
}
