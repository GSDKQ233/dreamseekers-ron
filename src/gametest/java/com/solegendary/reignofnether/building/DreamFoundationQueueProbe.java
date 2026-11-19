package com.solegendary.reignofnether.building;
/** Read-only test probe; no construction shortcut or production exposure. */
public final class DreamFoundationQueueProbe {
 private DreamFoundationQueueProbe(){}
 public static int queued(BuildingPlacement placement){return placement.blockPlaceQueue.size();}
}
