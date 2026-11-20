package com.dreamseekers.skill;

import com.solegendary.reignofnether.client.ActiveClientTickClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillReadinessTest {
    @Test void waitingIsNotACooldownOrPermissionToConsumeATargetClick() {
        assertTrue(SkillReadiness.WAITING.waiting());assertFalse(SkillReadiness.WAITING.ready());
        assertEquals("?",SkillReadiness.WAITING.badge());
        assertEquals(240,SkillReadiness.WAITING.nativeCooldown(240));
    }
    @Test void actualExpiryStaleAndOwnerResyncAreThreeDistinctStates() {
        var clock=new ActiveClientTickClock();clock.context(new Object(),new Object());long t=clock.now();
        assertEquals("2s",SkillReadiness.at(clock,t,21).badge());
        clock.endTick(false);assertEquals("1s",SkillReadiness.at(clock,t,21).badge());
        for(int i=0;i<20;i++)clock.endTick(false);
        assertTrue(SkillReadiness.at(clock,t,21).ready());assertEquals("",SkillReadiness.at(clock,t,21).badge());
        for(int i=0;i<20;i++)clock.endTick(false);
        assertEquals(SkillReadiness.WAITING,SkillReadiness.at(clock,t,21));
        assertEquals(SkillReadiness.READY,SkillReadiness.at(clock,clock.now(),0));
    }
}
