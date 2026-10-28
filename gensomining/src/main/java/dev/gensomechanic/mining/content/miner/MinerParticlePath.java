package dev.gensomechanic.mining.content.miner;

import dev.gensomechanic.mining.content.miner.MiningContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Visual rays ignore only the emitting machine. Gameplay collision remains unchanged. */
public final class MinerParticlePath {
    public static Vec3 exhaust(BlockPos controller, Direction facing) {
        return Vec3.atCenterOf(controller).add(Vec3.atLowerCornerOf(facing.getCounterClockWise().getNormal()).scale(1.64)).add(0, -1, 0);
    }

    public static BlockHitResult clip(BlockGetter level, BlockPos owner, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()) {
            @Override public VoxelShape getBlockShape(BlockState state, BlockGetter world, BlockPos pos) {
                boolean machine = state.is(MiningContent.MECHANICAL_MINER.get()) || state.getBlock() instanceof MechanicalMinerPartBlock;
                if (owner != null && machine && MinerStructure.controller(pos, state).equals(owner)) return Shapes.empty();
                return super.getBlockShape(state, world, pos);
            }
        });
    }
    private MinerParticlePath() {}
}
