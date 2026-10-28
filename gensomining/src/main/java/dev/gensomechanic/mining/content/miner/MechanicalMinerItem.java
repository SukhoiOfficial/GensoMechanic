package dev.gensomechanic.mining.content.miner;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class MechanicalMinerItem extends BlockItem {
    // Installed by client setup; dedicated servers never load the outline renderer.
    private static Consumer<BlockPlaceContext> placementFailureHandler = context -> {};

    public MechanicalMinerItem(Block block, Properties properties) { super(block, properties); }

    public static void registerPlacementFailureHandler(Consumer<BlockPlaceContext> handler) {
        placementFailureHandler = Objects.requireNonNull(handler);
    }

    public static BlockPos placementController(BlockPlaceContext context) {
        return context.getClickedPos().above();
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (result == InteractionResult.FAIL && context.getLevel().isClientSide())
            placementFailureHandler.accept(context);
        return result;
    }

    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext original) {
        var top = placementController(original);
        if (!original.getLevel().getBlockState(top).canBeReplaced()) return null;
        return new BlockPlaceContext(original.getLevel(), original.getPlayer(), original.getHand(), original.getItemInHand(),
                new BlockHitResult(Vec3.atCenterOf(top), original.getClickedFace(), top, false));
    }
    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        return super.canPlace(context, state) && MinerStructure.canPlace(context, state);
    }
    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return MinerStructure.canPlace(context, state)
                && MinerStructure.place(context.getLevel(), context.getClickedPos(), MinerStructure.facing(state));
    }
}
