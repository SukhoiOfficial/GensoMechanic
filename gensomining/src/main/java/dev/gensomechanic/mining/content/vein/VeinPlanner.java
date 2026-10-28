package dev.gensomechanic.mining.content.vein;

import java.util.*;
import net.minecraft.world.level.ChunkPos;
import static dev.gensomechanic.mining.content.vein.VeinGenerator.*;

/** District proposal and resolution are separate. Only raw neighbours can veto members;
 * no rejected result can recursively promote a later district. All work is server-thread local. */
final class VeinPlanner {
    private static final class Yield extends RuntimeException { Yield(){super(null,null,false,false);} }
    private static final Yield YIELD=new Yield();
    record District(VeinSites.Site site,VeinResources.Category category,List<Occurrence> veins,VeinWorldDefinition.VeinCount count) {}
    private static final class ProposalWork {
        VeinResources.Category category; boolean selected;
        VeinWorldDefinition.VeinCount count;
        int slot,attempt,biomeCursor,validProfile,validVein;
        VeinResources.Selection selection; List<ChunkPos> cells;
        ChunkPos center; double u,v;
        final List<Occurrence> veins=new ArrayList<>();
        final List<double[]> offsets=new ArrayList<>();
        final Set<ChunkPos> blocked=new HashSet<>();
    }
    private static final class SolveWork {List<VeinSites.Site> peers;int cursor;Set<ChunkPos> targets;final Set<ChunkPos> blocked=new HashSet<>();}
    private static final class Lookup {List<VeinSites.Site> roots;int cursor;}
    private final Dimension dimension;
    private final Environment environment;
    private final VeinSites sites;
    private final VeinResources resources;
    private final VeinCache<VeinSites.Key,Optional<District>> proposals,solved;
    private final VeinCache<VeinSites.Key,ProposalWork> partial=new VeinCache<>(512,v->1);
    private final VeinCache<VeinSites.Key,SolveWork> resolving=new VeinCache<>(256,v->1);
    private final VeinCache<Long,Lookup> lookups=new VeinCache<>(256,v->1);
    private int remaining=Integer.MAX_VALUE;private long deadline=Long.MAX_VALUE;
    private long plans,retained,rejected,shapeTests,biomeMisses,collisionTrials;
    record Statistics(long jointPlans,long fallbackPlans,long rawCandidates,long retained,long relocated,long shrunk,
                      long rejected,long shapeTests,long biomeMisses,long collisionTrials,long topologyTrials,long budgetMisses) {}
    Statistics statistics(){return new Statistics(plans,0,proposals.size(),retained,0,0,rejected,shapeTests,biomeMisses,collisionTrials,0,0);}
    VeinPlanner(VeinGenerator generator,Environment environment) {
        this.environment=environment;dimension=generator.dimension(environment.dimension());
        resources=dimension==null?null:new VeinResources(dimension,environment,this::checkpoint,this::completed,generator.limits.cells()/2);
        sites=dimension==null?null:new VeinSites(typeSeed(environment.seed(),environment.dimension(),dimension.profile().id()),
            dimension.profile().placement(),this::checkpoint,this::completed,resources::environment,generator.limits.cells());
        proposals=new VeinCache<>(generator.limits.cells()/4,VeinPlanner::weight);
        solved=new VeinCache<>(generator.limits.cells()/4,VeinPlanner::weight);
    }
    private static int weight(Optional<District> d){return d.map(v->Math.max(1,v.veins.stream().mapToInt(o->o.chunks().size()).sum())).orElse(1);}
    private void checkpoint(){if(remaining<=0||System.nanoTime()>=deadline)throw YIELD;}
    private void completed(){if(remaining<Integer.MAX_VALUE)remaining--;}
    Optional<Optional<Occurrence>> tryFind(ChunkPos chunk,int budget){remaining=Math.max(1,budget);deadline=Long.MAX_VALUE;return query(chunk);}
    Optional<Optional<Occurrence>> tryFindUntil(ChunkPos chunk,long until){remaining=Integer.MAX_VALUE;deadline=until;return query(chunk);}
    Optional<Occurrence> find(ChunkPos chunk){remaining=Integer.MAX_VALUE;deadline=Long.MAX_VALUE;return query(chunk).orElseThrow();}
    private Optional<Optional<Occurrence>> query(ChunkPos chunk) {
        if(dimension==null||dimension.definitions().isEmpty()||dimension.profile().placement().proposalMean()==0)return Optional.of(Optional.empty());
        try {
            var lookup=lookups.computeIfAbsent(chunk.toLong(),k->new Lookup());
            if(lookup.roots==null){int r=dimension.reach();lookup.roots=sites.near(chunk.x-r,chunk.z-r,chunk.x+r,chunk.z+r);}
            while(lookup.cursor<lookup.roots.size()) {
                checkpoint();var result=solve(lookup.roots.get(lookup.cursor));lookup.cursor++;
                if(result.isPresent())for(var vein:result.get().veins)if(vein.chunks().contains(chunk)) {
                    lookups.remove(chunk.toLong());return Optional.of(Optional.of(vein));
                }
            }
            lookups.remove(chunk.toLong());return Optional.of(Optional.empty());
        } catch(Yield ignored){return Optional.empty();}
    }
    private Optional<District> proposal(VeinSites.Site site) {
        var saved=proposals.get(site.key());if(saved!=null)return saved;
        var w=partial.computeIfAbsent(site.key(),k->new ProposalWork());var cfg=dimension.profile().districts();
        if(!w.selected){w.category=resources.category(site);w.count=resources.districtCount(site.pos());w.selected=true;}
        if(w.category==null)return finishProposal(site,Optional.empty());
        int wanted=w.count.min()+(int)(unit(mix(site.seed()^41))*(w.count.max()-w.count.min()+1));
        boolean patch=unit(mix(site.seed()^42))<cfg.patchFraction();
        double theta=unit(mix(site.seed()^43))*Math.PI*2,c=Math.cos(theta),s=Math.sin(theta),bend=(unit(mix(site.seed()^44))*2-1)*.23;
        while(w.slot<wanted) {
            checkpoint();long child=mix(site.seed()^mix(w.slot+100));
            while(w.attempt<cfg.attempts()) {
                checkpoint();
                if(w.cells==null) {
                    var rng=new SplittableRandom(mix(child^mix(w.attempt+500)));
                    if(patch) {
                        if(w.slot==0&&w.attempt==0){w.u=0;w.v=0;}
                        else if(!w.offsets.isEmpty()&&w.attempt<cfg.attempts()*2/3) {
                            var base=w.offsets.get(rng.nextInt(w.offsets.size()));double angle=rng.nextDouble()*Math.PI*2,distance=cfg.step()*(.85+rng.nextDouble()*.45);
                            w.u=base[0]+Math.cos(angle)*distance;w.v=base[1]+Math.sin(angle)*distance;
                        }else {w.u=(rng.nextDouble()*2-1)*cfg.radius()*.385;w.v=(rng.nextDouble()*2-1)*cfg.radius()*.308;}
                        if(square(w.u/(cfg.radius()*12d/26))+square(w.v/(cfg.radius()*9d/26))>1){next(w);completed();continue;}
                    }else {
                        w.u=(w.slot-(wanted-1)/2d)*cfg.step()+(rng.nextDouble()*2-1)*cfg.step()*.24;
                        w.v=bend*w.u*w.u/cfg.step()+(rng.nextDouble()*2-1)*cfg.step()*.31;
                    }
                    w.center=new ChunkPos((int)Math.round(site.x()+c*w.u-s*w.v),(int)Math.round(site.z()+s*w.u+c*w.v));
                    if(Math.hypot(w.center.x-site.x(),w.center.z-site.z())>cfg.radius()
                        ||w.veins.stream().anyMatch(v->distance(v.identity().centre(),w.center)<Math.min(4,cfg.step()*.69))){next(w);completed();continue;}
                    w.selection=resources.member(w.category,w.center,child);
                    if(w.selection==null){next(w);completed();continue;}
                    var d=w.selection.definition();
                    var shape=VeinShapes.template(w.selection.size(),d.shape(),child,w.attempt%8,8);shapeTests++;
                    if(shape.isEmpty()){next(w);completed();continue;}
                    var cells=shape.stream().map(p->new ChunkPos(w.center.x+p.x,w.center.z+p.z)).toList();
                    if(cells.stream().anyMatch(w.blocked::contains)){collisionTrials++;next(w);completed();continue;}
                    w.cells=cells;w.biomeCursor=0;w.validProfile=0;w.validVein=0;
                }
                var d=w.selection.definition();
                boolean disabled=false;
                while(w.biomeCursor<w.cells.size()) {
                    checkpoint();var cell=w.cells.get(w.biomeCursor);
                    if(!resources.permits(d,cell)){disabled=true;break;}
                    int bits=resources.memberBits(d,cell);
                    if((bits&1)!=0)w.validProfile++;if((bits&2)!=0)w.validVein++;w.biomeCursor++;completed();
                }
                if(disabled){biomeMisses++;next(w);continue;}
                if(w.validProfile+1e-12<w.cells.size()*dimension.profile().memberFraction()
                    ||w.validVein+1e-12<w.cells.size()*d.memberFraction()) {biomeMisses++;next(w);continue;}
                long hash=child;for(var p:w.cells)hash=mix(hash^p.toLong());
                var id=new Identity(environment.dimension(),d.id(),w.center);
                w.veins.add(new Occurrence(d,w.selection.capacity(),id,w.cells,dimension.signature()+":"+Long.toHexString(hash)));
                w.offsets.add(new double[]{w.u,w.v});dilate(w.cells,dimension.gap(),w.blocked);
                break;
            }
            w.slot++;w.attempt=0;w.cells=null;w.selection=null;completed();
        }
        return finishProposal(site,w.veins.size()<w.count.min()?Optional.empty():Optional.of(new District(site,w.category,List.copyOf(w.veins),w.count)));
    }
    private static void next(ProposalWork w){w.attempt++;w.cells=null;w.selection=null;}
    private Optional<District> finishProposal(VeinSites.Site site,Optional<District> result){partial.remove(site.key());proposals.put(site.key(),result);return result;}
    private static int order(District a,District b) {
        int p=Integer.compare(b.category.resource().priority(),a.category.resource().priority());
        return p==0?VeinSites.order(a.site,b.site):p;
    }
    private Optional<District> solve(VeinSites.Site site) {
        var cached=solved.get(site.key());if(cached!=null)return cached;
        var candidate=proposal(site);if(candidate.isEmpty()){solved.put(site.key(),candidate);return candidate;}
        var own=candidate.get();var work=resolving.computeIfAbsent(site.key(),k->new SolveWork());
        if(work.targets==null){work.targets=new HashSet<>();own.veins.forEach(v->work.targets.addAll(v.chunks()));}
        if(work.peers==null){int r=2*dimension.reach()+dimension.gap()+2;var p=site.pos();work.peers=sites.near(p.x-r,p.z-r,p.x+r,p.z+r);}
        while(work.cursor<work.peers.size()) {
            checkpoint();var other=work.peers.get(work.cursor);
            if(!other.key().equals(site.key())) {
                var peer=proposal(other);
                if(peer.isPresent()&&order(peer.get(),own)<0)
                    for(var vein:peer.get().veins)for(var cell:vein.chunks())for(int z=-dimension.gap();z<=dimension.gap();z++)for(int x=-dimension.gap();x<=dimension.gap();x++) {
                        var p=new ChunkPos(cell.x+x,cell.z+z);if(work.targets.contains(p))work.blocked.add(p);
                    }
            }
            work.cursor++;completed();
        }
        var kept=own.veins.stream().filter(v->v.chunks().stream().noneMatch(work.blocked::contains)).toList();
        kept=largestComponent(kept,dimension.profile().districts().linkDistance());
        Optional<District> result;
        if(kept.size()<own.count.min()){result=Optional.empty();rejected++;}
        else {
            var districtId=new DistrictIdentity(own.category.resource().category(),site.pos(),site.seed(),kept.size());
            kept=kept.stream().map(v->new Occurrence(v.definition(),v.capacity(),v.identity(),v.chunks(),v.signature(),districtId)).toList();
            result=Optional.of(new District(site,own.category,kept,own.count));retained++;
        }
        plans++;resolving.remove(site.key());solved.put(site.key(),result);return result;
    }
    private static List<Occurrence> largestComponent(List<Occurrence> veins,double maxDistance) {
        List<Occurrence> best=List.of();boolean[] seen=new boolean[veins.size()];
        for(int i=0;i<veins.size();i++)if(!seen[i]) {
            var indices=new ArrayList<Integer>();indices.add(i);seen[i]=true;
            for(int k=0;k<indices.size();k++)for(int j=0;j<veins.size();j++)
                if(!seen[j]&&distance(veins.get(indices.get(k)).identity().centre(),veins.get(j).identity().centre())<=maxDistance){seen[j]=true;indices.add(j);}
            if(indices.size()>best.size())best=indices.stream().map(veins::get).toList();
        }
        return best;
    }
    private static double distance(ChunkPos a,ChunkPos b){return Math.hypot(a.x-(double)b.x,a.z-(double)b.z);}
    private static double square(double n){return n*n;}
    private static void dilate(List<ChunkPos> cells,int gap,Set<ChunkPos> result){for(var p:cells)for(int z=-gap;z<=gap;z++)for(int x=-gap;x<=gap;x++)result.add(new ChunkPos(p.x+x,p.z+z));}
    Map<String,Integer> cachePeaks(){var result=new LinkedHashMap<String,Integer>();if(sites!=null){result.putAll(sites.peaks());result.put("biome_columns",resources.peak());}result.put("raw_members",proposals.peak());result.put("solved_members",solved.peak());result.put("partial_districts",partial.peak());result.put("partial_queries",lookups.peak());return result;}
    void clear(){if(sites!=null){sites.clear();resources.clear();}proposals.clear();solved.clear();partial.clear();resolving.clear();lookups.clear();}
}
