// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import static com.dreamseekers.ShadowGolemStrikeSkillGameTests.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.ShadowGolemUnit;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.*;
import com.solegendary.reignofnether.building.buildings.placements.*;
import com.solegendary.reignofnether.building.custombuilding.CustomBuilding;
import com.solegendary.reignofnether.blocks.BlockServerEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import java.util.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.function.BooleanSupplier;

/** Three fixed main-suite cases. Small actual registered stone placements use the real Sanctuary
 * definition, not a synthetic HP pool. Probe hooks are explicit adversarial virtual API boundaries;
 * completion-guard mutation is not represented as a third-party world-write event. */
final class ShadowGolemStrikeBuildingCases {
    private static final List<BuildingPlacement> BUILDINGS=new ArrayList<>();
    private static final List<Object> LISTENERS=new ArrayList<>();
    private static final Map<GameTestHelper,Map<String,ProbePlacement>> RESERVES=new IdentityHashMap<>();
    static void cleanup(ServerLevel level){BuildingServerEvents.getBuildings().removeAll(BUILDINGS);BUILDINGS.clear();for(var listener:LISTENERS)MinecraftForge.EVENT_BUS.unregister(listener);LISTENERS.clear();RESERVES.clear();}

    static void encodedBuildingBudgetAndLegacyAftermath(GameTestHelper h){encodedBuildingBudgetAndLegacyAftermath(h,false);}
    static void encodedBuildingBudgetAndLegacyAftermath(GameTestHelper h,boolean researched){
        room(h);
        String suffix=researched?"Research":""; // Parallel baseline and researched cases must not share research ownership.
        var contactCaster=golem(h,"BuildingContactOwner"+suffix,8.5,5.5);
        var budgetCaster=golem(h,"BuildingBudgetOwner"+suffix,8.5,15.5);
        var collateral=target(h,"BuildingContactEnemy"+suffix,7,7.5);
        var budgetTarget=target(h,"BuildingBudgetEnemy"+suffix,7,17.5);
        int[] collateralWrites={0,0};Object listener=new Object(){@SubscribeEvent public void shadowGolemBuildingObserveNativeVersusDirectCollateral(LivingAttackEvent e){if(e.getEntity()==collateral){collateralWrites[ShadowGolemStrike.isStrike(e.getSource())?0:1]++;}}};MinecraftForge.EVENT_BUS.register(listener);LISTENERS.add(listener);
        var contact=install(h,"BuildingContactEnemy"+suffix,10,2,5,1);
        install(h,"BuildingContactEnemy"+suffix,25,2,4,2);install(h,"BuildingLegacyEnemy"+suffix,25,2,29,2);
        var budget=install(h,"BuildingBudgetEnemy"+suffix,10,2,15,4);
        var legacy=install(h,"BuildingLegacyEnemy"+suffix,23,2,24,2);
        damage(contactCaster,500);damage(budgetCaster,10);
        var ordinary=budgetCaster.getAttribute(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get());
        h.assertTrue(ordinary!=null,"real existing building-damage attribute, not research activation");ordinary.setBaseValue(3);
        var command=new BuildingCommand(0,100,"time query daytime","ON_DAMAGE_TAKEN");contact.commands.add(command);
        long[] at={-1};double[] expected={0,0};
        ready(h,contactCaster,budgetCaster,collateral,budgetTarget).thenExecute(()->{
            h.assertTrue(contact.getBlocksPlaced()==1&&budget.getBlocksPlaced()==4,"actual physical registered placement counts");
            h.assertTrue(contact.getMeleeDamageMult()>0&&budget.getMeleeDamageMult()>0,"real definition coefficients");
            expected[0]=contactCaster.getUnitAttackDamage()*contact.getMeleeDamageMult()*Math.max(2,contactCaster.getBuildingDamageMultiplier());
            expected[1]=budgetCaster.getUnitAttackDamage()*budget.getMeleeDamageMult()*Math.max(2,budgetCaster.getBuildingDamageMultiplier());
            if(researched){com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(budgetCaster.getOwnerName(),com.dreamseekers.unitprogression.ExpansionResearch.BREACHING_STRUCTURE_ID);expected[1]*=1.25;}
            h.assertTrue(expected[0]>=contact.getHealthPerBlock()/2&&expected[1]<budget.getHealthPerBlock()/2,"declared deterministic one-break versus fractional-only lanes");
            at[0]=h.getLevel().getGameTime();requireCast(h,encoded(h,contactCaster,contact,contact.originPos),SkillResult.SUCCESS,"actual encoded contact cast");
            requireCast(h,encoded(h,budgetCaster,budget,budget.originPos),SkillResult.SUCCESS,"actual encoded ordinary-multiplier4 cast");
            h.assertTrue(cd(contactCaster)==480&&cd(budgetCaster)==480&&contact.receipt==null&&budget.receipt==null,"pay480, no immediate budget/write");
        }).thenIdle(19).thenExecute(()->{
            h.assertTrue(contact.receipt==null&&budget.receipt==null&&contact.getBlocksPlaced()==1,"real tick19 no building write");
        }).thenIdle(1).thenExecute(()->{
            h.assertTrue(h.getLevel().getGameTime()==at[0]+20&&contact.receipt!=null&&budget.receipt!=null,"real20 release invokes guarded native building API");
            close(h,contact.budget,expected[0],"max2 budget");close(h,budget.budget,expected[1],"max4 not multiplied by2");
            h.assertTrue(contact.receipt.accepted()&&contact.receipt.blocksDestroyed()==1&&contact.receipt.interrupted(),"real contact removal interrupts future work");
            h.assertTrue(contact.callbacks==1&&contact.rawBroken()==1&&contact.getBlocksPlaced()==0&&h.getLevel().getBlockState(contact.originPos).isAir(),"successful contact break gets exactly one native completion and original accounting");
            h.assertTrue(command.triggerCount==1,"original native ON_DAMAGE_TAKEN command ran once despite contact vanishing");
            h.assertTrue(collateralWrites[0]==0,"contact invalidation prevents direct secondary skill writes; preexisting native building reactions are separate and not cap3");
            h.assertTrue(h.getLevel().getBlockState(contact.originPos.below(2)).is(Blocks.STONE),"unregistered arena floor untouched by direct skill");
            h.assertTrue(budget.receipt.accepted()&&!budget.receipt.interrupted()&&budget.receipt.blocksDestroyed()==0&&budget.getBlocksPlaced()==4,"fractional real building budget does not fabricate block loss");
            close(h,budget.partialBlocksDestroyed,expected[1]/(budget.getHealthPerBlock()/2),"committed native fraction");
            close(h,budgetTarget.getHealth(),985,"still-valid primary permits actual150percent secondary physical hit");
            int prior=legacy.getBlocksPlaced();legacy.destroyRandomBlocks(legacy.getHealthPerBlock()/2);
            h.assertTrue(legacy.getBlocksPlaced()==prior-1&&legacy.callbacks==1&&legacy.rawBroken()==1,"unchanged legacy method still performs its own native callback and bookkeeping");
            bank(h);System.out.println("DREAMSEEKERS_SHADOW_GOLEM_STRIKE_BUILDING_BUDGET_CONTACT_NATIVE_AFTER_MATH_PASS budget2="+expected[0]+" budget4="+expected[1]+" nativeCollateralAttackEvents="+collateralWrites[1]);
        }).thenSucceed();
    }

    static void buildingIdentityAndCallbackInvalidation(GameTestHelper h){
        room(h);
        var originCaster=golem(h,"BuildingOriginOwner",8.5,4.5);
        var replacementCaster=golem(h,"BuildingReplaceOwner",8.5,12.5);
        var captureCaster=golem(h,"BuildingCaptureOwner",8.5,22.5);
        var origin=install(h,"BuildingOriginEnemy",10,2,4,3);
        var replacement=install(h,"BuildingReplaceEnemy",10,2,12,3);
        var capture=install(h,"BuildingCaptureEnemy",10,2,22,3);
        // Completed reserve placements prevent genuine native capture from defeating either account.
        install(h,"BuildingCaptureEnemy",25,2,26,2);install(h,"BuildingCaptureOwner",25,2,29,2);
        var returnCapturer=target(h,"BuildingCaptureEnemy",22.5,22.5);
        var originVictim=target(h,"BuildingOriginEnemy",7,6.5);
        var replacementVictim=target(h,"BuildingReplaceEnemy",7,14.5);
        var captureVictim=target(h,"BuildingCaptureEnemy",7,24.5);
        // This nearby enemy is moved outside the normal capture radius for the explicit capture transition.
        var savedOrigin=captureCaster.position();var returnStart=returnCapturer.position();var victimStart=captureVictim.position();
        ProbePlacement[] replacementObject={null};long[] revision={-1};
        ready(h,originCaster,replacementCaster,captureCaster,returnCapturer,originVictim,replacementVictim,captureVictim).thenExecute(()->{
            var original=origin.originPos;
            requireDenied(h,new SkillNetwork.BuildingCast(new SkillBuildingRequest(UnitSkill.SHADOW_GOLEM_STRIKE,originCaster.getId(),UUID.randomUUID(),originCaster.rts().skills().revision(),original,original)).execute(player(h,originCaster.getOwnerName())),"forged source UUID");
            requireDenied(h,new SkillNetwork.BuildingCast(new SkillBuildingRequest(UnitSkill.SHADOW_GOLEM_STRIKE,originCaster.getId(),originCaster.getUUID(),originCaster.rts().skills().revision()+1,original,original)).execute(player(h,originCaster.getOwnerName())),"forged revision");
            requireDenied(h,new SkillNetwork.BuildingCast(new SkillBuildingRequest(UnitSkill.SHADOW_GOLEM_STRIKE,originCaster.getId(),originCaster.getUUID(),originCaster.rts().skills().revision(),original,original.offset(0,0,1))).execute(player(h,originCaster.getOwnerName())),"unregistered contact");
            requireDenied(h,new SkillNetwork.BuildingCast(new SkillBuildingRequest(UnitSkill.SHADOW_GOLEM_STRIKE,originCaster.getId(),originCaster.getUUID(),originCaster.rts().skills().revision(),original,original)).execute(player(h,"BuildingOriginEnemy")),"foreign sender");
            h.assertTrue(cd(originCaster)==0&&origin.receipt==null,"denied requests never spend or mutate building");
            requireCast(h,encoded(h,originCaster,origin,original),SkillResult.SUCCESS,"origin reservation");
            requireCast(h,encoded(h,replacementCaster,replacement,replacement.originPos),SkillResult.SUCCESS,"exact-object reservation");
            requireCast(h,encoded(h,captureCaster,capture,capture.originPos),SkillResult.SUCCESS,"normal capture revision reservation");
            origin.originPos=origin.originPos.east();
            BuildingServerEvents.getBuildings().remove(replacement);
            replacementObject[0]=new ProbePlacement(h.getLevel(),replacement.originPos,replacement.ownerName,copyBlocks(replacement));
            BuildingServerEvents.getBuildings().add(replacementObject[0]);BUILDINGS.add(replacementObject[0]);
            revision[0]=capture.getCaptureRevision();
            captureVictim.moveTo(victimStart.x+16,victimStart.y,victimStart.z);
            h.assertTrue(capture.capture(h.getLevel())&&capture.ownerName.equals(captureCaster.getOwnerName())&&capture.getCaptureRevision()==revision[0]+1,"first genuine supported capture callback transitions A to B");
            captureCaster.moveTo(savedOrigin.x+15,savedOrigin.y,savedOrigin.z);
            returnCapturer.moveTo(savedOrigin.x,savedOrigin.y,savedOrigin.z);
            h.assertTrue(capture.capture(h.getLevel())&&capture.ownerName.equals(account("BuildingCaptureEnemy"))&&capture.getCaptureRevision()==revision[0]+2,"second genuine supported capture restores A but advances epoch");
            returnCapturer.moveTo(returnStart.x,returnStart.y,returnStart.z);captureCaster.moveTo(savedOrigin.x,savedOrigin.y,savedOrigin.z);captureVictim.moveTo(victimStart.x,victimStart.y,victimStart.z);
        }).thenIdle(21).thenExecute(()->{
            for(var source:List.of(originCaster,replacementCaster,captureCaster))h.assertTrue(!ShadowGolemStrike.active(source)&&cd(source)>0,"identity invalidation never refunds paid CD");
            h.assertTrue(origin.receipt==null&&replacement.receipt==null&&replacementObject[0].receipt==null&&capture.receipt==null,"changed origin/same-coordinate replacement/normal capture ABA never receive original budget");
            close(h,originVictim.getHealth(),1000,"invalid origin no collateral");close(h,replacementVictim.getHealth(),1000,"replacement no collateral");close(h,captureVictim.getHealth(),1000,"capture ABA no collateral");
            bank(h);System.out.println("DREAMSEEKERS_SHADOW_GOLEM_STRIKE_BUILDING_OBJECT_ORIGIN_CAPTURE_ABA_PASS revision="+revision[0]+"->"+capture.getCaptureRevision());
        }).thenSucceed();
    }

    static void guardedReentryFractionAndCommittedAccounting(GameTestHelper h){
        room(h);var attacker=golem(h,"BuildingGuardOwner",5.5,5.5);
        var initial=install(h,"BuildingGuardEnemy",12,2,3,4);
        var fractional=install(h,"BuildingGuardEnemy",12,2,10,8);
        var committed=install(h,"BuildingGuardEnemy",12,2,20,4);
        var exception=install(h,"BuildingGuardEnemy",24,2,20,3);
        BlockPos[] liquidRestore={null};
        var worldOwnerCaster=golem(h,"BuildingWorldOwner",3.5,14.5);
        var worldObjectCaster=golem(h,"BuildingWorldObject",3.5,24.5);
        var worldOwner=install(h,"BuildingWorldOwnerEnemy",5,2,14,4);
        var worldObject=install(h,"BuildingWorldObjectEnemy",5,2,24,4);
        var worldOwnerVictim=target(h,"BuildingWorldOwnerEnemy",2,16.5);
        var worldObjectVictim=target(h,"BuildingWorldObjectEnemy",2,26.5);
        reserve(h,"BuildingWorldOwner");reserve(h,"BuildingGuardOwner");
        damage(worldOwnerCaster,500);damage(worldObjectCaster,500);
        var placements=List.of(worldOwner,worldObject);var sources=List.of(worldOwnerCaster,worldObjectCaster);
        var victims=List.of(worldOwnerVictim,worldObjectVictim);
        var candidates=placements.stream().map(b->b.getBlocks().stream().map(BuildingBlock::getBlockPos).collect(java.util.stream.Collectors.toUnmodifiableSet())).toList();
        boolean[] armed={false};int[] notifications={0,0},directCollateral={0,0};BlockPos[] firstRemoved={null,null};ProbePlacement[] replacement={null};
        Object worldWrites=new Object(){
            @SubscribeEvent public void shadowGolemStrikeActualWorldNeighborNotifyInvalidatesBeforeDestroyReturns(BlockEvent.NeighborNotifyEvent e){
                if(!armed[0]||e.getLevel()!=h.getLevel())return;
                for(int i=0;i<placements.size();i++)if(notifications[i]==0&&candidates.get(i).contains(e.getPos())&&h.getLevel().getBlockState(e.getPos()).isAir()){
                    var building=placements.get(i);notifications[i]++;firstRemoved[i]=e.getPos().immutable();
                    h.assertTrue(building.rawBroken()==0&&building.callbacks==0&&building.getBlocksPlaced()==4,"real neighbor event occurs after world write but BEFORE original placement completion");
                    h.assertTrue(building.getBlocks().stream().filter(b->!b.isPlaced(h.getLevel())).count()==1,"first actual world removal alone precedes callback invalidation");
                    if(i==0)building.ownerName=sources.get(i).getOwnerName();
                    else {BuildingServerEvents.getBuildings().remove(building);replacement[0]=new ProbePlacement(h.getLevel(),building.originPos,building.ownerName,copyBlocks(building));BUILDINGS.add(replacement[0]);BuildingServerEvents.getBuildings().add(replacement[0]);}
                    System.out.println("SHADOW_GOLEM_STRIKE_REAL_WORLD_NOTIFY lane="+i+" pos="+e.getPos()+" requestContact="+building.originPos+" firstRemovedIsContact="+e.getPos().equals(building.originPos)+" rawBeforeCompletion="+building.rawBroken());
                }
            }
            @SubscribeEvent public void shadowGolemStrikeActualWorldNotifyObserveNoDirectCollateral(LivingAttackEvent e){if(ShadowGolemStrike.isStrike(e.getSource()))for(int i=0;i<victims.size();i++)if(e.getEntity()==victims.get(i))directCollateral[i]++;}
        };
        MinecraftForge.EVENT_BUS.register(worldWrites);LISTENERS.add(worldWrites);
        long[] worldReservedAt={-1};
        ready(h,attacker,worldOwnerCaster,worldObjectCaster,worldOwnerVictim,worldObjectVictim).thenExecute(()->{
            List<BuildingPlacement.GuardedDamageReceipt> nested=new ArrayList<>();
            BooleanSupplier yes=()->true;
            initial.attackHook=()->nested.add(initial.destroyRandomBlocksGuarded(1,yes,yes,attacker));
            initial.healthHook=()->nested.add(initial.destroyRandomBlocksGuarded(1,yes,yes,attacker));
            boolean[] guarded={false};
            BooleanSupplier nestingGuard=()->{if(!guarded[0]){guarded[0]=true;nested.add(initial.destroyRandomBlocksGuarded(1,yes,yes,attacker));}return true;};
            var accepted=initial.destroyRandomBlocksGuarded(1,nestingGuard,yes,attacker);
            h.assertTrue(accepted.accepted()&&nested.size()==3&&nested.stream().allMatch(r->!r.accepted()&&r.blocksDestroyed()==0&&r.interrupted()),"reservation covers initial isAttackable, health and valid virtual callbacks");
            h.assertTrue(initial.callbacks==0&&initial.rawBroken()==0,"nested initial calls performed no world writes");
            double unit=fractional.getHealthPerBlock()/2;int before=fractional.getBlocksPlaced();boolean[] valid={true};
            fractional.breakHook=()->valid[0]=false;
            var cut=fractional.destroyRandomBlocksGuarded(unit*3.25,()->valid[0],yes,attacker);
            h.assertTrue(cut.accepted()&&cut.interrupted()&&cut.blocksDestroyed()==1&&fractional.callbacks==1&&fractional.getBlocksPlaced()==before-1,"first actual callback cancels all next direct writes");
            close(h,fractional.partialBlocksDestroyed,.25,"only legitimate quarter fraction committed");
            valid[0]=true;var later=fractional.destroyRandomBlocksGuarded(unit*.25,()->valid[0],yes,attacker);
            h.assertTrue(later.accepted()&&!later.interrupted()&&later.blocksDestroyed()==0&&fractional.getBlocksPlaced()==before-1,"unexecuted three-budget integers are not a delayed charge");close(h,fractional.partialBlocksDestroyed,.5,"next quarter is exactly half, not queued full damage");
            int committedBefore=committed.getBlocksPlaced();String old=committed.ownerName;boolean[] live={true};
            var done=committed.destroyRandomBlocksGuarded(committed.getHealthPerBlock()/2*3,()->live[0],()->{
                // Actual completion supplier runs only after a successful physical world write.
                h.assertTrue(committed.getBlocks().stream().filter(b->!b.isPlaced(h.getLevel())).count()==1,"completion boundary observes one actually removed registered block");
                committed.ownerName=attacker.getOwnerName();BuildingServerEvents.getBuildings().remove(committed);live[0]=false;return false;
            },attacker);
            h.assertTrue(done.blocksDestroyed()==1&&done.interrupted()&&committed.callbacks==0&&committed.rawBroken()==1&&committed.getBlocksPlaced()==committedBefore-1,"invalid completion identity settles original base accounting only, no aftermath under replacement owner");
            h.assertTrue(!old.equals(committed.ownerName),"callback really changed identity, not a constant false guard");
            boolean threw=false;try{exception.destroyRandomBlocksGuarded(1,()->{throw new IllegalStateException("declared guarded callback test");},yes,attacker);}catch(IllegalStateException expected){threw=expected.getMessage().equals("declared guarded callback test");}
            h.assertTrue(threw&&exception.destroyRandomBlocksGuarded(1,yes,yes,attacker).accepted(),"finally releases reservation after actual callback exception");
            var nether=h.getLevel().getServer().getLevel(Level.NETHER);h.assertTrue(nether!=null,"real other server dimension exists");
            var other=new ProbePlacement(nether,exception.originPos,exception.ownerName,copyBlocks(exception));
            h.assertTrue(!other.destroyRandomBlocksGuarded(1,yes,yes,attacker).accepted(),"actual other-level placement refuses this overworld attacker before writes");
            liquidRestore[0]=nativeOverrideCases(h,attacker);
            worldReservedAt[0]=h.getLevel().getGameTime();armed[0]=true;
            for(int i=0;i<placements.size();i++){
                var building=placements.get(i);var source=sources.get(i);
                h.assertTrue(building.getBlocksPlaced()==4&&source.getUnitAttackDamage()*building.getMeleeDamageMult()*Math.max(2,source.getBuildingDamageMultiplier())>=building.getHealthPerBlock()/2*4,"four real eligible blocks and sufficient declared budget, no forced shuffle or future-state fixture");
                requireCast(h,encoded(h,source,building,building.originPos),SkillResult.SUCCESS,"real encoded world-write invalidation lane="+i);
            }
            bank(h);System.out.println("DREAMSEEKERS_SHADOW_GOLEM_STRIKE_GUARDED_REENTRY_FRACTION_COMMITTED_ACCOUNTING_PASS completionMutation=explicit_post_write_supplier_not_thirdparty_world_event");
        }).thenIdle(19).thenExecute(()->{
            h.assertTrue(notifications[0]==0&&notifications[1]==0&&worldOwner.receipt==null&&worldObject.receipt==null,"world-write observers see no early skill damage through exact windup19");
            h.assertTrue(liquidRestore[0]!=null&&h.getLevel().getBlockState(liquidRestore[0]).is(Blocks.WATER),"original Bridge queued liquid restoration really reached world through native tick");
        }).thenIdle(1).thenExecute(()->{
            h.assertTrue(h.getLevel().getGameTime()==worldReservedAt[0]+20,"actual release after unchanged20 windup");
            for(int i=0;i<placements.size();i++){
                var building=placements.get(i);var receipt=building.receipt;
                h.assertTrue(notifications[i]==1&&firstRemoved[i]!=null&&receipt!=null&&receipt.accepted()&&receipt.interrupted()&&receipt.blocksDestroyed()==1,"real world callback invalidation stops remaining eligible writes lane="+i);
                h.assertTrue(building.callbacks==0&&building.rawBroken()==1&&building.getBlocksPlaced()==3,"original object alone completes raw accounting without new-owner aftermath lane="+i);
                h.assertTrue(candidates.get(i).stream().filter(p->h.getLevel().getBlockState(p).isAir()).count()==1,"exactly one physical registered block removed despite four-block budget lane="+i);
                h.assertTrue(directCollateral[i]==0&&!ShadowGolemStrike.active(sources.get(i))&&cd(sources.get(i))==460,"no collateral/replay/refund after world callback lane="+i);close(h,victims.get(i).getHealth(),1000,"no native aftermath or direct damage transferred after identity invalidation");
            }
            h.assertTrue(replacement[0]!=null&&replacement[0].callbacks==0&&replacement[0].rawBroken()==0&&replacement[0].getBlocksPlaced()==3,"new same-origin placement inherits physical world state only, never old break callback/accounting");
            bank(h);System.out.println("DREAMSEEKERS_SHADOW_GOLEM_STRIKE_REAL_WORLD_NEIGHBOR_INVALIDATION_PASS ownerEvents="+notifications[0]+" objectEvents="+notifications[1]);
        }).thenSucceed();
    }

    private static BlockPos nativeOverrideCases(GameTestHelper h,ShadowGolemUnit attacker){
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(24,2,7));var bridgeBlocks=new ArrayList<BuildingBlock>();
        for(int i=0;i<2;i++){var p=origin.east(i);level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),2);level.setBlock(p.below(),Blocks.WATER.defaultBlockState(),2);bridgeBlocks.add(new BuildingBlock(p,Blocks.OAK_PLANKS.defaultBlockState()));}
        var bridge=new BridgePlacement(Buildings.OAK_BRIDGE,level,origin,Rotation.NONE,account("BuildingGuardEnemy"),bridgeBlocks,false,false);BUILDINGS.add(bridge);BuildingServerEvents.getBuildings().add(bridge);
        double amount=bridge.getHealthPerBlock()/2;h.assertTrue(Double.isFinite(amount)&&amount>0,"native bridge health calculation on actual solid registered blocks");
        var bridgeReceipt=bridge.destroyRandomBlocksGuarded(amount,()->BuildingServerEvents.getBuildings().contains(bridge),()->BuildingServerEvents.getBuildings().contains(bridge),attacker);
        h.assertTrue(bridgeReceipt.accepted()&&bridgeReceipt.blocksDestroyed()==1&&bridge.getBlocksPlaced()==1,"actual Bridge override completes original bookkeeping");
        var restored=bridgeBlocks.stream().map(BuildingBlock::getBlockPos).filter(p->level.getBlockState(p).isAir()).findFirst().orElseThrow();
        h.assertTrue(BlockServerEvents.blocksToPlace.containsKey(restored)&&BlockServerEvents.blocksToPlace.get(restored).is(Blocks.WATER),"actual native Bridge override queued water from physical block below");
        BuildingServerEvents.getBuildings().remove(bridge); // boundary fixture detaches before ordinary half-health collapse, not before callback evidence
        var structure=new CompoundTag();var palette=new ListTag();palette.add(NbtUtils.writeBlockState(Blocks.STONE_BRICKS.defaultBlockState()));structure.put("palette",palette);
        var blocks=new ListTag();var block=new CompoundTag();var zero=new ListTag();for(int i=0;i<3;i++)zero.add(IntTag.valueOf(0));block.put("pos",zero);block.putInt("state",0);blocks.add(block);structure.put("blocks",blocks);
        var definition=new CustomBuilding("golem_guard_custom_boundary",new Vec3i(1,1,1),Blocks.STONE_BRICKS,structure);definition.commands.clear();definition.commands.add(new BuildingCommand(0,100,"time query daytime","ON_DAMAGE_TAKEN"));
        var customPos=h.absolutePos(new BlockPos(24,2,14));level.setBlock(customPos,Blocks.STONE_BRICKS.defaultBlockState(),2);var customBlocks=new ArrayList<BuildingBlock>();customBlocks.add(new BuildingBlock(customPos,Blocks.STONE_BRICKS.defaultBlockState()));
        var custom=new CustomBuildingPlacement(definition,level,customPos,Rotation.NONE,account("BuildingGuardEnemy"),customBlocks);BUILDINGS.add(custom);BuildingServerEvents.getBuildings().add(custom);
        var baseCommand=new BuildingCommand(0,100,"time query daytime","ON_DAMAGE_TAKEN");((BuildingPlacement)custom).commands.add(baseCommand);
        var customReceipt=custom.destroyRandomBlocksGuarded(custom.getHealthPerBlock()/2,()->BuildingServerEvents.getBuildings().contains(custom),()->BuildingServerEvents.getBuildings().contains(custom),attacker);
        h.assertTrue(customReceipt.accepted()&&customReceipt.blocksDestroyed()==1&&custom.getBlocksPlaced()==0&&custom.commands.get(0).triggerCount==1&&baseCommand.triggerCount==1,"actual Custom override retains its distinct command list plus base native callback exactly once");
        BuildingServerEvents.getBuildings().remove(custom);
        return restored;
    }

    private static SkillResult encoded(GameTestHelper h,ShadowGolemUnit source,ProbePlacement b,BlockPos contact){
        var sent=new SkillNetwork.BuildingCast(new SkillBuildingRequest(UnitSkill.SHADOW_GOLEM_STRIKE,source.getId(),source.getUUID(),source.rts().skills().revision(),b.originPos,contact));
        var bytes=new FriendlyByteBuf(Unpooled.buffer());try{sent.encode(bytes);var received=SkillNetwork.BuildingCast.decode(bytes);h.assertTrue(sent.equals(received)&&bytes.readableBytes()==0,"real building request encode/decode consumes exact payload, no client budget/owner/dimension fields");return received.execute(player(h,source.getOwnerName()));}finally{bytes.release();}
    }
    private static ProbePlacement install(GameTestHelper h,String owner,int x,int y,int z,int count){
        var origin=h.absolutePos(new BlockPos(x,y,z));var blocks=new ArrayList<BuildingBlock>();
        for(int i=0;i<count;i++){var pos=origin.offset(i%4,0,i/4);h.getLevel().setBlock(pos,Blocks.STONE_BRICKS.defaultBlockState(),2);blocks.add(new BuildingBlock(pos,Blocks.STONE_BRICKS.defaultBlockState()));}
        var p=new ProbePlacement(h.getLevel(),origin,account(owner),blocks);BUILDINGS.add(p);BuildingServerEvents.getBuildings().add(p);reserve(h,owner);return p;
    }
    /** Real completed reserve in the arena's upper layer, outside all ground combat/capture fixtures.
     * Never disable native defeat or recreate a defeated account; this persists until AfterBatch. */
    private static void reserve(GameTestHelper h,String owner){
        String name=account(owner);var reserves=RESERVES.computeIfAbsent(h,k->new LinkedHashMap<>());if(reserves.containsKey(name))return;
        int index=reserves.size();h.assertTrue(index<16,"bounded upper-layer reserve cells");
        var origin=h.absolutePos(new BlockPos(2+(index%4)*7,8,2+(index/4)*7));var blocks=new ArrayList<BuildingBlock>();
        for(int x=0;x<2;x++)for(int z=0;z<2;z++){var pos=origin.offset(x,0,z);h.getLevel().setBlock(pos,Blocks.STONE_BRICKS.defaultBlockState(),2);blocks.add(new BuildingBlock(pos,Blocks.STONE_BRICKS.defaultBlockState()));}
        var reserve=new ProbePlacement(h.getLevel(),origin,name,blocks);BUILDINGS.add(reserve);BuildingServerEvents.getBuildings().add(reserve);reserve.onBuilt();reserves.put(name,reserve);
        h.assertTrue(reserve.isBuilt&&reserve.getBlocksPlaced()==4&&reserve.getBlocksTotal()==4&&!reserve.shouldBeDestroyed()&&!reserve.getBuilding().capturable,"real completed physical reserve, native defeat and capture policies unchanged");
        System.out.println("SHADOW_GOLEM_STRIKE_REAL_BUILDING_RESERVE owner="+name+" origin="+origin+" blocks="+reserve.getBlocksPlaced());
    }
    private static ArrayList<BuildingBlock> copyBlocks(ProbePlacement p){var copy=new ArrayList<BuildingBlock>();for(var b:p.getBlocks())copy.add(new BuildingBlock(b.getBlockPos(),b.getBlockState()));return copy;}
    private static void requireCast(GameTestHelper h,SkillResult actual,SkillResult expected,String why){h.assertTrue(actual==expected,why+" actual="+actual+" expected="+expected);}
    private static void requireDenied(GameTestHelper h,SkillResult actual,String why){h.assertTrue(actual!=SkillResult.SUCCESS,why+" actual="+actual);}
    private static final class ProbePlacement extends BuildingPlacement {
        int callbacks;double budget;GuardedDamageReceipt receipt;Runnable attackHook,healthHook,breakHook;
        ProbePlacement(Level level,BlockPos origin,String owner,ArrayList<BuildingBlock> blocks){super(DreamseekersMod.BEETLE_SANCTUARY,level,origin,Rotation.NONE,owner,blocks,false);}
        @Override public boolean isAttackable(){if(attackHook!=null){var hook=attackHook;attackHook=null;hook.run();}return super.isAttackable();}
        @Override public double getHealthPerBlock(){if(healthHook!=null){var hook=healthHook;healthHook=null;hook.run();}return super.getHealthPerBlock();}
        @Override public void onBlockBreak(ServerLevel level,BlockPos pos,boolean breakBlocks){callbacks++;super.onBlockBreak(level,pos,breakBlocks);if(breakHook!=null){var hook=breakHook;breakHook=null;hook.run();}}
        @Override public GuardedDamageReceipt destroyRandomBlocksGuarded(double amount,BooleanSupplier valid,BooleanSupplier completion,Mob attacker){budget=amount;var result=super.destroyRandomBlocksGuarded(amount,valid,completion,attacker);receipt=result;return result;}
        @Override public void destroy(ServerLevel level){System.out.println("SHADOW_GOLEM_STRIKE_BUILDING_NATIVE_DESTROY owner="+ownerName+" time="+level.getGameTime()+" origin="+originPos+" built="+isBuilt+" placed="+getBlocksPlaced()+" total="+getBlocksTotal()+" physical="+getBlocks().stream().filter(b->b.isPlaced(level)).count()+" broken="+rawBroken()+" callbacks="+callbacks+" lastAttacker="+(lastAttacker==null?null:lastAttacker.getUUID())+" captureRevision="+getCaptureRevision());super.destroy(level);}
        boolean capture(ServerLevel level){return super.checkAndDoCapture(level);}
        int rawBroken(){try{var f=BuildingPlacement.class.getDeclaredField("totalBlocksEverBroken");f.setAccessible(true);return f.getInt(this);}catch(ReflectiveOperationException error){throw new RuntimeException(error);}}
    }
}
