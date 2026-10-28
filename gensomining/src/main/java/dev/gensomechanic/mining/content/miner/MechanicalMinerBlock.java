package dev.gensomechanic.mining.content.miner;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MechanicalMinerBlock extends HorizontalKineticBlock implements IBE<MechanicalMinerBlockEntity> {
    public MechanicalMinerBlock(Properties properties) { super(properties); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face == Direction.UP; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MinerShapes.get(MinerStructure.CONTROLLER, MinerStructure.facing(state));
    }
    @Override public void onRemove(BlockState old, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!old.is(next.getBlock()) && !level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof MechanicalMinerBlockEntity miner) miner.dropOutput();
            MinerStructure.removeParts(level, pos, MinerStructure.facing(old));
        }
        super.onRemove(old, level, pos, next, moving);
    }
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        BlockState rotated = getRotatedBlockState(state, context.getClickedFace());
        if (rotated.equals(state)) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!MinerStructure.rotate(context.getLevel(), context.getClickedPos(), MinerStructure.facing(state), MinerStructure.facing(rotated)))
            return InteractionResult.FAIL;
        IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
        return InteractionResult.SUCCESS;
    }
    @Override public Class<MechanicalMinerBlockEntity> getBlockEntityClass() { return MechanicalMinerBlockEntity.class; }
    @Override public BlockEntityType<? extends MechanicalMinerBlockEntity> getBlockEntityType() { return MiningContent.MINER_ENTITY.get(); }
}
