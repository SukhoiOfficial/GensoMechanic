package dev.gensomechanic.mining.client.compat.xaero.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import xaero.map.WorldMapSession;
import xaero.map.element.*;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.map.gui.GuiMap;
import dev.gensomechanic.mining.client.compat.xaero.ScannerMapLabels;
@Mixin(value = MapElementRenderHandler.class, remap = false)
public abstract class WorldMapLabelsMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void genso$labels(GuiMap screen, GuiGraphics graphics, BufferSource buffers, MultiTextureRenderTypeRendererProvider renderers,
            double cameraX, double cameraZ, int width, int height, double textScale, double scale, double dimensionScale,
            double mouseX, double mouseZ, float brightness, boolean cave, HoveredMapElementHolder<?, ?> hovered,
            Minecraft mc, float partialTicks, CallbackInfoReturnable<HoveredMapElementHolder<?, ?>> cir) {
        var session = WorldMapSession.getCurrentSession();
        if (session == null || scale <= 0) return;
        var dim = session.getMapProcessor().getMapWorld().getCurrentDimension();
        var pose = graphics.pose(); pose.pushPose();
        try {
            // Same framebuffer-pixel coordinate system as native map elements, below waypoints.
            pose.translate(0, 0, -981); pose.scale((float)(1 / scale), (float)(1 / scale), 1);
            ScannerMapLabels.draw(graphics, dim.getDimId(), cameraX, cameraZ, 1, 0, scale, width / 2d, height / 2d, false, (float)(textScale * 1.5), false);
        } finally { pose.popPose(); }
    }
}
