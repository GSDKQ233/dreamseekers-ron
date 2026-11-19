package com.dreamseekers.client;

/** Pure presentation policy. Never mutates, clamps, estimates or authorizes resource spending. */
public final class ShadowHudPresentation {
    private ShadowHudPresentation(){}
    public static boolean visible(boolean snapshotReady, boolean dream, int balance) {
        return snapshotReady && (dream || balance > 0);
    }
    public static String amount(int balance,int cap){return balance + " / " + cap;}
    public static boolean overflow(int balance,int cap){return balance>cap;}
    public static int width(int screenWidth,int titleWidth,int amountWidth){
        return Math.max(1,Math.min(screenWidth,Math.max(96,Math.max(titleWidth,amountWidth)+30)));
    }
}
