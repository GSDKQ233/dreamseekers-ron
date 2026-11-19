// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.EvokerUnit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Narrow adapter for RoN's ordinary evoker fangs, which do not read the attack-damage attribute.
 * Wind keeps its established lease policy; Bone is optional final vulnerability on precisely ordinary-born fangs.
 * Ascendancy also applies only to proven ordinary-born fangs: their native base AD is deliberately zero.
 * Without these contributions the exact legacy branch runs. Nothing here affects other magic.
 */
public final class WindFangDamage {
    private WindFangDamage() { }
    private static final Map<EvokerFangs,SkillIdentity.Stamp> CASTERS=new WeakHashMap<>();
    private static final Map<DamageSource,Impact> FRAMES=new IdentityHashMap<>();

    static void joined(EvokerFangs fangs) {
        if(fangs.level() instanceof ServerLevel&&fangs.getOwner() instanceof EvokerUnit source)
            CASTERS.putIfAbsent(fangs,SkillIdentity.capture(source));
    }
    static void left(EvokerFangs fangs) { CASTERS.remove(fangs); }
    /** Reuse the fixed native hurt only for Wind, a captured ordinary effect, or qualified ordinary Ascendancy. */
    public static boolean hurtIfEnhanced(EvokerFangs fangs,LivingEntity victim) {
        if(!(fangs.level() instanceof ServerLevel level)||!level.getServer().isSameThread()
                ||!(fangs.getOwner() instanceof EvokerUnit owner))return false;
        SkillEffects.BuffLease lease=SkillEffects.captureNamedBonus(owner,WindDash.COMMAND_KEY,BuffKind.ATTACK_DAMAGE);
        PrimaryUnitHitHooks.Impact ordinary=OrdinarySkillHit.captureFang(fangs,victim);
        // Capture an ordinary impact, never grant a multiplier to hand-created/wild/loaded fangs.
        boolean ascendancy=OrdinaryFangOrigin.capture(fangs)!=null
                &&com.dreamseekers.economy.DreamAscendancyAttributes.enabledFor(owner);
        if(lease==null&&ordinary==null&&!ascendancy)return false;
        // Ascendancy-only now opts into this branch too: retain the core override's friendly-fire guard.
        if(UnitServerEvents.getUnitToEntityRelationship(owner,victim)==Relationship.FRIENDLY)return true;
        SkillIdentity.Stamp caster=CASTERS.get(fangs);
        if(lease!=null&&(caster==null||caster.entity()!=owner||!caster.valid()||!lease.valid()
                ||fangs.isRemoved()||level.getEntity(fangs.getUUID())!=fangs
                ||!victim.isAlive()||victim.isInvulnerable()||victim==owner||victim.level()!=level
                ||level.getEntity(victim.getUUID())!=victim
                ||UnitServerEvents.getUnitToEntityRelationship(owner,victim)==Relationship.FRIENDLY))return true;
        if(lease==null&&(!victim.isAlive()||victim.isInvulnerable()||victim==owner))return false;
        DamageSource damage=fangs.damageSources().indirectMagic(owner,owner);
        Impact impact=lease==null?null:new Impact(fangs,owner,victim,caster,lease);
        if(impact!=null)FRAMES.put(damage,impact);
        try {
            // Preserve the original live circle flag and the original downstream Zeal +1/level event.
            float base=EvokerUnit.FANGS_DAMAGE*(owner.lastCastedCircleFangs?2:1);
            // Native AD is fixed: apply this owner upgrade once, before unchanged Wind and downstream Zeal/Bone.
            float amount=(float)(base*(ascendancy?1.15D:1D)*(lease==null?1:1+lease.amount()));
            com.solegendary.reignofnether.resources.kill.EconomicFangHooks.wrap(
                    fangs,victim,ordinary!=null?ordinary:victim::hurt).hurt(damage,amount);
        } finally { if(impact!=null)FRAMES.remove(damage); }
        return true;
    }
    static final class Impact {
        final EvokerFangs fangs;
        final EvokerUnit owner;
        final LivingEntity victim;
        final ServerLevel level;
        final UUID victimUuid;
        final SkillIdentity.Stamp caster,target;
        final SkillEffects.BuffLease lease;
        boolean entered,revoked;
        Impact(EvokerFangs fangs,EvokerUnit owner,LivingEntity victim,SkillIdentity.Stamp caster,SkillEffects.BuffLease lease) {
            this.fangs=fangs;this.owner=owner;this.victim=victim;this.caster=caster;this.lease=lease;
            level=(ServerLevel)fangs.level();victimUuid=victim.getUUID();
            target=victim instanceof Unit?SkillIdentity.capture(victim):null;
        }
        boolean valid() {
            return !revoked&&caster.valid()&&caster.entity()==owner&&lease.valid()
                    &&fangs.getOwner()==owner&&!fangs.isRemoved()&&fangs.level()==level
                    &&level.getEntity(fangs.getUUID())==fangs&&victim.isAlive()&&!victim.isRemoved()
                    &&!victim.isInvulnerable()&&victim.level()==level&&level.getEntity(victimUuid)==victim
                    &&(target==null||target.valid())
                    &&UnitServerEvents.getUnitToEntityRelationship(owner,victim)!=Relationship.FRIENDLY;
        }
    }
    static Impact matching(LivingEntity victim,DamageSource damage) {
        Impact impact=FRAMES.get(damage);return impact!=null&&impact.victim==victim?impact:null;
    }
    public static boolean rejectRepeatedHurt(LivingEntity victim,DamageSource damage) {
        Impact impact=matching(victim,damage);if(impact==null)return false;
        if(impact.entered)return true;
        impact.entered=true;return !impact.valid();
    }
    /** This is the vanilla indirect-magic path, not RoN's ordinary-attack pre-armor ticket path. */
    public static float validateAfterHurt(LivingEntity victim,DamageSource damage,float legacyAmount) {
        Impact impact=matching(victim,damage);
        if(impact==null)return legacyAmount;
        return impact.valid()&&Float.isFinite(legacyAmount)?Math.max(0,legacyAmount):0;
    }
    static void clear(LivingEntity entity) {
        for(Impact impact:FRAMES.values())if(impact.owner==entity||impact.victim==entity)impact.revoked=true;
    }
    static void relationshipsChanged() {
        for(Impact impact:FRAMES.values())if(!impact.valid())impact.revoked=true;
    }
    static void stop() { CASTERS.clear();FRAMES.clear(); }
}
