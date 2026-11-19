package com.dreamseekers.economy;

/** Root-approved faction distinction. Never a payment/refund authorization rule. */
public final class DreamEconomyPolicy {
    private DreamEconomyPolicy(){}
    public static boolean allowsProduction(boolean validRtsOwner,boolean dreamOwner,boolean shadowOutput){
        return validRtsOwner&&(!shadowOutput||dreamOwner);
    }
    public static boolean showShadowBalance(boolean dreamOwner,int ownBalance){return dreamOwner||ownBalance>0;}
    public static boolean shadowReadOnly(boolean dreamOwner){return !dreamOwner;}
}
