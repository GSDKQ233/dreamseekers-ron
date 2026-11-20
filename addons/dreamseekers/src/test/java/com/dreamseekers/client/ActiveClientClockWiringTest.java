// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Source-wiring regression checks; real GUI and packet flow require client verification. */
class ActiveClientClockWiringTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/" + relative));
    }
    @Test void actualClientWiringUsesOneEndTickAndNeverVisualWorldTime() throws Exception {
        for (String file : List.of("encounter/EncounterClient.java", "neutralcontract/ContractClient.java",
                "resonance/ResonanceClient.java", "ecologyworkposts/EcologyClient.java",
                "shadow/TerrorbeakUpgradeClient.java", "worldtree/WorldTreeClient.java",
                "progression/DreamWindClient.java", "scout/ScoutMarkClient.java", "skill/SkillClient.java",
                "client/DreamEconomyClientHud.java")) {
            String text = source(file);
            assertFalse(text.contains("getGameTime("), file);
            assertEquals(1, text.split("CLOCK\\.endTick\\(", -1).length - 1, file);
            assertTrue(text.contains("Phase.END"), file);
            assertTrue(text.contains("isPaused()"), file);
            assertTrue(text.contains("CLOCK.context("), file);
            assertTrue(text.contains("clearView()"), file);
            assertTrue(text.indexOf("Phase.END") < text.indexOf("CLOCK.endTick("), file);
            assertTrue(text.contains("import com.solegendary.reignofnether.client.ActiveClientTickClock;"), file);
        }
    }

    @Test void authenticatedSharedAckPrecedesTransientViewFiltering() throws Exception {
        for (var pair : List.of(
                List.of("encounter/EncounterClient.java", "if(!editor()||level==null"),
                List.of("neutralcontract/ContractClient.java", "if(level==null||!packet.dimension()"),
                List.of("resonance/ResonanceClient.java", "if(level==null||!level.dimension()"),
                List.of("ecologyworkposts/EcologyClient.java", "if(level==null||!level.dimension()"),
                List.of("shadow/TerrorbeakUpgradeClient.java", "if(level==null||!packet.dimension()"),
                List.of("worldtree/WorldTreeClient.java", "if(mc.level==null||!mc.level.dimension()"))) {
            String text = source(pair.get(0)); int accept = text.indexOf("public static void accept(");
            int ack = text.indexOf("EconomyActionClient.accept(", accept);
            int view = text.indexOf(pair.get(1), accept);
            assertTrue(ack > accept && view > ack, pair.get(0));
            String envelope = text.substring(accept, ack);
            assertTrue(envelope.contains("owner()"), pair.get(0));
            assertTrue(envelope.contains("connection") || envelope.contains("Connection"), pair.get(0));
            assertFalse(envelope.contains("isPaused()"), pair.get(0));
        }
    }

    @Test void relativeSkillDurationsAndHudFreshnessDoNotUseAbsoluteDeadlines() throws Exception {
        String scout = source("scout/ScoutMarkClient.java"), skill = source("skill/SkillClient.java");
        for (String text : List.of(scout, skill)) {
            assertFalse(text.contains("now + Math.max("));
            assertFalse(text.contains("cooldownUntil"));
            assertTrue(text.contains("SkillReadiness.at(CLOCK,"));
            assertTrue(text.contains("CLOCK.fresh("));
        }
        String readiness = source("skill/SkillReadiness.java");
        assertTrue(readiness.contains("clock.remaining(received, duration)"));
        assertTrue(readiness.contains("clock.fresh(received, 40)"));
        assertTrue(source("client/DreamEconomyClientHud.java").contains("!CLOCK.fresh(refillTick,1)"));
        assertTrue(source("resonance/ResonanceClient.java").contains("CLOCK.remaining(VIEWS.get(p).received,s.cooldown())"));
    }

    @Test void levelResetDoesNotEraseWindConnectionReplayWatermark() throws Exception {
        String text = source("progression/DreamWindClient.java");
        String clearView = text.substring(text.indexOf("private static void clearView()"), text.indexOf("public static void clear()"));
        assertFalse(clearView.contains("stream=null"));
        assertFalse(clearView.contains("order=-1"));
        assertTrue(text.contains("if(live!=connection){stream=null;order=-1;}"));
        assertTrue(text.contains("p.order()<=order"));
        assertTrue(text.contains("!stream.equals(p.stream())"));
    }

    @Test void staleCompletedUpgradeAckReleasesItsBodyBeforeRejectingUiSnapshot() throws Exception {
        String text = source("shadow/TerrorbeakUpgradeClient.java");
        int accept = text.indexOf("public static void accept(");
        int sharedAck = text.indexOf("boolean accepted=EconomyActionClient.accept(", accept);
        int release = text.indexOf("PENDING_VIEWS.remove(packet.view(),pending);", sharedAck);
        int rejectStaleUi = text.indexOf("if(!accepted)return;", sharedAck);
        int dimension = text.indexOf("if(level==null||!packet.dimension()", rejectStaleUi);
        assertTrue(accept < sharedAck && sharedAck < release && release < rejectStaleUi
                && rejectStaleUi < dimension);
        assertTrue(text.substring(accept, sharedAck).contains("packet.request()<0"));
        assertFalse(text.substring(sharedAck, rejectStaleUi).contains("PENDING_VIEWS.clear()"));
        assertTrue(text.substring(sharedAck, release).contains("pending.completedBy(packet.stream(),packet.session(),packet.epoch(),packet.request(),packet.id(),packet.uuid())"));
        assertTrue(text.contains("new PendingUpgradeView<>(source,s.stream(),session,s.epoch(),sequence,payload.id(),payload.uuid())"));
        assertTrue(text.contains("private static void clearView(){PENDING_VIEWS.clear();VIEWS.clear();}"));
    }

    @Test void worldTreeViewsCannotRebindResidualPlacementsFromAnotherLevel() throws Exception {
        String text = source("worldtree/WorldTreeClient.java");
        int query = text.indexOf("public static void query(");
        int ready = text.indexOf("private static WorldTreePackets.Snapshot ready(");
        String queryBody = text.substring(query, ready);
        String readyBody = text.substring(ready, text.indexOf("private static boolean dream()", ready));
        for (String body : List.of(queryBody, readyBody)) {
            assertTrue(body.contains("p.getLevel()!=mc.level"));
            assertTrue(body.contains("!BuildingClientEvents.getBuildings().contains(p)"));
        }
        int accept = text.indexOf("public static void accept(");
        int ack = text.indexOf("EconomyActionClient.accept(", accept);
        int placement = text.indexOf("candidate.getLevel()==mc.level", accept);
        assertTrue(accept < ack && ack < placement && placement < query);
    }

    @Test void residualUnitAndBuildingCollectionsCannotReuseAnOldLevel() throws Exception {
        assertTrue(source("scout/ScoutMarkClient.java").contains("scout.level() == MC.level"));
        assertTrue(source("skill/SkillClient.java").contains("source.mob().level() == MC.level"));
        assertTrue(source("progression/DreamWindClient.java").contains("b.getLevel()==Minecraft.getInstance().level"));
        assertTrue(source("neutralcontract/ContractClient.java").contains("w.level()==mc.level"));
        assertTrue(source("shadow/TerrorbeakUpgradeClient.java").contains("source.level()==mc.level"));
        assertTrue(source("resonance/ResonanceClient.java").contains("p.getLevel()==mc.level"));
        assertTrue(source("ecologyworkposts/EcologyClient.java").contains("p.level==mc.level"));
    }
}
