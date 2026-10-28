package dev.gensomechanic.mining.content.miner;

import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MechanicalMinerPartBlock extends Block implements IWrenchable, IProxyHoveringInformation {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 17);
    public MechanicalMinerPartBlock(Properties properties) { super(properties); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, BlockStateProperties.HORIZONTAL_FACING);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MinerShapes.get(state.getValue(PART), MinerStructure.facing(state));
    }
    @Override public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        var main = MinerStructure.controller(pos, state);
        return level.hasChunkAt(main) ? main : pos;
    }
    @Override public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) { return MiningContent.MECHANICAL_MINER.asStack(); }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 40);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos main = MinerStructure.controller(pos, state);
        if (level.hasChunkAt(main) && !level.getBlockState(main).equals(MinerStructure.state(MinerStructure.CONTROLLER, MinerStructure.facing(state)))) {
            level.removeBlock(pos, false);
            return;
        }
        level.scheduleTick(pos, this, 40);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        MinerStructure.removeController(level, pos, state, !player.isCreative() && player.hasCorrectToolForDrops(state));
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override public void onRemove(BlockState old, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!old.is(next.getBlock())) MinerStructure.removeController(level, pos, old, true);
        super.onRemove(old, level, pos, next, moving);
    }
    private InteractionResult wrench(BlockState state, UseOnContext context, boolean sneak) {
        Level level = context.getLevel();
        BlockPos main = MinerStructure.controller(context.getClickedPos(), state);
        if (!level.hasChunkAt(main)) return InteractionResult.FAIL;
        BlockState mainState = level.getBlockState(main);
        if (!mainState.is(MiningContent.MECHANICAL_MINER.get()) || MinerStructure.facing(mainState) != MinerStructure.facing(state)) return InteractionResult.PASS;
        UseOnContext forwarded = new UseOnContext(level, context.getPlayer(), context.getHand(), context.getItemInHand(),
                new BlockHitResult(Vec3.atCenterOf(main), context.getClickedFace(), main, false));
        IWrenchable block = (IWrenchable) mainState.getBlock();
        return sneak ? block.onSneakWrenched(mainState, forwarded) : block.onWrenched(mainState, forwarded);
    }
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) { return wrench(state, context, false); }
    @Override public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) { return wrench(state, context, true); }
}
