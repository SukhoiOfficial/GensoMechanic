package dev.gensomechanic.mining.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.depot.DepotRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.content.miner.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Isolated render-data checks; no UI automation, world editing or claim of manual visual acceptance. */
@EventBusSubscriber(modid = GensoMining.MOD_ID, value = Dist.CLIENT)
public final class MiningClientChecks {
    private static boolean done, reloadRequested;
    private static volatile boolean reloadReady;
    private static Map<String, List<String>> nativeBefore;
    private static BakedModel previousFunnel;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (done || !Boolean.getBoolean("gensomining.verificationExit") || reloadRequested && !reloadReady) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.getModelManager().getMissingModel() == null || !GensoPartialModels.ready()) return;
        try {
            Map<String, List<String>> initial = nativeSnapshot();
            if (nativeBefore != null) require(nativeBefore.equals(initial), "Native Create geometry/material/local UV/layer changed across reload");
            checkMinerModels();
            int capturedVertices = checkDepotItemRendering();
            require(initial.equals(nativeSnapshot()), "Miner rendering mutated a shared Create model");
            if (!reloadRequested) {
                nativeBefore = initial;
                previousFunnel = GensoPartialModels.INPUT_FUNNEL_OPEN.get();
                reloadRequested = true;
                GensoMining.LOGGER.info("MINING CLIENT initial checks passed: {} item vertices; reloading resources", capturedVertices);
                mc.reloadResourcePacks().whenComplete((unused, error) -> {
                    if (error == null) reloadReady = true;
                    else { done = true; GensoMining.LOGGER.error("MINING CLIENT FAIL: reload", error); mc.stop(); }
                });
                return;
            }
            require(previousFunnel != GensoPartialModels.INPUT_FUNNEL_OPEN.get(), "Partial model retained stale bake after reload");
            GensoMining.LOGGER.info("MINING CLIENT PASS: funnel geometry/UV/independent phases, complete item composition, actual Depot item vertices and stacking in four orientations, native Create model/material/local UV/render layers before and after reload; not manual visual acceptance");
        } catch (Throwable error) {
            GensoMining.LOGGER.error("MINING CLIENT FAIL", error);
        }
        done = true;
        mc.stop();
    }

    private static List<BakedQuad> quads(BakedModel model, BlockState state) {
        require(model != null && model != Minecraft.getInstance().getModelManager().getMissingModel(), "Missing model");
        var data = net.createmod.ponder.render.VirtualRenderHelper.VIRTUAL_DATA;
        List<BakedQuad> result = new ArrayList<>(model.getQuads(state, null, RandomSource.create(0), data, null));
        for (Direction side : Direction.values()) result.addAll(model.getQuads(state, side, RandomSource.create(0), data, null));
        for (BakedQuad face : result) require(!face.getSprite().contents().name().getPath().equals("missingno"), "Missing sprite");
        return result;
    }
    private static List<BakedQuad> quads(PartialModel model) {
        return quads(model.get(), MiningContent.MECHANICAL_MINER.getDefaultState());
    }
    private static String geometry(BakedQuad q) {
        int[] data = q.getVertices();
        List<String> points = new ArrayList<>();
        for (int i = 0; i < 4; i++) points.add(Math.round(Float.intBitsToFloat(data[i * 8]) * 10000) + ","
                + Math.round(Float.intBitsToFloat(data[i * 8 + 1]) * 10000) + "," + Math.round(Float.intBitsToFloat(data[i * 8 + 2]) * 10000));
        Collections.sort(points);
        return q.getDirection() + ":" + points;
    }
    private static String faceKey(BakedQuad q) {
        int[] data = q.getVertices(); var sprite = q.getSprite();
        StringBuilder key = new StringBuilder(geometry(q)).append(':').append(sprite.contents().name());
        for (int i = 0; i < 4; i++) {
            float u = (Float.intBitsToFloat(data[i * 8 + 4]) - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
            float v = (Float.intBitsToFloat(data[i * 8 + 5]) - sprite.getV0()) / (sprite.getV1() - sprite.getV0());
            key.append(':').append(Math.round(u * 1000)).append(',').append(Math.round(v * 1000));
        }
        return key.toString();
    }
    private static List<String> signature(List<BakedQuad> faces) { return faces.stream().map(MiningClientChecks::faceKey).sorted().toList(); }
    private static Map<String, List<String>> nativeSnapshot() {
        Map<String, List<String>> result = new TreeMap<>();
        for (BlockState state : List.of(AllBlocks.DEPOT.getDefaultState(), AllBlocks.ANDESITE_FUNNEL.getDefaultState(),
                AllBlocks.ANDESITE_CASING.getDefaultState(), AllBlocks.SHAFT.getDefaultState())) {
            var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            var faces = quads(model, state);
            require(!faces.isEmpty(), "Native model empty: " + state);
            List<String> snapshot = new ArrayList<>(signature(faces));
            model.getRenderTypes(state, RandomSource.create(0), net.neoforged.neoforge.client.model.data.ModelData.EMPTY)
                    .forEach(type -> snapshot.add("layer:" + type));
            require(snapshot.size() > faces.size(), "Native render layer absent");
            result.put(state.toString(), List.copyOf(snapshot));
        }
        return result;
    }

    private static void checkMinerModels() {
        var state = MiningContent.MECHANICAL_MINER.getDefaultState();
        var shell = quads(Minecraft.getInstance().getBlockRenderer().getBlockModel(state), state);
        var complete = new ArrayList<>(shell);
        for (var part : List.of(GensoPartialModels.DRILL, GensoPartialModels.SHAFT, GensoPartialModels.FAN,
                GensoPartialModels.INPUT_TRAY, GensoPartialModels.OUTPUT_TRAY,
                GensoPartialModels.INPUT_FUNNEL_CLOSED, GensoPartialModels.OUTPUT_FUNNEL_CLOSED)) complete.addAll(quads(part));
        var item = Minecraft.getInstance().getItemRenderer().getModel(MiningContent.MECHANICAL_MINER.asStack(), null, null, 0);
        require(signature(complete).equals(signature(quads(item, null))), "World components do not reconstruct the supplied complete item geometry/materials");
        for (var pair : List.of(List.of(GensoPartialModels.INPUT_FUNNEL_CLOSED, GensoPartialModels.INPUT_FUNNEL_OPEN),
                List.of(GensoPartialModels.OUTPUT_FUNNEL_CLOSED, GensoPartialModels.OUTPUT_FUNNEL_OPEN))) {
            var closed = quads(pair.get(0)); var opened = quads(pair.get(1));
            require(closed.size() == 2 && opened.size() == 2, "Funnel partial must contain only two faces");
            require(closed.stream().map(MiningClientChecks::geometry).sorted().toList()
                    .equals(opened.stream().map(MiningClientChecks::geometry).sorted().toList()), "Texture switch moved the funnel");
            for (var face : closed) {
                require(face.getSprite().contents().name().toString().equals("create:block/funnel/andesite_funnel"), "Closed face lost native funnel texture");
                require(shell.stream().noneMatch(q -> geometry(q).equals(geometry(face))), "Closed face remains in static shell");
            }
            for (var face : opened) {
                require(face.getDirection().getAxis() == Direction.Axis.Z, "Wrong switched funnel face");
                require(face.getSprite().contents().name().equals(GensoMining.asResource("block/mechanical_miner_funnel")), "Open texture was not baked");
                var sprite = face.getSprite(); int[] vertices = face.getVertices();
                for (int i = 0; i < 4; i++) {
                    double u = (Float.intBitsToFloat(vertices[i * 8 + 4]) - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
                    double v = (Float.intBitsToFloat(vertices[i * 8 + 5]) - sprite.getV0()) / (sprite.getV1() - sprite.getV0());
                    require(Math.abs(u - .125) < .003 || Math.abs(u - .875) < .003, "Funnel U density changed");
                    require(Math.abs(v - .125) < .003 || Math.abs(v - .875) < .003, "Funnel V density changed");
                }
            }
        }
        for (int age = -1; age <= 60; age++) {
            Capture actual = new Capture();
            MechanicalMinerRenderer.renderFunnels(state, new PoseStack(), type -> actual, LightTexture.FULL_BRIGHT, age, age - 30);
            require(actual.points.size() == 16, "Funnel render duplicates or drops faces at age " + age);
            long newTextureQuads = actual.uvs.stream().filter(uv -> withinOpenSprite(uv[0], uv[1])).count();
            int expected = (age >= 0 && age < 20 ? 8 : 0) + (age >= 30 && age < 50 ? 8 : 0);
            require(newTextureQuads == expected, "Input/output funnel phases are not independent at " + age);
        }
    }
    private static boolean withinOpenSprite(float u, float v) {
        var sprite = quads(GensoPartialModels.INPUT_FUNNEL_OPEN).getFirst().getSprite();
        return u >= sprite.getU0() && u <= sprite.getU1() && v >= sprite.getV0() && v <= sprite.getV1();
    }

    private static int checkDepotItemRendering() {
        // Surface heights come from actual baked geometry; this is not the production anchor constant.
        double minerSurface = maxY(quads(GensoPartialModels.OUTPUT_TRAY));
        var depot = AllBlocks.DEPOT.getDefaultState();
        double depotSurface = maxY(quads(Minecraft.getInstance().getBlockRenderer().getBlockModel(depot), depot));
        require(Math.abs(minerSurface - .25) < 1e-6 && Math.abs(depotSurface - 13 / 16d) < 1e-6, "Unexpected actual tray/depot surface");
        int vertices = 0;
        for (var item : List.of(Items.ANDESITE, Items.COPPER_INGOT, Items.WATER_BUCKET)) {
            for (int count : item == Items.WATER_BUCKET ? new int[]{1} : new int[]{1, 3, 4, 15, 16, 63, 64}) {
                var stack = new ItemStack(item, count);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    var state = MiningContent.MECHANICAL_MINER.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
                    var miner = new MechanicalMinerBlockEntity(MiningContent.MINER_ENTITY.get(), new BlockPos(10, 64, 20), state);
                    double z = -9 / 16d;
                    Capture actual = new Capture(), nativeResult = new Capture();
                    PoseStack pose = new PoseStack();
                    pose.translate(.5, 0, .5); pose.mulPose(Axis.YP.rotationDegrees(-90 * MinerStructure.turns(facing))); pose.translate(-.5, 0, -.5);
                    MinerItemDisplay.render(miner, stack, true, z, pose, type -> actual, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    double distance = .5 - z;
                    Vec3 origin = new Vec3(.5 + facing.getStepX() * distance, 15 / 16d + minerSurface - depotSurface, .5 + facing.getStepZ() * distance);
                    PoseStack nativePose = new PoseStack(); nativePose.translate(origin.x, origin.y, origin.z);
                    DepotRenderer.renderItem(nativePose, type -> nativeResult, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                            stack, 0, new Random(0), Vec3.atLowerCornerOf(miner.getBlockPos()).add(origin), false);
                    require(!actual.points.isEmpty() && actual.points.size() == nativeResult.points.size(), "Native depot stack copy count differs: " + stack);
                    for (int i = 0; i < actual.points.size(); i++) {
                        require(actual.points.get(i).distance(nativeResult.points.get(i)) < .00001, "Depot final vertex placement/size differs: " + stack + "/" + facing);
                    }
                    vertices += actual.points.size();
                }
            }
        }
        return vertices;
    }
    private static double maxY(List<BakedQuad> faces) {
        double maximum = -Double.MAX_VALUE;
        for (var face : faces) for (int i = 0; i < 4; i++) maximum = Math.max(maximum, Float.intBitsToFloat(face.getVertices()[i * 8 + 1]));
        return maximum;
    }
    private static final class Capture implements VertexConsumer {
        final List<org.joml.Vector3f> points = new ArrayList<>();
        final List<float[]> uvs = new ArrayList<>();
        public VertexConsumer addVertex(float x, float y, float z) { points.add(new org.joml.Vector3f(x, y, z)); return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setUv(float u, float v) { uvs.add(new float[]{u, v}); return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
