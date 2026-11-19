// Audit-only external probe. Product code is neither replaced nor manually deserialized in verify.
package com.dreamseekers;

import com.dreamseekers.worldtree.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Separate clean worlds per source revision. Run prepare, archive its whole world, then verify in a new JVM.
 * The ABA is performed at the final server END/LOWEST callback, AFTER native building LevelTick.
 * halt(false) requests an ordinary graceful stop before another server tick. A HIGH stop witness proves
 * the native tree's lastTick did not advance after ABA; verify rejects evidence if that window was missed.
 * Native Resources/Buildings/RTS save/load and ordinary Anvil save own persisted objects. No injection.
 */
@GameTestHolder("dreamseekers_audit_capture_restart")
@PrefixGameTestTemplate(false)
public final class ResourceAuditWorldTreeRestartGameTests {
    private static final File MANIFEST=new File("resource-audit-tree-aba-restart.nbt");
    private static final String PREFIX="ResourceAuditTreeRestart_";
    private static final BlockPos ORIGIN=new BlockPos(18432,100,18432);
    private static final List<Case> CASES=new ArrayList<>();
    private static final Set<Long> CHUNKS=new LinkedHashSet<>();
    private static final class Case {
        final String owner; final Resources account; final DreamAnchorPlacement tree; final PassiveIncomePeer peer;
        UUID receipt; long lastTickAtAba; CompoundTag before,after;
        Case(String o,Resources r,DreamAnchorPlacement p,PassiveIncomePeer w){owner=o;account=r;tree=p;peer=w;}
    }
    private static CompoundTag accountTag(Resources r){
        var t=new CompoundTag();t.putInt("food",r.food);t.putInt("wood",r.wood);t.putInt("ore",r.ore);t.putInt("emerald",r.emerald);r.writeExtended(t);return t;
    }
    private static CompoundTag observation(Case c){
        var t=new CompoundTag();t.put("state",c.tree.tree().write());t.put("account",accountTag(c.account));
        t.putLong("captureRevision",c.tree.getCaptureRevision());t.putLong("lastTreeTick",c.tree.tree().lastTick);t.putString("owner",c.tree.ownerName);return t;
    }
    private static Case install(GameTestHelper h,int n){
        var level=h.getLevel();String owner=PREFIX+n;BlockPos at=ORIGIN.offset(n*40,0,0);
        h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null,"fresh isolated owner");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(owner,DreamseekersMod.DREAMSEEKERS,98440+n,0,false,0L,0L));
        var r=new Resources(owner,10000,10000,10000);r.extra.put(DreamPassiveEconomy.SHADOW,1000);ResourcesServerEvents.resourcesList.add(r);
        for(int x=(at.getX()-4)>>4;x<=(at.getX()+16)>>4;x++)for(int z=(at.getZ()-4)>>4;z<=(at.getZ()+16)>>4;z++){
            long key=ChunkPos.asLong(x,z);if(CHUNKS.add(key)){h.assertTrue(!level.getForcedChunks().contains(key),"fresh forced chunk");level.setChunkForced(x,z,true);}
        }
        for(int x=-2;x<14;x++)for(int z=-2;z<12;z++){
            level.setBlock(at.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<20;y++)level.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(level),level,at,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        var p=(DreamAnchorPlacement)BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,level,at,Rotation.NONE,owner,false);
        p.isBuilt=true;BuildingServerEvents.getBuildings().add(p);var peer=new PassiveIncomePeer(level,owner);
        h.assertTrue(DreamWorldTreeService.start(peer.actor,p,p.tree().instance).equals("started"),"actual paid upgrade admitted");
        if(n==1)h.assertTrue(ResourceTransaction.creditIncome(level.getServer(),r,r.accountEpoch,r.revision,new ResourceTransaction.Cost(Integer.MAX_VALUE,0,0)).applied()&&r.food==Integer.MAX_VALUE,"real capped income creates refund-overflow boundary");
        var c=new Case(owner,r,p,peer);c.receipt=p.tree().receipt;return c;
    }
    private static final class FinalTickAndStopWitness {
        private final GameTestHelper h;private boolean armed=true;private long operationGameTime;
        FinalTickAndStopWitness(GameTestHelper h){this.h=h;}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void finalTick(TickEvent.ServerTickEvent e){
            if(!armed||e.phase!=TickEvent.Phase.END||e.getServer()!=h.getLevel().getServer())return;armed=false;
            operationGameTime=h.getLevel().getGameTime();
            for(var c:CASES){
                h.assertTrue(c.tree.tree().phase==WorldTreeState.Phase.RUNNING&&c.account.paidReceipts.containsKey(c.receipt),"live paid task before supported ABA");
                c.lastTickAtAba=c.tree.tree().lastTick;c.before=observation(c);
                SandboxServer.setBuildingOwner(c.tree.originPos,c.owner+"Other");
                SandboxServer.setBuildingOwner(c.tree.originPos,c.owner);
                c.after=observation(c);
                h.assertTrue(c.tree.getCaptureRevision()==c.before.getLong("captureRevision")+2,"both supported transitions advanced capture authority");
                h.assertTrue(c.tree.tree().lastTick==c.lastTickAtAba,"no native tree tick during ABA/save callback");
            }
            PlayerServerEvents.saveRTSPlayers();ResourcesServerEvents.saveResources(h.getLevel());BuildingServerEvents.saveBuildings(h.getLevel());
            System.out.println("RESOURCE_AUDIT_TREE_ABA_PREPARED gameTime="+operationGameTime+" before_native_stop=true");
            h.succeed();e.getServer().halt(false); // ordinary graceful server stop; never kill the process or invoke load manually
        }
        @SubscribeEvent(priority=EventPriority.HIGH) public void stop(ServerStoppingEvent e){
            if(armed)return;
            var root=new CompoundTag();root.putLong("jvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
            root.putLong("operationGameTime",operationGameTime);root.putLong("stopGameTime",h.getLevel().getGameTime());
            root.putLongArray("chunks",new ArrayList<>(CHUNKS));var list=new ListTag();boolean noInterveningTick=true;
            for(var c:CASES){
                var t=new CompoundTag();t.putString("owner",c.owner);t.putLong("origin",c.tree.originPos.asLong());t.putUUID("receipt",c.receipt);t.putUUID("epoch",c.account.accountEpoch);t.putUUID("instance",c.tree.tree().instance);
                t.put("beforeAba",c.before);t.put("afterAba",c.after);t.put("atStop",observation(c));
                boolean same=c.tree.tree().lastTick==c.lastTickAtAba;noInterveningTick&=same;t.putBoolean("noInterveningTreeTick",same);list.add(t);
            }
            root.put("cases",list);root.putBoolean("normalStopWitness",true);root.putBoolean("noInterveningTreeTick",noInterveningTick);
            try{NbtIo.writeCompressed(root,MANIFEST);}catch(Exception ex){throw new RuntimeException(ex);}
            for(var c:CASES)c.peer.close();
            System.out.println("RESOURCE_AUDIT_TREE_ABA_NORMAL_STOP noInterveningTreeTick="+noInterveningTick+" gameTime="+h.getLevel().getGameTime());
        }
    }
    @GameTest(templateNamespace="dreamseekers_audit_capture_restart",template="ore_mine_test_arena",batch="resource_audit_tree_restart",timeoutTicks=160)
    public static void supportedEditorAbaSurvivesRealJvmRestart(GameTestHelper h)throws Exception{
        switch(System.getProperty("dreamseekers.resource.audit.restart.phase","")){
            case "prepare"->{
                h.assertTrue(!MANIFEST.exists(),"fresh isolated restart manifest");CASES.clear();CHUNKS.clear();
                CASES.add(install(h,0));CASES.add(install(h,1));
                MinecraftForge.EVENT_BUS.register(new FinalTickAndStopWitness(h));
            }
            case "verify"->verify(h);
            default->throw new IllegalArgumentException("dreamseekers.resource.audit.restart.phase=prepare|verify required");
        }
    }
    private static void verify(GameTestHelper h)throws Exception{
        h.assertTrue(MANIFEST.isFile(),"ordinary stop witness exists");var root=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(root.getLong("jvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"independent JVM, not a codec simulation");
        h.assertTrue(root.getBoolean("normalStopWitness")&&root.getBoolean("noInterveningTreeTick"),"ABA/save-before-next-tree-tick window was actually observed; otherwise result is inconclusive");
        var restored=new ArrayList<DreamAnchorPlacement>();var accounts=new ArrayList<Resources>();var rows=root.getList("cases",Tag.TAG_COMPOUND);
        h.assertTrue(rows.size()==2,"both available-room and overflow cases persisted");
        for(int i=0;i<rows.size();i++){
            var t=rows.getCompound(i);String owner=t.getString("owner");var r=ExtendedResourcesSync.account(owner);
            h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)!=null&&r!=null&&r.accountEpoch.equals(t.getUUID("epoch")),"native RTS/account loader restored original owner and epoch");
            var p=(DreamAnchorPlacement)BuildingServerEvents.getBuildings().stream().filter(b->b.originPos.asLong()==t.getLong("origin")&&b.ownerName.equals(owner)).findFirst().orElseThrow();
            h.assertTrue(p.tree().instance.equals(t.getUUID("instance"))&&p.tree().model==WorldTreeModel.BASE_V3,"native building loader restored original base identity");
            restored.add(p);accounts.add(r);UUID receipt=t.getUUID("receipt");
            System.out.println("RESOURCE_AUDIT_TREE_ABA_VERIFY owner="+owner+" phase="+p.tree().phase+" remaining="+p.tree().remaining+" food="+r.food+" receipt="+r.paidReceipts.get(receipt));
            if(i==0){
                h.assertTrue(p.tree().phase==WorldTreeState.Phase.IDLE&&!r.paidReceipts.containsKey(receipt)&&r.food==10000&&r.wood==10000&&r.ore==10000&&r.extra.get(DreamPassiveEconomy.SHADOW)==1000,"normal restart must not resume captured original upgrade; full original refund exactly once");
            }else{
                var paid=r.paidReceipts.get(receipt);
                h.assertTrue(p.tree().phase==WorldTreeState.Phase.REFUND_PENDING&&paid!=null&&paid.payerEpoch().equals(r.accountEpoch)&&paid.purpose().equals(DreamWorldTreeService.purpose(WorldTreeModel.SHADOW_V3)+":refund"),"overflow retains original-epoch earned refund intent instead of resumed upgrade");
                h.assertTrue(r.food==Integer.MAX_VALUE&&r.wood==9200&&r.ore==9400&&r.extra.get(DreamPassiveEconomy.SHADOW)==850,"no partial overflow refund");
                h.assertTrue(ResourceTransaction.exchange(h.getLevel().getServer(),r,r.accountEpoch,r.revision,new ResourceTransaction.Cost(600,0,0),ResourceTransaction.Cost.ZERO).applied(),"actual debit frees exact original refund room");
            }
        }
        h.runAtTickTime(40,()->{
            var r=accounts.get(1);UUID receipt=rows.getCompound(1).getUUID("receipt");
            h.assertTrue(!r.paidReceipts.containsKey(receipt)&&r.food==Integer.MAX_VALUE&&r.wood==10000&&r.ore==10000&&r.extra.get(DreamPassiveEconomy.SHADOW)==1000,"normal detached intent retry paid exact original amounts once");
            var snapshot=accountTag(r);WorldTreeReceiptIntents.retry(h.getLevel());h.assertTrue(snapshot.equals(accountTag(r)),"fresh retry does not pay twice");
            for(var p:restored){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(a->a.ownerName.startsWith(PREFIX));
            for(long key:root.getLongArray("chunks")){var c=new ChunkPos(key);h.getLevel().setChunkForced(c.x,c.z,false);}
            System.out.println("RESOURCE_AUDIT_TREE_ABA_TWO_JVM_PASS original_epoch exact_refund overflow_retry no_replay");h.succeed();
        });
    }
}
