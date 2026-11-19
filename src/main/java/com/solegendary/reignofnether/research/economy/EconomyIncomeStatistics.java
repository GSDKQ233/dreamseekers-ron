package com.solegendary.reignofnether.research.economy;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.ResourceTransaction;
/** Only actual received F/W/O; extensions are not disguised as ore or total FWO. */
public final class EconomyIncomeStatistics {
    private EconomyIncomeStatistics(){}
    public static void record(String owner,ResourceTransaction.Cost credited) {
        RTSPlayer p=PlayerServerEvents.getRTSPlayer(owner);if(p==null||credited==null)return;
        add(p,RTSPlayerScoresEnum.FOOD_HARVESTED,credited.food());
        add(p,RTSPlayerScoresEnum.WOOD_HARVESTED,credited.wood());
        add(p,RTSPlayerScoresEnum.ORES_HARVESTED,credited.ore());
        add(p,RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED,credited.food());
        add(p,RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED,credited.wood());
        add(p,RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED,credited.ore());
    }
    private static void add(RTSPlayer p,RTSPlayerScoresEnum key,int amount) {
        if(amount<=0)return;
        int old=p.scores.scoreList.getOrDefault(key,0);
        p.scores.scoreList.put(key,(int)Math.min(Integer.MAX_VALUE,Math.max(0L,(long)old)+amount));
    }
}
