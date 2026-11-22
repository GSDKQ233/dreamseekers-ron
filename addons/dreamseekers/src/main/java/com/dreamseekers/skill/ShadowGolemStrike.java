// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.unit.ShadowGolemUnit;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Ordinary confirmed melee may sweep nearby enemies without winding up again. Manual strikes,
 * including targeted siege, keep their paid windup. The secondary writes have no ordinary role. */
public final class ShadowGolemStrike {
    public static final int WINDUP_TICKS=20, RANGE=3, MAX_TARGETS=3;
    public static final double PASSIVE_RADIUS=2.0;
    public static final float UNIT_MULTIPLIER=1.5f;
    public static final double BUILDING_MULTIPLIER=2.0;
    private static final Map<ShadowGolemUnit,Charge> CHARGES=new IdentityHashMap<>();
    // Never consulted by isCasting/goal/navigation. Lives only for this one ordinary hit's aftermath.
    private static final Map<ShadowGolemUnit,Sweep> SWEEPS=new IdentityHashMap<>();
    private static final ArrayDeque<Impact> HITS=new ArrayDeque<>();
    private ShadowGolemStrike() { }
    private static boolean server(Entity e) {
        return e.level() instanceof ServerLevel l&&l.getServer().isSameThread();
    }
    private static boolean loaded(SkillIdentity.Stamp s) {
        return s!=null&&s.valid()&&s.entity().isAddedToWorld()&&s.level().getEntity(s.entity().getId())==s.entity();
    }
    private static boolean disabled(ShadowGolemUnit s) {
        // Deliberately NOT DISARM or managed root: those do not forbid independent skills.
        return s.isPassenger()||s.hasEffect(MobEffectRegistrar.STUN.get())
                ||s.hasEffect(MobEffectRegistrar.FREEZE.get())||s.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get());
    }
    public static double distanceSquared(Vec3 p,AABB box) {
        double x=Math.max(box.minX,Math.min(box.maxX,p.x));
        double y=Math.max(box.minY,Math.min(box.maxY,p.y));
        double z=Math.max(box.minZ,Math.min(box.maxZ,p.z));
        return p.distanceToSqr(x,y,z);
    }
    private static boolean enemy(ShadowGolemUnit s,LivingEntity t) {
        return t!=s&&t instanceof Unit&&!(t instanceof com.dreamseekers.spore.JungleSporeCarrier)
                &&t.isAlive()&&!t.isRemoved()&&t.isAddedToWorld()&&!t.isSpectator()
                &&t.level()==s.level()&&s.level().getEntity(t.getId())==t
                &&((ServerLevel)s.level()).getEntity(t.getUUID())==t&&s.canHarm(t)
                &&s.level().getWorldBorder().isWithinBounds(t.blockPosition());
    }
    private static boolean legalUnit(ShadowGolemUnit s,ServerPlayer viewer,LivingEntity t) {
        return enemy(s,t)&&distanceSquared(s.position(),t.getBoundingBox())<=RANGE*RANGE
                &&CoordinatedMark.visible(viewer,t)&&s.hasLineOfSight(t);
    }
    private static boolean viewer(ServerPlayer p,SkillIdentity.Stamp s) {
        return p!=null&&p.isAlive()&&!p.isRemoved()&&p.serverLevel()==s.level()
                &&p.getGameProfile().getName().equals(s.owner())
                &&s.level().getServer().getPlayerList().getPlayer(p.getUUID())==p;
    }
    private static final class Sweep {
        final ShadowGolemUnit source;
        final SkillIdentity.Stamp caster,primary;
        final ServerPlayer viewer;
        final Vec3 origin;
        final long command,revision;
        boolean paid;
        Sweep(ShadowGolemUnit source,LivingEntity primary,ServerPlayer viewer) {
            this.source=source;this.viewer=viewer;caster=SkillIdentity.capture(source);this.primary=SkillIdentity.capture(primary);
            origin=primary.getBoundingBox().getCenter();
            command=source.rts().commandRevision;revision=source.rts().skills().revision();
        }
        boolean current() {
            if(SWEEPS.get(source)!=this||!loaded(caster)||source.rts().commandRevision!=command
                    ||source.rts().skills().revision()!=(paid?revision+1:revision)
                    ||source.isNoAi()||source.ignoreNonStopCommands()||UnitServerEvents.hasPendingImmediateAction(source.getId())
                    ||source.getFollowTarget()!=null||source.getMoveGoal().getMoveTarget()!=null
                    ||com.solegendary.reignofnether.unit.interfaces.AttackerUnit.isAttackingBuilding(source)
                    ||disabled(source)||source.hasEffect(MobEffectRegistrar.DISARM.get())||TerrorbeakDreadRoar.hasDisarm(source)
                    ||(paid?!SkillService.currentCommander(source,viewer):SkillService.passiveCommander(source,UnitSkill.SHADOW_GOLEM_STRIKE)!=viewer)
                    ||!loaded(primary)||source.getTarget()!=primary.entity()
                    ||!source.canHarmOwner(primary.owner())||!SkillAutocastPolicy.observable(viewer,primary.entity())
                    ||distanceSquared(source.position(),primary.entity().getBoundingBox())>RANGE*RANGE
                    ||!SkillAutocastPolicy.sees(source,primary.entity()))return false;
            return true;
        }
        boolean nearby(LivingEntity target) {
            return target!=primary.entity()&&enemy(source,target)&&SkillAutocastPolicy.observable(viewer,target)
                    &&inRangeAndSight(target);
        }
        boolean inRangeAndSight(LivingEntity target) {
            return distanceSquared(source.position(),target.getBoundingBox())<=RANGE*RANGE
                    &&target.getBoundingBox().getCenter().distanceToSqr(origin)<=PASSIVE_RADIUS*PASSIVE_RADIUS
                    &&SkillAutocastPolicy.sees(source,target);
        }
        void applied(SkillIdentity.Stamp secondary,float health,float gel) {
            if(!paid&&(health>0||gel>0)&&current()
                    &&(secondary.entity().isAlive()||health>0&&secondary.entity().getHealth()<=0)
                    &&source.canHarmOwner(secondary.owner())&&PassiveSplashTargets.observableAfterHit(viewer,secondary)
                    &&inRangeAndSight(secondary.entity()))
                paid=SkillService.commitPassive(source,UnitSkill.SHADOW_GOLEM_STRIKE,viewer,false);
        }
    }
    /** Package-only continuation of an exact ordinary melee HP/Gel receipt. No primary rewrite,
     * no new order, no native area procedure and no resource/building write on this path. */
    static void passiveReceipt(ShadowGolemUnit source,LivingEntity primary,ServerPlayer owner) {
        if(!server(source)||owner==null||SWEEPS.containsKey(source)||!(primary instanceof Unit)
                ||primary instanceof com.dreamseekers.spore.JungleSporeCarrier||primary.level()!=source.level()
                ||!source.canHarmOwner(((Unit)primary).getOwnerName())
                ||SkillService.passiveCommander(source,UnitSkill.SHADOW_GOLEM_STRIKE)!=owner)return;
        Sweep sweep=new Sweep(source,primary,owner);SWEEPS.put(source,sweep);
        try {
            if(!sweep.current())return;
            float amount=source.getUnitAttackDamage()*UNIT_MULTIPLIER;
            if(!Float.isFinite(amount)||amount<=0||amount>150000)return;
            var targets=PassiveSplashTargets.nearest(sweep.caster.level(),new AABB(sweep.origin,sweep.origin).inflate(PASSIVE_RADIUS),
                    sweep::nearby,t->t.getBoundingBox().getCenter().distanceToSqr(sweep.origin),MAX_TARGETS-1);
            for(SkillIdentity.Stamp target:targets) {
                if(!sweep.current())break;
                if(loaded(target)&&sweep.nearby(target.entity()))hurt(source,new Impact(sweep,target,amount));
            }
        } finally { SWEEPS.remove(source,sweep); }
    }
    private static boolean buildingLegal(ShadowGolemUnit s,ServerPlayer viewer,BuildingPlacement b,BlockPos contact) {
        if(b==null||contact==null||b.getLevel()!=s.level()||!BuildingServerEvents.getBuildings().contains(b)
                ||b.isDestroyedServerside||!b.isAttackable()||!s.canHarmOwner(b.ownerName)
                ||b.getCaptureRevision()<0||!b.isPosPartOfBuilding(contact,true)||!s.level().getWorldBorder().isWithinBounds(contact)
                ||s.position().distanceToSqr(Vec3.atCenterOf(contact))>RANGE*RANGE)return false;
        if(FogOfWarServerEvents.isFogActiveFor(viewer)&&viewer.serverLevel().dimension()!=Level.OVERWORLD)return false;
        if(!FogOfWarServerEvents.isBlockVisibleFor(viewer,contact.getX(),contact.getZ()))return false;
        var hit=s.level().clip(new ClipContext(s.getEyePosition(),Vec3.atCenterOf(contact),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,s));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(contact);
    }
    private static final class Charge {
        final ShadowGolemUnit source;
        final SkillIdentity.Stamp caster,primary;
        final BuildingPlacement building;
        final String buildingOwner;
        final long buildingRevision;
        final BlockPos contact,origin;
        final ServerPlayer viewer;
        final long commandRevision,releaseAt;
        boolean releasing,primaryKilledByOwnStrike;
        Charge(ShadowGolemUnit s,ServerPlayer v,LivingEntity target,BuildingPlacement b,BlockPos p,BlockPos expectedOrigin,String expectedOwner,long expectedCapture) {
            source=s;viewer=v;caster=SkillIdentity.capture(s);primary=target==null?null:SkillIdentity.capture(target);
            building=b;buildingOwner=expectedOwner;buildingRevision=expectedCapture;
            origin=expectedOrigin;
            contact=p==null?null:p.immutable();commandRevision=s.rts().commandRevision;
            releaseAt=s.level().getGameTime()+WINDUP_TICKS;
        }
        boolean authorized() {
            return CHARGES.get(source)==this&&loaded(caster)&&viewer(viewer,caster)&&!disabled(source)
                    &&source.rts().commandRevision==commandRevision
                    &&!UnitServerEvents.hasPendingImmediateAction(source.getId());
        }
        boolean buildingIdentity() {
            return building!=null&&origin.equals(building.originPos)&&buildingOwner.equals(building.ownerName)
                    &&buildingRevision==building.getCaptureRevision()&&building.getLevel()==caster.level()
                    &&BuildingServerEvents.getBuildings().contains(building)&&!building.isDestroyedServerside;
        }
        boolean primaryLegal() {
            return primary!=null?loaded(primary)&&legalUnit(source,viewer,primary.entity())
                    :buildingIdentity()&&buildingLegal(source,viewer,building,contact)&&buildingIdentity();
        }
        boolean valid() { return authorized()&&primaryLegal()&&authorized(); }
        private boolean visibleAfterOwnPrimaryDeath() {
            LivingEntity t=primary.entity();
            if(t.isAlive())return CoordinatedMark.visible(viewer,t);
            if(!primaryKilledByOwnStrike||t.isInvisible()||t.isRemoved()||t.isSpectator()
                    ||viewer.serverLevel()!=t.level())return false;
            if(FogOfWarServerEvents.isFogActiveFor(viewer)&&viewer.serverLevel().dimension()!=Level.OVERWORLD)return false;
            return FogOfWarServerEvents.isBlockVisibleFor(viewer,t.blockPosition().getX(),t.blockPosition().getZ());
        }
        boolean releaseAuthorized() {
            if(!authorized())return false;
            // Own lethal entity hit does not erase this already fixed secondary list. Changed identity/owner does.
            return primary!=null?primary.identityValid()&&source.canHarmOwner(primary.owner())
                    &&(primary.entity().isAlive()||primaryKilledByOwnStrike)
                    &&distanceSquared(source.position(),primary.entity().getBoundingBox())<=RANGE*RANGE
                    &&visibleAfterOwnPrimaryDeath()&&source.hasLineOfSight(primary.entity()):primaryLegal();
        }
    }
    public static boolean active(ShadowGolemUnit s) {
        if(!server(s))return false;Charge c=CHARGES.get(s);return c!=null&&(c.releasing?c.authorized():c.valid());
    }
    public static int remainingTicks(ShadowGolemUnit s) {
        return active(s)?(int)Math.max(0,CHARGES.get(s).releaseAt-s.level().getGameTime()):0;
    }
    static SkillResult start(ServerPlayer sender,ShadowGolemUnit source,LivingEntity target,BooleanSupplier commit) {
        return start0(sender,source,target,null,null,null,commit);
    }
    static SkillResult startBuilding(ServerPlayer sender,ShadowGolemUnit source,BlockPos origin,BlockPos contact,BooleanSupplier commit) {
        if(origin==null||contact==null)return SkillResult.INVALID_REQUEST;
        BuildingPlacement found=null;
        for(BuildingPlacement b:BuildingServerEvents.getBuildings())if(b.getLevel()==sender.serverLevel()&&b.originPos.equals(origin)) {
            if(found!=null)return SkillResult.INVALID_TARGET; // ambiguous registration fails closed
            found=b;
        }
        return start0(sender,source,null,found,contact,origin.immutable(),commit);
    }
    private static SkillResult start0(ServerPlayer sender,ShadowGolemUnit source,LivingEntity target,BuildingPlacement building,BlockPos contact,BlockPos expectedOrigin,BooleanSupplier commit) {
        String owner=building==null?null:building.ownerName;
        long capture=building==null?0:building.getCaptureRevision();
        if(!server(source)||!source.isAlive()||source.isRemoved()||!source.isAddedToWorld()||source.getOwnerName().isBlank())return SkillResult.INVALID_SOURCE;
        if(active(source)||SWEEPS.containsKey(source))return SkillResult.ALREADY_ACTIVE;
        if(disabled(source)||UnitServerEvents.hasPendingImmediateAction(source.getId()))return SkillResult.DISABLED;
        if(target!=null?!legalUnit(source,sender,target):(!buildingLegal(source,sender,building,contact)
                ||expectedOrigin==null||!expectedOrigin.equals(building.originPos)
                ||!owner.equals(building.ownerName)||capture!=building.getCaptureRevision()))return SkillResult.INVALID_TARGET;
        SkillIdentity.Stamp before=SkillIdentity.capture(source);
        SkillIdentity.Stamp aim=target==null?null:SkillIdentity.capture(target);
        long command=source.rts().commandRevision;
        long revision=source.rts().skills().revision();
        CHARGES.remove(source);Unit.fullResetBehaviours(source);
        if(!loaded(before)||!viewer(sender,before)||source.rts().commandRevision!=command+1
                ||source.rts().skills().revision()!=revision||UnitServerEvents.hasPendingImmediateAction(source.getId()))return SkillResult.REPLAY;
        if(disabled(source)||(target!=null?(!loaded(aim)||!legalUnit(source,sender,target))
                :(!expectedOrigin.equals(building.originPos)||!owner.equals(building.ownerName)||capture!=building.getCaptureRevision()||!buildingLegal(source,sender,building,contact))))return SkillResult.INVALID_TARGET;
        Charge c=new Charge(source,sender,target,building,contact,expectedOrigin,owner,capture);CHARGES.put(source,c);
        stopMovement(source);
        if(!c.valid()||!commit.getAsBoolean()) { CHARGES.remove(source,c);return SkillResult.REPLAY; }
        // Generic swing is an intentional telegraph; no native timer, damage or knock-up procedure.
        source.swing(InteractionHand.MAIN_HAND,true);
        return SkillResult.SUCCESS;
    }
    private static void stopMovement(ShadowGolemUnit s) {
        s.getNavigation().stop();s.getMoveControl().setWantedPosition(s.getX(),s.getY(),s.getZ(),0);
        s.setSpeed(0);s.setZza(0);s.setXxa(0); // never erase deltaMovement/gravity/knockback
    }
    public static Goal goal(ShadowGolemUnit s) {
        return new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
            @Override public boolean canUse() { return active(s); }
            @Override public boolean canContinueToUse() { return active(s); }
            @Override public boolean requiresUpdateEveryTick() { return true; }
            @Override public void tick() { if(active(s))stopMovement(s); }
            @Override public void stop() { }
        };
    }
    public static void beforeTick(ShadowGolemUnit s) {
        if(!server(s))return;Charge c=CHARGES.get(s);
        if(c!=null&&!c.valid())CHARGES.remove(s,c);else if(c!=null)stopMovement(s);
    }
    public static void afterTick(ShadowGolemUnit s) {
        if(!server(s))return;Charge c=CHARGES.get(s);
        if(c==null||c.releasing)return;
        if(!c.valid()) { CHARGES.remove(s,c);return; }
        if(s.level().getGameTime()<c.releaseAt)return;
        c.releasing=true; // consume release before any callback; retain reservation until finally
        try {
            float ad=s.getUnitAttackDamage();
            if(!Float.isFinite(ad)||ad<=0||ad>100000||!c.valid())return;
            List<SkillIdentity.Stamp> targets=new ArrayList<>();
            if(c.primary!=null)targets.add(c.primary);
            c.caster.level().getEntitiesOfClass(LivingEntity.class,s.getBoundingBox().inflate(RANGE),
                    t->(c.primary==null||t!=c.primary.entity())&&legalUnit(s,c.viewer,t)).stream()
                    .sorted(Comparator.comparingDouble((LivingEntity t)->distanceSquared(s.position(),t.getBoundingBox()))
                            .thenComparing(t->t.getUUID().toString()))
                    .limit(MAX_TARGETS-1).map(SkillIdentity::capture).forEach(targets::add);
            if(!c.valid())return;
            s.swing(InteractionHand.MAIN_HAND,true);
            if(!c.valid())return;
            if(c.building!=null) {
                double normal=s.getBuildingDamageMultiplier(),coefficient=c.building.getMeleeDamageMult();
                double budget=com.dreamseekers.unitprogression.ExpansionResearchMath.buildingBudget(
                        ad*coefficient*Math.max(BUILDING_MULTIPLIER,normal),
                        com.dreamseekers.unitprogression.ExpansionResearch.unlocked(s.getOwnerName(),
                            com.dreamseekers.unitprogression.ExpansionResearch.BREACHING_STRUCTURE_ID,false));
                if(!Double.isFinite(normal)||normal<0||!Double.isFinite(coefficient)||coefficient<=0
                        ||!Double.isFinite(budget)||budget<=0||budget>1000000||!c.valid())return;
                var receipt=c.building.destroyRandomBlocksGuarded(budget,()->c.valid(),()->c.buildingIdentity(),s);
                if(!receipt.accepted()||receipt.interrupted()||!c.valid())return;
            }
            for(SkillIdentity.Stamp target:targets) {
                if(!c.releaseAuthorized())break;
                if(!loaded(target)||!legalUnit(s,c.viewer,target.entity()))continue;
                hurt(s,new Impact(c,target,ad*UNIT_MULTIPLIER));
            }
        } finally { CHARGES.remove(s,c); }
    }
    private static void hurt(ShadowGolemUnit source,Impact hit) {
        HITS.push(hit);
        try { com.solegendary.reignofnether.resources.kill.EconomicExactDamageHooks.wrap(source,hit.target.entity(),hit.target.entity()::hurt).hurt(hit.damage,hit.amount); }
        finally { HITS.removeFirstOccurrence(hit); }
    }
    /** Exact private marker carries normal melee classification but is never an ordinary goal hit. */
    private static final class StrikeDamage extends DamageSource {
        StrikeDamage(ShadowGolemUnit s) {
            super(s.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.MOB_ATTACK),s,s);
        }
    }
    static final class Impact {
        final Charge charge;
        final Sweep sweep;
        final SkillIdentity.Stamp target;
        final DamageSource damage;
        final float amount;
        boolean entered,claimed,revoked,finalizedPositive,committedLethal;
        Impact(Charge c,SkillIdentity.Stamp target,float amount) { charge=c;sweep=null;this.target=target;this.amount=amount;damage=new StrikeDamage(c.source); }
        Impact(Sweep s,SkillIdentity.Stamp target,float amount) { charge=null;sweep=s;this.target=target;this.amount=amount;damage=new StrikeDamage(s.source); }
        boolean legal() {
            return entered&&!revoked&&loaded(target)&&(sweep!=null?sweep.current()&&sweep.nearby(target.entity())
                    :charge.releaseAuthorized()&&legalUnit(charge.source,charge.viewer,target.entity()));
        }
        void applied(float health,float gel) {
            if(sweep!=null&&!revoked)sweep.applied(target,health,gel);
        }
    }
    public static boolean isStrike(DamageSource d) { return d instanceof StrikeDamage; }
    private static Impact matching(LivingEntity t,DamageSource d) {
        Impact h=HITS.peek();return h!=null&&h.target.entity()==t&&h.damage==d?h:null;
    }
    public static boolean rejectRepeatedHurt(LivingEntity t,DamageSource d) {
        if(!isStrike(d))return false;if(!server(t))return true;
        Impact h=matching(t,d);if(h==null)return true;
        if(h.entered) { h.revoked=true;return true; }h.entered=true;return !h.legal();
    }
    public static float validateAfterHurt(LivingEntity target,DamageSource damage,float amount) {
        if(!isStrike(damage))return amount;
        Impact h=matching(target,damage);return h!=null&&h.claimed&&h.legal()?amount:0;
    }
    private static final FinalUnitDamageHooks.OrdinaryAttack DENIED=new FinalUnitDamageHooks.OrdinaryAttack() {
        @Override public float beforeArmor(float ignored) { return 0; }
        @Override public FinalUnitDamageHooks.Finalizer prepareFinal() { return value->0; }
    };
    static FinalUnitDamageHooks.OrdinaryAttack prepareOrdinaryAttack(LivingEntity t,DamageSource d) {
        if(!isStrike(d))return null;Impact h=matching(t,d);
        if(h==null||!h.entered||h.claimed)return DENIED;h.claimed=true;
        return new FinalUnitDamageHooks.OrdinaryAttack() {
            @Override public float beforeArmor(float ignored) { return h.legal()?h.amount:0; }
            @Override public float validateAfterHurt(float amount) { return h.legal()?amount:0; }
            @Override public FinalUnitDamageHooks.Finalizer prepareFinal() { return SkillEffects.prepareGolemFinal(t,d,h); }
        };
    }
    public static void commandChanged(ShadowGolemUnit s) { if(server(s)){CHARGES.remove(s);SWEEPS.remove(s);} }
    static void tick(ServerLevel level) { CHARGES.entrySet().removeIf(e->e.getKey().level()==level&&!e.getValue().valid()); }
    static void clear(LivingEntity e) { clear(e,null); }
    /** Only this exact already-finalized primary strike may retain its fixed collateral list.
     * Ordinary death/owner/leave clears never receive this exception. */
    static void clear(LivingEntity e,DamageSource deathSource) {
        Impact hit=deathSource==null?null:matching(e,deathSource);
        Charge keep=hit!=null&&hit.charge!=null&&hit.entered&&hit.claimed&&hit.finalizedPositive&&hit.committedLethal&&!hit.revoked
                &&hit.charge.releasing&&hit.charge.primary==hit.target&&e.getHealth()<=0
                &&hit.charge.authorized()&&hit.target.identityValid()?hit.charge:null;
        if(keep!=null)keep.primaryKilledByOwnStrike=true;
        Charge retained=keep;
        CHARGES.entrySet().removeIf(x->x.getKey()==e||x.getValue()!=retained
                &&x.getValue().primary!=null&&x.getValue().primary.entity()==e);
        SWEEPS.entrySet().removeIf(x->x.getKey()==e||x.getValue().primary.entity()==e);
    }
    static void stop() { CHARGES.clear();SWEEPS.clear();HITS.clear(); }
}
