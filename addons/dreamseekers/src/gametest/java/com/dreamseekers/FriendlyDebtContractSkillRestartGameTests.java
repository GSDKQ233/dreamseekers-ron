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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Two different JVMs and real ordinary Anvil storage, never manually loaded replacement bodies.
 * The external read-only gate must find the original bodies/CD before the genuine 120-tick mark
 * deadline. Verify also requires gameTime < that original deadline before observing absence or
 * probing old contributors. Ordinary-goal probes are controlled NoAI entries, not natural cadence.
 * No saved-active chase AS is claimed: only mark+count2 are deliberately active at normal shutdown.
 */
@GameTestHolder("dreamseekers_friendly_debt_contract_skill_restart")
@PrefixGameTestTemplate(false)
public final class FriendlyDebtContractSkillRestartGameTests {
    private static final String OWNER="DebtRestart_Owner", ENEMY="DebtRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(100352,90,100352);
    private static final File MANIFEST=new File("friendly-debt-contract-restart-expectations.nbt");
    private static final UUID APS_TECH=UUID.fromString("c7489dba-a134-4269-9b26-f7cbf89b284f");
    private static final List<String> CONTRIBUTORS=List.of("firstContributor","secondContributor","thirdContributor");
    private static final List<String> KEYS=List.of("paidCaster","unusedCaster","firstContributor","secondContributor","thirdContributor","oldTarget","ownerReserve","enemyReserve");
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();
    private static final SettlementProbe PROBE=new SettlementProbe();
    private static boolean listening;

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_friendly_debt_contract_skill_restart",timeoutTicks=1450)
    public static void trueRestartKeepsOriginalBodiesAndPaidClockButNeverReplaysSavedTwoContributorContract(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.friendlyDebtContract.test.phase","")) {
            case "prepare" -> prepare(h);
            case "verify" -> verify(h);
            default -> throw new IllegalArgumentException("Unknown friendly-debt restart phase");
        }
    }

    private static void prepare(GameTestHelper h) {
        var level=h.getLevel();
        h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(ENEMY)==null,"fresh isolated normal-save fixture, never reuse a consumed verify world");
        var expected=new CompoundTag();expected.putInt("format",1);
        expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        for(int x=ORIGIN.getX()>>4;x<=(ORIGIN.getX()+31)>>4;x++)for(int z=ORIGIN.getZ()>>4;z<=(ORIGIN.getZ()+31)>>4;z++) {
            long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"only new fixture tickets");
            level.setChunkForced(x,z,true);forced.add(key);
        }
        expected.putLongArray("forcedChunks",forced);arena(level);
        for(String name:List.of(OWNER,ENEMY)) {
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));
            ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));player(level,name);
        }
        Map<String,Mob> bodies=new LinkedHashMap<>();
        for(int i=0;i<2;i++) {
            var body=DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(level);
            owned(h,body,OWNER,16.5,5.5+3*i,8001+i);damage(body,10);
            // Explicit fixture containment, not the native Friendly range: mark service still has r10.
            body.getAttribute(AttributeRegistrar.ATTACK_RANGE.get()).setBaseValue(2);
            bodies.put(i==0?"paidCaster":"unusedCaster",body);
        }
        for(int i=0;i<3;i++) {
            var body=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(level);
            owned(h,body,OWNER,i==1?25.6:24.5,i==0?5.4:i==1?6.5:7.6,8003+i);
            body.setAnchor(at(4,0,5+3*i));damage(body,10);bodies.put(CONTRIBUTORS.get(i),body);
        }
        var target=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);
        owned(h,target,ENEMY,24.5,6.5,8006);durable(target);bodies.put("oldTarget",target);
        for(int i=0;i<2;i++) {
            var reserve=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);
            owned(h,reserve,i==0?OWNER:ENEMY,i==0?4.5:24.5,25.5,8007+i);
            bodies.put(i==0?"ownerReserve":"enemyReserve",reserve);
        }
        for(var entry:bodies.entrySet())expected.putUUID(entry.getKey(),entry.getValue().getUUID());
        var seq=h.startSequence().thenWaitUntil(()-> {
            for(var body:bodies.values())published(h,body);for(var p:PLAYERS.values())published(h,p);
        }).thenExecute(()-> {
            h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"actual isolated restart world uses fog off, no synthetic visibility provider");
            ledger(h);observe(target);
            for(String key:CONTRIBUTORS)order(h,bodies.get(key),UnitAction.ATTACK,target);
        }).thenWaitUntil(()-> {
            for(String key:CONTRIBUTORS)meleeReady(h,(ShadowGhostUnit)bodies.get(key),target);
        }).thenExecute(()-> {
            var paid=(FriendlyGhostUnit)bodies.get("paidCaster");var unused=(FriendlyGhostUnit)bodies.get("unusedCaster");
            expected.putLong("castGameTime",level.getGameTime());expected.putLong("markExpiresAt",level.getGameTime()+120);
            h.assertTrue(cast(level,paid,target)==SkillResult.SUCCESS&&cd(paid)==400&&cd(unused)==0,"one authenticated actual targeted cast pays CD400 while the other original caster is unused");
            h.assertTrue(mark(target)==120&&count(target)==0,"genuine untouched duration120 starts with zero UUID receipts");
        });
        for(int i=0;i<2;i++) {
            final int contributor=i;
            seq.thenWaitUntil(()->meleeReady(h,(ShadowGhostUnit)bodies.get(CONTRIBUTORS.get(contributor)),target)).thenExecute(()-> {
                close(h,hit(h,(ShadowGhostUnit)bodies.get(CONTRIBUTORS.get(contributor)),target),10,"original installed goal "+(contributor+1)+" causes actual ordinary ten HP after native CD/iframe expiry");
                h.assertTrue(mark(target)>0&&count(target)==contributor+1&&PROBE.settlements==0,"live real mark records this completed distinct ordinary UUID, never an injected counter");
            });
        }
        seq.thenExecute(()-> {
            close(h,target.getHealth(),980,"two genuinely native-ready distinct ordinary hits and no settlement before save");
            // One declared pre-save relocation to safe original homes, never per-hit teleporting.
            // Initial attack stances were real; normal loaded AI must not auto-hit before observations.
            for(String key:CONTRIBUTORS)home(bodies.get(key));
            for(var body:bodies.values())order(h,body,UnitAction.STOP,null);
        }).thenWaitUntil(()-> {
            for(var body:bodies.values())h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(body.getId())&&body.getTarget()==null,"actual STOP dispatcher clears pending/attack intent before normal save");
        }).thenExecute(()-> {
            var paid=(FriendlyGhostUnit)bodies.get("paidCaster");
            int remaining=mark(target);long now=level.getGameTime();
            h.assertTrue(remaining>0&&remaining<120&&remaining==expected.getLong("markExpiresAt")-now&&count(target)==2,"native readiness and STOP processing consumed actual time; save uses observed remaining without imposing an artificial20-tick gap");
            expected.putLong("snapshotGameTime",now);expected.putInt("snapshotRemaining",remaining);expected.putInt("snapshotContributors",count(target));
            expected.putUUID("permanentApsUuid",APS_TECH);expected.putUUID("transientApsUuid",BuffKind.ATTACK_SPEED.modifierId);
            for(var entry:bodies.entrySet()) {
                var body=entry.getValue();var unit=(Unit)body;var state=new CompoundTag();
                state.putString("type",EntityType.getKey(body.getType()).toString());state.putString("class",body.getClass().getName());
                state.putString("owner",unit.getOwnerName());state.putInt("scenario",unit.getScenarioRoleIndex());state.putFloat("health",body.getHealth());
                state.putBoolean("noGravity",body.isNoGravity());state.putLong("anchor",unit.getAnchor().asLong());
                state.putDouble("apsBase",body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue());
                state.putDouble("maxHealthBase",body.getAttribute(Attributes.MAX_HEALTH).getBaseValue());
                state.putDouble("attackRangeBase",body.getAttribute(AttributeRegistrar.ATTACK_RANGE.get()).getBaseValue());
                h.assertTrue(body.isNoAi()&&body.getActiveEffects().isEmpty()&&body.getTarget()==null&&!unit.getAnchor().equals(BlockPos.ZERO),"declared AI containment, empty attack intent, original nonzero home and no native potion substitution: "+entry.getKey());
                h.assertTrue(body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(APS_TECH)!=null,"actual permanent APS research exists before normal save");
                close(h,FriendlyDebtContract.pursuitBonus(body),0,"no saved-active chase AS claimed by this controlled restart fixture");
                if(body instanceof FriendlyGhostUnit source) {
                    state.putInt("cooldown",cd(source));state.putLong("revision",source.rts().skills().revision());
                    h.assertTrue(source.distanceTo(target)>source.getAttackRange()&&source.distanceTo(target)<10,"declared range2 containment leaves the real mark r10 valid");
                }
                expected.put(entry.getKey()+"State",state);
            }
            h.assertTrue(mark(target)==remaining&&count(target)==2&&cd(paid)==400-(now-expected.getLong("castGameTime")),"live actual remaining/count2/paid absolute clock still intact immediately before normal stop");ledger(h);
            try {NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception failure){throw new RuntimeException(failure);}
            System.out.println("DREAMSEEKERS_FRIENDLY_DEBT_RESTART_PREPARED time="+level.getGameTime()+" castAt="+expected.getLong("castGameTime")+" remaining="+remaining+" count2 paidRemaining="+cd(paid)+" originalUUIDs="+KEYS.stream().map(key->key+":"+bodies.get(key).getUUID()).toList());
            // No transient clear, entity discard, fake reload or manual save: normal server stop owns persistence.
        }).thenSucceed();
    }

    private static void verify(GameTestHelper h) throws Exception {
        var level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"original prepare expectation manifest exists");var expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getInt("format")==1&&expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different real JVM, never test-deserialized replacement bodies");
        h.assertTrue(expected.getInt("snapshotRemaining")>0&&expected.getInt("snapshotRemaining")<120&&expected.getInt("snapshotContributors")==2&&expected.getLong("markExpiresAt")==expected.getLong("castGameTime")+120&&expected.getLong("markExpiresAt")==expected.getLong("snapshotGameTime")+expected.getInt("snapshotRemaining"),"actual prepare observed live count2 and genuinely reduced remaining time; no enlarged restart window");
        for(long key:expected.getLongArray("forcedChunks")) {
            h.assertTrue(level.getForcedChunks().contains(key),"original forced chunks retained by normal save");var c=new ChunkPos(key);level.getChunk(c.x,c.z);
        }
        for(String name:List.of(OWNER,ENEMY))player(level,name);
        Map<String,Mob> bodies=new LinkedHashMap<>();long[] elapsedAtLoad={-1},freshAt={-1};float[] finalTargetHp={-1};
        var seq=h.startSequence().thenWaitUntil(()-> {
            for(String key:KEYS) {var body=level.getEntity(expected.getUUID(key));h.assertTrue(body instanceof Mob,"ordinary world loader returns original persisted UUID: "+key);published(h,body);}
            for(var p:PLAYERS.values())published(h,p);
        }).thenExecute(()-> {
            long elapsed=level.getGameTime()-expected.getLong("snapshotGameTime");elapsedAtLoad[0]=elapsed;
            h.assertTrue(elapsed>=0&&level.getGameTime()<expected.getLong("markExpiresAt"),"actual original mark deadline is still future before all absence/replay observations, never natural-expiry false positive: elapsed="+elapsed);
            h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"same actual fog-off profile after normal restart");
            for(String key:KEYS) {
                var body=(Mob)level.getEntity(expected.getUUID(key));bodies.put(key,body);var unit=(Unit)body;var state=expected.getCompound(key+"State");
                h.assertTrue(!body.isNoAi()&&body.isNoGravity()==state.getBoolean("noGravity")&&body.getClass().getName().equals(state.getString("class"))&&EntityType.getKey(body.getType()).toString().equals(state.getString("type")),"observe native loaded NoAI=false and exact original gravity/type/class BEFORE fixture isolation: "+key);
                h.assertTrue(unit.getOwnerName().equals(state.getString("owner"))&&unit.getScenarioRoleIndex()==state.getInt("scenario")&&unit.getAnchor().equals(BlockPos.of(state.getLong("anchor"))),"original owner, role and nonzero anchor retained without recreation: "+key);
                h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(body.getUUID())).count()==1,"exactly one actual original roster body: "+key);
                close(h,body.getHealth(),state.getFloat("health"),"original HP unchanged by autonomous restart attacks/replayed settlement: "+key);
                close(h,body.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),state.getDouble("maxHealthBase"),"original max-health base retained: "+key);
                close(h,body.getAttribute(AttributeRegistrar.ATTACK_RANGE.get()).getBaseValue(),state.getDouble("attackRangeBase"),"declared original range containment retained: "+key);
                h.assertTrue(mark(body)==0&&count(body)==0&&body.getActiveEffects().isEmpty(),"old transient mark/count absent before original deadline; no potion substitution: "+key);
                close(h,FriendlyDebtContract.pursuitBonus(body),0,"no old transient chase-AS contribution");
                var aps=body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get());
                close(h,aps.getBaseValue(),state.getDouble("apsBase"),"original APS base retained: "+key);
                h.assertTrue(aps.getModifier(BuffKind.ATTACK_SPEED.modifierId)==null&&aps.getModifier(APS_TECH)!=null&&aps.getModifiers().size()==1,"only actual permanent APS research survives; transient UUID absent: "+key);
                close(h,aps.getValue(),state.getDouble("apsBase")+.1,"permanent additive APS remains effective: "+key);
                if(body instanceof FriendlyGhostUnit source)h.assertTrue(cd(source)==Math.max(0,state.getInt("cooldown")-elapsed)&&source.rts().skills().revision()>=state.getLong("revision"),"paid/unused original absolute clocks and replay revision retained: "+key);
            }
            ledger(h);
            // Only AFTER native loaded identity/HP/NoAI/CD/permanent observations isolate unrelated AI.
            for(var body:bodies.values())body.setNoAi(true);
            var paid=(FriendlyGhostUnit)bodies.get("paidCaster");var unused=(FriendlyGhostUnit)bodies.get("unusedCaster");var target=bodies.get("oldTarget");observe(target);
            h.assertTrue(cast(level,paid,target)==SkillResult.COOLDOWN,"original paid source cannot regain a free postload cast");
            // A single post-observation placement into three real nonoverlapping attack stances.
            // No original saved field/HP/CD is restored and no position is changed between hits.
            for(int i=0;i<3;i++) {
                var source=bodies.get(CONTRIBUTORS.get(i));
                position(source,target.position().add(i==1?1.1:0,0,i==0?-1.1:i==1?0:1.1));
                order(h,source,UnitAction.ATTACK,target);
            }
        });
        // Exactly the TWO previously contributing UUIDs are old-history negatives. The third body
        // had not contributed before save and is reserved for the fresh three-original-source proof.
        for(int i=0;i<2;i++) {
            final int contributor=i;
            seq.thenWaitUntil(()->meleeReady(h,(ShadowGhostUnit)bodies.get(CONTRIBUTORS.get(contributor)),bodies.get("oldTarget"))).thenExecute(()-> {
                var target=bodies.get("oldTarget");
                h.assertTrue(level.getGameTime()<expected.getLong("markExpiresAt"),"every old-history actual ordinary probe must precede original cast+120; slow startup fails, never extends deadline");
                close(h,hit(h,(ShadowGhostUnit)bodies.get(CONTRIBUTORS.get(contributor)),target),10,"previous original contributor's new naturally-ready ordinary hit cannot replay old history");
                h.assertTrue(mark(target)==0&&count(target)==0&&PROBE.settlements==0,"old mark/count cannot be inherited or rebuilt without a fresh real cast");
            });
        }
        seq.thenExecute(()-> {
            var unused=(FriendlyGhostUnit)bodies.get("unusedCaster");var target=bodies.get("oldTarget");
            close(h,target.getHealth(),expected.getCompound("oldTargetState").getFloat("health")-20,"two previously contributing UUIDs cause only new ordinary HP, no replay");
            h.assertTrue(level.getGameTime()<expected.getLong("markExpiresAt"),"BOTH old-contributor replay probes finished before genuine original cast+120 deadline");
            freshAt[0]=level.getGameTime();
            h.assertTrue(cd(unused)==0&&cast(level,unused,target)==SkillResult.SUCCESS&&cd(unused)==400&&mark(target)==120&&count(target)==0,"original unused caster makes fresh authenticated r10 cast on SAME original target with empty new history");
        });
        for(int i=0;i<3;i++) {
            final int contributor=i;
            seq.thenWaitUntil(()->meleeReady(h,(ShadowGhostUnit)bodies.get(CONTRIBUTORS.get(contributor)),bodies.get("oldTarget"))).thenExecute(()-> {
                var target=bodies.get("oldTarget");var unused=(FriendlyGhostUnit)bodies.get("unusedCaster");
                h.assertTrue(level.getGameTime()<freshAt[0]+120,"fresh three-source actual ordinary proof stays inside its own genuine120 deadline");
                close(h,hit(h,(ShadowGhostUnit)bodies.get(CONTRIBUTORS.get(contributor)),target),contributor==2?20:10,"fresh naturally-ready original contributor "+(contributor+1)+" has ordinary HP plus only the third independent AD10 settlement");
                if(contributor<2)h.assertTrue(mark(target)==freshAt[0]+120-level.getGameTime()&&count(target)==contributor+1&&PROBE.settlements==0,"fresh history does not inherit old contributors or settle early");
                else h.assertTrue(mark(target)==0&&count(target)==0&&PROBE.settlements==1&&unused.getUUID().equals(PROBE.caster)&&Math.abs(PROBE.amount-10)<.001&&PROBE.iframeAtAttack>0,"third complete ordinary goal consumes mark once and emits separate actual skill attack from original new caster while third ordinary iframe is untouched");
            });
        }
        seq.thenExecute(()-> {
            var target=bodies.get("oldTarget");
            close(h,target.getHealth(),expected.getCompound("oldTargetState").getFloat("health")-60,"five real ordinary hits plus exactly one independent AD10 settlement");
            h.assertTrue(target.invulnerableTime==20,"third ordinary and independent settlement leave native iframe20; fixture never writes iframe or cooldown");
            finalTargetHp[0]=target.getHealth();ledger(h);
            System.out.println("FRIENDLY_DEBT_RESTART_LOADED_BOUNDARY elapsed="+elapsedAtLoad[0]+" savedRemaining="+expected.getInt("snapshotRemaining")+" oldTwoContributorProbesBeforeOriginalDeadline=true freshCastAt="+freshAt[0]+" freshActualSettlementCount="+PROBE.settlements);
        }).thenWaitUntil(()->h.assertTrue(level.getGameTime()>=freshAt[0]+121,"naturally wait beyond fresh nominal120 deadline, never alter game time"))
          .thenExecute(()-> {
            h.assertTrue(level.getGameTime()==freshAt[0]+121&&level.getGameTime()>expected.getLong("markExpiresAt"),"natural observation crosses original and fresh nominal120 deadlines");
            for(String key:KEYS) {
                var body=bodies.get(key);h.assertTrue(mark(body)==0&&count(body)==0,"no delayed old/fresh mark or historical counter replay: "+key);
                close(h,body.getHealth(),key.equals("oldTarget")?finalTargetHp[0]:expected.getCompound(key+"State").getFloat("health"),"only declared ordinary+one settlement changed actual HP: "+key);
                h.assertTrue(body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(APS_TECH)!=null&&body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(BuffKind.ATTACK_SPEED.modifierId)==null,"permanent APS retained, no delayed transient modifier: "+key);
                if(body instanceof FriendlyGhostUnit source) {
                    long expiry=key.equals("paidCaster")?expected.getLong("castGameTime")+400:freshAt[0]+400;
                    h.assertTrue(cd(source)==Math.max(0,expiry-level.getGameTime()),"both original paid and fresh paid clocks remain exact with no refund");
                }
            }
            h.assertTrue(PROBE.settlements==1,"no delayed duplicate skill attack");ledger(h);
            System.out.println("DREAMSEEKERS_FRIENDLY_DEBT_RESTART_UUID_CD_NO_MARK_COUNT_AS_REPLAY_FRESH_SETTLEMENT_PASS elapsedAtLoad="+elapsedAtLoad[0]+" finalElapsed="+(level.getGameTime()-expected.getLong("snapshotGameTime"))+" originalUUIDs="+KEYS.stream().map(key->bodies.get(key).getUUID()).toList());
            PROBE.target=null;
            for(var body:bodies.values())body.discard();
            for(var p:PLAYERS.values()){membership(p,false);p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}PLAYERS.clear();
            PlayerServerEvents.players.removeIf(p->List.of(OWNER,ENEMY).contains(p.getGameProfile().getName()));
            PlayerServerEvents.rtsPlayers.removeIf(p->List.of(OWNER,ENEMY).contains(p.name));
            ResourcesServerEvents.resourcesList.removeIf(r->List.of(OWNER,ENEMY).contains(r.ownerName));
            for(long key:expected.getLongArray("forcedChunks")){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}
        }).thenSucceed();
    }

    private static SkillResult cast(ServerLevel level,FriendlyGhostUnit source,LivingEntity target) {
        return new SkillNetwork.Cast(new SkillRequest(UnitSkill.FRIENDLY_DEBT_CONTRACT,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID())).execute(player(level,OWNER));
    }
    private static int cd(FriendlyGhostUnit source){return source.rts().skills().cooldownTicks(UnitSkill.FRIENDLY_DEBT_CONTRACT);}
    private static int mark(LivingEntity target){return FriendlyDebtContract.remainingTicks(target,OWNER);}
    private static int count(LivingEntity target){return FriendlyDebtContract.contributors(target,OWNER);}
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
        body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(APS_TECH,"actual permanent restart APS research",.1,AttributeModifier.Operation.ADDITION));
        h.assertTrue(h.getLevel().addFreshEntity(body),"original registered body enters ordinary world storage");
    }
    private static void home(Mob body){position(body,Vec3.atBottomCenterOf(((Unit)body).getAnchor().above()));}
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
        System.out.println("FRIENDLY_DEBT_RESTART_LEDGER time="+h.getLevel().getGameTime()+" banks=123,234,345,456 pending=0 cargo=empty");
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
    private static void observe(LivingEntity target){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}PROBE.target=target;PROBE.settlements=0;PROBE.caster=null;PROBE.amount=0;PROBE.iframeAtAttack=0;}
    /** Read-only real hurt event observer; it neither authorizes nor cancels nor changes damage. */
    public static final class SettlementProbe {
        LivingEntity target;int settlements,iframeAtAttack;UUID caster;float amount;
        @SubscribeEvent public void attack(LivingAttackEvent event) {
            if(event.getEntity()!=target||!FriendlyDebtContract.isSettlement(event.getSource()))return;
            settlements++;iframeAtAttack=target.invulnerableTime;caster=event.getSource().getEntity()==null?null:event.getSource().getEntity().getUUID();amount=event.getAmount();
        }
    }
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
