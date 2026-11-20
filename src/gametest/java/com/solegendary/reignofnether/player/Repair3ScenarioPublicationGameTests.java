package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.custombuilding.CustomBuilding;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.research.economy.ResearchEconomyFixtures.Peer;
import com.solegendary.reignofnether.scenario.ScenarioUtils;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@GameTestHolder("api65_repair3_scenario")
@PrefixGameTestTemplate(false)
public class Repair3ScenarioPublicationGameTests {
    private static int messageId(Object sample) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try { PacketHandler.INSTANCE.encodeMessage(sample, buf); return buf.readVarInt(); }
        finally { buf.release(); }
    }

    private static List<BuildingClientboundPacket> buildingPackets(Peer peer, int id) {
        List<BuildingClientboundPacket> out = new ArrayList<>();
        for (Object wire : peer.drain()) if (wire instanceof ClientboundCustomPayloadPacket payload) {
            FriendlyByteBuf buf = new FriendlyByteBuf(payload.getData().copy());
            try { if (buf.readVarInt() == id) out.add(new BuildingClientboundPacket(buf)); }
            finally { buf.release(); }
        }
        return out;
    }

    @GameTest(templateNamespace="api65_repair3_scenario", template="ore_mine_test_arena", batch="repair3_map_publish")
    public static void sandboxHardResetThenPublishSendsRoleToOtherConnection(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var server = level.getServer();
        h.assertTrue(server instanceof GameTestServer && "api65_repair3_scenario".equals(System.getProperty("forge.enabledGameTestNamespaces"))
                && Path.of("").toAbsolutePath().normalize().getFileName().toString().startsWith("run-resource-tech-api65_repair3_scenario-"),
                "Whole-match operations run only inside this named Forge GameTest world");
        h.assertTrue(server.getPlayerList().getPlayers().isEmpty() && PlayerServerEvents.rtsPlayers.isEmpty()
                && PlayerServerEvents.postGameRtsPlayers.isEmpty() && BuildingServerEvents.getBuildings().isEmpty()
                && UnitServerEvents.getAllUnits().isEmpty() && !level.getGameRules().getBoolean(GameRuleRegistrar.SCENARIO_MODE),
                "Fresh isolated map required; refuse to touch existing content");
        var rule = level.getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE);
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var custom = new CustomBuilding("Repair3Scene", new Vec3i(1, 1, 1), Blocks.STONE, new CompoundTag());
        BuildingPlacement building = new BuildingPlacement(custom, level, pos, Rotation.NONE, "MapEditor",
                new ArrayList<>(List.of(new BuildingBlock(pos, Blocks.STONE.defaultBlockState()))), false);
        var editor = RTSPlayer.getNewPlayer("MapEditor", Factions.NONE, 7745, 0, true, -1L, -1L);
        var role = Objects.requireNonNull(ScenarioUtils.getScenarioRole(false, 3));
        var oldBlock = level.getBlockState(pos);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        building.isBuilt = true; building.scenarioRoleIndex = role.index;
        CustomBuildingServerEvents.customBuildings.add(custom);
        BuildingServerEvents.getBuildings().add(building);
        PlayerServerEvents.rtsPlayers.add(editor);
        try (Peer peer = new Peer(level, "MapObserver")) {
            var observer = server.getPlayerList().getPlayerByName("MapObserver");
            peer.drain();
            PlayerServerEvents.resetRTS(true);
            h.assertTrue(BuildingServerEvents.getBuildings().contains(building) && level.getBlockState(pos).is(Blocks.STONE),
                    "hard reset preserved active editor map and physical building");
            int playerId = messageId(new PlayerClientboundPacket(PlayerAction.RESET_RTS_HARD, "", 0L));
            boolean sawReset = false;
            for (Object obj : peer.drain()) if (obj instanceof ClientboundCustomPayloadPacket wire) {
                var buf = new FriendlyByteBuf(wire.getData().copy());
                try { if (buf.readVarInt() == playerId) {
                    var p = new PlayerClientboundPacket(buf);
                    if (p.playerAction == PlayerAction.RESET_RTS_HARD)
                        sawReset = MatchResetPolicy.fromWire(true, p.value1, () -> false).preserveMap();
                }} finally { buf.release(); }
            }
            h.assertTrue(sawReset, "non-editor peer got explicit preserve-map policy despite having no sandbox seat");
            PlayerServerEvents.rtsPlayers.add(editor);
            PlayerServerEvents.publishScenarioMap();
            h.assertTrue(rule.get() && PlayerServerEvents.rtsPlayers.isEmpty() && building.ownerName.equals(role.name),
                    "server committed the scenario, owner and empty roster with a real building present");
            var sample = new BuildingClientboundPacket(BuildingAction.SYNC_BLOCKS_AND_OWNER, BuildingClientboundPacket.EMPTY, "", pos);
            int buildingId = messageId(sample);
            h.assertTrue(buildingPackets(peer, buildingId).stream().anyMatch(p -> p.action == BuildingAction.SYNC_BLOCKS_AND_OWNER
                    && p.buildingPos.equals(pos) && p.ownerName.equals(role.name) && p.scenarioRoleIndex == role.index),
                    "remote connection received the published owner and role at the exact origin");
            BuildingClientboundPacket.sendSnapshotTo(observer, building);
            h.assertTrue(buildingPackets(peer, buildingId).stream().anyMatch(p -> p.action == BuildingAction.PLACE_CUSTOM
                    && p.buildingPos.equals(pos) && p.ownerName.equals(role.name) && p.scenarioRoleIndex == role.index),
                    "targeted login/replacement snapshot carries the same published custom-building identity");
            h.assertTrue(BuildingSaveData.getInstance(level).buildings.stream().anyMatch(s -> s.originPos.equals(pos)
                    && s.ownerName.equals(role.name) && s.scenarioRoleIndex == role.index), "saved owner matches packet owner");
        } finally {
            PlayerServerEvents.rtsPlayers.remove(editor);
            BuildingServerEvents.getBuildings().remove(building);
            CustomBuildingServerEvents.customBuildings.remove(custom);
            rule.set(false, server);
            level.setBlockAndUpdate(pos, oldBlock);
            BuildingServerEvents.saveBuildings(level);
            PlayerServerEvents.saveRTSPlayers();
        }
        h.succeed();
    }
}
