// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.*;
import com.dreamseekers.bonewing.*;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Physical projectile flight + actual server goals, not an assertion that calling hurt is ranged combat. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoneWingCombatGameTests {
    private static final String BATCH="dreamseekers_bone_wing_combat",PREFIX="BoneCombat_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final Set<net.minecraft.world.level.ChunkPos> TICKETS=new HashSet<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        var owners=PlayerServerEvents.rtsPlayers.stream().map(p->p.name).filter(n->n.startsWith(PREFIX)).toList();
        for(String a:owners)for(String b:owners)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        for(var e:FIXTURES)if(!e.isRemoved())e.discard();FIXTURES.clear();
        List<Entity> bolts=new ArrayList<>();for(Entity entity:level.getAllEntities())if(entity instanceof BoneWingBolt bolt
                &&bolt.getOwner() instanceof Unit u&&u.getOwnerName().startsWith(PREFIX))bolts.add(entity);
        bolts.forEach(Entity::discard);
        for(String owner:owners)com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(var c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=220)
    public static void researchedPhysicalFlightAirOnlyAndLaunchSnapshot(GameTestHelper h){
        floor(h);var source=source(h,"Research",4,2,10);var air=target(h,"ResearchEnemy",12,2,10);
        var ground=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());ground.setOwnerName(owner("ResearchEnemy"));ground.setNoAi(true);place(h,ground,12,1,16);
        float[] hp={0};
        ready(h,source,air,ground).thenExecute(()->{
            source.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);
            if(source.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)!=null)
                source.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(10);
            var critical=source.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.CRITICAL_HIT_CHANCE.get());
            if(critical!=null)critical.setBaseValue(0);
            h.assertTrue(Math.abs(source.getUnitAttackDamage()-10)<.001,"fixture sets the real RTS attack attribute to10");
            for(var target:List.of(air,ground)){
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
                var resist=target.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.RANGED_DAMAGE_RESIST.get());if(resist!=null)resist.setBaseValue(0);
                target.setAbsorptionAmount(0);
            }
            hp[0]=air.getHealth();source.performRangedAttack(air,1);h.assertTrue(bolts(h).size()==1,"actual unresearched projectile published");
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(source.getOwnerName(),com.dreamseekers.unitprogression.ExpansionResearch.AIR_SUPERIORITY_ID);
            source.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(20);
            h.assertTrue(Math.abs(source.getUnitAttackDamage()-20)<.001,"live source attack changes after launch, not projectile snapshot");
        }).thenIdle(35).thenExecute(()->{
            h.assertTrue(Math.abs(hp[0]-air.getHealth()-10)<.001&&bolts(h).isEmpty(),"already released flight retains10 despite research and live attack20: before="+hp[0]+", after="+air.getHealth()+", delta="+(hp[0]-air.getHealth())+", bolts="+bolts(h).size());
            source.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);
            h.assertTrue(air.isFlyingUnit(),"fixture remains a formal RTS flying victim");
            h.assertTrue(com.dreamseekers.unitprogression.ExpansionResearch.unlocked(source.getOwnerName(),com.dreamseekers.unitprogression.ExpansionResearch.AIR_SUPERIORITY_ID,false),"research is actually unlocked for supported owner before second launch");
            hp[0]=air.getHealth();System.out.println("API61_AIR_RESEARCH_LAUNCH before="+hp[0]+" attack="+source.getUnitAttackDamage()+" source="+source.position()+" target="+air.position());
            source.performRangedAttack(air,1);h.assertTrue(bolts(h).size()==1,"new researched projectile published");
        }).thenIdle(35).thenExecute(()->{
            System.out.println("API61_AIR_RESEARCH_IMPACT before="+hp[0]+" after="+air.getHealth()+" delta="+(hp[0]-air.getHealth())+" bolts="+bolts(h).size()+" source="+source.position()+" target="+air.position()+" flying="+air.isFlyingUnit());
            h.assertTrue(Math.abs(hp[0]-air.getHealth()-12)<.001&&bolts(h).isEmpty(),"actual air collision gains precisely20percent: before="+hp[0]+", after="+air.getHealth()+", delta="+(hp[0]-air.getHealth())+", bolts="+bolts(h).size());
            var p=h.absolutePos(new BlockPos(12,1,10));ground.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);air.discard();hp[0]=ground.getHealth();
            source.performRangedAttack(ground,1);h.assertTrue(bolts(h).size()==1,"actual researched ground-target flight");
        }).thenIdle(35).thenExecute(()->{
            h.assertTrue(Math.abs(hp[0]-ground.getHealth()-10)<.001&&bolts(h).isEmpty(),"ground collision remains ordinary10 damage");h.succeed();
        });
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=520)
    public static void realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutFriendlyCollateral(GameTestHelper h) {
        floor(h);BoneWingUnit source=source(h,"air",4,2,10);
        JellyfishUnit target=target(h,"airEnemy",12,4,10),own=target(h,"air",7,3,10),ally=target(h,"airAlly",9,3,10);
        AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());
        float hp=target.getHealth(),ownHp=own.getHealth(),allyHp=ally.getHealth();float[] afterAir={hp};boolean[] seen={false};double[] travel={0};Vec3[] start={null};
        var sequence=ready(h,source,target,own,ally).thenExecute(()->{
            h.assertTrue(source.getAttackGoal() instanceof UnitRangedAttackGoal&&source.getAttackBuildingGoal()==null&&!source.canAttackBuildings(),"real ranged goal, no inherited airborne melee siege");
            source.setNoAi(false);new UnitActionItem(source.getOwnerName(),UnitAction.ATTACK,target.getId(),new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());source.setHoldPosition(true);start[0]=source.position();
            h.assertTrue(target.getHealth()==hp,"order does not instant-hurt target");
        });
        for(int i=0;i<120;i++)sequence.thenIdle(1).thenExecute(()->{
            for(var bolt:bolts(h))if(bolt.getOwner()==source){seen[0]=true;travel[0]=Math.max(travel[0],bolt.position().distanceTo(start[0]));h.assertTrue(!bolt.noPhysics&&!bolt.isOnFire(),"live physical bolt has normal collision and no original fire");}
            h.assertTrue(own.getHealth()==ownHp&&ally.getHealth()==allyHp&&!own.isOnFire()&&!ally.isOnFire(),"own/allied bodies are never collateral targets");
            h.assertTrue(h.getLevel().noCollision(source),"actual low-altitude ranged unit never clips");
        });
        sequence.thenExecute(()->{
            h.assertTrue(seen[0]&&travel[0]>1&&target.getHealth()<hp&&!target.isOnFire(),"actual ranged goal launches, travels, then damages an airborne enemy");
            h.assertTrue(source.position().distanceToSqr(start[0])<.25,"HOLD shoots in range without chasing");
            System.out.println("DREAMSEEKERS_BONE_WING_TRUE_RANGED_AIR_TEAM_FILTER_PASS travel="+travel[0]+" hp="+target.getHealth());
        }).thenWaitUntil(()->h.assertTrue(bolts(h).stream().noneMatch(b->b.getOwner()==source),"wait actual released bolts finish before out-of-range hold check"))
            .thenExecute(()->{afterAir[0]=target.getHealth();BlockPos p=h.absolutePos(new BlockPos(21,4,10));target.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);})
            .thenIdle(70).thenExecute(()->{
                h.assertTrue(source.position().distanceToSqr(start[0])<.25&&source.getNavigation().isDone()&&bolts(h).stream().noneMatch(b->b.getOwner()==source),"HOLD does not pursue or release new bolts at the now out-of-range enemy");
                BlockPos p=h.absolutePos(new BlockPos(12,4,10));target.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);
            }).thenIdle(90).thenExecute(()->h.assertTrue(target.getHealth()<afterAir[0],"same held real goal resumes legal in-range physical fire")).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=280)
    public static void allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(GameTestHelper h) {
        floor(h);BoneWingUnit source=source(h,"epoch",4,2,10);JellyfishUnit target=target(h,"epochEnemy",12,2,10);float hp=target.getHealth();float[] beforeDeath={0};
        ready(h,source,target).thenExecute(()->{
            source.performRangedAttack(target,1);h.assertTrue(bolts(h).size()==1,"actual one direct native ranged release uses only addon bolt");
            AlliancesServerEvents.addAlliance(source.getOwnerName(),target.getOwnerName());
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(target.getHealth()==hp&&!target.isOnFire(),"new alliance before physical impact cancels damage");
            AlliancesServerEvents.removeAlliance(source.getOwnerName(),target.getOwnerName());
            source.performRangedAttack(target,1);String owner=source.getOwnerName();source.setOwnerName(owner+"Temporary");source.setOwnerName(owner);
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(target.getHealth()==hp,"owner ABA invalidates old projectile epoch even when final owner string matches");
            source.performRangedAttack(target,1);
            new UnitActionItem(source.getOwnerName(),UnitAction.STOP,-1,new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
            h.assertTrue(source.getTarget()==null&&source.getMoveGoal().getMoveTarget()==null,"STOP immediately clears existing combat and movement orders");
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(target.getHealth()<hp,"ordinary STOP does not retract a previously legal physical projectile");
            h.assertTrue(source.getMoveGoal().getMoveTarget()==null,"in-flight projectile does not reinstate stale movement; ordinary idle enemy reacquisition is allowed");
            beforeDeath[0]=target.getHealth();source.performRangedAttack(target,1);h.assertTrue(bolts(h).size()==1,"one genuine physical bolt released before caster death");
            source.hurt(source.damageSources().genericKill(),999);
        }).thenIdle(25).thenExecute(()->h.assertTrue(source.isRemoved()&&source.deathTime>=20&&bolts(h).isEmpty()&&target.getHealth()==beforeDeath[0],"actual caster death invalidates in-flight bolt before damage")).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void freshLineOfSightRangeAndDisableEffectsGateEveryRelease(GameTestHelper h) {
        floor(h);BoneWingUnit source=source(h,"release",4,2,10);JellyfishUnit target=target(h,"releaseEnemy",12,2,10),far=target(h,"far",21,2,10);
        ready(h,source,target,far).thenExecute(()->{
            source.performRangedAttack(far,1);h.assertTrue(bolts(h).isEmpty(),"fresh range rejects native direct release");
            for(int z=8;z<=12;z++)for(int y=1;y<=6;y++)set(h,8,y,z,Blocks.STONE);
            source.performRangedAttack(target,1);h.assertTrue(bolts(h).isEmpty(),"fresh ray prevents fire through a newly built solid wall");
            for(int z=8;z<=12;z++)for(int y=1;y<=6;y++)set(h,8,y,z,Blocks.AIR);
            for(var effect:List.of(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get(),com.solegendary.reignofnether.registrars.MobEffectRegistrar.FREEZE.get())) {
                source.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,80));source.performRangedAttack(target,1);
                h.assertTrue(bolts(h).isEmpty(),"stun/freeze cannot leak a last-windup or direct native shot");source.removeEffect(effect);
            }
            source.performRangedAttack(target,1);h.assertTrue(bolts(h).size()==1,"same source resumes a legal release after controls clear");
            for(int z=8;z<=12;z++)for(int y=1;y<=6;y++)set(h,8,y,z,Blocks.STONE);
        }).thenIdle(40).thenExecute(()->{
            h.assertTrue(target.getHealth()==target.getMaxHealth()&&!target.isOnFire()&&bolts(h).isEmpty(),"wall introduced after launch stops physical projectile without damage/AOE");
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(8,2,10))).is(Blocks.STONE),"impact never destroys world blocks");
        }).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=180)
    public static void spentInvalidAndReloadedProjectilesCannotBeRearmedOrDamageTwice(GameTestHelper h) {
        floor(h);BoneWingUnit source=source(h,"spent",4,2,10);JellyfishUnit enemy=target(h,"spentEnemy",12,2,10),own=target(h,"spent",7,2,10);
        ready(h,source,enemy,own).thenExecute(()->{
            float hp=enemy.getHealth();BoneWingBolt invalid=new BoneWingBolt(DreamseekersMod.BONE_WING_BOLT.get(),h.getLevel());invalid.arm(source,enemy);
            invalid.onHitEntity(new EntityHitResult(own));invalid.arm(source,enemy);invalid.onHitEntity(new EntityHitResult(enemy));
            h.assertTrue(enemy.getHealth()==hp&&invalid.isRemoved(),"invalid first impact consumes permanently; removed projectile cannot hurt later");
            BoneWingBolt live=new BoneWingBolt(DreamseekersMod.BONE_WING_BOLT.get(),h.getLevel());live.arm(source,enemy);CompoundTag tag=new CompoundTag();live.saveWithoutId(tag);
            BoneWingBolt loaded=new BoneWingBolt(DreamseekersMod.BONE_WING_BOLT.get(),h.getLevel());loaded.load(tag);loaded.arm(source,enemy);loaded.onHitEntity(new EntityHitResult(enemy));
            h.assertTrue(enemy.getHealth()==hp,"NBT load is permanently unarmed despite manual attempt to rearm");
            BoneWingBolt hit=new BoneWingBolt(DreamseekersMod.BONE_WING_BOLT.get(),h.getLevel());hit.arm(source,enemy);hit.onHitEntity(new EntityHitResult(enemy));float after=enemy.getHealth();
            h.assertTrue(after<hp,"valid direct impact positive control");enemy.invulnerableTime=0;hit.arm(source,enemy);hit.onHitEntity(new EntityHitResult(enemy));
            h.assertTrue(enemy.getHealth()==after,"a spent valid impact cannot apply twice, independent of hurt cooldown");
        }).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=280)
    public static void lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(GameTestHelper h) throws Exception {
        floor(h);BoneWingUnit source=source(h,"windup",4,2,10);JellyfishUnit target=target(h,"windupEnemy",12,2,10);
        var field=UnitRangedAttackGoal.class.getDeclaredField("attackWindupTicksLeft");field.setAccessible(true);
        ready(h,source,target).thenExecute(()->{source.setNoAi(false);source.setUnitAttackTarget(target);source.setHoldPosition(true);})
            .thenWaitUntil(()->{
                try {h.assertTrue(field.getInt(source.getAttackGoal())==1,"await actual last windup tick, never manually tick goal");}
                catch(IllegalAccessException e){throw new RuntimeException(e);}
            }).thenExecute(()->{
                h.assertTrue(bolts(h).isEmpty()&&target.getHealth()==target.getMaxHealth(),"no projectile before final observed windup");
                source.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get(),60));
            }).thenIdle(20).thenExecute(()->{
                h.assertTrue(bolts(h).isEmpty()&&target.getHealth()==target.getMaxHealth(),"new stun blocks last windup release, not merely later target cleanup");
                source.removeEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get());source.setUnitAttackTarget(target);source.setHoldPosition(true);
            }).thenIdle(90).thenExecute(()->h.assertTrue(target.getHealth()<target.getMaxHealth(),"same true ranged goal recovers after stun ends")).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=280)
    public static void stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(GameTestHelper h) throws Exception {
        floor(h);BoneWingUnit source=source(h,"stopWindup",4,2,10);JellyfishUnit target=target(h,"stopWindupEnemy",12,2,10);
        var field=UnitRangedAttackGoal.class.getDeclaredField("attackWindupTicksLeft");field.setAccessible(true);
        ready(h,source,target).thenExecute(()->{source.setNoAi(false);source.setUnitAttackTarget(target);source.setHoldPosition(true);})
            .thenWaitUntil(()->{try {h.assertTrue(field.getInt(source.getAttackGoal())==1,"observe last actual windup tick");}catch(IllegalAccessException e){throw new RuntimeException(e);}})
            .thenExecute(()->{
                h.assertTrue(bolts(h).isEmpty(),"no released projectile yet");
                new UnitActionItem(source.getOwnerName(),UnitAction.STOP,-1,new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                try {h.assertTrue(field.getInt(source.getAttackGoal())==BoneWingBalance.WINDUP_TICKS,"actual STOP immediately cancels old ranged windup");}catch(IllegalAccessException e){throw new RuntimeException(e);}
                new UnitActionItem(source.getOwnerName(),UnitAction.ATTACK,target.getId(),new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());source.setHoldPosition(true);
            }).thenIdle(4).thenExecute(()->h.assertTrue(bolts(h).isEmpty()&&target.getHealth()==target.getMaxHealth(),"same-tick retarget cannot reuse the old final windup tick"))
            .thenIdle(80).thenExecute(()->h.assertTrue(target.getHealth()<target.getMaxHealth(),"full new windup eventually fires an ordinary projectile"))
            .thenWaitUntil(()->h.assertTrue(((UnitRangedAttackGoal<?>)source.getAttackGoal()).getAttackCooldown()>0,"observe a real post-release attack cooldown"))
            .thenExecute(()->{float before=((UnitRangedAttackGoal<?>)source.getAttackGoal()).getAttackCooldown();
                new UnitActionItem(source.getOwnerName(),UnitAction.STOP,-1,new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                h.assertTrue(((UnitRangedAttackGoal<?>)source.getAttackGoal()).getAttackCooldown()==before,"STOP never resets or discounts real attack cooldown");
            }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=140)
    public static void nativeImpulseWildControlPersistenceInteractionsAndDeathAreIsolated(GameTestHelper h) {
        floor(h);BoneWingUnit source=source(h,"native",5,3,5);
        var wild=net.pasterdream.init.PasterdreamModEntities.BONE_WING.get().create(h.getLevel());
        BlockPos wildPos=h.absolutePos(new BlockPos(17,3,17));wild.moveTo(wildPos.getX()+.5,wildPos.getY(),wildPos.getZ()+.5);
        Vec3 motion=new Vec3(.12,0,.13);boolean changed=false;String owner=source.getOwnerName();
        for(String name:List.of(owner,"","Other")) {
            source.setOwnerName(name);
            for(int i=0;i<100;i++) {
                source.setDeltaMovement(motion);net.pasterdream.procedures.BoneWingPr0Procedure.execute(h.getLevel(),source.getX(),source.getY(),source.getZ(),source);
                h.assertTrue(source.getDeltaMovement().equals(motion),"paid native impulse isolated by subtype even empty/changed owner");
                wild.setDeltaMovement(Vec3.ZERO);net.pasterdream.procedures.BoneWingPr0Procedure.execute(h.getLevel(),wild.getX(),wild.getY(),wild.getZ(),wild);
                if(wild.getDeltaMovement().y>.05)changed=true;
            }
        }
        net.minecraft.world.Difficulty oldDifficulty=h.getLevel().getDifficulty();
        try {
            h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
            source.checkDespawn();wild.checkDespawn();
            h.assertTrue(source.isAlive()&&!source.isRemoved()&&wild.isRemoved(),"real peaceful despawn protects only paid subtype; wild Monster still despawns");
        } finally {h.getLevel().getServer().setDifficulty(oldDifficulty,true);}
        h.assertTrue(changed&&!(wild instanceof Unit),"original wild up-float remains active and is not free RTS unit");source.setOwnerName(owner);source.setDeltaMovement(Vec3.ZERO);
        CompoundTag tag=new CompoundTag();source.saveWithoutId(tag);tag.putBoolean("NoAI",true);tag.putBoolean("CanPickUpLoot",true);
        BoneWingUnit loaded=DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel());loaded.load(tag);
        h.assertTrue(loaded.getUUID().equals(source.getUUID())&&loaded.getOwnerName().equals(owner)&&!loaded.isNoAi()&&loaded.isNoGravity()
                &&loaded.isPersistenceRequired()&&!loaded.canPickUpLoot()&&loaded.getExperienceReward()==0,"NBT preserves identity while restoring safe active paid flags");
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),owner));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE,3));
        h.assertTrue(source.mobInteract(player,net.minecraft.world.InteractionHand.MAIN_HAND)==net.minecraft.world.InteractionResult.PASS
                &&player.getMainHandItem().getCount()==3&&!source.canBeLeashed(player),"no native item consumption, tame/reward/leash interaction");
        var request=new com.dreamseekers.skill.ReservedSkillRequest(com.dreamseekers.skill.ReservedSkillRequest.Skill.BONE_WING_SPECIAL,source.getId(),source.getUUID(),new com.dreamseekers.skill.ReservedSkillRequest.Self());
        ready(h,source).thenExecute(()->{
            int cooldown=source.rts().skills().cooldownTicks(com.dreamseekers.skill.UnitSkill.COORDINATED_MARK);
            h.assertTrue(com.dreamseekers.skill.ReservedSkillService.request(player,request)==com.dreamseekers.skill.ReservedSkillService.Result.DISABLED
                    &&source.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.COORDINATED_MARK)),"old typed native special remains disabled; only the separately approved coordinated mark is exposed");
            Resources resources=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(owner)).findFirst().orElseThrow();
            h.assertTrue(resources.food==500&&resources.wood==500&&resources.ore==500
                    &&source.rts().skills().cooldownTicks(com.dreamseekers.skill.UnitSkill.COORDINATED_MARK)==cooldown
                    &&com.dreamseekers.skill.CoordinatedMark.ticksRemaining(source,owner)==0,"disabled reserved request grants no resources, mark or paid skill cooldown mutation");
            h.assertTrue(source.hurt(source.damageSources().generic(),3),"paid combat unit accepts ordinary damage");source.invulnerableTime=0;
            source.setLastHurtByPlayer(player);source.hurt(source.damageSources().genericKill(),999);player.getInventory().clearContent();
        }).thenWaitUntil(()->h.assertTrue(source.isRemoved()&&source.deathTime>=20,"real native twenty-tick death, not manual death callback"))
            .thenExecute(()->{
                BlockPos p=h.absolutePos(BlockPos.ZERO);AABB area=new AABB(p,p.offset(24,10,24));
                h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).isEmpty()
                        &&h.getLevel().getEntitiesOfClass(ExperienceOrb.class,area).isEmpty(),"attributed native death gives no original drops or XP");
            }).thenSucceed();
    }

    private static net.minecraft.gametest.framework.GameTestSequence ready(GameTestHelper h,Entity... entities) {
        return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"wait real ticks and both publication lookups");});
    }
    private static String owner(String suffix) {String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));}return name;}
    private static BoneWingUnit source(GameTestHelper h,String suffix,int x,int y,int z) {BoneWingUnit e=DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel());e.setOwnerName(owner(suffix));e.setNoAi(true);place(h,e,x,y,z);return e;}
    private static JellyfishUnit target(GameTestHelper h,String suffix,int x,int y,int z) {JellyfishUnit e=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());e.setOwnerName(owner(suffix));e.setNoAi(true);place(h,e,x,y,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int y,int z) {BlockPos p=h.absolutePos(new BlockPos(x,y,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"real entity registration");}
    private static List<BoneWingBolt> bolts(GameTestHelper h) {BlockPos p=h.absolutePos(BlockPos.ZERO);return h.getLevel().getEntitiesOfClass(BoneWingBolt.class,new AABB(p,p.offset(24,10,24)));}
    private static void set(GameTestHelper h,int x,int y,int z,net.minecraft.world.level.block.Block b) {h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),b.defaultBlockState(),2);}
    private static void floor(GameTestHelper h) {
        BlockPos p=h.absolutePos(BlockPos.ZERO);
        for(int cx=p.getX()>>4;cx<=(p.getX()+23)>>4;cx++)for(int cz=p.getZ()>>4;cz<=(p.getZ()+23)>>4;cz++){var c=new net.minecraft.world.level.ChunkPos(cx,cz);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(cx,cz,true);TICKETS.add(c);}}
        for(int x=0;x<24;x++)for(int z=0;z<24;z++)set(h,x,10,z,Blocks.STONE);
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p,p.offset(24,h.getLevel().getMaxBuildHeight()-p.getY(),24))).forEach(Entity::discard);
        for(int x=0;x<24;x++)for(int z=0;z<24;z++){set(h,x,0,z,Blocks.STONE);for(int y=1;y<10;y++)set(h,x,y,z,Blocks.AIR);}
    }
}
