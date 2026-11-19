package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;
import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraftforge.gametest.*;

/** Post-fix controls for unified-free-provenance-proposal.patch only.
 * Uses full Entity.save -> EntityType.loadEntityRecursive -> Entity.load; not direct readAdditionalSaveData.
 * Native in-process serialization is not a separate-JVM restart / disk durability test. */
@GameTestHolder("dreamseekers_audit_fixes")
@PrefixGameTestTemplate(false)
public final class UnifiedFreeProvenancePostfixControls {
    private static final String SUMMONED="reignofnetherSummoned";
    private static Mob body(GameTestHelper h,boolean bee) {
        Mob e=bee?EntityRegistrar.BEE_UNIT.get().create(h.getLevel()):EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());
        ((Unit)e).setOwnerName("AuditFreeNbt"); e.setNoAi(true); return e;
    }
    private static void summoned(Mob e,boolean value) { if(e instanceof BeeUnit b)b.setIsSummoned(value);else ((ZombieUnit)e).setIsSummoned(value); }
    private static boolean summoned(Mob e) { return e instanceof BeeUnit b?b.isSummoned():((ZombieUnit)e).isSummoned(); }
    private static CompoundTag save(GameTestHelper h,Entity e) {
        CompoundTag nbt=new CompoundTag();h.assertTrue(e.save(nbt),"full native Entity.save includes registered type id");return nbt;
    }
    private static Mob load(GameTestHelper h,CompoundTag nbt) {
        Entity e=EntityType.loadEntityRecursive(nbt,h.getLevel(),value->value);
        h.assertTrue(e instanceof Mob,"native factory reconstructs saved entity type");return (Mob)e;
    }
    private static void forgetLegacyMarker(CompoundTag nbt) {
        nbt.getCompound("ForgeData").remove(RtsKillValueClaims.FREE_ORIGIN_KEY);
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="core_economy_arena",batch="reaudit_free_provenance_controls",timeoutTicks=40)
    public static void completeNativeSaveLoadMigrationAndFalseCannotWashOrigin(GameTestHelper h) {
        for(boolean bee:new boolean[]{false,true})for(int variant=0;variant<7;variant++) {
            Mob original=body(h,bee);
            if(variant==0||variant==1)summoned(original,true);
            if(variant==1) { original.removeAllEffects();summoned(original,false); }
            if(variant==2||variant==5)original.addEffect(new MobEffectInstance(MobEffectRegistrar.LIMITED_LIFESPAN.get(),100,0,true,false));
            CompoundTag nbt=save(h,original);
            // 0 explicit true; 1 true->false + cleanse; 2 old missing + actual native lifetime;
            // 3 old missing/no evidence; 4 malformed boolean; 5 explicit false + lifetime;
            // 6 malformed persistent provenance is conservative, not a redeemable value.
            if(variant==2||variant==3) { nbt.remove(SUMMONED);forgetLegacyMarker(nbt); }
            if(variant==4) { nbt.putString(SUMMONED,"bad type");forgetLegacyMarker(nbt); }
            if(variant==6) { CompoundTag forge=nbt.getCompound("ForgeData");forge.putString(RtsKillValueClaims.FREE_ORIGIN_KEY,"bad type");nbt.put("ForgeData",forge); }
            Mob loaded=load(h,nbt);
            boolean expectedFlag=variant==0||variant==2||variant==4;
            boolean expectedFree=variant==0||variant==1||variant==2||variant==4||variant==6;
            String label=(bee?"bee":"zombie")+" variant="+variant;
            h.assertTrue(summoned(loaded)==expectedFlag,"native lifecycle flag "+label);
            h.assertTrue(RtsKillValueClaims.isFree(loaded)==expectedFree,"independent economic origin "+label);
            Mob twice=load(h,save(h,loaded));
            h.assertTrue(summoned(twice)==expectedFlag&&RtsKillValueClaims.isFree(twice)==expectedFree,"second full native roundtrip "+label);
            h.assertTrue(twice.getUUID().equals(original.getUUID()),"native identity preserved "+label);
            original.discard();loaded.discard();twice.discard();
        }
        h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="core_economy_arena",batch="reaudit_free_provenance_controls",timeoutTicks=40)
    public static void explicitInfectionOriginIsMonotonicAndNativePersistent(GameTestHelper h) {
        Mob child=EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get().create(h.getLevel());((Unit)child).setOwnerName("AuditInfNbt");
        h.assertTrue(!RtsKillValueClaims.isFree(child),"normal newly constructed unit is not guessed to be free");
        RtsKillValueClaims.markFree(child,RtsKillValueClaims.FreeOrigin.NATIVE_INFECTION);
        CompoundTag first=child.getPersistentData().getCompound(RtsKillValueClaims.FREE_ORIGIN_KEY).copy();
        RtsKillValueClaims.markFree(child,RtsKillValueClaims.FreeOrigin.NATIVE_SUMMON);
        h.assertTrue(first.equals(child.getPersistentData().getCompound(RtsKillValueClaims.FREE_ORIGIN_KEY)),"second marking cannot rewrite economic origin");
        Mob loaded=load(h,save(h,child));
        h.assertTrue(RtsKillValueClaims.isFree(loaded)&&first.equals(loaded.getPersistentData().getCompound(RtsKillValueClaims.FREE_ORIGIN_KEY)),"infection origin survives actual native save/factory/load");
        h.assertTrue(((Unit)loaded).getCost().food>0&&!RtsKillValueClaims.isClaimed(loaded),"free provenance is independent of configured paid-unit cost and redeemed claim");
        child.discard();loaded.discard();h.succeed();
    }
}
