// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.BuildingAbility;
import com.dreamseekers.buildingability.BuildingAbilityPackets;
import com.dreamseekers.buildingability.BuildingCodex;
import com.dreamseekers.buildingability.TowerMarkPackets;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real packet codecs and rejection bounds. No client classes, render simulation or authority bypass. */
@GameTestHolder("dreamseekers_building_abilities")
@PrefixGameTestTemplate(false)
public final class BuildingAbilityWireGameTests {
    private static final String BATCH = "building_ability_wire";
    private static final UUID STREAM = new UUID(101, 102), SESSION = new UUID(103, 104), EPOCH = new UUID(105, 106);
    private static final UUID VIEW = new UUID(107, 108), INSTANCE = new UUID(109, 110);
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("minecraft", "wolf");
    private static final BlockPos ORIGIN = new BlockPos(-17, 64, 31); // Own source, never an enemy position.
    private BuildingAbilityWireGameTests() { }

    private static List<TowerMarkPackets.Target> targets(int count) {
        List<TowerMarkPackets.Target> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) rows.add(new TowerMarkPackets.Target(i + 1, new UUID(201, i + 1)));
        return rows;
    }
    private static TowerMarkPackets.Snapshot tower(int remaining, List<TowerMarkPackets.Target> rows) {
        return new TowerMarkPackets.Snapshot(41, SESSION, EPOCH, "WireOwner", DIMENSION, ORIGIN,
            INSTANCE, 11, 12, remaining, rows);
    }
    private static BuildingCodex.Row row(int id, UUID uuid, float hp, float maxHp, int care) {
        return new BuildingCodex.Row(id, uuid, TYPE, hp, maxHp, care, true);
    }
    private static List<BuildingCodex.Row> rows(int count) {
        List<BuildingCodex.Row> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(row(i + 1, new UUID(301, i + 1), 6.25F + i, 40F, i % 4));
        return result;
    }
    private static BuildingAbilityPackets.Snapshot ability(BuildingAbility ability, BuildingCodex.Page page,
                                                           int active, boolean supported, boolean available) {
        return new BuildingAbilityPackets.Snapshot(STREAM, 42, SESSION, EPOCH, "WireOwner", DIMENSION,
            ORIGIN, VIEW, INSTANCE, 10, 11, 12, ability, ability.cooldownTicks(), active,
            supported, available, "ready", "success", 43, page);
    }
    private static BuildingAbilityPackets.Snapshot codex(BuildingCodex.Page page) {
        return ability(BuildingAbility.ECOLOGY_CODEX, page, 0, true, true);
    }
    private static TowerMarkPackets.Snapshot roundTrip(GameTestHelper h, TowerMarkPackets.Snapshot sent) {
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            sent.encode(bytes);
            var received = new TowerMarkPackets.Snapshot(bytes);
            h.assertTrue(sent.equals(received) && bytes.readableBytes() == 0,
                "real tower codec preserves every identity/source field and consumes exactly its payload");
            return received;
        } finally { bytes.release(); }
    }
    private static BuildingAbilityPackets.Snapshot roundTrip(GameTestHelper h, BuildingAbilityPackets.Snapshot sent) {
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            sent.encode(bytes);
            var received = new BuildingAbilityPackets.Snapshot(bytes);
            h.assertTrue(sent.equals(received) && bytes.readableBytes() == 0,
                "real ability codec preserves all economy/view/source/row fields and consumes exactly its payload");
            return received;
        } finally { bytes.release(); }
    }
    private static void rejectsTowerCount(GameTestHelper h, int count) {
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        boolean rejected = false;
        try {
            tower(120, List.of()).encode(bytes);
            // The final one-byte zero is the real encoded target count; preserve all real header bytes.
            bytes.writerIndex(bytes.writerIndex() - 1);
            bytes.writeVarInt(count);
            try { new TowerMarkPackets.Snapshot(bytes); }
            catch (IllegalArgumentException error) {
                rejected = "Tower target limit".equals(error.getMessage());
            }
            h.assertTrue(rejected, "tower count " + count + " rejected by explicit bound before reading target bodies");
        } finally { bytes.release(); }
    }
    private static void rejectsPageDecode(GameTestHelper h, BuildingCodex.Page page) {
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        boolean rejected = false;
        try {
            codex(page).encode(bytes);
            try { new BuildingAbilityPackets.Snapshot(bytes); }
            catch (IllegalArgumentException error) {
                rejected = "Invalid codex page bounds".equals(error.getMessage());
            }
            h.assertTrue(rejected, "codex header rejected by explicit page/total/count bound");
        } finally { bytes.release(); }
    }
    private static void invalidPageRoundTrip(GameTestHelper h, BuildingCodex.Page page, String message) {
        h.assertTrue(!page.valid(), message + " (page rejects)");
        h.assertTrue(!roundTrip(h, codex(page)).valid(), message + " (decoded snapshot rejects)");
    }

    @GameTest(templateNamespace="dreamseekers_building_abilities", template="dreamseekers_test_arena", batch=BATCH, timeoutTicks=40)
    public static void towerIdUuidMaxSixAndClearRoundTrip(GameTestHelper h) {
        h.assertTrue(roundTrip(h, tower(120, targets(6))).valid(), "six IDs/UUIDs and full 120-tick lifetime accepted");
        h.assertTrue(roundTrip(h, tower(1, targets(1))).valid(), "final active tick survives codec");
        h.assertTrue(roundTrip(h, tower(0, List.of())).valid(), "zero remaining with empty targets is valid clear");
        h.assertTrue(roundTrip(h, tower(120, List.of())).valid(), "empty subset remains legal for immediate client removal");
        List<TowerMarkPackets.Target> mutable = targets(6);
        var copied = tower(120, mutable);
        mutable.clear();
        h.assertTrue(roundTrip(h, copied).targets().size() == 6, "snapshot defensively copies target list");
        h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_building_abilities", template="dreamseekers_test_arena", batch=BATCH, timeoutTicks=40)
    public static void towerOversizeIdentityAndLifetimeRejected(GameTestHelper h) {
        h.assertTrue(!tower(120, targets(7)).valid(), "seventh target cannot be sent as a valid snapshot");
        rejectsTowerCount(h, 7);
        rejectsTowerCount(h, -1);
        for (int remaining : new int[]{-1, 121})
            h.assertTrue(!roundTrip(h, tower(remaining, targets(1))).valid(), "out-of-range remaining rejected after decode");
        h.assertTrue(!roundTrip(h, tower(0, targets(1))).valid(), "clear cannot retain an enemy target");
        var first = targets(1).get(0);
        h.assertTrue(!roundTrip(h, tower(1, List.of(first,
            new TowerMarkPackets.Target(first.id(), new UUID(202, 2))))).valid(), "duplicate entity ID rejected");
        h.assertTrue(!roundTrip(h, tower(1, List.of(first,
            new TowerMarkPackets.Target(first.id() + 1, first.uuid())))).valid(), "duplicate UUID rejected");
        h.assertTrue(!roundTrip(h, tower(1, List.of(new TowerMarkPackets.Target(-1, first.uuid())))).valid(), "negative entity ID rejected");
        h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_building_abilities", template="dreamseekers_test_arena", batch=BATCH, timeoutTicks=40)
    public static void everyAbilitySnapshotAndEnvelopeRules(GameTestHelper h) {
        for (var kind : BuildingAbility.values()) {
            var sent = ability(kind, BuildingCodex.EMPTY, kind.durationTicks(), true, true);
            h.assertTrue(roundTrip(h, sent).valid(), "real " + kind + " full-duration snapshot accepted");
            h.assertTrue(!roundTrip(h, ability(kind, BuildingCodex.EMPTY, kind.durationTicks() + 1, true, true)).valid(),
                "active duration over frozen " + kind + " bound rejected");
            h.assertTrue(!roundTrip(h, ability(kind, BuildingCodex.EMPTY, -1, true, true)).valid(), "negative active ticks rejected");
        }
        h.assertTrue(!roundTrip(h, ability(BuildingAbility.ROOT_CROWN, BuildingCodex.EMPTY, 0, false, true)).valid(),
            "unsupported source cannot advertise an available action");
        h.assertTrue(roundTrip(h, ability(BuildingAbility.ROOT_CROWN, BuildingCodex.EMPTY, 0, false, false)).valid(),
            "unsupported/disabled source can safely explain its state");
        var one = new BuildingCodex.Page(0, 1, false, rows(1));
        h.assertTrue(!roundTrip(h, ability(BuildingAbility.ROOT_CROWN, one, 0, true, true)).valid(), "non-codex snapshot cannot carry roster rows");
        h.assertTrue(!roundTrip(h, ability(BuildingAbility.ROOT_CROWN,
            new BuildingCodex.Page(0, 1, false, List.of()), 0, true, true)).valid(), "non-codex snapshot cannot carry private roster total");
        h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_building_abilities", template="dreamseekers_test_arena", batch=BATCH, timeoutTicks=40)
    public static void codexMaxPageRowsHealthAndCareRoundTrip(GameTestHelper h) {
        h.assertTrue(BuildingCodex.PAGE_SIZE == 32 && BuildingCodex.MAX_ROWS == 256 && BuildingCodex.MAX_PAGE == 7,
            "frozen bounded roster is eight pages of at most 32 rows");
        var page = new BuildingCodex.Page(7, 256, true, rows(32));
        h.assertTrue(page.valid() && roundTrip(h, codex(page)).valid(), "last full page preserves all HP/care/ID/UUID/type fields");
        h.assertTrue(roundTrip(h, codex(new BuildingCodex.Page(7, 0, false, List.of()))).valid(),
            "roster shrinking does not silently clamp requested page, empty page remains valid");
        List<BuildingCodex.Row> mutable = rows(1);
        var copied = new BuildingCodex.Page(0, 1, false, mutable);
        mutable.clear();
        h.assertTrue(roundTrip(h, codex(copied)).codex().rows().size() == 1, "page defensively copies roster rows");
        h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_building_abilities", template="dreamseekers_test_arena", batch=BATCH, timeoutTicks=40)
    public static void codexHeaderBoundsRejectedBeforeRowRead(GameTestHelper h) {
        rejectsPageDecode(h, new BuildingCodex.Page(-1, 0, false, List.of()));
        rejectsPageDecode(h, new BuildingCodex.Page(8, 0, false, List.of()));
        rejectsPageDecode(h, new BuildingCodex.Page(0, -1, false, List.of()));
        rejectsPageDecode(h, new BuildingCodex.Page(0, 257, false, List.of()));
        rejectsPageDecode(h, new BuildingCodex.Page(0, 33, false, rows(33)));
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        boolean rejected = false;
        try {
            codex(BuildingCodex.EMPTY).encode(bytes);
            bytes.writerIndex(bytes.writerIndex() - 1); // Final zero row count, not a reimplemented header.
            bytes.writeVarInt(-1);
            try { new BuildingAbilityPackets.Snapshot(bytes); }
            catch (IllegalArgumentException error) {
                rejected = "Invalid codex page bounds".equals(error.getMessage());
            }
            h.assertTrue(rejected, "negative row count rejected before attempting a row read");
        } finally { bytes.release(); }
        h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_building_abilities", template="dreamseekers_test_arena", batch=BATCH, timeoutTicks=40)
    public static void codexSemanticRowsFailClosedAfterDecode(GameTestHelper h) {
        UUID uuid = new UUID(401, 1);
        List<BuildingCodex.Row> invalid = List.of(
            row(-1, uuid, 1, 2, 0), row(1, uuid, 0, 2, 0), row(1, uuid, -1, 2, 0),
            row(1, uuid, 3, 2, 0), row(1, uuid, 1, 0, 0), row(1, uuid, 1, -2, 0),
            row(1, uuid, Float.NaN, 2, 0), row(1, uuid, Float.POSITIVE_INFINITY, 2, 0),
            row(1, uuid, 1, Float.NaN, 0), row(1, uuid, 1, Float.POSITIVE_INFINITY, 0),
            row(1, uuid, 1, 2, -1), row(1, uuid, 1, 2, 4));
        for (var row : invalid)
            invalidPageRoundTrip(h, new BuildingCodex.Page(0, 1, false, List.of(row)), "invalid HP/care/ID row " + row);
        var first = row(1, uuid, 1, 2, 0);
        invalidPageRoundTrip(h, new BuildingCodex.Page(0, 2, false,
            List.of(first, row(1, new UUID(401, 2), 1, 2, 0))), "duplicate roster ID");
        invalidPageRoundTrip(h, new BuildingCodex.Page(0, 2, false,
            List.of(first, row(2, uuid, 1, 2, 0))), "duplicate roster UUID");
        invalidPageRoundTrip(h, new BuildingCodex.Page(0, 0, false, List.of(first)), "rows exceed total");
        invalidPageRoundTrip(h, new BuildingCodex.Page(7, 224, false, List.of(first)), "rows exceed this page's remaining total");
        h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_building_abilities", template="dreamseekers_test_arena", batch=BATCH, timeoutTicks=40)
    public static void requestIdentityPageAndActionRoundTrip(GameTestHelper h) {
        for (var action : BuildingAbilityPackets.Action.values()) for (int page : new int[]{0, 7}) {
            var sent = new BuildingAbilityPackets.Request(new BuildingAbilityPackets.Payload(action, DIMENSION, ORIGIN,
                VIEW, INSTANCE, 10, 11, 12, page), EPOCH, SESSION, action == BuildingAbilityPackets.Action.CAST ? 43 : 0);
            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                sent.encode(bytes);
                var received = new BuildingAbilityPackets.Request(bytes);
                h.assertTrue(sent.equals(received) && bytes.readableBytes() == 0,
                    "actual request codec retains action, nonce, source lifetime, page, epoch and session");
            } finally { bytes.release(); }
        }
        // Decoding is not authorization: server actor/session/sequence/source/economy gates are tested separately.
        h.succeed();
    }

    private static void schema(GameTestHelper h, Class<?> type, String[] names, Class<?>[] types) {
        RecordComponent[] components = type.getRecordComponents();
        h.assertTrue(components != null && components.length == names.length && types.length == names.length,
            type.getSimpleName() + " has exactly the frozen wire fields, no extras");
        for (int i = 0; i < names.length; i++)
            h.assertTrue(components[i].getName().equals(names[i]) && components[i].getType() == types[i],
                type.getSimpleName() + " field " + i + " is exactly " + names[i]);
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities", template="dreamseekers_test_arena", batch=BATCH, timeoutTicks=40)
    public static void schemasCarryNoEnemyCoordinatesOrClientPrices(GameTestHelper h) {
        schema(h, TowerMarkPackets.Target.class, new String[]{"id", "uuid"}, new Class<?>[]{int.class, UUID.class});
        schema(h, TowerMarkPackets.Snapshot.class,
            new String[]{"order", "session", "epoch", "owner", "dimension", "origin", "instance", "generation", "revision", "remaining", "targets"},
            new Class<?>[]{long.class, UUID.class, UUID.class, String.class, ResourceLocation.class, BlockPos.class,
                UUID.class, long.class, long.class, int.class, List.class});
        schema(h, BuildingCodex.Row.class,
            new String[]{"id", "uuid", "typeId", "hp", "maxHp", "careState", "careEligible"},
            new Class<?>[]{int.class, UUID.class, ResourceLocation.class, float.class, float.class, int.class, boolean.class});
        schema(h, BuildingCodex.Page.class,
            new String[]{"page", "totalRows", "truncated", "rows"},
            new Class<?>[]{int.class, int.class, boolean.class, List.class});
        schema(h, BuildingAbilityPackets.Snapshot.class,
            new String[]{"stream", "order", "session", "epoch", "owner", "dimension", "origin", "view", "instance", "capture",
                "generation", "revision", "ability", "cooldown", "activeTicks", "supported", "available", "reason", "status", "request", "codex"},
            new Class<?>[]{UUID.class, long.class, UUID.class, UUID.class, String.class, ResourceLocation.class, BlockPos.class,
                UUID.class, UUID.class, long.class, long.class, long.class, BuildingAbility.class, int.class, int.class,
                boolean.class, boolean.class, String.class, String.class, long.class, BuildingCodex.Page.class});
        schema(h, BuildingAbilityPackets.Payload.class,
            new String[]{"action", "dimension", "origin", "view", "instance", "capture", "generation", "revision", "page"},
            new Class<?>[]{BuildingAbilityPackets.Action.class, ResourceLocation.class, BlockPos.class, UUID.class,
                UUID.class, long.class, long.class, long.class, int.class});
        FriendlyByteBuf empty = new FriendlyByteBuf(Unpooled.buffer()), full = new FriendlyByteBuf(Unpooled.buffer());
        try {
            tower(120, List.of()).encode(empty);
            tower(120, targets(6)).encode(full);
            h.assertTrue(full.writerIndex() - empty.writerIndex() == 6 * (1 + 16),
                "six small positive IDs add exactly six VarInts and six UUIDs, zero target position bytes");
        } finally { empty.release(); full.release(); }
        h.succeed();
    }
}
