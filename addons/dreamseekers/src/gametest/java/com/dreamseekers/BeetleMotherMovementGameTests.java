// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.beetlemother.BeetleMotherBalance;
import com.dreamseekers.unit.BeetleMotherUnit;
import com.dreamseekers.unit.PinkChickenScout;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.pasterdream.entity.BlackBeetleMotherEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Actual ground motion through the ordinary RTS command path, never per-tick fixture teleportation. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BeetleMotherMovementGameTests {
    private static final String BATCH="dreamseekers_beetle_mother_movement", PREFIX="MotherMove_";
    // The copied Wind fixture used160 ticks at speed.25; retain a finite speed-scaled budget plus one20tick base-recompute allowance (not a scheduling upper bound).
    private static final int LAST_REPLACEMENT_BUDGET=(int)Math.ceil(160.0*.25/BeetleMotherBalance.SPEED)+20;
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final java.util.Set<net.minecraft.world.level.ChunkPos> TICKETS=new java.util.HashSet<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity entity:FIXTURES)if(!entity.isRemoved())entity.discard();FIXTURES.clear();
        for(var c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();
        for(String a:List.of("Hostile","Ally"))for(String b:List.of("Hostile","Ally"))
            if(!a.equals(b))AlliancesServerEvents.removeAlliance(PREFIX+a,PREFIX+b);
    }

    @GameTest(template="mother_unit_arena",batch=BATCH,timeoutTicks=500)
    public static void groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(GameTestHelper h) {
        floor(h);
        for(int z=2;z<=16;z++)for(int y=1;y<=3;y++)set(h,12,y,z,Blocks.STONE);
        BeetleMotherUnit mother=spawn(h,owner("Barrier"),4,10);BlockPos destination=h.absolutePos(new BlockPos(20,0,10));
        boolean[] detoured={false};double y=h.absolutePos(BlockPos.ZERO).getY();
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,mother),"wait genuine mother simulation")).thenExecute(()->{
            order(mother,UnitAction.MOVE,destination);
            for(int tick=1;tick<=400;tick++)h.runAfterDelay(tick,()->{
                if(mother.tickCount>280&&mother.tickCount%10==0)System.out.println("MOTHER_BARRIER_TRACE "+diagnostic(h,mother)+" delta="+mother.getDeltaMovement()+" idle="+mother.isIdle());
                h.assertTrue(!mother.isNoAi()&&!mother.isNoGravity()&&!mother.noPhysics&&h.getLevel().noCollision(mother),"normal ground body and active AI remain collision-safe");
                h.assertTrue(mother.getY()<y+1.6,"three-block wall is not crossed by flight or climbing");
                if(mother.getZ()>h.absolutePos(new BlockPos(0,0,17)).getZ() || mother.getZ()<h.absolutePos(new BlockPos(0,0,2)).getZ())detoured[0]=true;
            });
            h.runAfterDelay(410,()->{
                h.assertTrue(mother instanceof BlackBeetleMotherEntity&&mother.getNavigation() instanceof GroundPathNavigation&&!mother.isFlyingUnit(),"actual original mother with normal ground navigator");
                h.assertTrue(detoured[0]&&arrivedByCoreContract(mother,destination),"ground order routes around wall: "+describe(mother));
                System.out.println("DREAMSEEKERS_BEETLE_MOTHER_GROUND_BARRIER_PASS "+describe(mother));h.succeed();
            });
        });
    }

    @GameTest(template="mother_unit_arena",batch=BATCH,timeoutTicks=690)
    public static void enclosedGroundMotherCannotPhaseAndRetriesOpenedExit(GameTestHelper h) {
        floor(h);
        for(int x=3;x<=11;x++)for(int z=3;z<=11;z++)for(int y=1;y<=5;y++)
            if(x==3||x==11||z==3||z==11||y==5)set(h,x,y,z,Blocks.STONE);
        BeetleMotherUnit mother=spawn(h,owner("Enclosed"),7,7);BlockPos destination=h.absolutePos(new BlockPos(18,0,7));
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,mother),"wait genuine enclosed simulation")).thenExecute(()->{
            order(mother,UnitAction.MOVE,destination);
            for(int tick=1;tick<190;tick++)h.runAfterDelay(tick,()->{
                h.assertTrue(h.getLevel().noCollision(mother)&&mother.getX()<h.absolutePos(new BlockPos(11,0,0)).getX(),"sealed wall prevents clipping/teleporting");
            });
            h.runAfterDelay(200,()->{for(int z=6;z<=8;z++)for(int y=1;y<=4;y++)set(h,11,y,z,Blocks.AIR);});
            h.runAfterDelay(580,()->{
                h.assertTrue(near(mother,destination,2)&&h.getLevel().noCollision(mother),"same pending ground order recovers through genuinely opened passage: "+describe(mother));
                System.out.println("DREAMSEEKERS_BEETLE_MOTHER_OPENING_RETRY_PASS "+describe(mother));h.succeed();
            });
        });
    }

    @GameTest(template="mother_unit_arena",batch=BATCH,timeoutTicks=900)
    public static void queuedMovesHoldStopAndReplacementNeverResumeStalePath(GameTestHelper h) {
        floor(h);BeetleMotherUnit mother=spawn(h,owner("Queue"),4,4);
        BlockPos first=h.absolutePos(new BlockPos(17,0,4)),second=h.absolutePos(new BlockPos(17,0,17));
        boolean[] passedFirst={false};Vec3[] held={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,mother),"wait ground queue readiness")).thenExecute(()->{
            order(mother,UnitAction.MOVE,first);
            UnitServerEvents.addActionItem(mother.getOwnerName(),UnitAction.MOVE,-1,new int[]{mother.getId()},second,BlockPos.ZERO,true);
            for(int tick=1;tick<=330;tick++)h.runAfterDelay(tick,()->{h.assertTrue(h.getLevel().noCollision(mother),"queue keeps collision");if(near(mother,first,1.8))passedFirst[0]=true;});
            h.runAfterDelay(340,()->{
                h.assertTrue(passedFirst[0]&&near(mother,second,1.8),"both queued waypoints visited in order: "+describe(mother));order(mother,UnitAction.HOLD,BlockPos.ZERO);
            });
            h.runAfterDelay(360,()->{
                held[0]=mother.position();h.assertTrue(mother.getHoldPosition()&&mother.getMoveGoal().getMoveTarget()==null,"HOLD has no stale ground destination");
                UnitServerEvents.addActionItem(mother.getOwnerName(),UnitAction.MOVE,-1,new int[]{mother.getId()},first,BlockPos.ZERO,true);
                order(mother,UnitAction.STOP,BlockPos.ZERO);
            });
            h.runAfterDelay(430,()->{
                h.assertTrue(mother.position().distanceToSqr(held[0])<.20&&!mother.getHoldPosition()&&mother.getNavigation().isDone(),"STOP cancels hold, queue and delayed paths");
                h.assertTrue(UnitServerEvents.getUnitActionSlowQueue().stream().noneMatch(a->Arrays.stream(a.getUnitIds()).anyMatch(id->id==mother.getId())),"no stale queued order");
                order(mother,UnitAction.MOVE,first);order(mother,UnitAction.MOVE,h.absolutePos(new BlockPos(4,0,17)));
            });
            h.runAfterDelay(550,()->{
                h.assertTrue(near(mother,h.absolutePos(new BlockPos(4,0,17)),1.8),"replacement defeats older async path: "+describe(mother));
                // Same-tick A -> B -> A is not a duplicate of the older first A.
                order(mother,UnitAction.MOVE,first);order(mother,UnitAction.MOVE,second);order(mother,UnitAction.MOVE,first);
            });
            for(int dt=551;dt<550+LAST_REPLACEMENT_BUDGET;dt++)h.runAfterDelay(dt,()->{h.assertTrue(first.equals(mother.getMoveGoal().getMoveTarget())||(mother.getMoveGoal().getMoveTarget()==null&&arrivedByCoreContract(mother,first)),"final A must never be replaced by stale B or cleared before real arrival");if(mother.tickCount%10==0)System.out.println("MOTHER_ABA_TRACE "+diagnostic(h,mother)+" delta="+mother.getDeltaMovement()+" idle="+mother.isIdle());});
            h.runAfterDelay(550+LAST_REPLACEMENT_BUDGET,()->{
                h.assertTrue(arrivedByCoreContract(mother,first),"last A wins genuine A-B-A same-tick replacement: "+describe(mother));
                System.out.println("DREAMSEEKERS_BEETLE_MOTHER_QUEUE_LAST_A_ARRIVED "+describe(mother));
            });
            for(int dt=551+LAST_REPLACEMENT_BUDGET;dt<=570+LAST_REPLACEMENT_BUDGET;dt++)h.runAfterDelay(dt,()->h.assertTrue(arrivedByCoreContract(mother,first)&&mother.getMoveGoal().getMoveTarget()==null,"last A stays settled through20 additional real ticks; no stale B revival"));
            h.runAfterDelay(571+LAST_REPLACEMENT_BUDGET,()->{System.out.println("DREAMSEEKERS_BEETLE_MOTHER_QUEUE_HOLD_STOP_REPLACE_PASS");h.succeed();});
        });
    }

    @GameTest(template="mother_unit_arena",batch=BATCH,timeoutTicks=80)
    public static void malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(GameTestHelper h) {
        floor(h);String owner=owner("InvalidTarget");BeetleMotherUnit mother=spawn(h,owner,4,4);
        PinkChickenScout scout=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(h.getLevel());
        h.assertTrue(scout!=null,"real scout regression control created");scout.setOwnerName(owner);
        BlockPos sp=h.absolutePos(new BlockPos(4,2,12));scout.moveTo(sp.getX()+.5,sp.getY(),sp.getZ()+.5,0,0);
        FIXTURES.add(scout);h.assertTrue(h.getLevel().addFreshEntity(scout),"scout enters actual level");
        BlockPos tp=h.absolutePos(new BlockPos(16,1,16));
        var item=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),tp.getX()+.5,tp.getY(),tp.getZ()+.5,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
        item.setNoGravity(true);FIXTURES.add(item);h.assertTrue(h.getLevel().addFreshEntity(item),"actual nonliving item target");
        var boat=net.minecraft.world.entity.EntityType.BOAT.create(h.getLevel());
        h.assertTrue(boat!=null,"actual boat created");boat.moveTo(tp.getX()+3.5,tp.getY(),tp.getZ()+.5,0,0);
        FIXTURES.add(boat);h.assertTrue(h.getLevel().addFreshEntity(boat),"actual nonliving boat target");
        var removed=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),tp.getX(),tp.getY(),tp.getZ(),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD));
        FIXTURES.add(removed);h.assertTrue(h.getLevel().addFreshEntity(removed),"removed target was genuinely registered");
        int removedId=removed.getId();removed.discard();
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,mother)&&published(h,scout),"wait both real controllers")).thenExecute(()->{
            h.assertTrue(h.getLevel().getEntity(removedId)==null,"removed target cannot still resolve");
            Vec3 motherStart=mother.position(),scoutStart=scout.position();
            for(Unit unit:List.<Unit>of(mother,scout))for(UnitAction action:List.of(UnitAction.FOLLOW,UnitAction.ATTACK))
                for(int id:new int[]{item.getId(),boat.getId(),Integer.MAX_VALUE,removedId}){
                    // Exercise actual dispatcher rather than replacing it with a test-side eligibility helper.
                    new UnitActionItem(owner,action,id,new int[]{((Entity)unit).getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                    h.assertTrue(unit.getFollowTarget()==null&&((net.minecraft.world.entity.Mob)unit).getTarget()==null
                            &&unit.getMoveGoal().getMoveTarget()==null,"invalid target installs no attack/follow/path");
                }
            h.runAfterDelay(35,()->{
                h.assertTrue(mother.position().distanceToSqr(motherStart)<.2
                        &&Math.abs(scout.getX()-scoutStart.x)<.3&&Math.abs(scout.getZ()-scoutStart.z)<.3,"invalid targets do not start delayed pursuit");
                h.assertTrue(mother.getItems().isEmpty()&&scout.getItems().isEmpty()&&item.isAlive()&&item.getItem().getCount()==7
                        &&boat.isAlive(),"invalid target commands consume/grant no objects");
                Resources ledger=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(owner)).findFirst().orElseThrow();
                h.assertTrue(ledger.food==500&&ledger.wood==500&&ledger.ore==500,"invalid targets grant no resources");
                System.out.println("DREAMSEEKERS_BEETLE_MOTHER_SCOUT_INVALID_ENTITY_COMMAND_NOCRASH_PASS");h.succeed();
            });
        });
    }

    @GameTest(template="mother_unit_arena",batch=BATCH,timeoutTicks=760)
    public static void nativeWideBodyCannotUseOneBlockGapButUsesThreeBlockOpening(GameTestHelper h) {
        floor(h);sealEdges(h);for(int z=0;z<24;z++)for(int y=1;y<=6;y++)if(z!=10)set(h,12,y,z,Blocks.STONE);
        BeetleMotherUnit knight=spawn(h,owner("Width"),5,10);BlockPos goal=h.absolutePos(new BlockPos(20,0,10));
        var seq=h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,knight)&&h.getLevel().getEntity(knight.getId())==knight,"published real wide body"))
            .thenExecute(()->order(knight,UnitAction.MOVE,goal));
        seq.thenExecute(()->System.out.println("MOTHER_GEOMETRY_INITIAL "+diagnostic(h,knight)));
        for(int i=0;i<160;i++)seq.thenIdle(1).thenExecute(()->h.assertTrue(h.getLevel().noCollision(knight)&&knight.getX()<h.absolutePos(new BlockPos(12,0,0)).getX(),"2-wide body cannot clip a one-block gap: "+diagnostic(h,knight)));
        seq.thenExecute(()->{for(int z=9;z<=11;z++)for(int y=1;y<=4;y++)set(h,12,y,z,Blocks.AIR);});
        seq.thenExecute(()->System.out.println("MOTHER_GEOMETRY_OPEN "+diagnostic(h,knight)));
        for(int i=0;i<300;i++)seq.thenIdle(1).thenExecute(()->h.assertTrue(h.getLevel().noCollision(knight),"new opening remains actual body collision safe"));
        seq.thenExecute(()->h.assertTrue(near(knight,goal,1.8)&&Math.abs(knight.getBbWidth()-2.0)<.001&&Math.abs(knight.getBbHeight()-1.0)<.001,"same retained order reaches through real3-wide4-high opening with no double native scaling")).thenSucceed();
    }
    @GameTest(template="mother_unit_arena",batch=BATCH,timeoutTicks=560)
    public static void diagonalTerminalNodeReallySettlesAndQueuedLegCanAdvance(GameTestHelper h){
        terminalPath(h,"Terminal",false,UnitAction.MOVE);
    }
    @GameTest(template="mother_unit_arena",batch=BATCH,timeoutTicks=560)
    public static void reverseDiagonalAlsoMeetsBothArrivalPredicatesBeforeShift(GameTestHelper h){terminalPath(h,"Reverse",true,UnitAction.MOVE);}
    @GameTest(template="mother_unit_arena",batch=BATCH,timeoutTicks=560)
    public static void unopposedAttackMoveUsesSameSafeTerminalPrecisionAndThenShift(GameTestHelper h){terminalPath(h,"AttackMove",true,UnitAction.ATTACK_MOVE);}
    private static void terminalPath(GameTestHelper h,String suffix,boolean reverse,UnitAction initial){
        floor(h);var mother=spawn(h,owner(suffix),reverse?20:4,reverse?20:17);
        BlockPos first=h.absolutePos(new BlockPos(reverse?4:17,0,4)),second=h.absolutePos(new BlockPos(reverse?20:4,0,4));
        boolean[] visited={false},advanced={false};
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,mother),"published actual wide mother"))
            .thenExecute(()->{
                order(mother,initial,first);
                UnitServerEvents.addActionItem(mother.getOwnerName(),UnitAction.MOVE,-1,new int[]{mother.getId()},second,BlockPos.ZERO,true);
                h.runAfterDelay(10,()->{if(initial==UnitAction.ATTACK_MOVE)h.assertTrue(first.equals(mother.getAttackMoveTarget()),"real ATTACK_MOVE dispatcher installs actual attack-move leg");});
                for(int tick=1;tick<=450;tick++)h.runAfterDelay(tick,()->{
                    h.assertTrue(h.getLevel().noCollision(mother)&&!mother.noPhysics&&!mother.isNoGravity(),"terminal steering preserves actual ground collision");
                    if(mother.distanceToSqr(first.getCenter())<4)visited[0]=true;
                    if(second.equals(mother.getMoveGoal().getMoveTarget())){h.assertTrue(visited[0],"Shift cannot skip first diagonal arrival");advanced[0]=true;}
                });
                h.runAfterDelay(460,()->{
                    h.assertTrue(visited[0]&&advanced[0]&&arrivedByCoreContract(mother,second)&&mother.getMoveGoal().getMoveTarget()==null,"existing core arrival clears each MOVE and actually dispatches the queued next leg: "+diagnostic(h,mother));
                    System.out.println("DREAMSEEKERS_MOTHER_WIDE_TERMINAL_ARRIVAL_AND_SHIFT_PASS "+suffix);h.succeed();
                });
            });
    }
    private static void sealEdges(GameTestHelper h){for(int i=0;i<24;i++)for(int y=1;y<=6;y++){set(h,0,y,i,Blocks.STONE);set(h,23,y,i,Blocks.STONE);set(h,i,y,0,Blocks.STONE);set(h,i,y,23,Blocks.STONE);}}
    private static String diagnostic(GameTestHelper h,BeetleMotherUnit k){var p=k.getNavigation().getPath();return "origin="+h.absolutePos(BlockPos.ZERO)+" pos="+k.position()+" body="+k.getBoundingBox()+" collisionFree="+h.getLevel().noCollision(k)+" tick="+k.tickCount+" move="+k.getMoveGoal().getMoveTarget()+" path="+(p==null?null:("reach="+p.canReach()+",next="+p.getNextNodeIndex()+"/"+p.getNodeCount()+",target="+p.getTarget()+",nodes="+java.util.stream.IntStream.range(0,p.getNodeCount()).mapToObj(i->p.getNode(i).asBlockPos()).toList()));}
    private static String owner(String suffix){String name=PREFIX+suffix;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));return name;}
    private static BeetleMotherUnit spawn(GameTestHelper h,String owner,int x,int z){BeetleMotherUnit mother=DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel());h.assertTrue(mother!=null,"paid mother created");mother.setOwnerName(owner);BlockPos p=h.absolutePos(new BlockPos(x,1,z));mother.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);FIXTURES.add(mother);h.assertTrue(h.getLevel().addFreshEntity(mother),"paid mother entered actual level");return mother;}
    private static void order(BeetleMotherUnit mother,UnitAction action,BlockPos pos){UnitServerEvents.addActionItem(mother.getOwnerName(),action,-1,new int[]{mother.getId()},pos,BlockPos.ZERO);}
    private static boolean near(BeetleMotherUnit mother,BlockPos pos,double tolerance){return Math.abs(mother.getX()-(pos.getX()+.5))<tolerance&&Math.abs(mother.getZ()-(pos.getZ()+.5))<tolerance;}
    private static String describe(BeetleMotherUnit mother){return "pos="+mother.position()+" ticks="+mother.tickCount+" target="+mother.getMoveGoal().getMoveTarget()+" navDone="+mother.getNavigation().isDone()+" noAI="+mother.isNoAi();}
    private static void set(GameTestHelper h,int x,int y,int z,net.minecraft.world.level.block.Block b){h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),b.defaultBlockState(),2);}
    private static void floor(GameTestHelper h){for(int i=-1;i<=24;i++)for(int yy=1;yy<=10;yy++){h.getLevel().setBlock(h.absolutePos(new BlockPos(-1,yy,i)),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(24,yy,i)),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(i,yy,-1)),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(i,yy,24)),Blocks.STONE.defaultBlockState(),2);}
        BlockPos p=h.absolutePos(BlockPos.ZERO);
        for(int x=p.getX()>>4;x<=(p.getX()+23)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+23)>>4;z++){var c=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(x,z,true);TICKETS.add(c);}}
        for(int x=0;x<24;x++)for(int z=0;z<24;z++)set(h,x,10,z,Blocks.STONE);
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);
        for(int x=0;x<24;x++)for(int z=0;z<24;z++){set(h,x,0,z,Blocks.STONE);for(int y=1;y<10;y++)set(h,x,y,z,Blocks.AIR);}
    }
    private static boolean published(GameTestHelper h,Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}

    /** Exact existing RoN large-body arrival fallback, not a widened arbitrary tolerance.
     * Vanilla width2 uses a1.5 node center offset and1.0 waypoint reach. A stationary completed
     * path can therefore finish just beyond the copied Wind fixture's1.8-axis threshold.
     * Require completion, real idle and the existing strict3D squared-distance<4 together. */
    private static boolean arrivedByCoreContract(BeetleMotherUnit e,BlockPos click){return e.getNavigation().isDone()&&e.isIdle()&&e.distanceToSqr(click.getCenter())<4;}

}
