// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import net.pasterdream.entity.BlackBeetleMotherEntity;
import net.pasterdream.entity.BlackBeetleEntity;
import net.pasterdream.procedures.BlackBeetleMotherPr0Procedure;
import java.util.*;

/** Executes the original runtime procedure, not a copied implementation. Each room has its own correctly-sized template. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BeetleMotherNativeGameTests {
    private static final String BATCH="dreamseekers_mother_native",PREFIX="MotherNative_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final Set<net.minecraft.world.level.ChunkPos> TICKETS=new HashSet<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity e:FIXTURES){var objective=level.getScoreboard().getObjective("skill");if(objective!=null)level.getScoreboard().resetPlayerScore(e.getScoreboardName(),objective);if(!e.isRemoved())e.discard();}FIXTURES.clear();
        for(var c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @GameTest(template="mother_native_arena",batch=BATCH,timeoutTicks=320)
    public static void realWildSummonAndBuffExcludePaidBeetlesWhilePaidMotherNeverSchedules(GameTestHelper h){
        room(h);var wild=wild(h,15,16);wild.setHealth(40);
        var paid=paid(h,"Source",54,16);var wildBeetle=beetle(h,18,16);var paidBeetle=paidBeetle(h,"Target",21,16);
        var player=player(h,"Witness",15,21);float[] hp={0};
        ready(h,wild,paid,wildBeetle,paidBeetle,player).thenExecute(()->{
            h.assertTrue(!hasSkillScore(paid),"paid source has no native score before damage");
            // The procedure is invoked before native FALL rejection. NoAI is deliberately true on both sides.
            h.assertTrue(!wild.hurt(wild.damageSources().fall(),4),"wild native fall damage rejected");
            h.assertTrue(hasSkillScore(wild)&&score(wild)==1&&wild.hasEffect(MobEffects.LEVITATION),"rejected wild damage still enters original immediate skill branch");
            hp[0]=wild.getHealth();
            h.assertTrue(!paid.hurt(paid.damageSources().fall(),4),"paid retains fall immunity");
            paid.hurt(paid.damageSources().generic(),0);paid.invulnerableTime=0;paid.hurt(paid.damageSources().generic(),1);
            paid.setOwnerName("");BlackBeetleMotherPr0Procedure.execute(h.getLevel(),paid.getX(),paid.getY(),paid.getZ(),paid);
            h.assertTrue(!hasSkillScore(paid)&&paid.getActiveEffects().isEmpty(),"paid and blank-owner source cancel before score, effects or deferred work");
        }).thenIdle(6).thenExecute(()->{
            var wildChildren=h.getLevel().getEntitiesOfClass(BlackBeetleEntity.class,wild.getBoundingBox().inflate(12),e->!(e instanceof BeetleUnit));
            h.assertTrue(wildChildren.size()==5,"actual original 1-tick callback adds exactly four wild beetles to the preexisting one: "+wildChildren.size());
            h.assertTrue(wildBeetle.hasEffect(MobEffects.DAMAGE_BOOST)&&wildBeetle.hasEffect(MobEffects.MOVEMENT_SPEED)&&wildBeetle.hasEffect(MobEffects.DAMAGE_RESISTANCE)&&wildBeetle.getTarget()==player,"actual native 2-tick buffs and player targeting preserved for wild beetle");
            h.assertTrue(paidBeetle.getActiveEffects().isEmpty()&&paidBeetle.getTarget()!=player,"wild mother cannot buff or command existing paid beetle subtype");
            h.assertTrue(wild.getHealth()==hp[0]+5,"query excludes paid beetle from original source healing count too: "+hp[0]+" -> "+wild.getHealth());
            for(var e:wildChildren){e.setNoAi(true);if(!FIXTURES.contains(e))FIXTURES.add(e);}
            h.assertTrue(h.getLevel().getEntitiesOfClass(BlackBeetleEntity.class,paid.getBoundingBox().inflate(12)).isEmpty(),"no free beetles near paid source after actual delayed callback window");
        }).thenIdle(205).thenExecute(()->{
            h.assertTrue(score(wild)==0,"wild original 200-tick cooldown callback still executes");
            h.assertTrue(!hasSkillScore(paid)&&paid.getActiveEffects().isEmpty()&&paidBeetle.getActiveEffects().isEmpty(),"paid source never schedules later native writes and target remains unbuffed");
            System.out.println("DREAMSEEKERS_MOTHER_NATIVE_SUMMON_BUFF_WILD_POSITIVE_PAID_SOURCE_AND_TARGET_ISOLATED_PASS");
        }).thenSucceed();
    }
    @GameTest(template="mother_native_arena",batch=BATCH,timeoutTicks=160)
    public static void nativeHelpGoalRetainsWildPositiveButCannotRecruitPaidMother(GameTestHelper h){
        room(h);var caller=wild(h,12,16);var sibling=wild(h,16,16);var paid=paid(h,"Alert",20,16);var player=player(h,"Aggressor",9,16);
        ready(h,caller,sibling,paid,player).thenExecute(()->{
            setSkillScore(caller,1); // Native cooldown isolates this help-goal test from the separately verified summons.
            h.assertTrue(caller.hurt(caller.damageSources().playerAttack(player),1),"real player damage sets native revenge memory");
            var goal=(HurtByTargetGoal)caller.targetSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(g->g instanceof HurtByTargetGoal).findFirst().orElseThrow();
            h.assertTrue(goal.canUse(),"original help goal sees real revenge");goal.start();
            h.assertTrue(sibling.getTarget()==player,"original wild helper receives the native alert");
            h.assertTrue(paid.getTarget()==null&&!paid.doHurtTarget(player),"parent-class query cannot make paid mother attack an unowned Player");
            caller.setTarget(null);sibling.setTarget(null);
        }).thenIdle(30).thenExecute(()->h.assertTrue(paid.getTarget()==null,"no later native goal resumes Player pursuit")).thenSucceed();
    }
    @GameTest(template="mother_native_arena",batch=BATCH,timeoutTicks=180)
    public static void ordinaryNbtBodyAndPeacefulKeepPaidNonHeroIdentity(GameTestHelper h){
        room(h);var paid=paid(h,"Nbt",15,16);var wild=wild(h,54,16);paid.setNoAi(false);paid.setScenarioRoleIndex(96);
        // Keep a real local observer, not persistence: unrelated >128-block native despawn must not erase this positive control.
        var witness=player(h,"NbtWitness",34,16);
        ready(h,paid,wild,witness).thenExecute(()->{
            h.assertTrue(h.getLevel().getNearestPlayer(wild,-1)==witness&&wild.distanceTo(witness)<128&&!wild.isPersistenceRequired()&&wild.isNoAi(),"real local NBT witness is nearest within native despawn range; original wild stays nonpersistent and NoAI");
            CompoundTag tag=new CompoundTag();paid.saveWithoutId(tag);tag.putBoolean("NoAI",true);
            var restored=DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel());restored.load(tag);
            h.assertTrue(restored.getUUID().equals(paid.getUUID())&&restored.getOwnerName().equals(paid.getOwnerName())&&restored.getScenarioRoleIndex()==96&&!restored.isNoAi(),"ordinary NBT keeps paid identity but cannot disable controller");
            BlackBeetleMotherPr0Procedure.execute(h.getLevel(),restored.getX(),restored.getY(),restored.getZ(),restored);
            h.assertTrue(!hasSkillScore(restored)&&restored.getActiveEffects().isEmpty(),"unpublished loaded subtype remains natively isolated");
            h.assertTrue(!((Object)paid instanceof com.solegendary.reignofnether.unit.interfaces.HeroUnit)&&paid.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.MOTHER_BROOD,com.dreamseekers.skill.UnitSkillAbility.MOTHER_NEST)),"exact approved summon skill, no implicit XP, mana, fallen hero or revival contract");
            var old=h.getLevel().getDifficulty();try{h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);paid.checkDespawn();wild.checkDespawn();h.assertTrue(paid.isAlive()&&!paid.isRemoved()&&wild.isRemoved(),"paid persistence only; wild peaceful disappearance retained");}finally{h.getLevel().getServer().setDifficulty(old,true);}
        }).thenIdle(35).thenExecute(()->h.assertTrue(Math.abs(paid.getBbWidth()-2)<.001&&Math.abs(paid.getBbHeight()-1)<.001&&h.getLevel().noCollision(paid)&&!paid.isNoGravity()&&!paid.isFlyingUnit(),"actual unscaled2x1 ground collision body retained")).thenSucceed();
    }
    @GameTest(template="mother_native_arena",batch=BATCH,timeoutTicks=100)
    public static void nativeBossbarCallbacksAreInstanceIsolated(GameTestHelper h)throws Exception{
        room(h);var paid=paid(h,"Bossbar",15,16);var wild=wild(h,54,16);var player=player(h,"Viewer",34,16);
        var field=BlackBeetleMotherEntity.class.getDeclaredField("bossInfo");field.setAccessible(true);
        var paidBar=(ServerBossEvent)field.get(paid);var wildBar=(ServerBossEvent)field.get(wild);
        ready(h,paid,wild,player).thenExecute(()->{
            paid.startSeenByPlayer(player);wild.startSeenByPlayer(player);
            h.assertTrue(paidBar.getPlayers().isEmpty()&&wildBar.getPlayers().contains(player),"actual native Bossbar subscription retained only on wild object");
            paid.setHealth(80);wild.setHealth(50);paid.customServerAiStep();wild.customServerAiStep();
            h.assertTrue(wildBar.getProgress()==.5f&&paidBar.getProgress()==1f,"wild native bar updates while paid callback has no Boss HUD effect");
            paid.stopSeenByPlayer(player);wild.stopSeenByPlayer(player);h.assertTrue(wildBar.getPlayers().isEmpty()&&paidBar.getPlayers().isEmpty(),"wild unsubscribe works normally");
        }).thenSucceed();
    }
    @GameTest(template="mother_native_arena",batch=BATCH,timeoutTicks=160)
    public static void actualWildDeathKeepsKeysShellsAndVocalcordsButPaidDeathGivesNothing(GameTestHelper h){
        room(h);var paid=paid(h,"Death",15,16);var wild=wild(h,54,16);wild.setHealth(1); // Avoid arming wild summon on this separate death-reward positive control.
        var witness=player(h,"DeathWitness",34,16);
        ready(h,paid,wild,witness).thenExecute(()->{
            h.assertTrue(h.getLevel().getNearestPlayer(wild,-1)==witness&&wild.distanceTo(witness)<128&&!wild.isPersistenceRequired()&&wild.isNoAi(),"real local death witness is nearest within native despawn range; original wild stays nonpersistent and NoAI");
            paid.hurt(paid.damageSources().genericKill(),999);wild.hurt(wild.damageSources().genericKill(),999);
        }).thenIdle(35).thenExecute(()->{
            h.assertTrue(paid.isRemoved()&&wild.isRemoved()&&paid.deathTime>=20&&wild.deathTime>=20,"both real native20tick deaths complete");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,area(h));Map<String,Integer> amounts=new HashMap<>();
            for(var item:drops){String key=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem().getItem()).toString();amounts.merge(key,item.getItem().getCount(),Integer::sum);}
            h.assertTrue(amounts.size()==3&&amounts.getOrDefault("pasterdream:shadow_dungeon_key",0)==1&&amounts.getOrDefault("pasterdream:black_beetle_carapace",0)>=3&&amounts.getOrDefault("pasterdream:black_beetle_carapace",0)<=5&&amounts.getOrDefault("pasterdream:black_beetle_vocalcord",0)>=1&&amounts.getOrDefault("pasterdream:black_beetle_vocalcord",0)<=2,"original wild loot unchanged, no paid native rewards: "+amounts);
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,paid.getBoundingBox().inflate(8)).isEmpty()&&h.getLevel().getEntitiesOfClass(ExperienceOrb.class,paid.getBoundingBox().inflate(8)).isEmpty()&&paid.getExperienceReward()==0,"paid corpse yields neither items nor XP");
            drops.forEach(Entity::discard);System.out.println("DREAMSEEKERS_MOTHER_REAL_DEATH_WILD_LOOT_POSITIVE_PAID_NEGATIVE_PASS "+amounts);
        }).thenSucceed();
    }
    @GameTest(template="mother_native_arena",batch=BATCH,timeoutTicks=130)
    public static void portalCallbacksAndForcedVehiclesCannotBypassInstanceRestriction(GameTestHelper h){
        room(h);var paid=paid(h,"Portal",15,16);var boat=EntityType.BOAT.create(h.getLevel());place(h,boat,17,16);
        ready(h,paid,boat).thenExecute(()->{
            h.assertTrue(!paid.startRiding(boat)&&!paid.startRiding(boat,true)&&!paid.isPassenger(),"ordinary and forced/NBT style vehicle hooks fail closed");
            h.assertTrue(!paid.canChangeDimensions()&&paid.isOnPortalCooldown()&&!net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(paid),"actual gateway eligibility and cross-dimension hooks reject paid mother");
            paid.handleInsidePortal(paid.blockPosition());
        }).thenIdle(35).thenExecute(()->{CompoundTag tag=new CompoundTag();paid.saveWithoutId(tag);h.assertTrue(tag.getInt("PortalCooldown")>=0&&!paid.isPassenger()&&paid.level()==h.getLevel(),"natural ticks do not decrement always-on portal guard into a negative saved cooldown");}).thenSucceed();
    }
    private static boolean hasSkillScore(Entity e){var o=e.level().getScoreboard().getObjective("skill");return o!=null&&e.level().getScoreboard().hasPlayerScore(e.getScoreboardName(),o);}
    private static int score(Entity e){var o=e.level().getScoreboard().getObjective("skill");return o==null?0:e.level().getScoreboard().getOrCreatePlayerScore(e.getScoreboardName(),o).getScore();}
    private static void setSkillScore(Entity e,int value){var b=e.level().getScoreboard();var o=b.getObjective("skill");if(o==null)o=b.addObjective("skill",net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,net.minecraft.network.chat.Component.literal("skill"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER);b.getOrCreatePlayerScore(e.getScoreboardName(),o).setScore(value);}
    private static GameTestSequence ready(GameTestHelper h,Entity...entities){
        // Diagnostics only: read the original caller annotation; never change its budget or entity state.
        String test=StackWalker.getInstance().walk(frames->frames
                .filter(f->f.getClassName().equals(BeetleMotherNativeGameTests.class.getName())&&!f.getMethodName().equals("ready"))
                .map(StackWalker.StackFrame::getMethodName).findFirst().orElse("unknown"));
        int budget=-1;
        try{var a=BeetleMotherNativeGameTests.class.getDeclaredMethod(test,GameTestHelper.class).getAnnotation(GameTest.class);if(a!=null)budget=a.timeoutTicks();}
        catch(ReflectiveOperationException ignored){/* Diagnostic metadata missing must not change the test outcome. */}
        final int timeout=budget;
        String[] previous={null};int[] changeLogs={0};boolean[] nearLogged={false},readyLogged={false};
        return h.startSequence().thenWaitUntil(()->{
            StringBuilder detail=new StringBuilder(),state=new StringBuilder();boolean allReady=true;
            for(Entity e:entities){
                Entity byId=h.getLevel().getEntity(e.getId()),byUuid=h.getLevel().getEntity(e.getUUID());
                boolean published=byId==e&&byUuid==e,natural=e instanceof net.minecraft.world.entity.player.Player||e.tickCount>2;
                allReady&=published&&natural;
                var nearest=h.getLevel().getNearestPlayer(e,-1);double distance=nearest==null?Double.NaN:Math.sqrt(e.distanceToSqr(nearest));
                int despawnDistance=e.getType().getCategory().getDespawnDistance();var chunk=e.chunkPosition();
                boolean forced=h.getLevel().getForcedChunks().contains(chunk.toLong());
                String mob=e instanceof Mob m?" noAi="+m.isNoAi()+" persist="+m.isPersistenceRequired()+" customPersist="+m.requiresCustomPersistence():"";
                String health=e instanceof LivingEntity living?" hp="+living.getHealth():"";
                String stable=e.getId()+":"+(byId==e)+":"+(byUuid==e)+":"+natural+":"+e.isRemoved()+":"+e.getRemovalReason()+":"+e.isAlive()+health+mob
                        +":"+(nearest==null?"none":nearest.getUUID())+":"+(distance>despawnDistance)+":"+h.getLevel().getDifficulty()+":"+chunk+":"+forced;
                state.append(stable).append(';');
                detail.append(" [type=").append(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType()))
                        .append(" id=").append(e.getId()).append(" uuid=").append(e.getUUID()).append(" entityTick=").append(e.tickCount)
                        .append(" idLookup=").append(byId==null?"null":byId.getId()+"/"+byId.getUUID()+"/same="+(byId==e))
                        .append(" uuidLookup=").append(byUuid==null?"null":byUuid.getId()+"/"+byUuid.getUUID()+"/same="+(byUuid==e))
                        .append(" removed=").append(e.isRemoved()).append(" reason=").append(e.getRemovalReason()).append(" alive=").append(e.isAlive())
                        .append(" pos=").append(e.position()).append(health).append(mob)
                        .append(" nearestPlayer=").append(nearest==null?"none":nearest.getGameProfile().getName()+"/"+nearest.getUUID()+"/pos="+nearest.position())
                        .append(" distance=").append(distance).append(" despawnDistance=").append(despawnDistance)
                        .append(" difficulty=").append(h.getLevel().getDifficulty()).append(" chunk=").append(chunk).append(" forced=").append(forced).append(']');
            }
            boolean first=previous[0]==null,changed=!first&&!state.toString().equals(previous[0]);
            boolean near=timeout>0&&h.getTick()>=timeout-2&&!nearLogged[0],readyNow=allReady&&!readyLogged[0];
            // At most first + four lifecycle changes + near-timeout + ready (seven lines per test).
            if(first||(changed&&changeLogs[0]<4)||near||readyNow){
                System.out.println("DREAMSEEKERS_MOTHER_NATIVE_READY_DIAGNOSTIC test="+test+" arena="+h.absolutePos(BlockPos.ZERO)
                        +" testTick="+h.getTick()+" gameTime="+h.getLevel().getGameTime()+" timeout="+timeout
                        +" first="+first+" changed="+changed+" nearTimeout="+near+" allReady="+allReady+detail);
                if(changed)changeLogs[0]++;
            }
            previous[0]=state.toString();if(near)nearLogged[0]=true;if(readyNow)readyLogged[0]=true;
            // Keep the original per-entity condition and order; only enrich its failure text with this observation.
            for(Entity e:entities)h.assertTrue((e instanceof net.minecraft.world.entity.player.Player||e.tickCount>2)&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,
                    "actual published ID/UUID and natural native ticks; test="+test+" testTick="+h.getTick()+detail);
        });
    }
    private static String owner(String suffix){String o=PREFIX+suffix;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(o,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(o,500,500,500));return o;}
    private static BeetleMotherUnit paid(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel());e.setOwnerName(owner(suffix));e.setNoAi(true);place(h,e,x,z);return e;}
    private static BeetleUnit paidBeetle(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.BEETLE.get().create(h.getLevel());e.setOwnerName(owner(suffix));e.setNoAi(true);place(h,e,x,z);return e;}
    private static BlackBeetleMotherEntity wild(GameTestHelper h,int x,int z){var e=net.pasterdream.init.PasterdreamModEntities.BLACK_BEETLE_MOTHER.get().create(h.getLevel());e.setNoAi(true);place(h,e,x,z);return e;}
    private static BlackBeetleEntity beetle(GameTestHelper h,int x,int z){var e=net.pasterdream.init.PasterdreamModEntities.BLACK_BEETLE.get().create(h.getLevel());e.setNoAi(true);place(h,e,x,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"native fixture added through actual world");}
    private static FakePlayer player(GameTestHelper h,String suffix,int x,int z){var e=new FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),PREFIX+suffix));e.connection=new RecordingHandler(e);BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.getLevel().addNewPlayer(e);return e;}
    private static final class RecordingHandler extends ServerGamePacketListenerImpl{RecordingHandler(ServerPlayer p){super(p.serverLevel().getServer(),new RecordingConnection(),p);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener l){}}
    private static final class RecordingConnection extends net.minecraft.network.Connection{RecordingConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener l){}}
    private static AABB area(GameTestHelper h){return new AABB(h.absolutePos(BlockPos.ZERO),h.absolutePos(new BlockPos(72,11,34)));}
    private static void room(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=p.getX()>>4;x<=(p.getX()+71)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+33)>>4;z++){var c=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(x,z,true);TICKETS.add(c);}}for(int x=0;x<72;x++)for(int z=0;z<34;z++){h.getLevel().setBlock(p.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);if(x==0||x==71||z==0||z==33)for(int y=1;y<10;y++)h.getLevel().setBlock(p.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);}h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+72,h.getLevel().getMaxBuildHeight(),p.getZ()+34)).forEach(Entity::discard);for(int x=0;x<72;x++)for(int z=0;z<34;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);if(x>0&&x<71&&z>0&&z<33)for(int y=1;y<10;y++)h.getLevel().setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}h.getLevel().getEntitiesOfClass(ItemEntity.class,area(h)).forEach(Entity::discard);}
}
