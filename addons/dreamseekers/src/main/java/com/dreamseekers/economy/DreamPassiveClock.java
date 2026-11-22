// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.economy;

/**
 * A single ten-second settlement window, using only whole numbers. Work is denominated in
 * half-legacy-ticks (80 work/food or ore, 200 work/shadow). A rate change affects only future work.
 */
public final class DreamPassiveClock {
    public static final int LEGACY_WORK=2, BASE_WORK=4, RESEARCHED_WORK=10, BATCH_TICKS=200;
    private final int legacyInterval, threshold;
    private int work, batchElapsed, dueWork=-1;
    private String owner;
    private boolean bound;
    private long revision;
    public DreamPassiveClock(int legacyInterval) {
        if(!validInterval(legacyInterval))throw new IllegalArgumentException("Unsupported producer interval");
        this.legacyInterval=legacyInterval;threshold=legacyInterval*LEGACY_WORK;owner="";
    }
    static boolean validInterval(int value){return value==40 || value==100;}
    static boolean validProgress(int legacyInterval,int work,int batchElapsed) {
        return validInterval(legacyInterval) && batchElapsed>=0 && batchElapsed<BATCH_TICKS
                && work>=batchElapsed*BASE_WORK && work<legacyInterval*LEGACY_WORK+batchElapsed*RESEARCHED_WORK;
    }
    public static DreamPassiveClock restore(int legacyInterval,long oldElapsed,String owner) {
        // Clamp legacy numeric extrema before scaling; elapsed is old work, NOT time spent in the new batch.
        int elapsed=(int)Math.max(0,Math.min(legacyInterval-1L,oldElapsed));
        return restoreWork(legacyInterval,elapsed*LEGACY_WORK,0,owner);
    }
    static DreamPassiveClock restoreWork(int legacyInterval,int work,int batchElapsed,String owner) {
        var s=new DreamPassiveClock(legacyInterval);
        if(!validProgress(legacyInterval,work,batchElapsed))throw new IllegalArgumentException("Invalid saved producer work");
        s.work=work;s.batchElapsed=batchElapsed;s.owner=normalize(owner);return s;
    }
    private static String normalize(String owner){return owner==null?"":owner;}
    public void observeOwner(String value,long captureRevision) {
        value=normalize(value);
        boolean reset=!owner.equals(value)||captureRevision<0
                ||(!bound ? captureRevision!=0 : revision!=captureRevision);
        if(reset)resetProgress();
        owner=value;revision=captureRevision;bound=true;
    }
    /** No account operation, stock change or notification until the 200th eligible tick of this window. */
    public int advanceBatch(boolean active,int workPerTick) {
        if(workPerTick!=BASE_WORK && workPerTick!=RESEARCHED_WORK)throw new IllegalArgumentException("Invalid producer work rate");
        dueWork=-1;
        if(!bound || owner.isBlank() || revision<0 || !active)return 0;
        if(batchElapsed<BATCH_TICKS-1){work+=workPerTick;batchElapsed++;return 0;}
        dueWork=work+workPerTick;
        return dueWork/threshold;
    }
    /** Failed transaction/zero credit keeps only already-earned work and cannot bank extra failed ticks.
     * A clipped/full store discards unreceivable output rather than paying it later. */
    public void commitBatch(int actuallyCredited,boolean atCapAfter) {
        if(dueWork>=0 && actuallyCredited>0) {
            int requested=dueWork/threshold;
            work=actuallyCredited<requested || atCapAfter ? 0 : dueWork%threshold;
            batchElapsed=0;
        }
        dueWork=-1;
    }
    public void resetProgress(){work=0;batchElapsed=0;dueWork=-1;}
    public int work(){return work;}
    public int batchElapsed(){return batchElapsed;}
    public int interval(){return legacyInterval;}
    public String owner(){return owner;}
}
