package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.Map;
import java.util.UUID;

/** Opt-in stage A, genuine server thread/account registry/ordinary save projection. */
@GameTestHolder("reignofnether_resource_economy")
@PrefixGameTestTemplate(false)
public final class ResourceEconomyGameTests {
    private static final String OWNER="ResourceEconomyTest_Original", OTHER="ResourceEconomyTest_Captor";
    private static final ResourceLocation ID=ResourceLocation.parse("test_resource_economy:shadow"),UNKNOWN=ResourceLocation.parse("uninstalled_economy:shadow");
    private static int assertions;
    private static void check(GameTestHelper h,boolean b,String why){assertions++;h.assertTrue(b,why);}
    private static CompoundTag snapshot(Resources r){CompoundTag n=new CompoundTag();n.putInt("food",r.food);n.putInt("wood",r.wood);n.putInt("ore",r.ore);n.putInt("emerald",r.emerald);r.writeExtended(n);return n;}
    private static Resources account(String name){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,Factions.VILLAGERS));Resources r=new Resources(name,100,100,100,100);r.extra.put(ID,100);ResourcesServerEvents.resourcesList.add(r);return r;}
    private static ResourceTransaction.Cost fee(int index){int[]v={20,20,20,20,20};if(index>=0)v[index]=101;return new ResourceTransaction.Cost(v[0],v[1],v[2],v[3],Map.of(ID,v[4]));}
    @GameTest(templateNamespace="reignofnether_resource_economy",template="ore_mine_test_arena",batch="resource_economy_stage_a",timeoutTicks=80)
    public static void authoritativeTransactionAndOrdinarySaveProjection(GameTestHelper h){
        assertions=0;var server=h.getLevel().getServer();
        ResourceExtensionRegistry.register(ID,1000,"resources.test_resource_economy.shadow",ResourceLocation.parse("minecraft:textures/item/amethyst_shard.png"));
        check(h,PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(OTHER)==null&&ResourcesServerEvents.resourcesList.stream().noneMatch(r->r.ownerName.equals(OWNER)||r.ownerName.equals(OTHER)),"isolated temporary account fixtures");
        Resources a=account(OWNER),b=account(OTHER);UUID epoch=a.accountEpoch;long revision=a.revision;
        try {
            for(int part=0;part<5;part++){
                CompoundTag before=snapshot(a);var result=ResourceTransaction.pay(server,a,epoch,revision,"test/component/"+part,fee(part));
                check(h,result.status()==ResourceTransaction.Status.INSUFFICIENT_RESOURCES,"each insufficient component rejects");check(h,snapshot(a).equals(before),"all components/receipts/revision unchanged on insufficiency");
            }
            CompoundTag before=snapshot(a);
            check(h,!ResourceTransaction.pay(server,a,UUID.randomUUID(),revision,"test/stale",fee(-1)).applied(),"stale account epoch denied");check(h,snapshot(a).equals(before),"wrong epoch has no mutation");
            check(h,!ResourceTransaction.pay(server,a,epoch,revision,"test/negative",new ResourceTransaction.Cost(-1,0,0)).applied(),"negative price denied");check(h,snapshot(a).equals(before),"negative price cannot mint");
            check(h,ResourceTransaction.pay(server,a,epoch,revision,"test/unknown",new ResourceTransaction.Cost(0,0,0,Map.of(UNKNOWN,1))).status()==ResourceTransaction.Status.UNKNOWN_EXTENSION,"unregistered extension cannot spend");check(h,snapshot(a).equals(before),"unknown extension is no-op");
            var paid=ResourceTransaction.pay(server,a,epoch,revision,"test/research",fee(-1));check(h,paid.applied()&&paid.receiptId()!=null,"mixed fee accepted with receipt");check(h,a.food==80&&a.wood==80&&a.ore==80&&a.emerald==80&&a.extra.get(ID)==80,"all paid components debited together");check(h,a.revision==revision+1,"one success one account revision");
            CompoundTag after=snapshot(a);check(h,!ResourceTransaction.pay(server,a,epoch,revision,"test/research",fee(-1)).applied(),"identical stale CAS request cannot pay twice");check(h,!ResourceTransaction.exchange(server,a,epoch,revision,new ResourceTransaction.Cost(1,0,0),new ResourceTransaction.Cost(0,0,999)).applied(),"different payload same CAS token cannot commit");check(h,snapshot(a).equals(after),"duplicate CAS causes no mutation");
            CompoundTag otherBefore=snapshot(b);ResourcePaidReceipt foreign=a.paidReceipts.get(paid.receiptId());b.paidReceipts.put(paid.receiptId(),foreign);check(h,!ResourceTransaction.refund(server,b,b.accountEpoch,b.revision,paid.receiptId(),10000).applied(),"copied receipt cannot pay capturing owner epoch");check(h,b.paidReceipts.size()==1&&foreign.equals(b.paidReceipts.get(paid.receiptId())),"rejected foreign receipt remains untouched");b.paidReceipts.clear();check(h,snapshot(b).equals(otherBefore),"foreign refund leaves balances/revision/other state unchanged");
            a.extra.put(ID,1000);var refund=ResourceTransaction.refund(server,a,epoch,a.revision,paid.receiptId(),10000);check(h,refund.applied(),"original payer refunds exact receipt");check(h,a.food==100&&a.wood==100&&a.ore==100&&a.emerald==100&&a.extra.get(ID)==1020,"refund retained even over income cap");after=snapshot(a);check(h,!ResourceTransaction.refund(server,a,epoch,a.revision,paid.receiptId(),10000).applied(),"receipt refunds only once even with fresh revision");check(h,snapshot(a).equals(after),"second refund no mutation");
            b.extra.clear();for(int i=0;i<ResourceAccountCodec.MAX_EXTRA;i++)b.extra.put(ResourceLocation.parse("unknown_capacity:k"+i),1);otherBefore=snapshot(b);
            check(h,!ResourceTransaction.exchange(server,b,b.accountEpoch,b.revision,ResourceTransaction.Cost.ZERO,new ResourceTransaction.Cost(0,0,0,Map.of(ID,1))).applied(),"transaction cannot exceed saved extension-key capacity");check(h,snapshot(b).equals(otherBefore),"extension-key overflow is zero mutation");b.extra.clear();b.extra.put(ID,100);
            var noIncome=ResourceTransaction.creditIncome(server,a,epoch,a.revision,new ResourceTransaction.Cost(0,0,0,Map.of(ID,50)));check(h,noIncome.applied()&&a.extra.get(ID)==1020&&noIncome.credited().extra().get(ID)==0,"normal income cannot grow overcap refund balance");
            a.conversionRemainders.put("cap_stream",9000L);long beforeIncomeRevision=a.revision;
            check(h,ResourceTransaction.creditIncome(server,a,epoch,a.revision,new ResourceTransaction.Cost(0,0,0,Map.of(ID,10)),Map.of("cap_stream",0L)).applied(),"income and cap-cleared remainder commit together");check(h,a.revision==beforeIncomeRevision+1&&a.conversionRemainders.get("cap_stream")==0&&a.extra.get(ID)==1020,"single revision contains both clipped balance and discarded remainder");before=snapshot(a);check(h,!ResourceTransaction.creditIncome(server,a,epoch,a.revision,new ResourceTransaction.Cost(1,1,1),Map.of("invalid",10000L)).applied(),"invalid remainder prevents whole income commit");check(h,snapshot(a).equals(before),"invalid remainder does not partially credit components");
            a.ore=Integer.MAX_VALUE;before=snapshot(a);check(h,ResourceTransaction.exchange(server,a,epoch,a.revision,new ResourceTransaction.Cost(20,0,0),new ResourceTransaction.Cost(0,0,40)).status()==ResourceTransaction.Status.OUTPUT_OVERFLOW,"exchange rejects missing output room");check(h,snapshot(a).equals(before),"output overflow never consumes input");a.ore=100;
            var partial=ResourceTransaction.pay(server,a,epoch,a.revision,"test/partial",new ResourceTransaction.Cost(3,5,7,Map.of(ID,9)));check(h,partial.applied(),"partial receipt creation");check(h,ResourceTransaction.refund(server,a,epoch,a.revision,partial.receiptId(),5000).applied(),"one partial settlement");check(h,a.food==98&&a.wood==97&&a.ore==96&&a.extra.get(ID)==1015,"partial refund floors each paid component");check(h,!ResourceTransaction.refund(server,a,epoch,a.revision,partial.receiptId(),5000).applied(),"forfeited partial remainder cannot be redeemed again");
            var pending=ResourceTransaction.pay(server,a,epoch,a.revision,"test/pending-save",new ResourceTransaction.Cost(1,2,3,Map.of(ID,4)));check(h,pending.applied(),"live receipt saved with account");a.conversionRemainders.put("test/shadow",9876L);long savedRevision=a.revision;int liveFood=a.food;
            ResourcesServerEvents.saveResources(h.getLevel());ResourcesSaveData disk=ResourcesSaveData.getInstance(h.getLevel());Resources saved=disk.resources.stream().filter(r->r.ownerName.equals(OWNER)).findFirst().orElseThrow();
            check(h,saved!=a&&saved.accountEpoch.equals(epoch)&&saved.revision==savedRevision,"ordinary save projection keeps same epoch/revision in independent snapshot");check(h,saved.extra.equals(a.extra)&&saved.paidReceipts.equals(a.paidReceipts)&&saved.conversionRemainders.equals(a.conversionRemainders),"ordinary save projection retains all extension state");check(h,a.food==liveFood&&a.paidReceipts.containsKey(pending.receiptId()),"saving never consumes live payment or receipt");
            Resources loaded=ResourcesSaveData.load(disk.save(new CompoundTag())).resources.stream().filter(r->r.ownerName.equals(OWNER)).findFirst().orElseThrow();check(h,snapshot(loaded).equals(snapshot(saved)),"actual ResourcesSaveData codec exact roundtrip");
            ResourcesServerEvents.resourcesList.remove(a);ResourcesServerEvents.resourcesList.add(loaded);check(h,ResourceTransaction.refund(server,loaded,loaded.accountEpoch,loaded.revision,pending.receiptId(),10000).applied(),"normally restored receipt remains refundable once");check(h,!ResourceTransaction.refund(server,loaded,loaded.accountEpoch,loaded.revision,pending.receiptId(),10000).applied(),"restored receipt not replayable");
            var oldEpoch=loaded.accountEpoch;loaded.resetExtendedAccount();check(h,!ResourceTransaction.exchange(server,loaded,oldEpoch,loaded.revision,ResourceTransaction.Cost.ZERO,new ResourceTransaction.Cost(1,1,1)).applied(),"same-name reset rejects previous epoch authority");
            System.out.println("RESOURCE_ECONOMY_GAMETEST_PASS assertions="+assertions);h.succeed();
        }finally{ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(OWNER)||r.ownerName.equals(OTHER));PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER)||p.name.equals(OTHER));}
    }
}
