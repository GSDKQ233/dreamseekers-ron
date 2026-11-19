// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.spore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.pasterdream.entity.SporeEntityEntity;
/** Non-Unit carrier: only an in-memory authenticated birth lease authorizes server lifetime.
 * Synced display fields and saved tags never authorize an instance or replay its activation.
 */
public final class JungleSporeCarrier extends SporeEntityEntity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> LEASE_ACTIVE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(JungleSporeCarrier.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> LEASE_OWNER =
            net.minecraft.network.syncher.SynchedEntityData.defineId(JungleSporeCarrier.class,net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private boolean constructionComplete;
    private boolean authorityTerminated;
    private boolean everAuthorized;
    public boolean wasAuthorityTerminated() { return authorityTerminated; }
    public boolean wasLeaseInitialized() { return everAuthorized; }
    @SuppressWarnings("unchecked")
    public JungleSporeCarrier(EntityType<? extends SporeEntityEntity> type,Level level) {
        super((EntityType<SporeEntityEntity>)type,level);
        constructionComplete=true;setNoAi(true);setCanPickUpLoot(false);setNoGravity(true);xpReward=0;
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();entityData.define(LEASE_ACTIVE,false);entityData.define(LEASE_OWNER,"");
    }
    void setLeaseDisplay(boolean active,String owner) { entityData.set(LEASE_ACTIVE,active);entityData.set(LEASE_OWNER,owner); }
    /** Only SporeLease's fresh, exact, unpublished pending object may receive its initial 10 HP.
     * This is neither a healing API nor an NBT authority path; the tombstone never resets on lease loss.
     */
    boolean initializeBirthHealth() {
        if(everAuthorized||!SporeLease.pendingBirth(this))return false;
        var health=getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if(health==null)return false;
        everAuthorized=true;
        health.setBaseValue(SporeLease.HEALTH);super.setHealth(SporeLease.HEALTH);
        return getHealth()==SporeLease.HEALTH&&getMaxHealth()==SporeLease.HEALTH;
    }
    /** Presentation only. All server permission checks use SporeLease, never these fields. */
    public boolean displayedActive() { return entityData.get(LEASE_ACTIVE); }
    public String displayedOwner() { return entityData.get(LEASE_OWNER); }
    @Override protected void registerGoals() { }
    @Override public void setTarget(LivingEntity ignored) { super.setTarget(null); }
    @Override public LivingEntity getTarget() { return null; }
    @Override public boolean doHurtTarget(Entity ignored) { return false; }
    @Override public boolean canBeLeashed(Player ignored) { return false; }
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand) { return InteractionResult.PASS; }
    @Override protected void pickUpItem(ItemEntity ignored) { }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override protected void dropCustomDeathLoot(DamageSource source,int looting,boolean recentlyHit) { }
    @Override public int getExperienceReward() { return 0; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    /** An unavailable carrier has no valid beneficiary, including native direct-setHealth healing. */
    @Override public void setHealth(float health) {
        if(!Float.isFinite(health))health=0;
        if(constructionComplete&&health>getHealth())return;
        super.setHealth(health);
    }
    @Override public boolean canBeAffected(MobEffectInstance effect) { return false; }
    /** Raw 1 HP behavior stays native. A lease-initialized body never regains that fallback. */
    @Override public boolean hurt(DamageSource source,float amount) {
        if(!everAuthorized)return super.hurt(source,amount);
        return SporeCombat.enter(this,source,amount)&&super.hurt(source,amount);
    }
    @Override public void tick() {
        setCanPickUpLoot(false);setDeltaMovement(Vec3.ZERO);
        if(!level().isClientSide()&&!isDeadOrDying()&&!SporeLease.authorizedToExist(this)) {
            authorityTerminated=true;discard();return;
        }
        super.tick();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.putInt("DreamSporeSchema",2);tag.putBoolean("DreamSporeDisabled",true);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        // LivingEntity loads effects directly into its map, bypassing canBeAffected.
        // Sanitize a copy: callers retain their original tag, and no saved payload grants authority.
        SporeLease.clear(this);setLeaseDisplay(false,"");
        CompoundTag safe=tag.copy();
        // An authorized save contains a 10 HP base; that data is not authority for a loaded object.
        String healthId=net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES
                .getKey(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).toString();
        safe.getList("Attributes",net.minecraft.nbt.Tag.TAG_COMPOUND)
                .removeIf(entry->entry instanceof CompoundTag attribute&&attribute.getString("Name").equals(healthId));
        var health=getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if(health!=null)health.setBaseValue(1);
        if(safe.getFloat("Health")>1)safe.putFloat("Health",1);
        for(String key:new String[]{"ActiveEffects","AbsorptionAmount","HandItems","ArmorItems",
                "HandDropChances","ArmorDropChances","DeathLootTable","DeathLootTableSeed","Leash"})safe.remove(key);
        super.readAdditionalSaveData(safe);setAbsorptionAmount(0);setNoAi(true);setCanPickUpLoot(false);setNoGravity(true);xpReward=0;
        setTarget(null);setDeltaMovement(Vec3.ZERO); // No NBT value can grant an activation or reset a lifetime.
    }
}
