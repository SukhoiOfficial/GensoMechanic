// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.client.jei.category.animations;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Axis;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import dev.gensomechanic.metallurgy.MetallurgyBlocks;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.fluids.FluidStack;

public final class AnimatedCastingTable extends AnimatedKinetics {
    private static final int SCALE = 22;
    private static final float RENDER_INSET = 1 / 128f;
    private static final float FLUID_MIN_X = 5 / 16f + RENDER_INSET;
    private static final float FLUID_MAX_X = 11 / 16f - RENDER_INSET;
    private static final float FLUID_MIN_Z = 2 / 16f + RENDER_INSET;
    private static final float FLUID_MAX_Z = 14 / 16f - RENDER_INSET;
    private static final float FLUID_MIN_Y = 6 / 16f + RENDER_INSET;
    private static final float FLUID_MAX_Y = 10 / 16f - RENDER_INSET;

    private FluidStack fluid = FluidStack.EMPTY;

    public AnimatedCastingTable withFluid(FluidStack fluid) {
        this.fluid = fluid.copy();
        return this;
    }

    @Override
    public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(xOffset, yOffset, 0);
        AllGuiTextures.JEI_SHADOW.render(graphics, -16, 13);
        poseStack.translate(-2, 18, 0);

        blockElement(MetallurgyBlocks.CASTING_TABLE.getDefaultState())
            .rotateBlock(22.5, 22.5, 0)
            .scale(SCALE)
            .render(graphics);
        renderFluid(graphics);

        poseStack.popPose();
    }

    private void renderFluid(GuiGraphics graphics) {
        if (fluid.isEmpty())
            return;

        PoseStack poseStack = graphics.pose();
        graphics.flush();
        AnimatedKinetics.DEFAULT_LIGHTING.applyLighting();
        MultiBufferSource.BufferSource buffer = graphics.bufferSource();
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        UIRenderHelper.flipForGuiRender(poseStack);
        poseStack.translate(.5, .5, .5);
        poseStack.mulPose(Axis.XP.rotationDegrees(22.5f));
        poseStack.mulPose(Axis.YP.rotationDegrees(22.5f));
        poseStack.translate(-.5, -.5, -.5);
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid,
            FLUID_MIN_X, FLUID_MIN_Y, FLUID_MIN_Z,
            FLUID_MAX_X, FLUID_MAX_Y, FLUID_MAX_Z,
            buffer, poseStack, LightTexture.FULL_BRIGHT, false, true);
        buffer.endBatch();
        poseStack.popPose();
        Lighting.setupFor3DItems();
    }
}
