// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.resonance.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.shadow.*;
import com.dreamseekers.unit.*;
import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.EconomyActionGate;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.*;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Four real support sources in isolated sequential batches. Connected embedded peers are not
 * remote sockets. A real ServerPlayer teleportTo changes dimension; no dimension field mutation.
 * Tree geometry/research are explicit established prerequisites, not upgrade/research payment tests.
 * No synthetic clock, manual effect ticker, duration mutation or hidden lease insertion.
 */
@GameTestHolder("dreamseekers_building_support_lifecycle") @PrefixGameTestTemplate(false)
public final class BuildingSupportLifecycleGameTests {
    private static final List<F> FIXTURES=new ArrayList<>();
    private static final List<PassiveIncomePeer> RECONNECTED=new ArrayList<>();
    private static void cleanup(){for(var p:List.copyOf(RECONNECTED))p.close();RECONNECTED.clear();for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();}
    @AfterBatch(batch="support_upgrade") public static void cleanupUpgrade(ServerLevel l){cleanup();}
    @AfterBatch(batch="support_logout") public static void cleanupLogout(ServerLevel l){cleanup();}
    @AfterBatch(batch="support_dimension") public static void cleanupDimension(ServerLevel l){cleanup();}
    @AfterBatch(batch="support_capture") public static void cleanupCapture(ServerLevel l){cleanup();}
    @AfterBatch(batch="support_account") public static void cleanupAccount(ServerLevel l){cleanup();}
    static final class F implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final String owner;final Resources account;final PassiveIncomePeer peer;final BlockPos origin;
        final List<BuildingPlacement> buildings=new ArrayList<>();final List<LivingEntity> units=new ArrayList<>();final Set<Long> forced=new HashSet<>();
        final BuildingPlacement root,dais;boolean closed;
        F(GameTestHelper h,String label,int index){this.h=h;level=h.getLevel();owner="SupportLifecycle_"+label;origin=new BlockPos(-64,100,-64);
            FIXTURES.add(this);h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null,"unique owner");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
            account=new Resources(owner,5000,5000,5000);account.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(account);peer=new PassiveIncomePeer(level,owner);
            root=install(DreamseekersMod.DREAM_ANCHOR,origin);dais=install(DreamseekersMod.RESONANCE_DAIS,origin.offset(24,0,0));}
        void load(BlockPos p){for(int x=(p.getX()-32)>>4;x<=(p.getX()+48)>>4;x++)for(int z=(p.getZ()-32)>>4;z<=(p.getZ()+48)>>4;z++){
            long c=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(c)){level.setChunkForced(x,z,true);forced.add(c);}level.getChunk(x,z);}}
        BuildingPlacement install(Building type,BlockPos p){load(p);for(int x=0;x<22;x++)for(int z=0;z<22;z++){
            level.setBlock(p.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<20;y++)level.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            for(var b:BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(level),level,p,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            var b=BuildingUtils.getNewBuildingPlacement(type,level,p,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(b);buildings.add(b);b.onBuilt();return b;}
        VillagerUnit unit(BuildingPlacement p,int x,int z){load(p.originPos.offset(x,0,z));var u=EntityRegistrar.VILLAGER_UNIT.get().create(level);u.setOwnerName(owner);u.setNoAi(true);
            var pos=p.originPos.offset(x,1,z);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){level.setBlock(pos.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)level.setBlock(pos.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);}
            u.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);h.assertTrue(level.addFreshEntity(u),"actual unit added");units.add(u);return u;}
        void ready(LivingEntity u){h.assertTrue(u.tickCount>2&&level.getEntity(u.getUUID())==u&&level.getEntity(u.getId())==u&&UnitServerEvents.getAllUnits().contains(u),"native exact entity publication");}
        String pulse(BuildingPlacement p){var v=ResonanceNetwork.view(p);return ResonanceNetwork.pulse(peer.actor,p,v.instance(),v.ownerRevision(),v.capture(),v.generation());}
        int shadow(){return account.extra.get(DreamPassiveEconomy.SHADOW);}
        void later(int ticks,Runnable action){h.runAtTickTime(ticks,action);}
        @Override public void close(){if(closed)return;closed=true;if(peer.actor.serverLevel().players().contains(peer.actor))peer.actor.serverLevel().removePlayerImmediately(peer.actor,Entity.RemovalReason.DISCARDED);peer.close();for(var u:units)if(!u.isRemoved())u.discard();
            for(var b:buildings){b.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(b);}
            ResearchServerEvents.removeAllResearchFor(owner);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(owner));
            for(long c:forced)level.setChunkForced(ChunkPos.getX(c),ChunkPos.getZ(c),false);}
    }

    private static void near(GameTestHelper h,double actual,double expected,String message){h.assertTrue(Math.abs(actual-expected)<.0002,message+" actual="+actual+" expected="+expected);}
    private static String cast(ServerPlayer actor,BuildingPlacement host){var view=BuildingAbilityService.view(actor,host);return view==null?"missing_view":BuildingAbilityService.cast(actor,host,view.instance(),view.capture(),view.generation(),view.revision());}
    private static final class Support {
        final F f;final BuildingPlacement root,garden,hall,dais;
        final VillagerUnit runner,patient,warded;final com.dreamseekers.unit.FriendlyGhostUnit attacker;
        final List<BuildingPlacement> hosts;final long[] cooldown=new long[4];
        double move,ad;float hp;long start;int shadow;
        Support(GameTestHelper h,String label){
            f=new F(h,label,0);root=f.root;dais=f.dais;
            var tree=(DreamAnchorPlacement)root;
            for(var block:tree.getBlocks())f.level.setBlock(block.getBlockPos(),Blocks.AIR.defaultBlockState(),2);
            var geometry=tree.blocks(WorldTreeModel.WIND_V4);for(var block:geometry)f.level.setBlock(block.getBlockPos(),block.getBlockState(),2);
            tree.tree().model=WorldTreeModel.WIND_V4;tree.tree().phase=WorldTreeState.Phase.COMPLETE;tree.tree().ascended=false;
            tree.tree().geometryHighest=WorldTreeModel.WIND_V4.count;tree.commitGeometry(geometry,WorldTreeModel.WIND_V4.count,0);
            garden=f.install(DreamseekersMod.REVERIE_GARDEN,f.origin.offset(64,0,0));
            hall=f.install(DreamseekersMod.DREAM_HALL,f.origin.offset(96,0,0));
            f.install(DreamseekersMod.RESONANCE_LABORATORY,f.origin.offset(128,0,0));
            ResearchServerEvents.addResearch(f.owner,ResonanceResearch.ID);
            runner=f.unit(root,-3,-3);patient=f.unit(garden,-3,-3);warded=f.unit(dais,-3,-3);
            patient.setHealth(patient.getMaxHealth()-8);
            attacker=DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(f.level);attacker.setOwnerName(f.owner);attacker.setNoAi(true);
            var p=hall.originPos.offset(-3,1,-3);f.load(p);
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){f.level.setBlock(p.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<5;y++)f.level.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            attacker.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.assertTrue(f.level.addFreshEntity(attacker),"real Hall attacker publication");f.units.add(attacker);
            hosts=List.of(root,garden,hall,dais);
        }
        void ready(){for(var unit:List.of(runner,patient,warded,attacker))f.ready(unit);f.h.assertTrue(patient.tickCount>205,"natural garden combat-rest window");for(var host:hosts)f.h.assertTrue(host.isBuilt,"all real sources complete");}
        void pay(){
            move=runner.getAttributeValue(Attributes.MOVEMENT_SPEED);ad=attacker.getUnitAttackDamage();hp=patient.getHealth();int before=f.shadow(),food=f.account.food;
            for(var host:hosts)f.h.assertTrue(cast(f.peer.actor,host).equals("success"),"real paid support cast "+host.getBuilding());
            start=f.level.getGameTime();shadow=f.shadow();f.h.assertTrue(shadow==before-65&&f.account.food==food-30,"same-tick exact 20S+25S+20S and30F, no fabricated lease");
            for(int i=0;i<hosts.size();i++){var host=hosts.get(i);cooldown[i]=host.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.forBuilding(host));}
            active();
        }
        void active(){
            near(f.h,runner.getAttributeValue(Attributes.MOVEMENT_SPEED),move*1.15,"real root movement consumer remains15percent");
            near(f.h,attacker.getUnitAttackDamage(),ad*1.10,"real Hall attack consumer remains10percent");
            f.h.assertTrue(BuildingAbilityEffects.hasEffects(patient)&&DaisWardEffects.active(warded),"real garden and ward leases remain valid");
            int[] duration={160,120,200,120};for(int i=0;i<hosts.size();i++)f.h.assertTrue(BuildingAbilityEffects.remaining(hosts.get(i))==start+duration[i]-f.level.getGameTime(),"original natural deadline, not paused or refreshed: "+i);
            unchanged();
        }
        void unchanged(){f.h.assertTrue(f.shadow()==shadow,"no shadow refund/redebit; WIND fixture has no shadow passive income");for(int i=0;i<hosts.size();i++)f.h.assertTrue(hosts.get(i).getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.forBuilding(hosts.get(i)))==cooldown[i],"original paid cooldown retained: "+i);}
        void retired(){near(f.h,runner.getAttributeValue(Attributes.MOVEMENT_SPEED),move,"root actual consumer cleared");near(f.h,attacker.getUnitAttackDamage(),ad,"Hall actual consumer cleared");f.h.assertTrue(!BuildingAbilityEffects.hasEffects(patient)&&!DaisWardEffects.active(warded),"garden/ward retired");for(var host:hosts)f.h.assertTrue(BuildingAbilityEffects.remaining(host)==0,"no surviving support duration");unchanged();}
        void naturalDone(){retired();near(f.h,patient.getHealth()-hp,6,"exact six real1HP pulses survive lifecycle transition, never replayed or extended");}
    }
    @GameTest(templateNamespace="dreamseekers_building_support_lifecycle",template="dreamseekers_test_arena",batch="support_logout",timeoutTicks=500)
    public static void allFourPaidSupportsSurviveFortyOneOfflineTicksAndReconnectWithoutRenewal(GameTestHelper h){
        var s=new Support(h,"Logout");var f=s.f;PassiveIncomePeer[] online={null};UUID[] oldSession={null};var account=f.account;var player=PlayerServerEvents.getRTSPlayer(f.owner);
        h.startSequence().thenWaitUntil(s::ready).thenExecute(()->{
            s.pay();oldSession[0]=EconomyActionGate.query(f.peer.actor,f.peer.connection).session();
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.peer.actor));f.peer.close();
            h.assertTrue(!f.peer.connection.isConnected()&&f.level.getServer().getPlayerList().getPlayerByName(f.owner)==null,"actual peer/channel/PlayerList logout");
            h.assertTrue(ExtendedResourcesSync.account(f.owner)==account&&PlayerServerEvents.getRTSPlayer(f.owner)==player,"logout retains owning RTS account and identity");s.active();
        }).thenExecuteAfter(41,()->{
            h.assertTrue(f.level.getServer().getPlayerList().getPlayerByName(f.owner)==null,"actual forty-one offline ticks elapsed");s.active();near(h,s.patient.getHealth()-s.hp,2,"two actual garden pulses occur offline");
            online[0]=new PassiveIncomePeer(f.level,f.owner);RECONNECTED.add(online[0]);var token=EconomyActionGate.query(online[0].actor,online[0].connection);
            h.assertTrue(token!=null&&!token.session().equals(oldSession[0])&&token.epoch().equals(account.accountEpoch),"new connection, new session, original account epoch");
            for(var host:s.hosts)h.assertTrue(cast(online[0].actor,host).equals("cooldown"),"fresh-session retries retain each paid cooldown");s.active();
        }).thenExecuteAfter(164,()->{h.assertTrue(f.level.getGameTime()==s.start+205,"natural unmodified205tick observation");s.naturalDone();online[0].close();RECONNECTED.remove(online[0]);f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_building_support_lifecycle",template="dreamseekers_test_arena",batch="support_dimension",timeoutTicks=500)
    public static void sameRegisteredPlayerActuallyCrossesDimensionWithoutRevokingOrRenewingPaidSupport(GameTestHelper h){
        var s=new Support(h,"Dimension");var f=s.f;var actor=f.peer.actor;var home=actor.position();var other=f.level.getServer().getLevel(Level.NETHER);
        h.startSequence().thenWaitUntil(s::ready).thenExecute(()->{
            s.pay();h.assertTrue(other!=null,"real Nether exists");actor.teleportTo(other,0.5,200,0.5,0,0);
            h.assertTrue(actor.serverLevel()==other&&other.players().contains(actor)&&f.level.getServer().getPlayerList().getPlayer(actor.getUUID())==actor,"same registered ServerPlayer actually transferred, not dimension marker");s.active();
            for(var host:s.hosts)h.assertTrue(BuildingAbilityService.view(actor,host)==null,"cross-dimension actor cannot obtain/cast old-world source");
        }).thenExecuteAfter(41,()->{
            h.assertTrue(actor.serverLevel()==other,"actual forty-one ticks in other dimension");s.active();near(h,s.patient.getHealth()-s.hp,2,"old-world garden keeps original pulse clock");
            actor.teleportTo(f.level,home.x,home.y,home.z,0,0);h.assertTrue(actor.serverLevel()==f.level&&f.level.players().contains(actor),"same actual player returns to source world");
            for(var host:s.hosts)h.assertTrue(cast(actor,host).equals("cooldown"),"returning cannot repay or renew");s.active();
        }).thenExecuteAfter(164,()->{s.naturalDone();f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_building_support_lifecycle",template="dreamseekers_test_arena",batch="support_capture",timeoutTicks=330)
    public static void ActualBuildingCaptureAbaRevokesEverySupportWithoutReturnRevival(GameTestHelper h){
        var s=new Support(h,"Capture");var f=s.f;
        h.startSequence().thenWaitUntil(s::ready).thenExecute(()->{
            s.pay();for(var host:s.hosts){host.setOwnerNameFromServerEditor(f.owner+"_foreign");host.setOwnerNameFromServerEditor(f.owner);}
            s.retired();
        }).thenExecuteAfter(45,()->{s.retired();near(h,s.patient.getHealth(),s.hp,"capture-revoked garden never writes later heal");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_building_support_lifecycle",template="dreamseekers_test_arena",batch="support_account",timeoutTicks=330)
    public static void ActualAccountReplacementRevokesEverySupportAndRestoringOldAccountCannotRevive(GameTestHelper h){
        var s=new Support(h,"Account");var f=s.f;
        h.startSequence().thenWaitUntil(s::ready).thenExecute(()->{
            s.pay();h.assertTrue(ResourcesServerEvents.resourcesList.remove(f.account),"remove exact old account from native authority");
            var replacement=new Resources(f.owner,5000,5000,5000);replacement.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(replacement);
            h.assertTrue(ExtendedResourcesSync.account(f.owner)==replacement&&!replacement.accountEpoch.equals(f.account.accountEpoch),"genuine new account object and epoch");s.retired();
            h.assertTrue(replacement.extra.get(DreamPassiveEconomy.SHADOW)==500,"new account neither charged nor refunded old casts");
            ResourcesServerEvents.resourcesList.remove(replacement);ResourcesServerEvents.resourcesList.add(f.account);s.retired();
        }).thenExecuteAfter(45,()->{s.retired();near(h,s.patient.getHealth(),s.hp,"old account restoration cannot restart canceled pulses");f.close();h.succeed();});
    }

    private static TerrorbeakUpgradeService.Result upgrade(F f,TerrorbeakUnit source){return TerrorbeakUpgradeService.request(f.peer.actor,
        new TerrorbeakUpgradeRequest(1,UUID.randomUUID(),new TerrorbeakUpgradeRequest.Source(source.getId(),source.getUUID(),source.rts().ownershipRevision,f.level.dimension().location()),TerrorbeakUpgradeRequest.UpgradeTarget.CRAZY_TERRORBEAK));}
    @GameTest(templateNamespace="dreamseekers_building_support_lifecycle",template="dreamseekers_test_arena",batch="support_upgrade",timeoutTicks=260)
    public static void PaidRootBlocksRealTerrorbeakUpgradeUntilOriginalNatural160TickExpiry(GameTestHelper h){
        var s=new Support(h,"Upgrade");var f=s.f;var source=DreamseekersMod.TERRORBEAK_UNIT.get().create(f.level);source.setOwnerName(f.owner);source.setNoAi(true);
        var at=s.root.originPos.offset(-8,1,-6);f.load(at);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){f.level.setBlock(at.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<7;y++)f.level.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        source.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);h.assertTrue(f.level.addFreshEntity(source),"actual base Terrorbeak publication");f.units.add(source);
        long[] started={-1},ready={-1};double[] base={0};
        Runnable denied=()->{int food=f.account.food,ore=f.account.ore,shadow=f.shadow();long revision=f.account.revision;
            h.assertTrue(upgrade(f,source)==TerrorbeakUpgradeService.Result.ACTIVE_EFFECT,"real upgrade specifically rejects active paid building effect");
            h.assertTrue(f.account.food==food&&f.account.ore==ore&&f.shadow()==shadow&&f.account.revision==revision,"rejected conversion cannot debit/refund or revise account");
            h.assertTrue(!source.isRemoved()&&f.level.getEntity(source.getUUID())==source&&UnitServerEvents.getAllUnits().stream().noneMatch(e->e instanceof CrazyTerrorbeakUnit u&&u.getOwnerName().equals(f.owner)),"exact old body retained, no new form published");
            near(h,source.getAttributeValue(Attributes.MOVEMENT_SPEED),base[0]*1.15,"refusal cannot wash paid15percent movement");
        };
        h.startSequence().thenWaitUntil(()->{f.ready(source);h.assertTrue(s.root.isBuilt,"real completed root ready");}).thenExecute(()->{
            base[0]=source.getAttributeValue(Attributes.MOVEMENT_SPEED);int before=f.shadow();h.assertTrue(cast(f.peer.actor,s.root).equals("success")&&f.shadow()==before-20,"real20S root payment includes permanent base Terrorbeak");
            started[0]=f.level.getGameTime();ready[0]=s.root.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.ROOT_CROWN);denied.run();
        }).thenExecuteAfter(159,()->{
            h.assertTrue(f.level.getGameTime()==started[0]+159&&BuildingAbilityEffects.remaining(s.root)==1,"natural last active tick, never manual duration clear");denied.run();
        }).thenExecuteAfter(1,()->{
            h.assertTrue(f.level.getGameTime()==started[0]+160,"original exact160tick deadline");
            int food=f.account.food,ore=f.account.ore,shadow=f.shadow();h.assertTrue(upgrade(f,source)==TerrorbeakUpgradeService.Result.SUCCESS,"normal actual paid replacement succeeds only after natural root expiry");
            var replacements=UnitServerEvents.getAllUnits().stream().filter(e->e instanceof CrazyTerrorbeakUnit u&&u.getOwnerName().equals(f.owner)).toList();
            h.assertTrue(source.isRemoved()&&replacements.size()==1&&f.account.food==food-80&&f.account.ore==ore-60&&f.shadow()==shadow-30,"one physical replacement and original80F60O30S cost");
            f.units.addAll(replacements);h.assertTrue(!BuildingAbilityEffects.hasEffects(replacements.get(0))&&s.root.getDataStorage().getData(BuildingAbilityState.TYPE).readyAt(BuildingAbility.ROOT_CROWN)==ready[0],"replacement cannot inherit expired lease or reset original building cooldown");f.close();h.succeed();
        });
    }
}
