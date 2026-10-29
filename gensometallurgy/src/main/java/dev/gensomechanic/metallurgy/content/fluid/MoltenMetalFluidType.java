package dev.gensomechanic.metallurgy.content.fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
public final class MoltenMetalFluidType extends FluidType {
    public MoltenMetalFluidType(Properties properties) { super(properties); }
    @Override public boolean move(FluidState state, LivingEntity entity, Vec3 motion, double gravity) {
        entity.lavaHurt(); return false;
    }
}
