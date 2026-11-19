// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.unit.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import net.pasterdream.entity.ShadowGolemEntity;
import net.pasterdream.init.PasterdreamModEntities;
import net.pasterdream.procedures.ShadowGolemPr0Procedure;
import net.pasterdream.procedures.ShadowGolemPr1Procedure;
import java.util.*;
@GameTestHolder(DreamseekersMod.MOD_ID) @PrefixGameTestTemplate(false)
public final class ShadowGolemNativeGameTests {
    static final String BATCH="dreamseekers_golem_native";
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel l){
        // Stop observations before fixture disposal: cleanup is not a native removal cause.
        for(var trace:SHADOW_GOLEM_ARROW_DIAGNOSTICS)trace.close();
        SHADOW_GOLEM_ARROW_DIAGNOSTICS.clear();
        ShadowGolemFixture.cleanup(l);
    }
    @AfterBatch(batch="dreamseekers_golem_peaceful") public static void cleanupPeaceful(ServerLevel l){cleanup(l);}
    @GameTest(template="golem_unit_arena",batch=BATCH,timeoutTicks=180)
    public static void realWildFortyFourTickAreaHitsCowButNeverControlledCombatWorkerOrNoncombat(GameTestHelper h){
        var f=new ShadowGolemFixture(h);var wild=wild(f,16,16);var cow=cow(f,12,14);var activeCow=cow(f,12,16);activeCow.setNoAi(false);activeCow.goalSelector.removeAllGoals(g->true);activeCow.targetSelector.removeAllGoals(g->true);var golem=f.unit("AreaTargets",20,14);golem.setNoAi(true);
        var fox=f.fox("AreaTargets",12,18);fox.setNoAi(true);var worker=DreamseekersMod.FIREFLY_WORKER.get().create(h.getLevel());worker.setOwnerName(golem.getOwnerName());worker.setNoAi(true);f.place(worker,20,18);
        var witness=player(f,"WildTrigger",16,20);double[] baselineCow={cow.getY(),activeCow.getY()},peak={cow.getY(),activeCow.getY()},maxVelocity={0,0};int[] startTick={0};Vec3[] baseline=new Vec3[3];float[] hp=new float[3];var protectedActors=List.of(golem,fox,worker);
        var t=new ShadowGolemFixture.Timeline(h);t.at(0,()->{
            h.assertTrue(h.getLevel().getEntitiesOfClass(Player.class,AABB.ofSize(wild.position(),10,10,10)).contains(witness),"native exact trigger box contains real indexed Player; FakePlayer only triggers, never health positive");
            for(int i=0;i<3;i++){var e=protectedActors.get(i);e.setDeltaMovement(Vec3.ZERO);baseline[i]=e.position();hp[i]=e.getHealth();}
            startTick[0]=wild.tickCount;activeCow.getNavigation().stop();h.assertTrue(!activeCow.isNoAi()&&!activeCow.isNoGravity()&&activeCow.goalSelector.getAvailableGoals().isEmpty()&&activeCow.targetSelector.getAvailableGoals().isEmpty(),"normal physical Cow, only autonomous goals removed");
            wild.getPersistentData().putDouble("time",200);ShadowGolemPr0Procedure.execute(h.getLevel(),wild.getX(),wild.getY(),wild.getZ(),wild);
            h.assertTrue(wild.getPersistentData().getDouble("time")==0,"actual original native branch scheduled1/8/44tick callbacks");
        });
        for(int tick=1;tick<=65;tick++)t.at(tick,()->{
            peak[0]=Math.max(peak[0],cow.getY());peak[1]=Math.max(peak[1],activeCow.getY());maxVelocity[0]=Math.max(maxVelocity[0],cow.getDeltaMovement().y);maxVelocity[1]=Math.max(maxVelocity[1],activeCow.getDeltaMovement().y);
            for(int i=0;i<3;i++){var e=protectedActors.get(i);h.assertTrue(e.getHealth()==hp[i]&&e.position().distanceToSqr(baseline[i])<.02&&Math.abs(e.getDeltaMovement().y)<.1,"same wild impact box cannot damage or forcibly lift opted-in combat/worker/noncombat target");}
        });
        t.at(44,()->System.out.println("GOLEM_WILD_AT44 actorTicks="+(wild.tickCount-startTick[0])+" noAIHP="+cow.getHealth()+" activeHP="+activeCow.getHealth()+" activeY="+activeCow.getY()));
        t.at(66,()->{System.out.println("GOLEM_WILD_AOE_DIAGNOSTIC noAIHP="+cow.getHealth()+" activeHP="+activeCow.getHealth()+" peak="+java.util.Arrays.toString(peak)+" maxVelocity="+java.util.Arrays.toString(maxVelocity)+" sourceTime="+wild.getPersistentData().getDouble("time")+" sourcePos="+wild.position());h.assertTrue(cow.getHealth()<100&&peak[0]==baselineCow[0]&&maxVelocity[0]>1,"original NoAI Cow accepts damage and velocity but does not integrate physical travel");h.assertTrue(activeCow.getHealth()<100&&peak[1]>baselineCow[1]+.5&&maxVelocity[1]>1,"normal Cow actually damaged and physically knocked upward by real delayed impact");System.out.println("GOLEM_WILD_DELAYED_POSITIVE_RTS_TARGET_FILTER_PASS cowHP="+cow.getHealth()+" peakY="+peak[0]);});
        t.start(wild,()->f.ready(wild,cow,activeCow,golem,fox,worker)&&publishedPlayer(h,witness));
    }
    @GameTest(template="golem_unit_arena",batch=BATCH,timeoutTicks=160)
    public static void paidAndBlankSourceNeverChargeScheduleOrHitEvenWithForgedReadyTimer(GameTestHelper h){
        var f=new ShadowGolemFixture(h);var paid=f.unit("NoNative",12,12);paid.setNoAi(true);var cow=cow(f,16,12);var witness=player(f,"PaidTrigger",12,16);Vec3[] start={null};
        var t=new ShadowGolemFixture.Timeline(h);t.at(0,()->{
            h.assertTrue(h.getLevel().getEntitiesOfClass(Player.class,AABB.ofSize(paid.position(),10,10,10)).contains(witness),"paid case meets native trigger precondition too");
            paid.getPersistentData().putDouble("time",200);ShadowGolemPr0Procedure.execute(h.getLevel(),paid.getX(),paid.getY(),paid.getZ(),paid);paid.setOwnerName("");ShadowGolemPr0Procedure.execute(h.getLevel(),paid.getX(),paid.getY(),paid.getZ(),paid);
            ShadowGolemPr1Procedure.execute(paid);paid.invulnerableTime=0;h.assertTrue(!paid.hurt(paid.damageSources().fall(),2),"native FALL still rejected");h.assertTrue(paid.getPersistentData().getDouble("time")==200,"source type cancels even pre-rejected-damage charge and blank owner");start[0]=cow.position();
        });
        for(int i=1;i<=70;i++)t.at(i,()->h.assertTrue(cow.getHealth()==100&&cow.position().distanceToSqr(start[0])<.02&&paid.getPersistentData().getDouble("time")==200,"actual natural baseTicks never schedule AoE/callback or increment forged timer"));
        t.start(paid,()->f.ready(paid,cow)&&publishedPlayer(h,witness));
    }
    @GameTest(template="golem_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void exactSourceCallbacksRemainInertForRemovedAndRestoredPaidInstances(GameTestHelper h){
        var f=new ShadowGolemFixture(h);var paid=f.unit("Callback",12,12);paid.setNoAi(true);var cow=cow(f,16,12);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ready(paid,cow),"callback fixtures published")).thenExecute(()->{
            CompoundTag tag=new CompoundTag();paid.saveWithoutId(tag);var restored=DreamseekersMod.SHADOW_GOLEM_UNIT.get().create(h.getLevel());restored.load(tag);paid.discard();float hp=cow.getHealth();Vec3 motion=cow.getDeltaMovement();
            for(var source:List.of(paid,restored))try{
                source.setAnimation("empty");var animation=ShadowGolemPr0Procedure.class.getDeclaredMethod("lambda$execute$2",Entity.class);animation.setAccessible(true);animation.invoke(null,source);h.assertTrue(source.getSyncedAnimation().equals("empty"),"exact stale callback cannot overwrite paid synced animation");
                var impact=ShadowGolemPr0Procedure.class.getDeclaredMethod("lambda$execute$5",net.minecraft.world.level.LevelAccessor.class,double.class,double.class,double.class,Entity.class);impact.setAccessible(true);impact.invoke(null,h.getLevel(),paid.getX(),paid.getY(),paid.getZ(),source);
            }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
            h.assertTrue(cow.getHealth()==hp&&cow.getDeltaMovement().equals(motion),"direct stale callback cannot damage or overwrite velocity; these are callback guards, not natural scheduling claim");
        }).thenSucceed();
    }
    @GameTest(template="golem_unit_arena",batch=BATCH,timeoutTicks=140)
    public static void onlyNewArrowDamageIsNormalizedOtherNativeExclusionsAndWildChargeRemain(GameTestHelper h){
        var f=new ShadowGolemFixture(h);var paid=f.unit("Damage",6,8);paid.setNoAi(true);var wild=wild(f,25,8);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ready(paid,wild),"actual damage fixtures")).thenExecute(()->{
            paid.getPersistentData().putDouble("time",13);wild.getPersistentData().putDouble("time",0);
            var arrow=new net.minecraft.world.entity.projectile.Arrow(h.getLevel(),0,0,0);float php=paid.getHealth(),whp=wild.getHealth();paid.invulnerableTime=0;wild.invulnerableTime=0;
            h.assertTrue(paid.hurt(paid.damageSources().arrow(arrow,null),3)&&paid.getHealth()<php,"new heavy RTS unit takes ordinary Arrow damage");
            h.assertTrue(!wild.hurt(wild.damageSources().arrow(arrow,null),3)&&wild.getHealth()==whp&&wild.getPersistentData().getDouble("time")==10,"wild arrow immunity plus pre-accept charge remain original");
            var potion=new net.minecraft.world.entity.projectile.ThrownPotion(h.getLevel(),0,0,0);var cloud=new AreaEffectCloud(h.getLevel(),0,0,0);
            for(var e:List.of(paid,wild)){
                h.assertTrue(!e.fireImmune()&&e.getMobType()==MobType.UNDEFINED,"no invented type fire/undead immunity");
                for(var ds:List.of(e.damageSources().fall(),e.damageSources().cactus(),e.damageSources().indirectMagic(potion,null),e.damageSources().indirectMagic(cloud,null))){float hp=e.getHealth();e.invulnerableTime=0;h.assertTrue(!e.hurt(ds,1)&&e.getHealth()==hp,"other native direct-source and environmental exclusions retained");}
                for(var ds:List.of(e.damageSources().inFire(),e.damageSources().drown(),e.damageSources().magic(),e.damageSources().generic())){float hp=e.getHealth();e.invulnerableTime=0;h.assertTrue(e.hurt(ds,1)&&e.getHealth()<hp,"ordinary fire/drown/magic/generic still hurts");}
                h.assertTrue(e.addEffect(new MobEffectInstance(MobEffects.POISON,40)),"direct potion damage exclusion is not status immunity");e.removeAllEffects();
            }
            h.assertTrue(paid.getPersistentData().getDouble("time")==13,"all accepted/rejected hurt paths preserve paid timer");
        }).thenSucceed();
    }
    @GameTest(template="golem_unit_arena",batch=BATCH,timeoutTicks=150)
    public static void realDeathDropsOnlyOriginalLootAndExperience(GameTestHelper h){
        var f=new ShadowGolemFixture(h);var paid=f.unit("Loot",6,8);paid.setNoAi(true);var wild=wild(f,25,8);var killer=CrazyTerrorbeakNativeGameTests.player(h,"GolemKiller");
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ready(paid,wild),"actual death actors")).thenExecute(()->{for(var e:List.of(paid,wild)){e.setHealth(1);e.invulnerableTime=0;h.assertTrue(e.hurt(e.damageSources().playerAttack(killer),1000),"real lethal attributed damage");}}).thenIdle(35).thenExecute(()->{
            h.assertTrue(paid.isRemoved()&&wild.isRemoved(),"actual20tick death removal");h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class,wild.getBoundingBox().inflate(4)).isEmpty(),"original loot table really drops");h.assertTrue(!h.getLevel().getEntitiesOfClass(ExperienceOrb.class,wild.getBoundingBox().inflate(4)).isEmpty(),"real original player-attributed XP positive");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,paid.getBoundingBox().inflate(4)).isEmpty()&&h.getLevel().getEntitiesOfClass(ExperienceOrb.class,paid.getBoundingBox().inflate(4)).isEmpty(),"RTS no loot/XP");
        }).thenSucceed();
    }
    @GameTest(template="golem_unit_arena",batch="dreamseekers_golem_peaceful",timeoutTicks=120)
    public static void nbtPeacefulAndGoalsKeepActualGroundIdentityWithoutNativeTimer(GameTestHelper h){
        var f=new ShadowGolemFixture(h);var paid=f.unit("Nbt",6,8);var wild=wild(f,25,8);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ready(paid,wild),"identity fixtures")).thenExecute(()->{
            paid.setScenarioRoleIndex(79);CompoundTag tag=new CompoundTag();paid.saveWithoutId(tag);tag.putBoolean("NoAI",true);var copy=DreamseekersMod.SHADOW_GOLEM_UNIT.get().create(h.getLevel());copy.load(tag);copy.getPersistentData().putDouble("time",200);ShadowGolemPr0Procedure.execute(h.getLevel(),copy.getX(),copy.getY(),copy.getZ(),copy);
            h.assertTrue(copy.getUUID().equals(paid.getUUID())&&copy.getOwnerName().equals(paid.getOwnerName())&&copy.getScenarioRoleIndex()==79&&!copy.isNoAi()&&!copy.isNoGravity()&&copy.getPersistentData().getDouble("time")==200,"unpublished normal load fixes controller and cannot activate native timer");
            h.assertTrue(Math.abs(paid.getBbWidth()-2.2)<.001&&Math.abs(paid.getBbHeight()-3.5)<.001&&!paid.isFlyingUnit()&&paid.getAttackGoal() instanceof TeamSafeMeleeAttackGoal,"exact body and actual team safe ground melee");
            h.assertTrue(paid.targetSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal||g.getGoal() instanceof net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal),"no native Player/revenge target goals");
            var old=h.getLevel().getDifficulty();try{h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);paid.checkDespawn();wild.checkDespawn();h.assertTrue(paid.isAlive()&&!paid.isRemoved()&&wild.isRemoved(),"paid persists, original peaceful despawn untouched");}finally{h.getLevel().getServer().setDifficulty(old,true);}
        }).thenSucceed();
    }
    @GameTest(template="golem_unit_arena",batch=BATCH,timeoutTicks=130)
    public static void portalsAndForcedVehiclesCannotTransportPaidUnit(GameTestHelper h){var f=new ShadowGolemFixture(h);var paid=f.unit("Portal",5,8);var boat=f.place(new net.minecraft.world.entity.vehicle.Boat(h.getLevel(),0,0,0),22,22);h.startSequence().thenWaitUntil(()->h.assertTrue(f.ready(paid,boat),"portal callback fixtures")).thenExecute(()->{h.assertTrue(!paid.startRiding(boat)&&!paid.startRiding(boat,true)&&!paid.isPassenger(),"normal and forced/NBT mounting rejected");h.assertTrue(!paid.canChangeDimensions()&&paid.isOnPortalCooldown()&&!net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(paid),"actual gateway predicate and dimension hook reject");paid.handleInsidePortal(paid.blockPosition());}).thenIdle(35).thenExecute(()->{CompoundTag tag=new CompoundTag();paid.saveWithoutId(tag);h.assertTrue(tag.getInt("PortalCooldown")>=0&&paid.level()==h.getLevel()&&!paid.isPassenger(),"natural cooldown does not become negative");}).thenSucceed();}
    @GameTest(template="golem_unit_arena",batch=BATCH,timeoutTicks=150)
    public static void actualTravelingArrowsDamageOnlyNormalizedRtsVariant(GameTestHelper h){
        var f=new ShadowGolemFixture(h);var paid=f.unit("ArrowFlight",16,8);paid.setNoAi(true);var wild=wild(f,16,23);
        var roofWitness=new ShadowGolemArrowRoofWitness(f,paid,wild);
        var trace=new ShadowGolemArrowReadinessDiagnostic(h.getLevel(),paid,wild);
        SHADOW_GOLEM_ARROW_DIAGNOSTICS.add(trace);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(trace);
        trace.snapshot("initial-after-original-add",null);
        var arrows=new java.util.ArrayList<net.minecraft.world.entity.projectile.Arrow>();var starts=new java.util.ArrayList<Vec3>();float[] hp={0,0};double[] traveled={0,0};
        roofWitness.check("initial",arrows);
        var t=new ShadowGolemFixture.Timeline(h);t.at(0,()->{
            roofWitness.check("flight-tick-0",arrows);
            int i=0;for(var target:List.of(paid,wild)){hp[i++]=target.getHealth();var arrow=new net.minecraft.world.entity.projectile.Arrow(h.getLevel(),target.getX()-7,target.getY()+1.5,target.getZ());arrow.setNoGravity(true);arrow.setBaseDamage(3);arrow.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;arrow.setDeltaMovement(1,0,0);arrows.add(arrow);starts.add(arrow.position());ShadowGolemFixture.ENTITIES.add(arrow);h.assertTrue(h.getLevel().addFreshEntity(arrow),"real vanilla Arrow added, no manual hit invocation");}
        });
        for(int tick=1;tick<=45;tick++)t.at(tick,()->{roofWitness.check("flight-observation",arrows);for(int i=0;i<2;i++)traveled[i]=Math.max(traveled[i],arrows.get(i).position().distanceTo(starts.get(i)));});
        t.at(46,()->{roofWitness.check("flight-tick-46",arrows);h.assertTrue(traveled[0]>4&&traveled[1]>4&&paid.getHealth()<hp[0]&&wild.getHealth()==hp[1],"actual arrows cross space and collide: only new subtype loses health; original arrow immunity intact");System.out.println("GOLEM_PHYSICAL_ARROW_NORMALIZATION_PASS paid="+paid.getHealth()+" wild="+wild.getHealth()+" traveled="+java.util.Arrays.toString(traveled));});t.start(paid,()->{
            // Evaluate the unchanged original predicate exactly once; diagnostics do not decide admission.
            boolean ready=f.ready(paid,wild);
            roofWitness.check("original-ready-evaluated",arrows);
            trace.snapshot("original-ready-evaluated",ready);
            return ready;
        });
    }
    /** One real indexed Player above the existing roof; original wild despawn behavior is untouched. */
    private static final class ShadowGolemArrowRoofWitness {
        private final ShadowGolemFixture f;
        private final Mob paid,wild;
        private final FakePlayer witness;
        private final Vec3 expectedPosition;
        private final float initialHealth;
        private String firstViolation;
        ShadowGolemArrowRoofWitness(ShadowGolemFixture f,Mob paid,Mob wild){
            this.f=f;this.paid=paid;this.wild=wild;
            witness=new FakePlayer(f.h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),ShadowGolemFixture.PREFIX+"ArrowRoofWitness"));
            witness.connection=new CrazyTerrorbeakNativeGameTests.RecordingHandler(witness);
            var p=f.pos(16,13,16);expectedPosition=new Vec3(p.getX()+.5,p.getY(),p.getZ()+.5);
            witness.moveTo(expectedPosition.x,expectedPosition.y,expectedPosition.z);
            initialHealth=witness.getHealth();
            ShadowGolemFixture.ENTITIES.add(witness);
            f.h.getLevel().addNewPlayer(witness);
        }
        private void require(boolean condition,String detail){if(!condition&&firstViolation==null)firstViolation=detail;}
        void check(String stage,List<net.minecraft.world.entity.projectile.Arrow> arrows){
            var level=f.h.getLevel();var box=witness.getBoundingBox();var roof=f.pos(16,12,16);
            require(publishedPlayer(f.h,witness)&&level.players().contains(witness)&&witness.level()==level&&!witness.isRemoved()&&witness.isAlive()&&!witness.isSpectator(),"actual indexed roof witness identity");
            require(witness.position().equals(expectedPosition)&&witness.getHealth()==initialHealth,"roof witness remains at original setup position with unchanged health (not damage immunity evidence)");
            require(level.getBlockState(roof).is(net.minecraft.world.level.block.Blocks.STONE)&&level.getBlockState(roof).isFaceSturdy(level,roof,net.minecraft.core.Direction.UP)&&box.minY==roof.getY()+1&&level.noCollision(witness),"existing solid roof supports collision-free witness; no terrain edits");
            for(var body:List.of(paid,wild)){
                var nearest=level.getNearestPlayer(body,-1.0);
                require(nearest!=null&&body.distanceToSqr(nearest)<32*32&&body.distanceToSqr(witness)<32*32,"original comparison bodies have actual nearby native nearest player");
                require(body.isNoAi()&&body.getTarget()!=witness&&!body.getBoundingBox().intersects(box),"roof witness is not an original comparison combat target/collider");
                // Conservative entire original horizontal arrow corridor; no fixture arrow parameters change.
                var lane=new AABB(body.getX()-8,body.getY()+1,body.getZ()-1,body.getX()+32,body.getY()+2,body.getZ()+1);
                require(!lane.intersects(box),"roof witness outside original horizontal arrow corridor");
            }
            for(var arrow:arrows)require(!arrow.getBoundingBox().expandTowards(arrow.getDeltaMovement()).inflate(.3).intersects(box),"roof witness outside actual arrow swept box");
            for(var entity:ShadowGolemFixture.ENTITIES){
                if(!(entity instanceof ShadowGolemEntity body)||body.isRemoved()||body.level()!=level)continue;
                var trigger=AABB.ofSize(body.position(),10,10,10);
                var impactCenter=body.position().add(0,-3,0);var impact=new AABB(impactCenter,impactCenter).inflate(5);
                require(!trigger.intersects(box)&&!impact.intersects(box)&&!level.getEntitiesOfClass(Player.class,trigger).contains(witness),"roof witness outside every live native source trigger/impact box");
                require(body.getTarget()!=witness,"roof witness not targeted by any live native or controlled test body");
                if(body.getType()!=PasterdreamModEntities.SHADOW_GOLEM.get())continue;
                require(body.isNoAi(),"original exact native wild retains its existing NoAI");
                if(body==wild)continue;
                // Native EntityGetter(Entity,-1) delegates to NO_SPECTATORS; use its same iteration/distance implementation.
                var withWitness=level.getNearestPlayer(body,-1.0);
                var withoutWitness=level.getNearestPlayer(body.getX(),body.getY(),body.getZ(),-1.0,e->EntitySelector.NO_SPECTATORS.test(e)&&e!=witness);
                double withDistance=withWitness==null?Double.NaN:body.distanceToSqr(withWitness),withoutDistance=withoutWitness==null?Double.NaN:body.distanceToSqr(withoutWitness);
                int far=body.getType().getCategory().getDespawnDistance(),near=body.getType().getCategory().getNoDespawnDistance();
                boolean forcedWith=withWitness!=null&&withDistance>(double)far*far,forcedWithout=withoutWitness!=null&&withoutDistance>(double)far*far;
                boolean resetWith=withWitness!=null&&withDistance<(double)near*near,resetWithout=withoutWitness!=null&&withoutDistance<(double)near*near;
                require(body.getNoActionTime()<=600&&(withWitness==null)==(withoutWitness==null)&&forcedWith==forcedWithout&&resetWith==resetWithout,"roof witness must not change neighboring wild despawn/reset threshold classification");
                System.out.println("GOLEM_ARROW_ROOF_NEIGHBOR stage="+stage+" gameTime="+level.getGameTime()+" uuid="+body.getUUID()+" pos="+body.position()+" noAI="+body.isNoAi()+" noActionTime="+body.getNoActionTime()+" withoutWitness="+(withoutWitness==null?"none":withoutWitness.getUUID())+" withoutDistanceSqr="+withoutDistance+" withWitness="+(withWitness==null?"none":withWitness.getUUID())+" withDistanceSqr="+withDistance+" forcedWithout="+forcedWithout+" forcedWith="+forcedWith+" resetWithout="+resetWithout+" resetWith="+resetWith);
            }
            System.out.println("GOLEM_ARROW_ROOF_WITNESS stage="+stage+" gameTime="+level.getGameTime()+" uuid="+witness.getUUID()+" pos="+witness.position()+" paidDistanceSqr="+paid.distanceToSqr(witness)+" wildDistanceSqr="+wild.distanceToSqr(witness)+" hp="+witness.getHealth()+" firstViolation="+firstViolation);
            f.h.assertTrue(firstViolation==null,"roof witness noninterference: "+firstViolation);
        }
    }
    private static final List<ShadowGolemArrowReadinessDiagnostic> SHADOW_GOLEM_ARROW_DIAGNOSTICS=new ArrayList<>();
    /** Read-only observation of this one original test's exact two bodies, not a fixture remedy. */
    public static final class ShadowGolemArrowReadinessDiagnostic {
        private final ServerLevel level;
        private final Mob paid,wild;
        private boolean closed;
        ShadowGolemArrowReadinessDiagnostic(ServerLevel level,Mob paid,Mob wild){this.level=level;this.paid=paid;this.wild=wild;}
        void snapshot(String stage,Boolean originalReady){
            shadowGolemArrowBodySnapshot(stage,"paid",paid,originalReady);
            shadowGolemArrowBodySnapshot(stage,"wild",wild,originalReady);
        }
        private void shadowGolemArrowBodySnapshot(String stage,String role,Mob body,Boolean originalReady){
            var cp=body.chunkPosition();var byId=level.getEntity(body.getId());var byUuid=level.getEntity(body.getUUID());
            var nearest=level.getNearestPlayer(body,-1.0);double distanceSqr=nearest==null?Double.NaN:body.distanceToSqr(nearest);
            int despawnDistance=body.getType().getCategory().getDespawnDistance();
            String player=nearest==null?"none":("id="+nearest.getId()+",uuid="+nearest.getUUID()+",name="+nearest.getGameProfile().getName()+",pos="+nearest.position()+",alive="+nearest.isAlive()+",spectator="+nearest.isSpectator()+",removed="+nearest.isRemoved());
            System.out.println("GOLEM_ARROW_READINESS_OBSERVATION stage="+stage+" role="+role+" gameTime="+level.getGameTime()+" originalReady="+originalReady
                +" type="+net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(body.getType())+" id="+body.getId()+" uuid="+body.getUUID()+" ticks="+body.tickCount+" tickGt2="+(body.tickCount>2)
                +" sameId="+(byId==body)+" sameUuid="+(byUuid==body)+" lookupId="+byId+" lookupUuid="+byUuid+" added="+body.isAddedToWorld()+" removed="+body.isRemoved()+" removalReason="+body.getRemovalReason()+" alive="+body.isAlive()+" hp="+body.getHealth()
                +" pos="+body.position()+" motion="+body.getDeltaMovement()+" difficulty="+level.getDifficulty()+" noAI="+body.isNoAi()+" noGravity="+body.isNoGravity()+" persistenceRequired="+body.isPersistenceRequired()+" customPersistence="+body.requiresCustomPersistence()
                +" chunk="+cp+" chunkNow="+(level.getChunkSource().getChunkNow(cp.x,cp.z)!=null)+" entitiesLoaded="+level.areEntitiesLoaded(cp.toLong())+" entityTicking="+level.isPositionEntityTicking(body.blockPosition())
                +" nearestPlayer={"+player+"} nearestDistanceSqr="+distanceSqr+" category="+body.getType().getCategory()+" despawnDistanceSqr="+((long)despawnDistance*despawnDistance));
        }
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void shadowGolemArrowExactBodyLeaveDiagnostic(net.minecraftforge.event.entity.EntityLeaveLevelEvent event){
            if(closed||event.getLevel()!=level||(event.getEntity()!=paid&&event.getEntity()!=wild))return;
            snapshot("exact-body-leave-event",null);
            System.out.println("GOLEM_ARROW_EXACT_BODY_LEAVE_STACK role="+(event.getEntity()==paid?"paid":"wild")+" gameTime="+level.getGameTime()+" uuid="+event.getEntity().getUUID()+" removalReason="+event.getEntity().getRemovalReason());
            new Exception("observational-only exact arrow fixture leave call stack").printStackTrace(System.out);
        }
        void close(){if(!closed){closed=true;net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);}}
    }
    static ShadowGolemEntity wild(ShadowGolemFixture f,int x,int z){var e=PasterdreamModEntities.SHADOW_GOLEM.get().create(f.h.getLevel());e.setNoAi(true);return f.place(e,x,z);}
    static net.minecraft.world.entity.animal.Cow cow(ShadowGolemFixture f,int x,int z){var e=EntityType.COW.create(f.h.getLevel());e.setNoAi(true);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);e.setHealth(100);return f.place(e,x,z);}
    static FakePlayer player(ShadowGolemFixture f,String name,int x,int z){var p=new FakePlayer(f.h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),ShadowGolemFixture.PREFIX+name));p.connection=new CrazyTerrorbeakNativeGameTests.RecordingHandler(p);var pos=f.pos(x,1,z);p.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);ShadowGolemFixture.ENTITIES.add(p);f.h.getLevel().addNewPlayer(p);return p;}
    static boolean publishedPlayer(GameTestHelper h,Player p){return h.getLevel().getEntity(p.getId())==p&&h.getLevel().getEntity(p.getUUID())==p;}
}
