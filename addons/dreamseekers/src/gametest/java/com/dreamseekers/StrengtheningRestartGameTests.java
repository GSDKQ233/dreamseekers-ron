// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.economy.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.summon.BoundedSummons;
import com.dreamseekers.unit.*;
import com.dreamseekers.unitprogression.UnitBalanceMigration;
import com.dreamseekers.unitprogression.UnitBalanceMigration.Profile;
import com.dreamseekers.unitprogression.UnitBalanceMigration.Result;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Two real JVMs, two explicitly separate evidence routes:
 * (1) Legacy-format NBT fixtures constructed from real current save envelopes, read from disk in
 *     JVM2 by vanilla EntityType.loadEntityRecursive -> entity.load -> normal migration hook.
 *     These are NOT old API51 binary saves and NOT original Anvil legacy entities.
 * (2) Real paid runtime mother/right, recipients and temporary children save/restore through normal
 *     Anvil, preserving original UUID/CD/ledger. No world NBT edits and no reflected product state.
 */
@GameTestHolder("dreamseekers_strengthening_restart")
@PrefixGameTestTemplate(false)
public final class StrengtheningRestartGameTests {
    private static final String PHASE="dreamseekers.strengthening.restart.phase";
    private static final String OWNER="StrengtheningRestart_Owner",ENEMY="StrengtheningRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(-64,100,-64);
    private static final File MANIFEST=new File("strengthening-restart-expectations.nbt");
    private static final File LEGACY=new File("strengthening-legacy-fixtures.nbt");
    private static final File VERIFIED=new File("strengthening-restart-verified.nbt");
    private static final Map<String,Mob> UNITS=new LinkedHashMap<>();
    private static final List<Mob> CHILDREN=new ArrayList<>();
    private static final Set<Long> CHUNKS=new LinkedHashSet<>();
    private static final Map<UUID,CompoundTag> JOIN_READS=new HashMap<>();
    private static final Map<UUID,CompoundTag> PUBLISHED_READS=new HashMap<>();
    private static ListTag legacyResults;
    private static CompoundTag expectations;
    private static Resources account;
    private static PassiveIncomePeer peer;
    private static long motherCastAt,rightCastAt;
    private static boolean verified;
    private static long movedOutAt;
    private static final List<CompoundTag> QUALIFICATION_READS=new ArrayList<>();
    private static long jvm() { return java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(); }
    private static void close(GameTestHelper h,double actual,double expected,String why) { h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.0004,why+" actual="+actual+" expected="+expected); }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }

    @GameTest(templateNamespace="dreamseekers_strengthening_restart",template="ore_mine_test_arena",batch="strengthening_restart",timeoutTicks=1000)
    public static void legacyFormatDiskMigrationAndOriginalPaidRuntimeNeverReplayAcrossTwoJvms(GameTestHelper h)throws Exception {
        switch(System.getProperty(PHASE,"")) {
            case "prepare" -> prepare(h);
            case "verify" -> verify(h);
            default -> throw new IllegalArgumentException(PHASE+" prepare|verify required");
        }
    }
    /** The early Join observation reads raw fields only: live-effect getters can retire invalid
     * unpublished leases and would make their own subsequent zero result meaningless evidence.
     * First valid published LivingTick records the live getters separately, before the old shock
     * deadline. A mother may already legitimately rediscover her nearby original beetle by then.
     * Neither hook loads/registers replacement world entities or writes product effect state.
     */
    @Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
    public static final class LoadObserver {
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public static void join(EntityJoinLevelEvent event) {
            if(!"verify".equals(System.getProperty(PHASE))||event.getLevel().isClientSide()||!(event.getEntity() instanceof Mob body)||!MANIFEST.isFile())return;
            try { if(expectations==null)expectations=NbtIo.readCompressed(MANIFEST); }
            catch(Exception failure){throw new RuntimeException(failure);}
            String key=null;for(Tag raw:expectations.getList("entities",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getUUID("uuid").equals(body.getUUID()))key=((CompoundTag)raw).getString("key");
            if(key==null)return;
            require(!event.isCanceled(),"Original permanent world body unexpectedly canceled on normal load");
            require(!JOIN_READS.containsKey(body.getUUID()),"Original world UUID joined twice");
            require(body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(BuffKind.ATTACK_SPEED.modifierId)==null,"No saved transient APS modifier on normal Anvil load");
            CompoundTag observation=new CompoundTag();observation.putFloat("health",body.getHealth());observation.putDouble("aps",body.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()));observation.putLong("at",event.getLevel().getGameTime());observation.putString("key",key);observation.putUUID("uuid",body.getUUID());observation.putBoolean("transientApsModifier",false);observation.putBoolean("publicEffectsProbed",false);
            JOIN_READS.put(body.getUUID(),observation);body.setNoAi(true); // containment AFTER raw-field observation only
        }
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public static void observeQualificationEnd(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
            if("verify".equals(System.getProperty(PHASE))&&event.phase==net.minecraftforge.event.TickEvent.Phase.END
                    &&event.level instanceof ServerLevel level)recordQualifications("END",level);
        }
        @SubscribeEvent(priority=EventPriority.HIGHEST)
        public static void published(LivingEvent.LivingTickEvent event) {
            if(!"verify".equals(System.getProperty(PHASE))||expectations==null
                    ||!(event.getEntity() instanceof Mob body)||!(body.level() instanceof ServerLevel level)
                    ||PUBLISHED_READS.containsKey(body.getUUID()))return;
            Resources restoredAccount=ExtendedResourcesSync.account(OWNER);
            if(restoredAccount==null||!restoredAccount.extendedStateSupported()
                    ||!restoredAccount.accountEpoch.equals(expectations.getUUID("epoch"))
                    ||!DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(OWNER))
                    ||!DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(ENEMY)))return;
            String key=null;
            for(Tag raw:expectations.getList("entities",Tag.TAG_COMPOUND)) {
                CompoundTag state=(CompoundTag)raw;Entity current=level.getEntity(state.getUUID("uuid"));
                // Both source and target must be genuinely published before any live-effect probe.
                if(!(current instanceof Mob unit)||!unit.isAlive()||!unit.isAddedToWorld()
                        ||level.getEntity(unit.getId())!=unit||!unit.getUUID().equals(state.getUUID("uuid"))
                        ||!(unit instanceof Unit owned)||!owned.getOwnerName().equals(state.getString("owner"))
                        ||!BuiltInRegistries.ENTITY_TYPE.getKey(unit.getType()).toString().equals(state.getString("type")))return;
                if(unit==body)key=state.getString("key");
            }
            if(key==null||!JOIN_READS.containsKey(body.getUUID()))return;
            long now=level.getGameTime();
            require(now<expectations.getLong("shockExpiresAt"),"First valid published observation must precede old shock expiry; never wait out the state");
            int remaining=AaroncosShockSuppression.remainingTicks(body);
            float factor=AaroncosShockSuppression.factor(body);
            require(remaining==0&&factor==1,"Every published original, including non-attacking support, has no replayed shock");
            require(body.getType()==switch(key) {
                case "mother" -> DreamseekersMod.BEETLE_MOTHER_UNIT.get();
                case "beetle" -> DreamseekersMod.BEETLE.get();
                case "right" -> DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get();
                case "target" -> DreamseekersMod.GOLDEN_FOX_UNIT.get();
                default -> throw new AssertionError("Unknown original runtime key "+key);
            },"Exact original controlled type determines whether ordinary attack cadence exists");
            boolean attacking=body instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
            require(attacking!=key.equals("target"),"Three original combat units are attackers; exact GoldenFox target is not");
            double aura=SkillEffects.bonus(body,BuffKind.ATTACK_SPEED);
            // Do not call a legitimately rediscovered mother aura serialized replay or require it zero.
            require(aura==0||key.equals("beetle")&&Math.abs(aura-.15)<.0001,"Only a newly legal beetle aura may already be present");
            CompoundTag read=new CompoundTag();read.putString("key",key);read.putUUID("uuid",body.getUUID());read.putLong("at",now);
            read.putBoolean("allOriginalBodiesPublished",true);read.putUUID("accountEpoch",restoredAccount.accountEpoch);
            read.putBoolean("accountSupported",true);read.putBoolean("ownerFactionAllowed",true);read.putBoolean("enemyFactionAllowed",true);
            read.putInt("shockRemaining",remaining);read.putFloat("shockFactor",factor);
            read.putBoolean("attacker",attacking);
            if(body instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit attacker) {
                float multiplier=attacker.getAttackCooldownMultiplier(),interval=attacker.getAttackCooldown();
                require(multiplier==1&&Float.isFinite(interval)&&interval>0,"Actual combat unit ordinary cadence has no old interval penalty");
                read.putFloat("attackMultiplier",multiplier);read.putFloat("normalAttackInterval",interval);
            } // Genuine noncombat GoldenFox has no cadence fields, not fabricated identity values.
            read.putDouble("auraBonus",aura);
            read.putFloat("health",body.getHealth());PUBLISHED_READS.put(body.getUUID(),read);
            System.out.println("STRENGTHENING_FIRST_VALID_PUBLISHED "+read+" oldShockDeadline="+expectations.getLong("shockExpiresAt"));
        }
    }
    /** Read-only public qualification witness after all original first-published probes.
     * The END observer records, never throws or mutates tracking/effects; sequence assertions
     * reject any missing/invalid intermediate sample. No reflected product eligibility getters. */
    private static boolean recordQualifications(String phase,ServerLevel level) {
        if(PUBLISHED_READS.size()!=4||UNITS.size()!=4||UNITS.get("mother").level()!=level)return false;
        CompoundTag sample=new CompoundTag();sample.putString("phase",phase);sample.putLong("at",level.getGameTime());
        boolean valid=true;
        for(var entry:UNITS.entrySet()) {
            Mob body=entry.getValue();CompoundTag row=new CompoundTag();row.putUUID("uuid",body.getUUID());
            boolean added=body.isAddedToWorld(),byUuid=level.getEntity(body.getUUID())==body,byId=level.getEntity(body.getId())==body;
            boolean roster=UnitServerEvents.getAllUnits().stream().filter(e->e==body).count()==1;
            boolean ticking=level.isPositionEntityTicking(body.blockPosition());
            row.putBoolean("added",added);row.putBoolean("uuidIndex",byUuid);row.putBoolean("idIndex",byId);
            row.putBoolean("roster",roster);row.putBoolean("entityTicking",ticking);row.putInt("naturalTicks",body.tickCount);
            valid&=body.isAlive()&&!body.isRemoved()&&added&&byUuid&&byId&&roster&&ticking;
            sample.put(entry.getKey(),row);
        }
        sample.putBoolean("valid",valid);QUALIFICATION_READS.add(sample);
        if(!valid||!phase.equals("END")||level.getGameTime()%10==0)System.out.println("STRENGTHENING_ORIGINAL_QUALIFICATION "+sample);
        return valid;
    }
    private static void assertCompletedQualificationWindow(GameTestHelper h) {
        long now=h.getLevel().getGameTime();h.assertTrue(now-movedOutAt==20,"two original ten-tick movement windows remain exact");
        for(long at=movedOutAt+1;at<now;at++) {
            long tick=at;
            var samples=QUALIFICATION_READS.stream().filter(r->r.getString("phase").equals("END")&&r.getLong("at")==tick).toList();
            h.assertTrue(samples.size()==1&&samples.get(0).getBoolean("valid"),"every completed intermediate natural END retains all original qualified bodies at "+tick);
        }
        h.assertTrue(recordQualifications("assert",h.getLevel()),"original bodies remain published at rediscovery assertion");
        System.out.println("STRENGTHENING_QUALIFICATION_WINDOW_PASS out="+movedOutAt+" return="+(movedOutAt+10)+" assert="+now+" completedIntermediateEnds="+(now-movedOutAt-1));
    }
    private static <T extends Mob> T owned(GameTestHelper h,T body,String owner,int x,int z) {
        ((Unit)body).setOwnerName(owner);body.setNoAi(true);body.setPersistenceRequired();body.moveTo(ORIGIN.getX()+x+.5,100,ORIGIN.getZ()+z+.5);
        ((Unit)body).setAnchor(body.blockPosition().below());body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);body.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);
        h.assertTrue(h.getLevel().addFreshEntity(body),"original runtime entity published");return body;
    }
    private static SkillResult cast(SkillUnit source,LivingEntity target,UnitSkill skill) {
        return SkillService.request(peer.actor,new SkillRequest(skill,source.mob().getId(),source.mob().getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID()));
    }
    /** Keep this persistent restart fixture inside native startup spawn loading and outside
     * the actual GameTest arena cleanup. No clock, tracking, original UUID or effect edits. */
    private static void assertStartupGeometry(GameTestHelper h) {
        var spawn=h.getLevel().getSharedSpawnPos();
        // Frozen template is112x16x136; native cleanup expands -2/-3/-3 and +3/+20/+3.
        // This deliberately larger box also includes its structure block and marker allowance.
        var arena=new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(-4,-4,-4)),h.absolutePos(new BlockPos(116,36,140)));
        var fixture=new net.minecraft.world.phys.AABB(ORIGIN.offset(-12,-1,-10),ORIGIN.offset(57,8,11));
        h.assertTrue(!fixture.intersects(arena),"persistent four-body fixture is outside actual arena cleanup bounds");
        for(int x=(ORIGIN.getX()-16)>>4;x<=(ORIGIN.getX()+64)>>4;x++)for(int z=(ORIGIN.getZ()-16)>>4;z<=(ORIGIN.getZ()+16)>>4;z++) {
            h.assertTrue(Math.abs(x-(spawn.getX()>>4))<=5&&Math.abs(z-(spawn.getZ()>>4))<=5,"fixture remains deeply inside native startup441-chunk area");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(x,z)!=null,"normal startup already loaded fixture blocks before explicit fixture tickets");
        }
        System.out.println("STRENGTHENING_RESTART_STARTUP_GEOMETRY phase="+System.getProperty(PHASE)+" spawn="+spawn+" arena="+arena+" fixture="+fixture);
    }
    private static void prepare(GameTestHelper h)throws Exception {
        assertStartupGeometry(h);
        h.assertTrue(!MANIFEST.exists()&&!LEGACY.exists()&&!VERIFIED.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"fresh independent two-JVM fixture directory");
        for(int x=(ORIGIN.getX()-16)>>4;x<=(ORIGIN.getX()+64)>>4;x++)for(int z=(ORIGIN.getZ()-16)>>4;z<=(ORIGIN.getZ()+16)>>4;z++) {
            long key=ChunkPos.asLong(x,z);h.assertTrue(!h.getLevel().getForcedChunks().contains(key),"never adopt preexisting unrelated ticket");h.getLevel().setChunkForced(x,z,true);CHUNKS.add(key);h.getLevel().getChunk(x,z);
        }
        for(BlockPos at:BlockPos.betweenClosed(ORIGIN.offset(-12,-1,-10),ORIGIN.offset(56,7,10)))h.getLevel().setBlock(at,at.getY()==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        for(String owner:List.of(OWNER,ENEMY)) {PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(owner,DreamseekersMod.DREAMSEEKERS,owner.equals(OWNER)?97562:97563,0,false,0L,0L));ResourcesServerEvents.resourcesList.add(new Resources(owner,1000,1000,1000));}
        account=ExtendedResourcesSync.account(OWNER);account.extra.put(DreamPassiveEconomy.SHADOW,500);peer=new PassiveIncomePeer(h.getLevel(),OWNER);
        ResearchServerEvents.addCheat(OWNER,"foodforthought");
        var mother=owned(h,DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel()),OWNER,0,0);UNITS.put("mother",mother);
        var beetle=owned(h,DreamseekersMod.BEETLE.get().create(h.getLevel()),OWNER,4,0);UNITS.put("beetle",beetle);
        var right=owned(h,DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel()),OWNER,40,0);UNITS.put("right",right);
        var target=owned(h,DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel()),ENEMY,43,0);UNITS.put("target",target);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(1000);
        writeLegacyFixtures(h); // real disk, never injected into the world or silently called API51 binary
        h.startSequence().thenIdle(30).thenWaitUntil(()->{for(Mob body:UNITS.values())h.assertTrue(body.tickCount>0&&h.getLevel().getEntity(body.getUUID())==body,"original normal indexed unit ready");})
          .thenExecute(()->{
              close(h,SkillEffects.bonus(beetle,BuffKind.ATTACK_SPEED),.15,"natural mother aura before paid skills");
              motherCastAt=h.getLevel().getGameTime();h.assertTrue(cast(mother,mother,UnitSkill.MOTHER_BROOD)==SkillResult.SUCCESS,"actual mother thirty-shadow summon");
              UnitServerEvents.getAllUnits().stream().filter(e->e instanceof Mob&&e instanceof Unit u&&u.getOwnerName().equals(OWNER)&&BoundedSummons.isTemporary(e)).map(e->(Mob)e).forEach(CHILDREN::add);
              h.assertTrue(CHILDREN.size()==2&&CHILDREN.stream().allMatch(RtsKillValueClaims::isFree),"two real active free-origin children");CHILDREN.forEach(e->e.setNoAi(true));
              rightCastAt=h.getLevel().getGameTime();h.assertTrue(cast(right,right,UnitSkill.AARONCOS_RIGHT_SHOCK)==SkillResult.SUCCESS,"actual right thirty-shadow cast");
          }).thenIdle(20).thenExecute(()->{
              close(h,target.getHealth(),976,"real twenty-four HP loss at natural release");h.assertTrue(AaroncosShockSuppression.remainingTicks(target)==40,"real transient shock exists before normal save");
              close(h,SkillEffects.bonus(beetle,BuffKind.ATTACK_SPEED),.15,"natural aura still active");h.assertTrue(account.extra.get(DreamPassiveEconomy.SHADOW)==440,"both real thirty-shadow payments spent");
              MinecraftForge.EVENT_BUS.register(new StopWitness());PlayerServerEvents.saveRTSPlayers();ResourcesServerEvents.saveResources(h.getLevel());ResearchServerEvents.saveResearch();
              System.out.println("STRENGTHENING_RESTART_PREPARED_WAIT_FOR_NORMAL_STOP_WITNESS");h.succeed();
          });
    }
    public static final class StopWitness {
        @SubscribeEvent(priority=EventPriority.HIGH) public void stopping(ServerStoppingEvent event) {
            var level=event.getServer().overworld();long now=level.getGameTime();
            if(!verified) {
                require(now<rightCastAt+60,"Normal stop and disk save must occur while original shock remains live");
                require(AaroncosShockSuppression.remainingTicks(UNITS.get("target"))==(int)(rightCastAt+60-now),"Exact original live shock at normal stop");
                require(Math.abs(SkillEffects.bonus(UNITS.get("beetle"),BuffKind.ATTACK_SPEED)-.15)<.0001,"Original live mother aura at normal stop");
                require(CHILDREN.size()==2&&CHILDREN.stream().allMatch(BoundedSummons::canAct),"Temporary children still hold genuine live lease at normal stop");
                CompoundTag saved=new CompoundTag();saved.putInt("format",1);saved.putBoolean("normalStopWitness",true);saved.putLong("jvmStart",jvm());saved.putLong("stopTime",now);saved.putString("owner",OWNER);saved.putString("enemy",ENEMY);saved.putUUID("epoch",account.accountEpoch);saved.put("account",accountTag());saved.putLongArray("chunks",new ArrayList<>(CHUNKS));
                saved.putLong("motherCastAt",motherCastAt);saved.putLong("rightCastAt",rightCastAt);saved.putLong("shockExpiresAt",rightCastAt+60);saved.putInt("shockRemaining",AaroncosShockSuppression.remainingTicks(UNITS.get("target")));saved.putDouble("auraBonus",SkillEffects.bonus(UNITS.get("beetle"),BuffKind.ATTACK_SPEED));saved.putString("legacyFile",LEGACY.getName());
                ListTag children=new ListTag();for(Mob child:CHILDREN)children.add(NbtUtils.createUUID(child.getUUID()));saved.put("children",children);saved.put("entities",entityStates());write(saved,MANIFEST);
                System.out.println("STRENGTHENING_RESTART_STOP_WITNESS aura15 shock_live children2 shadow440 stopTime="+now);
            } else {
                require(AaroncosShockSuppression.remainingTicks(UNITS.get("target"))==0&&BoundedSummons.activeCount(OWNER)==0,"Verify normal stop has no replayed managed negative or temporary births");
                CompoundTag saved=new CompoundTag();saved.putInt("format",1);saved.putBoolean("normalStopWitness",true);saved.putLong("jvmStart",jvm());saved.putLong("prepareJvmStart",expectations.getLong("jvmStart"));saved.putLong("verifiedTime",now);saved.putLong("stopTime",now);saved.put("legacyResults",legacyResults.copy());saved.put("entities",entityStates());saved.put("account",accountTag());saved.putLongArray("chunks",expectations.getLongArray("chunks"));saved.putBoolean("rawJoinObservations",JOIN_READS.size()==4);saved.putBoolean("validPublishedObservations",PUBLISHED_READS.size()==4);saved.putBoolean("naturalAuraRediscovered",true);ListTag joins=new ListTag();for(CompoundTag join:JOIN_READS.values())joins.add(join.copy());saved.put("joinReads",joins);ListTag published=new ListTag();for(CompoundTag read:PUBLISHED_READS.values())published.add(read.copy());saved.put("publishedReads",published);write(saved,VERIFIED);
                System.out.println("STRENGTHENING_RESTART_VERIFY_STOP_WITNESS legacy6 native_uuid4 original_CD no_short_replay");
            }
            if(peer!=null){peer.close();peer=null;}
        }
    }
    private static ListTag entityStates() {
        ListTag states=new ListTag();for(var entry:UNITS.entrySet()) {
            Mob body=entry.getValue();CompoundTag raw=body.saveWithoutId(new CompoundTag()),state=new CompoundTag();state.putString("key",entry.getKey());state.putUUID("uuid",body.getUUID());state.putString("type",BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()).toString());state.putString("owner",((Unit)body).getOwnerName());state.putFloat("health",body.getHealth());state.putDouble("apsBase",body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue());state.put("skills",raw.getCompound("DreamseekersUnitSkills").copy());states.add(state);
        }return states;
    }
    private static CompoundTag accountTag(){CompoundTag tag=new CompoundTag();tag.putInt("food",account.food);tag.putInt("wood",account.wood);tag.putInt("ore",account.ore);tag.putInt("emerald",account.emerald);account.writeExtended(tag);return tag;}
    private static void write(CompoundTag tag,File file){try{NbtIo.writeCompressed(tag,file);}catch(Exception failure){throw new RuntimeException(failure);}}

    private static void verify(GameTestHelper h)throws Exception {
        assertStartupGeometry(h);
        h.assertTrue(MANIFEST.isFile()&&LEGACY.isFile()&&!VERIFIED.exists(),"real prepared files exist and verify never reuses prior success");expectations=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expectations.getInt("format")==1&&expectations.getBoolean("normalStopWitness")&&expectations.getLong("jvmStart")!=jvm(),"separate actual JVM and genuine normal-stop witness");
        h.assertTrue(expectations.getLong("stopTime")<expectations.getLong("shockExpiresAt")&&expectations.getInt("shockRemaining")>0,"prepare stopped during true live forty-tick post-effect");
        for(long packed:expectations.getLongArray("chunks")){h.assertTrue(h.getLevel().getForcedChunks().contains(packed),"original forced ticket restored by normal world load");var chunk=new ChunkPos(packed);h.getLevel().getChunk(chunk.x,chunk.z);CHUNKS.add(packed);}
        account=ExtendedResourcesSync.account(OWNER);h.assertTrue(account!=null&&account.accountEpoch.equals(expectations.getUUID("epoch")),"original actual account epoch normally restored");peer=new PassiveIncomePeer(h.getLevel(),OWNER);
        legacyResults=verifyLegacyFixtures(h); // consumes actual persisted fixture bytes from JVM1
        h.startSequence().thenWaitUntil(()->{
            for(Tag raw:expectations.getList("entities",Tag.TAG_COMPOUND)){var state=(CompoundTag)raw;h.assertTrue(h.getLevel().getEntity(state.getUUID("uuid")) instanceof Mob&&PUBLISHED_READS.containsKey(state.getUUID("uuid")),"original Anvil UUID had its first valid published observation before old expiry: "+state.getString("key"));}
        }).thenExecute(()->{
            h.assertTrue(JOIN_READS.size()==4&&PUBLISHED_READS.size()==4,"four raw joins and first valid published observations, never manually re-added");
            for(CompoundTag read:PUBLISHED_READS.values())h.assertTrue(read.getLong("at")<expectations.getLong("shockExpiresAt"),"live absence observed before original expiry, not after natural expiration");
            for(Tag raw:expectations.getList("entities",Tag.TAG_COMPOUND)) {
                var state=(CompoundTag)raw;Mob body=(Mob)h.getLevel().getEntity(state.getUUID("uuid"));UNITS.put(state.getString("key"),body);h.assertTrue(body.getType().equals(BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.tryParse(state.getString("type"))))&&((Unit)body).getOwnerName().equals(state.getString("owner")),"original exact registered type and owner");
                close(h,JOIN_READS.get(body.getUUID()).getFloat("health"),state.getFloat("health"),"no HP mutation before load containment");close(h,body.getHealth(),state.getFloat("health"),"no replayed HP damage or healing");close(h,body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue(),state.getDouble("apsBase"),"saved base APS unchanged");
                h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(u->u.getUUID().equals(body.getUUID())).count()==1,"exactly one original UUID in normal RTS roster");
                for(UnitSkill skill:List.of(UnitSkill.MOTHER_BROOD,UnitSkill.AARONCOS_RIGHT_SHOCK)){long deadline=state.getCompound("skills").getLong(skill.name());h.assertTrue(((SkillUnit)body).rts().skills().cooldownTicks(skill)==Math.max(0,deadline-h.getLevel().getGameTime()),"original absolute paid deadline "+state.getString("key")+"/"+skill);}
                h.assertTrue(AaroncosShockSuppression.remainingTicks(body)==0,"no replayed negative lease on any original body");
            }
            for(Tag child:expectations.getList("children",Tag.TAG_INT_ARRAY))h.assertTrue(h.getLevel().getEntity(NbtUtils.loadUUID(child))==null,"temporary marker cannot recreate original child lease");h.assertTrue(BoundedSummons.activeCount(OWNER)==0,"no temporary group restored");
            h.assertTrue(accountTag().equals(expectations.getCompound("account")),"no refund, free economy or account mutation after normal restart");
            var mother=(BeetleMotherUnit)UNITS.get("mother");var right=(AaroncosRightHandUnit)UNITS.get("right");
            h.assertTrue(mother.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD)>0&&right.rts().skills().cooldownTicks(UnitSkill.AARONCOS_RIGHT_SHOCK)>0,"original paid clocks still pending");
            h.assertTrue(cast(mother,mother,UnitSkill.MOTHER_BROOD)==SkillResult.COOLDOWN&&cast(right,right,UnitSkill.AARONCOS_RIGHT_SHOCK)==SkillResult.COOLDOWN,"saved cooldowns deny new free replacement");
            h.assertTrue(recordQualifications("before-out",h.getLevel()),"original bodies qualified before moving out");movedOutAt=h.getLevel().getGameTime();mother.setPos(ORIGIN.getX()-10.5,100,ORIGIN.getZ()+.5);h.assertTrue(recordQualifications("after-out",h.getLevel()),"out-of-range destination does not unpublish original bodies");close(h,SkillEffects.bonus(UNITS.get("beetle"),BuffKind.ATTACK_SPEED),0,"out-of-range original mother cannot leave a replay tail");
        }).thenIdle(10).thenExecute(()->{
            close(h,SkillEffects.bonus(UNITS.get("beetle"),BuffKind.ATTACK_SPEED),0,"original out-of-range source cannot grant live aura during ten ticks");UNITS.get("mother").setPos(ORIGIN.getX()+.5,100,ORIGIN.getZ()+.5);h.assertTrue(recordQualifications("return",h.getLevel()),"original source returns without tracking or roster loss");
        }).thenIdle(10).thenExecute(()->{assertCompletedQualificationWindow(h);close(h,SkillEffects.bonus(UNITS.get("beetle"),BuffKind.ATTACK_SPEED),.15,"new legitimate discovery may rebuild aura, not saved replay");})
          .thenIdle(45).thenExecute(()->{
              h.assertTrue(AaroncosShockSuppression.remainingTicks(UNITS.get("target"))==0&&BoundedSummons.activeCount(OWNER)==0,"no delayed short-effect or temporary child replay beyond forty ticks");close(h,UNITS.get("target").getHealth(),976,"no delayed damage");h.assertTrue(accountTag().equals(expectations.getCompound("account")),"original real debit remains final");
              verified=true;MinecraftForge.EVENT_BUS.register(new StopWitness());PlayerServerEvents.saveRTSPlayers();ResourcesServerEvents.saveResources(h.getLevel());ResearchServerEvents.saveResearch();System.out.println("STRENGTHENING_RESTART_VERIFY_PASS legacy6 normal_UUID4 paid_CD_and_no_short_replay");h.succeed();
          });
    }

    private static Attribute[] changed(){return new Attribute[]{Attributes.MAX_HEALTH,Attributes.ATTACK_DAMAGE,AttributeRegistrar.ATTACK_DAMAGE.get(),Attributes.ARMOR};}
    private static CompoundTag attribute(CompoundTag root,Attribute attribute){String name=BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString();for(Tag raw:root.getList("Attributes",Tag.TAG_COMPOUND)){var entry=(CompoundTag)raw;if(name.equals(entry.getString("Name")))return entry;}throw new AssertionError("Missing real save attribute "+name);}
    private static double[] old(Profile profile){return profile==Profile.MOTHER?new double[]{160,12,12,3}:new double[]{350,20,20,6};}
    private static double[] current(Profile profile){return switch(profile){case MOTHER->new double[]{200,14,14,4};case LEFT->new double[]{450,26,26,7};case RIGHT->new double[]{420,24,24,6};};}
    private static Mob create(ServerLevel level,Profile profile){return switch(profile){case MOTHER->DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(level);case LEFT->DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(level);case RIGHT->DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(level);};}
    private static Result result(Mob body){if(body instanceof BeetleMotherUnit mother)return mother.balanceMigrationResult();if(body instanceof AaroncosLeftHandUnit left)return left.balanceMigrationResult();return ((AaroncosRightHandUnit)body).balanceMigrationResult();}
    private static CompoundTag bases(Mob body){CompoundTag tag=new CompoundTag();for(Attribute attribute:changed())tag.putDouble(BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString(),body.getAttribute(attribute).getBaseValue());return tag;}
    private static void writeLegacyFixtures(GameTestHelper h) {
        ListTag fixtures=new ListTag();
        for(String key:List.of("mother","left","right","researched_mother","custom_mother","unknown_mother")) {
            Profile profile=key.equals("left")?Profile.LEFT:key.equals("right")?Profile.RIGHT:Profile.MOTHER;Mob seed=create(h.getLevel(),profile);((Unit)seed).setOwnerName("LegacyFormat_"+key);
            seed.setItemSlot(EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            CompoundTag value=new CompoundTag();value.putInt("version",1);value.putUUID("origin",UUID.randomUUID());value.put("claim",new CompoundTag());seed.getPersistentData().put(RtsKillValueClaims.KEY,value);
            // Native AttributeMap.save serializes instantiated values only; materialize untouched registered defaults before the real save envelope.
            for(Attribute attribute:changed())h.assertTrue(seed.getAttribute(attribute)!=null,"registered attribute instance before actual native save");
            CompoundTag body=seed.saveWithoutId(new CompoundTag());body.putString("id",BuiltInRegistries.ENTITY_TYPE.getKey(seed.getType()).toString());seed.discard();body.remove(UnitBalanceMigration.KEY);
            double[] before=old(profile),after=current(profile);boolean research=key.equals("researched_mother");double ratio=profile==Profile.RIGHT?.25:.5;
            for(int i=0;i<changed().length;i++)attribute(body,changed()[i]).putDouble("Base",before[i]);
            if(research)for(var entry:Map.of(Attributes.MAX_HEALTH,DreamAscendancyAttributes.HEALTH_ID,AttributeRegistrar.ATTACK_DAMAGE.get(),DreamAscendancyAttributes.DAMAGE_ID).entrySet()) {
                CompoundTag saved=attribute(body,entry.getKey());ListTag modifiers=saved.getList("Modifiers",Tag.TAG_COMPOUND);modifiers.add(new AttributeModifier(entry.getValue(),"saved permanent research fixture",.15,AttributeModifier.Operation.MULTIPLY_TOTAL).save());saved.put("Modifiers",modifiers);
            }
            String expectedResult="MIGRATED";if(key.equals("custom_mother")){before=new double[]{500,31,31,3};after=before.clone();for(int i=0;i<changed().length;i++)attribute(body,changed()[i]).putDouble("Base",before[i]);ratio=.25;expectedResult="CUSTOM_OR_UNRECOGNIZED";}
            if(key.equals("unknown_mother")){CompoundTag unknown=new CompoundTag();unknown.putInt("version",77);unknown.putString("profile","mother_v77");unknown.putLong("preserveUnknown",991871);body.put(UnitBalanceMigration.KEY,unknown);after=before.clone();expectedResult="UNSUPPORTED_SCHEMA";}
            body.putFloat("Health",(float)(before[0]*(research?1.15:1)*ratio));body.putShort("DeathTime",(short)0);
            CompoundTag expectedBases=new CompoundTag();for(int i=0;i<changed().length;i++)expectedBases.putDouble(BuiltInRegistries.ATTRIBUTE.getKey(changed()[i]).toString(),after[i]);
            CompoundTag entry=new CompoundTag();entry.putString("key",key);entry.putString("profile",profile.id());entry.putString("expectedResult",expectedResult);entry.putFloat("expectedHealth",(float)(after[0]*(research?1.15:1)*ratio));entry.putFloat("expectedMax",(float)(after[0]*(research?1.15:1)));entry.put("expectedBases",expectedBases);entry.put("nbt",body);fixtures.add(entry);
        }
        CompoundTag root=new CompoundTag();root.putInt("format",1);root.putLong("jvmStart",jvm());root.putBoolean("legacyFormatNotOldBinary",true);root.put("entries",fixtures);write(root,LEGACY);
    }
    private static ListTag verifyLegacyFixtures(GameTestHelper h)throws Exception {
        CompoundTag root=NbtIo.readCompressed(LEGACY);h.assertTrue(root.getInt("format")==1&&root.getBoolean("legacyFormatNotOldBinary")&&root.getLong("jvmStart")==expectations.getLong("jvmStart")&&root.getLong("jvmStart")!=jvm(),"actual persisted declared legacy-format fixtures came from different prepare JVM");
        ListTag results=new ListTag();h.assertTrue(root.getList("entries",Tag.TAG_COMPOUND).size()==6,"six explicit legacy boundary fixtures on disk");
        for(Tag raw:root.getList("entries",Tag.TAG_COMPOUND)) {
            CompoundTag fixture=(CompoundTag)raw,body=fixture.getCompound("nbt");
            // Normal native loader invokes the real superclass Attributes load before addon hook.
            Entity entity=EntityType.loadEntityRecursive(body.copy(),h.getLevel(),loaded->loaded);h.assertTrue(entity instanceof Mob,"normal EntityType loader recognizes saved exact type");Mob loaded=(Mob)entity;
            h.assertTrue(result(loaded).name().equals(fixture.getString("expectedResult")),"real migration diagnostic "+fixture.getString("key"));h.assertTrue(bases(loaded).equals(fixture.getCompound("expectedBases")),"all four exact loaded bases");close(h,loaded.getHealth(),fixture.getFloat("expectedHealth"),"native load then migration keeps intended ratio");close(h,loaded.getMaxHealth(),fixture.getFloat("expectedMax"),"effective maximum after saved permanent modifiers");
            h.assertTrue(loaded.getUUID().equals(body.getUUID("UUID"))&&loaded.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD)&&loaded.getPersistentData().get(RtsKillValueClaims.KEY).equals(body.getCompound("ForgeData").get(RtsKillValueClaims.KEY)),"fixture identity/equipment/claimed origin survive normal loader");
            if(fixture.getString("key").equals("researched_mother")){h.assertTrue(loaded.getAttribute(Attributes.MAX_HEALTH).getModifier(DreamAscendancyAttributes.HEALTH_ID)!=null&&loaded.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).getModifier(DreamAscendancyAttributes.DAMAGE_ID)!=null,"saved research UUIDs preserved exactly once without runtime account gating");}
            CompoundTag first=loaded.saveWithoutId(new CompoundTag());first.putString("id",body.getString("id"));
            Entity again=EntityType.loadEntityRecursive(first.copy(),h.getLevel(),value->value);h.assertTrue(again instanceof Mob,"second native load of actual migrated save");Mob second=(Mob)again;
            h.assertTrue(bases(second).equals(bases(loaded)),"second native load never multiplies bases twice");close(h,second.getHealth(),loaded.getHealth(),"second load preserves exact migrated injury");close(h,second.getMaxHealth(),loaded.getMaxHealth(),"second load preserves effective max and modifiers");
            CompoundTag secondSave=second.saveWithoutId(new CompoundTag());secondSave.putString("id",body.getString("id"));
            Entity thirdEntity=EntityType.loadEntityRecursive(secondSave.copy(),h.getLevel(),value->value);h.assertTrue(thirdEntity instanceof Mob,"third normal load of second saved result");Mob third=(Mob)thirdEntity;
            h.assertTrue(bases(third).equals(bases(loaded)),"third native load preserves exact four bases");close(h,third.getHealth(),loaded.getHealth(),"third native load does not multiply health ratio again");close(h,third.getMaxHealth(),loaded.getMaxHealth(),"third load keeps permanent modifier effective max");
            CompoundTag thirdSave=third.saveWithoutId(new CompoundTag());thirdSave.putString("id",body.getString("id"));
            if(fixture.getString("expectedResult").equals("MIGRATED"))h.assertTrue(result(second)==Result.CURRENT&&result(third)==Result.CURRENT,"success marker prevents second and third historical migration");
            else h.assertTrue(Objects.equals(first.get(UnitBalanceMigration.KEY),body.get(UnitBalanceMigration.KEY))&&Objects.equals(secondSave.get(UnitBalanceMigration.KEY),body.get(UnitBalanceMigration.KEY))&&Objects.equals(thirdSave.get(UnitBalanceMigration.KEY),body.get(UnitBalanceMigration.KEY)),"custom absence and unknown complete schema preserved three times");
            CompoundTag record=new CompoundTag();record.putString("key",fixture.getString("key"));record.putUUID("uuid",loaded.getUUID());record.putString("result",result(loaded).name());record.putFloat("health",loaded.getHealth());record.putFloat("maxHealth",loaded.getMaxHealth());record.put("bases",bases(loaded));record.put("firstSave",first);record.put("secondSave",secondSave);record.put("thirdSave",thirdSave);results.add(record);loaded.discard();second.discard();third.discard();
        }
        return results;
    }
}
