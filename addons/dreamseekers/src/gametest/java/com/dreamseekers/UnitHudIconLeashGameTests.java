// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Every faction unit resolves its own HUD icon and cannot be lead-captured by a player. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnitHudIconLeashGameTests {
    private static final String BATCH = "dreamseekers_unit_hud_icon_leash";

    private static List<RegistryObject<? extends EntityType<? extends Entity>>> roster() {
        return List.of(
            DreamseekersMod.FIREFLY_WORKER, DreamseekersMod.STONE_SPIRIT_WORKER, DreamseekersMod.BEETLE,
            DreamseekersMod.BASALT_SNAIL, DreamseekersMod.PINK_CHICKEN_SCOUT, DreamseekersMod.GOLDEN_FOX_UNIT,
            DreamseekersMod.PINK_SLIME_UNIT, DreamseekersMod.JELLYFISH_UNIT, DreamseekersMod.BONE_WING_UNIT,
            DreamseekersMod.WIND_KNIGHT_UNIT, DreamseekersMod.STONE_SPIRIT_THROWER_UNIT, DreamseekersMod.BEETLE_MOTHER_UNIT,
            DreamseekersMod.SHADOW_GHOST_UNIT, DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT, DreamseekersMod.TERRORBEAK_UNIT,
            DreamseekersMod.FRIENDLY_GHOST_UNIT, DreamseekersMod.SHADOW_HAND_UNIT, DreamseekersMod.ASH_BONE_WING_UNIT,
            DreamseekersMod.SHADOW_GOLEM_UNIT, DreamseekersMod.WEEPING_SQUEAL_GHOST_UNIT,
            DreamseekersMod.AARONCOS_LEFT_HAND_UNIT, DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT,
            DreamseekersMod.CRAZY_TERRORBEAK_UNIT);
    }

    @GameTest(template = "dreamseekers_test_arena", batch = BATCH, timeoutTicks = 40)
    public static void everyUnitHasHudIconAndCannotBeLeashed(GameTestHelper h) {
        String name = "DreamUnitLeashProbe";
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        var roster = roster();
        h.assertTrue(roster.size() == 23, "roster covers all 23 faction units");
        for (var type : roster) {
            String id = type.getId().toString();
            Entity entity = type.get().create(h.getLevel());
            h.assertTrue(entity instanceof Unit && entity instanceof Mob, id + " is an RTS unit");
            h.assertTrue(((Unit) entity).getHudIcon() != null, id + " supplies its own HUD icon");
            h.assertTrue(!((Mob) entity).canBeLeashed(player), id + " cannot be leashed by a player");
            entity.discard();
        }
        h.succeed();
    }
}
