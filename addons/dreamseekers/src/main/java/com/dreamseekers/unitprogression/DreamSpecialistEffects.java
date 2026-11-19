package com.dreamseekers.unitprogression;

import com.dreamseekers.economy.DreamResearchMath;
import com.dreamseekers.unit.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Owner-derived effects. No item mutation, order reset, healing pulse or new cached authority. */
@Mod.EventBusSubscriber(modid="dreamseekers")
public final class DreamSpecialistEffects {
    public static final UUID SHELL_HEALTH_ID=UUID.fromString("d5b82a9b-07d9-4723-a76e-7d26bdc1d9e4");
    private DreamSpecialistEffects() {}
    public static boolean precision(FireflyWorker worker) {
        return DreamSpecialistResearch.unlocked(worker.getOwnerName(),DreamSpecialistResearch.PRECISION_TOOLS_ID,worker.level().isClientSide());
    }
    public static int cargoCapacity(FireflyWorker worker) { return precision(worker) ? 125 : 100; }
    public static int returnThreshold(FireflyWorker worker,int nativeThreshold) {
        return precision(worker) ? Math.max(nativeThreshold,63) : nativeThreshold;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity body=event.getEntity();
        if (body.level().isClientSide() || body.isRemoved()
                || !(body instanceof BeetleUnit || body instanceof BasaltSnailUnit)) return;
        var unit=(DreamRtsUnit)body;
        reconcileShell(body,DreamSpecialistResearch.unlocked(unit.getOwnerName(),DreamSpecialistResearch.SHELL_WEAVING_ID,false));
    }
    public static void reconcileShell(LivingEntity body,boolean enabled) {
        var hp=body.getAttribute(Attributes.MAX_HEALTH);
        if(hp==null)return;
        var previous=hp.getModifier(SHELL_HEALTH_ID);
        if(enabled && previous!=null && previous.getOperation()==AttributeModifier.Operation.MULTIPLY_TOTAL
                && Double.compare(previous.getAmount(),0.10D)==0)return;
        if(!enabled && previous==null)return;
        double oldMax=body.getMaxHealth();float oldHealth=body.getHealth();
        if(previous!=null)hp.removeModifier(SHELL_HEALTH_ID);
        if(enabled)hp.addPermanentModifier(new AttributeModifier(SHELL_HEALTH_ID,"dreamseekers_shell_weaving",0.10D,AttributeModifier.Operation.MULTIPLY_TOTAL));
        if(oldMax>0 && Double.isFinite(oldMax) && Float.isFinite(oldHealth))
            body.setHealth(DreamResearchMath.healthAfter(oldHealth,oldMax,body.getMaxHealth()));
    }
}
