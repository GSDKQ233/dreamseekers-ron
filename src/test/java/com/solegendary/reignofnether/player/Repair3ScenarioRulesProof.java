package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.fogofwar.CapitolFogRule;

/** Uses the actual dependency-free production rules. Can run with plain Java 17 or from JUnit. */
public final class Repair3ScenarioRulesProof {
    private int checks;

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    public static int verify() {
        var proof = new Repair3ScenarioRulesProof();
        proof.run();
        return proof.checks;
    }

    private void run() {
        for (boolean hard : new boolean[]{false, true}) {
            for (boolean keep : new boolean[]{false, true}) {
                var policy = new MatchResetPolicy(hard, keep);
                check(MatchResetPolicy.fromWire(hard, policy.wireValue(), () -> {
                    throw new AssertionError("a spectator's local mode must not override server policy");
                }).equals(policy), "reset wire roundtrip");
                check(MatchResetPolicy.fromWire(hard, 0, () -> keep).equals(policy), "old sender fallback");
                check(MatchResetPolicy.fromWire(hard, 42, () -> false).preserveMap(), "unknown future marker preserves map");
                for (boolean disposable : new boolean[]{false, true}) {
                    check(policy.removeBuilding(disposable) == (!keep && (hard || disposable)),
                            "classic/arena vs editor/scenario building policy");
                    check(policy.removeUnit(disposable) == (hard || (!keep && disposable)),
                            "hard removes loaded units; soft honors anchors and map preservation");
                }
            }
        }
        int threshold = 1200;
        for (boolean hasBuilding : new boolean[]{false, true})
            for (boolean hasCapitol : new boolean[]{false, true})
                for (boolean npc : new boolean[]{false, true})
                    for (int old : new int[]{0, threshold - 2, threshold - 1, threshold, threshold + 1, Integer.MAX_VALUE}) {
                        int current = CapitolFogRule.nextTicks(old, threshold, hasBuilding, hasCapitol);
                        boolean waiting = hasBuilding && !hasCapitol;
                        check(current == (waiting ? (int)Math.min((long)old + 1, threshold) : 0),
                                "timer saturates, resets on rebuild or last building loss");
                        boolean firstMaskAfterLoad = CapitolFogRule.reveals(old, threshold, hasBuilding, hasCapitol, npc);
                        check(firstMaskAfterLoad == (waiting && !npc && old >= threshold),
                                "saved mature timer immediately grants sight, not NPCs or owners of capitols");
                        check(CapitolFogRule.reveals(current, threshold, hasBuilding, hasCapitol, npc)
                                        == (waiting && !npc && old >= threshold - 1),
                                "crossing and already-mature re-evaluation agree");
                    }
        check(CapitolFogRule.nextTicks(-4, threshold, true, false) == 1, "legacy negative timer cannot extend immunity");
    }

    public static void main(String[] args) {
        System.out.println("REPAIR3_SCENARIO_RULES_PASS assertions=" + verify());
    }
}
