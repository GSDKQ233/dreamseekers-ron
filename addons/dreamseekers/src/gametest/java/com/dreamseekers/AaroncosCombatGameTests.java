// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.aaroncos.*;
import com.dreamseekers.shadow.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder(DreamseekersMod.MOD_ID) @PrefixGameTestTemplate(false)
public final class AaroncosCombatGameTests {
 @GameTest(template="aaroncos_unit_arena",batch="aaron_combat",timeoutTicks=640) public static void leftRealMeleeAndAlliance(GameTestHelper h){melee(h,false);}
 @GameTest(template="aaroncos_unit_arena",batch="aaron_combat",timeoutTicks=640) public static void rightRealMeleeAndAlliance(GameTestHelper h){melee(h,true);}
 static void melee(GameTestHelper h,boolean right){var f=new AaroncosTestFixture(h,"Melee"+right);var m=f.hand(right,"Owner",6,1,8);var u=AaroncosTestFixture.unit(m);var enemy=f.fox("Enemy",24,1,8);Vec3[] start={null};float[] hp={0};
 f.ready(m,enemy).thenExecute(()->start[0]=m.position()).thenIdle(20).thenExecute(()->{h.assertTrue(enemy.getHealth()==1000&&m.position().distanceToSqr(start[0])<.1,"outside12 aggro no wild attack/wandering");f.attack(m,enemy);}).thenIdle(330).thenExecute(()->{h.assertTrue(enemy.getHealth()<1000&&m.position().distanceToSqr(start[0])>9,"real same-faction hostile pursuit and ordinary melee "+f.describe(m));AlliancesServerEvents.addAlliance(u.getOwnerName(),enemy.getOwnerName());hp[0]=enemy.getHealth();h.assertTrue(!m.doHurtTarget(enemy),"new alliance checked at final damage");u.setUnitAttackTargetForced(enemy);h.assertTrue(m.getTarget()==null&&!u.getTargetGoal().forced,"forced ally rejected");}).thenIdle(45).thenExecute(()->h.assertTrue(enemy.getHealth()==hp[0]&&m.getTarget()==null,"no later ally hit")).thenSucceed();}
 @GameTest(template="aaroncos_unit_arena",batch="aaron_combat",timeoutTicks=480) public static void leftHoldAndSameTargetExplicitAttack(GameTestHelper h){hold(h,false);}
 @GameTest(template="aaroncos_unit_arena",batch="aaron_combat",timeoutTicks=480) public static void rightHoldAndSameTargetExplicitAttack(GameTestHelper h){hold(h,true);}
 static void hold(GameTestHelper h,boolean right){var f=new AaroncosTestFixture(h,"Hold"+right);var m=f.hand(right,"Owner",6,1,8);var u=AaroncosTestFixture.unit(m);u.setHoldPosition(true);var enemy=f.fox("Enemy",17,1,8);var own=f.fox("Owner",40,1,30);Vec3[] start={null};
 f.ready(m,enemy,own).thenExecute(()->start[0]=m.position()).thenIdle(30).thenExecute(()->{h.assertTrue(u.getHoldPosition()&&m.getTarget()==enemy&&enemy.getHealth()==1000&&m.position().distanceToSqr(start[0])<.1,"held selected enemy outside actual seven-block melee cannot drag guard");u.setUnitAttackTargetForced(own);h.assertTrue(u.getHoldPosition()&&m.getTarget()==null,"invalid explicit target does not clear HOLD");u.setUnitAttackTarget(enemy);f.attack(m,enemy);h.assertTrue(!u.getHoldPosition()&&m.getTarget()==enemy&&u.getTargetGoal().forced,"same enemy real action releases hold");}).thenIdle(170).thenExecute(()->{h.assertTrue(enemy.getHealth()<1000&&m.position().distanceToSqr(start[0])>1,"genuine released pursuit/hit");f.order(m,UnitAction.HOLD,BlockPos.ZERO);}).thenIdle(20).thenExecute(()->{start[0]=m.position();enemy.moveTo(m.getX()+4,m.getY(),m.getZ());}).thenIdle(50).thenExecute(()->h.assertTrue(u.getHoldPosition()&&m.position().distanceToSqr(start[0])<.15&&enemy.getHealth()<960,"held in-range guard really hits without moving")).thenSucceed();}
 @GameTest(template="aaroncos_unit_arena",batch="aaron_combat",timeoutTicks=300) public static void leftAirMeleeNotMagicball(GameTestHelper h){air(h,false);}
 @GameTest(template="aaroncos_unit_arena",batch="aaron_combat",timeoutTicks=300) public static void rightAirMeleeNotMagicball(GameTestHelper h){air(h,true);}
 static void air(GameTestHelper h,boolean right){var f=new AaroncosTestFixture(h,"Air"+right);var m=f.hand(right,"Owner",6,1,8);var e=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());e.setOwnerName(f.owner("Enemy"));e.setNoAi(true);e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);e.setHealth(1000);f.place(e,18,7,8);
 f.ready(m,e).thenExecute(()->f.attack(m,e)).thenIdle(230).thenExecute(()->{h.assertTrue(e.getHealth()<1000&&m.getY()>f.p(0,2,0).getY()&&h.getLevel().noCollision(m),"genuine wide airborne pursuit and melee "+f.describe(m));h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,m.getBoundingBox().inflate(30)).isEmpty(),"no high-risk native projectile as basic attack");}).thenSucceed();}
 @GameTest(template="aaroncos_unit_arena",batch="aaron_combat",timeoutTicks=220) public static void leftDirectDamageWallStunFreeze(GameTestHelper h){wall(h,false);}
 @GameTest(template="aaroncos_unit_arena",batch="aaron_combat",timeoutTicks=220) public static void rightDirectDamageWallStunFreeze(GameTestHelper h){wall(h,true);}
 static void wall(GameTestHelper h,boolean right){var f=new AaroncosTestFixture(h,"Wall"+right);var m=f.hand(right,"Owner",8,1,8);var u=AaroncosTestFixture.unit(m);u.setHoldPosition(true);var e=f.fox("Enemy",14,1,8);for(int z=0;z<44;z++)for(int y=1;y<14;y++)f.set(11,y,z,Blocks.STONE);
 f.ready(m,e).thenExecute(()->{h.assertTrue(h.getLevel().noCollision(m)&&h.getLevel().noCollision(e)&&m.distanceToSqr(e)<49+e.getBbWidth(),"separate actual bodies within real core reach");h.assertTrue(!m.hasLineOfSight(e)&&!m.doHurtTarget(e),"direct blocked hit denied");u.setUnitAttackTarget(e);}).thenIdle(45).thenExecute(()->{h.assertTrue(e.getHealth()==1000,"no native through-wall hit");for(int z=6;z<=10;z++)for(int y=1;y<=7;y++)f.set(11,y,z,Blocks.AIR);for(var effect:List.of(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get(),com.solegendary.reignofnether.registrars.MobEffectRegistrar.FREEZE.get())){m.addEffect(new MobEffectInstance(effect,40));h.assertTrue(m.hasLineOfSight(e)&&!m.doHurtTarget(e),"actual STUN/FREEZE rejects damage immediately");m.removeEffect(effect);}h.assertTrue(m.doHurtTarget(e)&&e.getHealth()<1000,"ordinary actual unblocked enemy damage positive");}).thenSucceed();}
 @GameTest(template="aaroncos_unit_arena",batch="aaron_combat",timeoutTicks=300) public static void leftFollowRevocationAndNewEnemy(GameTestHelper h){follow(h,false);}
 @GameTest(template="aaroncos_unit_arena",batch="aaron_combat",timeoutTicks=300) public static void rightFollowRevocationAndNewEnemy(GameTestHelper h){follow(h,true);}
 static void follow(GameTestHelper h,boolean right){var f=new AaroncosTestFixture(h,"Follow"+right);var m=f.hand(right,"Owner",6,1,8);var u=AaroncosTestFixture.unit(m);var ally=f.fox("Ally",45,1,8);var e=f.fox("Enemy",45,1,35);AlliancesServerEvents.addAlliance(u.getOwnerName(),ally.getOwnerName());Vec3[] start={null},stop={null};
 var s=f.ready(m,ally,e).thenExecute(()->{start[0]=m.position();UnitServerEvents.addActionItem(u.getOwnerName(),UnitAction.FOLLOW,ally.getId(),new int[]{m.getId()},BlockPos.ZERO,BlockPos.ZERO);}).thenIdle(40).thenExecute(()->{h.assertTrue(u.getFollowTarget()==ally&&m.position().distanceToSqr(start[0])>1&&m.distanceTo(ally)>13,"real friendly follow before revocation outside aggro");AlliancesServerEvents.removeAlliance(u.getOwnerName(),ally.getOwnerName());}).thenIdle(10).thenExecute(()->stop[0]=m.position());
 for(int i=0;i<30;i++)s.thenIdle(1).thenExecute(()->h.assertTrue(u.getFollowTarget()==null&&u.getMoveGoal().getMoveTarget()==null&&m.getNavigation().isDone()&&m.position().distanceToSqr(stop[0])<.1,"revoked friendly path never resurrects"));
 s.thenExecute(()->{start[0]=m.position();e.moveTo(m.getX()+10,m.getY(),m.getZ());}).thenIdle(140).thenExecute(()->h.assertTrue(e.getHealth()<1000&&m.position().distanceToSqr(start[0])>1&&u.getFollowTarget()==null&&ally.getHealth()==1000,"new hostile reacquisition is legitimate, not old follow")).thenSucceed();}
 @AfterBatch(batch="aaron_combat") public static void cleanup(ServerLevel l){AaroncosTestFixture.cleanup(l);}
}
