// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.content.industrial_blast_furnace;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.gensomechanic.metallurgy.MetallurgyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IndustrialBlastFurnaceBlock extends Block
    implements IWrenchable, IBE<IndustrialBlastFurnaceBlockEntity> {

    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
    public static final BooleanProperty WINDOW = BooleanProperty.create("window");
    public static final EnumProperty<WindowConnection> WINDOW_CONNECTION =
        EnumProperty.create("window_connection", WindowConnection.class);

    private static final VoxelShape FLOOR = box(0, 0, 0, 16, 4, 16);
    private static final VoxelShape NORTH_WALL = box(0, 0, 0, 16, 16, 3);
    private static final VoxelShape EAST_WALL = box(13, 0, 0, 16, 16, 16);
    private static final VoxelShape SOUTH_WALL = box(0, 0, 13, 16, 16, 16);
    private static final VoxelShape WEST_WALL = box(0, 0, 0, 3, 16, 16);
    private static final VoxelShape SINGLE = Shapes.or(NORTH_WALL, EAST_WALL, SOUTH_WALL, WEST_WALL);

    public IndustrialBlastFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(TOP, true)
            .setValue(BOTTOM, true)
            .setValue(SHAPE, Shape.PLAIN)
            .setValue(WINDOW, false)
            .setValue(WINDOW_CONNECTION, WindowConnection.SINGLE));
    }

    public static boolean isFurnace(BlockState state) {
        return state.getBlock() instanceof IndustrialBlastFurnaceBlock;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (oldState.getBlock() == state.getBlock() || moved)
            return;
        withBlockEntityDo(level, pos, IndustrialBlastFurnaceBlockEntity::updateConnectivity);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP, BOTTOM, SHAPE, WINDOW, WINDOW_CONNECTION);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (state.getValue(SHAPE) == Shape.INNER)
            return InteractionResult.PASS;

        Level level = context.getLevel();
        if (state.getValue(BOTTOM))
            return InteractionResult.sidedSuccess(level.isClientSide);

        BlockPos pos = context.getClickedPos();
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        withBlockEntityDo(level, pos, IndustrialBlastFurnaceBlockEntity::toggleLayerWindows);
        level.playSound(null, pos, SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 0.75f,
            0.9f + level.random.nextFloat() * 0.2f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        return state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shell = switch (state.getValue(SHAPE)) {
            case PLAIN -> SINGLE;
            case INNER -> Shapes.empty();
            case NORTH -> NORTH_WALL;
            case EAST -> EAST_WALL;
            case SOUTH -> SOUTH_WALL;
            case WEST -> WEST_WALL;
            case NW -> Shapes.or(NORTH_WALL, WEST_WALL);
            case NE -> Shapes.or(NORTH_WALL, EAST_WALL);
            case SE -> Shapes.or(SOUTH_WALL, EAST_WALL);
            case SW -> Shapes.or(SOUTH_WALL, WEST_WALL);
        };
        return state.getValue(BOTTOM) ? Shapes.or(shell, FLOOR) : shell;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide || !(entity instanceof ItemEntity itemEntity) || !itemEntity.isAlive())
            return;
        withBlockEntityDo(level, pos, furnace -> furnace.tryCollectItem(itemEntity));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.hasBlockEntity() && (state.getBlock() != newState.getBlock() || !newState.hasBlockEntity())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof IndustrialBlastFurnaceBlockEntity furnace) {
                if (!level.isClientSide && !moving) {
                    furnace.prepareFluidStorageForRemoval();
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), furnace.removeLocalItem());
                }
                level.removeBlockEntity(pos);
                ConnectivityHandler.splitMulti(furnace);
            }
        }
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE)
            return state;
        boolean mirrorX = mirror == Mirror.FRONT_BACK;
        return switch (state.getValue(SHAPE)) {
            case NE -> state.setValue(SHAPE, mirrorX ? Shape.NW : Shape.SE);
            case NW -> state.setValue(SHAPE, mirrorX ? Shape.NE : Shape.SW);
            case SE -> state.setValue(SHAPE, mirrorX ? Shape.SW : Shape.NE);
            case SW -> state.setValue(SHAPE, mirrorX ? Shape.SE : Shape.NW);
            default -> state;
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        for (int i = 0; i < rotation.ordinal(); i++)
            state = rotateOnce(state);
        return state;
    }

    private BlockState rotateOnce(BlockState state) {
        return switch (state.getValue(SHAPE)) {
            case NORTH -> state.setValue(SHAPE, Shape.EAST);
            case EAST -> state.setValue(SHAPE, Shape.SOUTH);
            case SOUTH -> state.setValue(SHAPE, Shape.WEST);
            case WEST -> state.setValue(SHAPE, Shape.NORTH);
            case NW -> state.setValue(SHAPE, Shape.NE);
            case NE -> state.setValue(SHAPE, Shape.SE);
            case SE -> state.setValue(SHAPE, Shape.SW);
            case SW -> state.setValue(SHAPE, Shape.NW);
            default -> state;
        };
    }

    @Override
    public Class<IndustrialBlastFurnaceBlockEntity> getBlockEntityClass() {
        return IndustrialBlastFurnaceBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends IndustrialBlastFurnaceBlockEntity> getBlockEntityType() {
        return MetallurgyBlockEntities.INDUSTRIAL_BLAST_FURNACE.get();
    }

    public enum Shape implements StringRepresentable {
        PLAIN,
        INNER,
        NW,
        SW,
        NE,
        SE,
        NORTH,
        SOUTH,
        WEST,
        EAST;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public boolean isWall() {
            return this == NORTH || this == SOUTH || this == WEST || this == EAST;
        }

        public boolean isCorner() {
            return this == NW || this == SW || this == NE || this == SE;
        }
    }

    public enum WindowConnection implements StringRepresentable {
        SINGLE,
        BOTTOM,
        MIDDLE,
        TOP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
