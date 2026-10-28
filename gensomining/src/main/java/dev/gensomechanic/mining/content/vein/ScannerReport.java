package dev.gensomechanic.mining.content.vein;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import dev.gensomechanic.mining.content.miner.MiningTarget;

import java.util.*;

/** Localizable components only: text is resolved in the receiving player's language. */
public final class ScannerReport {
    public static final int HIGHLIGHT_COLOR = 0x55FF55;

    public static List<Component> create(VeinScan scan, long dayTime, double activeMiners) {
        return create(scan, dayTime, activeMiners, scan.current().map(VeinGenerator.Occurrence::capacity).orElse(0));
    }

    /** Resolve only after the real-vein query has completed, using the captured scan origin. */
    public static List<Component> create(ServerLevel level, BlockPos origin, VeinScan scan, long dayTime) {
        var target = MiningTarget.resolve(level, origin, scan.current());
        double occupied = target == null ? 0 : VeinManager.capacity(level).occupiedCapacity(target.identity());
        return create(scan, dayTime, occupied, target == null ? 0 : target.capacity());
    }

    private static List<Component> create(VeinScan scan, long dayTime, double activeMiners, int capacity) {
        List<Component> lines = new ArrayList<>();
        lines.add(text("header", text("title").withStyle(ChatFormatting.GOLD),
                highlight(Component.literal("[" + scan.centre().x + ", " + scan.centre().z + "]")),
                highlight(Component.literal("[" + formatWorldTime(dayTime) + "]"))));
        lines.add(text("current", scan.current().<Component>map(v -> highlight(v.definition().name()))
                .orElseGet(() -> text("none"))));
        if (capacity > 0) lines.add(text("capacity",
                highlight(Component.literal(dev.gensomechanic.mining.content.miner.MiningNumbers.format(activeMiners) + " / " + capacity))));

        if (scan.traces().isEmpty()) lines.add(text("no_trace"));
        else for (VeinScan.Trace trace : scan.traces()) {
            long metres = Math.round(Math.sqrt(trace.distanceSquared()));
            lines.add(text("trace", highlight(trace.vein().definition().name()),
                    highlight(Component.translatable("scanner.gensomining.bearing_distance",
                            Component.translatable(trace.bearing().translationKey()), metres))));
        }
        return List.copyOf(lines);
    }

    public static String formatWorldTime(long dayTime) {
        // Minecraft 0 = 06:00. Reduce before adding to avoid overflowing a long-running world clock.
        long shiftedTicks = (Math.floorMod(dayTime, 24000L) + 6000) % 24000;
        long seconds = shiftedTicks * 86400 / 24000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private static MutableComponent highlight(Component component) {
        return component.copy().withStyle(Style.EMPTY.withColor(HIGHLIGHT_COLOR));
    }
    private static MutableComponent text(String key, Object... arguments) {
        return Component.translatable("scanner.gensomining." + key, arguments).withStyle(ChatFormatting.WHITE);
    }
    private ScannerReport() {}
}
