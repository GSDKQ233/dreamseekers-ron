// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import com.solegendary.reignofnether.unit.units.monsters.SlimeUnit;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;

/** Real authenticated casts and server damage/goal paths; no GUI/socket or uncontrolled collision claim. */
@GameTestHolder("dreamseekers_fox_blessing")
@PrefixGameTestTemplate(false)
public final class FoxBlessingSkillGameTests {
    private static final String BATCH="dreamseekers_fox_blessing",PREFIX="FoxBless_",KEY="fox_blessing";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final ProjectileProbe PROBE=new ProjectileProbe();
    private static boolean listening;
    private static final UUID PERMANENT=UUID.fromString("aa7e5cba-5a10-4809-b43c-a6620dd65028");

    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        if(listening){MinecraftForge.EVENT_BUS.unregister(PROBE);listening=false;}PROBE.shots.clear();
        var names=PlayerServerEvents.rtsPlayers.stream().map(p->p.name).filter(n->n.startsWith(PREFIX)).toList();
        for(String a:names)for(String b:names)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity e:FIXTURES)if(!e.isRemoved())e.discard();FIXTURES.clear();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=860)
    public static void authoritativeSelfTokenRejectsForgedIdentitySenderThreadAndReplay(GameTestHelper h) {
        floor(h);var source=fox(h,"Authority",4,5);var ally=slime(h,"Authority",7,5);var foreign=player(h,"AuthorityForeign");
        ready(h,source,ally).thenExecute(()->{
            SkillRequest good=request(source);var owner=player(h,"Authority");
            require(h,SkillService.request(null,good),SkillResult.INVALID_REQUEST,"no authenticated sender");require(h,SkillService.request(foreign,good),SkillResult.NOT_OWNER,"foreign same-faction sender");
            require(h,SkillService.request(owner,new SkillRequest(UnitSkill.FOX_BLESSING,source.getId(),UUID.randomUUID(),good.revision(),source.getId(),source.getUUID())),SkillResult.INVALID_SOURCE,"wrong source UUID");
            denied(h,SkillService.request(owner,new SkillRequest(UnitSkill.FOX_BLESSING,source.getId(),source.getUUID(),good.revision(),source.getId(),UUID.randomUUID())),"wrong self-token UUID");
            denied(h,SkillService.request(owner,new SkillRequest(UnitSkill.FOX_BLESSING,source.getId(),source.getUUID(),good.revision(),ally.getId(),ally.getUUID())),"a valid friendly target cannot substitute for the self-centered token");
            require(h,java.util.concurrent.CompletableFuture.supplyAsync(()->SkillService.request(owner,good)).join(),SkillResult.INVALID_REQUEST,"off-thread input cannot mutate authoritative state");
            source.addEffect(new MobEffectInstance(MobEffectRegistrar.STUN.get(),20));require(h,SkillService.request(owner,good),SkillResult.DISABLED,"stunned source");source.removeEffect(MobEffectRegistrar.STUN.get());
            var other=h.getLevel().getServer().getLevel(Level.NETHER);if(other!=null)denied(h,SkillService.request(FakePlayerFactory.get(other,owner.getGameProfile()),good),"wrong sender dimension");
            h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==0&&!blessed(ally),"all rejected inputs leave effect and cooldown untouched");
            require(h,SkillService.request(owner,good),SkillResult.SUCCESS,"exact owner/self token accepted");denied(h,SkillService.request(owner,good),"successful request replay");
            h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==480,"exact twenty-four-second authoritative cooldown");close(h,SkillEffects.bonus(ally,BuffKind.ATTACK_SPEED),.2,"twenty-percent blessing");ledger(h,"Authority");pass("SENDER_SELF_TOKEN_UUID_THREAD_REPLAY");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=860)
    public static void oneButtonCastCodecUsesSelfIdentityAndOwnerOnlyState(GameTestHelper h) {
        floor(h);var source=fox(h,"Codec",4,5);var target=slime(h,"Codec",7,5);
        ready(h,source,target).thenExecute(()->{
            var original=new SkillNetwork.Cast(request(source));FriendlyByteBuf wire=new FriendlyByteBuf(Unpooled.buffer());SkillNetwork.Cast decoded;
            try{original.encode(wire);decoded=SkillNetwork.Cast.decode(wire);h.assertTrue(decoded.equals(original)&&wire.readableBytes()==0&&decoded.request().sourceId()==decoded.request().targetId()&&decoded.request().sourceUuid().equals(decoded.request().targetUuid()),"one-button request roundtrips self ID/UUID, no client amount or owner");}finally{wire.release();}
            require(h,decoded.execute(player(h,"CodecForeign")),SkillResult.NOT_OWNER,"network execute checks actual sender");require(h,decoded.execute(player(h,"Codec")),SkillResult.SUCCESS,"decoded packet applies real blessing");denied(h,decoded.execute(player(h,"Codec")),"decoded packet replay");
            var skillState=source.rts().skills();
            h.assertTrue(skillState.autocastEnabled(UnitSkill.FOX_BLESSING),"new Fox Blessing preference is actually enabled");
            var sync=new SkillNetwork.Sync(UnitSkill.FOX_BLESSING,source.getId(),source.getUUID(),skillState.revision(),480,skillState.autocastEnabled(UnitSkill.FOX_BLESSING));FriendlyByteBuf state=new FriendlyByteBuf(Unpooled.buffer());try{sync.encode(state);h.assertTrue(sync.equals(SkillNetwork.Sync.decode(state))&&state.readableBytes()==0,"owner state codec preserves skill/revision/cooldown without target list");}finally{state.release();}
            var off=new SkillNetwork.Toggle(UnitSkill.FOX_BLESSING,source.getId(),source.getUUID(),skillState.revision(),false);
            require(h,off.execute(player(h,"CodecForeign")),SkillResult.NOT_OWNER,"foreign sender cannot disable a fox");
            require(h,off.execute(player(h,"Codec")),SkillResult.SUCCESS,"owner may switch off future automatic blessings");
            h.assertTrue(!skillState.autocastEnabled(UnitSkill.FOX_BLESSING)&&skillState.cooldownTicks(UnitSkill.FOX_BLESSING)==480&&blessed(target),"preference change neither cancels nor refunds the completed manual cast");
            pass("ONE_BUTTON_CODEC_EXECUTE_OWNER_TOGGLE");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=880)
    public static void onlyThreeNearestRealCombatUnitsIncludingAlliedCoreUnitsAreEligible(GameTestHelper h) {
        floor(h);var source=fox(h,"Filter",6,10);var a=slime(h,"Filter",8,10);var b=coreSlime(h,"FilterAlly",10,10);var c=pillager(h,"FilterAlly",12,10);var fourth=slime(h,"Filter",13,10);var far=slime(h,"Filter",20,10);var enemy=slime(h,"FilterEnemy",6,14);
        var worker=DreamseekersMod.FIREFLY_WORKER.get().create(h.getLevel());owned(h,worker,"Filter",6,8);var chicken=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(h.getLevel());owned(h,chicken,"Filter",4,10);var jelly=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());owned(h,jelly,"Filter",6,12);var otherFox=fox(h,"Filter",4,8);var creeper=EntityRegistrar.CREEPER_UNIT.get().create(h.getLevel());owned(h,creeper,"Filter",4,12);var pig=EntityType.PIG.create(h.getLevel());place(h,pig,8,8);pig.setNoAi(true);
        AlliancesServerEvents.addAlliance(source.getOwnerName(),b.getOwnerName());
        ready(h,source,a,b,c,fourth,far,enemy,worker,chicken,jelly,otherFox,creeper,pig).thenExecute(()->{
            require(h,cast(h,source),SkillResult.SUCCESS,"self-centered real combat selection");for(LivingEntity e:List.of(a,b,c))h.assertTrue(blessed(e),"eligible nearest combat unit blessed: "+e.getType());
            for(LivingEntity e:List.of(fourth,far,enemy,worker,chicken,jelly,otherFox,creeper,pig,source))h.assertTrue(!blessed(e)&&!e.isCurrentlyGlowing(),"excluded/outside/fourth unit unchanged: "+e.getType());
            for(LivingEntity e:List.of(a,b,c))h.assertTrue(!e.isCurrentlyGlowing(),"blessing does not add global glow/vision");ledger(h,"Filter");ledger(h,"FilterAlly");pass("THREE_COMBAT_FILTER_ALLIED_PILLAGER_CORE_SLIME");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=860)
    public static void equalDistanceSelectionUsesStableUuidOrder(GameTestHelper h) {
        floor(h);var source=fox(h,"Tie",10,10);List<PinkSlimeUnit> targets=new ArrayList<>();int[][] positions={{13,10},{7,10},{10,13},{10,7}};
        for(int i=0;i<4;i++){var e=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());e.setUUID(UUID.fromString("00000000-0000-4000-8000-00000000"+String.format("%04d",40-i)));owned(h,e,"Tie",positions[i][0],positions[i][1]);targets.add(e);}
        List<Entity> all=new ArrayList<>(targets);all.add(source);ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
            for(var e:targets)close(h,source.distanceToSqr(e),9,"equal-distance fixture remains exact");require(h,cast(h,source),SkillResult.SUCCESS,"stable tie selection");var sorted=targets.stream().sorted(Comparator.comparing(e->e.getUUID().toString())).toList();for(int i=0;i<4;i++)h.assertTrue(blessed(sorted.get(i))==(i<3),"UUID order decides equal-distance cutoff "+i);pass("DISTANCE_THEN_UUID_STABLE_SELECTION");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=990)
    public static void twoAuthenticatedOwnersCannotRefreshSameNamedBlessingOrChargeLoser(GameTestHelper h) {
        floor(h);var a=fox(h,"AtomicA",4,5);var b=fox(h,"AtomicB",5,8);var target=slime(h,"AtomicA",7,5);AlliancesServerEvents.addAlliance(a.getOwnerName(),b.getOwnerName());
        ready(h,a,b,target).thenExecute(()->{require(h,cast(h,a),SkillResult.SUCCESS,"first actual sender wins");require(h,cast(h,b),SkillResult.ALREADY_ACTIVE,"different allied sender cannot stack");h.assertTrue(b.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==0,"loser does not pay cooldown");})
          .thenIdle(100).thenExecute(()->{require(h,cast(h,b),SkillResult.ALREADY_ACTIVE,"late duplicate cannot refresh");close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.2,"still exactly one blessing");})
          .thenIdle(21).thenExecute(()->{h.assertTrue(!blessed(target),"duplicate attempts did not extend original lifetime");h.assertTrue(b.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==0,"all rejected duplicates remain free");require(h,cast(h,b),SkillResult.SUCCESS,"after true expiry another caster can supply a genuinely new blessing");ledger(h,"AtomicA");ledger(h,"AtomicB");pass("TWO_SENDERS_ATOMIC_NAMED_NO_REFRESH");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=980)
    public static void alreadyCoveredNearestUnitsDoNotStarveUncoveredFourthCandidate(GameTestHelper h) {
        floor(h);var a=fox(h,"Uncovered",4,9);var b=fox(h,"Uncovered",4,11);List<PinkSlimeUnit> targets=new ArrayList<>();for(int x:new int[]{6,8,10,11})targets.add(slime(h,"Uncovered",x,10));
        List<Entity> all=new ArrayList<>(targets);all.add(a);all.add(b);ready(h,all.toArray(Entity[]::new)).thenExecute(()->{require(h,cast(h,a),SkillResult.SUCCESS,"first three selected");h.assertTrue(targets.stream().filter(FoxBlessingSkillGameTests::blessed).count()==3,"first cast has exactly three recipients");})
          .thenIdle(40).thenExecute(()->{require(h,cast(h,b),SkillResult.SUCCESS,"at least one uncovered recipient makes a real second cast");h.assertTrue(targets.stream().allMatch(FoxBlessingSkillGameTests::blessed),"uncovered fourth selected despite three covered nearer candidates");})
          .thenIdle(81).thenExecute(()->{for(int i=0;i<3;i++)h.assertTrue(!blessed(targets.get(i)),"second cast does not refresh covered original recipient "+i);h.assertTrue(blessed(targets.get(3)),"newly added fourth keeps its independent lifetime");pass("UNCOVERED_PRIORITY_WITHOUT_REFRESH");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=920)
    public static void emptyCombatNeighborhoodAndNormalAiNeverSpendOrAutomaticallyCast(GameTestHelper h) {
        floor(h);var source=fox(h,"NoAuto",4,5);var other=fox(h,"NoAuto",7,5);var distant=slime(h,"NoAuto",20,5);
        ready(h,source,other,distant).thenExecute(()->{require(h,cast(h,source),SkillResult.NO_TARGET,"noncombat-only neighborhood cannot burn cooldown");h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==0,"empty cast preserves cooldown");moveFixture(h,distant,7,8);source.setNoAi(false);})
          .thenIdle(45).thenExecute(()->{h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==0&&!blessed(distant)&&source.rts().skills().autocastEnabled(UnitSkill.FOX_BLESSING),"enabled normal AI must not cast on a nearby idle combat unit");require(h,cast(h,source),SkillResult.SUCCESS,"one manual self action still works");ledger(h,"NoAuto");pass("NO_TARGET_NO_CD_NO_IDLE_AUTO");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=990)
    public static void blessingLastsExactlySixSecondsEvenAfterRecipientLeavesCastRange(GameTestHelper h) {
        floor(h);var source=fox(h,"Lifetime",4,5);var target=slime(h,"Lifetime",7,5);double[] base={0};
        ready(h,source,target).thenExecute(()->{base[0]=aps(target);require(h,cast(h,source),SkillResult.SUCCESS,"real blessing");moveFixture(h,target,20,5);})
          .thenIdle(119).thenExecute(()->{h.assertTrue(source.distanceToSqr(target)>64&&blessed(target),"range is a cast boundary, not a sustained tether");close(h,aps(target),base[0]*1.2,"real APS modifier remains before expiry");})
          .thenIdle(2).thenExecute(()->{h.assertTrue(!blessed(target),"six-second blessing expires without refresh");close(h,aps(target),base[0],"actual APS restored after expiry");h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)>0,"effect expiry never refunds cooldown");pass("EXACT_DURATION_NO_POSTCAST_TETHER");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=890)
    public static void deathAndRemovalOfEitherEndpointClearActualAttributeWithoutCooldownRefund(GameTestHelper h) {
        floor(h);List<GoldenFoxUnit> sources=new ArrayList<>();List<PinkSlimeUnit> targets=new ArrayList<>();for(int i=0;i<4;i++){int x=i%2==0?3:15,z=i<2?5:17;sources.add(fox(h,"Life"+i,x,z));targets.add(slime(h,"Life"+i,x+3,z));}
        List<Entity> all=new ArrayList<>(sources);all.addAll(targets);double[] base=new double[4];ready(h,all.toArray(Entity[]::new)).thenExecute(()->{for(int i=0;i<4;i++){base[i]=aps(targets.get(i));require(h,cast(h,sources.get(i)),SkillResult.SUCCESS,"lifecycle fixture "+i);}sources.get(0).hurt(sources.get(0).damageSources().genericKill(),10000);sources.get(1).discard();targets.get(2).hurt(targets.get(2).damageSources().genericKill(),10000);targets.get(3).discard();})
          .thenIdle(3).thenExecute(()->{for(int i=0;i<4;i++){h.assertTrue(!blessed(targets.get(i)),"invalid endpoint clears named effect "+i);close(h,aps(targets.get(i)),base[i],"invalid endpoint removes actual attribute "+i);h.assertTrue(sources.get(i).rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)>0,"paid cooldown not refunded "+i);}pass("SOURCE_TARGET_DEATH_REMOVE_CLEANUP");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=880)
    public static void sourceAddonRecipientAndCoreRecipientOwnerAbaCannotPreserveBlessing(GameTestHelper h) {
        floor(h);var a=fox(h,"ABA0",3,4);var t1=slime(h,"ABA0",6,4);var b=fox(h,"ABA1",3,11);var t2=slime(h,"ABA1",6,11);var c=fox(h,"ABA2",3,18);var t3=coreSlime(h,"ABA2",6,18);
        ready(h,a,t1,b,t2,c,t3).thenExecute(()->{for(var source:List.of(a,b,c))require(h,cast(h,source),SkillResult.SUCCESS,"ABA fixture cast");String owner=a.getOwnerName();a.setOwnerName(account("ABAForeign"));a.setOwnerName(owner);owner=t2.getOwnerName();t2.setOwnerName(account("ABAForeign"));t2.setOwnerName(owner);owner=t3.getOwnerName();t3.setOwnerName(account("ABAForeign"));t3.setOwnerName(owner);for(LivingEntity e:List.of(t1,t2,t3)){h.assertTrue(!blessed(e),"same-tick ownership ABA invalidates source or recipient");close(h,SkillEffects.bonus(e,BuffKind.ATTACK_SPEED),0,"no revived temporary APS");}pass("SOURCE_ADDON_CORE_OWNER_ABA");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=880)
    public static void allianceRemovalClearsRealModifierImmediatelyAndReaddCannotResurrect(GameTestHelper h) {
        floor(h);var source=fox(h,"Alliance",4,5);var allied=slime(h,"AlliancePeer",7,5);var own=slime(h,"Alliance",7,10);AlliancesServerEvents.addAlliance(source.getOwnerName(),allied.getOwnerName());double[] base=new double[2];
        ready(h,source,allied,own).thenExecute(()->{base[0]=aps(allied);base[1]=aps(own);require(h,cast(h,source),SkillResult.SUCCESS,"allied and owned recipients");AlliancesServerEvents.removeAlliance(source.getOwnerName(),allied.getOwnerName());close(h,aps(allied),base[0],"actual attribute removed in same callback");h.assertTrue(!blessed(allied),"allied effect removed");close(h,aps(own),base[1]*1.2,"unrelated same-owner blessing remains");AlliancesServerEvents.addAlliance(source.getOwnerName(),allied.getOwnerName());close(h,aps(allied),base[0],"diplomacy ABA cannot recreate modifier");h.assertTrue(!blessed(allied)&&source.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)>0,"no resurrection or refund");pass("ALLIANCE_IMMEDIATE_ATTRIBUTE_ABA");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1010)
    public static void strongestCapAndExpiryPreservePermanentResearchAndOtherLiveProvider(GameTestHelper h) {
        floor(h);var source=fox(h,"Caps",4,5);var target=slime(h,"Caps",7,5);double[] base={0};
        ready(h,source,target).thenExecute(()->{target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(PERMANENT,"permanent research",.1,AttributeModifier.Operation.ADDITION));base[0]=aps(target);require(h,cast(h,source),SkillResult.SUCCESS,"actual named blessing");h.assertTrue(SkillEffects.addBuff(source,target,"weaker",BuffKind.ATTACK_SPEED,.15,180)&&SkillEffects.addBuff(source,target,"stronger",BuffKind.ATTACK_SPEED,.9,30),"independent temporary providers");close(h,aps(target),base[0]*1.25,"normal APS cap applied once, not compounded");})
          .thenIdle(31).thenExecute(()->close(h,aps(target),base[0]*1.2,"stronger expiry falls back to genuine blessing"))
          .thenIdle(90).thenExecute(()->{h.assertTrue(!blessed(target),"named blessing expires independently");close(h,aps(target),base[0]*1.15,"blessing expiry must not erase another surviving provider");})
          .thenIdle(60).thenExecute(()->{close(h,aps(target),base[0],"all temporary effects cleared");h.assertTrue(target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(PERMANENT)!=null,"permanent research remains");pass("MAX_CAP_OTHER_PROVIDER_PERMANENT");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=990)
    public static void actualBlessingShortensOrdinaryMeleeHitIntervals(GameTestHelper h) {
        floor(h);var source=fox(h,"MeleeBuff",3,17);var normal=slime(h,"MeleeNormal",6,5);var boosted=slime(h,"MeleeBuff",6,15);var v1=fox(h,"MeleeEnemy",7,5);var v2=fox(h,"MeleeEnemy",7,15);List<Long> normalHits=new ArrayList<>(),boostHits=new ArrayList<>();float[] hp=new float[2];
        var sequence=ready(h,source,normal,boosted,v1,v2).thenExecute(()->{for(var v:List.of(v1,v2))durable(v);damage(normal,1);damage(boosted,1);require(h,cast(h,source),SkillResult.SUCCESS,"real fox blessing before melee");normal.setNoAi(false);boosted.setNoAi(false);normal.setUnitAttackTargetForced(v1);boosted.setUnitAttackTargetForced(v2);normal.setHoldPosition(true);boosted.setHoldPosition(true);hp[0]=v1.getHealth();hp[1]=v2.getHealth();});
        for(int i=0;i<110;i++)sequence.thenIdle(1).thenExecute(()->{recordDamage(h,v1,hp,0,normalHits);recordDamage(h,v2,hp,1,boostHits);});
        sequence.thenExecute(()->{h.assertTrue(normalHits.size()>=3&&boostHits.size()>=3,"actual repeated melee hits recorded: normal="+normalHits+" boosted="+boostHits);h.assertTrue(mean(boostHits)<mean(normalHits),"actual fox blessing shortens ordinary melee interval: normal="+mean(normalHits)+" boosted="+mean(boostHits));normal.setNoAi(true);boosted.setNoAi(true);pass("REAL_BLESSING_MELEE_INTERVALS");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1030)
    public static void cachedPillagerCooldownChangesOnlyForActiveSkillBonusAndNeverMutatesBaseline(GameTestHelper h) {
        floor(h);var source=fox(h,"Cache",4,5);var target=pillager(h,"Cache",7,5);int[] cached={0};double[] permanentValue={0};
        ready(h,source,target).thenExecute(()->{var goal=crossbow(target);cached[0]=cached(goal);target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(PERMANENT,"permanent APS research",.1,AttributeModifier.Operation.ADDITION));permanentValue[0]=aps(target);h.assertTrue((int)target.getAttackCooldown()!=cached[0],"fixture exposes original constructor cache versus changed real APS");goal.setToMaxAttackCooldown();close(h,goal.getAttackCooldown(),cached[0],"without active addon bonus the original cached path is unchanged");require(h,cast(h,source),SkillResult.SUCCESS,"real blessing enables bounded cache adapter");for(int i=0;i<8;i++){goal.setToMaxAttackCooldown();close(h,goal.getAttackCooldown(),shortened(cached[0],.2),"repeated read divides original cache exactly once");}h.assertTrue(cached(goal)==cached[0],"cached baseline itself remains untouched");})
          .thenIdle(121).thenExecute(()->{crossbow(target).setToMaxAttackCooldown();close(h,crossbow(target).getAttackCooldown(),cached[0],"expiry returns original fixed cooldown");close(h,aps(target),permanentValue[0],"permanent APS preserved despite compatibility adapter");h.assertTrue(cached(crossbow(target))==cached[0],"no permanently rewritten cache");pass("PILLAGER_CACHE_BASELINE_OPTOUT_AND_RESTORE");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1300)
    public static void realCrossbowProjectilesAccelerateUnderBlessingThenReturnToLegacyCadence(GameTestHelper h) {crossbowIntervals(h,false);}

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1300)
    public static void realCrossbowProjectilesAccelerateUnderActualGelReboundThenReturnToLegacyCadence(GameTestHelper h) {crossbowIntervals(h,true);}

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1210)
    public static void actualCoreSlimePushDamageAcceleratesUnderBlessingThenReturnsToCachedCadence(GameTestHelper h) {pushIntervals(h,false);}

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1210)
    public static void actualCoreSlimePushDamageAcceleratesUnderGelReboundThenReturnsToCachedCadence(GameTestHelper h) {pushIntervals(h,true);}

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1020)
    public static void actualSlimeLandingAttackAlsoReadsScopedCooldownAndExpiresNormally(GameTestHelper h) {
        floor(h);var source=fox(h,"Landing",4,5);var target=coreSlime(h,"Landing",7,5);var victim=fox(h,"LandingEnemy",8,5);int[] cached={0};
        ready(h,source,target,victim).thenExecute(()->{durable(victim);damage(target,1);target.setUnitAttackTargetForced(victim);target.setHoldPosition(true);cached[0]=target.PUSH_ATTACK_CD_MAX;float hp=victim.getHealth();((MeleeAttackSlimeUnitGoal)target.getAttackGoal()).landedJump();h.assertTrue(victim.getHealth()<hp&&target.pushAttackCd==cached[0],"real unbuffed landing produces damage and original fixed cooldown");require(h,cast(h,source),SkillResult.SUCCESS,"real blessing before next landing");})
          .thenIdle(45).thenExecute(()->{victim.invulnerableTime=0;float hp=victim.getHealth();((MeleeAttackSlimeUnitGoal)target.getAttackGoal()).landedJump();h.assertTrue(victim.getHealth()<hp&&target.pushAttackCd==shortened(cached[0],.2),"real boosted landing produces damage with scoped shortened cooldown");})
          .thenIdle(80).thenExecute(()->{victim.invulnerableTime=0;float hp=victim.getHealth();((MeleeAttackSlimeUnitGoal)target.getAttackGoal()).landedJump();h.assertTrue(victim.getHealth()<hp&&target.pushAttackCd==cached[0],"post-expiry real landing returns legacy cooldown");pass("REAL_SLIME_LANDING_FIXED_CACHE_ADAPTER");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=860)
    public static void namedReservationIsIndependentOfStrongerUnrelatedBonus(GameTestHelper h) {
        floor(h);var source=fox(h,"Named",4,5);var target=slime(h,"Named",7,5);
        ready(h,source,target).thenExecute(()->{h.assertTrue(SkillEffects.addBuff(source,target,"other_stronger",BuffKind.ATTACK_SPEED,.25,180),"unrelated stronger provider");require(h,cast(h,source),SkillResult.SUCCESS,"new named blessing is an effective independent reservation even while stronger buff wins aggregation");h.assertTrue(blessed(target)&&source.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==480,"named effect committed exactly once");close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.25,"category aggregation does not stack named reservation");pass("NAMED_RESERVATION_NOT_AGGREGATE_VALUE");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1020)
    public static void militiaModeConstantsOnlyScaleWhileBlessedAndWeaponSwapPreservesPermanentResearch(GameTestHelper h) {
        floor(h);var source=fox(h,"MilitiaModes",4,5);var target=militia(h,"MilitiaModes",7,5,false);var gelSource=slime(h,"MilitiaModes",4,8);var breaker=slime(h,"MilitiaModesEnemy",8,5);double[] base={0};
        ready(h,source,target,gelSource,breaker).thenExecute(()->{
            target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(PERMANENT,"permanent APS research",.17,AttributeModifier.Operation.ADDITION));base[0]=aps(target);
            close(h,target.getAttackCooldown(),40,"unbuffed sword retains original constant despite permanent APS");target.swapWeapons(true);close(h,target.getAttackCooldown(),20f/.3f,"unbuffed bow retains its original constant");require(h,cast(h,source),SkillResult.SUCCESS,"armed bow militia receives real blessing");
            close(h,target.getAttackCooldown(),(20f/.3f)/1.2,"bow return scaled once, without premature integer floor");target.swapWeapons(false);close(h,target.getAttackCooldown(),40/1.2,"same live blessing follows legitimate sword mode switch");target.swapWeapons(true);close(h,target.getAttackCooldown(),(20f/.3f)/1.2,"switching back cannot multiply bonus again");close(h,aps(target),base[0]*1.2,"mode switch preserves real permanent attribute plus one transient modifier");
        }).thenIdle(121).thenExecute(()->{
            close(h,target.getAttackCooldown(),20f/.3f,"expired bow returns original constant");target.swapWeapons(false);close(h,target.getAttackCooldown(),40,"expired sword returns original constant");close(h,aps(target),base[0],"permanent APS retained through swaps and expiry");h.assertTrue(target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(PERMANENT)!=null,"permanent modifier remains installed");
            triggerGel(h,gelSource,target,breaker);close(h,target.getAttackCooldown(),40/1.15,"actual Gel break scales militia sword float return exactly once");target.swapWeapons(true);close(h,target.getAttackCooldown(),(20f/.3f)/1.15,"actual Gel break also scales bow float return without double application");close(h,aps(target),base[0]*1.15,"permanent research plus one genuine rebound modifier");
        }).thenIdle(61).thenExecute(()->{
            close(h,target.getAttackCooldown(),20f/.3f,"Gel expiry restores original bow constant");target.swapWeapons(false);close(h,target.getAttackCooldown(),40,"Gel expiry restores original sword constant");close(h,aps(target),base[0],"both skill lifetimes end without erasing permanent research");pass("MILITIA_MODE_CONSTANTS_SCOPED_SWAP_PERMANENT_GEL");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1300)
    public static void realMilitiaSwordHitsAccelerateUnderBlessingThenReturnToFortyTicks(GameTestHelper h) {militiaIntervals(h,false);}

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1420)
    public static void realMilitiaBowProjectilesAccelerateUnderBlessingThenReturnToLegacyTicks(GameTestHelper h) {militiaIntervals(h,true);}

    private static void militiaIntervals(GameTestHelper h,boolean bow) {
        floor(h);String tag=bow?"MilitiaBow":"MilitiaSword";var source=fox(h,tag+"Buff",4,18);var normal=militia(h,tag+"Normal",6,5,bow);var boosted=militia(h,tag+"Buff",6,15,bow);var v1=zombie(h,tag+"Enemy",bow?12:7,5);var v2=zombie(h,tag+"Enemy",bow?12:7,15);List<Long> normalHits=new ArrayList<>(),boostHits=new ArrayList<>();float[] hp=new float[2];long[] start={0};double[] base={0};
        var sequence=ready(h,source,normal,boosted,v1,v2).thenExecute(()->{
            for(var v:List.of(v1,v2))durable(v);for(var m:List.of(normal,boosted)){damage(m,1);m.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(PERMANENT,"permanent APS research",.17,AttributeModifier.Operation.ADDITION));m.setHoldPosition(true);}base[0]=aps(boosted);close(h,normal.getAttackCooldown(),bow?20f/.3f:40,"unbuffed legacy mode constant before combat");require(h,cast(h,source),SkillResult.SUCCESS,"actual blessing on militia combat actor");
            if(bow){for(var m:List.of(normal,boosted)){seedBow((UnitBowAttackGoal<?>)m.getAttackGoal());((UnitBowAttackGoal<?>)m.getAttackGoal()).setToMaxAttackCooldown();watch(m);}}
            hp[0]=v1.getHealth();hp[1]=v2.getHealth();start[0]=h.getLevel().getGameTime();normal.setUnitAttackTargetForced(v1);boosted.setUnitAttackTargetForced(v2);normal.setNoAi(false);boosted.setNoAi(false);
        });
        for(int i=0;i<(bow?420:330);i++)sequence.thenIdle(1).thenExecute(()->{recordDamage(h,v1,hp,0,normalHits);recordDamage(h,v2,hp,1,boostHits);});
        sequence.thenExecute(()->{
            List<Long> regular=bow?PROBE.shots.get(normal.getUUID()):normalHits,fast=bow?PROBE.shots.get(boosted.getUUID()):boostHits;
            h.assertTrue(regular.size()>=5&&fast.size()>=5,"both real militia modes attack repeatedly: normal="+regular+" boosted="+fast);h.assertTrue(fast.get(1)-fast.get(0)<regular.get(1)-regular.get(0),"first real "+(bow?"bow projectile":"sword damage")+" interval shortened by actual blessing");h.assertTrue(!normalHits.isEmpty()&&!boostHits.isEmpty(),"both actors actually damaged enemy units");
            var late=fast.stream().filter(t->t-start[0]>=180).toList();h.assertTrue(late.size()>=3,"full post-expiry intervals recorded: "+late);double legacy=bow?(int)(20f/.3f):40;h.assertTrue(mean(late)>=legacy-1&&mean(late)<=legacy+2,"actual post-expiry mode cadence returns to legacy: mean="+mean(late)+" legacy="+legacy);close(h,boosted.getAttackCooldown(),bow?20f/.3f:40,"legacy mode return restored after expiry");close(h,aps(boosted),base[0],"permanent research retained after real combat");normal.setNoAi(true);boosted.setNoAi(true);pass(bow?"REAL_MILITIA_BOW_PROJECTILES_AND_EXPIRY":"REAL_MILITIA_SWORD_DAMAGE_AND_EXPIRY");
        }).thenSucceed();
    }

    private static void crossbowIntervals(GameTestHelper h,boolean gel) {
        floor(h);String tag=gel?"CrossGel":"CrossFox";var source=fox(h,tag+"Buff",4,17);var gelSource=slime(h,tag+"Buff",3,17);var normal=pillager(h,tag+"Normal",5,5);var boosted=pillager(h,tag+"Buff",5,14);var v1=zombie(h,tag+"Enemy",12,5);var v2=zombie(h,tag+"Enemy",12,14);var breaker=slime(h,tag+"Enemy",3,14);int[] raw=new int[2];long[] start={0};double[] permanent={0};
        var sequence=ready(h,source,gelSource,normal,boosted,v1,v2,breaker).thenExecute(()->{for(var v:List.of(v1,v2))durable(v);for(var p:List.of(normal,boosted)){p.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(PERMANENT,"permanent APS research",.1,AttributeModifier.Operation.ADDITION));p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);neutral(p);damage(p,1);}permanent[0]=aps(boosted);raw[0]=cached(crossbow(normal));raw[1]=cached(crossbow(boosted));h.assertTrue(raw[0]==raw[1],"matched original cached cooldowns");})
          .thenIdle(30).thenExecute(()->{
            // Inspect the actual retained equipment/native charge implementation; never assume vanilla duration.
            // Matching goal RNG removes windup noise; no equipment or cached maximum is changed here.
            for(var p:List.of(normal,boosted))System.out.println("FOX_BLESSING_CROSSBOW_EQUIPMENT gel="+gel+" actor="+p.getUUID()+" item="+p.getMainHandItem()+" quickCharge="+net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(Enchantments.QUICK_CHARGE,p.getMainHandItem())+" actualChargeDuration="+CrossbowItem.getChargeDuration(p.getMainHandItem())+" cachedMax="+cached(crossbow(p)));
            for(var p:List.of(normal,boosted)){seedCrossbow(crossbow(p));crossbow(p).setToMaxAttackCooldown();close(h,crossbow(p).getAttackCooldown(),raw[0],"unbuffed cached path before real skill");watch(p);}
            if(gel)triggerGel(h,gelSource,boosted,breaker);else require(h,cast(h,source),SkillResult.SUCCESS,"actual fox blessing for crossbow actor");
            double amount=gel?.15:.2;crossbow(boosted).setToMaxAttackCooldown();close(h,crossbow(boosted).getAttackCooldown(),shortened(raw[1],amount),"active scoped fixed cooldown read");start[0]=h.getLevel().getGameTime();normal.setNoAi(false);boosted.setNoAi(false);normal.setHoldPosition(true);boosted.setHoldPosition(true);normal.setUnitAttackTargetForced(v1);boosted.setUnitAttackTargetForced(v2);
          });
        for(int i=0;i<450;i++)sequence.thenIdle(1).thenExecute(()->h.assertTrue(h.getLevel().noCollision(normal)&&h.getLevel().noCollision(boosted),"stationary native crossbow actors remain collision-safe"));
        sequence.thenExecute(()->{
            List<Long> ordinary=PROBE.shots.get(normal.getUUID()),fast=PROBE.shots.get(boosted.getUUID());h.assertTrue(ordinary.size()>=5&&fast.size()>=5,"real vanilla projectiles emitted repeatedly: normal="+ordinary+" boosted="+fast);
            long normalFirst=ordinary.get(1)-ordinary.get(0),boostFirst=fast.get(1)-fast.get(0);h.assertTrue(boostFirst<normalFirst,"first real projectile interval shortens under "+(gel?"actual Gel rebound":"actual blessing")+": normal="+normalFirst+" boosted="+boostFirst);
            h.assertTrue(v1.getHealth()<v1.getMaxHealth()&&v2.getHealth()<v2.getMaxHealth(),"actual spawned projectiles also hit real enemy units, not just a counter query");
            System.out.println("FOX_BLESSING_CROSSBOW_INTERVALS gel="+gel+" start="+start[0]+" raw="+Arrays.toString(raw)+" normal="+ordinary+" boosted="+fast);
            // Match the same emission ordinal, not wall-time buckets: the seeded private goal RNG
            // advances once per charge completion, so equal ordinals have equal native windup draws.
            // Both preceding emissions must be safely beyond expiry. No guessed charge-duration
            // formula or timing tolerance is used: stationary equal-equipment native goals must match.
            int compared=0;for(int ordinal=1;ordinal<Math.min(ordinary.size(),fast.size());ordinal++){
                if(ordinary.get(ordinal-1)-start[0]<180||fast.get(ordinal-1)-start[0]<180)continue;
                long normalDelta=ordinary.get(ordinal)-ordinary.get(ordinal-1),fastDelta=fast.get(ordinal)-fast.get(ordinal-1);
                System.out.println("FOX_BLESSING_CROSSBOW_POSTEXPIRY gel="+gel+" ordinal="+ordinal+" normalInterval="+normalDelta+" boostedInterval="+fastDelta);
                h.assertTrue(fastDelta==normalDelta,"expired same-ordinal real native projectile intervals exactly match unbuffed control: ordinal="+ordinal+" normal="+normalDelta+" boosted="+fastDelta);compared++;
            }
            h.assertTrue(compared>=2,"at least two complete matched post-expiry intervals, compared="+compared+" normal="+ordinary+" boosted="+fast);
            crossbow(boosted).setToMaxAttackCooldown();close(h,crossbow(boosted).getAttackCooldown(),raw[1],"expired native path returns exact original cache");close(h,aps(boosted),permanent[0],"permanent modifier retained");h.assertTrue(cached(crossbow(boosted))==raw[1]&&!blessed(boosted),"cache never rewritten and blessing absent");normal.setNoAi(true);boosted.setNoAi(true);pass(gel?"REAL_CROSSBOW_GEL_PROJECTILES_AND_EXPIRED_CADENCE":"REAL_CROSSBOW_BLESSING_PROJECTILES_AND_EXPIRED_CADENCE");
        }).thenSucceed();
    }

    private static void pushIntervals(GameTestHelper h,boolean gel) {
        floor(h);String tag=gel?"PushGel":"PushFox";var source=fox(h,tag+"Buff",4,17);var gelSource=slime(h,tag+"Buff",3,17);var normal=coreSlime(h,tag+"Normal",6,5);var boosted=coreSlime(h,tag+"Buff",6,15);var v1=fox(h,tag+"Enemy",7,5);var v2=fox(h,tag+"Enemy",7,15);var breaker=slime(h,tag+"Enemy",4,15);List<Long> normalHits=new ArrayList<>(),boostHits=new ArrayList<>();float[] hp=new float[2];int[] raw={0};long[] start={0};
        var sequence=ready(h,source,gelSource,normal,boosted,v1,v2,breaker).thenExecute(()->{
            for(var v:List.of(v1,v2))durable(v);for(var s:List.of(normal,boosted)){neutral(s);damage(s,1);s.setNoGravity(true);s.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);s.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(PERMANENT,"permanent APS research",.1,AttributeModifier.Operation.ADDITION));}
            raw[0]=normal.PUSH_ATTACK_CD_MAX;h.assertTrue(raw[0]==boosted.PUSH_ATTACK_CD_MAX&&(int)normal.getAttackCooldown()!=raw[0],"constructor-cached push cooldown remains distinct from changed permanent APS");normal.setUnitAttackTargetForced(v1);boosted.setUnitAttackTargetForced(v2);normal.setHoldPosition(true);boosted.setHoldPosition(true);hp[0]=v1.getHealth();hp[1]=v2.getHealth();
            if(gel)triggerGel(h,gelSource,boosted,breaker);else require(h,cast(h,source),SkillResult.SUCCESS,"actual blessing before controlled collision callback");start[0]=h.getLevel().getGameTime();
        });
        // Explicit controlled real push callbacks. Ordinary entity ticks alone decrement pushAttackCd.
        // This tests actual push damage/cooldown, not autonomous collision navigation.
        for(int i=0;i<300;i++)sequence.thenIdle(1).thenExecute(()->{
            normal.setOnGround(false);boosted.setOnGround(false);normal.setDeltaMovement(Vec3.ZERO);boosted.setDeltaMovement(Vec3.ZERO);v1.invulnerableTime=0;v2.invulnerableTime=0;normal.push(v1);boosted.push(v2);recordDamage(h,v1,hp,0,normalHits);recordDamage(h,v2,hp,1,boostHits);
        });
        sequence.thenExecute(()->{
            h.assertTrue(normalHits.size()>=6&&boostHits.size()>=6,"real push callbacks repeatedly damage selected targets: normal="+normalHits+" boosted="+boostHits);close(h,normalHits.get(1)-normalHits.get(0),raw[0],"unbuffed actual push retains cached interval");close(h,boostHits.get(1)-boostHits.get(0),shortened(raw[0],gel?.15:.2),"real active skill shortens actual push damage interval");
            var late=boostHits.stream().filter(t->t-start[0]>=170).toList();h.assertTrue(late.size()>=3,"three post-expiry real pushes available: "+late);close(h,mean(late),raw[0],"expired push returns exactly cached legacy cadence");h.assertTrue(boosted.PUSH_ATTACK_CD_MAX==raw[0]&&boosted.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(PERMANENT)!=null,"constant cache and permanent research unchanged");pass(gel?"REAL_CONTROLLED_PUSH_GEL_INTERVALS_AND_EXPIRY":"REAL_CONTROLLED_PUSH_BLESSING_INTERVALS_AND_EXPIRY");
        }).thenSucceed();
    }

    private static void triggerGel(GameTestHelper h,PinkSlimeUnit caster,LivingEntity target,PinkSlimeUnit enemy){require(h,SkillService.request(playerNamed(h,caster.getOwnerName()),new SkillRequest(UnitSkill.GEL_GUARD,caster.getId(),caster.getUUID(),caster.rts().skills().revision(),target.getId(),target.getUUID())),SkillResult.SUCCESS,"genuine Gel guard before enemy break");neutral(target);damage(enemy,12);target.invulnerableTime=0;h.assertTrue(enemy.doHurtTarget(target),"actual hostile melee exhausts twelve-point shield");close(h,SkillEffects.shieldRemaining(target),0,"actual shield depleted");close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.15,"actual Gel elastic rebound, not synthetic provider");}
    private static void recordDamage(GameTestHelper h,LivingEntity target,float[] hp,int index,List<Long> hits){if(target.getHealth()<hp[index]){hits.add(h.getLevel().getGameTime());hp[index]=target.getHealth();}}
    private static int shortened(int raw,double bonus){return Math.max(1,(int)Math.floor(raw/(1+bonus)));}
    private static double mean(List<Long> hits){return (double)(hits.get(hits.size()-1)-hits.get(0))/(hits.size()-1);}
    private static UnitCrossbowAttackGoal<?> crossbow(PillagerUnit p){return (UnitCrossbowAttackGoal<?>)p.getAttackGoal();}
    private static int cached(UnitCrossbowAttackGoal<?> goal){try{Field f=UnitCrossbowAttackGoal.class.getDeclaredField("attackCooldownMax");f.setAccessible(true);return f.getInt(goal);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
    private static void seedCrossbow(UnitCrossbowAttackGoal<?> goal){try{Field r=UnitCrossbowAttackGoal.class.getDeclaredField("random"),w=UnitCrossbowAttackGoal.class.getDeclaredField("windupTime");r.setAccessible(true);w.setAccessible(true);((Random)r.get(goal)).setSeed(739105L);w.setInt(goal,0);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
    private static void seedBow(UnitBowAttackGoal<?> goal){try{Field r=UnitBowAttackGoal.class.getDeclaredField("random"),w=UnitBowAttackGoal.class.getDeclaredField("attackWindupTime");r.setAccessible(true);w.setAccessible(true);((Random)r.get(goal)).setSeed(739105L);w.setInt(goal,0);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
    private static void watch(LivingEntity p){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}PROBE.shots.put(p.getUUID(),new ArrayList<>());}
    public static final class ProjectileProbe {
        final Map<UUID,List<Long>> shots=new HashMap<>();
        @SubscribeEvent public void join(EntityJoinLevelEvent event){if(event.getEntity() instanceof AbstractArrow arrow&&arrow.getOwner()!=null){var list=shots.get(arrow.getOwner().getUUID());if(list!=null)list.add(event.getLevel().getGameTime());}}
    }
    private static boolean blessed(LivingEntity e){return SkillEffects.hasNamedBuff(e,KEY,BuffKind.ATTACK_SPEED);}
    private static double aps(LivingEntity e){return e.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get());}
    private static void neutral(LivingEntity e){e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);e.setAbsorptionAmount(0);var resist=e.getAttribute(AttributeRegistrar.RANGED_DAMAGE_RESIST.get());if(resist!=null)resist.setBaseValue(0);}
    private static void damage(LivingEntity e,double value){e.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(value);if(e.getAttribute(Attributes.ATTACK_DAMAGE)!=null)e.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(value);var crit=e.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);}
    private static void durable(LivingEntity e){neutral(e);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);e.setHealth(2000);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);}
    private static SkillRequest request(GoldenFoxUnit source){return new SkillRequest(UnitSkill.FOX_BLESSING,source.getId(),source.getUUID(),source.rts().skills().revision(),source.getId(),source.getUUID());}
    private static SkillResult cast(GameTestHelper h,GoldenFoxUnit source){return SkillService.request(playerNamed(h,source.getOwnerName()),request(source));}
    private static GameTestSequence ready(GameTestHelper h,Entity...entities){return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>3&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"real published fixture ready: "+e.getUUID()+" ticks="+e.tickCount);}).thenExecute(()->System.out.println("FOX_BLESSING_READY testTick="+h.getTick()+" fixtures="+Arrays.stream(entities).map(e->e.getId()+":"+e.tickCount).toList()));}
    private static void require(GameTestHelper h,SkillResult actual,SkillResult expected,String why){h.assertTrue(actual==expected,why+": expected "+expected+", got "+actual);}
    private static void denied(GameTestHelper h,SkillResult actual,String why){h.assertTrue(actual!=SkillResult.SUCCESS,why+": got "+actual);}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<.0003,why+": expected "+expected+", got "+actual);}
    private static void pass(String name){System.out.println("DREAMSEEKERS_FOX_BLESSING_"+name+"_PASS");}
    private static String account(String suffix){String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500,123));}return name;}
    private static void ledger(GameTestHelper h,String suffix){var r=ResourcesServerEvents.resourcesList.stream().filter(e->e.ownerName.equals(PREFIX+suffix)).findFirst().orElseThrow();h.assertTrue(r.food==500&&r.wood==500&&r.ore==500&&r.emerald==123,"ordinary blessing neither spends nor grants global resources");}
    private static GoldenFoxUnit fox(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());owned(h,e,suffix,x,z);return e;}
    private static PinkSlimeUnit slime(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());owned(h,e,suffix,x,z);return e;}
    private static SlimeUnit coreSlime(GameTestHelper h,String suffix,int x,int z){var e=EntityRegistrar.SLIME_UNIT.get().create(h.getLevel());owned(h,e,suffix,x,z);return e;}
    private static PillagerUnit pillager(GameTestHelper h,String suffix,int x,int z){var e=EntityRegistrar.PILLAGER_UNIT.get().create(h.getLevel());var bow=new ItemStack(Items.CROSSBOW);bow.enchant(Enchantments.QUICK_CHARGE,3);e.setItemSlot(EquipmentSlot.MAINHAND,bow);owned(h,e,suffix,x,z);return e;}
    private static MilitiaUnit militia(GameTestHelper h,String suffix,int x,int z,boolean bow){var e=EntityRegistrar.MILITIA_UNIT.get().create(h.getLevel());e.swapWeapons(bow);owned(h,e,suffix,x,z);return e;}
    private static net.minecraft.world.entity.Mob zombie(GameTestHelper h,String suffix,int x,int z){var e=EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());owned(h,e,suffix,x,z);return e;}
    private static void owned(GameTestHelper h,Mob e,String suffix,int x,int z){((Unit)e).setOwnerName(account(suffix));place(h,e,x,z);e.setNoAi(true);}
    private static FakePlayer player(GameTestHelper h,String suffix){return playerNamed(h,account(suffix));}
    private static FakePlayer playerNamed(GameTestHelper h,String name){return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));}
    private static void moveFixture(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);e.setDeltaMovement(Vec3.ZERO);}
    private static void place(GameTestHelper h,Entity e,int x,int z){h.assertTrue(e!=null,"registered fixture created");moveFixture(h,e,x,z);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"fixture enters real level");}
    private static void floor(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,7,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<7;y++)h.getLevel().setBlock(p.offset(x,y,z),(x==0||x==23||z==0||z==23?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}}
}
