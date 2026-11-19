package com.solegendary.reignofnether.unit.interfaces;

/**
 * Opt-in for flying workers whose jobs use collision-aware PathNavigation.
 * Unlike the existing high-altitude flying combat units, their ordinary worker
 * goals must run, and must not enter the ground-only RTS grid pathfinder.
 * Implementations provide their own close-work approach goals/navigation.
 */
public interface CollisionFlyingWorker extends WorkerUnit { }
