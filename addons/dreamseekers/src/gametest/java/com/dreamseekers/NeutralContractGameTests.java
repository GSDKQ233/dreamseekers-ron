package com.dreamseekers;
import com.dreamseekers.neutralcontract.*;
import com.dreamseekers.economy.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import net.pasterdream.init.PasterdreamModEntities;
import net.pasterdream.entity.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("dreamseekers_neutral_contract") @PrefixGameTestTemplate(false)
public final class NeutralContractGameTests {
    private static final String BATCH="neutral_contract";
    private static final List<F> FIXTURES=new ArrayList<>();
    static final class F implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final String owner;final Resources account;final PassiveIncomePeer peer;final BlockPos origin;final BuildingPlacement court;
        final Set<Mob> freezeAfterNaturalLanding=Collections.newSetFromMap(new IdentityHashMap<>());final List<Runnable> cleanupActions=new ArrayList<>();final List<BuildingPlacement> extraBuildings=new ArrayList<>();final List<Mob> bodies=new ArrayList<>();final List<Consumer<EntityJoinLevelEvent>> probes=new ArrayList<>();final Set<Long> chunks=new HashSet<>();boolean closed;
        F(GameTestHelper h,String suffix){this.h=h;level=h.getLevel();owner="Neutral_"+suffix;FIXTURES.add(this);
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));account=new Resources(owner,1000,1000,1000);account.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(account);
            ResearchServerEvents.addCheat(owner,"foodforthought");peer=new PassiveIncomePeer(level,owner);origin=BuildingAssetGameTests.outdoorPos(h,40,1,40);
            for(int x=(origin.getX()-25)>>4;x<=(origin.getX()+30)>>4;x++)for(int z=(origin.getZ()-25)>>4;z<=(origin.getZ()+30)>>4;z++){long key=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(key)){level.setChunkForced(x,z,true);chunks.add(key);}}
            for(int x=-25;x<=30;x++)for(int z=-25;z<=30;z++){level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=16;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.CONTRACT_ECOLOGY.getRelativeBlockData(level),level,origin,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            court=BuildingUtils.getNewBuildingPlacement(DreamseekersMod.CONTRACT_ECOLOGY,level,origin,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(court);court.onBuilt();court.getDataStorage();
        }
        BuildingPlacement install(Building definition,int x,int z){BlockPos pos=origin.offset(x,0,z);
            for(int cx=(pos.getX()-2)>>4;cx<=(pos.getX()+25)>>4;cx++)for(int cz=(pos.getZ()-2)>>4;cz<=(pos.getZ()+25)>>4;cz++){long key=ChunkPos.asLong(cx,cz);if(!level.getForcedChunks().contains(key)){level.setChunkForced(cx,cz,true);chunks.add(key);}}
            for(int dx=-2;dx<25;dx++)for(int dz=-2;dz<25;dz++){level.setBlock(pos.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<24;y++)level.setBlock(pos.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);}
            for(var b:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,pos,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            var p=BuildingUtils.getNewBuildingPlacement(definition,level,pos,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(p);extraBuildings.add(p);return p;
        }
        FireflyWorker worker(int x,int z,boolean ai){var w=DreamseekersMod.FIREFLY_WORKER.get().create(level);w.setOwnerName(owner);w.setNoAi(!ai);place(w,x,z);return w;}
        Mob nativeBody(boolean snail,int x,int z){Mob m=snail?PasterdreamModEntities.BASALT_SNAIL.get().create(level):PasterdreamModEntities.BLACK_BEETLE.get().create(level);freezeAfterNaturalLanding.add(m);place(m,x,z);return m;}
        void place(Mob m,int x,int z){if(!(m instanceof FireflyWorker)&&m.isNoAi()){m.setNoAi(false);freezeAfterNaturalLanding.add(m);}m.moveTo(origin.getX()+x+.5,origin.getY()+1,origin.getZ()+z+.5);h.assertTrue(level.addFreshEntity(m),"actual entity published");bodies.add(m);}
        long readinessDiagnosticAt=Long.MIN_VALUE;
        void ready(Mob... ms){boolean all=true;for(var m:ms){boolean ready=m.tickCount>3&&(m instanceof FireflyWorker||m.onGround())&&level.getEntity(m.getUUID())==m&&level.getEntity(m.getId())==m;
                // Freeze only after the SAME original body has naturally ticked and collided with its floor.
                // Current Firefly workers never enter this set: their real flying navigation is untouched.
                if(ready&&freezeAfterNaturalLanding.remove(m))m.setNoAi(true);all&=ready;}
            if(!all&&(readinessDiagnosticAt==Long.MIN_VALUE||level.getGameTime()-readinessDiagnosticAt>=20)){readinessDiagnosticAt=level.getGameTime();for(var m:ms){var cp=new ChunkPos(m.blockPosition());var chunk=level.getChunkSource().getChunkNow(cp.x,cp.z);
                System.out.println("CONTRACT_NATIVE_READY owner="+owner+" time="+level.getGameTime()+" type="+m.getType()+" id="+m.getId()+" uuid="+m.getUUID()+" tick="+m.tickCount+" ground="+m.onGround()+" noAI="+m.isNoAi()+" noGravity="+m.isNoGravity()+" pos="+m.position()+" velocity="+m.getDeltaMovement()+" idSame="+(level.getEntity(m.getId())==m)+" uuidSame="+(level.getEntity(m.getUUID())==m)+" added="+m.isAddedToWorld()+" removed="+m.isRemoved()+" alive="+m.isAlive()+" full="+(chunk==null?"null":chunk.getFullStatus())+" entitiesLoaded="+level.areEntitiesLoaded(cp.toLong())+" entityTicking="+level.isPositionEntityTicking(m.blockPosition()));}}
            h.assertTrue(all,"original world bodies ready");}
        String start(FireflyWorker w,LivingEntity t){return NeutralContracts.begin(peer.actor,peer.connection,w,w.rts().ownershipRevision,t,UUID.randomUUID());}
        List<Mob> converted(){return UnitServerEvents.getAllUnits().stream().filter(e->e instanceof Mob&&e instanceof Unit u&&u.getOwnerName().equals(owner)&&e.getPersistentData().contains(NeutralContracts.ORIGIN)).map(e->(Mob)e).toList();}
        void noSpend(){h.assertTrue(account.food==1000&&account.ore==1000&&account.extra.get(DreamPassiveEconomy.SHADOW)==500,"no payment on unsuccessful order");}
        void guard(Consumer<EntityJoinLevelEvent> action){Consumer<EntityJoinLevelEvent> scoped=e->{if(e.getLevel()==level&&e.getEntity() instanceof Unit u&&u.getOwnerName().equals(owner)&&NeutralContracts.pending(e.getEntity()))action.accept(e);};probes.add(scoped);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,scoped);}
        @Override public void close(){if(closed)return;closed=true;cleanupActions.forEach(Runnable::run);probes.forEach(MinecraftForge.EVENT_BUS::unregister);for(var b:List.copyOf(bodies))if(b instanceof FireflyWorker w&&w.getOwnerName().equals(owner))NeutralContracts.cancel(peer.actor,w,w.rts().ownershipRevision);
            for(var e:new ArrayList<>(UnitServerEvents.getAllUnits()))if(e instanceof Unit u&&u.getOwnerName().equals(owner)){ContractLedger.get(level.getServer()).died(e.getUUID());e.discard();}bodies.forEach(Entity::discard);
            for(var p:extraBuildings){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}court.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(court);ResearchServerEvents.removeAllResearchFor(owner);ResearchServerEvents.removeCheat(owner,"foodforthought");ResourcesServerEvents.resourcesList.removeIf(r->r==account);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));peer.close();for(long key:chunks){var p=new ChunkPos(key);level.setChunkForced(p.x,p.z,false);}FIXTURES.remove(this);}
    }
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var f:List.copyOf(FIXTURES))f.close();}
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=800)
    public static void actualWorkerWalkAndEightSecondPaidBeetlePreserveInjuryAndFreeOrigin(GameTestHelper h){
        var f=new F(h,"walk");var w=f.worker(2,-15,true);var t=f.nativeBody(false,11,-15);t.setHealth(t.getMaxHealth()/2);var start=w.position();long[] begun={0};int[] population={0};
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->{population[0]=UnitServerEvents.getCurrentPopulation(f.owner);begun[0]=f.level.getGameTime();h.assertTrue(f.start(w,t).equals("approaching"),"real worker order accepted");f.noSpend();})
            .thenWaitUntil(()->h.assertTrue(!f.converted().isEmpty(),"natural pathfinding and full channel finish"))
            .thenExecute(()->{var next=f.converted().get(0);h.assertTrue(f.level.getGameTime()-begun[0]>=160&&w.distanceToSqr(start)>4,"real movement and unshortened160ticks");h.assertTrue(t.isRemoved()&&next instanceof BeetleUnit,"one native body replaced, no forced spawn proof");
                h.assertTrue(Math.abs(next.getHealth()/next.getMaxHealth()-.5)<.001,"injury ratio preserved");h.assertTrue(f.account.food==950&&f.account.ore==1000&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==485,"exact50F15S once");
                h.assertTrue(UnitServerEvents.getCurrentPopulation(f.owner)==population[0]+2&&RtsKillValueClaims.isFree(next),"native population and nonredeemable free origin");h.assertTrue(ContractLedger.get(f.level.getServer()).count(f.owner,f.account.accountEpoch)==1,"persistent slot" );f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=350)
    public static void snailCostPopulationAndDeathRelease(GameTestHelper h){
        var f=new F(h,"snail");var w=f.worker(3,-10,false);var t=f.nativeBody(true,5,-10);t.setHealth(t.getMaxHealth()*.4f);int[] pop={0};Mob[] next={null};
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->{pop[0]=UnitServerEvents.getCurrentPopulation(f.owner);h.assertTrue(f.start(w,t).equals("approaching"),"snail order accepted");})
            .thenWaitUntil(()->h.assertTrue(!f.converted().isEmpty(),"snail natural160tickchannel finishes"))
            .thenExecute(()->{next[0]=f.converted().get(0);h.assertTrue(next[0] instanceof BasaltSnailUnit&&Math.abs(next[0].getHealth()/next[0].getMaxHealth()-.4)<.001,"actual snail and healthratio");h.assertTrue(f.account.food==940&&f.account.ore==980&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==480&&UnitServerEvents.getCurrentPopulation(f.owner)==pop[0]+3,"60F20O20S3pop");next[0].hurt(next[0].damageSources().genericKill(),10000);})
            .thenExecuteAfter(3,()->{h.assertTrue(ContractLedger.get(f.level.getServer()).count(f.owner,f.account.accountEpoch)==0,"actual death releases original accountslot");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void exactNativeTypesPetsEffectsFundsAndPopulationAreFailClosed(GameTestHelper h){
        var f=new F(h,"gates");var w=f.worker(3,-10,false);var t=(BlackBeetleEntity)f.nativeBody(false,5,-10);
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->{
            t.setTame(true);h.assertTrue(f.start(w,t).equals("invalid_target"),"tame denied");t.setTame(false);t.setOwnerUUID(UUID.randomUUID());h.assertTrue(f.start(w,t).equals("invalid_target"),"pet owner denied evenfalse tame");t.setOwnerUUID(null);
            t.setLeashedTo(f.peer.actor,false);h.assertTrue(f.start(w,t).equals("invalid_target"),"leashed native is not wild");t.dropLeash(false,false);
            var leashTag=t.saveWithoutId(new CompoundTag());var leash=new CompoundTag();leash.putUUID("UUID",f.peer.actor.getUUID());leashTag.put("Leash",leash);var pendingLeash=PasterdreamModEntities.BLACK_BEETLE.get().create(f.level);pendingLeash.load(leashTag);h.assertTrue(NeutralContracts.nativeKind(pendingLeash)==null,"unresolved saved native leash denied before native restoration");
            t.setCustomName(net.minecraft.network.chat.Component.literal("pet"));h.assertTrue(f.start(w,t).equals("invalid_target"),"named native asset denied");t.setCustomName(null);
            t.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));h.assertTrue(f.start(w,t).equals("invalid_target"),"equipped native asset denied");t.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.item.ItemStack.EMPTY);
            t.setAge(-100);h.assertTrue(f.start(w,t).equals("invalid_target"),"baby denied");t.setAge(0);
            t.getPersistentData().putBoolean("foreign_owner",true);h.assertTrue(f.start(w,t).equals("invalid_target"),"foreign owner metadata denied");t.getPersistentData().remove("foreign_owner");
            var controlled=DreamseekersMod.BEETLE.get().create(f.level);controlled.setOwnerName(f.owner);f.place(controlled,8,-10);h.assertTrue(NeutralContracts.nativeKind(controlled)==null,"RTS subclass categorically excluded");
            f.account.extra.put(DreamPassiveEconomy.SHADOW,14);h.assertTrue(f.start(w,t).equals("insufficient"),"insufficientactualshadow denied");f.account.extra.put(DreamPassiveEconomy.SHADOW,500);
            ResearchServerEvents.removeCheat(f.owner,"foodforthought");h.assertTrue(f.start(w,t).equals("insufficient"),"no physicalpopulation supply denied");f.noSpend();f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void ReservationsCountBeforeCompletionAndCapacityResearchIsReal(GameTestHelper h){
        var f=new F(h,"capacity");var workers=new ArrayList<FireflyWorker>();var targets=new ArrayList<Mob>();for(int i=0;i<6;i++){workers.add(f.worker(-12+i*4,-10,false));targets.add(f.nativeBody(false,-10+i*4,-10));}
        h.startSequence().thenWaitUntil(()->{for(int i=0;i<6;i++)f.ready(workers.get(i),targets.get(i));}).thenExecute(()->{
            for(int i=0;i<3;i++)h.assertTrue(f.start(workers.get(i),targets.get(i)).equals("approaching"),"reservation "+i);h.assertTrue(NeutralContracts.used(workers.get(0))==3&&f.start(workers.get(3),targets.get(3)).equals("full"),"pending orders consume3slots");
            ResearchServerEvents.addResearch(f.owner,ContractResearch.ID);h.assertTrue(ContractResearch.capacity(f.owner,false)==5,"real native research expands3to5");
            h.assertTrue(f.start(workers.get(3),targets.get(3)).equals("approaching")&&f.start(workers.get(4),targets.get(4)).equals("approaching")&&f.start(workers.get(5),targets.get(5)).equals("full"),"five notsix reservations");
            NeutralContracts.cancel(f.peer.actor,workers.get(0),workers.get(0).rts().ownershipRevision);h.assertTrue(NeutralContracts.used(workers.get(1))==4,"cancel releases onlytransientreservation");f.noSpend();f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=300)
    public static void NewOrdersAndWorkerHostOrNativeTameAbaCannotResume(GameTestHelper h){
        var f=new F(h,"interrupt");var w=f.worker(3,-10,false);var t=(BlackBeetleEntity)f.nativeBody(false,5,-10);
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->{h.assertTrue(f.start(w,t).equals("approaching"),"start");Unit.fullResetBehaviours(w);w.setMoveTarget(f.origin.offset(-10,1,-10));})
            .thenExecuteAfter(2,()->{h.assertTrue(!NeutralContracts.active(w)&&w.getMoveGoal().getMoveTarget().equals(f.origin.offset(-10,1,-10)),"new command preserved, old channel canceled");h.assertTrue(f.start(w,t).equals("approaching"),"restart explicit");w.setOwnerName("Other");w.setOwnerName(f.owner);})
            .thenExecuteAfter(2,()->{h.assertTrue(!NeutralContracts.active(w),"workerABA invalid");h.assertTrue(f.start(w,t).equals("approaching"),"new tameprobe");t.setTame(true);t.setTame(false);})
            .thenExecuteAfter(2,()->{h.assertTrue(!NeutralContracts.active(w),"same ticknative tameABA invalid");h.assertTrue(f.start(w,t).equals("approaching"),"new hostprobe");f.court.setOwnerNameFromServerEditor("Other");f.court.setOwnerNameFromServerEditor(f.owner);})
            .thenExecuteAfter(180,()->{h.assertTrue(!NeutralContracts.active(w)&&f.converted().isEmpty()&&t.isAlive(),"capturedcourtcannotlatecomplete");f.noSpend();f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=300)
    public static void DamageAndLateInsufficientFundsCannotChargeOrConvert(GameTestHelper h){
        var f=new F(h,"damage");var w=f.worker(3,-10,false);var t=f.nativeBody(false,5,-10);
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->{h.assertTrue(f.start(w,t).equals("approaching"),"start damageprobe");w.hurt(w.damageSources().generic(),1);})
            .thenExecuteAfter(30,()->{h.assertTrue(!NeutralContracts.active(w)&&f.converted().isEmpty(),"real damage invalidateschannel");h.assertTrue(f.start(w,t).equals("approaching"),"freshaftercombatrest");f.account.extra.put(DreamPassiveEconomy.SHADOW,14);})
            .thenExecuteAfter(180,()->{h.assertTrue(!NeutralContracts.active(w)&&f.converted().isEmpty()&&t.isAlive()&&f.account.food==1000&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==14,"completion affordability failure preservesbodyandactualbalance");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=300)
    public static void PublicationCancellationAndReentryRollbackExactCandidate(GameTestHelper h){
        var f=new F(h,"join_cancel");var w=f.worker(3,-10,false);var t=f.nativeBody(false,5,-10);int[] joined={0};
        f.guard(e->{joined[0]++;h.assertTrue(f.start(w,t).equals("busy"),"callback reentry cannot reserveagain");e.setCanceled(true);});
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->h.assertTrue(f.start(w,t).equals("approaching"),"start"))
            .thenWaitUntil(()->h.assertTrue(joined[0]==1,"real Forgejoin reached"))
            .thenExecuteAfter(3,()->{h.assertTrue(f.converted().isEmpty()&&t.isAlive()&&!t.isRemoved()&&NeutralContracts.used(w)==0,"rollback leavesnativebody andnofreeslotclaim");f.noSpend();f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=300)
    public static void PublicationTameAbaAndCommandChangeCannotCommit(GameTestHelper h){
        var f=new F(h,"join_mutate");var w=f.worker(3,-10,false);var t=(BlackBeetleEntity)f.nativeBody(false,5,-10);int[] joined={0};
        f.guard(e->{joined[0]++;t.setOwnerUUID(UUID.randomUUID());t.setOwnerUUID(null);Unit.fullResetBehaviours(w);});
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->h.assertTrue(f.start(w,t).equals("approaching"),"start"))
            .thenWaitUntil(()->h.assertTrue(joined[0]==1,"real publication mutation"))
            .thenExecuteAfter(3,()->{h.assertTrue(f.converted().isEmpty()&&t.isAlive()&&ContractLedger.get(f.level.getServer()).count(f.owner,f.account.accountEpoch)==0,"nativeownerABA cannot commit");f.noSpend();f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=300)
    public static void AccountReplacementAndDisconnectReleaseReservationsWithoutPayment(GameTestHelper h){
        var f=new F(h,"account");var w=f.worker(3,-10,false);var t=f.nativeBody(false,5,-10);
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->{h.assertTrue(f.start(w,t).equals("approaching"),"start");ResourcesServerEvents.resourcesList.remove(f.account);})
            .thenExecuteAfter(3,()->{h.assertTrue(!NeutralContracts.active(w)&&t.isAlive(),"detachedaccount invalidates");ResourcesServerEvents.resourcesList.add(f.account);h.assertTrue(f.start(w,t).equals("approaching"),"freshconnectionorder");f.peer.close();})
            .thenExecuteAfter(180,()->{h.assertTrue(!NeutralContracts.active(w)&&f.converted().isEmpty()&&t.isAlive(),"disconnect no late conversion");f.noSpend();f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=80)
    public static void LedgerOpaqueFutureAndPendingSavedBodyFailClosed(GameTestHelper h){
        var f=new F(h,"opaque");CompoundTag raw=new CompoundTag();raw.putInt("schema",99);raw.putString("future","unchanged");var ledger=ContractLedger.load(raw);
        h.assertTrue(!ledger.supported()&&ledger.save(new CompoundTag()).equals(raw)&&!ledger.canCommit(UUID.randomUUID()),"futuredata byte-semantic preservation");
        var pending=DreamseekersMod.BEETLE.get().create(f.level);pending.setOwnerName(f.owner);pending.getPersistentData().putBoolean(NeutralContracts.PENDING,true);
        h.assertTrue(!f.level.addFreshEntity(pending),"uncommittedsavedcandidatecannotenterworld");f.close();h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=300)
    public static void PublicationUuidMutationCannotChargeOrOverwriteExistingSlot(GameTestHelper h){
        var f=new F(h,"uuid");var w=f.worker(3,-10,false);var t=f.nativeBody(false,5,-10);UUID occupied=UUID.randomUUID();
        var ledger=ContractLedger.get(f.level.getServer());ledger.commit(UUID.randomUUID(),occupied,f.owner,f.account.accountEpoch);int[] seen={0};
        f.guard(e->{seen[0]++;e.getEntity().setUUID(occupied);});
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->h.assertTrue(f.start(w,t).equals("approaching"),"start"))
          .thenWaitUntil(()->h.assertTrue(seen[0]==1,"actual publication UUID mutation"))
          .thenExecuteAfter(3,()->{f.noSpend();h.assertTrue(t.isAlive()&&f.converted().isEmpty()&&ledger.count(f.owner,f.account.accountEpoch)==1&&ledger.entry(occupied)!=null,"existing unloaded-body slot untouched and payment rolled back");ledger.died(occupied);f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=300)
    public static void OpaqueObservedCourtOwnerAbaCannotReviveChannel(GameTestHelper h){
        var f=new F(h,"court_opaque");var w=f.worker(3,-10,false);var t=f.nativeBody(false,5,-10);
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->{
            h.assertTrue(f.start(w,t).equals("approaching"),"start");var storage=f.court.getDataStorage();
            var key=com.dreamseekers.function.DreamFunctionalPlacement.STATE;var supported=storage.getData(key).write();var future=new CompoundTag();future.putInt("schemaVersion",99);future.putString("future","retain");
            storage.setData(key,com.dreamseekers.function.DreamFunctionState.read(future));f.court.ownerName="Neutral_other";f.court.getDataStorage();f.court.ownerName=f.owner;f.court.getDataStorage();
            h.assertTrue(storage.getData(key).write().equals(future),"observer preserves unknown raw");storage.setData(key,com.dreamseekers.function.DreamFunctionState.read(supported));
          }).thenExecuteAfter(180,()->{h.assertTrue(!NeutralContracts.active(w)&&t.isAlive()&&f.converted().isEmpty(),"raw restoration cannot erase observed live owner generation");f.noSpend();f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=350)
    public static void LateCanceledDeathDoesNotReleaseCapturedOriginalPayerSlot(GameTestHelper h){
        var f=new F(h,"death_cancel");var w=f.worker(3,-10,false);var t=f.nativeBody(false,5,-10);
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingDeathEvent> cancel=e->{if(e.getEntity().getPersistentData().getCompound(NeutralContracts.ORIGIN).getString("payer").equals(f.owner))e.setCanceled(true);};
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,cancel);
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->h.assertTrue(f.start(w,t).equals("approaching"),"start"))
          .thenWaitUntil(()->h.assertTrue(f.converted().size()==1,"paid body"))
          .thenExecute(()->{var body=f.converted().get(0);f.bodies.add(body);((Unit)body).setOwnerName("Neutral_captor");body.hurt(f.level.damageSources().genericKill(),100000);})
          .thenExecuteAfter(3,()->{h.assertTrue(ContractLedger.get(f.level.getServer()).count(f.owner,f.account.accountEpoch)==1,"final canceled death retains original payer slot despite owner capture");MinecraftForge.EVENT_BUS.unregister(cancel);for(var b:f.bodies)if(b.getPersistentData().contains(NeutralContracts.ORIGIN))ContractLedger.get(f.level.getServer()).died(b.getUUID());f.close();h.succeed();});
    }
    private record Replies(List<ContractPackets.Snapshot> views,List<EconomyActionResultPacket> acknowledgements) {}
    private static Replies exchange(GameTestHelper h,PassiveIncomePeer peer,ContractPackets.Request packet){try{
        var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);
        var context=ctor.newInstance(peer.connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);var b=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{packet.encode(b);var decoded=new ContractPackets.Request(b);h.assertTrue(decoded.equals(packet)&&b.readableBytes()==0,"exact real request codec");decoded.handle(()->context);}finally{b.release();}
        var views=new ArrayList<ContractPackets.Snapshot>();var acks=new ArrayList<EconomyActionResultPacket>();
        var zero=ContractPackets.ZERO;
        int resultId=PassiveIncomePeer.discriminator(new EconomyActionResultPacket(zero,0,zero,zero,"",0,""));
        var core=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.solegendary.reignofnether.ReignOfNether.MOD_ID,"main");
        for(var out:peer.drain())if(out instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket c
                &&(c.getIdentifier().equals(core)||c.getIdentifier().equals(DreamseekersMod.id("neutral_contract")))){
            var bytes=new net.minecraft.network.FriendlyByteBuf(c.getData().copy());
            try{
                int id=bytes.readVarInt();
                if(c.getIdentifier().equals(core)){if(id!=resultId)continue;acks.add(new EconomyActionResultPacket(bytes));}
                else{h.assertTrue(id==1,"private contract reply discriminator");views.add(new ContractPackets.Snapshot(bytes));}
                h.assertTrue(bytes.readableBytes()==0,"exact acknowledgement or view, without trailing details");
            }finally{bytes.release();}
        }
        return new Replies(views,acks);
    }catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static ContractPackets.Snapshot wire(GameTestHelper h,PassiveIncomePeer peer,ContractPackets.Request packet){
        var views=exchange(h,peer,packet).views();return views.isEmpty()?null:views.get(views.size()-1);
    }
    private static void expectClosed(GameTestHelper h,PassiveIncomePeer peer,ContractPackets.Request request,
                                     ContractPackets.Snapshot initial){
        var reply=exchange(h,peer,request);
        h.assertTrue(reply.views().isEmpty()&&reply.acknowledgements().size()==1,"one completion only, without a worker view");
        var ack=reply.acknowledgements().get(0);
        h.assertTrue(ack.stream().equals(initial.stream())&&ack.order()>initial.order()&&ack.owner().equals(initial.owner())
                &&ack.session().equals(request.session())&&ack.epoch().equals(request.epoch())&&ack.request()==request.sequence()
                &&ack.status().equals("neutral_contract_closed"),"authenticated neutral completion retires only the original request");
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
    public static void retiredWorkerCompletesStartCancelAndRetriesWithoutAWorkerSnapshot(GameTestHelper h){
        var f=new F(h,"closing");var w=f.worker(3,-10,false);var t=f.nativeBody(false,5,-10);
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->{
            var dim=f.level.dimension().location();var view=UUID.randomUUID();
            var q=new ContractPackets.Payload(ContractPackets.Action.QUERY,dim,w.getId(),w.getUUID(),w.rts().ownershipRevision,view,-1,ContractPackets.ZERO);
            var query=new ContractPackets.Request(q,ContractPackets.ZERO,ContractPackets.ZERO,0);
            var initial=wire(h,f.peer,query);h.assertTrue(initial!=null&&initial.available(),"authorized live view established the action session");
            var start=new ContractPackets.Payload(ContractPackets.Action.START,dim,q.id(),q.uuid(),q.ownerRevision(),view,t.getId(),t.getUUID());
            var cancel=new ContractPackets.Payload(ContractPackets.Action.CANCEL,dim,q.id(),q.uuid(),q.ownerRevision(),view,-1,ContractPackets.ZERO);
            var accepted=new ContractPackets.Request(start,initial.epoch(),initial.session(),1);
            h.assertTrue(wire(h,f.peer,accepted).status().equals("approaching"),"live start still emits worker state");
            h.assertTrue(wire(h,f.peer,new ContractPackets.Request(cancel,initial.epoch(),initial.session(),2)).status().equals("canceled"),"live cancel still uses normal outcome");
            w.setOwnerName("Neutral_new_owner");f.peer.drain();
            expectClosed(h,f.peer,accepted,initial); // a delayed retry of a previously admitted action
            var lostStart=new ContractPackets.Request(start,initial.epoch(),initial.session(),3);
            var lostCancel=new ContractPackets.Request(cancel,initial.epoch(),initial.session(),4);
            expectClosed(h,f.peer,lostStart,initial);expectClosed(h,f.peer,lostCancel,initial);
            var silent=exchange(h,f.peer,query);
            h.assertTrue(silent.views().isEmpty()&&silent.acknowledgements().isEmpty(),"lost worker query is silent, not an action");
            w.setOwnerName(f.owner);f.peer.drain();
            expectClosed(h,f.peer,lostStart,initial);expectClosed(h,f.peer,lostCancel,initial); // cached terminal results even after recapture
            h.assertTrue(!NeutralContracts.active(w),"retry did not open a new contract");f.noSpend();
            w.discard();f.peer.drain();
            expectClosed(h,f.peer,new ContractPackets.Request(cancel,initial.epoch(),initial.session(),5),initial);
            f.peer.close();
            try(var reconnected=new PassiveIncomePeer(f.level,f.owner)){
                var fresh=EconomyActionGate.query(reconnected.actor,reconnected.connection);
                h.assertTrue(fresh!=null&&!fresh.session().equals(initial.session()),"reconnect has a fresh action context");
                var old=exchange(h,reconnected,lostStart);
                h.assertTrue(old.views().isEmpty()&&old.acknowledgements().isEmpty(),"old action never retires a reused number in the new session");
            }
            f.close();h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
    public static void RealPacketReplayChangedPayloadEpochAndForeignOwnerFailClosed(GameTestHelper h){
        var f=new F(h,"wire");var w=f.worker(3,-10,false);var t=f.nativeBody(false,5,-10);
        h.startSequence().thenWaitUntil(()->f.ready(w,t)).thenExecute(()->{
            var payload=new ContractPackets.Payload(ContractPackets.Action.QUERY,f.level.dimension().location(),w.getId(),w.getUUID(),w.rts().ownershipRevision,UUID.randomUUID(),t.getId(),t.getUUID());
            var token=wire(h,f.peer,new ContractPackets.Request(payload,ContractPackets.ZERO,ContractPackets.ZERO,0));h.assertTrue(token!=null&&token.available()&&!token.active()&&token.capacity()==3,"real button private state");
            var start=new ContractPackets.Payload(ContractPackets.Action.START,payload.dimension(),payload.id(),payload.uuid(),payload.ownerRevision(),payload.view(),payload.targetId(),payload.targetUuid());var request=new ContractPackets.Request(start,token.epoch(),token.session(),1);
            h.assertTrue(wire(h,f.peer,request).status().equals("approaching"),"authenticated start");wire(h,f.peer,request);h.assertTrue(NeutralContracts.used(w)==1,"duplicate request only one reservation");
            var cancel=new ContractPackets.Payload(ContractPackets.Action.CANCEL,payload.dimension(),payload.id(),payload.uuid(),payload.ownerRevision(),payload.view(),payload.targetId(),payload.targetUuid());
            wire(h,f.peer,new ContractPackets.Request(cancel,token.epoch(),token.session(),1));h.assertTrue(NeutralContracts.active(w),"same sequence changed payload cannot cancel");
            wire(h,f.peer,new ContractPackets.Request(cancel,UUID.randomUUID(),token.session(),2));h.assertTrue(NeutralContracts.active(w),"old epoch cannot mutate");
            wire(h,f.peer,new ContractPackets.Request(cancel,token.epoch(),UUID.randomUUID(),3));h.assertTrue(NeutralContracts.active(w),"old session cannot mutate");
            try(var foreign=new PassiveIncomePeer(f.level,"Neutral_foreign")){h.assertTrue(wire(h,foreign,request)==null&&NeutralContracts.active(w),"other player cannot query or cancel worker");}
            f.noSpend();f.close();h.succeed();
        });
    }

    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1100)
    public static void CapacityResearchRealPaidCancelRefundAndNaturalCompletion(GameTestHelper h){
        var f=new F(h,"research");var tree=f.install(DreamseekersMod.DREAM_ANCHOR,40,0);var lab=(com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement)f.install(DreamseekersMod.RESONANCE_LABORATORY,72,0);
        var item=ContractResearch.CAPACITY;h.assertTrue(item!=null&&item.defaultCost.ticks==900,"registered forty-five second research");
        h.startSequence().thenWaitUntil(()->h.assertTrue(tree.isBuilt&&lab.isBuilt&&f.court.isBuilt,"actual templates built"))
          .thenExecute(()->{
            h.assertTrue(ReceiptResearchService.start(f.peer.actor,lab,item,ResearchPlacementIdentity.serverIdentity(lab),lab.getCaptureRevision(),f.account.accountEpoch,f.account.revision).equals("started"),"actual paid research queue");
            h.assertTrue(f.account.food==900&&f.account.wood==900&&f.account.ore==925&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==480,"exact registered payment");var receipt=lab.productionQueue.get(0).receiptResearch.receiptId();
            h.assertTrue(ReceiptResearchService.cancel(f.peer.actor,lab,receipt,ResearchPlacementIdentity.serverIdentity(lab),lab.getCaptureRevision(),f.account.accountEpoch,f.account.revision).equals("cancelled_refunded"),"receipt actual refund");f.noSpend();h.assertTrue(f.account.wood==1000,"wood refund");
            h.assertTrue(ReceiptResearchService.start(f.peer.actor,lab,item,ResearchPlacementIdentity.serverIdentity(lab),lab.getCaptureRevision(),f.account.accountEpoch,f.account.revision).equals("started"),"restart real queue");
          }).thenExecuteAfter(899,()->h.assertTrue(ContractResearch.capacity(f.owner,false)==3,"cannot finish early"))
          .thenWaitUntil(()->h.assertTrue(ContractResearch.capacity(f.owner,false)==5&&lab.productionQueue.isEmpty(),"natural server queue completion expands slots"))
          .thenExecute(()->{h.assertTrue(f.account.food==900&&f.account.wood==900&&f.account.ore==925&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==480&&f.account.paidReceipts.isEmpty(),"one durable final research payment");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=350)
    public static void PhysicalPopulationExactCapReservationAndJoinMutation(GameTestHelper h){
        var f=new F(h,"popcap");ResearchServerEvents.removeCheat(f.owner,"foodforthought");var tree=f.install(DreamseekersMod.DREAM_ANCHOR,40,0);
        var w=f.worker(3,-10,false);var t=f.nativeBody(false,5,-10);var w2=f.worker(3,-15,false);var t2=f.nativeBody(false,5,-15);
        int[] supply={0};List<FireflyWorker> fillers=new ArrayList<>();boolean[] mutate={false};int[] publications={0};
        f.guard(e->{publications[0]++;if(mutate[0])fillers.add(f.worker(-15,-15,false));});
        h.startSequence().thenWaitUntil(()->{f.ready(w,t,w2,t2);h.assertTrue(tree.isBuilt,"real population building built");})
          .thenExecute(()->{supply[0]=BuildingServerEvents.getTotalPopulationSupply(f.owner);h.assertTrue(supply[0]>=4&&supply[0]<UnitServerEvents.maxPopulation,"physical finite cap without cheat");
            while(UnitServerEvents.getCurrentPopulation(f.owner)<supply[0]-2)fillers.add(f.worker(-15,-15,false));
            h.assertTrue(f.start(w,t).equals("approaching"),"exact remaining two population accepted");h.assertTrue(f.start(w2,t2).equals("insufficient")&&!NeutralContracts.active(w2),"other reservation already consumes remaining cap");mutate[0]=true;
          }).thenWaitUntil(()->h.assertTrue(publications[0]==1,"real join injects one population"))
          .thenExecuteAfter(3,()->{f.noSpend();h.assertTrue(f.converted().isEmpty()&&t.isAlive(),"postpublication overcap by one rejects without cost");var extra=fillers.remove(fillers.size()-1);extra.discard();mutate[0]=false;})
          .thenWaitUntil(()->h.assertTrue(UnitServerEvents.getCurrentPopulation(f.owner)==supply[0]-2,"discarded filler reconciles actual population"))
          .thenExecute(()->h.assertTrue(f.start(w,t).equals("approaching"),"retry exact cap"))
          .thenWaitUntil(()->h.assertTrue(f.converted().size()==1,"exact cap actual conversion"))
          .thenExecute(()->{h.assertTrue(UnitServerEvents.getCurrentPopulation(f.owner)==supply[0]&&f.account.food==950&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==485,"candidate counted exactly once");f.close();h.succeed();});
    }

    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=500)
    public static void TwoRealPayersContendThenCanceledFirstClaimCannotRevive(GameTestHelper h){
        var f=new F(h,"contest_a");String other="Neutral_contest_b";
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(other,DreamseekersMod.DREAMSEEKERS));var accountB=new Resources(other,1000,1000,1000);accountB.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(accountB);ResearchServerEvents.addCheat(other,"foodforthought");var peerB=new PassiveIncomePeer(f.level,other);
        var courtB=f.install(DreamseekersMod.CONTRACT_ECOLOGY,16,8);courtB.ownerName=other;courtB.getDataStorage();
        var a=f.worker(3,-10,false);var b=f.worker(5,-12,false);b.setOwnerName(other);var target=f.nativeBody(false,5,-10);int[] popB={0};UUID[] bodyId={null};
        f.cleanupActions.add(()->{NeutralContracts.cancel(peerB.actor,b,b.rts().ownershipRevision);for(var entity:List.copyOf(UnitServerEvents.getAllUnits()))if(entity instanceof Unit u&&u.getOwnerName().equals(other)){ContractLedger.get(f.level.getServer()).died(entity.getUUID());entity.discard();}peerB.close();ResearchServerEvents.removeAllResearchFor(other);ResearchServerEvents.removeCheat(other,"foodforthought");ResourcesServerEvents.resourcesList.remove(accountB);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(other));});
        h.startSequence().thenWaitUntil(()->{f.ready(a,b,target);h.assertTrue(courtB.isBuilt&&f.court.isBuilt,"two distinct real owned courts completed");})
          .thenExecute(()->{
            h.assertTrue(NeutralContracts.available(f.peer.actor,a)&&NeutralContracts.available(peerB.actor,b),"both legitimate live owners independently eligible");popB[0]=UnitServerEvents.getCurrentPopulation(other);
            h.assertTrue(f.start(a,target).equals("approaching"),"A reserves exact wild native");
            h.assertTrue(NeutralContracts.begin(peerB.actor,peerB.connection,b,b.rts().ownershipRevision,target,UUID.randomUUID()).equals("busy"),"B cannot claim same native while A reserves");
            f.noSpend();h.assertTrue(accountB.food==1000&&accountB.extra.get(DreamPassiveEconomy.SHADOW)==500&&NeutralContracts.used(a)==1&&NeutralContracts.used(b)==0,"contested attempt charges neither account and adds no B reservation");
            h.assertTrue(NeutralContracts.cancel(f.peer.actor,a,a.rts().ownershipRevision).equals("canceled"),"A real cancellation releases claim");
            h.assertTrue(NeutralContracts.begin(peerB.actor,peerB.connection,b,b.rts().ownershipRevision,target,UUID.randomUUID()).equals("approaching"),"B now reserves legally");
            h.assertTrue(f.start(a,target).equals("busy"),"A cannot restore old claim over B");
          }).thenWaitUntil(()->h.assertTrue(target.isRemoved()&&!NeutralContracts.active(b),"B completes actual full channel"))
          .thenExecute(()->{var body=UnitServerEvents.getAllUnits().stream().filter(e->e instanceof BeetleUnit u&&u.getOwnerName().equals(other)&&e.getPersistentData().contains(NeutralContracts.ORIGIN)).findFirst().orElseThrow();bodyId[0]=body.getUUID();
            h.assertTrue(RtsKillValueClaims.isFree(body)&&UnitServerEvents.getCurrentPopulation(other)==popB[0]+2,"actual B population and permanent free provenance");
            h.assertTrue(accountB.food==950&&accountB.wood==1000&&accountB.ore==1000&&accountB.extra.get(DreamPassiveEconomy.SHADOW)==485,"only B exact fifty food fifteen shadow");f.noSpend();
            var ledger=ContractLedger.get(f.level.getServer());h.assertTrue(ledger.count(f.owner,f.account.accountEpoch)==0&&ledger.count(other,accountB.accountEpoch)==1&&ledger.entry(bodyId[0]).account().equals(accountB.accountEpoch),"permanent slot belongs solely to B payer epoch");
          }).thenExecuteAfter(180,()->{h.assertTrue(!NeutralContracts.active(a)&&f.converted().isEmpty()&&f.level.getEntity(bodyId[0])!=null,"canceled A never resumes or replaces B body");f.noSpend();h.assertTrue(accountB.food==950&&accountB.extra.get(DreamPassiveEconomy.SHADOW)==485,"no replay charge/refund");f.close();h.succeed();});
    }

}
