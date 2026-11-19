package com.solegendary.reignofnether.research.economy;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.shared.PassiveOreBuilding;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("reignofnether_research_economy")
@PrefixGameTestTemplate(false)
public final class PiglinEconomyGameTests {
    static final String OWNER="PiglinAction_Owner",WATCHER="PiglinAction_Watcher";
    static final UUID ZERO=new UUID(0,0);
    static void send(ResearchEconomyFixtures.Peer peer,PiglinEconomyServerboundPacket packet) {
        FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());try{packet.encode(b);var copy=new PiglinEconomyServerboundPacket(b);if(!packet.equals(copy)||b.isReadable())throw new AssertionError("piglin request codec");copy.handle(()->peer.context);}finally{b.release();}peer.channel.runPendingTasks();
    }
    static PiglinEconomyStatusPacket response(ResearchEconomyFixtures.Peer peer) {
        var sample=new PiglinEconomyStatusPacket(ZERO,0,ZERO,ZERO,"",net.minecraft.resources.ResourceLocation.parse("minecraft:overworld"),BlockPos.ZERO,ZERO,0,0,0,0,false,false,"",0,ZERO);
        int id=ResearchEconomyFixtures.discriminator(sample);PiglinEconomyStatusPacket last=null;
        for(Object o:peer.drain())if(o instanceof ClientboundCustomPayloadPacket custom){var b=new FriendlyByteBuf(custom.getData().copy());try{if(b.readVarInt()==id)last=new PiglinEconomyStatusPacket(b);}finally{b.release();}}
        if(last==null)throw new AssertionError("missing real owner private piglin status");return last;
    }
    static PiglinEconomyServerboundPacket request(PiglinEconomyStatusPacket s,PiglinEconomyServerboundPacket.Action action,long seq){return new PiglinEconomyServerboundPacket(action,s.dimension(),s.origin(),s.instance(),s.capture(),s.epoch(),s.session(),seq,s.view());}
    @GameTest(templateNamespace="reignofnether_research_economy",template="ore_mine_test_arena",batch="piglin_economy_packet",timeoutTicks=160)
    public static void realPaidOverloadAndImmediateExchangeWithReplay(GameTestHelper h)throws Exception {
        runPaidActions(h,Factions.PIGLINS,"PiglinAction");
    }
    @GameTest(templateNamespace="reignofnether_research_economy",template="ore_mine_test_arena",batch="piglin_economy_sandbox_packet",timeoutTicks=160)
    public static void registeredSandboxPaidActionsRetainReplayAndOwnershipChecks(GameTestHelper h)throws Exception {
        runPaidActions(h,Factions.NONE,"PiglinSandbox");
    }
    private static void runPaidActions(GameTestHelper h,com.solegendary.reignofnether.faction.Faction faction,String prefix)throws Exception {
        final String OWNER=prefix+"_Owner",WATCHER=prefix+"_Watcher";
        ResearchEconomyFixtures.register();Resources a=ResearchEconomyFixtures.account(OWNER,1000);ResearchEconomyFixtures.account(WATCHER,0);PlayerServerEvents.getRTSPlayer(OWNER).faction=faction;
        BlockPos origin=h.absolutePos(new BlockPos(30,1,30));
        for(int x=-1;x<18;x++)for(int z=-1;z<18;z++){h.getLevel().setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<15;y++)h.getLevel().setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(BuildingBlock b:BuildingUtils.getAbsoluteBlockData(Buildings.PIGLIN_ORE_MINE.getRelativeBlockData(h.getLevel()),h.getLevel(),origin,Rotation.NONE))h.getLevel().setBlock(b.getBlockPos(),b.getBlockState(),2);
        BuildingPlacement mine=BuildingUtils.getNewBuildingPlacement(Buildings.PIGLIN_ORE_MINE,h.getLevel(),origin,Rotation.NONE,OWNER,false);BuildingServerEvents.getBuildings().add(mine);
        var owner=new ResearchEconomyFixtures.Peer(h.getLevel(),OWNER);var watcher=new ResearchEconomyFixtures.Peer(h.getLevel(),WATCHER);PiglinEconomyStatusPacket[] token={null};PiglinEconomyServerboundPacket[] exchange={null};
        Runnable cleanup=()->{ResearchServerEvents.removeAllResearchFor(OWNER);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(prefix+"_"));ResourcesServerEvents.resourcesList.removeIf(p->p.ownerName.startsWith(prefix+"_"));BuildingServerEvents.getBuildings().remove(mine);owner.close();watcher.close();};
        java.util.function.BiConsumer<Integer,Runnable> at=(t,run)->h.runAtTickTime(t,()->{try{run.run();}catch(RuntimeException|Error e){cleanup.run();throw e;}});
        at.accept(20,()->{
            h.assertTrue(mine.isBuilt,"real completed piglin mine");owner.drain();watcher.drain();
            PlayerServerEvents.getRTSPlayer(OWNER).faction=Factions.VILLAGERS;
            h.assertTrue(!PiglinEconomyService.owns(owner.actor,mine),"normal non-piglin owner cannot use piglin actions");
            PlayerServerEvents.getRTSPlayer(OWNER).faction=faction;
            h.assertTrue(PiglinEconomyService.owns(owner.actor,mine),"registered piglin or sandbox owner allowed");
            var query=new PiglinEconomyServerboundPacket(PiglinEconomyServerboundPacket.Action.QUERY,mine.level.dimension().location(),origin,ZERO,0,ZERO,ZERO,0,UUID.randomUUID());send(owner,query);token[0]=response(owner);h.assertTrue(token[0].view().equals(query.view()),"private reply echoes the requesting client view");h.assertTrue(watcher.drain().isEmpty(),"piglin status private to real exact owner");
            var noResearch=request(token[0],PiglinEconomyServerboundPacket.Action.EXCHANGE,1);int before=a.food;send(owner,noResearch);h.assertTrue(response(owner).status().equals("research_required")&&a.food==before,"exchange cannot precede research");
            ResearchServerEvents.addResearch(OWNER,CoreEconomyResearch.id("research_instant_smelting"));send(owner,noResearch);h.assertTrue(response(owner).status().equals("research_required")&&a.food==before,"research unlock does not reinterpret an old rejected nonce");
            exchange[0]=request(token[0],PiglinEconomyServerboundPacket.Action.EXCHANGE,2);int ore=a.ore;send(owner,exchange[0]);h.assertTrue(response(owner).status().equals("exchange_complete")&&a.food==before-100&&a.ore==ore+40,"one real packet immediately debits100F credits40O");
            var same=request(token[0],PiglinEconomyServerboundPacket.Action.EXCHANGE,3);before=a.food;send(owner,same);h.assertTrue(response(owner).status().equals("action_throttled")&&a.food==before,"new nonce within20 ticks cannot exchange");
            var overload=request(token[0],PiglinEconomyServerboundPacket.Action.OVERLOAD,4);before=a.food;send(owner,overload);h.assertTrue(response(owner).status().equals("overload_started")&&a.food==before-20&&PassiveOreBuilding.traitState(mine).getOverloadTicks()==400&&PassiveOreBuilding.traitState(mine).getCooldownTicks()==1200,"overload charged once with exact effective/running budgets");
            send(owner,overload);h.assertTrue(response(owner).status().equals("overload_started")&&a.food==before-20,"same overload packet cannot charge or refresh twice");
        });
        at.accept(40,()->{
            int f=a.food,o=a.ore;send(owner,exchange[0]);h.assertTrue(response(owner).status().equals("exchange_complete")&&a.food==f&&a.ore==o,"old success replays without debit while real mine produced between replies");
            send(owner,request(token[0],PiglinEconomyServerboundPacket.Action.EXCHANGE,5));h.assertTrue(response(owner).status().equals("exchange_complete")&&a.food==f-100&&a.ore==o+40,"next nonce succeeds after actual20 tick interval");
            h.assertTrue(PassiveOreBuilding.traitState(mine).getOverloadTicks()==380&&PassiveOreBuilding.traitState(mine).getCooldownTicks()==1180,"normal server placement ticks consume20 active boost and20 cooldown ticks");
            f=a.food;send(watcher,request(token[0],PiglinEconomyServerboundPacket.Action.OVERLOAD,6));h.assertTrue(a.food==f,"other authenticated owner cannot use copied action token");
            var bad=new PiglinEconomyServerboundPacket(PiglinEconomyServerboundPacket.Action.OVERLOAD,token[0].dimension(),origin,UUID.randomUUID(),token[0].capture(),token[0].epoch(),token[0].session(),6,token[0].view());send(owner,bad);h.assertTrue(response(owner).status().equals("forbidden")&&a.food==f,"replacement instance token rejected without payment");
        });
        at.accept(60,()->{
            a.food=99;var before=ResearchEconomyFixtures.snapshot(a);
            send(owner,request(token[0],PiglinEconomyServerboundPacket.Action.EXCHANGE,7));
            h.assertTrue(response(owner).status().equals("insufficient_resources")&&ResearchEconomyFixtures.snapshot(a).equals(before),"insufficient100F exchange rejects atomically without resource or revision change");
        });
        at.accept(80,()->{
            a.food=1000;a.ore=Integer.MAX_VALUE-39;var before=ResearchEconomyFixtures.snapshot(a);
            send(owner,request(token[0],PiglinEconomyServerboundPacket.Action.EXCHANGE,8));
            h.assertTrue(response(owner).status().equals("output_overflow")&&ResearchEconomyFixtures.snapshot(a).equals(before),"40ore output overflow rejects entire packet without100food debit or revision change");
        });
        at.accept(100,()->{
            a.ore=1000;var before=ResearchEconomyFixtures.snapshot(a);
            send(owner,request(token[0],PiglinEconomyServerboundPacket.Action.EXCHANGE,8));
            h.assertTrue(response(owner).status().equals("output_overflow")&&ResearchEconomyFixtures.snapshot(a).equals(before),"capacity recovery cannot reinterpret old rejected exchange nonce");
            int food=a.food;send(owner,request(token[0],PiglinEconomyServerboundPacket.Action.EXCHANGE,9));
            h.assertTrue(response(owner).status().equals("exchange_complete")&&a.food==food-100&&a.ore==1040,"new exchange nonce succeeds after capacity recovered");
            System.out.println(prefix+" PIGLIN_ECONOMY_RUNTIME_PASS real_packet exact_cost native_ticks replay research throttle private_owner stale_instance insufficient overflow");cleanup.run();h.succeed();
        });
    }
}
