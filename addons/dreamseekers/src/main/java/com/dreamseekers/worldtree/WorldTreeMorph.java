package com.dreamseekers.worldtree;
import com.solegendary.reignofnether.building.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.*;
/** Same-task conservative read-set and rollback. Never clears the entire bounding box. */
public final class WorldTreeMorph {
    public record Plan(String error,ArrayList<BuildingBlock> target,LinkedHashMap<BlockPos,BlockState> before,
                       LinkedHashMap<BlockPos,BlockState> after,double partial,ArrayList<BuildingBlock> oldGeometry,int oldHighest,double oldPartial) {public boolean valid(){return error==null;}}
    private static Plan fail(String reason){return new Plan(reason,null,null,null,0,null,0,0);}
    private WorldTreeMorph(){}
    /** Native neighbour updates change fence connections without changing ownership/material.
     * Never use BuildingBlock.isPlaced here: it accepts different blocks within broad classes.
     * Exact block identity and every non-connection property (including waterlogging) stay required.
     */
    private static boolean ownedStateMatches(BlockState expected,BlockState current) {
        if(expected.equals(current))return true;
        if(expected.getBlock()!=current.getBlock())return false;
        if(expected.getBlock() instanceof FenceBlock||expected.getBlock() instanceof IronBarsBlock){
            return current.setValue(FenceBlock.NORTH,expected.getValue(FenceBlock.NORTH))
                .setValue(FenceBlock.SOUTH,expected.getValue(FenceBlock.SOUTH))
                .setValue(FenceBlock.EAST,expected.getValue(FenceBlock.EAST))
                .setValue(FenceBlock.WEST,expected.getValue(FenceBlock.WEST)).equals(expected);
        }
        if(expected.getBlock() instanceof WallBlock){
            return current.setValue(WallBlock.NORTH_WALL,expected.getValue(WallBlock.NORTH_WALL))
                .setValue(WallBlock.SOUTH_WALL,expected.getValue(WallBlock.SOUTH_WALL))
                .setValue(WallBlock.EAST_WALL,expected.getValue(WallBlock.EAST_WALL))
                .setValue(WallBlock.WEST_WALL,expected.getValue(WallBlock.WEST_WALL))
                .setValue(WallBlock.UP,expected.getValue(WallBlock.UP)).equals(expected);
        }
        return false;
    }

    private static boolean hangingLamp(BuildingBlock block){
        var state=block.getBlockState();return state.is(Blocks.LANTERN)&&state.getValue(LanternBlock.HANGING);
    }
    /** A damaged target keeps a prefix of this order. Never keep a v5 hanging lantern while
     * removing the block above it: the native neighbour update would drop it, changing HP.
     * Historic paid morphs retain their original hole selection, including the position tie-break. */
    static Comparator<BuildingBlock> damageOrder(WorldTreeModel target){
        if(target==WorldTreeModel.SHADOW_V5||target==WorldTreeModel.WIND_V5){
            return Comparator.comparingInt((BuildingBlock b)->b.getBlockPos().getY()+(hangingLamp(b)?1:0))
                .thenComparingInt(b->hangingLamp(b)?1:0)
                .thenComparingLong(b->b.getBlockPos().asLong());
        }
        return Comparator.comparingInt((BuildingBlock b)->b.getBlockPos().getY()).thenComparingLong(b->b.getBlockPos().asLong());
    }

    public static Plan plan(DreamAnchorPlacement p) {return plan(p,p.tree().target==null?WorldTreeModel.CURRENT_SHADOW:p.tree().target);}
    public static Plan plan(DreamAnchorPlacement p,WorldTreeModel descriptor) {
        if(descriptor==null||descriptor.branch==WorldTreeModel.Branch.BASE)return fail("invalid_model");
        if(!(p.getLevel() instanceof ServerLevel l)||!l.getServer().isSameThread()||!p.isBuilt||p.getHealth()<=0||p.shouldBeDestroyed())return fail("invalid_host");
        var target=p.blocks(descriptor);var old=new HashMap<BlockPos,BlockState>();for(var b:p.getBlocks())old.put(b.getBlockPos(),b.getBlockState());
        var newMap=new HashMap<BlockPos,BlockState>();for(var b:target)newMap.put(b.getBlockPos(),b.getBlockState());
        var before=new LinkedHashMap<BlockPos,BlockState>();var after=new LinkedHashMap<BlockPos,BlockState>();
        int lowX=Integer.MAX_VALUE,lowY=lowX,lowZ=lowX,highX=Integer.MIN_VALUE,highY=highX,highZ=highX;
        for(var b:target){var at=b.getBlockPos();lowX=Math.min(lowX,at.getX());lowY=Math.min(lowY,at.getY());lowZ=Math.min(lowZ,at.getZ());highX=Math.max(highX,at.getX());highY=Math.max(highY,at.getY());highZ=Math.max(highZ,at.getZ());
            if(!l.isLoaded(at)||l.isOutsideBuildHeight(at)||!l.getWorldBorder().isWithinBounds(at))return fail("unloaded_or_border");
        }
        var bounds=new AABB(lowX,lowY,lowZ,highX+1,highY+1,highZ+1);
        for(var other:BuildingServerEvents.getBuildings())if(other!=p&&other.getLevel()==l&&bounds.intersects(new AABB(other.minCorner,other.maxCorner.offset(1,1,1))))return fail("other_building");
        for(var b:target){var at=b.getBlockPos();var desired=b.getBlockState();var current=l.getBlockState(at);before.put(at,current);
            if(l.getBlockEntity(at)!=null)return fail("block_entity");
            boolean owned=old.containsKey(at)&&!old.get(at).isAir()&&ownedStateMatches(old.get(at),current)&&p.claims(at);
            if(!desired.isAir()) {
                if(!current.isAir()&&!owned)return fail("foreign_block");
                if(!owned&&!desired.getCollisionShape(l,at).isEmpty()) {
                    for(var box:desired.getCollisionShape(l,at).toAabbs())if(!l.getEntities((net.minecraft.world.entity.Entity)null,box.move(at),e->!e.isRemoved()&&e.isPickable()).isEmpty())return fail("entity_collision");
                }
                after.put(at,desired);
            } else if(owned)after.put(at,Blocks.AIR.defaultBlockState());
        }
        // Preserve the documented three-wide entrance and access spine, not arbitrary template air.
        for(int x=4;x<=6;x++)for(int z=0;z<=5;z++)for(int y=1;y<=3;y++){
            var at=new BlockPos(x,y,z).rotate(p.rotation).offset(p.originPos).above();
            var current=l.getBlockState(at);var next=after.getOrDefault(at,current);
            if(!next.isAir())return fail("entry_obstructed");
        }
        var solids=new ArrayList<BuildingBlock>();for(var b:target)if(!b.getBlockState().isAir())solids.add(b);
        if(solids.size()!=descriptor.count)return fail("invalid_model");
        var damage=WorldTreeHealth.project(solids.size(),p.minFraction(),Math.min(p.getHealth(),p.getMaxHealth()),p.getMaxHealth());
        // Retain low structural/entrance blocks first; upper missing blocks remain in the actual repair template.
        solids.sort(damageOrder(descriptor));
        for(int i=damage.present();i<solids.size();i++)after.put(solids.get(i).getBlockPos(),Blocks.AIR.defaultBlockState());
        return new Plan(null,target,before,after,damage.partial(),new ArrayList<>(p.getBlocks()),p.getHighestBlockCountReached(),p.partialBlocksDestroyed);
    }
    public static void rollback(DreamAnchorPlacement p,Plan plan) {
        var l=(ServerLevel)p.getLevel();
        for(var e:plan.before.entrySet())if(plan.after.containsKey(e.getKey()))l.setBlock(e.getKey(),e.getValue(),2);
        p.commitGeometry(plan.oldGeometry,plan.oldHighest,plan.oldPartial);
    }
    public static boolean commit(DreamAnchorPlacement p,Plan plan) {
        if(!plan.valid()||!(p.getLevel() instanceof ServerLevel l)||!l.getServer().isSameThread())return false;
        for(var e:plan.before.entrySet())if(!l.isLoaded(e.getKey())||!l.getBlockState(e.getKey()).equals(e.getValue())||l.getBlockEntity(e.getKey())!=null)return false;
        var changed=new ArrayList<BlockPos>();
        try {
            for(var e:plan.after.entrySet())if(!l.getBlockState(e.getKey()).equals(e.getValue())) {
                if(!l.setBlock(e.getKey(),e.getValue(),2))throw new IllegalStateException("World write rejected");changed.add(e.getKey());
            }
            p.commitGeometry(plan.target,(int)plan.target.stream().filter(b->!b.getBlockState().isAir()).count(),plan.partial);return true;
        } catch(RuntimeException failure) {
            Collections.reverse(changed);for(var at:changed)l.setBlock(at,plan.before.get(at),2);
            p.commitGeometry(plan.oldGeometry,plan.oldHighest,plan.oldPartial);return false;
        }
    }
}
