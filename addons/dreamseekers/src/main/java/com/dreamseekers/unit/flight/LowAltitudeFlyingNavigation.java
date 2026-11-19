package com.dreamseekers.unit.flight;

import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.Set;

/** Real vanilla flight paths, with a short vertical detour budget instead of combat flight's +10. */
public class LowAltitudeFlyingNavigation extends FlyingPathNavigation {
    public static final int VERTICAL_DETOUR = 3;
    private int routeCeiling = Integer.MAX_VALUE;

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
        // Never raise the budget from the current height on a retry: that
        // would ratchet a partial path up arbitrarily tall walls. Above the
        // destination band (e.g. loaded on a roof), allow only nonascending nodes until it can descend.
        routeCeiling = Math.min(ruleMax, targetY + VERTICAL_DETOUR);
        return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
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
