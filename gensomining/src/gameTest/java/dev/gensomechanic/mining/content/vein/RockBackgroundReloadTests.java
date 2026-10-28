package dev.gensomechanic.mining.content.vein;

import dev.gensomechanic.mining.content.miner.MiningTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("gensomining_background")
@PrefixGameTestTemplate(false)
public final class RockBackgroundReloadTests {
    private static void write(Path file, String value) {
        try { Files.writeString(file, value); } catch (java.io.IOException error) { throw new RuntimeException(error); }
    }
    @GameTest(template = "empty", batch = "background_reload", timeoutTicks = 2400)
    public static void reloadChangesConditionsAtomicallyWithoutChangingVeinLayout(GameTestHelper h) throws Exception {
        var level = h.getLevel(); var server = level.getServer(); var pos = h.absolutePos(new BlockPos(1, 1, 1));
        var query = VeinManager.query(level); var layout = VeinManager.signature(level);
        var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        var pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("background_reload_fixture");
        var file = pack.resolve("data/gensomining/rock_backgrounds/rock_layer.json");
        Files.createDirectories(file.getParent());
        write(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"Isolated background reload test\"}}");
        String valid = "{\"schema_version\":1,\"name\":\"Fixture Rock Layer\",\"icon\":\"minecraft:stone\","
                + "\"conditions\":{\"dimensions\":{\"allow\":[\"minecraft:overworld\"]},\"min_y\":" + pos.getY()
                + ",\"max_y\":" + pos.getY() + "},\"priority\":10,\"capacity\":2}";
        write(file, valid);
        server.getPackRepository().reload();
        var enabled = new ArrayList<>(selected); enabled.add("file/background_reload_fixture");
        var result = server.reloadResources(enabled).thenComposeAsync(ignored -> {
            var target = MiningTarget.resolve(level, pos, Optional.empty());
            h.assertTrue(target != null && target.capacity() == 2 && MiningTarget.resolve(level, pos.above(), Optional.empty()) == null,
                    "Successful reload did not update background capacity/height");
            h.assertTrue(VeinManager.query(level) == query && VeinManager.signature(level).equals(layout), "Background edit invalidated vein layout/query");
            var snapshot = VeinManager.backgrounds(server);
            write(file, valid.replace("\"capacity\":2", "\"capacity\":0"));
            return server.reloadResources(enabled).handleAsync((value, error) -> {
                h.assertTrue(VeinManager.backgrounds(server) == snapshot && MiningTarget.resolve(level, pos, Optional.empty()).capacity() == 2,
                        "Failed reload published partial background rules");
                return null;
            }, server);
        }, server).thenComposeAsync(ignored -> {
            write(file, valid.replace("\"allow\":[\"minecraft:overworld\"]", "\"deny\":[\"minecraft:overworld\"]"));
            return server.reloadResources(enabled);
        }, server).thenRunAsync(() -> {
            h.assertTrue(MiningTarget.resolve(level, pos, Optional.empty()) == null, "Reload retained stale dimension condition");
            h.assertTrue(VeinManager.query(level) == query && VeinManager.signature(level).equals(layout), "Conditional source changed vein layout");
        }, server);
        var restored = result.handleAsync((ignored, error) -> server.reloadResources(selected).thenRun(() -> {
            if (error != null) throw new RuntimeException(error);
        }), server).thenCompose(future -> future);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(restored.isDone(), "Waiting for background reload sequence"))
                .thenExecute(() -> {
                    restored.join();
                    var target = MiningTarget.resolve(level, pos, Optional.empty());
                    h.assertTrue(target != null && target.capacity() == 4 && VeinManager.signature(level).equals(layout), "Original rules not restored");
                }).thenSucceed();
    }
}
