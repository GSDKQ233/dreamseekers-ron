// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.*;
import com.dreamseekers.ecologyworkposts.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.function.DreamWatchtowerPlacement;
import com.dreamseekers.skill.CoordinatedMark;
import com.dreamseekers.unit.StoneSpiritThrowerUnit;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.fogofwar.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.*;
import java.lang.reflect.Field;
import java.util.*;

/** Test-only connected peers and original-body fixtures. No product visibility/authority hook is replaced. */
final class BuildingObservationTestSupport {
    static final String NS="dreamseekers_building_observation";
    private static final List<F> FIXTURES=new ArrayList<>();
    private BuildingObservationTestSupport(){}
    static void cleanup(){for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();}
    record Traffic(List<TowerMarkPackets.Snapshot> marks,List<BuildingAbilityPackets.Snapshot> views,
                   List<BuildingAbilityPackets.Ack> acks,List<EcologyPackets.Snapshot> care,int fogPackets){}
    record Delivery(Traffic owner,Traffic enemy,Traffic other){}
    static final class F implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final String owner,enemy,other;
        final PassiveIncomePeer peer,enemyPeer,otherPeer;final Resources account;final BlockPos origin;
        final BuildingPlacement host;final List<BuildingPlacement> buildings=new ArrayList<>();
        final List<Mob> bodies=new ArrayList<>();final Map<Mob,UUID> original=new IdentityHashMap<>();
        final Map<ServerLevel,Set<Long>> forced=new IdentityHashMap<>();final List<Resources> accounts=new ArrayList<>();
        final List<Runnable> cleanups=new ArrayList<>();long sequence;boolean closed;
        F(GameTestHelper h,String label,int index,boolean tower){
            this.h=h;level=h.getLevel();owner="Observation53_"+label;enemy=owner+"_Enemy";other=owner+"_Other";
            origin=new BlockPos(90000+index*256,100,90000);FIXTURES.add(this);
            for(String name:List.of(owner,enemy,other)){
                h.assertTrue(PlayerServerEvents.getRTSPlayer(name)==null,"fresh fixture owner "+name);
                PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));
                Resources bank=new Resources(name,5000,5000,5000);bank.extra.put(DreamPassiveEconomy.SHADOW,500);
                ResourcesServerEvents.resourcesList.add(bank);accounts.add(bank);
            }
            account=accounts.get(0);peer=new PassiveIncomePeer(level,owner);enemyPeer=new PassiveIncomePeer(level,enemy);otherPeer=new PassiveIncomePeer(level,other);
            load(level,origin,48);
            // Unique high-altitude flat pad; the actual registered host template is installed unchanged.
            for(int x=-40;x<=40;x++)for(int z=-44;z<=24;z++){
                level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=1;y<=24;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            host=install(tower?DreamseekersMod.SHADOW_WATCHTOWER:DreamseekersMod.CONTRACT_ECOLOGY,origin);
        }
        void load(ServerLevel world,BlockPos p,int margin){
            Set<Long> ours=forced.computeIfAbsent(world,k->new HashSet<>());
            for(int x=(p.getX()-margin)>>4;x<=(p.getX()+margin)>>4;x++)for(int z=(p.getZ()-margin)>>4;z<=(p.getZ()+margin)>>4;z++){
                long key=ChunkPos.asLong(x,z);if(!world.getForcedChunks().contains(key)){world.setChunkForced(x,z,true);ours.add(key);}world.getChunk(x,z);
            }
        }
        BuildingPlacement install(Building type,BlockPos at){
            for(var b:BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(level),level,at,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            var p=BuildingUtils.getNewBuildingPlacement(type,level,at,Rotation.NONE,owner,false);
            BuildingServerEvents.getBuildings().add(p);buildings.add(p);p.onBuilt();return p;
        }
        <T extends Mob>T spawn(EntityType<T> type,String name,int x,int z,boolean ai){return spawnAt(type,name,level,origin.offset(x,1,z),ai);}
        <T extends Mob>T spawnAt(EntityType<T> type,String name,ServerLevel world,BlockPos at,boolean ai){
            T body=Objects.requireNonNull(type.create(world));((Unit)body).setOwnerName(name);body.setNoAi(!ai);
            body.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);UUID uuid=body.getUUID();
            h.assertTrue(world.addFreshEntity(body),"original exact unit admitted once");bodies.add(body);original.put(body,uuid);return body;
        }
        Mob unit(String name,int x,int z){return spawn(EntityRegistrar.VILLAGER_UNIT.get(),name,x,z,false);}
        StoneSpiritThrowerUnit guard(){return spawn(DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),owner,3,-4,true);}
        void ready(Mob body){var world=(ServerLevel)body.level();h.assertTrue(body.tickCount>3&&original.get(body).equals(body.getUUID())
            &&world.getEntity(body.getUUID())==body&&world.getEntity(body.getId())==body&&body.isAddedToWorld()&&body.isAlive()&&!body.isRemoved()
            &&world.isPositionEntityTicking(body.blockPosition())&&UnitServerEvents.getAllUnits().stream().filter(e->e==body).count()==1,
            "same original UUID/body naturally ticked and reached both indexes exactly once");}
        void readyAll(){h.assertTrue(host.isBuilt,"real original template completed");for(var body:bodies)ready(body);}
        void enter(StoneSpiritThrowerUnit guard){
            h.assertTrue(host instanceof DreamWatchtowerPlacement tower&&tower.modelRevision()==2,"actual rev2 host");
            h.assertTrue(guard.canGarrison()&&GarrisonableBuildingAddon.getGarrison(guard)==null,"real eligible unseated body");
            UnitServerEvents.addActionItem(owner,UnitAction.GARRISON,-1,new int[]{guard.getId()},host.originPos,BlockPos.ZERO);
        }
        void seated(StoneSpiritThrowerUnit guard){ready(guard);h.assertTrue(GarrisonableBuildingAddon.getGarrison(guard)==host&&guard.onGround()
            &&level.noCollision(guard),"original body naturally reached and settled in collision-safe native garrison seat");}
        void freeze(StoneSpiritThrowerUnit guard){seated(guard);Unit.fullResetBehaviours(guard);guard.setNoAi(true);guard.getNavigation().stop();}
        void clearTraffic(){peer.drain();enemyPeer.drain();otherPeer.drain();}
        Delivery collect(){return new Delivery(read(h,peer),read(h,enemyPeer),read(h,otherPeer));}
        Delivery exchange(BuildingAbilityPackets.Request request){clearTraffic();transmit(h,peer,request);return collect();}
        Delivery cast(){return exchange(request(BuildingAbilityPackets.Action.CAST,0,++sequence));}
        Delivery query(int page){return exchange(request(BuildingAbilityPackets.Action.CODEX_QUERY,page,0));}
        BuildingAbilityPackets.Request request(BuildingAbilityPackets.Action action,int page,long seq){
            var v=BuildingAbilityService.view(peer.actor,host);h.assertTrue(v!=null,"exact source view");var token=EconomyActionGate.query(peer.actor,peer.connection);
            h.assertTrue(token!=null,"real live shared economy session");
            return new BuildingAbilityPackets.Request(new BuildingAbilityPackets.Payload(action,level.dimension().location(),host.originPos,UUID.randomUUID(),
                v.instance(),v.capture(),v.generation(),v.revision(),page),token.epoch(),token.session(),seq);
        }
        Delivery ecology(EcologyPackets.Action action,List<EcologyPackets.Target> targets,UUID lease){
            var token=EconomyActionGate.query(peer.actor,peer.connection);
            var request=new EcologyPackets.Request(new EcologyPackets.Payload(action,level.dimension().location(),host.originPos,UUID.randomUUID(),
                ResearchPlacementIdentity.serverIdentity(host),host.getCaptureRevision(),lease,targets),token.epoch(),token.session(),++sequence);
            clearTraffic();transmitEcology(h,peer,request);return collect();
        }
        int shadow(){return account.extra.get(DreamPassiveEconomy.SHADOW);}
        long cooldownAt(){return host.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.WATCHTOWER_MARK);}
        void assertPrivate(Delivery d){
            for(Traffic t:List.of(d.enemy(),d.other()))h.assertTrue(t.marks().isEmpty()&&t.views().isEmpty()&&t.acks().isEmpty()&&t.care().isEmpty(),
                "actual unrelated/enemy embedded recipient received no private source, roster, or target packet");
            for(var s:d.owner().marks()){h.assertTrue(s.valid()&&s.owner().equals(owner)&&s.origin().equals(host.originPos),"real mark publication bound to casting owner/source");
                for(var row:s.targets())h.assertTrue(bodies.stream().anyMatch(e->row.id()==e.getId()&&row.uuid().equals(original.get(e))),"actual outbound row references original published body ID+UUID");}
        }
        void visible(List<? extends Mob> targets){var source=BuildingAbilitySource.capture(peer.actor,host);h.assertTrue(source!=null&&source.valid(),"live authority");
            for(var body:targets){ready(body);boolean visible=CoordinatedMark.visible(peer.actor,body);boolean los=LoadedBuildingSight.clear(level,TowerObservation.eye(host),body.getEyePosition(),null);
                h.assertTrue(visible&&los,"fixture visible="+visible+" los="+los+" invisible="+body.isInvisible()+" hp="+body.getHealth()+" target="+body.position()+" eye="+TowerObservation.eye(host)+" uuid="+body.getUUID());}}
        @Override public void close(){if(closed)return;closed=true;for(int i=cleanups.size()-1;i>=0;i--)cleanups.get(i).run();
            peer.close();enemyPeer.close();otherPeer.close();for(var body:bodies)if(!body.isRemoved())body.discard();
            for(var p:buildings){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}
            for(String name:List.of(owner,enemy,other)){ResearchServerEvents.removeAllResearchFor(name);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(name));}
            AlliancesServerEvents.removeAlliance(owner,other);ResourcesServerEvents.resourcesList.removeAll(accounts);
            forced.forEach((world,keys)->keys.forEach(key->world.setChunkForced(ChunkPos.getX(key),ChunkPos.getZ(key),false)));
        }
    }
    static GameTestSequence occupy(F f,StoneSpiritThrowerUnit guard){return f.h.startSequence().thenWaitUntil(f::readyAll)
        .thenExecute(()->f.enter(guard)).thenWaitUntil(()->f.seated(guard)).thenExecute(()->f.freeze(guard));}
    static void invisible(Mob body){body.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY,2400,0));body.setInvisible(true);}
    static List<Mob> enemies(F f,int count){var out=new ArrayList<Mob>();for(int i=0;i<count;i++)out.add(f.unit(f.enemy,-12+4*i,-16));return out;}
    static List<Mob> distanceOrder(F f,List<Mob> bodies){return bodies.stream().sorted(Comparator.comparingDouble((Mob e)->e.distanceToSqr(f.origin.getX()+.5,f.origin.getY()+.5,f.origin.getZ()+.5)).thenComparing(e->e.getUUID().toString())).toList();}
    static List<Mob> codexOrder(List<Mob> bodies){return bodies.stream().sorted(Comparator.comparing((Mob e)->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()).thenComparing(e->e.getUUID().toString())).toList();}
    static List<UUID> ids(List<? extends LivingEntity> bodies){return bodies.stream().map(Entity::getUUID).toList();}
    static TowerMarkPackets.Snapshot lastMark(F f,Delivery d){f.assertPrivate(d);f.h.assertTrue(!d.owner().marks().isEmpty(),"real owner outbound contains an observation snapshot");return d.owner().marks().get(d.owner().marks().size()-1);}
    static BuildingAbilityPackets.Snapshot view(F f,Delivery d){f.assertPrivate(d);f.h.assertTrue(d.owner().views().size()==1,"exactly one actual owner view");var s=d.owner().views().get(0);f.h.assertTrue(s.valid()&&s.owner().equals(f.owner)&&s.origin().equals(f.host.originPos),"owner-bound real snapshot");return s;}
    static String status(F f,Delivery d){f.assertPrivate(d);if(!d.owner().views().isEmpty())return view(f,d).status();f.h.assertTrue(d.owner().acks().size()==1,"exactly one real completion ACK");return d.owner().acks().get(0).status();}
    private static NetworkEvent.Context context(PassiveIncomePeer peer){try{
        var ctor=NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,NetworkDirection.class,int.class);ctor.setAccessible(true);
        return ctor.newInstance(peer.connection,NetworkDirection.PLAY_TO_SERVER,-1);
    }catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    static void transmit(GameTestHelper h,PassiveIncomePeer peer,BuildingAbilityPackets.Request request){
        var bytes=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{request.encode(bytes);var decoded=new BuildingAbilityPackets.Request(bytes);
            h.assertTrue(decoded.equals(request)&&bytes.readableBytes()==0,"actual decoded request, not direct service emulation");decoded.handle(()->context(peer));
        }finally{bytes.release();}peer.channel.runPendingTasks();}
    static void transmitEcology(GameTestHelper h,PassiveIncomePeer peer,EcologyPackets.Request request){
        var bytes=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{request.encode(bytes);var decoded=new EcologyPackets.Request(bytes);
            h.assertTrue(decoded.equals(request)&&bytes.readableBytes()==0,"original care request codec");decoded.handle(()->context(peer));
        }finally{bytes.release();}peer.channel.runPendingTasks();}
    private static Traffic read(GameTestHelper h,PassiveIncomePeer peer){
        var marks=new ArrayList<TowerMarkPackets.Snapshot>();var views=new ArrayList<BuildingAbilityPackets.Snapshot>();
        var acks=new ArrayList<BuildingAbilityPackets.Ack>();var care=new ArrayList<EcologyPackets.Snapshot>();int fog=0;
        Set<Integer> fogIds=Set.of(PassiveIncomePeer.discriminator(new FogOfWarClientboundPacket(false,"",0)),
            PassiveIncomePeer.discriminator(new FogChunksClientboundPacket(Set.of(),Map.of())),
            PassiveIncomePeer.discriminator(new FogNeutralUnitClientboundPacket(new java.util.UUID(0,0),new AABB(0,0,0,0,0,0),false)));
        for(Object out:peer.drain())if(out instanceof ClientboundCustomPayloadPacket p){
            var b=new FriendlyByteBuf(p.getData().copy());try{
                int discriminator=b.readVarInt();boolean consumed=false;
                if(p.getIdentifier().equals(DreamseekersMod.id("tower_observations"))){
                    h.assertTrue(discriminator==0,"actual tower S2C discriminator");var snapshot=new TowerMarkPackets.Snapshot(b);h.assertTrue(snapshot.valid(),"valid actual tower packet");marks.add(snapshot);consumed=true;
                }else if(p.getIdentifier().equals(DreamseekersMod.id("building_abilities"))){
                    if(discriminator==1){var snapshot=new BuildingAbilityPackets.Snapshot(b);h.assertTrue(snapshot.valid(),"valid actual ability S2C");views.add(snapshot);}
                    else if(discriminator==2){var ack=new BuildingAbilityPackets.Ack(b);h.assertTrue(ack.valid(),"valid actual ACK");acks.add(ack);}else throw new AssertionError("unexpected outbound ability packet");consumed=true;
                }else if(p.getIdentifier().equals(DreamseekersMod.id("ecology_workposts"))){h.assertTrue(discriminator==1,"original care S2C");var snapshot=new EcologyPackets.Snapshot(b);h.assertTrue(snapshot.valid(),"valid actual original care snapshot");care.add(snapshot);consumed=true;}
                else if(p.getIdentifier().getNamespace().equals(ReignOfNether.MOD_ID)&&p.getIdentifier().getPath().equals("main")&&fogIds.contains(discriminator))fog++;
                // On captured server output, consuming exactly the declared IDs/UUIDs (and source origin)
                // leaves no enemy XYZ/AABB/trailing payload. This is not a fabricated roundtrip-only test.
                if(consumed)h.assertTrue(b.readableBytes()==0,"actual private payload has no undeclared coordinate/trailing fields");
            }finally{b.release();}
        }
        return new Traffic(List.copyOf(marks),List.copyOf(views),List.copyOf(acks),List.copyOf(care),fog);
    }

    /** Native fog masks are controlled inputs, not mocked visible(). Run only in its separate batch. */
    static final class Fog implements AutoCloseable {
        final F f;final boolean enabled,force;final double size,x,z;final int timer;
        final Map<UUID,Set<ChunkPos>> live,edge,pending,oldLive,oldEdge,oldPending;
        final Map<UUID,Map<ChunkPos,long[]>> masks,oldMasks;boolean closed;
        @SuppressWarnings("unchecked") Fog(F f){this.f=f;enabled=FogOfWarServerEvents.isEnabled();force=f.level.getGameRules().getBoolean(GameRuleRegistrar.FORCE_FOG);
            size=f.level.getWorldBorder().getSize();x=f.level.getWorldBorder().getCenterX();z=f.level.getWorldBorder().getCenterZ();timer=(int)get("ticksUntilUpdate");
            live=(Map<UUID,Set<ChunkPos>>)get("playerLiveChunks");edge=(Map<UUID,Set<ChunkPos>>)get("playerEdgeChunks");pending=(Map<UUID,Set<ChunkPos>>)get("pendingResends");masks=(Map<UUID,Map<ChunkPos,long[]>>)get("playerEdgeMasks");
            oldLive=sets(live);oldEdge=sets(edge);oldPending=sets(pending);oldMasks=maskCopy(masks);f.cleanups.add(this::close);
        }
        void install(){f.h.assertTrue(!enabled,"isolated fog batch starts without another fog test");f.level.getGameRules().getRule(GameRuleRegistrar.FORCE_FOG).set(false,f.level.getServer());
            f.level.getWorldBorder().setCenter(f.origin.getX(),f.origin.getZ());f.level.getWorldBorder().setSize(1280);FogOfWarServerEvents.invalidateRtsCache();FogOfWarServerEvents.setEnabled(true);
            f.h.assertTrue(FogOfWarServerEvents.isFogActiveFor(f.peer.actor),"real public fog enabled for real RTS peer");set("ticksUntilUpdate",10000);columns(List.of());}
        void columns(List<BlockPos> positions){f.h.assertTrue((int)get("ticksUntilUpdate")>9000,"controlled native-cache interval remains bounded");
            Map<ChunkPos,long[]> map=new HashMap<>();for(var p:positions){var bits=map.computeIfAbsent(new ChunkPos(p),k->new long[4]);int i=((p.getX()&15)<<4)|(p.getZ()&15);bits[i>>6]|=1L<<(i&63);}
            live.put(f.peer.actor.getUUID(),new HashSet<>());edge.put(f.peer.actor.getUUID(),new HashSet<>(map.keySet()));masks.put(f.peer.actor.getUUID(),map);}
        String ownerMasks(){var map=masks.getOrDefault(f.peer.actor.getUUID(),Map.of());return map.entrySet().stream().sorted(Comparator.comparingLong(e->e.getKey().toLong())).map(e->e.getKey()+Arrays.toString(e.getValue())).toList().toString()+live.get(f.peer.actor.getUUID())+edge.get(f.peer.actor.getUUID());}
        @Override public void close(){if(closed)return;closed=true;try{f.level.getGameRules().getRule(GameRuleRegistrar.FORCE_FOG).set(false,f.level.getServer());FogOfWarServerEvents.setEnabled(enabled);}
            finally{live.clear();live.putAll(sets(oldLive));edge.clear();edge.putAll(sets(oldEdge));pending.clear();pending.putAll(sets(oldPending));masks.clear();masks.putAll(maskCopy(oldMasks));set("ticksUntilUpdate",timer);FogOfWarServerEvents.invalidateRtsCache();f.level.getWorldBorder().setCenter(x,z);f.level.getWorldBorder().setSize(size);f.level.getGameRules().getRule(GameRuleRegistrar.FORCE_FOG).set(force,f.level.getServer());}}
        static Map<UUID,Set<ChunkPos>> sets(Map<UUID,Set<ChunkPos>> in){var out=new HashMap<UUID,Set<ChunkPos>>();in.forEach((k,v)->out.put(k,new HashSet<>(v)));return out;}
        static Map<UUID,Map<ChunkPos,long[]>> maskCopy(Map<UUID,Map<ChunkPos,long[]>> in){var out=new HashMap<UUID,Map<ChunkPos,long[]>>();in.forEach((k,v)->{var copy=new HashMap<ChunkPos,long[]>();v.forEach((p,b)->copy.put(p,b.clone()));out.put(k,copy);});return out;}
        static Object get(String name){try{Field field=FogOfWarServerEvents.class.getDeclaredField(name);field.setAccessible(true);return field.get(null);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
        static void set(String name,int value){try{Field field=FogOfWarServerEvents.class.getDeclaredField(name);field.setAccessible(true);field.setInt(null,value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    }
}
