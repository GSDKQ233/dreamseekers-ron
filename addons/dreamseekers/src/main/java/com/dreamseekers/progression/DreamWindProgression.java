package com.dreamseekers.progression;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;
/** Epoch-local progression is saved in the same account compound as consumed receipts/balances. */
public final class DreamWindProgression {
 public static final String KEY="dreamseekers:wind_v1";
 public record State(boolean supported,boolean wind,int costMask,int trainMask,int researchMask) {
  public int mask(int family){return switch(family){case 0->costMask;case 1->trainMask;case 2->researchMask;default->-1;};}
  public int level(int family){return supported?Integer.bitCount(mask(family)):0;}
 }
 public static final State EMPTY=new State(true,false,0,0,0),INVALID=new State(false,false,0,0,0);
 private DreamWindProgression(){}
 private static boolean contiguous(int mask){return mask>=0&&mask<=31&&(mask&(mask+1))==0;}
 public static State read(Resources r) {
  if(r==null||!r.extendedStateSupported()||!r.addonDataSupported())return INVALID;
  if(!r.addonData.contains(KEY))return EMPTY;
  if(!r.addonData.contains(KEY,Tag.TAG_COMPOUND))return INVALID;
  var t=r.addonData.getCompound(KEY);
  if(!t.contains("schema",Tag.TAG_INT)||t.getInt("schema")!=1||!t.contains("wind",Tag.TAG_BYTE)||(t.getByte("wind")!=0&&t.getByte("wind")!=1))return INVALID;
  for(String k:new String[]{"cost","train","research"})if(!t.contains(k,Tag.TAG_INT)||!contiguous(t.getInt(k)))return INVALID;
  var s=new State(true,t.getBoolean("wind"),t.getInt("cost"),t.getInt("train"),t.getInt("research"));
  return !s.wind&&(s.costMask!=0||s.trainMask!=0||s.researchMask!=0)?INVALID:s;
 }
 private static void write(Resources r,State s) {
  var t=r.addonData.getCompound(KEY).copy();t.putInt("schema",1);t.putBoolean("wind",s.wind);t.putInt("cost",s.costMask);t.putInt("train",s.trainMask);t.putInt("research",s.researchMask);
  r.addonData.put(KEY,t);
 }
 public static State server(String owner){return read(ExtendedResourcesSync.account(owner));}
 public static State state(boolean client,String owner){return client?DreamWindClient.state(owner):server(owner);}
 public static boolean canEarnWind(ServerLevel level,String payer,UUID epoch) {
  if(!level.getServer().isSameThread())return false;var r=ExtendedResourcesSync.account(payer);
  if(r==null||!r.accountEpoch.equals(epoch)||!read(r).supported)return false;
  var probe=r.addonData.copy();if(!probe.contains(KEY)){var t=new CompoundTag();t.putInt("schema",1);t.putBoolean("wind",true);t.putInt("cost",0);t.putInt("train",0);t.putInt("research",0);probe.put(KEY,t);}
  return ResourceAddonData.valid(probe);
 }
 /** Trusted paid-completion hook, no packet exposes this award. No callbacks or revision increment. */
 public static boolean earnWind(ServerLevel level,String payer,UUID epoch) {
  if(!canEarnWind(level,payer,epoch))return false;var r=ExtendedResourcesSync.account(payer);var s=read(r);
  write(r,new State(true,true,s.costMask,s.trainMask,s.researchMask));return true;
 }
 public static boolean canAdvance(Resources r,int family,int tier){var s=read(r);return r!=null&&ResourceAddonData.valid(r.addonData)&&family>=0&&family<3&&tier>=1&&tier<=5&&s.supported&&s.wind&&s.level(family)==tier-1;}
 /** Called immediately after receipt consume in the same server task, with canAdvance prechecked. */
 public static void commitTier(Resources r,int family,int tier) {
  if(!canAdvance(r,family,tier))throw new IllegalStateException("Wind tier completion lost canonical predecessor");
  var s=read(r);int mask=(1<<tier)-1;
  write(r,new State(true,true,family==0?mask:s.costMask,family==1?mask:s.trainMask,family==2?mask:s.researchMask));
 }
 public static int trainingDiscount(boolean client,String owner){var s=state(client,owner);return s.supported&&s.wind?30+3*s.level(0):0;}
 public static ResourceCost quote(ResourceCost base,boolean client,String owner) {
  int discount=trainingDiscount(client,owner);var c=ResourceCost.Unit(0,0,0,0,base.population);
  c.food=discount(base.food,discount);c.wood=discount(base.wood,discount);c.ore=discount(base.ore,discount);c.emerald=discount(base.emerald,discount);c.ticks=base.ticks;return c;
 }
 public static int discount(int value,int percent){if(value<0||percent<0||percent>45)throw new IllegalArgumentException();return value==0?0:(int)(((long)value*(100-percent)+99)/100);}
}
