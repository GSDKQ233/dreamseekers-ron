package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.hud.ProductionProgressLabel;
import com.solegendary.reignofnether.resources.ResourceCost;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProductionPauseTest {
    @Test void onlyTransitionsPublishAndResumeCannotRewindAJob() {
        var item = new ProductionItem(ResourceCost.Unit(1,0,0,5,1)) {
            @Override public String getItemName() { return "test"; }
        };
        var id = UUID.randomUUID();
        var server = new ActiveProduction(item, false, "owner");
        server.beginPauseObservation();
        server.pauseBecause("test.exit");
        assertTrue(server.finishPauseObservation());
        var held = server.pause();
        for (int i=0; i<80; i++) {
            server.beginPauseObservation();
            server.pauseBecause("test.exit");
            assertFalse(server.finishPauseObservation());
            assertSame(held,server.pause());
        }
        server.beginPauseObservation();
        assertTrue(server.finishPauseObservation());
        var resumed = server.pause();
        var view = new ActiveProduction(item,"owner",id);
        assertFalse(view.acceptPause(UUID.randomUUID(),held,1)); // same recipe, different entry
        assertTrue(view.acceptPause(id,held,2));
        assertTrue(view.pause().paused());
        assertFalse(view.acceptPause(id,held,20));
        assertEquals(2,view.ticksLeft);
        assertTrue(view.acceptPause(id,resumed,1));
        assertFalse(view.acceptPause(id,held,90));
        assertFalse(view.pause().paused());
        assertEquals(1,view.ticksLeft);
        assertFalse(view.completed);
    }

    @Test void progressDisplayDoesNotClaimAnUnacknowledgedFinish() {
        assertEquals(99,ProductionProgressLabel.percent(0,true));
        assertEquals(99,ProductionProgressLabel.percent(.001f,true));
        assertEquals(100,ProductionProgressLabel.percent(0,false));
        assertEquals("暂停：出…",ProductionProgressLabel.fitStatus("暂停：出口受阻",5,String::length));
        assertEquals("",ProductionProgressLabel.fitStatus("Paused",0,String::length));
    }
}
