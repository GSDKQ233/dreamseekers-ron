// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.building.DreamLaboratoryPlacement;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.resonance.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Real paid research, ordinary save and a different JVM. Verify never injects saved world objects. */
@GameTestHolder("dreamseekers_luminous_restart") @PrefixGameTestTemplate(false)
public final class LightAmplificationRestartGameTests {
    private static final String OWNER="LuminousRestart_Owner",ENEMY="LuminousRestart_Enemy";
    private static final BlockPos ROOT=new BlockPos(38912,100,38912),DAIS=ROOT.offset(24,0,0),LAB=ROOT.offset(0,0,32);
    private static final File MANIFEST=new File("luminous-restart-expectations.nbt");
    private static final UUID PERMANENT=UUID.fromString("4d2298b3-f59d-4f18-a4f1-697c933912ab");
    private static final List<String> KEYS=List.of("gelCaster","gelTarget","reboundCaster","reboundTarget",
        "hotCaster","hotTarget","tideCaster","tideTarget","foxCaster","foxTarget","freshCaster","freshTarget");
    private static final List<UnitSkill> SKILLS=List.of(UnitSkill.GEL_GUARD,UnitSkill.JELLY_FLOW,UnitSkill.FOX_BLESSING);
    private static final Set<Long> CHUNKS=new LinkedHashSet<>();
    private static final Map<String,Mob> UNITS=new LinkedHashMap<>();
    private static final CompoundTag PURCHASES=new CompoundTag();
    private static DreamAnchorPlacement root;
    private static BuildingPlacement dais;
    private static DreamLaboratoryPlacement lab;
    private static Resources account;
    private static PassiveIncomePeer peer;
    private static PinkSlimeUnit enemy;
    private static long hotAt,finalCastAt,tideAt;

    @GameTest(templateNamespace="dreamseekers_luminous_restart",template="dreamseekers_test_arena",batch="luminous_restart",timeoutTicks=9000)
    public static void paidTiersAndCooldownSurviveWithoutAmplifiedShortStateReplay(GameTestHelper h)throws Exception {
        switch(System.getProperty("dreamseekers.luminous.restart.phase","")) {
            case "prepare" -> prepare(h);case "verify" -> verify(h);
            default -> throw new IllegalArgumentException("dreamseekers.luminous.restart.phase prepare|verify required");
        }
    }
    private static void prepare(GameTestHelper h) {
        var level=h.getLevel();h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"fresh independent prepared world");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(OWNER,DreamseekersMod.DREAMSEEKERS,97561,0,false,0L,0L));
        account=new Resources(OWNER,10000,10000,10000,37);account.extra.put(DreamPassiveEconomy.SHADOW,1000);ResourcesServerEvents.resourcesList.add(account);peer=new PassiveIncomePeer(level,OWNER);
        for(int x=(ROOT.getX()-32)>>4;x<=(ROOT.getX()+64)>>4;x++)for(int z=(ROOT.getZ()-32)>>4;z<=(ROOT.getZ()+64)>>4;z++) {
            long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"no adopting unrelated test chunk tickets");level.setChunkForced(x,z,true);CHUNKS.add(key);level.getChunk(x,z);
        }
        root=(DreamAnchorPlacement)install(level,DreamseekersMod.DREAM_ANCHOR,ROOT);
        dais=install(level,DreamseekersMod.RESONANCE_DAIS,DAIS);
        lab=(DreamLaboratoryPlacement)install(level,DreamseekersMod.RESONANCE_LABORATORY,LAB);
        h.assertTrue(!com.dreamseekers.building.DreamResonanceAura.active(lab),"research host outside local work-rate aura");
        h.assertTrue(WorldTreeMorph.plan(root,WorldTreeModel.SHADOW_V5).valid(),"real physical upgrade preflight");
        h.assertTrue(DreamWorldTreeService.start(peer.actor,root,root.tree().instance,WorldTreeModel.SHADOW_V5).equals("started"),"actual paid 2400 tick tree upgrade");
        PURCHASES.putUUID("treeReceipt",root.tree().receipt);PURCHASES.putLong("treeStartedAt",level.getGameTime());
        h.startSequence().thenWaitUntil(()->h.assertTrue(root.ascended()&&root.model()==WorldTreeModel.SHADOW_V5&&root.tree().phase==WorldTreeState.Phase.COMPLETE,"natural tree upgrade completion, no edited remaining"))
        .thenExecute(()->{
            h.assertTrue(level.getGameTime()-PURCHASES.getLong("treeStartedAt")>=2399,"full real tree duration (same-tick first advance allowed)");
            h.assertTrue(!account.paidReceipts.containsKey(PURCHASES.getUUID("treeReceipt")),"tree receipt consumed exactly once");
            payResearch(h,LightResonanceResearch.FIRST,"first",120,120,100,30);
        }).thenWaitUntil(()->h.assertTrue(LightResonanceResearch.tier(OWNER,false)==1&&lab.productionQueue.isEmpty(),"first real paid research completes naturally"))
        .thenExecute(()->{
            h.assertTrue(level.getGameTime()-PURCHASES.getLong("firstAt")>=899,"full first research duration without work-rate aura (same-tick first advance allowed)");
            h.assertTrue(!account.paidReceipts.containsKey(PURCHASES.getUUID("firstReceipt")),"first receipt consumed");
            payResearch(h,LightResonanceResearch.FINAL,"final",240,240,200,80);
        }).thenWaitUntil(()->h.assertTrue(LightResonanceResearch.tier(OWNER,false)==2&&lab.productionQueue.isEmpty(),"second real paid research completes naturally"))
        .thenExecute(()->{
            h.assertTrue(level.getGameTime()-PURCHASES.getLong("finalAt")>=1499,"full second research duration without work-rate aura (same-tick first advance allowed)");
            h.assertTrue(!account.paidReceipts.containsKey(PURCHASES.getUUID("finalReceipt")),"final receipt consumed");
            spawnFixtures(h);
        }).thenWaitUntil(()->{for(var body:UNITS.values())ready(h,body);ready(h,enemy);})
        .thenExecute(()->{
            for(var body:UNITS.values())if(body instanceof JellyfishUnit jelly)
                h.assertTrue(SkillService.setAutocast(peer.actor,UnitSkill.JELLY_FLOW,body.getId(),body.getUUID(),jelly.rts().skills().revision(),false)==SkillResult.SUCCESS,"owner disables automatic casting before save; no legitimate new cast confused with replay");
            UNITS.get("tideTarget").setHealth(20);UNITS.get("hotTarget").setHealth(20);
            h.assertTrue(SkillAmplification.currentTier((SkillUnit)UNITS.get("tideCaster"),UnitSkill.JELLY_FLOW)==2,"caster sees real final-tier connected network");
            tideAt=level.getGameTime();cast(h,"tideCaster","tideTarget",UnitSkill.JELLY_FLOW);
        }).thenIdle(101).thenExecute(()->{
            close(h,UNITS.get("tideTarget").getHealth(),35,"five actual amplified three-HP pulses");
            hotAt=level.getGameTime();cast(h,"hotCaster","hotTarget",UnitSkill.JELLY_FLOW);
        }).thenIdle(21).thenExecute(()->{
            close(h,UNITS.get("tideTarget").getHealth(),38,"six amplified pulses total eighteen HP");
            close(h,UNITS.get("hotTarget").getHealth(),23,"incomplete flow has one real three-HP pulse");
            close(h,SkillEffects.bonus(UNITS.get("tideTarget"),BuffKind.MOVEMENT_SPEED),.15,"real amplified completion tide");
            finalCastAt=level.getGameTime();cast(h,"gelCaster","gelTarget",UnitSkill.GEL_GUARD);cast(h,"reboundCaster","reboundTarget",UnitSkill.GEL_GUARD);
            close(h,SkillEffects.shieldRemaining(UNITS.get("gelTarget")),18,"real late shield before normal save");
            var rebound=UNITS.get("reboundTarget");rebound.getAttribute(Attributes.ARMOR).setBaseValue(0);
            h.assertTrue(enemy.doHurtTarget(rebound),"real hostile ordinary hit consumes eighteen-point shield");
            close(h,SkillEffects.shieldRemaining(rebound),0,"rebound shield genuinely exhausted");
            close(h,SkillEffects.bonus(rebound,BuffKind.ATTACK_SPEED),.225,"amplified rebound is real before save");
            cast(h,"foxCaster","foxCaster",UnitSkill.FOX_BLESSING);
            close(h,SkillEffects.bonus(UNITS.get("foxTarget"),BuffKind.ATTACK_SPEED),.30,"real late Fox thirty percent, not global twenty-five clipping");
            var fox=UNITS.get("foxTarget");close(h,fox.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),(fox.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue()+.1)*1.30,"actual APS combines permanent addition and transient 30 percent");
            enemy.discard();PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(ENEMY));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(ENEMY));
            MinecraftForge.EVENT_BUS.register(new StopWitness());
            PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(level);ResourcesServerEvents.saveResources(level);ResearchServerEvents.saveResearch();
            System.out.println("LUMINOUS_RESTART_PREPARED_WAIT_FOR_NORMAL_STOP_WITNESS");h.succeed();
        });
    }
    /** HIGH observes the exact final server tick between core ledger save and building save. */
    public static final class StopWitness {
        @SubscribeEvent(priority=EventPriority.HIGH) public void stopping(ServerStoppingEvent event) {
            long now=event.getServer().overworld().getGameTime();
            if(now>=finalCastAt+SkillEffects.REBOUND_TICKS||now>=hotAt+120||now>=tideAt+120+SkillEffects.TIDE_TICKS)
                throw new AssertionError("Normal disk save must precede original short-state deadlines");
            if(Math.abs(SkillEffects.shieldRemaining(UNITS.get("gelTarget"))-18)>.0003||SkillEffects.healTicks(UNITS.get("hotTarget"))<=0
                ||Math.abs(SkillEffects.bonus(UNITS.get("reboundTarget"),BuffKind.ATTACK_SPEED)-.225)>.0003
                ||Math.abs(SkillEffects.bonus(UNITS.get("tideTarget"),BuffKind.MOVEMENT_SPEED)-.15)>.0003
                ||Math.abs(SkillEffects.bonus(UNITS.get("foxTarget"),BuffKind.ATTACK_SPEED)-.30)>.0003)
                throw new AssertionError("Every amplified transient must still exist at actual normal stop");
            if(LightResonanceResearch.tier(OWNER,false)!=2||!lab.productionQueue.isEmpty()||!account.paidReceipts.isEmpty())
                throw new AssertionError("Paid research complete, no unsettled receipts or queued replay");
            var tag=new CompoundTag();tag.putInt("format",1);tag.putBoolean("normalStopWitness",true);
            tag.putLong("jvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());tag.putLong("stopTime",now);
            tag.putLong("hotAt",hotAt);tag.putLong("finalCastAt",finalCastAt);tag.putLong("tideAt",tideAt);tag.putLong("rootLastTick",root.tree().lastTick);
            tag.putUUID("epoch",account.accountEpoch);tag.putUUID("rootInstance",root.tree().instance);tag.putUUID("daisInstance",ResonanceNetwork.view(dais).instance());tag.putUUID("labInstance",ResearchPlacementIdentity.serverIdentity(lab));
            tag.put("account",accountTag());tag.put("treeState",root.tree().write());tag.put("purchases",PURCHASES.copy());tag.putLongArray("chunks",new ArrayList<>(CHUNKS));
            for(var entry:UNITS.entrySet()) {
                var body=entry.getValue();var unit=(SkillUnit)body;var state=new CompoundTag();tag.putUUID(entry.getKey(),body.getUUID());
                state.putString("type",net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(body.getType()).toString());
                state.putFloat("health",body.getHealth());state.putString("owner",unit.getOwnerName());state.putInt("scenario",unit.getScenarioRoleIndex());
                state.putLong("revision",unit.rts().skills().revision());state.putDouble("apsBase",body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue());
                state.putDouble("moveBase",body.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue());
                for(var skill:SKILLS)state.putInt(skill.name(),unit.rts().skills().cooldownTicks(skill));tag.put(entry.getKey()+"State",state);
            }
            try{NbtIo.writeCompressed(tag,MANIFEST);}catch(Exception failure){throw new RuntimeException(failure);}
            peer.close();System.out.println("LUMINOUS_RESTART_STOP_WITNESS all_five_short_states_live tier=2 paid_receipts_consumed stopTime="+now);
        }
    }
    private static void verify(GameTestHelper h)throws Exception {
        h.assertTrue(MANIFEST.isFile(),"prepare normal-stop manifest exists");var saved=NbtIo.readCompressed(MANIFEST);var level=h.getLevel();
        h.assertTrue(saved.getInt("format")==1&&saved.getBoolean("normalStopWitness")&&saved.getLong("jvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"genuinely separate normal-save JVM pair");
        h.assertTrue(saved.getLong("stopTime")<saved.getLong("finalCastAt")+60&&saved.getLong("stopTime")<saved.getLong("hotAt")+120,"old runtime leases still live at actual stop");
        for(long key:saved.getLongArray("chunks")) {h.assertTrue(level.getForcedChunks().contains(key),"original forced chunks normally restored");var chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);}
        account=ExtendedResourcesSync.account(OWNER);h.assertTrue(account!=null&&account.accountEpoch.equals(saved.getUUID("epoch")),"same ordinary saved account epoch");
        root=(DreamAnchorPlacement)find(level,ROOT);dais=find(level,DAIS);lab=(DreamLaboratoryPlacement)find(level,LAB);peer=new PassiveIncomePeer(level,OWNER);
        h.startSequence().thenWaitUntil(()->{for(var key:KEYS){var body=level.getEntity(saved.getUUID(key));h.assertTrue(body instanceof Mob&&body instanceof SkillUnit,"normal asynchronous saved UUID restoration: "+key);ready(h,(Mob)body);}})
        .thenExecute(()->{
            h.assertTrue(root.tree().instance.equals(saved.getUUID("rootInstance"))&&root.model()==WorldTreeModel.SHADOW_V5&&root.ascended(),"same actually upgraded root identity and geometry");
            h.assertTrue(ResonanceNetwork.view(dais).instance().equals(saved.getUUID("daisInstance"))&&ResonanceNetwork.network(level,OWNER).rank(dais)>0,"same saved dais and connected network");
            h.assertTrue(ResearchPlacementIdentity.serverIdentity(lab).equals(saved.getUUID("labInstance"))&&lab.productionQueue.isEmpty(),"same laboratory, no reconstructed completed queue");
            h.assertTrue(LightResonanceResearch.tier(OWNER,false)==2&&ResearchServerEvents.playerHasResearch(OWNER,LightResonanceResearch.FIRST_ID)&&ResearchServerEvents.playerHasResearch(OWNER,LightResonanceResearch.FINAL_ID),"both real paid research grants saved");
            h.assertTrue(account.paidReceipts.isEmpty(),"consumed original receipts do not revive on restart");
            assertIncomeOnly(h,saved);var before=accountTag();
            for(var item:List.of(LightResonanceResearch.FIRST,LightResonanceResearch.FINAL))h.assertTrue(ReceiptResearchService.start(peer.actor,lab,item,ResearchPlacementIdentity.serverIdentity(lab),lab.getCaptureRevision(),account.accountEpoch,account.revision).equals("duplicate"),"completed research cannot debit or requeue after restart");
            h.assertTrue(before.equals(accountTag()),"duplicate paid requests do not spend/refund/mint");
            for(var key:KEYS) {
                var body=(Mob)level.getEntity(saved.getUUID(key));UNITS.put(key,body);var unit=(SkillUnit)body;var old=saved.getCompound(key+"State");
                h.assertTrue(unit.getOwnerName().equals(OWNER)&&unit.getScenarioRoleIndex()==old.getInt("scenario")&&UnitServerEvents.getAllUnits().stream().filter(u->u.getUUID().equals(body.getUUID())).count()==1,"one original owned UUID and scenario: "+key);
                close(h,body.getHealth(),old.getFloat("health"),"saved HP unchanged, no replayed amplified healing: "+key);
                assertNoRuntime(h,body,key);close(h,body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue(),old.getDouble("apsBase"),"base APS unchanged: "+key);
                close(h,body.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),old.getDouble("apsBase")+(key.equals("foxTarget")?.1:0),"only declared permanent APS modifier remains: "+key);
                for(var skill:SKILLS) {
                    int expected=(int)Math.max(0,saved.getLong("stopTime")+old.getInt(skill.name())-level.getGameTime());
                    h.assertTrue(unit.rts().skills().cooldownTicks(skill)==expected,"same absolute saved cooldown: "+key+"/"+skill);
                }
                h.assertTrue(unit.rts().skills().revision()>=old.getLong("revision"),"revisions do not rewind: "+key);
                if(body instanceof JellyfishUnit)h.assertTrue(!unit.rts().skills().autocastEnabled(UnitSkill.JELLY_FLOW),"saved disabled auto preference prevents legitimate new auto casts");
            }
            h.assertTrue(UNITS.get("foxTarget").getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(PERMANENT)!=null,"permanent attribute remains independent of thirty-percent temporary allowance");
            var spent=(SkillUnit)UNITS.get("gelCaster");h.assertTrue(spent.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)>0,"original full gel cooldown still pending after normal restart");
            h.assertTrue(SkillService.request(peer.actor,request(spent,UNITS.get("gelTarget"),UnitSkill.GEL_GUARD))==SkillResult.COOLDOWN,"saved cooldown rejects premature replacement without clearing it");
        }).thenIdle(141).thenExecute(()->{
            for(var key:KEYS){var body=UNITS.get(key);assertNoRuntime(h,body,key);close(h,body.getHealth(),saved.getCompound(key+"State").getFloat("health"),"no delayed flow/tide/rebound reward after original lease horizon: "+key);}
            assertIncomeOnly(h,saved);
            var fresh=(SkillUnit)UNITS.get("freshCaster");h.assertTrue(fresh.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==0,"unused source remains unused");
            h.assertTrue(SkillAmplification.currentTier(fresh,UnitSkill.GEL_GUARD)==2,"actual restored network and paid research still provide late eligibility");
            cast(h,"freshCaster","freshTarget",UnitSkill.GEL_GUARD);close(h,SkillEffects.shieldRemaining(UNITS.get("freshTarget")),18,"only new authorized post-restart cast creates new late shield");
            cleanup(level,saved);System.out.println("LUMINOUS_RESTART_VERIFY_PASS paid_tiers_original_UUIDs_epoch_absolute_CD_no_short_state_replay");h.succeed();
        });
    }
    private static void assertIncomeOnly(GameTestHelper h,CompoundTag saved) {
        var old=saved.getCompound("account");var tree=WorldTreeState.read(saved.getCompound("treeState"));long elapsed=root.tree().lastTick-saved.getLong("rootLastTick");
        h.assertTrue(elapsed>=0&&elapsed<2000,"only bounded real restart/verification ticks elapsed");long cycles=(tree.incomeElapsed+elapsed)/100;
        h.assertTrue(account.food==old.getInt("food")+cycles*5&&account.wood==old.getInt("wood")+cycles*5&&account.ore==old.getInt("ore")+cycles*5&&account.emerald==old.getInt("emerald")+cycles,"only existing actual five-resource world-tree income, no research refund/replay windfall");
        int shadow=old.getCompound(ResourceAccountCodec.KEY).getCompound("extra").getInt(DreamPassiveEconomy.SHADOW.toString());
        h.assertTrue(account.extra.get(DreamPassiveEconomy.SHADOW)==Math.min(DreamPassiveEconomy.SHADOW_CAP,shadow+cycles)&&root.tree().incomeElapsed==(tree.incomeElapsed+elapsed)%100,"existing shadow cadence and fractional progress preserved");
    }
    private static void assertNoRuntime(GameTestHelper h,Mob body,String key) {
        close(h,SkillEffects.shieldRemaining(body),0,"no restored shield: "+key);h.assertTrue(SkillEffects.healTicks(body)==0&&!SkillEffects.hasHeal(body),"no restored heal reservation: "+key);
        for(var kind:BuffKind.values())close(h,SkillEffects.bonus(body,kind),0,"no restored transient category: "+key+"/"+kind);
        h.assertTrue(!SkillEffects.hasNamedBuff(body,SkillEffects.FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED)&&!SkillEffects.hasNamedBuff(body,"elastic_rebound",BuffKind.ATTACK_SPEED)&&!SkillEffects.hasNamedBuff(body,"light_tide",BuffKind.MOVEMENT_SPEED),"no restored named record: "+key);
    }
    private static void payResearch(GameTestHelper h,ReceiptBackedResearchItem item,String key,int food,int wood,int ore,int shadow) {
        int f=account.food,w=account.wood,o=account.ore,s=account.extra.get(DreamPassiveEconomy.SHADOW);
        h.assertTrue(ReceiptResearchService.start(peer.actor,lab,item,ResearchPlacementIdentity.serverIdentity(lab),lab.getCaptureRevision(),account.accountEpoch,account.revision).equals("started"),"real owner paid research start: "+key);
        h.assertTrue(account.food==f-food&&account.wood==w-wood&&account.ore==o-ore&&account.extra.get(DreamPassiveEconomy.SHADOW)==s-shadow,"exact immediate research debit: "+key);
        h.assertTrue(lab.productionQueue.get(0).ticksLeft==(key.equals("first")?900:1500),"original unmodified research timer: "+key);
        var binding=lab.productionQueue.get(0).receiptResearch;h.assertTrue(binding!=null&&account.paidReceipts.containsKey(binding.receiptId()),"actual queued receipt exists: "+key);
        PURCHASES.putUUID(key+"Receipt",binding.receiptId());PURCHASES.putLong(key+"At",h.getLevel().getGameTime());
    }
    private static BuildingPlacement install(ServerLevel level,Building type,BlockPos pos) {
        for(int x=0;x<22;x++)for(int z=0;z<22;z++){level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<22;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(var block:BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(level),level,pos,Rotation.NONE))level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        var placement=BuildingUtils.getNewBuildingPlacement(type,level,pos,Rotation.NONE,OWNER,false);BuildingServerEvents.getBuildings().add(placement);placement.onBuilt();return placement;
    }
    private static void spawnFixtures(GameTestHelper h) {
        // A physical isolated north-side arena survives native AI restoration without teleporting bodies.
        for(int x=-17;x<=17;x++)for(int z=-17;z<=-1;z++)for(int y=0;y<=8;y++)
            h.getLevel().setBlock(DAIS.offset(x,y,z),(y==0||y==8||x==-17||x==17||z==-17||z==-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        spawn(h,"gelCaster",DreamseekersMod.PINK_SLIME_UNIT.get(),-2,-5);spawn(h,"gelTarget",DreamseekersMod.PINK_SLIME_UNIT.get(),-4,-5);
        spawn(h,"reboundCaster",DreamseekersMod.PINK_SLIME_UNIT.get(),0,-8);spawn(h,"reboundTarget",DreamseekersMod.PINK_SLIME_UNIT.get(),-2,-8);
        spawn(h,"hotCaster",DreamseekersMod.JELLYFISH_UNIT.get(),3,-8);spawn(h,"hotTarget",DreamseekersMod.PINK_SLIME_UNIT.get(),5,-8);
        spawn(h,"tideCaster",DreamseekersMod.JELLYFISH_UNIT.get(),0,-3);spawn(h,"tideTarget",DreamseekersMod.PINK_SLIME_UNIT.get(),-2,-3);
        spawn(h,"foxCaster",DreamseekersMod.GOLDEN_FOX_UNIT.get(),9,-3);spawn(h,"foxTarget",DreamseekersMod.PINK_SLIME_UNIT.get(),11,-3);
        spawn(h,"freshCaster",DreamseekersMod.PINK_SLIME_UNIT.get(),-9,-4);spawn(h,"freshTarget",DreamseekersMod.PINK_SLIME_UNIT.get(),-11,-4);
        UNITS.get("foxTarget").getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(PERMANENT,"luminous restart permanent APS",.1,AttributeModifier.Operation.ADDITION));
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(ENEMY,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(ENEMY,0,0,0));
        for(int x=-6;x<=-3;x++)for(int z=-12;z<=-10;z++)h.getLevel().setBlock(DAIS.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
        enemy=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());enemy.setOwnerName(ENEMY);enemy.setNoAi(true);enemy.moveTo(DAIS.getX()-4.5,DAIS.getY()+1,DAIS.getZ()-10.5);
        enemy.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(22);enemy.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(22);h.assertTrue(h.getLevel().addFreshEntity(enemy),"real hostile attack fixture");
    }
    private static void spawn(GameTestHelper h,String key,EntityType<? extends Mob> type,int x,int z) {
        var body=type.create(h.getLevel());h.assertTrue(body instanceof SkillUnit,"registered ordinary supported unit");var unit=(SkillUnit)body;unit.setOwnerName(OWNER);unit.setScenarioRoleIndex(5100+UNITS.size());body.setNoAi(true);
        var pos=DAIS.offset(x,1,z);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){h.getLevel().setBlock(pos.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<5;y++)h.getLevel().setBlock(pos.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);}
        body.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(body),"ordinary entity storage publication: "+key);UNITS.put(key,body);
    }
    private static void ready(GameTestHelper h,Mob body){h.assertTrue(body.tickCount>3&&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body&&UnitServerEvents.getAllUnits().contains(body),"exact registered native entity ticked");}
    private static void cast(GameTestHelper h,String from,String to,UnitSkill skill){var source=(SkillUnit)UNITS.get(from);h.assertTrue(SkillService.request(peer.actor,request(source,UNITS.get(to),skill))==SkillResult.SUCCESS,"genuine authoritative successful "+skill+" cast: "+from);}
    private static SkillRequest request(SkillUnit source,Entity target,UnitSkill skill){return new SkillRequest(skill,source.mob().getId(),source.mob().getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID());}
    private static CompoundTag accountTag(){var tag=new CompoundTag();tag.putInt("food",account.food);tag.putInt("wood",account.wood);tag.putInt("ore",account.ore);tag.putInt("emerald",account.emerald);account.writeExtended(tag);return tag;}
    private static BuildingPlacement find(ServerLevel level,BlockPos pos){return BuildingServerEvents.getBuildings().stream().filter(p->p.level==level&&p.ownerName.equals(OWNER)&&p.originPos.equals(pos)).findFirst().orElseThrow();}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<.0004,why+" actual="+actual+" expected="+expected);}
    private static void cleanup(ServerLevel level,CompoundTag saved){peer.close();for(var body:UNITS.values())body.discard();for(var p:List.of(root,dais,lab)){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}ResearchServerEvents.removeAllResearchFor(OWNER);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r==account);for(long key:saved.getLongArray("chunks")){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}}
}
