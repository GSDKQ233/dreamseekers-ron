// SPDX-License-Identifier: GPL-3.0-only
package com.solegendary.reignofnether.api;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import java.util.*;
/** Opt-in final damage write. Death callbacks run only AFTER this write's exact receipts. */
public final class HealthDamageWriters {
    private HealthDamageWriters() {}
    public interface Managed { void writeDamageHealth(DamageSource source,float requestedHealth); }
    private static final ThreadLocal<ArrayDeque<List<Runnable>>> FRAMES=ThreadLocal.withInitial(ArrayDeque::new);
    /** Wrap the original write AND every associated exact-write observer/finalizer. */
    public static void withReceipt(LivingEntity victim,DamageSource source,Runnable original){
        if(victim.level().isClientSide()||!(victim instanceof Managed)){original.run();return;}
        com.solegendary.reignofnether.resources.kill.EconomicSharedDamageHooks.write(victim,source,()->{
            var frames=FRAMES.get();var completions=new ArrayList<Runnable>();frames.push(completions);
            try{original.run();}
            finally{
                frames.pop();
                try{for(Runnable completion:completions)completion.run();}
                finally{if(frames.isEmpty())FRAMES.remove();}
            }
        });
    }
    public static void afterReceipt(Runnable completion){
        var frames=FRAMES.get();if(frames.isEmpty()){FRAMES.remove();completion.run();}else frames.peek().add(completion);
    }
    public static void write(LivingEntity victim,DamageSource source,float health) {
        if(!victim.level().isClientSide()&&victim instanceof Managed managed)managed.writeDamageHealth(source,health);
        else victim.setHealth(health);
    }
}
