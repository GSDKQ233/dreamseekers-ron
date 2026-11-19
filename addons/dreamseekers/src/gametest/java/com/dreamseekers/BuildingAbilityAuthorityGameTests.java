// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.*;
import com.dreamseekers.function.DreamFunctionState;
import com.dreamseekers.function.DreamFunctionalPlacement;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.economy.EconomyActionGate;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Genuine source objects, connected peers, decoded requests and atomic payment, not direct counters. */
@GameTestHolder("dreamseekers_building_abilities") @PrefixGameTestTemplate(false)
public final class BuildingAbilityAuthorityGameTests {
    private static final String BATCH="building_ability_authority";
    private static final List<NeutralContractGameTests.F> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();}
    private static NeutralContractGameTests.F fixture(GameTestHelper h,String suffix){var f=new NeutralContractGameTests.F(h,"API53_Ability_"+suffix);FIXTURES.add(f);return f;}
    private static String cast(NeutralContractGameTests.F f,BuildingPlacement host,BuildingAbilityService.View view){return BuildingAbilityService.cast(f.peer.actor,host,view.instance(),view.capture(),view.generation(),view.revision());}
    private static VillagerUnit recipient(NeutralContractGameTests.F f,BuildingPlacement host){
        BlockPos at=host.originPos.offset(3,1,-3);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            f.level.setBlock(at.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<5;y++)f.level.setBlock(at.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);
        }
        var unit=EntityRegistrar.VILLAGER_UNIT.get().create(f.level);unit.setOwnerName(f.owner);unit.setNoAi(true);
        unit.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);f.h.assertTrue(f.level.addFreshEntity(unit),"real exact own recipient published");f.bodies.add(unit);return unit;
    }
    private static void packet(PassiveIncomePeer peer,BuildingAbilityPackets.Request original){
        var bytes=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{
            original.encode(bytes);var decoded=new BuildingAbilityPackets.Request(bytes);
            if(!decoded.equals(original)||bytes.readableBytes()!=0)throw new AssertionError("exact building ability request wire roundtrip");
            var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);
            ctor.setAccessible(true);var context=ctor.newInstance(peer.connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);decoded.handle(()->context);
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}finally{bytes.release();}
        peer.channel.runPendingTasks();
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void exactReplacementCannotReusePersistentIdentityAndOwnerAbaRevokesSource(GameTestHelper h){
        var f=fixture(h,"identity");BuildingPlacement[] replacement={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.court.isBuilt,"real completed original source"))
          .thenExecute(()->{
              var old=BuildingAbilityService.view(f.peer.actor,f.court);var originalSource=BuildingAbilitySource.capture(f.peer.actor,f.court);
              h.assertTrue(old!=null&&old.supported()&&originalSource!=null&&originalSource.valid(),"exact live original authority established");
              var savedAbility=f.court.getDataStorage().getData(BuildingAbilityState.TYPE).write();var savedFunction=f.court.getDataStorage().getData(DreamFunctionalPlacement.STATE).write();
              f.court.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(f.court);
              var next=BuildingUtils.getNewBuildingPlacement(DreamseekersMod.CONTRACT_ECOLOGY,f.level,f.court.originPos,Rotation.NONE,f.owner,false);
              BuildingServerEvents.getBuildings().add(next);f.extraBuildings.add(next);next.onBuilt();
              next.getDataStorage().setData(BuildingAbilityState.TYPE,BuildingAbilityState.read(savedAbility));
              next.getDataStorage().setData(DreamFunctionalPlacement.STATE,DreamFunctionState.read(savedFunction));replacement[0]=next;
              var fresh=BuildingAbilityService.view(f.peer.actor,next);
              h.assertTrue(next!=f.court&&fresh!=null&&fresh.supported()&&fresh.instance().equals(old.instance())&&fresh.revision()==old.revision()&&fresh.capture()==old.capture(),
                  "real replacement deliberately retains same origin/persisted instance/capture/revision");
              h.assertTrue(fresh.generation()!=old.generation()&&cast(f,next,old).equals("stale_building"),"server-session generation distinguishes replacement object and rejects old view");
              h.assertTrue(!originalSource.valid(),"original source cannot become replacement lifetime");
              var captured=BuildingAbilitySource.capture(f.peer.actor,next);h.assertTrue(captured!=null&&captured.valid(),"fresh source independently authorized");
              next.setOwnerNameFromServerEditor(f.owner+"_other");next.setOwnerNameFromServerEditor(f.owner);
              h.assertTrue(!captured.valid()&&cast(f,next,fresh).equals("stale_building"),"supported same-tick owner A-B-A cannot reuse source or view");
              h.assertTrue(f.account.food==1000&&f.account.revision==0,"information source and invalid requests have no economic mutations");f.close();h.succeed();
          });
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void unknownStateMissingTargetsAndImpersonatingOfflineActorNeverCharge(GameTestHelper h){
        var f=fixture(h,"denials");var garden=f.install(DreamseekersMod.REVERIE_GARDEN,40,0);
        h.startSequence().thenWaitUntil(()->h.assertTrue(garden.isBuilt,"real garden completed"))
          .thenExecute(()->{
              var view=BuildingAbilityService.view(f.peer.actor,garden);int food=f.account.food;long revision=f.account.revision;
              h.assertTrue(view.supported()&&!view.available()&&view.reason().equals("no_target")&&cast(f,garden,view).equals("no_target"),"real empty target preflight before debit");
              h.assertTrue(f.account.food==food&&f.account.revision==revision&&garden.getDataStorage().getData(BuildingAbilityState.TYPE).revision()==0,"no target does not spend price/CD/cast revision");
              var spoof=FakePlayerFactory.get(f.level,new GameProfile(UUID.randomUUID(),f.owner));
              h.assertTrue(!BuildingAbilitySource.owns(spoof,garden)&&BuildingAbilityService.cast(spoof,garden,view.instance(),view.capture(),view.generation(),view.revision()).equals("forbidden"),
                  "same owner string without exact currently registered online actor is not authority");
              var original=garden.getDataStorage().getData(BuildingAbilityState.TYPE);var future=original.write();future.putInt("schema",999);future.putString("future","do not downgrade");
              garden.getDataStorage().setData(BuildingAbilityState.TYPE,BuildingAbilityState.read(future));
              h.assertTrue(!BuildingAbilityService.view(f.peer.actor,garden).supported()&&cast(f,garden,view).equals("forbidden"),"future ability payload has no action source");
              h.assertTrue(garden.getDataStorage().getData(BuildingAbilityState.TYPE).write().equals(future)&&f.account.food==food&&f.account.revision==revision,"unknown payload untouched and no resource mutation");
              garden.getDataStorage().setData(BuildingAbilityState.TYPE,original);
              h.assertTrue(cast(f,garden,view).equals("stale_building"),"restoring prior raw object after observed unknown interval cannot revive old generation");f.close();h.succeed();
          });
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=500)
    public static void realPaidDecodedPacketReplaysOnceAndReloadedStateKeepsCooldownWithoutEffect(GameTestHelper h){
        var f=fixture(h,"packet");var garden=f.install(DreamseekersMod.REVERIE_GARDEN,40,0);var unit=recipient(f,garden);unit.setHealth(unit.getMaxHealth()-8);
        float[] hp={0};long[] ready={0};BuildingPlacement[] reloaded={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(garden.isBuilt&&unit.tickCount>205&&UnitServerEvents.getAllUnits().contains(unit)
              &&f.level.getEntity(unit.getUUID())==unit&&f.level.getEntity(unit.getId())==unit,"actual publication and natural200tick no-combat window"))
          .thenExecute(()->{
              var view=BuildingAbilityService.view(f.peer.actor,garden);h.assertTrue(view.available(),"real injured resting target has ready action");
              var token=EconomyActionGate.query(f.peer.actor,f.peer.connection);h.assertTrue(token!=null,"actual connected shared economy session");
              var payload=new BuildingAbilityPackets.Payload(BuildingAbilityPackets.Action.CAST,f.level.dimension().location(),garden.originPos,UUID.randomUUID(),view.instance(),view.capture(),view.generation(),view.revision(),0);
              var request=new BuildingAbilityPackets.Request(payload,token.epoch(),token.session(),1);int food=f.account.food;hp[0]=unit.getHealth();
              packet(f.peer,request);var state=garden.getDataStorage().getData(BuildingAbilityState.TYPE);ready[0]=state.readyAt(BuildingAbility.GARDEN_RECOVERY);
              h.assertTrue(f.account.food==food-30&&state.revision()==1&&ready[0]==f.level.getGameTime()+600&&BuildingAbilityEffects.remaining(garden)>0,
                  "actual decoded authenticated action pays exactly30F and commits600tick CD before live effect");
              long accountRevision=f.account.revision;packet(f.peer,request);
              var changed=new BuildingAbilityPackets.Payload(payload.action(),payload.dimension(),payload.origin(),UUID.randomUUID(),payload.instance(),payload.capture(),payload.generation(),payload.revision(),0);
              packet(f.peer,new BuildingAbilityPackets.Request(changed,token.epoch(),token.session(),1));packet(f.peer,new BuildingAbilityPackets.Request(payload,UUID.randomUUID(),token.session(),2));
              h.assertTrue(f.account.food==food-30&&f.account.revision==accountRevision&&state.revision()==1&&state.readyAt(BuildingAbility.GARDEN_RECOVERY)==ready[0],
                  "duplicate/changed same sequence/wrong epoch cannot pay or refresh effect/CD again");
          }).thenExecuteAfter(25,()->{
              h.assertTrue(unit.getHealth()==hp[0]+1,"the paid plan reached actual first capped healing pulse");hp[0]=unit.getHealth();
              var serialized=garden.getDataStorage().write();var saved=DataStorage.read(serialized,f.level.getServer()).getData(BuildingAbilityState.TYPE);
              garden.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(garden);
              var next=BuildingUtils.getNewBuildingPlacement(DreamseekersMod.REVERIE_GARDEN,f.level,garden.originPos,Rotation.NONE,f.owner,false);
              BuildingServerEvents.getBuildings().add(next);f.extraBuildings.add(next);next.onBuilt();next.getDataStorage().setData(BuildingAbilityState.TYPE,saved);reloaded[0]=next;
              var current=BuildingAbilityService.view(f.peer.actor,next);
              h.assertTrue(current.supported()&&current.cooldown()>0&&cast(f,next,current).equals("cooldown")&&saved.readyAt(BuildingAbility.GARDEN_RECOVERY)==ready[0],
                  "actual registered storage codec preserves already-paid cooldown across replacement, not a refund or fresh cast");
              h.assertTrue(BuildingAbilityEffects.remaining(next)==0,"new host receives no reconstructed transient paid effect");
          }).thenExecuteAfter(80,()->{
              h.assertTrue(unit.getHealth()==hp[0]&&reloaded[0].getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.GARDEN_RECOVERY)==ready[0],
                  "old detached source cannot heal or erase persisted CD; this is codec/replacement coverage, not a JVM restart claim");f.close();h.succeed();
          });
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void gardenRemovalIsTerminalEvenIfSameObjectReinsertedBeforeAnyRead(GameTestHelper h){
        var f=fixture(h,"garden_detach");var garden=f.install(DreamseekersMod.REVERIE_GARDEN,40,0);
        h.startSequence().thenWaitUntil(()->h.assertTrue(garden.isBuilt,"real garden completed"))
          .thenExecute(()->{
              var source=BuildingAbilitySource.capture(f.peer.actor,garden);var old=BuildingAbilityService.view(f.peer.actor,garden);
              h.assertTrue(source!=null&&source.valid()&&old.supported(),"actual source captured before lifecycle removal");
              var saved=garden.getDataStorage().getData(BuildingAbilityState.TYPE).write();int food=f.account.food;long accountRevision=f.account.revision;
              garden.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(garden);
              // No Source.valid/physical/capture is called in the gap: removal itself must revoke forever.
              BuildingServerEvents.getBuildings().add(garden);
              h.assertTrue(!BuildingAbilitySource.physical(garden)&&!source.valid()&&BuildingAbilitySource.capture(f.peer.actor,garden)==null,
                  "terminal same physical object cannot revive after same-task remove/reinsert");
              h.assertTrue(cast(f,garden,old).equals("forbidden")&&f.account.food==food&&f.account.revision==accountRevision
                  &&garden.getDataStorage().getData(BuildingAbilityState.TYPE).write().equals(saved),
                  "stale source cannot charge/refund/alter cooldown or persisted state after detach");f.close();h.succeed();
          });
    }

}
