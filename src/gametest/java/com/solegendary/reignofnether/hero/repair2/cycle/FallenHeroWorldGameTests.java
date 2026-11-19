package com.solegendary.reignofnether.hero.repair2.cycle;

import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.items.UnitInventory;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.HeroUnitSave;
import com.solegendary.reignofnether.unit.HeroUnitSaveData;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(HeroWorldCycle.SUITE)
@PrefixGameTestTemplate(false)
public final class FallenHeroWorldGameTests {
    private static final String OWNER = "Repair2_Hero";
    private static final String REJECT_MISSING = "hud.hero.reignofnether.error.no_dead_hero";
    private static String heroId;
    private static CompoundTag claim;
    private static CompoundTag heldItem;

    /** No reset, fake lifecycle events, mocked server or manual readback into the active list. */
    @GameTest(templateNamespace = HeroWorldCycle.SUITE, template = "ore_mine_test_arena",
            batch = "fallen_hero_world", timeoutTicks = 180)
    public static void sameProcessFallenHeroRoster(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        HeroWorldCycle.started(level.getServer());
        var data = HeroUnitSaveData.getInstance(level);
        h.assertTrue(data.heroUnits == HeroServerEvents.fallenHeroes, "Lookups and persistence use the current world's one roster");
        if (HeroWorldCycle.phase == 0) {
            checkLegacyRows(h);
            h.assertTrue(data.heroUnits.isEmpty(), "A starts empty");
            var hero = EntityRegistrar.ROYAL_GUARD_UNIT.get().create(level);
            h.assertTrue(hero != null, "Actual Royal Guard unit type");
            var pos = h.absolutePos(new BlockPos(2, 2, 2));
            hero.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            hero.setOwnerName(OWNER);
            hero.setNoAi(true);
            h.assertTrue(level.addFreshEntity(hero), "Published original hero");
            hero.setExperience(4850);
            hero.setSkillPoints(2);
            int[] ranks = {2, 1, 3, 1};
            for (int i = 0; i < ranks.length; i++) hero.getHeroAbilities().get(i).setRank(hero, ranks[i]);
            heroId = hero.getStringUUID();
            claim = new CompoundTag();
            claim.putInt("version", 1);
            claim.putUUID("origin", hero.getUUID());
            CompoundTag receipt = new CompoundTag();
            receipt.putUUID("id", UUID.randomUUID());
            receipt.putUUID("accountEpoch", UUID.randomUUID());
            receipt.putString("owner", "earlierOpponent");
            receipt.putString("outcome", "paid");
            claim.put("claim", receipt);
            // Known prior claim, not a newly minted reward. The separate kill_restart suite
            // covers earning an actual confirmed payout and paying for native revival.
            RtsKillValueClaims.restoreValue(hero, claim);
            var spyglass = new ItemStack(Items.SPYGLASS);
            ((UnitInventory) hero).set(2, spyglass);
            heldItem = spyglass.save(new CompoundTag());
            hero.hurt(level.damageSources().genericKill(), 10000);
            h.assertTrue(hero.isDeadOrDying(), "Normal final death of the published hero");
            h.startSequence().thenWaitUntil(() ->
                    h.assertTrue(HeroUnit.getHeroes(false, OWNER).isEmpty(), "Wait for the corpse's normal removal"))
                    .thenExecute(() -> {
                        HeroUnitSave record = requireOne(h);
                        checkFields(h, record);
                        // A historically serialized charge field with no live-body setter for this
                        // hero. Do not call save/setDirty: prove the normal stop really flushes it.
                        record.charges = 13;
                        HeroWorldCycle.expectedRoster = data.save(new CompoundTag());
                        assertRevival(h, true);
                        HeroWorldCycle.tested = true;
                    }).thenSucceed();
        } else if (HeroWorldCycle.phase == 2) {
            HeroUnitSave record = requireOne(h);
            checkFields(h, record);
            h.assertTrue(record.charges == 13 && data.save(new CompoundTag()).equals(HeroWorldCycle.expectedRoster),
                    "Reopened A preserved every saved row field and inventory slot");
            assertRevival(h, true);
            HeroWorldCycle.tested = true;
            h.succeed();
        } else {
            h.assertTrue(data.heroUnits.isEmpty() && HeroServerEvents.fallenHeroes.isEmpty()
                    && HeroUnit.getNumHeroesOwnedOrInTraining(false, OWNER) == 0
                    && HeroUnit.getFallenHero(false, OWNER, type()) == null, "B has zero, including counts and lookup");
            assertRevival(h, false);
            HeroWorldCycle.tested = true;
            h.succeed();
        }
    }

    private static String type() { return EntityRegistrar.ROYAL_GUARD_UNIT.get().getDescriptionId(); }

    private static HeroUnitSave requireOne(GameTestHelper h) {
        HeroUnitSave record = HeroUnit.getFallenHero(false, OWNER, type());
        h.assertTrue(HeroServerEvents.fallenHeroes.size() == 1 && HeroUnit.getFallenHeroes(false, OWNER).size() == 1
                && HeroUnit.getNumHeroesOwnedOrInTraining(false, OWNER) == 1
                && record == HeroServerEvents.fallenHeroes.get(0), "Exactly one hero counted and selected in A");
        return record;
    }

    private static void checkFields(GameTestHelper h, HeroUnitSave r) {
        h.assertTrue(r != null && heroId.equals(r.uuid) && OWNER.equals(r.ownerName) && type().equals(r.name)
                        && r.experience == 4850 && r.skillPoints == 2 && r.ability1Rank == 2 && r.ability2Rank == 1
                        && r.ability3Rank == 3 && r.ability4Rank == 1 && claim.equals(r.economicValue)
                        && r.items.size() == UnitInventory.MAX_INVENTORY_SIZE && heldItem.equals(r.items.get(2).save(new CompoundTag())),
                "Exact UUID, name/owner, XP, skill points, ranks, item with its UUID, and full claim");
    }

    private static void assertRevival(GameTestHelper h, boolean allowed) {
        var shrine = (ProductionPlacement) BuildingUtils.getNewBuildingPlacement(Buildings.SHRINE_OF_PROSPERITY,
                h.getLevel(), h.absolutePos(new BlockPos(2, 1, 2)), Rotation.NONE, OWNER, false);
        String error = ProductionItems.ROYAL_GUARD_REVIVE.getProduceErrorMsg(shrine);
        h.assertTrue(allowed ? error == null : REJECT_MISSING.equals(error), "Native revival decision: " + error);
    }

    private static void checkLegacyRows(GameTestHelper h) {
        var original = new CompoundTag();
        var rows = new ListTag();
        var old = new CompoundTag();
        old.putString("uuid", UUID.randomUUID().toString());
        old.putString("name", "entity.oldermod.renamed_guard");
        old.putString("ownerName", "旧玩家");
        old.putInt("experience", 321);
        old.putInt("skillPoints", 2);
        old.putInt("charges", 11);
        old.putInt("ability1Rank", 1);
        old.putInt("ability2Rank", 2);
        old.putInt("ability3Rank", 0);
        old.putInt("ability4Rank", 1);
        var items = new ListTag();
        for (int i = 0; i < UnitInventory.MAX_INVENTORY_SIZE; i++) items.add(new CompoundTag());
        old.put("items", items);
        rows.add(old);
        rows.add(old.copy()); // Existing historical duplicate: do not guess which row to delete.
        var different = old.copy();
        different.putString("uuid", UUID.randomUUID().toString());
        different.putInt("experience", 777);
        var opaque = new CompoundTag();
        opaque.putString("unrecognized", "leave-me-intact");
        different.put("economicValue", opaque);
        rows.add(different);
        original.put("heroUnits", rows);
        var restored = HeroUnitSaveData.load(original);
        h.assertTrue(restored.heroUnits.size() == 3 && restored.save(new CompoundTag()).equals(original)
                && restored.heroUnits.get(0).economicValue == null, "Legacy order, duplicate rows, names and opaque/no claim survive unchanged");
    }
}
