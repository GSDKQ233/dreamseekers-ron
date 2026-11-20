// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import com.dreamseekers.function.DreamWatchtowerPlacement;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;

/** Native occupied defense only; empty tower never damages anything. */
public final class DreamWatchtower extends DreamScaffoldBuilding implements GarrisonableBuildingAddon {
    public DreamWatchtower() {
        super(DreamBuildingSpec.SHADOW_WATCHTOWER);
        DreamWatchtowerPlacement.bootstrap();
        setActiveAddon(GarrisonableBuildingAddon.class,this,true);
    }
    @Override public ArrayList<BuildingBlock> getRelativeBlockData(LevelAccessor level){return DreamWatchtowerPlacement.template(2,true);}
    @Override public BuildingPlacement createBuildingPlacement(Level level,BlockPos pos,Rotation rotation,String owner){return new DreamWatchtowerPlacement(this,level,pos,rotation,owner);}
    @Override public BuildingPlacement createRestoredBuildingPlacement(Level level,BlockPos pos,Rotation rotation,String owner,DataStorage saved){return new DreamWatchtowerPlacement(this,level,pos,rotation,owner,saved);}
    @Override public boolean acceptsInitialStructureSnapshot(CompoundTag snapshot){return DreamWatchtowerPlacement.validSnapshot(snapshot);}
    @Override public String getUpgradedName(BuildingPlacement placement) {
        if(placement instanceof DreamWatchtowerPlacement tower&&tower.modelRevision()==1)
            return net.minecraft.network.chat.Component.translatable("buildings.dreamseekers.shadow_watchtower.legacy").getString();
        return super.getUpgradedName(placement);
    }
    @Override public int getCapacity(){return 3;}
    @Override public int getAttackRange(){return 24;}
    @Override public int getExternalAttackRangeBonus(){return 10;}
    /** No anonymous teleport destination: this tower requires the actual incoming body's clearance. */
    @Override public BlockPos getEntryPosition(BuildingPlacement placement){return null;}
    @Override public BlockPos getExitPosition(BuildingPlacement placement){return null;}
    @Override public boolean isGarrisonEnabled(BuildingPlacement p) {
        var buildings=p.getLevel().isClientSide()?BuildingClientEvents.getBuildings():BuildingServerEvents.getBuildings();
        return p instanceof DreamWatchtowerPlacement tower&&tower.modelRevision()==2&&tower.stateSnapshot().supported()
            &&buildings.contains(p)&&p.isBuilt&&!p.isDestroyedServerside&&!p.shouldBeDestroyed()
            &&p.getHealth()>0&&!p.ownerName.isBlank()&&p.getLevel().hasChunksAt(p.minCorner,p.maxCorner);
    }
    @Override public boolean isFull(BuildingPlacement placement) {
        if(!isGarrisonEnabled(placement))return true;
        int count=0;
        for(var e:placement.getLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(placement.minCorner,placement.maxCorner.offset(1,1,1)),e->e instanceof Unit&&e.isAlive()))
            if(e.getOnPos().getY()>placement.originPos.getY()+2&&++count>=getCapacity())return true;
        return false;
    }
    private boolean live(BuildingPlacement p) {
        return !p.getLevel().isClientSide()&&p.getLevel().getServer().isSameThread()&&isGarrisonEnabled(p);
    }
    @Override public BlockPos getEntryPosition(BuildingPlacement p,LivingEntity occupant) {
        if(!live(p)||occupant.level()!=p.getLevel()||!occupant.isAlive()||occupant.isRemoved()
                ||!(occupant instanceof Unit unit)||!unit.canGarrison()
                ||!AlliancesServerEvents.isAlliedOrOwned(unit.getOwnerName(),p.ownerName)||isFull(p))return null;
        for(int x:new int[]{3,2,4}) {
            // Front row keeps short ranged units clear of the real platform lip after gravity settles.
            BlockPos seat=local(p,x,8,5);
            if(safe(p,occupant,seat))return seat;
        }
        return null;
    }
    @Override public BlockPos getExitPosition(BuildingPlacement p,LivingEntity occupant) {
        if(!live(p)||occupant.level()!=p.getLevel()||!occupant.isAlive()||occupant.isRemoved()
                ||!(occupant instanceof Unit)||GarrisonableBuildingAddon.getGarrison((Unit)occupant)!=p)return null;
        for(int x:new int[]{3,2,4}) {
            BlockPos exit=local(p,x,1,1);
            if(safe(p,occupant,exit))return exit;
        }
        return null;
    }
    private static BlockPos local(BuildingPlacement p,int x,int y,int z){return new BlockPos(x,y,z).rotate(p.rotation).offset(p.originPos).above();}
    private static boolean safe(BuildingPlacement p,LivingEntity occupant,BlockPos at) {
        Level level=p.getLevel();
        AABB teleported=occupant.getBoundingBox().move(at.getX()+.5-occupant.getX(),at.getY()+.5-occupant.getY(),at.getZ()+.5-occupant.getZ());
        AABB settled=teleported.move(0,-.5,0);
        AABB entire=teleported.minmax(settled);
        if(!level.hasChunksAt(BlockPos.containing(entire.minX,entire.minY-1,entire.minZ),BlockPos.containing(entire.maxX,entire.maxY,entire.maxZ))
                ||entire.minY<level.getMinBuildHeight()||entire.maxY>level.getMaxBuildHeight()
                ||!level.getWorldBorder().isWithinBounds(entire)||level.containsAnyLiquid(entire)
                ||!level.noCollision(occupant,entire)
                ||!level.getEntities(occupant,entire,e->e instanceof LivingEntity&&e.isAlive()&&!e.isRemoved()).isEmpty())return false;
        // Require full support for the body's actual footprint, not merely an air cell or central post.
        int minX=(int)Math.floor(settled.minX+1e-6),maxX=(int)Math.floor(settled.maxX-1e-6);
        int minZ=(int)Math.floor(settled.minZ+1e-6),maxZ=(int)Math.floor(settled.maxZ-1e-6);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            BlockPos floor=new BlockPos(x,at.getY()-1,z);
            if(!level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP))return false;
        }
        return true;
    }
}
