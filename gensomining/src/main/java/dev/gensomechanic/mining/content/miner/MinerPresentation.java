package dev.gensomechanic.mining.content.miner;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Server-owned presentation timestamps. Visual copies never participate in inventory or drops. */
public final class MinerPresentation {
    public static final int TRAY_TICKS = 20, FUSE_START = 10, BLAST_TICK = 50;
    private long explosiveStart = -1, outputStart = -1;
    private ItemStack consumed = ItemStack.EMPTY;
    private boolean worked;

    public void consumed(long now, ItemStack stack) {
        if (stack.isEmpty() || explosiveAge(now) <= BLAST_TICK) return;
        explosiveStart = now;
        consumed = stack.copyWithCount(1);
    }
    public void produced(long now, boolean wasEmpty, boolean isEmpty) {
        if (wasEmpty && !isEmpty && !outputLocked(now)) outputStart = now;
    }
    public void worked() { worked = true; }
    public boolean hasWorked() { return worked; }
    public long explosiveStart() { return explosiveStart; }
    public long outputStart() { return outputStart; }
    public long explosiveAge(long now) { return age(now, explosiveStart); }
    public long outputAge(long now) { return age(now, outputStart); }
    private static long age(long now, long start) { return start < 0 ? Integer.MAX_VALUE : Math.max(0, now - start); }
    public boolean outputLocked(long now) { return outputAge(now) < TRAY_TICKS; }
    public static boolean funnelOpen(float age) { return age >= 0 && age < TRAY_TICKS; }
    public ItemStack consumedItem() { return consumed.copy(); }
    public static float trayOffset(float age) {
        if (age < 0 || age >= TRAY_TICKS) return 0;
        float progress = Mth.clamp(age / 10f, 0, 2);
        float t = progress <= 1 ? progress : 2 - progress;
        return t * t * (3 - 2 * t);
    }
    public CompoundTag write(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("ExplosiveStart", explosiveStart);
        tag.putLong("OutputStart", outputStart);
        tag.put("Consumed", consumed.saveOptional(registries));
        tag.putBoolean("Worked", worked);
        return tag;
    }
    public void read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        explosiveStart = tag.contains("ExplosiveStart") ? tag.getLong("ExplosiveStart") : -1;
        outputStart = tag.contains("OutputStart") ? tag.getLong("OutputStart") : -1;
        consumed = ItemStack.parseOptional(registries, tag.getCompound("Consumed"));
        if (!consumed.isEmpty()) consumed.setCount(1);
        worked = tag.getBoolean("Worked");
    }
}
