// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.scout.ScoutBalance;
import com.dreamseekers.scout.ScoutMarkState;
import com.dreamseekers.unit.PinkChickenScout;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.io.File;
import java.util.UUID;

/** Genuine ordinary-world, two-JVM storage probe. No production world and no simulated deserialize. */
@GameTestHolder("dreamseekers_scout_restart")
@PrefixGameTestTemplate(false)
public final class PinkChickenRestartGameTests {
    private static final String OWNER="ScoutRestart_Owner";
    private static final String ENEMY="ScoutRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(8192,90,8192);
    private static final File MANIFEST=new File("scout-restart-expectations.nbt");
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_scout_restart",timeoutTicks=450)
    public static void ordinaryRestartPreservesIdentityAndCooldownButNeverRecreatesOfflineMark(GameTestHelper h) throws Exception {
        String phase=System.getProperty("dreamseekers.scout.test.phase","");
        if(phase.equals("prepare")) prepare(h); else if(phase.equals("verify")) verify(h);
        else throw new IllegalArgumentException("Unknown scout restart phase "+phase);
    }
    private static void prepare(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();
        h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER)==null && !MANIFEST.exists(),"fresh isolated scout fixture");
        CompoundTag expected=new CompoundTag();
        expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        for(int cx=512;cx<=513;cx++) for(int cz=512;cz<=513;cz++) {
            h.assertTrue(!level.getForcedChunks().contains(ChunkPos.asLong(cx,cz)),"does not adopt existing forced chunks");
            level.setChunkForced(cx,cz,true);
        }
        for(int x=0;x<30;x++) for(int z=0;z<20;z++) {
            level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<8;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        var owner=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(OWNER.getBytes(java.nio.charset.StandardCharsets.UTF_8)),OWNER));
        PinkChickenScout scout=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(level);
        scout.setOwnerName(OWNER);scout.setScenarioRoleIndex(84);scout.setOnDeathCommand("");
        scout.moveTo(ORIGIN.getX()+4.5,ORIGIN.getY()+1,ORIGIN.getZ()+5.5,0,0);
        h.assertTrue(level.addFreshEntity(scout),"scout enters ordinary entity storage");
        PinkChickenScout target=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(level);
        target.setOwnerName(ENEMY);target.moveTo(scout.getX()+5,scout.getY(),scout.getZ(),0,0);
        h.assertTrue(level.addFreshEntity(target),"target enters ordinary entity storage");
        h.assertTrue(scout.scoutMark().tryCast(owner,target,0)==ScoutMarkState.Result.SUCCESS,"genuine accepted cast before shutdown");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(ENEMY,DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(OWNER,123,234,345));
        expected.putUUID("scout",scout.getUUID());expected.putUUID("target",target.getUUID());
        CompoundTag state=new CompoundTag();scout.scoutMark().save(state);
        expected.putLong("cooldownUntil",state.getCompound("DreamseekersScoutMark").getLong("CooldownUntil"));
        // A newly forced ordinary chunk may not yet be entity-ticking at a fixed
        // early test tick. Require genuine simulation readiness, not a manual tick.
        h.startSequence().thenWaitUntil(()->h.assertTrue(scout.tickCount>2,
                "ordinary scout enters real entity ticking before shutdown: ticks="+scout.tickCount
                        +" removed="+scout.isRemoved())).thenExecute(()->{
            // Fake owner has no network session: policy explicitly clears active observation on logout.
            h.assertTrue(!scout.scoutMark().hasActiveMark() && scout.scoutMark().revision()==1,"offline observation clears but replay revision remains");
            h.assertTrue(scout.scoutMark().cooldownTicks()>0,"logout cannot erase cooldown");
            try { NbtIo.writeCompressed(expected,MANIFEST); } catch(Exception e){throw new RuntimeException(e);}
            System.out.println("DREAMSEEKERS_SCOUT_RESTART_PREPARED uuid="+scout.getUUID()+" cooldownUntil="+expected.getLong("cooldownUntil"));
            h.succeed();
        });
    }
    private static void verify(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();CompoundTag expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"genuine second JVM");
        for(int cx=512;cx<=513;cx++)for(int cz=512;cz<=513;cz++) {
            h.assertTrue(level.getForcedChunks().contains(ChunkPos.asLong(cx,cz)),"normal world loaded same forced fixtures");level.getChunk(cx,cz);
        }
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.getEntity(expected.getUUID("scout")) instanceof PinkChickenScout,"wait ordinary entity loader")).thenIdle(2).thenExecute(()->{
            PinkChickenScout scout=(PinkChickenScout)level.getEntity(expected.getUUID("scout"));
            h.assertTrue(scout.getOwnerName().equals(OWNER) && scout.getScenarioRoleIndex()==84 && Factions.getFaction(scout)==DreamseekersMod.DREAMSEEKERS,"same UUID owner registry faction scenario");
            h.assertTrue(scout.isNoGravity() && scout.isFlyingUnit() && !scout.isNoAi() && !scout.noPhysics,"native collision flight survives normal restart");
            h.assertTrue(scout.scoutMark().revision()==1 && !scout.scoutMark().hasActiveMark(),"no duplicate/recreated offline observation after restart");
            int due=(int)Math.max(0,expected.getLong("cooldownUntil")-level.getGameTime());
            h.assertTrue(scout.scoutMark().cooldownTicks()==due && due< ScoutBalance.MARK_COOLDOWN_TICKS,"absolute saved deadline not restarted or refunded");
            var account=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(OWNER)).findFirst().orElseThrow();
            h.assertTrue(account.food==123 && account.wood==234 && account.ore==345,"no restart resource grant");
            h.assertTrue(scout.getMoveGoal().getMoveTarget()==null,"idle command state not replaced by wild goals");
            Vec3 start=scout.position();BlockPos destination=ORIGIN.offset(18,0,5);
            System.out.println("DREAMSEEKERS_SCOUT_RESTART_MOVE_START pos="+start+" ticks="+scout.tickCount+" loaded="+UnitServerEvents.getAllUnits().contains(scout));
            UnitServerEvents.addActionItem(OWNER,UnitAction.MOVE,-1,new int[]{scout.getId()},destination,BlockPos.ZERO);
            // Same 310-tick arrival budget as the independently exercised 14-block
            // obstacle flight; the native controller is not a teleport/ground step.
            long end=h.getTick()+310;
            h.runAtTickTime(end,()->{
                h.assertTrue(scout.position().distanceToSqr(start)>16 && Math.abs(scout.getX()-(destination.getX()+.5))<2,"ordinary post-restart move is real flight: pos="+scout.position()+" start="+start+" ticks="+scout.tickCount+" target="+scout.getMoveGoal().getMoveTarget()+" navDone="+scout.getNavigation().isDone());
                h.assertTrue(level.noCollision(scout),"second JVM keeps collision");
                Entity target=level.getEntity(expected.getUUID("target"));if(target!=null)target.discard();scout.discard();
                PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER)||p.name.equals(ENEMY));
                ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(OWNER));
                for(int cx=512;cx<=513;cx++)for(int cz=512;cz<=513;cz++)level.setChunkForced(cx,cz,false);
                System.out.println("DREAMSEEKERS_SCOUT_RESTART_UUID_COOLDOWN_FLIGHT_PASS uuid="+expected.getUUID("scout"));h.succeed();
            });
        });
    }
}
