// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import static com.dreamseekers.ShadowGolemStrikeSkillGameTests.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BooleanSupplier;

/** One isolated permission test, not natural vision-raster generation coverage.
 * Explicit native edge-cache inputs use the production column gate and authenticated packet services.
 * The ordinary raster timer is suspended while those inputs are controlled, then fully restored.
 * No visibility provider override, no live-chunk shortcut and no particle-audience claim.
 */
@GameTestHolder("dreamseekers_shadow_golem_strike_fog")
@PrefixGameTestTemplate(false)
public final class GolemStrikeFogGameTests {
    private static final String BATCH="dreamseekers_shadow_golem_strike_fog";
    private static final List<Placement> BUILDINGS=new ArrayList<>();
    private static ControlledFog fog;
    private static NetherProbe nether;
    private static GeometryWatch geometry;
    private static final Object LEGACY_TICK_OBSERVER=new Object(){
        @net.minecraftforge.eventbus.api.SubscribeEvent public void shadowGolemFogDiagnosticAfterLegacyNaturalTick(net.minecraftforge.event.TickEvent.LevelTickEvent event){
            for(var b:BUILDINGS)if(event.level==b.getLevel()&&b.legacyPendingTime>=0&&event.level.getGameTime()>b.legacyPendingTime){
                System.out.println("GOLEM_FOG_LEGACY_LATER_TICK time="+event.level.getGameTime()+" legacyTime="+b.legacyPendingTime+" origin="+b.originPos+" lastAttacker="+b.lastAttacker+" partial="+b.partialBlocksDestroyed+" blocks="+b.getBlocksPlaced());b.legacyPendingTime=-1;
            }
        }
        @net.minecraftforge.eventbus.api.SubscribeEvent public void shadowGolemFogObserveActualBuildingGeometry(net.minecraftforge.event.TickEvent.LevelTickEvent event){
            if(geometry!=null&&event.level==geometry.level&&event.phase==net.minecraftforge.event.TickEvent.Phase.END)geometry.capture("NATIVE_END");
        }
    };
    @AfterBatch(batch=BATCH) public static void cleanupFog(ServerLevel level) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(LEGACY_TICK_OBSERVER);geometry=null;
        try { try { if(nether!=null)nether.close(); } finally { nether=null;if(fog!=null)fog.close(); } }
        finally { fog=null;BuildingServerEvents.getBuildings().removeAll(BUILDINGS);BUILDINGS.clear();ShadowGolemStrikeSkillGameTests.cleanup(level); }
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=150)
    public static void actualCasterColumnPermissionsRejectHiddenAndRevokeUnitAndBuildingWindups(GameTestHelper h) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(LEGACY_TICK_OBSERVER);
        room(h);
        var unitPositive=golem(h,"FogOwner",5.5,7.5);var unitLost=golem(h,"FogOwner",5.5,23.5);
        var buildingPositive=golem(h,"FogOwner",21.75,7.5);var buildingLost=golem(h,"FogOwner",21.75,23.5);
        var seen=target(h,"FogEnemy",8,7.5);var lost=target(h,"FogEnemy",8,23.5);
        var buildingSeen=placement(h,24,1,7);var buildingHidden=placement(h,24,1,23);
        var owner=player(h,"FogOwner");
        List<ShadowGolemUnit> casters=List.of(unitPositive,unitLost,buildingPositive,buildingLost);
        long[] reserved={-1};double[] initial={0,0},budget={0};BlockPos[] columns=new BlockPos[4];
        ready(h,unitPositive,unitLost,buildingPositive,buildingLost,seen,lost)
        .thenExecute(()->{nether=new NetherProbe(h,owner);nether.prepare();})
        .thenWaitUntil(()->nether.awaitPublication())
        .thenExecute(()->{
            fog=new ControlledFog(h,owner);fog.install();
            try {nether.verifyPermission();} finally {nether.close();nether=null;}
        }).thenExecute(()->{
            h.assertTrue(h.getLevel().dimension()==net.minecraft.world.level.Level.OVERWORLD,"native cache here is explicitly overworld scoped");
            columns[0]=seen.blockPosition();columns[1]=lost.blockPosition();columns[2]=buildingSeen.originPos;columns[3]=buildingHidden.originPos;
            h.assertTrue(buildingPositive.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(buildingSeen.originPos))<=9&&buildingLost.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(buildingHidden.originPos))<=9,"real contact centers inside unchanged range3 before any fog denial");
            h.assertTrue(FogOfWarServerEvents.isFogActiveFor(owner),"actual RTS caster's authenticated viewer is subject to real fog");
            for(int i=0;i<2;i++) {
                var caster=i==0?buildingPositive:buildingLost;var placement=i==0?buildingSeen:buildingHidden;
                var hit=h.getLevel().clip(new net.minecraft.world.level.ClipContext(caster.getEyePosition(),net.minecraft.world.phys.Vec3.atCenterOf(placement.originPos),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,caster));
                h.assertTrue(h.getLevel().noCollision(caster)&&hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&hit.getBlockPos().equals(placement.originPos),"real noncolliding caster AABB and unobstructed contact ray before hidden request");
                h.assertTrue(placement.isAttackable()&&caster.canHarmOwner(placement.ownerName)&&placement.isPosPartOfBuilding(placement.originPos,true)&&h.getLevel().getWorldBorder().isWithinBounds(placement.originPos),"actual nonfog building legality before permission-only negative");
            }
            geometry=new GeometryWatch(h.getLevel(),List.of(buildingPositive,buildingLost),List.of(buildingSeen,buildingHidden));
            geometry.checkNow(h,"BEFORE_ADMISSION");
            // Neighboring edge columns do not confer whole-chunk or Y-based visibility.
            fog.columns(Arrays.stream(columns).map(BlockPos::east).toList());
            for(BlockPos p:columns)h.assertTrue(!FogOfWarServerEvents.isBlockVisibleFor(owner,p.getX(),p.getZ()),"actual requested column stays hidden despite adjacent visible column");
            no(h,encodedUnit(h,unitPositive,seen),"unseen unit encoded request rejected");
            no(h,encodedUnit(h,unitLost,lost),"second unseen unit encoded request rejected");
            no(h,encodedBuilding(h,buildingPositive,buildingSeen),"unseen building encoded request rejected");
            no(h,encodedBuilding(h,buildingLost,buildingHidden),"second unseen building encoded request rejected");
            for(var s:casters)h.assertTrue(cd(s)==0&&!ShadowGolemStrike.active(s),"hidden inputs spend no cooldown and create no windup");
            h.assertTrue(buildingSeen.receipt==null&&buildingHidden.receipt==null,"hidden building inputs perform no budget call");
            fog.columns(List.of(columns));
            seen.setInvisible(true);no(h,encodedUnit(h,unitPositive,seen),"invisible entity remains ineligible even when its actual column is visible");
            h.assertTrue(cd(unitPositive)==0,"invisibility rejection is unpaid");seen.setInvisible(false);
            for(BlockPos p:columns)h.assertTrue(FogOfWarServerEvents.isBlockVisibleFor(owner,p.getX(),p.getZ()),"native edge bit authorizes exact current target column");
            initial[0]=buildingSeen.partialBlocksDestroyed;initial[1]=buildingHidden.partialBlocksDestroyed;
            budget[0]=buildingPositive.getUnitAttackDamage()*buildingSeen.getMeleeDamageMult()*Math.max(2,buildingPositive.getBuildingDamageMultiplier());
            h.assertTrue(budget[0]>0&&budget[0]<buildingSeen.getHealthPerBlock()/2,"visible building positive is a real fractional budget with no forced contact destruction");
            reserved[0]=h.getLevel().getGameTime();geometry.start=reserved[0];geometry.capture("RESERVATION_TICK");
            ok(h,encodedUnit(h,unitPositive,seen),"visible entity positive admitted through actual codec/service");
            ok(h,encodedUnit(h,unitLost,lost),"visible entity loss-control admitted");
            ok(h,encodedBuilding(h,buildingPositive,buildingSeen),"visible registered building positive admitted through actual codec/service");
            ok(h,encodedBuilding(h,buildingLost,buildingHidden),"visible building loss-control admitted");
            for(var s:casters)h.assertTrue(cd(s)==480&&ShadowGolemStrike.remainingTicks(s)==20,"all four real windups reserve original20/480");
            System.out.println("SHADOW_GOLEM_STRIKE_FOG_CONTROLLED_CACHE_ADMISSION at="+reserved[0]+" columns="+List.of(columns)+" mode=actual_native_edge_masks_not_natural_raster");
        }).thenIdle(6).thenExecute(()->{
            h.assertTrue(h.getLevel().getGameTime()==reserved[0]+6,"real elapsed six ticks before loss");
            geometry.checkNow(h,"BEFORE_VISIBILITY_LOSS");
            fog.columns(List.of(columns[0],columns[2]));
            h.assertTrue(!FogOfWarServerEvents.isBlockVisibleFor(owner,columns[1].getX(),columns[1].getZ())&&!FogOfWarServerEvents.isBlockVisibleFor(owner,columns[3].getX(),columns[3].getZ()),"both loss lanes now hidden in actual authoritative cache");
            h.assertTrue(!ShadowGolemStrike.active(unitLost)&&!ShadowGolemStrike.active(buildingLost)&&cd(unitLost)==474&&cd(buildingLost)==474,"visibility loss revokes both primary kinds without refund");
            h.assertTrue(ShadowGolemStrike.active(unitPositive)&&ShadowGolemStrike.active(buildingPositive),"other exact visible columns remain independently authorized");
            geometry.checkNow(h,"AFTER_VISIBILITY_LOSS");
        }).thenIdle(14).thenExecute(()->{
            h.assertTrue(h.getLevel().getGameTime()==reserved[0]+20,"real original release tick20");
            geometry.checkNow(h,"FINAL_RELEASE_TICK");geometry.requireComplete(h);
            close(h,seen.getHealth(),985,"visible unit really receives normal150percent releaseAD10");close(h,lost.getHealth(),1000,"hidden-before-release unit receives no damage");
            h.assertTrue(buildingSeen.receipt!=null&&buildingSeen.receipt.accepted()&&!buildingSeen.receipt.interrupted()&&buildingSeen.receipt.blocksDestroyed()==0,"visible building really traverses guarded budget API");
            close(h,buildingSeen.receipt.acceptedBudget(),budget[0],"real building receipt uses releaseAD/native coefficient/max multiplier");
            close(h,buildingSeen.partialBlocksDestroyed,initial[0]+budget[0]/(buildingSeen.getHealthPerBlock()/2),"visible fractional progress actually committed");
            h.assertTrue(buildingHidden.receipt==null&&buildingHidden.getBlocksPlaced()==4,"hidden-before-release building has no budget call or block write");
            close(h,buildingHidden.partialBlocksDestroyed,initial[1],"lost visibility leaves original building progress unchanged");
            for(var s:casters)h.assertTrue(cd(s)==460&&!ShadowGolemStrike.active(s),"original paid cooldown remains exact for success and cancellation");
            bank(h);geometry=null;fog.close();fog=null;
            h.assertTrue(!FogOfWarServerEvents.isEnabled(),"isolated profile restores initial fog-off state before completion");
            System.out.println("DREAMSEEKERS_SHADOW_GOLEM_STRIKE_ACTUAL_COLUMN_FOG_UNIT_BUILDING_HIDDEN_INVISIBLE_VISIBLE_LOSS_PAID480_PASS");
        }).thenSucceed();
    }

    /** Read-only geometry observations; Forge callbacks store failures, GameTest sequence asserts them. */
    private static final class GeometryWatch {
        final ServerLevel level;final List<ShadowGolemUnit> casters;final List<Placement> placements;
        final Set<Long> observed=new TreeSet<>();final List<String> failures=new ArrayList<>();long start=-1;
        GeometryWatch(ServerLevel level,List<ShadowGolemUnit> casters,List<Placement> placements){this.level=level;this.casters=casters;this.placements=placements;}
        void capture(String stage) {
            long now=level.getGameTime();
            if(start>=0&&(now<start||now>start+20))return;
            try {
                for(int i=0;i<casters.size();i++) {
                    var caster=casters.get(i);var placement=placements.get(i);
                    double distance=caster.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(placement.originPos));
                    boolean ordinaryRange=placement.isPosInsideBuilding(caster.getOnPos(),2);
                    var ordinaryGoal=caster.getAttackBuildingGoal();
                    boolean actualMeleeGoal=ordinaryGoal instanceof com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal;
                    boolean ordinaryAttacking=actualMeleeGoal&&((com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal)ordinaryGoal).isAttacking();
                    boolean collisionFree=level.noCollision(caster);
                    var hit=level.clip(new net.minecraft.world.level.ClipContext(caster.getEyePosition(),net.minecraft.world.phys.Vec3.atCenterOf(placement.originPos),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,caster));
                    boolean contactRay=hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&hit.getBlockPos().equals(placement.originPos);
                    String row="stage="+stage+" time="+now+" lane="+i+" caster="+caster.getUUID()+" pos="+caster.position()+" onPos="+caster.getOnPos()+" contact="+placement.originPos+" distanceSquared="+distance+" ordinaryRange="+ordinaryRange+" actualMeleeGoal="+actualMeleeGoal+" ordinaryIsAttacking="+ordinaryAttacking+" collisionFree="+collisionFree+" contactRay="+contactRay;
                    System.out.println("GOLEM_FOG_ACTUAL_GEOMETRY "+row);
                    if(!Double.isFinite(distance)||distance>9||ordinaryRange||!actualMeleeGoal||ordinaryAttacking||!collisionFree||!contactRay)failures.add(row);
                }
                if(start>=0)observed.add(now);
            } catch(RuntimeException error) {failures.add("observation exception at="+now+" stage="+stage+" error="+error);}
        }
        void checkNow(GameTestHelper h,String stage){capture(stage);h.assertTrue(failures.isEmpty(),"actual skill-range/native-ray/noncollision and outside ordinary building range throughout: "+failures);}
        void requireComplete(GameTestHelper h) {
            h.assertTrue(start>=0&&level.getGameTime()==start+20,"geometry window equals original20 tick release");
            for(long tick=start;tick<=start+20;tick++)h.assertTrue(observed.contains(tick),"geometry observation missing actual gameTime="+tick+" observed="+observed);
            h.assertTrue(observed.size()==21&&failures.isEmpty(),"complete21 inclusive gameTime samples, no geometry failure: "+failures);
            System.out.println("GOLEM_FOG_GEOMETRY_FULL20_WINDOW_PASS first="+start+" last="+(start+20)+" observed="+observed);
        }
    }

    /** Native publication is awaited; only the two permission requests share one tick. */
    private static final class NetherProbe implements AutoCloseable {
        final GameTestHelper h;final ServerPlayer owner;final ServerLevel other;
        final net.minecraft.world.phys.Vec3 home;final float yaw,pitch;
        final ShadowGolemUnit source;final GoldenFoxUnit target;boolean closed;
        NetherProbe(GameTestHelper h,ServerPlayer owner) {
            this.h=h;this.owner=owner;other=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            h.assertTrue(other!=null,"actual Nether level exists");home=owner.position();yaw=owner.getYRot();pitch=owner.getXRot();
            source=DreamseekersMod.SHADOW_GOLEM_UNIT.get().create(other);target=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(other);
            h.assertTrue(source!=null&&target!=null,"registered actual dimension control bodies");
        }
        void prepare() {
            var origin=h.absolutePos(BlockPos.ZERO);
            source.setOwnerName(account("FogOwner"));target.setOwnerName(account("FogEnemy"));
            source.setNoAi(true);target.setNoAi(true);source.setPersistenceRequired();target.setPersistenceRequired();damage(source,10);neutral(target);
            source.moveTo(origin.getX()+13.5,200,origin.getZ()+15.5,0,0);target.moveTo(origin.getX()+16,200,origin.getZ()+15.5,0,0);
            prewarmNativeFullDependencyUnion();
            h.assertTrue(other.noCollision(source)&&other.noCollision(target)&&other.isEmptyBlock(source.blockPosition())&&other.isEmptyBlock(target.blockPosition()),"confirmed natural high air; no Nether terrain changes");
            h.assertTrue(other.addFreshEntity(source)&&other.addFreshEntity(target),"native Nether addFreshEntity accepted; visibility lookup is not yet assumed");
            owner.teleportTo(other,origin.getX()+13.5,200,origin.getZ()+19.5,0,0);
            trace("PREPARED");
        }
        /** Setup-only normal native generation/loading; never publishes or ticks an entity. */
        void prewarmNativeFullDependencyUnion() {
            var centers=List.of(new ChunkPos(source.blockPosition()),new ChunkPos(target.blockPosition()));
            var chunks=new TreeSet<ChunkPos>(Comparator.comparingInt((ChunkPos c)->c.x).thenComparingInt(c->c.z));
            for(var center:centers)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)chunks.add(new ChunkPos(center.x+dx,center.z+dz));
            h.assertTrue(chunks.size()>=25&&chunks.size()<=50,"exact two actual radius2 dependency rings, no wider pre-generation");
            h.assertTrue(!source.isAddedToWorld()&&!target.isAddedToWorld(),"FULL dependency prewarm occurs before actual body publication");
            long start=other.getGameTime(),overworldStart=h.getLevel().getGameTime(),wallStart=System.nanoTime();
            System.out.println("GOLEM_FOG_NATIVE_FULL_PREWARM_BEGIN time="+start+" overworldTime="+overworldStart+" centers="+centers+" radius=2 count="+chunks.size()+" exactChunks="+chunks);
            int completed=0;
            for(var cp:chunks) {
                boolean presentBefore=other.getChunkSource().getChunkNow(cp.x,cp.z)!=null;
                var actual=other.getChunkSource().getChunk(cp.x,cp.z,net.minecraft.world.level.chunk.ChunkStatus.FULL,true);
                h.assertTrue(actual instanceof net.minecraft.world.level.chunk.LevelChunk&&actual.getPos().equals(cp),"native synchronous FULL returns exact requested LevelChunk "+cp);
                completed++;
                System.out.println("GOLEM_FOG_NATIVE_FULL_PREWARM_CHUNK time="+other.getGameTime()+" requested="+cp+" actual="+actual.getPos()+" status="+actual.getStatus()+" presentBefore="+presentBefore+" completed="+completed+" total="+chunks.size());
            }
            for(var cp:chunks) {
                var actual=other.getChunkSource().getChunkNow(cp.x,cp.z);
                h.assertTrue(actual!=null&&actual.getPos().equals(cp),"every exact dependency still has native FULL chunk at end "+cp);
            }
            h.assertTrue(completed==chunks.size()&&other.getGameTime()==start&&h.getLevel().getGameTime()==overworldStart,"normal synchronous FULL setup does not advance or rewrite either gameTime");
            h.assertTrue(!source.isAddedToWorld()&&!target.isAddedToWorld()&&source.tickCount==0&&target.tickCount==0,"setup never publishes or manually ticks either body");
            System.out.println("GOLEM_FOG_NATIVE_FULL_PREWARM_END time="+other.getGameTime()+" overworldTime="+h.getLevel().getGameTime()+" count="+completed+" wallMillis="+((System.nanoTime()-wallStart)/1_000_000)+" exactChunks="+chunks);
        }
        void awaitPublication() {
            trace("WAIT_PUBLICATION");
            h.assertTrue(source.tickCount>3&&target.tickCount>3&&source.isAddedToWorld()&&target.isAddedToWorld()
                &&other.getEntity(source.getId())==source&&other.getEntity(source.getUUID())==source
                &&other.getEntity(target.getId())==target&&other.getEntity(target.getUUID())==target,
                "naturally ticked exact real dimension IDs and UUIDs before permission requests");
            trace("PUBLICATION_READY");
        }
        void verifyPermission() {
            awaitPublication();
            h.assertTrue(owner.serverLevel()==other&&other.players().contains(owner)&&other.getServer().getPlayerList().getPlayer(owner.getUUID())==owner,"same actual authenticated viewer legally transferred");
            h.assertTrue(source.isAlive()&&target.isAlive()&&other.noCollision(source)&&other.noCollision(target)&&source.hasLineOfSight(target)
                &&ShadowGolemStrike.distanceSquared(source.position(),target.getBoundingBox())<=9&&source.canHarm(target),"actual naturally moved bodies retain nonfog geometry and enemy legality");
            long at=other.getGameTime();fog.columns(List.of(target.blockPosition()));
            h.assertTrue(FogOfWarServerEvents.isFogActiveFor(owner)&&FogOfWarServerEvents.isBlockVisibleFor(owner,target.blockPosition().getX(),target.blockPosition().getZ()),"dimensionless real native cache is visible, so cannot alone authorize Nether");
            no(h,encodedUnit(h,source,target),"actual non-overworld fog request fails closed despite visible raw column");
            h.assertTrue(cd(source)==0&&!ShadowGolemStrike.active(source),"non-overworld denial is unpaid");
            try {
                FogOfWarServerEvents.setEnabled(false);
                ok(h,encodedUnit(h,source,target),"identical actual sender bodies and geometry admit with fog disabled, isolates dimension visibility policy");
                h.assertTrue(other.getGameTime()==at&&cd(source)==480&&ShadowGolemStrike.remainingTicks(source)==20,"same tick permission pair and genuine original20/480; no deadline edits");
                System.out.println("SHADOW_GOLEM_STRIKE_FOG_REAL_NETHER_RAW_VISIBLE_DENIED_FOG_OFF_ADMITTED at="+at);
            } finally {FogOfWarServerEvents.setEnabled(true);fog.columns(List.of());}
        }
        void trace(String stage) {
            for(var body:List.of(source,target)) {
                var cp=new ChunkPos(body.blockPosition());var byId=other.getEntity(body.getId());var byUuid=other.getEntity(body.getUUID());
                System.out.println("SHADOW_GOLEM_STRIKE_FOG_NATIVE_PUBLICATION stage="+stage+" gameTime="+other.getGameTime()+" id="+body.getId()+" uuid="+body.getUUID()+" ticks="+body.tickCount+" added="+body.isAddedToWorld()+" removed="+body.isRemoved()+" byId="+byId+" sameId="+(byId==body)+" byUuid="+byUuid+" sameUuid="+(byUuid==body)+" chunk="+cp+" chunkNow="+(other.getChunkSource().getChunkNow(cp.x,cp.z)!=null)+" entitiesLoaded="+other.areEntitiesLoaded(cp.toLong())+" entityTicking="+other.isPositionEntityTicking(body.blockPosition())+" pos="+body.position()+" motion="+body.getDeltaMovement());
            }
        }
        @Override public void close() {
            if(closed)return;closed=true;
            try {try {source.discard();} finally {target.discard();}}
            finally {owner.teleportTo(h.getLevel(),home.x,home.y,home.z,yaw,pitch);}
        }
    }

    private static SkillResult encodedUnit(GameTestHelper h,ShadowGolemUnit source,GoldenFoxUnit target) {
        var sent=new SkillNetwork.Cast(request(source,target));var wire=new FriendlyByteBuf(Unpooled.buffer());
        try {sent.encode(wire);var decoded=SkillNetwork.Cast.decode(wire);h.assertTrue(decoded.equals(sent)&&wire.readableBytes()==0,"actual entity packet roundtrip");return decoded.execute(player(h,source.getOwnerName()));}
        finally {wire.release();}
    }
    private static SkillResult encodedBuilding(GameTestHelper h,ShadowGolemUnit source,Placement building) {
        var sent=new SkillNetwork.BuildingCast(UnitSkill.SHADOW_GOLEM_STRIKE,source.getId(),source.getUUID(),source.rts().skills().revision(),building.originPos,building.originPos);
        var wire=new FriendlyByteBuf(Unpooled.buffer());try {sent.encode(wire);var decoded=SkillNetwork.BuildingCast.decode(wire);h.assertTrue(decoded.equals(sent)&&wire.readableBytes()==0,"actual building packet roundtrip with no owner/amount/dimension fields");return decoded.execute(player(h,source.getOwnerName()));}finally {wire.release();}
    }
    private static Placement placement(GameTestHelper h,int x,int y,int z) {
        var origin=h.absolutePos(new BlockPos(x,y,z));var blocks=new ArrayList<BuildingBlock>();
        for(int i=0;i<4;i++){var p=origin.east(i);h.getLevel().setBlock(p,Blocks.STONE_BRICKS.defaultBlockState(),2);blocks.add(new BuildingBlock(p,Blocks.STONE_BRICKS.defaultBlockState()));}
        var p=new Placement(h.getLevel(),origin,account("FogEnemy"),blocks);BUILDINGS.add(p);BuildingServerEvents.getBuildings().add(p);p.onBuilt();
        h.assertTrue(p.isBuilt&&p.getBlocksPlaced()==4&&p.getBlocksTotal()==4&&!p.shouldBeDestroyed(),"actual complete registered enemy placement");return p;
    }
    private static final class Placement extends BuildingPlacement {
        GuardedDamageReceipt receipt;long legacyPendingTime=-1;
@Override public void destroyRandomBlocks(double amount) {
    long now=getLevel().getGameTime();
    double before=partialBlocksDestroyed;int countBefore=getBlocksPlaced();
    var oldAttacker=lastAttacker;
    var stack=java.util.Arrays.toString(Thread.currentThread().getStackTrace());
    System.out.println("GOLEM_FOG_LEGACY_ENTER time="+now+" origin="+originPos
        +" amount="+amount+" partial="+before+" blocks="+countBefore
        +" previousLastAttacker="+oldAttacker+" stack="+stack);
    for(var candidate:com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits()) {
        if(candidate.level()!=getLevel()
            ||!(candidate instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit attacker)
            ||!(attacker.getAttackBuildingGoal() instanceof com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal goal)
            ||goal.getBuildingTarget()!=this)continue;
        System.out.println("GOLEM_FOG_LEGACY_CANDIDATE time="+now+" origin="+originPos
            +" id="+candidate.getId()+" uuid="+candidate.getUUID()+" pos="+candidate.position()
            +" onPos="+candidate.getOnPos()+" isAttacking="+goal.isAttacking()
            +" ordinaryAD="+attacker.getUnitAttackDamage()+" ordinaryCD="+attacker.getAttackCooldown()
            +" ordinaryMult="+attacker.getBuildingDamageMultiplier()
            +" strikeActive="+(candidate instanceof com.dreamseekers.unit.ShadowGolemUnit g
                &&com.dreamseekers.skill.ShadowGolemStrike.active(g)));
    }
    legacyPendingTime=now;
    try {super.destroyRandomBlocks(amount);}
    finally {System.out.println("GOLEM_FOG_LEGACY_EXIT time="+getLevel().getGameTime()
        +" origin="+originPos+" partialBefore="+before+" partialAfter="+partialBlocksDestroyed
        +" blocksBefore="+countBefore+" blocksAfter="+getBlocksPlaced()
        +" lastAttackerNotCurrentProof="+lastAttacker);}
}
        Placement(ServerLevel l,BlockPos origin,String owner,ArrayList<BuildingBlock> blocks){super(DreamseekersMod.BEETLE_SANCTUARY,l,origin,Rotation.NONE,owner,blocks,false);}
        @Override public GuardedDamageReceipt destroyRandomBlocksGuarded(double amount,BooleanSupplier valid,BooleanSupplier completion,Mob attacker){return receipt=super.destroyRandomBlocksGuarded(amount,valid,completion,attacker);}
    }

    /** Controlled raw native cache inputs. No production visibility method is replaced or bypassed. */
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
            var origin=h.absolutePos(BlockPos.ZERO);level.getGameRules().getRule(GameRuleRegistrar.FORCE_FOG).set(false,level.getServer());
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
