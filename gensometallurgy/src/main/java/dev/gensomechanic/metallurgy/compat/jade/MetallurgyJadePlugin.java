package dev.gensomechanic.metallurgy.compat.jade;

import dev.gensomechanic.metallurgy.GensoMetallurgy;
import dev.gensomechanic.metallurgy.content.casting.CastingTableBlockEntity;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;
import snownee.jade.util.CommonProxy;

/** Discovered only by Jade. Common mod registration never links this optional API. */
@WailaPlugin
public final class MetallurgyJadePlugin implements IWailaPlugin {
    public static final ResourceLocation ITEMS = GensoMetallurgy.asResource("machine_items");
    public static final ResourceLocation FLUIDS = GensoMetallurgy.asResource("machine_fluids");
    public static final String SHARED_CAPACITY = "SharedCapacity";
    public static final String SHARED_AMOUNT = "SharedAmount";

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerItemStorage(Items.INSTANCE, IndustrialBlastFurnaceBlockEntity.class);
        registration.registerItemStorage(Items.INSTANCE, CastingTableBlockEntity.class);
        registration.registerFluidStorage(Fluids.INSTANCE, IndustrialBlastFurnaceBlockEntity.class);
        registration.registerFluidStorage(Fluids.INSTANCE, CastingTableBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        dev.gensomechanic.metallurgy.client.compat.MetallurgyJadeClient.register(registration);
    }

    private static BlockAccessor machine(Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block) || block.getLevel().isClientSide
                || !block.getLevel().hasChunkAt(block.getPosition())) return null;
        var entity = block.getBlockEntity();
        if (!(entity instanceof IndustrialBlastFurnaceBlockEntity || entity instanceof CastingTableBlockEntity)
                || entity.isRemoved() || block.getLevel().getBlockEntity(block.getPosition()) != entity) return null;
        return block;
    }

    public enum Items implements IServerExtensionProvider<ItemStack> {
        INSTANCE;

        @Override public ResourceLocation getUid() { return ITEMS; }

        @Override
        public List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
            BlockAccessor block = machine(accessor);
            if (block == null) return List.of();
            var storage = block.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                    block.getPosition(), (Direction) null);
            if (storage == null || storage.getSlots() == 0) return List.of();
            ItemStack[] copies = new ItemStack[storage.getSlots()];
            for (int slot = 0; slot < copies.length; slot++) copies[slot] = storage.getStackInSlot(slot).copy();
            // The normal Jade collector handles item coalescing, limits and detail settings.
            var snapshot = new SimpleContainer(copies);
            return CommonProxy.containerGroup(snapshot, accessor, ignored -> snapshot);
        }
    }

    public enum Fluids implements IServerExtensionProvider<CompoundTag> {
        INSTANCE;

        @Override public ResourceLocation getUid() { return FLUIDS; }

        @Override
        public List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
            BlockAccessor block = machine(accessor);
            if (block == null) return List.of();
            var storage = block.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                    block.getPosition(), (Direction) null);
            if (storage == null || storage.getTanks() == 0) return List.of();

            // All furnace virtual slots report the same shared capacity. Summing them,
            // as the generic independent-tank collector does, would invent extra space.
            int capacity = storage.getTankCapacity(0);
            if (capacity <= 0) return List.of();
            List<CompoundTag> views = new ArrayList<>();
            long amount = 0;
            for (int tank = 0; tank < storage.getTanks(); tank++) {
                var fluid = storage.getFluidInTank(tank).copy();
                if (fluid.isEmpty()) continue;
                amount += fluid.getAmount();
                views.add(FluidView.writeDefault(JadeFluidObject.of(fluid.getFluid(), fluid.getAmount(),
                        fluid.getComponentsPatch()), capacity, accessor.nbtOps()));
            }
            if (views.isEmpty()) {
                views.add(FluidView.writeDefault(JadeFluidObject.empty(), capacity, accessor.nbtOps()));
            }
            ViewGroup<CompoundTag> group = new ViewGroup<>(List.copyOf(views));
            if (block.getBlockEntity() instanceof IndustrialBlastFurnaceBlockEntity) {
                group.getExtraData().putLong(SHARED_CAPACITY, capacity);
                group.getExtraData().putLong(SHARED_AMOUNT, amount);
            }
            return List.of(group);
        }
    }
}
