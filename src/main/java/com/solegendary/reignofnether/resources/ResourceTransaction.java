package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.player.PlayerServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Main-thread atomic ledger operations for the new economy only. This class does not grant
 * actor/building/research authority: callers must verify those before invoking it in the same task.
 * Epoch/revision is a compare-and-swap token, not permission. No packet or statistics are emitted here.
 * Normal saves persist one account snapshot; this does not promise crash-safe world-file transactions.
 */
public final class ResourceTransaction {
    public static final int MAX_PENDING_RECEIPTS = ResourceAccountCodec.MAX_RECEIPTS;
    private ResourceTransaction() {}

    public enum Status { APPLIED, WRONG_THREAD, INVALID_ACCOUNT, STALE_ACCOUNT, INVALID_VALUE,
        INSUFFICIENT_RESOURCES, OUTPUT_OVERFLOW, UNKNOWN_EXTENSION, RECEIPT_LIMIT, UNKNOWN_RECEIPT }

    /** Server-defined nonnegative amounts. A malformed vector is rejected without ledger mutation. */
    public record Cost(int food, int wood, int ore, int emerald, Map<ResourceLocation, Integer> extra) {
        public static final Cost ZERO = new Cost(0,0,0,0,Map.of());
        public Cost { extra = extra == null ? Map.of() : Map.copyOf(extra); }
        public Cost(int food, int wood, int ore, Map<ResourceLocation,Integer> extra) {
            this(food,wood,ore,0,extra);
        }
        public Cost(int food, int wood, int ore) { this(food,wood,ore,0,Map.of()); }
    }
    public record Result(Status status, UUID receiptId, Cost credited) {
        public boolean applied() { return status == Status.APPLIED; }
    }
    private static Result fail(Status status) { return new Result(status,null,Cost.ZERO); }

    private static Status check(MinecraftServer server, Resources account, UUID expectedEpoch, long expectedRevision) {
        if (server == null || !server.isSameThread()) return Status.WRONG_THREAD;
        if (account == null || account.ownerName == null || account.ownerName.isBlank()
                || !account.extendedStateSupported()
                || PlayerServerEvents.getRTSPlayer(account.ownerName) == null) return Status.INVALID_ACCOUNT;
        int matches = 0;
        for (Resources candidate : ResourcesServerEvents.resourcesList) {
            if (account.ownerName.equals(candidate.ownerName)) {
                if (candidate != account) return Status.INVALID_ACCOUNT;
                matches++;
            }
        }
        if (matches != 1) return Status.INVALID_ACCOUNT;
        if (expectedEpoch == null || !expectedEpoch.equals(account.accountEpoch)
                || expectedRevision != account.revision || account.revision < 0
                || account.revision == Long.MAX_VALUE) return Status.STALE_ACCOUNT;
        return null;
    }
    private static boolean valid(Cost value) {
        if (value == null || value.food < 0 || value.wood < 0 || value.ore < 0 || value.emerald < 0
                || value.extra.size() > 64) return false;
        return value.extra.entrySet().stream().allMatch(e -> e.getKey() != null && e.getValue() != null && e.getValue() >= 0);
    }
    private static Status known(Cost value) {
        for (ResourceLocation id : value.extra.keySet())
            if (ResourceExtensionRegistry.get(id) == null) return Status.UNKNOWN_EXTENSION;
        return null;
    }

    /** Exact debit and exact output: an exchange with no output room rejects the entire operation. */
    public static Result exchange(MinecraftServer server, Resources account, UUID expectedEpoch, long expectedRevision,
                                  Cost debit, Cost credit) {
        return apply(server,account,expectedEpoch,expectedRevision,debit,credit,null,false,null);
    }

    /** Reserve an immutable paid-price receipt for a queued research/upgrade. */
    public static Result pay(MinecraftServer server, Resources account, UUID expectedEpoch, long expectedRevision,
                             String purpose, Cost debit) {
        if (purpose == null || purpose.isBlank() || purpose.length() > 128) return fail(Status.INVALID_VALUE);
        return apply(server,account,expectedEpoch,expectedRevision,debit,Cost.ZERO,purpose,false,null);
    }

    /** Each component is independently clipped. Callers count only Result.credited in income statistics. */
    public static Result creditIncome(MinecraftServer server, Resources account, UUID expectedEpoch, long expectedRevision,
                                      Cost requested) {
        return creditIncome(server,account,expectedEpoch,expectedRevision,requested,Map.of());
    }

    /** Caller supplies the final remainders, clearing a stream's remainder when its output hits capacity.
     * Remainder updates and income commit in one main-thread operation, with one revision increment. */
    public static Result creditIncome(MinecraftServer server, Resources account, UUID expectedEpoch, long expectedRevision,
                                      Cost requested, Map<String,Long> remainderUpdates) {
        Status status = check(server,account,expectedEpoch,expectedRevision);
        if (status != null) return fail(status);
        if (remainderUpdates == null || remainderUpdates.size() > ResourceAccountCodec.MAX_REMAINDERS)
            return fail(Status.INVALID_VALUE);
        for (var e : remainderUpdates.entrySet())
            if (e.getKey() == null || e.getKey().isBlank() || e.getKey().length() > 128 || e.getValue() == null
                    || e.getValue() < 0 || e.getValue() >= ResourceTransactionMath.BASIS_POINTS)
                return fail(Status.INVALID_VALUE);
        var remainderKeys = new java.util.HashSet<>(account.conversionRemainders.keySet());
        remainderKeys.addAll(remainderUpdates.keySet());
        if (remainderKeys.size() > ResourceAccountCodec.MAX_REMAINDERS) return fail(Status.INVALID_VALUE);
        // Copy before balance mutation so caller-owned collections cannot change during the commit.
        Map<String,Long> immutableRemainders = Map.copyOf(remainderUpdates);
        if (!valid(requested)) return fail(Status.INVALID_VALUE);
        if ((status=known(requested)) != null) return fail(status);
        if (account.food < 0 || account.wood < 0 || account.ore < 0 || account.emerald < 0) return fail(Status.INVALID_VALUE);
        Map<ResourceLocation,Integer> extra = new HashMap<>();
        for (var e : requested.extra.entrySet()) {
            int current = account.extra.getOrDefault(e.getKey(),0);
            if (current < 0) return fail(Status.INVALID_VALUE);
            extra.put(e.getKey(), ResourceTransactionMath.cappedCredit(current,e.getValue(),ResourceExtensionRegistry.get(e.getKey()).incomeCap()));
        }
        Cost clipped = new Cost(ResourceTransactionMath.cappedCredit(account.food,requested.food,Integer.MAX_VALUE),
                ResourceTransactionMath.cappedCredit(account.wood,requested.wood,Integer.MAX_VALUE),
                ResourceTransactionMath.cappedCredit(account.ore,requested.ore,Integer.MAX_VALUE),
                ResourceTransactionMath.cappedCredit(account.emerald,requested.emerald,Integer.MAX_VALUE),extra);
        Result result = apply(server,account,expectedEpoch,expectedRevision,Cost.ZERO,clipped,null,false,null);
        if (result.applied()) account.conversionRemainders.putAll(immutableRemainders);
        return result;
    }

    /** Cancels the whole receipt once; the unrefunded fraction is forfeited, not left redeemable. */
    public static Result refund(MinecraftServer server, Resources account, UUID expectedEpoch, long expectedRevision,
                                UUID receiptId, int basisPoints) {
        Status status = check(server,account,expectedEpoch,expectedRevision);
        if (status != null) return fail(status);
        if (basisPoints < 0 || basisPoints > ResourceTransactionMath.BASIS_POINTS) return fail(Status.INVALID_VALUE);
        ResourcePaidReceipt receipt = account.paidReceipts.get(receiptId);
        if (receipt == null || !Objects.equals(receipt.payerEpoch(),account.accountEpoch)) return fail(Status.UNKNOWN_RECEIPT);
        Map<ResourceLocation,Integer> extra = new HashMap<>();
        for (var e : receipt.extra().entrySet()) extra.put(e.getKey(), ResourceTransactionMath.fraction(e.getValue(),basisPoints));
        Cost refund = new Cost(ResourceTransactionMath.fraction(receipt.food(),basisPoints),
                ResourceTransactionMath.fraction(receipt.wood(),basisPoints),
                ResourceTransactionMath.fraction(receipt.ore(),basisPoints),
                ResourceTransactionMath.fraction(receipt.emerald(),basisPoints),extra);
        return apply(server,account,expectedEpoch,expectedRevision,Cost.ZERO,refund,null,true,receiptId);
    }

    public enum ReceiptDisposition { REFUND, FORFEIT }
    /**
     * Internal server-only monotonic disposition CAS. Paid amounts, ID and payer epoch never change.
     * Callers authorize their known pending purpose; no network handler exposes arbitrary purposes.
     * A terminal refund/forfeit intent cannot be changed into the other or back into pending.
     */
    public static Result markReceiptDisposition(MinecraftServer server, Resources account, UUID expectedEpoch,
            long expectedRevision, UUID receiptId, String pendingPurpose, ReceiptDisposition disposition) {
        Status status=check(server,account,expectedEpoch,expectedRevision);if(status!=null)return fail(status);
        if(pendingPurpose==null||pendingPurpose.isBlank()||pendingPurpose.length()>112||disposition==null)return fail(Status.INVALID_VALUE);
        var receipt=account.paidReceipts.get(receiptId);
        if(receipt==null||!receipt.payerEpoch().equals(account.accountEpoch))return fail(Status.UNKNOWN_RECEIPT);
        String terminal=pendingPurpose+(disposition==ReceiptDisposition.REFUND?":refund":":forfeit");
        if(receipt.purpose().equals(terminal))return new Result(Status.APPLIED,receiptId,Cost.ZERO);
        if(!receipt.purpose().equals(pendingPurpose))return fail(Status.INVALID_VALUE);
        var replacement=new ResourcePaidReceipt(receipt.id(),receipt.payerEpoch(),terminal,
            receipt.food(),receipt.wood(),receipt.ore(),receipt.emerald(),receipt.extra());
        account.paidReceipts.put(receiptId,replacement);account.revision++;
        return new Result(Status.APPLIED,receiptId,Cost.ZERO);
    }

    /** Successfully completed purchases discard their refund right exactly once. */
    public static Result consumeReceipt(MinecraftServer server, Resources account, UUID expectedEpoch, long expectedRevision,
                                        UUID receiptId) {
        Status status = check(server,account,expectedEpoch,expectedRevision);
        if (status != null) return fail(status);
        ResourcePaidReceipt receipt = account.paidReceipts.get(receiptId);
        if (receipt == null || !Objects.equals(receipt.payerEpoch(),account.accountEpoch)) return fail(Status.UNKNOWN_RECEIPT);
        account.paidReceipts.remove(receiptId);
        account.revision++;
        return new Result(Status.APPLIED,receiptId,Cost.ZERO);
    }

    private static Result apply(MinecraftServer server, Resources account, UUID expectedEpoch, long expectedRevision,
                                Cost debit, Cost credit, String receiptPurpose, boolean refund, UUID consumedReceipt) {
        Status status = check(server,account,expectedEpoch,expectedRevision);
        if (status != null) return fail(status);
        if (!valid(debit) || !valid(credit)) return fail(Status.INVALID_VALUE);
        if ((status=known(debit)) != null || (status=known(credit)) != null) return fail(status);
        if (receiptPurpose != null && account.paidReceipts.size() >= MAX_PENDING_RECEIPTS) return fail(Status.RECEIPT_LIMIT);
        int[] current = {account.food,account.wood,account.ore,account.emerald};
        int[] costs = {debit.food,debit.wood,debit.ore,debit.emerald};
        int[] outputs = {credit.food,credit.wood,credit.ore,credit.emerald};
        int[] next = new int[4];
        for (int i=0; i<4; i++) {
            if (current[i] < 0) return fail(Status.INVALID_VALUE);
            if (current[i] < costs[i]) return fail(Status.INSUFFICIENT_RESOURCES);
            next[i] = ResourceTransactionMath.balanceAfter(current[i],costs[i],outputs[i]);
            if (next[i] < 0) return fail(Status.OUTPUT_OVERFLOW);
        }
        Map<ResourceLocation,Integer> extras = new HashMap<>();
        TreeSet<ResourceLocation> ids = new TreeSet<>(); ids.addAll(debit.extra.keySet()); ids.addAll(credit.extra.keySet());
        var storedIds = new java.util.HashSet<>(account.extra.keySet());
        storedIds.addAll(ids);
        if (storedIds.size() > ResourceAccountCodec.MAX_EXTRA) return fail(Status.INVALID_VALUE);
        for (ResourceLocation id : ids) {
            int before = account.extra.getOrDefault(id,0), cost = debit.extra.getOrDefault(id,0), output = credit.extra.getOrDefault(id,0);
            if (before < 0) return fail(Status.INVALID_VALUE);
            if (before < cost) return fail(Status.INSUFFICIENT_RESOURCES);
            int after = ResourceTransactionMath.balanceAfter(before,cost,output);
            if (after < 0 || (!refund && output > 0 && after > ResourceExtensionRegistry.get(id).incomeCap()))
                return fail(Status.OUTPUT_OVERFLOW);
            extras.put(id,after);
        }
        UUID receiptId = null;
        ResourcePaidReceipt paid = null;
        if (receiptPurpose != null) {
            do { receiptId=UUID.randomUUID(); } while(account.paidReceipts.containsKey(receiptId));
            paid = new ResourcePaidReceipt(receiptId, account.accountEpoch, receiptPurpose,
                    debit.food,debit.wood,debit.ore,debit.emerald,debit.extra);
        }
        // No callbacks, networking or mutable external state between checks and the complete commit.
        account.food=next[0]; account.wood=next[1]; account.ore=next[2]; account.emerald=next[3];
        account.extra.putAll(extras);
        if (consumedReceipt != null) account.paidReceipts.remove(consumedReceipt);
        if (paid != null) account.paidReceipts.put(receiptId,paid);
        account.revision++;
        return new Result(Status.APPLIED, receiptId == null ? consumedReceipt : receiptId, credit);
    }
}
