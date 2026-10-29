package dev.gensomechanic.metallurgy.content.industrial_blast_furnace;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import dev.gensomechanic.metallurgy.content.metallurgy.MetallurgyRecipeParams;
public abstract class IndustrialBlastFurnaceRecipe extends BasinRecipe {
    protected IndustrialBlastFurnaceRecipe(IRecipeTypeInfo type, MetallurgyRecipeParams params) { super(type, params); }
    @Override protected int getMaxInputCount() { return 81; }
    @Override protected int getMaxOutputCount() { return 81; }
    @Override protected int getMaxFluidInputCount() { return 9; }
    @Override protected int getMaxFluidOutputCount() { return 9; }
    @Override public java.util.List<String> validate() {
        var errors = super.validate();
        if (getIngredients().isEmpty() && getFluidIngredients().isEmpty()) errors.add("Missing inputs");
        if (getRollableResults().isEmpty() && getFluidResults().isEmpty()) errors.add("Missing outputs");
        for (var o : getRollableResults()) if (!Float.isFinite(o.getChance()) || o.getChance() < 0 || o.getChance() > 1) errors.add("Invalid chance");
        return errors;
    }
    @Override public java.util.List<net.minecraft.world.item.ItemStack> rollResults(net.minecraft.util.RandomSource random) {
        return getRollableResults().stream().filter(o -> o.getChance() > 0).map(o -> o.rollOutput(random)).filter(s -> !s.isEmpty()).toList();
    }
}
