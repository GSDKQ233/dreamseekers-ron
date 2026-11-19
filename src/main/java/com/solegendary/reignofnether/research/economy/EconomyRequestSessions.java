package com.solegendary.reignofnether.research.economy;

import java.util.*;
import java.util.function.Predicate;
/** Pure connection-identity/owner/epoch session store. Only the authenticated adapter may call current(). */
public final class EconomyRequestSessions<C,P> {
    public static final class Entry<P> {
        public final String owner;public final UUID epoch,session=UUID.randomUUID();
        public final EconomyRequestWindow<P> window=new EconomyRequestWindow<>();
        Entry(String owner,UUID epoch){this.owner=owner;this.epoch=epoch;}
        public EconomyRequestWindow.Result admit(UUID expectedSession,UUID expectedEpoch,long sequence,P payload) {
            if(!session.equals(expectedSession)||!epoch.equals(expectedEpoch))return new EconomyRequestWindow.Result(false,"stale_session");
            return window.admit(sequence,payload);
        }
    }
    private final IdentityHashMap<C,Entry<P>> states=new IdentityHashMap<>();
    public Entry<P> current(C connection,String owner,UUID epoch) {
        Objects.requireNonNull(connection);Objects.requireNonNull(owner);Objects.requireNonNull(epoch);
        var state=states.get(connection);
        if(state==null||!state.owner.equals(owner)||!state.epoch.equals(epoch)){
            state=new Entry<P>(owner,epoch);states.put(connection,state);
        }
        return state;
    }
    public void remove(C connection){states.remove(connection);}
    public void removeIf(Predicate<C> predicate){states.keySet().removeIf(predicate);}
    public List<C> connections(){return List.copyOf(states.keySet());}
    public int size(){return states.size();}
    public void clear(){states.clear();}
}
