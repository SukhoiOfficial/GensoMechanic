package dev.gensomechanic.mining.client;

import dev.gensomechanic.mining.network.MiningNetwork;
import dev.gensomechanic.mining.network.ScannerOverlayPayload;
import dev.gensomechanic.mining.network.ScannerOverlayPayload.Mark;
import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.resources.ResourceLocation;

/** Assembly failures discard only the pending scan. Completed instances expire independently. */
public final class ScannerOverlayState {
    public record Snapshot(ResourceLocation dimension, List<Mark> marks, long revision) {
        public Snapshot { marks = List.copyOf(marks); }
    }
    private record Key(ResourceLocation dimension, ScannerOverlayPayload.Key vein) {}
    private record Timed(List<Mark> marks, long shownAt) {}
    private final LongSupplier clock;
    private final Map<Key,Timed> shown = new LinkedHashMap<>();
    private final Map<Integer,List<Mark>> fragments = new HashMap<>();
    private Map<ResourceLocation, MiningNetwork.Entry> catalogue = Map.of();
    private UUID commandTask;
    private ResourceLocation commandDimension;
    private boolean publishQueued;
    private long lastPublish;
    private int commandPage;
    private long commandUpdated;
    private ScannerOverlayState commandAssembly;
    public boolean commandActive() { return commandTask != null; }
    public void command(dev.gensomechanic.mining.network.VeinSurveyPayload packet,ResourceLocation dimension) {
        var f=packet.fragment();
        if(!Objects.equals(dimension,f.dimension())||!layout(dimension).equals(f.generation()))return;
        switch(packet.kind()) {
            case START -> { cancelCommand();commandTask=packet.task();commandDimension=dimension;commandPage=0;commandUpdated=clock.getAsLong(); }
            case END, CANCEL -> { if(packet.task().equals(commandTask)){if(publishQueued)publish();cancelCommand();} }
            case PAGE -> {
                if(!packet.task().equals(commandTask)||packet.page()<commandPage)return;
                if(packet.page()!=commandPage){cancelCommand();return;}
                commandUpdated=clock.getAsLong();
                if(commandAssembly==null){
                    commandAssembly=new ScannerOverlayState(clock);
                    commandAssembly.catalogue(new MiningNetwork.Catalog(new ArrayList<>(catalogue.values()),generation,layouts));
                    commandAssembly.accept(new ScannerOverlayPayload(packet.task(),dimension,ScannerOverlayPayload.Stage.START,List.of(),layout(dimension),0,1),dimension);
                }
                commandAssembly.accept(f,dimension);
                if(commandAssembly.pending==null){
                    if(commandAssembly.shown.isEmpty()){cancelCommand();return;}
                    shown.putAll(commandAssembly.shown);currentDimension=dimension;commandAssembly=null;commandPage++;publishQueued=true;
                }
            }
        }
    }
    private void cancelCommand(){commandTask=null;commandDimension=null;commandAssembly=null;commandPage=0;}
    private UUID pending;
    private ResourceLocation pendingDimension, currentDimension;
    private String generation = "";
    private Map<ResourceLocation,String> layouts=Map.of();
    private String layout(ResourceLocation dimension){return layouts.isEmpty()?generation:layouts.getOrDefault(dimension,"");}
    private int expectedParts, members;
    private long startedAt, revision;
    private volatile Map<ResourceLocation,Snapshot> snapshots = Map.of();
    public ScannerOverlayState(LongSupplier clock) { this.clock = clock; }
    public Snapshot snapshot() { return currentDimension == null ? new Snapshot(null,List.of(),revision) : snapshots.getOrDefault(currentDimension, new Snapshot(currentDimension,List.of(),revision)); }
    public Map<ResourceLocation,Snapshot> snapshots() { return snapshots; }
    public long revision() { return revision; }
    public void catalogue(MiningNetwork.Catalog packet) {
        shown.keySet().removeIf(k->!layout(k.dimension()).equals(packet.layout(k.dimension())));
        if(pendingDimension!=null&&!layout(pendingDimension).equals(packet.layout(pendingDimension)))cancel();
        if(commandDimension!=null&&!layout(commandDimension).equals(packet.layout(commandDimension)))cancelCommand();
        generation = packet.generation();
        layouts=packet.layouts();
        Map<ResourceLocation,MiningNetwork.Entry> entries = new HashMap<>(); packet.entries().forEach(e -> entries.put(e.id(),e)); catalogue = Map.copyOf(entries);
        publish();
    }
    private Mark metadata(Mark mark) {
        var entry = catalogue.get(mark.vein());
        return entry == null ? mark : new Mark(mark.chunk(),mark.vein(),entry.name(),mark.centre(),entry.color());
    }
    public void accept(ScannerOverlayPayload packet, ResourceLocation dimension) {
        if (packet.stage() == ScannerOverlayPayload.Stage.CLEAR) { clear(); return; }
        if (!packet.dimension().equals(dimension) || !layout(dimension).equals(packet.generation())) return;
        currentDimension = dimension;
        if (packet.stage() == ScannerOverlayPayload.Stage.START) {
            cancel(); pending = packet.scan(); pendingDimension = dimension; startedAt = clock.getAsLong(); return;
        }
        if (!packet.scan().equals(pending) || !packet.dimension().equals(pendingDimension)) return;
        if (expectedParts != 0 && expectedParts != packet.parts()) { cancel(); return; }
        expectedParts = packet.parts();
        var previous = fragments.putIfAbsent(packet.part(),packet.marks());
        if (previous != null) { if (!previous.equals(packet.marks())) cancel(); return; }
        members += packet.marks().size();
        if (members > ScannerOverlayPayload.MAX_TOTAL_CHUNKS) { cancel(); return; }
        if (fragments.size() != expectedParts) return;
        Set<Long> cells = new HashSet<>(); Map<Key,List<Mark>> groups = new LinkedHashMap<>();
        for (int i=0;i<expectedParts;i++) for (var mark : fragments.get(i)) {
            if (!cells.add(mark.chunk().toLong())) { cancel(); return; }
            var group = groups.computeIfAbsent(new Key(dimension,mark.key()),k -> new ArrayList<>()); group.add(mark);
            if (group.size() > ScannerOverlayPayload.MAX_VEIN_CHUNKS) { cancel(); return; }
        }
        if (groups.size() > ScannerOverlayPayload.MAX_INSTANCES) { cancel(); return; }
        long now = clock.getAsLong(); groups.forEach((k,v) -> shown.put(k,new Timed(List.copyOf(v),now)));
        cancel(); publish();
    }
    public void tick(ResourceLocation dimension, boolean alive, int durationSeconds) {
        long now = clock.getAsLong();
        if(commandTask!=null && (!alive || !Objects.equals(commandDimension,dimension) || now-commandUpdated>=120_000_000_000L))cancelCommand();
        if (pending != null && (!alive || !Objects.equals(pendingDimension,dimension) || now-startedAt >= 30_000_000_000L)) cancel();
        boolean changed = shown.values().removeIf(v -> now-v.shownAt >= durationSeconds*1_000_000_000L);
        if (!Objects.equals(currentDimension,dimension)) { currentDimension = dimension; changed = true; }
        if (changed || publishQueued && now-lastPublish>=250_000_000L) publish();
    }
    private void publish() {
        publishQueued=false;lastPublish=clock.getAsLong();
        revision++; Map<ResourceLocation,List<Mark>> grouped = new HashMap<>();
        shown.forEach((k,v) -> v.marks.forEach(m -> grouped.computeIfAbsent(k.dimension,d -> new ArrayList<>()).add(metadata(m))));
        Map<ResourceLocation,Snapshot> next = new HashMap<>(); grouped.forEach((d,m) -> next.put(d,new Snapshot(d,m,revision))); snapshots = Map.copyOf(next);
    }
    private void cancel() { pending = null; pendingDimension = null; fragments.clear(); expectedParts = members = 0; }
    public void clear() { cancelCommand(); cancel(); shown.clear(); currentDimension = null; publish(); }
}

