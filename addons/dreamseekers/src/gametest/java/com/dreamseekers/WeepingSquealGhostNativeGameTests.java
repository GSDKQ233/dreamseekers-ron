// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.unit.*;
import com.dreamseekers.shadow.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.pasterdream.entity.ShadowSquealGhost0Entity;
import java.util.*;
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WeepingSquealGhostNativeGameTests {
    private static final String BATCH="dreamseekers_weeping_squeal_ghost_native",PREFIX="WeepingNative_";
    private static final List<Entity> ENTITIES=new ArrayList<>();
    private static final Set<net.minecraft.world.level.ChunkPos> TICKETS=new HashSet<>();
    private static Difficulty oldDifficulty;
    private static final List<java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent>> JOIN_PROBES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel l){
        for(var probe:JOIN_PROBES)net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(probe);JOIN_PROBES.clear();
        for(Entity e:ENTITIES)l.getScoreboard().resetPlayerScore(e.getScoreboardName(),null);
        if(oldDifficulty!=null){l.getServer().setDifficulty(oldDifficulty,true);oldDifficulty=null;}
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity e:ENTITIES)if(!e.isRemoved())e.discard();ENTITIES.clear();
        for(var c:TICKETS)l.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @AfterBatch(batch="dreamseekers_weeping_squeal_ghost_peaceful") public static void cleanupPeaceful(ServerLevel l){cleanup(l);}
    @GameTest(template="weeping_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void nativeDiveAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(GameTestHelper h){
        floor(h);var paid=paid(h,"Goals",5,5);var wild=wild(h,18,18);
        h.assertTrue(paid.getMoveControl() instanceof FlyingMoveControl&&paid.getNavigation() instanceof FlyingPathNavigation,"genuine flight controls");
        h.assertTrue(paid.getBbWidth()==.8f&&paid.getBbHeight()==1.3f&&paid.getMobType()==MobType.UNDEFINED,"exact original dimensions/type, not invented undead or phasing");
        h.assertTrue(wild.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal().getClass().getName().startsWith("net.pasterdream.entity.ShadowSquealGhost0Entity$")),"original native dive remains on wild positive control");
        h.assertTrue(paid.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal().getClass().getName().startsWith("net.pasterdream.")||g.getGoal() instanceof MeleeAttackGoal),"all original attack/random goals replaced, not merely Player selection");
        h.assertTrue(paid.targetSelector.getAvailableGoals().size()==1&&paid.getAttackGoal() instanceof com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal && paid.getAttackBuildingGoal()==null && !paid.canAttackBuildings(),"single RTS selected-target and APS goal");
        h.assertTrue(!(wild instanceof Unit)&&!paid.noPhysics&&paid.fireImmune()&&wild.fireImmune(),"wild not converted; native EntityType fire immunity deliberately retained");
        var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,DreamseekersMod.paster("shadow_mob"));
        h.assertTrue(!paid.getType().is(tag),"paid type not opted into unfiltered original shadow tag effects");
        h.succeed();
    }
    @GameTest(template="weeping_unit_arena",batch=BATCH,timeoutTicks=160)
    public static void documentedNativeImmunitiesRemainButRealArrowsDamageBoth(GameTestHelper h){
        floor(h);var paid=paid(h,"Damage",12,6);var wild=wild(h,12,17);var player=player(h,"Damage");
        for(LivingEntity e:List.of(paid,wild)){
            float hp=e.getHealth();for(var ds:List.of(e.damageSources().inFire(),e.damageSources().fall(),e.damageSources().cactus(),e.damageSources().drown())){
                e.invulnerableTime=0;h.assertTrue(!e.hurt(ds,2)&&e.getHealth()==hp,"documented native environment immunity retained");
            }
            ThrownPotion potion=new ThrownPotion(h.getLevel(),player);AreaEffectCloud cloud=new AreaEffectCloud(h.getLevel(),e.getX(),e.getY(),e.getZ());
            h.assertTrue(!e.hurt(e.damageSources().indirectMagic(potion,player),2)&&!e.hurt(e.damageSources().indirectMagic(cloud,player),2),"direct potion/cloud damage rejected, not a claim of all status immunity");
            h.assertTrue(e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20,0)),"ordinary potion status is not falsely declared immune");e.removeAllEffects();
            h.assertTrue(e.hurt(e.damageSources().generic(),1)&&e.getHealth()<hp,"generic positive damage retained");e.invulnerableTime=0;
        }
        float[] health={0,0};
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,wild),"real arrow victims published")).thenExecute(()->{
            health[0]=paid.getHealth();health[1]=wild.getHealth();
            arrow(h,player,paid);arrow(h,player,wild);
        }).thenIdle(35).thenExecute(()->h.assertTrue(paid.getHealth()<health[0]&&wild.getHealth()<health[1],"actual flying arrows damage paid and original entities")).thenSucceed();
    }
    @GameTest(template="weeping_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void interactionAndRealDeathCannotCreatePaidLootOrExperience(GameTestHelper h){
        floor(h);var paid=paid(h,"Death",6,6);var wild=wild(h,18,18);var p=player(h,"Death");
        for(Item item:List.of(Items.LEAD,Items.WHEAT,ForgeRegistries.ITEMS.getValue(DreamseekersMod.paster("shadow_squeal_ghost_0_spawn_egg")))){
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item,5));paid.mobInteract(p,InteractionHand.MAIN_HAND);paid.interact(p,InteractionHand.MAIN_HAND);p.interactOn(paid,InteractionHand.MAIN_HAND);
            h.assertTrue(p.getMainHandItem().getCount()==5&&!paid.isLeashed(),"paid interaction does not breed, leash, reward or consume item");
        }p.getInventory().clearContent();
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,wild),"real death fixtures published")).thenExecute(()->{
            paid.hurt(paid.damageSources().playerAttack(p),999);wild.hurt(wild.damageSources().playerAttack(p),999);
        }).thenIdle(35).thenExecute(()->{
                h.assertTrue(paid.isRemoved()&&paid.deathTime>=20&&wild.isRemoved()&&wild.deathTime>=20,"actual native death windows advance");
                h.assertTrue(h.getLevel().getEntitiesOfClass(ExperienceOrb.class,paid.getBoundingBox().inflate(4)).isEmpty()&&h.getLevel().getEntitiesOfClass(ItemEntity.class,paid.getBoundingBox().inflate(4)).isEmpty(),"no paid native loot or XP");
                h.assertTrue(!h.getLevel().getEntitiesOfClass(ExperienceOrb.class,wild.getBoundingBox().inflate(4)).isEmpty(),"wild original XP positive control remains");
                h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class,wild.getBoundingBox().inflate(4)).isEmpty(),"wild weighted own loot table still drops, without demanding rare3percent wand");
        }).thenSucceed();
    }
    @GameTest(template="weeping_unit_arena",batch="dreamseekers_weeping_squeal_ghost_peaceful",timeoutTicks=130)
    public static void paidPersistenceSurvivesPeacefulWhileOriginalMonsterDespawns(GameTestHelper h){
        floor(h);var paid=paid(h,"Peaceful",6,6);var wild=wild(h,18,18);
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,wild),"normal simulation before peaceful")).thenExecute(()->{
            oldDifficulty=h.getLevel().getDifficulty();h.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL,true);
        }).thenIdle(20).thenExecute(()->{
            h.getLevel().getServer().setDifficulty(oldDifficulty,true);oldDifficulty=null;
            h.assertTrue(paid.isAlive()&&!paid.isRemoved()&&paid.isPersistenceRequired()&&wild.isRemoved(),"only paid subtype persists in peaceful; NoAI alone is not a workaround");
        }).thenSucceed();
    }
    @GameTest(template="weeping_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(GameTestHelper h){
        floor(h);var paid=paid(h,"Portal",6,6);var wild=wild(h,18,18);var boat=EntityType.BOAT.create(h.getLevel());place(h,boat,12,12);
        h.assertTrue(!paid.startRiding(boat)&&!paid.startRiding(boat,true)&&paid.getVehicle()==null,"ordinary and forced mounting cannot bypass gateway through root vehicle");
        h.assertTrue(!net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(paid)&&net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(wild),"native gateway eligibility isolates paid only");
        paid.handleInsidePortal(paid.blockPosition());h.assertTrue(!paid.canChangeDimensions()&&!paid.canUsePortal(),"true portal callback and dimension hooks are closed");
        CompoundTag tag=new CompoundTag();paid.saveWithoutId(tag);tag.putBoolean("NoAI",true);tag.putBoolean("NoGravity",false);
        var loaded=DreamseekersMod.WEEPING_SQUEAL_GHOST_UNIT.get().create(h.getLevel());loaded.load(tag);
        h.assertTrue(!loaded.isNoAi()&&loaded.isNoGravity()&&loaded.isPersistenceRequired()&&loaded.getOwnerName().equals(paid.getOwnerName()),"ordinary NBT retains owner but restores active flight policy");
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid),"portal actor real ticks")).thenIdle(30).thenExecute(()->{
            CompoundTag out=new CompoundTag();paid.saveWithoutId(out);h.assertTrue(out.getInt("PortalCooldown")>=0,"eligibility override does not decrement cooldown negative");h.succeed();
        });
    }
    @GameTest(template="weeping_unit_arena",batch=BATCH,timeoutTicks=140)
    public static void rejectedWildDamageNaturallySpawnsExactlyThreeChildrenAndResetsAtThirty(GameTestHelper h){
        floor(h);var wild=wild(h,6,12);score(h,wild,0);List<Entity> children=observeChildren(h);
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,wild),"real native summon source published")).thenExecute(()->{
            float hp=wild.getHealth();h.assertTrue(!wild.hurt(wild.damageSources().inFire(),2)&&wild.getHealth()==hp,"native rejected fire still executes pre-hurt summon");
            h.assertTrue(score(wild)==1&&wild.getSyncedAnimation().equals("skill")&&children.isEmpty(),"immediate native score/animation, no instantaneous children");
        }).thenIdle(6).thenExecute(()->h.assertTrue(children.isEmpty(),"not before8tick delay"))
          .thenIdle(4).thenExecute(()->assertChildren(h,children,1,0))
          .thenIdle(8).thenExecute(()->assertChildren(h,children,2,0))
          .thenIdle(8).thenExecute(()->assertChildren(h,children,2,1))
          .thenIdle(10).thenExecute(()->{
              assertChildren(h,children,2,1);h.assertTrue(score(wild)==0,"actual native30tick score reset");
              h.assertTrue(children.stream().allMatch(e->e.isAddedToWorld()&&!e.isRemoved()&&h.getLevel().getEntity(e.getUUID())==e&&!(e instanceof Unit)),"three genuinely stored unowned original entities, not queued promises");
              System.out.println("WEEPING_WILD_8_16_24_SPAWN_30_RESET_PASS uuids="+children.stream().map(Entity::getUUID).toList());
          }).thenSucceed();
    }
    @GameTest(template="weeping_unit_arena",batch=BATCH,timeoutTicks=150)
    public static void removedWildStillSummonsButPaidBlankRestoredRemovedSourcesNeverQueue(GameTestHelper h){
        floor(h);var wild=wild(h,6,12);score(h,wild,0);var paid=paid(h,"NoFree",18,12);paid.setNoAi(true);List<Entity> children=observeChildren(h);
        WeepingSquealGhostUnit[] restored={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,wild)&&published(h,paid),"native/paid source publication")).thenExecute(()->{
            h.assertTrue(!hasScore(paid),"fresh paid scoreboard row absent");paid.setAnimation("empty");
            h.assertTrue(!paid.hurt(paid.damageSources().inFire(),2),"paid native fire immunity retained");paid.hurt(paid.damageSources().generic(),1);
            paid.setOwnerName("");net.pasterdream.procedures.ShadowSquealGhost0Pr0Procedure.execute(h.getLevel(),paid.getX(),paid.getY(),paid.getZ(),paid);
            CompoundTag tag=new CompoundTag();paid.saveWithoutId(tag);restored[0]=DreamseekersMod.WEEPING_SQUEAL_GHOST_UNIT.get().create(h.getLevel());restored[0].load(tag);
            net.pasterdream.procedures.ShadowSquealGhost0Pr0Procedure.execute(h.getLevel(),paid.getX(),paid.getY(),paid.getZ(),restored[0]);
            paid.discard();net.pasterdream.procedures.ShadowSquealGhost0Pr0Procedure.execute(h.getLevel(),paid.getX(),paid.getY(),paid.getZ(),paid);
            h.assertTrue(!hasScore(paid)&&!hasScore(restored[0])&&paid.getSyncedAnimation().equals("empty"),"type guard precedes every score/animation/queue side effect despite blank/removed/loaded identity");
            wild.hurt(wild.damageSources().inFire(),2);h.assertTrue(score(wild)==1,"wild source actually schedules before removal");wild.discard();
        }).thenIdle(38).thenExecute(()->{
            assertChildren(h,children,2,1);h.assertTrue(children.stream().allMatch(e->Math.abs(e.getX()-wild.getX())<2&&Math.abs(e.getZ()-wild.getZ())<2&&e.isAddedToWorld()),"all three real children arise only at removed wild source; none at paid source");
            h.assertTrue(score(wild)==0&&!hasScore(paid)&&!hasScore(restored[0]),"wild world-only callbacks survive source removal; paid never queued them");
            System.out.println("WEEPING_REMOVED_WILD_POSITIVE_PAID_ALL_IDENTITIES_CLOSED_PASS");
        }).thenSucceed();
    }
    @GameTest(template="weeping_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void exactStaleResetCallbackNeverWritesPaidScoreButWildStillResets(GameTestHelper h)throws Exception{
        floor(h);var paid=paid(h,"Reset",6,6);var wild=wild(h,18,18);
        var callback=net.pasterdream.procedures.ShadowSquealGhost0Pr0Procedure.class.getDeclaredMethod("lambda$execute$3",Entity.class);callback.setAccessible(true);
        // Real vanilla removal clears non-player scoreboard rows independently of the native callback.
        score(h,paid,9);callback.invoke(null,paid);h.assertTrue(score(paid)==9,"live paid callback cannot reset score");
        paid.setOwnerName("");paid.discard();h.assertTrue(!hasScore(paid),"real vanilla discard clears score before any stale callback");
        score(h,paid,9);score(h,wild,9);h.assertTrue(score(paid)==9&&score(wild)==9,"explicit post-removal pre-callback baseline");
        callback.invoke(null,paid);callback.invoke(null,wild);
        h.assertTrue(score(paid)==9&&score(wild)==0,"exact stale callback preserves removed paid baseline while wild resets");
        System.out.println("WEEPING_DISCARD_CLEARS_SCORE_AND_POST_REMOVAL_CALLBACK_GUARD_PASS paid="+score(paid)+" wild="+score(wild));h.succeed();
    }
    private static List<Entity> observeChildren(GameTestHelper h){
        var p=h.absolutePos(BlockPos.ZERO);var box=new AABB(p,p.offset(24,10,24));List<Entity> children=new ArrayList<>();
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> probe=event->{Entity e=event.getEntity();
            if(event.getLevel()==h.getLevel()&&box.contains(e.position())&&(e.getType()==net.pasterdream.init.PasterdreamModEntities.SHADOW_GHOST.get()||e.getType()==net.pasterdream.init.PasterdreamModEntities.SHADOW_SQUEAL_GHOST.get())){
                children.add(e);ENTITIES.add(e);((Mob)e).setNoAi(true); // Only fixture AI after original spawn; never cancel/add/replace the join.
            }};
        JOIN_PROBES.add(probe);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,net.minecraftforge.event.entity.EntityJoinLevelEvent.class,probe);return children;
    }
    private static void assertChildren(GameTestHelper h,List<Entity> children,int ghosts,int squeals){
        h.assertTrue(children.size()==ghosts+squeals&&children.stream().map(Entity::getUUID).distinct().count()==children.size(),"exact newly joined UUID count "+children.size());
        h.assertTrue(children.stream().filter(e->e.getType()==net.pasterdream.init.PasterdreamModEntities.SHADOW_GHOST.get()).count()==ghosts&&children.stream().filter(e->e.getType()==net.pasterdream.init.PasterdreamModEntities.SHADOW_SQUEAL_GHOST.get()).count()==squeals,"exact original child registry types");
    }
    private static boolean hasScore(Entity e){var o=e.level().getScoreboard().getObjective("switch");return o!=null&&e.level().getScoreboard().hasPlayerScore(e.getScoreboardName(),o);}
    private static int score(Entity e){var o=e.level().getScoreboard().getObjective("switch");return o==null?0:e.level().getScoreboard().getOrCreatePlayerScore(e.getScoreboardName(),o).getScore();}
    private static void score(GameTestHelper h,Entity e,int value){var b=h.getLevel().getScoreboard();var o=b.getObjective("switch");if(o==null)o=b.addObjective("switch",net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,net.minecraft.network.chat.Component.literal("switch"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER);b.getOrCreatePlayerScore(e.getScoreboardName(),o).setScore(value);}
    private static WeepingSquealGhostUnit paid(GameTestHelper h,String suffix,int x,int z){String owner=PREFIX+suffix;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(owner,500,500,500));var e=DreamseekersMod.WEEPING_SQUEAL_GHOST_UNIT.get().create(h.getLevel());e.setOwnerName(owner);place(h,e,x,z);return e;}
    private static ShadowSquealGhost0Entity wild(GameTestHelper h,int x,int z){var e=(ShadowSquealGhost0Entity)ForgeRegistries.ENTITY_TYPES.getValue(DreamseekersMod.paster("shadow_squeal_ghost_0")).create(h.getLevel());e.setNoAi(true);place(h,e,x,z);score(h,e,1);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);ENTITIES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"actual fixture enters world");}
    private static FakePlayer player(GameTestHelper h,String n){return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes((PREFIX+n).getBytes()),PREFIX+n));}
    private static void arrow(GameTestHelper h,FakePlayer p,LivingEntity victim){var a=new Arrow(h.getLevel(),p);a.setBaseDamage(3);a.setNoGravity(true);a.moveTo(victim.getX()-5,victim.getY()+.5,victim.getZ());a.setDeltaMovement(.8,0,0);ENTITIES.add(a);h.assertTrue(h.getLevel().addFreshEntity(a),"physical arrow actually spawned");}
    private static boolean published(GameTestHelper h,Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}
    private static void floor(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);for(int i=-1;i<=24;i++)for(int y=1;y<=10;y++){h.getLevel().setBlock(p.offset(-1,y,i),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.offset(24,y,i),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.offset(i,y,-1),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.offset(i,y,24),Blocks.STONE.defaultBlockState(),2);}for(int x=p.getX()>>4;x<=(p.getX()+23)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+23)>>4;z++){var c=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(x,z,true);TICKETS.add(c);}}for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<10;y++)h.getLevel().setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}}
}
