package dev.gensomechanic.mining.content.miner;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Each returned handler is a lease on this exact port, controller instance, orientation and epoch. */
public final class MinerPortBlockEntity extends SmartBlockEntity {
    public MinerPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public static MechanicalMinerBlockEntity resolve(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MechanicalMinerBlock || state.getBlock() instanceof MechanicalMinerPartBlock)) return null;
        BlockPos main = MinerStructure.controller(pos, state);
        if (!level.hasChunkAt(main) || !(level.getBlockEntity(main) instanceof MechanicalMinerBlockEntity miner) || !miner.live()) return null;
        return miner;
    }
    public Direction face() {
        Direction facing = MinerStructure.facing(getBlockState());
        return switch (getBlockState().getValue(MechanicalMinerPartBlock.PART)) {
            case MinerStructure.OUTPUT -> facing;
            case MinerStructure.EXPLOSIVE -> facing.getOpposite();
            case MinerStructure.LUBRICANT, MinerStructure.LUBRICANT_TANK -> facing.getClockWise();
            default -> null;
        };
    }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this).onlyInsertWhen(this::acceptsItemMovement)
                .setInsertionHandler((stack, movement, simulate) -> {
                    if (!acceptsItemMovement(movement)) return stack.stack;
                    IItemHandler handler = items(movement.getOpposite());
                    return handler == null ? stack.stack : handler.insertItem(0, stack.stack, simulate);
                }));
    }
    private boolean acceptsItemMovement(Direction movement) {
        return getBlockState().getValue(MechanicalMinerPartBlock.PART) == MinerStructure.EXPLOSIVE
                && (movement == Direction.DOWN || movement == face().getOpposite());
    }
    private final class Lease {
        final MechanicalMinerBlockEntity miner;
        final BlockState state = getBlockState();
        final long epoch;
        private boolean revoked;
        Lease(MechanicalMinerBlockEntity miner) { this.miner = miner; epoch = miner.portEpoch(); }
        boolean live() {
            if (!revoked && (isRemoved() || level == null || !level.hasChunkAt(worldPosition)
                    || level.getBlockEntity(worldPosition) != MinerPortBlockEntity.this || !getBlockState().equals(state)
                    || miner.portEpoch() != epoch || !miner.live())) revoked = true;
            return !revoked;
        }
    }
    public IItemHandler items(Direction side) {
        int part = getBlockState().getValue(MechanicalMinerPartBlock.PART);
        if (side == null || side != face() && side != Direction.UP
                || part != MinerStructure.OUTPUT && part != MinerStructure.EXPLOSIVE || level == null) return null;
        var miner = resolve(level, worldPosition); if (miner == null) return null;
        Lease lease = new Lease(miner); boolean output = part == MinerStructure.OUTPUT;
        return new IItemHandler() {
            public int getSlots() { return output ? 9 : 1; }
            private boolean valid(int slot) { return slot >= 0 && slot < getSlots() && lease.live(); }
            public ItemStack getStackInSlot(int slot) { return valid(slot) ? output ? miner.outputInSlot(slot) : miner.explosiveStack() : ItemStack.EMPTY; }
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return !output && valid(slot) ? miner.insertExplosive(stack, simulate) : stack; }
            public ItemStack extractItem(int slot, int count, boolean simulate) { return valid(slot) && count > 0 ? output ? miner.extractOutput(slot, count, simulate) : miner.extractExplosive(count, simulate) : ItemStack.EMPTY; }
            public int getSlotLimit(int slot) { return 64; }
            public boolean isItemValid(int slot, ItemStack stack) { return !output && valid(slot) && miner.acceptsExplosive(stack); }
        };
    }
    public IFluidHandler fluids(Direction side) {
        int part = getBlockState().getValue(MechanicalMinerPartBlock.PART);
        if (level == null || side == null || part != MinerStructure.LUBRICANT && part != MinerStructure.LUBRICANT_TANK) return null;
        if (side != face() && !(part == MinerStructure.LUBRICANT_TANK && side == Direction.UP)) return null;
        var miner = resolve(level, worldPosition); if (miner == null) return null;
        Lease lease = new Lease(miner);
        return new IFluidHandler() {
            public int getTanks() { return 1; }
            public FluidStack getFluidInTank(int tank) { return tank == 0 && lease.live() ? miner.lubricantFluid() : FluidStack.EMPTY; }
            public int getTankCapacity(int tank) { return 1000; }
            public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && lease.live() && miner.acceptsLubricant(stack); }
            public int fill(FluidStack stack, FluidAction action) { return lease.live() ? miner.fillLubricant(stack, action) : 0; }
            public FluidStack drain(FluidStack stack, FluidAction action) { return lease.live() ? miner.drainLubricant(stack, action) : FluidStack.EMPTY; }
            public FluidStack drain(int amount, FluidAction action) { return lease.live() ? miner.drainLubricant(amount, action) : FluidStack.EMPTY; }
        };
    }
    @Override public void invalidate() { super.invalidate(); invalidateCapabilities(); }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MiningContent.PORT_ENTITY.get(), MinerPortBlockEntity::items);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MiningContent.PORT_ENTITY.get(), MinerPortBlockEntity::fluids);
    }
}
