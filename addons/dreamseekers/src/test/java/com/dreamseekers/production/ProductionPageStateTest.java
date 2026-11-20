// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.production;
import com.dreamseekers.production.pagination.ProductionPageState;
import com.dreamseekers.client.DreamProductionPageInput;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProductionPageStateTest {
    private ScreenEvent.KeyReleased.Pre release(int key){return new ScreenEvent.KeyReleased.Pre(new Screen(Component.literal("Unrendered Hall event probe")){},key,0,0);}
    @Test void sixPerPageNoMissingOrRepeatedRecipes(){
        for(int count:new int[]{0,1,6,7,8,9,10,11,12,13,37}){
            var s=new ProductionPageState(count,List.of());List<Integer> seen=new ArrayList<>();
            do{assertTrue(s.end()-s.start()<=6);for(int i=s.start();i<s.end();i++)seen.add(i);}while(s.turn(1,List.of()));
            assertEquals(java.util.stream.IntStream.range(0,count).boxed().toList(),seen);
            assertFalse(s.turn(0,List.of()));assertFalse(s.turn(2,List.of()));
            while(s.turn(-1,List.of())){}assertEquals(0,s.page());assertFalse(s.turn(-1,List.of()));
        }
        assertThrows(IllegalArgumentException.class,()->new ProductionPageState(-1,List.of()));
    }
    @Test void staleRenderedButtonsCannotFireAfterPageAbaOrSelectionReplacement(){
        var s=new ProductionPageState(7,List.of());long old=s.generation();
        assertTrue(s.allows(old,true,true,true,true,true));
        s.turn(1,List.of());s.turn(-1,List.of());assertFalse(s.allows(old,true,true,true,true,true));
        long now=s.generation();
        for(int missing=0;missing<5;missing++){boolean[] v={true,true,true,true,true};v[missing]=false;assertFalse(s.allows(now,v[0],v[1],v[2],v[3],v[4]));}
    }
    @Test void heldReboundKeyThenFlipCancelsActualForgePreExactlyOnce(){
        var s=new ProductionPageState(7,List.of());s.turn(1,List.of(90)); // user rebound Q slot to Z
        var first=release(90);assertTrue(DreamProductionPageInput.gate(first,s,true));assertTrue(first.isCanceled());
        var fresh=release(90);assertFalse(DreamProductionPageInput.gate(fresh,s,true));assertFalse(fresh.isCanceled());
    }
    @Test void replacementButtonListAlsoQuarantinesHeldKeysAndOtherKeysRemainOriginal(){
        var s=new ProductionPageState(7,List.of(81,89));
        var ordinary=release(87);assertFalse(DreamProductionPageInput.gate(ordinary,s,true));assertFalse(ordinary.isCanceled());
        assertTrue(DreamProductionPageInput.gate(release(81),s,true));assertTrue(DreamProductionPageInput.gate(release(89),s,true));
    }
    @Test void inactiveContextNeverCancelsAnotherFactionReleaseAndRetiresQuarantine(){
        var s=new ProductionPageState(7,List.of(81));var event=release(81);
        assertFalse(DreamProductionPageInput.gate(event,s,false));assertFalse(event.isCanceled());
        assertFalse(DreamProductionPageInput.gate(release(81),s,true));
    }
    @Test void realRoutingRetiresOldHallReleaseWhenSelectionHasChanged(){
        var a=new ProductionPageState(7,List.of(81));var b=new ProductionPageState(7,List.of());
        var event=release(81);DreamProductionPageInput.route(event,List.of(new DreamProductionPageInput.Context(a,false),new DreamProductionPageInput.Context(b,true)));
        assertFalse(event.isCanceled());
        var fresh=release(81);DreamProductionPageInput.route(fresh,List.of(new DreamProductionPageInput.Context(a,true),new DreamProductionPageInput.Context(b,false)));
        assertFalse(fresh.isCanceled());
    }
    @Test void independentHallModelsDoNotSharePageOrKeyState(){
        var a=new ProductionPageState(13,List.of());var b=new ProductionPageState(13,List.of());
        a.turn(1,List.of(81));assertEquals(0,b.page());assertFalse(b.consumeStaleRelease(81));assertTrue(a.consumeStaleRelease(81));
    }
    record Building(String owner,boolean selected,boolean live,double queued){}
    @Test void sameOwnerLegalIdlestKeepsStableTieAndNeverMutatesSelection(){
        var first=new Building("A",true,true,12);var second=new Building("A",true,true,12);
        var input=new ArrayList<>(List.of(new Building("B",true,true,0),new Building("A",false,true,0),new Building("A",true,false,0),first,second));
        var copy=List.copyOf(input);
        assertSame(first,ProductionPageState.idlest(input,b->b.owner.equals("A")&&b.selected&&b.live,Building::queued));
        assertEquals(copy,input);
        assertNull(ProductionPageState.idlest(List.of(new Building("A",true,true,Double.NaN),new Building("A",true,true,-1)),b->true,Building::queued));
    }
    @Test void missingLocalPrerequisitesDoNotSwallowARequestForALegalHall() {
        var blocked = new Building("A",true,true,0);
        var ready = new Building("A",true,true,15);
        var foreign = new Building("B",true,true,0);
        var invalidEstimate = new Building("A",true,true,Double.NaN);
        java.util.function.Predicate<Building> legal = b -> b.owner.equals("A") && b.selected && b.live;
        assertSame(blocked, ProductionPageState.preferredIdlest(List.of(blocked), legal, b -> false, Building::queued));
        assertSame(ready, ProductionPageState.preferredIdlest(List.of(blocked, ready, foreign), legal, b -> b != blocked, Building::queued));
        assertSame(invalidEstimate, ProductionPageState.preferredIdlest(List.of(invalidEstimate, foreign), legal, b -> false, Building::queued));
        assertNull(ProductionPageState.preferredIdlest(List.of(foreign), legal, b -> true, Building::queued));
    }
    @Test void eighthRecipeUsesOnlySecondPageWAndHeldWCannotCrossPageChange(){
        var s=new ProductionPageState(8,List.of());assertEquals(6,s.end());long old=s.generation();
        assertTrue(s.turn(1,List.of(87)));assertEquals(6,s.start());assertEquals(8,s.end());assertEquals(2,s.pages());
        assertEquals(1,7%s.PAGE_SIZE); // eighth recipe => second-page W, no third production row
        assertFalse(s.allows(old,true,true,true,true,true));var stale=release(87);
        assertTrue(DreamProductionPageInput.gate(stale,s,true));assertTrue(stale.isCanceled());
        var fresh=release(87);assertFalse(DreamProductionPageInput.gate(fresh,s,true));assertFalse(fresh.isCanceled());
    }
    @Test void ninthRecipeAddsOnlySecondPageEWithoutMovingEarlierKeys(){
        var s=new ProductionPageState(9,List.of());assertTrue(s.turn(1,List.of(69)));
        assertEquals(6,s.start());assertEquals(9,s.end());assertEquals(List.of(0,1,2),java.util.stream.IntStream.range(s.start(),s.end()).map(i->i%ProductionPageState.PAGE_SIZE).boxed().toList());
        var old=release(69);assertTrue(DreamProductionPageInput.gate(old,s,true));assertTrue(old.isCanceled());
        assertFalse(DreamProductionPageInput.gate(release(69),s,true));assertFalse(s.turn(1,List.of()));
    }
    @Test void tenthRecipeCompletesSecondPageQwerAndHeldRIsQuarantined(){
        var s=new ProductionPageState(10,List.of());assertTrue(s.turn(1,List.of(82)));assertEquals(6,s.start());assertEquals(10,s.end());
        assertEquals(List.of(0,1,2,3),java.util.stream.IntStream.range(s.start(),s.end()).map(i->i%ProductionPageState.PAGE_SIZE).boxed().toList());
        assertEquals(6,s.end()-s.start()+2); // four productions + two page controls fit exactly one six-column row
        var old=release(82);assertTrue(DreamProductionPageInput.gate(old,s,true));assertTrue(old.isCanceled());assertFalse(DreamProductionPageInput.gate(release(82),s,true));
    }
    @Test void eleventhRecipeAddsSecondPageTWithinExistingTwoRowBudget(){
        var s=new ProductionPageState(11,List.of());long first=s.generation();assertTrue(s.turn(1,List.of(84)));
        assertEquals(6,s.start());assertEquals(11,s.end());assertEquals(List.of(0,1,2,3,4),java.util.stream.IntStream.range(s.start(),s.end()).map(i->i%ProductionPageState.PAGE_SIZE).boxed().toList());
        assertEquals(2,(s.end()-s.start()+2+5)/6); // five productions + mouse-only previous/next, no third row
        assertFalse(s.allows(first,true,true,true,true,true));var stale=release(84);assertTrue(DreamProductionPageInput.gate(stale,s,true));assertTrue(stale.isCanceled());assertFalse(DreamProductionPageInput.gate(release(84),s,true));
    }

    @Test void twelfthRecipeCompletesSecondPageQwertyAndHeldYCannotBorrowOldPage(){
        var s=new ProductionPageState(12,List.of());long old=s.generation();assertTrue(s.turn(1,List.of(89)));
        assertEquals(6,s.start());assertEquals(12,s.end());assertEquals(List.of(0,1,2,3,4,5),java.util.stream.IntStream.range(s.start(),s.end()).map(i->i%ProductionPageState.PAGE_SIZE).boxed().toList());
        assertEquals(2,(s.end()-s.start()+2+5)/6);assertFalse(s.turn(1,List.of()));assertFalse(s.allows(old,true,true,true,true,true));
        var stale=release(89);assertTrue(DreamProductionPageInput.gate(stale,s,true));assertTrue(stale.isCanceled());assertFalse(DreamProductionPageInput.gate(release(89),s,true));
    }
}
