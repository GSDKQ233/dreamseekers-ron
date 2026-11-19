// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.unit.*;
import com.dreamseekers.shadow.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.*;
import java.util.*;
@GameTestHolder(DreamseekersMod.MOD_ID) @PrefixGameTestTemplate(false)
public final class CrazyTerrorbeakSafetyGameTests {
    static final String BATCH="dreamseekers_crazy_terrorbeak_safety";
    @AfterBatch(batch=BATCH)public static void cleanup(ServerLevel l){CrazyTerrorbeakFixture.cleanup(l);}
    @GameTest(template="crazy_unit_arena",batch=BATCH,timeoutTicks=80)
    public static void ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h) {
        float[] enemyHealth={0};var f=new CrazyTerrorbeakFixture(h);CrazyTerrorbeakUnit source=f.unit("Owner",5,5);GoldenFoxUnit own=f.fox("Owner",6,5),ally=f.fox("Ally",7,5),enemy=f.fox("Enemy",20,5);
        AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ready(source)&&f.ready(enemy)&&h.getLevel().getEntity(source.getId())==source&&h.getLevel().getEntity(enemy.getId())==enemy,"actual published reserved skill fixtures")).thenExecute(()->{
        AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());
        for(GoldenFoxUnit target:List.of(own,ally)) {
            float hp=target.getHealth();h.assertTrue(!source.doHurtTarget(target)&&target.getHealth()==hp,"own/allied health protected");
            source.setUnitAttackTargetForced(target);h.assertTrue(source.getTarget()==null&&!source.getTargetGoal().forced,"forced ally target rejected without sticky forced flag");
        }
        FakePlayer owner=player(h,"Owner"),foreign=player(h,"Enemy");
        var request=request(source);
        var wrongSkill=new ShadowSkillRequest(1,UUID.randomUUID(),ShadowSkillRequest.Skill.SHADOW_GHOST_SPECIAL,request.caster(),request.target());
        h.assertTrue(ShadowSkillService.request(player(h,"Owner"),wrongSkill)==ShadowSkillService.Result.INVALID_SOURCE,"skill and subtype must match exactly");
        var energy=new ShadowEnergyGate.Reservation(UUID.randomUUID(),owner.getUUID(),1);
        h.assertTrue(ShadowEnergyGate.reserve(owner,energy)==ShadowEnergyGate.Result.UNAVAILABLE,"positive energy reservation unavailable, never fake permission");
        h.assertTrue(ShadowEnergyGate.reserve(foreign,energy)==ShadowEnergyGate.Result.NOT_PAYER,"real sender bound to payer");
        h.assertTrue(ShadowEnergyGate.reserve(owner,new ShadowEnergyGate.Reservation(UUID.randomUUID(),owner.getUUID(),0))==ShadowEnergyGate.Result.INVALID_REQUEST,"zero energy cannot authorize free effects");
        h.assertTrue(ShadowEnergyGate.reserve(owner,new ShadowEnergyGate.Reservation(UUID.randomUUID(),owner.getUUID(),-1))==ShadowEnergyGate.Result.INVALID_REQUEST,"negative energy cannot grant currency");
        h.assertTrue(ShadowSkillService.request(null,request)==ShadowSkillService.Result.INVALID_REQUEST,"no sender rejected");
        h.assertTrue(ShadowSkillService.request(foreign,request)==ShadowSkillService.Result.NOT_OWNER,"same-faction foreign sender rejected");
        var stale=new ShadowSkillRequest(1,UUID.randomUUID(),ShadowSkillRequest.Skill.CRAZY_TERRORBEAK_SPECIAL,new ShadowSkillRequest.Source(source.getId(),UUID.randomUUID(),source.rts().ownershipRevision,h.getLevel().dimension().location()),new ShadowSkillRequest.SelfTarget());
        h.assertTrue(ShadowSkillService.request(owner,stale)==ShadowSkillService.Result.INVALID_SOURCE,"ID reuse/stale UUID rejected");
        h.assertTrue(ShadowSkillService.request(owner,request)==ShadowSkillService.Result.UNAVAILABLE&&ShadowSkillService.request(owner,request)==ShadowSkillService.Result.UNAVAILABLE,"valid and duplicate requests never activate undefined summons");
        h.assertTrue(ShadowSkillService.request(owner,new ShadowSkillRequest(2,request.requestId(),request.skill(),request.caster(),request.target()))==ShadowSkillService.Result.INVALID_REQUEST,"future schema rejected");
        var wrongDimension=new ShadowSkillRequest(1,UUID.randomUUID(),ShadowSkillRequest.Skill.CRAZY_TERRORBEAK_SPECIAL,new ShadowSkillRequest.Source(source.getId(),source.getUUID(),source.rts().ownershipRevision,net.minecraft.world.level.Level.NETHER.location()),request.target());
        h.assertTrue(ShadowSkillService.request(owner,wrongDimension)==ShadowSkillService.Result.WRONG_DIMENSION,"cross-dimension request fails closed");
        enemy.setOwnerName("");h.assertTrue(!source.doHurtTarget(enemy),"unowned target is not a valid paid combat target");enemy.setOwnerName(CrazyTerrorbeakFixture.PREFIX+"Enemy");
        source.setOwnerName("");h.assertTrue(!source.doHurtTarget(enemy)&&ShadowSkillService.request(owner,request)==ShadowSkillService.Result.STALE_OWNER,"unowned source cannot fight or cast");
        source.setOwnerName(CrazyTerrorbeakFixture.PREFIX+"Owner");h.assertTrue(ShadowSkillService.request(owner,request)==ShadowSkillService.Result.STALE_OWNER,"observed owner ABA invalidates old authority epoch");enemyHealth[0]=enemy.getHealth();
        }).thenIdle(20).thenExecute(()->{
            Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(source.getOwnerName())).findFirst().orElseThrow();
            h.assertTrue(r.food==500&&r.wood==500&&r.ore==500&&source.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.TERRORBEAK_DREAD_ROAR))&&enemy.getHealth()==enemyHealth[0],"disabled interface has no currency/cooldown/damage side effect");
            System.out.println("DREAMSEEKERS_CRAZY_TERRORBEAK_TEAM_FILTER_RESERVED_SKILL_PASS");
        }).thenSucceed();
    }

    private static ShadowSkillRequest request(CrazyTerrorbeakUnit e){return new ShadowSkillRequest(1,UUID.randomUUID(),ShadowSkillRequest.Skill.CRAZY_TERRORBEAK_SPECIAL,new ShadowSkillRequest.Source(e.getId(),e.getUUID(),e.rts().ownershipRevision,e.level().dimension().location()),new ShadowSkillRequest.SelfTarget());}
    private static FakePlayer player(GameTestHelper h,String suffix){return FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes((CrazyTerrorbeakFixture.PREFIX+suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8)),CrazyTerrorbeakFixture.PREFIX+suffix));}
    @GameTest(template="crazy_unit_arena",batch=BATCH,timeoutTicks=350)
    public static void sameTargetExplicitAttackReleasesHoldAndAllianceStopsDamage(GameTestHelper h){var f=new CrazyTerrorbeakFixture(h);var u=f.unit("Fight",5,8);var enemy=f.fox("FightEnemy",13,8);u.setHoldPosition(true);Vec3 start=u.position();float hp=enemy.getHealth();float[] allied={0};var t=new CrazyTerrorbeakFixture.Timeline(h);t.at(30,()->{h.assertTrue(u.getHoldPosition()&&u.getTarget()==enemy&&enemy.getHealth()==hp&&u.position().distanceToSqr(start)<.1,"guard selected enemy without chase");new UnitActionItem(u.getOwnerName(),UnitAction.ATTACK,enemy.getId(),new int[]{u.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());h.assertTrue(!u.getHoldPosition()&&u.getTarget()==enemy,"same target explicit command releases HOLD");});t.at(150,()->{h.assertTrue(enemy.getHealth()<hp&&u.position().distanceToSqr(start)>9,"real pursuit and melee damage against same-faction enemy");AlliancesServerEvents.addAlliance(u.getOwnerName(),enemy.getOwnerName());allied[0]=enemy.getHealth();h.assertTrue(!u.doHurtTarget(enemy),"actual final damage gate observes new alliance immediately");u.setUnitAttackTargetForced(enemy);h.assertTrue(u.getTarget()==null,"forced path does not bypass alliance");});t.at(220,()->h.assertTrue(enemy.getHealth()==allied[0]&&u.getTarget()==null,"no delayed allied damage"));t.start(u,()->f.ready(u,enemy));}
    @GameTest(template="crazy_unit_arena",batch=BATCH,timeoutTicks=320)
    public static void holdAttacksOnlyActualMeleeReachWithoutChasing(GameTestHelper h){var f=new CrazyTerrorbeakFixture(h);var u=f.unit("Hold",5,8);var enemy=f.fox("HoldEnemy",13,8);u.setHoldPosition(true);Vec3 start=u.position();float hp=enemy.getHealth();float[] hit={0};var t=new CrazyTerrorbeakFixture.Timeline(h);t.at(60,()->{h.assertTrue(enemy.getHealth()==hp&&u.position().distanceToSqr(start)<.1,"far enemy never drags HOLD");enemy.moveTo(u.getX()+1.4,u.getY(),u.getZ());});t.at(120,()->{h.assertTrue(enemy.getHealth()<hp&&u.position().distanceToSqr(start)<.15,"in-range guard really deals melee while stationary");hit[0]=enemy.getHealth();enemy.moveTo(u.getX()+8,u.getY(),u.getZ());});t.at(200,()->h.assertTrue(u.getHoldPosition()&&enemy.getHealth()==hit[0]&&u.position().distanceToSqr(start)<.15&&u.getNavigation().isDone(),"enemy leaving range cannot restart pursuit"));t.start(u,()->f.ready(u,enemy));}
    @GameTest(template="crazy_unit_arena",batch=BATCH,timeoutTicks=220)
    public static void followRevocationCancelsOrdinaryPath(GameTestHelper h){var f=new CrazyTerrorbeakFixture(h);var u=f.unit("Follow",3,25);var ally=f.fox("FollowAlly",28,25);AlliancesServerEvents.addAlliance(u.getOwnerName(),ally.getOwnerName());Vec3 start=u.position();Vec3[] stopped={null};var t=new CrazyTerrorbeakFixture.Timeline(h);t.at(0,()->f.target(u,UnitAction.FOLLOW,ally));t.at(35,()->{h.assertTrue(u.position().distanceToSqr(start)>1&&u.getFollowTarget()==ally&&u.distanceTo(ally)>u.getAggroRange()+1,"actual friendly follow outside reacquisition radius");AlliancesServerEvents.removeAlliance(u.getOwnerName(),ally.getOwnerName());});t.at(50,()->stopped[0]=u.position());for(int i=51;i<=110;i++)t.at(i,()->h.assertTrue(u.getFollowTarget()==null&&u.getMoveGoal().getMoveTarget()==null&&u.getTarget()==null&&u.position().distanceToSqr(stopped[0])<.1,"revoked friendly command never revives old path"));t.start(u,()->f.ready(u,ally));}
    @GameTest(template="crazy_unit_arena",batch=BATCH,timeoutTicks=100)
    public static void malformedTargetsAndInteractionsNeverInventEffects(GameTestHelper h){var f=new CrazyTerrorbeakFixture(h);var u=f.unit("Invalid",5,5);var boat=f.place(new net.minecraft.world.entity.vehicle.Boat(h.getLevel(),0,0,0),20,20);var item=f.place(new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),0,0,0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,4)),23,20);var t=new CrazyTerrorbeakFixture.Timeline(h);t.at(0,()->{for(var target:List.of(boat,item))for(var action:List.of(UnitAction.ATTACK,UnitAction.FOLLOW))new UnitActionItem(u.getOwnerName(),action,target.getId(),new int[]{u.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());new UnitActionItem(u.getOwnerName(),UnitAction.ATTACK,Integer.MAX_VALUE,new int[]{u.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());h.assertTrue(u.getTarget()==null&&u.getFollowTarget()==null,"nonliving and missing targets safely rejected");var p=player(h,"Invalid");p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,3));h.assertTrue(u.mobInteract(p,net.minecraft.world.InteractionHand.MAIN_HAND)==net.minecraft.world.InteractionResult.PASS&&p.getMainHandItem().getCount()==3&&!u.canBeLeashed(p),"no original interaction/transaction/leash side effects");});t.at(35,()->h.assertTrue(!u.canPickUpLoot()&&item.isAlive(),"no native item pickup"));t.start(u,()->f.ready(u,boat,item));}
    @GameTest(template="crazy_unit_arena",batch=BATCH,timeoutTicks=100)
    public static void finalDamageRejectsUnpublishedStunnedFrozenAndOccludedTargets(GameTestHelper h){
        var f=new CrazyTerrorbeakFixture(h);var u=f.unit("Final",5,8);var enemy=f.fox("FinalEnemy",9,8);u.setHoldPosition(true);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ready(u,enemy),"actual final-damage fixtures")).thenExecute(()->{
            float hp=enemy.getHealth();var phantom=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());phantom.setOwnerName(enemy.getOwnerName());phantom.moveTo(enemy.position());float missingHp=phantom.getHealth();
            h.assertTrue(u.canHarm(phantom)&&!u.doHurtTarget(phantom)&&phantom.getHealth()==missingHp,"unpublished same-world hostile cannot be directly damaged");
            for(var effect:java.util.List.of(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get(),com.solegendary.reignofnether.registrars.MobEffectRegistrar.FREEZE.get())){
                h.assertTrue(u.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,30)),"actual new disabling effect accepted");
                h.assertTrue(!u.doHurtTarget(enemy)&&enemy.getHealth()==hp,"same-tick disabled damage rejected before core reset cadence");u.removeEffect(effect);
            }
            for(int z=6;z<=10;z++)for(int y=1;y<=8;y++)f.set(7,y,z,net.minecraft.world.level.block.Blocks.STONE);
            h.assertTrue(!u.hasLineOfSight(enemy)&&!u.doHurtTarget(enemy)&&enemy.getHealth()==hp,"solid wall rejects final direct damage");
            for(int z=6;z<=10;z++)for(int y=1;y<=8;y++)f.set(7,y,z,net.minecraft.world.level.block.Blocks.AIR);
            enemy.moveTo(u.getX()+1.5,u.getY(),u.getZ());enemy.invulnerableTime=0;
            h.assertTrue(u.doHurtTarget(enemy)&&enemy.getHealth()<hp,"published visible enemy ordinary melee positive");
        }).thenSucceed();
    }

}
