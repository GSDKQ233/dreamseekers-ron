// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.spore.*;
import com.dreamseekers.stone.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.util.function.Consumer;
import static com.dreamseekers.DreamseekersMod.*;
import static com.dreamseekers.skill.UnitSkill.*;

/** Passive proofs use real Forge entities, one RTS order and natural server ticks.
 * Event probes observe/reject a real publication or hurt; they never synthesize a positive trigger. */
@GameTestHolder("dreamseekers_passive_triggers")
@PrefixGameTestTemplate(false)
public final class PassiveTriggerGameTests {
    private static final String ARENA="unit_isolation_arena", BATCH="dreamseekers_passive_triggers";
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) { AutocastGameTests.cleanup(level); }

    private static int cd(SkillUnit s, UnitSkill k){return s.rts().skills().cooldownTicks(k);}
    private static long rev(SkillUnit s){return s.rts().skills().revision();}
    private static SkillResult switchOff(AutocastGameTests.F f, SkillUnit s, UnitSkill k) {
        return new SkillNetwork.Toggle(k,s.mob().getId(),s.mob().getUUID(),rev(s),false).execute(f.peer.actor);
    }
    private static void close(GameTestHelper h,double actual,double expected,String why) {
        h.assertTrue(Math.abs(actual-expected)<.002,why+" actual="+actual+" expected="+expected);
    }
    private static JellyfishUnit victim(AutocastGameTests.F f,int x,int z) {
        var e=f.durable(f.spawn(JELLYFISH_UNIT.get(),f.foe,x,z));
        ShadowGolemStrikeSkillGameTests.neutral(e);
        return e;
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=160)
    public static void seedRequiresCombatAndRealCleansableDebuffAndNeverReachesInvisibleAlly(GameTestHelper h) {
        var f=new AutocastGameTests.F(h,"Seed");
        var fox=f.spawn(GOLDEN_FOX_UNIT.get(),f.own,7,10);
        var patient=f.spawn(BONE_WING_UNIT.get(),f.ally,10,10);
        var hidden=f.spawn(BONE_WING_UNIT.get(),f.ally,10,10);
        JellyfishUnit[] enemy={null}; // During the negative phase no hostile unit exists in this arena.
        patient.noPhysics=true;hidden.noPhysics=true;f.stationary(patient);f.stationary(hidden);
        var removals=new ArrayList<Long>();
        Consumer<MobEffectEvent.Remove> removal=e->{if(e.getEntity()==patient&&e.getEffect()==MobEffects.MOVEMENT_SLOWDOWN)removals.add(f.level.getGameTime());};
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,removal);f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(removal));
        f.ready().thenExecute(()->{
            fox.setNoAi(false);hidden.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,600));
            patient.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400));
            hidden.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400));
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(cd(fox,FOX_DREAM_SEED)==0&&SporeLease.activeFor(fox)==null&&patient.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            &&patient.getTarget()==null&&patient.getLastHurtByMob()==null,
                    "actual debuff but no hostile, focus or aggressor: cooldown="+cd(fox,FOX_DREAM_SEED)+" debuff="+patient.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                    +" focus="+patient.getTarget()+" lastAttacker="+patient.getLastHurtByMob()+" carrier="+SporeLease.activeFor(fox));
            enemy[0]=victim(f,21,20); // Publish far away, outside either side's ordinary attack reach.
        }).thenWaitUntil(()->h.assertTrue(enemy[0].tickCount>3&&f.level.getEntity(enemy[0].getUUID())==enemy[0],"live, separately published opposing unit"))
          .thenExecute(()->{
            h.assertTrue(cd(fox,FOX_DREAM_SEED)==0,"distant opponent outside observed fight is still insufficient");
            f.place(enemy[0],13,10);f.focus(patient,enemy[0]);f.focus(hidden,enemy[0]);
        }).thenWaitUntil(()->h.assertTrue(cd(fox,FOX_DREAM_SEED)>0&&SporeLease.activeFor(fox)!=null,"combat plus debuff publishes an actual live spore"))
          .thenExecute(()->{
              var spore=SporeLease.activeFor(fox);
              h.assertTrue(!patient.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&removals.size()==1&&SporeEffects.cleanseProtectionTicks(patient)>0
                      &&SporeEffects.cleanseProtectionTicks(patient)==SporeLease.CLEANSE_PROTECTION_TICKS-(f.level.getGameTime()-removals.get(0))
                      &&spore.distanceToSqr(patient)<=9,"actual nearby patient was cleansed and protected: debuff="+patient.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)+" protection="+SporeEffects.cleanseProtectionTicks(patient)+" d2="+spore.distanceToSqr(patient)+" visiblePatient="+CoordinatedMark.visible(f.peer.actor,patient)+" visibleSpore="+CoordinatedMark.visible(f.peer.actor,spore)+" LOS="+spore.hasLineOfSight(patient)+" hiddenD2="+spore.distanceToSqr(hidden));
              h.assertTrue(hidden.isInvisible()&&spore.distanceToSqr(hidden)<=9&&hidden.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                      &&SporeEffects.cleanseProtectionTicks(hidden)==0,"co-located invisible ally is not a hidden splash recipient");
              hidden.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,100));
              h.assertTrue(hidden.getEffect(MobEffects.WEAKNESS).getDuration()==100,"hidden recipient's new debuff isn't shortened by automatic aura");
              f.done();
          });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=140)
    public static void seedRevokedInNativePublicationLeavesNoCarrierCleanseOrSpentCooldown(GameTestHelper h) {
        var f=new AutocastGameTests.F(h,"SeedOff");var fox=f.spawn(GOLDEN_FOX_UNIT.get(),f.own,8,8);
        var patient=f.spawn(BONE_WING_UNIT.get(),f.own,10,8);var enemy=victim(f,13,8);
        List<JungleSporeCarrier> attempts=new ArrayList<>();
        Consumer<EntityJoinLevelEvent> probe=e->{
            if(e.getEntity() instanceof JungleSporeCarrier p&&p.level()==f.level&&fox.distanceToSqr(p)<100) {
                attempts.add(p);h.assertTrue(switchOff(f,fox,FOX_DREAM_SEED)==SkillResult.SUCCESS,"owner's real off at Join");
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,probe);
        f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(probe));
        f.ready().thenExecute(()->{
            patient.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,500));
            f.focus(patient,enemy);fox.setNoAi(false);
        }).thenWaitUntil(()->h.assertTrue(!attempts.isEmpty(),"auto Seed attempted real entity publication"))
          .thenIdle(22).thenExecute(()->{
              h.assertTrue(attempts.size()==1&&attempts.get(0).isRemoved()&&SporeLease.activeFor(fox)==null
                      &&cd(fox,FOX_DREAM_SEED)==0&&patient.hasEffect(MobEffects.WEAKNESS)
                      &&SporeEffects.cleanseProtectionTicks(patient)==0,"revoked publication didn't pay, cleanse, leave a lease, or replay");
              f.done();
          });
    }

    private static void watchShots(AutocastGameTests.F f, StoneSpiritThrowerUnit shooter,List<Projectile> launches,List<Long> times) {
        Consumer<EntityJoinLevelEvent> probe=e->{
            if(e.getEntity() instanceof Projectile p&&p.getOwner()==shooter&&(p instanceof StoneProjectile||p instanceof HeavyStoneProjectile)) {
                launches.add(p);times.add(f.level.getGameTime());
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,probe);
        f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(probe));
    }
    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=260)
    public static void heavyReplacesExactlyOneNativeReleaseAndKeepsOrdinaryCadence(GameTestHelper h) {
        var f=new AutocastGameTests.F(h,"Shot");var stone=f.spawn(STONE_SPIRIT_THROWER_UNIT.get(),f.own,5,8);
        var ctrl=f.spawn(STONE_SPIRIT_THROWER_UNIT.get(),f.own,5,17);
        var target=victim(f,12,8);var neighbor=victim(f,12,9);var far=victim(f,12,11);
        var ctlTarget=victim(f,12,17);
        var main=new ArrayList<Projectile>();var control=new ArrayList<Projectile>();
        var mt=new ArrayList<Long>();var ct=new ArrayList<Long>();watchShots(f,stone,main,mt);watchShots(f,ctrl,control,ct);
        long[] generation={0},revision={0},expires={0};float[] ad={0};
        f.ready().thenExecute(()->{
            h.assertTrue(!ctrl.rts().skills().autocastEnabled(STONE_HEAVY_SHOT)||switchOff(f,ctrl,STONE_HEAVY_SHOT)==SkillResult.SUCCESS,"matched ordinary-shot control is off");
            f.focus(stone,target);f.focus(ctrl,ctlTarget);generation[0]=stone.rts().commandRevision;revision[0]=rev(stone);ad[0]=stone.getUnitAttackDamage();
            stone.setNoAi(false);ctrl.setNoAi(false);
        }).thenWaitUntil(()->h.assertTrue(target.getHealth()<800&&ctlTarget.getHealth()<800,"paired native releases arrive"))
          .thenExecute(()->{
              h.assertTrue(main.size()==1&&main.get(0) instanceof HeavyStoneProjectile&&main.get(0).isRemoved()
                      &&control.size()==1&&control.get(0) instanceof StoneProjectile,"heavy replaces, never supplements, the first ordinary release: "+main+" / "+control);
              close(h,800-target.getHealth(),1.5*ad[0],"one physical heavy primary");close(h,800-neighbor.getHealth(),.30*ad[0],"one real collision splash");
              close(h,800-far.getHealth(),0,"no far splash");close(h,800-ctlTarget.getHealth(),ad[0],"unchanged ordinary control hit");
              h.assertTrue(rev(stone)==revision[0]+1&&cd(ctrl,STONE_HEAVY_SHOT)==0&&stone.rts().commandRevision==generation[0]&&stone.getTarget()==target,
                      "ability spends once without replacing ordinary command");
              expires[0]=f.level.getGameTime()+cd(stone,STONE_HEAVY_SHOT);
              h.assertTrue(expires[0]==mt.get(0)+STONE_HEAVY_SHOT.cooldownTicks(),"single skill cooldown begins on accepted physical launch");
          }).thenWaitUntil(()->h.assertTrue(main.size()>=2&&control.size()>=2,"ordinary cadence continues without another command"))
          .thenExecute(()->{
              h.assertTrue(main.size()==2&&main.get(1) instanceof StoneProjectile&&control.size()==2&&control.get(1) instanceof StoneProjectile
                      &&Math.abs((mt.get(1)-mt.get(0))-(ct.get(1)-ct.get(0)))<=1,"next release matches ordinary control cadence: "+mt+" / "+ct);
              h.assertTrue(rev(stone)==revision[0]+1&&f.level.getGameTime()+cd(stone,STONE_HEAVY_SHOT)==expires[0],"no second cooldown");
              f.done();
          });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=250)
    public static void rejectedHeavyPrimaryCannotEmitSplash(GameTestHelper h) {
        var f=new AutocastGameTests.F(h,"ShotNo");var stone=f.spawn(STONE_SPIRIT_THROWER_UNIT.get(),f.own,5,8);
        var target=victim(f,12,8);var neighbor=victim(f,12,9);var shots=new ArrayList<Projectile>();watchShots(f,stone,shots,new ArrayList<>());
        int[] rejected={0};
        Consumer<LivingDamageEvent> cancel=e->{
            if(e.getEntity()==target&&e.getSource().getDirectEntity() instanceof HeavyStoneProjectile p&&p.getOwner()==stone) {
                rejected[0]++;e.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,cancel);f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(cancel));
        f.ready().thenExecute(()->{f.focus(stone,target);stone.setNoAi(false);})
          .thenWaitUntil(()->h.assertTrue(rejected[0]>0&&shots.size()==1&&shots.get(0).isRemoved(),"first natural heavy reached rejected final damage"))
          .thenExecute(()->{
              h.assertTrue(rejected[0]==1&&target.getHealth()==800&&neighbor.getHealth()==800&&cd(stone,STONE_HEAVY_SHOT)>0,
                    "a launched shot used its cooldown, but canceled HP write never authorized splash");
              f.done();
          });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=240)
    public static void golemCleaveOnlyAfterActualOrdinaryMeleeDoesNotRehitPrimaryOrRecurse(GameTestHelper h) {
        var f=new AutocastGameTests.F(h,"Cleave");var golem=f.spawn(SHADOW_GOLEM_UNIT.get(),f.own,7,9);
        var primary=victim(f,9,9);var n1=victim(f,9,10);var n2=victim(f,9,8);var n3=victim(f,10,10);var far=victim(f,14,9);
        for(var e:List.of(primary,n1,n2,n3,far)){e.noPhysics=true;f.stationary(e);}
        List<LivingEntity> nativeHits=new ArrayList<>(),cleaveHits=new ArrayList<>();
        boolean[] reject={true};int[] denied={0};long[] command={0},revision={0};float[] ad={0};
        Consumer<LivingDamageEvent> probe=e->{
            if(e.getSource().getEntity()!=golem)return;
            if(ShadowGolemStrike.isStrike(e.getSource()))cleaveHits.add(e.getEntity());
            else if(e.getEntity()==primary&&reject[0]){denied[0]++;e.setCanceled(true);}
            else nativeHits.add(e.getEntity());
        };MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,probe);f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(probe));
        f.ready().thenExecute(()->{
            f.focus(golem,primary);command[0]=golem.rts().commandRevision;revision[0]=rev(golem);ad[0]=golem.getUnitAttackDamage();golem.setNoAi(false);
        }).thenWaitUntil(()->h.assertTrue(denied[0]>0,"actual primary melee made a damage attempt"))
          .thenExecute(()->{
              h.assertTrue(primary.getHealth()==800&&cleaveHits.isEmpty()&&cd(golem,SHADOW_GOLEM_STRIKE)==0&&rev(golem)==revision[0],
                      "rejected primary never triggers ability or spends");reject[0]=false;
          }).thenWaitUntil(()->h.assertTrue(primary.getHealth()<800&&cd(golem,SHADOW_GOLEM_STRIKE)>0,
                      "real ordinary direct melee lands and owns a cleave; hp="+primary.getHealth()+" hits="+nativeHits.size()+"/"+cleaveHits.size()))
          .thenExecute(()->{
              close(h,800-primary.getHealth(),ad[0],"original victim took unchanged single melee");
              var damaged=List.of(n1,n2,n3).stream().filter(t->t.getHealth()<800).toList();
              h.assertTrue(nativeHits.equals(List.of(primary))&&damaged.size()==2&&cleaveHits.size()==2&&new HashSet<>(cleaveHits).equals(new HashSet<>(damaged))
                      &&!cleaveHits.contains(primary)&&far.getHealth()==800,"exactly two separate physical targets, no primary replay or chained beyond cap");
              for(var t:damaged)close(h,800-t.getHealth(),ad[0]*1.5,"fixed one cleave per nearby enemy");
              h.assertTrue(rev(golem)==revision[0]+1&&golem.rts().commandRevision==command[0]&&golem.getTarget()==primary&&!ShadowGolemStrike.active(golem),
                      "exactly one proc, no manual windup or reset of same ATTACK");
              f.done();
          });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=220)
    public static void windActualHitRalliesNearbyAllyWithoutAutomaticDash(GameTestHelper h) {
        var f=new AutocastGameTests.F(h,"Rally");var knight=f.spawn(WIND_KNIGHT_UNIT.get(),f.own,7,9);
        var target=victim(f,8,9);var ally=f.spawn(BONE_WING_UNIT.get(),f.ally,7,11);var far=f.spawn(BONE_WING_UNIT.get(),f.own,7,16);
        target.noPhysics=true;
        boolean[] block={true};int[] attempts={0};long[] cmd={0},revision={0};Vec3[] contact={null};
        Consumer<LivingDamageEvent> probe=e->{if(e.getEntity()==target&&e.getSource().getEntity()==knight){
            attempts[0]++;if(block[0])e.setCanceled(true);else contact[0]=knight.position();}};
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,probe);f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(probe));
        f.ready().thenExecute(()->{
            f.focus(knight,target);cmd[0]=knight.rts().commandRevision;revision[0]=rev(knight);knight.setNoAi(false);
        }).thenWaitUntil(()->h.assertTrue(attempts[0]>0,"canceled genuine melee physically attempted; focus="+(knight.getTarget()==target)+" source="+knight.position()+" victim="+target.position()+" LOS="+knight.hasLineOfSight(target)+" HP="+target.getHealth()))
          .thenExecute(()->{
              h.assertTrue(target.getHealth()==800&&cd(knight,WIND_DASH)==0&&!WindDash.isActive(knight)&&SkillEffects.bonus(ally,BuffKind.ATTACK_DAMAGE)==0,
                      "attempt or focus alone does not rally or dash");block[0]=false;
          }).thenWaitUntil(()->h.assertTrue(target.getHealth()<800&&cd(knight,WIND_DASH)>0,"actual confirmed ordinary hit procs"))
          .thenExecute(()->{
              close(h,800-target.getHealth(),10,"no bonus hit on ordinary primary");close(h,SkillEffects.bonus(ally,BuffKind.MOVEMENT_SPEED),.20,"near allied movement");
              close(h,SkillEffects.bonus(ally,BuffKind.ATTACK_DAMAGE),.10,"near allied attack");close(h,SkillEffects.bonus(far,BuffKind.ATTACK_DAMAGE),0,"no out-of-range command");
              h.assertTrue(rev(knight)==revision[0]+1&&knight.rts().commandRevision==cmd[0]&&knight.getTarget()==target&&!WindDash.isActive(knight)
                      &&contact[0]!=null&&contact[0].distanceToSqr(knight.position())<.04,"one local rally, no forced relocation, extra charge or reset");
              f.done();
          });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=520)
    public static void loadedFireflyBoostsOnlyAnExistingReturnAndDepositsExactlyOnce(GameTestHelper h) {
        var f=new AutocastGameTests.F(h,"Return");var w=f.spawn(FIREFLY_WORKER.get(),f.own,3,17);
        var anchorPos=h.absolutePos(new BlockPos(16,1,10));var building=DREAM_ANCHOR;
        var blocks=BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(f.level),f.level,anchorPos,Rotation.NONE);
        for(var block:blocks)f.level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        var anchor=BuildingUtils.getNewBuildingPlacement(building,f.level,anchorPos,Rotation.NONE,f.own,false);
        BuildingServerEvents.getBuildings().add(anchor);anchor.onBuilt();anchor.initPlacedBlocks();f.cleanups.add(()->BuildingServerEvents.getBuildings().remove(anchor));
        int[] value={0};Vec3[] start={null};long[] revision={0};ItemEntity[] token={null};
        var log=h.absolutePos(new BlockPos(8,2,17));f.level.setBlock(log,Blocks.OAK_LOG.defaultBlockState(),3);
        f.ready().thenExecute(()->{
            w.setNoAi(false);revision[0]=rev(w);start[0]=w.position();h.assertTrue(anchor.isBuilt&&anchor.getBuilding().canAcceptResources,"real owned stockpile ready");
            // Empty and idle: no home route or free cooldown.
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(cd(w,FIREFLY_RUSH)==0&&!w.getReturnResourcesGoal().hasReturnOrder()&&w.getNavigation().isDone(),"idle passive cannot create a return");
            // A single real harvest order; no synthetic return command, bank credit, route, or direct invocation of work.
            new UnitActionItem(f.own,UnitAction.MOVE,-1,new int[]{w.getId()},log,BlockPos.ZERO).action(f.level);
        }).thenWaitUntil(()->h.assertTrue(log.equals(w.getGatherResourceGoal().getGatherTarget())&&w.getReturnResourcesGoal().getBuildingTarget()==null,
                    "authenticated gather already selected an actual resource before receiving more cargo"))
          .thenExecute(()->{
              var cargo=new ItemStack(Items.OAK_LOG,4);value[0]=Resources.getTotalResourcesFromItems(List.of(cargo)).wood;
              h.assertTrue(value[0]>=50&&value[0]<=w.getMaxResources(),"four real priced logs cross the native threshold without overflowing capacity");
              var drop=new ItemEntity(f.level,w.getX()+.12,w.getY()+.05,w.getZ(),cargo);drop.setNoPickUpDelay();drop.setNoGravity(true);drop.setDeltaMovement(Vec3.ZERO);
              h.assertTrue(f.level.addFreshEntity(drop),"pickup is a published priced item");token[0]=drop;f.cleanups.add(()->{if(!drop.isRemoved())drop.discard();});
          }).thenWaitUntil(()->h.assertTrue(cd(w,FIREFLY_RUSH)>0&&SkillEffects.bonus(w,BuffKind.MOVEMENT_SPEED)>0,
                "existing loaded, outbound-to-stockpile flight gained speed; value="+value[0]+" held="+w.getItems()+" path="+w.getNavigation().getPath()+" depot="+w.getReturnResourcesGoal().getBuildingTarget()+" drop="+token[0].isRemoved()))
          .thenExecute(()->{
              h.assertTrue(w.getReturnResourcesGoal().getBuildingTarget()==anchor&&!w.getNavigation().isDone()&&rev(w)==revision[0]+1
                      &&token[0].isRemoved()&&Unit.atThresholdResources(w)&&!FireflyRush.hasPausedWork(w)&&Resources.getTotalResourcesFromItems(w.getItems()).wood==value[0],
                      "same active destination and unpaused work; no free cargo created");
              close(h,SkillEffects.bonus(w,BuffKind.MOVEMENT_SPEED),.35,"native flight buff active during return");
          }).thenWaitUntil(()->h.assertTrue(w.getItems().isEmpty()&&f.account.wood==543+value[0],
                  "worker flew to actual stockpile and transferred exactly the fixture cargo; bank="+f.account.wood+" carried="+w.getItems().size()))
          .thenIdle(21).thenExecute(()->{
              h.assertTrue(w.position().distanceToSqr(start[0])>4&&SkillEffects.bonus(w,BuffKind.MOVEMENT_SPEED)==0&&cd(w,FIREFLY_RUSH)>0
                      &&f.account.wood==543+value[0]&&rev(w)==revision[0]+1&&f.account.food==432&&f.account.ore==654&&f.account.emerald==123,
                      "no post-delivery replay or duplicate, no work pause");
              f.close();h.succeed();
          });
    }
}
