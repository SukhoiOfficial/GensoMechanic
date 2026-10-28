package dev.gensomechanic.mining.network;

import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.content.vein.VeinScan;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/** Bounded fragments of a private discovery snapshot, never candidate positions or world seeds. */
public record ScannerOverlayPayload(UUID scan, ResourceLocation dimension, Stage stage, List<Mark> marks,
                                    String generation, int part, int parts) implements CustomPacketPayload {
    public enum Stage { START, RESULT, CLEAR }
    public record Key(ResourceLocation type, ChunkPos centre) {}
    public record Mark(ChunkPos chunk, ResourceLocation vein, Component name, ChunkPos centre, int color) {
        public Mark { name = name.copy(); if ((color & 0xFF000000) != 0) throw new IllegalArgumentException("Invalid RGB"); }
        public Mark(ChunkPos chunk, ResourceLocation vein, Component name) { this(chunk, vein, name, chunk, 0xA0A0A0); }
        @Override public Component name() { return name.copy(); }
        public Key key() { return new Key(vein, centre); }
    }
    public static final int MAX_INSTANCES = (2 * (VeinScan.RANGE / 16 + 1) + 1) * (2 * (VeinScan.RANGE / 16 + 1) + 1), MAX_VEIN_CHUNKS = 4096, MAX_TOTAL_CHUNKS = MAX_INSTANCES * MAX_VEIN_CHUNKS;
    public static final int MAX_FRAGMENT_BYTES = 49152, MAX_PARTS = 256, MAX_TOTAL_BYTES = 16 * 1024 * 1024;
    public ScannerOverlayPayload(UUID scan, ResourceLocation dimension, Stage stage, List<Mark> marks) {
        this(scan, dimension, stage, marks, "", 0, 1);
    }
    public ScannerOverlayPayload {
        marks = List.copyOf(marks);
        if (marks.size() > MAX_FRAGMENT_BYTES / 8 || parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts
            || generation.length() > 64 || stage != Stage.RESULT && (!marks.isEmpty() || part != 0 || parts != 1))
            throw new IllegalArgumentException("Invalid scan fragment");
    }
    public static List<Mark> marks(VeinScan scan) {
        Map<Key, dev.gensomechanic.mining.content.vein.VeinGenerator.Occurrence> found = new LinkedHashMap<>();
        scan.current().ifPresent(v -> found.put(new Key(v.definition().id(), v.identity().centre()), v));
        scan.traces().forEach(t -> found.put(new Key(t.vein().definition().id(), t.vein().identity().centre()), t.vein()));
        List<Mark> result = new ArrayList<>();
        found.values().forEach(v -> v.chunks().forEach(p -> result.add(new Mark(p, v.definition().id(), v.definition().name(), v.identity().centre(), v.definition().mapColor()))));
        return List.copyOf(result);
    }
    private static Map<Key,List<Mark>> groups(List<Mark> marks) {
        Map<Key,List<Mark>> groups = new LinkedHashMap<>();
        for (Mark mark : marks) groups.computeIfAbsent(mark.key(), k -> new ArrayList<>()).add(mark);
        return groups;
    }
    public static List<ScannerOverlayPayload> fragments(UUID scan, ResourceLocation dimension, String generation,
            List<Mark> marks, net.minecraft.core.RegistryAccess registries) {
        return fragments(scan,dimension,generation,marks,registries,0);
    }
    public static List<ScannerOverlayPayload> fragments(UUID scan, ResourceLocation dimension, String generation,
            List<Mark> marks, net.minecraft.core.RegistryAccess registries, int envelopeBytes) {
        if(envelopeBytes<0 || envelopeBytes>256)throw new IllegalArgumentException("Invalid envelope budget");
        final int fragmentBudget=MAX_FRAGMENT_BYTES-envelopeBytes;
        if (marks.size() > MAX_TOTAL_CHUNKS) throw new IllegalArgumentException("Scan exceeds supported footprint count");
        Map<Key,List<Mark>> groups = groups(marks);
        if (groups.size() > MAX_INSTANCES) throw new IllegalArgumentException("Too many discovered veins");
        List<List<Mark>> batches = new ArrayList<>(); List<Mark> batch = new ArrayList<>(); int total = 0;
        var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
        try {
            buffer.writeUUID(scan); buffer.writeResourceLocation(dimension); buffer.writeEnum(Stage.RESULT); buffer.writeUtf(generation, 64);
            // Three worst-case VarInts: part, part count, instance count.
            int baseBytes = buffer.readableBytes() + 15, bytes = baseBytes;
            for (var group : groups.values()) {
                if (group.size() > MAX_VEIN_CHUNKS) throw new IllegalArgumentException("Vein exceeds protocol size limit");
                buffer.clear(); writeHeader(buffer, group.getFirst()); int header = buffer.readableBytes() + 5;
                if (header > fragmentBudget - baseBytes - 8) throw new IllegalArgumentException("Vein metadata exceeds fragment budget");
                int offset = 0;
                while (offset < group.size()) {
                    int count = Math.min(group.size() - offset, (fragmentBudget - bytes - header) / 8);
                    if (count <= 0) { batches.add(List.copyOf(batch)); total += bytes; batch.clear(); bytes = baseBytes; continue; }
                    batch.addAll(group.subList(offset, offset + count)); offset += count; bytes += header + count * 8;
                }
            }
            batches.add(List.copyOf(batch)); total += bytes;
            if (total > MAX_TOTAL_BYTES || batches.size() > MAX_PARTS) throw new IllegalArgumentException("Scan exceeds assembly budget");
            List<ScannerOverlayPayload> result = new ArrayList<>();
            for (int i = 0; i < batches.size(); i++) result.add(new ScannerOverlayPayload(scan, dimension, Stage.RESULT, batches.get(i), generation, i, batches.size()));
            return List.copyOf(result);
        } finally { buffer.release(); }
    }
    private static void writeHeader(RegistryFriendlyByteBuf b, Mark m) {
        b.writeResourceLocation(m.vein); b.writeChunkPos(m.centre); b.writeInt(m.color); ComponentSerialization.STREAM_CODEC.encode(b, m.name);
    }
    public static final Type<ScannerOverlayPayload> TYPE = new Type<>(GensoMining.asResource("scanner_overlay"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScannerOverlayPayload> CODEC = StreamCodec.of((b, p) -> {
        int start = b.writerIndex();
        b.writeUUID(p.scan); b.writeResourceLocation(p.dimension); b.writeEnum(p.stage); b.writeUtf(p.generation, 64); b.writeVarInt(p.part); b.writeVarInt(p.parts);
        var groups = groups(p.marks); b.writeVarInt(groups.size());
        for (var group : groups.values()) { writeHeader(b, group.getFirst()); b.writeVarInt(group.size()); for (Mark m : group) b.writeChunkPos(m.chunk); }
        if (b.writerIndex() - start > MAX_FRAGMENT_BYTES) throw new IllegalArgumentException("Scan fragment exceeds byte budget");
    }, b -> {
        int start = b.readerIndex();
        if (b.readableBytes() > MAX_FRAGMENT_BYTES) throw new IllegalArgumentException("Oversized scan fragment");
        UUID scan = b.readUUID(); var dimension = b.readResourceLocation(); Stage stage = b.readEnum(Stage.class);
        String generation = b.readUtf(64); int part = b.readVarInt(), parts = b.readVarInt(), size = b.readVarInt();
        if (size < 0 || size > MAX_INSTANCES) throw new IllegalArgumentException("Invalid scan instance count");
        List<Mark> marks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            var type = b.readResourceLocation(); var centre = b.readChunkPos(); int color = b.readInt(); var name = ComponentSerialization.STREAM_CODEC.decode(b);
            int count = b.readVarInt();
            if (count < 1 || count > MAX_VEIN_CHUNKS || count * 8 > b.readableBytes() || marks.size() + count > MAX_FRAGMENT_BYTES / 8)
                throw new IllegalArgumentException("Invalid scan member count");
            for (int j = 0; j < count; j++) marks.add(new Mark(b.readChunkPos(), type, name, centre, color));
        }
        if (b.readerIndex() - start > MAX_FRAGMENT_BYTES) throw new IllegalArgumentException("Oversized scan fragment");
        return new ScannerOverlayPayload(scan, dimension, stage, marks, generation, part, parts);
    });
    @Override public Type<ScannerOverlayPayload> type() { return TYPE; }
}
