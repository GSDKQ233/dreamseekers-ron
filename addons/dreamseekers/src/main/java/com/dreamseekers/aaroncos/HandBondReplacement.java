// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.aaroncos;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.production.DreamUnitProduction;
import com.dreamseekers.unit.DreamRtsUnit;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayerScoresEnum;
import com.solegendary.reignofnether.resources.kill.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** A full-price new body, not a free resurrection. Stage -> revalidate -> atomic debit -> publish. */
@Mod.EventBusSubscriber(modid="dreamseekers")
public final class HandBondReplacement {
    public static final String PENDING="dreamseekers:pending_hand_replacement";
    private static final Set<Entity> STAGING=Collections.newSetFromMap(new IdentityHashMap<>());
    private HandBondReplacement(){}
    public static boolean pending(Entity e){return e!=null&&(STAGING.contains(e)||e.getPersistentData().contains(PENDING));}
    private static ProductionPlacement host(ServerLevel level,String owner,DreamUnitProduction recipe){
        return BuildingServerEvents.getBuildings().stream().filter(b->b instanceof ProductionPlacement&&b.getLevel()==level&&b.ownerName.equals(owner)
            &&b.isBuilt&&!b.isDestroyedServerside&&!b.shouldBeDestroyed()&&b.getHealth()>0&&level.hasChunksAt(b.minCorner,b.maxCorner))
            .map(b->(ProductionPlacement)b).filter(b->recipe.getProduceErrorMsg(b)==null).findFirst().orElse(null);
    }
    private static boolean safe(ServerLevel level,LivingEntity replacement){
        var box=replacement.getBoundingBox();
        return level.hasChunksAt(net.minecraft.core.BlockPos.containing(box.minX,box.minY,box.minZ),net.minecraft.core.BlockPos.containing(box.maxX,box.maxY,box.maxZ))
            &&box.minY>=level.getMinBuildHeight()&&box.maxY<=level.getMaxBuildHeight()&&level.getWorldBorder().isWithinBounds(box)
            &&!level.containsAnyLiquid(box)&&!level.getBlockCollisions(replacement,box).iterator().hasNext()
            &&level.getEntities(replacement,box,e->e instanceof LivingEntity&&e.isAlive()&&!e.isRemoved()).isEmpty();
    }
    private static int cap(String owner){return Math.min(UnitServerEvents.maxPopulation,BuildingServerEvents.getTotalPopulationSupply(owner));}
    private static boolean funds(Resources account,ResourceCost quote,Map<net.minecraft.resources.ResourceLocation,Integer> extras){
        return account!=null&&account.extendedStateSupported()&&account.food>=quote.food&&account.wood>=quote.wood&&account.ore>=quote.ore&&account.emerald>=quote.emerald
            &&extras.entrySet().stream().allMatch(e->account.extra.getOrDefault(e.getKey(),0)>=e.getValue());
    }
    static void attempt(ServerLevel level,HandBondData data,HandBondData.Bond bond,LivingEntity survivor){
        if(HandBondService.stopping(level)||data.retired.size()>=HandBondData.MAX_RETIRED||level.getGameTime()%20!=0||!HandBondService.body(survivor)||data.forEntity(survivor.getUUID())!=bond||bond.missing==0)return;
        var recipe=bond.missing==1?DreamseekersMod.AARONCOS_LEFT_HAND_PRODUCTION:DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION;
        var production=host(level,bond.owner,recipe);if(!(production instanceof com.dreamseekers.production.DreamProductionPlacement producer))return;
        final var placement=producer.unitPlacementIdentity();final long capture=producer.getCaptureRevision(),ownerRevisionHost=producer.unitOwnerRevision(),binding=producer.unitBindingRevision();
        if(placement==null||capture<0||ownerRevisionHost<0||binding<0)return;
        var account=ExtendedResourcesSync.account(bond.owner);var quote=recipe.getCost(false,bond.owner);var extras=recipe.extraCost(false,bond.owner);
        if(account==null||!bond.accountEpoch.equals(account.accountEpoch)||!funds(account,quote,extras)
            ||UnitServerEvents.getCurrentPopulation(bond.owner)+quote.population>cap(bond.owner))return;
        LivingEntity replacement=bond.missing==1?DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(level):DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(level);
        if(!(replacement instanceof DreamRtsUnit unit))return;
        ((Mob)replacement).setNoAi(true);replacement.getPersistentData().putBoolean(PENDING,true);unit.setOwnerName(bond.owner);
        // The fresh paid hand inherits neither orders, inventory, scenario death commands, nor prior economic claims.
        boolean found=false;
        for(int[] offset:new int[][]{{6,0},{-6,0},{0,6},{0,-6},{6,6},{-6,-6},{6,-6},{-6,6}}){
            replacement.moveTo(survivor.getX()+offset[0],survivor.getY(),survivor.getZ()+offset[1],survivor.getYRot(),0);
            if(safe(level,replacement)){found=true;break;}
        }
        if(!found)return;
        final var position=replacement.position();final int missing=bond.missing;final UUID old=bond.missing==1?bond.left:bond.right;
        final var survivorPosition=survivor.position();final float hp=survivor.getHealth();
        final long ownerRevision=unit.rts().ownershipRevision,commandRevision=unit.rts().commandRevision;
        boolean committed=false;STAGING.add(replacement);
        try{
            if(!level.addFreshEntity(replacement)||!replacement.isAddedToWorld()||replacement.isRemoved()||!replacement.isAlive())return;
            if(HandBondService.stopping(level)||data.forEntity(survivor.getUUID())!=bond||bond.missing!=missing||level.getEntity(old)!=null||!HandBondService.body(survivor)
                ||!((ControlledAaroncosHand)survivor).getOwnerName().equals(bond.owner)||!survivor.position().equals(survivorPosition)||survivor.getHealth()!=hp
                ||level.getGameTime()<bond.readyAt||level.getGameTime()-bond.lastCombat<HandBondService.QUIET_TICKS
                ||!HandBondService.researched(bond.owner)||host(level,bond.owner,recipe)!=production
                ||!placement.equals(producer.unitPlacementIdentity())||capture!=producer.getCaptureRevision()||ownerRevisionHost!=producer.unitOwnerRevision()||binding!=producer.unitBindingRevision()
                ||recipe.getProduceErrorMsg(production)!=null||ExtendedResourcesSync.account(bond.owner)!=account||!account.accountEpoch.equals(bond.accountEpoch)
                ||!funds(account,quote,extras)||UnitServerEvents.getCurrentPopulation(bond.owner)>cap(bond.owner)
                ||level.getEntity(replacement.getId())!=replacement||level.getEntity(replacement.getUUID())!=replacement
                ||UnitServerEvents.getAllUnits().stream().filter(e->e==replacement).count()!=1
                ||!unit.getOwnerName().equals(bond.owner)||unit.rts().ownershipRevision!=ownerRevision||unit.rts().commandRevision!=commandRevision
                ||!replacement.getPersistentData().contains(PENDING)||!((Mob)replacement).isNoAi()||!replacement.position().equals(position)||!safe(level,replacement)
                ||replacement.getHealth()!=replacement.getMaxHealth()||RtsKillValueClaims.isFree(replacement)||RtsKillValueClaims.isClaimed(replacement))return;
            var current=recipe.getCost(false,bond.owner);
            if(current.food!=quote.food||current.wood!=quote.wood||current.ore!=quote.ore||current.emerald!=quote.emerald||current.population!=quote.population)return;
            var owner=PlayerServerEvents.getRTSPlayer(bond.owner);if(owner==null)return;
            if(!ResourceTransaction.exchange(level.getServer(),account,account.accountEpoch,account.revision,
                new ResourceTransaction.Cost(quote.food,quote.wood,quote.ore,quote.emerald,extras),ResourceTransaction.Cost.ZERO).applied())return;
            // No event/callback between the debit and ledger commit. Full price gives a fresh lineage.
            committed=true;data.retired.add(old);if(missing==1)bond.left=replacement.getUUID();else bond.right=replacement.getUUID();
            bond.missing=0;bond.readyAt=0;bond.lastCombat=level.getGameTime();data.setDirty();
            // This is a full-price new military unit, not a revive. Record only after the
            // one-way ledger commit; waiting, rejected births and later ticks cannot score.
            // Queue-only recipe.recordScore is deliberately not bypassed or impersonated.
            owner.scores.addToScore(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED);
            owner.scores.addToScore(RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED);
            replacement.getPersistentData().remove(PENDING);((Mob)replacement).setNoAi(false);unit.rts().hold=true;
            var player=level.getServer().getPlayerList().getPlayerByName(bond.owner);if(player!=null)ExtendedResourcesSync.send(player);
        }finally{STAGING.remove(replacement);if(!committed)UnitServerEvents.rollbackUncommittedProductionSpawn(replacement);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(!event.getLevel().isClientSide()&&pending(event.getEntity())&&!STAGING.contains(event.getEntity()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(net.minecraftforge.event.entity.living.LivingAttackEvent event){
        if(pending(event.getEntity())||pending(event.getSource().getEntity()))event.setCanceled(true);
    }
    @SubscribeEvent public static void economic(RtsKillValueEligibilityEvent event){if(pending(event.victim())||pending(event.killer()))event.setCanceled(true);}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){STAGING.clear();}
}
