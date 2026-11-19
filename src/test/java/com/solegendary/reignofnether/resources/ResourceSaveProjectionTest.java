package com.solegendary.reignofnether.resources;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ResourceSaveProjectionTest {
    @Test void projectionSaturatesWithoutIntermediateIntegerWrap(){
        int[] balances={Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE-1,Integer.MAX_VALUE};
        int[] pending={Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE};
        // Include signed long extremes to verify the helper's precise sum before its final clamp.
        long[] additions={Long.MIN_VALUE,-1,0,1,Integer.MAX_VALUE,(long)Integer.MAX_VALUE*3,Long.MAX_VALUE};
        for(int balance:balances)for(int delta:pending)for(long carried:additions)for(long refunds:additions){
            BigInteger exact=BigInteger.valueOf(balance).add(BigInteger.valueOf(delta)).add(BigInteger.valueOf(carried)).add(BigInteger.valueOf(refunds));
            int expected=exact.max(BigInteger.ZERO).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
            assertEquals(expected,ResourceTransactionMath.projectSavedBalance(balance,delta,carried,refunds),"balance="+balance+" pending="+delta+" carried="+carried+" refunds="+refunds);
        }
    }
    @Test void maxBalancePlusCargoRemainsMax(){
        assertEquals(Integer.MAX_VALUE,ResourceTransactionMath.projectSavedBalance(Integer.MAX_VALUE,0,4,0));
        assertEquals(Integer.MAX_VALUE,ResourceTransactionMath.projectSavedBalance(Integer.MAX_VALUE,0,0,1));
        assertEquals(6,ResourceTransactionMath.projectSavedBalance(10,-8,4,0));
        assertEquals(0,ResourceTransactionMath.projectSavedBalance(0,-8,4,0));
    }
}
