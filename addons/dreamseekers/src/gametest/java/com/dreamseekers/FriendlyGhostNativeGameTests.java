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
import net.pasterdream.entity.FriendlyGhostEntity;
import java.util.*;
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FriendlyGhostNativeGameTests {
    private static final String BATCH="dreamseekers_friendly_ghost_native",PREFIX="FriendlyNative_";
    private static final List<Entity> ENTITIES=new ArrayList<>();
    private static final Set<net.minecraft.world.level.ChunkPos> TICKETS=new HashSet<>();
    private static Difficulty oldDifficulty;
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel l){
        if(oldDifficulty!=null){l.getServer().setDifficulty(oldDifficulty,true);oldDifficulty=null;}
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity e:ENTITIES)if(!e.isRemoved())e.discard();ENTITIES.clear();
        for(var c:TICKETS)l.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @AfterBatch(batch="dreamseekers_friendly_ghost_peaceful") public static void cleanupPeaceful(ServerLevel l){cleanup(l);}
    @GameTest(template="friendly_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void nativeOwnerAndRandomGoalsAreReplacedWithoutChangingWildIdentity(GameTestHelper h){
        floor(h);var paid=paid(h,"Goals",5,5);var wild=wild(h,18,18);
        h.assertTrue(paid.getMoveControl() instanceof FlyingMoveControl&&paid.getNavigation() instanceof FlyingPathNavigation,"genuine flight controls");
        h.assertTrue(paid.getBbWidth()==.7f&&paid.getBbHeight()==1.2f&&paid.getMobType()==MobType.UNDEFINED,"exact original dimensions/type, not invented undead or phasing");
        h.assertTrue(wild.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal().getClass().getName().startsWith("net.pasterdream.entity.FriendlyGhostEntity$")),"original native owner/ranged AI remains on wild positive control");
        h.assertTrue(paid.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal().getClass().getName().startsWith("net.pasterdream.")||g.getGoal() instanceof MeleeAttackGoal),"all original attack/random goals replaced, not merely Player selection");
        h.assertTrue(paid.targetSelector.getAvailableGoals().size()==1&&paid.getAttackGoal() instanceof com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal && paid.getAttackBuildingGoal()==null && !paid.canAttackBuildings(),"single RTS selected-target and APS goal");
        h.assertTrue(!(wild instanceof Unit)&&!paid.noPhysics&&paid.fireImmune()&&wild.fireImmune(),"wild not converted; native EntityType fire immunity deliberately retained");
        var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,DreamseekersMod.paster("shadow_mob"));
        h.assertTrue(!paid.getType().is(tag),"paid type not opted into unfiltered original shadow tag effects");
        h.succeed();
    }
    @GameTest(template="friendly_unit_arena",batch=BATCH,timeoutTicks=160)
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
    @GameTest(template="friendly_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void interactionAndRealDeathCannotCreatePaidLootOrExperience(GameTestHelper h){
        floor(h);var paid=paid(h,"Death",6,6);var wild=wild(h,18,18);var p=player(h,"Death");
        for(Item item:List.of(Items.LEAD,Items.WHEAT,ForgeRegistries.ITEMS.getValue(DreamseekersMod.paster("friendly_ghost_spawn_egg")))){
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item,5));paid.mobInteract(p,InteractionHand.MAIN_HAND);paid.interact(p,InteractionHand.MAIN_HAND);p.interactOn(paid,InteractionHand.MAIN_HAND);
            h.assertTrue(p.getMainHandItem().getCount()==5&&!paid.isLeashed(),"paid interaction does not breed, leash, reward or consume item");
        }p.getInventory().clearContent();
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,wild),"real death fixtures published")).thenExecute(()->{
            paid.hurt(paid.damageSources().playerAttack(p),999);wild.hurt(wild.damageSources().playerAttack(p),999);
        }).thenIdle(35).thenExecute(()->{
                h.assertTrue(paid.isRemoved()&&paid.deathTime>=20&&wild.isRemoved()&&wild.deathTime>=20,"actual native death windows advance");
                h.assertTrue(h.getLevel().getEntitiesOfClass(ExperienceOrb.class,paid.getBoundingBox().inflate(4)).isEmpty()&&h.getLevel().getEntitiesOfClass(ItemEntity.class,paid.getBoundingBox().inflate(4)).isEmpty(),"no paid native loot or XP");
                h.assertTrue(!h.getLevel().getEntitiesOfClass(ExperienceOrb.class,wild.getBoundingBox().inflate(4)).isEmpty(),"wild original XP positive control remains");
        }).thenSucceed();
    }
    @GameTest(template="friendly_unit_arena",batch="dreamseekers_friendly_ghost_peaceful",timeoutTicks=130)
    public static void paidAndOriginalAnimalPersistInPeacefulWithoutConversion(GameTestHelper h){
        floor(h);var paid=paid(h,"Peaceful",6,6);var wild=wild(h,18,18);
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,wild),"normal simulation before peaceful")).thenExecute(()->{
            oldDifficulty=h.getLevel().getDifficulty();h.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL,true);
        }).thenIdle(20).thenExecute(()->{
            h.getLevel().getServer().setDifficulty(oldDifficulty,true);oldDifficulty=null;
            h.assertTrue(paid.isAlive()&&!paid.isRemoved()&&paid.isPersistenceRequired()&&wild.isAlive()&&!wild.isRemoved(),"TamableAnimal wild remains in peaceful too; persistence does not convert it");
        }).thenSucceed();
    }
    @GameTest(template="friendly_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(GameTestHelper h){
        floor(h);var paid=paid(h,"Portal",6,6);var wild=wild(h,18,18);var boat=EntityType.BOAT.create(h.getLevel());place(h,boat,12,12);
        h.assertTrue(!paid.startRiding(boat)&&!paid.startRiding(boat,true)&&paid.getVehicle()==null,"ordinary and forced mounting cannot bypass gateway through root vehicle");
        h.assertTrue(!net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(paid)&&net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(wild),"native gateway eligibility isolates paid only");
        paid.handleInsidePortal(paid.blockPosition());h.assertTrue(!paid.canChangeDimensions()&&!paid.canUsePortal(),"true portal callback and dimension hooks are closed");
        CompoundTag tag=new CompoundTag();paid.saveWithoutId(tag);tag.putBoolean("NoAI",true);tag.putBoolean("NoGravity",false);
        var loaded=DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(h.getLevel());loaded.load(tag);
        h.assertTrue(!loaded.isNoAi()&&loaded.isNoGravity()&&loaded.isPersistenceRequired()&&loaded.getOwnerName().equals(paid.getOwnerName()),"ordinary NBT retains owner but restores active flight policy");
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid),"portal actor real ticks")).thenIdle(30).thenExecute(()->{
            CompoundTag out=new CompoundTag();paid.saveWithoutId(out);h.assertTrue(out.getInt("PortalCooldown")>=0,"eligibility override does not decrement cooldown negative");h.succeed();
        });
    }
    @GameTest(template="friendly_unit_arena",batch=BATCH,timeoutTicks=100)
    public static void directTameAndHostilePetNbtCannotReplaceRtsOwnerOrEnableBreeding(GameTestHelper h){
        floor(h);var paid=paid(h,"PetNbt",6,6);var wild=wild(h,18,18);var player=player(h,"ForeignPet");String owner=paid.getOwnerName();long epoch=paid.rts().ownershipRevision;
        paid.tame(player);paid.setTame(true);paid.setOwnerUUID(player.getUUID());paid.setOrderedToSit(true);paid.setInSittingPose(true);paid.setAge(-24000);paid.setInLove(player);paid.setInLoveTime(600);
        assertNoPet(h,paid,owner);h.assertTrue(paid.rts().ownershipRevision==epoch,"native pet calls cannot change RTS owner epoch");
        wild.tame(player);h.assertTrue(wild.isTame()&&player.getUUID().equals(wild.getOwnerUUID()),"wild original tame remains enabled");
        CompoundTag tag=new CompoundTag();paid.saveWithoutId(tag);tag.putUUID("Owner",player.getUUID());tag.putBoolean("Sitting",true);tag.putInt("Age",-24000);tag.putInt("ForcedAge",123);tag.putInt("InLove",600);tag.putUUID("LoveCause",player.getUUID());
        var copy=tag.copy();var loaded=DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(h.getLevel());loaded.load(tag);assertNoPet(h,loaded,owner);
        h.assertTrue(tag.equals(copy),"load sanitizes a copy, not caller-owned NBT");
        CompoundTag oldName=tag.copy();oldName.putString("Owner","UnknownNativeOwner");loaded.load(oldName);assertNoPet(h,loaded,owner);
        h.assertTrue(!paid.canMate(wild)&&!paid.isFood(new ItemStack(Items.WHEAT))&&paid.getBreedOffspring(h.getLevel(),wild)==null,"no food, mate or direct offspring factory");
        java.util.Set<UUID> before=new java.util.HashSet<>();int[] units={0};
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,wild),"breeding-boundary actors published")).thenExecute(()->{
            h.getLevel().getEntitiesOfClass(FriendlyGhostEntity.class,paid.getBoundingBox().inflate(8)).forEach(e->before.add(e.getUUID()));units[0]=UnitServerEvents.getAllUnits().size();paid.spawnChildFromBreeding(h.getLevel(),wild);
        }).thenIdle(5).thenExecute(()->{
            var after=h.getLevel().getEntitiesOfClass(FriendlyGhostEntity.class,paid.getBoundingBox().inflate(8)).stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toSet());
            h.assertTrue(before.equals(after)&&UnitServerEvents.getAllUnits().size()==units[0],"no free native non-RTS or paid child after real ticks");
        }).thenSucceed();
    }
    @GameTest(template="friendly_unit_arena",batch=BATCH,timeoutTicks=130)
    public static void originalWandImmediateAreaExcludesPaidButRetainsWildMagicDamage(GameTestHelper h){
        floor(h);var paid=paid(h,"WandArea",10,10);var blank=paid(h,"WandBlank",10,11);blank.setOwnerName("");var wild=wild(h,11,10);var player=player(h,"WandArea");
        var magic=player.getAttribute(net.pasterdream.init.PasterdreamModAttributes.MAGICPOWER.get());h.assertTrue(magic!=null,"actual original player magic attribute");double previous=magic.getBaseValue();
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,blank)&&published(h,wild),"all targets visible to real query")).thenExecute(()->{
            float a=paid.getHealth(),b=blank.getHealth(),w=wild.getHealth();magic.setBaseValue(1);
            try{net.pasterdream.procedures.SquealWaveWandPr3Procedure.execute(h.getLevel(),paid.getX(),paid.getY()+.4,paid.getZ(),wild,player);}finally{magic.setBaseValue(previous);}
            h.assertTrue(paid.getHealth()==a&&blank.getHealth()==b&&wild.getHealth()<w,"exact native immediate AOE skips paid identities including blank owner, wild damage remains");
            h.assertTrue(paid.hurt(paid.damageSources().magic(),1)&&paid.getHealth()<a,"ordinary MAGIC remains damageable; no broad immunity workaround");
        }).thenSucceed();
    }
    @GameTest(template="friendly_unit_arena",batch=BATCH,timeoutTicks=150)
    public static void originalOneTickWandCallbackSpawnsAndTamesWildButNotPaid(GameTestHelper h)throws Exception{
        floor(h);var paid=paid(h,"WandCallback",10,10);var blank=paid(h,"CallbackBlank",10,11);blank.setOwnerName("");var wild=wild(h,11,10);var player=player(h,"CallbackEnemy");String owner=paid.getOwnerName();long epoch=paid.rts().ownershipRevision;
        var callback=net.pasterdream.procedures.SquealWaveWandPr3Procedure.class.getDeclaredMethod("lambda$execute$4",net.minecraft.world.level.LevelAccessor.class,double.class,double.class,double.class,Entity.class);callback.setAccessible(true);
        java.util.Set<UUID> before=new java.util.HashSet<>();long[] queued={0},ran={-1};
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,blank)&&published(h,wild),"callback neighborhood published")).thenExecute(()->{
            h.getLevel().getEntitiesOfClass(FriendlyGhostEntity.class,paid.getBoundingBox().inflate(4)).forEach(e->before.add(e.getUUID()));queued[0]=h.getLevel().getGameTime();
            // Deterministic callback-boundary test: use the original server queue and exact
            // original callback, not a claim that random curio activation was exercised.
            net.pasterdream.PasterdreamMod.queueServerWork(1,()->{try{ran[0]=h.getLevel().getGameTime();callback.invoke(null,h.getLevel(),paid.getX(),paid.getY(),paid.getZ(),player);}catch(Exception e){throw new RuntimeException(e);}});
            h.assertTrue(!wild.isTame(),"callback has not synchronously tamed wild");
        }).thenIdle(6).thenExecute(()->{
            h.assertTrue(ran[0]>=queued[0]&&wild.isTame()&&player.getUUID().equals(wild.getOwnerUUID()),"actual queued original callback tames wild neighbor");
            var spawned=h.getLevel().getEntitiesOfClass(FriendlyGhostEntity.class,paid.getBoundingBox().inflate(4)).stream().filter(e->!before.contains(e.getUUID())).toList();
            ENTITIES.addAll(spawned);h.assertTrue(spawned.size()==1&&spawned.get(0).getClass()==FriendlyGhostEntity.class&&spawned.get(0).isTame()&&player.getUUID().equals(spawned.get(0).getOwnerUUID()),"exactly one new original ghost spawned and tamed by original callback");
            assertNoPet(h,paid,owner);assertNoPet(h,blank,"");h.assertTrue(paid.rts().ownershipRevision==epoch,"delayed callback cannot steal RTS ownership");
        }).thenSucceed();
    }
    private static void assertNoPet(GameTestHelper h,FriendlyGhostUnit e,String owner){
        h.assertTrue(e.getOwnerName().equals(owner)&&!e.isTame()&&e.getOwnerUUID()==null&&e.getOwner()==null&&!e.isOrderedToSit()&&!e.isInSittingPose()&&!e.isBaby()&&e.getAge()==0&&!e.isInLove(),"paid owner stays separate from all vanilla pet/age/love state");
    }
    private static FriendlyGhostUnit paid(GameTestHelper h,String suffix,int x,int z){String owner=PREFIX+suffix;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(owner,500,500,500));var e=DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(h.getLevel());e.setOwnerName(owner);place(h,e,x,z);return e;}
    private static FriendlyGhostEntity wild(GameTestHelper h,int x,int z){var e=(FriendlyGhostEntity)ForgeRegistries.ENTITY_TYPES.getValue(DreamseekersMod.paster("friendly_ghost")).create(h.getLevel());e.setNoAi(true);place(h,e,x,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);ENTITIES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"actual fixture enters world");}
    private static FakePlayer player(GameTestHelper h,String n){return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes((PREFIX+n).getBytes()),PREFIX+n));}
    private static void arrow(GameTestHelper h,FakePlayer p,LivingEntity victim){var a=new Arrow(h.getLevel(),p);a.setBaseDamage(3);a.setNoGravity(true);a.moveTo(victim.getX()-5,victim.getY()+.5,victim.getZ());a.setDeltaMovement(.8,0,0);ENTITIES.add(a);h.assertTrue(h.getLevel().addFreshEntity(a),"physical arrow actually spawned");}
    private static boolean published(GameTestHelper h,Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}
    private static void floor(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);for(int i=-1;i<=24;i++)for(int y=1;y<=10;y++){h.getLevel().setBlock(p.offset(-1,y,i),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.offset(24,y,i),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.offset(i,y,-1),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.offset(i,y,24),Blocks.STONE.defaultBlockState(),2);}for(int x=p.getX()>>4;x<=(p.getX()+23)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+23)>>4;z++){var c=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(x,z,true);TICKETS.add(c);}}for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<10;y++)h.getLevel().setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}}
}
