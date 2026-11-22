package com.dreamseekers.economy;

import com.dreamseekers.building.DreamBuildingSupport;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.ConfirmedRtsKillEvent;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.Map;

@Mod.EventBusSubscriber(modid="dreamseekers")
public final class DreamShadowKillIncome {
    public static final String REMAINDER="dreamseekers:shadow_kill";
    private DreamShadowKillIncome(){}
    @SubscribeEvent public static void onConfirmedKill(ConfirmedRtsKillEvent event) {
        var snapshot=event.snapshot();var server=ServerLifecycleHooks.getCurrentServer();
        if(server==null||!server.isSameThread()||!DreamBuildingSupport.FACTION.equals(snapshot.rewardFaction()))return;
        var player=PlayerServerEvents.getRTSPlayer(snapshot.killerOwner());
        if(!DreamPassiveEconomy.isDreamOwner(player))return;
        // Only completed keys in the death-time snapshot count. Later research cannot revalue a kill.
        int rank=DreamEconomyResearch.Line.CONVERSION.level(snapshot::hadResearch);
        int rate=100*CapitalResearchRanks.conversionPercent(rank);
        ResourceTransaction.Result[] committed=new ResourceTransaction.Result[1];
        boolean claimed=event.tryClaim(account->{
            if(!account.accountEpoch.equals(snapshot.accountEpoch()))return false;
            long previous=account.conversionRemainders.getOrDefault(REMAINDER,0L);
            var credit=DreamResearchMath.convert(snapshot.totalCost(),rate,previous,
                account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0),DreamPassiveEconomy.SHADOW_CAP);
            // Claim even at full capacity: the same victim cannot be banked for a later award.
            committed[0]=ResourceTransaction.creditIncome(server,account,account.accountEpoch,account.revision,
                new ResourceTransaction.Cost(0,0,0,Map.of(DreamPassiveEconomy.SHADOW,credit.amount())),
                Map.of(REMAINDER,(long)credit.remainder()));
            return committed[0].applied();
        });
        // Outside the atomic callback: network/statistics must never cause a partially credited retry.
        if(claimed&&committed[0]!=null)DreamPassiveIncomePublication.publish(server,player,committed[0]);
    }
}
