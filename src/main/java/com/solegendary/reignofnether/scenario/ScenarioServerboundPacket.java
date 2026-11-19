package com.solegendary.reignofnether.scenario;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingClientboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.sandbox.SandboxEditorAccess;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitSyncClientboundPacket;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import static com.solegendary.reignofnether.scenario.ScenarioAction.*;

public class ScenarioServerboundPacket {

    public ScenarioAction action;
    public int roleIndex;
    public Faction faction;
    public int x;
    public int y;
    public int z;
    public boolean boolValue;
    public int intValue;
    public String strValue;

    public static void setUnitRole(int roleIndex, int unitId) {
        if (!MiscUtil.isConnected()) return;
        PacketHandler.INSTANCE.sendToServer(new ScenarioServerboundPacket(ScenarioAction.SET_UNIT_ROLE, roleIndex, 0,0,0, false, unitId, ""));
    }

    public static void setBuildingRole(int roleIndex, BlockPos bp) {
        if (!MiscUtil.isConnected()) return;
        PacketHandler.INSTANCE.sendToServer(new ScenarioServerboundPacket(ScenarioAction.SET_BUILDING_ROLE, roleIndex, bp.getX(), bp.getY(), bp.getZ(), false, 0, ""));
    }

    public static void setStartingResources(int roleIndex, ResourceName resName, int amount) {
        if (!MiscUtil.isConnected()) return;
        ScenarioAction scenarioAction = switch (resName) {
            case FOOD -> ScenarioAction.SET_ROLE_STARTING_FOOD;
            case WOOD -> ScenarioAction.SET_ROLE_STARTING_WOOD;
            case ORE -> ScenarioAction.SET_ROLE_STARTING_ORE;
            default -> null;
        };
        if (scenarioAction != null)
            PacketHandler.INSTANCE.sendToServer(new ScenarioServerboundPacket(scenarioAction, roleIndex, 0,0,0, false, amount, ""));
    }

    public static void setTeamNumber(int roleIndex, int teamNumber) {
        if (!MiscUtil.isConnected()) return;
        PacketHandler.INSTANCE.sendToServer(new ScenarioServerboundPacket(ScenarioAction.SET_ROLE_TEAM_NUMBER, roleIndex, 0,0,0, false, teamNumber, ""));
    }

    public static void setRoleFaction(int roleIndex, Faction faction) {
        if (!MiscUtil.isConnected()) return;
        PacketHandler.INSTANCE.sendToServer(new ScenarioServerboundPacket(SET_ROLE_FACTION, roleIndex, 0,0,0, false, 0, "", faction));
    }

    public static void setRoleIsNpc(int roleIndex, boolean isNpc) {
        if (!MiscUtil.isConnected()) return;
        PacketHandler.INSTANCE.sendToServer(new ScenarioServerboundPacket(ScenarioAction.SET_ROLE_NPC, roleIndex, 0,0,0, isNpc, 0, ""));
    }

    public static void setRoleName(int roleIndex, String name) {
        if (!MiscUtil.isConnected()) return;
        PacketHandler.INSTANCE.sendToServer(new ScenarioServerboundPacket(ScenarioAction.SET_ROLE_NAME, roleIndex, 0,0,0, false, 0, name));
    }

    public static void saveScenario() {
        if (!MiscUtil.isConnected()) return;
        PacketHandler.INSTANCE.sendToServer(new ScenarioServerboundPacket(ScenarioAction.SAVE_SCENARIO, 0, 0,0,0, false, 0, ""));
    }
    
    public ScenarioServerboundPacket(ScenarioAction action, int roleIndex, int x, int y, int z,
                                     boolean boolValue, int intValue, String strValue, Faction faction) {
        this.action = action;
        this.roleIndex = roleIndex;
        this.faction = faction;
        this.x = x;
        this.y = y;
        this.z = z;
        this.boolValue = boolValue;
        this.intValue = intValue;
        this.strValue = strValue;
    }

    public ScenarioServerboundPacket(ScenarioAction action, int roleIndex, int x, int y, int z,
                                   boolean boolValue, int intValue, String strValue) {
        this.action = action;
        this.roleIndex = roleIndex;
        this.x = x;
        this.y = y;
        this.z = z;
        this.boolValue = boolValue;
        this.intValue = intValue;
        this.strValue = strValue;
    }

    public ScenarioServerboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(ScenarioAction.class);
        if (this.action == SET_ROLE_FACTION) {
            this.faction = Factions.getFaction(buffer.readResourceLocation());
        }
        this.roleIndex = buffer.readInt();
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
        this.boolValue = buffer.readBoolean();
        this.intValue = buffer.readInt();
        this.strValue = buffer.readUtf();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        if (this.action == SET_ROLE_FACTION)
            buffer.writeResourceLocation(this.faction.key);
        buffer.writeInt(this.roleIndex);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeBoolean(this.boolValue);
        buffer.writeInt(this.intValue);
        buffer.writeUtf(this.strValue);
    }

    // Server-side: do not mutate anything until the editor and the particular role/target are resolved.
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            SandboxEditorAccess editor = SandboxEditorAccess.resolve(context.getSender(), context.getNetworkManager());
            if (editor == null || !ScenarioServerEvents.isActiveWorld(editor.level())) return;

            if (action == SAVE_SCENARIO) {
                ScenarioServerEvents.saveScenarioRoles(editor.level());
                return;
            }
            ScenarioRole role = ScenarioUtils.getScenarioRole(false, roleIndex);
            if (action == SET_UNIT_ROLE || action == SET_BUILDING_ROLE) {
                if (role == null && roleIndex != -1) return; // -1 is the editor's explicit clear choice
                if (action == SET_UNIT_ROLE) {
                    LivingEntity entity = editor.unit(intValue);
                    if (entity == null) return;
                    Unit unit = (Unit) entity;
                    unit.setScenarioRoleIndex(roleIndex);
                    UnitSyncClientboundPacket.sendSyncScenarioRoleIndexPacket(unit);
                } else {
                    BuildingPlacement bpl = editor.building(new BlockPos(x, y, z));
                    if (bpl == null) return;
                    bpl.scenarioRoleIndex = roleIndex;
                    BuildingClientboundPacket.syncBuilding(bpl.originPos, bpl.getBlocksPlaced(),
                            bpl.partialBlocksDestroyed, bpl.ownerName, bpl.scenarioRoleIndex);
                }
                return;
            }
            if (role == null) return;
            switch (action) {
                case SET_ROLE_STARTING_FOOD, SET_ROLE_STARTING_WOOD, SET_ROLE_STARTING_ORE -> {
                    if (intValue < 0 || intValue > 10_000) return; // existing menu limits
                    switch (action) {
                        case SET_ROLE_STARTING_FOOD -> role.startingResources.food = intValue;
                        case SET_ROLE_STARTING_WOOD -> role.startingResources.wood = intValue;
                        case SET_ROLE_STARTING_ORE -> role.startingResources.ore = intValue;
                    }
                }
                case SET_ROLE_FACTION -> {
                    if (faction == null || (faction != Factions.NEUTRAL
                            && !Factions.PLAYABLE_FACTIONS.contains(faction.key))) return;
                    role.faction = faction;
                }
                case SET_ROLE_NAME -> {
                    if (strValue == null || strValue.length() > 32) return; // matches the name input
                    role.name = strValue;
                    // input may lose focus after Close & Save, so persist the name here
                    ScenarioServerEvents.saveScenarioRoles(editor.level());
                }
                case SET_ROLE_TEAM_NUMBER -> {
                    if (intValue < 1 || intValue > ScenarioRole.COUNT) return;
                    role.teamNumber = intValue;
                    var rules = editor.level().getGameRules();
                    if (rules.getBoolean(GameRuleRegistrar.SCENARIO_MODE)) AlliancesServerEvents.applyScenarioAlliances();
                    if (rules.getBoolean(GameRuleRegistrar.COOP_MODE)) AlliancesServerEvents.applyCoopAlliances();
                }
                case SET_ROLE_NPC -> role.isNpc = boolValue;
                default -> { return; }
            }
            ScenarioRoleSaveData.getInstance(editor.level()).setDirty();
        });
        context.setPacketHandled(true);
        return true;
    }
}
