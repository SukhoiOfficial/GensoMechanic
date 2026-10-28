package dev.gensomechanic.mining.content.miner;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class MinerPortBlock extends MechanicalMinerPartBlock implements IBE<MinerPortBlockEntity> {
    public MinerPortBlock(Properties properties) { super(properties); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(PART) != MinerStructure.OUTPUT || !player.getMainHandItem().isEmpty()
                || !player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var port = getBlockEntity(level, pos);
        var output = port == null ? null : port.items(port.face());
        if (output == null) return InteractionResult.PASS;
        // Share automation's validated handler and tray lock; extract before handing items to the player.
        for (int slot = 0; slot < output.getSlots(); slot++) {
            var extracted = output.extractItem(slot, Integer.MAX_VALUE, false);
            if (!extracted.isEmpty()) player.getInventory().placeItemBackInInventory(extracted);
        }
        return InteractionResult.CONSUME;
    }
    @Override public Class<MinerPortBlockEntity> getBlockEntityClass() { return MinerPortBlockEntity.class; }
    @Override public BlockEntityType<? extends MinerPortBlockEntity> getBlockEntityType() { return MiningContent.PORT_ENTITY.get(); }
}
