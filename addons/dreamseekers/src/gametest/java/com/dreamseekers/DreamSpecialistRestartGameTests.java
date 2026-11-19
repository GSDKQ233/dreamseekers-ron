package com.dreamseekers;

import com.dreamseekers.unitprogression.*;
import com.dreamseekers.unit.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Distinct JVM proof. Prepare really pays all four registered research; only this save-focused
 * fixture shortens remaining queue time. Separate normal suite proves full durations.
 * Ordinary worker cargo settles into the account at stop: reload must NOT recreate that cargo. */
@GameTestHolder("dreamseekers_specialist_restart")
@PrefixGameTestTemplate(false)
public final class DreamSpecialistRestartGameTests {
    private static final String OWNER="Specialist_Restart",OTHER="Specialist_Restart_NoTech";
    private static final BlockPos ORIGIN=new BlockPos(24000,100,24000);
    private static final File MANIFEST=new File("specialist-restart-expectations.nbt");
    @GameTest(templateNamespace="dreamseekers_specialist_restart",template="ore_mine_test_arena",timeoutTicks=180,batch="specialist_restart")
    public static void paidResearchAndEffectsSurviveOrdinaryRestart(GameTestHelper h)throws Exception {
        switch(System.getProperty("dreamseekers.specialist.restart.phase","")) {
            case "prepare" -> prepare(h);
            case "verify" -> verify(h);
            default -> throw new IllegalArgumentException("prepare|verify required");
        }
    }
    private static List<net.minecraft.resources.ResourceLocation> ids(){return List.of(DreamSpecialistResearch.PRECISION_TOOLS_ID,
            DreamSpecialistResearch.SHELL_WEAVING_ID,DreamSpecialistResearch.CONTRACT_CARE_ID,DreamSpecialistResearch.STONE_SIEGE_ID);}
    private static void near(GameTestHelper h,double actual,double expected,String reason){h.assertTrue(Math.abs(actual-expected)<.001,reason+": "+actual+" != "+expected);}
    private static BuildingPlacement install(ServerLevel level,Building definition,String owner,BlockPos pos){
        for(int x=0;x<24;x++)for(int z=0;z<24;z++){
            level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<20;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        for(var block:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,pos,Rotation.NONE))level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        var p=BuildingUtils.getNewBuildingPlacement(definition,level,pos,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(p);return p;
    }
    private static <T extends Mob> T spawn(ServerLevel level,EntityType<T> type,String owner,int x){
        T body=type.create(level);((Unit)body).setOwnerName(owner);body.setNoAi(true);body.setPersistenceRequired();
        BlockPos pos=ORIGIN.offset(x,1,36);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);
        body.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);if(!level.addFreshEntity(body))throw new IllegalStateException("fixture entity not published");return body;
    }
    private static void prepare(GameTestHelper h)throws Exception {
        h.assertTrue(!MANIFEST.exists(),"fresh restart fixture");var level=h.getLevel();var expected=new CompoundTag();
        expected.putLong("jvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        // Native prepareEntityTickingChunk needs FULL chunks at Chebyshev radius 2.
        // Entities occupy (1500,1502)/(1501,1502): include x1498 and z1504 too.
        // Construct this complete footprint in the fresh prepare, never mint tickets in verify.
        for(int x=1498;x<=1508;x++)for(int z=1499;z<=1504;z++){
            long c=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(c),"fixture previously unforced");level.setChunkForced(x,z,true);forced.add(c);}
        expected.putLongArray("chunks",forced);
        // Visit the complete normal-loader footprint before creating the persisted actors.
        for(long key:forced){var chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);}
        for(String owner:List.of(OWNER,OTHER)) {
            h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null&&ExtendedResourcesSync.account(owner)==null,"fresh owner identity");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(owner,DreamseekersMod.DREAMSEEKERS,owner.equals(OWNER)?95701:95702,0,false,0L,0L));
            var r=new Resources(owner,5000,5000,5000);r.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(r);expected.putUUID(owner+"Epoch",r.accountEpoch);
        }
        install(level,DreamseekersMod.DREAM_ANCHOR,OWNER,ORIGIN);
        install(level,DreamseekersMod.CONTRACT_ECOLOGY,OWNER,ORIGIN.offset(32,0,0));
        var lab=(ProductionPlacement)install(level,DreamseekersMod.RESONANCE_LABORATORY,OWNER,ORIGIN.offset(64,0,0));
        install(level,DreamseekersMod.DREAM_ANCHOR,OTHER,ORIGIN.offset(96,0,0));
        var worker=spawn(level,DreamseekersMod.FIREFLY_WORKER.get(),OWNER,4);
        var beetle=spawn(level,DreamseekersMod.BEETLE.get(),OWNER,8);
        var snail=spawn(level,DreamseekersMod.BASALT_SNAIL.get(),OWNER,12);
        var control=spawn(level,DreamseekersMod.FIREFLY_WORKER.get(),OTHER,16);
        var controlSnail=spawn(level,DreamseekersMod.BASALT_SNAIL.get(),OTHER,20);
        expected.putUUID("worker",worker.getUUID());expected.putUUID("beetle",beetle.getUUID());expected.putUUID("snail",snail.getUUID());
        expected.putUUID("control",control.getUUID());expected.putUUID("controlSnail",controlSnail.getUUID());
        expected.putDouble("beetleBase",beetle.getMaxHealth());expected.putDouble("snailBase",snail.getMaxHealth());expected.putDouble("controlBase",controlSnail.getMaxHealth());
        beetle.setHealth(beetle.getMaxHealth()*.5F);snail.setHealth(snail.getMaxHealth()*.25F);
        var actor=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(OWNER.getBytes(StandardCharsets.UTF_8)),OWNER));
        h.runAtTickTime(20,()->{
            h.assertTrue(lab.isBuilt,"ordinary model built");var r=ExtendedResourcesSync.account(OWNER);
            for(var item:List.of(DreamSpecialistResearch.PRECISION_TOOLS,DreamSpecialistResearch.SHELL_WEAVING,DreamSpecialistResearch.CONTRACT_CARE,DreamSpecialistResearch.STONE_SIEGE)) {
                h.assertTrue(ReceiptResearchService.start(actor,lab,item,ResearchPlacementIdentity.serverIdentity(lab),lab.getCaptureRevision(),r.accountEpoch,r.revision).equals("started"),"real paid restart research "+item.id());
            }
            h.assertTrue(r.food==4725&&r.wood==4625&&r.ore==4700&&r.extra.get(DreamPassiveEconomy.SHADOW)==480&&r.paidReceipts.size()==4,"all four real payments andclaims");
            for(var queued:lab.productionQueue)queued.ticksLeft=2; // Explicit persistence-only acceleration after payment.
        });
        h.runAtTickTime(50,()->{
            var r=ExtendedResourcesSync.account(OWNER);
            for(var id:ids())h.assertTrue(ResearchServerEvents.playerHasResearch(OWNER,id)&&!ResearchServerEvents.playerHasResearch(OTHER,id),"paid completion only correctowner "+id);
            h.assertTrue(lab.productionQueue.isEmpty()&&r.paidReceipts.isEmpty(),"ordinary native completion consumedall claims");
            worker.getItems().add(new ItemStack(Items.STICK,63));
            h.assertTrue(worker.getMaxResources()==125&&Unit.atThresholdResources(worker)&&worker.getResourceReturnThreshold(50)==63,"prestop real125/63 rules andcargo");
            near(h,beetle.getMaxHealth(),expected.getDouble("beetleBase")*1.1,"prestop beetle modifier");near(h,beetle.getHealth()/beetle.getMaxHealth(),.5,"prestop beetle healthratio");
            near(h,snail.getMaxHealth(),expected.getDouble("snailBase")*1.1,"prestop snail modifier");near(h,snail.getHealth()/snail.getMaxHealth(),.25,"prestop snail healthratio");
            h.assertTrue(control.getMaxResources()==100,"unresearched controlworker");
            expected.putInt("woodAfterStop",r.wood+63);expected.putLong("revision",r.revision);
            try{NbtIo.writeCompressed(expected,MANIFEST);}catch(java.io.IOException e){throw new RuntimeException(e);}
            System.out.println("SPECIALIST_RESTART_PREPARE_PASS real_four_paid_receipts shortened_save_fixture full_duration_separate cargo_projected_once");h.succeed();
        });
    }
    /** Test-only observer. Never writes entities, chunk state, research, tickets or time. */
    public static final class LoadProbe {
        final java.util.Set<java.util.UUID> expected;
        int joins,leaves,deaths;
        LoadProbe(CompoundTag manifest){expected=new java.util.HashSet<>();for(String key:List.of("worker","beetle","snail","control","controlSnail"))expected.add(manifest.getUUID(key));}
        @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
        public void join(net.minecraftforge.event.entity.EntityJoinLevelEvent e){if(expected.contains(e.getEntity().getUUID())){joins++;body("JOIN cancelled="+e.isCanceled(),e.getEntity());}}
        @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public void leave(net.minecraftforge.event.entity.EntityLeaveLevelEvent e){if(expected.contains(e.getEntity().getUUID())){leaves++;body("LEAVE",e.getEntity());}}
        @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
        public void death(net.minecraftforge.event.entity.living.LivingDeathEvent e){if(expected.contains(e.getEntity().getUUID())){deaths++;body("DEATH cancelled="+e.isCanceled()+" source="+e.getSource(),e.getEntity());}}
        private static void body(String phase,Entity entity){
            System.out.println("SPECIALIST_LOAD_EVENT phase="+phase+" uuid="+entity.getUUID()+" pos="+entity.position()+" alive="+entity.isAlive()+" removed="+entity.getRemovalReason()
                    +" health="+(entity instanceof LivingEntity living?living.getHealth():-1)+" attacker="+(entity instanceof LivingEntity living?living.getLastHurtByMob():null));
        }
    }
    private static void loadDiagnostic(ServerLevel level,CompoundTag expected,String phase,LoadProbe probe){
        net.minecraft.world.level.entity.PersistentEntitySectionManager<?> manager=null;
        try{
            for(var field:ServerLevel.class.getDeclaredFields())if(field.getType()==net.minecraft.world.level.entity.PersistentEntitySectionManager.class){field.setAccessible(true);manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level);break;}
        }catch(ReflectiveOperationException error){throw new RuntimeException("read-only entity manager diagnostic failed",error);}
        System.out.println("SPECIALIST_LOAD_STATE phase="+phase+" gameTime="+level.getGameTime()+" manager="+(manager==null?"unavailable":manager.gatherStats())
                +" chunks="+level.getChunkSource().gatherStats()+" pendingTasks="+level.getChunkSource().getPendingTasksCount()+" events="+probe.joins+","+probe.leaves+","+probe.deaths);
        for(int x:new int[]{1500,1501}) {
            var pos=new ChunkPos(x,1502);var at=new BlockPos(x*16,101,1502*16);
            System.out.println("SPECIALIST_LOAD_CHUNK phase="+phase+" chunk="+pos+" forced="+level.getForcedChunks().contains(pos.toLong())+" blockLoaded="+level.hasChunk(x,1502)
                    +" chunkTick="+level.getChunkSource().isPositionTicking(pos.toLong())+" entityTick="+level.isPositionEntityTicking(at)
                    +" entityLoaded="+(manager!=null&&manager.areEntitiesLoaded(pos.toLong()))+" canEntityTick="+(manager!=null&&manager.canPositionTick(pos))
                    +" debug="+level.getChunkSource().getChunkDebugData(pos));
        }
        for(String key:List.of("worker","beetle","snail","control","controlSnail")) {
            var id=expected.getUUID(key);var body=level.getEntity(id);
            System.out.println("SPECIALIST_LOAD_UUID phase="+phase+" key="+key+" uuid="+id+" known="+(manager!=null&&manager.isLoaded(id))+" lookup="+body);
            if(body!=null)LoadProbe.body(phase,body);
        }
    }
    private static void verify(GameTestHelper h)throws Exception {
        var expected=NbtIo.readCompressed(MANIFEST);var level=h.getLevel();
        h.assertTrue(expected.getLong("jvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"distinct JVM required");
        var probe=new LoadProbe(expected);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(probe);
        loadDiagnostic(level,expected,"before_chunk_access",probe);
        h.runAtTickTime(20,()->loadDiagnostic(level,expected,"tick20",probe));
        h.runAtTickTime(40,()->loadDiagnostic(level,expected,"tick40",probe));
        // A headless GameTest server does not visit these remote saved fixture chunks on its own.
        // Exercise the ordinary chunk/entity loader, as other independent-JVM entity fixtures do.
        // Never mint replacement tickets/entities or inject account, research, attributes or NBT.
        for(long key:expected.getLongArray("chunks")) {
            h.assertTrue(level.getForcedChunks().contains(key),"original forced chunk ticket restored from disk");
            var chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);
        }
        for(String owner:List.of(OWNER,OTHER)) {
            var r=ExtendedResourcesSync.account(owner);h.assertTrue(r!=null&&PlayerServerEvents.getRTSPlayer(owner)!=null&&r.accountEpoch.equals(expected.getUUID(owner+"Epoch")),"normal account loader preservedidentity "+owner);
        }
        h.runAtTickTime(60,()->{
            loadDiagnostic(level,expected,"tick60",probe);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(probe);
            var r=ExtendedResourcesSync.account(OWNER);var controlAccount=ExtendedResourcesSync.account(OTHER);
            h.assertTrue(r.food==4725&&r.wood==expected.getInt("woodAfterStop")&&r.ore==4700&&r.extra.get(DreamPassiveEconomy.SHADOW)==480&&r.paidReceipts.isEmpty(),"real stop settled63cargo once and preservedcompleted research costs");
            for(var id:ids())h.assertTrue(DreamSpecialistResearch.unlocked(OWNER,id,false)&&!DreamSpecialistResearch.unlocked(OTHER,id,false),"ordinary research file loaded withoutgrants "+id);
            var worker=(FireflyWorker)level.getEntity(expected.getUUID("worker"));var beetle=(BeetleUnit)level.getEntity(expected.getUUID("beetle"));var snail=(BasaltSnailUnit)level.getEntity(expected.getUUID("snail"));
            var control=(FireflyWorker)level.getEntity(expected.getUUID("control"));var controlSnail=(BasaltSnailUnit)level.getEntity(expected.getUUID("controlSnail"));
            for(String key:List.of("worker","beetle","snail","control","controlSnail")) {
                var body=level.getEntity(expected.getUUID(key));
                System.out.println("SPECIALIST_RESTART_ENTITY_DIAG key="+key+" uuid="+expected.getUUID(key)
                        +" body="+body+" chunk1500_1502="+level.hasChunk(1500,1502)+" chunk1501_1502="+level.hasChunk(1501,1502));
                h.assertTrue(body!=null,"actual saved "+key+" UUID restored by ordinary world loader, no replacement");
            }
            h.assertTrue(worker!=null&&beetle!=null&&snail!=null&&control!=null&&controlSnail!=null,"actual entity UUIDs restored by world loader, no replacements");
            h.assertTrue(worker.getMaxResources()==125&&worker.getResourceReturnThreshold(50)==63&&worker.getResourceReturnThreshold(100)==100,"restored research drives125/63 andcarrybags max");
            h.assertTrue(Resources.getTotalResourcesFromItems(worker.getItems()).getTotalValue()==0,"settled cargo not resurrected alongside accountcredit");
            near(h,beetle.getMaxHealth(),expected.getDouble("beetleBase")*1.1,"saved modifier exactlyonce after60ticks");near(h,beetle.getHealth()/beetle.getMaxHealth(),.5,"saved beetleratio");
            near(h,snail.getMaxHealth(),expected.getDouble("snailBase")*1.1,"saved snailmodifier exactlyonce");near(h,snail.getHealth()/snail.getMaxHealth(),.25,"saved snailratio");
            h.assertTrue(control.getMaxResources()==100&&control.getResourceReturnThreshold(50)==50,"unresearched worker staysbase");near(h,controlSnail.getMaxHealth(),expected.getDouble("controlBase"),"unresearched snail staysbase");
            h.assertTrue(controlAccount.food==5000&&controlAccount.wood==5000&&controlAccount.ore==5000,"otherowner account untouched");
            for(var body:List.of(worker,beetle,snail,control,controlSnail))body.discard();
            BuildingServerEvents.getBuildings().removeIf(b->Set.of(OWNER,OTHER).contains(b.ownerName));
            for(String owner:List.of(OWNER,OTHER))ResearchServerEvents.removeAllResearchFor(owner);
            PlayerServerEvents.rtsPlayers.removeIf(p->Set.of(OWNER,OTHER).contains(p.name));ResourcesServerEvents.resourcesList.removeIf(a->Set.of(OWNER,OTHER).contains(a.ownerName));
            for(long c:expected.getLongArray("chunks")){var pos=new ChunkPos(c);level.setChunkForced(pos.x,pos.z,false);}
            System.out.println("SPECIALIST_RESTART_VERIFY_PASS ordinary_accounts_research_entities cargo_conserved no_double_modifiers negative_owner distinct_JVM");h.succeed();
        });
    }
}
