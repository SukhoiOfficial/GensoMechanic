package dev.gensomechanic.mining.content.vein;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

/** Strict JSON helpers shared by the two public datapack formats. */
final class VeinData {
    record Context(Registry<Biome> biomes, Map<ResourceLocation, Collection<Holder<Biome>>> tags) {}
    static JsonParseException invalid(String message) { return new JsonParseException(message); }
    static void keys(JsonObject object, String... allowed) {
        var names = Set.of(allowed);
        for (String key : object.keySet()) if (!names.contains(key)) throw invalid("Unknown setting: " + key);
    }
    static JsonElement required(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) throw invalid("Missing " + key);
        return object.get(key);
    }
    static JsonObject object(JsonObject object, String key) { return required(object, key).getAsJsonObject(); }
    static JsonObject optionalObject(JsonObject object, String key) { return object.has(key) ? object(object,key) : new JsonObject(); }
    static String string(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Expected a string");
        return value.getAsString();
    }
    static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) return fallback;
        var value=object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid(key+" must be boolean");
        return value.getAsBoolean();
    }
    static int integer(JsonObject object, String key, int fallback, int min, int max) {
        if (!object.has(key)) return fallback;
        var value=object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid(key+" must be an integer");
        try {
            int n=value.getAsBigDecimal().intValueExact();
            if (n<min || n>max) throw invalid(key+" must be "+min+".."+max);
            return n;
        } catch (ArithmeticException | NumberFormatException e) { throw invalid(key+" must be a 32-bit integer"); }
    }
    static double number(JsonObject object, String key, double fallback, double min, double max) {
        if (!object.has(key)) return fallback;
        var value=object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid(key+" must be a number");
        double n=value.getAsDouble();
        if (!Double.isFinite(n) || n<min || n>max) throw invalid(key+" must be finite in "+min+".."+max);
        return n;
    }
    static ResourceLocation id(JsonElement value) {
        var id=ResourceLocation.tryParse(string(value));
        if (id==null) throw invalid("Invalid resource ID: "+value);
        return id;
    }
    static VeinDefinition.Filter filter(JsonObject json, Context context, boolean dimensions) {
        keys(json,"allow","deny");
        var allow=json.has("allow") ? json.getAsJsonArray("allow") : new JsonArray();
        var deny=json.has("deny") ? json.getAsJsonArray("deny") : new JsonArray();
        var a=conditions(allow,context,dimensions); var d=conditions(deny,context,dimensions);
        return new VeinDefinition.Filter(allow.isEmpty(),members(a),members(d),a,d);
    }
    static Set<ResourceLocation> members(List<VeinCondition> conditions) {
        Set<ResourceLocation> out=new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        for (var condition:conditions) out.addAll(condition.members());
        return Set.copyOf(out);
    }
    static List<VeinCondition> conditions(JsonArray entries, Context context, boolean dimensions) {
        if (entries.size()>VeinCondition.MAX_ENTRIES) throw invalid("Too many condition entries");
        List<VeinCondition> result=new ArrayList<>();
        for (var entry:entries) {
            String value; boolean required=true;
            if (entry.isJsonObject()) {
                var o=entry.getAsJsonObject(); keys(o,"id","required");
                value=string(required(o,"id")); required=bool(o,"required",true);
            } else value=string(entry);
            List<ResourceLocation> members=new ArrayList<>();
            if (value.startsWith("#")) {
                if (dimensions) throw invalid("Dimension selectors use IDs, not tags");
                var tag=ResourceLocation.tryParse(value.substring(1));
                if (tag==null) throw invalid("Invalid biome tag: "+value);
                var values=context.tags().get(tag);
                if (values==null && required) throw invalid("Unknown biome tag: "+value);
                if (values!=null) for (var biome:values) members.add(biome.unwrapKey().orElseThrow().location());
            } else {
                var id=ResourceLocation.tryParse(value);
                if (id==null) throw invalid("Invalid resource ID: "+value);
                if (dimensions || context.biomes().containsKey(id)) members.add(id);
                else if (required) throw invalid("Unknown biome: "+id);
            }
            members.sort(Comparator.comparing(ResourceLocation::toString));
            result.add(new VeinCondition(value,List.copyOf(members)));
        }
        return List.copyOf(result);
    }
    static boolean enabled(JsonObject json) {
        if (!json.has("requires_mod")) return true;
        String mod=string(json.get("requires_mod"));
        if (!mod.matches("[a-z][a-z0-9_]{1,63}")) throw invalid("Invalid requires_mod: "+mod);
        return net.neoforged.fml.ModList.get().isLoaded(mod);
    }
    private VeinData() {}
}
