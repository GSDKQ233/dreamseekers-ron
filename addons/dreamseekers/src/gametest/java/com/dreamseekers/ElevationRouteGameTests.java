// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.bonewing.BoneWingBolt;
import com.dreamseekers.unit.TeamSafeMeleeAttackGoal;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One native server order per journey. Only fixture creation positions entities; all later movement is AI. */
@GameTestHolder("dreamseekers_elevation_routes")
@PrefixGameTestTemplate(false)
public final class ElevationRouteGameTests {
    private static final String ARENA = "elevation_arena", VANILLA = "elevation_vanilla", GRID = "elevation_grid";
    private static final List<Mob> BODIES = new ArrayList<>();
    private static final Set<ChunkPos> TICKETS = new HashSet<>();
    private static boolean priorRule, priorFlag;

    @BeforeBatch(batch=VANILLA) public static void vanilla(ServerLevel level) { navigationMode(level, false); }
    @BeforeBatch(batch=GRID) public static void grid(ServerLevel level) { navigationMode(level, true); }
    private static void navigationMode(ServerLevel level, boolean grid) {
        priorRule = level.getGameRules().getBoolean(GameRuleRegistrar.RTS_PATHFINDING);
        priorFlag = UnitServerEvents.rtsPathfinding;
        level.getGameRules().getRule(GameRuleRegistrar.RTS_PATHFINDING).set(grid, level.getServer());
        UnitServerEvents.rtsPathfinding = grid;
    }
    @AfterBatch(batch=VANILLA) public static void cleanVanilla(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch=GRID) public static void cleanGrid(ServerLevel level) { cleanup(level); }
    private static void cleanup(ServerLevel level) {
        BODIES.forEach(Entity::discard);
        BODIES.clear();
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.startsWith("ElRoute_"));
        for (ChunkPos p : TICKETS) level.setChunkForced(p.x, p.z, false);
        TICKETS.clear();
        level.getGameRules().getRule(GameRuleRegistrar.RTS_PATHFINDING).set(priorRule, level.getServer());
        UnitServerEvents.rtsPathfinding = priorFlag;
    }

    private static final class Arena {
        final GameTestHelper h;
        final ServerLevel level;
        final BlockPos origin;
        final String own, foe;
        Arena(GameTestHelper h, String label) {
            this.h = h; level = h.getLevel(); origin = h.absolutePos(BlockPos.ZERO);
            own = "ElRoute_" + label; foe = own + "_foe";
            for (String name : List.of(own, foe))
                PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name, DreamseekersMod.DREAMSEEKERS));
            for (int cx = origin.getX() >> 4; cx <= (origin.getX() + 23) >> 4; cx++)
                for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + 23) >> 4; cz++) {
                    ChunkPos p = new ChunkPos(cx, cz);
                    if (!level.getForcedChunks().contains(p.toLong())) {
                        level.setChunkForced(cx, cz, true);
                        TICKETS.add(p);
                    }
                }
            // World generation may leave falling gravel. Seal before clearing, never edit geometry during a run.
            stone(0,13,0,23,13,23);
            level.getEntitiesOfClass(FallingBlockEntity.class, new AABB(origin.getX(), origin.getY()+1, origin.getZ(),
                    origin.getX()+24, level.getMaxBuildHeight(), origin.getZ()+24)).forEach(Entity::discard);
            for (int x=0; x<24; x++) for (int z=0; z<24; z++) for (int y=0; y<13; y++) {
                boolean solid = y==0 || x==0 || z==0 || x==23 || z==23;
                level.setBlock(origin.offset(x,y,z), (solid ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
            }
        }
        void stone(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x=x0; x<=x1; x++) for (int y=y0; y<=y1; y++) for (int z=z0; z<=z1; z++)
                level.setBlock(origin.offset(x,y,z), Blocks.STONE.defaultBlockState(), 2);
        }
        <T extends Mob & Unit> T unit(EntityType<T> type, String owner, int x, int y, int z) {
            T actor = Objects.requireNonNull(type.create(level));
            actor.setOwnerName(owner);
            actor.setNoAi(true); // wait for level publication; movement actor is enabled before its single order
            actor.moveTo(feet(x,y,z));
            h.assertTrue(level.addFreshEntity(actor), "registered fixture " + type);
            BODIES.add(actor);
            return actor;
        }
        GameTestSequence ready(Mob... units) {
            return h.startSequence().thenWaitUntil(() -> {
                for (Mob actor : units) h.assertTrue(actor.tickCount > 2 && level.getEntity(actor.getId()) == actor
                                && level.getEntity(actor.getUUID()) == actor,
                        "waiting for server publication and ticking: " + actor.getType());
            });
        }
        Vec3 feet(int x, int y, int z) { return Vec3.atBottomCenterOf(origin.offset(x,y,z)); }
        Vec3 local(Mob mob) { return mob.position().subtract(origin.getX(), origin.getY(), origin.getZ()); }
        void order(Mob source, UnitAction action, Mob target, BlockPos click) {
            source.setNoAi(false);
            new UnitActionItem(own,action,target==null ? -1 : target.getId(),new int[]{source.getId()},
                    click,BlockPos.ZERO).action(level);
            if (action == UnitAction.ATTACK) h.assertTrue(source.getTarget()==target && ((Unit)source).getTargetGoal().forced,
                    "native ATTACK did not select the requested target");
            else h.assertTrue(((Unit)source).getMoveGoal().getMoveTarget()!=null,"native MOVE did not retain an endpoint");
        }
        void solidBody(Mob actor) {
            h.assertTrue(actor.isAlive() && !actor.isNoAi() && !actor.noPhysics && level.noCollision(actor),
                    "real moving body must stay active and clear of solid blocks: "+local(actor));
        }
        void ledge(int lipTop) {
            stone(1,1,1,10,6,22);       // six-block plateau touching the walled back and both sides
            stone(10,7,1,10,lipTop,22); // only exit is up over the opaque lip, then down to the lower floor
        }
    }

    @GameTest(template=ARENA,batch=VANILLA,timeoutTicks=420)
    public static void scoutClimbsPastLipThenDescendsToClickedLowerFloor(GameTestHelper h) {
        var a=new Arena(h,"Down");a.ledge(8);
        var scout=a.unit(DreamseekersMod.PINK_CHICKEN_SCOUT.get(),a.own,5,7,12);
        var landing=a.feet(20,1,12);
        var peak=new double[]{7};var crossed=new boolean[1];
        var sequence=a.ready(scout).thenExecute(()->{
            h.assertTrue(!com.dreamseekers.unit.flight.FlightWorkPositions.clearLine(scout,scout.getEyePosition(),
                    landing.add(0,scout.getEyeHeight(),0),null),"ledge must obscure the low click");
            a.order(scout,UnitAction.MOVE,null,a.origin.offset(20,0,12));
        });
        for(int i=0;i<355;i++)sequence.thenIdle(1).thenExecute(()->{
            a.solidBody(scout);
            var pos=a.local(scout);peak[0]=Math.max(peak[0],pos.y);
            if(pos.x>11.5)crossed[0]=true;
            h.assertTrue(pos.y<10.9,"low flyer overshot launch+3 band: "+pos);
        });
        sequence.thenExecute(()->h.assertTrue(peak[0]>=8.99 && crossed[0] && scout.position().distanceTo(landing)<1.5
                        && scout.getMoveGoal().isAtDestination(),
                "MOVE must climb, cross, and settle at low clicked floor: peak="+peak[0]+" final="+a.local(scout)
                        +" goal="+scout.getMoveGoal().getMoveTarget())).thenSucceed();
    }

    @GameTest(template=ARENA,batch=VANILLA,timeoutTicks=440)
    public static void boneWingPursuesLowerEnemyBeyondLipBeforeFiring(GameTestHelper h) {
        var a=new Arena(h,"Bolt");a.ledge(8);
        var wing=a.unit(DreamseekersMod.BONE_WING_UNIT.get(),a.own,5,7,12);
        var foe=a.unit(DreamseekersMod.JELLYFISH_UNIT.get(),a.foe,21,1,12);
        foe.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        float hp=foe.getHealth();var peak=new double[]{7};boolean[] seen=new boolean[3];
        var sequence=a.ready(wing,foe).thenExecute(()->{
            h.assertTrue(!wing.hasLineOfSight(foe),"opaque lip must initially block actual eye LOS");
            a.order(wing,UnitAction.ATTACK,foe,BlockPos.ZERO);
        });
        for(int i=0;i<365;i++)sequence.thenIdle(1).thenExecute(()->{
            a.solidBody(wing);var pos=a.local(wing);peak[0]=Math.max(peak[0],pos.y);
            if(pos.x>11)seen[0]=true;
            if(wing.hasLineOfSight(foe))seen[1]=true;
            for(var bolt:a.level.getEntitiesOfClass(BoneWingBolt.class,wing.getBoundingBox().inflate(12),
                    b->b.getOwner()==wing)) {
                h.assertTrue(seen[1] && !bolt.noPhysics,"no bone bolt through opaque terrain");
                seen[2]=true;
            }
            h.assertTrue(foe.getHealth()==hp || seen[1],"no impact before obtaining real eye LOS");
            h.assertTrue(pos.y<10.9,"repeated pursuit path queries cannot ratchet above launch+3");
        });
        sequence.thenExecute(()->h.assertTrue(peak[0]>=8.99 && seen[0] && seen[1] && seen[2] && foe.getHealth()<hp,
                "same forced ATTACK must climb, regain LOS, release a real bolt, and hit: peak="+peak[0]+" final="
                        +a.local(wing)+" targetHP="+foe.getHealth()+" bolt="+seen[2])).thenSucceed();
    }

    @GameTest(template=ARENA,batch=VANILLA,timeoutTicks=365)
    public static void impossibleHighLipCannotBeScaledByReplanning(GameTestHelper h) {
        var a=new Arena(h,"High");a.ledge(11); // 1 free block over the wall fits this scout physically, but lies above its launch+3 budget
        var scout=a.unit(DreamseekersMod.PINK_CHICKEN_SCOUT.get(),a.own,5,7,12);
        var sequence=a.ready(scout).thenExecute(()->a.order(scout,UnitAction.MOVE,null,a.origin.offset(20,0,12)));
        for(int i=0;i<300;i++)sequence.thenIdle(1).thenExecute(()->{
            a.solidBody(scout);var p=a.local(scout);
            h.assertTrue(p.x<10 && p.y<10.9,"blocked flyer cannot phase or progressively climb above launch band: "+p);
        });
        sequence.thenExecute(()->h.assertTrue(scout.getMoveGoal().getMoveTarget()!=null && !scout.getMoveGoal().isAtDestination(),
                "inaccessible low destination must not be marked as reached")).thenSucceed();
    }

    @GameTest(template=ARENA,batch=VANILLA,timeoutTicks=390)
    public static void groundMeleeWithinNominalRangeMustDetourBeforeHitting(GameTestHelper h) { melee(h,false); }
    @GameTest(template=ARENA,batch=VANILLA,timeoutTicks=390)
    public static void unreachableRaisedEnemyCannotBeHitThroughClosedPartition(GameTestHelper h) { melee(h,true); }
    private static void melee(GameTestHelper h, boolean sealed) {
        var a=new Arena(h,sealed?"Seal":"Around");
        a.stone(12,1,1,22,1,22);
        a.stone(11,1,1,11,8,sealed?22:15);
        // The raised enemy is already within raw melee reach across one block of stone.
        var knight=a.unit(DreamseekersMod.WIND_KNIGHT_UNIT.get(),a.own,9,1,9);
        var foe=a.unit(DreamseekersMod.JELLYFISH_UNIT.get(),a.foe,12,2,9);
        foe.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        float full=foe.getHealth();boolean[] exposed={false},around={false},up={false};
        var goal=(TeamSafeMeleeAttackGoal)knight.getAttackGoal();
        var sequence=a.ready(knight,foe).thenExecute(()->{
            double reach=Math.pow((knight.getBbWidth()+knight.getBonusMeleeRange())*2,2)+foe.getBbWidth()+foe.getBonusMeleeRangeForAttackers();
            h.assertTrue(knight.distanceToSqr(foe)<reach && !knight.hasLineOfSight(foe),"fixture must start in nominal reach but occluded");
            a.order(knight,UnitAction.ATTACK,foe,BlockPos.ZERO);
        });
        for(int i=0;i<300;i++)sequence.thenIdle(1).thenExecute(()->{
            a.solidBody(knight);var p=a.local(knight);
            if(p.z>15.9)around[0]=true;
            if(p.x>12 && p.y>=1.99)up[0]=true;
            if(knight.hasLineOfSight(foe))exposed[0]=true;
            if(!exposed[0])h.assertTrue(foe.getHealth()==full && goal.transformationCooldown()==0,
                    "no damage or spent melee cooldown through occluding wall: "+p+" hp="+foe.getHealth()+" cd="+goal.transformationCooldown());
        });
        sequence.thenExecute(()->{
            if(sealed)h.assertTrue(!exposed[0] && !up[0] && foe.getHealth()==full && knight.getTarget()==foe,
                    "closed raised target stays out of reach despite a retained ATTACK");
            else h.assertTrue(exposed[0] && around[0] && up[0] && foe.getHealth()<full,
                    "native melee must round the wall, step up, then hit: "+a.local(knight)+" hp="+foe.getHealth());
        }).thenSucceed();
    }

    @GameTest(template=ARENA,batch=VANILLA,timeoutTicks=405)
    public static void meleeFlyerMustRoundPartitionBeforeSpendingOrdinarySwing(GameTestHelper h) {
        var a=new Arena(h,"AirMelee");a.stone(12,1,1,22,1,22);a.stone(11,1,1,11,12,14);
        var hand=a.unit(DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get(),a.own,8,1,9);
        var foe=a.unit(DreamseekersMod.JELLYFISH_UNIT.get(),a.foe,14,2,9);
        foe.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        float full=foe.getHealth();boolean[] detoured={false},exposed={false};
        var goal=(TeamSafeMeleeAttackGoal)hand.getAttackGoal();
        var seq=a.ready(hand,foe).thenExecute(()->{
            double reach=Math.pow((hand.getBbWidth()+hand.getBonusMeleeRange())*2,2)+foe.getBbWidth()+foe.getBonusMeleeRangeForAttackers();
            h.assertTrue(hand.distanceToSqr(foe)<reach && !hand.hasLineOfSight(foe),"large flyer starts within occluded raw reach");
            a.order(hand,UnitAction.ATTACK,foe,BlockPos.ZERO);
        });
        for(int i=0;i<325;i++)seq.thenIdle(1).thenExecute(()->{
            a.solidBody(hand);var p=a.local(hand);
            if(p.z>14.9)detoured[0]=true;
            if(hand.hasLineOfSight(foe))exposed[0]=true;
            if(!exposed[0])h.assertTrue(foe.getHealth()==full && goal.transformationCooldown()==0,
                    "ordinary flying melee must not spend a swing into opaque terrain: "+p+" cd="+goal.transformationCooldown());
        });
        seq.thenExecute(()->h.assertTrue(detoured[0] && exposed[0] && foe.getHealth()<full,
                "real airborne hand must take lateral route to visible raised enemy before hitting: "+a.local(hand)+" hp="+foe.getHealth())).thenSucceed();
    }

    @GameTest(template=ARENA,batch=VANILLA,timeoutTicks=385)
    public static void groundMoveReachesObscuredRampDestination(GameTestHelper h) { ramp(h,false); }
    @GameTest(template=ARENA,batch=GRID,timeoutTicks=420)
    public static void gridMoveReachesObscuredRampDestination(GameTestHelper h) { ramp(h,true); }
    private static void ramp(GameTestHelper h, boolean grid) {
        var a=new Arena(h,grid?"Grid":"Ramp");a.stone(9,1,7,9,7,22);
        for(int step=1;step<=3;step++)a.stone(10+step,step,step==3?11:1,22,step,22);
        // The clicked block is on the north edge of the deck. The standable cell north of it is
        // exactly as far from the solid click as the intended feet cell, but one storey lower.
        var source=a.unit(DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),a.own,5,1,11);
        BlockPos click=a.origin.offset(19,3,11);
        var landing=a.feet(19,4,11);var wentAround=new boolean[1];
        BlockPos[] delivered={null};
        var sequence=a.ready(source).thenExecute(()->{
            h.assertTrue(UnitServerEvents.rtsPathfinding==grid,"correct navigation mode for this batch");
            h.assertTrue(!com.dreamseekers.unit.flight.FlightWorkPositions.clearLine(source,source.getEyePosition(),
                    landing.add(0,source.getEyeHeight(),0),null),"initial sightline to top of ramp must be blocked");
            a.order(source,UnitAction.MOVE,null,click);
            h.assertTrue(click.equals(source.getMoveGoal().getMoveTarget()), "order must retain the clicked surface, not a shifted waypoint");
        });
        for(int i=0;i<325;i++)sequence.thenIdle(1).thenExecute(()->{
            a.solidBody(source);var p=a.local(source);
            if(p.z<7.05)wentAround[0]=true;
            var path=source.getNavigation().getPath();
            if(delivered[0]==null && path!=null && !path.isDone())delivered[0]=path.getTarget();
        });
        sequence.thenExecute(()->h.assertTrue(wentAround[0] && click.above().equals(delivered[0]) && Math.abs(source.getY()-landing.y)<.1
                        && source.position().distanceTo(landing)<1.5 && source.getNavigation().isDone(),
                "ground MOVE must reach the requested upper deck via the opening from below: final="+a.local(source)
                        +" delivered="+(delivered[0]==null?null:delivered[0].subtract(a.origin))+" click="+click.subtract(a.origin))).thenSucceed();
    }

    @GameTest(template=ARENA,batch=VANILLA,timeoutTicks=230)
    public static void clearLevelLaneStillSettlesWithoutDetour(GameTestHelper h) {
        var a=new Arena(h,"Flat");
        var scout=a.unit(DreamseekersMod.PINK_CHICKEN_SCOUT.get(),a.own,4,1,10);
        var sequence=a.ready(scout).thenExecute(()->a.order(scout,UnitAction.MOVE,null,a.origin.offset(19,0,10)));
        for(int i=0;i<170;i++)sequence.thenIdle(1).thenExecute(()->{
            a.solidBody(scout);
            var p=a.local(scout);
            h.assertTrue(p.y<4.9 && Math.abs(p.z-10.5)<1.5,
                    "level lane stays within the native low-flight band without a lateral detour: "+p);
        });
        sequence.thenExecute(()->h.assertTrue(scout.position().distanceTo(a.feet(19,1,10))<1.5
                        && scout.getMoveGoal().isAtDestination(),"flat MOVE settles normally: "+a.local(scout))).thenSucceed();
    }
}
