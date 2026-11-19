package com.solegendary.reignofnether.ability;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.NecromancerUnit;
import com.solegendary.reignofnether.unit.units.monsters.WraithUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.*;

@GameTestHolder("api63_native_abilities") @PrefixGameTestTemplate(false)
public final class NativeAbilitySaveGameTests {
    static final String KEY = "RoNUnitAbilities";
    private static WraithUnit wraith(GameTestHelper h) {
        var unit=EntityRegistrar.WRAITH_UNIT.get().create(h.getLevel());
        unit.setOwnerName("Api63Abilities");unit.setNoAi(true);unit.setPersistenceRequired();
        unit.moveTo(h.absolutePos(new BlockPos(4,4,4)).getX()+.5,h.absolutePos(new BlockPos(4,4,4)).getY(),h.absolutePos(new BlockPos(4,4,4)).getZ()+.5);
        return unit;
    }
    static Ability first(Unit unit){return unit.getAbilities().get().get(0);}
    static HeroAbility curse(NecromancerUnit unit){return unit.getHeroAbilities().stream().filter(a->a.action.name().equals("INSOMNIA_CURSE")).findFirst().orElseThrow();}
    static void eq(GameTestHelper h,float actual,float expected,String why){h.assertTrue(Math.abs(actual-expected)<.001,why+" actual="+actual+" expected="+expected);}
    @GameTest(templateNamespace="api63_native_abilities",template="ore_mine_test_arena",batch="native_abilities",timeoutTicks=100)
    public static void cooldownAndExplicitAutocastOffSurviveRealReload(GameTestHelper h){
        var original=wraith(h);WraithUnit restored=null;
        try{
            h.assertTrue(h.getLevel().addFreshEntity(original),"original body published");
            var ability=first(original);float remaining=ability.cooldownMax/2+.25f;original.setCooldown(ability,remaining);original.setAutocast(null);
            var tag=original.saveWithoutId(new CompoundTag());original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            restored=wraith(h);restored.load(tag);h.assertTrue(h.getLevel().addFreshEntity(restored),"same UUID reloaded through native join");
            eq(h,restored.getCooldown(first(restored)),remaining,"remaining cooldown is not reset");
            h.assertTrue(!restored.hasAutocast(first(restored)),"manual autocast off overrides constructor default");
            Unit.tick(restored);eq(h,restored.getCooldown(first(restored)),Math.max(0,remaining-1),"normal tick resumes remaining counter");
        }finally{if(restored!=null)restored.discard();if(!original.isRemoved())original.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api63_native_abilities",template="ore_mine_test_arena",batch="native_abilities",timeoutTicks=100)
    public static void enabledAutocastPersistsAndLegacyDefaultsRemain(GameTestHelper h){
        var original=wraith(h);var copy=wraith(h);var legacy=wraith(h);
        try{
            original.setAutocast(first(original));var tag=original.saveWithoutId(new CompoundTag());copy.setAutocast(null);copy.load(tag);
            h.assertTrue(copy.hasAutocast(first(copy)),"explicit enabled selection restored");
            boolean defaultOn=legacy.hasAutocast(first(legacy));tag.remove(KEY);legacy.load(tag);
            h.assertTrue(legacy.hasAutocast(first(legacy))==defaultOn&&legacy.getCooldown(first(legacy))==0,"absent old block keeps native defaults");
        }finally{original.discard();copy.discard();legacy.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api63_native_abilities",template="ore_mine_test_arena",batch="native_abilities",timeoutTicks=100)
    public static void heroRanksAndConsumedChargesRestoreBeforeNativeJoin(GameTestHelper h){
        var original=EntityRegistrar.NECROMANCER_UNIT.get().create(h.getLevel());var copy=EntityRegistrar.NECROMANCER_UNIT.get().create(h.getLevel());
        try{
            original.setOwnerName("Api63Hero");original.setNoAi(true);var ability=curse(original);original.setHeroAbilityRank(ability,3);ability.updateStatsForRank(original);
            h.assertTrue(ability.maxCharges==5,"rank-three fixture");original.setCharges(ability,1);original.setCooldown(ability,137.5f);original.setAutocast(ability);
            var tag=original.saveWithoutId(new CompoundTag());copy.load(tag);copy.setNoAi(true);
            h.assertTrue(h.getLevel().addFreshEntity(copy),"restored hero enters actual server upgrade/join path");var restored=curse(copy);
            h.assertTrue(copy.getHeroAbilityRank(restored)==3&&restored.maxCharges==5&&copy.getAbilityCharges(restored)==1,"rank restored before consumed charge bounds");
            eq(h,copy.getCooldown(restored),137.5f,"hero cooldown restored");h.assertTrue(copy.hasAutocast(restored),"one selected ability restored once");
        }finally{original.discard();copy.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api63_native_abilities",template="ore_mine_test_arena",batch="native_abilities",timeoutTicks=100)
    public static void malformedKnownValuesAreBoundedByRestoredRank(GameTestHelper h){
        var unit=EntityRegistrar.NECROMANCER_UNIT.get().create(h.getLevel());
        try{
            var ability=curse(unit);unit.setHeroAbilityRank(ability,1);ability.updateStatsForRank(unit);var tag=unit.saveWithoutId(new CompoundTag());
            var state=new CompoundTag();state.putInt("Version",1);var entries=new CompoundTag();var known=new CompoundTag();known.putFloat("Cooldown",Float.NaN);known.putInt("Charges",Integer.MAX_VALUE);entries.put(ability.action.name(),known);state.put("Entries",entries);state.putString("Autocast","");tag.put(KEY,state);unit.load(tag);
            ability=curse(unit);eq(h,unit.getCooldown(ability),ability.cooldownMax,"NaN fails closed to finite full cooldown");h.assertTrue(unit.getAbilityCharges(ability)==3,"charges bounded to restored rank one");
            known.putFloat("Cooldown",-80);known.putInt("Charges",-4);unit.load(tag);eq(h,unit.getCooldown(curse(unit)),0,"negative cooldown bounded");h.assertTrue(unit.getAbilityCharges(curse(unit))==0,"negative charges bounded");
        }finally{unit.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api63_native_abilities",template="ore_mine_test_arena",batch="native_abilities",timeoutTicks=100)
    public static void futureSchemaAndUnknownEntriesRoundTripWithoutInterpretation(GameTestHelper h){
        var unit=wraith(h);
        try{
            var tag=unit.saveWithoutId(new CompoundTag());var future=new CompoundTag();future.putInt("Version",99);future.putString("FutureField","opaque");tag.put(KEY,future);unit.load(tag);
            h.assertTrue(unit.saveWithoutId(new CompoundTag()).getCompound(KEY).equals(future),"future payload preserved exactly");
            var current=new CompoundTag();current.putInt("Version",1);current.putString("FutureField","retain");current.putString("Autocast","FUTURE_ACTION");var entries=new CompoundTag();var unknown=new CompoundTag();unknown.putString("payload","retain too");entries.put("FUTURE_ACTION",unknown);current.put("Entries",entries);tag.put(KEY,current);unit.load(tag);
            var saved=unit.saveWithoutId(new CompoundTag()).getCompound(KEY);h.assertTrue(saved.getString("FutureField").equals("retain")&&saved.getString("Autocast").equals("FUTURE_ACTION")&&saved.getCompound("Entries").getCompound("FUTURE_ACTION").equals(unknown),"unknown fields/action/selection remain opaque");
            first(unit).setAutocast(true,unit);
            var enabled=unit.saveWithoutId(new CompoundTag());h.assertTrue(enabled.getCompound(KEY).getString("Autocast").equals(first(unit).action.name()),"explicit public known choice supersedes unknown selection in current schema");
            unit.setAutocast(null);unit.load(enabled);h.assertTrue(unit.hasAutocast(first(unit)),"explicit known on reloads");
            // Off must override even if the current effective selection was already off.
            tag.put(KEY,current);unit.load(tag);unit.setAutocast(null);first(unit).setAutocast(false,unit);
            var disabled=unit.saveWithoutId(new CompoundTag());h.assertTrue(disabled.getCompound(KEY).getString("Autocast").isEmpty(),"explicit off supersedes opaque current selection");
            unit.setAutocast(first(unit));unit.load(disabled);h.assertTrue(!unit.hasAutocast(first(unit)),"explicit off reloads");
            tag.put(KEY,future);unit.load(tag);first(unit).setAutocast(false,unit);h.assertTrue(unit.saveWithoutId(new CompoundTag()).getCompound(KEY).equals(future),"explicit toggle never rewrites an unrecognised future schema");
        }finally{unit.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api63_native_abilities",template="ore_mine_test_arena",batch="native_abilities",timeoutTicks=100)
    public static void opaqueStateUsesBodyIdentityNotRecycledNumericId(GameTestHelper h){
        var first=wraith(h);var second=wraith(h);
        try{
            second.setId(first.getId());
            var firstTag=first.saveWithoutId(new CompoundTag());var secondTag=second.saveWithoutId(new CompoundTag());
            var a=new CompoundTag();a.putInt("Version",99);a.putString("Owner","first");var b=new CompoundTag();b.putInt("Version",99);b.putString("Owner","second");
            firstTag.put(KEY,a);secondTag.put(KEY,b);first.load(firstTag);second.load(secondTag);
            h.assertTrue(first.saveWithoutId(new CompoundTag()).getCompound(KEY).equals(a)&&second.saveWithoutId(new CompoundTag()).getCompound(KEY).equals(b),"equal runtime IDs never exchange preserved future payloads");
        }finally{first.discard();second.discard();}h.succeed();
    }

}
