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
import net.pasterdream.entity.ShadowSquealGhostEntity;
import java.util.*;
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShadowSquealGhostNativeGameTests {
    private static final String BATCH="dreamseekers_shadow_squeal_ghost_native",PREFIX="SquealNative_";
    private static final List<Entity> ENTITIES=new ArrayList<>();
    private static final Set<net.minecraft.world.level.ChunkPos> TICKETS=new HashSet<>();
    private static Difficulty oldDifficulty;
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel l){
        if(oldDifficulty!=null){l.getServer().setDifficulty(oldDifficulty,true);oldDifficulty=null;}
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity e:ENTITIES)if(!e.isRemoved())e.discard();ENTITIES.clear();
        for(var c:TICKETS)l.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @AfterBatch(batch="dreamseekers_shadow_squeal_ghost_peaceful") public static void cleanupPeaceful(ServerLevel l){cleanup(l);}
    @GameTest(template="squeal_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void nativeDiveAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(GameTestHelper h){
        floor(h);var paid=paid(h,"Goals",5,5);var wild=wild(h,18,18);
        h.assertTrue(paid.getMoveControl() instanceof FlyingMoveControl&&paid.getNavigation() instanceof FlyingPathNavigation,"genuine flight controls");
        h.assertTrue(paid.getBbWidth()==.7f&&paid.getBbHeight()==1.2f&&paid.getMobType()==MobType.UNDEFINED,"exact original dimensions/type, not invented undead or phasing");
        h.assertTrue(wild.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal().getClass().getName().startsWith("net.pasterdream.entity.ShadowSquealGhostEntity$")),"original native dive remains on wild positive control");
        h.assertTrue(paid.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal().getClass().getName().startsWith("net.pasterdream.")||g.getGoal() instanceof MeleeAttackGoal),"all original attack/random goals replaced, not merely Player selection");
        h.assertTrue(paid.targetSelector.getAvailableGoals().size()==1&&paid.getAttackGoal() instanceof com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal && paid.getAttackBuildingGoal()==null && !paid.canAttackBuildings(),"single RTS selected-target and APS goal");
        h.assertTrue(!(wild instanceof Unit)&&!paid.noPhysics&&paid.fireImmune()&&wild.fireImmune(),"wild not converted; native EntityType fire immunity deliberately retained");
        var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,DreamseekersMod.paster("shadow_mob"));
        h.assertTrue(!paid.getType().is(tag),"paid type not opted into unfiltered original shadow tag effects");
        h.succeed();
    }
    @GameTest(template="squeal_unit_arena",batch=BATCH,timeoutTicks=160)
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
    @GameTest(template="squeal_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void interactionAndRealDeathCannotCreatePaidLootOrExperience(GameTestHelper h){
        floor(h);var paid=paid(h,"Death",6,6);var wild=wild(h,18,18);var p=player(h,"Death");
        for(Item item:List.of(Items.LEAD,Items.WHEAT,ForgeRegistries.ITEMS.getValue(DreamseekersMod.paster("shadow_squeal_ghost_spawn_egg")))){
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
    @GameTest(template="squeal_unit_arena",batch="dreamseekers_shadow_squeal_ghost_peaceful",timeoutTicks=130)
    public static void paidPersistenceSurvivesPeacefulWhileOriginalMonsterDespawns(GameTestHelper h){
        floor(h);var paid=paid(h,"Peaceful",6,6);var wild=wild(h,18,18);
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,wild),"normal simulation before peaceful")).thenExecute(()->{
            oldDifficulty=h.getLevel().getDifficulty();h.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL,true);
        }).thenIdle(20).thenExecute(()->{
            h.getLevel().getServer().setDifficulty(oldDifficulty,true);oldDifficulty=null;
            h.assertTrue(paid.isAlive()&&!paid.isRemoved()&&paid.isPersistenceRequired()&&wild.isRemoved(),"only paid subtype persists in peaceful; NoAI alone is not a workaround");
        }).thenSucceed();
    }
    @GameTest(template="squeal_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(GameTestHelper h){
        floor(h);var paid=paid(h,"Portal",6,6);var wild=wild(h,18,18);var boat=EntityType.BOAT.create(h.getLevel());place(h,boat,12,12);
        h.assertTrue(!paid.startRiding(boat)&&!paid.startRiding(boat,true)&&paid.getVehicle()==null,"ordinary and forced mounting cannot bypass gateway through root vehicle");
        h.assertTrue(!net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(paid)&&net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(wild),"native gateway eligibility isolates paid only");
        paid.handleInsidePortal(paid.blockPosition());h.assertTrue(!paid.canChangeDimensions()&&!paid.canUsePortal(),"true portal callback and dimension hooks are closed");
        CompoundTag tag=new CompoundTag();paid.saveWithoutId(tag);tag.putBoolean("NoAI",true);tag.putBoolean("NoGravity",false);
        var loaded=DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT.get().create(h.getLevel());loaded.load(tag);
        h.assertTrue(!loaded.isNoAi()&&loaded.isNoGravity()&&loaded.isPersistenceRequired()&&loaded.getOwnerName().equals(paid.getOwnerName()),"ordinary NBT retains owner but restores active flight policy");
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid),"portal actor real ticks")).thenIdle(30).thenExecute(()->{
            CompoundTag out=new CompoundTag();paid.saveWithoutId(out);h.assertTrue(out.getInt("PortalCooldown")>=0,"eligibility override does not decrement cooldown negative");h.succeed();
        });
    }
    private static ShadowSquealGhostUnit paid(GameTestHelper h,String suffix,int x,int z){String owner=PREFIX+suffix;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(owner,500,500,500));var e=DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT.get().create(h.getLevel());e.setOwnerName(owner);place(h,e,x,z);return e;}
    private static ShadowSquealGhostEntity wild(GameTestHelper h,int x,int z){var e=(ShadowSquealGhostEntity)ForgeRegistries.ENTITY_TYPES.getValue(DreamseekersMod.paster("shadow_squeal_ghost")).create(h.getLevel());e.setNoAi(true);place(h,e,x,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);ENTITIES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"actual fixture enters world");}
    private static FakePlayer player(GameTestHelper h,String n){return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes((PREFIX+n).getBytes()),PREFIX+n));}
    private static void arrow(GameTestHelper h,FakePlayer p,LivingEntity victim){var a=new Arrow(h.getLevel(),p);a.setBaseDamage(3);a.setNoGravity(true);a.moveTo(victim.getX()-5,victim.getY()+.5,victim.getZ());a.setDeltaMovement(.8,0,0);ENTITIES.add(a);h.assertTrue(h.getLevel().addFreshEntity(a),"physical arrow actually spawned");}
    private static boolean published(GameTestHelper h,Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}
    private static void floor(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);for(int i=-1;i<=24;i++)for(int y=1;y<=10;y++){h.getLevel().setBlock(p.offset(-1,y,i),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.offset(24,y,i),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.offset(i,y,-1),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.offset(i,y,24),Blocks.STONE.defaultBlockState(),2);}for(int x=p.getX()>>4;x<=(p.getX()+23)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+23)>>4;z++){var c=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(x,z,true);TICKETS.add(c);}}for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<10;y++)h.getLevel().setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}}
}
