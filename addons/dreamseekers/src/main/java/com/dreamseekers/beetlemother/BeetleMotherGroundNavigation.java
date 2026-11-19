// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.beetlemother;
import com.dreamseekers.unit.BeetleMotherUnit;
import com.dreamseekers.unit.TeamSafeBuildingAttackGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

/** Width-two vanilla waypoint completion may precede RoN's move-goal completion (MOVE or an unopposed ATTACK_MOVE leg).
 * Keep only the existing final safe node until the existing command arrival predicate is met.
 * No new target, coordinate shift, teleport, collision bypass, distance expansion or global rule.
 */
public final class BeetleMotherGroundNavigation extends GroundPathNavigation {
    private final BeetleMotherUnit mother;
    public BeetleMotherGroundNavigation(BeetleMotherUnit mother,Level level){super(mother,level);this.mother=mother;}
    public void stopForNest(){super.stop();mother.getMoveControl().setWantedPosition(mother.getX(),mother.getY(),mother.getZ(),0);mother.setSpeed(0);mother.setZza(0);mother.setXxa(0);}
    @Override public boolean moveTo(Path path,double speed){return !com.dreamseekers.skill.MotherNestStance.active(mother)&&super.moveTo(path,speed);}
    @Override public boolean moveTo(net.minecraft.world.entity.Entity target,double speed){return !com.dreamseekers.skill.MotherNestStance.active(mother)&&super.moveTo(target,speed);}
    @Override public boolean moveTo(double x,double y,double z,double speed){return !com.dreamseekers.skill.MotherNestStance.active(mother)&&super.moveTo(x,y,z,speed);}
    @Override public void tick(){if(com.dreamseekers.skill.MotherNestStance.active(mother))stopForNest();else super.tick();}
    @Override protected void followThePath(){
        Path before=path;
        boolean wasFollowing=before!=null&&!before.isDone();
        super.followThePath();
        // A stuck/STOP callback can clear or replace the path. Never resurrect an old reference.
        if(!wasFollowing||path!=before||!before.isDone()||!before.canReach()||before.getNodeCount()==0)return;
        BlockPos click=mother.getMoveGoal().getMoveTarget();
        if(click==null||mother.getHoldPosition()||mother.getFollowTarget()!=null||mother.getTarget()!=null
                ||((TeamSafeBuildingAttackGoal)mother.getAttackBuildingGoal()).getBuildingTarget()!=null)return;
        var end=before.getEndNode();
        // Only a genuinely reached ground-click column, not an adjusted, partial or blocked path.
        if(end==null||end.x!=click.getX()||end.z!=click.getZ()||end.y!=click.getY()+1)return;
        if(mother.getOnPos().distSqr(click)>mother.getMoveGoal().getMinDistToRecalculateSqr()
                ||mother.distanceToSqr(click.getCenter())>=4)
            before.setNextNodeIndex(before.getNodeCount()-1);
    }
}
