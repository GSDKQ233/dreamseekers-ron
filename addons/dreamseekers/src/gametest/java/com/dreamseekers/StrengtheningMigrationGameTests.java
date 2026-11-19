// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.economy.DreamAscendancyAttributes;
import com.dreamseekers.skill.UnitSkill;
import com.dreamseekers.unit.*;
import com.dreamseekers.unitprogression.UnitBalanceMigration;
import com.dreamseekers.unitprogression.UnitBalanceMigration.Profile;
import com.dreamseekers.unitprogression.UnitBalanceMigration.Result;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Explicit minimal legacy-format fixtures derived from a current real save envelope and the
 * independently documented API50/51 base table. These are NOT archives produced by an API51 JVM,
 * and same-JVM load/save assertions do NOT replace the required separate-process restart suite.
 * Entities stay unpublished so account restoration order cannot accidentally gate a load migration.
 */
@GameTestHolder("dreamseekers_strengthening_migration")
@PrefixGameTestTemplate(false)
public final class StrengtheningMigrationGameTests {
    private static final String BATCH="dreamseekers_strengthening_migration";
    private static final UUID PERMANENT=UUID.fromString("8d2d7b68-ed24-4990-bfe8-019388994f52");
    private static Mob create(GameTestHelper h,Profile profile) {
        return switch(profile) {
            case MOTHER -> DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel());
            case LEFT -> DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(h.getLevel());
            case RIGHT -> DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel());
        };
    }
    private static Result result(Mob entity) {
        if(entity instanceof BeetleMotherUnit mother)return mother.balanceMigrationResult();
        if(entity instanceof AaroncosLeftHandUnit left)return left.balanceMigrationResult();
        return ((AaroncosRightHandUnit)entity).balanceMigrationResult();
    }
    private static Attribute[] changed() {
        return new Attribute[]{Attributes.MAX_HEALTH,Attributes.ATTACK_DAMAGE,AttributeRegistrar.ATTACK_DAMAGE.get(),Attributes.ARMOR};
    }
    private static double[] old(Profile profile) { return profile==Profile.MOTHER?new double[]{160,12,12,3}:new double[]{350,20,20,6}; }
    private static double[] current(Profile profile) { return switch(profile){case MOTHER->new double[]{200,14,14,4};case LEFT->new double[]{450,26,26,7};case RIGHT->new double[]{420,24,24,6};}; }
    private static void close(GameTestHelper h,double actual,double expected,String message) {
        h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.0001,message+" actual="+actual+" expected="+expected);
    }
    private static CompoundTag attribute(CompoundTag root,Attribute attribute) {
        String name=BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString();
        for(Tag tag:root.getList("Attributes",Tag.TAG_COMPOUND))if(tag instanceof CompoundTag entry&&name.equals(entry.getString("Name")))return entry;
        throw new AssertionError("Missing real saved attribute "+name);
    }
    private static CompoundTag legacy(GameTestHelper h,Profile profile,float health) {
        Mob seed=create(h,profile);((DreamRtsUnit)seed).setOwnerName("API52_Legacy_"+profile.name());
        seed.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        var claim=new CompoundTag();claim.putInt("version",1);claim.putUUID("origin",UUID.fromString("6acfaead-5aae-4f44-a45d-0c47d4da0525"));
        var redeemed=new CompoundTag();redeemed.putString("outcome","committed");claim.put("claim",redeemed);seed.getPersistentData().put(RtsKillValueClaims.KEY,claim);
        // Native AttributeMap.save serializes instantiated values only; materialize untouched registered defaults before the real save envelope.
        for(Attribute attribute:changed())h.assertTrue(seed.getAttribute(attribute)!=null,"registered attribute instance before actual native save");
        CompoundTag root=seed.saveWithoutId(new CompoundTag());seed.discard();root.remove(UnitBalanceMigration.KEY);
        Attribute[] attributes=changed();double[] bases=old(profile);
        for(int i=0;i<attributes.length;i++)attribute(root,attributes[i]).putDouble("Base",bases[i]);
        root.putFloat("Health",health);root.putShort("DeathTime",(short)0);
        // Explicitly retained old long absolute deadline, not a synthetic runtime lease.
        root.getCompound("DreamseekersUnitSkills").putLong(skill(profile).name(),h.getLevel().getGameTime()+100);
        return root;
    }
    private static UnitSkill skill(Profile profile) { return switch(profile){case MOTHER->UnitSkill.MOTHER_BROOD;case LEFT->UnitSkill.AARONCOS_LEFT_PRESS;case RIGHT->UnitSkill.AARONCOS_RIGHT_SHOCK;}; }
    private static Mob load(GameTestHelper h,Profile profile,CompoundTag root) { Mob entity=create(h,profile);entity.load(root);return entity; }
    private static void bases(GameTestHelper h,Mob entity,double[] expected,String why) {
        Attribute[] attributes=changed();for(int i=0;i<attributes.length;i++)close(h,entity.getAttribute(attributes[i]).getBaseValue(),expected[i],why+" base "+BuiltInRegistries.ATTRIBUTE.getKey(attributes[i]));
    }
    private static void modifier(CompoundTag root,Attribute attribute,UUID id,double amount,AttributeModifier.Operation operation) {
        CompoundTag saved=attribute(root,attribute);ListTag modifiers=saved.getList("Modifiers",Tag.TAG_COMPOUND);
        modifiers.add(new AttributeModifier(id,"explicit legacy permanent fixture",amount,operation).save());saved.put("Modifiers",modifiers);
    }
    private static void retainedModifier(GameTestHelper h,Mob entity,Attribute attribute,UUID id,double amount,AttributeModifier.Operation operation) {
        var modifier=entity.getAttribute(attribute).getModifier(id);h.assertTrue(modifier!=null&&modifier.getId().equals(id)&&modifier.getOperation()==operation,"exact permanent UUID and operation survive");close(h,modifier.getAmount(),amount,"permanent amount is not multiplied");
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_migration",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=60)
    public static void allThreeExplicitLegacyProfilesKeepHealthRatioCooldownOwnerUuidEquipmentAndClaim(GameTestHelper h) {
        for(Profile profile:Profile.values()) {
            float ratio=profile==Profile.RIGHT?.25f:.5f;CompoundTag root=legacy(h,profile,(float)(old(profile)[0]*ratio));
            Tag originalClaim=root.getCompound("ForgeData").get(RtsKillValueClaims.KEY).copy();UUID id=root.getUUID("UUID");String owner=root.getString("ownerName");
            Mob loaded=load(h,profile,root);bases(h,loaded,current(profile),"recognized old "+profile);close(h,loaded.getHealth(),current(profile)[0]*ratio,"preserved injured ratio");
            h.assertTrue(result(loaded)==Result.MIGRATED&&loaded.getUUID().equals(id)&&((DreamRtsUnit)loaded).getOwnerName().equals(owner),"successful migration retains exact object lineage");
            h.assertTrue(loaded.getMainHandItem().is(Items.IRON_SWORD)&&originalClaim.equals(loaded.getPersistentData().get(RtsKillValueClaims.KEY))&&RtsKillValueClaims.isClaimed(loaded),"equipment and already-redeemed economic lineage unchanged");
            h.assertTrue(((DreamRtsUnit)loaded).rts().skills().cooldownTicks(skill(profile))==100,"old absolute paid deadline neither reset nor refunded");
            CompoundTag saved=loaded.saveWithoutId(new CompoundTag());h.assertTrue(saved.getCompound(UnitBalanceMigration.KEY).getInt("version")==1&&saved.getCompound(UnitBalanceMigration.KEY).getString("profile").equals(profile.id()),"precise current profile marker only after success");loaded.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_migration",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=60)
    public static void migratedSaveLoadIsIdempotentAndFreshProfilesNeverReceiveLegacyMultiplier(GameTestHelper h) {
        for(Profile profile:Profile.values()) {
            Mob first=load(h,profile,legacy(h,profile,(float)(old(profile)[0]*.5)));CompoundTag once=first.saveWithoutId(new CompoundTag());float health=first.getHealth();first.discard();
            Mob second=load(h,profile,once);bases(h,second,current(profile),"second actual native load");close(h,second.getHealth(),health,"second actual load does not multiply injury twice");h.assertTrue(result(second)==Result.CURRENT,"current version is not migrated again");second.discard();
            Mob fresh=create(h,profile);bases(h,fresh,current(profile),"fresh registered attributes");close(h,fresh.getHealth(),current(profile)[0],"fresh full health");
            var freshSave=fresh.saveWithoutId(new CompoundTag());fresh.discard();Mob freshLoaded=load(h,profile,freshSave);close(h,freshLoaded.getHealth(),current(profile)[0],"fresh current save no legacy expansion");h.assertTrue(result(freshLoaded)==Result.CURRENT,"fresh schema saved");freshLoaded.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_migration",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=60)
    public static void permanentResearchAndExternalModifiersRemainSeparateFromChangedBases(GameTestHelper h) {
        CompoundTag research=legacy(h,Profile.MOTHER,92);modifier(research,Attributes.MAX_HEALTH,DreamAscendancyAttributes.HEALTH_ID,.15,AttributeModifier.Operation.MULTIPLY_TOTAL);modifier(research,AttributeRegistrar.ATTACK_DAMAGE.get(),DreamAscendancyAttributes.DAMAGE_ID,.15,AttributeModifier.Operation.MULTIPLY_TOTAL);
        Mob researched=load(h,Profile.MOTHER,research);bases(h,researched,current(Profile.MOTHER),"research not baked into base");close(h,researched.getMaxHealth(),230,"saved research max");close(h,researched.getHealth(),115,"saved research keeps half health");retainedModifier(h,researched,Attributes.MAX_HEALTH,DreamAscendancyAttributes.HEALTH_ID,.15,AttributeModifier.Operation.MULTIPLY_TOTAL);retainedModifier(h,researched,AttributeRegistrar.ATTACK_DAMAGE.get(),DreamAscendancyAttributes.DAMAGE_ID,.15,AttributeModifier.Operation.MULTIPLY_TOTAL);
        DreamAscendancyAttributes.reconcile(researched,false);close(h,researched.getMaxHealth(),200,"research provider removes only own UUID");close(h,researched.getHealth(),100,"provider removal preserves same ratio");DreamAscendancyAttributes.reconcile(researched,true);close(h,researched.getMaxHealth(),230,"provider readds once");close(h,researched.getHealth(),115,"provider readd preserves same ratio");researched.discard();
        CompoundTag external=legacy(h,Profile.MOTHER,90);modifier(external,Attributes.MAX_HEALTH,PERMANENT,20,AttributeModifier.Operation.ADDITION);modifier(external,Attributes.ATTACK_DAMAGE,PERMANENT,2,AttributeModifier.Operation.ADDITION);modifier(external,Attributes.ARMOR,PERMANENT,1,AttributeModifier.Operation.ADDITION);
        Mob customModifier=load(h,Profile.MOTHER,external);bases(h,customModifier,current(Profile.MOTHER),"external addition not baked into changed base");close(h,customModifier.getMaxHealth(),220,"external plus twenty preserved");close(h,customModifier.getHealth(),110,"old ninety of180 becomes110 of220");for(Attribute attribute:List.of(Attributes.MAX_HEALTH,Attributes.ATTACK_DAMAGE,Attributes.ARMOR))retainedModifier(h,customModifier,attribute,PERMANENT,attribute==Attributes.MAX_HEALTH?20:attribute==Attributes.ATTACK_DAMAGE?2:1,AttributeModifier.Operation.ADDITION);customModifier.discard();h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_migration",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=60)
    public static void oneCustomizedBaseRejectsEntireStandardConversionWithoutBlessingVersion(GameTestHelper h) {
        for(Attribute custom:List.of(Attributes.MAX_HEALTH,Attributes.ATTACK_DAMAGE,AttributeRegistrar.ATTACK_DAMAGE.get(),Attributes.ARMOR)) {
            CompoundTag root=legacy(h,Profile.MOTHER,80);attribute(root,custom).putDouble("Base",custom==Attributes.MAX_HEALTH?500:31);
            Mob loaded=load(h,Profile.MOTHER,root);h.assertTrue(result(loaded)==Result.CUSTOM_OR_UNRECOGNIZED,"explicit custom changed base rejects whole profile");
            double[] expected=old(Profile.MOTHER);Attribute[] changed=changed();for(int i=0;i<changed.length;i++)if(changed[i]==custom)expected[i]=custom==Attributes.MAX_HEALTH?500:31;
            bases(h,loaded,expected,"zero partial base conversion");close(h,loaded.getHealth(),80,"no custom health rescaling");h.assertTrue(!loaded.saveWithoutId(new CompoundTag()).contains(UnitBalanceMigration.KEY),"rejected custom legacy stays unversioned on save");loaded.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_migration",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=60)
    public static void futureWrongProfileAndMalformedSchemaPreserveTheirExactUnknownTag(GameTestHelper h) {
        var future=new CompoundTag();future.putInt("version",77);future.putString("profile","mother_v77");future.putLong("UnknownFutureField",891234567L);
        var wrong=new CompoundTag();wrong.putInt("version",1);wrong.putString("profile","aaroncos_right_v1");wrong.putString("DoNotErase","payload");
        for(Tag schema:List.of(future,wrong,StringTag.valueOf("opaque legacy vendor extension"))) {
            CompoundTag root=legacy(h,Profile.MOTHER,80);root.put(UnitBalanceMigration.KEY,schema.copy());Mob loaded=load(h,Profile.MOTHER,root);
            h.assertTrue(result(loaded)==Result.UNSUPPORTED_SCHEMA,"future/wrong/malformed schema not treated as absent");bases(h,loaded,old(Profile.MOTHER),"unsupported means zero migration");close(h,loaded.getHealth(),80,"unsupported injured health untouched");h.assertTrue(schema.equals(loaded.saveWithoutId(new CompoundTag()).get(UnitBalanceMigration.KEY)),"entire unknown original Tag survives save");loaded.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_migration",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=60)
    public static void missingRtsEnvelopeAndWrongRegisteredIdNeverAuthorizeLegacyMigration(GameTestHelper h) {
        for(int mode=0;mode<5;mode++) {
            CompoundTag root=legacy(h,Profile.MOTHER,80);
            switch(mode){case 0->root.remove("ownerName");case 1->root.putString("ownerName","");case 2->root.remove("DreamseekersUnitSkills");case 3->root.getCompound("DreamseekersUnitSkills").putInt("Revision",0);case 4->root.putString("id","pasterdream:black_beetle_mother");}
            Mob loaded=load(h,Profile.MOTHER,root);h.assertTrue(result(loaded)==Result.INELIGIBLE_IDENTITY,"missing released RTS identity envelope denied case "+mode);bases(h,loaded,old(Profile.MOTHER),"identity denial preserves old profile");h.assertTrue(!loaded.saveWithoutId(new CompoundTag()).contains(UnitBalanceMigration.KEY),"no success marker on identity denial");loaded.discard();
        }
        var hand=DreamseekersMod.SHADOW_HAND_UNIT.get().create(h.getLevel());var root=legacy(h,Profile.MOTHER,80);UnitBalanceMigration.State state=UnitBalanceMigration.afterRtsLoad(hand,root,Profile.MOTHER);h.assertTrue(state.result()==Result.INELIGIBLE_IDENTITY,"ordinary shadow hand cannot borrow mother profile");close(h,hand.getMaxHealth(),55,"ordinary shadow hand remains55");hand.discard();h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_migration",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=60)
    public static void missingHealthAnchorDuplicateAttributeAndMalformedNumbersFailClosed(GameTestHelper h) {
        for(int mode=0;mode<7;mode++) {
            CompoundTag root=legacy(h,Profile.MOTHER,80);CompoundTag hp=attribute(root,Attributes.MAX_HEALTH);
            switch(mode){case 0->root.remove("Attributes");case 1->hp.remove("Base");case 2->hp.putString("Base","160");case 3->hp.putDouble("Base",Double.NaN);case 4->hp.putDouble("Base",Double.POSITIVE_INFINITY);case 5->root.getList("Attributes",Tag.TAG_COMPOUND).add(hp.copy());case 6->root.remove("Health");}
            Mob loaded=load(h,Profile.MOTHER,root);h.assertTrue(result(loaded)==Result.MALFORMED,"malformed input never defaults missing numbers into a profile case "+mode);h.assertTrue(!loaded.saveWithoutId(new CompoundTag()).contains(UnitBalanceMigration.KEY),"malformed input not blessed as current");loaded.discard();
        }
        CompoundTag missing=legacy(h,Profile.MOTHER,80);String hpName=BuiltInRegistries.ATTRIBUTE.getKey(Attributes.MAX_HEALTH).toString();ListTag attributes=missing.getList("Attributes",Tag.TAG_COMPOUND);attributes.removeIf(t->t instanceof CompoundTag a&&a.getString("Name").equals(hpName));Mob loaded=load(h,Profile.MOTHER,missing);h.assertTrue(result(loaded)==Result.CUSTOM_OR_UNRECOGNIZED,"no explicit old HP means no historical anchor");h.assertTrue(!loaded.saveWithoutId(new CompoundTag()).contains(UnitBalanceMigration.KEY),"missing historical anchor not marked migrated");loaded.discard();h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_migration",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=60)
    public static void deathTimeMustBeExplicitNonnegativeShortBeforeAnyLegacyBalanceWrite(GameTestHelper h) {
        // Actual native load, not direct migration invocation: getShort narrows these malformed
        // inputs before the addon runs. None may be treated as an old live standard archive.
        List<Tag> malformed=List.of(IntTag.valueOf(32768),DoubleTag.valueOf(.5),IntTag.valueOf(0),
                IntTag.valueOf(1),LongTag.valueOf(Long.MAX_VALUE),FloatTag.valueOf(0),DoubleTag.valueOf(0),
                ByteTag.valueOf((byte)0),ShortTag.valueOf((short)-1),StringTag.valueOf("0"));
        for(Profile profile:Profile.values())for(int mode=0;mode<=malformed.size();mode++) {
            float oldHealth=(float)(old(profile)[0]*.5);CompoundTag root=legacy(h,profile,oldHealth);
            if(mode==malformed.size())root.remove("DeathTime");
            else root.put("DeathTime",malformed.get(mode).copy());
            Tag original=root.get("DeathTime");short nativeDeathTime=root.getShort("DeathTime");
            Mob loaded=load(h,profile,root);
            h.assertTrue(result(loaded)==Result.MALFORMED,"non-issued DeathTime cannot authenticate migration: "+profile+" case "+mode);
            bases(h,loaded,old(profile),"malformed DeathTime leaves all four loaded Bases unchanged");
            close(h,loaded.getHealth(),oldHealth,"malformed DeathTime neither ratio-heals nor zeroes loaded HP");
            h.assertTrue(loaded.deathTime==nativeDeathTime,"addon does not normalize the superclass's narrowed death timer");
            h.assertTrue(Objects.equals(root.get("DeathTime"),original),"input DeathTime tag is not rewritten by migration");
            h.assertTrue(!loaded.saveWithoutId(new CompoundTag()).contains(UnitBalanceMigration.KEY),"malformed or absent DeathTime never gets a successful balance marker");
            loaded.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_migration",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=60)
    public static void missingOtherChangedAttributesUseRegisteredDefaultsButNeverGuessMissingHealth(GameTestHelper h) {
        CompoundTag root=legacy(h,Profile.MOTHER,80);String hpName=BuiltInRegistries.ATTRIBUTE.getKey(Attributes.MAX_HEALTH).toString();Set<String> changedNames=new HashSet<>();for(Attribute attribute:changed())changedNames.add(BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString());
        root.getList("Attributes",Tag.TAG_COMPOUND).removeIf(t->t instanceof CompoundTag a&&changedNames.contains(a.getString("Name"))&&!hpName.equals(a.getString("Name")));
        Mob loaded=load(h,Profile.MOTHER,root);h.assertTrue(result(loaded)==Result.MIGRATED,"explicit known old HP and no contradictory saved bases may migrate");bases(h,loaded,current(Profile.MOTHER),"missing AD/armor use current registered defaults");close(h,loaded.getHealth(),100,"old HP anchor still preserves ratio");loaded.discard();h.succeed();
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_migration",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=60)
    public static void zeroDeathTimeAndTinyInjuriesNeverBecomeUnrequestedFullHealing(GameTestHelper h) {
        for(float health:new float[]{0,160,.0001f}) {
            Mob loaded=load(h,Profile.MOTHER,legacy(h,Profile.MOTHER,health));close(h,loaded.getHealth(),health*1.25,"exact zero/full/tiny ratio");h.assertTrue(result(loaded)==Result.MIGRATED,"valid standard archive still records numeric migration");loaded.discard();
        }
        CompoundTag dying=legacy(h,Profile.MOTHER,80);dying.putShort("DeathTime",(short)4);Mob loaded=load(h,Profile.MOTHER,dying);close(h,loaded.getHealth(),0,"positive deathTime cannot resurrect inconsistent saved health");loaded.discard();h.succeed();
    }
}
