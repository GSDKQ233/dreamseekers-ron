// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.bonewing;

import com.dreamseekers.unit.BoneWingUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.pasterdream.entity.BoneWingFireBallProjectileEntity;
import java.util.UUID;

/** Physical single-impact ranged bolt; only installed runtime renderer/particle visuals are reused.
 * No original hit procedure, fire, extra knockback, block activation, explosion or pickup.
 * Unloaded/reloaded projectiles fail closed; NBT can never arm one or mint another paid attack.
 */
public final class BoneWingBolt extends BoneWingFireBallProjectileEntity {
    private BoneWingUnit caster;
    private UUID casterId;
    private String ownerAtLaunch;
    private long ownerEpoch;
    private float damage;
    private boolean airSuperiority;
    private boolean armed;
    private boolean spent;
    private boolean everArmed;
    public BoneWingBolt(EntityType<? extends BoneWingFireBallProjectileEntity> type,Level level) {
        super(type,level);pickup=Pickup.DISALLOWED;setNoGravity(true);
    }
    public void arm(BoneWingUnit source,LivingEntity target) {
        if(everArmed||spent||isRemoved()||!(level() instanceof ServerLevel server)||!server.getServer().isSameThread()
                ||source.level()!=level()||!source.canHarm(target))return;
        caster=source;casterId=source.getUUID();ownerAtLaunch=source.getOwnerName();ownerEpoch=source.rts().ownershipRevision;
        damage=Math.max(0,source.getUnitAttackDamage());
        airSuperiority=com.dreamseekers.unitprogression.ExpansionResearch.unlocked(source.getOwnerName(),
                com.dreamseekers.unitprogression.ExpansionResearch.AIR_SUPERIORITY_ID,false);setOwner(source);
        setPos(source.getX(),source.getEyeY(),source.getZ());
        var delta=target.getBoundingBox().getCenter().subtract(position());
        shoot(delta.x,delta.y,delta.z,BoneWingBalance.PROJECTILE_SPEED,0);
        setBaseDamage(0);setKnockback(0);setCritArrow(false);clearFire();armed=true;everArmed=true;
    }
    private boolean validCaster() {
        return armed&&!spent&&!isRemoved()&&level() instanceof ServerLevel level&&level.getServer().isSameThread()&&caster!=null&&caster.isAlive()&&!caster.isRemoved()
                &&caster.level()==level&&level.getEntity(casterId)==caster&&getOwner()==caster
                &&ownerAtLaunch.equals(caster.getOwnerName())&&ownerEpoch==caster.rts().ownershipRevision;
    }
    @Override protected boolean canHitEntity(Entity entity) {
        return validCaster()&&caster.canHarm(entity)&&super.canHitEntity(entity);
    }
    /** Only the exact physical impact may replace RoN's dynamic projectile base with its launch snapshot. */
    private static final java.util.ArrayDeque<Impact> IMPACTS=new java.util.ArrayDeque<>();
    private static final com.solegendary.reignofnether.api.FinalUnitDamageHooks.OrdinaryAttack DENIED=
        new com.solegendary.reignofnether.api.FinalUnitDamageHooks.OrdinaryAttack(){
            public float beforeArmor(float amount){return 0;}
            public com.solegendary.reignofnether.api.FinalUnitDamageHooks.Finalizer prepareFinal(){return amount->0;}
        };
    private static final class Impact {
        final BoneWingBolt projectile;final LivingEntity target;final net.minecraft.world.damagesource.DamageSource source;
        final com.dreamseekers.skill.SkillIdentity.Stamp caster,targetStamp;
        final UUID targetId;final float amount;boolean claimed;
        Impact(BoneWingBolt projectile,LivingEntity target,net.minecraft.world.damagesource.DamageSource source,float amount){
            this.projectile=projectile;this.target=target;this.source=source;this.amount=amount;targetId=target.getUUID();
            caster=com.dreamseekers.skill.SkillIdentity.capture(projectile.caster);
            targetStamp=target instanceof com.solegendary.reignofnether.unit.interfaces.Unit?com.dreamseekers.skill.SkillIdentity.capture(target):null;
        }
        boolean legal(){return IMPACTS.peek()==this&&caster.valid()&&target.isAlive()&&!target.isRemoved()
            &&target.level()==caster.level()&&caster.level().getEntity(targetId)==target&&caster.level().getEntity(target.getId())==target
            &&(targetStamp==null||targetStamp.valid())&&projectile.caster.canHarm(target)
            &&source.getEntity()==caster.entity()&&source.getDirectEntity()==projectile;}
    }
    public static com.solegendary.reignofnether.api.FinalUnitDamageHooks.OrdinaryAttack prepareOrdinaryAttack(
            LivingEntity target,net.minecraft.world.damagesource.DamageSource source){
        if(!(source.getDirectEntity() instanceof BoneWingBolt))return null;
        Impact hit=IMPACTS.peek();
        if(hit==null||hit.target!=target||hit.source!=source||hit.claimed||!hit.legal())return DENIED;
        hit.claimed=true;
        return new com.solegendary.reignofnether.api.FinalUnitDamageHooks.OrdinaryAttack(){
            @Override public float beforeArmor(float ignoredDynamicBase){return hit.legal()?hit.amount:0;}
            @Override public float validateAfterHurt(float amount){return hit.legal()?amount:0;}
            @Override public com.solegendary.reignofnether.api.FinalUnitDamageHooks.Finalizer prepareFinal(){
                // Keep ordinary coordinated mark, debt/corrosion receipts, defensive hooks and Gel.
                var delegate=com.dreamseekers.skill.SkillEffects.prepareFinalDamage(target,source);
                return new com.solegendary.reignofnether.api.FinalUnitDamageHooks.Finalizer(){
                    @Override public float resolve(float amount){
                        if(!hit.legal())return 0;
                        float value=delegate==null?amount:delegate.resolve(amount);
                        return hit.legal()?value:0;
                    }
                    @Override public void afterHealthDamage(float actual){if(delegate!=null)delegate.afterHealthDamage(actual);}
                };
            }
        };
    }
    @Override public void onHitEntity(EntityHitResult result) {
        if(!level().isClientSide()&&validCaster()&&caster.canHarm(result.getEntity())&&result.getEntity() instanceof LivingEntity target) {
            var primary=com.solegendary.reignofnether.api.PrimaryUnitHitHooks.capture(this,target);
            float amount=com.dreamseekers.unitprogression.ExpansionResearchMath.airDamage(damage,airSuperiority,
                    target instanceof com.solegendary.reignofnether.unit.interfaces.Unit unit&&unit.isFlyingUnit());
            var source=damageSources().arrow(this,caster);
            Impact hit=new Impact(this,target,source,amount);
            terminate(); // Consume before hurt: event callbacks cannot reuse this projectile.
            IMPACTS.push(hit);
            try {primary.hurt(source,amount);} finally {IMPACTS.removeFirstOccurrence(hit);}
        }
        terminate();
    }
    private void terminate() {armed=false;spent=true;discard();}
    @Override protected void onHitBlock(BlockHitResult result) {terminate();}
    @Override protected ItemStack getPickupItem() {return ItemStack.EMPTY;}
    @Override public void playerTouch(Player player) { }
    @Override public void tick() {
        if(!level().isClientSide()&&(!validCaster()||tickCount>=BoneWingBalance.PROJECTILE_TTL)) {terminate();return;}
        pickup=Pickup.DISALLOWED;clearFire();setNoGravity(true);super.tick();
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);armed=false;spent=true;everArmed=true;caster=null;casterId=null;ownerAtLaunch=null;
        pickup=Pickup.DISALLOWED;clearFire();setNoGravity(true);
    }
}
