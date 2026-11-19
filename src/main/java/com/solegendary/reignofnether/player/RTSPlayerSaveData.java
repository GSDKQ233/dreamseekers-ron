package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.TradeAction;
import com.solegendary.reignofnether.ability.abilities.TradeResources;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RTSPlayerSaveData extends SavedData {

    public final ArrayList<RTSPlayer> rtsPlayers = new ArrayList<>();
    public final ArrayList<RTSPlayer> postGameRtsPlayers = new ArrayList<>();
    public long gameTicks;

    private static RTSPlayerSaveData create() {
        return new RTSPlayerSaveData();
    }

    @Nonnull
    public static RTSPlayerSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return create();
        }
        return server.overworld()
            .getDataStorage()
            .computeIfAbsent(RTSPlayerSaveData::load, RTSPlayerSaveData::create, "saved-rtsplayer-data");
    }

    public static RTSPlayerSaveData load(CompoundTag tag) {
        ReignOfNether.LOGGER.info("RTSPlayerSaveData.load");

        RTSPlayerSaveData data = create();
        readPlayers(tag, "rtsplayers", data.rtsPlayers);
        readPlayers(tag, "postGameRtsPlayers", data.postGameRtsPlayers);
        data.gameTicks = Math.max(0L, tag.getLong("gameTicks"));
        return data;
    }

    private static void readPlayers(CompoundTag tag, String key, ArrayList<RTSPlayer> target) {
        ListTag ltag = tag.getList(key, Tag.TAG_COMPOUND);

        if (ltag != null) {
            for (Tag ctag : ltag) {
                CompoundTag ptag = (CompoundTag) ctag;

                String name = ptag.getString("name");
                int id = ptag.getInt("id");
                int ticksWithoutCapitol = ptag.getInt("ticksWithoutCapitol");
                int beaconOwnerTicks = ptag.getInt("beaconOwnerTicks");
                Faction faction;
                try {
                    faction = Factions.getFaction(ResourceLocation.parse(ptag.getString("faction")));
                } catch (Exception e) {
                    faction = switch (ptag.getString("faction")) {
                        case "VILLAGERS" -> Factions.VILLAGERS;
                        case "MONSTERS" -> Factions.MONSTERS;
                        case "PIGLINS" -> Factions.PIGLINS;
	                    default -> Factions.NEUTRAL;
                    };
                }
                int[] scores = ptag.contains("scores") ? ptag.getIntArray("scores") : new RTSPlayerScores().getScoreListAsArray();
                int scenarioRoleIndex = ptag.getInt("scenarioRoleIndex");

                Map<TradeAction, Integer> tradeRates = new HashMap<>();
                tradeRates.put(TradeAction.FOOD_FOR_EMERALD, ptag.contains("foodEmeraldRate") ? ptag.getInt("foodEmeraldRate") : TradeResources.START_SELL_RATE);
                tradeRates.put(TradeAction.EMERALD_FOR_FOOD, ptag.contains("emeraldFoodRate") ? ptag.getInt("emeraldFoodRate") : TradeResources.START_BUY_RATE);
                tradeRates.put(TradeAction.WOOD_FOR_EMERALD, ptag.contains("woodEmeraldRate") ? ptag.getInt("woodEmeraldRate") : TradeResources.START_SELL_RATE);
                tradeRates.put(TradeAction.EMERALD_FOR_WOOD, ptag.contains("emeraldWoodRate") ? ptag.getInt("emeraldWoodRate") : TradeResources.START_BUY_RATE);
                tradeRates.put(TradeAction.ORE_FOR_EMERALD, ptag.contains("oreEmeraldRate") ? ptag.getInt("oreEmeraldRate") : TradeResources.START_SELL_RATE);
                tradeRates.put(TradeAction.EMERALD_FOR_ORE, ptag.contains("emeraldOreRate") ? ptag.getInt("emeraldOreRate") : TradeResources.START_BUY_RATE);

                int creepScore = ptag.contains("creepScore") ? ptag.getInt("creepScore") : 0;
                int itemsDropped = ptag.contains("itemsDropped") ? ptag.getInt("itemsDropped") : 0;
                Long itemSeed = ptag.contains("itemSeed") ? ptag.getLong("itemSeed") : new Random().nextLong();
                Long raritySeed = ptag.contains("raritySeed") ? ptag.getLong("raritySeed") : new Random().nextLong();

                RTSPlayer player = RTSPlayer.getFromSave(
                        name, id, ticksWithoutCapitol, faction,
                        beaconOwnerTicks, scores, scenarioRoleIndex,
                        tradeRates, creepScore, itemsDropped, itemSeed, raritySeed
                );
                // Older saves never distinguished survival draws; do not fabricate historical rolls.
                player.survivalRewardDraws = Math.min(Math.max(0, ptag.getInt("survivalRewardDraws")), player.itemDropQueue.size());
                for (int i = 0; i < player.survivalRewardDraws; i++) player.itemDropQueue.pollFirst();
                if (ptag.hasUUID("playerUuid") && id >= 0) player.playerUuid = ptag.getUUID("playerUuid");
                player.startPosColorId = ptag.getInt("startPosColorId");
                // Absent on older saves: preserve the historical dog preference default.
                player.isDogPerson = !ptag.contains("isDogPerson", Tag.TAG_BYTE) || ptag.getBoolean("isDogPerson");
                target.add(player);

                ReignOfNether.LOGGER.info("RTSPlayerSaveData.load: " + name + "|" + id + "|" + faction);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("rtsplayers", writePlayers(rtsPlayers));
        tag.put("postGameRtsPlayers", writePlayers(postGameRtsPlayers));
        tag.putLong("gameTicks", Math.max(0L, gameTicks));
        return tag;
    }

    private static ListTag writePlayers(ArrayList<RTSPlayer> players) {
        ListTag list = new ListTag();
        players.forEach(p -> {
            CompoundTag cTag = new CompoundTag();
            cTag.putString("name", p.name);
            cTag.putInt("id", p.id);
            if (p.playerUuid != null && !p.isBot()) cTag.putUUID("playerUuid", p.playerUuid);
            cTag.putInt("ticksWithoutCapitol", p.ticksWithoutCapitol);
            cTag.putInt("beaconOwnerTicks", p.beaconOwnerTicks);
            cTag.putString("faction", p.faction.key.toString());
            cTag.putIntArray("scores", p.scores.getScoreListAsArray());
            cTag.putInt("scenarioRoleIndex", p.scenarioRoleIndex);
            cTag.putInt("startPosColorId", p.startPosColorId);
            cTag.putBoolean("isDogPerson", p.isDogPerson);
            cTag.putInt("foodEmeraldRate", p.tradeRates.get(TradeAction.FOOD_FOR_EMERALD));
            cTag.putInt("emeraldFoodRate", p.tradeRates.get(TradeAction.EMERALD_FOR_FOOD));
            cTag.putInt("woodEmeraldRate", p.tradeRates.get(TradeAction.WOOD_FOR_EMERALD));
            cTag.putInt("emeraldWoodRate", p.tradeRates.get(TradeAction.EMERALD_FOR_WOOD));
            cTag.putInt("oreEmeraldRate", p.tradeRates.get(TradeAction.ORE_FOR_EMERALD));
            cTag.putInt("emeraldOreRate", p.tradeRates.get(TradeAction.EMERALD_FOR_ORE));
            cTag.putInt("creepScore", p.creepScore);
            cTag.putInt("itemsDropped", p.itemsDropped);
            cTag.putInt("survivalRewardDraws", p.survivalRewardDraws);
            cTag.putLong("itemSeed", p.itemSeed);
            cTag.putLong("raritySeed", p.raritySeed);
            list.add(cTag);

            //ReignOfNether.LOGGER.info("RTSPlayerSaveData.save: " + p.name + "|" + p.id + "|" + p.faction);
        });
        return list;
    }

    public void save() {
        this.setDirty();
    }
}
