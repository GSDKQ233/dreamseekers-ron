package com.solegendary.reignofnether.research.economy;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.shared.PassiveOreBuilding;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
@Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID)
public final class PiglinEconomyService {
    public static final int EXCHANGE_FOOD=100,EXCHANGE_ORE=40,EXCHANGE_THROTTLE=20;
    private record Key(String owner,UUID epoch){}
    private static final Map<Key,Long> exchanges=new HashMap<>();
    private PiglinEconomyService(){}
    public static boolean owns(ServerPlayer actor,BuildingPlacement p) {
        if(actor==null||p==null||actor.level()!=p.level||!actor.getName().getString().equals(p.ownerName)
           ||!BuildingServerEvents.getBuildings().contains(p)||p.getBuilding()!=Buildings.PIGLIN_ORE_MINE
           ||!p.isBuilt||p.isDestroyedServerside||p.getHealth()<=0||p.shouldBeDestroyed()||p.getCaptureRevision()<0)return false;
        var player=PlayerServerEvents.getRTSPlayer(p.ownerName);
        return player!=null&&EconomyFactionAccess.permits(player.faction,Factions.PIGLINS);
    }
    public static int exchangeRemaining(Resources a,long now) {
        // This is a short throttle, not a payment/replay ledger; expired entries carry no authority.
        exchanges.entrySet().removeIf(e->now-e.getValue()>=EXCHANGE_THROTTLE
                ||PlayerServerEvents.getRTSPlayer(e.getKey().owner)==null);
        Long last=exchanges.get(new Key(a.ownerName,a.accountEpoch));
        return last==null?0:(int)Math.max(0,Math.min(EXCHANGE_THROTTLE,EXCHANGE_THROTTLE-(now-last)));
    }
    public static String act(ServerPlayer actor,BuildingPlacement p,Resources a,PiglinEconomyServerboundPacket.Action action,UUID instance,long capture) {
        if(!owns(actor,p)||a==null||a!=ExtendedResourcesSync.account(p.ownerName)
                ||!ResearchPlacementIdentity.serverIdentity(p).equals(instance)||p.getCaptureRevision()!=capture)return "forbidden";
        var server=actor.getServer(); long now=server.overworld().getGameTime();
        if(action==PiglinEconomyServerboundPacket.Action.EXCHANGE) {
            if(!ResearchServerEvents.playerHasResearch(p.ownerName,CoreEconomyResearch.id("research_instant_smelting")))return "research_required";
            if(exchangeRemaining(a,now)>0)return "action_throttled";
            var paid=ResourceTransaction.exchange(server,a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(EXCHANGE_FOOD,0,0),new ResourceTransaction.Cost(0,0,EXCHANGE_ORE));
            if(!paid.applied())return paid.status().name().toLowerCase(Locale.ROOT);
            exchanges.entrySet().removeIf(e->e.getKey().owner.equals(a.ownerName)&&!e.getKey().epoch.equals(a.accountEpoch));
            exchanges.put(new Key(a.ownerName,a.accountEpoch),now);return "exchange_complete";
        }
        if(action!=PiglinEconomyServerboundPacket.Action.OVERLOAD)return "forbidden";
        var state=PassiveOreBuilding.traitState(p);
        if(!PassiveOreBuilding.isProductionActive(p))return "production_paused";
        if(!state.canActivateOverload())return "on_cooldown";
        var paid=ResourceTransaction.exchange(server,a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(20,0,0),ResourceTransaction.Cost.ZERO);
        if(!paid.applied())return paid.status().name().toLowerCase(Locale.ROOT);
        // No callback/yield between validated state, ledger commit and state commit.
        if(!state.activateOverload())throw new IllegalStateException("Validated overload state changed on one server task");
        return "overload_started";
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e){exchanges.clear();}
}
