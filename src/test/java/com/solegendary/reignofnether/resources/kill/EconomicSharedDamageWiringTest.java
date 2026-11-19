package com.solegendary.reignofnether.resources.kill;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Structural authority-boundary guards; paired-hand GameTests supply actual runtime evidence. */
class EconomicSharedDamageWiringTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/resources/kill/"+name+".java"))
                .replaceAll("\\s+", "");
    }
    @Test void onlyReusesOriginalEvidenceAndDoesNotLeasePendingValidityToScope() throws Exception {
        String s=source("EconomicSharedDamageHooks");
        assertTrue(s.contains("RtsProjectileProvenance.captureDamage(source,victim)"));
        assertFalse(s.contains("captureRegistered("));
        assertFalse(s.contains("nativeEvidence("));
        assertFalse(s.contains("extraValidity"));
        assertTrue(s.contains("?share.token.evidence:null"));
    }
    @Test void oneShotTokenRequiresCurrentExactWriteAndIdentityEpoch() throws Exception {
        String s=source("EconomicSharedDamageHooks");
        assertTrue(s.contains("if(used)return;used=true;"));
        assertTrue(s.contains("frame.closed||frame.captured"));
        assertTrue(s.contains("frame.victim!=victim||frame.source!=source"));
        assertTrue(s.contains("!frame.closed&&WRITE.get()==frame"));
        assertTrue(s.contains("level.getServer().getTickCount()==tick"));
        assertTrue(s.contains("stamp.sameOwner()"));
        assertTrue(s.contains("partner==boundPartner"));
        assertTrue(s.contains("?boundIdentity:null"));
        assertTrue(s.contains("epoch.equals(account.accountEpoch)"));
        assertTrue(s.contains("share.partner!=victim||share.token.source!=source"));
        assertTrue(s.contains("finally{if(previous==null)SHARE.remove();elseSHARE.set(previous);}"));
    }
    @Test void sharedScopeDeniesFallbackAndReentrancy() throws Exception {
        String s=source("EconomicSharedDamageHooks"), p=source("RtsProjectileProvenance");
        assertTrue(s.contains("if(previous!=null)previous.tainted=true;"));
        assertTrue(s.contains("share.captured||share.tainted"));
        assertTrue(s.contains("try{writeAndDie.run();}"));
        int boundary=p.indexOf("if(EconomicSharedDamageHooks.active())returnEconomicSharedDamageHooks.captureShared(victim,damage);");
        assertTrue(boundary>0&&boundary<p.indexOf("if(EconomicFangHooks.matches(victim,damage))"));
        assertTrue(p.contains("EconomicSharedDamageHooks.clear();"));
    }
}
