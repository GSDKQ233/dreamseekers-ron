// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.progression;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Pure presentation state. Observing a completion/query never invokes a purchase. */
final class LaboratoryResearchSlotState {
    private final BooleanSupplier completed,nativeEnabled;
    private final Runnable nativeClick;
    LaboratoryResearchSlotState(BooleanSupplier completed,BooleanSupplier nativeEnabled,Runnable nativeClick){
        this.completed=Objects.requireNonNull(completed);this.nativeEnabled=Objects.requireNonNull(nativeEnabled);
        this.nativeClick=Objects.requireNonNull(nativeClick);
    }
    boolean completed(){return completed.getAsBoolean();}
    boolean enabled(){return !completed()&&nativeEnabled.getAsBoolean();}
    void click(){if(enabled())nativeClick.run();}
}
