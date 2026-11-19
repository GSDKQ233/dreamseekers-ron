// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;

import com.dreamseekers.unit.AshBoneWingUnit;
import com.dreamseekers.unit.flight.LowAltitudeFlyingNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.stream.Collectors;

/** The 2.55-wide fly evaluator uses three-cell corner nodes; the RTS order uses a body-center cell.
 * Translate only requested targets to the evaluator's corner coordinates, retaining all original
 * collision-tested nodes and the ordinary MoveControl. Existing small flyers are unchanged.
 */
public final class AshBoneWingNavigation extends LowAltitudeFlyingNavigation {
    private final AshBoneWingUnit ash;
    private boolean recomputingNativeTarget;
    public AshBoneWingNavigation(AshBoneWingUnit ash,Level level){super(ash,level);this.ash=ash;}
    private static BlockPos corner(BlockPos center){return center.offset(-1,0,-1);}
    @Override @Nullable protected Path createPath(Set<BlockPos> targets,int regionOffset,boolean offsetUpward,int accuracy,float range){
        // PathNavigation stores its target in native corner coordinates. Its synchronous re-entry
        // during recomputePath must not translate that stored value a second time.
        return super.createPath(recomputingNativeTarget?targets:targets.stream().map(AshBoneWingNavigation::corner).collect(Collectors.toSet()),
                regionOffset,offsetUpward,accuracy,range);
    }
    @Override public void recomputePath(){
        boolean previous=recomputingNativeTarget;recomputingNativeTarget=true;
        try{super.recomputePath();}finally{recomputingNativeTarget=previous;}
    }
    @Override protected void followThePath(){
        Path before=path;boolean following=before!=null&&!before.isDone();
        super.followThePath();
        // A normal STOP/stuck callback may discard/replace the path; never resurrect that reference.
        if(!following||path!=before||!before.isDone()||!before.canReach()||before.getNodeCount()==0)return;
        BlockPos destination=ash.getMoveGoal().getMoveTarget();
        if(destination==null||ash.getHoldPosition()||ash.getTarget()!=null||!corner(destination).equals(before.getTarget()))return;
        var end=before.getEndNode();
        if(end==null||!end.asBlockPos().equals(corner(destination)))return;
        // Vanilla's broad width-based completion is too early for the existing .8-foot goal.
        // Retain only this last safe node: no changed coordinates, new shortcut or expanded threshold.
        if(!ash.getMoveGoal().isAtDestination())before.setNextNodeIndex(before.getNodeCount()-1);
    }
}
