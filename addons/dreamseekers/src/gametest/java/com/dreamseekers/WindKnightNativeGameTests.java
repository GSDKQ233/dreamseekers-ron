// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.unit.WindKnightUnit;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import net.pasterdream.entity.WindKnightEntity;
import net.pasterdream.procedures.WindKnightPr0Procedure;
import java.util.*;

/** Real native delayed procedure and native help goal retain wild positive controls. No socket/GUI claim. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WindKnightNativeGameTests {
    private static final String BATCH="dreamseekers_wind_knight_native",PREFIX="WindNative_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final Set<net.minecraft.world.level.ChunkPos> TICKETS=new HashSet<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity e:FIXTURES)if(!e.isRemoved())e.discard();FIXTURES.clear();
        for(var c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=300)
    public static void actualNativeDelayedAoeStillHitsWildSideButPaidBaseTicksCannotScheduleIt(GameTestHelper h) {
        floor(h);var wild=wild(h,5,6);var paid=paid(h,"AOE",18,6);
        var wildPlayer=player(h,"WildWitness",6,6);var paidPlayer=player(h,"PaidWitness",19,6);
        var wildVictim=cow(h,6,8);var paidVictim=cow(h,19,8);
        ready(h,wild,paid,wildPlayer,paidPlayer,wildVictim,paidVictim).thenExecute(()->{
            h.assertTrue(!h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,wild.getBoundingBox().inflate(5)).isEmpty(),"real published native Player query is positive");
            wild.getPersistentData().putDouble("time",180);paid.getPersistentData().putDouble("time",180);
            WindKnightPr0Procedure.execute(h.getLevel(),wild.getX(),wild.getY(),wild.getZ(),wild);
            WindKnightPr0Procedure.execute(h.getLevel(),paid.getX(),paid.getY(),paid.getZ(),paid);
            h.assertTrue(wild.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&wild.getPersistentData().getDouble("time")==0,"actual wild procedure entered native delayed attack branch");
            h.assertTrue(!paid.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&paid.getPersistentData().getDouble("time")==180,"paid procedure isolated before any immediate effect or delayed schedule");
            paid.setOwnerName("");WindKnightPr0Procedure.execute(h.getLevel(),paid.getX(),paid.getY(),paid.getZ(),paid);
        }).thenIdle(35).thenExecute(()->{
            h.assertTrue(wildVictim.getHealth()<100&&paidVictim.getHealth()==100,"actual25tick native damage reaches wild-side actual Cow only (FakePlayers only trigger the Player query); paid/blank-owner native baseTick never arms it");
            h.assertTrue(paid.isAlive()&&!paid.hasEffect(MobEffects.MOVEMENT_SPEED)&&!paid.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&paid.getPersistentData().getDouble("time")==180,"paid identity guard remains after owner removal and actual elapsed callbacks");
            System.out.println("DREAMSEEKERS_WIND_NATIVE_DELAYED_AOE_WILD_POSITIVE_PAID_NEGATIVE_PASS wildHP="+wildVictim.getHealth());
        }).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=160)
    public static void nativeHelpGoalAlertsWildSiblingButCannotRecruitPaidSubclassAgainstPlayer(GameTestHelper h) {
        floor(h);var caller=wild(h,8,10);var helper=wild(h,11,10);var paid=paid(h,"Help",14,10);var player=player(h,"Aggressor",7,10);
        ready(h,caller,helper,paid,player).thenExecute(()->{
            h.assertTrue(caller.hurt(caller.damageSources().playerAttack(player),1),"real player damage establishes native revenge memory");
            var goal=(HurtByTargetGoal)caller.targetSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(g->g instanceof HurtByTargetGoal).findFirst().orElseThrow();
            h.assertTrue(goal.canUse(),"native HurtByTargetGoal can use genuine revenge damage");goal.start();
            h.assertTrue(helper.getTarget()==player,"real native call-for-help still recruits wild same-class sibling");
            h.assertTrue(paid.getTarget()==null&&!paid.doHurtTarget(player),"paid subclass cannot be recruited into unowned/player damage by parent-class native alert");
            caller.setTarget(null);helper.setTarget(null);
        }).thenIdle(30).thenExecute(()->h.assertTrue(paid.getTarget()==null,"no later native goal reinstates paid player pursuit")).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=160)
    public static void realDimensionsNbtAndPeacefulRetainPaidIdentityWithoutNativeTimer(GameTestHelper h) {
        floor(h);var paid=paid(h,"Restore",8,8);var wild=wild(h,19,19);paid.setNoAi(false);paid.setScenarioRoleIndex(94);paid.getPersistentData().putDouble("time",180);
        ready(h,paid,wild).thenExecute(()->{
            CompoundTag tag=new CompoundTag();paid.saveWithoutId(tag);tag.putBoolean("NoAI",true);
            var restored=DreamseekersMod.WIND_KNIGHT_UNIT.get().create(h.getLevel());restored.load(tag);
            h.assertTrue(restored.getUUID().equals(paid.getUUID())&&restored.getOwnerName().equals(paid.getOwnerName())&&restored.getScenarioRoleIndex()==94&&!restored.isNoAi(),"ordinary native NBT preserves identity but cannot disable paid AI");
            WindKnightPr0Procedure.execute(h.getLevel(),restored.getX(),restored.getY(),restored.getZ(),restored);
            h.assertTrue(restored.getPersistentData().getDouble("time")==180,"restored instance never reopens native timer even without tracking publication");
            var difficulty=h.getLevel().getDifficulty();
            try {h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);paid.checkDespawn();wild.checkDespawn();h.assertTrue(paid.isAlive()&&!paid.isRemoved()&&wild.isRemoved(),"peaceful persists paid monster only, wild ordinary despawn remains");}
            finally{h.getLevel().getServer().setDifficulty(difficulty,true);}
        }).thenIdle(50).thenExecute(()->{
            h.assertTrue(Math.abs(paid.getBbWidth()-1.6)<.001&&Math.abs(paid.getBbHeight()-3.2)<.001&&h.getLevel().noCollision(paid),"native refreshDimensions retains actual1.6x3.2 instead of double-scaling registered body");
            h.assertTrue(paid.getPersistentData().getDouble("time")==180&&!paid.isFlyingUnit()&&!paid.isNoGravity(),"real ticks remain ground movement without native periodic timer");
        }).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=160)
    public static void wildDeathStillDropsNativeCrystalWhilePaidDeathDropsNothing(GameTestHelper h) {
        floor(h);var paid=paid(h,"Death",6,6);var wild=wild(h,18,18);
        ready(h,paid,wild).thenExecute(()->{paid.hurt(paid.damageSources().genericKill(),999);wild.hurt(wild.damageSources().genericKill(),999);})
          .thenIdle(35).thenExecute(()->{
            h.assertTrue(paid.isRemoved()&&wild.isRemoved()&&paid.deathTime>=20&&wild.deathTime>=20,"both actual native20tick deaths completed");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(BlockPos.ZERO),h.absolutePos(new BlockPos(24,10,24))));
            h.assertTrue(drops.size()==1&&net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(drops.get(0).getItem().getItem()).equals(DreamseekersMod.paster("pulse_windrunner_crystal"))&&drops.get(0).getItem().getCount()==1,"wild native crystal reward preserved; paid death contributes no extra drop");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ExperienceOrb.class,new AABB(h.absolutePos(BlockPos.ZERO),h.absolutePos(new BlockPos(12,10,12)))).isEmpty(),"paid corpse area yields no XP");
            drops.forEach(Entity::discard);
          }).thenSucceed();
    }
    private static GameTestSequence ready(GameTestHelper h,Entity...entities){return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue((e instanceof net.minecraft.world.entity.player.Player||e.tickCount>2)&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"published ID/UUID and genuine ticking native fixtures");});}
    private static WindKnightUnit paid(GameTestHelper h,String suffix,int x,int z){String owner=PREFIX+suffix;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(owner,500,500,500));var e=DreamseekersMod.WIND_KNIGHT_UNIT.get().create(h.getLevel());e.setOwnerName(owner);e.setNoAi(true);place(h,e,x,z);return e;}
    private static WindKnightEntity wild(GameTestHelper h,int x,int z){var e=net.pasterdream.init.PasterdreamModEntities.WIND_KNIGHT.get().create(h.getLevel());e.setNoAi(true);place(h,e,x,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"real entity enters level");}
    private static net.minecraft.world.entity.animal.Cow cow(GameTestHelper h,int x,int z){var e=EntityType.COW.create(h.getLevel());e.setNoAi(true);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);e.setHealth(100);place(h,e,x,z);return e;}
    private static FakePlayer player(GameTestHelper h,String suffix,int x,int z){var p=new FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),PREFIX+suffix));p.connection=new RecordingHandler(p);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);p.setHealth(100);BlockPos pos=h.absolutePos(new BlockPos(x,1,z));p.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);FIXTURES.add(p);h.getLevel().addNewPlayer(p);return p;}
    private static final class RecordingHandler extends ServerGamePacketListenerImpl {
        RecordingHandler(ServerPlayer player){super(player.serverLevel().getServer(),new RecordingConnection(),player);}
        @Override public void tick() { }
        @Override public void send(net.minecraft.network.protocol.Packet<?> p) { }
        @Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener) { }
    }
    private static final class RecordingConnection extends net.minecraft.network.Connection {
        RecordingConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}
        @Override public void tick() { }
        @Override public void send(net.minecraft.network.protocol.Packet<?> p) { }
        @Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener) { }
    }
    private static void floor(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=p.getX()>>4;x<=(p.getX()+23)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+23)>>4;z++){var c=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(x,z,true);TICKETS.add(c);}}for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<10;y++)h.getLevel().setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}}
}
