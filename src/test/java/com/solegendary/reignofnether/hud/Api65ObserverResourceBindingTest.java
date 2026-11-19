package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Source-wiring guard only; no client GUI/renderer is launched by this test. */
class Api65ObserverResourceBindingTest {
    @Test void eachResourceRenderResolvesTheCurrentOwnerAccount() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/hud/playerdisplay/ObserverPlayerDisplay.java"));
        assertFalse(source.contains("final Resources resources"),"display must not freeze the replaced SYNC instance");
        int begin=source.indexOf("private void renderResource(");
        int end=source.indexOf("private void renderSupplyDetail(",begin);
        String render=source.substring(begin,end);
        assertTrue(render.contains("Resources resources = ResourcesClientEvents.getResources(this.playerName)"),
                "resolve from current registry during resource rendering, not only in constructor");
        for(String field:new String[]{"food","wood","ore"})
            assertTrue(render.contains("resources == null ? \"—\" : String.valueOf(resources."+field+")"),
                    "missing current account is a safe unknown, not stale value or NPE: "+field);
    }
}
