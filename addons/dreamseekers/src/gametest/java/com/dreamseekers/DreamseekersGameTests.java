// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.player.RTSPlayerScoresEnum;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.pasterdream.entity.FireflyEntity;
import net.pasterdream.entity.BlackBeetleEntity;
import net.pasterdream.entity.BasaltSnailEntity;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Headless real-Forge integration tests, excluded from both distributable jars. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DreamseekersGameTests {
    private static final String PREFIX = "DreamGT_";
    private static final String BATCH = "dreamseekers_runtime";
    private static final String ARENA = "dreamseekers_test_arena";

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        // Every test owns a distinct 112 x 16 x 136 arena and distinct account names.
        // Never remove other tests' state or the user's normal names.
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.startsWith(PREFIX));
        PlayerServerEvents.players.removeIf(p -> p.getGameProfile().getName().startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.startsWith(PREFIX));
        for (var entity : new ArrayList<>(UnitServerEvents.getAllUnits()))
            if (entity instanceof Unit unit && unit.getOwnerName().startsWith(PREFIX)) entity.discard();
        BuildingServerEvents.getBuildings().removeIf(b -> b.ownerName.startsWith(PREFIX));
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 80)
    public static void realCreatureContractsAndPersistence(GameTestHelper helper) {
        Test t = new Test(helper);
        t.check(Factions.getFaction(DreamseekersMod.FACTION_ID) == DreamseekersMod.DREAMSEEKERS, "registered faction identity");
        t.check(Factions.PLAYABLE_FACTIONS.contains(DreamseekersMod.FACTION_ID), "faction is selectable");
        t.check(!Factions.CLASSIC_FACTIONS.contains(DreamseekersMod.FACTION_ID), "no accidental survival/panorama registration");
        t.check(DreamseekersMod.DREAMSEEKERS.workerEntityType.equals(DreamseekersMod.FIREFLY_WORKER.getId()), "starting worker ID");
        t.check(DreamseekersMod.DREAMSEEKERS.scoutEntityType.equals(DreamseekersMod.BEETLE.getId()), "starting scout ID");
        t.check(ReignOfNetherRegistries.BUILDING.get(DreamseekersMod.DREAMSEEKERS.capitolBuilding) == DreamseekersMod.DREAM_ANCHOR, "starting capitol ID");
        List<EntityType<? extends Mob>> types = List.of(DreamseekersMod.FIREFLY_WORKER.get(), DreamseekersMod.BEETLE.get(), DreamseekersMod.BASALT_SNAIL.get());
        Class<?>[] originals = {FireflyEntity.class, BlackBeetleEntity.class, BasaltSnailEntity.class};
        var productions = List.of(DreamseekersMod.WORKER_PRODUCTION, DreamseekersMod.BEETLE_PRODUCTION, DreamseekersMod.SNAIL_PRODUCTION);
        for (int i = 0; i < types.size(); i++) {
            Mob original = types.get(i).create(t.level);
            t.check(original != null && originals[i].isInstance(original), "adapter retains actual PasterDream parent " + originals[i].getSimpleName());
            t.check(original instanceof Unit && original instanceof AttackerUnit, "real RTS interfaces");
            if (i == 0) t.check(original instanceof WorkerUnit, "worker interface");
            Unit unit = (Unit) original;
            unit.setOwnerName(PREFIX + "NBT" + i);
            unit.setScenarioRoleIndex(7);
            unit.setAnchor(new BlockPos(11, 12, 13));
            unit.getItems().add(new ItemStack(Items.OAK_LOG, 2));
            unit.getItems().add(new ItemStack(Items.AMETHYST_SHARD, 3));
            CompoundTag saved = new CompoundTag();
            t.check(original.save(saved), "entity writes full registry-tagged NBT");
            t.check(unit.getItems().size() == 2 && unit.getItems().get(0).is(Items.OAK_LOG),
                    "entity serialization leaves live resource cargo available to root ledger settlement");
            Entity restored = EntityType.loadEntityRecursive(saved, t.level, e -> e);
            t.check(restored instanceof Unit && originals[i].isInstance(restored), "registry reload retains external creature identity");
            Unit loaded = (Unit) restored;
            t.check(loaded.getOwnerName().equals(unit.getOwnerName()), "owner survives NBT roundtrip");
            t.equal(loaded.getScenarioRoleIndex(), 7, "scenario role persists");
            t.check(loaded.getAnchor().equals(unit.getAnchor()), "anchor persists");
            t.check(loaded.getItems().size() == 1 && loaded.getItems().get(0).is(Items.AMETHYST_SHARD)
                    && loaded.getItems().get(0).getCount() == 3, "non-resource cargo persists while ledger-settled resources do not duplicate");
            saved.getList("DreamseekersCarried", 10).add(new ItemStack(Items.OAK_LOG, 2).save(new CompoundTag()));
            Entity legacyRestored = EntityType.loadEntityRecursive(saved, t.level, e -> e);
            t.check(legacyRestored instanceof Unit legacy && legacy.getItems().size() == 1
                    && legacy.getItems().get(0).is(Items.AMETHYST_SHARD), "legacy valued cargo is filtered during NBT read too");
            t.check(Factions.getFaction(loaded) == DreamseekersMod.DREAMSEEKERS, "restored entity faction map");
            t.check(EntityRegistrar.getEntityType(EntityType.getKey(types.get(i)).toString()) == types.get(i), "namespaced entity lookup");
            t.check(ProductionItems.getProductionItem(types.get(i)) == productions.get(i), "addon production registry lookup");
        }
        t.check(EntityRegistrar.getEntityType("minecraft:item") == null, "non-RTS entity rejected");
        t.check(EntityRegistrar.getEntityType("missing:no_such_unit") == null, "unknown entity rejected");
        t.check(EntityRegistrar.getEntityType("INVALID:ID") == null, "invalid entity ID rejected");
        t.check(EntityRegistrar.getEntityType("Villager") == EntityRegistrar.VILLAGER_UNIT.get(), "legacy lookup retained");
        t.check(OreMinePlacementAccess.canPlaceFor("A", "A", false, false), "own worker/budget policy");
        t.check(!OreMinePlacementAccess.canPlaceFor("A", "B", false, false), "foreign worker/budget policy");
        t.check(OreMinePlacementAccess.canPlaceFor("A", "B", false, true), "explicit ally control policy");
        t.check(OreMinePlacementAccess.canPlaceFor("A", "B", true, false), "actual sandbox sender policy");
        t.pass("contracts_and_nbt");
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 80)
    public static void realBlockPalettesAndFourRotations(GameTestHelper helper) throws Exception {
        Test t = new Test(helper);
        for (Building building : buildings()) {
            try (InputStream stream = DreamseekersGameTests.class.getResourceAsStream("/data/dreamseekers/structures/" + building.structureName + ".nbt")) {
                t.check(stream != null, "addon structure resource exists");
                CompoundTag nbt = NbtIo.readCompressed(stream);
                ListTag palette = nbt.getList("palette", 10);
                boolean linkedBlock = false;
                for (int i = 0; i < palette.size(); i++) {
                    CompoundTag entry = palette.getCompound(i);
                    ResourceLocation id = ResourceLocation.tryParse(entry.getString("Name"));
                    t.check(id != null && BuiltInRegistries.BLOCK.containsKey(id), "real block registry contains " + id);
                    linkedBlock |= id.getNamespace().equals("pasterdream");
                    Block block = BuiltInRegistries.BLOCK.get(id);
                    CompoundTag props = entry.getCompound("Properties");
                    for (String key : props.getAllKeys()) {
                        Property<?> property = block.getStateDefinition().getProperty(key);
                        t.check(property != null && property.getValue(props.getString(key)).isPresent(), "valid runtime property " + id + "/" + key);
                    }
                }
                t.check(linkedBlock, "building uses original PasterDream registry blocks, not a renamed vanilla structure");
            }
            for (Rotation rotation : Rotation.values()) {
                var relative = building.getRelativeBlockData(t.level);
                var absolute = BuildingUtils.getAbsoluteBlockData(relative, t.level, BlockPos.ZERO, rotation);
                t.check(!relative.isEmpty(), "structure has actual geometry");
                t.equal(absolute.size(), relative.size(), "rotation preserves all blocks");
                for (int i = 0; i < relative.size(); i++) {
                    t.check(absolute.get(i).getBlockPos().equals(relative.get(i).getBlockPos().rotate(rotation).above()), "correct rotated coordinate");
                    t.check(absolute.get(i).getBlockState().getBlock() == relative.get(i).getBlockState().getBlock(), "rotated block identity");
                }
            }
        }
        t.pass("blocks_and_rotations");
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 160)
    public static void realReadiedMatchStart(GameTestHelper helper) {
        Test t = new Test(helper);
        t.flat(4, 4, 80, 80);
        String name = PREFIX + "start";
        var player = FakePlayerFactory.get(t.level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        player.moveTo(t.pos(40, 2, 40), 0, 0);
        PlayerServerEvents.players.add(player);
        PlayerServerEvents.startRTS(player.getId(), Vec3.atCenterOf(t.pos(40, 1, 40)), DreamseekersMod.DREAMSEEKERS, 1);
        t.check(PlayerServerEvents.getRTSPlayer(name) != null, "real startRTS creates RTS player");
        t.check(PlayerServerEvents.getRTSPlayer(name).faction == DreamseekersMod.DREAMSEEKERS, "real start selects new faction");
        t.equal((int) owned(name).stream().filter(e -> e instanceof FireflyWorker).count(), 5, "real match start spawns five genuine firefly workers");
        if (FogOfWarServerEvents.isEnabled())
            t.equal((int) owned(name).stream().filter(e -> e instanceof BeetleUnit).count(), 1, "real fog-enabled start spawns beetle scout");
        t.check(BuildingServerEvents.getBuildings().stream().anyMatch(b -> b.ownerName.equals(name) && b.getBuilding() == DreamseekersMod.DREAM_ANCHOR), "real readied start places registered capitol");
        helper.runAtTickTime(30, () -> {
            t.check(owned(name).stream().allMatch(e -> ((Unit) e).getOwnerName().equals(name)), "start-unit ownership remains stable after ticks");
            t.pass("readied_match_start");
        });
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 100)
    public static void placementPrerequisitesAndNoRejectedCharges(GameTestHelper helper) {
        Test t = new Test(helper);
        Resources rich = t.account("rules", DreamseekersMod.DREAMSEEKERS, 5000);
        BlockPos site = t.flat(36, 4, 25, 25);
        t.check(t.place(DreamseekersMod.BEETLE_SANCTUARY, site, rich.ownerName) == null, "real placement rejects missing completed anchor");
        t.balance(rich, 5000, 5000, 5000, "missing prerequisite does not charge");
        t.install(DreamseekersMod.DREAM_ANCHOR, rich.ownerName, 4, 4);
        Resources wrong = t.account("wrong", Factions.VILLAGERS, 5000);
        t.check(t.place(DreamseekersMod.DREAM_ANCHOR, site, wrong.ownerName) == null, "real placement rejects wrong faction");
        t.balance(wrong, 5000, 5000, 5000, "wrong faction does not charge");
        Resources poor = t.account("poor", DreamseekersMod.DREAMSEEKERS, 0);
        t.check(t.place(DreamseekersMod.DREAM_ANCHOR, site, poor.ownerName) == null, "unaffordable placement rejected");
        t.balance(poor, 0, 0, 0, "insufficient funds do not become negative");
        BuildingPlacement valid = t.place(DreamseekersMod.BEETLE_SANCTUARY, site, rich.ownerName);
        t.check(valid != null && !valid.isBuilt, "valid paid placement creates unfinished structure");
        t.balance(rich, 5000, 5000 - DreamseekersMod.BEETLE_SANCTUARY.cost.wood, 5000 - DreamseekersMod.BEETLE_SANCTUARY.cost.ore, "valid purchase charges exactly once");
        int wood = rich.wood, ore = rich.ore;
        t.check(t.place(DreamseekersMod.BEETLE_SANCTUARY, site, rich.ownerName) == null, "duplicate placement rejected");
        t.balance(rich, 5000, wood, ore, "duplicate does not charge again");
        t.pass("placement_security_and_costs");
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 240)
    public static void paidTrainingPopulationAndCancellation(GameTestHelper helper) {
        Test t = new Test(helper);
        Resources account = t.account("train", DreamseekersMod.DREAMSEEKERS, 5000);
        ProductionPlacement anchor = (ProductionPlacement) t.install(DreamseekersMod.DREAM_ANCHOR, account.ownerName, 4, 4);
        ProductionPlacement hall = (ProductionPlacement) t.install(DreamseekersMod.BEETLE_SANCTUARY, account.ownerName, 36, 4);
        var worker = DreamseekersMod.WORKER_PRODUCTION;
        var beetle = DreamseekersMod.BEETLE_PRODUCTION;
        var snail = DreamseekersMod.SNAIL_PRODUCTION;
        t.check(anchor.startProductionItem(worker), "real worker queue accepts production");
        t.equal(account.food, 5000 - worker.defaultCost.food, "training deducts food");
        t.equal(UnitServerEvents.getCurrentPopulation(account.ownerName), worker.defaultCost.population, "queued worker reserves population");
        t.check(anchor.cancelProductionItem(worker, true), "real cancellation succeeds");
        t.balance(account, 5000, 5000, 5000, "cancellation refunds exactly");
        t.equal(UnitServerEvents.getCurrentPopulation(account.ownerName), 0, "cancellation releases population");
        t.check(!anchor.startProductionItem(beetle), "capitol cannot train hall-only unit");
        int accepted = 0;
        while (hall.startProductionItem(beetle) && ++accepted < 20) { }
        t.check(accepted > 0 && accepted < 20, "population cap rejects excess queued units");
        t.check(UnitServerEvents.getCurrentPopulation(account.ownerName) <= BuildingServerEvents.getTotalPopulationSupply(account.ownerName), "queued units never exceed supply");
        while (!hall.productionQueue.isEmpty()) hall.cancelProductionItem(beetle, true);
        t.balance(account, 5000, 5000, 5000, "all queued cancellations refund without duplication");
        var scores = PlayerServerEvents.getRTSPlayer(account.ownerName).scores.scoreList;
        t.equal(scores.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED), 0, "cancelled training never records production score");
        t.equal(scores.get(RTSPlayerScoresEnum.WORKER_UNITS_PRODUCED), 0, "cancelled worker does not score");
        t.equal(scores.get(RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED), 0, "cancelled army units do not score");
        t.check(anchor.startProductionItem(worker) && hall.startProductionItem(beetle) && hall.startProductionItem(snail), "all three real unit types enter paid queue");
        anchor.productionQueue.get(0).ticksLeft = 5;
        hall.productionQueue.get(0).ticksLeft = 5;
        hall.productionQueue.get(1).ticksLeft = 5;
        helper.runAtTickTime(80, () -> {
            t.check(anchor.productionQueue.isEmpty() && hall.productionQueue.isEmpty(), "actual server ticks complete both queues");
            t.equal((int) owned(account.ownerName).stream().filter(e -> e instanceof FireflyWorker).count(), 1, "trained original firefly");
            t.equal((int) owned(account.ownerName).stream().filter(e -> e instanceof BeetleUnit).count(), 1, "trained original beetle");
            t.equal((int) owned(account.ownerName).stream().filter(e -> e instanceof BasaltSnailUnit).count(), 1, "trained original snail");
            t.equal(UnitServerEvents.getCurrentPopulation(account.ownerName), worker.defaultCost.population + beetle.defaultCost.population + snail.defaultCost.population, "completed units replace reserved population exactly");
            t.balance(account, 5000 - worker.defaultCost.food - beetle.defaultCost.food - snail.defaultCost.food,
                    5000 - worker.defaultCost.wood - beetle.defaultCost.wood - snail.defaultCost.wood,
                    5000 - worker.defaultCost.ore - beetle.defaultCost.ore - snail.defaultCost.ore, "completed training charges only once");
            t.equal(scores.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED), 3, "three completed units record total production score");
            t.equal(scores.get(RTSPlayerScoresEnum.WORKER_UNITS_PRODUCED), 1, "firefly records one worker production");
            t.equal(scores.get(RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED), 2, "beetle and snail record two military productions");
            t.pass("paid_training_and_population");
        });
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 240)
    public static void realMoveAndHoldOrders(GameTestHelper helper) {
        Test t = new Test(helper);
        String owner = t.account("move", DreamseekersMod.DREAMSEEKERS, 500).ownerName;
        t.flat(4, 4, 44, 24);
        BeetleUnit beetle = t.spawn(DreamseekersMod.BEETLE.get(), owner, 8, 10);
        Vec3 start = beetle.position();
        BlockPos target = t.pos(30, 1, 10);
        t.order(owner, UnitAction.MOVE, beetle, -1, target, BlockPos.ZERO);
        Vec3[] held = new Vec3[1];
        helper.runAtTickTime(90, () -> {
            t.check(beetle.position().distanceToSqr(start) > 9, "MOVE order really moves the PasterDream creature");
            t.check(beetle.position().distanceToSqr(Vec3.atCenterOf(target)) < start.distanceToSqr(Vec3.atCenterOf(target)), "movement approaches ordered target");
            t.order(owner, UnitAction.HOLD, beetle, -1, BlockPos.ZERO, BlockPos.ZERO);
        });
        helper.runAtTickTime(110, () -> {
            t.check(beetle.getHoldPosition(), "HOLD command is applied through real action queue");
            held[0] = beetle.position();
        });
        helper.runAtTickTime(170, () -> {
            t.check(beetle.position().distanceToSqr(held[0]) < 0.25, "HOLD prevents later navigation drift");
            t.pass("move_and_hold");
        });
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 320)
    public static void alliedSafetyAndActualEnemyDamage(GameTestHelper helper) {
        Test t = new Test(helper);
        String owner = t.account("combatA", DreamseekersMod.DREAMSEEKERS, 500).ownerName;
        String enemyOwner = t.account("combatB", DreamseekersMod.DREAMSEEKERS, 500).ownerName;
        t.flat(4, 4, 35, 24);
        BeetleUnit attacker = t.spawn(DreamseekersMod.BEETLE.get(), owner, 9, 10);
        BeetleUnit friendly = t.spawn(DreamseekersMod.BEETLE.get(), owner, 11, 10);
        float friendlyHealth = friendly.getHealth();
        StoneSpiritWorker[] enemy = new StoneSpiritWorker[1];
        float[] enemyHealth = new float[1];
        helper.runAtTickTime(60, () -> {
            t.check(friendly.getHealth() == friendlyHealth && attacker.getTarget() != friendly, "idle allied creatures do not target or hurt one another");
            enemy[0] = t.spawn(DreamseekersMod.STONE_SPIRIT_WORKER.get(), enemyOwner, 14, 10);
            enemyHealth[0] = enemy[0].getHealth();
            t.order(owner, UnitAction.ATTACK, attacker, enemy[0].getId(), enemy[0].blockPosition(), BlockPos.ZERO);
        });
        helper.runAtTickTime(240, () -> {
            t.check(!enemy[0].isAlive() || enemy[0].getHealth() < enemyHealth[0], "actual RTS attack causes health loss to enemy creature");
            t.check(friendly.getHealth() == friendlyHealth, "friendly creature was not damaged during combat");
            t.pass("allied_safety_and_enemy_damage");
        });
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 1300)
    public static void workerBuildsAndHarvestsLinkedLog(GameTestHelper helper) {
        Test t = new Test(helper);
        // Only this fixture: catch natural falling gravel before install/flat clears its support.
        // y=13 caps unchanged cleared y=2..12; all coordinates stay inside the 112x16x136 arena.
        for (int roofX = 2; roofX <= 60; roofX++)
            for (int roofZ = 2; roofZ <= 54; roofZ++)
                t.level.setBlock(t.pos(roofX, 13, roofZ), Blocks.STONE.defaultBlockState(), 2);
        Resources account = t.account("work", DreamseekersMod.DREAMSEEKERS, 5000);
        BuildingPlacement anchor = t.install(DreamseekersMod.DREAM_ANCHOR, account.ownerName, 4, 4);
        BlockPos site = t.flat(36, 4, 25, 25);
        BuildingPlacement garden = t.place(DreamseekersMod.REVERIE_GARDEN, site, account.ownerName);
        t.check(garden != null && !garden.isBuilt, "worker fixture has a genuinely unfinished paid building");
        FireflyWorker builder = t.spawn(DreamseekersMod.FIREFLY_WORKER.get(), account.ownerName, 35, 7);
        t.order(account.ownerName, UnitAction.BUILD_REPAIR, builder, -1, garden.originPos, garden.originPos);
        int foundationBlocks = (int) garden.getBlocks().stream()
                .filter(b -> !b.getBlockState().isAir()
                        && b.getBlockPos().getY() <= garden.minCorner.getY() + garden.getBuilding().foundationYLayers - 1
                        && garden.getBuilding().startingBlockTypes.contains(b.getBlockState().getBlock())).count();
        helper.runAtTickTime(260, () -> {
            t.check(garden.getBlocksPlaced() > foundationBlocks, "real worker builds beyond every automatically queued foundation block");
            t.check(garden.isBuilt || builder.getBuildRepairGoal().getBuildingTarget() == garden, "worker retains actual build target until completed");
            t.check(!garden.selfBuilding, "construction was worker AI, not self-build shortcut");
        });
        t.flat(4, 25, 26, 30);
        FireflyWorker gatherer = t.spawn(DreamseekersMod.FIREFLY_WORKER.get(), account.ownerName, 9, 38);
        BlockPos logPos = t.pos(11, 2, 38);
        Block log = ForgeRegistries.BLOCKS.getValue(DreamseekersMod.paster("dyedream_log"));
        t.check(log != null, "real PasterDream dyedream log exists");
        t.level.setBlock(logPos, log.defaultBlockState(), 3);
        t.check(ResourceSources.getBlockResourceName(logPos, t.level) == ResourceName.WOOD, "PasterDream log is actually recognized as RTS wood");
        t.order(account.ownerName, UnitAction.MOVE, gatherer, -1, logPos, BlockPos.ZERO);
        int woodBefore = account.wood;
        linkedHarvestRaw(helper, gatherer, account, logPos, "setup-after-original-MOVE");
        for (int diagnosticTick : new int[]{20, 100, 200, 349})
            helper.runAtTickTime(diagnosticTick, () ->
                    linkedHarvestRaw(helper, gatherer, account, logPos, "scheduled-" + diagnosticTick));
        helper.runAtTickTime(350, () -> {
            linkedHarvestRaw(helper, gatherer, account, logPos, "350-before-original-assertion");
            t.check(t.level.getBlockState(logPos).getBlock() != log, "worker actually harvested the linked-mod log");
            t.check(!gatherer.getItems().isEmpty() || account.wood > woodBefore, "harvest produced carried RTS resources or deposited wood");
            t.order(account.ownerName, UnitAction.RETURN_RESOURCES, gatherer, -1, anchor.originPos, anchor.originPos);
        });
        for (int diagnosticTick : new int[]{351, 400, 500, 619, 900, 1199}) helper.runAtTickTime(diagnosticTick, () ->
                System.out.println("FIREFLY_LINKED_RETURN_DIAGNOSTIC tick=" + diagnosticTick + " " + FireflyNavigationTestDiagnostics.describe(gatherer, account)));
        // Native air movement measured about .05 blocks/tick with unchanged .25
        // attributes: allow the actual 25-block return trip, not the old ground deadline.
        helper.runAtTickTime(1200, () -> {
            t.check(account.wood > woodBefore, "real worker drop-off credits owner's wood ledger");
            t.pass("worker_build_and_linked_log_gather");
        });
    }

    private static void linkedHarvestRaw(GameTestHelper helper, FireflyWorker worker,
                                         Resources account, BlockPos logPos, String phase) {
        var level = helper.getLevel();
        var gather = worker.getGatherResourceGoal();
        var path = worker.getNavigation().getPath();
        var source = gather.data.targetResourceSource;
        var returning = worker.getReturnResourcesGoal().getBuildingTarget();
        var building = worker.getBuildRepairGoal().getBuildingTarget();
        String queued;
        var slow = UnitServerEvents.getUnitActionSlowQueue();
        synchronized (slow) {
            queued = slow.stream()
                    .filter(action -> java.util.Arrays.stream(action.getUnitIds()).anyMatch(id -> id == worker.getId()))
                    .map(action -> action.getAction().toString()).toList().toString();
        }
        System.out.println("FIREFLY_LINKED_HARVEST_RAW phase=" + phase
                + " testTick=" + helper.getTick() + " gameTime=" + level.getGameTime()
                + " worldSeed=" + level.getSeed() + " templateOrigin=" + helper.absolutePos(BlockPos.ZERO)
                + " entityTick=" + worker.tickCount + " id=" + worker.getId() + " uuid=" + worker.getUUID()
                + " publishedId=" + (level.getEntity(worker.getId()) == worker)
                + " publishedUuid=" + (level.getEntity(worker.getUUID()) == worker)
                + " registered=" + UnitServerEvents.getAllUnits().contains(worker)
                + " alive=" + worker.isAlive() + " removed=" + worker.isRemoved()
                + " added=" + worker.isAddedToWorld() + " noAI=" + worker.isNoAi()
                + " sourceChunk=" + linkedChunkRaw(level, worker.blockPosition())
                + " logChunk=" + linkedChunkRaw(level, logPos)
                + " health=" + worker.getHealth() + " pos=" + worker.position()
                + " velocity=" + worker.getDeltaMovement() + " onGround=" + worker.onGround()
                + " horizontalCollision=" + worker.horizontalCollision + " verticalCollision=" + worker.verticalCollision
                + " move=" + worker.getMoveGoal().getMoveTarget()
                + " moveRaw=" + linkedRawFields(worker.getMoveGoal(), "isRunning", "pathPending", "recalcCooldown", "currentRecalcCooldown", "pathRequestSeq")
                + " navDone=" + worker.getNavigation().isDone()
                + " path=" + (path == null ? "null" : "reach=" + path.canReach() + ",next=" + path.getNextNodeIndex()
                    + "/" + path.getNodeCount() + ",target=" + path.getTarget()
                    + ",nodes=" + path.nodes.stream().map(node -> node.asBlockPos().toString()).toList())
                + " control=" + worker.getMoveControl().operation
                + " controlRaw=" + linkedRawFields(worker.getMoveControl(), "wantedX", "wantedY", "wantedZ", "speedModifier")
                + " gather=" + gather.data.gatherTarget + " gatherMove=" + gather.getMoveTarget()
                + " gatherName=" + gather.data.targetResourceName + " gatherTodo=" + gather.data.todoGatherTargets
                + " gatherSource=" + (source == null ? "null" : source.name + ":ticks=" + source.ticksToGather + ":value=" + source.resourceValue + ":kind=" + source.resourceName)
                + " gatherRaw=" + linkedRawFields(gather, "gatherTicksLeft", "cdTicksLeft", "searchCdTicksLeft", "failedSearches",
                    "ticksWithoutTarget", "ticksIdle", "ticksStationaryWithTarget", "lastOnPos", "altSearchPos", "isGatheringServerside",
                    "isRunning", "pathPending", "recalcCooldown", "currentRecalcCooldown", "pathRequestSeq", "approaches")
                + " return=" + (returning == null ? "null" : returning.originPos)
                + " build=" + (building == null ? "null" : building.originPos)
                + " pending=" + UnitServerEvents.hasPendingImmediateAction(worker.getId()) + " queued=" + queued
                + " rush=" + com.dreamseekers.skill.FireflyRush.active(worker)
                + " rushRemaining=" + com.dreamseekers.skill.FireflyRush.remainingTicks(worker)
                + " cargoCount=" + worker.getItems().stream().mapToInt(ItemStack::getCount).sum()
                + " ledger=" + account.food + "/" + account.wood + "/" + account.ore);
        // Fixed existing-fixture positions only, from already-loaded LevelChunks.
        // Missing chunks print unloaded; these observations never request/create a chunk.
        System.out.println("FIREFLY_LINKED_HARVEST_BLOCKS phase=" + phase + " logPos=" + logPos
                + " log=" + linkedLoadedBlock(level, logPos) + " floor=" + linkedLoadedBlock(level, logPos.below())
                + " up=" + linkedLoadedBlock(level, logPos.above()) + " up2=" + linkedLoadedBlock(level, logPos.above(2))
                + " north=" + linkedLoadedBlock(level, logPos.north()) + " south=" + linkedLoadedBlock(level, logPos.south())
                + " east=" + linkedLoadedBlock(level, logPos.east()) + " west=" + linkedLoadedBlock(level, logPos.west())
                + " west2=" + linkedLoadedBlock(level, logPos.west(2)) + " westUp=" + linkedLoadedBlock(level, logPos.west().above())
                + " clearedCeiling=" + linkedLoadedBlock(level, logPos.above(10))
                + " firstUnclearedAbove=" + linkedLoadedBlock(level, logPos.above(11)));
    }

    /** Never uses Level.getBlockState, which may synchronously request a missing chunk. */
    private static String linkedLoadedBlock(ServerLevel level, BlockPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        return chunk == null ? "unloaded" : chunk.getBlockState(position).toString();
    }

    private static String linkedChunkRaw(ServerLevel level, BlockPos position) {
        var chunkPos = new net.minecraft.world.level.ChunkPos(position);
        var chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        return chunkPos + ":loaded=" + (chunk != null)
                + ":entityTicking=" + level.isPositionEntityTicking(position)
                + ":entitiesLoaded=" + level.areEntitiesLoaded(chunkPos.toLong());
    }

    /** Only reads declared field values; never invokes a goal method or a predicate. */
    private static String linkedRawFields(Object object, String... names) {
        StringBuilder result = new StringBuilder("{");
        for (String name : names) {
            if (result.length() > 1) result.append(',');
            result.append(name).append('=');
            Class<?> type = object.getClass();
            while (type != null) {
                try {
                    var field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    result.append(field.get(object));
                    break;
                } catch (NoSuchFieldException absentHere) {
                    type = type.getSuperclass();
                } catch (ReflectiveOperationException | RuntimeException unavailable) {
                    result.append("unreadable:").append(unavailable.getClass().getSimpleName());
                    break;
                }
            }
            if (type == null) result.append("missing");
        }
        return result.append('}').toString();
    }

    private static List<Building> buildings() { return List.of(DreamseekersMod.DREAM_ANCHOR, DreamseekersMod.REVERIE_GARDEN, DreamseekersMod.BEETLE_SANCTUARY); }
    private static List<net.minecraft.world.entity.LivingEntity> owned(String owner) {
        return UnitServerEvents.getAllUnits().stream().filter(e -> e instanceof Unit u && owner.equals(u.getOwnerName())).toList();
    }

    private static final class Test {
        final GameTestHelper helper;
        final ServerLevel level;
        int assertions;
        Test(GameTestHelper helper) { this.helper = helper; level = helper.getLevel(); }
        void check(boolean condition, String message) { assertions++; helper.assertTrue(condition, message); }
        void equal(int actual, int expected, String message) { check(actual == expected, message + ": expected=" + expected + " actual=" + actual); }
        void balance(Resources r, int food, int wood, int ore, String message) {
            check(r.food == food && r.wood == wood && r.ore == ore, message + ": actual=" + r.food + "/" + r.wood + "/" + r.ore);
        }
        void pass(String name) { ReignOfNether.LOGGER.info("DREAMSEEKERS_GAMETEST_PASS {} assertions={}", name, assertions); helper.succeed(); }
        BlockPos pos(int x, int y, int z) { return helper.absolutePos(new BlockPos(x, y, z)); }
        BlockPos flat(int x, int z, int width, int depth) {
            BlockPos origin = pos(x, 1, z);
            for (int dx = -2; dx < width; dx++) for (int dz = -2; dz < depth; dz++) {
                level.setBlock(origin.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 2);
                for (int dy = 1; dy < 12; dy++) level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
            return origin;
        }
        Resources account(String suffix, Faction faction, int amount) {
            String owner = PREFIX + suffix;
            check(PlayerServerEvents.getRTSPlayer(owner) == null, "test owner is unique");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner, faction));
            Resources r = new Resources(owner, amount, amount, amount);
            ResourcesServerEvents.resourcesList.add(r);
            return r;
        }
        BuildingPlacement install(Building building, String owner, int x, int z) {
            BlockPos origin = flat(x, z, 25, 25);
            for (BuildingBlock block : BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(level), level, origin, Rotation.NONE))
                level.setBlock(block.getBlockPos(), block.getBlockState(), 2);
            BuildingPlacement p = BuildingUtils.getNewBuildingPlacement(building, level, origin, Rotation.NONE, owner, false);
            BuildingServerEvents.getBuildings().add(p);
            p.onBuilt(); // Fixture: complete geometry is already present; worker tests never use this shortcut.
            return p;
        }
        BuildingPlacement place(Building building, BlockPos pos, String owner) {
            return BuildingServerEvents.placeBuilding(building, pos, Rotation.NONE, owner, new int[0], false, false, false, true);
        }
        <T extends Mob> T spawn(EntityType<T> type, String owner, int x, int z) {
            T entity = type.create(level);
            check(entity instanceof Unit, "spawned entity is a real RTS unit");
            ((Unit) entity).setOwnerName(owner);
            BlockPos p = pos(x, 2, z);
            entity.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
            check(level.addFreshEntity(entity), "world accepts real entity");
            return entity;
        }
        void order(String owner, UnitAction action, Entity entity, int targetId, BlockPos target, BlockPos building) {
            UnitServerEvents.addActionItem(owner, action, targetId, new int[]{entity.getId()}, target, building);
        }
    }
}
