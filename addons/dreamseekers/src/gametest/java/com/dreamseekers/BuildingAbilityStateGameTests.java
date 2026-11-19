// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.BuildingAbility;
import com.dreamseekers.buildingability.BuildingAbilityState;
import com.dreamseekers.buildingability.BuildingCodex;
import com.solegendary.reignofnether.building.data.DataStorage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.UUID;

/** Exercises real registered DataStorage codec; no gameplay result is inferred from constants. */
@GameTestHolder("dreamseekers_building_abilities") @PrefixGameTestTemplate(false)
public final class BuildingAbilityStateGameTests {
    private static final String BATCH="building_ability_state";
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=30)
    public static void oldAbsentStateInitializesOnceAndRealStorageRoundTripsCooldown(GameTestHelper h){
        var storage=DataStorage.read(new ListTag(),h.getLevel().getServer());var state=storage.getData(BuildingAbilityState.TYPE);
        h.assertTrue(state.supported()&&state.instance()!=null&&state.revision()==0,"old absent key creates one supported default instance");
        h.assertTrue(storage.getData(BuildingAbilityState.TYPE)==state,"same storage never remints instance on another query");
        for(var ability:BuildingAbility.values())h.assertTrue(state.readyAt(ability)==0,"old save has no phantom paid cooldown");
        var encoded=state.write();encoded.putLong("revision",8);encoded.getCompound("readyAt").putLong(BuildingAbility.GARDEN_RECOVERY.path(),900);
        storage.setData(BuildingAbilityState.TYPE,BuildingAbilityState.read(encoded));
        var reloaded=DataStorage.read(storage.write(),h.getLevel().getServer()).getData(BuildingAbilityState.TYPE);
        h.assertTrue(reloaded.supported()&&reloaded.instance().equals(state.instance())&&reloaded.revision()==8&&reloaded.cooldown(BuildingAbility.GARDEN_RECOVERY,500)==400,
            "actual registered codec preserves paid deadline and cast revision");
        h.assertTrue(reloaded.cooldown(BuildingAbility.GARDEN_RECOVERY,900)==0&&reloaded.readyAt(BuildingAbility.GARDEN_RECOVERY)==900,
            "deadline observation does not refund or mutate historical paid state");h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=30)
    public static void futureMalformedAndZeroIdentityStayOpaqueWithoutFreshAuthority(GameTestHelper h){
        var ordinary=new BuildingAbilityState().write();var samples=new java.util.ArrayList<CompoundTag>();
        var future=ordinary.copy();future.putInt("schema",999);future.putString("unknown","retain me");samples.add(future);
        var missing=ordinary.copy();missing.remove("instance");samples.add(missing);
        var zero=ordinary.copy();zero.putUUID("instance",new UUID(0,0));samples.add(zero);
        var negative=ordinary.copy();negative.putLong("revision",-1);samples.add(negative);
        var wrongType=ordinary.copy();wrongType.putString("revision","0");samples.add(wrongType);
        var unknownDeadline=ordinary.copy();unknownDeadline.getCompound("readyAt").putLong("future_ability",17);samples.add(unknownDeadline);
        var negativeDeadline=ordinary.copy();negativeDeadline.getCompound("readyAt").putLong(BuildingAbility.ROOT_CROWN.path(),-1);samples.add(negativeDeadline);
        for(var raw:samples){
            var decoded=BuildingAbilityState.read(raw);h.assertTrue(!decoded.supported()&&decoded.instance()==null&&decoded.write().equals(raw),"unsupported raw is opaque, not a fresh default");
            var storage=new DataStorage();storage.setData(BuildingAbilityState.TYPE,decoded);
            var restored=DataStorage.read(storage.write(),h.getLevel().getServer()).getData(BuildingAbilityState.TYPE);
            h.assertTrue(!restored.supported()&&restored.write().equals(raw)&&!restored.canSpend(BuildingAbility.ROOT_CROWN,1000),"real storage saves unknown data unchanged and never spends");
        }
        var exhausted=ordinary.copy();exhausted.putLong("revision",Long.MAX_VALUE);
        h.assertTrue(!BuildingAbilityState.read(exhausted).canSpend(BuildingAbility.ROOT_CROWN,1000),"revision exhaustion fails closed instead of wrapping");h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=30)
    public static void codexBoundsKeepShrunkPageAndRejectDuplicates(GameTestHelper h){
        var id=UUID.randomUUID();var type=DreamseekersMod.id("beetle");var row=new BuildingCodex.Row(10,id,type,5,10,2,true);
        h.assertTrue(new BuildingCodex.Page(7,1,false,List.of()).valid(),"last requested page stays empty after roster shrink rather than silently changing page identity");
        h.assertTrue(!new BuildingCodex.Page(8,1,false,List.of()).valid()&&!new BuildingCodex.Page(0,257,true,List.of()).valid(),"maximum eight pages and256 rows");
        h.assertTrue(!new BuildingCodex.Page(0,2,false,List.of(row,row)).valid(),"same id/UUID cannot count twice");
        h.assertTrue(!new BuildingCodex.Row(11,UUID.randomUUID(),type,Float.NaN,10,0,false).valid(),"NaN health is not valid private runtime data");h.succeed();
    }
}
