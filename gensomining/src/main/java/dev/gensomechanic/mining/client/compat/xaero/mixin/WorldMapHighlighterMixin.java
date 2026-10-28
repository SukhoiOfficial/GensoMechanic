package dev.gensomechanic.mining.client.compat.xaero.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.HighlighterRegistry;
import dev.gensomechanic.mining.client.compat.xaero.WorldMapOverlay;
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class WorldMapHighlighterMixin {
    @Inject(method = "end", at = @At("HEAD"))
    private void genso$register(CallbackInfo ci) { ((HighlighterRegistry)(Object)this).register(new WorldMapOverlay()); }
}
