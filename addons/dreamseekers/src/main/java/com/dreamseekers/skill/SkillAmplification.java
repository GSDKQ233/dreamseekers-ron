// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.resonance.LightResonanceResearch;
import com.dreamseekers.resonance.ResonanceNetwork;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import net.minecraft.server.level.ServerLevel;

/** Server-owned, cast-time potency and bounded timing snapshots. Research tiers replace one another, never multiply.
 * No global provider registry, client authority, client authority or persistent effect replay.
 */
public interface SkillAmplification {
    double potency(SkillUnit source);
    double duration(SkillUnit source);
    double cooldown(SkillUnit source);
    double cap(BuffKind kind);
    SkillAmplification BASELINE = new SkillAmplification() {
        public double potency(SkillUnit source) { return 1; }
        public double duration(SkillUnit source) { return 1; }
        public double cooldown(SkillUnit source) { return 1; }
        public double cap(BuffKind kind) { return kind.cap; }
    };

    int RANGE = 12;
    static boolean eligibleSkill(UnitSkill skill) {
        return skill == UnitSkill.GEL_GUARD || skill == UnitSkill.JELLY_FLOW || skill == UnitSkill.FOX_BLESSING;
    }
    /** Read-only server query: does not cast, spend, change research, load chunks or touch skill state.
     * Recipient alliances do not make an allied building into this caster's network.
     */
    static int currentTier(SkillUnit source,UnitSkill skill) {
        if(source==null || !eligibleSkill(skill) || !SkillService.supports(source,skill)) return 0;
        var body=source.mob();
        if(!(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !body.isAlive() || body.isRemoved() || body.isSpectator()
                || level.getEntity(body.getId())!=body || level.getEntity(body.getUUID())!=body
                || !level.hasChunkAt(body.blockPosition())
                || !DreamSpecialistResearch.ownerSupported(source.getOwnerName(),false)) return 0;
        int tier=LightResonanceResearch.tier(source.getOwnerName(),false);
        if(tier<1 || tier>2) return 0;
        boolean connected=ResonanceNetwork.network(level,source.getOwnerName()).active().stream()
                .anyMatch(p->body.distanceToSqr(p.originPos.getX()+.5,p.originPos.getY()+.5,p.originPos.getZ()+.5)
                        <=(double)RANGE*RANGE);
        return connected?tier:0;
    }
    static boolean extensionActive(SkillUnit source,UnitSkill skill) {
        return currentTier(source,skill)>0 && com.dreamseekers.unitprogression.AdvancedUnitResearch.unlocked(
                source.getOwnerName(),com.dreamseekers.unitprogression.AdvancedUnitResearch.LIGHT_EXTENSION_ID,false);
    }
    /** Capture exactly once inside a successful-cast transaction. Only these server factories can
     * create profiles. Reusing a profile for a different caster or skill is never permitted.
     */
    static Profile capture(SkillUnit source,UnitSkill skill) {
        int tier=currentTier(source,skill);
        SkillIdentity.Stamp identity=source!=null && source.mob().level() instanceof ServerLevel level
                && level.getServer().isSameThread()?SkillIdentity.capture(source.mob()):null;
        return new Profile(source,skill,tier,identity,tier>0 && com.dreamseekers.unitprogression.AdvancedUnitResearch.unlocked(
                source.getOwnerName(),com.dreamseekers.unitprogression.AdvancedUnitResearch.LIGHT_EXTENSION_ID,false));
    }
    final class Profile {
        private final SkillUnit source;
        private final UnitSkill skill;
        private final int tier;
        private final boolean extended;
        private final SkillIdentity.Stamp identity;
        private Profile(SkillUnit source,UnitSkill skill,int tier,SkillIdentity.Stamp identity,boolean extended) {
            this.source=source;this.skill=skill;this.tier=tier;this.identity=identity;this.extended=extended;
        }
        public boolean extended() { return extended; }
        /** Jelly's six healing pulses are deliberately unchanged; only its derived tide lasts longer. */
        public int durationTicks() { return extended && skill!=UnitSkill.JELLY_FLOW ? skill.durationTicks()*6/5 : skill.durationTicks(); }
        public int cooldownTicks() { return extended ? skill.cooldownTicks()*9/10 : skill.cooldownTicks(); }
        public int tideTicks() { return extended ? SkillEffects.TIDE_TICKS*6/5 : SkillEffects.TIDE_TICKS; }
        public int tier() { return tier; }
        public double potency() { return tier==2?1.5:tier==1?1.2:1; }
        boolean matches(SkillUnit source,UnitSkill skill) {
            return this.source==source && this.skill==skill && identity!=null && identity.valid();
        }
        float scale(float baseline) { return (float)(baseline*potency()); }
        double scale(double baseline) { return baseline*potency(); }
        boolean lateFox(SkillUnit source) { return tier==2 && matches(source,UnitSkill.FOX_BLESSING); }
    }
}
