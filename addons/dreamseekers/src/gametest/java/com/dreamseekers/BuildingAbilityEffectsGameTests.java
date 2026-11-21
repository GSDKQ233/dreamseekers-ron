// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.*;
import com.dreamseekers.resonance.ResonanceResearch;
import com.dreamseekers.skill.*;
import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** Actual service payment, real attribute/final-damage consumers and Forge healing callbacks.
 * Advanced tree/research fixtures establish existing prerequisites; these are not upgrade-payment tests.
 */
@GameTestHolder("dreamseekers_building_abilities") @PrefixGameTestTemplate(false)
public final class BuildingAbilityEffectsGameTests {
    private static final String BATCH="building_ability_effects";
    private static final List<ResonanceNetworkGameTests.F> FIXTURES=new ArrayList<>();
    private static final List<Object> PROBES=new ArrayList<>();
    private static final List<PassiveIncomePeer> RECONNECTED=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel ignored){
        for(Object probe:List.copyOf(PROBES))MinecraftForge.EVENT_BUS.unregister(probe);PROBES.clear();
        for(var peer:List.copyOf(RECONNECTED))peer.close();RECONNECTED.clear();
        for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();
    }
    private static ResonanceNetworkGameTests.F fixture(GameTestHelper h,String label,int index){
        var f=new ResonanceNetworkGameTests.F(h,"Ability_"+label,index);FIXTURES.add(f);return f;
    }
    private static void near(GameTestHelper h,double actual,double expected,String reason){
        h.assertTrue(Math.abs(actual-expected)<.0002,reason+" actual="+actual+" expected="+expected);
    }
    private static String cast(ResonanceNetworkGameTests.F f,BuildingPlacement host){
        var v=BuildingAbilityService.view(f.peer.actor,host);return v==null?"missing_view":BuildingAbilityService.cast(f.peer.actor,host,v.instance(),v.capture(),v.generation(),v.revision());
    }
    private static <T extends Mob> T publish(ResonanceNetworkGameTests.F f,BuildingPlacement host,T body,int x,int z,String owner){
        ((Unit)body).setOwnerName(owner);body.setNoAi(true);BlockPos p=host.originPos.offset(x,1,z);f.load(p);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            f.level.setBlock(p.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<5;y++)f.level.setBlock(p.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);
        }
        body.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);f.h.assertTrue(f.level.addFreshEntity(body),"real body publication");f.units.add(body);return body;
    }
    private static DreamAnchorPlacement completedWind(ResonanceNetworkGameTests.F f){
        var root=(DreamAnchorPlacement)f.root;
        // Explicit known completed-state fixture with the real registered WIND_V4 geometry.
        // Do not claim that this tests its original 2400-tick paid upgrade (covered by the old suite).
        for(var old:root.getBlocks())f.level.setBlock(old.getBlockPos(),Blocks.AIR.defaultBlockState(),2);
        var blocks=root.blocks(WorldTreeModel.WIND_V4);for(var block:blocks)f.level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        root.tree().model=WorldTreeModel.WIND_V4;root.tree().phase=WorldTreeState.Phase.COMPLETE;root.tree().ascended=false;
        root.tree().geometryHighest=WorldTreeModel.WIND_V4.count;root.commitGeometry(blocks,WorldTreeModel.WIND_V4.count,0);
        f.h.assertTrue(root.productionAvailable()&&root.getBlocksPlaced()==WorldTreeModel.WIND_V4.count,"genuine complete advanced geometry fixture");return root;
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=240)
    public static void windRootPaysOnceUsesCategoryMaxAndCannotReviveAfterLeavingRange(GameTestHelper h){
        var f=fixture(h,"Root",100);var root=completedWind(f);var target=f.unit(root,-3,-3);
        var donor=publish(f,root,DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(f.level),-6,-3,f.owner);
        var cohort=new ArrayList<LivingEntity>();cohort.add(target);cohort.add(donor);
        for(int i=0;i<10;i++)cohort.add(f.unit(root,-5+(i%5)*2,-6-(i/5)*2));
        double[] base={0};long[] ready={0};
        h.startSequence().thenWaitUntil(()->{for(var unit:cohort)f.ready(unit);}).thenExecute(()->{
            base[0]=target.getAttributeValue(Attributes.MOVEMENT_SPEED);int shadow=f.shadow();
            h.assertTrue(cast(f,root).equals("success")&&f.shadow()==shadow-20,"real completed WIND (not shadow-only) source pays exactly20S");
            near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0]*1.15,"same-tick actual movement consumer receives15percent");
            h.assertTrue(cohort.stream().filter(u->SkillEffects.hasNamedBuff(u,BuildingAbilityEffects.ROOT_KEY,BuffKind.MOVEMENT_SPEED)).count()==10,"exact ten recipient cap, not the full twelve-body cohort");
            h.assertTrue(SkillEffects.addBuff((SkillUnit)donor,target,"api53_max_probe",BuffKind.MOVEMENT_SPEED,.25,20),"existing25percent category contribution admitted");
            near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0]*1.25,"same category max25, not multiplied or added with15");
            ready[0]=root.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.ROOT_CROWN);
            h.assertTrue(cast(f,root).equals("cooldown")&&f.shadow()==shadow-20,"repeat action cannot repay or extend");
        }).thenExecuteAfter(25,()->{
            near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0]*1.15,"weaker still-live root contribution resumes when stronger expires");
            var before=target.position();target.moveTo(root.originPos.getX()-20,root.originPos.getY()+1,root.originPos.getZ()-3);
            near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0],"actual getter retires out-of-circle effect before a tick");
            target.moveTo(before.x,before.y,before.z);
            near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0],"returning cannot revive revoked paid lease");
            h.assertTrue(root.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.ROOT_CROWN)==ready[0],"range cancellation retains paid cooldown");f.close();h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=430)
    public static void gardenActualSixRecipientsSixPulsesNeverRefillsOrAffectsSeventh(GameTestHelper h){
        var f=fixture(h,"GardenBudget",101);var garden=f.install(DreamseekersMod.REVERIE_GARDEN,f.origin.offset(64,0,0));
        var units=new ArrayList<LivingEntity>();for(int i=0;i<7;i++){
            var u=f.unit(garden,-4+i,-3-(i%2)*2);u.setHealth(u.getMaxHealth()-8);units.add(u);
        }
        var initial=new IdentityHashMap<LivingEntity,Float>();long[] ready={0};
        h.startSequence().thenWaitUntil(()->{for(var u:units){f.ready(u);h.assertTrue(u.tickCount>205,"natural200tick rest");}}).thenExecute(()->{
            units.forEach(u->initial.put(u,u.getHealth()));int food=f.account.food;
            h.assertTrue(cast(f,garden).equals("success")&&f.account.food==food-30,"real garden fixed30F debit");
            h.assertTrue(units.stream().filter(BuildingAbilityEffects::hasEffects).count()==6,"exactly six recipients, never seventh");
            ready[0]=garden.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.GARDEN_RECOVERY);
        }).thenExecuteAfter(125,()->{
            long healed=units.stream().filter(u->Math.abs(u.getHealth()-initial.get(u)-6)<.0001).count();
            double sum=units.stream().mapToDouble(u->u.getHealth()-initial.get(u)).sum();
            near(h,sum,36,"actual max36HP across original six-target cohort");h.assertTrue(healed==6,"six actual oneHP pulses each, not fractional/catch-up or replacement recipients");
            h.assertTrue(units.stream().noneMatch(BuildingAbilityEffects::hasEffects)&&BuildingAbilityEffects.remaining(garden)==0,"transient heal leases naturally retire");
            h.assertTrue(garden.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.GARDEN_RECOVERY)==ready[0]&&cast(f,garden).equals("cooldown"),"budget completion never refunds or refreshes CD");f.close();h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=430)
    public static void gardenRealHealCancellationAndReentrantCleanupCannotOverhealOrRefund(GameTestHelper h){
        var f=fixture(h,"GardenHooks",102);var garden=f.install(DreamseekersMod.REVERIE_GARDEN,f.origin.offset(64,0,0));
        var canceled=f.unit(garden,-3,-3);var cleared=f.unit(garden,1,-3);
        var departed=f.unit(garden,-4,-5);var injured=f.unit(garden,2,-5);
        for(var unit:List.of(canceled,cleared,departed,injured))unit.setHealth(unit.getMaxHealth()-8);
        int[] count={0,0};float[] hp={0,0,0,0};long[] ready={0};
        Consumer<LivingHealEvent> probe=event->{
            if(event.getEntity()==canceled){if(++count[0]==1)event.setCanceled(true);else event.setAmount(999);}
            if(event.getEntity()==cleared){count[1]++;long revision=f.account.revision;int food=f.account.food;
                SkillEffects.clear(cleared);event.setAmount(999);
                h.assertTrue(cast(f,garden).equals("cooldown")&&f.account.revision==revision&&f.account.food==food,"real callback cleanup/reentrant action cannot repay, refund or recast committed cooldown");
            }
        };
        PROBES.add(probe);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,probe);
        h.startSequence().thenWaitUntil(()->{for(var unit:List.of(canceled,cleared,departed,injured)){f.ready(unit);h.assertTrue(unit.tickCount>205,"natural full rest");}}).thenExecute(()->{
            hp[0]=canceled.getHealth();hp[1]=cleared.getHealth();h.assertTrue(cast(f,garden).equals("success"),"actual paid effect before Forge callbacks");
            ready[0]=garden.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.GARDEN_RECOVERY);
            hp[2]=departed.getHealth();var previous=departed.position();departed.moveTo(garden.originPos.getX()-20,garden.originPos.getY()+1,garden.originPos.getZ()-3);
            h.assertTrue(!BuildingAbilityEffects.hasEffects(departed),"leaving radius retires garden source immediately");departed.moveTo(previous.x,previous.y,previous.z);
            injured.invulnerableTime=0;h.assertTrue(injured.hurt(injured.damageSources().generic(),1),"real final damage interrupts resting recipient");hp[3]=injured.getHealth();
        }).thenExecuteAfter(125,()->{
            h.assertTrue(count[0]==6&&count[1]==1,"one canceled attempt is consumed, terminal cleanup has no later attempts");
            near(h,canceled.getHealth(),hp[0]+5,"inflated hook amount still caps each accepted pulse to oneHP");near(h,cleared.getHealth(),hp[1],"cleanup during callback prevents stale post-event write");
            near(h,departed.getHealth(),hp[2],"leaving and returning never resumes garden pulses");near(h,injured.getHealth(),hp[3],"actual received damage terminates garden recovery");
            h.assertTrue(garden.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.GARDEN_RECOVERY)==ready[0],"callback cancellation retains paid cooldown");
            MinecraftForge.EVENT_BUS.unregister(probe);PROBES.remove(probe);f.close();h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=240)
    public static void hallRequiresActualLabRejectsWorkersAndUsesExistingAttackDamageMaximum(GameTestHelper h){
        var f=fixture(h,"Hall",103);var hall=f.install(DreamseekersMod.DREAM_HALL,f.origin.offset(64,0,0));var worker=f.unit(hall,-3,-3);
        var attacker=publish(f,hall,DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(f.level),-5,-3,f.owner);
        double[] base={0},raw={0};BuildingPlacement[] lab={null};long[] ready={0};
        h.startSequence().thenWaitUntil(()->{f.ready(worker);f.ready(attacker);}).thenExecute(()->{
            int shadow=f.shadow();h.assertTrue(cast(f,hall).equals("requires_laboratory")&&f.shadow()==shadow,"actual missing laboratory denies before fee");
            lab[0]=f.install(DreamseekersMod.RESONANCE_LABORATORY,f.origin.offset(96,0,0));
        }).thenWaitUntil(()->h.assertTrue(lab[0].isBuilt,"real laboratory completes")).thenExecute(()->{
            var previous=attacker.position();attacker.moveTo(hall.originPos.getX()-20,hall.originPos.getY()+1,hall.originPos.getZ()-3);
            int shadow=f.shadow();h.assertTrue(cast(f,hall).equals("no_target")&&f.shadow()==shadow,"only worker near hall never consumes quota/fee for nonexistent attack preparation");attacker.moveTo(previous.x,previous.y,previous.z);
            base[0]=attacker.getUnitAttackDamage();raw[0]=attacker.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get());h.assertTrue(cast(f,hall).equals("success")&&f.shadow()==shadow-25,"real trained roster attack type pays exactly25S");
            near(h,attacker.getUnitAttackDamage(),base[0]*1.10,"actual attack-damage getter gains10percent (also sampled by AD-based skills)");
            near(h,attacker.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get()),raw[0]*1.10,"raw attribute-value consumer receives exactly10percent");
            near(h,SkillEffects.bonus(worker,BuffKind.ATTACK_DAMAGE),0,"worker never gains hall attack modifier");
            h.assertTrue(SkillEffects.addBuff((SkillUnit)attacker,attacker,"api53_ad_max",BuffKind.ATTACK_DAMAGE,.9,20),"generic existing provider stays capped");
            near(h,attacker.getUnitAttackDamage(),base[0]*1.20,"existing20percent AD cap/max, not20+10");
            near(h,attacker.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get()),raw[0]*1.20,"raw attribute chain shares existing20percent maximum");
            ready[0]=hall.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.HALL_PREPARATION);
        }).thenExecuteAfter(25,()->{
            near(h,attacker.getUnitAttackDamage(),base[0]*1.10,"weaker hall contribution resumes after stronger provider expires");
            lab[0].onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(lab[0]);
            near(h,attacker.getUnitAttackDamage(),base[0],"same-tick laboratory loss prunes hall contribution before actual attack sampling");
            near(h,attacker.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get()),raw[0],"same-tick prerequisite loss clears raw attribute-value consumer too");
            h.assertTrue(hall.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.HALL_PREPARATION)==ready[0],"prerequisite cancellation no CD refund");f.close();h.succeed();
        });
    }

    /** Both probes are real Forge events. The first consumer runs inside a living entity tick;
     * lastEnd proves the same world's END-phase sweep has not happened for that gameTime yet.
     * Do not advance gameTime or manually invoke the effects ticker to manufacture this boundary.
     */
    private static void exactExpiryProbe(GameTestHelper h,ResonanceNetworkGameTests.F f,LivingEntity trigger,long[] deadline,
            boolean[] witnessed,Runnable before,Runnable expired){
        long[] lastEnd={Long.MIN_VALUE};
        Consumer<TickEvent.LevelTickEvent> end=event->{if(event.level==f.level&&event.phase==TickEvent.Phase.END)lastEnd[0]=f.level.getGameTime();};
        Consumer<LivingEvent.LivingTickEvent> living=event->{
            if(event.getEntity()!=trigger||deadline[0]<0)return;long now=f.level.getGameTime();
            if(now==deadline[0]-1&&!witnessed[0]){h.assertTrue(lastEnd[0]<now,"positive boundary read precedes same-level END sweep");before.run();witnessed[0]=true;}
            if(now==deadline[0]&&!witnessed[1]){h.assertTrue(lastEnd[0]<now,"exact expiry read precedes same-level END sweep");expired.run();witnessed[1]=true;}
        };
        PROBES.add(end);PROBES.add(living);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,end);MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,living);
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=230)
    public static void rootExact160DeadlineIsConsumedInsideLivingTickBeforeLevelEnd(GameTestHelper h){
        var f=fixture(h,"RootExpiry",105);var root=completedWind(f);var a=f.unit(root,-3,-3);var b=f.unit(root,-6,-3);
        double[] base={0,0};long[] deadline={-1};boolean[] witnessed={false,false};
        exactExpiryProbe(h,f,a,deadline,witnessed,()->{
            near(h,a.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0]*1.15,"root still active at natural159 via attribute-value");
            near(h,b.getAttribute(Attributes.MOVEMENT_SPEED).getValue(),base[1]*1.15,"root still active at natural159 via attribute-instance");
        },()->{
            // Separate bodies make either path independently responsible for its own removal.
            near(h,a.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0],"natural160 attribute-value read cannot consume expired15percent");
            near(h,b.getAttribute(Attributes.MOVEMENT_SPEED).getValue(),base[1],"natural160 attribute-instance read cannot consume expired15percent");
        });
        h.startSequence().thenWaitUntil(()->{f.ready(a);f.ready(b);}).thenExecute(()->{
            base[0]=a.getAttributeValue(Attributes.MOVEMENT_SPEED);base[1]=b.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
            h.assertTrue(cast(f,root).equals("success"),"real paid root plan before natural expiry");deadline[0]=f.level.getGameTime()+160;
        }).thenWaitUntil(()->h.assertTrue(witnessed[0]&&witnessed[1],"actual159 and160 before-END living-tick samples both reached"))
          .thenExecute(()->{h.assertTrue(root.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.ROOT_CROWN)==deadline[0]+740,"expiry retains original900tick cooldown");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=270)
    public static void hallExact200DeadlineInvalidatesBothIndependentAttackConsumersBeforeLevelEnd(GameTestHelper h){
        var f=fixture(h,"HallExpiry",106);var hall=f.install(DreamseekersMod.DREAM_HALL,f.origin.offset(64,0,0));
        var lab=f.install(DreamseekersMod.RESONANCE_LABORATORY,f.origin.offset(96,0,0));
        var a=publish(f,hall,DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(f.level),-3,-3,f.owner);
        var b=publish(f,hall,DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(f.level),-6,-3,f.owner);
        double[] derived={0,0},raw={0,0};long[] deadline={-1};boolean[] witnessed={false,false};
        exactExpiryProbe(h,f,a,deadline,witnessed,()->{
            near(h,a.getUnitAttackDamage(),derived[0]*1.10,"hall natural199 actual unit AD remains10percent");
            near(h,b.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get()),raw[1]*1.10,"hall natural199 independent raw attribute remains10percent");
        },()->{
            near(h,a.getUnitAttackDamage(),derived[0],"hall natural200 actual unit AD consumer must clear expired modifier itself");
            near(h,b.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get()),raw[1],"hall natural200 independent raw attribute consumer must clear expired modifier itself");
            near(h,a.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get()),raw[0],"derived-first body raw read agrees");
            near(h,b.getUnitAttackDamage(),derived[1],"raw-first body derived read agrees");
        });
        h.startSequence().thenWaitUntil(()->{f.ready(a);f.ready(b);h.assertTrue(lab.isBuilt,"actual completed laboratory");}).thenExecute(()->{
            derived[0]=a.getUnitAttackDamage();derived[1]=b.getUnitAttackDamage();raw[0]=a.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get());raw[1]=b.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get());
            h.assertTrue(cast(f,hall).equals("success"),"real paid hall plan before natural expiry");deadline[0]=f.level.getGameTime()+200;
        }).thenWaitUntil(()->h.assertTrue(witnessed[0]&&witnessed[1],"actual199 and200 before-END living-tick samples both reached"))
          .thenExecute(()->{h.assertTrue(hall.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.HALL_PREPARATION)==deadline[0]+600,"expiry retains original800tick cooldown");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=130)
    public static void realSourceRemovalAndHallRangeExitInvalidateConsumersInSameCallback(GameTestHelper h){
        var f=fixture(h,"Removal",107);var root=completedWind(f);var rootTarget=f.unit(root,-3,-3);
        var hall=f.install(DreamseekersMod.DREAM_HALL,f.origin.offset(64,0,0));var lab=f.install(DreamseekersMod.RESONANCE_LABORATORY,f.origin.offset(96,0,0));
        var leave=publish(f,hall,DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(f.level),-3,-3,f.owner);
        var remove=publish(f,hall,DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(f.level),-6,-3,f.owner);
        h.startSequence().thenWaitUntil(()->{f.ready(rootTarget);f.ready(leave);f.ready(remove);h.assertTrue(lab.isBuilt,"real laboratory ready");}).thenExecute(()->{
            double movement=rootTarget.getAttributeValue(Attributes.MOVEMENT_SPEED),leaveAd=leave.getUnitAttackDamage(),leaveRaw=leave.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get()),removeAd=remove.getUnitAttackDamage(),removeRaw=remove.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get());
            h.assertTrue(cast(f,root).equals("success")&&cast(f,hall).equals("success"),"two real paid source plans");
            near(h,rootTarget.getAttributeValue(Attributes.MOVEMENT_SPEED),movement*1.15,"root positive control immediately before removal");near(h,remove.getUnitAttackDamage(),removeAd*1.10,"hall positive control immediately before removal");
            long now=f.level.getGameTime();var previous=leave.position();leave.moveTo(hall.originPos.getX()-20,hall.originPos.getY()+1,hall.originPos.getZ()-3);
            near(h,leave.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get()),leaveRaw,"hall range exit raw consumer clears immediately");near(h,leave.getUnitAttackDamage(),leaveAd,"hall range exit derived consumer agrees immediately");leave.moveTo(previous.x,previous.y,previous.z);
            near(h,leave.getUnitAttackDamage(),leaveAd,"hall return cannot revive retired incoming lease");
            root.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(root);
            near(h,rootTarget.getAttributeValue(Attributes.MOVEMENT_SPEED),movement,"root source lifecycle removal clears on same callback's actual read");
            hall.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(hall);
            near(h,remove.getUnitAttackDamage(),removeAd,"hall source lifecycle removal clears on same callback's actual unit AD read");near(h,remove.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get()),removeRaw,"hall source removal clears raw attribute chain too");
            h.assertTrue(f.level.getGameTime()==now,"no END sweep or time advance occurred between removals/range exit and consumers");f.close();h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=140)
    public static void hallActualPaidLeftPressSamplesTenPercentOnceWithoutChangingSkillClockOrMultiplier(GameTestHelper h){
        var f=fixture(h,"PaidAD",108);var hall=f.install(DreamseekersMod.DREAM_HALL,f.origin.offset(64,0,0));var lab=f.install(DreamseekersMod.RESONANCE_LABORATORY,f.origin.offset(96,0,0));
        var boosted=publish(f,hall,DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(f.level),-5,-5,f.owner);
        // The control stands 13 blocks past the hall's far edge: range is measured from the footprint, not the origin corner.
        var control=publish(f,hall,DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(f.level),24,-5,f.owner);
        var a=publish(f,hall,DreamseekersMod.GOLDEN_FOX_UNIT.get().create(f.level),-1,-5,f.owner+"_enemy");
        var b=publish(f,hall,DreamseekersMod.GOLDEN_FOX_UNIT.get().create(f.level),28,-5,f.owner+"_enemy");
        for(var target:List.of(a,b)){neutralDamage(target);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);}
        for(var hand:List.of(boosted,control)){hand.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);hand.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);}
        int[] writes={0,0},cooldownAtRelease={-1,-1};double[] plainAd={0};long[] started={-1},released={-1,-1};
        Consumer<LivingDamageEvent> hits=event->{
            if(!AaroncosCombat.isStrike(event.getSource()))return;
            int lane=event.getEntity()==a&&event.getSource().getEntity()==boosted?0:event.getEntity()==b&&event.getSource().getEntity()==control?1:-1;
            if(lane<0)return;
            writes[lane]++;
            if(released[lane]<0){released[lane]=f.level.getGameTime();cooldownAtRelease[lane]=(lane==0?boosted:control).rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS);}
        };
        PROBES.add(hits);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,hits);
        h.startSequence().thenWaitUntil(()->{for(var unit:List.of(boosted,control,a,b)){f.ready(unit);h.assertTrue(unit.tickCount>=30,"natural entity initialization");}h.assertTrue(lab.isBuilt,"real laboratory");}).thenExecute(()->{
            plainAd[0]=control.getUnitAttackDamage();near(h,boosted.getUnitAttackDamage(),plainAd[0],"matching real left-hand baseline AD");int shadow=f.shadow();
            h.assertTrue(cast(f,hall).equals("success")&&f.shadow()==shadow-25,"actual25S Hall cast includes nearby hand, not distant control");
            near(h,boosted.getUnitAttackDamage(),plainAd[0]*1.10,"nearby hand actual AD samples10percent");near(h,control.getUnitAttackDamage(),plainAd[0],"control outside12 remains unmodified");
            var q1=new SkillRequest(UnitSkill.AARONCOS_LEFT_PRESS,boosted.getId(),boosted.getUUID(),boosted.rts().skills().revision(),a.getId(),a.getUUID());
            var q2=new SkillRequest(UnitSkill.AARONCOS_LEFT_PRESS,control.getId(),control.getUUID(),control.rts().skills().revision(),b.getId(),b.getUUID());
            h.assertTrue(new SkillNetwork.Cast(q1).execute(f.peer.actor)==SkillResult.SUCCESS&&new SkillNetwork.Cast(q2).execute(f.peer.actor)==SkillResult.SUCCESS,"two real authenticated left-press requests");started[0]=f.level.getGameTime();
            h.assertTrue(f.shadow()==shadow-65&&boosted.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==400&&control.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==400,"Hall25 plus two genuine20S skills; original400 cooldown unchanged");
            h.assertTrue(AaroncosCombat.remainingTicks(boosted)==12&&AaroncosCombat.remainingTicks(control)==12,"original12tick windup unchanged");
        }).thenExecuteAfter(11,()->{
              h.assertTrue(f.level.getGameTime()==started[0]+11&&writes[0]==0&&writes[1]==0,"natural first11ticks contain no physical skill writes");
              near(h,a.getHealth(),1000,"boosted paid skill cannot release early");near(h,b.getHealth(),1000,"unboosted paid skill cannot release early");
          })
          // GameTest scheduling may precede entity consumers in a tick. Wait for actual writes,
          // but judge the deadline from the real Forge event, never from this polling statement.
          .thenWaitUntil(()->h.assertTrue(writes[0]>=1&&writes[1]>=1,"both real physical skill writes arrived"))
          .thenExecute(()->{
              h.assertTrue(released[0]==started[0]+12&&released[1]==started[0]+12&&writes[0]==1&&writes[1]==1,"exactly one real physical write per skill recorded at original12tick deadline");
              near(h,1000-b.getHealth(),plainAd[0]*1.75,"original fixed1.75 skill coefficient unchanged");near(h,1000-a.getHealth(),(1000-b.getHealth())*1.10,"actual paid skill HP loss is10percent once, never1.1 squared");
              h.assertTrue(cooldownAtRelease[0]==388&&cooldownAtRelease[1]==388,"actual release event sees the unchanged400tick deadline minus12");
          }).thenExecuteAfter(3,()->{
              h.assertTrue(writes[0]==1&&writes[1]==1&&!AaroncosCombat.active(boosted)&&!AaroncosCombat.active(control),"completed paid skill cannot repeat a later physical write");
              long elapsed=f.level.getGameTime()-started[0];
              h.assertTrue(boosted.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==400-elapsed&&control.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==400-elapsed,"post-observation polling never changes original400tick cooldown");
              MinecraftForge.EVENT_BUS.unregister(hits);PROBES.remove(hits);f.close();h.succeed();
          });
    }


    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=430)
    public static void gardenHalfPointRealInjuryPaysAfterNaturalRestAndNeverOverheals(GameTestHelper h){
        var f=fixture(h,"GardenHalfV12",110);var garden=f.install(DreamseekersMod.REVERIE_GARDEN,f.origin.offset(64,0,0));var target=f.unit(garden,-3,-3);
        int[] pulses={0};double[] requested={0};float[] hurtHp={0};long[] hurtAt={-1},ready={-1};
        Consumer<LivingHealEvent> heal=event->{if(event.getEntity()==target){pulses[0]++;requested[0]+=event.getAmount();h.assertTrue(event.getAmount()>0&&event.getAmount()<=.5f,"fractional actual recovery request never exceeds remaining halfHP");}};
        PROBES.add(heal);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,heal);
        h.startSequence().thenWaitUntil(()->{f.ready(target);h.assertTrue(garden.isBuilt,"real completed garden");}).thenExecute(()->{
            neutralDamage(target);target.setHealth(target.getMaxHealth());target.invulnerableTime=0;
            h.assertTrue(target.hurt(target.damageSources().generic(),.5f),"real native halfHP damage admitted");hurtAt[0]=f.level.getGameTime();hurtHp[0]=target.getHealth();
            near(h,target.getMaxHealth()-hurtHp[0],.5,"actual health deficit is halfHP, not synthetic setHealth injury");
            int food=f.account.food;long revision=f.account.revision;
            h.assertTrue(cast(f,garden).equals("no_target")&&f.account.food==food&&f.account.revision==revision,"recently damaged fractional target still obeys natural rest gate, zero premature fee");
        }).thenExecuteAfter(200,()->{
            h.assertTrue(f.level.getGameTime()==hurtAt[0]+200&&pulses[0]==0,"full natural200tick post-damage rest before any healing");int food=f.account.food;
            h.assertTrue(cast(f,garden).equals("success")&&f.account.food==food-30,"positive fractional deficit is real target and pays exact30F");
            ready[0]=garden.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.GARDEN_RECOVERY);
        }).thenExecuteAfter(125,()->{
            h.assertTrue(pulses[0]==1,"full target produces only one real heal event, never repeated positive pulses");near(h,requested[0],.5,"actual total requested recovery stays at halfHP within the original sixHP per-target budget");
            near(h,target.getHealth()-hurtHp[0],.5,"actual health recovers only missing halfHP");near(h,target.getHealth(),target.getMaxHealth(),"no overheal beyond real maximum");
            h.assertTrue(BuildingAbilityEffects.remaining(garden)==0&&garden.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.GARDEN_RECOVERY)==ready[0]
                &&cast(f,garden).equals("cooldown"),"fractional completion retains original paid cooldown and no effect renewal");
            MinecraftForge.EVENT_BUS.unregister(heal);PROBES.remove(heal);f.close();h.succeed();
        });
    }
    private record AbilityReply(UUID session,UUID epoch,String owner,long request,String status) { }
    private static AbilityReply abilityWire(GameTestHelper h,PassiveIncomePeer peer,BuildingAbilityPackets.Request packet){
        try{
            var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);
            ctor.setAccessible(true);var context=ctor.newInstance(peer.connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);
            var bytes=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{packet.encode(bytes);var decoded=new BuildingAbilityPackets.Request(bytes);h.assertTrue(decoded.equals(packet)&&bytes.readableBytes()==0,"actual lifecycle request exact codec");decoded.handle(()->context);}finally{bytes.release();}
            AbilityReply result=null;
            for(var out:peer.drain())if(out instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket payload&&payload.getIdentifier().equals(DreamseekersMod.id("building_abilities"))){
                var reply=new net.minecraft.network.FriendlyByteBuf(payload.getData().copy());
                try{
                    int discriminator=reply.readVarInt();AbilityReply received;
                    if(discriminator==1){var snapshot=new BuildingAbilityPackets.Snapshot(reply);h.assertTrue(snapshot.valid(),"bounded actual owner view codec");received=new AbilityReply(snapshot.session(),snapshot.epoch(),snapshot.owner(),snapshot.request(),snapshot.status());}
                    else if(discriminator==2){var ack=new BuildingAbilityPackets.Ack(reply);h.assertTrue(ack.valid(),"bounded actual data-free action acknowledgement codec");received=new AbilityReply(ack.session(),ack.epoch(),ack.owner(),ack.request(),ack.status());}
                    else throw new AssertionError("unknown building response discriminator "+discriminator);
                    h.assertTrue(reply.readableBytes()==0&&received.owner().equals(peer.actor.getGameProfile().getName()),"exact owner-private response without trailing data");
                    if(received.request()==packet.sequence())result=received;
                }finally{reply.release();}
            }
            return result;
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    /** v1.1 delta: committed support is independent of online presence, unlike Watch/Codex views.
     * Uses the real logout event bus, exact native PlayerList registration, and real connected
     * embedded channels. This is not a remote-socket test or a claimed cross-dimension migration.
     */
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=250)
    public static void committedRootSurvivesActualLogoutAndReconnectWithoutSessionReplayOrExtendedExpiry(GameTestHelper h){
        var f=fixture(h,"LogoutV11",109);var root=completedWind(f);var target=f.unit(root,-3,-3);
        var rts=com.solegendary.reignofnether.player.PlayerServerEvents.getRTSPlayer(f.owner);var epoch=f.account.accountEpoch;
        double[] base={0};long[] deadline={-1},ready={-1},paidAccountRevision={-1};int[] paidShadow={-1};boolean[] witnessed={false,false};
        BuildingAbilityPackets.Request[] original={null};PassiveIncomePeer[] replacement={null};
        exactExpiryProbe(h,f,target,deadline,witnessed,()->near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0]*1.15,"reconnected support still obeys original natural159 deadline"),
            ()->near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0],"reconnection cannot extend original natural160 deadline before END"));
        h.startSequence().thenWaitUntil(()->f.ready(target)).thenExecute(()->{
            base[0]=target.getAttributeValue(Attributes.MOVEMENT_SPEED);var view=BuildingAbilityService.view(f.peer.actor,root);
            var token=com.solegendary.reignofnether.research.economy.EconomyActionGate.query(f.peer.actor,f.peer.connection);
            h.assertTrue(token!=null&&view.available(),"real registered peer has valid session and paid root target");
            var payload=new BuildingAbilityPackets.Payload(BuildingAbilityPackets.Action.CAST,f.level.dimension().location(),root.originPos,UUID.randomUUID(),view.instance(),view.capture(),view.generation(),view.revision(),0);
            original[0]=new BuildingAbilityPackets.Request(payload,token.epoch(),token.session(),1);int before=f.shadow();
            var response=abilityWire(h,f.peer,original[0]);
            h.assertTrue(response!=null&&response.status().equals("success")&&f.shadow()==before-20,"actual decoded authenticated CAST pays exactly20S");
            deadline[0]=f.level.getGameTime()+160;ready[0]=root.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.ROOT_CROWN);
            paidShadow[0]=f.shadow();paidAccountRevision[0]=f.account.revision;
            near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0]*1.15,"committed root positive control before actual logout event");
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(f.peer.actor));
            f.peer.close(); // Real channel closes and exact native name/UUID PlayerList attachment is removed.
            h.assertTrue(!f.peer.connection.isConnected()&&f.level.getServer().getPlayerList().getPlayerByName(f.owner)==null
                    &&com.solegendary.reignofnether.research.economy.EconomyActionGate.query(f.peer.actor,f.peer.connection)==null,"actual disconnected peer has no current request session");
            h.assertTrue(com.solegendary.reignofnether.player.PlayerServerEvents.getRTSPlayer(f.owner)==rts
                    &&com.solegendary.reignofnether.resources.ExtendedResourcesSync.account(f.owner)==f.account&&epoch.equals(f.account.accountEpoch),"logout retains original RTS identity/account/epoch");
            near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0]*1.15,"logout event and native peer removal do not revoke already-paid support");
        }).thenExecuteAfter(40,()->{
            h.assertTrue(f.level.getServer().getPlayerList().getPlayerByName(f.owner)==null&&BuildingServerEvents.getBuildings().contains(root),"full offline interval retains real building and no registered owner actor");
            near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0]*1.15,"support actually survives forty offline ticks, not only same-callback logout");
            h.assertTrue(BuildingAbilityEffects.remaining(root)==deadline[0]-f.level.getGameTime(),"offline ticking preserves original expiry instead of pausing duration");
            replacement[0]=new PassiveIncomePeer(f.level,f.owner);RECONNECTED.add(replacement[0]);var online=replacement[0];
            var fresh=com.solegendary.reignofnether.research.economy.EconomyActionGate.query(online.actor,online.connection);
            h.assertTrue(fresh!=null&&!fresh.session().equals(original[0].session())&&fresh.epoch().equals(epoch),"new real connection gets new session, same enduring RTS account epoch");
            var stale=abilityWire(h,online,original[0]);
            h.assertTrue(stale!=null&&stale.status().equals("stale_session"),"old decoded network request rejected by new connection session before cached success or resource mutation");
            var current=BuildingAbilityService.view(online.actor,root);var payload=new BuildingAbilityPackets.Payload(BuildingAbilityPackets.Action.CAST,f.level.dimension().location(),root.originPos,UUID.randomUUID(),current.instance(),current.capture(),current.generation(),current.revision(),0);
            var recast=abilityWire(h,online,new BuildingAbilityPackets.Request(payload,fresh.epoch(),fresh.session(),1));
            h.assertTrue(recast!=null&&recast.status().equals("cooldown"),"new genuine online request still respects original paid cooldown");
            h.assertTrue(f.shadow()==paidShadow[0]&&f.account.revision==paidAccountRevision[0]&&root.getDataStorage().getData(BuildingAbilityState.TYPE).revision()==1
                    &&root.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.ROOT_CROWN)==ready[0],"logout/reconnect/old replay/fresh retry never repays, refunds, revisions or refreshes cooldown");
            h.assertTrue(BuildingAbilityEffects.remaining(root)==deadline[0]-f.level.getGameTime(),"reconnect and retry do not refresh or replace existing effect expiry");
        }).thenWaitUntil(()->h.assertTrue(witnessed[0]&&witnessed[1],"original159 and160 lifecycle boundary reads both observed"))
          .thenExecute(()->{
              near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0],"natural original expiry remains retired while owner is online again");
              h.assertTrue(f.shadow()==paidShadow[0]&&f.account.revision==paidAccountRevision[0]&&root.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.ROOT_CROWN)==ready[0],"natural expiry cannot refund original payment or cooldown");
              replacement[0].close();RECONNECTED.remove(replacement[0]);f.close();h.succeed();
          });
    }
    private static void neutralDamage(LivingEntity target){
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);target.removeAllEffects();target.setAbsorptionAmount(0);
        var ranged=target.getAttribute(AttributeRegistrar.RANGED_DAMAGE_RESIST.get());if(ranged!=null)ranged.setBaseValue(0);
    }
    private static float hit(LivingEntity target,DamageSource damage){float before=target.getHealth();target.invulnerableTime=0;target.hurt(damage,10);float lost=before-target.getHealth();target.setHealth(before);return lost;}
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=220)
    public static void daisActualFinalDamageNativeResistanceMaximumAndNestedTicketsStaySeparate(GameTestHelper h){
        var f=fixture(h,"Dais",104);var target=f.unit(f.dais,-3,-3);var control=f.unit(f.dais,-17,-3);
        var enemy=publish(f,f.dais,DreamseekersMod.BEETLE.get().create(f.level),-6,-3,f.owner+"_enemy");
        float[] plain={0},resisted={0};int[] nested={0};DamageSource[] exact={null};
        Consumer<LivingDamageEvent> probe=event->{
            if(event.getSource()!=exact[0])return;
            if(event.getEntity()==target&&nested[0]==0){nested[0]++;
                h.assertTrue(DaisWardEffects.matching(target,exact[0])!=null,"outer actual hurt owns its exact ward plan");
                hit(control,exact[0]);h.assertTrue(DaisWardEffects.matching(target,exact[0])!=null,"nested actual hurt returns to original exact frame");
            }else if(event.getEntity()==control){nested[0]++;
                h.assertTrue(DaisWardEffects.matching(control,exact[0])==null&&DaisWardEffects.matching(target,exact[0])==null,"same DS nested unwarded hurt cannot borrow outer ward or resistance receipt");
            }
        };
        h.startSequence().thenWaitUntil(()->{f.ready(target);f.ready(control);f.ready(enemy);}).thenExecute(()->{
            neutralDamage(target);neutralDamage(control);enemy.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);
            enemy.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);var crit=enemy.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);
            exact[0]=target.damageSources().mobAttack(enemy);plain[0]=hit(target,exact[0]);h.assertTrue(plain[0]>0,"real pre-ward RoN melee positive damage");
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,120,0));resisted[0]=hit(target,exact[0]);target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            int shadow=f.shadow();h.assertTrue(cast(f,f.dais).equals("requires_dais_network_tuning")&&f.shadow()==shadow,"real missing technology denies before fee");
            ResearchServerEvents.addResearch(f.owner,ResonanceResearch.ID);h.assertTrue(cast(f,f.dais).equals("success")&&f.shadow()==shadow-20,"real connected/tuned dais pays20S");
            near(h,hit(target,exact[0]),plain[0]*.90,"single final consumer applies10percent exactly once");
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,120,0));near(h,hit(target,exact[0]),resisted[0],"native20percent defense dominates ward; preserve existing native arithmetic without second10percent");target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            PROBES.add(probe);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,probe);
            near(h,hit(target,exact[0]),plain[0]*.90,"real same-DS nested callback cannot steal or multiply outer protection");h.assertTrue(nested[0]==2,"outer and nested final events actually observed");
            MinecraftForge.EVENT_BUS.unregister(probe);PROBES.remove(probe);
            BuildingServerEvents.getBuildings().remove(f.root);near(h,hit(target,exact[0]),plain[0],"same-tick network disconnection removes actual final protection");
            BuildingServerEvents.getBuildings().add(f.root);near(h,hit(target,exact[0]),plain[0],"reconnection cannot revive revoked old ward path");
            h.assertTrue(DaisWardEffects.matching(target,exact[0])==null,"all actual hurt frames retired after completion");f.close();h.succeed();
        });
    }
}
