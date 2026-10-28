package dev.gensomechanic.mining.network;

import java.util.UUID;
import dev.gensomechanic.mining.GensoMining;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Independent command stream; page fragments use the same bounded geometry codec as scans. */
public record VeinSurveyPayload(UUID task, int page, Kind kind, ScannerOverlayPayload fragment) implements CustomPacketPayload {
    public enum Kind { START, PAGE, END, CANCEL }
    public VeinSurveyPayload {
        if(page<0||page>20000||!fragment.scan().equals(task)
            || (kind==Kind.PAGE ? fragment.stage()!=ScannerOverlayPayload.Stage.RESULT : fragment.stage()!=ScannerOverlayPayload.Stage.START))
            throw new IllegalArgumentException("Invalid survey page");
    }
    public static final Type<VeinSurveyPayload> TYPE=new Type<>(GensoMining.asResource("vein_survey"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VeinSurveyPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeUUID(p.task);b.writeVarInt(p.page);b.writeEnum(p.kind);ScannerOverlayPayload.CODEC.encode(b,p.fragment);
    },b->new VeinSurveyPayload(b.readUUID(),b.readVarInt(),b.readEnum(Kind.class),ScannerOverlayPayload.CODEC.decode(b)));
    @Override public Type<VeinSurveyPayload> type(){return TYPE;}
}
