package com.dreamseekers;

import com.dreamseekers.building.*;
import com.dreamseekers.production.DreamUnitProduction;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.keybinds.Keybindings;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** Independent original PasterDream adapters; no PasterDream code or assets are bundled. */
@Mod(DreamseekersMod.MOD_ID)
public final class DreamseekersMod {
    public static final String MOD_ID = "dreamseekers";
    public static final ResourceLocation FACTION_ID = id("dreamseekers");
    public static final ResourceLocation FACTION_ICON = paster("textures/item/xun_meng_zhe_bi_ji_.png");
    public static final ResourceLocation WORKER_ICON = id("textures/icons/firefly_worker.png");
    public static final ResourceLocation LEGACY_WORKER_ICON = paster("textures/item/dream_meter.png");
    public static final ResourceLocation BEETLE_ICON = paster("textures/item/hou_chi_jia_ke_.png");
    public static final ResourceLocation PINK_CHICKEN_ICON =ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/pink_chicken_scout.png");
    public static final ResourceLocation GOLDEN_FOX_ICON =ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/golden_fox_unit.png");
    public static final ResourceLocation PINK_SLIME_ICON =ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/pink_slime_unit.png");
    public static final ResourceLocation JELLYFISH_ICON =ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/jellyfish_unit.png");
    public static final ResourceLocation BONE_WING_ICON =ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/bone_wing_unit.png");
    public static final ResourceLocation SHADOW_GHOST_ICON =ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/shadow_ghost_unit.png");
    public static final ResourceLocation SHADOW_SQUEAL_GHOST_ICON =ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/shadow_squeal_ghost_unit.png");
    public static final ResourceLocation ASH_BONE_WING_ICON=ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/ash_bone_wing_unit.png");
    public static final ResourceLocation SHADOW_HAND_ICON=ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/shadow_hand_unit.png");
    public static final ResourceLocation FRIENDLY_GHOST_ICON=ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/friendly_ghost_unit.png");
    public static final ResourceLocation CRAZY_TERRORBEAK_ICON=ResourceLocation.fromNamespaceAndPath("minecraft","textures/item/netherite_scrap.png");
    public static final ResourceLocation WEEPING_SQUEAL_GHOST_ICON=ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/weeping_squeal_ghost_unit.png");
    public static final ResourceLocation AARONCOS_LEFT_HAND_ICON=ResourceLocation.fromNamespaceAndPath("dreamseekers","textures/icons/shadow_hand_unit.png");
    public static final ResourceLocation AARONCOS_RIGHT_HAND_ICON=ResourceLocation.fromNamespaceAndPath("dreamseekers","textures/icons/skill_shadow_hand_grip.png");
    public static final ResourceLocation SHADOW_GOLEM_ICON=ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/shadow_golem_unit.png");
    public static final ResourceLocation TERRORBEAK_ICON=ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/terrorbeak_unit.png");
    public static final ResourceLocation BEETLE_MOTHER_ICON =ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/beetle_mother_unit.png");
    public static final ResourceLocation STONE_SPIRIT_THROWER_ICON =ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/stone_spirit_thrower.png");
    public static final ResourceLocation WIND_KNIGHT_ICON =ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/wind_knight_unit.png");
    public static final ResourceLocation SNAIL_ICON = paster("textures/item/xuan_wu_gua_niu_ke_.png");
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final RegistryObject<net.minecraft.world.level.block.Block> MELTDREAM_CROWN_CRYSTAL = BLOCKS.register("meltdream_crown_crystal",com.dreamseekers.worldtree.MeltdreamCrownBlock::new);
    public static final RegistryObject<EntityType<FireflyWorker>> FIREFLY_WORKER = ENTITIES.register("firefly_worker", () ->
            EntityType.Builder.<FireflyWorker>of(FireflyWorker::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).build(id("firefly_worker").toString()));
    public static final RegistryObject<EntityType<PinkChickenScout>> PINK_CHICKEN_SCOUT = ENTITIES.register("pink_chicken_scout", () ->
            EntityType.Builder.<PinkChickenScout>of(PinkChickenScout::new, MobCategory.MISC)
                    .sized(0.4F, 0.7F).clientTrackingRange(10).build(id("pink_chicken_scout").toString()));
    public static final RegistryObject<EntityType<GoldenFoxUnit>> GOLDEN_FOX_UNIT = ENTITIES.register("golden_fox_unit", () ->
            EntityType.Builder.<GoldenFoxUnit>of(GoldenFoxUnit::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).clientTrackingRange(10).build(id("golden_fox_unit").toString()));
    public static final RegistryObject<EntityType<PinkSlimeUnit>> PINK_SLIME_UNIT = ENTITIES.register("pink_slime_unit", () ->
            EntityType.Builder.<PinkSlimeUnit>of(PinkSlimeUnit::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).build(id("pink_slime_unit").toString()));
    public static final RegistryObject<EntityType<JellyfishUnit>> JELLYFISH_UNIT = ENTITIES.register("jellyfish_unit", () ->
            EntityType.Builder.<JellyfishUnit>of(JellyfishUnit::new, MobCategory.MISC)
                    .sized(0.7F, 1.4F).clientTrackingRange(10).build(id("jellyfish_unit").toString()));
    public static final RegistryObject<EntityType<BoneWingUnit>> BONE_WING_UNIT = ENTITIES.register("bone_wing_unit", () ->
            EntityType.Builder.<BoneWingUnit>of(BoneWingUnit::new, MobCategory.MISC)
                    .sized(1.7F,.8F).clientTrackingRange(10).build(id("bone_wing_unit").toString()));
    public static final RegistryObject<EntityType<com.dreamseekers.bonewing.BoneWingBolt>> BONE_WING_BOLT = ENTITIES.register("bone_wing_bolt", () ->
            EntityType.Builder.<com.dreamseekers.bonewing.BoneWingBolt>of(com.dreamseekers.bonewing.BoneWingBolt::new,MobCategory.MISC)
                    .sized(.5F,.5F).clientTrackingRange(10).updateInterval(1).build(id("bone_wing_bolt").toString()));
    public static final RegistryObject<EntityType<WindKnightUnit>> WIND_KNIGHT_UNIT = ENTITIES.register("wind_knight_unit", () ->
            EntityType.Builder.<WindKnightUnit>of(WindKnightUnit::new, MobCategory.MISC)
                    .sized(1.6F,3.2F).fireImmune().clientTrackingRange(10).build(id("wind_knight_unit").toString()));
    public static final RegistryObject<EntityType<StoneSpiritThrowerUnit>> STONE_SPIRIT_THROWER_UNIT = ENTITIES.register("stone_spirit_thrower", () ->
            EntityType.Builder.<StoneSpiritThrowerUnit>of(StoneSpiritThrowerUnit::new,MobCategory.MISC)
                    .sized(.7F,1F).clientTrackingRange(10).build(id("stone_spirit_thrower").toString()));
    public static final RegistryObject<EntityType<com.dreamseekers.stone.StoneProjectile>> STONE_SPIRIT_THROWER_BOLT = ENTITIES.register("stone_projectile", () ->
            EntityType.Builder.<com.dreamseekers.stone.StoneProjectile>of(com.dreamseekers.stone.StoneProjectile::new,MobCategory.MISC)
                    .sized(.25F,.25F).clientTrackingRange(10).updateInterval(1).build(id("stone_projectile").toString()));
    public static final RegistryObject<EntityType<com.dreamseekers.stone.HeavyStoneProjectile>> STONE_HEAVY_PROJECTILE = ENTITIES.register("stone_heavy_projectile", () ->
            EntityType.Builder.<com.dreamseekers.stone.HeavyStoneProjectile>of(com.dreamseekers.stone.HeavyStoneProjectile::new,MobCategory.MISC)
                    .sized(.25F,.25F).clientTrackingRange(10).updateInterval(1).build(id("stone_heavy_projectile").toString()));
    public static final RegistryObject<EntityType<CrazyTerrorbeakUnit>> CRAZY_TERRORBEAK_UNIT=ENTITIES.register("crazy_terrorbeak_unit",()->
            EntityType.Builder.<CrazyTerrorbeakUnit>of(CrazyTerrorbeakUnit::new,MobCategory.MISC).sized(1.8F,4F).fireImmune().clientTrackingRange(10).build(id("crazy_terrorbeak_unit").toString()));
    public static final RegistryObject<EntityType<WeepingSquealGhostUnit>> WEEPING_SQUEAL_GHOST_UNIT=ENTITIES.register("weeping_squeal_ghost_unit",()->
            EntityType.Builder.<WeepingSquealGhostUnit>of(WeepingSquealGhostUnit::new,MobCategory.MISC).sized(.8f,1.3f).fireImmune().clientTrackingRange(10).build(id("weeping_squeal_ghost_unit").toString()));
    public static final RegistryObject<EntityType<com.dreamseekers.shadow.WeepingSquealWaveBolt>> WEEPING_SQUEAL_WAVE_BOLT=ENTITIES.register("weeping_squeal_wave_bolt",()->
            EntityType.Builder.<com.dreamseekers.shadow.WeepingSquealWaveBolt>of(com.dreamseekers.shadow.WeepingSquealWaveBolt::new,MobCategory.MISC).sized(.5f,.5f).clientTrackingRange(8).updateInterval(1).build(id("weeping_squeal_wave_bolt").toString()));
    public static final RegistryObject<EntityType<AaroncosLeftHandUnit>> AARONCOS_LEFT_HAND_UNIT=ENTITIES.register("aaroncos_left_hand_unit",()->
            EntityType.Builder.<AaroncosLeftHandUnit>of(AaroncosLeftHandUnit::new,MobCategory.MISC).sized(3.5f,3.5f).fireImmune().clientTrackingRange(12).build(id("aaroncos_left_hand_unit").toString()));
    public static final RegistryObject<EntityType<AaroncosRightHandUnit>> AARONCOS_RIGHT_HAND_UNIT=ENTITIES.register("aaroncos_right_hand_unit",()->
            EntityType.Builder.<AaroncosRightHandUnit>of(AaroncosRightHandUnit::new,MobCategory.MISC).sized(3.5f,3.5f).fireImmune().clientTrackingRange(12).build(id("aaroncos_right_hand_unit").toString()));
    public static final RegistryObject<EntityType<ShadowGolemUnit>> SHADOW_GOLEM_UNIT=ENTITIES.register("shadow_golem_unit",()->
            EntityType.Builder.<ShadowGolemUnit>of(ShadowGolemUnit::new,MobCategory.MISC).sized(2.2F,3.5F).clientTrackingRange(10).build(id("shadow_golem_unit").toString()));
    public static final RegistryObject<EntityType<TerrorbeakUnit>> TERRORBEAK_UNIT=ENTITIES.register("terrorbeak_unit",()->
            EntityType.Builder.<TerrorbeakUnit>of(TerrorbeakUnit::new,MobCategory.MISC).sized(1.8F,4F).fireImmune().clientTrackingRange(10).build(id("terrorbeak_unit").toString()));
    public static final RegistryObject<EntityType<BeetleMotherUnit>> BEETLE_MOTHER_UNIT = ENTITIES.register("beetle_mother_unit", () ->
            EntityType.Builder.<BeetleMotherUnit>of(BeetleMotherUnit::new,MobCategory.MISC)
                    .sized(2F,1F).clientTrackingRange(10).build(id("beetle_mother_unit").toString()));
    public static final RegistryObject<EntityType<ShadowGhostUnit>> SHADOW_GHOST_UNIT=ENTITIES.register("shadow_ghost_unit",()->
            EntityType.Builder.<ShadowGhostUnit>of(ShadowGhostUnit::new,MobCategory.MISC).fireImmune()
                    .sized(.7F,1.2F).clientTrackingRange(10).build(id("shadow_ghost_unit").toString()));
    public static final RegistryObject<EntityType<ShadowSquealGhostUnit>> SHADOW_SQUEAL_GHOST_UNIT=ENTITIES.register("shadow_squeal_ghost_unit",()->
            EntityType.Builder.<ShadowSquealGhostUnit>of(ShadowSquealGhostUnit::new,MobCategory.MISC).sized(.7f,1.2f).fireImmune().clientTrackingRange(10).build(id("shadow_squeal_ghost_unit").toString()));
    public static final RegistryObject<EntityType<com.dreamseekers.shadow.SuppressionWaveProjectile>> SUPPRESSION_WAVE_PROJECTILE=ENTITIES.register("suppression_wave_projectile",()->
            EntityType.Builder.<com.dreamseekers.shadow.SuppressionWaveProjectile>of(com.dreamseekers.shadow.SuppressionWaveProjectile::new,MobCategory.MISC)
                    .sized(.5f,.5f).clientTrackingRange(8).updateInterval(1).build(id("suppression_wave_projectile").toString()));
    public static final RegistryObject<EntityType<com.dreamseekers.shadow.SquealWaveBolt>> SQUEAL_WAVE_BOLT=ENTITIES.register("squeal_wave_bolt",()->
            EntityType.Builder.<com.dreamseekers.shadow.SquealWaveBolt>of(com.dreamseekers.shadow.SquealWaveBolt::new,MobCategory.MISC).sized(.5f,.5f).clientTrackingRange(8).updateInterval(1).build(id("squeal_wave_bolt").toString()));

    public static final RegistryObject<EntityType<FriendlyGhostUnit>> FRIENDLY_GHOST_UNIT=ENTITIES.register("friendly_ghost_unit",()->
            EntityType.Builder.<FriendlyGhostUnit>of(FriendlyGhostUnit::new,MobCategory.MISC).sized(.7f,1.2f).fireImmune().clientTrackingRange(10).build(id("friendly_ghost_unit").toString()));
    public static final RegistryObject<EntityType<com.dreamseekers.shadow.FriendlyGhostBolt>> FRIENDLY_GHOST_BOLT=ENTITIES.register("friendly_ghost_bolt",()->
            EntityType.Builder.<com.dreamseekers.shadow.FriendlyGhostBolt>of(com.dreamseekers.shadow.FriendlyGhostBolt::new,MobCategory.MISC).sized(.5f,.5f).clientTrackingRange(8).updateInterval(1).build(id("friendly_ghost_bolt").toString()));

    public static final RegistryObject<EntityType<com.dreamseekers.shadow.EmberLandingProjectile>> EMBER_LANDING_PROJECTILE=ENTITIES.register("ember_landing_projectile",()->
            EntityType.Builder.<com.dreamseekers.shadow.EmberLandingProjectile>of(com.dreamseekers.shadow.EmberLandingProjectile::new,MobCategory.MISC)
                    .sized(.5f,.5f).clientTrackingRange(8).updateInterval(1).build(id("ember_landing_projectile").toString()));
    public static final RegistryObject<EntityType<AshBoneWingUnit>> ASH_BONE_WING_UNIT=ENTITIES.register("ash_bone_wing_unit",()->
            EntityType.Builder.<AshBoneWingUnit>of(AshBoneWingUnit::new,MobCategory.MISC).sized(com.dreamseekers.shadow.AshBoneWingBalance.WIDTH,com.dreamseekers.shadow.AshBoneWingBalance.HEIGHT).clientTrackingRange(10).build(id("ash_bone_wing_unit").toString()));
    public static final RegistryObject<EntityType<com.dreamseekers.shadow.AshBoneWingBolt>> ASH_BONE_WING_BOLT=ENTITIES.register("ash_bone_wing_bolt",()->
            EntityType.Builder.<com.dreamseekers.shadow.AshBoneWingBolt>of(com.dreamseekers.shadow.AshBoneWingBolt::new,MobCategory.MISC).sized(.5f,.5f).clientTrackingRange(8).updateInterval(1).build(id("ash_bone_wing_bolt").toString()));
    public static final RegistryObject<EntityType<ShadowHandUnit>> SHADOW_HAND_UNIT=ENTITIES.register("shadow_hand_unit",()->
            EntityType.Builder.<ShadowHandUnit>of(ShadowHandUnit::new,MobCategory.MISC).sized(com.dreamseekers.shadow.ShadowHandBalance.WIDTH,com.dreamseekers.shadow.ShadowHandBalance.HEIGHT).fireImmune().clientTrackingRange(10).build(id("shadow_hand_unit").toString()));

    /** Skill carrier only: intentionally absent from faction units, production, eggs and natural spawning. */
    public static final RegistryObject<EntityType<com.dreamseekers.spore.JungleSporeCarrier>> JUNGLE_SPORE_CARRIER = ENTITIES.register("jungle_spore_carrier", () ->
            EntityType.Builder.<com.dreamseekers.spore.JungleSporeCarrier>of(com.dreamseekers.spore.JungleSporeCarrier::new,MobCategory.MISC)
                    .sized(.2F,.2F).clientTrackingRange(8).build(id("jungle_spore_carrier").toString()));
    /** Kept for existing entity NBT; no silent world migration or deletion. */
    public static final RegistryObject<EntityType<StoneSpiritWorker>> STONE_SPIRIT_WORKER = ENTITIES.register("stone_spirit_worker", () ->
            EntityType.Builder.<StoneSpiritWorker>of(StoneSpiritWorker::new, MobCategory.MISC)
                    .sized(0.6F, 0.8F).clientTrackingRange(10).build(id("stone_spirit_worker").toString()));
    public static final RegistryObject<EntityType<BeetleUnit>> BEETLE = ENTITIES.register("beetle", () ->
            EntityType.Builder.<BeetleUnit>of(BeetleUnit::new, MobCategory.MISC)
                    .sized(0.9F, 0.65F).clientTrackingRange(10).build(id("beetle").toString()));
    public static final RegistryObject<EntityType<BasaltSnailUnit>> BASALT_SNAIL = ENTITIES.register("basalt_snail", () ->
            EntityType.Builder.<BasaltSnailUnit>of(BasaltSnailUnit::new, MobCategory.MISC)
                    .sized(1.1F, 1.1F).clientTrackingRange(10).build(id("basalt_snail").toString()));

    public static Faction DREAMSEEKERS;
    public static DreamUnitProduction AARONCOS_LEFT_HAND_PRODUCTION, AARONCOS_RIGHT_HAND_PRODUCTION;
    public static DreamUnitProduction WORKER_PRODUCTION;
    public static DreamUnitProduction LEGACY_WORKER_PRODUCTION;
    public static DreamUnitProduction BEETLE_PRODUCTION;
    public static DreamUnitProduction SNAIL_PRODUCTION;
    public static DreamUnitProduction PINK_CHICKEN_PRODUCTION;
    public static DreamUnitProduction GOLDEN_FOX_PRODUCTION;
    public static DreamUnitProduction PINK_SLIME_PRODUCTION;
    public static DreamUnitProduction JELLYFISH_PRODUCTION;
    public static DreamUnitProduction BONE_WING_PRODUCTION;
    public static DreamUnitProduction WIND_KNIGHT_PRODUCTION;
    public static DreamUnitProduction STONE_SPIRIT_THROWER_PRODUCTION;
    public static DreamUnitProduction SHADOW_GOLEM_PRODUCTION;
    public static DreamUnitProduction WEEPING_SQUEAL_GHOST_PRODUCTION;
    public static DreamUnitProduction ASH_BONE_WING_PRODUCTION;
    public static DreamUnitProduction SHADOW_HAND_PRODUCTION;
    public static DreamUnitProduction FRIENDLY_GHOST_PRODUCTION;
    public static DreamUnitProduction TERRORBEAK_PRODUCTION;
    public static DreamUnitProduction BEETLE_MOTHER_PRODUCTION;
    public static DreamUnitProduction SHADOW_GHOST_PRODUCTION;
    public static DreamUnitProduction SHADOW_SQUEAL_GHOST_PRODUCTION;
    public static DreamAnchor DREAM_ANCHOR;
    public static ReverieGarden REVERIE_GARDEN;
    public static BeetleSanctuary BEETLE_SANCTUARY;
    public static DreamScaffoldBuilding DREAM_DEPOT;
    public static DreamScaffoldBuilding SHADOW_MINE;
    public static DreamScaffoldBuilding SHADOW_FURNACE;
    public static DreamHall DREAM_HALL;
    public static DreamLaboratory RESONANCE_LABORATORY;
    public static DreamScaffoldBuilding CONTRACT_ECOLOGY;
    public static DreamScaffoldBuilding RESONANCE_DAIS;
    public static DreamScaffoldBuilding SHADOW_WATCHTOWER;
    /** Registered shared building implementations, in stable faction-menu order. */
    public static List<DreamScaffoldBuilding> SCAFFOLD_BUILDINGS = List.of();
    /** All eight original extension identities, in their unchanged worker-menu order. */
    public static List<com.solegendary.reignofnether.building.Building> FUNCTIONAL_BUILDINGS = List.of();

    public DreamseekersMod(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ENTITIES.register(bus);
        BLOCKS.register(bus);
        com.dreamseekers.economy.DreamConstructionPayment.bootstrap();
        com.dreamseekers.economy.DreamPassiveEconomy.bootstrap(bus);
        com.dreamseekers.economy.DreamSandboxEconomy.bootstrap();
        com.dreamseekers.worldtree.DreamWorldTreeService.bootstrap();
        com.dreamseekers.buildingability.BuildingAbilityState.bootstrap();
        com.dreamseekers.scout.ScoutMarkNetwork.init();
        com.dreamseekers.skill.SkillNetwork.init();
        com.solegendary.reignofnether.api.FinalUnitDamageHooks.register(com.dreamseekers.skill.SkillEffects.HANDLER);
        com.solegendary.reignofnether.api.FinalUnitDamageHooks.register(com.dreamseekers.summon.BoundedSummons.DAMAGE_HANDLER);
        com.solegendary.reignofnether.api.PrimaryUnitHitHooks.register(com.dreamseekers.skill.OrdinarySkillHit.PROJECTILE_PROVIDER);
        bus.addListener(DreamseekersMod::attributes);
        // RoN registers its factions in common-setup enqueued work. Register after that,
        // not from concurrently dispatched common-setup listeners.
        bus.addListener(DreamseekersMod::loadComplete);
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
    public static ResourceLocation paster(String path) { return ResourceLocation.fromNamespaceAndPath("pasterdream", path); }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(FIREFLY_WORKER.get(), FireflyWorker.createAttributes().build());
        event.put(PINK_CHICKEN_SCOUT.get(), PinkChickenScout.createAttributes().build());
        event.put(GOLDEN_FOX_UNIT.get(), GoldenFoxUnit.createAttributes().build());
        event.put(PINK_SLIME_UNIT.get(), PinkSlimeUnit.createAttributes().build());
        event.put(JELLYFISH_UNIT.get(), JellyfishUnit.createAttributes().build());
        event.put(BONE_WING_UNIT.get(), BoneWingUnit.createAttributes().build());
        event.put(SHADOW_GHOST_UNIT.get(), ShadowGhostUnit.createAttributes().build());
        event.put(SHADOW_SQUEAL_GHOST_UNIT.get(), ShadowSquealGhostUnit.createAttributes().build());
        event.put(ASH_BONE_WING_UNIT.get(),AshBoneWingUnit.createAttributes().build());
        event.put(SHADOW_HAND_UNIT.get(),ShadowHandUnit.createAttributes().build());
        event.put(FRIENDLY_GHOST_UNIT.get(),FriendlyGhostUnit.createAttributes().build());
        event.put(CRAZY_TERRORBEAK_UNIT.get(),CrazyTerrorbeakUnit.createAttributes().build());
        event.put(SHADOW_GOLEM_UNIT.get(),ShadowGolemUnit.createAttributes().build());
        event.put(AARONCOS_RIGHT_HAND_UNIT.get(),AaroncosRightHandUnit.createAttributes().build());
        event.put(AARONCOS_LEFT_HAND_UNIT.get(),AaroncosLeftHandUnit.createAttributes().build());
        event.put(WEEPING_SQUEAL_GHOST_UNIT.get(),WeepingSquealGhostUnit.createAttributes().build());
        event.put(TERRORBEAK_UNIT.get(),TerrorbeakUnit.createAttributes().build());
        event.put(BEETLE_MOTHER_UNIT.get(), BeetleMotherUnit.createAttributes().build());
        event.put(STONE_SPIRIT_THROWER_UNIT.get(), StoneSpiritThrowerUnit.createAttributes().build());
        event.put(WIND_KNIGHT_UNIT.get(), WindKnightUnit.createAttributes().build());
        event.put(JUNGLE_SPORE_CARRIER.get(), net.pasterdream.entity.SporeEntityEntity.createAttributes().build());
        event.put(STONE_SPIRIT_WORKER.get(), StoneSpiritWorker.createAttributes().build());
        event.put(BEETLE.get(), BeetleUnit.createAttributes().build());
        event.put(BASALT_SNAIL.get(), BasaltSnailUnit.createAttributes().build());
    }

    private static void loadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            verifyOriginalDependencyProfile();
            if (Factions.VILLAGERS == null) throw new IllegalStateException("Reign of Nether setup did not finish before Dreamseekers");
            DREAMSEEKERS = Factions.register(FACTION_ID, new DreamseekersFaction().setKey(FACTION_ID)
                    .setIcon(FACTION_ICON).setWorkerIcon(WORKER_ICON).setSound(SoundEvents.MUSIC_GAME.value())
                    .setCustomBuildingCondition(custom -> false).noCubeMap());
            Factions.PLAYABLE_FACTIONS.add(FACTION_ID);
            // Intentionally not CLASSIC_FACTIONS: RoN also uses it for survival waves
            // and title panorama assets. This faction is registered for RTS matches.
            WORKER_PRODUCTION = registerProduction("firefly_worker", new DreamUnitProduction(FIREFLY_WORKER, FireflyWorker.COST, WORKER_ICON));
            // Preserve the historical registry identity without offering new stone-worker orders.
            // RoN refunds pending production on shutdown; this does not promise queue migration.
            // It is deliberately absent from the new production and creative menus.
            LEGACY_WORKER_PRODUCTION = registerProduction("stone_spirit_worker", new DreamUnitProduction(STONE_SPIRIT_WORKER, StoneSpiritWorker.COST, LEGACY_WORKER_ICON));
            BEETLE_PRODUCTION = registerProduction("beetle", new DreamUnitProduction(BEETLE, BeetleUnit.COST, BEETLE_ICON));
            SNAIL_PRODUCTION = registerProduction("basalt_snail", new DreamUnitProduction(BASALT_SNAIL, BasaltSnailUnit.COST, SNAIL_ICON));
            PINK_CHICKEN_PRODUCTION = registerProduction("pink_chicken_scout", new DreamUnitProduction(PINK_CHICKEN_SCOUT, PinkChickenScout.COST, PINK_CHICKEN_ICON,
                    () -> BEETLE_SANCTUARY, () -> CONTRACT_ECOLOGY, "dreamseekers.error.requires_contract_ecology"));
            GOLDEN_FOX_PRODUCTION = registerProduction("golden_fox_unit", new DreamUnitProduction(GOLDEN_FOX_UNIT, GoldenFoxUnit.COST, GOLDEN_FOX_ICON,
                    () -> BEETLE_SANCTUARY, () -> CONTRACT_ECOLOGY, "dreamseekers.error.requires_contract_ecology"));
            PINK_SLIME_PRODUCTION = registerProduction("pink_slime_unit", new DreamUnitProduction(PINK_SLIME_UNIT, PinkSlimeUnit.COST, PINK_SLIME_ICON,
                    () -> BEETLE_SANCTUARY, () -> CONTRACT_ECOLOGY, "dreamseekers.error.requires_contract_ecology"));
            JELLYFISH_PRODUCTION = registerProduction("jellyfish_unit", new DreamUnitProduction(JELLYFISH_UNIT, JellyfishUnit.COST, JELLYFISH_ICON,
                    () -> BEETLE_SANCTUARY, () -> CONTRACT_ECOLOGY, "dreamseekers.error.requires_contract_ecology"));
            BONE_WING_PRODUCTION = registerProduction("bone_wing_unit", new DreamUnitProduction(BONE_WING_UNIT, BoneWingUnit.COST, BONE_WING_ICON,
                    () -> DREAM_HALL, null, null));
            Factions.registerEntity(DREAMSEEKERS,BONE_WING_UNIT.get(),BONE_WING_PRODUCTION);
            WIND_KNIGHT_PRODUCTION = registerProduction("wind_knight_unit", new DreamUnitProduction(WIND_KNIGHT_UNIT, WindKnightUnit.COST, WIND_KNIGHT_ICON,
                    () -> DREAM_HALL, null, null));
            Factions.registerEntity(DREAMSEEKERS,WIND_KNIGHT_UNIT.get(),WIND_KNIGHT_PRODUCTION);
            STONE_SPIRIT_THROWER_PRODUCTION = registerProduction("stone_spirit_thrower", new DreamUnitProduction(STONE_SPIRIT_THROWER_UNIT, StoneSpiritThrowerUnit.COST, STONE_SPIRIT_THROWER_ICON,
                    () -> DREAM_HALL, null, null));
            Factions.registerEntity(DREAMSEEKERS,STONE_SPIRIT_THROWER_UNIT.get(),STONE_SPIRIT_THROWER_PRODUCTION);
            WEEPING_SQUEAL_GHOST_PRODUCTION=registerProduction("weeping_squeal_ghost_unit",new DreamUnitProduction(WEEPING_SQUEAL_GHOST_UNIT,WeepingSquealGhostUnit.COST,WEEPING_SQUEAL_GHOST_ICON,()->DREAM_HALL,null,null));
            Factions.registerEntity(DREAMSEEKERS,WEEPING_SQUEAL_GHOST_UNIT.get(),WEEPING_SQUEAL_GHOST_PRODUCTION);
            SHADOW_GOLEM_PRODUCTION=registerProduction("shadow_golem_unit",new DreamUnitProduction(SHADOW_GOLEM_UNIT,ShadowGolemUnit.COST,SHADOW_GOLEM_ICON,()->DREAM_HALL,null,null));
            Factions.registerEntity(DREAMSEEKERS,SHADOW_GOLEM_UNIT.get(),SHADOW_GOLEM_PRODUCTION);
            ASH_BONE_WING_PRODUCTION=registerProduction("ash_bone_wing_unit",new DreamUnitProduction(ASH_BONE_WING_UNIT,AshBoneWingUnit.COST,ASH_BONE_WING_ICON,()->DREAM_HALL,null,null));
            Factions.registerEntity(DREAMSEEKERS,ASH_BONE_WING_UNIT.get(),ASH_BONE_WING_PRODUCTION);
            SHADOW_HAND_PRODUCTION=registerProduction("shadow_hand_unit",new DreamUnitProduction(SHADOW_HAND_UNIT,ShadowHandUnit.COST,SHADOW_HAND_ICON,()->DREAM_HALL,null,null));
            Factions.registerEntity(DREAMSEEKERS,SHADOW_HAND_UNIT.get(),SHADOW_HAND_PRODUCTION);
            FRIENDLY_GHOST_PRODUCTION=registerProduction("friendly_ghost_unit",new DreamUnitProduction(FRIENDLY_GHOST_UNIT,FriendlyGhostUnit.COST,FRIENDLY_GHOST_ICON,()->DREAM_HALL,null,null));
            Factions.registerEntity(DREAMSEEKERS,FRIENDLY_GHOST_UNIT.get(),FRIENDLY_GHOST_PRODUCTION);
            TERRORBEAK_PRODUCTION=registerProduction("terrorbeak_unit",new DreamUnitProduction(TERRORBEAK_UNIT,TerrorbeakUnit.COST,TERRORBEAK_ICON,()->DREAM_HALL,null,null));
            Factions.registerEntity(DREAMSEEKERS,TERRORBEAK_UNIT.get(),TERRORBEAK_PRODUCTION);
            // Upgrade-branch foundation only: no placement button or normal/free production recipe.
            Factions.registerEntity(DREAMSEEKERS,CRAZY_TERRORBEAK_UNIT.get());
            AARONCOS_LEFT_HAND_PRODUCTION=registerProduction("aaroncos_left_hand_unit",new DreamUnitProduction(AARONCOS_LEFT_HAND_UNIT,AaroncosLeftHandUnit.COST,AARONCOS_LEFT_HAND_ICON,
                ()->DREAM_HALL,null,"dreamseekers.error.hands_prerequisite",com.dreamseekers.aaroncos.AaroncosTraining::missingRequirement,java.util.Map.of(com.dreamseekers.economy.DreamPassiveEconomy.SHADOW,100)));
            AARONCOS_RIGHT_HAND_PRODUCTION=registerProduction("aaroncos_right_hand_unit",new DreamUnitProduction(AARONCOS_RIGHT_HAND_UNIT,AaroncosRightHandUnit.COST,AARONCOS_RIGHT_HAND_ICON,
                ()->DREAM_HALL,null,"dreamseekers.error.hands_prerequisite",com.dreamseekers.aaroncos.AaroncosTraining::missingRequirement,java.util.Map.of(com.dreamseekers.economy.DreamPassiveEconomy.SHADOW,100)));
            Factions.registerEntity(DREAMSEEKERS,AARONCOS_LEFT_HAND_UNIT.get(),AARONCOS_LEFT_HAND_PRODUCTION);
            Factions.registerEntity(DREAMSEEKERS,AARONCOS_RIGHT_HAND_UNIT.get(),AARONCOS_RIGHT_HAND_PRODUCTION);
            BEETLE_MOTHER_PRODUCTION=registerProduction("beetle_mother_unit",new DreamUnitProduction(BEETLE_MOTHER_UNIT,BeetleMotherUnit.COST,BEETLE_MOTHER_ICON,()->DREAM_HALL,null,null));
            Factions.registerEntity(DREAMSEEKERS,BEETLE_MOTHER_UNIT.get(),BEETLE_MOTHER_PRODUCTION);
            SHADOW_GHOST_PRODUCTION=registerProduction("shadow_ghost_unit",new DreamUnitProduction(SHADOW_GHOST_UNIT,ShadowGhostUnit.COST,SHADOW_GHOST_ICON,()->DREAM_HALL,null,null));
            Factions.registerEntity(DREAMSEEKERS,SHADOW_GHOST_UNIT.get(),SHADOW_GHOST_PRODUCTION);
            SHADOW_SQUEAL_GHOST_PRODUCTION=registerProduction("shadow_squeal_ghost_unit",new DreamUnitProduction(SHADOW_SQUEAL_GHOST_UNIT,ShadowSquealGhostUnit.COST,SHADOW_SQUEAL_GHOST_ICON,()->DREAM_HALL,null,null));
            Factions.registerEntity(DREAMSEEKERS,SHADOW_SQUEAL_GHOST_UNIT.get(),SHADOW_SQUEAL_GHOST_PRODUCTION);
            Factions.registerEntity(DREAMSEEKERS, JELLYFISH_UNIT.get(), JELLYFISH_PRODUCTION);
            Factions.registerEntity(DREAMSEEKERS, PINK_SLIME_UNIT.get(), PINK_SLIME_PRODUCTION);
            Factions.registerEntity(DREAMSEEKERS, GOLDEN_FOX_UNIT.get(), GOLDEN_FOX_PRODUCTION);
            Factions.registerEntity(DREAMSEEKERS, PINK_CHICKEN_SCOUT.get(), PINK_CHICKEN_PRODUCTION);
            Factions.registerWorkerEntity(DREAMSEEKERS, FIREFLY_WORKER.get(), WORKER_PRODUCTION);
            Factions.registerEntity(DREAMSEEKERS, STONE_SPIRIT_WORKER.get());
            Factions.registerScoutEntity(DREAMSEEKERS, BEETLE.get(), BEETLE_PRODUCTION);
            Factions.registerEntity(DREAMSEEKERS, BASALT_SNAIL.get(), SNAIL_PRODUCTION);
            DREAM_ANCHOR = Registry.register(ReignOfNetherRegistries.BUILDING, id("dream_anchor"), new DreamAnchor(WORKER_PRODUCTION));
            com.dreamseekers.economy.DreamEconomyResearch.register(DREAM_ANCHOR);
            REVERIE_GARDEN = Registry.register(ReignOfNetherRegistries.BUILDING, id("reverie_garden"), new ReverieGarden());
            BEETLE_SANCTUARY = Registry.register(ReignOfNetherRegistries.BUILDING, id("beetle_sanctuary"), new BeetleSanctuary(BEETLE_PRODUCTION, SNAIL_PRODUCTION, PINK_CHICKEN_PRODUCTION, GOLDEN_FOX_PRODUCTION, PINK_SLIME_PRODUCTION, JELLYFISH_PRODUCTION));
            Factions.registerStartBuilding(DREAMSEEKERS, DREAM_ANCHOR, null); // Worker Q is Firefly Rush; construction remains mouse-accessible.
            Factions.registerBuildings(DREAMSEEKERS, REVERIE_GARDEN, null); // Worker W is Neutral Contract; do not arm a building too.
            Factions.registerBuildings(DREAMSEEKERS, BEETLE_SANCTUARY, Keybindings.abilitySlot3);
            DREAM_DEPOT = registerScaffold(DreamBuildingSpec.DREAM_DEPOT);
            SHADOW_MINE = registerScaffold(DreamBuildingSpec.SHADOW_MINE);
            SHADOW_FURNACE = registerScaffold(DreamBuildingSpec.SHADOW_FURNACE);
            DREAM_HALL = Registry.register(ReignOfNetherRegistries.BUILDING,id("dream_hall"),new DreamHall(BONE_WING_PRODUCTION,WIND_KNIGHT_PRODUCTION,STONE_SPIRIT_THROWER_PRODUCTION,BEETLE_MOTHER_PRODUCTION,SHADOW_GHOST_PRODUCTION,SHADOW_SQUEAL_GHOST_PRODUCTION,TERRORBEAK_PRODUCTION,FRIENDLY_GHOST_PRODUCTION,SHADOW_HAND_PRODUCTION,ASH_BONE_WING_PRODUCTION,SHADOW_GOLEM_PRODUCTION,WEEPING_SQUEAL_GHOST_PRODUCTION,AARONCOS_LEFT_HAND_PRODUCTION,AARONCOS_RIGHT_HAND_PRODUCTION));
            RESONANCE_LABORATORY = Registry.register(ReignOfNetherRegistries.BUILDING,id("resonance_laboratory"),new DreamLaboratory());
            com.dreamseekers.progression.DreamWindResearch.register(RESONANCE_LABORATORY);
            com.dreamseekers.unitprogression.DreamSpecialistResearch.register(RESONANCE_LABORATORY);
            com.dreamseekers.resonance.ResonanceResearch.register(RESONANCE_LABORATORY);
            com.dreamseekers.neutralcontract.ContractResearch.register(RESONANCE_LABORATORY);
            com.dreamseekers.resonance.LightResonanceResearch.register(RESONANCE_LABORATORY);
            com.dreamseekers.unitprogression.AdvancedUnitResearch.register(RESONANCE_LABORATORY);
            com.dreamseekers.unitprogression.ExpansionResearch.register(RESONANCE_LABORATORY);
            com.dreamseekers.progression.DreamWindPackets.register();
            com.dreamseekers.ecologyworkposts.EcologyPackets.register();
            com.dreamseekers.resonance.ResonancePackets.register();
            com.dreamseekers.buildingability.BuildingAbilityPackets.register();
            com.solegendary.reignofnether.api.UnitCommandResetHooks.register(com.dreamseekers.ecologyworkposts.EcologyWorkPosts::commandReset);
            CONTRACT_ECOLOGY = registerScaffold(DreamBuildingSpec.CONTRACT_ECOLOGY);
            RESONANCE_DAIS = registerScaffold(DreamBuildingSpec.RESONANCE_DAIS);
            SHADOW_WATCHTOWER = registerScaffold(DreamBuildingSpec.SHADOW_WATCHTOWER);
            SCAFFOLD_BUILDINGS = List.of(DREAM_DEPOT,SHADOW_MINE,SHADOW_FURNACE,CONTRACT_ECOLOGY,RESONANCE_DAIS,SHADOW_WATCHTOWER);
            FUNCTIONAL_BUILDINGS = List.of(DREAM_DEPOT, SHADOW_MINE, SHADOW_FURNACE, DREAM_HALL,
                    RESONANCE_LABORATORY, CONTRACT_ECOLOGY, RESONANCE_DAIS, SHADOW_WATCHTOWER);
            // RoN wraps the worker's building menu across rows. Leave these mouse
            // buttons unbound rather than stealing existing unit/action hotkeys.
            for (com.solegendary.reignofnether.building.Building building : FUNCTIONAL_BUILDINGS)
                Factions.registerBuildings(DREAMSEEKERS, building);
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient())
                com.dreamseekers.client.DreamSurvivalBeaconButton.register();
        });
    }

    private static DreamUnitProduction registerProduction(String path, DreamUnitProduction production) {
        return Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM, id(path), production);
    }

    private static DreamScaffoldBuilding registerScaffold(DreamBuildingSpec spec) {
        return Registry.register(ReignOfNetherRegistries.BUILDING, id(spec.structureName()),
                spec == DreamBuildingSpec.SHADOW_WATCHTOWER ? new DreamWatchtower() : new DreamScaffoldBuilding(spec));
    }

    /** The Reborn rewrite reuses this mod ID; do not silently treat it as the original. */
    private static void verifyOriginalDependencyProfile() {
        var info = ModList.get().getModContainerById("pasterdream").orElseThrow().getModInfo();
        String authors = info.getConfig().getConfigElement("authors").map(Object::toString).orElse("");
        if (!authors.contains("异星之尘") && !authors.contains("Aerolite"))
            throw new IllegalStateException("Dreamseekers requires the original PasterDream by Aerolite_Dust, not PasterDream: Reborn. See the installation guide.");
        for (String entity : new String[]{"firefly", "small_stone_spirit", "shadow_ghost", "black_beetle_mother", "black_beetle", "basalt_snail", "pink_chicken", "golden_fox", "pink_slime", "jellyfish", "bone_wing", "projectile_bone_wing_fire_ball_projectile",
                "wind_knight", "shadow_squeal_ghost", "shadow_squeal_ghost_0", "friendly_ghost", "ash_bone_wing", "shadow_hand",
                "terrorbeak", "crazy_terrorbeak", "shadow_golem", "aaroncos_lefthand_0", "aaroncos_righthand_0", "spore_entity"})
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(paster(entity)))
                throw new IllegalStateException("Unsupported PasterDream entity profile: missing " + entity);
        for (String block : new String[]{"smooth_dyedreamquartz_block", "chiseled_dyedreamquartz_block", "dyedream_planks"})
            if (!ForgeRegistries.BLOCKS.containsKey(paster(block)))
                throw new IllegalStateException("Unsupported PasterDream block profile: missing " + block);
    }
}
