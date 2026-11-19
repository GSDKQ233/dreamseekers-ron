// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.spore.*;
import com.dreamseekers.gametest.SporeRandomProbe;
import com.dreamseekers.unit.GoldenFoxUnit;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;
import com.solegendary.reignofnether.building.production.ProductionItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import net.minecraftforge.network.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.pasterdream.entity.SporeEntityEntity;
import net.pasterdream.procedures.*;
import java.util.*;

/** Carrier rejection/isolation, not a claim that the undefined summon skill is playable. */
@GameTestHolder(DreamseekersMod.MOD_ID) @PrefixGameTestTemplate(false)
public final class SporeCarrierGameTests {
    private static final String BATCH="dreamseekers_spore_carrier",PREFIX="SporeTest_";
    private static final List<Entity> ENTITIES=new ArrayList<>();
    private static final Set<ChunkPos> TICKETS=new HashSet<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        UnitServerEvents.getUnitActionSlowQueue().removeIf(q->Arrays.stream(q.getUnitIds()).anyMatch(id->ENTITIES.stream().anyMatch(e->e.getId()==id)));
        for(Entity e:ENTITIES)if(!e.isRemoved())e.discard();ENTITIES.clear();SporeRandomProbe.clear();
        for(ChunkPos c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=120)
    public static void nonUnitHasNoTrainingPopulationOrLivingAuthority(GameTestHelper h){ready(h,()->{
        JungleSporeCarrier s=carrier(h,6,6);
        h.assertTrue((Object)s instanceof SporeEntityEntity&&!((Object)s instanceof Unit),"runtime native parent, deliberately not RTS ordinary unit");
        h.assertTrue(s.getBbWidth()==.2f&&s.getBbHeight()==.2f&&s.getHealth()==1,"actual native body and initial HP, not invented summon balance");
        h.assertTrue(ProductionItems.getProductionItem(DreamseekersMod.JUNGLE_SPORE_CARRIER.get())==null&&!UnitServerEvents.getAllUnits().contains(s),"no recipe/roster/population reservation");
        h.assertTrue(s.goalSelector.getAvailableGoals().isEmpty()&&s.targetSelector.getAvailableGoals().isEmpty(),"no autonomous goals");
        h.runAfterDelay(3,()->{
            h.assertTrue(s.wasAuthorityTerminated()&&s.isRemoved()&&h.getLevel().getEntity(s.getUUID())==null,"first real entity tick discards an unauthorized live carrier");
            h.assertTrue(items(h)==0&&xp(h)==0&&!UnitServerEvents.getAllUnits().contains(s),"expiry has no resource/population reward");
            System.out.println("DREAMSEEKERS_SPORE_NONUNIT_AUTHORITY_EXPIRY_PASS");h.succeed();
        });
    });}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=120)
    public static void deterministicOriginalRewardBranchOnlyRunsForWildSpore(GameTestHelper h){ready(h,()->{
        JungleSporeCarrier s=carrier(h,6,6);SporeEntityEntity wild=wild(h,10,6);
        SporeRandomProbe.force(s,0);SporeRandomProbe.force(wild,0);
        SporeEntityPr0Procedure.execute(h.getLevel(),s.getX(),s.getY(),s.getZ(),s);
        h.assertTrue(SporeRandomProbe.calls(s)==0&&!s.isRemoved()&&items(h)==0,"paid-type HEAD gate prevents original random branch entry");
        SporeEntityPr0Procedure.execute(h.getLevel(),wild.getX(),wild.getY(),wild.getZ(),wild);
        h.assertTrue(SporeRandomProbe.calls(wild)==2&&wild.isRemoved()&&items(h)==1,"forced original wild branch actually drops one native jungle spore and discards");
        h.runAfterDelay(4,()->{
            h.assertTrue(s.wasAuthorityTerminated()&&s.isRemoved()&&items(h)==1&&xp(h)==0,"carrier tick adds no later reward");
            System.out.println("DREAMSEEKERS_SPORE_REAL_NATIVE_RANDOM_POSITIVE_PAID_GATE_PASS");h.succeed();
        });
    });}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=120)
    public static void realDeathKeepsWildLootButNoCarrierLootOrExperience(GameTestHelper h){ready(h,()->{
        JungleSporeCarrier s=carrier(h,6,6);SporeEntityEntity wild=wild(h,10,6);
        SporeRandomProbe.force(wild,1);SporeRandomProbe.force(s,0);
        h.assertTrue(s.hurt(s.damageSources().genericKill(),99)&&wild.hurt(wild.damageSources().genericKill(),99),"both actual entities take lethal damage");
        h.runAfterDelay(35,()->{
            h.assertTrue(s.isRemoved()&&wild.isRemoved()&&s.deathTime>=20&&wild.deathTime>=20,"real native corpse ticks completed");
            h.assertTrue(items(h)==1&&xp(h)==0&&SporeRandomProbe.calls(s)==0,"only wild death loot retained; carrier dead baseTick still cannot run mixed reward procedure");
            System.out.println("DREAMSEEKERS_SPORE_NATIVE_DEATH_POSITIVE_PAID_ZERO_PASS");h.succeed();
        });
    });}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=120)
    public static void interactionsAndAttackCannotTurnCarrierIntoFreeUnit(GameTestHelper h){ready(h,()->{
        JungleSporeCarrier s=carrier(h,6,6);Cow victim=cow(h,8,6);ServerPlayer p=actor(h.getLevel(),"Interaction");
        for(Item item:List.of(Items.LEAD,Items.WHEAT,Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(DreamseekersMod.paster("spore_entity_spawn_egg"))),Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(DreamseekersMod.paster("jungle_spore"))))){
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item,8));
            for(int i=0;i<4;i++){s.mobInteract(p,InteractionHand.MAIN_HAND);s.interact(p,InteractionHand.MAIN_HAND);p.interactOn(s,InteractionHand.MAIN_HAND);}
            h.assertTrue(p.getMainHandItem().getCount()==8&&!s.isLeashed(),"no consume/trade/breeding/lead capture");
        }p.getInventory().clearContent();
        float hp=victim.getHealth();s.setTarget(victim);
        h.assertTrue(s.getTarget()==null&&!s.doHurtTarget(victim)&&victim.getHealth()==hp,"no friendly/enemy or unowned arbitrary target can receive carrier attack");
        h.assertTrue(!s.hurt(s.damageSources().fall(),.1f)&&!s.hurt(s.damageSources().cactus(),.1f),"native fall/cactus immunity remains");
        var arrow=new net.minecraft.world.entity.projectile.Arrow(h.getLevel(),p);
        h.assertTrue(!s.hurt(s.damageSources().arrow(arrow,p),.1f),"native direct arrow immunity remains");
        h.assertTrue(s.hurt(s.damageSources().generic(),.25f)&&s.getHealth()==.75f,"ordinary damage still accepted before expiry");
        h.runAfterDelay(3,()->{h.assertTrue(s.wasAuthorityTerminated()&&items(h)==0&&victim.getHealth()==hp,"no late interaction/combat rewards");h.succeed();});
    });}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=120)
    public static void originalHealAndFoxAreaKeepWildEffectsButCannotActivateCarrier(GameTestHelper h){ready(h,()->{
        JungleSporeCarrier s=carrier(h,6,6),dead=carrier(h,7,6);SporeEntityEntity wild=wild(h,8,6);Cow source=cow(h,7,7);
        s.setHealth(.25f);dead.setHealth(0);wild.setHealth(.25f);source.getPersistentData().putDouble("time",10);
        HealingSpellEntityPr1Procedure.execute(h.getLevel(),source.getX(),source.getY(),source.getZ(),source);
        h.assertTrue(wild.getHealth()>.25f&&s.getHealth()==.25f&&dead.getHealth()==0,"original spatial heal runs on wild but cannot heal/revive unavailable carrier");
        FoxFirePr0Procedure.execute(h.getLevel(),source.getX(),source.getY(),source.getZ(),source);
        h.assertTrue(wild.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&!s.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&dead.getActiveEffects().isEmpty(),"original spatial debuff positive retained for wild, no beneficiary on disabled carrier");
        h.assertTrue(!s.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100,3)),"direct status API denied");
        s.forceAddEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,3),source);
        h.assertTrue(s.getActiveEffects().isEmpty()&&s.getAbsorptionAmount()==0,"forceAddEffect still honors subtype gate");
        h.runAfterDelay(30,()->{h.assertTrue(s.wasAuthorityTerminated()&&dead.isRemoved()&&dead.deathTime>=20,"neither native healing nor status can extend inactive lifetime");h.succeed();});
    });}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=120)
    public static void forgedNbtCannotEnableEffectsEquipmentShieldLootOrLifetime(GameTestHelper h){ready(h,()->{
        JungleSporeCarrier s=DreamseekersMod.JUNGLE_SPORE_CARRIER.get().create(h.getLevel());
        CompoundTag tag=new CompoundTag();s.saveWithoutId(tag);tag.putFloat("Health",0);
        tag.putBoolean("DreamSporeDisabled",false);tag.putBoolean("Enabled",true);tag.putInt("DreamSporeSchema",999);tag.putInt("TTL",Integer.MAX_VALUE);
        ListTag effects=new ListTag();effects.add(new MobEffectInstance(MobEffects.REGENERATION,1000,4).save(new CompoundTag()));tag.put("ActiveEffects",effects);tag.putFloat("AbsorptionAmount",200);
        ListTag hands=new ListTag();hands.add(new ItemStack(Items.DIAMOND_SWORD).save(new CompoundTag()));hands.add(new CompoundTag());tag.put("HandItems",hands);
        tag.putString("DeathLootTable","minecraft:entities/cow");CompoundTag original=tag.copy();
        s.load(tag);h.assertTrue(tag.equals(original),"NBT sanitation does not mutate caller input");
        h.assertTrue(s.getActiveEffects().isEmpty()&&s.getAbsorptionAmount()==0&&s.getMainHandItem().isEmpty()&&s.getHealth()==0,"saved effects bypass cannot inject healing/equipment/shield or resurrection");
        place(h,s,6,6);h.runAfterDelay(30,()->{
            h.assertTrue(s.isRemoved()&&s.deathTime>=20&&items(h)==0&&xp(h)==0,"forged enabled/TTL/death loot payload still dies without side effect");
            h.succeed();
        });
    });}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=120)
    public static void nonfiniteHealthAndUnknownLiveSchemaNeverPersistAuthority(GameTestHelper h){ready(h,()->{
        JungleSporeCarrier nan=DreamseekersMod.JUNGLE_SPORE_CARRIER.get().create(h.getLevel()),live=DreamseekersMod.JUNGLE_SPORE_CARRIER.get().create(h.getLevel());
        CompoundTag n=new CompoundTag();nan.saveWithoutId(n);n.putFloat("Health",Float.NaN);nan.load(n);place(h,nan,6,6);
        CompoundTag t=new CompoundTag();live.saveWithoutId(t);t.putInt("DreamSporeSchema",987);t.putBoolean("DreamSporeDisabled",false);live.load(t);place(h,live,9,6);
        h.assertTrue(Float.isFinite(nan.getHealth())&&nan.isDeadOrDying(),"NaN cannot become neither-dead-nor-alive permanent carrier");
        h.runAfterDelay(30,()->{h.assertTrue(nan.isRemoved()&&nan.deathTime>=20&&live.wasAuthorityTerminated()&&live.isRemoved()&&items(h)==0,"ordinary real ticks close both malformed lifetime routes");h.succeed();});
    });}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=140)
    public static void typedRequestChecksRealSenderEpochDimensionAndNeverSpendsOrSummons(GameTestHelper h){
        room(h);String owner=account("Caster"),enemy=account("Foreign");GoldenFoxUnit fox=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());fox.setOwnerName(owner);place(h,fox,6,6);
        h.startSequence().thenWaitUntil(()->h.assertTrue(fox.tickCount>2&&published(h,fox),"ordinary published caster")).thenExecute(()->{
            ServerPlayer p=actor(h.getLevel(),"Caster"),foreign=actor(h.getLevel(),"Foreign");
            var req=request(fox,h.absolutePos(new BlockPos(8,1,6)));var unavailable=SporeSummonService.Result.UNAVAILABLE;
            h.assertTrue(SporeSummonService.request(null,req)==SporeSummonService.Result.INVALID_REQUEST,"no authenticated sender");
            h.assertTrue(SporeSummonService.request(foreign,req)==SporeSummonService.Result.NOT_OWNER,"same-faction foreign owner rejected");
            h.assertTrue(SporeSummonService.request(p,new SporeSummonRequest(99,req.requestId(),req.caster(),req.target()))==SporeSummonService.Result.INVALID_REQUEST,"unknown request schema fail closed");
            var bad=new SporeSummonRequest.Source(fox.getId(),UUID.randomUUID(),fox.rts().ownershipRevision,h.getLevel().dimension().location());
            h.assertTrue(SporeSummonService.request(p,new SporeSummonRequest(1,req.requestId(),bad,req.target()))==SporeSummonService.Result.INVALID_SOURCE,"stale ID/UUID denied");
            var wrong=new SporeSummonRequest.Source(fox.getId(),fox.getUUID(),fox.rts().ownershipRevision,Level.NETHER.location());
            h.assertTrue(SporeSummonService.request(p,new SporeSummonRequest(1,req.requestId(),wrong,req.target()))==SporeSummonService.Result.WRONG_DIMENSION,"dimension-bound source");
            for(int i=0;i<50;i++)h.assertTrue(SporeSummonService.request(p,req)==unavailable,"valid/replayed request remains unavailable");
            long epoch=fox.rts().ownershipRevision;
            fox.setOwnerName(owner);fox.resetBehaviours();
            h.assertTrue(fox.rts().ownershipRevision==epoch,"same owner and command reset do not change ownership epoch");
            fox.setOwnerName(enemy);fox.setOwnerName(owner);
            h.assertTrue(fox.rts().ownershipRevision==epoch+2,"each actual noncombat ownership transition increments epoch");
            h.assertTrue(SporeSummonService.request(p,req)==SporeSummonService.Result.STALE_OWNER,"observed owner ABA invalidates old request revision");
            h.assertTrue(SporeSummonService.request(p,request(fox,h.absolutePos(new BlockPos(8,1,6))))==unavailable,"fresh owner epoch still does not enable effects");
            Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(owner)).findFirst().orElseThrow();
            h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456&&h.getLevel().getEntitiesOfClass(JungleSporeCarrier.class,area(h)).isEmpty()&&fox.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.FOX_BLESSING,com.dreamseekers.skill.UnitSkillAbility.FOX_DREAM_SEED))&&fox.rts().skills().cooldownTicks(com.dreamseekers.skill.UnitSkill.FOX_BLESSING)==0&&fox.rts().skills().cooldownTicks(com.dreamseekers.skill.UnitSkill.FOX_DREAM_SEED)==0,"unavailable legacy spore API leaves currencies, both approved cooldowns/abilities and summon state unchanged");
            System.out.println("DREAMSEEKERS_SPORE_TYPED_AUTHORITY_UNAVAILABLE_PASS");h.succeed();
        });
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=120)
    public static void authenticatedOrdinaryCommandCannotEnqueueCarrier(GameTestHelper h){ready(h,()->{
        JungleSporeCarrier s=carrier(h,6,6);String name=account("Command");ServerPlayer p=actor(h.getLevel(),"Command");
        try{
            Connection connection=new Connection(PacketFlow.SERVERBOUND);connection.setListener(p.connection);
            var ctor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);ctor.setAccessible(true);
            NetworkEvent.Context ctx=ctor.newInstance(connection,NetworkDirection.PLAY_TO_SERVER,-1);h.assertTrue(ctx.getSender()==p,"actual Forge sender");
            var field=UnitServerEvents.class.getDeclaredField("unitActionFastQueue");field.setAccessible(true);List<?> fast=(List<?>)field.get(null);int before=fast.size(),slow=UnitServerEvents.getUnitActionSlowQueue().size();
            for(UnitAction action:List.of(UnitAction.MOVE,UnitAction.ATTACK,UnitAction.FOLLOW,UnitAction.HOLD,UnitAction.STOP))
                h.assertTrue(!new UnitActionServerboundPacket(name,action,-1,new int[]{s.getId()},h.absolutePos(new BlockPos(10,0,6)),BlockPos.ZERO,false).handle(()->ctx),"nonUnit ordinary command rejected: "+action);
            h.assertTrue(fast.size()==before&&UnitServerEvents.getUnitActionSlowQueue().size()==slow,"denied carrier selection does not mutate queues");
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        h.runAfterDelay(3,()->{h.assertTrue(s.wasAuthorityTerminated()&&s.isRemoved(),"commands do not authorize lifetime");h.succeed();});
    });}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=140)
    public static void combatCasterDeadRawUnloadedAndOutOfBoundsSourcesRemainClosed(GameTestHelper h){
        room(h);String owner=account("CombatCaster");var caster=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());caster.setOwnerName(owner);place(h,caster,6,6);
        Cow raw=cow(h,8,6);
        h.startSequence().thenWaitUntil(()->h.assertTrue(caster.tickCount>2&&raw.tickCount>2&&published(h,caster)&&published(h,raw),"real combat and raw source publication")).thenExecute(()->{
            ServerPlayer p=actor(h.getLevel(),"CombatCaster");
            var source=new SporeSummonRequest.Source(caster.getId(),caster.getUUID(),caster.rts().ownershipRevision,h.getLevel().dimension().location());
            var target=new SporeSummonRequest.GroundTarget(h.absolutePos(new BlockPos(10,1,6)));
            var req=new SporeSummonRequest(1,UUID.randomUUID(),source,target);
            h.assertTrue(SporeSummonService.request(p,req)==SporeSummonService.Result.UNAVAILABLE,"combat-contract branch remains unavailable");
            var invalid=new SporeSummonRequest(1,UUID.randomUUID(),source,new SporeSummonRequest.GroundTarget(new BlockPos(0,h.getLevel().getMaxBuildHeight()+1,0)));
            h.assertTrue(SporeSummonService.request(p,invalid)==SporeSummonService.Result.INVALID_TARGET,"out of world target rejected");
            BlockPos unloaded=new BlockPos(25000000,80,25000000);h.assertTrue(!h.getLevel().hasChunkAt(unloaded),"unloaded target premise without loading it");
            h.assertTrue(SporeSummonService.request(p,new SporeSummonRequest(1,UUID.randomUUID(),source,new SporeSummonRequest.GroundTarget(unloaded)))==SporeSummonService.Result.INVALID_TARGET,"unloaded target cannot trigger chunk load/summon");
            var rawSource=new SporeSummonRequest.Source(raw.getId(),raw.getUUID(),0,h.getLevel().dimension().location());
            h.assertTrue(SporeSummonService.request(p,new SporeSummonRequest(1,UUID.randomUUID(),rawSource,target))==SporeSummonService.Result.INVALID_SOURCE,"nonDream raw caster rejected");
            caster.setHealth(0);h.assertTrue(SporeSummonService.request(p,req)==SporeSummonService.Result.INVALID_SOURCE,"dead paid caster rejected before any economy/effect");
            h.assertTrue(h.getLevel().getEntitiesOfClass(JungleSporeCarrier.class,area(h)).isEmpty(),"no rejected request produced a carrier");h.succeed();
        });
    }
    private static SporeSummonRequest request(GoldenFoxUnit s,BlockPos target){return new SporeSummonRequest(1,UUID.randomUUID(),new SporeSummonRequest.Source(s.getId(),s.getUUID(),s.rts().ownershipRevision,s.level().dimension().location()),new SporeSummonRequest.GroundTarget(target));}
    private static String account(String suffix){String n=PREFIX+suffix;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(n,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(n,123,234,345,456));return n;}
    private static ServerPlayer actor(ServerLevel level,String suffix){String n=PREFIX+suffix;return FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(n.getBytes(java.nio.charset.StandardCharsets.UTF_8)),n));}
    private static JungleSporeCarrier carrier(GameTestHelper h,int x,int z){JungleSporeCarrier s=DreamseekersMod.JUNGLE_SPORE_CARRIER.get().create(h.getLevel());place(h,s,x,z);h.assertTrue(published(h,s),"new carrier must already be published for synchronous native spatial tests");return s;}
    private static SporeEntityEntity wild(GameTestHelper h,int x,int z){SporeEntityEntity s=(SporeEntityEntity)Objects.requireNonNull(ForgeRegistries.ENTITY_TYPES.getValue(DreamseekersMod.paster("spore_entity"))).create(h.getLevel());s.setNoAi(true);SporeRandomProbe.force(s,1);place(h,s,x,z);h.assertTrue(published(h,s),"wild spatial positive published");return s;}
    private static Cow cow(GameTestHelper h,int x,int z){Cow c=EntityType.COW.create(h.getLevel());c.setNoAi(true);place(h,c,x,z);return c;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY()+.4,p.getZ()+.5);ENTITIES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"real fixture entity added");}
    private static boolean published(GameTestHelper h,Entity e){return h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e;}
    private static void ready(GameTestHelper h,Runnable test){room(h);Cow marker=cow(h,6,9);h.startSequence().thenWaitUntil(()->h.assertTrue(marker.tickCount>2&&published(h,marker),"fixture chunk actually ticking/published")).thenExecute(()->{marker.discard();test.run();});}
    private static int items(GameTestHelper h){return h.getLevel().getEntitiesOfClass(ItemEntity.class,area(h)).stream().mapToInt(e->e.getItem().getCount()).sum();}
    private static int xp(GameTestHelper h){return h.getLevel().getEntitiesOfClass(ExperienceOrb.class,area(h)).size();}
    private static AABB area(GameTestHelper h){return new AABB(h.absolutePos(BlockPos.ZERO),h.absolutePos(new BlockPos(20,8,20)));}
    private static void room(GameTestHelper h){ServerLevel l=h.getLevel();BlockPos origin=h.absolutePos(BlockPos.ZERO);
        for(int x=0;x<20;x++)for(int z=0;z<20;z++){ChunkPos c=new ChunkPos(origin.offset(x,0,z));if(!l.getForcedChunks().contains(c.toLong())){l.setChunkForced(c.x,c.z,true);TICKETS.add(c);}}
        for(int x=0;x<20;x++)for(int z=0;z<20;z++)for(int y=0;y<=8;y++)if(y==0||y==8||x==0||z==0||x==19||z==19)l.setBlock(origin.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        l.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(origin,origin.offset(20,l.getMaxBuildHeight()-origin.getY(),20))).forEach(Entity::discard);
        for(int x=1;x<19;x++)for(int z=1;z<19;z++)for(int y=1;y<8;y++)l.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        l.getEntitiesOfClass(ItemEntity.class,area(h)).forEach(Entity::discard);
    }
}
