// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Ordinary Anvil storage across two genuine JVMs; no replaced bodies, time rewrite or load hook.
 * Prepare leaves a genuine successful20-tick root and100-tick protection alive at normal shutdown;
 * the read-only disk gate requires savedTime before its ORIGINAL root expiry. Normal verify startup
 * may exceed20: loaded absence is then only no-replay, never a claim of revocation before old20.
 * An original unused caster must finish a new real10-tick windup on the SAME original target BEFORE
 * old100 expires. Root permits the target's ordinary attacks; this is not the old Dread disarm test.
 */
@GameTestHolder("dreamseekers_shadow_hand_grip_skill_restart")
@PrefixGameTestTemplate(false)
public final class ShadowHandGripSkillRestartGameTests {
    private static final String OWNER="GripRestart_Owner",ENEMY="GripRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(102400,90,102400);
    private static final File MANIFEST=new File("shadow-hand-grip-restart-expectations.nbt");
    private static final UUID MS_TECH=UUID.fromString("7a183715-d531-4986-b519-0a675633074e");
    private static final List<String> KEYS=List.of("paidCaster","unusedCaster","oldTarget","ordinaryVictim","ownerReserve","enemyReserve");
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_shadow_hand_grip_skill_restart",timeoutTicks=1450)
    public static void originalBodiesAndPaidClockSurviveButOldRootAndProtectionNeverReplayAndFreshRootStillAllowsOrdinaryAttack(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.shadowHandGrip.test.phase","")) {
            case "prepare" -> prepare(h);
            case "verify" -> verify(h);
            default -> throw new IllegalArgumentException("Unknown shadow-hand-grip restart phase");
        }
    }

    private static void prepare(GameTestHelper h) {
        var level=h.getLevel();
        h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(ENEMY)==null,"fresh isolated normal-save fixture, never reuse consumed verify state");
        var expected=new CompoundTag();expected.putInt("format",1);
        expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        for(int x=ORIGIN.getX()>>4;x<=(ORIGIN.getX()+31)>>4;x++)for(int z=ORIGIN.getZ()>>4;z<=(ORIGIN.getZ()+31)>>4;z++) {
            long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"only new fixture chunk tickets");
            level.setChunkForced(x,z,true);forced.add(key);
        }
        expected.putLongArray("forcedChunks",forced);arena(level);
        for(String name:List.of(OWNER,ENEMY)) {
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));
            ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));player(level,name);
        }
        Map<String,Mob> bodies=new LinkedHashMap<>();
        for(int i=0;i<2;i++) {
            var body=DreamseekersMod.SHADOW_HAND_UNIT.get().create(level);
            owned(h,body,OWNER,19.5,5.5+3*i,9001+i);damage(body,10);
            bodies.put(i==0?"paidCaster":"unusedCaster",body);
        }
        var target=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(level);
        owned(h,target,ENEMY,24.5,5.5,9003);damage(target,10);durable(target);bodies.put("oldTarget",target);
        var victim=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);
        owned(h,victim,OWNER,4.5,25.5,9004);durable(victim);bodies.put("ordinaryVictim",victim);
        for(int i=0;i<2;i++) {
            var reserve=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);
            owned(h,reserve,i==0?OWNER:ENEMY,i==0?4.5:24.5,i==0?5.5:25.5,9005+i);
            bodies.put(i==0?"ownerReserve":"enemyReserve",reserve);
        }
        for(var entry:bodies.entrySet())expected.putUUID(entry.getKey(),entry.getValue().getUUID());
        var paid=(ShadowHandUnit)bodies.get("paidCaster");var unused=(ShadowHandUnit)bodies.get("unusedCaster");
        h.startSequence().thenWaitUntil(()-> {
            for(var body:bodies.values())published(h,body);for(var p:PLAYERS.values())published(h,p);
        }).thenExecute(()-> {
            h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"actual isolated world uses fog off, not fabricated visibility");
            ledger(h);expected.putLong("reservationGameTime",level.getGameTime());
            h.assertTrue(cast(level,paid,target)==SkillResult.SUCCESS&&cd(paid)==440&&cd(unused)==0,"real owner-target reservation immediately pays CD440; original second caster remains unused");
            h.assertTrue(ShadowHandGrip.active(paid)&&windup(paid)==10&&!ShadowHandGrip.hasRoot(target)&&protection(target)==0,"genuine new10-tick reservation has not prematurely rooted or granted protection");
        }).thenWaitUntil(()->h.assertTrue(ShadowHandGrip.hasRoot(target),"wait for actual natural source tick to finish original10-tick windup"))
          .thenExecute(()-> {
            long now=level.getGameTime();int remaining=root(target),protectedRemaining=protection(target);
            long appliedAt=now+remaining-20;
            h.assertTrue(remaining>0&&remaining<=20&&protectedRemaining==remaining+80&&appliedAt==expected.getLong("reservationGameTime")+10,"actual successful root20/protection100 share real application after exact10, recording observed remaining rather than resetting clocks");
            h.assertTrue(!ShadowHandGrip.active(paid)&&windup(paid)==0&&cd(paid)==expected.getLong("reservationGameTime")+440-now&&cd(unused)==0,"completed short windup is gone but exact paid absolute clock remains");
            close(h,target.getHealth(),1000,"grip is root only, not a replacement damage hit");close(h,victim.getHealth(),1000,"no ordinary fixture attack occurred before normal save");
            expected.putLong("rootAppliedAt",appliedAt);expected.putLong("rootExpiresAt",appliedAt+20);expected.putLong("protectionExpiresAt",appliedAt+100);
            expected.putLong("snapshotGameTime",now);expected.putInt("snapshotRootRemaining",remaining);expected.putInt("snapshotProtectionRemaining",protectedRemaining);
            expected.putUUID("permanentMovementUuid",MS_TECH);expected.putUUID("transientMovementUuid",BuffKind.MOVEMENT_SPEED.modifierId);
            for(var entry:bodies.entrySet()) {
                var body=entry.getValue();var unit=(Unit)body;var state=new CompoundTag();
                state.putString("type",EntityType.getKey(body.getType()).toString());state.putString("class",body.getClass().getName());
                state.putString("owner",unit.getOwnerName());state.putInt("scenario",unit.getScenarioRoleIndex());state.putFloat("health",body.getHealth());
                state.putBoolean("noGravity",body.isNoGravity());state.putLong("anchor",unit.getAnchor().asLong());
                state.putDouble("movementBase",body.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue());
                state.putDouble("maxHealthBase",body.getAttribute(Attributes.MAX_HEALTH).getBaseValue());
                h.assertTrue(body.isNoAi()&&body.getActiveEffects().isEmpty()&&body.getTarget()==null&&!unit.getAnchor().equals(BlockPos.ZERO),"declared prepare AI containment, empty attack intent, actual nonzero home and no potion substitute: "+entry.getKey());
                var movement=body.getAttribute(Attributes.MOVEMENT_SPEED);
                h.assertTrue(movement.getModifier(MS_TECH)!=null&&movement.getModifiers().size()==1,"root is not a movement attribute mutation; actual permanent movement research alone exists");
                close(h,movement.getValue(),state.getDouble("movementBase")+.03,"root leaves permanent movement attribute value intact");
                if(body instanceof ShadowHandUnit source) {
                    state.putInt("cooldown",cd(source));state.putLong("revision",source.rts().skills().revision());
                    h.assertTrue(source.distanceTo(target)>source.getAggroRange()&&source.distanceTo(target)<6,"actual home remains inside skill6 but outside unchanged native ordinary auto-aggro range");
                }
                expected.put(entry.getKey()+"State",state);
            }
            h.assertTrue(root(target)==remaining&&protection(target)==protectedRemaining,"actual successful root/protection still live immediately before ordinary stop");ledger(h);
            try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception failure){throw new RuntimeException(failure);}
            System.out.println("DREAMSEEKERS_SHADOW_HAND_GRIP_RESTART_PREPARED time="+now+" reservedAt="+expected.getLong("reservationGameTime")+" appliedAt="+appliedAt+" rootRemaining="+remaining+" protectionRemaining="+protectedRemaining+" paidRemaining="+cd(paid)+" originalUUIDs="+KEYS.stream().map(key->key+":"+bodies.get(key).getUUID()).toList());
            // No root clear, post-root reposition, entity discard, manual save or fake reload.
            // Normal server shutdown must reach real Anvil storage before rootExpiresAt; external gate proves this.
        }).thenSucceed();
    }

    private static void verify(GameTestHelper h) throws Exception {
        var level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"original prepare manifest exists");var expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getInt("format")==1&&expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different actual JVM, never reconstructed combat/reserve bodies");
        h.assertTrue(expected.getLong("rootAppliedAt")==expected.getLong("reservationGameTime")+10&&expected.getLong("rootExpiresAt")==expected.getLong("rootAppliedAt")+20&&expected.getLong("protectionExpiresAt")==expected.getLong("rootAppliedAt")+100,"original10/20/100 deadlines are fixed, never a larger startup window");
        h.assertTrue(expected.getInt("snapshotRootRemaining")>0&&expected.getInt("snapshotRootRemaining")<=20&&expected.getLong("snapshotGameTime")+expected.getInt("snapshotRootRemaining")==expected.getLong("rootExpiresAt")&&expected.getLong("snapshotGameTime")+expected.getInt("snapshotProtectionRemaining")==expected.getLong("protectionExpiresAt"),"genuine live saved snapshot reconciles to original successful deadlines");
        for(long key:expected.getLongArray("forcedChunks")) {
            h.assertTrue(level.getForcedChunks().contains(key),"original forced chunks survive normal save");var c=new ChunkPos(key);level.getChunk(c.x,c.z);
        }
        for(String name:List.of(OWNER,ENEMY))player(level,name);
        Map<String,Mob> bodies=new LinkedHashMap<>();long[] elapsedAtLoad={-1},freshReservedAt={-1},freshAppliedAt={-1},freshRootExpiresAt={-1},freshProtectionExpiresAt={-1};
        h.startSequence().thenWaitUntil(()-> {
            for(String key:KEYS){var body=level.getEntity(expected.getUUID(key));h.assertTrue(body instanceof Mob,"ordinary real loader returns exact original UUID: "+key);published(h,body);}
            for(var p:PLAYERS.values())published(h,p);
        }).thenExecute(()-> {
            long now=level.getGameTime(),elapsed=now-expected.getLong("snapshotGameTime");elapsedAtLoad[0]=elapsed;
            h.assertTrue(elapsed>=0&&now<expected.getLong("protectionExpiresAt"),"old successful100 protection deadline must still be future for an honest noninheritance proof; no startup extension");
            h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"same actual fog-off visibility profile");
            for(String key:KEYS) {
                var body=(Mob)level.getEntity(expected.getUUID(key));bodies.put(key,body);var unit=(Unit)body;var state=expected.getCompound(key+"State");
                h.assertTrue(!body.isNoAi()&&body.isNoGravity()==state.getBoolean("noGravity")&&body.getClass().getName().equals(state.getString("class"))&&EntityType.getKey(body.getType()).toString().equals(state.getString("type")),"observe original native loaded NoAI=false plus exact type/class/gravity BEFORE fixture isolation: "+key);
                h.assertTrue(unit.getOwnerName().equals(state.getString("owner"))&&unit.getScenarioRoleIndex()==state.getInt("scenario")&&unit.getAnchor().equals(BlockPos.of(state.getLong("anchor"))),"original owner/role/nonzero home preserved without restoring a snapshot: "+key);
                h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(body.getUUID())).count()==1,"one actual original registered roster body: "+key);
                close(h,body.getHealth(),state.getFloat("health"),"original HP unchanged by autonomous load attacks or replay: "+key);
                close(h,body.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),state.getDouble("maxHealthBase"),"original max-health base retained: "+key);
                h.assertTrue(!ShadowHandGrip.hasRoot(body)&&root(body)==0&&protection(body)==0&&body.getActiveEffects().isEmpty(),"no old root/protection/native potion replay at ACTUAL observed loaded time: "+key);
                var movement=body.getAttribute(Attributes.MOVEMENT_SPEED);
                close(h,movement.getBaseValue(),state.getDouble("movementBase"),"original movement base retained: "+key);
                h.assertTrue(movement.getModifier(MS_TECH)!=null&&movement.getModifiers().size()==1&&movement.getModifier(BuffKind.MOVEMENT_SPEED.modifierId)==null,"only actual permanent movement research remains, no transient attribute state: "+key);
                close(h,movement.getValue(),state.getDouble("movementBase")+.03,"original permanent movement remains effective: "+key);
                if(body instanceof ShadowHandUnit source)h.assertTrue(!ShadowHandGrip.active(source)&&windup(source)==0&&cd(source)==Math.max(0,state.getInt("cooldown")-elapsed)&&source.rts().skills().revision()>=state.getLong("revision"),"no windup replay and exact paid/unused original absolute clocks: "+key);
            }
            ledger(h);
            System.out.println("SHADOW_HAND_GRIP_RESTART_LOADED_BOUNDARY elapsed="+elapsed+" observedBeforeOldRootDeadline="+(now<expected.getLong("rootExpiresAt"))+" observedBeforeOldProtectionDeadline=true oldRootAbsenceClaim=no_replay_at_observed_time_not_early_revocation");
            // Only AFTER all original native AI/HP/owner/clock/permanent observations contain unrelated AI.
            for(var body:bodies.values())body.setNoAi(true);
            var paid=(ShadowHandUnit)bodies.get("paidCaster");var target=(ShadowGhostUnit)bodies.get("oldTarget");var victim=bodies.get("ordinaryVictim");
            h.assertTrue(cast(level,paid,target)==SkillResult.COOLDOWN,"original paid source cannot receive a free rearm on reload");
            // Exactly one controlled pre-root placement of the original victim. Target and both
            // original casters retain their original positions/homes throughout both root casts.
            position(victim,target.position().add(1.1,0,0));order(h,target,UnitAction.ATTACK,victim);
        }).thenWaitUntil(()->meleeReady(h,(ShadowGhostUnit)bodies.get("oldTarget"),bodies.get("ordinaryVictim")))
          .thenExecute(()-> {
            var unused=(ShadowHandUnit)bodies.get("unusedCaster");var target=bodies.get("oldTarget");freshReservedAt[0]=level.getGameTime();
            h.assertTrue(level.getGameTime()<expected.getLong("protectionExpiresAt"),"fresh reservation begins before original protection expiry");
            h.assertTrue(cd(unused)==0&&cast(level,unused,target)==SkillResult.SUCCESS&&cd(unused)==440&&ShadowHandGrip.active(unused)&&windup(unused)==10,"original unused caster makes new real targeted10-tick reservation and pays440 against SAME original target");
            h.assertTrue(root(target)==0&&protection(target)==0,"new reservation has not prematurely installed root/protection");
        }).thenWaitUntil(()->h.assertTrue(ShadowHandGrip.hasRoot(bodies.get("oldTarget")),"natural original source ticks must actually complete fresh windup"))
          .thenExecute(()-> {
            var target=(ShadowGhostUnit)bodies.get("oldTarget");var victim=bodies.get("ordinaryVictim");var unused=(ShadowHandUnit)bodies.get("unusedCaster");
            long now=level.getGameTime();int remaining=root(target);freshAppliedAt[0]=now+remaining-20;freshRootExpiresAt[0]=freshAppliedAt[0]+20;freshProtectionExpiresAt[0]=freshAppliedAt[0]+100;
            h.assertTrue(remaining>0&&remaining<=20&&protection(target)==remaining+80&&freshAppliedAt[0]==freshReservedAt[0]+10&&!ShadowHandGrip.active(unused)&&windup(unused)==0,"fresh real10 completes to independent20/100 clocks without a substituted effect");
            h.assertTrue(now<expected.getLong("protectionExpiresAt")&&freshAppliedAt[0]<expected.getLong("protectionExpiresAt"),"actual new root SUCCESS, including its10-tick windup, occurs BEFORE old100 expires; no old protection inheritance");
            close(h,target.getHealth(),expected.getCompound("oldTargetState").getFloat("health"),"actual fresh root itself is damage-free");
            close(h,hit(h,target,victim),10,"ROOTED original target's real selected installed ordinary goal still causes ten HP; root is not disarm");
            h.assertTrue(ShadowHandGrip.hasRoot(target)&&level.getGameTime()<freshRootExpiresAt[0],"ordinary positive happened DURING genuine root, never after natural expiry");
            close(h,victim.getHealth(),990,"exact first actual ordinary HP receipt while rooted");ledger(h);
            System.out.println("SHADOW_HAND_GRIP_RESTART_FRESH_ROOT_BEFORE_OLD100 appliedAt="+freshAppliedAt[0]+" observedAt="+now+" oldProtectionExpiresAt="+expected.getLong("protectionExpiresAt")+" rootedOrdinaryHp=10");
        }).thenWaitUntil(()->h.assertTrue(level.getGameTime()>=freshRootExpiresAt[0],"wait for actual new20 root deadline, never rewrite time"))
          .thenExecute(()-> {
            var target=bodies.get("oldTarget");h.assertTrue(level.getGameTime()==freshRootExpiresAt[0]&&!ShadowHandGrip.hasRoot(target)&&root(target)==0&&protection(target)==80,"fresh root expires exactly20 while genuine100 protection remains80");
        }).thenWaitUntil(()->meleeReady(h,(ShadowGhostUnit)bodies.get("oldTarget"),bodies.get("ordinaryVictim")))
          .thenExecute(()-> {
            var target=(ShadowGhostUnit)bodies.get("oldTarget");var victim=bodies.get("ordinaryVictim");
            h.assertTrue(!ShadowHandGrip.hasRoot(target)&&level.getGameTime()>=freshRootExpiresAt[0],"second ordinary positive is after root, at actual native CD/iframe readiness");
            close(h,hit(h,target,victim),10,"same original installed ordinary entry remains legal after root expiration");close(h,victim.getHealth(),980,"exact two ordinary positives, no skill damage or bonus matrix added");ledger(h);
        }).thenWaitUntil(()->h.assertTrue(level.getGameTime()>=freshProtectionExpiresAt[0]+1,"natural observation passes both original and fresh protection deadlines"))
          .thenExecute(()-> {
            h.assertTrue(level.getGameTime()==freshProtectionExpiresAt[0]+1&&level.getGameTime()>expected.getLong("protectionExpiresAt"),"crossed original and new100 deadlines without altering clocks");
            for(String key:KEYS) {
                var body=bodies.get(key);h.assertTrue(!ShadowHandGrip.hasRoot(body)&&root(body)==0&&protection(body)==0,"no delayed root/protection replay: "+key);
                close(h,body.getHealth(),key.equals("ordinaryVictim")?980:expected.getCompound(key+"State").getFloat("health"),"only two declared ordinary HP writes changed bodies: "+key);
                var movement=body.getAttribute(Attributes.MOVEMENT_SPEED);h.assertTrue(movement.getModifier(MS_TECH)!=null&&movement.getModifiers().size()==1,"permanent movement unchanged after new temporary control expires");
                if(body instanceof ShadowHandUnit source) {
                    long expiry=(key.equals("paidCaster")?expected.getLong("reservationGameTime"):freshReservedAt[0])+440;
                    h.assertTrue(!ShadowHandGrip.active(source)&&windup(source)==0&&cd(source)==Math.max(0,expiry-level.getGameTime()),"both genuine paid absolute clocks stay exact, no reload or expiry refund");
                }
            }
            ledger(h);System.out.println("DREAMSEEKERS_SHADOW_HAND_GRIP_RESTART_UUID_CD_NO_ROOT_PROTECTION_REPLAY_FRESH_ROOT_ORDINARY_ALLOWED_PASS elapsedAtLoad="+elapsedAtLoad[0]+" freshAppliedAt="+freshAppliedAt[0]+" originalRootExpiresAt="+expected.getLong("rootExpiresAt")+" originalProtectionExpiresAt="+expected.getLong("protectionExpiresAt")+" originalUUIDs="+KEYS.stream().map(key->bodies.get(key).getUUID()).toList());
            for(var body:bodies.values())body.discard();
            for(var p:PLAYERS.values()){membership(p,false);p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}PLAYERS.clear();
            PlayerServerEvents.players.removeIf(p->List.of(OWNER,ENEMY).contains(p.getGameProfile().getName()));
            PlayerServerEvents.rtsPlayers.removeIf(p->List.of(OWNER,ENEMY).contains(p.name));
            ResourcesServerEvents.resourcesList.removeIf(r->List.of(OWNER,ENEMY).contains(r.ownerName));
            for(long key:expected.getLongArray("forcedChunks")){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}
        }).thenSucceed();
    }

    private static SkillResult cast(ServerLevel level,ShadowHandUnit source,LivingEntity target) {
        return new SkillNetwork.Cast(new SkillRequest(UnitSkill.SHADOW_HAND_GRIP,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID())).execute(player(level,OWNER));
    }
    private static int cd(ShadowHandUnit source){return source.rts().skills().cooldownTicks(UnitSkill.SHADOW_HAND_GRIP);}
    private static int windup(ShadowHandUnit source){return ShadowHandGrip.remainingWindupTicks(source);}
    private static int root(LivingEntity target){return ShadowHandGrip.remainingRootTicks(target);}
    private static int protection(LivingEntity target){return ShadowHandGrip.remainingRecontrolTicks(target);}
    /** Read-only readiness observation. Real native entity ticks alone advance CD and iframe. */
    private static void meleeReady(GameTestHelper h,ShadowGhostUnit source,LivingEntity target) {
        h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(source.getId())&&source.getTarget()==target&&source.getTargetGoal().getTarget()==target&&source.getTargetGoal().forced,"actual authenticated ATTACK dispatcher selected original target");
        h.assertTrue(source.isNoAi()&&!source.isVehicle()&&source.getAttackGoal() instanceof TeamSafeMeleeAttackGoal,"controlled installed original goal, no autonomous/vehicle duplicate tick");
        h.assertTrue(nativeCooldown(source)<=0&&target.invulnerableTime==0,"native ordinary CD and target iframe actually reached zero; neither is ever rewritten");
    }
    private static double hit(GameTestHelper h,ShadowGhostUnit source,LivingEntity target) {
        meleeReady(h,source,target);
        float width=source.getBbWidth()+source.getBonusMeleeRange(),targetWidth=target.getBbWidth()+((Unit)target).getBonusMeleeRangeForAttackers();
        h.assertTrue(source.distanceToSqr(target)<=width*2f*width*2f+targetWidth&&!source.getBoundingBox().intersects(target.getBoundingBox())&&source.hasLineOfSight(target),"real fixed nonoverlapping original native reach and LOS, no synthetic distance");
        float before=target.getHealth();source.getAttackGoal().tick();
        h.assertTrue(nativeCooldown(source)>0,"one actual installed-goal tick consumes its own ready ordinary cycle");
        return before-target.getHealth();
    }
    private static int nativeCooldown(ShadowGhostUnit source) {
        try {var field=AbstractMeleeAttackUnitGoal.class.getDeclaredField("ticksUntilNextAttack");field.setAccessible(true);return field.getInt(source.getAttackGoal());}
        catch(ReflectiveOperationException error){throw new RuntimeException("read-only original ordinary cooldown observation",error);}
    }
    /** Same actual authenticated command packet/dispatcher as focused tests, not direct target writes. */
    private static void order(GameTestHelper h,Mob source,UnitAction action,Entity target) {
        try {
            String owner=((Unit)source).getOwnerName();var sender=player(h.getLevel(),owner);
            var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.setListener(sender.connection);
            var constructor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);constructor.setAccessible(true);
            var context=constructor.newInstance(connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);
            h.assertTrue(context.getSender()==sender,"actual Forge command context resolves exact online owner");
            h.assertTrue(new com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket(owner,action,target==null?-1:target.getId(),new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO,false).handle(()->context),"actual authenticated native order accepted");
        }catch(ReflectiveOperationException error){throw new RuntimeException("actual native command context",error);}
    }
    private static void owned(GameTestHelper h,Mob body,String owner,double x,double z,int role) {
        var unit=(Unit)body;unit.setOwnerName(owner);unit.setScenarioRoleIndex(role);unit.setOnDeathCommand("");
        body.setNoAi(true);body.setPersistenceRequired();body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);
        neutral(body);position(body,new Vec3(ORIGIN.getX()+x,ORIGIN.getY()+1,ORIGIN.getZ()+z));unit.setAnchor(body.blockPosition().below());
        body.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new AttributeModifier(MS_TECH,"actual permanent restart movement research",.03,AttributeModifier.Operation.ADDITION));
        h.assertTrue(h.getLevel().addFreshEntity(body),"original registered body enters ordinary world storage");
    }
    private static void damage(LivingEntity body,double amount) {
        body.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);
        if(body.getAttribute(Attributes.ATTACK_DAMAGE)!=null)body.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);
        if(body.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get())!=null)body.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);
    }
    private static void durable(LivingEntity body){body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);body.setHealth(1000);}
    private static void neutral(LivingEntity body) {
        body.getAttribute(Attributes.ARMOR).setBaseValue(0);body.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);body.setAbsorptionAmount(0);
        for(var attribute:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(body.getAttribute(attribute)!=null)body.getAttribute(attribute).setBaseValue(0);
    }
    private static void published(GameTestHelper h,Entity body){h.assertTrue(body.tickCount>3&&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body,"real original ID/UUID publication and natural ticks: "+body.getUUID());}
    private static void position(Entity body,Vec3 pos){body.moveTo(pos.x,pos.y,pos.z);body.setDeltaMovement(Vec3.ZERO);}
    private static BlockPos at(int x,int y,int z){return ORIGIN.offset(x,y,z);}
    private static FakePlayer player(ServerLevel level,String name) {
        return PLAYERS.computeIfAbsent(name,key->{var p=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));p.connection=new SilentHandler(p);position(p,Vec3.atBottomCenterOf(at(key.equals(OWNER)?2:29,1,28)));level.addNewPlayer(p);PlayerServerEvents.players.add(p);membership(p,true);return p;});
    }
    /** Real local online-owner identity fixture, not a socket login or weakened visibility check. */
    @SuppressWarnings("unchecked") private static void membership(ServerPlayer player,boolean present) {
        try {
            var list=player.serverLevel().getServer().getPlayerList();
            var players=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
            var byId=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            players.setAccessible(true);byId.setAccessible(true);
            List<ServerPlayer> actual=(List<ServerPlayer>)players.get(list);Map<UUID,ServerPlayer> ids=(Map<UUID,ServerPlayer>)byId.get(list);
            if(present){if(!actual.contains(player))actual.add(player);ids.put(player.getUUID(),player);}
            else{actual.remove(player);ids.remove(player.getUUID(),player);}
        }catch(ReflectiveOperationException error){throw new RuntimeException("real local PlayerList membership",error);}
    }
    private static void ledger(GameTestHelper h) {
        for(String owner:List.of(OWNER,ENEMY)) {
            var ownerPlayer=PLAYERS.get(owner);var online=h.getLevel().getServer().getPlayerList();
            h.assertTrue(ownerPlayer!=null&&online.getPlayer(ownerPlayer.getUUID())==ownerPlayer&&online.getPlayerByName(owner)==ownerPlayer&&online.getPlayers().stream().filter(p->p==ownerPlayer).count()==1,"actual single online owner UUID/name/list membership, not a fake visibility bypass: "+owner);
            var matching=ResourcesServerEvents.resourcesList.stream().filter(e->e.ownerName.equals(owner)).toList();
            h.assertTrue(matching.size()==1&&PlayerServerEvents.getRTSPlayer(owner)!=null,"one original bank and original living-owner RTS record, never recreated: "+owner);var r=matching.get(0);
            h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456&&r.foodToAdd==0&&r.woodToAdd==0&&r.oreToAdd==0&&r.emeraldToAdd==0,"strict original balances and pending zero, no shadow-energy refund or fixture restoration: "+owner);
            for(var entity:UnitServerEvents.getAllUnits())if(entity instanceof Unit unit&&unit.getOwnerName().equals(owner))h.assertTrue(unit.getItems().isEmpty(),"declared actual original cargo stays empty: "+entity.getUUID());
        }
        System.out.println("SHADOW_HAND_GRIP_RESTART_LEDGER time="+h.getLevel().getGameTime()+" banks=123,234,345,456 pending=0 cargo=empty");
    }
    private static void arena(ServerLevel level) {
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(at(x,9,z),Blocks.STONE.defaultBlockState(),2);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+32,level.getMaxBuildHeight(),ORIGIN.getZ()+32)).forEach(Entity::discard);
        for(int x=0;x<32;x++)for(int z=0;z<32;z++) {
            level.setBlock(at(x,0,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<9;y++)level.setBlock(at(x,y,z),(x==0||x==31||z==0||z==31||z==16?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        }
        level.getEntitiesOfClass(ItemEntity.class,new AABB(ORIGIN,ORIGIN.offset(32,10,32))).forEach(Entity::discard);
    }
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
    private static final class SilentHandler extends ServerGamePacketListenerImpl {
        SilentHandler(ServerPlayer p){super(p.serverLevel().getServer(),new SilentConnection(),p);}
        @Override public void tick(){} @Override public void send(net.minecraft.network.protocol.Packet<?> p){}
        @Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}
    }
    private static final class SilentConnection extends net.minecraft.network.Connection {
        SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}
        @Override public void tick(){} @Override public void send(net.minecraft.network.protocol.Packet<?> p){}
        @Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}
    }
}
