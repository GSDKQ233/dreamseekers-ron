// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.*;
import com.dreamseekers.skill.CoordinatedMark;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.research.economy.EconomyActionGate;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.dreamseekers.BuildingObservationTestSupport.*;

/** Actual rev2 native garrison, decoded authenticated CAST and captured outgoing recipients.
 * Headless server evidence only: it cannot prove per-frame client rendering or selection persistence.
 */
@GameTestHolder("dreamseekers_building_observation") @PrefixGameTestTemplate(false)
public final class BuildingWatchObservationGameTests {
    private static final String BATCH="building_observation_watch",FOG_BATCH="building_observation_fog";
    @AfterBatch(batch=BATCH) public static void cleanupWatch(ServerLevel ignored){cleanup();}
    @AfterBatch(batch=FOG_BATCH) public static void cleanupFog(ServerLevel ignored){cleanup();}

    @GameTest(templateNamespace="dreamseekers_building_observation",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1800)
    public static void realNativeGarrisonSixStablePrivateTargetsNatural120ExpiryAnd500Cooldown(GameTestHelper h){
        var f=new F(h,"WatchExpiry",0,true);var guard=f.guard();var enemies=enemies(f,7);
        var friendly=f.unit(f.owner,8,-10);var allied=f.unit(f.other,-8,-10);AlliancesServerEvents.addAlliance(f.owner,f.other);
        var invisible=f.unit(f.enemy,0,-10);invisible(invisible);var distant=f.unit(f.enemy,0,-26);
        long[] castAt={-1},readyAt={-1};List<Mob>[] expected=new List[]{List.of()};
        h.startSequence().thenWaitUntil(f::readyAll).thenExecute(()->{
            f.visible(enemies);int shadow=f.shadow();long revision=f.account.revision;
            h.assertTrue(status(f,f.cast()).equals("requires_own_garrison")&&f.shadow()==shadow&&f.account.revision==revision&&f.cooldownAt()==0,
                "actual empty rev2 tower denies before any payment/CD");
            f.enter(guard);
            h.assertTrue(status(f,f.cast()).equals("requires_own_garrison")&&f.shadow()==shadow,
                "real submitted native approach order is not an occupied garrison");
        }).thenWaitUntil(()->f.seated(guard)).thenExecute(()->{
            f.freeze(guard);f.visible(enemies);expected[0]=distanceOrder(f,enemies).subList(0,6);
            castAt[0]=f.level.getGameTime();var d=f.cast();var s=lastMark(f,d);
            h.assertTrue(status(f,d).equals("success")&&f.shadow()==490&&f.host.getDataStorage().getData(BuildingAbilityState.TYPE).revision()==1,
                "actual decoded authenticated native-seated CAST pays exactly10S once");
            readyAt[0]=f.cooldownAt();h.assertTrue(readyAt[0]==castAt[0]+500&&s.remaining()==120,
                "product500tick CD and120tick lifetime start at genuine CAST, not fixture initialization");
            h.assertTrue(s.targets().stream().map(TowerMarkPackets.Target::uuid).toList().equals(ids(expected[0])),
                "actual outbound exactly stable distance then UUID first six out of seven eligible targets");
            h.assertTrue(!TowerObservation.marked(friendly)&&!TowerObservation.marked(allied)&&!TowerObservation.marked(invisible)&&!TowerObservation.marked(distant),
                "own/allied/invisible/out-of24sphere bodies excluded despite nearer apparent candidates");
            h.assertTrue(enemies.stream().noneMatch(Mob::isCurrentlyGlowing),"no global entity glow is written");
            h.assertTrue(status(f,f.cast()).equals("cooldown")&&f.shadow()==490&&f.cooldownAt()==readyAt[0],"new repeated request cannot repay or extend");f.clearTraffic();
        }).thenExecuteAfter(119,()->{
            h.assertTrue(f.level.getGameTime()==castAt[0]+119&&TowerObservation.remaining(f.host)==1&&expected[0].stream().allMatch(TowerObservation::marked),
                "natural119 remains on original six without manipulating gameTime");
            var d=f.collect();f.assertPrivate(d);h.assertTrue(!d.owner().marks().isEmpty(),"real owner heartbeat packets observed during actual elapsed lifetime");
            long last=-1;int remaining=121;for(var s:d.owner().marks()){
                h.assertTrue(s.order()>last&&s.remaining()<remaining&&s.targets().stream().map(TowerMarkPackets.Target::uuid).toList().equals(ids(expected[0])),
                    "actual heartbeat order rises while original remaining lifetime decreases, never refreshes/cohort-refills");last=s.order();remaining=s.remaining();}
        }).thenExecuteAfter(1,()->{
            h.assertTrue(f.level.getGameTime()==castAt[0]+120&&TowerObservation.remaining(f.host)==0&&enemies.stream().noneMatch(TowerObservation::marked),
                "exact natural120 reader sees expiry independently of subsequent END packet sweep");
            h.assertTrue(f.cooldownAt()==readyAt[0]&&f.shadow()==490,"expiry neither refunds nor resets cooldown");
        }).thenExecuteAfter(1,()->{
            var clear=lastMark(f,f.collect());h.assertTrue(clear.remaining()==0&&clear.targets().isEmpty(),"actual owner retirement packet follows natural expiry");
        }).thenExecuteAfter(378,()->{
            h.assertTrue(f.level.getGameTime()==castAt[0]+499&&BuildingAbilityService.view(f.peer.actor,f.host).cooldown()==1,
                "natural499 preserves final cooldown tick after expired effect");
            h.assertTrue(status(f,f.cast()).equals("cooldown")&&f.shadow()==490,"cannot recast at499");
        }).thenExecuteAfter(1,()->{
            h.assertTrue(f.level.getGameTime()==castAt[0]+500,"actual500 boundary reached");var d=f.cast();
            h.assertTrue(status(f,d).equals("success")&&f.shadow()==480&&lastMark(f,d).remaining()==120&&f.cooldownAt()==castAt[0]+1000,
                "new real cast at500 alone starts fresh paid120/500 windows");f.close();h.succeed();
        });
    }

    @GameTest(templateNamespace="dreamseekers_building_observation",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1300)
    public static void realRangeInvisibilityAndWallLossShrinkWithoutRefillThenActualExitClears(GameTestHelper h){
        var f=new F(h,"WatchLoss",1,true);var guard=f.guard();var enemies=enemies(f,7);List<Mob>[] selected=new List[]{List.of()};
        Vec3[] before=new Vec3[1];BlockPos[] wall={null};long[] deadline={0};
        occupy(f,guard).thenExecute(()->{
            f.visible(enemies);var d=f.cast();h.assertTrue(status(f,d).equals("success"),"real paid observation before independent losses");
            selected[0]=distanceOrder(f,enemies).subList(0,6);h.assertTrue(lastMark(f,d).targets().stream().map(TowerMarkPackets.Target::uuid).toList().equals(ids(selected[0])),"six actual original recipients");deadline[0]=f.cooldownAt();
            var range=selected[0].get(0);before[0]=range.position();range.moveTo(f.origin.getX()+.5,f.origin.getY()+1,f.origin.getZ()-26+.5);
            invisible(selected[0].get(1));
            var blocked=selected[0].get(2);Vec3 middle=TowerObservation.eye(f.host).lerp(blocked.getEyePosition(),.75);wall[0]=BlockPos.containing(middle);
            h.assertTrue(!f.host.isPosInsideBuilding(wall[0]),"real LOS obstruction is external, not corrupting host geometry");
            f.level.setBlock(wall[0],Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(!LoadedBuildingSight.clear(f.level,TowerObservation.eye(f.host),blocked.getEyePosition(),null),"actual installed solid wall blocks loaded collision ray");
            for(int i=0;i<3;i++)h.assertTrue(!TowerObservation.marked(selected[0].get(i)),"same-tick server read denies newly invalid target");
            f.clearTraffic();
        }).thenExecuteAfter(2,()->{
            var s=lastMark(f,f.collect());var expected=selected[0].subList(3,6);
            h.assertTrue(s.targets().stream().map(TowerMarkPackets.Target::uuid).toList().equals(ids(expected)),
                "actual periodic wire shrinks to remaining legal original three, never seventh replacement");
            selected[0].get(0).moveTo(before[0].x,before[0].y,before[0].z);selected[0].get(1).removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);selected[0].get(1).setInvisible(false);f.level.setBlock(wall[0],Blocks.AIR.defaultBlockState(),2);
            h.assertTrue(selected[0].subList(0,3).stream().noneMatch(TowerObservation::marked),"restoring range/light/LOS after retirement cannot resurrect original removed rows");
            var definition=f.host.getBuilding().getActiveAddon(GarrisonableBuildingAddon.class);var exit=definition.getExitPosition(f.host,guard);
            h.assertTrue(exit!=null,"native collision-safe exit exists");
            // Use the existing native garrison management service; no new observation-specific withdrawal path.
            var seatedPosition=guard.position();
            // Restore native physics/AI before checking a real nongarrison exit; no stale frozen support cache.
            guard.setNoAi(false);
            String released=com.solegendary.reignofnether.building.garrison.TowerGarrisonService.release(f.peer.actor,f.host,
                com.solegendary.reignofnether.research.economy.ResearchPlacementIdentity.serverIdentity(f.host),f.host.getCaptureRevision());
            h.assertTrue(released.equals("tower_released")&&!guard.position().equals(seatedPosition)
                &&guard.position().equals(Vec3.atCenterOf(exit)),"original management actually teleports to its exact native nongarrison exit, not outside building AABB");f.clearTraffic();
        }).thenWaitUntil(()->{
            System.out.println("BUILDING_WATCH_EGRESS tick="+f.level.getGameTime()+" position="+guard.position()+" onPos="+guard.getOnPos()+" grounded="+guard.onGround()+" garrison="+(GarrisonableBuildingAddon.getGarrison(guard)==f.host));
            h.assertTrue(guard.onGround()&&GarrisonableBuildingAddon.getGarrison(guard)!=f.host,"same original body naturally settles at native nongarrison exit");
            h.assertTrue(f.level.getGameTime()<deadline[0]-380,"egress is observed before original120tick expiry, not accepted due to timeout");
        }).thenExecuteAfter(1,()->{
            h.assertTrue(TowerObservation.remaining(f.host)==0&&enemies.stream().noneMatch(TowerObservation::marked),"native settled egress revokes all original marks before natural expiry");
            var s=lastMark(f,f.collect());h.assertTrue(s.remaining()==0&&s.targets().isEmpty()&&f.shadow()==490&&f.cooldownAt()==deadline[0],"native exit emits private clear without refund or CD reset");f.close();h.succeed();
        });
    }

    @GameTest(templateNamespace="dreamseekers_building_observation",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1300)
    public static void paidWatchSourceRemovalSendsPrivateTerminalClear(GameTestHelper h){
        var f=new F(h,"WatchRemove",2,true);var guard=f.guard();var targets=enemies(f,1);long[] ready={0};
        occupy(f,guard).thenExecute(()->{
            f.visible(targets);var d=f.cast();h.assertTrue(status(f,d).equals("success")&&lastMark(f,d).targets().size()==1,"actual paid one-target positive control");ready[0]=f.cooldownAt();
            f.host.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(f.host);
            h.assertTrue(!TowerObservation.marked(targets.get(0))&&TowerObservation.remaining(f.host)==0,"native source removal invalidates immediately");f.clearTraffic();
        }).thenExecuteAfter(2,()->{
            var clear=lastMark(f,f.collect());h.assertTrue(clear.remaining()==0&&clear.targets().isEmpty()&&f.shadow()==490&&f.cooldownAt()==ready[0],"actual original caster clear, no refund or future deadline rewrite");f.close();h.succeed();
        });
    }

    @GameTest(templateNamespace="dreamseekers_building_observation",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1300)
    public static void paidWatchOwnerAbaNeverTransfersOldObservationToNewOwner(GameTestHelper h){
        var f=new F(h,"WatchCapture",3,true);var guard=f.guard();var targets=enemies(f,1);long[] ready={0};
        occupy(f,guard).thenExecute(()->{
            f.visible(targets);var d=f.cast();h.assertTrue(status(f,d).equals("success")&&lastMark(f,d).targets().size()==1,"actual paid control");ready[0]=f.cooldownAt();
            f.host.setOwnerNameFromServerEditor(f.other);f.host.setOwnerNameFromServerEditor(f.owner);
            h.assertTrue(!TowerObservation.marked(targets.get(0))&&TowerObservation.remaining(f.host)==0,"capture A-B-A cannot revive source generation");f.clearTraffic();
        }).thenExecuteAfter(2,()->{
            var clear=lastMark(f,f.collect());h.assertTrue(clear.remaining()==0&&clear.targets().isEmpty()&&f.shadow()==490&&f.cooldownAt()==ready[0],"clear delivered only to original caster, never intermediate owner");f.close();h.succeed();
        });
    }

    @GameTest(templateNamespace="dreamseekers_building_observation",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1300)
    public static void paidWatchSessionRotationDoesNotPublishOldTargetsToNewConnection(GameTestHelper h){
        var f=new F(h,"WatchSession",4,true);var guard=f.guard();var targets=enemies(f,1);PassiveIncomePeer[] next={null};long[] ready={0};
        occupy(f,guard).thenExecute(()->{
            f.visible(targets);var token=EconomyActionGate.query(f.peer.actor,f.peer.connection);var d=f.cast();h.assertTrue(status(f,d).equals("success")&&lastMark(f,d).targets().size()==1,"actual paid old-session control");ready[0]=f.cooldownAt();
            f.peer.close();next[0]=new PassiveIncomePeer(f.level,f.owner);f.cleanups.add(next[0]::close);
            var fresh=EconomyActionGate.query(next[0].actor,next[0].connection);h.assertTrue(fresh!=null&&!fresh.session().equals(token.session()),"real new connection rotates economy session");
            h.assertTrue(TowerObservation.remaining(f.host)==0&&!TowerObservation.marked(targets.get(0)),"old connected-peer lease cannot survive replacement connection");
            next[0].drain();f.enemyPeer.drain();f.otherPeer.drain();
        }).thenExecuteAfter(6,()->{
            h.assertTrue(next[0].drain().stream().noneMatch(p->p instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket c&&c.getIdentifier().equals(DreamseekersMod.id("tower_observations"))),
                "captured new recipient receives no old-session target snapshot or clear");
            var rest=f.collect();f.assertPrivate(rest);h.assertTrue(f.shadow()==490&&f.cooldownAt()==ready[0],"disconnect neither refunds original debit nor clears paid cooldown");f.close();h.succeed();
        });
    }


    @GameTest(templateNamespace="dreamseekers_building_observation",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1600)
    public static void actualAlliedGarrisonIsNotOwnGarrisonAndCannotAuthorizePayment(GameTestHelper h){
        var f=new F(h,"WatchAlly",6,true);AlliancesServerEvents.addAlliance(f.owner,f.other);
        var ally=f.spawn(DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(),f.other,3,-4,true);var own=f.guard();
        own.moveTo(f.origin.getX()-8+.5,f.origin.getY()+1,f.origin.getZ()-4+.5);own.setNoAi(true);var targets=enemies(f,1);
        h.startSequence().thenWaitUntil(f::readyAll).thenExecute(()->
            com.solegendary.reignofnether.unit.UnitServerEvents.addActionItem(f.other,com.solegendary.reignofnether.unit.UnitAction.GARRISON,-1,new int[]{ally.getId()},f.host.originPos,BlockPos.ZERO))
          .thenWaitUntil(()->f.seated(ally)).thenExecute(()->{
            f.freeze(ally);f.visible(targets);h.assertTrue(status(f,f.cast()).equals("requires_own_garrison")&&f.shadow()==500&&f.cooldownAt()==0,
                "actual allied seated body is visible/native-garrisoned but never supplies owner's prerequisite or fee");
            own.setNoAi(false);f.enter(own);
          }).thenWaitUntil(()->f.seated(own)).thenExecute(()->{
            f.freeze(own);var d=f.cast();h.assertTrue(status(f,d).equals("success")&&lastMark(f,d).targets().size()==1&&f.shadow()==490,
                "same tower/target succeeds only after actual own native garrison arrives");f.close();h.succeed();
          });
    }


    @GameTest(templateNamespace="dreamseekers_building_observation",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1300)
    public static void actualOnlyWallInvisibleAndOutOfRadiusCandidatesNeverAuthorizePayment(GameTestHelper h){
        var f=new F(h,"WatchReject",7,true);var guard=f.guard();var wallTarget=f.unit(f.enemy,0,-16);
        var invisible=f.unit(f.enemy,4,-16);invisible(invisible);var distant=f.unit(f.enemy,0,-26);
        occupy(f,guard).thenExecute(()->{
            f.visible(List.of(wallTarget));var wall=BlockPos.containing(TowerObservation.eye(f.host).lerp(wallTarget.getEyePosition(),.75));
            h.assertTrue(!f.host.isPosInsideBuilding(wall),"solid obstruction is external to unchanged real rev2 template");f.level.setBlock(wall,Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(!LoadedBuildingSight.clear(f.level,TowerObservation.eye(f.host),wallTarget.getEyePosition(),null),"real loaded solid collision blocks only otherwise valid body");
            var d=f.cast();h.assertTrue(status(f,d).equals("no_target")&&d.owner().marks().isEmpty()&&f.shadow()==500&&f.account.revision==0&&f.cooldownAt()==0,
                "real authenticated CAST with only wall/invisible/out-of24sphere enemies cannot pay or publish target payload");
            f.level.setBlock(wall,Blocks.AIR.defaultBlockState(),2);f.visible(List.of(wallTarget));var valid=f.cast();var s=lastMark(f,valid);
            h.assertTrue(status(f,valid).equals("success")&&f.shadow()==490&&s.targets().stream().map(TowerMarkPackets.Target::uuid).toList().equals(List.of(wallTarget.getUUID()))
                &&!TowerObservation.marked(invisible)&&!TowerObservation.marked(distant),"same real source/session succeeds only after existing visible target gains real clear LOS");f.close();h.succeed();
        });
    }

    @GameTest(templateNamespace="dreamseekers_building_observation",template="dreamseekers_test_arena",batch=FOG_BATCH,timeoutTicks=1300)
    public static void realNativeFogDeniesThenVisibleCastAndFogLossNeverRevealOrRefill(GameTestHelper h){
        var f=new F(h,"WatchFog",5,true);var guard=f.guard();var targets=enemies(f,2);Fog[] fog={null};String[] masks={null};long[] ready={0};
        occupy(f,guard).thenExecute(()->{
            f.visible(targets);fog[0]=new Fog(f);fog[0].install();f.clearTraffic();masks[0]=fog[0].ownerMasks();
            h.assertTrue(targets.stream().noneMatch(e->CoordinatedMark.visible(f.peer.actor,e)),"real current native column masks hide both live enemies");
            var hidden=f.cast();h.assertTrue(status(f,hidden).equals("no_target")&&hidden.owner().marks().isEmpty()&&f.shadow()==500&&f.cooldownAt()==0,
                "actual authenticated hidden cast never charges or sends target IDs");
            h.assertTrue(hidden.owner().fogPackets()==0&&hidden.enemy().fogPackets()==0&&hidden.other().fogPackets()==0&&masks[0].equals(fog[0].ownerMasks()),
                "hidden cast neither mutates real fog caches nor emits reveal/fog/neutral-position packets");
            fog[0].columns(List.of(targets.get(0).blockPosition()));masks[0]=fog[0].ownerMasks();f.clearTraffic();
            var visible=f.cast();var s=lastMark(f,visible);h.assertTrue(status(f,visible).equals("success")&&f.shadow()==490&&s.targets().stream().map(TowerMarkPackets.Target::uuid).toList().equals(List.of(targets.get(0).getUUID())),
                "same actual bodies/session: exactly the already-bright legal target is privately published");
            h.assertTrue(visible.owner().fogPackets()==0&&visible.enemy().fogPackets()==0&&visible.other().fogPackets()==0&&masks[0].equals(fog[0].ownerMasks()),"successful observation creates no vision or coordinate reveal");
            ready[0]=f.cooldownAt();fog[0].columns(List.of());masks[0]=fog[0].ownerMasks();
            h.assertTrue(!TowerObservation.marked(targets.get(0)),"same-tick native fog loss makes marked read false");f.clearTraffic();
        }).thenExecuteAfter(2,()->{
            var d=f.collect();var s=lastMark(f,d);h.assertTrue(s.remaining()==0&&s.targets().isEmpty()&&d.owner().fogPackets()==0&&masks[0].equals(fog[0].ownerMasks()),"real next-END private clear without fog mutation/reveal");
            fog[0].columns(targets.stream().map(Mob::blockPosition).toList());
            h.assertTrue(targets.stream().noneMatch(TowerObservation::marked)&&f.shadow()==490&&f.cooldownAt()==ready[0],"restored bright visibility cannot re-add retired targets or refund original cast");f.close();h.succeed();
        });
    }
}
