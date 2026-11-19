package com.dreamseekers.worldtree;
import com.dreamseekers.building.DreamAnchor;
import com.dreamseekers.economy.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.data.DataType;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.economy.EconomyBalanceSync;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
@Mod.EventBusSubscriber(modid="dreamseekers")
public final class DreamWorldTreeService {
    public static final String STRUCTURE="dream_anchor_ascended",PURPOSE="dreamseekers:world_tree_upgrade";
    public static final DataType<WorldTreeState> STATE=DataType.createRegistered(ResourceLocation.fromNamespaceAndPath("dreamseekers","world_tree"),(tag,server)->WorldTreeState.read(tag),WorldTreeState::write,WorldTreeState::new);
    public static final ResourceTransaction.Cost COST=new ResourceTransaction.Cost(600,800,600,Map.of(DreamPassiveEconomy.SHADOW,150));
    public static final ResourceTransaction.Cost WIND_COST=new ResourceTransaction.Cost(450,650,450,Map.of(DreamPassiveEconomy.SHADOW,100));
    public static String purpose(WorldTreeModel target){return target==WorldTreeModel.LEGACY_SHADOW?PURPOSE:PURPOSE+":"+target.branch.name().toLowerCase(Locale.ROOT)+":v"+target.revision;}
    public static ResourceTransaction.Cost cost(WorldTreeModel target){return target==WorldTreeModel.WIND_V4?WIND_COST:COST;}
    public static boolean receiptMatches(WorldTreeState state,ResourcePaidReceipt receipt){
        if(state.target==null||receipt==null)return false;var price=cost(state.target);
        return receipt.payerEpoch().equals(state.payerEpoch)&&receipt.purpose().equals(purpose(state.target))
            &&receipt.food()==price.food()&&receipt.wood()==price.wood()&&receipt.ore()==price.ore()&&receipt.emerald()==price.emerald()&&receipt.extra().equals(price.extra());
    }
    private record ElectionKey(net.minecraft.server.MinecraftServer server,String owner,UUID epoch){}
    private record Election(long tick,DreamAnchorPlacement leader){}
    private static final Map<ElectionKey,Election> elections=new HashMap<>();
    private DreamWorldTreeService(){}
    private static boolean loaded(DreamAnchorPlacement p){return p.getLevel().isLoaded(p.minCorner)&&p.getLevel().isLoaded(p.maxCorner);}
    private static boolean producerReady(DreamAnchorPlacement p){return p.ascended()&&p.isBuilt&&!p.isDestroyedServerside&&!p.shouldBeDestroyed()&&loaded(p);}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){elections.clear();}
    public static void bootstrap(){WorldTreePackets.register();}
    public static boolean isAscended(BuildingPlacement p){return p instanceof DreamAnchorPlacement tree&&tree.ascended();}
    public static boolean owns(ServerPlayer actor,DreamAnchorPlacement p){return actor!=null&&p.getLevel()==actor.level()&&actor.getName().getString().equals(p.ownerName)
        &&p.getBuilding() instanceof DreamAnchor&&BuildingServerEvents.getBuildings().contains(p)&&p.isBuilt&&!p.isDestroyedServerside&&!p.shouldBeDestroyed()&&p.getHealth()>0;}
    public static String start(ServerPlayer actor,DreamAnchorPlacement p,UUID instance){return start(actor,p,instance,WorldTreeModel.SHADOW_V3);}
    public static String start(ServerPlayer actor,DreamAnchorPlacement p,UUID instance,WorldTreeModel target){
        if(!WorldTreeModel.newTarget(target))return "invalid_target";
        if(!owns(actor,p)||!DreamPassiveEconomy.isDreamOwner(PlayerServerEvents.getRTSPlayer(p.ownerName)))return "invalid_host";
        var s=p.tree();if(!s.supported||!s.instance.equals(instance)||s.phase!=WorldTreeState.Phase.IDLE||s.model.branch!=WorldTreeModel.Branch.BASE)return "invalid_state";
        var plan=WorldTreeMorph.plan(p,target);if(!plan.valid())return plan.error();
        var r=ExtendedResourcesSync.account(p.ownerName);if(r==null)return "invalid_account";
        var paid=ResourceTransaction.pay(actor.getServer(),r,r.accountEpoch,r.revision,purpose(target),cost(target));if(!paid.applied())return paid.status().name().toLowerCase(Locale.ROOT);
        s.target=target;s.payer=p.ownerName;s.payerEpoch=r.accountEpoch;s.receipt=paid.receiptId();s.remaining=2400;s.phase=WorldTreeState.Phase.RUNNING;s.captureRevision=p.getCaptureRevision();s.blockedReason="";
        EconomyBalanceSync.send(actor);return "started";
    }
    public static String cancel(ServerPlayer actor,DreamAnchorPlacement p,UUID instance){
        if(!owns(actor,p)||!p.tree().instance.equals(instance))return "invalid_host";
        var s=p.tree();if(!s.supported||s.ascended||s.receipt==null)return "invalid_state";
        return requestRefund(p,(ServerLevel)p.getLevel())?"cancelled_refunded":"refund_pending";
    }
    private static boolean requestRefund(DreamAnchorPlacement p,ServerLevel l){var s=p.tree();s.phase=WorldTreeState.Phase.REFUND_PENDING;
        if(!WorldTreeReceiptIntents.mark(l,s,true))return false;WorldTreeReceiptIntents.retry(l);
        var r=ExtendedResourcesSync.account(s.payer);if(r!=null&&s.payerEpoch.equals(r.accountEpoch)&&!r.paidReceipts.containsKey(s.receipt)){s.clearTask();return true;}return false;
    }
    public static void captured(DreamAnchorPlacement p,ServerLevel l){
        var s=p.tree();if(!s.supported)return;
        s.incomeElapsed=0;s.wasLeader=false;s.incomeEpoch=null;
        if(s.receipt!=null)requestRefund(p,l); // synchronously establishes earned intent before owner defeat/commands/save
        s.captureRevision=p.getCaptureRevision();
    }
    public static void destroyed(DreamAnchorPlacement p,ServerLevel l){var s=p.tree();if(!s.supported||s.receipt==null)return;
        // A previously earned cancellation refund is not revoked by later destruction.
        boolean captured=!s.payer.equals(p.ownerName)||(s.captureRevision!=Long.MIN_VALUE&&s.captureRevision!=p.getCaptureRevision());
        if(WorldTreeReceiptIntents.mark(l,s,s.phase==WorldTreeState.Phase.REFUND_PENDING||captured))WorldTreeReceiptIntents.retry(l);
    }
    public static void tick(DreamAnchorPlacement p,Level tickLevel){
        if(!(tickLevel instanceof ServerLevel l)||tickLevel!=p.getLevel()||tickLevel.dimension()!=Level.OVERWORLD||!l.getServer().isSameThread()||!BuildingServerEvents.getBuildings().contains(p))return;
        var s=p.tree();if(!s.supported||s.lastTick==l.getGameTime())return;s.lastTick=l.getGameTime();
        boolean capture=s.captureRevision!=Long.MIN_VALUE&&s.captureRevision!=p.getCaptureRevision();s.captureRevision=p.getCaptureRevision();
        if(capture||!s.incomeOwner.equals(p.ownerName)){s.incomeElapsed=0;s.wasLeader=false;s.incomeOwner=p.ownerName;s.incomeEpoch=null;}
        if(s.receipt!=null&&(capture||!s.payer.equals(p.ownerName)))requestRefund(p,l);
        if(s.phase==WorldTreeState.Phase.REFUND_PENDING){requestRefund(p,l);return;}
        if(s.receipt!=null){
            var r=ExtendedResourcesSync.account(s.payer);
            if(r==null||!Objects.equals(s.payerEpoch,r.accountEpoch)){s.blockedReason="account_changed";return;}
            var receipt=r.paidReceipts.get(s.receipt);
            if(receipt!=null&&receipt.purpose().equals(purpose(s.target)+":refund")){requestRefund(p,l);return;}
            if(receipt!=null&&receipt.purpose().equals(purpose(s.target)+":forfeit")){WorldTreeReceiptIntents.retry(l);s.clearTask();return;}
            if(!receiptMatches(s,receipt)){s.blockedReason="missing_receipt";return;}
            if(!p.isBuilt||p.shouldBeDestroyed()||p.isDestroyedServerside||!loaded(p)||!DreamPassiveEconomy.isDreamOwner(PlayerServerEvents.getRTSPlayer(p.ownerName)))return;
            if(s.remaining>0)s.remaining--;
            if(s.remaining==0){
                var plan=WorldTreeMorph.plan(p);
                if(!plan.valid()){s.phase=WorldTreeState.Phase.BLOCKED;s.blockedReason=plan.error();return;}
                if(r.revision<0||r.revision==Long.MAX_VALUE||!r.extendedStateSupported()){s.blockedReason="invalid_account";return;}
                if(s.target==WorldTreeModel.WIND_V4&&!com.dreamseekers.progression.DreamWindProgression.canEarnWind(l,s.payer,s.payerEpoch)){s.blockedReason="invalid_progression";return;}
                if(!WorldTreeMorph.commit(p,plan)){s.phase=WorldTreeState.Phase.BLOCKED;s.blockedReason="world_write_rejected";return;}
                // World writes may invoke native/mixin callbacks. Revalidate entitlement after them,
                // leaving only the callback-free ledger consume and pure grant in the atomic tail.
                if(s.target==WorldTreeModel.WIND_V4&&!com.dreamseekers.progression.DreamWindProgression.canEarnWind(l,s.payer,s.payerEpoch)){
                    WorldTreeMorph.rollback(p,plan);s.phase=WorldTreeState.Phase.BLOCKED;s.blockedReason="invalid_progression";return;
                }
                var used=ResourceTransaction.consumeReceipt(l.getServer(),r,s.payerEpoch,r.revision,s.receipt);
                if(!used.applied()){WorldTreeMorph.rollback(p,plan);s.phase=WorldTreeState.Phase.BLOCKED;s.blockedReason="receipt_commit_failed";return;}
                if(s.target==WorldTreeModel.WIND_V4){if(!com.dreamseekers.progression.DreamWindProgression.earnWind(l,s.payer,s.payerEpoch))throw new IllegalStateException("Validated Wind grant lost invariant after callback-free receipt consume");com.dreamseekers.progression.DreamWindPackets.sendToOwner(l.getServer(),s.payer);}
                s.model=s.target;s.geometryHighest=s.model.count;s.ascended=s.model.branch==WorldTreeModel.Branch.SHADOW;s.clearTask();s.incomeElapsed=0;s.wasLeader=false;
                BuildingClientboundPacket.syncBuilding(p.originPos,p.getBlocksPlaced(),p.partialBlocksDestroyed,p.ownerName,p.scenarioRoleIndex);
            }
            return;
        }
        if(!s.ascended)return;
        income(p,l,s);
    }
    private static void income(DreamAnchorPlacement p,ServerLevel l,WorldTreeState s){
        var player=PlayerServerEvents.getRTSPlayer(p.ownerName);var r=ExtendedResourcesSync.account(p.ownerName);
        if(player==null||r==null||!r.extendedStateSupported()||!producerReady(p)){s.incomeElapsed=0;s.wasLeader=false;return;}
        if(!Objects.equals(s.incomeEpoch,r.accountEpoch)){s.incomeEpoch=r.accountEpoch;s.incomeElapsed=0;s.wasLeader=false;}
        var key=new ElectionKey(l.getServer(),p.ownerName,r.accountEpoch);
        elections.keySet().removeIf(k->k.server==l.getServer()&&k.owner.equals(p.ownerName)&&!k.epoch.equals(r.accountEpoch));
        var elected=elections.get(key);
        if(elected==null||elected.tick!=l.getGameTime()){
            DreamAnchorPlacement selected=null;
            for(var b:BuildingServerEvents.getBuildings())if(b instanceof DreamAnchorPlacement candidate&&candidate.getLevel()==l&&candidate.ownerName.equals(p.ownerName)&&producerReady(candidate)){
                if(selected==null||candidate.tree().instance.compareTo(selected.tree().instance)<0)selected=candidate;
            }
            elected=new Election(l.getGameTime(),selected);elections.put(key,elected);
        }
        DreamAnchorPlacement leader=elected.leader;
        if(leader!=p){s.incomeElapsed=0;s.wasLeader=false;return;}
        if(!s.wasLeader){s.incomeElapsed=0;s.wasLeader=true;}
        boolean dream=DreamPassiveEconomy.isDreamOwner(player);
        boolean room=r.food<Integer.MAX_VALUE||r.wood<Integer.MAX_VALUE||r.ore<Integer.MAX_VALUE||r.emerald<Integer.MAX_VALUE||(dream&&r.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)<DreamPassiveEconomy.SHADOW_CAP);
        if(!room)return;
        if(s.incomeElapsed<99){s.incomeElapsed++;return;}
        var amount=new ResourceTransaction.Cost(5,5,5,1,dream?Map.of(DreamPassiveEconomy.SHADOW,1):Map.of());
        var result=ResourceTransaction.creditIncome(l.getServer(),r,r.accountEpoch,r.revision,amount);
        if(result.applied()){s.incomeElapsed=0;DreamPassiveIncomePublication.publish(l.getServer(),player,result);}
    }
    @SubscribeEvent public static void retryClaims(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END)return;
        elections.entrySet().removeIf(entry->entry.getKey().server==e.getServer()&&entry.getValue().tick<e.getServer().overworld().getGameTime());
        if(e.getServer().getTickCount()%20==0)WorldTreeReceiptIntents.retry(e.getServer().overworld());}
}
