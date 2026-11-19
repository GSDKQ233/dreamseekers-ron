package com.solegendary.reignofnether.api;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/** Optional client presentation/input hooks. No handler grants server command authority. */
public final class BuildingUnitOrderHooks {
    private static final List<BiPredicate<BuildingPlacement,List<LivingEntity>>> ORDERS = new CopyOnWriteArrayList<>();
    private static final List<BiFunction<BuildingPlacement,List<AbilityButton>,List<AbilityButton>>> BUTTONS = new CopyOnWriteArrayList<>();
    private BuildingUnitOrderHooks() {}
    public static void registerRightClick(BiPredicate<BuildingPlacement,List<LivingEntity>> hook) {
        ORDERS.add(Objects.requireNonNull(hook));
    }
    public static void registerButtons(BiFunction<BuildingPlacement,List<AbilityButton>,List<AbilityButton>> hook) {
        BUTTONS.add(Objects.requireNonNull(hook));
    }
    public static boolean rightClick(BuildingPlacement building,List<LivingEntity> selected) {
        if(building==null||!building.getLevel().isClientSide()||selected.isEmpty())return false;
        var snapshot=List.copyOf(selected);
        for(var hook:ORDERS)if(hook.test(building,snapshot))return true;
        return false;
    }
    public static List<AbilityButton> append(BuildingPlacement building,List<AbilityButton> original) {
        if(building==null||!building.getLevel().isClientSide())return original;
        var result=original;
        for(var hook:BUTTONS)result=Objects.requireNonNull(hook.apply(building,result));
        return result;
    }
}
