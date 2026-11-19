package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.registrars.PacketHandler;
import java.util.*;
import java.util.function.BiFunction;
/** One sequence space for every new-economy action; retransmissions retain exact payload and sequence. */
public final class EconomyActionClient {
    private static UUID session,epoch,snapshotSession;private static long next=1,serverSequence=-1;
    private record Pending(Object fingerprint,Object packet,java.util.function.Consumer<Object> sender) {}
    private static final LinkedHashMap<Long,Pending> pending=new LinkedHashMap<>();
    private EconomyActionClient() {}
    public static boolean accept(UUID stream,long order,UUID actionSession,UUID accountEpoch,long completedSequence) {
        if(order<0)return false;
        // A newer snapshot may precede this completion. Retire only our established
        // action context; stale snapshots still cannot refresh UI or reset sequence state.
        if(snapshotSession!=null&&session!=null&&epoch!=null
            &&snapshotSession.equals(stream)&&session.equals(actionSession)&&epoch.equals(accountEpoch)
            &&completedSequence>0)pending.remove(completedSequence);
        if(order<=serverSequence||(snapshotSession!=null&&!snapshotSession.equals(stream)))return false;
        snapshotSession=stream;serverSequence=order;
        if(!Objects.equals(session,actionSession)||!Objects.equals(epoch,accountEpoch)) {
            session=actionSession;epoch=accountEpoch;next=1;pending.clear();
        }
        if(completedSequence>0)pending.remove(completedSequence);
        return true;
    }
    public static boolean ready(UUID accountEpoch){return session!=null&&Objects.equals(epoch,accountEpoch);}
    /** Read-only presentation guard; does not establish a session, submit an action or grant authority. */
    public static boolean matchesContext(UUID expectedSession,UUID expectedEpoch) {
        return session!=null&&epoch!=null&&Objects.equals(session,expectedSession)&&Objects.equals(epoch,expectedEpoch);
    }

    public static void submit(Object fingerprint,BiFunction<UUID,Long,Object> factory) {
        submit(fingerprint,factory,PacketHandler.INSTANCE::sendToServer);
    }
    /** Addon channels share this exact sequence space; retain their sender for identical retries. */
    public static void submit(Object fingerprint,BiFunction<UUID,Long,Object> factory,java.util.function.Consumer<Object> sender) {
        Objects.requireNonNull(sender);
        if(session==null)return;
        for(var p:pending.values())if(p.fingerprint().equals(fingerprint)){p.sender().accept(p.packet());return;}
        if(pending.size()>=64||next==Long.MAX_VALUE)return; // astronomic exhaustion requires reconnect; never wrap
        long n=next++;Object packet=factory.apply(session,n);pending.put(n,new Pending(fingerprint,packet,sender));
        sender.accept(packet);
    }
    public static void retryPending(){for(var p:List.copyOf(pending.values()))p.sender().accept(p.packet());}
    public static void clear(){session=null;epoch=null;snapshotSession=null;next=1;serverSequence=-1;pending.clear();}
}
