package com.solegendary.reignofnether.building.production;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
/** Integer hundredths are authoritative; public float ticksLeft is only the legacy queue mirror. */
public final class ProductionWorkRates {
 private ProductionWorkRates(){}
 public static int percent(ProductionPlacement p,ProductionItem item){var event=new ProductionWorkEvent(p,item);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);return Math.max(1,Math.min(10000,event.percent));}
 public static void advance(ProductionPlacement p,ActiveProduction a,int baseMultiplier){
  if(!Float.isFinite(a.ticksLeft)||a.ticksLeft<0||baseMultiplier<1)return;
  // Explicit server test/admin progress writes and reconnect snapshots rebase the mirror once.
  if(a.workHundredths<0||Float.compare(a.workMirror,a.ticksLeft)!=0)a.workHundredths=Math.round((double)a.ticksLeft*100d);
  long amount=(long)percent(p,a.item)*baseMultiplier;
  if(p.getLevel().isClientSide())amount=Math.max(1,Math.round(amount*(com.solegendary.reignofnether.debug.RtsDebugClientEvents.getCappedTPS()/20d)));
  a.workHundredths=Math.max(0,a.workHundredths-amount);a.ticksLeft=a.workHundredths/100f;a.workMirror=a.ticksLeft;
 }
 public static int estimatedSeconds(int baseTicks,int percent){return (int)(((long)baseTicks*100+20L*percent-1)/(20L*percent));}
}
