package com.dreamseekers;

import com.dreamseekers.worldtree.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real native blocks and account receipts; run in the paired core/addon Forge server. */
@GameTestHolder("dreamseekers_branches") @PrefixGameTestTemplate(false)
public final class WorldTreeBranchGameTests {
    private static final List<Fixture> active=new ArrayList<>();
    @AfterBatch(batch="branches") public static void cleanup(ServerLevel ignored){for(var f:active)f.close();active.clear();}
    @GameTest(templateNamespace="dreamseekers_branches",template="branch_test_arena",batch="branches",timeoutTicks=400)
    public static void fourRotationsAndLegacyMigration(GameTestHelper h){
        for(var rotation:Rotation.values()){var f=new Fixture(h,rotation);active.add(f);try{f.run();}finally{f.close();active.remove(f);}}
        h.succeed();
    }
    /** The reviewed common court uses a bud wall, not the v3 fence row. Exercise a real
     * neighbour update without relaxing ownership of waterlogged/different-material blocks. */
    static void checkReviewedWall(GameTestHelper h,DreamAnchorPlacement p,WorldTreeModel target){
        var level=h.getLevel();var at=new BlockPos(3,3,3).rotate(p.rotation).offset(p.originPos).above();
        var neighbour=new BlockPos(3,3,2).rotate(p.rotation).offset(p.originPos).above();
        var old=level.getBlockState(at);
        h.assertTrue(old.getBlock() instanceof WallBlock&&level.isEmptyBlock(neighbour),"reviewed isolated bud wall");
        level.setBlockAndUpdate(neighbour,Blocks.STONE.defaultBlockState());
        var connected=level.getBlockState(at);
        h.assertTrue(connected.getBlock()==old.getBlock()&&!connected.equals(old),"native neighbour connects the wall");
        var plan=WorldTreeMorph.plan(p,target);
        h.assertTrue(plan.valid(),"adjacency-only change allowed, foreign stone in unused air left alone: "+plan.error());
        level.destroyBlock(neighbour,false);
        h.assertTrue(!WorldTreeMorph.commit(p,plan),"world changed after plan; optimistic read-set stays exact");
        var dry=level.getBlockState(at);
        level.setBlock(at,Blocks.COBBLESTONE_WALL.defaultBlockState(),2);
        h.assertTrue("foreign_block".equals(WorldTreeMorph.plan(p,target).error()),"different wall material is foreign");
        level.setBlock(at,dry.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true),2);
        h.assertTrue("foreign_block".equals(WorldTreeMorph.plan(p,target).error()),"waterlogging is not an adjacency change");
        level.setBlock(at,dry,2);
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerLevel level;final Rotation rotation;final String owner;
        final Set<Long> chunks=new HashSet<>();final List<DreamAnchorPlacement> buildings=new ArrayList<>();
        Resources account;PassiveIncomePeer peer;
        Fixture(GameTestHelper h,Rotation rotation){this.h=h;this.level=h.getLevel();this.rotation=rotation;this.owner="Branch_"+rotation.name();}
        void check(boolean value,String message){h.assertTrue(value,message);}
        DreamAnchorPlacement create(int offset,WorldTreeModel model){
            var origin=h.absolutePos(new BlockPos(40+offset,1,40));
            for(int x=(origin.getX()-16)>>4;x<=(origin.getX()+16)>>4;x++)for(int z=(origin.getZ()-16)>>4;z<=(origin.getZ()+16)>>4;z++){
                long c=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(c)){level.setChunkForced(x,z,true);chunks.add(c);}}
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=22;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            var geometry=BuildingUtils.getAbsoluteBlockData(model.blocks(),level,origin,rotation);
            for(var b:geometry)level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            var p=new DreamAnchorPlacement(DreamseekersMod.DREAM_ANCHOR,level,origin,rotation,owner,geometry,true);
            p.tree().model=model;p.tree().ascended=model.branch==WorldTreeModel.Branch.SHADOW;p.tree().phase=model.branch==WorldTreeModel.Branch.BASE?WorldTreeState.Phase.IDLE:WorldTreeState.Phase.COMPLETE;
            p.isBuilt=true;BuildingServerEvents.getBuildings().add(p);buildings.add(p);return p;
        }
        void complete(DreamAnchorPlacement p){p.tree().remaining=0;p.tree().lastTick=Long.MIN_VALUE;DreamWorldTreeService.tick(p,level);}
        void run(){
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
            account=new Resources(owner,10000,10000,10000);account.extra.put(DreamPassiveEconomy.SHADOW,2000);ResourcesServerEvents.resourcesList.add(account);peer=new PassiveIncomePeer(level,owner);
            for(var model:WorldTreeModel.values())check(model.blocks().stream().filter(b->!b.getBlockState().isAir()).count()==model.count,"trusted count "+model);
            var shadow=create(0,WorldTreeModel.BASE_V5);var wind=create(35,WorldTreeModel.LEGACY_BASE);
            var legacy=wind.tree().write();legacy.putInt("schema",1);legacy.remove("model");legacy.remove("target");
            var old=WorldTreeState.read(legacy);check(old.supported&&old.model==WorldTreeModel.LEGACY_BASE,"schema1 remains legacy base");
            wind.getDataStorage().setData(DreamWorldTreeService.STATE,old);
            wind.destroyRandomBlocks(20);int legacyHp=wind.getHealth();
            var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();for(var b:wind.getBlocks())before.put(b.getBlockPos(),level.getBlockState(b.getBlockPos()));
            var restored=DreamseekersMod.DREAM_ANCHOR.createRestoredBuildingPlacement(level,wind.originPos,rotation,owner,wind.getDataStorage());
            restored.isBuilt=true;restored.partialBlocksDestroyed=wind.partialBlocksDestroyed;restored.getDataStorage().setData(DreamWorldTreeService.STATE,old);restored.afterRestoredFromSave();
            check(restored.getHealth()==legacyHp&&Double.compare(restored.partialBlocksDestroyed,wind.partialBlocksDestroyed)==0,"damaged legacy base reload preserves exact HP and partial damage");
            check(restored.getBlocksTotal()==324,"restore does not auto migrate base");for(var e:before.entrySet())check(level.getBlockState(e.getKey()).equals(e.getValue()),"restore no world edits");
            var unknown=old.write();unknown.getCompound("model").putInt("revision",999);var inert=WorldTreeState.read(unknown);check(!inert.supported&&inert.write().equals(unknown),"future descriptor round trips inert");
            var target=wind.blocks(WorldTreeModel.WIND_V5);var obstacle=target.stream().filter(b->!b.getBlockState().isAir()&&b.getBlockPos().getY()>wind.maxCorner.getY()).findFirst().orElseThrow().getBlockPos();
            level.setBlock(obstacle,Blocks.GOLD_BLOCK.defaultBlockState(),2);
            check(DreamWorldTreeService.start(peer.actor,wind,wind.tree().instance,WorldTreeModel.WIND_V5).equals("foreign_block"),"foreign block preflight rejected");check(account.food==10000,"no charge rejected start");level.setBlock(obstacle,Blocks.AIR.defaultBlockState(),2);
            checkReviewedWall(h,shadow,WorldTreeModel.SHADOW_V5);
            check(DreamWorldTreeService.start(peer.actor,shadow,shadow.tree().instance,WorldTreeModel.SHADOW_V5).equals("started"),"shadow starts");
            check(DreamWorldTreeService.start(peer.actor,wind,wind.tree().instance,WorldTreeModel.WIND_V5).equals("started"),"wind coexists with same owner shadow");
            check(!com.dreamseekers.progression.DreamWindProgression.server(owner).wind(),"pending Wind has no free unlock");
            var receipt=account.paidReceipts.get(wind.tree().receipt);wind.tree().target=WorldTreeModel.SHADOW_V5;complete(wind);
            check(wind.model()==WorldTreeModel.LEGACY_BASE&&wind.tree().blockedReason.equals("missing_receipt"),"wrong target receipt cannot complete");wind.tree().target=WorldTreeModel.WIND_V5;
            account.paidReceipts.put(receipt.id(),new ResourcePaidReceipt(receipt.id(),receipt.payerEpoch(),receipt.purpose(),1,receipt.wood(),receipt.ore(),receipt.emerald(),receipt.extra()));complete(wind);
            check(wind.model()==WorldTreeModel.LEGACY_BASE&&wind.tree().blockedReason.equals("missing_receipt"),"wrong real price cannot complete");account.paidReceipts.put(receipt.id(),receipt);
            level.setBlock(obstacle,Blocks.GOLD_BLOCK.defaultBlockState(),2);complete(wind);
            check(wind.tree().phase==WorldTreeState.Phase.BLOCKED&&level.getBlockState(obstacle).is(Blocks.GOLD_BLOCK),"BLOCKED foreign block untouched");
            check(DreamWorldTreeService.cancel(peer.actor,wind,wind.tree().instance).equals("cancelled_refunded"),"wind blocked refund");
            check(account.food==9400&&account.wood==9200&&account.ore==9400&&account.extra.get(DreamPassiveEconomy.SHADOW)==1850,"exact wind full refund retains shadow price");
            level.setBlock(obstacle,Blocks.AIR.defaultBlockState(),2);
            wind.destroyRandomBlocks(30);int hp=wind.getHealth();
            check(DreamWorldTreeService.start(peer.actor,wind,wind.tree().instance,WorldTreeModel.WIND_V5).equals("started"),"damaged legacy base explicit wind upgrade");complete(wind);complete(shadow);
            check(wind.model()==WorldTreeModel.WIND_V5&&shadow.model()==WorldTreeModel.SHADOW_V5,"independent completed branches");
            check(com.dreamseekers.progression.DreamWindProgression.server(owner).wind(),"original paid completion earns durable account Wind");
            check(!wind.ascended()&&shadow.ascended(),"wind cannot satisfy shadow ascendancy");
            check(Math.abs(hp-wind.getHealth())<=1&&wind.getBlocksPlaced()<393,"damage projects into real repair holes without healing");
            var shadowSnapshot=shadow.structureSnapshot();check(DreamAnchorPlacement.validSnapshot(shadowSnapshot),"new Shadow descriptor snapshot accepted");
            shadowSnapshot.putInt("highest",999);check(!DreamAnchorPlacement.validSnapshot(shadowSnapshot),"arbitrary count rejected");
            var windSaved=WorldTreeState.read(wind.tree().write());var savedData=new DataStorage();savedData.setData(DreamWorldTreeService.STATE,windSaved);
            var restoredWind=DreamseekersMod.DREAM_ANCHOR.createRestoredBuildingPlacement(level,wind.originPos,rotation,owner,savedData);
            restoredWind.isBuilt=true;restoredWind.partialBlocksDestroyed=wind.partialBlocksDestroyed;restoredWind.getDataStorage().setData(DreamWorldTreeService.STATE,windSaved);restoredWind.afterRestoredFromSave();
            check(Math.abs(restoredWind.getHealth()-wind.getHealth())<=1&&restoredWind.getBlocksTotal()==393,"damaged Wind reload preserves geometry and HP");
            check(account.paidReceipts.isEmpty(),"all original receipts consumed once");
            var crown=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("dreamseekers:meltdream_crown_crystal"));
            check(crown!=Blocks.AIR&&!(crown instanceof EntityBlock)&&!crown.defaultBlockState().isRandomlyTicking(),"static registered crown no BE/no random tick");
            check(crown.getLootTable().equals(net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY),"crown has no loot");
            // Old already-paid work uses immutable legacy target, not today's visual revision.
            var pending=create(70,WorldTreeModel.LEGACY_BASE);var payment=ResourceTransaction.pay(level.getServer(),account,account.accountEpoch,account.revision,DreamWorldTreeService.PURPOSE,DreamWorldTreeService.COST);
            check(payment.applied(),"legacy paid receipt");var st=pending.tree();st.target=WorldTreeModel.LEGACY_SHADOW;st.receipt=payment.receiptId();st.payer=owner;st.payerEpoch=account.accountEpoch;st.phase=WorldTreeState.Phase.RUNNING;
            var oldPending=st.write();oldPending.putInt("schema",1);oldPending.remove("model");oldPending.remove("target");pending.getDataStorage().setData(DreamWorldTreeService.STATE,WorldTreeState.read(oldPending));complete(pending);
            check(pending.model()==WorldTreeModel.LEGACY_SHADOW&&pending.getBlocksTotal()==451,"legacy pending finishes legacy target");
            pending.destroyRandomBlocks(25);int legacyShadowHp=pending.getHealth();var legacyComplete=pending.tree().write();legacyComplete.putInt("schema",1);legacyComplete.remove("model");legacyComplete.remove("target");
            var legacyData=new DataStorage();legacyData.setData(DreamWorldTreeService.STATE,WorldTreeState.read(legacyComplete));
            var oldShadow=DreamseekersMod.DREAM_ANCHOR.createRestoredBuildingPlacement(level,pending.originPos,rotation,owner,legacyData);
            oldShadow.isBuilt=true;oldShadow.partialBlocksDestroyed=pending.partialBlocksDestroyed;oldShadow.getDataStorage().setData(DreamWorldTreeService.STATE,legacyData.getData(DreamWorldTreeService.STATE));oldShadow.afterRestoredFromSave();
            check(oldShadow.getBlocksTotal()==451&&Math.abs(oldShadow.getHealth()-legacyShadowHp)<=1,"completed legacy Shadow reload unchanged and nonhealing");
            if(rotation==Rotation.NONE){
                resumeHistoricalPaid(105,WorldTreeModel.SHADOW_V3);
                resumeHistoricalPaid(140,WorldTreeModel.WIND_V4);
            }
            BuildingServerEvents.getBuildings().remove(wind);wind.destroy(level);
            check(com.dreamseekers.progression.DreamWindProgression.server(owner).wind(),"paid Wind unlock survives building loss");
        }
        /** Model v3/v4 was already paid in an old save: do not reprice, redirect, or heal it. */
        void resumeHistoricalPaid(int offset,WorldTreeModel target){
            var p=create(offset,WorldTreeModel.BASE_V3);
            check(DreamWorldTreeService.start(peer.actor,p,p.tree().instance,target).equals("invalid_target"),"retired revision cannot be bought anew");
            // Regression for previously reported v3 fence behaviour: damaging the adjacent owned
            // fence must leave a naturally disconnected survivor eligible for the old paid target.
            var west=p.originPos.offset(2,6,8);var east=p.originPos.offset(3,6,8);
            check(level.getBlockState(east).getValue(FenceBlock.WEST),"old fence starts connected");
            check(level.destroyBlock(west,false),"damage old v3 fence");p.onBlockBreak(level,west,false);
            var disconnected=level.getBlockState(east);
            check(!disconnected.getValue(FenceBlock.WEST)&&WorldTreeMorph.plan(p,target).valid(),"native fence disconnect still owned");
            level.setBlock(east,Blocks.OAK_FENCE.defaultBlockState(),2);
            check("foreign_block".equals(WorldTreeMorph.plan(p,target).error()),"old descriptor still rejects other fence material");
            level.setBlock(east,disconnected.setValue(FenceBlock.WATERLOGGED,true),2);
            check("foreign_block".equals(WorldTreeMorph.plan(p,target).error()),"old descriptor still rejects waterlogging");
            level.setBlock(east,disconnected,2);

            var payment=ResourceTransaction.pay(level.getServer(),account,account.accountEpoch,account.revision,
                    DreamWorldTreeService.purpose(target),DreamWorldTreeService.cost(target));
            check(payment.applied(),"historical "+target+" payment");
            var before=p.tree();before.target=target;before.receipt=payment.receiptId();before.payer=owner;
            before.payerEpoch=account.accountEpoch;before.phase=WorldTreeState.Phase.RUNNING;before.remaining=10;
            int oldHealth=p.getHealth();var restored=WorldTreeState.read(before.write());
            check(restored.supported&&restored.model==WorldTreeModel.BASE_V3&&restored.target==target
                &&restored.instance.equals(before.instance)&&restored.receipt.equals(payment.receiptId()),"historical in-flight receipt/descriptor survived save");
            p.getDataStorage().setData(DreamWorldTreeService.STATE,restored);p.afterRestoredFromSave();complete(p);
            check(p.model()==target&&p.getBlocksTotal()==target.count&&Math.abs(p.getHealth()-oldHealth)<=1
                &&!account.paidReceipts.containsKey(payment.receiptId()),"old paid task consumed once at its exact geometry and health: "+target);
            var snapshot=p.structureSnapshot();check(DreamAnchorPlacement.validSnapshot(snapshot)&&WorldTreeModel.read(snapshot,"model")==target,
                "current client accepts the old finished descriptor: "+target);
        }
        void close(){if(peer!=null)peer.close();BuildingServerEvents.getBuildings().removeAll(buildings);PlayerServerEvents.rtsPlayers.removeIf(p->owner.equals(p.name));ResourcesServerEvents.removeAccount(owner);for(long c:chunks){var p=new ChunkPos(c);level.setChunkForced(p.x,p.z,false);}}
    }
}
