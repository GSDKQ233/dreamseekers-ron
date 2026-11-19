package com.solegendary.reignofnether.tutorial;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.villagers.Barracks;
import com.solegendary.reignofnether.building.buildings.villagers.TownCentre;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieVillagerUnit;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.util.ArrayUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.UUID;

import static com.solegendary.reignofnether.unit.UnitServerEvents.spawnMobs;

public class TutorialServerEvents {

    private static final String TUTORIAL_MAP_NAME = "reign_of_nether_tutorial";
    private static final Long TUTORIAL_MAP_SEED = 4756899154123723533L;
    private static final String TUTORIAL_ENEMY_NAME = "Monsters";
    private static boolean enabled = false;
    private static ServerLevel tutorialLevel;
    private static UUID controllerId;

    private static final Vec3i ARMY_SPAWN_POS = new Vec3i(-2946, 68, -1177);
    private static final Vec3i ANIMAL_POS = new Vec3i(-2923, 67, -1184);
    private static final Vec3i MONSTER_ATTACK_SPAWN_POS = new Vec3i(-2968, 64, -1216);
    private static final Vec3i MONSTER_WORKER_POS = new Vec3i(-3082, 71, -1286);

    private static final Vec3i MAUSOLEUM_POS = new Vec3i(-3082, 71, -1293);
    private static final Vec3i FARM_POS_1 = new Vec3i(-3091, 70, -1291);
    private static final Vec3i FARM_POS_2 = new Vec3i(-3080, 70, -1281);
    private static final Vec3i GRAVEYARD_POS = new Vec3i(-3076, 71, -1278);
    private static final Vec3i TOWER_POS = new Vec3i(-3070, 71, -1286);
    private static final Vec3i SPIDER_LAIR_POS = new Vec3i(-3084, 71, -1302);

    private static final Vec3i TOWER_SKELETON_POS = new Vec3i(-3067, 82, -1283);
    private static final Vec3i GROUND_SKELETON_POS_1 = new Vec3i(-3074, 72, -1281);
    private static final Vec3i GROUND_SKELETON_POS_2 = new Vec3i(-3080, 71, -1272);
    private static final Vec3i SPIDER_POS = new Vec3i(-3082, 73, -1297);
    private static final Vec3i GRAVEYARD_ZOMBIE_POS = new Vec3i(-3074, 73, -1273);

    public static boolean isEnabled() {
        return enabled && tutorialLevel != null && tutorialLevel.getServer() == ServerLifecycleHooks.getCurrentServer();
    }

    private static boolean isTutorialWorld(MinecraftServer server) {
        return server != null && server.overworld() != null && server.overworld().getSeed() == TUTORIAL_MAP_SEED
                && server.getWorldData().getLevelSettings().levelName().equals(TUTORIAL_MAP_NAME);
    }
    private static ServerLevel getServerLevel() {
        if (!isEnabled() || !tutorialLevel.getServer().isSameThread() || !isTutorialWorld(tutorialLevel.getServer())
                || controllerId == null) return null;
        ServerPlayer controller = tutorialLevel.getServer().getPlayerList().getPlayer(controllerId);
        return controller != null && controller.serverLevel() == tutorialLevel ? tutorialLevel : null;
    }
    public static boolean canControl(ServerPlayer player) {
        ServerLevel level = getServerLevel();
        return player != null && level != null && player.serverLevel() == level
                && player.getUUID().equals(controllerId) && level.getServer().isSingleplayerOwner(player.getGameProfile());
    }
    private static boolean tutorialEnemy(LivingEntity entity) {
        return entity.level() == getServerLevel() && entity instanceof Unit unit
                && unit.getOwnerName().equals(TUTORIAL_ENEMY_NAME);
    }
    private static boolean tutorialTarget(BuildingPlacement building) {
        ServerLevel level=getServerLevel();
        ServerPlayer controller=level==null?null:level.getServer().getPlayerList().getPlayer(controllerId);
        return controller!=null && building.getLevel()==level
                && building.ownerName.equals(controller.getGameProfile().getName());
    }
    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) {
        if (tutorialLevel == null || tutorialLevel.getServer() == event.getServer()) {
            enabled=false;tutorialLevel=null;controllerId=null;
        }
    }

    public static void saveStage(TutorialStage stage) {
        ServerLevel serverLevel = getServerLevel();
        if (serverLevel != null) {
            TutorialSaveData tutorialData = TutorialSaveData.getInstance(serverLevel);
            tutorialData.stage = stage;
            tutorialData.save();
            serverLevel.getDataStorage().save();
            //ReignOfNether.LOGGER.info("saved tutorialStage in serverevents");
        }
    }

    public static TutorialStage loadStage(ServerLevel level) {
        if (level != null) {
            TutorialStage stage = TutorialSaveData.getInstance(level).stage;
            ReignOfNether.LOGGER.info("loaded tutorialStage in serverevents: " + stage);
            return stage;
        }
        return null;
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        // The shipped tutorial is the integrated world's owner-controlled tutorial, not a LAN editor.
        if (server == null || !isTutorialWorld(server) || !server.isSingleplayerOwner(player.getGameProfile())
                || player.serverLevel() != server.overworld()) {
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new TutorialClientboundPacket(TutorialAction.DISABLE, TutorialStage.INTRO));
            return;
        }
        TutorialStage stage=loadStage(server.overworld());
        if (stage == TutorialStage.COMPLETED) {
            enabled=false;tutorialLevel=null;controllerId=null;
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new TutorialClientboundPacket(TutorialAction.DISABLE, TutorialStage.INTRO));
            return;
        }
        tutorialLevel=server.overworld();controllerId=player.getUUID();enabled=true;
        if (stage != null) PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new TutorialClientboundPacket(TutorialAction.LOAD_STAGE, stage));
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new TutorialClientboundPacket(TutorialAction.ENABLE, TutorialStage.INTRO));
    }

    public static void setDayTime() {
        ServerLevel level = getServerLevel();
        if (level != null) {
            level.setDayTime(1000);
        }
    }

    public static void setNightTime() {
        ServerLevel level = getServerLevel();
        if (level != null) {
            level.setDayTime(13000);
        }
    }

    public static void spawnAnimals() {
        if (getServerLevel() == null) return;
        spawnMobs(EntityType.PIG, getServerLevel(), ANIMAL_POS, 3, "");
    }


    // also officially adds the tutorial bot to the game as an RTSPlayer
    public static void spawnMonsterWorkers() {
        if (getServerLevel() == null) return;
        PlayerServerEvents.startRTSBot(TUTORIAL_ENEMY_NAME, Vec3.atCenterOf(MAUSOLEUM_POS), Factions.MONSTERS, getServerLevel());
        ResearchServerEvents.addCheat(TUTORIAL_ENEMY_NAME, "warpten");
        ResourcesServerEvents.addSubtractResources(new Resources(TUTORIAL_ENEMY_NAME, 10000, 10000, 10000));
    }

    public static void spawnMonstersA() {
        if (getServerLevel() == null) return;
        spawnMobs(EntityRegistrar.ZOMBIE_UNIT.get(),
            getServerLevel(),
            MONSTER_ATTACK_SPAWN_POS,
            1,
            TUTORIAL_ENEMY_NAME
        );
        spawnMobs(EntityRegistrar.SKELETON_UNIT.get(),
            getServerLevel(),
            MONSTER_ATTACK_SPAWN_POS.north(),
            1,
            TUTORIAL_ENEMY_NAME
        );
    }

    public static void spawnMonstersB() {
        if (getServerLevel() == null) return;
        spawnMobs(EntityRegistrar.ZOMBIE_UNIT.get(),
            getServerLevel(),
            MONSTER_ATTACK_SPAWN_POS,
            4,
            TUTORIAL_ENEMY_NAME
        );
        spawnMobs(EntityRegistrar.SKELETON_UNIT.get(),
            getServerLevel(),
            MONSTER_ATTACK_SPAWN_POS.north(),
            2,
            TUTORIAL_ENEMY_NAME
        );
    }

    public static void attackWithMonstersA() {
        if (getServerLevel() == null) return; // order all monster units to attack move towards the enemy base
        BlockPos attackPos = null;
        for (BuildingPlacement building : BuildingServerEvents.getBuildings())
            if (tutorialTarget(building) && building.getBuilding() instanceof TownCentre) {
                attackPos = building.originPos;
            }

        if (attackPos == null) {
            for (BuildingPlacement building : BuildingServerEvents.getBuildings())
                if (tutorialTarget(building) && building.getBuilding() instanceof Barracks) {
                    attackPos = building.originPos;
                }
        }

        if (attackPos != null) {
            for (LivingEntity entity : UnitServerEvents.getAllUnits())
                if (tutorialEnemy(entity) && (entity instanceof ZombieUnit || entity instanceof SkeletonUnit)) {
                    ((AttackerUnit) entity).setAttackMoveTarget(attackPos);
                }
        }
    }

    public static void attackWithMonstersB() {
        if (getServerLevel() == null) return;
        BlockPos townCentrePos = null;
        BlockPos barracksPos = null;
        for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
            if (tutorialTarget(building) && building.getBuilding() instanceof TownCentre) {
                townCentrePos = building.originPos;
            } else if (tutorialTarget(building) && building.getBuilding() instanceof Barracks) {
                barracksPos = building.originPos;
            }
        }

        int zombiesCommanded = 0;
        int skeletonsCommanded = 0;
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (!tutorialEnemy(entity)) continue;
            if (entity instanceof ZombieUnit zUnit) {
                if (zombiesCommanded == 0 && barracksPos != null) {
                    zUnit.setAttackBuildingTarget(barracksPos);
                } else if (townCentrePos != null) {
                    zUnit.setAttackBuildingTarget(townCentrePos);
                }
                zombiesCommanded += 1;
            } else if (entity instanceof SkeletonUnit sUnit) {
                if (skeletonsCommanded == 0 && barracksPos != null) {
                    sUnit.setAttackMoveTarget(barracksPos);
                } else if (townCentrePos != null) {
                    sUnit.setAttackMoveTarget(townCentrePos);
                }
                skeletonsCommanded += 1;
            }
        }
    }

    public static void startBuildingMonsterBase() {
        if (getServerLevel() == null) return;
        var builderUnitIds = new ArrayList<LivingEntity>();
        for (LivingEntity u : UnitServerEvents.getAllUnits()) {
            if (tutorialEnemy(u) && u instanceof ZombieVillagerUnit) {
                builderUnitIds.add(u);
            }
        }
        if (!builderUnitIds.isEmpty()) {
            BuildingServerEvents.placeBuilding(Buildings.MAUSOLEUM,
                new BlockPos(MAUSOLEUM_POS.getX(), MAUSOLEUM_POS.getY(), MAUSOLEUM_POS.getZ()),
                Rotation.NONE,
                TUTORIAL_ENEMY_NAME,
                ArrayUtil.livingEntityListToIdArray(builderUnitIds),
                false,
                false,
                true,
                true
            );
        }
    }

    public static void expandMonsterBaseA() {
        if (getServerLevel() == null) return;
        var index = 0;
        for (LivingEntity unit : UnitServerEvents.getAllUnits()) {
            if (!tutorialEnemy(unit) || !(unit instanceof ZombieVillagerUnit)) continue;
            switch (index) {
                case 0 -> BuildingServerEvents.placeBuilding(Buildings.PUMPKIN_FARM,
                        new BlockPos(FARM_POS_1),
                        Rotation.NONE,
                        TUTORIAL_ENEMY_NAME,
                        new int[] { unit.getId() },
                        false,
                        false,
                        true,
                        true
                );
                case 1 -> BuildingServerEvents.placeBuilding(Buildings.PUMPKIN_FARM,
                        new BlockPos(FARM_POS_2),
                        Rotation.CLOCKWISE_90,
                        TUTORIAL_ENEMY_NAME,
                        new int[] { unit.getId() },
                        false,
                        false,
                        true,
                        true
                );
            }
            index++;
        }
    }

    public static void expandMonsterBaseB() {
        if (getServerLevel() == null) return;
        var index = 0;
        for (LivingEntity unit : UnitServerEvents.getAllUnits()) {
            if (!tutorialEnemy(unit) || !(unit instanceof ZombieVillagerUnit)) continue;
            switch (index) {
                case 0 -> BuildingServerEvents.placeBuilding(Buildings.GRAVEYARD,
                        new BlockPos(GRAVEYARD_POS),
                        Rotation.NONE,
                        TUTORIAL_ENEMY_NAME,
                        new int[] { unit.getId() },
                        false,
                        false,
                        true,
                        true
                );
                case 1 -> BuildingServerEvents.placeBuilding(Buildings.DARK_WATCHTOWER,
                        new BlockPos(TOWER_POS),
                        Rotation.NONE,
                        TUTORIAL_ENEMY_NAME,
                        new int[] { unit.getId() },
                        false,
                        false,
                        true,
                        true
                );
                case 2 -> BuildingServerEvents.placeBuilding(Buildings.SPIDER_LAIR,
                        new BlockPos(SPIDER_LAIR_POS),
                        Rotation.NONE,
                        TUTORIAL_ENEMY_NAME,
                        new int[] { unit.getId() },
                        false,
                        false,
                        true,
                        true
                );
                case 3 -> {
                    return;
                }
            }
            index++;
        }
    }

    public static void spawnMonsterBaseArmy() {
        if (getServerLevel() == null) return;
        spawnMobs(EntityRegistrar.ZOMBIE_UNIT.get(), getServerLevel(), GRAVEYARD_ZOMBIE_POS, 3, TUTORIAL_ENEMY_NAME);
        spawnMobs(EntityRegistrar.SKELETON_UNIT.get(), getServerLevel(), GROUND_SKELETON_POS_1, 1, TUTORIAL_ENEMY_NAME);
        spawnMobs(EntityRegistrar.SKELETON_UNIT.get(), getServerLevel(), GROUND_SKELETON_POS_2, 1, TUTORIAL_ENEMY_NAME);
        spawnMobs(EntityRegistrar.SKELETON_UNIT.get(), getServerLevel(), TOWER_SKELETON_POS, 1, TUTORIAL_ENEMY_NAME);
        spawnMobs(EntityRegistrar.SPIDER_UNIT.get(), getServerLevel(), SPIDER_POS, 3, TUTORIAL_ENEMY_NAME);
    }

    public static void spawnFriendlyArmy() {
        if (getServerLevel() == null) return;
        String ownerName = getServerLevel().getServer().getPlayerList().getPlayer(controllerId).getGameProfile().getName();
        spawnMobs(EntityRegistrar.VINDICATOR_UNIT.get(), getServerLevel(), ARMY_SPAWN_POS, 5, ownerName);
        spawnMobs(EntityRegistrar.PILLAGER_UNIT.get(), getServerLevel(), ARMY_SPAWN_POS.south(), 3, ownerName);
        spawnMobs(EntityRegistrar.IRON_GOLEM_UNIT.get(),
            getServerLevel(),
            ARMY_SPAWN_POS.south().south(),
            1,
            ownerName
        );
    }
}
