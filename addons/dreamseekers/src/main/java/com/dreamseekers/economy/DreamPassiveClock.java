package com.dreamseekers.economy;

/** Pure external draft. One tick of credit only commits after the caller accepts the due income.
 * No wall clock, balance, network or production-queue dependency. */
public final class DreamPassiveClock {
    private final int interval;
    private int elapsed;
    private String owner;
    private boolean bound;
    private long revision;
    public DreamPassiveClock(int interval) {
        if(interval != 40 && interval != 100) throw new IllegalArgumentException("Unsupported producer interval");
        this.interval=interval;this.owner="";
    }
    public static DreamPassiveClock restore(int interval,long elapsed,String owner) {
        DreamPassiveClock state=new DreamPassiveClock(interval);
        state.elapsed=(int)Math.max(0,Math.min(interval-1L,elapsed));
        state.owner=normalize(owner);return state;
    }
    private static String normalize(String owner){return owner==null?"":owner;}
    public void observeOwner(String value,long captureRevision) {
        value=normalize(value);
        // Core currently never persists captureRevision: normal reload starts at zero.
        // A nonzero first bind proves a supported capture happened before this state observed it.
        boolean reset=!owner.equals(value)||captureRevision<0
                ||(!bound ? captureRevision!=0 : revision!=captureRevision);
        if(reset)elapsed=0;
        owner=value;revision=captureRevision;bound=true;
    }
    /** Caller invokes exactly once per real server tick after observeOwner. Inactive pauses credit. */
    public boolean advanceToDue(boolean active) {
        if(!bound||owner.isBlank()||revision<0||!active)return false;
        if(elapsed==interval-1)return true;
        elapsed++;return false;
    }
    /** Same-task post-transaction success only. False/zero actual credit leaves the due credit intact. */
    public void commitIncome(boolean actualOneCredited) {
        if(actualOneCredited && elapsed==interval-1)elapsed=0;
    }
    public void resetProgress(){elapsed=0;}
    public int elapsed(){return elapsed;}
    public int interval(){return interval;}
    public String owner(){return owner;}
}
