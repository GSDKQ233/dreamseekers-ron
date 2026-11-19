package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import com.solegendary.reignofnether.building.buildings.shared.PassiveOreBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.faction.SurvivalBeaconFaction;
import com.solegendary.reignofnether.gamemode.GameMode;
import com.solegendary.reignofnether.gamemode.GameModeServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.ServerLifecycleHooks;
import javax.annotation.Nullable;
import java.util.Set;

/** Server counterpart of the native worker build menu. Never evaluates client UI lambdas. */
public final class NativeBuildingAvailability {
    private NativeBuildingAvailability() {}
    private static final String UNAVAILABLE="building.reignofnether.native_unavailable";
    private static final String FACTION="building.reignofnether.native_wrong_faction";
    private static final String PREREQUISITE="building.reignofnether.native_prerequisite";
    private static final Set<String> EDITOR_ONLY=Set.of("capturable_beacon","end_portal","healing_fountain","neutral_transport_portal","haunted_house");
    private static final Set<String> TRANSFORM_ONLY=Set.of("portal_civilian","portal_military","portal_transport","portal_pocket");

    /** Packet-only gate: pocket portals remain valid for the separately paid item API. */
    @Nullable public static String packetError(Building building,String owner) {
        var id=ReignOfNetherRegistries.BUILDING.getKey(building);
        if(id!=null&&id.getNamespace().equals(ReignOfNether.MOD_ID)
                &&TRANSFORM_ONLY.contains(id.getPath())&&!SandboxServer.isAnyoneASandboxPlayer())return UNAVAILABLE;
        return building.getBuildAvailabilityError(owner);
    }

    @Nullable public static String error(Building building,String owner) {
        var id=ReignOfNetherRegistries.BUILDING.getKey(building);
        // Custom/addon definitions retain their explicit extension policy, and mines retain their stricter cap policy.
        if(id==null||!id.getNamespace().equals(ReignOfNether.MOD_ID)||building instanceof PassiveOreBuilding)return null;
        // Preserve the native shared-editor contract; addon strict policies do not call this fallback.
        if(SandboxServer.isAnyoneASandboxPlayer())return null;
        String key=id.getPath();
        if(EDITOR_ONLY.contains(key))return UNAVAILABLE;
        // This definition is placed by a paid item across factions, not by a normal construction packet.
        if(key.equals("portal_pocket"))return null;
        if(TRANSFORM_ONLY.contains(key))return UNAVAILABLE;
        var rts=owner==null?null:PlayerServerEvents.getRTSPlayer(owner);
        if(rts==null||rts.faction==null)return UNAVAILABLE;
        boolean beacon=building==Buildings.BEACON;
        SurvivalBeaconFaction survivalRoute=null;
        if(beacon) {
            if(!rts.faction.equals(Factions.VILLAGERS)&&!rts.faction.equals(Factions.MONSTERS)&&!rts.faction.equals(Factions.PIGLINS)){
                if(!(rts.faction instanceof SurvivalBeaconFaction route))return FACTION;
                survivalRoute=route;
            }
        } else if(!rts.faction.equals(building.getFaction()))return FACTION;
        var server=ServerLifecycleHooks.getCurrentServer();
        ServerLevel level=server==null?null:server.overworld();
        if(level==null)return UNAVAILABLE;
        if(beacon) {
            if(survivalRoute!=null && (!SurvivalServerEvents.isEnabled()
                    || GameModeServerEvents.getGameMode(level)!=GameMode.SURVIVAL))return FACTION;
            if(!level.getGameRules().getBoolean(GameRuleRegistrar.ALLOW_BEACONS))return "building.reignofnether.native_beacons_disabled";
            if(BuildingServerEvents.getBuildings().stream().anyMatch(b->b.level==level&&b instanceof BeaconPlacement))
                return "buildings.reignofnether.beacon.tooltip3";
            if(survivalRoute!=null)return survivalRoute.getSurvivalBeaconPrerequisiteError(level,owner);
        }
        if(Set.of("altar_of_darkness","infernal_portal","shrine_of_prosperity").contains(key)
                &&level.getGameRules().getInt(GameRuleRegistrar.ALLOWED_HEROES)<=0)return UNAVAILABLE;
        boolean cheat=ResearchServerEvents.playerHasCheat(owner,"modifythephasevariance");
        // Golem's UI requires the smithy even with the prerequisite cheat.
        if(key.equals("iron_golem_building"))return has(level,owner,Buildings.BLACKSMITH)
                &&(cheat||ResearchServerEvents.playerHasResearch(owner,ProductionItems.RESEARCH_GOLEM_SMITHING))?null:PREREQUISITE;
        if(cheat)return null;
        boolean allowed=switch(key) {
            case "town_centre","mausoleum","central_portal","villager_market","monster_market","piglin_market" -> true;
            case "oak_stockpile","spruce_stockpile","oak_bridge","spruce_bridge" -> has(level,owner,Buildings.TOWN_CENTRE)||has(level,owner,Buildings.MAUSOLEUM);
            case "villager_house","wheat_farm","watchtower","barracks","shrine_of_prosperity" -> has(level,owner,Buildings.TOWN_CENTRE);
            case "blacksmith","witch_hut","arcane_tower","library" -> has(level,owner,Buildings.BARRACKS);
            case "castle" -> has(level,owner,Buildings.WITCH_HUT)&&has(level,owner,Buildings.BLACKSMITH)&&has(level,owner,Buildings.ARCANE_TOWER);
            case "sculk_catalyst","pumpkin_farm","dark_watchtower","graveyard","altar_of_darkness" -> has(level,owner,Buildings.MAUSOLEUM);
            case "dungeon","spider_lair","slime_pit" -> has(level,owner,Buildings.GRAVEYARD);
            case "laboratory" -> has(level,owner,Buildings.MAUSOLEUM)&&has(level,owner,Buildings.GRAVEYARD);
            case "stronghold" -> count(level,owner,Buildings.LABORATORY,Buildings.SPIDER_LAIR,Buildings.DUNGEON,Buildings.SLIME_PIT)>=3;
            case "portal_basic","netherwart_farm","infernal_portal","blackstone_bridge" -> has(level,owner,Buildings.CENTRAL_PORTAL);
            case "bastion","hoglin_stables" -> has(level,owner,Buildings.PORTAL_BASIC);
            case "flame_sanctuary" -> has(level,owner,Buildings.HOGLIN_STABLES);
            case "wither_shrine","basalt_springs" -> has(level,owner,Buildings.BASTION);
            case "fortress" -> has(level,owner,Buildings.FLAME_SANCTUARY)&&(has(level,owner,Buildings.BASALT_SPRINGS)||has(level,owner,Buildings.WITHER_SHRINE));
            case "beacon" -> has(level,owner,Buildings.CASTLE)||has(level,owner,Buildings.STRONGHOLD)||has(level,owner,Buildings.FORTRESS);
            default -> false; // a new native definition must opt into an explicit policy
        };
        return allowed?null:PREREQUISITE;
    }
    private static boolean has(ServerLevel level,String owner,Building prerequisite) {
        return BuildingServerEvents.getBuildings().stream().anyMatch(p->p.level==level&&p.isBuilt
                &&owner.equals(p.ownerName)&&p.getBuilding().isTypeOf(prerequisite));
    }
    private static int count(ServerLevel level,String owner,Building... prerequisites) {
        int count=0;for(Building b:prerequisites)if(has(level,owner,b))count++;return count;
    }
}
