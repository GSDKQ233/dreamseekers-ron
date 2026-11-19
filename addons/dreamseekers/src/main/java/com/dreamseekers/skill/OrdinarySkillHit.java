// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.bonewing.BoneWingBolt;
import com.dreamseekers.unit.WindKnightUnit;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.projectile.EvokerFangs;

/** Provenance for only a real ordinary goal's first victim, or a real projectile's primary hurt call.
 * No damage-source tag, proxy arrow, entire onHit method, secondary splash or magic event grants authority.
 */
public final class OrdinarySkillHit {
    private OrdinarySkillHit() { }
    public static final PrimaryUnitHitHooks.Provider PROJECTILE_PROVIDER=(projectile,victim)->
            captureContractProjectile(projectile,victim,SnailShellFormation.wrapProjectile(projectile,victim,
                    BeetleGuard.wrapProjectile(projectile,victim,captureProjectile(projectile,victim))));
    private static final Map<Projectile,Shot> SHOTS=new WeakHashMap<>();
    private static final ArrayDeque<Frame> FRAMES=new ArrayDeque<>();
    private static final Map<Projectile,com.dreamseekers.spore.SporeCombat.Intent> SPORE_BIRTHS=new WeakHashMap<>();
    private static int sporeConstructDepth;
    private static int spawnDispatchDepth;
    private static LivingEntity crossbowSource;
    private static CrossbowShot crossbowShot;
    private static final class CrossbowShot {
        final LivingEntity source;
        final boolean ordinary;
        AbstractArrow arrow;
        boolean factoryStarted,spawned,sporeAimConfirmed;
        com.dreamseekers.spore.SporeCombat.Intent spore;
        Entity sporeTarget;
        CrossbowShot(LivingEntity source,boolean ordinary) { this.source=source;this.ordinary=ordinary; }
    }
    private static boolean server(Entity entity) {
        return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    private static final class Shot {
        final SkillIdentity.Stamp caster;
        final UUID uuid;
        final boolean ordinary;
        final com.dreamseekers.spore.SporeCombat.Intent spore;
        boolean sporePublished,sporeImpactCaptured;
        Entity observedOwner;
        long ownerEpoch;
        boolean left;
        Shot(Projectile projectile,LivingEntity caster,boolean ordinary) { this(projectile,caster,ordinary,null); }
        Shot(Projectile projectile,LivingEntity caster,boolean ordinary,com.dreamseekers.spore.SporeCombat.Intent spore) {
            this.caster=SkillIdentity.capture(caster);uuid=projectile.getUUID();observedOwner=projectile.getOwner();
            this.ordinary=ordinary;this.spore=spore;
        }
        void observe(Projectile projectile) {
            Entity current=projectile.getOwner();
            if(current!=observedOwner) { observedOwner=current;ownerEpoch++; }
        }
        boolean launchValid(Projectile projectile) {
            return ordinary&&ownerEpoch==0&&caster.valid()&&projectile.getUUID().equals(uuid)
                    &&projectile.level()==caster.level()&&projectile.getOwner()==caster.entity();
        }
    }
    static void projectileJoined(Projectile projectile) {
        if(server(projectile)&&projectile.getOwner() instanceof LivingEntity caster
                &&caster instanceof Unit&&caster instanceof AttackerUnit)
            SHOTS.putIfAbsent(projectile,new Shot(projectile,caster,false));
    }
    /** Called only at a known ordinary unit factory's exact actual spawn argument, before Join callbacks.
     * Merely arming, owning, loading or manually publishing another arrow never grants this provenance.
     */
    public static boolean spawn(Level world,Entity entity,LivingEntity owner) { return spawn(world,entity,owner,null); }
    public static boolean spawn(Level world,Entity entity,LivingEntity owner,Entity actualTarget) {
        return OrdinaryAttackGate.publish(owner,entity,()->spawnUnchecked(world,entity,owner,actualTarget));
    }
    private static boolean spawnUnchecked(Level world,Entity entity,LivingEntity owner,Entity actualTarget) {
        if(!(world instanceof ServerLevel level)||!level.getServer().isSameThread()
                ||!(entity instanceof Projectile projectile)||!(owner instanceof Unit)||!(owner instanceof AttackerUnit)
                ||owner.level()!=level||level.getEntity(owner.getUUID())!=owner||!owner.isAlive()
                ||projectile.level()!=level||projectile.getOwner()!=owner||projectile.isRemoved()
                ||level.getEntity(projectile.getUUID())!=null||spawnDispatchDepth>0||!FRAMES.isEmpty()
                ||SHOTS.containsKey(projectile))return world.addFreshEntity(entity);
        var intended=SPORE_BIRTHS.remove(projectile);
        if(!(actualTarget instanceof com.dreamseekers.spore.JungleSporeCarrier carrier)
                ||intended==null||!intended.validFor(carrier)||!com.dreamseekers.spore.SporeCombat.stillDirected(intended))intended=null;
        Shot shot=new Shot(projectile,owner,true,intended);SHOTS.put(projectile,shot);
        boolean accepted=false;spawnDispatchDepth++;
        try { accepted=world.addFreshEntity(entity);return accepted; }
        finally {
            spawnDispatchDepth--;if(!accepted)shot.left=true;
            shot.sporePublished=accepted&&shot.spore!=null&&shot.launchValid(projectile)&&!projectile.isRemoved()
                    &&level.getEntity(projectile.getUUID())==projectile&&level.getEntity(projectile.getId())==projectile
                    &&com.dreamseekers.spore.SporeCombat.stillDirected(shot.spore);
        }
    }
    /** Exact actual factory only. Snapshot the accepted target before constructor callbacks, not at Join. */
    public static <P extends Projectile> P constructSporeShot(LivingEntity source,Entity actualTarget,Supplier<P> factory) {
        if(!server(source))return factory.get();
        var intended=sporeConstructDepth==0&&spawnDispatchDepth==0&&FRAMES.isEmpty()
                &&!com.dreamseekers.spore.SporeCombat.inImpact()
                ?com.dreamseekers.spore.SporeCombat.directedIntent(source,actualTarget):null;
        sporeConstructDepth++;
        try {
            P projectile=factory.get();
            if(intended!=null&&projectile!=null&&projectile.level()==source.level()&&!projectile.isRemoved())
                SPORE_BIRTHS.putIfAbsent(projectile,intended);
            return projectile;
        } finally { sporeConstructDepth--; }
    }
    /** The source is tied to Pillager's real ordinary shooting call, not any player's crossbow use. */
    public static void crossbow(LivingEntity owner,Runnable shooting) {
        OrdinaryAttackGate.ranged(owner,()->ordinaryCrossbow(owner,shooting));
    }
    private static void ordinaryCrossbow(LivingEntity owner,Runnable shooting) {
        if(!server(owner)||!FRAMES.isEmpty()||spawnDispatchDepth>0||crossbowSource!=null) { shooting.run();return; }
        crossbowSource=owner;
        try { shooting.run(); } finally { crossbowSource=null; }
    }
    /** Each actual single-projectile call gets its own frame, including callback-reentrant calls.
     * A nested performShooting cannot borrow the outer Pillager's ordinary launch authority.
     */
    public static void crossbowProjectile(LivingEntity owner,Runnable shooting) {
        if(!server(owner)) { shooting.run();return; }
        CrossbowShot previous=crossbowShot;
        crossbowShot=new CrossbowShot(owner,previous==null&&crossbowSource==owner
                &&spawnDispatchDepth==0&&FRAMES.isEmpty());
        try { shooting.run(); } finally { crossbowShot=previous; }
    }
    /** Bind only this single call's actual getArrow return; never an arrow from a nested factory callback. */
    public static AbstractArrow crossbowArrow(LivingEntity owner,Supplier<AbstractArrow> factory) {
        if(!server(owner))return factory.get();
        CrossbowShot shot=crossbowShot;
        if(shot==null||!shot.ordinary||shot.source!=owner||shot.factoryStarted)return factory.get();
        shot.factoryStarted=true;
        Entity actualTarget=owner instanceof Mob mob?mob.getTarget():null;
        AbstractArrow arrow=constructSporeShot(owner,actualTarget,factory);
        if(crossbowShot==shot) { shot.arrow=arrow;shot.spore=SPORE_BIRTHS.get(arrow); }
        return arrow;
    }
    /** Confirm the same actual aim parameter; a constructor/sound callback may never retarget the old token. */
    public static void confirmSporeCrossbowAim(LivingEntity owner,Entity actualTarget,Projectile projectile) {
        if(!server(owner))return;
        CrossbowShot shot=crossbowShot;
        if(shot==null||!shot.ordinary||shot.source!=owner||shot.arrow!=projectile||shot.sporeAimConfirmed)return;
        shot.sporeAimConfirmed=true;shot.sporeTarget=actualTarget;
        if(!(actualTarget instanceof com.dreamseekers.spore.JungleSporeCarrier carrier)||shot.spore==null
                ||!shot.spore.validFor(carrier)||!com.dreamseekers.spore.SporeCombat.stillDirected(shot.spore)) {
            shot.spore=null;SPORE_BIRTHS.remove(projectile);
        }
    }
    /** Only this proven, not-yet-published ordinary shot may move the native tiny-target correction from Z to Y.
     * Preserve distance compensation, shot rotation, velocity and every unrelated target's legacy aim.
     */
    public static net.minecraft.world.phys.Vec3 sporeCrossbowAim(LivingEntity owner,Entity actualTarget,
            Projectile projectile,net.minecraft.world.phys.Vec3 legacy) {
        if(!server(owner))return legacy;
        CrossbowShot shot=crossbowShot;
        if(shot==null||!shot.ordinary||shot.spawned||shot.source!=owner||crossbowSource!=owner
                ||shot.arrow!=projectile||projectile.getOwner()!=owner||!shot.sporeAimConfirmed
                ||shot.sporeTarget!=actualTarget||shot.spore==null
                ||!(actualTarget instanceof com.dreamseekers.spore.JungleSporeCarrier carrier)
                ||!shot.spore.validFor(carrier)||!com.dreamseekers.spore.SporeCombat.stillDirected(shot.spore))return legacy;
        float lowEyeCorrection=carrier.getEyeHeight()<=1.0F?1.0F-carrier.getEyeHeight():0;
        return new net.minecraft.world.phys.Vec3(legacy.x,legacy.y-lowEyeCorrection,actualTarget.getZ()-owner.getZ());
    }
    public static boolean spawnCrossbow(Level world,Entity entity) {
        if(entity instanceof Projectile projectile&&projectile.getOwner() instanceof LivingEntity source)
            return OrdinaryAttackGate.publish(source,entity,()->spawnCrossbowUnchecked(world,entity));
        return spawnCrossbowUnchecked(world,entity);
    }
    private static boolean spawnCrossbowUnchecked(Level world,Entity entity) {
        if(!(world instanceof ServerLevel level)||!level.getServer().isSameThread()
                ||!(entity instanceof AbstractArrow arrow))return world.addFreshEntity(entity);
        CrossbowShot shot=crossbowShot;
        if(shot==null||!shot.ordinary||shot.spawned||shot.arrow!=arrow
                ||shot.source!=crossbowSource||arrow.getOwner()!=shot.source)return world.addFreshEntity(entity);
        shot.spawned=true; // publish the reservation before EntityJoin callbacks
        Entity intendedTarget=shot.sporeAimConfirmed&&shot.spore!=null?shot.sporeTarget:null;
        return spawn(world,entity,shot.source,intendedTarget);
    }
    static void projectileLeft(Projectile projectile) {
        if(!server(projectile))return;
        Shot shot=SHOTS.get(projectile);if(shot!=null)shot.left=true;
        // Keep only the weak-key tombstone: a consumed bolt's already-captured impact still observes owner ABA.
    }
    public static void projectileOwnerChanged(Projectile projectile) {
        if(!server(projectile))return;
        Shot shot=SHOTS.get(projectile);if(shot!=null)shot.observe(projectile);
    }
    private static boolean actor(LivingEntity source,LivingEntity victim) {
        return source instanceof Unit&&source instanceof AttackerUnit&&source.level() instanceof ServerLevel level
                &&level.getServer().isSameThread()&&level.getEntity(source.getUUID())==source
                &&level.getEntity(victim.getUUID())==victim&&CoordinatedMark.enemy(source,victim)
                &&(!(source instanceof WindKnightUnit knight)||!WindDash.isActive(knight));
    }
    /** Called at the goal's exact virtual doHurtTarget call, preserving native overrides and evasion. */
    public static boolean melee(Mob source,Entity victim) {
        // Only the actual outer reservation is a skill; inAttempt alone cannot exempt callback ordinary hits.
        if(BeetleAdvance.canAttempt(source,victim))return ShadowCorrosionTouch.melee(source,victim,()->ordinaryMelee(source,victim));
        return OrdinaryAttackGate.melee(source,victim,()->FriendlyDebtContract.melee(source,victim,
                ()->ShadowCorrosionTouch.melee(source,victim,()->ordinaryMelee(source,victim))));
    }
    private static boolean ordinaryMelee(Mob source,Entity victim) {
        // A reserved skill primary never enters the Bone frame, including callback-nested ordinary calls.
        if(BeetleAdvance.inAttempt(source))return source.doHurtTarget(victim);
        if(BeetleAdvance.canAttempt(source,victim))
            return BeetleAdvance.melee((com.dreamseekers.unit.BeetleUnit)source,(LivingEntity)victim);
        if(server(source)&&FRAMES.isEmpty()&&victim instanceof com.dreamseekers.spore.JungleSporeCarrier carrier)
            return com.dreamseekers.spore.SporeCombat.melee(source,carrier);
        if(!server(source)||!FRAMES.isEmpty()||com.dreamseekers.spore.SporeCombat.inImpact()||!(victim instanceof LivingEntity target)||!actor(source,target))return source.doHurtTarget(victim);
        CoordinatedMark.Mark mark=CoordinatedMark.find(target,((Unit)source).getOwnerName());
        if(mark==null)return source.doHurtTarget(victim);
        Frame frame=new Frame(source,target,mark,null,null,false);
        FRAMES.push(frame);
        try { return source.doHurtTarget(victim); }
        finally { FRAMES.removeFirstOccurrence(frame); }
    }
    public static boolean mayHitSpore(Projectile projectile,com.dreamseekers.spore.JungleSporeCarrier carrier) {
        if(!server(projectile)||projectile.isRemoved()||!(projectile.level() instanceof ServerLevel level)
                ||level.getEntity(projectile.getUUID())!=projectile)return false;
        Shot shot=SHOTS.get(projectile);
        return shot!=null&&!shot.left&&shot.launchValid(projectile)&&shot.sporePublished&&!shot.sporeImpactCaptured
                &&shot.spore!=null&&shot.spore.validFor(carrier);
    }
    private static PrimaryUnitHitHooks.Impact captureSporeProjectile(Projectile projectile,com.dreamseekers.spore.JungleSporeCarrier carrier) {
        if(!FRAMES.isEmpty()||!mayHitSpore(projectile,carrier))return null;
        Shot shot=SHOTS.get(projectile);
        boolean consumed=consumedProjectile(projectile);
        PrimaryUnitHitHooks.Impact impact=com.dreamseekers.spore.SporeCombat.capture(shot.spore,carrier,()->
                shot.launchValid(projectile)&&shot.sporePublished
                    &&(consumed&&projectile.getRemovalReason()==Entity.RemovalReason.DISCARDED
                        ||!shot.left&&!projectile.isRemoved()&&shot.caster.level().getEntity(shot.uuid)==projectile));
        if(impact!=null)shot.sporeImpactCaptured=true;
        return impact;
    }
    /** Independent post-hit contract role; never grants Bone or trusts an anonymous/proxy publication. */
    private static PrimaryUnitHitHooks.Impact captureContractProjectile(Projectile projectile,Entity victim,PrimaryUnitHitHooks.Impact original) {
        if(!server(projectile)||!FRAMES.isEmpty()||!(victim instanceof LivingEntity target)
                ||!(target instanceof Unit)||target instanceof com.dreamseekers.spore.JungleSporeCarrier
                ||!(projectile.level() instanceof ServerLevel level)||projectile.isRemoved()||!projectile.isAddedToWorld()
                ||level.getEntity(projectile.getUUID())!=projectile||level.getEntity(projectile.getId())!=projectile)return original;
        Shot shot=SHOTS.get(projectile);
        if(shot==null||shot.left||!shot.launchValid(projectile))return original;
        boolean consumed=consumedProjectile(projectile);
        return FriendlyDebtContract.wrapOrdinary(shot.caster,target,()->shot.launchValid(projectile)
                &&(consumed&&projectile.getRemovalReason()==Entity.RemovalReason.DISCARDED
                   ||!shot.left&&!projectile.isRemoved()&&level.getEntity(projectile.getUUID())==projectile
                     &&level.getEntity(projectile.getId())==projectile),false,original);
    }
    private static PrimaryUnitHitHooks.Impact captureProjectile(Projectile projectile,Entity victim) {
        if(server(projectile)&&victim instanceof com.dreamseekers.spore.JungleSporeCarrier carrier)
            return captureSporeProjectile(projectile,carrier);
        if(!server(projectile)||!FRAMES.isEmpty()||!(victim instanceof LivingEntity target)||!(projectile.level() instanceof ServerLevel level)
                ||!level.getServer().isSameThread()||projectile.isRemoved()||level.getEntity(projectile.getUUID())!=projectile
                ||!(projectile.getOwner() instanceof LivingEntity source)||!actor(source,target))return null;
        Shot shot=SHOTS.get(projectile);
        if(shot==null||shot.left||!shot.launchValid(projectile))return null;
        CoordinatedMark.Mark mark=CoordinatedMark.find(target,((Unit)source).getOwnerName());
        if(mark==null)return null;
        Frame frame=new Frame(source,target,mark,projectile,shot,consumedProjectile(projectile));
        return new PrimaryUnitHitHooks.Impact() {
            private boolean used;
            @Override public boolean hurt(DamageSource damage,float amount) {
                if(!server(projectile))return target.hurt(damage,amount);
                if(used)return false;
                used=true; // this captured primary impact cannot be replayed by callback code
                if(damage.getEntity()!=source)return target.hurt(damage,amount);
                frame.damage=damage;
                FRAMES.push(frame);
                try { return target.hurt(damage,amount); }
                finally { FRAMES.removeFirstOccurrence(frame); }
            }
        };
    }
    public static boolean inImpact() {
        net.minecraft.server.MinecraftServer server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        return server!=null&&server.isSameThread()&&!FRAMES.isEmpty();
    }
    /** Only fangs from the completed ordinary Evoker factory may carry the independent Bone mark. */
    static PrimaryUnitHitHooks.Impact captureFang(EvokerFangs fangs,LivingEntity target) {
        PrimaryUnitHitHooks.Impact ordinary=captureBoneFang(fangs,target);
        if(!server(fangs)||!FRAMES.isEmpty()||!(target instanceof Unit)||target instanceof com.dreamseekers.spore.JungleSporeCarrier)return ordinary;
        OrdinaryFangOrigin.Birth birth=OrdinaryFangOrigin.capture(fangs);
        return birth==null?ordinary:FriendlyDebtContract.wrapOrdinary(birth.caster,target,()->birth.valid(fangs),true,ordinary);
    }
    private static PrimaryUnitHitHooks.Impact captureBoneFang(EvokerFangs fangs,LivingEntity target) {
        if(!server(fangs)||!FRAMES.isEmpty())return null;
        LivingEntity source=fangs.getOwner();
        if(target instanceof com.dreamseekers.spore.JungleSporeCarrier carrier) {
            OrdinaryFangOrigin.Birth born=OrdinaryFangOrigin.capture(fangs);
            if(born==null||!born.sporePublished||born.sporeImpactCaptured||born.spore==null||!born.spore.validFor(carrier))return null;
            var impact=com.dreamseekers.spore.SporeCombat.capture(born.spore,carrier,()->born.valid(fangs)&&born.sporePublished);
            if(impact!=null)born.sporeImpactCaptured=true;
            return impact;
        }
        if(source==null||!actor(source,target))return null;
        OrdinaryFangOrigin.Birth birth=OrdinaryFangOrigin.capture(fangs);
        if(birth==null)return null;
        CoordinatedMark.Mark mark=CoordinatedMark.find(target,((Unit)source).getOwnerName());
        if(mark==null)return null;
        Frame frame=new Frame(source,target,mark,()->birth.valid(fangs));
        return new PrimaryUnitHitHooks.Impact() {
            private boolean used;
            @Override public boolean hurt(DamageSource damage,float amount) {
                if(!server(fangs))return target.hurt(damage,amount);
                if(used)return false;
                used=true;
                if(damage.getEntity()!=source)return target.hurt(damage,amount);
                frame.damage=damage;FRAMES.push(frame);
                try { return target.hurt(damage,amount); }
                finally { FRAMES.removeFirstOccurrence(frame); }
            }
        };
    }
    static boolean consumedProjectile(Projectile projectile) {
        return projectile instanceof BoneWingBolt
                ||projectile instanceof com.dreamseekers.shadow.AshBoneWingBolt
                ||projectile instanceof com.dreamseekers.shadow.FriendlyGhostBolt
                ||projectile instanceof com.dreamseekers.shadow.SquealWaveBolt
                ||projectile instanceof com.dreamseekers.shadow.WeepingSquealWaveBolt
                ||projectile instanceof com.dreamseekers.stone.StoneProjectile;
    }
    static final class Frame {
        final SkillIdentity.Stamp caster,target;
        final CoordinatedMark.Mark mark;
        final Projectile projectile;
        final Shot shot;
        final boolean allowsConsumedBolt;
        final BooleanSupplier additionalOrigin;
        DamageSource damage;
        boolean entered,revoked;
        int suspended;
        Frame(LivingEntity caster,LivingEntity target,CoordinatedMark.Mark mark,Projectile projectile,Shot shot,boolean allowsConsumedBolt) {
            this.caster=SkillIdentity.capture(caster);this.target=SkillIdentity.capture(target);this.mark=mark;
            this.projectile=projectile;this.shot=shot;this.allowsConsumedBolt=allowsConsumedBolt;
            additionalOrigin=()->true;
        }
        Frame(LivingEntity caster,LivingEntity target,CoordinatedMark.Mark mark,BooleanSupplier additionalOrigin) {
            this.caster=SkillIdentity.capture(caster);this.target=SkillIdentity.capture(target);this.mark=mark;
            projectile=null;shot=null;allowsConsumedBolt=false;this.additionalOrigin=additionalOrigin;
        }
        boolean valid() {
            if(suspended>0||revoked||!additionalOrigin.getAsBoolean()||!mark.current()||!caster.valid()||!target.valid()||!caster.owner().equals(mark.caster.owner())
                    ||!CoordinatedMark.enemy(caster.entity(),target.entity()))return false;
            if(projectile==null)return true;
            if(!shot.launchValid(projectile))return false;
            // Only the known single-impact paid bolt may deliberately discard itself between capture and hurt.
            if(allowsConsumedBolt&&projectile.getRemovalReason()==Entity.RemovalReason.DISCARDED)return true;
            return !shot.left&&!projectile.isRemoved()&&caster.level().getEntity(projectile.getUUID())==projectile;
        }
        double vulnerability() { return valid()?CoordinatedMark.VULNERABILITY:0; }
    }
    /** Bind before LivingAttack callbacks. Secondary victims/DS never inherit a goal's first impact. */
    public static void observeHurt(LivingEntity victim,DamageSource damage) {
        if(!server(victim))return;
        Frame frame=FRAMES.peek();
        if(frame==null||frame.suspended>0||frame.target.entity()!=victim||damage.getEntity()!=frame.caster.entity())return;
        if(frame.damage==null)frame.damage=damage;
        if(frame.damage!=damage)return;
        // Unlike a Wind skill strike, this is an existing ordinary attack: never cancel its base damage.
        // A recursive same-DS invocation contaminates this impact only; neither call may claim its bonus.
        if(frame.entered)frame.revoked=true;
        frame.entered=true;
    }
    static Frame matching(LivingEntity victim,DamageSource damage) {
        if(!server(victim))return null;
        Frame frame=FRAMES.peek();
        return frame!=null&&frame.suspended==0&&frame.entered&&frame.damage==damage&&frame.target.entity()==victim?frame:null;
    }
    /** Suspend only the exact known Rabbit pre-hurt sound callback, preserving the outer primary. */
    public static void rabbitSound(LivingEntity source,Runnable sound) {
        OrdinaryAttackGate.rabbitSound(source,()->ordinaryRabbitSound(source,sound));
    }
    private static void ordinaryRabbitSound(LivingEntity source,Runnable sound) {
        if(!server(source)) { sound.run();return; }
        Frame frame=FRAMES.peek();
        if(frame==null||frame.caster.entity()!=source) { sound.run();return; }
        frame.suspended++;
        try { sound.run(); } finally { frame.suspended--; }
    }
    static void stop() { SHOTS.clear();SPORE_BIRTHS.clear();sporeConstructDepth=0;FRAMES.clear();spawnDispatchDepth=0;crossbowSource=null;crossbowShot=null; }
}
