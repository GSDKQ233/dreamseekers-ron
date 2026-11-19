// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.*;
import com.dreamseekers.stone.*;
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
public final class StoneThrowerCombatGameTests {
    private static final String BATCH="dreamseekers_stone_combat",PREFIX="StoneCombat_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final Set<net.minecraft.world.level.ChunkPos> TICKETS=new HashSet<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        var owners=PlayerServerEvents.rtsPlayers.stream().map(p->p.name).filter(n->n.startsWith(PREFIX)).toList();
        for(String a:owners)for(String b:owners)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        for(var e:FIXTURES)if(!e.isRemoved())e.discard();FIXTURES.clear();
        List<Entity> bolts=new ArrayList<>();for(Entity entity:level.getAllEntities())if(entity instanceof StoneProjectile bolt
                &&bolt.getOwner() instanceof Unit u&&u.getOwnerName().startsWith(PREFIX))bolts.add(entity);
        bolts.forEach(Entity::discard);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(var c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=520)
    public static void realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutFriendlyCollateral(GameTestHelper h) {
        floor(h);StoneSpiritThrowerUnit source=source(h,"air",4,2,10);
        JellyfishUnit target=target(h,"airEnemy",12,4,10),own=target(h,"air",7,2,10),ally=target(h,"airAlly",9,3,10);
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
            h.assertTrue(h.getLevel().noCollision(source),"actual ground ranged unit never clips");
        });
        sequence.thenExecute(()->{
            h.assertTrue(seen[0]&&travel[0]>1&&target.getHealth()<hp&&!target.isOnFire(),"actual ranged goal launches, travels, then damages an airborne enemy");
            h.assertTrue(source.position().distanceToSqr(start[0])<.25,"HOLD shoots in range without chasing");
            System.out.println("DREAMSEEKERS_STONE_SPIRIT_THROWER_TRUE_RANGED_AIR_TEAM_FILTER_PASS travel="+travel[0]+" hp="+target.getHealth());
        }).thenWaitUntil(()->h.assertTrue(bolts(h).stream().noneMatch(b->b.getOwner()==source),"wait actual released bolts finish before out-of-range hold check"))
            .thenExecute(()->{afterAir[0]=target.getHealth();BlockPos p=h.absolutePos(new BlockPos(21,4,10));target.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);})
            .thenIdle(70).thenExecute(()->{
                h.assertTrue(source.position().distanceToSqr(start[0])<.25&&source.getNavigation().isDone()&&bolts(h).stream().noneMatch(b->b.getOwner()==source),"HOLD does not pursue or release new bolts at the now out-of-range enemy");
                BlockPos p=h.absolutePos(new BlockPos(12,4,10));target.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);
            }).thenIdle(90).thenExecute(()->h.assertTrue(target.getHealth()<afterAir[0],"same held real goal resumes legal in-range physical fire")).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=280)
    public static void allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(GameTestHelper h) {
        floor(h);StoneSpiritThrowerUnit source=source(h,"epoch",4,2,10);JellyfishUnit target=target(h,"epochEnemy",12,2,10);float hp=target.getHealth();float[] beforeDeath={0};
        ready(h,source,target).thenExecute(()->{
            source.performRangedAttack(target,1);h.assertTrue(bolts(h).size()==1,"actual one direct addon ranged release uses only addon bolt");
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
        }).thenIdle(25).thenExecute(()->h.assertTrue(source.isRemoved()&&source.deathTime>=24&&bolts(h).isEmpty()&&target.getHealth()==beforeDeath[0],"actual caster death invalidates in-flight bolt before damage")).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void freshLineOfSightRangeAndDisableEffectsGateEveryRelease(GameTestHelper h) {
        floor(h);StoneSpiritThrowerUnit source=source(h,"release",4,2,10);JellyfishUnit target=target(h,"releaseEnemy",12,2,10),far=target(h,"far",21,2,10);
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
        floor(h);StoneSpiritThrowerUnit source=source(h,"spent",4,2,10);JellyfishUnit enemy=target(h,"spentEnemy",12,2,10),own=target(h,"spent",7,2,10);
        ready(h,source,enemy,own).thenExecute(()->{
            float hp=enemy.getHealth();StoneProjectile invalid=new StoneProjectile(DreamseekersMod.STONE_SPIRIT_THROWER_BOLT.get(),h.getLevel());invalid.arm(source,enemy);
            invalid.onHitEntity(new EntityHitResult(own));invalid.arm(source,enemy);invalid.onHitEntity(new EntityHitResult(enemy));
            h.assertTrue(enemy.getHealth()==hp&&invalid.isRemoved(),"invalid first impact consumes permanently; removed projectile cannot hurt later");
            StoneProjectile live=new StoneProjectile(DreamseekersMod.STONE_SPIRIT_THROWER_BOLT.get(),h.getLevel());live.arm(source,enemy);CompoundTag tag=new CompoundTag();live.saveWithoutId(tag);
            StoneProjectile loaded=new StoneProjectile(DreamseekersMod.STONE_SPIRIT_THROWER_BOLT.get(),h.getLevel());loaded.load(tag);loaded.arm(source,enemy);loaded.onHitEntity(new EntityHitResult(enemy));
            h.assertTrue(enemy.getHealth()==hp,"NBT load is permanently unarmed despite manual attempt to rearm");
            StoneProjectile hit=new StoneProjectile(DreamseekersMod.STONE_SPIRIT_THROWER_BOLT.get(),h.getLevel());hit.arm(source,enemy);hit.onHitEntity(new EntityHitResult(enemy));float after=enemy.getHealth();
            h.assertTrue(after<hp,"valid direct impact positive control");enemy.invulnerableTime=0;hit.arm(source,enemy);hit.onHitEntity(new EntityHitResult(enemy));
            h.assertTrue(enemy.getHealth()==after,"a spent valid impact cannot apply twice, independent of hurt cooldown");
        }).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=280)
    public static void lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(GameTestHelper h) throws Exception {
        floor(h);StoneSpiritThrowerUnit source=source(h,"windup",4,2,10);JellyfishUnit target=target(h,"windupEnemy",12,2,10);
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
        floor(h);StoneSpiritThrowerUnit source=source(h,"stopWindup",4,2,10);JellyfishUnit target=target(h,"stopWindupEnemy",12,2,10);
        var field=UnitRangedAttackGoal.class.getDeclaredField("attackWindupTicksLeft");field.setAccessible(true);
        ready(h,source,target).thenExecute(()->{source.setNoAi(false);source.setUnitAttackTarget(target);source.setHoldPosition(true);})
            .thenWaitUntil(()->{try {h.assertTrue(field.getInt(source.getAttackGoal())==1,"observe last actual windup tick");}catch(IllegalAccessException e){throw new RuntimeException(e);}})
            .thenExecute(()->{
                h.assertTrue(bolts(h).isEmpty(),"no released projectile yet");
                new UnitActionItem(source.getOwnerName(),UnitAction.STOP,-1,new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                try {h.assertTrue(field.getInt(source.getAttackGoal())==StoneThrowerBalance.WINDUP_TICKS,"actual STOP immediately cancels old ranged windup");}catch(IllegalAccessException e){throw new RuntimeException(e);}
                new UnitActionItem(source.getOwnerName(),UnitAction.ATTACK,target.getId(),new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());source.setHoldPosition(true);
            }).thenIdle(4).thenExecute(()->h.assertTrue(bolts(h).isEmpty()&&target.getHealth()==target.getMaxHealth(),"same-tick retarget cannot reuse the old final windup tick"))
            .thenIdle(80).thenExecute(()->h.assertTrue(target.getHealth()<target.getMaxHealth(),"full new windup eventually fires an ordinary projectile"))
            .thenWaitUntil(()->h.assertTrue(((UnitRangedAttackGoal<?>)source.getAttackGoal()).getAttackCooldown()>0,"observe a real post-release attack cooldown"))
            .thenExecute(()->{float before=((UnitRangedAttackGoal<?>)source.getAttackGoal()).getAttackCooldown();
                new UnitActionItem(source.getOwnerName(),UnitAction.STOP,-1,new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                h.assertTrue(((UnitRangedAttackGoal<?>)source.getAttackGoal()).getAttackCooldown()==before,"STOP never resets or discounts real attack cooldown");
            }).thenSucceed();
    }



    private static net.minecraft.gametest.framework.GameTestSequence ready(GameTestHelper h,Entity... entities) {
        return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"wait real ticks and both publication lookups");});
    }
    private static String owner(String suffix) {String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));}return name;}
    private static StoneSpiritThrowerUnit source(GameTestHelper h,String suffix,int x,int y,int z) {StoneSpiritThrowerUnit e=DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get().create(h.getLevel());e.setOwnerName(owner(suffix));e.setNoAi(true);place(h,e,x,1,z);return e;}
    private static JellyfishUnit target(GameTestHelper h,String suffix,int x,int y,int z) {JellyfishUnit e=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());e.setOwnerName(owner(suffix));e.setNoAi(true);place(h,e,x,y,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int y,int z) {BlockPos p=h.absolutePos(new BlockPos(x,y,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"real entity registration");}
    private static List<StoneProjectile> bolts(GameTestHelper h) {BlockPos p=h.absolutePos(BlockPos.ZERO);return h.getLevel().getEntitiesOfClass(StoneProjectile.class,new AABB(p,p.offset(24,10,24)));}
    private static void set(GameTestHelper h,int x,int y,int z,net.minecraft.world.level.block.Block b) {h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),b.defaultBlockState(),2);}
    private static void floor(GameTestHelper h) {
        BlockPos p=h.absolutePos(BlockPos.ZERO);
        for(int cx=p.getX()>>4;cx<=(p.getX()+23)>>4;cx++)for(int cz=p.getZ()>>4;cz<=(p.getZ()+23)>>4;cz++){var c=new net.minecraft.world.level.ChunkPos(cx,cz);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(cx,cz,true);TICKETS.add(c);}}
        for(int x=0;x<24;x++)for(int z=0;z<24;z++)set(h,x,10,z,Blocks.STONE);
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p,p.offset(24,h.getLevel().getMaxBuildHeight()-p.getY(),24))).forEach(Entity::discard);
        for(int x=-1;x<=24;x++)for(int z=-1;z<=24;z++)if(x==-1||x==24||z==-1||z==24)for(int y=0;y<=10;y++)set(h,x,y,z,Blocks.STONE);
        for(int x=0;x<24;x++)for(int z=0;z<24;z++){set(h,x,0,z,Blocks.STONE);for(int y=1;y<10;y++)set(h,x,y,z,Blocks.AIR);}
    }
}
