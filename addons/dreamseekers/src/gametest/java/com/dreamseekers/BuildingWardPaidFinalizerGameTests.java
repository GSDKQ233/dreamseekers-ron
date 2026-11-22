// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.*;
import com.dreamseekers.resonance.ResonanceResearch;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.AaroncosLeftHandUnit;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** External source-only Dais x Aaroncos finalizer coverage. Genuine registered Aaroncos LeftHand
 * bodies, actual authenticated20S requests, original AD/weapon sampling and natural12tick release.
 * Recipient maxHP is only a survival fixture. No source damage/cap/Impact/clock is fabricated.
 * These two tests cover prepareAaroncosFinal, not every legacy paid finalizer branch.
 */
@GameTestHolder("dreamseekers_building_boundary") @PrefixGameTestTemplate(false)
public final class BuildingWardPaidFinalizerGameTests {
    private static final String BATCH="building_ward_paid";
    private static final List<ResonanceNetworkGameTests.F> FIXTURES=new ArrayList<>();
    private static final List<PaidWardReceiptProbe> PROBES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel ignored){
        for(var probe:List.copyOf(PROBES))probe.close();
        for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();
    }
    private static ResonanceNetworkGameTests.F fixture(GameTestHelper h,String label,int index){
        var f=new ResonanceNetworkGameTests.F(h,"WardPaid_"+label,index);FIXTURES.add(f);return f;
    }
    private static <T extends Mob> T body(ResonanceNetworkGameTests.F arena,T entity,int x,int z,String owner){
        arena.h.assertTrue(entity!=null&&entity instanceof Unit,"real registered RTS body");
        ((Unit)entity).setOwnerName(owner);entity.setNoAi(true);var p=arena.dais.originPos.offset(x,1,z);arena.load(p);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            arena.level.setBlock(p.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<5;y++)arena.level.setBlock(p.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);
        }
        entity.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);
        arena.h.assertTrue(arena.level.addFreshEntity(entity),"actual exact body publication");arena.units.add(entity);return entity;
    }
    private static void ready(ResonanceNetworkGameTests.F f,LivingEntity...bodies){
        for(var body:bodies){f.ready(body);f.h.assertTrue(body.tickCount>=30,"natural initial setup before sampling native attributes");}
    }
    private static void recipient(LivingEntity target){
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.removeAllEffects();target.setAbsorptionAmount(0);
        for(var attr:List.of(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(),AttributeRegistrar.MAGIC_DAMAGE_RESIST.get()))
            if(target.getAttribute(attr)!=null)target.getAttribute(attr).setBaseValue(0);
        // Survival only: source ATTACK_DAMAGE, skill coefficients and all cooldowns remain native.
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
    }
    private static void calm(AaroncosLeftHandUnit hand){
        hand.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);
        hand.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);
    }
    private static double nativeAttack(AaroncosLeftHandUnit hand){
        return (float)(hand.getUnitAttackDamage()+AttackerUnit.getWeaponDamageModifier(hand));
    }
    private static void near(GameTestHelper h,double actual,double expected,String why){
        h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" actual="+actual+" expected="+expected);
    }
    private static long ward(GameTestHelper h,ResonanceNetworkGameTests.F f){
        ResearchServerEvents.addResearch(f.owner,ResonanceResearch.ID);var view=BuildingAbilityService.view(f.peer.actor,f.dais);
        int shadow=f.shadow();long revision=f.account.revision;
        h.assertTrue(view!=null&&BuildingAbilityService.cast(f.peer.actor,f.dais,view.instance(),view.capture(),view.generation(),view.revision()).equals("success"),"real owner commits connected/tuned Dais");
        var state=f.dais.getDataStorage().getData(BuildingAbilityState.TYPE);
        h.assertTrue(f.shadow()==shadow-20&&f.account.revision==revision+1&&state.revision()==1
                &&state.readyAt(BuildingAbility.DAIS_WARD)==f.level.getGameTime()+900,"exact20S original900tick building cooldown");
        return state.readyAt(BuildingAbility.DAIS_WARD);
    }
    private static void press(GameTestHelper h,ResonanceNetworkGameTests.F enemy,AaroncosLeftHandUnit hand,LivingEntity target){
        h.assertTrue(hand.getType()==DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get(),"actual controlled Aaroncos LeftHand, never55HP ShadowHand surrogate");
        int shadow=enemy.shadow();long revision=enemy.account.revision;
        var request=new SkillRequest(UnitSkill.AARONCOS_LEFT_PRESS,hand.getId(),hand.getUUID(),hand.rts().skills().revision(),target.getId(),target.getUUID());
        h.assertTrue(new SkillNetwork.Cast(request).execute(enemy.peer.actor)==SkillResult.SUCCESS,"genuine registered enemy peer submits authorized LeftPress");
        h.assertTrue(enemy.shadow()==shadow-20&&enemy.account.revision==revision+1&&hand.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==400
                &&AaroncosCombat.remainingTicks(hand)==12,"actual20S debit, original400CD and12tick windup");
        h.assertTrue(new SkillNetwork.Cast(request).execute(enemy.peer.actor)==SkillResult.REPLAY&&enemy.shadow()==shadow-20&&enemy.account.revision==revision+1,
                "old actual skill request cannot repay or restart charge");
    }
    private static void payments(GameTestHelper h,ResonanceNetworkGameTests.F own,ResonanceNetworkGameTests.F enemy,
            long ready,int ownShadow,long ownRevision,int enemyShadow,long enemyRevision){
        h.assertTrue(own.shadow()==ownShadow&&own.account.revision==ownRevision&&enemy.shadow()==enemyShadow&&enemy.account.revision==enemyRevision
                &&own.dais.getDataStorage().getData(BuildingAbilityState.TYPE).revision()==1
                &&own.dais.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.DAIS_WARD)==ready,
                "real finalizers cannot duplicate/refund building or paid-skill resource transactions or refresh buildingCD");
    }
    private static final class PaidWardReceiptProbe implements AutoCloseable {
        final Consumer<LivingDamageEvent> action;
        PaidWardReceiptProbe(Consumer<LivingDamageEvent> action){this.action=action;PROBES.add(this);MinecraftForge.EVENT_BUS.register(this);}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void paidWardDamageReceipt(LivingDamageEvent event){action.accept(event);}
        @Override public void close(){MinecraftForge.EVENT_BUS.unregister(this);PROBES.remove(this);}
    }

    @GameTest(templateNamespace="dreamseekers_building_boundary",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=170)
    public static void genuinePaidLeftPressKeepsNativeSnapshotTwelveTickClockAndOneWardMultiplier(GameTestHelper h){
        var own=fixture(h,"PressOwn",180);var enemy=fixture(h,"PressEnemy",181);
        var protectedTarget=body(own,DreamseekersMod.GOLDEN_FOX_UNIT.get().create(own.level),-3,-3,own.owner);
        var plainTarget=body(own,DreamseekersMod.GOLDEN_FOX_UNIT.get().create(own.level),-3,-18,own.owner);
        var protectedHand=body(own,DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(own.level),-7,-3,enemy.owner);
        var plainHand=body(own,DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(own.level),-7,-18,enemy.owner);
        int[] events={0,0},cdAtRelease={-1,-1},paidShadow={0,0};long[] started={-1},released={-1,-1},readyAt={0},paidRevision={0,0};double[] ad={0};
        var probe=new PaidWardReceiptProbe(event->{if(!AaroncosCombat.isStrike(event.getSource()))return;
            int lane=event.getEntity()==protectedTarget&&event.getSource().getEntity()==protectedHand?0:event.getEntity()==plainTarget&&event.getSource().getEntity()==plainHand?1:-1;
            if(lane<0)return;events[lane]++;released[lane]=own.level.getGameTime();cdAtRelease[lane]=(lane==0?protectedHand:plainHand).rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS);
            near(h,event.getAmount(),ad[0]*4,"genuine dedicated pre-final snapshot keeps native AD times four, not a second native attack sample");
            h.assertTrue((DaisWardEffects.matching(event.getEntity(),event.getSource())!=null)==(lane==0),"same genuine skill path captures ward only for in-range original recipient");
        });
        h.startSequence().thenWaitUntil(()->ready(own,protectedTarget,plainTarget,protectedHand,plainHand)).thenExecute(()->{
            recipient(protectedTarget);recipient(plainTarget);calm(protectedHand);calm(plainHand);
            near(h,protectedHand.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).getBaseValue(),26,"real unchanged Aaroncos26AD fixture");
            ad[0]=nativeAttack(plainHand);near(h,nativeAttack(protectedHand),ad[0],"two genuine native AD plus weapon baselines match");
            readyAt[0]=ward(h,own);h.assertTrue(DaisWardEffects.active(protectedTarget)&&!DaisWardEffects.active(plainTarget),"real building sphere separates paired recipients");
            press(h,enemy,protectedHand,protectedTarget);press(h,enemy,plainHand,plainTarget);started[0]=own.level.getGameTime();
            paidShadow[0]=own.shadow();paidShadow[1]=enemy.shadow();paidRevision[0]=own.account.revision;paidRevision[1]=enemy.account.revision;
        }).thenExecuteAfter(11,()->{
            h.assertTrue(own.level.getGameTime()==started[0]+11&&events[0]==0&&events[1]==0,"natural first11ticks contain no paid physical write");near(h,protectedTarget.getHealth(),1000,"ward lane not hit early");near(h,plainTarget.getHealth(),1000,"control lane not hit early");
        }).thenWaitUntil(()->h.assertTrue(events[0]>0&&events[1]>0,"both genuine paid physical releases observed"))
          .thenExecute(()->{
              h.assertTrue(events[0]==1&&events[1]==1&&released[0]==started[0]+12&&released[1]==started[0]+12&&cdAtRelease[0]==388&&cdAtRelease[1]==388,"one actual event each at original12tick release with400-minus12CD");
              near(h,1000-plainTarget.getHealth(),ad[0]*4,"actual unwarded paid HP loss uses 400% of the sampled AD");
              near(h,1000-protectedTarget.getHealth(),(1000-plainTarget.getHealth())*.9,"actual protected paid HP loss gets one10percent reduction, never0.9 squared");
          }).thenExecuteAfter(3,()->{
              long elapsed=own.level.getGameTime()-started[0];
              h.assertTrue(events[0]==1&&events[1]==1&&!AaroncosCombat.active(protectedHand)&&!AaroncosCombat.active(plainHand)
                      &&protectedHand.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==400-elapsed&&plainHand.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==400-elapsed,"natural release completion neither repeats damage nor changes either original paid clock");
              payments(h,own,enemy,readyAt[0],paidShadow[0],paidRevision[0],paidShadow[1],paidRevision[1]);probe.close();own.close();enemy.close();h.succeed();
          });
    }

    @GameTest(templateNamespace="dreamseekers_building_boundary",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=230)
    public static void twoGenuinePaidHandsApplyWardBeforeGelAndCannotReuseShieldOrRefreshOriginalRebound(GameTestHelper h){
        var own=fixture(h,"GelOwn",182);var enemy=fixture(h,"GelEnemy",183);
        var target=body(own,DreamseekersMod.PINK_SLIME_UNIT.get().create(own.level),-3,-3,own.owner);
        var donor=body(own,DreamseekersMod.PINK_SLIME_UNIT.get().create(own.level),-1,-5,own.owner);
        var first=body(own,DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(own.level),-7,-3,enemy.owner);
        var second=body(own,DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(own.level),-7,-7,enemy.owner);
        int[] events={0,0},cdAtRelease={-1,-1},paidShadow={0,0};long[] starts={-1,-1},released={-1,-1},readyAt={0},paidRevision={0,0},gelAt={0};
        double[] amount={0},aps={0};float[] afterFirst={0};
        var probe=new PaidWardReceiptProbe(event->{if(event.getEntity()!=target||!AaroncosCombat.isStrike(event.getSource()))return;
            int lane=event.getSource().getEntity()==first?0:event.getSource().getEntity()==second?1:-1;if(lane<0)return;
            events[lane]++;released[lane]=own.level.getGameTime();cdAtRelease[lane]=(lane==0?first:second).rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS);
            near(h,event.getAmount(),amount[0],"each genuine paid hit reaches final boundary with a single 400% native AD/weapon snapshot");
            h.assertTrue(DaisWardEffects.matching(target,event.getSource())!=null,"actual dedicated finalizer has original paid ward at both releases");
        });
        h.startSequence().thenWaitUntil(()->ready(own,target,donor,first,second)).thenExecute(()->{
            recipient(target);calm(first);calm(second);near(h,first.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).getBaseValue(),26,"real26AD LeftHand, no small-damage special fixture");
            amount[0]=nativeAttack(first)*4;near(h,nativeAttack(second)*4,amount[0],"fresh second real hand has matching original paid snapshot");
            h.assertTrue(amount[0]*.9>12,"first real native hit genuinely exhausts full12Gel; test never assumes residual shield");aps[0]=target.getNonBaseAttacksPerSecond();
            readyAt[0]=ward(h,own);int shadow=own.shadow();long revision=own.account.revision;
            var gel=new SkillRequest(UnitSkill.GEL_GUARD,donor.getId(),donor.getUUID(),donor.rts().skills().revision(),target.getId(),target.getUUID());
            h.assertTrue(new SkillNetwork.Cast(gel).execute(own.peer.actor)==SkillResult.SUCCESS,"actual owner Gel request, no fabricated shield");gelAt[0]=own.level.getGameTime();
            near(h,SkillEffects.shieldRemaining(target),12,"real unamplified baseline Gel budget");
            h.assertTrue(own.shadow()==shadow&&own.account.revision==revision&&donor.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==360,"original Gel has zero resource fee and360tick personal cooldown");
            press(h,enemy,first,target);starts[0]=own.level.getGameTime();paidShadow[0]=own.shadow();paidRevision[0]=own.account.revision;
        }).thenExecuteAfter(11,()->{
            h.assertTrue(events[0]==0&&events[1]==0&&own.level.getGameTime()==starts[0]+11,"first real paid charge cannot release early");near(h,target.getHealth(),1000,"no early paid HP loss");near(h,SkillEffects.shieldRemaining(target),12,"no early Gel debit");
        }).thenWaitUntil(()->h.assertTrue(events[0]>0,"first genuine paid physical finalizer reached"))
          .thenExecute(()->{
              h.assertTrue(events[0]==1&&released[0]==starts[0]+12&&cdAtRelease[0]==388,"first actual paid write has exact original12/400 clocks");
              near(h,1000-target.getHealth(),amount[0]*.9-12,"Dais reduces full paid snapshot before Gel12; never (damage-minus12)*.9 or double ward");
              near(h,SkillEffects.shieldRemaining(target),0,"original Gel was genuinely exhausted exactly once");near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.15,"one genuine original break rebound on protected target");
              near(h,target.getNonBaseAttacksPerSecond(),aps[0]*1.15,"actual APS consumer sees one original15percent rebound");near(h,SkillEffects.bonus(donor,BuffKind.ATTACK_SPEED),0,"donor never steals target break reward");
              afterFirst[0]=target.getHealth();press(h,enemy,second,target);starts[1]=own.level.getGameTime();
              paidShadow[1]=enemy.shadow();paidRevision[1]=enemy.account.revision;
          }).thenExecuteAfter(11,()->{
              h.assertTrue(events[1]==0&&own.level.getGameTime()==starts[1]+11,"fresh second real hand also obeys original12tick windup");near(h,target.getHealth(),afterFirst[0],"no fabricated second write before release");
          }).thenWaitUntil(()->h.assertTrue(events[1]>0,"second independent genuine paid physical finalizer reached"))
          .thenExecute(()->{
              h.assertTrue(events[0]==1&&events[1]==1&&released[1]==starts[1]+12&&cdAtRelease[1]==388,"second actual write is single and uses its own unchanged12/400clock");
              near(h,afterFirst[0]-target.getHealth(),amount[0]*.9,"fresh paid hit after real Gel break has ward once and no borrowed old shield");
              near(h,1000-target.getHealth(),amount[0]*1.8-12,"two actual HP settlements conserve original single12Gel budget");
              near(h,SkillEffects.shieldRemaining(target),0,"second finalizer cannot recreate or spend exhausted Gel");near(h,target.getNonBaseAttacksPerSecond(),aps[0]*1.15,"second hit cannot stack another break rebound");
              payments(h,own,enemy,readyAt[0],paidShadow[0],paidRevision[0],paidShadow[1],paidRevision[1]);
          }).thenWaitUntil(()->h.assertTrue(released[0]>=0&&own.level.getGameTime()>=released[0]+60,"natural original60tick rebound deadline reached"))
          .thenExecute(()->{
              h.assertTrue(events[0]==1&&events[1]==1&&!AaroncosCombat.active(first)&&!AaroncosCombat.active(second),"no repeated paid writes while waiting for original rebound expiry");
              near(h,target.getNonBaseAttacksPerSecond(),aps[0],"original rebound expires without being refreshed by second paid hit");near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"no late replacement rebound record");
              long now=own.level.getGameTime();
              h.assertTrue(first.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==400-(now-starts[0])
                      &&second.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==400-(now-starts[1])
                      &&donor.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==360-(now-gelAt[0]),"both genuine20S skills and zero-resource Gel retain their own original absolute cooldowns");
              h.assertTrue(DaisWardEffects.active(target),"observation finishes before original120tick ward expiry, so removal cannot be mistaken for natural timeout");
              own.dais.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(own.dais);
              float before=target.getHealth();target.invulnerableTime=0;h.assertTrue(target.hurt(target.damageSources().mobAttack(first),1),"fresh real ordinary native hit after original source cleanup");
              near(h,before-target.getHealth(),nativeAttack(first),"original source detach prevents stale ward in next actual damage consumer");
              payments(h,own,enemy,readyAt[0],paidShadow[0],paidRevision[0],paidShadow[1],paidRevision[1]);probe.close();own.close();enemy.close();h.succeed();
          });
    }
}
