// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.production.pagination;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** Client presentation policy only. No recipe authority, resource state, world or persistence. */
public final class ProductionPageState {
    public static final int PAGE_SIZE=6;
    private final int count;
    private int page;
    private long generation;
    private final Set<Integer> staleReleases=new HashSet<>();
    public ProductionPageState(int count,Collection<Integer> heldKeys){
        if(count<0)throw new IllegalArgumentException("negative recipe count");
        this.count=count;quarantine(heldKeys);
    }
    public int page(){return page;}
    public int pages(){return Math.max(1,(count+PAGE_SIZE-1)/PAGE_SIZE);}
    public int start(){return page*PAGE_SIZE;}
    public int end(){return Math.min(count,start()+PAGE_SIZE);}
    public long generation(){return generation;}
    public boolean canTurn(int delta){return (delta==-1||delta==1)&&page+delta>=0&&page+delta<pages();}
    public boolean turn(int delta,Collection<Integer> heldKeys){
        if(!canTurn(delta))return false;
        quarantine(heldKeys);page+=delta;generation++;return true;
    }
    private void quarantine(Collection<Integer> keys){if(keys!=null)for(Integer k:keys)if(k!=null&&k>=0)staleReleases.add(k);}
    /** Consume exactly the pre-page-change release; normal fresh press/release thereafter works. */
    public boolean consumeStaleRelease(int key){return staleReleases.remove(key);}
    public boolean allows(long snapshotGeneration,boolean exactCurrentList,boolean exactCurrentSelection,
                          boolean liveAuthorizedContext,boolean originalVisible,boolean originalEnabled){
        return generation==snapshotGeneration&&exactCurrentList&&exactCurrentSelection&&liveAuthorizedContext&&originalVisible&&originalEnabled;
    }
    /** Stable first tie wins, matching existing selected-building order; never mutates HUD selection. */
    public static <T> T idlest(List<T> selected,Predicate<T> eligible,ToDoubleFunction<T> queuedTicks){
        T best=null;double remaining=Double.POSITIVE_INFINITY;
        for(T candidate:selected)if(eligible.test(candidate)){
            double ticks=queuedTicks.applyAsDouble(candidate);
            if(Double.isFinite(ticks)&&ticks>=0&&ticks<remaining){remaining=ticks;best=candidate;}
        }
        return best;
    }
}
