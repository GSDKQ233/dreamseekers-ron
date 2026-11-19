package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.shared.MineWorkPosts;
import com.solegendary.reignofnether.building.workposts.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("reignofnether_research_economy")
@PrefixGameTestTemplate(false)
public final class MineWorkPostPacketGameTests {
    private static final String OWNER="MineControl_Owner", WATCHER="MineControl_Watcher";
    private static final UUID ZERO=new UUID(0,0), VIEW=UUID.randomUUID();
    private static void send(ResearchEconomyFixtures.Peer peer, MineWorkPostServerboundPacket packet) {
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {packet.encode(b);var copy=new MineWorkPostServerboundPacket(b);if(!packet.equals(copy)||b.isReadable())throw new AssertionError("workpost request codec");copy.handle(()->peer.context);}
        finally {b.release();}peer.channel.runPendingTasks();
    }
    private static MineWorkPostStatusPacket response(ResearchEconomyFixtures.Peer peer) {
        var sample=new MineWorkPostStatusPacket(ZERO,0,ZERO,ZERO,"",ResourceLocation.parse("minecraft:overworld"),BlockPos.ZERO,ZERO,ZERO,0,2,false,List.of(),"",0);
        int id=ResearchEconomyFixtures.discriminator(sample);MineWorkPostStatusPacket latest=null;
        for(Object object:peer.drain())if(object instanceof ClientboundCustomPayloadPacket custom){
            var b=new FriendlyByteBuf(custom.getData().copy());try{if(b.readVarInt()==id)latest=new MineWorkPostStatusPacket(b);}finally{b.release();}
        }
        return latest;
    }
    private static MineWorkPostServerboundPacket request(MineWorkPostStatusPacket token,long sequence,UUID instance,long capture) {
        return new MineWorkPostServerboundPacket(MineWorkPostServerboundPacket.Action.RELEASE_ALL,token.dimension(),token.origin(),token.view(),instance,capture,token.epoch(),token.session(),sequence);
    }
    @GameTest(templateNamespace="reignofnether_research_economy",template="ore_mine_test_arena",batch="mine_workpost_packet",timeoutTicks=100)
    public static void privateAuthoritativeOccupancyAndReplaySafeRelease(GameTestHelper h)throws Exception {
        ResearchEconomyFixtures.register();var account=ResearchEconomyFixtures.account(OWNER,1000);ResearchEconomyFixtures.account(WATCHER,1000);
        // Registered sandbox account is valid; an unrelated normal peer never inherits its rights.
        PlayerServerEvents.getRTSPlayer(OWNER).faction=Factions.NONE;
        BlockPos origin=h.absolutePos(new BlockPos(30,1,30));
        for(int x=-10;x<20;x++)for(int z=-10;z<20;z++){
            h.getLevel().setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<15;y++)h.getLevel().setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        for(BuildingBlock block:BuildingUtils.getAbsoluteBlockData(Buildings.VILLAGER_ORE_MINE.getRelativeBlockData(h.getLevel()),h.getLevel(),origin,Rotation.NONE))
            h.getLevel().setBlock(block.getBlockPos(),block.getBlockState(),2);
        var mine=BuildingUtils.getNewBuildingPlacement(Buildings.VILLAGER_ORE_MINE,h.getLevel(),origin,Rotation.NONE,OWNER,false);
        BuildingServerEvents.getBuildings().add(mine);
        VillagerUnit worker=EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());
        worker.setOwnerName(OWNER);worker.setNoAi(true);worker.moveTo(origin.getX()+4.5,origin.getY()+1,origin.getZ()-5.5);
        h.assertTrue(h.getLevel().addFreshEntity(worker),"real mine worker inserted");
        var owner=new ResearchEconomyFixtures.Peer(h.getLevel(),OWNER);var watcher=new ResearchEconomyFixtures.Peer(h.getLevel(),WATCHER);
        Runnable cleanup=()->{worker.getGarrisonGoal().stopGarrisoning();worker.discard();BuildingServerEvents.getBuildings().remove(mine);
            ResourcesServerEvents.resourcesList.removeIf(a->a.ownerName.startsWith("MineControl_"));PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith("MineControl_"));owner.close();watcher.close();};
        h.runAtTickTime(20,()->{try{
            h.assertTrue(mine.isBuilt,"real complete mine");owner.drain();watcher.drain();
            worker.getGarrisonGoal().setBuildingTarget(origin);
            h.assertTrue(MineWorkPosts.hasAssignment(worker),"explicit lease reserved before packet tests");
            var query=new MineWorkPostServerboundPacket(MineWorkPostServerboundPacket.Action.QUERY,mine.level.dimension().location(),origin,VIEW,ZERO,0,ZERO,ZERO,0);
            send(owner,query);var token=response(owner);
            h.assertTrue(token!=null&&token.validOccupancy()&&token.view().equals(VIEW),"real owner receives valid correlated snapshot");
            h.assertTrue(token.capacity()==2&&token.workers().size()==1&&token.activeCount()==0,"distant reservation is visible but earns zero bonus");
            h.assertTrue(token.workers().get(0).uuid().equals(worker.getUUID()),"server actual worker identity, not requested IDs");
            send(watcher,query);h.assertTrue(response(watcher)==null,"unrelated peer receives no worker identities despite sandbox owner existing");
            var release=request(token,1,token.instance(),token.capture());var before=ResearchEconomyFixtures.snapshot(account);var position=worker.position();
            send(watcher,release);h.assertTrue(response(watcher)==null&&MineWorkPosts.hasAssignment(worker),"copied private token cannot control another player's leases");
            send(owner,release);var released=response(owner);
            h.assertTrue(released!=null&&released.status().equals("mine_workers_released")&&released.workers().isEmpty(),"live server all-release returns empty authority snapshot");
            h.assertTrue(!MineWorkPosts.hasAssignment(worker)&&worker.position().equals(position),"all-release does not teleport");
            h.assertTrue(ResearchEconomyFixtures.snapshot(account).equals(before),"view/release never debit or credit resources");
            worker.getGarrisonGoal().setBuildingTarget(origin);send(owner,release);var replay=response(owner);
            h.assertTrue(replay!=null&&replay.workers().size()==1&&MineWorkPosts.hasAssignment(worker),"replayed completed release cannot cancel a later explicit order");
            send(owner,request(token,2,UUID.randomUUID(),token.capture()));h.assertTrue(response(owner).status().equals("forbidden")&&MineWorkPosts.hasAssignment(worker),"replacement placement token denied");
            mine.setOwnerNameFromServerEditor(WATCHER);mine.setOwnerNameFromServerEditor(OWNER);
            h.assertTrue(MineWorkPosts.snapshot(mine).isEmpty(),"capture ABA invalidates prior worker identity");
            worker.getGarrisonGoal().setBuildingTarget(origin);
            send(owner,request(token,3,token.instance(),token.capture()));h.assertTrue(response(owner).status().equals("forbidden")&&MineWorkPosts.hasAssignment(worker),"old capture token cannot release new-generation lease");
            send(owner,new MineWorkPostServerboundPacket(MineWorkPostServerboundPacket.Action.QUERY,ResourceLocation.parse("minecraft:the_nether"),origin,VIEW,ZERO,0,ZERO,ZERO,0));
            h.assertTrue(response(owner)==null,"same-coordinate other-world request cannot query current-world mine");
            ResourcesServerEvents.resourcesList.remove(account);Resources replacement=new Resources(OWNER,1000,1000,1000);ResourcesServerEvents.resourcesList.add(replacement);
            send(owner,request(token,4,token.instance(),mine.getCaptureRevision()));var stale=response(owner);
            h.assertTrue(stale!=null&&stale.status().equals("stale_session")&&MineWorkPosts.hasAssignment(worker),"account recreation rejects old epoch before releasing live leases");
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));
            h.assertTrue(!MineWorkPostService.owns(owner.actor,mine),"unregistered NONE fallback conveys no control");
            System.out.println("MINE_WORKPOST_PACKET_PASS private_status actual_reservation copied_token no_teleport replay instance capture_aba cross_world account_epoch registered_sandbox");
            cleanup.run();h.succeed();
        }catch(RuntimeException|Error failure){cleanup.run();throw failure;}});
    }
}
