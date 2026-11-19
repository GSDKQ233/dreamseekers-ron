package com.dreamseekers;
import com.dreamseekers.resonance.*;
import com.dreamseekers.skill.*;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;
@GameTestHolder("dreamseekers_resonance_fog") @PrefixGameTestTemplate(false)
public final class ResonanceFogGameTests {
    private static ResonanceNetworkGameTests.F fixture;private static ControlledFog fog;
    @AfterBatch(batch="resonance_fog") public static void cleanup(ServerLevel level){if(fog!=null){fog.close();fog=null;}if(fixture!=null){fixture.close();fixture=null;}}
    @GameTest(templateNamespace="dreamseekers_resonance_fog",template="dreamseekers_test_arena",batch="resonance_fog",timeoutTicks=160)
    public static void realFogHiddenRecipientDoesNotAuthorizePaidPulse(GameTestHelper h){var f=new ResonanceNetworkGameTests.F(h,"Fog",20);fixture=f;var u=f.unit(f.dais,3,-4);
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{fog=new ControlledFog(h,f.peer.actor);fog.install();
            h.assertTrue(!CoordinatedMark.visible(f.peer.actor,u)&&f.pulse(f.dais).equals("no_target")&&f.shadow()==500&&ResonanceNetwork.view(f.dais).cooldown()==0,"real native fog hides target anddenies allcosts");
            fog.columns(List.of(u.blockPosition()));h.assertTrue(CoordinatedMark.visible(f.peer.actor,u)&&f.pulse(f.dais).equals("success")&&f.shadow()==485,"sameactualbody revealed throughnativefog canbe affected");
            ResonanceNetworkGameTests.near(h,SkillEffects.bonus(u,BuffKind.ATTACK_SPEED),.10,"visible legalbody actualeffect");h.succeed();});}
    private static final class ControlledFog implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final ServerPlayer owner;
        final boolean oldEnabled,oldForce;final double oldSize,oldX,oldZ;final int oldTimer;
        final Map<UUID,Set<ChunkPos>> live,edge,pending,oldLive,oldEdge,oldPending;
        final Map<UUID,Map<ChunkPos,long[]>> masks,oldMasks;
        boolean closed;
        @SuppressWarnings("unchecked") ControlledFog(GameTestHelper h,ServerPlayer owner){
            this.h=h;this.level=h.getLevel();this.owner=owner;oldEnabled=FogOfWarServerEvents.isEnabled();oldForce=level.getGameRules().getBoolean(GameRuleRegistrar.FORCE_FOG);
            oldSize=level.getWorldBorder().getSize();oldX=level.getWorldBorder().getCenterX();oldZ=level.getWorldBorder().getCenterZ();oldTimer=(int)get("ticksUntilUpdate");
            live=(Map<UUID,Set<ChunkPos>>)get("playerLiveChunks");edge=(Map<UUID,Set<ChunkPos>>)get("playerEdgeChunks");pending=(Map<UUID,Set<ChunkPos>>)get("pendingResends");masks=(Map<UUID,Map<ChunkPos,long[]>>)get("playerEdgeMasks");
            oldLive=copySets(live);oldEdge=copySets(edge);oldPending=copySets(pending);oldMasks=copyMasks(masks);
        }
        void install(){
            h.assertTrue(!oldEnabled,"isolated fog profile starts without another fog owner");
            var origin=fixture.origin;level.getGameRules().getRule(GameRuleRegistrar.FORCE_FOG).set(false,level.getServer());
            level.getWorldBorder().setCenter(origin.getX()+16,origin.getZ()+16);level.getWorldBorder().setSize(1280);
            FogOfWarServerEvents.invalidateRtsCache();FogOfWarServerEvents.setEnabled(true);
            h.assertTrue(FogOfWarServerEvents.isEnabled()&&FogOfWarServerEvents.isFogActiveFor(owner),"public fog enable on actual bounded RTS map");
            set("ticksUntilUpdate",1000);columns(List.of());
        }
        void columns(List<BlockPos> positions){
            h.assertTrue((int)get("ticksUntilUpdate")>900,"explicit controlled-cache interval prevents native recomputation, without replacing provider");
            live.put(owner.getUUID(),new HashSet<>());Map<ChunkPos,long[]> selected=new HashMap<>();
            for(BlockPos p:positions){var cp=new ChunkPos(p.getX()>>4,p.getZ()>>4);var bits=selected.computeIfAbsent(cp,k->new long[4]);int index=((p.getX()&15)<<4)|(p.getZ()&15);bits[index>>6]|=1L<<(index&63);}
            masks.put(owner.getUUID(),selected);edge.put(owner.getUUID(),new HashSet<>(selected.keySet()));
        }
        @Override public void close(){if(closed)return;closed=true;
            try {level.getGameRules().getRule(GameRuleRegistrar.FORCE_FOG).set(false,level.getServer());FogOfWarServerEvents.setEnabled(oldEnabled);}
            finally {live.clear();live.putAll(copySets(oldLive));edge.clear();edge.putAll(copySets(oldEdge));pending.clear();pending.putAll(copySets(oldPending));masks.clear();masks.putAll(copyMasks(oldMasks));set("ticksUntilUpdate",oldTimer);FogOfWarServerEvents.invalidateRtsCache();level.getWorldBorder().setCenter(oldX,oldZ);level.getWorldBorder().setSize(oldSize);level.getGameRules().getRule(GameRuleRegistrar.FORCE_FOG).set(oldForce,level.getServer());}
        }
        private static Map<UUID,Set<ChunkPos>> copySets(Map<UUID,Set<ChunkPos>> source){Map<UUID,Set<ChunkPos>> out=new HashMap<>();source.forEach((id,set)->out.put(id,new HashSet<>(set)));return out;}
        private static Map<UUID,Map<ChunkPos,long[]>> copyMasks(Map<UUID,Map<ChunkPos,long[]>> source){Map<UUID,Map<ChunkPos,long[]>> out=new HashMap<>();source.forEach((id,map)->{Map<ChunkPos,long[]> copy=new HashMap<>();map.forEach((p,bits)->copy.put(p,bits.clone()));out.put(id,copy);});return out;}
        private static Object get(String name){try{Field f=FogOfWarServerEvents.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(ReflectiveOperationException e){throw new RuntimeException("native fog fixture field "+name,e);}}
        private static void set(String name,int value){try{Field f=FogOfWarServerEvents.class.getDeclaredField(name);f.setAccessible(true);f.setInt(null,value);}catch(ReflectiveOperationException e){throw new RuntimeException("native fog fixture timer "+name,e);}}
    }
}
