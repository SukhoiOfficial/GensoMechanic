package dev.gensomechanic.mining.content.vein;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.content.miner.MiningRecipes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Conditional source mechanics only; this namespace never runs a distribution study. */
@GameTestHolder("gensomining_background")
@PrefixGameTestTemplate(false)
public final class RockBackgroundDefinitionTests {
    private static JsonObject fixture() {
        return JsonParser.parseString("""
                {"schema_version":1,"name":{"text":"Fixture"},"icon":"minecraft:stone",
                 "conditions":{},"priority":0,"capacity":1}
                """).getAsJsonObject();
    }

    private static RockBackgroundDefinition parse(GameTestHelper helper, String id, JsonObject json,
            Map<ResourceLocation, Collection<Holder<Biome>>> tags) {
        var registries = helper.getLevel().registryAccess();
        return RockBackgroundDefinition.parse(ResourceLocation.parse(id), json,
                registries.registryOrThrow(Registries.BIOME), tags, registries);
    }

    private static void filter(JsonObject json, String kind, String selectors) {
        json.getAsJsonObject("conditions").add(kind, JsonParser.parseString(selectors));
    }

    private static void rejected(GameTestHelper helper, JsonObject json, String description) {
        boolean refused = false;
        try { parse(helper, "test:invalid", json, Map.of()); }
        catch (JsonParseException expected) { refused = true; }
        helper.assertTrue(refused, "Accepted invalid background: " + description);
    }

    @GameTest(template = "empty", batch = "background_definition")
    public static void dimensionAndBiomeSelectorsRespectDenyAndMissingEntries(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos controller = helper.absolutePos(new BlockPos(1, 1, 1));
        var biome = level.getBiome(controller);
        String biomeId = biome.unwrapKey().orElseThrow().location().toString();
        String dimensionId = level.dimension().location().toString();
        var tagId = ResourceLocation.parse("test:controller_biome");
        Map<ResourceLocation, Collection<Holder<Biome>>> tags = Map.of(tagId, List.of(biome));

        var json = fixture();
        filter(json, "dimensions", "{\"allow\":[\"" + dimensionId + "\"]}");
        filter(json, "biomes", "{\"allow\":[\"" + biomeId + "\"]}");
        helper.assertTrue(parse(helper, "test:matching_ids", json, tags).matches(level, controller),
                "Actual dimension and biome IDs did not match");
        filter(json, "dimensions", "{\"allow\":[\"" + dimensionId + "\"],\"deny\":[\"" + dimensionId + "\"]}");
        helper.assertTrue(!parse(helper, "test:denied_dimension", json, tags).matches(level, controller),
                "Dimension deny did not override allow");
        filter(json, "dimensions", "{\"allow\":[\"test:other_dimension\"]}");
        helper.assertTrue(!parse(helper, "test:other_dimension", json, tags).matches(level, controller),
                "A different dimension matched");
        filter(json, "dimensions", "{}");
        filter(json, "biomes", "{\"allow\":[\"" + biomeId + "\"],\"deny\":[\"" + biomeId + "\"]}");
        helper.assertTrue(!parse(helper, "test:denied_biome", json, tags).matches(level, controller),
                "Biome ID deny did not override allow");

        filter(json, "biomes", "{\"allow\":[\"#test:controller_biome\"]}");
        var tagged = parse(helper, "test:tagged", json, tags);
        helper.assertTrue(tagged.matches(level, controller)
                && tagged.biomes().displayAllow().getFirst().members().contains(ResourceLocation.parse(biomeId)),
                "Biome tag did not resolve against the supplied reload snapshot");
        filter(json, "biomes", "{\"allow\":[\"" + biomeId + "\"],\"deny\":[\"#test:controller_biome\"]}");
        helper.assertTrue(!parse(helper, "test:denied_tag", json, tags).matches(level, controller),
                "Biome tag deny did not override an allowed ID");

        for (String missing : List.of("test:missing_biome", "#test:missing_tag")) {
            filter(json, "biomes", "{\"allow\":[{\"id\":\"" + missing + "\",\"required\":false}]}");
            var optional = parse(helper, "test:optional", json, tags);
            helper.assertTrue(!optional.biomes().unrestricted() && !optional.matches(level, controller),
                    "Missing optional allow entry became unrestricted: " + missing);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "background_definition")
    public static void heightsOrderingAndInvalidDefinitionsAreDeterministic(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos controller = helper.absolutePos(new BlockPos(1, 1, 1));
        int min = controller.getY(), max = min + 1;
        var json = fixture();
        json.getAsJsonObject("conditions").addProperty("min_y", min);
        json.getAsJsonObject("conditions").addProperty("max_y", max);
        var bounded = parse(helper, "test:bounded", json, Map.of());
        helper.assertTrue(bounded.matches(level, controller) && bounded.matches(level, controller.above()),
                "Height bounds must include the controller at both endpoints");
        helper.assertTrue(!bounded.matches(level, controller.below()) && !bounded.matches(level, controller.above(2)),
                "Height condition accepted a controller outside the range");

        var low = parse(helper, "test:lowest", fixture(), Map.of());
        var highJson = fixture(); highJson.addProperty("priority", 9);
        var highZ = parse(helper, "test:z_high", highJson, Map.of());
        var highA = parse(helper, "test:a_high", highJson, Map.of());
        var ordered = new ArrayList<>(List.of(low, highZ, highA));
        ordered.sort(RockBackgroundDefinition.ORDER);
        helper.assertTrue(ordered.equals(List.of(highA, highZ, low)), "Background priority or equal-priority ID ordering changed");

        var invalid = fixture();
        invalid.getAsJsonObject("conditions").addProperty("min_y", 10);
        invalid.getAsJsonObject("conditions").addProperty("max_y", 9);
        rejected(helper, invalid, "inverted height range");
        invalid = fixture(); invalid.addProperty("capacity", 0);
        rejected(helper, invalid, "zero capacity");
        invalid = fixture(); invalid.addProperty("capacity", 1.5);
        rejected(helper, invalid, "fractional capacity");
        invalid = fixture(); invalid.addProperty("unexpected", true);
        rejected(helper, invalid, "unknown root field");
        invalid = fixture(); invalid.getAsJsonObject("conditions").addProperty("max_height", 30);
        rejected(helper, invalid, "unknown condition field");
        invalid = fixture(); filter(invalid, "biomes", "{\"allow\":[\"#test:missing_required_tag\"]}");
        rejected(helper, invalid, "missing required biome tag");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "background_definition")
    public static void loadedBackgroundHasRecipeButNoRealVeinCatalogueEntry(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var id = GensoMining.asResource("rock_layer");
        helper.assertTrue(VeinManager.backgrounds(server).stream().anyMatch(background -> background.id().equals(id)),
                "Built-in rock background was not loaded");
        helper.assertTrue(!VeinManager.definitions(server).containsKey(id), "Rock background became a generated vein");
        helper.assertTrue(MiningRecipes.forServer(server).containsKey(id), "Rock background recipe was not indexed");
        var catalogue = ScannerService.catalogue(server);
        helper.assertTrue(catalogue.entries().stream().noneMatch(entry -> entry.id().equals(id)),
                "Rock background leaked into the real vein catalogue");
        helper.assertTrue(catalogue.backgrounds().stream().filter(entry -> entry.id().equals(id)).count() == 1,
                "Rock background is missing or duplicated in its separate recipe metadata");
        var metadata = catalogue.backgrounds().stream().filter(entry -> entry.id().equals(id)).findFirst().orElseThrow();
        helper.assertTrue(metadata.details().stream().noneMatch(line -> line.getContents() instanceof
                        net.minecraft.network.chat.contents.TranslatableContents text
                        && (text.getKey().equals("jei.gensomining.background.no_vein")
                        || text.getKey().equals("jei.gensomining.background.capacity"))),
                "Server still sends hidden background explanations to JEI");
        helper.succeed();
    }
}
