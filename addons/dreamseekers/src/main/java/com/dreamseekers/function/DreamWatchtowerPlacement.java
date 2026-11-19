// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.function;

import com.dreamseekers.building.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import java.util.*;

/** Versioned templates never replace old solid tower blocks on load. Unknown payload remains inert. */
public final class DreamWatchtowerPlacement extends DreamFunctionalPlacement {
    public static final DataType<CompoundTag> MODEL=DataType.createRegistered(
        ResourceLocation.fromNamespaceAndPath("dreamseekers","watchtower_model"),
        (tag,server)->tag.copy(),CompoundTag::copy,CompoundTag::new);
    private int revision;
    private UUID instance;
    private int savedHighest=2;
    private boolean supported=true;
    public static void bootstrap() {}
    public static int count(int revision){return revision==1?236:revision==2?211:0;}
    public static ArrayList<BuildingBlock> template(int revision) {
        if(revision!=1&&revision!=2)throw new IllegalArgumentException("Unknown watchtower model");
        var blocks=DreamBuildingSupport.load(revision==1?"shadow_watchtower":"shadow_watchtower_garrison_v2");
        if(blocks.stream().filter(b->!b.getBlockState().isAir()).count()!=count(revision)
                ||blocks.stream().map(BuildingBlock::getBlockPos).distinct().count()!=blocks.size()
                ||blocks.stream().anyMatch(b->{var pos=b.getBlockPos();return pos.getX()<0||pos.getX()>6||pos.getY()<0||pos.getY()>13||pos.getZ()<0||pos.getZ()>6;}))
            throw new IllegalStateException("Watchtower descriptor bounds/count mismatch");
        return blocks;
    }
    private static int restoredRevision(DataStorage saved) {
        var tag=saved.getData(MODEL);
        return tag.isEmpty()?1:validState(tag)?tag.getInt("modelRevision"):1;
    }
    public DreamWatchtowerPlacement(DreamWatchtower building,Level level,BlockPos origin,Rotation rotation,String owner) {
        super(building,level,origin,rotation,owner,BuildingUtils.getAbsoluteBlockData(template(2),level,origin,rotation));
        revision=2;instance=UUID.randomUUID();writeModel();
    }
    public DreamWatchtowerPlacement(DreamWatchtower building,Level level,BlockPos origin,Rotation rotation,String owner,DataStorage saved) {
        super(building,level,origin,rotation,owner,BuildingUtils.getAbsoluteBlockData(template(restoredRevision(saved)),level,origin,rotation));
        if(level.isClientSide())throw new IllegalArgumentException("Server restore only");
        // Native restore assigns the full saved DataStorage after construction.
        var tag=saved.getData(MODEL);
        if(tag.isEmpty()){revision=1;instance=UUID.randomUUID();savedHighest=2;}
        else if(validState(tag)){revision=tag.getInt("modelRevision");instance=tag.getUUID("instance");savedHighest=tag.getInt("highest");}
        else {supported=false;revision=1;instance=null;}
        super.getDataStorage().setData(MODEL,tag.copy());
    }
    private static boolean validState(CompoundTag tag) {
        int revision=tag.getInt("modelRevision"),highest=tag.getInt("highest");
        return tag.contains("schema",3)&&tag.getInt("schema")==1&&tag.contains("modelRevision",3)
            &&(revision==1||revision==2)&&tag.hasUUID("instance")&&tag.contains("highest",3)&&highest>=2&&highest<=count(revision);
    }
    public int modelRevision(){return supported?revision:-1;}
    @Override public boolean shouldBeDestroyed() {
        // A fallback descriptor cannot establish physical damage for an opaque saved model.
        if(!getLevel().isClientSide()&&!supported)return false;
        return super.shouldBeDestroyed();
    }
    @Override public void tick(Level level) {
        // Do not let ordinary workers rebuild a different fallback template on unknown saves.
        if(!getLevel().isClientSide()&&!supported)return;
        super.tick(level);
    }
    private void writeModel() {
        if(!supported||instance==null||getLevel().isClientSide())return;
        var tag=new CompoundTag();tag.putInt("schema",1);tag.putInt("modelRevision",revision);tag.putUUID("instance",instance);
        tag.putInt("highest",Math.max(2,Math.min(count(revision),getHighestBlockCountReached())));
        super.getDataStorage().setData(MODEL,tag);
    }
    @Override public DataStorage getDataStorage(){writeModel();return super.getDataStorage();}
    /** Requires the core post-isBuilt restore hook shared with branch model implementation. */
    @Override public void afterRestoredFromSave() {
        if(!supported)return;
        restoreGeometry(BuildingUtils.getAbsoluteBlockData(template(revision),getLevel(),originPos,rotation),
                isBuilt?count(revision):savedHighest,partialBlocksDestroyed);
        writeModel();
    }
    @Override public CompoundTag structureSnapshot() {
        if(getLevel().isClientSide()||!supported||instance==null)return new CompoundTag();
        var tag=new CompoundTag();tag.putString("kind","dream_watchtower");tag.putInt("modelRevision",revision);tag.putUUID("instance",instance);
        tag.putInt("highest",getHighestBlockCountReached());tag.putInt("placed",getBlocksPlaced());tag.putDouble("partial",partialBlocksDestroyed);return tag;
    }
    public static boolean validSnapshot(CompoundTag tag) {
        int revision=tag.getInt("modelRevision"),highest=tag.getInt("highest"),placed=tag.getInt("placed");
        return "dream_watchtower".equals(tag.getString("kind"))&&tag.contains("modelRevision",3)
            &&(revision==1||revision==2)&&tag.hasUUID("instance")&&tag.contains("highest",3)&&tag.contains("placed",3)
            &&highest>=2&&highest<=count(revision)&&placed>=0&&placed<=count(revision)
            &&tag.contains("partial",6)&&Double.isFinite(tag.getDouble("partial"))&&tag.getDouble("partial")>=0&&tag.getDouble("partial")<1;
    }
    @Override public boolean acceptsStructureSnapshot(CompoundTag tag){return validSnapshot(tag)&&instance!=null&&instance.equals(tag.getUUID("instance"));}
    @Override public void applyStructureSnapshot(CompoundTag tag,boolean initial) {
        if(!getLevel().isClientSide()||!validSnapshot(tag)||(!initial&&!acceptsStructureSnapshot(tag)))return;
        revision=tag.getInt("modelRevision");instance=tag.getUUID("instance");supported=true;
        restoreGeometry(BuildingUtils.getAbsoluteBlockData(template(revision),getLevel(),originPos,rotation),tag.getInt("highest"),tag.getDouble("partial"));
        setServerBlocksPlaced(tag.getInt("placed"));
    }
}
