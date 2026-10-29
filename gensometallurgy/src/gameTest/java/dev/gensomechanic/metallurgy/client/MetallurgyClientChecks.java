package dev.gensomechanic.metallurgy.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.simibubi.create.AllBlocks;
import dev.gensomechanic.metallurgy.*;
import dev.gensomechanic.metallurgy.client.metallurgy.MetallurgyModels;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/** Creates an isolated local world, then exercises real viewer and Ponder execution paths. */
@EventBusSubscriber(modid=GensoMetallurgy.MOD_ID, value=Dist.CLIENT)
public final class MetallurgyClientChecks {
    private static boolean started, done, inspected, reloaded;
    private static int ticks, sceneIndex, sceneTicks, nextCapture;
    private static List<PonderScene> scenes;
    private static CheckPonderUI ponder;
    private static Map<String,List<String>> nativeBefore;
    private static CompletableFuture<Void> reload;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (done || !Boolean.getBoolean("gensometallurgy.clientCheck")) return;
        var mc=Minecraft.getInstance();
        if (mc.getOverlay()!=null) return;
        try {
            if (!started) {
                started=true;
                var rules=new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                rules.getRule(GameRules.RULE_RANDOMTICKING).set(0,null);
                rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);
                mc.createWorldOpenFlows().createFreshLevel("metallurgy-check-"+System.currentTimeMillis(),
                        new LevelSettings("Metallurgy verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1010,false,false), r->r.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
                return;
            }
            require(++ticks<3600,"Client verification timed out");
            if (mc.level==null || mc.player==null || ticks<30) return;
            if (ModList.get().isLoaded("jei") && !MetallurgyJeiChecks.ready()) return;
            if (reload!=null && !reload.isDone()) return;
            if (reload!=null) { reload.join();reload=null; }
            if (!inspected) {
                resources();
                var current=nativeSnapshot();
                if (nativeBefore==null) nativeBefore=current;
                else require(nativeBefore.equals(current),"Native Create geometry/material/UV/layers changed on reload");
                if (ModList.get().isLoaded("jade") && !MetallurgyJadeClientChecks.advance()) return;
                if (ModList.get().isLoaded("jei")) MetallurgyJeiChecks.verify(reloaded);
                scenes=PonderIndex.getSceneAccess().compile(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.getId());
                require(scenes.size()==4,"Expected all four registered Ponder scenes");
                require(scenes.stream().allMatch(s->s.getNamespace().equals(GensoMetallurgy.MOD_ID)),"Old Ponder namespace");
                sceneIndex=0;ponder=null;inspected=true;
            }
            if (!playPonder()) return;
            if (!reloaded) {
                reloaded=true;inspected=false;scenes=null;ponder=null;
                GensoMetallurgy.LOGGER.info("METALLURGY CLIENT initial checks passed; reloading resources");
                reload=mc.reloadResourcePacks();return;
            }
            done=true;
            GensoMetallurgy.LOGGER.info("METALLURGY CLIENT PASS: isolated world, all four Ponder scenes compiled, ticked to completion and rendered before/after reload; actual JEI layouts/limits when installed; Jade shared-capacity client views when installed; native Create model/UV/layer comparison; not manual visual acceptance");
            mc.stop();
        } catch(Throwable failure) {
            done=true;GensoMetallurgy.LOGGER.error("METALLURGY CLIENT FAIL",failure);mc.stop();
        }
    }

    private static boolean playPonder() throws Exception {
        if (sceneIndex==scenes.size()) return true;
        var scene=scenes.get(sceneIndex);
        if (ponder==null) {
            ponder=new CheckPonderUI(scene);ponder.init(Minecraft.getInstance(),960,540);
            scene.begin();sceneTicks=0;nextCapture=Math.max(1,scene.getTotalTime()/3);
            require(scene.getTotalTime()>0,"Empty Ponder schedule: "+scene.getId());
        }
        // Accelerate scheduling only; every normal tick/instruction and BE tick still executes.
        for (int step=0;step<20 && !scene.isFinished();step++) {
            ponder.tick();require(++sceneTicks<10000,"Ponder schedule did not finish: "+scene.getId());
        }
        if (scene.getCurrentTime()>=nextCapture || scene.isFinished()) {
            var ui=ponder;
            capture("ponder-"+sceneIndex+"-"+scene.getCurrentTime()+"-"+reloaded,960,540,g->ui.render(g,-1,-1,0));
            nextCapture+=Math.max(1,scene.getTotalTime()/3);
        }
        if (scene.isFinished()) {
            GensoMetallurgy.LOGGER.info("METALLURGY PONDER PASS {} ticks={} reload={}",scene.getId(),sceneTicks,reloaded);
            ponder.removed();ponder=null;sceneIndex++;
        }
        return false;
    }

    private static void resources() {
        var mc=Minecraft.getInstance();
        for (var block:List.of(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE,MetallurgyBlocks.CASTING_TABLE,AllBlocks.FLUID_TANK,AllBlocks.BASIN)) {
            require(mc.getItemRenderer().getModel(block.asStack(),mc.level,null,0)!=mc.getModelManager().getMissingModel(),"Missing item model "+block.getId());
            for (var state:block.get().getStateDefinition().getPossibleStates()) {
                var model=mc.getBlockRenderer().getBlockModel(state);
                require(model!=mc.getModelManager().getMissingModel(),"Missing state "+state);
                require(!model.getParticleIcon().contents().name().getPath().equals("missingno"),"Missing particle "+state);
            }
        }
        require(MetallurgyModels.INDUSTRIAL_BLAST_FURNACE_GAUGE.get()!=null,"Gauge not baked");
        require(MetallurgyModels.INDUSTRIAL_BLAST_FURNACE_GAUGE_DIAL.get()!=null,"Gauge dial not baked");
        var atlas=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        for (var metal:GensoFluids.MoltenMetal.values()) {
            var fluid=GensoFluids.get(metal).orElseThrow();
            var extension=IClientFluidTypeExtensions.of(fluid.getSource());
            for (var texture:List.of(extension.getStillTexture(),extension.getFlowingTexture())) {
                var sprite=atlas.apply(texture);
                require(sprite.contents().name().equals(texture),"Missing fluid atlas source "+texture);
                require(sprite.contents().getUniqueFrames().count()>1,"Fluid animation absent "+texture);
            }
            require(mc.getItemRenderer().getModel(new ItemStack(fluid.getBucket().orElseThrow()),mc.level,null,0)!=mc.getModelManager().getMissingModel(),"Missing bucket "+metal);
        }
    }

    private static Map<String,List<String>> nativeSnapshot() {
        Map<String,List<String>> result=new TreeMap<>();
        var mc=Minecraft.getInstance();
        for (var state:List.of(AllBlocks.FLUID_TANK.getDefaultState(),AllBlocks.BASIN.getDefaultState(),AllBlocks.BLAZE_BURNER.getDefaultState())) {
            var model=mc.getBlockRenderer().getBlockModel(state);
            var quads=new ArrayList<BakedQuad>(model.getQuads(state,null,RandomSource.create(0),ModelData.EMPTY,null));
            for (Direction side:Direction.values()) quads.addAll(model.getQuads(state,side,RandomSource.create(0),ModelData.EMPTY,null));
            require(!quads.isEmpty(),"Empty native model "+state);
            List<String> lines=new ArrayList<>();
            for (var q:quads) {
                var sprite=q.getSprite();require(!sprite.contents().name().getPath().equals("missingno"),"Native material missing");
                var v=q.getVertices();StringBuilder line=new StringBuilder(q.getDirection()+":"+sprite.contents().name());
                for (int i=0;i<4;i++) {
                    for(int axis=0;axis<3;axis++)line.append(':').append(v[i*8+axis]);
                    double u=(Float.intBitsToFloat(v[i*8+4])-sprite.getU0())/(sprite.getU1()-sprite.getU0());
                    double w=(Float.intBitsToFloat(v[i*8+5])-sprite.getV0())/(sprite.getV1()-sprite.getV0());
                    line.append(':').append(Math.round(u*1000)).append(',').append(Math.round(w*1000));
                }
                lines.add(line.toString());
            }
            model.getRenderTypes(state,RandomSource.create(0),ModelData.EMPTY).forEach(layer->lines.add("layer:"+layer));
            Collections.sort(lines);result.put(state.toString(),List.copyOf(lines));
        }
        return result;
    }

    static void capture(String name,int width,int height,Consumer<GuiGraphics> draw) throws Exception {
        var mc=Minecraft.getInstance();var target=new TextureTarget(width,height,true,Minecraft.ON_OSX);
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        var view=RenderSystem.getModelViewStack();view.pushMatrix();
        try {
            target.setClearColor(.13f,.13f,.13f,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,width,height,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            view.identity().translate(0,0,-11000);RenderSystem.applyModelViewMatrix();
            var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());draw.accept(g);g.flush();
            var path=mc.gameDirectory.toPath().resolve("../../build/verification/client/"+name+".png").normalize();
            Files.createDirectories(path.getParent());try(var image=Screenshot.takeScreenshot(target)) {
                Set<Integer> colors = new HashSet<>();
                for (int y=0;y<height;y+=7) for (int x=0;x<width;x+=7) colors.add(image.getPixelRGBA(x,y));
                require(colors.size()>16,"Captured frame is blank: "+name);
                image.writeToFile(path);
            }
        } finally {
            view.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);
            target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);
        }
    }
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static final class CheckPonderUI extends PonderUI {
        CheckPonderUI(PonderScene scene){super(List.of(scene));}
        @Override protected void renderBlurredBackground(float partialTick) {
            // Screen's world-background blur rebinds Minecraft's main framebuffer.
            // Keep this test's offscreen target; Ponder scene/widgets still render normally.
        }
    }
}
