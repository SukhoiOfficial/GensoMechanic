package dev.gensomechanic.mining.content.vein;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Server-thread scheduling: all players, commands and miner lookups share one 4 ms planning budget. */
public final class VeinPlanningScheduler {
    private record Key(ServerLevel level,ChunkPos chunk) {}
    private static final class State {
        final LinkedHashMap<Key,Integer> miners=new LinkedHashMap<>();
        int next,tick=Integer.MIN_VALUE;long lastPump,totalNanos,maxNanos,pumps;boolean lastPaused;
    }
    private static final Map<MinecraftServer,State> STATES=new IdentityHashMap<>();
    public static Optional<Optional<VeinGenerator.Occurrence>> miner(ServerLevel level,ChunkPos chunk) {
        var ready=VeinManager.query(level).peek(chunk);if(ready.isPresent())return ready;
        var state=STATES.computeIfAbsent(level.getServer(),ignored->new State());
        state.miners.put(new Key(level,chunk),level.getServer().getTickCount());return Optional.empty();
    }
    public static void pump(MinecraftServer server,boolean paused) {
        var state=STATES.computeIfAbsent(server,ignored->new State());long start=System.nanoTime();
        int tick=server.getTickCount();
        if(paused){if(start-state.lastPump<50_000_000L)return;}
        else if(state.tick==tick||state.lastPaused&&start-state.lastPump<50_000_000L)return;
        // A queued paused-map callback followed by a resumed tick cannot double-spend the same interval.
        state.tick=tick;state.lastPump=start;state.lastPaused=paused;long deadline=start+4_000_000L;int idle=0;
        while(System.nanoTime()<deadline&&idle<3) {
            long slice=Math.min(deadline,System.nanoTime()+750_000L);
            boolean work=switch(Math.floorMod(state.next++,3)){case 0->minerStep(server,state,slice);case 1->ScannerService.pumpOne(server,slice);default->VeinSurveyCommand.pumpOne(server,slice);};
            idle=work?0:idle+1;
        }
        long spent=System.nanoTime()-start;state.totalNanos+=spent;state.maxNanos=Math.max(state.maxNanos,spent);state.pumps++;
    }
    private static boolean minerStep(MinecraftServer server,State state,long deadline) {
        if(state.miners.isEmpty())return false;
        var first=state.miners.entrySet().iterator().next();var key=first.getKey();int requested=first.getValue();state.miners.remove(key);
        if(server.getTickCount()-requested>2||!key.level().hasChunk(key.chunk().x,key.chunk().z))return true;
        var answer=VeinManager.query(key.level()).tryFindUntil(key.chunk(),deadline);
        if(answer.isEmpty())state.miners.put(key,requested);return true;
    }
    static void unload(ServerLevel level){var state=STATES.get(level.getServer());if(state!=null)state.miners.keySet().removeIf(k->k.level()==level);}
    static void stop(MinecraftServer server){STATES.remove(server);}
    record Statistics(long pumps,long cpuNanos,long maxNanos){}
    static Statistics statistics(MinecraftServer server){var s=STATES.get(server);return s==null?new Statistics(0,0,0):new Statistics(s.pumps,s.totalNanos,s.maxNanos);}
    private VeinPlanningScheduler(){}
}
