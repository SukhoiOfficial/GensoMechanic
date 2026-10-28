package dev.gensomechanic.mining.network;

import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.content.vein.ScannerService;
import dev.gensomechanic.mining.content.vein.ScanSelection;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Public catalogue/filter metadata and private scan results; never the world seed or candidate positions. */
public final class MiningNetwork {
    public record Entry(ResourceLocation id, Component name, ResourceLocation icon, ResourceLocation variantOf,
                        List<List<dev.gensomechanic.mining.content.vein.VeinCondition>> conditions, int color,List<Component> details) {
        public Entry(ResourceLocation id, Component name, ResourceLocation icon, ResourceLocation parent, List<List<dev.gensomechanic.mining.content.vein.VeinCondition>> conditions) { this(id, name, icon, parent, conditions, 0xA0A0A0); }
        public Entry(ResourceLocation id,Component name,ResourceLocation icon,ResourceLocation parent,List<List<dev.gensomechanic.mining.content.vein.VeinCondition>> conditions,int color){this(id,name,icon,parent,conditions,color,List.of());}
        public Entry {
            name = name.copy(); conditions = conditions.stream().map(List::copyOf).toList();details=details.stream().map(Component::copy).map(c->(Component)c).toList();
            if (conditions.size() != 4) throw new IllegalArgumentException("Expected four filter lists");
            if(details.size()>256)throw new IllegalArgumentException("Too many generation detail rows");
        }
        @Override public Component name() { return name.copy(); }
    }
    public static Consumer<Catalog> catalogueReceiver = packet -> {};
    public static Consumer<Open> openReceiver = packet -> {};
    public static Consumer<ScannerOverlayPayload> overlayReceiver = packet -> {};
    public static Consumer<VeinSurveyPayload> surveyReceiver = packet -> {};
    public static Consumer<Wave> waveReceiver = packet -> {};
    private static ResourceLocation readOptional(RegistryFriendlyByteBuf b) { return b.readBoolean() ? b.readResourceLocation() : null; }
    private static void writeOptional(RegistryFriendlyByteBuf b, ResourceLocation id) { b.writeBoolean(id != null); if (id != null) b.writeResourceLocation(id); }
    public record Catalog(List<Entry> entries, String generation, Map<ResourceLocation,String> layouts,
                          List<Entry> backgrounds) implements CustomPacketPayload {
        public Catalog(List<Entry> entries) { this(entries, ""); }
        public Catalog(List<Entry> entries,String generation) { this(entries,generation,Map.of()); }
        public Catalog(List<Entry> entries,String generation,Map<ResourceLocation,String> layouts) { this(entries,generation,layouts,List.of()); }
        public Catalog {
            entries = List.copyOf(entries); layouts=Map.copyOf(layouts); backgrounds=List.copyOf(backgrounds);
            if (entries.size()>4096 || backgrounds.size()>4096 || layouts.size()>256)
                throw new IllegalArgumentException("Mining catalogue exceeds its size limits");
            Set<ResourceLocation> ids=new HashSet<>();
            for (Entry entry : entries) if(!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate vein ID " + entry.id());
            for (Entry entry : backgrounds) {
                if(!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate mining target ID " + entry.id());
                if(entry.variantOf()!=null) throw new IllegalArgumentException("Rock backgrounds cannot have vein variants");
            }
        }
        public String layout(ResourceLocation dimension) { return layouts.isEmpty()?generation:layouts.getOrDefault(dimension,""); }
        public static final Type<Catalog> TYPE = new Type<>(GensoMining.asResource("scanner_catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Catalog> CODEC = StreamCodec.of((b, p) -> {
            b.writeUtf(p.generation, 64); writeEntries(b,p.entries); writeEntries(b,p.backgrounds);
            b.writeVarInt(p.layouts.size());
            p.layouts.forEach((dimension,signature)->{b.writeResourceLocation(dimension);b.writeUtf(signature,64);});
        }, b -> {
            String generation = b.readUtf(64);
            List<Entry> entries=readEntries(b), backgrounds=readEntries(b);
            int dimensions=b.readVarInt();if(dimensions<0||dimensions>256)throw new IllegalArgumentException("Invalid layout directory size");
            Map<ResourceLocation,String> layouts=new HashMap<>();
            for(int i=0;i<dimensions;i++)if(layouts.put(b.readResourceLocation(),b.readUtf(64))!=null)throw new IllegalArgumentException("Duplicate layout dimension");
            return new Catalog(entries, generation, layouts, backgrounds);
        });
        @Override public Type<Catalog> type() { return TYPE; }
    }
    private static void writeEntries(RegistryFriendlyByteBuf buffer,List<Entry> entries) {
        buffer.writeVarInt(entries.size());
        for(Entry entry:entries) {
            buffer.writeResourceLocation(entry.id()); ComponentSerialization.STREAM_CODEC.encode(buffer,entry.name());
            buffer.writeResourceLocation(entry.icon()); writeOptional(buffer,entry.variantOf()); buffer.writeInt(entry.color());
            for(var list:entry.conditions()) dev.gensomechanic.mining.content.vein.VeinCondition.writeList(buffer,list);
            buffer.writeVarInt(entry.details().size()); entry.details().forEach(line->ComponentSerialization.STREAM_CODEC.encode(buffer,line));
        }
    }
    private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
        int count=buffer.readVarInt();
        if(count<0 || count>4096) throw new IllegalArgumentException("Invalid mining catalogue size");
        List<Entry> entries=new ArrayList<>(count);
        for(int i=0;i<count;i++) {
            var id=buffer.readResourceLocation(); var name=ComponentSerialization.STREAM_CODEC.decode(buffer);
            var icon=buffer.readResourceLocation(); var parent=readOptional(buffer); int color=buffer.readInt();
            List<List<dev.gensomechanic.mining.content.vein.VeinCondition>> conditions=new ArrayList<>(4);
            for(int row=0;row<4;row++) conditions.add(dev.gensomechanic.mining.content.vein.VeinCondition.readList(buffer));
            int countDetails=buffer.readVarInt();
            if(countDetails<0 || countDetails>256) throw new IllegalArgumentException("Invalid mining catalogue details size");
            List<Component> details=new ArrayList<>(countDetails);
            for(int row=0;row<countDetails;row++) details.add(ComponentSerialization.STREAM_CODEC.decode(buffer));
            entries.add(new Entry(id,name,icon,parent,conditions,color,details));
        }
        return List.copyOf(entries);
    }
    public record Open(UUID session, ScanSelection selection, boolean archive) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(GensoMining.asResource("scanner_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of((b, p) -> { b.writeUUID(p.session); ScanSelection.STREAM_CODEC.encode(b, p.selection); b.writeBoolean(p.archive); }, b -> new Open(b.readUUID(), ScanSelection.STREAM_CODEC.decode(b), b.readBoolean()));
        @Override public Type<Open> type() { return TYPE; }
    }
    public record Configure(UUID session, ScanSelection selection) implements CustomPacketPayload {
        public static final Type<Configure> TYPE = new Type<>(GensoMining.asResource("scanner_configure"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Configure> CODEC = StreamCodec.of((b, p) -> { b.writeUUID(p.session); ScanSelection.STREAM_CODEC.encode(b, p.selection); }, b -> new Configure(b.readUUID(), ScanSelection.STREAM_CODEC.decode(b)));
        @Override public Type<Configure> type() { return TYPE; }
    }
    public record Wave(Vec3 origin) implements CustomPacketPayload {
        public static final Type<Wave> TYPE = new Type<>(GensoMining.asResource("scanner_wave"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Wave> CODEC = StreamCodec.of((b, p) -> {
            b.writeDouble(p.origin.x); b.writeDouble(p.origin.y); b.writeDouble(p.origin.z);
        }, b -> new Wave(new Vec3(b.readDouble(), b.readDouble(), b.readDouble())));
        @Override public Type<Wave> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("mining-2");
        ArchiveNetwork.register(registrar);
        registrar.playToClient(VeinSurveyPayload.TYPE, VeinSurveyPayload.CODEC, (p,c) -> surveyReceiver.accept(p));
        registrar.playToClient(Catalog.TYPE, Catalog.CODEC, (p, c) -> catalogueReceiver.accept(p));
        registrar.playToClient(Open.TYPE, Open.CODEC, (p, c) -> openReceiver.accept(p));
        registrar.playToClient(ScannerOverlayPayload.TYPE, ScannerOverlayPayload.CODEC, (p, c) -> overlayReceiver.accept(p));
        registrar.playToClient(Wave.TYPE, Wave.CODEC, (p, c) -> waveReceiver.accept(p));
        registrar.playToServer(Configure.TYPE, Configure.CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer player) ScannerService.configureSelection(player, p.session, p.selection);
        });
    }
    private MiningNetwork() {}
}
