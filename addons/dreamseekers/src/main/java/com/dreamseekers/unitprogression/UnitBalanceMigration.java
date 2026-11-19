// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unitprogression;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.aaroncos.AaroncosBalance;
import com.dreamseekers.beetlemother.BeetleMotherBalance;
import com.dreamseekers.economy.DreamResearchMath;
import com.dreamseekers.unit.AaroncosLeftHandUnit;
import com.dreamseekers.unit.AaroncosRightHandUnit;
import com.dreamseekers.unit.BeetleMotherUnit;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** A one-time conversion of the three released RTS base profiles, not a general attribute fixer.
 * The saved Bases identify the old profile; the loaded effective maximum preserves HP percentage.
 * No resource account, research flag, economic origin, permanent modifier or cooldown is rewritten.
 */
public final class UnitBalanceMigration {
    public static final String KEY="DreamseekersBalance";
    public static final int VERSION=1;
    private UnitBalanceMigration() { }

    public enum Profile {
        MOTHER("mother_v1","MOTHER_BROOD",160,12,3,
                BeetleMotherBalance.MAX_HEALTH,BeetleMotherBalance.DAMAGE,BeetleMotherBalance.ARMOR),
        LEFT("aaroncos_left_v1","AARONCOS_LEFT_PRESS",350,20,6,
                AaroncosBalance.LEFT_MAX_HEALTH,AaroncosBalance.LEFT_DAMAGE,AaroncosBalance.LEFT_ARMOR),
        RIGHT("aaroncos_right_v1","AARONCOS_RIGHT_SHOCK",350,20,6,
                AaroncosBalance.RIGHT_MAX_HEALTH,AaroncosBalance.RIGHT_DAMAGE,AaroncosBalance.RIGHT_ARMOR);
        private final String id,skill;
        private final double oldHealth,oldDamage,oldArmor,newHealth,newDamage,newArmor;
        Profile(String id,String skill,double oldHealth,double oldDamage,double oldArmor,
                double newHealth,double newDamage,double newArmor) {
            this.id=id;this.skill=skill;this.oldHealth=oldHealth;this.oldDamage=oldDamage;this.oldArmor=oldArmor;
            this.newHealth=newHealth;this.newDamage=newDamage;this.newArmor=newArmor;
        }
        public String id() { return id; }
        private boolean matches(LivingEntity entity) {
            return switch(this) {
                case MOTHER -> entity.getClass()==BeetleMotherUnit.class&&entity.getType()==DreamseekersMod.BEETLE_MOTHER_UNIT.get();
                case LEFT -> entity.getClass()==AaroncosLeftHandUnit.class&&entity.getType()==DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get();
                case RIGHT -> entity.getClass()==AaroncosRightHandUnit.class&&entity.getType()==DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get();
            };
        }
        private double[] oldBases() { return new double[]{oldHealth,oldDamage,oldDamage,oldArmor}; }
        private double[] newBases() { return new double[]{newHealth,newDamage,newDamage,newArmor}; }
    }

    public enum Result { FRESH, CURRENT, MIGRATED, CUSTOM_OR_UNRECOGNIZED, MALFORMED, UNSUPPORTED_SCHEMA, INELIGIBLE_IDENTITY }

    /** Retains the exact original schema tag, even a future compound or malformed non-compound.
     * An absent rejected legacy schema stays absent: saving must not bless it as version 1.
     */
    public static final class State {
        private final Tag schema;
        private final Result result;
        private State(Tag schema,Result result) { this.schema=schema==null?null:schema.copy();this.result=result; }
        public Result result() { return result; }
        public void writeTo(CompoundTag root) {
            if(schema==null)root.remove(KEY);
            else root.put(KEY,schema.copy());
        }
    }

    public static State fresh(Profile profile) { return current(profile,Result.FRESH); }
    private static State current(Profile profile,Result result) {
        CompoundTag schema=new CompoundTag();schema.putInt("version",VERSION);schema.putString("profile",profile.id);
        return new State(schema,result);
    }
    private static State preserved(CompoundTag root,Result result) { return new State(root.get(KEY),result); }
    private static Attribute[] changedAttributes() {
        return new Attribute[]{Attributes.MAX_HEALTH,Attributes.ATTACK_DAMAGE,AttributeRegistrar.ATTACK_DAMAGE.get(),Attributes.ARMOR};
    }

    /** Call after native attribute/health load and readRtsData, exactly once per actual NBT read.
     * The only historical table is the released unversioned API50/51 profile. No version 0 was
     * issued; explicit zero/future/wrong-profile markers are not an invitation to guess a version.
     */
    public static State afterRtsLoad(LivingEntity entity,CompoundTag root,Profile profile) {
        if(entity==null||root==null||profile==null)throw new IllegalArgumentException("Missing balance load context");
        if(entity.level().isClientSide()||!profile.matches(entity))return preserved(root,Result.INELIGIBLE_IDENTITY);
        if(root.contains(KEY)) {
            Tag raw=root.get(KEY);
            if(!(raw instanceof CompoundTag schema)||!schema.contains("version",Tag.TAG_INT)
                    ||!schema.contains("profile",Tag.TAG_STRING))return preserved(root,Result.UNSUPPORTED_SCHEMA);
            if(schema.getInt("version")!=VERSION||!profile.id.equals(schema.getString("profile")))
                return preserved(root,Result.UNSUPPORTED_SCHEMA);
            return preserved(root,Result.CURRENT);
        }
        // An authentic saved RTS envelope, rather than merely a native parent with matching HP.
        if(!root.contains("ownerName",Tag.TAG_STRING)||root.getString("ownerName").isBlank()
                ||!(root.get("DreamseekersUnitSkills") instanceof CompoundTag skills)
                ||!skills.contains("Revision",Tag.TAG_LONG)||skills.getLong("Revision")<0
                ||!skills.contains(profile.skill,Tag.TAG_LONG)||skills.getLong(profile.skill)<0)
            return preserved(root,Result.INELIGIBLE_IDENTITY);
        if(root.contains("id")&&(!root.contains("id",Tag.TAG_STRING)
                ||!BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(root.getString("id"))))
            return preserved(root,Result.INELIGIBLE_IDENTITY);
        if(!(root.get("Attributes") instanceof ListTag saved)||saved.getElementType()!=Tag.TAG_COMPOUND
                ||!(root.get("Health") instanceof NumericTag healthTag)||!Double.isFinite(healthTag.getAsDouble())
                ||!Float.isFinite(healthTag.getAsFloat()))return preserved(root,Result.MALFORMED);
        // Released LivingEntity saves always write an explicit ShortTag. getShort() in the
        // superclass narrows other numeric kinds (e.g. Int32768 -> -32768, Double.5 -> 0);
        // that lossy loaded value must never authenticate an old live profile for migration.
        // Missing, negative or non-short data is unknown: leave every loaded attribute/HP alone.
        if(!(root.get("DeathTime") instanceof ShortTag death)||death.getAsShort()<0)
            return preserved(root,Result.MALFORMED);

        Attribute[] attributes=changedAttributes();
        Map<String,Integer> changed=new HashMap<>();
        for(int i=0;i<attributes.length;i++)changed.put(BuiltInRegistries.ATTRIBUTE.getKey(attributes[i]).toString(),i);
        double[] oldBases=profile.oldBases(),newBases=profile.newBases();
        boolean[] present=new boolean[attributes.length];
        HashSet<String> names=new HashSet<>();
        for(int i=0;i<saved.size();i++) {
            CompoundTag attribute=saved.getCompound(i);
            if(!attribute.contains("Name",Tag.TAG_STRING)||!names.add(attribute.getString("Name")))
                return preserved(root,Result.MALFORMED);
            Integer index=changed.get(attribute.getString("Name"));
            if(index==null)continue;
            if(!(attribute.get("Base") instanceof NumericTag base)||!Double.isFinite(base.getAsDouble()))
                return preserved(root,Result.MALFORMED);
            if(Double.compare(base.getAsDouble(),oldBases[index])!=0)return preserved(root,Result.CUSTOM_OR_UNRECOGNIZED);
            present[index]=true;
        }
        // HP is the mandatory historical anchor. Missing other changed attributes may use the
        // new registration default, but any explicitly saved custom Base rejects the whole group.
        if(!present[0])return preserved(root,Result.CUSTOM_OR_UNRECOGNIZED);
        AttributeInstance[] instances=new AttributeInstance[attributes.length];
        double oldMax=entity.getMaxHealth();float oldHealth=entity.getHealth();
        if(!Double.isFinite(oldMax)||oldMax<=0||!Float.isFinite(oldHealth))return preserved(root,Result.MALFORMED);
        double newMax=0;
        for(int i=0;i<attributes.length;i++) {
            AttributeInstance live=entity.getAttribute(attributes[i]);instances[i]=live;
            if(live==null||!Double.isFinite(live.getBaseValue())
                    ||Double.compare(live.getBaseValue(),present[i]?oldBases[i]:newBases[i])!=0)
                return preserved(root,Result.CUSTOM_OR_UNRECOGNIZED);
            if(live.getModifiers().stream().anyMatch(modifier->!Double.isFinite(modifier.getAmount())))
                return preserved(root,Result.MALFORMED);
            // Preflight against the exact modifier set without touching the live entity. This also
            // avoids partially applying the four bases when external modifier data is non-finite.
            AttributeInstance preview=new AttributeInstance(attributes[i],ignored->{});
            preview.replaceFrom(live);preview.setBaseValue(newBases[i]);
            double value=preview.getValue();
            if(!Double.isFinite(value)||(i==0&&value<=0))return preserved(root,Result.MALFORMED);
            if(i==0)newMax=(float)value; // LivingEntity.getMaxHealth exposes the effective maximum as float.
        }
        if(!Double.isFinite(newMax)||newMax<=0)return preserved(root,Result.MALFORMED);
        float nextHealth=DreamResearchMath.healthAfter(oldHealth,oldMax,newMax);
        // A dead archive never becomes a living one, even if its Health field is inconsistent.
        if(entity.deathTime>0)nextHealth=0;
        for(int i=0;i<instances.length;i++)if(Double.compare(instances[i].getBaseValue(),newBases[i])!=0)
            instances[i].setBaseValue(newBases[i]);
        entity.setHealth(nextHealth);
        return current(profile,Result.MIGRATED);
    }
}
