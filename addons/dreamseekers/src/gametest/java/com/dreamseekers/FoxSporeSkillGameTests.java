// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.spore.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** Server birth/lease and cleansing/duration tests. No GUI/socket or point-kill claim.
 * Every observation timeline is prebuilt, and every timeout reserves at least 600 initialization ticks.
 */
@GameTestHolder("dreamseekers_fox_spore")
@PrefixGameTestTemplate(false)
public final class FoxSporeSkillGameTests {
    private static final String BATCH="dreamseekers_fox_spore",PREFIX="FoxSeed_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final Set<String> ACCOUNTS=new LinkedHashSet<>();
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();
    private static final JoinProbe PROBE=new JoinProbe();
    private static boolean listening;

    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){
        cleanupRanged();
        if(listening){MinecraftForge.EVENT_BUS.unregister(PROBE);listening=false;}PROBE.callbacks.clear();PROBE.constructionCallback=null;PROBE.removals.clear();PROBE.applicables.clear();PROBE.added.clear();PROBE.attacks.clear();PROBE.firstDamage.clear();PROBE.rabbitSounds.clear();PROBE.birthObservation=null;
        for(Entity e:List.copyOf(FIXTURES))if(e instanceof LivingEntity living)SporeLease.clear(living);
        for(Entity e:List.copyOf(FIXTURES))if(!e.isRemoved()){
            if(e instanceof ServerPlayer player)player.serverLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);else e.discard();
        }
        for(String a:ACCOUNTS)for(String b:ACCOUNTS)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.players.removeIf(p->ACCOUNTS.contains(p.getGameProfile().getName()));
        PlayerServerEvents.orthoviewPlayers.removeIf(p->ACCOUNTS.contains(p.getGameProfile().getName()));
        PlayerServerEvents.rtsPlayers.removeIf(p->ACCOUNTS.contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->ACCOUNTS.contains(r.ownerName));
        FIXTURES.clear();PLAYERS.clear();ACCOUNTS.clear();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=860)
    public static void groundPacketCodecRealSenderTokensThreadAndReplayAreAuthoritative(GameTestHelper h){
        floor(h);var source=fox(h,"Authority",4,5);var owner=player(h,"Authority");var foreign=player(h,"AuthorityForeign");AlliancesServerEvents.addAlliance(source.getOwnerName(),foreign.getGameProfile().getName());
        ready(h,source,owner,foreign).thenExecute(()->{
            BlockPos p=pos(h,8,5);var good=request(source,p);var mutable=p.mutable();var frozen=new SkillGroundRequest(UnitSkill.FOX_DREAM_SEED,source.getId(),source.getUUID(),source.rts().skills().revision(),mutable);mutable.move(1,0,0);h.assertTrue(frozen.position().equals(p),"request defensively captures ground cell, not mutable client reference");
            var original=new SkillNetwork.GroundCast(good);FriendlyByteBuf wire=new FriendlyByteBuf(Unpooled.buffer());SkillNetwork.GroundCast decoded;
            try{original.encode(wire);decoded=SkillNetwork.GroundCast.decode(wire);h.assertTrue(original.equals(decoded)&&wire.readableBytes()==0,"actual ground packet codec roundtrips skill/source/revision/position only");}finally{wire.release();}
            require(h,decoded.execute(null),SkillResult.INVALID_REQUEST,"missing authenticated sender");require(h,decoded.execute(foreign),SkillResult.NOT_OWNER,"allied same-faction player cannot control another owner");
            require(h,SkillService.requestGround(owner,new SkillGroundRequest(UnitSkill.FOX_DREAM_SEED,source.getId(),UUID.randomUUID(),good.revision(),p)),SkillResult.INVALID_SOURCE,"UUID/ID substitution");
            require(h,SkillService.requestGround(owner,new SkillGroundRequest(UnitSkill.FOX_DREAM_SEED,source.getId(),source.getUUID(),good.revision()+1,p)),SkillResult.REPLAY,"forged revision");
            require(h,SkillService.requestGround(owner,new SkillGroundRequest(UnitSkill.FOX_BLESSING,source.getId(),source.getUUID(),good.revision(),p)),SkillResult.INVALID_REQUEST,"entity skill cannot be reinterpreted as ground skill");
            require(h,SkillService.requestGround(owner,new SkillGroundRequest(UnitSkill.FOX_DREAM_SEED,source.getId(),source.getUUID(),good.revision(),null)),SkillResult.INVALID_REQUEST,"missing ground position");
            require(h,java.util.concurrent.CompletableFuture.supplyAsync(()->decoded.execute(owner)).join(),SkillResult.INVALID_REQUEST,"off-thread request fails closed");
            ServerLevel other=h.getLevel().getServer().getLevel(Level.NETHER);if(other!=null)require(h,decoded.execute(FakePlayerFactory.get(other,owner.getGameProfile())),SkillResult.INVALID_SOURCE,"real sender world selects lookup, not packet-selected dimension");
            h.assertTrue(cd(source)==0&&SporeLease.activeFor(source)==null&&source.rts().skills().revision()==good.revision(),"all denials leave cooldown/revision/lease untouched");
            require(h,decoded.execute(owner),SkillResult.SUCCESS,"real authenticated ground cast");var carrier=active(h,source);h.assertTrue(carrier.position().equals(Vec3.atBottomCenterOf(p))&&carrier.getHealth()==10&&SporeLease.remainingTicks(carrier)==200&&cd(source)==480,"server owns exact destination, health, lifetime and cooldown");
            require(h,decoded.execute(owner),SkillResult.REPLAY,"successful packet replay cannot summon twice");guard(h);pass("GROUND_CODEC_SENDER_TOKEN_THREAD_REPLAY");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=860)
    public static void groundSupportFluidCollisionBoundsAndRangeRejectWithoutSpending(GameTestHelper h){
        floor(h);var source=fox(h,"Ground",4,5);var boat=EntityType.BOAT.create(h.getLevel());place(h,boat,8,13);
        ready(h,source,boat,player(h,"Ground")).thenExecute(()->{
            BlockPos p=pos(h,8,5);h.getLevel().setBlock(p.below(),Blocks.AIR.defaultBlockState(),2);require(h,cast(h,source,p),SkillResult.GROUND_INVALID,"unsupported air is not a ground target");h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);
            h.getLevel().setBlock(p,Blocks.WATER.defaultBlockState(),2);require(h,cast(h,source,p),SkillResult.GROUND_INVALID,"water cell rejected");h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),2);
            h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);require(h,cast(h,source,p),SkillResult.GROUND_INVALID,"solid occupied cell rejected");h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),2);
            require(h,cast(h,source,pos(h,8,13)),SkillResult.GROUND_INVALID,"real collidable boat occupies destination");
            require(h,cast(h,source,new BlockPos(p.getX(),h.getLevel().getMaxBuildHeight()+1,p.getZ())),SkillResult.GROUND_INVALID,"out-of-world input rejected before any chunk summon");
            require(h,cast(h,source,pos(h,19,5)),SkillResult.RANGE,"ten-block cast range is authoritative");
            h.assertTrue(cd(source)==0&&SporeLease.activeFor(source)==null,"invalid ground attempts do not spend or reserve");require(h,cast(h,source,p),SkillResult.SUCCESS,"same real input succeeds after restoring legal ground");active(h,source);guard(h);pass("GROUND_GEOMETRY_RANGE_NO_SPEND");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=870)
    public static void blessingAndSeedHaveIndependentCooldownsButShareReplayRevision(GameTestHelper h){
        floor(h);var a=fox(h,"CdA",4,5);var ta=slime(h,"CdA",6,5);var b=fox(h,"CdB",4,15);var tb=slime(h,"CdB",6,15);
        ready(h,a,ta,b,tb).thenExecute(()->{
            for(var source:List.of(a,b))h.assertTrue(source.getAbilities().get().equals(List.of(UnitSkillAbility.FOX_BLESSING,UnitSkillAbility.FOX_DREAM_SEED)),"public ability list exposes exactly the two approved independent active skills in stable order");
            var oldBless=blessing(a);var oldSeed=request(b,pos(h,8,15));long ar=a.rts().skills().revision(),br=b.rts().skills().revision();
            require(h,cast(h,a,pos(h,8,5)),SkillResult.SUCCESS,"seed first");h.assertTrue(a.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==0,"seed does not debit blessing cooldown");
            require(h,SkillService.request(playerFor(h,a),oldBless),SkillResult.REPLAY,"old blessing input cannot reuse consumed shared revision");require(h,SkillService.request(playerFor(h,a),blessing(a)),SkillResult.SUCCESS,"fresh blessing independently available");
            require(h,SkillService.request(playerFor(h,b),blessing(b)),SkillResult.SUCCESS,"blessing first");h.assertTrue(cd(b)==0,"blessing does not debit seed cooldown");
            require(h,SkillService.requestGround(playerFor(h,b),oldSeed),SkillResult.REPLAY,"old seed input cannot reuse consumed shared revision");require(h,cast(h,b,pos(h,8,15)),SkillResult.SUCCESS,"fresh seed independently available");
            for(var source:List.of(a,b))h.assertTrue(cd(source)==480&&source.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==480&&SporeLease.activeFor(source)!=null,"both independent twenty-four-second cooldowns committed exactly once");
            h.assertTrue(a.rts().skills().revision()==ar+2&&b.rts().skills().revision()==br+2,"each successful skill advances shared replay protection once");close(h,SkillEffects.bonus(ta,BuffKind.ATTACK_SPEED),.2,"real blessing still applies");close(h,SkillEffects.bonus(tb,BuffKind.ATTACK_SPEED),.2,"opposite call order retains real blessing");guard(h);pass("TWO_COOLDOWNS_SHARED_REVISION");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=880)
    public static void sourceOwnerAbaDeathUnloadAndCarrierUnloadRetireWithoutRefund(GameTestHelper h){
        floor(h);List<GoldenFoxUnit> sources=new ArrayList<>();List<JungleSporeCarrier> carriers=new ArrayList<>();int[][] cells={{4,4},{4,12},{14,4},{14,12}};
        for(int i=0;i<cells.length;i++)sources.add(fox(h,"Life"+i,cells[i][0],cells[i][1]));
        // A real untouched reserve prevents native last-unit defeat from deleting the test owner's bank.
        // Both remain inside this 24x24 room and far outside every lease's three-block effect radius.
        var deathReserve=fox(h,"Life1",19,20);var unloadReserve=fox(h,"Life2",21,20);
        List<Entity> readyEntities=new ArrayList<>(sources);readyEntities.add(deathReserve);readyEntities.add(unloadReserve);
        ready(h,readyEntities.toArray(Entity[]::new)).thenExecute(()->{
            h.assertTrue(deathReserve.distanceToSqr(Vec3.atBottomCenterOf(pos(h,8,12)))>9&&unloadReserve.distanceToSqr(Vec3.atBottomCenterOf(pos(h,18,4)))>9,"published noncombat reserves are outside their owners' lease effects");
            for(int i=0;i<sources.size();i++){require(h,cast(h,sources.get(i),pos(h,cells[i][0]+4,cells[i][1])),SkillResult.SUCCESS,"real lease lifecycle fixture "+i);carriers.add(active(h,sources.get(i)));}
            String old=sources.get(0).getOwnerName();sources.get(0).setOwnerName(account("LifeOther"));sources.get(0).setOwnerName(old);
            sources.get(1).hurt(sources.get(1).damageSources().genericKill(),10000);sources.get(2).remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);h.assertTrue(!carriers.get(2).displayedActive()&&cd(sources.get(2))>0&&SporeLease.activeFor(sources.get(2))==null&&!SporeLease.active(carriers.get(2)),"source leave synchronously revokes displayed and server authority without refund; physical removal may wait for safe tick");carriers.get(3).remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        }).thenIdle(3).thenExecute(()->{
            for(int i=0;i<sources.size();i++)h.assertTrue(SporeLease.activeFor(sources.get(i))==null&&!SporeLease.active(carriers.get(i))&&carriers.get(i).isRemoved()&&cd(sources.get(i))>0,"invalid source/carrier cannot preserve authority or refund cooldown "+i);
            h.assertTrue(deathReserve.isAlive()&&unloadReserve.isAlive()&&deathReserve.isNoAi()&&unloadReserve.isNoAi()&&cd(deathReserve)==0&&cd(unloadReserve)==0&&SporeLease.activeFor(deathReserve)==null&&SporeLease.activeFor(unloadReserve)==null,"untouched published reserves preserve owners without casting or replacing the tested sources");
            guard(h);pass("OWNER_ABA_DEATH_UNLOAD_NO_REFUND");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1040)
    public static void actualTwoHundredTickLeaseExpiresOnceAndCooldownRemainsAbsolute(GameTestHelper h){
        floor(h);var source=fox(h,"Expiry",4,5);JungleSporeCarrier[] carrier={null};long[] start={0};
        ready(h,source).thenExecute(()->{require(h,cast(h,source,pos(h,8,5)),SkillResult.SUCCESS,"real timed lease");carrier[0]=active(h,source);start[0]=h.getLevel().getGameTime();h.assertTrue(SporeLease.remainingTicks(carrier[0])==200,"exact ten-second initial duration");})
          .thenIdle(199).thenExecute(()->{h.assertTrue(SporeLease.active(carrier[0])&&SporeLease.remainingTicks(carrier[0])==1,"original object still active just before absolute deadline");require(h,cast(h,source,pos(h,10,5)),SkillResult.COOLDOWN,"fresh duplicate cannot refresh lifetime");})
          .thenIdle(2).thenExecute(()->{h.assertTrue(!SporeLease.active(carrier[0])&&carrier[0].isRemoved()&&SporeLease.activeCount(source.getOwnerName())==0&&SporeLease.activeFor(source)==null,"expiry revokes and removes original carrier");h.assertTrue(cd(source)==480-(h.getLevel().getGameTime()-start[0])&&cd(source)>0,"expiry never refunds or resets spent absolute cooldown");guard(h);pass("EXACT_200_TICK_LEASE_ABSOLUTE_COOLDOWN");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=900)
    public static void sliceOneCarrierRejectsDamageAndSavedOrForgedNbtCannotGrantLease(GameTestHelper h){
        floor(h);var source=fox(h,"Nbt",4,5);var enemy=slime(h,"NbtEnemy",4,15);List<JungleSporeCarrier> rejected=new ArrayList<>();JungleSporeCarrier[] live={null};
        ready(h,source,enemy).thenExecute(()->{
            require(h,cast(h,source,pos(h,8,5)),SkillResult.SUCCESS,"active carrier before serialization");live[0]=active(h,source);
            h.assertTrue(!((Object)live[0] instanceof Unit)&&!UnitServerEvents.getAllUnits().contains(live[0])&&!live[0].isCurrentlyGlowing(),"lease is not a soldier or global glow provider");
            h.assertTrue(!live[0].hurt(live[0].damageSources().genericKill(),1000)&&!live[0].hurt(enemy.damageSources().mobAttack(enemy),1000)&&live[0].getHealth()==10&&SporeLease.active(live[0]),"slice-one damage gate remains closed, not yet claiming authorized point-kill");
            CompoundTag saved=new CompoundTag();live[0].saveWithoutId(saved);saved.putUUID("UUID",UUID.randomUUID());saved.putBoolean("DreamSporeDisabled",false);saved.putInt("TTL",Integer.MAX_VALUE);saved.putString("Owner",source.getOwnerName());
            var loaded=DreamseekersMod.JUNGLE_SPORE_CARRIER.get().create(h.getLevel());loaded.load(saved);place(h,loaded,12,5);rejected.add(loaded);
            var raw=DreamseekersMod.JUNGLE_SPORE_CARRIER.get().create(h.getLevel());place(h,raw,16,5);rejected.add(raw);
            for(var e:rejected)h.assertTrue(!SporeLease.active(e)&&SporeLease.remainingTicks(e)==0&&!e.displayedActive(),"normal load or raw creation never recreates in-memory lease");
            h.assertTrue(SporeLease.activeCount(source.getOwnerName())==1&&SporeLease.active(live[0]),"copy cannot steal/duplicate live original authority");
        }).thenIdle(4).thenExecute(()->{for(var e:rejected)h.assertTrue(e.isRemoved()&&!SporeLease.active(e),"unleased carrier naturally discarded, not manually hidden");h.assertTrue(SporeLease.active(live[0])&&cd(source)>0,"original lease and cooldown survive negative NBT copies");guard(h);pass("SLICE_ONE_DAMAGE_CLOSED_NBT_NO_AUTHORITY");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=890)
    public static void ownerCapCountsPendingBirthAndSameFoxCannotReenterItsReservedSlot(GameTestHelper h){
        floor(h);List<GoldenFoxUnit> sources=new ArrayList<>();for(int z:new int[]{4,8,12,16,20})sources.add(fox(h,"Cap",4,z));var peer=fox(h,"CapPeer",14,20);AlliancesServerEvents.addAlliance(sources.get(0).getOwnerName(),peer.getOwnerName());List<Entity> all=new ArrayList<>(sources);all.add(peer);int[] joins={0};SkillResult[] nested={null,null};
        ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
            for(int i=0;i<3;i++)require(h,cast(h,sources.get(i),pos(h,8,4+4*i)),SkillResult.SUCCESS,"initial real owner slot "+i);
            var fourth=sources.get(3);var fifth=sources.get(4);BlockPos fourthPos=pos(h,8,16);
            watch(fourthPos,event->{joins[0]++;nested[0]=cast(h,fourth,pos(h,10,16));nested[1]=cast(h,fifth,pos(h,8,20));h.assertTrue(SporeLease.activeCount(fourth.getOwnerName())==3,"pending slot counts for cap but is not active authority");});
            require(h,cast(h,fourth,fourthPos),SkillResult.SUCCESS,"fourth outer birth commits once");h.assertTrue(joins[0]==1&&nested[0]==SkillResult.ALREADY_ACTIVE&&nested[1]==SkillResult.CAP_REACHED,"real Join recursion sees same-fox reservation and fourth pending owner slot");
            require(h,cast(h,fifth,pos(h,8,20)),SkillResult.CAP_REACHED,"fifth owner carrier rejected");h.assertTrue(cd(fifth)==0&&SporeLease.activeFor(fifth)==null&&SporeLease.activeCount(fourth.getOwnerName())==4,"cap loser does not spend or publish");
            require(h,cast(h,peer,pos(h,18,20)),SkillResult.SUCCESS,"allied independent true owner retains separate cap");h.assertTrue(SporeLease.activeCount(peer.getOwnerName())==1,"owner slots do not merge by alliance");guard(h);pass("PENDING_SLOT_CAP_AND_SAME_FOX_JOIN_REENTRY");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=890)
    public static void joinCancellationAndNestedOtherSkillRollbackSeedWithoutLosingAuthorizedSpend(GameTestHelper h){
        floor(h);var canceled=fox(h,"Cancel",4,5);var stolen=fox(h,"Revision",4,13);var combat=slime(h,"Revision",6,13);int[] joins={0,0};SkillResult[] nested={null};JungleSporeCarrier[] canceledBody={null},staleBody={null};
        ready(h,canceled,stolen,combat).thenExecute(()->{
            BlockPos p=pos(h,8,5);var original=request(canceled,p);watch(p,event->{joins[0]++;canceledBody[0]=(JungleSporeCarrier)event.getEntity();event.setCanceled(true);});
            require(h,SkillService.requestGround(playerFor(h,canceled),original),SkillResult.SPAWN_FAILED,"actual Forge Join cancellation rolls back birth");h.assertTrue(joins[0]==1&&canceledBody[0]!=null&&canceledBody[0].isRemoved()&&cd(canceled)==0&&canceled.rts().skills().revision()==original.revision()&&SporeLease.activeFor(canceled)==null,"canceled reservation releases object/slot without debit");
            require(h,SkillService.requestGround(playerFor(h,canceled),original),SkillResult.SUCCESS,"same still-valid input genuinely succeeds after cancellation ends");
            BlockPos q=pos(h,8,13);watch(q,event->{joins[1]++;staleBody[0]=(JungleSporeCarrier)event.getEntity();nested[0]=SkillService.request(playerFor(h,stolen),blessing(stolen));});
            require(h,cast(h,stolen,q),SkillResult.REPLAY,"another actual skill consumed shared revision inside Join");h.assertTrue(joins[1]==1&&nested[0]==SkillResult.SUCCESS&&staleBody[0]!=null&&staleBody[0].isRemoved()&&SporeLease.activeFor(stolen)==null&&cd(stolen)==0,"stale outer seed rolls back without stealing nested successful spend");
            h.assertTrue(stolen.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==480,"nested real blessing debit remains authoritative");close(h,SkillEffects.bonus(combat,BuffKind.ATTACK_SPEED),.2,"nested real blessing effect remains");require(h,cast(h,stolen,q),SkillResult.SUCCESS,"fresh revision can later create seed after rollback");guard(h);pass("JOIN_CANCEL_AND_SHARED_REVISION_ROLLBACK");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=890)
    public static void actualEntityConstructingReentryCannotStealSameFoxOrFifthOwnerSlot(GameTestHelper h){
        floor(h);List<GoldenFoxUnit> sources=new ArrayList<>();for(int z:new int[]{4,8,12,16,20})sources.add(fox(h,"Construct",4,z));
        int[] callbacks={0};SkillResult[] nested={null,null};
        ready(h,sources.toArray(Entity[]::new)).thenExecute(()->{
            for(int i=0;i<3;i++)require(h,cast(h,sources.get(i),pos(h,8,4+4*i)),SkillResult.SUCCESS,"three committed slots before constructor callback");
            var fourth=sources.get(3);var fifth=sources.get(4);long fifthRevision=fifth.rts().skills().revision();
            watchConstruction(event->{
                // The probe consumes itself before invoking us. Never query UUID/position/health on the unfinished entity.
                callbacks[0]++;nested[0]=cast(h,fourth,pos(h,10,16));nested[1]=cast(h,fifth,pos(h,8,20));
                h.assertTrue(SporeLease.activeCount(fourth.getOwnerName())==3,"preconstruction reservation counts toward cap but is not active aura authority");
            });
            require(h,cast(h,fourth,pos(h,8,16)),SkillResult.SUCCESS,"original fourth cast commits after genuine EntityConstructing callback");
            h.assertTrue(callbacks[0]==1&&nested[0]==SkillResult.ALREADY_ACTIVE&&nested[1]==SkillResult.CAP_REACHED,"reservation exists before Entity constructor exposes reentrant callbacks");
            h.assertTrue(SporeLease.activeCount(fourth.getOwnerName())==4&&cd(fourth)==480&&SporeLease.activeFor(fourth)!=null,"exactly four original-owner leases, one outer spend");
            h.assertTrue(cd(fifth)==0&&SporeLease.activeFor(fifth)==null&&fifth.rts().skills().revision()==fifthRevision,"constructor-reentrant losing source has no effect, debit or consumed revision");
            guard(h);pass("ENTITY_CONSTRUCTING_PREBIRTH_CAP_AND_SOURCE_RESERVATION");
        }).thenSucceed();
    }


    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=900)
    public static void constructorOwnerAbaAndCaughtCallbackFailureReleaseSlotWithoutSpending(GameTestHelper h){
        floor(h);var aba=fox(h,"ConstructAba",4,5);var throwing=fox(h,"ConstructThrow",4,15);
        int[] callbacks={0,0};JungleSporeCarrier[] invalid={null};
        ready(h,aba,throwing).thenExecute(()->{
            var stale=request(aba,pos(h,8,5));String owner=aba.getOwnerName();
            watchConstruction(event->{callbacks[0]++;invalid[0]=(JungleSporeCarrier)event.getEntity();aba.setOwnerName(account("ConstructAbaOther"));aba.setOwnerName(owner);});
            SkillResult result=SkillService.requestGround(playerFor(h,aba),stale);
            h.assertTrue(callbacks[0]==1,"real constructor callback executed for source ABA");
            require(h,result,SkillResult.INVALID_SOURCE,"constructor owner ABA invalidates the ORIGINAL captured source authority");
            h.assertTrue(cd(aba)==0&&SporeLease.activeFor(aba)==null&&SporeLease.activeCount(owner)==0&&aba.rts().skills().revision()>stale.revision(),"ABA cannot spend, keep a lease, occupy a slot or rewind replay epoch");
            h.assertTrue(invalid[0]!=null&&!SporeLease.active(invalid[0])&&h.getLevel().getEntity(invalid[0].getUUID())!=invalid[0],"uncommitted constructor object never becomes authorized or published");
            require(h,cast(h,aba,pos(h,8,5)),SkillResult.SUCCESS,"fresh post-ABA revision can genuinely reserve the released slot");

            RuntimeException sentinel=new RuntimeException("expected GameTest-only EntityConstructing sentinel");
            watchConstruction(event->{callbacks[1]++;throw sentinel;});
            var retry=request(throwing,pos(h,8,15));SkillResult failed=null;RuntimeException caught=null;
            try{failed=SkillService.requestGround(playerFor(h,throwing),retry);}catch(RuntimeException exception){caught=exception;}
            // No GameTest assertion is executed within that try block. Only the deliberately injected sentinel may escape the service.
            boolean sentinelCaught=false;for(Throwable cause=caught;cause!=null;cause=cause.getCause())if(cause==sentinel)sentinelCaught=true;
            h.assertTrue(callbacks[1]==1&&sentinelCaught&&failed==null,"the injected constructor exception propagates to this local catcher; unrelated exceptions never count as success");
            h.assertTrue(cd(throwing)==0&&throwing.rts().skills().revision()==retry.revision()&&SporeLease.activeFor(throwing)==null&&SporeLease.activeCount(throwing.getOwnerName())==0,"constructor exception finally releases reservation without spending revision/CD");
            require(h,SkillService.requestGround(playerFor(h,throwing),retry),SkillResult.SUCCESS,"same still-current input can genuinely retry after callback exception");
            active(h,throwing);guard(h);pass("CONSTRUCTOR_OWNER_ABA_AND_EXCEPTION_ROLLBACK");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=900)
    public static void realActivationCleansesOneByRegistryOrderAndNeverCleansesLateEntrants(GameTestHelper h){
        floor(h);var source=fox(h,"CleanOnce",4,8);var own=slime(h,"CleanOnce",8,10);var ally=slime(h,"CleanAlly",10,8);var enemy=slime(h,"CleanEnemy",8,6);var late=slime(h,"CleanOnce",20,8);var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);place(h,cow,6,8);
        AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());MobEffectInstance[] oldWeak={null,null},lateSlow={null};long[] start={0};
        ready(h,source,own,ally,enemy,late,cow).thenExecute(()->{
            for(LivingEntity target:List.of(own,ally,enemy,cow)){h.assertTrue(target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400))&&target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,400)),"native additions establish paired eligible negatives before any aura");}
            oldWeak[0]=own.getEffect(MobEffects.WEAKNESS);oldWeak[1]=ally.getEffect(MobEffects.WEAKNESS);h.assertTrue(late.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,300)),"late entrant starts with real preexisting negative");lateSlow[0]=late.getEffect(MobEffects.MOVEMENT_SLOWDOWN);start[0]=h.getLevel().getGameTime();
            require(h,cast(h,source,pos(h,8,8)),SkillResult.SUCCESS,"real birth activates cleanse after commit");
            for(LivingEntity target:List.of(own,ally))h.assertTrue(!target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&target.hasEffect(MobEffects.WEAKNESS)&&SporeEffects.cleanseProtectionTicks(target)==200,"registry-ID slowness before weakness, at most one removal per friendly Unit");
            h.assertTrue(own.getEffect(MobEffects.WEAKNESS)==oldWeak[0]&&ally.getEffect(MobEffects.WEAKNESS)==oldWeak[1]&&oldWeak[0].getDuration()==400&&oldWeak[1].getDuration()==400,"unselected original instances and durations are not rewritten");
            for(LivingEntity target:List.of(enemy,cow))h.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&target.hasEffect(MobEffects.WEAKNESS)&&SporeEffects.cleanseProtectionTicks(target)==0,"enemy or non-Unit does not receive activation cleanse");
            late.setPos(Vec3.atBottomCenterOf(pos(h,10,10)));late.setDeltaMovement(Vec3.ZERO);h.assertTrue(active(h,source).distanceToSqr(late)<9,"late entrant genuinely enters spherical aura");
        }).thenIdle(5).thenExecute(()->{
            int elapsed=(int)(h.getLevel().getGameTime()-start[0]);h.assertTrue(late.getEffect(MobEffects.MOVEMENT_SLOWDOWN)==lateSlow[0]&&lateSlow[0].getDuration()==300-elapsed&&SporeEffects.cleanseProtectionTicks(late)==0,"late entry neither triggers periodic cleanse nor rewrites an old duration");
            h.assertTrue(own.getEffect(MobEffects.WEAKNESS)==oldWeak[0]&&oldWeak[0].getDuration()==400-elapsed,"remaining old effect ticks normally, not repeatedly shortened");guard(h);pass("ACTIVATION_REGISTRY_ORDER_ONCE_FRIENDLY_ONLY_NO_LATE_CLEANSE");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1080)
    public static void realRemoveCancellationDoesNotSpendCrossFoxProtectionAndItExpiresAfterTwoHundredTicks(GameTestHelper h){
        floor(h);var a=fox(h,"ProtectA",6,12);var b=fox(h,"ProtectB",18,12);var c=fox(h,"ProtectC",12,4);var d=fox(h,"ProtectD",12,20);var target=slime(h,"ProtectA",12,12);
        for(var peer:List.of(b,c,d))AlliancesServerEvents.addAlliance(a.getOwnerName(),peer.getOwnerName());int[] removals={0};long[] start={0};MobEffectInstance[] oldWeak={null};
        ready(h,a,b,c,d,target).thenExecute(()->{
            h.assertTrue(target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400))&&target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,400)),"two real preexisting negatives");oldWeak[0]=target.getEffect(MobEffects.WEAKNESS);
            watchRemoval(target,event->{removals[0]++;h.assertTrue(event.getEffect()==MobEffects.MOVEMENT_SLOWDOWN,"actual chosen registered effect reaches Forge Remove");event.setCanceled(true);});
            require(h,cast(h,a,pos(h,10,12)),SkillResult.SUCCESS,"birth itself succeeds despite canceled cleansing");h.assertTrue(removals[0]==1&&target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&SporeEffects.cleanseProtectionTicks(target)==0&&cd(a)==480,"canceled removal consumes no cleanse protection but cannot refund a committed birth");
            require(h,cast(h,b,pos(h,14,12)),SkillResult.SUCCESS,"independent allied fox gets a genuine later activation");start[0]=h.getLevel().getGameTime();h.assertTrue(!target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&SporeEffects.cleanseProtectionTicks(target)==200,"actual removal starts exact shared target protection");
            require(h,cast(h,c,pos(h,12,10)),SkillResult.SUCCESS,"third legitimate source can spawn but cannot recleanse protected friend");h.assertTrue(target.getEffect(MobEffects.WEAKNESS)==oldWeak[0]&&SporeEffects.cleanseProtectionTicks(target)==200,"different source does not remove next negative or refresh protection");
        }).thenIdle(199).thenExecute(()->h.assertTrue(SporeEffects.cleanseProtectionTicks(target)==1&&target.getEffect(MobEffects.WEAKNESS)==oldWeak[0],"cross-fox protection remains through original tick199"))
          .thenIdle(2).thenExecute(()->{
            h.assertTrue(SporeEffects.cleanseProtectionTicks(target)==0&&SporeLease.activeFor(a)==null&&SporeLease.activeFor(b)==null&&SporeLease.activeFor(c)==null,"original protection and original200t leases genuinely expired");
            h.assertTrue(cd(d)==0&&cd(a)==480-(h.getLevel().getGameTime()-start[0]),"fresh fourth fox is unused; no cooldown reset or time jump used");
            require(h,cast(h,d,pos(h,12,14)),SkillResult.SUCCESS,"new legal fresh caster acts after real protection deadline");h.assertTrue(!target.hasEffect(MobEffects.WEAKNESS)&&SporeEffects.cleanseProtectionTicks(target)==200,"remaining negative may be removed only after actual shared protection expired");guard(h);pass("REAL_REMOVE_CANCEL_SHARED_PROTECTION_199_PLUS_2");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=920)
    public static void nativeIncomingEffectsAreCopiedOnceAcrossOverlappingAurasAndOldEffectsKeepNativeMerge(GameTestHelper h){
        floor(h);var a=fox(h,"DurationA",4,8);var b=fox(h,"DurationB",16,8);var old=slime(h,"DurationA",10,8);var fresh=slime(h,"DurationA",10,10);var outside=slime(h,"DurationA",20,20);AlliancesServerEvents.addAlliance(a.getOwnerName(),b.getOwnerName());
        MobEffectInstance[] oldWeak={null},appliedSlow={null},appliedWeak={null};long[] start={0};
        ready(h,a,b,old,fresh,outside).thenExecute(()->{
            h.assertTrue(old.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400))&&old.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,400)),"old actual effects exist before aura birth");oldWeak[0]=old.getEffect(MobEffects.WEAKNESS);
            require(h,cast(h,a,pos(h,8,8)),SkillResult.SUCCESS,"first real aura");require(h,cast(h,b,pos(h,12,8)),SkillResult.SUCCESS,"second real overlapping aura");h.assertTrue(active(h,a).distanceToSqr(fresh)<9&&active(h,b).distanceToSqr(fresh)<9,"new recipient is genuinely inside both spherical radii");
            var incomingSlow=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,100,2,true,false,true);var incomingWeak=new MobEffectInstance(MobEffects.WEAKNESS,101,0,false,true,false);
            h.assertTrue(fresh.addEffect(incomingSlow)&&fresh.addEffect(incomingWeak),"actual native addEffect accepts finite known negatives");appliedSlow[0]=fresh.getEffect(MobEffects.MOVEMENT_SLOWDOWN);appliedWeak[0]=fresh.getEffect(MobEffects.WEAKNESS);
            h.assertTrue(appliedSlow[0]!=incomingSlow&&appliedSlow[0].getDuration()==80&&appliedWeak[0]!=incomingWeak&&appliedWeak[0].getDuration()==81,"entry copies reduce100 to80 and ceil101 to81 once, never64 under two spores");
            h.assertTrue(incomingSlow.getDuration()==100&&incomingWeak.getDuration()==101&&incomingSlow.getAmplifier()==2&&incomingSlow.isAmbient()&&!incomingSlow.isVisible()&&incomingSlow.showIcon(),"caller's original instances and flags remain unchanged");
            h.assertTrue(appliedSlow[0].getAmplifier()==2&&appliedSlow[0].isAmbient()&&!appliedSlow[0].isVisible()&&appliedSlow[0].showIcon()&&!appliedWeak[0].showIcon(),"copy preserves amplifier, ambient, visibility and icon flags");
            var shorter=new MobEffectInstance(MobEffects.WEAKNESS,100);old.addEffect(shorter);h.assertTrue(old.getEffect(MobEffects.WEAKNESS)==oldWeak[0]&&oldWeak[0].getDuration()==400&&shorter.getDuration()==100,"longer old state follows native same-type merge, not forcibly shortened or refreshed");
            var far=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,100);h.assertTrue(outside.addEffect(far)&&outside.getEffect(MobEffects.MOVEMENT_SLOWDOWN)==far&&far.getDuration()==100,"ordinary outside-aura addEffect remains untouched");start[0]=h.getLevel().getGameTime();
        }).thenIdle(5).thenExecute(()->{
            int elapsed=(int)(h.getLevel().getGameTime()-start[0]);h.assertTrue(oldWeak[0].getDuration()==400-elapsed&&appliedSlow[0].getDuration()==80-elapsed&&appliedWeak[0].getDuration()==81-elapsed,"ongoing ticks use normal duration countdown, not per-tick or per-spore multiplication");guard(h);pass("NATIVE_ADD_EFFECT_COPY_CEIL_MAX_AURA_OLD_MERGE");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=900)
    public static void whitelistRejectsUnknownInfiniteMaxAndHiddenChainsAndPreservesNativePoisonImmunity(GameTestHelper h){
        floor(h);var source=fox(h,"Excluded",4,8);var finite=slime(h,"Excluded",8,10);var infinite=slime(h,"Excluded",8,6);var maximum=slime(h,"Excluded",6,8);var hidden=slime(h,"Excluded",10,8);var unknown=slime(h,"Excluded",10,10);
        var undead=com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());undead.setOwnerName(account("Excluded"));undead.setNoAi(true);place(h,undead,6,6);
        var vanilla=EntityType.ZOMBIE.create(h.getLevel());vanilla.setNoAi(true);place(h,vanilla,6,10);
        ready(h,source,finite,infinite,maximum,hidden,unknown,undead,vanilla).thenExecute(()->{
            var infiniteOld=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,-1);var maximumOld=new MobEffectInstance(MobEffects.WEAKNESS,Integer.MAX_VALUE);
            var hiddenOld=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400,1,false,true,true,new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,-1,0),Optional.empty());var unknownOld=new MobEffectInstance(MobEffects.DIG_SLOWDOWN,400);
            h.assertTrue(finite.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400))&&infinite.addEffect(infiniteOld)&&maximum.addEffect(maximumOld)&&hidden.addEffect(hiddenOld)&&unknown.addEffect(unknownOld),"native controls contain one finite known, infinite, maximum, hidden chain and unlisted effect");
            h.assertTrue(!vanilla.addEffect(new MobEffectInstance(MobEffects.POISON,100))&&!vanilla.hasEffect(MobEffects.POISON),"non-Unit vanilla Zombie retains native poison immunity; it is not an aura-managed target");
            var baseline=new MobEffectInstance(MobEffects.POISON,100);h.assertTrue(undead.addEffect(baseline)&&undead.getEffect(MobEffects.POISON)==baseline&&baseline.getDuration()==100,"actual RTS Zombie intentionally allows poison before aura");h.assertTrue(undead.removeEffect(MobEffects.POISON),"clear only the explicit allowed-poison setup effect before birth");
            require(h,cast(h,source,pos(h,8,8)),SkillResult.SUCCESS,"real activation tests actual eligibility");
            h.assertTrue(!finite.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&SporeEffects.cleanseProtectionTicks(finite)==200,"ordinary finite whitelist effect is positively cleansed");
            h.assertTrue(infinite.getEffect(MobEffects.MOVEMENT_SLOWDOWN)==infiniteOld&&infiniteOld.isInfiniteDuration()&&maximum.getEffect(MobEffects.WEAKNESS)==maximumOld&&maximumOld.getDuration()==Integer.MAX_VALUE&&hidden.getEffect(MobEffects.MOVEMENT_SLOWDOWN)==hiddenOld&&unknown.getEffect(MobEffects.DIG_SLOWDOWN)==unknownOld,"complex/permanent/unlisted originals are never removed as an entire chain");
            for(LivingEntity target:List.of(infinite,maximum,hidden,unknown,undead))h.assertTrue(SporeEffects.cleanseProtectionTicks(target)==0,"excluded/no-removal target does not consume protection");
            var infiniteNew=new MobEffectInstance(MobEffects.WEAKNESS,-1);var maximumNew=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,Integer.MAX_VALUE);
            var hiddenNew=new MobEffectInstance(MobEffects.WEAKNESS,100,1,false,true,true,new MobEffectInstance(MobEffects.WEAKNESS,1000,0),Optional.empty());var unknownNew=new MobEffectInstance(MobEffects.CONFUSION,100);
            h.assertTrue(infinite.addEffect(infiniteNew)&&maximum.addEffect(maximumNew)&&hidden.addEffect(hiddenNew)&&unknown.addEffect(unknownNew),"native addEffect accepts corresponding excluded incoming controls");
            h.assertTrue(infinite.getEffect(MobEffects.WEAKNESS)==infiniteNew&&infiniteNew.isInfiniteDuration()&&maximum.getEffect(MobEffects.MOVEMENT_SLOWDOWN)==maximumNew&&maximumNew.getDuration()==Integer.MAX_VALUE&&hidden.getEffect(MobEffects.WEAKNESS)==hiddenNew&&hiddenNew.getDuration()==100&&unknown.getEffect(MobEffects.CONFUSION)==unknownNew&&unknownNew.getDuration()==100,"excluded incoming effects are not copied/reduced under real aura");
            var poison=new MobEffectInstance(MobEffects.POISON,100);int[] applicableCalls={0};MobEffectInstance[] seen={null};boolean[] coreAllowed={false};
            watchApplicable(undead,event->{applicableCalls[0]++;seen[0]=event.getEffectInstance();coreAllowed[0]=event.getResult()==net.minecraftforge.eventbus.api.Event.Result.ALLOW;event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);});
            h.assertTrue(!undead.addEffect(poison)&&!undead.hasEffect(MobEffects.POISON)&&poison.getDuration()==100,"real managed addEffect still respects final Applicable DENY and leaves caller input unchanged");
            h.assertTrue(applicableCalls[0]==1&&coreAllowed[0]&&seen[0]!=poison&&seen[0].getDuration()==80,"actual Applicable observer sees one transformed copy after original RTS ALLOW, then rejects it");
            var allowed=new MobEffectInstance(MobEffects.POISON,100);h.assertTrue(undead.addEffect(allowed)&&undead.getEffect(MobEffects.POISON).getDuration()==80&&allowed.getDuration()==100,"without the one-shot DENY, the same real RTS Zombie native allowance remains intact under aura");
            h.assertTrue(!vanilla.addEffect(new MobEffectInstance(MobEffects.POISON,100))&&!vanilla.hasEffect(MobEffects.POISON),"nearby non-Unit vanilla Zombie remains outside aura and keeps native poison immunity");
            var positive=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,100);h.assertTrue(finite.addEffect(positive)&&finite.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration()==80&&positive.getDuration()==100,"same live aura still reduces a genuinely eligible incoming effect");guard(h);pass("WHITELIST_COMPLEX_EFFECT_EXCLUSIONS_AND_NATIVE_IMMUNITY");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=920)
    public static void realRemovalCallbacksRecheckEpochAndEffectIdentityAndBlockNestedDoubleCleanse(GameTestHelper h){
        floor(h);var sourceAba=fox(h,"RemoveSource",2,4);var sourceVictim=slime(h,"RemoveSource",8,4);
        var targetAbaSource=fox(h,"RemoveTarget",2,10);var targetAba=slime(h,"RemoveTarget",8,10);
        var replaceSource=fox(h,"RemoveReplace",14,4);var replaced=slime(h,"RemoveReplace",20,4);
        var outer=fox(h,"RemoveNested",14,14);var nested=fox(h,"RemoveNested",14,20);var nestedTarget=slime(h,"RemoveNested",20,14);
        ready(h,sourceAba,sourceVictim,targetAbaSource,targetAba,replaceSource,replaced,outer,nested,nestedTarget).thenExecute(()->{
            var sourceOld=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400);var targetOld=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400);var replacedOld=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400);
            var nestedSlow=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400);var nestedWeak=new MobEffectInstance(MobEffects.WEAKNESS,400);
            h.assertTrue(sourceVictim.addEffect(sourceOld)&&targetAba.addEffect(targetOld)&&replaced.addEffect(replacedOld)&&nestedTarget.addEffect(nestedSlow)&&nestedTarget.addEffect(nestedWeak),"actual native negative effects are installed before every real activation");
            int[] calls={0,0,0,0};
            watchRemoval(sourceVictim,event->{calls[0]++;String owner=sourceAba.getOwnerName();sourceAba.setOwnerName(targetAbaSource.getOwnerName());sourceAba.setOwnerName(owner);});
            require(h,cast(h,sourceAba,pos(h,6,4)),SkillResult.SUCCESS,"birth committed before source ABA in the actual Remove callback");
            h.assertTrue(calls[0]==1&&sourceVictim.getEffect(MobEffects.MOVEMENT_SLOWDOWN)==sourceOld&&SporeEffects.cleanseProtectionTicks(sourceVictim)==0&&SporeLease.activeFor(sourceAba)==null&&cd(sourceAba)==480,"source ABA revokes this cleanse/lease without removing old effect, consuming protection or refunding committed birth");

            watchRemoval(targetAba,event->{calls[1]++;String owner=targetAba.getOwnerName();targetAba.setOwnerName(sourceAba.getOwnerName());targetAba.setOwnerName(owner);});
            require(h,cast(h,targetAbaSource,pos(h,6,10)),SkillResult.SUCCESS,"real activation with target ABA inside actual Remove");
            h.assertTrue(calls[1]==1&&targetAba.getEffect(MobEffects.MOVEMENT_SLOWDOWN)==targetOld&&SporeEffects.cleanseProtectionTicks(targetAba)==0&&cd(targetAbaSource)==480,"target ABA invalidates captured epoch even when owner string returns; original effect remains and no protection is issued");active(h,targetAbaSource);

            var replacement=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,600,1);MobEffectInstance[] removed={null};boolean[] replacementAdded={false};
            watchRemoval(replaced,event->{calls[2]++;removed[0]=replaced.removeEffectNoUpdate(MobEffects.MOVEMENT_SLOWDOWN);replacementAdded[0]=replaced.addEffect(replacement);});
            require(h,cast(h,replaceSource,pos(h,18,4)),SkillResult.SUCCESS,"real activation whose callback replaces the selected same-type instance");
            h.assertTrue(calls[2]==1&&removed[0]==replacedOld&&replacementAdded[0]&&replaced.getEffect(MobEffects.MOVEMENT_SLOWDOWN)!=replacedOld&&replaced.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration()==480&&replacement.getDuration()==600,"callback installed a genuinely new aura-transformed instance; outer stale selection cannot delete it");
            h.assertTrue(SporeEffects.cleanseProtectionTicks(replaced)==0&&cd(replaceSource)==480,"replacement is not counted as a successful cleanse and does not refund birth");active(h,replaceSource);

            SkillResult[] nestedResult={null};boolean[] nestedKeptBoth={false};
            watchRemoval(nestedTarget,event->{calls[3]++;nestedResult[0]=cast(h,nested,pos(h,20,16));nestedKeptBoth[0]=nestedTarget.getEffect(MobEffects.MOVEMENT_SLOWDOWN)==nestedSlow&&nestedTarget.getEffect(MobEffects.WEAKNESS)==nestedWeak&&SporeEffects.cleanseProtectionTicks(nestedTarget)==0;});
            require(h,cast(h,outer,pos(h,18,14)),SkillResult.SUCCESS,"outer real activation remains authorized after nested real birth");
            require(h,nestedResult[0],SkillResult.SUCCESS,"a distinct fox can legitimately create its own carrier inside Remove callback");
            h.assertTrue(calls[3]==1&&nestedKeptBoth[0]&&!nestedTarget.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&nestedTarget.getEffect(MobEffects.WEAKNESS)==nestedWeak&&SporeEffects.cleanseProtectionTicks(nestedTarget)==200,"nested activation cannot recursively cleanse locked target; only outer selected instance is removed once");
            h.assertTrue(cd(outer)==480&&cd(nested)==480&&SporeLease.activeCount(outer.getOwnerName())==2,"both legitimately committed births spend their own cooldown and retain exactly their own leases");active(h,outer);active(h,nested);guard(h);pass("REMOVE_CALLBACK_EPOCH_IDENTITY_AND_NESTED_BIRTH");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=920)
    public static void realApplicableAndAddedCallbacksPreserveEntrySnapshotAndCopyMetadataExactlyOnce(GameTestHelper h){
        floor(h);var left=fox(h,"IncomingLeft",4,8);var right=fox(h,"IncomingRight",16,8);AlliancesServerEvents.addAlliance(left.getOwnerName(),right.getOwnerName());
        var leaving=slime(h,"IncomingLeft",10,8);var recursive=slime(h,"IncomingLeft",10,10);var custom=slime(h,"IncomingLeft",8,10);var lazy=slime(h,"IncomingLeft",8,6);var minimum=slime(h,"IncomingLeft",12,6);
        ready(h,left,right,leaving,recursive,custom,lazy,minimum).thenExecute(()->{
            require(h,cast(h,left,pos(h,8,8)),SkillResult.SUCCESS,"first true aura");require(h,cast(h,right,pos(h,12,8)),SkillResult.SUCCESS,"second overlapping true aura");
            var original=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,100);int[] applicableCalls={0};MobEffectInstance[] observed={null};
            watchApplicable(leaving,event->{applicableCalls[0]++;observed[0]=event.getEffectInstance();var p=pos(h,20,20);leaving.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);});
            h.assertTrue(leaving.addEffect(original),"native addEffect still applies after permitted callback movement");
            h.assertTrue(applicableCalls[0]==1&&observed[0]!=original&&observed[0].getDuration()==80&&leaving.getEffect(MobEffects.MOVEMENT_SLOWDOWN)==observed[0]&&original.getDuration()==100,"entry-qualified incoming keeps its single reduction even though actual Applicable callback leaves all auras");
            h.assertTrue(leaving.distanceToSqr(active(h,left))>9&&leaving.distanceToSqr(active(h,right))>9,"callback truly moved target outside both live auras; no duration restoration");

            var recursiveOriginal=new MobEffectInstance(MobEffects.WEAKNESS,100);int[] addedCalls={0};MobEffectInstance[] transformed={null};boolean[] recursiveAccepted={false};
            watchAdded(recursive,event->{addedCalls[0]++;transformed[0]=event.getEffectInstance();recursiveAccepted[0]=recursive.addEffect(event.getEffectInstance());});
            h.assertTrue(recursive.addEffect(recursiveOriginal),"outer native addition completes after actual Added reentry");
            h.assertTrue(addedCalls[0]==1&&recursiveAccepted[0]&&transformed[0]!=recursiveOriginal&&recursive.getEffect(MobEffects.WEAKNESS)==transformed[0]&&transformed[0].getDuration()==80&&recursiveOriginal.getDuration()==100,"reusing the actual transformed event instance cannot multiply 80 into 64 under overlapping auras");

            var explicit=new MobEffectInstance(MobEffects.WEAKNESS,100,2,true,false,true);var originalStack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MILK_BUCKET,2);originalStack.getOrCreateTag().putString("fixture","original");var originalCuratives=new ArrayList<net.minecraft.world.item.ItemStack>();originalCuratives.add(originalStack);explicit.setCurativeItems(originalCuratives);
            h.assertTrue(custom.addEffect(explicit),"native addEffect accepts explicit-curative incoming instance");var applied=custom.getEffect(MobEffects.WEAKNESS);var copiedCuratives=applied.getCurativeItems();
            h.assertTrue(applied!=explicit&&applied.getDuration()==80&&explicit.getDuration()==100&&applied.getAmplifier()==2&&applied.isAmbient()&&!applied.isVisible()&&applied.showIcon(),"single copied reduction preserves all incoming flags/amplifier without mutating caller duration");
            h.assertTrue(copiedCuratives!=originalCuratives&&copiedCuratives.size()==1&&copiedCuratives.get(0)!=originalStack&&net.minecraft.world.item.ItemStack.isSameItemSameTags(copiedCuratives.get(0),originalStack)&&copiedCuratives.get(0).getCount()==2,"curative list and nested stack/NBT are deep copied");
            copiedCuratives.get(0).setCount(1);copiedCuratives.get(0).getOrCreateTag().putString("fixture","copy");applied.addCurativeItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE));
            h.assertTrue(copiedCuratives.size()==2&&originalCuratives.size()==1&&originalStack.getCount()==2&&"original".equals(originalStack.getTag().getString("fixture")),"copied curatives stay mutable without changing the caller list, stack or tag");

            var lazyOriginal=new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,100);h.assertTrue(((com.dreamseekers.unit.mixin.SporeEffectAccess)lazyOriginal).dreamseekers$curativeItems()==null,"caller starts with actual uninitialized lazy-curative field");
            h.assertTrue(lazy.addEffect(lazyOriginal)&&lazy.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration()==80&&lazyOriginal.getDuration()==100&&((com.dreamseekers.unit.mixin.SporeEffectAccess)lazyOriginal).dreamseekers$curativeItems()==null,"actual aura transform never lazily initializes the caller's curative list");
            var shortest=new MobEffectInstance(MobEffects.WEAKNESS,1);h.assertTrue(minimum.addEffect(shortest)&&minimum.getEffect(MobEffects.WEAKNESS).getDuration()==1&&shortest.getDuration()==1,"minimum finite incoming duration remains one tick rather than zero");guard(h);pass("ENTRY_SNAPSHOT_RECURSIVE_COPY_AND_METADATA");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_fox_spore_cross_dimension",timeoutTicks=1250)
    public static void realDimensionTransferPreservesOneTrueOwnerCapAndOtherOwnersRemainIndependent(GameTestHelper h){
        floor(h);var arena=netherArena(h);List<GoldenFoxUnit> sources=new ArrayList<>();int[][] cells={{4,4},{4,10},{14,4},{14,10}};
        for(var cell:cells)sources.add(fox(h,"CrossOwner",cell[0],cell[1]));var owner=player(h,"CrossOwner");var foreign=player(h,"CrossForeign");
        var fifth=otherFox(h,arena,"CrossOwner",4,4);var independent=otherFox(h,arena,"CrossForeign",4,8);
        ready(h,sources.toArray(Entity[]::new)).thenWaitUntil(()->{published(h,arena.level(),fifth);published(h,arena.level(),independent);}).thenExecute(()->{
            for(int i=0;i<sources.size();i++)require(h,cast(h,sources.get(i),pos(h,cells[i][0]+4,cells[i][1])),SkillResult.SUCCESS,"legitimate overworld lease "+i);
            h.assertTrue(SporeLease.activeCount(owner.getGameProfile().getName())==4,"same true owner has exactly four actual committed overworld leases");
            owner.teleportTo(arena.level(),arena.base().getX()+2.5,arena.base().getY()+1,arena.base().getZ()+2.5,0,0);
            h.assertTrue(owner.serverLevel()==arena.level()&&arena.level().players().contains(owner)&&!h.getLevel().players().contains(owner),"same authenticated ServerPlayer actually transfers dimensions, not a copied profile");
            require(h,SkillService.requestGround(owner,request(fifth,arena.at(8,4))),SkillResult.CAP_REACHED,"a legal fifth source/ground in another actual dimension cannot bypass true-owner global cap");
            h.assertTrue(cd(fifth)==0&&SporeLease.activeFor(fifth)==null&&SporeLease.activeCount(owner.getGameProfile().getName())==4,"cross-dimension cap rejection leaves fifth CD and all previous leases untouched");
            foreign.teleportTo(arena.level(),arena.base().getX()+2.5,arena.base().getY()+1,arena.base().getZ()+9.5,0,0);
            h.assertTrue(foreign.serverLevel()==arena.level()&&arena.level().players().contains(foreign),"second true owner also uses real transfer before authenticated cast");
            require(h,SkillService.requestGround(foreign,request(independent,arena.at(8,8))),SkillResult.SUCCESS,"another owner can legitimately create its own Nether carrier");
            h.assertTrue(SporeLease.activeFor(independent)!=null&&SporeLease.activeCount(foreign.getGameProfile().getName())==1&&SporeLease.activeCount(owner.getGameProfile().getName())==4&&cd(independent)==480,"owner quotas remain independent across actual worlds");
            for(var source:sources)h.assertTrue(SporeLease.activeFor(source)!=null&&cd(source)==480,"all four original overworld leases/CD are still committed in this same tick");
            var back=pos(h,2,21);owner.teleportTo(h.getLevel(),back.getX()+.5,back.getY(),back.getZ()+.5,0,0);foreign.teleportTo(h.getLevel(),back.getX()+1.5,back.getY(),back.getZ()+.5,0,0);guard(h);pass("REAL_CROSS_DIMENSION_OWNER_CAP");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_fox_spore_real_ground_fog",timeoutTicks=1400)
    public static void actualGroundFogLossDoesNotGainSporeVisionAndNonOverworldFogFailsClosed(GameTestHelper h){
        floor(h);var arena=netherArena(h);var source=fox(h,"GroundFog",4,10);var fresh=fox(h,"GroundFog",4,14);var owner=player(h,"GroundFog");var spotter=chicken(h,"GroundFog",9,12);
        for(var body:List.of(source,fresh,spotter))body.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.SIGHT_RANGE.get()).setBaseValue(4);
        var firstGround=pos(h,10,10);var newGround=pos(h,10,14);JungleSporeCarrier[] carrier={null};PinkChickenScout[] returned={null};GoldenFoxUnit[] nether={null};long[] castAt={0},hiddenAt={0};
        ready(h,source,fresh,spotter,owner).thenExecute(()->{fogScope=new RealFogScope(h,List.of(owner));fogScope.prepare();moveObserver(h,owner,120,120);fogScope.enable();h.assertTrue(source.distanceToSqr(Vec3.atBottomCenterOf(firstGround))>16&&fresh.distanceToSqr(Vec3.atBottomCenterOf(newGround))>16,"actual four-block unit sights cannot independently reveal six-block cast positions");})
          .thenWaitUntil(()->h.assertTrue(FogOfWarServerEvents.isFogActiveFor(owner)&&visible(owner,firstGround)&&visible(owner,newGround),"ordinary ten-tick raster sees both legal ground cells through real owned spotter"))
          .thenExecute(()->{require(h,cast(h,source,firstGround),SkillResult.SUCCESS,"current authoritative ground visibility authorizes real birth");carrier[0]=active(h,source);castAt[0]=h.getLevel().getGameTime();h.assertTrue(!carrier[0].isCurrentlyGlowing()&&!((Object)carrier[0] instanceof Unit),"carrier is neither a glowing marker nor an RTS sight unit");var retreat=pos(h,2,20);source.moveTo(retreat.getX()+.5,retreat.getY(),retreat.getZ()+.5);spotter.discard();})
          .thenWaitUntil(()->h.assertTrue(!visible(owner,firstGround)&&!visible(owner,newGround),"real mask converges after loaded fox retreat and actual spotter removal; no synthetic mask writes"))
          .thenExecute(()->{hiddenAt[0]=h.getLevel().getGameTime();h.assertTrue(hiddenAt[0]-castAt[0]<200&&SporeLease.active(carrier[0])&&SporeLease.remainingTicks(carrier[0])>0,"still-live carrier does not retain ground vision; this is not normal expiration");h.assertTrue(cd(fresh)==0&&fresh.distanceToSqr(Vec3.atBottomCenterOf(newGround))<=100,"hidden request uses genuine unused in-range caster, not a cooldown/range rejection");require(h,cast(h,fresh,newGround),SkillResult.HIDDEN,"actual currently hidden legal ground rejects fresh request");h.assertTrue(cd(fresh)==0&&SporeLease.activeFor(fresh)==null,"hidden denial spends no cooldown/slot");returned[0]=chicken(h,"GroundFog",9,12);returned[0].getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.SIGHT_RANGE.get()).setBaseValue(4);})
          .thenWaitUntil(()->h.assertTrue(returned[0].tickCount>3&&visible(owner,newGround),"a genuine new spotter naturally restores the overworld column"))
          .thenExecute(()->{nether[0]=otherFox(h,arena,"GroundFog",4,8);})
          .thenWaitUntil(()->{published(h,arena.level(),nether[0]);var ground=arena.at(8,8);h.assertTrue(arena.level().isPositionEntityTicking(ground)&&arena.level().areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(ground).toLong()),"actual destination chunk is entity-ticking with entity data loaded before testing birth: "+chunkDiagnostic(arena.level(),ground));})
          .thenExecute(()->{var ground=arena.at(8,8);h.assertTrue(visible(owner,ground),"same x/z is really visible in the dimensionless overworld mask immediately before transfer");owner.teleportTo(arena.level(),arena.base().getX()+2.5,arena.base().getY()+1,arena.base().getZ()+2.5,0,0);h.assertTrue(owner.serverLevel()==arena.level()&&arena.level().players().contains(owner)&&FogOfWarServerEvents.isFogActiveFor(owner),"same actual owner is now in real Nether with fog still enabled");require(h,SkillService.requestGround(owner,request(nether[0],ground)),SkillResult.HIDDEN,"dimensionless overworld provider never authorizes a non-overworld ground cast");h.assertTrue(cd(nether[0])==0&&SporeLease.activeFor(nether[0])==null,"non-overworld fog denial spends no cooldown/slot");FogOfWarServerEvents.setEnabled(false);JungleSporeCarrier[] constructed={null},joined={null};boolean[] joinCanceled={false};
            System.out.println("FOX_SPORE_NETHER_BIRTH_PRE base="+arena.base()+" "+netherBirthDiagnostic(arena.level(),nether[0],ground,null)+" remainingJoinCallbacks="+PROBE.callbacks.size()+" constructorProbe="+(PROBE.constructionCallback!=null));
            watchConstruction(event->{constructed[0]=(JungleSporeCarrier)event.getEntity();});watchBirthObservation(arena.level(),ground,event->{joined[0]=(JungleSporeCarrier)event.getEntity();joinCanceled[0]=event.isCanceled();System.out.println("FOX_SPORE_NETHER_BIRTH_JOIN canceled="+event.isCanceled()+" "+netherBirthDiagnostic(arena.level(),nether[0],ground,joined[0]));});
            SkillResult netherResult=SkillService.requestGround(owner,request(nether[0],ground));System.out.println("FOX_SPORE_NETHER_BIRTH_POST result="+netherResult+" constructed="+(constructed[0]!=null)+" joined="+(joined[0]!=null)+" joinCanceled="+joinCanceled[0]+" "+netherBirthDiagnostic(arena.level(),nether[0],ground,constructed[0]));require(h,netherResult,SkillResult.SUCCESS,"same sender/source/legal ground truly succeeds with global fog disabled");h.assertTrue(cd(nether[0])==480&&SporeLease.activeFor(nether[0])!=null,"positive non-fog control creates real Nether lease");var back=pos(h,2,21);owner.teleportTo(h.getLevel(),back.getX()+.5,back.getY(),back.getZ()+.5,0,0);System.out.println("FOX_SPORE_REAL_FOG castAt="+castAt[0]+" hiddenAt="+hiddenAt[0]+" current="+h.getLevel().getGameTime());guard(h);pass("REAL_GROUND_FOG_NO_SPORE_VISION_AND_DIMENSION_GATE");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_fox_spore_real_effect_audience",timeoutTicks=1350)
    public static void realEffectRemovalPacketReachesOnlyExactTrackedAndCurrentlyVisibleObservers(GameTestHelper h){
        floor(h);var source=fox(h,"AudienceOwner",4,10);var target=slime(h,"AudienceOwner",10,10);var owner=player(h,"AudienceOwner");var hidden=player(h,"AudienceHidden");var untracked=player(h,"AudienceFar");AlliancesServerEvents.addAlliance(source.getOwnerName(),untracked.getGameProfile().getName());String[] lastTracking={""};
        ready(h,source,target,owner,hidden,untracked).thenExecute(()->{
            var expected=new SkillNetwork.EffectRemoved(target.getId(),target.getUUID(),new net.minecraft.resources.ResourceLocation("minecraft","slowness"));FriendlyByteBuf wire=new FriendlyByteBuf(Unpooled.buffer());try{expected.encode(wire);h.assertTrue(expected.equals(SkillNetwork.EffectRemoved.decode(wire))&&wire.readableBytes()==0,"actual EffectRemoved codec carries precise entity ID+UUID and registry key without trailing data");}finally{wire.release();}
            fogScope=new RealFogScope(h,List.of(owner,hidden,untracked));fogScope.prepare();moveObserver(h,owner,18,18);moveObserver(h,hidden,22,18);moveObserver(h,untracked,500,500);
        }).thenWaitUntil(()->{String state=trackingDiagnostic(target,owner,hidden,untracked);if(!state.equals(lastTracking[0])||h.getTick()>=1320&&h.getTick()%10==0){System.out.println("FOX_SPORE_TRACKING_DIAGNOSTIC testTick="+h.getTick()+" "+state);lastTracking[0]=state;}h.assertTrue(tracked(target,owner)&&tracked(target,hidden)&&!tracked(target,untracked),"real fog-off ChunkMap tracking pairs near observers and excludes distant observer without editing seenBy: "+state);})
          .thenExecute(()->{fogScope.enable();})
          .thenWaitUntil(()->h.assertTrue(visible(owner,target.blockPosition())&&!visible(hidden,target.blockPosition())&&visible(untracked,target.blockPosition()),"actual per-player masks differ: own/ally visible, orthoview enemy hidden, regardless of viewer position"))
          .thenExecute(()->{
            h.assertTrue(tracked(target,owner)&&tracked(target,hidden)&&!tracked(target,untracked),"native hidden update cancellation really leaves old hidden connection tracked; far visible observer remains untracked");
            var recipients=SporeEffectAudience.visibleTracking(target);h.assertTrue(recipients.contains(owner)&&!recipients.contains(hidden)&&!recipients.contains(untracked),"read-only actual recipient provider is the exact tracking and current visibility intersection");
            h.assertTrue(target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400)),"real negative effect exists before activation");for(var viewer:List.of(owner,hidden,untracked))wire(viewer).removals.clear();
            require(h,cast(h,source,pos(h,8,10)),SkillResult.SUCCESS,"real authenticated birth performs actual successful removal");h.assertTrue(!target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&SporeEffects.cleanseProtectionTicks(target)==200,"server removal really occurred before testing its packets");
            var ownPackets=wire(owner).removals.stream().filter(p->p.targetUuid().equals(target.getUUID())).toList();h.assertTrue(ownPackets.size()==1&&ownPackets.get(0).targetId()==target.getId()&&ownPackets.get(0).effect().equals(new net.minecraft.resources.ResourceLocation("minecraft","slowness")),"actual encoded unit_skills discriminator4 emits exactly the successful removal to tracked-visible owner");
            h.assertTrue(wire(hidden).removals.stream().noneMatch(p->p.targetUuid().equals(target.getUUID()))&&wire(untracked).removals.stream().noneMatch(p->p.targetUuid().equals(target.getUUID())),"neither real tracked-hidden nor visible-untracked player receives the removal identity");System.out.println("FOX_SPORE_ACTUAL_AUDIENCE target="+target.getId()+"/"+target.getUUID()+" ownerTracked="+tracked(target,owner)+" hiddenTracked="+tracked(target,hidden)+" farTracked="+tracked(target,untracked)+" packets="+ownPackets.size()+"/"+wire(hidden).removals.size()+"/"+wire(untracked).removals.size());guard(h);pass("REAL_TRACKING_FOG_INTERSECTION_AND_ENCODED_REMOVAL");
          }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1120)
    public static void successfulCleanseProtectionKeepsItsOriginalDeadlineAcrossSameObjectOwnerAbaOnly(GameTestHelper h){
        floor(h);var first=fox(h,"ProtectAbaA",4,10);var second=fox(h,"ProtectAbaB",16,10);var fresh=fox(h,"ProtectAbaA",4,16);var target=slime(h,"ProtectAbaA",10,10);AlliancesServerEvents.addAlliance(first.getOwnerName(),second.getOwnerName());long[] cleansedAt={0};UUID originalUuid=target.getUUID();
        ready(h,first,second,fresh,target).thenExecute(()->{
            h.assertTrue(target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,400))&&target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,400)),"real target starts with two eligible finite effects");require(h,cast(h,first,pos(h,8,10)),SkillResult.SUCCESS,"first successful real cleanse starts target throttle");cleansedAt[0]=h.getLevel().getGameTime();
            h.assertTrue(!target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&target.hasEffect(MobEffects.WEAKNESS)&&SporeEffects.cleanseProtectionTicks(target)==200,"successful removal really created a two-hundred-tick target protection");
            require(h,SkillService.request(playerFor(h,first),blessing(first)),SkillResult.SUCCESS,"same genuine fox grants ordinary blessing as ownership-cleanup control");close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.20,"real independent temporary blessing exists before transfer");
        }).thenIdle(40).thenExecute(()->{
            int remaining=200-(int)(h.getLevel().getGameTime()-cleansedAt[0]);h.assertTrue(remaining==160&&SporeEffects.cleanseProtectionTicks(target)==remaining,"forty actual ticks elapsed before transfer; no simulated time or cooldown edits");String originalOwner=target.getOwnerName();target.setOwnerName(second.getOwnerName());target.setOwnerName(originalOwner);
            h.assertTrue(target.getUUID().equals(originalUuid)&&target.level()==h.getLevel()&&h.getLevel().getEntity(originalUuid)==target&&SporeEffects.cleanseProtectionTicks(target)==remaining,"same loaded object/UUID/world owner ABA preserves only the original successful-cleanse deadline");close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"ordinary blessing still clears on ownership epoch change; throttle exception does not preserve other buffs");
            require(h,cast(h,second,pos(h,12,10)),SkillResult.SUCCESS,"another real allied fox can spend its own seed after ownership ABA");h.assertTrue(target.hasEffect(MobEffects.WEAKNESS)&&SporeEffects.cleanseProtectionTicks(target)==remaining,"second activation cannot exploit ABA to cleanse again or refresh the original throttle");guard(h);
        }).thenIdle(159).thenExecute(()->{h.assertTrue(h.getLevel().getGameTime()-cleansedAt[0]==199&&SporeEffects.cleanseProtectionTicks(target)==1&&target.hasEffect(MobEffects.WEAKNESS),"protection lasts the original actual 199 ticks across transfer, not a new 200-tick window");})
          .thenIdle(2).thenExecute(()->{h.assertTrue(h.getLevel().getGameTime()-cleansedAt[0]==201&&SporeEffects.cleanseProtectionTicks(target)==0&&target.hasEffect(MobEffects.WEAKNESS)&&cd(fresh)==0,"original protection expires normally while untouched fresh caster remains available");require(h,cast(h,fresh,pos(h,8,12)),SkillResult.SUCCESS,"fresh actual activation may cleanse after the original deadline");h.assertTrue(!target.hasEffect(MobEffects.WEAKNESS)&&SporeEffects.cleanseProtectionTicks(target)==200&&cd(first)==480-(int)(h.getLevel().getGameTime()-cleansedAt[0]),"post-deadline new removal creates its own throttle without refunding any original cooldown");h.assertTrue(target.hurt(target.damageSources().genericKill(),10000)&&!target.isAlive()&&SporeEffects.cleanseProtectionTicks(target)==0,"actual death still clears successful protection; owner-only exception cannot preserve dead-target throttle");guard(h);pass("SUCCESSFUL_CLEANSE_THROTTLE_OWNER_ABA_ORIGINAL_DEADLINE");}).thenSucceed();
    }

    private static RealFogScope fogScope;
    private static final Map<ServerLevel,Set<Long>> DIMENSION_CHUNKS=new IdentityHashMap<>();
    @AfterBatch(batch="dreamseekers_fox_spore_cross_dimension") public static void cleanupCrossDimension(ServerLevel level){cleanupIsolated(level);}
    @AfterBatch(batch="dreamseekers_fox_spore_real_ground_fog") public static void cleanupGroundFog(ServerLevel level){cleanupIsolated(level);}
    @AfterBatch(batch="dreamseekers_fox_spore_real_effect_audience") public static void cleanupEffectAudience(ServerLevel level){cleanupIsolated(level);}
    private static void cleanupIsolated(ServerLevel level){
        try{if(fogScope!=null){var scope=fogScope;fogScope=null;scope.close();}}
        finally{try{cleanup(level);}finally{for(var entry:DIMENSION_CHUNKS.entrySet())for(long packed:entry.getValue()){var chunk=new net.minecraft.world.level.ChunkPos(packed);entry.getKey().setChunkForced(chunk.x,chunk.z,false);}DIMENSION_CHUNKS.clear();}}
    }
    private record NetArena(ServerLevel level,BlockPos base){BlockPos at(int x,int z){return base.offset(x,1,z);}}
    private static NetArena netherArena(GameTestHelper h){
        ServerLevel level=h.getLevel().getServer().getLevel(Level.NETHER);h.assertTrue(level!=null,"actual server Nether exists");var origin=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(origin.getX()+2,70,origin.getZ()+2);Set<Long> chunks=DIMENSION_CHUNKS.computeIfAbsent(level,key->new LinkedHashSet<>());
        for(int x=base.getX()>>4;x<=(base.getX()+11)>>4;x++)for(int z=base.getZ()>>4;z<=(base.getZ()+11)>>4;z++){long key=net.minecraft.world.level.ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"fixture never adopts an unrelated forced Nether chunk");level.setChunkForced(x,z,true);chunks.add(key);}
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)level.setBlock(base.offset(x,7,z),Blocks.STONE.defaultBlockState(),2);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(base.getX(),base.getY()+1,base.getZ(),base.getX()+12,level.getMaxBuildHeight(),base.getZ()+12)).forEach(Entity::discard);
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)for(int y=0;y<7;y++)level.setBlock(base.offset(x,y,z),(y==0||x==0||x==11||z==0||z==11?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        level.getEntitiesOfClass(ItemEntity.class,new AABB(base,base.offset(12,8,12))).forEach(Entity::discard);return new NetArena(level,base);
    }
    private static GoldenFoxUnit otherFox(GameTestHelper h,NetArena arena,String suffix,int x,int z){var e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(arena.level());e.setOwnerName(account(suffix));e.setNoAi(true);var p=arena.at(x,z);e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(arena.level().addFreshEntity(e),"actual Nether source added through world");player(h,suffix);return e;}
    private static PinkChickenScout chicken(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(h.getLevel());e.setOwnerName(account(suffix));e.setNoAi(true);place(h,e,x,z);player(h,suffix);return e;}
    private static void published(GameTestHelper h,ServerLevel level,Entity body){h.assertTrue(body!=null&&body.level()==level&&body.tickCount>3&&level.getEntity(body.getId())==body&&level.getEntity(body.getUUID())==body,"exact actual dimension identity and natural entity ticks: "+(body==null?"null":body.getId()+"/"+body.tickCount));}
    private static boolean visible(ServerPlayer viewer,BlockPos position){return FogOfWarServerEvents.isBlockVisibleFor(viewer,position.getX(),position.getZ());}
    private static boolean tracked(LivingEntity target,ServerPlayer viewer){var level=(ServerLevel)target.level();Object entry=((com.dreamseekers.unit.mixin.SporeChunkTrackingAccess)level.getChunkSource().chunkMap).dreamseekers$trackedEntities().get(target.getId());return entry instanceof com.dreamseekers.unit.mixin.SporeTrackedEntityAccess access&&access.dreamseekers$trackedEntity()==target&&access.dreamseekers$trackingConnections().contains(viewer.connection);}
    /** Read only: no SkillIdentity capture, no lease pruning and no chunk-load request. */
    private static String netherBirthDiagnostic(ServerLevel level,GoldenFoxUnit source,BlockPos ground,JungleSporeCarrier carrier){
        String value="source="+source.getId()+"/"+source.getUUID()+" pos="+source.position()+" alive="+source.isAlive()+" removed="+source.isRemoved()+" owner="+source.getOwnerName()+" ownerRevision="+source.rts().ownershipRevision+" commandRevision="+source.rts().commandRevision+" skillRevision="+source.rts().skills().revision()+" sourceIdLookup="+(level.getEntity(source.getId())==source)+" sourceUuidLookup="+(level.getEntity(source.getUUID())==source)+" sourceChunk{"+chunkDiagnostic(level,source.blockPosition())+"} ground="+ground+" groundChunk{"+chunkDiagnostic(level,ground)+"}";
        if(carrier!=null)value+=" carrier="+carrier.getId()+"/"+carrier.getUUID()+" pos="+carrier.position()+" hp="+carrier.getHealth()+" max="+carrier.getMaxHealth()+" alive="+carrier.isAlive()+" removed="+carrier.isRemoved()+" reason="+carrier.getRemovalReason()+" idLookup="+(level.getEntity(carrier.getId())==carrier)+" uuidLookup="+(level.getEntity(carrier.getUUID())==carrier);return value;
    }
    private static String chunkDiagnostic(ServerLevel level,BlockPos position){var chunk=new net.minecraft.world.level.ChunkPos(position);var actual=level.getChunkSource().getChunkNow(chunk.x,chunk.z);return "chunk="+chunk+" has="+level.hasChunkAt(position)+" actual="+(actual!=null)+" fullStatus="+(actual==null?"null":actual.getFullStatus())+" entityTicking="+level.isPositionEntityTicking(position)+" entitiesLoaded="+level.areEntitiesLoaded(chunk.toLong())+" forced="+level.getForcedChunks().contains(chunk.toLong());}
    private record BirthObservation(ServerLevel level,BlockPos position,Consumer<EntityJoinLevelEvent> callback){}
    private static void watchBirthObservation(ServerLevel level,BlockPos position,Consumer<EntityJoinLevelEvent> callback){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}if(PROBE.birthObservation!=null)throw new IllegalStateException("duplicate read-only birth observation");PROBE.birthObservation=new BirthObservation(level,position.immutable(),callback);}
    /** Read-only runtime evidence; no tracker membership, configuration or fixture timing mutation. */
    private static String trackingDiagnostic(LivingEntity target,ServerPlayer...viewers){
        ServerLevel level=(ServerLevel)target.level();Object chunkMap=level.getChunkSource().chunkMap;Object entry=((com.dreamseekers.unit.mixin.SporeChunkTrackingAccess)chunkMap).dreamseekers$trackedEntities().get(target.getId());StringBuilder out=new StringBuilder("target=").append(target.getId()).append('/').append(target.getUUID()).append(" pos=").append(target.position()).append(" playerListView=").append(level.getServer().getPlayerList().getViewDistance()).append(" typeRangeChunks=").append(target.getType().clientTrackingRange()).append(" exactTrackedObject=").append(entry instanceof com.dreamseekers.unit.mixin.SporeTrackedEntityAccess a&&a.dreamseekers$trackedEntity()==target);
        try{var view=net.minecraft.server.level.ChunkMap.class.getDeclaredField("viewDistance");view.setAccessible(true);out.append(" chunkMapView=").append(view.getInt(chunkMap));if(entry!=null){var range=entry.getClass().getDeclaredField("range");range.setAccessible(true);var effective=entry.getClass().getDeclaredMethod("getEffectiveRange");effective.setAccessible(true);out.append(" rawRange=").append(range.getInt(entry)).append(" effectiveRange=").append(effective.invoke(entry));}}catch(ReflectiveOperationException failure){out.append(" readOnlyReflectionFailure=").append(failure);}
        for(var player:viewers){double dx=target.getX()-player.getX(),dz=target.getZ()-player.getZ();out.append(" observer=").append(player.getGameProfile().getName()).append(" tracked=").append(tracked(target,player)).append(" xzDistance=").append(Math.sqrt(dx*dx+dz*dz)).append(" pos=").append(player.position()).append(" sameWorld=").append(player.serverLevel()==level).append(" actualLevelPlayer=").append(level.players().contains(player));}return out.toString();
    }
    private static void moveObserver(GameTestHelper h,ServerPlayer player,int x,int z){var origin=h.absolutePos(BlockPos.ZERO);player.setNoGravity(true);player.moveTo(origin.getX()+x+.5,200,origin.getZ()+z+.5,0,0);}
    private static RecordingConnection wire(ServerPlayer player){return ((RecordingHandler)player.connection).wire;}
    /** Only normal fog configuration + genuine player membership; never writes a visibility mask or tracker set. */
    private static final class RealFogScope implements AutoCloseable{
        final GameTestHelper helper;final ServerLevel level;final List<FakePlayer> viewers;final boolean oldEnabled,oldForce;final double size,centerX,centerZ;boolean closed;
        RealFogScope(GameTestHelper h,List<FakePlayer> viewers){helper=h;level=h.getLevel();this.viewers=List.copyOf(viewers);oldEnabled=FogOfWarServerEvents.isEnabled();oldForce=level.getGameRules().getBoolean(com.solegendary.reignofnether.registrars.GameRuleRegistrar.FORCE_FOG);var border=level.getWorldBorder();size=border.getSize();centerX=border.getCenterX();centerZ=border.getCenterZ();}
        void prepare(){var origin=helper.absolutePos(BlockPos.ZERO);level.getGameRules().getRule(com.solegendary.reignofnether.registrars.GameRuleRegistrar.FORCE_FOG).set(false,level.getServer());level.getWorldBorder().setCenter(origin.getX()+12,origin.getZ()+12);level.getWorldBorder().setSize(1280);for(var player:viewers){helper.assertTrue(player.serverLevel()==level&&level.players().contains(player),"Fog viewer is the actual published same-world ServerPlayer");membership(player,true);if(!PlayerServerEvents.orthoviewPlayers.contains(player))PlayerServerEvents.orthoviewPlayers.add(player);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(player.getGameProfile().getName()));PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(player.getGameProfile().getName(),DreamseekersMod.DREAMSEEKERS,player.getId(),0,true,-1L,-1L));}FogOfWarServerEvents.invalidateRtsCache();}
        void enable(){FogOfWarServerEvents.setEnabled(true);helper.assertTrue(FogOfWarServerEvents.isEnabled(),"real public fog toggle accepted on bounded actual map");}
        @SuppressWarnings("unchecked") private void membership(ServerPlayer player,boolean present){try{var list=level.getServer().getPlayerList();var p=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");var u=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");p.setAccessible(true);u.setAccessible(true);List<ServerPlayer> ps=(List<ServerPlayer>)p.get(list);Map<UUID,ServerPlayer> ids=(Map<UUID,ServerPlayer>)u.get(list);if(present){if(!ps.contains(player))ps.add(player);ids.put(player.getUUID(),player);}else{ps.remove(player);ids.remove(player.getUUID(),player);}}catch(ReflectiveOperationException failure){throw new RuntimeException("actual local test-player membership",failure);}}
        @Override public void close(){if(closed)return;closed=true;try{for(var player:viewers){membership(player,false);PlayerServerEvents.orthoviewPlayers.remove(player);FogOfWarServerEvents.onPlayerDisconnect(player.getUUID());}FogOfWarServerEvents.invalidateRtsCache();}finally{level.getGameRules().getRule(com.solegendary.reignofnether.registrars.GameRuleRegistrar.FORCE_FOG).set(false,level.getServer());FogOfWarServerEvents.setEnabled(oldEnabled);level.getWorldBorder().setCenter(centerX,centerZ);level.getWorldBorder().setSize(size);level.getGameRules().getRule(com.solegendary.reignofnether.registrars.GameRuleRegistrar.FORCE_FOG).set(oldForce,level.getServer());}}
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1060)
    public static void authenticatedExplicitAttackLetsActualOrdinaryPinkSlimeAiDestroyOnlyEnemyCarrier(GameTestHelper h){
        floor(h);var fox=fox(h,"PointOwner",4,8);var attacker=slime(h,"PointAttacker",10,8);JungleSporeCarrier[] carrier={null};long[] castAt={0};
        ready(h,fox,attacker).thenExecute(()->{setAttackDamage(attacker,20);attacker.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);require(h,cast(h,fox,pos(h,8,8)),SkillResult.SUCCESS,"real ten-HP target born before explicit order");carrier[0]=active(h,fox);castAt[0]=h.getLevel().getGameTime();
            h.assertTrue(!unitCommand(h,playerFor(h,fox),attacker,com.solegendary.reignofnether.unit.UnitAction.ATTACK,carrier[0],false),"real foreign sender cannot control attacker's RTS unit");h.assertTrue(unitCommand(h,player(h,attacker.getOwnerName()),attacker,com.solegendary.reignofnether.unit.UnitAction.ATTACK,carrier[0],false),"actual owner network context accepts explicit ATTACK");})
          .thenWaitUntil(()->directed(h,attacker,carrier[0]))
          .thenExecute(()->{close(h,carrier[0].getHealth(),10,"no premature damage while waiting for actual command execution");countAttacks(carrier[0]);attacker.setNoAi(false);})
          .thenWaitUntil(()->h.assertTrue(!carrier[0].isAlive()||carrier[0].isRemoved(),"normal installed melee goal naturally destroys explicitly ordered enemy carrier"))
          .thenExecute(()->{attacker.setNoAi(true);h.assertTrue(PROBE.attacks.getOrDefault(carrier[0].getUUID(),0)>0&&h.getLevel().getGameTime()-castAt[0]<200&&carrier[0].getHealth()==0,"real native attack caused death before lease expiration; not a timed discard");h.assertTrue(SporeLease.activeFor(fox)==null&&cd(fox)>0,"destroyed carrier frees lease but never refunds caster's cooldown");guard(h);pass("AUTHENTICATED_POINT_ORDER_NORMAL_MELEE_AI_KILL");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=960)
    public static void uncommandedForcedBitsFriendlyAndRawDamageCannotEnterCarrierOrGrantOnHitBenefits(GameTestHelper h){
        floor(h);var fox=fox(h,"NoPointOwner",4,8);var attacker=slime(h,"NoPointEnemy",10,8);var friendly=slime(h,"NoPointOwner",8,12);var wind=DreamseekersMod.WIND_KNIGHT_UNIT.get().create(h.getLevel());wind.setOwnerName(account("NoPointEnemy"));wind.setNoAi(true);place(h,wind,12,8);JungleSporeCarrier[] carrier={null};
        ready(h,fox,attacker,friendly,wind).thenExecute(()->{setAttackDamage(attacker,4);attacker.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.LIFESTEAL.get()).setBaseValue(.5);attacker.setHealth(attacker.getMaxHealth()-10);float hp=attacker.getHealth();require(h,cast(h,fox,pos(h,8,8)),SkillResult.SUCCESS,"real authorized carrier");carrier[0]=active(h,fox);countAttacks(carrier[0]);
            h.assertTrue(com.solegendary.reignofnether.util.MiscUtil.findClosestAttackableEntity(attacker,3,h.getLevel())!=carrier[0],"actual idle/attack-move target search does not select nearby active carrier");
            h.assertTrue(!nativeAutomaticCandidate(attacker,carrier[0],true)&&nativeAutomaticCandidate(attacker,fox,true),"actual native candidate predicate rejects carrier even with neutral aggression enabled while accepting a real hostile ordinary Unit");
            h.assertTrue(!carrier[0].hurt(carrier[0].damageSources().genericKill(),10000)&&!carrier[0].hurt(carrier[0].damageSources().fall(),10)&&!carrier[0].hurt(carrier[0].damageSources().magic(),10)&&!carrier[0].hurt(carrier[0].damageSources().playerAttack(player(h,attacker.getOwnerName())),10)&&!carrier[0].hurt(carrier[0].damageSources().mobAttack(attacker),10)&&!carrier[0].hurt(carrier[0].damageSources().mobAttack(friendly),10),"player/environment/magic/direct attacker/friendly injections all fail before native damage dispatch");
            h.assertTrue(!attacker.doHurtTarget(carrier[0]),"direct native doHurtTarget without primary goal/order cannot grant carrier authority");attacker.setUnitAttackTargetForced(carrier[0]);preparedGoal(h,attacker,carrier[0]).checkAndPerformAttackIgnoreDist(carrier[0]);
            close(h,carrier[0].getHealth(),10,"manual forced bit plus real goal still has no accepted command intent");close(h,attacker.getHealth(),hp,"unauthorized direct/goal calls grant no native lifesteal");h.assertTrue(PROBE.attacks.getOrDefault(carrier[0].getUUID(),0)==0,"unauthorized routes never even reach LivingAttack, not just zero final HP");
            var dash=new SkillRequest(UnitSkill.WIND_DASH,wind.getId(),wind.getUUID(),wind.rts().skills().revision(),carrier[0].getId(),carrier[0].getUUID());require(h,SkillService.request(player(h,wind.getOwnerName()),dash),SkillResult.INVALID_TARGET,"ordinary point-kill permission never admits Wind skill damage target");h.assertTrue(wind.rts().skills().cooldownTicks(UnitSkill.WIND_DASH)==0,"rejected skill did not spend cooldown");
            h.assertTrue(unitCommand(h,player(h,friendly.getOwnerName()),friendly,com.solegendary.reignofnether.unit.UnitAction.ATTACK,carrier[0],false),"friendly owner request reaches normal authenticated dispatcher");})
          .thenWaitUntil(()->h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(friendly.getId()),"actual friendly order processed"))
          .thenExecute(()->{h.assertTrue(friendly.getTargetGoal().getTarget()!=carrier[0],"accepted packet cannot create friendly carrier attack intent");preparedGoal(h,friendly,carrier[0]).checkAndPerformAttackIgnoreDist(carrier[0]);close(h,carrier[0].getHealth(),10,"friendly installed goal cannot damage carrier either");h.assertTrue(PROBE.attacks.getOrDefault(carrier[0].getUUID(),0)==0,"all unauthorized paths remain before native LivingAttack/on-hit");guard(h);pass("NO_ORDER_OR_RAW_FRIENDLY_PLAYER_SKILL_HIT_AUTHORITY");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1050)
    public static void realRoyalGuardWindupRewardsLegalDamageButEventRevokedAuthorityCannotMintHealthOrMana(GameTestHelper h){
        floor(h);var fox=fox(h,"BenefitOwner",4,8);var hero=com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_GUARD_UNIT.get().create(h.getLevel());hero.setOwnerName(account("BenefitAttacker"));hero.setNoAi(true);place(h,hero,10,8);player(h,hero.getOwnerName());JungleSporeCarrier[] carrier={null};int[] callbacks={0};
        ready(h,fox,hero).thenExecute(()->{setAttackDamage(hero,4);hero.setItemSlot(EquipmentSlot.MAINHAND,net.minecraft.world.item.ItemStack.EMPTY);hero.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.LIFESTEAL.get()).setBaseValue(.5);hero.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.MANA_ON_HIT.get()).setBaseValue(.5);hero.setMaxMana(100);require(h,cast(h,fox,pos(h,8,8)),SkillResult.SUCCESS,"real carrier for native benefit boundary");carrier[0]=active(h,fox);h.assertTrue(unitCommand(h,player(h,hero.getOwnerName()),hero,com.solegendary.reignofnether.unit.UnitAction.ATTACK,carrier[0],false),"genuine Hero explicit attack accepted");})
          .thenWaitUntil(()->directed(h,hero,carrier[0]))
          .thenExecute(()->{hero.setHealth(hero.getMaxHealth()-20);hero.setMana(10);float hp=hero.getHealth(),mana=hero.getMana();float nativeAmount=hero.getUnitAttackDamage();h.assertTrue(nativeAmount>0&&nativeAmount<10,"known nonlethal native hero baseline including original hero-level bonus");controlledStrike(h,hero,carrier[0]);close(h,10-carrier[0].getHealth(),nativeAmount,"actual installed windup goal dealt native baseline");close(h,hero.getHealth()-hp,nativeAmount*.5,"legal actual hit retains existing native lifesteal");close(h,hero.getMana()-mana,nativeAmount*.5,"legal actual hit retains existing native hero mana-on-hit");
            float beforeTarget=carrier[0].getHealth(),beforeHp=hero.getHealth(),beforeMana=hero.getMana();watchDamageFirst(carrier[0],event->{callbacks[0]++;String owner=hero.getOwnerName();hero.setOwnerName(fox.getOwnerName());hero.setOwnerName(owner);});controlledStrike(h,hero,carrier[0]);
            h.assertTrue(callbacks[0]==1,"actual high-priority LivingDamage callback revoked attacker's captured owner epoch before native benefit handler");close(h,carrier[0].getHealth(),beforeTarget,"revoked primary cannot damage target");close(h,hero.getHealth(),beforeHp,"same-tick revoked primary cannot grant native lifesteal");close(h,hero.getMana(),beforeMana,"same-tick revoked primary cannot mint native hero mana");
            h.assertTrue(unitCommand(h,player(h,hero.getOwnerName()),hero,com.solegendary.reignofnether.unit.UnitAction.ATTACK,carrier[0],false),"fresh actual order can re-authorize restored owner after ABA");})
          .thenWaitUntil(()->directed(h,hero,carrier[0]))
          .thenExecute(()->{float before=carrier[0].getHealth(),hp=hero.getHealth(),mana=hero.getMana();float amount=hero.getUnitAttackDamage();controlledStrike(h,hero,carrier[0]);close(h,before-carrier[0].getHealth(),Math.min(before,amount),"fresh actual order still performs legal native damage");close(h,hero.getHealth()-hp,amount*.5,"fresh legal native lifesteal still works after revoked frame");close(h,hero.getMana()-mana,amount*.5,"fresh legal native mana benefit still works after revoked frame");guard(h);pass("LEGAL_NATIVE_HERO_BENEFITS_VS_REVOKED_PRIMARY");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1080)
    public static void realStopSourceRevocationAndReplacementLeaseCannotReuseOldExplicitAttackIntent(GameTestHelper h){
        floor(h);var stopFox=fox(h,"RevokeCarrier",4,4);var sourceFox=fox(h,"RevokeCarrier",4,12);var oldFox=fox(h,"RevokeCarrier",14,4);var freshFox=fox(h,"RevokeCarrier",14,12);var stop=slime(h,"RevokeEnemy",10,4);var source=slime(h,"RevokeEnemy",10,12);var replace=slime(h,"RevokeEnemy",20,4);JungleSporeCarrier[] carriers=new JungleSporeCarrier[4];
        ready(h,stopFox,sourceFox,oldFox,freshFox,stop,source,replace).thenExecute(()->{for(var attacker:List.of(stop,source,replace))setAttackDamage(attacker,4);require(h,cast(h,stopFox,pos(h,8,4)),SkillResult.SUCCESS,"STOP target lease");require(h,cast(h,sourceFox,pos(h,8,12)),SkillResult.SUCCESS,"source-revoke target lease");require(h,cast(h,oldFox,pos(h,18,4)),SkillResult.SUCCESS,"old carrier lease");carriers[0]=active(h,stopFox);carriers[1]=active(h,sourceFox);carriers[2]=active(h,oldFox);for(int i=0;i<3;i++)h.assertTrue(unitCommand(h,player(h,"RevokeEnemy"),List.of(stop,source,replace).get(i),com.solegendary.reignofnether.unit.UnitAction.ATTACK,carriers[i],false),"real initial explicit order "+i);})
          .thenWaitUntil(()->{directed(h,stop,carriers[0]);directed(h,source,carriers[1]);directed(h,replace,carriers[2]);})
          .thenExecute(()->{h.assertTrue(unitCommand(h,player(h,"RevokeEnemy"),stop,com.solegendary.reignofnether.unit.UnitAction.STOP,null,false),"real STOP packet accepted");preparedGoal(h,stop,carriers[0]).checkAndPerformAttackIgnoreDist(carriers[0]);close(h,carriers[0].getHealth(),10,"queued immediate STOP already blocks old intent before asynchronous dispatcher cleanup");
            String owner=sourceFox.getOwnerName();sourceFox.setOwnerName(replace.getOwnerName());sourceFox.setOwnerName(owner);h.assertTrue(SporeLease.activeFor(sourceFox)==null&&carriers[1].isRemoved()&&cd(sourceFox)>0,"actual caster ABA retires old carrier without cooldown refund");h.assertTrue(!carriers[1].hurt(carriers[1].damageSources().mobAttack(source),4),"removed source lease cannot authorize a late old-target hit");
            carriers[2].remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);require(h,cast(h,freshFox,pos(h,18,4)),SkillResult.SUCCESS,"distinct fresh fox creates real new carrier at old carrier position");carriers[3]=active(h,freshFox);h.assertTrue(carriers[3]!=carriers[2]&&!carriers[3].getUUID().equals(carriers[2].getUUID()),"replacement is genuinely a different lease/object/UUID");preparedGoal(h,replace,carriers[3]).checkAndPerformAttackIgnoreDist(carriers[3]);close(h,carriers[3].getHealth(),10,"old intent cannot be borrowed even by an installed goal hitting same-position replacement");h.assertTrue(unitCommand(h,player(h,"RevokeEnemy"),replace,com.solegendary.reignofnether.unit.UnitAction.ATTACK,carriers[3],false),"fresh replacement needs its own actual authenticated order");})
          .thenWaitUntil(()->{h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(stop.getId())&&stop.getTargetGoal().getTarget()==null,"real STOP completed without reacquiring old carrier");directed(h,replace,carriers[3]);})
          .thenExecute(()->{controlledStrike(h,replace,carriers[3]);close(h,carriers[3].getHealth(),6,"new real explicit replacement order succeeds normally");close(h,carriers[0].getHealth(),10,"stopped carrier remains untouched");guard(h);pass("STOP_SOURCE_REVOCATION_AND_REPLACEMENT_INTENT");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1120)
    public static void realRabbitSoundCallbackCannotBorrowSporeOrBonePrimaryAndExceptionRestoresOuterScope(GameTestHelper h){
        floor(h);var fox=fox(h,"RabbitCarrier",4,5);var rabbit=com.solegendary.reignofnether.registrars.EntityRegistrar.KILLER_RABBIT_UNIT.get().create(h.getLevel());rabbit.setOwnerName(account("RabbitAttacker"));rabbit.setNoAi(true);place(h,rabbit,10,5);player(h,rabbit.getOwnerName());setAttackDamage(rabbit,4);
        var marker=DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel());marker.setOwnerName(rabbit.getOwnerName());marker.setNoAi(true);place(h,marker,4,15);var victim=com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());victim.setOwnerName(fox.getOwnerName());victim.setNoAi(true);victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);victim.setHealth(1000);place(h,victim,10,15);JungleSporeCarrier[] carrier={null};int[] sounds={0,0,0};
        ready(h,fox,rabbit,marker,victim).thenExecute(()->{require(h,cast(h,fox,pos(h,8,5)),SkillResult.SUCCESS,"real Rabbit target lease");carrier[0]=active(h,fox);h.assertTrue(unitCommand(h,player(h,rabbit.getOwnerName()),rabbit,com.solegendary.reignofnether.unit.UnitAction.ATTACK,carrier[0],false),"true Rabbit point order accepted");})
          .thenWaitUntil(()->directed(h,rabbit,carrier[0]))
          .thenExecute(()->{boolean[] inner={true,true};float[] innerHp={0};countAttacks(carrier[0]);watchRabbitSound(rabbit,event->{sounds[0]++;inner[0]=carrier[0].hurt(carrier[0].damageSources().mobAttack(rabbit),4);inner[1]=rabbit.doHurtTarget(carrier[0]);innerHp[0]=carrier[0].getHealth();});controlledStrike(h,rabbit,carrier[0]);h.assertTrue(sounds[0]==1&&!inner[0]&&!inner[1]&&innerHp[0]==10&&PROBE.attacks.getOrDefault(carrier[0].getUUID(),0)==1,"actual native pre-hurt sound callback injections are rejected before native events while one outer primary survives");close(h,carrier[0].getHealth(),6,"original Rabbit primary still deals native four after suspended callbacks");
            RuntimeException sentinel=new RuntimeException("expected GameTest-only Rabbit sound sentinel");watchRabbitSound(rabbit,event->{sounds[1]++;throw sentinel;});var goal=preparedGoal(h,rabbit,carrier[0]);RuntimeException caught=null;try{goal.checkAndPerformAttackIgnoreDist(carrier[0]);}catch(RuntimeException failure){caught=failure;}boolean expected=false;for(Throwable cause=caught;cause!=null;cause=cause.getCause())if(cause==sentinel)expected=true;h.assertTrue(sounds[1]==1&&expected,"only deliberate real sound callback sentinel is caught outside actual goal; unrelated failures never count");close(h,carrier[0].getHealth(),6,"exception before primary hurt grants no damage");h.assertTrue(!carrier[0].hurt(carrier[0].damageSources().mobAttack(rabbit),4),"exception did not leak an ordinary frame into later raw hurt");controlledStrike(h,rabbit,carrier[0]);close(h,carrier[0].getHealth(),2,"fresh actual ordinary primary after exception proves finally restored scope");
            h.assertTrue(unitCommand(h,player(h,rabbit.getOwnerName()),rabbit,com.solegendary.reignofnether.unit.UnitAction.ATTACK,victim,false),"same real Rabbit receives normal unit target command for Bone regression");})
          .thenWaitUntil(()->directed(h,rabbit,victim))
          .thenExecute(()->{require(h,SkillService.request(player(h,marker.getOwnerName()),new SkillRequest(UnitSkill.COORDINATED_MARK,marker.getId(),marker.getUUID(),marker.rts().skills().revision(),victim.getId(),victim.getUUID())),SkillResult.SUCCESS,"real Bone mark before actual Rabbit goal hit");countAttacks(victim);boolean[] legalBase={false,false};watchRabbitSound(rabbit,event->{sounds[2]++;victim.invulnerableTime=0;legalBase[0]=victim.hurt(victim.damageSources().mobAttack(rabbit),4);victim.invulnerableTime=0;legalBase[1]=rabbit.doHurtTarget(victim);victim.invulnerableTime=0;});controlledStrike(h,rabbit,victim);
            h.assertTrue(sounds[2]==1&&legalBase[0]&&legalBase[1]&&PROBE.attacks.getOrDefault(victim.getUUID(),0)==3,"real callback inner base hits remain legal for ordinary Unit target, not cancelled by Spore gate");close(h,1000-victim.getHealth(),4+4+4*1.12,"only outer exact Rabbit primary receives Bone twelve percent; both callback hits retain legal base");h.assertTrue(CoordinatedMark.ticksRemaining(victim,marker.getOwnerName())>0,"callback suspension never clears otherwise live real mark");float before=victim.getHealth();controlledStrike(h,rabbit,victim);close(h,before-victim.getHealth(),4*1.12,"later clean actual Rabbit ordinary primary continues to benefit normally");guard(h);pass("REAL_RABBIT_SOUND_SPORE_AND_BONE_SCOPE_WITH_EXCEPTION");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1080)
    public static void realLightPaidFactoriesRequireExplicitLeaseAndTravelToCarrier(GameTestHelper h){paidSporeFlight(h,0);}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1080)
    public static void realShadowPaidFactoriesRequireExplicitLeaseAndTravelToCarrier(GameTestHelper h){paidSporeFlight(h,3);}
    private static void paidSporeFlight(GameTestHelper h,int first){
        floor(h);var sources=new ArrayList<Mob>();var foxes=new ArrayList<GoldenFoxUnit>();var all=new ArrayList<Entity>();JungleSporeCarrier[] carriers=new JungleSporeCarrier[3];double[] travel=new double[3];Vec3[] starts=new Vec3[3];
        for(int i=0;i<3;i++){int z=5+i*6;var source=rangedSource(h,"PaidShoot"+first,first+i,4,z);var caster=fox(h,"PaidLease"+first,16,z);sources.add(source);foxes.add(caster);all.add(source);all.add(caster);}
        var seq=ready(h,all.toArray(Entity[]::new)).thenExecute(()->{for(int i=0;i<3;i++){require(h,cast(h,foxes.get(i),pos(h,10,5+i*6)),SkillResult.SUCCESS,"real paid projectile target lease "+(first+i));carriers[i]=active(h,foxes.get(i));requestAttack(h,sources.get(i),carriers[i]);}})
          .thenWaitUntil(()->{for(int i=0;i<3;i++)directed(h,sources.get(i),carriers[i]);})
          .thenExecute(()->{for(int i=0;i<3;i++){var source=sources.get(i);watchProjectiles(source);starts[i]=source.position();ordinaryFactory(source,carriers[i]);h.assertTrue(carriers[i].getHealth()==10,"real factory launch never applies instant remote damage");var shot=singleShot(h,source);h.assertTrue(shot.getClass()==paidShotType(first+i)&&OrdinarySkillHit.mayHitSpore(shot,carriers[i]),"exact real paid factory publication captured the accepted explicit lease; kind="+(first+i));}});
        for(int t=0;t<45;t++)seq.thenIdle(1).thenExecute(()->{for(int i=0;i<3;i++)for(var shot:shots(sources.get(i)))travel[i]=Math.max(travel[i],shot.position().distanceTo(starts[i]));});
        seq.thenExecute(()->{for(int i=0;i<3;i++){var shot=singleShot(h,sources.get(i));h.assertTrue(shot.getClass()==paidShotType(first+i)&&travel[i]>1&&shot.isRemoved(),"actual factory projectile physically travelled and consumed once; kind="+(first+i)+" travel="+travel[i]);close(h,carriers[i].getHealth(),6,"real exact paid class damage4 reaches selected ten-HP carrier, kind="+(first+i));h.assertTrue(!carriers[i].isOnFire()&&SporeLease.active(carriers[i]),"no fake expiration or native-fire substitute for paid primary hit");System.out.println("FOX_SPORE_PAID_FLIGHT kind="+(first+i)+" projectile="+shot.getClass().getSimpleName()+" travel="+travel[i]+" hp="+carriers[i].getHealth());}guard(h);pass("PAID_ACTUAL_FACTORY_EXPLICIT_LEASE_GROUP_"+first);}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1120)
    public static void nativeSkeletonStrayMilitiaBowAndHeadhunterTridentConsumeOnlyTheirActualExplicitPrimary(GameTestHelper h){
        floor(h);var sources=new ArrayList<Mob>();var foxes=new ArrayList<GoldenFoxUnit>();var all=new ArrayList<Entity>();JungleSporeCarrier[] carriers=new JungleSporeCarrier[4];double[] travel=new double[4];Vec3[] starts=new Vec3[4];
        for(int i=0;i<4;i++){int z=4+i*5;var source=rangedSource(h,"NativeShoot",6+i,4,z);var caster=fox(h,"NativeLease",16,z);sources.add(source);foxes.add(caster);all.add(source);all.add(caster);}
        var seq=ready(h,all.toArray(Entity[]::new)).thenExecute(()->{for(int i=0;i<4;i++){require(h,cast(h,foxes.get(i),pos(h,10,4+i*5)),SkillResult.SUCCESS,"real native projectile target lease "+i);carriers[i]=active(h,foxes.get(i));requestAttack(h,sources.get(i),carriers[i]);}})
          .thenWaitUntil(()->{for(int i=0;i<4;i++)directed(h,sources.get(i),carriers[i]);})
          .thenExecute(()->{for(int i=0;i<4;i++){var source=sources.get(i);watchProjectiles(source);starts[i]=source.position();ordinaryFactory(source,carriers[i]);var shot=singleShot(h,source);h.assertTrue(carriers[i].getHealth()==10&&OrdinarySkillHit.mayHitSpore(shot,carriers[i]),"actual native factory binds explicit selected lease before flight, kind="+i);h.assertTrue(i==3?shot instanceof net.minecraft.world.entity.projectile.ThrownTrident:shot instanceof net.minecraft.world.entity.projectile.AbstractArrow,"real native arrow/trident class");System.out.println("FOX_SPORE_NATIVE_LAUNCH kind="+i+" source="+source.position()+" target="+carriers[i].position()+" eye="+carriers[i].getEyeHeight()+" shot="+shot.position()+" velocity="+shot.getDeltaMovement());}});
        for(int t=0;t<45;t++)seq.thenIdle(1).thenExecute(()->{for(int i=0;i<4;i++)for(var shot:shots(sources.get(i)))travel[i]=Math.max(travel[i],shot.position().distanceTo(starts[i]));});
        seq.thenExecute(()->{for(int i=0;i<4;i++){var shot=singleShot(h,sources.get(i));System.out.println("FOX_SPORE_NATIVE_FLIGHT kind="+i+" projectile="+shot.getClass().getSimpleName()+" pos="+shot.position()+" travel="+travel[i]+" hp="+carriers[i].getHealth()+" eye="+carriers[i].getEyeHeight());h.assertTrue(travel[i]>2,"genuine native projectile physically travelled, not a direct injected hurt");close(h,carriers[i].getHealth(),6,"original actual native factory primary damage4, kind="+i);h.assertTrue(SporeLease.active(carriers[i]),"selected carrier remains a real live lease after nonlethal physical hit");}guard(h);pass("NATIVE_ARROW_THREE_CLASSES_AND_TRIDENT_ACTUAL_FACTORY_FLIGHT");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1140)
    public static void completedNativeFangFactoryDamagesOnlyChosenSporeLeaseNotNearbyCarriers(GameTestHelper h){
        floor(h);var line=(com.solegendary.reignofnether.unit.units.villagers.EvokerUnit)rangedSource(h,"FangShoot",10,4,5);var circle=(com.solegendary.reignofnether.unit.units.villagers.EvokerUnit)rangedSource(h,"FangShoot",10,4,16);var foxes=List.of(fox(h,"FangLease",16,4),fox(h,"FangLease",16,7),fox(h,"FangLease",16,15),fox(h,"FangLease",16,18));JungleSporeCarrier[] carriers=new JungleSporeCarrier[4];var grounds=List.of(pos(h,10,5),pos(h,11,5),pos(h,10,16),pos(h,11,17));
        ready(h,line,circle,foxes.get(0),foxes.get(1),foxes.get(2),foxes.get(3)).thenExecute(()->{for(int i=0;i<4;i++){require(h,cast(h,foxes.get(i),grounds.get(i)),SkillResult.SUCCESS,"real selected/adjacent fang lease "+i);carriers[i]=active(h,foxes.get(i));countAttacks(carriers[i]);}Vec3 p=carriers[0].position().add(-1.25,0,0);line.moveTo(p.x,p.y,p.z);p=carriers[2].position().add(-1.5,0,0);circle.moveTo(p.x,p.y,p.z);requestAttack(h,line,carriers[0]);requestAttack(h,circle,carriers[2]);})
          .thenWaitUntil(()->{directed(h,line,carriers[0]);directed(h,circle,carriers[2]);})
          .thenExecute(()->{watchNativeFangs(line);watchNativeFangs(circle);line.isUsingLineFangs=true;circle.isUsingLineFangs=false;line.performUnitRangedAttack(carriers[0],1);circle.performUnitRangedAttack(carriers[2],1);h.assertTrue(line.getCastFangsGoal().isCasting()&&circle.getCastFangsGoal().isCasting()&&carriers[0].getHealth()==10&&carriers[2].getHealth()==10,"real ordinary ranged attack begins original twenty-tick channel, not direct private fang creation");})
          .thenIdle(50).thenExecute(()->{close(h,carriers[0].getHealth(),4,"native selected line primary deals six actual HP");close(h,carriers[2].getHealth(),0,"native selected circle primary really kills ten-HP selected lease with native twelve damage");for(int i:new int[]{1,3}){close(h,carriers[i].getHealth(),10,"different adjacent carrier cannot borrow explicit selected lease, index="+i);h.assertTrue(PROBE.attacks.getOrDefault(carriers[i].getUUID(),0)==0,"unselected fang candidate is rejected before LivingAttack, index="+i);}for(int i=0;i<2;i++){var e=i==0?line:circle;var fangs=RANGED.fangs.get(e.getUUID());var neighbor=carriers[i*2+1];h.assertTrue(fangs!=null&&!fangs.isEmpty()&&fangs.stream().anyMatch(f->f.getBoundingBox().inflate(.2,0,.2).intersects(neighbor.getBoundingBox())),"real emitted fang geometry includes the separate adjacent candidate, not an out-of-area negative");}h.assertTrue(PROBE.attacks.getOrDefault(carriers[0].getUUID(),0)>0&&PROBE.attacks.getOrDefault(carriers[2].getUUID(),0)>0,"both real selected fang routes entered native attack dispatch");
            // A public raw fang helper is deliberately not an ordinary completed cast origin.
            line.createEvokerFangsLine(carriers[1]);h.assertTrue(!carriers[1].hurt(carriers[1].damageSources().magic(),6),"raw magic cannot substitute for explicit ordinary fang provenance");})
          .thenIdle(35).thenExecute(()->{close(h,carriers[1].getHealth(),10,"raw public fang creation has no accepted ordinary channel origin even from same caster");close(h,carriers[3].getHealth(),10,"unselected circle neighbor remains untouched");guard(h);pass("REAL_COMPLETED_LINE_CIRCLE_FANG_EXACT_SELECTED_LEASE_ONLY");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1190)
    public static void publishedProjectileStopKeepsFrozenLeaseButSourceOrProjectileOwnerAbaRevokesIt(GameTestHelper h){
        floor(h);var sources=new ArrayList<Mob>();var foxes=new ArrayList<GoldenFoxUnit>();var all=new ArrayList<Entity>();JungleSporeCarrier[] carriers=new JungleSporeCarrier[3];net.minecraft.world.entity.projectile.Projectile[] old=new net.minecraft.world.entity.projectile.Projectile[3];Vec3[] velocities=new Vec3[3];
        for(int i=0;i<3;i++){int z=5+6*i;var source=rangedSource(h,"AirShoot",6,4,z);var caster=fox(h,"AirLease",16,z);sources.add(source);foxes.add(caster);all.add(source);all.add(caster);}
        ready(h,all.toArray(Entity[]::new)).thenExecute(()->{for(int i=0;i<3;i++){require(h,cast(h,foxes.get(i),pos(h,10,5+6*i)),SkillResult.SUCCESS,"real frozen-flight target "+i);carriers[i]=active(h,foxes.get(i));requestAttack(h,sources.get(i),carriers[i]);}})
          .thenWaitUntil(()->{for(int i=0;i<3;i++)directed(h,sources.get(i),carriers[i]);})
          .thenExecute(()->{for(int i=0;i<3;i++){var source=sources.get(i);watchProjectiles(source);ordinaryFactory(source,carriers[i]);old[i]=singleShot(h,source);h.assertTrue(h.getLevel().getEntity(old[i].getId())==old[i]&&OrdinarySkillHit.mayHitSpore(old[i],carriers[i]),"exact actual factory projectile was published and authorized before mutation");velocities[i]=old[i].getDeltaMovement();old[i].setNoGravity(true);old[i].setDeltaMovement(Vec3.ZERO);}h.assertTrue(unitCommand(h,player(h,"AirShoot"),sources.get(0),com.solegendary.reignofnether.unit.UnitAction.STOP,null,false),"real STOP accepted only after original shot publication");String owner=((Unit)sources.get(1)).getOwnerName();((Unit)sources.get(1)).setOwnerName(foxes.get(1).getOwnerName());((Unit)sources.get(1)).setOwnerName(owner);old[2].setOwner(foxes.get(2));old[2].setOwner(sources.get(2));})
          .thenWaitUntil(()->h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(sources.get(0).getId())&&((Unit)sources.get(0)).getTargetGoal().getTarget()==null,"actual STOP completed before same original shot resumes flight"))
          .thenExecute(()->{h.assertTrue(OrdinarySkillHit.mayHitSpore(old[0],carriers[0])&&!OrdinarySkillHit.mayHitSpore(old[1],carriers[1])&&!OrdinarySkillHit.mayHitSpore(old[2],carriers[2]),"read-only captured publication intent survives STOP but never source or projectile owner ABA");for(int i=0;i<3;i++){old[i].setNoGravity(false);old[i].setDeltaMovement(velocities[i]);}})
          .thenIdle(45).thenExecute(()->{close(h,carriers[0].getHealth(),6,"same published pre-STOP actual arrow retains frozen selected lease permission");for(int i=1;i<3;i++){close(h,carriers[i].getHealth(),10,"old actual arrow cannot recover after ABA, lane="+i);requestAttack(h,sources.get(i),carriers[i]);}})
          .thenWaitUntil(()->{for(int i=1;i<3;i++)directed(h,sources.get(i),carriers[i]);})
          .thenExecute(()->{for(int i=1;i<3;i++){watchProjectiles(sources.get(i));ordinaryFactory(sources.get(i),carriers[i]);h.assertTrue(OrdinarySkillHit.mayHitSpore(singleShot(h,sources.get(i)),carriers[i]),"fresh real command and factory grant new shot, never resurrect old one");}})
          .thenIdle(45).thenExecute(()->{for(int i=1;i<3;i++)close(h,carriers[i].getHealth(),6,"fresh actual post-ABA arrow damages normally, lane="+i);guard(h);pass("PUBLISHED_SHOT_STOP_VS_SOURCE_PROJECTILE_ABA_WITH_FRESH_POSITIVE");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1200)
    public static void constructorAndJoinCallbacksCannotGrantSporePermissionToNewOrNestedProjectile(GameTestHelper h){
        floor(h);var sources=new ArrayList<Mob>();var foxes=new ArrayList<GoldenFoxUnit>();var all=new ArrayList<Entity>();JungleSporeCarrier[] carriers=new JungleSporeCarrier[4];int[] callbacks={0,0};
        for(int i=0;i<4;i++){int z=4+i*5;var source=rangedSource(h,"BirthShot",i==3?0:6,4,z);var caster=fox(h,"BirthLease",16,z);sources.add(source);foxes.add(caster);all.add(source);all.add(caster);}
        ready(h,all.toArray(Entity[]::new)).thenExecute(()->{for(int i=0;i<4;i++){require(h,cast(h,foxes.get(i),pos(h,10,4+i*5)),SkillResult.SUCCESS,"real pending/publication negative target "+i);carriers[i]=active(h,foxes.get(i));requestAttack(h,sources.get(i),carriers[i]);}})
          .thenWaitUntil(()->{for(int i=0;i<4;i++)directed(h,sources.get(i),carriers[i]);})
          .thenExecute(()->{for(var source:sources)watchProjectiles(source);RANGED.constructing=event->{callbacks[0]++;String owner=((Unit)sources.get(0)).getOwnerName();((Unit)sources.get(0)).setOwnerName(foxes.get(0).getOwnerName());((Unit)sources.get(0)).setOwnerName(owner);};ordinaryFactory(sources.get(0),carriers[0]);
            RANGED.joins.put(sources.get(1).getUUID(),event->{callbacks[1]++;h.assertTrue(unitCommand(h,player(h,"BirthShot"),sources.get(1),com.solegendary.reignofnether.unit.UnitAction.STOP,null,false),"real Join callback queues STOP before original projectile is fully published");});ordinaryFactory(sources.get(1),carriers[1]);
            var raw=new net.minecraft.world.entity.projectile.Arrow(h.getLevel(),sources.get(2));raw.setBaseDamage(2);raw.setCritArrow(false);raw.setKnockback(0);Vec3 delta=carriers[2].getBoundingBox().getCenter().subtract(raw.position());raw.shoot(delta.x,delta.y+delta.horizontalDistance()*.2,delta.z,1.6f,0);h.assertTrue(h.getLevel().addFreshEntity(raw),"hand-created owned arrow really published, never stamped by a test helper");
            var bolt=new com.dreamseekers.bonewing.BoneWingBolt(DreamseekersMod.BONE_WING_BOLT.get(),h.getLevel());bolt.arm((BoneWingUnit)sources.get(3),carriers[3]);h.assertTrue(h.getLevel().addFreshEntity(bolt),"public arm plus real publication cannot forge ordinary factory origin");
            h.assertTrue(callbacks[0]==1&&callbacks[1]==1,"real constructor and real Join callbacks both executed once");for(int i=0;i<4;i++)h.assertTrue(!OrdinarySkillHit.mayHitSpore(singleShot(h,sources.get(i)),carriers[i]),"construction/order mutation or handmade source has no point-kill grant, lane="+i);})
          .thenIdle(45).thenExecute(()->{for(int i=0;i<4;i++){close(h,carriers[i].getHealth(),10,"old/forged projectile deals no carrier damage, lane="+i);requestAttack(h,sources.get(i),carriers[i]);}})
          .thenWaitUntil(()->{for(int i=0;i<4;i++)directed(h,sources.get(i),carriers[i]);})
          .thenExecute(()->{for(int i=0;i<4;i++){watchProjectiles(sources.get(i));ordinaryFactory(sources.get(i),carriers[i]);h.assertTrue(OrdinarySkillHit.mayHitSpore(singleShot(h,sources.get(i)),carriers[i]),"new accepted order and genuine factory produce a fresh valid shot, lane="+i);}})
          .thenIdle(45).thenExecute(()->{for(int i=0;i<4;i++)close(h,carriers[i].getHealth(),6,"each genuine fresh factory has actual HP positive control, lane="+i);guard(h);pass("PROJECTILE_PRECONSTRUCTOR_POSTJOIN_AND_HANDMADE_PROVENANCE");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1260)
    public static void pillagerSoundRetargetAndMountedSecondaryNeverBorrowActualPrimaryLease(GameTestHelper h){
        floor(h);var retarget=(com.solegendary.reignofnether.unit.units.villagers.PillagerUnit)rangedSource(h,"CrossbowShoot",11,4,5);var nested=(com.solegendary.reignofnether.unit.units.villagers.PillagerUnit)rangedSource(h,"CrossbowShoot",11,4,12);var mounted=(com.solegendary.reignofnether.unit.units.villagers.PillagerUnit)rangedSource(h,"CrossbowShoot",11,4,19);var foxes=List.of(fox(h,"RetargetLease",16,3),fox(h,"RetargetLease",16,7),fox(h,"NestedLease",16,12),fox(h,"MountedLease",16,18),fox(h,"MountedLease",16,21));var grounds=List.of(pos(h,10,4),pos(h,10,6),pos(h,10,12),pos(h,10,19),pos(h,11,19));JungleSporeCarrier[] carriers=new JungleSporeCarrier[5];int[] sounds={0,0};long[] castAt={0};Set<net.minecraft.world.entity.projectile.Projectile> observed=Collections.newSetFromMap(new IdentityHashMap<>());net.minecraft.world.damagesource.DamageSource[] impacts=new net.minecraft.world.damagesource.DamageSource[2];
        var horse=EntityType.HORSE.create(h.getLevel());horse.setNoAi(true);place(h,horse,4,19);var witness=com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());witness.setOwnerName(account("MountedLease"));witness.setNoAi(true);witness.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);witness.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);witness.setHealth(1000);place(h,witness,10,20);
        var seq=ready(h,retarget,nested,mounted,horse,witness,foxes.get(0),foxes.get(1),foxes.get(2),foxes.get(3),foxes.get(4)).thenExecute(()->{castAt[0]=h.getLevel().getGameTime();for(int i=0;i<5;i++){require(h,cast(h,foxes.get(i),grounds.get(i)),SkillResult.SUCCESS,"real Crossbow selected or secondary target lease "+i);carriers[i]=active(h,foxes.get(i));countAttacks(carriers[i]);}h.assertTrue(mounted.startRiding(horse,true),"actual native horse mount enables original mounted explosion");h.assertTrue(!carriers[3].getBoundingBox().intersects(carriers[4].getBoundingBox())&&carriers[3].getEyePosition().distanceTo(carriers[4].position())<2&&carriers[3].getEyePosition().distanceTo(witness.position())<2,"secondary carrier and ordinary witness are truly within native explosion diameter, without overlapping primary");requestAttack(h,retarget,carriers[0]);requestAttack(h,nested,carriers[2]);requestAttack(h,mounted,carriers[3]);})
          .thenWaitUntil(()->{directed(h,retarget,carriers[0]);directed(h,nested,carriers[2]);directed(h,mounted,carriers[3]);})
          .thenExecute(()->{for(var source:List.of(retarget,nested,mounted))watchProjectiles(source);watchDamageFirst(carriers[3],event->impacts[0]=event.getSource());watchDamageFirst(witness,event->impacts[1]=event.getSource());
            RANGED.sounds.put(retarget,event->{sounds[0]++;h.assertTrue(shots(retarget).isEmpty(),"real aim/sound occurs before outer projectile publication");retarget.getTargetGoal().setTarget(carriers[1]);try{net.minecraft.world.item.CrossbowItem.performShooting(h.getLevel(),retarget,net.minecraft.world.InteractionHand.MAIN_HAND,retarget.getMainHandItem(),1.6f,0);}finally{retarget.getTargetGoal().setTarget(carriers[0]);}});
            RANGED.sounds.put(nested,event->{sounds[1]++;net.minecraft.world.item.CrossbowItem.performShooting(h.getLevel(),nested,net.minecraft.world.InteractionHand.MAIN_HAND,nested.getMainHandItem(),1.6f,0);});for(var source:List.of(retarget,nested,mounted)){source.setHoldPosition(true);source.setNoAi(false);}});
        // Observe actual first native goal shots; pause each source immediately after publication, not by changing cached attack cooldowns.
        for(int t=0;t<110;t++)seq.thenIdle(1).thenExecute(()->{for(var source:List.of(retarget,nested,mounted)){for(var shot:shots(source))if(observed.add(shot))crossbowShotDiagnostic(h,"FIRST_POST_PUBLISH_TICK",castAt[0],source,shot,carriers);if(!shots(source).isEmpty())source.setNoAi(true);}});
        seq.thenExecute(()->{for(var source:List.of(retarget,nested,mounted))for(var shot:shots(source))crossbowShotDiagnostic(h,"END_110",castAt[0],source,shot,carriers);System.out.println("FOX_SPORE_CROSSBOW_DIAGNOSTIC sounds="+Arrays.toString(sounds)+" hp="+Arrays.stream(carriers).map(c->Float.toString(c.getHealth())).toList()+" arrows="+List.of(shots(retarget).size(),shots(nested).size(),shots(mounted).size())+" witnessHp="+witness.getHealth()+" mounted="+mounted.isPassenger()+" secondarySameDS="+(impacts[0]!=null&&impacts[0]==impacts[1]));h.assertTrue(sounds[0]==1&&sounds[1]==1&&shots(retarget).size()==2&&shots(nested).size()==2&&shots(mounted).size()==1,"actual sound callbacks emitted real outer/nested arrows and one mounted ordinary arrow");close(h,carriers[0].getHealth(),10,"manual A-to-B-to-A target change permanently revoked outer original order");close(h,carriers[1].getHealth(),10,"nested generic crossbow factory cannot steal a different selected lease");close(h,carriers[2].getHealth(),6,"without retargeting only genuine outer arrow damages once; nested raw same-target arrow rejected");close(h,carriers[3].getHealth(),5,"real mounted ordinary primary keeps native +1 damage, not secondary blast damage");close(h,carriers[4].getHealth(),10,"native mounted secondary explosion can never borrow actual primary lease");h.assertTrue(PROBE.attacks.getOrDefault(carriers[4].getUUID(),0)==0&&witness.getHealth()<1000&&impacts[0]!=null&&impacts[0]==impacts[1]&&mounted.isPassenger(),"real mounted same-DamageSource explosion damaged ordinary witness while rejecting carrier before native attack effects");
            // The retarget lane needs a genuinely new accepted order, not merely restoring the target field.
            requestAttack(h,retarget,carriers[0]);})
          .thenWaitUntil(()->directed(h,retarget,carriers[0]))
          .thenExecute(()->{watchProjectiles(retarget);retarget.setNoAi(false);});
        for(int t=0;t<80;t++)seq.thenIdle(1).thenExecute(()->{for(var shot:shots(retarget))if(observed.add(shot))crossbowShotDiagnostic(h,"FRESH_FIRST_POST_PUBLISH_TICK",castAt[0],retarget,shot,carriers);if(!shots(retarget).isEmpty())retarget.setNoAi(true);});
        seq.thenExecute(()->{h.assertTrue(shots(retarget).size()==1&&SporeLease.active(carriers[0]),"fresh real goal emitted one new ordinary arrow before original lease expiration");close(h,carriers[0].getHealth(),6,"fresh accepted order plus genuine native crossbow factory restores lawful damage");guard(h);pass("ACTUAL_CROSSBOW_SOUND_RETARGET_NESTED_AND_MOUNTED_SECONDARY");}).thenSucceed();
    }

    /** Read-only, once after real publication has returned (never from inside the Join grant window). */
    private static void crossbowShotDiagnostic(GameTestHelper h,String stage,long castAt,Mob source,net.minecraft.world.entity.projectile.Projectile shot,JungleSporeCarrier[] carriers){
        LivingEntity selected=((Unit)source).getTargetGoal().getTarget();System.out.println("FOX_SPORE_CROSSBOW_SHOT_DIAGNOSTIC stage="+stage+" testTick="+h.getTick()+" gameTime="+h.getLevel().getGameTime()+" elapsedSinceBirth="+(h.getLevel().getGameTime()-castAt)+" source="+source.getId()+":"+source.getUUID()+" sourceOwner="+((Unit)source).getOwnerName()+" sourcePos="+source.position()+" noAI="+source.isNoAi()+" selectedAtObservation="+(selected==null?"null":selected.getId()+":"+selected.getUUID()+":"+selected.position())+" forced="+((Unit)source).getTargetGoal().forced+" shot="+shot.getId()+":"+shot.getUUID()+" ownerExact="+(shot.getOwner()==source)+" shotPos="+shot.position()+" velocity="+shot.getDeltaMovement()+" shotTick="+shot.tickCount+" removed="+shot.isRemoved()+" removal="+shot.getRemovalReason()+" idPublished="+(h.getLevel().getEntity(shot.getId())==shot)+" uuidPublished="+(h.getLevel().getEntity(shot.getUUID())==shot)+" block="+h.getLevel().getBlockState(shot.blockPosition())+" below="+h.getLevel().getBlockState(shot.blockPosition().below())+" leaseCandidates="+Arrays.stream(carriers).map(c->c.getId()+":"+c.getUUID()+":pos="+c.position()+":box="+c.getBoundingBox()+":hp="+c.getHealth()+":mayHit="+OrdinarySkillHit.mayHitSpore(shot,c)).toList());
    }

    private static final RangedProbe RANGED=new RangedProbe();private static boolean rangedListening;
    private static void rangedListen(){if(!rangedListening){MinecraftForge.EVENT_BUS.register(RANGED);rangedListening=true;}}
    private static void cleanupRanged(){if(rangedListening){MinecraftForge.EVENT_BUS.unregister(RANGED);rangedListening=false;}for(var shot:RANGED.allShots)if(!shot.isRemoved())shot.discard();RANGED.allShots.clear();for(var list:RANGED.fangs.values())for(var fang:list)if(!fang.isRemoved())fang.discard();RANGED.projectiles.clear();RANGED.fangs.clear();RANGED.joins.clear();RANGED.sounds.clear();RANGED.constructing=null;}
    private static void watchNativeFangs(Mob source){rangedListen();RANGED.fangs.put(source.getUUID(),new ArrayList<>());}
    private static void watchProjectiles(Mob source){rangedListen();RANGED.projectiles.put(source.getUUID(),new ArrayList<>());}
    private static List<net.minecraft.world.entity.projectile.Projectile> shots(Mob source){return RANGED.projectiles.getOrDefault(source.getUUID(),List.of());}
    private static net.minecraft.world.entity.projectile.Projectile singleShot(GameTestHelper h,Mob source){var list=shots(source);h.assertTrue(list.size()==1,"exactly one actual published factory projectile for "+source.getClass().getSimpleName()+": "+list.size());return list.get(0);}
    private static void requestAttack(GameTestHelper h,Mob source,LivingEntity target){h.assertTrue(unitCommand(h,player(h,((Unit)source).getOwnerName()),source,com.solegendary.reignofnether.unit.UnitAction.ATTACK,target,false),"real source owner packet accepts explicit current target");}
    private static void ordinaryFactory(Mob source,LivingEntity target){((com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit)source).performUnitRangedAttack(target,1);}
    private static Mob rangedSource(GameTestHelper h,String owner,int kind,int x,int z){
        Mob source=switch(kind){case 0->DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel());case 1->DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get().create(h.getLevel());case 2->DreamseekersMod.ASH_BONE_WING_UNIT.get().create(h.getLevel());case 3->DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT.get().create(h.getLevel());case 4->DreamseekersMod.WEEPING_SQUEAL_GHOST_UNIT.get().create(h.getLevel());case 5->DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(h.getLevel());case 6->com.solegendary.reignofnether.registrars.EntityRegistrar.SKELETON_UNIT.get().create(h.getLevel());case 7->com.solegendary.reignofnether.registrars.EntityRegistrar.STRAY_UNIT.get().create(h.getLevel());case 8->com.solegendary.reignofnether.registrars.EntityRegistrar.MILITIA_UNIT.get().create(h.getLevel());case 9->com.solegendary.reignofnether.registrars.EntityRegistrar.HEADHUNTER_UNIT.get().create(h.getLevel());case 10->com.solegendary.reignofnether.registrars.EntityRegistrar.EVOKER_UNIT.get().create(h.getLevel());case 11->com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get().create(h.getLevel());default->throw new IllegalArgumentException("ranged source kind="+kind);};
        ((Unit)source).setOwnerName(account(owner));source.setNoAi(true);((Unit)source).setupEquipmentAndUpgradesServer();if(source instanceof com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit militia)militia.swapWeapons(true);setAttackDamage(source,4);var aggro=source.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.AGGRO_RANGE.get());if(aggro!=null)aggro.setBaseValue(0);source.getRandom().setSeed(17);place(h,source,x,z);player(h,owner);return source;
    }
    private static Class<?> paidShotType(int kind){return switch(kind){case 0->com.dreamseekers.bonewing.BoneWingBolt.class;case 1->com.dreamseekers.stone.StoneProjectile.class;case 2->com.dreamseekers.shadow.AshBoneWingBolt.class;case 3->com.dreamseekers.shadow.SquealWaveBolt.class;case 4->com.dreamseekers.shadow.WeepingSquealWaveBolt.class;case 5->com.dreamseekers.shadow.FriendlyGhostBolt.class;default->throw new IllegalArgumentException("paid projectile kind");};}
    private static final class RangedProbe{
        final List<net.minecraft.world.entity.projectile.Projectile> allShots=new ArrayList<>();final Map<UUID,List<net.minecraft.world.entity.projectile.Projectile>> projectiles=new HashMap<>();final Map<UUID,List<net.minecraft.world.entity.projectile.EvokerFangs>> fangs=new HashMap<>();final Map<UUID,Consumer<EntityJoinLevelEvent>> joins=new HashMap<>();final Map<Mob,Consumer<net.minecraftforge.event.PlayLevelSoundEvent>> sounds=new IdentityHashMap<>();Consumer<EntityEvent.EntityConstructing> constructing;
        @SubscribeEvent public void joined(EntityJoinLevelEvent event){if(event.getLevel().isClientSide())return;if(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile shot&&shot.getOwner()!=null){var list=projectiles.get(shot.getOwner().getUUID());if(list!=null){list.add(shot);allShots.add(shot);}var callback=joins.remove(shot.getOwner().getUUID());if(callback!=null)callback.accept(event);}else if(event.getEntity() instanceof net.minecraft.world.entity.projectile.EvokerFangs fang&&fang.getOwner()!=null){var list=fangs.get(fang.getOwner().getUUID());if(list!=null)list.add(fang);}}
        @SubscribeEvent public void constructing(EntityEvent.EntityConstructing event){if(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile&&constructing!=null){var callback=constructing;constructing=null;callback.accept(event);}}
        @SubscribeEvent public void sound(net.minecraftforge.event.PlayLevelSoundEvent event){if(event.getSound()==null||event.getSound().value()!=net.minecraft.sounds.SoundEvents.CROSSBOW_SHOOT)return;for(var source:List.copyOf(sounds.keySet())){if(source.level()!=event.getLevel())continue;boolean exact=event instanceof net.minecraftforge.event.PlayLevelSoundEvent.AtEntity e&&e.getEntity()==source||event instanceof net.minecraftforge.event.PlayLevelSoundEvent.AtPosition p&&p.getPosition().distanceToSqr(source.position())<.000001;if(exact){var callback=sounds.remove(source);if(callback!=null)callback.accept(event);}}}
    }

    /** Pure original candidate predicate with explicit neutral-aggro input; never mutates the global rule. */
    private static boolean nativeAutomaticCandidate(Mob source,LivingEntity target,boolean neutralAggro){
        try{var lookup=java.lang.invoke.MethodHandles.privateLookupIn(com.solegendary.reignofnether.util.MiscUtil.class,java.lang.invoke.MethodHandles.lookup());var method=lookup.findStatic(com.solegendary.reignofnether.util.MiscUtil.class,"isIdleOrMoveAttackable",java.lang.invoke.MethodType.methodType(boolean.class,Mob.class,LivingEntity.class,boolean.class));return (boolean)method.invokeExact(source,target,neutralAggro);}
        catch(Throwable failure){if(failure instanceof Error fatal)throw fatal;var assertion=new GameTestAssertException("exact native candidate predicate lookup/invocation failed: "+failure);assertion.initCause(failure);throw assertion;}
    }
    /** Exact existing network authority path, not a direct UnitActionItem or internal point-kill intent grant. */
    private static boolean unitCommand(GameTestHelper h,ServerPlayer sender,Mob source,com.solegendary.reignofnether.unit.UnitAction action,LivingEntity target,boolean shift){
        try{var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.setListener(sender.connection);var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);var context=ctor.newInstance(connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);h.assertTrue(context.getSender()==sender,"real Forge context carries exact authenticated test player");return new com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket(((Unit)source).getOwnerName(),action,target==null?-1:target.getId(),new int[]{source.getId()},target==null?source.blockPosition():target.blockPosition(),BlockPos.ZERO,shift).handle(()->context);}catch(ReflectiveOperationException failure){throw new RuntimeException("existing actual network context",failure);}
    }
    private static void directed(GameTestHelper h,Mob source,LivingEntity target){h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(source.getId())&&((Unit)source).getTargetGoal().forced&&((Unit)source).getTargetGoal().getTarget()==target,"actual authenticated ATTACK executed against exact current target identity: "+source.getId()+" -> "+target.getId());}
    private static void setAttackDamage(LivingEntity source,double amount){source.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);if(source.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)!=null)source.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(amount);var crit=source.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);}
    /** Controlled scheduling of the entity's already-installed native goal, never a forced-target or authority helper. */
    private static com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal preparedGoal(GameTestHelper h,Mob source,LivingEntity target){
        var attacker=(com.solegendary.reignofnether.unit.interfaces.AttackerUnit)source;h.assertTrue(attacker.getAttackGoal() instanceof com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal,"real source owns installed melee goal");var goal=(com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal)attacker.getAttackGoal();source.setNoAi(true);double gap=source.getBbWidth()/2.0+target.getBbWidth()/2.0+.1;var p=target.position().add(-gap,0,0);source.moveTo(p.x,p.y,p.z);h.assertTrue(!source.getBoundingBox().intersects(target.getBoundingBox())&&source.hasLineOfSight(target),"actual non-overlapping visible melee stance");for(int i=0;i<(int)Math.ceil(attacker.getAttackCooldown())+2;i++)goal.tickAttackCooldown();target.invulnerableTime=0;return goal;
    }
    private static void controlledStrike(GameTestHelper h,Mob source,LivingEntity target){var goal=preparedGoal(h,source,target);if(goal instanceof com.solegendary.reignofnether.unit.goals.MeleeWindupAttackUnitGoal windup){windup.resetWindup();int ticks=((com.solegendary.reignofnether.unit.interfaces.KeyframeAnimated)source).getAttackWindupTicks();for(int i=0;i<ticks+2;i++)windup.checkAndPerformAttackWithWindup();}else goal.checkAndPerformAttackIgnoreDist(target);}
    private static void countAttacks(LivingEntity target){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}PROBE.attacks.put(target.getUUID(),0);}
    private static void watchDamageFirst(LivingEntity target,Consumer<net.minecraftforge.event.entity.living.LivingDamageEvent> callback){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}if(PROBE.firstDamage.put(target.getUUID(),callback)!=null)throw new IllegalStateException("duplicate first damage callback");}
    private static void watchRabbitSound(Mob source,Consumer<net.minecraftforge.event.PlayLevelSoundEvent> callback){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}if(PROBE.rabbitSounds.put(source,callback)!=null)throw new IllegalStateException("duplicate rabbit sound callback");}

    private static SkillGroundRequest request(GoldenFoxUnit source,BlockPos position){return new SkillGroundRequest(UnitSkill.FOX_DREAM_SEED,source.getId(),source.getUUID(),source.rts().skills().revision(),position);}
    private static SkillRequest blessing(GoldenFoxUnit source){return new SkillRequest(UnitSkill.FOX_BLESSING,source.getId(),source.getUUID(),source.rts().skills().revision(),source.getId(),source.getUUID());}
    private static SkillResult cast(GameTestHelper h,GoldenFoxUnit source,BlockPos position){return SkillService.requestGround(playerFor(h,source),request(source,position));}
    private static int cd(GoldenFoxUnit source){return source.rts().skills().cooldownTicks(UnitSkill.FOX_DREAM_SEED);}
    private static JungleSporeCarrier active(GameTestHelper h,GoldenFoxUnit source){var e=SporeLease.activeFor(source);h.assertTrue(e!=null&&SporeLease.active(e)&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"actual committed carrier published under exact identity");if(!FIXTURES.contains(e))FIXTURES.add(e);return e;}
    private static void require(GameTestHelper h,SkillResult actual,SkillResult expected,String why){h.assertTrue(actual==expected,why+": expected="+expected+" actual="+actual);}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.0005,why+": expected="+expected+" actual="+actual);}
    private static void pass(String name){System.out.println("DREAMSEEKERS_FOX_SPORE_SMOKE_PASS "+name);}
    private static String account(String suffix){String name=suffix.startsWith(PREFIX)?suffix:PREFIX+suffix;if(ACCOUNTS.add(name)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));}return name;}
    private static GoldenFoxUnit fox(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());e.setOwnerName(account(suffix));e.setNoAi(true);place(h,e,x,z);player(h,suffix);return e;}
    private static PinkSlimeUnit slime(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());e.setOwnerName(account(suffix));e.setNoAi(true);place(h,e,x,z);player(h,suffix);return e;}
    private static FakePlayer playerFor(GameTestHelper h,GoldenFoxUnit source){return player(h,source.getOwnerName());}
    private static FakePlayer player(GameTestHelper h,String suffix){String owner=account(suffix);return PLAYERS.computeIfAbsent(owner,key->{var e=new FakePlayer(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));e.connection=new RecordingHandler(e);BlockPos p=pos(h,2,21);e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.getLevel().addNewPlayer(e);PlayerServerEvents.players.add(e);return e;});}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=pos(h,x,z);e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"fixture added through actual world");}
    private static BlockPos pos(GameTestHelper h,int x,int z){return h.absolutePos(new BlockPos(x,1,z));}
    private static GameTestSequence ready(GameTestHelper h,Entity...entities){return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>3&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"actual published identity and natural entity ticks: "+e.getId()+" tick="+e.tickCount);}).thenExecute(()->{h.assertTrue(!FogOfWarServerEvents.isEnabled(),"birth smoke uses real fog-off baseline; real fog mask coverage belongs to later slice");guard(h);System.out.println("FOX_SPORE_SMOKE_READY testTick="+h.getTick()+" entities="+Arrays.stream(entities).map(e->e.getId()+":"+e.tickCount).toList());});}
    private static void guard(GameTestHelper h){for(String owner:ACCOUNTS){var r=ResourcesServerEvents.resourcesList.stream().filter(v->v.ownerName.equals(owner)).findFirst().orElse(null);h.assertTrue(r!=null,"fixture ledger account must remain present, never silently skipped or recreated: "+owner);h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456,"ordinary birth/lease leaves global resources unchanged: "+owner);h.assertTrue(r.foodToAdd==0&&r.woodToAdd==0&&r.oreToAdd==0&&r.emeraldToAdd==0,"no pending ledger settlement: "+owner);for(Entity body:FIXTURES)if(body instanceof Unit unit&&unit.getOwnerName().equals(owner))h.assertTrue(unit.getItems().isEmpty(),"no hidden unit cargo; never erase it: "+body.getUUID()+" "+unit.getItems());}}
    private static void watchAdded(LivingEntity target,Consumer<MobEffectEvent.Added> callback){
        if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}
        if(PROBE.added.put(target.getUUID(),callback)!=null)throw new IllegalStateException("duplicate added probe");
    }
    private static void watchApplicable(LivingEntity target,Consumer<MobEffectEvent.Applicable> callback){
        if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}
        if(PROBE.applicables.put(target.getUUID(),callback)!=null)throw new IllegalStateException("duplicate applicability probe");
    }
    private static void watchRemoval(LivingEntity target,Consumer<MobEffectEvent.Remove> callback){
        if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}
        if(PROBE.removals.put(target.getUUID(),callback)!=null)throw new IllegalStateException("duplicate removal probe");
    }
    private static void watchConstruction(Consumer<EntityEvent.EntityConstructing> callback){
        if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}
        if(PROBE.constructionCallback!=null)throw new IllegalStateException("duplicate construction probe");
        PROBE.constructionCallback=callback;
    }
    private static void watch(BlockPos position,Consumer<EntityJoinLevelEvent> callback){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}if(PROBE.callbacks.put(position.immutable(),callback)!=null)throw new IllegalStateException("duplicate Join probe position");}
    private static final class JoinProbe{
        BirthObservation birthObservation;
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true) public void observeBirth(EntityJoinLevelEvent event){var observation=birthObservation;if(observation!=null&&event.getLevel()==observation.level()&&event.getEntity() instanceof JungleSporeCarrier carrier&&carrier.blockPosition().equals(observation.position())){birthObservation=null;observation.callback().accept(event);}}

        final Map<UUID,Integer> attacks=new HashMap<>();
        final Map<UUID,Consumer<net.minecraftforge.event.entity.living.LivingDamageEvent>> firstDamage=new HashMap<>();
        final Map<Mob,Consumer<net.minecraftforge.event.PlayLevelSoundEvent>> rabbitSounds=new IdentityHashMap<>();
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST,receiveCanceled=true) public void attack(net.minecraftforge.event.entity.living.LivingAttackEvent event){attacks.computeIfPresent(event.getEntity().getUUID(),(key,value)->value+1);}
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public void damageFirst(net.minecraftforge.event.entity.living.LivingDamageEvent event){var callback=firstDamage.remove(event.getEntity().getUUID());if(callback!=null)callback.accept(event);}
        @SubscribeEvent public void rabbitSound(net.minecraftforge.event.PlayLevelSoundEvent event){if(event.getSound()==null||event.getSound().value()!=net.minecraft.sounds.SoundEvents.RABBIT_ATTACK)return;for(var source:List.copyOf(rabbitSounds.keySet())){if(source.level()!=event.getLevel())continue;boolean matches=event instanceof net.minecraftforge.event.PlayLevelSoundEvent.AtEntity e&&e.getEntity()==source||event instanceof net.minecraftforge.event.PlayLevelSoundEvent.AtPosition p&&p.getPosition().distanceToSqr(source.position())<.000001;if(matches){var callback=rabbitSounds.remove(source);if(callback!=null)callback.accept(event);}}}

        final Map<BlockPos,Consumer<EntityJoinLevelEvent>> callbacks=new HashMap<>();
        Consumer<EntityEvent.EntityConstructing> constructionCallback;
        final Map<UUID,Consumer<MobEffectEvent.Remove>> removals=new HashMap<>();
        final Map<UUID,Consumer<MobEffectEvent.Applicable>> applicables=new HashMap<>();
        final Map<UUID,Consumer<MobEffectEvent.Added>> added=new HashMap<>();
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public void added(MobEffectEvent.Added event){var callback=added.remove(event.getEntity().getUUID());if(callback!=null)callback.accept(event);}
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public void applicable(MobEffectEvent.Applicable event){var callback=applicables.remove(event.getEntity().getUUID());if(callback!=null)callback.accept(event);}
        @SubscribeEvent public void removing(MobEffectEvent.Remove event){var callback=removals.remove(event.getEntity().getUUID());if(callback!=null)callback.accept(event);}
        @SubscribeEvent public void constructing(EntityEvent.EntityConstructing event){
            if(!(event.getEntity() instanceof JungleSporeCarrier))return;
            var callback=constructionCallback;constructionCallback=null;
            if(callback!=null)callback.accept(event);
        }
        @SubscribeEvent public void joined(EntityJoinLevelEvent event){if(!(event.getEntity() instanceof JungleSporeCarrier carrier)||event.getLevel().isClientSide())return;var callback=callbacks.remove(carrier.blockPosition());if(callback!=null)callback.accept(event);}
    }
    private static void floor(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,7,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<7;y++)h.getLevel().setBlock(p.offset(x,y,z),(x==0||x==23||z==0||z==23?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p,p.offset(24,8,24))).forEach(Entity::discard);}
    private static final class RecordingHandler extends ServerGamePacketListenerImpl{
        final RecordingConnection wire;RecordingHandler(ServerPlayer player){this(player,new RecordingConnection());}
        private RecordingHandler(ServerPlayer player,RecordingConnection wire){super(player.serverLevel().getServer(),wire,player);this.wire=wire;}
        @Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet){wire.send(packet);}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){wire.send(packet,listener);}
    }
    /** Forge PLAYER distribution uses handler.connection, so capture the actual encoded payload here. */
    private static final class RecordingConnection extends net.minecraft.network.Connection{
        final List<SkillNetwork.EffectRemoved> removals=new ArrayList<>();RecordingConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}
        @Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet){record(packet);}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){record(packet);}
        private void record(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket payload&&payload.getIdentifier().equals(DreamseekersMod.id("unit_skills"))){FriendlyByteBuf data=new FriendlyByteBuf(payload.getData().copy());try{if(data.readVarInt()==4)removals.add(SkillNetwork.EffectRemoved.decode(data));}finally{data.release();}}}
    }
}
