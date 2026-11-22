package com.dreamseekers;

import com.dreamseekers.aaroncos.ControlledAaroncosHand;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** Real paid ground casts and natural END pulses; no manual pulse/cooldown injection. */
@GameTestHolder("dreamseekers_aaroncos_vortex")
@PrefixGameTestTemplate(false)
public final class AaroncosVortexGameTests {
    private static final String BATCH="dreamseekers_aaroncos_vortex";
    private static int arenaIndex;
    private static final List<F> FIXTURES=new ArrayList<>();
    static final class F implements AutoCloseable {
        final GameTestHelper h;final String owner,enemy;final Resources account;final PassiveIncomePeer peer;
        final BlockPos center;final List<Mob> bodies=new ArrayList<>();final List<Object> listeners=new ArrayList<>();
        F(GameTestHelper h,String name) {
            this.h=h;owner="Vortex_"+name;enemy=owner+"_enemy";
            for(String n:List.of(owner,enemy))PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(n,DreamseekersMod.DREAMSEEKERS));
            account=new Resources(owner,1000,1000,1000);account.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(account);
            ResourcesServerEvents.resourcesList.add(new Resources(enemy,1000,1000,1000));peer=new PassiveIncomePeer(h.getLevel(),owner);
            center=new BlockPos(155648+128*arenaIndex++,100,155648);
            for(int x=(center.getX()-16)>>4;x<=(center.getX()+16)>>4;x++)for(int z=(center.getZ()-16)>>4;z<=(center.getZ()+16)>>4;z++)h.getLevel().setChunkForced(x,z,true);
            for(BlockPos at:BlockPos.betweenClosed(center.offset(-16,-1,-16),center.offset(16,7,16)))h.getLevel().setBlock(at,at.getY()==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            FIXTURES.add(this);
        }
        <T extends Mob> T add(T body,String name,double x,double z) {
            ((Unit)body).setOwnerName(name);body.setNoAi(true);body.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);body.moveTo(center.getX()+x+.5,100,center.getZ()+z+.5);
            h.assertTrue(h.getLevel().addFreshEntity(body),"real entity published");bodies.add(body);return body;
        }
        AaroncosRightHandUnit hand() { return add(DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel()),owner,0,0); }
        void unlock(){com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,com.dreamseekers.unitprogression.ExpansionResearch.AARONCOS_VORTEX_ID);}
        SkillGroundRequest ground(AaroncosRightHandUnit s){return new SkillGroundRequest(UnitSkill.AARONCOS_RIGHT_VORTEX,s.getId(),s.getUUID(),s.rts().skills().revision(),center.offset(5,0,0));}
        SkillResult cast(AaroncosRightHandUnit s){return SkillService.requestGround(peer.actor,ground(s));}
        GoldenFoxUnit target(double x,double z) {
            var t=add(DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel()),enemy,x,z);
            t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);t.setHealth(1000);
            t.getAttribute(Attributes.ARMOR).setBaseValue(0);t.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
            t.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);t.setAbsorptionAmount(0);
            for(var a:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(t.getAttribute(a)!=null)t.getAttribute(a).setBaseValue(0);
            return t;
        }
        SkillRequest request(ControlledAaroncosHand s,LivingEntity target){return new SkillRequest(SkillService.skillFor(s),s.mob().getId(),s.mob().getUUID(),s.rts().skills().revision(),target.getId(),target.getUUID());}
        SkillResult cast(ControlledAaroncosHand s,LivingEntity target){
            var packet=new SkillNetwork.Cast(request(s,target));var bytes=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{packet.encode(bytes);var decoded=SkillNetwork.Cast.decode(bytes);h.assertTrue(decoded.equals(packet)&&bytes.readableBytes()==0,"real request codec exact roundtrip");return decoded.execute(peer.actor);}finally{bytes.release();}
        }
        void guard(Consumer<LivingDamageEvent> listener){listeners.add(listener);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,listener);}
        int shadow(){return account.extra.get(DreamPassiveEconomy.SHADOW);}
        void hp(LivingEntity body,float expected,String why){h.assertTrue(Math.abs(body.getHealth()-expected)<.001,why+" actual="+body.getHealth()+" expected="+expected);}
        @Override public void close(){listeners.forEach(MinecraftForge.EVENT_BUS::unregister);listeners.clear();bodies.forEach(Entity::discard);peer.close();
            com.solegendary.reignofnether.research.ResearchServerEvents.removeResearch(owner,com.dreamseekers.unitprogression.ExpansionResearch.AARONCOS_VORTEX_ID);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner)||p.name.equals(enemy));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(owner)||r.ownerName.equals(enemy));for(int x=(center.getX()-16)>>4;x<=(center.getX()+16)>>4;x++)for(int z=(center.getZ()-16)>>4;z<=(center.getZ()+16)>>4;z++)h.getLevel().setChunkForced(x,z,false);FIXTURES.remove(this);}
        void done(){close();h.succeed();}
    }
    @AfterBatch(batch=BATCH) public static void cleanup(net.minecraft.server.level.ServerLevel level){for(F f:List.copyOf(FIXTURES))f.close();}

    @GameTest(templateNamespace="dreamseekers_aaroncos_vortex",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=900)
    public static void researchPaidClockSixPulsesSlowRefreshAndTerrain(GameTestHelper h){
        F f=new F(h,"clock");var s=f.hand();var t=f.target(10.25,0);var ally=f.target(5,2);ally.setOwnerName(f.owner);
        var far=f.target(12,0);var screened=f.target(5,-4);
        for(BlockPos p:BlockPos.betweenClosed(f.center.offset(4,0,-2),f.center.offset(6,4,-2)))
            h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
        double speed=t.getAttributeValue(Attributes.MOVEMENT_SPEED);long[] start={0};List<Long> hits=new ArrayList<>();
        f.guard(e->{if(AaroncosVortex.isPulse(e.getSource())&&e.getEntity()==t)hits.add(h.getLevel().getGameTime());});
        var seq=h.startSequence().thenIdle(30).thenExecute(()->{
            h.assertTrue(f.cast(s)==SkillResult.DISABLED&&f.shadow()==500,"research locked no spend");f.unlock();
            f.account.extra.put(DreamPassiveEconomy.SHADOW,39);h.assertTrue(f.cast(s)==SkillResult.INSUFFICIENT_RESOURCES,"insufficient resources rejected");f.account.extra.put(DreamPassiveEconomy.SHADOW,500);
            var center=net.minecraft.world.phys.Vec3.atBottomCenterOf(f.ground(s).position()).add(0,.25,0);
            double distance=t.getBoundingBox().getCenter().distanceToSqr(center);
            h.assertTrue(distance>4*4&&distance<=6*6,"target is inside the new six-block radius, outside the former four");
            var q=f.ground(s);start[0]=h.getLevel().getGameTime();h.assertTrue(SkillService.requestGround(f.peer.actor,q)==SkillResult.SUCCESS,"real authorized ground cast");
            h.assertTrue(f.shadow()==460&&s.rts().skills().cooldownTicks(q.skill())==800,"exact cost and cooldown");h.assertTrue(SkillService.requestGround(f.peer.actor,q)==SkillResult.REPLAY&&f.shadow()==460,"replay cannot pay twice");
        });
        for(int i=1;i<=6;i++){final int n=i;seq.thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>=start[0]+20*n-1,"before pulse"))
            .thenExecute(()->f.hp(t,1000-5*(n-1),"no early pulse"))
            .thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>=start[0]+20*n+1,"after natural END pulse"))
            .thenExecute(()->{
                f.hp(t,1000-5*n,"fixed five magic damage each pulse");f.hp(s,420,"no self damage");
                for(var protectedUnit:List.of(ally,far,screened)){
                    f.hp(protectedUnit,1000,"no friendly, distant or through-wall damage");
                    h.assertTrue(AaroncosVortex.negative(protectedUnit)==null,"no slow on a rejected target");
                }
                h.assertTrue(hits.size()==n&&hits.get(n-1)==start[0]+20*n,"exact pulse timeline");
                h.assertTrue(Math.abs(t.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed*.65)<1e-6,"35 percent slow never stacked");
            });}
        seq.thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>=start[0]+159,"last pulse slow still current"))
            .thenExecute(()->h.assertTrue(AaroncosVortex.negative(t)!=null,"sixth pulse refreshed the final 40-tick slow"))
            .thenIdle(2).thenExecute(()->{h.assertTrue(AaroncosVortex.negative(t)==null&&Math.abs(t.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<1e-6&&!AaroncosVortex.active(s),"natural zone and slow expiry");h.assertTrue(h.getLevel().getBlockState(f.center.offset(5,-1,0)).is(Blocks.STONE)&&h.getLevel().getBlockState(f.center.offset(5,0,0)).isAir(),"no terrain or fire mutation");})
            .thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>=start[0]+799,"last cooldown tick"))
            .thenExecute(()->h.assertTrue(s.rts().skills().cooldownTicks(UnitSkill.AARONCOS_RIGHT_VORTEX)==1&&f.cast(s)==SkillResult.COOLDOWN,"not early at799"))
            .thenIdle(1).thenExecute(()->{h.assertTrue(f.cast(s)==SkillResult.SUCCESS&&f.shadow()==420,"recast exactly800 pays once");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_vortex",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=900)
    public static void sameOwnerOverlapCapsSixActualEnemies(GameTestHelper h){
        F f=new F(h,"overlap");var a=f.hand();var b=f.hand();f.unlock();List<GoldenFoxUnit> targets=new ArrayList<>();for(int i=0;i<7;i++){double angle=i*Math.PI*2/7;targets.add(f.target(5+Math.cos(angle)*2,Math.sin(angle)*2));}
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(a)==SkillResult.SUCCESS&&f.cast(b)==SkillResult.SUCCESS&&f.shadow()==420,"two independently paid zones"))
            .thenIdle(21).thenExecute(()->{h.assertTrue(targets.stream().filter(t->Math.abs(t.getHealth()-995)<.001).count()==6&&targets.stream().filter(t->t.getHealth()==1000).count()==1,"six recipients only, same-owner overlapping zones do not double hit");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_vortex",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=900)
    public static void stopCancelsWindupWithoutRefund(GameTestHelper h){
        F f=new F(h,"stop");var s=f.hand();var t=f.target(5,0);f.unlock();
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(s)==SkillResult.SUCCESS,"paid start"))
            .thenIdle(5).thenExecute(()->new UnitActionItem(f.owner,UnitAction.STOP,-1,new int[]{s.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel()))
            .thenIdle(20).thenExecute(()->{f.hp(t,1000,"STOP before first pulse cancels all damage");h.assertTrue(!AaroncosVortex.active(s)&&f.shadow()==460&&s.rts().skills().cooldownTicks(UnitSkill.AARONCOS_RIGHT_VORTEX)==775,"STOP retains paid cooldown and cost");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_vortex",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=900)
    public static void ownerChangeDuringDamageCannotWriteHealthOrSlow(GameTestHelper h){
        F f=new F(h,"aba");var s=f.hand();var t=f.target(5,0);f.unlock();boolean[] callback={false};
        f.guard(e->{if(AaroncosVortex.isPulse(e.getSource())&&e.getEntity()==t){callback[0]=true;s.setOwnerName(f.enemy);s.setOwnerName(f.owner);}});
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(s)==SkillResult.SUCCESS,"paid before callback"))
            .thenIdle(25).thenExecute(()->{h.assertTrue(callback[0],"real damage callback reached");f.hp(t,1000,"revoked exact hit cannot write health");h.assertTrue(AaroncosVortex.negative(t)==null&&!AaroncosVortex.active(s)&&f.shadow()==460,"no slow or follow-up after ownership ABA");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_vortex",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=900)
    public static void disconnectAndRemovalCancelTransientEffects(GameTestHelper h){
        F f=new F(h,"offline");var s=f.hand();var t=f.target(5,0);f.unlock();
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(s)==SkillResult.SUCCESS,"paid before disconnect"))
            .thenIdle(21).thenExecute(()->{f.hp(t,995,"first actual hit");h.assertTrue(AaroncosVortex.negative(t)!=null,"actual slow installed");f.peer.close();})
            .thenIdle(21).thenExecute(()->{f.hp(t,995,"offline caster no secondpulse");h.assertTrue(AaroncosVortex.negative(t)==null&&!AaroncosVortex.active(s),"disconnect revokes lease and slow");s.discard();h.assertTrue(f.shadow()==460,"no refund on unload");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_vortex",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void actualEntityNbtRetainsCooldownButNotTransientZone(GameTestHelper h){
        F f=new F(h,"reload");var s=f.hand();var t=f.target(5,0);f.unlock();
        net.minecraft.nbt.CompoundTag saved=new net.minecraft.nbt.CompoundTag();long[] deadline={0};AaroncosRightHandUnit[] restored={null};
        h.startSequence().thenIdle(30).thenExecute(()->{h.assertTrue(f.cast(s)==SkillResult.SUCCESS,"paid before save");deadline[0]=h.getLevel().getGameTime()+800;s.saveWithoutId(saved);s.discard();})
            .thenIdle(2).thenExecute(()->{var next=DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel());next.load(saved);h.assertTrue(h.getLevel().addFreshEntity(next),"real original NBT entity republished");restored[0]=next;f.bodies.add(next);h.assertTrue(next.getUUID().equals(s.getUUID())&&!AaroncosVortex.active(next),"same UUID does not restore transient zone");h.assertTrue(next.rts().skills().cooldownTicks(UnitSkill.AARONCOS_RIGHT_VORTEX)==deadline[0]-h.getLevel().getGameTime(),"original absolute paid cooldown survives NBT");})
            .thenIdle(25).thenExecute(()->{f.hp(t,1000,"saved windup never replays");h.assertTrue(f.shadow()==460&&f.cast(restored[0])==SkillResult.COOLDOWN,"no refund or free recast after reload");f.done();});
    }

    @GameTest(templateNamespace="dreamseekers_aaroncos_vortex",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void exactGelAbsorptionReceivesSlowWithoutHealthLoss(GameTestHelper h){
        F f=new F(h,"shield");var s=f.hand();var t=f.target(5,0);var gel=f.add(DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel()),f.enemy,5,3);f.unlock();
        h.startSequence().thenIdle(30).thenExecute(()->{
            try(var peer=new PassiveIncomePeer(h.getLevel(),f.enemy)){
                h.assertTrue(SkillService.request(peer.actor,new SkillRequest(UnitSkill.GEL_GUARD,gel.getId(),gel.getUUID(),gel.rts().skills().revision(),t.getId(),t.getUUID()))==SkillResult.SUCCESS,"real enemy Gel shield");
            }
            h.assertTrue(SkillEffects.shieldRemaining(t)==12&&f.cast(s)==SkillResult.SUCCESS,"paid vortex against12 Gel");
        }).thenIdle(21).thenExecute(()->{f.hp(t,1000,"full Gel absorption no health write");h.assertTrue(SkillEffects.shieldRemaining(t)==7&&AaroncosVortex.negative(t)!=null,"five points of shield absorbed: one genuine slow receipt");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_vortex",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void canceledDamageHasNoSlowButNextRealPulseDoes(GameTestHelper h){
        F f=new F(h,"cancel");var s=f.hand();var t=f.target(5,0);f.unlock();boolean[] canceled={false};
        f.guard(e->{if(AaroncosVortex.isPulse(e.getSource())&&e.getEntity()==t&&!canceled[0]){canceled[0]=true;e.setCanceled(true);}});
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(s)==SkillResult.SUCCESS,"real paid cast"))
            .thenIdle(21).thenExecute(()->{f.hp(t,1000,"canceled actual final damage writes no health");h.assertTrue(canceled[0]&&AaroncosVortex.negative(t)==null,"zero damage not a successful receipt");})
            .thenIdle(20).thenExecute(()->{f.hp(t,995,"second independent pulse health write");h.assertTrue(AaroncosVortex.negative(t)!=null,"next genuine receipt installs slow");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_vortex",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void recursiveSamePulseCannotReportHealthOrSlowTwice(GameTestHelper h){
        F f=new F(h,"recursive");var s=f.hand();var t=f.target(5,0);f.unlock();boolean[] called={false};
        f.guard(e->{if(AaroncosVortex.isPulse(e.getSource())&&e.getEntity()==t&&!called[0]){called[0]=true;h.assertTrue(!t.hurt(e.getSource(),999),"nested use of exact same pulse rejected");}});
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(s)==SkillResult.SUCCESS,"real paid cast"))
            .thenIdle(21).thenExecute(()->{f.hp(t,1000,"revoked recursive outer cannot write health");h.assertTrue(called[0]&&AaroncosVortex.negative(t)==null,"no fabricated receipt from either nested or outer attempt");f.done();});
    }

}
