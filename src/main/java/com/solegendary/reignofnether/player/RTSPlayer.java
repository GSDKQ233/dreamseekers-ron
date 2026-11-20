package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.ability.TradeAction;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.neutral.Beacon;
import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.fogofwar.CapitolFogRule;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.items.ItemUtil;
import com.solegendary.reignofnether.items.UnitItem;
import com.solegendary.reignofnether.scenario.ScenarioUtils;

import java.util.*;

import static com.solegendary.reignofnether.ability.TradeAction.*;
import static com.solegendary.reignofnether.ability.abilities.TradeResources.*;
import static com.solegendary.reignofnether.player.PlayerServerEvents.TICKS_TO_REVEAL;

public class RTSPlayer {
    public String name;
    public int id; // current connection entity id; for AI, always negative
    /** Stable identity, absent for bots and legacy saves until authenticated name migration. */
    public UUID playerUuid;
    /** Positive evidence of this seat's temporarily unavailable units, never a population/asset grant. */
    public final Set<UUID> unavailableUnitIds = new HashSet<>();
    public int ticksWithoutCapitol = 0;
    public Faction faction;
    public int beaconOwnerTicks = 0; // ticks owning a beacon - will win upon reaching
    public int startPosColorId = 0;
    public RTSPlayerScores scores = new RTSPlayerScores();
    public int scenarioRoleIndex = -1;
    public Map<TradeAction, Integer> tradeRates = new HashMap<>();
    public boolean isDogPerson = true;
    public int itemsDropped = 0;
    // Positions consumed by wave rewards, separate from creep thresholds and score.
    public int survivalRewardDraws = 0;
    // Each position reserves one upcoming deterministic draw for this seat, not a player name.
    public final ArrayDeque<net.minecraft.world.phys.Vec3> pendingSurvivalRewards = new ArrayDeque<>();
    public int creepScore = 0; // value of neutral enemies killed, for credit towards item drops
    public Long itemSeed = 0L;
    public Long raritySeed = 0L;
    public ArrayDeque<UnitItem> itemDropQueue = new ArrayDeque<>();

    public static RTSPlayer getNewScenarioPlayer(String playerName, Faction faction, int id, int scenarioRoleIndex) {
        RTSPlayer rtsPlayer = new RTSPlayer(playerName, faction, id);
        rtsPlayer.scenarioRoleIndex = scenarioRoleIndex;
        return rtsPlayer;
    }

    private RTSPlayer(String playerName, Faction faction, int id) {
        this.name = playerName;
        this.id = id;
        this.faction = faction;
        initTradeRates();
    }

    public static RTSPlayer getNewPlayer(String playerName, Faction faction, int id, int startPosColorId, boolean isDogPerson, Long itemSeed, Long raritySeed) {
        return new RTSPlayer(playerName, faction, id, startPosColorId, isDogPerson, itemSeed, raritySeed);
    }

    private RTSPlayer(String playerName, Faction faction, int id, int startPosColorId, boolean isDogPerson, Long itemSeed, Long raritySeed) {
        this.name = playerName;
        this.id = id;
        this.faction = faction;
        this.startPosColorId = startPosColorId;
        this.isDogPerson = isDogPerson;
        this.itemSeed = itemSeed;
        this.raritySeed = raritySeed;
        initTradeRates();
        if (itemSeed >= 0)
            this.itemDropQueue = ItemUtil.getRandomItemDropsList(itemSeed, raritySeed);

        System.out.println("");
        System.out.println("Player: " + playerName);
        System.out.println("----------------------");
        for (UnitItem unitItem : this.itemDropQueue) {
            System.out.println(unitItem.rarity.name() + " | " + unitItem.getName().getString());
        }
    }

    public static RTSPlayer getNewBot(String name, Faction faction) {
        return new RTSPlayer(name, faction);
    }

    private RTSPlayer(String name, Faction faction) {
        int minId = Integer.MAX_VALUE;
        if (!PlayerServerEvents.rtsPlayers.isEmpty()) {
            for (RTSPlayer r : PlayerServerEvents.rtsPlayers) {
                minId = Math.min(r.id, minId);
            }
        }
        if (minId >= 0) {
            this.id = -1;
        } else {
            this.id = minId - 1;
        }
        this.faction = faction;
        this.name = name;
        initTradeRates();
    }

    public static RTSPlayer getFromSave(String name, int id, int ticksWithoutCapitol, Faction faction, int beaconOwnerTicks,
                                        int[] scores, int scenarioRoleIndex, Map<TradeAction, Integer> tradeRates,
                                        int creepScore, int itemsDropped, Long itemSeed, Long raritySeed) {
        return new RTSPlayer(
                name, id, ticksWithoutCapitol, faction, beaconOwnerTicks, scores,
                scenarioRoleIndex, tradeRates, creepScore, itemsDropped, itemSeed, raritySeed
        );
    }

    private RTSPlayer(String name, int id, int ticksWithoutCapitol, Faction faction, int beaconOwnerTicks,
                      int[] scores, int scenarioRoleIndex, Map<TradeAction, Integer> tradeRates,
                      int creepScore, int itemsDropped, Long itemSeed, Long raritySeed) {
        this.name = name;
        this.id = id;
        this.ticksWithoutCapitol = ticksWithoutCapitol;
        this.faction = faction;
        this.beaconOwnerTicks = beaconOwnerTicks;
        this.scores.setScoreListFromArray(scores);
        this.scenarioRoleIndex = scenarioRoleIndex;
        this.tradeRates = tradeRates;
        this.creepScore = creepScore;
        this.itemsDropped = itemsDropped;
        this.itemSeed = itemSeed;
        this.raritySeed = raritySeed;
        if (itemSeed >= 0) this.itemDropQueue = ItemUtil.getRandomItemDropsList(itemSeed, raritySeed);
        for (int i = 0; i < itemsDropped && !this.itemDropQueue.isEmpty(); i++)
            this.itemDropQueue.pollFirst();

        System.out.println("");
        System.out.println("Player: " + name);
        System.out.println("----------------------");
        for (UnitItem unitItem : this.itemDropQueue) {
            System.out.println(unitItem.rarity.name() + " | " + unitItem.getName().getString());
        }
    }

    private void initTradeRates() {
        tradeRates.put(FOOD_FOR_EMERALD, START_SELL_RATE);
        tradeRates.put(WOOD_FOR_EMERALD, START_SELL_RATE);
        tradeRates.put(ORE_FOR_EMERALD, START_SELL_RATE);
        tradeRates.put(EMERALD_FOR_FOOD, START_BUY_RATE);
        tradeRates.put(EMERALD_FOR_WOOD, START_BUY_RATE);
        tradeRates.put(EMERALD_FOR_ORE, START_BUY_RATE);
    }

    public boolean isBot() {
        return id < 0;
    }

    public void serverTick() {
        boolean ownsBuilding = false, ownsCapitol = false;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (b.level != PlayerServerEvents.serverLevel || !name.equals(b.ownerName)) continue;
            ownsBuilding = true;
            if (b.isCapitol) { ownsCapitol = true; break; }
        }
        int previous = ticksWithoutCapitol;
        ticksWithoutCapitol = CapitolFogRule.nextTicks(previous, TICKS_TO_REVEAL, ownsBuilding, ownsCapitol);
        // A restored mature timer still grants vision. Only a real threshold crossing announces it.
        if (previous < TICKS_TO_REVEAL && ticksWithoutCapitol == TICKS_TO_REVEAL && FogOfWarServerEvents.isEnabled()
                && !ScenarioUtils.isScenarioNpc(false, scenarioRoleIndex))
            PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.revealed", false, name);

        for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
            if (building instanceof BeaconPlacement beacon && beacon.isBuilt && building.ownerName.equals(this.name)) {
                if (beacon.getUpgradeLevel() == Beacon.MAX_UPGRADE_LEVEL) {
                    beaconOwnerTicks += 1;
                    if (beaconOwnerTicks == Beacon.getTicksToWin(beacon.getLevel()) / 4 ||
                            beaconOwnerTicks == Beacon.getTicksToWin(beacon.getLevel()) / 2 ||
                            beaconOwnerTicks == (Beacon.getTicksToWin(beacon.getLevel()) * 3) / 4 ||
                            beaconOwnerTicks == Beacon.getTicksToWin(beacon.getLevel()) - 1200)
                        beacon.sendWarning("time_warning");
                }
            }
        }
    }

    public boolean isRevealed() {
        return this.ticksWithoutCapitol >= TICKS_TO_REVEAL;
    }
}
