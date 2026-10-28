package dev.gensomechanic.mining.content.vein;

import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.network.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=GensoMining.MOD_ID)
public final class ArchiveService {
    private static final ModConfigSpec.Builder B=new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue PAGE_SIZE=B.defineInRange("archivePageInstances",8,1,32);
    public static final ModConfigSpec.IntValue SEND_BUDGET=B.defineInRange("archiveFragmentsPerTick",2,1,8);
    public static final ModConfigSpec.IntValue CACHE_REGIONS=B.defineInRange("archiveCachedRegions",256,16,4096);
    public static final ModConfigSpec CONFIG=B.build();
    private static final Map<UUID,ArchiveNetwork.Status> STATES=new HashMap<>();
    private static final Map<UUID,Job> JOBS=new HashMap<>();
    private static final Map<UUID,Long> LAST_REQUEST=new HashMap<>();
    private static final class Job {
        final ArchiveNetwork.Request request; final ExplorationData data; final List<VeinGenerator.Identity> ids;
        final List<ScannerOverlayPayload.Mark> marks=new ArrayList<>(); final int end; int cursor,send;
        List<ScannerOverlayPayload> fragments;
        Job(ArchiveNetwork.Request request,ExplorationData data) {
            this.request=request; this.data=data; ids=data.in(new ExplorationData.Region(request.dimension(),request.regionX(),request.regionZ()));
            cursor=Math.min(request.offset(),ids.size()); end=Math.min(ids.size(),cursor+PAGE_SIZE.get());
        }
    }
    public static ArchiveNetwork.Status status(ServerPlayer player) {
        var stack=ExplorationArchiveItem.held(player); boolean active=player.isAlive()&&!stack.isEmpty();
        var selection=active ? ScannerService.normalize(player.server,ScannerTarget.archive(stack),true) : ScanSelection.ALL;
        if(active && !selection.equals(ScannerTarget.archive(stack))) { stack.set(ScannerTarget.ARCHIVE,selection); player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); }
        return new ArchiveNetwork.Status(active,active?ExplorationData.get(player).revision():0,VeinManager.signature(player.server),selection);
    }
    public static void invalidate(ServerPlayer player) { JOBS.remove(player.getUUID()); LAST_REQUEST.remove(player.getUUID()); }
    public static void request(ServerPlayer player,ArchiveNetwork.Request request) {
        var state=status(player);
        if(!state.active() || state.revision()!=request.revision() || !state.generation().equals(request.generation()) || !state.selection().equals(request.selection())) return;
        long tick=System.nanoTime();
        if(JOBS.containsKey(player.getUUID()) || tick-LAST_REQUEST.getOrDefault(player.getUUID(),tick-100_000_000L)<100_000_000L) return;
        LAST_REQUEST.put(player.getUUID(),tick); JOBS.put(player.getUUID(),new Job(request,ExplorationData.get(player)));
    }
    public static void index(ServerPlayer player,ArchiveNetwork.IndexRequest request) {
        var state=status(player); long tick=System.nanoTime();
        if(!state.active() || state.revision()!=request.revision() || !state.generation().equals(request.generation())
            || tick-LAST_REQUEST.getOrDefault(player.getUUID(),tick-100_000_000L)<100_000_000L || JOBS.containsKey(player.getUUID()))return;
        LAST_REQUEST.put(player.getUUID(),tick);
        var keys=ExplorationData.get(player).regionKeys(request.dimension()); int start=Math.min(request.offset(),keys.size()),end=Math.min(start+2048,keys.size());
        PacketDistributor.sendToPlayer(player,new ArchiveNetwork.IndexPage(request.request(),end<keys.size()?end:-1,keys.subList(start,end)));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { pump(event.getServer()); }
    /** Also called on the integrated server thread while its world simulation is paused by the map. */
    public static void pump(net.minecraft.server.MinecraftServer server) {
        for(var player:server.getPlayerList().getPlayers()) {
            var state=status(player); var old=STATES.put(player.getUUID(),state);
            if(!state.equals(old)) { JOBS.remove(player.getUUID()); PacketDistributor.sendToPlayer(player,state); }
            var job=JOBS.get(player.getUUID()); if(job==null) continue;
            if(job.cursor<job.end) {
                var d=job.data.get(job.ids.get(job.cursor++)); var definition=VeinManager.definitions(player.server).get(d.identity().type());
                if(definition!=null && state.selection().matches(definition) && d.validate(player.server,state.generation())) job.marks.addAll(d.marks(player.server));
                continue; // At most one potentially expensive geometry revalidation per player per tick.
            }
            if(job.fragments==null) job.fragments=ScannerOverlayPayload.fragments(job.request.request(),job.request.dimension(),state.generation(),job.marks,player.registryAccess(),28);
            for(int n=0;n<SEND_BUDGET.get() && job.send<job.fragments.size();n++) PacketDistributor.sendToPlayer(player,
                new ArchiveNetwork.Page(job.request.request(),state.revision(),job.end<job.ids.size()?job.end:-1,job.fragments.get(job.send++)));
            if(job.send==job.fragments.size()) JOBS.remove(player.getUUID());
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { var id=e.getEntity().getUUID(); STATES.remove(id); JOBS.remove(id); LAST_REQUEST.remove(id); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { STATES.clear(); JOBS.clear(); LAST_REQUEST.clear(); }
    private ArchiveService(){}
}
