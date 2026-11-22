// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.economy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapitalResearchRanksTest {
    @Test void legacyKeyIsExactlyOneRankEvenIfDuplicated() {
        var legacy=List.of(1,1,1);
        assertEquals(1,CapitalResearchRanks.completed(legacy::contains));
        assertEquals(1,CapitalResearchRanks.completed(List.of(1,3)::contains));
        assertEquals(0,CapitalResearchRanks.completed(List.of(2,3)::contains));
        // Arrival order in the native client's completion stream is unimportant once all keys are known.
        assertEquals(3,CapitalResearchRanks.completed(List.of(3,1,1,2)::contains));
    }
    @Test void exactlyOneVisibleSlotOnlyTheUnpaidNextRankIsPurchasable() {
        for(int n=0;n<=3;n++) {
            final int owned=n;
            assertEquals(1L,IntStream.rangeClosed(1,3).filter(i->CapitalResearchRanks.displayed(owned,i)).count());
            for(int id=0;id<=4;id++) {
                assertEquals(owned<3 && id==owned+1,CapitalResearchRanks.isNext(owned,id));
                assertEquals(id==Math.min(3,owned+1),CapitalResearchRanks.displayed(owned,id));
            }
        }
    }
    @Test void bothEffectCurvesAreBoundedAndAdditiveNotCompound() {
        assertArrayEquals(new int[]{5,10,15,20},IntStream.rangeClosed(0,3).map(CapitalResearchRanks::conversionPercent).toArray());
        assertArrayEquals(new int[]{0,15,30,45},IntStream.rangeClosed(0,3).map(CapitalResearchRanks::ascendancyPercent).toArray());
        assertEquals(1.45D,1+CapitalResearchRanks.ascendancyBonus(3));
        assertThrows(IllegalArgumentException.class,()->CapitalResearchRanks.conversionPercent(4));
    }
}
