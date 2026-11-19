package com.dreamseekers.economy;

/** Pure fixed-point conversion and ratio preservation; no ledger or attribute mutations. */
public final class DreamResearchMath {
    private DreamResearchMath(){}
    public record Credit(int amount,int remainder){}
    public static Credit convert(long basis,int basisPoints,long previous,int balance,int cap) {
        if(basis<0||basisPoints<0||basisPoints>10000||previous<0||previous>=10000||balance<0||cap<0)
            throw new IllegalArgumentException("Invalid shadow conversion");
        long numerator=Math.addExact(Math.multiplyExact(basis,basisPoints),previous);
        long room=Math.max(0L,(long)cap-balance),amount=Math.min(room,numerator/10000);
        // Reaching or exceeding cap discards fractional overflow, including old retained fractions.
        int remainder=amount>=room?0:(int)(numerator%10000);
        return new Credit((int)amount,remainder);
    }
    public static float healthAfter(float health,double before,double after) {
        if(!Float.isFinite(health)||!Double.isFinite(before)||!Double.isFinite(after)||before<=0||after<=0)
            throw new IllegalArgumentException("Invalid health ratio");
        if(health<=0)return 0;
        return (float)Math.min(after,Math.max(0,health/before*after));
    }
}
