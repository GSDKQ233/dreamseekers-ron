package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.faction.Factions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** One complete, immutable lobby image for one newly connected player. Never a client request. */
public final class StartPosSnapshotPacket {
    // Native start blocks allow 16, but custom map modes can have considerably more seats.
    public static final int MAX_SEATS = 1024;
    private static final int MAX_OWNER_CHARS = 64;
    // At most 200k UTF-16 units across all rows (well below the 1 MiB S2C payload limit).
    private static final int MAX_TEXT_CHARS = 200_000;

    private record Seat(BlockPos pos, ResourceLocation faction, String owner, int color,
                        boolean enabled, boolean ready, boolean fromStartBlock) {
        Seat(StartPos p) {
            this(p.pos.immutable(), p.faction.key, p.playerName, p.colorId, p.enabled, p.ready, p.isFromStartBlock);
        }

        StartPos materialize() {
            StartPos seat = new StartPos(pos, Factions.getFaction(faction), owner, color);
            seat.enabled = enabled;
            seat.ready = ready;
            seat.isFromStartBlock = fromStartBlock;
            return seat;
        }

        int textChars() {
            int factionChars = faction.toString().length();
            if (owner.length() > MAX_OWNER_CHARS || factionChars > 32767)
                throw new IllegalArgumentException("Lobby seat text too long");
            return owner.length() + factionChars;
        }
    }

    private final List<Seat> seats;
    private final int countdownTicks;

    private static int checkSize(int size) {
        if (size < 0 || size > MAX_SEATS)
            throw new IllegalArgumentException("Lobby roster exceeds " + MAX_SEATS + " seats");
        return size;
    }

    private static int addText(int used, Seat seat) {
        int next = used + seat.textChars();
        if (next > MAX_TEXT_CHARS) throw new IllegalArgumentException("Lobby roster text budget exceeded");
        return next;
    }

    StartPosSnapshotPacket(List<StartPos> source, int countdownTicks) {
        this.countdownTicks = StartPosCountdown.check(countdownTicks);
        List<Seat> copy = new ArrayList<>(checkSize(source.size()));
        int text = 0;
        for (StartPos pos : source) {
            Seat seat = new Seat(pos);
            text = addText(text, seat);
            copy.add(seat);
        }
        seats = List.copyOf(copy);
    }

    public StartPosSnapshotPacket(FriendlyByteBuf b) {
        countdownTicks = StartPosCountdown.check(b.readByte());
        int count = checkSize(b.readVarInt());
        List<Seat> copy = new ArrayList<>(count);
        int text = 0;
        for (int i = 0; i < count; i++) {
            Seat seat = new Seat(b.readBlockPos(), b.readResourceLocation(), b.readUtf(MAX_OWNER_CHARS),
                    b.readInt(), b.readBoolean(), b.readBoolean(), b.readBoolean());
            text = addText(text, seat);
            copy.add(seat);
        }
        seats = List.copyOf(copy);
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(countdownTicks);
        b.writeVarInt(seats.size());
        for (Seat seat : seats) {
            b.writeBlockPos(seat.pos());
            b.writeResourceLocation(seat.faction());
            b.writeUtf(seat.owner(), MAX_OWNER_CHARS);
            b.writeInt(seat.color());
            b.writeBoolean(seat.enabled());
            b.writeBoolean(seat.ready());
            b.writeBoolean(seat.fromStartBlock());
        }
    }

    int countdownTicks() { return countdownTicks; }

    /** Build first, then replace on the game thread; an empty image also removes stale seats. */
    void replaceSeats(List<StartPos> target) {
        List<StartPos> replacement = new ArrayList<>(seats.size());
        for (Seat seat : seats) replacement.add(seat.materialize());
        target.clear();
        target.addAll(replacement);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // Registered with consumerMainThread; apply in stream order with the following live deltas.
        var context = ctx.get();
        var connection = context.getNetworkManager();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> StartPosClientEvents.receiveSnapshot(this, connection));
        context.setPacketHandled(true);
    }
}
