package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.buildings.shared.MineWorkPosts;
import com.solegendary.reignofnether.building.buildings.shared.PassiveOreBuilding;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Real world navigation/command regression. Installs templates, but never teleports a working assignee to a post. */
@GameTestHolder("reignofnether_mine_workposts")
@PrefixGameTestTemplate(false)
public final class MineWorkPostsGameTests {
    private static Runner active;
    @AfterBatch(batch="mine_workposts_b2")
    public static void cleanup(ServerLevel ignored) { if(active!=null){active.cleanup();active=null;} }
    @GameTest(template="ore_mine_test_arena",batch="mine_workposts_b2",timeoutTicks=3600)
    public static void trueCommandsSixWorkersAndMilitaryRegression(GameTestHelper helper) {
        helper.assertTrue(active==null,"single isolated workpost test runner");
        active=new Runner(helper);active.prepare();helper.onEachTick(active::tick);
    }

    private static final class Runner {
        static final String PREFIX="WorkpostB2_";
        final GameTestHelper h; final ServerLevel level;
        final List<BuildingPlacement> buildings=new ArrayList<>();
        final List<Mob> entities=new ArrayList<>();
        final List<String> owners=new ArrayList<>();
        final Set<Long> forced=new LinkedHashSet<>();
        final BuildingPlacement[] rotations=new BuildingPlacement[4];
        final VillagerUnit[] rotationWorkers=new VillagerUnit[4];
        final VillagerUnit[] crew=new VillagerUnit[7];
        Resources ledger,opponent;BuildingPlacement mine,tower,worksite;Mob ranged;
        int stage,ticks,entered,assertions,rotationOrderTick=-1;long baseline;
        final boolean[] rotationArrived=new boolean[4];
        final int[] rotationCommandEntityTicks=new int[4];
        final com.solegendary.reignofnether.unit.goals.GarrisonGoal[] commandedRotationGoals=new com.solegendary.reignofnether.unit.goals.GarrisonGoal[4];
        boolean rotationsReleased;
        BlockPos blockedPost;
        net.minecraft.world.level.block.state.BlockState blockedPostOriginal;
        net.minecraft.world.phys.Vec3 blockedWorkerPosition;
        Runner(GameTestHelper h){this.h=h;this.level=h.getLevel();}
        void check(boolean value,String reason){assertions++;h.assertTrue(value,reason);}
        void eq(long value,long expected,String reason){check(value==expected,reason+" actual="+value+" expected="+expected);}
        void next(int value){stage=value;entered=ticks;}
        int age(){return ticks-entered;}
        Resources account(String suffix){
            String name=PREFIX+suffix;check(PlayerServerEvents.getRTSPlayer(name)==null,"fresh isolated owner "+name);
            owners.add(name);PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,Factions.VILLAGERS));
            Resources resources=new Resources(name,1000,1000,0);ResourcesServerEvents.resourcesList.add(resources);return resources;
        }
        BlockPos site(int x,int z){
            BlockPos p=h.absolutePos(new BlockPos(x,1,z));
            for(int cx=(p.getX()-20)>>4;cx<=(p.getX()+20)>>4;cx++)for(int cz=(p.getZ()-20)>>4;cz<=(p.getZ()+20)>>4;cz++){
                long key=ChunkPos.asLong(cx,cz);if(!level.getForcedChunks().contains(key)){level.setChunkForced(cx,cz,true);forced.add(key);}
            }
            // Normal-terrain GameTest structure clearing can already have released natural gravel.
            // Seal BEFORE clearing, then remove only pre-existing falling-block setup debris once.
            // Never clear after commands: actual blocked posts must still revoke their leases.
            for(int dx=-20;dx<=20;dx++)for(int dz=-20;dz<=20;dz++)
                level.setBlock(p.offset(dx,18,dz),Blocks.STONE.defaultBlockState(),2);
            var falling=level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new net.minecraft.world.phys.AABB(p.getX()-20,p.getY()+1,p.getZ()-20,
                    p.getX()+21,level.getMaxBuildHeight(),p.getZ()+21));
            System.out.println("MINE_FIXTURE_SETUP_FALLING_DEBRIS site="+p+" count="+falling.size());
            falling.forEach(Entity::discard);
            for(int dx=-20;dx<=20;dx++)for(int dz=-20;dz<=20;dz++){
                level.setBlock(p.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);
                for(int dy=1;dy<=17;dy++)level.setBlock(p.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);
            }
            return p;
        }
        BuildingPlacement install(Building definition,String owner,int x,int z,Rotation rotation,boolean complete){
            BlockPos origin=site(x,z);int placed=0;
            for(BuildingBlock block:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,origin,rotation)){
                if(complete || (placed<3&&!block.getBlockState().isAir())){
                    level.setBlock(block.getBlockPos(),block.getBlockState(),2);if(!block.getBlockState().isAir())placed++;
                }
            }
            BuildingPlacement p=BuildingUtils.getNewBuildingPlacement(definition,level,origin,rotation,owner,false);
            BuildingServerEvents.getBuildings().add(p);buildings.add(p);return p;
        }
        <T extends Mob>T spawn(EntityType<T> type,String owner,ServerLevel where,BlockPos feet,boolean noAi){
            T unit=type.create(where);check(unit instanceof Unit,"real registered RTS unit");((Unit)unit).setOwnerName(owner);
            unit.setNoAi(noAi);unit.moveTo(feet.getX()+.5,feet.getY(),feet.getZ()+.5);
            check(where.addFreshEntity(unit),"real entity inserted");entities.add(unit);return unit;
        }
        BlockPos approach(BuildingPlacement p,int index){
            // Only spawn initial approach points outside the structure. Assignees must walk the entire approach.
            return p.originPos.offset(new BlockPos(3+2*(index%3),2,-6-2*(index/3)).rotate(p.rotation));
        }
        void order(VillagerUnit unit,UnitAction action,BlockPos target){
            unit.setNoAi(false);new UnitActionItem(unit.getOwnerName(),action,-1,new int[]{unit.getId()},target,BlockPos.ZERO).action(unit.level());
        }
        void garrison(VillagerUnit unit,BuildingPlacement p){order(unit,UnitAction.GARRISON,p.originPos);}
        long creditTotal(){return (long)ledger.ore*160+mine.getDataStorage().getData(PassiveOreBuilding.PRODUCTION_STATE).getQuarterTicks();}
        void prepare(){
            ledger=account("Main");opponent=account("Opponent");
            mine=install(Buildings.VILLAGER_ORE_MINE,ledger.ownerName,30,30,Rotation.CLOCKWISE_90,true);
            tower=install(Buildings.WATCHTOWER,ledger.ownerName,90,30,Rotation.NONE,true);
            worksite=install(Buildings.BLACKSMITH,ledger.ownerName,150,30,Rotation.NONE,false);
            install(Buildings.BLACKSMITH,opponent.ownerName,210,30,Rotation.NONE,true); // do not defeat fixture owner on capture return
            Rotation[] values=Rotation.values();
            for(int i=0;i<values.length;i++){
                if(values[i]==mine.rotation){rotations[i]=mine;continue;}
                Resources r=account("Rotation"+i);rotations[i]=install(Buildings.VILLAGER_ORE_MINE,r.ownerName,30+i*60,95,values[i],true);
                rotationWorkers[i]=spawn(EntityRegistrar.VILLAGER_UNIT.get(),r.ownerName,level,approach(rotations[i],0),true);
            }
            for(int i=0;i<crew.length;i++)crew[i]=spawn(EntityRegistrar.VILLAGER_UNIT.get(),ledger.ownerName,level,approach(mine,i),true);
            ranged=spawn(EntityRegistrar.PILLAGER_UNIT.get(),ledger.ownerName,level,tower.originPos.offset(2,2,-5),true);
            System.out.println("MINE_WORKPOSTS_START normalRtsPathfinding="+UnitServerEvents.rtsPathfinding+" sixRotation="+mine.rotation);
        }
        void tick(){
            ticks++;
            if(rotationOrderTick>=0&&!rotationsReleased)observeRotationArrivals();
            switch(stage){
                case 0 -> {
                    if(ticks<20)return;
                    if(!awaitInitialCommandReadiness())return;
                    check(mine.isBuilt&&tower.isBuilt,"real full structures complete through normal server ticks");
                    for(BuildingPlacement p:rotations){check(p.isBuilt,"rotation mine built");for(int i=0;i<6;i++)check(MineWorkPosts.isPostClear(p,i),"real rotated support/headroom "+p.rotation+" #"+i);}
                    negativeOccupancy();
                    rotationOrderTick=ticks;
                    for(int i=0;i<rotations.length;i++)if(rotationWorkers[i]!=null){
                        var worker=rotationWorkers[i];
                        rotationCommandEntityTicks[i]=worker.tickCount;
                        System.out.println("MINE_ROTATION_COMMAND_BEFORE "+rotationDiagnostic(i));
                        check(level.getEntity(worker.getId())==worker,"native command ID lookup must resolve exact auxiliary worker before sending: "+rotationDiagnostic(i));
                        garrison(worker,rotations[i]);
                        commandedRotationGoals[i]=worker.getGarrisonGoal();
                        System.out.println("MINE_ROTATION_COMMAND_AFTER "+rotationDiagnostic(i));
                        check(worker.getGarrisonGoal().getBuildingTarget()==rotations[i]&&MineWorkPosts.hasAssignment(worker),
                            "native GARRISON must synchronously admit initial auxiliary order: "+rotationDiagnostic(i));
                    }
                    ranged.setNoAi(false);new UnitActionItem(ledger.ownerName,UnitAction.GARRISON,-1,new int[]{ranged.getId()},tower.originPos,BlockPos.ZERO).action(level);
                    baseline=creditTotal();garrison(crew[0],mine);eq(MineWorkPosts.countActive(mine),0,"explicit assignment alone grants zero bonus");eq(MineWorkPosts.snapshot(mine).size(),1,"server snapshot includes actual approach reservation");check(!MineWorkPosts.snapshot(mine).get(0).active(),"travelling snapshot never marks income active");next(1);
                }
                case 1 -> {
                    if(age()<2)return;
                    eq(MineWorkPosts.countActive(mine),0,"normal distant approach not instantly stationed");eq(creditTotal()-baseline,8,"two pre-arrival ticks earn base only");next(2);
                }
                case 2 -> {if(awaitCount(1,700)){baseline=creditTotal();next(3);}}
                case 3 -> {
                    eq(MineWorkPosts.countActive(mine),1,"one worker remains truly stationed during measurement");
                    if(age()<40)return;eq(creditTotal()-baseline,240,"one arrived worker earns exactly1.5x across40real ticks");
                    garrison(crew[1],mine);next(4);
                }
                case 4 -> {if(awaitCount(2,700)){baseline=creditTotal();garrison(crew[2],mine);check(!MineWorkPosts.hasAssignment(crew[2]),"third explicit order rejected before technology");next(5);}}
                case 5 -> {
                    eq(MineWorkPosts.countActive(mine),2,"two workers remain stationed during measurement");
                    if(age()<40)return;eq(creditTotal()-baseline,320,"two arrived workers earn exactly2x");
                    ResearchServerEvents.addResearch(ledger.ownerName,MineWorkPosts.EXPANDED_WORKFORCE);
                    eq(MineWorkPosts.capacity(mine),6,"normal completed research API unlocks six posts");
                    for(int i=2;i<6;i++)garrison(crew[i],mine);
                    eq(MineWorkPosts.countActive(mine),2,"new reservations do not pay before arrival");next(6);
                }
                case 6 -> {if(awaitCount(6,1100)){baseline=creditTotal();garrison(crew[6],mine);check(!MineWorkPosts.hasAssignment(crew[6]),"seventh rejected with six real workers");next(7);}}
                case 7 -> {
                    eq(MineWorkPosts.countActive(mine),6,"six remain at their actual posts");
                    if(age()<40)return;eq(creditTotal()-baseline,480,"six real workers earn3x across40real ticks");
                    Set<Integer> posts=new LinkedHashSet<>();for(int i=0;i<6;i++){check(MineWorkPosts.hasAssignment(crew[i]),"each worker has a valid lease");check(crew[i].getGarrisonGoal().isMineWorkerActive(),"each worker actually arrived");posts.add(crew[i].getGarrisonGoal().getMinePost());check(GarrisonableBuildingAddon.getGarrison(crew[i])==null,"workpost does not grant military garrison identity");}eq(posts.size(),6,"six distinct posts, no duplicated worker/slot");eq(MineWorkPosts.snapshot(mine).size(),6,"authoritative six-slot snapshot");eq(MineWorkPosts.snapshot(mine).stream().filter(w->w.active()).count(),6,"all snapshot workers arrived through real navigation");
                    // Main mine rate measurement is complete at exactly40 ticks. Its speed is not
                    // a synchronization barrier for independently pathfinding auxiliary rotations.
                    next(9);
                }
                case 9 -> {
                    eq(MineWorkPosts.countActive(mine),6,"main six remain stationed while auxiliary arrival synchronizes");
                    if(!awaitRotationArrivals())return;
                    rotationsReleased=true;
                    for(int i=0;i<rotations.length;i++)if(rotationWorkers[i]!=null){eq(MineWorkPosts.countActive(rotations[i]),1,"real path arrival for rotation "+rotations[i].rotation);order(rotationWorkers[i],UnitAction.STOP,BlockPos.ZERO);eq(MineWorkPosts.countActive(rotations[i]),0,"rotated STOP releases");}
                    check(GarrisonableBuildingAddon.getGarrison((Unit)ranged)==tower,"original ranged GARRISON follows original watchtower path/entry");
                    check(!MineWorkPosts.hasAssignment((Unit)ranged),"original ranged garrison is not a worker lease");
                    GarrisonableBuildingAddon military=tower.getBuilding().getActiveAddon(GarrisonableBuildingAddon.class);
                    BlockPos exit=military.getExitPosition(tower);
                    check(exit!=null,"original watchtower has native exit position");
                    System.out.println("MINE_MILITARY_UNGARRISON_BEFORE position="+ranged.position()+" onPos="+ranged.getOnPos()+" support="+ranged.mainSupportingBlockPos+" exit="+exit);
                    new UnitActionItem(ledger.ownerName,UnitAction.UNGARRISON,-1,new int[]{ranged.getId()}).action(level);
                    // Native teleport updates real coordinates synchronously, while Entity.getOnPos may
                    // retain its old mainSupportingBlockPos until the next actual movement tick.
                    check(ranged.distanceToSqr(exit.getX()+0.5,exit.getY()+0.5,exit.getZ()+0.5)<1.0e-10,
                        "original military UNGARRISON synchronously reaches exact native exit; position="+ranged.position()+" exit="+exit);
                    System.out.println("MINE_MILITARY_UNGARRISON_AFTER position="+ranged.position()+" onPos="+ranged.getOnPos()+" support="+ranged.mainSupportingBlockPos);
                    next(8);
                }
                case 8 -> {
                    if(GarrisonableBuildingAddon.getGarrison((Unit)ranged)!=null) {
                        check(age()<10,"original military UNGARRISON must release after normal entity ticks; position="+ranged.position()+" onPos="+ranged.getOnPos()+" support="+ranged.mainSupportingBlockPos);
                        return;
                    }
                    check(GarrisonableBuildingAddon.getGarrison((Unit)ranged)==null,"original military UNGARRISON still releases");
                    check(!MineWorkPosts.hasAssignment((Unit)ranged),"military exit never creates a worker lease");
                    System.out.println("MINE_MILITARY_UNGARRISON_SETTLED ticks="+age()+" position="+ranged.position()+" onPos="+ranged.getOnPos()+" support="+ranged.mainSupportingBlockPos);
                    releaseAndCapture();next(10);
                }
                case 10 -> {
                    check(!MineWorkPosts.hasAssignment(crew[5])&&MineWorkPosts.snapshot(mine).isEmpty(),
                        "deliberately obstructed post stays invalid; fixture never clears real gameplay obstruction");
                    eq(MineWorkPosts.countActive(mine),0,"blocked post never produces bonus during real AI cleanup");
                    if(age()<5)return;
                    var goal=crew[5].getGarrisonGoal();
                    check(goal.getMinePost()==-1&&goal.getBuildingTarget()==null&&goal.getMoveTarget()==null,
                        "actual entity/goal ticks retire obstructed lease without injected STOP/reset");
                    check(crew[5].position().distanceToSqr(blockedWorkerPosition)<2.25,
                        "blocked-post release leaves worker locally in place, not teleported into the new post");
                    check(level.getBlockState(blockedPost).is(Blocks.STONE),"intentional blocker persists until negative control completed");
                    level.setBlock(blockedPost,blockedPostOriginal,2);
                    System.out.println("MINE_BLOCKED_POST_NEGATIVE_PASS nativeTicks="+age()+" position="+crew[5].position()+" blockedPost="+blockedPost);
                    System.out.println("MINE_WORKPOSTS_B2_PASS assertions="+assertions+" ticks="+ticks+" rotation="+mine.rotation);h.succeed();next(99);
                }
                default -> { }
            }
        }
        boolean initialCommandReady(Mob unit){
            return unit.level()==level && unit.isAlive() && !unit.isRemoved() && unit.tickCount>0
                && level.getEntity(unit.getId())==unit && level.getEntity(unit.getUUID())==unit
                && level.isPositionEntityTicking(unit.blockPosition());
        }
        String commandReadinessDiagnostic(Mob unit){
            return " id="+unit.getId()+" uuid="+unit.getUUID()+" position="+unit.position()
                +" entityTick="+unit.tickCount+" sameLevel="+(unit.level()==level)
                +" exactIdLookup="+(level.getEntity(unit.getId())==unit)
                +" exactUuidLookup="+(level.getEntity(unit.getUUID())==unit)
                +" entityTicking="+level.isPositionEntityTicking(unit.blockPosition())
                +" alive="+unit.isAlive()+" removed="+unit.isRemoved();
        }
        boolean awaitInitialCommandReadiness(){
            StringBuilder missing=new StringBuilder();
            for(VillagerUnit unit:crew)if(!initialCommandReady(unit))missing.append(" crew{").append(commandReadinessDiagnostic(unit)).append('}');
            for(VillagerUnit unit:rotationWorkers)if(unit!=null&&!initialCommandReady(unit))missing.append(" rotation{").append(commandReadinessDiagnostic(unit)).append('}');
            if(!initialCommandReady(ranged))missing.append(" military{").append(commandReadinessDiagnostic(ranged)).append('}');
            if(missing.length()==0){
                System.out.println("MINE_INITIAL_COMMANDS_READY fixtureTick="+ticks);
                return true;
            }
            if(ticks==20||ticks==100||ticks>=700)System.out.println("MINE_INITIAL_COMMANDS_NOT_READY fixtureTick="+ticks+missing);
            check(ticks<700,"initial command recipients must become natively tracked and entity-ticking within700fixture ticks; no command sent:"+missing);
            return false;
        }
        void observeRotationArrivals(){
            for(int i=0;i<rotations.length;i++)if(rotationWorkers[i]!=null){
                var worker=rotationWorkers[i];
                int elapsed=ticks-rotationOrderTick;
                if(elapsed==1||elapsed==20||elapsed==100||elapsed==500||elapsed==699)
                    System.out.println("MINE_ROTATION_TICK_PROGRESS elapsed="+elapsed+" "+rotationDiagnostic(i));
                if(worker.getGarrisonGoal()!=commandedRotationGoals[i]||!MineWorkPosts.hasAssignment(worker)){
                    System.out.println("MINE_ROTATION_FIRST_LEASE_LOSS elapsed="+(ticks-rotationOrderTick)+" sameGoal="+(worker.getGarrisonGoal()==commandedRotationGoals[i])+" "+rotationDiagnostic(i));
                    check(false,"auxiliary admitted lease disappeared before explicit STOP; never silently retry: "+rotationDiagnostic(i));
                }
                int activeCount=MineWorkPosts.countActive(rotations[i]);
                if(rotationArrived[i])check(activeCount==1,"previously arrived auxiliary worker must not lose its post: "+rotationDiagnostic(i));
                else if(activeCount==1){
                    check(ticks-rotationOrderTick<=700,"auxiliary arrival must fit original single-worker700tick budget: "+rotationDiagnostic(i));
                    rotationArrived[i]=true;System.out.println("MINE_ROTATION_ARRIVED elapsed="+(ticks-rotationOrderTick)+" "+rotationDiagnostic(i));
                } else if(ticks-rotationOrderTick>=700){
                    System.out.println("MINE_ROTATION_TIMEOUT elapsed="+(ticks-rotationOrderTick)+" "+rotationDiagnostic(i));
                    check(false,"auxiliary arrival timed out independently of main crew progress: "+rotationDiagnostic(i));
                }
            }
        }
        boolean awaitRotationArrivals(){
            boolean ready=true;
            for(int i=0;i<rotations.length;i++)if(rotationWorkers[i]!=null&&!rotationArrived[i]){
                ready=false;
                if(ticks-rotationOrderTick>=700){
                    System.out.println("MINE_ROTATION_TIMEOUT elapsed="+(ticks-rotationOrderTick)+" "+rotationDiagnostic(i));
                    check(false,"independent auxiliary path arrival exceeded original single-worker700tick budget: "+rotationDiagnostic(i));
                }
            }
            return ready;
        }
        String rotationDiagnostic(int i){
            var w=rotationWorkers[i];var p=rotations[i];var g=w.getGarrisonGoal();int post=g.getMinePost();
            return "rotation="+p.rotation+" worker="+w.getUUID()+" position="+w.position()+" onPos="+w.getOnPos()+" post="+post
                +" target="+g.getMoveTarget()+" expected="+(post>=0?MineWorkPosts.getPost(p,post):null)
                +" lease="+g.hasMineAssignment()+" active="+g.isMineWorkerActive()+" activeCount="+MineWorkPosts.countActive(p)
                +" navDone="+w.getNavigation().isDone()+" alive="+w.isAlive()+" noAi="+w.isNoAi()
                +" entityTick="+w.tickCount+" entityTickDelta="+(w.tickCount-rotationCommandEntityTicks[i])
                +" entityTickingPosition="+level.isPositionEntityTicking(w.blockPosition())
                +" chunkPositionTicking="+level.getChunkSource().isPositionTicking(w.chunkPosition().toLong())
                +" postEntityTicking="+(post>=0&&post<6&&level.isPositionEntityTicking(MineWorkPosts.getPost(p,post)))
                +" exactIdLookup="+(level.getEntity(w.getId())==w)+" exactUuidLookup="+(level.getEntity(w.getUUID())==w)
                +" onGround="+w.onGround()+" anchor="+w.getAnchor()+" validWorker="+MineWorkPosts.isValidWorker(w,p)
                +" workerOwner="+w.getOwnerName()+" buildingOwner="+p.ownerName+" capture="+p.getCaptureRevision()
                +" built="+p.isBuilt+" health="+p.getHealth()+" liveBuilding="+BuildingServerEvents.getBuildings().contains(p)
                +" destroyed="+p.shouldBeDestroyed()+" clear="+(post>=0&&post<6&&MineWorkPosts.isPostClear(p,post))
                +" capacity="+MineWorkPosts.capacity(p)+" passenger="+w.isPassenger()+" buildTarget="+w.getBuildRepairGoal().getBuildingTarget();
        }
        boolean awaitCount(int expected,int deadline){
            if(MineWorkPosts.countActive(mine)==expected)return true;
            if(age()>=deadline){
                System.out.println("MINE_WORKPOSTS_TIMEOUT wanted="+expected+" age="+age()+" "+diagnostics());
                check(false,"real navigation failed to fill "+expected+" posts; do not loosen arrival distance: "+diagnostics());
            }
            return false;
        }
        String diagnostics(){StringBuilder text=new StringBuilder();for(int i=0;i<crew.length;i++){var w=crew[i];var g=w.getGarrisonGoal();text.append(" worker").append(i).append(" pos=").append(w.position()).append(" post=").append(g.getMinePost()).append(" lease=").append(g.hasMineAssignment()).append(" active=").append(g.isMineWorkerActive()).append(" navDone=").append(w.getNavigation().isDone()).append(" target=").append(g.getMoveTarget());}return text.toString();}
        void negativeOccupancy(){
            VillagerUnit passer=spawn(EntityRegistrar.VILLAGER_UNIT.get(),ledger.ownerName,level,MineWorkPosts.getPost(mine,0),true);
            eq(MineWorkPosts.countActive(mine),0,"nearby/on-post unassigned worker never auto-stations");
            AlliancesServerEvents.addAlliance(ledger.ownerName,opponent.ownerName);
            VillagerUnit ally=spawn(EntityRegistrar.VILLAGER_UNIT.get(),opponent.ownerName,level,MineWorkPosts.getPost(mine,1),true);
            garrison(ally,mine);check(!MineWorkPosts.hasAssignment(ally),"allied worker cannot occupy own-only mine");eq(MineWorkPosts.countActive(mine),0,"allied bystander earns no bonus");
            VillagerUnit builder=spawn(EntityRegistrar.VILLAGER_UNIT.get(),ledger.ownerName,level,MineWorkPosts.getPost(mine,0),true);
            order(builder,UnitAction.BUILD_REPAIR,worksite.originPos);check(builder.getBuildRepairGoal().getBuildingTarget()==worksite,"real BUILD_REPAIR task assigned");eq(MineWorkPosts.countActive(mine),0,"construction task near a mine does not auto-station");
            ServerLevel nether=level.getServer().getLevel(Level.NETHER);check(nether!=null,"real alternate dimension exists");
            VillagerUnit cross=spawn(EntityRegistrar.VILLAGER_UNIT.get(),ledger.ownerName,nether,MineWorkPosts.getPost(mine,0),true);cross.setNoGravity(true);
            garrison(cross,mine);check(!MineWorkPosts.isValidWorker(cross,mine)&&!MineWorkPosts.hasAssignment(cross),"same coordinates in another Level reject real assignment");eq(MineWorkPosts.countActive(mine),0,"cross-dimension body cannot count");
            for(Mob e:List.of(passer,ally,builder,cross))e.discard();AlliancesServerEvents.removeAlliance(ledger.ownerName,opponent.ownerName);
        }
        void releaseAndCapture(){
            order(crew[0],UnitAction.STOP,BlockPos.ZERO);eq(MineWorkPosts.countActive(mine),5,"STOP synchronously releases the actual lease");
            order(crew[0],UnitAction.MOVE,approach(mine,0));
            BlockPos log=mine.originPos.offset(new BlockPos(5,1,-11).rotate(mine.rotation));level.setBlock(log,Blocks.OAK_LOG.defaultBlockState(),2);
            order(crew[1],UnitAction.MOVE,log);eq(crew[1].getGatherResourceGoal().getTargetResourceName().ordinal(),ResourceName.WOOD.ordinal(),"MOVE to a real wood source starts native gather task");eq(MineWorkPosts.countActive(mine),4,"gather command releases worker lease");
            order(crew[2],UnitAction.BUILD_REPAIR,worksite.originPos);check(crew[2].getBuildRepairGoal().getBuildingTarget()==worksite,"native build command owns target");eq(MineWorkPosts.countActive(mine),3,"building command releases worker lease");
            crew[3].hurt(level.damageSources().genericKill(),10000);check(!crew[3].isAlive(),"real worker death");eq(MineWorkPosts.countActive(mine),2,"dead worker immediately stops income");
            order(crew[4],UnitAction.UNGARRISON,BlockPos.ZERO);eq(MineWorkPosts.countActive(mine),1,"native UNGARRISON releases ground lease without military teleport");
            long before=mine.getCaptureRevision();
            Mob enemy=spawn(EntityRegistrar.PILLAGER_UNIT.get(),opponent.ownerName,level,mine.originPos.offset(new BlockPos(5,2,-2).rotate(mine.rotation)),true);
            mine.checkAndDoCapture(level);check(mine.ownerName.equals(opponent.ownerName),"canonical capture transition A to B");enemy.discard();
            Mob friendly=spawn(EntityRegistrar.PILLAGER_UNIT.get(),ledger.ownerName,level,mine.originPos.offset(new BlockPos(5,2,-2).rotate(mine.rotation)),true);
            mine.checkAndDoCapture(level);check(mine.ownerName.equals(ledger.ownerName),"canonical capture transition B back to A");friendly.discard();
            eq(mine.getCaptureRevision(),before+2,"capture ABA advances real placement revision twice");eq(MineWorkPosts.countActive(mine),0,"same-tick owner ABA cannot inherit prior worker lease");
            eq(MineWorkPosts.snapshot(mine).size(),0,"capture invalidates private occupancy snapshot immediately");
            garrison(crew[5],mine);check(MineWorkPosts.hasAssignment(crew[5]),"fresh explicit post-capture reservation allowed");
            var position=crew[5].position();MineWorkPosts.releaseAll(mine);
            eq(MineWorkPosts.snapshot(mine).size(),0,"all-release empties server live reservations");
            check(!MineWorkPosts.hasAssignment(crew[5])&&crew[5].position().equals(position),"all-release neither teleports nor leaves a stale order");
            // Separate negative control: after the full positive path/rate matrix, obstruct an explicitly
            // reserved post. Do not auto-reassign, remove the blocker, or call reset to manufacture expiry.
            garrison(crew[5],mine);check(MineWorkPosts.hasAssignment(crew[5]),"explicit reservation exists before intentional obstruction");
            blockedPost=MineWorkPosts.getPost(mine,crew[5].getGarrisonGoal().getMinePost());
            blockedPostOriginal=level.getBlockState(blockedPost);blockedWorkerPosition=crew[5].position();
            check(blockedPostOriginal.isAir(),"negative-control post starts genuinely unobstructed");
            level.setBlock(blockedPost,Blocks.STONE.defaultBlockState(),2);
            check(!MineWorkPosts.hasAssignment(crew[5])&&MineWorkPosts.snapshot(mine).isEmpty(),
                "real target obstruction immediately invalidates lease and authoritative snapshot");
            check(crew[5].position().equals(blockedWorkerPosition),"obstruction validity check does not teleport worker");
        }
        void cleanup(){
            for(Mob e:entities){if(e instanceof Unit unit)Unit.fullResetBehaviours(unit);e.discard();}
            BuildingServerEvents.getBuildings().removeAll(buildings);
            if(ledger!=null&&opponent!=null)AlliancesServerEvents.removeAlliance(ledger.ownerName,opponent.ownerName);
            for(String owner:owners){ResearchServerEvents.removeAllResearchFor(owner);ResourcesServerEvents.removeAccount(owner);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));}
            for(long key:forced){ChunkPos chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}
        }
    }
}
