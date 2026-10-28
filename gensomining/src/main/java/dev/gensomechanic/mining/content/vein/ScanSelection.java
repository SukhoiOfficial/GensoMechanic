package dev.gensomechanic.mining.content.vein;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** No synthetic vein IDs: all, unset and a real type are distinct on disk and on the wire. */
public record ScanSelection(boolean all, Optional<ResourceLocation> type) {
    public static final ScanSelection NONE = new ScanSelection(false, Optional.empty());
    public static final ScanSelection ALL = new ScanSelection(true, Optional.empty());
    public ScanSelection { if (all && type.isPresent()) throw new IllegalArgumentException("All selection cannot contain a type"); }
    public static ScanSelection of(ResourceLocation id) { return id == null ? NONE : new ScanSelection(false, Optional.of(id)); }
    public static final Codec<ScanSelection> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.optionalFieldOf("all", false).forGetter(ScanSelection::all),
        ResourceLocation.CODEC.optionalFieldOf("type").forGetter(ScanSelection::type)).apply(i, ScanSelection::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScanSelection> STREAM_CODEC = StreamCodec.of((b,s) -> {
        b.writeBoolean(s.all); b.writeBoolean(s.type.isPresent()); s.type.ifPresent(b::writeResourceLocation);
    }, b -> new ScanSelection(b.readBoolean(), b.readBoolean() ? Optional.of(b.readResourceLocation()) : Optional.empty()));
    public boolean matches(VeinDefinition definition) { return all || type.filter(definition.searchId()::equals).isPresent(); }
    public static java.util.List<ScanSelection> options(java.util.List<ResourceLocation> types, boolean archive) {
        var choices=new java.util.ArrayList<ScanSelection>();
        if(!archive)choices.add(NONE); choices.add(ALL); types.forEach(id->choices.add(of(id))); return java.util.List.copyOf(choices);
    }
}
