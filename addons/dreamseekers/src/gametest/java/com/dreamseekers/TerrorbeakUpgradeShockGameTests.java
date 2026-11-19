package com.dreamseekers;

import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.shadow.TerrorbeakUpgradeRequest;
import com.dreamseekers.shadow.TerrorbeakUpgradeService;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** One unchanged real-impact fixture for the legacy observation and the strict regression.
 * Test-only environment switch changes expected outcomes, never product behavior or timers.
 * No direct damage, suppression installation, hurtTime write, game-time write or manual tick.
 */
@GameTestHolder("dreamseekers_terrorbeak_upgrade")
@PrefixGameTestTemplate(false)
public final class TerrorbeakUpgradeShockGameTests {
    private static final String BATCH="terrorbeak_upgrade_shock";
    private static final List<F> FIXTURES=new ArrayList<>();
    private static final class F implements AutoCloseable {
        final AaroncosSkillCombatGameTests.F arena;
        final PassiveIncomePeer defender;
        final Resources account;
        final List<CrazyTerrorbeakUnit> published=new ArrayList<>();
        final Consumer<EntityJoinLevelEvent> observer;
        boolean closed;
        F(GameTestHelper h) {
            arena=new AaroncosSkillCombatGameTests.F(h,"upgrade_shock");
            account=ExtendedResourcesSync.account(arena.enemy);
            h.assertTrue(account!=null,"real defender account exists");
            account.extra.put(DreamPassiveEconomy.SHADOW,500); // initial fixture budget, never a refund
            defender=new PassiveIncomePeer(h.getLevel(),arena.enemy);
            observer=event->{
                if(event.getLevel()==h.getLevel()&&event.getEntity() instanceof CrazyTerrorbeakUnit body
                        &&body.getOwnerName().equals(arena.enemy))published.add(body);
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,observer);
            FIXTURES.add(this);
        }
        List<CrazyTerrorbeakUnit> replacements() {
            return UnitServerEvents.getAllUnits().stream()
                    .filter(e->e instanceof CrazyTerrorbeakUnit body&&body.getOwnerName().equals(arena.enemy))
                    .map(e->(CrazyTerrorbeakUnit)e).toList();
        }
        TerrorbeakUpgradeService.Result upgrade(TerrorbeakUnit source) {
            return TerrorbeakUpgradeService.request(defender.actor,new TerrorbeakUpgradeRequest(1,UUID.randomUUID(),
                    new TerrorbeakUpgradeRequest.Source(source.getId(),source.getUUID(),source.rts().ownershipRevision,
                            arena.h.getLevel().dimension().location()),TerrorbeakUpgradeRequest.UpgradeTarget.CRAZY_TERRORBEAK));
        }
        void unspent() {
            arena.h.assertTrue(account.food==1000&&account.wood==1000&&account.ore==1000
                    &&account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==500,"defender's full original budget remains");
        }
        @Override public void close() {
            if(closed)return;closed=true;
            MinecraftForge.EVENT_BUS.unregister(observer);
            // Includes an accepted replacement and any rollback publication witness, not unrelated units.
            for(var body:List.copyOf(published))if(!body.isRemoved())body.discard();
            defender.close();arena.close();FIXTURES.remove(this);
        }
        void done() { close();arena.h.succeed(); }
    }
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        for(var fixture:List.copyOf(FIXTURES))fixture.close();
    }
    private static boolean legacyMode(GameTestHelper h) {
        String value=System.getenv("API52_UPGRADE_SHOCK_EXPECT_LEGACY_WASH");
        h.assertTrue(value==null||value.equals("1"),"test-only legacy observation flag must be absent or exactly 1");
        return "1".equals(value);
    }
    private static void otherEffectsClear(GameTestHelper h,TerrorbeakUnit source) {
        h.assertTrue(source.getActiveEffects().isEmpty()&&!source.isOnFire()&&source.getTicksFrozen()==0
                &&!source.isPassenger()&&!source.isVehicle()&&source.hurtTime==0&&source.fallDistance<=0
                &&source.getAirSupply()>=source.getMaxAirSupply()&&!source.isCasting()
                &&ShadowCorrosionTouch.negative(source)==null&&SquealSuppressionWave.negative(source)==null
                &&TerrorbeakDreadRoar.negative(source)==null&&ShadowHandGrip.negative(source)==null
                &&FriendlyDebtContract.negatives(source).isEmpty()&&!CoordinatedMark.hasActiveTargetMark(source)
                &&WindDash.knockbackTicks(source)==0,"all original effect gates clear naturally; hurtTime="+source.hurtTime
                +" fall="+source.fallDistance+" vanilla="+source.getActiveEffects());
    }
    private static void committed(F f,TerrorbeakUnit original,UUID oldUuid,float oldRatio,int oldCooldown,int population) {
        var h=f.arena.h;
        h.assertTrue(original.isRemoved()&&f.replacements().size()==1&&f.published.size()==1,"exactly one real replacement publication and old retirement");
        var next=f.replacements().get(0);
        h.assertTrue(!next.getUUID().equals(oldUuid)&&h.getLevel().getEntity(next.getUUID())==next
                &&UnitServerEvents.getCurrentPopulation(f.arena.enemy)==population,"new exact UUID once, same population");
        h.assertTrue(Math.abs(next.getHealth()/next.getMaxHealth()-oldRatio)<.0001
                &&next.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR)==oldCooldown,"actual health ratio and natural roar cooldown preserved");
        h.assertTrue(f.account.food==920&&f.account.wood==1000&&f.account.ore==940
                &&f.account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==470,"one exact 80F/60O/30S upgrade payment");
        h.assertTrue(AaroncosShockSuppression.remainingTicks(next)==0,"replacement has no old body's transient shock lease");
        next.setNoAi(true); // only after successful product commit; never changes eligibility of the original
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=150)
    public static void realRightShockCannotBeWashedByUpgradeAfterNaturalHurtRecovery(GameTestHelper h) {
        boolean legacy=legacyMode(h);var f=new F(h);var right=f.arena.hand(false);
        var source=f.arena.add(DreamseekersMod.TERRORBEAK_UNIT.get().create(h.getLevel()),f.arena.enemy,3,0);
        UUID originalUuid=source.getUUID();SkillIdentity.Stamp original=SkillIdentity.capture(source);
        long[] castAt={-1},deadline={-1};float[] beforeHp={0};int[] population={0};
        System.out.println("UPGRADE_SHOCK_PROBE mode="+(legacy?"before-legacy-observation":"after-strict-regression")+" source="+originalUuid);
        h.startSequence().thenIdle(30).thenExecute(()->{
            otherEffectsClear(h,source);
            h.assertTrue(TerrorbeakUpgradeService.mayOffer(f.defender.actor,source),"ordinary paid upgrade offered before any shock");
            h.assertTrue(SkillService.request(f.defender.actor,new SkillRequest(UnitSkill.TERRORBEAK_DREAD_ROAR,
                    source.getId(),source.getUUID(),source.rts().skills().revision(),source.getId(),source.getUUID()))==SkillResult.SUCCESS,
                    "real ordinary roar establishes a naturally spent cooldown, not injected NBT");
            h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR)==400,"real roar paid full cooldown");
            castAt[0]=h.getLevel().getGameTime();beforeHp[0]=source.getHealth();population[0]=UnitServerEvents.getCurrentPopulation(f.arena.enemy);
            h.assertTrue(f.arena.cast(right,right.mob())==SkillResult.SUCCESS&&f.arena.shadow()==470,"actual right cast paid thirty shadow through real skill codec/service");
        }).thenIdle(20).thenExecute(()->{
            h.assertTrue(source.isAlive()&&source.getHealth()<beforeHp[0]&&AaroncosShockSuppression.remainingTicks(source)==40,
                    "natural twenty-tick right release caused real surviving HP loss and forty-tick suppression");
            h.assertTrue(source.hurtTime>0,"native damage really produced hurtTime before natural recovery");
            deadline[0]=h.getLevel().getGameTime()+40;
            System.out.println("UPGRADE_SHOCK_HIT source="+originalUuid+" castAt="+castAt[0]+" now="+h.getLevel().getGameTime()
                    +" deadline="+deadline[0]+" hurtTime="+source.hurtTime+" hpBefore="+beforeHp[0]+" hpAfter="+source.getHealth());
        }).thenIdle(12).thenExecute(()->{
            otherEffectsClear(h,source);int remaining=(int)(deadline[0]-h.getLevel().getGameTime());
            h.assertTrue(remaining==28&&AaroncosShockSuppression.remainingTicks(source)==remaining&&original.valid()
                    &&source.getUUID().equals(originalUuid),"same exact body retains unexpired shock after native hurtTime naturally reached zero");
            var position=source.position();float hp=source.getHealth(),ratio=hp/source.getMaxHealth();
            long revision=source.rts().skills().revision(),accountRevision=f.account.revision,command=source.rts().commandRevision;
            int cooldown=source.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR);
            boolean noAi=source.isNoAi();var result=f.upgrade(source);
            System.out.println("UPGRADE_SHOCK_ATTEMPT mode="+(legacy?"before":"after")+" result="+result+" source="+originalUuid
                    +" now="+h.getLevel().getGameTime()+" deadline="+deadline[0]+" remainingBefore="+remaining
                    +" hurtTime="+source.hurtTime+" sourceRemoved="+source.isRemoved()+" newBodies="+f.replacements().size()
                    +" joins="+f.published.size()+" defenderF="+f.account.food+" defenderO="+f.account.ore
                    +" defenderS="+f.account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0));
            if(legacy) {
                h.assertTrue(result==TerrorbeakUpgradeService.Result.SUCCESS,"legacy observation must prove reachable success; another gate is not proof of the suspected wash");
                committed(f,source,originalUuid,ratio,cooldown,population[0]);
                h.assertTrue(h.getLevel().getGameTime()<deadline[0],"legacy replacement happened before original shock deadline");
            } else {
                h.assertTrue(result==TerrorbeakUpgradeService.Result.ACTIVE_EFFECT,"new explicit shock guard rejects before any publication");f.unspent();
                h.assertTrue(original.valid()&&!source.isRemoved()&&h.getLevel().getEntity(originalUuid)==source
                        &&f.replacements().isEmpty()&&f.published.isEmpty()&&UnitServerEvents.getCurrentPopulation(f.arena.enemy)==population[0],"same source UUID and population, zero replacement publication");
                h.assertTrue(source.getHealth()==hp&&source.position().equals(position)&&source.isNoAi()==noAi
                        &&source.rts().skills().revision()==revision&&source.rts().commandRevision==command
                        &&source.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR)==cooldown&&f.account.revision==accountRevision,
                        "refusal preserves HP, position, NoAI, command/skill/account revisions and cooldown");
                h.assertTrue(AaroncosShockSuppression.remainingTicks(source)==remaining,"rejection does not cleanse or refresh shock deadline");
            }
            h.assertTrue(f.arena.shadow()==470&&right.rts().skills().cooldownTicks(UnitSkill.AARONCOS_RIGHT_SHOCK)==568,
                    "right's original payment and naturally elapsed cooldown are unchanged by upgrade attempt");
        }).thenIdle(27).thenExecute(()->{
            h.assertTrue(h.getLevel().getGameTime()==deadline[0]-1,"observe natural final shock tick without rewriting time");
            if(!legacy) {
                otherEffectsClear(h,source);
                h.assertTrue(AaroncosShockSuppression.remainingTicks(source)==1&&f.upgrade(source)==TerrorbeakUpgradeService.Result.ACTIVE_EFFECT,
                        "last remaining tick still blocks a second upgrade attempt");
                h.assertTrue(AaroncosShockSuppression.remainingTicks(source)==1&&f.published.isEmpty()&&original.valid(),"no deadline refresh, cleanse or new publication on repeated refusal");f.unspent();
            }
        }).thenIdle(1).thenExecute(()->{
            h.assertTrue(h.getLevel().getGameTime()==deadline[0],"natural forty-tick suppression deadline reached");
            if(!legacy) {
                otherEffectsClear(h,source);
                h.assertTrue(AaroncosShockSuppression.remainingTicks(source)==0&&TerrorbeakUpgradeService.mayOffer(f.defender.actor,source),"same original becomes eligible exactly at natural expiry");
                float ratio=source.getHealth()/source.getMaxHealth();int cooldown=source.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR);
                h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.SUCCESS,"normal paid transformation succeeds after expiry");
                committed(f,source,originalUuid,ratio,cooldown,population[0]);
            } else {
                h.assertTrue(f.replacements().size()==1&&f.published.size()==1&&f.account.food==920&&f.account.ore==940
                        &&f.account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==470,"legacy observation stays a single spent transformation");
            }
            System.out.println("UPGRADE_SHOCK_COMPLETE mode="+(legacy?"before":"after")+" source="+originalUuid
                    +" now="+h.getLevel().getGameTime()+" deadline="+deadline[0]+" joins="+f.published.size());
            f.done();
        });
    }
}
