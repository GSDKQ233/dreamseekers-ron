package com.solegendary.reignofnether.commands;

import com.mojang.brigadier.StringReader;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.commands.rtsapi.argument.BuildingArgument;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.scores.Objective;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real registered OP-2 command pipeline, original API64 APIs only; no player-privilege claim. */
@GameTestHolder("api65_admin_buildings") @PrefixGameTestTemplate(false)
public final class Api65AdminBuildingCommandGameTests {
    private static final String BATCH="api65_admin_buildings";
    private static CommandSourceStack source(GameTestHelper h,BlockPos p) {
        return h.getLevel().getServer().createCommandSourceStack().withPermission(2)
                .withLevel(h.getLevel()).withPosition(p.getCenter()).withSuppressedOutput();
    }
    private static BuildingPlacement building(GameTestHelper h,Building type,BlockPos origin,BlockPos farCorner,String owner) {
        ArrayList<BuildingBlock> blocks=new ArrayList<>();
        blocks.add(new BuildingBlock(origin,Blocks.STONE.defaultBlockState()));
        if(!origin.equals(farCorner))blocks.add(new BuildingBlock(farCorner,Blocks.STONE.defaultBlockState()));
        var b=new BuildingPlacement(type,h.getLevel(),origin,Rotation.NONE,owner,blocks,false);b.isBuilt=true;return b;
    }
    private static int execute(GameTestHelper h,CommandSourceStack source,String command) throws Exception {
        return h.getLevel().getServer().getCommands().getDispatcher().execute(command,source);
    }
    private static Objective counter(GameTestHelper h,CommandSourceStack source,String name) throws Exception {
        var board=h.getLevel().getServer().getScoreboard();
        h.assertTrue(board.getObjective(name)==null,"isolated scoreboard objective");
        int beforeCount=board.getObjectives().size();
        Objective created=null;
        boolean ready=false;
        try {
            int result=execute(h,source,"scoreboard objectives add "+name+" dummy");
            created=board.getObjective(name);
            // Vanilla addObjective returns the total objective count, not a boolean 1.
            // Other completed GameTest batches legitimately leave unrelated objectives.
            h.assertTrue(created!=null&&created.getName().equals(name)
                    &&created.getCriteria()==net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                    "real dispatcher created the exact requested dummy objective");
            h.assertTrue(result==beforeCount+1&&board.getObjectives().size()==beforeCount+1,
                    "objective command returns the exact new total: before="+beforeCount+" result="+result);
            board.getOrCreatePlayerScore("hits",created).setScore(0);
            ready=true;
            return created;
        } finally {
            // If an assertion/command throws before this method returns, the caller's
            // local objective remains null. Retire only our previously-absent name here.
            if(!ready) {
                Objective current=board.getObjective(name);
                if(current!=null&&(created==null||current==created))board.removeObjective(current);
            }
        }
    }
    private static void assertCount(GameTestHelper h,Objective obj,int expected,String why) {
        int actual=h.getLevel().getServer().getScoreboard().getOrCreatePlayerScore("hits",obj).getScore();
        h.assertTrue(actual==expected,why+" actual="+actual+" expected="+expected);
    }
    private static void selectedExactly(GameTestHelper h,CommandSourceStack source,String input,String owner,BuildingPlacement expected) throws Exception {
        var selector=BuildingArgument.buildings().parse(new StringReader(input));selector.setOwnerName(owner);
        var list=selector.findBuildings(source);
        h.assertTrue(list.size()==1&&list.get(0)==expected,"real parsed selector resolves the exact expected building");
    }
    @GameTest(templateNamespace="api65_admin_buildings",template="ore_mine_test_arena",batch=BATCH)
    public static void explicitDistanceLowerBoundFiltersNearBuildingInRegisteredExecute(GameTestHelper h) throws Exception {
        BlockPos origin=h.absolutePos(new BlockPos(4,3,4));String owner="Api65AdminDistanceMin";
        var near=building(h,Buildings.BARRACKS,origin,origin,owner);
        var far=building(h,Buildings.BARRACKS,origin.offset(120,0,0),origin.offset(120,0,0),owner);
        var source=source(h,origin);Objective objective=null;
        try {
            BuildingServerEvents.getBuildings().add(near);BuildingServerEvents.getBuildings().add(far);
            objective=counter(h,source,"a65distmin");
            int branches=execute(h,source,"execute building @b[distance=100..] \""+owner+"\" run scoreboard players add hits a65distmin 1");
            assertCount(h,objective,1,"distance minimum must exclude the near building from real execute branches");
            h.assertTrue(branches==1,"one successful command branch");
            selectedExactly(h,source,"@b[distance=100..]",owner,far);
            // Exact explicit distances are measured from origin.getCenter(), not a coarse
            // integer block AABB that can discard the matching origin itself.
            for(String range:List.of("0","..0.49")) {
                h.getLevel().getServer().getScoreboard().getOrCreatePlayerScore("hits",objective).setScore(0);
                int exactBranches=execute(h,source,"execute building @b[distance="+range+"] \""+owner+"\" run scoreboard players add hits a65distmin 1");
                assertCount(h,objective,1,"zero/sub-half explicit radius keeps the exact origin-center match: "+range);
                h.assertTrue(exactBranches==1,"one origin-center command branch");
                selectedExactly(h,source,"@b[distance="+range+"]",owner,near);
            }
            // Explicit dx/dy/dz remains footprint-volume selection, independent of distance.
            var boxSource=source.withPosition(net.minecraft.world.phys.Vec3.atLowerCornerOf(origin));
            selectedExactly(h,boxSource,"@b[dx=0,dy=0,dz=0]",owner,near);
        } finally {
            BuildingServerEvents.getBuildings().remove(near);BuildingServerEvents.getBuildings().remove(far);
            if(objective!=null)h.getLevel().getServer().getScoreboard().removeObjective(objective);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="api65_admin_buildings",template="ore_mine_test_arena",batch=BATCH)
    public static void explicitDistanceMaximumRejectsAabbCornerInRegisteredExecute(GameTestHelper h) throws Exception {
        BlockPos origin=h.absolutePos(new BlockPos(4,3,4));String owner="Api65AdminDistanceMax";
        var inside=building(h,Buildings.BARRACKS,origin.offset(6,0,0),origin.offset(6,0,0),owner);
        var corner=building(h,Buildings.BARRACKS,origin.offset(8,0,8),origin.offset(8,0,8),owner);
        var source=source(h,origin);Objective objective=null;
        try {
            BuildingServerEvents.getBuildings().add(inside);BuildingServerEvents.getBuildings().add(corner);
            h.assertTrue(corner.originPos.getCenter().distanceToSqr(source.getPosition())>100,"corner is inside ten-block axis bounds but outside the requested radius");
            objective=counter(h,source,"a65distmax");
            int branches=execute(h,source,"execute building @b[distance=..10] \""+owner+"\" run scoreboard players add hits a65distmax 1");
            assertCount(h,objective,1,"distance maximum is Euclidean, not only AABB intersection");
            h.assertTrue(branches==1,"one legitimate in-radius command branch");
            selectedExactly(h,source,"@b[distance=..10]",owner,inside);
        } finally {
            BuildingServerEvents.getBuildings().remove(inside);BuildingServerEvents.getBuildings().remove(corner);
            if(objective!=null)h.getLevel().getServer().getScoreboard().removeObjective(objective);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="api65_admin_buildings",template="ore_mine_test_arena",batch=BATCH)
    public static void implicitSelfSelectorKeepsLargeBuildingFootprintSemantics(GameTestHelper h) throws Exception {
        BlockPos origin=h.absolutePos(new BlockPos(2,3,2));String owner="Api65AdminFootprint";
        var large=building(h,Buildings.BARRACKS,origin,origin.offset(16,3,4),owner);
        var source=source(h,origin.offset(12,1,2));Objective objective=null;
        try {
            BuildingServerEvents.getBuildings().add(large);
            h.assertTrue(large.originPos.getCenter().distanceToSqr(source.getPosition())>100,"source is inside footprint but far from origin");
            objective=counter(h,source,"a65self");
            int branches=execute(h,source,"execute building @s \""+owner+"\" run scoreboard players add hits a65self 1");
            assertCount(h,objective,1,"implicit @s remains current-footprint selection, not explicit one-block origin radius");
            h.assertTrue(branches==1,"existing @s command syntax still succeeds");
            selectedExactly(h,source,"@s",owner,large);
        } finally {
            BuildingServerEvents.getBuildings().remove(large);
            if(objective!=null)h.getLevel().getServer().getScoreboard().removeObjective(objective);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="api65_admin_buildings",template="ore_mine_test_arena",batch=BATCH)
    public static void unrelatedDataMergePreservesSharedAbilityMaximumAndOtherPlacement(GameTestHelper h) throws Exception {
        BlockPos origin=h.absolutePos(new BlockPos(3,3,3));String owner="Api65AdminCooldown";
        var first=building(h,Buildings.BLACKSMITH,origin,origin,owner);
        var second=building(h,Buildings.BLACKSMITH,origin.offset(16,0,0),origin.offset(16,0,0),owner);
        Ability ability=first.getAbilities().stream().filter(a->a.action==UnitAction.EQUIP_LEATHER_ARMOR).findFirst().orElseThrow();
        float originalMax=ability.cooldownMax;var source=source(h,origin);
        try {
            h.assertTrue(originalMax>0&&second.getAbilities().stream().anyMatch(a->a==ability),"registered Blacksmith definition shares the real ability prototype");
            BuildingServerEvents.getBuildings().add(first);BuildingServerEvents.getBuildings().add(second);
            // Both a live partial cooldown and a normal completed-but-retained zero entry are legal states.
            for(int i=0;i<2;i++) {
                float remaining=i==0?originalMax/2:0;first.setCooldown(ability,remaining);
                int result=execute(h,source,"data merge building @p \""+owner+"\" {Tags:[\"api65_probe_"+i+"\"]}");
                h.assertTrue(result==1&&first.getTags().contains("api65_probe_"+i),"registered /data provider merged the unrelated Tags field on the nearest placement");
                h.assertTrue(Math.abs(first.getCooldown(ability)-remaining)<.0001,"unrelated edit preserves the edited building's remaining cooldown");
                h.assertTrue(Math.abs(ability.cooldownMax-originalMax)<.0001,"per-placement remaining cooldown cannot rewrite a shared ability maximum");
                ability.setToMaxCooldown(second);
                h.assertTrue(Math.abs(second.getCooldown(ability)-originalMax)<.0001,"another building's next cast still receives its configured full cooldown");
            }
        } finally {
            // A failing frozen baseline may mutate the process-wide definition: never leak that fixture effect.
            ability.cooldownMax=originalMax;
            BuildingServerEvents.getBuildings().remove(first);BuildingServerEvents.getBuildings().remove(second);
        }
        h.succeed();
    }
}
