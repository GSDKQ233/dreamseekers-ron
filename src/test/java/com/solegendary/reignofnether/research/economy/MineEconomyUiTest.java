package com.solegendary.reignofnether.research.economy;

import com.google.gson.JsonParser;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.resources.ResourceExtensionRegistry;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MineEconomyUiTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/"+relative));
    }
    @Test void actualHudComposesBothMineExtensionsBeforeFilteringAndReservingTheRow() throws Exception {
        String hud=source("hud/HudClientEvents.java");
        int base=hud.indexOf("PiglinEconomyClient.append(");
        int posts=hud.indexOf("MineWorkPostClient.append(");
        int filter=hud.indexOf("buildingAbilities = buildingAbilities.stream()");
        int reserve=hud.indexOf("if (buildingAbilities.size() > 0 || !hudSelectedPlacement.isBuilt)");
        assertTrue(base>0&&posts>base&&filter>posts&&reserve>filter);
        assertEquals(1,hud.split("PiglinEconomyClient\\.append\\(",-1).length-1);
        assertEquals(1,hud.split("MineWorkPostClient\\.append\\(",-1).length-1);
        assertTrue(hud.substring(base,posts).contains("hudSelectedPlacement.getAbilityButtons()"));
        assertTrue(hud.substring(posts,filter).contains("hudSelectedPlacement, buildingAbilities"));
    }
    @Test void clientEligibilityRequiresRealRosterAndRetiresStaleWorldState() throws Exception {
        String client=source("research/economy/PiglinEconomyClient.java");
        assertTrue(client.contains("PlayerClientEvents.getRTSPlayer(p.ownerName)"));
        assertFalse(client.contains("getPlayerFaction("),"unregistered spectator fallback NONE must not grant authority");
        assertTrue(client.contains("query(p);List<AbilityButton>"),"dynamic append refreshes server state");
        assertTrue(client.contains("CLOCK.queryDue(last,20)"));
        assertTrue(client.contains("CLOCK.fresh(received,60)"));
        assertTrue(client.contains("s.epoch().equals(ExtendedResourcesClient.epoch())"));
        assertTrue(client.contains("states.keySet().removeIf(p->!own(p))"));
        assertTrue(client.contains("receivedTicks.clear()"));
    }
    @Test void differentNormalFactionAndAbsentFactionFailClosed() {
        var piglin=new Faction();var villagers=new Faction();
        assertTrue(EconomyFactionAccess.permits(piglin,piglin));
        assertFalse(EconomyFactionAccess.permits(villagers,piglin));
        assertFalse(EconomyFactionAccess.permits(null,piglin));
        assertFalse(EconomyFactionAccess.permits(piglin,null));
    }
    @Test void rebuiltPlacementCannotConsumeOldQueryOrActionReply() throws Exception {
        var views=new EconomyViewIdentity<Object>();
        Object oldPlacement=new Object(),replacementAtSameOrigin=new Object();
        var oldNonce=views.token(oldPlacement);
        assertTrue(views.matches(oldPlacement,oldNonce));
        assertFalse(views.matches(replacementAtSameOrigin,oldNonce),"unsolicited reply cannot create a view");
        var newNonce=views.token(replacementAtSameOrigin);
        assertNotEquals(oldNonce,newNonce);
        assertFalse(views.matches(replacementAtSameOrigin,oldNonce),"same origin/owner is not placement identity");
        views.retain(p->p==replacementAtSameOrigin);
        assertFalse(views.matches(oldPlacement,oldNonce),"removed or unauthorized placement retires its view");
        assertNotEquals(oldNonce,views.token(oldPlacement),"reacquiring control requires a new view");
        views.clear();
        assertFalse(views.matches(replacementAtSameOrigin,newNonce),"world/reconnect resets correlation");
        String client=source("research/economy/PiglinEconomyClient.java");
        assertTrue(client.indexOf("!EconomyActionClient.accept(")<client.indexOf("!views.matches(p,s.view())"),
                "authenticated ACK retires pending work before stale view filtering; stale view still cannot repaint");
        assertTrue(client.contains("0,views.token(p)"));
        assertTrue(client.contains("s.epoch(),session,n,s.view()"));
        String request=source("research/economy/PiglinEconomyServerboundPacket.java");
        assertTrue(request.contains("p,\"ready\",0,view)"));
        assertTrue(request.contains("p,status,sequence,view)"));
    }
    @Test void tooltipUsesRegisteredTranslationKeyAndIdFallbackWithoutMutatingTheQuote() {
        var id=ResourceLocation.parse("mine_ui_test:energy");
        ResourceExtensionRegistry.register(id,1000,"resource.mine_ui_test.localized_energy",ResourceLocation.parse("minecraft:textures/item/amethyst_shard.png"));
        var content=(TranslatableContents)ReceiptBackedResearchItem.resourceLabel(id).getContents();
        assertEquals("resource.mine_ui_test.localized_energy",content.getKey());
        assertEquals(id.toString(),content.getFallback());
        var unknown=ResourceLocation.parse("unknown_addon:unregistered_resource");
        var fallback=(TranslatableContents)ReceiptBackedResearchItem.resourceLabel(unknown).getContents();
        assertEquals("resource.unknown_addon.unregistered_resource",fallback.getKey());
        assertEquals(unknown.toString(),fallback.getFallback());
    }
    @Test void shadowTooltipAndAllMineMessagesHaveBothLanguageEntries() throws Exception {
        var shadow=ResourceLocation.parse("dreamseekers:shadow_energy");
        var content=(TranslatableContents)ReceiptBackedResearchItem.resourceLabel(shadow).getContents();
        assertEquals("resource.dreamseekers.shadow_energy",content.getKey());
        for(String locale:new String[]{"en_us","zh_cn"}) {
            var addon=JsonParser.parseString(Files.readString(Path.of("addons/dreamseekers/src/main/resources/assets/dreamseekers/lang/"+locale+".json"))).getAsJsonObject();
            assertEquals(locale.equals("zh_cn")?"影能":"Shadow energy",addon.get(content.getKey()).getAsString());
            var core=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/reignofnether/lang/"+locale+".json"))).getAsJsonObject();
            for(String key:new String[]{"mine_workposts.status","mine_workposts.summary","mine_workposts.multiplier","mine_workposts.rules","mine_workposts.release_all","mine_workposts.release_all.description","mine_workposts.waiting","mine_workposts.paused","mine_workposts.released","hud.reignofnether.mine_workpost_unavailable","research.economy.exchange_state","research.economy.cannot_pay_action"})
                assertTrue(core.has(key)&&!core.get(key).getAsString().isBlank(),locale+": "+key);
        }
        assertTrue(source("research/economy/ReceiptBackedResearchItem.java").contains("resourceLabel(e.getKey()),e.getValue()"));
    }
}
