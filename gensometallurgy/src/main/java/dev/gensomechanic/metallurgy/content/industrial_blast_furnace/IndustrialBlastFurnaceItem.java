// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.content.industrial_blast_furnace;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;
import dev.gensomechanic.metallurgy.MetallurgyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class IndustrialBlastFurnaceItem extends BlockItem {
    public IndustrialBlastFurnaceItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult initialResult = super.place(context);
        if (!initialResult.consumesAction())
            return initialResult;

        tryMultiPlace(context);
        return initialResult;
    }

    private void tryMultiPlace(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown())
            return;

        Direction face = context.getClickedFace();
        if (!face.getAxis().isVertical())
            return;
        if (SymmetryWandItem.presentInHotbar(player))
            return;

        Level level = context.getLevel();
        BlockPos placedPos = context.getClickedPos();
        BlockPos placedOnPos = placedPos.relative(face.getOpposite());
        if (!IndustrialBlastFurnaceBlock.isFurnace(level.getBlockState(placedOnPos)))
            return;

        IndustrialBlastFurnaceBlockEntity part = ConnectivityHandler.partAt(
            MetallurgyBlockEntities.INDUSTRIAL_BLAST_FURNACE.get(), level, placedOnPos);
        if (part == null)
            return;
        IndustrialBlastFurnaceBlockEntity controller = part.getControllerBE();
        if (controller == null)
            return;

        int width = controller.getWidth();
        if (width == 1 || controller.getHeight() >= controller.getMaxLength(Direction.Axis.Y, width))
            return;

        BlockPos startPos = face == Direction.DOWN
            ? controller.getBlockPos().below()
            : controller.getBlockPos().above(controller.getHeight());
        if (startPos.getY() != placedPos.getY())
            return;

        int blocksToPlace = 0;
        for (int xOffset = 0; xOffset < width; xOffset++) {
            for (int zOffset = 0; zOffset < width; zOffset++) {
                BlockPos targetPos = startPos.offset(xOffset, 0, zOffset);
                if (level.isOutsideBuildHeight(targetPos))
                    return;
                BlockState targetState = level.getBlockState(targetPos);
                if (IndustrialBlastFurnaceBlock.isFurnace(targetState))
                    continue;
                if (!targetState.canBeReplaced())
                    return;
                blocksToPlace++;
            }
        }

        ItemStack heldStack = context.getItemInHand();
        if (!player.isCreative() && heldStack.getCount() < blocksToPlace)
            return;

        for (int xOffset = 0; xOffset < width; xOffset++) {
            for (int zOffset = 0; zOffset < width; zOffset++) {
                BlockPos targetPos = startPos.offset(xOffset, 0, zOffset);
                if (IndustrialBlastFurnaceBlock.isFurnace(level.getBlockState(targetPos)))
                    continue;
                super.place(BlockPlaceContext.at(context, targetPos, face));
            }
        }
    }
}
