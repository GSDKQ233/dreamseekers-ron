// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.*;
import com.dreamseekers.shadow.*;
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
public final class AshBoneWingCombatGameTests {
    private static final String BATCH="dreamseekers_ash_combat",PREFIX="AshCombat_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final Set<net.minecraft.world.level.ChunkPos> TICKETS=new HashSet<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        var owners=PlayerServerEvents.rtsPlayers.stream().map(p->p.name).filter(n->n.startsWith(PREFIX)).toList();
        for(String a:owners)for(String b:owners)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        for(var e:FIXTURES)if(!e.isRemoved())e.discard();FIXTURES.clear();
        List<Entity> bolts=new ArrayList<>();for(Entity entity:level.getAllEntities())if(entity instanceof AshBoneWingBolt bolt
                &&bolt.getOwner() instanceof Unit u&&u.getOwnerName().startsWith(PREFIX))bolts.add(entity);
        bolts.forEach(Entity::discard);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(var c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @GameTest(template="ash_unit_arena",batch=BATCH,timeoutTicks=520)
    public static void realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutAshCollateral(GameTestHelper h) {
        floor(h);AshBoneWingUnit source=source(h,"air",4,2,10);
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
            System.out.println("DREAMSEEKERS_ASH_BONE_WING_TRUE_RANGED_AIR_TEAM_FILTER_PASS travel="+travel[0]+" hp="+target.getHealth());
        }).thenWaitUntil(()->h.assertTrue(bolts(h).stream().noneMatch(b->b.getOwner()==source),"wait actual released bolts finish before out-of-range hold check"))
            .thenExecute(()->{afterAir[0]=target.getHealth();BlockPos p=h.absolutePos(new BlockPos(21,4,10));target.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);})
            .thenIdle(70).thenExecute(()->{
                h.assertTrue(source.position().distanceToSqr(start[0])<.25&&source.getNavigation().isDone()&&bolts(h).stream().noneMatch(b->b.getOwner()==source),"HOLD does not pursue or release new bolts at the now out-of-range enemy");
                BlockPos p=h.absolutePos(new BlockPos(12,4,10));target.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);
            }).thenIdle(90).thenExecute(()->h.assertTrue(target.getHealth()<afterAir[0],"same held real goal resumes legal in-range physical fire")).thenSucceed();
    }
    @GameTest(template="ash_unit_arena",batch=BATCH,timeoutTicks=280)
    public static void allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(GameTestHelper h) {
        floor(h);AshBoneWingUnit source=source(h,"epoch",4,2,10);JellyfishUnit target=target(h,"epochEnemy",12,2,10);float hp=target.getHealth();float[] beforeDeath={0};
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
    @GameTest(template="ash_unit_arena",batch=BATCH,timeoutTicks=260)
    public static void freshLineOfSightRangeAndDisableEffectsGateEveryRelease(GameTestHelper h) {
        floor(h);AshBoneWingUnit source=source(h,"release",4,2,10);JellyfishUnit target=target(h,"releaseEnemy",12,2,10),far=target(h,"far",21,2,10);
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
    @GameTest(template="ash_unit_arena",batch=BATCH,timeoutTicks=180)
    public static void spentInvalidAndReloadedProjectilesCannotBeRearmedOrDamageTwice(GameTestHelper h) {
        floor(h);AshBoneWingUnit source=source(h,"spent",4,2,10);JellyfishUnit enemy=target(h,"spentEnemy",12,2,10),own=target(h,"spent",7,2,10);
        ready(h,source,enemy,own).thenExecute(()->{
            float hp=enemy.getHealth();AshBoneWingBolt invalid=new AshBoneWingBolt(DreamseekersMod.ASH_BONE_WING_BOLT.get(),h.getLevel());invalid.arm(source,enemy);
            invalid.onHitEntity(new EntityHitResult(own));invalid.arm(source,enemy);invalid.onHitEntity(new EntityHitResult(enemy));
            h.assertTrue(enemy.getHealth()==hp&&invalid.isRemoved(),"invalid first impact consumes permanently; removed projectile cannot hurt later");
            AshBoneWingBolt live=new AshBoneWingBolt(DreamseekersMod.ASH_BONE_WING_BOLT.get(),h.getLevel());live.arm(source,enemy);CompoundTag tag=new CompoundTag();live.saveWithoutId(tag);
            AshBoneWingBolt loaded=new AshBoneWingBolt(DreamseekersMod.ASH_BONE_WING_BOLT.get(),h.getLevel());loaded.load(tag);loaded.arm(source,enemy);loaded.onHitEntity(new EntityHitResult(enemy));
            h.assertTrue(enemy.getHealth()==hp,"NBT load is permanently unarmed despite manual attempt to rearm");
            AshBoneWingBolt hit=new AshBoneWingBolt(DreamseekersMod.ASH_BONE_WING_BOLT.get(),h.getLevel());hit.arm(source,enemy);hit.onHitEntity(new EntityHitResult(enemy));float after=enemy.getHealth();
            h.assertTrue(after<hp,"valid direct impact positive control");enemy.invulnerableTime=0;hit.arm(source,enemy);hit.onHitEntity(new EntityHitResult(enemy));
            h.assertTrue(enemy.getHealth()==after,"a spent valid impact cannot apply twice, independent of hurt cooldown");
        }).thenSucceed();
    }
    @GameTest(template="ash_unit_arena",batch=BATCH,timeoutTicks=280)
    public static void lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(GameTestHelper h) throws Exception {
        floor(h);AshBoneWingUnit source=source(h,"windup",4,2,10);JellyfishUnit target=target(h,"windupEnemy",12,2,10);
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

    @GameTest(template="ash_unit_arena",batch=BATCH,timeoutTicks=280)
    public static void stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(GameTestHelper h) throws Exception {
        floor(h);AshBoneWingUnit source=source(h,"stopWindup",4,2,10);JellyfishUnit target=target(h,"stopWindupEnemy",12,2,10);
        var field=UnitRangedAttackGoal.class.getDeclaredField("attackWindupTicksLeft");field.setAccessible(true);
        ready(h,source,target).thenExecute(()->{source.setNoAi(false);source.setUnitAttackTarget(target);source.setHoldPosition(true);})
            .thenWaitUntil(()->{try {h.assertTrue(field.getInt(source.getAttackGoal())==1,"observe last actual windup tick");}catch(IllegalAccessException e){throw new RuntimeException(e);}})
            .thenExecute(()->{
                h.assertTrue(bolts(h).isEmpty(),"no released projectile yet");
                new UnitActionItem(source.getOwnerName(),UnitAction.STOP,-1,new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                try {h.assertTrue(field.getInt(source.getAttackGoal())==AshBoneWingBalance.WINDUP_TICKS,"actual STOP immediately cancels old ranged windup");}catch(IllegalAccessException e){throw new RuntimeException(e);}
                new UnitActionItem(source.getOwnerName(),UnitAction.ATTACK,target.getId(),new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());source.setHoldPosition(true);
            }).thenIdle(4).thenExecute(()->h.assertTrue(bolts(h).isEmpty()&&target.getHealth()==target.getMaxHealth(),"same-tick retarget cannot reuse the old final windup tick"))
            .thenIdle(80).thenExecute(()->h.assertTrue(target.getHealth()<target.getMaxHealth(),"full new windup eventually fires an ordinary projectile"))
            .thenWaitUntil(()->h.assertTrue(((UnitRangedAttackGoal<?>)source.getAttackGoal()).getAttackCooldown()>0,"observe a real post-release attack cooldown"))
            .thenExecute(()->{float before=((UnitRangedAttackGoal<?>)source.getAttackGoal()).getAttackCooldown();
                new UnitActionItem(source.getOwnerName(),UnitAction.STOP,-1,new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                h.assertTrue(((UnitRangedAttackGoal<?>)source.getAttackGoal()).getAttackCooldown()==before,"STOP never resets or discounts real attack cooldown");
            }).thenSucceed();
    }

    @GameTest(template="ash_unit_arena",batch=BATCH,timeoutTicks=220)
    public static void physicalGroundFireAndOriginalProjectileRemainSeparate(GameTestHelper h) {
        floor(h);var source=source(h,"ground",4,2,5);
        var target=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());target.setOwnerName(owner("groundEnemy"));target.setNoAi(true);place(h,target,12,1,5);
        var wild=net.pasterdream.init.PasterdreamModEntities.ASH_BONE_WING.get().create(h.getLevel());wild.setNoAi(true);place(h,wild,4,2,18);
        var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);place(h,cow,12,1,18);
        float hp=target.getHealth(),wildHp=cow.getHealth();double[] travel={0};boolean[] nativeSeen={false};net.pasterdream.entity.BoneWingFireBallProjectileEntity[] direct={null};double[] nativeTravel={0};
        var q=ready(h,source,target,wild,cow).thenExecute(()->{
            h.assertTrue(!source.doHurtTarget(target)&&target.getHealth()==hp,"ranged subtype never leaks direct native melee");
            source.setNoAi(false);new UnitActionItem(source.getOwnerName(),UnitAction.ATTACK,target.getId(),new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());source.setHoldPosition(true);
            wild.performRangedAttack(cow,1);
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.pasterdream.entity.BoneWingFireBallProjectileEntity.class,wild.getBoundingBox().inflate(3)).stream().anyMatch(w->w.getClass()==net.pasterdream.entity.BoneWingFireBallProjectileEntity.class&&w.getOwner()==wild),"actual original ranged factory emits exact native fireball arrow");
            // Native factory deliberately has inaccuracy12, so a single random shot is not a reliable hit fixture.
            // Separately exercise the unchanged original projectile collision with a deterministic trajectory.
            direct[0]=net.pasterdream.init.PasterdreamModEntities.BONE_WING_FIRE_BALL_PROJECTILE.get().create(h.getLevel());
            direct[0].setOwner(wild);direct[0].setPos(wild.getX(),wild.getEyeY(),wild.getZ());direct[0].setNoGravity(true);direct[0].setBaseDamage(3);
            Vec3 delta=cow.getBoundingBox().getCenter().subtract(direct[0].position());direct[0].shoot(delta.x,delta.y,delta.z,.8f,0);
            h.assertTrue(h.getLevel().addFreshEntity(direct[0]),"genuine native arrow with deterministic test aim enters world");
        });
        for(int i=0;i<100;i++)q.thenIdle(1).thenExecute(()->{
            if(direct[0]!=null)nativeTravel[0]=Math.max(nativeTravel[0],direct[0].distanceTo(wild));
            for(Entity e:h.getLevel().getAllEntities())if(e instanceof net.pasterdream.entity.BoneWingFireBallProjectileEntity wave){
                if(wave.getOwner()==source){h.assertTrue(wave instanceof AshBoneWingBolt,"paid native entry never creates original projectile type");travel[0]=Math.max(travel[0],wave.distanceTo(source));}
                if(wave.getOwner()==wild){h.assertTrue(wave.getClass()==net.pasterdream.entity.BoneWingFireBallProjectileEntity.class,"wild exact original projectile retained");nativeSeen[0]=true;}
            }
        });
        q.thenExecute(()->{h.assertTrue(travel[0]>1&&target.getHealth()<hp&&!target.isOnFire(),"real goal physically hits ground enemy without native fire");h.assertTrue(nativeSeen[0]&&nativeTravel[0]>1&&cow.getHealth()<wildHp,"unchanged native projectile really travels and hurts under deterministic test aim");System.out.println("ASH_REAL_GROUND_AND_NATIVE_FIREBALL_PASS travel="+travel[0]+" paidHp="+target.getHealth()+" wildHp="+cow.getHealth());}).thenSucceed();
    }
    @GameTest(template="ash_unit_arena",batch=BATCH,timeoutTicks=200)
    public static void identityCaptureForgedOwnerAndPortalPickupFailClosed(GameTestHelper h) {
        floor(h);var source=source(h,"identity",4,2,10);var target=target(h,"identityEnemy",12,2,10);var own=target(h,"identity",8,2,18);
        ready(h,source,target,own).thenExecute(()->{
            float hp=target.getHealth();var fake=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());fake.setOwnerName(target.getOwnerName());fake.setUUID(target.getUUID());float fakeHp=fake.getHealth();
            AshBoneWingBolt forged=new AshBoneWingBolt(DreamseekersMod.ASH_BONE_WING_BOLT.get(),h.getLevel());forged.arm(source,target);forged.setOwner(own);forged.onHitEntity(new EntityHitResult(target));h.assertTrue(target.getHealth()==hp,"forged projectile owner denied");
            AshBoneWingBolt staleTarget=new AshBoneWingBolt(DreamseekersMod.ASH_BONE_WING_BOLT.get(),h.getLevel());staleTarget.arm(source,target);staleTarget.onHitEntity(new EntityHitResult(fake));h.assertTrue(fake.getHealth()==fakeHp,"unpublished identity duplicate cannot receive damage");
            AshBoneWingBolt captured=new AshBoneWingBolt(DreamseekersMod.ASH_BONE_WING_BOLT.get(),h.getLevel());captured.arm(source,target);String old=target.getOwnerName();target.setOwnerName(source.getOwnerName());captured.onHitEntity(new EntityHitResult(target));h.assertTrue(target.getHealth()==hp,"captured target protected at impact");target.setOwnerName(old);
            var boat=EntityType.BOAT.create(h.getLevel());var bolt=new AshBoneWingBolt(DreamseekersMod.ASH_BONE_WING_BOLT.get(),h.getLevel());bolt.arm(source,target);
            h.assertTrue(!bolt.startRiding(boat)&&!bolt.startRiding(boat,true)&&!bolt.canChangeDimensions()&&!net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(bolt),"projectile cannot enter portals via itself or a forced root vehicle");bolt.handleInsidePortal(bolt.blockPosition());
            var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AshPickup"));player.getInventory().clearContent();bolt.playerTouch(player);h.assertTrue(player.getInventory().isEmpty()&&bolt.pickup==net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED,"native fireball arrow item cannot be picked up");
            bolt.discard();
        }).thenSucceed();
    }
    @GameTest(template="ash_unit_arena",batch=BATCH,timeoutTicks=160)
    public static void reentrantHurtCannotReplayConsumedProjectile(GameTestHelper h){
        floor(h);var source=source(h,"reentrant",4,2,10);var target=target(h,"reentrantEnemy",12,2,10);
        ready(h,source,target).thenExecute(()->{
            float hp=target.getHealth();AshBoneWingBolt bolt=new AshBoneWingBolt(DreamseekersMod.ASH_BONE_WING_BOLT.get(),h.getLevel());bolt.arm(source,target);int[] calls={0};
            java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingHurtEvent> listener=event->{if(event.getEntity()==target&&event.getSource().getDirectEntity()==bolt){calls[0]++;target.invulnerableTime=0;bolt.onHitEntity(new EntityHitResult(target));}};
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,false,net.minecraftforge.event.entity.living.LivingHurtEvent.class,listener);
            try {bolt.onHitEntity(new EntityHitResult(target));} finally {net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);}
            h.assertTrue(calls[0]==1&&target.getHealth()<hp&&bolt.isRemoved(),"actual hurt event cannot recursively reuse spent projectile");
        }).thenSucceed();
    }

    private static net.minecraft.gametest.framework.GameTestSequence ready(GameTestHelper h,Entity... entities) {
        return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"wait real ticks and both publication lookups");});
    }
    private static String owner(String suffix) {String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));}return name;}
    private static AshBoneWingUnit source(GameTestHelper h,String suffix,int x,int y,int z) {AshBoneWingUnit e=DreamseekersMod.ASH_BONE_WING_UNIT.get().create(h.getLevel());e.setOwnerName(owner(suffix));e.setNoAi(true);place(h,e,x,y,z);return e;}
    private static JellyfishUnit target(GameTestHelper h,String suffix,int x,int y,int z) {JellyfishUnit e=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());e.setOwnerName(owner(suffix));e.setNoAi(true);place(h,e,x,y,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int y,int z) {BlockPos p=h.absolutePos(new BlockPos(x,y,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"real entity registration");}
    private static List<AshBoneWingBolt> bolts(GameTestHelper h) {BlockPos p=h.absolutePos(BlockPos.ZERO);return h.getLevel().getEntitiesOfClass(AshBoneWingBolt.class,new AABB(p,p.offset(24,10,24)));}
    private static void set(GameTestHelper h,int x,int y,int z,net.minecraft.world.level.block.Block b) {h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),b.defaultBlockState(),2);}
    private static void floor(GameTestHelper h) {
        BlockPos p=h.absolutePos(BlockPos.ZERO);
        for(int i=-1;i<=24;i++)for(int y=1;y<=10;y++){set(h,-1,y,i,Blocks.STONE);set(h,24,y,i,Blocks.STONE);set(h,i,y,-1,Blocks.STONE);set(h,i,y,24,Blocks.STONE);}
        for(int cx=p.getX()>>4;cx<=(p.getX()+23)>>4;cx++)for(int cz=p.getZ()>>4;cz<=(p.getZ()+23)>>4;cz++){var c=new net.minecraft.world.level.ChunkPos(cx,cz);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(cx,cz,true);TICKETS.add(c);}}
        for(int x=0;x<24;x++)for(int z=0;z<24;z++)set(h,x,10,z,Blocks.STONE);
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p,p.offset(24,h.getLevel().getMaxBuildHeight()-p.getY(),24))).forEach(Entity::discard);
        for(int x=0;x<24;x++)for(int z=0;z<24;z++){set(h,x,0,z,Blocks.STONE);for(int y=1;y<10;y++)set(h,x,y,z,Blocks.AIR);}
    }
}
