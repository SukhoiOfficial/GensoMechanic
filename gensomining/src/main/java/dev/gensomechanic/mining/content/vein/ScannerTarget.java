package dev.gensomechanic.mining.content.vein;

import dev.gensomechanic.mining.GensoMining;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class ScannerTarget {
    private static final DeferredRegister.DataComponents TYPES = DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, GensoMining.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ScanSelection>> SELECTION = TYPES.registerComponentType("scan_selection",
            b -> b.persistent(ScanSelection.CODEC).networkSynchronized(ScanSelection.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ScanSelection>> ARCHIVE = TYPES.registerComponentType("archive_selection",
            b -> b.persistent(ScanSelection.CODEC).networkSynchronized(ScanSelection.STREAM_CODEC));
    public static ScanSelection selection(ItemStack stack) { return stack.getOrDefault(SELECTION, ScanSelection.NONE); }
    public static void select(ItemStack stack, ScanSelection selection) { stack.set(SELECTION, selection); }
    public static ScanSelection archive(ItemStack stack) { return stack.getOrDefault(ARCHIVE, ScanSelection.ALL); }
    public static ResourceLocation get(ItemStack stack) { return selection(stack).type().orElse(null); }
    public static void set(ItemStack stack, ResourceLocation target) { select(stack, ScanSelection.of(target)); }
    public static void register(IEventBus bus) { TYPES.register(bus); }
    private ScannerTarget() {}
}
