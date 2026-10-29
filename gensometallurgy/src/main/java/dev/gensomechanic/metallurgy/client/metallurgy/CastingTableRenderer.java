// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.client.metallurgy;

import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.*;
import dev.gensomechanic.metallurgy.content.casting.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public class CastingTableRenderer extends SafeBlockEntityRenderer<CastingTableBlockEntity> {
    private static final float RENDER_INSET = 1 / 128f;
    private static final float FLUID_MIN_X = 5 / 16f + RENDER_INSET;
    private static final float FLUID_MAX_X = 11 / 16f - RENDER_INSET;
    private static final float FLUID_MIN_Z = 2 / 16f + RENDER_INSET;
    private static final float FLUID_MAX_Z = 14 / 16f - RENDER_INSET;
    private static final float FLUID_MIN_Y = 6 / 16f + RENDER_INSET;
    private static final float FLUID_MAX_Y = 10 / 16f - RENDER_INSET;
    private static final float OUTPUT_SURFACE_Y = 10 / 16f;
    private static final float OUTPUT_SIZE = 8 / 16f;

    public CastingTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(CastingTableBlockEntity table, float partialTicks, PoseStack poseStack,
                              MultiBufferSource buffer, int light, int overlay) {
        renderFluid(table, partialTicks, poseStack, buffer, light);
        renderOutput(table, poseStack, buffer, light, overlay);
    }

    private void renderFluid(CastingTableBlockEntity table, float partialTicks, PoseStack poseStack,
                             MultiBufferSource buffer, int light) {
        TankSegment segment = table.getFluidTank().getPrimaryTank();
        if (segment.isEmpty(partialTicks))
            return;

        FluidStack fluid = segment.getRenderedFluid();
        float level = Math.max(0, Math.min(1, segment.getFluidLevel().getValue(partialTicks)));
        float yMax = FLUID_MIN_Y + (FLUID_MAX_Y - FLUID_MIN_Y) * level;
        if (yMax <= FLUID_MIN_Y)
            return;

        Direction facing = table.getBlockState().getValue(CastingTableBlock.FACING);
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        float xMin = alongX ? FLUID_MIN_Z : FLUID_MIN_X;
        float xMax = alongX ? FLUID_MAX_Z : FLUID_MAX_X;
        float zMin = alongX ? FLUID_MIN_X : FLUID_MIN_Z;
        float zMax = alongX ? FLUID_MAX_X : FLUID_MAX_Z;

        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid,
            xMin, FLUID_MIN_Y, zMin,
            xMax, yMax, zMax,
            buffer, poseStack, light, false, true);
    }

    private void renderOutput(CastingTableBlockEntity table, PoseStack poseStack,
                              MultiBufferSource buffer, int light, int overlay) {
        ItemStack output = table.getOutput();
        if (output.isEmpty())
            return;

        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        BakedModel model = itemRenderer.getModel(output, table.getLevel(), null, 0);
        boolean threeDimensional = model.isGui3d();

        poseStack.pushPose();
        float y = OUTPUT_SURFACE_Y + (threeDimensional ? OUTPUT_SIZE / 2 : RENDER_INSET);
        poseStack.translate(.5f, y, .5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(getYRotation(table.getBlockState()
            .getValue(CastingTableBlock.FACING))));
        poseStack.scale(OUTPUT_SIZE, OUTPUT_SIZE, OUTPUT_SIZE);
        if (!threeDimensional)
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
        itemRenderer.render(output, ItemDisplayContext.FIXED, false, poseStack, buffer, light, overlay, model);
        poseStack.popPose();
    }

    private float getYRotation(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }
}
