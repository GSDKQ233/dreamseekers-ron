package com.solegendary.reignofnether.resources;

import net.minecraftforge.eventbus.api.Event;

/** Server-only save extension hook. Mutate only this detached account copy, never live gameplay.
 * A listener may project refunds for its own transient queues; unresolved claims stay in the copy. */
public final class ResourceSaveProjectionEvent extends Event {
    public final Resources copy;
    public ResourceSaveProjectionEvent(Resources copy) {this.copy=copy;}
}
