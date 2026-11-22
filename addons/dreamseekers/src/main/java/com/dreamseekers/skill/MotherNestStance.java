// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.unit.BeetleMotherUnit;
import com.dreamseekers.unitprogression.AdvancedUnitResearch;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Transient stationary stance. It removes steering, never gravity/knockback or queued commands.
 * An order, ownership epoch, physical displacement or reload retires it without restoring orders. */
public final class MotherNestStance {
    public static final double DISPLACEMENT_SQUARED=.0625; // 0.25 blocks in any axis, including falling
    private static final Map<BeetleMotherUnit,Nest> ACTIVE=new IdentityHashMap<>();
    private MotherNestStance(){}
    private static boolean server(Entity e){return e.level() instanceof ServerLevel l&&l.getServer().isSameThread();}
    private static boolean disabled(BeetleMotherUnit s){return s.hasEffect(MobEffectRegistrar.STUN.get())
        ||s.hasEffect(MobEffectRegistrar.FREEZE.get())||s.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get());}
    private static final class Nest {
        final BeetleMotherUnit source;final SkillIdentity.Stamp identity;final Vec3 origin;
        final long command;final Resources account;final UUID accountEpoch;final RTSPlayer owner;
        Nest(BeetleMotherUnit s){source=s;identity=SkillIdentity.capture(s);origin=s.position();command=s.rts().commandRevision;
            account=ExtendedResourcesSync.account(s.getOwnerName());accountEpoch=account.accountEpoch;owner=PlayerServerEvents.getRTSPlayer(s.getOwnerName());}
        boolean valid(){return BeetleAdvance.loaded(identity)&&source.rts().commandRevision==command
            &&source.position().distanceToSqr(origin)<=DISPLACEMENT_SQUARED&&!disabled(source)
            &&!UnitServerEvents.hasPendingImmediateAction(source.getId())
            &&ExtendedResourcesSync.account(identity.owner())==account&&accountEpoch.equals(account.accountEpoch)
            &&PlayerServerEvents.getRTSPlayer(identity.owner())==owner
            &&AdvancedUnitResearch.unlocked(identity.owner(),AdvancedUnitResearch.MOTHER_NEST_ID,false);}
    }
    public static boolean active(LivingEntity e){if(!(e instanceof BeetleMotherUnit s)||!server(e))return false;
        Nest n=ACTIVE.get(s);if(n==null)return false;if(n.valid())return true;ACTIVE.remove(s,n);return false;}
    static SkillResult toggle(BeetleMotherUnit s){
        if(!server(s)||!s.isAlive()||s.isRemoved()||!s.isAddedToWorld())return SkillResult.INVALID_SOURCE;
        if(active(s)){ACTIVE.remove(s);return SkillResult.SUCCESS;}
        ACTIVE.remove(s);
        if(!AdvancedUnitResearch.unlocked(s.getOwnerName(),AdvancedUnitResearch.MOTHER_NEST_ID,false))return SkillResult.DISABLED;
        if(disabled(s)||UnitServerEvents.hasPendingImmediateAction(s.getId()))return SkillResult.DISABLED;
        SkillIdentity.Stamp identity=SkillIdentity.capture(s);long command=s.rts().commandRevision,revision=s.rts().skills().revision();
        if(!BeetleAdvance.loaded(identity))return SkillResult.INVALID_SOURCE;
        Unit.fullResetBehaviours(s);
        if(!BeetleAdvance.loaded(identity)||s.rts().commandRevision!=command+1||s.rts().skills().revision()!=revision)return SkillResult.REPLAY;
        if(disabled(s)||UnitServerEvents.hasPendingImmediateAction(s.getId())
            ||!AdvancedUnitResearch.unlocked(s.getOwnerName(),AdvancedUnitResearch.MOTHER_NEST_ID,false))return SkillResult.DISABLED;
        ACTIVE.put(s,new Nest(s));
        if(s.getNavigation() instanceof com.dreamseekers.beetlemother.BeetleMotherGroundNavigation nav)nav.stopForNest();
        return SkillResult.SUCCESS;
    }
    public static void commandChanged(BeetleMotherUnit s){if(server(s))ACTIVE.remove(s);}
    private static boolean inReach(BeetleMotherUnit s,LivingEntity t){
        double reach=OrdinaryMeleeReach.rawSquared(s,s,t);
        return Double.isFinite(reach)&&reach>=0&&s.distanceToSqr(t)<=reach;
    }
    private static boolean legalTarget(BeetleMotherUnit s,LivingEntity t){
        return t!=null&&s.canHarm(t)&&!t.isSpectator()&&!t.isInvisible()&&t.isAddedToWorld()
            &&t.level()==s.level()&&s.level() instanceof ServerLevel l&&l.getEntity(t.getUUID())==t&&l.getEntity(t.getId())==t
            &&l.hasChunkAt(s.blockPosition())&&l.hasChunkAt(t.blockPosition())&&l.getWorldBorder().isWithinBounds(s.blockPosition())&&l.getWorldBorder().isWithinBounds(t.blockPosition())
            &&!(t instanceof Unit u&&u.isGarrisoned())&&inReach(s,t)&&BeetleMotherCommandAura.hasLoadedLineOfSight(l,s,t);
    }
    public static boolean permitsMelee(BeetleMotherUnit s,Entity t){return !active(s)||
        (!s.isNoAi()&&!s.isCasting()&&!s.hasEffect(MobEffectRegistrar.DISARM.get())&&t instanceof LivingEntity l&&legalTarget(s,l));}
    /** Natural melee goal retains its own attack timer; only nearest in-place target selection is added. */
    public static void tickCombat(BeetleMotherUnit s){
        if(!server(s)||!active(s))return;
        LivingEntity current=s.getTargetGoal().getTarget();
        boolean can=!s.isNoAi()&&!s.isCasting()&&s.getAggressiveWhenIdle()&&!s.hasEffect(MobEffectRegistrar.DISARM.get());
        if(current!=null&&(!can||!legalTarget(s,current))){s.setUnitAttackTarget(null);current=null;}
        if(current!=null||!can||s.tickCount%4!=0)return;
        var nearest=s.level().getEntitiesOfClass(LivingEntity.class,s.getBoundingBox().inflate(6),t->legalTarget(s,t)).stream()
            .min(Comparator.comparingDouble((LivingEntity t)->s.distanceToSqr(t)).thenComparing(t->t.getUUID().toString())).orElse(null);
        if(nearest!=null&&active(s)&&legalTarget(s,nearest))s.setUnitAttackTarget(nearest);
    }
    static void tick(ServerLevel l){ACTIVE.entrySet().removeIf(e->e.getKey().level()==l&&!e.getValue().valid());}
    static void clear(LivingEntity e){if(e instanceof BeetleMotherUnit s)ACTIVE.remove(s);}
    static void stop(){ACTIVE.clear();}
}
