// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.client.metallurgy;

import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.*;
import dev.gensomechanic.metallurgy.content.casting.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.gensomechanic.metallurgy.client.metallurgy.MetallurgyModels;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public class IndustrialBlastFurnaceRenderer extends SafeBlockEntityRenderer<IndustrialBlastFurnaceBlockEntity> {

    private static final float HULL_INSET = 3 / 16f + 1 / 128f;
    private static final float CONTENTS_BOTTOM = 4 / 16f + 1 / 128f;
    private static final float CONTENTS_TOP_INSET = 3 / 16f;

    public IndustrialBlastFurnaceRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(IndustrialBlastFurnaceBlockEntity furnace, float partialTicks, PoseStack poseStack,
                              MultiBufferSource buffer, int light, int overlay) {
        renderLocalItem(furnace, poseStack, buffer, light, overlay);
        if (furnace.isController()) {
            renderFluid(furnace, poseStack, buffer, light);
            renderGauges(furnace, partialTicks, poseStack, buffer, light);
        }
    }

    private void renderGauges(IndustrialBlastFurnaceBlockEntity furnace, float partialTicks,
                              PoseStack poseStack, MultiBufferSource buffer, int light) {
        // A broken or unusually ordered resource reload must not make an otherwise
        // functional furnace crash the render thread. The models will be retried on
        // the next reload after RegisterAdditional has included them.
        if (MetallurgyModels.INDUSTRIAL_BLAST_FURNACE_GAUGE.get() == null
            || MetallurgyModels.INDUSTRIAL_BLAST_FURNACE_GAUGE_DIAL.get() == null)
            return;

        BlockState blockState = furnace.getBlockState();
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.cutout());
        float progress = Mth.clamp(furnace.getHeatGaugeValue(partialTicks), 0, 1);
        float dialPivotY = 6 / 16f;
        float dialPivotZ = 8 / 16f;

        poseStack.pushPose();
        TransformStack.of(poseStack).translate(furnace.getWidth() / 2f, .5f, furnace.getWidth() / 2f);

        for (Direction direction : Iterate.horizontalDirections) {
            poseStack.pushPose();
            float yRotation = -direction.toYRot() - 90;
            CachedBuffers.partial(MetallurgyModels.INDUSTRIAL_BLAST_FURNACE_GAUGE, blockState)
                .rotateYDegrees(yRotation)
                .uncenter()
                .translate(furnace.getWidth() / 2f - 6 / 16f, 0, 0)
                .light(light)
                .renderInto(poseStack, vertexConsumer);
            CachedBuffers.partial(MetallurgyModels.INDUSTRIAL_BLAST_FURNACE_GAUGE_DIAL, blockState)
                .rotateYDegrees(yRotation)
                .uncenter()
                .translate(furnace.getWidth() / 2f - 6 / 16f, 0, 0)
                .translate(0, dialPivotY, dialPivotZ)
                .rotateXDegrees(-145 * progress + 90)
                .translate(0, -dialPivotY, -dialPivotZ)
                .light(light)
                .renderInto(poseStack, vertexConsumer);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private void renderLocalItem(IndustrialBlastFurnaceBlockEntity furnace, PoseStack poseStack,
                                 MultiBufferSource buffer, int light, int overlay) {
        ItemStack stack = furnace.getLocalItem();
        if (stack.isEmpty())
            return;

        poseStack.pushPose();
        if (stack.getItem() instanceof BlockItem) {
            poseStack.translate(.5f, .1f, .5f);
            poseStack.scale(2.5f, 2.5f, 2.5f);
        } else {
            poseStack.translate(.5f, .5f, .5f);
        }

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, light, overlay,
            poseStack, buffer, minecraft.level, 0);
        poseStack.popPose();
    }

    private void renderFluid(IndustrialBlastFurnaceBlockEntity furnace, PoseStack poseStack,
                             MultiBufferSource buffer, int light) {
        List<FluidStack> fluids = furnace.getFluidInventory().getFluids();
        int capacity = furnace.getFluidInventory().getCapacity();
        if (fluids.isEmpty() || capacity <= 0)
            return;

        float availableHeight = furnace.getHeight() - CONTENTS_BOTTOM - CONTENTS_TOP_INSET;
        float horizontalMax = furnace.getWidth() - HULL_INSET;
        float usedHeight = 0;

        // Liquids build upward from the floor. Each distinct fluid occupies a layer.
        for (FluidStack fluid : fluids) {
            if (fluid.getFluid().getFluidType().isLighterThanAir())
                continue;
            float fluidHeight = getLayerHeight(fluid, capacity, availableHeight, usedHeight);
            if (fluidHeight <= 0)
                continue;
            float yMin = CONTENTS_BOTTOM + usedHeight;
            renderFluidLayer(fluid, yMin, fluidHeight, horizontalMax, buffer, poseStack, light);
            usedHeight += fluidHeight;
        }

        // Gases are always above liquids, but do not float down from the furnace roof.
        float gasHeight = 0;
        for (FluidStack fluid : fluids) {
            if (!fluid.getFluid().getFluidType().isLighterThanAir())
                continue;
            float fluidHeight = getLayerHeight(fluid, capacity, availableHeight, usedHeight + gasHeight);
            if (fluidHeight <= 0)
                continue;
            float yMin = CONTENTS_BOTTOM + usedHeight + gasHeight;
            renderFluidLayer(fluid, yMin, fluidHeight, horizontalMax, buffer, poseStack, light);
            gasHeight += fluidHeight;
        }
    }

    private float getLayerHeight(FluidStack fluid, int capacity, float availableHeight, float usedHeight) {
        float requestedHeight = (float) fluid.getAmount() / capacity * availableHeight;
        float height = Math.min(requestedHeight, Math.max(0, availableHeight - usedHeight));
        return height <= 1 / 512f ? 0 : height;
    }

    private void renderFluidLayer(FluidStack fluid, float yMin, float fluidHeight, float horizontalMax,
                                  MultiBufferSource buffer, PoseStack poseStack, int light) {
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid,
            HULL_INSET, yMin, HULL_INSET,
            horizontalMax, yMin + fluidHeight, horizontalMax,
            buffer, poseStack, light, false, true);
    }

    @Override
    public boolean shouldRenderOffScreen(IndustrialBlastFurnaceBlockEntity furnace) {
        return furnace.isController();
    }
}
