// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.dreamseekers.unit.ShadowGolemUnit;
import com.dreamseekers.unit.TeamSafeBuildingAttackGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

/** Width-2.2 vanilla waypoint completion may precede RoN's move-goal completion (MOVE or an unopposed ATTACK_MOVE leg).
 * Keep only the existing final safe node until the existing command arrival predicate is met.
 * No new target, coordinate shift, teleport, collision bypass, distance expansion or global rule.
 */
public final class ShadowGolemGroundNavigation extends GroundPathNavigation {
    private final ShadowGolemUnit golem;
    public ShadowGolemGroundNavigation(ShadowGolemUnit golem,Level level){super(golem,level);this.golem=golem;}
    @Override protected void followThePath(){
        Path before=path;
        boolean wasFollowing=before!=null&&!before.isDone();
        super.followThePath();
        // A stuck/STOP callback can clear or replace the path. Never resurrect an old reference.
        if(!wasFollowing||path!=before||!before.isDone()||!before.canReach()||before.getNodeCount()==0)return;
        BlockPos click=golem.getMoveGoal().getMoveTarget();
        if(click==null||golem.getHoldPosition()||golem.getFollowTarget()!=null||golem.getTarget()!=null
                ||((TeamSafeBuildingAttackGoal)golem.getAttackBuildingGoal()).getBuildingTarget()!=null)return;
        var end=before.getEndNode();
        // Only a genuinely reached ground-click column, not an adjusted, partial or blocked path.
        if(end==null||end.x!=click.getX()||end.z!=click.getZ()||end.y!=click.getY()+1)return;
        if(golem.getOnPos().distSqr(click)>golem.getMoveGoal().getMinDistToRecalculateSqr()
                ||golem.distanceToSqr(click.getCenter())>=4)
            before.setNextNodeIndex(before.getNodeCount()-1);
    }
}
