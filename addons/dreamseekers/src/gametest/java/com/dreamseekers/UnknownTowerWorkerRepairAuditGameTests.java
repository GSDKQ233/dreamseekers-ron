package com.dreamseekers;

import com.dreamseekers.building.DreamWatchtower;
import com.dreamseekers.function.DreamWatchtowerPlacement;
import com.dreamseekers.unit.FireflyWorker;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Audit-only supported-vs-opaque loader boundary. Initial templates are fixtures; all repair after
 * setup is performed by published real Fireflies through one-shot native BUILD_REPAIR commands.
 */
@GameTestHolder("dreamseekers_audit_fixes")
@PrefixGameTestTemplate(false)
public final class UnknownTowerWorkerRepairAuditGameTests {
    private static final List<Fixture> FIXTURES=new ArrayList<>();
    @AfterBatch(batch="unknown_tower_audit")
    public static void cleanup(ServerLevel ignored){for(var f:FIXTURES)f.close();FIXTURES.clear();}
    private static final class Fixture {
        final GameTestHelper h;final ServerLevel level;final String owner="UnknownTowerRepairAudit";
        final List<FireflyWorker> workers=new ArrayList<>();final List<BuildingPlacement> buildings=new ArrayList<>();
        final Set<Long> forced=new HashSet<>();final Resources account;
        Fixture(GameTestHelper h){this.h=h;level=h.getLevel();FIXTURES.add(this);
            h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null,"fresh exact audit owner");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
            account=new Resources(owner,0,1000,0);ResourcesServerEvents.resourcesList.add(account);
        }
        void prepare(BlockPos origin){
            for(int x=(origin.getX()-8)>>4;x<=(origin.getX()+15)>>4;x++)for(int z=(origin.getZ()-8)>>4;z<=(origin.getZ()+15)>>4;z++){
                long id=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(id)){level.setChunkForced(x,z,true);forced.add(id);}level.getChunk(x,z);}
            // Bound the underground fixture before clearing: natural gravel must not masquerade as repair writes.
            for(int x=-8;x<=15;x++)for(int z=-8;z<=15;z++)level.setBlock(origin.offset(x,18,z),Blocks.STONE.defaultBlockState(),2);
            for(int x=-8;x<=15;x++)for(int z=-8;z<=15;z++){
                level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=1;y<=17;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            for(var b:BuildingUtils.getAbsoluteBlockData(DreamWatchtowerPlacement.template(2),level,origin,Rotation.NONE))
                level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        }
        FireflyWorker spawn(BlockPos at){var worker=DreamseekersMod.FIREFLY_WORKER.get().create(level);
            h.assertTrue(worker!=null,"real Firefly created");worker.setOwnerName(owner);
            worker.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);
            h.assertTrue(level.addFreshEntity(worker),"real Firefly admitted");workers.add(worker);return worker;}
        boolean published(FireflyWorker w){return w.tickCount>2&&level.getEntity(w.getId())==w&&level.getEntity(w.getUUID())==w&&level.isPositionEntityTicking(w.blockPosition());}
        void close(){
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(owner));
            for(var w:workers){Unit.fullResetBehaviours(w);if(!w.isRemoved())w.discard();}
            for(var p:buildings){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}
            for(long id:forced){var c=new ChunkPos(id);level.setChunkForced(c.x,c.z,false);}
        }
    }
    private static DataStorage savedV2(){
        var tag=new net.minecraft.nbt.CompoundTag();
        tag.putInt("schema",1);tag.putInt("modelRevision",2);tag.putInt("highest",211);tag.putUUID("instance",UUID.randomUUID());
        var storage=new DataStorage();storage.setData(DreamWatchtowerPlacement.MODEL,tag);return storage;
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="branch_arena",batch="unknown_tower_audit",timeoutTicks=20)
    public static void sameRevisionForeignDeckIsParkedWithoutLoadingOrRewriting(GameTestHelper h){
        for(int variant=0;variant<4;variant++){
            var storage=savedV2();var model=storage.getData(DreamWatchtowerPlacement.MODEL);
            // A half-built foreign deck can have a count valid for stock v2, so count alone is not a discriminator.
            model.putInt("highest",75);
            if(variant==1)model.putBoolean("restyled",false);
            if(variant==2)model.putBoolean("restyled",true);
            if(variant==3)model.putString("restyled","not a boolean");
            var snap=model.copy();snap.putString("kind","dream_watchtower");snap.putInt("placed",70);snap.putDouble("partial",.25);
            h.assertTrue(DreamWatchtowerPlacement.validSnapshot(snap)==(variant<2),"only the stock v2 deck may bind a client snapshot");
            var row=new net.minecraft.nbt.CompoundTag();
            row.putString("buildingKey","dreamseekers:shadow_watchtower");row.putString("rotation","NONE");row.putString("portalType","BASIC");
            row.put("dataStorage",storage.write());row.putDouble("partialBlocksDestroyed",.25);
            var pending=new net.minecraft.nbt.CompoundTag();pending.putString("witness","retain-whole-row");row.put("pendingConstruction",pending);
            var rows=new net.minecraft.nbt.ListTag();rows.add(row);var root=new net.minecraft.nbt.CompoundTag();root.put("buildings",rows);
            var parsed=BuildingSaveData.load(root,h.getLevel().getServer());
            h.assertTrue(parsed.buildings.size()==(variant<2?1:0),"native loader must not construct foreign geometry");
            if(variant>=2)h.assertTrue(parsed.save(new net.minecraft.nbt.CompoundTag()).equals(root),"complete foreign row survives round-trip untouched");
        }
        h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="branch_arena",batch="unknown_tower_audit",timeoutTicks=1200)
    public static void opaqueTowerNeverRepairsFallbackButKnownV2StillRepairs(GameTestHelper h){
        var f=new Fixture(h);var origin=h.absolutePos(new BlockPos(20,1,20));var knownOrigin=origin.offset(40,0,0);
        f.prepare(origin);f.prepare(knownOrigin);
        var definition=(DreamWatchtower)DreamseekersMod.SHADOW_WATCHTOWER;
        // Restore a real historical v2 fixture, then make only its saved version unknown.
        var prior=(DreamWatchtowerPlacement)definition.createRestoredBuildingPlacement(f.level,origin,Rotation.NONE,f.owner,savedV2());
        prior.isBuilt=true;prior.afterRestoredFromSave();
        var saved=DataStorage.read(prior.getDataStorage().write(),f.level.getServer());
        var unknown=saved.getData(DreamWatchtowerPlacement.MODEL).copy();unknown.putInt("schema",99);unknown.putString("futurePayload","preserve-me");
        saved.setData(DreamWatchtowerPlacement.MODEL,unknown);
        var inert=(DreamWatchtowerPlacement)definition.createRestoredBuildingPlacement(f.level,origin,Rotation.NONE,f.owner,saved);
        inert.isBuilt=true;inert.afterRestoredFromSave();f.buildings.add(inert);BuildingServerEvents.getBuildings().add(inert);
        h.assertTrue(inert.modelRevision()==-1&&!definition.isGarrisonEnabled(inert),"unknown model disables garrison before repair");
        var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var b:BuildingUtils.getAbsoluteBlockData(DreamWatchtowerPlacement.template(2),f.level,origin,Rotation.NONE))before.put(b.getBlockPos(),f.level.getBlockState(b.getBlockPos()));
        var known=(DreamWatchtowerPlacement)definition.createRestoredBuildingPlacement(f.level,knownOrigin,Rotation.NONE,f.owner,savedV2());
        known.isBuilt=true;known.afterRestoredFromSave();
        f.buildings.add(known);BuildingServerEvents.getBuildings().add(known);known.onBuilt();known.destroyRandomBlocks(10);
        h.assertTrue(definition.isGarrisonEnabled(known)&&DreamWatchtowerPlacement.validSnapshot(known.structureSnapshot()),
            "historical v2 keeps its garrison and is recognized by the current client");
        h.assertTrue(known.modelRevision()==2&&known.getBlocksPlaced()<211&&!known.shouldBeDestroyed(),"known v2 has actual native damage to repair");
        var unknownWorker=f.spawn(origin.offset(3,2,-3));var knownWorker=f.spawn(knownOrigin.offset(3,2,-3));
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.published(unknownWorker)&&f.published(knownWorker),"both actual workers published before one-shot orders"))
            .thenExecute(()->{
                UnitServerEvents.addActionItem(f.owner,UnitAction.BUILD_REPAIR,-1,new int[]{unknownWorker.getId()},inert.originPos,BlockPos.ZERO);
                UnitServerEvents.addActionItem(f.owner,UnitAction.BUILD_REPAIR,-1,new int[]{knownWorker.getId()},known.originPos,BlockPos.ZERO);
            }).thenWaitUntil(()->{
                h.assertTrue(unknownWorker.getBuildRepairGoal().getBuildingTarget()==inert&&unknownWorker.getBuildRepairGoal().isBuilding(),"unknown worker really arrives in native repair range");
            }).thenWaitUntil(()->h.assertTrue(known.getBlocksPlaced()==211&&known.getHealth()==known.getMaxHealth(),"known v2 completes actual worker repair"))
            .thenIdle(120).thenExecute(()->{
                long changed=before.entrySet().stream().filter(e->!f.level.getBlockState(e.getKey()).equals(e.getValue())).count();
                before.entrySet().stream().filter(e->!f.level.getBlockState(e.getKey()).equals(e.getValue())).forEach(e->System.out.println("UNKNOWN_TOWER_CHANGED pos="+e.getKey()+" before="+e.getValue()+" after="+f.level.getBlockState(e.getKey())));
                System.out.println("UNKNOWN_TOWER_NATIVE_REPAIR_AUDIT changed="+changed+" placed="+inert.getBlocksPlaced()+" total="+inert.getBlocksTotal()+" known="+known.getBlocksPlaced()+" wood="+f.account.wood);
                h.assertTrue(BuildingServerEvents.getBuildings().contains(inert),"unknown tower stays tracked");
                h.assertTrue(inert.modelRevision()==-1&&!definition.isGarrisonEnabled(inert),"unknown garrison remains disabled");
                h.assertTrue(inert.getDataStorage().getData(DreamWatchtowerPlacement.MODEL).equals(unknown),"unknown model tag remains byte-semantic equivalent");
                h.assertTrue(changed==0,"actual worker must not write legacy fallback geometry into unknown tower");
                h.assertTrue(known.getBlocksPlaced()==211&&known.modelRevision()==2,"known v2 repair remains operational");
            }).thenSucceed();
    }
}
