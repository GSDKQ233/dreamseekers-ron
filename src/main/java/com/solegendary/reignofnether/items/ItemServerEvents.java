package com.solegendary.reignofnether.items;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.addon.ItemShopAddon;
import com.solegendary.reignofnether.building.buildings.placements.ItemShopPlacement;
import com.solegendary.reignofnether.entities.AdjustablePrimedTnt;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.player.PlayerClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.kill.EconomicUnitIdentity;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.survival.SurvivalResult;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Random;
import java.util.UUID;

public class ItemServerEvents {

    public static final boolean ENABLED = UnitItem.ENABLED;

    // for making every RTS player have the same drops
    public static final Long ITEM_DROPS_SEED = new Random().nextLong(1, Long.MAX_VALUE);
    public static final Long ITEM_RARITY_SEED = new Random().nextLong(1, Long.MAX_VALUE);

    public static void buyItem(
            Unit unit,
            String descId,
            BlockPos shopPos
    ) {
        if (!ENABLED || unit == null || descId == null || descId.isBlank() || descId.length() > 256 || shopPos == null) return;
        UnitItem requested = ItemUtil.getUnitItem(descId);
        if (requested == null) return;

        for (BuildingPlacement bpl : BuildingServerEvents.getBuildings()) {
            if (bpl.originPos.equals(shopPos) && bpl instanceof ItemShopPlacement itemShopPlacement && bpl.isBuilt
                    && unit instanceof Entity entity && entity.level()==bpl.getLevel()) {
                ItemShopAddon itemShopAddon = bpl.getBuilding().getActiveAddon(ItemShopAddon.class);
                if (itemShopAddon != null && unit instanceof UnitInventory) {
                    itemShopAddon.buyItem(unit, itemShopPlacement, requested);
                    break;
                }
            }
        }
    }

    public static void swapItems(
            Unit unit, // unit performing the action
            int invIndex1,
            int invIndex2
    ) {
        if (!ENABLED) return;

        if (unit instanceof UnitInventory inv)
            inv.swapSlots(invIndex1, invIndex2);
    }
    
    public static void doAction(
            ItemAction action,
            Unit unit, // unit performing the action
            UUID itemUuid, // uuid of the item in the unit's inventory (unused for PICKUP/NONE)
            int targetId, // GIVE/USE_ON_ENTITY: target unit, PICKUP: target ItemEntity (-1 if unused)
            BlockPos blockTarget // DROP/USE_ON_BLOCK: block, SELL/USE_ON_BUILDING: building pos (null if unused)) {
    ) {
        if (!ENABLED) return;

        ServerLevel level = unit instanceof Entity actor && actor.level() instanceof ServerLevel serverLevel
                ? serverLevel : null;

        if (unit instanceof UnitInventory inv) {
            if (inv.canPickupUnitItems() && unit.getItemGoal() != null && level != null) {
                Entity entity = level.getEntity(targetId);
                ItemStack itemInHand = inv.get(itemUuid);
                if (action == ItemAction.USE) {
                    if (inv.use(ItemUtil.getUUID(itemInHand)))
                        Unit.fullResetBehaviours(unit);
                } else if (action == ItemAction.GIVE && entity instanceof UnitInventory invTarget && !invTarget.canPickupUnitItems()) {
                    HudClientboundPacket.showTempMessageI18n(unit.getOwnerName(), "item.reignofnether.error.cant_hold_items");
                } else {
                    ItemEntity itemTarget = (entity instanceof ItemEntity ie) ? ie : null;
                    LivingEntity leTarget = (entity instanceof LivingEntity le2) ? le2 : null;
                    BuildingPlacement buildingTarget = blockTarget != null ? BuildingUtils.findBuilding(level, blockTarget) : null;
                    boolean useItem = List.of(ItemAction.USE_ON_BUILDING, ItemAction.USE_ON_BLOCK, ItemAction.USE_ON_ENTITY).contains(action);
                    boolean canUseItem = false;
                    if (useItem && itemInHand != null) {
                        UnitItem unitItem = ItemUtil.getUnitItem(itemInHand);
                        if (unitItem != null)
                            canUseItem = inv.canUseItem(unitItem);
                        if (!canUseItem)
                            HudClientboundPacket.showTempMessageI18n(unit.getOwnerName(), "item.reignofnether.error.cant_use_item");
                    }
                    if (canUseItem || !useItem) {
                        Unit.fullResetBehaviours(unit);
                        if (action == ItemAction.USE_ON_BLOCK)
                            buildingTarget = null;
                        unit.getItemGoal().start(itemInHand, itemTarget, leTarget, blockTarget, buildingTarget, useItem);
                    }
                }
            } else if (action == ItemAction.DROP && SandboxServer.isAnyoneASandboxPlayer()) {
                if (inv.deleteItem(itemUuid))
                    HudClientboundPacket.showTempMessageI18n(unit.getOwnerName(), "item.reignofnether.hud.item_deleted");
            } else {
                HudClientboundPacket.showTempMessageI18n(unit.getOwnerName(), "item.reignofnether.error.cant_use_item");
            }
        }
    }

    // Points needed for each successive drop increase range from 15 to 45
    private static int getCreepScoreThreshold(int itemsDropped) {
        int total = 0;
        for (int i = 0; i <= itemsDropped; i++)
            total += Math.min(12 + (i * 4), 48);
        return total;
    }

    public static void dropNextItem(RTSPlayer rtsPlayer, LivingEntity dropper) {
        dropNextItem(rtsPlayer, dropper.level(), dropper.position());
    }

    private static final java.util.Set<RTSPlayer> publishingDrop =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public static boolean dropNextItem(RTSPlayer seat, Level level, net.minecraft.world.phys.Vec3 pos) {
        return publishDrop(seat, level, pos, false);
    }

    /** Only this path consumes a previously vested survival claim. Creep cannot steal its head. */
    public static boolean redeemNextSurvivalReward(RTSPlayer seat, ServerLevel level, net.minecraft.world.phys.Vec3 pos) {
        return publishDrop(seat, level, pos, true);
    }

    /** Forge can call back into defeat/reset/beacon completion before addFreshEntity returns.
     * Revalidate both the exact seat/claim and the exact published item before consuming anything. */
    private static boolean publishDrop(RTSPlayer seat, Level world, net.minecraft.world.phys.Vec3 pos, boolean survivalClaim) {
        if (!(world instanceof ServerLevel level) || seat == null || pos == null || SurvivalResult.isAfterMatch()
                || PlayerServerEvents.getRTSPlayer(seat.name) != seat || !publishingDrop.add(seat)) return false;
        try {
            UnitItem item = seat.itemDropQueue.peekFirst();
            var claim = seat.pendingSurvivalRewards.peekFirst();
            if (item == null || (survivalClaim ? claim != pos : claim != null)) return false;
            ItemStack stack = new ItemStack(item.item);
            stack.setCount(item.defaultStackCount);
            ItemEntity entity = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
            if (!level.addFreshEntity(entity)) return false;
            Entity visible = level.getEntity(entity.getUUID());
            if (SurvivalResult.isAfterMatch() || PlayerServerEvents.getRTSPlayer(seat.name) != seat
                    || seat.itemDropQueue.peekFirst() != item || seat.pendingSurvivalRewards.peekFirst() != claim
                    || entity.level() != level || !entity.isAlive() || entity.isRemoved()
                    || !(visible == entity || visible == null && entity.isAddedToWorld())
                    || !entity.getItem().is(item.item) || entity.getItem().getCount() != item.defaultStackCount) {
                if (!entity.isRemoved()) entity.discard(); // this attempt's exact candidate, never another UUID lifetime
                return false;
            }
            seat.itemDropQueue.removeFirst();
            if (survivalClaim) {
                seat.pendingSurvivalRewards.removeFirst();
                seat.survivalRewardDraws++;
            }
            SoundClientboundPacket.playSoundAtPos(getItemDropSound(item.rarity), BlockPos.containing(pos), 2.5f);
            ReignOfNether.LOGGER.info("{} received item drop", seat.name);
            return true;
        } finally {
            publishingDrop.remove(seat);
        }
    }

    private record PendingItemDeath(LivingEntity victim, EconomicUnitIdentity.Stamp victimIdentity,
                                    EconomicUnitIdentity.Stamp killerIdentity, RTSPlayer player,
                                    String killerName, int creepScore) {}
    private static final Map<LivingDeathEvent, PendingItemDeath> pendingItemDeaths = new WeakHashMap<>();

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent evt) {
        LivingEntity killedEntity = evt.getEntity();
        if (!(killedEntity.level() instanceof ServerLevel) || !(killedEntity instanceof Unit unitKilled)) return;
        String killerName = "";
        LivingEntity attributedKiller = null;
        Entity directEntity = evt.getSource().getDirectEntity();
        Entity sourceEntity = evt.getSource().getEntity();
        LivingEntity lastHurtBy = killedEntity.getLastHurtByMob();
        if (directEntity instanceof Unit unit) {
            killerName = unit.getOwnerName();
            if (directEntity instanceof LivingEntity living) attributedKiller = living;
        } else if (directEntity != null && directEntity.getPersistentData().contains("ownerName")) {
            killerName = directEntity.getPersistentData().getString("ownerName");
        } else if (sourceEntity instanceof Unit unit) {
            killerName = unit.getOwnerName();
            if (sourceEntity instanceof LivingEntity living) attributedKiller = living;
        } else if (sourceEntity != null && sourceEntity.getPersistentData().contains("ownerName")) {
            killerName = sourceEntity.getPersistentData().getString("ownerName");
        } else if (lastHurtBy instanceof Unit unit) {
            killerName = unit.getOwnerName(); attributedKiller = lastHurtBy;
        } else if (lastHurtBy != null && lastHurtBy.getPersistentData().contains("ownerName")) {
            killerName = lastHurtBy.getPersistentData().getString("ownerName");
        } else if (directEntity instanceof AdjustablePrimedTnt tnt && tnt.getOwner() instanceof Unit unit) {
            killerName = unit.getOwnerName(); attributedKiller = tnt.getOwner();
        }
        boolean isNeutral = unitKilled.getOwnerName().isBlank();
        boolean willDropOwnItem = unitKilled instanceof UnitInventory inv && !inv.isEmpty() && !(unitKilled instanceof HeroUnit);
        RTSPlayer player = PlayerServerEvents.getRTSPlayer(killerName);
        if (killerName.isBlank() || !isNeutral || willDropOwnItem || player == null) return;
        int creepScore = unitKilled.getCost().population + 3;
        if (killedEntity.getPersistentData().getBoolean("isFromSpawner"))
            creepScore = Math.max(1, unitKilled.getCost().population);
        pendingItemDeaths.put(evt, new PendingItemDeath(killedEntity, EconomicUnitIdentity.capture(killedEntity),
                attributedKiller != null ? EconomicUnitIdentity.capture(attributedKiller) : null,
                player, killerName, creepScore));
    }

    /** Shares UnitServerEvents' final Forge-dispatch death gate. Consuming the exact event snapshot
     * once prevents canceled/replayed deaths from consuming neutral-drop progression or inventory. */
    public static void commitConfirmedDeath(LivingDeathEvent evt) {
        PendingItemDeath pending = pendingItemDeaths.remove(evt);
        if (SurvivalResult.isAfterMatch() || pending == null || evt.isCanceled() || pending.victim.getHealth() > 0 || !pending.victim.isDeadOrDying()
                || pending.victim.isRemoved() && pending.victim.getRemovalReason() != Entity.RemovalReason.KILLED
                || !(pending.victimIdentity.sameOwner() || pending.victimIdentity.clearedOnceByDefeat())
                || pending.killerIdentity != null && !pending.killerIdentity.sameOwner()
                || PlayerServerEvents.getRTSPlayer(pending.killerName) != pending.player) return;
        RTSPlayer player = pending.player;
        player.creepScore += pending.creepScore;
        ReignOfNether.LOGGER.info("+" + pending.creepScore + " creepScore for: " + player.name);
        // Keep the existing progression/balance; only the irreversible commit boundary changes.
        while (player.creepScore >= getCreepScoreThreshold(player.itemsDropped)) {
            // Unredeemed wave claims own the queue head. Never consume that item for a
            // subsequent creep or spin on an empty/vetoed drop.
            if (!player.pendingSurvivalRewards.isEmpty()
                    || !dropNextItem(player, pending.victim.level(), pending.victim.position())) break;
            player.itemsDropped += 1;
            PlayerClientboundPacket.syncItemsDropped(player.name, player.itemsDropped);
        }
    }

    @SubscribeEvent
    public static void clearPendingItemDeaths(ServerStoppingEvent evt) {
        pendingItemDeaths.clear();
    }

    public static SoundAction getItemDropSound(Rarity rarity) {
        if (rarity == Rarity.RARE || rarity == Rarity.EPIC)
            return SoundAction.ITEM_DROP_RARE;
        if (rarity == UnitItemRarity.LEGENDARY || rarity == UnitItemRarity.MYTHIC)
            return SoundAction.ITEM_DROP_LEGENDARY;
        return SoundAction.ITEM_DROP_COMMON;
    }

    // give items to regular units to hold and drop on death
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract evt) {
        if (evt.getLevel().isClientSide()) return;
        if (evt.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = evt.getEntity();
        ItemStack stack = evt.getItemStack();
        Entity target = evt.getTarget();
        String mobName = target.getName().getString();
        String itemName = stack.getHoverName().getString();
        UnitItem unitItem = ItemUtil.getUnitItem(stack);

        if (target instanceof Mob && target instanceof UnitInventory inv &&
            (inv.canPickupUnitItems() || player.isCreative()) &&
            !stack.isEmpty() && unitItem != null
        ) {
            evt.setCanceled(true);

            if (inv.isFull(unitItem)) {
                player.sendSystemMessage(Component.translatable("item.reignofnether.error.full_inventory", mobName));
                evt.setCancellationResult(InteractionResult.FAIL);
            } else if (inv.tryAdding(new ItemStack(stack.getItem(), 1))) {
                player.sendSystemMessage(Component.translatable("item.reignofnether.hud.give_to_unit", itemName, mobName));
                if (!player.isCreative()) {
                    stack.setCount(stack.getCount() - 1);
                }
                evt.setCancellationResult(InteractionResult.SUCCESS);
            }
            else {
                player.sendSystemMessage(Component.translatable("item.reignofnether.error.failed_other", mobName));
                evt.setCancellationResult(InteractionResult.FAIL);
            }
        }
    }
}
