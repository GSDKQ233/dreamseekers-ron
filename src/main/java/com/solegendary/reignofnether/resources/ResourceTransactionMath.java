package com.solegendary.reignofnether.resources;

/** Pure, overflow-safe integer arithmetic shared by new multi-resource transactions. */
public final class ResourceTransactionMath {
    public static final int BASIS_POINTS = 10_000;
    private ResourceTransactionMath() {}

    /** -1 means invalid/insufficient/overflow. No mutation occurs here. */
    public static int balanceAfter(int current, int debit, int credit) {
        if (current < 0 || debit < 0 || credit < 0 || current < debit) return -1;
        long result = (long) current - debit + credit;
        return result > Integer.MAX_VALUE ? -1 : (int) result;
    }
    public static int fraction(int amount, int basisPoints) {
        if (amount < 0 || basisPoints < 0 || basisPoints > BASIS_POINTS)
            throw new IllegalArgumentException("Invalid refund fraction");
        return (int) ((long) amount * basisPoints / BASIS_POINTS);
    }
    public static int cappedCredit(int current, int requested, int cap) {
        if (current < 0 || requested < 0 || cap < 0) throw new IllegalArgumentException("Invalid income value");
        return (int) Math.min((long) requested, Math.max(0L, (long) cap - current));
    }
    /** Checked long arithmetic supports summed F/W/O costs; remainder is in [0,9999]. */
    public static Conversion convert(long basis, int basisPoints, int previousRemainder) {
        if (basis < 0 || basisPoints < 0 || basisPoints > BASIS_POINTS
                || previousRemainder < 0 || previousRemainder >= BASIS_POINTS)
            throw new IllegalArgumentException("Invalid conversion inputs");
        long numerator = Math.addExact(Math.multiplyExact(basis, basisPoints), previousRemainder);
        return new Conversion(numerator / BASIS_POINTS, (int) (numerator % BASIS_POINTS));
    }
    public record Conversion(long amount, int remainder) {}
    /** Exact arithmetic for the save-only cargo/refund projection; live balances are not mutated. */
    public static int projectSavedBalance(int balance, int pending, long carried, long refunds) {
        java.math.BigInteger total = java.math.BigInteger.valueOf(balance)
                .add(java.math.BigInteger.valueOf(pending))
                .add(java.math.BigInteger.valueOf(carried))
                .add(java.math.BigInteger.valueOf(refunds));
        return total.max(java.math.BigInteger.ZERO)
                .min(java.math.BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
    }

}
