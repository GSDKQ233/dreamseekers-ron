// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.unit.*;
import com.dreamseekers.skill.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import net.pasterdream.entity.JellyfishEntity;
import net.pasterdream.procedures.JellyfishPr0Procedure;
import java.util.*;
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JellyfishSafetyGameTests {
    private static final String BATCH="dreamseekers_jellyfish_safety", PREFIX="JellySafe_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final List<FakePlayer> PLAYERS=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        var owners=PlayerServerEvents.rtsPlayers.stream().map(p->p.name).filter(n->n.startsWith(PREFIX)).toList();
        for(String a:owners)for(String b:owners)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity e:FIXTURES)if(!e.isRemoved())e.discard();FIXTURES.clear();
        for(FakePlayer p:PLAYERS)p.getInventory().clearContent();PLAYERS.clear();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=180)
    public static void nativeFloatImpulseOnlySuppressedForPaidSubtypeAndNeverReturnsAfterLoad(GameTestHelper h) {
        floor(h);JellyfishUnit jelly=spawn(h,"Impulse",6,6);
        JellyfishEntity wild=net.pasterdream.init.PasterdreamModEntities.JELLYFISH.get().create(h.getLevel());
        h.assertTrue(wild!=null,"actual original jellyfish exists");FIXTURES.add(wild);
        BlockPos pos=h.absolutePos(new BlockPos(15,2,15));wild.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        Vec3 motion=new Vec3(.12,0,.13);boolean wildFloat=false;
        for(String owner:List.of(jelly.getOwnerName(),"","ChangedOwner")) {
            jelly.setOwnerName(owner);
            for(int i=0;i<100;i++) {
                jelly.setDeltaMovement(motion);JellyfishPr0Procedure.execute(h.getLevel(),jelly.getX(),jelly.getY(),jelly.getZ(),jelly);
                h.assertTrue(jelly.getDeltaMovement().equals(motion),"paid original procedure cannot replace commanded motion regardless of ownership");
                wild.setDeltaMovement(Vec3.ZERO);JellyfishPr0Procedure.execute(h.getLevel(),wild.getX(),wild.getY(),wild.getZ(),wild);
                if(wild.getDeltaMovement().y>.05)wildFloat=true;
            }
        }
        h.assertTrue(wildFloat,"actual wild upfloat remains enabled, not global procedure suppression");
        jelly.setOwnerName(PREFIX+"Impulse");jelly.setDeltaMovement(Vec3.ZERO);jelly.setScenarioRoleIndex(92);
        CompoundTag tag=new CompoundTag();jelly.saveWithoutId(tag);tag.putBoolean("NoAI",true);tag.putBoolean("NoGravity",false);tag.putInt("Age",-24000);tag.putInt("InLove",600);
        JellyfishUnit restored=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());restored.load(tag);
        h.assertTrue(restored.getUUID().equals(jelly.getUUID())&&restored.getOwnerName().equals(jelly.getOwnerName())&&restored.getScenarioRoleIndex()==92
                &&!restored.isNoAi()&&restored.isNoGravity()&&!restored.isBaby()&&!restored.isInLove(),"loaded identity retained but malformed wild AI/breeding flags cannot enable behavior");
        restored.setDeltaMovement(motion);JellyfishPr0Procedure.execute(h.getLevel(),restored.getX(),restored.getY(),restored.getZ(),restored);
        h.assertTrue(restored.getDeltaMovement().equals(motion),"load cannot reopen native upfloat");
        Vec3[] stopped={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(jelly.tickCount>2,"real entity ticks before hold"))
                .thenExecute(()->order(jelly,UnitAction.HOLD,-1,BlockPos.ZERO)).thenIdle(20).thenExecute(()->stopped[0]=jelly.position())
                .thenIdle(100).thenExecute(()->{
                    h.assertTrue(jelly.getHoldPosition()&&jelly.position().distanceToSqr(stopped[0])<.05,"native baseTick never restarts drifting in HOLD");
                    h.assertTrue(jelly.getBbWidth()==.7f&&jelly.getBbHeight()==1.4f&&h.getLevel().noCollision(jelly),"original adult dimensions kept with solid collision: width="+jelly.getBbWidth()+" height="+jelly.getBbHeight()+" pos="+jelly.position()+" body="+jelly.getBoundingBox()+" blocks="+BlockPos.betweenClosedStream(jelly.getBoundingBox().inflate(.1)).map(bp->bp+":"+h.getLevel().getBlockState(bp)).toList()+" nearby="+h.getLevel().getEntities(jelly,jelly.getBoundingBox().inflate(2)).stream().map(e->e.getType()+":"+e.position()).toList());
                    System.out.println("DREAMSEEKERS_JELLYFISH_NATIVE_FLOAT_WILD_NBT_HOLD_PASS");
                }).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=100)
    public static void ordinaryDamageAndActualDeathRemainSafeWithoutInventedInvulnerability(GameTestHelper h) {
        floor(h);JellyfishUnit jelly=spawn(h,"Damage",8,8);FakePlayer p=player(h,"Killer");
        h.startSequence().thenWaitUntil(()->h.assertTrue(jelly.tickCount>2,"real ticking before damage"))
                .thenExecute(()->{
                    float hp=jelly.getHealth();h.assertTrue(jelly.hurt(jelly.damageSources().generic(),3)&&jelly.getHealth()<hp,"ordinary damage accepted");
                    jelly.invulnerableTime=0;var arrow=new net.minecraft.world.entity.projectile.Arrow(h.getLevel(),p);
                    hp=jelly.getHealth();h.assertTrue(jelly.hurt(jelly.damageSources().arrow(arrow,p),3)&&jelly.getHealth()<hp,"arrow damage source accepted, no physical projectile flight claim");
                    h.assertTrue(!jelly.causeFallDamage(10,1,jelly.damageSources().fall()),"native no-fall landing behavior retained");
                    jelly.setLastHurtByPlayer(p);jelly.hurt(jelly.damageSources().genericKill(),999);
                }).thenWaitUntil(()->h.assertTrue(jelly.isRemoved()&&jelly.deathTime>=10,"genuine native ten-tick death"))
                .thenExecute(()->{
                    h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,area(h)).isEmpty()&&h.getLevel().getEntitiesOfClass(ExperienceOrb.class,area(h)).isEmpty(),"no native loot or Animal XP after actual attributed death");
                    System.out.println("DREAMSEEKERS_JELLYFISH_DAMAGE_DEATH_PASS");
                }).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=120)
    public static void reservedProtectionAndHealAreTypedUnavailableAndNeverGrantEffectsOrResources(GameTestHelper h) {
        floor(h);JellyfishUnit source=spawn(h,"Owner",5,5),own=spawn(h,"Owner",9,5),ally=spawn(h,"Ally",13,5),enemy=spawn(h,"Enemy",18,5);
        AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());
        FakePlayer owner=player(h,"Owner"),foreign=player(h,"Enemy");
        for(JellyfishUnit fixture:List.of(source,own,ally,enemy))fixture.setNoAi(true); // isolate the old disabled API from approved autocast
        own.hurt(own.damageSources().generic(),5);ally.hurt(ally.damageSources().generic(),5);enemy.hurt(enemy.damageSources().generic(),5);
        Map<JellyfishUnit,Float> health=Map.of(source,source.getHealth(),own,own.getHealth(),ally,ally.getHealth(),enemy,enemy.getHealth());
        for(var skill:List.of(ReservedSkillRequest.Skill.JELLYFISH_PROTECTION,ReservedSkillRequest.Skill.JELLYFISH_GRADUAL_HEAL)) {
            var self=new ReservedSkillRequest(skill,source.getId(),source.getUUID(),new ReservedSkillRequest.Self());
            h.assertTrue(ReservedSkillService.request(null,self)==ReservedSkillService.Result.INVALID_REQUEST,"no sender rejected");
            h.assertTrue(ReservedSkillService.request(foreign,self)==ReservedSkillService.Result.NOT_OWNER,"same faction is not casting authority");
            h.assertTrue(ReservedSkillService.request(owner,new ReservedSkillRequest(skill,source.getId(),UUID.randomUUID(),self.target()))==ReservedSkillService.Result.INVALID_SOURCE,"stale source UUID rejected");
            for(JellyfishUnit target:health.keySet()) {
                var request=new ReservedSkillRequest(skill,source.getId(),source.getUUID(),new ReservedSkillRequest.EntityTarget(target.getId(),target.getUUID()));
                h.assertTrue(ReservedSkillService.request(owner,request)==ReservedSkillService.Result.DISABLED,"even valid owner/source/target cannot open the legacy reserved API");
                h.assertTrue(!source.doHurtTarget(target),"no direct damage against any relation");
            }
            h.assertTrue(ReservedSkillService.request(owner,new ReservedSkillRequest(skill,source.getId(),source.getUUID(),new ReservedSkillRequest.EntityTarget(enemy.getId(),UUID.randomUUID())))==ReservedSkillService.Result.INVALID_TARGET,"stale target UUID rejected");
        }
        h.assertTrue(ReservedSkillService.request(owner,new ReservedSkillRequest(ReservedSkillRequest.Skill.PINK_SLIME_SUPPORT,source.getId(),source.getUUID(),new ReservedSkillRequest.Self()))==ReservedSkillService.Result.INVALID_SOURCE,"different unit's typed skill cannot be confused with support");
        h.startSequence().thenWaitUntil(()->h.assertTrue(source.tickCount>2&&own.tickCount>2&&ally.tickCount>2&&enemy.tickCount>2,"all fixtures published before owner preference requests"))
          .thenExecute(()->{
            for(JellyfishUnit fixture:List.of(source,own,ally,enemy)) {
                FakePlayer actualOwner=player(h,fixture.getOwnerName().substring(PREFIX.length()));
                h.assertTrue(SkillService.setAutocast(actualOwner,UnitSkill.JELLY_FLOW,fixture.getId(),fixture.getUUID(),fixture.rts().skills().revision(),false)==SkillResult.SUCCESS,"actual owner disables new automatic flow to isolate old reserved request");
                fixture.setNoAi(false);
            }
          }).thenIdle(70).thenExecute(()->{
            for(var entry:health.entrySet())h.assertTrue(entry.getKey().getHealth()==entry.getValue()&&entry.getKey().getActiveEffects().isEmpty()&&entry.getKey().getAbsorptionAmount()==0,"no delayed heal, damage, buff or shield");
            var ledger=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(source.getOwnerName())).findFirst().orElseThrow();
            h.assertTrue(ledger.food==500&&ledger.wood==500&&ledger.ore==500,"legacy request changes no global resources: "+ledger.food+"/"+ledger.wood+"/"+ledger.ore);
            h.assertTrue(source.getAbilities().get().equals(List.of(UnitSkillAbility.JELLY_FLOW)),"only separately approved flow button: "+source.getAbilities().get());
            System.out.println("DREAMSEEKERS_JELLYFISH_TYPED_DISABLED_SUPPORT_PASS");
        }).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=220)
    public static void friendlyFollowMovesButRevokedAllianceCannotTrackOrDamage(GameTestHelper h) {
        floor(h);JellyfishUnit source=spawn(h,"Follow",3,18),ally=spawn(h,"FollowAlly",21,18);
        AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());Vec3[] start={null},stop={null};int[] revokedTick={0};float hp=ally.getHealth();
        var sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(source.tickCount>2&&ally.tickCount>2,"real follow fixtures ready"))
                .thenExecute(()->{start[0]=source.position();System.out.println("JELLY_FOLLOW before "+followState(source,ally));order(source,UnitAction.FOLLOW,ally.getId(),BlockPos.ZERO);}).thenIdle(40)
                .thenExecute(()->{h.assertTrue(source.getFollowTarget()==ally&&source.position().distanceToSqr(start[0])>1,"actual friendly flight follow moves");revokedTick[0]=source.tickCount;System.out.println("JELLY_FOLLOW revoke "+followState(source,ally));AlliancesServerEvents.removeAlliance(source.getOwnerName(),ally.getOwnerName());})
                .thenWaitUntil(()->h.assertTrue(source.tickCount>=revokedTick[0]+10,"wait ten actual revoke processing ticks")).thenExecute(()->{stop[0]=source.position();System.out.println("JELLY_FOLLOW settled "+followState(source,ally));});
        for(int i=0;i<60;i++)sequence.thenIdle(1).thenExecute(()->h.assertTrue(source.getFollowTarget()==null&&source.getMoveGoal().getMoveTarget()==null&&source.getTarget()==null
                &&source.position().distanceToSqr(stop[0])<.1&&ally.getHealth()==hp,"revoked follow stays canceled without covert movement or attacks every sampled tick: start="+stop[0]+" "+followState(source,ally)));
        sequence.thenExecute(()->System.out.println("DREAMSEEKERS_JELLYFISH_FOLLOW_REVOKE_PASS")).thenSucceed();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=920)
    public static void stopClearsDeferredNavigationTargetForAddonButNotWildControl(GameTestHelper h) throws Exception {
        floor(h);JellyfishUnit jelly=spawn(h,"DeferredFlight",4,6);
        PinkSlimeUnit slime=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());slime.setOwnerName(jelly.getOwnerName());
        BlockPos p=h.absolutePos(new BlockPos(4,1,12));slime.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(slime);h.assertTrue(h.getLevel().addFreshEntity(slime),"actual prior guard regression control");
        // Original flying navigation is the closest native positive control and needs no fabricated ground flag.
        var wild=net.pasterdream.init.PasterdreamModEntities.JELLYFISH.get().create(h.getLevel());
        wild.moveTo(p.getX()+.5,p.getY()+2,p.getZ()+4.5);wild.setNoAi(true);wild.setPersistenceRequired();FIXTURES.add(wild);
        h.assertTrue(h.getLevel().addFreshEntity(wild),"original jellyfish control enters world with random goals disabled only for this fixture");
        var pending=net.minecraft.world.entity.ai.navigation.PathNavigation.class.getDeclaredField("hasDelayedRecomputation");pending.setAccessible(true);
        var target=net.minecraft.world.entity.ai.navigation.PathNavigation.class.getDeclaredField("targetPos");target.setAccessible(true);
        Vec3[] flightStop={null},groundStop={null};
        var sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(jelly.tickCount>2&&slime.tickCount>2&&wild.tickCount>2,"actual ground and flight controllers ready: jelly="+jelly.tickCount+" slime="+slime.tickCount+" wild="+wild.tickCount+" wildPos="+wild.position()+" removed="+wild.isRemoved()))
          .thenExecute(()->{
            h.assertTrue(!UnitServerEvents.rtsPathfinding,"probe exercises normal vanilla navigation mode, does not mutate global pathfinding mode");
            try {
                for(net.minecraft.world.entity.Mob actor:List.of(jelly,slime,wild)) {
                    var nav=actor.getNavigation();BlockPos destination=actor.blockPosition().east(13);
                    var path=nav.createPath(destination,0);h.assertTrue(path!=null,"real vanilla path exists for delayed recomputation fixture: actor="+actor.getType()+" pos="+actor.position()+" navigation="+nav.getClass()+" destination="+destination);nav.moveTo(path,1);
                    nav.recomputePath();nav.recomputePath();
                    h.assertTrue(pending.getBoolean(nav)&&target.get(nav)!=null,"two real same-tick recompute calls arm vanilla deferred target");
                    if(actor==wild) nav.stop();
                    else new UnitActionItem(((com.solegendary.reignofnether.unit.interfaces.Unit)actor).getOwnerName(),UnitAction.STOP,-1,new int[]{actor.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                    System.out.println("JELLY_DEFERRED_STOP actor="+actor.getType()+" pending="+pending.getBoolean(nav)+" target="+target.get(nav)+" navDone="+nav.isDone());
                    if(actor==wild)h.assertTrue(pending.getBoolean(nav)&&target.get(nav)!=null,"original non-RTS stop semantics untouched");
                    else h.assertTrue(!pending.getBoolean(nav)&&target.get(nav)==null&&nav.isDone(),"addon STOP must clear deferred recomputation and stale destination, not merely path");
                }
            }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
            flightStop[0]=jelly.position();groundStop[0]=slime.position();
          });
        for(int i=0;i<70;i++)sequence.thenIdle(1).thenExecute(()->{
            h.assertTrue(jelly.getNavigation().isDone()&&slime.getNavigation().isDone()&&jelly.getMoveGoal().getMoveTarget()==null&&slime.getMoveGoal().getMoveTarget()==null,"no delayed cancelled path resurrects");
            h.assertTrue(jelly.position().distanceToSqr(flightStop[0])<.1&&slime.position().distanceToSqr(groundStop[0])<.1,"cancelled ground/flight actors remain stationary through deferred window");
        });
        sequence.thenExecute(()->{
            wild.getNavigation().tick();
            h.assertTrue(!wild.getNavigation().isDone(),"real original navigation tick still reconstructs its stopped pending path; scope is not global");
            order(jelly,UnitAction.MOVE,-1,h.absolutePos(new BlockPos(17,0,6)));
            UnitServerEvents.addActionItem(slime.getOwnerName(),UnitAction.MOVE,-1,new int[]{slime.getId()},h.absolutePos(new BlockPos(17,0,12)),BlockPos.ZERO);
        }).thenIdle(330).thenExecute(()->{
            h.assertTrue(Math.abs(jelly.getX()-(h.absolutePos(new BlockPos(17,0,6)).getX()+.5))<2&&Math.abs(slime.getX()-(h.absolutePos(new BlockPos(17,0,12)).getX()+.5))<2,"genuinely new MOVE still works after full cancellation on ground and in flight");
            System.out.println("DREAMSEEKERS_ADDON_DEFERRED_NAVIGATION_STOP_WILD_CONTROL_PASS");
        }).thenSucceed();
    }

    private static String followState(JellyfishUnit s,JellyfishUnit a){return "tick="+s.tickCount+" pos="+s.position()+" velocity="+s.getDeltaMovement()+" follow="+s.getFollowTarget()+" move="+s.getMoveGoal().getMoveTarget()+" nav="+s.getNavigation().isDone()+" target="+s.getTarget()+" allyHp="+a.getHealth();}
    private static String account(String suffix){String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));}return name;}
    private static JellyfishUnit spawn(GameTestHelper h,String name,int x,int z){JellyfishUnit e=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());e.setOwnerName(account(name));BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"paid jellyfish added");return e;}
    private static FakePlayer player(GameTestHelper h,String name){String n=PREFIX+name;FakePlayer p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(n.getBytes(java.nio.charset.StandardCharsets.UTF_8)),n));PLAYERS.add(p);return p;}
    private static void order(JellyfishUnit e,UnitAction action,int target,BlockPos pos){UnitServerEvents.addActionItem(e.getOwnerName(),action,target,new int[]{e.getId()},pos,BlockPos.ZERO);}
    private static AABB area(GameTestHelper h){return new AABB(h.absolutePos(BlockPos.ZERO),h.absolutePos(new BlockPos(24,10,24)));}
    private static void floor(GameTestHelper h) {
        // Ordinary aggregate terrain contains gravel above the cleared test volume. Stabilize
        // it before clearing/spawning, as the existing collision-flight fixtures already do.
        BlockPos base=h.absolutePos(BlockPos.ZERO);
        for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,10,z)),Blocks.STONE.defaultBlockState(),2);
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new AABB(base.getX(),base.getY()+1,base.getZ(),base.getX()+24,h.getLevel().getMaxBuildHeight(),base.getZ()+24)).forEach(Entity::discard);
        for(int x=0;x<24;x++)for(int z=0;z<24;z++) {
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x,0,z)),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<10;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),Blocks.AIR.defaultBlockState(),2);
        }
        h.getLevel().getEntitiesOfClass(ItemEntity.class,area(h)).forEach(Entity::discard);
    }
}
