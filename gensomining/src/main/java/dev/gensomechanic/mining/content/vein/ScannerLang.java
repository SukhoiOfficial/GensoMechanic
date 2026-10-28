package dev.gensomechanic.mining.content.vein;
public final class ScannerLang {
    public static void register() {
        var r = dev.gensomechanic.mining.GensoMining.registrate();
        r.addRawLang("command.gensomining.vein.permission", "Requires cheat permission level 2.");
        r.addRawLang("command.gensomining.vein.dimension", "Execute in the receiving player's dimension.");
        r.addRawLang("command.gensomining.vein.unknown", "Unknown vein ID; use all or an ID from the suggestions.");
        r.addRawLang("command.gensomining.vein.started", "Survey started: radius %s blocks, target %s.");
        r.addRawLang("command.gensomining.vein.cancelled", "Survey cancelled; completed discoveries are retained.");
        r.addRawLang("command.gensomining.vein.failed", "Survey failed; results are incomplete. See the server log.");
        r.addRawLang("command.gensomining.vein.complete", "Survey complete: %s veins found.");
        r.addRawLang("command.gensomining.vein.page", "Survey results: page %s/%s");
        r.addRawLang("command.gensomining.vein.row", "%s — X: %s, Z: %s (%s chunks)");
        r.addRawLang("command.gensomining.vein.view_page", "[View page %s]");
        r.addRawLang("command.gensomining.vein.no_results", "No current results on this page.");

        r.addRawLang("vein.gensomining.copper_vein", "Copper Vein");
        r.addRawLang("vein.gensomining.large_copper_vein", "Large Copper Vein");
        r.addRawLang("vein.gensomining.iron_vein", "Iron Vein");
        r.addRawLang("vein.gensomining.large_iron_vein", "Large Iron Vein");
        r.addRawLang("vein.gensomining.coal_field", "Coal Field");
        r.addRawLang("vein.gensomining.large_coal_field", "Large Coal Field");
        r.addRawLang("vein.gensomining.lapis_vein", "Lapis Lazuli Vein");
        r.addRawLang("vein.gensomining.bauxite_vein", "Bauxite Vein");
        r.addRawLang("vein.gensomining.nether_gold_vein", "Gold Vein");
        r.addRawLang("vein.gensomining.nitre_vein", "Nitre Vein");
        r.addRawLang("vein.gensomining.pure_nitre_vein", "High-Purity Nitre Vein");
        r.addRawLang("vein.gensomining.sulfur_vein", "Sulfur Vein");
        r.addRawLang("vein.gensomining.pure_sulfur_vein", "High-Purity Sulfur Vein");
        r.addRawLang("vein.gensomining.glowstone_cluster", "Glowstone Cluster");
        r.addRawLang("vein.gensomining.rose_quartz_cluster", "Rose Quartz Cluster");
        r.addRawLang("vein.gensomining.pure_rose_quartz_cluster", "High-Purity Rose Quartz Cluster");
        r.addRawLang("rock_background.gensomining.nether_rock_layer", "Nether Rock Layer");
        r.addRawLang("jei.gensomining.vein_generation", "Vein Generation");
        r.addRawLang("jei.gensomining.generation.dimension_allow", "Dimension Whitelist");
        r.addRawLang("jei.gensomining.generation.dimension_deny", "Dimension Blacklist");
        r.addRawLang("jei.gensomining.generation.biome_allow", "Biome Whitelist");
        r.addRawLang("jei.gensomining.generation.biome_deny", "Biome Blacklist");
        r.addRawLang("jei.gensomining.generation.any", "Unrestricted");
        r.addRawLang("jei.gensomining.generation.none", "None");
        r.addRawLang("jei.gensomining.generation.unrestricted", "No Generation Conditions");
        r.addRawLang("jei.gensomining.generation.empty_tag", "No members");
        r.addRawLang("jei.gensomining.generation.area", "Area in chunks (band weight): %s");
        r.addRawLang("jei.gensomining.generation.profile_weights", "%s: category weight %s, variant weight %s");
        r.addRawLang("jei.gensomining.generation.profile_allow", "Profile biome whitelist: %s");
        r.addRawLang("jei.gensomining.generation.profile_deny", "Profile biome blacklist: %s");
        r.addRawLang("jei.gensomining.generation.sampling", "Biome sample Y:weight: %s");
        r.addRawLang("jei.gensomining.generation.regional_weight", "%s: category ×%s, variant weight %s");
        r.addRawLang("jei.gensomining.generation.scroll", "%s–%s / %s (scroll to view)");
        r.addRawLang("dimension.minecraft.overworld", "Overworld");
        r.addRawLang("dimension.minecraft.the_nether", "The Nether");
        r.addRawLang("dimension.minecraft.the_end", "The End");

        r.addRawLang("scanner.gensomining.all_targets", "All Targets");
        r.addRawLang("scanner.gensomining.archive_records", "Recorded Target");
        r.addRawLang("scanner.gensomining.archive_target", "Recorded target: %s");
        r.addRawLang("scanner.gensomining.analysing", "Analysing…");
        r.addRawLang("scanner.gensomining.map.settings", "Geological Scanner");
        r.addRawLang("scanner.gensomining.map.enabled", "Show Scan Results");
        r.addRawLang("scanner.gensomining.map.duration", "Display Duration (seconds)");
        r.addRawLang("scanner.gensomining.map.opacity", "Opacity (%)");

        r.addRawLang("direction.gensomining.east", "east");
        r.addRawLang("direction.gensomining.north", "north");
        r.addRawLang("direction.gensomining.north_east", "northeast");
        r.addRawLang("direction.gensomining.north_west", "northwest");
        r.addRawLang("direction.gensomining.south", "south");
        r.addRawLang("direction.gensomining.south_east", "southeast");
        r.addRawLang("direction.gensomining.south_west", "southwest");
        r.addRawLang("direction.gensomining.west", "west");
        r.addRawLang("scanner.gensomining.bearing_distance", "%s %sm");
        r.addRawLang("scanner.gensomining.capacity", "Capacity: %s in use");
        r.addRawLang("scanner.gensomining.current", "Current chunk: %s");
        r.addRawLang("scanner.gensomining.header", "%s: Chunk %s World time %s");
        r.addRawLang("scanner.gensomining.no_selection", "No target");
        r.addRawLang("scanner.gensomining.no_target", "No scan target set");
        r.addRawLang("scanner.gensomining.no_trace", "No veins detected in nearby chunks");
        r.addRawLang("scanner.gensomining.none", "No vein detected");
        r.addRawLang("scanner.gensomining.scanning", "Scanning geological information\u2026");
        r.addRawLang("scanner.gensomining.select_target", "Scan Target");
        r.addRawLang("scanner.gensomining.target", "Current target: %s");
        r.addRawLang("scanner.gensomining.title", "Scan Information");
        r.addRawLang("scanner.gensomining.trace", "Detected %s to the %s");
        r.addRawLang("vein.gensomining.andesite_layer", "Andesite Stratum");
    }
    private ScannerLang() {}
}
