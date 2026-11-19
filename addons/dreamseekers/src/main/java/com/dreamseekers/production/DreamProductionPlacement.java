// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.production;

import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import java.util.*;

/** Addon-only first-rally handoff; never retries arbitrary client entity IDs or wall-clock tasks. */
public class DreamProductionPlacement extends ProductionPlacement {
    public static final int RALLY_PUBLICATION_TIMEOUT_TICKS=40;
    private final IdentityHashMap<com.solegendary.reignofnether.building.production.ActiveProduction,DreamUnitReceipts.Binding> unitReceipts=new IdentityHashMap<>();
    private com.solegendary.reignofnether.building.production.ActiveProduction tickingUnit;
    private boolean completingUnit,committedUnit;
    private boolean deferredDestroy,productionDetached;
    // Runtime-only generation: opaque metadata must not erase an observed owner transition.
    private long unitBindingRevision;
    public long unitBindingRevision(){return unitBindingRevision;}
    protected final void advanceUnitBindingRevision(){
        if(unitBindingRevision==Long.MAX_VALUE)unitBindingRevision=Long.MIN_VALUE;
        else if(unitBindingRevision>=0)unitBindingRevision++;
    }
    boolean quarantinesUnitBinding(DreamUnitReceipts.Binding binding){return false;}
    private RemovalReason deferredRemoval;
    protected boolean onProductionServerThread() {
        return getLevel() instanceof ServerLevel serverLevel&&serverLevel.getServer().isSameThread();
    }
    protected final boolean productionCallbackActive(){return tickingUnit!=null||completingUnit;}
    public boolean productionAvailable() {
        return !productionDetached&&!deferredDestroy&&onProductionServerThread()&&isBuilt&&!isDestroyedServerside&&!shouldBeDestroyed()
                &&BuildingServerEvents.getBuildings().contains(this)&&!ownerName.isBlank();
    }
    public UUID unitPlacementIdentity() {
        return com.solegendary.reignofnether.research.economy.ResearchPlacementIdentity.serverIdentity(this);
    }
    public long unitOwnerRevision(){return getCaptureRevision();}
    public Set<UUID> attachedUnitReceipts() {
        Set<UUID> result=new HashSet<>();
        for(var entry:unitReceipts.entrySet())if(!entry.getKey().completed&&productionQueue.contains(entry.getKey()))result.add(entry.getValue().receipt());
        return result;
    }
    private void retireUnitReceipts(){unitReceipts.keySet().removeIf(active->active.completed||!productionQueue.contains(active));}
    private boolean reconcileUnitReceipts() {
        retireUnitReceipts();boolean changed=false;
        for(var active:List.copyOf(productionQueue)) {
            var binding=unitReceipts.get(active);
            if(binding!=null&&!DreamUnitReceipts.valid(this,active,binding)
                    &&!DreamUnitReceipts.retainedWhileOpaque(this,active,binding)) {
                detachUnitAndRefund(active);changed=true;
            }
        }
        if(changed)BuildingProductionClientboundPacket.clearQueue(ownerName,originPos);
        return changed;
    }
    private boolean detachUnitAndRefund(com.solegendary.reignofnether.building.production.ActiveProduction active) {
        if(!productionQueue.remove(active))return false;
        var binding=unitReceipts.remove(active);
        if(binding!=null&&!active.completed)DreamUnitReceipts.refund(getLevel().getServer(),binding);
        // An overflow/temporarily unavailable payer retains its original durable ledger claim.
        return true;
    }
    private boolean validUnitHead(com.solegendary.reignofnether.building.production.ActiveProduction active) {
        return productionAvailable()&&!productionQueue.isEmpty()&&productionQueue.get(0)==active&&!active.completed
                &&DreamUnitReceipts.valid(this,active,unitReceipts.get(active));
    }
    public boolean authorizedCompletion(com.solegendary.reignofnether.building.production.ProductionItem recipe) {
        return completingUnit&&tickingUnit!=null&&tickingUnit.item==recipe&&validUnitHead(tickingUnit);
    }
    public boolean authorizedScore(com.solegendary.reignofnether.building.production.ProductionItem recipe) {
        return completingUnit&&committedUnit&&tickingUnit!=null&&tickingUnit.item==recipe;
    }
    public boolean tickProduction(com.solegendary.reignofnether.building.production.ActiveProduction active,java.util.function.BooleanSupplier baseTick) {
        if(getLevel().isClientSide())return baseTick.getAsBoolean();
        if(tickingUnit!=null||!validUnitHead(active))return active.pauseBecause("dreamseekers.error.unavailable_producer");
        tickingUnit=active;
        try{return baseTick.getAsBoolean()&&active.completed;}finally{tickingUnit=null;}
    }
    void reportCompletionPause(String reason) {
        if (completingUnit && tickingUnit != null) tickingUnit.pauseBecause(reason);
    }

    @Override public boolean startProductionItem(com.solegendary.reignofnether.building.production.ProductionItem item,float ticksLeft) {
        if(getLevel().isClientSide() || !(item instanceof DreamUnitProduction recipe))return super.startProductionItem(item,ticksLeft);
        return startPaidUnit(recipe) == null; // Bots and internal callers have no requesting player.
    }

    @Override public boolean startRequestedProduction(com.solegendary.reignofnether.building.production.ProductionItem item,
            java.util.function.Consumer<String> reject) {
        if(getLevel().isClientSide() || !(item instanceof DreamUnitProduction recipe))return super.startRequestedProduction(item,reject);
        String reason = startPaidUnit(recipe);
        if (reason != null) reject.accept(reason);
        return reason == null;
    }

    /** Exactly one decision and one debit attempt. No standing recipient or retry is recorded. */
    private String startPaidUnit(DreamUnitProduction item) {
        if(!onProductionServerThread()||tickingUnit!=null||completingUnit)return "dreamseekers.error.unavailable_producer";
        reconcileUnitReceipts();
        if(!DreamUnitReceipts.supported(item)
                ||!(getBuilding() instanceof com.solegendary.reignofnether.building.production.ProductionBuilding host)
                ||!host.productions.get().contains(item))return "dreamseekers.error.wrong_producer";
        String error = item.getProduceErrorMsg(this);
        if(error != null)return error;
        if(com.solegendary.reignofnether.resources.ExtendedResourcesSync.account(ownerName)==null)return "research.economy.invalid_account";
        if(!item.isBelowMaxPopulation(this))return "server.resources.reignofnether.max_pop";
        if(!item.canAffordPopulation(this))return "server.resources.reignofnether.not_enough_pop";
        boolean allow=switch(item.dupeRule) {
            case ALLOW -> true;
            case DISALLOW -> !item.itemIsBeingProduced(false,ownerName);
            case DISALLOW_FOR_BUILDING -> !item.itemIsBeingProducedAt(false,this);
        };
        if(!allow)return "dreamseekers.production.already_queued";
        var active=new com.solegendary.reignofnether.building.production.ActiveProduction(item,false,ownerName) {
            @Override public void complete(ProductionPlacement placement) {
                if(placement!=DreamProductionPlacement.this||tickingUnit!=this||!validUnitHead(this)||completingUnit)return;
                completingUnit=true;
                try {
                    Entity spawned=((DreamUnitProduction)this.item).spawnForPaidCompletion(DreamProductionPlacement.this);
                    if(spawned==null)return;
                    if(!DreamUnitReceipts.consume(DreamProductionPlacement.this,this,unitReceipts.get(this))) {
                        rollbackUncommittedSpawn(spawned);pauseBecause("dreamseekers.production.settlement_pending");return;
                    }
                    completed=true;committedUnit=true;item.recordScore(placement);
                } finally {committedUnit=false;completingUnit=false;}
            }
        };
        var payment=DreamUnitReceipts.reserve(this,item);
        if(payment.refusal()!=null)return payment.refusal();
        var binding=payment.binding();
        active.ticksLeft=binding.work();productionQueue.add(active);unitReceipts.put(active,binding);
        DreamUnitReceipts.sync(getLevel().getServer(),ownerName);return null;
    }
    @Override public boolean cancelProductionItem(com.solegendary.reignofnether.building.production.ProductionItem item,boolean front) {
        if(getLevel().isClientSide())return super.cancelProductionItem(item,front);
        if(!onProductionServerThread()||tickingUnit!=null||completingUnit)return false;
        var observed = front && !productionQueue.isEmpty() ? productionQueue.get(0) : null;
        if (front && (observed == null || observed.item != item)) return false;
        boolean settled=reconcileUnitReceipts();
        // Reconciliation can retire the observed job. Never reinterpret this request
        // against its successor, even if both jobs have the same recipe.
        if (front && (productionQueue.isEmpty() || productionQueue.get(0) != observed)) return false;
        if(productionQueue.isEmpty())return settled;
        if(front && observed.item instanceof DreamUnitProduction)
            return detachUnitAndRefund(observed);
        if(!front&&item instanceof DreamUnitProduction) {
            for(var active:List.copyOf(productionQueue)) {
                var binding=unitReceipts.get(active);
                if(active.item==item&&binding!=null&&active.ticksLeft>=binding.work())return detachUnitAndRefund(active);
            }
            return false;
        }
        return super.cancelProductionItem(item,front);
    }
    @Override public com.solegendary.reignofnether.resources.Resources getPendingProductionRefund(String payer) {
        // All permitted Dream units use ledger projection; receipt-backed research already does likewise.
        return new com.solegendary.reignofnether.resources.Resources(payer,0,0,0,0);
    }

    private record PendingRally(Entity entity, UUID uuid, String owner, long commandRevision,
                                long expiresAt, List<BlockPos> points, LivingEntity target, UUID targetUuid,
                                boolean attackMove, ResourceName resource) { }
    private final List<PendingRally> pendingRallies=new ArrayList<>();

    public DreamProductionPlacement(Building building, Level level, BlockPos pos, Rotation rotation,
                                    String owner, ArrayList<BuildingBlock> blocks, boolean capitol) {
        super(building,level,pos,rotation,owner,blocks,capitol);
    }
    private static RtsUnitState state(Unit unit) {
        if(unit instanceof DreamRtsUnit combat)return combat.rts();
        if(unit instanceof DreamNoncombatUnit support)return support.rts();
        return null;
    }
    public void rollbackUncommittedSpawn(Entity entity) {
        pendingRallies.removeIf(pending->pending.entity()==entity);
        if(entity instanceof LivingEntity living)UnitServerEvents.rollbackUncommittedProductionSpawn(living);
        else if(!entity.isRemoved())entity.discard();
    }
    @Override protected void setDelayedRally(Unit unit) {
        if(!(getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread())return;
        RtsUnitState state=state(unit);
        // This placement's whitelist contains only addon units. Fail closed if that ever changes.
        if(state==null||unit.getOwnerName().isBlank()||!ownerName.equals(unit.getOwnerName()))return;
        Entity entity=(Entity)unit;LivingEntity target=getRallyPointEntity();
        List<BlockPos> points=rallyPoints.isEmpty()?List.of(getDefaultOutdoorSpawnPoint()):List.copyOf(rallyPoints);
        pendingRallies.add(new PendingRally(entity,entity.getUUID(),unit.getOwnerName(),state.commandRevision,
                level.getGameTime()+RALLY_PUBLICATION_TIMEOUT_TICKS,points,target,target==null?null:target.getUUID(),
                attackRally,rallyResourceName));
    }
    @Override public void tick(Level tickLevel) {
        if(tickingUnit!=null||completingUnit)return;
        if(tickLevel==getLevel()&&onProductionServerThread())reconcileUnitReceipts();
        if(tickLevel==getLevel()&&tickLevel instanceof ServerLevel level&&level.getServer().isSameThread()) {
            Iterator<PendingRally> iterator=pendingRallies.iterator();
            while(iterator.hasNext()) {
                PendingRally pending=iterator.next();Entity entity=pending.entity();Unit unit=(Unit)entity;
                RtsUnitState state=state(unit);
                if(!isBuilt||isDestroyedServerside||shouldBeDestroyed()||!BuildingServerEvents.getBuildings().contains(this)
                        ||!ownerName.equals(pending.owner())||!unit.getOwnerName().equals(pending.owner())
                        ||entity.level()!=level||!entity.isAlive()||entity.isRemoved()||!entity.getUUID().equals(pending.uuid())
                        ||state==null||state.commandRevision!=pending.commandRevision()||level.getGameTime()>pending.expiresAt()) {
                    iterator.remove();continue;
                }
                if(level.getEntity(pending.uuid())!=entity||level.getEntity(entity.getId())!=entity)continue;
                // An already-enqueued immediate command has priority even before its reset hook runs.
                // Wait for real authorization/dispatch: an invalid foreign action must not cancel this rally.
                if(UnitServerEvents.hasPendingImmediateAction(entity.getId()))continue;
                iterator.remove(); // One-shot before dispatch, which advances the unit's command revision.
                LivingEntity target=pending.target();
                if(target!=null) {
                    if(!target.isAlive()||target.isRemoved()||target.level()!=level||!target.getUUID().equals(pending.targetUuid())
                            ||level.getEntity(pending.targetUuid())!=target)continue;
                    boolean hostile=ResourceSources.isHuntableAnimal(target)||(target instanceof Unit other
                            &&!AlliancesServerEvents.isAlliedOrOwned(other.getOwnerName(),pending.owner()));
                    new UnitActionItem(pending.owner(),hostile?UnitAction.ATTACK:UnitAction.FOLLOW,target.getId(),
                            new int[]{entity.getId()},BlockPos.ZERO,BlockPos.ZERO).action(level);
                } else if(!pending.points().isEmpty()) {
                    UnitAction action=pending.attackMove()?UnitAction.ATTACK_MOVE:UnitAction.MOVE;
                    new UnitActionItem(pending.owner(),action,-1,new int[]{entity.getId()},pending.points().get(0),BlockPos.ZERO).action(level);
                    // Queue remaining legs once: there are no wall-clock callbacks to reinsert them.
                    // After handoff these legs obey ordinary core queue/owner semantics, not a separate epoch.
                    for(int i=1;i<pending.points().size();i++)UnitServerEvents.addActionItem(pending.owner(),action,-1,
                            new int[]{entity.getId()},pending.points().get(i),BlockPos.ZERO,true);
                    if(!pending.attackMove()&&pending.resource()!=ResourceName.NONE&&unit instanceof WorkerUnit worker
                            &&worker.getGatherResourceGoal()!=null)worker.getGatherResourceGoal().setTargetResourceName(pending.resource());
                }
            }
        }
        super.tick(tickLevel);
        if(tickLevel==getLevel()&&onProductionServerThread()) {
            retireUnitReceipts();
            if(deferredRemoval!=null){var reason=deferredRemoval;deferredRemoval=null;onRemoved(reason);}
            else if(deferredDestroy){deferredDestroy=false;destroy((ServerLevel)getLevel());}
        }
    }
    @Override public void destroy(ServerLevel level) {
        if(!onProductionServerThread()||level!=getLevel())return;
        if(productionCallbackActive()){deferredDestroy=true;return;}
        // Drain exact unit claims before native destroy's generic while-loop.
        for(var active:List.copyOf(productionQueue))if(active.item instanceof DreamUnitProduction)detachUnitAndRefund(active);
        super.destroy(level);
    }
    @Override public void onRemoved(RemovalReason reason) {
        productionDetached=true;
        if(productionCallbackActive()){deferredRemoval=reason;return;}
        pendingRallies.clear();productionQueue.clear();unitReceipts.clear();tickingUnit=null;completingUnit=false;committedUnit=false;super.onRemoved(reason);}
}
