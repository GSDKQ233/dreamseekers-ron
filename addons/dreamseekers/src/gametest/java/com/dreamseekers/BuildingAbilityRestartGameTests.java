// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.*;
import com.dreamseekers.economy.*;
import com.dreamseekers.resonance.ResonanceResearch;
import com.dreamseekers.skill.*;
import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.EconomyActionGate;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Real paid requests -> normal world save -> second JVM's original Anvil bodies/buildings.
 * The completed WIND tree and existing resonance research are declared prerequisite fixtures,
 * NOT evidence of having paid for those old upgrades. New ability state/payment is never injected.
 * Natural garden income stays enabled and is audited independently of exact five debits.
 * This WIND_V4 prerequisite is not ascended: shadow-tree income must remain exactly zero.
 * Embedded peers exercise actual server authority/codec, not remote sockets or rendered clients.
 */
@GameTestHolder("dreamseekers_building_abilities_restart")
@PrefixGameTestTemplate(false)
public final class BuildingAbilityRestartGameTests {
    private static final String PHASE="dreamseekers.building_abilities.restart.phase";
    private static final String OWNER="BuildingAbilitiesRestart_Owner", ENEMY="BuildingAbilitiesRestart_Enemy";
    private static final File MANIFEST=new File("building-abilities-restart-expectations.nbt");
    private static final File VERIFIED=new File("building-abilities-restart-verified.nbt");
    private static final BlockPos ORIGIN=new BlockPos(-64,100,-64);
    private static final Map<String,BuildingPlacement> HOSTS=new LinkedHashMap<>();
    private static final Map<String,Mob> BODIES=new LinkedHashMap<>();
    private static final Set<Long> CHUNKS=new LinkedHashSet<>();
    // Full evidence/read scope is independent of who forced a chunk. Never own or clear
    // Minecraft GameTest StructureUtils.forceLoadChunks' pre-existing arena tickets.
    private static final Set<Long> PREEXISTING_CHUNKS=new LinkedHashSet<>(),OWNED_CHUNKS=new LinkedHashSet<>();
    private static int[] nativeArenaChunk;
    private static final Map<String,CompoundTag> JOINS=new LinkedHashMap<>(),PUBLISHED=new LinkedHashMap<>();
    private static final ListTag PAYMENTS=new ListTag();
    private static CompoundTag expectations,afterCasts;
    private static Resources account;
    private static PassiveIncomePeer peer;
    private static UUID originalSession;
    private static long castAt,sequence;
    private static double rootBase,hallBase;
    private static float gardenBefore;
    private static boolean verified;
    private static ListTag codexOriginal;
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static void near(double got,double want,String why){require(Double.isFinite(got)&&Math.abs(got-want)<.0005,why+" got="+got+" expected="+want);}
    private static long jvm(){return java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();}
    private static BuildingAbilityState state(BuildingPlacement host){return host.getDataStorage().getData(BuildingAbilityState.TYPE);}
    private static void write(CompoundTag value,File file){try{NbtIo.writeCompressed(value,file);}catch(Exception e){throw new RuntimeException(e);}}
    private static CompoundTag accountTag(){var t=new CompoundTag();t.putInt("food",account.food);t.putInt("wood",account.wood);t.putInt("ore",account.ore);t.putInt("emerald",account.emerald);account.writeExtended(t);return t;}
    private static int shadow(CompoundTag t){return t.getCompound("resourceExtensions").getCompound("extra").getInt("dreamseekers:shadow_energy");}
    private static long revision(CompoundTag t){return t.getCompound("resourceExtensions").getLong("revision");}
    private static void naturalIncomeOnly(CompoundTag before,CompoundTag after,long elapsed,String label){
        long dw=(long)after.getInt("wood")-before.getInt("wood");
        require(dw==0,label+" WIND fixture is not an ascended shadow producer; no tree income");long trees=0;
        long gardens=(long)after.getInt("food")-before.getInt("food")-5*trees;
        require(gardens>=0&&trees<=elapsed/100+1&&gardens<=elapsed/40+1,label+" bounded real elapsed income");
        require((long)after.getInt("ore")-before.getInt("ore")==5*trees&&(long)after.getInt("emerald")-before.getInt("emerald")==trees
                &&(long)shadow(after)-shadow(before)==trees&&revision(after)-revision(before)==trees+gardens,label+" no refund/debit hidden among native income vectors");
        var a=before.getCompound("resourceExtensions").copy();var b=after.getCompound("resourceExtensions").copy();
        a.remove("extra");b.remove("extra");a.remove("revision");b.remove("revision");require(a.equals(b),label+" same native epoch/schema/receipt envelope");
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities_restart",template="ore_mine_test_arena",batch="building_abilities_restart",timeoutTicks=1600)
    public static void fiveRealPaidAbilitiesAndFreeCodexSurviveOnlyAsSavedCooldownsAcrossTwoJvms(GameTestHelper h)throws Exception{
        switch(System.getProperty(PHASE,"")){case "prepare"->prepare(h);case "verify"->verify(h);default->throw new IllegalArgumentException(PHASE+"=prepare|verify required");}
    }
    private static void geometry(GameTestHelper h){
        var arena=new AABB(h.absolutePos(new BlockPos(-4,-4,-4)),h.absolutePos(new BlockPos(116,36,140)));
        var footprint=new AABB(ORIGIN.offset(-8,-1,-8),ORIGIN.offset(120,28,58));
        h.assertTrue(!footprint.intersects(arena),"persistent fixture cannot intersect arena cleanup/marker expanded bounds");
        var spawn=h.getLevel().getSharedSpawnPos();
        for(int x=(ORIGIN.getX()-8)>>4;x<=(ORIGIN.getX()+120)>>4;x++)for(int z=(ORIGIN.getZ()-8)>>4;z<=(ORIGIN.getZ()+58)>>4;z++){
            h.assertTrue(Math.abs(x-(spawn.getX()>>4))<=5&&Math.abs(z-(spawn.getZ()>>4))<=5,"fixture deeply inside native startup441 area");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(x,z)!=null,"already native startup-loaded before any fixture tickets");
        }
        System.out.println("BUILDING_ABILITIES_RESTART_GEOMETRY phase="+System.getProperty(PHASE)+" spawn="+spawn+" arena="+arena+" fixture="+footprint);
    }
    private static boolean nativeArenaTicket(int x,int z){return x>=nativeArenaChunk[0]-1&&x<nativeArenaChunk[0]+4&&z>=nativeArenaChunk[1]-1&&z<nativeArenaChunk[1]+4;}
    private static void ticketWitness(ServerLevel level){
        var union=new LinkedHashSet<Long>(PREEXISTING_CHUNKS);require(Collections.disjoint(PREEXISTING_CHUNKS,OWNED_CHUNKS),"existing/native and test-owned ticket sets are disjoint");union.addAll(OWNED_CHUNKS);require(union.equals(CHUNKS),"all fixture chunks remain in complete Anvil evidence scope");
        for(long packed:CHUNKS){require(level.getForcedChunks().contains(packed),"original persistent fixture ticket still present");require(PREEXISTING_CHUNKS.contains(packed)==nativeArenaTicket(ChunkPos.getX(packed),ChunkPos.getZ(packed)),"known native arena intersection is not silently adopted");}
        for(int x=nativeArenaChunk[0]-1;x<nativeArenaChunk[0]+4;x++)for(int z=nativeArenaChunk[1]-1;z<nativeArenaChunk[1]+4;z++)require(level.getForcedChunks().contains(ChunkPos.asLong(x,z)),"all original native GameTest tickets retained, including outside fixture evidence scope");
    }
    private static BuildingPlacement install(ServerLevel level,String key,Building type,int x,int z){
        var p=ORIGIN.offset(x,0,z);
        for(var b:BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(level),level,p,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        var host=BuildingUtils.getNewBuildingPlacement(type,level,p,Rotation.NONE,OWNER,false);
        BuildingServerEvents.getBuildings().add(host);host.onBuilt();HOSTS.put(key,host);return host;
    }
    private static <T extends Mob>T publish(ServerLevel level,String key,T body,BuildingPlacement host,int x,int z,String owner){
        var pos=host.originPos.offset(x,1,z);((Unit)body).setOwnerName(owner);body.setNoAi(true);body.setPersistenceRequired();
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){level.setBlock(pos.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<5;y++)level.setBlock(pos.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);}
        body.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);((Unit)body).setAnchor(pos.below());
        body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);body.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);
        require(level.addFreshEntity(body),"real original body published");BODIES.put(key,body);return body;
    }
    private static void ready(GameTestHelper h,Mob body){var level=h.getLevel();h.assertTrue(body.isAlive()&&!body.isRemoved()&&body.isAddedToWorld()&&body.tickCount>2
        &&level.getEntity(body.getUUID())==body&&level.getEntity(body.getId())==body&&level.isPositionEntityTicking(body.blockPosition())
        &&UnitServerEvents.getAllUnits().stream().filter(e->e==body).count()==1,"native exact body ready "+body.getUUID());}
    private record Wire(BuildingAbilityPackets.Snapshot ability,BuildingAbilityPackets.Ack ack,List<TowerMarkPackets.Snapshot> marks){
        String status(){return ability!=null?ability.status():ack!=null?ack.status():"missing";}
        UUID session(){return ability!=null?ability.session():ack!=null?ack.session():null;}
        UUID epoch(){return ability!=null?ability.epoch():ack!=null?ack.epoch():null;}
        String owner(){return ability!=null?ability.owner():ack!=null?ack.owner():"";}
        long request(){return ability!=null?ability.request():ack!=null?ack.request():-1;}
    }
    private static void envelope(Wire reply,UUID session,long sequence){require(reply.session()!=null&&reply.session().equals(session)&&reply.epoch().equals(account.accountEpoch)&&reply.owner().equals(OWNER)&&reply.request()==sequence,"actual response current session/epoch/owner/sequence");}
    private static Wire wire(BuildingAbilityPackets.Request request){
        try{
            var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);
            var context=ctor.newInstance(peer.connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);
            var bytes=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{request.encode(bytes);var decoded=new BuildingAbilityPackets.Request(bytes);require(decoded.equals(request)&&bytes.readableBytes()==0,"real request wire roundtrip");decoded.handle(()->context);}finally{bytes.release();}
            BuildingAbilityPackets.Snapshot answer=null;BuildingAbilityPackets.Ack ack=null;var marks=new ArrayList<TowerMarkPackets.Snapshot>();
            for(var out:peer.drain())if(out instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket payload){
                if(payload.getIdentifier().equals(DreamseekersMod.id("building_abilities"))){var b=new net.minecraft.network.FriendlyByteBuf(payload.getData().copy());try{int discriminator=b.readVarInt();require(answer==null&&ack==null,"one exact action completion, no repeated snapshot");if(discriminator==1){answer=new BuildingAbilityPackets.Snapshot(b);require(answer.valid(),"bounded actual ability response");}else if(discriminator==2){ack=new BuildingAbilityPackets.Ack(b);require(ack.valid(),"bounded data-free actual action acknowledgement");}else throw new AssertionError("unexpected ability response "+discriminator);require(b.readableBytes()==0,"exact response bytes consumed");}finally{b.release();}}
                if(payload.getIdentifier().equals(DreamseekersMod.id("tower_observations"))){var b=new net.minecraft.network.FriendlyByteBuf(payload.getData().copy());try{require(b.readVarInt()==0,"actual tower response discriminator");var s=new TowerMarkPackets.Snapshot(b);require(s.valid()&&b.readableBytes()==0,"bounded actual tower response");marks.add(s);}finally{b.release();}}
            }
            return new Wire(answer,ack,marks);
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static BuildingAbilityPackets.Request request(BuildingPlacement host,BuildingAbilityPackets.Action action,UUID session,long number){
        var v=BuildingAbilityService.view(peer.actor,host);require(v!=null&&v.supported(),"genuine supported owner view");
        return new BuildingAbilityPackets.Request(new BuildingAbilityPackets.Payload(action,host.level.dimension().location(),host.originPos,UUID.randomUUID(),v.instance(),v.capture(),v.generation(),v.revision(),0),account.accountEpoch,session,number);
    }
    private static void pay(String key){
        var host=HOSTS.get(key);var ability=BuildingAbility.forBuilding(host);var before=accountTag();var beforeState=state(host).write();
        require(ability.paid()&&beforeState.getLong("revision")==0&&state(host).readyAt(ability)==0,"fresh original paid source, never injected state");
        var token=EconomyActionGate.query(peer.actor,peer.connection);require(token!=null&&token.epoch().equals(account.accountEpoch),"actual authority token");originalSession=token.session();
        var req=request(host,BuildingAbilityPackets.Action.CAST,token.session(),++sequence);var result=wire(req);envelope(result,token.session(),sequence);var after=accountTag();
        require(result.ability()!=null&&result.ability().status().equals("success")&&result.ability().request()==sequence,"actual authenticated decoded CAST success "+key);
        require(before.getInt("food")-after.getInt("food")==ability.foodCost()&&shadow(before)-shadow(after)==ability.shadowCost()
            &&before.getInt("wood")==after.getInt("wood")&&before.getInt("ore")==after.getInt("ore")&&before.getInt("emerald")==after.getInt("emerald")
            &&revision(after)==revision(before)+1,"one exact real debit no synthetic paid claim "+key);
        require(state(host).revision()==1&&state(host).readyAt(ability)==castAt+ability.cooldownTicks()&&BuildingAbilityEffects.remaining(host)==ability.durationTicks(),"one original persisted CD plus actual live plan "+key);
        if(ability==BuildingAbility.WATCHTOWER_MARK)require(result.marks().stream().anyMatch(s->s.session().equals(token.session())&&s.epoch().equals(account.accountEpoch)&&s.owner().equals(OWNER)&&s.remaining()==120&&s.targets().stream().anyMatch(t->t.uuid().equals(BODIES.get("enemy").getUUID()))),"actual private mark packet includes exact enemy UUID");
        var row=new CompoundTag();row.putString("key",key);row.putString("ability",ability.path());row.putLong("at",castAt);row.put("before",before);row.put("after",after);row.put("beforeState",beforeState);row.put("afterState",state(host).write());row.putUUID("session",token.session());row.putLong("sequence",sequence);row.putLong("capture",req.payload().capture());row.putLong("generation",req.payload().generation());PAYMENTS.add(row);
        // Exact duplicate goes through the real nonce/session gate, not direct service replay.
        var duplicate=wire(req);envelope(duplicate,token.session(),sequence);require(duplicate.ability()==null&&duplicate.ack()!=null&&duplicate.status().equals("success")&&duplicate.marks().isEmpty(),"duplicate completion-only Ack cannot refresh private data");require(accountTag().equals(after)&&state(host).revision()==1,"duplicate cannot repay/refund/restart original deadline");
    }
    private static ListTag queryCodex(){
        var before=accountTag();var host=HOSTS.get("codex");var saved=state(host).write();var token=EconomyActionGate.query(peer.actor,peer.connection);
        var response=wire(request(host,BuildingAbilityPackets.Action.CODEX_QUERY,token.session(),0));envelope(response,token.session(),0);var reply=response.ability();
        require(reply!=null&&reply.ability()==BuildingAbility.ECOLOGY_CODEX&&reply.status().equals("ready")&&reply.cooldown()==0&&reply.activeTicks()==0,"actual informational Codex response has no paid cooldown");
        require(accountTag().equals(before)&&state(host).write().equals(saved)&&state(host).revision()==0&&state(host).readyAt(BuildingAbility.ECOLOGY_CODEX)==0,"Codex QUERY truly free and zero-CD, not sixth debit");
        var rows=new ListTag();for(var row:reply.codex().rows()){var t=new CompoundTag();t.putUUID("uuid",row.uuid());t.putString("type",row.typeId().toString());rows.add(t);}
        require(reply.codex().totalRows()==5&&rows.size()==5,"private live roster contains five own originals, excludes enemy");return rows;
    }
    private static void prepare(GameTestHelper h){
        geometry(h);require(!MANIFEST.exists()&&!VERIFIED.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"fresh isolated prepare directory");var level=h.getLevel();var arenaChunk=new ChunkPos(h.absolutePos(BlockPos.ZERO));nativeArenaChunk=new int[]{arenaChunk.x,arenaChunk.z};
        for(int x=(ORIGIN.getX()-8)>>4;x<=(ORIGIN.getX()+120)>>4;x++)for(int z=(ORIGIN.getZ()-8)>>4;z<=(ORIGIN.getZ()+58)>>4;z++){
            long packed=ChunkPos.asLong(x,z);CHUNKS.add(packed);
            if(level.getForcedChunks().contains(packed)){require(nativeArenaTicket(x,z),"only already observed native arena tickets may overlap this fresh fixture");PREEXISTING_CHUNKS.add(packed);}
            else{require(!nativeArenaTicket(x,z),"native GameTest arena ticket must predate this fixture");level.setChunkForced(x,z,true);OWNED_CHUNKS.add(packed);}}
        ticketWitness(level);System.out.println("BUILDING_ABILITIES_RESTART_TICKETS all="+CHUNKS.size()+" nativePreexisting="+PREEXISTING_CHUNKS.size()+" fixtureOwned="+OWNED_CHUNKS.size()+" noPreexistingMutation=true");
        for(BlockPos at:BlockPos.betweenClosed(ORIGIN.offset(-8,-1,-8),ORIGIN.offset(120,27,57)))level.setBlock(at,at.getY()==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        for(String owner:List.of(OWNER,ENEMY)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(owner,DreamseekersMod.DREAMSEEKERS,owner.equals(OWNER)?97564:97565,0,false,0L,0L));ResourcesServerEvents.resourcesList.add(new Resources(owner,1000,1000,1000));}
        account=ExtendedResourcesSync.account(OWNER);account.extra.put(DreamPassiveEconomy.SHADOW,500);peer=new PassiveIncomePeer(level,OWNER);
        require(EconomyActionGate.query(peer.actor,peer.connection)!=null,"actual query establishes the connected economy session before availability inspection");
        ResearchServerEvents.addCheat(OWNER,"foodforthought");ResearchServerEvents.addResearch(OWNER,ResonanceResearch.ID); // explicit OLD prerequisite fixture, not this test's purchase evidence
        var root=(DreamAnchorPlacement)install(level,"root",DreamseekersMod.DREAM_ANCHOR,0,0);
        for(var b:root.getBlocks())level.setBlock(b.getBlockPos(),Blocks.AIR.defaultBlockState(),2);
        var advanced=root.blocks(WorldTreeModel.WIND_V4);for(var b:advanced)level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        root.tree().model=WorldTreeModel.WIND_V4;root.tree().phase=WorldTreeState.Phase.COMPLETE;root.tree().ascended=false;root.tree().geometryHighest=WorldTreeModel.WIND_V4.count;root.commitGeometry(advanced,WorldTreeModel.WIND_V4.count,0);
        var dais=install(level,"dais",DreamseekersMod.RESONANCE_DAIS,24,0);var garden=install(level,"garden",DreamseekersMod.REVERIE_GARDEN,48,0);
        var hall=install(level,"hall",DreamseekersMod.DREAM_HALL,72,0);install(level,"lab",DreamseekersMod.RESONANCE_LABORATORY,96,0);
        install(level,"codex",DreamseekersMod.CONTRACT_ECOLOGY,0,36);var tower=install(level,"watch",DreamseekersMod.SHADOW_WATCHTOWER,32,36);
        publish(level,"root",EntityRegistrar.VILLAGER_UNIT.get().create(level),root,-3,-3,OWNER);
        var heal=publish(level,"garden",EntityRegistrar.VILLAGER_UNIT.get().create(level),garden,-3,-3,OWNER);heal.setHealth(heal.getMaxHealth()-8);
        publish(level,"hall",DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(level),hall,-3,-3,OWNER);
        var ward=publish(level,"dais",EntityRegistrar.VILLAGER_UNIT.get().create(level),dais,-3,-3,OWNER);ward.getAttribute(Attributes.ARMOR).setBaseValue(0);
        var occupant=publish(level,"garrison",DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get().create(level),tower,3,-6,OWNER);
        publish(level,"enemy",EntityRegistrar.VILLAGER_UNIT.get().create(level),tower,-10,-10,ENEMY);
        h.startSequence().thenWaitUntil(()->{for(var b:BODIES.values())ready(h,b);for(var b:HOSTS.values())h.assertTrue(b.isBuilt&&!b.shouldBeDestroyed(),"native building completed");})
          .thenExecute(()->{occupant.setNoAi(false);UnitServerEvents.addActionItem(OWNER,UnitAction.GARRISON,-1,new int[]{occupant.getId()},tower.originPos,BlockPos.ZERO);})
          .thenWaitUntil(()->{h.assertTrue(GarrisonableBuildingAddon.getGarrison(occupant)==tower,"actual native garrison arrival, not slot injection");for(var b:BODIES.values())ready(h,b);
              h.assertTrue(heal.tickCount>205&&CoordinatedMark.visible(peer.actor,BODIES.get("enemy")),"actual rest and existing fog visibility ready");
              for(String key:List.of("root","garden","hall","dais","watch")){var v=BuildingAbilityService.view(peer.actor,HOSTS.get(key));h.assertTrue(v!=null&&v.available(),"actual source/targets/preconditions ready "+key+" view="+v);}})
          .thenExecute(()->{occupant.setNoAi(true);castAt=level.getGameTime();rootBase=BODIES.get("root").getAttributeValue(Attributes.MOVEMENT_SPEED);hallBase=BODIES.get("hall").getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get());gardenBefore=heal.getHealth();
              codexOriginal=queryCodex();for(String key:List.of("root","garden","hall","dais","watch"))pay(key);afterCasts=accountTag();
              near(BODIES.get("root").getAttributeValue(Attributes.MOVEMENT_SPEED),rootBase*1.15,"actual paid movement attribute");near(BODIES.get("hall").getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get()),hallBase*1.10,"actual paid hall AD attribute");
              require(DaisWardEffects.active(ward)&&TowerObservation.marked(BODIES.get("enemy")),"paid ward and private marker are actually live");
              // Native core replaces WorkerUnit damage with its real AD; set the explicit10HP fixture after native initialization.
              BODIES.get("enemy").getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);
              ward.invulnerableTime=0;float before=ward.getHealth();require(ward.hurt(ward.damageSources().mobAttack(BODIES.get("enemy")),10),"real incoming hostile damage");near(before-ward.getHealth(),9,"real final-damage consumer applies exact10percent ward");
          }).thenExecuteAfter(25,()->{near(heal.getHealth(),gardenBefore+1,"actual first garden pulse occurred before normal stop");
              MinecraftForge.EVENT_BUS.register(new BuildingAbilityRestartStopWitness());System.out.println("BUILDING_ABILITIES_PREPARED_WAIT_NORMAL_FULL_SAVE");h.succeed();});
    }
    private static ListTag buildings(){
        var rows=new ListTag();for(var e:HOSTS.entrySet()){var p=e.getValue();var row=new CompoundTag();row.putString("key",e.getKey());row.putString("buildingKey",ReignOfNetherRegistries.BUILDING.getKey(p.getBuilding()).toString());
            row.putIntArray("origin",new int[]{p.originPos.getX(),p.originPos.getY(),p.originPos.getZ()});row.putString("owner",p.ownerName);row.putBoolean("built",p.isBuilt);row.putLong("capture",p.getCaptureRevision());
            var a=BuildingAbility.forBuilding(p);if(a!=null){row.putString("ability",a.path());row.put("abilityState",state(p).write());}row.put("storage",p.getDataStorage().write());rows.add(row);}return rows;
    }
    private static ListTag bodies(){
        var rows=new ListTag();for(var e:BODIES.entrySet()){var b=e.getValue();var row=new CompoundTag();row.putString("key",e.getKey());row.putUUID("uuid",b.getUUID());row.putString("type",BuiltInRegistries.ENTITY_TYPE.getKey(b.getType()).toString());row.putString("owner",((Unit)b).getOwnerName());
            row.putFloat("health",b.getHealth());row.put("raw",b.saveWithoutId(new CompoundTag()));rows.add(row);}return rows;
    }
    private static ListTag list(Map<String,CompoundTag> values){var rows=new ListTag();for(var t:values.values())rows.add(t.copy());return rows;}
    public static final class BuildingAbilityRestartStopWitness {
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void buildingAbilityNormalStop(ServerStoppingEvent e){
            var level=e.getServer().overworld();long now=level.getGameTime();var out=new CompoundTag();out.putInt("format",2);out.putLong("jvmStart",jvm());out.putLong("stopTime",now);out.putBoolean("normalStopWitness",true);
            out.putString("owner",OWNER);out.putString("enemy",ENEMY);out.putUUID("epoch",account.accountEpoch);out.put("account",accountTag());ticketWitness(level);out.putLongArray("chunks",new ArrayList<>(CHUNKS));out.putLongArray("preexistingChunks",new ArrayList<>(PREEXISTING_CHUNKS));out.putLongArray("ownedChunks",new ArrayList<>(OWNED_CHUNKS));out.putIntArray("nativeArenaChunk",nativeArenaChunk);
            if(!verified){
                require(now<castAt+120,"all original shortest effects must STILL be live at normal stop, never expired substitute");
                var active=new CompoundTag();for(String key:List.of("root","garden","hall","dais","watch")){var p=HOSTS.get(key);int remaining=BuildingAbilityEffects.remaining(p);int exact=(int)(castAt+BuildingAbility.forBuilding(p).durationTicks()-now);require(remaining==exact&&remaining>0,"original exact active expiry at stop "+key);active.putInt(key,remaining);}
                near(BODIES.get("root").getAttributeValue(Attributes.MOVEMENT_SPEED),rootBase*1.15,"root active immediately before normal stop cleanup");near(BODIES.get("hall").getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get()),hallBase*1.10,"hall active immediately before normal stop cleanup");
                require(DaisWardEffects.active(BODIES.get("dais"))&&TowerObservation.marked(BODIES.get("enemy")),"ward/mark active before normal source detach and peer disconnect");
                require(BODIES.get("garden").getHealth()>gardenBefore&&BODIES.get("garden").getHealth()<gardenBefore+6,"actual partial garden recovery, not finished budget");
                naturalIncomeOnly(afterCasts,accountTag(),now-castAt,"prepare stop");
                out.putLong("castAt",castAt);out.putLong("shortestExpiry",castAt+120);out.put("activeRemaining",active);out.put("payments",PAYMENTS.copy());out.put("afterCasts",afterCasts.copy());out.put("codexRows",codexOriginal.copy());out.putUUID("originalSession",originalSession);out.putDouble("rootBase",rootBase);out.putDouble("hallBase",hallBase);out.putFloat("gardenBefore",gardenBefore);
                out.putString("prerequisiteFixture","completed real WIND_V4 geometry and old ResonanceResearch.ID; neither claimed paid by this new test");
                out.put("buildings",buildings());out.put("entities",bodies());write(out,MANIFEST);
                System.out.println("BUILDING_ABILITIES_RESTART_STOP_WITNESS paid5=75S+30F codexFree0CD activeOriginalDeadlines="+active+" stop="+now);
            }else{
                naturalIncomeOnly(expectations.getCompound("account"),accountTag(),now-expectations.getLong("stopTime"),"verify stop");
                out.putLong("prepareJvmStart",expectations.getLong("jvmStart"));out.put("joinReads",list(JOINS));out.put("publishedReads",list(PUBLISHED));out.put("buildings",buildings());out.put("entities",bodies());out.putUUID("newSession",originalSession);out.putBoolean("oldSessionRejected",true);out.putBoolean("newSessionCooldownRejected",true);out.putBoolean("codexNewQueryFree",true);write(out,VERIFIED);
                System.out.println("BUILDING_ABILITIES_RESTART_VERIFY_STOP_WITNESS originalUUIDs6 originalPaidCDs5 codexZero noShortReplay");
            }
            // Unregister the embedded actor only after the before-cleanup witness. No building,
            // entity, owner, research, ledger or persistent fixture is manually removed here.
            if(peer!=null){peer.close();peer=null;}
        }
    }
    private static CompoundTag entityExpected(UUID uuid){if(expectations==null)return null;for(Tag tag:expectations.getList("entities",Tag.TAG_COMPOUND)){var row=(CompoundTag)tag;if(row.getUUID("uuid").equals(uuid))return row;}return null;}
    private static BuildingPlacement originalHost(ServerLevel level,CompoundTag row){
        int[] p=row.getIntArray("origin");BuildingPlacement found=null;
        for(var b:BuildingServerEvents.getBuildings())if(b.level==level&&b.originPos.equals(new BlockPos(p[0],p[1],p[2]))){require(found==null,"duplicate restored original origin");found=b;}
        return found;
    }
    private static void originalBacking(BuildingPlacement host,CompoundTag expected){
        var actual=new HashMap<String,CompoundTag>();for(Tag t:host.getDataStorage().write()){var slot=(CompoundTag)t;actual.put(slot.getString("key"),slot.getCompound("data"));}
        for(Tag t:expected.getList("storage",Tag.TAG_COMPOUND)){var slot=(CompoundTag)t;String key=slot.getString("key");var old=slot.getCompound("data");
            if(Set.of("dreamseekers:function_state","dreamseekers:watchtower_model","dreamseekers:garden_decoration_schema").contains(key))require(old.equals(actual.get(key)),"same original functional/backing identity "+key);
            if(key.equals("dreamseekers:world_tree")){var now=actual.get(key);require(now!=null,"original tree backing restored");for(String field:List.of("schema","instance","model","phase","ascended","geometryHighest"))require(Objects.equals(old.get(field),now.get(field)),"same original completed tree backing "+field);}
        }
    }
    private static boolean originalSourcesReady(ServerLevel level){
        var restored=ExtendedResourcesSync.account(OWNER);if(restored==null||!restored.extendedStateSupported()||!restored.accountEpoch.equals(expectations.getUUID("epoch"))
            ||!DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(OWNER))||!DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(ENEMY)))return false;
        for(Tag tag:expectations.getList("buildings",Tag.TAG_COMPOUND)){var row=(CompoundTag)tag;var p=originalHost(level,row);if(p==null||!p.isBuilt||p.isDestroyedServerside||!p.ownerName.equals(row.getString("owner"))||!p.level.hasChunksAt(p.minCorner,p.maxCorner)||p.shouldBeDestroyed()||p.getHealth()<=0)return false;
            require(ReignOfNetherRegistries.BUILDING.getKey(p.getBuilding()).toString().equals(row.getString("buildingKey")),"exact original building definition");originalBacking(p,row);
            if(row.contains("ability")){require(state(p).write().equals(row.getCompound("abilityState")),"same original saved instance/revision/absolute CD before public effect read");if(!BuildingAbilitySource.physical(p)||!BuildingAbilityService.prerequisite(p,BuildingAbility.forBuilding(p)).equals("ready"))return false;}
        }
        for(Tag tag:expectations.getList("entities",Tag.TAG_COMPOUND)){var row=(CompoundTag)tag;var e=level.getEntity(row.getUUID("uuid"));if(!(e instanceof Mob b)||!b.isAlive()||b.isRemoved()||!b.isAddedToWorld()||b.tickCount<1||level.getEntity(b.getId())!=b||!level.isPositionEntityTicking(b.blockPosition())||UnitServerEvents.getAllUnits().stream().filter(v->v==b).count()!=1)return false;
            require(b instanceof Unit u&&u.getOwnerName().equals(row.getString("owner"))&&BuiltInRegistries.ENTITY_TYPE.getKey(b.getType()).toString().equals(row.getString("type")),"exact original body owner/type before public effect read");}
        return true;
    }
    private static void rawHasNoTransient(CompoundTag raw){
        for(Tag t:raw.getList("Attributes",Tag.TAG_COMPOUND)){var attr=(CompoundTag)t;for(Tag m:attr.getList("Modifiers",Tag.TAG_COMPOUND)){var modifier=(CompoundTag)m;for(var kind:BuffKind.values())require(!modifier.getUUID("UUID").equals(kind.modifierId),"no saved transient category modifier on raw original Join");}}
    }
    /** Join never probes live-effect getters: only the untouched save envelope and identity.
     * First published checks wait for ALL original source buildings, owner epoch and original
     * target UUIDs to be genuinely available; there is no manual re-add/index/replacement.
     */
    @Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
    public static final class BuildingAbilityRestartLoadObserver {
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void buildingAbilityOriginalJoin(EntityJoinLevelEvent e){
            if(!"verify".equals(System.getProperty(PHASE))||e.getLevel().isClientSide()||!(e.getEntity() instanceof Mob b)||!MANIFEST.isFile())return;
            try{if(expectations==null)expectations=NbtIo.readCompressed(MANIFEST);}catch(Exception failure){throw new RuntimeException(failure);}
            var expected=entityExpected(b.getUUID());if(expected==null)return;require(!e.isCanceled(),"original persistent body may not be canceled");String key=expected.getString("key");require(!JOINS.containsKey(key),"no duplicate original join");
            var raw=b.saveWithoutId(new CompoundTag());rawHasNoTransient(raw);var row=new CompoundTag();row.putString("key",key);row.putUUID("uuid",b.getUUID());row.putLong("at",e.getLevel().getGameTime());row.putFloat("health",raw.getFloat("Health"));row.putBoolean("publicEffectsProbed",false);row.put("raw",raw);JOINS.put(key,row);b.setNoAi(true);
        }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public static void buildingAbilityOriginalPublished(LivingEvent.LivingTickEvent e){
            if(!"verify".equals(System.getProperty(PHASE))||expectations==null||!(e.getEntity() instanceof Mob b)||!(b.level() instanceof ServerLevel level))return;
            var expected=entityExpected(b.getUUID());if(expected==null)return;String key=expected.getString("key");if(PUBLISHED.containsKey(key)||!originalSourcesReady(level))return;
            long now=level.getGameTime();require(now<expectations.getLong("shortestExpiry"),"first valid published observation MUST precede original120tick expiry");require(JOINS.containsKey(key),"first publication follows actual original raw Join");
            require(!BuildingAbilityEffects.hasEffects(b)&&!DaisWardEffects.active(b)&&!TowerObservation.marked(b),"no restarted transient on original body "+key);
            near(SkillEffects.bonus(b,BuffKind.MOVEMENT_SPEED),0,"no root contribution replay");near(SkillEffects.bonus(b,BuffKind.ATTACK_DAMAGE),0,"no hall contribution replay");
            for(Tag tag:expectations.getList("buildings",Tag.TAG_COMPOUND)){var saved=(CompoundTag)tag;if(saved.contains("ability"))require(BuildingAbilityEffects.remaining(originalHost(level,saved))==0,"no source short-state replay");}
            near(b.getHealth(),expected.getFloat("health"),"no pre-publication heal/hit replay");
            if(key.equals("root"))near(b.getAttributeValue(Attributes.MOVEMENT_SPEED),expectations.getDouble("rootBase"),"actual original movement consumer unbuffed before expiry");
            if(key.equals("hall"))near(b.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get()),expectations.getDouble("hallBase"),"actual original attack consumer unbuffed before expiry");
            var row=new CompoundTag();row.putString("key",key);row.putUUID("uuid",b.getUUID());row.putLong("at",now);row.putBoolean("allOriginalSourcesAndBodiesPublished",true);row.putUUID("epoch",ExtendedResourcesSync.account(OWNER).accountEpoch);row.putBoolean("effectsAbsent",true);row.putFloat("health",b.getHealth());row.putInt("naturalTicks",b.tickCount);PUBLISHED.put(key,row);
            System.out.println("BUILDING_ABILITIES_FIRST_VALID_PUBLISHED "+row+" originalShortestExpiry="+expectations.getLong("shortestExpiry"));
        }
    }
    private static void verify(GameTestHelper h)throws Exception{
        geometry(h);require(MANIFEST.isFile()&&!VERIFIED.exists(),"prepared file exists; never reuse previous verification result");expectations=NbtIo.readCompressed(MANIFEST);
        require(expectations.getInt("format")==2&&expectations.getBoolean("normalStopWitness")&&expectations.getLong("jvmStart")!=jvm(),"genuine distinct JVM and pre-cleanup normal-stop witness");require(expectations.getLong("stopTime")<expectations.getLong("shortestExpiry"),"original prepare stopped with all five paid short effects alive");
        nativeArenaChunk=expectations.getIntArray("nativeArenaChunk");var arenaChunk=new ChunkPos(h.absolutePos(BlockPos.ZERO));require(nativeArenaChunk.length==2&&nativeArenaChunk[0]==arenaChunk.x&&nativeArenaChunk[1]==arenaChunk.z,"same native arena origin, not a replacement forced scope");
        for(long chunk:expectations.getLongArray("chunks"))require(CHUNKS.add(chunk),"unique complete original evidence chunks");
        for(long chunk:expectations.getLongArray("preexistingChunks"))require(PREEXISTING_CHUNKS.add(chunk),"unique original native ticket intersection");
        for(long chunk:expectations.getLongArray("ownedChunks"))require(OWNED_CHUNKS.add(chunk),"unique original fixture-owned tickets");
        ticketWitness(h.getLevel()); // read-only: no explicit forcing, loading or re-publication
        h.startSequence().thenWaitUntil(()->{h.assertTrue(JOINS.size()==6&&PUBLISHED.size()==6,"all six actual originals first published before old shortest expiry");for(Tag t:expectations.getList("entities",Tag.TAG_COMPOUND)){var row=(CompoundTag)t;var body=h.getLevel().getEntity(row.getUUID("uuid"));h.assertTrue(body instanceof Mob,"same original entity remains published");ready(h,(Mob)body);}})
          .thenExecute(()->{
              var level=h.getLevel();account=ExtendedResourcesSync.account(OWNER);require(account!=null&&account.accountEpoch.equals(expectations.getUUID("epoch")),"original account epoch restored");
              for(Tag t:expectations.getList("buildings",Tag.TAG_COMPOUND)){var row=(CompoundTag)t;var host=originalHost(level,row);require(host!=null,"actual original host");HOSTS.put(row.getString("key"),host);}
              for(Tag t:expectations.getList("entities",Tag.TAG_COMPOUND)){var row=(CompoundTag)t;var body=(Mob)level.getEntity(row.getUUID("uuid"));BODIES.put(row.getString("key"),body);ready(h,body);near(body.getHealth(),row.getFloat("health"),"original saved injury no replay");}
              naturalIncomeOnly(expectations.getCompound("account"),accountTag(),level.getGameTime()-expectations.getLong("stopTime"),"first published ledger");
              peer=new PassiveIncomePeer(level,OWNER);require(peer.drain().stream().noneMatch(o->o instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket p&&(p.getIdentifier().equals(DreamseekersMod.id("building_abilities"))||p.getIdentifier().equals(DreamseekersMod.id("tower_observations")))),"fresh embedded connection has no replayed private observations");var token=EconomyActionGate.query(peer.actor,peer.connection);require(token!=null&&!token.session().equals(expectations.getUUID("originalSession")),"new legitimate session cannot inherit old private stream");originalSession=token.session();
              var baseline=accountTag();queryCodex();require(accountTag().equals(baseline),"fresh session query remains free before reject acknowledgements");for(String key:List.of("root","garden","hall","dais","watch")){
                  var host=HOSTS.get(key);var paid=state(host).write();require(state(host).cooldown(BuildingAbility.forBuilding(host),level.getGameTime())>0,"original absolute paid CD still pending");
                  var stale=wire(request(host,BuildingAbilityPackets.Action.CAST,expectations.getUUID("originalSession"),1));
                  envelope(stale,originalSession,1);require(stale.ability()==null&&stale.ack()!=null&&stale.status().equals("stale_session")&&stale.marks().isEmpty(),"old pre-restart session cannot repay or republish");
                  var denied=wire(request(host,BuildingAbilityPackets.Action.CAST,originalSession,++sequence));envelope(denied,originalSession,sequence);require(denied.ability()==null&&denied.ack()!=null&&denied.status().equals("cooldown")&&denied.marks().isEmpty(),"new valid session respects same persisted cooldown "+key);
                  require(state(host).write().equals(paid)&&accountTag().equals(baseline)&&BuildingAbilityEffects.remaining(host)==0,"reconnect no refund, refresh, state replay or cooldown change");
              }
              require(accountTag().equals(baseline),"new-session free Codex and rejection acknowledgements never mutate ledger");
          }).thenExecuteAfter(30,()->{
              for(Tag t:expectations.getList("entities",Tag.TAG_COMPOUND)){var row=(CompoundTag)t;var body=BODIES.get(row.getString("key"));ready(h,body);near(body.getHealth(),row.getFloat("health"),"no delayed garden pulse or damage replay");require(!BuildingAbilityEffects.hasEffects(body),"no delayed short replay");}
              naturalIncomeOnly(expectations.getCompound("account"),accountTag(),h.getLevel().getGameTime()-expectations.getLong("stopTime"),"completed verify ledger");verified=true;MinecraftForge.EVENT_BUS.register(new BuildingAbilityRestartStopWitness());System.out.println("BUILDING_ABILITIES_RESTART_VERIFY_PASS_WAIT_NORMAL_FULL_SAVE");h.succeed();
          });
    }
}
