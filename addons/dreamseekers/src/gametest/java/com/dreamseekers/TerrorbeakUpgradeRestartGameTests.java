package com.dreamseekers;

import com.dreamseekers.economy.*;
import com.dreamseekers.shadow.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Two actual JVMs and ordinary world storage. Verify never constructs/deserializes a replacement body. */
@GameTestHolder("dreamseekers_terrorbeak_upgrade_restart")
@PrefixGameTestTemplate(false)
public final class TerrorbeakUpgradeRestartGameTests {
    private static final String OWNER="CrazyUpgradeRestart",ENEMY="CrazyUpgradeEnemy";
    private static final BlockPos ORIGIN=new BlockPos(155648,100,155648);
    private static PassiveIncomePeer loadedPeer;
    @AfterBatch(batch="terrorbeak_upgrade_restart") public static void cleanup(net.minecraft.server.level.ServerLevel level){if(loadedPeer!=null){loadedPeer.close();loadedPeer=null;}}
    private static final File MANIFEST=new File("terrorbeak-upgrade-restart.nbt");
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade_restart",template="ore_mine_test_arena",batch="terrorbeak_upgrade_restart",timeoutTicks=180)
    public static void actualReplacementUuidLineagePaidBankAndOriginalRoarDeadlinePersist(GameTestHelper h)throws Exception {
        switch(System.getProperty("dreamseekers.terrorbeakUpgrade.phase","")){
            case "prepare"->prepare(h);case "verify"->verify(h);default->throw new IllegalArgumentException("prepare/verify required");
        }
    }
    private static void prepare(GameTestHelper h)throws Exception {
        var level=h.getLevel();h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(ENEMY)==null,"fresh independent normal-save fixture");
        var expected=new CompoundTag();expected.putInt("schema",1);expected.putLong("jvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();for(int x=(ORIGIN.getX()>>4)-2;x<=(ORIGIN.getX()>>4)+2;x++)for(int z=(ORIGIN.getZ()>>4)-2;z<=(ORIGIN.getZ()>>4)+2;z++){long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"new fixture owns only its tickets");level.setChunkForced(x,z,true);level.getChunk(x,z);forced.add(key);}expected.putLongArray("forced",forced);
        for(var p:BlockPos.betweenClosed(ORIGIN.offset(-10,-1,-10),ORIGIN.offset(10,7,10)))level.setBlock(p,p.getY()==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        for(String owner:List.of(OWNER,ENEMY)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));var r=new Resources(owner,1000,1000,1000);r.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(r);}
        var source=DreamseekersMod.TERRORBEAK_UNIT.get().create(level);source.setOwnerName(OWNER);source.setNoAi(true);source.setPersistenceRequired();source.moveTo(ORIGIN.getX()+.5,100,ORIGIN.getZ()+.5);source.setHealth(source.getMaxHealth()/2);h.assertTrue(level.addFreshEntity(source),"original normal paid source published");
        var enemy=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(level);enemy.setOwnerName(ENEMY);enemy.setNoAi(true);enemy.setPersistenceRequired();enemy.moveTo(ORIGIN.getX()+3.5,100,ORIGIN.getZ()+.5);h.assertTrue(level.addFreshEntity(enemy),"actual enemy target for genuine roar cast");enemy.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);
        UUID origin=RtsKillValueClaims.valueId(source);expected.putUUID("old",source.getUUID());expected.putUUID("enemy",enemy.getUUID());expected.putUUID("origin",origin);
        h.startSequence().thenWaitUntil(()->{
            h.assertTrue(source.tickCount>0&&enemy.tickCount>0&&level.getEntity(source.getId())==source&&level.getEntity(source.getUUID())==source
                &&level.getEntity(enemy.getId())==enemy&&level.getEntity(enemy.getUUID())==enemy,"original bodies naturally tick and enter exact world lookup before paid clocks start");
        }).thenExecute(()->{try{finishPrepare(h,expected,source,enemy,origin);}catch(Exception e){throw new RuntimeException(e);}});
    }
    private static void finishPrepare(GameTestHelper h,CompoundTag expected,TerrorbeakUnit source,ShadowGhostUnit enemy,UUID origin)throws Exception {
        var level=h.getLevel();
        try(var peer=new PassiveIncomePeer(level,OWNER)){
            h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"isolated test world actual fog disabled, not a fabricated visibility bypass");
            h.assertTrue(SkillService.request(peer.actor,new SkillRequest(UnitSkill.TERRORBEAK_DREAD_ROAR,source.getId(),source.getUUID(),source.rts().skills().revision(),source.getId(),source.getUUID()))==SkillResult.SUCCESS,"real old form roar pays original400tick clock");
            h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR)==400,"ordinary skill duration unchanged for restart window");
            h.assertTrue(TerrorbeakUpgradeService.request(peer.actor,new TerrorbeakUpgradeRequest(1,UUID.randomUUID(),new TerrorbeakUpgradeRequest.Source(source.getId(),source.getUUID(),source.rts().ownershipRevision,level.dimension().location()),TerrorbeakUpgradeRequest.UpgradeTarget.CRAZY_TERRORBEAK))==TerrorbeakUpgradeService.Result.SUCCESS,"real paid form transition before normal save");
        }
        var body=(CrazyTerrorbeakUnit)UnitServerEvents.getAllUnits().stream().filter(e->e instanceof CrazyTerrorbeakUnit u&&OWNER.equals(u.getOwnerName())).findFirst().orElseThrow();
        body.setPersistenceRequired();body.setAnchor(body.blockPosition());body.setNoAi(true);body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);enemy.moveTo(ORIGIN.getX()+9.5,100,ORIGIN.getZ()+9.5);enemy.setAnchor(enemy.blockPosition());
        var account=ExtendedResourcesSync.account(OWNER);expected.putUUID("new",body.getUUID());expected.putUUID("epoch",account.accountEpoch);expected.putLong("now",level.getGameTime());expected.putLong("deadline",level.getGameTime()+body.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR));expected.putFloat("ratio",body.getHealth()/body.getMaxHealth());expected.putLong("revision",body.rts().skills().revision());expected.put("value",RtsKillValueClaims.copyValue(body));
        h.assertTrue(source.isRemoved()&&UnitServerEvents.getCurrentPopulation(OWNER)==3&&account.food==920&&account.ore==940&&account.extra.get(DreamPassiveEconomy.SHADOW)==470,"single3pop replacement and one full upgrade debit at checkpoint");
        h.assertTrue(expected.getLong("deadline")==expected.getLong("now")+400&&!TerrorbeakUpgradeService.pending(body),"original paid CD and completed publication marker state");
        NbtIo.writeCompressed(expected,MANIFEST);System.out.println("TERRORBEAK_UPGRADE_RESTART_PREPARED old="+source.getUUID()+" new="+body.getUUID()+" origin="+origin+" deadline="+expected.getLong("deadline"));h.succeed();
    }
    private static void verify(GameTestHelper h)throws Exception {
        h.assertTrue(MANIFEST.isFile(),"prepare expectations exist");var expected=NbtIo.readCompressed(MANIFEST);var level=h.getLevel();
        h.assertTrue(expected.getInt("schema")==1&&expected.getLong("jvm")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different actual JVM, no in-process pseudo restart");
        for(long key:expected.getLongArray("forced")){h.assertTrue(level.getForcedChunks().contains(key),"original forced ticket loaded from normal disk save");var c=new ChunkPos(key);level.getChunk(c.x,c.z);}
        loadedPeer=new PassiveIncomePeer(level,OWNER);loadedPeer.drain();
        h.startSequence().thenWaitUntil(()->{h.assertTrue(level.getEntity(expected.getUUID("new")) instanceof CrazyTerrorbeakUnit,"actual original replacement UUID loaded by ordinary entity storage");h.assertTrue(level.getEntity(expected.getUUID("enemy")) instanceof ShadowGhostUnit,"original target UUID loaded");}).thenExecute(()->{
            var body=(CrazyTerrorbeakUnit)level.getEntity(expected.getUUID("new"));var account=ExtendedResourcesSync.account(OWNER);long now=level.getGameTime();
            h.assertTrue(now>=expected.getLong("now")&&now<expected.getLong("deadline"),"normal restart remains inside original400tick deadline; no enlarged test cooldown");
            h.assertTrue(body.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR)==expected.getLong("deadline")-now&&body.rts().skills().revision()>=expected.getLong("revision"),"exact original absolute roar deadline minus loaded world clock");
            h.assertTrue(OWNER.equals(body.getOwnerName())&&!TerrorbeakUpgradeService.pending(body)&&!RtsKillValueClaims.isFree(body)&&!RtsKillValueClaims.isClaimed(body)&&RtsKillValueClaims.valueId(body).equals(expected.getUUID("origin"))&&RtsKillValueClaims.copyValue(body).equals(expected.get("value")),"same completed owner and original single economic lineage, no reissue");
            h.assertTrue(level.getEntity(expected.getUUID("old"))==null&&UnitServerEvents.getAllUnits().stream().noneMatch(e->e.getUUID().equals(expected.getUUID("old")))&&UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(expected.getUUID("new"))).count()==1&&UnitServerEvents.getCurrentPopulation(OWNER)==3,"retired old body absent, exact one original replacement and population3");
            h.assertTrue(account!=null&&account.accountEpoch.equals(expected.getUUID("epoch"))&&account.food==920&&account.wood==1000&&account.ore==940&&account.extra.get(DreamPassiveEconomy.SHADOW)==470,"normal saved account epoch and one debit retained, no refund");
            h.assertTrue(Math.abs(body.getHealth()/body.getMaxHealth()-expected.getFloat("ratio"))<.001&&body.getCost().food==210&&body.getCost().ore==145,"original HP ratio and standard economic cost survived");
            System.out.println("TERRORBEAK_UPGRADE_RESTART_STATE_OBSERVED new="+body.getUUID()+" origin="+RtsKillValueClaims.valueId(body)+" remaining="+body.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR));
        }).thenIdle(41).thenExecute(()->loadedPeer.drain()).thenIdle(14).thenExecute(()->{
            var body=(CrazyTerrorbeakUnit)level.getEntity(expected.getUUID("new"));long now=level.getGameTime();
            h.assertTrue(now<expected.getLong("deadline"),"fresh view observed while original paid cooldown still live");
            h.assertTrue(TerrorbeakUpgradeGameTests.ownerSkillViews(loadedPeer.drain()).stream().anyMatch(v->v.sourceUuid().equals(body.getUUID())&&v.sourceId()==body.getId()&&v.skill()==UnitSkill.TERRORBEAK_DREAD_ROAR&&v.revision()>=expected.getLong("revision")&&v.cooldownTicks()>0),"normally reloaded original Crazy emits fresh owner-private roar view after40tick client lifetime, no test sync call");
            System.out.println("TERRORBEAK_UPGRADE_RESTART_VERIFIED new="+body.getUUID()+" origin="+RtsKillValueClaims.valueId(body)+" remaining="+body.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR)+" freshPrivateViewAfterLoad=true");
            cleanup(level);
        }).thenSucceed();
    }
}
