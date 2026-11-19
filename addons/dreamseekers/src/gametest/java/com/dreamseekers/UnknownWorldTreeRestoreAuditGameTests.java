package com.dreamseekers;

import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.data.DataStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Audit-only fixture. Known-complete template is intentional for a save-loader boundary test,
 * not evidence that normal paid foundation construction works. Do not package in release.
 */
@GameTestHolder("dreamseekers_audit_fixes")
@PrefixGameTestTemplate(false)
public final class UnknownWorldTreeRestoreAuditGameTests {
    private static final List<BuildingPlacement> FIXTURES=new ArrayList<>();
    @AfterBatch(batch="unknown_model_audit")
    public static void cleanup(net.minecraft.server.level.ServerLevel level) {
        for(var p:FIXTURES) {
            p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);
            BuildingServerEvents.getBuildings().remove(p);
        }
        FIXTURES.clear();
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="branch_arena",batch="unknown_model_audit",timeoutTicks=20)
    public static void unsupportedWindPayloadMustNotEnterNativeDestruction(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(3,1,3));
        var geometry=BuildingUtils.getAbsoluteBlockData(WorldTreeModel.WIND_V4.blocks(),level,origin,Rotation.NONE);
        for(var b:geometry)level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        var prior=new WorldTreeState();prior.model=WorldTreeModel.WIND_V4;
        prior.phase=WorldTreeState.Phase.COMPLETE;prior.geometryHighest=377;
        var tag=prior.write();tag.putInt("schema",99);tag.putString("futurePayload","preserve-me");
        var opaque=WorldTreeState.read(tag);
        h.assertTrue(!opaque.supported&&opaque.write().equals(tag),"unknown fixture codec preserved");
        var saved=new DataStorage();saved.setData(DreamWorldTreeService.STATE,opaque);
        var inert=(DreamAnchorPlacement)DreamseekersMod.DREAM_ANCHOR.createRestoredBuildingPlacement(
                level,origin,Rotation.NONE,"UnknownModelAudit",saved);
        // Equivalent to native BuildingServerEvents saved DataStorage assignment for this single relevant field.
        inert.getDataStorage().setData(DreamWorldTreeService.STATE,opaque);
        inert.isBuilt=true;inert.afterRestoredFromSave();
        var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var b:geometry)before.put(b.getBlockPos(),level.getBlockState(b.getBlockPos()));
        BuildingServerEvents.getBuildings().add(inert);FIXTURES.add(inert);
        long installedTick=level.getGameTime();
        System.out.println("UNKNOWN_WIND_RESTORE_AUDIT placed="+inert.getBlocksPlaced()
                +" total="+inert.getBlocksTotal()+" shouldDestroy="+inert.shouldBeDestroyed()
                +" supported="+inert.tree().supported);
        // Do not invoke tick manually or stop at the predicate. Await real ordinary server ticks:
        // BuildingServerEvents.onWorldTick runs its destruction/removal path before placement.tick.
        h.startSequence().thenIdle(3).thenExecute(()->{
            h.assertTrue(level.getGameTime()>installedTick,"actual game time advanced");
            System.out.println("UNKNOWN_WIND_NATIVE_TICK_AUDIT tracked="+BuildingServerEvents.getBuildings().contains(inert)
                    +" changed="+before.entrySet().stream().filter(e->!level.getBlockState(e.getKey()).equals(e.getValue())).count());
            h.assertTrue(BuildingServerEvents.getBuildings().contains(inert),"unknown model survives real core tick without removal");
            for(var e:before.entrySet())h.assertTrue(level.getBlockState(e.getKey()).equals(e.getValue()),"unknown model never mutates world");
            h.assertTrue(inert.getDataStorage().getData(DreamWorldTreeService.STATE).write().equals(tag),"unknown payload remains byte-semantic equivalent");
        }).thenSucceed();
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="branch_arena",batch="unknown_model_audit",timeoutTicks=20)
    public static void supportedWindStillUsesNativeDamageAndDestruction(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(3,1,3));
        var geometry=BuildingUtils.getAbsoluteBlockData(WorldTreeModel.WIND_V4.blocks(),level,origin,Rotation.NONE);
        for(var b:geometry)level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        var state=new WorldTreeState();state.model=WorldTreeModel.WIND_V4;
        state.phase=WorldTreeState.Phase.COMPLETE;state.geometryHighest=377;
        var saved=new DataStorage();saved.setData(DreamWorldTreeService.STATE,state);
        var tree=(DreamAnchorPlacement)DreamseekersMod.DREAM_ANCHOR.createRestoredBuildingPlacement(
                level,origin,Rotation.NONE,"SupportedModelAudit",saved);
        tree.getDataStorage().setData(DreamWorldTreeService.STATE,state);
        tree.isBuilt=true;tree.afterRestoredFromSave();
        BuildingServerEvents.getBuildings().add(tree);FIXTURES.add(tree);
        h.assertTrue(tree.tree().supported&&!tree.shouldBeDestroyed(),"supported intact tree remains live");
        tree.destroyRandomBlocks(tree.getMaxHealth());
        h.assertTrue(tree.shouldBeDestroyed(),"supported tree retains actual native destruction predicate after physical damage");
        long damagedTick=level.getGameTime();
        h.startSequence().thenIdle(3).thenExecute(()->{
            h.assertTrue(level.getGameTime()>damagedTick,"actual game time advanced");
            h.assertTrue(!BuildingServerEvents.getBuildings().contains(tree),"supported damaged tree removed by ordinary core tick");
            System.out.println("SUPPORTED_WIND_NATIVE_DESTRUCTION_AUDIT tracked=false");
        }).thenSucceed();
    }
}
