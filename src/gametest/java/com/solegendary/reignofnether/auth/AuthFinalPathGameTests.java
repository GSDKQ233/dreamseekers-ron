package com.solegendary.reignofnether.auth;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.hud.custombutton.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal;
import com.solegendary.reignofnether.unit.goals.RangedAttackBuildingGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** API64-compatible registered command/callback probes. OP2 and model-only UI boundaries are explicit. */
@GameTestHolder("api65_auth_final") @PrefixGameTestTemplate(false)
public final class AuthFinalPathGameTests {
    private static CommandSourceStack source(GameTestHelper h) {
        return h.getLevel().getServer().createCommandSourceStack().withPermission(2)
                .withLevel(h.getLevel()).withPosition(h.absolutePos(new BlockPos(4,2,4)).getCenter())
                .withSuppressedOutput();
    }
    private static String xyz(BlockPos p) { return p.getX()+" "+p.getY()+" "+p.getZ(); }
    @GameTest(templateNamespace="api65_auth_final",template="ore_mine_test_arena",batch="auth_final_type_completion")
    public static void buildingTypeCompletionUsesServerRegistryWithoutClientLinkage(GameTestHelper h) throws Exception {
        h.assertTrue(net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER,
                "physical dedicated environment proves the client linkage boundary");
        var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
        try {
            var suggestions=dispatcher.getCompletionSuggestions(dispatcher.parse("rtsapi building destroy @b[type=",source(h))).join();
            h.assertTrue(suggestions.getList().stream().anyMatch(s->s.getText().contains("blacksmith")),
                    "real registered type selector completion returns a native server building key");
        } catch(Throwable failure) { throw new AssertionError("registered building type completion must not initialize Minecraft client classes",failure); }
        h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_final",template="ore_mine_test_arena",batch="auth_final_execute_target")
    public static void idleMeleeAndRangedTargetForkIsEmptyWhileRealTargetStillForks(GameTestHelper h) throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)) {
            var melee=EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());
            var ranged=EntityRegistrar.GHAST_UNIT.get().create(h.getLevel());
            h.assertTrue(melee!=null&&ranged!=null,"registered native attacker types exist");
            h.assertTrue(((AttackerUnit)melee).getAttackBuildingGoal() instanceof MeleeAttackBuildingGoal,
                    "native melee fixture really has the registered building goal");
            h.assertTrue(((AttackerUnit)ranged).getAttackBuildingGoal() instanceof RangedAttackBuildingGoal<?>,
                    "native ranged fixture really has the registered building goal");
            melee.setNoAi(true);ranged.setNoAi(true);
            var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
            String command="execute rts-related target building run list";
            h.assertTrue(dispatcher.execute(command,source(h).withEntity(melee))==0,"idle melee goal gives zero forks instead of null target exception");
            h.assertTrue(dispatcher.execute(command,source(h).withEntity(ranged))==0,"idle ranged goal gives zero forks instead of null target exception");
            BlockPos pos=h.absolutePos(new BlockPos(6,2,6));
            var blocks=new ArrayList<BuildingBlock>();blocks.add(new BuildingBlock(pos,Blocks.STONE.defaultBlockState()));
            var target=new BuildingPlacement(Buildings.BARRACKS,h.getLevel(),pos,Rotation.NONE,f.name,blocks,false);
            target.isBuilt=true;
            var goal=(RangedAttackBuildingGoal<?>)((AttackerUnit)ranged).getAttackBuildingGoal();
            goal.setBuildingTarget(target);
            h.assertTrue(dispatcher.execute(command,source(h).withEntity(ranged))==1,"real nonnull building target still produces one registered execute branch");
            goal.setBuildingTarget((BuildingPlacement)null);
            h.assertTrue(dispatcher.execute(command,source(h).withEntity(ranged))==0,"clearing a previous target returns to zero forks");
        }
        h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_final",template="ore_mine_test_arena",batch="auth_final_unit_owner")
    public static void ownerOnlyRangeCommandUsesProvidedOwnerAndPreservesOtherOwners(GameTestHelper h) throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)) {
            var own=new VillagerUnit(EntityRegistrar.VILLAGER_UNIT.get(),h.getLevel());
            var other=new VillagerUnit(EntityRegistrar.VILLAGER_UNIT.get(),h.getLevel());
            own.setNoAi(true);other.setNoAi(true);own.setOwnerName(f.name);other.setOwnerName(f.name+"Other");
            own.setPos(h.absolutePos(new BlockPos(3,2,3)).getCenter());other.setPos(h.absolutePos(new BlockPos(5,2,3)).getCenter());
            UnitServerEvents.getAllUnits().add(own);UnitServerEvents.getAllUnits().add(other);
            try {
                var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
                String range=xyz(h.absolutePos(new BlockPos(1,0,1)))+" "+xyz(h.absolutePos(new BlockPos(9,5,9)));
                int changed=dispatcher.execute("rtsapi unit owner "+range+" \""+f.name+"\"",source(h));
                h.assertTrue(changed==1&&own.getOwnerName().isEmpty(),"owner-only branch clears the named owner's unit");
                h.assertTrue(other.getOwnerName().equals(f.name+"Other"),"other owner's unit remains unchanged");
                own.setOwnerName(f.name);
                changed=dispatcher.execute("rtsapi unit owner "+range+" \""+f.name+"\" \""+f.name+"Moved\"",source(h));
                h.assertTrue(changed==1&&own.getOwnerName().equals(f.name+"Moved"),"existing explicit newOwner child remains functional");
                h.assertTrue(other.getOwnerName().equals(f.name+"Other"),"explicit transfer still preserves other owner");
            } finally {UnitServerEvents.getAllUnits().remove(own);UnitServerEvents.getAllUnits().remove(other);own.discard();other.discard();}
        }
        h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_final",template="ore_mine_test_arena",batch="auth_final_button_size")
    public static void customButtonWireSizeReachesPresentationModelWithoutRendering(GameTestHelper h) throws Exception {
        ResourceLocation id=ResourceLocation.parse("api65_auth:wire_size");
        CustomButton previous=CustomButtonClientEvents.customButtons.get(id);
        try {
            for(int size:new int[]{23,14}) {
                var packet=new CustomButtonClientboundPacket((byte)1,id,"SizeProbe",ResourceLocation.parse("minecraft:textures/item/stone.png"),
                        17,-9,size,null,true,true,false,true);
                FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());
                try {
                    packet.encode(b);var decoded=CustomButtonClientboundPacket.decode(b);
                    h.assertTrue(b.readableBytes()==0,"real S2C codec consumes every field");
                    // Directly exercise the data-model callback only; no physical client or render is claimed.
                    decoded.handlePacket();
                } finally {b.release();}
                var model=CustomButtonClientEvents.customButtons.get(id);
                h.assertTrue(model!=null&&model.iconSize()==size&&model.imageSize==size
                        &&((Button)model).iconSize==size&&model.iconFrameSize==size+8,
                        "decoded size reaches both presentation model and inherited hit-frame dimensions");
                h.assertTrue(model.offsetX()==17&&model.offsetY()==-9,"layout offsets preserved");
            }
        } finally {if(previous==null)CustomButtonClientEvents.customButtons.remove(id);else CustomButtonClientEvents.customButtons.put(id,previous);}
        h.succeed();
    }
}
