// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.economy;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceClockMigrationTest {
    private static CompoundTag legacy(long elapsed) {
        var t=new CompoundTag(); t.putInt("schema",1); t.putString("owner","owner");
        t.putLong("elapsed",elapsed); return t;
    }
    private static CompoundTag saved(int interval,int work,int phase) {
        var t=new CompoundTag(); t.putInt("schema",2); t.putString("owner","owner");
        t.putInt("legacyInterval",interval); t.putInt("work",work); t.putInt("batchElapsed",phase); return t;
    }

    @Test void everyLegacyFractionSurvivesAChangedRateFailedCreditPauseAndMidWindowSave() {
        assertEquals(200,DreamPassiveClock.BATCH_TICKS);
        for(int old:new int[]{40,100}) {
            int threshold=2*old;
            for(int elapsed=0;elapsed<old;elapsed++) {
                var state=DreamPassiveEconomy.ClockState.read(legacy(elapsed)); var clock=state.clock(old);
                int work=elapsed*2, phase=0, paid=0;
                assertEquals(work,clock.work()); assertEquals(phase,clock.batchElapsed());
                clock.observeOwner("owner",0);
                for(int tick=0;tick<600;tick++) {
                    // Completing research during a paid-out window must not retroactively upgrade its first 41 ticks.
                    int step=tick<41?4:10;
                    int next=work+step;
                    int expected=(phase+1==200)?next/threshold:0;
                    assertEquals(expected,clock.advanceBatch(true,step));
                    if(expected>0) {
                        clock.commitBatch(0,false);
                        assertEquals(expected,clock.advanceBatch(true,step),"refused settlement retries this same batch");
                        clock.commitBatch(expected,false); paid+=expected;
                        phase=0;work=next%threshold;
                    } else {phase++;work=next;}
                    assertEquals(phase,clock.batchElapsed()); assertEquals(work,clock.work());
                    if(tick==79) {
                        var wire=state.write();
                        assertEquals(2,wire.getInt("schema")); assertEquals(old,wire.getInt("legacyInterval"));
                        assertEquals(work,wire.getInt("work")); assertEquals(phase,wire.getInt("batchElapsed"));
                        state=DreamPassiveEconomy.ClockState.read(wire); clock=state.clock(old);
                        clock.observeOwner("owner",0);
                        for(int paused=0;paused<250;paused++) assertEquals(0,clock.advanceBatch(false,step));
                        assertEquals(phase,clock.batchElapsed()); assertEquals(work,clock.work());
                    }
                }
                assertEquals((elapsed*2+41*4+559*10)/threshold,paid);
            }
        }
    }

    @Test void researchedBuildingsSettleExactly25Or10PerWindowNotPerItem() {
        for(int old:new int[]{40,100}) {
            var clock=DreamPassiveClock.restore(old,0,"owner"); clock.observeOwner("owner",0);
            int[] settles=old==40?new int[]{25,25}:new int[]{10,10};
            for(int amount:settles) {
                for(int i=0;i<199;i++) assertEquals(0,clock.advanceBatch(true,10));
                assertEquals(amount,clock.advanceBatch(true,10)); clock.commitBatch(amount,false);
            }
            assertEquals(0,clock.work()); assertEquals(0,clock.batchElapsed());
        }
    }

    @Test void invalidOrUnrelatedClockDataIsQuarantinedAndOwnerAbaDiscardsPartialOldWork() {
        var future=saved(40,5,0); future.putInt("schema",52);
        for(var wire:new CompoundTag[]{saved(100,11,0),saved(40,-1,0),saved(40,Integer.MAX_VALUE,199),
                saved(40,1,200),saved(91,0,0),future}) {
            wire.putString("extension","preserve");
            var state=DreamPassiveEconomy.ClockState.read(wire);
            assertFalse(state.supportsInterval(40)); assertNull(state.clock(40));
            assertEquals(wire,state.write(),"opaque payload is retained verbatim");
        }
        var clock=DreamPassiveClock.restore(40,Long.MAX_VALUE,"owner");
        assertEquals(78,clock.work(),"clamp before multiplying a legacy long");
        clock.observeOwner("owner",0); assertEquals(0,clock.advanceBatch(true,10));
        clock.observeOwner("owner",2);
        assertEquals(0,clock.work()); assertEquals(0,clock.batchElapsed(),"A→B→A revokes the partial batch");
    }
}
