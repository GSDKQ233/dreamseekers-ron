package com.dreamseekers;

import com.dreamseekers.economy.DreamConstructionPayment;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.survival.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.network.*;
import java.util.*;

/** Limited native packet/admin/Survival refund regressions; no production edits or ledger reflection. */
@GameTestHolder("dreamseekers_construction_price_modes") @PrefixGameTestTemplate(false)
public final class ConstructionPriceModeGameTests {
    private static final String BATCH="construction_price_modes";
    private static final List<ModeFixture> ACTIVE=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void priceModesCleanup(ServerLevel ignored){for(var f:ACTIVE)f.close();ACTIVE.clear();}
    @GameTest(templateNamespace="dreamseekers_construction_price_modes",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=200)
    public static void sandboxPacketPaysAndAdminNoAccountCannotMint(GameTestHelper h)throws Exception {
        var f=new ModeFixture(h,"Sandbox",true);ACTIVE.add(f);
        try {
            f.anchor();h.assertTrue(SandboxServer.isSandboxPlayer(f.owner),"actual NONE faction sandbox owner");
            var paidSite=f.site(60);int w=f.account.wood;
            f.packet(BuildingAction.PLACE,paidSite);
            var paid=f.find(paidSite);h.assertTrue(paid!=null,"normal GUI PLACE handler admits own sandbox building");
            h.assertTrue(f.account.wood==w-75,"sandbox normal PLACE actually debits75W");
            var snapshot=paid.getConstructionPaymentStorage().getData(DreamConstructionPayment.STATE).write();
            h.assertTrue(snapshot.getInt("schema")==1&&snapshot.getInt("wood")==75,"sandbox real debit not misclassified as free");
            float progress=paid.getBlocksPlacedPercent();w=f.account.wood;f.packet(BuildingAction.DESTROY,paidSite);
            h.assertTrue(!BuildingServerEvents.getBuildings().contains(paid),"authenticated paid cancellation actually removed original placement");
            h.assertTrue(f.account.wood==w+Math.round(75*(1-progress)),"authenticated GUI cancel refunds real sandbox debit");

            // Missing budget is rejected by ordinary GUI/native business rule, even for a legitimate sandbox owner.
            ResourcesServerEvents.resourcesList.remove(f.account);var denied=f.site(120);
            f.packet(BuildingAction.PLACE,denied);
            h.assertTrue(f.find(denied)==null&&ExtendedResourcesSync.account(f.owner)==null,"no account normal GUI placement is genuinely rejected");
            // Server-console admin command is the real authorized free-placement route, not a forged payment.
            var freeSite=f.site(180);String command="rtsapi building place dreamseekers:dream_depot \""+f.owner+"\" "+freeSite.getX()+" "+freeSite.getY()+" "+freeSite.getZ()+" false";
            int result=f.level.getServer().getCommands().performPrefixedCommand(f.level.getServer().createCommandSourceStack(),command);
            h.assertTrue(result>0,"authorized console admin command admitted free no-account placement");
            var free=f.find(freeSite);h.assertTrue(free!=null,"actual admin placement exists");
            var zero=free.getConstructionPaymentStorage().getData(DreamConstructionPayment.STATE).write();
            h.assertTrue(zero.getInt("schema")==1&&zero.getInt("wood")==0&&zero.getInt("ore")==0,"no-account admin placement has explicit zero snapshot");
            ResourcesServerEvents.resourcesList.add(f.account);w=f.account.wood;int o=f.account.ore;
            f.packet(BuildingAction.DESTROY,freeSite);
            h.assertTrue(!BuildingServerEvents.getBuildings().contains(free),"authenticated free cancellation actually succeeded, not merely rejected");
            h.assertTrue(f.account.wood==w&&f.account.ore==o,"later account availability cannot mint cancellation resources");
            h.succeed();
        } finally {f.close();ACTIVE.remove(f);}
    }
    @GameTest(templateNamespace="dreamseekers_construction_price_modes",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=200)
    public static void survivalNativePartialAndCompletedRefundRatios(GameTestHelper h)throws Exception {
        h.assertTrue(!SurvivalServerEvents.isEnabled(),"isolated fixture requires Survival initially disabled");
        var f=new ModeFixture(h,"Survival",false);ACTIVE.add(f);boolean changed=false;
        try {
            f.anchor();SurvivalServerEvents.enable(WaveDifficulty.EASY);changed=SurvivalServerEvents.isEnabled();h.assertTrue(changed,"real public Survival enable lifecycle");
            var partial=BuildingServerEvents.placeBuilding(DreamseekersMod.DREAM_DEPOT,f.site(60),Rotation.NONE,f.owner,new int[0],false,false,false,true);
            h.assertTrue(partial!=null,"real paid unfinished placement");f.placements.add(partial);
            int halfway=Math.max(1,partial.getBlocksTotal()/2),placed=0;
            for(var b:partial.getBlocks())if(!b.getBlockState().isAir()&&placed++<halfway)f.level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            partial.initPlacedBlocks();h.assertTrue(!partial.isBuilt&&partial.getBlocksPlacedPercent()>0&&partial.getBlocksPlacedPercent()<1,"real partial physical structure");
            float fraction=partial.getBlocksPlacedPercent();int w=f.account.wood;
            BuildingServerEvents.cancelBuilding(partial,f.owner);
            h.assertTrue(f.account.wood==w+Math.round(75*(1-fraction)),"Survival unfinished cancellation retains native remaining-fraction formula");
            var completed=BuildingServerEvents.placeBuilding(DreamseekersMod.DREAM_DEPOT,f.site(120),Rotation.NONE,f.owner,new int[0],false,false,false,true);
            h.assertTrue(completed!=null,"second real paid placement");f.placements.add(completed);
            for(var b:completed.getBlocks())f.level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            completed.initPlacedBlocks();completed.onBuilt();h.assertTrue(completed.isBuilt&&completed.getBlocksPlacedPercent()==1,"actual physical completion and public onBuilt lifecycle");
            w=f.account.wood;int quoted=DreamseekersMod.DREAM_DEPOT.cost.wood;
            try {DreamseekersMod.DREAM_DEPOT.cost.wood=999;BuildingServerEvents.cancelBuilding(completed,f.owner);}
            finally {DreamseekersMod.DREAM_DEPOT.cost.wood=quoted;}
            h.assertTrue(f.account.wood==w+Math.round(75*.5f),"Survival completed50% refund uses original75W not changed definition999W");
            w=f.account.wood;BuildingServerEvents.cancelBuilding(completed,f.owner);h.assertTrue(f.account.wood==w,"Survival duplicate cannot reclaim paid snapshot");
            h.succeed();
        } finally {if(SurvivalServerEvents.isEnabled())SurvivalServerEvents.reset();f.close();ACTIVE.remove(f);h.assertTrue(!SurvivalServerEvents.isEnabled(),"public reset restores disabled mode for later batches");}
    }
    private static final class ModeFixture {
        final GameTestHelper h;final ServerLevel level;final String owner;final Resources account;final PassiveIncomePeer peer;final boolean originalDepotReset=DreamseekersMod.DREAM_DEPOT.shouldDestroyOnReset;
        final List<BuildingPlacement> placements=new ArrayList<>();final Set<Long> chunks=new HashSet<>();
        ModeFixture(GameTestHelper h,String label,boolean sandbox){this.h=h;level=h.getLevel();owner="API54_PriceMode_"+label;h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null,"isolated owner");PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,sandbox?Factions.NONE:DreamseekersMod.DREAMSEEKERS));account=new Resources(owner,10000,10000,10000);ResourcesServerEvents.resourcesList.add(account);peer=new PassiveIncomePeer(level,owner);}
        BlockPos site(int x){BlockPos p=h.absolutePos(new BlockPos(x,1,40));for(int cx=(p.getX()-20)>>4;cx<=(p.getX()+25)>>4;cx++)for(int cz=(p.getZ()-20)>>4;cz<=(p.getZ()+25)>>4;cz++){long c=ChunkPos.asLong(cx,cz);if(!level.getForcedChunks().contains(c)){level.setChunkForced(cx,cz,true);chunks.add(c);}}for(int dx=-20;dx<=25;dx++)for(int dz=-20;dz<=25;dz++){level.setBlock(p.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=22;y++)level.setBlock(p.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);}return p;}
        void anchor(){var origin=site(0);for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(level),level,origin,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);var p=BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,level,origin,Rotation.NONE,owner,false);p.isBuilt=true;BuildingServerEvents.getBuildings().add(p);placements.add(p);}
        BuildingPlacement find(BlockPos pos){var p=BuildingServerEvents.getBuildings().stream().filter(b->b.originPos.equals(pos)&&b.ownerName.equals(owner)).findFirst().orElse(null);if(p!=null&&!placements.contains(p))placements.add(p);return p;}
        void packet(BuildingAction action,BlockPos pos)throws Exception {
            var constructor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);constructor.setAccessible(true);
            NetworkEvent.Context context=constructor.newInstance(peer.connection,NetworkDirection.PLAY_TO_SERVER,-1);
            h.assertTrue(context.getSender()==peer.actor,"actual Forge sender is registered own sandbox peer");
            new BuildingServerboundPacket(action,ReignOfNetherRegistries.BUILDING.getKey(DreamseekersMod.DREAM_DEPOT).toString(),pos,BlockPos.ZERO,Rotation.NONE,owner,new int[0],false).handle(()->context);
        }
        void close(){DreamseekersMod.DREAM_DEPOT.shouldDestroyOnReset=originalDepotReset;peer.close();PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(owner));for(var p:placements){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}for(long c:chunks){var p=new ChunkPos(c);level.setChunkForced(p.x,p.z,false);}}
    }
}
