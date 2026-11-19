// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.bonewing.BoneWingBalance;
import com.dreamseekers.production.DreamProductionPlacement;
import com.solegendary.reignofnether.building.*;
import net.minecraft.world.level.block.Rotation;
import com.dreamseekers.unit.BoneWingUnit;
import com.dreamseekers.unit.flight.LowAltitudeFlyingNavigation;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;
/** Ordinary entity/RTS/resource storage across two real JVMs, no manually substituted deserialize. */
@GameTestHolder("dreamseekers_bone_wing_restart")
@PrefixGameTestTemplate(false)
public final class BoneWingRestartGameTests {
    private static final String OWNER="BoneRestart_Owner";
    private static final BlockPos ORIGIN=new BlockPos(14336,90,14336);
    private static final File MANIFEST=new File("bonewing-restart-expectations.nbt");
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_bone_wing_restart",timeoutTicks=840)
    public static void ordinaryRestartRetainsIdentityHealthAndGenuineCollisionFlight(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.bonewing.test.phase","")) {
            case "prepare" -> prepare(h); case "verify" -> verify(h);
            default -> throw new IllegalArgumentException("Unknown bonewing restart phase");
        }
    }
    private static void prepare(GameTestHelper h) {
        ServerLevel level=h.getLevel();h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER)==null&&!MANIFEST.exists(),"fresh isolated restart fixture");
        CompoundTag expected=new CompoundTag();expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        for(int cx=896;cx<=897;cx++)for(int cz=896;cz<=899;cz++) {
            long key=ChunkPos.asLong(cx,cz);h.assertTrue(!level.getForcedChunks().contains(key),"never adopt existing forced chunks");level.setChunkForced(cx,cz,true);forced.add(key);
        }
        expected.putLongArray("forcedChunks",forced);
        for(int x=0;x<30;x++)for(int z=0;z<60;z++)level.setBlock(ORIGIN.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+30,level.getMaxBuildHeight(),ORIGIN.getZ()+60)).forEach(Entity::discard);
        // One-time side-fluid isolation, matching the already verified later restart fixtures.
        for(int x=0;x<30;x++)for(int z=0;z<60;z++)if(x==0||x==29||z==0||z==59)
            for(int y=1;y<10;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        for(int x=0;x<30;x++)for(int z=0;z<60;z++) {
            level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            if(x>0&&x<29&&z>0&&z<59)for(int y=1;y<10;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(OWNER,123,234,345,456));
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER+"_NewOwner",DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(OWNER+"_NewOwner",777,888,999,111));
        BlockPos anchorPos=ORIGIN.offset(0,0,43);
        for(BuildingBlock block:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(level),level,anchorPos,Rotation.NONE))level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        BuildingPlacement anchor=BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,level,anchorPos,Rotation.NONE,OWNER,false);
        BuildingServerEvents.getBuildings().add(anchor);anchor.onBuilt();
        BlockPos producerPos=ORIGIN.offset(0,0,25);
        for(BuildingBlock block:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_HALL.getRelativeBlockData(level),level,producerPos,Rotation.NONE))
            level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        BuildingPlacement producer=BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_HALL,level,producerPos,Rotation.NONE,OWNER,false);
        h.assertTrue(producer instanceof DreamProductionPlacement,"real addon producer factory before ordinary save");
        BuildingServerEvents.getBuildings().add(producer);producer.onBuilt();
        ((DreamProductionPlacement)producer).setRallyPoint(ORIGIN.offset(25,0,35));
        expected.putLong("producerOrigin",producerPos.asLong());
        BoneWingUnit jelly=DreamseekersMod.BONE_WING_UNIT.get().create(level);h.assertTrue(jelly!=null,"registered paid native bonewing");
        jelly.setOwnerName(OWNER);jelly.setScenarioRoleIndex(88);jelly.setOnDeathCommand("");jelly.moveTo(ORIGIN.getX()+4.5,ORIGIN.getY()+1,ORIGIN.getZ()+5.5);
        h.assertTrue(level.addFreshEntity(jelly),"actual normal entity storage");expected.putUUID("bonewing",jelly.getUUID());
        h.startSequence().thenWaitUntil(()->h.assertTrue(jelly.tickCount>2&&level.getEntity(jelly.getId())==jelly&&level.getEntity(jelly.getUUID())==jelly,"real published ordinary ticks before persistence"))
            .thenExecute(()->{h.assertTrue(jelly.hurt(jelly.damageSources().generic(),5)&&jelly.getHealth()<BoneWingBalance.MAX_HEALTH,"ordinary damage before shutdown");order(jelly,UnitAction.MOVE,ORIGIN.offset(12,0,5));})
            .thenIdle(240).thenExecute(()->{h.assertTrue(near(jelly,ORIGIN.offset(12,0,5)),"first JVM genuine flight arrival: "+jelly.position());order(jelly,UnitAction.STOP,BlockPos.ZERO);})
            .thenIdle(30).thenExecute(()->{
                foundation(h,jelly);ledger(h);h.assertTrue(jelly.getMoveGoal().getMoveTarget()==null&&jelly.getNavigation().isDone(),"actual stopped state before storage");
                expected.putFloat("health",jelly.getHealth());expected.putDouble("x",jelly.getX());expected.putDouble("y",jelly.getY());expected.putDouble("z",jelly.getZ());
                var paidHall=(com.dreamseekers.production.DreamHallPlacement)producer;
                h.assertTrue(paidHall.startProductionItem(DreamseekersMod.BONE_WING_PRODUCTION),"genuine paid queue immediately before normal shutdown");
                Resources debit=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(OWNER)).findFirst().orElseThrow();
                h.assertTrue(debit.food==23&&debit.ore==285&&paidHall.productionQueue.size()==1,"actual original payment deducted once");
                paidHall.ownerName=OWNER+"_NewOwner"; // No intervening tick: HIGHEST resource snapshot must use original receipt.
                expected.putBoolean("paidBeforeTransferAndStop",true);
                try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
                System.out.println("DREAMSEEKERS_BONE_WING_RESTART_PREPARED uuid="+jelly.getUUID()+" health="+jelly.getHealth()+" pos="+jelly.position());
            }).thenSucceed(); // Normal shutdown owns entity, roster and resource saving.
    }
    private static void verify(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"first JVM manifest exists");CompoundTag expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"genuine different JVM");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"normal world restored same chunks");ChunkPos c=new ChunkPos(key);level.getChunk(c.x,c.z);}
        BoneWingUnit[] ref={null};Vec3[] start={null};double[] maxY={0};
        com.dreamseekers.unit.GoldenFoxUnit[] ground={null};float[] enemyHealth={0};double[] travel={0};
        var sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(level.getEntity(expected.getUUID("bonewing")) instanceof BoneWingUnit jelly&&jelly.tickCount>2,"actual asynchronous entity loader and real ticks"))
            .thenExecute(()->{
                BoneWingUnit jelly=(BoneWingUnit)level.getEntity(expected.getUUID("bonewing"));ref[0]=jelly;foundation(h,jelly);ledger(h);
                var loadedProducer=BuildingServerEvents.getBuildings().stream().filter(b->b.originPos.equals(BlockPos.of(expected.getLong("producerOrigin")))).findFirst().orElseThrow();
                h.assertTrue(loadedProducer instanceof DreamProductionPlacement p&&p.getBuilding()==DreamseekersMod.DREAM_HALL
                        &&p.ownerName.equals(OWNER+"_NewOwner")&&p.isBuilt&&!p.shouldBeDestroyed()&&p.productionQueue.isEmpty()
                        &&p.getFinalRallyPoint().equals(ORIGIN.offset(25,0,35)),"ordinary registry-based producer load retains identity, live geometry and rally, without a free production");

                // Verify the actual restored factory, not a newly created stand-in Hall.
                h.assertTrue(loadedProducer instanceof com.dreamseekers.production.DreamHallPlacement,"restored Hall retains paid receipt subclass");
                h.assertTrue(((com.solegendary.reignofnether.building.production.ProductionBuilding)loadedProducer.getBuilding()).productions.get().equals(java.util.List.of(
                    DreamseekersMod.BONE_WING_PRODUCTION,DreamseekersMod.WIND_KNIGHT_PRODUCTION,DreamseekersMod.STONE_SPIRIT_THROWER_PRODUCTION,
                    DreamseekersMod.BEETLE_MOTHER_PRODUCTION,DreamseekersMod.SHADOW_GHOST_PRODUCTION,DreamseekersMod.SHADOW_SQUEAL_GHOST_PRODUCTION,
                    DreamseekersMod.TERRORBEAK_PRODUCTION,DreamseekersMod.FRIENDLY_GHOST_PRODUCTION,DreamseekersMod.SHADOW_HAND_PRODUCTION,
                    DreamseekersMod.ASH_BONE_WING_PRODUCTION,DreamseekersMod.SHADOW_GOLEM_PRODUCTION,DreamseekersMod.WEEPING_SQUEAL_GHOST_PRODUCTION)),
                    "loaded Hall preserves the exact two six-slot page recipe order; this is not GUI rendering");
                var hall=(com.dreamseekers.production.DreamHallPlacement)loadedProducer;
                var restoredAnchor=BuildingServerEvents.getBuildings().stream().filter(b->b.originPos.equals(ORIGIN.offset(0,0,43))&&b.getBuilding()==DreamseekersMod.DREAM_ANCHOR&&b.ownerName.equals(OWNER)).findFirst().orElseThrow();
                long rosterBefore=UnitServerEvents.getAllUnits().size();
                // Give the transferee a real prerequisite only for this same-callback paid/cancel probe.
                // No time advance, artificial queue completion or minted resources.
                String anchorOwner=restoredAnchor.ownerName;restoredAnchor.ownerName=hall.ownerName;
                try {
                    var recipe=DreamseekersMod.WEEPING_SQUEAL_GHOST_PRODUCTION;
                    var cost=recipe.getCost(false,hall.ownerName);
                    Resources payer=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(hall.ownerName)).findFirst().orElseThrow();
                    h.assertTrue(hall.startProductionItem(recipe)&&hall.productionQueue.size()==1,"loaded Hall accepts real paid last-page recipe");
                    h.assertTrue(payer.food==777-cost.food&&payer.wood==888-cost.wood&&payer.ore==999-cost.ore&&payer.emerald==111-cost.emerald,"new owner pays its actual ordinary bill exactly once after load");
                    h.assertTrue(hall.cancelProductionItem(recipe,true)&&!hall.cancelProductionItem(recipe,true)&&hall.productionQueue.isEmpty(),"loaded paid queue refunds once and repeated cancel is empty");
                    ledger(h);h.assertTrue(UnitServerEvents.getAllUnits().size()==rosterBefore,"cancellation does not mint any unit");
                } finally {restoredAnchor.ownerName=anchorOwner;}
                System.out.println("ROSTER_RESTART_HALL_12_WHITELIST_LAST_RECIPE_PAID_CANCEL_PASS");

                h.assertTrue(jelly.getScenarioRoleIndex()==88&&jelly.getOnDeathCommand().isEmpty()&&jelly.getAnchor().equals(BlockPos.ZERO),"scenario owner and unanchored identity preserved");
                h.assertTrue(jelly.getHealth()==expected.getFloat("health")&&jelly.getHealth()<BoneWingBalance.MAX_HEALTH,"health not reset on native load");
                h.assertTrue(jelly.position().distanceToSqr(new Vec3(expected.getDouble("x"),expected.getDouble("y"),expected.getDouble("z")))<.2&&jelly.getMoveGoal().getMoveTarget()==null&&jelly.getFollowTarget()==null,"stopped position has no wild motion or stale target");
                h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(jelly.getUUID())).count()==1,"exact UUID appears once");
                h.assertTrue(expected.getBoolean("paidBeforeTransferAndStop"),"first JVM actually paid before transfer/stop");
                var target=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);target.setOwnerName(OWNER+"_NewOwner");target.setNoAi(true);
                target.moveTo(ORIGIN.getX()+20.5,ORIGIN.getY()+1,ORIGIN.getZ()+5.5);h.assertTrue(level.addFreshEntity(target),"real post-load ground enemy");ground[0]=target;enemyHealth[0]=target.getHealth();
            }).thenWaitUntil(()->h.assertTrue(ground[0].tickCount>2&&level.getEntity(ground[0].getId())==ground[0]&&level.getEntity(ground[0].getUUID())==ground[0],"ground enemy genuinely published"))
            .thenExecute(()->{new com.solegendary.reignofnether.unit.UnitActionItem(OWNER,UnitAction.ATTACK,ground[0].getId(),new int[]{ref[0].getId()},BlockPos.ZERO,BlockPos.ZERO).action(level);ref[0].setHoldPosition(true);});
        for(int i=0;i<100;i++)sequence.thenIdle(1).thenExecute(()->{for(Entity e:level.getAllEntities())if(e instanceof com.dreamseekers.bonewing.BoneWingBolt bolt&&bolt.getOwner()==ref[0])travel[0]=Math.max(travel[0],bolt.distanceTo(ref[0]));});
        sequence.thenExecute(()->{
                BoneWingUnit jelly=ref[0];h.assertTrue(travel[0]>1&&ground[0].getHealth()<enemyHealth[0]&&!ground[0].isOnFire(),"reloaded real ranged goal physically damages a ground enemy without native fire");
                ground[0].discard();order(jelly,UnitAction.STOP,BlockPos.ZERO);
                for(int x=10;x<=28;x++)for(int y=1;y<=7;y++)for(int z:new int[]{3,7})level.setBlock(ORIGIN.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
                for(int z=4;z<=6;z++)for(int y=1;y<=2;y++)level.setBlock(ORIGIN.offset(18,y,z),Blocks.STONE.defaultBlockState(),2);
                start[0]=jelly.position();maxY[0]=jelly.getY();order(jelly,UnitAction.MOVE,ORIGIN.offset(25,0,5));
            });
        for(int i=0;i<330;i++)sequence.thenIdle(1).thenExecute(()->{
            BoneWingUnit jelly=ref[0];h.assertTrue(level.noCollision(jelly)&&!jelly.isNoAi()&&jelly.isNoGravity()&&!jelly.noPhysics,"every observed second-JVM flight tick preserves body collision and active AI");maxY[0]=Math.max(maxY[0],jelly.getY());
        });
        sequence.thenExecute(()->{
            BoneWingUnit jelly=ref[0];h.assertTrue(jelly.position().distanceToSqr(start[0])>16&&near(jelly,ORIGIN.offset(25,0,5))&&maxY[0]>=ORIGIN.getY()+2.95,"second JVM genuinely flies over new two-block obstacle: "+jelly.position()+" maxY="+maxY[0]);order(jelly,UnitAction.HOLD,BlockPos.ZERO);
        }).thenIdle(20).thenExecute(()->{h.assertTrue(ref[0].getHoldPosition()&&ref[0].getNavigation().isDone(),"post restart HOLD executes");order(ref[0],UnitAction.STOP,BlockPos.ZERO);})
          .thenIdle(20).thenExecute(()->{
            BoneWingUnit jelly=ref[0];foundation(h,jelly);ledger(h);h.assertTrue(!jelly.getHoldPosition()&&jelly.getMoveGoal().getMoveTarget()==null&&jelly.getItems().isEmpty(),"post restart STOP executes without gifts or cargo");
            BuildingServerEvents.getBuildings().removeIf(b->b.ownerName.startsWith(OWNER));
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(OWNER));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(OWNER));jelly.discard();
            for(long key:expected.getLongArray("forcedChunks")){ChunkPos c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}
            System.out.println("DREAMSEEKERS_BONE_WING_RESTART_UUID_HEALTH_FLIGHT_PASS uuid="+expected.getUUID("bonewing"));
          }).thenSucceed();
    }
    private static void foundation(GameTestHelper h,BoneWingUnit jelly) {
        h.assertTrue(jelly.getOwnerName().equals(OWNER)&&Factions.getFaction(jelly)==DreamseekersMod.DREAMSEEKERS,"owner faction identity");
        h.assertTrue(!jelly.isNoAi()&&jelly.isNoGravity()&&jelly.isFlyingUnit()&&!jelly.noPhysics&&jelly.isPersistenceRequired()&&!jelly.canPickUpLoot(),"paid active collision flight and persistence flags");
        h.assertTrue(jelly.getNavigation() instanceof LowAltitudeFlyingNavigation&&jelly.getAttributeValue(Attributes.FLYING_SPEED)==BoneWingBalance.SPEED&&jelly.getBbWidth()==1.7f&&jelly.getBbHeight()==.8f,"original adult body and actual provisional flight navigator");
        h.assertTrue(((Object)jelly instanceof AttackerUnit)&&!((Object)jelly instanceof WorkerUnit)&&jelly.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.COORDINATED_MARK))&&jelly.getTarget()==null&&!jelly.canGarrison()&&jelly.getAttackGoal() instanceof com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal&&!jelly.canAttackBuildings(),"true ranged baseline and only approved coordinated mark remain active; native special/building attack/worker duties unavailable");
    }
    private static void ledger(GameTestHelper h){Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(OWNER)).findFirst().orElseThrow();h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456,"ordinary save movement grants nothing and debits nothing twice");Resources b=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(OWNER+"_NewOwner")).findFirst().orElseThrow();h.assertTrue(b.food==777&&b.wood==888&&b.ore==999&&b.emerald==111,"transferee never receives original payer pending refund");}
    private static void order(BoneWingUnit jelly,UnitAction action,BlockPos pos){UnitServerEvents.addActionItem(OWNER,action,-1,new int[]{jelly.getId()},pos,BlockPos.ZERO);}
    private static boolean near(BoneWingUnit jelly,BlockPos p){return Math.abs(jelly.getX()-(p.getX()+.5))<2&&Math.abs(jelly.getZ()-(p.getZ()+.5))<2;}
}
