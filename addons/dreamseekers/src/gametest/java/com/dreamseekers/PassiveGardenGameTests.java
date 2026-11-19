package com.dreamseekers;

import com.dreamseekers.economy.*;
import com.dreamseekers.building.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Candidate: real commands/navigation/normal income ticks. Not run by the source-external author. */
@GameTestHolder("dreamseekers_passive_c")
@PrefixGameTestTemplate(false)
public final class PassiveGardenGameTests {
 private static Run active;
 @AfterBatch(batch="passive_c") public static void cleanup(ServerLevel ignored){if(active!=null){active.cleanup();active=null;}}
 @GameTest(templateNamespace="dreamseekers_passive_c",template="ore_mine_test_arena",batch="passive_c",timeoutTicks=3200)
 public static void migrationRepairIncomeAndOriginalFarm(GameTestHelper helper){active=new Run(helper);active.prepare();helper.onEachTick(active::tick);}
 private static final class Run {
  final GameTestHelper h;final ServerLevel level;
  final List<BuildingPlacement> buildings=new ArrayList<>();final List<Mob> mobs=new ArrayList<>();
  final List<String> owners=new ArrayList<>();final Set<Long> chunks=new HashSet<>();
  record LegacySite(BlockPos origin,Rotation rotation,ArrayList<BuildingBlock> template,List<BlockPos> wheat){}
  final List<LegacySite> legacySites=new ArrayList<>();final List<BlockPos> litCropPositions=new ArrayList<>();
  final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> lampOriginals=new LinkedHashMap<>();
  boolean lightingReady;int lightingWait;
  final List<DreamGardenPlacement> gardens=new ArrayList<>();final List<BlockPos> cropPositions=new ArrayList<>();
  PassiveIncomePeer ownerPeer,otherPeer;Resources account,vanilla,captured;BuildingPlacement mine,furnace;FarmPlacement farm;Mob repairer,farmer;int ticks,stage,entered,assertions;long foodStart,oreStart,shadowStart,capturedFood,capturedOre;
  Run(GameTestHelper helper){h=helper;level=h.getLevel();}
  void check(boolean value,String message){assertions++;h.assertTrue(value,message);}
  Resources account(String name,boolean dream){check(PlayerServerEvents.getRTSPlayer(name)==null,"fresh isolated account");owners.add(name);
   PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,dream?DreamseekersMod.DREAMSEEKERS:Factions.VILLAGERS));
   Resources r=new Resources(name,1000,10000,1000);ResourcesServerEvents.resourcesList.add(r);return r;}
  BlockPos site(int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));
   for(int cx=(p.getX()-18)>>4;cx<=(p.getX()+18)>>4;cx++)for(int cz=(p.getZ()-18)>>4;cz<=(p.getZ()+18)>>4;cz++){
    long key=ChunkPos.asLong(cx,cz);if(!level.getForcedChunks().contains(key)){level.setChunkForced(cx,cz,true);chunks.add(key);}}
   for(int dx=-18;dx<=18;dx++)for(int dz=-18;dz<=18;dz++){level.setBlock(p.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);for(int dy=1;dy<18;dy++)level.setBlock(p.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);}return p;}
  void register(BuildingPlacement p){p.isBuilt=true;buildings.add(p);BuildingServerEvents.getBuildings().add(p);}
  BuildingPlacement full(Building b,String owner,int x,int z){BlockPos origin=site(x,z);for(var block:BuildingUtils.getAbsoluteBlockData(b.getRelativeBlockData(level),level,origin,Rotation.NONE))level.setBlock(block.getBlockPos(),block.getBlockState(),2);
   BuildingPlacement p=BuildingUtils.getNewBuildingPlacement(b,level,origin,Rotation.NONE,owner,false);register(p);return p;}
  Mob worker(String owner,BlockPos feet,boolean dream){Mob mob=dream?DreamseekersMod.FIREFLY_WORKER.get().create(level):EntityRegistrar.VILLAGER_UNIT.get().create(level);
   check(mob instanceof WorkerUnit,"registered genuine worker");((Unit)mob).setOwnerName(owner);mob.moveTo(feet.getX()+.5,feet.getY(),feet.getZ()+.5);check(level.addFreshEntity(mob),"spawn worker");mobs.add(mob);return mob;}
  void order(Mob mob,UnitAction action,BlockPos target){new UnitActionItem(((Unit)mob).getOwnerName(),action,-1,new int[]{mob.getId()},target,BlockPos.ZERO).action(level);}
  int score(String owner,RTSPlayerScoresEnum key){return PlayerServerEvents.getRTSPlayer(owner).scores.scoreList.get(key);}
  void prepare(){
   check(DreamseekersMod.SHADOW_FURNACE.cost.wood==150&&DreamseekersMod.SHADOW_FURNACE.cost.ore==100,"furnace uses approved150W/100O cost shared by button/server");
   check(DreamseekersMod.SHADOW_MINE.cost.wood==300&&DreamseekersMod.SHADOW_MINE.cost.ore==150,"mine uses calibrated300W/150O");
   account=account("PassiveC_Dream",true);vanilla=account("PassiveC_Vanilla",false);captured=account("PassiveC_Captured",false);ownerPeer=new PassiveIncomePeer(level,account.ownerName);otherPeer=new PassiveIncomePeer(level,captured.ownerName);
   Resources purchase=account("PassiveC_Price",true);
   full(DreamseekersMod.DREAM_ANCHOR,purchase.ownerName,240,95);
   for(int n=0;n<2;n++) {
    BlockPos origin=site(240+n*55,150);int wood=purchase.wood,ore=purchase.ore;
    BuildingPlacement bought=BuildingServerEvents.placeBuilding(DreamseekersMod.SHADOW_FURNACE,origin,Rotation.NONE,purchase.ownerName,new int[0],false,false,false,true);
    check(bought!=null,"real legal furnace placement succeeds");if(bought!=null)buildings.add(bought);
    check(purchase.wood==wood-150&&purchase.ore==ore-100,"actual placement charges exactly150W100O, same definition as UI");
   }
   BlockPos rejectedOrigin=site(350,150);int beforeWood=purchase.wood,beforeOre=purchase.ore;
   check(BuildingServerEvents.placeBuilding(DreamseekersMod.SHADOW_FURNACE,rejectedOrigin,Rotation.NONE,purchase.ownerName,new int[0],false,false,false,true)==null,"two unfinished furnace foundations enforce cap");
   check(purchase.wood==beforeWood&&purchase.ore==beforeOre,"rejected third furnace does not charge");
   int index=0;for(Rotation rotation:Rotation.values()){
    BlockPos origin=site(30+index*55,30);ArrayList<BuildingBlock> old=BuildingUtils.getAbsoluteBlockData(DreamBuildingSupport.load(ReverieGarden.STRUCTURE),level,origin,rotation);
    List<BlockPos> wheat=new ArrayList<>();for(var block:old){
     if(block.getBlockState().is(Blocks.WHEAT))wheat.add(block.getBlockPos());
     else level.setBlock(block.getBlockPos(),block.getBlockState(),2);
    }
    check(wheat.size()==16&&new HashSet<>(wheat).size()==16,"sixteen unique intended legacy crops");
    legacySites.add(new LegacySite(origin,rotation,old,wheat));installFixtureLighting(old,wheat);index++;
   }
   BlockPos farmOrigin=site(150,95);var farmTemplate=BuildingUtils.getAbsoluteBlockData(Buildings.WHEAT_FARM.getRelativeBlockData(level),level,farmOrigin,Rotation.NONE);
   for(var block:farmTemplate)if(!(block.getBlockState().getBlock() instanceof CropBlock))level.setBlock(block.getBlockPos(),block.getBlockState(),2);
   farm=(FarmPlacement)BuildingUtils.getNewBuildingPlacement(Buildings.WHEAT_FARM,level,farmOrigin,Rotation.NONE,vanilla.ownerName,false);register(farm);
   List<BlockPos> vanillaCrops=new ArrayList<>();for(var block:farm.getBlocks())if(block.getBlockState().is(Blocks.FARMLAND))vanillaCrops.add(block.getBlockPos().above());
   installFixtureLighting(farm.getBlocks(),vanillaCrops);
   // Let the real server light engine propagate physical lamp emission before any legacy crop is installed.
   // No fake light, product survival override, or skipped age assertion is used.
  }
  void installFixtureLighting(List<BuildingBlock> template,List<BlockPos> crops){
   int minX=template.stream().mapToInt(b->b.getBlockPos().getX()).min().orElseThrow();
   int maxX=template.stream().mapToInt(b->b.getBlockPos().getX()).max().orElseThrow();
   int minZ=template.stream().mapToInt(b->b.getBlockPos().getZ()).min().orElseThrow();
   int maxZ=template.stream().mapToInt(b->b.getBlockPos().getZ()).max().orElseThrow();
   // Keep the fixture light ring above native worker headroom, including raised farm borders.
   Set<Integer> heights=new HashSet<>();for(var crop:crops)heights.add(crop.getY()+2);
   for(int y:heights){
    for(int x=minX-1;x<=maxX+1;x++){fixtureLamp(new BlockPos(x,y,minZ-1));fixtureLamp(new BlockPos(x,y,maxZ+1));}
    for(int z=minZ;z<=maxZ;z++){fixtureLamp(new BlockPos(minX-1,y,z));fixtureLamp(new BlockPos(maxX+1,y,z));}
   }
   litCropPositions.addAll(crops);
  }
  void fixtureLamp(BlockPos pos){
   lampOriginals.putIfAbsent(pos,level.getBlockState(pos));
   check(level.setBlock(pos,Blocks.SEA_LANTERN.defaultBlockState(),3),"place physical lamp outside authoritative footprint");
  }
  void finishLitPreparation(){
   int index=0;for(LegacySite fixture:legacySites){
    BlockPos origin=fixture.origin();Rotation rotation=fixture.rotation();ArrayList<BuildingBlock> old=fixture.template();List<BlockPos> wheat=fixture.wheat();
    check(wheat.size()==16&&new java.util.HashSet<>(wheat).size()==16,"sixteen unique legacy wheat positions");
    for(int n=0;n<14;n++)level.setBlock(wheat.get(n),Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,n%8),2);
    level.setBlock(wheat.get(14),Blocks.AIR.defaultBlockState(),2);level.setBlock(wheat.get(15),Blocks.GOLD_BLOCK.defaultBlockState(),2);
    for(int n=0;n<14;n++) {
     var pos=wheat.get(n);var state=level.getBlockState(pos);var below=level.getBlockState(pos.below());
     System.out.println("PASSIVE_C_PRE_MIGRATION rotation="+rotation+" index="+n+" pos="+pos+" state="+state+" below="+below+" sturdy="+below.isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP)+" light="+level.getRawBrightness(pos,0)+" sky="+level.canSeeSky(pos));
     check(state.is(Blocks.WHEAT)&&state.getValue(BlockStateProperties.AGE_7)==n%8,"legacy fixture must exist before migration index="+n+" rotation="+rotation+" actual="+state);
    }
    FarmPlacement before=new FarmPlacement(DreamseekersMod.REVERIE_GARDEN,level,origin,rotation,account.ownerName,old,false);
    before.partialBlocksDestroyed=.25;
    int count=before.getBlocksPlaced(),total=before.getBlocksTotal(),highest=before.getHighestBlockCountReached();int hp=before.getHealth();
    DataStorage saved=DataStorage.read(new DataStorage().write(),level.getServer());
    DreamGardenPlacement restored=(DreamGardenPlacement)DreamseekersMod.REVERIE_GARDEN.createRestoredBuildingPlacement(level,origin,rotation,account.ownerName,saved);
    // Mirror the ordinary loader's partial damage injection; the factory tests the actual pre-count migration route.
    restored.partialBlocksDestroyed=.25;
    check(restored.migrationReady()&&restored.getBlocksPlaced()==count&&restored.getBlocksTotal()==total&&restored.getHighestBlockCountReached()==highest&&restored.getHealth()==hp,"migration preserves original exact health accounting "+rotation);
    for(int n=0;n<14;n++){var state=level.getBlockState(wheat.get(n));check(state.is(DreamPassiveEconomy.DECORATIVE_WHEAT.get())&&state.getValue(DecorativeGardenWheatBlock.LEGACY_AGE)==n%8,"all eight legacy states retained index="+n+" rotation="+rotation+" pos="+wheat.get(n)+" actual="+state+" below="+level.getBlockState(wheat.get(n).below()));check(ResourceSources.getFromBlockPos(wheat.get(n),level)==null,"decorative state is not a harvest source");}
    check(level.isEmptyBlock(wheat.get(14))&&level.getBlockState(wheat.get(15)).is(Blocks.GOLD_BLOCK),"missing and foreign blocks not overwritten");
    check(saved.getData(DreamGardenPlacement.DECOR_SCHEMA).knownVersion()==1,"successful migration marks supplied saved storage");
    gardens.add(restored);register(restored);if(index==0)cropPositions.addAll(wheat);index++;
   }
   mine=full(DreamseekersMod.SHADOW_MINE,account.ownerName,30,95);furnace=full(DreamseekersMod.SHADOW_FURNACE,account.ownerName,90,95);
   for(var block:farm.getBlocks())if(block.getBlockState().is(Blocks.FARMLAND))level.setBlock(block.getBlockPos().above(),Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,7),2);
   repairer=worker(account.ownerName,gardens.get(0).originPos.offset(5,2,-6),true);
   farmer=worker(vanilla.ownerName,farm.originPos.offset(5,2,-6),false);
   check(!DreamseekersMod.REVERIE_GARDEN.allowsManualFarming()&&!DreamseekersMod.REVERIE_GARDEN.allowsCropReplant()&&!DreamseekersMod.REVERIE_GARDEN.allowsCropGrowth(),"garden opts out of all manual crop policies");
   check(Buildings.WHEAT_FARM.allowsManualFarming()&&Buildings.WHEAT_FARM.allowsCropReplant()&&Buildings.WHEAT_FARM.allowsCropGrowth(),"ordinary farm default policies unchanged");
   check(!((Block)DreamPassiveEconomy.DECORATIVE_WHEAT.get() instanceof CropBlock),"new block is not a CropBlock");
   check(DreamPassiveEconomy.DECORATIVE_WHEAT.get().getDrops(DreamPassiveEconomy.DECORATIVE_WHEAT.get().defaultBlockState(),null).isEmpty(),"decoration drops no resources even through loot entry");
   foodStart=account.food;oreStart=account.ore;shadowStart=account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0);
   order(repairer,UnitAction.FARM,gardens.get(0).originPos);check(((WorkerUnit)repairer).getGatherResourceGoal().getTargetFarm()==null,"native FARM denied, not UI-only");
   order(repairer,UnitAction.BUILD_REPAIR,gardens.get(0).originPos);order(farmer,UnitAction.FARM,farm.originPos);
  }
  void tick(){
   if(!lightingReady){
    lightingWait++;
    boolean ready=litCropPositions.stream().allMatch(p->level.getRawBrightness(p,0)>=8||level.canSeeSky(p));
    if(!ready){if(lightingWait>=100)check(false,"physical fixture light failed to reach crop positions after100ticks: "+litCropPositions.stream().filter(p->level.getRawBrightness(p,0)<8&&!level.canSeeSky(p)).map(p->p+" light="+level.getRawBrightness(p,0)).toList());return;}
    finishLitPreparation();lightingReady=true;return;
   }
   ticks++;
   if(ticks==100){check(account.food-foodStart==8,"four gardens each produce two food in100ticks");check(account.ore-oreStart==2,"mine produces two ore in100ticks");check(account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)-shadowStart==1,"furnace produces one shadow in100ticks");}
   if(ticks==100){
    check(score(account.ownerName,RTSPlayerScoresEnum.FOOD_HARVESTED)==8&&score(account.ownerName,RTSPlayerScoresEnum.ORES_HARVESTED)==2,"actual F/O credited updates original harvest categories");
    check(score(account.ownerName,RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED)==10,"S not misreported in originalFWO total");
   }
   if(ticks==102){
    ownerPeer.drain();otherPeer.drain();
    var result=ResourceTransaction.creditIncome(level.getServer(),account,account.accountEpoch,account.revision,new ResourceTransaction.Cost(1,0,0));
    int after=account.food;long revision=account.revision;
    DreamPassiveIncomePublication.publish(level.getServer(),PlayerServerEvents.getRTSPlayer(account.ownerName),result);
    var own=ownerPeer.drain();var other=otherPeer.drain();
    check(PassiveIncomePeer.hasFwo(own,account)&&PassiveIncomePeer.hasExtra(own,account),"online owner receives actual authoritative FWO and private extra snapshots");
    check(!PassiveIncomePeer.hasFwo(other,account)&&!PassiveIncomePeer.hasExtra(other,account),"other connected peer receives no publication for owner's balance");
    check(account.food==after&&account.revision==revision,"publication never mutates ledger a second time");
   }
   if(ticks==101)account.extra.put(DreamPassiveEconomy.SHADOW,1000); // fixture boundary, not a purchase
   if(ticks==201){check(account.extra.get(DreamPassiveEconomy.SHADOW)==1000,"full shadow capacity pauses without overflow");account.extra.put(DreamPassiveEconomy.SHADOW,999);}
   if(ticks==301)check(account.extra.get(DreamPassiveEconomy.SHADOW)==1000,"room resumes exactly one100tick production cycle");
   if(stage==0&&ticks>301){DreamGardenPlacement garden=gardens.get(0);boolean repaired=true;for(BlockPos p:cropPositions)repaired&=level.getBlockState(p).equals(DreamPassiveEconomy.DECORATIVE_WHEAT.get().defaultBlockState());
    boolean replanted=false;for(var block:farm.getBlocks())if(block.getBlockState().is(Blocks.FARMLAND)){var state=level.getBlockState(block.getBlockPos().above());if(state.is(Blocks.WHEAT)&&state.getValue(BlockStateProperties.AGE_7)==0)replanted=true;}
    if(repaired&&replanted){check(garden.getHealth()==garden.getMaxHealth()&&garden.getMaxHealth()==130,"real worker repaired every mismatched state to full130HP");check(account.wood<10000,"actual repair paid ordinary wood");check(vanilla.wood<10000,"original native farmer still pays/replants");check(Resources.getTotalResourcesFromItems(((Unit)farmer).getItems()).food>0,"original farmer actually harvested food");
     check(((WorkerUnit)repairer).getGatherResourceGoal().getTargetFarm()==null,"completed repair did not auto-switch garden into farming");
     BlockPos target=cropPositions.get(0);level.setBlock(target,Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,7),2);order(repairer,UnitAction.MOVE,target);stage=1;entered=ticks;
    }
   }else if(stage==1&&ticks-entered>=100){check(level.getBlockState(cropPositions.get(0)).is(Blocks.WHEAT),"player's foreign wheat preserved and not RTS harvested");check(Resources.getTotalResourcesFromItems(((Unit)repairer).getItems()).food==0,"garden worker never receives secondary food");
    captured.extra.put(DreamPassiveEconomy.SHADOW,17); // pre-existing/refunded S must not be erased by faction gates
    for(BuildingPlacement p:List.of(gardens.get(0),mine,furnace)) {
     Mob attacker=EntityRegistrar.PILLAGER_UNIT.get().create(level);((Unit)attacker).setOwnerName(captured.ownerName);attacker.setNoAi(true);
     attacker.moveTo(p.centrePos.getX()+.5,p.originPos.getY()+2,p.centrePos.getZ()+.5);level.addFreshEntity(attacker);mobs.add(attacker);
     check(PassiveCaptureFixture.capture(p,level)&&captured.ownerName.equals(p.ownerName),"canonical capture changes real placement owner");attacker.discard();
    }
    capturedFood=captured.food;capturedOre=captured.ore;stage=2;entered=ticks;
   }
   else if(stage==2&&ticks-entered>=80){
    check(captured.food-capturedFood==2&&captured.ore-capturedOre==2,"captured garden/mine produce exact F/O for a legal non-Dream owner with fresh clocks");
    check(captured.extra.get(DreamPassiveEconomy.SHADOW)==17,"captured furnace is paused, retaining pre-existing S");
    check(DreamPassiveEconomy.factionPauseReason(furnace)!=null,"paused furnace has an explicit reason");
    check(DreamPassiveEconomy.factionPauseReason(furnace).equals(DreamPassiveEconomy.factionPauseReason(
        furnace.getBuilding(),PlayerServerEvents.getRTSPlayer(furnace.ownerName))),"client-supplied owner policy matches server reason after capture");
    check(!DreamEconomyPolicy.showShadowBalance(false,0)&&DreamEconomyPolicy.showShadowBalance(false,17)&&DreamEconomyPolicy.shadowReadOnly(false),"zero non-Dream HUD hidden; positive balance read-only");
    ownerPeer.drain();otherPeer.drain();
    com.solegendary.reignofnether.research.economy.EconomyBalanceSync.send(otherPeer.actor);
    check(PassiveIncomePeer.hasExtra(otherPeer.drain(),captured)&&!PassiveIncomePeer.hasExtra(ownerPeer.drain(),captured),"non-Dream owner still gets own positiveS snapshot without sharing it");
    PlayerServerEvents.getRTSPlayer(captured.ownerName).faction=DreamseekersMod.DREAMSEEKERS;
    stage=3;entered=ticks;
   }else if(stage==3&&ticks-entered>=100){
    check(captured.extra.get(DreamPassiveEconomy.SHADOW)==18,"furnace resumes after100actual Dream-owner ticks without paused catch-up");
    check(DreamPassiveEconomy.factionPauseReason(furnace.getBuilding(),PlayerServerEvents.getRTSPlayer(furnace.ownerName))==null,
        "live owner faction change removes the shared status reason without caching");
    System.out.println("PASSIVE_GARDEN_C_PASS assertions="+assertions+" ticks="+ticks);h.succeed();stage=4;
   }
   if(ticks>=3000&&stage<4)check(false,"real worker repair/farm navigation timeout; inspect geometry, do not teleport or weaken assertions");
  }
  void cleanup(){if(ownerPeer!=null)ownerPeer.close();if(otherPeer!=null)otherPeer.close();for(String owner:owners){PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.removeAccount(owner);}for(Mob mob:mobs){Unit.fullResetBehaviours((Unit)mob);mob.discard();}BuildingServerEvents.getBuildings().removeAll(buildings);lampOriginals.forEach((pos,state)->level.setBlock(pos,state,3));for(long key:chunks){ChunkPos c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}}
 }
}
