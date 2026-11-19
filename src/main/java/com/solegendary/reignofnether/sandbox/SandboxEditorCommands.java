package com.solegendary.reignofnether.sandbox;

import com.solegendary.reignofnether.ReignOfNether;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Grants editing of this map only, not vanilla command privileges, admin, or ordinary alliance control. */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class SandboxEditorCommands {
    private SandboxEditorCommands() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rts-sandbox-editor")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("grant").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> change(c.getSource(), EntityArgument.getPlayer(c, "player").getUUID(), true))))
                .then(Commands.literal("revoke").then(Commands.argument("uuid", UuidArgument.uuid())
                        .executes(c -> change(c.getSource(), UuidArgument.getUuid(c, "uuid"), false))))
                .then(Commands.literal("list").executes(c -> {
                    var editors = new TreeSet<>(SandboxEditorSaveData.get(c.getSource().getLevel()).policy.snapshot());
                    c.getSource().sendSuccess(() -> Component.literal("Sandbox editor grants: " + editors), false);
                    return editors.size();
                })));
    }

    private static int change(CommandSourceStack source, UUID id, boolean grant) {
        boolean changed = SandboxEditorSaveData.get(source.getLevel()).change(id, grant);
        if (!changed) {
            source.sendFailure(Component.literal(grant ? "Already granted, or the world's 256 grants are full."
                    : "No explicit editor grant for " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal((grant ? "Granted" : "Revoked") + " sandbox editor: " + id), true);
        return 1;
    }
}
