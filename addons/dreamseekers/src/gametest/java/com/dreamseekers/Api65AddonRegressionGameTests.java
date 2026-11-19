package com.dreamseekers;

import com.dreamseekers.unit.DreamRtsUnit;
import com.dreamseekers.unit.FireflyWorker;
import com.dreamseekers.unit.flight.FlightWorkPositions;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesSaveData;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Actual server entities and Forge changeDimension; no new API is required to compile on API64. */
@GameTestHolder("api65_addon")
@PrefixGameTestTemplate(false)
public final class Api65AddonRegressionGameTests {
    private static final String BATCH = "api65_addon", PREFIX = "A65Cargo_";
    private static final List<Entity> FIXTURES = new ArrayList<>();

    @AfterBatch(batch=BATCH)
    public static void cleanup(ServerLevel level) {
        for (Entity entity : FIXTURES) if (!entity.isRemoved()) entity.discard();
        FIXTURES.clear();
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.startsWith(PREFIX));
    }

    static Mob spawn(GameTestHelper h, EntityType<? extends Mob> type, String suffix) {
        String owner = PREFIX + suffix;
        h.assertTrue(PlayerServerEvents.getRTSPlayer(owner) == null, "fresh isolated account");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner, DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(owner, 1000, 2000, 3000, 4000));
        Mob mob = type.create(h.getLevel());
        h.assertTrue(mob instanceof DreamRtsUnit, "real worker registry object");
        ((DreamRtsUnit)mob).setOwnerName(owner);
        BlockPos p = h.absolutePos(new BlockPos(3, 3, 3));
        mob.moveTo(p.getX()+.5, p.getY(), p.getZ()+.5, 0, 0);
        mob.setNoAi(true); mob.setNoGravity(true);
        h.assertTrue(h.getLevel().addFreshEntity(mob), "source is genuinely indexed");
        FIXTURES.add(mob);
        ((DreamRtsUnit)mob).getItems().addAll(List.of(new ItemStack(Items.WHEAT, 2), new ItemStack(Items.OAK_LOG, 3),
                new ItemStack(Items.RAW_IRON, 4), new ItemStack(Items.EMERALD, 5), new ItemStack(Items.AMETHYST_SHARD, 7)));
        return mob;
    }

    static ITeleporter teleporter(Vec3 position) {
        return new ITeleporter() {
            @Override public PortalInfo getPortalInfo(Entity entity, ServerLevel destination, Function<ServerLevel, PortalInfo> fallback) {
                destination.getChunk(BlockPos.containing(position));
                return new PortalInfo(position, Vec3.ZERO, 0, 0);
            }
            @Override public Entity placeEntity(Entity entity, ServerLevel previous, ServerLevel destination, float yaw,
                    Function<Boolean, Entity> reposition) { return reposition.apply(false); }
        };
    }

    static void sameValue(GameTestHelper h, Resources actual, Resources expected, String label) {
        h.assertTrue(actual.food == expected.food && actual.wood == expected.wood && actual.ore == expected.ore
                && actual.emerald == expected.emerald, label + " all four resource values");
    }

    static String transferDiagnostic(Entity source, Entity moved, ServerLevel destination) {
        Entity observed = moved == null ? source : moved;
        var pos = observed.blockPosition();
        var chunk = new net.minecraft.world.level.ChunkPos(pos);
        var actual = destination.getChunkSource().getChunkNow(chunk.x,chunk.z);
        return "source="+source.getUUID()+" sourceAdded="+source.isAddedToWorld()+" sourceRemoved="+source.isRemoved()
                +" sourceReason="+source.getRemovalReason()+" sourceLookup="+(source.level() instanceof ServerLevel sl && sl.getEntity(source.getUUID())==source)
                +" moved="+(moved==null?"null":moved.getClass().getName())+" sameBody="+(moved==source)
                +" uuid="+(moved==null?"null":moved.getUUID())+" targetLevel="+(moved!=null&&moved.level()==destination)
                +" added="+(moved!=null&&moved.isAddedToWorld())+" alive="+(moved!=null&&moved.isAlive())
                +" removed="+(moved!=null&&moved.isRemoved())+" byUuid="+(moved!=null&&destination.getEntity(moved.getUUID())==moved)
                +" byId="+(moved!=null&&destination.getEntity(moved.getId())==moved)
                +" full="+(actual==null?"null":actual.getFullStatus())
                +" entityTicking="+destination.isPositionEntityTicking(pos)+" entitiesLoaded="+destination.areEntitiesLoaded(chunk.toLong());
    }

    /** Assert committed live cargo and real persisted ledger independently of asynchronous visibility publication. */
    private static void verifyTransferState(GameTestHelper h, EntityType<? extends Mob> type,
            Mob source, Entity moved, Resources cargo, ServerLevel destination) {
        DreamRtsUnit before = (DreamRtsUnit)source;
        h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e -> e == moved).count() == 1,
                "accepted replacement participates exactly once in ordinary resource save projection");
        DreamRtsUnit target = (DreamRtsUnit)moved;
        sameValue(h, Resources.getTotalResourcesFromItems(target.getItems()), cargo, "handoff conserves");
        h.assertTrue(target.getItems().stream().filter(s -> s.is(Items.AMETHYST_SHARD)).mapToInt(ItemStack::getCount).sum() == 7,
                "non-valued cargo copied once, not duplicated");
        sameValue(h, Resources.getTotalResourcesFromItems(before.getItems()), new Resources("",0,0,0,0), "retired source cannot deliver again");
        Resources account = ResourcesServerEvents.resourcesList.stream().filter(r -> r.ownerName.equals(target.getOwnerName())).findFirst().orElseThrow();
        sameValue(h, account, new Resources("",1000,2000,3000,4000), "live bank not credited during physical transfer");
        for (int i=0; i<2; i++) {
            ResourcesServerEvents.saveResources(h.getLevel());
            Resources saved = ResourcesSaveData.getInstance(h.getLevel()).resources.stream().filter(r -> r.ownerName.equals(target.getOwnerName())).findFirst().orElseThrow();
            sameValue(h, saved, new Resources("",1000+cargo.food,2000+cargo.wood,3000+cargo.ore,4000+cargo.emerald), "repeat save projects exactly once");
        }
        CompoundTag tag = moved.saveWithoutId(new CompoundTag());
        Mob loaded = type.create(destination); loaded.load(tag);
        sameValue(h, Resources.getTotalResourcesFromItems(((DreamRtsUnit)loaded).getItems()), new Resources("",0,0,0,0),
                "ordinary persistent NBT still excludes ledger-settled cargo");
        h.assertTrue(source.changeDimension(destination, teleporter(moved.position())) == null, "retired body cannot repeat transfer");
        sameValue(h, Resources.getTotalResourcesFromItems(target.getItems()), cargo, "repeated call cannot multiply cargo");
    }

    private static void transfer(GameTestHelper h, EntityType<? extends Mob> type, String suffix) {
        Mob source = spawn(h, type, suffix);
        DreamRtsUnit before = (DreamRtsUnit)source;
        Resources cargo = Resources.getTotalResourcesFromItems(before.getItems());
        h.assertTrue(cargo.food > 0 && cargo.wood > 0 && cargo.ore > 0 && cargo.emerald > 0, "fixture carries all currencies");
        ServerLevel destination = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(destination != null, "real second dimension");
        Entity[] transferred = {null};
        h.startSequence().thenWaitUntil(() -> h.assertTrue(source.tickCount > 0
                && h.getLevel().getEntity(source.getUUID()) == source && h.getLevel().getEntity(source.getId()) == source,
                "source ordinarily published before one-shot real dimension travel"))
            .thenExecute(() -> {
                Entity moved = source.changeDimension(destination, teleporter(new Vec3(source.getX()+2048, 180, source.getZ()+2048)));
                transferred[0] = moved;
                if (moved != null && moved != source) FIXTURES.add(moved);
                System.out.println("API65_DIMENSION_AFTER_RETURN " + transferDiagnostic(source,moved,destination));
                h.assertTrue(moved instanceof DreamRtsUnit && moved != source && moved.getUUID().equals(source.getUUID())
                        && moved.level() == destination && moved.isAddedToWorld() && !moved.isRemoved(),
                        "actual same-UUID replacement admitted by destination entity manager");
                verifyTransferState(h,type,source,moved,cargo,destination);
                System.out.println("API65_DIMENSION_CARGO_COMMITTED_BEFORE_PUBLICATION " + transferDiagnostic(source,moved,destination));
            }).thenWaitUntil(() -> {
                Entity moved = transferred[0];
                h.assertTrue(destination.getEntity(moved.getUUID()) == moved && destination.getEntity(moved.getId()) == moved,
                        "ordinary destination ticket publishes same accepted replacement: " + transferDiagnostic(source,moved,destination));
            }).thenExecute(() -> {
                Entity moved = transferred[0];
                System.out.println("API65_DIMENSION_PUBLISHED " + transferDiagnostic(source,moved,destination));
                verifyTransferState(h,type,source,moved,cargo,destination);
            }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena", batch=BATCH, timeoutTicks=600)
    public static void fireflyActualDimensionTransferConservesCargo(GameTestHelper h) { transfer(h, DreamseekersMod.FIREFLY_WORKER.get(), "Firefly"); }

    @GameTest(template="unit_isolation_arena", batch=BATCH, timeoutTicks=600)
    public static void legacyStoneActualDimensionTransferConservesCargo(GameTestHelper h) { transfer(h, DreamseekersMod.STONE_SPIRIT_WORKER.get(), "Stone"); }

    @GameTest(template="unit_isolation_arena", batch=BATCH, timeoutTicks=100)
    public static void failedAndCancelledTravelKeepOriginalCargo(GameTestHelper h) {
        Mob source = spawn(h, DreamseekersMod.FIREFLY_WORKER.get(), "Cancel");
        var unit = (DreamRtsUnit)source;
        Resources cargo = Resources.getTotalResourcesFromItems(unit.getItems());
        ServerLevel destination = h.getLevel().getServer().getLevel(Level.NETHER);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(source.tickCount > 0
                && h.getLevel().getEntity(source.getUUID()) == source && h.getLevel().getEntity(source.getId()) == source,
                "source genuinely published before failure/cancellation probes"))
            .thenExecute(() -> {
                boolean[] portalCalled = {false};
                Entity failed = source.changeDimension(destination, new ITeleporter() {
                    @Override public PortalInfo getPortalInfo(Entity e, ServerLevel d, Function<ServerLevel, PortalInfo> fallback) {
                        portalCalled[0] = true;
                        return null;
                    }
                });
                h.assertTrue(portalCalled[0] && failed == null && !source.isRemoved(), "actual failed portal leaves original live");
                sameValue(h, Resources.getTotalResourcesFromItems(unit.getItems()), cargo, "failed portal keeps cargo");
                boolean[] cancelled = {false};
                Consumer<EntityTravelToDimensionEvent> cancel = event -> {
                    if (event.getEntity() == source) { cancelled[0] = true; event.setCanceled(true); }
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityTravelToDimensionEvent.class, cancel);
                try {
                    h.assertTrue(source.changeDimension(destination, teleporter(new Vec3(8096,180,8096))) == null && cancelled[0],
                            "actual Forge travel cancellation callback reached and respected");
                } finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
                h.assertTrue(!source.isRemoved() && h.getLevel().getEntity(source.getUUID()) == source, "cancel keeps original identity");
                sameValue(h, Resources.getTotalResourcesFromItems(unit.getItems()), cargo, "cancel keeps cargo");
            }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena", batch=BATCH, timeoutTicks=100)
    public static void flightWorkRejectsOtherDimensionGeometry(GameTestHelper h) {
        FireflyWorker source = (FireflyWorker)spawn(h, DreamseekersMod.FIREFLY_WORKER.get(), "Geometry");
        ServerLevel other = h.getLevel().getServer().getLevel(Level.NETHER);
        BuildingPlacement foreign = BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR, other,
                source.blockPosition(), Rotation.NONE, source.getOwnerName(), false);
        h.assertTrue(FlightWorkPositions.building(source, foreign) == null, "foreign geometry cannot create a local approach");
        h.assertTrue(!FlightWorkPositions.canWorkAtBuilding(source, foreign, source.blockPosition()), "foreign geometry cannot authorize work");
        h.succeed();
    }

    /** Candidate classification coverage; not the baseline bug proof and not a renderer/GUI acceptance. */
    @GameTest(template="unit_isolation_arena", batch=BATCH, timeoutTicks=100)
    public static void actualRegisteredNoncombatAdaptersShareVisibilitySubject(GameTestHelper h) throws Exception {
        var matches = Class.forName("com.dreamseekers.unit.DreamUnitVisibilitySubject").getMethod("matches", Object.class);
        for (EntityType<?> type : List.of(DreamseekersMod.FIREFLY_WORKER.get(), DreamseekersMod.PINK_CHICKEN_SCOUT.get(),
                DreamseekersMod.GOLDEN_FOX_UNIT.get(), DreamseekersMod.JELLYFISH_UNIT.get(), DreamseekersMod.BEETLE.get()))
            h.assertTrue(Boolean.TRUE.equals(matches.invoke(null, type.create(h.getLevel()))), "actual addon registry class is included: " + type);
        for (String id : List.of("golden_fox", "jellyfish", "pink_chicken")) {
            var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(DreamseekersMod.paster(id));
            h.assertTrue(type != null && Boolean.FALSE.equals(matches.invoke(null, type.create(h.getLevel()))), "natural dependency creature excluded: " + id);
        }
        h.assertTrue(Boolean.FALSE.equals(matches.invoke(null, new Object[]{null})), "null excluded");
        h.succeed();
    }
}
