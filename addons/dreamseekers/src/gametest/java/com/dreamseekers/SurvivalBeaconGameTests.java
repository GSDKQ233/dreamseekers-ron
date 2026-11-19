// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.building.DreamLaboratoryPlacement;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.unit.FireflyWorker;
import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.neutral.Beacon;
import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.gamemode.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.survival.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import net.minecraftforge.network.*;
import java.util.*;

/** Empty, isolated map; embedded owner, actual starter workers, ordinary paid building packets and native upgrades.
 * Supplies a test bankroll and speeds construction/upgrade work, not permission, costs or victory dispatch. */
@GameTestHolder(SurvivalBeaconGameTests.NS) @PrefixGameTestTemplate(false)
public final class SurvivalBeaconGameTests {
    static final String NS = "api65_repair2_beacon", WIN = NS + "_win", GUARDS = NS + "_guards";
    private static final String TEMPLATE = "dreamseekers_test_arena";
    private static final List<ProductionItem> TIERS = List.of(ProductionItems.RESEARCH_BEACON_LEVEL_1,
            ProductionItems.RESEARCH_BEACON_LEVEL_2, ProductionItems.RESEARCH_BEACON_LEVEL_3,
            ProductionItems.RESEARCH_BEACON_LEVEL_4, ProductionItems.RESEARCH_BEACON_LEVEL_5);
    private static final List<Fixture> OPEN = new ArrayList<>();
    @AfterBatch(batch=WIN) public static void cleanupWin(ServerLevel l) { cleanup(); }
    @AfterBatch(batch=GUARDS) public static void cleanupGuards(ServerLevel l) { cleanup(); }
    private static void cleanup() { for (var f : List.copyOf(OPEN)) f.close(); }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final ServerLevel l; final String owner; final PassiveIncomePeer peer;
        final NetworkEvent.Context ctx; final BlockPos base;
        final List<BuildingPlacement> buildings = new ArrayList<>(); final List<Entity> workers = new ArrayList<>();
        final long day; final boolean allow; final int minutes;
        final net.minecraft.nbt.CompoundTag savedSurvival;
        DreamAnchorPlacement tree; DreamLaboratoryPlacement lab; BeaconPlacement beacon; RTSPlayer slot; Resources account;
        Fixture(GameTestHelper h, String owner) throws Exception {
            this.h=h; this.owner=owner; l=h.getLevel(); base=BuildingAssetGameTests.outdoorPos(h,1,2,1);
            h.assertTrue(!SurvivalServerEvents.isEnabled() && PlayerServerEvents.rtsPlayers.isEmpty()
                    && BuildingServerEvents.getBuildings().stream().noneMatch(b -> b instanceof BeaconPlacement),
                    "requires isolated fresh match, no preplaced beacon or allies");
            day=l.getDayTime(); allow=l.getGameRules().getBoolean(GameRuleRegistrar.ALLOW_BEACONS);
            minutes=l.getGameRules().getInt(GameRuleRegistrar.BEACON_WIN_MINUTES);
            savedSurvival=SurvivalSaveData.getInstance(l).save(new net.minecraft.nbt.CompoundTag());
            peer=new PassiveIncomePeer(l,owner); OPEN.add(this);
            var ctor=NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,
                    NetworkDirection.class,int.class); ctor.setAccessible(true);
            ctx=ctor.newInstance(peer.connection,NetworkDirection.PLAY_TO_SERVER,-1);
            peer.actor.moveTo(base.getX()+.5,base.getY()+3,base.getZ()+.5,0,0);
            l.addNewPlayer(peer.actor);
            PlayerServerEvents.players.add(peer.actor);
            for(int x=-3;x<5;x++)for(int z=-3;z<5;z++)l.setBlock(base.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
        }
        void start(boolean survival) {
            h.assertTrue(GameModeServerEvents.requestMode(peer.actor,survival?GameMode.SURVIVAL:GameMode.CLASSIC),
                    "authenticated solo mode proposal accepted");
            PlayerServerEvents.startRTS(peer.actor.getId(),Vec3.atCenterOf(base),DreamseekersMod.DREAMSEEKERS);
            slot=PlayerServerEvents.getRTSPlayer(owner); account=ExtendedResourcesSync.account(owner);
            h.assertTrue(slot!=null && slot.playerUuid.equals(peer.actor.getUUID()) && account!=null,
                    "normal single-player RTS start created the owner and account");
            if(survival){
                new SurvivalServerboundPacket(WaveDifficulty.BEGINNER,0).handle(()->ctx);
                h.assertTrue(SurvivalServerEvents.isEnabled(),"difficulty handshake activated Survival");
            }
            // A supplied economy, not an assertion that harvesting has been exercised.
            account.food=account.wood=account.ore=20000; account.extra.put(DreamPassiveEconomy.SHADOW,500);
        }
        void readyWorkers() {
            workers.clear();
            for(var e:UnitServerEvents.getAllUnits())
                if(e instanceof FireflyWorker w && w.level()==l && owner.equals(w.getOwnerName())
                        && l.getEntity(w.getId())==w && w.isAlive())workers.add(w);
            h.assertTrue(workers.size()==5,"five published starting fireflies for "+owner+", observed "+workers.size());
        }
        void prepare(Building b, BlockPos p) {
            var blocks=BuildingUtils.getAbsoluteBlockData(b.getRelativeBlockData(l),l,p,Rotation.NONE);
            var lo=BuildingUtils.getMinCorner(blocks); var hi=BuildingUtils.getMaxCorner(blocks);
            for(int x=lo.getX()-1;x<=hi.getX()+1;x++)for(int z=lo.getZ()-1;z<=hi.getZ()+1;z++){
                l.setBlock(new BlockPos(x,lo.getY()-1,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=lo.getY();y<=hi.getY()+12;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
        }
        void request(Building b,BlockPos p) {
            String id=ReignOfNetherRegistries.BUILDING.getKey(b).toString();
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {
                new BuildingServerboundPacket(BuildingAction.PLACE,id,p,BlockPos.ZERO,Rotation.NONE,owner,
                        new int[]{workers.get(0).getId()},false).encode(buffer);
                new BuildingServerboundPacket(buffer).handle(()->ctx);
                h.assertTrue(buffer.readableBytes()==0,"actual ordinary building packet decoded");
            } finally { buffer.release(); }
        }
        BuildingPlacement buy(Building b,BlockPos p) {
            prepare(b,p); int food=account.food,wood=account.wood,ore=account.ore;
            request(b,p);
            var placed=BuildingServerEvents.getBuildings().stream().filter(x->x.level==l && x.originPos.equals(p)
                    && x.getBuilding()==b && x.ownerName.equals(owner)).findFirst().orElse(null);
            h.assertTrue(placed!=null, "ordinary placement accepted for "+b.name+" (terrain="
                    +BuildingValidators.getPlacementValidityError(l,b,p,owner,Rotation.NONE,false,false,false)+")");
            buildings.add(placed);
            h.assertTrue(!placed.isBuilt && food-account.food==b.cost.food && wood-account.wood==b.cost.wood
                    && ore-account.ore==b.cost.ore,"actual full construction debit; starts as foundation");
            return placed;
        }
        void finish(BuildingPlacement p) {
            // Place physical geometry in this fixture instead of waiting for a several-minute worker build.
            for(var b:p.getBlocks())l.setBlock(b.getBlockPos(),b.getBlockState(),2);
            p.initPlacedBlocks();p.queueAllBlocks(l);p.onBuilt();
            h.assertTrue(p.isBuilt && p.getHealth()>0,"physically completed building");
        }
        void startTree(WorldTreeModel target) {
            tree=(DreamAnchorPlacement)buy(DreamseekersMod.DREAM_ANCHOR,base.offset(8,0,0));finish(tree);
            lab=(DreamLaboratoryPlacement)buy(DreamseekersMod.RESONANCE_LABORATORY,base.offset(26,0,0));finish(lab);
            h.assertTrue(DreamWorldTreeService.start(peer.actor,tree,tree.tree().instance,target).equals("started"),
                    "genuine paid physical tree upgrade");
            h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(owner)!=null,"pending tree is not qualified");
            tree.tree().remaining=1; // accelerate the existing paid work, not the morph or its validation
        }
        void tier(int index) {
            int f=account.food,w=account.wood,o=account.ore;var item=TIERS.get(index);
            new BuildingProductionServerboundPacket(BuildingAction.START_PRODUCTION,
                    ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item).toString(),beacon.originPos).handle(()->ctx);
            var cost=item.getCost(false,owner);
            h.assertTrue(beacon.productionQueue.size()==1 && beacon.productionQueue.get(0).item==item
                    && f-account.food==cost.food && w-account.wood==cost.wood && o-account.ore==cost.ore,
                    "full native price for tier "+(index+1));
            beacon.productionQueue.get(0).ticksLeft=1; // actual completion callback and structure transition still required
        }
        boolean receivedWinner() {
            int discriminator=PassiveIncomePeer.discriminator(new MatchStatsClientboundPacket(0,List.of()));
            for(Object packet:peer.drain())if(packet instanceof ClientboundCustomPayloadPacket payload
                    && payload.getIdentifier().toString().equals("reignofnether:main")){
                var b=payload.getData();try{
                    if(b.readVarInt()!=discriminator)continue;
                    b.readLong(); return b.readInt()==1 && b.readUtf().equals(owner)
                            && b.readResourceLocation().equals(DreamseekersMod.FACTION_ID) && b.readBoolean();
                } finally {b.release();}
            }
            return false;
        }
        public void close() {
            if(!OPEN.remove(this))return;
            for(var p:buildings){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);
                BuildingServerEvents.getBuildings().remove(p);p.forceChunk(false);}
            for(var e:l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(base).inflate(140)))
                if(e instanceof Unit u && owner.equals(u.getOwnerName()))e.discard();
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));
            ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(owner));
            PlayerServerEvents.players.remove(peer.actor);PlayerServerEvents.orthoviewPlayers.remove(peer.actor);
            l.removePlayerImmediately(peer.actor,Entity.RemovalReason.DISCARDED);peer.close();
            GameModeServerEvents.reset(l.getServer()); SurvivalServerEvents.reset();
            var old=SurvivalSaveData.load(savedSurvival);l.getDataStorage().set("saved-survival-data",old);old.setDirty();
            SurvivalServerEvents.loadWaveData(new net.minecraftforge.event.server.ServerStartedEvent(l.getServer()));
            l.getGameRules().getRule(GameRuleRegistrar.ALLOW_BEACONS).set(allow,l.getServer());
            l.getGameRules().getRule(GameRuleRegistrar.BEACON_WIN_MINUTES).set(minutes,l.getServer());
            l.setDayTime(day); PlayerServerEvents.saveRTSPlayers();
        }
    }

    @GameTest(templateNamespace=NS,template=TEMPLATE,batch=WIN,timeoutTicks=1600)
    public static void soloWindOwnerBuysAndUpgradesTheOrdinaryBeaconAndActuallyWins(GameTestHelper h)throws Exception{
        var f=new Fixture(h,"BeaconWindGT");f.start(true);
        var sequence=h.startSequence().thenWaitUntil(f::readyWorkers).thenExecute(()->f.startTree(WorldTreeModel.WIND_V4))
            .thenWaitUntil(()->h.assertTrue(f.lab.available()
                && f.tree.model()==WorldTreeModel.WIND_V4,"completed wind tree and lab"))
            .thenExecute(()->{
                h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(f.owner)==null,"Wind qualifies");
                var p=f.base.offset(42,0,0); int ore=f.account.ore;
                f.account.ore=0;f.prepare(Buildings.BEACON,p);f.request(Buildings.BEACON,p);
                h.assertTrue(f.account.ore==0 && BuildingUtils.getBeacon(false)==null,"unaffordable request builds nothing");
                f.account.ore=ore; f.beacon=(BeaconPlacement)f.buy(Buildings.BEACON,p);
                h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(f.owner)!=null,"unfinished beacon already claims sole slot");
                f.finish(f.beacon);
                h.assertTrue(!f.beacon.startProductionItem(TIERS.get(4)),"cannot skip from base to tier 5");
                f.l.getGameRules().getRule(GameRuleRegistrar.BEACON_WIN_MINUTES).set(1,f.l.getServer());
            });
        for(int i=0;i<TIERS.size();i++){
            final int tier=i;
            sequence.thenExecute(()->f.tier(tier)).thenWaitUntil(()->h.assertTrue(f.beacon.productionQueue.isEmpty()
                    && f.beacon.getUpgradeLevel()==tier+1,"native completion changes tier "+(tier+1)));
        }
        sequence.thenExecuteAfter(500,()->h.assertTrue(f.slot.beaconOwnerTicks>0 && f.slot.beaconOwnerTicks<1200
                    && !f.receivedWinner(),"real ownership timer, no premature victory"))
            .thenWaitUntil(()->h.assertTrue(f.slot.beaconOwnerTicks>=Beacon.getTicksToWin(f.l)
                    && f.receivedWinner(),"actual Survival result packet names sole Dreamseekers winner"))
            .thenSucceed();
    }

    @GameTest(templateNamespace=NS,template=TEMPLATE,batch=GUARDS,timeoutTicks=200)
    public static void shadowRequiresOwnCompletedBuildingsOrdinaryRulesAndUnoccupiedWorld(GameTestHelper h)throws Exception{
        var f=new Fixture(h,"BeaconShadowGT");f.start(false);
        h.startSequence().thenWaitUntil(f::readyWorkers).thenExecute(()->f.startTree(WorldTreeModel.SHADOW_V3))
        .thenWaitUntil(()->h.assertTrue(f.lab.available() && f.tree.model()==WorldTreeModel.SHADOW_V3,"shadow tree and lab"))
        .thenExecute(()->{
            h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(f.owner)!=null,"qualified Classic player still cannot build");
            SurvivalServerEvents.enable(WaveDifficulty.BEGINNER); // isolate mode gate; full admission is covered in companion test
            h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(f.owner)==null,"completed Shadow branch also qualifies");
            f.lab.isBuilt=false;h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(f.owner)!=null,"unfinished lab rejected");f.lab.isBuilt=true;
            f.tree.setOwnerNameFromServerEditor("OtherOwner");
            h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(f.owner)!=null,"another owner's tree is not a prerequisite");
            f.tree.setOwnerNameFromServerEditor(f.owner);
            var known=f.tree.tree();var future=known.write();
            future.getCompound("model").putString("branch","UNKNOWN_FUTURE_BRANCH");
            var opaque=WorldTreeState.read(future);
            f.tree.getDataStorage().setData(DreamWorldTreeService.STATE,opaque);
            try {
                h.assertTrue(!opaque.supported && opaque.model==null && Buildings.BEACON.getBuildAvailabilityError(f.owner)!=null
                        && opaque.write().equals(future),"unknown tree format denies without dereference or data loss");
            } finally {f.tree.getDataStorage().setData(DreamWorldTreeService.STATE,known);}
            f.l.getGameRules().getRule(GameRuleRegistrar.ALLOW_BEACONS).set(false,f.l.getServer());
            h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(f.owner)!=null,"rule disabled rejects otherwise ready owner");
            f.l.getGameRules().getRule(GameRuleRegistrar.ALLOW_BEACONS).set(true,f.l.getServer());
            h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(f.owner)==null,"same qualified owner after rule restored");
            var pre=Buildings.CAPTURABLE_BEACON.createBuildingPlacement(f.l,f.base.offset(55,0,0),Rotation.NONE,"");
            BuildingServerEvents.getBuildings().add(pre);
            try{h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(f.owner)!=null,"preplaced map beacon occupies existing singleton");}
            finally{BuildingServerEvents.getBuildings().remove(pre);}
            var nativeFactions=List.of(Factions.VILLAGERS,Factions.MONSTERS,Factions.PIGLINS);
            var caps=List.of(Buildings.CASTLE,Buildings.STRONGHOLD,Buildings.FORTRESS);
            for(int i=0;i<3;i++){
                var p=RTSPlayer.getNewBot("BeaconNative"+i,nativeFactions.get(i));PlayerServerEvents.rtsPlayers.add(p);
                var required=caps.get(i).createBuildingPlacement(f.l,f.base.offset(80,0,0),Rotation.NONE,p.name);
                try {
                    h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(p.name)!=null,"native owner needs native stronghold");
                    required.isBuilt=true;BuildingServerEvents.getBuildings().add(required);
                    h.assertTrue(Buildings.BEACON.getBuildAvailabilityError(p.name)==null,"native route unchanged for "+p.faction.key);
                } finally {BuildingServerEvents.getBuildings().remove(required);PlayerServerEvents.rtsPlayers.remove(p);}
            }
            h.assertTrue(!Factions.SURVIVAL_FACTIONS.contains(DreamseekersMod.FACTION_ID),"no Dreamseekers enemy waves");
        }).thenSucceed();
    }
}
