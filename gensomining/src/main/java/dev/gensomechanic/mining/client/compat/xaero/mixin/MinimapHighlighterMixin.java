package dev.gensomechanic.mining.client.compat.xaero.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.highlight.HighlighterRegistry;
import dev.gensomechanic.mining.client.compat.xaero.MinimapOverlay;
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class MinimapHighlighterMixin {
    @Inject(method = "end", at = @At("HEAD"))
    private void genso$register(CallbackInfo ci) { ((HighlighterRegistry)(Object)this).register(new MinimapOverlay()); }
}
