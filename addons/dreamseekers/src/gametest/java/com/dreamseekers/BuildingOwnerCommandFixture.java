package com.dreamseekers;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.solegendary.reignofnether.building.BuildingPlacement;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/** Dispatch the public command, rather than bypassing its callback with a setter. */
final class BuildingOwnerCommandFixture {
    private BuildingOwnerCommandFixture() {}

    static int execute(GameTestHelper h, BuildingPlacement building, String arguments) {
        var source = h.getLevel().getServer().createCommandSourceStack()
            .withLevel(h.getLevel()).withPosition(Vec3.atCenterOf(building.centrePos))
            .withPermission(2).withSuppressedOutput();
        try {
            return h.getLevel().getServer().getCommands().getDispatcher().execute(
                "rtsapi player owner building @s" + arguments, source);
        } catch (CommandSyntaxException e) {
            throw new AssertionError("Building ownership command failed", e);
        }
    }

    static int transfer(GameTestHelper h, BuildingPlacement building, String oldOwner, String newOwner) {
        return execute(h, building, " \"" + oldOwner + "\" \"" + newOwner + "\"");
    }
}
