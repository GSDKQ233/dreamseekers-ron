package com.dreamseekers;

import com.dreamseekers.unit.DreamRtsUnit;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesSaveData;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Isolated prepare/verify runs must use different JVMs and the same test world. No manual entity/account load. */
@GameTestHolder("api65_addon_restart")
@PrefixGameTestTemplate(false)
public final class Api65DimensionCargoRestartGameTests {
    private static final String OWNER = "A65Dimension";
    private static final File MANIFEST = new File("api65-addon-dimension-cargo-expectations.nbt");

    @GameTest(template="unit_isolation_arena", batch="api65_addon_restart", timeoutTicks=600)
    public static void realDimensionTransferThenOrdinaryProcessRestartSettlesOnce(GameTestHelper h) throws Exception {
        String phase = System.getProperty("api65.addon.restart.phase", "");
        if (phase.equals("prepare")) prepare(h);
        else if (phase.equals("verify")) verify(h);
        else throw new IllegalArgumentException("Set api65.addon.restart.phase=prepare or verify in an isolated world");
    }

    private static void prepare(GameTestHelper h) throws Exception {
        h.assertTrue(!MANIFEST.exists() && PlayerServerEvents.getRTSPlayer(OWNER) == null, "fresh restart fixture only");
        ServerLevel destination = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(destination != null, "real second dimension");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER, DreamseekersMod.DREAMSEEKERS));
        Resources account = new Resources(OWNER, 1000, 2000, 3000, 4000);
        ResourcesServerEvents.resourcesList.add(account);
        CompoundTag expected = new CompoundTag();
        expected.putLong("jvmStart", java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        expected.putInt("food", account.food); expected.putInt("wood", account.wood);
        expected.putInt("ore", account.ore); expected.putInt("emerald", account.emerald);
        var chunks = new LinkedHashSet<Long>();
        ListTag bodies = new ListTag();
        List<EntityType<? extends Mob>> types = List.of(DreamseekersMod.FIREFLY_WORKER.get(), DreamseekersMod.STONE_SPIRIT_WORKER.get());
        var sources = new ArrayList<Mob>();
        var targets = new ArrayList<Entity>();
        var positions = new ArrayList<Vec3>();
        for (int i=0; i<types.size(); i++) {
            Mob source = types.get(i).create(h.getLevel());
            DreamRtsUnit unit = (DreamRtsUnit)source;
            unit.setOwnerName(OWNER);
            BlockPos start = h.absolutePos(new BlockPos(4+i*3, 4, 4));
            source.moveTo(start.getX()+.5, start.getY(), start.getZ()+.5, 0, 0);
            source.setNoAi(true); source.setNoGravity(true); source.setInvulnerable(true);
            h.assertTrue(h.getLevel().addFreshEntity(source), "original worker admitted");
            unit.getItems().addAll(List.of(new ItemStack(Items.WHEAT,2+i), new ItemStack(Items.OAK_LOG,3+i),
                    new ItemStack(Items.RAW_IRON,4+i), new ItemStack(Items.EMERALD,5+i), new ItemStack(Items.AMETHYST_SHARD,7+i)));
            Vec3 pos = new Vec3(19008+i*32+.5, 200, 19008.5);
            ChunkPos chunk = new ChunkPos(BlockPos.containing(pos));
            h.assertTrue(!destination.getForcedChunks().contains(chunk.toLong()), "do not adopt another fixture ticket");
            destination.setChunkForced(chunk.x, chunk.z, true); chunks.add(chunk.toLong());
            sources.add(source); positions.add(pos);
        }
        h.startSequence().thenWaitUntil(() -> {
            for (Mob source : sources)
                h.assertTrue(source.tickCount > 0 && h.getLevel().getEntity(source.getUUID()) == source
                        && h.getLevel().getEntity(source.getId()) == source, "ordinary source publication before real travel");
        }).thenExecute(() -> {
            for (int i=0; i<sources.size(); i++) {
                Mob source = sources.get(i);
                Resources cargo = Resources.getTotalResourcesFromItems(((DreamRtsUnit)source).getItems());
                h.assertTrue(cargo.food>0 && cargo.wood>0 && cargo.ore>0 && cargo.emerald>0, "all currencies before transfer");
                Entity moved = source.changeDimension(destination, Api65AddonRegressionGameTests.teleporter(positions.get(i)));
                System.out.println("API65_RESTART_DIMENSION_AFTER_RETURN " + Api65AddonRegressionGameTests.transferDiagnostic(source,moved,destination));
                h.assertTrue(moved instanceof DreamRtsUnit && moved != source && moved.getUUID().equals(source.getUUID())
                        && moved.level() == destination && moved.isAddedToWorld() && !moved.isRemoved(),
                        "actual same-UUID clone admitted by real destination entity manager");
                targets.add(moved);
                Api65AddonRegressionGameTests.sameValue(h, Resources.getTotalResourcesFromItems(((DreamRtsUnit)moved).getItems()), cargo,
                        "before shutdown cargo preserved");
                expected.putInt("food", expected.getInt("food")+cargo.food);
                expected.putInt("wood", expected.getInt("wood")+cargo.wood);
                expected.putInt("ore", expected.getInt("ore")+cargo.ore);
                expected.putInt("emerald", expected.getInt("emerald")+cargo.emerald);
                CompoundTag body = new CompoundTag(); body.putUUID("uuid", moved.getUUID()); body.putInt("nonValued",7+i);
                body.putString("type", net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(moved.getType()).toString());
                bodies.add(body);
            }
        }).thenWaitUntil(() -> {
            for (Entity target : targets)
                h.assertTrue(destination.getEntity(target.getUUID()) == target && destination.getEntity(target.getId()) == target,
                        "ordinary destination tracking publication before save");
        }).thenExecute(() -> {
            expected.put("bodies", bodies); expected.putLongArray("chunks", chunks.stream().mapToLong(Long::longValue).toArray());
            for (int i=0; i<2; i++) {
                ResourcesServerEvents.saveResources(h.getLevel());
                var saved = ResourcesSaveData.getInstance(h.getLevel()).resources.stream().filter(r -> OWNER.equals(r.ownerName)).findFirst().orElseThrow();
                checkLedger(h, saved, expected);
            }
            try { NbtIo.writeCompressed(expected, MANIFEST); }
            catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            System.out.println("API65_DIMENSION_CARGO_PREPARED bodies="+bodies.size());
        }).thenSucceed(); // Normal server stop and actual world entity persistence own the transition.
    }

    private static void verify(GameTestHelper h) throws Exception {
        h.assertTrue(MANIFEST.isFile(), "prepare expectations exist");
        CompoundTag expected = NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getLong("jvmStart") != java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(), "different genuine JVM");
        ServerLevel destination = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(destination != null, "restored second dimension");
        for (long packed : expected.getLongArray("chunks")) {
            ChunkPos chunk = new ChunkPos(packed);
            h.assertTrue(destination.getForcedChunks().contains(packed), "ordinary forced-chunk data persisted");
            destination.getChunk(chunk.x, chunk.z);
        }
        ListTag bodies = expected.getList("bodies", 10);
        h.startSequence().thenWaitUntil(() -> {
            for (int i=0; i<bodies.size(); i++)
                h.assertTrue(destination.getEntity(bodies.getCompound(i).getUUID("uuid")) instanceof DreamRtsUnit,
                        "ordinary chunk/entity load restores original UUID");
        }).thenExecute(() -> {
            var account = ResourcesServerEvents.resourcesList.stream().filter(r -> OWNER.equals(r.ownerName)).findFirst().orElseThrow();
            checkLedger(h, account, expected);
            for (int i=0; i<bodies.size(); i++) {
                CompoundTag wanted = bodies.getCompound(i);
                Entity body = destination.getEntity(wanted.getUUID("uuid"));
                var unit = (DreamRtsUnit)body;
                h.assertTrue(wanted.getString("type").equals(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(body.getType()).toString()), "real registry type retained");
                h.assertTrue(OWNER.equals(unit.getOwnerName()), "owner retained");
                Api65AddonRegressionGameTests.sameValue(h, Resources.getTotalResourcesFromItems(unit.getItems()), new Resources("",0,0,0,0),
                        "restart does not duplicate ledger-settled cargo");
                h.assertTrue(unit.getItems().stream().filter(s -> s.is(Items.AMETHYST_SHARD)).mapToInt(ItemStack::getCount).sum() == wanted.getInt("nonValued"),
                        "non-valued cargo survives normal persistence once");
                body.discard();
            }
            for (long packed : expected.getLongArray("chunks")) { var chunk = new ChunkPos(packed); destination.setChunkForced(chunk.x,chunk.z,false); }
            PlayerServerEvents.rtsPlayers.removeIf(p -> OWNER.equals(p.name));
            ResourcesServerEvents.resourcesList.removeIf(r -> OWNER.equals(r.ownerName));
            System.out.println("API65_DIMENSION_CARGO_VERIFIED bodies="+bodies.size());
        }).thenSucceed();
    }

    private static void checkLedger(GameTestHelper h, Resources account, CompoundTag expected) {
        Api65AddonRegressionGameTests.sameValue(h, account, new Resources("",expected.getInt("food"),expected.getInt("wood"),
                expected.getInt("ore"),expected.getInt("emerald")), "ordinary saved/reloaded bank contains original balance plus cargo once");
    }
}
