package com.dreamseekers;

import com.dreamseekers.ecologyworkposts.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.ResearchPlacementIdentity;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Focused real-tick care orders. No synthetic tick, teleport-to-arrival, or default healed HP assumptions. */
@GameTestHolder("dreamseekers_ecology")
@PrefixGameTestTemplate(false)
public final class EcologyWorkPostGameTests {
    private static final String BATCH="dreamseekers_ecology";
    private static final List<Fixture> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var f:FIXTURES)f.close();FIXTURES.clear();}
    private static final class Fixture {
        final GameTestHelper h;final ServerLevel level;final String owner;final ServerPlayer actor;final Resources account;
        final BuildingPlacement court;final BlockPos origin;final List<VillagerUnit> units=new ArrayList<>();final Set<Long> forced=new HashSet<>();final List<Object> probes=new ArrayList<>();
        Fixture(GameTestHelper h,String label){this.h=h;level=h.getLevel();owner="EcologyGT_"+label;FIXTURES.add(this);
            h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null,"unique owner");PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
            account=new Resources(owner,1000,1000,1000);ResourcesServerEvents.resourcesList.add(account);
            actor=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(owner.getBytes(java.nio.charset.StandardCharsets.UTF_8)),owner));
            origin=BuildingAssetGameTests.outdoorPos(h,10,1,10);
            for(int x=(origin.getX()-25)>>4;x<=(origin.getX()+30)>>4;x++)for(int z=(origin.getZ()-25)>>4;z<=(origin.getZ()+30)>>4;z++) {
                long key=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(key)){level.setChunkForced(x,z,true);forced.add(key);}
            }
            for(int x=-25;x<=30;x++)for(int z=-25;z<=30;z++){level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=18;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.CONTRACT_ECOLOGY.getRelativeBlockData(level),level,origin,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            court=BuildingUtils.getNewBuildingPlacement(DreamseekersMod.CONTRACT_ECOLOGY,level,origin,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(court);court.onBuilt();
        }
        VillagerUnit spawn(int x,int z){var u=EntityRegistrar.VILLAGER_UNIT.get().create(level);u.setOwnerName(owner);u.moveTo(origin.getX()+x+.5,origin.getY()+1,origin.getZ()+z+.5);u.setNoAi(false);h.assertTrue(level.addFreshEntity(u),"real RTS body published");units.add(u);return u;}
        void ready(VillagerUnit u){h.assertTrue(u.tickCount>3&&u.onGround()&&UnitServerEvents.getAllUnits().contains(u)&&level.getEntity(u.getUUID())==u,"real entity tracking and ground readiness");}
        String assign(VillagerUnit... us){return EcologyWorkPosts.assign(actor,court,ResearchPlacementIdentity.serverIdentity(court),court.getCaptureRevision(),Arrays.stream(us).map(u->new EcologyPackets.Target(u.getId(),u.getUUID())).toList());}
        EcologyWorkPosts.Occupant entry(VillagerUnit u){return EcologyWorkPosts.snapshot(court).stream().filter(e->e.uuid().equals(u.getUUID())).findFirst().orElseThrow();}
        void research(){ResearchServerEvents.addResearch(owner,ResourceLocation.fromNamespaceAndPath("dreamseekers","contract_care"));}
        void release(){EcologyWorkPosts.release(actor,court,ResearchPlacementIdentity.serverIdentity(court),court.getCaptureRevision(),null);}
        void close(){for(Object probe:probes)MinecraftForge.EVENT_BUS.unregister(probe);release();ResearchServerEvents.removeAllResearchFor(owner);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(r->r==account);for(var u:units)if(!u.isRemoved())u.discard();court.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(court);for(long key:forced){var p=new ChunkPos(key);level.setChunkForced(p.x,p.z,false);}}
    }
    @GameTest(templateNamespace="dreamseekers_ecology",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1100)
    public static void actualWalkResearchGateHealAndNativeStop(GameTestHelper h){
        var f=new Fixture(h,"Walk");var u=f.spawn(4,-12);var second=f.spawn(9,-12);var start=u.position();float[] baseline={0};long[] arrival={0};
        h.startSequence().thenWaitUntil(()->{f.ready(u);f.ready(second);}).thenExecute(()->{
            u.setHealth(u.getMaxHealth()-6);baseline[0]=u.getHealth();h.assertTrue(f.assign(u,second).equals("assigned"),"two explicit accepted orders");h.assertTrue(!f.entry(u).arrived(),"distant unit does not count as arrived");
        }).thenWaitUntil(()->h.assertTrue(f.entry(u).arrived()&&f.entry(second).arrived(),"natural AI walking reaches both reserved posts within native settle tolerance"))
        .thenExecute(()->{arrival[0]=f.level.getGameTime();h.assertTrue(f.level.noCollision(u)&&f.level.noCollision(second),"both actual bodies have physical clearance");h.assertTrue(!u.getBoundingBox().intersects(second.getBoundingBox())&&u.distanceToSqr(start)>4,"two natural arrivals are non-overlapping and did travel");})
        .thenWaitUntil(()->h.assertTrue(f.level.getGameTime()-arrival[0]>=240,"observe no-research interval"))
        .thenExecute(()->{h.assertTrue(u.getHealth()==baseline[0],"no care before research");f.research();})
        .thenWaitUntil(()->h.assertTrue(u.getHealth()>baseline[0],"research unlocks actual one HP pulse"))
        .thenExecute(()->{
            h.assertTrue(u.getHealth()==baseline[0]+1,"single capped HP pulse");new UnitActionItem(f.owner,UnitAction.STOP,-1,new int[]{u.getId()},BlockPos.ZERO,BlockPos.ZERO).action(f.level);
            h.assertTrue(EcologyWorkPosts.snapshot(f.court).size()==1&&f.entry(second).arrived(),"native STOP revokes only that lease and keeps the other real occupant");baseline[0]=u.getHealth();
        }).thenExecuteAfter(100,()->{h.assertTrue(u.getHealth()==baseline[0],"stopped unit receives no further care");h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_ecology",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=600)
    public static void capacityFreeBodyWrongActorAndStaleLease(GameTestHelper h){
        var f=new Fixture(h,"Authority");var a=f.spawn(3,-10);var b=f.spawn(7,-10);var c=f.spawn(11,-10);var d=f.spawn(15,-10);UUID[] old={null};
        h.startSequence().thenWaitUntil(()->{f.ready(a);f.ready(b);f.ready(c);f.ready(d);}).thenExecute(()->{
            RtsKillValueClaims.markFree(c,RtsKillValueClaims.FreeOrigin.NATIVE_SUMMON);h.assertTrue(f.assign(c).equals("no_post"),"free summoned body cannot reserve care");
            h.assertTrue(f.assign(a,b).equals("assigned")&&EcologyWorkPosts.snapshot(f.court).size()==2,"two explicit reservations");old[0]=f.entry(a).lease();
            h.assertTrue(f.assign(d).equals("no_post")&&EcologyWorkPosts.snapshot(f.court).size()==2,"third eligible permanent unit rejected at capacity");
            var stranger=FakePlayerFactory.get(f.level,new GameProfile(UUID.randomUUID(),"EcologyOther"));
            h.assertTrue(EcologyWorkPosts.release(stranger,f.court,ResearchPlacementIdentity.serverIdentity(f.court),f.court.getCaptureRevision(),null).equals("forbidden"),"foreign actor cannot release");
            h.assertTrue(EcologyWorkPosts.assign(f.actor,f.court,UUID.randomUUID(),f.court.getCaptureRevision(),List.of(new EcologyPackets.Target(c.getId(),c.getUUID()))).equals("forbidden"),"wrong building instance rejected");
            EcologyWorkPosts.release(f.actor,f.court,ResearchPlacementIdentity.serverIdentity(f.court),f.court.getCaptureRevision(),old[0]);
            h.assertTrue(EcologyWorkPosts.snapshot(f.court).size()==1,"single release preserves other order");h.assertTrue(f.assign(a).equals("assigned"),"new explicit reassignment");
            EcologyWorkPosts.release(f.actor,f.court,ResearchPlacementIdentity.serverIdentity(f.court),f.court.getCaptureRevision(),old[0]);
            h.assertTrue(EcologyWorkPosts.snapshot(f.court).size()==2&&!f.entry(a).lease().equals(old[0]),"old single-release lease never cancels new order");
            a.setOwnerName("EcologyOther");a.setOwnerName(f.owner);
            h.assertTrue(EcologyWorkPosts.snapshot(f.court).stream().noneMatch(e->e.uuid().equals(a.getUUID())),"same-tick owner ABA revokes old lease");h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_ecology",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=600)
    public static void playerBuildingOwnerCommandAbaRevokesCareLease(GameTestHelper h){
        var f=new Fixture(h,"BuildingCommand");var u=f.spawn(4,-10);
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{
            h.assertTrue(f.assign(u).equals("assigned"),"explicit care lease before building command");
            UUID lease=f.entry(u).lease();long revision=f.court.getCaptureRevision();
            BuildingOwnerCommandFixture.transfer(h,f.court,f.owner,f.owner);
            h.assertTrue(f.court.getCaptureRevision()==revision&&f.entry(u).lease().equals(lease),
                "no-op owner command preserves existing care lease");
            BuildingOwnerCommandFixture.transfer(h,f.court,f.owner,f.owner+"Other");
            BuildingOwnerCommandFixture.transfer(h,f.court,f.owner+"Other",f.owner);
            h.assertTrue(f.court.getCaptureRevision()==revision+2&&EcologyWorkPosts.snapshot(f.court).isEmpty(),
                "actual same-tick building command ABA invalidates old care lease before another tick");
            h.assertTrue(EcologyWorkPosts.assign(f.actor,f.court,ResearchPlacementIdentity.serverIdentity(f.court),revision,
                List.of(new EcologyPackets.Target(u.getId(),u.getUUID()))).equals("forbidden"),
                "stale pre-capture authority cannot restore old workpost order");
            h.assertTrue(f.assign(u).equals("assigned")&&!f.entry(u).lease().equals(lease),
                "fresh explicit order can acquire a new post after capture");
            System.out.println("PLAYER_BUILDING_OWNER_WORKPOST_PASS noop same_tick_ABA stale_revision fresh_order");
            h.succeed();
        });
    }

    public static final class ResetDuringHeal {
        final VillagerUnit target;boolean fired;
        ResetDuringHeal(VillagerUnit target){this.target=target;}
        @SubscribeEvent public void heal(LivingHealEvent e){if(e.getEntity()==target&&!fired){fired=true;Unit.fullResetBehaviours(target);}}
    }
    @GameTest(templateNamespace="dreamseekers_ecology",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=700)
    public static void healingCallbackCannotWriteAfterOrderRevocation(GameTestHelper h){
        var f=new Fixture(h,"Reentrant");var u=f.spawn(4,-10);f.research();float[] hp={0};var probe=new ResetDuringHeal(u);f.probes.add(probe);
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{
            u.setHealth(u.getMaxHealth()-4);hp[0]=u.getHealth();MinecraftForge.EVENT_BUS.register(probe);h.assertTrue(f.assign(u).equals("assigned"),"order accepted");
        }).thenWaitUntil(()->h.assertTrue(probe.fired,"real Forge heal callback reached"))
        .thenExecute(()->{h.assertTrue(u.getHealth()==hp[0]&&EcologyWorkPosts.snapshot(f.court).isEmpty(),"callback reset removes lease before HP write");h.succeed();});
    }

    public static final class DamageDuringHeal {
        final VillagerUnit target;boolean fired;float after;
        DamageDuringHeal(VillagerUnit target){this.target=target;}
        @SubscribeEvent public void heal(LivingHealEvent e){if(e.getEntity()==target&&!fired){fired=true;target.invulnerableTime=0;target.hurt(target.damageSources().generic(),1);after=target.getHealth();}}
    }
    @GameTest(templateNamespace="dreamseekers_ecology",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=850)
    public static void realDamageRestWindowAndReentrantHitNeverHeals(GameTestHelper h){
        var f=new Fixture(h,"Combat");var u=f.spawn(4,-10);f.research();var probe=new DamageDuringHeal(u);f.probes.add(probe);long[] hit={0};
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{
            u.setHealth(u.getMaxHealth()-4);MinecraftForge.EVENT_BUS.register(probe);h.assertTrue(f.assign(u).equals("assigned"),"explicit care order");
        }).thenWaitUntil(()->h.assertTrue(probe.fired,"real heal callback delivers actual same-tick damage"))
        .thenExecute(()->{hit[0]=f.level.getGameTime();h.assertTrue(u.getHealth()==probe.after,"same-tick damage token blocks outer healing write");})
        .thenExecuteAfter(190,()->h.assertTrue(u.getHealth()==probe.after,"no care during real 10-second post-hit rest window"))
        .thenWaitUntil(()->h.assertTrue(u.getHealth()>probe.after,"healing resumes only after combat rest interval"))
        .thenExecute(()->{h.assertTrue(f.level.getGameTime()-hit[0]>=200,"actual rest duration");h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_ecology",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=400)
    public static void capturedCourtReleaseCannotResetFormerOwnersCommand(GameTestHelper h){
        var f=new Fixture(h,"Capture");var u=f.spawn(4,-10);
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{
            h.assertTrue(f.assign(u).equals("assigned"),"old owner lease");
            String second="EcologyCaptureNew";PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(second,DreamseekersMod.DREAMSEEKERS));var account=new Resources(second,100,100,100);ResourcesServerEvents.resourcesList.add(account);
            try {
                f.court.setOwnerNameFromServerEditor(second);BlockPos newer=f.origin.offset(-15,1,3);u.setMoveTarget(newer);
                var actor=FakePlayerFactory.get(f.level,new GameProfile(UUID.randomUUID(),second));
                EcologyWorkPosts.release(actor,f.court,ResearchPlacementIdentity.serverIdentity(f.court),f.court.getCaptureRevision(),null);
                h.assertTrue(newer.equals(u.getMoveGoal().getMoveTarget()),"new building owner cannot stop old owners unit via stale lease");
            } finally {PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(second));ResourcesServerEvents.resourcesList.removeIf(r->r==account);f.court.setOwnerNameFromServerEditor(f.owner);}
            h.succeed();
        });
    }
    private static void packet(PassiveIncomePeer peer,EcologyPackets.Request original)throws Exception {
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            original.encode(buffer);var decoded=new EcologyPackets.Request(buffer);
            if(!decoded.equals(original)||buffer.readableBytes()!=0)throw new AssertionError("exact bounded ecology request codec");
            var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);
            ctor.setAccessible(true);var context=ctor.newInstance(peer.connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);decoded.handle(()->context);
        }finally{buffer.release();}peer.channel.runPendingTasks();
    }
    @GameTest(templateNamespace="dreamseekers_ecology",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=400)
    public static void authenticatedPacketReplayCannotCancelLaterAssignment(GameTestHelper h){
        var f=new Fixture(h,"Packets");var u=f.spawn(4,-10);
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{
            try(var peer=new PassiveIncomePeer(f.level,f.owner)) {
                var token=com.solegendary.reignofnether.research.economy.EconomyActionGate.query(peer.actor,peer.connection);UUID view=UUID.randomUUID();
                var assign=new EcologyPackets.Payload(EcologyPackets.Action.ASSIGN,f.level.dimension().location(),f.court.originPos,view,
                    ResearchPlacementIdentity.serverIdentity(f.court),f.court.getCaptureRevision(),EcologyPackets.ZERO,List.of(new EcologyPackets.Target(u.getId(),u.getUUID())));
                packet(peer,new EcologyPackets.Request(assign,token.epoch(),token.session(),1));h.assertTrue(EcologyWorkPosts.snapshot(f.court).size()==1,"actual authenticated handler admitted assignment");
                var release=new EcologyPackets.Payload(EcologyPackets.Action.RELEASE_ALL,assign.dimension(),assign.origin(),view,assign.instance(),assign.capture(),EcologyPackets.ZERO,List.of());
                var old=new EcologyPackets.Request(release,token.epoch(),token.session(),2);packet(peer,old);h.assertTrue(EcologyWorkPosts.snapshot(f.court).isEmpty(),"real release request");
                packet(peer,new EcologyPackets.Request(assign,token.epoch(),token.session(),3));UUID current=f.entry(u).lease();packet(peer,old);
                h.assertTrue(f.entry(u).lease().equals(current),"replayed old release returns cached outcome without stopping newer lease");
                var stale=new EcologyPackets.Request(release,UUID.randomUUID(),token.session(),4);packet(peer,stale);h.assertTrue(f.entry(u).lease().equals(current),"wrong account epoch rejected before mutation");
            }catch(Exception e){throw new RuntimeException(e);}h.succeed();
        });
    }

    @GameTest(templateNamespace="dreamseekers_ecology",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1450)
    public static void blockedRealPathExpiresWithoutTeleportOrHealing(GameTestHelper h){
        var f=new Fixture(h,"Timeout");var u=f.spawn(4,-16);f.research();long[] start={0};float[] hp={0};
        BlockPos cage=u.blockPosition();for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=4;y++)if(Math.abs(x)==2||Math.abs(z)==2||y==4)f.level.setBlock(cage.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{
            u.setHealth(u.getMaxHealth()-4);hp[0]=u.getHealth();h.assertTrue(f.assign(u).equals("assigned"),"unobstructed target but physically blocked route accepts reservation");start[0]=f.level.getGameTime();
        }).thenWaitUntil(()->h.assertTrue(f.level.getGameTime()-start[0]>=1199,"real server ticks reach deadline boundary"))
        .thenExecute(()->h.assertTrue(!EcologyWorkPosts.snapshot(f.court).isEmpty()&&!f.entry(u).arrived()&&u.getHealth()==hp[0],"still reserved before approach timeout and no travel healing"))
        .thenExecuteAfter(6,()->{h.assertTrue(EcologyWorkPosts.snapshot(f.court).isEmpty()&&u.getMoveGoal().getMoveTarget()==null,"timeout releases blocked order and native navigation");h.assertTrue(u.distanceToSqr(cage.getX()+.5,cage.getY(),cage.getZ()+.5)<4&&u.getHealth()==hp[0],"never teleported or healed");h.succeed();});
    }
}
