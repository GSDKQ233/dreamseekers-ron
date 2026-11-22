// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.economy;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.research.economy.EconomyActionGate;
import com.solegendary.reignofnether.research.economy.EconomyBalanceSync;
import com.solegendary.reignofnether.research.economy.ResearchQueueSync;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Only the owner receives a research view. The public building snapshot carries appearance, not work or payment. */
@Mod.EventBusSubscriber(modid = DreamseekersMod.MOD_ID)
public final class ResourceResearchPackets {
    public static final UUID ZERO = new UUID(0, 0);
    private static final String PROTOCOL = "1";
    // Pure wire validation/tests do not bootstrap Forge. Registration initializes this once on both sides.
    private static final class Network {
        static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
                DreamseekersMod.id("resource_research"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    }
    public static SimpleChannel channel() { return Network.CHANNEL; }
    static final int QUERY_TICKS = 20;
    private static final Map<Connection, Poll> POLLS = new WeakHashMap<>();
    private record Poll(ServerLevel level, UUID epoch, long tick) {}
    private static boolean registered;
    private ResourceResearchPackets() {}

    public static void register() {
        if (registered) return;
        registered = true;
        channel().messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Request::encode).decoder(Request::new).consumerMainThread(Request::handle).add();
        channel().messageBuilder(Snapshot.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Snapshot::encode).decoder(Snapshot::new).consumerMainThread(Snapshot::handle).add();
    }

    public enum Action { QUERY, START, CANCEL }

    /** The instance comes from the native PLACE; view only echoes this opening of this client's panel. */
    public record Target(ResourceLocation dimension, BlockPos origin, UUID instance, UUID view, long capture, long revision) {
        public Target { if (origin != null) origin = origin.immutable(); }
        private Target(FriendlyByteBuf b) {
            this(b.readResourceLocation(), b.readBlockPos(), b.readUUID(), b.readUUID(), b.readLong(), b.readLong());
        }
        private void encode(FriendlyByteBuf b) {
            b.writeResourceLocation(dimension); b.writeBlockPos(origin); b.writeUUID(instance); b.writeUUID(view);
            b.writeLong(capture); b.writeLong(revision);
        }
        public boolean valid() {
            return dimension != null && origin != null && id(instance) && id(view) && capture >= 0 && revision >= 0;
        }
        /** A private observation is for one live object and panel, not simply the block position. */
        public boolean matchesView(ResourceLocation level, BlockPos pos, UUID nativeInstance, UUID panel) {
            return dimension.equals(level) && origin.equals(pos) && instance.equals(nativeInstance) && view.equals(panel);
        }
        private Target observed(ResourceBuildingResearch.View state) {
            return new Target(dimension, origin, state.instance(), view, state.capture(), state.revision());
        }
    }
    private static boolean id(UUID value) {
        return value != null && !ZERO.equals(value);
    }

    public record Request(Action action, Target target, UUID session, UUID epoch, long sequence) {
        public Request(FriendlyByteBuf b) {
            this(b.readEnum(Action.class), new Target(b), b.readUUID(), b.readUUID(), b.readLong());
        }
        public void encode(FriendlyByteBuf b) {
            b.writeEnum(action); target.encode(b); b.writeUUID(session); b.writeUUID(epoch); b.writeLong(sequence);
        }
        public boolean valid() {
            if (action == null || target == null || !target.valid() || session == null || epoch == null) return false;
            if (action == Action.QUERY)
                return sequence == 0 && target.capture == 0 && target.revision == 0 && ZERO.equals(session) == ZERO.equals(epoch);
            return id(session) && id(epoch) && sequence > 0;
        }
        public void handle(Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            execute(context.getSender(), context.getNetworkManager()); // registered with consumerMainThread
        }
        public void execute(ServerPlayer actor, Connection connection) {
            if (!valid() || actor == null || connection == null || !actor.serverLevel().getServer().isSameThread()) return;
            var token = EconomyActionGate.query(actor, connection);
            if (token == null || actor.serverLevel().getServer().getPlayerList().getPlayer(actor.getUUID()) != actor) return;
            boolean sameSession = token.session().equals(session) && token.epoch().equals(epoch);
            if (action == Action.QUERY) {
                boolean bootstrap = ZERO.equals(session);
                if ((!bootstrap && !sameSession) || !actor.serverLevel().dimension().location().equals(target.dimension)
                        || !mayPoll(actor.serverLevel(), connection, token.epoch())) return;
                var state = inspect(actor, resolveOwn(actor, target), target);
                if (state != null) {
                    reply(actor, token, target, state, "ready", 0);
                    // The first query also obtains the current private balances/epoch. Routine progress does not republish them.
                    if (bootstrap) EconomyBalanceSync.send(actor);
                }
                return;
            }
            if (!sameSession) {
                // Echo only the caller's obsolete token, never a new token or view. The client admits bare ACKs
                // only against an already-established matching session, so this cannot retire a new purchase.
                reply(actor, new EconomyActionGate.Token(session, epoch), target, null, "stale_session", sequence);
                return;
            }
            BuildingPlacement[] attempted = {null};
            String status = EconomyActionGate.execute(actor, connection, session, epoch, sequence, fingerprint(action, target), account -> {
                var host = resolveOwn(actor, target);
                if (host == null) return "forbidden";
                attempted[0] = host;
                return action == Action.START
                        ? ResourceBuildingResearch.start(actor, host, target.instance, target.capture, target.revision)
                        : ResourceBuildingResearch.cancel(actor, host, target.instance, target.capture, target.revision);
            });
            boolean changed = attempted[0] != null && ("started".equals(status) || "cancelled_refunded".equals(status)
                    || "refund_pending".equals(status));
            var currentToken = EconomyActionGate.query(actor, connection);
            if (!token.equals(currentToken)) return; // State callbacks may have changed the paying account.
            var state = changed ? inspect(actor, attempted[0], target) : null;
            if (state != null && state.capture() != target.capture) state = null;
            reply(actor, token, target, state, status, sequence);
            if (changed) EconomyBalanceSync.send(actor);
        }
    }

    private record Intent(Action action, Target target) {}
    static EconomyActionGate.Fingerprint fingerprint(Action action, Target target) {
        return new EconomyActionGate.Fingerprint("resource_building_research", new Intent(action, target));
    }

    /** Resolve from this actor's dimension only; never look up remote levels or load chunks. An ambiguous origin fails closed. */
    private static BuildingPlacement resolveOwn(ServerPlayer actor, Target target) {
        var level = actor.serverLevel();
        if (!level.dimension().location().equals(target.dimension)) return null;
        BuildingPlacement found = null;
        for (var p : BuildingServerEvents.getBuildings()) {
            if (p.level != level || !p.originPos.equals(target.origin)) continue;
            if (found != null) return null;
            found = p;
        }
        return found != null && actor.getGameProfile().getName().equals(found.ownerName)
                && ResourceBuildingResearch.supports(found) && target.instance.equals(ResourceBuildingResearch.instance(found))
                ? found : null;
    }
    private static ResourceBuildingResearch.View inspect(ServerPlayer actor, BuildingPlacement host, Target target) {
        if (host == null) return null;
        var state = ResourceBuildingResearch.view(actor, host);
        return state != null && target.instance.equals(state.instance()) ? state : null;
    }
    private static boolean mayPoll(ServerLevel level, Connection connection, UUID epoch) {
        long now = level.getGameTime();
        var old = POLLS.get(connection);
        if (now < 0 || old != null && old.level == level && old.epoch.equals(epoch)
                && old.tick <= now && now - old.tick < QUERY_TICKS) return false;
        POLLS.put(connection, new Poll(level, epoch, now));
        return true;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { POLLS.clear(); }

    public record Progress(int remaining, boolean upgraded) {
        public boolean valid() {
            return remaining >= 0 && remaining <= ResourceBuildingResearch.DURATION && (!upgraded || remaining == 0);
        }
    }

    /** Null progress is only an ACK. It never establishes a session, refreshes a view or discloses another host's data. */
    public record Snapshot(UUID stream, long order, UUID session, UUID epoch, String owner, long completed,
                           String status, Target target, Progress progress) {
        public Snapshot(FriendlyByteBuf b) {
            this(b.readUUID(), b.readLong(), b.readUUID(), b.readUUID(), b.readUtf(256), b.readLong(),
                    b.readUtf(96), new Target(b), b.readBoolean() ? new Progress(b.readVarInt(), b.readBoolean()) : null);
        }
        public void encode(FriendlyByteBuf b) {
            b.writeUUID(stream); b.writeLong(order); b.writeUUID(session); b.writeUUID(epoch); b.writeUtf(owner, 256);
            b.writeLong(completed); b.writeUtf(status, 96); target.encode(b);
            b.writeBoolean(progress != null);
            if (progress != null) { b.writeVarInt(progress.remaining); b.writeBoolean(progress.upgraded); }
        }
        public boolean valid() {
            return id(stream) && order >= 0 && id(session) && id(epoch) && owner != null && !owner.isBlank()
                    && owner.length() <= 256 && completed >= 0 && status != null && !status.isBlank() && status.length() <= 96
                    && (completed != 0 || progress != null && "ready".equals(status)) && target != null && target.valid()
                    && (progress == null || progress.valid());
        }
        public void handle(Supplier<NetworkEvent.Context> supplier) {
            Connection source = supplier.get().getNetworkManager();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ResourceResearchClient.accept(this, source));
        }
    }
    private static void reply(ServerPlayer actor, EconomyActionGate.Token token, Target target,
                              ResourceBuildingResearch.View state, String status, long completed) {
        long order = ResearchQueueSync.nextSequence();
        if (order < 0) return;
        var message = new Snapshot(ResearchQueueSync.snapshotSession(), order, token.session(), token.epoch(),
                actor.getGameProfile().getName(), completed, status, state == null ? target : target.observed(state),
                state == null ? null : new Progress(state.remaining(), state.upgraded()));
        if (message.valid()) channel().send(PacketDistributor.PLAYER.with(() -> actor), message);
    }
}
