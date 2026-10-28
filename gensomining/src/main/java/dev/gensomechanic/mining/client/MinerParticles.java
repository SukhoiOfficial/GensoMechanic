package dev.gensomechanic.mining.client;

import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.GensoParticles;
import dev.gensomechanic.mining.content.miner.MinerParticlePath;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.HugeExplosionParticle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = GensoMining.MOD_ID, value = Dist.CLIENT)
public final class MinerParticles {
    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(GensoParticles.MINER_AIR.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> new Air(level, x, y, z, dx, dy, dz, sprites));
    }
    static final class Air extends SimpleAnimatedParticle {
        private final Vec3 origin, direction;
        private final double distance;
        private double travelled;
        Air(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, sprites, level.random.nextFloat() * .5f);
            lifetime = 40; hasPhysics = false;
            // The velocity arguments encode the entire allowed travel vector, capped at two blocks.
            Vec3 path = new Vec3(dx, dy, dz);
            origin = new Vec3(x, y, z); direction = path.normalize(); distance = Math.min(2, path.length());
            // Native colour/opacity; 20% larger than Create's .75 scale for this short outlet.
            quadSize *= .9f; setColor(0xEEEEEE); setAlpha(.25f); setSprite(sprites.get(7, 8));
        }
        @Override public void tick() {
            xo = x; yo = y; zo = z;
            if (age++ >= lifetime || travelled >= distance) { remove(); return; }
            // The native fan starts quickly, then slows towards the end of its current.
            double next = Math.min(distance, travelled + (distance - travelled + 1) / 16);
            Vec3 end = origin.add(direction.scale(next));
            if (MinerParticlePath.clip(level, null, new Vec3(x, y, z), end).getType() != HitResult.Type.MISS) { remove(); return; }
            travelled = next;
            setPos(end.x, end.y, end.z);
            // Native fans start at a three-block reach. Compressing all their frames into <2 blocks made ours disappear.
            int frame = (int) (travelled / Math.max(3, distance) * 8) + random.nextInt(4);
            double progress = travelled / distance;
            if (progress > .85) frame = Math.max(frame, (int) ((progress - .85) / .15 * 7));
            frame = net.minecraft.util.Mth.clamp(frame, 0, 7);
            setSprite(sprites.get(frame, 8));
        }
        @Override public int getLightColor(float partialTick) {
            var pos = BlockPos.containing(x, y, z);
            return level.isLoaded(pos) ? net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos) : 0;
        }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    }
    /** Keeps vanilla block tint/atlas UVs, while escaping the drill's intentionally coarse collider. */
    static final class Chip extends TerrainParticle {
        private final BlockPos owner;
        Chip(ClientLevel level, BlockPos owner, BlockState state, Vec3 origin, Vec3 velocity) {
            super(level, origin.x, origin.y, origin.z, 0, 0, 0, state, owner.below(2));
            this.owner = owner.immutable();
            updateSprite(state, owner.below(2));
            xd = velocity.x; yd = velocity.y; zd = velocity.z;
            lifetime = 16 + random.nextInt(9); gravity = .75f; friction = .96f; hasPhysics = false;
            quadSize = (.035f + random.nextFloat() * .03f) * 1.5f;
        }
        @Override public void move(double dx, double dy, double dz) {
            Vec3 from = new Vec3(x, y, z), delta = new Vec3(dx, dy, dz);
            var hit = MinerParticlePath.clip(level, owner, from, from.add(delta));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 movement = hit.getLocation().subtract(from);
                movement = movement.scale(Math.max(0, movement.length() - .015) / Math.max(.00001, movement.length()));
                super.move(movement.x, movement.y, movement.z);
                xd = yd = zd = 0;
                gravity = 0;
                return;
            }
            super.move(dx, dy, dz);
        }
    }
    private MinerParticles() {}
}
