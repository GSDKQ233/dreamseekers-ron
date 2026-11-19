// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.economy.DreamAscendancyAttributes;
import com.dreamseekers.economy.DreamEconomyResearch;
import com.dreamseekers.unit.DreamRtsUnit;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Current-binary natural-save witness, NOT an archive produced by an API51 JVM.
 * The unchanged API51 LivingTick research hook is the behavior under observation.
 * Never calls getAttribute, reconcile, setBaseValue or setHealth on any witness body.
 */
@GameTestHolder("dreamseekers_strengthening_migration")
@PrefixGameTestTemplate(false)
public final class StrengtheningNaturalSaveGameTests {
    private static final String BATCH="dreamseekers_strengthening_migration";
    private static final String HP="minecraft:generic.max_health", AD="reignofnether:attack_damage";
    private static final String[] ATTRIBUTES={HP,"minecraft:generic.attack_damage",AD,"minecraft:generic.armor"};
    private static final List<AaroncosSkillCombatGameTests.F> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        for(var fixture:List.copyOf(FIXTURES))closeFixture(fixture);
    }
    private static void closeFixture(AaroncosSkillCombatGameTests.F fixture) {
        ResearchServerEvents.removeAllResearchFor(fixture.owner);FIXTURES.remove(fixture);fixture.close();
    }
    private static CompoundTag attribute(CompoundTag root,String name) {
        for(Tag raw:root.getList("Attributes",Tag.TAG_COMPOUND)) {
            CompoundTag entry=(CompoundTag)raw;if(name.equals(entry.getString("Name")))return entry;
        }
        return null;
    }
    private static void close(GameTestHelper h,double actual,double expected,String reason) {
        h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.0005,reason+" actual="+actual+" expected="+expected);
    }
    private static void permanent(GameTestHelper h,CompoundTag saved,UUID id,boolean researched,String label) {
        int found=0;
        for(Tag raw:saved.getList("Modifiers",Tag.TAG_COMPOUND)) {
            CompoundTag modifier=(CompoundTag)raw;
            if(modifier.hasUUID("UUID")&&id.equals(modifier.getUUID("UUID"))) {
                found++;close(h,modifier.getDouble("Amount"),.15,label+" permanent amount");
                h.assertTrue(modifier.getInt("Operation")==2,label+" MULTIPLY_TOTAL operation");
            }
        }
        h.assertTrue(found==(researched?1:0),label+" exact own permanent UUID count="+found);
    }
    private static CompoundTag observe(GameTestHelper h,Mob body,String profile,double[] bases,boolean researched) {
        // Native save first. All following checks inspect its NBT, not live AttributeInstances.
        CompoundTag saved=body.saveWithoutId(new CompoundTag());
        CompoundTag hp=attribute(saved,HP),ad=attribute(saved,AD);
        h.assertTrue(hp!=null&&ad!=null,"natural LivingTick saved mandatory HP anchor and RoN AD: "+profile);
        for(int i=0;i<ATTRIBUTES.length;i++) {
            CompoundTag entry=attribute(saved,ATTRIBUTES[i]);
            if(entry!=null)close(h,entry.getDouble("Base"),bases[i],profile+" registered base never altered by fixture or research "+ATTRIBUTES[i]);
        }
        close(h,saved.getFloat("Health"),bases[0]*(researched?1.15:1),profile+" undamaged full-health ratio after natural tick");
        permanent(h,hp,DreamAscendancyAttributes.HEALTH_ID,researched,profile+" HP");
        permanent(h,ad,DreamAscendancyAttributes.DAMAGE_ID,researched,profile+" RoN AD");
        h.assertTrue(saved.getUUID("UUID").equals(body.getUUID()),profile+" same original body saved");
        System.out.println("STRENGTHENING_NATURAL_SAVE_ANCHOR profile="+profile+" researched="+researched
                +" tick="+body.tickCount+" uuid="+body.getUUID()+" health="+saved.getFloat("Health")
                +" attributes="+saved.getList("Attributes",Tag.TAG_COMPOUND));
        return saved;
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_migration",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void normalLivingTicksSaveKnownHealthAnchorWithoutFixtureInstantiationOrInjury(GameTestHelper h) {
        var fixture=new AaroncosSkillCombatGameTests.F(h,"API52_NaturalSaveAnchor");FIXTURES.add(fixture);
        h.assertTrue(!ResearchServerEvents.playerHasResearch(fixture.owner,DreamEconomyResearch.ASCENDANCY_ID),"fresh owner is genuinely unresearched");
        Mob[] bodies={DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel()),
                DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(h.getLevel()),
                DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel())};
        String[] profiles={"mother","left","right"};
        double[][] bases={{200,14,14,4},{450,26,26,7},{420,24,24,6}};
        int[] unresearchedTick=new int[bodies.length];
        for(int i=0;i<bodies.length;i++) {
            Mob body=bodies[i];h.assertTrue(body!=null,"real registered profile created");
            ((DreamRtsUnit)body).setOwnerName(fixture.owner);
            CompoundTag sparse=body.saveWithoutId(new CompoundTag());
            h.assertTrue(body.tickCount==0&&!body.isAddedToWorld(),"initial sparse witness has never been published or ticked");
            h.assertTrue(attribute(sparse,HP)==null,"unticked constructor save demonstrates absent HP instance, not a normal ticked archive: "+profiles[i]);
            close(h,sparse.getFloat("Health"),bases[i][0],"unticked default full HP without accessing MAX_HEALTH instance");
            System.out.println("STRENGTHENING_UNTICKED_SPARSE_SAVE profile="+profiles[i]+" uuid="+body.getUUID()
                    +" health="+sparse.getFloat("Health")+" attributes="+sparse.getList("Attributes",Tag.TAG_COMPOUND));
            // Existing fixture only instantiates CRITICAL_HIT_CHANCE and AGGRO_RANGE, not the four balance attributes.
            fixture.add(body,fixture.owner,(i-1)*8,0);
        }
        h.startSequence().thenWaitUntil(()->{
            for(Mob body:bodies)h.assertTrue(body.tickCount>=3&&body.isAddedToWorld()
                    &&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body,"three genuine naturally ticking original published bodies");
        }).thenExecute(()->{
            for(int i=0;i<bodies.length;i++) {
                h.assertTrue(!DreamAscendancyAttributes.enabledFor(bodies[i]),"unresearched authority stays false");
                observe(h,bodies[i],profiles[i],bases[i],false);unresearchedTick[i]=bodies[i].tickCount;
            }
            // Real owner research state; no direct reconcile or attribute/modifier write by this test.
            ResearchServerEvents.addResearch(fixture.owner,DreamEconomyResearch.ASCENDANCY_ID);
        }).thenWaitUntil(()->{
            for(int i=0;i<bodies.length;i++)h.assertTrue(bodies[i].tickCount>=unresearchedTick[i]+3,"three further natural LivingTicks after real research grant");
        }).thenExecute(()->{
            for(int i=0;i<bodies.length;i++) {
                h.assertTrue(DreamAscendancyAttributes.enabledFor(bodies[i]),"actual supported owner account/faction/research qualifies");
                observe(h,bodies[i],profiles[i],bases[i],true);
            }
            closeFixture(fixture);h.succeed();
        });
    }
}
