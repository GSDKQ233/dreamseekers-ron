// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.economy.DreamFactionAccess;
import com.dreamseekers.neutralcontract.NeutralContracts;
import com.dreamseekers.spore.SporeEffects;
import com.dreamseekers.summon.BoundedSummons;
import com.dreamseekers.unit.AaroncosRightHandUnit;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.resources.Resources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Exact post-impact interval penalty. No damage, payment, serialization or command ownership. */
public final class AaroncosShockSuppression {
    public static final int DURATION=40;
    public static final float FACTOR=1.20f;
    public static final ResourceLocation EFFECT_ID=ResourceLocation.fromNamespaceAndPath("dreamseekers","aaroncos_shock_suppression");
    private static final Map<LivingEntity,Debuff> DEBUFFS=new IdentityHashMap<>();
    private AaroncosShockSuppression() { }
    private static boolean server(LivingEntity entity) {
        return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    private static boolean loaded(SkillIdentity.Stamp stamp) {
        return stamp.valid()&&stamp.entity().getUUID().equals(stamp.uuid())&&stamp.entity().isAddedToWorld()
                &&stamp.level().getEntity(stamp.entity().getId())==stamp.entity();
    }
    private static final class Debuff implements SporeEffects.ManagedNegative {
        final SkillIdentity.Stamp caster,target;
        final Resources account;
        final UUID accountEpoch;
        final RTSPlayer owner;
        final Faction faction;
        final long expiresAt;
        Debuff(AaroncosCombat.Impact hit,int ticks) {
            caster=hit.caster();target=hit.target;account=hit.account();accountEpoch=hit.accountEpoch();
            owner=PlayerServerEvents.getRTSPlayer(caster.owner());faction=owner==null?null:owner.faction;
            expiresAt=target.level().getGameTime()+ticks;
        }
        boolean valid() {
            return target.level().getGameTime()<expiresAt&&loaded(caster)&&loaded(target)
                    &&caster.entity().getClass()==AaroncosRightHandUnit.class
                    &&caster.entity().getType()==DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get()
                    &&caster.level()==target.level()&&!target.entity().isSpectator()
                    &&((AaroncosRightHandUnit)caster.entity()).canHarm(target.entity())
                    &&!NeutralContracts.pending(target.entity())&&BoundedSummons.canAct(target.entity())
                    &&ExtendedResourcesSync.account(caster.owner())==account&&account.extendedStateSupported()
                    &&accountEpoch.equals(account.accountEpoch)&&PlayerServerEvents.getRTSPlayer(caster.owner())==owner
                    &&owner!=null&&owner.faction==faction&&DreamFactionAccess.allows(owner);
        }
        @Override public ResourceLocation id() { return EFFECT_ID; }
        @Override public boolean current() { return DEBUFFS.get(target.entity())==this&&valid(); }
        @Override public boolean remove() { return current()&&DEBUFFS.remove(target.entity(),this); }
    }
    /** Only the private paid hand impact can carry a receipt, and consumes it before any new state. */
    static void install(AaroncosCombat.Impact hit) {
        if(!hit.consumeSuppressionReceipt())return;
        LivingEntity target=hit.target.entity();Debuff previous=DEBUFFS.get(target);
        if(previous!=null&&previous.current())return; // do not stack, extend, or steal an existing source
        if(previous!=null)DEBUFFS.remove(target,previous);
        int ticks=SporeEffects.incomingManagedDuration(target,DURATION);
        if(ticks<=0||!hit.suppressionAuthority())return;
        Debuff effect=new Debuff(hit,ticks);
        if(effect.valid()&&hit.suppressionAuthority())DEBUFFS.put(target,effect);
    }
    public static int remainingTicks(LivingEntity target) {
        if(!server(target))return 0;
        Debuff effect=DEBUFFS.get(target);
        if(effect==null)return 0;
        if(!effect.current()) { DEBUFFS.remove(target,effect);return 0; }
        return (int)(effect.expiresAt-target.level().getGameTime());
    }
    public static float factor(LivingEntity target) { return remainingTicks(target)>0?FACTOR:1; }
    public static SporeEffects.ManagedNegative negative(LivingEntity target) {
        return remainingTicks(target)>0?DEBUFFS.get(target):null;
    }
    static void tick(ServerLevel level) { DEBUFFS.entrySet().removeIf(entry->entry.getKey().level()==level&&!entry.getValue().current()); }
    static void clear(LivingEntity entity) { DEBUFFS.entrySet().removeIf(entry->entry.getKey()==entity||entry.getValue().caster.entity()==entity); }
    static void relationshipsChanged() { DEBUFFS.entrySet().removeIf(entry->!entry.getValue().current()); }
    static void stop() { DEBUFFS.clear(); }
}
