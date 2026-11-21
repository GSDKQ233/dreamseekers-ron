package com.dreamseekers;

import com.dreamseekers.building.DreamLaboratoryPlacement;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.resonance.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.dreamseekers.unitprogression.AdvancedUnitResearch;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Two ordinary-save JVMs. Upgrade and first four new paid jobs use explicit accelerated fixtures;
 * the fifth pending paid receipt is refunded by the native saved-copy policy, then bought anew and completed naturally. Not a natural-duration test for all research. */
@GameTestHolder("dreamseekers_advanced_restart") @PrefixGameTestTemplate(false)
public final class AdvancedProgressionRestartGameTests {
    private static final String OWNER="AdvancedProgressionRestartOwner",PHASE="dreamseekers.advanced.restart.phase";
    private static final BlockPos ROOT=new BlockPos(91648,100,91648),DAIS=ROOT.offset(24,0,0),LAB=ROOT.offset(0,0,32),ECOLOGY=ROOT.offset(32,0,32);
    private static final File MANIFEST=new File("advanced-progression-restart-expectations.nbt");
    private static final List<String> KEYS=List.of("gel","target","mother","left","right");
    private static final Map<String,Mob> UNITS=new LinkedHashMap<>();
    private static final Set<Long> CHUNKS=new LinkedHashSet<>();
    private static final CompoundTag PURCHASES=new CompoundTag();
    private static DreamAnchorPlacement root;
    private static BuildingPlacement dais,ecology;
    private static DreamLaboratoryPlacement lab;
    private static Resources account;
    private static PassiveIncomePeer peer;
    private static long gelAt;

    /** Registered before saved entity loading, not at the later GameTest method. No gameplay mutation. */
    @Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
    public static final class AdvancedProgressionInitialJoinWitness {
        static final Map<UUID,CompoundTag> INITIAL=new HashMap<>();
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public static void advancedProgressionFirstJoin(EntityJoinLevelEvent event){
            if(!"verify".equals(System.getProperty(PHASE,""))||!(event.getLevel() instanceof ServerLevel)
                    ||!(event.getEntity() instanceof Mob body)||!(body instanceof SkillUnit unit)||!OWNER.equals(unit.getOwnerName()))return;
            var tag=new CompoundTag();tag.putLong("at",event.getLevel().getGameTime());
            var aps=body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get());
            // First raw attribute reads precede any named-effect getter that could prune a stale modifier.
            tag.putBoolean("transientModifierPresent",aps.getModifier(BuffKind.ATTACK_SPEED.modifierId)!=null);
            tag.putDouble("aps",aps.getValue());tag.putDouble("base",aps.getBaseValue());
            tag.putBoolean("harmony",AaroncosHarmony.active(unit));tag.putBoolean("nest",MotherNestStance.active(body));
            tag.putFloat("shield",SkillEffects.shieldRemaining(body));INITIAL.putIfAbsent(body.getUUID(),tag);
        }
    }
    @GameTest(templateNamespace="dreamseekers_advanced_restart",template="dreamseekers_test_arena",batch="advanced_progression_restart",timeoutTicks=1800)
    public static void paidAdvancedProgressionAndRuntimeBoundariesCrossTwoNormalJvms(GameTestHelper h)throws Exception{
        switch(System.getProperty(PHASE,"")){
            case "prepare"->prepare(h);case "verify"->verify(h);
            default->throw new IllegalArgumentException(PHASE+" prepare|verify required");
        }
    }
    private static List<ReceiptBackedResearchItem> completedItems(){return List.of(AdvancedUnitResearch.STONE_RANGE,AdvancedUnitResearch.MOTHER_NEST,AdvancedUnitResearch.HAND_HARMONY,AdvancedUnitResearch.LIGHT_EXTENSION);}
    private static void prepare(GameTestHelper h){
        var level=h.getLevel();h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"fresh isolated restart fixture");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(OWNER,DreamseekersMod.DREAMSEEKERS,97562,0,false,0L,0L));
        account=new Resources(OWNER,10000,10000,10000,37);account.extra.put(DreamPassiveEconomy.SHADOW,1000);ResourcesServerEvents.resourcesList.add(account);peer=new PassiveIncomePeer(level,OWNER);
        for(int x=(ROOT.getX()-32)>>4;x<=(ROOT.getX()+80)>>4;x++)for(int z=(ROOT.getZ()-32)>>4;z<=(ROOT.getZ()+80)>>4;z++){
            long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"isolated ticket ownership");level.setChunkForced(x,z,true);CHUNKS.add(key);level.getChunk(x,z);
        }
        root=(DreamAnchorPlacement)install(level,DreamseekersMod.DREAM_ANCHOR,ROOT);dais=install(level,DreamseekersMod.RESONANCE_DAIS,DAIS);
        lab=(DreamLaboratoryPlacement)install(level,DreamseekersMod.RESONANCE_LABORATORY,LAB);ecology=install(level,DreamseekersMod.CONTRACT_ECOLOGY,ECOLOGY);
        // Existing prerequisite grants are explicit fixtures, not claimed as newly purchased evidence.
        ResearchServerEvents.addResearch(OWNER,ResonanceResearch.ID);ResearchServerEvents.addResearch(OWNER,LightResonanceResearch.FIRST_ID);
        h.assertTrue(DreamWorldTreeService.start(peer.actor,root,root.tree().instance,WorldTreeModel.WIND_V5).equals("started"),"actual paid wind upgrade start");
        root.tree().remaining=1; // explicit geometry-fixture acceleration only
        var sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(root.model()==WorldTreeModel.WIND_V5&&root.tree().phase==WorldTreeState.Phase.COMPLETE&&lab.available(),"actual completed wind geometry"));
        for(var item:completedItems()){
            sequence.thenExecute(()->{pay(h,item);lab.productionQueue.get(0).ticksLeft=1;}) // explicit paid-work acceleration
                .thenWaitUntil(()->h.assertTrue(item.hasCompleted(OWNER)&&lab.productionQueue.isEmpty(),"actual paid commit "+item.id()));
        }
        sequence.thenExecute(()->{
            pay(h,AdvancedUnitResearch.PULSE_RANGE);h.assertTrue(lab.productionQueue.get(0).ticksLeft==900,"pending fifth original forty-five-second timer, not accelerated");
            spawn(h,"gel",DreamseekersMod.PINK_SLIME_UNIT.get(),2,-5);spawn(h,"target",DreamseekersMod.PINK_SLIME_UNIT.get(),4,-5);
            spawn(h,"mother",DreamseekersMod.BEETLE_MOTHER_UNIT.get(),-8,-6);
            spawn(h,"left",DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get(),16,-10);spawn(h,"right",DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get(),21,-10);
        }).thenWaitUntil(()->{for(var body:UNITS.values())ready(h,body);h.assertTrue(AaroncosHarmony.active((SkillUnit)UNITS.get("left"))&&AaroncosHarmony.active((SkillUnit)UNITS.get("right")),"real discovered live harmony on both hands");})
        .thenExecute(()->{
            cast(h,"mother","mother",UnitSkill.MOTHER_NEST);h.assertTrue(MotherNestStance.active(UNITS.get("mother")),"real authenticated stationary nest active");
            gelAt=level.getGameTime();cast(h,"gel","target",UnitSkill.GEL_GUARD);
            h.assertTrue(((SkillUnit)UNITS.get("gel")).rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==324&&SkillEffects.shieldTicks(UNITS.get("target"))==192,"real extension CD and duration before save");
            MinecraftForge.EVENT_BUS.register(new AdvancedProgressionStopWitness());
            PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(level);ResourcesServerEvents.saveResources(level);ResearchServerEvents.saveResearch();
            System.out.println("ADVANCED_PROGRESSION_PREPARED_WAIT_NORMAL_STOP");h.succeed();
        });
    }
    public static final class AdvancedProgressionStopWitness {
        @SubscribeEvent(priority=EventPriority.HIGH)
        public void advancedProgressionNormalStopping(ServerStoppingEvent event){
            long now=event.getServer().overworld().getGameTime();
            if(now>=gelAt+192||!MotherNestStance.active(UNITS.get("mother"))||SkillEffects.shieldRemaining(UNITS.get("target"))<=0
                    ||!AaroncosHarmony.active((SkillUnit)UNITS.get("left"))||!AaroncosHarmony.active((SkillUnit)UNITS.get("right")))throw new AssertionError("all new runtime states must actually remain live at normal stop");
            if(lab.productionQueue.size()!=1||lab.productionQueue.get(0).ticksLeft<=0||AdvancedUnitResearch.PULSE_RANGE.hasCompleted(OWNER))throw new AssertionError("fifth paid job must still be pending at stop");
            var active=lab.productionQueue.get(0);if(active.receiptResearch==null||!account.paidReceipts.containsKey(active.receiptResearch.receiptId()))throw new AssertionError("original live fifth receipt");
            for(var item:completedItems())if(!item.hasCompleted(OWNER)||account.paidReceipts.containsKey(PURCHASES.getUUID(item.id().getPath())))throw new AssertionError("four consumed paid completion receipts");
            if(account.paidReceipts.size()!=1)throw new AssertionError("exactly one unsettled paid receipt");
            var tag=new CompoundTag();tag.putInt("format",1);tag.putBoolean("normalStopWitness",true);tag.putLong("stopTime",now);tag.putLong("gelAt",gelAt);
            tag.putLong("jvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());tag.putLongArray("chunks",new ArrayList<>(CHUNKS));
            tag.putUUID("epoch",account.accountEpoch);tag.putUUID("rootInstance",root.tree().instance);tag.putUUID("labInstance",ResearchPlacementIdentity.serverIdentity(lab));
            var projected=ResourcesSaveData.getInstance(event.getServer().overworld()).resources.stream().filter(r->r.ownerName.equals(OWNER)).findFirst().orElseThrow();
            if(projected.food!=account.food||projected.wood!=account.wood+150||projected.ore!=account.ore+125
                    ||projected.emerald!=account.emerald||projected.extra.get(DreamPassiveEconomy.SHADOW)!=account.extra.get(DreamPassiveEconomy.SHADOW)+25
                    ||!projected.paidReceipts.isEmpty())throw new AssertionError("ordinary saved copy must refund exact original 150W125O25S once without mutating live queue/account");
            tag.put("projectedAccount",accountTag(projected));
            tag.putUUID("pendingReceipt",active.receiptResearch.receiptId());tag.putDouble("pendingTicks",active.ticksLeft);tag.put("purchases",PURCHASES.copy());tag.put("account",accountTag());
            for(var entry:UNITS.entrySet()){
                var body=entry.getValue();var unit=(SkillUnit)body;var state=new CompoundTag();tag.putUUID(entry.getKey(),body.getUUID());
                state.putFloat("health",body.getHealth());state.putInt("scenario",unit.getScenarioRoleIndex());state.putLong("revision",unit.rts().skills().revision());
                state.putInt("gelCD",unit.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD));state.putDouble("apsBase",body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue());tag.put(entry.getKey()+"State",state);
            }
            try{NbtIo.writeCompressed(tag,MANIFEST);}catch(Exception failure){throw new RuntimeException(failure);}
            peer.close();System.out.println("ADVANCED_PROGRESSION_STOP_WITNESS four_completed_one_pending_live_nest_harmony_extended_gel stopTime="+now);
        }
    }
    private static void verify(GameTestHelper h)throws Exception{
        h.assertTrue(MANIFEST.isFile(),"normal-stop manifest exists");var saved=NbtIo.readCompressed(MANIFEST);var level=h.getLevel();
        h.assertTrue(saved.getInt("format")==1&&saved.getBoolean("normalStopWitness")&&saved.getLong("jvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different JVM with exact normal-stop witness");
        for(long key:saved.getLongArray("chunks")){h.assertTrue(level.getForcedChunks().contains(key),"original saved chunk tickets restored");var pos=new ChunkPos(key);level.getChunk(pos.x,pos.z);}
        account=ExtendedResourcesSync.account(OWNER);h.assertTrue(account!=null&&account.accountEpoch.equals(saved.getUUID("epoch")),"same saved resource epoch");
        root=(DreamAnchorPlacement)find(level,ROOT);dais=find(level,DAIS);lab=(DreamLaboratoryPlacement)find(level,LAB);ecology=find(level,ECOLOGY);peer=new PassiveIncomePeer(level,OWNER);
        final CompoundTag[] repaidAccount={null};final UUID[] newReceipt={null};final long[] repaidAt={0};
        h.startSequence().thenWaitUntil(()->{for(var key:KEYS){var body=level.getEntity(saved.getUUID(key));h.assertTrue(body instanceof Mob&&body instanceof SkillUnit,"saved original UUID restored "+key);ready(h,(Mob)body);}})
        .thenExecute(()->{
            h.assertTrue(root.tree().instance.equals(saved.getUUID("rootInstance"))&&root.model()==WorldTreeModel.WIND_V5&&root.tree().phase==WorldTreeState.Phase.COMPLETE,"same physical upgraded tree");
            h.assertTrue(ResearchPlacementIdentity.serverIdentity(lab).equals(saved.getUUID("labInstance")),"same actual laboratory identity");
            h.assertTrue(accountTag().equals(saved.getCompound("projectedAccount")),"ordinary loader restores exact saved-copy refund including shadow energy once");
            for(var item:completedItems())h.assertTrue(item.hasCompleted(OWNER),"actual saved completed research "+item.id());
            h.assertTrue(!AdvancedUnitResearch.PULSE_RANGE.hasCompleted(OWNER)&&lab.productionQueue.isEmpty()&&account.paidReceipts.isEmpty(),"native restart cancels unfinished queue; refund grants no completion and retains no spent receipt");
            var refunded=accountTag();
            h.assertTrue(ReceiptResearchService.cancel(peer.actor,lab,saved.getUUID("pendingReceipt"),ResearchPlacementIdentity.serverIdentity(lab),lab.getCaptureRevision(),account.accountEpoch,account.revision).equals("unknown_receipt"),"old pending receipt cannot refund twice");
            ReceiptResearchService.retryDetachedRefunds(level.getServer());h.assertTrue(refunded.equals(accountTag()),"repeat orphan retry and old cancellation do not refund twice");
            var before=accountTag();for(var item:completedItems())h.assertTrue(start(item).equals("duplicate"),"completed research duplicate cannot charge");h.assertTrue(before.equals(accountTag()),"duplicate calls leave account exact");
            for(var key:KEYS){
                var body=(Mob)level.getEntity(saved.getUUID(key));UNITS.put(key,body);var unit=(SkillUnit)body;var old=saved.getCompound(key+"State");
                h.assertTrue(unit.getOwnerName().equals(OWNER)&&unit.getScenarioRoleIndex()==old.getInt("scenario")&&UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(body.getUUID())).count()==1,"one original owned UUID/scenario "+key);
                h.assertTrue(Math.abs(body.getHealth()-old.getFloat("health"))<.001&&unit.rts().skills().revision()>=old.getLong("revision"),"HP and monotone replay revision retained "+key);
                int expected=(int)Math.max(0,saved.getLong("stopTime")+old.getInt("gelCD")-level.getGameTime());h.assertTrue(unit.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==expected,"exact original absolute cooldown "+key);
                var initial=AdvancedProgressionInitialJoinWitness.INITIAL.get(body.getUUID());h.assertTrue(initial!=null,"first-load observer ran before later test assertions "+key);
                h.assertTrue(!initial.getBoolean("nest")&&!initial.getBoolean("harmony")&&initial.getFloat("shield")==0,"no saved transient runtime record at initial publication "+key);
                h.assertTrue(!initial.getBoolean("transientModifierPresent")&&Math.abs(initial.getDouble("aps")-old.getDouble("apsBase"))<.0001,"raw pre-pruning attribute has no saved transient APS modifier at first publication "+key);
            }
            h.assertTrue(!MotherNestStance.active(UNITS.get("mother"))&&SkillEffects.shieldRemaining(UNITS.get("target"))==0,"mother stance and gel do not restart");
            var gel=(SkillUnit)UNITS.get("gel");h.assertTrue(gel.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)>0&&SkillService.request(peer.actor,request(gel,UNITS.get("target"),UnitSkill.GEL_GUARD))==SkillResult.COOLDOWN,"original shortened but pending CD blocks premature cast");
        }).thenWaitUntil(()->h.assertTrue(AaroncosHarmony.active((SkillUnit)UNITS.get("left"))&&AaroncosHarmony.active((SkillUnit)UNITS.get("right")),"legal nearby hands independently rediscover a new lease after clean initial publication"))
          .thenExecute(()->{
              h.assertTrue(accountTag().equals(saved.getCompound("projectedAccount")),"refund remains exactly once through initial live ticks");
              pay(h,AdvancedUnitResearch.PULSE_RANGE);newReceipt[0]=lab.productionQueue.get(0).receiptResearch.receiptId();repaidAccount[0]=accountTag();repaidAt[0]=level.getGameTime();
              h.assertTrue(!newReceipt[0].equals(saved.getUUID("pendingReceipt"))&&lab.productionQueue.get(0).ticksLeft==900,"new explicit payment creates distinct receipt and full work, never resurrects canceled queue");
          })
          .thenWaitUntil(()->h.assertTrue(AdvancedUnitResearch.PULSE_RANGE.hasCompleted(OWNER)&&lab.productionQueue.isEmpty(),"new fifth paid research completes through natural post-repurchase work ticks"))
          .thenExecute(()->{
              h.assertTrue(account.paidReceipts.isEmpty()&&!account.paidReceipts.containsKey(newReceipt[0]),"new fifth receipt consumed once after canceled old receipt");
              h.assertTrue(level.getGameTime()-repaidAt[0]>=719,"real 900 work at earned wind125percent; no fixture acceleration for repurchased job");
              var old=repaidAccount[0];h.assertTrue(account.food==old.getInt("food")&&account.wood==old.getInt("wood")&&account.ore==old.getInt("ore")&&account.emerald==old.getInt("emerald")&&account.extra.get(DreamPassiveEconomy.SHADOW)==old.getInt("shadow"),"new completion does not debit/refund/mint resources");
              for(var item:AdvancedUnitResearch.items())h.assertTrue(item.hasCompleted(OWNER)&&start(item).equals("duplicate"),"all five saved/newly completed one-time identities reject duplicate");
              h.assertTrue(!MotherNestStance.active(UNITS.get("mother"))&&SkillEffects.shieldRemaining(UNITS.get("target"))==0,"no delayed pose or shield replay");
              cleanup(level,saved);System.out.println("ADVANCED_PROGRESSION_RESTART_VERIFY_PASS five_paid_research_old_pending_refunded_once_new_payment_extended_CD_initial_transients_absent_fresh_harmony");h.succeed();
          });
    }
    private static String start(ReceiptBackedResearchItem item){return ReceiptResearchService.start(peer.actor,lab,item,ResearchPlacementIdentity.serverIdentity(lab),lab.getCaptureRevision(),account.accountEpoch,account.revision);}
    private static void pay(GameTestHelper h,ReceiptBackedResearchItem item){
        var cost=item.quote(lab);int f=account.food,w=account.wood,o=account.ore,s=account.extra.get(DreamPassiveEconomy.SHADOW);
        h.assertTrue(start(item).equals("started"),"actual paid queue start "+item.id());
        h.assertTrue(account.food==f-cost.food()&&account.wood==w-cost.wood()&&account.ore==o-cost.ore()&&account.extra.get(DreamPassiveEconomy.SHADOW)==s-cost.extra().getOrDefault(DreamPassiveEconomy.SHADOW,0),"exact original quote debit "+item.id());
        var binding=lab.productionQueue.get(0).receiptResearch;h.assertTrue(binding!=null&&account.paidReceipts.containsKey(binding.receiptId()),"real account-bound paid receipt");PURCHASES.putUUID(item.id().getPath(),binding.receiptId());
    }
    private static CompoundTag accountTag(){return accountTag(account);}
    private static CompoundTag accountTag(Resources value){var tag=new CompoundTag();tag.putInt("food",value.food);tag.putInt("wood",value.wood);tag.putInt("ore",value.ore);tag.putInt("emerald",value.emerald);tag.putInt("shadow",value.extra.get(DreamPassiveEconomy.SHADOW));value.writeExtended(tag);return tag;}
    private static BuildingPlacement install(ServerLevel level,Building type,BlockPos pos){
        for(int x=0;x<22;x++)for(int z=0;z<22;z++){level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<22;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(var block:BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(level),level,pos,Rotation.NONE))level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        var p=BuildingUtils.getNewBuildingPlacement(type,level,pos,Rotation.NONE,OWNER,false);BuildingServerEvents.getBuildings().add(p);p.onBuilt();return p;
    }
    private static void spawn(GameTestHelper h,String key,EntityType<? extends Mob> type,int x,int z){
        var body=type.create(h.getLevel());var unit=(SkillUnit)body;unit.setOwnerName(OWNER);unit.setScenarioRoleIndex(6100+UNITS.size());body.setNoAi(true);
        var pos=DAIS.offset(x,1,z);for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){h.getLevel().setBlock(pos.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<8;y++)h.getLevel().setBlock(pos.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);}
        body.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(body),"actual owned entity publication "+key);UNITS.put(key,body);
    }
    private static void ready(GameTestHelper h,Mob body){h.assertTrue(body.tickCount>3&&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body&&UnitServerEvents.getAllUnits().contains(body),"exact original loaded/ticked entity");}
    private static void cast(GameTestHelper h,String from,String to,UnitSkill skill){var source=(SkillUnit)UNITS.get(from);h.assertTrue(SkillService.request(peer.actor,request(source,UNITS.get(to),skill))==SkillResult.SUCCESS,"authenticated real cast "+skill);}
    private static SkillRequest request(SkillUnit source,Entity target,UnitSkill skill){return new SkillRequest(skill,source.mob().getId(),source.mob().getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID());}
    private static BuildingPlacement find(ServerLevel level,BlockPos pos){return BuildingServerEvents.getBuildings().stream().filter(p->p.level==level&&p.ownerName.equals(OWNER)&&p.originPos.equals(pos)).findFirst().orElseThrow();}
    private static void cleanup(ServerLevel level,CompoundTag saved){peer.close();for(var body:UNITS.values())body.discard();for(var p:List.of(root,dais,lab,ecology)){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}ResearchServerEvents.removeAllResearchFor(OWNER);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r==account);for(long key:saved.getLongArray("chunks")){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}}
}
