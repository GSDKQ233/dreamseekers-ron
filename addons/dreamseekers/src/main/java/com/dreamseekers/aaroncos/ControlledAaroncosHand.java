// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.aaroncos;
/** Explicit paid identity shared ONLY by the two new controlled foundations, never wild bosses. */
public interface ControlledAaroncosHand extends com.dreamseekers.unit.TeamSafeCombatUnit, com.solegendary.reignofnether.api.HealthDamageWriters.Managed {
    enum Side { LEFT, RIGHT }
    Side handSide();
    @Override default void writeDamageHealth(net.minecraft.world.damagesource.DamageSource source,float health) { HandBondService.writeDamage(mob(),source,health); }
}
