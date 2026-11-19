package com.dreamseekers.economy;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.data.DataType;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Construction-only payment snapshot; current definition changes never reprice a paid placement. */
public final class DreamConstructionPayment {
    public static final DataType<State> STATE=DataType.createRegistered(
            ResourceLocation.fromNamespaceAndPath("dreamseekers","construction_payment"),
            (tag,server)->State.read(tag),State::write,State::legacy);
    private DreamConstructionPayment() { }
    public static void bootstrap() { }
    private static ResourceCost zero(){return ResourceCost.Building(0,0,0,0);}

    /** Successful-placement payment observation only; never called by restoration. */
    public static void record(BuildingPlacement p,int f,int w,int o) {
        if(p.getLevel().isClientSide()||!p.getLevel().getServer().isSameThread())return;
        State previous=p.getConstructionPaymentStorage().getData(STATE);
        if(!previous.missing)return; // duplicate notifications cannot overwrite a paid/claimed/opaque snapshot
        Resources account=ExtendedResourcesSync.account(p.ownerName);
        CompoundTag t=new CompoundTag();t.putInt("schema",1);
        t.putInt("food",Math.max(0,f));t.putInt("wood",Math.max(0,w));t.putInt("ore",Math.max(0,o));
        t.putString("payer",p.ownerName);t.putBoolean("claimed",false);
        if(account!=null)t.putUUID("epoch",account.accountEpoch);
        else {t.putInt("food",0);t.putInt("wood",0);t.putInt("ore",0);}
        p.getConstructionPaymentStorage().setData(STATE,State.read(t));
    }

    /** Consume before credit. Invalid/unknown serialized metadata is preserved and fails closed. */
    public static ResourceCost take(BuildingPlacement p) {
        if(p.getLevel().isClientSide()||!p.getLevel().getServer().isSameThread())return zero();
        State s=p.getConstructionPaymentStorage().getData(STATE);
        if(s.missing) {
            ResourceCost old=legacyCost(p.getBuilding().structureName);
            record(p,old.food,old.wood,old.ore);
            s=p.getConstructionPaymentStorage().getData(STATE);
        }
        if(!s.supported||s.raw.getBoolean("claimed"))return zero();
        CompoundTag claimed=s.raw.copy();claimed.putBoolean("claimed",true);
        p.getConstructionPaymentStorage().setData(STATE,State.read(claimed));
        // Preserve published native semantics: cancellation credits the current owner.
        // Payer/epoch are audit metadata, not a new ownership or account-generation gate.
        return ResourceCost.Building(s.raw.getInt("food"),s.raw.getInt("wood"),s.raw.getInt("ore"),0);
    }

    /** Exact API53 legacy table, independent of current mutable definitions. */
    public static ResourceCost legacyCost(String structure) {
        return switch(structure) {
            case "dream_anchor" -> ResourceCost.Building(0,400,100,0);
            case "reverie_garden" -> ResourceCost.Building(0,110,0,0);
            case "beetle_sanctuary" -> ResourceCost.Building(0,160,60,0);
            case "shadow_furnace" -> ResourceCost.Building(0,150,100,0);
            case "dream_depot","shadow_mine","dream_hall","resonance_laboratory",
                    "contract_ecology","resonance_dais","shadow_watchtower" -> ResourceCost.Building(0,100,50,0);
            default -> zero();
        };
    }

    public static final class State {
        private final boolean missing,supported;
        private final CompoundTag raw;
        private State(boolean missing,boolean supported,CompoundTag raw){this.missing=missing;this.supported=supported;this.raw=raw;}
        public static State legacy(){return new State(true,true,new CompoundTag());}
        public static State read(CompoundTag input) {
            CompoundTag t=input.copy();
            // Serialize lazy absent state as an explicit legacy marker, not an ambiguous empty schema.
            if(t.size()==1&&t.contains("legacyApi53",Tag.TAG_BYTE)&&t.getByte("legacyApi53")==1)return legacy();
            boolean valid=t.contains("schema",Tag.TAG_INT)&&t.getInt("schema")==1
                    &&t.contains("payer",Tag.TAG_STRING)&&t.getString("payer").length()<=128
                    &&t.contains("claimed",Tag.TAG_BYTE)&&(t.getByte("claimed")==0||t.getByte("claimed")==1);
            for(String key:new String[]{"food","wood","ore"})
                valid&=t.contains(key,Tag.TAG_INT)&&t.getInt(key)>=0;
            if(t.getInt("food")>0||t.getInt("wood")>0||t.getInt("ore")>0)valid&=t.hasUUID("epoch");
            if(t.contains("epoch"))valid&=t.hasUUID("epoch");
            return new State(false,valid,t);
        }
        public CompoundTag write(){if(missing){CompoundTag t=new CompoundTag();t.putBoolean("legacyApi53",true);return t;}return raw.copy();}
        public boolean supported(){return supported;}
    }
}
