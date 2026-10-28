package dev.gensomechanic.mining.content.vein;

import java.util.*;
import java.util.function.*;

/** Weighted access-order cache. Eviction never converts an unfinished query into an empty result. */
final class VeinCache<K,V> extends LinkedHashMap<K,V> {
    private final int limit;
    private final ToIntFunction<V> weight;
    private int units,peak;
    VeinCache(int limit,ToIntFunction<V> weight){super(256,.75f,true);this.limit=Math.max(1,limit);this.weight=weight;}
    @Override public V put(K key,V value) {
        V old=super.put(key,value);units+=weight.applyAsInt(value)-(old==null?0:weight.applyAsInt(old));peak=Math.max(peak,units);
        while(units>limit&&size()>1){var it=entrySet().iterator();units-=weight.applyAsInt(it.next().getValue());it.remove();}
        return old;
    }
    @Override public V remove(Object key){V old=super.remove(key);if(old!=null)units-=weight.applyAsInt(old);return old;}
    @Override public V computeIfAbsent(K key,Function<? super K,? extends V> factory){V value=get(key);if(value==null){value=factory.apply(key);if(value!=null)put(key,value);}return value;}
    @Override public void clear(){super.clear();units=0;}
    int units(){return units;}
    int peak(){return peak;}
}
