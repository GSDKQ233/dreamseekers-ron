package com.dreamseekers.economy;

import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.player.RTSPlayerScores;
import com.solegendary.reignofnether.player.RTSPlayerScoresEnum;
import com.solegendary.reignofnether.resources.ResourceTransaction;
import com.solegendary.reignofnether.research.economy.EconomyBalanceSync;
import net.minecraft.server.MinecraftServer;

/** Post-commit publication only. B2 EconomyBalanceSync is an explicit prerequisite; never repeats the income delta. */
public final class DreamPassiveIncomePublication {
    private DreamPassiveIncomePublication(){}
    public static void publish(MinecraftServer server,RTSPlayer owner,ResourceTransaction.Result result) {
        if(server==null||!server.isSameThread()||owner==null||result==null||!result.applied())return;
        var credited=result.credited();
        long actual=(long)credited.food()+credited.wood()+credited.ore()+credited.emerald();
        for(int value:credited.extra().values())actual+=value;
        if(actual<=0)return;
        recordLegacyHarvest(owner.scores,credited);
        var player=server.getPlayerList().getPlayerByName(owner.name);
        if(player!=null)EconomyBalanceSync.send(player);
    }
    /** Keep existing F/W/O/emerald total semantics. S has no existing enum and is not misreported as ore or legacy total. */
    public static void recordLegacyHarvest(RTSPlayerScores scores,ResourceTransaction.Cost actual) {
        int food=Math.max(0,actual.food()),wood=Math.max(0,actual.wood()),ore=Math.max(0,actual.ore());
        addSaturated(scores,RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED,(long)food+wood+ore+Math.max(0,actual.emerald()));
        addSaturated(scores,RTSPlayerScoresEnum.FOOD_HARVESTED,food);
        addSaturated(scores,RTSPlayerScoresEnum.WOOD_HARVESTED,wood);
        addSaturated(scores,RTSPlayerScoresEnum.ORES_HARVESTED,ore);
    }
    private static void addSaturated(RTSPlayerScores scores,RTSPlayerScoresEnum key,long amount) {
        if(amount<=0)return;
        int old=scores.scoreList.getOrDefault(key,0);
        scores.scoreList.put(key,(int)Math.min(Integer.MAX_VALUE,(long)old+amount));
    }
}
