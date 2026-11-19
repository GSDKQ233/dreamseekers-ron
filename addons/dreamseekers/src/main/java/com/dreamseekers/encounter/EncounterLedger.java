package com.dreamseekers.encounter;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Global live registrations; chunk unloading never refunds capacity. Unknown formats are preserved. */
public final class EncounterLedger extends SavedData {
    public static final String NAME="dreamseekers_map_encounters";
    public static final int LIMIT=128;
    private final Map<UUID,NeutralEncounters.Kind> entries=new HashMap<>();
    private CompoundTag opaque;
    public static EncounterLedger get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(EncounterLedger::load,EncounterLedger::new,NAME);}
    public static EncounterLedger load(CompoundTag raw){var d=new EncounterLedger();try{
        if(!raw.contains("schema",Tag.TAG_INT)||raw.getInt("schema")!=1||!raw.contains("entries",Tag.TAG_LIST))throw new IllegalArgumentException();
        ListTag list=raw.getList("entries",Tag.TAG_COMPOUND);if(list.size()>LIMIT||list.size()!=((ListTag)raw.get("entries")).size())throw new IllegalArgumentException();
        for(Tag t:list){CompoundTag e=(CompoundTag)t;if(!e.hasUUID("id")||!e.contains("kind",Tag.TAG_STRING))throw new IllegalArgumentException();
            if(d.entries.put(e.getUUID("id"),NeutralEncounters.Kind.valueOf(e.getString("kind")))!=null)throw new IllegalArgumentException();}
    }catch(RuntimeException ex){d.entries.clear();d.opaque=raw.copy();}return d;}
    public boolean supported(){return opaque==null;}
    public int count(){return entries.size();}
    public boolean matches(UUID id,NeutralEncounters.Kind kind){return supported()&&entries.get(id)==kind;}
    boolean register(UUID id,NeutralEncounters.Kind kind){if(!supported()||count()>=LIMIT||entries.containsKey(id))return false;entries.put(id,kind);setDirty();return true;}
    void release(UUID id,NeutralEncounters.Kind kind){if(matches(id,kind)){entries.remove(id);setDirty();}}
    @Override public CompoundTag save(CompoundTag out){if(opaque!=null)return opaque.copy();out.putInt("schema",1);ListTag list=new ListTag();entries.forEach((id,kind)->{CompoundTag e=new CompoundTag();e.putUUID("id",id);e.putString("kind",kind.name());list.add(e);});out.put("entries",list);return out;}
}
