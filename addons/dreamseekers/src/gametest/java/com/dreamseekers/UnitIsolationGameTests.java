package com.dreamseekers;

import com.dreamseekers.unit.BasaltSnailUnit;
import com.dreamseekers.unit.BeetleUnit;
import com.dreamseekers.unit.DreamRtsUnit;
import com.dreamseekers.unit.StoneSpiritWorker;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.pasterdream.entity.BasaltSnailEntity;
import net.pasterdream.entity.SmallStoneSpiritEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Isolated runtime regression tests, not packaged in the addon release JAR. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnitIsolationGameTests {
    private static final String BATCH = "dreamseekers_unit_isolation";
    private static final String TEMPLATE = "unit_isolation_arena";
    private static final List<Entity> FIXTURES = new ArrayList<>();

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        for (Entity entity : FIXTURES) if (!entity.isRemoved()) entity.discard();
        FIXTURES.clear();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void stoneWorkerDeathDoesNotGrowSpiritsOrResurrectTerrain(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        StoneSpiritWorker worker = spawn(helper, DreamseekersMod.STONE_SPIRIT_WORKER.get(), 8, 8);
        StoneSpiritWorker neighbour = spawn(helper, DreamseekersMod.STONE_SPIRIT_WORKER.get(), 10, 8);
        SmallStoneSpiritEntity wild = (SmallStoneSpiritEntity) spawnOriginal(helper, "small_stone_spirit", 8, 10);
        worker.getPersistentData().putDouble("size", 0);
        neighbour.getPersistentData().putDouble("size", 0);
        wild.getPersistentData().putDouble("size", 0);
        BlockPos deathPos = worker.blockPosition();
        Block nativeSpiritBlock = ForgeRegistries.BLOCKS.getValue(DreamseekersMod.paster("small_stone_spirit_block"));
        helper.assertTrue(nativeSpiritBlock != null && nativeSpiritBlock != Blocks.AIR, "native resurrection block exists");
        helper.assertTrue(worker.hurt(level.damageSources().genericKill(), Float.MAX_VALUE), "worker receives lethal damage");
        helper.assertTrue(!worker.isAlive(), "worker genuinely dies, not just removed by fixture");
        // Check immediately: a future worker tick zeroing size must not conceal a broken death mixin.
        helper.assertTrue(neighbour.getPersistentData().getDouble("size") == 0, "RTS death must not grow adjacent worker");
        helper.assertTrue(wild.getPersistentData().getDouble("size") == 0, "RTS death must not grow original wild spirit");
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(level.getBlockState(deathPos).isAir(), "RTS death must not queue terrain resurrection");
            helper.assertTrue(wild.getPersistentData().getDouble("size") == 0, "wild spirit remains unchanged after death work queue");
            helper.assertTrue(neighbour.getPersistentData().getDouble("size") == 0, "adjacent worker remains fixed size");
            System.out.println("DREAMSEEKERS_STONE_DEATH_ISOLATION_PASS");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void snailShellIsBoundedPersistentAndDoesNotChangeWildSnails(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        BasaltSnailUnit snail = spawn(helper, DreamseekersMod.BASALT_SNAIL.get(), 5, 5);
        BasaltSnailEntity wild = (BasaltSnailEntity) spawnOriginal(helper, "basalt_snail", 18, 18);
        helper.assertTrue(snail.hurt(level.damageSources().generic(), 2), "RTS snail receives damage");
        helper.assertTrue(snail.getShellCooldown() == BasaltSnailUnit.SHELL_COOLDOWN, "shell starts bounded cooldown");
        assertRtsShell(helper, snail, BasaltSnailUnit.SHELL_DURATION);
        helper.assertTrue(wild.hurt(level.damageSources().generic(), 2), "wild snail receives control damage");
        helper.runAtTickTime(10, () -> {
            MobEffectInstance wildResistance = wild.getEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(wildResistance != null && wildResistance.getAmplifier() == 3,
                    "original wild snail retains native resistance IV; mixin is subclass scoped");
            helper.assertTrue(wildResistance.getDuration() > BasaltSnailUnit.SHELL_DURATION,
                    "original native shell duration is not replaced by RTS cap");
            MobEffectInstance resistance = snail.getEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(resistance != null && resistance.getAmplifier() == 0, "no delayed native resistance IV leaked into RTS snail");
            int beforeDuration = resistance.getDuration();
            int beforeCooldown = snail.getShellCooldown();
            snail.invulnerableTime = 0;
            helper.assertTrue(snail.hurt(level.damageSources().generic(), 2), "second hit reaches RTS snail");
            helper.assertTrue(snail.getEffect(MobEffects.DAMAGE_RESISTANCE).getDuration() == beforeDuration,
                    "repeated hit does not refresh capped shell duration");
            helper.assertTrue(snail.getShellCooldown() == beforeCooldown, "repeated hit does not reset cooldown");
            CompoundTag saved = new CompoundTag();
            snail.addAdditionalSaveData(saved);
            BasaltSnailUnit restored = DreamseekersMod.BASALT_SNAIL.get().create(level);
            helper.assertTrue(restored != null, "RTS snail reload instance exists");
            restored.readAdditionalSaveData(saved);
            helper.assertTrue(restored.getShellCooldown() == beforeCooldown, "shell cooldown survives entity NBT roundtrip");
            helper.assertTrue(restored.getOwnerName().equals(snail.getOwnerName()), "shell reload keeps RTS owner");
            restored.discard();
        });
        helper.runAtTickTime(220, () -> {
            helper.assertTrue(snail.getShellCooldown() == 0, "shell cooldown expires under real world ticking");
            helper.assertTrue(snail.getEffect(MobEffects.DAMAGE_RESISTANCE) == null, "old shell buff expires instead of permanent mitigation");
            snail.invulnerableTime = 0;
            helper.assertTrue(snail.hurt(level.damageSources().generic(), 2), "new hit after cooldown is accepted");
            helper.assertTrue(snail.getShellCooldown() == BasaltSnailUnit.SHELL_COOLDOWN, "shell can activate again after cooldown");
            assertRtsShell(helper, snail, BasaltSnailUnit.SHELL_DURATION);
            System.out.println("DREAMSEEKERS_SNAIL_SHELL_ISOLATION_PASS");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void beetleCannotBeTamedBredOrFarmedForNativeCarapace(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        BeetleUnit beetle = spawn(helper, DreamseekersMod.BEETLE.get(), 10, 10);
        Item nativeFood = ForgeRegistries.ITEMS.getValue(DreamseekersMod.paster("flower_15"));
        Item nativeCarapace = ForgeRegistries.ITEMS.getValue(DreamseekersMod.paster("black_beetle_carapace"));
        helper.assertTrue(nativeFood != null && nativeFood != Items.AIR, "native beetle food exists");
        helper.assertTrue(nativeCarapace != null && nativeCarapace != Items.AIR, "native beetle carapace exists");
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.nameUUIDFromBytes("DreamseekersIsolationFakePlayer".getBytes(StandardCharsets.UTF_8)), "DreamIsoTest"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(nativeFood, 16));
        String ownerBefore = beetle.getOwnerName();
        for (int i = 0; i < 8; i++)
            helper.assertTrue(beetle.mobInteract(player, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                    "RTS beetle rejects native food/taming interaction");
        helper.assertTrue(!beetle.isFood(player.getMainHandItem()), "native breeding food does not enable RTS breeding");
        helper.assertTrue(!beetle.isTame() && !beetle.isInLove() && !beetle.isOrderedToSit(), "beetle remains untamed, not breeding, and standing");
        helper.assertTrue(beetle.getOwnerName().equals(ownerBefore), "pet interaction cannot overwrite RTS owner");
        helper.assertTrue(player.getMainHandItem().getCount() == 16, "rejected interaction does not consume player's items");
        AABB deathArea = beetle.getBoundingBox().inflate(3);
        helper.assertTrue(beetle.hurt(level.damageSources().genericKill(), Float.MAX_VALUE), "RTS beetle genuinely dies");
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, deathArea,
                    item -> item.getItem().is(nativeCarapace)).isEmpty(), "trained beetles cannot yield native carapace loot");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            System.out.println("DREAMSEEKERS_BEETLE_ISOLATION_PASS");
            helper.succeed();
        });
    }

    private static void assertRtsShell(GameTestHelper helper, BasaltSnailUnit snail, int expectedDuration) {
        MobEffectInstance resistance = snail.getEffect(MobEffects.DAMAGE_RESISTANCE);
        MobEffectInstance slowness = snail.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(resistance != null && resistance.getAmplifier() == 0 && resistance.getDuration() == expectedDuration,
                "RTS shell is resistance I for exactly four seconds");
        helper.assertTrue(slowness != null && slowness.getAmplifier() == 2 && slowness.getDuration() == expectedDuration,
                "RTS shell tradeoff is slowness III for exactly four seconds");
    }

    private static void floor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
            for (int y = 1; y < 5; y++)
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState());
        }
    }

    private static <T extends Mob> T spawn(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T entity = type.create(helper.getLevel());
        helper.assertTrue(entity != null, "fixture entity exists");
        place(helper, entity, x, z);
        return entity;
    }

    private static Entity spawnOriginal(GameTestHelper helper, String path, int x, int z) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(DreamseekersMod.paster(path));
        helper.assertTrue(type != null, "original dependency entity type exists: " + path);
        Entity entity = type.create(helper.getLevel());
        helper.assertTrue(entity instanceof Mob, "original dependency creature is a real mob: " + path);
        place(helper, (Mob) entity, x, z);
        return entity;
    }

    private static void place(GameTestHelper helper, Mob entity, int x, int z) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, 1, z));
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        entity.setNoAi(true); // Side-effect tests isolate native hooks, not navigation.
        entity.setPersistenceRequired();
        if (entity instanceof DreamRtsUnit rts) rts.setOwnerName("DreamseekersIsolation");
        FIXTURES.add(entity);
        helper.assertTrue(helper.getLevel().addFreshEntity(entity), "fixture entered real server level");
    }
}
