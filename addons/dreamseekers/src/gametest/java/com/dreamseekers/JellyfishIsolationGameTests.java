// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.JellyfishUnit;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.pasterdream.entity.JellyfishEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Native side-effect isolation tests; no test classes or original assets ship in the addon. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JellyfishIsolationGameTests {
    private static final String BATCH = "dreamseekers_jellyfish_isolation";
    private static final String TEMPLATE = "unit_isolation_arena";
    private static final List<Entity> FIXTURES = new ArrayList<>();
    private static final List<AABB> AREAS = new ArrayList<>();
    private static final List<FakePlayer> PLAYERS = new ArrayList<>();

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        for (Entity entity : FIXTURES) if (!entity.isRemoved()) entity.discard();
        for (AABB area : AREAS) removeFixtureDrops(level, area);
        for (FakePlayer player : PLAYERS) player.getInventory().clearContent();
        FIXTURES.clear();
        AREAS.clear();
        PLAYERS.clear();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void paidScoutKeepsNativeFlightButCannotAttackBreedOrConsumeInteractions(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        JellyfishUnit scout = scout(helper, "JellyIsolationOwner", 6, 6);
        JellyfishUnit otherScout = scout(helper, "JellyIsolationOther", 10, 6);
        JellyfishEntity wild = wild(level);
        JellyfishEntity wildMate = wild(level);
        helper.assertTrue(scout instanceof JellyfishEntity && scout.getMoveControl() instanceof FlyingMoveControl,
                "actual native jellyfish with original flight controller, not a lookalike unit");
        helper.assertTrue(scout instanceof Unit && !(((Object) scout) instanceof AttackerUnit)
                && !(((Object) scout) instanceof WorkerUnit), "scout has no basic-combat or worker contract");
        helper.assertTrue(!ResourceSources.isHuntableAnimal(scout), "scout cannot yield RoN's synthetic hunted-animal food");
        helper.assertTrue(scout.goalSelector.getAvailableGoals().stream().noneMatch(w ->
                        w.getGoal() instanceof BreedGoal || w.getGoal() instanceof FollowParentGoal),
                "paid scout has no native breeding or parent-following goals");
        float otherHealth = otherScout.getHealth();
        helper.assertTrue(!scout.doHurtTarget(otherScout) && otherScout.getHealth() == otherHealth,
                "even a direct attack call cannot damage a neighbouring unit");
        Item fruit = Items.KELP;
        Item egg = nativeItem(helper, "jellyfish_spawn_egg");
        for (String name : List.of("JellyIsolationOwner", "JellyIsolationEnemy", "JellyIsolationAlly")) {
            FakePlayer player = player(level, name);
            for (Item item : List.of(fruit, egg, Items.LEAD, Items.GLASS_BOTTLE, Items.BUCKET)) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 8));
                for (int n = 0; n < 3; n++)
                    helper.assertTrue(scout.mobInteract(player, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                            "native mob interaction is inert for every player, including the owner");
                // Mob.interact is final and handles spawn eggs/leads before mobInteract.
                scout.interact(player, InteractionHand.MAIN_HAND);
                player.interactOn(scout, InteractionHand.MAIN_HAND);
                player.getMainHandItem().interactLivingEntity(player, scout, InteractionHand.MAIN_HAND);
                helper.assertTrue(player.getMainHandItem().getCount() == 8 && scout.isAlive(),
                        "full entity/player/item entrypoints neither consume items nor discard paid scout");
                helper.assertTrue(!scout.isLeashed(), "full interaction entrypoint cannot attach a lead");
            }
            // Even an operator-created matching EntityTag must hit the no-offspring guard,
            // rather than relying only on the raw/addon registry IDs being different.
            ItemStack matchingEgg = new ItemStack(egg, 8);
            matchingEgg.getOrCreateTagElement("EntityTag").putString("id", EntityType.getKey(scout.getType()).toString());
            SpawnEggItem spawnEgg = (SpawnEggItem) egg;
            helper.assertTrue(spawnEgg.spawnsEntity(matchingEgg.getTag(), scout.getType()),
                    "adversarial egg fixture actually matches the paid scout registry ID");
            player.setItemInHand(InteractionHand.MAIN_HAND, matchingEgg);
            scout.interact(player, InteractionHand.MAIN_HAND);
            player.interactOn(scout, InteractionHand.MAIN_HAND);
            helper.assertTrue(spawnEgg.spawnOffspringFromSpawnEgg(player, scout, DreamseekersMod.JELLYFISH_UNIT.get(), level,
                            scout.position(), matchingEgg).isEmpty(),
                    "direct matching-egg path cannot bypass the paid entity's no-offspring policy");
            helper.assertTrue(matchingEgg.getCount() == 8 && scout.isAlive(),
                    "rejected matching egg is neither consumed nor converted into a free creature");
            helper.assertTrue(!scout.canBeLeashed(player), "paid scout cannot become a lead-captured animal");
            scout.setInLove(player);
            scout.setInLoveTime(600);
            helper.assertTrue(!scout.isInLove() && !scout.canFallInLove(), "direct love setters cannot enable breeding");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        helper.assertTrue(!scout.isFood(new ItemStack(fruit)) && !scout.canMate(otherScout) && !scout.canMate(wild),
                "native food and raw/paid partners never enable paid breeding");
        helper.assertTrue(scout.getBreedOffspring(level, otherScout) == null && scout.getBreedOffspring(level, wild) == null,
                "direct native-offspring path cannot create a free raw or paid jellyfish");
        int before = level.getEntitiesOfClass(JellyfishEntity.class, area(helper)).size();
        scout.spawnChildFromBreeding(level, otherScout);
        scout.spawnChildFromBreeding(level, wild);
        helper.assertTrue(level.getEntitiesOfClass(JellyfishEntity.class, area(helper)).size() == before,
                "direct breeding completion creates no extra entity");
        helper.assertTrue(scout.getOwnerName().equals("JellyIsolationOwner"), "all interactions preserve RTS owner");

        // Genuine original-class control, created but not world-ticked so random wild eggs cannot contaminate the paid test.
        FakePlayer breeder = player(level, "JellyIsolationWildControl");
        wild.setInLove(breeder);
        wildMate.setInLove(breeder);
        helper.assertTrue(wild.isFood(new ItemStack(fruit)) && wild.isInLove() && wild.canMate(wildMate),
                "wild native jellyfishs retain food, love and breeding behaviour");
        AgeableMob offspring = wild.getBreedOffspring(level, wildMate);
        helper.assertTrue(offspring != null && offspring.getType() == wild.getType()
                        && !(((Object) offspring) instanceof Unit), "wild control still creates original non-RTS offspring");
        FIXTURES.add(offspring);
        helper.assertTrue(wild.getExperienceReward() > 0, "original Animal XP remains unchanged");
        helper.succeed();
        System.out.println("DREAMSEEKERS_JELLYFISH_NATIVE_INTERACTION_ISOLATION_PASS");
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 70)
    public static void paidSupportDeathAndReloadCannotRestoreAnimalRewards(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        JellyfishUnit scout = scout(helper, "JellyDeathIsolation", 8, 8);
        CompoundTag saved = new CompoundTag();
        scout.addAdditionalSaveData(saved);
        saved.putInt("Age", -24000);
        saved.putInt("InLove", 600);
        saved.putBoolean("NoAI", true);
        scout.readAdditionalSaveData(saved);
        helper.assertTrue(!scout.isBaby() && !scout.isInLove() && !scout.isNoAi(),
                "hostile/native age-love-NoAI NBT cannot restore raw animal behaviour on reload");
        helper.assertTrue(scout.getOwnerName().equals("JellyDeathIsolation") && scout.isNoGravity(),
                "reload retains owner and real native flight policy");
        helper.assertTrue(scout.getExperienceReward() == 0,
                "Animal overrides xpReward: paid scout must explicitly return zero experience");
        FakePlayer killer = player(level, "JellyDeathKiller");
        scout.setLastHurtByPlayer(killer);
        helper.assertTrue(scout.hurt(level.damageSources().genericKill(), Float.MAX_VALUE) && !scout.isAlive(),
                "paid scout genuinely dies with a player-attributed kill, not fixture discard");
        AABB deathArea = scout.getBoundingBox().inflate(4);
        helper.startSequence().thenWaitUntil(()->helper.assertTrue(scout.isRemoved() && scout.deathTime>=10, "actual native ten-tick death completed")).thenExecute(() -> {
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, deathArea).isEmpty(),
                    "paid death yields no slime, water bottle, condensed wind or other native loot");
            helper.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class, deathArea).isEmpty(),
                    "paid death yields no animal XP orb even with player attribution");
            helper.succeed();
            System.out.println("DREAMSEEKERS_JELLYFISH_DEATH_RELOAD_ISOLATION_PASS");
        });
    }

    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        if (!PLAYERS.contains(player)) PLAYERS.add(player);
        return player;
    }

    private static Item nativeItem(GameTestHelper helper, String path) {
        Item item = ForgeRegistries.ITEMS.getValue(DreamseekersMod.paster(path));
        helper.assertTrue(item != null && item != Items.AIR, "native fixture item exists: " + path);
        return item;
    }

    private static JellyfishEntity wild(ServerLevel level) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(DreamseekersMod.paster("jellyfish"));
        JellyfishEntity wild = (JellyfishEntity) type.create(level);
        FIXTURES.add(wild);
        return wild;
    }

    private static JellyfishUnit scout(GameTestHelper helper, String owner, int x, int z) {
        JellyfishUnit scout = DreamseekersMod.JELLYFISH_UNIT.get().create(helper.getLevel());
        helper.assertTrue(scout != null, "registered paid scout exists");
        scout.setOwnerName(owner);
        BlockPos pos = helper.absolutePos(new BlockPos(x, 1, z));
        scout.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        FIXTURES.add(scout);
        helper.assertTrue(helper.getLevel().addFreshEntity(scout), "paid scout entered actual server level");
        return scout;
    }

    private static void floor(GameTestHelper helper) {
        AABB area = area(helper);
        AREAS.add(area);
        removeFixtureDrops(helper.getLevel(), area);
        BlockPos base=helper.absolutePos(BlockPos.ZERO);
        for(int x=0;x<24;x++)for(int z=0;z<24;z++)helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x,10,z)),Blocks.STONE.defaultBlockState(),2);
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new AABB(base.getX(),base.getY()+1,base.getZ(),base.getX()+24,helper.getLevel().getMaxBuildHeight(),base.getZ()+24)).forEach(Entity::discard);
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) {
            helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 2);
            for (int y = 1; y < 10; y++)
                helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static AABB area(GameTestHelper helper) {
        return new AABB(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(24, 10, 24)));
    }

    private static void removeFixtureDrops(ServerLevel level, AABB area) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) item.discard();
        for (ExperienceOrb xp : level.getEntitiesOfClass(ExperienceOrb.class, area)) xp.discard();
    }
}
