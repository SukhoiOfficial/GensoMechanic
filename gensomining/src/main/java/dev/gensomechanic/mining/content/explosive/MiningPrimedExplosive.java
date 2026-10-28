package dev.gensomechanic.mining.content.explosive;

import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.DimensionTransition;

/** Inherits TNT movement, fuse, smoke, tracked block state and native renderer. */
public final class MiningPrimedExplosive extends PrimedTnt {
    // Matches PrimedTnt's portal protection, which is private in Minecraft 1.21.1.
    private static final ExplosionDamageCalculator PORTAL_PROTECTION = new ExplosionDamageCalculator() {
        @Override public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
            return !state.is(Blocks.NETHER_PORTAL) && super.shouldBlockExplode(explosion, level, pos, state, power);
        }
        @Override public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos,
                                                                     BlockState state, FluidState fluid) {
            return state.is(Blocks.NETHER_PORTAL) ? Optional.empty() : super.getBlockExplosionResistance(explosion, level, pos, state, fluid);
        }
    };
    @Nullable private LivingEntity igniter;
    @Nullable private UUID igniterId;
    private boolean passedPortal;

    public MiningPrimedExplosive(EntityType<? extends MiningPrimedExplosive> type, Level level) {
        super(type, level);
        setBlockState(ExplosiveContent.CRUDE_EXPLOSIVE.getDefaultState());
    }

    public MiningPrimedExplosive(Level level, double x, double y, double z, BlockState state, @Nullable LivingEntity igniter) {
        this(ExplosiveContent.PRIMED_EXPLOSIVE.get(), level);
        setPos(x, y, z);
        double angle = level.random.nextDouble() * (float) (Math.PI * 2);
        setDeltaMovement(-Math.sin(angle) * 0.02, 0.2F, -Math.cos(angle) * 0.02);
        xo = x; yo = y; zo = z;
        setFuse(80);
        setBlockState(state);
        this.igniter = igniter;
        this.igniterId = igniter == null ? null : igniter.getUUID();
    }

    public float explosionPower() {
        return getBlockState().getBlock() instanceof MiningExplosiveBlock explosive ? explosive.explosionPower() : 2;
    }

    @Override protected void explode() {
        level().explode(this, Explosion.getDefaultDamageSource(level(), this), passedPortal ? PORTAL_PROTECTION : null,
                getX(), getY(0.0625), getZ(), explosionPower(), false, Level.ExplosionInteraction.TNT);
    }

    @Override protected Component getTypeName() { return getBlockState().getBlock().getName(); }

    @Override @Nullable public LivingEntity getOwner() {
        if (igniter == null && igniterId != null && level() instanceof ServerLevel server) {
            var entity = server.getEntity(igniterId);
            if (entity instanceof LivingEntity living) igniter = living;
        }
        return igniter;
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (igniterId != null) tag.putUUID("Igniter", igniterId);
        tag.putBoolean("PassedPortal", passedPortal);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        igniter = null;
        igniterId = tag.hasUUID("Igniter") ? tag.getUUID("Igniter") : null;
        passedPortal = tag.getBoolean("PassedPortal");
    }

    @Override public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof MiningPrimedExplosive explosive) {
            igniter = explosive.igniter;
            igniterId = explosive.igniterId;
            passedPortal = explosive.passedPortal;
        }
    }

    @Override @Nullable public Entity changeDimension(DimensionTransition transition) {
        var moved = super.changeDimension(transition);
        if (moved instanceof MiningPrimedExplosive explosive) explosive.passedPortal = true;
        return moved;
    }
}
