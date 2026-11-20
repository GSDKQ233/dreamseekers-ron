package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.heroAbilities.necromancer.RaiseDead;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;

/** Additional old-API-compatible boundaries and positive controls; no new API65 helper used. */
@GameTestHolder("api65_units") @PrefixGameTestTemplate(false)
public final class Api65UnitLifecycleGameTests {
    private static final String BATCH="api65_unit_lifecycle";
    private static <T extends Mob> T publish(GameTestHelper h,T mob,String key) {
        ((Unit)mob).setOwnerName("Api65Lifecycle_"+key);mob.setNoAi(true);mob.setNoGravity(true);mob.setPersistenceRequired();
        var p=h.absolutePos(new BlockPos(5,2,5));mob.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);h.assertTrue(h.getLevel().addFreshEntity(mob),"real native publication");return mob;
    }
    private static void floor(GameTestHelper h) {
        var p=h.absolutePos(BlockPos.ZERO);for(int x=0;x<22;x++)for(int z=0;z<22;z++)for(int y=1;y<7;y++)h.getLevel().setBlock(p.offset(x,y,z),(y==1?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
    }
    private static void command(GameTestHelper h,Unit unit,UnitAction action,int target) {new UnitActionItem(unit.getOwnerName(),action,target,new int[]{((Entity)unit).getId()},((Entity)unit).blockPosition(),BlockPos.ZERO).action(h.getLevel());}
    private static Ability ability(Unit unit,UnitAction action){return unit.getAbilities().get().stream().filter(a->a.action==action).findFirst().orElseThrow();}
    private static void set(Object object,Class<?> owner,String name,Object value){try{var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
    private static boolean flag(Object object,Class<?> owner,String name){try{var f=owner.getDeclaredField(name);f.setAccessible(true);return f.getBoolean(object);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
    private static <T extends Mob> T reload(GameTestHelper h,T old,EntityType<T> type) {var tag=old.saveWithoutId(new CompoundTag());old.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);T restored=type.create(h.getLevel());restored.load(tag);h.assertTrue(h.getLevel().addFreshEntity(restored),"same UUID reload is published");return restored;}
    private static void ticks(Mob mob,int count) {
        // ServerLevel, not Entity.tick(), advances tickCount. Use the genuine native dispatcher:
        // otherwise tickCount remains zero and the eight-second monster heal runs every test tick.
        var level=(net.minecraft.server.level.ServerLevel)mob.level();
        for(int i=0;i<count&&!mob.isRemoved()&&!mob.isDeadOrDying();i++) {
            int ageBefore=mob.tickCount;
            level.tickNonPassenger(mob);
            if(mob.tickCount!=ageBefore+1)throw new IllegalStateException("native tick dispatcher must increment entity age exactly once");
        }
    }

    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void holdDoesNotRepathFollowingMilitiaGoal(GameTestHelper h){
        floor(h);var militia=publish(h,EntityRegistrar.MILITIA_UNIT.get().create(h.getLevel()),"Hold");var target=publish(h,EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel()),"HoldEnemy");
        try {
            target.moveTo(militia.getX()+8,militia.getY(),militia.getZ());
            // publish() deliberately disables gravity/AI and this test runs before a physics tick.
            // The flat floor is real, but GroundPathNavigation also requires the grounded flag.
            militia.setOnGround(true);target.setOnGround(true);
            militia.setUnitAttackTargetForced(target);
            h.assertTrue(militia.getTarget()==target&&militia.getAttackGoal() instanceof AbstractMeleeAttackUnitGoal,"real selected target and installed melee goal are active");
            militia.setHoldPosition(false);
            h.assertTrue(militia.getAttackGoal().canContinueToUse(),"positive control: native following goal continues for the live target");
            h.assertTrue(!militia.getNavigation().isDone(),"positive control: real following melee goal can path on the flat arena");
            militia.getNavigation().stop();militia.setHoldPosition(true);
            h.assertTrue(militia.getAttackGoal().canContinueToUse(),"HOLD keeps the existing live attack target");
            h.assertTrue(militia.getNavigation().isDone(),"HOLD never resumes chase navigation from canContinueToUse");
        }finally{militia.discard();target.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void disablingRtsPathfindingSettlesPendingOrder(GameTestHelper h){
        floor(h);var unit=publish(h,EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel()),"PathMode");boolean prior=UnitServerEvents.rtsPathfinding;
        try {UnitServerEvents.rtsPathfinding=true;var goal=unit.getMoveGoal();goal.setMoveTarget(unit.blockPosition().offset(12,-1,0));goal.start();h.assertTrue(flag(goal,MoveToTargetBlockGoal.class,"pathPending"),"a real RTS request is queued before mode change");UnitServerEvents.rtsPathfinding=false;goal.canContinueToUse();h.assertTrue(!flag(goal,MoveToTargetBlockGoal.class,"pathPending"),"disabled worker delivery cannot leave the active order permanently pending");h.assertTrue(goal.getMoveTarget()!=null,"same order retained for vanilla fallback");
        }finally{UnitServerEvents.rtsPathfinding=prior;unit.discard();}h.succeed();
    }
    private static String deathCounter(GameTestHelper h,String key){var server=h.getLevel().getServer();var source=server.createCommandSourceStack().withPermission(4);if(server.getScoreboard().getObjective("api65_units")==null)server.getCommands().performPrefixedCommand(source,"scoreboard objectives add api65_units dummy");server.getScoreboard().getOrCreatePlayerScore(key,server.getScoreboard().getObjective("api65_units")).setScore(0);return "scoreboard players add "+key+" api65_units 1";}
    private static int count(GameTestHelper h,String key){var board=h.getLevel().getServer().getScoreboard();return board.getOrCreatePlayerScore(key,board.getObjective("api65_units")).getScore();}
    /** A real seat has two owned bodies but no completed base yet. Normal unloaded/loaded body
     * lifetimes (including a sibling's permanent removal) never count as an unobserved death. */
    private static void unavailableBodyIsNotDefeat(GameTestHelper h) {
        var players = com.solegendary.reignofnether.player.PlayerServerEvents.rtsPlayers;
        var type = EntityRegistrar.VILLAGER_UNIT.get();
        var first = publish(h, type.create(h.getLevel()), "PendingReturn");
        var other = publish(h, type.create(h.getLevel()), "PendingReturn");
        String name = first.getOwnerName();
        var seat = com.solegendary.reignofnether.player.RTSPlayer.getNewBot(name, com.solegendary.reignofnether.faction.Factions.VILLAGERS);
        players.add(seat);
        var resources = com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList;
        var held = new com.solegendary.reignofnether.resources.Resources(name, 13, 17, 19);
        resources.add(held);
        var restored = type.create(h.getLevel());
        try {
            var saved = first.saveWithoutId(new CompoundTag());
            first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            other.discard();
            h.assertTrue(players.contains(seat) && resources.contains(held) && held.food == 13
                    && seat.unavailableUnitIds.contains(first.getUUID()), "the absent unit still protects this seat/account");
            restored.load(saved);
            h.assertTrue(h.getLevel().addFreshEntity(restored), "same UUID returns");
            restored.kill();
            for (int i = 0; i < 25 && !restored.isRemoved(); i++) h.getLevel().tickNonPassenger(restored);
            h.assertTrue(restored.isRemoved() && !players.contains(seat) && !resources.contains(held),
                    "actual last death with no base still defeats the owner");
        } finally {
            players.remove(seat); resources.remove(held);
            com.solegendary.reignofnether.player.PlayerServerEvents.postGameRtsPlayers.remove(seat);
            for (var e : List.of(first, other, restored)) if (!e.isRemoved()) e.discard();
        }
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void hoglinArmourConversionIsNotADeathCommand(GameTestHelper h){
        var hoglin=publish(h,EntityRegistrar.HOGLIN_UNIT.get().create(h.getLevel()),"ConversionCommand");String owner=hoglin.getOwnerName();
        try {hoglin.setOnDeathCommand(deathCounter(h,"conversion"));hoglin.onPickupEquipment(new ItemStack(Items.NETHERITE_CHESTPLATE));h.assertTrue(hoglin.isRemoved(),"real native armour conversion retires original");h.assertTrue(count(h,"conversion")==0,"successful living conversion is not death");
        }finally{new ArrayList<>(UnitServerEvents.getAllUnits()).stream().filter(e->e instanceof Unit u&&u.getOwnerName().equals(owner)).forEach(Entity::discard);if(!hoglin.isRemoved())hoglin.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=70)
    public static void actualDeathCommandRunsExactlyOnce(GameTestHelper h){
        unavailableBodyIsNotDefeat(h);
        var victim=publish(h,EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel()),"DeathCommand");victim.setOnDeathCommand(deathCounter(h,"death"));victim.kill();
        h.startSequence().thenExecuteAfter(25,()->{h.assertTrue(count(h,"death")==1,"genuine confirmed death executes command once");victim.discard();h.assertTrue(count(h,"death")==1,"later cleanup cannot repeat a death command");}).thenSucceed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void summonedBeeRemainingLifeSurvivesReload(GameTestHelper h){
        var old=publish(h,EntityRegistrar.BEE_UNIT.get().create(h.getLevel()),"BeeTtl");old.setIsSummoned(true);ticks(old,390);var restored=reload(h,old,EntityRegistrar.BEE_UNIT.get());
        try {ticks(restored,20);h.assertTrue(restored.isDeadOrDying()||restored.isRemoved(),"bee expires after remaining active ticks, not another full lifetime");}finally{restored.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void summonedZombieDecaySurvivesReload(GameTestHelper h){
        var old=publish(h,EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel()),"ZombieTtl");old.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.NETHERITE_HELMET));old.setIsSummoned(true);ticks(old,RaiseDead.ZOMBIE_TICKS_BEFORE_DECAY-10);var restored=reload(h,old,EntityRegistrar.ZOMBIE_UNIT.get());
        try {float hp=restored.getHealth();ticks(restored,40);h.assertTrue(restored.getHealth()<hp,"zombie decay starts after saved remainder, not a fresh sixty seconds");}finally{restored.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void totemRemainingLifeSurvivesReload(GameTestHelper h){
        var old=publish(h,EntityRegistrar.TOTEM_OF_REGENERATION.get().create(h.getLevel()),"TotemTtl");ticks(old,old.lifeTimeTicks-10);var restored=reload(h,old,EntityRegistrar.TOTEM_OF_REGENERATION.get());
        try {ticks(restored,20);h.assertTrue(restored.isDeadOrDying()||restored.isRemoved(),"totem expires after saved remaining active ticks");}finally{restored.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void temporarySilverfishRemainsTemporaryAfterReload(GameTestHelper h){
        var old=publish(h,EntityRegistrar.SILVERFISH_UNIT.get().create(h.getLevel()),"SilverfishTtl");old.setLimitedLife();ticks(old,190);var restored=reload(h,old,EntityRegistrar.SILVERFISH_UNIT.get());
        try {float hp=restored.getHealth();ticks(restored,20);h.assertTrue(restored.getHealth()<hp,"limited-life flag and remaining decay counter survive reload");}finally{restored.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void frostBlinkSaveReloadInterruptsAtSafePosition(GameTestHelper h){
        var old=publish(h,EntityRegistrar.WRETCHED_WRAITH_UNIT.get().create(h.getLevel()),"BlinkSave");Vec3 before=old.position();old.frostBlink(old.getOnPos().offset(18,0,0));ticks(old,5);h.assertTrue(old.getY()<before.y-5,"real blink moved body into its transient hidden phase");var restored=reload(h,old,EntityRegistrar.WRETCHED_WRAITH_UNIT.get());
        try {h.assertTrue(restored.position().distanceToSqr(before)<.01,"save/load resumes at stable pre-cast position, not buried render displacement");ticks(restored,5);h.assertTrue(restored.position().distanceToSqr(before)<.1,"interrupted cast never resumes an unsaved destination");}finally{restored.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void endermanChoosesDistinctCollisionFreeAdjacentCandidate(GameTestHelper h){
        floor(h);var enderman=publish(h,EntityRegistrar.ENDERMAN_UNIT.get().create(h.getLevel()),"Teleport");var ground=h.absolutePos(new BlockPos(12,1,12));for(int y=1;y<=4;y++)h.getLevel().setBlock(ground.above(y),Blocks.STONE.defaultBlockState(),2);
        try {enderman.teleport(ground);h.assertTrue(!enderman.blockPosition().equals(ground.above())&&h.getLevel().noCollision(enderman,enderman.getBoundingBox()),"blocked primary candidate cannot alias every adjacent candidate");h.assertTrue(enderman.position().distanceToSqr(Vec3.atBottomCenterOf(ground.above()))<3,"a nearby valid landing, not silently unchanged origin");}finally{enderman.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void learnedAvatarStillCastsAndPaysNativeMana(GameTestHelper h){
        var hero=publish(h,EntityRegistrar.ROYAL_GUARD_UNIT.get().create(h.getLevel()),"AvatarPositive");
        try {hero.setExperience(100000);hero.setSkillPoints(1);h.assertTrue(hero.getAvatar().rankUp(hero),"legal level/rank transition");hero.setMana(100);command(h,hero,UnitAction.AVATAR,-1);h.assertTrue(hero.getCastAvatarGoal().isCasting(),"learned ultimate accepted");for(int i=0;i<=hero.getCastAvatarGoal().channelTicksMax;i++)hero.getCastAvatarGoal().tick();h.assertTrue(hero.getAvatarTicksLeft()>0&&hero.getMana()==0&&hero.getCooldown(hero.getAvatar())>0,"native cast commits exactly one mana/cooldown payment and effect");}finally{hero.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void researchedHealingPotionCanAutocastAndThrow(GameTestHelper h){
        var witch=publish(h,EntityRegistrar.WITCH_UNIT.get().create(h.getLevel()),"PotionPositive");var target=publish(h,EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel()),"PotionAlly");target.setOwnerName(witch.getOwnerName());target.setHealth(target.getMaxHealth()-5);
        try {ResearchServerEvents.addResearch(witch.getOwnerName(),ProductionItems.RESEARCH_HEALING_POTIONS);var ability=ability(witch,UnitAction.THROW_HEALING_POTION);command(h,witch,UnitAction.THROW_HEALING_POTION_AUTOCAST_ENABLE,-1);h.assertTrue(ability.isAutocasting(witch),"researched autocast accepted");command(h,witch,UnitAction.THROW_HEALING_POTION,target.getId());witch.getThrowPotionGoal().tick();h.assertTrue(ability.getCooldown(witch)>0,"valid researched throw reaches native commit");h.assertTrue(!h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.ThrownPotion.class,witch.getBoundingBox().inflate(4),p->p.getOwner()==witch).isEmpty(),"actual potion projectile was published");
        }finally{target.discard();witch.discard();ResearchServerEvents.removeAllResearchFor(witch.getOwnerName());}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void ordinaryNativeDamagePositiveControl(GameTestHelper h){
        var attacker=publish(h,EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel()),"DamagePositive");var victim=publish(h,EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel()),"DamageVictimPositive");
        try {attacker.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(12);attacker.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);victim.getAttribute(Attributes.ARMOR).setBaseValue(0);float hp=victim.getHealth();victim.hurt(attacker.damageSources().mobAttack(attacker),12);h.assertTrue(Math.abs(hp-victim.getHealth()-12)<.001,"unmodified native damage remains twelve");}finally{attacker.discard();victim.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void intactFlyingPortalStillTeleports(GameTestHelper h){
        var ghast=publish(h,EntityRegistrar.GHAST_UNIT.get().create(h.getLevel()),"PortalPositive");
        try {var goal=(FlyingUsePortalGoal)ghast.getUsePortalGoal();var portal=new PortalPlacement(Buildings.PORTAL_TRANSPORT,h.getLevel(),ghast.blockPosition(),Rotation.NONE,ghast.getOwnerName(),new ArrayList<>(),false);portal.isBuilt=true;portal.destination=ghast.blockPosition().offset(10,0,0);h.getLevel().setBlock(portal.originPos,Blocks.OBSIDIAN.defaultBlockState(),2);var field=BuildingPlacement.class.getDeclaredField("placedBlockPosSet");field.setAccessible(true);((Set<BlockPos>)field.get(portal)).add(portal.originPos);set(goal,FlyingUsePortalGoal.class,"buildingTarget",portal);set(goal,MoveToTargetBlockGoal.class,"moveTarget",portal.centrePos.above(4));ghast.moveTo(Vec3.atCenterOf(portal.centrePos.above(4)));goal.tick();h.assertTrue(ghast.position().distanceToSqr(Vec3.atCenterOf(portal.destination.above(4)))<.001&&goal.getBuildingTarget()==null,"intact portal executes existing destination teleport then clears order");
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}finally{ghast.discard();}h.succeed();
    }
}
