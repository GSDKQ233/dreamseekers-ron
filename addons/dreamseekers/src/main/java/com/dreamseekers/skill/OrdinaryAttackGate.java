// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/** Only real ordinary call sites establish these short scopes. No damage tag grants a role.
 * The final native hurt/publication is the commit boundary; previously committed hits stay valid.
 */
public final class OrdinaryAttackGate {
    private OrdinaryAttackGate() { }
    private static final ArrayDeque<Melee> MELEE=new ArrayDeque<>();
    private static final ArrayDeque<LivingEntity> RANGED=new ArrayDeque<>();
    private static final class Melee {
        final Mob source;final Entity target;
        int suspended;boolean claimed;
        Melee(Mob source,Entity target) { this.source=source;this.target=target; }
    }
    private static boolean server(Entity entity) {
        return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    public static boolean melee(Mob source,Entity target,BooleanSupplier original) {
        if(!server(source))return original.getAsBoolean();
        if(TerrorbeakDreadRoar.hasDisarm(source))return false;
        Melee frame=new Melee(source,target);MELEE.push(frame);
        try { return original.getAsBoolean(); }
        finally { MELEE.removeFirstOccurrence(frame); }
    }
    /** Exact native Mob/Rabbit final hurt call, never a blanket LivingAttack/final-damage gate. */
    public static boolean finalMelee(Mob source,Entity target,net.minecraft.world.damagesource.DamageSource exact,BooleanSupplier original) {
        return finalMelee(source,target,exact,null,original);
    }
    static boolean finalNativeMelee(Mob source,Entity target,net.minecraft.world.damagesource.DamageSource damage,BooleanSupplier original) {
        return finalMelee(source,target,damage,damage,original);
    }
    private static boolean finalMelee(Mob source,Entity target,net.minecraft.world.damagesource.DamageSource exact,
            net.minecraft.world.damagesource.DamageSource debtExact,BooleanSupplier original) {
        Melee frame=MELEE.peek();
        if(server(source)&&frame!=null&&frame.source==source&&frame.target==target&&!frame.claimed&&frame.suspended==0) {
            frame.claimed=true;
            if(TerrorbeakDreadRoar.hasDisarm(source))return false;
            return DamageTriggeredDefense.melee(source,target,exact,()->ShadowHandGrip.commitMelee(source,target,
                    ()->FriendlyDebtContract.commitMelee(source,target,debtExact,original)));
        }
        return original.getAsBoolean();
    }
    public static void rabbitSound(LivingEntity source,Runnable original) {
        Melee frame=MELEE.peek();
        if(!server(source)||frame==null||frame.source!=source) { original.run();return; }
        frame.suspended++;
        try { original.run(); } finally { frame.suspended--; }
    }
    /** Called only around native ordinary goal/fang/crossbow dispatch, never around skill factories. */
    public static void ranged(LivingEntity source,Runnable original) {
        if(!server(source)) { original.run();return; }
        if(TerrorbeakDreadRoar.hasDisarm(source))return;
        RANGED.push(source);
        try { original.run(); } finally { RANGED.pop(); }
    }
    /** Called at known factories' exact new entity argument. Join callbacks are before commit.
     * No rejection path falls back to publishing, and no already-published projectile is revisited.
     */
    public static boolean publish(LivingEntity source,Entity projectile,BooleanSupplier original) {
        if(!server(source)||RANGED.peek()!=source)return original.getAsBoolean();
        if(TerrorbeakDreadRoar.hasDisarm(source)) { projectile.discard();return false; }
        boolean accepted=original.getAsBoolean();
        if(accepted&&TerrorbeakDreadRoar.hasDisarm(source)) { projectile.discard();return false; }
        return accepted;
    }
}
