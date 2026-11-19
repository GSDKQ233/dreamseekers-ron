package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.sandbox.SandboxEconomyFixture;
import net.minecraft.resources.ResourceLocation;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.AbstractTotem;
import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;
import com.solegendary.reignofnether.unit.units.monsters.SilverfishUnit;
import com.solegendary.reignofnether.unit.units.monsters.SlimeUnit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;
import com.solegendary.reignofnether.unit.units.piglins.MagmaCubeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** One shared economic death bridge. No mine proximity and no direct reward here. */
@Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID)
public final class RtsKillConfirmationEvents {
    private RtsKillConfirmationEvents() {}
    private static final Map<LivingEntity,Boolean> SEEN_THIS_DEATH = new WeakHashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    // Short-lived event receipts only. LOWEST consumes even canceled events; tick/stop clears leftovers.
    private static final Map<LivingEntity,EarlyDeath> EARLY = new java.util.IdentityHashMap<>();
    private static final class EarlyDeath {
        final Pending pending; boolean nativeCreeperDeath;
        EarlyDeath(Pending pending){this.pending=pending;}
    }
    private record Pending(LivingDeathEvent death, LivingEntity victim, LivingEntity killer,
                           RTSPlayer player, Resources account, Faction faction,
                           VictimEligibility victimEligibility,
                           EconomicUnitIdentity.Stamp killerStamp, EconomicUnitIdentity.Stamp victimStamp,
                           RtsProjectileProvenance.DamageEvidence damageEvidence,
                           ServerLevel level, MinecraftServer server, int tick, ConfirmedRtsKillEvent.Snapshot snapshot) {}

    private sealed interface VictimEligibility permits AccountVictim, FixtureVictim {
        boolean valid(Pending pending);
    }
    /** Original ordinary-game qualification, including the narrowly supported final defeat. */
    private record AccountVictim(RTSPlayer player, Resources account, java.util.UUID epoch) implements VictimEligibility {
        public boolean valid(Pending p) {
            if(SandboxEconomyFixture.has(p.victim))return false;
            RTSPlayer currentPlayer=PlayerServerEvents.getRTSPlayer(p.snapshot.victimOwner());
            Resources currentAccount=RtsKillConfirmationEvents.account(p.snapshot.victimOwner());
            if(currentPlayer==player&&currentAccount==account
                    &&epoch.equals(account.accountEpoch)&&account.extendedStateSupported())
                return p.victimStamp.sameOwner();
            return currentPlayer==null&&currentAccount==null
                    &&ResourcesServerEvents.resourcesList.stream().noneMatch(r -> p.snapshot.victimOwner().equals(r.ownerName))
                    &&(p.victimStamp.sameOwner()||p.victimStamp.clearedOnceByDefeat());
        }
    }
    /** No defeat/unowned exception and no fallback to ordinary eligibility for any marked fixture. */
    private record FixtureVictim(SandboxEconomyFixture.Proof proof) implements VictimEligibility {
        public boolean valid(Pending p) {
            return p.victimStamp.sameOwner()&&SandboxEconomyFixture.valid(p.victim,
                    p.snapshot.killerOwner(),p.snapshot.accountEpoch(),proof);
        }
    }
    private static ResourceLocation rewardFaction(Faction ownerFaction, LivingEntity shooter) {
        return ownerFaction==Factions.NONE&&shooter instanceof Unit unit
                ?Factions.getFaction(unit).key:ownerFaction.key;
    }

    private static boolean coreEligible(LivingEntity victim) {
        if (!(victim instanceof Unit) || RtsKillValueClaims.isFree(victim)) return false;
        return !(victim instanceof AbstractTotem) && !(victim instanceof SilverfishUnit)
                && !(victim instanceof ZombieUnit zombie && zombie.isSummoned())
                && !(victim instanceof BeeUnit bee && bee.isSummoned())
                && !(victim instanceof SlimeUnit slime && !(victim instanceof MagmaCubeUnit) && slime.getSize() <= 1);
    }
    private static boolean enemies(String killer, String victim) {
        return killer != null && !killer.isBlank() && victim != null && !victim.isBlank()
                && !killer.equals(victim) && !AlliancesServerEvents.isAllied(killer,victim);
    }
    private static Resources account(String owner) {
        Resources found=null;
        for (Resources r : ResourcesServerEvents.resourcesList) if (owner.equals(r.ownerName)) {
            if (found != null) return null;
            found=r;
        }
        return found;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void captureEarlyDeath(LivingDeathEvent event) {
        LivingEntity victim=event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || event.isCanceled() || EARLY.size()>=4096 || EARLY.containsKey(victim)
                || !coreEligible(victim) || !RtsProjectileProvenance.registered(victim) || RtsKillValueClaims.isClaimed(victim) || SEEN_THIS_DEATH.containsKey(victim)) return;
        RtsProjectileProvenance.DamageEvidence damageEvidence=RtsProjectileProvenance.captureDamage(event.getSource(),victim);
        if(damageEvidence==null)return;
        LivingEntity killer=damageEvidence.shooter();
        if(!(killer instanceof Unit attacker)||killer.level()!=level)return;
        Unit target=(Unit)victim;
        String owner=attacker.getOwnerName(), victimOwner=target.getOwnerName();
        if (!enemies(owner,victimOwner)) return;
        RTSPlayer player=PlayerServerEvents.getRTSPlayer(owner);
        Resources account=account(owner);
        RTSPlayer victimPlayer=PlayerServerEvents.getRTSPlayer(victimOwner);
        Resources victimAccount=account(victimOwner);
        if (player==null || player.faction==null || player.faction.key==null
                || account==null || !account.extendedStateSupported()) return;
        VictimEligibility victimEligibility;
        if(SandboxEconomyFixture.has(victim)) {
            if(player.faction!=Factions.NONE)return;
            var proof=SandboxEconomyFixture.capture(victim,owner,account.accountEpoch);
            if(proof==null)return;
            victimEligibility=new FixtureVictim(proof);
        } else {
            if(victimPlayer==null||victimAccount==null||!victimAccount.extendedStateSupported())return;
            victimEligibility=new AccountVictim(victimPlayer,victimAccount,victimAccount.accountEpoch);
        }
        EconomicUnitIdentity.Stamp killerStamp=EconomicUnitIdentity.capture(killer);
        EconomicUnitIdentity.Stamp victimStamp=EconomicUnitIdentity.capture(victim);
        java.util.UUID killerAccountEpoch=account.accountEpoch;
        Faction killerFaction=player.faction;
        ResourceLocation rewardFaction=rewardFaction(killerFaction,killer);
        java.util.Set<net.minecraft.resources.ResourceLocation> research=ResearchServerEvents.getResearchSnapshot(owner);
        boolean allResearchCheat=ResearchServerEvents.playerHasCheat(owner,"medievalman");
        ResourceCost cost=target.getCost();
        if (cost==null || cost.food<0 || cost.wood<0 || cost.ore<0
                || ((long)cost.food+cost.wood+cost.ore)==0) return;
        // Snapshot before addon callbacks: a listener cannot inflate the value by modifying its mutable cost object.
        int food=cost.food, wood=cost.wood, ore=cost.ore;
        RtsKillValueEligibilityEvent eligibility=new RtsKillValueEligibilityEvent(victim,killer);
        if (MinecraftForge.EVENT_BUS.post(eligibility)) return;
        ConfirmedRtsKillEvent.Snapshot snapshot=new ConfirmedRtsKillEvent.Snapshot(owner,killerFaction.key,rewardFaction,
                killerAccountEpoch,victim.getUUID(),RtsKillValueClaims.valueId(victim),victimOwner,food,wood,ore,
                research,allResearchCheat);
        EARLY.put(victim,new EarlyDeath(new Pending(event,victim,killer,player,account,killerFaction,victimEligibility,
                killerStamp,victimStamp,damageEvidence,level,level.getServer(),level.getServer().getTickCount(),snapshot)));
    }
    /** Called solely by CreeperUnit's unchanged native explosion path, not by an arbitrary discard. */
    public static void nativeCreeperDeath(com.solegendary.reignofnether.unit.units.monsters.CreeperUnit victim) {
        EarlyDeath early=EARLY.get(victim);
        if(early!=null&&!early.pending.death.isCanceled()&&early.pending.victim==victim
                &&early.pending.tick==early.pending.server.getTickCount()&&victim.getHealth()<=0)
            early.nativeCreeperDeath=true;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void onDeath(LivingDeathEvent event) {
        EarlyDeath early=EARLY.get(event.getEntity());
        if(early==null||early.pending.death!=event)return;
        EARLY.remove(event.getEntity());
        Pending pending=early.pending;
        if(event.isCanceled()||SEEN_THIS_DEATH.containsKey(pending.victim)||RtsKillValueClaims.isClaimed(pending.victim))return;
        boolean nativeCreeper=early.nativeCreeperDeath
                &&pending.victim instanceof com.solegendary.reignofnether.unit.units.monsters.CreeperUnit
                &&pending.victim.isRemoved()&&pending.victim.getRemovalReason()==Entity.RemovalReason.DISCARDED;
        if(!RtsProjectileProvenance.registered(pending.victim)&&!nativeCreeper)return;
        if(!valid(pending))return;
        SEEN_THIS_DEATH.put(pending.victim,true);
        PENDING.add(pending);
    }
    private static boolean valid(Pending p) {
        if (p.death.isCanceled() || p.victim.getHealth()>0 || !p.victim.isDeadOrDying()
                || p.victim.level()!=p.level || p.killer.level()!=p.level
                || !p.killerStamp.sameOwner() || !p.damageEvidence.valid()
                || !p.victimEligibility.valid(p)
                || PlayerServerEvents.getRTSPlayer(p.snapshot.killerOwner())!=p.player
                || p.player.faction!=p.faction || !p.snapshot.killerFaction().equals(p.player.faction.key)
                || !p.snapshot.rewardFaction().equals(rewardFaction(p.player.faction,p.killer))
                || account(p.snapshot.killerOwner())!=p.account || !p.account.extendedStateSupported()
                || !p.snapshot.accountEpoch().equals(p.account.accountEpoch)
                || !enemies(p.snapshot.killerOwner(),p.snapshot.victimOwner())) return false;
        return true;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase!=TickEvent.Phase.START) return;
        EARLY.entrySet().removeIf(e -> e.getValue().pending.server==event.getServer()
                &&e.getValue().pending.tick!=event.getServer().getTickCount());
        List<Pending> ready=new ArrayList<>();
        PENDING.removeIf(p -> {
            if (p.server!=event.getServer() || p.tick==p.server.getTickCount()) return false;
            ready.add(p); return true;
        });
        for (Pending p : ready) {
            if (!valid(p)) {
                if (p.death.isCanceled() || p.victim.getHealth()>0) SEEN_THIS_DEATH.remove(p.victim);
                continue;
            }
            ConfirmedRtsKillEvent confirmed=new ConfirmedRtsKillEvent(p.snapshot,p.server,p.victim,p.account,() -> valid(p));
            confirmed.beginDispatch();
            try { MinecraftForge.EVENT_BUS.post(confirmed); }
            finally { confirmed.endDispatch(); }
        }
    }
    @SubscribeEvent public static void aliveTick(LivingEvent.LivingTickEvent event) {
        // This clears ONLY transient duplicate-event suppression after an unclaimed/cancelled death.
        // The persistent economicValue.claim is deliberately never removed by revival.
        if (!event.getEntity().level().isClientSide() && event.getEntity().isAlive()) SEEN_THIS_DEATH.remove(event.getEntity());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        PENDING.clear(); SEEN_THIS_DEATH.clear(); EARLY.clear(); // Pending deaths are never replayed after restart.
    }
}
