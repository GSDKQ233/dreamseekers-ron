package com.dreamseekers.worldtree;

import com.dreamseekers.building.DreamBuildingSupport;
import com.solegendary.reignofnether.building.BuildingBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;

/** Closed, immutable server/client descriptor set. No wire/save string is a resource path. */
public enum WorldTreeModel {
    LEGACY_BASE(Branch.BASE, 1, "dream_anchor", 324, 11, 10, 9),
    LEGACY_SHADOW(Branch.SHADOW, 1, "dream_anchor_ascended", 451, 11, 16, 9),
    BASE_V3(Branch.BASE, 3, "dream_anchor_base_v3", 325, 11, 11, 9),
    SHADOW_V3(Branch.SHADOW, 3, "dream_anchor_shadow_v3", 452, 11, 17, 9),
    WIND_V4(Branch.WIND, 4, "dream_anchor_wind_v4", 377, 11, 18, 9),
    // Snapshots encode enum ordinals; append revisions and never repurpose a saved identity.
    BASE_V5(Branch.BASE, 5, "dream_anchor_base_v5", 376, 11, 11, 9),
    SHADOW_V5(Branch.SHADOW, 5, "dream_anchor_shadow_v5", 351, 11, 17, 9),
    WIND_V5(Branch.WIND, 5, "dream_anchor_wind_v5", 393, 11, 18, 9);

    public static final WorldTreeModel CURRENT_BASE=BASE_V5, CURRENT_SHADOW=SHADOW_V5, CURRENT_WIND=WIND_V5;
    public enum Branch { BASE, SHADOW, WIND }
    public final Branch branch;
    public final int revision, count, width, height, depth;
    public final String structure;
    WorldTreeModel(Branch branch,int revision,String structure,int count,int width,int height,int depth){
        this.branch=branch;this.revision=revision;this.structure=structure;this.count=count;this.width=width;this.height=height;this.depth=depth;
    }
    public static WorldTreeModel known(String branch,int revision){
        for(var model:values())if(model.branch.name().equals(branch)&&model.revision==revision)return model;
        return null;
    }
    public static WorldTreeModel read(CompoundTag tag,String key){
        if(!tag.contains(key,Tag.TAG_COMPOUND))return null;
        var d=tag.getCompound(key);
        return d.contains("branch",Tag.TAG_STRING)&&d.contains("revision",Tag.TAG_INT)?known(d.getString("branch"),d.getInt("revision")):null;
    }
    public void write(CompoundTag tag,String key){var d=new CompoundTag();d.putString("branch",branch.name());d.putInt("revision",revision);tag.put(key,d);}
    public static boolean newTarget(WorldTreeModel model){return model==CURRENT_SHADOW||model==CURRENT_WIND;}
    /** A saved paid operation finishes with the exact model revision written when it was bought. */
    public static boolean supportsSavedTarget(WorldTreeModel source,WorldTreeModel target){
        if(source==null||source.branch!=Branch.BASE)return false;
        if(newTarget(target))return true;
        if(source==LEGACY_BASE&&target==LEGACY_SHADOW)return true;
        return (source==LEGACY_BASE||source==BASE_V3)&&(target==SHADOW_V3||target==WIND_V4);
    }
    public ArrayList<BuildingBlock> blocks(){
        var blocks=DreamBuildingSupport.load(structure);
        int solids=0,minX=Integer.MAX_VALUE,minY=minX,minZ=minX,maxX=Integer.MIN_VALUE,maxY=maxX,maxZ=maxX;
        var seen=new java.util.HashSet<net.minecraft.core.BlockPos>();
        for(var b:blocks){var p=b.getBlockPos();if(!seen.add(p))throw new IllegalStateException("Duplicate model position: "+name());
            minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());
            maxX=Math.max(maxX,p.getX());maxY=Math.max(maxY,p.getY());maxZ=Math.max(maxZ,p.getZ());if(!b.getBlockState().isAir())solids++;
        }
        if(solids!=count||maxX-minX+1!=width||maxY-minY+1!=height||maxZ-minZ+1!=depth)throw new IllegalStateException("Untrusted model geometry: "+name());
        return blocks;
    }
}
