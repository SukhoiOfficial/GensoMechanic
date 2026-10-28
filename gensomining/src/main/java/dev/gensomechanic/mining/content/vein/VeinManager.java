package dev.gensomechanic.mining.content.vein;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.gensomechanic.mining.GensoMining;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

@EventBusSubscriber(modid = GensoMining.MOD_ID)
public final class VeinManager {
    public static final int CACHE_LIMIT = 8192;
    // A reload's data is selected only after vanilla commits that resource manager successfully.
    private record Definitions(VeinGenerator generator, List<RockBackgroundDefinition> backgrounds) {}
    private static final Map<ReloadableServerResources, Definitions> DEFINITIONS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerLevel, Runtime> WORLDS = new IdentityHashMap<>();

    private static final class Runtime {
        private VeinGenerator generator;
        private VeinQuery query;
        private final VeinCapacityTracker capacity = new VeinCapacityTracker();
    }
    private record RawDefinition(ResourceLocation id, JsonObject json, String directory) {}

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<List<RawDefinition>>() {
            @Override
            protected List<RawDefinition> prepare(ResourceManager resources, ProfilerFiller profiler) {
                List<RawDefinition> result = new ArrayList<>();
                for (String directory : List.of("veins", "vein_worlds", "rock_backgrounds")) {
                FileToIdConverter files = FileToIdConverter.json(directory);
                for (var entry : files.listMatchingResources(resources).entrySet()) {
                    ResourceLocation id = files.fileToId(entry.getKey());
                    try (Reader reader = entry.getValue().openAsReader()) {
                        result.add(new RawDefinition(id, JsonParser.parseReader(reader).getAsJsonObject(), directory));
                    } catch (IOException | RuntimeException e) {
                        throw new JsonParseException("Cannot read vein " + id + " from " + entry.getKey(), e);
                    }
                }
                }
                return List.copyOf(result);
            }

            @Override
            protected void apply(List<RawDefinition> raw, ResourceManager resources, ProfilerFiller profiler) {
                var registry = event.getRegistryAccess().registryOrThrow(Registries.BIOME);
                var tags = event.getConditionContext().getAllTags(Registries.BIOME);
                List<VeinDefinition> parsed = new ArrayList<>();
                for (RawDefinition definition : raw) {
                    if (!definition.directory().equals("veins") || !VeinData.enabled(definition.json())) continue;
                    try {
                        parsed.add(VeinDefinition.parse(definition.id(), definition.json(), registry, tags, event.getRegistryAccess()));
                    } catch (RuntimeException e) {
                        throw new JsonParseException("Invalid vein " + definition.id() + ": " + e.getMessage(), e);
                    }
                }
                Map<ResourceLocation, VeinDefinition> definitions = new HashMap<>();
                parsed.forEach(d -> definitions.put(d.id(), d));
                List<VeinWorldDefinition> worlds = new ArrayList<>();
                for (RawDefinition definition : raw) {
                    if (!definition.directory().equals("vein_worlds") || !VeinData.enabled(definition.json())) continue;
                    try {
                        worlds.add(VeinWorldDefinition.parse(definition.id(), definition.json(), new VeinData.Context(registry, tags), definitions));
                    } catch (RuntimeException e) {
                        throw new JsonParseException("Invalid vein_worlds " + definition.id() + ": " + e.getMessage(), e);
                    }
                }
                var compiled=new VeinGenerator(parsed, worlds);
                compiled.definitions().values().forEach(d->VeinPublicMetadata.entry(compiled,d));
                List<RockBackgroundDefinition> backgrounds = new ArrayList<>();
                Set<ResourceLocation> backgroundIds = new HashSet<>();
                for (RawDefinition definition : raw) {
                    if (!definition.directory().equals("rock_backgrounds") || !VeinData.enabled(definition.json())) continue;
                    try {
                        if (definitions.containsKey(definition.id()) || !backgroundIds.add(definition.id()))
                            throw new IllegalArgumentException("Duplicate mining source ID: " + definition.id());
                        var background = RockBackgroundDefinition.parse(definition.id(), definition.json(), registry, tags, event.getRegistryAccess());
                        background.metadata();
                        backgrounds.add(background);
                    } catch (RuntimeException e) {
                        throw new JsonParseException("Invalid rock_backgrounds " + definition.id() + ": " + e.getMessage(), e);
                    }
                }
                if (backgrounds.size() > 512) throw new JsonParseException("Rock background catalogue exceeds 512 entries");
                backgrounds.sort(RockBackgroundDefinition.ORDER);
                DEFINITIONS.put(event.getServerResources(), new Definitions(compiled, List.copyOf(backgrounds)));
                GensoMining.LOGGER.info("Loaded {} virtual vein definitions, {} dimension profiles and {} rock backgrounds", parsed.size(), worlds.size(), backgrounds.size());
            }
        });
    }

    public static String signature(net.minecraft.server.MinecraftServer server) {
        VeinGenerator g = generator(server);
        return g == null ? "" : g.signature();
    }
    public static VeinQuery query(ServerLevel level) { return runtime(level).query; }
    public static VeinGenerator generator(net.minecraft.server.MinecraftServer server) {
        var snapshot = DEFINITIONS.get(server.getServerResources().managers());
        return snapshot == null ? null : snapshot.generator();
    }
    public static List<RockBackgroundDefinition> backgrounds(net.minecraft.server.MinecraftServer server) {
        var snapshot = DEFINITIONS.get(server.getServerResources().managers());
        return snapshot == null ? List.of() : snapshot.backgrounds();
    }
    public static String signature(ServerLevel level) {
        var generator = generator(level.getServer());
        return generator == null ? "" : generator.signature(level.dimension().location());
    }
    public static VeinCapacityTracker capacity(ServerLevel level) { return runtime(level).capacity; }
    public static Map<ResourceLocation, VeinDefinition> definitions(net.minecraft.server.MinecraftServer server) {
        VeinGenerator generator = generator(server);
        return generator == null ? Map.of() : generator.definitions();
    }
    public static void unregisterMiner(ServerLevel level, net.minecraft.core.BlockPos pos) {
        Runtime runtime = WORLDS.get(level);
        if (runtime != null) runtime.capacity.unregister(pos);
    }

    private static Runtime runtime(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Vein queries require the server thread");
        VeinGenerator current = generator(level.getServer());
        if (current == null) throw new IllegalStateException("Virtual vein definitions have not loaded");
        Runtime runtime = WORLDS.computeIfAbsent(level, ignored -> new Runtime());
        if (runtime.generator != current) {
            var previous=runtime.generator==null?null:runtime.generator.dimension(level.dimension().location());
            var next=current.dimension(level.dimension().location());
            if(runtime.query!=null&&Objects.equals(previous==null?null:previous.signature(),next==null?null:next.signature())
                &&Objects.equals(previous==null?null:previous.capacitySignature(),next==null?null:next.capacitySignature())) {
                runtime.generator=current;runtime.query.refreshMetadata(current);return runtime;
            }
            runtime.capacity.invalidate();
            runtime.generator = current;
            runtime.query = new VeinQuery(current, new VeinBiomes(level), CACHE_LIMIT);
        }
        return runtime;
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Runtime runtime = WORLDS.get(level);
            if (runtime != null) {
                runtime(level); // Invalidate query state before evaluating this tick's activity predicates.
                runtime.capacity.beginTick(level.getServer().getTickCount(), level::hasChunkAt);
            }
        }
    }
    @SubscribeEvent
    public static void unloadChunk(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Runtime runtime = WORLDS.get(level);
            if (runtime != null) runtime.capacity.unload(event.getChunk().getPos());
        }
    }
    @SubscribeEvent
    public static void unloadLevel(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {WORLDS.remove(level);VeinPlanningScheduler.unload(level);}
    }
    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        WORLDS.keySet().removeIf(level -> level.getServer() == event.getServer());
        VeinPlanningScheduler.stop(event.getServer());
    }
    private VeinManager() {}
}
