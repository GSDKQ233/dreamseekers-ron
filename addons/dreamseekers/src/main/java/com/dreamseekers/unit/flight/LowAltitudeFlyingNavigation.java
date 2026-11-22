package com.dreamseekers.unit.flight;

import com.dreamseekers.skill.SkillUnit;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.Set;

/** Real vanilla flight paths, with a short vertical detour budget instead of combat flight's +10. */
public class LowAltitudeFlyingNavigation extends FlyingPathNavigation {
    public static final int VERTICAL_DETOUR = 3;
    private int routeCeiling = Integer.MAX_VALUE;
    private record Departure(long revision, Object intent, int surfaceY) { }
    @Nullable private Departure moveDeparture, pursuitDeparture;

    public LowAltitudeFlyingNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanOpenDoors(false);
        setCanPassDoors(true);
        setCanFloat(false);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new FlyNodeEvaluator() {
            @Override public int getNeighbors(Node[] nodes, Node node) {
                int count = super.getNeighbors(nodes, node);
                int accepted = 0;
                for (int i = 0; i < count; i++)
                    if (nodes[i].y <= routeCeiling || node.y > routeCeiling && nodes[i].y <= node.y) nodes[accepted++] = nodes[i];
                return accepted;
            }
        };
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    // Every vanilla entry point (BlockPos, coordinates, entity pursuit) eventually
    // passes here. Bound *path nodes*, not only the final target's display height.
    @Override
    @Nullable
    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward,
                              int accuracy, float followRange) {
        int targetY = targets.stream().mapToInt(BlockPos::getY).max().orElse(mob.getBlockY());
        int ruleMax = level.getGameRules().getInt(GameRuleRegistrar.FLYING_MAX_Y_LEVEL);
        // A downhill order can require a short ascent over its launch terrace's lip.
        // Once down in the valley a repath need not regain the old terrace's height.
        int launchY = Math.min(orderDepartureSurface(), mob.getBlockY());
        routeCeiling = Math.min(ruleMax, Math.max(targetY, launchY) + VERTICAL_DETOUR);
        return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
    }

    private int orderDepartureSurface() {
        if (!(mob instanceof SkillUnit unit)) return Integer.MIN_VALUE;
        long revision = unit.rts().commandRevision;
        var pursued = mob.getTarget();
        if (pursued == null) pursued = unit.getFollowTarget();
        if (pursued != null) {
            // Use entity identity, not its moving block position. Keep separate from
            // the move order so losing/reacquiring the same enemy cannot add headroom.
            pursuitDeparture = departure(pursuitDeparture, revision, pursued.getUUID());
            return pursuitDeparture.surfaceY();
        }
        if (unit.getMoveGoal() instanceof LowAltitudeMoveGoal move && move.orderedTarget() != null) {
            moveDeparture = departure(moveDeparture, revision, move.orderedTarget());
            return moveDeparture.surfaceY();
        }
        // Independent WORK/RETURN/BUILD endpoint probes keep their original budget.
        return Integer.MIN_VALUE;
    }

    private Departure departure(@Nullable Departure saved, long revision, Object intent) {
        if (saved != null && saved.revision() == revision && saved.intent().equals(intent)) return saved;
        Vec3 feet = mob.position();
        var support = level.clip(new ClipContext(feet.add(0, .001, 0), feet.add(0, -VERTICAL_DETOUR - .001, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mob));
        // The destination resolver also treats the water surface as a floor. A
        // midair partial path, with no surface below it, grants no new launch bonus.
        int y = support.getType() == HitResult.Type.BLOCK && support.getDirection() == Direction.UP
                ? Mth.ceil(support.getLocation().y - .001) : Integer.MIN_VALUE;
        return new Departure(revision, intent, y);
    }

    @Override
    protected boolean canMoveDirectly(Vec3 from, Vec3 to) {
        // The native node evaluator handles block collision. Also prevent shortcut
        // smoothing from selecting a center-line ray whose body clips a corner.
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 4));
        for (int i = 1; i <= steps; i++) {
            Vec3 feet = from.lerp(to, (double) i / steps);
            if (!FlightWorkPositions.isClear(mob, feet)) return false;
        }
        return true;
    }

    /** Only the skill's exact, still-owned work path may discard its pending native block-update repath. */
    public void stopWorkForRush(Path expectedPath) {
        if(expectedPath!=null&&getPath()==expectedPath) {
            hasDelayedRecomputation=false;
            stop();
        }
    }

    @Override public void recomputePath() {
        Object work=com.dreamseekers.skill.FireflyRush.beforeRecompute(mob);
        super.recomputePath();
        com.dreamseekers.skill.FireflyRush.afterRecompute(mob,work);
    }

    @Override
    public void stop() {
        super.stop();
        mob.getMoveControl().operation = MoveControl.Operation.WAIT;
        mob.setDeltaMovement(Vec3.ZERO);
    }
}
