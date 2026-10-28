package dev.gensomechanic.mining.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.gensomechanic.mining.content.miner.MechanicalMinerBlockEntity;
import dev.gensomechanic.mining.content.miner.MinerStructure;
import dev.gensomechanic.mining.content.miner.MinerPresentation;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/** Fluid, trays and item presentation plus the ordinary-rendering fallback for native kinetic visuals. */
public final class MechanicalMinerRenderer extends SafeBlockEntityRenderer<MechanicalMinerBlockEntity> {
    public MechanicalMinerRenderer(BlockEntityRendererProvider.Context context) {}
    @Override protected void renderSafe(MechanicalMinerBlockEntity be, float partialTick, PoseStack pose,
                                         MultiBufferSource buffers, int light, int overlay) {
        // A resource reload may temporarily have no baked model. Never hand null to CachedBuffers.
        if (!GensoPartialModels.ready()) return;
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-90 * MinerStructure.turns(MinerStructure.facing(be.getBlockState()))));
        pose.translate(-.5, 0, -.5);
        var tank = be.lubricantTank().getPrimaryTank();
        var fluid = tank.getRenderedFluid();
        float fill = Math.max(0, Math.min(1, tank.getFluidLevel().getValue(partialTick)));
        if (!fluid.isEmpty() && fill > 0) {
            // Upper-left spout tank, inset from the user's glass surfaces.
            float bottom = 2.5f / 16, top = 13.5f / 16, height = (top - bottom) * fill;
            boolean gas = fluid.getFluid().getFluidType().isLighterThanAir();
            NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, 18.5f / 16, gas ? top - height : bottom,
                    2.5f / 16, 29.5f / 16, gas ? top : bottom + height, 13.5f / 16,
                    buffers, pose, light, false, true);
        }
        if (!dev.engine_room.flywheel.api.visualization.VisualizationManager.supportsVisualization(be.getLevel())) {
        float angle = (float) Math.toDegrees(com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), net.minecraft.core.Direction.Axis.Y));
        renderPart(GensoPartialModels.DRILL, be.getBlockState(), pose, buffers, light, Axis.YP, angle, .5, 0, .5);
        renderPart(GensoPartialModels.SHAFT, be.getBlockState(), pose, buffers, light, Axis.YP, angle, .5, 0, .5);
        renderPart(GensoPartialModels.FAN, be.getBlockState(), pose, buffers, light, Axis.XP, -angle, -.5, -.5, .5);
        }
        long now = be.animationTime();
        float inputAge = be.presentation().explosiveAge(now) + partialTick;
        float outputAge = be.presentation().outputAge(now) + partialTick;
        float inputOffset = MinerPresentation.trayOffset(inputAge), outputOffset = MinerPresentation.trayOffset(outputAge);
        renderTray(GensoPartialModels.INPUT_TRAY, be.getBlockState(), pose, buffers, light, -inputOffset);
        renderTray(GensoPartialModels.OUTPUT_TRAY, be.getBlockState(), pose, buffers, light, outputOffset);
        renderFunnels(be.getBlockState(), pose, buffers, light, inputAge, outputAge);
        var input = inputAge < 10 ? be.presentation().consumedItem() : inputAge < 20 ? net.minecraft.world.item.ItemStack.EMPTY : be.explosiveStack();
        MinerItemDisplay.render(be, input, false, 25 / 16d - inputOffset, pose, buffers, light, overlay);
        if (outputAge >= 10) MinerItemDisplay.render(be, MinerItemDisplay.output(be), true, -9 / 16d + outputOffset, pose, buffers, light, overlay);
        MinerRubble.render(be, pose, buffers, light, overlay);
        pose.popPose();
    }
    public static void renderTray(PartialModel part, BlockState state, PoseStack pose, MultiBufferSource buffers, int light, double z) {
        pose.pushPose(); pose.translate(0, 0, z);
        CachedBuffers.partial(part, state).light(light).renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
        pose.popPose();
    }
    public static void renderFunnels(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, float inputAge, float outputAge) {
        // Only the two switchable faces are rendered here. The other four remain in the baked shell.
        renderTray(MinerPresentation.funnelOpen(inputAge) ? GensoPartialModels.INPUT_FUNNEL_OPEN : GensoPartialModels.INPUT_FUNNEL_CLOSED,
                state, pose, buffers, light, 0);
        renderTray(MinerPresentation.funnelOpen(outputAge) ? GensoPartialModels.OUTPUT_FUNNEL_OPEN : GensoPartialModels.OUTPUT_FUNNEL_CLOSED,
                state, pose, buffers, light, 0);
    }
    public static void renderPart(PartialModel part, BlockState state, PoseStack pose, MultiBufferSource buffers,
                                   int light, Axis axis, float angle, double x, double y, double z) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(axis.rotationDegrees(angle));
        pose.translate(-x, -y, -z);
        CachedBuffers.partial(part, state).light(light).renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(MechanicalMinerBlockEntity be) { return true; }
}
