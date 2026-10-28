package dev.gensomechanic.mining.client.compat;

import dev.gensomechanic.mining.compat.jade.GensoJadePlugin;
import dev.gensomechanic.mining.content.miner.MinerPortBlockEntity;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.*;
import snownee.jade.api.view.*;

public final class GensoJadeClient {
    public static void register(IWailaClientRegistration registration) {
        registration.registerItemStorageClient(Items.INSTANCE);
        registration.registerFluidStorageClient(Fluids.INSTANCE);
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (!(accessor instanceof BlockAccessor block)) return accessor;
            var miner = MinerPortBlockEntity.resolve(block.getLevel(), block.getPosition());
            if (miner == null || miner.getBlockPos().equals(block.getPosition())) return accessor;
            return registration.blockAccessor().from(block).blockState(miner.getBlockState()).blockEntity(miner)
                    .hit(block.getHitResult().withPosition(miner.getBlockPos())).requireVerification().build();
        });
    }

    public enum Items implements IClientExtensionProvider<ItemStack, ItemView> {
        INSTANCE;
        @Override public ResourceLocation getUid() { return GensoJadePlugin.ITEMS; }
        @Override public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
            var views = groups.stream().flatMap(group -> group.views.stream()).map(ItemView::new).toList();
            return views.isEmpty() ? List.of() : List.of(new ClientViewGroup<>(views));
        }
    }

    public enum Fluids implements IClientExtensionProvider<CompoundTag, FluidView> {
        INSTANCE;
        @Override public ResourceLocation getUid() { return GensoJadePlugin.FLUIDS; }
        @Override public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
            var ops = accessor.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var views = groups.stream().flatMap(group -> group.views.stream()).map(tag -> FluidView.readDefault(tag, ops)).toList();
            return views.isEmpty() ? List.of() : List.of(new ClientViewGroup<>(views));
        }
    }

    private GensoJadeClient() {}
}
