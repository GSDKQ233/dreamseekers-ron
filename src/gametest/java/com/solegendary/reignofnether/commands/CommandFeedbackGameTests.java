package com.solegendary.reignofnether.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Real dispatcher and accepting command source: success suppliers must actually run. */
@GameTestHolder("reignofnether_command_feedback")
@PrefixGameTestTemplate(false)
public final class CommandFeedbackGameTests {
    private static final String OWNER = "CommandFeedbackFixture";

    private static final class Feedback implements CommandSource {
        final List<Component> messages = new ArrayList<>();
        @Override public void sendSystemMessage(Component message) {
            // Exercise server-side rendering as well as component creation.
            message.getString();
            messages.add(message);
        }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }

    private static void execute(GameTestHelper h, CommandSourceStack source, Feedback feedback,
                                String command, String key, String resource, int amount) throws Exception {
        int previous = feedback.messages.size();
        int result = h.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        h.assertTrue(result == 1, "command reports success: " + command);
        h.assertTrue(feedback.messages.size() == previous + 1, "success supplier delivered one message");
        Component message = feedback.messages.get(previous);
        h.assertTrue(message.getContents() instanceof TranslatableContents, "feedback stays translatable");
        TranslatableContents text = (TranslatableContents) message.getContents();
        h.assertTrue(text.getKey().equals(key), "correct feedback key");
        Object[] args = text.getArgs();
        h.assertTrue(args.length == 3 && args[0] instanceof Component, "resource is a nested component");
        Component name = (Component) args[0];
        h.assertTrue(name.getContents() instanceof TranslatableContents
                && ((TranslatableContents) name.getContents()).getKey().equals("resources.reignofnether." + resource),
                "resource translation is deferred to recipient locale");
        h.assertTrue(args[1].equals(amount) && args[2].equals(OWNER), "amount and recipient preserved");
    }

    @GameTest(templateNamespace="reignofnether_command_feedback", template="ore_mine_test_arena", batch="command_feedback", timeoutTicks=100)
    public static void legacyAndModernResourcesEvaluateSuccessExactlyOnce(GameTestHelper h) throws Exception {
        h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER) == null
                && ResourcesServerEvents.resourcesList.stream().noneMatch(r -> r.ownerName.equals(OWNER)),
                "isolated fixture account");
        RTSPlayer player = RTSPlayer.getNewBot(OWNER, Factions.VILLAGERS);
        Resources account = new Resources(OWNER, 100, 100, 100, 100);
        PlayerServerEvents.rtsPlayers.add(player);
        ResourcesServerEvents.resourcesList.add(account);
        Feedback feedback = new Feedback();
        CommandSourceStack source = h.getLevel().getServer().createCommandSourceStack()
                .withSource(feedback).withPermission(4).withLevel(h.getLevel());
        try {
            execute(h, source, feedback, "rtsapi-change-resources food 7 " + OWNER,
                    "commands.reignofnether.player.resource.add", "food", 7);
            h.assertTrue(account.food == 107, "legacy add applied once, no success-path retry needed");
            execute(h, source, feedback, "rtsapi player resources add wood 11 \"" + OWNER + "\"",
                    "commands.reignofnether.player.resource.add", "wood", 11);
            h.assertTrue(account.wood == 111, "modern add applied once");
            execute(h, source, feedback, "rtsapi player resources remove ore 13 \"" + OWNER + "\"",
                    "commands.reignofnether.player.resource.add", "ore", -13);
            h.assertTrue(account.ore == 87, "modern remove applied once");
            execute(h, source, feedback, "rtsapi player resources set emerald 17 \"" + OWNER + "\"",
                    "commands.reignofnether.player.resource.set", "emerald", 17);
            h.assertTrue(account.food == 107 && account.wood == 111 && account.ore == 87 && account.emerald == 17,
                    "set affects only selected resource; other balances preserved");
            System.out.println("COMMAND_FEEDBACK_RESOURCES_PASS commands=4 feedback=4");
            h.succeed();
        } finally {
            ResourcesServerEvents.resourcesList.remove(account);
            PlayerServerEvents.rtsPlayers.remove(player);
        }
    }

    @GameTest(templateNamespace="reignofnether_command_feedback", template="ore_mine_test_arena", batch="command_feedback", timeoutTicks=100)
    public static void invalidRotationIsLocalizedWithoutClientClasses(GameTestHelper h) throws Exception {
        h.assertTrue(CommandsServerEvents.parseRotation("0") == Rotation.NONE, "zero rotation unchanged");
        h.assertTrue(CommandsServerEvents.parseRotation("90") == Rotation.CLOCKWISE_90, "90 unchanged");
        h.assertTrue(CommandsServerEvents.parseRotation("180") == Rotation.CLOCKWISE_180, "180 unchanged");
        h.assertTrue(CommandsServerEvents.parseRotation("270") == Rotation.COUNTERCLOCKWISE_90, "270 unchanged");
        try {
            CommandsServerEvents.parseRotation("45");
            throw new AssertionError("invalid rotation must fail");
        } catch (CommandSyntaxException ex) {
            h.assertTrue(ex.getRawMessage() instanceof Component, "error remains translatable");
            Component error = (Component) ex.getRawMessage();
            h.assertTrue(error.getContents() instanceof TranslatableContents, "error uses translation component");
            TranslatableContents text = (TranslatableContents) error.getContents();
            h.assertTrue(text.getKey().equals("commands.reignofnether.entity.rotation.invalid")
                    && text.getArgs().length == 1 && text.getArgs()[0].equals("45"), "key and invalid input preserved");
            error.getString();
        }
        System.out.println("COMMAND_FEEDBACK_ROTATION_PASS");
        h.succeed();
    }
    private static TranslatableContents dispatchFeedback(GameTestHelper h, CommandSourceStack source,
                                                         Feedback feedback, String command, String key) throws Exception {
        int before = feedback.messages.size();
        int result = h.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        h.assertTrue(result == 1 && feedback.messages.size() == before + 1, "successful dispatch evaluates feedback once");
        Component message = feedback.messages.get(before);
        h.assertTrue(message.getContents() instanceof TranslatableContents, "translatable success message");
        TranslatableContents text = (TranslatableContents) message.getContents();
        h.assertTrue(text.getKey().equals(key), "expected feedback key");
        return text;
    }

    private static void nested(GameTestHelper h, Object argument, String key, Object value) {
        h.assertTrue(argument instanceof Component && ((Component) argument).getContents() instanceof TranslatableContents,
                "optional parameter remains a nested translation");
        TranslatableContents nested = (TranslatableContents) ((Component) argument).getContents();
        h.assertTrue(nested.getKey().equals(key) && nested.getArgs().length == 1 && nested.getArgs()[0].equals(value),
                "nested key and value preserved");
    }

    @GameTest(templateNamespace="reignofnether_command_feedback", template="ore_mine_test_arena", batch="command_feedback", timeoutTicks=100)
    public static void cameraFeedbackWithAndWithoutOptionalParameters(GameTestHelper h) throws Exception {
        String owner = "CmdCameraPeer";
        Feedback feedback = new Feedback();
        CommandSourceStack source = h.getLevel().getServer().createCommandSourceStack()
                .withSource(feedback).withPermission(4).withLevel(h.getLevel());
        // Existing embedded-channel fixture; no socket, authentication change or GUI is involved.
        try (var peer = new com.solegendary.reignofnether.research.economy.ResearchEconomyFixtures.Peer(h.getLevel(), owner)) {
            String command = "rtsapi player camera move 3 4 5 \"" + owner + "\"";
            Object[] defaults = dispatchFeedback(h, source, feedback, command,
                    "commands.reignofnether.player.move_camera").getArgs();
            h.assertTrue(defaults.length == 6 && defaults[0].equals(owner) && defaults[1].equals(3)
                    && defaults[2].equals(5), "camera recipient and coordinates preserved");
            nested(h, defaults[3], "commands.reignofnether.player.move_camera.force_pan_ticks", 20);
            h.assertTrue(defaults[4] instanceof Component && ((Component) defaults[4]).getString().isEmpty()
                    && defaults[5] instanceof Component && ((Component) defaults[5]).getString().isEmpty(),
                    "omitted lock and zoom parameters remain empty");
            Object[] full = dispatchFeedback(h, source, feedback, command + " 10 30 40",
                    "commands.reignofnether.player.move_camera").getArgs();
            nested(h, full[3], "commands.reignofnether.player.move_camera.force_pan_ticks", 30);
            nested(h, full[4], "commands.reignofnether.player.move_camera.lock_ticks", 10);
            nested(h, full[5], "commands.reignofnether.player.move_camera.zoom_level", 40);
            System.out.println("COMMAND_FEEDBACK_CAMERA_PASS commands=2 feedback=2");
            h.succeed();
        }
    }

    @GameTest(templateNamespace="reignofnether_command_feedback", template="ore_mine_test_arena", batch="command_feedback", timeoutTicks=100)
    public static void legacyUnitActionFeedbackWithNamedAndEmptyOwner(GameTestHelper h) throws Exception {
        var unit = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());
        h.assertTrue(unit != null, "registered real unit fixture");
        var pos = h.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3));
        unit.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        unit.setNoAi(true);
        unit.setOwnerName("CmdUnitOwner");
        h.assertTrue(h.getLevel().addFreshEntity(unit), "unit enters server level");
        try {
            h.assertTrue(com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits().contains(unit),
                    "real unit registration hook completed");
            Feedback feedback = new Feedback();
            CommandSourceStack source = h.getLevel().getServer().createCommandSourceStack()
                    .withSource(feedback).withPermission(4).withLevel(h.getLevel());
            var at = unit.getOnPos();
            String selection = " " + at.getX() + " " + at.getY() + " " + at.getZ();
            String tail = selection + selection + " STOP";
            Object[] named = dispatchFeedback(h, source, feedback, "rtsapi-unit-action CmdUnitOwner" + tail,
                    "commands.reignofnether.unit.action.issue.with_owner.success").getArgs();
            h.assertTrue(named.length == 3 && named[0].equals("stop") && named[1].equals(1), "action and selection count preserved");
            nested(h, named[2], "commands.reignofnether.unit.action.issue.owner", "CmdUnitOwner");
            unit.setOwnerName("");
            Object[] empty = dispatchFeedback(h, source, feedback, "rtsapi-unit-action \"\"" + tail,
                    "commands.reignofnether.unit.action.issue.with_owner.success").getArgs();
            h.assertTrue(empty[2] instanceof Component && ((Component) empty[2]).getString().isEmpty(),
                    "empty owner produces empty component");
            System.out.println("COMMAND_FEEDBACK_UNIT_ACTION_PASS commands=2 feedback=2");
            h.succeed();
        } finally {
            unit.discard();
            com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits().remove(unit);
        }
    }

}
