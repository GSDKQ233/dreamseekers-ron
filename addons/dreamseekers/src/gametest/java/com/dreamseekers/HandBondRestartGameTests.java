package com.dreamseekers;

import com.dreamseekers.aaroncos.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Two independent ordinary JVMs. The manifest only records observations; it never recreates bonds,
 * entities, balances, cooldowns or research. Research unlock is an explicit prerequisite fixture. */
@GameTestHolder("dreamseekers_hand_bond_restart") @PrefixGameTestTemplate(false)
public final class HandBondRestartGameTests {
    static final File FILE=new File("api61-hand-bond-restart-expectations.nbt");
    static HandBondGameTests.F active,pending;
    static UUID activeLeft,activeRight,deadLeft,survivor;
    @GameTest(templateNamespace="dreamseekers_hand_bond_restart",template="ore_mine_test_arena",batch="dreamseekers_hand_bond_restart",timeoutTicks=2300)
    public static void actualPaidPairAndCombatPendingCrossTwoNormalJvms(GameTestHelper h)throws Exception{
        switch(System.getProperty("dreamseekers.hand.bond.restart.phase","")){
            case "prepare" -> prepare(h);case "verify" -> verify(h);default -> throw new IllegalArgumentException("prepare|verify required");
        }
    }
    private static void player(HandBondGameTests.F f,int id){PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(f.owner));PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(f.owner,DreamseekersMod.DREAMSEEKERS,id,0,false,0L,0L));}
    private static void prepare(GameTestHelper h){
        h.assertTrue(!FILE.exists(),"fresh isolated prepare manifest");
        active=new HandBondGameTests.F(h,"restart_active",true);pending=new HandBondGameTests.F(h,"restart_pending",true);player(active,99101);player(pending,99102);
        var al=active.left();var ar=active.right();var pl=pending.left();var pr=pending.right();var enemy=pending.enemy();
        activeLeft=al.getUUID();activeRight=ar.getUUID();deadLeft=pl.getUUID();survivor=pr.getUUID();
        h.runAtTickTime(10,()->{active.paired(al,ar);pending.paired(pl,pr);pending.kill(pl,enemy);pending.account.extra.put(DreamPassiveEconomy.SHADOW,99);
            h.assertTrue(pending.bond(pr)!=null&&pending.bond(pr).missing==1,"actual hostile death creates pending replacement");MinecraftForge.EVENT_BUS.register(new StopWitness());});
        h.runAtTickTime(40,()->{h.assertTrue(active.count()==2&&pending.count()==1,"genuine pair and survivor before ordinary shutdown");active.scores(0);pending.scores(0);PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(h.getLevel());ResourcesServerEvents.saveResources(h.getLevel());ResearchServerEvents.saveResearch();
            System.out.println("API61_HAND_BOND_PREPARED_PAID40_EACH_ACTUAL_COMBAT_DEATH");h.succeed();});
    }
    public static final class StopWitness {
        @SubscribeEvent(priority=EventPriority.HIGH) public void stopping(ServerStoppingEvent event){
            var tag=new CompoundTag();tag.putLong("jvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());tag.putBoolean("normalStop",true);
            tag.put("active",record(active,activeLeft));tag.put("pending",record(pending,survivor));
            if(active.shadow()!=960||pending.shadow()!=99||pending.bond(pending.h.getLevel().getEntity(survivor)).missing!=1)throw new AssertionError("original real payments and pending death at normal stop");
            try{NbtIo.writeCompressed(tag,FILE);}catch(Exception e){throw new RuntimeException(e);}active.peer.close();pending.peer.close();
        }
    }
    private static CompoundTag record(HandBondGameTests.F f,UUID entity){
        var b=HandBondData.get(f.h.getLevel()).forEntity(entity);if(b==null)throw new AssertionError("original bond must exist at stop");
        var t=new CompoundTag();t.putString("owner",f.owner);t.putUUID("epoch",f.account.accountEpoch);t.putUUID("id",b.id);t.putUUID("left",b.left);t.putUUID("right",b.right);t.putInt("missing",b.missing);t.putLong("readyAt",b.readyAt);t.putLong("lastCombat",b.lastCombat);t.putLongArray("chunks",new ArrayList<>(f.training.forced));return t;
    }
    private static void assertNoLiveOriginal(GameTestHelper h,UUID original){
        var entity=h.getLevel().getEntity(original);
        h.assertTrue(entity==null || entity instanceof AaroncosLeftHandUnit hand
            &&!hand.isAlive()&&hand.getHealth()==0.0f&&hand.deathTime>=0&&hand.deathTime<40,
            "original UUID may only be absent or a native HP0 death-animation corpse, never a revived hand");
    }
    private static void verify(GameTestHelper h)throws Exception{
        var saved=NbtIo.readCompressed(FILE);h.assertTrue(saved.getBoolean("normalStop")&&saved.getLong("jvm")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different actual JVM and normal-stop witness");
        var a=saved.getCompound("active");var p=saved.getCompound("pending");var level=h.getLevel();
        for(var t:List.of(a,p))for(long key:t.getLongArray("chunks")){h.assertTrue(level.getForcedChunks().contains(key),"original forced chunk ticket persisted");var c=new ChunkPos(key);level.getChunk(c.x,c.z);}
        var pa=ExtendedResourcesSync.account(p.getString("owner"));var aa=ExtendedResourcesSync.account(a.getString("owner"));
        h.assertTrue(pa!=null&&aa!=null&&pa.accountEpoch.equals(p.getUUID("epoch"))&&aa.accountEpoch.equals(a.getUUID("epoch")),"native same account identities restored");
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.getEntity(a.getUUID("left"))!=null&&level.getEntity(a.getUUID("right"))!=null&&level.getEntity(p.getUUID("right"))!=null,"native saved live bodies loaded"))
            .thenExecute(()->{
                for(var t:List.of(a,p)){var b=HandBondData.get(level).forEntity(t.getUUID("right"));h.assertTrue(b!=null&&b.id.equals(t.getUUID("id"))&&b.left.equals(t.getUUID("left"))&&b.right.equals(t.getUUID("right"))&&b.missing==t.getInt("missing")&&b.readyAt==t.getLong("readyAt"),"native disk restores original bond UUIDs/missing/deadline, not fabricated NBT");}
                h.assertTrue(aa.extra.get(DreamPassiveEconomy.SHADOW)==960&&pa.extra.get(DreamPassiveEconomy.SHADOW)==99&&pa.food==10000,"no shutdown refund or free replacement");
                HandBondGameTests.assertScores(h,a.getString("owner"),0);HandBondGameTests.assertScores(h,p.getString("owner"),0);
                assertNoLiveOriginal(h,p.getUUID("left"));
                var corpse=level.getEntity(p.getUUID("left"));
                System.out.println("HAND_RESTART_OLD_BODY "+(corpse instanceof AaroncosLeftHandUnit hand
                    ? "health="+hand.getHealth()+" deathTime="+hand.deathTime : "absent"));
            // Native PasterDream hands finish death at40ticks. Prepare may save a HP0 corpse
            // at30ticks; publication of the live siblings is not proof this corpse ticked yet.
            }).thenExecuteFor(50,()->{
                assertNoLiveOriginal(h,p.getUUID("left"));
                HandBondGameTests.assertScores(h,p.getString("owner"),0);
            }).thenExecute(()->h.assertTrue(level.getEntity(p.getUUID("left"))==null,
                "native death animation must completely retire original UUID within50ticks, without manual discard"))
            .thenWaitUntil(()->h.assertTrue(level.getGameTime()>=p.getLong("readyAt")+40,"natural remaining cooldown elapsed across saved game clock"))
            .thenExecute(()->{var b=HandBondData.get(level).forEntity(p.getUUID("right"));h.assertTrue(b!=null&&b.missing==1&&pa.extra.get(DreamPassiveEconomy.SHADOW)==99&&pa.food==10000&&UnitServerEvents.getCurrentPopulation(p.getString("owner"))==8,"mature pending remains blocked by actual99shadow after restart");HandBondGameTests.assertScores(h,p.getString("owner"),0);pa.extra.put(DreamPassiveEconomy.SHADOW,199);})
            .thenIdle(60).thenExecute(()->{var b=HandBondData.get(level).forEntity(p.getUUID("right"));h.assertTrue(b!=null&&b.missing==0&&!b.left.equals(p.getUUID("left"))&&level.getEntity(b.left) instanceof AaroncosLeftHandUnit,"one actual new replacement UUID after real funding");
                h.assertTrue(pa.extra.get(DreamPassiveEconomy.SHADOW)==99&&pa.food==9600&&pa.wood==9750&&pa.ore==9650&&UnitServerEvents.getCurrentPopulation(p.getString("owner"))==16,"one full400F250W350O100S paid after restart, exact8 additional population");
                h.assertTrue(aa.extra.get(DreamPassiveEconomy.SHADOW)==960&&HandBondData.get(level).forEntity(a.getUUID("right")).id.equals(a.getUUID("id")),"intact pair stays same without another fee");
                HandBondGameTests.assertScores(h,a.getString("owner"),0);HandBondGameTests.assertScores(h,p.getString("owner"),1);
            }).thenIdle(60).thenExecute(()->{
                HandBondGameTests.assertScores(h,a.getString("owner"),0);HandBondGameTests.assertScores(h,p.getString("owner"),1);
                for(var t:List.of(a,p)){String owner=t.getString("owner");for(var e:List.copyOf(UnitServerEvents.getAllUnits()))if(e instanceof Unit u&&u.getOwnerName().equals(owner))e.discard();for(var host:List.copyOf(BuildingServerEvents.getBuildings()))if(host.ownerName.equals(owner)){host.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(host);}ResearchServerEvents.removeAllResearchFor(owner);PlayerServerEvents.rtsPlayers.removeIf(v->v.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(v->v.ownerName.equals(owner));for(long key:t.getLongArray("chunks")){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}}
                System.out.println("API61_HAND_BOND_RESTART_VERIFIED_PAID_PAIR_PENDING_NO_FREE_REBIRTH");h.succeed();});
    }
}
