package com.solegendary.reignofnether.cursor;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Real immutable AABB/ray semantics plus call-site wiring; not interactive cursor acceptance. */
class CursorSelectionAabbTest {
    @Test void upperToleranceIsPresentOnlyInTheReturnedImmutableBox() {
        for (double extension : new double[]{.6f, .75d}) {
            AABB base = new AABB(0, 0, 0, 1, .5, 1);
            AABB enlarged = base.setMaxY(base.maxY + extension);
            assertNotSame(base, enlarged);
            assertEquals(.5, base.maxY);
            assertEquals(.5 + extension, enlarged.maxY);
            Vec3 from = new Vec3(-1, .5 + extension / 2, .5);
            Vec3 to = new Vec3(2, .5 + extension / 2, .5);
            assertTrue(base.clip(from, to).isEmpty());
            assertTrue(enlarged.clip(from, to).isPresent());
        }
    }

    @Test void bothActualCursorCallSitesKeepTheReturnedBox() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/cursor/CursorClientEvents.java"));
        assertTrue(source.contains("entityaabb = entityaabb.setMaxY(entityaabb.maxY + 0.6f);"));
        assertTrue(source.contains("entityaabb = entityaabb.setMaxY(entityaabb.maxY + 0.75d);"));
    }
}
