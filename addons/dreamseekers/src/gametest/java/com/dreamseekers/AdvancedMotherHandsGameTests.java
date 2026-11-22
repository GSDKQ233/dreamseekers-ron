// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.dreamseekers.unitprogression.AdvancedUnitResearch;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraftforge.gametest.*;
import java.util.*;

/** New-state GameTests: actual authenticated casts, real server entities and APS consumers.
 * These tests must be executed by the exclusive Java owner; authoring is not evidence of passing. */
@GameTestHolder("dreamseekers_advanced_mother_hands") @PrefixGameTestTemplate(false)
public final class AdvancedMotherHandsGameTests {
    private static final String BATCH="advanced_mother_hands";
    private static final List<AaroncosSkillCombatGameTests.F> FIXTURES=new ArrayList<>();
    private static final List<com.solegendary.reignofnether.building.BuildingPlacement> POPULATION_BUILDINGS=new ArrayList<>();
    private static final Set<Long> POPULATION_CHUNKS=new HashSet<>();
    private static AaroncosSkillCombatGameTests.F fixture(GameTestHelper h,String name){var f=new AaroncosSkillCombatGameTests.F(h,"API54"+name);FIXTURES.add(f);return f;}
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){
        for(var building:POPULATION_BUILDINGS){building.onRemoved(com.solegendary.reignofnether.building.BuildingPlacement.RemovalReason.RELOAD);com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(building);}POPULATION_BUILDINGS.clear();
        for(var f:List.copyOf(FIXTURES)){ResearchServerEvents.removeAllResearchFor(f.owner);f.close();}FIXTURES.clear();
        for(long key:POPULATION_CHUNKS){var chunk=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}POPULATION_CHUNKS.clear();
    }
    /** Real completed own population geometry, not a cheat or direct population override. */
    private static void installNestBroodPopulationAnchor(AaroncosSkillCombatGameTests.F f){
        var level=f.h.getLevel();var origin=f.center.offset(0,0,40);var type=DreamseekersMod.DREAM_ANCHOR;
        for(int x=(origin.getX()-24)>>4;x<=(origin.getX()+24)>>4;x++)for(int z=(origin.getZ()-24)>>4;z<=(origin.getZ()+24)>>4;z++){
            long key=net.minecraft.world.level.ChunkPos.asLong(x,z);
            if(!level.getForcedChunks().contains(key)){level.setChunkForced(x,z,true);POPULATION_CHUNKS.add(key);}level.getChunk(x,z);
        }
        for(BlockPos p:BlockPos.betweenClosed(origin.offset(0,-1,0),origin.offset(21,21,21)))
            level.setBlock(p,p.getY()==origin.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        for(var block:com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(level),level,origin,net.minecraft.world.level.block.Rotation.NONE))
            level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        var building=com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(type,level,origin,net.minecraft.world.level.block.Rotation.NONE,f.owner,false);
        POPULATION_BUILDINGS.add(building);com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().add(building);building.onBuilt();
        f.h.assertTrue(building.isBuilt&&building.ownerName.equals(f.owner)&&building.getHealth()>0,"physical completed owned population anchor");
    }
    private static BeetleMotherUnit mother(AaroncosSkillCombatGameTests.F f){return f.add(DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(f.h.getLevel()),f.owner,0,0);}
    private static SkillRequest request(BeetleMotherUnit s){return new SkillRequest(UnitSkill.MOTHER_NEST,s.getId(),s.getUUID(),s.rts().skills().revision(),s.getId(),s.getUUID());}
    private static SkillResult toggle(AaroncosSkillCombatGameTests.F f,BeetleMotherUnit s){return new SkillNetwork.Cast(request(s)).execute(f.peer.actor);}
    private static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.0001,why+" actual="+actual+" expected="+expected);}
    private static void unlock(AaroncosSkillCombatGameTests.F f,net.minecraft.resources.ResourceLocation id){ResearchServerEvents.addResearch(f.owner,id);}

    private static List<SkillNetwork.Sync> syncs(PassiveIncomePeer peer){
        var result=new ArrayList<SkillNetwork.Sync>();
        for(Object packet:peer.drain())if(packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket payload&&payload.getIdentifier().equals(DreamseekersMod.id("unit_skills"))){
            var bytes=new net.minecraft.network.FriendlyByteBuf(payload.getData().copy());try{if(bytes.readVarInt()==1){result.add(SkillNetwork.Sync.decode(bytes));if(bytes.readableBytes()!=0)throw new AssertionError("trailing skill sync bytes");}}finally{bytes.release();}}
        return result;
    }
    @GameTest(templateNamespace="dreamseekers_advanced_mother_hands",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=90)
    public static void privateSyncRecomputesNestRatherThanTrustingInternalClaim(GameTestHelper h){
        var f=fixture(h,"NestPrivate");var source=mother(f);unlock(f,AdvancedUnitResearch.MOTHER_NEST_ID);
        h.startSequence().thenIdle(25).thenExecute(()->{syncs(f.peer);
            var claimed=new SkillNetwork.Sync(UnitSkill.MOTHER_NEST,source.getId(),source.getUUID(),source.rts().skills().revision(),0,false,0,false,true,false);
            SkillNetwork.send(f.peer.actor,claimed);var observed=syncs(f.peer);
            h.assertTrue(observed.size()==1&&!observed.get(0).motherNestActive(),"send overwrites claimed active state with server idle stance");
            h.assertTrue(toggle(f,source)==SkillResult.SUCCESS,"real stance enters");observed=syncs(f.peer);
            h.assertTrue(observed.stream().anyMatch(packet->packet.skill()==UnitSkill.MOTHER_NEST&&packet.motherNestActive()),"actual successful cast publishes true owner-private stance");
            try(var foreign=new PassiveIncomePeer(h.getLevel(),f.enemy)){syncs(foreign);SkillNetwork.send(foreign.actor,claimed);h.assertTrue(syncs(foreign).isEmpty(),"different recipient gets no source state");}h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_advanced_mother_hands",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void rightHandHarmonyProjectsOnlyToItsOwnersShockSnapshot(GameTestHelper h) {
        var f=fixture(h,"HarmonySync");
        var left=f.hand(true);
        var right=f.add(DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel()),f.owner,7,0);
        unlock(f,AdvancedUnitResearch.HAND_HARMONY_ID);
        h.startSequence().thenIdle(35).thenExecute(()->{
            h.assertTrue(AaroncosHarmony.active(right),"ordinary researched partners acquired real harmony");
            var periodic=syncs(f.peer).stream().filter(s->s.sourceUuid().equals(right.getUUID())&&s.revision()>=0).toList();
            h.assertTrue(periodic.stream().anyMatch(s->s.skill()==UnitSkill.AARONCOS_RIGHT_SHOCK&&s.handHarmonyActive())
                    && periodic.stream().anyMatch(s->s.skill()==UnitSkill.AARONCOS_RIGHT_VORTEX)
                    && periodic.stream().noneMatch(s->s.skill()==UnitSkill.AARONCOS_RIGHT_VORTEX&&s.handHarmonyActive()),
                    "normal right-hand ticks publish both skills, not the unit-wide flag on vortex");
            try(var other=new PassiveIncomePeer(h.getLevel(),f.enemy)) {
                syncs(other);
                right.rts().skills().sync(f.peer.actor);
                var active=assertRightSnapshots(h,f.peer,right,true);
                right.rts().skills().sync(other.actor);
                for(var snapshot:active)SkillNetwork.send(other.actor,snapshot);
                h.assertTrue(syncs(other).isEmpty(),"both the periodic-state service and direct send stay owner-private");
                left.mob().moveTo(left.mob().getX()-12,left.mob().getY(),left.mob().getZ());
                h.assertTrue(!AaroncosHarmony.active(right),"leaving range actually ended harmony");
                // Replay the same correctly addressed internal snapshots after the aura left. The
                // owner's outbound data must be requalified, not copied from the earlier snapshot.
                for(var snapshot:active)SkillNetwork.send(f.peer.actor,snapshot);
                assertRightSnapshots(h,f.peer,right,false);
            }
            h.succeed();
        });
    }
    private static List<SkillNetwork.Sync> assertRightSnapshots(GameTestHelper h,PassiveIncomePeer peer,
                                                                AaroncosRightHandUnit right,boolean active) {
        var states=syncs(peer).stream().filter(s->s.sourceUuid().equals(right.getUUID())).toList();
        h.assertTrue(states.size()==2 && states.stream().allMatch(s->s.sourceId()==right.getId()
                && s.revision()==right.rts().skills().revision()),"exactly two snapshots for the current right hand");
        h.assertTrue(states.stream().anyMatch(s->s.skill()==UnitSkill.AARONCOS_RIGHT_SHOCK&&s.handHarmonyActive()==active)
                && states.stream().anyMatch(s->s.skill()==UnitSkill.AARONCOS_RIGHT_VORTEX&&!s.handHarmonyActive()),
                "shock reflects live harmony; vortex never exposes it");
        return states;
    }
    @GameTest(templateNamespace="dreamseekers_advanced_mother_hands",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=40)
    public static void advancedOwnerPrivateCodecRoundTripsAndRejectsWrongSkillFlags(GameTestHelper h){
        h.assertTrue(SkillNetwork.acceptsProtocol("19")&&!SkillNetwork.acceptsProtocol("18")&&!SkillNetwork.acceptsProtocol("ABSENT"),"strict protocol increment");
        for(var sync:List.of(new SkillNetwork.Sync(UnitSkill.MOTHER_NEST,7,UUID.randomUUID(),1,0,false,0,false,true,false),
                new SkillNetwork.Sync(UnitSkill.AARONCOS_LEFT_PRESS,8,UUID.randomUUID(),2,400,false,0,false,false,true),
                new SkillNetwork.Sync(UnitSkill.GEL_GUARD,9,UUID.randomUUID(),3,324,false,1,true,false,false))){
            var bytes=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{sync.encode(bytes);h.assertTrue(sync.equals(SkillNetwork.Sync.decode(bytes))&&bytes.readableBytes()==0,"all exact private flags and identities survive codec");}finally{bytes.release();}}
        boolean denied=false;try{new SkillNetwork.Sync(UnitSkill.FOX_DREAM_SEED,7,UUID.randomUUID(),1,0,false,0,true,false,false);}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied,"spore cannot claim light extension");
        denied=false;try{new SkillNetwork.Sync(UnitSkill.GEL_GUARD,7,UUID.randomUUID(),1,0,false,0,false,true,false);}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied,"unrelated skill cannot claim nest");
        denied=false;try{new SkillNetwork.Sync(UnitSkill.AARONCOS_RIGHT_VORTEX,8,UUID.randomUUID(),2,0,false,0,false,false,true);}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied,"vortex still rejects a harmony flag");h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_advanced_mother_hands",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void nestRequiresResearchHasRealButtonAndRejectsReplayWithoutCost(GameTestHelper h){
        var f=fixture(h,"NestAuthority");var s=mother(f);
        h.startSequence().thenIdle(25).thenExecute(()->{
            h.assertTrue(s.getAbilities().get().contains(UnitSkillAbility.MOTHER_NEST),"real constructor ability, not text-only research");
            long revision=s.rts().skills().revision();h.assertTrue(toggle(f,s)==SkillResult.DISABLED&&!MotherNestStance.active(s)&&s.rts().skills().revision()==revision,"no-research rejected without mutation");
            unlock(f,AdvancedUnitResearch.MOTHER_NEST_ID);var old=request(s);
            h.assertTrue(SkillService.request(f.peer.actor,old)==SkillResult.SUCCESS&&MotherNestStance.active(s),"authenticated researched toggle enters");
            h.assertTrue(SkillService.request(f.peer.actor,old)==SkillResult.REPLAY&&MotherNestStance.active(s),"old revision cannot toggle it back");
            h.assertTrue(!s.getNavigation().moveTo(s.getX()+10,s.getY(),s.getZ(),1),"active ordinary navigation cannot create autonomous pursuit");
            try(var enemy=new PassiveIncomePeer(h.getLevel(),f.enemy)) {h.assertTrue(SkillService.request(enemy.actor,request(s))==SkillResult.NOT_OWNER,"real foreign peer cannot toggle");}
            h.assertTrue(toggle(f,s)==SkillResult.SUCCESS&&!MotherNestStance.active(s),"second current own toggle exits");
            h.assertTrue(f.shadow()==500&&s.rts().skills().cooldownTicks(UnitSkill.MOTHER_NEST)==0,"free toggle no shared brood debit/CD");h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_advanced_mother_hands",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void commandsDisplacementOwnerAbaAndNbtNeverRestoreNest(GameTestHelper h){
        var f=fixture(h,"NestLifecycle");var s=mother(f);unlock(f,AdvancedUnitResearch.MOTHER_NEST_ID);
        h.startSequence().thenIdle(25).thenExecute(()->{
            h.assertTrue(toggle(f,s)==SkillResult.SUCCESS,"enter");s.setMoveTarget(s.blockPosition().offset(5,0,0));
            h.assertTrue(!MotherNestStance.active(s),"fresh move immediately owns movement");
            h.assertTrue(toggle(f,s)==SkillResult.SUCCESS,"reenter after move");s.setPos(s.getX()+.30,s.getY(),s.getZ());
            h.assertTrue(!MotherNestStance.active(s),"real displacement retires without position correction");
            h.assertTrue(toggle(f,s)==SkillResult.SUCCESS,"reenter new position");s.setOwnerName(f.enemy);s.setOwnerName(f.owner);
            h.assertTrue(!MotherNestStance.active(s),"owner ABA does not revive stance");
            h.assertTrue(toggle(f,s)==SkillResult.SUCCESS,"enter for save");CompoundTag saved=new CompoundTag();s.addAdditionalSaveData(saved);s.readAdditionalSaveData(saved);
            h.assertTrue(!MotherNestStance.active(s),"actual RTS NBT reload retires transient stance");
            h.assertTrue(s.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD)==0&&f.shadow()==500,"no phantom summon cooldown or charges");h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_advanced_mother_hands",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void stationedMotherCanUseFreeBroodWithoutLeavingNest(GameTestHelper h){
        var f=fixture(h,"NestBrood");var s=mother(f);unlock(f,AdvancedUnitResearch.MOTHER_NEST_ID);
        h.startSequence().thenIdle(25).thenExecute(()->{h.assertTrue(toggle(f,s)==SkillResult.SUCCESS,"nest active");
            long revision=s.rts().skills().revision();
            var first=new SkillNetwork.Cast(UnitSkill.MOTHER_BROOD,s.getId(),s.getUUID(),revision,s.getId(),s.getUUID()).execute(f.peer.actor);
            int used=com.solegendary.reignofnether.unit.UnitServerEvents.getCurrentPopulation(f.owner);
            int supply=com.solegendary.reignofnether.building.BuildingServerEvents.getTotalPopulationSupply(f.owner);
            System.out.println("API54_NEST_BROOD_BEFORE_SUPPLY result="+first+" used="+used+" supply="+supply+" owner="+f.owner+" position="+s.position());
            h.assertTrue(first==SkillResult.CAP_REACHED&&supply==0,"actual no-supply population gate, result="+first+" used="+used+" supply="+supply);
            h.assertTrue(MotherNestStance.active(s)&&f.shadow()==500&&s.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD)==0&&s.rts().skills().revision()==revision,
                "failed population admission preserves stance and charges no shadow/cooldown/revision");
            h.assertTrue(com.dreamseekers.summon.BoundedSummons.activeCount(f.owner)==0,"rejected group publishes no summon lease");
            installNestBroodPopulationAnchor(f);
        }).thenIdle(5).thenExecute(()->{
            int used=com.solegendary.reignofnether.unit.UnitServerEvents.getCurrentPopulation(f.owner);
            int supply=com.solegendary.reignofnether.building.BuildingServerEvents.getTotalPopulationSupply(f.owner);
            h.assertTrue(supply>=used+4&&!ResearchServerEvents.playerHasCheat(f.owner,"foodforthought"),"real completed population supply, no cheat");
            var result=new SkillNetwork.Cast(UnitSkill.MOTHER_BROOD,s.getId(),s.getUUID(),s.rts().skills().revision(),s.getId(),s.getUUID()).execute(f.peer.actor);
            System.out.println("API54_NEST_BROOD_AFTER_SUPPLY result="+result+" used="+used+" supply="+supply+" owner="+f.owner+" position="+s.position());
            h.assertTrue(result==SkillResult.SUCCESS,"stationary mother summons with real physical supply: "+result);
            h.assertTrue(MotherNestStance.active(s)&&f.shadow()==500&&s.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD)==900,"no shadow debit, unchanged 45-second cooldown, and stance remains");
            h.assertTrue(h.getLevel().getEntitiesOfClass(BeetleUnit.class,s.getBoundingBox().inflate(12),b->com.dreamseekers.summon.BoundedSummons.isTemporary(b)&&b.getOwnerName().equals(f.owner)).size()==2,"exact two genuine temporary beetles");h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_advanced_mother_hands",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=130)
    public static void nestExtendsOnlyMotherAuraAndRetiresExtendedRecipientImmediately(GameTestHelper h){
        var f=fixture(h,"NestAura");var s=mother(f);var beetle=f.add(DreamseekersMod.BEETLE.get().create(h.getLevel()),f.owner,7,0);
        unlock(f,AdvancedUnitResearch.MOTHER_NEST_ID);
        h.startSequence().thenIdle(30).thenExecute(()->{near(h,SkillEffects.bonus(beetle,BuffKind.ATTACK_SPEED),0,"mobile original radius6");h.assertTrue(toggle(f,s)==SkillResult.SUCCESS,"nest enters");})
        .thenIdle(12).thenExecute(()->{near(h,SkillEffects.bonus(beetle,BuffKind.ATTACK_SPEED),.15,"actual radius7 recipient gets unchanged15percent");near(h,beetle.getAttacksPerSecond(),.8*1.15,"real APS attribute consumer");
            s.setMoveTarget(s.blockPosition().offset(-5,0,0));near(h,SkillEffects.bonus(beetle,BuffKind.ATTACK_SPEED),0,"same-tick command invalidates expanded lease");near(h,beetle.getAttacksPerSecond(),.8,"no stale attribute until next aura tick");h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_advanced_mother_hands",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=160)
    public static void nestNaturalMeleeRemainsStationaryAndDoesNotRestorePriorPath(GameTestHelper h){
        var f=fixture(h,"NestMelee");var s=mother(f);var enemy=f.target(3.5,0);unlock(f,AdvancedUnitResearch.MOTHER_NEST_ID);s.setNoAi(false);
        net.minecraft.world.phys.Vec3[] position={null};
        h.startSequence().thenIdle(25).thenExecute(()->{h.assertTrue(toggle(f,s)==SkillResult.SUCCESS,"stationary stance starts");position[0]=s.position();})
        .thenIdle(70).thenExecute(()->{h.assertTrue(enemy.getHealth()<1000,"ordinary natural melee goal still damages legal nearby target");h.assertTrue(MotherNestStance.active(s)&&s.position().distanceToSqr(position[0])<.0625,"no autonomous pursuit");
            h.assertTrue(toggle(f,s)==SkillResult.SUCCESS&&!MotherNestStance.active(s),"manual release");h.assertTrue(s.getMoveGoal().getMoveTarget()==null,"no old path restored on release");h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_advanced_mother_hands",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=170)
    public static void harmonyNeedsOppositeResearchAndLiveRangeNeverStacksOrChangesSkillCost(GameTestHelper h){
        var f=fixture(h,"Harmony");var left=f.hand(true);var right=f.add(DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel()),f.owner,7,0);
        var second=f.add(DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel()),f.owner,-7,0);
        h.startSequence().thenIdle(30).thenExecute(()->{near(h,SkillEffects.bonus(left.mob(),BuffKind.ATTACK_SPEED),0,"no research no free harmony");near(h,left.getAttacksPerSecond(),.5,"real left baseline APS");near(h,right.getAttacksPerSecond(),.5,"real right baseline APS");unlock(f,AdvancedUnitResearch.HAND_HARMONY_ID);})
        .thenIdle(12).thenExecute(()->{near(h,SkillEffects.bonus(left.mob(),BuffKind.ATTACK_SPEED),.10,"two opposite candidates still one10percent");near(h,SkillEffects.bonus(right,BuffKind.ATTACK_SPEED),.10,"both sides actually buffed");near(h,left.getAttacksPerSecond(),.55,"real left APS consumes harmony");near(h,right.getAttacksPerSecond(),.55,"real right APS consumes harmony");near(h,left.getAttackCooldown(),20/.55,"actual native attack cooldown getter consumes one10percent boost");
            h.assertTrue(SkillEffects.addBuff(left,left.mob(),"test_harmony_stronger",BuffKind.ATTACK_SPEED,.20,40),"existing stronger category positive control");near(h,SkillEffects.bonus(left.mob(),BuffKind.ATTACK_SPEED),.20,"max not additive");near(h,left.getAttacksPerSecond(),.6,"real APS takes20percent maximum, not30percent sum");
            right.moveTo(right.getX()+5,right.getY(),right.getZ());second.setOwnerName(f.enemy);second.setOwnerName(f.owner);second.moveTo(second.getX()-5,second.getY(),second.getZ());
            near(h,SkillEffects.bonus(right,BuffKind.ATTACK_SPEED),0,"range exit immediately revokes target buff");near(h,right.getAttacksPerSecond(),.5,"same-tick real APS has no stale harmony modifier");h.assertTrue(!AaroncosHarmony.active(left),"no opposite within8 after both exits");})
        .thenIdle(42).thenExecute(()->{near(h,SkillEffects.bonus(left.mob(),BuffKind.ATTACK_SPEED),0,"expiry leaves no stale harmony");near(h,left.getAttacksPerSecond(),.5,"real left APS returns to unmodified baseline");h.assertTrue(f.shadow()==500&&left.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==0&&right.rts().skills().cooldownTicks(UnitSkill.AARONCOS_RIGHT_SHOCK)==0,"passive neither pays nor changes independent skill cooldowns");h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_advanced_mother_hands",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void harmonyWallPartnerDeathAndForeignOwnerAreImmediateBoundaries(GameTestHelper h){
        var f=fixture(h,"HarmonyWall");var left=f.hand(true);var right=f.add(DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel()),f.owner,7,0);unlock(f,AdvancedUnitResearch.HAND_HARMONY_ID);
        h.startSequence().thenIdle(30).thenExecute(()->{near(h,SkillEffects.bonus(left.mob(),BuffKind.ATTACK_SPEED),.10,"open LOS positive");
            for(BlockPos p:BlockPos.betweenClosed(f.center.offset(3,0,-3),f.center.offset(3,7,3)))h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
            near(h,SkillEffects.bonus(left.mob(),BuffKind.ATTACK_SPEED),0,"real wall immediately removes guarded contribution");
            for(BlockPos p:BlockPos.betweenClosed(f.center.offset(3,0,-3),f.center.offset(3,7,3)))h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),2);})
        .thenIdle(12).thenExecute(()->{near(h,SkillEffects.bonus(left.mob(),BuffKind.ATTACK_SPEED),.10,"new lawful discovery after wall removed");right.setOwnerName(f.enemy);
            near(h,SkillEffects.bonus(left.mob(),BuffKind.ATTACK_SPEED),0,"foreign owner cannot retain lease");right.setOwnerName(f.owner);})
        .thenIdle(12).thenExecute(()->{near(h,SkillEffects.bonus(left.mob(),BuffKind.ATTACK_SPEED),.10,"fresh discovery independent of old lease");right.discard();near(h,SkillEffects.bonus(left.mob(),BuffKind.ATTACK_SPEED),0,"removed exact partner revokes immediately");h.succeed();});
    }
}
