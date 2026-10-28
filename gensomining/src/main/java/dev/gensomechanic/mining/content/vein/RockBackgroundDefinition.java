package dev.gensomechanic.mining.content.vein;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import dev.gensomechanic.mining.network.MiningNetwork;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import static dev.gensomechanic.mining.content.vein.VeinData.*;

/** A conditional mining source, evaluated only after the chunk is known to contain no vein. */
public record RockBackgroundDefinition(ResourceLocation id, Component name, ResourceLocation icon,
        VeinDefinition.Filter dimensions, VeinDefinition.Filter biomes, int minY, int maxY,
        int priority, int capacity) {
    public static final Comparator<RockBackgroundDefinition> ORDER = Comparator
            .comparingInt(RockBackgroundDefinition::priority).reversed().thenComparing(d -> d.id().toString());
    public RockBackgroundDefinition { name = name.copy(); }
    @Override public Component name() { return name.copy(); }

    public boolean matches(ServerLevel level, BlockPos controller) {
        if (!dimensions.accepts(level.dimension().location()) || controller.getY() < minY || controller.getY() > maxY)
            return false;
        // The built-in background needs no biome lookup. Never load surrounding chunks or scan terrain.
        return biomes.unrestricted() && biomes.deny().isEmpty()
                || biomes.accepts(level.getBiome(controller).unwrapKey().orElseThrow().location());
    }
    public String semantics() {
        return id + "|" + dimensions.semantics() + "|" + biomes.semantics() + "|" + minY + "|" + maxY
                + "|" + priority + "|" + capacity;
    }
    public MiningNetwork.Entry metadata() {
        List<Component> details = new ArrayList<>();
        if (minY != Integer.MIN_VALUE || maxY != Integer.MAX_VALUE)
            details.add(Component.translatable("jei.gensomining.background.height",
                    minY == Integer.MIN_VALUE ? "−∞" : Integer.toString(minY),
                    maxY == Integer.MAX_VALUE ? "+∞" : Integer.toString(maxY)));
        details.add(Component.translatable("jei.gensomining.background.priority", priority));
        return new MiningNetwork.Entry(id, name, icon, null,
                List.of(dimensions.displayAllow(), dimensions.displayDeny(), biomes.displayAllow(), biomes.displayDeny()),
                0x808080, details);
    }
    public static RockBackgroundDefinition parse(ResourceLocation id, JsonObject json, Registry<Biome> registry,
            Map<ResourceLocation, Collection<Holder<Biome>>> tags, HolderLookup.Provider registries) {
        keys(json, "schema_version", "name", "icon", "conditions", "priority", "capacity", "requires_mod");
        if (integer(json, "schema_version", 0, 0, Integer.MAX_VALUE) != 1)
            throw invalid("Expected rock background schema_version 1");
        if (required(json, "name").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4096)
            throw invalid("name exceeds 4096-byte metadata budget");
        Component name = ComponentSerialization.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries),
                json.get("name")).getOrThrow(JsonParseException::new);
        ResourceLocation icon = VeinData.id(required(json, "icon"));
        if (!BuiltInRegistries.ITEM.containsKey(icon)) throw invalid("Unknown icon item: " + icon);
        JsonObject conditions = object(json, "conditions");
        keys(conditions, "dimensions", "biomes", "min_y", "max_y");
        int min = integer(conditions, "min_y", Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        int max = integer(conditions, "max_y", Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (min > max) throw invalid("min_y must not exceed max_y");
        var context = new Context(registry, tags);
        return new RockBackgroundDefinition(id, name, icon,
                filter(optionalObject(conditions, "dimensions"), context, true),
                filter(optionalObject(conditions, "biomes"), context, false), min, max,
                integer(json, "priority", 0, -1024, 1024), integer(json, "capacity", 1, 1, 1_000_000));
    }
}
