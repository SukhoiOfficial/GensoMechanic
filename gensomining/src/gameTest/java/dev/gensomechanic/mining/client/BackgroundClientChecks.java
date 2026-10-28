package dev.gensomechanic.mining.client;

import com.simibubi.create.AllBlocks;
import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.client.jei.MiningDisplay;
import dev.gensomechanic.mining.client.jei.VeinIngredient;
import dev.gensomechanic.mining.content.miner.MiningModifiers;
import dev.gensomechanic.mining.content.miner.MiningRecipe;
import dev.gensomechanic.mining.content.vein.VeinCondition;
import dev.gensomechanic.mining.network.MiningNetwork;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Main-menu integration check; does not operate UI or enter a world. */
@EventBusSubscriber(modid=GensoMining.MOD_ID,value=Dist.CLIENT)
public final class BackgroundClientChecks {
    private static boolean done,reloading;
    private static volatile boolean ready;
    private static Map<String,List<String>> before;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(done || !Boolean.getBoolean("gensomining.backgroundClientCheck") || reloading && !ready) return;
        var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null || mc.getModelManager().getMissingModel()==null) return;
        try {
            checkCatalogueAndJei();
            checkGroupedOutputs();
            var snapshot=nativeSnapshot();
            require(mc.getBlockRenderer().getBlockModelShaper().getParticleIcon(Blocks.STONE.defaultBlockState())
                    .contents().name().equals(ResourceLocation.withDefaultNamespace("block/stone")),"Stone rubble particle material missing");
            if(!reloading) {
                before=snapshot; reloading=true;
                GensoMining.LOGGER.info("BACKGROUND CLIENT initial catalogue/network/JEI/resource checks passed; requesting resource reload");
                mc.reloadResourcePacks().whenComplete((unused,error)->{
                    if(error==null) ready=true;
                    else { done=true;GensoMining.LOGGER.error("BACKGROUND CLIENT FAIL: resource reload",error);mc.stop(); }
                });
                return;
            }
            require(before.equals(snapshot),"Native Create model/UV/material/layer changed after resource reload");
            GensoMining.LOGGER.info("BACKGROUND CLIENT PASS: separate vein/background catalogues, bounded network codec, JEI names/conditions/probability, grouped outputs with original base/bonus identities, catalogue replacement/clear, stone particle material, native Create models/UV/layers before and after resource reload; no in-game visual acceptance");
        } catch(Throwable failure) { GensoMining.LOGGER.error("BACKGROUND CLIENT FAIL",failure); }
        done=true;mc.stop();
    }

    private static void checkCatalogueAndJei() {
        var actualId=GensoMining.asResource("client_check_actual");
        var backgroundId=GensoMining.asResource("rock_layer");
        var overworld=ResourceLocation.withDefaultNamespace("overworld");
        List<List<VeinCondition>> emptyFilters=List.of(List.of(),List.of(),List.of(),List.of());
        var actual=new MiningNetwork.Entry(actualId,Component.literal("Actual vein"),ResourceLocation.withDefaultNamespace("iron_ore"),null,emptyFilters);
        var background=new MiningNetwork.Entry(backgroundId,Component.translatable("rock_background.gensomining.rock_layer"),ResourceLocation.withDefaultNamespace("stone"),null,
                List.of(List.of(new VeinCondition("minecraft:overworld",List.of(overworld))),List.of(),List.of(),List.of()),0x777777,
                List.of(Component.translatable("jei.gensomining.background.priority",0)));
        var catalog=new MiningNetwork.Catalog(List.of(actual),"background-client-check",Map.of(overworld,"fixture"),List.of(background));
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            MiningNetwork.Catalog.CODEC.encode(buffer,catalog);
            var decoded=MiningNetwork.Catalog.CODEC.decode(buffer);
            require(decoded.equals(catalog) && buffer.readableBytes()==0,"Catalogue roundtrip lost a background or layout");
            MiningNetwork.catalogueReceiver.accept(decoded);
            require(ScannerClient.allEntries().equals(List.of(actual)) && ScannerClient.entries().equals(List.of(actual)),"Background leaked into scanner directory");
            require(ScannerClient.entry(backgroundId)==null && ScannerClient.miningEntry(backgroundId).equals(background),"Background lookup isolation failed");
            require(ScannerClient.backgroundEntries().equals(List.of(background)) && ScannerClient.miningEntries().size()==2,"Combined JEI directory missing background");
            var ingredient=new VeinIngredient(backgroundId);
            require(ingredient.name().equals(background.name()) && ingredient.icon().equals(background.icon()),"JEI background name/icon mismatch");
            require(new VeinIngredient.Helper().isIngredientOnServer(ingredient),"JEI rejects background ingredient");
            var tooltip=new VeinIngredient.Renderer().getTooltip(ingredient,TooltipFlag.NORMAL);
            require(tooltip.stream().noneMatch(line->line.getString().equals(Component.translatable("jei.gensomining.background.no_vein").getString())
                    || line.getString().equals(Component.translatable("jei.gensomining.background.capacity",4).getString())),"JEI retained hidden background explanations");
            for(String key:List.of("dimension_allow","dimension_deny","biome_allow","biome_deny"))
                require(tooltip.stream().anyMatch(line->line.getString().equals(Component.translatable("jei.gensomining.generation."+key).getString())),"JEI missing condition row "+key);
            var recipe=new MiningRecipe(new MiningRecipe.Definition(backgroundId,Optional.of(ResourceLocation.withDefaultNamespace("stone")),8,6400,
                    List.of(new MiningModifiers.Output("stone",new ItemStack(Items.COBBLESTONE,2),1,false),new MiningModifiers.Output("andesite",new ItemStack(Items.ANDESITE),.1,false)),MiningModifiers.Choices.EMPTY,List.of()));
            var pages=MiningDisplay.pages(new RecipeHolder<>(GensoMining.asResource("client_check_background"),recipe));
            require(pages.size()==1 && pages.getFirst().veinName().equals(background.name()) && pages.getFirst().veinIcon().equals(background.icon()),"JEI background page not resolved");
            require(pages.getFirst().operation().outputs().get(1).chance()==.1,"JEI lost probabilistic byproduct");
            MiningNetwork.catalogueReceiver.accept(new MiningNetwork.Catalog(List.of(actual)));
            require(ScannerClient.backgroundEntries().isEmpty() && ScannerClient.miningEntry(backgroundId)==null,"Removed background persisted after catalogue replacement");
            buffer.clear();buffer.writeUtf("");buffer.writeVarInt(0);buffer.writeVarInt(-1);
            boolean rejected=false;
            try { MiningNetwork.Catalog.CODEC.decode(buffer); } catch(IllegalArgumentException expected) { rejected=true; }
            require(rejected,"Negative background catalogue size was accepted");
            boolean duplicateRejected=false;
            try { new MiningNetwork.Catalog(List.of(actual),"",Map.of(),List.of(actual)); } catch(IllegalArgumentException expected) { duplicateRejected=true; }
            require(duplicateRejected,"Duplicate vein/background identity was accepted");
        } finally {
            buffer.release();MiningNetwork.catalogueReceiver.accept(new MiningNetwork.Catalog(List.of()));
        }
        require(ScannerClient.miningEntries().isEmpty() && ScannerClient.entries().isEmpty(),"Catalogue clear retained target entries");
    }

    private static void checkGroupedOutputs() {
        var base=List.of(new MiningModifiers.Output("resource",new ItemStack(Items.RAW_COPPER,2),.8,false),
                new MiningModifiers.Output("stone",new ItemStack(Items.COBBLESTONE,2),1,false));
        var bonus=List.of(new MiningModifiers.Output("resource_bonus",new ItemStack(Items.RAW_COPPER,2),.8,false),
                new MiningModifiers.Output("gravel_bonus",new ItemStack(Items.GRAVEL,2),1,false));
        var explosive=new MiningModifiers.Modifier("crude_explosive",0,null,
                new MiningModifiers.ItemInput(net.minecraft.world.item.crafting.Ingredient.of(Items.TNT),1),
                new MiningModifiers.Effects(1,1,0,List.of(),bonus));
        var recipe=new MiningRecipe(new MiningRecipe.Definition(GensoMining.asResource("copper_vein"),Optional.empty(),16,3200,
                base,new MiningModifiers.Choices(List.of(),List.of(explosive)),List.of()));
        var operation=recipe.modifiers().operation("","crude_explosive");
        var page=new MiningDisplay(new RecipeHolder<>(GensoMining.asResource("grouping_fixture"),recipe),operation);
        var outputs=page.orderedOutputs();
        require(outputs.stream().map(v->v.output().stack().getItem()).toList().equals(List.of(Items.RAW_COPPER,Items.RAW_COPPER,Items.COBBLESTONE,Items.GRAVEL)),"Copper/bonus/cobble/gravel display order changed");
        require(outputs.stream().map(v->v.originalIndex()<base.size()).toList().equals(List.of(true,false,true,false)),"Sorting lost base/additional tooltip identity");
        require(outputs.stream().allMatch(v->v.output().stack().getCount()==2),"Grouping merged item counts");
        require(outputs.get(0).output().chance()==.8 && outputs.get(1).output().chance()==.8,"Grouping changed probability");
        require(operation.outputs().stream().map(v->v.stack().getItem()).toList().equals(List.of(Items.RAW_COPPER,Items.COBBLESTONE,Items.RAW_COPPER,Items.GRAVEL)),"JEI sorting mutated production order");
    }

    private static Map<String,List<String>> nativeSnapshot() {
        var result=new TreeMap<String,List<String>>();
        for(BlockState state:List.of(AllBlocks.DEPOT.getDefaultState(),AllBlocks.ANDESITE_FUNNEL.getDefaultState(),AllBlocks.SHAFT.getDefaultState())) {
            // The shaft's blockstate model is intentionally empty; its geometry is a dynamic partial.
            var model=state.is(AllBlocks.SHAFT.get()) ? com.simibubi.create.AllPartialModels.SHAFT.get()
                    : Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            require(model!=Minecraft.getInstance().getModelManager().getMissingModel(),"Missing native model "+state);
            var quads=new ArrayList<BakedQuad>(model.getQuads(state,null,RandomSource.create(0),ModelData.EMPTY,null));
            for(Direction face:Direction.values()) quads.addAll(model.getQuads(state,face,RandomSource.create(0),ModelData.EMPTY,null));
            require(!quads.isEmpty(),"Empty native model "+state);
            List<String> lines=new ArrayList<>();
            for(var quad:quads) {
                var sprite=quad.getSprite();require(!sprite.contents().name().getPath().equals("missingno"),"Missing native material");
                var vertices=quad.getVertices();StringBuilder line=new StringBuilder(quad.getDirection()+":"+sprite.contents().name());
                for(int vertex=0;vertex<4;vertex++) {
                    for(int axis=0;axis<3;axis++) line.append(':').append(vertices[vertex*8+axis]);
                    double u=(Float.intBitsToFloat(vertices[vertex*8+4])-sprite.getU0())/(sprite.getU1()-sprite.getU0());
                    double v=(Float.intBitsToFloat(vertices[vertex*8+5])-sprite.getV0())/(sprite.getV1()-sprite.getV0());
                    line.append(':').append(Math.round(u*1000)).append(',').append(Math.round(v*1000));
                }
                lines.add(line.toString());
            }
            int geometryCount=lines.size();
            model.getRenderTypes(state,RandomSource.create(0),ModelData.EMPTY).forEach(layer->lines.add("layer:"+layer));
            require(lines.size()>geometryCount,"Native render layers absent");
            Collections.sort(lines);result.put(state.toString(),List.copyOf(lines));
        }
        return result;
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
