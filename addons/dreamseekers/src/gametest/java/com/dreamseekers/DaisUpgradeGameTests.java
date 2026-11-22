// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.building.DreamResonanceAura;
import com.dreamseekers.buildingability.BuildingAbilityService;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.economy.ResourceBuildingResearch;
import com.dreamseekers.economy.ResourceResearchPackets;
import com.dreamseekers.progression.DreamWindProgression;
import com.dreamseekers.resonance.ResonanceNetwork;
import com.dreamseekers.resonance.ResonanceResearch;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.building.production.ProductionWorkRates;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.*;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;

import static com.dreamseekers.DreamseekersMod.*;
import static com.dreamseekers.economy.ResourceResearchPackets.Action.*;
import static com.dreamseekers.economy.ResourceResearchPackets.ZERO;

/** Dedicated live server tests: real block-backed placements, a connected owner and actual elapsed ticks. */
@GameTestHolder("dreamseekers_altar_upgrade")
@PrefixGameTestTemplate(false)
public final class DaisUpgradeGameTests {
    private static final String BATCH = "per_dais_upgrade";
    private static final ResourceLocation NATIVE = ResourceLocation.fromNamespaceAndPath("reignofnether","main");
    private static final List<Fixture> FIXTURES = new ArrayList<>();

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final ServerLevel level;
        final String owner;
        final BlockPos site;
        final PassiveIncomePeer peer;
        final Resources account;
        final List<BuildingPlacement> placements = new ArrayList<>();
        final List<LivingEntity> units = new ArrayList<>();
        final Set<Long> tickets = new HashSet<>();

        Fixture(GameTestHelper h, String owner, int slot) {
            this.h=h; this.level=h.getLevel(); this.owner=owner;
            site=new BlockPos(34000 + 256*slot, 160, 34000);
            h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null, "isolated RTS owner");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DREAMSEEKERS));
            account=new Resources(owner,2000,2000,2000);
            account.extra.put(DreamPassiveEconomy.SHADOW,500);
            ResourcesServerEvents.resourcesList.add(account);
            peer=new PassiveIncomePeer(level,owner);
            FIXTURES.add(this);
        }

        void load(BlockPos at) {
            for (int x=(at.getX()-8)>>4; x<=(at.getX()+27)>>4; x++)
                for (int z=(at.getZ()-8)>>4; z<=(at.getZ()+27)>>4; z++) {
                    long key=ChunkPos.asLong(x,z);
                    if (!level.getForcedChunks().contains(key)) {
                        level.setChunkForced(x,z,true); tickets.add(key);
                    }
                    level.getChunk(x,z);
                }
        }

        BuildingPlacement install(Building definition,int x,int z) {
            BlockPos at=site.offset(x,0,z);
            load(at);
            for (var block : BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,at,Rotation.NONE)) {
                var pos=block.getBlockPos();
                if (pos.getY()==at.getY()+1) level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);
                level.setBlock(pos,block.getBlockState(),2);
            }
            var p=BuildingUtils.getNewBuildingPlacement(definition,level,at,Rotation.NONE,owner,false);
            placements.add(p);
            BuildingServerEvents.getBuildings().add(p);
            p.onBuilt();
            return p;
        }

        BuildingPlacement restore(BuildingPlacement p) {
            var loaded=ResourceBuildingRoundTrip.reload(level,p);
            placements.remove(p); placements.add(loaded);
            return loaded;
        }

        <M extends Mob & Unit> M publish(M unit,int x,int z,String team) {
            var pos=site.offset(x,1,z);
            load(pos);
            for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) {
                level.setBlock(pos.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);
                for (int y=0; y<3; y++)
                    level.setBlock(pos.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);
            }
            unit.setOwnerName(team); unit.setNoAi(true);
            unit.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            h.assertTrue(level.addFreshEntity(unit),"fixture publishes actual native unit");
            units.add(unit);
            return unit;
        }

        ResourceBuildingResearch.View inspect(BuildingPlacement p) {
            var view=ResourceBuildingResearch.view(peer.actor,p);
            h.assertTrue(view!=null,"local owner can inspect ready dais: "+p.originPos);
            return view;
        }
        String start(BuildingPlacement p,ResourceBuildingResearch.View v) {
            return ResourceBuildingResearch.start(peer.actor,p,v.instance(),v.capture(),v.revision());
        }
        String pulse(BuildingPlacement p) {
            var v=ResonanceNetwork.view(p);
            return ResonanceNetwork.pulse(peer.actor,p,v.instance(),v.ownerRevision(),v.capture(),v.generation());
        }
        String ward(BuildingPlacement p) {
            var v=BuildingAbilityService.view(peer.actor,p);
            return v==null?"no_view":BuildingAbilityService.cast(peer.actor,p,v.instance(),v.capture(),v.generation(),v.revision());
        }
        int population() { return BuildingServerEvents.getTotalPopulationSupply(owner); }
        int shadow() { return account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0); }

        @Override public void close() {
            for (var unit : units) if (!unit.isRemoved()) unit.discard();
            for (var p : placements) {
                p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);
                BuildingServerEvents.getBuildings().remove(p);
            }
            peer.close();
            ResearchServerEvents.removeAllResearchFor(owner);
            PlayerServerEvents.rtsPlayers.removeIf(player -> player.name.equals(owner));
            ResourcesServerEvents.removeAccount(owner);
            for (long key : tickets) level.setChunkForced(ChunkPos.getX(key),ChunkPos.getZ(key),false);
        }
    }

    @AfterBatch(batch=BATCH)
    public static void cleanup(ServerLevel level) {
        FIXTURES.forEach(Fixture::close); FIXTURES.clear();
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=700)
    public static void realPaidCompletionAddsOnlyThatDaisPopulationAndPersists(GameTestHelper h) {
        var f=new Fixture(h,"DaisSupply",0);
        var recipient=new Fixture(h,"DaisCaptor",1);
        h.assertTrue(f.population()==0,"no buildings means no supply");
        var promoted=f.install(RESONANCE_DAIS,26,0);
        h.assertTrue(f.population()==15,"first completed dais alone supplies 15");
        var other=f.install(RESONANCE_DAIS,50,0);
        h.assertTrue(f.population()==30,"two ordinary completed dais alone supply 30");
        var root=f.install(DREAM_ANCHOR,0,0);
        var garden=f.install(REVERIE_GARDEN,0,38);
        var baseSnapshot=promoted.structureSnapshot().copy();
        var original=new ResourceBuildingResearch.View[1];
        h.runAtTickTime(10,()-> {
            h.assertTrue(f.population()==53 && recipient.population()==0,"native tally includes 15 tree, 8 garden, and two base dais at 15 each");
            h.assertTrue(ResonanceNetwork.network(f.level,f.owner).rank(promoted)>0,"starting connected");
            other.isBuilt=false;
            try { h.assertTrue(f.population()==38,"unfinished dais contributes no population"); }
            finally { other.isBuilt=true; }
            original[0]=f.inspect(promoted);
            h.assertTrue(!original[0].upgraded() && f.start(promoted,original[0]).equals("started"),"one selected site pays for its research");
            h.assertTrue(f.inspect(promoted).remaining()==600 && f.account.wood==1880 && f.account.ore==1920
                    && f.population()==53 && promoted.structureSnapshot().equals(baseSnapshot),"pending research pays once, no early supply and no public queue data");
        });
        h.runAtTickTime(607,()-> {
            h.assertTrue(f.inspect(promoted).remaining()>0 && f.population()==53 && !ResourceBuildingResearch.isUpgraded(promoted),
                    "no promotion before the full 600 active ticks");
            f.peer.drain(); // Observe the completion's own native public synchronization next.
        });
        h.runAtTickTime(615,()-> {
            var completed=f.inspect(promoted);
            var snapshot=promoted.structureSnapshot();
            h.assertTrue(completed.upgraded() && completed.remaining()==0 && f.account.paidReceipts.isEmpty()
                    && f.account.wood==1880 && f.account.ore==1920,"mature work consumes its own exact receipt; no second debit");
            h.assertTrue(f.population()==68 && !f.inspect(other).upgraded(),"only the paid dais gains +15 more population");
            h.assertTrue(snapshot.getBoolean("daisResonance") && !snapshot.contains("productionMethods")
                    && promoted.acceptsStructureSnapshot(snapshot) && !other.acceptsStructureSnapshot(snapshot),
                    "completion flag is distinct from producer technology and bound to this physical placement");
            var noTier=snapshot.copy(); noTier.remove("daisResonance");
            h.assertTrue(noTier.equals(baseSnapshot) && promoted.getBuilding().acceptsInitialStructureSnapshot(snapshot),
                    "no unintended identity, geometry or construction damage change upon promotion");
            h.assertTrue(nativeMessages(f.peer.drain()).stream().anyMatch(p->p.action==BuildingAction.SYNC_BLOCKS_AND_OWNER
                    && p.buildingPos.equals(promoted.originPos) && p.structureSnapshot.equals(snapshot)),
                    "completion emits its authoritative instance-bound public sync to an actual connected peer");
            h.assertTrue(f.start(promoted,completed).equals("duplicate"),"fresh completed-state token still cannot repurchase");
            int originalCap=UnitServerEvents.maxPopulation;
            try {
                UnitServerEvents.maxPopulation=60;
                h.assertTrue(f.population()==60,"upgraded supply does not override the native global hard cap");
            } finally { UnitServerEvents.maxPopulation=originalCap; }

            var loaded=f.restore(promoted);
            var current=f.inspect(loaded);
            h.assertTrue(current.instance().equals(completed.instance()) && current.revision()!=completed.revision()
                    && current.upgraded() && f.population()==68 && f.start(loaded,completed).equals("stale_building"),
                    "native same-process NBT restoration rebinds completed supply, not the pre-load live token");
            f.peer.drain();
            BuildingClientboundPacket.sendSnapshotTo(f.peer.actor,loaded);
            h.assertTrue(nativeMessages(f.peer.drain()).stream().anyMatch(p->p.action==BuildingAction.PLACE && p.isBuilt
                    && p.buildingPos.equals(loaded.originPos) && p.structureSnapshot.getBoolean("daisResonance")),
                    "initial native snapshot supplies the completed form for an entering/reconnecting observer");

            // Reacquire the isolated sum without changing the world or running an interim tick.
            root.isBuilt=false; garden.isBuilt=false;
            try {
                h.assertTrue(f.population()==45,"one upgraded and one ordinary dais alone supply 45");
                other.isDestroyedServerside=true;
                try { h.assertTrue(f.population()==30,"destroyed ordinary dais supplies nothing, upgraded neighbor remains 30"); }
                finally { other.isDestroyedServerside=false; }
            } finally { root.isBuilt=true; garden.isBuilt=true; }

            loaded.setOwnerNameFromServerEditor(recipient.owner);
            h.assertTrue(f.population()==38 && recipient.population()==30
                    && ResourceBuildingResearch.view(f.peer.actor,loaded)==null && recipient.inspect(loaded).upgraded()
                    && ResonanceNetwork.network(f.level,recipient.owner).active().isEmpty(),
                    "captured physical dais retains its tier and population for new owner, independently of rooted network");
            h.succeed();
        });
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
    public static void exactAccountAndPlacementAreRequiredAndRefundSyncIsPrivate(GameTestHelper h) {
        var f=new Fixture(h,"DaisPay",2);
        var viewer=new Fixture(h,"DaisViewer",3);
        var first=f.install(RESONANCE_DAIS,0,0);
        var second=f.install(RESONANCE_DAIS,28,0);
        var lab=f.install(RESONANCE_LABORATORY,0,32);
        h.runAtTickTime(10,()-> {
            var q=new ResourceResearchPackets.Target(f.level.dimension().location(),first.originPos,
                    f.inspect(first).instance(),UUID.randomUUID(),0,0);
            var bootstrap=new ResourceResearchPackets.Request(QUERY,q,ZERO,ZERO,0);
            h.assertTrue(exchange(h,viewer.peer,bootstrap).snapshots().isEmpty(),"non-owner cannot query even with exact public physical identity");
            var opened=exchange(h,f.peer,bootstrap);
            var open=one(h,opened,"ready");
            h.assertTrue(open.progress()!=null && open.progress().remaining()==0 && !open.progress().upgraded()
                    && open.target().matchesView(q.dimension(),q.origin(),q.instance(),q.view())
                    && PassiveIncomePeer.hasFwo(opened.packets(),f.account) && PassiveIncomePeer.hasExtra(opened.packets(),f.account),
                    "owner bootstrap receives only its own panel progress and current authoritative balance/epoch");
            h.assertTrue(!ResourceBuildingResearch.supports(lab),"Dais research cannot be bought through the global research laboratory");
            var purchase=new ResourceResearchPackets.Request(START,open.target(),open.session(),open.epoch(),1);
            var started=exchange(h,f.peer,purchase);
            var paid=one(h,started,"started");
            h.assertTrue(paid.progress()!=null && paid.progress().remaining()==600 && !paid.progress().upgraded()
                    && f.account.food==2000 && f.account.wood==1880 && f.account.ore==1920 && f.shadow()==500
                    && f.account.paidReceipts.size()==1 && PassiveIncomePeer.hasFwo(started.packets(),f.account)
                    && PassiveIncomePeer.hasExtra(started.packets(),f.account),
                    "one decoded, authenticated START debits 120W/80O and resyncs real authoritative balances");
            var receipt=f.account.paidReceipts.values().iterator().next();
            h.assertTrue(receipt.purpose().equals("dreamseekers:dais_resonance/"+q.instance())
                    && receipt.payerEpoch().equals(open.epoch()),"immutable receipt is typed to this dais and the paying account epoch");
            long rev=f.account.revision;
            h.assertTrue(one(h,exchange(h,f.peer,purchase),"started").progress()==null && f.account.revision==rev,
                    "identical nonce generates only a cached ACK, not another debit or view");
            h.assertTrue(one(h,exchange(h,f.peer,new ResourceResearchPackets.Request(START,paid.target(),
                    open.session(),open.epoch(),2)),"duplicate").progress()==null && f.account.revision==rev,
                    "fresh nonce, same active site still cannot purchase twice");
            var other=f.inspect(second);
            h.assertTrue(f.start(second,other).equals("started") && f.account.paidReceipts.size()==2
                    && f.account.wood==1760 && f.account.ore==1840,"same account may independently research a second physical site");
            ResourcesServerEvents.saveResources(f.level);
            var saved=ResourcesSaveData.getInstance(f.level).resources.stream().filter(a->a.ownerName.equals(f.owner)).findFirst().orElseThrow();
            h.assertTrue(saved.wood==2000 && saved.ore==2000 && saved.paidReceipts.isEmpty() && f.account.paidReceipts.size()==2,
                    "pending research is refunded in the save projection, without refunding either live claim");

            var rogue=new ResourceResearchPackets.Request(CANCEL,paid.target(),open.session(),UUID.randomUUID(),3);
            h.assertTrue(one(h,exchange(h,f.peer,rogue),"stale_session").progress()==null
                    && f.account.paidReceipts.size()==2,"stale payer epoch cannot even disclose state or cancel");
            var cancel=new ResourceResearchPackets.Request(CANCEL,paid.target(),open.session(),open.epoch(),3);
            var refund=exchange(h,f.peer,cancel);
            var ready=one(h,refund,"cancelled_refunded");
            h.assertTrue(ready.progress()!=null && ready.progress().remaining()==0 && !ready.progress().upgraded()
                    && f.account.wood==1880 && f.account.ore==1920 && f.account.paidReceipts.size()==1
                    && !f.account.paidReceipts.containsKey(receipt.id()) && f.inspect(second).remaining()==600
                    && PassiveIncomePeer.hasFwo(refund.packets(),f.account) && PassiveIncomePeer.hasExtra(refund.packets(),f.account),
                    "only selected receipt is refunded and resynced; sibling work and its charge remain");
            h.assertTrue(one(h,exchange(h,f.peer,cancel),"cancelled_refunded").progress()==null
                    && f.account.wood==1880,"replay cannot refund twice");
            second.setOwnerNameFromServerEditor(viewer.owner);
        });
        h.runAtTickTime(50,()-> {
            h.assertTrue(f.account.wood==2000 && f.account.ore==2000 && f.account.paidReceipts.isEmpty()
                    && viewer.account.wood==2000 && viewer.account.ore==2000 && viewer.account.paidReceipts.isEmpty()
                    && !viewer.inspect(second).upgraded() && viewer.inspect(second).remaining()==0,
                    "capture cancels its pending work; only original payer gets the exact refund");
            h.succeed();
        });
    }

    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=830)
    public static void strongestAuraPulseAndWardKeepNativeEligibilityAndDoNotStack(GameTestHelper h) {
        var f=new Fixture(h,"DaisBuff",4);
        var root=f.install(DREAM_ANCHOR,0,0);
        var upgraded=f.install(RESONANCE_DAIS,24,0);
        var ordinary=f.install(RESONANCE_DAIS,44,0);
        var lab=(ProductionPlacement)f.install(RESONANCE_LABORATORY,34,11);
        var shared=f.publish(EntityRegistrar.VILLAGER_UNIT.get().create(f.level),38,-4,f.owner);
        var hostile=f.publish(BEETLE.get().create(f.level),38,-7,"DaisEnemy");
        double[] unbuffedAps={0}, plainDamage={0};
        DamageSource[] hit={null};

        h.runAtTickTime(10,()-> {
            h.assertTrue(shared.tickCount>2 && hostile.tickCount>2 && UnitServerEvents.getAllUnits().containsAll(List.of(shared,hostile)),
                    "real owned and hostile bodies are ticked and natively published");
            h.assertTrue(ResonanceNetwork.network(f.level,f.owner).active().containsAll(List.of(upgraded,ordinary)),"both Daises are rooted");
            assertPulseProfile(h,false,false,10,100);
            assertPulseProfile(h,true,false,15,160);
            assertPulseProfile(h,false,true,20,140);
            assertPulseProfile(h,true,true,25,200);
            assertRates(h,lab,110,110,"two ordinary auras do not stack");
            h.assertTrue(f.start(upgraded,f.inspect(upgraded)).equals("started"),"only left dais buys local upgrade");
        });
        h.runAtTickTime(615,()-> {
            h.assertTrue(f.inspect(upgraded).upgraded() && !f.inspect(ordinary).upgraded(),"real paid elapsed work applied to exactly one source");
            assertRates(h,lab,120,120,"strongest overlap is +20%, not +10/+20 added");
            BuildingServerEvents.getBuildings().remove(upgraded);
            try { assertRates(h,lab,110,110,"removing upgraded source leaves the ordinary neighbor's +10%"); }
            finally { BuildingServerEvents.getBuildings().add(upgraded); }

            unbuffedAps[0]=shared.getNonBaseAttacksPerSecond();
            h.assertTrue(unbuffedAps[0]>0 && f.pulse(ordinary).equals("success") && f.shadow()==485,"unmodified source casts normal paid pulse on shared recipient");
            near(h,shared.getNonBaseAttacksPerSecond(),unbuffedAps[0]*1.10,"unchanged ordinary pulse applies to native attack rate");
            h.assertTrue(f.pulse(upgraded).equals("success") && f.shadow()==470,"strictly stronger upgraded pulse may supersede existing weaker one, same 15 shadow quote");
            near(h,shared.getNonBaseAttacksPerSecond(),unbuffedAps[0]*1.20,"stronger pulse affects native attack rate once, not 10+20");
            h.assertTrue(f.pulse(upgraded).equals("cooldown") && f.shadow()==470,"repeated cast does not refresh, charge or alter cooldown");
            h.assertTrue(f.ward(upgraded).equals("requires_dais_network_tuning") && f.shadow()==470,
                    "local upgrade does not bypass account-wide tuning prerequisite for Ward");

            h.assertTrue(DreamWindProgression.earnWind(f.level,f.owner,f.account.accountEpoch),"canonical Wind fixture entitlement");
            for (int family=0; family<3; family++) for (int tier=1; tier<=5; tier++)
                DreamWindProgression.commitTier(f.account,family,tier);
            assertRates(h,lab,200,185,"combined bonuses respect existing native training and research work caps");
        });
        h.runAtTickTime(740,()->near(h,shared.getNonBaseAttacksPerSecond(),unbuffedAps[0]*1.20,
                "upgraded pulse still active after the ordinary 100-tick expiry"));
        h.runAtTickTime(760,()-> {
            near(h,shared.getNonBaseAttacksPerSecond(),unbuffedAps[0],"upgraded untuned pulse naturally ends after 140 ticks");
            // Global tuning is an existing feature with its own paid suite. Seed only that prerequisite,
            // not local completion, so the Ward tests isolate the independently researched difference.
            ResearchServerEvents.addResearch(f.owner,ResonanceResearch.ID);
            shared.getAttribute(Attributes.ARMOR).setBaseValue(0);
            shared.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
            shared.removeAllEffects(); shared.setAbsorptionAmount(0);
            var resist=shared.getAttribute(AttributeRegistrar.RANGED_DAMAGE_RESIST.get());
            if (resist!=null) resist.setBaseValue(0);
            hostile.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
            hostile.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);
            var crit=hostile.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());
            if (crit!=null) crit.setBaseValue(0);
            hit[0]=shared.damageSources().mobAttack(hostile);
            plainDamage[0]=hurtAndRestore(shared,hit[0]);
            h.assertTrue(plainDamage[0]>0 && f.ward(ordinary).equals("success") && f.shadow()==450,"ordinary connected, tuned Ward pays exactly 20 shadow");
            near(h,hurtAndRestore(shared,hit[0]),plainDamage[0]*.9,"ordinary ward's actual HP protection remains 10%");
            h.assertTrue(f.ward(upgraded).equals("success") && f.shadow()==430,"stronger overlapping ward admitted at original price");
            near(h,hurtAndRestore(shared,hit[0]),plainDamage[0]*.8,"upgraded ward reduces actual HP loss 20%, not summed or compounded");
            h.assertTrue(f.ward(upgraded).equals("cooldown") && f.shadow()==430,"ward upgrade does not refresh on duplicate cast");

            BuildingServerEvents.getBuildings().remove(root);
            try {
                near(h,hurtAndRestore(shared,hit[0]),plainDamage[0],"same-tick native damage reads cannot use a ward after disconnect");
                h.assertTrue(f.pulse(upgraded).equals("disconnected") && f.ward(upgraded).equals("requires_dais_network_tuning")
                        && f.shadow()==430,"upgraded active casts cannot bypass existing rooted network requirement");
                h.assertTrue(DreamResonanceAura.bonusPercent(lab)==20 && f.population()==45,
                        "native passive work and population retain their existing independence from the rooted network");
            } finally { BuildingServerEvents.getBuildings().add(root); }
            near(h,hurtAndRestore(shared,hit[0]),plainDamage[0],"reconnecting the network never revives a suspended ward");
            h.succeed();
        });
    }

    private static void assertRates(GameTestHelper h,ProductionPlacement lab,int units,int research,String why) {
        // Registered native queries; not ratios calculated from the tier constants under test.
        int actualUnits=ProductionWorkRates.percent(lab,WORKER_PRODUCTION);
        int actualResearch=ProductionWorkRates.percent(lab,ProductionItems.RESEARCH_EXPANDED_WORKFORCE);
        h.assertTrue(actualUnits==units && actualResearch==research,why+" actual: "+actualUnits+"/"+actualResearch);
    }
    private static void assertPulseProfile(GameTestHelper h,boolean tuned,boolean upgraded,int speed,int duration) {
        var profile=ResonanceNetwork.pulseProfile(tuned,upgraded);
        h.assertTrue(profile.speedPercent()==speed && profile.durationTicks()==duration,
                "fixed independent pulse profile for account tuned="+tuned+", local dais upgraded="+upgraded);
    }
    private static void near(GameTestHelper h,double actual,double expected,String why) {
        h.assertTrue(Math.abs(actual-expected)<.0002,why+" actual="+actual+" expected="+expected);
    }
    private static float hurtAndRestore(LivingEntity body,DamageSource damage) {
        float before=body.getHealth();
        body.invulnerableTime=0;
        body.hurt(damage,10);
        float lost=before-body.getHealth();
        body.setHealth(before);
        return lost;
    }

    private record Exchange(List<ResourceResearchPackets.Snapshot> snapshots,List<Object> packets) {}
    private static Exchange exchange(GameTestHelper h,PassiveIncomePeer peer,ResourceResearchPackets.Request sent) {
        var wire=new FriendlyByteBuf(Unpooled.buffer());
        try {
            sent.encode(wire);
            var decoded=new ResourceResearchPackets.Request(wire);
            h.assertTrue(decoded.equals(sent) && wire.readableBytes()==0,"request codec preserves exact action/session/epoch/target/sequence");
            var ctor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);
            ctor.setAccessible(true);
            var context=ctor.newInstance(peer.connection,NetworkDirection.PLAY_TO_SERVER,-1);
            decoded.handle(()->context);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("actual Forge request context unavailable",e);
        } finally { wire.release(); }

        var packets=peer.drain();
        var snapshots=new ArrayList<ResourceResearchPackets.Snapshot>();
        for (var packet : packets) if (packet instanceof ClientboundCustomPayloadPacket payload
                && payload.getIdentifier().equals(id("resource_research"))) {
            var bytes=new FriendlyByteBuf(payload.getData().copy());
            try {
                h.assertTrue(bytes.readVarInt()==1,"only the registered research reply discriminator is used");
                var reply=new ResourceResearchPackets.Snapshot(bytes);
                h.assertTrue(reply.valid() && bytes.readableBytes()==0 && reply.owner().equals(peer.actor.getGameProfile().getName())
                        && reply.completed()==sent.sequence(),"bounded reply belongs to this connected owner and sequence");
                snapshots.add(reply);
            } finally { bytes.release(); }
        }
        return new Exchange(snapshots,packets);
    }
    private static ResourceResearchPackets.Snapshot one(GameTestHelper h,Exchange exchange,String status) {
        h.assertTrue(exchange.snapshots().size()==1,"one private result expected: "+status+" actual="+exchange.snapshots());
        var reply=exchange.snapshots().get(0);
        h.assertTrue(reply.status().equals(status),"private status expected "+status+", got "+reply.status());
        return reply;
    }
    private static List<BuildingClientboundPacket> nativeMessages(List<Object> packets) {
        int discriminator=PassiveIncomePeer.discriminator(new BuildingClientboundPacket(
                BuildingAction.REMOVE,BuildingClientboundPacket.EMPTY,"",BlockPos.ZERO));
        var result=new ArrayList<BuildingClientboundPacket>();
        for (var packet : packets) if (packet instanceof ClientboundCustomPayloadPacket payload && payload.getIdentifier().equals(NATIVE)) {
            var bytes=new FriendlyByteBuf(payload.getData().copy());
            try {
                if (bytes.readVarInt()==discriminator) result.add(new BuildingClientboundPacket(bytes));
            } finally { bytes.release(); }
        }
        return result;
    }
}
