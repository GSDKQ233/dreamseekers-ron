package com.solegendary.reignofnether.research.economy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Pure client-state tests: explicit senders never initialize Forge networking. */
final class EconomyActionClientJUnitTest {
    private final UUID stream=UUID.randomUUID(), session=UUID.randomUUID(), epoch=UUID.randomUUID();
    private final List<Object> sent=new ArrayList<>();
    private record Packet(UUID session,long sequence,String payload) {}
    @BeforeEach void start(){EconomyActionClient.clear();assertTrue(accept(10,0));}
    @AfterEach void cleanup(){EconomyActionClient.clear();}
    private boolean accept(long order,long completed){return EconomyActionClient.accept(stream,order,session,epoch,completed);}
    private void submit(String key){EconomyActionClient.submit(key,(s,n)->new Packet(s,n,key),sent::add);}
    private void fill(){for(int i=1;i<=64;i++)submit("action-"+i);assertEquals(64,sent.size());}
    private void stillFull(){int before=sent.size();submit("overflow");assertEquals(before,sent.size());}
    private Packet last(){return (Packet)sent.get(sent.size()-1);}

    @Test void olderCompletionFreesSaturatedGlobalQueueWithoutRollingBackSnapshot(){
        fill();stillFull();assertTrue(accept(100,0));
        assertFalse(accept(99,1));submit("released");assertEquals(65,sent.size());assertEquals(65,last().sequence());
        assertFalse(accept(99,0));assertFalse(accept(100,0));assertTrue(accept(101,0));stillFull();
    }
    @Test void equalOrderCompletionAlsoRetiresWithoutRefreshingSnapshot(){
        fill();assertFalse(accept(10,1));submit("released");assertEquals(65,sent.size());assertEquals(65,last().sequence());
        assertFalse(accept(10,0));
    }
    @Test void foreignStreamNeverDrainsEvenWithNewerOrder(){
        fill();assertFalse(EconomyActionClient.accept(UUID.randomUUID(),100,session,epoch,1));stillFull();
        assertTrue(accept(11,0)); // foreign stream did not advance freshness
    }
    @Test void staleForeignSessionCannotRetireCurrentSequence(){
        fill();assertFalse(EconomyActionClient.accept(stream,9,UUID.randomUUID(),epoch,1));stillFull();
        assertTrue(EconomyActionClient.ready(epoch));
    }
    @Test void staleForeignEpochCannotRetireCurrentSequence(){
        fill();assertFalse(EconomyActionClient.accept(stream,9,session,UUID.randomUUID(),1));stillFull();
        assertTrue(EconomyActionClient.ready(epoch));
    }
    @Test void negativeOrderAndMissingOrUnknownCompletionNeverDrain(){
        fill();assertFalse(accept(-1,1));stillFull();assertFalse(accept(9,0));stillFull();
        assertFalse(accept(9,-1));stillFull();assertFalse(accept(9,999));stillFull();
        assertTrue(accept(11,0));stillFull();
    }
    @Test void duplicateCompletionIsHarmlessAndCannotRemoveDifferentPendingAction(){
        fill();assertFalse(accept(9,1));submit("replacement");assertEquals(65,last().sequence());
        assertFalse(accept(9,1));stillFull();
        sent.clear();EconomyActionClient.retryPending();assertEquals(64,sent.size());
        assertFalse(sent.stream().map(Packet.class::cast).anyMatch(p->p.sequence()==1));
        assertTrue(sent.stream().map(Packet.class::cast).anyMatch(p->p.sequence()==65));
    }
    @Test void freshEpochResetsSequenceAndLateOldEpochCannotDrainReusedSequence(){
        fill();UUID nextEpoch=UUID.randomUUID();assertTrue(EconomyActionClient.accept(stream,11,session,nextEpoch,0));
        sent.clear();submit("new-epoch");assertEquals(1,last().sequence());assertTrue(EconomyActionClient.ready(nextEpoch));assertFalse(EconomyActionClient.ready(epoch));
        assertFalse(accept(10,1));sent.clear();EconomyActionClient.retryPending();assertEquals(1,sent.size());assertEquals("new-epoch",last().payload());
    }
    @Test void freshSessionResetsSequenceAndLateOldSessionCannotDrainReusedSequence(){
        fill();UUID nextSession=UUID.randomUUID();assertTrue(EconomyActionClient.accept(stream,11,nextSession,epoch,0));
        sent.clear();submit("new-session");assertEquals(nextSession,last().session());assertEquals(1,last().sequence());
        assertFalse(accept(10,1));sent.clear();EconomyActionClient.retryPending();assertEquals(1,sent.size());assertEquals("new-session",last().payload());
    }
    @Test void sharedSendersRetryOriginalPacketWithoutRecreatingPayloadOrChangingChannel(){
        List<Object> a=new ArrayList<>(),b=new ArrayList<>();AtomicInteger factories=new AtomicInteger();
        EconomyActionClient.submit("a",(s,n)->{factories.incrementAndGet();return new Packet(s,n,"immutable-a");},a::add);
        EconomyActionClient.submit("b",(s,n)->{factories.incrementAndGet();return new Packet(s,n,"immutable-b");},b::add);
        Object first=a.get(0),second=b.get(0);assertEquals(1,((Packet)first).sequence());assertEquals(2,((Packet)second).sequence());
        EconomyActionClient.submit("a",(s,n)->{fail("retry must not rebuild packet");return null;},b::add);
        assertEquals(2,a.size());assertEquals(1,b.size());assertSame(first,a.get(1));
        EconomyActionClient.retryPending();assertSame(first,a.get(2));assertSame(second,b.get(1));assertEquals(2,factories.get());
        assertFalse(accept(9,1));EconomyActionClient.retryPending();assertEquals(3,a.size());assertSame(second,b.get(2));
    }
    @Test void clearRequiresNewContextAndDropsOldRetries(){
        submit("old");EconomyActionClient.clear();sent.clear();EconomyActionClient.retryPending();submit("not-ready");assertTrue(sent.isEmpty());assertFalse(EconomyActionClient.ready(epoch));
        UUID newStream=UUID.randomUUID();assertTrue(EconomyActionClient.accept(newStream,0,session,epoch,0));submit("reconnected");assertEquals(1,last().sequence());
        assertFalse(EconomyActionClient.accept(stream,100,session,epoch,1));sent.clear();EconomyActionClient.retryPending();assertEquals(1,sent.size());
    }
}
