package dev.gensomechanic.mining.network;

import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.content.vein.*;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ArchiveNetwork {
    public static Consumer<Status> statusReceiver=p -> {};
    public static Consumer<Page> pageReceiver=p -> {};
    public static Consumer<IndexPage> indexReceiver=p -> {};
    public record IndexRequest(UUID request, ResourceLocation dimension, int offset, long revision, String generation) implements CustomPacketPayload {
        public IndexRequest { if(offset<0) throw new IllegalArgumentException("Invalid index offset"); }
        public static final Type<IndexRequest> TYPE=new Type<>(GensoMining.asResource("archive_index_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,IndexRequest> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.request);b.writeResourceLocation(p.dimension);b.writeVarInt(p.offset);b.writeLong(p.revision);b.writeUtf(p.generation,64);
        },b->new IndexRequest(b.readUUID(),b.readResourceLocation(),b.readVarInt(),b.readLong(),b.readUtf(64)));
        @Override public Type<IndexRequest> type(){return TYPE;}
    }
    public record IndexPage(UUID request, int next, java.util.List<Long> regions) implements CustomPacketPayload {
        public IndexPage { regions=java.util.List.copyOf(regions);if(next < -1 || regions.size()>2048)throw new IllegalArgumentException("Invalid index page"); }
        public static final Type<IndexPage> TYPE=new Type<>(GensoMining.asResource("archive_index_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf,IndexPage> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.request);b.writeInt(p.next);b.writeVarInt(p.regions.size());p.regions.forEach(b::writeLong);
        },b->{var id=b.readUUID();int next=b.readInt(),size=b.readVarInt();if(size<0 || size>2048)throw new IllegalArgumentException("Invalid index page size");var list=new java.util.ArrayList<Long>(size);for(int i=0;i<size;i++)list.add(b.readLong());return new IndexPage(id,next,list);});
        @Override public Type<IndexPage> type(){return TYPE;}
    }
    public record Status(boolean active, long revision, String generation, ScanSelection selection) implements CustomPacketPayload {
        public static final Type<Status> TYPE=new Type<>(GensoMining.asResource("archive_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Status> CODEC=StreamCodec.of((b,p)->{
            b.writeBoolean(p.active); b.writeLong(p.revision); b.writeUtf(p.generation,64); ScanSelection.STREAM_CODEC.encode(b,p.selection);
        },b->new Status(b.readBoolean(),b.readLong(),b.readUtf(64),ScanSelection.STREAM_CODEC.decode(b)));
        @Override public Type<Status> type(){return TYPE;}
    }
    public record Request(UUID request, ResourceLocation dimension, int regionX, int regionZ, int offset, long revision, String generation, ScanSelection selection) implements CustomPacketPayload {
        public Request { if(offset<0 || Math.abs((long)regionX)>60000 || Math.abs((long)regionZ)>60000) throw new IllegalArgumentException("Invalid archive region request"); }
        public static final Type<Request> TYPE=new Type<>(GensoMining.asResource("archive_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.request); b.writeResourceLocation(p.dimension); b.writeInt(p.regionX); b.writeInt(p.regionZ); b.writeVarInt(p.offset);
            b.writeLong(p.revision); b.writeUtf(p.generation,64); ScanSelection.STREAM_CODEC.encode(b,p.selection);
        },b->new Request(b.readUUID(),b.readResourceLocation(),b.readInt(),b.readInt(),b.readVarInt(),b.readLong(),b.readUtf(64),ScanSelection.STREAM_CODEC.decode(b)));
        @Override public Type<Request> type(){return TYPE;}
    }
    public record Page(UUID request, long revision, int nextOffset, ScannerOverlayPayload fragment) implements CustomPacketPayload {
        public Page { if(nextOffset < -1 || fragment.stage()!=ScannerOverlayPayload.Stage.RESULT || !fragment.scan().equals(request)) throw new IllegalArgumentException("Invalid archive page"); }
        public static final Type<Page> TYPE=new Type<>(GensoMining.asResource("archive_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Page> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.request); b.writeLong(p.revision); b.writeInt(p.nextOffset); ScannerOverlayPayload.CODEC.encode(b,p.fragment);
        },b->new Page(b.readUUID(),b.readLong(),b.readInt(),ScannerOverlayPayload.CODEC.decode(b)));
        @Override public Type<Page> type(){return TYPE;}
    }
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(IndexPage.TYPE,IndexPage.CODEC,(p,c)->indexReceiver.accept(p));
        registrar.playToServer(IndexRequest.TYPE,IndexRequest.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player) ArchiveService.index(player,p);});
        registrar.playToClient(Status.TYPE,Status.CODEC,(p,c)->statusReceiver.accept(p));
        registrar.playToClient(Page.TYPE,Page.CODEC,(p,c)->pageReceiver.accept(p));
        registrar.playToServer(Request.TYPE,Request.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player) ArchiveService.request(player,p);});
    }
    private ArchiveNetwork(){}
}
