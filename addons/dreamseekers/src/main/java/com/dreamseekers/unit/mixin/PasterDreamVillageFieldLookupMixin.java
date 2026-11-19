// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Repairs only the original PasterDream beta0.4.2 village helper's Mojmap field strings.
 * Keeps its registration timing, element, projection and weight unchanged; does not register twice.
 */
@Mixin(targets = "net.pasterdream.world.VillageBuildingHelper", remap = false)
public abstract class PasterDreamVillageFieldLookupMixin {
    @Redirect(method = "registerBuild", at = @At(value = "INVOKE",
            target = "Ljava/lang/Class;getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;"),
            remap = false, require = 0)
    private static Field dreamseekers$mappedVillagePoolField(Class<?> owner, String name)
            throws NoSuchFieldException {
        if (owner == StructureTemplatePool.class) {
            if ("templates".equals(name))
                return ObfuscationReflectionHelper.findField(owner, "f_210560_");
            if ("rawTemplates".equals(name))
                return ObfuscationReflectionHelper.findField(owner, "f_210559_");
        }
        // A future helper using already-correct names retains its own behavior.
        return owner.getDeclaredField(name);
    }

    /** Datapack-decoded pools use an immutable raw list. The original helper silently catches
     * clear/addAll failures, so make only its raw list mutable before its own insertion runs.
     */
    @Redirect(method = "registerBuild", at = @At(value = "INVOKE",
            target = "Ljava/lang/reflect/Field;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            remap = false, require = 0)
    private static Object dreamseekers$mutableVillageRawTemplates(Field field, Object owner)
            throws IllegalAccessException {
        Object value = field.get(owner);
        if (owner instanceof StructureTemplatePool
                && field.equals(ObfuscationReflectionHelper.findField(
                        StructureTemplatePool.class, "f_210559_"))
                && value instanceof List<?> entries) {
            var mutableEntries = new ArrayList<>(entries);
            field.set(owner, mutableEntries);
            return mutableEntries;
        }
        return value;
    }
}
