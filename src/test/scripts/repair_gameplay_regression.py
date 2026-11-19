"""Offline JDK17 behavior checks for worker cargo, Shift construction and front cancellation.

Run: python3 src/test/scripts/repair_gameplay_regression.py
Compiles production method bodies into small test doubles (one qualified addon type name is shortened). No network,
Minecraft, Forge, Gradle, third-party packages, or world files. The real paired codecs,
registries, connected owner replies and server placements have a separate Forge GameTest suite.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
from subprocess import run
import os

repo = Path(__file__).resolve().parents[3]
core = repo / "src/main/java/com/solegendary/reignofnether"
addon = repo / "addons/dreamseekers/src/main/java/com/dreamseekers"

def method(path, signature):
    source = path.read_text()
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    for i in range(brace, len(source)):
        depth += (source[i] == "{") - (source[i] == "}")
        if not depth:
            return source[start:i+1]
    raise AssertionError(f"unclosed method {signature}")

res = core / "resources/Resources.java"
build = core / "unit/goals/BuildRepairGoal.java"
place = core / "building/buildings/placements/ProductionPlacement.java"
source = '''import java.util.*;
public class GameplayRepairProbe {
  static int checks; static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
  enum ResourceName { FOOD,WOOD,ORE,EMERALD,NONE }
  record ItemStack(String getItem,int getCount){boolean isEmpty(){return getCount <= 0;}}
  static class ResourceSource{ResourceName resourceName;int resourceValue;ResourceSource(ResourceName n,int v){resourceName=n;resourceValue=v;}}
  static class ResourceSources{static ResourceSource getFromItem(String name){return switch(name){
    case "NONE" -> new ResourceSource(ResourceName.NONE,99);
    case "free" -> new ResourceSource(ResourceName.FOOD,0);
    case "oversize" -> new ResourceSource(ResourceName.ORE,Integer.MAX_VALUE);
    case "apple" -> null;
    default -> new ResourceSource(ResourceName.valueOf(name),2);
  };}}
  static class Resources {String ownerName;int food,wood,ore,emerald,foodToAdd,woodToAdd,oreToAdd,emeraldToAdd;
    Resources(String owner,int f,int w,int o){this(owner,f,w,o,0);}
    Resources(String owner,int f,int w,int o,int e){ownerName=owner;food=f;wood=w;ore=o;emerald=e;}
    int[] amounts(){return new int[]{food,wood,ore,emerald};}
''' + ''.join(method(res, x) for x in (
    "public int getTotalValue()", "public void changeInstantly(", "private void addItemValue(",
    "public static Resources getTotalResourcesFromItems(", "public Resources depositCarriedItems(")) + '''
  }
  static class ExtendedResourcesSync{static Resources bank;static Resources account(String owner){return bank!=null&&owner.equals(bank.ownerName)?bank:null;}}
  enum RTSPlayerScoresEnum{TOTAL_RESOURCES_HARVESTED,FOOD_HARVESTED,WOOD_HARVESTED,ORES_HARVESTED}
  static class Scores {int[] amounts=new int[4];void addToScore(RTSPlayerScoresEnum key,int value){amounts[key.ordinal()]+=value;}}
  static class RTSPlayer{Scores scores=new Scores();} static class PlayerServerEvents{static RTSPlayer player;static RTSPlayer getRTSPlayer(String owner){return player;}}
  static class ResourcesClientboundPacket{static int events;static void addSubtractResources(Resources r){events++;}static void showFloatingText(Resources r,Object p){}}
  static class UnitSyncClientboundPacket{static void sendSyncResourcesPacket(Unit u){}}
  @interface Nullable{} static class Point{int getX(){return 0;}int getY(){return 1;}int getZ(){return 0;}}
  static class BlockPos extends Point{BlockPos(int x,int y,int z){}}
  static class Entity{}static class LivingEntity extends Entity{}static class Mob extends LivingEntity{Level level(){return serverLevel;}Object getOnPos(){return null;}}
  static class Level {Map<Integer,Entity> mobs=new HashMap<>();boolean isClientSide(){return false;}Entity getEntity(int id){return mobs.get(id);}}
  static final Level serverLevel=new Level();
  interface Unit{List<ItemStack> getItems();String getOwnerName();void resetBehaviours();}
  static class Worker extends Mob implements Unit,WorkerUnit {
     List<ItemStack> cargo = new ArrayList<>();BuildGoal goal=new BuildGoal(this);int resets;
     public List<ItemStack>getItems(){return cargo;}public String getOwnerName(){return "owner";}
     public void resetBehaviours(){resets++;}public BuildGoal getBuildRepairGoal(){return goal;}public Gather getGatherResourceGoal(){return new Gather();}
  }
  static class ReturnGoal{Mob mob;Object moveTarget;boolean valid=true;boolean hasValidDepositTarget(){return valid;}void stopReturning(){valid=false;}
''' + method(core/"unit/goals/ReturnResourcesGoal.java", "public void depositItems()") + '''
  }
  static class Gather{void stopGathering(){}}
  static class UnitServerEvents{static void discardPendingFormationMove(LivingEntity e){}}
  interface WorkerUnit{BuildGoal getBuildRepairGoal();Gather getGatherResourceGoal();
''' + method(core/"unit/interfaces/WorkerUnit.java", "public static void resetBehavioursExceptExploreBuild(") + '''
  }
  static class BuildingPlacement{boolean available=true;Level getLevel(){return serverLevel;}Point centrePos=new Point(),originPos=new Point();}
  static class BuildingUtils{static boolean isBuildingBuildable(boolean client,BuildingPlacement p){return p.available;}}
  static class MiscUtil{static void addUnitCheckpoint(Unit u,Object p,boolean b){}}
  static class Base{Object moveTarget;void stopMoving(){moveTarget=null;}}
  static class BuildGoal extends Base{Mob mob;List<BuildingPlacement> queuedBuildings=new ArrayList<>();BuildingPlacement buildingTarget;
    boolean ignoreNextCheckpoint;BuildGoal(Mob m){mob=m;}BuildingPlacement getBuildingTarget(){return buildingTarget;}void calcMoveTarget(){}void start(){}
''' + ''.join(method(build,x) for x in ("public boolean startNextQueuedBuilding()", "public void stopBuilding()", "public void setBuildingTarget(")) + '''
  }
''' + method(core/"building/BuildingServerEvents.java", "private static void assignBuilderUnits(") + '''
  static void cargoCases(){
    for(int mask=0;mask<32;mask++){
      var w=new Worker();var goal=new ReturnGoal();goal.mob=w;
      var bank=new Resources("owner",0,0,0,0);ExtendedResourcesSync.bank=bank;var p=new RTSPlayer();PlayerServerEvents.player=p;
      var kept=new ArrayList<ItemStack>();int[] expect=new int[4];String[] names={"FOOD","WOOD","ORE","EMERALD","apple"};
      for(int i=0;i<5;i++)if((mask&(1<<i))!=0){var stack=new ItemStack(names[i],i+1);w.cargo.add(stack);if(i<4)expect[i]=2*(i+1);else kept.add(stack);}
      int before=ResourcesClientboundPacket.events;int total=Arrays.stream(expect).sum();
      goal.depositItems();goal.valid=true;goal.depositItems();
      check(Arrays.equals(expect,bank.amounts()),"balances "+mask);
      check(Arrays.equals(new int[]{total,expect[0],expect[1],expect[2]},p.scores.amounts),"all category scores "+mask);
      check(w.cargo.equals(kept)&&(kept.isEmpty()||w.cargo.get(0)==kept.get(0)),"unpriced stack unchanged "+mask);
      check(ResourcesClientboundPacket.events-before==(total==0?0:1),"publish exactly once "+mask);
    }
    var w=new Worker();var goal=new ReturnGoal();goal.mob=w;
    var a=new ItemStack("apple",3);var none=new ItemStack("NONE",2);var empty=new ItemStack("WOOD",0);var free=new ItemStack("free",5);
    w.cargo.addAll(List.of(a,new ItemStack("WOOD",2),none,new ItemStack("WOOD",3),empty,free));
    var before=List.copyOf(w.cargo);ExtendedResourcesSync.bank=null;goal.depositItems();check(w.cargo.equals(before),"missing account retains cargo");
    var bank=new Resources("owner",0,0,0,0);ExtendedResourcesSync.bank=bank;goal.valid=false;goal.depositItems();check(w.cargo.equals(before)&&bank.wood==0,"invalid receiver retains cargo");
    goal.valid=true;PlayerServerEvents.player=null;goal.depositItems();check(w.cargo.equals(List.of(a,none,empty,free))&&bank.wood==10,"all and only priced slots removed without scoreboard");
    w.cargo.add(new ItemStack("WOOD",1));bank.wood=Integer.MAX_VALUE;before=List.copyOf(w.cargo);
    goal.valid=true;goal.depositItems();check(w.cargo.equals(before)&&bank.wood==Integer.MAX_VALUE,"account overflow is atomic");
    bank.wood=0;w.cargo.add(new ItemStack("oversize",2));before=List.copyOf(w.cargo);
    goal.depositItems();check(w.cargo.equals(before)&&bank.wood==0&&bank.ore==0,"unrepresentable cargo is atomic");
  }
  static void constructionCases(){
    for(int n=1;n<=8;n++){
      var w=new Worker();serverLevel.mobs.put(1,w);var a=new BuildingPlacement();var work=new ArrayList<BuildingPlacement>();
      assignBuilderUnits(new int[]{1},false,a);check(w.goal.buildingTarget==a&&w.goal.queuedBuildings.isEmpty(),"ordinary A outside queue");
      for(int i=0;i<n;i++){var b=new BuildingPlacement();work.add(b);assignBuilderUnits(new int[]{1},true,b);}
      check(w.goal.buildingTarget==a&&w.resets==1&&w.goal.queuedBuildings.equals(work),"all Shift orders preserve A");
      a.available=false;
      for(var b:work){check(w.goal.startNextQueuedBuilding()&&w.goal.buildingTarget==b,"FIFO advance");b.available=false;}
      check(!w.goal.startNextQueuedBuilding(),"no future work");
    }
    var w1=new Worker();var w2=new Worker();serverLevel.mobs.put(1,w1);serverLevel.mobs.put(2,w2);
    var a=new BuildingPlacement();var b=new BuildingPlacement();var c=new BuildingPlacement();var d=new BuildingPlacement();
    w1.goal.setBuildingTarget(a); // manually assigned repair
    assignBuilderUnits(new int[]{1,2},true,b);assignBuilderUnits(new int[]{1,2},true,c);
    check(w1.goal.buildingTarget==a&&w1.resets==0&&w2.goal.buildingTarget==b&&w2.resets==1,"manual repair preserved; independent idle worker starts B");
    a.available=false;b.available=false;check(w1.goal.startNextQueuedBuilding()&&w2.goal.startNextQueuedBuilding()&&w1.goal.buildingTarget==c&&w2.goal.buildingTarget==c,"destroyed work skipped");
    assignBuilderUnits(new int[]{1,2},false,d);check(w1.goal.queuedBuildings.isEmpty()&&w2.goal.queuedBuildings.isEmpty()&&w1.goal.buildingTarget==d&&w2.goal.buildingTarget==d,"immediate order still replaces");
  }
  static class Cost{int food=2,wood=3,ore=4,ticks=5;}
  static class ProductionItem{Cost getCost(boolean client,String owner){return new Cost();}}
  static class Research extends ProductionItem implements com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem{}
  static class DreamUnitProduction extends ProductionItem{}
  static class ActiveProduction{UUID queueEntryId=UUID.randomUUID();boolean completed;Object receiptResearch;ProductionItem item;float ticksLeft=5;ActiveProduction(ProductionItem i){item=i;}}
  static class ResourcesServerEvents{static int refunds;static void addSubtractResources(Resources r){refunds++;}}
  static class ProductionPlacement{List<ActiveProduction> productionQueue=new ArrayList<>();String ownerName="owner";Level level=serverLevel;Level getLevel(){return level;}
''' + method(place, "public final boolean cancelObservedHead(")+method(place,"public boolean cancelProductionItem(")+'''
  }
  static class Binding{int work(){return 5;}}
  static class DreamPlacement extends ProductionPlacement{boolean completingUnit,retireHead;Object tickingUnit;Map<ActiveProduction,Binding> unitReceipts=new IdentityHashMap<>();
    boolean onProductionServerThread(){return true;}boolean reconcileUnitReceipts(){if(retireHead){retireHead=false;productionQueue.remove(0);return true;}return false;}
    boolean detachUnitAndRefund(ActiveProduction a){boolean removed=productionQueue.remove(a);if(removed)ResourcesServerEvents.refunds++;return removed;}
''' + method(addon/"production/DreamProductionPlacement.java","public boolean cancelProductionItem(").replace("com.solegendary.reignofnether.building.production.ProductionItem","ProductionItem") + '''
  }
  static void cancellationCases(){
    for(boolean dream:List.of(false,true)){
      ProductionPlacement p=dream?new DreamPlacement():new ProductionPlacement();
      ProductionItem x=dream?new DreamUnitProduction():new ProductionItem();var y=new ProductionItem();var a=new ActiveProduction(x);var b=new ActiveProduction(x);
      p.productionQueue.addAll(List.of(a,b));a.completed=true;p.productionQueue.remove(a);int before=ResourcesServerEvents.refunds;
      check(!p.cancelObservedHead(x,a.queueEntryId)&&!p.cancelObservedHead(y,b.queueEntryId)&&!p.cancelObservedHead(x,null),"stale/same/mismatch/missing");
      check(p.productionQueue.equals(List.of(b))&&ResourcesServerEvents.refunds==before,"no mutation on stale");
      check(p.cancelObservedHead(x,b.queueEntryId)&&p.productionQueue.isEmpty()&&ResourcesServerEvents.refunds==before+1,"exact live entry refunded");
      check(!p.cancelObservedHead(x,b.queueEntryId)&&ResourcesServerEvents.refunds==before+1,"replay no refund");
      var research=new ActiveProduction(new Research());research.receiptResearch=new Object();p.productionQueue.add(research);
      check(!p.cancelObservedHead(research.item,research.queueEntryId)&&p.productionQueue.get(0)==research,"ordinary cancel cannot detach research");
      if(dream){var d=(DreamPlacement)p;d.productionQueue.clear();a=new ActiveProduction(x);b=new ActiveProduction(x);p.productionQueue.addAll(List.of(a,b));
        d.retireHead=true;check(!p.cancelObservedHead(x,a.queueEntryId)&&p.productionQueue.equals(List.of(b))&&ResourcesServerEvents.refunds==before+1,"reconciliation retires A without touching B");}
    }
  }
  public static void main(String[] args){cargoCases();constructionCases();cancellationCases();System.out.println("GAMEPLAY_REPAIR_METHODS_PASS assertions="+checks+" cargo_masks=32 shift_lengths=8 cancel_paths=native,addon");}
}
'''

def java_bin(name):
    return str(Path(os.environ["JAVA_HOME"])/"bin"/name) if os.environ.get("JAVA_HOME") else name

with TemporaryDirectory() as tmp:
    d=Path(tmp)
    (d/"GameplayRepairProbe.java").write_text(source)
    pkg = "com.solegendary.reignofnether.research.economy"
    (d/"ReceiptBackedResearchItem.java").write_text(f"package {pkg}; public interface ReceiptBackedResearchItem {{}}")
    (d/"ReceiptResearchService.java").write_text(f"package {pkg}; public class ReceiptResearchService {{ public static void detachAndRefund(Object a,Object b) {{throw new AssertionError();}} }}")
    run([java_bin("javac"),"--release","17","-d",str(d),*(str(p) for p in sorted(d.glob("*.java")))],check=True)
    run([java_bin("java"),"-cp",str(d),"GameplayRepairProbe"],check=True)
