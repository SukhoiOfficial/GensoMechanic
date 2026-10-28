package dev.gensomechanic.mining.content.vein;

import com.google.gson.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.content.material.MiningMaterials;
import dev.gensomechanic.mining.content.miner.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;

/** Small deterministic mechanism fixtures, never a distribution study. Run with and without BOP. */
@GameTestHolder("gensomining_nether")
@PrefixGameTestTemplate(false)
public final class NetherMiningTests {
    private static ResourceLocation id(String value){return GensoMining.asResource(value);}
    private static ResourceLocation biome(String value){return ResourceLocation.parse(value);}
    private static VeinGenerator.Environment environment(String name) {
        return new VeinGenerator.Environment() {
            public long seed(){return 20261028;}
            public ResourceLocation dimension(){return Level.NETHER.location();}
            public ResourceLocation biomeAt(ChunkPos p,int y){return biome(name);}
        };
    }
    @GameTest(template="empty")
    public static void terraBlenderSamplesMatchTheWorldBiomeSource(GameTestHelper h) {
        if(!ModList.get().isLoaded("terrablender")){h.succeed();return;}
        var level=h.getLevel().getServer().getLevel(Level.NETHER);
        var source=level.getChunkSource().getGenerator().getBiomeSource();
        var sampler=level.getChunkSource().randomState().sampler();
        var biomes=new VeinBiomes(level);int bopSamples=0;
        // Bounded API integration fixture: no chunk generation or vein distribution metrics.
        for(int x=-4;x<4;x++)for(int z=-4;z<4;z++) {
            var chunk=new ChunkPos(x*37,z*37);
            var expected=source.getNoiseBiome(QuartPos.fromBlock(chunk.getMiddleBlockX()),16,
                    QuartPos.fromBlock(chunk.getMiddleBlockZ()),sampler).unwrapKey().orElseThrow().location();
            h.assertTrue(biomes.biomeAt(chunk,64).equals(expected),"Vein sampling bypasses the native biome source at "+chunk);
            if(expected.getNamespace().equals("biomesoplenty"))bopSamples++;
        }
        if(ModList.get().isLoaded("biomesoplenty"))h.assertTrue(bopSamples>0,"Fixture never exercised a BOP biome");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void conditionalCataloguesAndBackgroundStayInTheirDimensions(GameTestHelper h) {
        var server=h.getLevel().getServer();var generator=VeinManager.generator(server);
        boolean bop=ModList.get().isLoaded("biomesoplenty");
        var nether=generator.dimension(Level.NETHER.location());
        h.assertTrue(nether!=null && nether.definitions().size()==(bop?8:6),"Conditional Nether catalogue size");
        h.assertTrue(generator.dimension(Level.OVERWORLD.location()).definitions().size()==9 && generator.dimension(Level.END.location())==null,"Nether entries leaked into other dimensions");
        h.assertTrue(generator.definitions().containsKey(id("rose_quartz_cluster"))==bop
                && MiningRecipes.forServer(server).containsKey(id("rose_quartz_cluster"))==bop,"Optional rose source/recipe disagree");
        h.assertTrue(MiningRecipes.forServer(server).size()==(bop?19:17),"Missing/disabled mining recipe");
        var catalogue=ScannerService.catalogue(server);
        h.assertTrue(catalogue.entries().size()==(bop?17:15) && catalogue.backgrounds().size()==2,"Network catalogue omitted conditional entries");
        var target=MiningTarget.resolve(server.getLevel(Level.NETHER),new BlockPos(0,64,0),Optional.empty());
        h.assertTrue(target!=null && target.id().equals(id("nether_rock_layer")) && target.capacity()==4,"Nether fallback background");
        h.assertTrue(MiningTarget.resolve(h.getLevel(),new BlockPos(0,64,0),Optional.empty()).id().equals(id("rock_layer")),"Overworld fallback changed");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void actualMemberBiomesGatePureVariantsAndDistrictCounts(GameTestHelper h) {
        var generator=VeinManager.generator(h.getLevel().getServer());var dimension=generator.dimension(Level.NETHER.location());
        var names=new ArrayList<>(List.of("minecraft:nether_wastes","minecraft:soul_sand_valley","minecraft:crimson_forest","minecraft:warped_forest","minecraft:basalt_deltas"));
        if(ModList.get().isLoaded("biomesoplenty"))names.addAll(List.of("biomesoplenty:erupting_inferno","biomesoplenty:visceral_heap","biomesoplenty:crystalline_chasm"));
        for(String name:names) {
            var resources=new VeinResources(dimension,environment(name),()->{},()->{},256);
            var count=resources.districtCount(new ChunkPos(0,0));
            boolean preferred=!name.endsWith("nether_wastes")&&!name.endsWith("soul_sand_valley");
            h.assertTrue(count.min()==(preferred?3:2) && count.max()==(preferred?8:4),"Wrong count for "+name);
            for(String pure:List.of("pure_nitre_vein","pure_sulfur_vein","pure_rose_quartz_cluster")) {
                var definition=dimension.definitions().get(id(pure));if(definition==null)continue;
                boolean allowed=pure.equals("pure_nitre_vein") ? name.endsWith("crimson_forest")||name.endsWith("warped_forest")||name.endsWith("visceral_heap")
                        :pure.equals("pure_sulfur_vein") ? name.endsWith("basalt_deltas")||name.endsWith("erupting_inferno"):name.endsWith("crystalline_chasm");
                h.assertTrue(resources.permits(definition,new ChunkPos(0,0))==allowed,"Pure variant escapes its biome: "+pure+" / "+name);
            }
        }
        h.assertTrue(dimension.profile().maxDistrictMembers()==8,"Planner budget ignores profile overrides");h.succeed();
    }
    @GameTest(template="empty")
    public static void districtProposalsUseTheSelectedCount(GameTestHelper h) throws Exception {
        var original=VeinManager.generator(h.getLevel().getServer());var p=original.dimension(Level.NETHER.location()).profile();var d=p.districts();
        var rules=p.rules().stream().map(r->new VeinWorldDefinition.BiomeRule(r.id(),r.priority(),r.influence(),r.biomes(),r.display(),r.multipliers(),r.variants(),new VeinWorldDefinition.VeinCount(3,3))).toList();
        var profile=new VeinWorldDefinition(p.id(),p.dimensions(),p.placement(),new VeinWorldDefinition.Districts(2,2,1,d.step(),d.radius(),d.gap(),d.linkDistance(),d.attempts(),d.environmentRadius()),p.geology(),p.sampling(),p.environment(),p.biomeFraction(),p.memberFraction(),p.resources(),rules);
        var generator=new VeinGenerator(original.definitions().values(),List.of(profile));
        var proposal=VeinPlanner.class.getDeclaredMethod("proposal",VeinSites.Site.class);proposal.setAccessible(true);
        for(String name:List.of("minecraft:nether_wastes","minecraft:warped_forest")) {
            var planner=new VeinPlanner(generator,environment(name));
            @SuppressWarnings("unchecked") var result=(Optional<VeinPlanner.District>)proposal.invoke(planner,new VeinSites.Site(new VeinSites.Key(0,0,0),20261028,0,0,8.5));
            int expected=name.endsWith("nether_wastes")?2:3;
            h.assertTrue(result.isPresent() && result.get().veins().size()==expected,"Proposal ignored biome count: "+name);
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void optionalRulesSkipBeforeResolvingReferencesAndInvalidCountsFail(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer();JsonObject json;
        try(var reader=server.getResourceManager().openAsReader(id("vein_worlds/the_nether.json"))){json=JsonParser.parseReader(reader).getAsJsonObject();}
        var context=new VeinData.Context(h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),Map.of());
        var skipped=JsonParser.parseString("{\"category\":\"missing:ore\",\"requires_mod\":\"missing_test_mod\"}").getAsJsonObject();
        json.getAsJsonArray("resources").add(skipped);
        json.getAsJsonArray("biome_profiles").add(JsonParser.parseString("{\"requires_mod\":\"missing_test_mod\",\"id\":\"optional_missing\"}"));
        var parsed=VeinWorldDefinition.parse(id("fixture"),json,context,VeinManager.definitions(server));
        h.assertTrue(parsed.resources().size()==(ModList.get().isLoaded("biomesoplenty")?5:4),"Optional category was not skipped");
        var range=json.getAsJsonArray("biome_profiles").get(0).getAsJsonObject().getAsJsonObject("districts");range.addProperty("min_veins",9);range.addProperty("max_veins",8);
        boolean rejected=false;
        try{VeinWorldDefinition.parse(id("fixture"),json,context,VeinManager.definitions(server));}catch(JsonParseException expected){rejected=true;}
        h.assertTrue(rejected,"Invalid profile count accepted");h.succeed();
    }
    @GameTest(template="empty")
    public static void probabilityOutputsKeepExplosiveTotalsAndPerItemRolls(GameTestHelper h) {
        var server=h.getLevel().getServer();var dimension=VeinManager.generator(server).dimension(Level.NETHER.location());
        var reducedBaseTypes=Set.of("nitre_vein","pure_nitre_vein","sulfur_vein","pure_sulfur_vein","rose_quartz_cluster","pure_rose_quartz_cluster");
        for(var definition:dimension.definitions().values()) {
            var recipe=MiningRecipes.forServer(server).get(definition.id()).value();var base=recipe.modifiers().baseOutputs().getFirst();
            h.assertTrue(recipe.duration()==3200 && base.chance()==.8 && !base.stackRoll(),"Probability/timing definition");
            double expectation=base.stack().getCount()*.8;
            for(String explosive:List.of("","crude_explosive","nitro_explosive")) {
                var op=recipe.modifiers().operation("",explosive);double total=op.outputs().stream().filter(o->o.stack().is(base.stack().getItem())).mapToDouble(o->o.stack().getCount()*o.chance()).sum();
                int factor=explosive.isEmpty()?1:(explosive.startsWith("crude")?2:4)*(reducedBaseTypes.contains(definition.id().getPath())?2:1);
                h.assertTrue(Math.abs(total-expectation*factor)<1e-9 && op.duration()==3200,"Explosive expected yield/time changed");
            }
            var random=new LegacyRandomSource(0){int index;@Override public float nextFloat(){return index++%2==0?.1f:.9f;}};
            h.assertTrue(base.roll(random).getCount()==(base.stack().getCount()+1)/2,"Output rolled a whole stack instead of separate items");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void nativeCreateCompressionAndMillingFallbackWork(GameTestHelper h) {
        var level=h.getLevel();var manager=level.getRecipeManager();
        for(String material:List.of("sulfur","nitre")) {
            var packing=manager.byKey(id("crafting/"+material+"_block")).orElseThrow();
            h.assertTrue(MechanicalPressBlockEntity.canCompress(packing.value())&&!AllRecipeTypes.shouldIgnoreInAutomation(packing),"Native press rejects storage crafting recipe");
            h.assertTrue(manager.byKey(id("compacting/"+material+"_block")).isEmpty() && manager.byKey(id("crushing/"+material)).isEmpty(),"Redundant native recipe override");
            var crusher=new CrushingWheelControllerBlockEntity(AllBlockEntityTypes.CRUSHING_WHEEL_CONTROLLER.get(),h.absolutePos(new BlockPos(1,1,1)),AllBlocks.CRUSHING_WHEEL_CONTROLLER.getDefaultState());crusher.setLevel(level);
            crusher.inventory.setStackInSlot(0,material.equals("sulfur")?MiningMaterials.SULFUR.asStack():MiningMaterials.NITRE.asStack());
            h.assertTrue(crusher.findRecipe().orElseThrow().value().getType()==AllRecipeTypes.MILLING.getType(),"Crushing wheel does not fall back to milling");
            crusher.inventory.setStackInSlot(0,material.equals("sulfur")?MiningMaterials.SULFUR_BLOCK.asStack():MiningMaterials.NITRE_BLOCK.asStack());
            var block=crusher.findRecipe().orElseThrow();
            h.assertTrue(block.value().getType()==AllRecipeTypes.CRUSHING.getType() && block.value().getResultItem(level.registryAccess()).getCount()==9,"Block crushing is not nine powders");
        }
        h.succeed();
    }
}
