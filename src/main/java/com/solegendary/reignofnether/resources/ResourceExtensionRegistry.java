package com.solegendary.reignofnether.resources;

import net.minecraft.resources.ResourceLocation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Common-side definitions only. Addons own their IDs, rules and rendering assets. */
public final class ResourceExtensionRegistry {
    public record Definition(ResourceLocation id, int incomeCap, String translationKey, ResourceLocation icon) {
        public Definition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(translationKey, "translationKey");
            Objects.requireNonNull(icon, "icon");
            if (incomeCap < 1 || translationKey.isBlank()) throw new IllegalArgumentException("Invalid resource definition");
        }
    }
    private static final Map<ResourceLocation, Definition> DEFINITIONS = new LinkedHashMap<>();
    private ResourceExtensionRegistry() {}
    public static synchronized Definition register(ResourceLocation id, int incomeCap, String translationKey, ResourceLocation icon) {
        Definition definition = new Definition(id, incomeCap, translationKey, icon);
        Definition old = DEFINITIONS.get(id);
        if (old != null) {
            if (!old.equals(definition)) throw new IllegalStateException("Conflicting resource definition: " + id);
            return old;
        }
        if (DEFINITIONS.size() >= ResourceAccountCodec.MAX_EXTRA) throw new IllegalStateException("Too many resource extensions");
        DEFINITIONS.put(id, definition);
        return definition;
    }
    public static synchronized Definition get(ResourceLocation id) { return DEFINITIONS.get(id); }
    public static synchronized Map<ResourceLocation, Definition> definitions() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(DEFINITIONS));
    }
}
