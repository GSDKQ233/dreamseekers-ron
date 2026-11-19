// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** Real authenticated self casts and real installed ordinary melee goals. Test two uses natural AI
 * cadence; tests three/four explicitly schedule the installed goal under NoAI, like the Bone/Beetle
 * controlled fixtures. No direct doHurtTarget, hand-created hit authority, or injected skill modifier.
 */
@GameTestHolder("dreamseekers_shadow_corrosion_touch")
@PrefixGameTestTemplate(false)
public final class ShadowCorrosionTouchSkillGameTests {
    private static final String BATCH="dreamseekers_shadow_corrosion_touch",PREFIX="Corrosion_";
    private static final List<Entity> ENTITIES=new ArrayList<>();
    private static final Set<String> ACCOUNTS=new LinkedHashSet<>();
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();
    private static final Set<ServerPlayer> SYNC_MEMBERS=new LinkedHashSet<>();
    private static final Probe PROBE=new Probe();
    private static boolean listening;
    private static final List<Object> EXTRA_LISTENERS=new ArrayList<>();

    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        for(Object listener:EXTRA_LISTENERS)MinecraftForge.EVENT_BUS.unregister(listener);EXTRA_LISTENERS.clear();
        if(listening){MinecraftForge.EVENT_BUS.unregister(PROBE);listening=false;}
        PROBE.traces.clear();PROBE.hurtOnce.clear();PROBE.damageOnce.clear();PROBE.expiries.clear();
        for(var member:List.copyOf(SYNC_MEMBERS))syncMembership(member,false);
        for(String a:ACCOUNTS)for(String b:ACCOUNTS)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.players.removeIf(p->ACCOUNTS.contains(p.getGameProfile().getName()));
        PlayerServerEvents.rtsPlayers.removeIf(p->ACCOUNTS.contains(p.name));
        ResourcesServerEvents.resourcesList.removeIf(r->ACCOUNTS.contains(r.ownerName));
        for(Entity e:List.copyOf(ENTITIES))if(!e.isRemoved()) {
            if(e instanceof ServerPlayer p)p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
            else e.discard();
        }
        ENTITIES.clear();ACCOUNTS.clear();PLAYERS.clear();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=520)
    public static void authenticatedSelfCodecAndNaturalOwnerSyncKeepIndependentHundredAndThreeTwentyClocks(GameTestHelper h) {
        room(h);var owner=player(h,"Authority");var stranger=player(h,"AuthorityOther");
        owner.connection=new RecordingHandler(owner);stranger.connection=new RecordingHandler(stranger);
        syncMembership(owner,true);syncMembership(stranger,true);
        var source=ghost(h,"Authority",5,5);var other=ghost(h,"AuthorityOther",20,5);
        var wrong=DreamseekersMod.FIREFLY_WORKER.get().create(h.getLevel());owned(h,wrong,"Authority",5,20);
        long[] at={-1};
        ready(h,source,other,wrong)
          .thenWaitUntil(()->h.assertTrue(syncs(owner,source).size()>=2,"natural source tick publishes both initial and ten-tick encoded owner Sync"))
          .thenExecute(()->{
              var initial=syncs(owner,source);h.assertTrue(initial.get(0).packet().cooldownTicks()==0
                      &&initial.get(1).gameTime()-initial.get(0).gameTime()==10,"real initial/heartbeat use ordinary world clock, not direct state.sync");
              h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayerByName(source.getOwnerName())==owner,"real PlayerList name lookup owns synchronization");
              h.assertTrue(syncs(stranger,source).isEmpty(),"foreign actual player receives no source cooldown/UUID payload");
              var packet=new SkillNetwork.Cast(request(source));var bytes=new FriendlyByteBuf(Unpooled.buffer());SkillNetwork.Cast decoded;
              try{packet.encode(bytes);decoded=SkillNetwork.Cast.decode(bytes);h.assertTrue(decoded.equals(packet)&&bytes.readableBytes()==0,"shared actual Cast codec preserves exact self identities/revision");}finally{bytes.release();}
              long revision=source.rts().skills().revision();
              require(h,decoded.execute(null),SkillResult.INVALID_REQUEST,"missing authenticated sender");
              require(h,decoded.execute(stranger),SkillResult.NOT_OWNER,"foreign sender cannot arm ghost");
              require(h,SkillService.request(owner,new SkillRequest(UnitSkill.SHADOW_CORROSION_TOUCH,source.getId(),UUID.randomUUID(),revision,source.getId(),source.getUUID())),SkillResult.INVALID_SOURCE,"stale source UUID");
              require(h,SkillService.request(owner,new SkillRequest(UnitSkill.SHADOW_CORROSION_TOUCH,source.getId(),source.getUUID(),revision,source.getId(),UUID.randomUUID())),SkillResult.INVALID_TARGET,"stale self target UUID");
              require(h,SkillService.request(owner,new SkillRequest(UnitSkill.SHADOW_CORROSION_TOUCH,source.getId(),source.getUUID(),revision,other.getId(),other.getUUID())),SkillResult.INVALID_TARGET,"self active cannot arm another body");
              require(h,SkillService.request(owner,new SkillRequest(UnitSkill.SHADOW_CORROSION_TOUCH,wrong.getId(),wrong.getUUID(),wrong.rts().skills().revision(),wrong.getId(),wrong.getUUID())),SkillResult.INVALID_SOURCE,"worker cannot inherit ghost skill");
              require(h,java.util.concurrent.CompletableFuture.supplyAsync(()->decoded.execute(owner)).join(),SkillResult.INVALID_REQUEST,"off-server-thread request is inert");
              h.assertTrue(cd(source)==0&&arm(source)==0&&source.rts().skills().revision()==revision,"denials never grant arm or alter paid clock/revision");
              h.assertTrue(source.getAbilities().get().equals(List.of(UnitSkillAbility.SHADOW_CORROSION_TOUCH)),"exact one approved active, no old special/research button");
              at[0]=h.getLevel().getGameTime();require(h,decoded.execute(owner),SkillResult.SUCCESS,"owner genuinely arms next ordinary melee");
              require(h,decoded.execute(owner),SkillResult.REPLAY,"successful request token cannot replay");
              require(h,cast(h,source),SkillResult.COOLDOWN,"fresh request cannot refresh paid arm");
              h.assertTrue(cd(source)==320&&arm(source)==100,"exact initial self CD320/arm100");
              var last=syncs(owner,source).get(syncs(owner,source).size()-1).packet();
              h.assertTrue(last.cooldownTicks()==320&&last.revision()==source.rts().skills().revision()&&last.skill()==UnitSkill.SHADOW_CORROSION_TOUCH,"actual cast encodes new owner revision/cooldown");
              bank(h);
          }).thenIdle(11).thenExecute(()->{
              h.assertTrue(syncs(owner,source).stream().anyMatch(s->s.gameTime()==at[0]+10&&s.packet().cooldownTicks()==310),"real natural tick sends exact tenth-tick paid heartbeat");
              h.assertTrue(syncs(stranger,source).isEmpty(),"heartbeat does not leak to foreign owner");
          }).thenIdle(88).thenExecute(()->h.assertTrue(h.getLevel().getGameTime()==at[0]+99&&arm(source)==1&&cd(source)==221,"unspent arm last tick independent of paid clock"))
          .thenIdle(1).thenExecute(()->h.assertTrue(arm(source)==0&&cd(source)==220,"arm ends at100, never refunded or extended"))
          .thenIdle(219).thenExecute(()->{h.assertTrue(cd(source)==1&&arm(source)==0,"exact last paid tick");require(h,cast(h,source),SkillResult.COOLDOWN,"still paid at319");})
          .thenIdle(1).thenExecute(()->{h.assertTrue(cd(source)==0,"paid clock ready exactly320");require(h,cast(h,source),SkillResult.SUCCESS,"real fresh owner cast after exact expiry");bank(h);pass("AUTH_SELF_CODEC_NATURAL_OWNER_SYNC_100_320");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=220)
    public static void naturalOrdinaryGoalAddsArmorOnlyAfterFirstHealthReceiptAndBoneStillAmplifiesOrdinaryHits(GameTestHelper h) {
        room(h);partition(h,15);
        var plain=ghost(h,"Natural",7,7);var marked=ghost(h,"Natural",7,23);
        var a=target(h,"NaturalEnemy",8,7,6);var b=target(h,"NaturalEnemy",8,23,6);
        var bone=bone(h,"Natural",10,25);var traces=List.of(watch(plain,a),watch(marked,b));
        long[] castAt={-1};
        var seq=ready(h,plain,marked,a,b,bone).thenExecute(()->{
            for(var s:List.of(plain,marked))h.assertTrue(s.getAttackGoal() instanceof TeamSafeMeleeAttackGoal,"exact original installed ordinary goal");
            order(h,plain,UnitAction.ATTACK,a.getId(),BlockPos.ZERO);order(h,marked,UnitAction.ATTACK,b.getId(),BlockPos.ZERO);
        }).thenWaitUntil(()->h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(plain.getId())&&!UnitServerEvents.hasPendingImmediateAction(marked.getId())
                &&plain.getTarget()==a&&marked.getTarget()==b&&plain.getTargetGoal().forced&&marked.getTargetGoal().forced,"real authenticated ATTACK dispatch selects each exact target"))
          .thenExecute(()->{
              h.assertTrue(traces.stream().allMatch(t->t.hits.isEmpty()&&t.incoming==0)&&a.getHealth()==1000&&b.getHealth()==1000,"controlled NoAI preparation has no hidden prehit");
              require(h,mark(h,bone,b),SkillResult.SUCCESS,"actual same-owner Bone target mark before ordinary ghost melee");
              castAt[0]=h.getLevel().getGameTime();
              for(var s:List.of(plain,marked)){require(h,cast(h,s),SkillResult.SUCCESS,"genuine self arm before natural goal");s.setHoldPosition(true);s.setNoAi(false);}
          });
        // Entire timeline is registered now. The observer reads HP only at real Level END.
        for(int tick=1;tick<=65;tick++)seq.thenIdle(1).thenExecute(()->{
            for(var t:traces)if(t.hits.size()>=2)t.source.setNoAi(true); // only prevents a third native ordinary hit
        });
        seq.thenExecute(()->{
            for(int lane=0;lane<traces.size();lane++){
                var t=traces.get(lane);h.assertTrue(t.hits.size()==2&&t.incoming==2,"exact two natural actual ordinary contacts, no extra damage contamination: "+t.describe());
                var first=t.hits.get(0);var second=t.hits.get(1);double boneFactor=lane==1?1.12:1;
                h.assertTrue(first.time>=castAt[0]&&first.time<castAt[0]+100&&second.time>first.time,"actual natural hit timing, not manually drained ordinary cooldown");
                h.assertTrue(first.armorBefore==6&&!first.hadDebuff&&first.armorAfter==4&&first.hasDebuff&&first.armAfter==0,"first base hurt returns before real additive debuff installs/one token consumes");
                close(h,first.hpBefore-first.hpAfter,first.nativeExpected*boneFactor,"first ordinary HP uses pre-debuff armor with only existing Bone multiplier; never 120-percent burst");
                h.assertTrue(second.armorBefore==4&&second.hadDebuff&&second.armorAfter==4&&second.hasDebuff,"next ordinary attack sees prior installed armor, never stacks");
                close(h,second.hpBefore-second.hpAfter,second.nativeExpected*boneFactor,"second native actual HP reflects new armor and independent ordinary Bone mark");
                h.assertTrue(second.hpBefore-second.hpAfter>first.hpBefore-first.hpAfter,"later ordinary hit genuinely benefits from lower armor");
                h.assertTrue(first.expiry==second.expiry,"ordinary second contact never refreshes original120tick deadline");
                h.assertTrue(cd(t.source)==320-(h.getLevel().getGameTime()-castAt[0]),"original paid cooldown proceeds naturally");
                System.out.println("SHADOW_CORROSION_NATURAL lane="+lane+" "+t.describe());
            }
            h.assertTrue(CoordinatedMark.ticksRemaining(b,marked.getOwnerName())>0,"Bone was live for both genuine ordinary hits");
            bank(h);pass("NATURAL_GOAL_AFTER_FIRST_HP_NEXT_DAMAGE_BONE_ORDINARY");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=160)
    public static void canceledZeroAndNativeAbsorptionKeepTokenWhileActualEnemyGelAndIntegerArmorDecreaseConsumeIt(GameTestHelper h) {
        room(h);
        var source=ghost(h,"Receipt",5,6);var victim=target(h,"ReceiptEnemy",10,6,6);var slime=slime(h,"ReceiptEnemy",12,9);
        var zero=ghost(h,"NoArmor",5,20);var zeroTarget=target(h,"NoArmorEnemy",10,20,0);
        var rounded=ghost(h,"Rounded",21,20);var roundedTarget=target(h,"RoundedEnemy",26,20,15);
        UUID small=UUID.fromString("5c5d77ba-fbc5-40ac-8c83-b1dfd34bdfe5");
        roundedTarget.getAttribute(Attributes.ARMOR).addPermanentModifier(new AttributeModifier(small,"declared original small armor multiplier",-.9,AttributeModifier.Operation.MULTIPLY_TOTAL));
        ready(h,source,victim,slime,zero,zeroTarget,rounded,roundedTarget).thenExecute(()->{
            require(h,cast(h,source),SkillResult.SUCCESS,"real receipt-test arm");double expected=nativeDamage(source,victim);h.assertTrue(expected>0&&expected<10,"real positive armor attenuates original ten");
            watchHurt(victim,e->e.setCanceled(true));close(h,controlledHit(h,source,victim),0,"actual Hurt cancellation has no health receipt");unspent(h,source,victim,"Hurt canceled");
            watchDamage(victim,e->e.setCanceled(true));close(h,controlledHit(h,source,victim),0,"managed actual final cancellation cannot apply HP");unspent(h,source,victim,"Damage canceled");
            watchDamage(victim,e->e.setAmount(0));close(h,controlledHit(h,source,victim),0,"actual final zero creates no health receipt");unspent(h,source,victim,"Damage zero");
            victim.setAbsorptionAmount(50);close(h,controlledHit(h,source,victim),0,"native absorption-only does not spend touch");
            close(h,victim.getAbsorptionAmount(),50-expected,"real native absorption consumed unmodified ordinary amount");unspent(h,source,victim,"native absorption only");
            victim.setAbsorptionAmount(0);require(h,gel(h,slime,victim),SkillResult.SUCCESS,"actual enemy owner's Gel cast supplies shield twelve");
            close(h,SkillEffects.shieldRemaining(victim),12,"real Gel starts at twelve");
            close(h,controlledHit(h,source,victim),0,"positive armor ordinary ten is fully consumed by actual Gel, HP unchanged");
            close(h,SkillEffects.shieldRemaining(victim),12-expected,"real enemy Gel debit is exact ordinary pre-corrosion amount");
            h.assertTrue(arm(source)==0&&ShadowCorrosionTouch.hasDebuff(victim)&&victim.getArmorValue()==4&&cd(source)==320,"Gel-only receipt installs real armor decrease and consumes exactly one token, not CD");
            require(h,cast(h,zero),SkillResult.SUCCESS,"independent zero-armor arm");
            close(h,controlledHit(h,zero,zeroTarget),10,"zero armor takes legal ordinary base HP");unspent(h,zero,zeroTarget,"zero actual armor cannot accept useless modifier");
            h.assertTrue(zeroTarget.getAttribute(Attributes.ARMOR).getModifiers().isEmpty(),"rejected zero armor leaves no transient UUID");
            zeroTarget.getAttribute(Attributes.ARMOR).setBaseValue(3);double now=nativeDamage(zero,zeroTarget);
            close(h,controlledHit(h,zero,zeroTarget),now,"same retained token later sees genuinely positive original armor");
            h.assertTrue(arm(zero)==0&&ShadowCorrosionTouch.hasDebuff(zeroTarget)&&zeroTarget.getArmorValue()==1,"later legal integer decrease consumes held token normally");
            require(h,cast(h,rounded),SkillResult.SUCCESS,"independent original multiplier arm");
            int before=roundedTarget.getArmorValue();h.assertTrue(before==1,"real native integer armor is one before attempted small additive contribution");
            double roundedExpected=nativeDamage(rounded,roundedTarget);
            close(h,controlledHit(h,rounded,roundedTarget),roundedExpected,"rounded lane still loses legitimate ordinary base HP");
            h.assertTrue(roundedTarget.getArmorValue()==before,"candidate contribution cannot change actual integer armor under original multiplier");
            unspent(h,rounded,roundedTarget,"no actual integer armor decrease");
            h.assertTrue(roundedTarget.getAttribute(Attributes.ARMOR).getModifiers().size()==1&&roundedTarget.getAttribute(Attributes.ARMOR).getModifier(small)!=null,"failed trial removes only its own UUID, keeps original permanent multiplier");
            roundedTarget.getAttribute(Attributes.ARMOR).removeModifier(small);
            double retry=nativeDamage(rounded,roundedTarget);close(h,controlledHit(h,rounded,roundedTarget),retry,"held token retries after declared permanent multiplier fixture is removed");
            h.assertTrue(roundedTarget.getArmorValue()==13&&arm(rounded)==0&&ShadowCorrosionTouch.hasDebuff(roundedTarget),"real later fifteen-to-thirteen succeeds");
            bank(h);pass("EXACT_CANCEL_ZERO_NATIVE_ABSORB_GEL_RECEIPT_INTEGER_EFFECT");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=300)
    public static void realTransientAdditionUsesOriginalMultipliersNeverStacksAndExpiresWithoutRewritingPermanentArmorOrNbt(GameTestHelper h) {
        room(h);var source=ghost(h,"Modifier",5,8);var second=ghost(h,"ModifierSecond",5,20);
        var victim=target(h,"ModifierEnemy",12,8,6);var baseline=target(h,"ModifierEnemy",23,22,6);
        UUID add=UUID.fromString("cd7b4c42-ef18-4f51-ac55-66dcc4b1dbaf"),multiply=UUID.fromString("8c4517eb-3c54-4099-9288-057855b8c5c4");
        for(var target:List.of(victim,baseline)){
            target.getAttribute(Attributes.ARMOR).addPermanentModifier(new AttributeModifier(add,"declared permanent armor research",2,AttributeModifier.Operation.ADDITION));
            target.getAttribute(Attributes.ARMOR).addPermanentModifier(new AttributeModifier(multiply,"declared permanent total multiplier",.5,AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        UUID[] transientId={null};long[] installed={-1};Expiry[] expiry={null};
        ready(h,source,second,victim,baseline).thenExecute(()->{
            close(h,victim.getAttributeValue(Attributes.ARMOR),12,"unmodified permanent layers produce native twelve armor");
            double first=nativeDamage(source,victim);require(h,cast(h,source),SkillResult.SUCCESS,"real self arm before first ordinary installed goal");
            close(h,controlledHit(h,source,victim),first,"current HP still uses original twelve armor, no retroactive subtraction");
            installed[0]=h.getLevel().getGameTime();
            var modifiers=victim.getAttribute(Attributes.ARMOR).getModifiers();
            var added=modifiers.stream().filter(m->!m.getId().equals(add)&&!m.getId().equals(multiply)).toList();
            h.assertTrue(added.size()==1&&added.get(0).getOperation()==AttributeModifier.Operation.ADDITION&&added.get(0).getAmount()==-2,"only real installed contribution is exact additive negative two");
            transientId[0]=added.get(0).getId();
            h.assertTrue(victim.getArmorValue()==9&&baseline.getArmorValue()==12,"original total multiplier turns additive-minus-two into actual-minus-three, not forced final-minus-two");
            close(h,victim.getAttribute(Attributes.ARMOR).getBaseValue(),6,"skill never writes base armor");
            require(h,cast(h,second),SkillResult.SUCCESS,"different-owner second real source has independent token");
            double original=nativeDamage(second,victim);close(h,controlledHit(h,second,victim),original,"already-corroded ordinary hit retains legal base");
            h.assertTrue(arm(second)==100&&ShadowCorrosionTouch.remainingDebuffTicks(victim)==120&&victim.getAttribute(Attributes.ARMOR).getModifiers().size()==3,"same named target from another source neither stacks nor consumes token");
            for(var target:List.of(victim,baseline))target.getAttribute(Attributes.ARMOR).setBaseValue(10);
            h.assertTrue(victim.getArmorValue()==15&&baseline.getArmorValue()==18,"later base research re-evaluates native layers, no stale armor snapshot restore");
            CompoundTag nbt=victim.saveWithoutId(new CompoundTag());
            h.assertTrue(serializedModifierCount(nbt,add)==1&&serializedModifierCount(nbt,multiply)==1&&serializedModifierCount(nbt,transientId[0])==0,"actual native NBT stores permanent UUIDs only; temporary armor contribution is never serialized");
            close(h,savedArmorBase(nbt),10,"actual serialized base is current original value, not skill-adjusted base");
            h.assertTrue(ShadowCorrosionTouch.hasDebuff(victim)&&victim.getAttribute(Attributes.ARMOR).getModifier(transientId[0])!=null,"save observation itself does not remove live temporary contribution");
            for(var target:List.of(victim,baseline)){
                target.getAttribute(Attributes.ARMOR).removeModifier(multiply);
                target.getAttribute(Attributes.ARMOR).addPermanentModifier(new AttributeModifier(multiply,"declared changed original multiplier",-.5,AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            h.assertTrue(victim.getArmorValue()==5&&baseline.getArmorValue()==6,"later multiplier changes naturally alter contribution; no forced two-point compensation");
            for(var target:List.of(victim,baseline))target.getAttribute(Attributes.ARMOR).setBaseValue(0);
            h.assertTrue(victim.getArmorValue()==0&&baseline.getArmorValue()==1&&ShadowCorrosionTouch.hasDebuff(victim),"native minimum zero remains, existing valid instance is not refreshed or replaced by dynamic zero");
            expiry[0]=new Expiry(victim,transientId[0],installed[0]+120);listen();PROBE.expiries.add(expiry[0]);
        }).thenIdle(20).thenExecute(()->{
            h.assertTrue(ShadowCorrosionTouch.remainingDebuffTicks(victim)==100&&arm(second)==80,"both original clocks naturally advance before duplicate attempt");
            close(h,controlledHit(h,second,victim),10,"existing named contribution at native zero keeps ordinary base legal");
            h.assertTrue(ShadowCorrosionTouch.remainingDebuffTicks(victim)==100&&arm(second)==80&&victim.getAttribute(Attributes.ARMOR).getModifier(transientId[0])!=null,"later duplicate neither refreshes120 nor spends fresh token nor removes original UUID");
        }).thenIdle(101).thenExecute(()->{
            h.assertTrue(expiry[0].sawLast&&expiry[0].sawDeadline&&expiry[0].lastPresent&&!expiry[0].deadlinePresent,"real Level END sees modifier at119 and removes it at exact120, never an assertion before lifecycle maintenance");
            h.assertTrue(!ShadowCorrosionTouch.hasDebuff(victim)&&ShadowCorrosionTouch.remainingDebuffTicks(victim)==0&&victim.getAttribute(Attributes.ARMOR).getModifier(transientId[0])==null,"original temporary instance truly gone");
            h.assertTrue(victim.getArmorValue()==baseline.getArmorValue()&&victim.getArmorValue()==1,"expiry re-exposes current original layers, not pre-cast armor snapshot");
            close(h,victim.getAttribute(Attributes.ARMOR).getBaseValue(),0,"expiry preserves explicitly changed original base");
            h.assertTrue(victim.getAttribute(Attributes.ARMOR).getModifier(add).getAmount()==2&&victim.getAttribute(Attributes.ARMOR).getModifier(multiply).getAmount()==-.5,"expiry never deletes/replaces permanent research modifiers");
            h.assertTrue(cd(source)==199&&cd(second)==199&&arm(source)==0&&arm(second)==0,"both paid cooldowns survive effect/unused-arm expiration");
            bank(h);pass("TRANSIENT_ADDITION_MULTIPLIER_ZERO_NBT_NONSTACK_EXACT120_END");
        }).thenSucceed();
    }


    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=180)
    public static void exactOrdinaryReceiptsRejectReentryAndCallbackCommandsOrEpochChangesWithoutDeletingBaseHits(GameTestHelper h) {
        room(h);var same=ghost(h,"Reentry",5,6);var a=target(h,"ReentryEnemy",10,6,6);
        var different=ghost(h,"Different",5,15);var b=target(h,"DifferentEnemy",10,15,6);
        var command=ghost(h,"Callback",5,24);var c=target(h,"CallbackEnemy",10,24,6);
        boolean[] callbacks=new boolean[5];long[] at={-1};double[] commandBase={0};
        ready(h,same,a,different,b,command,c).thenExecute(()->{
            at[0]=h.getLevel().getGameTime();require(h,cast(h,same),SkillResult.SUCCESS,"real armed same-source recursion lane");double base=nativeDamage(same,a);
            watchHurt(a,event->{callbacks[0]=true;a.invulnerableTime=0;h.assertTrue(a.hurt(event.getSource(),10),"actual same exact DamageSource reenters native hurt");a.invulnerableTime=0;});
            close(h,controlledHit(h,same,a),base*2,"same-DS recursion preserves both actual ordinary bases, not extra corrosion damage");
            h.assertTrue(callbacks[0]&&arm(same)==100&&!ShadowCorrosionTouch.hasDebuff(a),"recursive invocation cannot consume original arm or attach twice");
            close(h,controlledHit(h,same,a),base,"subsequent clean original installed goal still uses original armor for current HP");
            h.assertTrue(arm(same)==0&&a.getArmorValue()==4&&ShadowCorrosionTouch.hasDebuff(a),"clean receipt later consumes exactly the retained token");
            require(h,cast(h,different),SkillResult.SUCCESS,"independent different-source-object receipt lane");double otherBase=nativeDamage(different,b);
            watchDamage(b,event->{callbacks[1]=true;b.invulnerableTime=0;var inner=b.damageSources().mobAttack(different);h.assertTrue(inner!=event.getSource()&&b.hurt(inner,10),"real different DamageSource nested base hurt");b.invulnerableTime=0;event.setAmount(0);});
            close(h,controlledHit(h,different,b),otherBase,"outer final zero cannot borrow independent nested positive HP as own receipt");
            h.assertTrue(callbacks[1]&&arm(different)==100&&!ShadowCorrosionTouch.hasDebuff(b),"different DS has no authority to spend outer touch");
            watchDamage(b,event->{callbacks[2]=true;String old=b.getOwnerName();b.setOwnerName(old+"_ABA");b.setOwnerName(old);});
            close(h,controlledHit(h,different,b),otherBase,"target owner ABA retracts only proposed corrosion, never legal ordinary base");
            h.assertTrue(callbacks[2]&&arm(different)==100&&!ShadowCorrosionTouch.hasDebuff(b),"invalid old target stamp cannot attach after ABA");
            close(h,controlledHit(h,different,b),otherBase,"fresh clean ordinary goal independently captures current target epoch");
            h.assertTrue(arm(different)==0&&b.getArmorValue()==4,"new legal hit may spend original still-live token after target ABA");
            require(h,cast(h,command),SkillResult.SUCCESS,"real arm before authentic new-command callback");commandBase[0]=nativeDamage(command,c);
            watchDamage(c,event->{callbacks[3]=true;order(h,command,UnitAction.STOP,-1,BlockPos.ZERO);h.assertTrue(UnitServerEvents.hasPendingImmediateAction(command.getId()),"real owner STOP is pending inside actual Damage callback");});
            close(h,controlledHit(h,command,c),commandBase[0],"new command during hurt cannot delete legitimate ordinary base");
            h.assertTrue(callbacks[3]&&arm(command)==100&&!ShadowCorrosionTouch.hasDebuff(c),"pending command revokes only this hit's attachment");
        }).thenWaitUntil(()->h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(command.getId()),"real queued immediate STOP naturally dispatches"))
          .thenExecute(()->{
              h.assertTrue(arm(command)>0&&cd(command)==320-(h.getLevel().getGameTime()-at[0]),"actual command dispatch retains finite original arm and paid deadline");
              watchDamage(c,event->{callbacks[4]=true;String old=command.getOwnerName();command.setOwnerName(old+"_ABA");command.setOwnerName(old);});
              close(h,controlledHit(h,command,c),commandBase[0],"source owner ABA during callback preserves ordinary base but invalidates old skill epoch");
              h.assertTrue(callbacks[4]&&arm(command)==0&&!ShadowCorrosionTouch.hasDebuff(c)&&cd(command)>0,"source ABA clears short authority, never refunds paid cooldown");
              bank(h);pass("EXACT_REENTRY_DIFFERENT_DS_COMMAND_SOURCE_TARGET_EPOCH_BASE");
          }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=380)
    public static void actualSporeBirthUnifiesManagedAndVanillaChoiceAndNeverRetriesAStaleSelectedCandidate(GameTestHelper h) {
        room(h);var attacker=ghost(h,"CleanseEnemy",5,5);var lateAttacker=ghost(h,"CleanseEnemy",5,14);var victim=target(h,"CleanseFriend",8,8,6);
        var foxes=List.of(target(h,"CleanseFriend",4,8,0),target(h,"CleanseFriend",4,11,0),target(h,"CleanseFriend",4,14,0),target(h,"CleanseFriend",4,5,0));
        var staleAttacker=ghost(h,"StaleEnemy",18,20);var first=target(h,"StaleFriend",21,21,6);var second=target(h,"StaleFriend",23,21,6);var staleFox=target(h,"StaleFriend",18,23,0);
        var sorted=new ArrayList<>(List.of(first,second));sorted.sort(Comparator.comparing(e->e.getUUID().toString()));var early=sorted.get(0);var late=sorted.get(1);
        var removal=new RemovalProbe();EXTRA_LISTENERS.add(removal);MinecraftForge.EVENT_BUS.register(removal);
        int[] callbacks={0};long[] at={-1};net.minecraft.world.effect.MobEffectInstance[] slow={null};
        var all=new ArrayList<Entity>(List.of(attacker,lateAttacker,victim,staleAttacker,first,second,staleFox));all.addAll(foxes);
        ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
            require(h,cast(h,attacker),SkillResult.SUCCESS,"genuine touch before managed/vanilla coexistence");controlledHit(h,attacker,victim);
            h.assertTrue(ShadowCorrosionTouch.hasDebuff(victim),"actual installed-goal corrosion exists before seed birth");
            h.assertTrue(victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,400)),"real finite native slowness added independently");slow[0]=victim.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            at[0]=h.getLevel().getGameTime();require(h,seed(h,foxes.get(0),8,10),SkillResult.SUCCESS,"real registered ground seed birth activates shared choice");
            h.assertTrue(!ShadowCorrosionTouch.hasDebuff(victim)&&victim.getArmorValue()==6&&victim.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)==slow[0],"stable full ID dreamseekers before minecraft removes exactly managed corrosion, never also vanilla");
            h.assertTrue(removal.count(victim)==0&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)==200,"managed removal emits no fake native Remove and begins genuine shared200 protection");
            require(h,seed(h,foxes.get(1),10,8),SkillResult.SUCCESS,"second real source may birth but cannot cleanse protected target");
            h.assertTrue(victim.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)==slow[0]&&removal.count(victim)==0&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)==200,"cross-source throttle blocks second native removal without refresh");
            require(h,cast(h,staleAttacker),SkillResult.SUCCESS,"independent real managed effect on later UUID candidate");controlledHit(h,staleAttacker,late);
            h.assertTrue(late.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,400))&&early.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS,400)),"two real sorted target candidates, only earlier uses vanilla event path");
            removal.once.put(early.getUUID(),event->{callbacks[0]++;h.assertTrue(event.getEffect()==net.minecraft.world.effect.MobEffects.WEAKNESS,"earlier selected actual native effect reaches real Remove event");String old=staleAttacker.getOwnerName();staleAttacker.setOwnerName(old+"_ABA");staleAttacker.setOwnerName(old);});
            require(h,seed(h,staleFox,22,23),SkillResult.SUCCESS,"real activation captures all candidates before sorted genuine Remove callback");
            h.assertTrue(callbacks[0]==1&&!early.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(early)==200,"earlier actual native removal succeeds normally");
            h.assertTrue(!ShadowCorrosionTouch.hasDebuff(late)&&late.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)&&removal.count(late)==0&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(late)==0,"stale later selected managed candidate never falls back to vanilla or grants protection");
        }).thenIdle(180).thenExecute(()->{
            require(h,cast(h,lateAttacker),SkillResult.SUCCESS,"fresh real arm while target's original cleanse protection remains");controlledHit(h,lateAttacker,victim);
            h.assertTrue(ShadowCorrosionTouch.remainingDebuffTicks(victim)==96&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)==20,"protection is a removal throttle, not immunity; two active real auras reduce new duration only once");
        }).thenIdle(19).thenExecute(()->{
            h.assertTrue(h.getLevel().getGameTime()==at[0]+199&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)==1,"last true protection tick199");
            require(h,seed(h,foxes.get(2),8,6),SkillResult.SUCCESS,"third actual source births at protection199");
            h.assertTrue(ShadowCorrosionTouch.remainingDebuffTicks(victim)==77&&victim.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)==slow[0],"protected tick199 removes neither live new managed nor old native negative");
        }).thenIdle(1).thenExecute(()->{
            h.assertTrue(h.getLevel().getGameTime()==at[0]+200&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)==0,"original shared protection expires exactly200");
            require(h,seed(h,foxes.get(3),6,8),SkillResult.SUCCESS,"fourth original real caster acts after exact shared deadline");
            h.assertTrue(!ShadowCorrosionTouch.hasDebuff(victim)&&victim.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)==slow[0]&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)==200&&removal.count(victim)==0,"fresh activation again removes only live managed choice, never both routes");
            bank(h);pass("REAL_SEED_UNIFIED_ID_ONE_REMOVAL_STALE_NO_RETRY_199_200");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=300)
    public static void overlappingRealAurasShortenOnlyNewCorrosionOnceAndNeverRefreshOrRewriteALaterEntrant(GameTestHelper h) {
        room(h);var oldSource=ghost(h,"AuraEnemy",21,10);var newSource=ghost(h,"AuraEnemy",5,10);var duplicate=ghost(h,"AuraEnemy",5,20);
        var oldTarget=target(h,"AuraFriend",24,10,6);var newTarget=target(h,"AuraFriend",10,10,6);
        var foxA=target(h,"AuraFriend",6,6,0);var foxB=target(h,"AuraFriend",6,10,0);long[] at={-1};Expiry[] ends=new Expiry[2];
        ready(h,oldSource,newSource,duplicate,oldTarget,newTarget,foxA,foxB).thenExecute(()->{
            at[0]=h.getLevel().getGameTime();require(h,cast(h,oldSource),SkillResult.SUCCESS,"old instance before any friendly aura");controlledHit(h,oldSource,oldTarget);
            h.assertTrue(ShadowCorrosionTouch.remainingDebuffTicks(oldTarget)==120,"unprotected original duration120");
            require(h,seed(h,foxA,12,10),SkillResult.SUCCESS,"first actual registered aura born without a negative on new target");
            require(h,seed(h,foxB,10,12),SkillResult.SUCCESS,"second overlapping actual aura born");
            h.assertTrue(com.dreamseekers.spore.SporeLease.activeFor(foxA)!=null&&com.dreamseekers.spore.SporeLease.activeFor(foxB)!=null,"both real published sources have active leases, no hand-injected provider");
            require(h,cast(h,newSource),SkillResult.SUCCESS,"new genuine touch inside both real auras");controlledHit(h,newSource,newTarget);
            h.assertTrue(ShadowCorrosionTouch.remainingDebuffTicks(newTarget)==96&&newTarget.getArmorValue()==4,"new120 becomes96 once, not96*.8 from second aura");
            position(oldTarget,Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,1,11))));
            h.assertTrue(ShadowCorrosionTouch.remainingDebuffTicks(oldTarget)==120&&oldTarget.getArmorValue()==4&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(oldTarget)==0,"already-live instance enters later without periodic cleanse or duration rewrite");
            ends[0]=new Expiry(newTarget,ShadowCorrosionTouch.MODIFIER_ID,at[0]+96);ends[1]=new Expiry(oldTarget,ShadowCorrosionTouch.MODIFIER_ID,at[0]+120);listen();PROBE.expiries.addAll(List.of(ends));
        }).thenIdle(20).thenExecute(()->{
            h.assertTrue(ShadowCorrosionTouch.remainingDebuffTicks(newTarget)==76&&ShadowCorrosionTouch.remainingDebuffTicks(oldTarget)==100,"original distinct clocks advanced naturally");
            require(h,cast(h,duplicate),SkillResult.SUCCESS,"new caster has own real pending token");double amount=nativeDamage(duplicate,newTarget);close(h,controlledHit(h,duplicate,newTarget),amount,"duplicate contact still performs ordinary base HP");
            h.assertTrue(arm(duplicate)==100&&ShadowCorrosionTouch.remainingDebuffTicks(newTarget)==76,"valid same-name effect neither refreshes nor gets duration factor again");
            position(newTarget,Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(24,1,22))));
            h.assertTrue(ShadowCorrosionTouch.remainingDebuffTicks(newTarget)==76,"leaving both auras does not extend existing shortened instance");
        }).thenIdle(77).thenExecute(()->{
            h.assertTrue(ends[0].sawLast&&ends[0].sawDeadline&&ends[0].lastPresent&&!ends[0].deadlinePresent&&!ShadowCorrosionTouch.hasDebuff(newTarget)&&newTarget.getArmorValue()==6,"real Level END removes exactly original shortened deadline96");
            h.assertTrue(ShadowCorrosionTouch.remainingDebuffTicks(oldTarget)==23&&oldTarget.getArmorValue()==4,"later entrant still follows full original120 deadline");
        }).thenIdle(24).thenExecute(()->{
            h.assertTrue(ends[1].sawLast&&ends[1].sawDeadline&&ends[1].lastPresent&&!ends[1].deadlinePresent&&!ShadowCorrosionTouch.hasDebuff(oldTarget)&&oldTarget.getArmorValue()==6,"real unchanged old instance expires120, not entry+96 or refreshed120");
            bank(h);pass("REAL_OVERLAPPING_AURA_NEW96_ONCE_OLD120_NO_REFRESH");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void realDeathOwnerEpochUnloadAndNativeLocalNbtDropOnlyShortAuthorityWhilePreservingPaidClockAndPermanentArmor(GameTestHelper h) {
        room(h);var armed=ghost(h,"Life",5,5);var dead=ghost(h,"Life",5,10);var aba=ghost(h,"Life",5,15);
        var first=ghost(h,"Life",5,20);var saved=ghost(h,"Life",20,20);var victim=target(h,"LifeEnemy",25,20,6);
        var reserve=target(h,"LifeEnemy",25,5,6); // Real same-owner roster anchor survives victim's genuine unload/load gap; never replenish a defeated bank.
        UUID permanent=UUID.fromString("d6c9cfcb-c92e-46d6-ae67-f8e6c12cb963");victim.getAttribute(Attributes.ARMOR).addPermanentModifier(new AttributeModifier(permanent,"declared independent armor research",2,AttributeModifier.Operation.ADDITION));
        ShadowGhostUnit[] loadedArmed={null},loadedSource={null};GoldenFoxUnit[] loadedTarget={null};long[] at={-1};UUID[] original=new UUID[3];
        ready(h,armed,dead,aba,first,saved,victim,reserve).thenExecute(()->{
            at[0]=h.getLevel().getGameTime();for(var source:List.of(armed,dead,aba))require(h,cast(h,source),SkillResult.SUCCESS,"real pending arm before lifecycle transition");
            dead.invulnerableTime=0;h.assertTrue(dead.hurt(dead.damageSources().genericKill(),10000)&&!dead.isAlive(),"actual native fatal health path");
            h.assertTrue(arm(dead)==0&&cd(dead)==320,"actual death clears pending authority without refund");
            String owner=aba.getOwnerName();aba.setOwnerName(owner+"_ABA");aba.setOwnerName(owner);
            h.assertTrue(arm(aba)==0&&cd(aba)==320,"real source owner ABA clears old arm but keeps paid deadline");
            require(h,cast(h,first),SkillResult.SUCCESS,"real first target lifecycle arm");controlledHit(h,first,victim);h.assertTrue(victim.getArmorValue()==6&&ShadowCorrosionTouch.hasDebuff(victim),"positive original permanent8 becomes6");
            String targetOwner=victim.getOwnerName();victim.setOwnerName(targetOwner+"_ABA");victim.setOwnerName(targetOwner);
            h.assertTrue(!ShadowCorrosionTouch.hasDebuff(victim)&&victim.getArmorValue()==8&&arm(first)==0&&cd(first)==320,"actual target ABA removes only temporary modifier, never resurrects consumed token");
            require(h,cast(h,saved),SkillResult.SUCCESS,"fresh independent source recreates legal current-epoch debuff");controlledHit(h,saved,victim);
            h.assertTrue(ShadowCorrosionTouch.hasDebuff(victim)&&victim.getArmorValue()==6,"genuine new debuff exists before native save/unload");
            var armedNbt=armed.saveWithoutId(new CompoundTag());var sourceNbt=saved.saveWithoutId(new CompoundTag());var victimNbt=victim.saveWithoutId(new CompoundTag());
            h.assertTrue(serializedModifierCount(victimNbt,permanent)==1&&serializedModifierCount(victimNbt,ShadowCorrosionTouch.MODIFIER_ID)==0,"native NBT contains permanent UUID only");
            original[0]=armed.getUUID();original[1]=saved.getUUID();original[2]=victim.getUUID();
            armed.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);saved.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(arm(armed)==0&&arm(saved)==0&&!ShadowCorrosionTouch.hasDebuff(victim)&&victim.getArmorValue()==8&&cd(saved)==320,"actual source unload clears current debuff and arm, preserving ordinary base/permanent/CD");
            victim.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            loadedArmed[0]=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(h.getLevel());loadedArmed[0].load(armedNbt);
            loadedSource[0]=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(h.getLevel());loadedSource[0].load(sourceNbt);
            loadedTarget[0]=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());loadedTarget[0].load(victimNbt);
            for(var source:List.of(loadedArmed[0],loadedSource[0]))h.assertTrue(!source.isNoAi()&&source.isNoGravity()&&arm(source)==0&&cd(source)==320,"actual native ghost load restores active flight AI and original paid CD, not temporary arm");
            h.assertTrue(!ShadowCorrosionTouch.hasDebuff(loadedTarget[0])&&loadedTarget[0].getArmorValue()==8,"actual native target load never replays transient armor");
            // Same-process NBT test explicitly, NOT the separate two-JVM claim. Observe native load first,
            // then contain unrelated ordinary AI before publication of these same-UUID loaded objects.
            for(Mob entity:List.of(loadedArmed[0],loadedSource[0],loadedTarget[0])){entity.setNoAi(true);ENTITIES.add(entity);h.assertTrue(h.getLevel().addFreshEntity(entity),"normal loaded object enters ordinary actual entity lifecycle");}
        }).thenWaitUntil(()->{for(Entity entity:List.of(loadedArmed[0],loadedSource[0],loadedTarget[0]))h.assertTrue(entity!=null&&entity.tickCount>3&&h.getLevel().getEntity(entity.getId())==entity&&h.getLevel().getEntity(entity.getUUID())==entity,"same-process original UUID loaded objects genuinely publish and naturally tick");})
          .thenExecute(()->{
              h.assertTrue(loadedArmed[0].getUUID().equals(original[0])&&loadedSource[0].getUUID().equals(original[1])&&loadedTarget[0].getUUID().equals(original[2]),"original native identities retained in declared local NBT test");
              for(var source:List.of(loadedArmed[0],loadedSource[0])){h.assertTrue(arm(source)==0&&cd(source)==320-(h.getLevel().getGameTime()-at[0]),"loaded original paid absolute deadline, no refund or arm replay");require(h,cast(h,source),SkillResult.COOLDOWN,"loaded paid source cannot acquire replacement free token");}
              double ordinary=nativeDamage(loadedSource[0],loadedTarget[0]);close(h,controlledHit(h,loadedSource[0],loadedTarget[0]),ordinary,"loaded source's actual ordinary installed goal still functions without any restored arm");
              h.assertTrue(!ShadowCorrosionTouch.hasDebuff(loadedTarget[0])&&loadedTarget[0].getArmorValue()==8&&loadedTarget[0].getAttribute(Attributes.ARMOR).getModifier(permanent)!=null,"ordinary post-load contact cannot revive old debuff; permanent remains");
              h.assertTrue(reserve.isNoAi()&&reserve.isAlive()&&!reserve.isRemoved()&&reserve.getHealth()==1000
                      &&h.getLevel().getEntity(reserve.getUUID())==reserve&&h.getLevel().getEntity(reserve.getId())==reserve
                      &&reserve.getOwnerName().equals(victim.getOwnerName())&&!ShadowCorrosionTouch.hasDebuff(reserve)
                      &&reserve.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==0&&reserve.rts().skills().cooldownTicks(UnitSkill.FOX_DREAM_SEED)==0,
                      "real remote same-owner reserve remains naturally loaded and unused through original victim unload; original ledger was never recreated");
              bank(h);pass("REAL_DEATH_EPOCH_UNLOAD_LOCAL_NBT_NO_SHORT_REPLAY_PAID_CD");
          }).thenSucceed();
    }


    private static SkillResult seed(GameTestHelper h,GoldenFoxUnit source,int x,int z){return new SkillNetwork.GroundCast(new SkillGroundRequest(UnitSkill.FOX_DREAM_SEED,source.getId(),source.getUUID(),source.rts().skills().revision(),h.absolutePos(new BlockPos(x,1,z)))).execute(player(h,source.getOwnerName()));}
    private static final class RemovalProbe {
        final Map<UUID,Integer> counts=new HashMap<>();final Map<UUID,Consumer<MobEffectEvent.Remove>> once=new HashMap<>();
        int count(LivingEntity target){return counts.getOrDefault(target.getUUID(),0);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void remove(MobEffectEvent.Remove event){counts.merge(event.getEntity().getUUID(),1,Integer::sum);var callback=once.remove(event.getEntity().getUUID());if(callback!=null)callback.accept(event);}
    }

    private static ShadowGhostUnit ghost(GameTestHelper h,String owner,int x,int z){var e=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(h.getLevel());owned(h,e,owner,x,z);damage(e,10);return e;}
    private static GoldenFoxUnit target(GameTestHelper h,String owner,int x,int z,double armor){var e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());owned(h,e,owner,x,z);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);e.setHealth(1000);e.getAttribute(Attributes.ARMOR).setBaseValue(armor);return e;}
    private static BoneWingUnit bone(GameTestHelper h,String owner,int x,int z){var e=DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel());owned(h,e,owner,x,z);return e;}
    private static PinkSlimeUnit slime(GameTestHelper h,String owner,int x,int z){var e=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());owned(h,e,owner,x,z);return e;}
    private static void owned(GameTestHelper h,Mob e,String owner,int x,int z){
        ((Unit)e).setOwnerName(account(owner));e.setNoAi(true);e.setPersistenceRequired();neutral(e);
        if(e.getAttribute(AttributeRegistrar.AGGRO_RANGE.get())!=null)e.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);
        position(e,Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x,1,z))));ENTITIES.add(e);
        h.assertTrue(h.getLevel().addFreshEntity(e),"real registered fixture enters world before natural tick/identity wait");player(h,owner);
    }
    private static String account(String suffix){String name=suffix.startsWith(PREFIX)?suffix:PREFIX+suffix;if(ACCOUNTS.add(name)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));}return name;}
    private static FakePlayer player(GameTestHelper h,String suffix){String name=account(suffix);return PLAYERS.computeIfAbsent(name,key->{var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));p.connection=new SilentHandler(p);position(p,Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,1,29))));ENTITIES.add(p);h.getLevel().addNewPlayer(p);PlayerServerEvents.players.add(p);return p;});}
    private static SkillRequest request(ShadowGhostUnit e){return new SkillRequest(UnitSkill.SHADOW_CORROSION_TOUCH,e.getId(),e.getUUID(),e.rts().skills().revision(),e.getId(),e.getUUID());}
    private static SkillResult cast(GameTestHelper h,ShadowGhostUnit e){return new SkillNetwork.Cast(request(e)).execute(player(h,e.getOwnerName()));}
    private static SkillResult mark(GameTestHelper h,BoneWingUnit source,LivingEntity target){return new SkillNetwork.Cast(new SkillRequest(UnitSkill.COORDINATED_MARK,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID())).execute(player(h,source.getOwnerName()));}
    private static SkillResult gel(GameTestHelper h,PinkSlimeUnit source,LivingEntity target){return new SkillNetwork.Cast(new SkillRequest(UnitSkill.GEL_GUARD,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID())).execute(player(h,source.getOwnerName()));}
    private static int cd(ShadowGhostUnit source){return source.rts().skills().cooldownTicks(UnitSkill.SHADOW_CORROSION_TOUCH);}
    private static int arm(ShadowGhostUnit source){return ShadowCorrosionTouch.remainingArmTicks(source);}
    private static void unspent(GameTestHelper h,ShadowGhostUnit source,LivingEntity target,String why){h.assertTrue(arm(source)==100&&cd(source)==320&&!ShadowCorrosionTouch.hasDebuff(target)&&ShadowCorrosionTouch.remainingDebuffTicks(target)==0,"no grant/consumption for "+why);}
    /** Uses the original native armor/rounding getter before the relevant hurt; never reimplements it. */
    private static double nativeDamage(ShadowGhostUnit source,LivingEntity target){return source.getUnitAttackDamage()*(1-((Unit)target).getUnitPhysicalArmorPercentage());}
    /** Deliberately controlled installed native goal, not autonomous APS. Only this fixture drains
     * ordinary cooldown and clears native victim i-frames to schedule several real goals in one tick.
     * It never invokes doHurtTarget or a skill scope; the installed goal owns the actual call. */
    private static double controlledHit(GameTestHelper h,ShadowGhostUnit source,LivingEntity target){
        h.assertTrue(source.getAttackGoal() instanceof TeamSafeMeleeAttackGoal,"actual registered ghost owns exact native RTS melee goal");
        var goal=(AbstractMeleeAttackUnitGoal)source.getAttackGoal();double gap=source.getBbWidth()/2.0+target.getBbWidth()/2.0+.1;
        position(source,target.position().add(-gap,0,0));source.setNoAi(true);source.setUnitAttackTargetForced(target);source.setHoldPosition(true);
        h.assertTrue(source.getTarget()==target&&!source.getBoundingBox().intersects(target.getBoundingBox())&&source.hasLineOfSight(target),"actual exact enemy in visible, nonoverlapping native melee range");
        for(int tick=0;tick<(int)Math.ceil(source.getAttackCooldown())+2;tick++)goal.tickAttackCooldown();
        target.invulnerableTime=0;float before=target.getHealth();goal.start();goal.tick();goal.stop();source.setUnitAttackTargetForced(null);
        return before-target.getHealth();
    }
    private static void order(GameTestHelper h,ShadowGhostUnit source,UnitAction action,int target,BlockPos pos){
        try{var sender=player(h,source.getOwnerName());var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.setListener(sender.connection);
            var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);
            var context=ctor.newInstance(connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);
            h.assertTrue(context.getSender()==sender,"actual Forge command context authenticates exact source owner");
            h.assertTrue(new com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket(source.getOwnerName(),action,target,new int[]{source.getId()},pos,BlockPos.ZERO,false).handle(()->context),"actual owner command packet accepted");
        }catch(ReflectiveOperationException error){throw new RuntimeException("actual Forge command context",error);}
    }
    private static GameTestSequence ready(GameTestHelper h,Entity...entities){return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>3&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"real ID/UUID publication and natural entity ticks: "+e.getUUID()+" ticks="+e.tickCount);}).thenExecute(()->{h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"focused core uses actual fog-off environment, no fabricated visibility");for(Entity e:entities)if(e instanceof Unit u)h.assertTrue(u.getItems().isEmpty(),"cargo not contaminated by fixture construction");bank(h);});}
    private static void damage(LivingEntity e,double amount){e.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);if(e.getAttribute(Attributes.ATTACK_DAMAGE)!=null)e.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);if(e.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get())!=null)e.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);}
    private static void neutral(LivingEntity e){e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);e.setAbsorptionAmount(0);for(var attr:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(e.getAttribute(attr)!=null)e.getAttribute(attr).setBaseValue(0);}
    private static void position(Entity entity,Vec3 position){entity.moveTo(position.x,position.y,position.z);entity.setDeltaMovement(Vec3.ZERO);}
    private static void bank(GameTestHelper h){for(String owner:ACCOUNTS){var r=ResourcesServerEvents.resourcesList.stream().filter(v->v.ownerName.equals(owner)).findFirst().orElse(null);h.assertTrue(r!=null,"original bank present; never recreate defeated ledger");h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456&&r.foodToAdd==0&&r.woodToAdd==0&&r.oreToAdd==0&&r.emeraldToAdd==0,"skill never mutates bank/pending balances: "+owner);}}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
    private static void require(GameTestHelper h,SkillResult actual,SkillResult expected,String why){h.assertTrue(actual==expected,why+" expected="+expected+" actual="+actual);}
    private static void pass(String name){System.out.println("DREAMSEEKERS_SHADOW_CORROSION_TOUCH_PASS "+name);}
    private static int serializedModifierCount(CompoundTag nbt,UUID id){int count=0;var attributes=nbt.getList("Attributes",10);for(int i=0;i<attributes.size();i++){var modifiers=attributes.getCompound(i).getList("Modifiers",10);for(int j=0;j<modifiers.size();j++){var m=modifiers.getCompound(j);if(m.hasUUID("UUID")&&m.getUUID("UUID").equals(id))count++;}}return count;}
    private static double savedArmorBase(CompoundTag nbt){var attributes=nbt.getList("Attributes",10);for(int i=0;i<attributes.size();i++){var attr=attributes.getCompound(i);if(attr.getString("Name").equals("minecraft:generic.armor"))return attr.getDouble("Base");}throw new IllegalStateException("actual native armor attribute absent from save");}
    private static void room(GameTestHelper h){var p=h.absolutePos(BlockPos.ZERO);var level=h.getLevel();for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(p.offset(x,9,z),Blocks.STONE.defaultBlockState(),2);level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+32,level.getMaxBuildHeight(),p.getZ()+32)).forEach(Entity::discard);for(int x=0;x<32;x++)for(int z=0;z<32;z++){level.setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(p.offset(x,y,z),(x==0||x==31||z==0||z==31?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}level.getEntitiesOfClass(ItemEntity.class,new AABB(p,p.offset(32,10,32))).forEach(Entity::discard);}
    private static void partition(GameTestHelper h,int z){for(int x=1;x<31;x++)for(int y=1;y<9;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),Blocks.STONE.defaultBlockState(),2);}

    private static void listen(){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}}
    private static Trace watch(ShadowGhostUnit source,LivingEntity target){listen();var trace=new Trace(source,target);if(PROBE.traces.put(target.getUUID(),trace)!=null)throw new IllegalStateException("duplicate natural victim observer");return trace;}
    private static void watchHurt(LivingEntity target,Consumer<LivingHurtEvent> callback){listen();if(PROBE.hurtOnce.put(target.getUUID(),callback)!=null)throw new IllegalStateException("duplicate Hurt callback");}
    private static void watchDamage(LivingEntity target,Consumer<LivingDamageEvent> callback){listen();if(PROBE.damageOnce.put(target.getUUID(),callback)!=null)throw new IllegalStateException("duplicate Damage callback");}
    private static final class Trace {
        final ShadowGhostUnit source;final LivingEntity target;final List<Hit> hits=new ArrayList<>();Hit pending;int incoming;
        Trace(ShadowGhostUnit source,LivingEntity target){this.source=source;this.target=target;}
        String describe(){return "source="+source.getUUID()+" target="+target.getUUID()+" incoming="+incoming+" hits="+hits;}
    }
    private static final class Hit {
        final long time;final float hpBefore;final int armorBefore;final boolean hadDebuff;final double nativeExpected;
        float hpAfter;int armorAfter,armAfter;boolean hasDebuff;long expiry;
        Hit(Trace trace){time=trace.target.level().getGameTime();hpBefore=trace.target.getHealth();armorBefore=trace.target.getArmorValue();hadDebuff=ShadowCorrosionTouch.hasDebuff(trace.target);nativeExpected=nativeDamage(trace.source,trace.target);}
        @Override public String toString(){return "{time="+time+",hp="+hpBefore+"->"+hpAfter+",armor="+armorBefore+"->"+armorAfter+",debuff="+hadDebuff+"->"+hasDebuff+",arm="+armAfter+",expiry="+expiry+"}";}
    }
    private static final class Expiry {
        final LivingEntity target;final UUID modifier;final long deadline;boolean sawLast,sawDeadline,lastPresent,deadlinePresent;
        Expiry(LivingEntity target,UUID modifier,long deadline){this.target=target;this.modifier=modifier;this.deadline=deadline;}
    }
    private static final class Probe {
        final Map<UUID,Trace> traces=new HashMap<>();final Map<UUID,Consumer<LivingHurtEvent>> hurtOnce=new HashMap<>();final Map<UUID,Consumer<LivingDamageEvent>> damageOnce=new HashMap<>();final List<Expiry> expiries=new ArrayList<>();
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void attack(LivingAttackEvent event){
            var trace=traces.get(event.getEntity().getUUID());if(trace==null)return;trace.incoming++;
            if(event.getSource().getEntity()!=trace.source||event.getSource().getDirectEntity()!=trace.source||!event.getSource().is(DamageTypes.MOB_ATTACK))throw new IllegalStateException("nonordinary contamination of natural lane: "+event.getSource());
            if(trace.pending!=null)throw new IllegalStateException("multiple same-tick natural contacts contaminate HP snapshot");trace.pending=new Hit(trace);
        }
        @SubscribeEvent public void hurt(LivingHurtEvent event){var callback=hurtOnce.remove(event.getEntity().getUUID());if(callback!=null)callback.accept(event);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void damage(LivingDamageEvent event){var callback=damageOnce.remove(event.getEntity().getUUID());if(callback!=null)callback.accept(event);}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void end(TickEvent.LevelTickEvent event){
            if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
            for(var trace:traces.values())if(trace.target.level()==level&&trace.pending!=null){
                var hit=trace.pending;hit.hpAfter=trace.target.getHealth();hit.armorAfter=trace.target.getArmorValue();hit.hasDebuff=ShadowCorrosionTouch.hasDebuff(trace.target);hit.armAfter=arm(trace.source);hit.expiry=level.getGameTime()+ShadowCorrosionTouch.remainingDebuffTicks(trace.target);
                trace.hits.add(hit);trace.pending=null;
            }
            for(var expiry:expiries)if(expiry.target.level()==level){long now=level.getGameTime();if(now==expiry.deadline-1){expiry.sawLast=true;expiry.lastPresent=expiry.target.getAttribute(Attributes.ARMOR).getModifier(expiry.modifier)!=null;}if(now==expiry.deadline){expiry.sawDeadline=true;expiry.deadlinePresent=expiry.target.getAttribute(Attributes.ARMOR).getModifier(expiry.modifier)!=null;}}
        }
    }

    private record SyncObservation(long gameTime,SkillNetwork.Sync packet){}
    private static List<SyncObservation> syncs(ServerPlayer player,ShadowGhostUnit source){return ((RecordingHandler)player.connection).wire.syncs.stream().filter(p->p.packet().sourceUuid().equals(source.getUUID())&&p.packet().revision()>=0).toList();}
    /** Local real PlayerList membership, not GUI/socket login simulation or direct state.sync. */
    @SuppressWarnings("unchecked") private static void syncMembership(ServerPlayer player,boolean present){try{var list=player.serverLevel().getServer().getPlayerList();var players=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");var byId=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");players.setAccessible(true);byId.setAccessible(true);List<ServerPlayer> actual=(List<ServerPlayer>)players.get(list);Map<UUID,ServerPlayer> ids=(Map<UUID,ServerPlayer>)byId.get(list);if(present){if(!actual.contains(player))actual.add(player);ids.put(player.getUUID(),player);SYNC_MEMBERS.add(player);}else{actual.remove(player);ids.remove(player.getUUID(),player);SYNC_MEMBERS.remove(player);}}catch(ReflectiveOperationException error){throw new RuntimeException("real local PlayerList membership",error);}}
    private static final class RecordingHandler extends ServerGamePacketListenerImpl {
        final RecordingConnection wire;RecordingHandler(ServerPlayer p){this(p,new RecordingConnection(p.serverLevel()));}private RecordingHandler(ServerPlayer p,RecordingConnection wire){super(p.serverLevel().getServer(),wire,p);this.wire=wire;}
        @Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){wire.send(p);}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){wire.send(p,listener);}
    }
    private static final class RecordingConnection extends net.minecraft.network.Connection {
        final ServerLevel level;final List<SyncObservation> syncs=new ArrayList<>();RecordingConnection(ServerLevel level){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);this.level=level;}
        @Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){record(p);}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){record(p);}
        private void record(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket payload&&payload.getIdentifier().equals(DreamseekersMod.id("unit_skills"))){var bytes=new FriendlyByteBuf(payload.getData().copy());try{if(bytes.readVarInt()==1){var sync=SkillNetwork.Sync.decode(bytes);if(bytes.readableBytes()!=0)throw new IllegalStateException("trailing real Sync payload");syncs.add(new SyncObservation(level.getGameTime(),sync));}}finally{bytes.release();}}}
    }
    private static final class SilentHandler extends ServerGamePacketListenerImpl {SilentHandler(ServerPlayer p){super(p.serverLevel().getServer(),new SilentConnection(),p);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}}
    private static final class SilentConnection extends net.minecraft.network.Connection {SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}}
}
