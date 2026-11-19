package com.solegendary.reignofnether.survival;

import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.survival.spawners.PiglinWaveSpawner;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.GhastUnit;
import com.solegendary.reignofnether.unit.units.piglins.HoglinUnit;
import com.solegendary.reignofnether.unit.units.piglins.MagmaCubeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

import static com.solegendary.reignofnether.survival.SurvivalServerEvents.ENEMY_OWNER_NAME;
import static com.solegendary.reignofnether.survival.spawners.PiglinWaveSpawner.checkAndApplyArmour;
import static com.solegendary.reignofnether.survival.spawners.WaveSpawner.getModifiedPopCost;

public class WavePortal {

    private static final int SPAWN_TICKS_MAX = 600;
    private static int spawnTicks = 0;

    public final PortalPlacement portal;
    public final Wave wave;
    private int initialSpawnPop;

    private BlockPos lastOnPos;

    public WavePortal(PortalPlacement portal, Wave wave) {
        this.portal = portal;
        this.portal.selfBuilding = true;
        this.wave = wave;
        this.initialSpawnPop = (wave.population / wave.getNumPortals()) / 2;
    }

    int remainingInitialPop() { return Math.max(0, initialSpawnPop); }
    void restoreInitialPop(int remaining) { initialSpawnPop = Math.max(0, Math.min(initialSpawnPop, remaining)); }
    static int spawnClock() { return spawnTicks; }
    static void restoreSpawnClock(int ticks) { spawnTicks = Math.max(0, Math.min(ticks, SPAWN_TICKS_MAX)); }

    public PortalPlacement getPortal() {
        return portal;
    }

    public void tick(long ticksToAdd) {
        if (!portal.isBuilt)
            return;

        if (initialSpawnPop > 0) {
            doSpawn();
        } else {
            int pop = SurvivalServerEvents.getTotalEnemyPopulation();
            if (pop > wave.population * 2.0f) {
                return;
            }
            else if (spawnTicks >= SPAWN_TICKS_MAX) {
                spawnTicks = 0;
                doSpawn();
            } else {
                spawnTicks += ticksToAdd;
            }
        }
    }

    public void doSpawn() {
        Random random = new Random();

        EntityType<? extends Unit> mobType = (EntityType<? extends Unit>) PiglinWaveSpawner.getRandomUnitOfTier(wave.highestUnitTier);

        ServerLevel level = (ServerLevel) portal.getLevel();

        // produceUnit spawns them before applying the ownerName, meaning they aren't registered as WaveEnemies automatically
        Entity entity = portal.produceUnit(level, mobType, ENEMY_OWNER_NAME, true);
        // Native publication can be rejected by Forge. A failed birth has no body or budget.
        if (!(entity instanceof Unit produced) || !entity.isAddedToWorld()
                || !entity.isAlive() || entity.isRemoved() || entity.level() != level) return;
        // Production currently applies owner after publication; tolerate an already-registered
        // body without ever advancing the same entity through two independent WaveEnemy clocks.
        if (SurvivalServerEvents.getCurrentEnemies().stream().noneMatch(enemy -> enemy.unit == produced))
            SurvivalServerEvents.getCurrentEnemies().add(new WaveEnemy(produced));

        if (entity instanceof GhastUnit ghastUnit)
            ghastUnit.move(MoverType.SELF, new Vec3(0,10,0));

        if (entity instanceof Unit unit) {
            checkAndApplyArmour((LivingEntity) unit, wave.highestUnitTier);

            if (wave.highestUnitTier >= 3 && random.nextBoolean() && entity instanceof HoglinUnit hoglinUnit) {
                Entity entityPassenger = UnitServerEvents.spawnMob(EntityRegistrar.HEADHUNTER_UNIT.get(),
                        level, hoglinUnit.getOnPos(), ENEMY_OWNER_NAME);
                if (entityPassenger instanceof Unit pUnit) {
                    entityPassenger.startRiding(hoglinUnit);
                    if (initialSpawnPop > 0)
                        initialSpawnPop -= getModifiedPopCost(unit);
                }
            }

            if (unit instanceof MagmaCubeUnit magmaCubeUnit)
                magmaCubeUnit.setSize(wave.highestUnitTier, true);

            if (initialSpawnPop > 0)
                initialSpawnPop -= getModifiedPopCost(unit);
        }
    }
}
