package com.solegendary.reignofnether.resources;

import net.minecraftforge.eventbus.api.Event;
import java.util.UUID;

/** Narrow extension lifecycle notice; logout is intentionally not a resource reset. */
public final class ResourceAccountLifecycleEvent extends Event {
    public enum Kind { RESET, REMOVED }
    public final String owner;
    public final UUID previousEpoch;
    public final Kind kind;
    public ResourceAccountLifecycleEvent(String owner, UUID previousEpoch, Kind kind) {
        this.owner=owner; this.previousEpoch=previousEpoch; this.kind=kind;
    }
}
