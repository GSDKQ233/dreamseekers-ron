package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure server-policy matrix; no packets, network, or simulated authenticated peers. */
class BuildingPlacementWorkerAccessTest {
    @Test void ownWorkerAndExplicitSharedControlRemainAllowed() {
        for (boolean strict : new boolean[]{false, true}) {
            assertTrue(BuildingPlacementWorkerAccess.canControl("owner", "owner", strict, false, false, false));
            assertTrue(BuildingPlacementWorkerAccess.canControl("owner", "ally", strict, false, false, true));
            assertFalse(BuildingPlacementWorkerAccess.canControl("owner", "other", strict, false, false, false));
        }
    }

    @Test void ordinaryAndCustomPlacementKeepGlobalSandboxSemantics() {
        assertTrue(BuildingPlacementWorkerAccess.canControl("owner", "other", false, false, true, false));
        assertTrue(BuildingPlacementWorkerAccess.canControl("owner", "", false, false, true, false));
        assertFalse(BuildingPlacementWorkerAccess.canControl("owner", "", false, false, false, false));
    }

    @Test void strictPlacementOnlyUsesSenderSandboxStatus() {
        assertFalse(BuildingPlacementWorkerAccess.canControl("owner", "other", true, false, true, false));
        assertTrue(BuildingPlacementWorkerAccess.canControl("owner", "other", true, true, true, false));
        assertFalse(BuildingPlacementWorkerAccess.canControl("owner", "", true, true, true, false));
    }

    @Test void comparisonDoesNotNormalizeResourceAccountNames() {
        assertFalse(BuildingPlacementWorkerAccess.canControl("owner", "Owner", false, false, false, false));
        assertFalse(BuildingPlacementWorkerAccess.canControl("owner", " owner", true, false, false, false));
    }

    @Test void missingAuthenticatedSenderIsAlwaysRejected() {
        for (String sender : new String[]{null, "", " \t"})
            for (boolean strict : new boolean[]{false, true})
                assertFalse(BuildingPlacementWorkerAccess.canControl(sender, "owner", strict, true, true, true));
    }
}
