package dev.gensomechanic.mining.content.miner;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllFluids;
import dev.gensomechanic.mining.GensoMining;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.conditions.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("gensomining_lubricants")
@PrefixGameTestTemplate(false)
public final class LubricantConditionTests {
    private static final ResourceLocation OIL_TAG = ResourceLocation.parse("c:plantoil");

    @GameTest(template="empty")
    public static void conditionalCodecUsesPendingFluidTagsAndSyncsResolvedChoices(GameTestHelper h) throws Exception {
        JsonObject json;
        try(var reader=h.getLevel().getServer().getResourceManager().openAsReader(GensoMining.asResource("recipe/mining/rock_layer.json"))) {
            json=JsonParser.parseReader(reader).getAsJsonObject();
        }
        for(int state=0;state<3;state++) {
            int tagState=state;
            var context=new ICondition.IContext() {
                @Override @SuppressWarnings({"unchecked","rawtypes"})
                public <T> Map<ResourceLocation,Collection<Holder<T>>> getAllTags(ResourceKey<? extends Registry<T>> registry) {
                    if(!registry.equals(Registries.FLUID)||tagState==0)return Map.of();
                    return (Map)Map.of(OIL_TAG,tagState==1?List.of():List.of(AllFluids.HONEY.getSource().builtInRegistryHolder(),AllFluids.CHOCOLATE.getSource().builtInRegistryHolder()));
                }
            };
            var ops=new ConditionalOps<>(RegistryOps.create(JsonOps.INSTANCE,h.getLevel().registryAccess()),context);
            var recipe=MiningRecipe.CODEC.codec().parse(ops,json).getOrThrow();
            String selected=state==2?"plant_oil":"water";
            h.assertTrue(recipe.modifiers().lubricants().size()==1 && recipe.modifiers().lubricants().getFirst().id().equals(selected),"Pending fluid-tag snapshot did not select exactly one lubricant");
            var encoded=MiningRecipe.CODEC.codec().encodeStart(RegistryOps.create(JsonOps.INSTANCE,h.getLevel().registryAccess()),recipe).getOrThrow();
            h.assertTrue(!encoded.toString().contains("neoforge:conditions"),"Unresolved conditions leaked into recipe synchronization");
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try {
                MiningRecipes.SERIALIZER.get().streamCodec().encode(buffer,recipe);
                var received=MiningRecipes.SERIALIZER.get().streamCodec().decode(buffer);
                h.assertTrue(received.modifiers().lubricants().size()==1 && received.modifiers().lubricants().getFirst().id().equals(selected),"Receiver reevaluated server conditions without its context");
                h.assertTrue(buffer.readableBytes()==0 && received.fingerprint(h.getLevel().registryAccess()).equals(recipe.fingerprint(h.getLevel().registryAccess())),"Recipe fingerprint or packet roundtrip changed");
            } finally {buffer.release();}
        }
        h.succeed();
    }

    private static void checkRecipes(GameTestHelper h,boolean oil) {
        var server=h.getLevel().getServer();var recipes=MiningRecipes.forServer(server);
        h.assertTrue(recipes.size()==(net.neoforged.fml.ModList.get().isLoaded("biomesoplenty")?19:17),"Reload dropped a mining source");
        for(var holder:recipes.values()) {
            var recipe=holder.value();var choices=recipe.modifiers().lubricants();String expected=oil?"plant_oil":"water";
            h.assertTrue(choices.size()==1 && choices.getFirst().id().equals(expected),"Wrong active lubricant for "+holder.id());
            var lubricant=choices.getFirst();var operation=recipe.modifiers().operation(expected,"");
            h.assertTrue(lubricant.fluid().amount()==100 && operation.duration()==recipe.duration()*.75
                    && operation.stress()==recipe.stress()*.75,"Lubricant cost or effects changed");
            h.assertTrue(lubricant.accepts(new FluidStack(Fluids.WATER,100))!=oil,"Water remained active with plant oil");
            for(Fluid fluid:List.<Fluid>of(AllFluids.HONEY.getSource(),AllFluids.CHOCOLATE.getSource()))
                h.assertTrue(lubricant.accepts(new FluidStack(fluid,100))==oil,"Tag did not accept every provider fluid");
            Fluid selected=oil?AllFluids.HONEY.getSource():Fluids.WATER;
            h.assertTrue(recipe.modifiers().select(new FluidStack(selected,99),ItemStack.EMPTY).lubricant()==null
                    && recipe.modifiers().select(new FluidStack(selected,100),ItemStack.EMPTY).lubricantId().equals(expected),"Fluid amount check bypassed");
            h.assertTrue(lubricant.fluid().getFluids().length==(oil?2:1),"Display fluid list retained stale alternatives");
        }
    }
    private static void write(Path path,String json) {
        try {Files.writeString(path,json);}catch(java.io.IOException e){throw new RuntimeException(e);}
    }

    @GameTest(template="background_lab",timeoutTicks=2400)
    public static void liveReloadSwitchesAllSourcesAndMachineInputThenRestoresWater(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer();checkRecipes(h,false);
        var position=h.absolutePos(new BlockPos(3,2,3));
        h.assertTrue(MinerStructure.place(h.getLevel(),position,Direction.NORTH),"Miner fixture did not fit");
        var miner=(MechanicalMinerBlockEntity)h.getLevel().getBlockEntity(position);
        h.assertTrue(miner.fillLubricant(new FluidStack(Fluids.WATER,100),FluidAction.EXECUTE)==100,"Initial water input rejected");
        var selected=new ArrayList<>(server.getPackRepository().getSelectedIds());
        var pack=server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("lubricant_tag_fixture");
        var tag=pack.resolve("data/c/tags/fluid/plantoil.json");Files.createDirectories(tag.getParent());
        write(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":48,\"description\":\"Isolated lubricant tag test\"}}");
        write(tag,"{\"replace\":true,\"values\":[\"create:honey\",\"create:chocolate\"]}");
        server.getPackRepository().reload();var enabled=new ArrayList<>(selected);enabled.add("file/lubricant_tag_fixture");
        var switched=server.reloadResources(enabled).thenComposeAsync(ignored->{
            checkRecipes(h,true);
            h.assertTrue(miner.lubricantFluid().getAmount()==100,"Reload destroyed stored water");
            h.assertTrue(miner.fillLubricant(new FluidStack(Fluids.WATER,100),FluidAction.SIMULATE)==0,"New water input allowed after switch");
            h.assertTrue(miner.drainLubricant(100,FluidAction.EXECUTE).is(Fluids.WATER),"Old water could not be drained");
            h.assertTrue(miner.fillLubricant(new FluidStack((Fluid)AllFluids.HONEY.getSource(),100),FluidAction.EXECUTE)==100,"Plant oil tag input rejected");
            miner.drainLubricant(100,FluidAction.EXECUTE);
            write(tag,"{\"replace\":true,\"values\":[]}");
            return server.reloadResources(enabled);
        },server).thenRunAsync(()->checkRecipes(h,false),server);
        var restored=switched.handleAsync((ignored,error)->server.reloadResources(selected).thenRun(()->{
            if(error!=null)throw new RuntimeException(error);
        }),server).thenCompose(future->future);
        h.startSequence().thenWaitUntil(()->h.assertTrue(restored.isDone(),"Waiting for fluid-tag reload sequence"))
                .thenExecute(()->{restored.join();checkRecipes(h,false);}).thenSucceed();
    }
}
