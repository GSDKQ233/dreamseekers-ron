package com.dreamseekers;

import com.solegendary.reignofnether.research.economy.ResearchEconomyFixtures;
import com.solegendary.reignofnether.research.economy.CoreEconomyResearch;
import com.solegendary.reignofnether.research.economy.MonsterSalvageIncome;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.economy.*;
import com.solegendary.reignofnether.faction.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.*;
import com.solegendary.reignofnether.sandbox.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("dreamseekers_research_economy")
@PrefixGameTestTemplate(false)
public final class SandboxRewardFamilyGameTests {
    private static final List<Fixture> OPEN=new ArrayList<>();
    @AfterBatch(batch="sandbox_reward_family") public static void after(net.minecraft.server.level.ServerLevel level){for(Fixture f:List.copyOf(OPEN))f.close();}
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final String prefix;final List<String> owners=new ArrayList<>();final List<Entity> entities=new ArrayList<>();
        com.solegendary.reignofnether.sandbox.SandboxEditorTestGrant editorGrant;
        final List<ResearchEconomyFixtures.Peer> peers=new ArrayList<>();boolean reverse,closed;int cursor=2;
        Fixture(GameTestHelper h,String suffix){this.h=h;prefix="RewardFamily_"+suffix;OPEN.add(this);MinecraftForge.EVENT_BUS.register(this);}
        Resources account(String suffix,Faction faction){String owner=prefix+suffix;owners.add(owner);PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,faction));Resources r=new Resources(owner,0,0,0);ResourcesServerEvents.resourcesList.add(r);return r;}
        BlockPos pos(){var p=h.absolutePos(new BlockPos(cursor++,2,4));h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);return p;}
        <T extends Mob>T spawn(EntityType<T> type,String owner){T e=type.create(h.getLevel());((Unit)e).setOwnerName(owner);e.setNoAi(true);var p=pos();e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(e),"real RTS registered body");entities.add(e);return e;}
        Mob preview(ResearchEconomyFixtures.Peer peer){
            Set<Entity> before=new HashSet<>(UnitServerEvents.getAllUnits());var p=pos();
            peer.send(new SandboxServerboundPacket(SandboxAction.SPAWN_UNIT,"Enemy",EntityType.getKey(EntityRegistrar.VILLAGER_UNIT.get()).toString(),p.below(),new int[0]));
            var added=UnitServerEvents.getAllUnits().stream().filter(e->!before.contains(e)).toList();h.assertTrue(added.size()==1,"one native sandbox Enemy request");Mob e=(Mob)added.get(0);e.setNoAi(true);entities.add(e);h.assertTrue(SandboxEconomyFixture.has(e),"trusted fixture evidence exists");return e;
        }
        void kill(Mob victim,Mob killer){var c=((Unit)victim).getCost();int f=c.food,w=c.wood,o=c.ore;try{c.food=20;c.wood=40;c.ore=60;victim.setHealth(1);victim.hurt(h.getLevel().damageSources().mobAttack(killer),1000);}finally{c.food=f;c.wood=w;c.ore=o;}}
        /** Invoke actual consumers in both orders before their normal subscribers. The usual callbacks
         * still run too, proving neither order nor duplicate subscription can change the one claim. */
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void order(ConfirmedRtsKillEvent e){if(!owners.contains(e.snapshot().killerOwner()))return;if(reverse){DreamShadowKillIncome.onConfirmedKill(e);MonsterSalvageIncome.kill(e);}else{MonsterSalvageIncome.kill(e);DreamShadowKillIncome.onConfirmedKill(e);}}
        void at(int tick,Runnable action){h.runAtTickTime(tick,()->{try{action.run();}catch(RuntimeException|Error e){close();throw e;}});}
        public void close(){if(closed)return;closed=true;if(editorGrant!=null)editorGrant.close();MinecraftForge.EVENT_BUS.unregister(this);owners.forEach(ResearchServerEvents::removeAllResearchFor);PlayerServerEvents.rtsPlayers.removeIf(p->owners.contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->owners.contains(r.ownerName));peers.forEach(ResearchEconomyFixtures.Peer::close);entities.forEach(Entity::discard);OPEN.remove(this);}
    }
    @GameTest(templateNamespace="dreamseekers_research_economy",template="ore_mine_test_arena",batch="sandbox_reward_family",timeoutTicks=100)
    public static void mixedSandboxTrueShooterChoosesOnlyFwoOrShadowInEitherListenerOrder(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h,"Mixed");Resources a=f.account("Owner",Factions.NONE);var peer=new ResearchEconomyFixtures.Peer(h.getLevel(),a.ownerName);f.peers.add(peer);
        f.editorGrant=new com.solegendary.reignofnether.sandbox.SandboxEditorTestGrant(h.getLevel().getServer().getPlayerList().getPlayerByName(a.ownerName),PlayerServerEvents.getRTSPlayer(a.ownerName));
        // Exercise and retain the real once-per-epoch grant marker, then declare a zero-funds
        // combat fixture through the authoritative ledger. A later 20-tick initialization scan
        // must not cap-fill this fixture and mask its small kill deltas.
        h.assertTrue(DreamSandboxEconomy.initialize(h.getLevel().getServer(),a.ownerName),"real sandbox initial grant is explicitly settled before combat fixture");
        h.assertTrue(ResourceTransaction.exchange(h.getLevel().getServer(),a,a.accountEpoch,a.revision,
                new ResourceTransaction.Cost(0,0,0,Map.of(DreamPassiveEconomy.SHADOW,DreamPassiveEconomy.SHADOW_CAP)),ResourceTransaction.Cost.ZERO).applied(),"declared test setup spends initial funds without clearing initialized marker");
        Mob monster=f.spawn(EntityRegistrar.ZOMBIE_UNIT.get(),a.ownerName),dream=f.spawn(DreamseekersMod.SHADOW_HAND_UNIT.get(),a.ownerName),other=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),a.ownerName);
        h.assertTrue(!ResearchServerEvents.playerHasCheat(a.ownerName,"medievalman"),"explicit non-cheat fixture does not substitute default all-research for real research gate");
        f.kill(f.preview(peer),monster);f.kill(f.preview(peer),dream);f.kill(f.preview(peer),other);
        f.at(5,()->{
            h.assertTrue(a.food==0&&a.wood==0&&a.ore==0&&a.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==6,"unresearched Monster0, Dream5percent6shadow, Villager0; no cross-family claim");
            ResearchServerEvents.addResearch(a.ownerName,CoreEconomyResearch.id("research_global_salvage"));ResearchServerEvents.addResearch(a.ownerName,DreamEconomyResearch.CONVERSION_ID);
            f.kill(f.preview(peer),monster);f.kill(f.preview(peer),dream);
        });
        f.at(10,()->{
            h.assertTrue(a.food==2&&a.wood==4&&a.ore==6&&a.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==18,"forward listener order gives one correct family each");f.reverse=true;
            f.kill(f.preview(peer),monster);f.kill(f.preview(peer),dream);
        });
        f.at(15,()->{h.assertTrue(a.food==4&&a.wood==8&&a.ore==12&&a.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==30,"reverse listener order gives identical deltas; global duplicate subscribers cannot repay");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_research_economy",template="ore_mine_test_arena",batch="sandbox_reward_family",timeoutTicks=60)
    public static void normalOwnerCapturedOtherFactionStillUsesOriginalGlobalEconomy(GameTestHelper h) {
        Fixture f=new Fixture(h,"Normal");Resources monster=f.account("Monster",Factions.MONSTERS),dream=f.account("Dream",DreamseekersMod.DREAMSEEKERS),enemy=f.account("Victim",Factions.VILLAGERS);
        ResearchServerEvents.addResearch(monster.ownerName,CoreEconomyResearch.id("research_global_salvage"));
        Mob capturedDream=f.spawn(DreamseekersMod.SHADOW_HAND_UNIT.get(),monster.ownerName),capturedMonster=f.spawn(EntityRegistrar.ZOMBIE_UNIT.get(),dream.ownerName);
        f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy.ownerName); // retain ordinary target account throughout this test
        f.kill(f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy.ownerName),capturedDream);f.kill(f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy.ownerName),capturedMonster);
        f.at(5,()->{h.assertTrue(monster.food==2&&monster.wood==4&&monster.ore==6&&monster.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==0,"normal Monster owner captured Dream unit still earns only FWO");h.assertTrue(dream.food==0&&dream.wood==0&&dream.ore==0&&dream.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==6,"normal Dream owner captured Monster unit still earns only shadow without needing connected PlayerList actor");f.close();h.succeed();});
    }
}
