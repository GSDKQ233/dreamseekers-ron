// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unit.*;
import com.dreamseekers.unitprogression.AdvancedUnitResearch;
import com.dreamseekers.neutralcontract.NeutralContracts;
import com.dreamseekers.summon.BoundedSummons;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Research-owned, ordinary APS only. Each hand needs any real opposite hand nearby; no pair token,
 * shared cast/payment, summon or skill damage multiplier exists. A fixed category maximum is used. */
public final class AaroncosHarmony {
    public static final int RANGE=8, PERIOD=10, LEASE_TICKS=20;
    public static final double ATTACK_SPEED=.10;
    static final String KEY="aaroncos_harmony";
    private AaroncosHarmony(){}
    private static boolean hand(LivingEntity e){return e.getClass()==AaroncosLeftHandUnit.class&&e.getType()==DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get()
        ||e.getClass()==AaroncosRightHandUnit.class&&e.getType()==DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get();}
    private static boolean eligible(LivingEntity e){return hand(e)&&e instanceof SkillUnit u&&e.isAlive()&&!e.isRemoved()&&!e.isSpectator()
        &&e.isAddedToWorld()&&e.level() instanceof ServerLevel l&&l.getServer().isSameThread()
        &&l.getEntity(e.getUUID())==e&&l.getEntity(e.getId())==e&&l.hasChunkAt(e.blockPosition())
        &&!BoundedSummons.isTemporary(e)&&!NeutralContracts.pending(e)
        &&AdvancedUnitResearch.unlocked(u.getOwnerName(),AdvancedUnitResearch.HAND_HARMONY_ID,false);}
    private static boolean paired(LivingEntity a,LivingEntity b){return eligible(a)&&eligible(b)&&a.getClass()!=b.getClass()
        &&a.level()==b.level()&&((SkillUnit)a).getOwnerName().equals(((SkillUnit)b).getOwnerName())
        &&a.distanceToSqr(b)<=RANGE*RANGE&&BeetleMotherCommandAura.hasLoadedLineOfSight((ServerLevel)a.level(),a,b);}
    private static LivingEntity partner(LivingEntity source){
        if(!eligible(source))return null;
        return source.level().getEntitiesOfClass(LivingEntity.class,source.getBoundingBox().inflate(RANGE),e->paired(source,e)).stream()
            .min(Comparator.comparingDouble((LivingEntity e)->source.distanceToSqr(e)).thenComparing(e->e.getUUID().toString())).orElse(null);
    }
    /** Read-only owner-private display; no discovery mutation, load or client authority. */
    public static boolean active(SkillUnit source){return source!=null&&SkillEffects.hasNamedBuff(source.mob(),KEY,BuffKind.ATTACK_SPEED);}
    static final class Lease {
        final SkillIdentity.Stamp caster,target;final long expiresAt;
        final Resources account;final UUID epoch;final RTSPlayer owner;boolean retired;
        private Lease(LivingEntity source,LivingEntity partner){caster=SkillIdentity.capture(partner);target=SkillIdentity.capture(source);
            account=ExtendedResourcesSync.account(target.owner());epoch=account.accountEpoch;owner=PlayerServerEvents.getRTSPlayer(target.owner());
            expiresAt=target.level().getGameTime()+LEASE_TICKS;}
        boolean valid(){if(retired)return false;boolean valid=target.level().getGameTime()<expiresAt&&BeetleAdvance.loaded(caster)&&BeetleAdvance.loaded(target)
            &&ExtendedResourcesSync.account(target.owner())==account&&epoch.equals(account.accountEpoch)
            &&PlayerServerEvents.getRTSPlayer(target.owner())==owner&&paired(target.entity(),caster.entity());if(!valid)retired=true;return valid;}
    }
    static void tick(ServerLevel level){
        if(!level.getServer().isSameThread()||level.getGameTime()%PERIOD!=0)return;
        for(LivingEntity source:UnitServerEvents.getAllUnits())if(source.level()==level&&eligible(source)){
            LivingEntity partner=partner(source);if(partner==null)continue;
            Lease lease=new Lease(source,partner);if(lease.valid())SkillEffects.addHandHarmonyBuff(lease);
        }
    }
}
