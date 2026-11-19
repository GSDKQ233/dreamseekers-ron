// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.solegendary.reignofnether.building.production.ProductionItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

/** Real paid conversion and subsequent native care lifecycle; no synthetic healing or arrival. */
@GameTestHolder("dreamseekers_ecology_advanced")
@PrefixGameTestTemplate(false)
public final class EcologyAdvancedUnitGameTests {
    // Uses the single support_upgrade cleanup registered by BuildingSupportLifecycleGameTests.
    @GameTest(templateNamespace="dreamseekers_ecology_advanced",template="dreamseekers_test_arena",batch="support_upgrade",timeoutTicks=1000)
    public static void paidUpgradeNaturallyArrivesHealsAndStops(GameTestHelper h){
        var f=new BuildingSupportLifecycleGameTests.F(h,"Api59CareUpgrade",0);
        var origin=f.origin.offset(64,0,0);f.load(origin);
        for(int x=-25;x<=40;x++)for(int z=-25;z<=40;z++){f.level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)f.level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        var court=f.install(DreamseekersMod.CONTRACT_ECOLOGY,origin);
        var source=DreamseekersMod.TERRORBEAK_UNIT.get().create(f.level);source.setOwnerName(f.owner);source.setNoAi(false);source.setHoldPosition(true);
        var at=origin.offset(6,1,-8);source.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);h.assertTrue(f.level.addFreshEntity(source),"actual base Terrorbeak body published");f.units.add(source);
        final com.dreamseekers.unit.CrazyTerrorbeakUnit[] upgraded={null};
        final float[] health={0}; final long[] arrival={0};
        h.startSequence().thenWaitUntil(()->{f.ready(source);h.assertTrue(source.onGround(),"base unit naturally grounded before care/upgrade");})
        .thenExecute(()->{
            h.assertTrue(com.dreamseekers.ecologyworkposts.EcologyWorkPosts.live(court)&&com.dreamseekers.ecologyworkposts.EcologyWorkPosts.eligible(source,court),"positive control: finished own court admits ordinary permanent grounded base form");
            System.out.println("API59_REPAIR_CARE_BASE_READY onGround="+source.onGround()+" noAi="+source.isNoAi()+" position="+source.position());
            var identity=com.solegendary.reignofnether.research.economy.ResearchPlacementIdentity.serverIdentity(court);
            String base=com.dreamseekers.ecologyworkposts.EcologyWorkPosts.assign(f.peer.actor,court,identity,court.getCaptureRevision(),java.util.List.of(new com.dreamseekers.ecologyworkposts.EcologyPackets.Target(source.getId(),source.getUUID())));
            h.assertTrue(base.equals("assigned"),"actual legal base care assignment finds a safe post");
            h.assertTrue(com.dreamseekers.ecologyworkposts.EcologyWorkPosts.release(f.peer.actor,court,identity,court.getCaptureRevision(),null).equals("released")&&com.dreamseekers.ecologyworkposts.EcologyWorkPosts.snapshot(court).isEmpty(),"ordinary release frees court before actual paid transformation");
            int food=f.account.food,ore=f.account.ore,shadow=f.shadow();
            var request=new com.dreamseekers.shadow.TerrorbeakUpgradeRequest(1,java.util.UUID.randomUUID(),new com.dreamseekers.shadow.TerrorbeakUpgradeRequest.Source(source.getId(),source.getUUID(),source.rts().ownershipRevision,f.level.dimension().location()),com.dreamseekers.shadow.TerrorbeakUpgradeRequest.UpgradeTarget.CRAZY_TERRORBEAK);
            var result=com.dreamseekers.shadow.TerrorbeakUpgradeService.request(f.peer.actor,request);
            h.assertTrue(result==com.dreamseekers.shadow.TerrorbeakUpgradeService.Result.SUCCESS,"real supported paid upgrade commits: "+result);
            var bodies=com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits().stream().filter(e->e instanceof com.dreamseekers.unit.CrazyTerrorbeakUnit u&&u.getOwnerName().equals(f.owner)).toList();
            h.assertTrue(source.isRemoved()&&bodies.size()==1&&f.account.food==food-80&&f.account.ore==ore-60&&f.shadow()==shadow-30,"exactly one committed replacement after genuine80F60O30S debit");
            upgraded[0]=(com.dreamseekers.unit.CrazyTerrorbeakUnit)bodies.get(0);f.units.add(upgraded[0]);
        }).thenWaitUntil(()->{h.assertTrue(upgraded[0]!=null,"replacement exists");f.ready(upgraded[0]);})
        .thenExecute(()->{
            var body=upgraded[0];var support=com.dreamseekers.buildingability.BuildingAbilitySource.capture(f.peer.actor,f.root);
            h.assertTrue(support!=null&&com.dreamseekers.buildingability.BuildingAbilityEffects.permanentOwnUnit(support,body),"current building-support production policy recognizes actual upgraded body as permanent owned unit");
            h.assertTrue(!body.isFlyingUnit()&&!body.isPassenger()&&!body.isVehicle()&&body.getMoveGoal()!=null&&!com.dreamseekers.shadow.TerrorbeakUpgradeService.pending(body),"ordinary grounded committed mobile body, not passenger/vehicle/pending conversion");
            h.assertTrue(com.dreamseekers.ecologyworkposts.EcologyWorkPosts.snapshot(court).isEmpty(),"care capacity remains entirely free");
            Object post;try{var method=com.dreamseekers.ecologyworkposts.EcologyWorkPosts.class.getDeclaredMethod("freePost",net.minecraft.world.entity.Mob.class,com.solegendary.reignofnether.building.BuildingPlacement.class);method.setAccessible(true);post=method.invoke(null,body,court);}catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
            h.assertTrue(post!=null,"actual production geometry resolver finds safe post for actual upgraded body");
            boolean eligible=com.dreamseekers.ecologyworkposts.EcologyWorkPosts.eligible(body,court);
            String assigned=com.dreamseekers.ecologyworkposts.EcologyWorkPosts.assign(f.peer.actor,court,com.solegendary.reignofnether.research.economy.ResearchPlacementIdentity.serverIdentity(court),court.getCaptureRevision(),java.util.List.of(new com.dreamseekers.ecologyworkposts.EcologyPackets.Target(body.getId(),body.getUUID())));
            Object recipe=ProductionItems.getProductionItem(DreamseekersMod.CRAZY_TERRORBEAK_UNIT.get());
            System.out.println("API59_REPAIR_CARE_UPGRADE committed=true exactPrice=80F60O30S supportPermanent=true safePost="+post+" recipe="+recipe+" ecologyEligible="+eligible+" assign="+assigned);
            h.assertTrue(recipe==null,"paid advanced form still has no direct training recipe");
            h.assertTrue(eligible&&assigned.equals("assigned"),"committed paid permanent grounded upgrade retains care eligibility: eligible="+eligible+" assign="+assigned);
            body.setHealth(body.getMaxHealth()-6);health[0]=body.getHealth();
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(f.owner,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("dreamseekers","contract_care"));
        }).thenWaitUntil(()->h.assertTrue(com.dreamseekers.ecologyworkposts.EcologyWorkPosts.snapshot(court).stream()
                .anyMatch(o->o.uuid().equals(upgraded[0].getUUID())&&o.arrived()),"upgraded body's real AI naturally reaches the reserved post"))
        .thenExecute(()->{
            arrival[0]=f.level.getGameTime();
            h.assertTrue(f.level.noCollision(upgraded[0]),"actual large upgraded body fits the post");
            h.assertTrue(upgraded[0].getHealth()==health[0],"no healing while approaching the care post");
        }).thenWaitUntil(()->h.assertTrue(upgraded[0].getHealth()>health[0],"ordinary care ticks actually heal the paid upgraded body"))
        .thenExecute(()->{
            h.assertTrue(upgraded[0].getHealth()==health[0]+1,"one real care pulse heals exactly one HP");
            h.assertTrue(f.level.getGameTime()>=arrival[0],"healing follows natural arrival");
            new com.solegendary.reignofnether.unit.UnitActionItem(f.owner,
                    com.solegendary.reignofnether.unit.UnitAction.STOP,-1,new int[]{upgraded[0].getId()},BlockPos.ZERO,BlockPos.ZERO).action(f.level);
            h.assertTrue(com.dreamseekers.ecologyworkposts.EcologyWorkPosts.snapshot(court).isEmpty(),"native STOP revokes the advanced body's care lease");
            health[0]=upgraded[0].getHealth();
        }).thenExecuteAfter(100,()->{
            h.assertTrue(upgraded[0].getHealth()==health[0],"STOP prevents further care pulses");
            System.out.println("API59_REPAIR_CARE_FULL_PASS paid_upgrade natural_arrival actual_heal native_stop no_direct_recipe");
            f.close();h.succeed();
        });
    }

}
