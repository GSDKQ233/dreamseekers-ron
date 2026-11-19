package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ability.BeaconAbility;
import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real registered BeaconAbility implementations; subclass only observes activation thread.
 * Direct API supersession tests do not claim that ordinary UI clicks bypass the five-second cooldown. */
@GameTestHolder("api64_native_beacon") @PrefixGameTestTemplate(false)
public final class NativeBeaconLifecycleGameTests {
    private static final Set<Fixture> FIXTURES=Collections.newSetFromMap(new IdentityHashMap<>());
    @AfterBatch(batch="native_beacon_lifecycle") public static void cleanup(ServerLevel ignored){for(var f:List.copyOf(FIXTURES))f.close();}
    private static final class ObservedBeacon extends BeaconPlacement {
        volatile boolean offThread;
        volatile int activations;
        ObservedBeacon(ServerLevel level,BlockPos pos,String owner) {
            super(Buildings.BEACON,level,pos,Rotation.NONE,owner,
                    new ArrayList<>(List.of(new BuildingBlock(pos,Blocks.BEACON.defaultBlockState()))),false);
            isBuilt=true;
        }
        @Override public void activate(MobEffect effect) {
            if(!getLevel().getServer().isSameThread())offThread=true;
            activations++;
            super.activate(effect);
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ObservedBeacon beacon;final long start=System.nanoTime();
        Fixture(GameTestHelper h,int offset) {
            this.h=h;FIXTURES.add(this);BlockPos pos=h.absolutePos(new BlockPos(3+offset,2,3));
            h.getLevel().setBlockAndUpdate(pos,Blocks.BEACON.defaultBlockState());
            beacon=new ObservedBeacon(h.getLevel(),pos,"Beacon_"+pos.asLong());
            BuildingServerEvents.getBuildings().add(beacon);
        }
        void ability(UnitAction action) {
            var ability=beacon.getAbilities().stream().filter(a->a.action==action).findFirst().orElseThrow();
            h.assertTrue(ability instanceof BeaconAbility,"registered concrete beacon ability");
            ability.use(h.getLevel(),beacon,beacon.originPos);
        }
        void switchAura(){ability(UnitAction.BEACON_STRENGTH);h.assertTrue(beacon.isBeaconActive(),"first activation remains immediate");ability(UnitAction.BEACON_HASTE);h.assertTrue(!beacon.isBeaconActive(),"switch first deactivates");}
        // Baseline CompletableFuture uses wall-clock time. Never let fast test ticks turn it into a false pass.
        void afterOldDeadline(Runnable check){if(System.nanoTime()-start<3_200_000_000L){h.runAfterDelay(1,()->afterOldDeadline(check));return;}check.run();}
        void done(){close();h.succeed();}
        public void close(){FIXTURES.remove(this);beacon.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(beacon);}
    }
    @GameTest(templateNamespace="api64_native_beacon",template="ore_mine_test_arena",batch="native_beacon_lifecycle",timeoutTicks=240)
    public static void registeredAbilitySwitchUsesFiftyServerTicks(GameTestHelper h){
        Fixture f=new Fixture(h,0);f.switchAura();
        h.runAfterDelay(45,()->h.assertTrue(!f.beacon.isBeaconActive(),"switch waits fifty game ticks"));
        h.runAfterDelay(55,()->f.afterOldDeadline(()->{h.assertTrue(f.beacon.isBeaconActive()&&f.beacon.getAuraEffect()==MobEffects.DIG_SPEED,"requested aura activated");h.assertTrue(!f.beacon.offThread,"activation stays on owning server thread");h.assertTrue(f.beacon.activations==2,"one immediate and one delayed activation");f.done();}));
    }
    @GameTest(templateNamespace="api64_native_beacon",template="ore_mine_test_arena",batch="native_beacon_lifecycle",timeoutTicks=240)
    public static void explicitDeactivateRevokesPendingSwitch(GameTestHelper h){Fixture f=new Fixture(h,0);f.switchAura();f.beacon.deactivate();h.runAfterDelay(70,()->f.afterOldDeadline(()->{h.assertTrue(!f.beacon.isBeaconActive()&&f.beacon.activations==1,"deactivation cannot be undone by old request");f.done();}));}
    @GameTest(templateNamespace="api64_native_beacon",template="ore_mine_test_arena",batch="native_beacon_lifecycle",timeoutTicks=240)
    public static void supportedCaptureAbaRevokesOldOwnersRequest(GameTestHelper h){Fixture f=new Fixture(h,0);f.switchAura();String owner=f.beacon.ownerName;f.beacon.setOwnerNameFromServerEditor(owner+"_other");f.beacon.setOwnerNameFromServerEditor(owner);h.runAfterDelay(70,()->f.afterOldDeadline(()->{h.assertTrue(!f.beacon.isBeaconActive()&&f.beacon.activations==1,"owner ABA cannot revive prior owner request");f.done();}));}
    @GameTest(templateNamespace="api64_native_beacon",template="ore_mine_test_arena",batch="native_beacon_lifecycle",timeoutTicks=240)
    public static void removalAndStopDetachPendingRequests(GameTestHelper h){Fixture removed=new Fixture(h,0),stopped=new Fixture(h,3);removed.switchAura();stopped.switchAura();removed.beacon.onRemoved(BuildingPlacement.RemovalReason.REPLACED);BuildingServerEvents.getBuildings().remove(removed.beacon);stopped.beacon.onRemoved(BuildingPlacement.RemovalReason.SERVER_STOP);BuildingServerEvents.getBuildings().remove(stopped.beacon);h.runAfterDelay(70,()->removed.afterOldDeadline(()->{h.assertTrue(removed.beacon.activations==1&&stopped.beacon.activations==1,"detached/stop placements never activate later");stopped.close();removed.done();}));}
    @GameTest(templateNamespace="api64_native_beacon",template="ore_mine_test_arena",batch="native_beacon_lifecycle",timeoutTicks=240)
    public static void newerDirectApiSettingSupersedesOldSwitch(GameTestHelper h){Fixture f=new Fixture(h,0);f.switchAura();f.beacon.setAuraEffect(MobEffects.REGENERATION);h.runAfterDelay(70,()->f.afterOldDeadline(()->{h.assertTrue(f.beacon.isBeaconActive()&&f.beacon.getAuraEffect()==MobEffects.REGENERATION,"new direct API setting remains authoritative");h.assertTrue(f.beacon.activations==2&&!f.beacon.offThread,"old callback never reactivates");f.done();}));}
    @GameTest(templateNamespace="api64_native_beacon",template="ore_mine_test_arena",batch="native_beacon_lifecycle",timeoutTicks=240)
    public static void missingBeaconBlockInvalidatesPendingSwitch(GameTestHelper h){Fixture f=new Fixture(h,0);f.switchAura();h.getLevel().setBlockAndUpdate(f.beacon.beaconPos,Blocks.AIR.defaultBlockState());h.runAfterDelay(70,()->f.afterOldDeadline(()->{h.assertTrue(f.beacon.activations==1&&!f.beacon.isBeaconActive(),"missing beacon cannot publish stale activation");f.done();}));}
}
