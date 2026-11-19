// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.GoldenFoxUnit;
import com.dreamseekers.unit.NativeGoldenFoxHooks;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.pasterdream.entity.GoldenFoxEntity;
import net.pasterdream.procedures.GoldenFoxPr0Procedure;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Actual native calls and delayed side effects, not helper-predicate-only isolation claims. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldenFoxInteractionGameTests {
    private static final String BATCH = "dreamseekers_golden_fox_interaction";
    private static final String TEMPLATE = "unit_isolation_arena";
    private static final List<Entity> FIXTURES = new ArrayList<>();
    private static final List<AABB> AREAS = new ArrayList<>();
    private static final List<FakePlayer> PLAYERS = new ArrayList<>();
    private static final List<BlockPos> CHESTS = new ArrayList<>();

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        for (Entity entity : FIXTURES) if (!entity.isRemoved()) entity.discard();
        for (AABB area : AREAS) removeDrops(level, area);
        for (FakePlayer player : PLAYERS) player.getInventory().clearContent();
        for (BlockPos pos : CHESTS) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        FIXTURES.clear(); AREAS.clear(); PLAYERS.clear(); CHESTS.clear();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 70)
    public static void ownerEnemyAndRealAllyCannotTradeCaptureOrDuplicatePaidFox(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        String owner = "FoxIsoOwner", ally = "FoxIsoAlly";
        GoldenFoxUnit fox = paid(h, owner, 8, 8);
        Item rawEgg = nativeItem(h, "golden_fox_spawn_egg");
        List<Item> inputs = new ArrayList<>(payments(h));
        inputs.add(Items.LEAD);
        inputs.add(rawEgg);
        inputs.add(nativeItem(h, "ecology_glass_jar"));
        AlliancesServerEvents.addAlliance(owner, ally);
        try {
            h.assertTrue(AlliancesServerEvents.isAllied(owner, ally), "ally fixture has a real RoN alliance");
            for (String name : List.of(owner, "FoxIsoEnemy", ally)) {
                FakePlayer player = player(level, name);
                for (Item item : inputs) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 8));
                    for (int replay = 0; replay < 3; replay++) {
                        h.assertTrue(fox.mobInteract(player, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                                "paid NPC-specific interaction is inert: " + name);
                        fox.interact(player, InteractionHand.MAIN_HAND);
                        player.interactOn(fox, InteractionHand.MAIN_HAND);
                        player.getMainHandItem().interactLivingEntity(player, fox, InteractionHand.MAIN_HAND);
                        GoldenFoxPr0Procedure.execute(level, fox.getX(), fox.getY(), fox.getZ(), fox,
                                player.getMainHandItem());
                    }
                    h.assertTrue(player.getMainHandItem().getCount() == 8 && fox.isAlive() && !fox.isRemoved(),
                            "ordinary and direct native replay neither consume payment nor discard fox");
                    h.assertTrue(!fox.isLeashed() && !fox.canBeLeashed(player), "paid fox cannot become lead-captured NPC");
                }
                ItemStack matchingEgg = new ItemStack(rawEgg, 8);
                matchingEgg.getOrCreateTagElement("EntityTag").putString("id", EntityType.getKey(fox.getType()).toString());
                h.assertTrue(((SpawnEggItem) rawEgg).spawnsEntity(matchingEgg.getTag(), fox.getType()),
                        "adversarial spawn egg really matches the paid registry ID");
                player.setItemInHand(InteractionHand.MAIN_HAND, matchingEgg);
                fox.interact(player, InteractionHand.MAIN_HAND);
                player.interactOn(fox, InteractionHand.MAIN_HAND);
                matchingEgg.interactLivingEntity(player, fox, InteractionHand.MAIN_HAND);
                h.assertTrue(matchingEgg.getCount() == 8, "matching spawn egg cannot create free paid offspring");
                player.getInventory().clearContent();
            }
        } finally {
            AlliancesServerEvents.removeAlliance(owner, ally);
        }
        h.assertTrue(fox.getOwnerName().equals(owner) && !ResourceSources.isHuntableAnimal(fox),
                "interaction cannot transfer owner or yield synthetic hunting food");
        h.runAtTickTime(35, () -> {
            h.assertTrue(level.getEntitiesOfClass(GoldenFoxEntity.class, area(h)).size() == 1,
                    "interaction did not create a raw or paid duplicate");
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area(h)).isEmpty()
                            && level.getBlockState(fox.blockPosition()).isAir(),
                    "native 15-tick reward window passed without item grants or chest placement");
            h.assertTrue(fox.isAlive() && fox.getOwnerName().equals(owner), "paid lifecycle survives native trade attempts");
            System.out.println("DREAMSEEKERS_GOLDEN_FOX_INTERACTION_OWNER_ENEMY_ALLY_PASS");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 70)
    public static void directDelayedCallbacksCannotRewardLiveUnownedTransferredOrRemovedPaidFox(GameTestHelper h) throws Exception {
        floor(h);
        GoldenFoxUnit fox = paid(h, "FoxCallbackOwner", 8, 8);
        BlockPos reward = h.absolutePos(new BlockPos(12, 1, 12));
        List<Method> callbacks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Method method = GoldenFoxPr0Procedure.class.getDeclaredMethod("lambda$execute$" + i,
                    LevelAccessor.class, double.class, double.class, double.class, Entity.class);
            method.setAccessible(true);
            callbacks.add(method);
        }
        h.assertTrue(callbacks.size() == 4, "all four locked native delayed callbacks exist");
        for (String owner : List.of("FoxCallbackOwner", "", "FoxCallbackTransferred")) {
            fox.setOwnerName(owner);
            h.assertTrue(NativeGoldenFoxHooks.isPaidUnit(fox), "owner changes cannot reopen native NPC affordances");
            for (Method callback : callbacks) callback.invoke(null, h.getLevel(), reward.getX()+.5,
                    (double) reward.getY(), reward.getZ()+.5, fox);
        }
        fox.discard();
        for (Method callback : callbacks) callback.invoke(null, h.getLevel(), reward.getX()+.5,
                (double) reward.getY(), reward.getZ()+.5, fox);
        // An already removed captured object is still a paid unit; replaying execute cannot spend its payment either.
        ItemStack payment = new ItemStack(Items.GOLDEN_APPLE, 3);
        GoldenFoxPr0Procedure.execute(h.getLevel(), reward.getX()+.5, reward.getY(), reward.getZ()+.5, fox, payment);
        h.assertTrue(payment.getCount() == 3, "removed paid entity cannot be reused as a native exchange token");
        h.runAtTickTime(35, () -> {
            h.assertTrue(h.getLevel().getBlockState(reward).isAir()
                            && h.getLevel().getEntitiesOfClass(ItemEntity.class, area(h)).isEmpty(),
                    "direct callback calls and stale-entity execute replay grant no items or chest");
            System.out.println("DREAMSEEKERS_GOLDEN_FOX_DIRECT_CALLBACK_ISOLATION_PASS");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 70)
    public static void originalWildFoxKeepsAllTradesAndRealDelayedRewards(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        List<Item> payments = payments(h);
        List<Item> rewards = List.of(Items.ENCHANTED_GOLDEN_APPLE, nativeItem(h, "meltdream_liquid_bucket"),
                Items.AIR, nativeItem(h, "cradle_in_ones_arms"), nativeItem(h, "cradle_in_ones_arms"));
        List<BlockPos> positions = new ArrayList<>();
        FakePlayer customer = player(level, "FoxWildCustomer");
        for (int i = 0; i < payments.size(); i++) {
            GoldenFoxEntity fox = wild(h, 3 + i * 4, 12);
            h.assertTrue(fox.isNoAi() && !NativeGoldenFoxHooks.isPaidUnit(fox)
                            && !(((Object) fox) instanceof Unit), "wild registry retains original stationary NPC identity");
            BlockPos pos = fox.blockPosition();
            positions.add(pos);
            if (i == 2) CHESTS.add(pos);
            customer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(payments.get(i), 3));
            fox.interact(customer, InteractionHand.MAIN_HAND);
            h.assertTrue(fox.isRemoved() && customer.getMainHandItem().getCount() == 2,
                    "original wild interaction consumes exactly one and disappears, branch " + i);
        }
        customer.getInventory().clearContent();
        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area(h)).isEmpty()
                        && level.getBlockState(positions.get(2)).isAir(),
                "wild rewards are genuinely delayed, not produced by test setup or immediate interaction");
        h.runAtTickTime(35, () -> {
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                if (i == 2) {
                    Block chest = ForgeRegistries.BLOCKS.getValue(DreamseekersMod.paster("meltdream_chest"));
                    h.assertTrue(chest != null && level.getBlockState(pos).is(chest),
                            "real delayed wild coin exchange places its original chest");
                } else {
                    List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.3));
                    h.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(rewards.get(i))
                                    && drops.get(0).getItem().getCount() == 1,
                            "real delayed wild reward occurs exactly once, branch " + i);
                }
            }
            System.out.println("DREAMSEEKERS_GOLDEN_FOX_WILD_ALL_TRADES_DELAYED_REWARDS_PASS");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void paidFoxTakesOrdinaryDamageAndDeathLootIsEmptyWhileWildImmunitiesRemain(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        GoldenFoxUnit fox = paid(h, "FoxDamageOwner", 8, 8);
        GoldenFoxEntity wild = wild(h, 16, 8);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(fox.tickCount > 2 && wild.tickCount > 2,
                "both real entities are ticking before damage/death assertions")).thenExecute(() -> {
            h.assertTrue(!fox.fireImmune() && wild.fireImmune(), "only wild registry retains native fire immunity");
            h.assertTrue(!(((Object) fox) instanceof AttackerUnit) && !fox.doHurtTarget(wild),
                    "foundation is deliberately nonattacking, not an unapproved offensive skill tree");
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            ThrownPotion potion = new ThrownPotion(EntityType.POTION, level);
            AreaEffectCloud cloud = new AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, level);
            List<DamageSource> formerlyImmune = new ArrayList<>();
            for (ResourceKey<DamageType> type : List.of(DamageTypes.IN_FIRE, DamageTypes.FALL, DamageTypes.CACTUS,
                    DamageTypes.DROWN, DamageTypes.LIGHTNING_BOLT, DamageTypes.EXPLOSION, DamageTypes.TRIDENT,
                    DamageTypes.FALLING_ANVIL, DamageTypes.DRAGON_BREATH, DamageTypes.WITHER, DamageTypes.WITHER_SKULL))
                formerlyImmune.add(source(level, type, null));
            formerlyImmune.add(source(level, DamageTypes.ARROW, arrow));
            formerlyImmune.add(source(level, DamageTypes.INDIRECT_MAGIC, potion));
            formerlyImmune.add(source(level, DamageTypes.MAGIC, cloud));
            for (DamageSource source : formerlyImmune) {
                fox.invulnerableTime = 0; fox.setHealth(fox.getMaxHealth());
                float before = fox.getHealth();
                h.assertTrue(fox.hurt(source, 2) && fox.getHealth() < before,
                        "paid entity receives ordinary damage instead of native immunity: " + source.getMsgId());
                wild.invulnerableTime = 0; wild.setHealth(wild.getMaxHealth());
                h.assertTrue(!wild.hurt(source, 2) && wild.getHealth() == wild.getMaxHealth(),
                        "same original damage immunity remains on genuine wild control: " + source.getMsgId());
            }
            wild.invulnerableTime = 0;
            h.assertTrue(wild.hurt(level.damageSources().generic(), 1) && wild.isNoAi(),
                    "wild control is not universally invulnerable or silently given RTS movement AI");
            fox.invulnerableTime = 0;
            fox.setLastHurtByPlayer(player(level, "FoxDamageKiller"));
            h.assertTrue(fox.getExperienceReward() == 0 && fox.hurt(level.damageSources().genericKill(), Float.MAX_VALUE),
                    "genuine player-attributed paid death, not fixture discard");
            AABB deathArea = fox.getBoundingBox().inflate(3);
            h.runAfterDelay(35, () -> {
                h.assertTrue(fox.isRemoved() && !fox.isAlive() && fox.deathTime >= 20,
                        "native death lifecycle actually completed at least twenty entity death ticks");
                h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, deathArea).isEmpty()
                                && level.getEntitiesOfClass(ExperienceOrb.class, deathArea).isEmpty(),
                        "native death lifecycle yields no item grants or XP farming");
                System.out.println("DREAMSEEKERS_GOLDEN_FOX_NORMAL_DAMAGE_WILD_CONTROL_DEATH_PASS");
                h.succeed();
            });
        });
    }

    private static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, Entity direct) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),
                direct, null);
    }

    private static List<Item> payments(GameTestHelper h) {
        return List.of(Items.GOLDEN_APPLE, Items.BUCKET, nativeItem(h, "dream_coin_1"),
                nativeItem(h, "meltdream_crystal_0"), nativeItem(h, "memento_item_11"));
    }

    private static Item nativeItem(GameTestHelper h, String path) {
        Item item = ForgeRegistries.ITEMS.getValue(DreamseekersMod.paster(path));
        h.assertTrue(item != null && item != Items.AIR, "native fixture item exists: " + path);
        return item;
    }

    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        if (!PLAYERS.contains(player)) PLAYERS.add(player);
        return player;
    }

    private static GoldenFoxUnit paid(GameTestHelper h, String owner, int x, int z) {
        GoldenFoxUnit fox = DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());
        h.assertTrue(fox != null, "registered paid fox exists");
        fox.setOwnerName(owner);
        spawn(h, fox, x, z);
        return fox;
    }

    private static GoldenFoxEntity wild(GameTestHelper h, int x, int z) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(DreamseekersMod.paster("golden_fox"));
        h.assertTrue(type != null, "actual original registry is golden_fox");
        GoldenFoxEntity fox = (GoldenFoxEntity) type.create(h.getLevel());
        h.assertTrue(fox != null && fox.getTexture().equals("gloden_fox_light"),
                "native entity retains its original misspelled texture identifier");
        spawn(h, fox, x, z);
        return fox;
    }

    private static void spawn(GameTestHelper h, Entity entity, int x, int z) {
        BlockPos pos = h.absolutePos(new BlockPos(x, 1, z));
        entity.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5, 0, 0);
        FIXTURES.add(entity);
        h.assertTrue(h.getLevel().addFreshEntity(entity), "fixture entered real server level");
    }

    private static void floor(GameTestHelper h) {
        BlockPos base = h.absolutePos(BlockPos.ZERO);
        AREAS.add(area(h));
        // One-time isolation from natural terrain falling after GameTest's underground clearing.
        for (int x=0; x<24; x++) for (int z=0; z<24; z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x,10,z)), Blocks.STONE.defaultBlockState(), 2);
        h.getLevel().getEntitiesOfClass(FallingBlockEntity.class,
                new AABB(base.getX(),base.getY()+1,base.getZ(),base.getX()+24,h.getLevel().getMaxBuildHeight(),base.getZ()+24))
                .forEach(Entity::discard);
        removeDrops(h.getLevel(), area(h));
        for (int x=0; x<24; x++) for (int z=0; z<24; z++) {
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x,0,z)), Blocks.STONE.defaultBlockState(), 2);
            for (int y=1; y<10; y++)
                h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static AABB area(GameTestHelper h) {
        return new AABB(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(24,10,24)));
    }

    private static void removeDrops(ServerLevel level, AABB area) {
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(Entity::discard);
        level.getEntitiesOfClass(ExperienceOrb.class, area).forEach(Entity::discard);
    }
}
