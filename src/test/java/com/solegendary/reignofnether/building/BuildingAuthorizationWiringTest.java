package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Local source-policy regressions only; no packet construction, network or game runtime. */
class BuildingAuthorizationWiringTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/building/BuildingServerboundPacket.java"));
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static Set<String> actions(String text, String listName) {
        int start = text.indexOf(listName + " = List.of(");
        assertTrue(start >= 0);
        String list = text.substring(start, text.indexOf(");", start));
        return Pattern.compile("BuildingAction\\.([A-Z_]+)").matcher(list).results()
                .map(result -> result.group(1)).collect(Collectors.toSet());
    }

    @Test void allRallyMutationsRequireExistingTargetAuthorization() throws Exception {
        assertEquals(Set.of("DESTROY", "SET_RALLY_POINT", "SET_ATTACK_RALLY_POINT",
                "ADD_RALLY_POINT", "ADD_ATTACK_RALLY_POINT", "SET_RALLY_POINT_ENTITY", "CHANGE_PORTAL"),
                actions(source(), "existingBuildingAuthActions"));
    }

    @Test void sharedControlUsesActualExistingOwnerAndPlacementUsesRequestedOwner() throws Exception {
        String code = compact(source());
        assertTrue(code.contains("StringpermissionOwner=existingBuildingAuthActions.contains(this.action)&&building!=null"
                + "?building.ownerName:this.ownerName;"));
        int guard = code.indexOf("if(!strictExisting&&");
        int dispatch = code.indexOf("switch(this.action)", guard);
        assertTrue(guard >= 0 && dispatch > guard);
        String permissionCheck = code.substring(guard, dispatch);
        assertTrue(permissionCheck.contains("!AlliancesServerEvents.canControlAlly(player.getName().getString(),permissionOwner)"));
        assertFalse(permissionCheck.contains("canControlAlly(player.getName().getString(),ownerName)"));
        assertTrue(permissionCheck.contains("return;"));
    }

    @Test void selfSharedAllyAndExistingSandboxExceptionsRemainUnchanged() throws Exception {
        String code = compact(source());
        // This exact rejection predicate preserves owner and ally grants and the existing
        // global sandbox exception. Changing sandbox semantics is outside this fix.
        assertTrue(code.contains("if(!strictExisting&&((newBuildingAuthActions.contains(this.action)&&"
                + "!player.getName().getString().equals(ownerName))||"
                + "(existingBuildingAuthActions.contains(this.action)&&building!=null&&"
                + "!player.getName().getString().equals(building.ownerName)))&&"
                + "!SandboxServer.isAnyoneASandboxPlayer()&&"
                + "!AlliancesServerEvents.canControlAlly(player.getName().getString(),permissionOwner)){"));
    }

    @Test void readOnlyReplacementRemainsOutsideOwnershipMutationChecks() throws Exception {
        String text = source();
        assertFalse(actions(text, "existingBuildingAuthActions").contains("REQUEST_REPLACEMENT"));
        assertFalse(actions(text, "newBuildingAuthActions").contains("REQUEST_REPLACEMENT"));
        String code = compact(text);
        assertTrue(code.contains("&&this.action!=BuildingAction.REQUEST_REPLACEMENT;"));
        assertTrue(code.contains("caseREQUEST_REPLACEMENT->{BuildingServerEvents.replaceClientBuilding(player,buildingPos);}"));
        // Read-only does not mean broadcast: only this authenticated requester receives the replacement.
        assertFalse(Pattern.compile("BuildingServerEvents\\.replaceClientBuilding\\([^,;]*\\);")
                .matcher(code).find(), "packet route must not call the legacy one-argument broadcast overload");
    }

    @Test void strictAddonChecksRetainSenderSpecificSandboxAndTargetOwner() throws Exception {
        String code = compact(source());
        int start = code.indexOf("if(strictExisting){");
        int end = code.indexOf("if(!strictExisting&&", start);
        assertTrue(start >= 0 && end > start);
        String strict = code.substring(start, end);
        assertTrue(strict.contains("OreMinePlacementAccess.canPlaceFor(senderName,building.ownerName,"
                + "SandboxServer.isSandboxPlayer(senderName),"));
        assertTrue(strict.contains("AlliancesServerEvents.canControlAlly(senderName,building.ownerName)"));
        assertFalse(strict.contains("isAnyoneASandboxPlayer"));
    }

    @Test void allFourPlacementVariantsShareWorkerValidationBeforeDispatch() throws Exception {
        String text = source();
        assertEquals(Set.of("PLACE", "PLACE_AND_QUEUE", "PLACE_CUSTOM", "PLACE_AND_QUEUE_CUSTOM"),
                actions(text, "newBuildingAuthActions"));
        String code = compact(text);
        int placement = code.indexOf("if(newBuildingAuthActions.contains(this.action)){");
        int validation = code.indexOf("for(intbuilderId:this.builderUnitIds)", placement);
        int nextGuard = code.indexOf("booleanstrictExisting=", validation);
        int dispatch = code.indexOf("switch(this.action)", nextGuard);
        assertTrue(placement >= 0 && validation > placement && nextGuard > validation && dispatch > nextGuard);
        String check = code.substring(placement, nextGuard);
        assertTrue(check.contains("CustomBuildingServerEvents.getCustomBuilding(this.itemName)"));
        assertTrue(check.contains("BuildingPlacementWorkerAccess.canControl("));
        assertTrue(check.contains("senderName,unit.getOwnerName(),strictPlacement,senderIsSandbox,anyoneIsSandbox,"));
        assertTrue(check.contains("AlliancesServerEvents.canControlAlly(senderName,unit.getOwnerName())"));
        assertFalse(check.contains("BuildingServerEvents.placeBuilding("));
        assertFalse(check.contains("Unit.fullResetBehaviours("));
        assertTrue(code.substring(validation, nextGuard).contains("success.set(false);return;"));
    }

    @Test void placementValidationUsesAuthenticatedSenderAndServerResolvedWorker() throws Exception {
        String code = compact(source());
        int start = code.indexOf("if(newBuildingAuthActions.contains(this.action)){");
        int end = code.indexOf("booleanstrictExisting=", start);
        String check = code.substring(start, end);
        assertTrue(check.contains("StringsenderName=player.getName().getString();"));
        assertTrue(check.contains("BuildingServerEvents.getServerLevel().getEntity(builderId)"));
        assertTrue(check.contains("booleansenderIsSandbox=SandboxServer.isSandboxPlayer(senderName);"));
        assertTrue(check.contains("booleananyoneIsSandbox=SandboxServer.isAnyoneASandboxPlayer();"));
        assertTrue(check.contains("booleanstrictPlacement=requestedinstanceofPassiveOreBuilding"
                + "||requested.requiresStrictPlacementOwnerCheck();"));
    }
}
