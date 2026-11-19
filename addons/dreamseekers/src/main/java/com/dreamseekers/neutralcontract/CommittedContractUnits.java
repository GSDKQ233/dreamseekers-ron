// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.neutralcontract;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.summon.BoundedSummons;
import com.dreamseekers.unit.BasaltSnailUnit;
import com.dreamseekers.unit.BeetleUnit;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** Read-only proof of the original paid W conversion. Economic-free is not temporary life.
 * Does not mint a ledger entry, rewrite provenance, change an owner or remove a free marker.
 */
public final class CommittedContractUnits {
    private CommittedContractUnits() { }
    public static boolean isCommittedPermanent(LivingEntity body,String owner) {
        if(body==null||owner==null||owner.isBlank()||!(body.level() instanceof ServerLevel level)
                ||!level.getServer().isSameThread()||!(body instanceof Unit unit)||!owner.equals(unit.getOwnerName())
                ||!body.isAlive()||body.isRemoved()||!body.isAddedToWorld()||body.isSpectator()
                ||level.getEntity(body.getId())!=body||level.getEntity(body.getUUID())!=body
                ||!level.hasChunkAt(body.blockPosition())||UnitServerEvents.getAllUnits().stream().filter(e->e==body).count()!=1
                ||NeutralContracts.pending(body)||BoundedSummons.isTemporary(body)||!BoundedSummons.canAct(body)
                ||RtsKillValueClaims.isClaimed(body))return false;
        boolean originalKind=body.getClass()==BeetleUnit.class&&body.getType()==DreamseekersMod.BEETLE.get()
                ||body.getClass()==BasaltSnailUnit.class&&body.getType()==DreamseekersMod.BASALT_SNAIL.get();
        if(!originalKind||!body.getPersistentData().contains(RtsKillValueClaims.FREE_ORIGIN_KEY,Tag.TAG_COMPOUND)
                ||!body.getPersistentData().contains(NeutralContracts.ORIGIN,Tag.TAG_COMPOUND))return false;
        CompoundTag free=body.getPersistentData().getCompound(RtsKillValueClaims.FREE_ORIGIN_KEY);
        if(!free.contains("schema",Tag.TAG_INT)||free.getInt("schema")!=1||!free.contains("origin",Tag.TAG_STRING)
                ||!RtsKillValueClaims.FreeOrigin.NEUTRAL_CONTRACT.name().equals(free.getString("origin")))return false;
        CompoundTag provenance=body.getPersistentData().getCompound(NeutralContracts.ORIGIN);
        if(!provenance.contains("schema",Tag.TAG_INT)||provenance.getInt("schema")!=1
                ||!provenance.hasUUID("origin")||!provenance.hasUUID("account")
                ||!provenance.contains("payer",Tag.TAG_STRING)||!owner.equals(provenance.getString("payer")))return false;
        var account=ExtendedResourcesSync.account(owner);
        if(account==null||!account.extendedStateSupported()||!account.addonDataSupported()
                ||!account.accountEpoch.equals(provenance.getUUID("account")))return false;
        ContractLedger ledger=ContractLedger.get(level.getServer());
        ContractLedger.Entry entry=ledger.entry(body.getUUID());
        return ledger.supported()&&entry!=null&&entry.body().equals(body.getUUID())&&entry.owner().equals(owner)
                &&entry.account().equals(account.accountEpoch)&&entry.origin().equals(provenance.getUUID("origin"))
                &&ledger.consumed(entry.origin());
    }
}
