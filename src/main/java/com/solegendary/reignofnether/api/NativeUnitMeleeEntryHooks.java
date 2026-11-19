package com.solegendary.reignofnether.api;

import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;
import com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/** Optional entry witnesses for these two concrete native melee overrides only.
 * Called before their evasion/animation/sound/hurt callbacks; observers never replace damage.
 * No addon registered is a strict no-op, including on a core-only dedicated server.
 */
public final class NativeUnitMeleeEntryHooks {
    private NativeUnitMeleeEntryHooks() { }
    private static final List<BiConsumer<Mob,Entity>> OBSERVERS=new CopyOnWriteArrayList<>();
    public static void register(BiConsumer<Mob,Entity> observer) { OBSERVERS.add(Objects.requireNonNull(observer)); }
    public static void bee(BeeUnit source,Entity target) { for(var observer:OBSERVERS)observer.accept(source,target); }
    public static void ironGolem(IronGolemUnit source,Entity target) { for(var observer:OBSERVERS)observer.accept(source,target); }
}
