// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import java.util.*;
/** Dry sealed exact-sized room, genuine published actors; timelines are built before simulation. */
final class ShadowGolemFixture {
    static final String PREFIX="GolemGT_";
    static final List<Entity> ENTITIES=new ArrayList<>();
    static final Set<net.minecraft.world.level.ChunkPos> TICKETS=new HashSet<>();
    final GameTestHelper h;
    ShadowGolemFixture(GameTestHelper h){this.h=h;for(int x=-1;x<=32;x++)for(int z=-1;z<=32;z++){
        set(x,0,z,Blocks.STONE);set(x,12,z,Blocks.STONE);
        if(x==-1||x==32||z==-1||z==32)for(int y=1;y<12;y++)set(x,y,z,Blocks.STONE);
    }
    var p=pos(0,0,0);
    for(int x=p.getX()>>4;x<=(p.getX()+31)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+31)>>4;z++){var c=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(x,z,true);TICKETS.add(c);}}
    h.getLevel().getEntitiesOfClass(FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+32,h.getLevel().getMaxBuildHeight(),p.getZ()+32)).forEach(Entity::discard);
    for(int x=0;x<32;x++)for(int z=0;z<32;z++)for(int y=1;y<12;y++)set(x,y,z,Blocks.AIR);
    h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p.getX(),p.getY(),p.getZ(),p.getX()+32,p.getY()+12,p.getZ()+32)).forEach(Entity::discard);
    }
    BlockPos pos(int x,int y,int z){return h.absolutePos(new BlockPos(x,y,z));}
    void set(int x,int y,int z,Block b){h.getLevel().setBlock(pos(x,y,z),b.defaultBlockState(),2);}
    static String account(String suffix){String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));}return name;}
    <T extends Entity>T place(T e,int x,int z){var p=pos(x,1,z);e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);ENTITIES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"actual entity add");return e;}
    ShadowGolemUnit unit(String owner,int x,int z){var e=DreamseekersMod.SHADOW_GOLEM_UNIT.get().create(h.getLevel());e.setOwnerName(account(owner));return place(e,x,z);}
    GoldenFoxUnit fox(String owner,int x,int z){var e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());e.setOwnerName(account(owner));e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);e.setHealth(500);return place(e,x,z);}
    boolean ready(Entity... entities){return Arrays.stream(entities).allMatch(e->e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e);}
    void order(ShadowGolemUnit e,UnitAction action,BlockPos p){UnitServerEvents.addActionItem(e.getOwnerName(),action,-1,new int[]{e.getId()},p,BlockPos.ZERO);}
    void target(ShadowGolemUnit e,UnitAction action,Entity target){UnitServerEvents.addActionItem(e.getOwnerName(),action,target.getId(),new int[]{e.getId()},BlockPos.ZERO,BlockPos.ZERO);}
    boolean near(ShadowGolemUnit e,BlockPos p){return e.distanceToSqr(p.getCenter())<4&&e.getNavigation().isDone()&&e.isIdle();}
    String state(ShadowGolemUnit e){return "tick="+e.tickCount+" pos="+e.position()+" target="+e.getMoveGoal().getMoveTarget()+" nav="+e.getNavigation().isDone()+" idle="+e.isIdle();}
    static void cleanup(ServerLevel level){var owners=PlayerServerEvents.rtsPlayers.stream().map(p->p.name).filter(p->p.startsWith(PREFIX)).toList();for(String a:owners)for(String b:owners)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));for(Entity e:ENTITIES)if(!e.isRemoved())e.discard();ENTITIES.clear();for(var c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();}
    static final class Timeline {
        final GameTestHelper h;final TreeMap<Integer,List<Runnable>> events=new TreeMap<>();
        Timeline(GameTestHelper h){this.h=h;}
        Timeline at(int tick,Runnable callback){events.computeIfAbsent(tick,k->new ArrayList<>()).add(callback);return this;}
        void start(Entity actor,java.util.function.BooleanSupplier ready){start(()->actor,ready);}
        void start(java.util.function.Supplier<? extends Entity> actor,java.util.function.BooleanSupplier ready){int[] start={0};var seq=h.startSequence().thenWaitUntil(()->h.assertTrue(ready.getAsBoolean(),"published fixtures before commands")).thenExecute(()->start[0]=actor.get().tickCount);for(var e:events.entrySet())seq.thenWaitUntil(()->h.assertTrue(actor.get().tickCount>=start[0]+e.getKey(),"wait real actor ticks "+e.getKey())).thenExecute(()->e.getValue().forEach(Runnable::run));seq.thenSucceed();}
        void start(java.util.function.BooleanSupplier ready){var seq=h.startSequence().thenWaitUntil(()->h.assertTrue(ready.getAsBoolean(),"published fixtures before commands"));int last=0;for(var e:events.entrySet()){if(e.getKey()>last)seq.thenIdle(e.getKey()-last);seq.thenExecute(()->e.getValue().forEach(Runnable::run));last=e.getKey();}seq.thenSucceed();}
    }
}
