package com.dreamseekers.resonance;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.building.data.DataType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Only the paid cooldown persists. Unsupported data is opaque, never downgraded. */
public final class ResonanceState {
    public static final DataType<ResonanceState> TYPE=DataType.createRegistered(DreamseekersMod.id("resonance_state"),
        (tag,server)->read(tag),ResonanceState::write,ResonanceState::new);
    private final CompoundTag opaque;
    private long readyAt;
    public ResonanceState(){opaque=null;}
    private ResonanceState(CompoundTag opaque,long readyAt){this.opaque=opaque;this.readyAt=readyAt;}
    public static void bootstrap(){}
    public boolean supported(){return opaque==null;}
    public long readyAt(){return readyAt;}
    public static ResonanceState read(CompoundTag tag){
        boolean valid=tag.contains("schema",Tag.TAG_INT)&&tag.getInt("schema")==1
            &&tag.contains("readyAt",Tag.TAG_LONG)&&tag.getLong("readyAt")>=0;
        return valid?new ResonanceState(null,tag.getLong("readyAt")):new ResonanceState(tag.copy(),0);
    }
    public CompoundTag write(){if(opaque!=null)return opaque.copy();var tag=new CompoundTag();tag.putInt("schema",1);tag.putLong("readyAt",readyAt);return tag;}
    boolean spend(long now){if(!supported()||now<0||now<readyAt||now>Long.MAX_VALUE-ResonanceNetwork.COOLDOWN)return false;
        readyAt=now+ResonanceNetwork.COOLDOWN;return true;}
}
