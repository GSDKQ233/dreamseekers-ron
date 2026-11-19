// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.aaroncos;

import net.minecraft.world.entity.Mob;
import com.dreamseekers.unit.flight.LowAltitudeFlyingNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.stream.Collectors;

/** The 3.5-wide fly evaluator uses four-cell corner nodes; the RTS order uses a body-center cell.
 * Its integer node center lies two blocks from its corner. Shift center-cell requests by one
 * block to retain the existing .8-foot target tolerance (max horizontal offset sqrt(.5)).
 * Translate only requested targets to the evaluator's corner coordinates, retaining all original
 * collision-tested nodes and the ordinary MoveControl. Existing small flyers are unchanged.
 */
public final class AaroncosHandNavigation extends LowAltitudeFlyingNavigation {
    private final ControlledAaroncosHand hand;
    private boolean recomputingNativeTarget;
    public AaroncosHandNavigation(Mob mob,Level level){super(mob,level);this.hand=(ControlledAaroncosHand)mob;}
    private static BlockPos corner(BlockPos center){return center.offset(-1,0,-1);}
    @Override @Nullable protected Path createPath(Set<BlockPos> targets,int regionOffset,boolean offsetUpward,int accuracy,float range){
        // PathNavigation stores its target in native corner coordinates. Its synchronous re-entry
        // during recomputePath must not translate that stored value a second time.
        return super.createPath(recomputingNativeTarget?targets:targets.stream().map(AaroncosHandNavigation::corner).collect(Collectors.toSet()),
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
        BlockPos destination=hand.getMoveGoal().getMoveTarget();
        if(destination==null||hand.getHoldPosition()||mob.getTarget()!=null||!corner(destination).equals(before.getTarget()))return;
        var end=before.getEndNode();
        if(end==null||!end.asBlockPos().equals(corner(destination)))return;
        // Vanilla's broad width-based completion is too early for the existing .8-foot goal.
        // Retain only this last safe node: no changed coordinates, new shortcut or expanded threshold.
        if(!hand.getMoveGoal().isAtDestination())before.setNextNodeIndex(before.getNodeCount()-1);
    }
}
