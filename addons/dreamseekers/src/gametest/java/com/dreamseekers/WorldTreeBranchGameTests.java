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
            var shadow=create(0,WorldTreeModel.BASE_V3);var wind=create(35,WorldTreeModel.LEGACY_BASE);
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
            var target=wind.blocks(WorldTreeModel.WIND_V4);var obstacle=target.stream().filter(b->!b.getBlockState().isAir()&&b.getBlockPos().getY()>wind.maxCorner.getY()).findFirst().orElseThrow().getBlockPos();
            level.setBlock(obstacle,Blocks.GOLD_BLOCK.defaultBlockState(),2);
            check(DreamWorldTreeService.start(peer.actor,wind,wind.tree().instance,WorldTreeModel.WIND_V4).equals("foreign_block"),"foreign block preflight rejected");check(account.food==10000,"no charge rejected start");level.setBlock(obstacle,Blocks.AIR.defaultBlockState(),2);
            // Native pane/wall adjacency-only changes are owned; waterlogging and optimistic read-set stay strict.
            for(var block:shadow.getBlocks()){
                var expected=block.getBlockState();var at=block.getBlockPos();
                if(!(expected.getBlock() instanceof IronBarsBlock)&&!(expected.getBlock() instanceof WallBlock))continue;
                var changed=expected.getBlock() instanceof IronBarsBlock?expected.setValue(IronBarsBlock.NORTH,!expected.getValue(IronBarsBlock.NORTH)):
                    expected.setValue(WallBlock.UP,!expected.getValue(WallBlock.UP));
                level.setBlock(at,changed,2);check(WorldTreeMorph.plan(shadow,WorldTreeModel.SHADOW_V3).valid(),"native neighbor connection equivalence");
                var plan=WorldTreeMorph.plan(shadow,WorldTreeModel.SHADOW_V3);level.setBlock(at,expected,2);check(!WorldTreeMorph.commit(shadow,plan),"read-set remains exact even for connection changes");
                if(expected.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)){
                    level.setBlock(at,expected.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true),2);
                    check("foreign_block".equals(WorldTreeMorph.plan(shadow,WorldTreeModel.SHADOW_V3).error()),"waterlogged mismatch remains foreign");level.setBlock(at,expected,2);
                }
                break;
            }
            check(DreamWorldTreeService.start(peer.actor,shadow,shadow.tree().instance,WorldTreeModel.SHADOW_V3).equals("started"),"shadow starts");
            check(DreamWorldTreeService.start(peer.actor,wind,wind.tree().instance,WorldTreeModel.WIND_V4).equals("started"),"wind coexists with same owner shadow");
            check(!com.dreamseekers.progression.DreamWindProgression.server(owner).wind(),"pending Wind has no free unlock");
            var receipt=account.paidReceipts.get(wind.tree().receipt);wind.tree().target=WorldTreeModel.SHADOW_V3;complete(wind);
            check(wind.model()==WorldTreeModel.LEGACY_BASE&&wind.tree().blockedReason.equals("missing_receipt"),"wrong target receipt cannot complete");wind.tree().target=WorldTreeModel.WIND_V4;
            account.paidReceipts.put(receipt.id(),new ResourcePaidReceipt(receipt.id(),receipt.payerEpoch(),receipt.purpose(),1,receipt.wood(),receipt.ore(),receipt.emerald(),receipt.extra()));complete(wind);
            check(wind.model()==WorldTreeModel.LEGACY_BASE&&wind.tree().blockedReason.equals("missing_receipt"),"wrong real price cannot complete");account.paidReceipts.put(receipt.id(),receipt);
            level.setBlock(obstacle,Blocks.GOLD_BLOCK.defaultBlockState(),2);complete(wind);
            check(wind.tree().phase==WorldTreeState.Phase.BLOCKED&&level.getBlockState(obstacle).is(Blocks.GOLD_BLOCK),"BLOCKED foreign block untouched");
            check(DreamWorldTreeService.cancel(peer.actor,wind,wind.tree().instance).equals("cancelled_refunded"),"wind blocked refund");
            check(account.food==9400&&account.wood==9200&&account.ore==9400&&account.extra.get(DreamPassiveEconomy.SHADOW)==1850,"exact wind full refund retains shadow price");
            level.setBlock(obstacle,Blocks.AIR.defaultBlockState(),2);
            wind.destroyRandomBlocks(30);int hp=wind.getHealth();
            check(DreamWorldTreeService.start(peer.actor,wind,wind.tree().instance,WorldTreeModel.WIND_V4).equals("started"),"damaged legacy base explicit wind upgrade");complete(wind);complete(shadow);
            check(wind.model()==WorldTreeModel.WIND_V4&&shadow.model()==WorldTreeModel.SHADOW_V3,"independent completed branches");
            check(com.dreamseekers.progression.DreamWindProgression.server(owner).wind(),"original paid completion earns durable account Wind");
            check(!wind.ascended()&&shadow.ascended(),"wind cannot satisfy shadow ascendancy");
            check(Math.abs(hp-wind.getHealth())<=1&&wind.getBlocksPlaced()<377,"damage projects into real repair holes without healing");
            var shadowSnapshot=shadow.structureSnapshot();check(DreamAnchorPlacement.validSnapshot(shadowSnapshot),"new Shadow descriptor snapshot accepted");
            shadowSnapshot.putInt("highest",999);check(!DreamAnchorPlacement.validSnapshot(shadowSnapshot),"arbitrary count rejected");
            var windSaved=WorldTreeState.read(wind.tree().write());var savedData=new DataStorage();savedData.setData(DreamWorldTreeService.STATE,windSaved);
            var restoredWind=DreamseekersMod.DREAM_ANCHOR.createRestoredBuildingPlacement(level,wind.originPos,rotation,owner,savedData);
            restoredWind.isBuilt=true;restoredWind.partialBlocksDestroyed=wind.partialBlocksDestroyed;restoredWind.getDataStorage().setData(DreamWorldTreeService.STATE,windSaved);restoredWind.afterRestoredFromSave();
            check(Math.abs(restoredWind.getHealth()-wind.getHealth())<=1&&restoredWind.getBlocksTotal()==377,"damaged Wind reload preserves geometry and HP");
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
            BuildingServerEvents.getBuildings().remove(wind);wind.destroy(level);
            check(com.dreamseekers.progression.DreamWindProgression.server(owner).wind(),"paid Wind unlock survives building loss");
        }
        void close(){if(peer!=null)peer.close();BuildingServerEvents.getBuildings().removeAll(buildings);PlayerServerEvents.rtsPlayers.removeIf(p->owner.equals(p.name));ResourcesServerEvents.removeAccount(owner);for(long c:chunks){var p=new ChunkPos(c);level.setChunkForced(p.x,p.z,false);}}
    }
}
