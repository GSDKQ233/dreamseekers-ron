// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.slime.SlimeBalance;
import com.dreamseekers.unit.PinkSlimeUnit;
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
import net.pasterdream.entity.PinkSlimeEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Actual ground motion through the ordinary RTS command path, never per-tick fixture teleportation. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PinkSlimeMovementGameTests {
    private static final String BATCH="dreamseekers_slime_movement", PREFIX="SlimeMove_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity entity:FIXTURES)if(!entity.isRemoved())entity.discard();FIXTURES.clear();
        for(String a:List.of("Hostile","Ally"))for(String b:List.of("Hostile","Ally"))
            if(!a.equals(b))AlliancesServerEvents.removeAlliance(PREFIX+a,PREFIX+b);
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=500)
    public static void groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(GameTestHelper h) {
        floor(h);
        for(int z=2;z<=16;z++)for(int y=1;y<=3;y++)set(h,12,y,z,Blocks.STONE);
        PinkSlimeUnit slime=spawn(h,owner("Barrier"),4,10);BlockPos destination=h.absolutePos(new BlockPos(20,0,10));
        boolean[] detoured={false};double y=h.absolutePos(BlockPos.ZERO).getY();
        h.startSequence().thenWaitUntil(()->h.assertTrue(slime.tickCount>2,"wait genuine slime simulation")).thenExecute(()->{
            order(slime,UnitAction.MOVE,destination);
            for(int tick=1;tick<=400;tick++)h.runAfterDelay(tick,()->{
                h.assertTrue(!slime.isNoAi()&&!slime.isNoGravity()&&!slime.noPhysics&&h.getLevel().noCollision(slime),"normal ground body and active AI remain collision-safe");
                h.assertTrue(slime.getY()<y+1.6,"three-block wall is not crossed by flight or climbing");
                if(slime.getZ()>h.absolutePos(new BlockPos(0,0,17)).getZ() || slime.getZ()<h.absolutePos(new BlockPos(0,0,2)).getZ())detoured[0]=true;
            });
            h.runAfterDelay(410,()->{
                h.assertTrue(slime instanceof PinkSlimeEntity&&slime.getNavigation() instanceof GroundPathNavigation&&!slime.isFlyingUnit(),"actual original slime with normal ground navigator");
                h.assertTrue(detoured[0]&&near(slime,destination,1.8),"ground order routes around wall: "+describe(slime));
                System.out.println("DREAMSEEKERS_SLIME_GROUND_BARRIER_PASS "+describe(slime));h.succeed();
            });
        });
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=690)
    public static void enclosedGroundSlimeCannotPhaseAndRetriesOpenedExit(GameTestHelper h) {
        floor(h);
        for(int x=3;x<=11;x++)for(int z=3;z<=11;z++)for(int y=1;y<=5;y++)
            if(x==3||x==11||z==3||z==11||y==5)set(h,x,y,z,Blocks.STONE);
        PinkSlimeUnit slime=spawn(h,owner("Enclosed"),7,7);BlockPos destination=h.absolutePos(new BlockPos(18,0,7));
        h.startSequence().thenWaitUntil(()->h.assertTrue(slime.tickCount>2,"wait genuine enclosed simulation")).thenExecute(()->{
            order(slime,UnitAction.MOVE,destination);
            for(int tick=1;tick<190;tick++)h.runAfterDelay(tick,()->{
                h.assertTrue(h.getLevel().noCollision(slime)&&slime.getX()<h.absolutePos(new BlockPos(11,0,0)).getX(),"sealed wall prevents clipping/teleporting");
            });
            h.runAfterDelay(200,()->{for(int z=6;z<=8;z++)for(int y=1;y<=3;y++)set(h,11,y,z,Blocks.AIR);});
            h.runAfterDelay(580,()->{
                h.assertTrue(near(slime,destination,2)&&h.getLevel().noCollision(slime),"same pending ground order recovers through genuinely opened passage: "+describe(slime));
                System.out.println("DREAMSEEKERS_SLIME_OPENING_RETRY_PASS "+describe(slime));h.succeed();
            });
        });
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=780)
    public static void queuedMovesHoldStopAndReplacementNeverResumeStalePath(GameTestHelper h) {
        floor(h);PinkSlimeUnit slime=spawn(h,owner("Queue"),4,4);
        BlockPos first=h.absolutePos(new BlockPos(17,0,4)),second=h.absolutePos(new BlockPos(17,0,17));
        boolean[] passedFirst={false};Vec3[] held={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(slime.tickCount>2,"wait ground queue readiness")).thenExecute(()->{
            order(slime,UnitAction.MOVE,first);
            UnitServerEvents.addActionItem(slime.getOwnerName(),UnitAction.MOVE,-1,new int[]{slime.getId()},second,BlockPos.ZERO,true);
            for(int tick=1;tick<=330;tick++)h.runAfterDelay(tick,()->{h.assertTrue(h.getLevel().noCollision(slime),"queue keeps collision");if(near(slime,first,1.8))passedFirst[0]=true;});
            h.runAfterDelay(340,()->{
                h.assertTrue(passedFirst[0]&&near(slime,second,1.8),"both queued waypoints visited in order: "+describe(slime));order(slime,UnitAction.HOLD,BlockPos.ZERO);
            });
            h.runAfterDelay(360,()->{
                held[0]=slime.position();h.assertTrue(slime.getHoldPosition()&&slime.getMoveGoal().getMoveTarget()==null,"HOLD has no stale ground destination");
                UnitServerEvents.addActionItem(slime.getOwnerName(),UnitAction.MOVE,-1,new int[]{slime.getId()},first,BlockPos.ZERO,true);
                order(slime,UnitAction.STOP,BlockPos.ZERO);
            });
            h.runAfterDelay(430,()->{
                h.assertTrue(slime.position().distanceToSqr(held[0])<.20&&!slime.getHoldPosition()&&slime.getNavigation().isDone(),"STOP cancels hold, queue and delayed paths");
                h.assertTrue(UnitServerEvents.getUnitActionSlowQueue().stream().noneMatch(a->Arrays.stream(a.getUnitIds()).anyMatch(id->id==slime.getId())),"no stale queued order");
                order(slime,UnitAction.MOVE,first);order(slime,UnitAction.MOVE,h.absolutePos(new BlockPos(4,0,17)));
            });
            h.runAfterDelay(550,()->{
                h.assertTrue(near(slime,h.absolutePos(new BlockPos(4,0,17)),1.8),"replacement defeats older async path: "+describe(slime));
                // Same-tick A -> B -> A is not a duplicate of the older first A.
                order(slime,UnitAction.MOVE,first);order(slime,UnitAction.MOVE,second);order(slime,UnitAction.MOVE,first);
            });
            h.runAfterDelay(710,()->{
                h.assertTrue(near(slime,first,1.8),"last A wins genuine A-B-A same-tick replacement: "+describe(slime));
                System.out.println("DREAMSEEKERS_SLIME_QUEUE_HOLD_STOP_REPLACE_PASS");h.succeed();
            });
        });
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=80)
    public static void malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(GameTestHelper h) {
        floor(h);String owner=owner("InvalidTarget");PinkSlimeUnit slime=spawn(h,owner,4,4);
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
        h.startSequence().thenWaitUntil(()->h.assertTrue(slime.tickCount>2&&scout.tickCount>2,"wait both real controllers")).thenExecute(()->{
            h.assertTrue(h.getLevel().getEntity(removedId)==null,"removed target cannot still resolve");
            Vec3 slimeStart=slime.position(),scoutStart=scout.position();
            for(Unit unit:List.<Unit>of(slime,scout))for(UnitAction action:List.of(UnitAction.FOLLOW,UnitAction.ATTACK))
                for(int id:new int[]{item.getId(),boat.getId(),Integer.MAX_VALUE,removedId}){
                    // Exercise actual dispatcher rather than replacing it with a test-side eligibility helper.
                    new UnitActionItem(owner,action,id,new int[]{((Entity)unit).getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                    h.assertTrue(unit.getFollowTarget()==null&&((net.minecraft.world.entity.Mob)unit).getTarget()==null
                            &&unit.getMoveGoal().getMoveTarget()==null,"invalid target installs no attack/follow/path");
                }
            h.runAfterDelay(35,()->{
                h.assertTrue(slime.position().distanceToSqr(slimeStart)<.2
                        &&Math.abs(scout.getX()-scoutStart.x)<.3&&Math.abs(scout.getZ()-scoutStart.z)<.3,"invalid targets do not start delayed pursuit");
                h.assertTrue(slime.getItems().isEmpty()&&scout.getItems().isEmpty()&&item.isAlive()&&item.getItem().getCount()==7
                        &&boat.isAlive(),"invalid target commands consume/grant no objects");
                Resources ledger=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(owner)).findFirst().orElseThrow();
                h.assertTrue(ledger.food==500&&ledger.wood==500&&ledger.ore==500,"invalid targets grant no resources");
                System.out.println("DREAMSEEKERS_SLIME_SCOUT_INVALID_ENTITY_COMMAND_NOCRASH_PASS");h.succeed();
            });
        });
    }

    private static String owner(String suffix){String name=PREFIX+suffix;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));return name;}
    private static PinkSlimeUnit spawn(GameTestHelper h,String owner,int x,int z){PinkSlimeUnit slime=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());h.assertTrue(slime!=null,"paid slime created");slime.setOwnerName(owner);BlockPos p=h.absolutePos(new BlockPos(x,1,z));slime.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);FIXTURES.add(slime);h.assertTrue(h.getLevel().addFreshEntity(slime),"paid slime entered actual level");return slime;}
    private static void order(PinkSlimeUnit slime,UnitAction action,BlockPos pos){UnitServerEvents.addActionItem(slime.getOwnerName(),action,-1,new int[]{slime.getId()},pos,BlockPos.ZERO);}
    private static boolean near(PinkSlimeUnit slime,BlockPos pos,double tolerance){return Math.abs(slime.getX()-(pos.getX()+.5))<tolerance&&Math.abs(slime.getZ()-(pos.getZ()+.5))<tolerance;}
    private static String describe(PinkSlimeUnit slime){return "pos="+slime.position()+" ticks="+slime.tickCount+" target="+slime.getMoveGoal().getMoveTarget()+" navDone="+slime.getNavigation().isDone()+" noAI="+slime.isNoAi();}
    private static void set(GameTestHelper h,int x,int y,int z,net.minecraft.world.level.block.Block b){h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),b.defaultBlockState(),2);}
    private static void floor(GameTestHelper h){
        BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=0;x<24;x++)for(int z=0;z<24;z++)set(h,x,10,z,Blocks.STONE);
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);
        for(int x=0;x<24;x++)for(int z=0;z<24;z++){set(h,x,0,z,Blocks.STONE);for(int y=1;y<10;y++)set(h,x,y,z,Blocks.AIR);}
    }
}
