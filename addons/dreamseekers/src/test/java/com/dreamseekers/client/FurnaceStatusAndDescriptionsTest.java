package com.dreamseekers.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Resource/source contracts only; actual scaled GUI rendering still needs a client fixture. */
class FurnaceStatusAndDescriptionsTest {
    private static final String ROOT="addons/dreamseekers/";
    private String read(String file)throws Exception{return Files.readString(Path.of(ROOT+file));}
    private JsonObject json(String file)throws Exception{return JsonParser.parseString(read(file)).getAsJsonObject();}
    @Test void bothLanguagesDescribeActualRosterAndResearchScope()throws Exception {
        var zh=json("src/main/resources/assets/dreamseekers/lang/zh_cn.json");
        var en=json("src/main/resources/assets/dreamseekers/lang/en_us.json");
        assertEquals(zh.keySet(),en.keySet());
        for(var lang:new JsonObject[]{zh,en}) {
            assertTrue(lang.get("buildings.dreamseekers.dream_hall.tooltip1").getAsString().contains("14"));
            assertFalse(lang.get("hud.dreamseekers.furnace_paused").getAsString().isBlank());
            assertFalse(lang.get("dreamseekers.error.shadow_furnace_faction").getAsString().isBlank());
        }
        assertTrue(zh.get("buildings.dreamseekers.dream_hall.tooltip1").getAsString().contains("三页"));
        assertTrue(en.get("buildings.dreamseekers.dream_hall.tooltip1").getAsString().contains("three pages"));
        assertTrue(zh.get("buildings.dreamseekers.resonance_laboratory.tooltip2").getAsString().contains("因科技而异"));
        assertTrue(en.get("buildings.dreamseekers.resonance_laboratory.tooltip2").getAsString().contains("vary by research"));
    }
    @Test void manifestReflectsCurrentMineCostAndSeparatesRefundPaths()throws Exception {
        var manifest=json("economy_manifest.json");
        var cost=manifest.getAsJsonObject("mine").getAsJsonObject("cost");
        assertEquals(300,cost.get("wood").getAsInt());assertEquals(150,cost.get("ore").getAsInt());
        String refund=manifest.getAsJsonObject("ownerPolicy").get("refunds").getAsString();
        assertTrue(refund.contains("original payer"));assertTrue(refund.contains("same account epoch"));
        assertTrue(refund.contains("construction cancellation"));assertTrue(refund.contains("current-owner"));
    }
    @Test void statusUsesLiveLocalOwnershipAndClientFactionEvenWithZeroShadow()throws Exception {
        String hud=read("src/main/java/com/dreamseekers/client/DreamFurnaceStatusHud.java");
        for(String required:new String[]{"getGameProfile().getName().equals(placement.ownerName)",
                "placement.getLevel()!=mc.level","placement.isDestroyedServerside","placement.shouldBeDestroyed()",
                "getSelectedBuildings().contains(placement)","getBuildings().contains(placement)",
                "PlayerClientEvents.getRTSPlayer(placement.ownerName)","owner==null||owner.faction==null",
                "DreamPassiveEconomy.factionPauseReason(placement.getBuilding(),owner)",
                "event.reserve(width,height)","mc.font.split(Component.translatable(reason),wrapWidth)"})
            assertTrue(hud.contains(required),required);
        assertFalse(hud.contains("PlayerServerEvents"));assertFalse(hud.contains("ExtendedResourcesClient"));
        assertFalse(hud.contains("sendToServer"));assertFalse(hud.contains("getVisualOrderText"));
        String policy=read("src/main/java/com/dreamseekers/economy/DreamPassiveEconomy.java");
        assertTrue(policy.contains("factionPauseReason(placement.getBuilding(),PlayerServerEvents.getRTSPlayer(placement.ownerName))"));
        assertTrue(policy.contains("public static String factionPauseReason(Building building, RTSPlayer owner)"));
    }
}
