package dev.gensomechanic.mining.content.vein;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("gensomining_background")
@PrefixGameTestTemplate(false)
public final class ScannerBackgroundReportTests {
    static Optional<String> capacity(List<Component> report) {
        return report.stream().map(Component::getContents)
                .filter(c -> c instanceof TranslatableContents t && t.getKey().equals("scanner.gensomining.capacity"))
                .map(c -> ((Component) ((TranslatableContents) c).getArgs()[0]).getString()).findFirst();
    }

    @GameTest(template = "empty", batch = "background_report")
    public static void emptyChunkReportsEffectiveCapacityWithoutBecomingAVein(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(1, 1, 1));
        var scan = new VeinScan(new ChunkPos(origin), Optional.empty(), List.of());
        var report = ScannerReport.create(level, origin, scan, 0);
        h.assertTrue(capacity(report).orElseThrow().equals("0 / 4"), "Empty overworld omitted rock-layer capacity");
        var current = (TranslatableContents) report.get(1).getContents();
        h.assertTrue(((Component) current.getArgs()[0]).getContents() instanceof TranslatableContents name
                && name.getKey().equals("scanner.gensomining.none"), "Background was reported as a real vein");
        h.assertTrue(scan.current().isEmpty() && scan.traces().isEmpty(), "Report added background map traces");
        var nether = level.getServer().getLevel(Level.NETHER);
        h.assertTrue(capacity(ScannerReport.create(nether, origin, scan, 0)).orElseThrow().equals("0 / 4"), "Nether omitted rock-layer capacity");
        var end = level.getServer().getLevel(Level.END);
        h.assertTrue(capacity(ScannerReport.create(end, origin, scan, 0)).isEmpty(), "No matching background still reports capacity");
        h.succeed();
    }
}
