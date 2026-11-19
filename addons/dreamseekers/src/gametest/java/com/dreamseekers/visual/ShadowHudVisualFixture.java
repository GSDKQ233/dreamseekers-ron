package com.dreamseekers.visual;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.progression.*;
import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.economy.EconomyBalanceSync;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import java.nio.file.Path;
import java.io.IOException;

/** DEV DISPLAY FIXTURE ONLY. Raw seeding is NOT income/refund/payment verification. */
@Mod.EventBusSubscriber(modid="dreamseekers",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ShadowHudVisualFixture {
    private static final ResourceLocation SHADOW=ResourceLocation.fromNamespaceAndPath("dreamseekers","shadow_energy");
    private static final String WORLD="DREAMSEEKERS_VISUAL_FIXTURE";
    private static final SimpleCommandExceptionType DENIED=new SimpleCommandExceptionType(
            Component.literal("Visual fixture rejected: require dev flag, isolated named world, local operator and existing supported account."));
    private ShadowHudVisualFixture() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if(FMLEnvironment.production || !Boolean.getBoolean("dreamseekers.visualFixture"))return;
        event.getDispatcher().register(Commands.literal("dreamseekers-visual-polish")
                .requires(source -> source.hasPermission(2)).executes(ctx -> setupPolish(ctx.getSource())));
        event.getDispatcher().register(Commands.literal("dreamseekers-visual-progress")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("level",IntegerArgumentType.integer(0,5))
                        .executes(ctx -> progress(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"level")))));
        event.getDispatcher().register(Commands.literal("dreamseekers-visual-shadow")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("balance",IntegerArgumentType.integer(0))
                        .executes(ctx -> seed(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"balance")))));
    }

    private static net.minecraft.server.level.ServerPlayer authorized(CommandSourceStack source) throws CommandSyntaxException {
        var server=source.getServer();
        var player=source.getPlayerOrException(); // no console, selectors, owner string or arbitrary target
        if(FMLEnvironment.production || !Boolean.getBoolean("dreamseekers.visualFixture")
                || !source.hasPermission(2) || server.isDedicatedServer() || !server.isSingleplayer()
                || server.isPublished() || !server.isSingleplayerOwner(player.getGameProfile())
                || server.getPlayerList().getPlayers().size()!=1
                || server.getPlayerList().getPlayer(player.getUUID())!=player
                || !WORLD.equals(server.getWorldData().getLevelName()))throw DENIED.create();
        String expected=System.getProperty("dreamseekers.visualFixtureWorld","");
        try {
            if(expected.isBlank() || !server.getWorldPath(LevelResource.ROOT).toRealPath()
                    .equals(Path.of(expected).toRealPath()))throw DENIED.create();
        } catch(IOException | RuntimeException ex) { throw DENIED.create(); }
        return player;
    }

    private static int seed(CommandSourceStack source,int amount) throws CommandSyntaxException {
        var player=authorized(source);
        if(amount!=0 && amount!=999 && amount!=1000 && amount!=1300 && amount!=Integer.MAX_VALUE)
            throw new SimpleCommandExceptionType(Component.literal("Display fixtures: 0, 999, 1000, 1300, 2147483647 only.")).create();
        var account=ExtendedResourcesSync.account(player.getGameProfile().getName());
        if(account==null || !account.extendedStateSupported() || account.revision==Long.MAX_VALUE)throw DENIED.create();
        int before=account.extra.getOrDefault(SHADOW,0);
        account.extra.put(SHADOW,amount);
        account.revision++;
        ExtendedResourcesSync.send(player); // actual existing owner-only packet path; no synthetic client state
        String record="DISPLAY_FIXTURE_RAW_SEED shadow="+before+"->"+amount+" revision="+account.revision
                +"; NOT product income/refund proof. Disposable visual world only.";
        source.sendSuccess(() -> Component.literal(record),false);
        System.out.println(record);
        return 1;
    }

    private static final BlockPos DISPLAY_ORIGIN=new BlockPos(0,100,0);
    private static final String MARKER="dreamseekers-polish-display-setup.txt";
    private static int setupPolish(CommandSourceStack source)throws CommandSyntaxException {
        var player=authorized(source);var server=source.getServer();var level=player.serverLevel();var owner=player.getGameProfile().getName();
        var account=ExtendedResourcesSync.account(owner);var rts=PlayerServerEvents.getRTSPlayer(owner);
        var marker=server.getWorldPath(LevelResource.ROOT).resolve(MARKER);
        if(account==null||!account.extendedStateSupported()||!account.addonDataSupported()||account.revision==Long.MAX_VALUE
                ||rts==null||!DreamseekersMod.DREAMSEEKERS.equals(rts.faction)||!DreamWindProgression.canEarnWind(level,owner,account.accountEpoch)||java.nio.file.Files.exists(marker))throw DENIED.create();
        // Reject any preexisting fixture-area placement before changing terrain. One-shot named disposable world only.
        for(var existing:BuildingServerEvents.getBuildings())if(existing.getLevel()==level&&existing.originPos.distSqr(DISPLAY_ORIGIN)<160*160)throw DENIED.create();
        try{java.nio.file.Files.writeString(marker,"STARTED: trusted raw visual fixtures, never paid gameplay proof.\n",java.nio.file.StandardOpenOption.CREATE_NEW);}catch(IOException e){throw DENIED.create();}
        for(int cx=-2;cx<=7;cx++)for(int cz=-2;cz<=6;cz++){level.setChunkForced(cx,cz,true);level.getChunk(cx,cz);}
        for(int x=-8;x<=110;x++)for(int z=-8;z<=88;z++){
            level.setBlock(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=101;y<=122;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        account.food=50000;account.wood=50000;account.ore=50000;account.extra.put(SHADOW,1000);account.revision++;
        if(!DreamWindProgression.earnWind(level,owner,account.accountEpoch))throw DENIED.create();
        var placements=new ArrayList<BuildingPlacement>();
        placements.add(install(level,owner,DreamseekersMod.DREAM_ANCHOR,DISPLAY_ORIGIN,WorldTreeModel.BASE_V3));
        placements.add(install(level,owner,DreamseekersMod.DREAM_ANCHOR,DISPLAY_ORIGIN.offset(24,0,0),WorldTreeModel.SHADOW_V3));
        placements.add(install(level,owner,DreamseekersMod.DREAM_ANCHOR,DISPLAY_ORIGIN.offset(48,0,0),WorldTreeModel.WIND_V4));
        var others=new ArrayList<Building>();others.add(DreamseekersMod.REVERIE_GARDEN);others.add(DreamseekersMod.BEETLE_SANCTUARY);others.addAll(DreamseekersMod.FUNCTIONAL_BUILDINGS);
        int index=0;for(var def:others){int col=index%5,row=index/5;placements.add(install(level,owner,def,DISPLAY_ORIGIN.offset(col*22,0,26+row*22),null));index++;}
        var recipes=new LinkedHashSet<com.dreamseekers.production.DreamUnitProduction>();for(var host:List.of(DreamseekersMod.DREAM_ANCHOR,DreamseekersMod.BEETLE_SANCTUARY,DreamseekersMod.DREAM_HALL))for(var item:host.productions.get())if(item instanceof com.dreamseekers.production.DreamUnitProduction recipe)recipes.add(recipe);
        if(recipes.size()!=19)throw new IllegalStateException("visual fixture expected exactly19 visible production recipes");
        index=0;for(var recipe:recipes){var entity=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(recipe.getItemName())).create(level);if(!(entity instanceof Unit unit))throw new IllegalStateException("not an actual unit recipe");unit.setOwnerName(owner);unit.setupEquipmentAndUpgradesServer();entity.moveTo(3+(index%10)*10,101,73+(index/10)*10,0,0);if(entity instanceof net.minecraft.world.entity.Mob mob){mob.setNoAi(true);mob.setPersistenceRequired();}if(!level.addFreshEntity(entity))throw new IllegalStateException("visual unit spawn rejected");System.out.println("POLISH_VISUAL_UNIT index="+index+" id="+recipe.getItemName()+" entity="+entity.getId());index++;}
        for(var placement:placements){BuildingClientboundPacket.placeBuilding(placement.originPos,placement.getBuilding(),placement.rotation,owner,placement.scenarioRoleIndex,0,false,placement.getUpgradeLevel(),true,PortalPlacement.PortalType.BASIC,BlockPos.ZERO);System.out.println("POLISH_VISUAL_BUILDING "+placement.getBuilding().name+" origin="+placement.originPos+" snapshot="+placement.structureSnapshot());}
        pendingCheck=new VisualSurvivalCheck(level,player.getUUID(),List.copyOf(placements),40);
        EconomyBalanceSync.send(player);DreamWindPackets.sendToOwner(server,owner);
        var record="POLISH_VISUAL_RAW_SETUP buildings="+placements.size()+" units="+recipes.size()+" BASE/SHADOW/WIND coexist; trusted raw grants/spawns, NOT payment/income/refund proof.";
        source.sendSuccess(()->Component.literal(record),false);System.out.println(record);return 1;
    }
    private static BuildingPlacement install(net.minecraft.server.level.ServerLevel level,String owner,Building definition,BlockPos pos,WorldTreeModel model){
        var p=BuildingUtils.getNewBuildingPlacement(definition,level,pos,Rotation.NONE,owner,false);
        if(p instanceof DreamAnchorPlacement anchor&&model!=null){var s=anchor.tree();s.model=model;s.ascended=model.branch==WorldTreeModel.Branch.SHADOW;s.phase=model.branch==WorldTreeModel.Branch.BASE?WorldTreeState.Phase.IDLE:WorldTreeState.Phase.COMPLETE;s.geometryHighest=model.count;anchor.commitGeometry(anchor.blocks(model),model.count,0);}
        var blocks=p instanceof DreamAnchorPlacement anchor?anchor.blocks(anchor.model()):BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,pos,Rotation.NONE);
        for(var b:blocks)level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        p.initPlacedBlocks();
        if(p.getBlocksTotal()<=0 || p.getBlocksPlaced()!=p.getBlocksTotal())
            throw new IllegalStateException("Visual install incomplete: "+definition.name+" placed="+p.getBlocksPlaced()+" expected="+p.getBlocksTotal());
        BuildingServerEvents.getBuildings().add(p);p.onBuilt();
        System.out.println("POLISH_VISUAL_INSTALL full="+p.getBlocksPlaced()+" name="+definition.name);
        return p;
    }
    private static VisualSurvivalCheck pendingCheck;
    private static final class VisualSurvivalCheck {
        final net.minecraft.server.level.ServerLevel level;
        final UUID player;
        final List<BuildingPlacement> placements;
        int ticks;
        VisualSurvivalCheck(net.minecraft.server.level.ServerLevel level,UUID player,List<BuildingPlacement> placements,int ticks){this.level=level;this.player=player;this.placements=placements;this.ticks=ticks;}
    }
    @SubscribeEvent public static void verifyVisualSurvival(net.minecraftforge.event.TickEvent.LevelTickEvent event){
        var check=pendingCheck;
        if(check==null || event.phase!=net.minecraftforge.event.TickEvent.Phase.END || event.level!=check.level || --check.ticks>0)return;
        pendingCheck=null;
        var failures=new ArrayList<String>();
        for(var placement:check.placements)if(!BuildingServerEvents.getBuildings().contains(placement)||!placement.isBuilt
                ||placement.getBlocksTotal()<=0||placement.getBlocksPlaced()!=placement.getBlocksTotal())
            failures.add(placement.getBuilding().name+" placed="+placement.getBlocksPlaced()+"/"+placement.getBlocksTotal());
        String result="POLISH_VISUAL_SURVIVAL_40T "+(failures.isEmpty()?"PASS buildings="+check.placements.size():"FAIL "+failures);
        System.out.println(result);
        var player=check.level.getServer().getPlayerList().getPlayer(check.player);
        if(player!=null)player.sendSystemMessage(Component.literal(result));
        try{java.nio.file.Files.writeString(check.level.getServer().getWorldPath(LevelResource.ROOT).resolve(MARKER),result+"\n",java.nio.file.StandardOpenOption.APPEND);}
        catch(IOException ex){throw new IllegalStateException("Cannot retain visual survival evidence",ex);}
    }
    private static int progress(CommandSourceStack source,int requested)throws CommandSyntaxException{
        var player=authorized(source);var account=ExtendedResourcesSync.account(player.getGameProfile().getName());
        if(account==null||account.revision==Long.MAX_VALUE||!java.nio.file.Files.exists(source.getServer().getWorldPath(LevelResource.ROOT).resolve(MARKER)))throw DENIED.create();
        var state=DreamWindProgression.read(account);if(!state.supported()||!state.wind())throw DENIED.create();
        for(int f=0;f<3;f++)if(requested<state.level(f))throw DENIED.create();
        for(int f=0;f<3;f++)for(int next=state.level(f)+1;next<=requested;next++)DreamWindProgression.commitTier(account,f,next);
        account.revision++;DreamWindPackets.sendToOwner(source.getServer(),account.ownerName);EconomyBalanceSync.send(player);
        String record="POLISH_VISUAL_RAW_PROGRESS all3levels="+requested+"; trusted fixture grant, NOT paid research proof.";source.sendSuccess(()->Component.literal(record),false);System.out.println(record);return 1;
    }
}
