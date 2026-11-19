// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
/** Legacy reservation boundary: requests remain unavailable and never create a debit, grant or effect.
 * This class is not a resource ledger; active shadow-energy transactions use the shared economy services.
 * Registration, NBT flags and zero-valued quotes cannot authorize effects through this entry point.
 */
public final class ShadowEnergyGate {
    private ShadowEnergyGate() { }
    public record Reservation(UUID requestId,UUID payer,long positiveAmount) { }
    public enum Result { INVALID_REQUEST,NOT_PAYER,UNAVAILABLE }
    public static Result reserve(ServerPlayer sender,Reservation request) {
        if(sender==null||request==null||request.requestId()==null||request.payer()==null||request.positiveAmount()<=0
                ||!sender.serverLevel().getServer().isSameThread())return Result.INVALID_REQUEST;
        if(!sender.getUUID().equals(request.payer()))return Result.NOT_PAYER;
        return Result.UNAVAILABLE; // Always zero debit/grant/reservation/effect; no synthetic receipt.
    }
}
