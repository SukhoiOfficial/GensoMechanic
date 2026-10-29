// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.client.metallurgy;

import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.*;
import dev.gensomechanic.metallurgy.content.casting.*;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class IndustrialBlastFurnaceCTBehaviour extends ConnectedTextureBehaviour.Base {
    private final CTSpriteShiftEntry casingShift;
    private final CTSpriteShiftEntry topShift;
    private final CTSpriteShiftEntry innerShift;

    public IndustrialBlastFurnaceCTBehaviour(CTSpriteShiftEntry casingShift,
                                           CTSpriteShiftEntry topShift, CTSpriteShiftEntry innerShift) {
        this.casingShift = casingShift;
        this.topShift = topShift;
        this.innerShift = innerShift;
    }

    @Override
    public CTSpriteShiftEntry getShift(BlockState state, Direction direction,
                                       @Nullable TextureAtlasSprite sprite) {
        if (sprite != null && direction.getAxis() == Direction.Axis.Y && innerShift.getOriginal() == sprite)
            return innerShift;
        return direction.getAxis().isHorizontal() ? casingShift : topShift;
    }

    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter level,
                              BlockPos pos, BlockPos otherPos, Direction face) {
        boolean sameStructure = state.getBlock() == other.getBlock()
            && ConnectivityHandler.isConnected(level, pos, otherPos);
        return sameStructure;
    }
}
