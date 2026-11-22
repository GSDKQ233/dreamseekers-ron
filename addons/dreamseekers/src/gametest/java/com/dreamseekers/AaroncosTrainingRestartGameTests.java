package com.dreamseekers;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.production.*;
import com.dreamseekers.worldtree.WorldTreeModel;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** A real ordinary native save/load process pair. No deserialize into hand-made replacement accounts. */
@GameTestHolder("dreamseekers_hands_training_restart") @PrefixGameTestTemplate(false)
public final class AaroncosTrainingRestartGameTests {
    private static final File EXPECTED=new File("hands-training-restart-expectations.dat");
    private static AaroncosTrainingGameTests.F fixture;
    private static DreamHallPlacement hall;
    private static UUID receipt;
    private static final class StopWitness {
        // HIGHEST resource save projects the refund without mutating this live payment.
        // Observe at HIGH, before NORMAL building shutdown cancels and clears the real queue.
        // This deliberately avoids same-priority registration order and changes no product handler.
        @SubscribeEvent(priority=EventPriority.HIGH) public void stop(ServerStoppingEvent event){
            if(fixture==null||hall==null||hall.productionQueue.size()!=1
                ||hall.productionQueue.get(0).item!=DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION
                ||hall.productionQueue.get(0).ticksLeft<=0
                ||!hall.attachedUnitReceipts().contains(receipt)
                ||!fixture.r.paidReceipts.containsKey(receipt))
                throw new AssertionError("unfinished actual mixed-payment queue required at stop");
            var t=new CompoundTag();t.putLong("jvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());t.putString("owner",fixture.owner);t.putUUID("epoch",fixture.r.accountEpoch);t.putUUID("receipt",receipt);
            t.putLong("hall",hall.originPos.asLong());t.putUUID("instance",hall.unitPlacementIdentity());t.putLongArray("chunks",new ArrayList<>(fixture.forced));
            t.putInt("food",fixture.r.food);t.putInt("wood",fixture.r.wood);t.putInt("ore",fixture.r.ore);t.putInt("shadow",fixture.r.extra.get(DreamPassiveEconomy.SHADOW));
            try{NbtIo.writeCompressed(t,EXPECTED);}catch(Exception e){throw new RuntimeException(e);}
        }
    }
    @GameTest(templateNamespace="dreamseekers_hands_training_restart",template="ore_mine_test_arena",batch="hands_training_restart",timeoutTicks=120)
    public static void unfinishedMixedPaymentRefundsExactlyOnceAcrossProcess(GameTestHelper h)throws Exception{
        switch(System.getProperty("dreamseekers.hands.training.restart.phase","")){
            case "prepare" -> prepare(h);
            case "verify" -> verify(h);
            default -> throw new IllegalArgumentException("prepare|verify required");
        }
    }
    private static void prepare(GameTestHelper h){
        h.assertTrue(!EXPECTED.exists()&&PlayerServerEvents.getRTSPlayer("Hands_Restart")==null,"fresh isolated prepare world");
        fixture=new AaroncosTrainingGameTests.F(h,"Restart",27200);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(fixture.owner));
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(fixture.owner,DreamseekersMod.DREAMSEEKERS,97820,0,false,0L,0L));
        fixture.tree(WorldTreeModel.WIND_V4,0);fixture.lab();hall=fixture.hall();
        MinecraftForge.EVENT_BUS.register(new StopWitness());
        h.runAtTickTime(10,()->{
            h.assertTrue(hall.startProductionItem(DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION),"actual mixed payment unfinished queue");
            receipt=hall.attachedUnitReceipts().iterator().next();
            h.assertTrue(fixture.r.food==9600&&fixture.r.wood==9750&&fixture.r.ore==9650&&fixture.r.extra.get(DreamPassiveEconomy.SHADOW)==900,"exact original four currency debit");
        });
        h.runAtTickTime(20,()->{
            h.assertTrue(hall.productionQueue.get(0).ticksLeft>0,"normal unfinished72second queue");
            PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(h.getLevel());ResourcesServerEvents.saveResources(h.getLevel());
            h.assertTrue(fixture.r.paidReceipts.containsKey(receipt)&&fixture.r.extra.get(DreamPassiveEconomy.SHADOW)==900,"saved projection does not mutate live shadow or consume original receipt");h.succeed();
        });
    }
    private static void verify(GameTestHelper h)throws Exception{
        var t=NbtIo.readCompressed(EXPECTED);h.assertTrue(t.getLong("jvm")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"independent JVM verified");
        String owner=t.getString("owner");var account=ExtendedResourcesSync.account(owner);
        h.assertTrue(account!=null&&account.accountEpoch.equals(t.getUUID("epoch")),"native account identity restored");
        h.assertTrue(t.getInt("food")==9600&&t.getInt("wood")==9750&&t.getInt("ore")==9650&&t.getInt("shadow")==900,"real shutdown witnessed original unpaidcompletion cost");
        h.assertTrue(account.food==10000&&account.wood==10000&&account.ore==10000&&account.extra.get(DreamPassiveEconomy.SHADOW)==1000,"one exact FWO and100S saved refund");
        h.assertTrue(!account.paidReceipts.containsKey(t.getUUID("receipt")),"settled receipt cannot mint another refund");
        var loaded=BuildingServerEvents.getBuildings().stream().filter(p->p.ownerName.equals(owner)&&p.originPos.equals(BlockPos.of(t.getLong("hall")))).findFirst().orElseThrow();
        h.assertTrue(loaded instanceof DreamHallPlacement p&&p.productionQueue.isEmpty()&&p.unitPlacementIdentity().equals(t.getUUID("instance")),"native restored hall retains identity with transient queue cleared");
        var p=(DreamHallPlacement)loaded;
        h.assertTrue(!p.cancelProductionItem(DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION,true)&&account.extra.get(DreamPassiveEconomy.SHADOW)==1000,"old cancellation cannot refund again");
        h.assertTrue(p.startProductionItem(DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION)&&p.cancelProductionItem(DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION,true),"new mixed-paid queue and refund still usable after reload");
        h.assertTrue(account.food==10000&&account.extra.get(DreamPassiveEconomy.SHADOW)==1000,"new lifecycle does not duplicate previous payment");
        for(var b:List.copyOf(BuildingServerEvents.getBuildings()))if(b.ownerName.equals(owner)){b.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(b);}
        PlayerServerEvents.rtsPlayers.removeIf(a->a.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(a->a.ownerName.equals(owner));
        for(long c:t.getLongArray("chunks"))h.getLevel().setChunkForced(ChunkPos.getX(c),ChunkPos.getZ(c),false);h.succeed();
    }
}
