package com.solegendary.reignofnether.startpos;

import com.google.gson.Gson;
import com.solegendary.reignofnether.rtsmap.RTSMapInfo;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LobbySnapshotTest {
    @Test void midCountdownUsesRemainingTicksAndZeroWaitsForTheServer() {
        var clock = new StartPosCountdown();
        assertEquals(-1, clock.remaining());
        clock.sync(43);
        clock.tick();
        assertEquals(42, clock.remaining());
        for (int i = 0; i < 60; i++) clock.tick();
        assertEquals(0, clock.remaining()); // not a locally authorized start or stop
        clock.sync(-1);
        clock.tick();
        assertEquals(-1, clock.remaining());
    }

    @Test void emptySnapshotClearsStaleStateAndCanBeAppliedTwice() {
        var b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new StartPosSnapshotPacket(List.of(), StartPosCountdown.INACTIVE).encode(b);
            var decoded = new StartPosSnapshotPacket(b);
            assertFalse(b.isReadable());
            assertEquals(StartPosCountdown.INACTIVE, decoded.countdownTicks());
            List<StartPos> display = new ArrayList<>(Collections.singletonList(null));
            decoded.replaceSeats(display);
            decoded.replaceSeats(display);
            assertTrue(display.isEmpty());
        } finally { b.release(); }
    }

    @Test void sizeAndClockBoundsAreCheckedBeforeReplacingAnything() {
        var b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            b.writeByte(0);
            b.writeVarInt(StartPosSnapshotPacket.MAX_SEATS + 1);
            assertThrows(IllegalArgumentException.class, () -> new StartPosSnapshotPacket(b));
        } finally { b.release(); }
        assertThrows(IllegalArgumentException.class, () -> new StartPosSnapshotPacket(List.of(), -2));
        assertThrows(IllegalArgumentException.class, () -> new StartPosSnapshotPacket(List.of(), StartPosCountdown.DURATION + 1));
    }

    @Test void oversizedCustomModeIsNotActivatedAndItsSourceIsNotTrimmed() {
        var info = new Gson().fromJson("{\"defaultMode\":\"normal\",\"modes\":{}}", RTSMapInfo.class);
        var largeTeam = Collections.nCopies(StartPosSnapshotPacket.MAX_SEATS, 1);
        info.getModes().put("normal", List.of(largeTeam));
        info.getModes().put("tooLarge", List.of(largeTeam, List.of(2)));
        assertTrue(info.supportsMode("normal")); // >16 is supported for maps
        assertFalse(info.supportsMode("tooLarge"));
        assertEquals("normal", info.getDefaultMode());
        assertEquals(StartPosSnapshotPacket.MAX_SEATS + 1,
                info.getModes().get("tooLarge").stream().mapToInt(List::size).sum());
    }
}
