"""Offline model checks for production authorization, research projection, structure recipe
template isolation and the bounded vertical search. Uses the actual Java helper classes/method
bodies and small, explicit Minecraft/service doubles. This is NOT a Forge/GUI/codec test.
Run with a JDK 17+: python3 src/test/scripts/repair3_production_regression.py
"""
import os
from pathlib import Path
from subprocess import run
from tempfile import TemporaryDirectory

repo = Path(__file__).resolve().parents[3]
core = repo / "src/main/java/com/solegendary/reignofnether"
prod = core / "building/production"

def body(file, signature):
    s=file.read_text()
    i=s.index(signature); first=s.index("{",i); depth=0
    for end in range(first,len(s)):
        depth += (s[end]=="{") - (s[end]=="}")
        if not depth: return s[i:end+1]
    raise ValueError(signature)

test = """package com.solegendary.reignofnether.building.production;
import java.util.*;
import com.solegendary.reignofnether.building.OreMinePlacementAccess;
import net.minecraft.nbt.CompoundTag;
import com.solegendary.reignofnether.building.BuildingAction;
import net.minecraft.world.entity.EntityType;

public final class Repair3ProductionProbe {
    static int checks; static void check(boolean condition,String why){checks++; if(!condition)throw new AssertionError(why);}
    record Level(boolean isClientSide) {}
    record Profile(String getName) {}
    record ServerPlayer(Profile getGameProfile,Level serverLevel) {}
    static final Set<String> editors=new HashSet<>(),grants=new HashSet<>();
    static class SandboxServer {static boolean isSandboxPlayer(String name){return editors.contains(name);}}
    static class AlliancesServerEvents {static boolean canControlAlly(String actor,String owner){return grants.contains(actor+"->"+owner);}}
    static class BuildingPlacement {
        Level level; BlockPos originPos=new BlockPos(0,0,0);
        Set<BlockPos> inside=new HashSet<>();Level getLevel(){return level;}boolean isPosInsideBuilding(BlockPos address){return inside.contains(address);}
    }
    static class ProductionPlacement extends BuildingPlacement {
        String ownerName; boolean isDestroyedServerside, isBuilt=true, prerequisites=true, binding=true;
        int health=1; int getHealth(){return health;} boolean shouldBeDestroyed(){return isDestroyedServerside;}
        ProductionPlacement(String owner,Level level){this.ownerName=owner;this.level=level;}
    }
""" + body(core/"building/BuildingProductionServerboundPacket.java","public static boolean canOperate(") + """
""" + body(core/"building/ProductionQueueAddress.java","static ProductionPlacement resolve(") + """
    record BlockPos(int x,int y,int z) {}
    static void addresses(){
        var level=new Level(false);var other=new Level(false);var point=new BlockPos(1,3,5);var corner=new BlockPos(2,3,5);
        var near=new ProductionPlacement("Near",level);near.inside.add(point);near.inside.add(corner);
        var exact=new ProductionPlacement("Owner",level);exact.originPos=point;
        var far=new ProductionPlacement("Owner",other);far.originPos=point;
        check(resolve(level,point,List.of(near,far,exact))==exact,"origin wins over a containing host or other dimension");
        check(resolve(level,corner,List.of(far,exact,near))==near,"unambiguous legacy corner still resolves");
        exact.inside.add(corner);
        check(resolve(level,corner,List.of(near,exact))==null,"ambiguous legacy corner cannot select another queue");
        var duplicate=new ProductionPlacement("Other",level);duplicate.originPos=point;
        check(resolve(level,point,List.of(near,exact,duplicate))==null,"ambiguous origin never chooses by iteration order");
        var nonprod=new BuildingPlacement();nonprod.level=level;nonprod.originPos=point;
        check(resolve(level,point,List.of(near,nonprod))==null,"exact nonproducer is not another producer's address");
    }
    // This value-stream double verifies field selection, order and exact consumption, not Netty byte layouts.
    static class FriendlyByteBuf {
        final List<Object> fields=new ArrayList<>();int n;
        <E extends Enum<E>> E readEnum(Class<E> type){return type.cast(fields.get(n++));}
        void writeEnum(Enum<?> value){fields.add(value);}
        String readUtf(){return (String)fields.get(n++);}void writeUtf(String v){fields.add(v);}
        BlockPos readBlockPos(){return (BlockPos)fields.get(n++);}void writeBlockPos(BlockPos v){fields.add(v);}
        boolean readBoolean(){return (Boolean)fields.get(n++);}void writeBoolean(boolean v){fields.add(v);}
        UUID readUUID(){return (UUID)fields.get(n++);}void writeUUID(UUID v){fields.add(v);}
    }
    static class BuildingProductionServerboundPacket {
        BuildingAction action;String itemName;BlockPos buildingPos;UUID expectedEntry;
""" + "".join(body(core/"building/BuildingProductionServerboundPacket.java",sig) for sig in (
    "public BuildingProductionServerboundPacket(BuildingAction action, String itemName, BlockPos buildingPos, UUID expectedEntry)",
    "public BuildingProductionServerboundPacket(FriendlyByteBuf buffer)", "public void encode(FriendlyByteBuf buffer)")) + """
    }
    static void cancelFields(){
        var pos=new BlockPos(1,2,3);var row=UUID.randomUUID();
        for(var action:List.of(BuildingAction.CANCEL_PRODUCTION,BuildingAction.CANCEL_OBSERVED_BACK_PRODUCTION,
                BuildingAction.CANCEL_BACK_PRODUCTION,BuildingAction.REQUEST_PRODUCTION_SYNC)){
            boolean bound=action==BuildingAction.CANCEL_PRODUCTION||action==BuildingAction.CANCEL_OBSERVED_BACK_PRODUCTION;
            for(UUID id:new UUID[]{row,null}){
                var transport=new FriendlyByteBuf();new BuildingProductionServerboundPacket(action,"recipe",pos,id).encode(transport);
                int expectedCount=3+(bound?(id==null?1:2):0);
                check(transport.fields.size()==expectedCount,"legacy tail remains unchanged; observed tail encodes its ID");
                var decoded=new BuildingProductionServerboundPacket(transport);
                check(transport.n==expectedCount&&decoded.action==action&&decoded.itemName.equals("recipe")
                    &&pos.equals(decoded.buildingPos)&&Objects.equals(decoded.expectedEntry,bound?id:null),"exact UUID survives request decoding");
            }
        }
    }
    static void auth() {
        var here=new Level(false); var other=new Level(false);
        var host=new ProductionPlacement("Owner",here);
        var owner=new ServerPlayer(new Profile("Owner"),here); var delegate=new ServerPlayer(new Profile("Crew"),here);
        check(canOperate(owner,host)&&!canOperate(delegate,host),"owner/grant matrix: independent owner");
        editors.add("DifferentEditor");
        check(!canOperate(delegate,host),"unrelated sandbox seat is not a grant");
        grants.add("Crew->Owner");
        check(canOperate(delegate,host)&&!canOperate(new ServerPlayer(delegate.getGameProfile(),other),host),"grant belongs to target and world");
        host.ownerName="Neighbor";
        check(!canOperate(delegate,host),"grant to old owner is not a grant to current owner");
        host.ownerName="Owner";grants.clear();
        check(!canOperate(delegate,host),"revocation effective on next command");
        editors.add("Crew");check(canOperate(delegate,host),"personally authorized sandbox");
        host.isDestroyedServerside=true;check(!canOperate(delegate,host),"retired target");
        host.isDestroyedServerside=false;host.ownerName=" ";check(!canOperate(delegate,host),"no anonymous payer");
    }
    static class ActiveProduction {
        final UUID queueEntryId; boolean completed; float ticksLeft=10,workMirror; ProductionPause pause=ProductionPause.INITIAL;
        String observedPause="";
        ActiveProduction(UUID id){queueEntryId=id;}
""" + "".join(body(prod/"ActiveProduction.java",sig) for sig in (
    "public ProductionPause pause()", "public void beginPauseObservation()", "public boolean pauseBecause(",
    "public boolean finishPauseObservation()", "public boolean acceptPause(")) + """
    }
    static class ReceiptResearchService {
        static int completions,refunds;
        static boolean bindingValid(ProductionPlacement p,ActiveProduction a){return p.binding;}
        static boolean complete(ProductionPlacement p,ActiveProduction a){completions++;a.completed=true;return true;}
        static void detachAndRefund(ProductionPlacement p,ActiveProduction a){refunds++;}
    }
    static class ProductionWorkRates {static void advance(ProductionPlacement p,ActiveProduction a,int base){a.ticksLeft-=base;}}
    static class Research {
        boolean validHostAndPrerequisite(ProductionPlacement p){return p.prerequisites;}
""" + body(core/"research/economy/ReceiptBackedResearchItem.java","public boolean reportsHeadPause()") + body(core/"research/economy/ReceiptBackedResearchItem.java","public boolean tick(") + """
    }
    static void research() {
        var recipe=new Research();var id=UUID.randomUUID();var real=new ActiveProduction(id);var view=new ActiveProduction(id);
        var server=new ProductionPlacement("Owner",new Level(false));var client=new ProductionPlacement("Owner",new Level(true));
        check(recipe.reportsHeadPause(),"receipt research uses head observation");
        real.beginPauseObservation();recipe.tick(server,real);check(!real.finishPauseObservation()&&real.ticksLeft==9,"ordinary server work");
        server.prerequisites=false;real.beginPauseObservation();recipe.tick(server,real);
        check(real.finishPauseObservation()&&real.pause().paused()&&real.ticksLeft==9,"missing prerequisite freezes server on unchanged row");
        check(!view.acceptPause(UUID.randomUUID(),real.pause(),0)&&view.acceptPause(id,real.pause(),real.ticksLeft),"only matching UUID projects");
        var held=real.pause();
        for(int i=0;i<40;i++){real.beginPauseObservation();recipe.tick(server,real);check(!real.finishPauseObservation(),"steady hold not republished");recipe.tick(client,view);}
        check(view.ticksLeft==9&&real.ticksLeft==9&&ReceiptResearchService.completions==0&&ReceiptResearchService.refunds==0,"no client creep, no settlement while paused");
        server.prerequisites=true;real.beginPauseObservation();recipe.tick(server,real);
        check(real.finishPauseObservation()&&!real.pause().paused()&&real.ticksLeft==8,"exact same job resumed");
        check(view.acceptPause(id,real.pause(),real.ticksLeft)&&!view.acceptPause(id,held,9),"stale hold cannot rewind");
        recipe.tick(client,view);check(view.ticksLeft==7&&!view.completed,"working display predicts but cannot complete");
        while(real.ticksLeft>0)recipe.tick(server,real);check(ReceiptResearchService.completions==1&&ReceiptResearchService.refunds==0,"one final server settlement");
    }
    static void templates(){
        var species=new EntityType<>();var other=new EntityType<>();var map=new CompoundTag();map.put("Name","Alpha");
        var a=new RecipeSpawnTemplate(species,map);map.put("Name","Beta");var b=new RecipeSpawnTemplate(species,map);
        map.put("Name","Edited");var first=a.copyFor(species);first.put("Pos","temporary");
        check("Alpha".equals(first.get("Name"))&&"Beta".equals(b.copyFor(species).get("Name"))&&"Alpha".equals(a.copyFor(species).get("Name")),"A/B/A independent of source and sibling");
        check(!map.containsKey("Pos")&&!a.copyFor(species).containsKey("Pos"),"spawn never writes back");
        check(a.copyFor(other)==null&&new RecipeSpawnTemplate(species,null).copyFor(species)==null,"wrong species or untagged recipe has no template");
    }
    static void heights() {
        int bottom=-64,top=320;int[] count={0};
        check(IndoorSpawnSearch.find(319,bottom,top,y->{count[0]++;return false;}).isEmpty()&&count[0]==128,"void column stops at per-attempt budget");
        count[0]=0;check(IndoorSpawnSearch.find(-60,bottom,top,y->{count[0]++;check(y>=bottom,"never scan below world");return false;}).isEmpty()&&count[0]==4,"short column stops at actual world edge");
        for(int start=bottom-1;start<=top;start+=3)for(int floor=bottom;floor<top;floor+=7){
            var field=Set.of(floor);var found=IndoorSpawnSearch.find(start,bottom,top,field::contains);
            boolean expected=start>bottom&&start<top&&floor<start&&start-floor<=128;
            check(found.isPresent()==expected && (!expected || found.getAsInt()==floor+1),"bounded floor oracle");
        }
        check(IndoorSpawnSearch.find(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,y->{throw new AssertionError();}).isEmpty(),"extreme lower bound");
    }
    public static void main(String[] args){auth();addresses();cancelFields();research();templates();heights();System.out.println("REPAIR3_PRODUCTION_METHODS_PASS assertions="+checks+" areas=auth,address,cancel_fields,research,template,floor");}
}
"""
def binary(name):
    return str(Path(os.environ["JAVA_HOME"])/"bin"/name) if os.environ.get("JAVA_HOME") else name

with TemporaryDirectory() as tmp:
    root=Path(tmp)
    files={"Repair3ProductionProbe.java":test,
        "net/minecraft/world/entity/EntityType.java":"package net.minecraft.world.entity;public class EntityType<T>{}",
        "net/minecraft/nbt/CompoundTag.java":"package net.minecraft.nbt;public class CompoundTag extends java.util.HashMap<String,String>{public CompoundTag copy(){var c=new CompoundTag();c.putAll(this);return c;}}",
        "javax/annotation/Nullable.java":"package javax.annotation;public @interface Nullable{}",
        "net/minecraft/network/FriendlyByteBuf.java":"""package net.minecraft.network;public class FriendlyByteBuf {
            public long readVarLong(){throw new UnsupportedOperationException();}public String readUtf(int n){throw new UnsupportedOperationException();}
            public void writeVarLong(long n){throw new UnsupportedOperationException();}public void writeUtf(String s,int n){throw new UnsupportedOperationException();}
        }"""}
    for name,content in files.items():
        path=root/name; path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
    run([binary("javac"),"--release","17","-d",str(root),*(str(root/f) for f in files),
         str(core/"building/OreMinePlacementAccess.java"),str(core/"building/BuildingAction.java"),str(prod/"ProductionPause.java"),str(prod/"IndoorSpawnSearch.java"),str(prod/"RecipeSpawnTemplate.java")],check=True)
    run([binary("java"),"-cp",str(root),"com.solegendary.reignofnether.building.production.Repair3ProductionProbe"],check=True)
