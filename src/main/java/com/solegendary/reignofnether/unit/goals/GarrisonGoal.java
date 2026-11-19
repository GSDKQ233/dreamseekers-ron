package com.solegendary.reignofnether.unit.goals;

import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.shared.MineWorkPosts;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public class GarrisonGoal extends MoveToTargetBlockGoal {

    private BuildingPlacement buildingTarget;
    private int minePost = -1;
    private long mineCaptureRevision;
    private String mineOwner = "";
    private boolean mineArrived;
    private long mineApproachStartTick;

    public int getMinePost() { return minePost; }
    public boolean hasMineAssignment() {
        return minePost >= 0 && minePost < MineWorkPosts.capacity(buildingTarget)
                && MineWorkPosts.isValidWorker(mob, buildingTarget)
                && mineOwner.equals(buildingTarget.ownerName)
                && mineCaptureRevision == buildingTarget.getCaptureRevision()
                && MineWorkPosts.isPostClear(buildingTarget, minePost)
                && (mineArrived || (mob.level().getGameTime() >= mineApproachStartTick
                && mob.level().getGameTime() - mineApproachStartTick < MineWorkPosts.MAX_APPROACH_TICKS));
    }
    public boolean isMineWorkerActive() {
        return isRunning && hasMineAssignment() && mineArrived && MineWorkPosts.atPost(mob, buildingTarget, minePost)
                && mob.getNavigation().isDone();
    }

    private void arriveAtMinePost() {
        // Cancel outstanding async path callbacks, without discarding the work order.
        super.stopMoving();
        this.moveTarget = MineWorkPosts.getPost(buildingTarget, minePost);
        mineArrived = true;
    }

    @Override
    public boolean canUse() {
        if (minePost >= 0 && !hasMineAssignment()) {
            stopGarrisoning();
            return false;
        }
        return super.canUse();
    }

    @Override
    public void start() {
        if (minePost >= 0 && mineArrived && hasMineAssignment()
                && MineWorkPosts.atPost(mob, buildingTarget, minePost)) {
            isRunning = true;
            return;
        }
        super.start();
    }

    @Override
    public double getMinDistToRecalculateSqr() {
        // Military movement keeps its ordinary 1.5-block settle radius. Work
        // posts require exact arrival and must retry if a partial path ends short.
        return minePost >= 0 ? 0 : super.getMinDistToRecalculateSqr();
    }

    @Override
    public boolean canContinueToUse() {
        // Keep a real work order alive after navigation finishes, without re-pathing
        // or converting the worker into a military garrison occupant.
        if (minePost >= 0) {
            if (!hasMineAssignment() || (mineArrived && !MineWorkPosts.atPost(mob, buildingTarget, minePost))) {
                stopGarrisoning();
                return false;
            }
            if (MineWorkPosts.atPost(mob, buildingTarget, minePost)) {
                if (!mineArrived) arriveAtMinePost();
                return true;
            }
        }
        return super.canContinueToUse();
    }

    public GarrisonGoal(Mob mob) {
        super(mob, true, 0);
    }

    public void tick() {
        if (buildingTarget != null && buildingTarget.getLevel() != mob.level()) {
            stopGarrisoning();
            return;
        }
        if (minePost >= 0) {
            if (!hasMineAssignment() || (mineArrived && !MineWorkPosts.atPost(mob, buildingTarget, minePost))) {
                stopGarrisoning();
                return;
            }
            if (MineWorkPosts.atPost(mob, buildingTarget, minePost)) {
                if (!mineArrived) arriveAtMinePost();
            }
            return;
        }
        if (buildingTarget == null) return;
        GarrisonableBuildingAddon garr;
        if ((garr = buildingTarget.getBuilding().getActiveAddon(GarrisonableBuildingAddon.class)) != null) {
            calcMoveTarget();
            if (buildingTarget.getBlocksPlaced() <= 0) {
                stopGarrisoning();
            }
            if (moveTarget != null && this.mob.distanceToSqr(new Vec3(moveTarget.getX() + 0.5f,
                moveTarget.getY() + 0.5f,
                moveTarget.getZ() + 0.5f
            )) <= 3f) {

                // teleport to garrison entry pos
                if (!garr.isFull(buildingTarget) && buildingTarget.isBuilt) {
                    BlockPos bp = garr.getEntryPosition(buildingTarget, this.mob);
                    if (bp != null) this.mob.teleportTo(bp.getX() + 0.5f, bp.getY() + 0.5f, bp.getZ() + 0.5f);
                }
                this.stopGarrisoning();
            }
        } else {
            this.moveTarget = null;
        }
    }

    private void calcMoveTarget() {
        if (buildingTarget.getBuilding().hasActiveAddon(GarrisonableBuildingAddon.class)) {
            this.moveTarget = this.buildingTarget.getClosestGroundPos(mob.getOnPos(), 1);
        }
    }

    public void setBuildingTarget(BlockPos blockPos) {
        if (blockPos != null) {
            boolean clientside = this.mob.level().isClientSide();
            BuildingPlacement building = BuildingUtils.findBuilding(this.mob.level(), blockPos);

            if (building == null)
                return;

            if (MineWorkPosts.isMine(building)) {
                stopGarrisoning();
                if (clientside) {
                    // The local goal does not run server navigation and cannot know reservations.
                    // Keep only a visible command marker; the authoritative snapshot owns HUD occupancy.
                    this.buildingTarget = building;
                    MiscUtil.addUnitCheckpoint((Unit) mob, building.originPos.above(), true);
                    return;
                }
                int post = MineWorkPosts.findFreePost(mob, building);
                if (post < 0) {
                    var owner = mob.getServer().getPlayerList().getPlayerByName(((Unit) mob).getOwnerName());
                    if (owner != null && owner.level() == mob.level())
                        owner.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                "hud.reignofnether.mine_workpost_unavailable"), true);
                    return;
                }
                this.buildingTarget = building;
                this.minePost = post;
                this.mineOwner = building.ownerName;
                this.mineCaptureRevision = building.getCaptureRevision();
                this.mineApproachStartTick = mob.level().getGameTime();
                this.setMoveTarget(MineWorkPosts.getPost(building, post));
                return;
            }

            boolean isAllied;
            if (clientside)
                isAllied = AlliancesClient.isAllied(building.ownerName, ((Unit) mob).getOwnerName());
            else
                isAllied = AlliancesServerEvents.isAllied(building.ownerName, ((Unit) mob).getOwnerName());

            GarrisonableBuildingAddon garr;
            if ((garr = building.getBuilding().getActiveAddon(GarrisonableBuildingAddon.class)) == null ||
                (!building.ownerName.equals(((Unit) mob).getOwnerName()) && !isAllied)) {
                if (clientside)
                    HudClientEvents.showTemporaryMessage(I18n.get("hud.reignofnether.not_garrisonable"));
            }
            else if (garr.isFull(building)) {
                if (clientside)
                    HudClientEvents.showTemporaryMessage(I18n.get("hud.reignofnether.building_full"));
            } else {
                if (clientside) {
                    MiscUtil.addUnitCheckpoint(((Unit) mob), new BlockPos(
                            building.centrePos.getX(),
                            building.originPos.getY() + 1,
                            building.centrePos.getZ()
                    ), true);
                }
                this.buildingTarget = building;
                calcMoveTarget();
                this.start();
            }
        }
    }

    public BuildingPlacement getBuildingTarget() {
        return buildingTarget;
    }

    // if we override stop() it for some reason is called after start() and we can never begin this goal...
    public void stopGarrisoning() {
        buildingTarget = null;
        minePost = -1;
        mineOwner = "";
        mineArrived = false;
        super.stopMoving();
    }
}
