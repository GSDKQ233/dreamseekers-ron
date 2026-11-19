package com.dreamseekers.worldtree;
/** Inverts the core physical-block health equation; missing blocks remain genuinely repairable. */
public final class WorldTreeHealth {
    public record Damage(int present,double partial) {}
    private WorldTreeHealth(){}
    public static Damage project(int blocks,double minPercent,int health,int maxHealth) {
        if(blocks<2||!Double.isFinite(minPercent)||minPercent<=0||minPercent>=1||maxHealth<1||health<1||health>maxHealth)
            throw new IllegalArgumentException("Invalid live building health");
        double exact=minPercent*blocks*(1d+(double)health/maxHealth);
        int present=(int)Math.ceil(exact-1e-10);
        double partial=Math.max(0,present-exact);
        return new Damage(Math.min(blocks,present),partial);
    }
}
