package dev.gensomechanic.mining.content.explosive;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Native TNT interactions dispatch through onCaughtFire; chain ignition has its own hook. */
public final class MiningExplosiveBlock extends TntBlock {
    public static final MapCodec<TntBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Codec.floatRange(0.1f, 16).fieldOf("explosion_power")
                    .forGetter(block -> ((MiningExplosiveBlock) block).explosionPower)
    ).apply(instance, MiningExplosiveBlock::new));
    private final float explosionPower;

    public MiningExplosiveBlock(Properties properties, float explosionPower) {
        super(properties);
        this.explosionPower = explosionPower;
    }

    @Override public MapCodec<TntBlock> codec() { return CODEC; }
    public float explosionPower() { return explosionPower; }

    @Override public void onCaughtFire(BlockState state, Level level, BlockPos pos,
                                      @Nullable Direction face, @Nullable LivingEntity igniter) {
        prime(level, pos, state, igniter, false);
    }

    @Override public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        prime(level, pos, defaultBlockState(), explosion.getIndirectSourceEntity(), true);
    }

    private static void prime(Level level, BlockPos pos, BlockState state,
                              @Nullable LivingEntity igniter, boolean chain) {
        if (level.isClientSide) return;
        var primed = new MiningPrimedExplosive(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, state, igniter);
        if (chain) primed.setFuse(level.random.nextInt(primed.getFuse() / 4) + primed.getFuse() / 8);
        level.addFreshEntity(primed);
        if (!chain) {
            level.playSound(null, primed.getX(), primed.getY(), primed.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1, 1);
            level.gameEvent(igniter, GameEvent.PRIME_FUSE, pos);
        }
    }

    @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 100; }
    @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 15; }
}
