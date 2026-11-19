package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.*;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.neutral.Beacon;
import com.solegendary.reignofnether.building.buildings.piglins.AbstractPortal;
import com.solegendary.reignofnether.building.buildings.placements.*;
import com.solegendary.reignofnether.building.buildings.villagers.*;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/** Absolute native presentation state. Separate from opt-in addon instance/geometry snapshots. */
public final class NativeBuildingSync {
    private NativeBuildingSync() {}
    public static CompoundTag snapshot(BuildingPlacement p) {
        var id=ReignOfNetherRegistries.BUILDING.getKey(p.getBuilding());
        CompoundTag tag=new CompoundTag();
        if(id==null||!id.getNamespace().equals(ReignOfNether.MOD_ID))return tag;
        tag.putInt("version",1);tag.putString("type",id.toString());tag.putString("rotation",p.rotation.name());
        tag.putInt("upgrade",p.getUpgradeLevel());tag.putBoolean("built",p.isBuilt);
        tag.putString("owner",p.ownerName);tag.putInt("role",p.scenarioRoleIndex);
        tag.putInt("blocks",p.getBlocksPlaced());tag.putInt("highest",p.highestBlockCountReached);
        tag.putDouble("partial",p.partialBlocksDestroyed);
        if(p instanceof PortalPlacement portal){tag.putString("portal",portal.getPortalType().name());tag.putLong("destination",portal.hasDestination()?portal.destination.asLong():BlockPos.ZERO.asLong());}
        if(p instanceof BeaconPlacement beacon){tag.putBoolean("active",beacon.isBeaconActive());tag.putString("aura",BeaconPlacement.getActionForMobEffect(beacon.getAuraEffect()).name());}
        Ability auto=null;
        if(p.getBuilding() instanceof Library)auto=p.getDataStorage().getData(Library.AUTO_CAST_ENCHANT);
        else if(p.getBuilding() instanceof Blacksmith)auto=p.getDataStorage().getData(Blacksmith.AUTO_CAST_EQUIP);
        tag.putString("autocast",auto==null?UnitAction.NONE.name():auto.action.name());
        return tag;
    }
    public static Building definition(CompoundTag tag) {
        if(tag==null||tag.getInt("version")!=1||tag.getInt("upgrade")<0||tag.getInt("upgrade")>64
                ||tag.getInt("blocks")<0||tag.getInt("highest")<0||!Double.isFinite(tag.getDouble("partial")))return null;
        var id=ResourceLocation.tryParse(tag.getString("type"));
        if(id==null||!id.getNamespace().equals(ReignOfNether.MOD_ID))return null;
        try{Rotation.valueOf(tag.getString("rotation"));UnitAction.valueOf(tag.getString("autocast"));
            if(tag.contains("portal"))PortalPlacement.PortalType.valueOf(tag.getString("portal"));
            if(tag.contains("aura"))UnitAction.valueOf(tag.getString("aura"));
        }catch(IllegalArgumentException invalid){return null;}
        return ReignOfNetherRegistries.BUILDING.get(id);
    }
    public static boolean compatible(BuildingPlacement p,CompoundTag tag) {
        Building target=definition(tag);
        return target!=null&&p.rotation.name().equals(tag.getString("rotation"))
                &&(p.getBuilding()==target||(p instanceof PortalPlacement&&target instanceof AbstractPortal&&p.isCapitol==target.isCapitol));
    }
    /** No action callbacks, sounds, resources, world writes or construction work are replayed. */
    public static boolean apply(BuildingPlacement p,CompoundTag tag) {
        if(!compatible(p,tag))return false;
        Building target=definition(tag);
        int upgrade=tag.getInt("upgrade");
        boolean geometry=p.getBuilding()!=target||p.getUpgradeLevel()!=upgrade;
        if(p.getBuilding()!=target){p.setBuilding(target);p.updateButtons();}
        if(geometry){
            String structure=target.getUpgradedStructureName(upgrade);
            if(p instanceof BeaconPlacement)structure=switch(upgrade){case 1->Beacon.structureNameT1;case 2->Beacon.structureNameT2;case 3->Beacon.structureNameT3;case 4->Beacon.structureNameT4;case 5->Beacon.structureNameT5;default->Beacon.structureName;};
            var relative=BuildingBlockData.getBuildingBlocksFromNbt(structure,p.level);
            p.restoreGeometry(BuildingUtils.getAbsoluteBlockData(relative,p.level,p.originPos,p.rotation),
                    Math.max(2,tag.getInt("highest")),Math.max(0,Math.min(Math.nextDown(1d),tag.getDouble("partial"))));
            if(p instanceof BeaconPlacement beacon)for(BuildingBlock block:p.getBlocks())
                if(block.getBlockState().is(Blocks.BEACON))beacon.beaconPos=block.getBlockPos();
        }
        p.ownerName=tag.getString("owner");p.scenarioRoleIndex=tag.getInt("role");p.isBuilt=tag.getBoolean("built");
        p.highestBlockCountReached=Math.max(2,tag.getInt("highest"));p.partialBlocksDestroyed=tag.getDouble("partial");p.setServerBlocksPlaced(tag.getInt("blocks"));
        if(p instanceof PortalPlacement portal&&tag.contains("destination"))portal.destination=BlockPos.of(tag.getLong("destination"));
        if(p instanceof BeaconPlacement beacon&&tag.contains("aura"))
            beacon.restoreSyncedAura(BeaconPlacement.getMobEffectForAction(UnitAction.valueOf(tag.getString("aura"))),tag.getBoolean("active"));
        UnitAction action=UnitAction.valueOf(tag.getString("autocast"));
        Ability auto=p.getAbilities().stream().filter(a->a.action==action).findFirst().orElse(null);
        if(target instanceof Library)p.getDataStorage().setData(Library.AUTO_CAST_ENCHANT,auto instanceof EnchantAbility e?e:null);
        else if(target instanceof Blacksmith)p.getDataStorage().setData(Blacksmith.AUTO_CAST_EQUIP,auto instanceof EquipAbility e?e:null);
        return true;
    }
}
