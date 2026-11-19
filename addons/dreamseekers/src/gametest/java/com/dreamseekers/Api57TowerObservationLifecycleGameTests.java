// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.*;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.research.economy.EconomyActionGate;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
import static com.dreamseekers.BuildingObservationTestSupport.*;

/** API56 audit reproduction inverted into desired behavior, with real lifecycle controls. */
@GameTestHolder("api57_tower_observation_lifecycle") @PrefixGameTestTemplate(false)
public final class Api57TowerObservationLifecycleGameTests {
    private static final String NS="api57_tower_observation_lifecycle";
    @AfterBatch(batch=NS) public static void cleanupLifecycle(ServerLevel ignored){cleanup();}

    private static long cast(F f){
        // Establish the ordinary connected-peer session before asking the service for availability.
        f.h.assertTrue(EconomyActionGate.query(f.peer.actor,f.peer.connection)!=null,"live ordinary economy session");
        var view=BuildingAbilityService.view(f.peer.actor,f.host);
        f.h.assertTrue(view!=null&&view.available(),"exact-owner source available: "+view);
        String result=BuildingAbilityService.cast(f.peer.actor,f.host,view.instance(),view.capture(),view.generation(),view.revision());
        f.h.assertTrue(result.equals("success")&&f.shadow()==490&&TowerObservation.remaining(f.host)==120,
            "real supported paid cast starts original 120-tick observation");
        return f.cooldownAt();
    }
    private static void readySource(F f){
        var source=BuildingAbilitySource.capture(f.peer.actor,f.host);
        f.h.assertTrue(source!=null&&source.valid()&&TowerObservation.ready(source),"same source still ready with eligible own native garrison");
    }
    private static void twoOriginals(GameTestHelper h,boolean discard){
        var f=new F(h,discard?"Api57Discard":"Api57Death",discard?31:30,true);
        var a=f.guard();var b=f.spawn(DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),f.owner,6,-4,true);
        var targets=enemies(f,1);long[] deadline={-1},castAt={-1};
        h.startSequence().thenWaitUntil(f::readyAll)
            .thenExecute(()->f.enter(a)).thenWaitUntil(()->f.seated(a)).thenExecute(()->{f.freeze(a);f.enter(b);})
            .thenWaitUntil(()->f.seated(b)).thenExecute(()->{
                f.freeze(b);f.seated(a);f.visible(targets);castAt[0]=f.level.getGameTime();deadline[0]=cast(f);
                h.assertTrue(TowerObservation.marked(targets.get(0)),"original target marked");f.clearTraffic();
            }).thenExecuteAfter(1,()->{
                f.seated(a);f.seated(b);f.visible(targets);
                h.assertTrue(TowerObservation.remaining(f.host)==119,"positive control naturally lasts one tick");
                if(discard)a.discard();else a.hurt(a.damageSources().genericKill(),100000f);
                h.assertTrue(discard?a.isRemoved():!a.isAlive(),"first original actually leaves through normal lifecycle");
                f.seated(b);readySource(f);
                h.assertTrue(TowerObservation.marked(targets.get(0))&&TowerObservation.remaining(f.host)==119,
                    "loss of one original does not revoke while another eligible original remains");
                h.assertTrue(f.shadow()==490&&f.cooldownAt()==deadline[0],"paid fee and cooldown unchanged");
            }).thenExecuteAfter(2,()->{
                f.seated(b);f.visible(targets);
                h.assertTrue(f.level.getGameTime()==castAt[0]+3&&TowerObservation.remaining(f.host)==117
                    &&TowerObservation.marked(targets.get(0)),"surviving original supports effect across normal END sweeps without refresh");
                var traffic=f.collect();f.assertPrivate(traffic);
                h.assertTrue(traffic.owner().marks().stream().noneMatch(s->s.remaining()==0),"no premature private retirement packet");
                if(discard)b.discard();else b.hurt(b.damageSources().genericKill(),100000f);
                h.assertTrue(discard?b.isRemoved():!b.isAlive(),"last original actually leaves");
                h.assertTrue(TowerObservation.remaining(f.host)==0&&!TowerObservation.marked(targets.get(0)),"last original loss cancels immediately");
            }).thenExecuteAfter(1,()->{
                var clear=lastMark(f,f.collect());
                h.assertTrue(clear.remaining()==0&&clear.targets().isEmpty()&&f.shadow()==490&&f.cooldownAt()==deadline[0],
                    "normal tick publishes private clear without refund or cooldown reset");
                System.out.println("API57_TOWER_ORIGINAL_COHORT_PASS lifecycle="+(discard?"discard":"death"));
                f.close();h.succeed();
            });
    }
    @GameTest(templateNamespace=NS,template="dreamseekers_test_arena",batch=NS,timeoutTicks=1800)
    public static void firstOriginalDeathPreservesObservationLastOriginalDeathCancels(GameTestHelper h){twoOriginals(h,false);}
    @GameTest(templateNamespace=NS,template="dreamseekers_test_arena",batch=NS,timeoutTicks=1800)
    public static void entityLeaveDropsOnlyDepartingOriginalLastDropCancels(GameTestHelper h){twoOriginals(h,true);}

    @GameTest(templateNamespace=NS,template="dreamseekers_test_arena",batch=NS,timeoutTicks=1800)
    public static void newGarrisonCannotReplaceLastOriginalOrResurrectOldObservation(GameTestHelper h){
        var f=new F(h,"Api57Replacement",32,true);var original=f.guard();
        var replacement=f.spawn(DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),f.owner,6,-4,true);
        var targets=enemies(f,1);long[] deadline={-1},castAt={-1};
        occupy(f,original).thenExecute(()->{
            f.visible(targets);h.assertTrue(GarrisonableBuildingAddon.getGarrison(replacement)==null,"replacement not in original cast cohort");
            castAt[0]=f.level.getGameTime();deadline[0]=cast(f);f.enter(replacement);
        }).thenWaitUntil(()->f.seated(replacement)).thenExecute(()->{
            f.freeze(replacement);f.seated(original);f.visible(targets);readySource(f);
            h.assertTrue(f.level.getGameTime()<castAt[0]+120&&TowerObservation.remaining(f.host)>0
                &&TowerObservation.marked(targets.get(0)),"replacement seated before natural expiry while original still supports effect");
            f.clearTraffic();original.hurt(original.damageSources().genericKill(),100000f);
            h.assertTrue(!original.isAlive(),"last original killed by real damage");f.seated(replacement);readySource(f);
            h.assertTrue(TowerObservation.remaining(f.host)==0&&!TowerObservation.marked(targets.get(0)),
                "new eligible garrison cannot replace captured original even though source is ready");
        }).thenExecuteAfter(2,()->{
            f.seated(replacement);readySource(f);var clear=lastMark(f,f.collect());
            h.assertTrue(clear.remaining()==0&&clear.targets().isEmpty()&&TowerObservation.remaining(f.host)==0
                &&!TowerObservation.marked(targets.get(0))&&f.shadow()==490&&f.cooldownAt()==deadline[0],
                "newcomer cannot resurrect old observation across ticks; charge and cooldown retained");
            System.out.println("API57_TOWER_REPLACEMENT_NO_RESURRECTION_PASS");f.close();h.succeed();
        });
    }
}
