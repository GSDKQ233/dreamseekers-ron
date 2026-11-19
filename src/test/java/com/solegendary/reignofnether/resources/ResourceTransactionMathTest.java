package com.solegendary.reignofnether.resources;

/** No Minecraft dependency; exhaustive boundary combinations and deterministic arithmetic fuzzing. */
public final class ResourceTransactionMathTest {
    private static int assertions;
    private static void check(boolean b,String why){assertions++;if(!b)throw new AssertionError(why);}
    private static void eq(long actual,long expected,String why){check(actual==expected,why+" expected="+expected+" actual="+actual);}
    private static void rejected(Runnable call){boolean rejected=false;try{call.run();}catch(IllegalArgumentException|ArithmeticException e){rejected=true;}check(rejected,"invalid/overflow input must reject");}
    public static void main(String[] args){
        int[] edges={Integer.MIN_VALUE,-1,0,1,19,20,100,1000,Integer.MAX_VALUE-1,Integer.MAX_VALUE};
        for(int current:edges)for(int debit:edges)for(int credit:edges){long next=(long)current-debit+credit;int expected=current<0||debit<0||credit<0||current<debit||next>Integer.MAX_VALUE?-1:(int)next;eq(ResourceTransactionMath.balanceAfter(current,debit,credit),expected,"atomic scalar boundary");}
        java.util.Random rng=new java.util.Random(0x615AC);
        for(int i=0;i<100000;i++){int current=rng.nextInt(Integer.MAX_VALUE),debit=rng.nextInt(Integer.MAX_VALUE),credit=rng.nextInt(Integer.MAX_VALUE);long next=(long)current-debit+credit;eq(ResourceTransactionMath.balanceAfter(current,debit,credit),current<debit||next>Integer.MAX_VALUE?-1:next,"deterministic int arithmetic");}
        for(int bps:new int[]{0,1,500,1000,9999,10000})for(int amount:new int[]{0,1,19,9999,10000,Integer.MAX_VALUE})eq(ResourceTransactionMath.fraction(amount,bps),(long)amount*bps/10000,"refund rounding");
        int remainder=0;long total=0;for(int i=0;i<10;i++){var c=ResourceTransactionMath.convert(1,1000,remainder);total+=c.amount();remainder=c.remainder();}eq(total,1,"tiny values accumulate exactly");eq(remainder,0,"ten tenths leaves no residue");
        for(int i=0;i<10000;i++){long basis=rng.nextInt(Integer.MAX_VALUE);int bps=rng.nextInt(10001),previous=rng.nextInt(10000);var c=ResourceTransactionMath.convert(basis,bps,previous);eq(c.amount()*10000+c.remainder(),basis*bps+previous,"conversion conservation");check(c.remainder()>=0&&c.remainder()<10000,"remainder bound");}
        eq(ResourceTransactionMath.cappedCredit(999,20,1000),1,"cap clips exact space");eq(ResourceTransactionMath.cappedCredit(1100,20,1000),0,"overcap refunded account receives no new income");eq(ResourceTransactionMath.cappedCredit(Integer.MAX_VALUE-1,Integer.MAX_VALUE,Integer.MAX_VALUE),1,"income cap avoids overflow");
        rejected(()->ResourceTransactionMath.convert(Long.MAX_VALUE,10000,0));rejected(()->ResourceTransactionMath.convert(Long.MAX_VALUE,1,9999));rejected(()->ResourceTransactionMath.convert(-1,1,0));rejected(()->ResourceTransactionMath.convert(1,10001,0));rejected(()->ResourceTransactionMath.convert(1,1,10000));rejected(()->ResourceTransactionMath.fraction(-1,10000));rejected(()->ResourceTransactionMath.fraction(1,10001));rejected(()->ResourceTransactionMath.cappedCredit(-1,1,1000));
        System.out.println("RESOURCE_TRANSACTION_MATH_PASS scenarios=7 assertions="+assertions);
    }
}
