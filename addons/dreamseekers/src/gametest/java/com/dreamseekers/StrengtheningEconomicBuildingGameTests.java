// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.economy.*;
import com.dreamseekers.neutralcontract.NeutralContracts;
import com.dreamseekers.skill.*;
import com.dreamseekers.summon.BoundedSummons;
import com.dreamseekers.unit.*;
import com.dreamseekers.unitprogression.UnitBalanceMigration;
import com.dreamseekers.unitprogression.UnitBalanceMigration.Profile;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Narrow real-world consumers for API52. Building work is the original installed AI goal;
 * entity economics uses explicitly controlled ordinary doHurtTarget calls and actual deferred
 * death/claim events, NOT an assertion of autonomous melee timing. Legacy NBT is intentionally
 * constructed from real current save envelopes; it is not an API51-binary world archive.
 */
@GameTestHolder("dreamseekers_strengthening_economy")
@PrefixGameTestTemplate(false)
public final class StrengtheningEconomicBuildingGameTests {
    private static final String BATCH="strengthening_economy";
    private static final List<AaroncosSkillCombatGameTests.F> FIXTURES=new ArrayList<>();
    private static final List<NeutralContractGameTests.F> CONTRACTS=new ArrayList<>();
    private static final List<BudgetPlacement> BUILDINGS=new ArrayList<>();
    private static final List<Deaths> PROBES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        for(var probe:PROBES)MinecraftForge.EVENT_BUS.unregister(probe);PROBES.clear();
        for(var b:BUILDINGS){b.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(b);}BUILDINGS.clear();
        for(var f:List.copyOf(CONTRACTS))f.close();CONTRACTS.clear();
        for(var f:List.copyOf(FIXTURES)){ResearchServerEvents.removeAllResearchFor(f.owner);f.close();}FIXTURES.clear();
    }
    private static AaroncosSkillCombatGameTests.F fixture(GameTestHelper h,String name) {
        var f=new AaroncosSkillCombatGameTests.F(h,"API52_Economy_"+name);FIXTURES.add(f);return f;
    }
    private static void close(GameTestHelper h,double actual,double expected,String why) {
        h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.0001,why+" actual="+actual+" expected="+expected);
    }
    private static Mob create(GameTestHelper h,Profile profile) {
        return switch(profile){case MOTHER->DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel());case LEFT->DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(h.getLevel());case RIGHT->DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel());};
    }
    private static UnitBalanceMigration.Result migration(Mob body) {
        if(body instanceof BeetleMotherUnit mother)return mother.balanceMigrationResult();
        if(body instanceof AaroncosLeftHandUnit left)return left.balanceMigrationResult();
        return ((AaroncosRightHandUnit)body).balanceMigrationResult();
    }
    private static double damage(Profile p){return p==Profile.MOTHER?14:p==Profile.LEFT?26:24;}
    private static double health(Profile p){return p==Profile.MOTHER?200:p==Profile.LEFT?450:420;}
    private static long cost(Profile p){return p==Profile.MOTHER?320:1000;}
    private static void price(GameTestHelper h,Mob body,Profile p) {
        var c=((Unit)body).getCost();
        h.assertTrue(c.food==(p==Profile.MOTHER?180:400)&&c.wood==(p==Profile.MOTHER?0:250)
                &&c.ore==(p==Profile.MOTHER?140:350)&&c.population==(p==Profile.MOTHER?4:8)
                &&c.ticks==(p==Profile.MOTHER?720:1440),"unchanged exact recipe metadata "+p);
    }
    private record Budget(long at,double amount,double unit,double beforePartial,double afterPartial,int beforeBlocks,int afterBlocks,boolean nativeGoal) { }
    private static final class BudgetPlacement extends BuildingPlacement {
        final List<Budget> calls=new ArrayList<>();int callbacks;
        BudgetPlacement(Level level,BlockPos origin,String owner,ArrayList<BuildingBlock> blocks) {
            super(DreamseekersMod.BEETLE_SANCTUARY,level,origin,Rotation.NONE,owner,blocks,false);
        }
        @Override public void destroyRandomBlocks(double amount) {
            double unit=getHealthPerBlock()/2d,beforePartial=partialBlocksDestroyed;int before=getBlocksPlaced();
            boolean nativeGoal=Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(f->f.getClassName().equals("com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal")&&f.getMethodName().equals("doBuildingAttack"));
            super.destroyRandomBlocks(amount);
            calls.add(new Budget(level.getGameTime(),amount,unit,beforePartial,partialBlocksDestroyed,before,getBlocksPlaced(),nativeGoal));
        }
        @Override public void onBlockBreak(ServerLevel level,BlockPos pos,boolean physical){callbacks++;super.onBlockBreak(level,pos,physical);}
    }
    private static BudgetPlacement target(AaroncosSkillCombatGameTests.F f,int z) {
        BlockPos origin=f.center.offset(3,0,z);var blocks=new ArrayList<BuildingBlock>();
        for(int x=0;x<8;x++)for(int dz=0;dz<8;dz++){
            BlockPos p=origin.offset(x,0,dz);f.h.getLevel().setBlock(p,Blocks.STONE_BRICKS.defaultBlockState(),2);blocks.add(new BuildingBlock(p,Blocks.STONE_BRICKS.defaultBlockState()));
        }
        var result=new BudgetPlacement(f.h.getLevel(),origin,f.enemy,blocks);BUILDINGS.add(result);BuildingServerEvents.getBuildings().add(result);result.onBuilt();return result;
    }
    @GameTest(templateNamespace="dreamseekers_strengthening_economy",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=600)
    public static void threeStrengthenedProfilesFeedExactNaturalOrdinaryBuildingBudgets(GameTestHelper h) {
        var f=fixture(h,"building");List<Mob> actors=new ArrayList<>();List<BudgetPlacement> targets=new ArrayList<>();int[] zs={-12,-2,8};
        for(Profile profile:Profile.values()){
            int z=zs[actors.size()];Mob actor=f.add(create(h,profile),f.owner,.65,z+2);actors.add(actor);targets.add(target(f,z));
        }
        h.startSequence().thenIdle(30).thenExecute(()->{
            for(int i=0;i<actors.size();i++){
                Mob body=actors.get(i);var attacker=(AttackerUnit)body;var building=targets.get(i);Profile profile=Profile.values()[i];price(h,body,profile);
                close(h,body.getMaxHealth(),health(profile),"fresh registered HP");close(h,attacker.getUnitAttackDamage(),damage(profile),"unchanged fresh AD before actual goal");
                h.assertTrue(h.getLevel().noCollision(body)&&building.getBlocksPlaced()==64,"real nonclipping body and64 registered physical blocks");
                ((Unit)body).setHoldPosition(true);attacker.setAttackBuildingTarget(building.originPos,true);
                var goal=(TeamSafeBuildingAttackGoal)attacker.getAttackBuildingGoal();h.assertTrue(goal.getBuildingTarget()==building&&goal.isAttacking(),"original installed goal targets real in-range enemy placement");
                body.setNoAi(false); // Natural installed AI now consumes its original cooldown. Never call tick/doBuildingAttack or edit CD.
            }
        }).thenWaitUntil(()->{
            for(int i=0;i<actors.size();i++)h.assertTrue(targets.get(i).calls.size()>=3&&targets.get(i).callbacks>0,"natural repeated original goal and real physical building write lane "+i);
        }).thenExecute(()->{
            for(int i=0;i<actors.size();i++){
                Mob body=actors.get(i);body.setNoAi(true);var attacker=(AttackerUnit)body;var building=targets.get(i);Profile profile=Profile.values()[i];
                double expected=damage(profile)*building.getMeleeDamageMult()*attacker.getBuildingDamageMultiplier();
                for(Budget call:building.calls){h.assertTrue(call.nativeGoal,"only original ordinary building goal supplied the write");close(h,call.amount,expected,"exact new AD reaches actual native budget "+profile);
                    close(h,(call.beforeBlocks-call.afterBlocks)+(call.afterPartial-call.beforePartial),call.amount/call.unit,"native fraction plus actual blocks conserves supplied budget");}
                h.assertTrue(building.getBlocksPlaced()<64&&building.lastAttacker==body,"real original body attribution and block loss");
                h.assertTrue(building.getBlocks().stream().filter(b->!b.isPlaced(h.getLevel())).count()==64-building.getBlocksPlaced(),"native counted loss equals actual world blocks");
                ((TeamSafeBuildingAttackGoal)attacker.getAttackBuildingGoal()).stopAttacking();
                System.out.println("API52_REAL_ORDINARY_BUILDING_BUDGET profile="+profile+" uuid="+body.getUUID()+" calls="+building.calls);
            }
            h.assertTrue(f.shadow()==500&&f.account.food==1000&&f.account.wood==1000&&f.account.ore==1000,"ordinary building work neither casts nor spends resources");h.succeed();
        });
    }
    public static final class Deaths {
        final Set<UUID> watched=new HashSet<>();final Map<UUID,List<ConfirmedRtsKillEvent.Snapshot>> seen=new HashMap<>();
        @SubscribeEvent(priority=EventPriority.LOWEST) public void observe(ConfirmedRtsKillEvent e){if(watched.contains(e.snapshot().victimId()))seen.computeIfAbsent(e.snapshot().victimId(),ignored->new ArrayList<>()).add(e.snapshot());}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void attack(net.minecraftforge.event.entity.living.LivingAttackEvent e){if(watched.contains(e.getEntity().getUUID()))trace("attack",e.getEntity(),e.getSource(),e.getAmount(),e.isCanceled());}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e){if(watched.contains(e.getEntity().getUUID()))trace("hurt",e.getEntity(),e.getSource(),e.getAmount(),e.isCanceled());}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void damage(net.minecraftforge.event.entity.living.LivingDamageEvent e){if(watched.contains(e.getEntity().getUUID()))trace("damage",e.getEntity(),e.getSource(),e.getAmount(),e.isCanceled());}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void death(net.minecraftforge.event.entity.living.LivingDeathEvent e){if(watched.contains(e.getEntity().getUUID()))trace("death",e.getEntity(),e.getSource(),0,e.isCanceled());}
        private static void trace(String stage,LivingEntity victim,net.minecraft.world.damagesource.DamageSource source,float amount,boolean canceled){System.out.println("API52_ECONOMIC_FATAL_EVENT stage="+stage+" at="+victim.level().getGameTime()+" victim="+victim.getType()+"/"+victim.getUUID()+" source="+source+" cause="+(source.getEntity()==null?null:source.getEntity().getUUID())+" amount="+amount+" canceled="+canceled+" hp="+victim.getHealth());}
        void watch(Entity e){watched.add(e.getUUID());}
    }
    private static Deaths deaths(){var probe=new Deaths();PROBES.add(probe);MinecraftForge.EVENT_BUS.register(probe);return probe;}
    private static CompoundTag attribute(CompoundTag root,Attribute attribute) {
        String name=BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString();for(Tag raw:root.getList("Attributes",Tag.TAG_COMPOUND))if(raw instanceof CompoundTag entry&&entry.getString("Name").equals(name))return entry;
        throw new AssertionError("missing native attribute "+name);
    }
    private static Mob legacy(AaroncosSkillCombatGameTests.F f,Profile profile,int x,int z,boolean claimed) {
        Mob seed=create(f.h,profile);((Unit)seed).setOwnerName(f.enemy);RtsKillValueClaims.valueId(seed);
        // Keep the real save envelope: materialize existing registered defaults, without changing a Base.
        for(Attribute a:List.of(Attributes.MAX_HEALTH,Attributes.ATTACK_DAMAGE,AttributeRegistrar.ATTACK_DAMAGE.get(),Attributes.ARMOR))f.h.assertTrue(seed.getAttribute(a)!=null,"registered attribute before native save");
        CompoundTag tag=seed.saveWithoutId(new CompoundTag());seed.discard();tag.remove(UnitBalanceMigration.KEY);tag.putShort("DeathTime",(short)0);
        double oldHp=profile==Profile.MOTHER?160:350,oldAd=profile==Profile.MOTHER?12:20,oldArmor=profile==Profile.MOTHER?3:6;
        attribute(tag,Attributes.MAX_HEALTH).putDouble("Base",oldHp);attribute(tag,Attributes.ATTACK_DAMAGE).putDouble("Base",oldAd);
        attribute(tag,AttributeRegistrar.ATTACK_DAMAGE.get()).putDouble("Base",oldAd);attribute(tag,Attributes.ARMOR).putDouble("Base",oldArmor);tag.putFloat("Health",(float)(oldHp/2));
        if(claimed){CompoundTag claim=new CompoundTag();claim.putString("outcome","committed");tag.getCompound("ForgeData").getCompound(RtsKillValueClaims.KEY).put("claim",claim);}
        Tag origin=tag.getCompound("ForgeData").get(RtsKillValueClaims.KEY).copy();Mob loaded=create(f.h,profile);loaded.load(tag);
        f.h.assertTrue(migration(loaded)==UnitBalanceMigration.Result.MIGRATED,"real native load migrates recognized profile before death");close(f.h,loaded.getHealth(),health(profile)/2,"injured migrated ratio before explicit fatal fixture");
        f.h.assertTrue(origin.equals(loaded.getPersistentData().get(RtsKillValueClaims.KEY)),"migration preserves exact prior economic origin/claim");price(f.h,loaded,profile);
        return f.add(loaded,f.enemy,x,z);
    }
    private static boolean publishedAndTicked(Mob body) {
        var level=(ServerLevel)body.level();
        return body.isAlive()&&!body.isRemoved()&&body.isAddedToWorld()&&body.tickCount>3
                &&level.getEntity(body.getId())==body&&level.getEntity(body.getUUID())==body;
    }
    private static String fatalState(Mob body) {
        var level=(ServerLevel)body.level();
        return "type="+body.getType()+" uuid="+body.getUUID()+" id="+body.getId()+" owner="+((Unit)body).getOwnerName()
                +" hp="+body.getHealth()+" max="+body.getMaxHealth()+" hurtTime="+body.hurtTime+" iframe="+body.invulnerableTime
                +" tick="+body.tickCount+" alive="+body.isAlive()+" removed="+body.isRemoved()+" added="+body.isAddedToWorld()+" idSame="+(level.getEntity(body.getId())==body)+" uuidSame="+(level.getEntity(body.getUUID())==body)
                +" invulnerable="+body.isInvulnerable()+" noAI="+body.isNoAi()+" pos="+body.position()+" velocity="+body.getDeltaMovement()+" effects="+body.getActiveEffects();
    }
    private static void fatal(GameTestHelper h,Mob killer,Mob victim) {
        h.assertTrue(killer.level()==h.getLevel()&&victim.level()==h.getLevel()&&publishedAndTicked(killer)&&publishedAndTicked(victim),
                "original killer and victim remain live indexed ticking objects before real death: killer={"+fatalState(killer)+"} victim={"+fatalState(victim)+"}");
        // Explicit in-range fixture placement; this test measures death economics, not travel or cadence.
        killer.moveTo(victim.getX()-4,victim.getY(),victim.getZ());
        System.out.println("API52_ECONOMIC_FATAL_BEFORE at="+h.getLevel().getGameTime()+" killer={"+fatalState(killer)+"} victim={"+fatalState(victim)+"} los="+killer.hasLineOfSight(victim)+" distanceSquared="+killer.distanceToSqr(victim));
        victim.setHealth(1);
        boolean accepted=killer.doHurtTarget(victim);
        System.out.println("API52_ECONOMIC_FATAL_AFTER at="+h.getLevel().getGameTime()+" accepted="+accepted+" victim={"+fatalState(victim)+"}");
        h.assertTrue(accepted&&victim.isDeadOrDying(),"actual controlled ordinary attack causes real fatal native lifecycle: type="+victim.getType()+" uuid="+victim.getUUID()+" accepted="+accepted+" HP="+victim.getHealth());
    }
    private static void paidDeaths(GameTestHelper h,Deaths probe,List<Mob> victims,boolean researched) {
        for(int i=0;i<victims.size();i++){
            Mob victim=victims.get(i);var events=probe.seen.getOrDefault(victim.getUUID(),List.of());
            h.assertTrue(events.size()==1&&events.get(0).totalCost()==cost(Profile.values()[i])&&events.get(0).hadResearch(DreamEconomyResearch.CONVERSION_ID)==researched,
                    "one confirmed real death uses unchanged cost and exact research snapshot "+i);
            h.assertTrue(RtsKillValueClaims.isClaimed(victim),"actual faction consumer permanently claims migrated value once");
        }
    }
    @GameTest(templateNamespace="dreamseekers_strengthening_economy",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void migratedRealDeathsKeepStandardValueResearchRateAndRedeemedLineage(GameTestHelper h) {
        var f=fixture(h,"deaths");var killer=f.hand(true).mob();f.target(-12,12); // Genuine living enemy reserve; no defeat-state suppression.
        List<Mob> plain=new ArrayList<>(),researched=new ArrayList<>();var probe=deaths();
        for(Profile p:Profile.values()){
            int i=plain.size();var a=legacy(f,p,4+i*4,-8,false);var b=legacy(f,p,4+i*4,8,false);plain.add(a);researched.add(b);probe.watch(a);probe.watch(b);
        }
        var redeemed=legacy(f,Profile.MOTHER,12,0,true);probe.watch(redeemed);Tag originalClaim=redeemed.getPersistentData().get(RtsKillValueClaims.KEY).copy();
        List<Mob> originals=new ArrayList<>();originals.add(killer);originals.addAll(plain);originals.addAll(researched);originals.add(redeemed);
        long[] readySince={-1};
        h.startSequence().thenWaitUntil(()->{
            long now=h.getLevel().getGameTime();boolean ready=originals.stream().allMatch(StrengtheningEconomicBuildingGameTests::publishedAndTicked);
            if(!ready)readySince[0]=-1;else if(readySince[0]<0)readySince[0]=now;
            h.assertTrue(ready&&now-readySince[0]>=3,"all original killer/seven victims naturally published and ticking for four consecutive observations: at="+now+" since="+readySince[0]+" objects="+originals.stream().map(StrengtheningEconomicBuildingGameTests::fatalState).toList());
        }).thenExecute(()->{
            System.out.println("API52_ECONOMIC_ORIGINALS_READY at="+h.getLevel().getGameTime()+" since="+readySince[0]+" objects="+originals.stream().map(StrengtheningEconomicBuildingGameTests::fatalState).toList());
            for(var victim:plain)fatal(h,killer,victim);
        })
          .thenIdle(5).thenExecute(()->{
              paidDeaths(h,probe,plain,false);h.assertTrue(f.shadow()==616,"actual original five-percent rates:16+50+50, not raised HP/AD");
              ResearchServerEvents.addResearch(f.owner,DreamEconomyResearch.CONVERSION_ID); // Explicit research fixture; paid purchase is covered by dream_research.
              for(var victim:researched)fatal(h,killer,victim);
          }).thenIdle(5).thenExecute(()->{
              paidDeaths(h,probe,researched,true);h.assertTrue(f.shadow()==848,"actual researched ten-percent rates:32+100+100");fatal(h,killer,redeemed);
          }).thenIdle(5).thenExecute(()->{
              h.assertTrue(f.shadow()==848&&probe.seen.getOrDefault(redeemed.getUUID(),List.of()).isEmpty(),"already redeemed migrated lineage cannot pay again on real death");
              h.assertTrue(originalClaim.equals(redeemed.getPersistentData().get(RtsKillValueClaims.KEY)),"prior redeemed lineage remains byte-equivalent data after death");
              h.assertTrue(f.account.food==1000&&f.account.wood==1000&&f.account.ore==1000&&f.account.conversionRemainders.getOrDefault(DreamShadowKillIncome.REMAINDER,0L)==0,"only exact shadow awards, no principal or fractional drift");
              System.out.println("API52_MIGRATED_REAL_DEATH_VALUE_PASS base=16,50,50 researched=32,100,100 redeemed=0");h.succeed();
          });
    }
    @GameTest(templateNamespace="dreamseekers_strengthening_economy",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=850)
    public static void realPaidSummonAndCompletedContractVictimsNeverPayButOrdinaryPeerDoes(GameTestHelper h) {
        var f=new NeutralContractGameTests.F(h,"API52_FreeVictim");CONTRACTS.add(f);
        var worker=f.worker(2,-15,true);var nativeBody=f.nativeBody(false,11,-15);
        var mother=DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel());mother.setOwnerName(f.owner);mother.setNoAi(true);f.place(mother,-12,5);
        String killerOwner=f.owner+"_killer";PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(killerOwner,DreamseekersMod.DREAMSEEKERS));var bank=new Resources(killerOwner,1000,1000,1000);bank.extra.put(DreamPassiveEconomy.SHADOW,0);ResourcesServerEvents.resourcesList.add(bank);
        var killer=DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(h.getLevel());killer.setOwnerName(killerOwner);killer.setNoAi(true);killer.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);killer.moveTo(f.origin.getX()-16+.5,f.origin.getY()+1,f.origin.getZ()+15+.5);h.assertTrue(h.getLevel().addFreshEntity(killer),"real independent hostile killer");
        f.cleanupActions.add(()->{killer.discard();ResearchServerEvents.removeAllResearchFor(killerOwner);ResourcesServerEvents.resourcesList.removeIf(r->r==bank);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(killerOwner));});
        var probe=deaths();List<Mob> free=new ArrayList<>();List<Tag> origins=new ArrayList<>();Mob[] ordinary={null};
        h.startSequence().thenWaitUntil(()->f.ready(worker,nativeBody,mother)).thenExecute(()->h.assertTrue(f.start(worker,nativeBody).equals("approaching"),"actual worker begins normal paid contract"))
          .thenWaitUntil(()->h.assertTrue(f.converted().size()==1,"real unshortened channel produces exactly one contract beetle"))
          .thenExecute(()->{
              Mob contracted=f.converted().get(0);h.assertTrue(contracted instanceof BeetleUnit&&nativeBody.isRemoved()&&!NeutralContracts.pending(contracted)&&RtsKillValueClaims.isFree(contracted),"real completed contract provenance");
              h.assertTrue(f.account.food==950&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==485,"real contract paid50F15S once");
              h.assertTrue(SkillService.request(f.peer.actor,new SkillRequest(UnitSkill.MOTHER_BROOD,mother.getId(),mother.getUUID(),mother.rts().skills().revision(),mother.getId(),mother.getUUID()))==SkillResult.SUCCESS,"real paid mother summon rather than injected temporary marker");
              var children=UnitServerEvents.getAllUnits().stream().filter(e->e instanceof Mob&&e instanceof Unit u&&u.getOwnerName().equals(f.owner)&&BoundedSummons.isTemporary(e)).map(e->(Mob)e).toList();
              h.assertTrue(children.size()==2&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==455,"exact genuine two-child group and30S payment");free.add(contracted);free.addAll(children);
              for(var body:free){body.setNoAi(true);probe.watch(body);h.assertTrue(RtsKillValueClaims.isFree(body),"exact genuine free-origin victim");origins.add(body.getPersistentData().get(RtsKillValueClaims.FREE_ORIGIN_KEY).copy());}
              var normal=DreamseekersMod.BEETLE.get().create(h.getLevel());normal.setOwnerName(f.owner);normal.setNoAi(true);normal.moveTo(f.origin.getX()-7+.5,f.origin.getY()+1,f.origin.getZ()+5+.5);h.assertTrue(h.getLevel().addFreshEntity(normal),"ordinary same-type non-free comparator");f.bodies.add(normal);ordinary[0]=normal;probe.watch(normal);
              killer.moveTo(f.origin.getX()-10+.5,f.origin.getY()+1,f.origin.getZ()-15+.5);
          }).thenIdle(5).thenExecute(()->{for(var victim:free)fatal(h,killer,victim);})
          .thenIdle(5).thenExecute(()->{
              h.assertTrue(bank.extra.get(DreamPassiveEconomy.SHADOW)==0&&bank.conversionRemainders.getOrDefault(DreamShadowKillIncome.REMAINDER,0L)==0,"actual deaths of true summon/contract victims pay neither integer nor fractional income");
              for(int i=0;i<free.size();i++)h.assertTrue(probe.seen.getOrDefault(free.get(i).getUUID(),List.of()).isEmpty()&&origins.get(i).equals(free.get(i).getPersistentData().get(RtsKillValueClaims.FREE_ORIGIN_KEY)),"free lineage never emits redeemable death and is not erased");
              h.assertTrue(!RtsKillValueClaims.isFree(ordinary[0]),"registered ordinary comparator is genuinely not free");fatal(h,killer,ordinary[0]);
          }).thenIdle(5).thenExecute(()->{
              var events=probe.seen.getOrDefault(ordinary[0].getUUID(),List.of());h.assertTrue(events.size()==1&&events.get(0).totalCost()==95&&RtsKillValueClaims.isClaimed(ordinary[0]),"same native death path accepts ordinary95-cost comparator exactly once");
              h.assertTrue(bank.extra.get(DreamPassiveEconomy.SHADOW)==4&&bank.conversionRemainders.getOrDefault(DreamShadowKillIncome.REMAINDER,0L)==7500,"positive comparator proves live faction consumer and exact4.75 conversion");
              h.assertTrue(f.account.food==950&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==455,"free deaths do not refund actual contract/summon costs");
              System.out.println("API52_TRUE_FREE_VICTIM_DEATH_PASS contract=0 summons=0 ordinary95=4+7500/10000");h.succeed();
          });
    }
}
