// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.unit.TargetResourcesSave;
import com.solegendary.reignofnether.unit.TargetResourcesSaveData;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dreamseekers_passive_c")
@PrefixGameTestTemplate(false)
public final class GatherPersistenceRepairGameTests {
    @GameTest(templateNamespace = "dreamseekers_passive_c", template = "ore_mine_test_arena", batch = "repair_gather_persistence", timeoutTicks = 100)
    public static void pendingGatherOrderSurvivesSaveBeforeWorkerPublication(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String pendingWorker = java.util.UUID.randomUUID().toString();
        BlockPos oldTarget = helper.absolutePos(new BlockPos(2, 1, 2));
        var pending = new TargetResourcesSave();
        pending.unitUUID = pendingWorker;
        pending.gatherTarget = oldTarget;
        pending.targetResourceName = ResourceName.WOOD;
        synchronized (UnitServerEvents.savedTargetResources) {
            UnitServerEvents.savedTargetResources.add(pending);
        }
        try {
            UnitServerEvents.saveGatherTargets(level);
            TargetResourcesSaveData data = TargetResourcesSaveData.getInstance(level);
            pending.gatherTarget = oldTarget.east();
            var diskRoundTrip = TargetResourcesSaveData.load(data.save(new CompoundTag()));
            var matches = diskRoundTrip.targetData.stream().filter(s -> pendingWorker.equals(s.unitUUID)).toList();
            helper.assertTrue(matches.size() == 1
                            && oldTarget.equals(matches.get(0).gatherTarget)
                            && matches.get(0).targetResourceName == ResourceName.WOOD,
                    "saving before chunk publication retains an independent copy of the pending work order");
            UnitServerEvents.saveGatherTargets(level);
            diskRoundTrip = TargetResourcesSaveData.load(data.save(new CompoundTag()));
            matches = diskRoundTrip.targetData.stream().filter(s -> pendingWorker.equals(s.unitUUID)).toList();
            helper.assertTrue(matches.size() == 1 && oldTarget.east().equals(matches.get(0).gatherTarget),
                    "subsequent saves preserve the newest pending task exactly once");
        } finally {
            synchronized (UnitServerEvents.savedTargetResources) {
                UnitServerEvents.savedTargetResources.removeIf(s -> pendingWorker.equals(s.unitUUID));
            }
            UnitServerEvents.saveGatherTargets(level);
        }
        helper.succeed();
    }

}
