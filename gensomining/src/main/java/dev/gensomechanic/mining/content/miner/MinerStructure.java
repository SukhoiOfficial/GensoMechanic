package dev.gensomechanic.mining.content.miner;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Local NORTH model coordinates: front -Z, front-view left +X, upper centre is the controller. */
public final class MinerStructure {
    public static final int CONTROLLER = 13;
    /** Upper front centre, outward normal = machine FACING. */
    public static final int OUTPUT = 10;
    public static final int LUBRICANT = 5;
    /** Upper tank: the outward side and top share the lower lubricant inlet's inventory. */
    public static final int LUBRICANT_TANK = 14;
    public static final int EXPLOSIVE = 16;
    private record Edit(Level level, BlockPos controller) {}
    private static final ThreadLocal<Set<Edit>> EDITS = ThreadLocal.withInitial(HashSet::new);

    /** Full occupied volume, including empty-looking frame cells, in all horizontal orientations. */
    public static AABB bounds(BlockPos controller) {
        return new AABB(controller).inflate(1, 0, 1).expandTowards(0, -1, 0);
    }

    public static int turns(Direction facing) {
        return switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
    }
    public static BlockPos offset(int index, Direction facing) {
        int x = index % 3 - 1, y = index / 9 - 1, z = index % 9 / 3 - 1;
        for (int i = 0; i < turns(facing); i++) { int oldX = x; x = -z; z = oldX; }
        return new BlockPos(x, y, z);
    }
    public static Direction facing(BlockState state) {
        return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
    }
    public static BlockState state(int index, Direction facing) {
        return index == CONTROLLER
                ? MiningContent.MECHANICAL_MINER.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                : (index == OUTPUT ? MiningContent.MINER_OUTPUT.getDefaultState()
                    : index == LUBRICANT || index == LUBRICANT_TANK || index == EXPLOSIVE ? MiningContent.MINER_INPUT.getDefaultState() : MiningContent.MINER_PART.getDefaultState())
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                    .setValue(MechanicalMinerPartBlock.PART, index);
    }
    public static BlockPos controller(BlockPos pos, BlockState state) {
        return state.getBlock() instanceof MechanicalMinerPartBlock
                ? pos.subtract(offset(state.getValue(MechanicalMinerPartBlock.PART), facing(state))) : pos;
    }
    public static boolean editing(Level level, BlockPos controller) {
        return EDITS.get().contains(new Edit(level, controller));
    }
    public static boolean loaded(Level level, BlockPos controller) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (!level.hasChunkAt(controller.offset(x, 0, z))) return false;
        return true;
    }
    public static boolean complete(Level level, BlockPos controller, Direction facing) {
        if (!loaded(level, controller)) return false;
        for (int i = 0; i < 18; i++) {
            BlockState actual = level.getBlockState(controller.offset(offset(i, facing)));
            if (!matches(i, facing, actual)) return false;
        }
        return true;
    }
    private static boolean matches(int index, Direction facing, BlockState actual) {
        return actual.equals(state(index, facing));
    }
    public static boolean canPlace(BlockPlaceContext context, BlockState mainState) {
        Level level = context.getLevel();
        BlockPos controller = context.getClickedPos();
        Direction facing = facing(mainState);
        Player player = context.getPlayer();
        if (!loaded(level, controller)) return false;
        for (int i = 0; i < 18; i++) {
            BlockPos pos = controller.offset(offset(i, facing));
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
            BlockState existing = level.getBlockState(pos);
            if (!existing.canBeReplaced() || existing.hasBlockEntity()) return false;
            if (player != null && (!level.mayInteract(player, pos)
                    || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) return false;
            if (!level.isUnobstructed(state(i, facing), pos, CollisionContext.empty())) return false;
        }
        return true;
    }
    public static boolean place(Level level, BlockPos controller, Direction facing) {
        if (!loaded(level, controller)) return false;
        Edit edit = new Edit(level, controller.immutable());
        if (!EDITS.get().add(edit)) return false;
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        boolean success = false;
        try {
            for (int i = 0; i < 18; i++) {
                BlockPos pos = controller.offset(offset(i, facing));
                previous.put(pos, level.getBlockState(pos));
                if (!level.getBlockState(pos).equals(state(i, facing)) && !level.setBlock(pos, state(i, facing), Block.UPDATE_ALL)) return false;
            }
            success = complete(level, controller, facing);
            return success;
        } finally {
            if (!success) previous.forEach((pos, original) -> level.setBlock(pos, original, Block.UPDATE_ALL));
            EDITS.get().remove(edit);
            for (int cell = 0; cell < 18; cell++) {
                BlockPos p = controller.offset(offset(cell, Direction.NORTH));
                if (level.hasChunkAt(p)) level.invalidateCapabilities(p);
            }
        }
    }
    /** Invoked from the controller's onRemove; unloaded parts self-clean when their scheduled tick resumes. */
    public static void removeParts(Level level, BlockPos controller, Direction facing) {
        Edit edit = new Edit(level, controller.immutable());
        if (!EDITS.get().add(edit)) return;
        try {
            for (int i = 0; i < 18; i++) {
                if (i == CONTROLLER) continue;
                BlockPos pos = controller.offset(offset(i, facing));
                if (level.hasChunkAt(pos) && matches(i, facing, level.getBlockState(pos)))
                    level.removeBlock(pos, false);
            }
        } finally { EDITS.get().remove(edit);
            for (int cell = 0; cell < 18; cell++) {
                BlockPos p = controller.offset(offset(cell, Direction.NORTH));
                if (level.hasChunkAt(p)) level.invalidateCapabilities(p);
            } }
    }
    public static void removeController(Level level, BlockPos pos, BlockState part, boolean drops) {
        BlockPos controller = controller(pos, part);
        if (level.isClientSide || editing(level, controller) || !level.hasChunkAt(controller)) return;
        BlockState main = level.getBlockState(controller);
        if (main.is(MiningContent.MECHANICAL_MINER.get()) && facing(main) == facing(part))
            level.destroyBlock(controller, drops);
    }
    public static boolean rotate(Level level, BlockPos controller, Direction before, Direction after) {
        if (!complete(level, controller, before)) return false;
        if (level.getBlockEntity(controller) instanceof MechanicalMinerBlockEntity miner) miner.revokePorts();
        for (int i = 0; i < 18; i++) {
            BlockPos pos = controller.offset(offset(i, after));
            if (!level.isUnobstructed(state(i, after), pos, CollisionContext.empty())) return false;
        }
        Edit edit = new Edit(level, controller.immutable());
        if (!EDITS.get().add(edit)) return false;
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        boolean success = false;
        try {
            for (int i = 0; i < 18; i++) {
                BlockPos pos = controller.offset(offset(i, after));
                previous.put(pos, level.getBlockState(pos));
                if (!level.getBlockState(pos).equals(state(i, after)) && !level.setBlock(pos, state(i, after), Block.UPDATE_ALL)) return false;
            }
            success = complete(level, controller, after);
            return success;
        } finally {
            if (!success) previous.forEach((pos, original) -> level.setBlock(pos, original, Block.UPDATE_ALL));
            EDITS.get().remove(edit);
            for (int cell = 0; cell < 18; cell++) {
                BlockPos p = controller.offset(offset(cell, Direction.NORTH));
                if (level.hasChunkAt(p)) level.invalidateCapabilities(p);
            }
        }
    }
    private MinerStructure() {}
}
