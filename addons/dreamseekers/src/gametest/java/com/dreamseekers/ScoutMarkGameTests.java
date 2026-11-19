// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.scout.ScoutBalance;
import com.dreamseekers.scout.ScoutMarkNetwork;
import com.dreamseekers.scout.ScoutMarkState;
import com.dreamseekers.unit.BeetleUnit;
import com.dreamseekers.unit.PinkChickenScout;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import net.minecraft.world.effect.MobEffectInstance;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/** Runtime gate/codec/persistence tests; no public network connection or graphics is implied. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScoutMarkGameTests {
    private static final String BATCH = "dreamseekers_scout_mark";
    private static final List<Entity> FIXTURES = new ArrayList<>();
    private static final Set<ChunkPos> ADDED_FORCED_CHUNKS = new HashSet<>();
    @AfterBatch(batch = BATCH) public static void cleanup(ServerLevel level) {
        for (Entity e : FIXTURES) if (!e.isRemoved()) e.discard();
        FIXTURES.clear();
        for (ChunkPos chunk : ADDED_FORCED_CHUNKS) level.setChunkForced(chunk.x, chunk.z, false);
        ADDED_FORCED_CHUNKS.clear();
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 40)
    public static void exactOwnerAndIdentityAreRequiredAndReplayCannotRefresh(GameTestHelper h) {
        floor(h); var owner = player(h, "MarkOwnerA"); var stranger = player(h, "MarkOtherA");
        PinkChickenScout scout = scout(h, owner, 5, 5); BeetleUnit enemy = enemy(h, "MarkEnemyA", 10, 5);
        scout.getMoveGoal().setMoveTarget(h.absolutePos(new BlockPos(5, 0, 18)));
        BlockPos originalMove = scout.getMoveGoal().getMoveTarget();
        scout.setHoldPosition(true);
        int originalQueue = scout.getCheckpoints().size();
        var packet = new ScoutMarkNetwork.Cast(scout.getId(), scout.getUUID(), enemy.getId(), enemy.getUUID(), 0);
        assertResult(h, packet.execute(stranger), ScoutMarkState.Result.OWNER);
        assertResult(h, new ScoutMarkNetwork.Cast(scout.getId(), UUID.randomUUID(), enemy.getId(), enemy.getUUID(), 0).execute(owner), ScoutMarkState.Result.INVALID);
        assertResult(h, new ScoutMarkNetwork.Cast(scout.getId(), scout.getUUID(), enemy.getId(), UUID.randomUUID(), 0).execute(owner), ScoutMarkState.Result.INVALID);
        for (var effect : List.of(MobEffectRegistrar.STUN.get(), MobEffectRegistrar.FREEZE.get(), MobEffectRegistrar.UNCONTROLLABLE.get())) {
            scout.addEffect(new MobEffectInstance(effect, 40));
            assertResult(h, packet.execute(owner), ScoutMarkState.Result.DISABLED);
            scout.removeEffect(effect);
        }
        assertResult(h, packet.execute(owner), ScoutMarkState.Result.SUCCESS);
        h.assertTrue(scout.scoutMark().revision() == 1 && scout.scoutMark().remainingTicks() == ScoutBalance.MARK_DURATION_TICKS,
                "one accepted activation creates exactly one bounded mark");
        for (int i = 0; i < 12; i++) assertResult(h, packet.execute(owner), ScoutMarkState.Result.REPLAY);
        h.assertTrue(scout.scoutMark().revision() == 1 && scout.scoutMark().remainingTicks() == ScoutBalance.MARK_DURATION_TICKS,
                "replay never stacks, spends again or extends duration");
        assertResult(h, new ScoutMarkNetwork.Cast(scout.getId(), scout.getUUID(), enemy.getId(), enemy.getUUID(), 1).execute(owner), ScoutMarkState.Result.COOLDOWN);
        h.assertTrue(scout.getTargetGoal().getTarget() == null
                        && java.util.Objects.equals(scout.getMoveGoal().getMoveTarget(), originalMove)
                        && scout.getHoldPosition() && scout.getCheckpoints().size() == originalQueue,
                "mark does not retarget, clear movement, release hold or overwrite queued commands");
        System.out.println("DREAMSEEKERS_SCOUT_MARK_OWNER_UUID_REPLAY_PASS"); h.succeed();
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 40)
    public static void alliedOwnedNeutralAndRawTargetsRejectedButSameFactionEnemyAllowed(GameTestHelper h) {
        floor(h); var owner = player(h, "MarkOwnerB"); PinkChickenScout scout = scout(h, owner, 5, 5);
        BeetleUnit enemy = enemy(h, "MarkEnemyB", 10, 5); BeetleUnit ally = enemy(h, "MarkAllyB", 11, 5);
        BeetleUnit own = enemy(h, owner.getGameProfile().getName(), 12, 5); BeetleUnit neutral = enemy(h, "", 13, 5);
        Mob raw = EntityType.CHICKEN.create(h.getLevel()); place(h, raw, 14, 5); raw.setNoAi(true);
        AlliancesServerEvents.addAlliance(owner.getGameProfile().getName(), "MarkAllyB");
        try {
            assertResult(h, scout.scoutMark().eligibility(owner, ally), ScoutMarkState.Result.ALLY);
            assertResult(h, scout.scoutMark().eligibility(owner, own), ScoutMarkState.Result.ALLY);
            assertResult(h, scout.scoutMark().eligibility(owner, neutral), ScoutMarkState.Result.ALLY);
            assertResult(h, scout.scoutMark().eligibility(owner, raw), ScoutMarkState.Result.INVALID);
            assertResult(h, scout.scoutMark().eligibility(owner, scout), ScoutMarkState.Result.INVALID);
            assertResult(h, scout.scoutMark().eligibility(owner, enemy), ScoutMarkState.Result.SUCCESS);
            h.assertTrue(enemy instanceof BeetleUnit && scout.getType() == DreamseekersMod.PINK_CHICKEN_SCOUT.get(),
                    "same Dreamseekers faction is not implicitly an alliance");
        } finally { AlliancesServerEvents.removeAlliance(owner.getGameProfile().getName(), "MarkAllyB"); }
        System.out.println("DREAMSEEKERS_SCOUT_MARK_RELATIONSHIP_PASS"); h.succeed();
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 40)
    public static void lineOfSightRangeInvisibilityAndAuthoritativeFogAreRequired(GameTestHelper h) throws Exception {
        floor(h); var owner = player(h, "MarkOwnerC"); PinkChickenScout scout = scout(h, owner, 3, 5);
        BeetleUnit enemy = enemy(h, "MarkEnemyC", 9, 5);
        assertResult(h, scout.scoutMark().eligibility(owner, enemy), ScoutMarkState.Result.SUCCESS);
        for(int y=1;y<5;y++) h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(6,y,5)), Blocks.STONE.defaultBlockState());
        assertResult(h, scout.scoutMark().eligibility(owner, enemy), ScoutMarkState.Result.HIDDEN);
        for(int y=1;y<5;y++) h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(6,y,5)), Blocks.AIR.defaultBlockState());
        enemy.setInvisible(true); assertResult(h, scout.scoutMark().eligibility(owner, enemy), ScoutMarkState.Result.HIDDEN); enemy.setInvisible(false);
        enemy.setPos(scout.getX() + ScoutBalance.MARK_RANGE + 1, scout.getY(), scout.getZ());
        assertResult(h, scout.scoutMark().eligibility(owner, enemy), ScoutMarkState.Result.RANGE);
        enemy.setPos(scout.getX() + 6, scout.getY(), scout.getZ());
        // Controlled server fog cache fixture: synchronous restore avoids affecting other tests.
        Field enabled = FogOfWarServerEvents.class.getDeclaredField("enabled"); enabled.setAccessible(true);
        Field cache = FogOfWarServerEvents.class.getDeclaredField("isRtsPlayerCache"); cache.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID, Boolean> map = (Map<UUID, Boolean>) cache.get(null);
        boolean previous = enabled.getBoolean(null); Boolean old = map.put(owner.getUUID(), true);
        try {
            enabled.setBoolean(null, true);
            assertResult(h, scout.scoutMark().eligibility(owner, enemy), ScoutMarkState.Result.HIDDEN);
        } finally { enabled.setBoolean(null, previous); if (old == null) map.remove(owner.getUUID()); else map.put(owner.getUUID(), old); }
        h.assertTrue(scout.scoutMark().cooldownTicks() == 0 && !scout.scoutMark().hasActiveMark(), "rejected targets spend no cooldown");
        System.out.println("DREAMSEEKERS_SCOUT_MARK_VISIBILITY_PASS"); h.succeed();
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 40)
    public static void targetAndCasterOwnershipAllianceDeathAndLossInvalidate(GameTestHelper h) {
        floor(h); var owner = player(h, "MarkOwnerD"); PinkChickenScout scout = scout(h, owner, 5, 5);
        BeetleUnit enemy = enemy(h, "MarkEnemyD", 10, 5);
        assertResult(h, scout.scoutMark().tryCast(owner, enemy, 0), ScoutMarkState.Result.SUCCESS);
        h.assertTrue(scout.scoutMark().validateActiveFor(owner), "fresh mark is valid");
        enemy.setOwnerName("MarkTransferD"); h.assertTrue(!scout.scoutMark().validateActiveFor(owner), "target ownership transfer invalidates mark"); enemy.setOwnerName("MarkEnemyD");
        AlliancesServerEvents.addAlliance(owner.getGameProfile().getName(), "MarkEnemyD");
        try { h.assertTrue(!scout.scoutMark().validateActiveFor(owner), "new alliance invalidates mark"); }
        finally { AlliancesServerEvents.removeAlliance(owner.getGameProfile().getName(), "MarkEnemyD"); }
        scout.setOwnerName("MarkNewOwnerD"); h.assertTrue(!scout.scoutMark().validateActiveFor(owner), "caster transfer invalidates mark"); scout.setOwnerName(owner.getGameProfile().getName());
        enemy.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        h.assertTrue(!scout.scoutMark().validateActiveFor(owner), "dead target invalidates mark");
        scout.scoutMark().tick(); h.assertTrue(!scout.scoutMark().hasActiveMark(), "server tick clears lost/offline observation");
        h.assertTrue(scout.scoutMark().cooldownTicks() == ScoutBalance.MARK_COOLDOWN_TICKS, "invalidation cannot refund cooldown");
        System.out.println("DREAMSEEKERS_SCOUT_MARK_INVALIDATION_PASS"); h.succeed();
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 600)
    public static void worldTicksExpireMarkAndCooldownWithoutReloadExtension(GameTestHelper h) {
        floor(h); forceTicking(h, h.absolutePos(new BlockPos(5, 1, 5)));
        var owner = player(h, "MarkOwnerE"); PinkChickenScout scout = scout(h, owner, 5, 5);
        BeetleUnit enemy = enemy(h, "MarkEnemyE", 10, 5);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(scout.tickCount > 2,
                "wait for ordinary scout entity ticking before casting: ticks=" + scout.tickCount
                        + " chunkTicking=" + h.getLevel().isPositionEntityTicking(scout.blockPosition())
                        + " removed=" + scout.isRemoved())).thenExecute(() -> {
        int initialEntityTicks = scout.tickCount;
        assertResult(h, scout.scoutMark().tryCast(owner, enemy, 0), ScoutMarkState.Result.SUCCESS);
        CompoundTag saved = new CompoundTag(); scout.addAdditionalSaveData(saved);
        PinkChickenScout detached = DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(h.getLevel());
        detached.readAdditionalSaveData(saved); detached.setPos(scout.position()); FIXTURES.add(detached);
        // Detached reload fixture is not entity-ticked; absolute world time must still expire it.
        h.assertTrue(detached.scoutMark().revision() == 1 && detached.scoutMark().remainingTicks() == ScoutBalance.MARK_DURATION_TICKS,
                "NBT retains one target, revision and original deadline");
        h.runAfterDelay(30, () -> {
            h.assertTrue(detached.scoutMark().remainingTicks() <= ScoutBalance.MARK_DURATION_TICKS - 29, "absolute lifetime advances even unloaded");
            CompoundTag later = new CompoundTag(); detached.addAdditionalSaveData(later); detached.readAdditionalSaveData(later);
            h.assertTrue(detached.scoutMark().remainingTicks() <= ScoutBalance.MARK_DURATION_TICKS - 29, "save/reload does not restart duration");
            h.assertTrue(scout.tickCount > initialEntityTicks, "ordinary entity ticking advanced before offline check: ticks="
                    + scout.tickCount + " initial=" + initialEntityTicks + " chunkTicking="
                    + h.getLevel().isPositionEntityTicking(scout.blockPosition()) + " removed=" + scout.isRemoved());
            h.assertTrue(!scout.scoutMark().hasActiveMark(), "offline fake owner cannot leave a live server observation: entityTicks=" + scout.tickCount);
        });
        h.runAfterDelay(125, () -> {
            h.assertTrue(detached.scoutMark().remainingTicks() == 0 && !detached.scoutMark().validateActiveFor(owner), "mark expires by absolute world ticks");
            h.assertTrue(detached.scoutMark().cooldownTicks() > 0, "expiry does not also reset cooldown");
        });
        h.runAfterDelay(405, () -> {
            h.assertTrue(detached.scoutMark().cooldownTicks() == 0, "cooldown genuinely expires under real ticks");
            assertResult(h, detached.scoutMark().tryCast(owner, enemy, 0), ScoutMarkState.Result.REPLAY);
            var ray=h.getLevel().clip(new net.minecraft.world.level.ClipContext(detached.getEyePosition(),enemy.getEyePosition(),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,detached));
            System.out.println("SCOUT_WORLD_TIME_RECAST worldTick="+h.getTick()+" gameTime="+h.getLevel().getGameTime()
                    +" detached="+detached.position()+" target="+enemy.position()+" targetTick="+enemy.tickCount
                    +" cooldown="+detached.scoutMark().cooldownTicks()+" revision="+detached.scoutMark().revision()
                    +" eligibility="+detached.scoutMark().eligibility(owner,enemy)+" los="+detached.hasLineOfSight(enemy)
                    +" fog="+FogOfWarServerEvents.isBlockVisibleFor(owner,enemy.getBlockX(),enemy.getBlockZ())
                    +" ray="+ray.getType()+"@"+ray.getBlockPos()+":"+h.getLevel().getBlockState(ray.getBlockPos()));
            assertResult(h, detached.scoutMark().tryCast(owner, enemy, 1), ScoutMarkState.Result.SUCCESS);
            h.assertTrue(detached.scoutMark().revision() == 2, "new deliberate cast accepted once after cooldown, not old replay");
            System.out.println("DREAMSEEKERS_SCOUT_MARK_WORLD_TIME_NBT_PASS"); h.succeed();
        });
        });
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 40)
    public static void packetCodecAndCorruptNbtAreBounded(GameTestHelper h) {
        floor(h); var owner = player(h, "MarkOwnerF"); PinkChickenScout scout = scout(h, owner, 5, 5);
        BeetleUnit enemy = enemy(h, "MarkEnemyF", 10, 5);
        var cast = new ScoutMarkNetwork.Cast(scout.getId(), scout.getUUID(), enemy.getId(), enemy.getUUID(), 4);
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        try { cast.encode(bytes); h.assertTrue(cast.equals(ScoutMarkNetwork.Cast.decode(bytes)), "cast IDs UUIDs and revision survive wire codec"); }
        finally { bytes.release(); }
        var sync = new ScoutMarkNetwork.Sync(scout.getId(), scout.getUUID(), 7, 123, enemy.getId(), enemy.getUUID(), 80);
        FriendlyByteBuf reply = new FriendlyByteBuf(Unpooled.buffer());
        try { sync.encode(reply); h.assertTrue(sync.equals(ScoutMarkNetwork.Sync.decode(reply)), "private state wire codec roundtrips exactly"); }
        finally { reply.release(); }
        CompoundTag root = new CompoundTag(); CompoundTag state = new CompoundTag();
        state.putLong("CooldownUntil", Long.MAX_VALUE); state.putLong("Revision", -42); state.putLong("ExpiresAt", Long.MAX_VALUE);
        root.put("DreamseekersScoutMark", state); scout.scoutMark().load(root);
        h.assertTrue(scout.scoutMark().cooldownTicks() == ScoutBalance.MARK_COOLDOWN_TICKS && scout.scoutMark().revision() == 0,
                "malformed stored durations/revisions cannot become infinite or negative");
        h.assertTrue(!scout.scoutMark().hasActiveMark(), "missing caster/target never creates a phantom mark");
        System.out.println("DREAMSEEKERS_SCOUT_MARK_CODEC_NBT_BOUNDS_PASS"); h.succeed();
    }

    private static void assertResult(GameTestHelper h, ScoutMarkState.Result actual, ScoutMarkState.Result expected) {
        h.assertTrue(actual == expected, "expected " + expected + " but received " + actual);
    }
    private static FakePlayer player(GameTestHelper h, String name) {
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
    }
    private static PinkChickenScout scout(GameTestHelper h, FakePlayer owner, int x, int z) {
        PinkChickenScout scout = DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(h.getLevel());
        scout.setOwnerName(owner.getGameProfile().getName()); place(h, scout, x, z); return scout;
    }
    private static BeetleUnit enemy(GameTestHelper h, String owner, int x, int z) {
        BeetleUnit unit = DreamseekersMod.BEETLE.get().create(h.getLevel()); unit.setOwnerName(owner); unit.setNoAi(true);
        place(h, unit, x, z); return unit;
    }
    private static void place(GameTestHelper h, Mob entity, int x, int z) {
        h.assertTrue(entity != null, "fixture created"); BlockPos pos = h.absolutePos(new BlockPos(x,1,z));
        entity.moveTo(pos.getX()+.5, pos.getY()+.4, pos.getZ()+.5, 0, 0); entity.setPersistenceRequired();
        FIXTURES.add(entity); h.assertTrue(h.getLevel().addFreshEntity(entity), "fixture enters real level");
    }
    private static void forceTicking(GameTestHelper h, BlockPos position) {
        ChunkPos center = new ChunkPos(position);
        // Explicit local tickets keep real entity simulation active after the preceding fake-player
        // fixture disconnects. Never call entity.tick() manually or change pre-existing forced chunks.
        for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) {
            ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
            if (!h.getLevel().getForcedChunks().contains(chunk.toLong())) {
                ADDED_FORCED_CHUNKS.add(chunk);
                h.getLevel().setChunkForced(chunk.x, chunk.z, true);
            }
            h.getLevel().getChunk(chunk.x, chunk.z);
        }
    }
    private static void floor(GameTestHelper h) {
        // One-time test terrain isolation: the frozen failed recast arena contained
        // natural gravel in the detached caster's fixed position. No per-tick clearing.
        BlockPos base = h.absolutePos(BlockPos.ZERO);
        for (int x=0;x<24;x++) for(int z=0;z<24;z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x,10,z)), Blocks.STONE.defaultBlockState(), 2);
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new net.minecraft.world.phys.AABB(base.getX(),base.getY()+1,base.getZ(),
                        base.getX()+24,h.getLevel().getMaxBuildHeight(),base.getZ()+24)).forEach(Entity::discard);
        for (int x=0;x<24;x++) for(int z=0;z<24;z++) {
            h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(x,0,z)), Blocks.STONE.defaultBlockState());
            for(int y=1;y<10;y++) h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(x,y,z)), Blocks.AIR.defaultBlockState());
        }
    }
}
