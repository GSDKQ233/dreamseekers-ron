// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.PinkSlimeUnit;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Normal world/entity/resource persistence across two real JVMs; ephemeral effects never reconstructed. */
@GameTestHolder("dreamseekers_gel_skill_restart")
@PrefixGameTestTemplate(false)
public final class GelGuardSkillRestartGameTests {
    private static final String OWNER="GelRestart_Owner", ENEMY="GelRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(32768,90,32768);
    private static final File MANIFEST=new File("gel-skill-restart-expectations.nbt");
    private static final UUID PERMANENT=UUID.fromString("6f9a8c7b-1122-4356-889a-bbddee5511cc");
    private static final List<String> KEYS=List.of("shieldCaster","shieldTarget","counterCaster","counterTarget","freshCaster","freshTarget");
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_gel_skill_restart",timeoutTicks=700)
    public static void ordinaryRestartKeepsCooldownWithoutReplayingShieldOrCounter(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.gel.skill.test.phase","")){
            case "prepare" -> prepare(h);case "verify" -> verify(h);default -> throw new IllegalArgumentException("Unknown gel skill restart phase");
        }
    }
    private static void prepare(GameTestHelper h) {
        ServerLevel level=h.getLevel();h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"new isolated restart world, not a reused prepared probe");
        CompoundTag expected=new CompoundTag();expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();for(int cx=2048;cx<=2049;cx++)for(int cz=2048;cz<=2049;cz++){long key=ChunkPos.asLong(cx,cz);h.assertTrue(!level.getForcedChunks().contains(key),"no adopting unrelated forced chunks");level.setChunkForced(cx,cz,true);forced.add(key);}expected.putLongArray("forcedChunks",forced);
        arena(level);PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(OWNER,123,234,345,456));
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(ENEMY,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(ENEMY,111,222,333,444));
        Map<String,PinkSlimeUnit> units=new LinkedHashMap<>();
        for(int i=0;i<KEYS.size();i++){String key=KEYS.get(i);PinkSlimeUnit unit=spawn(h,OWNER,4+(i%2)*3,4+(i/2)*7);units.put(key,unit);expected.putUUID(key,unit.getUUID());unit.setScenarioRoleIndex(901+i);}
        PinkSlimeUnit enemy=spawn(h,ENEMY,10,11);enemy.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(12);enemy.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(12);
        PinkSlimeUnit counter=units.get("counterTarget");counter.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(PERMANENT,"restart permanent research",.1,AttributeModifier.Operation.ADDITION));
        var sequence=h.startSequence().thenWaitUntil(()->{for(PinkSlimeUnit e:units.values())h.assertTrue(e.tickCount>3&&level.getEntity(e.getId())==e&&level.getEntity(e.getUUID())==e,"ordinary fixture genuinely published before prepare");h.assertTrue(enemy.tickCount>3&&level.getEntity(enemy.getId())==enemy,"enemy fixture published");});
        sequence.thenExecute(()->{
            var owner=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(OWNER.getBytes(java.nio.charset.StandardCharsets.UTF_8)),OWNER));
            PinkSlimeUnit shieldCaster=units.get("shieldCaster"),shieldTarget=units.get("shieldTarget"),counterCaster=units.get("counterCaster");
            h.assertTrue(SkillService.request(owner,request(shieldCaster,shieldTarget))==SkillResult.SUCCESS,"real shield immediately before normal process save");
            h.assertTrue(SkillService.request(owner,request(counterCaster,counter))==SkillResult.SUCCESS,"real counter fixture cast");
            counter.getAttribute(Attributes.ARMOR).setBaseValue(0);counter.invulnerableTime=0;h.assertTrue(enemy.doHurtTarget(counter),"real enemy ordinary hit exhausts counter fixture shield");
            close(h,SkillEffects.shieldRemaining(shieldTarget),12,"prepare has an active shield");close(h,SkillEffects.shieldRemaining(counter),0,"prepare counter shield genuinely exhausted");close(h,SkillEffects.bonus(counter,BuffKind.ATTACK_SPEED),.15,"prepare has a live triggered counter buff");
            expected.putLong("snapshotGameTime",level.getGameTime());
            for(var entry:units.entrySet()){
                PinkSlimeUnit e=entry.getValue();CompoundTag state=new CompoundTag();state.putLong("revision",e.rts().skills().revision());state.putInt("cooldown",e.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD));state.putFloat("health",e.getHealth());state.putDouble("baseAPS",e.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue());state.putInt("scenario",e.getScenarioRoleIndex());expected.put(entry.getKey()+"State",state);
            }
            // Remove only the hostile transient fixture: no saved entity is serialized manually.
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(ENEMY));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(ENEMY));enemy.discard();
            ledger(h);try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
            System.out.println("DREAMSEEKERS_GEL_SKILL_RESTART_PREPARED gameTime="+level.getGameTime()+" shieldUUID="+shieldTarget.getUUID()+" counterUUID="+counter.getUUID()+" cooldown="+shieldCaster.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD));
        }).thenSucceed();
    }
    private static void verify(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"first JVM expectation manifest present");CompoundTag expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"verify actually runs in another JVM");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"world normally restored forced chunks");ChunkPos c=new ChunkPos(key);level.getChunk(c.x,c.z);}
        Map<String,PinkSlimeUnit> units=new LinkedHashMap<>();
        h.startSequence().thenWaitUntil(()->{for(String key:KEYS){Entity entity=level.getEntity(expected.getUUID(key));h.assertTrue(entity instanceof PinkSlimeUnit e&&e.tickCount>3&&level.getEntity(e.getId())==e,"wait ordinary asynchronous UUID restoration and real ticks: "+key);}})
          .thenExecute(()->{
            for(String key:KEYS){
                PinkSlimeUnit e=(PinkSlimeUnit)level.getEntity(expected.getUUID(key));units.put(key,e);CompoundTag saved=expected.getCompound(key+"State");
                h.assertTrue(e.getOwnerName().equals(OWNER)&&e.getScenarioRoleIndex()==saved.getInt("scenario")&&e.getHealth()==saved.getFloat("health"),"saved UUID owner scenario and HP preserved: "+key);
                h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(u->u.getUUID().equals(e.getUUID())).count()==1,"ordinary roster contains UUID exactly once: "+key);
                close(h,SkillEffects.shieldRemaining(e),0,"short-lived shield is not replayed on load: "+key);close(h,SkillEffects.bonus(e,BuffKind.ATTACK_SPEED),0,"counter not replayed or made permanent: "+key);
                close(h,e.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue(),saved.getDouble("baseAPS"),"base APS preserved: "+key);
                long expiry=expected.getLong("snapshotGameTime")+saved.getInt("cooldown");int shouldRemain=(int)Math.max(0,expiry-level.getGameTime());
                h.assertTrue(e.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==shouldRemain,"absolute saved cooldown neither refunded nor restarted: "+key+" expected="+shouldRemain+" actual="+e.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD));
                h.assertTrue(e.rts().skills().revision()>=saved.getLong("revision"),"loaded revision cannot rewind: "+key);
            }
            PinkSlimeUnit counter=units.get("counterTarget");h.assertTrue(counter.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(PERMANENT)!=null,"permanent research survives independently of temporary buff");close(h,counter.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),expected.getCompound("counterTargetState").getDouble("baseAPS")+.1,"only genuine permanent modifier remains");
            var owner=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(OWNER.getBytes(java.nio.charset.StandardCharsets.UTF_8)),OWNER));
            PinkSlimeUnit cast=units.get("shieldCaster"),target=units.get("shieldTarget");
            if(cast.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)>0)h.assertTrue(SkillService.request(owner,request(cast,target))!=SkillResult.SUCCESS,"still-live persisted cooldown blocks recast, no free new shield");
            PinkSlimeUnit fresh=units.get("freshCaster"),freshTarget=units.get("freshTarget");h.assertTrue(fresh.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==0,"uncast unit retains zero cooldown");h.assertTrue(SkillService.request(owner,request(fresh,freshTarget))==SkillResult.SUCCESS,"never-cast restored unit can cast normally after restart");close(h,SkillEffects.shieldRemaining(freshTarget),12,"genuine new post-restart cast works");
            ledger(h);System.out.println("DREAMSEEKERS_GEL_SKILL_RESTART_UUID_COOLDOWN_NO_REPLAY_PASS elapsedWorldTicks="+(level.getGameTime()-expected.getLong("snapshotGameTime"))+" remaining="+cast.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD));
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(OWNER));for(PinkSlimeUnit e:units.values())e.discard();
            for(long key:expected.getLongArray("forcedChunks")){ChunkPos c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}
          }).thenSucceed();
    }
    private static SkillRequest request(PinkSlimeUnit source,Entity target){return new SkillRequest(UnitSkill.GEL_GUARD,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID());}
    private static PinkSlimeUnit spawn(GameTestHelper h,String owner,int x,int z){PinkSlimeUnit e=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());h.assertTrue(e!=null,"registered paid slime exists");e.setOwnerName(owner);e.moveTo(ORIGIN.getX()+x+.5,ORIGIN.getY()+1,ORIGIN.getZ()+z+.5);e.setNoAi(true);h.assertTrue(h.getLevel().addFreshEntity(e),"fixture enters ordinary entity storage");return e;}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<.0002,why+": expected="+expected+" actual="+actual);}
    private static void ledger(GameTestHelper h){var r=ResourcesServerEvents.resourcesList.stream().filter(e->e.ownerName.equals(OWNER)).findFirst().orElseThrow();h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456,"cast/effect/save/restart neither mints nor spends ordinary resources");}
    private static void arena(ServerLevel level){for(int x=0;x<30;x++)for(int z=0;z<30;z++)level.setBlock(ORIGIN.offset(x,9,z),Blocks.STONE.defaultBlockState(),2);level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+30,level.getMaxBuildHeight(),ORIGIN.getZ()+30)).forEach(Entity::discard);for(int x=0;x<30;x++)for(int z=0;z<30;z++){level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(ORIGIN.offset(x,y,z),(x==0||x==29||z==0||z==29?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}}
}
