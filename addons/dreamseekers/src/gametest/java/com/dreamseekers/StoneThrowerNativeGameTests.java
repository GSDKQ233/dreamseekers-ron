// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.unit.*;
import com.dreamseekers.stone.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.gametest.StoneRandomProbe;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import net.pasterdream.entity.SmallStoneSpiritEntity;
import net.pasterdream.init.*;
import net.pasterdream.procedures.SmallStoneSpiritPr1Procedure;
import java.util.*;
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StoneThrowerNativeGameTests {
    private static final String BATCH="dreamseekers_stone_native",PREFIX="StoneNative_";
    private static net.minecraft.world.Difficulty previousDifficulty;
    private static final List<Entity> ENTITIES=new ArrayList<>();
    private static final Set<net.minecraft.world.level.ChunkPos> TICKETS=new HashSet<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){
        if(previousDifficulty!=null){level.getServer().setDifficulty(previousDifficulty,true);previousDifficulty=null;}
        var names=PlayerServerEvents.rtsPlayers.stream().map(p->p.name).filter(n->n.startsWith(PREFIX)).toList();
        for(String a:names)for(String b:names)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        ENTITIES.forEach(Entity::discard);ENTITIES.clear();StoneRandomProbe.clear();
        var stones=new ArrayList<Entity>();for(Entity e:level.getAllEntities())if(e instanceof StoneProjectile stone&&stone.getOwner() instanceof Unit u&&u.getOwnerName().startsWith(PREFIX))stones.add(e);stones.forEach(Entity::discard);
        for(var c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @AfterBatch(batch="dreamseekers_stone_peaceful") public static void cleanupPeaceful(ServerLevel level){cleanup(level);}
    @GameTest(template="stone_native_arena",batch=BATCH,timeoutTicks=180)
    public static void actualArrowHitsOnlyNewCombatWhileLegacyWorkerAndWildRetainImmunity(GameTestHelper h){
        room(h);var paid=paid(h,"arrows",12,5);var worker=worker(h,"arrows",12,11);var wild=wild(h,12,17);
        float ph=paid.getHealth(),wh=worker.getHealth(),nh=wild.getHealth();
        ready(h,paid,worker,wild).thenExecute(()->{
            for(Entity victim:List.of(paid,worker,wild)){
                Arrow arrow=new Arrow(h.getLevel(),victim.getX()-7,victim.getY()+.5,victim.getZ());arrow.pickup=AbstractArrow.Pickup.DISALLOWED;arrow.setNoGravity(true);arrow.setBaseDamage(4);arrow.shoot(1,0,0,.9f,0);ENTITIES.add(arrow);h.assertTrue(h.getLevel().addFreshEntity(arrow),"real physical arrow inserted");
            }
        }).thenIdle(35).thenExecute(()->{
            h.assertTrue(paid.getHealth()<ph&&worker.getHealth()==wh&&wild.getHealth()==nh,"only new paid thrower normalizes actual arrows; original wild and old-worker immunity preserved");
            for(var e:List.of(paid,worker,wild)){e.invulnerableTime=0;h.assertTrue(e.hurt(e.damageSources().generic(),2),"generic damage remains native positive control");}
            System.out.println("DREAMSEEKERS_STONE_ARROW_NEW_ONLY_PASS hp="+paid.getHealth());
        }).thenSucceed();
    }
    @GameTest(template="stone_native_arena",batch=BATCH,timeoutTicks=200)
    public static void actualWildDeathGrowsOnlyWildAndSchedulesOriginalTerrainWhilePaidSourcesCannot(GameTestHelper h){
        room(h);var paid=paid(h,"death",6,6);var old=worker(h,"death",14,6);var source=wild(h,29,6);var neighbour=wild(h,32,6);var protectedPaid=paid(h,"growth",29,9);var protectedOld=worker(h,"growth",32,9);
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),PREFIX+"killer"));
        BlockPos pp=paid.blockPosition(),op=old.blockPosition(),wp=source.blockPosition();
        ready(h,paid,old,source,neighbour,protectedPaid,protectedOld).thenExecute(()->{
            for(Entity e:List.of(paid,old,source))StoneRandomProbe.force(e,.9);
            double before=neighbour.getPersistentData().getDouble("size");
            for(var e:List.of(paid,old,source)){e.setLastHurtByPlayer(player);e.hurt(e.damageSources().genericKill(),999);}
            h.assertTrue(Math.abs(neighbour.getPersistentData().getDouble("size")-before-.1)<.00001,"actual wild death immediately executes original neighbouring wild growth");
            h.assertTrue(protectedPaid.getPersistentData().getDouble("size")==0&&protectedOld.getPersistentData().getDouble("size")==0,"same callback BEFORE another worker tick: paid targets never receive native size write");
            h.assertTrue(StoneRandomProbe.calls(source)==1&&StoneRandomProbe.calls(paid)==0&&StoneRandomProbe.calls(old)==0,"genuine wild random branch reached; paid sources cancel before scheduling");
        }).thenWaitUntil(()->h.assertTrue(paid.isRemoved()&&old.isRemoved()&&source.isRemoved()&&paid.deathTime>=24&&old.deathTime>=24,"real native 24 death ticks complete"))
            .thenIdle(10).thenExecute(()->{
                h.assertTrue(h.getLevel().getBlockState(pp).isAir()&&h.getLevel().getBlockState(op).isAir(),"paid deaths never place a resurrection block after22ticks");
                h.assertTrue(h.getLevel().getBlockState(wp).is(PasterdreamModBlocks.SMALL_STONE_SPIRIT_BLOCK.get()),"wild positive control retains actual22tick stone-spirit terrain callback");
                var items=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area(h));
                h.assertTrue(items.size()==1&&items.get(0).getItem().getItem()==PasterdreamModItems.CYAN_STONE.get()&&items.get(0).getItem().getCount()==1,"only actual wild death yields its native stone item; both paid identities yield nothing");
                h.assertTrue(h.getLevel().getEntitiesOfClass(ExperienceOrb.class,area(h)).isEmpty(),"no paid XP");
                System.out.println("DREAMSEEKERS_STONE_SOURCE_TARGET_DEATH_ISOLATION_PASS");
            }).thenSucceed();
    }
    @GameTest(template="stone_native_arena",batch=BATCH,timeoutTicks=100)
    public static void loadedScalePoisonCannotResizeEitherPaidIdentityAndNativeScaleStillWorks(GameTestHelper h){
        room(h);var paid=paid(h,"scale",5,5);var old=worker(h,"scale",10,5);var wild=wild(h,15,5);
        ready(h,paid,old,wild).thenExecute(()->{
            for(Entity e:List.of(paid,old)){CompoundTag tag=new CompoundTag();e.saveWithoutId(tag);tag.getCompound("ForgeData").putDouble("size",17);e.load(tag);
                h.assertTrue(SmallStoneSpiritPr1Procedure.execute(e)==1,"original renderer scale hook is fixed immediately after ordinary NBT load, before next tick");}
            h.assertTrue(Math.abs(paid.getBbWidth()-.7)<.001&&Math.abs(paid.getBbHeight()-1)<.001&&Math.abs(old.getBbWidth()-.6)<.001&&Math.abs(old.getBbHeight()-.8)<.001,"separate registered collision dimensions never inherit growth");
            h.assertTrue(paid.getType()!=old.getType()&&old instanceof WorkerUnit&&!((Object)paid instanceof WorkerUnit)&&paid.getAttackBuildingGoal()==null&&!paid.canAttackBuildings(),"old worker ID/profession retained, new ranged ID not a worker or free siege unit");
            wild.getPersistentData().putDouble("size",.5);h.assertTrue(SmallStoneSpiritPr1Procedure.execute(wild)==1.5,"original wild scale remains original size+1");
            h.assertTrue(paid.getNavigation() instanceof GroundPathNavigation&&!paid.isFlyingUnit()&&!paid.isNoGravity(),"ground class is not copied flight behavior");
        }).thenSucceed();
    }
    @GameTest(template="stone_native_arena",batch=BATCH,timeoutTicks=140)
    public static void noNativeInteractionOrUnapprovedSiegeAndRealSenderReservedRequestFailsClosed(GameTestHelper h){
        room(h);var paid=paid(h,"interface",5,5);var own=paid(h,"interface",9,5);var ally=paid(h,"ally",13,5);AlliancesServerEvents.addAlliance(paid.getOwnerName(),ally.getOwnerName());
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),paid.getOwnerName()));
        var foreign=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),PREFIX+"foreign"));
        ready(h,paid,own,ally).thenExecute(()->{
            for(Item item:List.of(Items.LEAD,Items.COBBLESTONE,PasterdreamModItems.SMALL_STONE_SPIRIT_SPAWN_EGG.get())){
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(item,3));
                h.assertTrue(paid.mobInteract(player,net.minecraft.world.InteractionHand.MAIN_HAND)==net.minecraft.world.InteractionResult.PASS&&player.getMainHandItem().getCount()==3&&!paid.canBeLeashed(player),"no native breeding, conversion, rewards, item use or leash");
            }
            var request=new ReservedSkillRequest(ReservedSkillRequest.Skill.STONE_SPIRIT_SIEGE_RESEARCH,paid.getId(),paid.getUUID(),new ReservedSkillRequest.Self());
            h.assertTrue(ReservedSkillService.request(player,request)==ReservedSkillService.Result.DISABLED&&ReservedSkillService.request(foreign,request)==ReservedSkillService.Result.NOT_OWNER,"actual sender checked, authorized siege research stays disabled");
            float oh=own.getHealth(),ah=ally.getHealth();for(var target:List.of(own,ally)){paid.setUnitAttackTargetForced(target);paid.performRangedAttack(target,1);h.assertTrue(!paid.doHurtTarget(target)&&paid.getTarget()==null,"no friendly native melee fallback or projectile release");}
            h.assertTrue(own.getHealth()==oh&&ally.getHealth()==ah&&stones(h,paid).isEmpty()&&paid.getAbilities().get().equals(List.of(com.dreamseekers.skill.UnitSkillAbility.STONE_HEAVY_SHOT))&&paid.rts().skills().cooldownTicks(com.dreamseekers.skill.UnitSkill.STONE_HEAVY_SHOT)==0,"disabled/illegal operations grant no damage/projectile and expose only the approved unused heavy-shot active with zero cooldown");
            Resources r=ResourcesServerEvents.resourcesList.stream().filter(x->x.ownerName.equals(paid.getOwnerName())).findFirst().orElseThrow();h.assertTrue(r.food==500&&r.wood==500&&r.ore==500,"no fake research cost or free resource effects");player.getInventory().clearContent();
        }).thenSucceed();
    }
    @GameTest(template="stone_native_arena",batch=BATCH,timeoutTicks=320)
    public static void sameGroundEnemyExplicitAttackReleasesHoldThenTravelsAndDamagesWithoutMelee(GameTestHelper h){
        room(h);var paid=paid(h,"ground",5,12);var enemy=paid(h,"groundEnemy",23,12);enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);enemy.setHealth(500);Vec3[] start={null};double[] travel={0};
        var seq=ready(h,paid,enemy).thenExecute(()->{paid.setNoAi(false);paid.setHoldPosition(true);paid.setUnitAttackTarget(enemy);start[0]=paid.position();});
        seq.thenIdle(30).thenExecute(()->{h.assertTrue(paid.position().distanceToSqr(start[0])<.2&&enemy.getHealth()==500,"automatic same enemy under HOLD never chases out of range");new UnitActionItem(paid.getOwnerName(),UnitAction.ATTACK,enemy.getId(),new int[]{paid.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());h.assertTrue(!paid.getHoldPosition(),"real same-target explicit ATTACK releases hold through shared contract");});
        for(int i=0;i<170;i++)seq.thenIdle(1).thenExecute(()->{for(var stone:stones(h,paid))travel[0]=Math.max(travel[0],stone.position().distanceTo(paid.position()));if(paid.tickCount%10==0)System.out.println("STONE_GROUND_TRACE tick="+paid.tickCount+" pos="+paid.position()+" target="+(paid.getTarget()==enemy)+" hold="+paid.getHoldPosition()+" enemy="+enemy.position()+" hp="+enemy.getHealth()+" travel="+travel[0]+" cooldown="+((com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal<?>)paid.getAttackGoal()).getAttackCooldown()+" navDone="+paid.getNavigation().isDone()+" navTarget="+paid.getNavigation().getTargetPos()+" los="+paid.hasLineOfSight(enemy));h.assertTrue(h.getLevel().noCollision(paid)&&!paid.isNoGravity(),"ground attacker stays physical");});
        seq.thenExecute(()->{System.out.println("STONE_GROUND_FINAL movedSq="+paid.position().distanceToSqr(start[0])+" travel="+travel[0]+" hp="+enemy.getHealth());h.assertTrue(paid.position().distanceToSqr(start[0])>4&&travel[0]>1&&enemy.getHealth()<500,"actual ground pursuit produces physical travelling stone and real enemy damage");h.assertTrue(!paid.doHurtTarget(enemy),"no inherited melee fallback");System.out.println("DREAMSEEKERS_STONE_GROUND_PHYSICAL_ATTACK_PASS travel="+travel[0]+" hp="+enemy.getHealth());}).thenSucceed();
    }
    @GameTest(template="stone_native_arena",batch=BATCH,timeoutTicks=220)
    public static void friendlyFollowMovesAndRevocationCancelsBeforeDistantTargetCanResume(GameTestHelper h){
        room(h);var paid=paid(h,"follow",5,12);var ally=paid(h,"followAlly",18,12);AlliancesServerEvents.addAlliance(paid.getOwnerName(),ally.getOwnerName());Vec3[] start={null},stop={null};
        ready(h,paid,ally).thenExecute(()->{paid.setNoAi(false);start[0]=paid.position();new UnitActionItem(paid.getOwnerName(),UnitAction.FOLLOW,ally.getId(),new int[]{paid.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());})
            .thenIdle(60).thenExecute(()->{h.assertTrue(paid.getFollowTarget()==ally&&paid.position().distanceToSqr(start[0])>4,"real allied follow moves on ground");AlliancesServerEvents.removeAlliance(paid.getOwnerName(),ally.getOwnerName());BlockPos p=h.absolutePos(new BlockPos(38,1,12));ally.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);})
            .thenIdle(5).thenExecute(()->{stop[0]=paid.position();h.assertTrue(paid.getFollowTarget()==null&&paid.getMoveGoal().getMoveTarget()==null,"revoked relation cancels old follow rather than converting to attack");})
            .thenIdle(45).thenExecute(()->h.assertTrue(paid.position().distanceToSqr(stop[0])<.3&&paid.getNavigation().isDone()&&paid.getNavigation().getTargetPos()==null,"cross delayed-recompute window with no stale path revival")).thenSucceed();
    }
    @GameTest(template="stone_native_arena",batch="dreamseekers_stone_peaceful",timeoutTicks=160)
    public static void peacefulKeepsPaidCombatWhileActualWildMonsterDespawns(GameTestHelper h){
        room(h);var paid=paid(h,"peaceful",5,5);var wild=wild(h,12,5);var server=h.getLevel().getServer();var previous=h.getLevel().getDifficulty();previousDifficulty=previous;
        ready(h,paid,wild).thenExecute(()->server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true)).thenIdle(30).thenExecute(()->{
            server.setDifficulty(previous,true);previousDifficulty=null;h.assertTrue(paid.isAlive()&&!paid.isRemoved()&&wild.isRemoved(),"only paid combat subtype survives actual peaceful monster despawn");
        }).thenSucceed();
    }
    @GameTest(template="stone_native_arena",batch=BATCH,timeoutTicks=180)
    public static void actualGatewayTeleportsVanillaButNotPaidStoneOrItsProjectile(GameTestHelper h){
        room(h);var source=paid(h,"gateway",5,8);var enemy=paid(h,"gatewayEnemy",23,8);var guard=paid(h,"gatewayGuard",12,18);var wild=wild(h,12,21);
        StoneProjectile[] shot={null};Snowball[] vanilla={null};Vec3[] previous=new Vec3[4];double[] jumps=new double[4];
        var seq=ready(h,source,enemy,guard,wild).thenExecute(()->{
            for(int z:new int[]{3,8,18,21}){
                int y=z<10?2:1;BlockPos gate=h.absolutePos(new BlockPos(12,y,z));
                h.getLevel().setBlock(gate,Blocks.END_GATEWAY.defaultBlockState(),2);
                var be=(net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity)h.getLevel().getBlockEntity(gate);
                h.assertTrue(be!=null&&!be.isCoolingDown(),"actual independent ready gateway");be.setExitPosition(h.absolutePos(new BlockPos(32,y,z)),true);
            }
            vanilla[0]=new Snowball(h.getLevel(),h.absolutePos(new BlockPos(8,2,3)).getX()+.5,h.absolutePos(new BlockPos(8,2,3)).getY()+.5,h.absolutePos(new BlockPos(8,2,3)).getZ()+.5);
            vanilla[0].setNoGravity(true);vanilla[0].setDeltaMovement(.8,0,0);ENTITIES.add(vanilla[0]);h.assertTrue(h.getLevel().addFreshEntity(vanilla[0]),"real vanilla projectile positive control");
            shot[0]=new StoneProjectile(DreamseekersMod.STONE_SPIRIT_THROWER_BOLT.get(),h.getLevel());shot[0].arm(source,enemy);
            BlockPos p=h.absolutePos(new BlockPos(8,2,8));shot[0].setPos(p.getX()+.5,p.getY()+.5,p.getZ()+.5);shot[0].setDeltaMovement(.8,0,0);ENTITIES.add(shot[0]);h.assertTrue(h.getLevel().addFreshEntity(shot[0]),"actual armed paid projectile, no manually ticked gateway");
            var boat=new net.minecraft.world.entity.vehicle.Boat(h.getLevel(),source.getX(),source.getY(),source.getZ()+3);ENTITIES.add(boat);h.assertTrue(h.getLevel().addFreshEntity(boat),"real boat fixture");
            h.assertTrue(!guard.startRiding(boat)&&!guard.startRiding(boat,true)&&!shot[0].startRiding(boat)&&!shot[0].startRiding(boat,true),"normal and forced/load riding cannot bypass root-vehicle gateway filter");
            previous[0]=vanilla[0].position();previous[1]=shot[0].position();previous[2]=guard.position();previous[3]=wild.position();
        });
        for(int tick=0;tick<30;tick++)seq.thenIdle(1).thenExecute(()->{
            Entity[] es={vanilla[0],shot[0],guard,wild};for(int n=0;n<4;n++){jumps[n]=Math.max(jumps[n],es[n].position().distanceTo(previous[n]));previous[n]=es[n].position();}
        });
        seq.thenExecute(()->{
            System.out.println("STONE_GATEWAY_TRACE vanillaJump="+jumps[0]+" stoneJump="+jumps[1]+" paidUnitJump="+jumps[2]+" wildUnitJump="+jumps[3]);
            h.assertTrue(jumps[0]>10&&jumps[3]>10,"natural gateway genuinely teleports original vanilla projectile and wild native monster");
            h.assertTrue(jumps[1]<=1.1&&jumps[2]<.2,"paid projectile and paid unit cannot trigger gateway relocation");
            h.assertTrue(!net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(shot[0])&&!net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.canEntityTeleport(guard),"both inherited gateway eligibility paths reject paid identities");
            h.assertTrue(!shot[0].canChangeDimensions()&&!guard.canChangeDimensions(),"dimension-changing eligibility also fails closed");
            for(Entity e:List.of(guard,shot[0])){CompoundTag saved=new CompoundTag();e.saveWithoutId(saved);h.assertTrue(saved.getInt("PortalCooldown")>=0,"permanent ineligibility never decrements persisted cooldown negative");}
            shot[0].handleInsidePortal(h.absolutePos(new BlockPos(12,2,8)));guard.handleInsidePortal(h.absolutePos(new BlockPos(12,1,18)));
            h.assertTrue(shot[0].isRemoved()&&guard.isAlive(),"Nether portal callback consumes only the projectile and cannot relocate the unit");
            System.out.println("DREAMSEEKERS_STONE_GATEWAY_NATIVE_POSITIVE_PAID_ISOLATION_PASS");
        }).thenSucceed();
    }
    private static GameTestSequence ready(GameTestHelper h,Entity... entities){return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"real ticks and both publication lookups");});}
    private static String owner(String suffix){String n=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(n)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(n,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(n,500,500,500));}return n;}
    private static StoneSpiritThrowerUnit paid(GameTestHelper h,String name,int x,int z){var e=DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get().create(h.getLevel());e.setOwnerName(owner(name));e.setNoAi(true);place(h,e,x,z);return e;}
    private static StoneSpiritWorker worker(GameTestHelper h,String name,int x,int z){var e=DreamseekersMod.STONE_SPIRIT_WORKER.get().create(h.getLevel());e.setOwnerName(owner(name));e.setNoAi(true);place(h,e,x,z);return e;}
    private static SmallStoneSpiritEntity wild(GameTestHelper h,int x,int z){var e=PasterdreamModEntities.SMALL_STONE_SPIRIT.get().create(h.getLevel());e.setNoAi(true);place(h,e,x,z);return e;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);ENTITIES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"actual entity addition");}
    private static AABB area(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);return new AABB(p,p.offset(44,10,24));}
    private static List<StoneProjectile> stones(GameTestHelper h,Entity caster){return h.getLevel().getEntitiesOfClass(StoneProjectile.class,area(h)).stream().filter(e->e.getOwner()==caster).toList();}
    private static void room(GameTestHelper h){
        BlockPos p=h.absolutePos(BlockPos.ZERO);var l=h.getLevel();for(int cx=p.getX()>>4;cx<=(p.getX()+43)>>4;cx++)for(int cz=p.getZ()>>4;cz<=(p.getZ()+23)>>4;cz++){var c=new net.minecraft.world.level.ChunkPos(cx,cz);if(!l.getForcedChunks().contains(c.toLong())){l.setChunkForced(cx,cz,true);TICKETS.add(c);}}
        for(int x=-1;x<=44;x++)for(int z=-1;z<=24;z++){l.setBlock(p.offset(x,10,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);if(x==-1||x==44||z==-1||z==24)for(int y=1;y<10;y++)l.setBlock(p.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);}
        l.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p,p.offset(44,l.getMaxBuildHeight()-p.getY(),24))).forEach(Entity::discard);
        for(int x=0;x<44;x++)for(int z=0;z<24;z++)for(int y=1;y<10;y++)l.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
    }
}
