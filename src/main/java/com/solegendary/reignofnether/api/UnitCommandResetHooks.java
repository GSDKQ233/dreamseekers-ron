package com.solegendary.reignofnether.api;

import com.solegendary.reignofnether.unit.interfaces.Unit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Optional order-lifecycle observers. Native resets remain unchanged when no addon registers. */
public final class UnitCommandResetHooks {
    private static final List<Consumer<Unit>> OBSERVERS = new CopyOnWriteArrayList<>();
    private UnitCommandResetHooks() {}
    public static void register(Consumer<Unit> observer) { OBSERVERS.add(Objects.requireNonNull(observer)); }
    public static void beforeReset(Unit unit) { for(var observer:OBSERVERS)observer.accept(unit); }
}
