package dev.gensomechanic.metallurgy.client;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import dev.gensomechanic.metallurgy.GensoMetallurgy;
import dev.gensomechanic.metallurgy.client.jei.MetallurgyJeiPlugin;
import dev.gensomechanic.metallurgy.client.jei.category.animations.AnimatedIndustrialBlastFurnace;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlock;
import dev.gensomechanic.metallurgy.content.metallurgy.MetallurgyRecipeTypes;
import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Optional JEI test companion, discovered only when JEI is installed. */
@JeiPlugin
public final class MetallurgyJeiChecks implements IModPlugin {
    private static IJeiRuntime runtime;
    @Override public ResourceLocation getPluginUid(){return GensoMetallurgy.asResource("client_checks");}
    @Override public void onRuntimeAvailable(IJeiRuntime value){runtime=value;}
    @Override public void onRuntimeUnavailable(){runtime=null;}
    static boolean ready(){return runtime!=null;}

    static void verify(boolean reloaded) throws Exception {
        for(int y=0;y<3;y++)MetallurgyClientChecks.require(!AnimatedIndustrialBlastFurnace.furnaceState(y).getValue(IndustrialBlastFurnaceBlock.WINDOW),"JEI opened furnace windows");
        var manager=runtime.getRecipeManager();
        var types=List.of(MetallurgyJeiPlugin.BLAST_FURNACE_SMELTING,MetallurgyJeiPlugin.BLAST_FURNACE_FUSING,MetallurgyJeiPlugin.CASTING);
        for(var type:types) {
            var actual=manager.createRecipeLookup(type).get().toList();
            if (Boolean.getBoolean("gensometallurgy.noRecipes"))
                MetallurgyClientChecks.require(actual.isEmpty(), "JEI must retain an empty metallurgy recipe directory");
            MetallurgyClientChecks.require(actual.stream().noneMatch(r->r.id().getNamespace().equals(GensoMetallurgy.MOD_ID)),"Production recipe unexpectedly registered");
            MetallurgyClientChecks.require(actual.stream().allMatch(r->r.id().getNamespace().equals("gensometallurgy_test")),"Unexpected recipe in isolated test directory");
        }
        var recipes=List.of(recipe(MetallurgyRecipeTypes.BLAST_FURNACE_SMELTING,false),recipe(MetallurgyRecipeTypes.BLAST_FURNACE_FUSING,false),recipe(MetallurgyRecipeTypes.CASTING,false),recipe(MetallurgyRecipeTypes.BLAST_FURNACE_SMELTING,true));
        for(String locale:List.of("zh_cn","en_us")) {
            var mc=Minecraft.getInstance();mc.getLanguageManager().setSelected(locale);mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
            MetallurgyClientChecks.capture("jei-"+locale+"-"+reloaded,800,300,g->{
                for(int i=0;i<recipes.size();i++) {
                    var type=types.get(i==3?0:i);var category=manager.getRecipeCategory(type);
                    var layout=manager.createRecipeLayoutDrawable(category,recipes.get(i),runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
                    var slots=layout.getRecipeSlotsView().getSlotViews();
                    MetallurgyClientChecks.require(slots.size()>1,"Actual layout has no inputs/outputs");
                    if(i==3) {
                        long ioSlots=slots.stream().filter(s->s.getRole()==mezz.jei.api.recipe.RecipeIngredientRole.INPUT
                                || s.getRole()==mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT).count();
                        MetallurgyClientChecks.require(ioSlots==180 && slots.size()==182,
                                "Maximum inputs/outputs or superheating catalysts lost: "+ioSlots+"/"+slots.size());
                    }
                    int x=10+i*195,y=35;layout.setPosition(x,y);g.drawString(mc.font,category.getTitle(),x,y-15,-1,false);layout.drawRecipe(g,-1,-1);
                }
            });
        }
        GensoMetallurgy.LOGGER.info("METALLURGY JEI PASS: three actual layouts, 180-slot boundary, empty production directory, two languages; reload={}",reloaded);
    }

    private static RecipeHolder<BasinRecipe> recipe(MetallurgyRecipeTypes type,boolean limits) {
        var mc=Minecraft.getInstance();var json=new JsonObject();var input=new JsonArray();var output=new JsonArray();
        if(limits) {
            BuiltInRegistries.ITEM.stream().filter(i->i!=Items.AIR).limit(81).forEach(item->{
                var a=new JsonObject();a.addProperty("item",BuiltInRegistries.ITEM.getKey(item).toString());input.add(a);
                var b=new JsonObject();b.addProperty("id",BuiltInRegistries.ITEM.getKey(item).toString());output.add(b);
            });
            var fluids=BuiltInRegistries.FLUID.stream().filter(f->!f.defaultFluidState().isEmpty()).limit(9).toList();
            for(var fluid:fluids) {
                var a=new JsonObject();a.addProperty("type","neoforge:single");a.addProperty("fluid",BuiltInRegistries.FLUID.getKey(fluid).toString());a.addProperty("amount",10);input.add(a);
                var b=new JsonObject();b.addProperty("id",BuiltInRegistries.FLUID.getKey(fluid).toString());b.addProperty("amount",10);output.add(b);
            }
        } else if(type==MetallurgyRecipeTypes.CASTING) {
            input.add(JsonParser.parseString("{\"type\":\"neoforge:single\",\"fluid\":\"gensometallurgy:molten_copper\",\"amount\":90}"));
            output.add(JsonParser.parseString("{\"id\":\"minecraft:copper_ingot\"}"));
        } else if(type==MetallurgyRecipeTypes.BLAST_FURNACE_FUSING) {
            input.add(JsonParser.parseString("{\"type\":\"neoforge:single\",\"fluid\":\"gensometallurgy:molten_copper\",\"amount\":10}"));
            input.add(JsonParser.parseString("{\"type\":\"neoforge:single\",\"fluid\":\"gensometallurgy:molten_zinc\",\"amount\":10}"));
            output.add(JsonParser.parseString("{\"id\":\"gensometallurgy:molten_brass\",\"amount\":20}"));
        } else {
            input.add(JsonParser.parseString("{\"item\":\"minecraft:copper_ingot\"}"));
            output.add(JsonParser.parseString("{\"id\":\"gensometallurgy:molten_copper\",\"amount\":90}"));
        }
        json.add("ingredients",input);json.add("results",output);json.addProperty("processing_time",100);
        if(type!=MetallurgyRecipeTypes.CASTING) json.addProperty("heat_requirement",limits?"superheated":"heated");
        var serializer=type.<net.minecraft.world.item.crafting.RecipeSerializer<?>>getSerializer();
        var result=serializer.codec().codec().parse(mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE),json).getOrThrow();
        return new RecipeHolder<>(GensoMetallurgy.asResource("layout_"+type.name().toLowerCase(Locale.ROOT)+(limits?"_limits":"")),(BasinRecipe)result);
    }
}
