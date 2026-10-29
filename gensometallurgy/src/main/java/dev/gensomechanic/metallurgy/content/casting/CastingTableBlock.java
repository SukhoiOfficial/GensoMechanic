// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.content.casting;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import dev.gensomechanic.metallurgy.MetallurgyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CastingTableBlock extends HorizontalDirectionalBlock
    implements IBE<CastingTableBlockEntity>, IWrenchable {
    public static final MapCodec<CastingTableBlock> CODEC = simpleCodec(CastingTableBlock::new);
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    private static final VoxelShape NORTH_SOUTH_SHAPE = box(0, 0, 0, 16, 12, 16);
    private static final VoxelShape EAST_WEST_SHAPE = NORTH_SOUTH_SHAPE;

    public CastingTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X
            ? EAST_WEST_SHAPE
            : NORTH_SOUTH_SHAPE;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        return onBlockEntityUseItemOn(level, pos, table -> {
            if (!heldItem.isEmpty()) {
                if (FluidHelper.tryEmptyItemIntoBE(level, player, hand, heldItem, table)
                    || FluidHelper.tryFillItemFromBE(level, player, hand, heldItem, table)) {
                    return ItemInteractionResult.SUCCESS;
                }
                if (GenericItemEmptying.canItemBeEmptied(level, heldItem)
                    || GenericItemFilling.canItemBeFilled(level, heldItem)) {
                    return ItemInteractionResult.SUCCESS;
                }
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }

            if (!table.hasOutput())
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (level.isClientSide)
                return ItemInteractionResult.SUCCESS;

            ItemStack output = table.takeOutput();
            if (!output.isEmpty()) {
                player.getInventory().placeItemBackInInventory(output);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                    .2f, 1f + level.random.nextFloat());
            }
            return ItemInteractionResult.SUCCESS;
        });
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.getBlock() != newState.getBlock() && !moving) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof CastingTableBlockEntity table && !level.isClientSide) {
                ItemStack output = table.takeOutput();
                if (!output.isEmpty())
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), output);
            }
        }
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<CastingTableBlockEntity> getBlockEntityClass() {
        return CastingTableBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CastingTableBlockEntity> getBlockEntityType() {
        return MetallurgyBlockEntities.CASTING_TABLE.get();
    }
}
