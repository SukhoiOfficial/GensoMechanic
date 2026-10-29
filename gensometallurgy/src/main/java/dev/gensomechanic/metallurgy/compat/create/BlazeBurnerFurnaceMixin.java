package dev.gensomechanic.metallurgy.compat.create;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import dev.gensomechanic.metallurgy.MetallurgyBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extend Create's shared vanilla/Flywheel animation predicate only for our furnace. */
@Mixin(value = BlazeBurnerBlockEntity.class, remap = false)
public abstract class BlazeBurnerFurnaceMixin {
    @Inject(method = "isValidBlockAbove", at = @At("RETURN"), cancellable = true)
    private void genso$furnaceAbove(CallbackInfoReturnable<Boolean> cir) {
        var burner = (BlazeBurnerBlockEntity) (Object) this;
        if (!cir.getReturnValueZ() && !burner.isVirtual() && burner.getLevel() != null
                && burner.getLevel().getBlockState(burner.getBlockPos().above()).is(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.get()))
            cir.setReturnValue(true);
    }
}
