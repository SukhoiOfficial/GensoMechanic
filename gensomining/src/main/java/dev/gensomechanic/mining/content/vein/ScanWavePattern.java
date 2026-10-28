package dev.gensomechanic.mining.content.vein;

import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** Cosmetic geometry, independent of the 80-block geological query range. */
public final class ScanWavePattern {
    public static final int RINGS = 3;
    public static final int RING_TICKS = 20;
    public static final int RING_DELAY = 5;
    public static final int DURATION = RING_TICKS + (RINGS - 1) * RING_DELAY;
    public static final double RADIUS = 8;

    public static void points(int age, Consumer<Vec3> point) {
        if (age < 0 || age >= DURATION) return;
        for (int ring = 0; ring < RINGS; ring++) {
            int ringAge = age - ring * RING_DELAY;
            if (ringAge < 0 || ringAge >= RING_TICKS) continue;
            double radius = RADIUS * (ringAge + 1.0) / RING_TICKS;
            int segments = Math.max(12, (int) Math.ceil(2 * Math.PI * radius / .7));
            for (int i = 0; i < segments; i++) {
                double angle = 2 * Math.PI * i / segments;
                point.accept(new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
            }
        }
    }
    private ScanWavePattern() {}
}
