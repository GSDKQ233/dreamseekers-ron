package com.solegendary.reignofnether.unit;

import com.mojang.datafixers.util.Pair;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Own-unit gameplay commands through the real action/formation dispatcher.
 * Deterministic END-tick stepping, not client/network or physical-route acceptance. */
@GameTestHolder("api58_formation_commands")
@PrefixGameTestTemplate(false)
public final class FormationCommandGameTests {
    private static final String OWNER = "FormationCommandFixture";

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final List<VillagerUnit> units = new ArrayList<>();
        final List<Entity> extras = new ArrayList<>();
        final boolean previousRts = UnitServerEvents.rtsPathfinding;
        final BlockPos first, second;

        Fixture(GameTestHelper h) {
            this.h = h;
            first = h.absolutePos(new BlockPos(24, 2, 24));
            second = h.absolutePos(new BlockPos(8, 2, 25));
            UnitServerEvents.rtsPathfinding = false;
            try {
                for (int i = 0; i < 25; i++) {
                    VillagerUnit unit = EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());
                    h.assertTrue(unit != null, "native worker created");
                    unit.setOwnerName(OWNER);
                    unit.setNoAi(true);
                    BlockPos pos = h.absolutePos(new BlockPos(4 + i % 5, 2, 4 + i / 5));
                    h.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
                    unit.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                    units.add(unit);
                    h.assertTrue(h.getLevel().addFreshEntity(unit), "unit genuinely enters level");
                    h.assertTrue(h.getLevel().getEntity(unit.getId()) == unit, "same live lookup used by action dispatcher");
                }
            } catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        void groupMove(BlockPos target) {
            new UnitActionItem(OWNER, UnitAction.MOVE, -1,
                    units.stream().mapToInt(Entity::getId).toArray(), target, BlockPos.ZERO).action(h.getLevel());
        }

        void command(VillagerUnit unit, UnitAction action, BlockPos target, int targetId) {
            new UnitActionItem(OWNER, action, targetId, new int[]{unit.getId()}, target, BlockPos.ZERO).action(h.getLevel());
        }

        void tick() {
            UnitServerEvents.onFormationDispatchTick(new TickEvent.LevelTickEvent(
                    LogicalSide.SERVER, TickEvent.Phase.END, h.getLevel(), () -> true));
        }

        void drain() { for (int i = 0; i < 8; i++) tick(); }

        void commandsTick() {
            UnitServerEvents.onWorldTick(new TickEvent.LevelTickEvent(
                    LogicalSide.SERVER, TickEvent.Phase.END, h.getLevel(), () -> true));
        }

        void advanceWorkerIdleClock(VillagerUnit unit) {
            // stopGathering resets to the idle threshold, while isIdle requires
            // strictly greater. Advance the actual throttled goal, not private
            // state, so a synchronous dispatch test reaches ordinary idle state.
            for (int tick = 0; tick < 40 && !unit.getGatherResourceGoal().isIdle(); tick++)
                unit.getGatherResourceGoal().tick();
            h.assertTrue(unit.isIdle(), "native worker idle clock advanced before queued command scheduling");
        }

        void queued() {
            groupMove(first);
            h.assertTrue(units.stream().allMatch(u -> u.getMoveGoal().getMoveTarget() == null),
                    "25-unit vanilla MOVE is really delayed before the first END tick");
        }

        @Override public void close() {
            UnitServerEvents.getUnitActionSlowQueue().removeIf(action ->
                    java.util.Arrays.stream(action.getUnitIds()).anyMatch(id ->
                            units.stream().anyMatch(unit -> unit.getId() == id)));
            for (VillagerUnit unit : units) {
                UnitServerEvents.discardPendingFormationMove(unit);
                if (!unit.isRemoved()) unit.discard();
            }
            for (Entity entity : extras) if (!entity.isRemoved()) entity.discard();
            UnitServerEvents.rtsPathfinding = previousRts;
        }
    }

    @GameTest(templateNamespace="api58_formation_commands", template="ore_mine_test_arena", batch="formation_commands", timeoutTicks=100)
    public static void stopHoldAndAttackSupersedeOlderGroupMove(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.queued();
            VillagerUnit stopped = f.units.get(24), held = f.units.get(23), attacker = f.units.get(22);
            Zombie target = EntityType.ZOMBIE.create(h.getLevel());
            h.assertTrue(target != null, "ordinary combat target exists");
            target.setNoAi(true);
            target.moveTo(f.second.getX(), f.second.getY(), f.second.getZ());
            f.extras.add(target);
            h.assertTrue(h.getLevel().addFreshEntity(target), "combat target published");
            f.command(stopped, UnitAction.STOP, BlockPos.ZERO, -1);
            f.command(held, UnitAction.HOLD, BlockPos.ZERO, -1);
            f.command(attacker, UnitAction.ATTACK, BlockPos.ZERO, target.getId());
            f.drain();
            h.assertTrue(stopped.getMoveGoal().getMoveTarget() == null, "STOP is not resurrected by older formation");
            h.assertTrue(held.getHoldPosition() && held.getMoveGoal().getMoveTarget() == null, "HOLD stays stationary");
            h.assertTrue(attacker.getTargetGoal().getTarget() == target && attacker.getMoveGoal().getMoveTarget() == null,
                    "new ATTACK survives stale movement dispatch");
            h.assertTrue(f.units.get(0).getMoveGoal().getMoveTarget() != null, "unmodified group member still moves");
        }
        System.out.println("FORMATION_COMMAND_STOP_HOLD_ATTACK_PASS");
        h.succeed();
    }

    @GameTest(templateNamespace="api58_formation_commands", template="ore_mine_test_arena", batch="formation_commands", timeoutTicks=100)
    public static void smallMoveAndRedundantLatestMoveSupersedeOlderGroupMove(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.queued();
            VillagerUnit selected = f.units.get(24);
            f.command(selected, UnitAction.MOVE, f.second, -1);
            f.drain();
            h.assertTrue(f.second.equals(selected.getMoveGoal().getMoveTarget()), "new one-unit MOVE remains latest");

            // Exercise the redundant-target fast path with a still-pending older slot.
            UnitServerEvents.queueFormationMove(List.of(Pair.of(selected, f.first)));
            f.command(selected, UnitAction.MOVE, f.second, -1);
            f.drain();
            h.assertTrue(f.second.equals(selected.getMoveGoal().getMoveTarget()), "redundant newest MOVE also cancels old slot");
        }
        System.out.println("FORMATION_COMMAND_SMALL_REDUNDANT_MOVE_PASS");
        h.succeed();
    }

    @GameTest(templateNamespace="api58_formation_commands", template="ore_mine_test_arena", batch="formation_commands", timeoutTicks=100)
    public static void ownerTransferAndOwnerRoundTripInvalidatePendingMove(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.queued();
            VillagerUnit transferred = f.units.get(24), roundTrip = f.units.get(23);
            transferred.setOwnerName("FormationNewOwner");
            roundTrip.setOwnerName("FormationTemporaryOwner");
            roundTrip.setOwnerName(OWNER);
            f.drain();
            h.assertTrue(transferred.getMoveGoal().getMoveTarget() == null, "old owner cannot control transferred unit");
            h.assertTrue(roundTrip.getMoveGoal().getMoveTarget() == null, "owner A-to-B-to-A does not revive old command");
            h.assertTrue(f.units.get(0).getMoveGoal().getMoveTarget() != null, "unchanged owner positive control");
        }
        System.out.println("FORMATION_COMMAND_OWNER_TRANSFER_ABA_PASS");
        h.succeed();
    }

    @GameTest(templateNamespace="api58_formation_commands", template="ore_mine_test_arena", batch="formation_commands", timeoutTicks=100)
    public static void removedLifetimeCannotTargetReplacementWithSameIdentity(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.queued();
            VillagerUnit original = f.units.get(24);
            var uuid = original.getUUID();
            int id = original.getId();
            original.discard();
            VillagerUnit replacement = EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());
            h.assertTrue(replacement != null, "replacement created");
            replacement.setOwnerName(OWNER);
            replacement.setUUID(uuid);
            replacement.setId(id);
            replacement.setNoAi(true);
            replacement.moveTo(original.getX(), original.getY(), original.getZ());
            f.extras.add(replacement);
            h.assertTrue(h.getLevel().addFreshEntity(replacement) && h.getLevel().getEntity(id) == replacement,
                    "native publication resolves same ID/UUID to different lifetime");
            f.drain();
            h.assertTrue(original.getMoveGoal().getMoveTarget() == null, "removed body is untouched");
            h.assertTrue(replacement.getMoveGoal().getMoveTarget() == null, "replacement does not inherit old movement");
        }
        System.out.println("FORMATION_COMMAND_EXACT_LIFETIME_PASS");
        h.succeed();
    }

    @GameTest(templateNamespace="api58_formation_commands", template="ore_mine_test_arena", batch="formation_commands", timeoutTicks=100)
    public static void directAndPartialBuilderResetInvalidatePendingMove(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.queued();
            VillagerUnit reset = f.units.get(24), builder = f.units.get(23);
            Unit.resetBehaviours(reset);
            // This is the exact partial reset used by ordinary/custom assignment;
            // no packet or permission boundary is constructed in this regression.
            builder.resetBehaviours();
            WorkerUnit.resetBehavioursExceptExploreBuild(builder);
            f.drain();
            h.assertTrue(reset.getMoveGoal().getMoveTarget() == null, "direct reset supersedes formation");
            h.assertTrue(builder.getMoveGoal().getMoveTarget() == null, "builder assignment reset supersedes formation");
        }
        System.out.println("FORMATION_COMMAND_RESET_BUILDER_PASS");
        h.succeed();
    }

    @GameTest(templateNamespace="api58_formation_commands", template="ore_mine_test_arena", batch="formation_commands", timeoutTicks=100)
    public static void largeMoveReplacementPreservesFivePerTickBudget(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.queued();
            f.groupMove(f.second);
            f.tick();
            h.assertTrue(f.units.stream().filter(u -> u.getMoveGoal().getMoveTarget() != null).count() == 5,
                    "only five current slots dispatch in one END tick");
            f.drain();
            for (VillagerUnit unit : f.units) {
                BlockPos target = unit.getMoveGoal().getMoveTarget();
                h.assertTrue(target != null && target.distSqr(f.second) <= 18, "every unit uses newest formation destination");
            }
        }
        System.out.println("FORMATION_COMMAND_TIME_SLICE_REPLACEMENT_PASS");
        h.succeed();
    }

    @GameTest(templateNamespace="api58_formation_commands", template="ore_mine_test_arena", batch="formation_commands", timeoutTicks=100)
    public static void shiftLegWaitsForItsPendingFormationThenExecutesWhenIdle(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.queued();
            VillagerUnit selected = f.units.get(24);
            f.advanceWorkerIdleClock(selected);
            UnitServerEvents.addActionItem(OWNER, UnitAction.MOVE, -1, new int[]{selected.getId()},
                    f.second, BlockPos.ZERO, true);
            f.commandsTick();
            h.assertTrue(selected.getMoveGoal().getMoveTarget() == null,
                    "shift leg cannot jump ahead while initial formation slot is pending");
            h.assertTrue(UnitServerEvents.getUnitActionSlowQueue().stream().anyMatch(a -> a.getUnitIds()[0] == selected.getId()),
                    "shift command stays queued, not rejected or consumed");
            f.drain();
            BlockPos initial = selected.getMoveGoal().getMoveTarget();
            h.assertTrue(initial != null && initial.distSqr(f.first) <= 18, "initial formation dispatched first");
            f.commandsTick();
            h.assertTrue(initial.equals(selected.getMoveGoal().getMoveTarget()), "shift leg waits while first move is active");
            Unit.fullResetBehaviours(selected);
            f.advanceWorkerIdleClock(selected);
            f.commandsTick();
            h.assertTrue(f.second.equals(selected.getMoveGoal().getMoveTarget()), "shift leg executes after first work is idle");
        }
        System.out.println("FORMATION_COMMAND_SHIFT_ORDER_PASS");
        h.succeed();
    }

    @GameTest(templateNamespace="api58_formation_commands", template="ore_mine_test_arena", batch="formation_commands", timeoutTicks=100)
    public static void pendingImmediateStopWinsRegardlessOfListenerOrder(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.queued();
            VillagerUnit selected = f.units.get(0);
            UnitServerEvents.addActionItem(OWNER, UnitAction.STOP, -1, new int[]{selected.getId()},
                    BlockPos.ZERO, BlockPos.ZERO);
            f.tick(); // Formation listener happens before command listener in this interleaving.
            h.assertTrue(selected.getMoveGoal().getMoveTarget() == null, "pending immediate action prevents stale dispatch");
            f.commandsTick();
            f.drain();
            h.assertTrue(selected.getMoveGoal().getMoveTarget() == null, "new immediate STOP remains authoritative");
            h.assertTrue(f.units.get(24).getMoveGoal().getMoveTarget() != null, "other queued members are not lost");
        }
        System.out.println("FORMATION_COMMAND_IMMEDIATE_ORDER_PASS");
        h.succeed();
    }
}
