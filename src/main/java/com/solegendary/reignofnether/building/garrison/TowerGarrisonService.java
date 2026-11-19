package com.solegendary.reignofnether.building.garrison;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.research.economy.ResearchPlacementIdentity;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import java.util.*;

/** Management only; native goal, collision-aware seat/exit and unit eligibility remain authoritative. */
public final class TowerGarrisonService {
    private static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("dreamseekers","shadow_watchtower");
    public record Counts(int occupied,int mine,int approaching) {}
    private TowerGarrisonService() {}
    public static boolean managed(BuildingPlacement p) {
        if(p==null||!ID.equals(ReignOfNetherRegistries.BUILDING.getKey(p.getBuilding())))return false;
        var addon=p.getBuilding().getActiveAddon(GarrisonableBuildingAddon.class);
        return addon!=null&&addon.getCapacity()==3&&addon.isGarrisonEnabled(p);
    }
    public static boolean accessible(ServerPlayer actor,BuildingPlacement p) {
        return actor!=null&&actor.getServer()!=null&&actor.getServer().isSameThread()&&p!=null
            &&actor.level()==p.level&&p.level.dimension().equals(Level.OVERWORLD)
            &&PlayerServerEvents.getRTSPlayer(actor.getName().getString())!=null
            &&PlayerServerEvents.getRTSPlayer(p.ownerName)!=null
            &&AlliancesServerEvents.isAlliedOrOwned(actor.getName().getString(),p.ownerName)
            &&BuildingServerEvents.getBuildings().contains(p)&&managed(p)&&p.isBuilt
            &&!p.isDestroyedServerside&&!p.shouldBeDestroyed()&&p.getHealth()>0&&p.getCaptureRevision()>=0
            &&p.level.hasChunksAt(p.minCorner,p.maxCorner);
    }
    private static boolean live(LivingEntity e,BuildingPlacement p) {
        return e instanceof Unit&&e.isAlive()&&!e.isRemoved()&&e.level()==p.level
            &&p.level.getEntity(e.getId())==e&&p.level.hasChunkAt(e.blockPosition());
    }
    public static Counts counts(BuildingPlacement p,String viewer) {
        int occupied=0,mine=0,approaching=0;
        for(var e:List.copyOf(UnitServerEvents.getAllUnits())) {
            if(!live(e,p))continue;
            Unit u=(Unit)e;
            if(GarrisonableBuildingAddon.getGarrison(u)==p) {occupied++;if(u.getOwnerName().equals(viewer))mine++;}
            else if(u.getOwnerName().equals(viewer)&&u.canGarrison()&&u.getGarrisonGoal()!=null
                    &&u.getGarrisonGoal().getBuildingTarget()==p)approaching++;
        }
        return new Counts(occupied,mine,approaching);
    }
    public static String release(ServerPlayer actor,BuildingPlacement p,UUID instance,long capture) {
        if(!accessible(actor,p)||!ResearchPlacementIdentity.serverIdentity(p).equals(instance)
                ||p.getCaptureRevision()!=capture)return "forbidden";
        String viewer=actor.getName().getString();boolean blocked=false;
        // No client IDs; never command the host's or allies' units, even if shared control is granted.
        for(var e:List.copyOf(UnitServerEvents.getAllUnits())) {
            if(!live(e,p))continue;Unit u=(Unit)e;
            if(!viewer.equals(u.getOwnerName()))continue;
            boolean seated=GarrisonableBuildingAddon.getGarrison(u)==p;
            boolean approaching=!seated&&u.canGarrison()&&u.getGarrisonGoal()!=null&&u.getGarrisonGoal().getBuildingTarget()==p;
            if(!seated&&!approaching)continue;
            var old=e.position();
            new UnitActionItem(viewer,seated?UnitAction.UNGARRISON:UnitAction.STOP,-1,new int[]{e.getId()},p.originPos,BlockPos.ZERO).action(p.level);
            // getOnPos can lag after the native teleport; compare actual coordinates instead.
            if(seated?e.position().equals(old):u.getGarrisonGoal().getBuildingTarget()==p)blocked=true;
        }
        return blocked?"tower_partial":"tower_released";
    }
}
