// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.unit.*;
import com.dreamseekers.shadow.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;


import java.util.*;

@GameTestHolder("dreamseekers_shadow_ghost_boundary")
@PrefixGameTestTemplate(false)
public final class ShadowGhostCombatDiagnosticGameTests {
    private static final String BATCH="dreamseekers_shadow_ghost_diagnostic", PREFIX="GhostDiag_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final Set<net.minecraft.world.level.ChunkPos> TICKETS=new HashSet<>();
    private static final List<FakePlayer> PLAYERS=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        var owners=PlayerServerEvents.rtsPlayers.stream().map(p->p.name).filter(n->n.startsWith(PREFIX)).toList();
        for(String a:owners)for(String b:owners)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity e:FIXTURES)if(!e.isRemoved())e.discard();FIXTURES.clear();
        for(FakePlayer p:PLAYERS)p.getInventory().clearContent();PLAYERS.clear();
        for(var c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @GameTest(template="ghost_unit_arena",batch=BATCH,timeoutTicks=460)
    public static void meleeIsRealAndSameFactionEnemiesAreNotAllies(GameTestHelper h) {
        floor(h);ShadowGhostUnit mother=spawn(h,"Melee",5,8);GoldenFoxUnit victim=fox(h,"Target",16,8);
        h.assertTrue(mother.canHarm(victim),"same faction different owners are enemies");
        float health=victim.getHealth();
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,mother)&&published(h,victim),"wait both real entities")).thenExecute(()->{
            Vec3 initial=mother.position();
            h.runAfterDelay(20,()->{
                h.assertTrue(victim.getHealth()==health&&mother.position().distanceToSqr(initial)<.05&&mother.getTarget()==null,"out-of-aggro victim receives no idle automatic attack: hp="+victim.getHealth()+" baseline="+health+" moved="+mother.position().distanceToSqr(initial)+" pos="+mother.position()+" origin="+initial+" target="+mother.getTarget()+" tick="+mother.tickCount+" aggro="+mother.getAggroRange());
                order(mother,UnitAction.ATTACK,victim.getId(),BlockPos.ZERO);
                for(int t=5;t<=220;t+=5){final int elapsed=t;h.runAfterDelay(t,()->trace(elapsed,mother,victim));}
                h.runAfterDelay(220,()->{
                    h.assertTrue(victim.getHealth()<health&&mother.position().distanceToSqr(initial)>9,"actual RTS ATTACK causes chase and melee beyond automatic aggro");
                    AlliancesServerEvents.addAlliance(mother.getOwnerName(),victim.getOwnerName());
                    float alliedHealth=victim.getHealth();
                    h.assertTrue(!mother.doHurtTarget(victim),"final damage guard rejects new alliance immediately");
                    mother.getTargetGoal().setTarget(victim);h.assertTrue(mother.getTarget()==null,"direct forced target cannot bypass alliance");
                    h.runAfterDelay(45,()->{
                        h.assertTrue(victim.getHealth()==alliedHealth&&mother.getTarget()==null,"no delayed hit after alliance change");
                        order(mother,UnitAction.STOP,-1,BlockPos.ZERO);
                        h.runAfterDelay(20,()->{
                            h.assertTrue(mother.getAttackMoveTarget()==null&&mother.getMoveGoal().getMoveTarget()==null,"STOP clears combat move state");
                            System.out.println("DREAMSEEKERS_SHADOW_GHOST_REAL_MELEE_ALLIANCE_RECHECK_PASS");h.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTest(template="ghost_unit_arena",batch=BATCH,timeoutTicks=460)
    public static void sameEnemyExplicitAttackReleasesHoldAndReallyPursues(GameTestHelper h) {
        floor(h);var source=spawn(h,"HeldAttack",5,8);var enemy=fox(h,"HeldEnemy",13,8);var own=fox(h,"HeldAttack",20,18);
        source.setHoldPosition(true);Vec3 initial=source.position();float hp=enemy.getHealth();
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,source)&&published(h,enemy)&&h.getLevel().getEntity(source.getUUID())==source&&h.getLevel().getEntity(enemy.getUUID())==enemy,"real published held combatants"))
            .thenIdle(25).thenExecute(()->{
                h.assertTrue(source.getHoldPosition()&&source.getTarget()==enemy&&source.position().distanceToSqr(initial)<.1&&enemy.getHealth()==hp,"HOLD has already selected this actual enemy but has not chased it");
                source.setUnitAttackTargetForced(own);
                h.assertTrue(source.getHoldPosition()&&source.getTarget()==null,"invalid allied forced target cannot release HOLD");
                source.getTargetGoal().setTarget(enemy);
                h.assertTrue(source.getTarget()==enemy,"restore exactly the same selected enemy before real ATTACK path");
                new com.solegendary.reignofnether.unit.UnitActionItem(source.getOwnerName(),UnitAction.ATTACK,enemy.getId(),new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                for(int t=5;t<=220;t+=5){final int elapsed=t;h.runAfterDelay(t,()->trace(elapsed,source,enemy));}
                System.out.println("GHOST_SAME_TARGET_ATTACK_AFTER_DISPATCH hold="+source.getHoldPosition()+" selected="+(source.getTarget()==enemy)+" forced="+source.getTargetGoal().forced);
                h.assertTrue(!source.getHoldPosition()&&source.getTarget()==enemy&&source.getTargetGoal().forced,"valid same-enemy explicit ATTACK must release HOLD despite core redundant-target reset optimization");
            }).thenIdle(220).thenExecute(()->h.assertTrue(source.position().distanceToSqr(initial)>9&&enemy.getHealth()<hp,"released unit really pursues and deals melee damage, not merely flips a flag")).thenSucceed();
    }
    private static void trace(int elapsed,ShadowGhostUnit a,GoldenFoxUnit b){var p=a.getNavigation().getPath();System.out.println("GHOST_MELEE_DIAGNOSTIC elapsed="+elapsed+" actor="+a.getOwnerName()+" tick="+a.tickCount+" pos="+a.position()+" target="+(a.getTarget()==b)+" hold="+a.getHoldPosition()+" hp="+b.getHealth()+" enemy="+b.position()+" dist="+a.distanceTo(b)+" delta="+a.getDeltaMovement()+" los="+a.hasLineOfSight(b)+" navDone="+a.getNavigation().isDone()+" path="+(p==null?null:p.getNextNodeIndex()+"/"+p.getNodeCount()+" end="+p.getEndNode()));}
    private static ShadowSkillRequest request(ShadowGhostUnit e){return new ShadowSkillRequest(1,UUID.randomUUID(),ShadowSkillRequest.Skill.SHADOW_GHOST_SPECIAL,new ShadowSkillRequest.Source(e.getId(),e.getUUID(),e.rts().ownershipRevision,e.level().dimension().location()),new ShadowSkillRequest.SelfTarget());}
    private static String followState(ShadowGhostUnit mother,GoldenFoxUnit ally) {
        var buildingGoal=(TeamSafeBuildingAttackGoal)mother.getAttackBuildingGoal();
        var building=buildingGoal.getBuildingTarget();
        return "tick="+mother.tickCount+" pos="+mother.position()+" ally="+ally.position()+" distance="+mother.distanceTo(ally)
                +" velocity="+mother.getDeltaMovement()+" follow="+mother.getFollowTarget()+" move="+mother.getMoveGoal().getMoveTarget()
                +" navDone="+mother.getNavigation().isDone()+" attack="+mother.getTarget()+" building="+(building==null?null:building.originPos+":"+building.ownerName)
                +" buildingMove="+buildingGoal.getMoveTarget()+" lastDamage="+mother.getLastDamageSource()+" anchor="+mother.getAnchor();
    }
    private static void followDiagnostic(String phase,ShadowGhostUnit mother,GoldenFoxUnit ally) {
        System.out.println("DREAMSEEKERS_SHADOW_GHOST_FOLLOW_DIAGNOSTIC phase="+phase+" "+followState(mother,ally)+" nearby="+mother.level().getEntitiesOfClass(LivingEntity.class,mother.getBoundingBox().inflate(10)).stream().map(e->e.getType()+":"+e.getId()+":"+(e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u?u.getOwnerName():"wild")+":"+e.position()).toList());
    }
    private static String account(String suffix){String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));}return name;}
    private static ShadowGhostUnit spawn(GameTestHelper h,String name,int x,int z){ShadowGhostUnit e=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(h.getLevel());e.setOwnerName(account(name));place(h,e,x,z);return e;}
    private static GoldenFoxUnit fox(GameTestHelper h,String name,int x,int z){GoldenFoxUnit e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);e.setHealth(500);e.setNoAi(true);e.setOwnerName(account(name));place(h,e,x,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"fixture enters actual world");}
    private static FakePlayer player(GameTestHelper h,String suffix){String name=PREFIX+suffix;FakePlayer p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));PLAYERS.add(p);return p;}
    private static void order(ShadowGhostUnit e,UnitAction a,int target,BlockPos pos){UnitServerEvents.addActionItem(e.getOwnerName(),a,target,new int[]{e.getId()},pos,BlockPos.ZERO);}
    private static AABB area(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);return new AABB(p.getX(),p.getY()+.5,p.getZ(),p.getX()+24,p.getY()+10,p.getZ()+24);}
    private static void floor(GameTestHelper h){for(int i=-1;i<=24;i++)for(int yy=1;yy<=10;yy++){h.getLevel().setBlock(h.absolutePos(new BlockPos(-1,yy,i)),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(24,yy,i)),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(i,yy,-1)),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(i,yy,24)),Blocks.STONE.defaultBlockState(),2);}BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=p.getX()>>4;x<=(p.getX()+23)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+23)>>4;z++){var c=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(x,z,true);TICKETS.add(c);}}for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<10;y++)h.getLevel().setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}h.getLevel().getEntitiesOfClass(ItemEntity.class,area(h)).forEach(Entity::discard);}
    private static boolean published(GameTestHelper h,Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}

}
