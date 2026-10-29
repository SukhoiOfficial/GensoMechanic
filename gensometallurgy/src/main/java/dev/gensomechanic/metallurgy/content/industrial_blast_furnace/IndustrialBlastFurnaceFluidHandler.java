// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.content.industrial_blast_furnace;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Exposes the furnace's per-block stores as nine virtual tanks with one shared capacity. */
public final class IndustrialBlastFurnaceFluidHandler implements IFluidHandler {

    private final Supplier<List<SharedCapacityFluidTank>> tanksSupplier;
    private final IntSupplier capacitySupplier;
    private final int maxFluidTypes;

    IndustrialBlastFurnaceFluidHandler(Supplier<List<SharedCapacityFluidTank>> tanksSupplier,
                                     IntSupplier capacitySupplier, int maxFluidTypes) {
        this.tanksSupplier = tanksSupplier;
        this.capacitySupplier = capacitySupplier;
        this.maxFluidTypes = maxFluidTypes;
    }

    @Override
    public int getTanks() {
        return maxFluidTypes;
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank) {
        List<FluidStack> fluids = getFluids();
        if (tank < 0 || tank >= fluids.size())
            return FluidStack.EMPTY;
        return fluids.get(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank >= 0 && tank < maxFluidTypes ? getCapacity() : 0;
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        if (tank < 0 || tank >= maxFluidTypes || stack.isEmpty())
            return false;
        List<FluidStack> fluids = getFluids();
        if (tank < fluids.size())
            return FluidStack.isSameFluidSameComponents(fluids.get(tank), stack);
        return fluids.size() < maxFluidTypes;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty())
            return 0;

        List<FluidStack> fluids = getFluids();
        boolean alreadyStored = contains(fluids, resource);
        if (!alreadyStored && fluids.size() >= maxFluidTypes)
            return 0;

        int remaining = Math.min(resource.getAmount(), Math.max(0, getCapacity() - getTotalFluidAmount(fluids)));
        if (remaining <= 0)
            return 0;

        List<SharedCapacityFluidTank> tanks = tanksSupplier.get();
        int accepted = 0;

        // Extend existing physical stacks first, keeping each fluid as contiguous as possible.
        if (alreadyStored) {
            for (SharedCapacityFluidTank tank : tanks) {
                if (!tank.contains(resource))
                    continue;
                int filled = fillTank(tank, resource, remaining, action);
                accepted += filled;
                remaining -= filled;
                if (remaining == 0)
                    return accepted;
            }
        }

        for (SharedCapacityFluidTank tank : tanks) {
            if (alreadyStored && tank.contains(resource)) continue;
            int filled = fillTank(tank, resource, remaining, action);
            accepted += filled;
            remaining -= filled;
            if (remaining == 0)
                break;
        }
        return accepted;
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty())
            return FluidStack.EMPTY;

        int remaining = resource.getAmount();
        int drainedAmount = 0;
        for (SharedCapacityFluidTank tank : tanksSupplier.get()) {
            FluidStack request = resource.copy();
            request.setAmount(remaining);
            FluidStack drained = tank.drain(request, action);
            drainedAmount += drained.getAmount();
            remaining -= drained.getAmount();
            if (remaining == 0)
                break;
        }
        if (drainedAmount == 0)
            return FluidStack.EMPTY;
        FluidStack result = resource.copy();
        result.setAmount(drainedAmount);
        return result;
    }

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0)
            return FluidStack.EMPTY;
        List<FluidStack> fluids = getFluids();
        if (fluids.isEmpty())
            return FluidStack.EMPTY;
        FluidStack request = fluids.get(0);
        request.setAmount(maxDrain);
        return drain(request, action);
    }

    public List<FluidStack> getFluids() {
        List<FluidStack> combined = new ArrayList<>(maxFluidTypes);
        for (SharedCapacityFluidTank tank : tanksSupplier.get()) {
            for (FluidStack stored : tank.getFluids()) {
                int matchingIndex = findFluid(combined, stored);
                if (matchingIndex == -1) {
                    combined.add(stored.copy());
                } else {
                    combined.get(matchingIndex).grow(stored.getAmount());
                }
            }
        }
        return combined;
    }

    public int getCapacity() {
        return Math.max(0, capacitySupplier.getAsInt());
    }

    int getTotalFluidAmount() {
        return getTotalFluidAmount(getFluids());
    }

    private int fillTank(SharedCapacityFluidTank tank, FluidStack resource, int amount, FluidAction action) {
        if (amount <= 0)
            return 0;
        FluidStack offered = resource.copy();
        offered.setAmount(amount);
        return tank.fill(offered, action);
    }

    private static boolean contains(List<FluidStack> fluids, FluidStack target) {
        return findFluid(fluids, target) != -1;
    }

    private static int findFluid(List<FluidStack> fluids, FluidStack target) {
        for (int i = 0; i < fluids.size(); i++) {
            if (FluidStack.isSameFluidSameComponents(fluids.get(i), target))
                return i;
        }
        return -1;
    }

    private static int getTotalFluidAmount(List<FluidStack> fluids) {
        int total = 0;
        for (FluidStack fluid : fluids)
            total += fluid.getAmount();
        return total;
    }
}
