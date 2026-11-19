package com.solegendary.reignofnether.unit;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Source integration checks complement the real-server own-unit gameplay regressions. */
class FormationCommandWiringTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/" + path));
    }

    @Test void newerExecutedMovementOrderInvalidatesBeforeRedundantMoveReturn() throws Exception {
        String code = source("unit/UnitActionItem.java");
        int loop = code.indexOf("for (Unit unit : actionableUnits)");
        int control = code.indexOf("unit.ignoreNonStopCommands()", loop);
        int discard = code.indexOf("UnitServerEvents.discardPendingFormationMove((Entity) unit)", control);
        int redundant = code.indexOf("if (action == UnitAction.MOVE && isRedundantMove", control);
        assertTrue(loop >= 0 && control > loop && discard > control && redundant > discard);
        assertTrue(code.substring(control, discard).contains("nonAbilityActions.contains(action)"));
    }

    @Test void directAndFullResetBothInvalidatePendingFormation() throws Exception {
        String code = source("unit/interfaces/Unit.java");
        int full = code.indexOf("static void fullResetBehaviours(Unit unit)");
        int reset = code.indexOf("static void resetBehaviours(Unit unit)");
        assertTrue(code.substring(full, reset).contains("Unit.resetBehaviours(unit)"));
        String body = code.substring(reset, code.indexOf("default void resetBehaviours()", reset));
        assertTrue(body.contains("UnitServerEvents.discardPendingFormationMove((Entity) unit)"));
        assertTrue(body.indexOf("discardPendingFormationMove") < body.indexOf("beforeReset"));
    }

    @Test void ownerMutationObserverSharesTheSyncedDataReturnHook() throws Exception {
        String code = source("mixin/EconomicOwnershipEpochMixin.java");
        assertTrue(code.contains("@Mixin(SynchedEntityData.class)"));
        assertTrue(code.contains("@At(\"RETURN\")"));
        assertTrue(code.contains("EconomicUnitIdentity.observeOwnerMutation(entity)"));
        assertTrue(code.contains("UnitServerEvents.observeFormationOwnerMutation(entity)"));
    }

    @Test void shiftQueueReceptionDoesNotInvalidateAnExecutingFormation() throws Exception {
        String code = source("unit/packets/UnitActionServerboundPacket.java");
        assertFalse(code.contains("discardPendingFormationMove"));
        assertFalse(code.contains("observeFormationOwnerMutation"));
    }

    @Test void directBuildingAssignmentPartialResetAlsoInvalidatesOlderMovement() throws Exception {
        String worker = source("unit/interfaces/WorkerUnit.java");
        int reset = worker.indexOf("static void resetBehavioursExceptExploreBuild");
        assertTrue(worker.indexOf("UnitServerEvents.discardPendingFormationMove", reset) > reset);
        // The assignment/append/reset ordering itself is covered by the worker command
        // behavior regression, not by requiring a particular empty-list branch here.
    }

    @Test void unitTickUsesLifecycleOwnedTicketsInsteadOfAnonymousForcedTickets() throws Exception {
        String code = source("unit/interfaces/Unit.java");
        assertTrue(code.contains("UnitServerEvents.updateUnitChunkTickets(unitMob)"));
        assertFalse(code.contains("addRegionTicket(TicketType.FORCED"));
    }
}
