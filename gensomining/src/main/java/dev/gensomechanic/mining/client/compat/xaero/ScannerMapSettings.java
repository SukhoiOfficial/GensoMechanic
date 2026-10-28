package dev.gensomechanic.mining.client.compat.xaero;

import dev.gensomechanic.mining.client.ScannerOverlayConfig;
import java.util.function.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import xaero.common.gui.ScreenSwitchSettingEntry;
import xaero.lib.client.gui.*;

/** Xaero's own paged settings and slider widgets, backed by this mod's client config. */
public final class ScannerMapSettings extends GuiSettings {
    public static ISettingEntry entry() { return new ScreenSwitchSettingEntry("scanner.gensomining.map.settings", ScannerMapSettings::new, null, true); }
    public ScannerMapSettings(Screen parent, Screen escape) {
        super(text("settings"), parent, escape);
        entries = new ISettingEntry[] {
            new CustomSettingEntry<Boolean>(() -> false, text("enabled"), null, false, ScannerOverlayConfig.ENABLED::get,
                    0, 1, i -> i == 1, b -> Component.translatable(b ? "options.on" : "options.off"), (old, value) -> {
                        ScannerOverlayConfig.ENABLED.set(value); ScannerOverlayConfig.ENABLED.save(); ScannerMapBridge.refresh();
                    }, () -> true),
            number("duration", ScannerOverlayConfig.DURATION::get, v -> set(ScannerOverlayConfig.DURATION, v), 1, 3600),
            number("opacity", ScannerOverlayConfig.OPACITY::get, v -> set(ScannerOverlayConfig.OPACITY, v), 0, 100)
        };
    }
    private static Component text(String key) { return Component.translatable("scanner.gensomining.map." + key); }
    private static ISettingEntry number(String key, Supplier<Integer> get, IntConsumer set, int min, int max) {
        return new CustomSettingEntry<Integer>(() -> false, text(key), null, true, get, min, max, i -> i,
                v -> Component.literal(Integer.toString(v)), (old, v) -> set.accept(v), () -> true);
    }
    private static void set(ModConfigSpec.IntValue value, int next) { value.set(next); value.save(); ScannerMapBridge.refresh(); }
}
