// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import java.util.UUID;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
/** One fixed transient modifier per category; the strongest valid contribution wins. */
public enum BuffKind {
    ATTACK_SPEED("02e6186c-7291-4d50-92dd-df7241a9b94d", .25),
    MOVEMENT_SPEED("ee4d2b1a-59b0-49a6-b2ed-f0db67b31382", .25),
    ATTACK_DAMAGE("c04f669f-3a55-4d7e-9fe7-2619abb55e3d", .20);
    public final UUID modifierId;
    public final double cap;
    BuffKind(String uuid,double cap) { this.modifierId=UUID.fromString(uuid);this.cap=cap; }
    public Attribute attribute() {
        return switch(this) {
            case ATTACK_SPEED -> AttributeRegistrar.ATTACKS_PER_SECOND.get();
            case MOVEMENT_SPEED -> Attributes.MOVEMENT_SPEED;
            case ATTACK_DAMAGE -> AttributeRegistrar.ATTACK_DAMAGE.get();
        };
    }
}
