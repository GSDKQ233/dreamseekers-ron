package com.dreamseekers;

import com.dreamseekers.function.DreamWatchtowerPlacement;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.garrison.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Actual authenticated management transport plus native multi-selection navigation. Not a GUI test. */
@GameTestHolder("dreamseekers_tower_management")
@PrefixGameTestTemplate(false)
public final class TowerManagementGameTests {
    private static final UUID ZERO=new UUID(0,0);
    private static final String BATCH="tower_management";
    private static final List<Fixture> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var f:FIXTURES)f.close();FIXTURES.clear();}
    static final class Fixture {
        final GameTestHelper h;final ServerLevel level;final String owner,other;
        final Resources account;final BlockPos origin;final DreamWatchtowerPlacement tower;
        final List<LivingEntity> entities=new ArrayList<>();final List<BuildingPlacement> buildings=new ArrayList<>();
        final List<ResearchEconomyFixtures.Peer> peers=new ArrayList<>();final Set<Long> forced=new HashSet<>();
        Fixture(GameTestHelper h,String suffix)throws Exception {
            this.h=h;level=h.getLevel();owner="Tower48_"+suffix;other=owner+"_Other";FIXTURES.add(this);
            for(String name:List.of(owner,other)){h.assertTrue(PlayerServerEvents.getRTSPlayer(name)==null,"fresh owner");PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,Factions.NONE));ResourcesServerEvents.resourcesList.add(new Resources(name,1000,1000,1000));}
            account=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(owner)).findFirst().orElseThrow();
            origin=BuildingAssetGameTests.outdoorPos(h,20,1,20);
            for(int x=(origin.getX()-20)>>4;x<=(origin.getX()+25)>>4;x++)for(int z=(origin.getZ()-20)>>4;z<=(origin.getZ()+25)>>4;z++){
                long id=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(id)){level.setChunkForced(x,z,true);forced.add(id);}}
            for(int x=-20;x<=25;x++)for(int z=-20;z<=25;z++){level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=18;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            tower=install(origin);
        }
        DreamWatchtowerPlacement install(BlockPos pos) {
            var definition=DreamseekersMod.SHADOW_WATCHTOWER;
            for(var b:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,pos,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            var p=(DreamWatchtowerPlacement)BuildingUtils.getNewBuildingPlacement(definition,level,pos,Rotation.NONE,owner,false);
            BuildingServerEvents.getBuildings().add(p);buildings.add(p);p.onBuilt();return p;
        }
        <T extends Mob>T spawn(EntityType<T> type,int x,int z,boolean ai){
            T e=type.create(level);((Unit)e).setOwnerName(owner);e.setNoAi(!ai);e.moveTo(origin.getX()+x+.5,origin.getY()+1,origin.getZ()+z+.5);
            h.assertTrue(level.addFreshEntity(e),"real entity admitted");entities.add(e);return e;
        }
        ResearchEconomyFixtures.Peer peer(String name)throws Exception{var p=new ResearchEconomyFixtures.Peer(level,name);peers.add(p);return p;}
        void ready(Entity e){h.assertTrue(e.tickCount>2&&level.getEntity(e.getId())==e&&level.getEntity(e.getUUID())==e&&level.isPositionEntityTicking(e.blockPosition()),"actual entity ready before native command");}
        TowerGarrisonStatusPacket sample(){return new TowerGarrisonStatusPacket(ZERO,0,ZERO,ZERO,"","",level.dimension().location(),origin,ZERO,ZERO,0,3,0,0,0,"",0);}
        TowerGarrisonServerboundPacket query(UUID view){return new TowerGarrisonServerboundPacket(TowerGarrisonServerboundPacket.Action.QUERY,level.dimension().location(),origin,view,ZERO,0,ZERO,ZERO,0);}
        TowerGarrisonStatusPacket reply(ResearchEconomyFixtures.Peer p){return TowerGarrisonTestTransport.response(p,sample());}
        void close(){
            AlliancesServerEvents.removeAlliance(owner,other);
            for(var p:peers)p.close();for(var e:entities)e.discard();BuildingServerEvents.getBuildings().removeAll(buildings);
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner)||p.name.equals(other));ResourcesServerEvents.resourcesList.removeIf(a->a.ownerName.equals(owner)||a.ownerName.equals(other));
            for(long key:forced){ChunkPos p=new ChunkPos(key);level.setChunkForced(p.x,p.z,false);}
        }
    }
    static TowerGarrisonServerboundPacket release(TowerGarrisonStatusPacket s,long n,UUID instance,long capture) {
        return new TowerGarrisonServerboundPacket(TowerGarrisonServerboundPacket.Action.RELEASE_ALL,s.dimension(),s.origin(),s.view(),instance,capture,s.epoch(),s.session(),n);
    }
    @GameTest(templateNamespace="dreamseekers_tower_management",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void authenticatedPendingReleaseReplayCaptureAndRebuild(GameTestHelper h)throws Exception {
        var f=new Fixture(h,"Packets");var owner=f.peer(f.owner);var stranger=f.peer(f.other);
        var worker=f.spawn(DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),3,-8,false);
        h.runAtTickTime(20,()->{
            h.assertTrue(TowerGarrisonService.managed(f.tower)&&f.tower.isBuilt,"real v2 tower active");
            worker.getGarrisonGoal().setBuildingTarget(f.origin);
            h.assertTrue(worker.getGarrisonGoal().getBuildingTarget()==f.tower,"real pending native tower goal");
            UUID view=UUID.randomUUID();TowerGarrisonTestTransport.send(owner,f.query(view));var token=f.reply(owner);
            h.assertTrue(token!=null&&token.validOccupancy()&&token.view().equals(view)&&token.occupied()==0&&token.approaching()==1,"server distinguishes approaching from occupied");
            TowerGarrisonTestTransport.send(stranger,f.query(view));h.assertTrue(f.reply(stranger)==null,"unrelated peer cannot query tower");
            var wrongDimension=new TowerGarrisonServerboundPacket(TowerGarrisonServerboundPacket.Action.RELEASE_ALL,
                net.minecraft.world.level.Level.NETHER.location(),token.origin(),token.view(),token.instance(),token.capture(),token.epoch(),token.session(),1);
            TowerGarrisonTestTransport.send(owner,wrongDimension);h.assertTrue(f.reply(owner)==null&&worker.getGarrisonGoal().getBuildingTarget()==f.tower,"cross-dimension packet cannot target same coordinates");
            var req=release(token,1,token.instance(),token.capture());
            TowerGarrisonTestTransport.send(stranger,req);f.reply(stranger);h.assertTrue(worker.getGarrisonGoal().getBuildingTarget()==f.tower,"copied session cannot withdraw other's pending order");
            var before=ResearchEconomyFixtures.snapshot(f.account);var pos=worker.position();
            TowerGarrisonTestTransport.send(owner,req);var result=f.reply(owner);
            h.assertTrue(result!=null&&result.status().equals("tower_released")&&worker.getGarrisonGoal().getBuildingTarget()==null&&pos.equals(worker.position()),"pending withdrawal cancels without teleport");
            worker.getGarrisonGoal().setBuildingTarget(f.origin);TowerGarrisonTestTransport.send(owner,req);f.reply(owner);
            h.assertTrue(worker.getGarrisonGoal().getBuildingTarget()==f.tower,"replayed consumed withdrawal cannot cancel later order");
            TowerGarrisonTestTransport.send(owner,release(token,2,UUID.randomUUID(),token.capture()));f.reply(owner);
            h.assertTrue(worker.getGarrisonGoal().getBuildingTarget()==f.tower,"forged placement rejected");
            f.tower.setOwnerNameFromServerEditor(f.other);f.tower.setOwnerNameFromServerEditor(f.owner);
            TowerGarrisonTestTransport.send(owner,release(token,3,token.instance(),token.capture()));f.reply(owner);
            h.assertTrue(worker.getGarrisonGoal().getBuildingTarget()==f.tower,"capture ABA revokes old authority");
            worker.getGarrisonGoal().stopGarrisoning();BuildingServerEvents.getBuildings().remove(f.tower);
            var replacement=f.install(f.origin);worker.getGarrisonGoal().setBuildingTarget(f.origin);
            TowerGarrisonTestTransport.send(owner,release(token,4,token.instance(),replacement.getCaptureRevision()));f.reply(owner);
            h.assertTrue(worker.getGarrisonGoal().getBuildingTarget()==replacement,"same-location replacement does not inherit old instance authority");
            h.assertTrue(before.equals(ResearchEconomyFixtures.snapshot(f.account)),"management never charges or credits resources");
            h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_tower_management",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1200)
    public static void nativeMixedSelectionAdmissionAndOwnedBulkWithdrawal(GameTestHelper h)throws Exception {
        var f=new Fixture(h,"Native");var peer=f.peer(f.owner);
        var a=f.spawn(DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),2,-8,true);
        var b=f.spawn(DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),4,-8,true);
        var beetle=f.spawn(DreamseekersMod.BEETLE.get(),0,-10,true);
        var ally=f.spawn(DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),3,-11,true);ally.setOwnerName(f.other);
        AlliancesServerEvents.addAlliance(f.owner,f.other);
        final BlockPos unrelated=f.origin.offset(-8,1,-9);
        h.startSequence().thenWaitUntil(()->{f.ready(a);f.ready(b);f.ready(beetle);f.ready(ally);})
            .thenExecute(()->{
                UnitServerEvents.addActionItem(f.owner,UnitAction.MOVE,-1,new int[]{beetle.getId()},unrelated,BlockPos.ZERO);
            }).thenIdle(2).thenExecute(()->{
                h.assertTrue(GarrisonSelectionPolicy.anyCanRightClick(List.of(beetle,a,b),f.tower)&&GarrisonSelectionPolicy.anyCanRightClick(List.of(a,beetle,b),f.tower),"primary member order does not hide eligible tower command");
                h.assertTrue(!GarrisonSelectionPolicy.canAssign(beetle,f.tower),"melee non-garrison member excluded");
                UnitServerEvents.addActionItem(f.owner,UnitAction.GARRISON,-1,new int[]{beetle.getId(),a.getId(),b.getId()},f.origin,BlockPos.ZERO);
                UnitServerEvents.addActionItem(f.other,UnitAction.GARRISON,-1,new int[]{ally.getId()},f.origin,BlockPos.ZERO);
            }).thenIdle(2).thenExecute(()->{
                h.assertTrue(beetle.getMoveGoal().getMoveTarget()!=null&&beetle.getMoveGoal().getMoveTarget().equals(unrelated),"ineligible mixed member keeps its existing move order");
            }).thenWaitUntil(()->{
                h.assertTrue(GarrisonableBuildingAddon.getGarrison(a)==f.tower&&GarrisonableBuildingAddon.getGarrison(b)==f.tower&&GarrisonableBuildingAddon.getGarrison(ally)==f.tower,"three real bodies reached collision-safe native seats");
            }).thenExecute(()->{
                TowerGarrisonTestTransport.send(peer,f.query(UUID.randomUUID()));var token=f.reply(peer);
                h.assertTrue(token!=null&&token.occupied()==3&&token.mine()==2&&token.approaching()==0,"server aggregate includes allied occupant but only own controllable count");
                var apos=a.position();var bpos=b.position();
                for(int x:new int[]{2,3,4})f.level.setBlock(f.origin.offset(x,2,1),Blocks.STONE.defaultBlockState(),2);
                TowerGarrisonTestTransport.send(peer,release(token,1,token.instance(),token.capture()));var blocked=f.reply(peer);
                h.assertTrue(blocked!=null&&blocked.status().equals("tower_partial")&&apos.equals(a.position())&&bpos.equals(b.position()),"blocked exits report partial and never teleport through geometry");
                for(int x:new int[]{2,3,4})f.level.setBlock(f.origin.offset(x,2,1),Blocks.AIR.defaultBlockState(),2);
                TowerGarrisonTestTransport.send(peer,release(token,2,token.instance(),token.capture()));var result=f.reply(peer);
                h.assertTrue(result!=null&&result.status().equals("tower_released"),"new safe withdrawal succeeds after clearing exits");
            }).thenIdle(3).thenExecute(()->{
                h.assertTrue(GarrisonableBuildingAddon.getGarrison(a)!=f.tower&&GarrisonableBuildingAddon.getGarrison(b)!=f.tower,"both own occupants actually withdrawn");
                h.assertTrue(GarrisonableBuildingAddon.getGarrison(ally)==f.tower,"allied occupant was never commanded by bulk withdrawal");
                h.assertTrue(f.level.noCollision(a)&&f.level.noCollision(b)&&!a.getBoundingBox().intersects(b.getBoundingBox()),"real egress bodies remain collision safe");
                h.succeed();
            });
    }
    @GameTest(templateNamespace="dreamseekers_tower_management",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
    public static void accountEpochAndAllianceRevocationDoNotCancelNewOrders(GameTestHelper h)throws Exception {
        var f=new Fixture(h,"Epoch");var peer=f.peer(f.owner);var allyPeer=f.peer(f.other);
        var worker=f.spawn(DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),3,-8,false);
        final TowerGarrisonStatusPacket[] old={null};
        h.runAtTickTime(20,()->{
            worker.getGarrisonGoal().setBuildingTarget(f.origin);
            TowerGarrisonTestTransport.send(peer,f.query(UUID.randomUUID()));old[0]=f.reply(peer);
            h.assertTrue(old[0]!=null&&old[0].approaching()==1,"old epoch authorized query established");
            AlliancesServerEvents.addAlliance(f.owner,f.other);
            TowerGarrisonTestTransport.send(allyPeer,f.query(UUID.randomUUID()));var ally=f.reply(allyPeer);
            h.assertTrue(ally!=null&&ally.viewer().equals(f.other)&&ally.owner().equals(f.owner)&&ally.mine()==0&&ally.approaching()==0,"ally view contains no other's approaching count or identity");
            AlliancesServerEvents.removeAlliance(f.owner,f.other);
            TowerGarrisonTestTransport.send(allyPeer,f.query(UUID.randomUUID()));h.assertTrue(f.reply(allyPeer)==null,"revoked alliance cannot continue query");
        });
        h.runAtTickTime(22,()->{
            ResourcesServerEvents.resourcesList.remove(f.account);
            var fresh=new Resources(f.owner,1000,1000,1000);ResourcesServerEvents.resourcesList.add(fresh);
            h.assertTrue(!fresh.accountEpoch.equals(old[0].epoch()),"same name has a genuinely new account epoch");
            TowerGarrisonTestTransport.send(peer,release(old[0],1,old[0].instance(),old[0].capture()));var denied=f.reply(peer);
            h.assertTrue(denied!=null&&denied.status().equals("stale_session")&&worker.getGarrisonGoal().getBuildingTarget()==f.tower,"old epoch action cannot clear retained/new order");
            TowerGarrisonTestTransport.send(peer,f.query(UUID.randomUUID()));var current=f.reply(peer);
            h.assertTrue(current!=null&&current.epoch().equals(fresh.accountEpoch)&&!current.session().equals(old[0].session()),"fresh connection session binds current epoch");
            TowerGarrisonTestTransport.send(peer,release(current,1,current.instance(),current.capture()));var done=f.reply(peer);
            h.assertTrue(done!=null&&done.status().equals("tower_released")&&worker.getGarrisonGoal().getBuildingTarget()==null,"fresh authorized action still works after epoch rotation");
            h.succeed();
        });
    }

}
