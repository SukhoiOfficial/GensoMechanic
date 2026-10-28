package dev.gensomechanic.mining.content.vein;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.level.ChunkPos;
import static dev.gensomechanic.mining.content.vein.VeinGenerator.*;

/** Locally reproducible Poisson proposals, thinned in a finite number of rounds. */
final class VeinSites {
    record Key(long bucket,int round,int index) {}
    record Site(Key key,long seed,double x,double z,double distance) {
        ChunkPos pos(){return new ChunkPos((int)Math.round(x),(int)Math.round(z));}
    }
    private record Bucket(long position,int round) {}
    private static final class Work { List<Site> peers; int cursor; }
    private final long seed;
    private final VeinWorldDefinition.Placement settings;
    private final Runnable checkpoint,completed;
    private final Predicate<ChunkPos> environment;
    private final VeinCache<Bucket,List<Site>> raw;
    private final VeinCache<Key,Boolean> accepted;
    private final VeinCache<Key,Work> partial;
    VeinSites(long seed,VeinWorldDefinition.Placement settings,Runnable checkpoint,Runnable completed,
              Predicate<ChunkPos> environment,int limit) {
        this.seed=seed;this.settings=settings;this.checkpoint=checkpoint;this.completed=completed;this.environment=environment;
        raw=new VeinCache<>(limit/4,v->Math.max(1,v.size()));accepted=new VeinCache<>(limit/2,v->1);partial=new VeinCache<>(1024,v->1);
    }
    private List<Site> raw(int bx,int bz,int round) {
        var key=new Bucket(ChunkPos.asLong(bx,bz),round);var found=raw.get(key);if(found!=null)return found;
        checkpoint.run();long salt=mix(seed^mix(key.position())^mix(round+31));var rng=new SplittableRandom(salt);
        double probability=Math.exp(-settings.proposalMean()),product=1;int count=0;
        // Bounded Poisson inverse sampling; the configured mean is at most four.
        while(count<64){product*=rng.nextDouble();if(product<=probability)break;count++;}
        List<Site> sites=new ArrayList<>();
        for(int i=0;i<count;i++) {
            double x=(double)bx*settings.bucket()+rng.nextDouble()*settings.bucket();
            double z=(double)bz*settings.bucket()+rng.nextDouble()*settings.bucket();
            sites.add(new Site(new Key(key.position(),round,i),mix(salt^mix(i+1000)),x,z,
                settings.distance()*(1+settings.jitter()*(rng.nextDouble()*2-1))));
        }
        found=List.copyOf(sites);raw.put(key,found);completed.run();return found;
    }
    static int order(Site a,Site b) {
        int c=Integer.compare(a.key.round,b.key.round);if(c!=0)return c;
        c=Long.compareUnsigned(a.seed,b.seed);if(c!=0)return c;
        c=Long.compare(a.key.bucket,b.key.bucket);return c!=0?c:Integer.compare(a.key.index,b.key.index);
    }
    private boolean overlaps(Site a,Site b){double d=(a.distance+b.distance)*.5;return square(a.x-b.x)+square(a.z-b.z)<d*d;}
    private static double square(double n){return n*n;}
    private boolean accepted(Site site) {
        var found=accepted.get(site.key);if(found!=null)return found;
        if(!environment.test(site.pos())){accepted.put(site.key,false);return false;}
        var work=partial.computeIfAbsent(site.key,k->new Work());
        if(work.peers==null) {
            double r=settings.distance()*(1+settings.jitter());List<Site> peers=new ArrayList<>();
            int b=settings.bucket();
            for(int round=0;round<=site.key.round;round++)
                for(int z=(int)Math.floor((site.z-r)/b);z<=(int)Math.floor((site.z+r)/b);z++)
                    for(int x=(int)Math.floor((site.x-r)/b);x<=(int)Math.floor((site.x+r)/b);x++)
                        for(var other:raw(x,z,round))if(order(other,site)<0&&overlaps(site,other))peers.add(other);
            work.peers=List.copyOf(peers);
        }
        while(work.cursor<work.peers.size()) {
            checkpoint.run();var other=work.peers.get(work.cursor);
            boolean blocks=other.key.round==site.key.round?environment.test(other.pos()):accepted(other);
            work.cursor++;completed.run();
            if(blocks){partial.remove(site.key);accepted.put(site.key,false);return false;}
        }
        partial.remove(site.key);accepted.put(site.key,true);return true;
    }
    List<Site> near(int minX,int minZ,int maxX,int maxZ) {
        List<Site> out=new ArrayList<>();int b=settings.bucket();
        for(int r=0;r<settings.rounds();r++)for(int z=Math.floorDiv(minZ-1,b);z<=Math.floorDiv(maxZ+1,b);z++)
            for(int x=Math.floorDiv(minX-1,b);x<=Math.floorDiv(maxX+1,b);x++)for(var site:raw(x,z,r)) {
                var p=site.pos();if(p.x>=minX&&p.x<=maxX&&p.z>=minZ&&p.z<=maxZ&&accepted(site))out.add(site);
            }
        out.sort(VeinSites::order);return out;
    }
    Map<String,Integer> peaks(){return Map.of("raw_site_members",raw.peak(),"accepted_sites",accepted.peak(),"partial_sites",partial.peak());}
    void clear(){raw.clear();accepted.clear();partial.clear();}
}
