package com.dreamseekers.worldtree;
import net.minecraft.nbt.*;
import java.util.UUID;
/** Building DataStorage payload, never a second resource ledger. Unknown data is preserved inert. */
public final class WorldTreeState {
    public enum Phase { IDLE, RUNNING, BLOCKED, REFUND_PENDING, COMPLETE }
    public boolean supported=true;
    public WorldTreeModel model=WorldTreeModel.CURRENT_BASE,target;
    private CompoundTag unknown;
    public UUID instance=UUID.randomUUID(),receipt,payerEpoch,incomeEpoch;
    public String payer="",incomeOwner="",blockedReason="";
    public Phase phase=Phase.IDLE;
    public int remaining=0,incomeElapsed=0,geometryHighest=0;
    public boolean ascended=false,wasLeader=false;
    public transient long captureRevision=Long.MIN_VALUE;
    public transient long lastTick=Long.MIN_VALUE;
    public static WorldTreeState read(CompoundTag t) {
        var s=new WorldTreeState();s.unknown=t.copy();
        try {
            if(!t.contains("schema",Tag.TAG_INT)||(t.getInt("schema")!=1&&t.getInt("schema")!=2)||!t.hasUUID("instance"))throw new IllegalArgumentException();
            for(String field:new String[]{"phase","payer","incomeOwner","blockedReason"})if(!t.contains(field,Tag.TAG_STRING))throw new IllegalArgumentException();
            for(String field:new String[]{"remaining","incomeElapsed"})if(!t.contains(field,Tag.TAG_INT))throw new IllegalArgumentException();
            for(String field:new String[]{"ascended","wasLeader"})if(!t.contains(field,Tag.TAG_BYTE)||(t.getByte(field)!=0&&t.getByte(field)!=1))throw new IllegalArgumentException();
            s.instance=t.getUUID("instance");s.phase=Phase.valueOf(t.getString("phase"));
            s.payer=t.getString("payer");s.incomeOwner=t.getString("incomeOwner");s.blockedReason=t.getString("blockedReason");
            s.remaining=t.getInt("remaining");s.incomeElapsed=t.getInt("incomeElapsed");s.ascended=t.getBoolean("ascended");s.wasLeader=t.getBoolean("wasLeader");
            if(s.payer.length()>128||s.incomeOwner.length()>128||s.blockedReason.length()>128||s.remaining<0||s.remaining>2400||s.incomeElapsed<0||s.incomeElapsed>=100)throw new IllegalArgumentException();
            if(t.contains("receipt")){if(!t.hasUUID("receipt"))throw new IllegalArgumentException();s.receipt=t.getUUID("receipt");}
            if(t.contains("payerEpoch")){if(!t.hasUUID("payerEpoch"))throw new IllegalArgumentException();s.payerEpoch=t.getUUID("payerEpoch");}
            if(t.contains("incomeEpoch")){if(!t.hasUUID("incomeEpoch"))throw new IllegalArgumentException();s.incomeEpoch=t.getUUID("incomeEpoch");}
            if(t.getInt("schema")==1){s.model=s.ascended?WorldTreeModel.LEGACY_SHADOW:WorldTreeModel.LEGACY_BASE;
                s.target=s.receipt==null?null:WorldTreeModel.LEGACY_SHADOW;
            }else{s.model=WorldTreeModel.read(t,"model");s.target=t.contains("target")?WorldTreeModel.read(t,"target"):null;
                if(s.model==null||(t.contains("target")&&s.target==null))throw new IllegalArgumentException();}
            if(t.contains("geometryHighest")){if(!t.contains("geometryHighest",Tag.TAG_INT))throw new IllegalArgumentException();s.geometryHighest=t.getInt("geometryHighest");
                if(s.geometryHighest<2||s.geometryHighest>s.model.count)throw new IllegalArgumentException();}
            boolean pending=s.phase==Phase.RUNNING||s.phase==Phase.BLOCKED||s.phase==Phase.REFUND_PENDING;
            if(pending&&(s.receipt==null||s.payerEpoch==null||s.payer.isBlank()||s.ascended))throw new IllegalArgumentException();
            if(s.ascended!=(s.model.branch==WorldTreeModel.Branch.SHADOW))throw new IllegalArgumentException();
            if(pending&&!WorldTreeModel.supportsSavedTarget(s.model,s.target))throw new IllegalArgumentException();
            if(!pending&&s.target!=null)throw new IllegalArgumentException();
            if((s.model.branch!=WorldTreeModel.Branch.BASE)!=(s.phase==Phase.COMPLETE)||(!pending&&(s.receipt!=null||s.payerEpoch!=null)))throw new IllegalArgumentException();
        } catch(RuntimeException e){s.supported=false;}
        return s;
    }
    public CompoundTag write() {
        if(!supported)return unknown==null?new CompoundTag():unknown.copy();
        var t=new CompoundTag();t.putInt("schema",2);model.write(t,"model");if(target!=null)target.write(t,"target");t.putUUID("instance",instance);t.putString("phase",phase.name());
        t.putString("payer",payer);t.putString("incomeOwner",incomeOwner);t.putString("blockedReason",blockedReason);
        if(geometryHighest>=2)t.putInt("geometryHighest",geometryHighest);t.putInt("remaining",remaining);t.putInt("incomeElapsed",incomeElapsed);t.putBoolean("ascended",ascended);t.putBoolean("wasLeader",wasLeader);
        if(receipt!=null)t.putUUID("receipt",receipt);if(payerEpoch!=null)t.putUUID("payerEpoch",payerEpoch);if(incomeEpoch!=null)t.putUUID("incomeEpoch",incomeEpoch);
        return t;
    }
    public void clearTask(){receipt=null;payerEpoch=null;payer="";remaining=0;blockedReason="";target=null;phase=model.branch!=WorldTreeModel.Branch.BASE?Phase.COMPLETE:Phase.IDLE;}
}
