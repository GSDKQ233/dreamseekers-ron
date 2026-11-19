package com.solegendary.reignofnether.api;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * Serializes writes to our four non-intrusive registries during parallel mod construction.
 * Values and their class initializers run at the call site, BEFORE this lock is acquired.
 * No mod factory, supplier or registration callback is invoked while holding the lock.
 *
 * Both entry points are covered: register reads nextId before calling registerMapping.
 * The shared lock also serializes Forge's markKnown updates across our registries.
 * This does not turn vanilla registries or concurrent iteration into a thread-safe API;
 * consumers must still wait for the normal mod-construction lifecycle barrier.
 */
final class ConstructionSafeMappedRegistry<T> extends MappedRegistry<T> {
    private static final Object REGISTRATION_LOCK = new Object();

    ConstructionSafeMappedRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle) {
        super(key, lifecycle, false);
    }

    @Override
    public Holder.Reference<T> register(ResourceKey<T> key, T value, Lifecycle lifecycle) {
        synchronized (REGISTRATION_LOCK) {
            return super.register(key, value, lifecycle);
        }
    }

    @Override
    public Holder.Reference<T> registerMapping(int id, ResourceKey<T> key, T value, Lifecycle lifecycle) {
        synchronized (REGISTRATION_LOCK) {
            return super.registerMapping(id, key, value, lifecycle);
        }
    }
}
