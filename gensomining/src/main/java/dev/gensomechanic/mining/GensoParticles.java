package dev.gensomechanic.mining;

import net.minecraft.core.particles.*;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class GensoParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, GensoMining.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MINER_AIR = TYPES.register("miner_air", () -> new SimpleParticleType(false) {});
    public static void register(IEventBus bus) { TYPES.register(bus); }
    private GensoParticles() {}
}
