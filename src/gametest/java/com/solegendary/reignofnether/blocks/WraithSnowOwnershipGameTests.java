package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.units.monsters.WretchedWraithUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native entity publication/removal and block callbacks. NBT reload is not a process restart. */
@GameTestHolder("api62_snow_ownership")
@PrefixGameTestTemplate(false)
public final class WraithSnowOwnershipGameTests {
    private static final String TEMPLATE = "ore_mine_test_arena";

    private static WraithSnowBlockEntity snow(GameTestHelper h, BlockPos pos, int layers) {
        var level = h.getLevel();
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, BlockRegistrar.WRAITH_SNOW_LAYER.get().defaultBlockState()
                .setValue(BlockStateProperties.LAYERS, layers));
        return (WraithSnowBlockEntity) level.getBlockEntity(pos);
    }

    private static WretchedWraithUnit wraith(GameTestHelper h, BlockPos pos, String owner) {
        var unit = EntityRegistrar.WRETCHED_WRAITH_UNIT.get().create(h.getLevel());
        h.assertTrue(unit != null, "wraith created");
        unit.setOwnerName(owner);
        unit.setNoAi(true);
        unit.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        h.assertTrue(h.getLevel().addFreshEntity(unit), "wraith natively published");
        return unit;
    }

    @GameTest(templateNamespace = "api62_snow_ownership", template = TEMPLATE, batch = "snow_ownership", timeoutTicks = 100)
    public static void persistentOwnerDoesNotFollowRecycledRuntimeId(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(5, 3, 5));
        var owner = wraith(h, pos, "snow-original");
        var be = snow(h, pos, 1);
        WretchedWraithUnit impostor = null, restored = null;
        try {
            be.setOwnerId(owner.getId());
            CompoundTag snowTag = be.saveWithoutMetadata();
            CompoundTag ownerTag = owner.saveWithoutId(new CompoundTag());
            int oldId = owner.getId();
            h.assertTrue(snowTag.hasUUID("ownerUuid") && !snowTag.contains("ownerId"), "only persistent UUID saved");
            owner.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            impostor = EntityRegistrar.WRETCHED_WRAITH_UNIT.get().create(level);
            h.assertTrue(impostor != null, "impostor created");
            impostor.setId(oldId);
            impostor.setNoAi(true);
            impostor.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            h.assertTrue(level.addFreshEntity(impostor), "old runtime ID reused by different UUID");
            be.load(snowTag);
            h.assertTrue(be.getOwnerId() == -1 && !be.cannotMelt(), "unloaded owner cannot bind to recycled ID");
            restored = EntityRegistrar.WRETCHED_WRAITH_UNIT.get().create(level);
            h.assertTrue(restored != null, "restored owner created");
            restored.load(ownerTag);
            h.assertTrue(level.addFreshEntity(restored), "same UUID reloaded with new runtime ID");
            h.assertTrue(restored.getId() != oldId && be.getOwnerId() == restored.getId(), "UUID tracks original owner after ID change");
            h.assertTrue(be.cannotMelt(), "same live nearby wraith still prevents melt");
        } finally {
            if (!owner.isRemoved()) owner.discard();
            if (impostor != null) impostor.discard();
            if (restored != null) restored.discard();
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "api62_snow_ownership", template = TEMPLATE, batch = "snow_ownership", timeoutTicks = 100)
    public static void legacyAndMalformedNbtCannotBindOrLiveForever(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(5, 3, 5));
        var owner = wraith(h, pos, "snow-legacy");
        var be = snow(h, pos, 1);
        try {
            for (int saved : new int[]{-500, 0, Integer.MAX_VALUE}) {
                var tag = new CompoundTag();
                tag.putInt("ownerId", owner.getId());
                tag.putInt("ticksToNextMelt", saved);
                be.load(tag);
                h.assertTrue(be.getOwnerId() == -1 && !be.cannotMelt(), "legacy numeric ID never rebound");
                int lifetime = be.saveWithoutMetadata().getInt("ticksToNextMelt");
                h.assertTrue(lifetime >= 180 && lifetime < 280, "invalid lifetime gets normal one-layer default");
            }
            var missing = new CompoundTag();
            missing.putString("ownerUuid", "not-a-uuid");
            be.load(missing);
            h.assertTrue(be.getOwnerId() == -1 && be.saveWithoutMetadata().getInt("ticksToNextMelt") >= 180,
                    "missing lifetime and malformed UUID safe");
            missing.putInt("ticksToNextMelt", 47);
            be.load(missing);
            h.assertTrue(be.saveWithoutMetadata().getInt("ticksToNextMelt") == 47, "valid countdown retained");
            for (int i = 0; i < 10; i++) WraithSnowBlockEntity.tick(h.getLevel(), pos, be.getBlockState(), be);
            h.assertTrue(be.saveWithoutMetadata().getInt("ticksToNextMelt") == 37, "countdown progresses normally");
        } finally {
            owner.discard();
            h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "api62_snow_ownership", template = TEMPLATE, batch = "snow_ownership", timeoutTicks = 100)
    public static void friendlyImmunityAndEnemyEffectsUseRestoredIdentity(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(5, 3, 5));
        var owner = wraith(h, pos, "snow-friendly");
        var friendly = EntityRegistrar.ZOMBIE_UNIT.get().create(level);
        var enemy = EntityRegistrar.ZOMBIE_UNIT.get().create(level);
        h.assertTrue(friendly != null && enemy != null, "target units created");
        var be = snow(h, pos, 2);
        try {
            be.setOwnerId(owner.getId());
            be.load(be.saveWithoutMetadata());
            friendly.setOwnerName("snow-friendly"); enemy.setOwnerName("snow-enemy");
            friendly.tickCount = 5; enemy.tickCount = 5;
            var state = level.getBlockState(pos);
            state.getBlock().entityInside(state, level, pos, friendly);
            state.getBlock().entityInside(state, level, pos, enemy);
            h.assertTrue(!friendly.hasEffect(MobEffectRegistrar.COLD.get())
                    && !friendly.hasEffect(MobEffectRegistrar.DAMAGE_TAKEN_INCREASE.get()), "same-owner unit remains immune");
            h.assertTrue(enemy.hasEffect(MobEffectRegistrar.COLD.get())
                    && enemy.hasEffect(MobEffectRegistrar.DAMAGE_TAKEN_INCREASE.get()), "enemy still receives both existing effects");
            var generic = EntityType.PIG.create(level);
            h.assertTrue(generic != null && level.addFreshEntity(generic), "generic non-wraith owner published");
            try {
                be.setOwnerId(generic.getId());
                be.load(be.saveWithoutMetadata());
                h.assertTrue(be.getOwnerId() == generic.getId() && !be.cannotMelt(), "generic owner identity retained without wraith melt privilege");
            } finally { generic.discard(); }
        } finally {
            owner.discard(); friendly.discard(); enemy.discard();
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "api62_snow_ownership", template = TEMPLATE, batch = "snow_ownership", timeoutTicks = 100)
    public static void normalCountdownMeltsLayersThenRemovesSnow(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(5, 3, 5));
        var owner = EntityType.PIG.create(level);
        h.assertTrue(owner != null && level.addFreshEntity(owner), "generic owner published");
        var be = snow(h, pos, 2);
        try {
            be.setOwnerId(owner.getId());
            var tag = be.saveWithoutMetadata();
            tag.putInt("ticksToNextMelt", 1);
            be.load(tag);
            for (int i = 0; i < 10; i++) WraithSnowBlockEntity.tick(level, pos, be.getBlockState(), be);
            h.assertTrue(BlockUtils.getWraithSnowLayers(level.getBlockState(pos)) == 1,
                    "elapsed countdown removes exactly one layer");
            h.assertTrue(level.getBlockEntity(pos) == be && be.getOwnerId() == owner.getId(),
                    "layer reduction preserves block entity and exact owner");
            h.assertTrue(be.saveWithoutMetadata().getInt("ticksToNextMelt") >= 180,
                    "remaining layer gets normal fresh countdown");
            tag = be.saveWithoutMetadata();
            tag.putInt("ticksToNextMelt", 1);
            be.load(tag);
            for (int i = 0; i < 10; i++) WraithSnowBlockEntity.tick(level, pos, be.getBlockState(), be);
            h.assertTrue(level.getBlockState(pos).isAir() && be.isRemoved(),
                    "last-layer countdown removes snow through native block lifecycle");
        } finally {
            owner.discard();
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    private static void verifySpread(GameTestHelper h, int horizontal, int rise) {
        var level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(5, 7, 5));
        // Remove other candidate support blocks so random selection has exactly one legal target.
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3)))
            level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        var originalOwner = wraith(h, origin, "snow-before");
        var newOwner = wraith(h, origin, "snow-after");
        var centre = snow(h, origin, 8);
        centre.setOwnerId(originalOwner.getId());
        BlockPos target = origin.north(horizontal).above(rise);
        level.setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
        try {
            h.assertTrue(BlockServerEvents.placeWraithSnow(level, origin, newOwner.getId(), false), "snow spreads to sole legal candidate");
            h.assertTrue(level.getBlockEntity(target) instanceof WraithSnowBlockEntity, "validated vertical coordinate used for placement");
            var spread = (WraithSnowBlockEntity) level.getBlockEntity(target);
            h.assertTrue(spread.getOwnerId() == newOwner.getId(), "new target gets new owner");
            h.assertTrue(centre.getOwnerId() == originalOwner.getId(), "original snow owner not overwritten");
            h.assertTrue(spread.saveWithoutMetadata().getInt("ticksToNextMelt") >= 180, "new target lifetime initialized");
        } finally {
            originalOwner.discard(); newOwner.discard();
            for (BlockPos p : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3)))
                level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
    }

    @GameTest(templateNamespace = "api62_snow_ownership", template = TEMPLATE, batch = "snow_ownership", timeoutTicks = 100)
    public static void oneBlockSpreadUsesActualElevatedTarget(GameTestHelper h) { verifySpread(h, 1, 1); h.succeed(); }

    @GameTest(templateNamespace = "api62_snow_ownership", template = TEMPLATE, batch = "snow_ownership", timeoutTicks = 100)
    public static void distanceTwoSpreadSearchesTwoVerticalBlocks(GameTestHelper h) { verifySpread(h, 2, 2); h.succeed(); }
}
