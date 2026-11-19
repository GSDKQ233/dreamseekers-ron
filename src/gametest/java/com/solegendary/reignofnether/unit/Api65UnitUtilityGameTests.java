package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.items.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

/** API64-only public APIs: overlay unchanged on the frozen baseline.
 * Pearl tests document the existing attempt-consumption policy; they are NOT refund-defect assertions. */
@GameTestHolder("api65_unit_utils") @PrefixGameTestTemplate(false)
public final class Api65UnitUtilityGameTests {
    private static final String BATCH="api65_unit_utils";
    private static ServerLevel other(GameTestHelper h) {
        ServerLevel other=h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(other!=null&&other!=h.getLevel(),"real second dimension available");return other;
    }
    private static BuildingPlacement building(ServerLevel level,BlockPos p,String owner) {
        var b=new BuildingPlacement(Buildings.BARRACKS,level,p,Rotation.NONE,owner,
                new ArrayList<>(List.of(new BuildingBlock(p,Blocks.STONE.defaultBlockState()))),false);
        b.isBuilt=true;return b;
    }
    private static <T extends Mob> T publish(GameTestHelper h,T mob,String suffix) {
        ((Unit)mob).setOwnerName("Api65Utility_"+suffix);mob.setNoAi(true);mob.setNoGravity(true);mob.setPersistenceRequired();
        BlockPos p=h.absolutePos(new BlockPos(5,3,5));mob.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);
        h.assertTrue(h.getLevel().addFreshEntity(mob),"registered native body published");return mob;
    }
    @GameTest(templateNamespace="api65_unit_utils",template="ore_mine_test_arena",batch=BATCH)
    public static void automaticBuildingSearchIgnoresAnotherDimension(GameTestHelper h) {
        var unit=publish(h,EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel()),"Aggro");
        BlockPos p=unit.blockPosition().offset(5,0,0);
        var foreign=building(other(h),p,"Api65ForeignEnemy");var local=building(h.getLevel(),p,"Api65LocalEnemy");
        try {
            h.assertTrue(foreign.isAttackable()&&UnitServerEvents.getUnitToBuildingRelationship(unit,foreign)==Relationship.HOSTILE,"foreign placement satisfies non-dimensional attack gates");
            BuildingServerEvents.getBuildings().add(foreign);
            h.assertTrue(MiscUtil.findClosestAttackableBuilding(unit,12)==null,"auto-attack search cannot select a same-coordinate foreign building");
            BuildingServerEvents.getBuildings().add(local);
            h.assertTrue(MiscUtil.findClosestAttackableBuilding(unit,12)==local,"same-dimension hostile building remains an attack candidate");
        } finally {BuildingServerEvents.getBuildings().remove(foreign);BuildingServerEvents.getBuildings().remove(local);unit.discard();}
        h.succeed();
    }
    @GameTest(templateNamespace="api65_unit_utils",template="ore_mine_test_arena",batch=BATCH)
    public static void foreignBuildingDoesNotHideLocalGroundBlock(GameTestHelper h) {
        BlockPos p=h.absolutePos(new BlockPos(6,2,6));BlockState before=h.getLevel().getBlockState(p);
        h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
        var foreign=building(other(h),p,"Api65ForeignGround");var local=building(h.getLevel(),p,"Api65LocalGround");
        try {
            h.assertTrue(MiscUtil.isGroundBlock(h.getLevel(),p),"plain local stone is valid ground");
            BuildingServerEvents.getBuildings().add(foreign);
            h.assertTrue(MiscUtil.isGroundBlock(h.getLevel(),p),"foreign metadata cannot hide local ground");
            BuildingServerEvents.getBuildings().add(local);
            h.assertTrue(!MiscUtil.isGroundBlock(h.getLevel(),p),"same-dimension building still excludes its ground block");
        } finally {BuildingServerEvents.getBuildings().remove(foreign);BuildingServerEvents.getBuildings().remove(local);h.getLevel().setBlock(p,before,2);}
        h.succeed();
    }
    @GameTest(templateNamespace="api65_unit_utils",template="ore_mine_test_arena",batch=BATCH)
    public static void highestGroundScanIgnoresForeignBuilding(GameTestHelper h) {
        BlockPos p=h.absolutePos(new BlockPos(7,12,7));Map<BlockPos,BlockState> saved=new LinkedHashMap<>();
        for(int y=p.getY()-1;y<h.getLevel().getMaxBuildHeight();y++) {
            BlockPos pos=new BlockPos(p.getX(),y,p.getZ());saved.put(pos,h.getLevel().getBlockState(pos));
            h.getLevel().setBlock(pos,(y<=p.getY()?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        }
        var foreign=building(other(h),p,"Api65ForeignTop");var local=building(h.getLevel(),p,"Api65LocalTop");
        try {
            BlockPos initial=MiscUtil.getHighestGroundBlock(h.getLevel(),p);
            h.assertTrue(initial.equals(p.below()),"fixture preserves native helper's one-below-surface convention");
            BuildingServerEvents.getBuildings().add(foreign);
            h.assertTrue(MiscUtil.getHighestGroundBlock(h.getLevel(),p).equals(initial),"foreign metadata cannot lower the local highest-ground result");
            BuildingServerEvents.getBuildings().add(local);
            h.assertTrue(MiscUtil.getHighestGroundBlock(h.getLevel(),p).equals(initial.below()),"local building exclusion is preserved");
        } finally {
            BuildingServerEvents.getBuildings().remove(foreign);BuildingServerEvents.getBuildings().remove(local);
            saved.forEach((pos,state)->h.getLevel().setBlock(pos,state,2));
        }
        h.succeed();
    }
    public static final class PearlAdmissionProbe {
        final LivingEntity owner;final boolean reject;int seen;
        PearlAdmissionProbe(LivingEntity owner,boolean reject){this.owner=owner;this.reject=reject;}
        @SubscribeEvent public void join(EntityJoinLevelEvent event) {
            if(event.getEntity() instanceof ThrownEnderpearl pearl&&pearl.getOwner()==owner){seen++;if(reject)event.setCanceled(true);}
        }
    }
    private static void pearl(GameTestHelper h,boolean reject) {
        var hero=publish(h,EntityRegistrar.ROYAL_GUARD_UNIT.get().create(h.getLevel()),"Pearl"+reject);hero.setCanPickUpLoot(true);
        UnitInventory inventory=(UnitInventory)hero;var stack=UnitItems.ENDER_PEARL.getNewItemStack();stack.setCount(3);inventory.set(0,stack);
        UUID uuid=ItemUtil.getUUID(inventory.get(0));BlockPos target=hero.blockPosition().offset(5,-1,0);BlockState before=h.getLevel().getBlockState(target);
        h.getLevel().setBlock(target,Blocks.STONE.defaultBlockState(),2);
        var listener=new PearlAdmissionProbe(hero,reject);MinecraftForge.EVENT_BUS.register(listener);
        try {
            h.assertTrue(inventory.canPickupUnitItems()&&inventory.canUseItem(UnitItems.ENDER_PEARL)&&hero.getItemGoal()!=null,"legitimate hero item-use preconditions");
            ItemServerEvents.doAction(ItemAction.USE_ON_BLOCK,hero,uuid,-1,target);hero.getItemGoal().tick();
            h.assertTrue(listener.seen==1,"registered pearl callback attempts exactly one projectile publication");
            var pearls=h.getLevel().getEntitiesOfClass(ThrownEnderpearl.class,hero.getBoundingBox().inflate(32),p->p.getOwner()==hero);
            h.assertTrue(pearls.size()==(reject?0:1),"actual world publication matches controlled admission");
            // Native EnderpearlItem likewise ignores publication veto when consuming the thrown item.
            // Do not silently replace the existing projectile-attempt policy with production-unit refunds.
            h.assertTrue(inventory.get(uuid)!=null&&inventory.get(uuid).getCount()==2,"current pearl policy consumes exactly one item per valid throw attempt");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            h.getLevel().getEntitiesOfClass(ThrownEnderpearl.class,hero.getBoundingBox().inflate(32),p->p.getOwner()==hero).forEach(Entity::discard);
            hero.discard();h.getLevel().setBlock(target,before,2);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="api65_unit_utils",template="ore_mine_test_arena",batch=BATCH)
    public static void rejectedPearlPublicationDocumentsAttemptConsumption(GameTestHelper h){pearl(h,true);}
    @GameTest(templateNamespace="api65_unit_utils",template="ore_mine_test_arena",batch=BATCH)
    public static void acceptedPearlPublicationConsumesExactlyOne(GameTestHelper h){pearl(h,false);}
}
