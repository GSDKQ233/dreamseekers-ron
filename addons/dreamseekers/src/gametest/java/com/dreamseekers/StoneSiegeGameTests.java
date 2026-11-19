package com.dreamseekers;

import com.dreamseekers.stone.*;
import com.dreamseekers.unit.StoneSpiritThrowerUnit;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Natural ordinary goal/physical projectile slice plus explicit projectile boundary negatives.
 * Research queue/payment itself is tested by the specialist research suite, not faked by this fixture. */
@GameTestHolder("dreamseekers_stone_siege")
@PrefixGameTestTemplate(false)
public final class StoneSiegeGameTests {
    private static final List<Fixture> ALL=new ArrayList<>();
    private static final List<Object> LISTENERS=new ArrayList<>();
    private static final class TestBuilding extends BuildingPlacement {
        boolean collapseAfterFirst;int callbacks,blocksAfterFirst;
        TestBuilding(ServerLevel level,BlockPos pos,String owner,ArrayList<BuildingBlock> blocks) {
            super(DreamseekersMod.BEETLE_SANCTUARY,level,pos,Rotation.NONE,owner,blocks,false);
        }
        @Override public boolean shouldBeDestroyed() {
            return collapseAfterFirst&&getBlocks().stream().anyMatch(b->!b.isPlaced(getLevel()))||super.shouldBeDestroyed();
        }
        @Override public void onBlockBreak(ServerLevel level,BlockPos pos,boolean breakBlocks) {
            callbacks++;super.onBlockBreak(level,pos,breakBlocks);
            if(callbacks==1)blocksAfterFirst=getBlocksPlaced();
        }
    }
    private static final class Fixture {
        final GameTestHelper h;final String owner,enemy;final List<BuildingPlacement> buildings=new ArrayList<>();
        final List<Entity> bodies=new ArrayList<>();final StoneSpiritThrowerUnit stone;final BuildingPlacement target;
        Fixture(GameTestHelper h,String suffix) {
            this.h=h;h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"this isolated physical suite uses no fog; fog checks are a separate boundary");owner="StoneSiege_"+suffix;enemy=owner+"Enemy";
            for(String name:List.of(owner,enemy)) {
                PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));
                ResourcesServerEvents.resourcesList.add(new Resources(name,5000,5000,5000));
            }
            for(int x=0;x<25;x++)for(int z=0;z<18;z++)for(int y=1;y<8;y++)
                h.getLevel().setBlock(pos(x,y,z),y==1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            target=building(enemy,12,2,5);building(owner,2,6,12);building(enemy,18,6,12);
            stone=DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get().create(h.getLevel());
            stone.setOwnerName(owner);var p=pos(6,2,5);stone.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);
            stone.setHoldPosition(true);h.assertTrue(h.getLevel().addFreshEntity(stone),"real stone publication");bodies.add(stone);ALL.add(this);
        }
        BlockPos pos(int x,int y,int z){return h.absolutePos(new BlockPos(x,0,z)).atY(160+y);}
        BuildingPlacement building(String name,int x,int y,int z) {
            var origin=pos(x,y,z);var blocks=new ArrayList<BuildingBlock>();
            for(int i=0;i<8;i++){var p=origin.offset(i%4,0,i/4);h.getLevel().setBlock(p,Blocks.STONE_BRICKS.defaultBlockState(),2);blocks.add(new BuildingBlock(p,Blocks.STONE_BRICKS.defaultBlockState()));}
            var b=new TestBuilding(h.getLevel(),origin,name,blocks);
            BuildingServerEvents.getBuildings().add(b);buildings.add(b);b.onBuilt();return b;
        }
        // HOLD is not passive: AttackerUnit.tick auto-acquires enemies every four ticks.
        // An explicit self-follow keeps this boundary fixture non-idle without disabling AI,
        // ranged cooldowns, command reset, physical flight, or the ordinary building goal.
        void preventUncommandedAcquisition(){stone.setFollowTarget(stone);}
        void research(){ResearchServerEvents.addResearch(owner,DreamSpecialistResearch.STONE_SIEGE_ID);}
        StoneProjectile shot(){System.out.println("STONE_SIEGE_BOUNDARY launch gameTime="+h.getLevel().getGameTime()+", damageBaseline="+damage()+", goal="+stone.getAttackBuildingGoal().getBuildingTarget());var s=new StoneProjectile(DreamseekersMod.STONE_SPIRIT_THROWER_BOLT.get(),h.getLevel());s.armBuilding(stone,target,target.originPos);h.assertTrue(s.isBuildingShotValid(),"trusted building shot armed before publication");h.assertTrue(h.getLevel().addFreshEntity(s),"real projectile publication");bodies.add(s);return s;}
        double damage(){return target.partialBlocksDestroyed+(8-target.getBlocksPlaced());}
    }
    @AfterBatch(batch="stone_siege") public static void cleanup(ServerLevel level) {
        LISTENERS.forEach(net.minecraftforge.common.MinecraftForge.EVENT_BUS::unregister);LISTENERS.clear();
        for(var f:ALL){BuildingServerEvents.getBuildings().removeAll(f.buildings);f.bodies.forEach(Entity::discard);
            for(String name:List.of(f.owner,f.enemy)){ResearchServerEvents.removeAllResearchFor(name);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(name));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(name));}}
        ALL.clear();
    }
    @GameTest(templateNamespace="dreamseekers_stone_siege",template="ore_mine_test_arena",batch="stone_siege",timeoutTicks=240)
    public static void realOrdinaryGoalRequiresResearchAndHitsBuilding(GameTestHelper h) {
        var f=new Fixture(h,"Ordinary");
        h.runAtTickTime(12,()->{
            h.assertTrue(!f.stone.canAttackBuildings(),"research absent denies ability");f.stone.setAttackBuildingTarget(f.target.originPos,true);
            h.assertTrue(f.stone.getAttackBuildingGoal()==null,"unresearched command has no exposed target goal");f.research();
            h.assertTrue(f.stone.canAttackBuildings(),"paid research identity unlocks existing stone");f.stone.setAttackBuildingTarget(f.target.originPos,true);
        });
        h.runAtTickTime(140,()->{
            h.assertTrue(f.damage()>0&&f.target.lastAttacker==f.stone,"natural goal emitted physical stone with native building budget");
            h.assertTrue(h.getLevel().getBlockState(f.pos(9,1,5)).is(Blocks.STONE),"natural terrain remains intact");
            f.stone.setAttackBuildingTarget(null,false);ResearchServerEvents.removeAllResearchFor(f.owner);
            h.assertTrue(!f.stone.canAttackBuildings(),"research removed disables existing stone");h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_stone_siege",template="ore_mine_test_arena",batch="stone_siege",timeoutTicks=230)
    public static void releasedProjectileRejectsOwnerCaptureReplacementAndReload(GameTestHelper h) {
        var f=new Fixture(h,"Boundaries");f.preventUncommandedAcquisition();f.research();BuildingPlacement[] replacement={null};
        h.runAtTickTime(15,()->{
            var s=f.shot();f.stone.setOwnerName(f.enemy);f.stone.setOwnerName(f.owner);
            h.assertTrue(!s.isBuildingShotValid(),"caster owner ABA immediately revokes projectile");
        });
        h.runAtTickTime(35,()->{
            h.assertTrue(f.damage()==0,"caster ABA caused zero budget");var s=f.shot();
            f.target.setOwnerNameFromServerEditor(f.owner);f.target.setOwnerNameFromServerEditor(f.enemy);
            h.assertTrue(!s.isBuildingShotValid(),"building captured away and back revokes projectile");
        });
        h.runAtTickTime(55,()->{
            h.assertTrue(f.damage()==0,"capture ABA caused zero budget; observed="+f.damage());var s=f.shot();
            var tag=new CompoundTag();s.save(tag);s.discard();var restored=new StoneProjectile(DreamseekersMod.STONE_SPIRIT_THROWER_BOLT.get(),h.getLevel());restored.load(tag);
            h.assertTrue(!restored.isBuildingShotValid(),"NBT cannot rearm flight");f.bodies.add(restored);h.getLevel().addFreshEntity(restored);
        });
        h.runAtTickTime(75,()->{
            h.assertTrue(f.damage()==0,"reloaded projectile caused zero budget");var s=f.shot();BuildingServerEvents.getBuildings().remove(f.target);
            replacement[0]=f.building(f.enemy,12,2,5);
            h.assertTrue(!s.isBuildingShotValid(),"same-coordinate replacement cannot inherit old shot authority");
        });
        h.runAtTickTime(95,()->{
            h.assertTrue(replacement[0].getBlocksPlaced()==8&&replacement[0].partialBlocksDestroyed==0,"new same-coordinate placement took no old projectile damage");
            BuildingServerEvents.getBuildings().remove(replacement[0]);BuildingServerEvents.getBuildings().add(f.target);
            h.assertTrue(f.damage()==0,"removed instance flight terminated before reattach");
            var s=f.shot();var wall=f.pos(9,2,5);h.getLevel().setBlock(wall,Blocks.OBSIDIAN.defaultBlockState(),2);
        });
        h.runAtTickTime(120,()->{
            h.assertTrue(f.damage()==0,"new physical obstruction catches already released stone");
            h.assertTrue(h.getLevel().getBlockState(f.pos(9,2,5)).is(Blocks.OBSIDIAN),"obstruction not griefed");h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_stone_siege",template="ore_mine_test_arena",batch="stone_siege",timeoutTicks=400)
    public static void normalAttackPathsAroundOcclusionWithoutShootingThroughIt(GameTestHelper h) {
        var f=new Fixture(h,"Path");f.research();
        var start=f.pos(2,2,5);f.stone.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5);
        for(int z=3;z<=8;z++)for(int y=2;y<=5;y++)h.getLevel().setBlock(f.pos(9,y,z),Blocks.OBSIDIAN.defaultBlockState(),2);
        h.runAtTickTime(12,()->{
            h.assertTrue(!StoneSiegeGoal.clearRay(f.stone,f.target.originPos),"initial physical wall blocks line of fire");
            f.stone.setHoldPosition(false);f.stone.setAttackBuildingTarget(f.target.originPos,true);
        });
        h.runAtTickTime(320,()->{
            h.assertTrue(f.stone.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(start))>4,"natural navigation approached instead of standing at blocked firing position");
            h.assertTrue(f.damage()>0,"natural navigation obtained line of fire and physical impact");
            h.assertTrue(h.getLevel().getBlockState(f.pos(9,2,5)).is(Blocks.OBSIDIAN),"wall never destroyed by siege projectile");h.succeed();
        });
    }

    public static final class JoinOrderProbe {
        final Fixture f;int calls;boolean ownerAba;final List<StoneProjectile> published=new ArrayList<>();
        JoinOrderProbe(Fixture f){this.f=f;}
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void stoneSiegePublicationCommandMutation(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
            if(event.getEntity() instanceof StoneProjectile shot&&shot.getOwner()==f.stone) {
                calls++;published.add(shot);
                final String phase=ownerAba?"OWNER_ABA":"STOP";
                var normal=(com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal<?>)f.stone.getAttackGoal();
                System.out.println("STONE_SIEGE_PUBLICATION phase="+phase+", call="+calls+", bolt="+shot.getUUID()+", damageBaseline="+f.damage()+", cooldown="+normal.getAttackCooldown());
                f.h.assertTrue(normal.getAttackCooldown()==0,"publication began with uncommitted cooldown");
                if(ownerAba){f.stone.setOwnerName(f.enemy);f.stone.setOwnerName(f.owner);}
                else {
                    com.solegendary.reignofnether.unit.interfaces.Unit.fullResetBehaviours(f.stone);
                    f.h.assertTrue(f.stone.getAttackBuildingGoal().getBuildingTarget()==null,"real STOP clears command inside publication callback");
                }
                f.h.runAfterDelay(1,()->{
                    System.out.println("STONE_SIEGE_PUBLICATION_AFTER phase="+phase+", calls="+calls+", removed="+shot.isRemoved()+", valid="+shot.isBuildingShotValid()+", damage="+f.damage()+", cooldown="+normal.getAttackCooldown());
                    f.h.assertTrue(shot.isRemoved()&&!shot.isBuildingShotValid(),"mutated publication discarded exact bolt and its authority");
                    f.h.assertTrue(f.damage()==0&&normal.getAttackCooldown()==0,"mutated publication committed neither damage nor cooldown");
                });
                // STOP intentionally permits future idle aggression in the real game. Isolate
                // this publication from a NEW valid order, not from the reset under test.
                f.preventUncommandedAcquisition();
            }
        }
    }
    @GameTest(templateNamespace="dreamseekers_stone_siege",template="ore_mine_test_arena",batch="stone_siege",timeoutTicks=220)
    public static void naturalPublicationRechecksSameTickStopAndOwnerChange(GameTestHelper h) {
        var f=new Fixture(h,"JoinOrders");f.preventUncommandedAcquisition();f.research();var probe=new JoinOrderProbe(f);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(probe);LISTENERS.add(probe);
        h.runAtTickTime(12,()->f.stone.setAttackBuildingTarget(f.target.originPos,true));
        h.runAtTickTime(85,()->{
            h.assertTrue(probe.calls==1&&f.damage()==0,"real natural publication STOP callback revoked the uncommitted shot; calls="+probe.calls+", damage="+f.damage());
            h.assertTrue(probe.published.get(0).isRemoved(),"STOP revoked the actual published bolt, not just its later damage");
            h.assertTrue(f.stone.getAttackBuildingGoal().getBuildingTarget()==null,"STOP did not retain building command");
            probe.ownerAba=true;f.stone.setAttackBuildingTarget(f.target.originPos,true);
        });
        h.runAtTickTime(160,()->{
            h.assertTrue(probe.calls==2&&f.damage()==0,"second natural publication owner ABA callback revoked old authority; calls="+probe.calls+", damage="+f.damage());
            h.assertTrue(probe.published.get(1).isRemoved(),"owner ABA revoked the actual second published bolt");
            h.assertTrue(f.stone.getAttackBuildingGoal().getBuildingTarget()==null,"changed-owner order cleared on natural tick");h.succeed();
        });
    }

    @GameTest(templateNamespace="dreamseekers_stone_siege",template="ore_mine_test_arena",batch="stone_siege",timeoutTicks=100)
    public static void committedPhysicalBreakKeepsAftermathButStopsFurtherUnauthorizedDamage(GameTestHelper h) {
        var f=new Fixture(h,"Aftermath");f.research();var target=(TestBuilding)f.target;
        h.runAtTickTime(15,()->{
            target.collapseAfterFirst=true;
            f.stone.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(1000);
            f.shot();
        });
        h.runAtTickTime(40,()->{
            h.assertTrue(target.callbacks==1&&target.blocksAfterFirst==7,"first actual block destruction ran original aftermath exactly once even though collapse predicate changed");
            h.assertTrue(target.lastAttacker==f.stone,"committed attribution retained");h.succeed();
        });
    }

}
