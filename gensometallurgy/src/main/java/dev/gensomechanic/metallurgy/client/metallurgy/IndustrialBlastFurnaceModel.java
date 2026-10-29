// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.client.metallurgy;

import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.*;
import dev.gensomechanic.metallurgy.content.casting.*;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.block.connected.CTModel;
import dev.gensomechanic.metallurgy.client.metallurgy.MetallurgySpriteShifts;
import net.createmod.catnip.data.Iterate;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class IndustrialBlastFurnaceModel extends CTModel {
    public static com.tterrag.registrate.util.nullness.NonNullFunction<BakedModel, ? extends BakedModel> factory() {
        return IndustrialBlastFurnaceModel::new;
    }

    private static final ModelProperty<EnumSet<Direction>> CULLED_FACES = new ModelProperty<>();

    public IndustrialBlastFurnaceModel(BakedModel originalModel) {
        super(originalModel, new IndustrialBlastFurnaceCTBehaviour(
            MetallurgySpriteShifts.INDUSTRIAL_BLAST_FURNACE_CASING,
            MetallurgySpriteShifts.INDUSTRIAL_BLAST_FURNACE_TOP,
            MetallurgySpriteShifts.INDUSTRIAL_BLAST_FURNACE_INNER));
    }

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter level,
                                                BlockPos pos, BlockState state, ModelData blockEntityData) {
        super.gatherModelData(builder, level, pos, state, blockEntityData);
        EnumSet<Direction> culled = EnumSet.noneOf(Direction.class);
        for (Direction direction : Iterate.horizontalDirections) {
            boolean connected = ConnectivityHandler.isConnected(level, pos, pos.relative(direction));
            if ((connected && !state.getValue(IndustrialBlastFurnaceBlock.TOP))
                || state.getValue(IndustrialBlastFurnaceBlock.SHAPE) == IndustrialBlastFurnaceBlock.Shape.INNER)
                culled.add(direction);
        }
        return builder.with(CULLED_FACES, culled);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                                    ModelData modelData, RenderType renderType) {
        if (side != null)
            return Collections.emptyList();

        EnumSet<Direction> culled = modelData.has(CULLED_FACES)
            ? modelData.get(CULLED_FACES)
            : EnumSet.noneOf(Direction.class);
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction direction : Iterate.directions) {
            if (!culled.contains(direction))
                quads.addAll(super.getQuads(state, direction, random, modelData, renderType));
        }
        quads.addAll(super.getQuads(state, null, random, modelData, renderType));
        return quads;
    }
}
