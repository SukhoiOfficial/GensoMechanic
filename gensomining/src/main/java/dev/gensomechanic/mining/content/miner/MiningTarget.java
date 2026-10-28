package dev.gensomechanic.mining.content.miner;

import dev.gensomechanic.mining.content.vein.*;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** A resolved source; absence of a recipe never changes which source owns the chunk. */
public record MiningTarget(VeinCapacityTracker.Key identity, ResourceLocation id, Component name,
                           int capacity, String signature, boolean background) {
    public static MiningTarget resolve(ServerLevel level, BlockPos controller, Optional<VeinGenerator.Occurrence> vein) {
        if (vein.isPresent()) {
            var value = vein.get();
            return new MiningTarget(VeinCapacityTracker.Key.vein(value.identity()), value.definition().id(),
                    value.definition().name(), value.capacity(),
                    value.signature() + "|" + value.identity() + "|" + value.capacity(), false);
        }
        for (var definition : VeinManager.backgrounds(level.getServer())) {
            if (!definition.matches(level, controller)) continue;
            var key = new VeinCapacityTracker.Key(true, level.dimension().location(), definition.id(), new ChunkPos(controller));
            return new MiningTarget(key, definition.id(), definition.name(), definition.capacity(),
                    "background|" + key + "|" + definition.semantics(), true);
        }
        return null;
    }
}
