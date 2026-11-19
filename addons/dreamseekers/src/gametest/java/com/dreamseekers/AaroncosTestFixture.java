// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.aaroncos.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.*;
import java.util.*;
/** Test-only real body world, published actors and upfront timelines. No runtime movement substitutions. */
final class AaroncosTestFixture {
    static final List<Entity> ENTITIES=new ArrayList<>();static final Set<ChunkPos> TICKETS=new HashSet<>();
    static final Set<String> OWNERS=new HashSet<>();static final List<FakePlayer> PLAYERS=new ArrayList<>();
    final GameTestHelper h;final String prefix;
    AaroncosTestFixture(GameTestHelper h,String key){this.h=h;prefix="Aaron_"+key+"_";room();}
    BlockPos p(int x,int y,int z){return h.absolutePos(new BlockPos(x,y,z));}
    void set(int x,int y,int z,Block b){h.getLevel().setBlock(p(x,y,z),b.defaultBlockState(),2);}
    void room(){var base=p(0,0,0);
        for(int cx=base.getX()>>4;cx<=(base.getX()+55)>>4;cx++)for(int cz=base.getZ()>>4;cz<=(base.getZ()+43)>>4;cz++){
            var c=new ChunkPos(cx,cz);if(!h.getLevel().getForcedChunks().contains(c.toLong())){TICKETS.add(c);h.getLevel().setChunkForced(cx,cz,true);}}
        for(int x=-1;x<=56;x++)for(int z=-1;z<=44;z++){set(x,0,z,Blocks.STONE);set(x,14,z,Blocks.STONE);if(x==-1||x==56||z==-1||z==44)for(int y=1;y<14;y++)set(x,y,z,Blocks.STONE);}
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(base.getX()-1,base.getY()+1,base.getZ()-1,base.getX()+57,h.getLevel().getMaxBuildHeight(),base.getZ()+45)).forEach(Entity::discard);
        for(int x=0;x<56;x++)for(int z=0;z<44;z++)for(int y=1;y<14;y++)set(x,y,z,Blocks.AIR);
    }
    String owner(String key){String n=prefix+key;if(OWNERS.add(n)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(n,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(n,1000,1000,1000,1000));}return n;}
    Mob hand(boolean right,String owner,int x,int y,int z){Mob m=right?DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel()):DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(h.getLevel());((ControlledAaroncosHand)m).setOwnerName(owner(owner));place(m,x,y,z);return m;}
    GoldenFoxUnit fox(String owner,int x,int y,int z){var e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());e.setOwnerName(owner(owner));e.setNoAi(true);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);e.setHealth(1000);place(e,x,y,z);return e;}
    void place(Entity e,int x,int y,int z){var q=p(x,y,z);e.moveTo(q.getX()+.5,q.getY(),q.getZ()+.5);ENTITIES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"real server added actor");}
    FakePlayer player(String key){String n=owner(key);var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(n.getBytes(java.nio.charset.StandardCharsets.UTF_8)),n));PLAYERS.add(p);return p;}
    static ControlledAaroncosHand unit(Mob m){return (ControlledAaroncosHand)m;}
    boolean published(Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}
    GameTestSequence ready(Entity... es){return h.startSequence().thenWaitUntil(()->h.assertTrue(Arrays.stream(es).allMatch(this::published),"real ID/UUID publication and simulation readiness"));}
    void order(Mob m,UnitAction a,BlockPos p){UnitServerEvents.addActionItem(unit(m).getOwnerName(),a,-1,new int[]{m.getId()},p,BlockPos.ZERO);}
    void attack(Mob m,LivingEntity e){new UnitActionItem(unit(m).getOwnerName(),UnitAction.ATTACK,e.getId(),new int[]{m.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());}
    boolean arrived(Mob m,BlockPos clicked){return unit(m).getMoveGoal().getMoveTarget()==null&&m.getNavigation().isDone()&&m.position().distanceToSqr(new Vec3(clicked.getX()+.5,clicked.getY()+1,clicked.getZ()+.5))<1.5;}
    String describe(Mob m){var n=m.getNavigation();var path=n.getPath();return m.getType()+" pos="+m.position()+" move="+unit(m).getMoveGoal().getMoveTarget()+" navTarget="+n.getTargetPos()+" done="+n.isDone()+" path="+(path==null?"null":path.getNextNodeIndex()+"/"+path.getNodeCount()+" end="+path.getEndNode()+" reach="+path.canReach())+" delta="+m.getDeltaMovement();}
    static void cleanup(ServerLevel l){for(String a:OWNERS)for(String b:OWNERS)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);PlayerServerEvents.rtsPlayers.removeIf(p->OWNERS.contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->OWNERS.contains(r.ownerName));UnitServerEvents.getUnitActionSlowQueue().removeIf(q->Arrays.stream(q.getUnitIds()).anyMatch(id->ENTITIES.stream().anyMatch(e->e.getId()==id)));for(var e:ENTITIES)if(!e.isRemoved())e.discard();ENTITIES.clear();OWNERS.clear();for(var c:TICKETS)l.setChunkForced(c.x,c.z,false);TICKETS.clear();for(var p:PLAYERS)p.getInventory().clearContent();PLAYERS.clear();}
}
