package com.dreamseekers;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.data.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;

/** Mixed delivery on two real workers, then two ordinary server stops and loads in fresh JVMs.
 * The oracle stores only identities/phase markers, never replacement entities or balances. */
@GameTestHolder("api65_repair2_cargo")
@PrefixGameTestTemplate(false)
public final class Repair2CargoRestartGameTests {
    private static final File ORACLE = new File("repair2-cargo-identities.nbt");
    private static final String[] OWNERS = {"R2CargoVillager", "R2CargoFirefly"};
    private static final String NATIVE_KEY = "RoNVillagerUnvaluedCargo";

    @GameTest(template = "unit_isolation_arena", batch = "repair2_cargo_restart", timeoutTicks = 650)
    public static void mixedDeliveryThenTwoOrdinaryRestarts(GameTestHelper h) throws Exception {
        switch (System.getProperty("api65.repair2.cargo.phase", "")) {
            case "prepare" -> prepare(h);
            case "verify" -> verify(h, false);
            case "verify_settled" -> verify(h, true);
            default -> throw new IllegalArgumentException("Run prepare, verify, verify_settled in one isolated world");
        }
    }

    private static List<ItemStack> kept(int side) {
        // Two distinct stacks of the same kind must not merge or lose their tags.
        var first = new ItemStack(Items.GOLDEN_APPLE, 3 + side);
        first.setHoverName(Component.literal("归途 " + OWNERS[side]));
        first.getOrCreateTagElement("Fixture").putIntArray("route", new int[]{side, 37, -5});
        var second = new ItemStack(Items.GOLDEN_APPLE, 6 - side);
        second.getOrCreateTag().putString("marker", "second-" + OWNERS[side]);
        // Unlike the apples, soul sand has a real ResourceSource, worth zero.
        return List.of(first, new ItemStack(Items.SOUL_SAND, 2 + side), second);
    }

    private static List<ItemStack> priced(int n) {
        return List.of(new ItemStack(Items.WHEAT, n), new ItemStack(Items.STICK, n + 1),
                new ItemStack(Items.STONE, n + 2), new ItemStack(Items.EMERALD, n + 3));
    }

    private static ListTag encode(List<ItemStack> stacks) {
        ListTag tag = new ListTag();
        for (ItemStack s : stacks) tag.add(s.save(new CompoundTag()));
        return tag;
    }

    private static void checkKept(GameTestHelper h, Unit u, int side, String step) {
        h.assertTrue(encode(u.getItems()).equals(encode(kept(side))),
                step + ": exact item NBT, count, order, and no priced stacks on " + OWNERS[side] + ": " + u.getItems());
    }

    private static void checkBank(GameTestHelper h, int i, boolean reloaded) {
        Resources bank = ExtendedResourcesSync.account(OWNERS[i]);
        int base = 100 + 50 * i;
        int delivered = 1 + i, pending = 3 + i;
        h.assertTrue(bank != null && bank.food == base + delivered * 4 + (reloaded ? pending * 4 : 0)
                        && bank.wood == base * 2 + delivered + 1 + (reloaded ? pending + 1 : 0)
                        && bank.ore == base * 3 + delivered + 2 + (reloaded ? pending + 2 : 0)
                        && bank.emerald == base * 4 + delivered + 3 + (reloaded ? pending + 3 : 0),
                "owner-specific food/wood/ore/emerald include each delivery and pending stack exactly once: " + OWNERS[i]);
    }

    // This isolated codec probe is not evidence of a server restart. The separate phases below are.
    private static void checkNativeCompatibility(GameTestHelper h) throws Exception {
        var probe = EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());
        var cargo = probe.getItems();
        cargo.addAll(kept(0));
        cargo.addAll(priced(3));
        cargo.add(ItemStack.EMPTY);
        CompoundTag tag = new CompoundTag();
        ListTag original = encode(cargo);
        probe.addAdditionalSaveData(tag);
        h.assertTrue(encode(cargo).equals(original), "ordinary save leaves every live stack untouched");
        ListTag saved = tag.getList(NATIVE_KEY, Tag.TAG_COMPOUND);
        h.assertTrue(saved.equals(encode(kept(0))), "entity NBT does not duplicate the account's priced items");
        saved.add(new ItemStack(Items.EMERALD, 55).save(new CompoundTag()));
        saved.add(ItemStack.EMPTY.save(new CompoundTag()));
        // Real loads below use fresh objects. Exercise repeated reads of this edited tag too.
        var reader = EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());
        reader.readAdditionalSaveData(tag);
        reader.readAdditionalSaveData(tag);
        checkKept(h, reader, 0, "repeated load rejects dirty priced and empty entries");
        tag.remove(NATIVE_KEY);
        reader.readAdditionalSaveData(tag);
        h.assertTrue(reader.getItems().isEmpty(), "legacy native NBT without cargo creates no items");
        tag.putString(NATIVE_KEY, "not a list");
        reader.readAdditionalSaveData(tag);
        h.assertTrue(reader.getItems().isEmpty(), "wrong NBT type creates no items");

        // Vanilla /data merge uses EntityDataAccessor.setData to reload the admitted entity.
        // An unrelated edit must not erase, pack or shuffle its still-live priced cargo.
        var unpaid = priced(2);
        var extras = kept(0);
        cargo.clear();
        for (int i = 0; i < unpaid.size(); i++) {
            cargo.add(unpaid.get(i));
            if (i < extras.size()) cargo.add(extras.get(i));
        }
        ListTag beforeMerge = encode(cargo);
        BlockPos p = h.absolutePos(new BlockPos(2, 2, 2));
        probe.moveTo(p.getX() + .5, p.getY(), p.getZ() + .5);
        h.assertTrue(h.getLevel().addFreshEntity(probe), "admitted native /data target");
        try {
            var accessor = new EntityDataAccessor(probe);
            CompoundTag edited = accessor.getData();
            edited.putBoolean("Silent", true);
            accessor.setData(edited.copy());
            accessor.setData(edited.copy());
            h.assertTrue(probe.isSilent() && encode(cargo).equals(beforeMerge)
                    && cargo.get(0) == unpaid.get(0) && cargo.get(6) == unpaid.get(3),
                    "repeated unrelated live edits preserve existing slots, order, values, counts and tags");
            var replacement = kept(1).get(0);
            edited.put(NATIVE_KEY, encode(List.of(replacement, new ItemStack(Items.EMERALD, 63))));
            accessor.setData(edited);
            h.assertTrue(encode(cargo).equals(encode(List.of(unpaid.get(0), replacement, unpaid.get(1),
                            unpaid.get(2), unpaid.get(3)))),
                    "targeted live edit replaces old unpriced slots without importing a forged priced stack");
        } finally {
            probe.discard();
        }
    }

    private static BuildingPlacement depot(ServerLevel level, BlockPos p, int i) {
        Building type = i == 0 ? Buildings.OAK_STOCKPILE : DreamseekersMod.DREAM_DEPOT;
        for (int x = -2; x < 18; x++) for (int z = -2; z < 18; z++)
            level.setBlock(p.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
        for (var block : BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(level), level, p, Rotation.NONE))
            level.setBlock(block.getBlockPos(), block.getBlockState(), 2);
        var b = BuildingUtils.getNewBuildingPlacement(type, level, p, Rotation.NONE, OWNERS[i], false);
        BuildingServerEvents.getBuildings().add(b);
        b.onBuilt();
        return b;
    }

    private static void prepare(GameTestHelper h) throws Exception {
        h.assertTrue(!ORACLE.exists(), "use a fresh isolated run suffix");
        checkNativeCompatibility(h);
        ServerLevel level = h.getLevel();
        CompoundTag ids = new CompoundTag();
        ids.putLong("prepareJvm", jvmStart());
        for (int i = 0; i < OWNERS.length; i++) {
            h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNERS[i]) == null && ExtendedResourcesSync.account(OWNERS[i]) == null,
                    "do not adopt a pre-existing owner or account");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNERS[i], i == 0 ? Factions.VILLAGERS : DreamseekersMod.DREAMSEEKERS));
            int base = 100 + 50 * i;
            ResourcesServerEvents.resourcesList.add(new Resources(OWNERS[i], base, 2 * base, 3 * base, 4 * base));
            BlockPos p = new BlockPos(2644 + 48 * i, 150, 2644);
            BlockPos stand = p.offset(-1, 0, -1);
            ChunkPos chunk = new ChunkPos(stand);
            h.assertTrue(!level.getForcedChunks().contains(chunk.toLong()), "test owns only its new forced chunk");
            level.setChunkForced(chunk.x, chunk.z, true);
            ids.putLong("chunk" + i, chunk.toLong());
            BuildingPlacement depot = depot(level, p, i);
            Mob body = i == 0 ? EntityRegistrar.VILLAGER_UNIT.get().create(level) : DreamseekersMod.FIREFLY_WORKER.get().create(level);
            Unit u = (Unit) body;
            u.setOwnerName(OWNERS[i]);
            body.moveTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
            body.setPersistenceRequired();
            body.setInvulnerable(true);
            h.assertTrue(level.addFreshEntity(body), "real worker admitted");
            ids.putUUID("body" + i, body.getUUID());
            u.getItems().addAll(kept(i));
            u.getItems().addAll(1, priced(1 + i));
            var goal = u.getReturnResourcesGoal();
            goal.setBuildingTarget(depot);
            goal.depositItems();
            checkBank(h, i, false);
            checkKept(h, u, i, "live delivery");
            // The native eating timer would consume the tagged apples after 40 ticks. Freeze only
            // the two fixture bodies, not serialization, entity admission, or the economy save events.
            body.canUpdate(false);
            u.getItems().addAll(1, priced(3 + i));
        }
        NbtIo.writeCompressed(ids, ORACLE);
        System.out.println("REPAIR2_CARGO_PREPARED native=1 firefly=1 liveDeliveries=2 pendingDeliveries=2");
        h.succeed(); // no manual saveResources, ledger restore or entity save/load for these two bodies
    }

    private static long jvmStart() { return ManagementFactory.getRuntimeMXBean().getStartTime(); }

    private static void verify(GameTestHelper h, boolean settled) throws IOException {
        CompoundTag ids = NbtIo.readCompressed(ORACLE);
        h.assertTrue(ids.contains("prepareJvm") && ids.getLong("prepareJvm") != jvmStart()
                        && ids.contains("verifyJvm") == settled && (!settled || ids.getLong("verifyJvm") != jvmStart()),
                "verify followed by verify_settled in two JVMs distinct from prepare");
        ServerLevel level = h.getLevel();
        for (int i = 0; i < OWNERS.length; i++) {
            ChunkPos c = new ChunkPos(ids.getLong("chunk" + i));
            h.assertTrue(level.getForcedChunks().contains(c.toLong()), "restored ordinary chunk ticket");
            level.getChunk(c.x, c.z);
        }
        h.startSequence().thenWaitUntil(() -> {
            for (int i = 0; i < OWNERS.length; i++)
                h.assertTrue(level.getEntity(ids.getUUID("body" + i)) != null, "wait for normal entity storage");
        }).thenExecute(() -> {
            for (int i = 0; i < OWNERS.length; i++) {
                var body = level.getEntity(ids.getUUID("body" + i));
                h.assertTrue(body.getType() == (i == 0 ? EntityRegistrar.VILLAGER_UNIT.get() : DreamseekersMod.FIREFLY_WORKER.get())
                                && body instanceof Unit u && OWNERS[i].equals(u.getOwnerName()), "same body type/owner/UUID after restart");
                checkKept(h, (Unit) body, i, "ordinary restarted entity");
                checkBank(h, i, true);
            }
            if (!settled) {
                ids.putLong("verifyJvm", jvmStart());
                try { NbtIo.writeCompressed(ids, ORACLE); }
                catch (IOException e) { throw new IllegalStateException("cannot mark first verified restart", e); }
                // Leave the actual entities and accounts for the second ordinary shutdown.
            }
            System.out.println("REPAIR2_CARGO_" + (settled ? "SECOND" : "FIRST") + "_RESTART_OK native=1 firefly=1 balances=stable");
        }).thenSucceed();
    }
}
