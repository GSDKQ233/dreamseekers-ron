// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.player.*;
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

/** Two real JVMs and ordinary world/entity save; the manifest contains expectations, not entity data. */
@GameTestHolder("dreamseekers_jelly_flow_restart")
@PrefixGameTestTemplate(false)
public final class JellyFlowSkillRestartGameTests {
    private static final String OWNER="JellyFlowRestart_Owner";
    private static final BlockPos ORIGIN=new BlockPos(34816,90,34816);
    private static final File MANIFEST=new File("jelly-flow-skill-restart-expectations.nbt");
    private static final UUID PERMANENT=UUID.fromString("02329172-a1a2-4849-b5b6-c1c233337777");
    private static final List<String> KEYS=List.of("hotCaster","hotTarget","tideCaster","tideTarget","freshCaster","freshTarget");

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jelly_flow_restart",timeoutTicks=850)
    public static void ordinaryRestartKeepsPaidCooldownAndAutoPreferenceWithoutFlowOrTideReplay(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.jelly.flow.test.phase","")){
            case "prepare" -> prepare(h);case "verify" -> verify(h);default -> throw new IllegalArgumentException("Unknown jelly flow restart phase");
        }
    }

    private static void prepare(GameTestHelper h) {
        ServerLevel level=h.getLevel();h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"new isolated restart world, not a reused prepared probe");
        CompoundTag expected=new CompoundTag();expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();for(int cx=2176;cx<=2177;cx++)for(int cz=2176;cz<=2177;cz++){long key=ChunkPos.asLong(cx,cz);h.assertTrue(!level.getForcedChunks().contains(key),"no adopting unrelated forced chunks");level.setChunkForced(cx,cz,true);forced.add(key);}expected.putLongArray("forcedChunks",forced);
        arena(level);PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(OWNER,123,234,345,456));
        Map<String,Mob> units=new LinkedHashMap<>();
        for(int i=0;i<KEYS.size();i++){
            String key=KEYS.get(i);Mob e=key.endsWith("Caster")||key.equals("tideTarget")?DreamseekersMod.JELLYFISH_UNIT.get().create(level):DreamseekersMod.PINK_SLIME_UNIT.get().create(level);
            h.assertTrue(e instanceof SkillUnit,"registered ordinary skill fixture exists");SkillUnit unit=(SkillUnit)e;unit.setOwnerName(OWNER);unit.setScenarioRoleIndex(1101+i);
            e.moveTo(ORIGIN.getX()+4+(i%2)*3+.5,ORIGIN.getY()+1,ORIGIN.getZ()+4+(i/2)*7+.5);e.setNoAi(true);h.assertTrue(level.addFreshEntity(e),"fixture enters normal entity storage");units.put(key,e);expected.putUUID(key,e.getUUID());
        }
        Mob tide=units.get("tideTarget");for(var attr:List.of(Attributes.MOVEMENT_SPEED,Attributes.FLYING_SPEED))tide.getAttribute(attr).addPermanentModifier(new AttributeModifier(PERMANENT,"restart permanent navigation research",.02,AttributeModifier.Operation.ADDITION));
        h.startSequence().thenWaitUntil(()->{for(Mob e:units.values())h.assertTrue(e.tickCount>3&&level.getEntity(e.getId())==e&&level.getEntity(e.getUUID())==e,"ordinary fixture genuinely published before prepare");})
          .thenExecute(()->{
            var owner=player(level);
            // Native Jellyfish load restores AI. Persistently disable auto on every jelly, including the recipient,
            // so a legitimate newly selected cast cannot be confused with replay of saved short-lived effects.
            for(Mob e:units.values())if(e instanceof JellyfishUnit jelly)h.assertTrue(SkillService.setAutocast(owner,UnitSkill.JELLY_FLOW,e.getId(),e.getUUID(),jelly.rts().skills().revision(),false)==SkillResult.SUCCESS,"real owner disables and persists automatic preference");
            tide.setHealth(tide.getMaxHealth()-2);h.assertTrue(SkillService.request(owner,request((JellyfishUnit)units.get("tideCaster"),tide))==SkillResult.SUCCESS,"real completed-flow fixture cast");
            units.get("hotTarget").setHealth(20);units.get("freshTarget").setHealth(20);
          }).thenIdle(121).thenExecute(()->{
            close(h,SkillEffects.bonus(tide,BuffKind.MOVEMENT_SPEED),.1,"prepare has a real live completion tide");
            JellyfishUnit hotCaster=(JellyfishUnit)units.get("hotCaster");Mob hotTarget=units.get("hotTarget");h.assertTrue(SkillService.request(player(level),request(hotCaster,hotTarget))==SkillResult.SUCCESS,"real active flow immediately before ordinary save");h.assertTrue(SkillEffects.healTicks(hotTarget)==120,"prepare has unconsumed live HoT");
            expected.putLong("snapshotGameTime",level.getGameTime());
            for(var entry:units.entrySet()){
                Mob e=entry.getValue();SkillUnit unit=(SkillUnit)e;CompoundTag state=new CompoundTag();state.putFloat("health",e.getHealth());state.putLong("revision",unit.rts().skills().revision());state.putInt("cooldown",unit.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW));state.putInt("scenario",unit.getScenarioRoleIndex());state.putDouble("baseMove",e.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue());if(e.getAttribute(Attributes.FLYING_SPEED)!=null)state.putDouble("baseFly",e.getAttribute(Attributes.FLYING_SPEED).getBaseValue());expected.put(entry.getKey()+"State",state);
            }
            ledger(h);try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
            System.out.println("DREAMSEEKERS_JELLY_FLOW_RESTART_PREPARED gameTime="+level.getGameTime()+" hotUUID="+hotTarget.getUUID()+" tideUUID="+tide.getUUID()+" cooldown="+hotCaster.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW));
          }).thenSucceed();
    }

    private static void verify(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"first JVM expectation manifest present");CompoundTag expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"verify actually runs in another JVM");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"world normally restored forced chunks");ChunkPos chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);}
        Map<String,Mob> units=new LinkedHashMap<>();float[] freshBefore={0};
        h.startSequence().thenWaitUntil(()->{for(String key:KEYS){Entity e=level.getEntity(expected.getUUID(key));h.assertTrue(e instanceof Mob mob&&mob instanceof SkillUnit&&mob.tickCount>3&&level.getEntity(mob.getId())==mob,"wait ordinary asynchronous UUID restoration and real ticks: "+key);}})
          .thenExecute(()->{
            for(String key:KEYS){
                Mob e=(Mob)level.getEntity(expected.getUUID(key));units.put(key,e);SkillUnit unit=(SkillUnit)e;CompoundTag saved=expected.getCompound(key+"State");
                h.assertTrue(unit.getOwnerName().equals(OWNER)&&unit.getScenarioRoleIndex()==saved.getInt("scenario"),"ordinary saved owner and scenario preserved: "+key);close(h,e.getHealth(),saved.getFloat("health"),"saved HP preserved without replayed healing: "+key);
                h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(u->u.getUUID().equals(e.getUUID())).count()==1,"ordinary roster contains UUID exactly once: "+key);
                h.assertTrue(SkillEffects.healTicks(e)==0,"short-lived HoT not replayed: "+key);close(h,SkillEffects.bonus(e,BuffKind.MOVEMENT_SPEED),0,"completion tide not replayed or permanent: "+key);
                long expiry=expected.getLong("snapshotGameTime")+saved.getInt("cooldown");int shouldRemain=(int)Math.max(0,expiry-level.getGameTime());h.assertTrue(unit.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)==shouldRemain,"absolute cooldown neither refunded nor restarted: "+key+" expected="+shouldRemain+" actual="+unit.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW));h.assertTrue(unit.rts().skills().revision()>=saved.getLong("revision"),"loaded revision cannot rewind: "+key);
                if(e instanceof JellyfishUnit)h.assertTrue(!unit.rts().skills().autocastEnabled(UnitSkill.JELLY_FLOW),"disabled automatic preference survives native AI restoration: "+key);
            }
            Mob tide=units.get("tideTarget");for(var attr:List.of(Attributes.MOVEMENT_SPEED,Attributes.FLYING_SPEED)){h.assertTrue(tide.getAttribute(attr).getModifier(PERMANENT)!=null,"permanent navigation research retained");String base=attr==Attributes.MOVEMENT_SPEED?"baseMove":"baseFly";close(h,tide.getAttributeValue(attr),expected.getCompound("tideTargetState").getDouble(base)+.02,"only genuine permanent modifier survives restart");}
            JellyfishUnit hotCaster=(JellyfishUnit)units.get("hotCaster");if(hotCaster.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0)h.assertTrue(SkillService.request(player(level),request(hotCaster,units.get("hotTarget")))!=SkillResult.SUCCESS,"still-live persisted cooldown prevents free replacement flow");
            JellyfishUnit fresh=(JellyfishUnit)units.get("freshCaster");h.assertTrue(fresh.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)==0,"uncast fixture retains zero cooldown");freshBefore[0]=units.get("freshTarget").getHealth();h.assertTrue(SkillService.setAutocast(player(level),UnitSkill.JELLY_FLOW,fresh.getId(),fresh.getUUID(),fresh.rts().skills().revision(),true)==SkillResult.SUCCESS,"real owner newly reenables automatic cast after restart");ledger(h);
          }).thenIdle(24).thenExecute(()->{
            JellyfishUnit fresh=(JellyfishUnit)units.get("freshCaster");Mob freshTarget=units.get("freshTarget");h.assertTrue(SkillEffects.healTicks(freshTarget)>0&&fresh.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0,"genuine newly enabled automatic cast works after restart");close(h,freshTarget.getHealth(),freshBefore[0]+2,"genuine new flow produces exactly one fresh pulse");ledger(h);
            System.out.println("DREAMSEEKERS_JELLY_FLOW_RESTART_UUID_COOLDOWN_AUTO_OFF_NO_REPLAY_PASS elapsedWorldTicks="+(level.getGameTime()-expected.getLong("snapshotGameTime"))+" hotRemaining="+((SkillUnit)units.get("hotCaster")).rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW));
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(OWNER));for(Mob e:units.values())e.discard();for(long key:expected.getLongArray("forcedChunks")){ChunkPos chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}
          }).thenSucceed();
    }

    private static SkillRequest request(JellyfishUnit source,Entity target){return new SkillRequest(UnitSkill.JELLY_FLOW,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID());}
    private static net.minecraftforge.common.util.FakePlayer player(ServerLevel level){return FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(OWNER.getBytes(java.nio.charset.StandardCharsets.UTF_8)),OWNER));}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<.0003,why+": expected="+expected+" actual="+actual);}
    private static void ledger(GameTestHelper h){var r=ResourcesServerEvents.resourcesList.stream().filter(e->e.ownerName.equals(OWNER)).findFirst().orElseThrow();h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456,"cast/effect/save/restart neither mints nor spends ordinary resources");}
    private static void arena(ServerLevel level){for(int x=0;x<30;x++)for(int z=0;z<30;z++)level.setBlock(ORIGIN.offset(x,9,z),Blocks.STONE.defaultBlockState(),2);level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+30,level.getMaxBuildHeight(),ORIGIN.getZ()+30)).forEach(Entity::discard);for(int x=0;x<30;x++)for(int z=0;z<30;z++){level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(ORIGIN.offset(x,y,z),(x==0||x==29||z==0||z==29?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}}
}
