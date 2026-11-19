package com.solegendary.reignofnether.minimap;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Headless lifetime policy; rendering and native texture ownership stay in Minecraft's manager. */
public final class MinimapSessionLifecycle {
    private MinimapSessionLifecycle() { }

    public static void clearSession(Collection<?> virtualUnits, Collection<?> neutralUnits, Collection<?> markers) {
        virtualUnits.clear();
        neutralUnits.clear();
        markers.clear();
    }

    /** Reuse one registry key. The registry, not this slot, closes its replaced resource exactly once. */
    public static final class TextureSlot<K> {
        private final K key;
        public TextureSlot(K key) { this.key = Objects.requireNonNull(key); }
        public K key() { return key; }
        public <T> void replace(T replacement, BiConsumer<K, T> register) {
            register.accept(key, Objects.requireNonNull(replacement));
        }
    }
}
