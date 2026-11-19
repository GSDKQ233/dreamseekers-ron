// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.aaroncos;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** World-scoped authoritative bonds. Entity NBT cannot independently create a paid bond. */
public final class HandBondData extends SavedData {
    public static final int MAX_BONDS = 1024;
    public static final int MAX_RETIRED=16384;
    final Set<UUID> retired=new HashSet<>();
    public boolean retired(UUID uuid){return retired.contains(uuid);}
    public static final class Bond {
        public final UUID id, accountEpoch;
        public final String owner;
        public UUID left, right;
        public int missing; // 0 = both present, 1 = left combat death, 2 = right combat death
        public long readyAt, lastCombat;
        public Bond(UUID id, String owner, UUID epoch, UUID left, UUID right) {
            this.id=id;this.owner=owner;this.accountEpoch=epoch;this.left=left;this.right=right;
        }
        public boolean contains(UUID entity) { return left.equals(entity)||right.equals(entity); }
        public UUID other(UUID entity) { return left.equals(entity)?right:left; }
    }
    public boolean supported=true;
    private CompoundTag futureData;
    final LinkedHashMap<UUID,Bond> bonds = new LinkedHashMap<>();
    public static HandBondData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HandBondData::load, HandBondData::new, "dreamseekers-hand-bonds");
    }
    public static HandBondData load(CompoundTag tag) {
        HandBondData data=new HandBondData(); Set<UUID> used=new HashSet<>();
        if(tag.getInt("version")!=1){data.supported=false;data.futureData=tag.copy();return data;}
        for(Tag raw:tag.getList("retired",Tag.TAG_INT_ARRAY)){
            if(data.retired.size()>=MAX_RETIRED)break;
            try{data.retired.add(NbtUtils.loadUUID(raw));}catch(IllegalArgumentException ignored){}
        }
        ListTag list=tag.getList("bonds",Tag.TAG_COMPOUND);
        for(Tag raw:list) {
            if(data.bonds.size()>=MAX_BONDS)break;
            CompoundTag t=(CompoundTag)raw;
            if(!t.hasUUID("id")||!t.hasUUID("left")||!t.hasUUID("right")||!t.hasUUID("epoch")||t.getString("owner").isBlank())continue;
            UUID id=t.getUUID("id"),left=t.getUUID("left"),right=t.getUUID("right");int missing=t.getInt("missing");
            if(left.equals(right)||used.contains(left)||used.contains(right)||data.bonds.containsKey(id)||missing<0||missing>2)continue;
            Bond b=new Bond(id,t.getString("owner"),t.getUUID("epoch"),left,right);
            b.missing=missing;b.readyAt=Math.max(0,t.getLong("readyAt"));b.lastCombat=Math.max(0,t.getLong("lastCombat"));
            data.bonds.put(id,b);used.add(left);used.add(right);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        if(!supported)return futureData.copy();
        tag.putInt("version",1);ListTag list=new ListTag();
        for(Bond b:bonds.values()) {
            CompoundTag t=new CompoundTag();t.putUUID("id",b.id);t.putString("owner",b.owner);t.putUUID("epoch",b.accountEpoch);
            t.putUUID("left",b.left);t.putUUID("right",b.right);t.putInt("missing",b.missing);t.putLong("readyAt",b.readyAt);t.putLong("lastCombat",b.lastCombat);list.add(t);
        }
        tag.put("bonds",list);ListTag retiredList=new ListTag();for(UUID uuid:retired)retiredList.add(NbtUtils.createUUID(uuid));tag.put("retired",retiredList);return tag;
    }
    public Bond forEntity(UUID entity) { return bonds.values().stream().filter(b->b.contains(entity)).findFirst().orElse(null); }
    public int size(){return bonds.size();}
    void add(Bond bond){bonds.put(bond.id,bond);setDirty();}
    void remove(Bond bond){if(bonds.remove(bond.id)!=null)setDirty();}
}
