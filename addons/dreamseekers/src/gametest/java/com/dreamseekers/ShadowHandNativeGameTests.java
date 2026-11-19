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
import net.pasterdream.entity.ShadowHandEntity;
import java.util.*;
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShadowHandNativeGameTests {
    private static final String BATCH="dreamseekers_shadow_hand_native",PREFIX="HandNative_";
    private static final List<Entity> ENTITIES=new ArrayList<>();
    private static final Set<net.minecraft.world.level.ChunkPos> TICKETS=new HashSet<>();
    private static Difficulty oldDifficulty;
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel l){
        if(oldDifficulty!=null){l.getServer().setDifficulty(oldDifficulty,true);oldDifficulty=null;}
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity e:ENTITIES)if(!e.isRemoved())e.discard();ENTITIES.clear();
        for(var c:TICKETS)l.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @AfterBatch(batch="dreamseekers_shadow_hand_peaceful") public static void cleanupPeaceful(ServerLevel l){cleanup(l);}
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void nativeDiveAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(GameTestHelper h){
        floor(h);var paid=paid(h,"Goals",5,5);var wild=wild(h,18,18);
        h.assertTrue(paid.getMoveControl() instanceof FlyingMoveControl&&paid.getNavigation() instanceof FlyingPathNavigation,"genuine flight controls");
        h.assertTrue(paid.getBbWidth()==com.dreamseekers.shadow.ShadowHandBalance.WIDTH&&paid.getBbHeight()==com.dreamseekers.shadow.ShadowHandBalance.HEIGHT&&paid.getMobType()==MobType.UNDEFINED,"exact original dimensions/type, not invented undead or phasing");
        h.assertTrue(wild.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal().getClass().getName().startsWith("net.pasterdream.entity.ShadowHandEntity$")),"original native dive remains on wild positive control");
        h.assertTrue(paid.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal().getClass().getName().startsWith("net.pasterdream.")||g.getGoal() instanceof MeleeAttackGoal),"all original attack/random goals replaced, not merely Player selection");
        h.assertTrue(paid.targetSelector.getAvailableGoals().size()==1&&paid.getAttackGoal() instanceof TeamSafeMeleeAttackGoal,"single RTS selected-target and APS goal");
        h.assertTrue(!(wild instanceof Unit)&&!paid.noPhysics&&paid.fireImmune()&&wild.fireImmune(),"wild not converted; native EntityType fire immunity deliberately retained");
        var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,DreamseekersMod.paster("shadow_mob"));
        h.assertTrue(!paid.getType().is(tag),"paid type not opted into unfiltered original shadow tag effects");
        h.succeed();
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=160)
    public static void documentedNativeImmunitiesRemainButRealArrowsDamageBoth(GameTestHelper h){
        floor(h);var paid=paid(h,"Damage",12,6);var wild=wild(h,12,17);var player=player(h,"Damage");
        for(LivingEntity e:List.of(paid,wild)){
            float hp=e.getHealth();for(var ds:List.of(e.damageSources().inFire(),e.damageSources().cactus())){
                e.invulnerableTime=0;h.assertTrue(!e.hurt(ds,2)&&e.getHealth()==hp,"documented native environment immunity retained");
            }
            ThrownPotion potion=new ThrownPotion(h.getLevel(),player);AreaEffectCloud cloud=new AreaEffectCloud(h.getLevel(),e.getX(),e.getY(),e.getZ());
            h.assertTrue(!e.causeFallDamage(20,2,e.damageSources().fall()),"physical fall rejected by native hook");
            for(var ds:List.of(e.damageSources().fall(),e.damageSources().drown(),e.damageSources().indirectMagic(potion,player),e.damageSources().indirectMagic(cloud,player))){
                e.invulnerableTime=0;float before=e.getHealth();h.assertTrue(e.hurt(ds,.25f)&&e.getHealth()<before,"do not copy unrelated ghost FALL/potion/cloud/drown immunity");
            }e.invulnerableTime=0;
            h.assertTrue(e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20,0)),"ordinary potion status is not falsely declared immune");e.removeAllEffects();
            h.assertTrue(e.hurt(e.damageSources().generic(),1)&&e.getHealth()<hp,"generic positive damage retained");e.invulnerableTime=0;e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
        float[] health={0,0};
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,wild),"real arrow victims published")).thenExecute(()->{
            health[0]=paid.getHealth();health[1]=wild.getHealth();
            arrow(h,player,paid);arrow(h,player,wild);
        }).thenIdle(35).thenExecute(()->h.assertTrue(paid.getHealth()<health[0]&&wild.getHealth()<health[1],"actual flying arrows damage paid and original entities")).thenSucceed();
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void interactionAndRealDeathCannotCreatePaidLootOrExperience(GameTestHelper h){
        floor(h);var paid=paid(h,"Death",6,6);var wild=wild(h,18,18);var p=player(h,"Death");
        for(Item item:List.of(Items.LEAD,Items.WHEAT,ForgeRegistries.ITEMS.getValue(DreamseekersMod.paster("shadow_hand_spawn_egg")))){
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
    @GameTest(template="hand_unit_arena",batch="dreamseekers_shadow_hand_peaceful",timeoutTicks=130)
    public static void paidPersistenceSurvivesPeacefulWhileOriginalMonsterDespawns(GameTestHelper h){
        floor(h);var paid=paid(h,"Peaceful",6,6);var wild=wild(h,18,18);
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,wild),"normal simulation before peaceful")).thenExecute(()->{
            oldDifficulty=h.getLevel().getDifficulty();h.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL,true);
        }).thenIdle(20).thenExecute(()->{
            h.getLevel().getServer().setDifficulty(oldDifficulty,true);oldDifficulty=null;
            h.assertTrue(paid.isAlive()&&!paid.isRemoved()&&paid.isPersistenceRequired()&&wild.isRemoved(),"only paid subtype persists in peaceful; NoAI alone is not a workaround");
        }).thenSucceed();
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=120)
    public static void portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(GameTestHelper h){
        floor(h);var paid=paid(h,"Portal",6,6);var wild=wild(h,18,18);var boat=EntityType.BOAT.create(h.getLevel());place(h,boat,12,12);
        h.assertTrue(!paid.startRiding(boat)&&!paid.startRiding(boat,true)&&paid.getVehicle()==null,"ordinary and forced mounting cannot bypass gateway through root vehicle");
        h.assertTrue(!net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(paid)&&net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(wild),"native gateway eligibility isolates paid only");
        paid.handleInsidePortal(paid.blockPosition());h.assertTrue(!paid.canChangeDimensions()&&!paid.canUsePortal(),"true portal callback and dimension hooks are closed");
        CompoundTag tag=new CompoundTag();paid.saveWithoutId(tag);tag.putBoolean("NoAI",true);tag.putBoolean("NoGravity",false);
        var loaded=DreamseekersMod.SHADOW_HAND_UNIT.get().create(h.getLevel());loaded.load(tag);
        h.assertTrue(!loaded.isNoAi()&&loaded.isNoGravity()&&loaded.isPersistenceRequired()&&loaded.getOwnerName().equals(paid.getOwnerName()),"ordinary NBT retains owner but restores active flight policy");
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid),"portal actor real ticks")).thenIdle(30).thenExecute(()->{
            CompoundTag out=new CompoundTag();paid.saveWithoutId(out);h.assertTrue(out.getInt("PortalCooldown")>=0,"eligibility override does not decrement cooldown negative");h.succeed();
        });
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=150)
    public static void hurtBeforeDamageAcceptanceCannotInjectNativeDashOrAnimation(GameTestHelper h){
        floor(h);var paid=paid(h,"Impulse",5,5);var blank=paid(h,"BlankImpulse",8,5);blank.setOwnerName("");var wild=wild(h,18,18);
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,blank)&&published(h,wild),"actual native hurt actors published")).thenExecute(()->{
            for(var e:List.of(paid,blank,wild)){e.setYRot(0);e.setXRot(0);e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}
            String animation=paid.getSyncedAnimation();float hp=paid.getHealth();
            h.assertTrue(!paid.hurt(paid.damageSources().inFire(),2)&&paid.getHealth()==hp&&!blank.hurt(blank.damageSources().inFire(),2),"native fire rejection still retained on paid");
            h.assertTrue(paid.getDeltaMovement().lengthSqr()==0&&blank.getDeltaMovement().lengthSqr()==0&&paid.getSyncedAnimation().equals(animation),"immune hurt cannot trigger native pre-validation dash or animation, even without owner");
            float whp=wild.getHealth();h.assertTrue(!wild.hurt(wild.damageSources().inFire(),2)&&wild.getHealth()==whp,"wild immune hurt control also rejected");
            h.assertTrue(wild.getDeltaMovement().lengthSqr()>1&&wild.getDeltaMovement().y<-.49&&wild.getSyncedAnimation().equals("attack"),"wild hurt-before-rejection original impulse and animation really remain");
            h.assertTrue(paid.hurt(paid.damageSources().generic(),1)&&paid.getHealth()<hp&&paid.getDeltaMovement().lengthSqr()==0,"accepted ordinary damage is not blocked and has no original dash");
            net.pasterdream.procedures.ShadowHandPr0Procedure.execute(paid);net.pasterdream.procedures.ShadowHandPr0Procedure.execute(blank);
            h.assertTrue(paid.getDeltaMovement().lengthSqr()==0&&blank.getDeltaMovement().lengthSqr()==0,"direct native procedure cannot bypass paid identity isolation");
        }).thenSucceed();
    }
    @GameTest(template="hand_unit_arena",batch=BATCH,timeoutTicks=100)
    public static void contactSanIsPaidScopedAndOrdinaryMagicRemainsEffective(GameTestHelper h){
        floor(h);var paid=paid(h,"San",6,6);var wild=wild(h,18,18);var p=player(h,"San");
        var cap=p.getCapability(net.pasterdream.capability.SanCapability.Provider.PLAYER_SAN_CAPABILITY).orElseThrow(()->new IllegalStateException("native SAN capability required"));
        var rule=h.getLevel().getGameRules().getRule(net.pasterdream.init.PasterdreamModGameRules.SANCHECKSYSTEM);boolean old=rule.get();
        try{rule.set(true,h.getLevel().getServer());cap.setSanValue(10);wild.playerTouch(p);h.assertTrue(Math.abs(cap.getSanValue()-9.98)<.0001,"wild contact actual SAN positive");cap.setSanValue(10);paid.playerTouch(p);paid.setOwnerName("");paid.playerTouch(p);h.assertTrue(cap.getSanValue()==10,"paid contact never deducts SAN, including blank owner");}finally{rule.set(old,h.getLevel().getServer());}
        float hp=paid.getHealth();h.assertTrue(paid.hurt(paid.damageSources().magic(),1)&&paid.getHealth()<hp,"no invented blanket MAGIC immunity");
        System.out.println("SHADOW_HAND_DIMENSION_TIMING initial wildTick="+wild.tickCount+" wildCached="+wild.getBbWidth()+"x"+wild.getBbHeight()+" wildGetter="+wild.getDimensions(Pose.STANDING)+" wildAabb="+wild.getBoundingBox()+" paidTick="+paid.tickCount+" paidCached="+paid.getBbWidth()+"x"+paid.getBbHeight()+" paidGetter="+paid.getDimensions(Pose.STANDING));
        // Native Entity caches registration dimensions in its constructor. Only its normal
        // first baseTick calls refreshDimensions; do not compare pre-tick cache to scaled body.
        h.startSequence().thenWaitUntil(()->h.assertTrue(published(h,paid)&&published(h,wild),"natural ticks apply original dimension refresh")).thenExecute(()->{
            System.out.println("SHADOW_HAND_DIMENSION_TIMING published wildTick="+wild.tickCount+" wildCached="+wild.getBbWidth()+"x"+wild.getBbHeight()+" wildGetter="+wild.getDimensions(Pose.STANDING)+" wildAabb="+wild.getBoundingBox()+" paidTick="+paid.tickCount+" paidCached="+paid.getBbWidth()+"x"+paid.getBbHeight());
            h.assertTrue(Math.abs(wild.getBbWidth()-paid.getBbWidth())<.00001&&Math.abs(wild.getBbHeight()-paid.getBbHeight())<.00001,"native1.5 dimensions scale applied exactly once to new physical body");
        }).thenSucceed();
    }
    private static ShadowHandUnit paid(GameTestHelper h,String suffix,int x,int z){String owner=PREFIX+suffix;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(owner,500,500,500));var e=DreamseekersMod.SHADOW_HAND_UNIT.get().create(h.getLevel());e.setOwnerName(owner);place(h,e,x,z);return e;}
    private static ShadowHandEntity wild(GameTestHelper h,int x,int z){var e=(ShadowHandEntity)ForgeRegistries.ENTITY_TYPES.getValue(DreamseekersMod.paster("shadow_hand")).create(h.getLevel());e.setNoAi(true);place(h,e,x,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);ENTITIES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"actual fixture enters world");}
    private static FakePlayer player(GameTestHelper h,String n){return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes((PREFIX+n).getBytes()),PREFIX+n));}
    private static void arrow(GameTestHelper h,FakePlayer p,LivingEntity victim){var a=new Arrow(h.getLevel(),p);a.setBaseDamage(3);a.setNoGravity(true);a.moveTo(victim.getX()-5,victim.getY()+.5,victim.getZ());a.setDeltaMovement(.8,0,0);ENTITIES.add(a);h.assertTrue(h.getLevel().addFreshEntity(a),"physical arrow actually spawned");}
    private static boolean published(GameTestHelper h,Entity e){return e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}
    private static void floor(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);for(int i=-1;i<=24;i++)for(int y=1;y<=10;y++){h.getLevel().setBlock(p.offset(-1,y,i),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.offset(24,y,i),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.offset(i,y,-1),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.offset(i,y,24),Blocks.STONE.defaultBlockState(),2);}for(int x=p.getX()>>4;x<=(p.getX()+23)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+23)>>4;z++){var c=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(x,z,true);TICKETS.add(c);}}for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<10;y++)h.getLevel().setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}}
}
