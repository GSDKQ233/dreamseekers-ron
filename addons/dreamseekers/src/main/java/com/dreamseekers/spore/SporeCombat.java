// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.spore;

import com.dreamseekers.skill.SkillIdentity;
import com.dreamseekers.skill.WindDash;
import com.dreamseekers.unit.WindKnightUnit;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

/** Only a current explicit RTS target and a proven ordinary primary call can damage an authorized spore. */
public final class SporeCombat {
    private SporeCombat() { }
    private static final ArrayDeque<Frame> FRAMES=new ArrayDeque<>();
    private static final java.util.Map<LivingEntity,Intent> ORDERS=new java.util.IdentityHashMap<>();
    private static boolean server(Entity entity) { return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread(); }
    /** Display-side permission is never used by the server. No generic non-Unit target is admitted. */
    public static boolean canHarm(LivingEntity source,JungleSporeCarrier target) {
        if(!(source instanceof AttackerUnit attacker)||!supportedAttacker(source,attacker))return false;
        if(!(source instanceof Unit unit)||!source.isAlive()||source.isRemoved()||!target.isAlive()||target.isRemoved()
                ||source.level()!=target.level()||unit.getOwnerName().isBlank())return false;
        if(source.level().isClientSide())return target.displayedActive()&&!target.displayedOwner().isBlank()
                &&!unit.getOwnerName().equals(target.displayedOwner())&&!AlliancesClient.isAllied(unit.getOwnerName(),target.displayedOwner());
        if(!server(source))return false;
        SporeLease.Lease lease=SporeLease.current(target);
        return lease!=null&&lease.active()&&((ServerLevel)source.level()).getEntity(source.getUUID())==source
                &&!unit.getOwnerName().equals(lease.caster.owner())&&!AlliancesServerEvents.isAllied(unit.getOwnerName(),lease.caster.owner());
    }
    private static boolean supportedAttacker(LivingEntity source,AttackerUnit attacker) {
        return attacker.getAttackGoal() instanceof com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal
                ||source instanceof com.dreamseekers.unit.BoneWingUnit||source instanceof com.dreamseekers.unit.AshBoneWingUnit
                ||source instanceof com.dreamseekers.unit.FriendlyGhostUnit||source instanceof com.dreamseekers.unit.ShadowSquealGhostUnit
                ||source instanceof com.dreamseekers.unit.WeepingSquealGhostUnit||source instanceof com.dreamseekers.unit.StoneSpiritThrowerUnit
                ||source instanceof com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit
                ||source instanceof com.solegendary.reignofnether.unit.units.monsters.StrayUnit
                ||source instanceof com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit
                ||source instanceof com.solegendary.reignofnether.unit.units.villagers.PillagerUnit
                ||source instanceof com.solegendary.reignofnether.unit.units.piglins.HeadhunterUnit
                ||source instanceof com.solegendary.reignofnether.unit.units.villagers.EvokerUnit;
    }
    public static final class Intent {
        final SkillIdentity.Stamp source;
        final SporeLease.Lease lease;
        Intent(LivingEntity source,SporeLease.Lease lease) { this.source=SkillIdentity.capture(source);this.lease=lease; }
        public boolean validFor(JungleSporeCarrier carrier) {
            return server(carrier)&&lease.carrier==carrier&&source.valid()&&lease.active()&&canHarm(source.entity(),carrier);
        }
    }
    /** Only UnitActionItem's accepted ATTACK setter call creates this order; stale forced bits do not. */
    public static void commandedAttack(AttackerUnit attacker,LivingEntity target) {
        if(!(attacker instanceof LivingEntity source)||!server(source)) { attacker.setUnitAttackTargetForced(target);return; }
        ORDERS.remove(source);
        if(target instanceof JungleSporeCarrier staged&&staged.wasLeaseInitialized()&&!canHarm(source,staged))return;
        Intent intent=target instanceof JungleSporeCarrier carrier&&canHarm(source,carrier)
                ?new Intent(source,SporeLease.current(carrier)):null;
        attacker.setUnitAttackTargetForced(target);
        if(!ORDERS.containsKey(source)&&intent!=null&&intent.validFor(intent.lease.carrier)&&source instanceof Unit unit
                &&unit.getTargetGoal().forced&&unit.getTargetGoal().getTarget()==target)ORDERS.put(source,intent);
    }
    /** Actual target changes/reset cancel authority; merely queuing a Shift command does not call this. */
    public static void targetChanged(LivingEntity source,LivingEntity target) {
        if(!server(source)||target!=null&&target.getId()==source.getId())return;
        Intent intent=ORDERS.get(source);
        if(intent!=null&&intent.lease.carrier!=target)ORDERS.remove(source,intent);
    }
    public static boolean stillDirected(Intent intent) {
        if(intent==null||!intent.validFor(intent.lease.carrier)||ORDERS.get(intent.source.entity())!=intent
                ||!(intent.source.entity() instanceof Unit unit))return false;
        return unit.getTargetGoal()!=null&&unit.getTargetGoal().forced&&unit.getTargetGoal().getTarget()==intent.lease.carrier
                &&!com.solegendary.reignofnether.unit.UnitServerEvents.hasPendingImmediateAction(intent.source.entity().getId());
    }
    /** Frozen intent uses the real factory's target, never a callback's newly selected target. */
    @Nullable public static Intent directedIntent(LivingEntity source,Entity actualTarget) {
        if(!server(source)||!(source instanceof AttackerUnit)||!(actualTarget instanceof JungleSporeCarrier carrier)
                ||source instanceof WindKnightUnit knight&&WindDash.isActive(knight))return null;
        Intent intent=ORDERS.get(source);
        return intent!=null&&intent.lease.carrier==carrier&&stillDirected(intent)?intent:null;
    }
    private static final class Frame {
        final Intent intent;
        final BooleanSupplier origin;
        DamageSource damage;
        boolean entered;
        int suspended;
        Frame(Intent intent,BooleanSupplier origin) { this.intent=intent;this.origin=origin; }
        boolean valid() { return suspended==0&&intent.validFor(intent.lease.carrier)&&origin.getAsBoolean(); }
    }
    public static boolean melee(Mob source,JungleSporeCarrier carrier) {
        if(!server(source)||!FRAMES.isEmpty())return source.doHurtTarget(carrier);
        Intent intent=directedIntent(source,carrier);
        if(intent==null)return source.doHurtTarget(carrier); // carrier gate rejects it; native path is not widened
        Frame frame=new Frame(intent,()->stillDirected(intent));FRAMES.push(frame);
        try { return source.doHurtTarget(carrier); }
        finally { FRAMES.removeFirstOccurrence(frame); }
    }
    @Nullable public static PrimaryUnitHitHooks.Impact capture(Intent intent,Entity actualVictim,BooleanSupplier origin) {
        if(intent==null||!(actualVictim instanceof JungleSporeCarrier carrier)||!server(carrier)||!FRAMES.isEmpty()
                ||!intent.validFor(carrier)||!origin.getAsBoolean())return null;
        return new PrimaryUnitHitHooks.Impact() {
            boolean used;
            @Override public boolean hurt(DamageSource damage,float amount) {
                if(used)return false;used=true;
                Frame frame=new Frame(intent,origin);frame.damage=damage;FRAMES.push(frame);
                try { return carrier.hurt(damage,amount); }
                finally { FRAMES.removeFirstOccurrence(frame); }
            }
        };
    }
    /** Exact subclass hurt entry, before super / Forge LivingAttack. Consume before any extensible callback. */
    public static boolean enter(JungleSporeCarrier carrier,DamageSource damage,float amount) {
        if(!server(carrier)||damage==null||!Float.isFinite(amount)||amount<=0)return false;
        Frame frame=FRAMES.peek();
        if(frame==null||frame.intent.lease.carrier!=carrier||frame.entered||damage.getEntity()!=frame.intent.source.entity()
                ||frame.damage!=null&&frame.damage!=damage||!frame.valid())return false;
        frame.damage=damage;frame.entered=true;return true;
    }
    private static Frame matching(LivingEntity victim,DamageSource damage) {
        if(!server(victim))return null;
        Frame frame=FRAMES.peek();return frame!=null&&frame.entered&&frame.damage==damage&&frame.intent.lease.carrier==victim?frame:null;
    }
    public static boolean validDamage(LivingEntity victim,DamageSource damage) {
        Frame frame=matching(victim,damage);return frame!=null&&frame.valid();
    }
    /** The native spore Arrow immunity bypass can consult this only after the subclass consumed its entry. */
    public static boolean ordinaryArrowAllowed(JungleSporeCarrier carrier,DamageSource damage) { return validDamage(carrier,damage); }
    public static float validateAfterHurt(LivingEntity victim,DamageSource damage,float amount) {
        if(!(victim instanceof JungleSporeCarrier carrier)||!carrier.wasLeaseInitialized())return amount;
        return validDamage(victim,damage)&&Float.isFinite(amount)?Math.max(0,amount):0;
    }
    @Nullable public static FinalUnitDamageHooks.Finalizer prepareFinal(LivingEntity victim,DamageSource damage) {
        if(!(victim instanceof JungleSporeCarrier carrier)||!carrier.wasLeaseInitialized()||!server(carrier))return null;
        Frame frame=matching(victim,damage);
        return amount->frame!=null&&frame.valid()&&Float.isFinite(amount)?Math.max(0,amount):0;
    }
    @Nullable public static FinalUnitDamageHooks.OrdinaryAttack prepareOrdinary(LivingEntity victim,DamageSource damage) {
        if(!(victim instanceof JungleSporeCarrier carrier)||!carrier.wasLeaseInitialized()||!server(carrier))return null;
        Frame frame=matching(victim,damage);
        return new FinalUnitDamageHooks.OrdinaryAttack() {
            private float validate(float amount) { return frame!=null&&frame.valid()&&Float.isFinite(amount)?Math.max(0,amount):0; }
            @Override public float beforeArmor(float amount) { return validate(amount); }
            @Override public float validateAfterHurt(float amount) { return validate(amount); }
            @Override public FinalUnitDamageHooks.Finalizer prepareFinal() { return this::validate; }
        };
    }
    public static boolean inImpact() {
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        return server!=null&&server.isSameThread()&&!FRAMES.isEmpty();
    }
    /** Only the known KillerRabbit attack sound call is scoped, not arbitrary sound events. */
    public static void rabbitSound(LivingEntity source,Runnable sound) {
        if(!server(source)) { sound.run();return; }
        Frame frame=FRAMES.peek();
        if(frame==null||frame.intent.source.entity()!=source) { sound.run();return; }
        frame.suspended++;
        try { sound.run(); } finally { frame.suspended--; }
    }
    static void tick(ServerLevel level) {
        for(var entry:java.util.List.copyOf(ORDERS.entrySet())) {
            Intent intent=entry.getValue();
            if(intent.source.level()!=level||ORDERS.get(entry.getKey())!=intent)continue;
            if(!intent.validFor(intent.lease.carrier)||!(entry.getKey() instanceof Unit unit)
                    ||unit.getTargetGoal()==null||!unit.getTargetGoal().forced||unit.getTargetGoal().getTarget()!=intent.lease.carrier)
                ORDERS.remove(entry.getKey(),intent);
        }
    }
    static void clear(LivingEntity entity) {
        if(!server(entity))return;
        ORDERS.remove(entity);
        for(var entry:java.util.List.copyOf(ORDERS.entrySet()))if(entry.getValue().lease.carrier==entity)
            ORDERS.remove(entry.getKey(),entry.getValue());
    }
    static void stop() { ORDERS.clear();FRAMES.clear(); }
}
