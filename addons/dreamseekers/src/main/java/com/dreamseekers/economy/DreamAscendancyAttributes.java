package com.dreamseekers.economy;

import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Owner-level permanent attributes survive normal entity saves, but are revalidated every server tick.
 * Exactly our own stable IDs are changed; other upgrades/buffs stay intact. */
@Mod.EventBusSubscriber(modid="dreamseekers")
public final class DreamAscendancyAttributes {
    public static final UUID HEALTH_ID=UUID.fromString("4593d511-a914-4fac-aed1-aac35f590e50");
    public static final UUID DAMAGE_ID=UUID.fromString("14d7ab84-9259-4b23-a6d0-c669ba3b1c0c");
    private DreamAscendancyAttributes(){}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity=event.getEntity();
        if(entity.level().isClientSide()||!(entity instanceof Unit unit)||entity.isRemoved())return;
        reconcile(entity,rankFor(entity));
    }
    /** Shared owner/account qualification for attributes and the native fixed-damage ordinary fang adapter. */
    public static int rankFor(LivingEntity entity) {
        if(entity.level().isClientSide()||!(entity instanceof Unit unit)||entity.isRemoved())return 0;
        String owner=unit.getOwnerName();
        var account=ExtendedResourcesSync.account(owner);
        return account!=null&&account.extendedStateSupported()
            &&DreamPassiveEconomy.isDreamOwner(PlayerServerEvents.getRTSPlayer(owner))
            ? DreamEconomyResearch.Line.ASCENDANCY.serverLevel(owner) : 0;
    }
    public static boolean enabledFor(LivingEntity entity) { return rankFor(entity)>0; }
    /** Compatibility with explicit rank-one provider callers; never down-rank a higher researched owner. */
    public static void reconcile(LivingEntity entity,boolean enabled) {
        reconcile(entity,enabled?Math.max(1,rankFor(entity)):0);
    }
    public static void reconcile(LivingEntity entity,int rank) {
        double bonus=CapitalResearchRanks.ascendancyBonus(rank);
        AttributeInstance hp=entity.getAttribute(Attributes.MAX_HEALTH);
        double oldMax=entity.getMaxHealth();float oldHealth=entity.getHealth();
        boolean changed=modify(hp,HEALTH_ID,bonus,"dreamseekers_world_tree_health");
        if(changed&&oldMax>0&&Double.isFinite(oldMax)&&Float.isFinite(oldHealth))
            entity.setHealth(DreamResearchMath.healthAfter(oldHealth,oldMax,entity.getMaxHealth()));
        // RoN uses its own attack_damage attribute. Vanilla generic.attack_damage is intentionally untouched.
        AttributeInstance ad=entity.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get());
        modify(ad,DAMAGE_ID,ad!=null&&ad.getBaseValue()>0?bonus:0,"dreamseekers_world_tree_damage");
    }
    private static boolean modify(AttributeInstance attribute,UUID id,double bonus,String name) {
        if(attribute==null)return false;
        var old=attribute.getModifier(id);
        if(bonus>0&&old!=null&&old.getOperation()==AttributeModifier.Operation.MULTIPLY_TOTAL
                &&Double.compare(old.getAmount(),bonus)==0)return false;
        if(bonus==0&&old==null)return false;
        if(old!=null)attribute.removeModifier(id);
        if(bonus>0)attribute.addPermanentModifier(new AttributeModifier(id,name,bonus,AttributeModifier.Operation.MULTIPLY_TOTAL));
        return true;
    }
}
