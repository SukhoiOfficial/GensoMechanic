package dev.gensomechanic.metallurgy.content.metallurgy;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Revokes previously resolved NeoForge handlers independently of capability-cache notifications. */
public final class MetallurgyStoragePort {
    private final BooleanSupplier connected;
    private final IItemHandler items;
    private final IFluidHandler fluids;
    private boolean valid = true;

    public MetallurgyStoragePort(IItemHandler itemStorage, IFluidHandler fluidStorage, BooleanSupplier connected) {
        this.connected = connected;
        items = new ItemPort(itemStorage);
        fluids = new FluidPort(fluidStorage);
    }

    public static boolean isPresent(SmartBlockEntity owner) {
        var level = owner.getLevel();
        return level != null && !owner.isRemoved() && !owner.isChunkUnloaded()
            && level.hasChunkAt(owner.getBlockPos()) && level.getBlockEntity(owner.getBlockPos()) == owner;
    }

    public IItemHandler items() { return items; }
    public IFluidHandler fluids() { return fluids; }

    public boolean isValid() {
        if (valid && !connected.getAsBoolean()) invalidate();
        return valid;
    }

    public void invalidate() {
        if (!valid) return;
        valid = false;

    }

    private final class ItemPort implements IItemHandler {
        private final IItemHandler delegate;
        private ItemPort(IItemHandler delegate) { this.delegate = delegate; }
        private boolean acceptsSlot(int slot) {
            return isValid() && slot >= 0 && slot < delegate.getSlots();
        }
        @Override public int getSlots() { return isValid() ? delegate.getSlots() : 0; }
        @Override public ItemStack getStackInSlot(int slot) {
            return acceptsSlot(slot) ? delegate.getStackInSlot(slot).copy() : ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return !stack.isEmpty() && acceptsSlot(slot) ? delegate.insertItem(slot, stack.copy(), simulate).copy() : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return amount > 0 && acceptsSlot(slot) ? delegate.extractItem(slot, amount, simulate).copy() : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return acceptsSlot(slot) ? delegate.getSlotLimit(slot) : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return acceptsSlot(slot) && delegate.isItemValid(slot, stack.copy());
        }
    }

    private final class FluidPort implements IFluidHandler {
        private final IFluidHandler delegate;
        private FluidPort(IFluidHandler delegate) { this.delegate = delegate; }
        private boolean acceptsTank(int tank) {
            return isValid() && tank >= 0 && tank < delegate.getTanks();
        }
        @Override public int getTanks() { return isValid() ? delegate.getTanks() : 0; }
        @Override public FluidStack getFluidInTank(int tank) {
            return acceptsTank(tank) ? delegate.getFluidInTank(tank).copy() : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) { return acceptsTank(tank) ? delegate.getTankCapacity(tank) : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return acceptsTank(tank) && delegate.isFluidValid(tank, stack.copy());
        }
        @Override public int fill(FluidStack resource, FluidAction action) {
            return !resource.isEmpty() && isValid() ? delegate.fill(resource.copy(), action) : 0;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return !resource.isEmpty() && isValid() ? delegate.drain(resource.copy(), action).copy() : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            return maxDrain > 0 && isValid() ? delegate.drain(maxDrain, action).copy() : FluidStack.EMPTY;
        }
    }
}
