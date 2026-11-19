package com.solegendary.reignofnether.resources.kill;
/** Accounting-only reentrancy guard. Never cancels or changes a combat call. */
public final class EconomicHurtInvocation implements AutoCloseable {
 private static final ThreadLocal<EconomicHurtInvocation> ACTIVE=new ThreadLocal<>();
 private final EconomicHurtInvocation previous;
 private int depth,attempts;
 private final net.minecraft.world.entity.Entity victim;private boolean tainted;
 private boolean closed;
 private EconomicHurtInvocation(net.minecraft.world.entity.Entity victim){this.victim=victim;previous=ACTIVE.get();ACTIVE.set(this);}
 static EconomicHurtInvocation begin(net.minecraft.world.entity.Entity victim){return new EconomicHurtInvocation(victim);}
 public static void enter(net.minecraft.world.entity.Entity victim){EconomicHurtInvocation f=ACTIVE.get();if(f!=null){if(f.depth==0)f.attempts++;else if(f.victim==victim)f.tainted=true;f.depth++;}}
 public static void leave(){EconomicHurtInvocation f=ACTIVE.get();if(f!=null&&f.depth>0)f.depth--;}
 boolean eligible(){return !closed&&!tainted&&ACTIVE.get()==this&&depth==1&&attempts==1;}
 @Override public void close(){closed=true;if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
}
