// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.unit.*;
import com.dreamseekers.skill.*;
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
import net.pasterdream.entity.WindKnightEntity;
import net.pasterdream.procedures.WindKnightPr0Procedure;
import java.util.*;

@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WindKnightSafetyGameTests {
    private static final String BATCH="dreamseekers_wind_knight_safety", PREFIX="WindSafe_";
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
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=100)
    public static void interactionsDamageAndDeathCannotYieldWildRewards(GameTestHelper h) {
        floor(h);WindKnightUnit slime=spawn(h,"Death",8,8);FakePlayer p=player(h,"Customer");
        for(Item item:List.of(Items.LEAD,Items.WHEAT,net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(DreamseekersMod.paster("wind_knight_spawn_egg")),net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(DreamseekersMod.paster("pulse_windrunner_crystal")))) {
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item,8));
            for(int i=0;i<4;i++){slime.mobInteract(p,InteractionHand.MAIN_HAND);slime.interact(p,InteractionHand.MAIN_HAND);p.interactOn(slime,InteractionHand.MAIN_HAND);}
            h.assertTrue(p.getMainHandItem().getCount()==8&&!slime.isLeashed(),"no breeding, payment or lead capture");
        }
        p.getInventory().clearContent();
        h.assertTrue(!ResourceSources.isHuntableAnimal(slime)&&slime.getExperienceReward()==0,"paid slime is not food/XP source");
        h.startSequence().thenWaitUntil(()->h.assertTrue(slime.tickCount>2,"actual simulation before damage")).thenExecute(()->{
            float hp=slime.getHealth();h.assertTrue(slime.hurt(slime.damageSources().generic(),3)&&slime.getHealth()<hp,"ordinary damage accepted");
            slime.invulnerableTime=0;net.minecraft.world.entity.projectile.Arrow arrow=new net.minecraft.world.entity.projectile.Arrow(h.getLevel(),p);
            hp=slime.getHealth();h.assertTrue(slime.hurt(slime.damageSources().arrow(arrow,p),3)&&slime.getHealth()<hp,"arrow DamageSource accepted (not physical projectile flight claim)");
            slime.invulnerableTime=0;h.assertTrue(!slime.hurt(slime.damageSources().inFire(),3),"native fire immunity retained");
            slime.invulnerableTime=0;h.assertTrue(!slime.hurt(slime.damageSources().fall(),3),"documented native fall immunity retained");
            slime.hurt(slime.damageSources().genericKill(),999);
        }).thenIdle(35).thenExecute(()->{
            h.assertTrue(slime.isRemoved()&&slime.deathTime>=20,"genuine death ticks complete, not only immediate hurt");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,area(h)).isEmpty()
                    &&h.getLevel().getEntitiesOfClass(ExperienceOrb.class,area(h)).isEmpty(),"native loot and XP disabled after real death");
            System.out.println("DREAMSEEKERS_WIND_KNIGHT_INTERACTION_DAMAGE_DEATH_PASS");
        }).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=240)
    public static void meleeIsRealAndSameFactionEnemiesAreNotAllies(GameTestHelper h) {
        floor(h);WindKnightUnit slime=spawn(h,"Melee",5,8);GoldenFoxUnit victim=fox(h,"Target",16,8);
        h.assertTrue(slime.canHarm(victim),"same faction different owners are enemies");
        float health=victim.getHealth();Vec3[] initial={null};float[] alliedHealth={0};
        h.startSequence().thenWaitUntil(()->h.assertTrue(slime.tickCount>2&&victim.tickCount>2,"wait both real entities"))
          .thenExecute(()->initial[0]=slime.position()).thenIdle(20).thenExecute(()->{
            h.assertTrue(victim.getHealth()==health&&slime.position().distanceToSqr(initial[0])<.05&&slime.getTarget()==null,"out-of-aggro victim receives no idle automatic attack");
            order(slime,UnitAction.ATTACK,victim.getId(),BlockPos.ZERO);
          }).thenIdle(95).thenExecute(()->{
            h.assertTrue(victim.getHealth()<health&&slime.position().distanceToSqr(initial[0])>9,"actual RTS ATTACK causes chase and melee beyond automatic aggro");
            AlliancesServerEvents.addAlliance(slime.getOwnerName(),victim.getOwnerName());
            alliedHealth[0]=victim.getHealth();
            h.assertTrue(!slime.doHurtTarget(victim),"final damage guard rejects new alliance immediately");
            slime.getTargetGoal().setTarget(victim);h.assertTrue(slime.getTarget()==null,"direct forced target cannot bypass alliance");
          }).thenIdle(45).thenExecute(()->{
            h.assertTrue(victim.getHealth()==alliedHealth[0]&&slime.getTarget()==null,"no delayed hit after alliance change");
            order(slime,UnitAction.STOP,-1,BlockPos.ZERO);
          }).thenIdle(20).thenExecute(()->{
            h.assertTrue(slime.getAttackMoveTarget()==null&&slime.getMoveGoal().getMoveTarget()==null,"STOP clears combat move state");
            System.out.println("DREAMSEEKERS_WIND_KNIGHT_REAL_MELEE_ALLIANCE_RECHECK_PASS");
          }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=80)
    public static void ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h) {
        float[] hpAfterRequests={0};
        floor(h);WindKnightUnit source=spawn(h,"Owner",5,5);GoldenFoxUnit own=fox(h,"Owner",6,5),ally=fox(h,"Ally",7,5),enemy=fox(h,"Enemy",20,5);
        AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());
        h.startSequence().thenWaitUntil(()->h.assertTrue(source.tickCount>2&&enemy.tickCount>2&&h.getLevel().getEntity(source.getId())==source&&h.getLevel().getEntity(enemy.getId())==enemy,"actual published reserved skill fixtures")).thenExecute(()->{
        AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());
        for(GoldenFoxUnit target:List.of(own,ally)) {
            float hp=target.getHealth();h.assertTrue(!source.doHurtTarget(target)&&target.getHealth()==hp,"own/allied health protected");
            source.setUnitAttackTargetForced(target);h.assertTrue(source.getTarget()==null&&!source.getTargetGoal().forced,"forced ally target rejected without sticky forced flag");
        }
        FakePlayer owner=player(h,"Owner"),foreign=player(h,"Enemy");
        var request=new ReservedSkillRequest(ReservedSkillRequest.Skill.WIND_KNIGHT_SPECIAL,source.getId(),source.getUUID(),new ReservedSkillRequest.Self());
        h.assertTrue(ReservedSkillService.request(null,request)==ReservedSkillService.Result.INVALID_REQUEST,"no sender rejected");
        h.assertTrue(ReservedSkillService.request(foreign,request)==ReservedSkillService.Result.NOT_OWNER,"same faction foreign sender rejected");
        h.assertTrue(ReservedSkillService.request(owner,new ReservedSkillRequest(request.skill(),source.getId(),UUID.randomUUID(),request.target()))==ReservedSkillService.Result.INVALID_SOURCE,"ID reuse/stale UUID rejected");
        h.assertTrue(ReservedSkillService.request(owner,request)==ReservedSkillService.Result.DISABLED,"valid reserved skill cannot run an effect");
        var badTarget=new ReservedSkillRequest(request.skill(),source.getId(),source.getUUID(),new ReservedSkillRequest.EntityTarget(enemy.getId(),UUID.randomUUID()));
        h.assertTrue(ReservedSkillService.request(owner,badTarget)==ReservedSkillService.Result.INVALID_TARGET,"typed target UUID verified");
        enemy.setOwnerName("");h.assertTrue(!source.doHurtTarget(enemy),"unowned target is not a valid paid combat target");enemy.setOwnerName(PREFIX+"Enemy");
        source.setOwnerName("");h.assertTrue(!source.doHurtTarget(enemy)&&ReservedSkillService.request(owner,request)==ReservedSkillService.Result.NOT_OWNER,"unowned source cannot fight or cast");
        source.setOwnerName(PREFIX+"Owner");hpAfterRequests[0]=enemy.getHealth();
        }).thenIdle(20).thenExecute(()->{
            Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(source.getOwnerName())).findFirst().orElseThrow();
            h.assertTrue(r.food==500&&r.wood==500&&r.ore==500&&source.getAbilities().get().equals(List.of(UnitSkillAbility.WIND_DASH))&&source.rts().skills().cooldownTicks(UnitSkill.WIND_DASH)==0&&enemy.getHealth()==hpAfterRequests[0],"reserved interface has no currency/approved-skill cooldown/damage side effect");
            System.out.println("DREAMSEEKERS_WIND_KNIGHT_TEAM_FILTER_RESERVED_SKILL_PASS");
        }).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=300)
    public static void holdWithEnemyNeverChasesButStillHitsInsideMeleeReach(GameTestHelper h) {
        floor(h);WindKnightUnit slime=spawn(h,"Hold",5,8);GoldenFoxUnit victim=fox(h,"HoldEnemy",11,8);
        Vec3[] origin={null};float[] health={0},after={0};
        h.startSequence().thenWaitUntil(()->h.assertTrue(slime.tickCount>2&&victim.tickCount>2,"genuine simulation before combat HOLD"))
          .thenExecute(()->order(slime,UnitAction.HOLD,-1,BlockPos.ZERO)).thenIdle(15).thenExecute(()->{
            origin[0]=slime.position();health[0]=victim.getHealth();slime.setUnitAttackTarget(victim); // Guard selection, not an explicit pursuit order.
          }).thenIdle(60).thenExecute(()->{
            h.assertTrue(slime.getHoldPosition()&&slime.position().distanceToSqr(origin[0])<.05&&victim.getHealth()==health[0],"held distant target does not induce legacy continue-path chase");
            victim.moveTo(slime.getX()+1.3,slime.getY(),slime.getZ()); // Move the target only, never the unit under test.
          }).thenIdle(55).thenExecute(()->{
            h.assertTrue(victim.getHealth()<health[0]&&slime.position().distanceToSqr(origin[0])<.15,"held guard really attacks an in-range enemy without following");
            victim.moveTo(slime.getX()+7,slime.getY(),slime.getZ());after[0]=victim.getHealth();
          }).thenIdle(65).thenExecute(()->{
            h.assertTrue(slime.position().distanceToSqr(origin[0])<.15&&victim.getHealth()==after[0]&&slime.getNavigation().isDone(),"target leaves range without dragging guard out of HOLD");
            System.out.println("DREAMSEEKERS_WIND_KNIGHT_COMBAT_HOLD_RANGE_PASS");
          }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=220)
    public static void friendlyFollowMovesAndRevokedAllianceCancelsItsPath(GameTestHelper h) {
        floor(h);WindKnightUnit slime=spawn(h,"Follow",3,18);GoldenFoxUnit ally=fox(h,"FollowAlly",21,18);
        AlliancesServerEvents.addAlliance(slime.getOwnerName(),ally.getOwnerName());
        Vec3[] start={null},stopped={null};
        // Register the entire sequence now. Nested runAfterDelay mutates GameTestInfo's live
        // fastutil callback map while it is being iterated and can re-execute a callback.
        GameTestSequence sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(slime.tickCount>2&&ally.tickCount>2,"genuine follow fixtures ready"))
                .thenExecute(()->{start[0]=slime.position();followDiagnostic("before",slime,ally);order(slime,UnitAction.FOLLOW,ally.getId(),BlockPos.ZERO);})
                .thenIdle(35).thenExecute(()->{
                    followDiagnostic("revoke",slime,ally);
                    h.assertTrue(slime.getFollowTarget()==ally&&slime.position().distanceToSqr(start[0])>1,"friendly FOLLOW really moves the ground guard");
                    h.assertTrue(slime.distanceTo(ally)>slime.getAggroRange()+1,"withdraw alliance outside combat reacquisition radius");
                    AlliancesServerEvents.removeAlliance(slime.getOwnerName(),ally.getOwnerName());
                }).thenIdle(5);
        for(int i=0;i<10;i++)sequence.thenExecute(()->assertEmptyRevoked(h,slime,ally)).thenIdle(1);
        sequence.thenExecute(()->{stopped[0]=slime.position();followDiagnostic("settled",slime,ally);});
        for(int i=0;i<50;i++)sequence.thenIdle(1).thenExecute(()->{
            assertEmptyRevoked(h,slime,ally);
            h.assertTrue(slime.position().distanceToSqr(stopped[0])<.1,"old friendly path stays stationary every observed tick: "+followState(slime,ally));
        });
        sequence.thenExecute(()->{followDiagnostic("final",slime,ally);System.out.println("DREAMSEEKERS_WIND_KNIGHT_FOLLOW_REVOKE_PASS");}).thenSucceed();
    }
    private static void assertEmptyRevoked(GameTestHelper h,WindKnightUnit slime,GoldenFoxUnit ally) {
        h.assertTrue(slime.getFollowTarget()==null&&slime.getMoveGoal().getMoveTarget()==null,
                "old friendly command stays canceled every observed tick");
        h.assertTrue(slime.getTarget()==null&&((TeamSafeBuildingAttackGoal)slime.getAttackBuildingGoal()).getBuildingTarget()==null,
                "empty fixture has no new entity/building combat target: "+followState(slime,ally));
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=220)
    public static void revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(GameTestHelper h) {
        floor(h);WindKnightUnit slime=spawn(h,"Reacquire",3,18);
        GoldenFoxUnit formerAlly=fox(h,"FormerAlly",21,18),newEnemy=fox(h,"NewEnemy",21,6);
        AlliancesServerEvents.addAlliance(slime.getOwnerName(),formerAlly.getOwnerName());
        Vec3[] start={null};float[] hp={0},allyHp={0};
        GameTestSequence sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(slime.tickCount>2&&formerAlly.tickCount>2&&newEnemy.tickCount>2,"real auto-aggro fixtures ready"))
                .thenExecute(()->order(slime,UnitAction.FOLLOW,formerAlly.getId(),BlockPos.ZERO))
                .thenIdle(35).thenExecute(()->{
                    h.assertTrue(slime.getFollowTarget()==formerAlly&&slime.distanceTo(formerAlly)>slime.getAggroRange()+1,"old friendly path active outside former ally combat radius");
                    AlliancesServerEvents.removeAlliance(slime.getOwnerName(),formerAlly.getOwnerName());
                    start[0]=slime.position();hp[0]=newEnemy.getHealth();allyHp[0]=formerAlly.getHealth();
                    // Deliberately place the separate enemy in aggro range. Never teleport the actor under test.
                    newEnemy.moveTo(slime.getX()+4,slime.getY(),slime.getZ());
                }).thenIdle(5);
        for(int i=0;i<61;i++) {
            final int elapsed=i;
            sequence.thenExecute(()->{
                h.assertTrue(slime.getFollowTarget()==null&&slime.getMoveGoal().getMoveTarget()==null,
                        "new combat never restores revoked follow or its ordinary move destination");
                h.assertTrue(slime.getTarget()==null||slime.getTarget()==newEnemy,"only the newly nearby enemy may be reacquired");
                if(elapsed==10)h.assertTrue(slime.getTarget()==newEnemy,"idle guard legitimately reacquires the new nearby enemy");
            }).thenIdle(1);
        }
        sequence.thenExecute(()->{
            h.assertTrue(newEnemy.getHealth()<hp[0]&&slime.position().distanceToSqr(start[0])>1,"actual new combat navigation and melee, not stale friendly FOLLOW");
            h.assertTrue(formerAlly.getHealth()==allyHp[0],"distant former ally is not chased or hit");
            followDiagnostic("legitimate_new_aggro",slime,formerAlly);
            System.out.println("DREAMSEEKERS_WIND_KNIGHT_FOLLOW_REVOKE_NEW_AGGRO_PASS");
        }).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=240)
    public static void sameEnemyExplicitAttackReleasesHoldAndReallyPursues(GameTestHelper h) {
        floor(h);var source=spawn(h,"HeldAttack",5,8);var enemy=fox(h,"HeldEnemy",13,8);var own=fox(h,"HeldAttack",20,18);
        source.setHoldPosition(true);Vec3 initial=source.position();float hp=enemy.getHealth();
        h.startSequence().thenWaitUntil(()->h.assertTrue(source.tickCount>2&&enemy.tickCount>2&&h.getLevel().getEntity(source.getUUID())==source&&h.getLevel().getEntity(enemy.getUUID())==enemy,"real published held combatants"))
            .thenIdle(25).thenExecute(()->{
                h.assertTrue(source.getHoldPosition()&&source.getTarget()==enemy&&source.position().distanceToSqr(initial)<.1&&enemy.getHealth()==hp,"HOLD has already selected this actual enemy but has not chased it");
                source.setUnitAttackTargetForced(own);
                h.assertTrue(source.getHoldPosition()&&source.getTarget()==null,"invalid allied forced target cannot release HOLD");
                source.getTargetGoal().setTarget(enemy);
                h.assertTrue(source.getTarget()==enemy,"restore exactly the same selected enemy before real ATTACK path");
                new com.solegendary.reignofnether.unit.UnitActionItem(source.getOwnerName(),UnitAction.ATTACK,enemy.getId(),new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                System.out.println("WIND_SAME_TARGET_ATTACK_AFTER_DISPATCH hold="+source.getHoldPosition()+" selected="+(source.getTarget()==enemy)+" forced="+source.getTargetGoal().forced);
                h.assertTrue(!source.getHoldPosition()&&source.getTarget()==enemy&&source.getTargetGoal().forced,"valid same-enemy explicit ATTACK must release HOLD despite core redundant-target reset optimization");
            }).thenIdle(95).thenExecute(()->h.assertTrue(source.position().distanceToSqr(initial)>9&&enemy.getHealth()<hp,"released unit really pursues and deals melee damage, not merely flips a flag")).thenSucceed();
    }
    private static String followState(WindKnightUnit slime,GoldenFoxUnit ally) {
        var buildingGoal=(TeamSafeBuildingAttackGoal)slime.getAttackBuildingGoal();
        var building=buildingGoal.getBuildingTarget();
        return "tick="+slime.tickCount+" pos="+slime.position()+" ally="+ally.position()+" distance="+slime.distanceTo(ally)
                +" velocity="+slime.getDeltaMovement()+" follow="+slime.getFollowTarget()+" move="+slime.getMoveGoal().getMoveTarget()
                +" navDone="+slime.getNavigation().isDone()+" attack="+slime.getTarget()+" building="+(building==null?null:building.originPos+":"+building.ownerName)
                +" buildingMove="+buildingGoal.getMoveTarget()+" lastDamage="+slime.getLastDamageSource()+" anchor="+slime.getAnchor();
    }
    private static void followDiagnostic(String phase,WindKnightUnit slime,GoldenFoxUnit ally) {
        System.out.println("DREAMSEEKERS_WIND_KNIGHT_FOLLOW_DIAGNOSTIC phase="+phase+" "+followState(slime,ally)+" nearby="+slime.level().getEntitiesOfClass(LivingEntity.class,slime.getBoundingBox().inflate(10)).stream().map(e->e.getType()+":"+e.getId()+":"+(e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u?u.getOwnerName():"wild")+":"+e.position()).toList());
    }
    private static String account(String suffix){String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));}return name;}
    private static WindKnightUnit spawn(GameTestHelper h,String name,int x,int z){WindKnightUnit e=DreamseekersMod.WIND_KNIGHT_UNIT.get().create(h.getLevel());e.setOwnerName(account(name));place(h,e,x,z);return e;}
    private static GoldenFoxUnit fox(GameTestHelper h,String name,int x,int z){GoldenFoxUnit e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);e.setHealth(500);e.setOwnerName(account(name));place(h,e,x,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"fixture enters actual world");}
    private static FakePlayer player(GameTestHelper h,String suffix){String name=PREFIX+suffix;FakePlayer p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));PLAYERS.add(p);return p;}
    private static void order(WindKnightUnit e,UnitAction a,int target,BlockPos pos){UnitServerEvents.addActionItem(e.getOwnerName(),a,target,new int[]{e.getId()},pos,BlockPos.ZERO);}
    private static AABB area(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);return new AABB(p.getX(),p.getY()+.5,p.getZ(),p.getX()+24,p.getY()+10,p.getZ()+24);}
    private static void floor(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=p.getX()>>4;x<=(p.getX()+23)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+23)>>4;z++){var c=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(x,z,true);TICKETS.add(c);}}for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<10;y++)h.getLevel().setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}h.getLevel().getEntitiesOfClass(ItemEntity.class,area(h)).forEach(Entity::discard);}
}
