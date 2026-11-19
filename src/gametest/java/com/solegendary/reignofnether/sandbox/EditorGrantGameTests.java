package com.solegendary.reignofnether.sandbox;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.economy.ResearchEconomyFixtures.Peer;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("api65_editor_worlds") @PrefixGameTestTemplate(false)
public final class EditorGrantGameTests {
    @GameTest(templateNamespace="api65_editor_worlds", template="ore_mine_test_arena", batch="editor_grant")
    public static void invitedEditorAndReadOnlyPeer(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var server = level.getServer();
        try (var peer = new Peer(level, "MapMaker_A65"); var reader = new Peer(level, "MapReader_A65")) {
            var actor = server.getPlayerList().getPlayerByName("MapMaker_A65");
            var observer = server.getPlayerList().getPlayerByName("MapReader_A65");
            var seat = RTSPlayer.getNewScenarioPlayer("MapMaker_A65", Factions.NONE, actor.getId(), -1);
            seat.playerUuid = actor.getUUID();
            PlayerServerEvents.rtsPlayers.add(seat);
            var mob = EntityRegistrar.VILLAGER_UNIT.get().create(level);
            var pos = h.absolutePos(new BlockPos(1, 2, 1));
            var target = pos.east();
            var source = server.createCommandSourceStack();
            var settings = SandboxEditorSaveData.get(level);
            boolean hadGrant = settings.policy.snapshot().contains(actor.getUUID());
            try {
                h.assertTrue(!hadGrant && !actor.hasPermissions(4), "fresh non-admin map editor");
                h.assertTrue(SandboxEditorAccess.resolve(actor, actor.connection.connection) == null,
                        "a self-selected seat alone is not a map grant");
                int granted = server.getCommands().performPrefixedCommand(source, "rts-sandbox-editor grant MapMaker_A65");
                h.assertTrue(granted == 1 && SandboxEditorAccess.resolve(actor, actor.connection.connection) != null,
                        "normal admin grant enables precisely the bound editor");
                h.assertTrue(SandboxEditorAccess.resolve(observer, observer.connection.connection) == null,
                        "read-only observer is checked through the same service (no mutation request sent)");
                mob.moveTo(pos, 0, 0);
                h.assertTrue(level.addFreshEntity(mob), "normal unit entered this level");
                peer.send(new SandboxServerboundPacket(SandboxAction.SET_ANCHOR, "", "", target, new int[]{mob.getId()}));
                h.assertTrue(target.equals(((Unit) mob).getAnchor()), "invited editor's legitimate change reached the unit");
                int revoked = server.getCommands().performPrefixedCommand(source, "rts-sandbox-editor revoke " + actor.getUUID());
                h.assertTrue(revoked == 1 && SandboxEditorAccess.resolve(actor, actor.connection.connection) == null,
                        "revocation takes effect on the same connection, with no mutation request");
            } finally {
                if (!hadGrant) settings.change(actor.getUUID(), false);
                if (mob != null) mob.discard();
                PlayerServerEvents.rtsPlayers.remove(seat);
            }
        }
        h.succeed();
    }
}
