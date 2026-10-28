package dev.gensomechanic.mining.compat.jade;

import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.content.miner.MechanicalMinerBlockEntity;
import dev.gensomechanic.mining.content.miner.MinerPortBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.*;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.*;

@WailaPlugin
public final class GensoJadePlugin implements IWailaPlugin {
    public static final ResourceLocation ITEMS = GensoMining.asResource("machine_items");
    public static final ResourceLocation FLUIDS = GensoMining.asResource("machine_fluids");

    @Override public void register(IWailaCommonRegistration registration) {
        registration.registerItemStorage(Items.INSTANCE, MechanicalMinerBlockEntity.class);
        registration.registerFluidStorage(Fluids.INSTANCE, MechanicalMinerBlockEntity.class);
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        dev.gensomechanic.mining.client.compat.GensoJadeClient.register(registration);
    }

    private static MechanicalMinerBlockEntity miner(Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block) || block.getLevel().isClientSide) return null;
        var miner = MinerPortBlockEntity.resolve(block.getLevel(), block.getPosition());
        return miner != null && block.getBlockEntity() == miner ? miner : null;
    }

    public enum Items implements IServerExtensionProvider<ItemStack> {
        INSTANCE;
        @Override public ResourceLocation getUid() { return ITEMS; }
        @Override public List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
            var miner = miner(accessor);
            if (miner == null) return List.of();
            List<ItemStack> items = new ArrayList<>();
            if (!miner.explosiveStack().isEmpty()) items.add(miner.explosiveStack());
            for (int i = 0; i < 9; i++) if (!miner.outputInSlot(i).isEmpty()) items.add(miner.outputInSlot(i));
            return items.isEmpty() ? List.of() : List.of(new ViewGroup<>(items));
        }
    }

    public enum Fluids implements IServerExtensionProvider<CompoundTag> {
        INSTANCE;
        @Override public ResourceLocation getUid() { return FLUIDS; }
        @Override public List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
            var miner = miner(accessor);
            if (miner == null) return List.of();
            var fluid = miner.lubricantFluid();
            if (fluid.isEmpty()) return List.of();
            var value = JadeFluidObject.of(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
            return List.of(new ViewGroup<>(List.of(FluidView.writeDefault(value, 1000,
                    accessor.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE)))));
        }
    }
}
