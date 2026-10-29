// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.content.industrial_blast_furnace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A small multi-fluid store whose fluids all consume the same capacity.
 *
 * <p>Instances are owned by individual furnace blocks. The controller combines
 * them through {@link IndustrialBlastFurnaceFluidHandler}, so structure changes do
 * not require Create's single-fluid multiblock transfer code.</p>
 */
final class SharedCapacityFluidTank implements IFluidHandler {

    private static final String FLUIDS_TAG = "Fluids";

    private final int capacity;
    private final int maxFluidTypes;
    private final Runnable onChanged;
    private final List<FluidStack> fluids;

    SharedCapacityFluidTank(int capacity, int maxFluidTypes, Runnable onChanged) {
        this.capacity = capacity;
        this.maxFluidTypes = maxFluidTypes;
        this.onChanged = onChanged;
        fluids = new ArrayList<>(maxFluidTypes);
    }

    @Override
    public int getTanks() {
        return maxFluidTypes;
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank < 0 || tank >= fluids.size())
            return FluidStack.EMPTY;
        return fluids.get(tank).copy();
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank >= 0 && tank < maxFluidTypes ? capacity : 0;
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        if (tank < 0 || tank >= maxFluidTypes || stack.isEmpty())
            return false;
        if (tank < fluids.size())
            return FluidStack.isSameFluidSameComponents(fluids.get(tank), stack);
        return fluids.size() < maxFluidTypes;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty())
            return 0;

        int matchingIndex = findFluid(resource);
        if (matchingIndex == -1 && fluids.size() >= maxFluidTypes)
            return 0;

        int accepted = Math.min(resource.getAmount(), Math.max(0, capacity - getTotalFluidAmount()));
        if (accepted <= 0)
            return 0;
        if (action.simulate())
            return accepted;

        if (matchingIndex == -1) {
            FluidStack inserted = resource.copy();
            inserted.setAmount(accepted);
            fluids.add(inserted);
        } else {
            fluids.get(matchingIndex).grow(accepted);
        }
        onChanged.run();
        return accepted;
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty())
            return FluidStack.EMPTY;
        int matchingIndex = findFluid(resource);
        if (matchingIndex == -1)
            return FluidStack.EMPTY;
        return drainFrom(matchingIndex, resource.getAmount(), action);
    }

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0 || fluids.isEmpty())
            return FluidStack.EMPTY;
        return drainFrom(0, maxDrain, action);
    }

    List<FluidStack> getFluids() {
        if (fluids.isEmpty())
            return Collections.emptyList();
        List<FluidStack> copies = new ArrayList<>(fluids.size());
        for (FluidStack fluid : fluids)
            copies.add(fluid.copy());
        return copies;
    }

    int getTotalFluidAmount() {
        int total = 0;
        for (FluidStack fluid : fluids)
            total += fluid.getAmount();
        return total;
    }

    boolean contains(FluidStack fluid) {
        return findFluid(fluid) != -1;
    }

    CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag fluidList = new ListTag();
        for (FluidStack fluid : fluids) {
            if (fluid.isEmpty())
                continue;
            fluidList.add(fluid.save(registries));
        }
        tag.put(FLUIDS_TAG, fluidList);
        return tag;
    }

    void deserializeNBT(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag) {
        fluids.clear();
        ListTag fluidList = tag.getList(FLUIDS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < fluidList.size() && fluids.size() < maxFluidTypes; i++) {
            FluidStack fluid = FluidStack.parseOptional(registries, fluidList.getCompound(i));
            int accepted = Math.min(fluid.getAmount(), Math.max(0, capacity - getTotalFluidAmount()));
            if (accepted <= 0)
                break;
            fluid.setAmount(accepted);
            addUnchecked(fluid);
        }
    }



    void clear() {
        if (fluids.isEmpty())
            return;
        fluids.clear();
        onChanged.run();
    }

    private FluidStack drainFrom(int index, int maxDrain, FluidAction action) {
        if (maxDrain <= 0 || index < 0 || index >= fluids.size())
            return FluidStack.EMPTY;

        FluidStack stored = fluids.get(index);
        int drainedAmount = Math.min(maxDrain, stored.getAmount());
        FluidStack drained = stored.copy();
        drained.setAmount(drainedAmount);
        if (action.execute()) {
            stored.shrink(drainedAmount);
            if (stored.isEmpty())
                fluids.remove(index);
            onChanged.run();
        }
        return drained;
    }

    private int findFluid(FluidStack target) {
        for (int i = 0; i < fluids.size(); i++) {
            if (FluidStack.isSameFluidSameComponents(fluids.get(i), target))
                return i;
        }
        return -1;
    }

    private void addUnchecked(FluidStack fluid) {
        if (fluid.isEmpty())
            return;
        int matchingIndex = findFluid(fluid);
        if (matchingIndex == -1) {
            if (fluids.size() < maxFluidTypes)
                fluids.add(fluid.copy());
        } else {
            fluids.get(matchingIndex).grow(fluid.getAmount());
        }
    }
}
