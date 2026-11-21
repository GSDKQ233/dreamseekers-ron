// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.building.*;
import com.dreamseekers.function.DreamWatchtowerPlacement;
import com.dreamseekers.unit.FireflyWorker;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real one-shot worker/garrison orders after publication. Additional cross-JVM/client coverage is separate. */
@GameTestHolder("dreamseekers_shell_functions")
@PrefixGameTestTemplate(false)
public final class DreamShellFunctionGameTests {
    private static final String BATCH="dreamseekers_shell_functions";
    private static final List<ShellNativeFixture> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var f:FIXTURES)f.close();FIXTURES.clear();}
    private static final class ShellNativeFixture {
        final GameTestHelper h;final ServerLevel level;final String owner;final Resources account;
        final List<LivingEntity> units=new ArrayList<>();final List<BuildingPlacement> buildings=new ArrayList<>();
        final List<Object> probes=new ArrayList<>();
        final Set<Long> forced=new HashSet<>();final Set<String> extraOwners=new HashSet<>();
        ShellNativeFixture(GameTestHelper helper,String key) {
            h=helper;level=h.getLevel();owner="ShellNativeGT_"+key;FIXTURES.add(this);
            h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null,"unique exact case owner");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
            account=new Resources(owner,0,0,0);ResourcesServerEvents.resourcesList.add(account);
        }
        BlockPos at(int x,int y,int z){return BuildingAssetGameTests.outdoorPos(h,x,y,z);}
        void prepare(BlockPos origin) {
            for(int cx=(origin.getX()-20)>>4;cx<=(origin.getX()+25)>>4;cx++)for(int cz=(origin.getZ()-20)>>4;cz<=(origin.getZ()+25)>>4;cz++) {
                long id=ChunkPos.asLong(cx,cz);if(!level.getForcedChunks().contains(id)){level.setChunkForced(cx,cz,true);forced.add(id);}
            }
            for(int dx=-20;dx<=25;dx++)for(int dz=-20;dz<=25;dz++) {
                level.setBlock(origin.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);
                for(int dy=1;dy<=18;dy++)level.setBlock(origin.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);
            }
        }
        BuildingPlacement install(Building definition,BlockPos origin,Rotation rotation) {
            for(var b:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,origin,rotation))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            var p=BuildingUtils.getNewBuildingPlacement(definition,level,origin,rotation,owner,false);
            BuildingServerEvents.getBuildings().add(p);buildings.add(p);p.onBuilt();return p;
        }
        <T extends Mob> T spawn(EntityType<T> type,BlockPos pos) {
            T entity=type.create(level);h.assertTrue(entity instanceof Unit,"real native RTS unit");((Unit)entity).setOwnerName(owner);
            entity.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);h.assertTrue(level.addFreshEntity(entity),"unit admitted");units.add(entity);return entity;
        }
        void ready(Entity entity) {
            h.assertTrue(entity.tickCount>2&&level.getEntity(entity.getId())==entity&&level.getEntity(entity.getUUID())==entity
                &&level.isPositionEntityTicking(entity.blockPosition())&&level.areEntitiesLoaded(new ChunkPos(entity.blockPosition()).toLong()),"same-object ID/UUID published and actual entity-ticking before one-shot command");
        }
        void command(UnitAction action,LivingEntity entity,BlockPos target){UnitServerEvents.addActionItem(owner,action,-1,new int[]{entity.getId()},target,BlockPos.ZERO);}
        void pass(String marker){System.out.println("DREAM_SHELL_FUNCTION_PASS "+marker);h.succeed();}
        void close(){
            for(var probe:probes)net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(probe);
            for(String extra:extraOwners){PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(extra));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(extra));}
            // Remove exact owner bookkeeping before fixture teardown can trigger native defeat side effects.
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(owner));
            for(var unit:units){if(unit instanceof Unit u)Unit.fullResetBehaviours(u);if(!unit.isRemoved())unit.discard();}
            for(var p:buildings){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}
            for(long id:forced){var p=new ChunkPos(id);level.setChunkForced(p.x,p.z,false);}
        }
    }
    /** Exact fixture-scoped observation only: never edits or fabricates damage. */
    private static final class ShellStoneDamageProbe {
        final ShellNativeFixture fixture;final LivingEntity target;int hits;
        ShellStoneDamageProbe(ShellNativeFixture fixture,LivingEntity target){this.fixture=fixture;this.target=target;}
        final java.util.Set<Integer> shots=new java.util.HashSet<>();
        private com.dreamseekers.unit.StoneSpiritThrowerUnit source(){
            for(var unit:fixture.units)if(unit instanceof com.dreamseekers.unit.StoneSpiritThrowerUnit stone&&stone.getOwnerName().equals(fixture.owner))return stone;
            return null;
        }
        private void log(String value){System.out.println("DREAM_STONE_TOWER_DIAG t="+fixture.level.getGameTime()+" "+value);}
        private static Object windup(Object goal){try{var field=goal.getClass().getDeclaredField("attackWindupTicksLeft");field.setAccessible(true);return field.get(goal);}catch(ReflectiveOperationException failure){return failure.getClass().getSimpleName();}}
        @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
        public void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
            if(event.getEntity() instanceof com.dreamseekers.stone.StoneProjectile shot&&shot.getOwner()==source()){
                shots.add(shot.getId());log("JOIN shot="+shot.getId()+" pos="+shot.position()+" velocity="+shot.getDeltaMovement()+" canceled="+event.isCanceled());
            }
        }
        @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
        public void impact(net.minecraftforge.event.entity.ProjectileImpactEvent event){
            if(shots.contains(event.getProjectile().getId())){
                var hit=event.getRayTraceResult();String detail=hit instanceof net.minecraft.world.phys.BlockHitResult block?" block="+block.getBlockPos()+" state="+fixture.level.getBlockState(block.getBlockPos()):hit instanceof net.minecraft.world.phys.EntityHitResult entity?" entity="+entity.getEntity().getId():"";
                log("IMPACT shot="+event.getProjectile().getId()+" age="+event.getProjectile().tickCount+" type="+hit.getType()+" point="+hit.getLocation()+detail+" canceled="+event.isCanceled());
            }
        }
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void left(net.minecraftforge.event.entity.EntityLeaveLevelEvent event){if(shots.contains(event.getEntity().getId()))log("LEAVE shot="+event.getEntity().getId()+" age="+event.getEntity().tickCount+" pos="+event.getEntity().position());}
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void tick(net.minecraftforge.event.TickEvent.LevelTickEvent event){
            if(event.level!=fixture.level||event.phase!=net.minecraftforge.event.TickEvent.Phase.END||fixture.level.getGameTime()%20!=0)return;
            var source=source();if(source==null)return;
            var eye=source.getEyePosition();var aim=target.getBoundingBox().getCenter();
            var ray=fixture.level.clip(new net.minecraft.world.level.ClipContext(eye,aim,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,source));
            var goal=(com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal<?>)source.getAttackGoal();
            log("STATE source="+source.position()+" eye="+eye+" target="+target.position()+" aim="+aim+" hp="+target.getHealth()
                +" selected="+(source.getTarget()==target)+" garrison="+source.isGarrisoned()+" hold="+source.getHoldPosition()+" anchorPermit="+source.permitsAutomaticAnchorReturn()
                +" LOS="+source.hasLineOfSight(target)+" centerRay="+ray.getType()+" rayPoint="+ray.getLocation()+" rayBlock="+ray.getBlockPos()+" block="+fixture.level.getBlockState(ray.getBlockPos())
                +" cooldown="+goal.getAttackCooldown()+" windup="+windup(goal)+" shots="+shots.size()+" hits="+hits+" stun="+source.hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get())
                +" activeGoals="+source.goalSelector.getRunningGoals().map(w->w.getGoal().getClass().getSimpleName()).toList());
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public void damage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
            if(event.getEntity()==target&&event.getAmount()>0&&event.getSource().getDirectEntity() instanceof com.dreamseekers.stone.StoneProjectile shot
                &&shot.getOwner() instanceof com.dreamseekers.unit.StoneSpiritThrowerUnit source&&fixture.units.contains(source)
                &&source.getOwnerName().equals(fixture.owner)&&source.isGarrisoned())hits++;
        }
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1200)
    public static void actualFireflyDeliversOneDeclaredBundleToDepot(GameTestHelper h) {
        var f=new ShellNativeFixture(h,"Depot");var origin=f.at(12,1,12);f.prepare(origin);
        var depot=f.install(DreamseekersMod.DREAM_DEPOT,origin,Rotation.NONE);
        FireflyWorker worker=f.spawn(DreamseekersMod.FIREFLY_WORKER.get(),origin.offset(3,2,-8));
        worker.getItems().add(new ItemStack(Items.WHEAT,2));worker.getItems().add(new ItemStack(Items.OAK_LOG,2));worker.getItems().add(new ItemStack(Items.RAW_IRON,2));
        var expected=Resources.getTotalResourcesFromItems(worker.getItems());h.assertTrue(expected.food>0&&expected.wood>0&&expected.ore>0,"real declared FWO resource valuation");
        int[] deliveredTick={-1};
        h.startSequence().thenWaitUntil(()->f.ready(worker)).thenExecute(()->f.command(UnitAction.RETURN_RESOURCES,worker,depot.originPos))
            .thenWaitUntil(()->{
                h.assertTrue(f.account.food==expected.food&&f.account.wood==expected.wood&&f.account.ore==expected.ore,"actual native worker route credits exactly declared bundle");
                h.assertTrue(worker.getItems().isEmpty(),"cargo consumed by real delivery");deliveredTick[0]=worker.tickCount;
            }).thenWaitUntil(()->h.assertTrue(worker.tickCount>=deliveredTick[0]+30,"observe no second credit"))
            .thenExecute(()->{
                h.assertTrue(f.account.food==expected.food&&f.account.wood==expected.wood&&f.account.ore==expected.ore,"no duplicate or passive income");
                h.assertTrue(f.level.noCollision(worker),"worker delivery did not clip geometry");f.pass("DEPOT_NATIVE_SINGLE_DELIVERY");
            });
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1200)
    public static void actualDepotRejectsUnfinishedForeignAndRemovedTargets(GameTestHelper h) {
        var f=new ShellNativeFixture(h,"DepotReject");var origin=f.at(12,1,12);f.prepare(origin);
        var depot=f.install(DreamseekersMod.DREAM_DEPOT,origin,Rotation.NONE);depot.isBuilt=false;
        // Keep a real incomplete block rather than allowing core tick to auto-complete the fixture.
        var missing=depot.getBlocks().stream().filter(b->!b.getBlockState().isAir()&&b.getBlockPos().getY()>origin.getY()+1).findFirst().orElseThrow();
        f.level.setBlock(missing.getBlockPos(),Blocks.AIR.defaultBlockState(),2);depot.onBlockBreak(f.level,missing.getBlockPos(),false);
        FireflyWorker worker=f.spawn(DreamseekersMod.FIREFLY_WORKER.get(),origin.offset(3,2,-2));worker.getItems().add(new ItemStack(Items.WHEAT,2));
        int food=Resources.getTotalResourcesFromItems(worker.getItems()).food;
        h.startSequence().thenWaitUntil(()->f.ready(worker)).thenExecute(()->f.command(UnitAction.RETURN_RESOURCES,worker,depot.originPos))
            .thenWaitUntil(()->h.assertTrue(worker.getReturnResourcesGoal().hasReturnOrder() && worker.getReturnResourcesGoal().getBuildingTarget()==null,"native return command retained, but an incomplete depot is not an active destination"))
            .thenIdle(70).thenExecute(()->{
                h.assertTrue(!depot.isBuilt&&f.account.food==0&&Resources.getTotalResourcesFromItems(worker.getItems()).food==food,"real incomplete depot cannot credit cargo");
                depot.isBuilt=true;depot.setOwnerNameFromServerEditor(f.owner+"Foreign");
            }).thenIdle(30).thenExecute(()->{
                h.assertTrue(f.account.food==0&&Resources.getTotalResourcesFromItems(worker.getItems()).food==food,"real foreign depot cannot credit cargo");
                BuildingServerEvents.getBuildings().remove(depot);depot.setOwnerNameFromServerEditor(f.owner);
            }).thenIdle(30).thenExecute(()->{
                h.assertTrue(f.account.food==0&&Resources.getTotalResourcesFromItems(worker.getItems()).food==food,"removed target cannot credit stale native return order");
                f.install(DreamseekersMod.DREAM_DEPOT,origin.offset(-12,0,-8),Rotation.NONE);
            }).thenWaitUntil(()->h.assertTrue(f.account.food==food && worker.getItems().isEmpty(),
                    "same pending order finds the new owned depot and flies there without a second player command"))
            .thenIdle(30).thenExecute(()->{
                h.assertTrue(f.account.food==food && !worker.getReturnResourcesGoal().hasReturnOrder(),"new depot credited exactly once");
                f.pass("DEPOT_NATIVE_REJECT_AND_REDIRECT");
            });
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1200)
    public static void actualFarChunkUnloadDisablesDaisWithoutRemovingTrackedPlacement(GameTestHelper h) {
        var f=new ShellNativeFixture(h,"AuraUnload");var origin=f.at(8192,1,8192);f.prepare(origin);
        var host=(ProductionPlacement)f.install(DreamseekersMod.DREAM_ANCHOR,origin,Rotation.NONE);
        var dais=f.install(DreamseekersMod.RESONANCE_DAIS,origin.offset(12,0,0),Rotation.NONE);
        h.assertTrue(DreamResonanceAura.active(host),"loaded far fixtures initially activate aura");
        host.forceChunk(false);dais.forceChunk(false);
        for(long id:List.copyOf(f.forced)){var chunk=new ChunkPos(id);f.level.setChunkForced(chunk.x,chunk.z,false);}f.forced.clear();
        h.startSequence().thenWaitUntil(()->h.assertTrue(!f.level.hasChunksAt(host.minCorner,host.maxCorner)||!f.level.hasChunksAt(dais.minCorner,dais.maxCorner),"wait for actual chunk unload, not a pretend flag"))
            .thenExecute(()->{
                h.assertTrue(BuildingServerEvents.getBuildings().contains(host)&&BuildingServerEvents.getBuildings().contains(dais),"both ordinary placements remain tracked through unload");
                h.assertTrue(!DreamResonanceAura.active(host),"actual unload removes aura without stale permanent state");f.pass("DAIS_ACTUAL_UNLOAD");
            });
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=150)
    public static void daisRangeOwnershipAndOverlapArePureNonstackingEligibility(GameTestHelper h) {
        var f=new ShellNativeFixture(h,"Aura");var origin=f.at(20,1,20);f.prepare(origin);
        var host=(ProductionPlacement)f.install(DreamseekersMod.DREAM_ANCHOR,origin,Rotation.NONE);
        h.assertTrue(!DreamResonanceAura.active(host),"no dais no aura");
        assertDaisRates(h,host,100,100,"no Wind and no local aura");
        var first=f.install(DreamseekersMod.RESONANCE_DAIS,origin.offset(12,8,0),Rotation.NONE);
        h.assertTrue(DreamResonanceAura.active(host),"inclusive horizontal12 vertical8");
        assertDaisRates(h,host,110,110,"one local aura without Wind");
        var second=f.install(DreamseekersMod.RESONANCE_DAIS,origin.offset(-12,8,0),Rotation.NONE);
        h.assertTrue(DreamResonanceAura.active(host),"two overlaps still one boolean bonus");
        assertDaisRates(h,host,110,110,"overlap adds no second local bonus");
        // Direct trusted hooks set canonical fixture state only; this does not demonstrate paid branch/research completion.
        h.assertTrue(com.dreamseekers.progression.DreamWindProgression.earnWind(f.level,f.owner,f.account.accountEpoch),"epoch-bound Wind entitlement fixture accepted");
        assertDaisRates(h,host,150,135,"Wind base plus one nonstacking local aura");
        for(int family=0;family<3;family++)for(int tier=1;tier<=5;tier++)
            com.dreamseekers.progression.DreamWindProgression.commitTier(f.account,family,tier);
        for(int family=0;family<3;family++)h.assertTrue(com.dreamseekers.progression.DreamWindProgression.read(f.account).level(family)==5,"canonical contiguous level-five fixture");
        assertDaisRates(h,host,200,185,"maximum levels plus overlapping auras respect training200 research185 caps");
        first.setOwnerNameFromServerEditor(f.owner+"Other");second.isBuilt=false;
        h.assertTrue(!DreamResonanceAura.active(host),"foreign and unfinished do not contribute");
        assertDaisRates(h,host,190,175,"foreign and deactivated auras immediately remove local increment");
        first.setOwnerNameFromServerEditor(f.owner);first.isDestroyedServerside=true;
        h.assertTrue(!DreamResonanceAura.active(host),"destroyed cannot contribute");
        first.isDestroyedServerside=false;BuildingServerEvents.getBuildings().remove(first);
        h.assertTrue(!DreamResonanceAura.active(host),"removed cannot contribute");
        assertDaisRates(h,host,190,175,"removed aura does not leave permanent work bonus");
        var beyond=f.install(DreamseekersMod.RESONANCE_DAIS,origin.offset(0,9,12),Rotation.NONE);
        h.assertTrue(!DreamResonanceAura.active(host),"vertical9 excluded");
        beyond.isBuilt=false;
        // Range is footprint to footprint: gaps are measured from the placements' real corners.
        var diagonal=f.install(DreamseekersMod.RESONANCE_DAIS,new BlockPos(host.maxCorner.getX()+12,origin.getY(),host.maxCorner.getZ()+2),Rotation.NONE);
        h.assertTrue(diagonal.minCorner.getX()-host.maxCorner.getX()==12&&diagonal.minCorner.getZ()-host.maxCorner.getZ()==2,"fixture gap12,2");
        h.assertTrue(!DreamResonanceAura.active(host),"Euclidean radius excludes a12,2 footprint gap");
        diagonal.isBuilt=false;
        var west=f.install(DreamseekersMod.RESONANCE_DAIS,new BlockPos(host.minCorner.getX()-21,origin.getY(),origin.getZ()),Rotation.NONE);
        h.assertTrue(host.minCorner.getX()-west.maxCorner.getX()==13&&!DreamResonanceAura.active(host),"footprint gap13 on the far side excluded");
        west.isBuilt=false;
        var touching=f.install(DreamseekersMod.RESONANCE_DAIS,new BlockPos(host.minCorner.getX()-9,origin.getY(),origin.getZ()),Rotation.NONE);
        h.assertTrue(host.minCorner.getX()-touching.maxCorner.getX()==1&&DreamResonanceAura.active(host),"dais touching the side away from the origin corner is covered");
        f.pass("DAIS_RANGE_OWNER_OVERLAP");
    }
    private static void assertDaisRates(GameTestHelper h,ProductionPlacement host,int training,int research,String phase) {
        var unit=DreamseekersMod.WORKER_PRODUCTION;
        var technology=com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_EXPANDED_WORKFORCE;
        h.assertTrue(technology.workKind==com.solegendary.reignofnether.building.production.ProductionItem.WorkKind.RESEARCH,"registered research explicitly classified, not population/name heuristic");
        h.assertTrue(com.solegendary.reignofnether.building.production.ProductionWorkRates.percent(host,unit)==training,"actual training rate: "+phase);
        h.assertTrue(com.solegendary.reignofnether.building.production.ProductionWorkRates.percent(host,technology)==research,"actual explicit research rate: "+phase);
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1200)
    public static void towerNativeGarrisonRotationNorth(GameTestHelper h){towerNative(h,Rotation.NONE,"N");}
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1200)
    public static void towerNativeGarrisonRotationEast(GameTestHelper h){towerNative(h,Rotation.CLOCKWISE_90,"E");}
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1200)
    public static void towerNativeGarrisonRotationSouth(GameTestHelper h){towerNative(h,Rotation.CLOCKWISE_180,"S");}
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1200)
    public static void towerNativeGarrisonRotationWest(GameTestHelper h){towerNative(h,Rotation.COUNTERCLOCKWISE_90,"W");}
    private static void towerNative(GameTestHelper h,Rotation rotation,String suffix) {
        var f=new ShellNativeFixture(h,"Tower"+suffix);var origin=f.at(20,1,20);f.prepare(origin);
        var tower=(DreamWatchtowerPlacement)f.install(DreamseekersMod.SHADOW_WATCHTOWER,origin,rotation);
        var definition=(DreamWatchtower)tower.getBuilding();var bodies=new ArrayList<LivingEntity>();
        for(int i=0;i<4;i++)bodies.add(f.spawn(EntityRegistrar.PILLAGER_UNIT.get(),new BlockPos(1+i,2,-4).rotate(rotation).offset(origin)));
        int[] issued={-1};
        h.startSequence().thenWaitUntil(()->bodies.forEach(f::ready)).thenExecute(()->{
            for(int i=0;i<3;i++)f.command(UnitAction.GARRISON,bodies.get(i),tower.originPos);
            issued[0]=bodies.get(0).tickCount;
        }).thenWaitUntil(()->{
            for(int i=0;i<3;i++)h.assertTrue(GarrisonableBuildingAddon.getGarrison((Unit)bodies.get(i))==tower,"native one-shot garrison occupies three seats");
        }).thenExecute(()->{
            for(int i=0;i<3;i++)for(int j=i+1;j<3;j++)h.assertTrue(!bodies.get(i).getBoundingBox().intersects(bodies.get(j).getBoundingBox()),"actual seated bodies do not overlap");
            h.assertTrue(definition.isFull(tower)&&definition.getEntryPosition(tower,bodies.get(3))==null,"fourth physical admission refused");
            f.command(UnitAction.GARRISON,bodies.get(3),tower.originPos);
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(GarrisonableBuildingAddon.getGarrison((Unit)bodies.get(3))!=tower,"real fourth command does not overfill");
            h.assertTrue(definition.getExitPosition(tower,bodies.get(0))!=null,"safe actual egress available");
            f.command(UnitAction.UNGARRISON,bodies.get(0),tower.originPos);
        }).thenWaitUntil(()->h.assertTrue(GarrisonableBuildingAddon.getGarrison((Unit)bodies.get(0))!=tower,"native UNGARRISON left platform"))
            .thenExecute(()->{h.assertTrue(f.level.noCollision(bodies.get(0)),"native egress clear");f.pass("TOWER_NATIVE_ROTATION_"+suffix);});
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1200)
    public static void emptyTowerIsInertAndNativeRangedOccupantActuallyDefends(GameTestHelper h) {
        var f=new ShellNativeFixture(h,"TowerCombat");var origin=f.at(20,1,20);f.prepare(origin);
        var tower=(DreamWatchtowerPlacement)f.install(DreamseekersMod.SHADOW_WATCHTOWER,origin,Rotation.NONE);
        String enemyOwner=f.owner+"Enemy";f.extraOwners.add(enemyOwner);
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(enemyOwner,com.solegendary.reignofnether.faction.Factions.VILLAGERS));
        ResourcesServerEvents.resourcesList.add(new Resources(enemyOwner,0,0,0));
        var enemy=f.spawn(EntityRegistrar.PILLAGER_UNIT.get(),origin.offset(3,1,24));((Unit)enemy).setOwnerName(enemyOwner);enemy.setNoAi(true);
        float health=enemy.getHealth();LivingEntity[] defender={null};
        var shotProbe=new ShellStoneDamageProbe(f,enemy);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(shotProbe);f.probes.add(shotProbe);
        h.startSequence().thenWaitUntil(()->f.ready(enemy)).thenIdle(60).thenExecute(()->{
            h.assertTrue(enemy.getHealth()==health&&GarrisonableBuildingAddon.getNumOccupants(tower)==0,"empty tower inflicted no damage during actual ticks");
            defender[0]=f.spawn(DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),origin.offset(3,1,-6));
            ((Unit)defender[0]).setAnchor(origin.offset(3,1,-6));
        }).thenWaitUntil(()->{h.assertTrue(defender[0]!=null,"defender created only after empty observation");f.ready(defender[0]);})
            .thenExecute(()->f.command(UnitAction.GARRISON,defender[0],tower.originPos))
            .thenWaitUntil(()->h.assertTrue(GarrisonableBuildingAddon.getGarrison((Unit)defender[0])==tower,"actual native defender reached platform"))
            .thenExecute(()->{
                h.assertTrue(enemy.getHealth()==health,"no pre-garrison attack contaminates occupied defense observation");
                h.assertTrue(defender[0] instanceof com.dreamseekers.unit.StoneSpiritThrowerUnit stone&&stone.canGarrison()&&stone.getHoldPosition()
                    &&!stone.permitsAutomaticAnchorReturn()&&stone.distanceTo(enemy)>stone.getAttackRange()&&stone.hasLineOfSight(enemy),
                    "own Dream thrower holds real seat, suppresses anchor return, and has clear physical shot beyond base range");
                new UnitActionItem(f.owner,UnitAction.ATTACK,enemy.getId(),new int[]{defender[0].getId()},BlockPos.ZERO,BlockPos.ZERO).action(f.level);
            }).thenWaitUntil(()->h.assertTrue(enemy.getHealth()<health&&shotProbe.hits>0,"actual StoneProjectile damage event and health loss from seated Dream defender"))
            .thenExecute(()->{
                h.assertTrue(GarrisonableBuildingAddon.getGarrison((Unit)defender[0])==tower,"defender stayed garrisoned while firing");
                h.assertTrue(f.level.noCollision(defender[0]),"occupied firing position collision-safe");f.pass("TOWER_DREAM_STONE_DEFENSE_EMPTY_INERT");
            });
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=150)
    public static void towerBlockedFloorExitLegacyAndSavedModelFailClosed(GameTestHelper h) {
        var f=new ShellNativeFixture(h,"TowerSafety");var origin=f.at(20,1,20);f.prepare(origin);
        var tower=(DreamWatchtowerPlacement)f.install(DreamseekersMod.SHADOW_WATCHTOWER,origin,Rotation.NONE);
        var definition=(DreamWatchtower)tower.getBuilding();var body=f.spawn(EntityRegistrar.PILLAGER_UNIT.get(),origin.offset(3,2,-3));body.setNoAi(true);
        h.startSequence().thenWaitUntil(()->f.ready(body)).thenExecute(()->{
            h.assertTrue(definition.getEntryPosition(tower,body)!=null,"baseline safe real body");
            for(int x=2;x<=4;x++)f.level.setBlock(new BlockPos(x,7,5).offset(origin).above(),Blocks.AIR.defaultBlockState(),2);
            h.assertTrue(definition.getEntryPosition(tower,body)==null,"missing actual floor refuses entry");
            for(var block:tower.getBlocks())if(block.getBlockPos().getY()==origin.getY()+8)f.level.setBlock(block.getBlockPos(),block.getBlockState(),2);
            var at=definition.getEntryPosition(tower,body);h.assertTrue(at!=null,"repaired true floor restores eligibility");body.teleportTo(at.getX()+.5,at.getY()+.5,at.getZ()+.5);
            for(int x=2;x<=4;x++)f.level.setBlock(new BlockPos(x,1,1).offset(origin).above(),Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(definition.getExitPosition(tower,body)==null,"all actual exits blocked refuses unsafe teleport");
            CompoundTag snapshot=tower.structureSnapshot();h.assertTrue(DreamWatchtowerPlacement.validSnapshot(snapshot),"real server snapshot validates");
            CompoundTag oversized=snapshot.copy();oversized.putInt("placed",177);h.assertTrue(!DreamWatchtowerPlacement.validSnapshot(oversized),"count bounded by trusted revision");
            var saved=DataStorage.read(tower.getDataStorage().write(),f.level.getServer());
            var restored=(DreamWatchtowerPlacement)definition.createRestoredBuildingPlacement(f.level,origin,Rotation.NONE,f.owner,saved);
            restored.isBuilt=true;restored.partialBlocksDestroyed=tower.partialBlocksDestroyed;restored.afterRestoredFromSave();
            h.assertTrue(restored.modelRevision()==3&&restored.getBlocksTotal()==176,"actual DataStorage roundtrip retains new descriptor");
            var oldOrigin=origin.offset(25,0,0);
            for(var block:BuildingUtils.getAbsoluteBlockData(DreamWatchtowerPlacement.template(1),f.level,oldOrigin,Rotation.NONE))f.level.setBlock(block.getBlockPos(),block.getBlockState(),2);
            var legacy=new DreamWatchtowerPlacement(definition,f.level,oldOrigin,Rotation.NONE,f.owner,new DataStorage());
            BuildingServerEvents.getBuildings().add(legacy);f.buildings.add(legacy);legacy.isBuilt=true;legacy.afterRestoredFromSave();legacy.getDataStorage();
            h.assertTrue(legacy.modelRevision()==1&&legacy.getBlocksTotal()==236&&definition.getEntryPosition(legacy,body)==null&&!definition.isGarrisonEnabled(legacy),"legacy remains solid and inert without silent migration");
            body.teleportTo(oldOrigin.getX()+3.5,oldOrigin.getY()+11,oldOrigin.getZ()+4.5);
            h.assertTrue(GarrisonableBuildingAddon.getGarrison((Unit)body)!=legacy,"manual elevated legacy unit receives no range/sight garrison authority");
            var unknownData=DataStorage.read(tower.getDataStorage().write(),f.level.getServer());
            var unknown=unknownData.getData(DreamWatchtowerPlacement.MODEL).copy();unknown.putInt("schema",99);unknown.putString("futurePayload","preserve-me");unknownData.setData(DreamWatchtowerPlacement.MODEL,unknown);
            var inert=(DreamWatchtowerPlacement)definition.createRestoredBuildingPlacement(f.level,origin,Rotation.NONE,f.owner,unknownData);
            inert.isBuilt=true;inert.afterRestoredFromSave();
            h.assertTrue(inert.modelRevision()==-1&&!definition.isGarrisonEnabled(inert)&&inert.getDataStorage().getData(DreamWatchtowerPlacement.MODEL).equals(unknown),"unknown saved payload remains inert and byte-equivalent at tag level");
            f.pass("TOWER_SAFETY_MODEL_ROUNDTRIP");
        });
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=150)
    public static void savedTowerDamageAndIncompleteProgressNeverHealOrMigrate(GameTestHelper h) {
        var f=new ShellNativeFixture(h,"TowerRestoreDamage");
        var definition=(DreamWatchtower)DreamseekersMod.SHADOW_WATCHTOWER;
        // Actual old model, no version tag: completed then seven real blocks lost.
        var oldOrigin=f.at(20,1,20);f.prepare(oldOrigin);
        var oldGeometry=BuildingUtils.getAbsoluteBlockData(DreamWatchtowerPlacement.template(1),f.level,oldOrigin,Rotation.NONE);
        for(var block:oldGeometry)f.level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        var old=new DreamWatchtowerPlacement(definition,f.level,oldOrigin,Rotation.NONE,f.owner,new DataStorage());
        old.isBuilt=true;old.afterRestoredFromSave();
        var oldSolid=oldGeometry.stream().filter(b->!b.getBlockState().isAir()).toList();
        for(int i=0;i<7;i++){var p=oldSolid.get(i).getBlockPos();f.level.setBlock(p,Blocks.AIR.defaultBlockState(),2);old.onBlockBreak(f.level,p,false);}
        old.partialBlocksDestroyed=.625;
        int cachedOldPlaced=old.getBlocksPlaced();old.initPlacedBlocks();
        System.out.println("TOWER_RESTORE_FIXTURE_WORLD_RECONCILE cached="+cachedOldPlaced+" actual="+old.getBlocksPlaced());
        for(var b:oldGeometry)if(!b.getBlockState().isAir()&&!b.isPlaced(f.level))System.out.println("TOWER_RESTORE_FIXTURE_MISSING "+b.getBlockPos()+" expected="+b.getBlockState()+" actual="+f.level.getBlockState(b.getBlockPos()));
        int oldPlaced=old.getBlocksPlaced(),oldHealth=old.getHealth();
        h.assertTrue(oldPlaced>0&&oldPlaced<=229&&old.getHighestBlockCountReached()==236,"legacy fixture has at least seven true missing blocks and full historical maximum after world reconciliation");
        var oldWorld=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var block:oldGeometry)oldWorld.put(block.getBlockPos(),f.level.getBlockState(block.getBlockPos()));
        // Model-less input intentionally exercises pre-version saves; native loader assigns these ordinary fields before hook.
        var oldReload=(DreamWatchtowerPlacement)definition.createRestoredBuildingPlacement(f.level,oldOrigin,Rotation.NONE,f.owner,new DataStorage());
        oldReload.isBuilt=true;oldReload.partialBlocksDestroyed=old.partialBlocksDestroyed;oldReload.afterRestoredFromSave();
        h.assertTrue(oldReload.modelRevision()==1&&oldReload.getBlocksTotal()==236&&oldReload.getBlocksPlaced()==oldPlaced
            &&oldReload.getHighestBlockCountReached()==236&&oldReload.partialBlocksDestroyed==.625&&oldReload.getHealth()==oldHealth,
            "completed legacy reload preserves damage: rev="+oldReload.modelRevision()+" total="+oldReload.getBlocksTotal()+" placed="+oldReload.getBlocksPlaced()+" expected="+oldPlaced+" highest="+oldReload.getHighestBlockCountReached()+" partial="+oldReload.partialBlocksDestroyed+" hp="+oldReload.getHealth()+" expectedHp="+oldHealth);
        for(var entry:oldWorld.entrySet())h.assertTrue(f.level.getBlockState(entry.getKey()).equals(entry.getValue()),"legacy restore never changes a world block");

        // Build only 120/176 new blocks, then lose 20. Saved maximum must remain120, not current100 or complete176.
        var newOrigin=oldOrigin.offset(80,0,0);f.prepare(newOrigin);
        var newGeometry=BuildingUtils.getAbsoluteBlockData(DreamWatchtowerPlacement.template(3),f.level,newOrigin,Rotation.NONE);
        var newSolid=newGeometry.stream().filter(b->!b.getBlockState().isAir()).toList();
        for(int i=0;i<120;i++){var block=newSolid.get(i);f.level.setBlock(block.getBlockPos(),block.getBlockState(),2);}
        var partial=new DreamWatchtowerPlacement(definition,f.level,newOrigin,Rotation.NONE,f.owner);
        h.assertTrue(!partial.isBuilt&&partial.getBlocksPlaced()==120&&partial.getHighestBlockCountReached()==120,"new fixture genuinely unfinished at120");
        for(int i=0;i<20;i++){var p=newSolid.get(i).getBlockPos();f.level.setBlock(p,Blocks.AIR.defaultBlockState(),2);partial.onBlockBreak(f.level,p,false);}
        partial.partialBlocksDestroyed=.375;
        int cachedPartialPlaced=partial.getBlocksPlaced();partial.initPlacedBlocks();
        int actualPartialPlaced=partial.getBlocksPlaced();
        System.out.println("TOWER_INCOMPLETE_FIXTURE_WORLD_RECONCILE cached="+cachedPartialPlaced+" actual="+actualPartialPlaced);
        int partialHealth=partial.getHealth();
        h.assertTrue(actualPartialPlaced>0&&actualPartialPlaced<=100&&partial.getHighestBlockCountReached()==120,"incomplete fixture retains pre-damage maximum");
        var saved=DataStorage.read(partial.getDataStorage().write(),f.level.getServer());
        h.assertTrue(saved.getData(DreamWatchtowerPlacement.MODEL).getInt("highest")==120,"real model codec persisted historical120");
        var newWorld=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var block:newGeometry)newWorld.put(block.getBlockPos(),f.level.getBlockState(block.getBlockPos()));
        var partialReload=(DreamWatchtowerPlacement)definition.createRestoredBuildingPlacement(f.level,newOrigin,Rotation.NONE,f.owner,saved);
        partialReload.isBuilt=false;partialReload.partialBlocksDestroyed=partial.partialBlocksDestroyed;partialReload.afterRestoredFromSave();
        h.assertTrue(partialReload.modelRevision()==3&&!partialReload.isBuilt&&partialReload.getBlocksTotal()==176
            &&partialReload.getBlocksPlaced()==actualPartialPlaced&&partialReload.getHighestBlockCountReached()==120
            &&partialReload.partialBlocksDestroyed==.375&&partialReload.getHealth()==partialHealth,
            "incomplete model reload keeps actual world count, historical120, total176, fraction and health exactly");
        for(var entry:newWorld.entrySet())h.assertTrue(f.level.getBlockState(entry.getKey()).equals(entry.getValue()),"incomplete restore never builds or heals a world block");
        f.pass("TOWER_LEGACY_DAMAGE_INCOMPLETE_RESTORE");
    }
}
