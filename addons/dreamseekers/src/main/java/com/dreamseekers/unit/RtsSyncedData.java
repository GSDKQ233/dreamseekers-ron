package com.dreamseekers.unit;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;

/** Per concrete subclass keys: never adds late keys to Minecraft/PasterDream base classes. */
public final class RtsSyncedData {
    final EntityDataAccessor<String> owner;
    final EntityDataAccessor<Integer> scenario;
    final EntityDataAccessor<String> deathCommand;

    public RtsSyncedData(EntityDataAccessor<String> owner, EntityDataAccessor<Integer> scenario,
                         EntityDataAccessor<String> deathCommand) {
        this.owner = owner;
        this.scenario = scenario;
        this.deathCommand = deathCommand;
    }

    public void define(SynchedEntityData data) {
        data.define(owner, "");
        data.define(scenario, -1);
        data.define(deathCommand, "");
    }
}
