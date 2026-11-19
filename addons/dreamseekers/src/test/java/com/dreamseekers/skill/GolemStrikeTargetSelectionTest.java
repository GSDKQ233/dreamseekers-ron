// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

/** Pure selection-policy tests, not native fog/raycast/world or GUI execution. The actual
 * client calls these same choose/contact policies through the native-world adapter. */
class GolemStrikeTargetSelectionTest {
    private static final Vec3 FEET=new Vec3(.5,.5,.5);
    private static final BlockPos NEAR=new BlockPos(1,0,0), FAR=new BlockPos(2,0,0), ROOF=new BlockPos(0,4,0);
    private static BlockPos contact(BlockPos clicked,List<BlockPos> candidates,Predicate<BlockPos> legal) {
        return GolemStrikeTargetSelection.contact(clicked,()->candidates,FEET,()->true,legal);
    }
    @Test void eligibleUnitWinsWithoutConsultingBuildingAndFallbackIsWired() {
        Object unit=new Object();
        var picked=GolemStrikeTargetSelection.choose(unit,()->{throw new AssertionError("must not inspect building");});
        assertSame(unit,picked.unit());assertNull(picked.building());assertNull(picked.contact());
        Object building=new Object();
        var fallback=new GolemStrikeTargetSelection.Selection<Object,Object>(null,building,NEAR);
        assertSame(fallback,GolemStrikeTargetSelection.choose(null,()->fallback));
        assertNull(GolemStrikeTargetSelection.choose(null,()->null));
    }
    @Test void roofFallsBackToNearestSameBuildingWithStableSignedCoordinateTieBreak() {
        assertEquals(NEAR,contact(ROOF,List.of(ROOF,FAR,NEAR),p->true));
        var negative=new BlockPos(-1,0,0);
        assertEquals(negative,contact(ROOF,List.of(NEAR,negative,ROOF),p->true));
        assertEquals(negative,contact(ROOF,List.of(ROOF,negative,NEAR),p->true));
        var lowerY=new BlockPos(0,-1,0);var lowerZ=new BlockPos(0,0,-1);var upperZ=new BlockPos(0,0,1);
        assertEquals(lowerY,contact(null,List.of(upperZ,lowerZ,lowerY),p->true));
        assertEquals(lowerZ,contact(null,List.of(upperZ,lowerZ),p->true));
    }
    @Test void legalClickedContactIsRetainedRatherThanReplacingItWithNearest() {
        assertEquals(FAR,contact(FAR,List.of(NEAR,FAR),p->true));
        assertEquals(NEAR,contact(FAR,List.of(NEAR,FAR),p->p.equals(NEAR)));
    }
    @Test void noCandidateRejectsAndRangeIsInclusiveThreeDimensionalNotHorizontal() {
        assertNull(contact(null,List.of(),p->true));
        assertNull(contact(ROOF,List.of(ROOF),p->true));
        assertNull(contact(new BlockPos(3,1,0),List.of(new BlockPos(3,1,0)),p->true));
        assertEquals(new BlockPos(3,0,0),contact(null,List.of(new BlockPos(3,0,0)),p->true));
        assertNull(GolemStrikeTargetSelection.contact(null,()->List.of(NEAR),new Vec3(Double.NaN,0,0),()->true,p->true));
    }
    @Test void hiddenContactRejectsOrUsesOnlyVisibleSamePlacementAlternative() {
        assertNull(contact(NEAR,List.of(NEAR,FAR),p->false));
        assertEquals(FAR,contact(NEAR,List.of(NEAR,FAR),p->p.equals(FAR)));
    }
    @Test void obstructedUnplacedBorderOrEmptyColliderRejectionsAreNotOverriddenByDistance() {
        // Each false result represents one adapter legality input, not a mocked-world claim.
        for(String gate:List.of("clip","placed","border","collider")) {
            assertNull(contact(NEAR,List.of(NEAR),p->false),gate);
            assertEquals(FAR,contact(NEAR,List.of(NEAR,FAR),p->!p.equals(NEAR)),gate);
        }
    }
    @Test void staleSelectedIdentityFailsBeforeEnumerationAndRechecksBeforeReturn() {
        assertNull(GolemStrikeTargetSelection.contact(NEAR,()->{throw new AssertionError("stale enumeration");},
                FEET,()->false,p->true));
        AtomicBoolean current=new AtomicBoolean(true);
        assertNull(GolemStrikeTargetSelection.contact(NEAR,()->List.of(NEAR),FEET,current::get,p->{current.set(false);return true;}));
    }
    @Test void otherBuildingClickedBlockNeverEntersSelectedPlacementCandidates() {
        BlockPos foreign=new BlockPos(0,0,1);
        assertEquals(NEAR,contact(foreign,List.of(NEAR,FAR),p->true));
        assertNull(contact(foreign,List.of(ROOF),p->true));
        assertNull(contact(foreign,List.of(),p->true));
    }
}
