package dev.gensomechanic.mining.client;

import dev.gensomechanic.mining.content.vein.*;
import dev.gensomechanic.mining.network.*;
import dev.gensomechanic.mining.network.ScannerOverlayPayload.Mark;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Main-thread region cache with visible regions pinned and off-screen LRU eviction. Eviction never deletes server-owned discoveries. */
public final class ArchiveClient {
    private record Cached(List<Mark> marks, int next, long revision, ScanSelection selection) {}
    private static final LinkedHashMap<ExplorationData.Region,Cached> CACHE=new LinkedHashMap<>(16,.75f,true);
    private static final LinkedHashMap<ExplorationData.Region,Long> WANTED=new LinkedHashMap<>();
    private static ArchiveNetwork.Status status=new ArchiveNetwork.Status(false,0,"",ScanSelection.ALL);
    private static String generation="";
    private static Map<ResourceLocation,String> layouts=Map.of();
    private static ScanSelection held=ScanSelection.NONE;
    private record Directory(List<Long> regions, int next, long revision) {}
    private record Viewport(double x,double z,double radius,long time) {}
    private static final Map<ResourceLocation,Directory> DIRECTORIES=new HashMap<>();
    private record ViewKey(ResourceLocation dimension, boolean minimap) {}
    private static final Map<ViewKey,Viewport> VIEWPORTS=new HashMap<>();
    private static ArchiveNetwork.IndexRequest pendingIndex;
    private static long lastSend,lastPausedPump;
    private static final java.util.concurrent.atomic.AtomicBoolean PUMP_QUEUED=new java.util.concurrent.atomic.AtomicBoolean();
    private static ArchiveNetwork.Request pending;
    private static final Map<Integer,List<Mark>> PARTS=new HashMap<>();
    private static int expected,nextOffset,members;
    private static long started,revision;
    public static void register() {
        ArchiveNetwork.statusReceiver=ArchiveClient::status;
        ArchiveNetwork.pageReceiver=ArchiveClient::page;
        ArchiveNetwork.indexReceiver=ArchiveClient::index;
        if(ExplorationArchiveItem.mapsAvailable()) ExplorationArchiveItem.openMap=dev.gensomechanic.mining.client.compat.xaero.ArchiveMapOpener::open;
    }
    public static long revision(){return revision;}
    public static boolean active(){return !held.equals(ScanSelection.NONE) && status.active() && status.generation().equals(generation);}
    private static ScanSelection localSelection() {
        var player=Minecraft.getInstance().player;
        if(player==null || !player.isAlive() || !ExplorationArchiveItem.mapsAvailable()) return ScanSelection.NONE;
        var stack=ExplorationArchiveItem.held(player); if(stack.isEmpty()) return ScanSelection.NONE;
        var selection=ScannerTarget.archive(stack);
        var entry=ScannerClient.entry(selection.type().orElse(null));
        return selection.all() || entry==null ? ScanSelection.ALL : ScanSelection.of(entry.variantOf()==null?entry.id():entry.variantOf());
    }
    public static void catalogue(MiningNetwork.Catalog catalog) {
        cancel();
        CACHE.keySet().removeIf(r->!Objects.equals(layouts.get(r.dimension()),catalog.layouts().get(r.dimension())));
        DIRECTORIES.keySet().removeIf(d->!Objects.equals(layouts.get(d),catalog.layouts().get(d)));
        layouts=catalog.layouts();
        generation=catalog.generation();
        // Family membership may change without changing generation geometry. Retain display while refreshing pages.
        CACHE.replaceAll((r,c)->new Cached(c.marks,c.next,-1,c.selection)); revision++;
    }
    private static void status(ArchiveNetwork.Status next) {
        if(!status.equals(next)) { cancel(); revision++; }
        status=next;
    }
    private static void cancel(){pendingIndex=null;pending=null; PARTS.clear(); expected=members=0;}
    public static void clear(){CACHE.clear();WANTED.clear();DIRECTORIES.clear();VIEWPORTS.clear();cancel();held=ScanSelection.NONE;generation="";layouts=Map.of();status=new ArchiveNetwork.Status(false,0,"",ScanSelection.ALL);revision++;}
    /** Render-thread demand, including rotation's enclosing box. Requests contain one region, never an unbounded rectangle. */
    public static void viewport(ResourceLocation dimension,double x,double z,double radius) {
        viewport(dimension,x,z,radius,false);
    }
    public static void viewport(ResourceLocation dimension,double x,double z,double radius,boolean minimap) {
        if(!active() || !ScannerOverlayConfig.ENABLED.get() || !Double.isFinite(radius) || radius<0) return;
        VIEWPORTS.put(new ViewKey(dimension,minimap),new Viewport(x,z,radius,System.nanoTime()));
    }
    private static void index(ArchiveNetwork.IndexPage page) {
        if(pendingIndex==null || !pendingIndex.request().equals(page.request()))return;
        var previous=DIRECTORIES.get(pendingIndex.dimension()); List<Long> regions=new ArrayList<>();
        if(pendingIndex.offset()>0 && previous!=null && previous.revision==pendingIndex.revision())regions.addAll(previous.regions);
        if(page.next()!=-1 && page.next()<=pendingIndex.offset()){cancel();return;}
        regions.addAll(page.regions());
        DIRECTORIES.put(pendingIndex.dimension(),new Directory(List.copyOf(regions),page.next(),pendingIndex.revision()));pendingIndex=null;
    }
    public static void tick() {
        var selection=localSelection(); if(!selection.equals(held)){held=selection;cancel();revision++;}
        long now=System.nanoTime();
        var mc=Minecraft.getInstance(); var server=mc.getSingleplayerServer();
        if((!held.equals(ScanSelection.NONE) || ScannerClient.OVERLAY.commandActive()) && mc.isPaused() && server!=null && now-lastPausedPump>=50_000_000L && PUMP_QUEUED.compareAndSet(false,true)) {
            lastPausedPump=now; server.execute(()->{try{ArchiveService.pump(server);dev.gensomechanic.mining.content.vein.VeinSurveyCommand.pump(server);}finally{PUMP_QUEUED.set(false);}});
        }
        VIEWPORTS.values().removeIf(v -> now-v.time>2_000_000_000L); WANTED.clear();
        VIEWPORTS.forEach((key,v)-> {
            var dimension=key.dimension; var directory=DIRECTORIES.get(dimension); if(directory==null)return;
            int minX=(int)Math.floor((v.x-v.radius)/512),maxX=(int)Math.floor((v.x+v.radius)/512);
            int minZ=(int)Math.floor((v.z-v.radius)/512),maxZ=(int)Math.floor((v.z+v.radius)/512);
            for(long region:directory.regions) {
                int x=net.minecraft.world.level.ChunkPos.getX(region),z=net.minecraft.world.level.ChunkPos.getZ(region);
                if(x>=minX && x<=maxX && z>=minZ && z<=maxZ)WANTED.put(new ExplorationData.Region(dimension,x,z),now);
            }
        });
        trim();
        if(!active() || !held.equals(status.selection()) || !ScannerOverlayConfig.ENABLED.get()) return;
        if(pending!=null || pendingIndex!=null) { if(now-started>30_000_000_000L) cancel(); else return; }
        if(now-lastSend<150_000_000L)return;
        for(var key:VIEWPORTS.keySet()) {
            var dimension=key.dimension; var directory=DIRECTORIES.get(dimension); boolean fresh=directory!=null && directory.revision==status.revision();
            if(fresh && directory.next<0)continue;
            pendingIndex=new ArchiveNetwork.IndexRequest(UUID.randomUUID(),dimension,fresh?directory.next:0,status.revision(),generation);
            started=lastSend=now;PacketDistributor.sendToServer(pendingIndex);return;
        }
        for(var key:WANTED.keySet()) {
            var cached=CACHE.get(key);
            boolean fresh=cached!=null && cached.revision==status.revision() && cached.selection.equals(held);
            if(fresh && cached.next<0) continue;
            int offset=fresh?cached.next:0;
            pending=new ArchiveNetwork.Request(UUID.randomUUID(),key.dimension(),key.x(),key.z(),offset,status.revision(),generation,held);
            started=lastSend=now; PacketDistributor.sendToServer(pending); break;
        }
    }
    private static void page(ArchiveNetwork.Page page) {
        if(pending==null || !pending.request().equals(page.request()) || pending.revision()!=page.revision() || !held.equals(pending.selection())) return;
        var fragment=page.fragment();
        if(!fragment.dimension().equals(pending.dimension()) || !fragment.generation().equals(generation)) return;
        if(expected!=0 && (expected!=fragment.parts() || nextOffset!=page.nextOffset())) {cancel();return;}
        expected=fragment.parts(); nextOffset=page.nextOffset();
        var old=PARTS.putIfAbsent(fragment.part(),fragment.marks()); if(old!=null) {if(!old.equals(fragment.marks()))cancel();return;}
        members+=fragment.marks().size(); if(members>ScannerOverlayPayload.MAX_TOTAL_CHUNKS){cancel();return;}
        if(PARTS.size()!=expected) return;
        var region=new ExplorationData.Region(pending.dimension(),pending.regionX(),pending.regionZ());
        Map<Long,Mark> merged=new LinkedHashMap<>();
        var cached=CACHE.get(region);
        if(cached!=null)cached.marks.forEach(m->merged.put(m.chunk().toLong(),m));
        Set<Long> cells=new HashSet<>();
        for(int i=0;i<expected;i++) for(var mark:PARTS.get(i)) {
            if(!cells.add(mark.chunk().toLong())){cancel();return;}
            merged.put(mark.chunk().toLong(),mark);
        }
        if(nextOffset!=-1 && nextOffset<=pending.offset()){cancel();return;}
        CACHE.put(region,new Cached(List.copyOf(merged.values()),nextOffset,pending.revision(),held));
        cancel();trim();revision++;
    }
    private static void trim() {
        int weight=CACHE.values().stream().mapToInt(c->c.marks.size()).sum(); boolean changed=false;
        while(CACHE.size()>ArchiveService.CACHE_REGIONS.get() || weight>1_048_576 && CACHE.size()>1) {
            var first=CACHE.keySet().stream().filter(k->!WANTED.containsKey(k)).findFirst();
            if(first.isEmpty())break;
            weight-=CACHE.remove(first.get()).marks.size();changed=true;
        }
        if(changed)revision++;
    }
    public static Map<ResourceLocation,List<Mark>> visible() {
        if(!active())return Map.of();
        Map<ResourceLocation,Map<Long,Mark>> byDimension=new HashMap<>();
        CACHE.forEach((region,cached)->cached.marks.forEach(mark->{
            var entry=ScannerClient.entry(mark.vein()); if(entry==null)return;
            if(!held.all() && !held.type().filter((entry.variantOf()==null?entry.id():entry.variantOf())::equals).isPresent())return;
            byDimension.computeIfAbsent(region.dimension(),d->new LinkedHashMap<>()).put(mark.chunk().toLong(),new Mark(mark.chunk(),mark.vein(),entry.name(),mark.centre(),entry.color()));
        }));
        Map<ResourceLocation,List<Mark>> result=new HashMap<>();byDimension.forEach((d,m)->result.put(d,List.copyOf(m.values())));return result;
    }
    private ArchiveClient(){}
}
