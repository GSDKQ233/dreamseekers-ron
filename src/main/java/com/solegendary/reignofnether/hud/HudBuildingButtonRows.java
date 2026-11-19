package com.solegendary.reignofnether.hud;

/** Coordinates only; the caller supplies the row reserved by its actual visible buttons. */
public final class HudBuildingButtonRows {
    private HudBuildingButtonRows() {}

    public static int sandboxRowY(int screenHeight, int frameSize, int productionRows,
                                  boolean reservedActionRow) {
        return screenHeight - frameSize * (1 + productionRows + (reservedActionRow ? 1 : 0));
    }
}
