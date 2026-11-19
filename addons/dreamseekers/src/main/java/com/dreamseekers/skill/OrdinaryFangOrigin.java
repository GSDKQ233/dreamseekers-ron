// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import com.solegendary.reignofnether.unit.goals.GenericTargetedSpellGoal;
import com.solegendary.reignofnether.unit.units.villagers.EvokerUnit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Bone-only provenance. The established independent Wind fang policy never consults these records. */
public final class OrdinaryFangOrigin {
    private OrdinaryFangOrigin() { }
    private static final Map<EvokerFangs,Birth> BIRTHS=new WeakHashMap<>();
    private static EvokerUnit casting;
    private static com.dreamseekers.spore.SporeCombat.Intent castingSpore;
    private static final Map<EvokerFangs,com.dreamseekers.spore.SporeCombat.Intent> SPORE_BIRTHS=new WeakHashMap<>();
    private static int dispatchDepth,sporeConstructDepth;
    private static boolean server(Entity entity) { return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread(); }
    static final class Birth {
        final SkillIdentity.Stamp caster;
        final UUID uuid;
        Entity observedOwner;
        long ownerEpoch;
        boolean revoked,sporePublished,sporeImpactCaptured;
        final com.dreamseekers.spore.SporeCombat.Intent spore;
        Birth(EvokerFangs fang,EvokerUnit source) {
            caster=SkillIdentity.capture(source);uuid=fang.getUUID();observedOwner=fang.getOwner();
            spore=SPORE_BIRTHS.remove(fang);
        }
        boolean valid(EvokerFangs fang) {
            return !revoked&&ownerEpoch==0&&caster.valid()&&fang.getOwner()==caster.entity()
                    &&fang.getUUID().equals(uuid)&&fang.level()==caster.level()&&!fang.isRemoved()
                    &&caster.level().getEntity(uuid)==fang;
        }
    }
    /** Only the completed native Evoker ordinary-fangs goal's actual entity callback permits its factory. */
    public static void cast(Mob mob,GenericTargetedSpellGoal goal,Consumer<Object> callback,Object victim) {
        if(mob instanceof EvokerUnit source&&server(source)&&goal==source.getCastFangsGoal())
            OrdinaryAttackGate.ranged(source,()->ordinaryCast(mob,goal,callback,victim));
        else ordinaryCast(mob,goal,callback,victim);
    }
    private static void ordinaryCast(Mob mob,GenericTargetedSpellGoal goal,Consumer<Object> callback,Object victim) {
        if(!(mob instanceof EvokerUnit source)||!server(source)||goal!=source.getCastFangsGoal()
                ||goal.getTargetEntity()!=victim||!goal.isCasting()||goal.getChannelTicks()<goal.getChannelTicksMax()
                ||casting!=null||dispatchDepth>0||OrdinarySkillHit.inImpact()) { callback.accept(victim);return; }
        casting=source;
        castingSpore=victim instanceof Entity entity?com.dreamseekers.spore.SporeCombat.directedIntent(source,entity):null;
        try { callback.accept(victim); } finally { casting=null;castingSpore=null; }
    }
    /** Exact constructor return only; callback-born fangs cannot borrow an outer target token. */
    public static EvokerFangs constructSporeFang(LivingEntity owner,java.util.function.Supplier<EvokerFangs> factory) {
        if(!server(owner))return factory.get();
        var intended=casting==owner&&dispatchDepth==0&&sporeConstructDepth==0?castingSpore:null;
        sporeConstructDepth++;
        try {
            EvokerFangs fang=factory.get();
            if(intended!=null&&fang!=null)SPORE_BIRTHS.putIfAbsent(fang,intended);
            return fang;
        } finally { sporeConstructDepth--; }
    }
    /** Exact freshly constructed fang parameter, before dispatching EntityJoin callbacks. */
    public static boolean spawn(Level world,Entity entity,EvokerUnit source) {
        return OrdinaryAttackGate.publish(source,entity,()->spawnUnchecked(world,entity,source));
    }
    private static boolean spawnUnchecked(Level world,Entity entity,EvokerUnit source) {
        if(!(world instanceof ServerLevel level)||!level.getServer().isSameThread()
                ||casting!=source||dispatchDepth>0||OrdinarySkillHit.inImpact()
                ||!(entity instanceof EvokerFangs fang)||fang.getOwner()!=source||fang.level()!=level
                ||source.level()!=level||level.getEntity(source.getUUID())!=source||!source.isAlive()
                ||fang.isRemoved()||level.getEntity(fang.getUUID())!=null||BIRTHS.containsKey(fang))return world.addFreshEntity(entity);
        Birth birth=new Birth(fang,source);BIRTHS.put(fang,birth);boolean accepted=false;dispatchDepth++;
        try { accepted=world.addFreshEntity(entity);return accepted; }
        finally {
            dispatchDepth--;if(!accepted)birth.revoked=true;
            birth.sporePublished=accepted&&birth.spore!=null&&birth.valid(fang)
                    &&com.dreamseekers.spore.SporeCombat.stillDirected(birth.spore);
        }
    }
    public static void ownerChanged(EvokerFangs fang) {
        if(!server(fang))return;
        Birth birth=BIRTHS.get(fang);if(birth==null)return;
        Entity owner=fang.getOwner();if(owner!=birth.observedOwner) { birth.observedOwner=owner;birth.ownerEpoch++; }
    }
    static Birth capture(EvokerFangs fang) {
        if(!server(fang))return null;
        Birth birth=BIRTHS.get(fang);return birth!=null&&birth.valid(fang)?birth:null;
    }
    static void left(EvokerFangs fang) {
        if(!server(fang))return;
        Birth birth=BIRTHS.get(fang);if(birth!=null)birth.revoked=true;
    }
    static void stop() { BIRTHS.clear();SPORE_BIRTHS.clear();casting=null;castingSpore=null;dispatchDepth=0;sporeConstructDepth=0; }
}
