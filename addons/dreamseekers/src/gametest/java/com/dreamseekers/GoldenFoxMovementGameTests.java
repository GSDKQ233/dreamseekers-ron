// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.fox.FoxBalance;
import com.dreamseekers.unit.GoldenFoxUnit;
import com.dreamseekers.unit.PinkChickenScout;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.pasterdream.entity.GoldenFoxEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Actual ground motion through the ordinary RTS command path, never per-tick fixture teleportation. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldenFoxMovementGameTests {
    private static final String BATCH="dreamseekers_fox_movement", PREFIX="FoxMove_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity entity:FIXTURES)if(!entity.isRemoved())entity.discard();FIXTURES.clear();
        for(String a:List.of("Hostile","Ally"))for(String b:List.of("Hostile","Ally"))
            if(!a.equals(b))AlliancesServerEvents.removeAlliance(PREFIX+a,PREFIX+b);
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=500)
    public static void groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(GameTestHelper h) {
        floor(h);
        for(int z=2;z<=16;z++)for(int y=1;y<=3;y++)set(h,12,y,z,Blocks.STONE);
        GoldenFoxUnit fox=spawn(h,owner("Barrier"),4,10);BlockPos destination=h.absolutePos(new BlockPos(20,0,10));
        boolean[] detoured={false};double y=h.absolutePos(BlockPos.ZERO).getY();
        h.startSequence().thenWaitUntil(()->h.assertTrue(fox.tickCount>2,"wait genuine fox simulation")).thenExecute(()->{
            order(fox,UnitAction.MOVE,destination);
            for(int tick=1;tick<=400;tick++)h.runAfterDelay(tick,()->{
                h.assertTrue(!fox.isNoAi()&&!fox.isNoGravity()&&!fox.noPhysics&&h.getLevel().noCollision(fox),"normal ground body and active AI remain collision-safe");
                h.assertTrue(fox.getY()<y+1.6,"three-block wall is not crossed by flight or climbing");
                if(fox.getZ()>h.absolutePos(new BlockPos(0,0,17)).getZ() || fox.getZ()<h.absolutePos(new BlockPos(0,0,2)).getZ())detoured[0]=true;
            });
            h.runAfterDelay(410,()->{
                h.assertTrue(fox instanceof GoldenFoxEntity&&fox.getNavigation() instanceof GroundPathNavigation&&!fox.isFlyingUnit(),"actual original fox with normal ground navigator");
                h.assertTrue(detoured[0]&&near(fox,destination,1.8),"ground order routes around wall: "+describe(fox));
                System.out.println("DREAMSEEKERS_FOX_GROUND_BARRIER_PASS "+describe(fox));h.succeed();
            });
        });
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=690)
    public static void enclosedGroundFoxCannotPhaseAndRetriesOpenedExit(GameTestHelper h) {
        floor(h);
        for(int x=3;x<=11;x++)for(int z=3;z<=11;z++)for(int y=1;y<=5;y++)
            if(x==3||x==11||z==3||z==11||y==5)set(h,x,y,z,Blocks.STONE);
        GoldenFoxUnit fox=spawn(h,owner("Enclosed"),7,7);BlockPos destination=h.absolutePos(new BlockPos(18,0,7));
        h.startSequence().thenWaitUntil(()->h.assertTrue(fox.tickCount>2,"wait genuine enclosed simulation")).thenExecute(()->{
            order(fox,UnitAction.MOVE,destination);
            for(int tick=1;tick<190;tick++)h.runAfterDelay(tick,()->{
                h.assertTrue(h.getLevel().noCollision(fox)&&fox.getX()<h.absolutePos(new BlockPos(11,0,0)).getX(),"sealed wall prevents clipping/teleporting");
            });
            h.runAfterDelay(200,()->{for(int z=6;z<=8;z++)for(int y=1;y<=3;y++)set(h,11,y,z,Blocks.AIR);});
            h.runAfterDelay(580,()->{
                h.assertTrue(near(fox,destination,2)&&h.getLevel().noCollision(fox),"same pending ground order recovers through genuinely opened passage: "+describe(fox));
                System.out.println("DREAMSEEKERS_FOX_OPENING_RETRY_PASS "+describe(fox));h.succeed();
            });
        });
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=780)
    public static void queuedMovesHoldStopAndReplacementNeverResumeStalePath(GameTestHelper h) {
        floor(h);GoldenFoxUnit fox=spawn(h,owner("Queue"),4,4);
        BlockPos first=h.absolutePos(new BlockPos(17,0,4)),second=h.absolutePos(new BlockPos(17,0,17));
        boolean[] passedFirst={false};Vec3[] held={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(fox.tickCount>2,"wait ground queue readiness")).thenExecute(()->{
            order(fox,UnitAction.MOVE,first);
            UnitServerEvents.addActionItem(fox.getOwnerName(),UnitAction.MOVE,-1,new int[]{fox.getId()},second,BlockPos.ZERO,true);
            for(int tick=1;tick<=330;tick++)h.runAfterDelay(tick,()->{h.assertTrue(h.getLevel().noCollision(fox),"queue keeps collision");if(near(fox,first,1.8))passedFirst[0]=true;});
            h.runAfterDelay(340,()->{
                h.assertTrue(passedFirst[0]&&near(fox,second,1.8),"both queued waypoints visited in order: "+describe(fox));order(fox,UnitAction.HOLD,BlockPos.ZERO);
            });
            h.runAfterDelay(360,()->{
                held[0]=fox.position();h.assertTrue(fox.getHoldPosition()&&fox.getMoveGoal().getMoveTarget()==null,"HOLD has no stale ground destination");
                UnitServerEvents.addActionItem(fox.getOwnerName(),UnitAction.MOVE,-1,new int[]{fox.getId()},first,BlockPos.ZERO,true);
                order(fox,UnitAction.STOP,BlockPos.ZERO);
            });
            h.runAfterDelay(430,()->{
                h.assertTrue(fox.position().distanceToSqr(held[0])<.20&&!fox.getHoldPosition()&&fox.getNavigation().isDone(),"STOP cancels hold, queue and delayed paths");
                h.assertTrue(UnitServerEvents.getUnitActionSlowQueue().stream().noneMatch(a->Arrays.stream(a.getUnitIds()).anyMatch(id->id==fox.getId())),"no stale queued order");
                order(fox,UnitAction.MOVE,first);order(fox,UnitAction.MOVE,h.absolutePos(new BlockPos(4,0,17)));
            });
            h.runAfterDelay(550,()->{
                h.assertTrue(near(fox,h.absolutePos(new BlockPos(4,0,17)),1.8),"replacement defeats older async path: "+describe(fox));
                // Same-tick A -> B -> A is not a duplicate of the older first A.
                order(fox,UnitAction.MOVE,first);order(fox,UnitAction.MOVE,second);order(fox,UnitAction.MOVE,first);
            });
            h.runAfterDelay(710,()->{
                h.assertTrue(near(fox,first,1.8),"last A wins genuine A-B-A same-tick replacement: "+describe(fox));
                System.out.println("DREAMSEEKERS_FOX_QUEUE_HOLD_STOP_REPLACE_PASS");h.succeed();
            });
        });
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=250)
    public static void hostileCommandsCannotAttackOrTrackAndAllianceLossStopsFollow(GameTestHelper h) {
        floor(h);GoldenFoxUnit fox=spawn(h,owner("Hostile"),4,4),enemy=spawn(h,owner("Enemy"),9,4),ally=spawn(h,owner("Ally"),15,4);
        Vec3[] idle={null},cancelled={null};float health=enemy.getHealth();
        h.startSequence().thenWaitUntil(()->h.assertTrue(fox.tickCount>2,"wait noncombat command readiness")).thenExecute(()->{
            idle[0]=fox.position();
            UnitServerEvents.addActionItem(enemy.getOwnerName(),UnitAction.MOVE,-1,new int[]{fox.getId()},h.absolutePos(new BlockPos(20,0,4)),BlockPos.ZERO);
            h.runAfterDelay(15,()->{
                h.assertTrue(fox.position().distanceToSqr(idle[0])<.20&&fox.getMoveGoal().getMoveTarget()==null,"wrong-owner ordinary command cannot move or install a path");
                UnitServerEvents.addActionItem(fox.getOwnerName(),UnitAction.ATTACK,enemy.getId(),new int[]{fox.getId()},BlockPos.ZERO,BlockPos.ZERO);
            });
            h.runAfterDelay(35,()->{
                h.assertTrue(fox.getTarget()==null&&fox.getFollowTarget()==null&&!fox.doHurtTarget(enemy)&&enemy.getHealth()==health,"noncombat direct and RTS attacks are inert");
                h.assertTrue(fox.position().distanceToSqr(idle[0])<.20,"hostile attack cannot silently become enemy FOLLOW");
                AlliancesServerEvents.addAlliance(fox.getOwnerName(),ally.getOwnerName());fox.setFollowTarget(ally);
                h.assertTrue(fox.getFollowTarget()==ally,"explicit friendly follow accepted");
            });
            h.runAfterDelay(65,()->{
                h.assertTrue(fox.position().distanceToSqr(idle[0])>1,"friendly follow genuinely moves");
                AlliancesServerEvents.removeAlliance(fox.getOwnerName(),ally.getOwnerName());
            });
            h.runAfterDelay(85,()->{cancelled[0]=fox.position();h.assertTrue(fox.getFollowTarget()==null&&fox.getMoveGoal().getMoveTarget()==null,"alliance loss cancels follow and its move target");});
            h.runAfterDelay(160,()->{
                h.assertTrue(fox.position().distanceToSqr(cancelled[0])<.20&&enemy.getHealth()==health,"no stale chase/attack after alliance loss");
                h.assertTrue(!((Object)fox instanceof AttackerUnit)&&!((Object)fox instanceof WorkerUnit)&&fox.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.FOX_BLESSING,com.dreamseekers.skill.UnitSkillAbility.FOX_DREAM_SEED)),"fox retains exactly the approved blessing and dream seed, not offensive or worker hooks");
                System.out.println("DREAMSEEKERS_FOX_NONCOMBAT_FOLLOW_PASS");h.succeed();
            });
        });
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=300)
    public static void nativeNoAiCannotReturnThroughNbtAndWildNpcRemainsStationary(GameTestHelper h) {
        floor(h);String owner=owner("Reload");GoldenFoxUnit fox=spawn(h,owner,4,5);
        GoldenFoxEntity wild=net.pasterdream.init.PasterdreamModEntities.GOLDEN_FOX.get().create(h.getLevel());
        BlockPos wp=h.absolutePos(new BlockPos(4,1,15));wild.moveTo(wp.getX()+.5,wp.getY(),wp.getZ()+.5,0,0);FIXTURES.add(wild);h.assertTrue(h.getLevel().addFreshEntity(wild),"wild original control spawned");
        fox.setScenarioRoleIndex(86);fox.setOnDeathCommand("");fox.setNoAi(true);
        CompoundTag saved=new CompoundTag();fox.saveWithoutId(saved);
        h.assertTrue(saved.getBoolean("NoAI"),"fixture really contains native-style persisted NoAI");
        GoldenFoxUnit restored=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());restored.load(saved);
        // Temporarily remove only the fixture roster entry during exact-UUID replacement,
        // so disposing its sole original unit cannot trigger ordinary match defeat.
        RTSPlayer roster=PlayerServerEvents.getRTSPlayer(owner);
        PlayerServerEvents.rtsPlayers.remove(roster);
        java.util.UUID original=fox.getUUID();fox.discard();FIXTURES.add(restored);
        try {h.assertTrue(h.getLevel().addFreshEntity(restored),"restored exact UUID inserted once");}
        finally {PlayerServerEvents.rtsPlayers.add(roster);}
        h.assertTrue(restored.getUUID().equals(original)&&restored.getOwnerName().equals(owner)&&restored.getScenarioRoleIndex()==86&&Factions.getFaction(restored)==DreamseekersMod.DREAMSEEKERS,"UUID owner scenario faction roundtrip");
        h.assertTrue(!restored.isNoAi()&&!restored.isNoGravity()&&restored.getAttributeValue(Attributes.MOVEMENT_SPEED)==FoxBalance.SPEED,"paid ground AI/speed re-enabled despite saved NoAI");
        h.assertTrue(wild.isNoAi()&&wild.getAttributeValue(Attributes.MOVEMENT_SPEED)==0,"ordinary native NPC remains unchanged");
        Vec3 wildStart=wild.position();BlockPos destination=h.absolutePos(new BlockPos(18,0,5));
        h.startSequence().thenWaitUntil(()->h.assertTrue(restored.tickCount>2,"wait restored ordinary ticking")).thenExecute(()->{
            order(restored,UnitAction.MOVE,destination);
            h.runAfterDelay(210,()->{
                h.assertTrue(near(restored,destination,1.8)&&h.getLevel().noCollision(restored),"restored paid fox obeys movement: "+describe(restored));
                h.assertTrue(wild.position().distanceToSqr(wildStart)<.05&&wild.isNoAi(),"wild NPC not animated by new RTS goals");
                System.out.println("DREAMSEEKERS_FOX_NBT_NOAI_WILD_CONTROL_PASS");h.succeed();
            });
        });
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=80)
    public static void malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(GameTestHelper h) {
        floor(h);String owner=owner("InvalidTarget");GoldenFoxUnit fox=spawn(h,owner,4,4);
        PinkChickenScout scout=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(h.getLevel());
        h.assertTrue(scout!=null,"real scout regression control created");scout.setOwnerName(owner);
        BlockPos sp=h.absolutePos(new BlockPos(4,2,12));scout.moveTo(sp.getX()+.5,sp.getY(),sp.getZ()+.5,0,0);
        FIXTURES.add(scout);h.assertTrue(h.getLevel().addFreshEntity(scout),"scout enters actual level");
        BlockPos tp=h.absolutePos(new BlockPos(16,1,16));
        var item=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),tp.getX()+.5,tp.getY(),tp.getZ()+.5,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
        item.setNoGravity(true);FIXTURES.add(item);h.assertTrue(h.getLevel().addFreshEntity(item),"actual nonliving item target");
        var boat=net.minecraft.world.entity.EntityType.BOAT.create(h.getLevel());
        h.assertTrue(boat!=null,"actual boat created");boat.moveTo(tp.getX()+3.5,tp.getY(),tp.getZ()+.5,0,0);
        FIXTURES.add(boat);h.assertTrue(h.getLevel().addFreshEntity(boat),"actual nonliving boat target");
        var removed=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),tp.getX(),tp.getY(),tp.getZ(),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD));
        FIXTURES.add(removed);h.assertTrue(h.getLevel().addFreshEntity(removed),"removed target was genuinely registered");
        int removedId=removed.getId();removed.discard();
        h.startSequence().thenWaitUntil(()->h.assertTrue(fox.tickCount>2&&scout.tickCount>2,"wait both real controllers")).thenExecute(()->{
            h.assertTrue(h.getLevel().getEntity(removedId)==null,"removed target cannot still resolve");
            Vec3 foxStart=fox.position(),scoutStart=scout.position();
            for(Unit unit:List.<Unit>of(fox,scout))for(UnitAction action:List.of(UnitAction.FOLLOW,UnitAction.ATTACK))
                for(int id:new int[]{item.getId(),boat.getId(),Integer.MAX_VALUE,removedId}){
                    // Exercise actual dispatcher rather than replacing it with a test-side eligibility helper.
                    new UnitActionItem(owner,action,id,new int[]{((Entity)unit).getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                    h.assertTrue(unit.getFollowTarget()==null&&((net.minecraft.world.entity.Mob)unit).getTarget()==null
                            &&unit.getMoveGoal().getMoveTarget()==null,"invalid target installs no attack/follow/path");
                }
            h.runAfterDelay(35,()->{
                h.assertTrue(fox.position().distanceToSqr(foxStart)<.2
                        &&Math.abs(scout.getX()-scoutStart.x)<.3&&Math.abs(scout.getZ()-scoutStart.z)<.3,"invalid targets do not start delayed pursuit");
                h.assertTrue(fox.getItems().isEmpty()&&scout.getItems().isEmpty()&&item.isAlive()&&item.getItem().getCount()==7
                        &&boat.isAlive(),"invalid target commands consume/grant no objects");
                Resources ledger=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(owner)).findFirst().orElseThrow();
                h.assertTrue(ledger.food==500&&ledger.wood==500&&ledger.ore==500,"invalid targets grant no resources");
                System.out.println("DREAMSEEKERS_FOX_SCOUT_INVALID_ENTITY_COMMAND_NOCRASH_PASS");h.succeed();
            });
        });
    }

    private static String owner(String suffix){String name=PREFIX+suffix;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));return name;}
    private static GoldenFoxUnit spawn(GameTestHelper h,String owner,int x,int z){GoldenFoxUnit fox=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());h.assertTrue(fox!=null,"paid fox created");fox.setOwnerName(owner);BlockPos p=h.absolutePos(new BlockPos(x,1,z));fox.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);FIXTURES.add(fox);h.assertTrue(h.getLevel().addFreshEntity(fox),"paid fox entered actual level");return fox;}
    private static void order(GoldenFoxUnit fox,UnitAction action,BlockPos pos){UnitServerEvents.addActionItem(fox.getOwnerName(),action,-1,new int[]{fox.getId()},pos,BlockPos.ZERO);}
    private static boolean near(GoldenFoxUnit fox,BlockPos pos,double tolerance){return Math.abs(fox.getX()-(pos.getX()+.5))<tolerance&&Math.abs(fox.getZ()-(pos.getZ()+.5))<tolerance;}
    private static String describe(GoldenFoxUnit fox){return "pos="+fox.position()+" ticks="+fox.tickCount+" target="+fox.getMoveGoal().getMoveTarget()+" navDone="+fox.getNavigation().isDone()+" noAI="+fox.isNoAi();}
    private static void set(GameTestHelper h,int x,int y,int z,net.minecraft.world.level.block.Block b){h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),b.defaultBlockState(),2);}
    private static void floor(GameTestHelper h){
        BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=0;x<24;x++)for(int z=0;z<24;z++)set(h,x,10,z,Blocks.STONE);
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);
        for(int x=0;x<24;x++)for(int z=0;z<24;z++){set(h,x,0,z,Blocks.STONE);for(int y=1;y<10;y++)set(h,x,y,z,Blocks.AIR);}
    }
}
