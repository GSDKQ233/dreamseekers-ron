// SPDX-License-Identifier: GPL-3.0-only
package com.solegendary.reignofnether.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Source-wiring guards complement the pure clock tests; these are not GUI/network tests. */
class ActiveClientClockWiringTest {
    private static final List<String> CLIENTS = List.of(
            "building/garrison/TowerGarrisonClient.java", "building/workposts/MineWorkPostClient.java",
            "research/economy/PiglinEconomyClient.java", "research/economy/ResearchQueueClient.java");
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/" + relative));
    }
    private static String compact(String text) { return text.replaceAll("\\s+", ""); }

    @Test void allFourClientsOwnOnePureClockAndAdvanceOnlyAtEnd() throws Exception {
        for (String file : CLIENTS) {
            String text = source(file), code = compact(text);
            assertFalse(text.contains("getGameTime("), file);
            assertFalse(text.contains("getDayTime("), file);
            assertEquals(1, text.split("new ActiveClientTickClock\\(", -1).length - 1, file);
            assertEquals(1, text.split("CLOCK\\.endTick\\(", -1).length - 1, file);
            int tick = text.indexOf("public static void tick(");
            int end = text.indexOf("Phase.END", tick), advance = text.indexOf("CLOCK.endTick(", tick);
            assertTrue(tick > 0 && end > tick && advance > end, file);
            assertTrue(code.contains("CLOCK.context("), file);
            assertTrue(code.contains("CLOCK.endTick(Minecraft.getInstance().isPaused())")
                    || code.contains("CLOCK.endTick(mc.isPaused())"), file);
            assertTrue(code.contains("CLOCK.clear()"), file);
            assertTrue(code.contains("if(CLOCK.endTick(") && code.contains("))clearView();"), file);
        }
        String helper = source("client/ActiveClientTickClock.java");
        assertFalse(helper.contains("import net.minecraft"));
        assertFalse(helper.contains("import net.minecraftforge"));
        assertFalse(helper.contains("com.dreamseekers"));
    }

    @Test void allAuthenticatedAcksPrecedeDimensionTargetAndViewFiltering() throws Exception {
        for (var spec : List.of(
                List.of(CLIENTS.get(0), "packet.viewer()", "!packet.validOccupancy()", "packet.dimension()", "views.keySet()"),
                List.of(CLIENTS.get(1), "packet.owner()", "!packet.validOccupancy()", "packet.dimension()", "views.keySet()"),
                List.of(CLIENTS.get(2), "s.owner()", "s.boost()<0", "s.dimension()", "BuildingPlacement p="),
                List.of(CLIENTS.get(3), "packet.owner", "!Float.isFinite(entry.ticks())", "packet.dimension", "ProductionPlacement p="))) {
            String text = source(spec.get(0)); int start = text.indexOf("public static void accept(");
            int ack = text.indexOf("EconomyActionClient.accept(", start);
            String envelope = text.substring(start, ack);
            assertTrue(ack > start && envelope.contains(spec.get(1)) && envelope.contains(spec.get(2)), spec.get(0));
            assertTrue(envelope.contains("Connection"), spec.get(0));
            assertFalse(envelope.contains("isPaused()"), spec.get(0));
            assertTrue(text.indexOf(spec.get(3), start) > ack, spec.get(0));
            assertTrue(text.indexOf(spec.get(4), start) > ack, spec.get(0));
        }
    }

    @Test void alliedTowerRecipientAndHostAuthorityRemainDistinct() throws Exception {
        String text = source(CLIENTS.get(0));
        assertTrue(text.contains("!packet.viewer().equals(mc.player.getName().getString())"));
        assertTrue(text.contains("p.ownerName.equals(packet.owner())"));
        assertTrue(text.contains("AlliancesClient.isAlliedOrOwned(mc.player.getName().getString(), mine.ownerName)"));
        assertFalse(text.contains("!packet.owner().equals(mc.player.getName().getString())"));
    }

    @Test void retryRequiresConnectedUnpausedEndTickAndSkipsRolloverZero() throws Exception {
        String text = compact(source(CLIENTS.get(3)));
        int tick = text.indexOf("publicstaticvoidtick(");
        int end = text.indexOf("Phase.END)return;", tick);
        int guard = text.indexOf("if(mc.isPaused()||mc.level==null||mc.getConnection()==null)return;", tick);
        int boundary = text.indexOf("if(CLOCK.now()>0&&CLOCK.now()%40==0)", tick);
        int retry = text.indexOf("EconomyActionClient.retryPending()", tick);
        assertTrue(end > tick && guard > end && boundary > guard && retry > boundary);
    }

    @Test void viewResetsDoNotDiscardSharedPendingActions() throws Exception {
        String text = compact(source(CLIENTS.get(3)));
        assertTrue(text.contains("if(CLOCK.context(mc.level,connection))clearView();"));
        assertTrue(text.contains("privatestaticvoidclearView(){session=null;sequence=-1;queues.clear();queried.clear();}"));
        assertTrue(text.contains("publicstaticvoidclear(){EconomyActionClient.clear();clearView();CLOCK.clear();}"));
        int ack = text.indexOf("EconomyActionClient.accept(packet.session,");
        assertTrue(ack < text.indexOf("packet.sequence<=sequence"));
        assertTrue(ack < text.indexOf("packet.revision<previous.revision"));
    }
    @Test void negativeAckNumbersCannotAdvanceSharedWatermarkAtAnyEntryPoint() throws Exception {
        for (var spec : List.of(
                List.of(CLIENTS.get(0), "public static void accept(", "packet.order()<0", "packet.request()<0"),
                List.of(CLIENTS.get(1), "public static void accept(", "packet.order()<0", "packet.request()<0"),
                List.of(CLIENTS.get(2), "public static void accept(", "s.order()<0", "s.request()<0"),
                List.of(CLIENTS.get(3), "public static void accept(", "packet.sequence<0", "packet.requestSequence<0"),
                List.of(CLIENTS.get(3), "public static void acceptResult(", "packet.order()<0", "packet.request()<0"))) {
            String text = source(spec.get(0)); int start = text.indexOf(spec.get(1));
            int ack = text.indexOf("EconomyActionClient.accept(", start);
            String envelope = compact(text.substring(start, ack));
            assertTrue(start >= 0 && ack > start, spec.get(0));
            assertTrue(envelope.contains(spec.get(2)) && envelope.contains(spec.get(3)), spec.get(0));
        }
    }
}
