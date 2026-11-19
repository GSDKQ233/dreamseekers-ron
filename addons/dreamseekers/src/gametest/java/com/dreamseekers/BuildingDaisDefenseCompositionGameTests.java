// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.BuildingAbility;
import com.dreamseekers.buildingability.BuildingAbilityService;
import com.dreamseekers.buildingability.BuildingAbilityState;
import com.dreamseekers.buildingability.DaisWardEffects;
import com.dreamseekers.resonance.ResonanceResearch;
import com.dreamseekers.skill.SkillNetwork;
import com.dreamseekers.skill.SkillRequest;
import com.dreamseekers.skill.SkillResult;
import com.dreamseekers.skill.SnailShellFormation;
import com.dreamseekers.skill.UnitSkill;
import com.dreamseekers.unit.BasaltSnailUnit;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** External source-only addition. Genuine paid Dais, real skill requests, native damage writes and
 * normal Skeleton Arrow factories. No handcrafted ward/guard/formation plans or damage receipts.
 * BeetleGuard is its existing nearby-combatant PASSIVE, not the unrelated BeetleAdvance active.
 */
@GameTestHolder("dreamseekers_building_boundary") @PrefixGameTestTemplate(false)
public final class BuildingDaisDefenseCompositionGameTests {
    private static final String DEFENSE="building_ward_defense", REENTRANT="building_ward_reentrant";
    private static final List<ResonanceNetworkGameTests.F> FIXTURES=new ArrayList<>();
    private static final List<DaisCompositionReceiptProbe> PROBES=new ArrayList<>();
    private static final List<Entity> PROJECTILES=new ArrayList<>();
    private BuildingDaisDefenseCompositionGameTests() { }
    @AfterBatch(batch=DEFENSE) public static void afterDefense(ServerLevel ignored){cleanup();}
    @AfterBatch(batch=REENTRANT) public static void afterReentrant(ServerLevel ignored){cleanup();}
    private static void cleanup(){
        for(var probe:List.copyOf(PROBES))probe.close();
        for(var projectile:List.copyOf(PROJECTILES))if(!projectile.isRemoved())projectile.discard();PROJECTILES.clear();
        for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();
    }
    private static ResonanceNetworkGameTests.F fixture(GameTestHelper h,String label,int index){
        var f=new ResonanceNetworkGameTests.F(h,"WardDefense_"+label,index);FIXTURES.add(f);return f;
    }
    private static void near(GameTestHelper h,double actual,double expected,String why){
        h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" actual="+actual+" expected="+expected);
    }
    private static <T extends Mob> T publish(ResonanceNetworkGameTests.F f,T body,int x,int z,String owner){
        f.h.assertTrue(body!=null&&body instanceof Unit,"real registered RTS fixture");
        ((Unit)body).setOwnerName(owner);body.setNoAi(true);BlockPos p=f.dais.originPos.offset(x,1,z);f.load(p);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            f.level.setBlock(p.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<5;y++)f.level.setBlock(p.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);
        }
        position(body,Vec3.atBottomCenterOf(p));
        f.h.assertTrue(f.level.addFreshEntity(body),"real body added before natural publication wait");f.units.add(body);return body;
    }
    private static void position(Entity entity,Vec3 point){entity.moveTo(point.x,point.y,point.z);entity.setDeltaMovement(Vec3.ZERO);}
    private static void neutral(LivingEntity body){
        body.getAttribute(Attributes.ARMOR).setBaseValue(0);body.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);body.setAbsorptionAmount(0);body.removeAllEffects();
        for(var attribute:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))
            if(body.getAttribute(attribute)!=null)body.getAttribute(attribute).setBaseValue(0);
        body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);body.setHealth(1000);
    }
    private static void damage(LivingEntity body){
        body.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);
        if(body.getAttribute(Attributes.ATTACK_DAMAGE)!=null)body.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
        if(body.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get())!=null)body.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);
        if(body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get())!=null)body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);
    }
    private static void ready(ResonanceNetworkGameTests.F f,LivingEntity...bodies){
        for(var body:bodies){f.ready(body);f.h.assertTrue(body.tickCount>=30,"natural initial owner/attribute setup completed");}
    }
    private static String castWard(ResonanceNetworkGameTests.F f){
        var view=BuildingAbilityService.view(f.peer.actor,f.dais);
        return view==null?"missing_view":BuildingAbilityService.cast(f.peer.actor,f.dais,view.instance(),view.capture(),view.generation(),view.revision());
    }
    private static long paidWard(GameTestHelper h,ResonanceNetworkGameTests.F f){
        ResearchServerEvents.addResearch(f.owner,ResonanceResearch.ID);int shadow=f.shadow();long revision=f.account.revision;
        h.assertTrue(castWard(f).equals("success")&&f.shadow()==shadow-20,"real authenticated connected/tuned Dais pays exactly20S");
        var state=f.dais.getDataStorage().getData(BuildingAbilityState.TYPE);
        h.assertTrue(state.revision()==1&&state.readyAt(BuildingAbility.DAIS_WARD)==f.level.getGameTime()+900&&f.account.revision==revision+1,
                "single original paid building revision and900tick cooldown");
        h.assertTrue(castWard(f).equals("cooldown")&&f.shadow()==shadow-20&&f.account.revision==revision+1,"same-tick second cast cannot repay or refresh");
        return state.readyAt(BuildingAbility.DAIS_WARD);
    }
    private static float hit(GameTestHelper h,LivingEntity target,DamageSource damage){
        float before=target.getHealth();target.invulnerableTime=0;
        h.assertTrue(target.hurt(damage,10),"real native hurt accepts positive ordinary damage");return before-target.getHealth();
    }
    private static void detachDais(ResonanceNetworkGameTests.F f){
        f.dais.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(f.dais);
    }
    private static void originalPayment(GameTestHelper h,ResonanceNetworkGameTests.F f,long ready,int shadow,long revision){
        h.assertTrue(f.shadow()==shadow&&f.account.revision==revision&&f.dais.getDataStorage().getData(BuildingAbilityState.TYPE).revision()==1
                &&f.dais.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.DAIS_WARD)==ready,
                "damage, native defenses and source cleanup never repay/refund/refresh original Dais cost/CD");
    }
    private static final class DaisCompositionReceiptProbe implements AutoCloseable {
        final Consumer<LivingDamageEvent> action;
        DaisCompositionReceiptProbe(Consumer<LivingDamageEvent> action){this.action=action;PROBES.add(this);MinecraftForge.EVENT_BUS.register(this);}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void daisCompositionDamageReceipt(LivingDamageEvent event){action.accept(event);}
        @Override public void close(){MinecraftForge.EVENT_BUS.unregister(this);PROBES.remove(this);}
    }

    @GameTest(templateNamespace="dreamseekers_building_boundary",template="dreamseekers_test_arena",batch=DEFENSE,timeoutTicks=140)
    public static void realBeetlePassiveAndPaidDaisTakeOneTenPercentMaximumAndPreserveNativeResistance(GameTestHelper h){
        var f=fixture(h,"Beetle",151);
        var target=publish(f,DreamseekersMod.BEETLE.get().create(f.level),-3,-3,f.owner);
        var supporter=publish(f,DreamseekersMod.PINK_SLIME_UNIT.get().create(f.level),-3,-9,f.owner);
        var enemy=publish(f,DreamseekersMod.PINK_SLIME_UNIT.get().create(f.level),-9,-3,f.owner+"_enemy");
        h.startSequence().thenWaitUntil(()->ready(f,target,supporter,enemy)).thenExecute(()->{
            neutral(target);damage(enemy);DamageSource source=target.damageSources().mobAttack(enemy);int[] events={0};
            var probe=new DaisCompositionReceiptProbe(event->{if(event.getEntity()==target&&event.getSource()==source){events[0]++;h.assertTrue(event.getAmount()>0,"actual final native event reached");}});
            near(h,hit(h,target,source),10,"real ordinary hit without nearby combatant has no BeetleGuard");
            h.assertTrue(target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,80,0)),"declared real native Resistance stimulus, not a fabricated receipt");
            float nativeBaseline=hit(h,target,source);h.assertTrue(nativeBaseline>0&&nativeBaseline<10,"native resistance actually reduced baseline hit");target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            Vec3 far=supporter.position();position(supporter,target.position().add(0,0,2));
            near(h,hit(h,target,source),9,"actual nearby combatant enables beetle passive exactly10percent");
            long readyAt=paidWard(h,f);int paid=f.shadow();long revision=f.account.revision;
            near(h,hit(h,target,source),9,"Dais10 plus real beetle10 is max10, never8.1 damage");
            position(supporter,far);near(h,hit(h,target,source),9,"removing real supporter leaves independently-paid Dais10");
            h.assertTrue(target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,80,0)),"same real native Resistance after paid ward");
            near(h,hit(h,target,source),nativeBaseline,"ward never multiplies extra10 over actual native Resistance calculation");target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            detachDais(f);near(h,hit(h,target,source),10,"original building detach ends ward at actual hurt boundary without END tick");
            h.assertTrue(events[0]==7,"all seven independent actual LivingDamage events occurred, no synthetic return-only assertion");
            originalPayment(h,f,readyAt,paid,revision);probe.close();f.close();h.succeed();
        });
    }

    private static void formation(GameTestHelper h,ResonanceNetworkGameTests.F f,BasaltSnailUnit source){
        int food=f.account.food,wood=f.account.wood,ore=f.account.ore,shadow=f.shadow();long revision=source.rts().skills().revision();
        var request=new SkillRequest(UnitSkill.SNAIL_SHELL_FORMATION,source.getId(),source.getUUID(),revision,source.getId(),source.getUUID());
        h.assertTrue(new SkillNetwork.Cast(request).execute(f.peer.actor)==SkillResult.SUCCESS,"real authenticated self-cast creates original Snail formation");
        h.assertTrue(SnailShellFormation.active(source)&&source.rts().skills().cooldownTicks(UnitSkill.SNAIL_SHELL_FORMATION)==480
                &&source.rts().skills().revision()>revision,"actual formation spends personal480tick skill cooldown");
        h.assertTrue(f.account.food==food&&f.account.wood==wood&&f.account.ore==ore&&f.shadow()==shadow,"existing Snail active has no invented resource fee");
    }
    private static final class Shot {
        final Mob shooter,target;final Vec3 start;final boolean ward;AbstractArrow arrow;double travel;int events;
        Shot(Mob shooter,Mob target,boolean ward){this.shooter=shooter;this.target=target;this.ward=ward;start=shooter.position();}
    }
    private static Shot launch(GameTestHelper h,Mob shooter,Mob target,boolean ward,Map<LivingEntity,Shot> pending){
        var shot=new Shot(shooter,target,ward);pending.put(target,shot);float before=target.getHealth();target.invulnerableTime=0;
        ((RangedAttackerUnit)shooter).performUnitRangedAttack(target,1);
        var arrows=h.getLevel().getEntitiesOfClass(AbstractArrow.class,shooter.getBoundingBox().inflate(2),a->a.getOwner()==shooter);
        h.assertTrue(arrows.size()==1,"exactly one genuine Skeleton factory Arrow");shot.arrow=arrows.get(0);PROJECTILES.add(shot.arrow);
        h.assertTrue(target.getHealth()==before,"factory release did not directly fabricate target damage");return shot;
    }
    private static void observe(GameTestSequence sequence,Shot[] shots,int ticks){
        for(int i=0;i<ticks;i++)sequence.thenIdle(1).thenExecute(()->{for(var shot:shots)shot.travel=Math.max(shot.travel,shot.arrow.position().distanceTo(shot.start));});
    }
    private static void flight(GameTestHelper h,Shot shot,String why){
        h.assertTrue(shot.events==1&&shot.travel>2&&shot.arrow.isRemoved(),why+" actual factory flight/one native collision: events="+shot.events+" travel="+shot.travel);
    }
    @GameTest(templateNamespace="dreamseekers_building_boundary",template="dreamseekers_test_arena",batch=DEFENSE,timeoutTicks=210)
    public static void realSnailFormationAndBeetleAndDaisUseTwentyMaximumOnNaturalPrimaryArrows(GameTestHelper h){
        var f=fixture(h,"Snail",152);
        var combined=publish(f,DreamseekersMod.BEETLE.get().create(f.level),-3,-3,f.owner);
        var wardOnly=publish(f,DreamseekersMod.GOLDEN_FOX_UNIT.get().create(f.level),-3,-9,f.owner);
        var control=publish(f,DreamseekersMod.BEETLE.get().create(f.level),-3,-18,f.owner);
        var nearSnail=publish(f,DreamseekersMod.BASALT_SNAIL.get().create(f.level),-1,-3,f.owner);
        var farSnail=publish(f,DreamseekersMod.BASALT_SNAIL.get().create(f.level),-1,-18,f.owner);
        var targets=List.<Mob>of(combined,wardOnly,control);var shooters=new ArrayList<Mob>();
        for(int z:new int[]{-3,-9,-18})shooters.add(publish(f,EntityRegistrar.SKELETON_UNIT.get().create(f.level),-9,z,f.owner+"_enemy"));
        Map<LivingEntity,Shot> pending=new IdentityHashMap<>();Shot[] first=new Shot[3],later=new Shot[1];
        long[] started={0},readyAt={0},revision={0};int[] paid={0};float[] beforeLater={0};
        var probe=new DaisCompositionReceiptProbe(event->{var shot=pending.get(event.getEntity());if(shot==null||event.getSource().getEntity()!=shot.shooter)return;
            shot.events++;h.assertTrue(shot.arrow!=null&&shot.events==1,"real projectile was published before single native damage callback");
            Entity proxy=event.getSource().getDirectEntity();
            h.assertTrue(proxy instanceof Arrow&&proxy!=shot.arrow&&!proxy.isAddedToWorld()&&f.level.getEntity(proxy.getUUID())==null,"native Arrow proxy provenance comes from actual flight, not handcrafted primary scope");
            h.assertTrue(f.level.getEntity(shot.arrow.getId())==shot.arrow&&f.level.getEntity(shot.arrow.getUUID())==shot.arrow,"original physical Arrow still published at callback");
            h.assertTrue((DaisWardEffects.matching(shot.target,event.getSource())!=null)==shot.ward,"actual hit captures ward only for original in-range recipient/source");
            near(h,event.getAmount(),10,"real pre-final event has native10 before max-category final reduction");
        });
        var sequence=h.startSequence().thenWaitUntil(()->{ready(f,combined,wardOnly,control,nearSnail,farSnail);for(var shooter:shooters)ready(f,shooter);}).thenExecute(()->{
            for(var target:targets)neutral(target);
            for(var shooter:shooters){((com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit)shooter).setupEquipmentAndUpgradesServer();damage(shooter);}
            formation(h,f,nearSnail);formation(h,f,farSnail);started[0]=f.level.getGameTime();
            readyAt[0]=paidWard(h,f);paid[0]=f.shadow();revision[0]=f.account.revision;
            h.assertTrue(DaisWardEffects.active(combined)&&DaisWardEffects.active(wardOnly)&&!DaisWardEffects.active(control),"real source radius separates paired supported and unsupported lanes");
            for(int i=0;i<3;i++)first[i]=launch(h,shooters.get(i),targets.get(i),i<2,pending);
        });
        observe(sequence,first,35);
        sequence.thenExecute(()->{
            for(int i=0;i<3;i++)flight(h,first[i],"composition lane="+i);
            near(h,1000-combined.getHealth(),8,"actual Snail20+Beetle10+Dais10 takes exactly20, never chained multipliers");
            near(h,1000-wardOnly.getHealth(),9,"matching in-range ordinary primary has independently-live Dais10");
            near(h,1000-control.getHealth(),8,"out-of-Dais real Snail20+Beetle10 control is identical to combined lane");
            long elapsed=f.level.getGameTime()-started[0];
            for(var snail:List.of(nearSnail,farSnail))h.assertTrue(SnailShellFormation.active(snail)&&snail.rts().skills().cooldownTicks(UnitSkill.SNAIL_SHELL_FORMATION)==480-elapsed,"real Snail personal cooldown naturally decreases without Dais refresh");
            originalPayment(h,f,readyAt[0],paid[0],revision[0]);detachDais(f);beforeLater[0]=wardOnly.getHealth();
            later[0]=launch(h,shooters.get(1),wardOnly,false,pending);
        });
        observe(sequence,later,35);
        sequence.thenExecute(()->{
            flight(h,later[0],"fresh real arrow after original source detach");near(h,beforeLater[0]-wardOnly.getHealth(),10,"fresh original-factory projectile observes no stale ward after source cleanup");
            originalPayment(h,f,readyAt[0],paid[0],revision[0]);probe.close();f.close();h.succeed();
        });
    }

    @GameTest(templateNamespace="dreamseekers_building_boundary",template="dreamseekers_test_arena",batch=REENTRANT,timeoutTicks=140)
    public static void sameTargetSameDamageSourceNestedHurtHasIndependentWardAndNativeReceipts(GameTestHelper h){
        var f=fixture(h,"Reentrant",153);var target=f.unit(f.dais,-3,-3);
        var enemy=publish(f,DreamseekersMod.PINK_SLIME_UNIT.get().create(f.level),-9,-3,f.owner+"_enemy");
        h.startSequence().thenWaitUntil(()->ready(f,target,enemy)).thenExecute(()->{
            neutral(target);damage(enemy);DamageSource exact=target.damageSources().mobAttack(enemy);
            near(h,hit(h,target,exact),10,"unprotected same actual target/source native baseline");
            h.assertTrue(target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,80,0)),"real native reference effect");
            float nativeBaseline=hit(h,target,exact);h.assertTrue(nativeBaseline>0&&nativeBaseline<10,"actual native reference reduced HP");target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            long readyAt=paidWard(h,f);int paid=f.shadow();long revision=f.account.revision;
            int[] depth={0},events={0};boolean[] nativeInner={false};float[] innerLoss={0};DaisWardEffects.HitPlan[] outer={null};
            var probe=new DaisCompositionReceiptProbe(event->{if(event.getEntity()!=target||event.getSource()!=exact)return;
                events[0]++;var current=DaisWardEffects.matching(target,exact);h.assertTrue(current!=null,"every actual nested hurt owns a real current ward plan");
                if(depth[0]==0){
                    depth[0]=1;outer[0]=current;float before=target.getHealth();
                    if(nativeInner[0])h.assertTrue(target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,80,0)),"only inner actual calculation receives native Resistance");
                    try {target.invulnerableTime=0;h.assertTrue(target.hurt(exact,10),"same target/same DS actually reenters native hurt");innerLoss[0]=before-target.getHealth();}
                    finally {if(nativeInner[0])target.removeEffect(MobEffects.DAMAGE_RESISTANCE);target.invulnerableTime=0;}
                    h.assertTrue(DaisWardEffects.matching(target,exact)==outer[0],"nested finally restores exact original outer plan, not a replacement");depth[0]=2;
                }else {h.assertTrue(depth[0]==1&&current!=outer[0],"one guarded recursive entry has a distinct inner plan and cannot recurse again");}
            });
            float before=target.getHealth();target.invulnerableTime=0;h.assertTrue(target.hurt(exact,10),"real outer positive ward hit");
            near(h,innerLoss[0],9,"independent inner ward reduces its own actual HP write once");near(h,before-target.getHealth(),18,"same-target two actual writes are9+9, not one borrowed plan or chained multipliers");
            h.assertTrue(events[0]==2&&DaisWardEffects.matching(target,exact)==null,"exactly two real final events and no frame after outer return");
            depth[0]=0;events[0]=0;nativeInner[0]=true;before=target.getHealth();target.invulnerableTime=0;h.assertTrue(target.hurt(exact,10),"second real outer hit with native-only inner stimulus");
            near(h,innerLoss[0],nativeBaseline,"inner native Resistance dominates its own ward without second10percent");
            near(h,before-target.getHealth(),nativeBaseline+9,"inner native receipt cannot suppress independent outer ward10");
            h.assertTrue(events[0]==2&&DaisWardEffects.matching(target,exact)==null&&!target.hasEffect(MobEffects.DAMAGE_RESISTANCE),"second pair retires both scopes and native test stimulus");
            probe.close();near(h,hit(h,target,exact),9,"later clean hit neither borrows stale native receipt nor loses still-live original ward");
            detachDais(f);near(h,hit(h,target,exact),10,"actual hit after original source removal gets no retired ward");
            originalPayment(h,f,readyAt,paid,revision);f.close();h.succeed();
        });
    }
}
