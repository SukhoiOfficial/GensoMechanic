// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.client.jei.category.animations;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlock;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlock.Shape;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlock.WindowConnection;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlockEntity;
import dev.gensomechanic.metallurgy.MetallurgyBlockEntities;
import dev.gensomechanic.metallurgy.MetallurgyBlocks;
import dev.gensomechanic.metallurgy.client.metallurgy.MetallurgyModels;
import dev.engine_room.flywheel.lib.model.baked.VirtualBlockGetter;
import net.createmod.catnip.client.render.model.BakedModelBufferer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class AnimatedIndustrialBlastFurnace extends AnimatedKinetics {
    private static final int STRUCTURE_HEIGHT = 3;
    private static final int SCALE = 23;
    private static final double FURNACE_BOTTOM_Y = 1.65;
    private static final double GAUGE_Z_OFFSET = 2 / 16d;
    private FurnaceRenderWorld renderWorld;
    private float gaugeProgress;

    public AnimatedIndustrialBlastFurnace withRecipe(BasinRecipe recipe) {
        HeatCondition requiredHeat = recipe.getRequiredHeat();
        HeatLevel heatLevel = requiredHeat.visualizeAsBlazeBurner();
        gaugeProgress = requiredHeat == HeatCondition.NONE ? 0
            : heatLevel == HeatLevel.SEETHING ? 1 : .6f;
        return this;
    }

    @Override
    public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(xOffset, yOffset, 200);
        poseStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
        poseStack.mulPose(Axis.YP.rotationDegrees(22.5f));

        renderFurnace(graphics);

        renderGauge(graphics);

        poseStack.popPose();
    }

    private void renderFurnace(GuiGraphics graphics) {
        FurnaceRenderWorld renderWorld = getRenderWorld();
        for (int y = 0; y < STRUCTURE_HEIGHT; y++) {
            BlockPos pos = new BlockPos(0, y, 0);
            BlockState state = renderWorld.getBlockState(pos);
            renderConnectedBlock(graphics, state, pos, FURNACE_BOTTOM_Y - y, renderWorld);
        }
    }

    private FurnaceRenderWorld getRenderWorld() {
        // JEI discovers plugin classes during mod construction, before Registrate entries
        // are guaranteed to exist. Build the connected render world only when JEI draws.
        if (renderWorld == null)
            renderWorld = new FurnaceRenderWorld();
        return renderWorld;
    }

    private void renderConnectedBlock(GuiGraphics graphics, BlockState state, BlockPos pos, double localY,
                                      FurnaceRenderWorld renderWorld) {
        // Keep the multi-block world for connected textures, but let Create's GUI builder
        // handle the same light directions, transforms and cleanup used by AnimatedMixer.
        new net.createmod.catnip.gui.element.GuiGameElement.GuiBlockStateRenderBuilder(state) {
            @Override
            protected void renderModel(net.minecraft.client.renderer.block.BlockRenderDispatcher dispatcher,
                    MultiBufferSource.BufferSource buffer, PoseStack poseStack) {
                BakedModelBufferer.bufferModel(blockModel, pos, renderWorld, state, poseStack,
                    (renderType, shaded) -> buffer.getBuffer(renderType == RenderType.translucent()
                        ? Sheets.translucentCullBlockSheet() : Sheets.cutoutBlockSheet()));
                buffer.endBatch();
            }
        }.lighting(DEFAULT_LIGHTING).atLocal(0, localY, 0).scale(SCALE).render(graphics);
    }

    private void renderGauge(GuiGraphics graphics) {
        if (MetallurgyModels.INDUSTRIAL_BLAST_FURNACE_GAUGE.get() == null
            || MetallurgyModels.INDUSTRIAL_BLAST_FURNACE_GAUGE_DIAL.get() == null)
            return;

        blockElement(MetallurgyModels.INDUSTRIAL_BLAST_FURNACE_GAUGE)
            .atLocal(0, FURNACE_BOTTOM_Y, GAUGE_Z_OFFSET)
            .rotateBlock(0, -90, 0)
            .scale(SCALE)
            .render(graphics);

        double dialAngle = 90 - 145 * gaugeProgress;
        blockElement(MetallurgyModels.INDUSTRIAL_BLAST_FURNACE_GAUGE_DIAL)
            .atLocal(0, FURNACE_BOTTOM_Y, GAUGE_Z_OFFSET)
            .withRotationOffset(new Vec3(.5, 6 / 16d, .5))
            .rotate(dialAngle, -90, 0)
            .scale(SCALE)
            .render(graphics);
    }



    public static BlockState furnaceState(int y) {
        boolean bottom = y == 0;
        boolean top = y == STRUCTURE_HEIGHT - 1;
        return MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.getDefaultState()
            .setValue(IndustrialBlastFurnaceBlock.BOTTOM, bottom)
            .setValue(IndustrialBlastFurnaceBlock.TOP, top)
            .setValue(IndustrialBlastFurnaceBlock.SHAPE, Shape.PLAIN)
            .setValue(IndustrialBlastFurnaceBlock.WINDOW, false)
            .setValue(IndustrialBlastFurnaceBlock.WINDOW_CONNECTION, WindowConnection.SINGLE);
    }

    /** Supplies real neighbours and a shared controller to Create's connected-texture model in JEI. */
    private static final class FurnaceRenderWorld extends VirtualBlockGetter {
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();

        private FurnaceRenderWorld() {
            super(pos -> 15, pos -> 15);
            for (int y = 0; y < STRUCTURE_HEIGHT; y++) {
                BlockPos pos = new BlockPos(0, y, 0);
                BlockState state = furnaceState(y);
                states.put(pos, state);

                IndustrialBlastFurnaceBlockEntity furnace =
                    MetallurgyBlockEntities.INDUSTRIAL_BLAST_FURNACE.get().create(pos, state);
                if (furnace != null) {
                    furnace.setWidth(1);
                    furnace.setHeight(STRUCTURE_HEIGHT);
                    if (y != 0)
                        furnace.setController(BlockPos.ZERO);
                    blockEntities.put(pos, furnace);
                }
            }
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return blockEntities.get(pos);
        }

        @Override
        public int getHeight() {
            return 16;
        }

        @Override
        public int getMinBuildHeight() {
            return -8;
        }
    }

}
