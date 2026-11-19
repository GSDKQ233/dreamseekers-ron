package com.solegendary.reignofnether.faction;

import net.minecraft.server.level.ServerLevel;
import javax.annotation.Nullable;

/** Optional late-game prerequisite for placing the ordinary beacon in an active Survival match.
 * The core retains mode, rule, uniqueness, cost and actor checks. This is not an editor permission. */
public interface SurvivalBeaconFaction {
    /** Return null only when the named owner's physical prerequisites are satisfied. */
    @Nullable String getSurvivalBeaconPrerequisiteError(ServerLevel level, String owner);
}
