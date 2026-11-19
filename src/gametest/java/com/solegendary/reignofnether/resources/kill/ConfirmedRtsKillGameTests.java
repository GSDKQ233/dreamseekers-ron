package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.items.UnitInventory;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceTransaction;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** External draft: actual server damage/events/transactions, not yet executed. */
@GameTestHolder("reignofnether_kill_economy")
@PrefixGameTestTemplate(false)
public final class ConfirmedRtsKillGameTests {
    private static final String PREFIX="KillEconomy_",KILLER=PREFIX+"Killer",VICTIM=PREFIX+"Victim",LAST=PREFIX+"Last";
    private static final class Consumer {
        final Set<UUID> eligible=new HashSet<>(); final java.util.List<Entity> entities=new java.util.ArrayList<>(); int committed,duplicateBlocked;
        @SubscribeEvent public void award(ConfirmedRtsKillEvent event) {
            if (!eligible.contains(event.snapshot().victimId())) return;
            boolean paid=event.tryClaim(account -> ResourceTransaction.creditIncome(
                    net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer(),account,account.accountEpoch,account.revision,
                    new ResourceTransaction.Cost(0,0,1)).applied());
            if(paid)committed++;
        }
        @SubscribeEvent(priority=EventPriority.LOW) public void duplicate(ConfirmedRtsKillEvent event) {
            if (!eligible.contains(event.snapshot().victimId())) return;
            if(!event.tryClaim(account -> {throw new AssertionError("second consumer must not execute");}))duplicateBlocked++;
        }
    }
    private static Resources account(String owner){
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,Factions.MONSTERS));
        Resources account=new Resources(owner,0,0,0);ResourcesServerEvents.resourcesList.add(account);return account;
    }
    private static <T extends Mob> T spawn(GameTestHelper h,Consumer consumer,EntityType<T> type,String owner,int x) {
        T entity=type.create(h.getLevel());h.assertTrue(entity instanceof Unit,"genuine RTS entity fixture");
        ((Unit)entity).setOwnerName(owner);entity.setNoAi(true);BlockPos p=h.absolutePos(new BlockPos(x,2,3));
        entity.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.getLevel().setBlock(p.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
        h.assertTrue(h.getLevel().addFreshEntity(entity),"fixture enters real world");consumer.entities.add(entity);return entity;
    }
    private static void cleanup(Consumer consumer){
        MinecraftForge.EVENT_BUS.unregister(consumer);
        // Remove only fixture identities first, so discard cannot trigger real defeat/postgame handling.
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.startsWith(PREFIX));
        consumer.entities.forEach(Entity::discard);
        UnitServerEvents.getAllUnits().stream().filter(e -> e instanceof Unit u&&u.getOwnerName().startsWith(PREFIX)).toList().forEach(Entity::discard);
        HeroServerEvents.fallenHeroes.removeIf(h -> h.ownerName.startsWith(PREFIX));
    }
    private static void at(GameTestHelper h,Consumer consumer,int tick,Runnable action) {
        h.runAtTickTime(tick,() -> {try {action.run();} catch (RuntimeException | Error failure) {cleanup(consumer);throw failure;}});
    }
    @GameTest(templateNamespace="reignofnether_kill_economy",template="ore_mine_test_arena",batch="kill_economy_bridge",timeoutTicks=80)
    public static void realDeathClaimAndDefeatOrdering(GameTestHelper h) {
        Consumer consumer=new Consumer();MinecraftForge.EVENT_BUS.register(consumer);
        Resources account=account(KILLER);account(VICTIM);account(LAST);
        var attacker=spawn(h,consumer,EntityRegistrar.ZOMBIE_UNIT.get(),KILLER,2);
        var first=spawn(h,consumer,EntityRegistrar.VILLAGER_UNIT.get(),VICTIM,4);consumer.eligible.add(first.getUUID());
        first.setHealth(1);h.assertTrue(first.hurt(h.getLevel().damageSources().mobAttack(attacker),1000),"real fatal damage accepted");
        MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(first,h.getLevel().damageSources().mobAttack(attacker)));
        at(h,consumer,4,() -> {
            h.assertTrue(account.ore==1&&consumer.committed==1&&consumer.duplicateBlocked==1,"one real death one consuming listener, duplicate notification blocked");
            h.assertTrue(RtsKillValueClaims.isClaimed(first),"successful claim persisted on entity");
            var last=spawn(h,consumer,EntityRegistrar.VILLAGER_UNIT.get(),LAST,6);consumer.eligible.add(last.getUUID());
            last.setHealth(1);last.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);
            // Reproduce the supported defeat ordering exactly: dead entity loses owner and its RTS identity is removed.
            ((Unit)last).setOwnerName("");PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.equals(LAST));
            ResourcesServerEvents.removeAccount(LAST);
        });
        at(h,consumer,8,() -> {
            h.assertTrue(account.ore==2&&consumer.committed==2,"valid final enemy kill survives normal defeat ownership clearing");
            var cleared=spawn(h,consumer,EntityRegistrar.VILLAGER_UNIT.get(),VICTIM,8);consumer.eligible.add(cleared.getUUID());
            cleared.setHealth(1);cleared.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);((Unit)cleared).setOwnerName("");
        });
        at(h,consumer,12,() -> {
            h.assertTrue(account.ore==2,"owner cleared while original victim RTS remains must reject");
            var totem=spawn(h,consumer,EntityRegistrar.ROYAL_GUARD_UNIT.get(),VICTIM,10);consumer.eligible.add(totem.getUUID());
            ((UnitInventory)totem).set(0,new ItemStack(Items.TOTEM_OF_UNDYING));totem.setHealth(1);
            totem.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);
            h.assertTrue(totem.isAlive(),"native HeroServerEvents totem cancelled lethal death");
            h.assertTrue(!RtsKillValueClaims.isClaimed(totem),"totem cancellation does not consume economic value");
        });
        at(h,consumer,16,() -> {
            h.assertTrue(account.ore==2,"native totem cancellation paid no reward");
            var stale=spawn(h,consumer,EntityRegistrar.VILLAGER_UNIT.get(),VICTIM,12);consumer.eligible.add(stale.getUUID());
            stale.setHealth(1);stale.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);account.resetExtendedAccount();
        });
        at(h,consumer,20,() -> {
            h.assertTrue(account.ore==2,"new killer account epoch blocks pending old-game reward");
            var staleVictim=spawn(h,consumer,EntityRegistrar.VILLAGER_UNIT.get(),VICTIM,14);consumer.eligible.add(staleVictim.getUUID());
            staleVictim.setHealth(1);staleVictim.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);
            ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(VICTIM)).findFirst().orElseThrow().resetExtendedAccount();
        });
        at(h,consumer,24,() -> {
            h.assertTrue(account.ore==2,"victim account epoch reset rejects pending death");
            var aba=spawn(h,consumer,EntityRegistrar.VILLAGER_UNIT.get(),VICTIM,16);consumer.eligible.add(aba.getUUID());
            aba.setHealth(1);aba.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);
            ((Unit)aba).setOwnerName(PREFIX+"Temporary");((Unit)aba).setOwnerName(VICTIM);
        });
        at(h,consumer,28,() -> {
            h.assertTrue(account.ore==2,"same-tick supported synced victim owner ABA rejects");
            var aba=spawn(h,consumer,EntityRegistrar.VILLAGER_UNIT.get(),VICTIM,18);consumer.eligible.add(aba.getUUID());
            aba.setHealth(1);aba.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);
            ((Unit)attacker).setOwnerName(PREFIX+"Temporary");((Unit)attacker).setOwnerName(KILLER);
        });
        at(h,consumer,32,() -> {
            h.assertTrue(account.ore==2,"same-tick supported synced killer owner ABA rejects");
            var replaced=spawn(h,consumer,EntityRegistrar.VILLAGER_UNIT.get(),VICTIM,20);consumer.eligible.add(replaced.getUUID());
            replaced.setHealth(1);replaced.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(VICTIM));ResourcesServerEvents.removeAccount(VICTIM);account(VICTIM);
        });
        at(h,consumer,36,() -> {
            h.assertTrue(account.ore==2,"same-name replacement victim RTS/account is not final-defeat exemption");cleanup(consumer);h.succeed();
        });
    }
}
