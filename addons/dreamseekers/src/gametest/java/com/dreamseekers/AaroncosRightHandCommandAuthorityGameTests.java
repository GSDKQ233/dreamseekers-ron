// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.AaroncosRightHandUnit;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Actual authenticated Forge handler and pre-dispatch queue boundary, not a socket multiplayer test. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AaroncosRightHandCommandAuthorityGameTests {
    private static final String PREFIX="AaronRightCmd_", BATCH="dreamseekers_aaroncos_right_hand_command_authority";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final List<String> OWNERS=new ArrayList<>();
    private static final List<UnitActionItem> FAST_FIXTURES=new ArrayList<>();

    @AfterBatch(batch=BATCH)
    public static void cleanup(ServerLevel level) {
        try { fastQueue().removeIf(FAST_FIXTURES::contains); }
        catch(Exception e) { throw new RuntimeException(e); }
        FAST_FIXTURES.clear();
        com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().removeIf(b->b.ownerName.startsWith(PREFIX));
        for(String owner:OWNERS)com.solegendary.reignofnether.research.ResearchServerEvents.removeAllCheatsFor(owner);
        // Remove our queued commands and roster before fixture disposal, avoiding synthetic match defeat.
        UnitServerEvents.getUnitActionSlowQueue().removeIf(q -> Arrays.stream(q.getUnitIds())
                .anyMatch(id -> FIXTURES.stream().anyMatch(e -> e.getId()==id)));
        for(String a:OWNERS)for(String b:OWNERS)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        AlliancesServerEvents.playersWithAlliedControl.removeIf(n -> n.startsWith(PREFIX));
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.startsWith(PREFIX));
        for(Entity e:FIXTURES)if(!e.isRemoved())e.discard();
        FIXTURES.clear();OWNERS.clear();
    }

    @GameTest(template="aaroncos_unit_arena",batch=BATCH,timeoutTicks=80)
    public static void authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h) throws Exception {
        String owner=account("D_owner",DreamseekersMod.DREAMSEEKERS);
        String enemy=account("D_enemy",DreamseekersMod.DREAMSEEKERS);
        String sandbox=account("D_unrelatedSandbox",Factions.NONE);
        AaroncosRightHandUnit mother=mother(h,owner,6);
        NetworkEvent.Context ownerContext=context(actor(h.getLevel(),owner));
        NetworkEvent.Context enemyContext=context(actor(h.getLevel(),enemy));
        BlockPos target=h.absolutePos(new BlockPos(18,0,6));
        h.assertTrue(send(ownerContext,owner,UnitAction.MOVE,new int[]{mother.getId()},target,true),"actual owner can queue a genuine shifted move");
        List<UnitActionItem> original=queued(mother);
        h.assertTrue(original.size()==1,"one pre-existing owner shift command reserved for cancellation regression");
        for(String forged:List.of(owner,sandbox,"")) {
            h.assertTrue(!send(enemyContext,forged,UnitAction.STOP,new int[]{mother.getId()},BlockPos.ZERO,false),"foreign claimed authority is denied: "+forged);
            unchanged(h,mother,original,"forged claimed owner cannot clear enemy queue");
        }
        h.assertTrue(!send(enemyContext,enemy,UnitAction.STOP,new int[]{mother.getId()},BlockPos.ZERO,false),"honest sender name does not authorize foreign selected IDs");
        unchanged(h,mother,original,"foreign IDs filtered before cancellation side effects");
        NetworkEvent.Context noSender=context(null);
        h.assertTrue(noSender.getSender()==null&&!send(noSender,owner,UnitAction.STOP,new int[]{mother.getId()},BlockPos.ZERO,false),"no authenticated sender cannot issue commands");
        unchanged(h,mother,original,"null sender does not clear queue");
        ServerLevel nether=h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether!=null,"genuine second server dimension available");
        h.assertTrue(!send(context(actor(nether,owner)),owner,UnitAction.STOP,new int[]{mother.getId()},BlockPos.ZERO,false),"same authenticated name in wrong dimension is denied");
        unchanged(h,mother,original,"wrong dimension cannot cancel queued Overworld command");
        AlliancesServerEvents.addAlliance(owner,enemy);
        try {
            h.assertTrue(!send(enemyContext,enemy,UnitAction.STOP,new int[]{mother.getId()},BlockPos.ZERO,false),"alliance without explicit control is not unit authority");
            unchanged(h,mother,original,"ordinary ally cannot cancel owner's shift queue");
        } finally {AlliancesServerEvents.removeAlliance(owner,enemy);}
        h.assertTrue(mother.getOwnerName().equals(owner)&&mother.isAlive(),"denied commands do not change unit lifecycle or owner");
        System.out.println("DREAMSEEKERS_AARONCOS_RIGHT_HAND_COMMAND_DENIED_SPOOF_NULL_DIMENSION_QUEUE_PRESERVED_PASS");
        h.succeed();
    }

    @GameTest(template="aaroncos_unit_arena",batch=BATCH,timeoutTicks=80)
    public static void ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h) throws Exception {
        String owner=account("P_owner",DreamseekersMod.DREAMSEEKERS);
        String ally=account("P_ally",DreamseekersMod.DREAMSEEKERS);
        String outsider=account("P_outsider",DreamseekersMod.DREAMSEEKERS);
        String sandbox=account("P_realSandbox",Factions.NONE);
        AaroncosRightHandUnit controlled=mother(h,owner,5),foreign=mother(h,outsider,11);
        BlockPos target=h.absolutePos(new BlockPos(18,0,8));
        h.assertTrue(send(context(actor(h.getLevel(),owner)),owner,UnitAction.MOVE,new int[]{controlled.getId()},target,true),"owner positive path accepted");
        h.assertTrue(queued(controlled).get(0).equals(new UnitActionItem(owner,UnitAction.MOVE,-1,new int[]{controlled.getId()},target,BlockPos.ZERO)),"owner authority and payload retained exactly");
        AlliancesServerEvents.addAlliance(owner,ally);
        AlliancesServerEvents.playersWithAlliedControl.add(owner);
        try {
            h.assertTrue(send(context(actor(h.getLevel(),ally)),owner,UnitAction.MOVE,
                    new int[]{controlled.getId(),foreign.getId(),controlled.getId(),Integer.MAX_VALUE},target,true),"explicit owner-granted ally can submit mixed selection");
            List<UnitActionItem> controlledQueue=queued(controlled);
            h.assertTrue(controlledQueue.size()==2&&queued(foreign).isEmpty(),"selection deduplicated and unauthorized/missing IDs filtered before queue mutation");
            h.assertTrue(controlledQueue.get(1).equals(new UnitActionItem(ally,UnitAction.MOVE,-1,new int[]{controlled.getId()},target,BlockPos.ZERO)),"queued authority is authenticated ally, never claimed owner");
        } finally {AlliancesServerEvents.playersWithAlliedControl.remove(owner);AlliancesServerEvents.removeAlliance(owner,ally);}
        Entity raw=EntityType.COW.create(h.getLevel());
        h.assertTrue(raw!=null,"non-RTS selection control exists");raw.moveTo(controlled.getX()+2,controlled.getY(),controlled.getZ());FIXTURES.add(raw);
        h.assertTrue(h.getLevel().addFreshEntity(raw),"raw control entered same server level");
        AaroncosRightHandUnit removed=mother(h,owner,15);removed.discard();
        h.assertTrue(send(context(actor(h.getLevel(),sandbox)),outsider,UnitAction.MOVE,
                new int[]{foreign.getId(),foreign.getId(),raw.getId(),removed.getId(),Integer.MAX_VALUE},target,true),"actual sandbox sender retains deliberate broad control");
        List<UnitActionItem> foreignQueue=queued(foreign);
        h.assertTrue(foreignQueue.size()==1&&foreignQueue.get(0).equals(new UnitActionItem(sandbox,UnitAction.MOVE,-1,new int[]{foreign.getId()},target,BlockPos.ZERO)),"sandbox action carries actual sender and only one live RTS ID");
        h.assertTrue(queuedId(raw.getId()).isEmpty()&&queued(removed).isEmpty(),"raw and removed entities do not enter RTS action queue");
        System.out.println("DREAMSEEKERS_AARONCOS_RIGHT_HAND_COMMAND_OWNER_ALLY_SANDBOX_ACTUAL_AUTHORITY_PASS");
        h.succeed();
    }

    @GameTest(template="aaroncos_unit_arena",batch=BATCH,timeoutTicks=80)
    public static void buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h) throws Exception {
        String owner=account("B_owner",Factions.VILLAGERS),enemy=account("B_enemy",DreamseekersMod.DREAMSEEKERS);
        NetworkEvent.Context own=context(actor(h.getLevel(),owner)),foreignContext=context(actor(h.getLevel(),enemy));
        var market=com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(
                com.solegendary.reignofnether.building.Buildings.VILLAGER_MARKET,h.getLevel(),
                h.absolutePos(new BlockPos(30,1,30)),net.minecraft.world.level.block.Rotation.NONE,owner,false);
        // Actual registered core building/ability identity. This is a synchronous packet-gate fixture,
        // not evidence of construction or a manually executed trade result.
        market.isBuilt=true;
        com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().add(market);
        h.assertTrue(market.getBuilding().getAbilities().get().stream().anyMatch(a->a.action==UnitAction.SELL_FOOD),"original market has the actual building-only trade ability");
        List<UnitActionItem> fast=fastQueue();int before=fast.size();
        h.assertTrue(!new UnitActionServerboundPacket(enemy,UnitAction.SELL_FOOD,-1,new int[0],BlockPos.ZERO,market.originPos,false).handle(()->foreignContext),"foreign building-only payload denied");
        h.assertTrue(fast.size()==before,"denied building command adds no fast action");
        h.assertTrue(new UnitActionServerboundPacket(owner,UnitAction.SELL_FOOD,-1,new int[0],BlockPos.ZERO,market.originPos,false).handle(()->own),"owned building-only empty selection accepted");
        UnitActionItem buildingAction=fast.get(fast.size()-1);FAST_FIXTURES.add(buildingAction);
        h.assertTrue(fast.size()==before+1&&buildingAction.equals(new UnitActionItem(owner,UnitAction.SELL_FOOD,-1,new int[0],BlockPos.ZERO,market.originPos)),"building-only authority/payload survives authenticated enqueue intact");
        AaroncosRightHandUnit enemyMother=mother(h,enemy,10);BlockPos target=h.absolutePos(new BlockPos(18,0,10));
        h.assertTrue(send(foreignContext,enemy,UnitAction.MOVE,new int[]{enemyMother.getId()},target,true),"enemy owns a genuine protected shifted command");
        List<UnitActionItem> protectedQueue=queued(enemyMother);
        h.assertTrue(new UnitActionServerboundPacket(owner,UnitAction.SELL_FOOD,-1,new int[]{enemyMother.getId()},BlockPos.ZERO,market.originPos,false).handle(()->own),"authorized building can still act when unauthorized unit IDs are filtered");
        UnitActionItem sanitized=fast.get(fast.size()-1);FAST_FIXTURES.add(sanitized);
        h.assertTrue(sanitized.getUnitIds().length==0,"owned building never launders authority over foreign unit IDs");
        unchanged(h,enemyMother,protectedQueue,"building path cannot cancel foreign shifted commands");
        Entity raw=EntityType.COW.create(h.getLevel());h.assertTrue(raw!=null,"ordinary non-RTS pathfinder control exists");
        BlockPos pos=h.absolutePos(new BlockPos(7,1,7));raw.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);FIXTURES.add(raw);
        h.assertTrue(h.getLevel().addFreshEntity(raw),"non-unit control in real level");
        int unchangedSize=fast.size();
        h.assertTrue(!send(own,owner,UnitAction.MOVE,new int[]{raw.getId()},target,false)&&fast.size()==unchangedSize,"ordinary sender cannot command raw mobs");
        com.solegendary.reignofnether.research.ResearchServerEvents.addCheat(owner,"wouldyoukindly");
        try {
            h.assertTrue(com.solegendary.reignofnether.unit.NonUnitServerEvents.canControlAllMobs(h.getLevel(),owner),"actual core full-control cheat active");
            h.assertTrue(!send(foreignContext,owner,UnitAction.MOVE,new int[]{raw.getId()},target,false),"claiming another player's cheat does not confer authority");
            h.assertTrue(send(own,owner,UnitAction.MOVE,new int[]{raw.getId(),raw.getId()},target,false),"actual cheat holder retains existing non-RTS pathfinder control");
            UnitActionItem rawAction=fast.get(fast.size()-1);FAST_FIXTURES.add(rawAction);
            h.assertTrue(rawAction.equals(new UnitActionItem(owner,UnitAction.MOVE,-1,new int[]{raw.getId()},target,BlockPos.ZERO)),"cheat path carries real sender and deduplicated raw ID");
        } finally {com.solegendary.reignofnether.research.ResearchServerEvents.removeCheat(owner,"wouldyoukindly");}
        int afterCheat=fast.size();
        h.assertTrue(!send(own,owner,UnitAction.MOVE,new int[]{raw.getId()},target,false)&&fast.size()==afterCheat,"revoked cheat restores ordinary denial without mutating prior queues");
        System.out.println("DREAMSEEKERS_AARONCOS_RIGHT_HAND_COMMAND_CORE_BUILDING_AND_NONUNIT_CHEAT_ROUTES_PASS");h.succeed();
    }

    @SuppressWarnings("unchecked")
    private static List<UnitActionItem> fastQueue() throws Exception {
        var field=UnitServerEvents.class.getDeclaredField("unitActionFastQueue");field.setAccessible(true);
        return (List<UnitActionItem>)field.get(null);
    }

    private static String account(String suffix,Faction faction) {
        String name=PREFIX+suffix;OWNERS.add(name);
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,faction));
        ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500));return name;
    }
    private static AaroncosRightHandUnit mother(GameTestHelper h,String owner,int x) {
        AaroncosRightHandUnit mother=DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel());h.assertTrue(mother!=null,"registered paid mother fixture exists");
        mother.setOwnerName(owner);BlockPos p=h.absolutePos(new BlockPos(x,1,8));mother.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(mother);
        h.assertTrue(h.getLevel().addFreshEntity(mother),"unit selection exists in genuine server level");return mother;
    }
    private static ServerPlayer actor(ServerLevel level,String name) {return FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),name));}
    private static NetworkEvent.Context context(ServerPlayer actor) throws Exception {
        Connection connection=new Connection(PacketFlow.SERVERBOUND);if(actor!=null)connection.setListener(actor.connection);
        var constructor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);constructor.setAccessible(true);
        NetworkEvent.Context ctx=constructor.newInstance(connection,NetworkDirection.PLAY_TO_SERVER,-1);
        if(ctx.getSender()!=actor)throw new AssertionError("real Forge context sender differs from requested fixture");return ctx;
    }
    private static boolean send(NetworkEvent.Context ctx,String claim,UnitAction action,int[] ids,BlockPos target,boolean shift) {
        return new UnitActionServerboundPacket(claim,action,-1,ids,target,BlockPos.ZERO,shift).handle(()->ctx);
    }
    private static List<UnitActionItem> queued(AaroncosRightHandUnit mother) {return queuedId(mother.getId());}
    private static List<UnitActionItem> queuedId(int id) {return UnitServerEvents.getUnitActionSlowQueue().stream().filter(q->Arrays.stream(q.getUnitIds()).anyMatch(i->i==id)).toList();}
    private static void unchanged(GameTestHelper h,AaroncosRightHandUnit mother,List<UnitActionItem> original,String why) {
        List<UnitActionItem> actual=queued(mother);h.assertTrue(actual.size()==original.size()&&actual.get(0)==original.get(0),why);
    }
}
