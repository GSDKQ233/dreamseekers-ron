package com.dreamseekers.production;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayerScoresEnum;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Uses RoN's actual paid, cancellable production queue and rally-point spawning. */
public final class DreamUnitProduction extends ProductionItem implements IUnitProductionItem {
    private final Supplier<? extends EntityType<? extends Unit>> entityType;
    private final ResourceLocation icon;
    private final Supplier<? extends Building> requiredProducer;
    private final Supplier<? extends Building> prerequisite;
    private final String prerequisiteError;
    private final java.util.Map<ResourceLocation,Integer> baseExtraCost;
    private final java.util.function.Function<ProductionPlacement,String> missingAdditionalRequirement;

    public DreamUnitProduction(Supplier<? extends EntityType<? extends Unit>> entityType, ResourceCost cost, ResourceLocation icon) {
        this(entityType, cost, icon, null, null, null);
    }

    /** Narrow, explicit unlock for this production; never enables a scaffold's general role hooks. */
    public DreamUnitProduction(Supplier<? extends EntityType<? extends Unit>> entityType, ResourceCost cost, ResourceLocation icon,
                               Supplier<? extends Building> requiredProducer, Supplier<? extends Building> prerequisite,
                               String prerequisiteError) {
        this(entityType,cost,icon,requiredProducer,prerequisite,prerequisiteError,java.util.Map.of(),p -> true);
    }

    /** Immutable extra currencies and narrow recipe-only prerequisite; legacy recipes remain unchanged. */
    public DreamUnitProduction(Supplier<? extends EntityType<? extends Unit>> entityType, ResourceCost cost, ResourceLocation icon,
            Supplier<? extends Building> requiredProducer, Supplier<? extends Building> prerequisite, String prerequisiteError,
            java.util.Map<ResourceLocation,Integer> extraCost, java.util.function.Predicate<ProductionPlacement> additionalPrerequisite) {
        this(entityType,cost,icon,requiredProducer,prerequisite,prerequisiteError,
                describe(java.util.Objects.requireNonNull(additionalPrerequisite),prerequisiteError),extraCost);
    }

    private static java.util.function.Function<ProductionPlacement,String> describe(
            java.util.function.Predicate<ProductionPlacement> test, String reason) {
        return p -> test.test(p) ? null : (reason == null ? "dreamseekers.error.unavailable_producer" : reason);
    }

    /** Complex recipes expose the actual missing condition, separately from the always-visible recipe hint. */
    public DreamUnitProduction(Supplier<? extends EntityType<? extends Unit>> entityType, ResourceCost cost, ResourceLocation icon,
            Supplier<? extends Building> requiredProducer, Supplier<? extends Building> prerequisite, String prerequisiteError,
            java.util.function.Function<ProductionPlacement,String> missingAdditionalRequirement, java.util.Map<ResourceLocation,Integer> extraCost) {
        super(cost);
        if(extraCost==null||missingAdditionalRequirement==null||extraCost.entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null||e.getValue()<0))
            throw new IllegalArgumentException("Invalid extra training cost");
        this.baseExtraCost=java.util.Map.copyOf(extraCost);
        this.missingAdditionalRequirement=missingAdditionalRequirement;
        this.requiredProducer = requiredProducer;
        this.prerequisite = prerequisite;
        this.prerequisiteError = prerequisiteError;
        this.entityType = entityType;
        this.icon = icon;
        this.onComplete = (level, placement) -> {
            if (!(placement instanceof DreamHallPlacement) && level instanceof ServerLevel serverLevel && getProduceErrorMsg(placement) == null
                    && (requiredProducer == null || hasSafeSpawnExit(placement)))
                placement.produceUnit(serverLevel, entityType.get(), placement.ownerName, false);
        };
    }

    @Override public ResourceCost getCost(boolean client,String owner){
        return DreamUnitReceipts.supported(this)?com.dreamseekers.progression.DreamWindProgression.quote(defaultCost,client,owner):defaultCost;
    }
    /** Wind discounts only the ordinary currencies; extra costs remain explicit fixed per-recipe amounts. */
    public java.util.Map<ResourceLocation,Integer> extraCost(boolean client,String owner) { return baseExtraCost; }
    public boolean canAffordExtra(ProductionPlacement placement) {
        if(baseExtraCost.isEmpty())return true;
        if(placement.getLevel().isClientSide())return ClientExtra.affordable(placement.ownerName,baseExtraCost);
        var account=com.solegendary.reignofnether.resources.ExtendedResourcesSync.account(placement.ownerName);
        return account!=null&&account.extendedStateSupported()&&baseExtraCost.entrySet().stream()
                .allMatch(e->account.extra.getOrDefault(e.getKey(),0)>=e.getValue());
    }
    @Override public boolean canAfford(ProductionPlacement placement) {
        return super.canAfford(placement)&&canAffordExtra(placement);
    }
    private static final class ClientExtra {
        static boolean affordable(String owner,java.util.Map<ResourceLocation,Integer> cost) {
            var mc=net.minecraft.client.Minecraft.getInstance();
            return mc.player!=null&&mc.getConnection()!=null&&owner.equals(mc.player.getName().getString())
                &&com.solegendary.reignofnether.resources.ExtendedResourcesClient.ready()
                &&cost.entrySet().stream().allMatch(e->com.solegendary.reignofnether.resources.ExtendedResourcesClient.balance(e.getKey())>=e.getValue());
        }
    }
    @Override public String getItemName() { return EntityType.getKey(entityType.get()).toString(); }

    @Override public boolean reportsHeadPause() { return true; }

    @Override public String getProduceErrorMsg(ProductionPlacement placement) {
        if (!placement.isBuilt) return "dreamseekers.error.unfinished";
        if (placement instanceof DreamProductionPlacement producer && !placement.getLevel().isClientSide() && !producer.productionAvailable())
            return "dreamseekers.error.unavailable_producer";
        if (requiredProducer != null && requiredProducer.get() == DreamseekersMod.DREAM_HALL
                && !(placement instanceof DreamHallPlacement)) return "dreamseekers.error.wrong_producer";
        if (!placement.getLevel().isClientSide()) {
            var owner = PlayerServerEvents.getRTSPlayer(placement.ownerName);
            if (!com.dreamseekers.economy.DreamFactionAccess.allows(owner))
                return "dreamseekers.error.wrong_faction";
        }
        if (requiredProducer != null) {
            if (placement.getBuilding() != requiredProducer.get()) return "dreamseekers.error.wrong_producer";
            List<BuildingPlacement> live = placement.getLevel().isClientSide()
                    ? BuildingClientEvents.getBuildings() : BuildingServerEvents.getBuildings();
            if (!live.contains(placement) || placement.isDestroyedServerside || placement.shouldBeDestroyed())
                return "dreamseekers.error.unavailable_producer";
            if (prerequisite != null && (prerequisite.get() == null || live.stream().noneMatch(candidate ->
                    candidate.getBuilding() == prerequisite.get() && candidate.getLevel() == placement.getLevel()
                            && (candidate.getLevel().isClientSide() || candidate.getLevel().hasChunkAt(candidate.originPos))
                            && placement.ownerName.equals(candidate.ownerName) && candidate.isBuilt
                            && !candidate.isDestroyedServerside && !candidate.shouldBeDestroyed())))
                return prerequisiteError;
        }
        return missingAdditionalRequirement.apply(placement);
    }

    /** No entity is created to probe. Vanilla aligns by collision-dropping from this initial Y+1 box. */
    private boolean hasSafeSpawnExit(ProductionPlacement placement) {
        if (!(placement.getLevel() instanceof ServerLevel level)) return true;
        BlockPos point = placement.getUnitSpawnPoint(level, false, Vec3i.ZERO);
        AABB box = entityType.get().getDimensions().makeBoundingBox(point.getX() + .5, point.getY() + 1, point.getZ() + .5);
        return level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                && box.minY >= level.getMinBuildHeight() && box.maxY <= level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(box) && !level.containsAnyLiquid(box) && level.noCollision(box);
    }

    /** Keep paid progress and population reserved while the explicitly gated prerequisite/exit is unavailable. */
    @Override public boolean tick(ProductionPlacement placement, ActiveProduction active) {
        if (placement.getLevel().isClientSide()) return super.tick(placement,active);
        if (requiredProducer != null) {
            String missing = getProduceErrorMsg(placement);
            if (missing != null) return active.pauseBecause(missing);
            // Preserve the gated recipes' existing final-window hold; any work-rate overshoot
            // and all ordinary recipes still revalidate the actual exit before spawning below.
            if (active.ticksLeft <= 10 && !hasSafeSpawnExit(placement))
                return active.pauseBecause("dreamseekers.production.exit_blocked");
        }
        return placement instanceof DreamProductionPlacement producer
                ? producer.tickProduction(active, () -> super.tick(placement,active)) : super.tick(placement, active);
    }

    /** Hall keeps a failed spawn queued; score/completion happen only after an entity was really added. */
    public net.minecraft.world.entity.Entity spawnForPaidCompletion(DreamProductionPlacement hall) {
        if(!hall.authorizedCompletion(this) || !(hall.getLevel() instanceof ServerLevel level))return null;
        String missing = getProduceErrorMsg(hall);
        if (missing != null) { hall.reportCompletionPause(missing); return null; }
        if (!hasSafeSpawnExit(hall)) { hall.reportCompletionPause("dreamseekers.production.exit_blocked"); return null; }
        var produced=hall.produceUnit(level,entityType.get(),hall.ownerName,false);
        // EntityType.spawn returns its created object even when Forge rejects EntityJoinLevelEvent.
        // Added-to-world is separate from later ID/UUID tracking publication: do not confuse the two.
        boolean accepted=produced!=null && produced.isAddedToWorld() && !produced.isRemoved() && produced.isAlive()
                && produced.level()==level && hall.authorizedCompletion(this);
        missing = getProduceErrorMsg(hall);
        if (accepted && missing == null) return produced;
        if(produced!=null)hall.rollbackUncommittedSpawn(produced);
        hall.reportCompletionPause(missing == null ? "dreamseekers.production.spawn_pending" : missing);
        return null;
    }

    /** RoN's default score method knows only its three built-in worker productions. */
    @Override public void recordScore(ProductionPlacement placement) {
        if (placement.getLevel().isClientSide() || (placement instanceof DreamProductionPlacement hall && !hall.authorizedScore(this))) return;
        var owner = PlayerServerEvents.getRTSPlayer(placement.ownerName);
        if (owner == null) return;
        owner.scores.addToScore(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED);
        boolean worker = this == DreamseekersMod.WORKER_PRODUCTION || this == DreamseekersMod.LEGACY_WORKER_PRODUCTION;
        owner.scores.addToScore(worker ? RTSPlayerScoresEnum.WORKER_UNITS_PRODUCED : RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED);
    }

    private List<FormattedCharSequence> tooltip(boolean cost) {
        var text = new ArrayList<FormattedCharSequence>();
        text.add(Component.translatable(entityType.get().getDescriptionId()).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText());
        if (cost) {
            text.add(ResourceCosts.getFormattedCost(defaultCost));
            text.add(ResourceCosts.getFormattedPopAndTime(defaultCost));
        }
        // Descriptions are whole sentences: split them at the current GUI width instead of one long line.
        text.addAll(wrap(Component.translatable(entityType.get().getDescriptionId() + ".tooltip")));
        if (prerequisiteError != null) {
            text.addAll(wrap(Component.translatable(prerequisiteError),
                    Component.translatable("dreamseekers.production.prerequisite_pause"),
                    Component.translatable("dreamseekers.production.spawn_exit")));
        }
        return text;
    }

    private static List<FormattedCharSequence> wrap(Component... lines) { return com.dreamseekers.client.DreamTooltipText.wrap(lines); }
    @Override public UnitSpawnButton getPlaceButton() {
        // Registered once at load: resolve language and width on every real hover instead.
        return new UnitSpawnButton(getItemName(), icon, List.of()) {
            @Override public void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY) {
                this.tooltipLines = tooltip(false);
                super.renderTooltip(graphics, mouseX, mouseY);
            }
        };
    }
    private List<FormattedCharSequence> tooltip(ProductionPlacement placement) {
        var text=tooltip(false);var quote=getCost(true,placement.ownerName);
        text.add(1,ResourceCosts.getFormattedCost(quote));
        text.add(2,Component.translatable("research.dreamseekers.training_seconds",quote.population,ProductionWorkRates.estimatedSeconds(quote.ticks,ProductionWorkRates.percent(placement,this))).getVisualOrderText());
        for(var extra:baseExtraCost.entrySet())text.add(Component.translatable("dreamseekers.production.extra_cost",
                com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem.resourceLabel(extra.getKey()),extra.getValue()).getVisualOrderText());
        String missing = getProduceErrorMsg(placement);
        if(missing != null && !missing.equals(prerequisiteError))
            text.addAll(wrap(Component.translatable(missing).withStyle(net.minecraft.ChatFormatting.RED)));
        if(!canAffordExtra(placement))text.addAll(wrap(Component.translatable("dreamseekers.production.extra_insufficient")));
        text.addAll(wrap(Component.translatable("research.economy.refund_notice")));return text;
    }
    @Override public StartProductionButton getStartButton(ProductionPlacement placement, Keybinding key) {
        return getStartButton(placement, key, lines -> {});
    }
    /** Compose presentation-only context after each fresh quote, never onto a cached tooltip. */
    static <T> Supplier<List<T>> decorateLiveTooltip(Supplier<List<T>> contents, Consumer<List<T>> decorator) {
        java.util.Objects.requireNonNull(contents);
        java.util.Objects.requireNonNull(decorator);
        return () -> {
            var lines = new ArrayList<>(contents.get());
            decorator.accept(lines);
            return lines;
        };
    }
    public StartProductionButton getStartButton(ProductionPlacement placement, Keybinding key,
            Consumer<List<FormattedCharSequence>> tooltipDecorator) {
        var liveTooltip = decorateLiveTooltip(() -> tooltip(placement), tooltipDecorator);
        return new StartProductionButton(getItemName(), icon, key, () -> false, () -> true, liveTooltip.get(), this) {
            @Override public void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY) {
                // Cached buttons/Hall pages retain identity; resolve the current private Wind snapshot,
                // owner, loaded Dais aura, language and page context on every real hover.
                this.tooltipLines = liveTooltip.get();
                super.renderTooltip(graphics, mouseX, mouseY);
            }
        };
    }
    @Override public StopProductionButton getCancelButton(ProductionPlacement placement, boolean first) {
        // Resolve at button creation, so changing the client language does not leave a cached label.
        // getItemName() remains the stable registry identity used by production and saves.
        var button = new StopProductionButton(Component.translatable(entityType.get().getDescriptionId()).getString(),
                icon, placement, this, first);
        // The public client queue does not expose the immutable paid receipt: never invent a refund quote.
        button.tooltipLines = wrap(Component.translatable(entityType.get().getDescriptionId()),
                Component.translatable("research.economy.refund_notice"));
        return button;
    }
}
