package dev.gensomechanic.mining.content.vein;

import java.util.*;
import net.minecraft.world.level.ChunkPos;

/** Broad row bands with stepped edges. Every template is centred on a member chunk. */
final class VeinShapes {
    static List<List<ChunkPos>> templates(int size,VeinDefinition.Shape shape,long seed,int attempts) {
        List<List<ChunkPos>> result=new ArrayList<>();
        for(int attempt=0;attempt<attempts;attempt++){
            var cells=template(size,shape,seed,attempt,attempts);if(!cells.isEmpty()&&!result.contains(cells))result.add(cells);
        }
        return result;
    }
    static List<ChunkPos> template(int size,VeinDefinition.Shape shape,long seed,int attempt,int attempts) {
        List<Integer> widths=new ArrayList<>();
        for(int w=shape.minWidth();w<=Math.ceil(Math.sqrt(size));w++)
            if(size/w>=shape.minWidth()&&(size+w-1)/w<=shape.maxAspect()*w)widths.add(w);
        if(widths.isEmpty())return List.of();
            long salt=VeinGenerator.mix(seed+attempt);
            int width=widths.get(Math.floorMod((int)salt+attempt,widths.size()));
            int base=size/width,extra=size%width;
            int rotation=Math.floorMod((int)VeinGenerator.mix(seed^0x524F544154494F4EL)+attempt,4);
            boolean mirror=(salt&1)!=0;
            int[] lengths=new int[width],shifts=new int[width];
            for(int row=0;row<width;row++)lengths[row]=base+(Math.floorMod((long)row+(int)(salt>>>32),width)<extra?1:0);
            // Keep four regular fallbacks; earlier proposals vary the outline without changing area.
            if(attempt<Math.max(1,attempts-4)) {
                for(int i=0;i<size;i++){
                    long roll=VeinGenerator.mix(salt+i*0x9E3779B97F4A7C15L);
                    if((roll>>>11)*0x1.0p-53>=shape.edgeJitter())continue;
                    int from=Math.floorMod((int)roll,width),to=Math.floorMod((int)(roll>>>32),width);
                    if(from!=to&&lengths[from]>shape.minWidth()){lengths[from]--;lengths[to]++;}
                }
                for(int row=1;row<width;row++){
                    long roll=VeinGenerator.mix(salt^row);
                    shifts[row]=shifts[row-1]+((roll>>>11)*0x1.0p-53<shape.edgeJitter()?((roll&1)==0?-1:1):0);
                }
            }
            List<ChunkPos> cells=new ArrayList<>();
            for(int row=0;row<width;row++) {
                int length=lengths[row],shift=shifts[row];
                for(int column=0;column<length;column++) {
                    int x=column-length/2+shift,z=row-width/2;
                    if(mirror)x=-x;
                    cells.add(switch(rotation){case 1->new ChunkPos(-z,x);case 2->new ChunkPos(-x,-z);case 3->new ChunkPos(z,-x);default->new ChunkPos(x,z);});
                }
            }
            double mx=cells.stream().mapToInt(p->p.x).average().orElse(0),mz=cells.stream().mapToInt(p->p.z).average().orElse(0);
            var centre=cells.stream().min(Comparator.<ChunkPos>comparingDouble(p->(p.x-mx)*(p.x-mx)+(p.z-mz)*(p.z-mz)).thenComparingLong(ChunkPos::toLong)).orElseThrow();
            var shifted=cells.stream().map(p->new ChunkPos(p.x-centre.x,p.z-centre.z)).sorted(Comparator.comparingLong(ChunkPos::toLong)).toList();
            int spanX=shifted.stream().mapToInt(p->p.x).max().orElseThrow()-shifted.stream().mapToInt(p->p.x).min().orElseThrow()+1;
            int spanZ=shifted.stream().mapToInt(p->p.z).max().orElseThrow()-shifted.stream().mapToInt(p->p.z).min().orElseThrow()+1;
            if(Math.max(spanX,spanZ)>shape.maxAspect()*Math.min(spanX,spanZ))return List.of();
            if(shifted.stream().anyMatch(p->Math.abs(p.x)>shape.radius()||Math.abs(p.z)>shape.radius())||!broad(Set.copyOf(shifted),shape.minWidth()))return List.of();
            return shifted;
    }
    static boolean broad(Set<ChunkPos> cells,int minWidth) {
        if(minWidth<=1)return connected(cells);
        Set<ChunkPos> body=new HashSet<>();
        for(var p:cells) {
            var a=new ChunkPos(p.x+1,p.z);var b=new ChunkPos(p.x,p.z+1);var c=new ChunkPos(p.x+1,p.z+1);
            if(cells.contains(a)&&cells.contains(b)&&cells.contains(c)){body.add(p);body.add(a);body.add(b);body.add(c);}
        }
        if(body.isEmpty()||!connected(body))return false;
        // A one-cell step at a band end is fine; two connected cells outside the broad body form a thin tail.
        for(var p:cells)if(!body.contains(p)) {
            boolean touches=false;
            for(var q:neighbors(p)) {if(cells.contains(q)&&!body.contains(q))return false;if(body.contains(q))touches=true;}
            if(!touches)return false;
        }
        // Generated rows have a broad overlap; reject an accidental one-cell joint between body lobes.
        return noArticulation(body);
    }
    private static boolean noArticulation(Set<ChunkPos> cells) {
        Map<ChunkPos,Integer> index=new HashMap<>();int n=0;for(var p:cells)index.put(p,n++);
        int[][] edges=new int[n][];
        for(var e:index.entrySet())edges[e.getValue()]=neighbors(e.getKey()).stream().filter(index::containsKey).mapToInt(index::get).toArray();
        int[] discovered=new int[n],low=new int[n],clock={0};
        return dfs(0,-1,edges,discovered,low,clock);
    }
    private static boolean dfs(int at,int parent,int[][] edges,int[] discovered,int[] low,int[] clock) {
        discovered[at]=low[at]=++clock[0];int children=0;
        for(int next:edges[at]) {
            if(next==parent)continue;
            if(discovered[next]==0){
                children++;if(!dfs(next,at,edges,discovered,low,clock))return false;
                low[at]=Math.min(low[at],low[next]);if(parent!=-1&&low[next]>=discovered[at])return false;
            }else low[at]=Math.min(low[at],discovered[next]);
        }
        return parent!=-1||children<=1;
    }
    private static List<ChunkPos> neighbors(ChunkPos p){return List.of(new ChunkPos(p.x+1,p.z),new ChunkPos(p.x-1,p.z),new ChunkPos(p.x,p.z+1),new ChunkPos(p.x,p.z-1));}
    private static boolean connected(Set<ChunkPos> cells) {
        if(cells.isEmpty())return true;
        Set<ChunkPos> seen=new HashSet<>();var todo=new ArrayDeque<ChunkPos>();todo.add(cells.iterator().next());seen.add(todo.peek());
        while(!todo.isEmpty())for(var q:neighbors(todo.remove()))if(cells.contains(q)&&seen.add(q))todo.add(q);
        return seen.size()==cells.size();
    }
    private VeinShapes() {}
}
