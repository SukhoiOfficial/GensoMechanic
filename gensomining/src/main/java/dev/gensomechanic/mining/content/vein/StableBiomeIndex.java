package dev.gensomechanic.mining.content.vein;

import com.mojang.datafixers.util.Pair;
import java.util.*;
import net.minecraft.world.level.biome.Climate;

/** Local index, with the native squared-range fitness formula and input ordinal tie breaking.
 * Matches native brute force without modifying any native/shared index or state. */
final class StableBiomeIndex<T> {
    private record Entry<T>(int ordinal,long[] low,long[] high,T value) {}
    private static final class Node<T> {
        final long[] low=new long[7],high=new long[7];
        int minimumOrdinal=Integer.MAX_VALUE;Node<T> left,right;List<Entry<T>> entries;
    }
    private final Node<T> root;
    private Entry<T> previous;
    StableBiomeIndex(List<Pair<Climate.ParameterPoint,T>> values) {
        if(values.isEmpty())throw new IllegalArgumentException("Biome index requires entries");
        List<Entry<T>> entries=new ArrayList<>();
        for(int i=0;i<values.size();i++) {
            var p=values.get(i).getFirst();var ranges=new Climate.Parameter[]{p.temperature(),p.humidity(),p.continentalness(),p.erosion(),p.depth(),p.weirdness(),new Climate.Parameter(p.offset(),p.offset())};
            long[] low=new long[7],high=new long[7];for(int axis=0;axis<7;axis++){low[axis]=ranges[axis].min();high[axis]=ranges[axis].max();}
            entries.add(new Entry<>(i,low,high,values.get(i).getSecond()));
        }
        root=build(entries);
    }
    private Node<T> build(List<Entry<T>> entries) {
        var node=new Node<T>();Arrays.fill(node.low,Long.MAX_VALUE);Arrays.fill(node.high,Long.MIN_VALUE);
        long[] smallest=new long[7],largest=new long[7];Arrays.fill(smallest,Long.MAX_VALUE);Arrays.fill(largest,Long.MIN_VALUE);
        for(var entry:entries) {
            node.minimumOrdinal=Math.min(node.minimumOrdinal,entry.ordinal());
            for(int axis=0;axis<7;axis++) {long lo=entry.low()[axis],hi=entry.high()[axis];node.low[axis]=Math.min(node.low[axis],lo);node.high[axis]=Math.max(node.high[axis],hi);
                long center=lo+(hi-lo)/2;smallest[axis]=Math.min(smallest[axis],center);largest[axis]=Math.max(largest[axis],center);}
        }
        if(entries.size()<=12){node.entries=List.copyOf(entries);return node;}
        int split=0;for(int i=1;i<7;i++)if(largest[i]-smallest[i]>largest[split]-smallest[split])split=i;
        final int axis=split;
        entries.sort(Comparator.<Entry<T>>comparingLong(e->{return e.low()[axis]+(e.high()[axis]-e.low()[axis])/2;}).thenComparingInt(Entry::ordinal));
        int middle=entries.size()/2;node.left=build(new ArrayList<>(entries.subList(0,middle)));node.right=build(new ArrayList<>(entries.subList(middle,entries.size())));return node;
    }
    private static long bound(Node<?> node,long[] target) {return distance(node.low,node.high,target);}
    private static long distance(long[] low,long[] high,long[] target) {
        long sum=0;for(int i=0;i<7;i++){long delta=target[i]<low[i]?low[i]-target[i]:target[i]>high[i]?target[i]-high[i]:0;sum+=delta*delta;}return sum;
    }
    private final class Search {
        final Climate.TargetPoint point;final long[] target;Entry<T> best;long distance=Long.MAX_VALUE;
        Search(Climate.TargetPoint point){this.point=point;target=new long[]{point.temperature(),point.humidity(),point.continentalness(),point.erosion(),point.depth(),point.weirdness(),0};if(previous!=null){best=previous;distance=distance(best.low(),best.high(),target);}}
        void visit(Node<T> node,long lower) {
            if(lower>distance||(lower==distance&&best!=null&&node.minimumOrdinal>=best.ordinal()))return;
            if(node.entries!=null) {
                for(var entry:node.entries){long d=distance(entry.low(),entry.high(),target);if(d<distance||(d==distance&&(best==null||entry.ordinal()<best.ordinal()))){best=entry;distance=d;}}
                return;
            }
            long left=bound(node.left,target),right=bound(node.right,target);
            if(left<=right){visit(node.left,left);visit(node.right,right);}else{visit(node.right,right);visit(node.left,left);}
        }
    }
    T findValue(Climate.TargetPoint point) {var search=new Search(point);search.visit(root,bound(root,search.target));previous=search.best;return previous.value();}
}
