package com.solegendary.reignofnether.survival;

import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Four genuine world stops/starts share ONE synthetic world. The sidecar contains expectations
 * only and is written by a stop witness. Small fixture spawns avoid 610 population in the final
 * encounter; the actual dispatcher, scheduler, NBT loaders, player reward queue and entities run.
 */
@GameTestHolder("api65_repair2_survival")
@PrefixGameTestTemplate(false)
public final class SurvivalCheckpointRestartGameTests {
    private static final File WITNESS = new File("repair2-survival-stop-witness.nbt");
    private static final String OWNER = "Repair2Survivor";
    private static final BlockPos ANCHOR = new BlockPos(4096, 90, 4096);
    private static final BlockPos POS = ANCHOR.offset(12, 2, -5);
    private static final String PHASE = "api65.repair2.survival.phase";
    private static long jvm() { return ManagementFactory.getRuntimeMXBean().getStartTime(); }
    private static RTSPlayer player() { return PlayerServerEvents.getRTSPlayer(OWNER); }

    public record StopWitness(CompoundTag expected, String stage) {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void stopping(ServerStoppingEvent event) throws Exception {
            expected.putString("stage", stage);
            expected.putLong("jvm", jvm());
            NbtIo.writeCompressed(expected, WITNESS);
            System.out.println("REPAIR2_SURVIVAL_STOP stage=" + stage + " jvm=" + jvm());
        }
    }

    private static void finish(GameTestHelper h, CompoundTag expected, String stage) {
        MinecraftForge.EVENT_BUS.register(new StopWitness(expected, stage));
        h.succeed();
    }

    private static CompoundTag read(GameTestHelper h, String previous) throws Exception {
        h.assertTrue(WITNESS.isFile(), "preceding JVM must have reached normal ServerStoppingEvent");
        CompoundTag n = NbtIo.readCompressed(WITNESS);
        h.assertTrue(previous.equals(n.getString("stage")) && jvm() != n.getLong("jvm"),
                "not the preceding process or a stale out-of-order fixture: " + previous);
        h.assertTrue(SurvivalServerEvents.isEnabled() && Wave.randomSeed == n.getLong("seed")
                        && SurvivalServerEvents.getDifficulty() == WaveDifficulty.HARD,
                "ordinary ServerStartedEvent bound the same enabled mode, seed and difficulty");
        h.assertTrue(player() != null && SurvivalServerEvents.isStarted(), "ordinary player and building restore");
        h.getLevel().getChunkAt(POS);
        return n;
    }

    private static void checkDraws(GameTestHelper h, CompoundTag n, int draws) {
        RTSPlayer p = player();
        h.assertTrue(p.survivalRewardDraws == draws && p.itemDropQueue.size() == n.getInt("queueSize") - draws,
                "one persisted queue advance per settled wave; actual=" + p.survivalRewardDraws + "/" + p.itemDropQueue.size());
        h.assertTrue(p.itemsDropped == 0 && p.creepScore == 0, "no fabricated creep rewards or harder thresholds");
        h.assertTrue(p.itemDropQueue.getFirst().getItem().getDescriptionId().equals(n.getString("item" + draws)),
                "next deterministic reward identity remains aligned at draw " + draws);
    }

    /** Override only composition for this dispatch. No synthetic cursor or in-memory save record. */
    private static UUID issue(GameTestHelper h, int number) {
        var level = h.getLevel();
        SurvivalServerEvents.setWaveNumber(number);
        var faction = SurvivalServerEvents.nextWave.faction;
        var ordinary = faction.spawnWave;
        var id = new AtomicReference<UUID>();
        try {
            faction.spawnWave = (world, wave) -> {
                var body = EntityRegistrar.VILLAGER_UNIT.get().create(world);
                body.setOwnerName(SurvivalServerEvents.ENEMY_OWNER_NAME);
                body.setNoAi(true); body.setNoGravity(true); body.setPersistenceRequired(); body.setInvulnerable(true);
                body.moveTo(POS.getX() + 0.5, POS.getY(), POS.getZ() + 0.5);
                h.assertTrue(world.addFreshEntity(body), "published a real persistent enemy");
                id.set(body.getUUID());
            };
            SurvivalServerEvents.startNextWave(level);
        } finally {
            faction.spawnWave = ordinary;
        }
        h.assertTrue(id.get() != null && SurvivalServerEvents.currentWave != null && SurvivalServerEvents.currentWave.number == number
                        && SurvivalServerEvents.getCurrentEnemies().size() == 1,
                "actual wave dispatcher installed active state and exactly one fixture body");
        return id.get();
    }

    private static void kill(ServerLevel level, UUID id) {
        LivingEntity body = (LivingEntity) level.getEntity(id);
        body.setInvulnerable(false);
        body.kill();
    }

    private static void checkReward(GameTestHelper h, CompoundTag n, int index) {
        var matches = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(POS).inflate(5),
                e -> e.isAlive() && e.getItem().getItem().getDescriptionId().equals(n.getString("item" + index)));
        h.assertTrue(matches.size() == 1, "one genuine world item for reward " + index + ", actual=" + matches.size());
    }

    @GameTest(templateNamespace="api65_repair2_survival", template="ore_mine_test_arena", timeoutTicks=400)
    public static void cleanProcessRestartSeparatesActiveSettledAndFinal(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        switch (System.getProperty(PHASE, "")) {
            case "prepare" -> {
                h.assertTrue(!WITNESS.exists() && !SurvivalServerEvents.isEnabled() && player() == null, "fresh test world");
                // Fixed daytime keeps unrequested nightfall out of a persistence test.
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                level.setDayTime(6000);
                var p = RTSPlayer.getNewPlayer(OWNER, Factions.VILLAGERS, 17003, 1, true, 71L, 19L);
                PlayerServerEvents.rtsPlayers.add(p);
                for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++)
                    level.setBlock(POS.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                level.setChunkForced(POS.getX() >> 4, POS.getZ() >> 4, true);
                for (BuildingBlock b : BuildingUtils.getAbsoluteBlockData(Buildings.BLACKSMITH.getRelativeBlockData(level), level, ANCHOR, Rotation.NONE))
                    level.setBlock(b.getBlockPos(), b.getBlockState(), 2);
                var anchor = BuildingUtils.getNewBuildingPlacement(Buildings.BLACKSMITH, level, ANCHOR, Rotation.NONE, OWNER, false);
                BuildingServerEvents.getBuildings().add(anchor); anchor.onBuilt();
                SurvivalServerEvents.enable(WaveDifficulty.HARD);
                var n = new CompoundTag();
                n.putLong("seed", Wave.randomSeed);
                n.putInt("queueSize", p.itemDropQueue.size());
                h.assertTrue(p.itemDropQueue.size() >= 3, "non-empty native deterministic reward sequence");
                var list = p.itemDropQueue.stream().limit(3).toList();
                for (int i = 0; i < list.size(); i++) n.putString("item" + i, list.get(i).getItem().getDescriptionId());
                n.putUUID("enemy", issue(h, 3));
                SurvivalServerEvents.waveCleared(level);
                h.assertTrue(SurvivalServerEvents.cursor().equals(new WaveCursor(4, 3, false)), "cannot settle a living wave");
                checkDraws(h, n, 0); finish(h, n, "active");
            }
            case "verify" -> {
                var n = read(h, "active");
                UUID id = n.getUUID("enemy");
                h.assertTrue(SurvivalServerEvents.cursor().equals(new WaveCursor(4, 3, false)), "restore active 3, upcoming 4");
                SurvivalServerEvents.waveCleared(level);
                h.assertTrue(SurvivalServerEvents.isWaveInProgress(), "unresolved/loading cannot be mistaken for killed");
                checkDraws(h, n, 0);
                h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getEntity(id) instanceof LivingEntity e
                                && e.isAlive() && SurvivalServerEvents.getCurrentEnemies().size() == 1
                                && SurvivalServerEvents.getCurrentEnemies().get(0).getEntity() == e,
                        "one original surviving enemy UUID joined, no replacement"))
                        .thenExecute(() -> kill(level, id))
                        .thenWaitUntil(() -> h.assertTrue(!SurvivalServerEvents.isWaveInProgress(), "ordinary scheduler settled the last kill"))
                        .thenExecute(() -> {
                            h.assertTrue(SurvivalServerEvents.cursor().equals(new WaveCursor(4, 0, false)), "next pending = 4");
                            SurvivalServerEvents.waveCleared(level); checkDraws(h, n, 1); checkReward(h, n, 0);
                            finish(h, n, "settled");
                        });
            }
            case "verify_settled" -> {
                var n = read(h, "settled");
                h.assertTrue(SurvivalServerEvents.cursor().equals(new WaveCursor(4, 0, false)), "cleared 3 cannot restart as 3");
                SurvivalServerEvents.waveCleared(level); checkDraws(h, n, 1);
                UUID id = issue(h, WaveCursor.LAST);
                SurvivalServerEvents.startNextWave(level);
                h.assertTrue(SurvivalServerEvents.getCurrentEnemies().size() == 1
                                && SurvivalServerEvents.getCurrentEnemies().get(0).getEntity().getUUID().equals(id)
                                && SurvivalSaveData.getInstance(level).activeEnemies.equals(java.util.Set.of(id)),
                        "only the original final enemy may be outstanding, including while its chunk becomes visible");
                h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getEntity(id) instanceof LivingEntity body && body.isAlive(),
                                "the genuinely published final enemy appears in the normal UUID lookup"))
                        .thenExecute(() -> kill(level, id))
                        .thenWaitUntil(() -> h.assertTrue(SurvivalServerEvents.cursor().finished(), "final wave settles"))
                        .thenExecute(() -> {
                            SurvivalServerEvents.waveCleared(level); checkDraws(h, n, 2); checkReward(h, n, 1);
                            finish(h, n, "terminal");
                        });
            }
            case "verify_final" -> {
                var n = read(h, "terminal");
                h.assertTrue(SurvivalServerEvents.cursor().equals(new WaveCursor(30, 0, true)), "terminal is not pending 30");
                SurvivalServerEvents.startNextWave(level); SurvivalServerEvents.waveCleared(level);
                h.assertTrue(SurvivalServerEvents.getCurrentEnemies().isEmpty() && !SurvivalServerEvents.isWaveInProgress()
                        && SurvivalServerEvents.isEnabled(), "no repeat wave, mode remains enabled for beacon route");
                checkDraws(h, n, 2);
                h.startSequence().thenWaitUntil(() -> {
                    checkReward(h, n, 0); checkReward(h, n, 1);
                }).thenExecute(() -> finish(h, n, "verified"));
            }
            default -> throw new IllegalArgumentException("Select prepare|verify|verify_settled|verify_final");
        }
    }
}
