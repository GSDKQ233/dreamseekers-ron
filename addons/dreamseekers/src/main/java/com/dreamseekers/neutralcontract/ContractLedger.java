package com.dreamseekers.neutralcontract;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Durable origin tombstones and live slots. Unloading is not death; future data is opaque and disabled. */
public final class ContractLedger extends SavedData {
    public static final String NAME="dreamseekers_neutral_contracts";
    public static final int LIMIT=16384;
    public record Entry(UUID origin,UUID body,String owner,UUID account) {}
    private final Map<UUID,Entry> entries=new LinkedHashMap<>();
    private final Set<UUID> consumed=new LinkedHashSet<>();
    private CompoundTag opaque;
    public ContractLedger(){}
    public static ContractLedger get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(ContractLedger::load,ContractLedger::new,NAME);}
    public boolean supported(){return opaque==null;}
    public boolean consumed(UUID origin){return consumed.contains(origin);}
    public int count(String owner,UUID account){return (int)entries.values().stream().filter(e->e.owner.equals(owner)&&e.account.equals(account)).count();}
    public Entry entry(UUID body){return entries.get(body);}
    public boolean canCommit(UUID origin){return supported()&&!consumed.contains(origin)&&consumed.size()<LIMIT;}
    public boolean canCommit(UUID origin,UUID body){return canCommit(origin)&&!entries.containsKey(body);}
    /** Pure server commit after all callbacks and payment checks; caller has reserved this exact origin. */
    public void commit(UUID origin,UUID body,String owner,UUID account){
        if(!canCommit(origin)||entries.containsKey(body)||owner==null||owner.isBlank())throw new IllegalStateException("Invalid neutral contract commit");
        consumed.add(origin);entries.put(body,new Entry(origin,body,owner,account));setDirty();
    }
    public void died(UUID body){if(supported()&&entries.remove(body)!=null)setDirty();}
    public static ContractLedger load(CompoundTag raw){
        var out=new ContractLedger();
        try {
            if(!raw.contains("schema",Tag.TAG_INT)||raw.getInt("schema")!=1||!raw.contains("origins",Tag.TAG_LIST)||!raw.contains("entries",Tag.TAG_LIST))throw new IllegalArgumentException();
            var rawOrigins=(ListTag)raw.get("origins");var rawEntries=(ListTag)raw.get("entries");
            if((!rawOrigins.isEmpty()&&rawOrigins.getElementType()!=Tag.TAG_COMPOUND)||(!rawEntries.isEmpty()&&rawEntries.getElementType()!=Tag.TAG_COMPOUND))throw new IllegalArgumentException();
            var origins=raw.getList("origins",Tag.TAG_COMPOUND);var list=raw.getList("entries",Tag.TAG_COMPOUND);var liveOrigins=new HashSet<UUID>();
            if(origins.size()>LIMIT||list.size()>LIMIT)throw new IllegalArgumentException();
            for(int i=0;i<origins.size();i++){var tag=origins.getCompound(i);if(!tag.hasUUID("id")||!out.consumed.add(tag.getUUID("id")))throw new IllegalArgumentException();}
            for(int i=0;i<list.size();i++){var tag=list.getCompound(i);
                if(!tag.hasUUID("origin")||!tag.hasUUID("body")||!tag.hasUUID("account")||!tag.contains("owner",Tag.TAG_STRING))throw new IllegalArgumentException();
                var entry=new Entry(tag.getUUID("origin"),tag.getUUID("body"),tag.getString("owner"),tag.getUUID("account"));
                if(entry.owner.isBlank()||entry.owner.length()>256||!out.consumed.contains(entry.origin)||!liveOrigins.add(entry.origin)||out.entries.putIfAbsent(entry.body,entry)!=null)throw new IllegalArgumentException();
            }
        } catch(RuntimeException bad){out.entries.clear();out.consumed.clear();out.opaque=raw.copy();}
        return out;
    }
    @Override public CompoundTag save(CompoundTag tag){
        if(opaque!=null)return opaque.copy();
        tag.putInt("schema",1);var origins=new ListTag();for(var id:consumed){var t=new CompoundTag();t.putUUID("id",id);origins.add(t);}tag.put("origins",origins);
        var list=new ListTag();for(var e:entries.values()){var t=new CompoundTag();t.putUUID("origin",e.origin);t.putUUID("body",e.body);t.putString("owner",e.owner);t.putUUID("account",e.account);list.add(t);}tag.put("entries",list);return tag;
    }
}
