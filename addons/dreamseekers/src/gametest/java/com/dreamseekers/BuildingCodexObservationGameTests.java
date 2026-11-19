// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.*;
import com.dreamseekers.ecologyworkposts.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.dreamseekers.BuildingObservationTestSupport.*;

/** Actual loaded roster scans through authenticated server handler and captured owner connection.
 * Real W-committed contract care/free0 stays in BuildingContractCareGameTests; no fake-tag positive here.
 * Client focus/selection and the60tick presentation TTL require rendered-client acceptance separately.
 */
@GameTestHolder("dreamseekers_building_observation") @PrefixGameTestTemplate(false)
public final class BuildingCodexObservationGameTests {
    private static final String BATCH="building_observation_codex";
    @AfterBatch(batch=BATCH) public static void cleanupCodex(ServerLevel ignored){cleanup();}
    private static List<Mob> roster(F f,int count){var list=new ArrayList<Mob>();for(int i=0;i<count;i++){
        int x=-38+(i%17)*2,z=-10-(i/17)*2;
        list.add(i%2==0?f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),f.owner,x,z,false):f.spawn(DreamseekersMod.BEETLE.get(),f.owner,x,z,false));
    }return list;}
    private static void page(F f,BuildingAbilityPackets.Snapshot snapshot,List<Mob> ordered,int page,int total,boolean truncated){
        var got=snapshot.codex();int start=page*32,end=Math.min(total,start+32);
        List<UUID> expected=start>=total?List.of():ids(ordered.subList(start,end));
        f.h.assertTrue(snapshot.ability()==BuildingAbility.ECOLOGY_CODEX&&snapshot.cooldown()==0&&snapshot.activeTicks()==0,
            "real informational view has no cooldown or active paid lease");
        f.h.assertTrue(got.valid()&&got.page()==page&&got.totalRows()==total&&got.truncated()==truncated&&got.rows().stream().map(BuildingCodex.Row::uuid).toList().equals(expected),
            "actual sorted roster page "+page+" total="+total+" truncated="+truncated+" has exact original body membership");
        for(var row:got.rows()){
            var body=ordered.stream().filter(e->e.getUUID().equals(row.uuid())).findFirst().orElseThrow();
            f.h.assertTrue(row.id()==body.getId()&&row.hp()==body.getHealth()&&row.maxHp()==body.getMaxHealth(),"actual outbound row uses current exact original id/UUID and injury values");
        }
    }
    private static void free(F f){f.h.assertTrue(f.account.food==5000&&f.account.wood==5000&&f.account.ore==5000&&f.shadow()==500&&f.account.revision==0
        &&f.host.getDataStorage().getData(BuildingAbilityState.TYPE).revision()==0&&BuildingAbilityService.view(f.peer.actor,f.host).cooldown()==0,
        "informational queries/pagination/original care commands spend no money, cast revision, or cooldown");}

    @GameTest(templateNamespace="dreamseekers_building_observation",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=400)
    public static void actualThirtyTwoRowsEmptyNextLastPageAndOutOfRangeQueriesStayPrivateFree(GameTestHelper h){
        var f=new F(h,"Codex32",10,false);var bodies=roster(f,32);List<Mob>[] ordered=new List[]{List.of()};
        h.startSequence().thenWaitUntil(f::readyAll).thenExecute(()->{
            ordered[0]=codexOrder(bodies);page(f,view(f,f.query(0)),ordered[0],0,32,false);free(f);
            // Same authentic foreign connection with the exact valid owner source payload must get no roster.
            var copied=f.request(BuildingAbilityPackets.Action.CODEX_QUERY,0,0);f.clearTraffic();transmit(h,f.otherPeer,copied);var foreign=f.collect();f.assertPrivate(foreign);
            h.assertTrue(foreign.owner().views().isEmpty()&&foreign.other().acks().isEmpty(),"authenticated nonowner copied request cannot query any private page");
            for(int invalid:List.of(-1,8,Integer.MAX_VALUE)){
                var d=f.exchange(f.request(BuildingAbilityPackets.Action.CODEX_QUERY,invalid,0));f.assertPrivate(d);
                h.assertTrue(d.owner().views().isEmpty()&&d.owner().acks().isEmpty(),"bounded authenticated invalid page produces no roster or action completion");
            }
        }).thenExecuteAfter(20,()->{page(f,view(f,f.query(1)),ordered[0],1,32,false);free(f);})
          .thenExecuteAfter(20,()->{page(f,view(f,f.query(7)),ordered[0],7,32,false);free(f);f.close();h.succeed();});
    }

    @GameTest(templateNamespace="dreamseekers_building_observation",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=700)
    public static void actualThirtyThreeMixedTypesStablePagesInjuryAndLifecycleFiltering(GameTestHelper h){
        var f=new F(h,"Codex33",11,false);var bodies=roster(f,33);bodies.get(0).setHealth(bodies.get(0).getMaxHealth()-3);
        var foreign=f.unit(f.enemy,4,-12);var hidden=f.unit(f.owner,8,-12);invisible(hidden);
        var otherLevel=Objects.requireNonNull(f.level.getServer().getLevel(Level.NETHER));BlockPos away=f.origin.offset(0,1,-20);f.load(otherLevel,away,16);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){otherLevel.setBlock(away.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)otherLevel.setBlock(away.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        var wrongDimension=f.spawnAt(EntityRegistrar.VILLAGER_UNIT.get(),f.owner,otherLevel,away,false);
        List<Mob>[] ordered=new List[]{List.of()};List<UUID>[] first=new List[]{List.of()};
        h.startSequence().thenWaitUntil(f::readyAll).thenExecute(()->{
            ordered[0]=codexOrder(bodies);var snapshot=view(f,f.query(0));page(f,snapshot,ordered[0],0,33,false);
            first[0]=snapshot.codex().rows().stream().map(BuildingCodex.Row::uuid).toList();
            h.assertTrue(snapshot.codex().rows().stream().noneMatch(r->r.uuid().equals(foreign.getUUID())||r.uuid().equals(hidden.getUUID())||r.uuid().equals(wrongDimension.getUUID())),
                "actual foreign/invisible/other-dimension published original bodies are excluded, not merely unavailable client rows");
        }).thenExecuteAfter(20,()->page(f,view(f,f.query(1)),ordered[0],1,33,false))
          .thenExecuteAfter(20,()->{
              var repeat=view(f,f.query(0));page(f,repeat,ordered[0],0,33,false);h.assertTrue(repeat.codex().rows().stream().map(BuildingCodex.Row::uuid).toList().equals(first[0]),"repeat actual scans retain type then UUID stable order independent of entity ID/insertion");
              ((Unit)bodies.get(1)).setOwnerName(f.enemy);bodies.get(2).discard();
          }).thenExecuteAfter(20,()->{
              var current=bodies.stream().filter(e->e!=bodies.get(1)&&e!=bodies.get(2)).toList();var snapshot=view(f,f.query(0));page(f,snapshot,codexOrder(current),0,31,false);
              h.assertTrue(snapshot.codex().rows().stream().noneMatch(r->r.uuid().equals(bodies.get(1).getUUID())||r.uuid().equals(bodies.get(2).getUUID())),"fresh actual scan removes transferred and disappeared bodies without exposing enemy coordinates");free(f);f.close();h.succeed();
          });
    }

    @GameTest(templateNamespace="dreamseekers_building_observation",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=800)
    public static void actual257LoadedBodiesProduceEightStable32PagesCapped256ThenExact256Untruncated(GameTestHelper h){
        var f=new F(h,"Codex257",12,false);var bodies=roster(f,257);List<Mob>[] ordered=new List[]{List.of()};var delivered=new ArrayList<UUID>();
        var sequence=h.startSequence().thenWaitUntil(f::readyAll).thenExecute(()->{
            ordered[0]=codexOrder(bodies);var s=view(f,f.query(0));page(f,s,ordered[0],0,256,true);delivered.addAll(s.codex().rows().stream().map(BuildingCodex.Row::uuid).toList());free(f);
        });
        for(int i=1;i<8;i++){final int p=i;sequence.thenExecuteAfter(20,()->{
            var s=view(f,f.query(p));page(f,s,ordered[0],p,256,true);delivered.addAll(s.codex().rows().stream().map(BuildingCodex.Row::uuid).toList());free(f);
        });}
        sequence.thenExecute(()->{
            h.assertTrue(delivered.size()==256&&new HashSet<>(delivered).size()==256&&delivered.equals(ids(ordered[0].subList(0,256))),
                "all eight actual responses form exactly stable sorted first256, no duplicates/skips/seventh-page spill");
            h.assertTrue(!delivered.contains(ordered[0].get(256).getUUID()),"genuine257th eligible live body is omitted only by authoritative cap");
            ordered[0].get(256).discard();
        }).thenExecuteAfter(20,()->{
            var s=view(f,f.query(0));page(f,s,ordered[0].subList(0,256),0,256,false);free(f);
        }).thenExecuteAfter(20,()->{
            var s=view(f,f.query(7));page(f,s,ordered[0].subList(0,256),7,256,false);free(f);f.close();h.succeed();
        });
    }

    @GameTest(templateNamespace="dreamseekers_building_observation",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1300)
    public static void actualCodexInjuredRowReusesOriginalAuthenticatedCareAssignAndWithdrawal(GameTestHelper h){
        var f=new F(h,"CodexCare",13,false);var body=f.spawn(DreamseekersMod.BEETLE.get(),f.owner,3,-8,false);body.setHealth(body.getMaxHealth()-4);
        BuildingCodex.Row[] row={null};UUID[] lease={null};float[] injury={0};
        h.startSequence().thenWaitUntil(f::readyAll).thenExecute(()->{
            var s=view(f,f.query(0));h.assertTrue(s.codex().rows().size()==1,"actual one-body owner roster");row[0]=s.codex().rows().get(0);injury[0]=body.getHealth();
            h.assertTrue(row[0].id()==body.getId()&&row[0].uuid().equals(body.getUUID())&&row[0].hp()==injury[0]&&row[0].careState()==0&&row[0].careEligible(),
                "live injured unassigned original permanent body offers existing care operation");
        }).thenExecuteAfter(20,()->{
            body.setNoAi(false);var d=f.ecology(EcologyPackets.Action.ASSIGN,List.of(new EcologyPackets.Target(row[0].id(),row[0].uuid())),EcologyPackets.ZERO);f.assertPrivate(d);
            h.assertTrue(d.owner().care().size()==1&&d.owner().care().get(0).status().equals("assigned"),"real original EcologyPackets authenticated handler accepts exact codex row identity");
            var occupants=d.owner().care().get(0).occupants();h.assertTrue(occupants.size()==1&&!occupants.get(0).arrived(),"native reservation remains approaching, not an invented teleport");lease[0]=occupants.get(0).lease();
            var s=view(f,f.query(0));h.assertTrue(s.codex().rows().get(0).careState()==1,"actual Codex response observes original care approaching state");free(f);
        }).thenWaitUntil(()->h.assertTrue(EcologyWorkPosts.snapshot(f.host).stream().anyMatch(e->e.uuid().equals(body.getUUID())&&e.arrived()),"same original body naturally walks to the existing care post"))
          .thenExecuteAfter(20,()->{
              var s=view(f,f.query(0));var live=s.codex().rows().get(0);
              h.assertTrue(live.uuid().equals(body.getUUID())&&live.careState()==2&&live.careEligible()&&live.hp()==body.getHealth()&&body.getHealth()==injury[0],
                  "real arrived state/current injury exposed without bypassing original research healing gate");
              var before=body.position();var d=f.ecology(EcologyPackets.Action.RELEASE,List.of(),lease[0]);f.assertPrivate(d);
              h.assertTrue(d.owner().care().size()==1&&d.owner().care().get(0).status().equals("released")&&d.owner().care().get(0).occupants().isEmpty()
                  &&EcologyWorkPosts.snapshot(f.host).isEmpty()&&body.position().equals(before),"existing authenticated withdrawal clears original lease without teleport or new action API");free(f);
          }).thenExecuteAfter(20,()->{
              var s=view(f,f.query(0));h.assertTrue(s.codex().rows().get(0).careState()==0&&s.codex().rows().get(0).uuid().equals(body.getUUID()),"fresh actual row returns to unassigned without changing identity");free(f);f.close();h.succeed();
          });
    }
}
