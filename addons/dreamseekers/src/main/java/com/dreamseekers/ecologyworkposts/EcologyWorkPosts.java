package com.dreamseekers.ecologyworkposts;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.DreamBuildingSpec;
import com.dreamseekers.building.DreamScaffoldBuilding;
import com.dreamseekers.function.DreamFunctionalPlacement;
import com.dreamseekers.skill.SkillIdentity;
import com.dreamseekers.shadow.TerrorbeakUpgradeService;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.research.economy.ResearchPlacementIdentity;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Explicit, transient outdoor care orders. Nearby bodies are never admitted implicitly. */
@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
public final class EcologyWorkPosts {
    public static final int CAPACITY=2, APPROACH_TIMEOUT=1200, REST_TICKS=200, PULSE_TICKS=40;
    private static final Map<LivingEntity,Lease> LEASES=new IdentityHashMap<>();
    private static final Map<LivingEntity,Combat> COMBAT=new WeakHashMap<>();
    private record Combat(long tick) {}
    private static void combat(LivingEntity entity,long tick){COMBAT.put(entity,new Combat(tick));}
    private EcologyWorkPosts() {}
    private static final class Lease {
        final Mob mob; final Unit unit; final BuildingPlacement court; final SkillIdentity.Stamp identity;
        final UUID instance, account, lease=UUID.randomUUID(); final long capture, started; final BlockPos post;
        boolean arrived,processing; long nextPulse;
        Lease(Mob mob,BuildingPlacement court,BlockPos post) {
            this.mob=mob;unit=(Unit)mob;this.court=court;this.post=post.immutable();identity=SkillIdentity.capture(mob);
            instance=ResearchPlacementIdentity.serverIdentity(court);capture=court.getCaptureRevision();
            account=ExtendedResourcesSync.account(court.ownerName).accountEpoch;
            started=mob.level().getGameTime();nextPulse=started+REST_TICKS;
        }
    }
    public record Occupant(int id,UUID uuid,UUID lease,BlockPos post,boolean arrived) {}
    public static boolean isCourt(BuildingPlacement p) {
        return p!=null&&p.getBuilding() instanceof DreamScaffoldBuilding b&&b.spec()==DreamBuildingSpec.CONTRACT_ECOLOGY;
    }
    public static boolean live(BuildingPlacement p) {
        return isCourt(p)&&p.level instanceof ServerLevel&&p.isBuilt&&!p.isDestroyedServerside
            &&p.getHealth()>0&&!p.shouldBeDestroyed()&&p.getCaptureRevision()>=0
            &&p instanceof DreamFunctionalPlacement f&&f.stateSnapshot().supported()
            &&BuildingServerEvents.getBuildings().contains(p)&&p.level.hasChunksAt(p.minCorner,p.maxCorner);
    }
    public static boolean owns(ServerPlayer actor,BuildingPlacement p) {
        if(actor==null||!live(p)||actor.level()!=p.level||!actor.level().dimension().equals(Level.OVERWORLD)
            ||!actor.getName().getString().equals(p.ownerName)||PlayerServerEvents.getRTSPlayer(p.ownerName)==null)return false;
        var account=ExtendedResourcesSync.account(p.ownerName);
        return account!=null&&account.extendedStateSupported();
    }
    public static boolean eligible(LivingEntity e,BuildingPlacement p) {
        if(!(e instanceof Mob mob)||!(e instanceof Unit unit)||!e.isAlive()||e.isRemoved()||e.level()!=p.level
            ||!p.ownerName.equals(unit.getOwnerName())||unit.isFlyingUnit()||e.isPassenger()||e.isVehicle()
            ||((e.getPersistentData().contains(com.dreamseekers.neutralcontract.NeutralContracts.ORIGIN)
                ||e.getPersistentData().contains(RtsKillValueClaims.FREE_ORIGIN_KEY))
                &&!com.dreamseekers.neutralcontract.CommittedContractUnits.isCommittedPermanent(e,p.ownerName))
            ||com.dreamseekers.neutralcontract.NeutralContracts.pending(e)||TerrorbeakUpgradeService.pending(e)
            ||e.getPersistentData().contains("dreamseekers:temporary_summon")
            ||e instanceof com.solegendary.reignofnether.unit.units.monsters.SilverfishUnit
            ||(e instanceof com.solegendary.reignofnether.unit.units.monsters.ZombieUnit zombie&&zombie.isSummoned())
            ||(e instanceof com.solegendary.reignofnether.unit.units.neutral.BeeUnit bee&&bee.isSummoned())
            ||!UnitServerEvents.getAllUnits().contains(e)||unit.getMoveGoal()==null)return false;
        @SuppressWarnings("unchecked") var type=(net.minecraft.world.entity.EntityType<? extends Mob>)mob.getType();
        return ProductionItems.getProductionItem(type)!=null||TerrorbeakUpgradeService.isCommittedAdvanced(e);
    }
    private static boolean valid(Lease l) {
        var a=ExtendedResourcesSync.account(l.court.ownerName);
        return LEASES.get(l.mob)==l&&live(l.court)&&l.identity.valid()&&eligible(l.mob,l.court)
            &&l.instance.equals(ResearchPlacementIdentity.serverIdentity(l.court))&&l.capture==l.court.getCaptureRevision()
            &&a!=null&&a.extendedStateSupported()&&l.account.equals(a.accountEpoch)
            &&PlayerServerEvents.getRTSPlayer(l.court.ownerName)!=null;
    }
    /** A reset means a new order, including STOP. Called before native goal reset, with no navigation writes. */
    public static void commandReset(Unit unit) {
        if(unit instanceof LivingEntity e&&!e.level().isClientSide())LEASES.remove(e);
    }
    private static AABB bodyAt(Mob mob,BlockPos post) {
        return mob.getBoundingBox().move(post.getX()+.5-mob.getX(),post.getY()-mob.getY(),post.getZ()+.5-mob.getZ());
    }
    private static boolean safe(Mob mob,BuildingPlacement p,BlockPos post) {
        AABB box=bodyAt(mob,post);Level level=p.level;
        if(!level.hasChunksAt(BlockPos.containing(box.minX,box.minY-1,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))
            ||box.minY<level.getMinBuildHeight()||box.maxY>level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(box)
            ||level.containsAnyLiquid(box)||!level.noCollision(mob,box)
            ||!level.getEntities(mob,box,e->e instanceof LivingEntity&&e.isAlive()).isEmpty())return false;
        for(int x=(int)Math.floor(box.minX+1e-6);x<=(int)Math.floor(box.maxX-1e-6);x++)
            for(int z=(int)Math.floor(box.minZ+1e-6);z<=(int)Math.floor(box.maxZ-1e-6);z++) {
                BlockPos floor=new BlockPos(x,post.getY()-1,z);
                if(!level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP))return false;
            }
        for(Lease other:LEASES.values())if(other.mob!=mob&&other.court.level==level&&bodyAt(other.mob,other.post).inflate(.2).intersects(box))return false;
        return true;
    }
    private static BlockPos freePost(Mob mob,BuildingPlacement p) {
        int cx=(p.minCorner.getX()+p.maxCorner.getX())/2,cz=(p.minCorner.getZ()+p.maxCorner.getZ())/2;
        List<BlockPos> candidates=new ArrayList<>();int radius=(int)Math.ceil(mob.getBbWidth()/2)+1;
        for(int off:new int[]{-3,3,0})for(int dy:new int[]{0,1,-1,2,-2}) {
            int y=p.originPos.getY()+1+dy;
            candidates.add(new BlockPos(cx+off,y,p.minCorner.getZ()-radius));
            candidates.add(new BlockPos(cx+off,y,p.maxCorner.getZ()+radius));
            candidates.add(new BlockPos(p.minCorner.getX()-radius,y,cz+off));
            candidates.add(new BlockPos(p.maxCorner.getX()+radius,y,cz+off));
        }
        candidates.sort(Comparator.comparingDouble(b->mob.distanceToSqr(b.getX()+.5,b.getY(),b.getZ()+.5)));
        return candidates.stream().filter(b->safe(mob,p,b)).findFirst().orElse(null);
    }
    private static boolean atPost(Lease l) {
        // Match the native movement goal's settle radius instead of demanding a point it stops short of.
        double dx=l.mob.getX()-(l.post.getX()+.5),dz=l.mob.getZ()-(l.post.getZ()+.5);
        var box=l.mob.getBoundingBox();
        return l.mob.onGround()&&Math.abs(l.mob.getY()-l.post.getY())<=.45
            &&dx*dx+dz*dz<=com.solegendary.reignofnether.unit.pathfinding.PathfinderConfig.ARRIVAL_SETTLE_SQ
            &&l.mob.level().noCollision(l.mob,box)&&!l.mob.level().containsAnyLiquid(box)
            &&l.mob.level().getEntities(l.mob,box,e->e instanceof LivingEntity&&e.isAlive()&&!e.isRemoved()).isEmpty();
    }
    /** Scope is eight blocks from the court's footprint, not its centre (the court itself is 13 blocks wide). */
    private static boolean inRange(Lease l) {
        return new AABB(l.court.minCorner,l.court.maxCorner.offset(1,1,1)).inflate(8).contains(l.mob.position());
    }
    public static String assign(ServerPlayer actor,BuildingPlacement court,UUID instance,long capture,List<EcologyPackets.Target> targets) {
        if(!owns(actor,court)||!ResearchPlacementIdentity.serverIdentity(court).equals(instance)||capture!=court.getCaptureRevision())return "forbidden";
        prune();int added=0;
        for(var target:targets) {
            Entity found=actor.serverLevel().getEntity(target.id());
            if(!(found instanceof Mob mob)||!mob.getUUID().equals(target.uuid())||!eligible(mob,court))continue;
            Lease old=LEASES.get(mob);if(old!=null&&old.court==court&&valid(old))continue;
            if(LEASES.values().stream().filter(l->l.court==court).count()>=CAPACITY)break;
            BlockPos post=freePost(mob,court);if(post==null)continue;
            Unit unit=(Unit)mob;Unit.fullResetBehaviours(unit);unit.setMoveTarget(post);
            Lease lease=new Lease(mob,court,post);LEASES.put(mob,lease);added++;
        }
        return added>0?"assigned":"no_post";
    }
    public static String release(ServerPlayer actor,BuildingPlacement court,UUID instance,long capture,UUID leaseId) {
        if(!owns(actor,court)||!ResearchPlacementIdentity.serverIdentity(court).equals(instance)||capture!=court.getCaptureRevision())return "forbidden";
        for(Lease l:List.copyOf(LEASES.values()))if(l.court==court&&(leaseId==null||leaseId.equals(l.lease))) {
            boolean reset=valid(l)&&actor.getName().getString().equals(l.unit.getOwnerName());
            LEASES.remove(l.mob);if(reset)Unit.fullResetBehaviours(l.unit);
        }
        return "released";
    }
    private static boolean validBody(Lease l){return l.identity.valid()&&l.mob.level()==l.court.level;}
    public static List<Occupant> snapshot(BuildingPlacement court) {
        return LEASES.values().stream().filter(l->l.court==court&&valid(l)).map(l->new Occupant(l.mob.getId(),l.mob.getUUID(),l.lease,l.post,l.arrived)).toList();
    }
    private static void prune(){LEASES.values().removeIf(l->!valid(l));}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event) {
        if(event.getEntity().level().isClientSide()||event.getAmount()<=0||event.isCanceled())return;
        long now=event.getEntity().level().getGameTime();combat(event.getEntity(),now);
        Entity attacker=event.getSource().getEntity();
        if(attacker instanceof LivingEntity living&&attacker.level()==event.getEntity().level())combat(living,now);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        for(Lease l:List.copyOf(LEASES.values())) {
            if(!valid(l)){LEASES.remove(l.mob);continue;}
            long now=l.mob.level().getGameTime();
            if(UnitServerEvents.hasPendingImmediateAction(l.mob.getId())){LEASES.remove(l.mob);continue;}
            if(!l.arrived) {
                if(atPost(l)) {l.arrived=true;l.unit.getMoveGoal().stopMoving();l.unit.setHoldPosition(true);l.nextPulse=now+REST_TICKS;}
                else if(now-l.started>=APPROACH_TIMEOUT||!l.post.equals(l.unit.getMoveGoal().getMoveTarget())) {LEASES.remove(l.mob);if(validBody(l))Unit.fullResetBehaviours(l.unit);}
                continue;
            }
            if(!atPost(l)||!inRange(l)||!l.unit.getHoldPosition()){LEASES.remove(l.mob);continue;}
            // Autonomous combat also suspends resting. Never change target/navigation to force healing.
            if(l.unit.hasLivingTarget())combat(l.mob,now);
            Combat combat=COMBAT.get(l.mob);long last=combat==null?l.started:combat.tick();
            if(now<last||now-last<REST_TICKS||!DreamSpecialistResearch.careUnlocked(l.court.ownerName,false)) {l.nextPulse=now+PULSE_TICKS;continue;}
            if(l.processing||now<l.nextPulse)continue;
            l.nextPulse=now+PULSE_TICKS; // no catch-up, no stored full-health burst
            float requested=Math.min(1,Math.max(0,l.mob.getMaxHealth()-l.mob.getHealth()));
            if(requested<=0)continue;
            l.processing=true;
            try {
                float healed=ForgeEventFactory.onLivingHeal(l.mob,requested);
                if(valid(l)&&atPost(l)&&inRange(l)&&l.unit.getHoldPosition()&&!UnitServerEvents.hasPendingImmediateAction(l.mob.getId())
                    &&DreamSpecialistResearch.careUnlocked(l.court.ownerName,false)&&COMBAT.get(l.mob)==combat&&!l.unit.hasLivingTarget()
                    &&Float.isFinite(healed)&&healed>0)l.mob.setHealth(Math.min(l.mob.getMaxHealth(),l.mob.getHealth()+Math.min(1,healed)));
            } finally {l.processing=false;}
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){LEASES.clear();COMBAT.clear();}
}
