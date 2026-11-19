package com.dreamseekers;

import com.dreamseekers.economy.DreamConstructionPayment;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real placement debit/cancellation plus codec roundtrip; separate native restart gate is still required. */
@GameTestHolder("dreamseekers_construction_prices") @PrefixGameTestTemplate(false)
public final class ConstructionPriceGameTests {
    private static final String BATCH="dreamseekers_construction_prices";
    private static final List<PriceFixture> ACTIVE=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void priceFixtureCleanup(ServerLevel ignored){for(var f:ACTIVE)f.close();ACTIVE.clear();}
    @GameTest(templateNamespace="dreamseekers_construction_prices",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=200)
    public static void realPaymentLegacyFreeMalformedAndTransfer(GameTestHelper h) throws Exception {
        var f=new PriceFixture(h);ACTIVE.add(f);
        try {
            f.base();
            var debit=BuildingServerEvents.class.getDeclaredMethod("constructionDebit",int.class,int.class);debit.setAccessible(true);
            h.assertTrue((Integer)debit.invoke(null,100,25)==75,"actual debit observation");
            h.assertTrue((Integer)debit.invoke(null,Integer.MIN_VALUE,2147483573)==0,"negative balance wrap cannot prove payment");
            h.assertTrue((Integer)debit.invoke(null,0,-75)==0&&(Integer)debit.invoke(null,50,75)==0,"negative ending balance / credit cannot prove payment");
            Resources duplicate=new Resources(f.owner,1000,1000,1000);ResourcesServerEvents.resourcesList.add(duplicate);
            BuildingPlacement ambiguous;
            try {ambiguous=f.place(DreamseekersMod.DREAM_DEPOT,540,false);h.assertTrue(duplicate.wood==925,"native duplicate fixture really follows debit path");}
            finally {ResourcesServerEvents.resourcesList.remove(duplicate);}
            f.cancelExpect(ambiguous,0,0,"ambiguous duplicate account cannot mint proved payment");
            int w=f.account.wood,o=f.account.ore;
            var paid=f.place(DreamseekersMod.SHADOW_MINE,60,false);
            h.assertTrue(f.account.wood==w-300&&f.account.ore==o-150,"new mine real placement debit300W150O");
            // Snapshot must survive codec, not merely an in-memory ResourceCost reference.
            var saved=DataStorage.read(paid.getDataStorage().write(),f.level.getServer());
            paid.getDataStorage().setData(DreamConstructionPayment.STATE,saved.getData(DreamConstructionPayment.STATE));
            f.cancelExpect(paid,300,150,"new paid codec roundtrip");
            int once=f.account.wood;BuildingServerEvents.cancelBuilding(paid,f.owner);
            h.assertTrue(f.account.wood==once,"repeat native cancellation cannot claim snapshot twice");
            var free=f.place(DreamseekersMod.DREAM_DEPOT,120,true);f.cancelExpect(free,0,0,"command explicitly zero");
            var changed=f.place(DreamseekersMod.DREAM_DEPOT,480,false);
            int currentWood=DreamseekersMod.DREAM_DEPOT.cost.wood;
            try {DreamseekersMod.DREAM_DEPOT.cost.wood=999;f.cancelExpect(changed,75,0,"definition price changes never reprice paid construction");}
            finally {DreamseekersMod.DREAM_DEPOT.cost.wood=currentWood;}
            var legacy=f.unpaidLegacy(DreamseekersMod.SHADOW_MINE,180);
            f.cancelExpect(legacy,100,50,"legacy no record uses API53 not API54");
            var lazyLegacy=f.unpaidLegacy(DreamseekersMod.DREAM_DEPOT,420);
            var oldState=lazyLegacy.getConstructionPaymentStorage().getData(DreamConstructionPayment.STATE);
            lazyLegacy.getConstructionPaymentStorage().setData(DreamConstructionPayment.STATE,DreamConstructionPayment.State.read(oldState.write()));
            f.cancelExpect(lazyLegacy,100,50,"lazy legacy marker roundtrip retains API53");
            h.assertTrue(!DreamConstructionPayment.State.read(new CompoundTag()).supported(),"serialized empty schema is malformed, not legacy");
            CompoundTag malformed=new CompoundTag();malformed.putInt("schema",1);malformed.putString("payer",f.owner);malformed.putBoolean("claimed",false);malformed.putInt("food",0);malformed.putInt("wood",75);malformed.putInt("ore",0);
            h.assertTrue(!DreamConstructionPayment.State.read(malformed).supported(),"positive payment without epoch rejected");
            malformed.putUUID("epoch",f.account.accountEpoch);malformed.putInt("wood",-1);
            h.assertTrue(!DreamConstructionPayment.State.read(malformed).supported(),"negative payment rejected");
            var oldSanctuary=f.unpaidLegacy(DreamseekersMod.BEETLE_SANCTUARY,240);
            f.cancelExpect(oldSanctuary,160,60,"legacy sanctuary retains60O despite new50O");
            var corrupt=f.place(DreamseekersMod.DREAM_DEPOT,300,false);
            CompoundTag bad=new CompoundTag();bad.putInt("schema",999);bad.putString("future","preserved");
            corrupt.getDataStorage().setData(DreamConstructionPayment.STATE,DreamConstructionPayment.State.read(bad));
            f.cancelExpect(corrupt,0,0,"future schema fails closed");
            h.assertTrue(corrupt.getDataStorage().getData(DreamConstructionPayment.STATE).write().equals(bad),"future bytes preserved");
            var transferred=f.place(DreamseekersMod.DREAM_DEPOT,360,false);
            String recipient=f.owner+"_Recipient";
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(recipient,DreamseekersMod.DREAMSEEKERS));
            Resources recipientAccount=new Resources(recipient,1000,1000,1000);ResourcesServerEvents.resourcesList.add(recipientAccount);
            try {
                f.account.resetExtendedAccount(); // original payer generation does not change transferred-building semantics
                transferred.ownerName=recipient;
                float progress=transferred.getBlocksPlacedPercent();int originalWood=f.account.wood;
                BuildingServerEvents.cancelBuilding(transferred,recipient);
                h.assertTrue(recipientAccount.wood==1000+Math.round(75*(1-progress))&&recipientAccount.ore==1000,"original paid amount refunded to current owner after payer epoch change");
                h.assertTrue(f.account.wood==originalWood,"transfer does not pay original owner");
                int received=recipientAccount.wood;BuildingServerEvents.cancelBuilding(transferred,recipient);
                h.assertTrue(recipientAccount.wood==received,"transferred cancellation cannot duplicate refund");
            } finally {PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(recipient));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(recipient));}
            for(int percent=0;percent<=45;percent++)for(int value:new int[]{0,1,20,60,400,Integer.MAX_VALUE}){
                int quote=com.dreamseekers.progression.DreamWindProgression.discount(value,percent);
                h.assertTrue(value==0?quote==0:quote>=1&&quote<=value,"wind positive floor / zero preservation");
            }
            h.assertTrue(DreamseekersMod.DREAM_ANCHOR.cost.wood==400&&DreamseekersMod.DREAM_ANCHOR.cost.ore==100,"keep asymmetric bootstrap anchor");
            h.assertTrue(DreamseekersMod.BEETLE_SANCTUARY.cost.wood==160&&DreamseekersMod.BEETLE_SANCTUARY.cost.ore==50,"sanctuary50O closes no-natural-ore bootstrap");
            int startOre=300-DreamseekersMod.DREAM_ANCHOR.cost.ore;
            h.assertTrue(startOre-DreamseekersMod.BEETLE_SANCTUARY.cost.ore-DreamseekersMod.SHADOW_MINE.cost.ore==0,"anchor sanctuary mine budget nonnegative");
            h.assertTrue(startOre-DreamseekersMod.SHADOW_MINE.cost.ore-DreamseekersMod.BEETLE_SANCTUARY.cost.ore==0,"anchor mine sanctuary budget nonnegative");
            h.succeed();
        } finally {f.close();ACTIVE.remove(f);}
    }
    private static final class PriceFixture {
        final GameTestHelper h;final ServerLevel level;final String owner="API54_ConstructionPrice";
        final Resources account;final List<BuildingPlacement> placements=new ArrayList<>();final Set<Long> chunks=new HashSet<>();
        PriceFixture(GameTestHelper h){this.h=h;level=h.getLevel();h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null,"isolated owner");PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));account=new Resources(owner,10000,10000,10000);ResourcesServerEvents.resourcesList.add(account);}
        BlockPos site(int x){BlockPos p=h.absolutePos(new BlockPos(x,1,40));
            for(int cx=(p.getX()-20)>>4;cx<=(p.getX()+25)>>4;cx++)for(int cz=(p.getZ()-20)>>4;cz<=(p.getZ()+25)>>4;cz++){long key=ChunkPos.asLong(cx,cz);if(!level.getForcedChunks().contains(key)){level.setChunkForced(cx,cz,true);chunks.add(key);}}
            for(int dx=-20;dx<=25;dx++)for(int dz=-20;dz<=25;dz++){level.setBlock(p.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=22;y++)level.setBlock(p.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);}return p;}
        void base(){var p=unpaidLegacy(DreamseekersMod.DREAM_ANCHOR,0);p.isBuilt=true;}
        BuildingPlacement unpaidLegacy(Building b,int x){var p=BuildingUtils.getNewBuildingPlacement(b,level,site(x),Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(p);placements.add(p);return p;}
        BuildingPlacement place(Building b,int x,boolean command){var p=BuildingServerEvents.placeBuilding(b,site(x),Rotation.NONE,owner,new int[0],false,false,command,true);h.assertTrue(p!=null,"real placement admitted "+b.structureName);placements.add(p);return p;}
        void cancelExpect(BuildingPlacement p,int paidW,int paidO,String label){float progress=p.getBlocksPlacedPercent();int w=account.wood,o=account.ore;BuildingServerEvents.cancelBuilding(p,owner);h.assertTrue(account.wood==w+Math.round(paidW*(1-progress))&&account.ore==o+Math.round(paidO*(1-progress)),label+" actual native partial refund");}
        void close(){PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(owner));for(var p:placements){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}for(long c:chunks){var p=new ChunkPos(c);level.setChunkForced(p.x,p.z,false);}}
    }
}
