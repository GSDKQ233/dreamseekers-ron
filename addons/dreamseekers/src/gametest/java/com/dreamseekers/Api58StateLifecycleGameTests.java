package com.dreamseekers;

import com.dreamseekers.unit.BeetleUnit;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.skill.*;
import com.dreamseekers.summon.BoundedSummons;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;

/** Positive lifecycle regressions: production paths, exact real Forge/runtime ticket evidence. */
@GameTestHolder("audit_api58_state_lifecycle") @PrefixGameTestTemplate(false)
public final class Api58StateLifecycleGameTests {
    private static final String BATCH="api58_state_lifecycle";
    private static final List<BeetleUnit> BODIES=new ArrayList<>();
    private static final Map<BeetleUnit,ServerLevel> LEVELS=new IdentityHashMap<>();
    private static BeetleUnit stopBody;
    private static UUID stopToken;
    private static BeetleUnit summonStopControl;
    private static UUID summonControlToken;
    private static final List<BeetleUnit> STOP_SUMMONS=new ArrayList<>();
    private static final Map<BeetleUnit,UUID> SUMMON_STOP_TOKENS=new IdentityHashMap<>();
    private static Object field(Object target,String name) {
        try {
            Class<?> type=target instanceof Class<?> c?c:target.getClass();
            while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class<?>?null:target);}catch(NoSuchFieldException absent){type=type.getSuperclass();}}
            throw new NoSuchFieldException(name);
        } catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static UUID token(BeetleUnit body) {
        Object lease=((Map<?,?>)field(UnitServerEvents.class,"unitChunkTickets")).get(body);
        return lease==null?null:(UUID)field(lease,"neighborhoodOwner");
    }
    @SuppressWarnings("unchecked") private static Map<Long,Integer> runtime(ServerLevel level,UUID token) {
        if(token==null)return Map.of();
        Map<Long,?> all=(Map<Long,?>)field(level.getChunkSource().chunkMap.getDistanceManager(),"tickets");
        Map<Long,Integer> found=new HashMap<>();
        for(var entry:all.entrySet())for(Object obj:(Iterable<?>)entry.getValue()){
            Ticket<?> t=(Ticket<?>)obj;
            if("reignofnether:unit_neighborhood".equals(t.getType().toString())&&token.equals(field(t,"key"))) {
                if(t.getTicketLevel()!=31)throw new AssertionError("changed native neighborhood ticket level");
                found.merge(entry.getKey(),1,Integer::sum);
            }
        }
        return found;
    }
    private static CompoundTag saved(ServerLevel level) {
        var data=level.getDataStorage().get(ForcedChunksSavedData::load,"chunks");
        return data==null?new CompoundTag():data.save(new CompoundTag());
    }
    private static List<Long> persistent(ServerLevel level,UUID id) {
        var mods=saved(level).getList("ForgeForced",Tag.TAG_COMPOUND);List<Long> out=new ArrayList<>();
        for(int i=0;i<mods.size();i++){var mod=mods.getCompound(i);if(!ReignOfNether.MOD_ID.equals(mod.getString("Mod")))continue;
            var chunks=mod.getList("ModForced",Tag.TAG_COMPOUND);
            for(int j=0;j<chunks.size();j++)for(Tag entry:chunks.getCompound(j).getList("TickingEntities",Tag.TAG_INT_ARRAY))
                if(id.equals(NbtUtils.loadUUID(entry)))out.add(chunks.getCompound(j).getLong("Chunk"));
        }return out;
    }
    private static BeetleUnit spawn(GameTestHelper h,String label,int x){
        var level=h.getLevel();BlockPos pos=h.absolutePos(new BlockPos(x,2,3));
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            level.setBlock(pos.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<4;y++)level.setBlock(pos.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);
        }
        BeetleUnit body=DreamseekersMod.BEETLE.get().create(level);h.assertTrue(body!=null,"real registered permanent unit");
        body.setOwnerName("State58_"+label);body.setNoAi(true);body.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        BODIES.add(body);LEVELS.put(body,level);h.assertTrue(level.addFreshEntity(body),"actual native publication");return body;
    }
    private static void ready(GameTestHelper h,BeetleUnit b){
        h.assertTrue(b.tickCount>3&&b.isAlive()&&!b.isRemoved()&&h.getLevel().getEntity(b.getUUID())==b,"ordinary exact live entity ticks");
        h.assertTrue(persistent(h.getLevel(),b.getUUID()).size()==1,"one real persisted center ticket");
        h.assertTrue(runtime(h.getLevel(),token(b)).size()==25,"exact existing five-by-five runtime neighborhood");
    }
    private static void gone(GameTestHelper h,BeetleUnit b,UUID oldToken){
        h.assertTrue(b.isRemoved()&&UnitServerEvents.getAllUnits().stream().noneMatch(e->e==b),"actual leave complete");
        h.assertTrue(persistent(h.getLevel(),b.getUUID()).isEmpty(),"terminal leave releases real UUID center ticket");
        h.assertTrue(runtime(h.getLevel(),oldToken).isEmpty()&&token(b)==null,"terminal leave releases exact runtime footprint/tracking");
    }
    @GameTest(templateNamespace="audit_api58_state_lifecycle",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=200)
    public static void ordinaryDeathReleasesOnlyDeadLifetime(GameTestHelper h){
        var dead=spawn(h,"Death",3);var alive=spawn(h,"Survivor",7);UUID[] tokens=new UUID[2];
        h.startSequence().thenWaitUntil(()->{ready(h,dead);ready(h,alive);}).thenExecute(()->{
            tokens[0]=token(dead);tokens[1]=token(alive);h.assertTrue(!tokens[0].equals(tokens[1]),"overlapping bodies have independent ticket owners");
            h.assertTrue(dead.hurt(dead.damageSources().genericKill(),100000f)&&!dead.isAlive(),"actual fatal damage");
        }).thenWaitUntil(()->h.assertTrue(dead.isRemoved(),"native final death removal"))
          .thenExecute(()->{gone(h,dead,tokens[0]);h.assertTrue(runtime(h.getLevel(),tokens[1]).size()==25&&persistent(h.getLevel(),alive.getUUID()).size()==1,"unrelated overlapping live unit retained");System.out.println("API58_STATE_DEATH_EXACT_RELEASE_OK");}).thenSucceed();
    }
    @GameTest(templateNamespace="audit_api58_state_lifecycle",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
    public static void ordinaryDiscardReleasesAllExactTickets(GameTestHelper h){
        var body=spawn(h,"Discard",3);UUID[] token={null};
        h.startSequence().thenWaitUntil(()->ready(h,body)).thenExecute(()->{token[0]=token(body);body.discard();})
          .thenExecute(()->{gone(h,body,token[0]);System.out.println("API58_STATE_DISCARD_RELEASE_OK");}).thenSucceed();
    }
    @GameTest(templateNamespace="audit_api58_state_lifecycle",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void movingNeighborhoodDropsOldCellsWithoutReleasingOverlapOrVanillaForce(GameTestHelper h){
        var moving=spawn(h,"Moving",3);var retained=spawn(h,"Retained",7);UUID[] owners=new UUID[2];Set<Long> old=new HashSet<>();ChunkPos[] vanilla={null};boolean[] added={false};
        h.startSequence().thenWaitUntil(()->{ready(h,moving);ready(h,retained);}).thenExecute(()->{
            owners[0]=token(moving);owners[1]=token(retained);old.addAll(runtime(h.getLevel(),owners[0]).keySet());
            vanilla[0]=new ChunkPos(moving.blockPosition());added[0]=!h.getLevel().getForcedChunks().contains(vanilla[0].toLong());if(added[0])h.getLevel().setChunkForced(vanilla[0].x,vanilla[0].z,true);
            BlockPos destination=moving.blockPosition().offset(16*6,0,0);h.getLevel().getChunkAt(destination);
            h.getLevel().setBlock(destination.below(),Blocks.STONE.defaultBlockState(),2);moving.moveTo(destination.getX()+.5,destination.getY(),destination.getZ()+.5);
        }).thenWaitUntil(()->{var current=runtime(h.getLevel(),owners[0]);h.assertTrue(current.size()==25&&Collections.disjoint(old,current.keySet()),"actual subsequent Unit.tick moved bounded footprint, no old cells retained");})
          .thenExecute(()->{moving.discard();gone(h,moving,owners[0]);h.assertTrue(runtime(h.getLevel(),owners[1]).size()==25,"other unit still owns its full overlapping original footprint");h.assertTrue(h.getLevel().getForcedChunks().contains(vanilla[0].toLong()),"unrelated vanilla force survives exact-token cleanup");if(added[0])h.getLevel().setChunkForced(vanilla[0].x,vanilla[0].z,false);System.out.println("API58_STATE_MOVE_OVERLAP_VANILLA_OK");}).thenSucceed();
    }
    @GameTest(templateNamespace="audit_api58_state_lifecycle",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void unloadRetainsPersistedCenterAndReloadGetsFreshRuntimeLifetime(GameTestHelper h){
        var original=spawn(h,"Reload",3);UUID[] old={null};BeetleUnit[] restored={null};
        h.startSequence().thenWaitUntil(()->ready(h,original)).thenExecute(()->{
            old[0]=token(original);CompoundTag entityData=original.saveWithoutId(new CompoundTag());original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(persistent(h.getLevel(),original.getUUID()).size()==1&&runtime(h.getLevel(),old[0]).isEmpty()&&token(original)==null,"real unload preserves durable live ticket but retires runtime object footprint");
            restored[0]=DreamseekersMod.BEETLE.get().create(h.getLevel());restored[0].load(entityData);BODIES.add(restored[0]);LEVELS.put(restored[0],h.getLevel());h.assertTrue(h.getLevel().addFreshEntity(restored[0]),"actual same-process saved entity reload");
        }).thenWaitUntil(()->ready(h,restored[0])).thenExecute(()->{
            h.assertTrue(restored[0].getUUID().equals(original.getUUID())&&!old[0].equals(token(restored[0]))&&runtime(h.getLevel(),old[0]).isEmpty(),"same saved UUID, fresh exact runtime lease, no resurrection of old tickets");
            restored[0].discard();h.assertTrue(persistent(h.getLevel(),original.getUUID()).isEmpty(),"reloaded lifetime death can release retained center once");System.out.println("API58_STATE_UNLOAD_RELOAD_OK_NOT_TWO_JVM");
        }).thenSucceed();
    }
    @GameTest(templateNamespace="audit_api58_state_lifecycle",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
    public static void changedDimensionRemovalRetiresSourceDimensionTicket(GameTestHelper h){
        var body=spawn(h,"Dimension",3);UUID[] old={null};
        h.startSequence().thenWaitUntil(()->ready(h,body)).thenExecute(()->{old[0]=token(body);body.remove(Entity.RemovalReason.CHANGED_DIMENSION);})
          .thenExecute(()->{gone(h,body,old[0]);System.out.println("API58_STATE_DIMENSION_REMOVAL_OK_NOT_PORTAL_E2E");}).thenSucceed();
    }
    public static final class StopWitness {
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(ServerStoppingEvent e){
            if(stopBody==null||!stopBody.isAlive()||persistent(LEVELS.get(stopBody),stopBody.getUUID()).size()!=1||runtime(LEVELS.get(stopBody),stopToken).size()!=25)
                throw new AssertionError("real live ticket/footprint required before clean server stop");
            System.out.println("API58_STATE_STOP_LIVE_BEFORE_OK body="+stopBody.getUUID());
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void after(ServerStoppingEvent e){
            if(persistent(LEVELS.get(stopBody),stopBody.getUUID()).size()!=1||!runtime(LEVELS.get(stopBody),stopToken).isEmpty()||token(stopBody)!=null
                ||!((Map<?,?>)field(UnitServerEvents.class,"formationDispatchQueue")).isEmpty())throw new AssertionError("clean stop must retain durable live ticket, clear runtime lease and delayed orders");
            System.out.println("API58_STATE_STOP_PERSISTENT_RETAINED_RUNTIME_CLEARED_OK body="+stopBody.getUUID());
        }
    }
    @GameTest(templateNamespace="audit_api58_state_lifecycle",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
    public static void prepareActualCleanShutdownWitness(GameTestHelper h){
        stopBody=spawn(h,"Stop",3);h.startSequence().thenWaitUntil(()->ready(h,stopBody)).thenExecute(()->{stopToken=token(stopBody);MinecraftForge.EVENT_BUS.register(new StopWitness());}).thenSucceed();
    }
    public static final class TemporaryStopWitness {
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(ServerStoppingEvent e){
            ServerLevel level=LEVELS.get(summonStopControl);
            if(STOP_SUMMONS.size()!=2||!summonStopControl.isAlive()||persistent(level,summonStopControl.getUUID()).size()!=1
                    ||runtime(level,summonControlToken).size()!=25)throw new AssertionError("real permanent same-type control required before stop");
            for(BeetleUnit body:STOP_SUMMONS)if(!body.isAlive()||body.isRemoved()||!BoundedSummons.canAct(body)
                    ||persistent(level,body.getUUID()).size()!=1||runtime(level,SUMMON_STOP_TOKENS.get(body)).size()!=25)
                throw new AssertionError("actual paid active temporary group and tickets required before stop");
            System.out.println("API58_SUMMON_STOP_LIVE_BEFORE_OK bodies="+STOP_SUMMONS.stream().map(Entity::getUUID).toList());
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void after(ServerStoppingEvent e){
            ServerLevel level=LEVELS.get(summonStopControl);
            for(BeetleUnit body:STOP_SUMMONS)if(!persistent(level,body.getUUID()).isEmpty()
                    ||!runtime(level,SUMMON_STOP_TOKENS.get(body)).isEmpty()||token(body)!=null||body.isRemoved())
                throw new AssertionError("temporary stop releases exact persistent/runtime tickets without discard callbacks");
            if(persistent(level,summonStopControl.getUUID()).size()!=1||!runtime(level,summonControlToken).isEmpty()
                    ||token(summonStopControl)!=null||summonStopControl.isRemoved())throw new AssertionError("ordinary same-type center must survive clean stop");
            System.out.println("API58_SUMMON_STOP_TEMP_CENTERS_RELEASED_PERMANENT_RETAINED_OK");
        }
    }
    @GameTest(templateNamespace="audit_api58_state_lifecycle",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void prepareActualTemporarySummonShutdownWitness(GameTestHelper h){
        String owner="State58_SummonStop";ServerLevel level=h.getLevel();
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
        Resources account=new Resources(owner,1000,1000,1000);account.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(account);
        ResearchServerEvents.addCheat(owner,"foodforthought"); // Fixture population supply; cast still authenticates and pays normally.
        BlockPos origin=h.absolutePos(new BlockPos(15,2,15));BlockPos center=new BlockPos(origin.getX(),100,origin.getZ());
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-10,-1,-10),center.offset(10,5,10)))level.setBlock(pos,pos.getY()==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        var caster=DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(level);h.assertTrue(caster!=null,"real caster type");
        caster.setOwnerName(owner);caster.setNoAi(true);caster.moveTo(center.getX()+.5,100,center.getZ()+.5);h.assertTrue(level.addFreshEntity(caster),"actual caster publication");
        summonStopControl=DreamseekersMod.BEETLE.get().create(level);h.assertTrue(summonStopControl!=null,"real same-type ordinary control");
        summonStopControl.setOwnerName(owner);summonStopControl.setNoAi(true);summonStopControl.moveTo(center.getX()+8.5,100,center.getZ()+8.5);
        BODIES.add(summonStopControl);LEVELS.put(summonStopControl,level);h.assertTrue(level.addFreshEntity(summonStopControl),"ordinary same-type publication");
        h.startSequence().thenWaitUntil(()->{ready(h,summonStopControl);h.assertTrue(caster.tickCount>3&&level.getEntity(caster.getUUID())==caster,"fully published live caster");})
          .thenExecute(()->{
            try(var peer=new PassiveIncomePeer(level,owner)){
                var request=new SkillRequest(UnitSkill.MOTHER_BROOD,caster.getId(),caster.getUUID(),caster.rts().skills().revision(),caster.getId(),caster.getUUID());
                h.assertTrue(SkillService.request(peer.actor,request)==SkillResult.SUCCESS,"real authenticated paid temporary group before stop");
            }
            UnitServerEvents.getAllUnits().stream().filter(e->e instanceof BeetleUnit b&&owner.equals(b.getOwnerName())&&BoundedSummons.isTemporary(e))
                .map(e->(BeetleUnit)e).forEach(b->{b.setNoAi(true);STOP_SUMMONS.add(b);});
            h.assertTrue(STOP_SUMMONS.size()==2&&account.extra.get(DreamPassiveEconomy.SHADOW)==470,"two actual temporary births, original cost paid");
          }).thenWaitUntil(()->{for(BeetleUnit body:STOP_SUMMONS)ready(h,body);})
          .thenExecute(()->{summonControlToken=token(summonStopControl);for(BeetleUnit body:STOP_SUMMONS)SUMMON_STOP_TOKENS.put(body,token(body));MinecraftForge.EVENT_BUS.register(new TemporaryStopWitness());})
          .thenSucceed();
        // Real group, caster, account and same-type control intentionally remain until the isolated server stops.
    }
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel ignored){
        for(var body:BODIES)if(body!=stopBody&&body!=summonStopControl){var level=LEVELS.get(body);if(!body.isRemoved())body.discard();for(long chunk:persistent(level,body.getUUID()))ForgeChunkManager.forceChunk(level,ReignOfNether.MOD_ID,body.getUUID(),ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false,true);}
        // The one dedicated live stop witness remains in this isolated world until actual normal shutdown.
    }
}
