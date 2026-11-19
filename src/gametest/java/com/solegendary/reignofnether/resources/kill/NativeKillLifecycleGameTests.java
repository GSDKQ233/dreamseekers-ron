package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Physical collision tests with explicit staged flight, not a natural ballistic timing test. */
@GameTestHolder("reignofnether_native_kill_lifecycle")
@PrefixGameTestTemplate(false)
public final class NativeKillLifecycleGameTests {
    static final String PREFIX="NativeKillA_";
    enum Mode { NORMAL_DEATH, LIVE_DISCARD, EXPLICIT_UNLOAD_REASON, CARRIER_ABA, ACCOUNT_RESET, ACCOUNT_REPLACED, NULL_OWNER_NOOP }
    static final class Case {
        Mode mode; String owner; Resources account; Mob shooter,target,alternate; Arrow arrow;
        int rewards; boolean scope,nativeNullSource; long impactRevision;
    }
    static final class Fixture {
        final GameTestHelper h; final List<Entity> entities=new ArrayList<>(); final List<Case> cases=new ArrayList<>();
        Fixture(GameTestHelper h){this.h=h;}
        Resources account(String name){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,Factions.VILLAGERS));Resources r=new Resources(name,100,100,100);ResourcesServerEvents.resourcesList.add(r);return r;}
        <T extends Mob>T spawn(EntityType<T> type,String owner,int x,int z){T e=type.create(h.getLevel());((Unit)e).setOwnerName(owner);e.setNoAi(true);BlockPos p=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(e),"unit genuinely enters level");entities.add(e);return e;}
        @SubscribeEvent public void witness(LivingDeathEvent e){for(Case c:cases)if(e.getEntity()==c.target){c.scope=RtsProjectileProvenance.hasExactPrimaryHit(c.arrow,c.target,e.getSource());c.nativeNullSource=c.arrow.getOwner()==null&&(e.getSource().getEntity()==c.arrow||e.getSource().getEntity()==null);}}
        @SubscribeEvent public void credit(ConfirmedRtsKillEvent e){for(Case c:cases)if(e.snapshot().victimId().equals(c.target.getUUID())){h.assertTrue(e.snapshot().killerOwner().equals(c.owner),"origin pays original owner only");if(e.tryClaim(a->ResourceTransaction.creditIncome(h.getLevel().getServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(0,0,1)).applied()))c.rewards++;}}
        void cleanup(){MinecraftForge.EVENT_BUS.unregister(this);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));entities.forEach(Entity::discard);}
        void at(int tick,Runnable action){h.runAtTickTime(tick,()->{try{action.run();}catch(RuntimeException|Error e){cleanup();throw e;}});}
    }
    @GameTest(templateNamespace="reignofnether_native_kill_lifecycle",template="ore_mine_test_arena",batch="native_lifecycle",timeoutTicks=120)
    public static void launchedArrowTerminalSourceAndInvalidOrigins(GameTestHelper h){
        Fixture f=new Fixture(h);MinecraftForge.EVENT_BUS.register(f);
        for(Mode mode:Mode.values()){
            Case c=new Case();c.mode=mode;c.owner=PREFIX+mode;c.account=f.account(c.owner);String victimOwner=c.owner+"_Victim",alternateOwner=c.owner+"_Other";f.account(victimOwner);f.account(alternateOwner);
            int z=5+mode.ordinal()*8;
            c.shooter=f.spawn(EntityRegistrar.SKELETON_UNIT.get(),c.owner,3,z);c.target=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),victimOwner,20,z);c.alternate=f.spawn(EntityRegistrar.SKELETON_UNIT.get(),alternateOwner,30,z);
            f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),c.owner,3,z+2);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),victimOwner,20,z+2); // Keep genuine RTS accounts alive after deaths.
            ResourceCost quote=((Unit)c.target).getCost();h.assertTrue((long)quote.food+quote.wood+quote.ore>0,"real victim has nonzero native cost before launch");h.assertTrue(((AttackerUnit)c.shooter).getUnitAttackDamage()>=1,"native projectile damage is at least one; baseDamage is not assumed authoritative");c.target.setHealth(1);
            c.arrow=new Arrow(h.getLevel(),c.shooter);c.arrow.setNoGravity(true);c.arrow.setPos(c.shooter.getX(),c.shooter.getY()+8,c.shooter.getZ());c.arrow.shoot(1,0,0,.01f,0);c.arrow.setBaseDamage(4);h.assertTrue(h.getLevel().addFreshEntity(c.arrow),"real fired arrow captures launch origin");f.entities.add(c.arrow);f.cases.add(c);
        }
        f.at(2,()->{for(Case c:f.cases)switch(c.mode){
            case NORMAL_DEATH -> {c.shooter.setHealth(1);c.shooter.hurt(h.getLevel().damageSources().genericKill(),1000);h.assertTrue(c.shooter.isDeadOrDying(),"real environmental source death, not discard");}
            case LIVE_DISCARD -> c.shooter.discard();
            case EXPLICIT_UNLOAD_REASON -> c.shooter.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            case CARRIER_ABA -> {c.arrow.setOwner(c.alternate);c.arrow.setOwner(c.shooter);}
            case ACCOUNT_RESET -> c.account.resetExtendedAccount();
            case ACCOUNT_REPLACED -> {PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(c.owner));ResourcesServerEvents.resourcesList.remove(c.account);c.account=f.account(c.owner);}
            case NULL_OWNER_NOOP -> {c.arrow.setOwner(null);h.assertTrue(c.arrow.getOwner()==c.shooter,"vanilla null setter is a no-op, not an economic transfer");}
        }});
        f.at(30,()->{for(Case c:f.cases){
            if(c.mode==Mode.NORMAL_DEATH)h.assertTrue(c.shooter.isRemoved()&&c.shooter.getRemovalReason()==Entity.RemovalReason.KILLED&&c.arrow.getOwner()==null,"ordinary death animation removed original source as KILLED and native owner resolution is null");
            h.assertTrue(!c.arrow.isRemoved()&&c.target.isAlive(),"real arrow survived staged flight and target awaits impact");c.impactRevision=c.account.revision;
            c.arrow.setPos(c.target.getX()-.9,c.target.getY()+.7,c.target.getZ());c.arrow.setDeltaMovement(Vec3.ZERO);c.arrow.shoot(1,0,0,3,0); // Deterministic final flight; collision remains vanilla entity tick.
        }});
        f.at(38,()->{for(Case c:f.cases){boolean accept=c.mode==Mode.NORMAL_DEATH||c.mode==Mode.NULL_OWNER_NOOP;
            h.assertTrue(c.target.isDeadOrDying()&&c.target.getLastDamageSource()!=null&&c.target.getLastDamageSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE),"actual projectile killed one-HP native target: "+c.mode);
            h.assertTrue(c.rewards==(accept?1:0)&&c.account.ore==(accept?101:100)&&c.account.revision==c.impactRevision+(accept?1:0),"one legitimate launch payment or zero invalid-origin mutation: "+c.mode);
            h.assertTrue(c.scope==accept,"exact physical-hit scope eligibility agrees with origin: "+c.mode);
            if(c.mode==Mode.NORMAL_DEATH)h.assertTrue(c.nativeNullSource,"native null-owner DamageSource preserved without rewriting shooter");
        }System.out.println("NATIVE_KILL_LIFECYCLE_RUNTIME_PASS normal_killed null_owner discard explicit_unload carrier_aba reset replacement");f.cleanup();h.succeed();});
    }
}
