package com.solegendary.reignofnether.hero.repair2.cycle;

import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.unit.HeroUnitSave;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Only the opt-in Forge GameTest process uses this driver. No symlinks, other saves, user paths or resets. */
public final class HeroWorldCycle {
    public static final String SUITE = "api65_repair2_hero";
    static final String B = "repair2-hero-B";
    static final String SAVE = "data/saved-herounit-data.dat";
    private static final long PID = ProcessHandle.current().pid();
    private static final Path RUN = Path.of("").toAbsolutePath().normalize();
    static int phase;
    static CompoundTag expectedRoster;
    static List<HeroUnitSave> sessionView;
    static MinecraftServer session;
    static boolean tested;

    private HeroWorldCycle() {}

    static Path world(int n) { return RUN.resolve((n & 1) == 0 ? "world" : B); }

    static void require(boolean ok, String error) {
        if (!ok) throw new IllegalStateException(error);
    }

    static void started(MinecraftServer current) throws IOException {
        require(SUITE.equals(System.getProperty("forge.enabledGameTestNamespaces")) &&
                RUN.getFileName().toString().startsWith("run-resource-tech-" + SUITE + "-"),
                "Run only in this suite's isolated resourceTechRun directory");
        require(!tested && session == null && current instanceof GameTestServer &&
                ServerLifecycleHooks.getCurrentServer() == current && ProcessHandle.current().pid() == PID,
                "Every phase needs a new genuine Forge server in this same JVM");
        Path path = current.getWorldPath(LevelResource.ROOT).toRealPath();
        require(!Files.isSymbolicLink(world(phase)) && path.equals(world(phase).toRealPath()),
                "Unexpected test world in phase " + phase);
        if (phase == 0) {
            require(!Files.exists(world(0).resolve(SAVE)) && !Files.exists(world(1)),
                    "Fresh test worlds required; choose a new -PresourceTechRun suffix. No existing files removed.");
        }
        session = current;
        sessionView = HeroServerEvents.fallenHeroes;
    }

    /** Only changes what a *successful* GameTest process does after an entire ordinary server stop. */
    public static void afterServerExit(GameTestServer stopped, int exitCode) {
        if (exitCode != 0 || !SUITE.equals(System.getProperty("forge.enabledGameTestNamespaces"))) {
            System.exit(exitCode);
            return;
        }
        try {
            require(session == stopped && tested && stopped.isStopped() &&
                    ServerLifecycleHooks.getCurrentServer() == null, "No completed Forge shutdown in phase " + phase);
            require(HeroServerEvents.fallenHeroes != sessionView && HeroServerEvents.fallenHeroes.isEmpty(),
                    "Stopped server retained live fallen roster");
            int count = (phase & 1) == 0 ? 1 : 0;
            require(sessionView.size() == count, "Detach mutated the saved list");
            CompoundTag persisted = NbtIo.readCompressed(world(phase).resolve(SAVE).toFile()).getCompound("data");
            require(persisted.contains("heroUnits", Tag.TAG_LIST) &&
                    persisted.getList("heroUnits", Tag.TAG_COMPOUND).size() == count, "Wrong final disk count");
            if (count == 1) require(persisted.equals(expectedRoster), "A records changed at normal shutdown");
            System.out.println("REPAIR2_HERO_WORLD_CLOSED phase=" + phase + " world=" + world(phase).getFileName()
                    + " count=" + count + " pid=" + PID);
            session = null;
            sessionView = null;
            tested = false;
            if (++phase == 4) {
                System.out.println("REPAIR2_HERO_WORLD_CYCLE_PASS counts=1,0,1,0 clean_stops=4 pid=" + PID);
                System.exit(0);
                return;
            }
            startNext();
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void startNext() throws Exception {
        Path next = world(phase);
        boolean create = phase == 1;
        require(!Files.isSymbolicLink(next) && (create ? Files.notExists(next) :
                Files.isRegularFile(next.resolve("level.dat"))), "Wrong on-disk world state " + next);
        var access = LevelStorageSource.createDefault(RUN).validateAndCreateAccess(next.getFileName().toString());
        try {
            access.readAdditionalLevelSaveData();
            var packs = ServerPacksSource.createPackRepository(access);
            WorldDataConfiguration saved = access.getDataConfiguration();
            require((saved == null) == create, "Reopen cannot silently create a replacement level");
            var config = new WorldLoader.InitConfig(new WorldLoader.PackConfig(packs,
                    create ? WorldDataConfiguration.DEFAULT : saved, false, create), Commands.CommandSelection.DEDICATED, 2);
            WorldStem stem = Util.<WorldStem>blockUntilDone(executor ->
                    WorldLoader.<WorldData, WorldStem>load(config, context -> {
                var stems = context.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM);
                if (!create) {
                    var pair = access.getDataTag(RegistryOps.create(NbtOps.INSTANCE, context.datapackWorldgen()),
                            context.dataConfiguration(), stems, context.datapackWorldgen().allRegistriesLifecycle());
                    require(pair != null, "Unable to read the actual saved level.dat");
                    return new WorldLoader.DataLoadOutput<>(pair.getFirst(), pair.getSecond().dimensionsRegistryAccess());
                }
                var dims = context.datapackWorldgen().registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions().bake(stems);
                var settings = new LevelSettings(B, GameType.CREATIVE, false, Difficulty.NORMAL,
                        true, new GameRules(), context.dataConfiguration());
                var data = new PrimaryLevelData(settings, new WorldOptions(65, false, false),
                        dims.specialWorldProperty(), dims.lifecycle().add(context.datapackWorldgen().allRegistriesLifecycle()));
                return new WorldLoader.DataLoadOutput<>(data, dims.dimensionsRegistryAccess());
            }, WorldStem::new, Util.backgroundExecutor(), executor)).get();
            access.saveDataTag(stem.registries().compositeAccess(), stem.worldData());
            var functions = GameTestRegistry.getAllTestFunctions();
            require(functions.size() == 1, "Isolated four-session namespace must contain exactly one required GameTest");
            var batches = GameTestRunner.groupTestsIntoBatches(functions);
            MinecraftServer.spin(thread -> new GameTestServer(thread, access, packs, stem, batches, new BlockPos(0, 60, 0)));
        } catch (Throwable failure) {
            access.close();
            throw failure;
        }
    }
}
