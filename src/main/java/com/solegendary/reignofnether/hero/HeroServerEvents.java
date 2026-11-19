package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.items.UnitInventory;
import com.solegendary.reignofnether.items.UnitItems;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.kill.EconomicUnitIdentity;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.unit.HeroUnitSave;
import com.solegendary.reignofnether.unit.HeroUnitSaveData;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.commons.lang3.text.WordUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class HeroServerEvents {

    // Compatibility view used by hero count, revival, claims and match reset. While a
    // server is active this IS its Overworld SavedData list, not a process-wide copy.
    public static ArrayList<HeroUnitSave> fallenHeroes = new ArrayList<>();
    private static ServerLevel activeOverworld;

    public static void bindFallenHeroes(ServerLevel overworld) {
        fallenHeroes = HeroUnitSaveData.getInstance(overworld).heroUnits;
        activeOverworld = overworld;
    }

    public static boolean isCurrentHeroWorld(ServerLevel level) {
        return activeOverworld != null && level != null
                && level.getServer().overworld() == activeOverworld && level.getServer().isSameThread();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent evt) {
        if (activeOverworld != null && activeOverworld.getServer() == evt.getServer()) {
            activeOverworld = null;
            // Detach after the normal final save; clearing this list would erase the old world's rows.
            fallenHeroes = new ArrayList<>();
        }
    }
    // Event keys are weak and are not retained by their values. Canceled/aborted dispatches
    // cannot leave a strong victim/event queue until server shutdown.
    private static final Map<LivingDeathEvent, PendingHeroDeath> pendingDeaths = new WeakHashMap<>();
    private static final Map<LivingEntity, Boolean> committedDeaths = new WeakHashMap<>();

    private record ExperienceRecipient(HeroUnit hero, EconomicUnitIdentity.Stamp identity, int value) {}

    private static final class PendingHeroDeath {
        final LivingEntity victim;
        final ServerLevel level;
        final EconomicUnitIdentity.Stamp identity;
        final String owner, displayName;
        final Vec3 position;
        final int heroLevel;
        final HeroUnitSave fallen;
        final List<ExperienceRecipient> recipients = new ArrayList<>();

        PendingHeroDeath(LivingEntity victim, ServerLevel level, Unit deadUnit) {
            this.victim = victim;
            this.level = level;
            identity = EconomicUnitIdentity.capture(victim);
            owner = deadUnit.getOwnerName();
            position = victim.position();
            displayName = WordUtils.capitalize(MiscUtil.getSimpleEntityName(victim).replace("_", " "));
            heroLevel = victim instanceof HeroUnit hero ? hero.getHeroLevel() : 0;
            int popCost = deadUnit.getCost().population;
            if (popCost > 0) {
                for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
                    if (entity == victim || entity.level() != level || !entity.isAlive()
                            || !(entity instanceof HeroUnit hero)
                            || entity.distanceToSqr(victim) >= HeroExperienceOrb.RANGE * HeroExperienceOrb.RANGE) continue;
                    String heroOwner = hero.getOwnerName();
                    if (!AlliancesServerEvents.isAllied(heroOwner, owner) && !heroOwner.equals(owner)
                            && hero.getHeroLevel() < HeroUnit.MAX_LEVEL
                            && (hero.getHeroLevel() < HeroUnit.MAX_NEUTRAL_EXP_LEVEL || !owner.isBlank())) {
                        recipients.add(new ExperienceRecipient(hero, EconomicUnitIdentity.capture(entity),
                                (popCost + 1) * 5 + heroLevel * 5));
                    }
                }
            }
            if (victim instanceof HeroUnit hero) {
                // Detached stacks prevent inventory/removal listeners from mutating the revival
                // evidence captured before the final result of Forge's event dispatch is known.
                NonNullList<ItemStack> items = NonNullList.withSize(UnitInventory.MAX_INVENTORY_SIZE, ItemStack.EMPTY);
                if (hero instanceof UnitInventory inventory) {
                    NonNullList<ItemStack> held = inventory.getAllItems();
                    for (int i = 0; i < items.size() && i < held.size(); i++) items.set(i, held.get(i).copy());
                }
                fallen = new HeroUnitSave(victim.getStringUUID(), victim.getType().getDescriptionId(), owner,
                        hero.getExperience(), hero.getSkillPoints(), 0,
                        hero.getHeroAbilities().size() > 0 ? hero.getHeroAbilities().get(0).getRank(hero) : 0,
                        hero.getHeroAbilities().size() > 1 ? hero.getHeroAbilities().get(1).getRank(hero) : 0,
                        hero.getHeroAbilities().size() > 2 ? hero.getHeroAbilities().get(2).getRank(hero) : 0,
                        hero.getHeroAbilities().size() > 3 ? hero.getHeroAbilities().get(3).getRank(hero) : 0, items);
                fallen.economicValue = RtsKillValueClaims.copyValue(victim);
            } else fallen = null;
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent evt) {
        if (!(evt.getEntity().level() instanceof ServerLevel level)) return;

        // Rescue must still run inside LivingDeathEvent so vanilla die observes the cancellation.
        if (evt.getEntity() instanceof UnitInventory inv && inv.canUseItem(UnitItems.TOTEM_OF_UNDYING)) {
            ItemStack itemStack = inv.get(UnitItems.TOTEM_OF_UNDYING);
            if (itemStack != null) {
                evt.getEntity().setHealth(evt.getEntity().getMaxHealth() / 2);
                evt.getEntity().addEffect(new MobEffectInstance(MobEffectRegistrar.INVINCIBLE.get(),
                        UnitItems.TOTEM_OF_UNDYING_INVINCIBILITY_DURATION_SECONDS * 20, 0, true, false));
                itemStack.setCount(itemStack.getCount() - 1);
                if (itemStack.isEmpty()) inv.deleteItem(UnitItems.TOTEM_OF_UNDYING);
                evt.getEntity().level().broadcastEntityEvent(evt.getEntity(), (byte) 35);
                evt.setCanceled(true);
                return;
            }
        }
        if (evt.getEntity() instanceof Unit unit && !committedDeaths.containsKey(evt.getEntity()))
            pendingDeaths.put(evt, new PendingHeroDeath(evt.getEntity(), level, unit));
    }

    /** Called by UnitServerEvents after the original ForgeHooks.onLivingDeath dispatch returns
     * uncanceled, before native rewards/scripts can reenter or change ownership. This preserves
     * synchronous real-death revival eligibility without publishing a canceled death. */
    public static void commitConfirmedDeath(LivingDeathEvent evt) {
        PendingHeroDeath pending = pendingDeaths.remove(evt);
        if (pending == null || evt.isCanceled() || pending.victim.getHealth() > 0
                || !pending.victim.isDeadOrDying() || committedDeaths.containsKey(pending.victim)
                || pending.victim.isRemoved() && pending.victim.getRemovalReason() != Entity.RemovalReason.KILLED
                || !(pending.identity.sameOwner() || pending.identity.clearedOnceByDefeat())) return;
        committedDeaths.put(pending.victim, true);

        for (ExperienceRecipient recipient : pending.recipients) {
            if (!recipient.identity.sameOwner() || !recipient.identity.entity().isAlive()) continue;
            int expValue = recipient.value;
            while (expValue > 0) {
                int amount = expValue >= 2 ? 2 : 1;
                HeroExperienceOrb orb = HeroExperienceOrb.newOrb(pending.level, recipient.hero, pending.owner.isBlank(),
                        pending.position.x, pending.position.y, pending.position.z, amount);
                expValue -= amount;
                pending.level.addFreshEntity(orb);
            }
        }
        if (pending.fallen != null && isCurrentHeroWorld(pending.level)) {
            fallenHeroes.removeIf(hero -> hero.ownerName.equals(pending.owner) && hero.name.equals(pending.fallen.name));
            fallenHeroes.add(pending.fallen);
            FallenHeroClientboundPacket.addFallenHero(pending.fallen);
            UnitServerEvents.saveFallenHeroUnits(pending.level);
            for (RTSPlayer player : PlayerServerEvents.rtsPlayers) {
                if (player.name.equals(pending.owner) || AlliancesServerEvents.isAllied(player.name, pending.owner)) {
                    PlayerServerEvents.sendMessageToPlayer(player.name, "hud.hero.reignofnether.death", true,
                            pending.owner, pending.displayName, pending.heroLevel);
                    SoundClientboundPacket.playSoundForPlayer(SoundAction.ENEMY, player.name);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent evt) {
        if (!evt.getEntity().level().isClientSide() && evt.getEntity().isAlive()) committedDeaths.remove(evt.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        pendingDeaths.clear();
        committedDeaths.clear();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp) || !isCurrentHeroWorld(sp.serverLevel())) return;
        for (HeroUnitSave fallenHero : fallenHeroes) {
            if (fallenHero.ownerName.equals(sp.getName().getString()))
                FallenHeroClientboundPacket.addFallenHero(sp, fallenHero);
        }
    }
}
