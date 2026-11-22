// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.economy.ResourceBuildingResearch;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.*;

import static com.dreamseekers.DreamseekersMod.*;

/** Real server ticks, physical placements and exact connected owners. No rendered client claim. */
@GameTestHolder("dreamseekers_resource_production")
@PrefixGameTestTemplate(false)
public final class LocalResourceResearchGameTests {
    private static final String BATCH = "local_resource_methods";
    private static final List<Fixture> OPEN = new ArrayList<>();

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final ServerLevel level;
        final String owner;
        final Resources account;
        final PassiveIncomePeer peer;
        final int site;
        final List<BuildingPlacement> buildings = new ArrayList<>();
        final Set<Long> tickets = new HashSet<>();

        Fixture(GameTestHelper h, String name, int site) {
            this.h = h; this.level = h.getLevel(); this.owner = name; this.site = site;
            h.assertTrue(PlayerServerEvents.getRTSPlayer(owner) == null, "isolated fixture owner");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner, DREAMSEEKERS));
            account = new Resources(owner, 2000, 2000, 2000);
            account.extra.put(DreamPassiveEconomy.SHADOW, 100);
            ResourcesServerEvents.resourcesList.add(account);
            peer = new PassiveIncomePeer(level, owner);
            OPEN.add(this);
        }

        BuildingPlacement install(Building type, int slot) {
            var at = new BlockPos(16000 + site * 192 + slot * 34, 162, 14000);
            for (int x = (at.getX()-8)>>4; x <= (at.getX()+28)>>4; x++)
                for (int z = (at.getZ()-8)>>4; z <= (at.getZ()+28)>>4; z++) {
                    long key = ChunkPos.asLong(x, z);
                    if (!level.getForcedChunks().contains(key)) {
                        level.setChunkForced(x,z,true); tickets.add(key);
                    }
                }
            for (var block : BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(level), level, at, Rotation.NONE)) {
                var pos = block.getBlockPos();
                if (pos.getY() == at.getY() + 1) level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(pos, block.getBlockState(), 2);
            }
            var p = BuildingUtils.getNewBuildingPlacement(type, level, at, Rotation.NONE, owner, false);
            buildings.add(p); BuildingServerEvents.getBuildings().add(p); p.onBuilt();
            return p;
        }

        ResourceBuildingResearch.View view(BuildingPlacement p) {
            var v = ResourceBuildingResearch.view(peer.actor, p);
            h.assertTrue(v != null, "local owner can inspect " + p.getBuilding().name);
            return v;
        }
        String start(BuildingPlacement p, ResourceBuildingResearch.View v) {
            return ResourceBuildingResearch.start(peer.actor, p, v.instance(), v.capture(), v.revision());
        }
        String cancel(BuildingPlacement p, ResourceBuildingResearch.View v) {
            return ResourceBuildingResearch.cancel(peer.actor, p, v.instance(), v.capture(), v.revision());
        }
        int shadow() { return account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0); }
        int netOre() { return account.ore - PlayerServerEvents.getRTSPlayer(owner).scores.scoreList.get(RTSPlayerScoresEnum.ORES_HARVESTED); }
        int[] balances() { return new int[]{account.food,account.wood,account.ore,account.emerald,shadow()}; }
        void restore(BuildingPlacement p, BuildingPlacement[] result) {
            result[0] = ResourceBuildingRoundTrip.reload(level, p);
            buildings.remove(p); buildings.add(result[0]);
        }
        @Override public void close() {
            for (var p : buildings) if (BuildingServerEvents.getBuildings().contains(p)) {
                p.onRemoved(BuildingPlacement.RemovalReason.RELOAD); BuildingServerEvents.getBuildings().remove(p);
            }
            peer.close();
            PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.equals(owner));
            ResourcesServerEvents.removeAccount(owner);
            for (long chunk : tickets) level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);
        }
    }

    @AfterBatch(batch=BATCH)
    public static void cleanup(ServerLevel level) {
        OPEN.forEach(Fixture::close); OPEN.clear();
    }

    @GameTest(template="ore_mine_test_arena", batch=BATCH, timeoutTicks=1450)
    public static void threeNaturalResearchesIncreaseOnlyPurchasedSitesAndTheirPayouts(GameTestHelper h) {
        var f = new Fixture(h,"MethodsCycle",0);
        var captor = new Fixture(h,"MethodsCaptor",6);
        var garden = f.install(REVERIE_GARDEN,0);
        var otherGarden = f.install(REVERIE_GARDEN,1); // same type, owner, dimension, and income ledger
        var mine = f.install(SHADOW_MINE,2);
        var furnace = f.install(SHADOW_FURNACE,3);
        var chosen = List.of(garden,mine,furnace);
        var before = new int[2][];
        var initial = new HashMap<BuildingPlacement,CompoundTag>();
        var reloaded = new BuildingPlacement[1];
        h.runAtTickTime(10, () -> {
            for (var p : chosen) {
                initial.put(p,p.structureSnapshot().copy());
                h.assertTrue(!f.view(p).upgraded() && !p.structureSnapshot().getBoolean("productionMethods"),"fresh ordinary form");
                h.assertTrue(f.start(p,f.view(p)).equals("started"),"independent paid local research: "+p.getBuilding().name);
                h.assertTrue(f.view(p).remaining()==600,"full 600 active-tick work starts at admission");
                h.assertTrue(p.structureSnapshot().equals(initial.get(p)), "public snapshot does not leak the paid pending row");
            }
            h.assertTrue(f.account.wood==1640 && f.account.ore==1760 && f.account.paidReceipts.size()==3,
                    "three distinct physical jobs pay 120 wood and 80 ore exactly once each");
            before[0] = f.balances();
        });
        h.runAtTickTime(410, () -> {
            for (var p : chosen) {
                int left = f.view(p).remaining();
                h.assertTrue(left>=199 && left<=201 && !ResourceBuildingResearch.isUpgraded(p), "400 of the 600 real work ticks elapsed");
            }
            // A complete 400-tick observation window, measured in authoritative currency, not a tooltip estimate.
            var b=before[0];
            h.assertTrue(f.account.food-b[0]==40 && f.account.ore-b[2]==20 && f.shadow()-b[4]==8,
                    "two gardens + mine + furnace still generate at 2× baseline during research, settled in two batches");
        });
        h.runAtTickTime(607, () -> {
            for (var p : chosen) h.assertTrue(!ResourceBuildingResearch.isUpgraded(p) && f.view(p).remaining()>0,
                    "no early promotion before 600 active ticks");
        });
        h.runAtTickTime(615, () -> {
            for (var p : chosen) {
                var snap=p.structureSnapshot();
                h.assertTrue(ResourceBuildingResearch.isUpgraded(p) && f.view(p).upgraded() && snap.getBoolean("productionMethods"),
                        "completed local technology publishes its form for "+p.getBuilding().name);
                var ordinary=snap.copy(); ordinary.remove("productionMethods"); var original=initial.get(p);
                h.assertTrue(ordinary.equals(original), "visual form changes neither placement identity, damage, model nor footprint");
                h.assertTrue(p.getBuilding().acceptsInitialStructureSnapshot(snap) && !otherGarden.acceptsStructureSnapshot(snap),
                        "reconnect accepts completed form; another placement never accepts the foreign snapshot");
                h.assertTrue(!f.start(p,f.view(p)).equals("started"),"completed site cannot pay again");
            }
            h.assertTrue(DreamPassiveEconomy.workPerTick(garden)==10 && DreamPassiveEconomy.workPerTick(mine)==10
                    && DreamPassiveEconomy.workPerTick(furnace)==10 && DreamPassiveEconomy.workPerTick(otherGarden)==4
                    && !f.view(otherGarden).upgraded() && !otherGarden.structureSnapshot().getBoolean("productionMethods"),
                    "unpaid same-owner garden retains baseline and original physical form");
            h.assertTrue(f.account.wood==1640 && f.account.paidReceipts.isEmpty(), "one debit per completion and no leftover claims");
            var id=f.view(garden).instance();
            f.restore(garden,reloaded);
            var restored=reloaded[0];
            h.assertTrue(ResourceBuildingResearch.isUpgraded(restored) && f.view(restored).instance().equals(id)
                    && restored.structureSnapshot().getBoolean("productionMethods") && !f.start(restored,f.view(restored)).equals("started"),
                    "completed physical site survives native row/codec/factory restoration, without replay or repurchase");
        });
        // First post-upgrade settlement may legitimately straddle the old and new rates.
        // Measure only after that batch, across two whole 200-active-tick settlement windows.
        h.runAtTickTime(815, () -> {
            before[1]=f.balances();
        });
        h.runAtTickTime(1215, () -> {
            var b=before[1];
            h.assertTrue(f.account.food-b[0]==70 && f.account.ore-b[2]==50 && f.shadow()-b[4]==20,
                    "400 actual ticks: restored upgraded garden +50, ordinary neighbor +20, mine +50, furnace +20");
            h.assertTrue(f.account.wood==b[1] && f.account.emerald==b[3] && f.account.paidReceipts.isEmpty(),
                    "no incidental income channel or duplicate research debit");
            var upgraded=reloaded[0];
            upgraded.setOwnerNameFromServerEditor(captor.owner);
            h.assertTrue(ResourceBuildingResearch.view(f.peer.actor,upgraded)==null && captor.view(upgraded).upgraded()
                    && !f.view(otherGarden).upgraded(), "completed physical tier stays with captured building, never either player");
        });
        h.runAtTickTime(1414, () -> h.assertTrue(captor.account.food==2000,"capture grants no early payout in the next 199 ticks"));
        h.runAtTickTime(1415, () -> {
            h.assertTrue(captor.account.food==2025 && captor.account.wood==2000 && captor.account.ore==2000,
                    "captured upgraded garden gets one fresh 25-food batch on its 200th active tick, without another payment");
            h.succeed();
        });
    }

    @GameTest(template="ore_mine_test_arena", batch=BATCH, timeoutTicks=100)
    public static void onlyOwnerOfAReadyResourceBuildingCanPayAndCancelExactlyOnce(GameTestHelper h) {
        var owner = new Fixture(h,"MethodsPay",1);
        var visitor = new Fixture(h,"MethodsVisitor",2);
        var garden = owner.install(REVERIE_GARDEN,0);
        var depot = owner.install(DREAM_DEPOT,1);
        var root = owner.install(DREAM_ANCHOR,2);
        var hall = owner.install(DREAM_HALL,3);
        ResourceBuildingResearch.View[] pending = new ResourceBuildingResearch.View[1];
        h.runAtTickTime(4, () -> {
            var token=owner.view(garden);
            for (var unrelated:List.of(depot,root,hall)) {
                h.assertTrue(DreamPassiveEconomy.workPerTick(unrelated)==0 && !owner.start(unrelated,token).equals("started"),
                        "drop-off, tree, and military Hall cannot run resource technology");
            }
            int[] saved=owner.balances();
            h.assertTrue(!visitor.start(garden,token).equals("started"),"foreign actor even with the exact site token cannot pay");
            garden.isBuilt=false;
            h.assertTrue(!owner.start(garden,token).equals("started"),"unfinished garden cannot research");
            garden.isBuilt=true;
            owner.account.wood=119; // One wood short; enough in every other channel.
            var v=owner.view(garden);
            h.assertTrue(!owner.start(garden,v).equals("started") && owner.account.wood==119 && owner.account.ore==saved[2]
                    && owner.account.paidReceipts.isEmpty(), "denial is atomic and grants no slot");
            owner.account.wood=saved[1];
            h.assertTrue(owner.start(garden,owner.view(garden)).equals("started"),"normal screened owner pays once");
            pending[0]=owner.view(garden);
            h.assertTrue(!owner.start(garden,pending[0]).equals("started"), "fresh existing-job revision rejects a duplicate, not just stale clicks");
            h.assertTrue(!visitor.cancel(garden,pending[0]).equals("cancelled_refunded"),"another actor cannot stop a paid job");
            h.assertTrue(owner.account.wood==saved[1]-120 && owner.account.ore==saved[2]-80
                    && owner.account.food==saved[0] && owner.shadow()==saved[4] && owner.account.paidReceipts.size()==1,
                    "only one immutable debit after duplicate and forged requests");
        });
        h.runAtTickTime(20, () -> {
            var now=owner.view(garden);
            h.assertTrue(now.remaining()>0 && now.remaining()<600 && !now.upgraded(),"real partial work, not an instant purchase");
            h.assertTrue(owner.cancel(garden,now).equals("cancelled_refunded"),"ordinary cancellation returns original payment");
            h.assertTrue(!owner.cancel(garden,now).equals("cancelled_refunded") && !owner.view(garden).upgraded()
                    && owner.account.wood==2000 && owner.account.ore==2000 && owner.account.paidReceipts.isEmpty(),
                    "cancelled work has no tier and cannot refund twice");
            h.assertTrue(owner.start(garden,owner.view(garden)).equals("started"),"the owner may start anew after cancellation");
            h.assertTrue(!owner.cancel(garden,pending[0]).equals("cancelled_refunded") && owner.view(garden).remaining()==600,
                    "stale cancel from the first generation neither cancels nor advances its replacement");
            h.assertTrue(owner.cancel(garden,owner.view(garden)).equals("cancelled_refunded"),"fresh cancel settles the fresh row");
            h.succeed();
        });
    }

    @GameTest(template="ore_mine_test_arena", batch=BATCH, timeoutTicks=140)
    public static void saveProjectionCaptureAndFreshRebuildNeverDuplicateTheOriginalClaim(GameTestHelper h) {
        var first=new Fixture(h,"MethodsFirst",3);
        var next=new Fixture(h,"MethodsNext",4);
        var mine=first.install(SHADOW_MINE,0);
        ResourceBuildingResearch.View[] observed=new ResourceBuildingResearch.View[1];
        BuildingPlacement[] replacement=new BuildingPlacement[1];
        h.runAtTickTime(5, () -> {
            h.assertTrue(first.start(mine,first.view(mine)).equals("started"),"real research purchase");
            observed[0]=first.view(mine);
            ResourcesServerEvents.saveResources(first.level);
            var copy=ResourcesSaveData.getInstance(first.level).resources.stream()
                    .filter(account->account.ownerName.equals(first.owner)).findFirst().orElseThrow();
            h.assertTrue(copy.wood==2000 && copy.ore==2000 && copy.paidReceipts.isEmpty(),"restart projection refunds the undelivered job");
            h.assertTrue(first.account.wood==1880 && first.account.ore==1920 && first.account.paidReceipts.size()==1
                    && first.view(mine).remaining()>0, "snapshot is copy-only, not a live refund or cancellation");
            mine.setOwnerNameFromServerEditor(next.owner);
        });
        h.runAtTickTime(25, () -> {
            h.assertTrue(first.account.wood==2000 && first.netOre()==2000 && first.account.paidReceipts.isEmpty(),
                    "capture settles original payer once");
            h.assertTrue(next.account.wood==2000 && next.netOre()==2000 && !ResourceBuildingResearch.isUpgraded(mine)
                    && !next.cancel(mine,observed[0]).equals("cancelled_refunded"),"capture conveys no partly paid tier or refund to captor");
            var fresh=next.view(mine);
            h.assertTrue(!next.start(mine,observed[0]).equals("started") && next.start(mine,fresh).equals("started"),
                    "capture revision rejects previous owner token while allowing a fresh purchase");
            var second=next.view(mine);
            h.assertTrue(next.cancel(mine,second).equals("cancelled_refunded"),"current payer recovers own charge");
            // Rebuild at the exact same coordinates, definition and owner. Only the physical identity changes.
            mine.onRemoved(BuildingPlacement.RemovalReason.REPLACED); BuildingServerEvents.getBuildings().remove(mine);
            var p=BuildingUtils.getNewBuildingPlacement(SHADOW_MINE,first.level,mine.originPos,mine.rotation,next.owner,false);
            replacement[0]=p; next.buildings.add(p); BuildingServerEvents.getBuildings().add(p); p.onBuilt();
            var newToken=next.view(p);
            h.assertTrue(!newToken.instance().equals(second.instance()) && !next.start(p,second).equals("started")
                    && !ResourceBuildingResearch.isUpgraded(p),"old site request cannot attach to same-position rebuild");
            h.assertTrue(next.start(p,newToken).equals("started"),"new owner can fund the actual replacement");
            // Exercise the supported actual administrative-removal path as well as capture.
            h.assertTrue(p.beforePermanentAdministrativeRemoval(),"ready for permanent administrative removal");
            p.onRemoved(BuildingPlacement.RemovalReason.SANDBOX); BuildingServerEvents.getBuildings().remove(p);
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(next.account.wood==2000 && next.netOre()==2000 && next.account.paidReceipts.isEmpty()
                    && first.account.wood==2000 && first.netOre()==2000 && first.account.paidReceipts.isEmpty(),
                    "removed replacement refunds only its own payer without duplication");
            h.assertTrue(!next.start(replacement[0],observed[0]).equals("started"),"detached producer cannot start research");
            h.succeed();
        });
    }

    private static CompoundTag legacyClock(String owner,int elapsed) {
        var old=new CompoundTag(); old.putInt("schema",1); old.putString("owner",owner); old.putInt("elapsed",elapsed);
        return old;
    }

    @GameTest(template="ore_mine_test_arena", batch=BATCH, timeoutTicks=890)
    public static void legacyFractionsWaitForOneAtomicBatchAndFullFurnaceCannotBankCredit(GameTestHelper h) {
        var f=new Fixture(h,"MethodsClock",5);
        var garden=f.install(REVERIE_GARDEN,0); var furnace=f.install(SHADOW_FURNACE,1);
        long[] original=new long[3]; int[] paused=new int[1];
        h.runAtTickTime(6,() -> {
            // A schema-1 save carried HALF a unit in either building. Keep that exact fraction, but
            // don't promote it to HALF a ten-second batch or award anything during offline time.
            garden.getDataStorage().setData(DreamPassiveEconomy.CLOCK,DreamPassiveEconomy.ClockState.read(legacyClock(f.owner,20)));
            furnace.getDataStorage().setData(DreamPassiveEconomy.CLOCK,DreamPassiveEconomy.ClockState.read(legacyClock(f.owner,50)));
            original[0]=f.account.food; original[1]=f.shadow(); original[2]=f.account.revision;
        });
        h.runAtTickTime(205,()-> h.assertTrue(f.account.food==original[0] && f.shadow()==original[1]
                && f.account.revision==original[2],"no payout, score or bank transaction after 199 active ticks"));
        h.runAtTickTime(206,()-> {
            h.assertTrue(f.account.food==original[0]+10 && f.shadow()==original[1]+4 && f.account.revision==original[2]+2,
                    "200th active tick: ten food and four shadow, exactly one transaction per producer");
            h.assertTrue(PlayerServerEvents.getRTSPlayer(f.owner).scores.scoreList.get(RTSPlayerScoresEnum.FOOD_HARVESTED)==10
                    && PlayerServerEvents.getRTSPlayer(f.owner).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED)==10,
                    "score records only the actually credited F/O; shadow is never misreported as ore");
            var foodClock=garden.getDataStorage().getData(DreamPassiveEconomy.CLOCK).clock(40);
            var shadowClock=furnace.getDataStorage().getData(DreamPassiveEconomy.CLOCK).clock(100);
            h.assertTrue(foodClock.batchElapsed()==0 && foodClock.work()==40 && shadowClock.batchElapsed()==0 && shadowClock.work()==100,
                    "both migrated half-units survive first settlement exactly");
            f.account.extra.put(DreamPassiveEconomy.SHADOW,999); // next batch has room for exactly one
            h.assertTrue(f.start(furnace,f.view(furnace)).equals("started"),"a nearly full balance does not block wood/ore research");
        });
        h.runAtTickTime(219,()-> {
            paused[0]=f.view(furnace).remaining();
            PlayerServerEvents.getRTSPlayer(f.owner).faction=com.solegendary.reignofnether.faction.Factions.VILLAGERS;
        });
        h.runAtTickTime(249,()-> {
            h.assertTrue(f.view(furnace).remaining()==paused[0] && f.shadow()==999,
                    "ineligible furnace faction pauses research and income");
            PlayerServerEvents.getRTSPlayer(f.owner).faction=DREAMSEEKERS;
        });
        h.runAtTickTime(266,()-> {
            int left=f.view(furnace).remaining();
            h.assertTrue(left<paused[0] && paused[0]-left<=18,"only re-enabled ticks count; no catch-up");
            h.assertTrue(f.cancel(furnace,f.view(furnace)).equals("cancelled_refunded"),"valid cancel refunds a previously paused job");
        });
        h.runAtTickTime(435,()-> h.assertTrue(f.shadow()==999,"30 paused ticks postpone the second settlement"));
        h.runAtTickTime(436,()-> h.assertTrue(f.shadow()==1000,"next four-unit request credits only one, never goes over cap"));
        h.runAtTickTime(650,()-> {
            h.assertTrue(f.shadow()==1000,"no credits beyond the cap in a further full batch");
            f.account.extra.put(DreamPassiveEconomy.SHADOW,990);
        });
        h.runAtTickTime(849,()-> h.assertTrue(f.shadow()==990,"full cap cannot bank time or clipped awards"));
        h.runAtTickTime(850,()-> {
            h.assertTrue(f.shadow()==994,"only the next independently earned four shadow are paid");
            h.succeed();
        });
    }
}
