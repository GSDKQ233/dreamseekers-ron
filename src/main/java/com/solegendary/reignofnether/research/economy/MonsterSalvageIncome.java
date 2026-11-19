package com.solegendary.reignofnether.research.economy;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.ConfirmedRtsKillEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.*;
/** Global confirmed enemy-value salvage, never local mine radius or historical payment. */
@Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID)
public final class MonsterSalvageIncome {
    private MonsterSalvageIncome() {}
    @SubscribeEvent public static void kill(ConfirmedRtsKillEvent e) {
        var s=e.snapshot(); var server=ServerLifecycleHooks.getCurrentServer();
        if(server==null||!s.rewardFaction().equals(Factions.MONSTERS.key)
           ||!s.hadResearch(CoreEconomyResearch.id("research_global_salvage")))return;
        ResourceTransaction.Result[] result={null};
        if(!e.tryClaim(a->{
            int[] basis={s.food(),s.wood(),s.ore()}, balance={a.food,a.wood,a.ore}, amounts=new int[3];
            String[] keys={"monster_salvage_food","monster_salvage_wood","monster_salvage_ore"};
            Map<String,Long> carry=new HashMap<>();
            for(int i=0;i<3;i++) {
                var c=ResourceTransactionMath.convert(basis[i],1000,a.conversionRemainders.getOrDefault(keys[i],0L).intValue());
                amounts[i]=(int)c.amount();
                carry.put(keys[i],(long)balance[i]+amounts[i]>=Integer.MAX_VALUE?0L:(long)c.remainder());
            }
            result[0]=ResourceTransaction.creditIncome(server,a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(amounts[0],amounts[1],amounts[2]),carry);
            return result[0].applied();
        }))return;
        EconomyIncomeStatistics.record(s.killerOwner(),result[0].credited());
        var actor=server.getPlayerList().getPlayerByName(s.killerOwner());if(actor!=null)EconomyBalanceSync.send(actor);
    }
}
