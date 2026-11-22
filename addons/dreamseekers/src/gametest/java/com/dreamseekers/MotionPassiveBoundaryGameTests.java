// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.BuffKind;
import com.dreamseekers.skill.FireflyRush;
import com.dreamseekers.skill.SkillEffects;
import com.dreamseekers.skill.UnitSkill;
import com.dreamseekers.skill.WindDash;
import com.dreamseekers.unit.FireflyWorker;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/** Negative controls still require real measurable combat or movement, never empty-tick success. */
@GameTestHolder("dreamseekers_passive_edges")
@PrefixGameTestTemplate(false)
public final class MotionPassiveBoundaryGameTests {
    private static final String BATCH="motion_passive_edges", ARENA="unit_isolation_arena";
    @AfterBatch(batch=BATCH)
    public static void cleanup(ServerLevel level){AutocastGameTests.cleanup(level);}

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=260)
    public static void ordinaryWindHitWithoutVisibleEligibleAlliesPreservesDash(GameTestHelper h) {
        var f=new AutocastGameTests.F(h,"NoWind");
        var knight=f.spawn(DreamseekersMod.WIND_KNIGHT_UNIT.get(),f.own,8,8);
        var foe=f.durable(f.spawn(DreamseekersMod.JELLYFISH_UNIT.get(),f.foe,10,8));
        ShadowGolemStrikeSkillGameTests.neutral(foe);
        var worker=f.spawn(DreamseekersMod.FIREFLY_WORKER.get(),f.own,8,6);
        var hidden=f.spawn(DreamseekersMod.BONE_WING_UNIT.get(),f.ally,8,10);
        long[] revision={0},command={0};
        f.ready().thenExecute(()->{
            hidden.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,300));
        }).thenWaitUntil(()->h.assertTrue(hidden.isInvisible() && SkillEffects.friendly(knight,hidden)
                    && knight.distanceToSqr(hidden)<=9 && knight.distanceToSqr(worker)<=9
                    && knight.rts().skills().autocastEnabled(UnitSkill.WIND_DASH),
                    "invisibility="+hidden.isInvisible()+" allied="+SkillEffects.friendly(knight,hidden)
                            +" hiddenD2="+knight.distanceToSqr(hidden)+" workerD2="+knight.distanceToSqr(worker)
                            +" auto="+knight.rts().skills().autocastEnabled(UnitSkill.WIND_DASH)))
          .thenExecute(()->{
              f.focus(knight,foe);
              revision[0]=knight.rts().skills().revision();command[0]=knight.rts().commandRevision;
              knight.setNoAi(false); // Physical ground actor and native melee goal, no direct hurt invocation.
          }).thenWaitUntil(()->h.assertTrue(foe.getHealth()<foe.getMaxHealth() && foe.getLastHurtByMob()==knight,
                "actual ordinary melee landed on the selected hostile; hp="+foe.getHealth()+" distSq="+knight.distanceToSqr(foe)))
          .thenExecute(()->{
              h.assertTrue(!WindDash.isActive(knight)&&WindDash.knockbackTicks(foe)==0
                      &&knight.rts().skills().cooldownTicks(UnitSkill.WIND_DASH)==0
                      &&knight.rts().skills().revision()==revision[0]&&knight.rts().commandRevision==command[0]
                      &&knight.getTarget()==foe,"no standalone proc, dash, knockback lease or order rewrite on actual hit");
              h.assertTrue(hidden.isInvisible() && !SkillEffects.hasNamedBuff(hidden,WindDash.COMMAND_KEY,BuffKind.MOVEMENT_SPEED)
                      && !SkillEffects.hasNamedBuff(worker,WindDash.COMMAND_KEY,BuffKind.MOVEMENT_SPEED)
                      && !SkillEffects.hasNamedBuff(knight,WindDash.COMMAND_KEY,BuffKind.MOVEMENT_SPEED),
                      "concealed friend, worker and caster cannot stand in for a recipient");
              f.done();
          });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=500)
    public static void loadedFireflyHonorsMoveAndShiftRatherThanBanking(GameTestHelper h) {
        var f=new AutocastGameTests.F(h,"NoBank");
        var worker=f.spawn(DreamseekersMod.FIREFLY_WORKER.get(),f.own,4,5);
        // Real alternative destination reachable in the same arena; failure to find a stockpile
        // must not be what keeps the worker on its orders.
        var stockpile=Buildings.OAK_STOCKPILE;
        var home=h.absolutePos(new BlockPos(12,0,14));
        for(var b:BuildingUtils.getAbsoluteBlockData(stockpile.getRelativeBlockData(f.level),f.level,home,Rotation.NONE))
            f.level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        var depot=BuildingUtils.getNewBuildingPlacement(stockpile,f.level,home,Rotation.NONE,f.own,false);
        BuildingServerEvents.getBuildings().add(depot);depot.onBuilt();depot.initPlacedBlocks();
        f.cleanups.add(()->BuildingServerEvents.getBuildings().remove(depot));
        BlockPos first=h.absolutePos(new BlockPos(11,1,5)),second=h.absolutePos(new BlockPos(19,1,5));
        long[] revision={0};int[] value={0};
        f.ready().thenExecute(()->{
            h.assertTrue(depot.isBuilt&&stockpile.canAcceptResources&&f.own.equals(depot.ownerName),"owned deposit target exists");
            worker.setNoAi(false);
            var stack=new ItemStack(Items.OAK_LOG,4);
            value[0]=Resources.getTotalResourcesFromItems(java.util.List.of(stack)).wood;
            var item=new ItemEntity(f.level,worker.getX(),worker.getY(),worker.getZ(),stack);
            item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);item.setNoPickUpDelay();
            h.assertTrue(f.level.addFreshEntity(item),"real valued token published for native pickup");
            f.cleanups.add(()->{if(!item.isRemoved())item.discard();});
        }).thenWaitUntil(()->h.assertTrue(Unit.atThresholdResources(worker)&&worker.isIdle()
                  &&Resources.getTotalResourcesFromItems(worker.getItems()).wood==value[0],
                  "idle worker picked up a native return-sized load, but was never assigned harvest or return"))
          .thenExecute(()->{
              revision[0]=worker.rts().skills().revision();
              h.assertTrue(!worker.getReturnResourcesGoal().hasReturnOrder()&&worker.rts().skills().autocastEnabled(UnitSkill.FIREFLY_RUSH),
                      "idle load alone has not generated a trip; option is on");
              move(f,worker,first,false);move(f,worker,second,true);
          }).thenWaitUntil(()->h.assertTrue(first.equals(worker.getMoveGoal().getMoveTarget())&&!worker.getNavigation().isDone()
                    &&UnitServerEvents.getUnitActionSlowQueue().stream().anyMatch(q->java.util.Arrays.stream(q.getUnitIds()).anyMatch(id->id==worker.getId())),
                    "first authenticated MOVE in progress; second leg still waits in genuine Shift queue"))
          .thenExecute(()->notBanking(h,worker,revision[0],value[0]))
          .thenWaitUntil(()->h.assertTrue(second.equals(worker.getMoveGoal().getMoveTarget()) && !worker.getNavigation().isDone(),
                    "the native queue advances to the exact second destination, not the stockpile"))
          .thenExecute(()->notBanking(h,worker,revision[0],value[0]))
          .thenWaitUntil(()->h.assertTrue(worker.position().distanceToSqr(Vec3.atBottomCenterOf(second))<1
                    &&worker.getMoveGoal().getMoveTarget()==null&&worker.getNavigation().isDone(),
                    "both horizontal legs reached completion at the original second order; current="+worker.position()))
          .thenExecute(()->{notBanking(h,worker,revision[0],value[0]);f.done();});
    }
    private static void notBanking(GameTestHelper h,FireflyWorker worker,long revision,int cargo) {
        h.assertTrue(!worker.getReturnResourcesGoal().hasReturnOrder()&&!FireflyRush.active(worker)
                &&SkillEffects.bonus(worker,BuffKind.MOVEMENT_SPEED)==0
                &&worker.rts().skills().cooldownTicks(UnitSkill.FIREFLY_RUSH)==0&&worker.rts().skills().revision()==revision
                &&Resources.getTotalResourcesFromItems(worker.getItems()).wood==cargo,
                "no manufactured delivery, speed lease, spend, or lost cargo");
    }
    private static void move(AutocastGameTests.F f,FireflyWorker worker,BlockPos target,boolean shift) {
        try {
            var ctor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);
            ctor.setAccessible(true);
            var ctx=ctor.newInstance(f.peer.connection,NetworkDirection.PLAY_TO_SERVER,-1);
            f.h.assertTrue(ctx.getSender()==f.peer.actor&&new UnitActionServerboundPacket(f.own,UnitAction.MOVE,-1,
                    new int[]{worker.getId()},target,BlockPos.ZERO,shift).handle(()->ctx),"actual owner MOVE accepted, shift="+shift);
        } catch(ReflectiveOperationException error){throw new IllegalStateException("Forge owner context",error);}
    }
}
