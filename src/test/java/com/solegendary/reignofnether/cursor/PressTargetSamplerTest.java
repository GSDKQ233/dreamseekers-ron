package com.solegendary.reignofnether.cursor;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PressTargetSamplerTest {
    @Test void crossingHudBoundaryUsesNewPointAndDoesNotPickBehindHud() {
        var picker=new Probe();
        var sampler=new PressTargetSampler();
        sampler.sample(new Object(),8.9,3.1,picker); // this X belongs to HUD
        sampler.sample(new Object(),12.8,6.2,picker); // now world, no intervening render
        sampler.sample(new Object(),1.1,9.9,picker); // back over HUD
        assertEquals(List.of("H8,3","H12,6","W12,6:a","H1,9"),picker.steps);
    }
    @Test void multiplePreListenersShareOneRayButSecondPhysicalPressRepicksMovingTarget() {
        var picker=new Probe();var sampler=new PressTargetSampler();var first=new Object();
        sampler.sample(first,20.4,2.2,picker);picker.body="b";
        sampler.sample(first,20.4,2.2,picker);
        sampler.sample(new Object(),20.4,2.2,picker);
        assertEquals(List.of("H20,2","W20,2:a","H20,2","W20,2:b"),picker.steps);
    }
    private static final class Probe implements PressTargetSampler.Picker {
        String body="a";final List<String> steps=new ArrayList<>();
        public boolean overHud(int x,int y){steps.add("H"+x+","+y);return x<10;}
        public void pickWorld(int x,int y){steps.add("W"+x+","+y+":"+body);}
    }
}
