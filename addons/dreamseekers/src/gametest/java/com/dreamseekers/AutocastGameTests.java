// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.dreamseekers.DreamseekersMod.*;
import static com.dreamseekers.skill.UnitSkill.*;

/** Real dedicated-server skills/ticks, native owner lookup, orders, and NBT. No simulated client or socket claims. */
@GameTestHolder("dreamseekers_autocast")
@PrefixGameTestTemplate(false)
public final class AutocastGameTests {
    private static final String BATCH="autocast_cases", FOG="autocast_fog";
    private static final String ARENA="unit_isolation_arena";
    private static final List<F> SESSIONS=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(F f:List.copyOf(SESSIONS))f.close();}
    @AfterBatch(batch=FOG) public static void cleanupFog(ServerLevel level){cleanup(level);}

    /** Stop test-only fixtures even after a failed assertion. Every test owns its own players/roster. */
    static final class F implements AutoCloseable {
        final GameTestHelper h; final ServerLevel level; final String own,foe,ally;
        final Resources account; final List<Mob> bodies=new ArrayList<>();
        final List<Runnable> cleanups=new ArrayList<>(); final PassiveIncomePeer peer;
        F(GameTestHelper h,String label) {
            this.h=h; level=h.getLevel(); own="AC"+label;foe=own+"E";ally=own+"A";SESSIONS.add(this);
            var at=h.absolutePos(BlockPos.ZERO);
            for(int x=0;x<24;x++)for(int z=0;z<24;z++)level.setBlock(at.offset(x,7,z),Blocks.STONE.defaultBlockState(),2);
            level.getEntitiesOfClass(FallingBlockEntity.class,new AABB(at.getX(),at.getY()+1,at.getZ(),at.getX()+24,level.getMaxBuildHeight(),at.getZ()+24)).forEach(Entity::discard);
            for(int x=0;x<24;x++)for(int z=0;z<24;z++) {
                level.setBlock(at.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=1;y<7;y++)level.setBlock(at.offset(x,y,z),((x==0||x==23||z==0||z==23)?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
            }
            peer=new PassiveIncomePeer(level,own);
            var seat=RTSPlayer.getNewPlayer(own,DREAMSEEKERS,peer.actor.getId(),0,true,-1L,-1L);
            seat.playerUuid=peer.actor.getUUID();PlayerServerEvents.rtsPlayers.add(seat);
            for(String name:List.of(foe,ally))PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DREAMSEEKERS));
            AlliancesServerEvents.addAlliance(own,ally);
            account=new Resources(own,432,543,654,123);account.extra.put(DreamPassiveEconomy.SHADOW,500);
            ResourcesServerEvents.resourcesList.add(account);
            peer.actor.moveTo(Vec3.atBottomCenterOf(at.offset(2,1,2)));level.addNewPlayer(peer.actor);PlayerServerEvents.players.add(peer.actor);
        }
        <T extends Mob> T spawn(EntityType<T> type,String owner,int x,int z) {return spawn(type,owner,x,z,null);}
        <T extends Mob> T spawn(EntityType<T> type,String owner,int x,int z,UUID id) {
            T e=Objects.requireNonNull(type.create(level));if(id!=null)e.setUUID(id);
            if(e instanceof Unit unit)unit.setOwnerName(owner);
            e.setNoAi(true);
            if(e.getAttribute(AttributeRegistrar.AGGRO_RANGE.get())!=null)e.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);
            if(e.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get())!=null)e.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);
            e.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x,1,z))));
            add(e);return e;
        }
        void place(Mob e,int x,int z){e.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x,1,z))));e.setDeltaMovement(Vec3.ZERO);}
        void shortReach(Mob e){e.getAttribute(AttributeRegistrar.ATTACK_RANGE.get()).setBaseValue(2);}
        void stationary(Mob e){for(var a:List.of(Attributes.MOVEMENT_SPEED,Attributes.FLYING_SPEED))if(e.getAttribute(a)!=null)e.getAttribute(a).setBaseValue(0);}
        void add(Mob e){h.assertTrue(level.addFreshEntity(e),"published fixture "+e.getType());bodies.add(e);}
        <T extends Mob> T durable(T e){e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(800);e.setHealth(800);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);return e;}
        GameTestSequence ready(){return h.startSequence().thenWaitUntil(()->{
            for(Mob e:bodies)h.assertTrue(e.tickCount>3&&e.isAddedToWorld()&&level.getEntity(e.getUUID())==e&&level.getEntity(e.getId())==e,"actual published fixture ready "+e.getUUID());
            h.assertTrue(level.getServer().getPlayerList().getPlayerByName(own)==peer.actor&&peer.connection.isConnected(),"original online embedded owner present");
        });}
        void order(SkillUnit source,UnitAction action,LivingEntity target){new UnitActionItem(source.getOwnerName(),action,target==null?-1:target.getId(),new int[]{source.mob().getId()},BlockPos.ZERO,BlockPos.ZERO).action(level);}
        void focus(SkillUnit source,LivingEntity target){order(source,UnitAction.ATTACK,target);h.assertTrue(source.mob().getTarget()==target,"real ATTACK selects requested target for "+source.mob().getType());}
        void bank(){h.assertTrue(account.food==432&&account.wood==543&&account.ore==654&&account.emerald==123&&account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==500,"automatic skills never spend/grant resources");}
        void done(){bank();close();h.succeed();}
        @Override public void close(){if(!SESSIONS.remove(this))return;
            for(int i=cleanups.size()-1;i>=0;i--)cleanups.get(i).run();
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(own)||p.name.equals(foe)||p.name.equals(ally));
            for(Mob e:bodies)if(!e.isRemoved())e.discard();
            AlliancesServerEvents.removeAlliance(own,ally);
            PlayerServerEvents.players.remove(peer.actor);level.removePlayerImmediately(peer.actor,Entity.RemovalReason.DISCARDED);
            peer.close();FogOfWarServerEvents.onPlayerDisconnect(peer.actor.getUUID());FogOfWarServerEvents.invalidateRtsCache();
            ResourcesServerEvents.resourcesList.remove(account);
        }
    }
    private static int cd(SkillUnit source,UnitSkill skill){return source.rts().skills().cooldownTicks(skill);}
    private static long rev(SkillUnit source){return source.rts().skills().revision();}
    private static SkillNetwork.Toggle pref(SkillUnit source,UnitSkill skill,boolean enable){return new SkillNetwork.Toggle(skill,source.mob().getId(),source.mob().getUUID(),rev(source),enable);}
    private static boolean queued(SkillUnit source,BlockPos end){return UnitServerEvents.getUnitActionSlowQueue().stream().anyMatch(a->Arrays.stream(a.getUnitIds()).anyMatch(id->id==source.mob().getId())&&a.getPreselectedBlockPos().equals(end));}

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=180)
    public static void explicitDefaultMatrixAndManualEconomyStayIntact(GameTestHelper h) {
        F f=new F(h,"Matrix");
        // Count actual registered entity skill routes, not HUD buttons or tooltip strings.
        var expected=EnumSet.of(JELLY_FLOW,GEL_GUARD,FOX_BLESSING,COORDINATED_MARK,BEETLE_ADVANCE,
            SHADOW_CORROSION_TOUCH,SQUEAL_SUPPRESSION_WAVE,TERRORBEAK_DREAD_ROAR,FRIENDLY_DEBT_CONTRACT,
            SHADOW_HAND_GRIP,ASH_EMBER_LANDING,SNAIL_SHELL_FORMATION);
        for(var skill:UnitSkill.values())h.assertTrue(skill.defaultAutocast()==expected.contains(skill),"new defaults explicit for "+skill);
        var knight=f.spawn(WIND_KNIGHT_UNIT.get(),f.own,6,6);
        var mother=f.spawn(BEETLE_MOTHER_UNIT.get(),f.own,5,15);
        var hand=f.spawn(AARONCOS_RIGHT_HAND_UNIT.get(),f.own,15,13);
        var enemy=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,8,7));
        f.ready().thenExecute(()->{
            f.focus(knight,enemy);for(var s:List.of(knight,mother,hand))s.setNoAi(false);
            h.assertTrue(pref(mother,MOTHER_BROOD,true).execute(f.peer.actor)!=SkillResult.SUCCESS,"summon cannot be enabled by forged toggle");
        }).thenIdle(45).thenExecute(()->{
            for(var s:List.<SkillUnit>of(knight,mother,hand))for(UnitSkill k:SkillService.skillsFor(s))
                h.assertTrue(!s.rts().skills().autocastEnabled(k)&&cd(s,k)==0,"manual tactical/economic skill stayed ready: "+k);
            h.assertTrue(!MotherNestStance.active(mother)&&!WindDash.isActive(knight),"no autonomous stance or dash");
            f.done();
        });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=185)
    public static void combatShieldRanksInjuredVisibleAlliesByRatioDistanceAndUuid(GameTestHelper h) {
        var f=new F(h,"Shield");var caster=f.spawn(PINK_SLIME_UNIT.get(),f.own,10,10);
        var closer=f.spawn(BONE_WING_UNIT.get(),f.own,10,12);
        // Spawn the higher UUID first: encounter order must not decide the tie.
        var later=f.spawn(BONE_WING_UNIT.get(),f.own,13,10,UUID.fromString("20000000-0000-0000-8000-000000000001"));
        var winner=f.spawn(BONE_WING_UNIT.get(),f.ally,13,10,UUID.fromString("10000000-0000-0000-8000-000000000001"));
        // Two motionless, collision-isolated targets share a point solely to force an exact UUID tie on the actual server cast tick.
        later.noPhysics=true;winner.noPhysics=true;f.stationary(later);f.stationary(winner);
        var idle=f.spawn(BONE_WING_UNIT.get(),f.own,10,7);f.shortReach(idle);
        var enemy=f.durable(f.spawn(BEETLE.get(),f.foe,10,14));
        f.ready().thenExecute(()->{
            f.peer.actor.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            h.assertTrue(f.peer.actor.isSpectator(),"real bound RTS commander remains authorized in top-down spectator mode");
            f.order(idle,UnitAction.STOP,null);idle.setHealth(1);closer.setHealth(closer.getMaxHealth()*.65f);
            for(var ally:List.of(later,winner))ally.setHealth(ally.getMaxHealth()*.30f);
            for(var ally:List.of(later,winner,closer))f.focus(ally,enemy);
            h.assertTrue(Math.abs(caster.distanceToSqr(winner)-caster.distanceToSqr(later))<0.0001,"exact distance tie established");
            caster.setNoAi(false);
        }).thenWaitUntil(()->h.assertTrue(cd(caster,GEL_GUARD)>0,"shield selected through native tick")).thenExecute(()->{
            h.assertTrue(Math.abs(caster.distanceToSqr(winner)-caster.distanceToSqr(later))<0.0001&&SkillEffects.shieldRemaining(winner)==SkillEffects.GEL_AMOUNT&&SkillEffects.shieldRemaining(later)==0
                &&SkillEffects.shieldRemaining(closer)==0&&SkillEffects.shieldRemaining(idle)==0,"shield picks wounded engaged ally by ratio/distance/UUID, not encounter order or nearly-dead idle bystander; shields winner/later/closer/idle="+List.of(SkillEffects.shieldRemaining(winner),SkillEffects.shieldRemaining(later),SkillEffects.shieldRemaining(closer),SkillEffects.shieldRemaining(idle))+" distances "+caster.distanceToSqr(winner)+"/"+caster.distanceToSqr(later));
            f.done();
        });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=210)
    public static void foxBlessingOnlyBenefitsEngagedOrdinaryCombatants(GameTestHelper h) {
        var f=new F(h,"Fox");var fox=f.spawn(GOLDEN_FOX_UNIT.get(),f.own,8,8);
        var engaged=f.spawn(BONE_WING_UNIT.get(),f.own,10,8);
        var idle=f.spawn(BONE_WING_UNIT.get(),f.own,8,11);f.shortReach(idle);
        var enemy=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,21,8));
        f.ready().thenExecute(()->fox.setNoAi(false)).thenIdle(22).thenExecute(()->{
            h.assertTrue(cd(fox,FOX_BLESSING)==0&&!SkillEffects.hasNamedBuff(idle,SkillEffects.FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED),"unengaged friends alone do not waste Blessing");
            f.place(enemy,12,8);f.focus(engaged,enemy);
        }).thenWaitUntil(()->h.assertTrue(cd(fox,FOX_BLESSING)>0,"battle support triggers real Blessing")).thenExecute(()->{
            h.assertTrue(SkillEffects.hasNamedBuff(engaged,SkillEffects.FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED)
                &&!SkillEffects.hasNamedBuff(idle,SkillEffects.FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED),"auto recipients respect their own eligible focus");
            h.assertTrue(cd(fox,FOX_DREAM_SEED)==0,"second ground/summon skill on same caster remains manual");
            f.done();
        });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=260)
    public static void newSwitchRejectsOutsidersAndOffSurvivesNativeEntityNbt(GameTestHelper h) {
        var f=new F(h,"Nbt");var old=f.spawn(BONE_WING_UNIT.get(),f.own,7,7);
        f.spawn(GOLDEN_FOX_UNIT.get(),f.own,20,18); // maintain actual seat while its only caster unloads
        var enemy=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,12,7));CompoundTag saved=new CompoundTag();
        BoneWingUnit[] restored={null};long[] offRevision={-1};
        f.ready().thenExecute(()->{
            var foreign=net.minecraftforge.common.util.FakePlayerFactory.get(f.level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),f.foe));
            var off=pref(old,COORDINATED_MARK,false);
            h.assertTrue(off.execute(foreign)==SkillResult.NOT_OWNER&&old.rts().skills().autocastEnabled(COORDINATED_MARK),"foreign toggle has no effect");
            h.assertTrue(off.execute(f.peer.actor)==SkillResult.SUCCESS&&off.execute(f.peer.actor)==SkillResult.REPLAY,"authenticated toggle accepted once");
            offRevision[0]=rev(old);
            h.assertTrue(old.save(saved)&&saved.getCompound("DreamseekersUnitSkills").getCompound("Autocast").contains("COORDINATED_MARK",Tag.TAG_BYTE)
                &&!saved.getCompound("DreamseekersUnitSkills").getCompound("Autocast").getBoolean("COORDINATED_MARK"),"actual entity save wrote explicit false, not an absent key");
            var legacy=saved.copy();legacy.getCompound("DreamseekersUnitSkills").getCompound("Autocast").remove("COORDINATED_MARK");
            var migrated=(BoneWingUnit)EntityType.loadEntityRecursive(legacy,f.level,e->e);
            h.assertTrue(migrated!=null&&migrated.rts().skills().autocastEnabled(COORDINATED_MARK),"old entity with genuinely missing preference adopts new default");
            old.discard();
        }).thenWaitUntil(()->h.assertTrue(f.level.getEntity(old.getUUID())==null,"former entity left UUID index")).thenExecute(()->{
            restored[0]=(BoneWingUnit)EntityType.loadEntityRecursive(saved,f.level,e->e);var r=restored[0];
            h.assertTrue(r!=null&&r!=old&&r.getUUID().equals(old.getUUID())&&!r.isNoAi(),"vanilla loader restored original UUID and normal AI in new body");f.add(r);f.focus(r,enemy);
        }).thenIdle(24).thenExecute(()->{
            var r=restored[0];
            h.assertTrue(!r.rts().skills().autocastEnabled(COORDINATED_MARK)&&cd(r,COORDINATED_MARK)==0&&CoordinatedMark.ticksRemaining(enemy,f.own)==0,"explicit off survives native reload and real targeting");
            h.assertTrue(new SkillNetwork.Toggle(COORDINATED_MARK,r.getId(),r.getUUID(),offRevision[0],true).execute(f.peer.actor)==SkillResult.REPLAY,"pre-load generation cannot re-enable");
            h.assertTrue(pref(r,COORDINATED_MARK,true).execute(f.peer.actor)==SkillResult.SUCCESS,"newer authenticated generation may re-enable");
        }).thenWaitUntil(()->h.assertTrue(cd(restored[0],COORDINATED_MARK)>0&&CoordinatedMark.ticksRemaining(enemy,f.own)>0,"later regular tick marks same live enemy")).thenExecute(f::done);
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=325)
    public static void offlineUnknownNeutralAllyControlledAndNoAiNeverMark(GameTestHelper h) {
        var f=new F(h,"Deny");var wing=f.spawn(BONE_WING_UNIT.get(),f.own,8,8);
        var enemy=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,13,8));
        var unknown=f.durable(f.spawn(PINK_SLIME_UNIT.get(),"NoSuchACSeat",12,9));
        var blank=f.spawn(PINK_SLIME_UNIT.get(),"",12,7);var friendly=f.spawn(PINK_SLIME_UNIT.get(),f.ally,12,6);
        var wild=f.spawn(EntityType.PIG,"",11,11);
        f.ready().thenExecute(()->f.focus(wing,enemy)).thenIdle(22).thenExecute(()->{
            h.assertTrue(cd(wing,COORDINATED_MARK)==0,"NoAI with a legal focus never fires");
            wing.setNoAi(false);wing.setOwnerName(f.foe); // valid offline RTS bot, no fabricated viewer
            f.focus(wing,friendly);
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(cd(wing,COORDINATED_MARK)==0&&f.level.getServer().getPlayerList().getPlayerByName(f.foe)==null,"no online owner means no fog-sensitive offense");
            wing.setOwnerName(f.own);
            f.focus(wing,unknown);h.assertTrue(PlayerServerEvents.getRTSPlayer("NoSuchACSeat")==null,"unregistered owner is a real, otherwise attackable foreign unit");
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(cd(wing,COORDINATED_MARK)==0&&!CoordinatedMark.hasActiveTargetMark(unknown),"unknown nonblank owner never authorizes new auto offense");
            for(var target:List.of(blank,friendly,wild)) {
                f.order(wing,UnitAction.ATTACK,target);
                h.assertTrue(wing.getTarget()!=target&&!CoordinatedMark.hasActiveTargetMark(target),"blank owner, allied, and wild bodies cannot gain an enemy focus");
            }
            f.focus(wing,enemy);wing.addEffect(new MobEffectInstance(MobEffectRegistrar.UNCONTROLLABLE.get(),65));
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(cd(wing,COORDINATED_MARK)==0,"uncontrollable combatant cannot act");
            wing.removeEffect(MobEffectRegistrar.UNCONTROLLABLE.get());wing.addEffect(new MobEffectInstance(MobEffectRegistrar.FREEZE.get(),65));
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(cd(wing,COORDINATED_MARK)==0,"freeze cannot act");
            wing.removeEffect(MobEffectRegistrar.FREEZE.get());wing.addEffect(new MobEffectInstance(MobEffectRegistrar.STUN.get(),65));
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(cd(wing,COORDINATED_MARK)==0,"stun cannot act");
            wing.removeEffect(MobEffectRegistrar.STUN.get());f.focus(wing,enemy);
        }).thenWaitUntil(()->h.assertTrue(CoordinatedMark.ticksRemaining(enemy,f.own)>0&&cd(wing,COORDINATED_MARK)>0,"same caster now marks explicitly visible eligible enemy")).thenExecute(f::done);
    }

    @GameTest(template=ARENA,batch=FOG,timeoutTicks=225)
    public static void enemyMarkNeedsPhysicalLosAndExactOwnerFogColumn(GameTestHelper h) {
        var f=new F(h,"Fog");var wing=f.spawn(BONE_WING_UNIT.get(),f.own,8,8);
        var target=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,13,8));f.stationary(wing);
        var fog=new GolemStrikeFogGameTests.ControlledFog(h,f.peer.actor);f.cleanups.add(fog::close);
        f.ready().thenExecute(()->{
            fog.install();fog.columns(List.of(wing.blockPosition(),target.blockPosition().east()));f.focus(wing,target);wing.setNoAi(false);
            h.assertTrue(CoordinatedMark.visible(f.peer.actor,wing)&&wing.hasLineOfSight(target)&&!CoordinatedMark.visible(f.peer.actor,target),"clear physical ray while only adjacent fog column is known");
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(cd(wing,COORDINATED_MARK)==0&&!CoordinatedMark.hasActiveTargetMark(target),"hidden target denied despite original real native focus");
            fog.columns(List.of(wing.blockPosition(),target.blockPosition()));
            // Full-height separation: obey native physical LOS even when the exact column is known.
            for(int z=1;z<=22;z++)for(int y=1;y<=6;y++)f.level.setBlock(h.absolutePos(new BlockPos(11,y,z)),Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(!wing.hasLineOfSight(target)&&CoordinatedMark.visible(f.peer.actor,target),"native ray blocked but exact owner column known");
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(cd(wing,COORDINATED_MARK)==0&&!CoordinatedMark.hasActiveTargetMark(target),"visibility cache is not a substitute for current LOS");
            for(int z=1;z<=22;z++)for(int y=1;y<=6;y++)f.level.setBlock(h.absolutePos(new BlockPos(11,y,z)),Blocks.AIR.defaultBlockState(),2);
            fog.columns(List.of(wing.blockPosition(),target.blockPosition()));f.focus(wing,target);
        }).thenWaitUntil(()->h.assertTrue(cd(wing,COORDINATED_MARK)>0&&CoordinatedMark.ticksRemaining(target,f.own)>0,"identical visible target marked after both gates open; focus="+(wing.getTarget()==target)+" owner="+(PlayerServerEvents.getRTSPlayer(f.own)!=null)+" enemy="+(PlayerServerEvents.getRTSPlayer(f.foe)!=null)+" see="+CoordinatedMark.visible(f.peer.actor,wing)+"/"+CoordinatedMark.visible(f.peer.actor,target)+" LOS="+wing.hasLineOfSight(target)+" pos="+wing.position()+"/"+target.position()+" cd="+cd(wing,COORDINATED_MARK))).thenExecute(f::done);
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=200)
    public static void stopAndShiftQueueWinAgainstReadySkillWithoutLaterReplay(GameTestHelper h) {
        var f=new F(h,"Orders");var wing=f.spawn(BONE_WING_UNIT.get(),f.own,5,7);
        var victim=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,10,7));f.shortReach(wing);
        var first=h.absolutePos(new BlockPos(21,0,7));var second=h.absolutePos(new BlockPos(21,0,19));long[] afterStop={0};
        f.ready().thenExecute(()->{
            f.focus(wing,victim);f.order(wing,UnitAction.STOP,null);afterStop[0]=wing.rts().commandRevision;wing.setNoAi(false);
        }).thenIdle(23).thenExecute(()->{
            h.assertTrue(cd(wing,COORDINATED_MARK)==0&&wing.getTarget()==null&&wing.rts().commandRevision==afterStop[0],"STOP invalidates old focus, no delayed cast/revision rewrite");
            UnitServerEvents.addActionItem(f.own,UnitAction.MOVE,-1,new int[]{wing.getId()},first,BlockPos.ZERO);
            UnitServerEvents.addActionItem(f.own,UnitAction.MOVE,-1,new int[]{wing.getId()},second,BlockPos.ZERO,true);
        }).thenIdle(4).thenExecute(()->h.assertTrue(wing.getMoveGoal().getMoveTarget()!=null&&queued(wing,second),"first actual move running, second Shift order pending"))
          .thenIdle(12).thenExecute(()->{
              h.assertTrue(cd(wing,COORDINATED_MARK)==0&&queued(wing,second)&&!CoordinatedMark.hasActiveTargetMark(victim),"automatic scheduler does not reacquire stale target or replace pending movement");
              f.done();
          });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=190)
    public static void canceledPhysicalLaunchesAreStaggeredAndCooldownStartsOnlyOnceAfterSuccess(GameTestHelper h) {
        var f=new F(h,"Pace");var caster=f.spawn(SHADOW_SQUEAL_GHOST_UNIT.get(),f.own,7,8);
        var target=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,12,8));List<Long> attempts=new ArrayList<>();
        boolean[] cancel={true};long[] startRevision={-1},expiry={-1};
        Consumer<EntityJoinLevelEvent> probe=event->{
            if(event.getEntity() instanceof com.dreamseekers.shadow.SuppressionWaveProjectile p&&p.getOwner()==caster){
                attempts.add(f.level.getGameTime());
                if(cancel[0]){event.setCanceled(true);caster.rts().skills().tick();} // real callback re-entry, same time
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,probe);f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(probe));
        f.ready().thenExecute(()->{f.focus(caster,target);startRevision[0]=rev(caster);caster.setNoAi(false);})
          .thenIdle(43).thenExecute(()->{
              h.assertTrue(attempts.size()>=3&&attempts.size()<=5,"productive but bounded failed physical launch attempts over 43 ticks: "+attempts);
              for(int i=1;i<attempts.size();i++)h.assertTrue(attempts.get(i)-attempts.get(i-1)>=10,"no duplicate/reentrant/repeated below ten ticks: "+attempts);
              h.assertTrue(cd(caster,SQUEAL_SUPPRESSION_WAVE)==0&&rev(caster)==startRevision[0],"canceled publications never spend or advance");
              cancel[0]=false;
          }).thenWaitUntil(()->h.assertTrue(cd(caster,SQUEAL_SUPPRESSION_WAVE)>0,"real launch after cancellation lifted")).thenExecute(()->{
              expiry[0]=f.level.getGameTime()+cd(caster,SQUEAL_SUPPRESSION_WAVE);
              h.assertTrue(expiry[0]==attempts.get(attempts.size()-1)+SQUEAL_SUPPRESSION_WAVE.cooldownTicks()&&rev(caster)==startRevision[0]+1,"first successful launch starts exact cooldown once");
          }).thenIdle(25).thenExecute(()->{
              h.assertTrue(expiry[0]==f.level.getGameTime()+cd(caster,SQUEAL_SUPPRESSION_WAVE)&&rev(caster)==startRevision[0]+1,"ongoing native ticks do not repeat or restart cooldown");
              h.assertTrue(SquealSuppressionWave.hasDebuff(target),"real published skill projectile struck and applied named suppression");
              f.done();
          });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=200)
    public static void holdOnlyShellRespondsToObservedRangedThreatAndNewStopEndsLease(GameTestHelper h) {
        var f=new F(h,"Shell");var snail=f.spawn(BASALT_SNAIL.get(),f.own,8,8);
        var threat=f.spawn(BONE_WING_UNIT.get(),f.foe,21,8);double[] beforeShoveZ={0};
        f.ready().thenExecute(()->{f.order(snail,UnitAction.HOLD,null);snail.setNoAi(false);})
          .thenIdle(22).thenExecute(()->{
              h.assertTrue(snail.getHoldPosition()&&!SnailShellFormation.active(snail)&&cd(snail,SNAIL_SHELL_FORMATION)==0,"HOLD without an aimed ranged threat does not spend");
              f.place(threat,13,8);f.focus(threat,snail);threat.setNoAi(false); // native ranged combat, not just a held test selection
          }).thenWaitUntil(()->h.assertTrue(SnailShellFormation.active(snail)&&cd(snail,SNAIL_SHELL_FORMATION)>0,"native HOLD plus real enemy ranged focus starts defensive lease")).thenExecute(()->{
              h.assertTrue(snail.getHoldPosition(),"automatic formation retained explicit HOLD");
              beforeShoveZ[0]=snail.getZ();snail.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
              snail.knockback(.55,0,-1);
              h.assertTrue(snail.getDeltaMovement().z>.3,"a native horizontal impulse enters the automatic HOLD overlay");
          }).thenIdle(2).thenExecute(()->{
              h.assertTrue(snail.getZ()>beforeShoveZ[0]+.12&&snail.getHoldPosition()&&SnailShellFormation.active(snail),"automatic deployment never pins native external motion or replaces the standing order");
              f.order(snail,UnitAction.STOP,null);
              h.assertTrue(!SnailShellFormation.active(snail)&&!snail.getHoldPosition()&&cd(snail,SNAIL_SHELL_FORMATION)>0,"newer STOP immediately ends formation without refund");
              f.done();
          });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=200)
    public static void boundedSupportSkipsCrowdedScanThenSelectsAfterOverflowClears(GameTestHelper h) {
        var f=new F(h,"Crowd");var gel=f.spawn(PINK_SLIME_UNIT.get(),f.own,10,10);
        var enemy=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,13,13));
        List<BoneWingUnit> crowd=new ArrayList<>();
        // 65 valid role matches inside six blocks; no base/other unit receives a skill grant.
        for(int i=0;i<65;i++){
            int x=7+i%7,z=7+(i/7)%7;
            var u=f.spawn(BONE_WING_UNIT.get(),f.own,x,z);u.noPhysics=true;
            // Prevent ordinary crowd collisions or entity-cramming deaths from reducing the sample.
            u.setInvulnerable(true);crowd.add(u);
        }
        f.ready().thenExecute(()->{
            for(var u:crowd){f.focus(u,enemy);u.setHealth(u.getMaxHealth()/2);}
            gel.setNoAi(false);
        }).thenIdle(22).thenExecute(()->{
            long within=crowd.stream().filter(u->u.isAlive()&&gel.distanceToSqr(u)<=36&&u.getTarget()==enemy).count();
            h.assertTrue(within==65&&cd(gel,GEL_GUARD)==0&&crowd.stream().noneMatch(u->SkillEffects.shieldRemaining(u)>0),"65 local matches must fail closed, not cast on arbitrary iteration slice; live matches="+within);
            for(int i=1;i<crowd.size();i++)crowd.get(i).discard();
        }).thenWaitUntil(()->h.assertTrue(cd(gel,GEL_GUARD)>0&&SkillEffects.shieldRemaining(crowd.get(0))>0,"normal dispatch resumes when overflow clears")).thenExecute(f::done);
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=200)
    public static void meleeAdvanceCorrosionAndRoarUseRealCurrentCombatant(GameTestHelper h) {
        var f=new F(h,"Melee");var beetle=f.spawn(BEETLE.get(),f.own,5,5);
        var ghost=f.spawn(SHADOW_GHOST_UNIT.get(),f.own,5,12);
        var beak=f.spawn(TERRORBEAK_UNIT.get(),f.own,5,19);
        var a=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,7,5));
        var b=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,6,12));
        var c=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,7,19));
        f.ready().thenExecute(()->{
            b.getAttribute(Attributes.ARMOR).setBaseValue(8); // actual reducible armor for the corrosion trigger
            f.focus(beetle,a);f.focus(ghost,b);f.focus(beak,c);
            for(var s:List.of(beetle,ghost,beak))s.setNoAi(false);
        }).thenWaitUntil(()->{
            h.assertTrue(cd(beetle,BEETLE_ADVANCE)>0&&BeetleAdvance.active(beetle),"real engaged Beetle receives advance");
            h.assertTrue(cd(ghost,SHADOW_CORROSION_TOUCH)>0&&(ShadowCorrosionTouch.remainingArmTicks(ghost)>0||ShadowCorrosionTouch.hasDebuff(b)),"real close-range Shadow Ghost arms/lands corrosion");
            h.assertTrue(cd(beak,TERRORBEAK_DREAD_ROAR)>0&&TerrorbeakDreadRoar.hasDisarm(c),"real eligible enemy combatant disarmed by roar");
        }).thenExecute(f::done);
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=240)
    public static void gripsStationaryEmbersAndContributorGatedDebtUseNativePaths(GameTestHelper h) {
        var f=new F(h,"Tactics");var hand=f.spawn(SHADOW_HAND_UNIT.get(),f.own,6,5);
        var ash=f.spawn(ASH_BONE_WING_UNIT.get(),f.own,6,12);
        var debt=f.spawn(FRIENDLY_GHOST_UNIT.get(),f.own,6,19);
        var peer1=f.spawn(BONE_WING_UNIT.get(),f.own,5,17);var peer2=f.spawn(BONE_WING_UNIT.get(),f.own,2,2);
        var a=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,10,5));
        var b=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,10,12));
        // NoAI stands at spawn height without running vanilla gravity/collision; use a real falling body for the ground-aim test.
        b.setNoAi(false);f.stationary(b);b.setPos(b.getX(),b.getY()+.8,b.getZ());
        var c=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,10,19));
        boolean[] sawGrip={false},sawEmber={false};
        List<Entity> embers=new ArrayList<>();Consumer<EntityJoinLevelEvent> emberJoin=ev->{
            if(ev.getEntity() instanceof com.dreamseekers.shadow.EmberLandingProjectile p&&p.getOwner()==ash)embers.add(p);
        };MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,emberJoin);f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(emberJoin));
        f.ready().thenWaitUntil(()->h.assertTrue(b.onGround()&&b.getNavigation().isDone(),"ground sample settled through native AI/collision")).thenExecute(()->{
            f.focus(hand,a);f.focus(ash,b);f.focus(debt,c);f.focus(peer1,c);
            for(var s:List.of(hand,ash,debt))s.setNoAi(false);
        }).thenIdle(18).thenExecute(()->{
            sawGrip[0]=cd(hand,SHADOW_HAND_GRIP)>0&&(ShadowHandGrip.remainingWindupTicks(hand)>0||ShadowHandGrip.hasRoot(a)||ShadowHandGrip.remainingRecontrolTicks(a)>0);
            sawEmber[0]=cd(ash,ASH_EMBER_LANDING)>0&&embers.size()==1;
            h.assertTrue(sawGrip[0]&&sawEmber[0],"natural hand grip and targeted-to-ground ember used normal gameplay effects: grip="+sawGrip[0]+" ember="+sawEmber[0]+" ashCd="+cd(ash,ASH_EMBER_LANDING)+" aim="+(ash.getTarget()==b)+" ground="+b.onGround()+" delta="+b.getDeltaMovement()+" src="+ash.position()+" target="+b.position()+" joins="+embers.size());
            h.assertTrue(cd(debt,FRIENDLY_DEBT_CONTRACT)==0&&FriendlyDebtContract.remainingTicks(c,f.own)==0,"only two focused allies do not reserve contract");f.place(peer2,5,21);f.focus(peer2,c);
        }).thenWaitUntil(()->h.assertTrue(cd(debt,FRIENDLY_DEBT_CONTRACT)>0&&FriendlyDebtContract.remainingTicks(c,f.own)>0,"third focused contributor enables real debt contract")).thenExecute(f::done);
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=200)
    public static void callbackToggleAndPendingStopRevokeInFlightAutomaticCommit(GameTestHelper h) {
        var f=new F(h,"Revoke");
        var off=f.spawn(SHADOW_SQUEAL_GHOST_UNIT.get(),f.own,7,6);
        var stopped=f.spawn(SHADOW_SQUEAL_GHOST_UNIT.get(),f.own,7,16);f.shortReach(stopped);f.stationary(stopped);
        var a=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,12,6));var b=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,12,16));
        List<Entity> joins=new ArrayList<>();int[] counts={0,0};
        Consumer<EntityJoinLevelEvent> probe=ev->{
            if(!(ev.getEntity() instanceof com.dreamseekers.shadow.SuppressionWaveProjectile p))return;
            if(p.getOwner()==off){joins.add(p);counts[0]++;
                h.assertTrue(pref(off,SQUEAL_SUPPRESSION_WAVE,false).execute(f.peer.actor)==SkillResult.SUCCESS,"owner's real toggle during physical Join is accepted");
            }else if(p.getOwner()==stopped){joins.add(p);counts[1]++;
                UnitServerEvents.addActionItem(f.own,UnitAction.STOP,-1,new int[]{stopped.getId()},BlockPos.ZERO,BlockPos.ZERO);
                h.assertTrue(UnitServerEvents.hasPendingImmediateAction(stopped.getId()),"actual deferred immediate command visible during Join");
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,probe);f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(probe));
        f.ready().thenExecute(()->{f.focus(off,a);f.focus(stopped,b);off.setNoAi(false);stopped.setNoAi(false);})
          .thenIdle(36).thenExecute(()->{
              h.assertTrue(Arrays.equals(counts,new int[]{1,1})&&joins.stream().allMatch(Entity::isRemoved),"each original in-flight attempt revoked, no stale resubmission: "+Arrays.toString(counts));
              h.assertTrue(!off.rts().skills().autocastEnabled(SQUEAL_SUPPRESSION_WAVE)&&stopped.getTarget()==null,"off and newer STOP both remain effective");
              h.assertTrue(cd(off,SQUEAL_SUPPRESSION_WAVE)==0&&cd(stopped,SQUEAL_SUPPRESSION_WAVE)==0&&!SquealSuppressionWave.hasDebuff(a)&&!SquealSuppressionWave.hasDebuff(b),"aborted publication is not a paid cast or hidden hit");
              f.done();
          });
    }
    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=185)
    public static void automaticHandsDoNotBothPayForOnePendingRoot(GameTestHelper h) {
        var f=new F(h,"Contest");
        var left=f.spawn(SHADOW_HAND_UNIT.get(),f.own,6,8);var right=f.spawn(SHADOW_HAND_UNIT.get(),f.own,7,11);
        var first=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,10,9));var next=f.durable(f.spawn(PINK_SLIME_UNIT.get(),f.foe,9,13));
        ShadowHandUnit[] waiting={null};
        f.ready().thenExecute(()->{f.focus(left,first);f.focus(right,first);left.setNoAi(false);right.setNoAi(false);})
          .thenWaitUntil(()->h.assertTrue(ShadowHandGrip.hasRoot(first),"one genuine delayed native root released"))
          .thenExecute(()->{
              boolean l=cd(left,SHADOW_HAND_GRIP)>0,r=cd(right,SHADOW_HAND_GRIP)>0;
              h.assertTrue(l!=r,"only one hand paid the full cooldown for the same pending root; left="+cd(left,SHADOW_HAND_GRIP)+" right="+cd(right,SHADOW_HAND_GRIP));
              waiting[0]=l?right:left;f.focus(waiting[0],next);
          }).thenWaitUntil(()->h.assertTrue(ShadowHandGrip.hasRoot(next)&&cd(waiting[0],SHADOW_HAND_GRIP)>0,"unspent second hand can root a different current target")).thenExecute(f::done);
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=200)
    public static void shellNeverStealsExecutingMoveAndShiftQueueFromATrulyTargetedSnail(GameTestHelper h) {
        var f=new F(h,"ShellMove");var snail=f.durable(f.spawn(BASALT_SNAIL.get(),f.own,5,8));
        var bow=f.spawn(BONE_WING_UNIT.get(),f.foe,21,8);
        BlockPos first=h.absolutePos(new BlockPos(21,0,8)),second=h.absolutePos(new BlockPos(21,0,18));
        BlockPos[] actualFirst={null};long[] movingGeneration={-1};Vec3[] start={null};
        f.ready().thenExecute(()->{
            f.order(snail,UnitAction.HOLD,null);snail.setNoAi(false);
            UnitServerEvents.addActionItem(f.own,UnitAction.MOVE,-1,new int[]{snail.getId()},first,BlockPos.ZERO);
            UnitServerEvents.addActionItem(f.own,UnitAction.MOVE,-1,new int[]{snail.getId()},second,BlockPos.ZERO,true);
        }).thenWaitUntil(()->h.assertTrue(!snail.getHoldPosition()&&snail.getMoveGoal().getMoveTarget()!=null&&queued(snail,second),"real first MOVE has superseded HOLD and is already running, second Shift-MOVE remains queued"))
          .thenExecute(()->{
              actualFirst[0]=snail.getMoveGoal().getMoveTarget();movingGeneration[0]=snail.rts().commandRevision;start[0]=snail.position();
              f.place(bow,10,8);f.focus(bow,snail);bow.setNoAi(false);
              h.assertTrue(bow.hasLineOfSight(snail)&&bow.distanceToSqr(snail)<=100&&bow.getTarget()==snail,"actual nearby ranged enemy acquired moving snail, not merely a bystander");
          }).thenIdle(13).thenExecute(()->{
              h.assertTrue(bow.getTarget()==snail&&snail.position().distanceToSqr(start[0])>.01,"real ranged lock and ordinary movement continue across a decision interval");
              h.assertTrue(actualFirst[0].equals(snail.getMoveGoal().getMoveTarget())&&queued(snail,second)&&snail.rts().commandRevision==movingGeneration[0],"same active destination, Shift destination and owner command generation survive");
              h.assertTrue(cd(snail,SNAIL_SHELL_FORMATION)==0&&!SnailShellFormation.active(snail)&&!snail.getHoldPosition(),"no automatic stance, cooldown, or restored HOLD during MOVE");
              f.done();
          });
    }
}
