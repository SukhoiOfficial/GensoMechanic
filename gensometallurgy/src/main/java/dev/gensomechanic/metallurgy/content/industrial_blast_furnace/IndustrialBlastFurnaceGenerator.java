// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.content.industrial_blast_furnace;

import com.simibubi.create.foundation.data.AssetLookup;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;

import java.util.EnumSet;
import java.util.List;

public class IndustrialBlastFurnaceGenerator {
    private static final String MODEL_PATH = "block/industrial_blast_furnace/";
    private static final String TEXTURE_PATH = "block/industrial_blast_furnace/";
    private static final float WINDOW_SILL_Z_FIGHTING_OFFSET = .01f;

    public <T extends Block> void generate(DataGenContext<Block, T> context,
                                           RegistrateBlockstateProvider provider) {
        provider.getVariantBuilder(context.getEntry())
            .forAllStates(state -> {
                int yRotation = getYRotation(state);
                return ConfiguredModel.builder()
                    .modelFile(getModel(context, provider, state))
                    .rotationY(yRotation)
                    .uvLock(yRotation != 0)
                    .build();
            });
    }

    private int getYRotation(BlockState state) {
        // Window models are generated in their final world orientation so half-windows in a 2x2
        // structure join without rotating or mirroring their texture coordinates.
        if (state.getValue(IndustrialBlastFurnaceBlock.WINDOW))
            return 0;
        if (!state.getValue(IndustrialBlastFurnaceBlock.TOP))
            return 0;

        return switch (state.getValue(IndustrialBlastFurnaceBlock.SHAPE)) {
            case EAST, NE -> 90;
            case SOUTH, SE -> 180;
            case WEST, SW -> 270;
            default -> 0;
        };
    }

    public <T extends Block> ModelFile getModel(DataGenContext<Block, T> context,
                                                RegistrateBlockstateProvider provider,
                                                BlockState state) {
        boolean top = state.getValue(IndustrialBlastFurnaceBlock.TOP);
        boolean bottom = state.getValue(IndustrialBlastFurnaceBlock.BOTTOM);
        boolean window = state.getValue(IndustrialBlastFurnaceBlock.WINDOW);
        IndustrialBlastFurnaceBlock.Shape shape = state.getValue(IndustrialBlastFurnaceBlock.SHAPE);

        if (window && shape != IndustrialBlastFurnaceBlock.Shape.INNER)
            return getWindowModel(provider, top, bottom, shape,
                state.getValue(IndustrialBlastFurnaceBlock.WINDOW_CONNECTION));

        String layer = top && bottom && shape != IndustrialBlastFurnaceBlock.Shape.INNER
            ? "single"
            : top && shape != IndustrialBlastFurnaceBlock.Shape.INNER
                ? "top"
                : bottom ? "bottom" : "middle";
        String modelName = layer;
        if ((layer.equals("top") || layer.equals("single")) && shape.isWall())
            modelName += "_edge";
        else if ((layer.equals("top") || layer.equals("single")) && shape.isCorner())
            modelName += "_corner";

        return AssetLookup.partialBaseModel(context, provider, modelName);
    }

    private ModelFile getWindowModel(RegistrateBlockstateProvider provider, boolean top, boolean bottom,
                                     IndustrialBlastFurnaceBlock.Shape shape,
                                     IndustrialBlastFurnaceBlock.WindowConnection connection) {
        String layer = top && bottom ? "single" : top ? "top" : bottom ? "bottom" : "middle";
        String modelName = "block_" + layer + "_window_" + shape.getSerializedName()
            + "_" + connection.getSerializedName();

        return provider.models().getExistingFile(provider.modLoc(MODEL_PATH + modelName));
    }
}
