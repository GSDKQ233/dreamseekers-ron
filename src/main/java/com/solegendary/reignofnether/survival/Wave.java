package com.solegendary.reignofnether.survival;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Comparator;


public class Wave {

    public int number;
    public int population; // multiplied by number of players
    public int highestUnitTier;
    public Faction faction;

    public static long randomSeed = System.currentTimeMillis();

    public Wave(int number, int population, int highestUnitTier) {
        this.number = number;
        this.population = population;
        this.highestUnitTier = highestUnitTier;
        this.faction = WaveFactionSequence.atWave(FACTIONS, number, Factions.NONE);
    }

    public int getNumPortals() {
        return Math.max(1, 1 + number / 3);
    }
    
    public void start(ServerLevel level) {
        if (faction != null && faction.spawnWave != null && Factions.SURVIVAL_FACTIONS.contains(faction.key))
            faction.spawnWave(level, this);
        else
            ReignOfNether.LOGGER.warn("Cannot spawn survival wave {}: no supported faction", number);
    }

    public static Wave getWave(int number) {
        return WaveFactionSequence.atWave(waves, number, null);
    }

    private static volatile List<Wave> waves = List.of();
    private static volatile List<Faction> FACTIONS = List.of();

    public static synchronized void reseedWaves() {
        // Canonical key order makes the seed independent of addon registration order.
        List<Faction> factions = Factions.SURVIVAL_FACTIONS.stream()
                .filter(key -> key != null)
                .distinct()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .map(Factions::getFaction)
                .filter(faction -> faction != null && faction != Factions.NONE && faction.spawnWave != null)
                .toList();
        FACTIONS = WaveFactionSequence.generate(factions, randomSeed, 30);

        // Publish a complete snapshot: integrated-server clients can reseed this shared class too.
        waves = List.of(
                new Wave(1, 5, 1), // every tier increase, raise rate of population increase by +1
                new Wave(2, 10, 1),
                new Wave(3, 15, 1),
                new Wave(4, 21, 2),
                new Wave(5, 27, 2),
                new Wave(6, 33, 2),
                new Wave(7, 40, 3),
                new Wave(8, 47, 3),
                new Wave(9, 54, 3),
                new Wave(10, 62, 4),
                new Wave(11, 70, 4),
                new Wave(12, 78, 4),
                new Wave(13, 87, 5),
                new Wave(14, 96, 5),
                new Wave(15, 105, 5),
                new Wave(16, 115, 6),
                new Wave(17, 125, 6),
                new Wave(18, 135, 6), // after this wave, start raising population geometrically
                new Wave(19, 150, 6),
                new Wave(20, 170, 6),
                new Wave(21, 195, 6),
                new Wave(22, 225, 6),
                new Wave(23, 260, 6),
                new Wave(24, 295, 6),
                new Wave(25, 335, 6),
                new Wave(26, 380, 6),
                new Wave(27, 430, 6),
                new Wave(28, 485, 6),
                new Wave(29, 545, 6),
                new Wave(30, 610, 6)
        );
    }

    static {
        reseedWaves();
    }
}
