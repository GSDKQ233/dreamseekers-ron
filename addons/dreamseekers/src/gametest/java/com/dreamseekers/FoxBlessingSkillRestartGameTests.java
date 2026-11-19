// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Ordinary save/load across two actual JVM processes; expectation manifest is not serialized entity data. */
@GameTestHolder("dreamseekers_fox_blessing_restart")
@PrefixGameTestTemplate(false)
public final class FoxBlessingSkillRestartGameTests {
    private static final String OWNER="FoxBlessRestart_Owner",KEY="fox_blessing";
    private static final BlockPos ORIGIN=new BlockPos(36864,90,36864);
    private static final File MANIFEST=new File("fox-blessing-skill-restart-expectations.nbt");
    private static final UUID PERMANENT=UUID.fromString("ea0e1b51-6ca1-4b58-9b76-e172703e798f");
    private static final List<String> KEYS=List.of("spentCaster","blessedTarget","unusedCaster","freshTarget");

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_fox_blessing_restart",timeoutTicks=1300)
    public static void ordinaryRestartKeepsPaidCooldownWithoutReplayingBlessingOrClearingPermanentResearch(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.fox.blessing.test.phase","")){
            case "prepare" -> prepare(h);case "verify" -> verify(h);default -> throw new IllegalArgumentException("Unknown fox blessing restart phase");
        }
    }
    private static void prepare(GameTestHelper h) {
        ServerLevel level=h.getLevel();h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"new isolated world, never reuse prior prepare");
        CompoundTag expected=new CompoundTag();expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();for(int cx=2304;cx<=2305;cx++)for(int cz=2304;cz<=2305;cz++){long key=ChunkPos.asLong(cx,cz);h.assertTrue(!level.getForcedChunks().contains(key),"not adopting unrelated forced chunks");level.setChunkForced(cx,cz,true);forced.add(key);}expected.putLongArray("forcedChunks",forced);
        arena(level);PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(OWNER,123,234,345,456));Map<String,Mob> units=new LinkedHashMap<>();
        for(int i=0;i<KEYS.size();i++){String key=KEYS.get(i);Mob e=key.endsWith("Caster")?DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level):DreamseekersMod.PINK_SLIME_UNIT.get().create(level);h.assertTrue(e instanceof SkillUnit,"ordinary registered skill fixture");SkillUnit unit=(SkillUnit)e;unit.setOwnerName(OWNER);unit.setScenarioRoleIndex(1201+i);e.moveTo(ORIGIN.getX()+4+(i%2)*3+.5,ORIGIN.getY()+1,ORIGIN.getZ()+4+(i/2)*12+.5);e.setNoAi(true);h.assertTrue(level.addFreshEntity(e),"fixture enters normal entity storage");units.put(key,e);expected.putUUID(key,e.getUUID());}
        Mob target=units.get("blessedTarget");target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(PERMANENT,"permanent restart APS research",.1,AttributeModifier.Operation.ADDITION));
        h.startSequence().thenWaitUntil(()->{for(Mob e:units.values())h.assertTrue(e.tickCount>3&&level.getEntity(e.getId())==e&&level.getEntity(e.getUUID())==e,"ordinary fixture truly published before cast");})
          .thenExecute(()->{
            System.out.println("FOX_BLESSING_RESTART_PREPARE_READY testTick="+h.getTick()+" entityTicks="+units.values().stream().map(e->e.getId()+":"+e.tickCount).toList());
            GoldenFoxUnit source=(GoldenFoxUnit)units.get("spentCaster");h.assertTrue(SkillService.request(player(level),request(source))==SkillResult.SUCCESS,"real self-centered blessing before normal shutdown save");h.assertTrue(SkillEffects.hasNamedBuff(target,KEY,BuffKind.ATTACK_SPEED),"prepare contains live named blessing");close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.2,"prepare has actual twenty-percent APS");h.assertTrue(!SkillEffects.hasNamedBuff(units.get("freshTarget"),KEY,BuffKind.ATTACK_SPEED),"remote fresh fixture not accidentally blessed");expected.putLong("snapshotGameTime",level.getGameTime());
            for(var entry:units.entrySet()){Mob e=entry.getValue();SkillUnit unit=(SkillUnit)e;CompoundTag saved=new CompoundTag();saved.putFloat("health",e.getHealth());saved.putInt("scenario",unit.getScenarioRoleIndex());saved.putLong("revision",unit.rts().skills().revision());saved.putInt("cooldown",unit.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING));saved.putDouble("baseAPS",e.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue());expected.put(entry.getKey()+"State",saved);}
            ledger(h);try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}System.out.println("DREAMSEEKERS_FOX_BLESSING_RESTART_PREPARED gameTime="+level.getGameTime()+" casterUUID="+source.getUUID()+" blessedUUID="+target.getUUID()+" cooldown="+source.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING));
          }).thenSucceed();
    }
    private static void verify(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"prepare expectations present");CompoundTag expected=NbtIo.readCompressed(MANIFEST);h.assertTrue(expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"actually a distinct JVM process");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"normal world save restored forced chunks");ChunkPos chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);}Map<String,Mob> units=new LinkedHashMap<>();
        h.startSequence().thenWaitUntil(()->{for(String key:KEYS){Entity e=level.getEntity(expected.getUUID(key));h.assertTrue(e instanceof Mob mob&&mob instanceof SkillUnit&&mob.tickCount>3&&level.getEntity(mob.getId())==mob,"ordinary asynchronous UUID restoration: "+key);}})
          .thenExecute(()->{
            System.out.println("FOX_BLESSING_RESTART_VERIFY_READY testTick="+h.getTick()+" entityTicks="+KEYS.stream().map(k->level.getEntity(expected.getUUID(k)).tickCount).toList());
            for(String key:KEYS){Mob e=(Mob)level.getEntity(expected.getUUID(key));units.put(key,e);SkillUnit unit=(SkillUnit)e;CompoundTag saved=expected.getCompound(key+"State");h.assertTrue(unit.getOwnerName().equals(OWNER)&&unit.getScenarioRoleIndex()==saved.getInt("scenario"),"saved owner/scenario intact: "+key);close(h,e.getHealth(),saved.getFloat("health"),"saved HP unchanged: "+key);h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(u->u.getUUID().equals(e.getUUID())).count()==1,"UUID roster restored once: "+key);h.assertTrue(!SkillEffects.hasNamedBuff(e,KEY,BuffKind.ATTACK_SPEED),"named short-lived effect never replayed: "+key);close(h,SkillEffects.bonus(e,BuffKind.ATTACK_SPEED),0,"no replayed or permanent temporary APS: "+key);close(h,e.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue(),saved.getDouble("baseAPS"),"base APS retained: "+key);long expiry=expected.getLong("snapshotGameTime")+saved.getInt("cooldown");int remaining=(int)Math.max(0,expiry-level.getGameTime());h.assertTrue(unit.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==remaining,"absolute cooldown neither refunded nor restarted: "+key+" expected="+remaining+" actual="+unit.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING));h.assertTrue(unit.rts().skills().revision()>=saved.getLong("revision"),"loaded revision cannot rewind: "+key);}
            Mob target=units.get("blessedTarget");h.assertTrue(target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(PERMANENT)!=null,"permanent research survives");close(h,target.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),expected.getCompound("blessedTargetState").getDouble("baseAPS")+.1,"only true permanent modifier remains");
            GoldenFoxUnit spent=(GoldenFoxUnit)units.get("spentCaster"),fresh=(GoldenFoxUnit)units.get("unusedCaster");if(spent.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)>0)h.assertTrue(SkillService.request(player(level),request(spent))!=SkillResult.SUCCESS,"persisted live cooldown rejects free recast");h.assertTrue(fresh.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==0,"unused source remains unused");h.assertTrue(SkillService.request(player(level),request(fresh))==SkillResult.SUCCESS,"genuine new manual cast works after restart");h.assertTrue(SkillEffects.hasNamedBuff(units.get("freshTarget"),KEY,BuffKind.ATTACK_SPEED),"new post-restart cast creates new effect, not replay");ledger(h);System.out.println("DREAMSEEKERS_FOX_BLESSING_RESTART_UUID_COOLDOWN_NO_REPLAY_PASS elapsedWorldTicks="+(level.getGameTime()-expected.getLong("snapshotGameTime"))+" remaining="+spent.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING));
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(OWNER));for(Mob e:units.values())e.discard();for(long key:expected.getLongArray("forcedChunks")){ChunkPos chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}
          }).thenSucceed();
    }
    private static SkillRequest request(GoldenFoxUnit source){return new SkillRequest(UnitSkill.FOX_BLESSING,source.getId(),source.getUUID(),source.rts().skills().revision(),source.getId(),source.getUUID());}
    private static net.minecraftforge.common.util.FakePlayer player(ServerLevel level){return FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(OWNER.getBytes(java.nio.charset.StandardCharsets.UTF_8)),OWNER));}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<.0003,why+": expected="+expected+" actual="+actual);}
    private static void ledger(GameTestHelper h){var r=ResourcesServerEvents.resourcesList.stream().filter(e->e.ownerName.equals(OWNER)).findFirst().orElseThrow();h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456,"cast/restart neither mints nor spends global resources");}
    private static void arena(ServerLevel level){for(int x=0;x<30;x++)for(int z=0;z<30;z++)level.setBlock(ORIGIN.offset(x,9,z),Blocks.STONE.defaultBlockState(),2);level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+30,level.getMaxBuildHeight(),ORIGIN.getZ()+30)).forEach(Entity::discard);for(int x=0;x<30;x++)for(int z=0;z<30;z++){level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(ORIGIN.offset(x,y,z),(x==0||x==29||z==0||z==29?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}}
}
