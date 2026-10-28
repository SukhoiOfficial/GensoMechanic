package dev.gensomechanic.mining.client.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import dev.gensomechanic.mining.content.miner.MiningContent;
import dev.gensomechanic.mining.client.GensoPartialModels;
import dev.gensomechanic.mining.client.MechanicalMinerRenderer;
import dev.gensomechanic.mining.client.MinerItemDisplay;
import dev.gensomechanic.mining.content.miner.MinerPresentation;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Create-clock demonstration only: no live block entity, inventory, sound or world side effects. */
public final class AnimatedMiner {
    public static float angle(float time) { return time % 20 * 18; } // 60 RPM * .3 degrees per tick.
    public static float cycle(float time) { return time % 60; }
    public static void draw(MiningDisplay page, GuiGraphics graphics, int x, int y) {
        drawAtTime(page, graphics, x, y, AnimationTickHolder.getRenderTime());
    }
    public static void drawAtTime(MiningDisplay page, GuiGraphics graphics, int x, int y, float time) {
        if (!GensoPartialModels.ready()) return;
        Minecraft mc = Minecraft.getInstance();
        var pose = graphics.pose();
        graphics.flush();
        pose.pushPose();
        try {
            // Preserve the supplied item's GUI pose and 4x footprint, but apply Z before scaling.
            // The old scaled GUI-item renderer also scaled its internal +100 Z to +400 (tooltip depth).
            applyGuiTransform(pose, x, y, mc.getItemRenderer().getModel(MiningContent.MECHANICAL_MINER.asStack(), mc.level, null, 0)
                    .getTransforms().getTransform(ItemDisplayContext.GUI));
            RenderSystem.enableDepthTest();
            AnimatedKinetics.DEFAULT_LIGHTING.applyLighting();
            var buffers = graphics.bufferSource();
            var state = MiningContent.MECHANICAL_MINER.getDefaultState();
            int light = LightTexture.FULL_BRIGHT, overlay = OverlayTexture.NO_OVERLAY;
            // Block shell excludes all five moving partials, so no stationary duplicate survives underneath.
            mc.getBlockRenderer().renderSingleBlock(state, pose, buffers, light, overlay);
            float rotation = angle(time), phase = cycle(time);
            MechanicalMinerRenderer.renderPart(GensoPartialModels.DRILL, state, pose, buffers, light, Axis.YP, rotation, .5, 0, .5);
            MechanicalMinerRenderer.renderPart(GensoPartialModels.SHAFT, state, pose, buffers, light, Axis.YP, rotation, .5, 0, .5);
            MechanicalMinerRenderer.renderPart(GensoPartialModels.FAN, state, pose, buffers, light, Axis.XP, -rotation, -.5, -.5, .5);
            float input = MinerPresentation.trayOffset(phase), output = MinerPresentation.trayOffset(phase - 30);
            MechanicalMinerRenderer.renderTray(GensoPartialModels.INPUT_TRAY, state, pose, buffers, light, -input);
            MechanicalMinerRenderer.renderTray(GensoPartialModels.OUTPUT_TRAY, state, pose, buffers, light, output);
            MechanicalMinerRenderer.renderFunnels(state, pose, buffers, light, phase, phase - 30);
            int loop = (int) (time / 60);
            var explosive = page.operation().explosive();
            if (explosive != null && (phase < 10 || phase >= 20)) {
                var choices = explosive.ingredient().getItems();
                if (choices.length > 0) MinerItemDisplay.render(mc.level, choices[Math.floorMod(loop, choices.length)], false,
                        25 / 16d - input, pose, buffers, light, overlay);
            }
            var results = page.orderedOutputs().stream().map(MiningDisplay.OutputView::output).filter(result -> result.chance() > 0).toList();
            if (!results.isEmpty() && (phase < 30 || phase >= 40)) {
                ItemStack shown = results.get(Math.floorMod(phase < 30 ? loop - 1 : loop, results.size())).stack();
                MinerItemDisplay.render(mc.level, shown, true, -9 / 16d + output, pose, buffers, light, overlay);
            }
            var lubricant = page.operation().lubricant();
            if (lubricant != null) {
                var choices = java.util.Arrays.asList(lubricant.fluid().getFluids());
                if (!choices.isEmpty()) {
                    var fluid = choices.get(Math.floorMod(loop, choices.size()));
                    boolean gas = fluid.getFluid().getFluidType().isLighterThanAir();
                    NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, 18.5f / 16, gas ? 6.5f / 16 : 2.5f / 16,
                            2.5f / 16, 29.5f / 16, gas ? 13.5f / 16 : 9.5f / 16, 13.5f / 16,
                            buffers, pose, light, false, true);
                }
            }
            graphics.flush();
        } finally {
            pose.popPose();
            Lighting.setupFor3DItems();
        }
    }
    public static void applyGuiTransform(com.mojang.blaze3d.vertex.PoseStack pose, int x, int y,
                                         net.minecraft.client.renderer.block.model.ItemTransform transform) {
        pose.translate(x + 32, y + 32, 100);
        // Like Create's GuiGameElement, flip screen geometry without turning lighting normals upside down.
        UIRenderHelper.flipForGuiRender(pose);
        pose.scale(64, 64, 64);
        transform.apply(false, pose);
        pose.translate(-.5, -.5, -.5);
    }
    private AnimatedMiner() {}
}
