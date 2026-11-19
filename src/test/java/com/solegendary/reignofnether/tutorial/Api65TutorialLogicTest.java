package com.solegendary.reignofnether.tutorial;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** One executable pure enum/state test plus two explicit source-wiring guards.
 * Does not instantiate Minecraft, load a language manager, or claim GUI acceptance. */
class Api65TutorialLogicTest {
    private String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/tutorial/TutorialClientEvents.java"));
    }
    @Test void completeIsAbsorbingAndOrdinaryStagesAdvanceExactlyOnce() {
        assertSame(TutorialStage.COMPLETED,TutorialStage.COMPLETED.next(),"completed tutorial never indexes beyond enum");
        assertSame(TutorialStage.INTRO,TutorialStage.INTRO.prev());
        TutorialStage[] stages=TutorialStage.values();
        for(int i=0;i<stages.length-1;i++)assertSame(stages[i+1],stages[i].next());
        for(int i=1;i<stages.length;i++)assertSame(stages[i-1],stages[i].prev());
    }
    @Test void workerSelectionIsTypedNotTranslatedOrUserNamed() throws Exception {
        String s=source();int begin=s.indexOf("private static boolean hasVillagerSelected()");
        assertTrue(begin>=0,"single typed VillagerUnit predicate exists");
        String predicate=s.substring(begin,s.indexOf("private static boolean hasBuildingSelected",begin));
        assertTrue(predicate.contains("get(0) instanceof VillagerUnit"));
        assertTrue(predicate.contains("getSelectedUnits().isEmpty()"));
        assertFalse(predicate.contains("getName()"));assertFalse(predicate.contains("getString()"));
        assertFalse(s.contains("hasUnitSelected(\"villager\")"),"no stage uses English display-name membership");
        String stages=s.substring(s.indexOf("case SELECT_UNIT ->"),s.indexOf("case BOX_SELECT_UNITS ->"));
        assertEquals(3,stages.split("hasVillagerSelected\\(\\)",-1).length-1,
                "SELECT_UNIT and both MOVE_UNIT gates use the typed predicate");
    }
    @Test void disabledHotkeyAndTerminalTransitionAreGuarded() throws Exception {
        String s=source();int begin=s.indexOf("public static void onKeyPress(");
        String handler=s.substring(begin,s.indexOf("public static void onClientTick(",begin));
        assertTrue(handler.contains("if (!isEnabled() || !OrthoviewClientEvents.isEnabled())"));
        begin=s.indexOf("private static void nextStage()");
        String next=s.substring(begin,s.indexOf("private static void prevStage()",begin));
        int guard=next.indexOf("if (tutorialStage == COMPLETED) return;");
        assertTrue(guard>=0&&guard<next.indexOf("tutorialStage = tutorialStage.next()"));
    }
}
