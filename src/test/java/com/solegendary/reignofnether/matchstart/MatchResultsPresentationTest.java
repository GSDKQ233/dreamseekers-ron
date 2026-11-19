package com.solegendary.reignofnether.matchstart;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class MatchResultsPresentationTest {
    private MatchResultsModel.Row row(String name, int team, boolean win, long... values) {
        return new MatchResultsModel.Row(name, "dreamseekers:dreamseekers", win, team, Arrays.stream(values).boxed().toList());
    }
    @Test void resultsWaitForWorldAndFreeScreenWithoutStealingExistingUi() {
        var state = new MatchResultsState();
        assertTrue(state.receive("match1"));
        assertFalse(state.consumeOpen(false, true));
        assertFalse(state.consumeOpen(true, false));
        assertTrue(state.consumeOpen(true, true));
        assertFalse(state.consumeOpen(true, true));
    }
    @Test void duplicateResultCannotReopenDismissedPopupButExplicitRequestCan() {
        var state = new MatchResultsState();
        var result = new MatchResultsModel.Snapshot(200, List.of(row("Alice", 1, true, 1, 2, 3)));
        assertTrue(state.receive(result));
        assertTrue(state.consumeOpen(true, true));
        state.dismiss();
        assertFalse(state.receive(new MatchResultsModel.Snapshot(200, List.of(row("Alice", 1, true, 1, 2, 3)))));
        assertFalse(state.consumeOpen(true, true));
        assertTrue(state.requestOpen());
        assertTrue(state.consumeOpen(true, true));
        assertFalse(state.consumeOpen(true, true));
    }
    @Test void resetOrLogoutClearsCacheAndPendingOpenIncludingQueuedCommand() {
        var state = new MatchResultsState();
        state.receive("old"); state.requestOpen(); state.clear();
        assertFalse(state.hasResults());
        assertFalse(state.requestOpen());
        assertFalse(state.consumeOpen(true, true));
        assertTrue(state.receive("new"));
        assertTrue(state.consumeOpen(true, true));
    }
    @Test void dismissCancelsPendingAutomaticOrExplicitOpeningButKeepsResult() {
        var state = new MatchResultsState();
        state.receive("a"); state.dismiss();
        assertTrue(state.hasResults()); assertFalse(state.consumeOpen(true, true));
        state.requestOpen(); state.dismiss(); assertFalse(state.consumeOpen(true, true));
        assertTrue(state.receive("b")); assertTrue(state.consumeOpen(true, true));
    }
    @Test void independentUnassignedPlayersNeverShareAnImplicitZeroTeam() {
        var teams = MatchResultsModel.teams(List.of(row("A",0,true,2),row("B",0,false,3),row("C",0,false,4)));
        assertEquals(3,teams.size()); assertTrue(teams.get(0).winner());
        assertEquals(List.of(2L,3L,4L),teams.stream().map(t->t.total(0)).toList());
    }
    @Test void teamResultCannotBeOverwrittenByLastMemberAndTotalsAreIndependent() {
        var teams = MatchResultsModel.teams(List.of(row("loser",4,false,5),row("win1",4,true,7),row("win2",4,true,11)));
        assertEquals(2,teams.size()); assertTrue(teams.get(0).winner());
        assertEquals(18,teams.get(0).total(0)); assertEquals(5,teams.get(1).total(0));
        assertEquals(2,teams.get(0).members().size()); assertEquals("loser",teams.get(1).members().get(0).name());
    }
    @Test void resultSnapshotAndScoresAreImmutableAndMissingScoresAreSafe() {
        var values = new ArrayList<>(List.of(1L,2L));
        var row = new MatchResultsModel.Row("a",null,true,1,values);
        values.set(0, 99L); assertEquals(1,row.score(0)); assertEquals(0,row.score(9)); assertEquals(0,row.score(-1));
        assertThrows(UnsupportedOperationException.class,()->row.scores().set(0,99L));
        var rows = new ArrayList<>(List.of(row)); var snapshot = new MatchResultsModel.Snapshot(-1,rows); rows.clear();
        assertEquals(0,snapshot.ticks()); assertEquals(1,snapshot.rows().size());
        assertThrows(UnsupportedOperationException.class,()->snapshot.rows().clear());
        assertEquals(0,new MatchResultsModel.Row("a",null,true,1,List.of(-5L)).score(0));
    }
    @Test void largeTotalsDoNotWrapToNegativeValues() {
        var teams = MatchResultsModel.teams(List.of(row("a",1,true,Integer.MAX_VALUE),row("b",1,true,Integer.MAX_VALUE)));
        assertEquals(4294967294L,teams.get(0).total(0));
        assertEquals(Long.MAX_VALUE,MatchResultsModel.teams(List.of(row("a",1,true,Long.MAX_VALUE),row("b",1,true,1))).get(0).total(0));
    }
    @Test void smallAndLargeWindowsKeepBodyInBoundsAndPermitLastRowScrolling() {
        for (int[] size : List.of(new int[]{320,240},new int[]{427,240},new int[]{854,480},new int[]{1920,1080},new int[]{240,120})) {
            var l=MatchResultsLayout.create(size[0],size[1],9000);
            assertTrue(l.left()>=0&&l.top()>=0&&l.left()+l.width()<=size[0]&&l.top()+l.height()<=size[1]);
            assertTrue(l.bodyTop()>=l.top()&&l.bodyBottom()<=l.top()+l.height());
            assertTrue(l.viewportHeight()>0); assertTrue(l.maxScroll()>0);
            assertEquals(9000,l.viewportHeight()+l.maxScroll());
            assertEquals(0,l.clampScroll(-500)); assertEquals(l.maxScroll(),l.clampScroll(1e6));
            assertEquals(0,l.clampScroll(Double.NaN));
        }
        assertTrue(MatchResultsLayout.create(320,240,300).compact());
        assertFalse(MatchResultsLayout.create(854,480,300).compact());
    }
    @Test void resizeAndShortResultsClampScrollWithoutEmptyOverflow() {
        var tall = MatchResultsLayout.create(320,240,1000);
        double previous = tall.clampScroll(9999);
        var shortList = MatchResultsLayout.create(854,480,40);
        assertEquals(0,shortList.maxScroll()); assertEquals(0,shortList.clampScroll(previous));
        assertEquals(0,MatchResultsLayout.create(320,240,0).maxScroll());
    }
    @Test void productionWiringUsesStateGatesLifecycleClearAndVisibleReopenEntries() throws Exception {
        String events=Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/matchstart/MatchEndClientEvents.java"));
        String player=Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/player/PlayerClientEvents.java"));
        String screen=Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/matchstart/MatchEndScreen.java"));
        assertTrue(events.contains("STATE.consumeOpen(mc.player != null && mc.level != null, mc.screen == null)"));
        assertTrue(events.contains("Commands.literal(\"rts-results\")")); assertTrue(events.contains("instanceof PauseScreen"));
        assertTrue(events.contains("onClientLogout(ClientPlayerNetworkEvent.LoggingOut evt) { clear(); }"));
        assertTrue(player.contains("if (!isSyncing) MatchEndClientEvents.clear();"));
        assertTrue(player.contains("public static void resetRTS(boolean hardReset) {\n        MatchEndClientEvents.clear();"));
        assertTrue(player.contains("public static void publishScenarioMap() {\n        MatchEndClientEvents.clear();"));
        assertTrue(screen.contains("g.enableScissor(")); assertTrue(screen.contains("finally { g.disableScissor(); }"));
        assertTrue(screen.contains("GLFW.GLFW_KEY_PAGE_DOWN")); assertTrue(screen.contains("mouseScrolled("));
        assertTrue(screen.contains("mouseDragged(")); assertTrue(screen.contains("font.plainSubstrByWidth"));
        assertTrue(screen.contains("g.pose().scale(scale, scale, 1)"));
    }
}
