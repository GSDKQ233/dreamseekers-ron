package com.dreamseekers;

import com.dreamseekers.economy.DreamConstructionPayment;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Two actual JVMs, normal disk save/load. Verify never fabricates a restored placement/account. */
@GameTestHolder("dreamseekers_construction_price_restart") @PrefixGameTestTemplate(false)
public final class ConstructionPriceRestartGameTests {
    private static final String OWNER="ConstructionPriceRestart_Owner",RECIPIENT="ConstructionPriceRestart_Recipient";
    private static final File EXPECTED=new File("construction-price-restart-expectations.nbt");
    private static final BlockPos ORIGIN=new BlockPos(14336,100,14336);
    private static final List<Long> CHUNKS=new ArrayList<>();
    private static final List<PassiveIncomePeer> PEERS=new ArrayList<>();
    private static final Map<String,BuildingPlacement> PREPARED=new LinkedHashMap<>();
    private static final Map<String,int[]> AMOUNTS=new LinkedHashMap<>();
    @GameTest(templateNamespace="dreamseekers_construction_price_restart",template="ore_mine_test_arena",batch="construction_price_restart",timeoutTicks=500)
    public static void nativeSavedOriginalConstructionPayments(GameTestHelper h)throws Exception {
        switch(System.getProperty("dreamseekers.construction.price.restart.phase","")) {
            case "prepare" -> prepare(h);
            case "verify" -> verify(h);
            default -> throw new IllegalArgumentException("dreamseekers.construction.price.restart.phase prepare|verify required");
        }
    }
    private static Resources account(String owner){return ExtendedResourcesSync.account(owner);}
    private static Resources createAccount(GameTestHelper h,String owner,int id) {
        h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null&&account(owner)==null,"fresh isolated owner");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(owner,DreamseekersMod.DREAMSEEKERS,id,0,false,0L,0L));
        Resources r=new Resources(owner,10000,10000,10000);ResourcesServerEvents.resourcesList.add(r);PEERS.add(new PassiveIncomePeer(h.getLevel(),owner));return r;
    }
    private static BlockPos site(ServerLevel level,int x) {
        BlockPos p=ORIGIN.offset(x,0,0);
        for(int cx=(p.getX()-20)>>4;cx<=(p.getX()+25)>>4;cx++)for(int cz=(p.getZ()-20)>>4;cz<=(p.getZ()+25)>>4;cz++){
            long key=ChunkPos.asLong(cx,cz);if(!level.getForcedChunks().contains(key)){level.setChunkForced(cx,cz,true);CHUNKS.add(key);}
        }
        for(int dx=-20;dx<=25;dx++)for(int dz=-20;dz<=25;dz++){level.setBlock(p.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=22;y++)level.setBlock(p.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);}return p;
    }
    private static void anchor(ServerLevel level,String owner,int x) {
        BlockPos p=site(level,x);for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(level),level,p,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        var placement=BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,level,p,Rotation.NONE,owner,false);placement.isBuilt=true;BuildingServerEvents.getBuildings().add(placement);
    }
    private static BuildingPlacement buy(GameTestHelper h,String key,Building b,int x,boolean command,int w,int o) {
        int beforeW=account(OWNER).wood,beforeO=account(OWNER).ore;
        var p=BuildingServerEvents.placeBuilding(b,site(h.getLevel(),x),Rotation.NONE,OWNER,new int[0],false,false,command,true);
        h.assertTrue(p!=null,"actual placement succeeds "+key);
        h.assertTrue(account(OWNER).wood==beforeW-w&&account(OWNER).ore==beforeO-o,"actual debit "+key);
        PREPARED.put(key,p);AMOUNTS.put(key,new int[]{w,o});return p;
    }
    private static void prepare(GameTestHelper h) {
        h.assertTrue(!EXPECTED.exists(),"fresh external witness");CHUNKS.clear();PEERS.clear();PREPARED.clear();AMOUNTS.clear();
        createAccount(h,OWNER,96541);createAccount(h,RECIPIENT,96542);anchor(h.getLevel(),OWNER,0);anchor(h.getLevel(),RECIPIENT,60);
        buy(h,"newMine",DreamseekersMod.SHADOW_MINE,120,false,300,150);
        var transferred=buy(h,"transferredDepot",DreamseekersMod.DREAM_DEPOT,180,false,75,0);transferred.ownerName=RECIPIENT;
        // Deliberately old-format saved building: no construction-payment DataType and no API54 purchase.
        var legacy=BuildingUtils.getNewBuildingPlacement(DreamseekersMod.BEETLE_SANCTUARY,h.getLevel(),site(h.getLevel(),240),Rotation.NONE,OWNER,false);
        // Physical partially built old-format fixture survives ordinary ticks and normal save/reload.
        int legacySolids=0;for(var block:legacy.getBlocks())if(!block.getBlockState().isAir()&&legacySolids++<20)h.getLevel().setBlock(block.getBlockPos(),block.getBlockState(),2);
        legacy.initPlacedBlocks();h.assertTrue(legacy.getBlocksPlaced()>0&&legacy.getBlocksPlaced()<legacy.getBlocksTotal(),"physical legacy fixture is genuinely partially built");
        BuildingServerEvents.getBuildings().add(legacy);PREPARED.put("legacySanctuary",legacy);AMOUNTS.put("legacySanctuary",new int[]{160,60});
        buy(h,"freeDepot",DreamseekersMod.DREAM_DEPOT,300,true,0,0);
        var opaque=buy(h,"futureDepot",DreamseekersMod.DREAM_DEPOT,360,false,75,0);
        CompoundTag future=new CompoundTag();future.putInt("schema",999);future.putString("future","preserve-me");opaque.getConstructionPaymentStorage().setData(DreamConstructionPayment.STATE,DreamConstructionPayment.State.read(future));AMOUNTS.put("futureDepot",new int[]{0,0});
        account(OWNER).resetExtendedAccount(); // old payer epoch must not change published recipient semantics
        // Keep this payment/refund fixture partially built. API55 persists queued work, so the stop
        // witness records exact original physical blocks and pending work; startup may legitimately
        // consume that saved queue, but may never reconstruct any other missing geometry.
        h.startSequence().thenWaitUntil(()->{
            for(var entry:PREPARED.entrySet()) {
                var p=entry.getValue();
                h.assertTrue(BuildingServerEvents.getBuildings().contains(p)&&!p.isBuilt
                        &&p.getBlocksPlaced()>0&&physicalBlocks(p)>0&&!p.shouldBeDestroyed(),
                        "original real foundation must exist before normal stop: "+entry.getKey()+" cached="+p.getBlocksPlaced()+" physical="+physicalBlocks(p));
            }
        }).thenIdle(2).thenExecute(()->{
            for(var entry:PREPARED.entrySet()) {
                var p=entry.getValue();h.assertTrue(BuildingServerEvents.getBuildings().contains(p)&&p.getBlocksPlaced()>0&&physicalBlocks(p)>0&&!p.isBuilt&&!p.shouldBeDestroyed(),"original partial placement survives ordinary ticks: "+entry.getKey());
                System.out.println("CONSTRUCTION_PRICE_RESTART_READY key="+entry.getKey()+" origin="+p.originPos+" cached="+p.getBlocksPlaced()+" physical="+physicalBlocks(p));
            }
            MinecraftForge.EVENT_BUS.register(new ConstructionPriceStopWitness());
            PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(h.getLevel());ResourcesServerEvents.saveResources(h.getLevel());
            System.out.println("CONSTRUCTION_PRICE_RESTART_PREPARED");
        }).thenSucceed();
    }
    private static long physicalBlocks(BuildingPlacement p) {return p.getBlocks().stream().filter(block->!block.getBlockState().isAir()&&block.isPlaced(p.getLevel())).count();}

    private static ListTag geometry(BuildingPlacement p) {
        ListTag result=new ListTag();
        for(var block:p.getBlocks()) {
            CompoundTag t=new CompoundTag();t.putLong("pos",block.getBlockPos().asLong());
            t.put("state",NbtUtils.writeBlockState(block.getBlockState()));
            if(block.getBlockNbt()!=null)t.put("nbt",block.getBlockNbt().copy());
            result.add(t);
        }
        return result;
    }
    private static ListTag physicalSnapshot(BuildingPlacement p) {
        ListTag result=new ListTag();
        for(var block:p.getBlocks())if(!block.getBlockState().isAir()&&block.isPlaced(p.getLevel())) {
            CompoundTag t=new CompoundTag();t.putLong("pos",block.getBlockPos().asLong());
            t.put("state",NbtUtils.writeBlockState(p.getLevel().getBlockState(block.getBlockPos())));result.add(t);
        }
        return result;
    }
    private static Map<Long,CompoundTag> positions(ListTag entries) {
        Map<Long,CompoundTag> result=new LinkedHashMap<>();
        for(Tag raw:entries){CompoundTag entry=(CompoundTag)raw;result.put(entry.getLong("pos"),entry.getCompound("state"));}
        return result;
    }
    private static void verifyPhysicalContinuation(GameTestHelper h,BuildingPlacement p,CompoundTag saved,String key) {
        h.assertTrue(geometry(p).equals(saved.getList("geometry",Tag.TAG_COMPOUND)),"exact original geometry definition and block NBT "+key);
        var before=positions(saved.getList("physical",Tag.TAG_COMPOUND));
        var now=positions(physicalSnapshot(p));
        var pending=saved.getCompound("pending");
        h.assertTrue(pending.getInt("version")==1,"known actual stop-time queue schema "+key);
        var authorized=positions(pending.getList("blocks",Tag.TAG_COMPOUND));
        h.assertTrue(before.size()>0&&before.size()==saved.getLong("physicalBlocks")
                &&before.size()==saved.getInt("blocks"),"complete nonempty stop-time physical witness "+key);
        for(var entry:before.entrySet())h.assertTrue(entry.getValue().equals(now.get(entry.getKey())),
                "original physical position/state remains unchanged "+key+" pos="+BlockPos.of(entry.getKey()));
        int added=0;
        for(var entry:now.entrySet())if(!before.containsKey(entry.getKey())) {
            added++;
            h.assertTrue(entry.getValue().equals(authorized.get(entry.getKey())),
                    "new physical block is exactly authorized by saved pending work "+key+" pos="+BlockPos.of(entry.getKey()));
        }
        var remaining=positions(p.savePendingConstruction().getList("blocks",Tag.TAG_COMPOUND));
        for(var entry:remaining.entrySet())h.assertTrue(entry.getValue().equals(authorized.get(entry.getKey())),
                "remaining queue contains no invented work "+key);
        for(var entry:authorized.entrySet())h.assertTrue(entry.getValue().equals(remaining.get(entry.getKey()))
                        ||entry.getValue().equals(now.get(entry.getKey())),
                "each original pending block remains queued or was physically consumed "+key);
        h.assertTrue(p.getBlocksPlaced()==now.size()&&now.size()==before.size()+added,
                "cached physical count agrees exactly with original body plus authorized additions "+key);
        System.out.println("CONSTRUCTION_PRICE_RESTART_GEOMETRY key="+key+" original="+before.size()
                +" addedFromSavedQueue="+added+" savedQueue="+authorized.size()+" remainingQueue="+remaining.size());
    }

    /** Unique class/method avoids Forge-generated same-name subscriber wrapper collisions. */
    private static final class ConstructionPriceStopWitness {
        @SubscribeEvent(priority=EventPriority.HIGH) public void constructionPriceStopping(ServerStoppingEvent event) {
            CompoundTag root=new CompoundTag();root.putInt("schema",2);root.putLong("jvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());root.putBoolean("normalStop",true);root.putLongArray("chunks",CHUNKS);
            ListTag entries=new ListTag();for(var entry:PREPARED.entrySet()){
                var p=entry.getValue();
                if(!BuildingServerEvents.getBuildings().contains(p)||p.getBlocksPlaced()<=0||physicalBlocks(p)<=0||p.isBuilt||p.shouldBeDestroyed())throw new AssertionError("stop witness requires live original partial physical body: "+entry.getKey());
                CompoundTag t=new CompoundTag();t.putString("key",entry.getKey());t.putString("owner",p.ownerName);t.putLong("origin",p.originPos.asLong());t.putBoolean("built",p.isBuilt);t.putInt("blocks",p.getBlocksPlaced());
                t.putLong("physicalBlocks",physicalBlocks(p));
                t.put("geometry",geometry(p));
                t.put("physical",physicalSnapshot(p));
                t.put("pending",p.savePendingConstruction());
                if(p instanceof com.dreamseekers.function.DreamFunctionalView view) {
                    UUID instance=view.stateSnapshot().instanceId();
                    if(instance==null)throw new AssertionError("original functional identity not established before stop: "+entry.getKey());
                    t.putUUID("functionalInstance",instance);
                }
                int[] amount=AMOUNTS.get(entry.getKey());t.putInt("paidW",amount[0]);t.putInt("paidO",amount[1]);
                if(!entry.getKey().equals("legacySanctuary"))t.put("snapshot",p.getConstructionPaymentStorage().getData(DreamConstructionPayment.STATE).write());entries.add(t);
            }root.put("entries",entries);
            for(String owner:new String[]{OWNER,RECIPIENT}){Resources r=account(owner);CompoundTag t=new CompoundTag();t.putInt("food",r.food);t.putInt("wood",r.wood);t.putInt("ore",r.ore);t.putUUID("epoch",r.accountEpoch);root.put(owner,t);}
            try{NbtIo.writeCompressed(root,EXPECTED);}catch(Exception e){throw new RuntimeException(e);}
            for(var peer:PEERS)peer.close();PEERS.clear();System.out.println("CONSTRUCTION_PRICE_RESTART_STOP_WITNESS");
        }
    }
    private static void verify(GameTestHelper h)throws Exception {
        CompoundTag expected=NbtIo.readCompressed(EXPECTED);
        h.assertTrue(expected.getInt("schema")==2&&expected.getBoolean("normalStop")&&expected.getLong("jvm")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"actual separate JVM normal-stop witness");
        var savedEntries=expected.getList("entries",Tag.TAG_COMPOUND);var savedKeys=new HashSet<String>();
        for(Tag raw:savedEntries)savedKeys.add(((CompoundTag)raw).getString("key"));
        h.assertTrue(savedEntries.size()==5&&savedKeys.equals(Set.of("newMine","transferredDepot","legacySanctuary","freeDepot","futureDepot")),"complete exact five original restart subjects, no vacuous missing-entry pass");
        for(long key:expected.getLongArray("chunks")){h.assertTrue(h.getLevel().getForcedChunks().contains(key),"ordinary saved force ticket");var c=new ChunkPos(key);h.getLevel().getChunk(c.x,c.z);}
        for(String owner:new String[]{OWNER,RECIPIENT}){Resources r=account(owner);CompoundTag t=expected.getCompound(owner);h.assertTrue(r!=null&&r.accountEpoch.equals(t.getUUID("epoch"))&&r.food==t.getInt("food")&&r.wood==t.getInt("wood")&&r.ore==t.getInt("ore"),"native account restored exact balance/epoch "+owner);}
        for(Tag raw:expected.getList("entries",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)raw;String key=t.getString("key");
            var matches=BuildingServerEvents.getBuildings().stream().filter(p->p.getLevel()==h.getLevel()&&p.originPos.asLong()==t.getLong("origin")).toList();
            h.assertTrue(matches.size()==1,"exact native loaded original placement "+key);var p=matches.get(0);
            h.assertTrue(p.ownerName.equals(t.getString("owner")),"original transferred owner "+key);
            verifyPhysicalContinuation(h,p,t,key);
            if(t.hasUUID("functionalInstance"))h.assertTrue(p instanceof com.dreamseekers.function.DreamFunctionalView view&&t.getUUID("functionalInstance").equals(view.stateSnapshot().instanceId()),"same persisted original functional UUID, not a replacement "+key);
            if(t.getInt("paidW")>0||t.getInt("paidO")>0)h.assertTrue(!p.isBuilt&&!t.getBoolean("built"),"paid/legacy fixture remains unfinished "+key);
            if(t.contains("snapshot"))h.assertTrue(p.getConstructionPaymentStorage().getData(DreamConstructionPayment.STATE).write().equals(t.getCompound("snapshot")),"exact persisted snapshot including zero/future "+key);
            Resources r=account(p.ownerName);int w=r.wood,o=r.ore;float progress=p.getBlocksPlacedPercent();
            BuildingServerEvents.cancelBuilding(p,p.ownerName);
            h.assertTrue(r.wood==w+Math.round(t.getInt("paidW")*(1-progress))&&r.ore==o+Math.round(t.getInt("paidO")*(1-progress)),"native loaded cancellation uses original amount / legacy60O "+key);
            w=r.wood;o=r.ore;BuildingServerEvents.cancelBuilding(p,p.ownerName);h.assertTrue(r.wood==w&&r.ore==o,"native loaded repeat cannot replay "+key);
            if(key.equals("futureDepot"))h.assertTrue(p.getConstructionPaymentStorage().getData(DreamConstructionPayment.STATE).write().equals(t.getCompound("snapshot")),"unknown payload survives cancellation unchanged");
        }
        System.out.println("CONSTRUCTION_PRICE_RESTART_VERIFIED");h.succeed();
    }
}
