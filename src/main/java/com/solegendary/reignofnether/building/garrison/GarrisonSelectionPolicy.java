package com.solegendary.reignofnether.building.garrison;

import com.solegendary.reignofnether.alliance.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.buildings.shared.MineWorkPosts;
import com.solegendary.reignofnether.unit.interfaces.*;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import net.minecraft.world.entity.LivingEntity;
import java.util.Collection;

/** Selection-order-independent admission hints. Does not grant control or replace server validation. */
public final class GarrisonSelectionPolicy {
    private GarrisonSelectionPolicy() {}
    public static boolean canRightClick(LivingEntity e,BuildingPlacement p) {
        if(p==null||e.level()!=p.level||!e.isAlive()||e.isRemoved()||!(e instanceof Unit u)
                ||!u.canGarrison()||u.getGarrisonGoal()==null)return false;
        if(MineWorkPosts.isMine(p))return e instanceof VillagerUnit&&u.getOwnerName().equals(p.ownerName);
        var g=p.getBuilding().getActiveAddon(GarrisonableBuildingAddon.class);
        if(!(e instanceof RangedAttackerUnit)||g==null||!g.isGarrisonEnabled(p))return false;
        return e.level().isClientSide()?AlliancesClient.isAlliedOrOwned(u.getOwnerName(),p.ownerName)
            :AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(),p.ownerName);
    }
    public static boolean anyCanRightClick(Collection<? extends LivingEntity> selected,BuildingPlacement p) {
        return selected.stream().anyMatch(e->canRightClick(e,p));
    }
    public static boolean canEnter(Unit u) {
        return ((LivingEntity)u).isAlive()&&!((LivingEntity)u).isRemoved()&&u.canGarrison()&&u.getGarrisonGoal()!=null
            &&GarrisonableBuildingAddon.getGarrison(u)==null&&!MineWorkPosts.hasAssignment(u);
    }
    /** Before reset: reject unrelated members without clearing their existing work/attack orders. */
    public static boolean canAssign(Unit u, BuildingPlacement p) {
        var e=(LivingEntity)u;
        if(p==null||e.level()!=p.level||!e.isAlive()||e.isRemoved()||!u.canGarrison()||u.getGarrisonGoal()==null
                ||GarrisonableBuildingAddon.getGarrison(u)!=null)return false;
        if(MineWorkPosts.isMine(p))return e instanceof VillagerUnit&&!e.isPassenger()&&p.isBuilt
            &&!p.isDestroyedServerside&&!p.shouldBeDestroyed()&&u.getOwnerName().equals(p.ownerName);
        var addon=p.getBuilding().getActiveAddon(GarrisonableBuildingAddon.class);
        if(addon==null||!addon.isGarrisonEnabled(p))return false;
        return e.level().isClientSide()?AlliancesClient.isAlliedOrOwned(u.getOwnerName(),p.ownerName)
            :AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(),p.ownerName);
    }
    public static boolean canLeave(Unit u) {
        return GarrisonableBuildingAddon.getGarrison(u)!=null||MineWorkPosts.hasAssignment(u)
            ||(u.canGarrison()&&u.getGarrisonGoal()!=null&&u.getGarrisonGoal().getBuildingTarget()!=null);
    }
}
