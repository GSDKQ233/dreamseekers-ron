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
public final class WeepingSquealGhostSafetyGameTests {
    private static final String BATCH="dreamseekers_weeping_squeal_ghost_safety", PREFIX="WeepingSafe_";
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

    @GameTest(template="weeping_unit_arena",batch=BATCH,timeoutTicks=180)
    public static void legalRepeatedSummonReserveLeavesRealCommandsCooldownPopulationAndWorldUntouched(GameTestHelper h){
        floor(h);var source=spawn(h,"Reserve",5,5);var owner=player(h,"Reserve");
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,source),"legal summoner published")).thenExecute(()->{
            source.getPersistentData().putBoolean("SummonEnabled",true);source.getPersistentData().putInt("SummonPaid",999);source.getPersistentData().putInt("ShadowEnergy",999);
            new UnitActionItem(source.getOwnerName(),UnitAction.MOVE,-1,new int[]{source.getId()},h.absolutePos(new BlockPos(14,0,5)),BlockPos.ZERO).action(h.getLevel());
            UnitServerEvents.addActionItem(source.getOwnerName(),UnitAction.MOVE,-1,new int[]{source.getId()},h.absolutePos(new BlockPos(14,0,18)),BlockPos.ZERO,true);
            var cooldown=new com.solegendary.reignofnether.ability.Ability(UnitAction.STOP,30,0,0,false);source.getAbilityCooldowns().put(cooldown,17f);var beforeCooldown=new java.util.HashMap<>(source.getAbilityCooldowns());
            var beforeMove=source.getMoveGoal().getMoveTarget();int queued=UnitServerEvents.getUnitActionSlowQueue().size(),population=UnitServerEvents.getCurrentPopulation(source.getOwnerName()),units=UnitServerEvents.getAllUnits().size();
            net.minecraft.nbt.CompoundTag before=new net.minecraft.nbt.CompoundTag();source.saveWithoutId(before);var req=request(source);
            h.assertTrue(beforeMove!=null&&queued>0,"actual MOVE and queued second leg, not empty-state snapshot");
            for(int i=0;i<5;i++)h.assertTrue(ShadowSkillService.request(owner,req)==ShadowSkillService.Result.UNAVAILABLE,"legal duplicate request cannot invent paid or free summons");
            net.minecraft.nbt.CompoundTag after=new net.minecraft.nbt.CompoundTag();source.saveWithoutId(after);
            h.assertTrue(before.equals(after)&&beforeCooldown.equals(source.getAbilityCooldowns())&&java.util.Objects.equals(beforeMove,source.getMoveGoal().getMoveTarget())&&queued==UnitServerEvents.getUnitActionSlowQueue().size(),"saved and nonpersisted cooldown/command state unchanged");
            h.assertTrue(population==UnitServerEvents.getCurrentPopulation(source.getOwnerName())&&units==UnitServerEvents.getAllUnits().size(),"no population reservation or child registration");
            var r=ResourcesServerEvents.resourcesList.stream().filter(v->v.ownerName.equals(source.getOwnerName())).findFirst().orElseThrow();h.assertTrue(r.food==500&&r.wood==500&&r.ore==500,"no undefined payment or refund");source.getAbilityCooldowns().remove(cooldown);
            System.out.println("WEEPING_VALID_SUMMON_UNAVAILABLE_ZERO_MUTATION_PASS");
        }).thenSucceed();
    }
    @GameTest(template="weeping_unit_arena",batch=BATCH,timeoutTicks=160)
    public static void ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h) {
        float[] enemyHealth={0};floor(h);WeepingSquealGhostUnit source=spawn(h,"Owner",5,5);GoldenFoxUnit own=fox(h,"Owner",6,5),ally=fox(h,"Ally",7,5),enemy=fox(h,"Enemy",20,5);
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
        h.assertTrue(ShadowSkillService.request(player(h,"Owner"),wrongSkill)==ShadowSkillService.Result.INVALID_SOURCE,"skill and subtype must match exactly");
        var energy=new ShadowEnergyGate.Reservation(UUID.randomUUID(),owner.getUUID(),1);
        h.assertTrue(ShadowEnergyGate.reserve(owner,energy)==ShadowEnergyGate.Result.UNAVAILABLE,"positive energy reservation unavailable, never fake permission");
        h.assertTrue(ShadowEnergyGate.reserve(foreign,energy)==ShadowEnergyGate.Result.NOT_PAYER,"real sender bound to payer");
        h.assertTrue(ShadowEnergyGate.reserve(owner,new ShadowEnergyGate.Reservation(UUID.randomUUID(),owner.getUUID(),0))==ShadowEnergyGate.Result.INVALID_REQUEST,"zero energy cannot authorize free effects");
        h.assertTrue(ShadowEnergyGate.reserve(owner,new ShadowEnergyGate.Reservation(UUID.randomUUID(),owner.getUUID(),-1))==ShadowEnergyGate.Result.INVALID_REQUEST,"negative energy cannot grant currency");
        h.assertTrue(ShadowSkillService.request(null,request)==ShadowSkillService.Result.INVALID_REQUEST,"no sender rejected");
        h.assertTrue(ShadowSkillService.request(foreign,request)==ShadowSkillService.Result.NOT_OWNER,"same-faction foreign sender rejected");
        var stale=new ShadowSkillRequest(1,UUID.randomUUID(),ShadowSkillRequest.Skill.WEEPING_SQUEAL_SUMMON,new ShadowSkillRequest.Source(source.getId(),UUID.randomUUID(),source.rts().ownershipRevision,h.getLevel().dimension().location()),new ShadowSkillRequest.SelfTarget());
        h.assertTrue(ShadowSkillService.request(owner,stale)==ShadowSkillService.Result.INVALID_SOURCE,"ID reuse/stale UUID rejected");
        h.assertTrue(ShadowSkillService.request(owner,request)==ShadowSkillService.Result.UNAVAILABLE&&ShadowSkillService.request(owner,request)==ShadowSkillService.Result.UNAVAILABLE,"valid and duplicate requests never activate undefined summons");
        h.assertTrue(ShadowSkillService.request(owner,new ShadowSkillRequest(2,request.requestId(),request.skill(),request.caster(),request.target()))==ShadowSkillService.Result.INVALID_REQUEST,"future schema rejected");
        var wrongDimension=new ShadowSkillRequest(1,UUID.randomUUID(),ShadowSkillRequest.Skill.WEEPING_SQUEAL_SUMMON,new ShadowSkillRequest.Source(source.getId(),source.getUUID(),source.rts().ownershipRevision,net.minecraft.world.level.Level.NETHER.location()),request.target());
        h.assertTrue(ShadowSkillService.request(owner,wrongDimension)==ShadowSkillService.Result.WRONG_DIMENSION,"cross-dimension request fails closed");
        enemy.setOwnerName("");h.assertTrue(!source.doHurtTarget(enemy),"unowned target is not a valid paid combat target");enemy.setOwnerName(PREFIX+"Enemy");
        source.setOwnerName("");h.assertTrue(!source.doHurtTarget(enemy)&&ShadowSkillService.request(owner,request)==ShadowSkillService.Result.STALE_OWNER,"unowned source cannot fight or cast");
        source.setOwnerName(PREFIX+"Owner");h.assertTrue(ShadowSkillService.request(owner,request)==ShadowSkillService.Result.STALE_OWNER,"observed owner ABA invalidates old authority epoch");enemyHealth[0]=enemy.getHealth();
        }).thenIdle(20).thenExecute(()->{
            Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(source.getOwnerName())).findFirst().orElseThrow();
            h.assertTrue(r.food==500&&r.wood==500&&r.ore==500&&source.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.WEEPING_ECHOES))&&enemy.getHealth()==enemyHealth[0],"legacy ownership-only interface has no currency/cooldown/damage side effect");
            System.out.println("DREAMSEEKERS_WEEPING_SQUEAL_GHOST_TEAM_FILTER_RESERVED_SKILL_PASS");
        }).thenSucceed();
    }

    @GameTest(template="weeping_unit_arena",batch=BATCH,timeoutTicks=220)
    public static void friendlyFollowMovesAndRevokedAllianceCancelsItsPath(GameTestHelper h) {
        floor(h);WeepingSquealGhostUnit mother=spawn(h,"Follow",3,18);GoldenFoxUnit ally=fox(h,"FollowAlly",21,18);
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
        sequence.thenExecute(()->{followDiagnostic("final",mother,ally);System.out.println("DREAMSEEKERS_WEEPING_SQUEAL_GHOST_FOLLOW_REVOKE_PASS");}).thenSucceed();
    }
    private static void assertEmptyRevoked(GameTestHelper h,WeepingSquealGhostUnit mother,GoldenFoxUnit ally) {
        h.assertTrue(mother.getFollowTarget()==null&&mother.getMoveGoal().getMoveTarget()==null,
                "old friendly command stays canceled every observed tick");
        h.assertTrue(mother.getTarget()==null&&mother.getAttackBuildingGoal()==null,
                "empty fixture has no new entity/building combat target: "+followState(mother,ally));
    }
    @GameTest(template="weeping_unit_arena",batch=BATCH,timeoutTicks=220)
    public static void revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(GameTestHelper h) {
        floor(h);WeepingSquealGhostUnit mother=spawn(h,"Reacquire",3,18);
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
            h.assertTrue(newEnemy.getHealth()<hp[0],"actual new ranged damage, not stale friendly FOLLOW");
            h.assertTrue(formerAlly.getHealth()==allyHp[0],"distant former ally is not chased or hit");
            followDiagnostic("legitimate_new_aggro",mother,formerAlly);
            System.out.println("DREAMSEEKERS_WEEPING_SQUEAL_GHOST_FOLLOW_REVOKE_NEW_AGGRO_PASS");
        }).thenSucceed();
    }
    @GameTest(template="weeping_unit_arena",batch=BATCH,timeoutTicks=460)
    public static void sameEnemyExplicitAttackReleasesHoldAndReallyPursues(GameTestHelper h) {
        floor(h);var source=spawn(h,"HeldAttack",5,8);var enemy=fox(h,"HeldEnemy",16,8);var own=fox(h,"HeldAttack",20,18);
        source.setHoldPosition(true);Vec3 initial=source.position();float hp=enemy.getHealth();
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,source)&&published(h,enemy)&&h.getLevel().getEntity(source.getUUID())==source&&h.getLevel().getEntity(enemy.getUUID())==enemy,"real published held combatants"))
            .thenIdle(25).thenExecute(()->{
                h.assertTrue(source.getHoldPosition()&&source.getTarget()==enemy&&source.position().distanceToSqr(initial)<.1&&enemy.getHealth()==hp,"HOLD has already selected this actual enemy but has not chased it");
                source.setUnitAttackTargetForced(own);
                h.assertTrue(source.getHoldPosition()&&source.getTarget()==null,"invalid allied forced target cannot release HOLD");
                source.getTargetGoal().setTarget(enemy);
                h.assertTrue(source.getTarget()==enemy,"restore exactly the same selected enemy before real ATTACK path");
                new com.solegendary.reignofnether.unit.UnitActionItem(source.getOwnerName(),UnitAction.ATTACK,enemy.getId(),new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                System.out.println("SQUEAL_SAME_TARGET_ATTACK_AFTER_DISPATCH hold="+source.getHoldPosition()+" selected="+(source.getTarget()==enemy)+" forced="+source.getTargetGoal().forced);
                h.assertTrue(!source.getHoldPosition()&&source.getTarget()==enemy&&source.getTargetGoal().forced,"valid same-enemy explicit ATTACK must release HOLD despite core redundant-target reset optimization");
            }).thenIdle(220).thenExecute(()->h.assertTrue(source.position().distanceToSqr(initial)>.25&&enemy.getHealth()<hp,"released unit really pursues and deals ranged damage, not merely flips a flag")).thenSucceed();
    }


    private static ShadowSkillRequest request(WeepingSquealGhostUnit e){return new ShadowSkillRequest(1,UUID.randomUUID(),ShadowSkillRequest.Skill.WEEPING_SQUEAL_SUMMON,new ShadowSkillRequest.Source(e.getId(),e.getUUID(),e.rts().ownershipRevision,e.level().dimension().location()),new ShadowSkillRequest.SelfTarget());}
    private static String followState(WeepingSquealGhostUnit mother,GoldenFoxUnit ally) {
        var buildingGoal=(TeamSafeBuildingAttackGoal)mother.getAttackBuildingGoal();
        var building=buildingGoal==null?null:buildingGoal.getBuildingTarget();
        return "tick="+mother.tickCount+" pos="+mother.position()+" ally="+ally.position()+" distance="+mother.distanceTo(ally)
                +" velocity="+mother.getDeltaMovement()+" follow="+mother.getFollowTarget()+" move="+mother.getMoveGoal().getMoveTarget()
                +" navDone="+mother.getNavigation().isDone()+" attack="+mother.getTarget()+" building="+(building==null?null:building.originPos+":"+building.ownerName)
                +" buildingMove="+(buildingGoal==null?null:buildingGoal.getMoveTarget())+" lastDamage="+mother.getLastDamageSource()+" anchor="+mother.getAnchor();
    }
    private static void followDiagnostic(String phase,WeepingSquealGhostUnit mother,GoldenFoxUnit ally) {
        System.out.println("DREAMSEEKERS_WEEPING_SQUEAL_GHOST_FOLLOW_DIAGNOSTIC phase="+phase+" "+followState(mother,ally)+" nearby="+mother.level().getEntitiesOfClass(LivingEntity.class,mother.getBoundingBox().inflate(10)).stream().map(e->e.getType()+":"+e.getId()+":"+(e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u?u.getOwnerName():"wild")+":"+e.position()).toList());
    }
    private static String account(String suffix){String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));}return name;}
    private static WeepingSquealGhostUnit spawn(GameTestHelper h,String name,int x,int z){WeepingSquealGhostUnit e=DreamseekersMod.WEEPING_SQUEAL_GHOST_UNIT.get().create(h.getLevel());e.setOwnerName(account(name));place(h,e,x,z);return e;}
    private static GoldenFoxUnit fox(GameTestHelper h,String name,int x,int z){GoldenFoxUnit e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);e.setHealth(500);e.setNoAi(true);e.setOwnerName(account(name));place(h,e,x,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"fixture enters actual world");}
    private static FakePlayer player(GameTestHelper h,String suffix){String name=PREFIX+suffix;FakePlayer p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));PLAYERS.add(p);return p;}
    private static void order(WeepingSquealGhostUnit e,UnitAction a,int target,BlockPos pos){UnitServerEvents.addActionItem(e.getOwnerName(),a,target,new int[]{e.getId()},pos,BlockPos.ZERO);}
    private static AABB area(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);return new AABB(p.getX(),p.getY()+.5,p.getZ(),p.getX()+24,p.getY()+10,p.getZ()+24);}
    private static void floor(GameTestHelper h){for(int i=-1;i<=24;i++)for(int yy=1;yy<=10;yy++){h.getLevel().setBlock(h.absolutePos(new BlockPos(-1,yy,i)),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(24,yy,i)),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(i,yy,-1)),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(i,yy,24)),Blocks.STONE.defaultBlockState(),2);}BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=p.getX()>>4;x<=(p.getX()+23)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+23)>>4;z++){var c=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(x,z,true);TICKETS.add(c);}}for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<10;y++)h.getLevel().setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}h.getLevel().getEntitiesOfClass(ItemEntity.class,area(h)).forEach(Entity::discard);}
    private static boolean published(GameTestHelper h,Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}

}
