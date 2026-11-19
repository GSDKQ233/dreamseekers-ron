// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.JellyfishUnit;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Isolated controlled-interleaving probe of real production spawn/rally, not a paid-queue test. */
@GameTestHolder("dreamseekers_jellyfish_rally_boundary")
@PrefixGameTestTemplate(false)
public final class JellyfishRallyBoundaryGameTests {
    private static final String OWNER="JellyRallyBoundary";
    private static final BlockPos BASE=new BlockPos(24576,100,24576);
    private static JellyfishUnit actor;
    private static ProductionPlacement producer;
    @AfterBatch(batch="jellyfish_rally_boundary") public static void cleanup(ServerLevel level) {
        if(actor!=null&&!actor.isRemoved())actor.discard();
        if(producer!=null)BuildingServerEvents.getBuildings().remove(producer);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));
        ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(OWNER));
    }
    @GameTest(template="unit_isolation_arena",batch="jellyfish_rally_boundary",timeoutTicks=1000)
    public static void firstProductionRallySurvivesFreshSectionVisibilityBoundary(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();
        h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER)==null,"fresh isolated single-case world");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(OWNER,500,500,500));
        for(int x=-4;x<38;x++)for(int z=-4;z<30;z++) {
            level.setBlock(BASE.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<10;y++)level.setBlock(BASE.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(BASE.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);
        }
        var definition=DreamseekersMod.BEETLE_SANCTUARY;
        for(BuildingBlock b:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,BASE,Rotation.NONE))
            level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        producer=(ProductionPlacement)BuildingUtils.getNewBuildingPlacement(definition,level,BASE,Rotation.NONE,OWNER,false);
        BuildingServerEvents.getBuildings().add(producer);producer.onBuilt();
        BlockPos rally=BASE.offset(34,0,10);producer.setRallyPoint(rally);
        // Public production offset overload touches the fresh spawn chunk only at actual spawn.
        // The normal producer/first-rally logic is unchanged; no visibility field is written.
        Entity result=producer.produceUnit(level,DreamseekersMod.JELLYFISH_UNIT.get(),OWNER,false,new net.minecraft.core.Vec3i(48,0,0));
        h.assertTrue(result instanceof JellyfishUnit,"actual production spawning phase returns paid subtype");actor=(JellyfishUnit)result;
        boolean visibleAtSpawn=level.getEntity(actor.getUUID())==actor;
        System.out.println("JELLY_RALLY_BOUNDARY spawned uuid="+actor.getUUID()+" visible="+visibleAtSpawn+" pos="+actor.position()+" rally="+rally);
        h.assertTrue(!visibleAtSpawn,"fixture must actually create a not-yet-accessible entity section; do not silently test the easy case");
        var field=UnitServerEvents.class.getDeclaredField("unitActionFastQueue");field.setAccessible(true);
        @SuppressWarnings("unchecked") var fast=(List<UnitActionItem>)field.get(null);
        h.assertTrue(producer instanceof com.dreamseekers.production.DreamProductionPlacement,"actual addon factory selects scoped server-tick rally placement");
        producer.tick(level); // Actual producer must retain its private handoff while lookup is unavailable.
        synchronized(fast) { h.assertTrue(fast.stream().noneMatch(a->Arrays.stream(a.getUnitIds()).anyMatch(id->id==actor.getId())),
                "no wall-clock callback or missing-ID fast action is emitted before publication"); }
        UnitServerEvents.onWorldTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,level,()->true));
        h.assertTrue(level.getEntity(actor.getId())==null&&level.getEntity(actor.getUUID())==null&&actor.getMoveGoal().getMoveTarget()==null,
                "controlled dispatcher leaves unpublished paid entity untouched; scoped producer keeps one-shot handoff");
        System.out.println("JELLY_RALLY_BOUNDARY dispatched invisible=true no_missing_id_action=true move="+actor.getMoveGoal().getMoveTarget());
        // Only after the controlled dispatch, prepare a clear real route for the positive control.
        for(int x=14;x<=70;x++)for(int z=7;z<=13;z++) {
            level.setBlock(BASE.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<8;y++)level.setBlock(BASE.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        boolean[] originalLost={false};
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.getEntity(actor.getUUID())==actor&&actor.tickCount>2,"normal server tracking eventually publishes produced entity"))
            .thenIdle(20).thenExecute(()->{
                originalLost[0]=actor.getMoveGoal().getMoveTarget()==null&&actor.getNavigation().isDone();
                System.out.println("JELLY_RALLY_BOUNDARY published originalLost="+originalLost[0]+" pos="+actor.position());
                if(originalLost[0])UnitServerEvents.addActionItem(OWNER,UnitAction.MOVE,-1,new int[]{actor.getId()},rally,BlockPos.ZERO);
            }).thenIdle(700).thenExecute(()->{
                double dx=actor.getX()-(rally.getX()+.5),dz=actor.getZ()-(rally.getZ()+.5);
                h.assertTrue(dx*dx+dz*dz<4,"real first rally (or diagnostic post-publication control if lost) reaches the same point");
                System.out.println("JELLY_RALLY_BOUNDARY arrived=true replacementIssued="+originalLost[0]+" pos="+actor.position());
                h.assertTrue(!originalLost[0],"first real production rally was consumed while invisible, although a fresh post-publication MOVE reached the same point");
                System.out.println("DREAMSEEKERS_PRODUCTION_FIRST_RALLY_VISIBILITY_BOUNDARY_PASS");
            }).thenSucceed();
    }
}
