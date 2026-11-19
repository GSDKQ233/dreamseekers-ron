package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.*;
import com.solegendary.reignofnether.sandbox.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real authenticated packet, native entity publication/death and authoritative ledger tests. */
@GameTestHolder("reignofnether_kill_economy")
@PrefixGameTestTemplate(false)
public final class SandboxEconomyPreviewGameTests {
    private static final List<Fixture> OPEN=new ArrayList<>();
    @AfterBatch(batch="sandbox_economy_preview") public static void after(net.minecraft.server.level.ServerLevel level) {
        for(Fixture f:List.copyOf(OPEN))f.close();
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final String owner;final Resources account;final RTSPlayer player;
        final SandboxEditorTestGrant editorGrant; final ResearchEconomyFixtures.Peer peer;final List<Entity> entities=new ArrayList<>();
        final List<ConfirmedRtsKillEvent.Snapshot> snapshots=new ArrayList<>();
        final Set<UUID> canceled=new HashSet<>();boolean closed;int cursor=2;
        Fixture(GameTestHelper h,String suffix)throws Exception {
            this.h=h;owner="SandboxEconomy_"+suffix;
            player=RTSPlayer.getNewBot(owner,Factions.NONE);PlayerServerEvents.rtsPlayers.add(player);
            account=new Resources(owner,0,0,0);ResourcesServerEvents.resourcesList.add(account);
            peer=new ResearchEconomyFixtures.Peer(h.getLevel(),owner);editorGrant=new SandboxEditorTestGrant(peer.actor,player);OPEN.add(this);MinecraftForge.EVENT_BUS.register(this);
        }
        @SubscribeEvent public void record(ConfirmedRtsKillEvent e){if(e.snapshot().killerOwner().equals(owner))snapshots.add(e.snapshot());}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void cancel(LivingDeathEvent e){if(canceled.contains(e.getEntity().getUUID()))e.setCanceled(true);}
        BlockPos pos(){BlockPos p=h.absolutePos(new BlockPos(cursor++,2,5));h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);return p;}
        <T extends Mob>T unit(EntityType<T> type,String owner){T e=type.create(h.getLevel());((Unit)e).setOwnerName(owner);e.setNoAi(true);BlockPos p=pos();e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(e),"real unit publication");entities.add(e);return e;}
        Mob target(){return target(EntityRegistrar.VILLAGER_UNIT.get());}
        Mob target(EntityType<? extends Mob> type){
            Set<Entity> before=new HashSet<>(UnitServerEvents.getAllUnits());BlockPos p=pos();
            var packet=new SandboxServerboundPacket(SandboxAction.SPAWN_UNIT,"Enemy",EntityType.getKey(type).toString(),p.below(),new int[0]);
            FriendlyByteBuf bytes=new FriendlyByteBuf(Unpooled.buffer());
            try{packet.encode(bytes);var decoded=new SandboxServerboundPacket(bytes);h.assertTrue(bytes.readableBytes()==0,"actual sandbox packet round trip");decoded.handle(()->peer.context);}
            finally{bytes.release();}
            peer.channel.runPendingTasks();
            var created=UnitServerEvents.getAllUnits().stream().filter(e->!before.contains(e)).toList();
            h.assertTrue(created.size()==1,"authenticated request creates exactly one unit");
            Mob e=(Mob)created.get(0);entities.add(e);e.setNoAi(true);
            h.assertTrue(((Unit)e).getOwnerName().equals("Enemy")&&((Unit)e).getAnchor().equals(p.below()),"native Enemy owner and anchor preserved");
            h.assertTrue(e.getX()==p.getX()+.5&&e.getY()==p.getY()&&e.getZ()==p.getZ()+.5,"native spawn position preserved");
            return e;
        }
        void research(){ResearchServerEvents.addResearch(owner,CoreEconomyResearch.id("research_global_salvage"));}
        void kill(Mob victim,Mob killer){
            ResourceCost cost=((Unit)victim).getCost();int food=cost.food,wood=cost.wood,ore=cost.ore;
            try{cost.food=20;cost.wood=40;cost.ore=60;victim.setHealth(1);victim.hurt(h.getLevel().damageSources().mobAttack(killer),1000);}
            finally{cost.food=food;cost.wood=wood;cost.ore=ore;}
        }
        void balances(int f,int w,int o,String label){h.assertTrue(account.food==f&&account.wood==w&&account.ore==o,label+" actual="+account.food+","+account.wood+","+account.ore);}
        void at(int tick,Runnable action){h.runAtTickTime(tick,()->{try{action.run();}catch(RuntimeException|Error e){close();throw e;}});}
        public void close(){if(closed)return;closed=true;editorGrant.close();MinecraftForge.EVENT_BUS.unregister(this);ResearchServerEvents.removeAllResearchFor(owner);PlayerServerEvents.rtsPlayers.removeIf(p->p==player);ResourcesServerEvents.resourcesList.removeIf(r->r==account);peer.close();entities.forEach(Entity::discard);OPEN.remove(this);}
    }
    @GameTest(templateNamespace="reignofnether_kill_economy",template="ore_mine_test_arena",batch="sandbox_economy_preview",timeoutTicks=100)
    public static void authenticatedEnemyDirectAndNativeArrowUseOneRewardFamily(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h,"Packet");
        Mob attacker=f.unit(EntityRegistrar.ZOMBIE_UNIT.get(),f.owner);
        Mob unpaid=f.target();h.assertTrue(SandboxEconomyFixture.has(unpaid),"native authenticated creation marks fixture before publication");f.kill(unpaid,attacker);
        f.at(4,()->{
            f.balances(0,0,0,"no research means no Monster salvage");h.assertTrue(!RtsKillValueClaims.isClaimed(unpaid),"unresearched fixture unclaimed");f.research();
            Mob paid=f.target();f.kill(paid,attacker);
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(paid,h.getLevel().damageSources().mobAttack(attacker)));
        });
        final Mob[] arrowTarget={null};final int[] expected=new int[3];
        f.at(9,()->{
            f.balances(2,4,6,"researched true fatal attack pays only10percentFWO once");
            h.assertTrue(f.snapshots.stream().allMatch(s->s.killerFaction().equals(Factions.NONE.key)&&s.rewardFaction().equals(Factions.MONSTERS.key)),"owner NONE retained, immutable Monster reward family selected");
            h.assertTrue(PlayerServerEvents.getRTSPlayer("Enemy")==null&&ResourcesServerEvents.resourcesList.stream().noneMatch(r->r.ownerName.equals("Enemy")),"no synthetic Enemy RTS/resource account");
            Mob ranged=f.unit(EntityRegistrar.SKELETON_UNIT.get(),f.owner);Mob victim=f.target();arrowTarget[0]=victim;victim.setHealth(1);
            var cost=((Unit)victim).getCost();expected[0]=2+cost.food/10;expected[1]=4+cost.wood/10;expected[2]=6+cost.ore/10;
            Arrow arrow=new Arrow(h.getLevel(),ranged);arrow.setBaseDamage(1000);arrow.setPos(victim.getX()-.9,victim.getY()+.7,victim.getZ());arrow.shoot(1,0,0,3,0);f.entities.add(arrow);h.assertTrue(h.getLevel().addFreshEntity(arrow),"real shooter-owned native arrow enters world");
        });
        f.at(20,()->{
            h.assertTrue(arrowTarget[0].isDeadOrDying()&&RtsKillValueClaims.isClaimed(arrowTarget[0]),"physical arrow impact confirmed and claimed");f.balances(expected[0],expected[1],expected[2],"native projectile uses same trusted reward bridge");
            Mob other=f.unit(EntityRegistrar.VILLAGER_UNIT.get(),f.owner);f.kill(f.target(),other);
        });
        f.at(25,()->{f.balances(expected[0],expected[1],expected[2],"NONE villager lethal unit receives neither Monster nor Dream reward");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="reignofnether_kill_economy",template="ore_mine_test_arena",batch="sandbox_economy_preview",timeoutTicks=100)
    public static void fixtureProvenanceSaveLoadCaptureAndMalformedMarkersFailClosed(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h,"Provenance");f.research();Mob attacker=f.unit(EntityRegistrar.ZOMBIE_UNIT.get(),f.owner);
        Mob raw=f.unit(EntityRegistrar.VILLAGER_UNIT.get(),"Enemy");f.kill(raw,attacker); // also the Survival/command owner, without explicit provenance
        Mob free=f.target();RtsKillValueClaims.markFree(free,RtsKillValueClaims.FreeOrigin.NATIVE_SUMMON);f.kill(free,attacker);
        Mob unknown=f.target();unknown.getPersistentData().getCompound(RtsKillValueClaims.KEY).getCompound(SandboxEconomyFixture.KEY).putInt("schema",99);f.kill(unknown,attacker);
        Mob malformed=f.target();malformed.getPersistentData().getCompound(RtsKillValueClaims.KEY).putString(SandboxEconomyFixture.KEY,"invalid");f.kill(malformed,attacker);
        Mob copySource=f.target();Mob copied=f.unit(EntityRegistrar.VILLAGER_UNIT.get(),"Enemy");RtsKillValueClaims.restoreValue(copied,RtsKillValueClaims.copyValue(copySource));f.kill(copied,attacker);
        Mob aba=f.target();((Unit)aba).setOwnerName(f.owner);((Unit)aba).setOwnerName("Enemy");
        h.assertTrue(aba.getPersistentData().getCompound(RtsKillValueClaims.KEY).getCompound(SandboxEconomyFixture.KEY).getBoolean("invalid"),"central owner mutation persistently invalidates ABA before save");
        CompoundTag saved=new CompoundTag();aba.saveWithoutId(saved);aba.discard();
        Mob loaded=EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());loaded.load(saved);loaded.setNoAi(true);f.entities.add(loaded);h.assertTrue(h.getLevel().addFreshEntity(loaded),"saved invalid fixture re-enters world");f.kill(loaded,attacker);
        // Legitimate unmodified NBT load initializes owner without being falsely treated as capture.
        Mob original=f.target();CompoundTag good=new CompoundTag();original.saveWithoutId(good);original.discard();
        Mob restored=EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());restored.load(good);restored.setNoAi(true);f.entities.add(restored);h.assertTrue(h.getLevel().addFreshEntity(restored),"valid original UUID fixture reloads");
        h.assertTrue(SandboxEconomyFixture.capture(restored,f.owner,f.account.accountEpoch)!=null,"ordinary NBT owner initialization is not capture");f.kill(restored,attacker);
        f.at(6,()->{f.balances(2,4,6,"only unmodified original-UUID reload pays; free/unmarked/unknown/malformed/copied/ABA all deny");h.assertTrue(!RtsKillValueClaims.isClaimed(loaded),"invalid capture is not consumed");
            // Even a real RTS account later named Enemy must not wash marked fixtures through ordinary branch.
            RTSPlayer enemy=RTSPlayer.getNewBot("Enemy",Factions.VILLAGERS);Resources account=new Resources("Enemy",0,0,0);PlayerServerEvents.rtsPlayers.add(enemy);ResourcesServerEvents.resourcesList.add(account);
            try{Mob bad=f.target();bad.getPersistentData().getCompound(RtsKillValueClaims.KEY).getCompound(SandboxEconomyFixture.KEY).putBoolean("invalid",true);f.kill(bad,attacker);}
            finally{PlayerServerEvents.rtsPlayers.remove(enemy);ResourcesServerEvents.resourcesList.remove(account);}
        });
        f.at(12,()->{f.balances(2,4,6,"marked invalid fixture never falls back to genuine Enemy account");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="reignofnether_kill_economy",template="ore_mine_test_arena",batch="sandbox_economy_preview",timeoutTicks=100)
    public static void fixtureIssuerAuthorityAndDelayedDeathChangesReject(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h,"Authority");f.research();Mob attacker=f.unit(EntityRegistrar.ZOMBIE_UNIT.get(),f.owner);
        Mob canceled=f.target();f.canceled.add(canceled.getUUID());f.kill(canceled,attacker);
        f.at(6,()->{
            f.balances(0,0,0,"canceled death alone cannot pay; no unrelated epoch or faction change");
            h.assertTrue(!RtsKillValueClaims.isClaimed(canceled),"cancellation preserves unclaimed value");
            Mob aba=f.target();f.kill(aba,attacker);((Unit)aba).setOwnerName("temporary");((Unit)aba).setOwnerName("Enemy");
        });
        f.at(12,()->{
            f.balances(0,0,0,"pending victim ABA alone rejects before any account reset");
            Mob wrongIssuer=f.target();wrongIssuer.getPersistentData().getCompound(RtsKillValueClaims.KEY).getCompound(SandboxEconomyFixture.KEY).putUUID("issuerId",UUID.randomUUID());f.kill(wrongIssuer,attacker);
        });
        f.at(18,()->{
            f.balances(0,0,0,"forged issuer UUID alone rejects while genuine issuer and epoch remain valid");
            Mob reset=f.target();f.kill(reset,attacker);f.account.resetExtendedAccount();
        });
        f.at(24,()->{
            f.balances(0,0,0,"pending account epoch reset alone rejects");
            f.player.faction=Factions.MONSTERS;
            RTSPlayer otherSandbox=RTSPlayer.getNewBot("OtherSandboxPreview",Factions.NONE);PlayerServerEvents.rtsPlayers.add(otherSandbox);
            try{h.assertTrue(SandboxEditorAccess.resolve(f.peer.actor,f.peer.connection)==null,"another sandbox seat cannot authorize a non-NONE actor; do not issue a spawn");}
            finally{PlayerServerEvents.rtsPlayers.remove(otherSandbox);}
            f.player.faction=Factions.NONE;Mob switchFaction=f.target();f.kill(switchFaction,attacker);f.player.faction=Factions.MONSTERS;
        });
        f.at(30,()->{
            f.balances(0,0,0,"issuer switching to normal mode before confirmation denies fixture");f.player.faction=Factions.NONE;
            Mob pending=f.target();f.kill(pending,attacker);f.peer.close();
        });
        f.at(36,()->{f.balances(0,0,0,"issuer must remain actual online UUID at confirmation");f.close();h.succeed();});
    }
}
