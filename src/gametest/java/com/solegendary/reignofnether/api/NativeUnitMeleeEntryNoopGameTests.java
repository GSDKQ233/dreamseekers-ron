package com.solegendary.reignofnether.api;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Core-only actual native entry tests. No entry observer is ever installed or called by tests. */
@GameTestHolder("reignofnether_native_entry_noop")
@PrefixGameTestTemplate(false)
public final class NativeUnitMeleeEntryNoopGameTests {
    private static final String BATCH="native_entry_noop", PREFIX="NativeEntryNoop_";
    private static final List<Fixture> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(net.minecraft.server.level.ServerLevel level) {
        for(var f:FIXTURES){MinecraftForge.EVENT_BUS.unregister(f);f.entities.forEach(Entity::discard);}
        FIXTURES.clear();PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
    }
    @GameTest(templateNamespace="reignofnether_native_entry_noop",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void beeInstalledGoalKeepsNativeStingWithoutAddonObserver(GameTestHelper h) {
        Fixture f=new Fixture(h,"Bee",true);
        f.ready().thenExecute(()->{f.goalAttack();f.positive(true);f.emptyObservers();
            System.out.println("NATIVE_ENTRY_NOOP_PASS bee exactSTING=true actualHP="+(1000-f.target.getHealth())+" observers=0");}).thenSucceed();
    }
    @GameTest(templateNamespace="reignofnether_native_entry_noop",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void golemInstalledGoalKeepsNativeMobAttackWithoutAddonObserver(GameTestHelper h) {
        Fixture f=new Fixture(h,"Golem",false);
        f.ready().thenExecute(()->{f.goalAttack();f.positive(false);f.emptyObservers();
            System.out.println("NATIVE_ENTRY_NOOP_PASS golem exactMOB_ATTACK=true actualHP="+(1000-f.target.getHealth())+" observers=0");}).thenSucceed();
    }
    @GameTest(templateNamespace="reignofnether_native_entry_noop",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void golemGuaranteedEvasionStillCancelsAndPlaysNativeSoundThenCanHit(GameTestHelper h) {
        Fixture f=new Fixture(h,"Evasion",false);
        f.ready().thenExecute(()->{
            f.target.getAttribute(AttributeRegistrar.EVASION_CHANCE.get()).setBaseValue(1);
            f.goalAttack();
            h.assertTrue(f.attacks==0&&f.target.getHealth()==1000&&f.golemSounds==1,"real installed goal reaches original evasion HEAD: no hurt and one native attack sound");
            h.assertTrue(!f.source.doHurtTarget(f.target)&&f.attacks==0&&f.target.getHealth()==1000&&f.golemSounds==2,"original native evasion boolean remains false, no suppressed native sound");
            f.target.getAttribute(AttributeRegistrar.EVASION_CHANCE.get()).setBaseValue(0);
        }).thenWaitUntil(f::attackReady).thenExecute(()->{
            f.goalAttack();f.positive(false);f.emptyObservers();
            h.assertTrue(f.golemSounds==3,"later unevaded real goal preserves original attack sound too");
            System.out.println("NATIVE_ENTRY_NOOP_PASS evasion canceledHP=0 laterHP="+(1000-f.target.getHealth())+" observers=0 sounds="+f.golemSounds);
        }).thenSucceed();
    }
    private static final class Fixture {
        final GameTestHelper h;final Mob source,target;final List<Entity> entities=new ArrayList<>();
        int attacks,golemSounds;DamageSource damage;
        Fixture(GameTestHelper h,String key,boolean bee) {
            this.h=h;FIXTURES.add(this);emptyObservers();
            var origin=h.absolutePos(BlockPos.ZERO);
            for(int x=0;x<24;x++)for(int z=0;z<24;z++){
                h.getLevel().setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=1;y<6;y++)h.getLevel().setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            String owner=PREFIX+key,enemy=owner+"_Enemy";account(owner);account(enemy);
            source=bee?EntityRegistrar.BEE_UNIT.get().create(h.getLevel()):EntityRegistrar.IRON_GOLEM_UNIT.get().create(h.getLevel());
            target=EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());
            spawn(source,owner,8,8);spawn(target,enemy,bee?9.1:10,8);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
            source.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);
            source.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
            source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);
            source.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).setBaseValue(1);
            ((AttackerUnit)source).setUnitAttackTargetForced(target);MinecraftForge.EVENT_BUS.register(this);
        }
        void emptyObservers() {
            h.assertTrue(!ModList.get().isLoaded("dreamseekers"),"actual core-only runtime, not addon hidden by namespace");
            try {var field=NativeUnitMeleeEntryHooks.class.getDeclaredField("OBSERVERS");field.setAccessible(true);
                h.assertTrue(((List<?>)field.get(null)).isEmpty(),"real optional entry hook has no installed observers; never cleared or replaced by fixture");
            }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
        }
        void account(String name){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,Factions.VILLAGERS));ResourcesServerEvents.resourcesList.add(new Resources(name,100,100,100));}
        void spawn(Mob mob,String owner,double x,double z) {
            ((Unit)mob).setOwnerName(owner);((Unit)mob).setHoldPosition(true);mob.setNoAi(true);mob.setNoGravity(true);mob.setPersistenceRequired();
            for(var attr:List.of(Attributes.ARMOR,Attributes.ARMOR_TOUGHNESS,AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get(),AttributeRegistrar.EVASION_CHANCE.get(),AttributeRegistrar.AGGRO_RANGE.get()))if(mob.getAttribute(attr)!=null)mob.getAttribute(attr).setBaseValue(0);
            mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);mob.setAbsorptionAmount(0);
            var p=h.absolutePos(BlockPos.ZERO);mob.moveTo(p.getX()+x,p.getY()+1,p.getZ()+z,0,0);
            h.assertTrue(h.getLevel().addFreshEntity(mob),"real registered core unit published");entities.add(mob);
        }
        GameTestSequence ready(){return h.startSequence().thenWaitUntil(()->{
            for(var entity:entities)h.assertTrue(entity.tickCount>3&&h.getLevel().getEntity(entity.getId())==entity&&h.getLevel().getEntity(entity.getUUID())==entity,"real level publication and native ticks");attackReady();
        });}
        void attackReady(){h.assertTrue(source.getTarget()==target&&((Unit)source).getTargetGoal().getTarget()==target&&source.isNoAi()&&target.invulnerableTime==0&&cooldown()<=0,"installed native goal selected target, natural cooldown and victim iframe ready");}
        int cooldown(){try {var field=AbstractMeleeAttackUnitGoal.class.getDeclaredField("ticksUntilNextAttack");field.setAccessible(true);return field.getInt(((AttackerUnit)source).getAttackGoal());}catch(ReflectiveOperationException error){throw new RuntimeException(error);}}
        void goalAttack(){attackReady();var unit=(AttackerUnit)source;float width=source.getBbWidth()+unit.getBonusMeleeRange(),targetWidth=target.getBbWidth()+((Unit)target).getBonusMeleeRangeForAttackers();
            h.assertTrue(unit.getAttackGoal() instanceof AbstractMeleeAttackUnitGoal&&source.distanceToSqr(target)<=width*2*width*2+targetWidth&&source.hasLineOfSight(target)&&!source.getBoundingBox().intersects(target.getBoundingBox()),"actual installed goal, physical native reach and LOS; no fabricated distance");
            unit.getAttackGoal().tick();h.assertTrue(cooldown()>0,"original installed goal consumed one actual attack cycle");
        }
        void positive(boolean bee){h.assertTrue(attacks==1&&damage!=null&&damage.getEntity()==source&&damage.getDirectEntity()==source&&damage.is(bee?DamageTypes.STING:DamageTypes.MOB_ATTACK),"one real exact native DamageSource, no direct hand hurt");h.assertTrue(Math.abs(target.getHealth()-990)<.0001,"original deterministic native positive HP10 unchanged with empty entry hooks");}
        @SubscribeEvent public void attack(LivingAttackEvent e){if(e.getEntity()==target&&e.getSource().getEntity()==source){attacks++;damage=e.getSource();}}
        @SubscribeEvent public void sound(PlayLevelSoundEvent e){if(e.getLevel()!=source.level()||e.getSound()==null||e.getSound().value()!=SoundEvents.IRON_GOLEM_ATTACK)return;
            if(e instanceof PlayLevelSoundEvent.AtEntity entitySound&&entitySound.getEntity()==source||e instanceof PlayLevelSoundEvent.AtPosition positionSound&&positionSound.getPosition().distanceToSqr(source.position())<.000001)golemSounds++;
        }
    }
}
