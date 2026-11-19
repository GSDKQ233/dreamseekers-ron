package com.dreamseekers.neutralcontract;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.ecologyworkposts.EcologyWorkPosts;
import com.dreamseekers.economy.*;
import com.dreamseekers.function.DreamFunctionalPlacement;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.dreamseekers.unitprogression.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.pasterdream.entity.*;
import java.util.*;

/** Real worker approach/channel, then one staged native-to-RTS conversion. No automatic collection or map-only marker. */
@Mod.EventBusSubscriber(modid="dreamseekers")
public final class NeutralContracts {
    public static final int CHANNEL_TICKS=160,APPROACH_TICKS=600,COVERAGE=32;
    public static final String PENDING="dreamseekers:pending_neutral_contract",ORIGIN="dreamseekers:neutral_contract";
    private static final Map<FireflyWorker,Order> ORDERS=new IdentityHashMap<>();
    private static final Map<LivingEntity,Order> CLAIMS=new IdentityHashMap<>();
    private static final Map<LivingEntity,Mob> STAGING=new IdentityHashMap<>();
    private static final Map<LivingEntity,Long> DAMAGED=new WeakHashMap<>();
    private static final Map<LivingEntity,LivingDeathEvent> DEATHS=new IdentityHashMap<>();
    private static final Set<Order> BUSY=Collections.newSetFromMap(new IdentityHashMap<>());
    private NeutralContracts(){}
    public enum Kind { BEETLE(50,0,15,2),SNAIL(60,20,20,3);
        public final int food,ore,shadow,population;
        Kind(int food,int ore,int shadow,int population){this.food=food;this.ore=ore;this.shadow=shadow;this.population=population;}
        ResourceTransaction.Cost price(){return new ResourceTransaction.Cost(food,0,ore,Map.of(DreamPassiveEconomy.SHADOW,shadow));}
    }
    private static final class Order {
        final FireflyWorker worker;final LivingEntity target;final Kind kind;final ServerPlayer actor;final Connection connection;
        final BuildingPlacement court;final UUID instance,epoch,origin,view;final long capture,ownerRevision,liveOwnerRevision,started;
        final Resources account;final SkillIdentity.Stamp stamp;final long command;final BlockPos moveTarget;
        long channelStart=-1;Vec3 workerPosition,targetPosition;boolean invalid;
        Order(ServerPlayer actor,Connection connection,FireflyWorker worker,LivingEntity target,Kind kind,BuildingPlacement court,UUID view){
            this.actor=actor;this.connection=connection;this.worker=worker;this.target=target;this.kind=kind;this.court=court;this.view=view;
            instance=ResearchPlacementIdentity.serverIdentity(court);capture=court.getCaptureRevision();
            ownerRevision=((DreamFunctionalPlacement)court).stateSnapshot().ownerRevision();liveOwnerRevision=((DreamFunctionalPlacement)court).functionalOwnerRevision();
            account=ExtendedResourcesSync.account(worker.getOwnerName());epoch=account.accountEpoch;origin=target.getUUID();
            stamp=SkillIdentity.capture(worker);command=worker.rts().commandRevision;started=worker.level().getGameTime();moveTarget=target.blockPosition().immutable();
        }
    }
    public static boolean pending(Entity e){return e!=null&&e.getPersistentData().contains(PENDING);}
    /** Exact source types only; our RTS subclasses and every tame/pet lineage are categorically excluded. */
    public static Kind nativeKind(Entity e){
        if(e==null||e instanceof Unit||e.isRemoved()||!e.isAlive()||e.isPassenger()||e.isVehicle())return null;
        var id=ForgeRegistries.ENTITY_TYPES.getKey(e.getType());if(id==null||!id.getNamespace().equals("pasterdream"))return null;
        Kind kind=e.getClass()==BlackBeetleEntity.class&&id.getPath().equals("black_beetle")?Kind.BEETLE
            :e.getClass()==BasaltSnailEntity.class&&id.getPath().equals("basalt_snail")?Kind.SNAIL:null;
        if(kind==null)return null;
        if(e.hasCustomName())return null;
        if(e instanceof LivingEntity living)for(var stack:living.getAllSlots())if(!stack.isEmpty())return null;
        if(e instanceof Mob mob&&(mob.isLeashed()||mob.getLeashHolder()!=null))return null;
        // Deferred native load-time leash belongs to a player even before holder resolution.
        if(e instanceof com.dreamseekers.unit.mixin.NeutralContractLeashAccess leash&&leash.dreamseekers$contractPendingLeash()!=null)return null;
        if(e instanceof TamableAnimal tame&&(tame.isTame()||tame.getOwnerUUID()!=null))return null;
        if(e instanceof AgeableMob age&&age.isBaby())return null;
        for(String key:e.getPersistentData().getAllKeys()){
            String lower=key.toLowerCase(Locale.ROOT);
            if(lower.contains("owner")||lower.contains("tame")||lower.contains("contract")||lower.contains("summon")
                ||key.equals(RtsKillValueClaims.KEY)||key.equals(RtsKillValueClaims.FREE_ORIGIN_KEY))return null;
        }
        return kind;
    }
    private static boolean peaceful(LivingEntity target){return target.isAlive()&&!target.isRemoved()&&!target.isInvisible()
        &&target.getActiveEffects().isEmpty()&&!target.isOnFire()&&target.getTicksFrozen()==0&&!target.isPassenger()&&!target.isVehicle()
        &&target.hurtTime==0&&Float.isFinite(target.getHealth())&&Float.isFinite(target.getMaxHealth())&&target.getMaxHealth()>0
        &&(!(target instanceof Mob mob)||mob.getTarget()==null)
        &&target.level().getGameTime()-DAMAGED.getOrDefault(target,Long.MIN_VALUE/2)>20;}
    public static boolean owns(ServerPlayer actor,FireflyWorker worker){
        if(actor==null||worker==null||!(worker.level() instanceof ServerLevel level)||!level.getServer().isSameThread()
            ||actor.serverLevel()!=level||!actor.isAlive()||actor.isRemoved()||actor.connection==null
            ||!worker.isAlive()||worker.isRemoved()||!worker.isAddedToWorld()||level.getEntity(worker.getId())!=worker||level.getEntity(worker.getUUID())!=worker
            ||!UnitServerEvents.getAllUnits().contains(worker)||!worker.getOwnerName().equals(actor.getGameProfile().getName()))return false;
        var players=level.getServer().getPlayerList();var account=ExtendedResourcesSync.account(worker.getOwnerName());
        return players.getPlayer(actor.getUUID())==actor&&players.getPlayerByName(worker.getOwnerName())==actor
            &&DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(worker.getOwnerName()))&&account!=null
            &&account.extendedStateSupported()&&account.addonDataSupported()&&!RtsKillValueClaims.isFree(worker);
    }
    private static boolean courtOwn(FireflyWorker worker,BuildingPlacement court){
        if(!EcologyWorkPosts.live(court)||court.level!=worker.level()||!court.ownerName.equals(worker.getOwnerName()))return false;
        court.getDataStorage();var s=((DreamFunctionalPlacement)court).stateSnapshot();
        return s.supported()&&s.instanceId()!=null&&s.owner().equals(worker.getOwnerName())&&s.built();
    }
    private static boolean inCoverage(Entity e,BuildingPlacement court){return e.level()==court.level&&e.position().distanceToSqr(Vec3.atCenterOf(court.originPos))<=COVERAGE*COVERAGE;}
    private static BuildingPlacement courtFor(FireflyWorker worker,LivingEntity target){return BuildingServerEvents.getBuildings().stream()
        .filter(c->courtOwn(worker,c)&&inCoverage(worker,c)&&(target==null||inCoverage(target,c)))
        .min(Comparator.<BuildingPlacement>comparingDouble(c->worker.position().distanceToSqr(Vec3.atCenterOf(c.originPos)))
            .thenComparingInt(c->c.originPos.getX()).thenComparingInt(c->c.originPos.getY()).thenComparingInt(c->c.originPos.getZ())).orElse(null);}
    public static int used(FireflyWorker worker){var account=ExtendedResourcesSync.account(worker.getOwnerName());if(account==null)return 0;
        return ContractLedger.get(worker.level().getServer()).count(worker.getOwnerName(),account.accountEpoch)
            +(int)ORDERS.values().stream().filter(o->o.stamp.owner().equals(worker.getOwnerName())&&o.epoch.equals(account.accountEpoch)).count();}
    public static int remaining(FireflyWorker worker){Order o=ORDERS.get(worker);return o==null?0:o.channelStart<0?-1:(int)Math.max(0,CHANNEL_TICKS-(worker.level().getGameTime()-o.channelStart));}
    public static boolean active(FireflyWorker worker){return ORDERS.containsKey(worker);}
    public static boolean available(ServerPlayer actor,FireflyWorker worker){return owns(actor,worker)&&!FireflyRush.active(worker)&&!FireflyRush.hasPausedWork(worker)&&courtFor(worker,null)!=null&&ContractLedger.get(actor.server).supported()
        &&(active(worker)||used(worker)<ContractResearch.capacity(worker.getOwnerName(),false));}
    private static boolean funds(Order o){return o.account.food>=o.kind.food&&o.account.ore>=o.kind.ore&&o.account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)>=o.kind.shadow;}
    private static boolean population(Order o,Mob candidate){long total=UnitServerEvents.getCurrentPopulation(o.stamp.owner());
        for(var other:ORDERS.values())if(other!=o&&other.stamp.owner().equals(o.stamp.owner())&&other.epoch.equals(o.epoch))total+=other.kind.population;
        if(candidate==null||!UnitServerEvents.getAllUnits().contains(candidate))total+=o.kind.population;
        return total<=UnitServerEvents.maxPopulation&&total<=BuildingServerEvents.getTotalPopulationSupply(o.stamp.owner());}
    private static boolean target(Order o){var level=(ServerLevel)o.worker.level();return nativeKind(o.target)==o.kind&&peaceful(o.target)
        &&o.target.getUUID().equals(o.origin)&&level.getEntity(o.target.getId())==o.target&&level.getEntity(o.origin)==o.target&&o.target.isAddedToWorld()
        &&level.hasChunkAt(o.target.blockPosition())&&CoordinatedMark.visible(o.actor,o.target)&&inCoverage(o.target,o.court);}
    private static boolean valid(Order o){
        if(o.invalid||FireflyRush.active(o.worker)||FireflyRush.hasPausedWork(o.worker)||ORDERS.get(o.worker)!=o||CLAIMS.get(o.target)!=o||!owns(o.actor,o.worker)||!o.stamp.valid()
            ||!o.connection.isConnected()||o.connection.getPacketListener()!=o.actor.connection||o.worker.rts().commandRevision!=o.command
            ||UnitServerEvents.hasPendingImmediateAction(o.worker.getId())||!courtOwn(o.worker,o.court)||!inCoverage(o.worker,o.court))return false;
        var s=((DreamFunctionalPlacement)o.court).stateSnapshot();var ledger=ContractLedger.get(o.actor.server);
        return o.instance.equals(ResearchPlacementIdentity.serverIdentity(o.court))&&o.capture==o.court.getCaptureRevision()
            &&o.ownerRevision==s.ownerRevision()&&o.liveOwnerRevision>=0&&o.liveOwnerRevision==((DreamFunctionalPlacement)o.court).functionalOwnerRevision()&&ExtendedResourcesSync.account(o.stamp.owner())==o.account&&o.epoch.equals(o.account.accountEpoch)
            &&o.account.extendedStateSupported()&&o.account.addonDataSupported()&&ledger.canCommit(o.origin)
            &&used(o.worker)<=ContractResearch.capacity(o.stamp.owner(),false)&&target(o)
            &&o.worker.level().getGameTime()-DAMAGED.getOrDefault(o.worker,Long.MIN_VALUE/2)>20;
    }
    public static String begin(ServerPlayer actor,Connection connection,FireflyWorker worker,long ownerRevision,LivingEntity target,UUID view){
        if(!owns(actor,worker)||connection==null||!connection.isConnected()||connection.getPacketListener()!=actor.connection)return "forbidden";
        if(worker.rts().ownershipRevision!=ownerRevision)return "stale_source";
        if(FireflyRush.active(worker)||FireflyRush.hasPausedWork(worker)||active(worker)||CLAIMS.containsKey(target))return "busy";
        Kind kind=nativeKind(target);if(kind==null||target.level()!=worker.level()||!peaceful(target)||!CoordinatedMark.visible(actor,target))return "invalid_target";
        var court=courtFor(worker,target);if(court==null)return "no_court";
        var ledger=ContractLedger.get(actor.server);if(!ledger.canCommit(target.getUUID()))return "unsupported";
        if(used(worker)>=ContractResearch.capacity(worker.getOwnerName(),false))return "full";
        var preview=new Order(actor,connection,worker,target,kind,court,view);
        if(preview.liveOwnerRevision<0)return "unsupported";
        if(!funds(preview)||!population(preview,null))return "insufficient";
        Unit.fullResetBehaviours(worker);worker.setMoveTarget(target.blockPosition());
        var order=new Order(actor,connection,worker,target,kind,court,view);ORDERS.put(worker,order);CLAIMS.put(target,order);
        if(!valid(order)||!funds(order)||!population(order,null)){finish(order,"insufficient",true);return "insufficient";}
        return "approaching";
    }
    public static String cancel(ServerPlayer actor,FireflyWorker worker,long revision){if(!owns(actor,worker)||worker.rts().ownershipRevision!=revision)return "forbidden";
        Order order=ORDERS.get(worker);if(order==null)return "idle";if(BUSY.contains(order))return "busy";finish(order,"canceled",true);return "canceled";}
    /** Native tame setter observer prevents same-tick pet ownership ABA from reviving a claimed wild body. */
    public static void nativeOwnershipChanging(Entity entity){Order o=CLAIMS.get(entity);if(o!=null)o.invalid=true;}
    private static void finish(Order o,String status,boolean stop){
        ORDERS.remove(o.worker,o);CLAIMS.remove(o.target,o);
        if(stop&&o.stamp.valid()&&o.worker.rts().commandRevision==o.command&&!UnitServerEvents.hasPendingImmediateAction(o.worker.getId()))Unit.fullResetBehaviours(o.worker);
        if(owns(o.actor,o.worker)&&o.connection.isConnected()&&o.connection.getPacketListener()==o.actor.connection)
            ContractPackets.send(o.actor,o.connection,o.worker,o.view,status,0);
    }
    private static boolean safe(Order o,Mob candidate){var level=o.worker.level();var box=candidate.getBoundingBox();
        if(!level.hasChunksAt(BlockPos.containing(box.minX,box.minY-1,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))
            ||box.minY<level.getMinBuildHeight()||box.maxY>level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(box)
            ||level.containsAnyLiquid(box)||level.getBlockCollisions(candidate,box).iterator().hasNext()
            ||!level.getEntities(candidate,box,e->e!=o.target&&e instanceof LivingEntity&&e.isAlive()&&!e.isRemoved()).isEmpty())return false;
        for(int x=(int)Math.floor(box.minX+1e-5);x<=(int)Math.floor(box.maxX-1e-5);x++)for(int z=(int)Math.floor(box.minZ+1e-5);z<=(int)Math.floor(box.maxZ-1e-5);z++){
            BlockPos floor=new BlockPos(x,(int)Math.floor(box.minY)-1,z);if(!level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP))return false;}
        return true;
    }
    private static String complete(Order o){
        if(!valid(o)||!funds(o)||!population(o,null)||!o.worker.hasLineOfSight(o.target))return "interrupted";
        var level=(ServerLevel)o.worker.level();Mob next=o.kind==Kind.BEETLE?DreamseekersMod.BEETLE.get().create(level):DreamseekersMod.BASALT_SNAIL.get().create(level);
        if(!(next instanceof DreamRtsUnit unit))return "spawn_failed";
        float hp=o.target.getHealth(),max=o.target.getMaxHealth();Vec3 position=o.target.position();
        next.getPersistentData().putBoolean(PENDING,true);next.setNoAi(true);unit.setOwnerName(o.stamp.owner());unit.setHoldPosition(true);
        next.moveTo(position.x,position.y,position.z,o.target.getYRot(),o.target.getXRot());
        RtsKillValueClaims.markFree(next,RtsKillValueClaims.FreeOrigin.NEUTRAL_CONTRACT);
        CompoundTag provenance=new CompoundTag();provenance.putInt("schema",1);provenance.putUUID("origin",o.origin);provenance.putString("payer",o.stamp.owner());provenance.putUUID("account",o.epoch);next.getPersistentData().put(ORIGIN,provenance);
        DreamAscendancyAttributes.reconcile(next,DreamAscendancyAttributes.enabledFor(next));
        DreamSpecialistEffects.reconcileShell(next,DreamSpecialistResearch.unlocked(o.stamp.owner(),DreamSpecialistResearch.SHELL_WEAVING_ID,false));
        next.setHealth(DreamResearchMath.healthAfter(hp,max,next.getMaxHealth()));
        if(!safe(o,next))return "unsafe";
        float nextHp=next.getHealth(),nextMax=next.getMaxHealth();long nextOwner=unit.rts().ownershipRevision,nextCommand=unit.rts().commandRevision;
        UUID expectedNextUuid=next.getUUID();int expectedNextId=next.getId();
        boolean committed=false;BUSY.add(o);STAGING.put(o.target,next);
        try {
            if(!level.addFreshEntity(next)||!next.isAddedToWorld()||next.isRemoved()||!next.isAlive())return "spawn_failed";
            if(!valid(o)||!funds(o)||!population(o,next)||!safe(o,next)||!o.worker.hasLineOfSight(o.target)
                ||!o.target.position().equals(position)||Float.compare(o.target.getHealth(),hp)!=0||Float.compare(o.target.getMaxHealth(),max)!=0
                ||!next.getUUID().equals(expectedNextUuid)||next.getId()!=expectedNextId||!ContractLedger.get(level.getServer()).canCommit(o.origin,expectedNextUuid)
                ||level.getEntity(next.getId())!=next||level.getEntity(next.getUUID())!=next||!next.isNoAi()||!pending(next)
                ||!unit.getOwnerName().equals(o.stamp.owner())||unit.rts().ownershipRevision!=nextOwner||unit.rts().commandRevision!=nextCommand
                ||!next.position().equals(position)||Float.compare(next.getHealth(),nextHp)!=0||Float.compare(next.getMaxHealth(),nextMax)!=0
                ||!RtsKillValueClaims.isFree(next)||!next.getPersistentData().getCompound(ORIGIN).equals(provenance)
                ||UnitServerEvents.getAllUnits().stream().filter(e->e==next).count()!=1)return "interrupted";
            if(!ResourceTransaction.exchange(level.getServer(),o.account,o.epoch,o.account.revision,o.kind.price(),ResourceTransaction.Cost.ZERO).applied())return "insufficient";
            ContractLedger.get(level.getServer()).commit(o.origin,next.getUUID(),o.stamp.owner(),o.epoch);
            committed=true;next.getPersistentData().remove(PENDING);next.setNoAi(false);
            // Commit is visible before synchronous leave callbacks. Never rewrite the new body afterwards.
            o.target.discard();
            return "success";
        } finally {STAGING.remove(o.target);BUSY.remove(o);if(!committed)UnitServerEvents.rollbackUncommittedProductionSpawn(next);}
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END)return;
        for(var entry:List.copyOf(DEATHS.entrySet())){var body=entry.getKey();DEATHS.remove(body);if(!entry.getValue().isCanceled()&&!body.isAlive()&&body.level() instanceof ServerLevel level)ContractLedger.get(level.getServer()).died(body.getUUID());}
        for(Order o:List.copyOf(ORDERS.values())){
            if(BUSY.contains(o))continue;
            if(!valid(o)){finish(o,"interrupted",true);continue;}
            long now=o.worker.level().getGameTime();
            if(o.channelStart<0){
                if(o.worker.distanceToSqr(o.target)<=9&&o.worker.hasLineOfSight(o.target)){
                    o.worker.getMoveGoal().stopMoving();o.worker.setHoldPosition(true);o.channelStart=now;o.workerPosition=o.worker.position();o.targetPosition=o.target.position();
                    ContractPackets.send(o.actor,o.connection,o.worker,o.view,"channeling",0);
                } else if(now-o.started>=APPROACH_TICKS||!o.moveTarget.equals(o.worker.getMoveGoal().getMoveTarget()))finish(o,"interrupted",true);
            } else {
                if(!o.worker.getHoldPosition()||o.worker.position().distanceToSqr(o.workerPosition)>.25||o.target.position().distanceToSqr(o.targetPosition)>9
                    ||o.worker.distanceToSqr(o.target)>9||!o.worker.hasLineOfSight(o.target)){finish(o,"interrupted",true);continue;}
                if(now-o.channelStart>=CHANNEL_TICKS)finish(o,complete(o),true);
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(LivingAttackEvent event){if(!event.getEntity().level().isClientSide()&&(pending(event.getEntity())||pending(event.getSource().getEntity())))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event){if(event.getEntity().level().isClientSide()||event.isCanceled()||event.getAmount()<=0)return;
        DAMAGED.put(event.getEntity(),event.getEntity().level().getGameTime());Order claim=CLAIMS.get(event.getEntity());if(claim!=null)claim.invalid=true;
        if(event.getEntity() instanceof FireflyWorker worker){Order o=ORDERS.get(worker);if(o!=null)o.invalid=true;}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void join(EntityJoinLevelEvent event){if(event.getLevel().isClientSide())return;
        Entity e=event.getEntity();if(pending(e)&&STAGING.values().stream().noneMatch(x->x==e)){event.setCanceled(true);return;}
        if(nativeKind(e)!=null&&ContractLedger.get(event.getLevel().getServer()).consumed(e.getUUID()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event){if(!event.isCanceled()&&event.getEntity().level() instanceof ServerLevel level&&!pending(event.getEntity()))DEATHS.put(event.getEntity(),event);}
    @SubscribeEvent public static void economics(RtsKillValueEligibilityEvent event){if(pending(event.victim())||pending(event.killer()))event.setCanceled(true);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){ORDERS.clear();CLAIMS.clear();STAGING.clear();BUSY.clear();DAMAGED.clear();DEATHS.clear();}
}
