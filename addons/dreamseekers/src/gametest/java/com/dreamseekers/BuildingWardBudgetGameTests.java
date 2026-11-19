// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.*;
import com.dreamseekers.resonance.ResonanceResearch;
import com.dreamseekers.skill.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("dreamseekers_building_boundary") @PrefixGameTestTemplate(false)
public final class BuildingWardBudgetGameTests {
    private static final String BATCH="building_ward_budget";
    private static final List<ResonanceNetworkGameTests.F> FIXTURES=new ArrayList<>();
    private static final List<Object> PROBES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        for(var p:List.copyOf(PROBES))MinecraftForge.EVENT_BUS.unregister(p);PROBES.clear();
        for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();
    }
    private static ResonanceNetworkGameTests.F fixture(GameTestHelper h,String label,int index) {
        var f=new ResonanceNetworkGameTests.F(h,label,index);FIXTURES.add(f);ResearchServerEvents.addResearch(f.owner,ResonanceResearch.ID);return f;
    }
    private static <T extends Mob> T body(ResonanceNetworkGameTests.F f,T e,int x,int z,String owner) {
        ((Unit)e).setOwnerName(owner);e.setNoAi(true);BlockPos p=f.dais.originPos.offset(x,1,z);f.load(p);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            f.level.setBlock(p.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<5;y++)f.level.setBlock(p.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);
        }
        e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);f.h.assertTrue(f.level.addFreshEntity(e),"native publication succeeds");f.units.add(e);return e;
    }
    private static void neutral(LivingEntity target) {
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        target.removeAllEffects();target.setAbsorptionAmount(0);var r=target.getAttribute(AttributeRegistrar.RANGED_DAMAGE_RESIST.get());if(r!=null)r.setBaseValue(0);
    }
    private static void damage(LivingEntity actor) {
        actor.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);actor.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
        var crit=actor.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);
    }
    private static String cast(ResonanceNetworkGameTests.F f) {
        var v=BuildingAbilityService.view(f.peer.actor,f.dais);return BuildingAbilityService.cast(f.peer.actor,f.dais,v.instance(),v.capture(),v.generation(),v.revision());
    }
    private static float hit(LivingEntity target,DamageSource source) {
        float before=target.getHealth();target.invulnerableTime=0;target.hurt(source,10);return before-target.getHealth();
    }
    private static void near(GameTestHelper h,double got,double expected,String why){h.assertTrue(Math.abs(got-expected)<.0002,why+" actual="+got+" expected="+expected);}
    @GameTest(templateNamespace="dreamseekers_building_boundary",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=240)
    public static void paidWardReducesBeforeRealGelBudgetAndDoesNotDuplicateDepletionCounter(GameTestHelper h) {
        var f=fixture(h,"WardGel",140);
        var target=body(f,DreamseekersMod.PINK_SLIME_UNIT.get().create(f.level),-3,-3,f.owner);
        var donor=body(f,DreamseekersMod.PINK_SLIME_UNIT.get().create(f.level),-5,-3,f.owner);
        var enemy=body(f,DreamseekersMod.BEETLE.get().create(f.level),-8,-3,f.owner+"_enemy");
        h.startSequence().thenWaitUntil(()->{f.ready(target);f.ready(donor);f.ready(enemy);}).thenExecute(()->{
            neutral(target);damage(enemy);DamageSource source=target.damageSources().mobAttack(enemy);
            float before=target.getHealth(),plain=hit(target,source);target.setHealth(before);near(h,plain,10,"unwarded actual hurt positive control");
            int shadow=f.shadow();h.assertTrue(cast(f).equals("success")&&f.shadow()==shadow-20,"one real paid ward commit");
            var request=new SkillRequest(UnitSkill.GEL_GUARD,donor.getId(),donor.getUUID(),donor.rts().skills().revision(),target.getId(),target.getUUID());
            h.assertTrue(SkillService.request(f.peer.actor,request)==SkillResult.SUCCESS,"actual ordinary Gel request succeeds");
            near(h,SkillEffects.shieldRemaining(target),12,"real initial Gel12 budget");
            near(h,hit(target,source),0,"first warded9 is fully absorbed");near(h,SkillEffects.shieldRemaining(target),3,"ward10percent applies before Gel budget12-9");
            near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"no counter before genuine depletion");
            near(h,hit(target,source),6,"second warded9 exhausts3 and loses6HP, not double ward");near(h,SkillEffects.shieldRemaining(target),0,"exact Gel depletion");
            near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.15,"one ordinary counter on protected body after depletion");near(h,SkillEffects.bonus(donor,BuffKind.ATTACK_SPEED),0,"donor never steals counter");
            near(h,hit(target,source),9,"third actual hit has only ward, no recreated Gel");near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.15,"later hit cannot stack counter above original15percent");
            h.assertTrue(donor.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==360&&f.shadow()==shadow-20,"all same-callback hits preserve original Gel CD and single building fee");
            h.assertTrue(DaisWardEffects.matching(target,source)==null,"no exact-hit frame leaks after real damage");f.close();h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_building_boundary",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=260)
    public static void actualWardDamageAtNatural119And120PrecedesLevelEndAndKeepsPaidCooldown(GameTestHelper h) {
        var f=fixture(h,"WardExpiry",141);var target=f.unit(f.dais,-3,-3);
        var enemy=body(f,DreamseekersMod.BEETLE.get().create(f.level),-8,-3,f.owner+"_enemy");
        long[] deadline={-1},lastEnd={Long.MIN_VALUE};boolean[] seen={false,false};DamageSource[] source={null};float[] hp={0};int[] paidShadow={0};
        Consumer<TickEvent.LevelTickEvent> end=e->{if(e.level==f.level&&e.phase==TickEvent.Phase.END)lastEnd[0]=f.level.getGameTime();};
        Consumer<LivingEvent.LivingTickEvent> living=e->{
            if(e.getEntity()!=target||deadline[0]<0)return;long now=f.level.getGameTime();
            if(now==deadline[0]-1&&!seen[0]){h.assertTrue(lastEnd[0]<now,"119 hit precedes same-level END sweep");near(h,hit(target,source[0]),9,"actual119 damage consumes still-live ward exactly once");target.setHealth(hp[0]);seen[0]=true;}
            if(now==deadline[0]&&!seen[1]){h.assertTrue(lastEnd[0]<now,"120 hit precedes same-level END sweep");near(h,hit(target,source[0]),10,"actual120 damage must reject expired lease before any cleanup getter");target.setHealth(hp[0]);seen[1]=true;}
        };
        PROBES.add(end);PROBES.add(living);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,end);MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,living);
        h.startSequence().thenWaitUntil(()->{f.ready(target);f.ready(enemy);}).thenExecute(()->{
            neutral(target);damage(enemy);source[0]=target.damageSources().mobAttack(enemy);hp[0]=target.getHealth();near(h,hit(target,source[0]),10,"real native baseline10");target.setHealth(hp[0]);
            int shadow=f.shadow();h.assertTrue(cast(f).equals("success")&&f.shadow()==shadow-20,"ward commits one20S payment");paidShadow[0]=f.shadow();deadline[0]=f.level.getGameTime()+120;
        }).thenWaitUntil(()->h.assertTrue(seen[0]&&seen[1],"both actual pre-END natural deadline damage observations reached"))
          .thenExecute(()->{h.assertTrue(f.shadow()==paidShadow[0]&&f.dais.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.DAIS_WARD)==deadline[0]+780,"original900 CD and fee survive short120 expiry");f.close();h.succeed();});
    }
}
