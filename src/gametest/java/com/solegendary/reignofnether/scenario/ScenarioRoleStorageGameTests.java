package com.solegendary.reignofnether.scenario;

import com.solegendary.reignofnether.faction.Factions;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("api65_editor_worlds") @PrefixGameTestTemplate(false)
public final class ScenarioRoleStorageGameTests {
    @GameTest(templateNamespace="api65_editor_worlds", template="ore_mine_test_arena", batch="world_role_storage")
    public static void reopenIndependentAEmptyBThenAB(GameTestHelper h) throws Exception {
        var fixer = h.getLevel().getServer().getFixerUpper();
        Path a = Files.createTempDirectory("scenario-test-A");
        Path b = Files.createTempDirectory("scenario-test-B");
        try {
            var storageA = new DimensionDataStorage(a.toFile(), fixer);
            var roleA = ScenarioRoleSaveData.fromStorage(storageA);
            var seatA = roleA.scenarioRoleSaves.get(3);
            seatA.name = "World A"; seatA.teamNumber = 2; seatA.isNpc = true;
            seatA.faction = Factions.MONSTERS; seatA.startingResources.wood = 65;
            roleA.setDirty(); storageA.save();

            var storageB = new DimensionDataStorage(b.toFile(), fixer);
            var roleB = ScenarioRoleSaveData.fromStorage(storageB);
            h.assertTrue(roleB.scenarioRoleSaves.size() == ScenarioRole.COUNT, "empty B has eight fresh roles");
            for (int i = 0; i < ScenarioRole.COUNT; i++) {
                var role = roleB.scenarioRoleSaves.get(i);
                h.assertTrue(role.index == i && role.name.equals("Player " + (i + 1)) && role.teamNumber == i + 1
                        && !role.isNpc && role.faction == Factions.NEUTRAL && role.startingResources.wood == 0,
                        "B default role " + i);
            }
            h.assertTrue(seatA != roleB.scenarioRoleSaves.get(3) &&
                    seatA.startingResources != roleB.scenarioRoleSaves.get(3).startingResources, "no shared mutable role");
            roleB.setDirty(); storageB.save();
            h.assertTrue(Files.isRegularFile(a.resolve("saved-scenario-data.dat")) &&
                    Files.isRegularFile(b.resolve("saved-scenario-data.dat")), "both independent stores actually wrote");
            var againA = ScenarioRoleSaveData.fromStorage(new DimensionDataStorage(a.toFile(), fixer)).scenarioRoleSaves.get(3);
            h.assertTrue(againA.name.equals("World A") && againA.teamNumber == 2 && againA.isNpc &&
                    againA.faction == Factions.MONSTERS && againA.startingResources.wood == 65, "reopened A kept its edit");
            var againB = ScenarioRoleSaveData.fromStorage(new DimensionDataStorage(b.toFile(), fixer)).scenarioRoleSaves.get(3);
            h.assertTrue(againB.name.equals("Player 4") && !againB.isNpc && againB.startingResources.wood == 0,
                    "reopened B remained its own default");

            var partial = new CompoundTag();
            var slots = new ListTag();
            var known = new CompoundTag(); known.putInt("index", 5); known.putString("name", "Fifth"); slots.add(known);
            var unused = new CompoundTag(); unused.putInt("index", 99); slots.add(unused);
            var duplicate = known.copy(); duplicate.putString("name", "Duplicate"); slots.add(duplicate);
            slots.add(new CompoundTag()); partial.put("scenarioRoles", slots);
            var merged = ScenarioRoleSaveData.load(partial).scenarioRoleSaves;
            h.assertTrue(merged.size() == 8 && merged.get(0).teamNumber == 1 &&
                    merged.get(5).name.equals("Fifth") && merged.get(5).teamNumber == 6,
                    "partial records overlay defaults by identity; incomplete, duplicate and out-of-range rows cannot displace other seats");
        } finally {
            for (Path dir : new Path[]{a, b}) {
                Files.deleteIfExists(dir.resolve("saved-scenario-data.dat"));
                Files.deleteIfExists(dir);
            }
        }
        h.succeed();
    }
}
