package dev.gensomechanic.mining.content.vein;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.network.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=GensoMining.MOD_ID)
public final class VeinSurveyCommand {
    public static final int MAX_RADIUS=1024;
    private static final Map<UUID,Job> JOBS=new LinkedHashMap<>();
    private static final Map<UUID,Report> REPORTS=new HashMap<>();
    private record Row(Component name, ChunkPos centre, int chunks) {}
    private record Report(String generation,ResourceLocation dimension,List<Row> rows) {}
    private static final class Job {
        final ServerPlayer player;final ServerLevel level;final UUID id=UUID.randomUUID();final String generation;
        final double x,z;final int radius,minX,maxX,maxZ;final ResourceLocation target;
        final Set<VeinGenerator.Identity> found=new HashSet<>();final List<Row> rows=new ArrayList<>();
        final List<ScannerOverlayPayload.Mark> marks=new ArrayList<>();
        List<ScannerOverlayPayload> fragments=List.of();int cx,cz,page,sent,pageInstances;
        Job(CommandSourceStack source,ServerPlayer player,int radius,ResourceLocation target) {
            this.player=player;level=source.getLevel();x=source.getPosition().x;z=source.getPosition().z;
            this.radius=radius;this.target=target;generation=VeinManager.signature(player.serverLevel());
            var bounds=VeinScan.bounds(x,z,radius);minX=bounds.minX();maxX=bounds.maxX();cx=minX;
            cz=bounds.minZ();maxZ=bounds.maxZ();
        }
        boolean done(){return cz>maxZ;}
        void advance(){if(++cx>maxX){cx=minX;cz++;}}
        boolean valid(){return !player.isRemoved()&&player.isAlive()&&player.serverLevel()==level&&generation.equals(VeinManager.signature(player.serverLevel()));}
        void control(VeinSurveyPayload.Kind kind){PacketDistributor.sendToPlayer(player,new VeinSurveyPayload(id,page,kind,new ScannerOverlayPayload(id,level.dimension().location(),ScannerOverlayPayload.Stage.START,List.of(),generation,0,1)));}
        void flush(){
            if(marks.isEmpty())return;
            fragments=ScannerOverlayPayload.fragments(id,level.dimension().location(),generation,marks,player.registryAccess(),32);
            marks.clear();pageInstances=0;sent=0;
        }
        void send(){
            if(sent<fragments.size())PacketDistributor.sendToPlayer(player,new VeinSurveyPayload(id,page,VeinSurveyPayload.Kind.PAGE,fragments.get(sent++)));
            if(!fragments.isEmpty()&&sent==fragments.size()){fragments=List.of();sent=0;page++;}
        }
    }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var target=Commands.argument("target",StringArgumentType.greedyString()).suggests((c,b)->{
            List<String> values=new ArrayList<>(List.of("all"));
            VeinManager.definitions(c.getSource().getServer()).keySet().forEach(id->values.add(id.toString()));
            return SharedSuggestionProvider.suggest(values,b);
        }).executes(c->start(c.getSource(),IntegerArgumentType.getInteger(c,"radius"),StringArgumentType.getString(c,"target")));
        var scan=Commands.literal("vein").then(Commands.literal("scan")
            .then(Commands.argument("radius",IntegerArgumentType.integer(1,MAX_RADIUS)).then(target)));
        var results=Commands.literal("vein_results").then(Commands.argument("page",IntegerArgumentType.integer(1))
            .executes(c->page(c.getSource(),IntegerArgumentType.getInteger(c,"page"))));
        event.getDispatcher().register(Commands.literal("gensomining").requires(s->s.hasPermission(2)).then(scan).then(results));
    }

    public static int start(CommandSourceStack source,int radius,String targetText) throws CommandSyntaxException {
        if(!source.hasPermission(2)||radius<1||radius>MAX_RADIUS)throw error("permission");
        var player=source.getPlayerOrException();
        if(player.serverLevel()!=source.getLevel())throw error("dimension");
        var target=targetText.equals("all")?null:ResourceLocation.tryParse(targetText);
        if(!targetText.equals("all")&&(target==null||!VeinManager.definitions(player.server).containsKey(target)))throw error("unknown");
        var previous=JOBS.remove(player.getUUID());if(previous!=null){previous.control(VeinSurveyPayload.Kind.CANCEL);player.sendSystemMessage(message("cancelled"));}
        REPORTS.remove(player.getUUID());var job=new Job(source,player,radius,target);JOBS.put(player.getUUID(),job);
        PacketDistributor.sendToPlayer(player,ScannerService.catalogue(player.server));job.control(VeinSurveyPayload.Kind.START);
        source.sendSuccess(()->message("started",radius,targetText),false);
        return 1;
    }
    public static void pump(MinecraftServer server) {
        VeinPlanningScheduler.pump(server,true);
    }
    static boolean pumpOne(MinecraftServer server,long deadline) {
        // Rotate queued players so a large job cannot monopolize subsequent ticks.
        for(var id:new ArrayList<>(JOBS.keySet())) {
            Job job=JOBS.get(id);if(job==null||job.player.server!=server)continue;
            JOBS.remove(id);
            if(!job.valid()){job.control(VeinSurveyPayload.Kind.CANCEL);job.player.sendSystemMessage(message("cancelled"));continue;}
            try {
                if(!job.fragments.isEmpty())job.send();
                else {
                    int checked=0;
                    while(!job.done()&&checked++<64&&System.nanoTime()<deadline) {
                        var p=new ChunkPos(job.cx,job.cz);
                        if(VeinScan.intersects(job.x,job.z,job.radius,p)) {
                            var ready=VeinManager.query(job.level).tryFindUntil(p,deadline);
                            if(ready.isEmpty())continue;
                            var vein=ready.get();
                            if(vein.isPresent()&&(job.target==null||job.target.equals(vein.get().definition().id()))&&job.found.add(vein.get().identity())) {
                                var v=vein.get();ExplorationData.get(job.player).discover(v,job.generation);
                                job.rows.add(new Row(v.definition().name(),v.identity().centre(),v.chunks().size()));
                                for(var cell:v.chunks())job.marks.add(new ScannerOverlayPayload.Mark(cell,v.definition().id(),v.definition().name(),v.identity().centre(),v.definition().mapColor()));
                                job.pageInstances++;
                            }
                        }
                        job.advance();
                        if(job.pageInstances>=32||job.marks.size()>=8192)break;
                    }
                    job.flush();
                }
                if(job.done()&&job.fragments.isEmpty()) {
                    job.control(VeinSurveyPayload.Kind.END);
                    REPORTS.put(id,new Report(job.generation,job.level.dimension().location(),List.copyOf(job.rows)));
                    job.player.sendSystemMessage(message("complete",job.rows.size()).append(" ").append(pageLink(1)));
                } else JOBS.put(id,job);
            } catch(RuntimeException ex) {
                GensoMining.LOGGER.error("Vein survey failed for {}",id,ex);job.control(VeinSurveyPayload.Kind.CANCEL);job.player.sendSystemMessage(message("failed"));
            }
            return true;
        }
        return false;
    }
    private static int page(CommandSourceStack source,int number) throws CommandSyntaxException {
        if(!source.hasPermission(2))throw error("permission");var player=source.getPlayerOrException();var report=REPORTS.get(player.getUUID());
        if(report==null||!report.generation.equals(VeinManager.signature(player.serverLevel()))||!report.dimension.equals(player.level().dimension().location()))throw error("no_results");
        int pages=Math.max(1,(report.rows.size()+9)/10);if(number>pages)throw error("no_results");
        source.sendSuccess(()->message("page",number,pages),false);
        for(var row:report.rows.subList((number-1)*10,Math.min(number*10,report.rows.size())))
            source.sendSuccess(()->message("row",row.name,row.centre.getMiddleBlockX(),row.centre.getMiddleBlockZ(),row.chunks),false);
        if(number<pages)source.sendSuccess(()->pageLink(number+1),false);return report.rows.size();
    }
    private static MutableComponent pageLink(int page){return message("view_page",page).withStyle(s->s.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/gensomining vein_results "+page)));}
    private static MutableComponent message(String key,Object...args){return Component.translatable("command.gensomining.vein."+key,args);}
    private static CommandSyntaxException error(String key){return new SimpleCommandExceptionType(message(key)).create();}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){JOBS.remove(e.getEntity().getUUID());REPORTS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){JOBS.clear();REPORTS.clear();}
    static boolean running(UUID player){return JOBS.containsKey(player);}
    private VeinSurveyCommand(){}
}

