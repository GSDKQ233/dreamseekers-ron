// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.solegendary.reignofnether.api.UnitCommandResetHooks;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;
import com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Two native overrides only. A damage tag, manual doHurtTarget, probe event, or spare goal is not proof.
 * This scopes contribution permission, never changes native damage or economic attribution.
 */
public final class NativeOrdinaryDebtHit {
    private NativeOrdinaryDebtHit() { }
    private static final ArrayDeque<Proof> GOALS=new ArrayDeque<>();
    static {
        com.solegendary.reignofnether.api.NativeUnitMeleeEntryHooks.register(NativeOrdinaryDebtHit::entered);
        // Invalidate an in-flight order even when a callback resets and restores the same target (ABA).
        UnitCommandResetHooks.register(unit->{for(Proof proof:GOALS)if(proof.source.entity()==unit)proof.revoked=true;});
    }
    static boolean supported(Mob source) {
        return source.getClass()==BeeUnit.class&&source.getType()==com.solegendary.reignofnether.registrars.EntityRegistrar.BEE_UNIT.get()
                ||source.getClass()==IronGolemUnit.class&&source.getType()==com.solegendary.reignofnether.registrars.EntityRegistrar.IRON_GOLEM_UNIT.get();
    }
    private static boolean server(Entity entity) {
        return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    private record Account(String owner,RTSPlayer player,Resources resources,UUID epoch,
                           com.solegendary.reignofnether.faction.Faction faction) {
        static Account capture(String owner) {
            RTSPlayer player=PlayerServerEvents.getRTSPlayer(owner);
            Resources resources=resources(owner);
            return player==null||player.faction==null||resources==null||!resources.extendedStateSupported()
                    ?null:new Account(owner,player,resources,resources.accountEpoch,player.faction);
        }
        private static Resources resources(String owner) {
            return ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(owner)).findFirst().orElse(null);
        }
        boolean valid() {
            return PlayerServerEvents.getRTSPlayer(owner)==player&&player.faction==faction&&resources(owner)==resources
                    &&resources.extendedStateSupported()&&epoch.equals(resources.accountEpoch);
        }
    }
    static final class Proof {
        final SkillIdentity.Stamp source,target;
        final AbstractMeleeAttackUnitGoal goal;
        final Account sourceAccount,targetAccount;
        boolean revoked;int suspended,nativeEntries;
        Proof(AbstractMeleeAttackUnitGoal goal,Mob source,LivingEntity target) {
            this.goal=goal;this.source=SkillIdentity.capture(source);this.target=SkillIdentity.capture(target);
            sourceAccount=Account.capture(this.source.owner());targetAccount=Account.capture(this.target.owner());
        }
        boolean valid() {
            if(revoked||suspended!=0||GOALS.peek()!=this||!loaded(source)||!loaded(target)
                    ||sourceAccount==null||targetAccount==null||!sourceAccount.valid()||!targetAccount.valid())return false;
            Mob mob=(Mob)source.entity();Unit unit=(Unit)mob;AttackerUnit attacker=(AttackerUnit)mob;
            if(attacker.getAttackGoal()!=goal||mob.getTarget()!=target.entity()||unit.getTargetGoal()==null
                    ||unit.getTargetGoal().getTarget()!=target.entity()||unit.getFollowTarget()!=null
                    ||unit.getMoveGoal().getMoveTarget()!=null||UnitServerEvents.hasPendingImmediateAction(mob.getId())
                    ||!CoordinatedMark.enemy(mob,target.entity())||!mob.hasLineOfSight(target.entity()))return false;
            // Historical contribution does not require the owner to remain online, alive, in this
            // dimension or able to see the target. Existing cast/settlement visibility gates own that rule.
            double reach=OrdinaryMeleeReach.rawSquared(mob,attacker,target.entity());
            return mob.distanceToSqr(target.entity())<=reach;
        }
    }
    private static boolean loaded(SkillIdentity.Stamp stamp) {
        return stamp.valid()&&stamp.entity().getUUID().equals(stamp.uuid())&&stamp.entity().isAddedToWorld()&&stamp.level().getEntity(stamp.entity().getId())==stamp.entity();
    }
    /** Called only at AbstractMeleeAttackUnitGoal's exact virtual attack instruction. */
    public static boolean goal(AbstractMeleeAttackUnitGoal goal,Mob source,Entity target,BooleanSupplier original) {
        if(!supported(source)||!server(source)||!(target instanceof LivingEntity living)||!(target instanceof Unit)
                ||((AttackerUnit)source).getAttackGoal()!=goal)return original.getAsBoolean();
        Proof proof=new Proof(goal,source,living);if(!GOALS.isEmpty())proof.revoked=true;
        GOALS.push(proof);
        try { return original.getAsBoolean(); }
        finally { GOALS.removeFirstOccurrence(proof); }
    }
    /** One actual override entry belongs to this goal. Any nested invocation (including a different
     * target or an evasion callback) irrevocably forfeits only its optional contribution receipt.
     * No depth decrement/RETURN injector is needed, so cancellation or exceptions cannot leak depth. */
    private static void entered(Mob source,Entity target) {
        if(!server(source))return;
        for(Proof proof:GOALS)if(proof.source.entity()==source) {
            if(++proof.nativeEntries!=1||proof.target.entity()!=target)proof.revoked=true;
        }
    }
    static Proof capture(Mob source,Entity target) {
        Proof proof=GOALS.peek();
        return proof!=null&&proof.source.entity()==source&&proof.target.entity()==target&&proof.valid()?proof:null;
    }
    static boolean accepts(Mob source,DamageSource damage) {
        return supported(source)&&damage!=null&&damage.getEntity()==source&&damage.getDirectEntity()==source
                &&!damage.is(DamageTypeTags.IS_PROJECTILE)
                &&(source.getClass()==BeeUnit.class&&damage.is(DamageTypes.STING)
                    ||source.getClass()==IronGolemUnit.class&&damage.is(DamageTypes.MOB_ATTACK));
    }
    /** Exact Bee EconomicExactDamageHooks.Impact.hurt / IronGolem Entity.hurt call only. */
    public static boolean hurt(Mob source,Entity target,DamageSource damage,BooleanSupplier original) {
        if(!server(source))return original.getAsBoolean();
        Proof proof=capture(source,target);
        if(proof==null||proof.nativeEntries!=1||!accepts(source,damage))return original.getAsBoolean();
        return OrdinaryAttackGate.finalNativeMelee(source,target,damage,original);
    }
    /** Native IronGolem's pre-hurt broadcast is extensible; nested attacks cannot steal its claim. */
    public static void animation(Mob source,Runnable original) {
        Proof proof=GOALS.peek();
        if(!server(source)||proof==null||proof.source.entity()!=source){original.run();return;}
        proof.suspended++;
        try { original.run(); } finally { proof.suspended--; }
    }
}
