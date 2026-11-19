package com.solegendary.reignofnether.building.data;
import net.minecraft.nbt.CompoundTag;
public final class PassiveQuarterCodecTest {
    private static int assertions;
    private static void eq(long a,long b,String message){assertions++;if(a!=b)throw new AssertionError(message+" got="+a+" expected="+b);}
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        CompoundTag n=new CompoundTag();n.putString("owner","A");n.putInt("elapsedTicks",39);eq(read(n),156,"real legacy NBT");
        n.putInt("halfTicks",79);eq(read(n),158,"half precedes whole");n.putInt("quarterTicks",151);eq(read(n),151,"quarter precedes half");
        n.putString("quarterTicks","159");eq(read(n),0,"bad canonical does not use obsolete windfall");n.putLong("quarterTicks",Long.MAX_VALUE);eq(read(n),159,"long before narrow");n.putLong("quarterTicks",Long.MIN_VALUE);eq(read(n),0,"long negative");n.putDouble("quarterTicks",159.9);eq(read(n),0,"floating counter invalid");
        n.remove("quarterTicks");n.putLong("halfTicks",Long.MAX_VALUE);eq(read(n),158,"half long clamps before multiply");n.remove("halfTicks");n.putLong("elapsedTicks",Long.MAX_VALUE);eq(read(n),156,"whole long clamps before multiply");
        for(int i=0;i<160;i++){OreProductionState s=OreProductionState.fromSavedQuarterTicks(i,"A");CompoundTag tag=OreMineStateCodecs.writeProduction(s);eq(OreMineStateCodecs.readProduction(tag).getQuarterTicks(),i,"quarter NBT roundtrip");check(!tag.contains("halfTicks"),"do not emit ambiguous legacy half state");}
        n=new CompoundTag();n.putString("owner","A");n.putLong("overloadTicks",Long.MAX_VALUE);n.putLong("cooldownTicks",Long.MAX_VALUE);n.putInt("soulTicks",600);OreMineTraitState trait=OreMineStateCodecs.readTrait(n);eq(trait.getOverloadTicks(),400,"boost bounded");eq(trait.getCooldownTicks(),1200,"cooldown bounded");check(!trait.observe("A",0),"real decode reload does not clear boost");CompoundTag saved=OreMineStateCodecs.writeTrait(trait);check(!saved.contains("soulTicks"),"removed monster pool cannot persist or pay");
        trait.observe("",1);eq(trait.getOverloadTicks(),0,"capture clears loaded boost");eq(trait.getCooldownTicks(),1200,"capture retains loaded cooldown");
        System.out.println("PASSIVE_QUARTER_CODEC_PASS scenarios=3 assertions="+assertions);
    }
    private static int read(CompoundTag n){return OreMineStateCodecs.readProduction(n).getQuarterTicks();}
}
