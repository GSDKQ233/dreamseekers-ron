package com.dreamseekers;

import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import java.util.*;
import java.util.function.LongConsumer;

/** Real physical paid upgrade and ordinary server-clock production. Only fixture upgrade preparation
 * shortens remaining to one tick; WorldTreeUpgradeGameTests retains the actual 2400-tick gate.
 * This is not a simulated restart: WorldTreeRestartGameTests supplies the separate two-JVM check. */
@GameTestHolder("dreamseekers_tree_emerald")
@PrefixGameTestTemplate(false)
public final class WorldTreeEmeraldIncomeGameTests {
    private static final List<Fixture> ACTIVE=new ArrayList<>();
    private static final Set<Long> FORCED=new LinkedHashSet<>();

    @AfterBatch(batch="tree_emerald")
    public static void cleanup(ServerLevel level) {
        for(var f:ACTIVE)f.close();ACTIVE.clear();
        for(long key:FORCED){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}FORCED.clear();
    }
    private record Balance(int food,int wood,int ore,int emerald,int shadow,long revision) {
        static Balance of(Resources r){return new Balance(r.food,r.wood,r.ore,r.emerald,r.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0),r.revision);}
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final String owner;
        final List<String> owners=new ArrayList<>();final List<PassiveIncomePeer> peers=new ArrayList<>();final List<Object> hooks=new ArrayList<>();
        final DreamAnchorPlacement tree;final PassiveIncomePeer peer;Resources account;boolean finished,closed;
        Fixture(GameTestHelper helper,String suffix,WorldTreeModel target) {this(helper,suffix,target,0);}
        Fixture(GameTestHelper helper,String suffix,WorldTreeModel target,int xOffset) {
            h=helper;level=h.getLevel();owner="TreeEmerald_"+suffix;ACTIVE.add(this);
            account=addOwner(owner);peer=new PassiveIncomePeer(level,owner);peers.add(peer);
            BlockPos at=h.absolutePos(new BlockPos(30+xOffset,1,30));
            for(int x=(at.getX()-4)>>4;x<=(at.getX()+18)>>4;x++)for(int z=(at.getZ()-4)>>4;z<=(at.getZ()+18)>>4;z++){
                long key=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(key)){level.setChunkForced(x,z,true);FORCED.add(key);}
            }
            // Existing tree fixture cavity: keep gravity terrain off both Shadow and Wind models.
            for(int x=-3;x<18;x++)for(int z=-3;z<18;z++)level.setBlock(at.offset(x,21,z),Blocks.STONE.defaultBlockState(),2);
            for(int x=-3;x<18;x++)for(int z=-3;z<18;z++){
                level.setBlock(at.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=1;y<21;y++)level.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(level),level,at,Rotation.NONE))
                level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            tree=(DreamAnchorPlacement)BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,level,at,Rotation.NONE,owner,false);
            tree.isBuilt=true;BuildingServerEvents.getBuildings().add(tree);
            check(tree.getHealth()==380,"complete physical base before upgrade");
            if(target!=null){
                Balance before=Balance.of(account);var cost=DreamWorldTreeService.cost(target);
                check(cost.emerald()==0,"existing upgrade price still charges no emeralds");
                check(DreamWorldTreeService.start(peer.actor,tree,tree.tree().instance,target).equals("started"),"actual paid upgrade admitted");
                check(tree.tree().remaining==2400,"original upgrade timer is unchanged");
                check(account.food==before.food-cost.food()&&account.wood==before.wood-cost.wood()&&account.ore==before.ore-cost.ore()
                    &&account.emerald==before.emerald&&account.extra.get(DreamPassiveEconomy.SHADOW)==before.shadow-cost.extra().get(DreamPassiveEconomy.SHADOW),"original full FWO/S price charged exactly; E untouched");
                UUID receipt=tree.tree().receipt;check(account.paidReceipts.containsKey(receipt),"real paid receipt exists");
                tree.tree().remaining=1; // Fixture preparation only; do not shorten or bypass any income cycle.
                DreamWorldTreeService.tick(tree,level);
                check(tree.model()==target&&tree.tree().phase==WorldTreeState.Phase.COMPLETE&&!account.paidReceipts.containsKey(receipt),"physical morph consumes original receipt exactly once");
                check(account.emerald==before.emerald&&tree.tree().incomeElapsed==0,"upgrade completion grants no immediate E or prepaid income");
            }else DreamWorldTreeService.tick(tree,level);
            peer.drain();
        }
        Resources addOwner(String name){
            check(PlayerServerEvents.getRTSPlayer(name)==null&&ExtendedResourcesSync.account(name)==null,"fresh isolated owner "+name);
            owners.add(name);PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));
            var r=new Resources(name,10000,10000,10000,37);r.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(r);return r;
        }
        void check(boolean condition,String message){h.assertTrue(condition,message);}
        void watch(long ticks,LongConsumer assertions){
            long start=level.getGameTime();
            h.onEachTick(()->{
                if(finished)return;
                // Derive earned cycles from the native service's actual ticks, never from wall time.
                // Unknown schema intentionally never advances lastTick; only its inert observation uses game time.
                long elapsed=tree.tree().supported&&PlayerServerEvents.getRTSPlayer(tree.ownerName)!=null?tree.tree().lastTick-start:level.getGameTime()-start;
                if(elapsed<0)return;
                check(elapsed<=ticks,"no skipped natural tick at test boundary");assertions.accept(elapsed);
                if(elapsed==ticks){finished=true;System.out.println("WORLD_TREE_EMERALD_PASS "+owner+" naturalTicks="+ticks);close();h.succeed();}
            });
        }
        void gain(Resources r,Balance before,long cycles,boolean emerald,boolean shadow){
            check(r.food==capped(before.food,cycles*5,Integer.MAX_VALUE)&&r.wood==capped(before.wood,cycles*5,Integer.MAX_VALUE)
                &&r.ore==capped(before.ore,cycles*5,Integer.MAX_VALUE)&&r.emerald==capped(before.emerald,emerald?cycles:0,Integer.MAX_VALUE)
                &&r.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==capped(before.shadow,shadow?cycles:0,DreamPassiveEconomy.SHADOW_CAP),"exact independently capped F5/W5/O5/E1/S1 cycle; actual="+Balance.of(r)+" cycles="+cycles);
        }
        public void close(){if(closed)return;closed=true;for(var hook:hooks)MinecraftForge.EVENT_BUS.unregister(hook);for(var p:peers)p.close();if(tree!=null)BuildingServerEvents.getBuildings().remove(tree);for(String name:owners){PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(name));ResourcesServerEvents.removeAccount(name);}}
    }
    /** Observe after the native LevelTick END building loop; GameTest callbacks can run before it.
     * Store failures for the GameTest callback rather than throwing from an event-bus subscriber. */
    private static final class DuplicateProbe {
        final Fixture f;boolean intact=true;int observations,payoutChecks,lastEmerald;
        DuplicateProbe(Fixture fixture){f=fixture;lastEmerald=f.account.emerald;f.hooks.add(this);MinecraftForge.EVENT_BUS.register(this);}
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public void afterNativeTick(TickEvent.ServerTickEvent event){
            if(f.closed||event.phase!=TickEvent.Phase.END||event.getServer()!=f.level.getServer()
                ||f.tree.tree().lastTick!=f.level.getGameTime())return;
            var snapshot=Balance.of(f.account);int clock=f.tree.tree().incomeElapsed;
            DreamWorldTreeService.tick(f.tree,f.level);DreamWorldTreeService.tick(f.tree,f.level);
            intact&=snapshot.equals(Balance.of(f.account))&&clock==f.tree.tree().incomeElapsed;observations++;
            if(f.account.emerald!=lastEmerald){payoutChecks++;lastEmerald=f.account.emerald;}
        }
    }
    private static int capped(int before,long amount,int cap){return (int)Math.min(cap,(long)before+amount);}
    private static Fixture ascended(GameTestHelper h,String suffix){return new Fixture(h,suffix,WorldTreeModel.SHADOW_V3);}

    @GameTest(templateNamespace="dreamseekers_tree_emerald",template="ore_mine_test_arena",batch="tree_emerald",timeoutTicks=260)
    public static void fiveResourcesExactAtomicPublicationAndNoDuplicateTick(GameTestHelper h){
        var f=ascended(h,"Five");var before=Balance.of(f.account);var duplicateProbe=new DuplicateProbe(f);
        int total=PlayerServerEvents.getRTSPlayer(f.owner).scores.scoreList.getOrDefault(RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED,0);
        f.watch(201,elapsed->{
            long cycles=elapsed/100;f.gain(f.account,before,cycles,true,true);
            f.check(f.account.revision==before.revision+cycles,"one atomic ledger revision per five-resource payout");
            f.check(f.tree.tree().incomeElapsed==elapsed%100,"exact original 100 effective tick cadence");
            f.check(PlayerServerEvents.getRTSPlayer(f.owner).scores.scoreList.getOrDefault(RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED,0)==total+cycles*16,"E included once in legacy harvest total; S not counted as ore");
            f.check(duplicateProbe.intact,"duplicate calls after the same actual native tick cannot mint E or advance clock");
            if(elapsed==201)f.check(duplicateProbe.observations>=200&&duplicateProbe.payoutChecks==2,"post-native duplicate probe actually observed both paid cycles");
            if(elapsed==100||elapsed==200){var packets=f.peer.drain();f.check(PassiveIncomePeer.hasFwo(packets,f.account)&&PassiveIncomePeer.hasExtra(packets,f.account),"normal post-commit owner snapshot carries exact FWOE and S/revision");}
        });
    }

    @GameTest(templateNamespace="dreamseekers_tree_emerald",template="ore_mine_test_arena",batch="tree_emerald",timeoutTicks=260)
    public static void emeraldOnlyRoomStillProducesOnNormalCadence(GameTestHelper h){
        var f=ascended(h,"OnlyE");f.account.food=f.account.wood=f.account.ore=Integer.MAX_VALUE;f.account.extra.put(DreamPassiveEconomy.SHADOW,DreamPassiveEconomy.SHADOW_CAP);var before=Balance.of(f.account);
        f.watch(200,elapsed->{long cycles=elapsed/100;f.gain(f.account,before,cycles,true,true);f.check(f.account.revision==before.revision+cycles,"E-only room remains an atomic paid cycle");if(elapsed==200)f.check(f.account.emerald==before.emerald+2,"two actual E-only cycles; full FWO/S did not halt tree");});
    }

    @GameTest(templateNamespace="dreamseekers_tree_emerald",template="ore_mine_test_arena",batch="tree_emerald",timeoutTicks=260)
    public static void fullEmeraldDoesNotBlockOtherFourResources(GameTestHelper h){
        var f=ascended(h,"FullE");f.account.emerald=Integer.MAX_VALUE;var before=Balance.of(f.account);
        f.watch(200,elapsed->{f.gain(f.account,before,elapsed/100,true,true);f.check(f.account.revision==before.revision+elapsed/100,"clipped E does not reject FWO/S commit");});
    }

    @GameTest(templateNamespace="dreamseekers_tree_emerald",template="ore_mine_test_arena",batch="tree_emerald",timeoutTicks=460)
    public static void nearCapsClipIndependentlyAndFullTreeCannotOverflow(GameTestHelper h){
        var f=ascended(h,"Caps");f.account.food=Integer.MAX_VALUE-2;f.account.wood=Integer.MAX_VALUE-4;f.account.ore=Integer.MAX_VALUE;
        f.account.emerald=Integer.MAX_VALUE-1;f.account.extra.put(DreamPassiveEconomy.SHADOW,DreamPassiveEconomy.SHADOW_CAP-1);var before=Balance.of(f.account);
        f.watch(400,elapsed->{
            if(elapsed<=300){
                f.gain(f.account,before,elapsed/100,true,true);f.check(f.account.revision==before.revision+(elapsed<100?0:1),"all-full tree stops without empty transactions or overflow");
                f.check(f.tree.tree().incomeElapsed==(elapsed<100?elapsed:0),"fully saturated income clock does not accumulate catch-up cycles");
                if(elapsed==300){
                    var result=ResourceTransaction.exchange(f.level.getServer(),f.account,f.account.accountEpoch,f.account.revision,new ResourceTransaction.Cost(0,0,0,1,Map.of()),ResourceTransaction.Cost.ZERO);
                    f.check(result.applied()&&f.account.emerald==Integer.MAX_VALUE-1&&f.tree.tree().incomeElapsed==0,"real atomic E spend reopens exactly one slot after 200 saturated ticks, without immediate refill");
                }
            }else{
                long resumed=elapsed-300,cycles=resumed/100;
                f.check(f.account.food==Integer.MAX_VALUE&&f.account.wood==Integer.MAX_VALUE&&f.account.ore==Integer.MAX_VALUE&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==DreamPassiveEconomy.SHADOW_CAP,"reopening E room does not affect the other capped resources");
                f.check(f.account.emerald==(long)Integer.MAX_VALUE-1+cycles&&f.account.revision==before.revision+2+cycles,"released E room earns only one complete new cycle; saturated time is never back-paid");
                f.check(f.tree.tree().incomeElapsed==resumed%100,"resumed producer waits a full new 100 natural ticks");
            }
        });
    }

    @GameTest(templateNamespace="dreamseekers_tree_emerald",template="ore_mine_test_arena",batch="tree_emerald",timeoutTicks=260)
    public static void unupgradedBaseNeverProducesEmeraldOrOtherIncome(GameTestHelper h){
        var f=new Fixture(h,"Base",null);var before=Balance.of(f.account);
        f.watch(200,elapsed->f.check(before.equals(Balance.of(f.account))&&!f.tree.ascended()&&f.tree.tree().incomeElapsed==0,"unupgraded base has no income or ledger side effect"));
    }

    @GameTest(templateNamespace="dreamseekers_tree_emerald",template="ore_mine_test_arena",batch="tree_emerald",timeoutTicks=260)
    public static void windBranchRemainsNonProducer(GameTestHelper h){
        var f=new Fixture(h,"Wind",WorldTreeModel.WIND_V4);var before=Balance.of(f.account);
        f.watch(200,elapsed->f.check(before.equals(Balance.of(f.account))&&f.tree.model()==WorldTreeModel.WIND_V4&&!f.tree.ascended(),"existing Wind branch does not gain Shadow production or E"));
    }

    @GameTest(templateNamespace="dreamseekers_tree_emerald",template="ore_mine_test_arena",batch="tree_emerald",timeoutTicks=260)
    public static void missingRtsOwnerCannotMintAnyIncome(GameTestHelper h){
        var f=ascended(h,"NoOwner");PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(f.owner));var before=Balance.of(f.account);
        f.watch(200,elapsed->f.check(before.equals(Balance.of(f.account))&&f.tree.tree().incomeElapsed==0,"account without registered RTS owner cannot mint E/FWO/S"));
    }

    @GameTest(templateNamespace="dreamseekers_tree_emerald",template="ore_mine_test_arena",batch="tree_emerald",timeoutTicks=260)
    public static void unknownTreeSchemaPreservesOpaqueDataAndCannotMint(GameTestHelper h){
        var f=ascended(h,"TreeSchema");var tag=f.tree.tree().write();tag.putInt("schema",999);
        f.tree.getDataStorage().setData(DreamWorldTreeService.STATE,WorldTreeState.read(tag));var before=Balance.of(f.account);
        f.watch(200,elapsed->f.check(before.equals(Balance.of(f.account))&&!f.tree.tree().supported&&f.tree.tree().write().equals(tag)&&BuildingServerEvents.getBuildings().contains(f.tree),"future tree state is inert and preserved, not migrated or paid"));
    }

    @GameTest(templateNamespace="dreamseekers_tree_emerald",template="ore_mine_test_arena",batch="tree_emerald",timeoutTicks=260)
    public static void unknownAccountSchemaCannotMintOrRewriteOpaqueLedger(GameTestHelper h){
        var f=ascended(h,"AccountSchema");var tag=new CompoundTag();f.account.writeExtended(tag);tag.getCompound(ResourceAccountCodec.KEY).putInt("version",999);f.account.readExtended(tag);var before=Balance.of(f.account);
        f.watch(200,elapsed->{var observed=new CompoundTag();f.account.writeExtended(observed);f.check(!f.account.extendedStateSupported()&&before.equals(Balance.of(f.account))&&observed.equals(tag),"future account ledger remains exact and cannot mint E");});
    }

    @GameTest(templateNamespace="dreamseekers_tree_emerald",template="ore_mine_test_arena",batch="tree_emerald",timeoutTicks=260)
    public static void supportedCaptureDiscardsOldPartialCycleWithoutBonus(GameTestHelper h){
        var f=ascended(h,"Capture");var previous=Balance.of(f.account);var next=f.addOwner(f.owner+"Next");PlayerServerEvents.getRTSPlayer(next.ownerName).faction=com.solegendary.reignofnether.faction.Factions.VILLAGERS;var nextBefore=Balance.of(next);boolean[] captured={false};
        f.watch(200,elapsed->{
            if(!captured[0]){
                f.check(previous.equals(Balance.of(f.account))&&nextBefore.equals(Balance.of(next)),"neither owner earns before first full cycle");
                if(elapsed==75){
                    f.check(f.tree.tree().incomeElapsed==75,"real partial cycle reached before supported ownership change");long revision=f.tree.getCaptureRevision();
                    SandboxServer.setBuildingOwner(f.tree.originPos,next.ownerName);captured[0]=true;
                    f.check(f.tree.getCaptureRevision()==revision+1&&f.tree.ownerName.equals(next.ownerName)&&f.tree.tree().incomeElapsed==0,"supported capture immediately discards former owner's partial cycle");
                    f.check(previous.equals(Balance.of(f.account))&&nextBefore.equals(Balance.of(next)),"capture itself gives no resource bonus");
                }
            }else{
                f.check(previous.equals(Balance.of(f.account)),"old owner receives no post-capture income");
                f.gain(next,nextBefore,(elapsed-75)/100,true,false);
                f.check(next.revision==nextBefore.revision+(elapsed-75)/100,"new owner waits full 100 actual ticks, no inherited 75-tick credit");
            }
        });
    }

    @GameTest(templateNamespace="dreamseekers_tree_emerald",template="ore_mine_test_arena",batch="tree_emerald",timeoutTicks=260)
    public static void twoAscendedTreesElectOnlyOneFiveResourceProducer(GameTestHelper h){
        var f=ascended(h,"Election");var other=new Fixture(h,"ElectionOther",WorldTreeModel.SHADOW_V3,45);
        SandboxServer.setBuildingOwner(other.tree.originPos,f.owner);
        var before=Balance.of(f.account);var former=Balance.of(other.account);
        var leader=f.tree.tree().instance.compareTo(other.tree.tree().instance)<0?f.tree:other.tree;
        var follower=leader==f.tree?other.tree:f.tree;
        f.watch(200,elapsed->{
            f.gain(f.account,before,elapsed/100,true,true);
            f.check(f.account.revision==before.revision+elapsed/100,"two physical ascended trees still grant only one atomic five-resource cycle");
            f.check(former.equals(Balance.of(other.account)),"former second-tree owner earns no captured income");
            f.check(leader.tree().incomeElapsed==elapsed%100&&follower.tree().incomeElapsed==0,"stable instance election advances only the selected producer");
            if(elapsed==200)other.close();
        });
    }

    @GameTest(templateNamespace="dreamseekers_tree_emerald",template="ore_mine_test_arena",batch="tree_emerald",timeoutTicks=260)
    public static void newAccountEpochDiscardsPartialCycleWithoutCatchup(GameTestHelper h){
        var f=ascended(h,"Epoch");Resources old=f.account;var before=Balance.of(old);Resources[] replacement={null};
        f.watch(200,elapsed->{
            if(replacement[0]==null){
                f.check(before.equals(Balance.of(old)),"old account has no early payout");
                if(elapsed==75){
                    f.check(f.tree.tree().incomeElapsed==75,"real partial clock before account reset");
                    ResourcesServerEvents.removeAccount(f.owner);var next=new Resources(f.owner,before.food,before.wood,before.ore,before.emerald);next.extra.put(DreamPassiveEconomy.SHADOW,before.shadow);ResourcesServerEvents.resourcesList.add(next);replacement[0]=next;
                    f.check(!old.accountEpoch.equals(next.accountEpoch),"replacement is a genuinely new account epoch");
                }
            }else{
                f.check(before.equals(Balance.of(old)),"retired account is not credited");var next=replacement[0];
                f.gain(next,before,(elapsed-75)/100,true,true);f.check(next.revision==(elapsed-75)/100,"new epoch earns only its own complete cycles, with no old fractional catchup");
            }
        });
    }
}
