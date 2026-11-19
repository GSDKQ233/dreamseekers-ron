package com.dreamseekers.worldtree;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.economy.EconomyBalanceSync;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
/** Intent lives in the same bounded immutable-price receipt and account snapshot as the payment. */
public final class WorldTreeReceiptIntents {
    public static final String REFUND=DreamWorldTreeService.PURPOSE+":refund",FORFEIT=DreamWorldTreeService.PURPOSE+":forfeit";
    private WorldTreeReceiptIntents(){}
    public static boolean mark(ServerLevel l,WorldTreeState s,boolean refund){
        var r=ExtendedResourcesSync.account(s.payer);
        if(r==null||s.receipt==null||!Objects.equals(s.payerEpoch,r.accountEpoch))return false;
        var receipt=r.paidReceipts.get(s.receipt);if(receipt==null)return true; // already settled, never synthesize payment
        if(s.target==null||!receipt.payerEpoch().equals(s.payerEpoch))return false;
        // An earned refund always wins over later physical destruction. Terminal intents never reverse.
        if(receipt.purpose().equals(DreamWorldTreeService.purpose(s.target)+":refund"))return true;
        if(receipt.purpose().equals(DreamWorldTreeService.purpose(s.target)+":forfeit"))return !refund;
        if(!DreamWorldTreeService.receiptMatches(s,receipt))return false;
        return ResourceTransaction.markReceiptDisposition(l.getServer(),r,s.payerEpoch,r.revision,s.receipt,DreamWorldTreeService.purpose(s.target),
            refund?ResourceTransaction.ReceiptDisposition.REFUND:ResourceTransaction.ReceiptDisposition.FORFEIT).applied();
    }
    private static boolean intent(ResourcePaidReceipt receipt,boolean refund){
        for(var target:new WorldTreeModel[]{WorldTreeModel.LEGACY_SHADOW,WorldTreeModel.SHADOW_V3,WorldTreeModel.WIND_V4}){
            var c=DreamWorldTreeService.cost(target);
            if(receipt.purpose().equals(DreamWorldTreeService.purpose(target)+(refund?":refund":":forfeit"))&&receipt.food()==c.food()&&receipt.wood()==c.wood()&&receipt.ore()==c.ore()&&receipt.emerald()==c.emerald()&&receipt.extra().equals(c.extra()))return true;
        }return false;
    }
    public static void retry(ServerLevel l){
        if(!l.getServer().isSameThread())return;
        for(var r:List.copyOf(ResourcesServerEvents.resourcesList))for(var receipt:List.copyOf(r.paidReceipts.values())){
            if(!receipt.payerEpoch().equals(r.accountEpoch)||(!intent(receipt,true)&&!intent(receipt,false)))continue;
            var result=intent(receipt,true)?ResourceTransaction.refund(l.getServer(),r,r.accountEpoch,r.revision,receipt.id(),10000)
                :ResourceTransaction.consumeReceipt(l.getServer(),r,r.accountEpoch,r.revision,receipt.id());
            if(result.applied()){var actor=l.getServer().getPlayerList().getPlayerByName(r.ownerName);if(actor!=null)EconomyBalanceSync.send(actor);}
        }
    }
}
