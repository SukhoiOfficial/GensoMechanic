package dev.gensomechanic.mining.client;

import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.content.explosive.ExplosiveContent;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Runs at the main menu; observes resource loading, never operates the client UI. */
@EventBusSubscriber(modid = GensoMining.MOD_ID, value = Dist.CLIENT)
public final class ExplosiveClientChecks {
    private static boolean done, reloading;
    private static volatile boolean ready;
    private static Map<String, String> before;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (done || !Boolean.getBoolean("gensomining.explosiveClientCheck") || reloading && !ready) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.getModelManager().getMissingModel() == null) return;
        try {
            var snapshot = new TreeMap<String, String>();
            snapshot.putAll(faces("crude", ExplosiveContent.CRUDE_EXPLOSIVE.getDefaultState()));
            snapshot.putAll(faces("nitro", ExplosiveContent.NITRO_EXPLOSIVE.getDefaultState()));
            snapshot.putAll(faces("vanilla", Blocks.TNT.defaultBlockState()));
            for (var face : Direction.values()) {
                String common = face == Direction.DOWN ? "explosive_bottom" : "explosive_side";
                for (String kind : java.util.List.of("crude", "nitro")) {
                    String expected = "gensomining:block/explosives/" + (face == Direction.UP ? kind + "_explosive_top" : common);
                    require(expected.equals(snapshot.get(kind + ":" + face)), "Unexpected " + kind + " material on " + face);
                }
                require(snapshot.get("vanilla:" + face).startsWith("minecraft:block/tnt_"), "Vanilla TNT material was replaced");
            }
            for (var block : java.util.List.of(ExplosiveContent.CRUDE_EXPLOSIVE, ExplosiveContent.NITRO_EXPLOSIVE)) {
                var itemModel = mc.getItemRenderer().getModel(block.asStack(), null, null, 0);
                require(itemModel != mc.getModelManager().getMissingModel(), "Missing explosive item model");
                faces("unstable", block.getDefaultState().setValue(net.minecraft.world.level.block.TntBlock.UNSTABLE, true));
            }
            if (!reloading) {
                before = snapshot; reloading = true;
                GensoMining.LOGGER.info("EXPLOSIVE CLIENT initial resources passed; requesting resource reload");
                mc.reloadResourcePacks().whenComplete((unused, error) -> {
                    if (error == null) ready = true;
                    else { done = true; GensoMining.LOGGER.error("EXPLOSIVE CLIENT FAIL: resource reload", error); mc.stop(); }
                });
                return;
            }
            require(before.equals(snapshot), "Explosive or native TNT material changed after reload");
            GensoMining.LOGGER.info("EXPLOSIVE CLIENT PASS: both cube and item models, shared side/bottom, distinct top, native TNT and resource reload; no in-game visual acceptance");
        } catch (Throwable failure) { GensoMining.LOGGER.error("EXPLOSIVE CLIENT FAIL", failure); }
        done = true; mc.stop();
    }

    private static Map<String, String> faces(String name, BlockState state) {
        var mc = Minecraft.getInstance();
        var model = mc.getBlockRenderer().getBlockModel(state);
        require(model != mc.getModelManager().getMissingModel(), "Missing block model: " + name);
        var result = new TreeMap<String, String>();
        for (Direction face : Direction.values()) {
            var quads = model.getQuads(state, face, RandomSource.create(0));
            require(quads.size() == 1, "Incomplete cube: " + name + " " + face);
            result.put(name + ":" + face, quads.getFirst().getSprite().contents().name().toString());
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
