package com.solegendary.reignofnether.research.economy;

import java.util.LinkedHashMap;
/** Pure bounded replay window shared by all new economy endpoints. Server thread owns each instance. */
public final class EconomyRequestWindow<P> {
    public static final int CAPACITY=64;
    public record Result(boolean execute,String status) {}
    private record Cached<P>(P payload,String status) {}
    private final LinkedHashMap<Long,Cached<P>> cache=new LinkedHashMap<>();
    private long highWater;
    public Result admit(long sequence,P payload) {
        if(sequence<=0)return new Result(false,"invalid_sequence");
        if(sequence==Long.MAX_VALUE)return new Result(false,"reconnect_required");
        if(sequence<=highWater) {
            var cached=cache.get(sequence);
            return new Result(false,cached==null?"stale_request":cached.payload().equals(payload)?cached.status():"replay_mismatch");
        }
        highWater=sequence;cache.put(sequence,new Cached<>(payload,"failed_closed"));
        while(cache.size()>CAPACITY)cache.remove(cache.keySet().iterator().next());
        return new Result(true,"admitted");
    }
    public void finish(long sequence,String result) {
        var old=cache.get(sequence);
        if(old==null)throw new IllegalStateException("Unreserved request");
        cache.put(sequence,new Cached<>(old.payload(),result));
    }
    public long highWater(){return highWater;}
    public int cachedResults(){return cache.size();}
}
