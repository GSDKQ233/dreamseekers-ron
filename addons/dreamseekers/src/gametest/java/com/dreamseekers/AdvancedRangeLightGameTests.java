package com.dreamseekers;

import com.dreamseekers.resonance.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.stone.StoneProjectile;
import com.dreamseekers.unit.*;
import com.dreamseekers.unitprogression.AdvancedUnitResearch;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Actual authenticated casts, ordinary projectile publication and live cadence consumers.
 * Research grants below are explicit effect fixtures; actual paid queue is tested separately. */
@GameTestHolder("dreamseekers_advanced_range_light") @PrefixGameTestTemplate(false)
public final class AdvancedRangeLightGameTests {
    private static final String BATCH="advanced_range_light";
    private static final List<ResonanceNetworkGameTests.F> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanupAdvancedRangeLight(ServerLevel level){for(var f:FIXTURES)f.close();FIXTURES.clear();}
    private static ResonanceNetworkGameTests.F fixture(GameTestHelper h,String name,int index){
        var f=new ResonanceNetworkGameTests.F(h,"Advanced"+name,190+index);FIXTURES.add(f);return f;
    }
    private static <T extends Mob> T mob(ResonanceNetworkGameTests.F f,EntityType<T> type,double x,double z){
        T u=type.create(f.level);((Unit)u).setOwnerName(f.owner);u.setNoAi(true);u.setNoGravity(true);
        u.moveTo(f.dais.originPos.getX()+.5+x,f.dais.originPos.getY()+1.5,f.dais.originPos.getZ()+.5+z);
        var p=u.blockPosition();for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int y=0;y<5;y++)f.level.setBlock(p.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);
        f.h.assertTrue(f.level.addFreshEntity(u),"actual entity published");f.units.add(u);return u;
    }
    private static SkillResult cast(ResonanceNetworkGameTests.F f,SkillUnit source,UnitSkill skill,LivingEntity target){
        return SkillService.request(f.peer.actor,new SkillRequest(skill,source.mob().getId(),source.mob().getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID()));
    }
    private static void light(ResonanceNetworkGameTests.F f){ResearchServerEvents.addResearch(f.owner,LightResonanceResearch.FIRST_ID);ResearchServerEvents.addResearch(f.owner,AdvancedUnitResearch.LIGHT_EXTENSION_ID);}
    @GameTest(templateNamespace="dreamseekers_advanced_range_light",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=260)
    public static void gelExtensionSnapshotsDurationAndCooldownWithoutRetroactiveReduction(GameTestHelper h){
        var f=fixture(h,"Gel",0);var baseline=mob(f,DreamseekersMod.PINK_SLIME_UNIT.get(),2,-5);var enhanced=mob(f,DreamseekersMod.PINK_SLIME_UNIT.get(),4,-5);
        var a=mob(f,DreamseekersMod.PINK_SLIME_UNIT.get(),2,-8);var b=mob(f,DreamseekersMod.PINK_SLIME_UNIT.get(),4,-8);
        h.startSequence().thenWaitUntil(()->{f.ready(baseline);f.ready(enhanced);f.ready(a);f.ready(b);}).thenExecute(()->{
            h.assertTrue(cast(f,baseline,UnitSkill.GEL_GUARD,a)==SkillResult.SUCCESS,"baseline genuine cast");
            light(f);h.assertTrue(baseline.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==360&&SkillEffects.shieldTicks(a)==160,"research never retroactively changes existing cast");
            h.assertTrue(cast(f,enhanced,UnitSkill.GEL_GUARD,b)==SkillResult.SUCCESS,"researched genuine cast");
            h.assertTrue(enhanced.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==324&&SkillEffects.shieldTicks(b)==192,"actual new deadline and cooldown");
            enhanced.moveTo(enhanced.getX()+30,enhanced.getY(),enhanced.getZ());
            h.assertTrue(!SkillAmplification.extensionActive(enhanced,UnitSkill.GEL_GUARD)&&SkillEffects.shieldTicks(b)==192,"cast snapshot survives leaving eligibility network");
        }).thenExecuteAfter(165,()->h.assertTrue(SkillEffects.shieldRemaining(a)==0&&SkillEffects.shieldRemaining(b)>0,"actual baseline expiry differs from extended shield"))
          .thenExecuteAfter(32,()->{h.assertTrue(SkillEffects.shieldRemaining(b)==0,"extended shield naturally expires");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_advanced_range_light",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=270)
    public static void jellyKeepsSixPulsesAndOnlyExtendsDerivedTide(GameTestHelper h){
        var f=fixture(h,"Jelly",1);var jelly=mob(f,DreamseekersMod.JELLYFISH_UNIT.get(),2,-5);var target=mob(f,DreamseekersMod.PINK_SLIME_UNIT.get(),4,-5);light(f);
        h.startSequence().thenWaitUntil(()->{f.ready(jelly);f.ready(target);}).thenExecute(()->{
            target.setHealth(1);h.assertTrue(cast(f,jelly,UnitSkill.JELLY_FLOW,target)==SkillResult.SUCCESS,"actual jelly cast");
            h.assertTrue(jelly.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)==216&&SkillEffects.healTicks(target)==120,"main duration untouched and cooldown shortened");
        }).thenExecuteAfter(125,()->{
            h.assertTrue(Math.abs(target.getHealth()-15.4)<.001&&!SkillEffects.hasHeal(target),"six actual 2.4 HP pulses, no seventh");
            h.assertTrue(SkillEffects.hasNamedBuff(target,"light_tide",BuffKind.MOVEMENT_SPEED),"completed treatment derives tide");
        }).thenExecuteAfter(80,()->h.assertTrue(SkillEffects.hasNamedBuff(target,"light_tide",BuffKind.MOVEMENT_SPEED)&&Math.abs(target.getHealth()-15.4)<.001,"tide lasts beyond old eighty ticks; no extra healing"))
          .thenExecuteAfter(15,()->{h.assertTrue(!SkillEffects.hasNamedBuff(target,"light_tide",BuffKind.MOVEMENT_SPEED),"extended tide bounded to ninety-six");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_advanced_range_light",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=200)
    public static void foxExtensionExpiresNaturallyAndDoesNotIncreasePotency(GameTestHelper h){
        var f=fixture(h,"Fox",5);var fox=mob(f,DreamseekersMod.GOLDEN_FOX_UNIT.get(),2,-5);var target=mob(f,DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),5,-5);light(f);
        h.startSequence().thenWaitUntil(()->{f.ready(fox);f.ready(target);}).thenExecute(()->{
            h.assertTrue(cast(f,fox,UnitSkill.FOX_BLESSING,fox)==SkillResult.SUCCESS,"genuine researched fox cast");
            h.assertTrue(fox.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==432,"fox actual shortened cooldown");
            h.assertTrue(Math.abs(SkillEffects.bonus(target,BuffKind.ATTACK_SPEED)-.24)<.0001,"old tier-one potency remains twenty-four percent");
        }).thenExecuteAfter(125,()->h.assertTrue(SkillEffects.hasNamedBuff(target,SkillEffects.FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED),"beyond baseline one hundred twenty ticks"))
          .thenExecuteAfter(25,()->{h.assertTrue(!SkillEffects.hasNamedBuff(target,SkillEffects.FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED),"extended fox naturally expires at one hundred forty-four");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_advanced_range_light",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void ordinaryStoneActualProjectileGateAndHeavyCastUseTwelve(GameTestHelper h){
        var f=fixture(h,"Stone",2);var enemy=fixture(h,"Enemy",3);var stone=mob(f,DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),2,-10);var target=mob(f,DreamseekersMod.PINK_SLIME_UNIT.get(),13,-10);target.setOwnerName(enemy.owner);
        h.startSequence().thenWaitUntil(()->{f.ready(stone);f.ready(target);}).thenExecute(()->{
            h.assertTrue(stone.getAttackRange()==10&&cast(f,stone,UnitSkill.STONE_HEAVY_SHOT,target)==SkillResult.RANGE,"baseline eleven-block target denied");
            stone.performUnitRangedAttack(target,1);h.assertTrue(f.level.getEntitiesOfClass(StoneProjectile.class,stone.getBoundingBox().inflate(2)).isEmpty(),"baseline ordinary projectile gate denies eleven");
            ResearchServerEvents.addResearch(f.owner,AdvancedUnitResearch.STONE_RANGE_ID);
            h.assertTrue(stone.getAttackRange()==12&&StoneHeavyShot.range(stone)==12&&stone.getSiegeAttackRange()==10,"ordinary goal and heavy gate consume research");
            double insideX=target.getX();target.setPos(insideX+2,target.getY(),target.getZ());
            h.assertTrue(cast(f,stone,UnitSkill.STONE_HEAVY_SHOT,target)==SkillResult.RANGE,"thirteen-block heavy target remains outside upgraded twelve");
            stone.performUnitRangedAttack(target,1);h.assertTrue(f.level.getEntitiesOfClass(StoneProjectile.class,stone.getBoundingBox().inflate(2)).isEmpty(),"ordinary shot also rejects beyond upgraded twelve");
            target.setPos(insideX,target.getY(),target.getZ());
            stone.performUnitRangedAttack(target,1);var shots=f.level.getEntitiesOfClass(StoneProjectile.class,stone.getBoundingBox().inflate(2));h.assertTrue(shots.size()==1,"researched ordinary shot actually published");shots.forEach(Entity::discard);
            h.assertTrue(cast(f,stone,UnitSkill.STONE_HEAVY_SHOT,target)==SkillResult.SUCCESS,"researched eleven-block heavy cast succeeds");
            f.close();enemy.close();h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_advanced_range_light",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void pulseFourteenUsesFixedSelectionAndKeepsPublishedMovementSemantics(GameTestHelper h){
        var f=fixture(h,"Pulse",4);var target=f.unit(f.dais,13,-4);target.setNoGravity(true);var outside=f.unit(f.dais,15,-4);outside.setNoGravity(true);
        h.startSequence().thenWaitUntil(()->{f.ready(target);f.ready(outside);}).thenExecute(()->{
            ResearchServerEvents.addResearch(f.owner,ResonanceResearch.ID);
            h.assertTrue(f.pulse(f.dais).equals("no_target")&&f.shadow()==500,"target beyond twelve but within fourteen: baseline denies without debit");
            ResearchServerEvents.addResearch(f.owner,AdvancedUnitResearch.PULSE_RANGE_ID);double base=target.getNonBaseAttacksPerSecond();
            h.assertTrue(f.pulse(f.dais).equals("success")&&f.shadow()==485,"actual extended pulse debits once");
            h.assertTrue(Math.abs(target.getNonBaseAttacksPerSecond()-base*1.15)<.0001,"actual tuned APS between twelve and fourteen blocks");
            h.assertTrue(SkillEffects.bonus(outside,BuffKind.ATTACK_SPEED)==0,"beyond upgraded fourteen remains excluded");
            ResearchServerEvents.removeAllResearchFor(f.owner);h.assertTrue(Math.abs(target.getNonBaseAttacksPerSecond()-base*1.15)<.0001,"existing cast retains snapshotted fourteen range and amount");
            double oldX=target.getX();target.setPos(oldX+2,target.getY(),target.getZ());h.assertTrue(Math.abs(target.getNonBaseAttacksPerSecond()-base*1.15)<.0001,"movement after cast retains original pulse semantics");
            target.setPos(oldX,target.getY(),target.getZ());h.assertTrue(Math.abs(target.getNonBaseAttacksPerSecond()-base*1.15)<.0001,"returning does not stack or refresh the pulse");
            h.assertTrue(f.shadow()==485,"leaving cannot refund");f.close();h.succeed();
        });
    }
}
