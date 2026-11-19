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

@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShadowHandSafetyGameTests {
    private static final String BATCH="dreamseekers_shadow_hand_safety", PREFIX="HandSafe_";
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
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=460)
    public static void meleeIsRealAndSameFactionEnemiesAreNotAllies(GameTestHelper h) {
        floor(h);ShadowHandUnit source=spawn(h,"Melee",5,8);GoldenFoxUnit victim=fox(h,"Target",16,8);
        h.assertTrue(source.canHarm(victim),"same faction different owners are enemies");
        float health=victim.getHealth();Vec3[] initial={null};float[] alliedHealth={0};
        // Pre-register the entire timeline: never mutate GameTestInfo's live callback map from a callback.
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,source)&&published(h,victim),"wait both real entities"))
            .thenExecute(()->initial[0]=source.position()).thenIdle(20).thenExecute(()->{
                h.assertTrue(victim.getHealth()==health&&source.position().distanceToSqr(initial[0])<.05&&source.getTarget()==null,"out-of-aggro victim receives no idle automatic attack");
                order(source,UnitAction.ATTACK,victim.getId(),BlockPos.ZERO);
            }).thenIdle(220).thenExecute(()->{
                h.assertTrue(victim.getHealth()<health&&source.position().distanceToSqr(initial[0])>9,"actual RTS ATTACK causes chase and melee beyond automatic aggro");
                AlliancesServerEvents.addAlliance(source.getOwnerName(),victim.getOwnerName());alliedHealth[0]=victim.getHealth();
                h.assertTrue(!source.doHurtTarget(victim),"final damage guard rejects new alliance immediately");
                source.getTargetGoal().setTarget(victim);h.assertTrue(source.getTarget()==null,"direct forced target cannot bypass alliance");
            }).thenIdle(45).thenExecute(()->{
                h.assertTrue(victim.getHealth()==alliedHealth[0]&&source.getTarget()==null,"no delayed hit after alliance change");
                order(source,UnitAction.STOP,-1,BlockPos.ZERO);
            }).thenIdle(20).thenExecute(()->{
                h.assertTrue(source.getAttackMoveTarget()==null&&source.getMoveGoal().getMoveTarget()==null,"STOP clears combat move state");
                System.out.println("DREAMSEEKERS_SHADOW_HAND_REAL_MELEE_ALLIANCE_RECHECK_PASS");
            }).thenSucceed();
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=80)
    public static void ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h) {
        float[] enemyHealth={0};floor(h);ShadowHandUnit source=spawn(h,"Owner",5,5);GoldenFoxUnit own=fox(h,"Owner",6,5),ally=fox(h,"Ally",7,5),enemy=fox(h,"Enemy",20,5);
        AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,source)&&published(h,enemy)&&h.getLevel().getEntity(source.getId())==source&&h.getLevel().getEntity(enemy.getId())==enemy,"actual published reserved skill fixtures")).thenExecute(()->{
        AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());
        for(GoldenFoxUnit target:List.of(own,ally)) {
            float hp=target.getHealth();h.assertTrue(!source.doHurtTarget(target)&&target.getHealth()==hp,"own/allied health protected");
            source.setUnitAttackTargetForced(target);h.assertTrue(source.getTarget()==null&&!source.getTargetGoal().forced,"forced ally target rejected without sticky forced flag");
        }
        FakePlayer owner=player(h,"Owner"),foreign=player(h,"Enemy");
        var request=request(source);
        var wrongSkill=new ShadowSkillRequest(1,UUID.randomUUID(),ShadowSkillRequest.Skill.SHADOW_GHOST_SPECIAL,request.caster(),request.target());
        h.assertTrue(ShadowSkillService.request(owner,wrongSkill)==ShadowSkillService.Result.INVALID_SOURCE,"another shadow skill cannot borrow this source type");
        var energy=new ShadowEnergyGate.Reservation(UUID.randomUUID(),owner.getUUID(),1);
        h.assertTrue(ShadowEnergyGate.reserve(owner,energy)==ShadowEnergyGate.Result.UNAVAILABLE,"positive energy reservation unavailable, never fake permission");
        h.assertTrue(ShadowEnergyGate.reserve(foreign,energy)==ShadowEnergyGate.Result.NOT_PAYER,"real sender bound to payer");
        h.assertTrue(ShadowEnergyGate.reserve(owner,new ShadowEnergyGate.Reservation(UUID.randomUUID(),owner.getUUID(),0))==ShadowEnergyGate.Result.INVALID_REQUEST,"zero energy cannot authorize free effects");
        h.assertTrue(ShadowEnergyGate.reserve(owner,new ShadowEnergyGate.Reservation(UUID.randomUUID(),owner.getUUID(),-1))==ShadowEnergyGate.Result.INVALID_REQUEST,"negative energy cannot grant currency");
        h.assertTrue(ShadowSkillService.request(null,request)==ShadowSkillService.Result.INVALID_REQUEST,"no sender rejected");
        h.assertTrue(ShadowSkillService.request(foreign,request)==ShadowSkillService.Result.NOT_OWNER,"same-faction foreign sender rejected");
        var stale=new ShadowSkillRequest(1,UUID.randomUUID(),ShadowSkillRequest.Skill.SHADOW_HAND_SPECIAL,new ShadowSkillRequest.Source(source.getId(),UUID.randomUUID(),source.rts().ownershipRevision,h.getLevel().dimension().location()),new ShadowSkillRequest.SelfTarget());
        h.assertTrue(ShadowSkillService.request(owner,stale)==ShadowSkillService.Result.INVALID_SOURCE,"ID reuse/stale UUID rejected");
        h.assertTrue(ShadowSkillService.request(owner,request)==ShadowSkillService.Result.UNAVAILABLE&&ShadowSkillService.request(owner,request)==ShadowSkillService.Result.UNAVAILABLE,"valid and duplicate requests never activate undefined summons");
        h.assertTrue(ShadowSkillService.request(owner,new ShadowSkillRequest(2,request.requestId(),request.skill(),request.caster(),request.target()))==ShadowSkillService.Result.INVALID_REQUEST,"future schema rejected");
        var wrongDimension=new ShadowSkillRequest(1,UUID.randomUUID(),ShadowSkillRequest.Skill.SHADOW_HAND_SPECIAL,new ShadowSkillRequest.Source(source.getId(),source.getUUID(),source.rts().ownershipRevision,net.minecraft.world.level.Level.NETHER.location()),request.target());
        h.assertTrue(ShadowSkillService.request(owner,wrongDimension)==ShadowSkillService.Result.WRONG_DIMENSION,"cross-dimension request fails closed");
        enemy.setOwnerName("");h.assertTrue(!source.doHurtTarget(enemy),"unowned target is not a valid paid combat target");enemy.setOwnerName(PREFIX+"Enemy");
        source.setOwnerName("");h.assertTrue(!source.doHurtTarget(enemy)&&ShadowSkillService.request(owner,request)==ShadowSkillService.Result.STALE_OWNER,"unowned source cannot fight or cast");
        source.setOwnerName(PREFIX+"Owner");h.assertTrue(ShadowSkillService.request(owner,request)==ShadowSkillService.Result.STALE_OWNER,"observed owner ABA invalidates old authority epoch");enemyHealth[0]=enemy.getHealth();
        }).thenIdle(20).thenExecute(()->{
            Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(source.getOwnerName())).findFirst().orElseThrow();
            h.assertTrue(r.food==500&&r.wood==500&&r.ore==500&&source.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.SHADOW_HAND_GRIP))&&source.rts().skills().cooldownTicks(com.dreamseekers.skill.UnitSkill.SHADOW_HAND_GRIP)==0&&enemy.getHealth()==enemyHealth[0],"disabled interface has no currency/cooldown/damage side effect");
            System.out.println("DREAMSEEKERS_SHADOW_HAND_TEAM_FILTER_RESERVED_SKILL_PASS");
        }).thenSucceed();
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=300)
    public static void holdWithEnemyNeverChasesButStillHitsInsideMeleeReach(GameTestHelper h) {
        floor(h);ShadowHandUnit mother=spawn(h,"Hold",5,8);GoldenFoxUnit victim=fox(h,"HoldEnemy",11,8);
        Vec3[] origin={null};float[] health={0},after={0};
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,mother)&&published(h,victim),"genuine simulation before combat HOLD"))
            .thenExecute(()->order(mother,UnitAction.HOLD,-1,BlockPos.ZERO)).thenIdle(15).thenExecute(()->{
                origin[0]=mother.position();health[0]=victim.getHealth();mother.setUnitAttackTarget(victim); // Automatic guard selection, not an explicit pursuit order.
            }).thenIdle(60).thenExecute(()->{
                h.assertTrue(mother.getHoldPosition()&&mother.position().distanceToSqr(origin[0])<.05&&victim.getHealth()==health[0],"held distant target does not induce legacy continue-path chase");
                victim.moveTo(mother.getX()+1.3,mother.getY(),mother.getZ()); // Target only, never actor.
            }).thenIdle(55).thenExecute(()->{
                h.assertTrue(victim.getHealth()<health[0]&&mother.position().distanceToSqr(origin[0])<.15,"held guard really attacks an in-range enemy without following");
                victim.moveTo(mother.getX()+7,mother.getY(),mother.getZ());after[0]=victim.getHealth();
            }).thenIdle(65).thenExecute(()->{
                h.assertTrue(mother.position().distanceToSqr(origin[0])<.15&&victim.getHealth()==after[0]&&mother.getNavigation().isDone(),"target leaves range without dragging guard out of HOLD");
                System.out.println("DREAMSEEKERS_SHADOW_HAND_COMBAT_HOLD_RANGE_PASS");
            }).thenSucceed();
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=220)
    public static void friendlyFollowMovesAndRevokedAllianceCancelsItsPath(GameTestHelper h) {
        floor(h);ShadowHandUnit mother=spawn(h,"Follow",3,18);GoldenFoxUnit ally=fox(h,"FollowAlly",21,18);
        AlliancesServerEvents.addAlliance(mother.getOwnerName(),ally.getOwnerName());
        Vec3[] start={null},stopped={null};
        // Register the entire sequence now. Nested runAfterDelay mutates GameTestInfo's live
        // fastutil callback map while it is being iterated and can re-execute a callback.
        GameTestSequence sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,mother)&&published(h,ally),"genuine follow fixtures ready"))
                .thenExecute(()->{start[0]=mother.position();followDiagnostic("before",mother,ally);order(mother,UnitAction.FOLLOW,ally.getId(),BlockPos.ZERO);})
                .thenIdle(35).thenExecute(()->{
                    followDiagnostic("revoke",mother,ally);
                    h.assertTrue(mother.getFollowTarget()==ally&&mother.position().distanceToSqr(start[0])>1,"friendly FOLLOW really moves the ground guard");
                    h.assertTrue(mother.distanceTo(ally)>mother.getAggroRange()+1,"withdraw alliance outside combat reacquisition radius");
                    AlliancesServerEvents.removeAlliance(mother.getOwnerName(),ally.getOwnerName());
                }).thenIdle(5);
        for(int i=0;i<10;i++)sequence.thenExecute(()->assertEmptyRevoked(h,mother,ally)).thenIdle(1);
        sequence.thenExecute(()->{stopped[0]=mother.position();followDiagnostic("settled",mother,ally);});
        for(int i=0;i<50;i++)sequence.thenIdle(1).thenExecute(()->{
            assertEmptyRevoked(h,mother,ally);
            h.assertTrue(mother.position().distanceToSqr(stopped[0])<.1,"old friendly path stays stationary every observed tick: "+followState(mother,ally));
        });
        sequence.thenExecute(()->{followDiagnostic("final",mother,ally);System.out.println("DREAMSEEKERS_SHADOW_HAND_FOLLOW_REVOKE_PASS");}).thenSucceed();
    }
    private static void assertEmptyRevoked(GameTestHelper h,ShadowHandUnit mother,GoldenFoxUnit ally) {
        h.assertTrue(mother.getFollowTarget()==null&&mother.getMoveGoal().getMoveTarget()==null,
                "old friendly command stays canceled every observed tick");
        h.assertTrue(mother.getTarget()==null&&((TeamSafeBuildingAttackGoal)mother.getAttackBuildingGoal()).getBuildingTarget()==null,
                "empty fixture has no new entity/building combat target: "+followState(mother,ally));
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=220)
    public static void revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(GameTestHelper h) {
        floor(h);ShadowHandUnit mother=spawn(h,"Reacquire",3,18);
        GoldenFoxUnit formerAlly=fox(h,"FormerAlly",21,18),newEnemy=fox(h,"NewEnemy",21,6);
        AlliancesServerEvents.addAlliance(mother.getOwnerName(),formerAlly.getOwnerName());
        Vec3[] start={null};float[] hp={0},allyHp={0};
        GameTestSequence sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,mother)&&published(h,formerAlly)&&published(h,newEnemy),"real auto-aggro fixtures ready"))
                .thenExecute(()->order(mother,UnitAction.FOLLOW,formerAlly.getId(),BlockPos.ZERO))
                .thenIdle(35).thenExecute(()->{
                    h.assertTrue(mother.getFollowTarget()==formerAlly&&mother.distanceTo(formerAlly)>mother.getAggroRange()+1,"old friendly path active outside former ally combat radius");
                    AlliancesServerEvents.removeAlliance(mother.getOwnerName(),formerAlly.getOwnerName());
                    start[0]=mother.position();hp[0]=newEnemy.getHealth();allyHp[0]=formerAlly.getHealth();
                    // Deliberately place the separate enemy in aggro range. Never teleport the actor under test.
                    newEnemy.moveTo(mother.getX()+4,mother.getY(),mother.getZ());
                }).thenIdle(5);
        for(int i=0;i<61;i++) {
            final int elapsed=i;
            sequence.thenExecute(()->{
                h.assertTrue(mother.getFollowTarget()==null&&mother.getMoveGoal().getMoveTarget()==null,
                        "new combat never restores revoked follow or its ordinary move destination");
                h.assertTrue(mother.getTarget()==null||mother.getTarget()==newEnemy,"only the newly nearby enemy may be reacquired");
                if(elapsed==10)h.assertTrue(mother.getTarget()==newEnemy,"idle guard legitimately reacquires the new nearby enemy");
            }).thenIdle(1);
        }
        sequence.thenExecute(()->{
            h.assertTrue(newEnemy.getHealth()<hp[0]&&mother.position().distanceToSqr(start[0])>1,"actual new combat navigation and melee, not stale friendly FOLLOW");
            h.assertTrue(formerAlly.getHealth()==allyHp[0],"distant former ally is not chased or hit");
            followDiagnostic("legitimate_new_aggro",mother,formerAlly);
            System.out.println("DREAMSEEKERS_SHADOW_HAND_FOLLOW_REVOKE_NEW_AGGRO_PASS");
        }).thenSucceed();
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=460)
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
                System.out.println("HAND_SAME_TARGET_ATTACK_AFTER_DISPATCH hold="+source.getHoldPosition()+" selected="+(source.getTarget()==enemy)+" forced="+source.getTargetGoal().forced);
                h.assertTrue(!source.getHoldPosition()&&source.getTarget()==enemy&&source.getTargetGoal().forced,"valid same-enemy explicit ATTACK must release HOLD despite core redundant-target reset optimization");
            }).thenIdle(220).thenExecute(()->h.assertTrue(source.position().distanceToSqr(initial)>9&&enemy.getHealth()<hp,"released unit really pursues and deals melee damage, not merely flips a flag")).thenSucceed();
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=260)
    public static void realFlightMeleeReachesAirborneEnemyWithoutContactDive(GameTestHelper h){
        floor(h);ShadowHandUnit source=spawn(h,"Air",4,7);
        var enemy=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());enemy.setOwnerName(account("AirEnemy"));enemy.setNoAi(true);
        enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);enemy.setHealth(500);
        BlockPos p=h.absolutePos(new BlockPos(16,4,7));enemy.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(enemy);h.assertTrue(h.getLevel().addFreshEntity(enemy),"real airborne target");
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,source)&&published(h,enemy),"both real air combatants published")).thenExecute(()->order(source,UnitAction.ATTACK,enemy.getId(),BlockPos.ZERO)).thenIdle(200).thenExecute(()->{
            h.assertTrue(enemy.getHealth()<500&&source.getY()>h.absolutePos(BlockPos.ZERO).getY()+2,"real flight pursuit damages an aerial enemy");
            h.assertTrue(h.getLevel().noCollision(source)&&!source.noPhysics,"flight is not phasing");
            h.assertTrue(source.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal().getClass().getName().startsWith("net.pasterdream.entity.ShadowHandEntity$")),"native contact dive cannot deliver extra attacks");
        }).thenSucceed();
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=180)
    public static void closeEnemyBehindSolidWallCannotBeHitEvenByDirectNativeEntry(GameTestHelper h){
        floor(h);var source=spawn(h,"Wall",8,8);var enemy=fox(h,"WallEnemy",10,8);source.setHoldPosition(true);
        // A thin collision wall separates both bodies while they remain within real ordinary melee reach.
        source.setPos(source.getX()+.4,source.getY(),source.getZ());enemy.setPos(enemy.getX()-.4,enemy.getY(),enemy.getZ());
        var bars=Blocks.IRON_BARS.defaultBlockState().setValue(net.minecraft.world.level.block.CrossCollisionBlock.NORTH,true).setValue(net.minecraft.world.level.block.CrossCollisionBlock.SOUTH,true);
        for(int z=0;z<24;z++)for(int y=1;y<10;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(9,y,z)),bars,2);
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,source)&&published(h,enemy),"wall actors published")).thenExecute(()->{
            double reachSquared=4*source.getBbWidth()*source.getBbWidth()+enemy.getBbWidth()+enemy.getBonusMeleeRangeForAttackers();
            h.assertTrue(source.distanceToSqr(enemy)<reachSquared&&h.getLevel().noCollision(source)&&h.getLevel().noCollision(enemy),"both separate bodies are genuinely within existing melee reach on opposite sides");
            h.assertTrue(!source.hasLineOfSight(enemy)&&!source.doHurtTarget(enemy),"final direct damage entry rejects blocked line of sight");
            source.setUnitAttackTarget(enemy);
        }).thenIdle(60).thenExecute(()->{
            h.assertTrue(enemy.getHealth()==500&&source.getHoldPosition(),"held wall target receives no native dive or through-wall damage");
            for(int y=1;y<=3;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(9,y,8)),Blocks.AIR.defaultBlockState(),2);
            for(var effect:java.util.List.of(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get(),com.solegendary.reignofnether.registrars.MobEffectRegistrar.FREEZE.get())) {
                source.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,40));
                h.assertTrue(source.hasLineOfSight(enemy)&&!source.doHurtTarget(enemy)&&enemy.getHealth()==500,"final entity damage entry rejects STUN/FREEZE before core periodic reset");source.removeEffect(effect);
            }
            order(source,UnitAction.ATTACK,enemy.getId(),BlockPos.ZERO);
        }).thenIdle(70).thenExecute(()->h.assertTrue(enemy.getHealth()<500,"opening real LOS and explicit order permit genuine hit")).thenSucceed();
    }
    private static ShadowSkillRequest request(ShadowHandUnit e){return new ShadowSkillRequest(1,UUID.randomUUID(),ShadowSkillRequest.Skill.SHADOW_HAND_SPECIAL,new ShadowSkillRequest.Source(e.getId(),e.getUUID(),e.rts().ownershipRevision,e.level().dimension().location()),new ShadowSkillRequest.SelfTarget());}
    private static String followState(ShadowHandUnit mother,GoldenFoxUnit ally) {
        var buildingGoal=(TeamSafeBuildingAttackGoal)mother.getAttackBuildingGoal();
        var building=buildingGoal.getBuildingTarget();
        return "tick="+mother.tickCount+" pos="+mother.position()+" ally="+ally.position()+" distance="+mother.distanceTo(ally)
                +" velocity="+mother.getDeltaMovement()+" follow="+mother.getFollowTarget()+" move="+mother.getMoveGoal().getMoveTarget()
                +" navDone="+mother.getNavigation().isDone()+" attack="+mother.getTarget()+" building="+(building==null?null:building.originPos+":"+building.ownerName)
                +" buildingMove="+buildingGoal.getMoveTarget()+" lastDamage="+mother.getLastDamageSource()+" anchor="+mother.getAnchor();
    }
    private static void followDiagnostic(String phase,ShadowHandUnit mother,GoldenFoxUnit ally) {
        System.out.println("DREAMSEEKERS_SHADOW_HAND_FOLLOW_DIAGNOSTIC phase="+phase+" "+followState(mother,ally)+" nearby="+mother.level().getEntitiesOfClass(LivingEntity.class,mother.getBoundingBox().inflate(10)).stream().map(e->e.getType()+":"+e.getId()+":"+(e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u?u.getOwnerName():"wild")+":"+e.position()).toList());
    }
    private static String account(String suffix){String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));}return name;}
    private static ShadowHandUnit spawn(GameTestHelper h,String name,int x,int z){ShadowHandUnit e=DreamseekersMod.SHADOW_HAND_UNIT.get().create(h.getLevel());e.setOwnerName(account(name));place(h,e,x,z);return e;}
    private static GoldenFoxUnit fox(GameTestHelper h,String name,int x,int z){GoldenFoxUnit e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);e.setHealth(500);e.setNoAi(true);e.setOwnerName(account(name));place(h,e,x,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"fixture enters actual world");}
    private static FakePlayer player(GameTestHelper h,String suffix){String name=PREFIX+suffix;FakePlayer p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));PLAYERS.add(p);return p;}
    private static void order(ShadowHandUnit e,UnitAction a,int target,BlockPos pos){UnitServerEvents.addActionItem(e.getOwnerName(),a,target,new int[]{e.getId()},pos,BlockPos.ZERO);}
    private static AABB area(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);return new AABB(p.getX(),p.getY()+.5,p.getZ(),p.getX()+24,p.getY()+10,p.getZ()+24);}
    private static void floor(GameTestHelper h){for(int i=-1;i<=24;i++)for(int yy=1;yy<=10;yy++){h.getLevel().setBlock(h.absolutePos(new BlockPos(-1,yy,i)),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(24,yy,i)),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(i,yy,-1)),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(i,yy,24)),Blocks.STONE.defaultBlockState(),2);}BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=p.getX()>>4;x<=(p.getX()+23)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+23)>>4;z++){var c=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(x,z,true);TICKETS.add(c);}}for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<10;y++)h.getLevel().setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}h.getLevel().getEntitiesOfClass(ItemEntity.class,area(h)).forEach(Entity::discard);}
    private static boolean published(GameTestHelper h,Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}

}
