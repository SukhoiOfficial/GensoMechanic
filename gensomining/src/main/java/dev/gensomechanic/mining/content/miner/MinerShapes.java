package dev.gensomechanic.mining.content.miner;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Coarse gameplay silhouettes, independent of mesh detail and animation. One or two boxes per cell. */
public final class MinerShapes {
    // NORTH, lower layer first; each 3x3 layer is ordered north to south, west to east.
    // All coordinates are local pixels (0..16), so no cell collides outside its own block.
    private static final VoxelShape[] NORTH = {
            // Lower feet, fan housing, gearbox/drill and fluid inlet.
            // Both trays are entirely in the upper layer; nothing hangs below them.
            Block.box(8, 0, 8, 16, 16, 16),
            Shapes.empty(),
            Block.box(0, 0, 8, 8, 16, 16),
            Shapes.block(),
            Shapes.or(Block.box(0, 10, 0, 16, 16, 16), Block.box(2, 0, 2, 14, 10, 14)),
            Shapes.or(Block.box(12, 0, 0, 16, 16, 16), Block.box(4, 4, 4, 12, 16, 12)),
            Block.box(8, 0, 0, 16, 16, 8),
            Shapes.empty(),
            Block.box(0, 0, 0, 8, 16, 8),
            // Upper posts and solid port envelopes (not their thin walls or moving trays).
            // Ignore the posts' 0.1-pixel caps and 1-pixel overlap into neighbouring cells.
            Block.box(9, 0, 9, 16, 16, 16),
            Block.box(1, 0, 0, 15, 14, 16),
            Block.box(0, 0, 9, 7, 16, 16),
            Block.box(1, 0, 0, 16, 16, 16),
            Shapes.block(),
            Block.box(0, 0, 1, 15, 16, 15),
            Block.box(9, 0, 0, 16, 16, 7),
            Block.box(1, 0, 0, 15, 14, 16),
            Block.box(0, 0, 0, 7, 16, 7)
    };
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][18];

    static {
        SHAPES[0] = NORTH;
        for (int turn = 1; turn < 4; turn++) {
            for (int cell = 0; cell < 18; cell++) {
                VoxelShape rotated = Shapes.empty();
                for (var box : SHAPES[turn - 1][cell].toAabbs()) {
                    rotated = Shapes.or(rotated, Shapes.box(1 - box.maxZ, box.minY, box.minX,
                            1 - box.minZ, box.maxY, box.maxX));
                }
                SHAPES[turn][cell] = rotated.optimize();
            }
        }
    }

    public static VoxelShape get(int part, Direction facing) {
        return SHAPES[MinerStructure.turns(facing)][part];
    }

    private MinerShapes() {}
}
