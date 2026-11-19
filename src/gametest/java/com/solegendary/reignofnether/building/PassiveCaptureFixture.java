package com.solegendary.reignofnether.building;
import net.minecraft.server.level.ServerLevel;
/** Canonical transition fixture only: does not enable automatic capture for these building definitions. */
public final class PassiveCaptureFixture {
 public static boolean capture(BuildingPlacement placement,ServerLevel level){return placement.checkAndDoCapture(level);}
}
