package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Observes only sources retained by an actual launch. No last-attacker inference. */
@Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID)
public final class EconomicSourceLifecycle {
    private EconomicSourceLifecycle() {}
    // A strong value holding LivingDeathEvent would retain its own weak key forever.
    private static final Map<LivingEntity,WeakReference<State>> TRACKED=new WeakHashMap<>();
    static final class State {
        LivingDeathEvent death;
        Entity.RemovalReason removal;
        boolean invalidRemoval;
        boolean terminal(LivingEntity source) {
            return !invalidRemoval&&death!=null&&death.getEntity()==source&&!death.isCanceled()
                    &&source.getHealth()<=0&&source.isDeadOrDying()
                    &&(!source.isRemoved()||(removal==Entity.RemovalReason.KILLED
                        &&source.getRemovalReason()==Entity.RemovalReason.KILLED));
        }
    }
    static State track(LivingEntity source) {
        State state=find(source);
        if(state==null) {state=new State();TRACKED.put(source,new WeakReference<>(state));}
        return state;
    }
    private static State find(LivingEntity source) {
        WeakReference<State> reference=TRACKED.get(source);return reference==null?null:reference.get();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void death(LivingDeathEvent event) {
        if(event.getEntity().level().isClientSide())return;
        State state=find(event.getEntity());if(state!=null)state.death=event;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void left(EntityLeaveLevelEvent event) {
        if(event.getLevel().isClientSide()||!(event.getEntity() instanceof LivingEntity source))return;
        State state=find(source);if(state==null)return;
        state.removal=source.getRemovalReason();
        if(state.removal!=Entity.RemovalReason.KILLED||state.death==null||state.death.isCanceled())
            state.invalidRemoval=true;
    }
    static void clear(){TRACKED.clear();}
}
