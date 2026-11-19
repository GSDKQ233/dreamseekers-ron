// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the production distance policy and checks its normal-click wiring.
 * Does not instantiate Minecraft, click a GUI, or simulate server research sync. */
class SkillTargetRangeTest {
    @Test void baseAndResearchedHeavyShotBoundariesAreInclusive() {
        for(double distance:new double[]{10,10.01,11,12,12.01}) {
            assertEquals(distance<=10,SkillTargetRange.withinRange(UnitSkill.STONE_HEAVY_SHOT,
                distance*distance,()->10),"base at "+distance);
            assertEquals(distance<=12,SkillTargetRange.withinRange(UnitSkill.STONE_HEAVY_SHOT,
                distance*distance,()->12),"researched at "+distance);
        }
    }
    @Test void researchRangeIsReadLiveRatherThanCachedAcrossClicks() {
        AtomicInteger range=new AtomicInteger(10);
        assertFalse(SkillTargetRange.withinRange(UnitSkill.STONE_HEAVY_SHOT,121,range::get));
        range.set(12);
        assertTrue(SkillTargetRange.withinRange(UnitSkill.STONE_HEAVY_SHOT,121,range::get));
        range.set(10);
        assertFalse(SkillTargetRange.withinRange(UnitSkill.STONE_HEAVY_SHOT,121,range::get));
    }
    @Test void everyOtherSkillRetainsItsOwnRangeAndDoesNotQueryStoneResearch() {
        for(UnitSkill skill:UnitSkill.values())if(skill!=UnitSkill.STONE_HEAVY_SHOT) {
            double boundary=(double)skill.range()*skill.range();
            assertTrue(SkillTargetRange.withinRange(skill,boundary,()->{throw new AssertionError(skill);}));
            assertFalse(SkillTargetRange.withinRange(skill,boundary+.01,()->{throw new AssertionError(skill);}));
        }
        assertFalse(SkillTargetRange.withinRange(UnitSkill.STONE_HEAVY_SHOT,Double.NaN,()->12));
        assertFalse(SkillTargetRange.withinRange(UnitSkill.STONE_HEAVY_SHOT,Double.POSITIVE_INFINITY,()->12));
    }
    @Test void normalClickUsesResearchRangeWithoutBypassingOwnershipFreshStateOrServerValidation() throws Exception {
        String client=Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/skill/SkillClient.java"));
        assertTrue(client.contains(".filter(entity -> eligibleTarget(source, skill, entity))"));
        String eligibility=client.substring(client.indexOf("private static boolean eligibleTarget"),client.indexOf("@SubscribeEvent\n    public static void tick"));
        assertTrue(eligibility.contains("SkillTargetRange.withinRange(skill,"));
        assertTrue(eligibility.contains("StoneSpiritThrowerUnit stone?StoneHeavyShot.range(stone):skill.range()"));
        for(String gate:new String[]{"!owned(source)","!living.isAlive()","target.isRemoved()","target.isSpectator()","target.level() != source.mob().level()","AlliancesClient.isAllied","!target.isInvisible()","FogOfWarClientEvents.isInBrightChunk"})
            assertTrue(eligibility.contains(gate),gate);
        assertTrue(client.contains("if (!SkillService.supports(source,skill) || !owned(source)"));
        assertTrue(client.contains("View view = currentView(source, skill);\n        if (view == null)"));
        assertTrue(client.contains("SkillNetwork.request(new SkillNetwork.Cast(skill, source.mob().getId()"));
        String server=Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/skill/StoneHeavyShot.java"));
        assertTrue(server.contains("STONE_RANGE_ID,source.level().isClientSide())?12:RANGE"));
        assertTrue(server.contains("if(source.distanceToSqr(target)>(double)range(source)*range(source))return SkillResult.RANGE;"));
        assertTrue(server.contains("!viewer(sender,caster)"));
        assertTrue(server.contains("CoordinatedMark.visible(sender,target)"));
    }
}
