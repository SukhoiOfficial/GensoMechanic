package dev.gensomechanic.mining.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ScannerOverlayConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = B.define("scannerOverlay.enabled", true);
    public static final ModConfigSpec.IntValue DURATION = B.defineInRange("scannerOverlay.durationSeconds", 120, 1, 3600);
    public static final ModConfigSpec.IntValue OPACITY = B.defineInRange("scannerOverlay.fillOpacityPercent", 25, 0, 100);
    public static final ModConfigSpec SPEC = B.build();
    private ScannerOverlayConfig() {}
}
