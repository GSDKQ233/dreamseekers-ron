package com.solegendary.reignofnether.taskscheduler;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.ArrayList;
import java.util.List;

/** Tick-delayed work is owned by the submitting server session, never a later integrated world. */
public class TaskSchedulerServerEvents {
    private static final List<ScheduledTask> tasks = new ArrayList<>();
    private record ScheduledTask(MinecraftServer owner, int ticksRemaining, Runnable task) {}

    public static void schedule(int delayTicks, Runnable task) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || task == null) return;
        tasks.add(new ScheduledTask(server, Math.max(0, delayTicks), task));
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        tasks.removeIf(task -> task.owner() != event.getServer());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        tasks.removeIf(task -> task.owner() == event.getServer());
    }
    @SubscribeEvent public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.getServer().isSameThread()) return;
        List<ScheduledTask> current = List.copyOf(tasks);
        tasks.clear(); // Tasks submitted by a callback belong to the next tick, with no iterator invalidation.
        for (ScheduledTask task : current) {
            if (task.owner() != event.getServer()) continue;
            if (task.ticksRemaining() <= 0) task.task().run();
            else tasks.add(new ScheduledTask(task.owner(), task.ticksRemaining() - 1, task.task()));
        }
    }
}
