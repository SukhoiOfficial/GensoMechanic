package dev.gensomechanic.metallurgy.content.casting;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import dev.gensomechanic.metallurgy.content.metallurgy.*;
public final class CastingRecipe extends BasinRecipe {
    public CastingRecipe(MetallurgyRecipeParams params) { super(MetallurgyRecipeTypes.CASTING, params); }
    @Override protected int getMaxInputCount() { return 0; }
    @Override protected int getMaxOutputCount() { return 1; }
    @Override protected int getMaxFluidInputCount() { return 1; }
    @Override protected int getMaxFluidOutputCount() { return 0; }
    @Override protected boolean canRequireHeat() { return false; }
    @Override public java.util.List<net.minecraft.world.item.ItemStack> rollResults(net.minecraft.util.RandomSource random) {
        return getRollableResults().stream().filter(o -> o.getChance() > 0).map(o -> o.rollOutput(random)).filter(s -> !s.isEmpty()).toList();
    }
}
