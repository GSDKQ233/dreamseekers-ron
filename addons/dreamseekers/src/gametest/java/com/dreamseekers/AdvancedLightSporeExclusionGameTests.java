// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.resonance.LightResonanceResearch;
import com.dreamseekers.skill.*;
import com.dreamseekers.spore.*;
import com.dreamseekers.unit.GoldenFoxUnit;
import com.dreamseekers.unitprogression.AdvancedUnitResearch;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Actual ground carrier + ordinary blessing under both eligible luminous tiers.
 * Research grants are explicit effect fixtures; payment is covered by separate real research tests. */
@GameTestHolder("dreamseekers_advanced_light_spore") @PrefixGameTestTemplate(false)
public final class AdvancedLightSporeExclusionGameTests {
    private static final String BATCH="advanced_light_spore";
    private static final List<ResonanceNetworkGameTests.F> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanupAdvancedLightSpore(ServerLevel level){
        for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();
    }
    private static <T extends Mob> T mob(ResonanceNetworkGameTests.F f,EntityType<T> type,int x,int z){
        T body=type.create(f.level);((Unit)body).setOwnerName(f.owner);body.setNoAi(true);
        BlockPos pos=f.dais.originPos.offset(x,1,z);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            f.level.setBlock(pos.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<6;y++)f.level.setBlock(pos.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);
        }
        body.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);f.h.assertTrue(f.level.addFreshEntity(body),"actual published owned unit");f.units.add(body);return body;
    }
    private static SkillRequest blessing(GoldenFoxUnit fox){return new SkillRequest(UnitSkill.FOX_BLESSING,fox.getId(),fox.getUUID(),fox.rts().skills().revision(),fox.getId(),fox.getUUID());}
    private static SkillGroundRequest seed(GoldenFoxUnit fox,BlockPos ground){return new SkillGroundRequest(UnitSkill.FOX_DREAM_SEED,fox.getId(),fox.getUUID(),fox.rts().skills().revision(),ground);}
    private static int cd(GoldenFoxUnit fox,UnitSkill skill){return fox.rts().skills().cooldownTicks(skill);}
    private static boolean blessed(LivingEntity target){return SkillEffects.hasNamedBuff(target,SkillEffects.FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED);}
    private static void ledger(ResonanceNetworkGameTests.F f){f.h.assertTrue(f.account.food==5000&&f.account.wood==5000&&f.account.ore==5000&&f.shadow()==500,"both independent casts/rejections leave original resource balances unchanged");}
    @GameTest(templateNamespace="dreamseekers_advanced_light_spore",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=290)
    public static void tierOneExtendedFoxSeedFirstKeepsOriginalSporeLifetimeAndIndependentCooldown(GameTestHelper h){run(h,1,true,250);}
    @GameTest(templateNamespace="dreamseekers_advanced_light_spore",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=290)
    public static void tierTwoExtendedFoxBlessingFirstKeepsOriginalSporeLifetimeAndIndependentCooldown(GameTestHelper h){run(h,2,false,251);}
    private static void run(GameTestHelper h,int tier,boolean seedFirst,int index){
        var f=new ResonanceNetworkGameTests.F(h,"API54SporeExclude"+tier,index);FIXTURES.add(f);
        var fox=mob(f,DreamseekersMod.GOLDEN_FOX_UNIT.get(),2,-5);
        var target=mob(f,DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),5,-5);
        BlockPos ground=f.dais.originPos.offset(2,1,-9);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            f.level.setBlock(ground.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<5;y++)f.level.setBlock(ground.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);
        }
        ResearchServerEvents.addResearch(f.owner,LightResonanceResearch.FIRST_ID);
        if(tier==2)ResearchServerEvents.addResearch(f.owner,LightResonanceResearch.FINAL_ID);
        ResearchServerEvents.addResearch(f.owner,AdvancedUnitResearch.LIGHT_EXTENSION_ID);
        final JungleSporeCarrier[] carrier={null};final long[] at={0};
        h.startSequence().thenIdle(25).thenWaitUntil(()->{f.ready(fox);f.ready(target);})
          .thenExecute(()->{
              h.assertTrue(!FogOfWarServerEvents.isEnabled(),"isolated default fog-off effect fixture; this is not a fog-visibility regression");
              h.assertTrue(SkillAmplification.currentTier(fox,UnitSkill.FOX_BLESSING)==tier&&SkillAmplification.extensionActive(fox,UnitSkill.FOX_BLESSING),"actual owned live network supplies luminous tier and extension");
              var oldBlessing=blessing(fox);var oldSeed=seed(fox,ground);at[0]=f.level.getGameTime();
              if(seedFirst){
                  var result=SkillService.requestGround(f.peer.actor,oldSeed);h.assertTrue(result==SkillResult.SUCCESS,"real seed-first ground cast: "+result);
                  h.assertTrue(cd(fox,UnitSkill.FOX_DREAM_SEED)==480&&cd(fox,UnitSkill.FOX_BLESSING)==0,"seed does not start blessing cooldown");
                  h.assertTrue(SkillService.request(f.peer.actor,oldBlessing)==SkillResult.REPLAY&&cd(fox,UnitSkill.FOX_BLESSING)==0,"old shared revision denied without spending unused blessing");
                  h.assertTrue(SkillService.request(f.peer.actor,blessing(fox))==SkillResult.SUCCESS,"fresh blessing still independently castable while seed cooling");
              }else{
                  h.assertTrue(SkillService.request(f.peer.actor,oldBlessing)==SkillResult.SUCCESS,"real blessing-first cast");
                  h.assertTrue(cd(fox,UnitSkill.FOX_BLESSING)==432&&cd(fox,UnitSkill.FOX_DREAM_SEED)==0,"extended blessing does not start seed cooldown");
                  h.assertTrue(SkillService.requestGround(f.peer.actor,oldSeed)==SkillResult.REPLAY&&cd(fox,UnitSkill.FOX_DREAM_SEED)==0,"old shared revision denied without spending unused seed");
                  var result=SkillService.requestGround(f.peer.actor,seed(fox,ground));h.assertTrue(result==SkillResult.SUCCESS,"fresh seed remains independently castable while blessing cooling: "+result);
              }
              carrier[0]=SporeLease.activeFor(fox);
              h.assertTrue(carrier[0]!=null&&carrier[0].isAddedToWorld()&&f.level.getEntity(carrier[0].getUUID())==carrier[0]&&SporeLease.remainingTicks(carrier[0])==200,"real published carrier has original200tick lease despite extension");
              h.assertTrue(blessed(target)&&Math.abs(SkillEffects.bonus(target,BuffKind.ATTACK_SPEED)-(tier==1?.24:.30))<.0001,"actual blessing positive control keeps old tier potency");
              h.assertTrue(cd(fox,UnitSkill.FOX_BLESSING)==432&&cd(fox,UnitSkill.FOX_DREAM_SEED)==480,"distinct actual absolute cooldowns, not constants-only checks");
              long revision=fox.rts().skills().revision();
              h.assertTrue(SkillService.requestGround(f.peer.actor,oldSeed)==SkillResult.REPLAY&&SkillService.request(f.peer.actor,oldBlessing)==SkillResult.REPLAY,"both stale inputs cannot replay");
              h.assertTrue(SkillService.requestGround(f.peer.actor,seed(fox,ground))==SkillResult.COOLDOWN&&SkillService.request(f.peer.actor,blessing(fox))==SkillResult.COOLDOWN,"fresh inputs independently observe their own cooldown");
              h.assertTrue(fox.rts().skills().revision()==revision&&SporeLease.activeFor(fox)==carrier[0]&&SporeLease.remainingTicks(carrier[0])==200,"rejections refresh neither lease nor revision");ledger(f);
          }).thenIdle(143).thenExecute(()->{
              h.assertTrue(f.level.getGameTime()-at[0]==143&&blessed(target),"extended blessing genuinely outlives original120 and remains before144");
              h.assertTrue(SporeLease.active(carrier[0])&&SporeLease.remainingTicks(carrier[0])==57,"spore continues original independent deadline");
          }).thenIdle(2).thenExecute(()->{
              h.assertTrue(!blessed(target)&&SkillEffects.bonus(target,BuffKind.ATTACK_SPEED)==0,"extended blessing naturally ends at144, no shared spore duration");
              h.assertTrue(SporeLease.active(carrier[0])&&SporeLease.remainingTicks(carrier[0])==55,"carrier remains alive after blessing expiry");
          }).thenIdle(54).thenExecute(()->{
              h.assertTrue(f.level.getGameTime()-at[0]==199&&SporeLease.active(carrier[0])&&SporeLease.remainingTicks(carrier[0])==1,"original carrier active exactly one tick before original200deadline");
          }).thenIdle(2).thenExecute(()->{
              h.assertTrue(carrier[0].isRemoved()&&!SporeLease.active(carrier[0])&&SporeLease.activeFor(fox)==null&&SporeLease.activeCount(f.owner)==0,"natural200deadline removes original carrier, not extended240");
              long elapsed=f.level.getGameTime()-at[0];h.assertTrue(elapsed==201&&cd(fox,UnitSkill.FOX_DREAM_SEED)==480-elapsed&&cd(fox,UnitSkill.FOX_BLESSING)==432-elapsed,"both natural absolute cooldowns survive independent effect expiry");
              ledger(f);System.out.println("API54_LIGHT_SPORE_EXCLUSION_PASS tier="+tier+" seedFirst="+seedFirst+" blessing144_CD432 spore200_CD480 elapsed="+elapsed);
              f.close();h.succeed();
          });
    }
}
