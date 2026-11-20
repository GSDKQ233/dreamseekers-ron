package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.units.villagers.RoyalGuardUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Hero experience that is still flying when its chunk is saved. The orb and the hero go through the
 * same entity NBT a chunk unload or a normal stop writes, and come back as new entity objects.
 */
@GameTestHolder(HeroExperienceOrbReloadGameTests.NAMESPACE) @PrefixGameTestTemplate(false)
public final class HeroExperienceOrbReloadGameTests {
    static final String NAMESPACE = "api65_followup_hero_xp";
    private static final String TEMPLATE = "ore_mine_test_arena";
    private static final int ORB_VALUE = 2; // an owned kill halves it: one point, ten experience
    private static final List<Fixture> OPEN = new ArrayList<>();

    private static final class Fixture {
        final GameTestHelper h; final String owner; final List<Entity> entities = new ArrayList<>();
        RoyalGuardUnit hero;
        Fixture(GameTestHelper h, String name) {
            this.h = h; owner = "FollowupXp_" + name;
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner, Factions.VILLAGERS));
            hero = EntityRegistrar.ROYAL_GUARD_UNIT.get().create(h.getLevel());
            hero.setOwnerName(owner); hero.setNoAi(true);
            BlockPos p = h.absolutePos(new BlockPos(6, 3, 6));
            hero.moveTo(p.getX() + .5, p.getY(), p.getZ() + .5);
            h.assertTrue(h.getLevel().addFreshEntity(hero), "hero entered the server level");
            entities.add(hero); OPEN.add(this);
        }
        HeroExperienceOrb orb() {
            HeroExperienceOrb orb = HeroExperienceOrb.newOrb(h.getLevel(), hero, false,
                    hero.getX() + 4, hero.getY() + 1, hero.getZ(), ORB_VALUE);
            h.assertTrue(h.getLevel().addFreshEntity(orb), "orb entered the server level");
            entities.add(orb); return orb;
        }
        /** Save, remove and rebuild from NBT, as a chunk reload does. The result is not yet in the level. */
        <T extends Entity> T reload(T entity) {
            CompoundTag tag = new CompoundTag();
            h.assertTrue(entity.save(tag), "entity is saveable");
            return rebuild(entity, tag);
        }
        @SuppressWarnings("unchecked")
        <T extends Entity> T rebuild(T entity, CompoundTag tag) {
            entity.discard();
            Entity loaded = EntityType.loadEntityRecursive(tag, h.getLevel(), e -> e);
            h.assertTrue(loaded != null && loaded != entity && loaded.getClass() == entity.getClass()
                    && loaded.getUUID().equals(entity.getUUID()), "a new object of the same entity was loaded");
            entities.add(loaded); return (T) loaded;
        }
        void publish(Entity entity) { h.assertTrue(h.getLevel().addFreshEntity(entity), "loaded entity published"); }
        void close() {
            PlayerServerEvents.rtsPlayers.removeIf(p -> owner.equals(p.name));
            for (Entity e : entities) if (!e.isRemoved()) e.discard();
        }
    }

    @AfterBatch(batch = "followup_xp_orb") public static void cleanupOrb(ServerLevel l) { closeAll(); }
    @AfterBatch(batch = "followup_xp_hero") public static void cleanupHero(ServerLevel l) { closeAll(); }
    @AfterBatch(batch = "followup_xp_legacy") public static void cleanupLegacy(ServerLevel l) { closeAll(); }
    private static void closeAll() { for (Fixture f : List.copyOf(OPEN)) f.close(); OPEN.clear(); }

    @GameTest(templateNamespace = NAMESPACE, template = TEMPLATE, batch = "followup_xp_orb", timeoutTicks = 400)
    public static void reloadedOrbStillDeliversItsExperience(GameTestHelper h) {
        Fixture f = new Fixture(h, "orb");
        int before = f.hero.getExperience();
        HeroExperienceOrb loaded = f.reload(f.orb());
        f.publish(loaded);
        h.succeedWhen(() -> {
            h.assertTrue(f.hero.getExperience() == before + 10, "reloaded orb pays its ten experience once");
            h.assertTrue(loaded.isRemoved(), "a delivered orb is gone");
        });
    }

    @GameTest(templateNamespace = NAMESPACE, template = TEMPLATE, batch = "followup_xp_hero", timeoutTicks = 400)
    public static void reloadedOrbWaitsForAHeroThatLoadsLater(GameTestHelper h) {
        Fixture f = new Fixture(h, "hero");
        HeroExperienceOrb orb = f.reload(f.orb());
        RoyalGuardUnit hero = f.reload(f.hero);
        f.hero = hero;
        f.publish(orb);
        h.startSequence().thenIdle(10).thenExecute(() -> {
            h.assertTrue(orb.isAlive(), "the orb waits while its hero is not loaded");
            hero.setExperience(0);
            f.publish(hero);
        }).thenWaitUntil(() -> h.assertTrue(hero.getExperience() == 10 && orb.isRemoved(),
                "the orb finds the reloaded hero by UUID and pays once")).thenSucceed();
    }

    @GameTest(templateNamespace = NAMESPACE, template = TEMPLATE, batch = "followup_xp_legacy", timeoutTicks = 100)
    public static void orbSavedWithoutAHeroIsStillRemoved(GameTestHelper h) {
        Fixture f = new Fixture(h, "legacy");
        int before = f.hero.getExperience();
        HeroExperienceOrb orb = f.orb();
        CompoundTag tag = new CompoundTag();
        h.assertTrue(orb.save(tag) && tag.hasUUID(HeroExperienceOrb.TAG_HERO), "the hero UUID is saved");
        tag.remove(HeroExperienceOrb.TAG_HERO); // what a save written before this fix contains
        HeroExperienceOrb loaded = f.rebuild(orb, tag);
        f.publish(loaded);
        h.startSequence().thenIdle(5).thenExecute(() -> h.assertTrue(loaded.isRemoved()
                && f.hero.getExperience() == before, "an orb with no saved hero cannot pay anyone")).thenSucceed();
    }
}
