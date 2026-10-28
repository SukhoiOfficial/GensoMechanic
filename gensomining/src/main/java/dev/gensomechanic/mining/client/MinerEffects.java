package dev.gensomechanic.mining.client;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import dev.gensomechanic.mining.GensoParticles;
import dev.gensomechanic.mining.content.miner.MechanicalMinerBlockEntity;
import dev.gensomechanic.mining.content.miner.MinerPresentation;
import dev.gensomechanic.mining.content.miner.MinerParticlePath;
import dev.gensomechanic.mining.content.miner.MinerStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Local effects are triggered by synchronized server timestamps, never by rendering frequency. */
@net.neoforged.fml.common.EventBusSubscriber(modid = dev.gensomechanic.mining.GensoMining.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class MinerEffects {
    private static final WeakHashMap<MechanicalMinerBlockEntity, State> STATES = new WeakHashMap<>();
    private static ClientLevel world;
    private static long budgetTick = -1;
    private static int particles, burstParticles;
    private static double soundTokens = 4;
    @net.neoforged.bus.api.SubscribeEvent
    public static void disconnected(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear(); world = null; budgetTick = -1; particles = burstParticles = 0; soundTokens = 4;
    }
    private static final class State {
        long start, previousAge;
        State(long start, long age) { this.start = start; previousAge = age; }
    }
    public static void tick(MechanicalMinerBlockEntity be) {
        Minecraft mc = Minecraft.getInstance();
        if (!(be.getLevel() instanceof ClientLevel level) || mc.level != level || mc.isPaused()) return;
        long now = level.getGameTime();
        if (world != level) { STATES.clear(); world = level; budgetTick = now; particles = burstParticles = 0; soundTokens = 4; }
        if (budgetTick != now) { soundTokens = Math.min(4, soundTokens + Math.max(0, now - budgetTick) * .2); budgetTick = now; particles = burstParticles = 0; }
        long start = be.presentation().explosiveStart(), age = be.presentation().explosiveAge(now);
        State state = STATES.computeIfAbsent(be, key -> new State(start, age));
        boolean visible = be.assembled() && mc.cameraEntity != null && mc.cameraEntity.position().distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) <= 32 * 32;
        // Complete the previous event before accepting a newer timestamp. Fast recipes cannot swallow the blast.
        if (state.start >= 0 && state.previousAge < MinerPresentation.BLAST_TICK && now - state.start >= MinerPresentation.BLAST_TICK && visible) {
            Vec3 tip = tip(be);
            blast(mc, tip.add(0, .45, 0));
            if (takeSound()) level.playLocalSound(tip.x, tip.y, tip.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, .35f, 1.35f, false);
        }
        if (start != state.start) { state.start = start; state.previousAge = -1; }
        if (visible && age >= MinerPresentation.FUSE_START && age < MinerPresentation.BLAST_TICK && state.previousAge < MinerPresentation.FUSE_START && takeSound())
            mc.getSoundManager().play(new FuseSound(be, start));
        state.previousAge = age;
        if (!visible || be.getSpeed() == 0) return;
        double rpm = Math.min(1, Math.abs(be.getSpeed()) / 256d);
        if (level.random.nextDouble() < com.simibubi.create.infrastructure.config.AllConfigs.client().fanParticleDensity.get() && takeParticle(mc)) {
            Vec3 facing = Vec3.atLowerCornerOf(MinerStructure.facing(be.getBlockState()).getCounterClockWise().getNormal());
            Vec3 sideways = new Vec3(-facing.z, 0, facing.x);
            Vec3 origin = MinerParticlePath.exhaust(be.getBlockPos(), MinerStructure.facing(be.getBlockState()))
                    .add(sideways.scale((level.random.nextDouble() - .5) * .5)).add(0, (level.random.nextDouble() - .5) * .5, 0);
            // Including source clearance and the billboard radius, reach stays below two blocks from the outlet.
            double distance = .35 + 1.25 * rpm;
            Vec3 end = origin.add(facing.scale(distance));
            var hit = MinerParticlePath.clip(level, null, origin, end);
            if (hit.getType() != HitResult.Type.MISS) distance = Math.max(0, origin.distanceTo(hit.getLocation()) - .26);
            // takeParticle already applies the user's particle setting; avoid a second random rejection by LevelRenderer.
            if (distance > .05) mc.particleEngine.createParticle(GensoParticles.MINER_AIR.get(), origin.x, origin.y, origin.z,
                    facing.x * distance, facing.y * distance, facing.z * distance);
        }
        var block = be.displayState();
        if (be.status() == MechanicalMinerBlockEntity.Status.MINING && !block.isAir()) {
            double rate = .4 + 1.2 * rpm; // 8–32 chips/second before the shared client budget.
            int count = (int) rate + (level.random.nextDouble() < rate % 1 ? 1 : 0);
            for (int i = 0; i < count; i++) if (takeParticle(mc)) {
                double angle = level.random.nextDouble() * Math.PI * 2;
                Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 origin = tip(be).add(direction.scale(.18 + level.random.nextDouble() * .18)).add(0, .08, 0);
                Vec3 velocity = direction.scale((.045 + level.random.nextDouble() * .045) * 1.25).add(0, .12 + level.random.nextDouble() * .08, 0);
                mc.particleEngine.add(new MinerParticles.Chip(level, be.getBlockPos(), block, origin, velocity));
            }
        }
    }
    private static Vec3 tip(MechanicalMinerBlockEntity be) { return Vec3.atBottomCenterOf(be.getBlockPos()).add(0, -.93, 0); }
    private static boolean takeSound() { if (soundTokens < 1) return false; soundTokens--; return true; }
    private static boolean takeParticle(Minecraft mc) {
        if (particles >= 32) return false;
        int stride = switch (mc.options.particles().get()) { case ALL -> 1; case DECREASED -> 2; case MINIMAL -> 4; };
        if (world.random.nextInt(stride) != 0) return false;
        particles++;
        return true;
    }
    private static void blast(Minecraft mc, Vec3 origin) {
        var connection = mc.getConnection();
        var packet = blastPacket(origin);
        // Reserve whole six-particle bursts. Vanilla applies the user's particle setting exactly once.
        if (connection == null || burstParticles + packet.getCount() > 24) return;
        burstParticles += packet.getCount();
        // Locally use the same handler as /particle; no command execution or additional network traffic.
        connection.handleParticleEvent(packet);
    }
    static ClientboundLevelParticlesPacket blastPacket(Vec3 origin) {
        // Positive count uses vanilla Gaussian spread and velocities, including EXPLOSION's scale semantics.
        return new ClientboundLevelParticlesPacket(ParticleTypes.EXPLOSION, false,
                origin.x, origin.y, origin.z, .6f, .3f, .6f, .1f, 6);
    }
    private static final class FuseSound extends AbstractTickableSoundInstance {
        private final WeakReference<MechanicalMinerBlockEntity> owner;
        private final long start;
        FuseSound(MechanicalMinerBlockEntity be, long start) {
            super(SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, RandomSource.create());
            this.owner = new WeakReference<>(be); this.start = start;
            Vec3 point = Vec3.atCenterOf(be.getBlockPos()); x = point.x; y = point.y; z = point.z;
            volume = .18f; pitch = 1; looping = true; delay = 0;
        }
        @Override public void tick() {
            var be = owner.get(); var mc = Minecraft.getInstance();
            if (be == null || be.isRemoved() || !be.assembled() || be.getLevel() != mc.level || mc.level == null
                    || mc.level.getBlockEntity(be.getBlockPos()) != be || be.animationTime() - start >= MinerPresentation.BLAST_TICK) stop();
        }
    }
    private MinerEffects() {}
}
