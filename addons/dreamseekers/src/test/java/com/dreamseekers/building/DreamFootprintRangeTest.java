package com.dreamseekers.building;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure geometry: ranges are measured from the building footprint, the same on all four sides. */
class DreamFootprintRangeTest {
    // A 9x9 dais occupying 0..8 on both axes and a 13x11 laboratory touching one of its sides.
    private static boolean aura(int labMinX,int labMinZ,int dy) {
        return DreamFootprintRange.footprintsWithin(
                DreamFootprintRange.spanGap(0,8,labMinX,labMinX+12),DreamFootprintRange.spanGap(0,8,labMinZ,labMinZ+10),dy,12,8);
    }
    @Test void touchingHostIsCoveredOnEverySide() {
        for(int slide=-10;slide<=8;slide++) {
            assertTrue(aura(9,slide,0),"east");assertTrue(aura(-13,slide,0),"west");
        }
        for(int slide=-12;slide<=8;slide++) {
            assertTrue(aura(slide,9,0),"south");assertTrue(aura(slide,-11,0),"north");
        }
    }
    @Test void gapIsSymmetricInclusiveAtTwelveAndEuclidean() {
        assertEquals(0,DreamFootprintRange.spanGap(0,8,8,20));
        assertEquals(1,DreamFootprintRange.spanGap(0,8,9,21));
        assertEquals(1,DreamFootprintRange.spanGap(9,21,0,8));
        assertTrue(aura(20,0,0),"gap 12 east");assertFalse(aura(21,0,0),"gap 13 east");
        assertTrue(aura(-24,0,0),"gap 12 west");assertFalse(aura(-25,0,0),"gap 13 west");
        assertFalse(aura(20,10,0),"gap 12 east and 2 south is outside the circle");
        assertTrue(aura(16,16,0),"gap 8,8");assertFalse(aura(17,17,0),"gap 9,9 is outside the circle");
    }
    @Test void verticalRuleStaysRelativeToOriginLayer() {
        assertTrue(aura(9,0,8));assertTrue(aura(9,0,-8));assertFalse(aura(9,0,9));assertFalse(aura(9,0,-9));
    }
    @Test void originOnlyMeasurementIsASubsetOfFootprintMeasurement() {
        // Old rule: origin corners (0,0) and (x,z) within 12. Every such layout must still be covered.
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)
            if(x*x+z*z<=144)assertTrue(aura(x,z,0),x+","+z);
    }
    @Test void pointRangeReachesTheFarSideOfAWideBuilding() {
        // 13x11 garden occupying 0..12 by 0..10 with radius 8; the origin block centre is (.5,.5).
        assertEquals(0,DreamFootprintRange.pointGap(6.5,0,12));
        assertEquals(2,DreamFootprintRange.pointGap(14.5,0,12));
        assertEquals(2,DreamFootprintRange.pointGap(-1.5,0,12));
        double farX=DreamFootprintRange.pointGap(16.5,0,12),farZ=DreamFootprintRange.pointGap(10.5,0,10);
        assertTrue(DreamFootprintRange.pointWithin(farX,1,farZ,8),"4 blocks beyond the far edge");
        assertTrue(DreamFootprintRange.pointWithin(8,0,0,8),"inclusive radius");
        assertFalse(DreamFootprintRange.pointWithin(8.01,0,0,8));
        assertFalse(DreamFootprintRange.pointWithin(6,0,6,8),"corner distance is Euclidean");
        assertFalse(DreamFootprintRange.pointWithin(0,9,0,8),"vertical distance still counts");
    }
}
