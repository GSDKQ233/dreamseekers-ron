package com.dreamseekers.worldtree;
import com.dreamseekers.building.*;
import com.dreamseekers.production.DreamProductionPlacement;
import com.solegendary.reignofnether.building.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
/** The same placement survives its controlled morph; worker queue and pending rallies are not copied. */
public final class DreamAnchorPlacement extends DreamProductionPlacement {
    private UUID clientInstance;
    private WorldTreeModel clientModel=WorldTreeModel.BASE_V3;
    public DreamAnchorPlacement(Building b,Level l,BlockPos p,Rotation r,String owner,ArrayList<BuildingBlock> blocks,boolean capitol){super(b,l,p,r,owner,blocks,capitol);}
    public UUID clientInstance(){return clientInstance;}
    @Override public java.util.List<com.solegendary.reignofnether.hud.buttons.AbilityButton> getAbilityButtons(){
        var list=new java.util.ArrayList<>(super.getAbilityButtons());
        if(getLevel().isClientSide())list.addAll(WorldTreeClient.buttons(this));return list;
    }
    public WorldTreeState tree(){var state=getDataStorage().getData(DreamWorldTreeService.STATE);
        if(state.supported)state.geometryHighest=Math.max(state.geometryHighest,getHighestBlockCountReached());return state;}
    @Override public void afterRestoredFromSave(){
        var state=tree();if(!state.supported)return;
        // Old completed bases never shrink their HP denominator to the surviving block count.
        int highest=isBuilt?state.model.count:Math.max(2,state.geometryHighest);
        restoreGeometry(blocks(state.model),highest,partialBlocksDestroyed);state.geometryHighest=highest;
    }
    public WorldTreeModel model(){return getLevel().isClientSide()?clientModel:tree().model;}
    public boolean ascended(){return (getLevel().isClientSide()||tree().supported)&&model().branch==WorldTreeModel.Branch.SHADOW;}
    public ArrayList<BuildingBlock> blocks(WorldTreeModel model){return BuildingUtils.getAbsoluteBlockData(model.blocks(),getLevel(),originPos,rotation);}
    public ArrayList<BuildingBlock> upgradedBlocks(){return blocks(tree().target!=null?tree().target:model().branch!=WorldTreeModel.Branch.BASE?model():WorldTreeModel.SHADOW_V3);}
    public void commitGeometry(ArrayList<BuildingBlock> blocks,int highest,double partial){restoreGeometry(blocks,highest,partial);}
    public boolean claims(BlockPos pos){return placedBlockPosSet.contains(pos);}
    public double minFraction(){return minBlocksPercent;}
    @Override public void changeStructure(String name){
        // Old loader's upgradeLevel is only a compatibility hint; persisted descriptor is authority.
        // Never accept resource names from a change-structure packet.
        if(getLevel().isClientSide()||!tree().supported)return;
        if(model().branch!=WorldTreeModel.Branch.BASE)
            restoreGeometry(blocks(model()),model().count,partialBlocksDestroyed);
    }
    @Override public CompoundTag structureSnapshot(){
        if(getLevel().isClientSide()||!tree().supported)return new CompoundTag();
        var t=new CompoundTag();t.putString("kind","dream_world_tree_v2");t.putUUID("instance",tree().instance);
        model().write(t,"model");t.putInt("highest",getHighestBlockCountReached());
        t.putInt("placed",getBlocksPlaced());t.putDouble("partial",partialBlocksDestroyed);return t;
    }
    @Override public boolean acceptsStructureSnapshot(CompoundTag t){return validSnapshot(t)&&clientInstance!=null&&clientInstance.equals(t.getUUID("instance"));}
    public static boolean validSnapshot(CompoundTag t){
        var model=WorldTreeModel.read(t,"model");
        return model!=null&&"dream_world_tree_v2".equals(t.getString("kind"))&&t.hasUUID("instance")
            &&t.contains("highest",net.minecraft.nbt.Tag.TAG_INT)&&t.getInt("highest")>=2&&t.getInt("highest")<=model.count
            &&t.contains("placed",net.minecraft.nbt.Tag.TAG_INT)&&t.getInt("placed")>=0&&t.getInt("placed")<=t.getInt("highest")
            &&t.contains("partial",net.minecraft.nbt.Tag.TAG_DOUBLE)&&Double.isFinite(t.getDouble("partial"))&&t.getDouble("partial")>=0&&t.getDouble("partial")<1;
    }
    @Override public void applyStructureSnapshot(CompoundTag t,boolean initial){
        if(!getLevel().isClientSide()||!validSnapshot(t)||(!initial&&!acceptsStructureSnapshot(t)))return;
        clientInstance=t.getUUID("instance");var descriptor=WorldTreeModel.read(t,"model");
        if(initial||descriptor!=clientModel){clientModel=descriptor;restoreGeometry(blocks(descriptor),t.getInt("highest"),t.getDouble("partial"));}
        highestBlockCountReached=t.getInt("highest");partialBlocksDestroyed=t.getDouble("partial");setServerBlocksPlaced(t.getInt("placed"));
    }
    @Override public boolean shouldBeDestroyed(){
        // Unknown saved geometry is not evidence of physical damage. Core checks this
        // before tick(), so the unsupported tick guard alone cannot preserve the world.
        if(!getLevel().isClientSide()&&!tree().supported)return false;
        return super.shouldBeDestroyed();
    }
    @Override public void tick(Level l){if(!l.isClientSide()&&!tree().supported)return;super.tick(l);DreamWorldTreeService.tick(this,l);}
    @Override protected void beforeOwnerCapture(String oldOwner,String newOwner){DreamWorldTreeService.captured(this,(ServerLevel)getLevel());}
    @Override public void destroy(ServerLevel l){DreamWorldTreeService.destroyed(this,l);super.destroy(l);}
    private boolean refusePermanentAdministrativeRemoval(String reason){
        com.solegendary.reignofnether.ReignOfNether.LOGGER.warn(
            "Refused permanent administrative WorldTree removal at {} for owner {}: {}",originPos,ownerName,reason);
        return false;
    }
    @Override public boolean beforePermanentAdministrativeRemoval(){
        if(!(getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread())return refusePermanentAdministrativeRemoval("not_server_thread");
        var state=tree();
        if(!state.supported)return refusePermanentAdministrativeRemoval("unsupported_saved_economic_state");
        if(state.receipt==null)return true;
        DreamWorldTreeService.destroyed(this,level); // Disposition only: no geometry destruction or superclass destroy.
        var account=com.solegendary.reignofnether.resources.ExtendedResourcesSync.account(state.payer);
        if(account==null)return refusePermanentAdministrativeRemoval("original_payer_account_unavailable");
        if(!java.util.Objects.equals(state.payerEpoch,account.accountEpoch))return refusePermanentAdministrativeRemoval("original_payer_epoch_unavailable");
        if(!account.extendedStateSupported())return refusePermanentAdministrativeRemoval("unsupported_original_ledger");
        var receipt=account.paidReceipts.get(state.receipt);
        if(receipt==null)return true;
        if(state.target==null||!state.payerEpoch.equals(receipt.payerEpoch()))return refusePermanentAdministrativeRemoval("invalid_original_receipt_binding");
        var purpose=DreamWorldTreeService.purpose(state.target);
        if(receipt.purpose().equals(purpose+":refund")||receipt.purpose().equals(purpose+":forfeit"))return true;
        return refusePermanentAdministrativeRemoval("original_receipt_disposition_still_pending");
    }
}
