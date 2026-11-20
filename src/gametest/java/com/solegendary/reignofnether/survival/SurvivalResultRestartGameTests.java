package com.solegendary.reignofnether.survival;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.neutral.Beacon;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Three clean JVMs sharing only the dedicated test world. Fixture controls wave composition
 * and advances the beacon clock to its threshold; all decisions/publications/saves are production. */
@GameTestHolder("api65_repair3_survival_result")
@PrefixGameTestTemplate(false)
public final class SurvivalResultRestartGameTests {
    private static final String A = "R3DefenderA", B = "R3DefenderB", C = "R3Eliminated", VIEWER = "R3Observer";
    private static final BlockPos BASE = new BlockPos(3584, 94, 3584), POS = BASE.offset(13, 2, -3);
    private static final File WITNESS = new File("repair3-survival-result-stop.nbt");
    private static long jvm() { return ManagementFactory.getRuntimeMXBean().getStartTime(); }
    public record StopWitness(String phase, CompoundTag expected) {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void stopping(ServerStoppingEvent event) throws Exception {
            expected.putString("phase", phase);
            expected.putLong("jvm", jvm());
            NbtIo.writeCompressed(expected, WITNESS);
        }
    }
    private static void stop(GameTestHelper h, String phase, CompoundTag expected) {
        MinecraftForge.EVENT_BUS.register(new StopWitness(phase, expected)); h.succeed();
    }
    private static CompoundTag loadWitness(GameTestHelper h, String preceding) throws Exception {
        h.assertTrue(WITNESS.isFile(), "previous clean stop was observed");
        var n = NbtIo.readCompressed(WITNESS);
        h.assertTrue(preceding.equals(n.getString("phase")) && n.getLong("jvm") != jvm(), "fresh JVM in correct phase");
        h.getLevel().getChunkAt(POS);
        return n;
    }

    private static RTSPlayer seat(String name, int id, long seed) {
        var seat = RTSPlayer.getNewPlayer(name, Factions.VILLAGERS, id, 1, true, seed, 7L);
        PlayerServerEvents.rtsPlayers.add(seat);
        return seat;
    }
    private static RTSPlayer seat(String name) { return PlayerServerEvents.getRTSPlayer(name); }
    private static void resetRoster() {
        PlayerServerEvents.rtsPlayers.clear(); PlayerServerEvents.postGameRtsPlayers.clear();
        AlliancesServerEvents.resetAllAlliances();
    }
    private static void anchor(GameTestHelper h) {
        var level = h.getLevel();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.setDayTime(6000);
        level.setChunkForced(POS.getX() >> 4, POS.getZ() >> 4, true);
        for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++)
            level.setBlock(POS.offset(x,-1,z), Blocks.STONE.defaultBlockState(), 2);
        for (var block : BuildingUtils.getAbsoluteBlockData(Buildings.BLACKSMITH.getRelativeBlockData(level),
                level, BASE, Rotation.NONE)) level.setBlock(block.getBlockPos(), block.getBlockState(), 2);
        var base = BuildingUtils.getNewBuildingPlacement(Buildings.BLACKSMITH, level, BASE, Rotation.NONE, A, false);
        BuildingServerEvents.getBuildings().add(base); base.onBuilt();
    }
    private static LivingEntity issue(GameTestHelper h, int number) {
        var level = h.getLevel(); SurvivalServerEvents.setWaveNumber(number);
        var faction = SurvivalServerEvents.nextWave.faction;
        var ordinary = faction.spawnWave; var result = new AtomicReference<LivingEntity>();
        try {
            faction.spawnWave = (world, wave) -> {
                var enemy = EntityRegistrar.VILLAGER_UNIT.get().create(world);
                enemy.setOwnerName(SurvivalServerEvents.ENEMY_OWNER_NAME); enemy.setNoAi(true);
                enemy.setNoGravity(true); enemy.setPersistenceRequired(); enemy.setInvulnerable(true);
                enemy.moveTo(POS.getX()+0.5, POS.getY(), POS.getZ()+0.5);
                h.assertTrue(world.addFreshEntity(enemy), "accepted one ordinary enemy"); result.set(enemy);
            };
            SurvivalServerEvents.startNextWave(level);
        } finally { faction.spawnWave = ordinary; }
        h.assertTrue(result.get()!=null && SurvivalServerEvents.isWaveInProgress(), "fixture dispatch committed");
        return result.get();
    }
    /** The first seat succeeds. A normal Forge listener vetoes the next seat and its retries in this JVM. */
    public static final class VetoSecondDrop {
        int vetoes;
        private final net.minecraft.world.item.Item denied;
        VetoSecondDrop(net.minecraft.world.item.Item denied) { this.denied=denied; }
        @SubscribeEvent public void joining(EntityJoinLevelEvent event) {
            if (!event.loadedFromDisk() && event.getEntity() instanceof ItemEntity e && e.getItem().is(denied)
                    && new AABB(POS).inflate(4).contains(e.position())) { vetoes++; event.setCanceled(true); }
        }
    }
    public static final class FinishOnDrop {
        ItemEntity candidate;
        @SubscribeEvent public void joining(EntityJoinLevelEvent event) {
            if (!event.loadedFromDisk() && event.getEntity() instanceof ItemEntity e && new AABB(POS).inflate(4).contains(e.position())) {
                candidate=e;
                PlayerServerEvents.beaconVictory(A);
            }
        }
    }
    /** Native Forge accepts a fresh item, but its listener officially ends the match before
     * addFreshEntity returns. No late item, draw advancement or replacement may escape. */
    private static void reentrantFinishCancelsPublication(GameTestHelper h) {
        var seat = seat(A,18001,25); var level=h.getLevel();
        SurvivalServerEvents.enable(WaveDifficulty.EASY);
        var claim=net.minecraft.world.phys.Vec3.atCenterOf(POS);
        seat.pendingSurvivalRewards.addLast(claim);
        var head=seat.itemDropQueue.getFirst();
        var watcher=new FinishOnDrop(); MinecraftForge.EVENT_BUS.register(watcher);
        try {
            h.assertTrue(!com.solegendary.reignofnether.items.ItemServerEvents.redeemNextSurvivalReward(seat,level,claim),
                    "a reentrant beacon invalidates a previously authorized draw");
            h.assertTrue(SurvivalResult.isAfterMatch() && watcher.candidate!=null && watcher.candidate.isRemoved()
                            && seat.itemDropQueue.getFirst()==head && seat.survivalRewardDraws==0 && seat.pendingSurvivalRewards.isEmpty(),
                    "terminal cutoff discards only the new item, consumes no draw and cancels the outstanding claim");
        } finally { MinecraftForge.EVENT_BUS.unregister(watcher); }
        SurvivalServerEvents.reset(); resetRoster();
    }
    private static void assertFinal(GameTestHelper h, CompoundTag n) {
        var result = SurvivalResult.current();
        h.assertTrue(result.kind() == SurvivalResult.Kind.BEACON_VICTORY && result.winners().equals(Set.of(A, B))
                        && result.elapsed() == n.getLong("elapsed") && PlayerServerEvents.rtsGameTicks == result.elapsed(),
                "persisted winners and elapsed; an observer and an eliminated ally are not winners");
        h.assertTrue(SurvivalServerEvents.isEnabled() && !SurvivalServerEvents.isWaveInProgress()
                        && SurvivalServerEvents.getCurrentEnemies().isEmpty()
                        && SurvivalServerEvents.cursor().equals(new WaveCursor(9,0,false)),
                "early beacon differs from clearing wave 30");
        h.assertTrue(seat(A).survivalRewardDraws==1 && seat(B).survivalRewardDraws==1
                        && seat(A).pendingSurvivalRewards.isEmpty() && seat(B).pendingSurvivalRewards.isEmpty(),
                "retiring a live wave never creates another grant");
        var rows = PlayerServerEvents.buildMatchStatsRows(result.winners());
        h.assertTrue(rows.size()==3 && rows.stream().noneMatch(r -> r.name.equals(VIEWER))
                        && rows.stream().filter(r -> r.winner).count()==2, "only three original seats score");
    }

    @GameTest(templateNamespace="api65_repair3_survival_result", template="ore_mine_test_arena", timeoutTicks=550)
    public static void scoredSurvivalAndSeatEntitlementAcrossThreeJvms(GameTestHelper h) throws Exception {
        ServerLevel level = h.getLevel();
        switch (System.getProperty("api65.repair3.survival.result.phase","")) {
            case "prepare" -> {
                h.assertTrue(!WITNESS.exists() && !SurvivalServerEvents.isEnabled() && PlayerServerEvents.rtsPlayers.isEmpty(),
                        "fresh isolated suite world");
                level.getGameRules().getRule(GameRuleRegistrar.COOP_MODE).set(false, level.getServer());
                var classic = seat(A,18001,11); seat(B,18002,12);
                PlayerServerEvents.defeat(B,"fixture");
                h.assertTrue(PlayerServerEvents.postGameRtsPlayers.contains(classic), "Classic last seat still wins");
                resetRoster();
                var survivor = seat(A,18001,21); seat(B,18002,22);
                SurvivalServerEvents.enable(WaveDifficulty.EASY);
                PlayerServerEvents.defeat(B,"fixture");
                h.assertTrue(!SurvivalResult.isAfterMatch() && !PlayerServerEvents.postGameRtsPlayers.contains(survivor),
                        "same default coop=false and two seats: Survival does not award a last-seat victory");
                PlayerServerEvents.defeat(A,"fixture");
                h.assertTrue(SurvivalResult.current().kind()==SurvivalResult.Kind.ALL_DEFEATED
                        && SurvivalResult.current().winners().isEmpty(), "last seat lost: scored defeat");
                SurvivalServerEvents.reset(); resetRoster();
                reentrantFinishCancelsPublication(h);

                var a = seat(A,18001,31);
                long seed=32;
                while (seed<2048 && com.solegendary.reignofnether.items.ItemUtil.getRandomItemDropsList(seed,-1)
                        .getFirst().getItem()==a.itemDropQueue.getFirst().getItem()) seed++;
                h.assertTrue(seed<2048,"two distinct native rewards for per-seat publication checks");
                var b=RTSPlayer.getNewPlayer(B,Factions.VILLAGERS,18002,1,true,seed,-1L);
                PlayerServerEvents.rtsPlayers.add(b);
                SurvivalServerEvents.enable(WaveDifficulty.HARD); anchor(h);
                var viewer=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),VIEWER));
                SurvivalServerEvents.onPlayerJoin(new PlayerEvent.PlayerLoggedInEvent(viewer));
                h.assertTrue(!AlliancesServerEvents.isAllied(A,VIEWER) && AlliancesServerEvents.isAllied(A,B),
                        "formal seats auto-ally; mere spectator login cannot mutate affiliations");
                var veto=new VetoSecondDrop(b.itemDropQueue.getFirst().getItem()); MinecraftForge.EVENT_BUS.register(veto);
                var n=new CompoundTag();
                n.putInt("aQueue",a.itemDropQueue.size()); n.putInt("bQueue",b.itemDropQueue.size());
                n.putString("bItem",b.itemDropQueue.getFirst().getItem().getDescriptionId());
                var body=issue(h,7);
                h.startSequence().thenWaitUntil(()->h.assertTrue(level.getEntity(body.getUUID())==body,"ordinary UUID publication"))
                        .thenExecute(()->{body.setInvulnerable(false);body.kill();})
                        .thenWaitUntil(()->h.assertTrue(!SurvivalServerEvents.isWaveInProgress(),"real scheduler cleared wave seven"))
                        .thenExecute(()->{
                            h.assertTrue(veto.vetoes>=1 && a.survivalRewardDraws==1 && a.pendingSurvivalRewards.isEmpty()
                                    && b.survivalRewardDraws==0 && b.pendingSurvivalRewards.size()==1
                                    && b.itemDropQueue.size()==n.getInt("bQueue")
                                    && b.itemDropQueue.getFirst().getItem().getDescriptionId().equals(n.getString("bItem")),
                                    "single-seat veto retains its exact draw, not its peer's or the wave");
                            stop(h,"partial",n);
                        });
            }
            case "verify" -> {
                var n=loadWitness(h,"partial");
                h.assertTrue(SurvivalServerEvents.cursor().equals(new WaveCursor(8,0,false)), "cleared encounter was not replayed");
                var a=seat(A); var b=seat(B);
                h.startSequence().thenWaitUntil(()->h.assertTrue(b.survivalRewardDraws==1 && b.pendingSurvivalRewards.isEmpty(),
                                "only previously denied seat redeems through the restored scheduler"))
                        .thenExecute(()->{
                            h.assertTrue(a.survivalRewardDraws==1 && a.itemDropQueue.size()==n.getInt("aQueue")-1
                                    && b.itemDropQueue.size()==n.getInt("bQueue")-1,"each queue advanced once");
                            h.assertTrue(!level.getEntitiesOfClass(ItemEntity.class,new AABB(POS).inflate(5),
                                    e->e.getItem().getItem().getDescriptionId().equals(n.getString("bItem"))).isEmpty(),
                                    "actual world item exists for the restored entitlement");
                            seat(C,18003,33); AlliancesServerEvents.addAlliance(A,C);
                            PlayerServerEvents.defeat(C,"fixture");
                            AlliancesServerEvents.addAlliance(A,VIEWER); // stale older world edge is NOT eligibility
                            var body=issue(h,8); n.putUUID("body",body.getUUID());
                            a.beaconOwnerTicks=Beacon.getTicksToWin(level); // use normal next server tick's endpoint
                        })
                        .thenWaitUntil(()->h.assertTrue(SurvivalResult.isAfterMatch(),"beacon latched in the middle of wave eight"))
                        .thenExecute(()->{
                            n.putLong("elapsed",SurvivalResult.current().elapsed());
                            assertFinal(h,n); stop(h,"beacon",n);
                        });
            }
            case "verify_terminal" -> {
                var n=loadWitness(h,"beacon"); assertFinal(h,n);
                var scores=seat(A).scores; int[] before=scores.getScoreListAsArray();
                scores.addToScore(RTSPlayerScoresEnum.TOTAL_BUILDINGS_CONSTRUCTED);
                scores.addHarvestScore(RTSPlayerScoresEnum.FOOD_HARVESTED,7);
                h.assertTrue(Arrays.equals(before,scores.getScoreListAsArray()),"free-play construction/income cannot change scores");
                h.assertTrue(!SurvivalServerEvents.finish(level,SurvivalResult.Kind.ALL_DEFEATED,Set.of()),"duplicate/conflicting terminal refused");
                PlayerServerEvents.beaconVictory(A); SurvivalServerEvents.waveCleared(level); SurvivalServerEvents.setWaveNumber(4);
                SurvivalServerEvents.startNextWave(level);
                level.setDayTime(11000);
                h.startSequence().thenIdle(25).thenExecute(()->level.setDayTime(18000))
                        .thenIdle(35).thenExecute(()->{
                            assertFinal(h,n);
                            h.assertTrue(level.getEntity(n.getUUID("body"))==null,"retired original enemy stays gone after reload");
                            stop(h,"verified",n);
                        });
            }
            default -> throw new IllegalArgumentException("Select prepare|verify|verify_terminal");
        }
    }
}
