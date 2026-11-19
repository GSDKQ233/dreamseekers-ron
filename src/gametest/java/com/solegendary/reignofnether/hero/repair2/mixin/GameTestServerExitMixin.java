package com.solegendary.reignofnether.hero.repair2.mixin;

import com.solegendary.reignofnether.hero.repair2.cycle.HeroWorldCycle;
import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Test-only. Vanilla otherwise exits the entire JVM after every GameTest server, unlike singleplayer.
 * This is after Forge ServerStopped, saving, closing worlds, releasing locks and checking test failures.
 * No game-state code, save/load method, event dispatch or crash exit is intercepted. */
@Mixin(GameTestServer.class)
public abstract class GameTestServerExitMixin {
    @Redirect(method = {"onServerExit()V", "m_6988_()V"}, remap = false,
            at = @At(value = "INVOKE", target = "Ljava/lang/System;exit(I)V", remap = false), require = 1, allow = 1)
    private void repair2$continueIsolatedWorlds(int exitCode) {
        HeroWorldCycle.afterServerExit((GameTestServer) (Object) this, exitCode);
    }
}
