package dev.gensomechanic.mining.content.vein;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Original filter entry and its resolved members from the same server reload snapshot. */
public record VeinCondition(String value, List<ResourceLocation> members) {
    public static final int MAX_ENTRIES = 256, MAX_MEMBERS = 4096;
    public VeinCondition {
        members = List.copyOf(members);
        String id = value.startsWith("#") ? value.substring(1) : value;
        if (value.length() > 256 || ResourceLocation.tryParse(id) == null || members.size() > MAX_MEMBERS)
            throw new IllegalArgumentException("Invalid vein condition: " + value);
    }
    public boolean isTag() { return value.startsWith("#"); }
    public static void writeList(RegistryFriendlyByteBuf buffer, List<VeinCondition> entries) {
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many vein conditions");
        buffer.writeVarInt(entries.size());
        for (var entry : entries) {
            buffer.writeUtf(entry.value, 256); buffer.writeVarInt(entry.members.size());
            entry.members.forEach(buffer::writeResourceLocation);
        }
    }
    public static List<VeinCondition> readList(RegistryFriendlyByteBuf buffer) {
        int size = bounded(buffer.readVarInt(), MAX_ENTRIES);
        List<VeinCondition> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String value = buffer.readUtf(256);
            int count = bounded(buffer.readVarInt(), MAX_MEMBERS);
            List<ResourceLocation> members = new ArrayList<>(count);
            for (int j = 0; j < count; j++) members.add(buffer.readResourceLocation());
            entries.add(new VeinCondition(value, members));
        }
        return List.copyOf(entries);
    }
    private static int bounded(int value, int max) {
        if (value < 0 || value > max) throw new IllegalArgumentException("Invalid vein condition list size");
        return value;
    }
}
