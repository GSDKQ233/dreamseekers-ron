package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.resources.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Pure-on-copy refund. No player lookup, live mutation, network, normal cap, or saturation. */
public final class ResearchRefundProjection {
    private ResearchRefundProjection() {}
    public static ResourceTransaction.Status refund(Resources copy, ResourcePaidReceipt receipt) {
        if (!copy.extendedStateSupported() || copy.revision < 0 || copy.revision == Long.MAX_VALUE
                || !copy.accountEpoch.equals(receipt.payerEpoch())
                || copy.paidReceipts.get(receipt.id()) != receipt) return ResourceTransaction.Status.STALE_ACCOUNT;
        int[] old={copy.food,copy.wood,copy.ore,copy.emerald};
        int[] credit={receipt.food(),receipt.wood(),receipt.ore(),receipt.emerald()};
        int[] next=new int[4];
        for(int i=0;i<4;i++) {
            next[i]=ResourceTransactionMath.balanceAfter(old[i],0,credit[i]);
            if(old[i]<0 || credit[i]<0 || next[i]<0) return ResourceTransaction.Status.OUTPUT_OVERFLOW;
        }
        Map<ResourceLocation,Integer> extras=new HashMap<>();
        var keys=new HashSet<>(copy.extra.keySet()); keys.addAll(receipt.extra().keySet());
        if(keys.size()>ResourceAccountCodec.MAX_EXTRA) return ResourceTransaction.Status.INVALID_VALUE;
        for(var e:receipt.extra().entrySet()) {
            if(ResourceExtensionRegistry.get(e.getKey())==null) return ResourceTransaction.Status.UNKNOWN_EXTENSION;
            int oldValue=copy.extra.getOrDefault(e.getKey(),0);
            int n=ResourceTransactionMath.balanceAfter(oldValue,0,e.getValue());
            if(oldValue<0 || e.getValue()<0 || n<0) return ResourceTransaction.Status.OUTPUT_OVERFLOW;
            extras.put(e.getKey(),n);
        }
        copy.food=next[0]; copy.wood=next[1]; copy.ore=next[2]; copy.emerald=next[3];
        copy.extra.putAll(extras); copy.paidReceipts.remove(receipt.id()); copy.revision++;
        return ResourceTransaction.Status.APPLIED;
    }
}
