package dev.gensomechanic.mining.client.compat.xaero.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.over.MinimapElementOverMapRendererHandler;
import dev.gensomechanic.mining.client.compat.xaero.ScannerMapLabels;
@Mixin(value = MinimapElementOverMapRendererHandler.class, remap = false)
public abstract class MinimapLabelsMixin {
    @Shadow private double ps;
    @Shadow private double pc;
    @Shadow private double zoom;
    @Shadow private int halfViewW;
    @Shadow private int halfViewH;
    @Shadow private boolean circle;
    @Shadow private float optionalScale;
    @Inject(method = "beforeRender", at = @At("HEAD"))
    private void genso$labels(GuiGraphics graphics, MinimapElementRenderInfo info, BufferSource buffers, CallbackInfo ci) {
        var pose = graphics.pose(); pose.pushPose();
        try {
            pose.translate(0, 0, -0.1);
            ScannerMapLabels.draw(graphics, info.mapDimension, info.renderPos.x, info.renderPos.z,
                    ps, pc, zoom, halfViewW, halfViewH, circle, optionalScale * 0.5f, true);
        } finally { pose.popPose(); }
    }
}
