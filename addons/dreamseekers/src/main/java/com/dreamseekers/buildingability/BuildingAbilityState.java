// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.building.data.DataType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.EnumMap;
import java.util.UUID;

/** Per-real-building paid cooldowns only. Unknown state is inert and retained byte-semantically. */
public final class BuildingAbilityState {
    public static final int SCHEMA=1;
    public static final DataType<BuildingAbilityState> TYPE=DataType.createRegistered(
        DreamseekersMod.id("building_ability_state"),(tag,server)->read(tag),BuildingAbilityState::write,BuildingAbilityState::new);
    private final CompoundTag opaque;
    private final UUID instance;
    private final EnumMap<BuildingAbility,Long> readyAt;
    private long revision;
    public BuildingAbilityState(){this(null,UUID.randomUUID(),0,new EnumMap<>(BuildingAbility.class));}
    private BuildingAbilityState(CompoundTag opaque,UUID instance,long revision,EnumMap<BuildingAbility,Long> readyAt){
        this.opaque=opaque;this.instance=instance;this.revision=revision;this.readyAt=readyAt;
    }
    public static void bootstrap(){}
    public boolean supported(){return opaque==null;}
    public UUID instance(){return instance;}
    public long revision(){return revision;}
    public long readyAt(BuildingAbility ability){return readyAt.getOrDefault(ability,0L);}
    public int cooldown(BuildingAbility ability,long now){
        if(!supported()||now<0)return 0;
        long due=readyAt(ability);return due<=now?0:(int)Math.min(Integer.MAX_VALUE,due-now);
    }
    public boolean canSpend(BuildingAbility ability,long now){return supported()&&ability!=null&&ability.paid()
        &&revision>=0&&revision<Long.MAX_VALUE&&now>=0&&now>=readyAt(ability)&&now<=Long.MAX_VALUE-ability.cooldownTicks();}
    /** No callback occurs between resource debit and this commit, on the same server thread. */
    boolean spend(BuildingAbility ability,long now){
        if(!canSpend(ability,now))return false;
        readyAt.put(ability,now+ability.cooldownTicks());revision++;return true;
    }
    public CompoundTag write(){
        if(opaque!=null)return opaque.copy();
        var tag=new CompoundTag();tag.putInt("schema",SCHEMA);tag.putUUID("instance",instance);tag.putLong("revision",revision);
        var cooldowns=new CompoundTag();for(var a:BuildingAbility.values())cooldowns.putLong(a.path(),readyAt(a));
        tag.put("readyAt",cooldowns);return tag;
    }
    public static BuildingAbilityState read(CompoundTag tag){
        boolean valid=tag.contains("schema",Tag.TAG_INT)&&tag.getInt("schema")==SCHEMA&&tag.hasUUID("instance")
            &&!new UUID(0,0).equals(tag.hasUUID("instance")?tag.getUUID("instance"):null)
            &&tag.contains("revision",Tag.TAG_LONG)&&tag.getLong("revision")>=0&&tag.contains("readyAt",Tag.TAG_COMPOUND);
        var values=new EnumMap<BuildingAbility,Long>(BuildingAbility.class);
        var cooldowns=tag.getCompound("readyAt");
        if(cooldowns.getAllKeys().size()!=BuildingAbility.values().length)valid=false;
        for(var a:BuildingAbility.values()){
            if(!cooldowns.contains(a.path(),Tag.TAG_LONG)||cooldowns.getLong(a.path())<0)valid=false;
            values.put(a,cooldowns.getLong(a.path()));
        }
        return valid?new BuildingAbilityState(null,tag.getUUID("instance"),tag.getLong("revision"),values)
            :new BuildingAbilityState(tag.copy(),null,0,new EnumMap<>(BuildingAbility.class));
    }
}
