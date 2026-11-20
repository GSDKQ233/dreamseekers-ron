package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.GraveyardPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.List;

/** One native queue completion attempt. A vetoed publication is not a paid unit or a score.
 * Receipt-backed addon queues retain their independent transaction protocol. */
public final class NativeProductionCompletion {
    private static final ThreadLocal<Attempt> CURRENT = new ThreadLocal<>();
    private NativeProductionCompletion() {}
    private static final class Attempt {
        final ProductionPlacement placement;
        final ActiveProduction active;
        final String owner;
        final long capture;
        final int heroLimit, populationLimit, populationSupply, heroReservations;
        final java.util.Set<net.minecraft.resources.ResourceLocation> research;
        final com.solegendary.reignofnether.resources.Resources account;
        final java.util.UUID accountEpoch;
        final List<Entity> births = new ArrayList<>();
        final List<Runnable> commitActions = new ArrayList<>();
        boolean failed;
        Attempt(ProductionPlacement p, ActiveProduction a) {
            placement=p; active=a; owner=p.ownerName; capture=p.getCaptureRevision();
            heroLimit=p.getLevel().getGameRules().getInt(com.solegendary.reignofnether.registrars.GameRuleRegistrar.ALLOWED_HEROES);
            populationLimit=UnitServerEvents.maxPopulation;
            populationSupply=BuildingServerEvents.getTotalPopulationSupply(owner);
            heroReservations=com.solegendary.reignofnether.unit.interfaces.HeroUnit.getNumHeroesOwnedOrInTraining(false,owner);
            research=com.solegendary.reignofnether.research.ResearchServerEvents.getResearchSnapshot(owner);
            account=com.solegendary.reignofnether.resources.ExtendedResourcesSync.account(owner);
            accountEpoch=account==null?null:account.accountEpoch;
        }
        boolean valid() {
            return !failed && !active.completed && !placement.productionQueue.isEmpty()
                && placement.productionQueue.get(0)==active && owner.equals(placement.ownerName)
                && capture==placement.getCaptureRevision() && capture>=0
                && (placement.isBuilt || placement.allowProdWhileBuilding) && !placement.isDestroyedServerside && !placement.shouldBeDestroyed()
                && BuildingServerEvents.getBuildings().contains(placement);
        }
    }
    public static boolean handles(ProductionPlacement p, ProductionItem item) {
        ProductionItem original=item.nativeCompletionItem();
        if (!(original instanceof IUnitProductionItem || original instanceof ReviveHeroProductionItem)) return false;
        // An upgraded graveyard intentionally produces a stored skull, not a live entity.
        return !(original instanceof GraveyardUnitProductionItem && p instanceof GraveyardPlacement && p.getUpgradeLevel()>0);
    }
    /** Only the exact head currently completing may exclude its own hero reservation. */
    public static boolean isCurrentReservation(ProductionPlacement p, ActiveProduction active, ProductionItem recipe) {
        Attempt a=CURRENT.get();
        return a!=null && a.placement==p && a.active==active && a.valid()
                && active.item.nativeCompletionItem()==recipe;
    }
    /** No recipe can borrow another queued recipe's template, even if both spawn the same entity type. */
    public static net.minecraft.nbt.CompoundTag copyCustomSpawnTemplate(ProductionPlacement p, net.minecraft.world.entity.EntityType<?> type) {
        Attempt a = CURRENT.get();
        return a != null && a.placement == p && a.valid() && a.active.item instanceof CustomProductionItem custom
                ? custom.copySpawnTemplate(type) : null;
    }

    private static final String NO_INDOOR_SPAWN = "building.reignofnether.indoor_spawn_blocked";
    static final int INDOOR_RETRY_TICKS = 20;

    public static void holdForIndoorSpawn(ProductionPlacement p) {
        Attempt a = CURRENT.get();
        if (a == null || a.placement != p) return;
        a.failed = true;
        a.active.nextIndoorSpawnAttempt = p.getLevel().getGameTime() + INDOOR_RETRY_TICKS;
        a.active.pauseBecause(NO_INDOOR_SPAWN);
    }

    public static boolean complete(ProductionPlacement p, ActiveProduction active) {
        if (!(p.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread() || CURRENT.get()!=null) return false;
        if (level.getGameTime() < active.nextIndoorSpawnAttempt) return active.pauseBecause(NO_INDOOR_SPAWN);
        Attempt attempt=new Attempt(p,active);
        if (!attempt.valid()) return false;
        CURRENT.set(attempt);
        boolean committed=false;
        try {
            if (!active.item.canProduce(p)) return false;
            active.item.onComplete.accept(level,p);
            if (!attempt.valid() || attempt.births.size()!=1) return false;
            Entity birth=attempt.births.get(0);
            if (!published(p,birth) || !(birth instanceof Unit unit) || !attempt.owner.equals(unit.getOwnerName())) return false;
            if (attempt.heroLimit!=level.getGameRules().getInt(com.solegendary.reignofnether.registrars.GameRuleRegistrar.ALLOWED_HEROES)
                    || attempt.populationLimit!=UnitServerEvents.maxPopulation
                    || attempt.populationSupply!=BuildingServerEvents.getTotalPopulationSupply(attempt.owner)
                    || !attempt.research.equals(com.solegendary.reignofnether.research.ResearchServerEvents.getResearchSnapshot(attempt.owner))
                    || attempt.account!=com.solegendary.reignofnether.resources.ExtendedResourcesSync.account(attempt.owner)
                    || attempt.account!=null && !attempt.accountEpoch.equals(attempt.account.accountEpoch)) return false;
            int finalPopulation=UnitServerEvents.getCurrentPopulation(attempt.owner)-active.item.getCost(false,attempt.owner).population;
            if (finalPopulation>Math.min(attempt.populationLimit,attempt.populationSupply)) return false;
            if (birth instanceof com.solegendary.reignofnether.unit.interfaces.HeroUnit
                    && !(active.item instanceof CustomProductionItem)
                    && com.solegendary.reignofnether.unit.interfaces.HeroUnit.getNumHeroesOwnedOrInTraining(false,attempt.owner)>attempt.heroReservations+1) return false;
            active.completed=true;
            committed=true;
            active.item.recordScore(p);
            for (Runnable action:attempt.commitActions) action.run();
            return true;
        } finally {
            CURRENT.remove();
            if (!committed) for (Entity birth:attempt.births) rollback(birth);
        }
    }
    private static boolean published(ProductionPlacement p, Entity entity) {
        return entity!=null && entity.level()==p.getLevel() && entity.isAddedToWorld()
                && entity.isAlive() && !entity.isRemoved();
    }
    /** Called immediately after native EntityType.spawn, before owner/hero state is transferred. */
    public static Entity acceptSpawn(ProductionPlacement p, Entity entity) {
        Attempt a=CURRENT.get();
        if (!published(p,entity) || a!=null && (a.placement!=p || !a.valid())) {
            if (a!=null && a.placement==p) a.failed=true;
            rollback(entity); return null;
        }
        if (a!=null && a.placement==p) a.births.add(entity);
        return entity;
    }
    public static void afterCommit(ProductionPlacement p, Runnable action) {
        Attempt a=CURRENT.get();
        if (a!=null && a.placement==p) a.commitActions.add(action);
        else action.run();
    }
    public static void invalidate(ProductionPlacement p) {
        Attempt a=CURRENT.get(); if(a!=null && a.placement==p)a.failed=true;
    }
    private static void rollback(Entity entity) {
        if(entity instanceof LivingEntity living)UnitServerEvents.rollbackUncommittedProductionSpawn(living);
        else if(entity!=null && !entity.isRemoved())entity.discard();
    }
}
