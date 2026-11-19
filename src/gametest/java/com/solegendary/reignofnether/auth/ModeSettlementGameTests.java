package com.solegendary.reignofnether.auth;

import com.solegendary.reignofnether.gamemode.*;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

/** API64-compatible interruption tests for the repaired, bounded three-packet mode handshake. */
@GameTestHolder("api65_auth_modesettlement") @PrefixGameTestTemplate(false)
public final class ModeSettlementGameTests {
    static final Set<Fixture> OPEN=Collections.newSetFromMap(new IdentityHashMap<>());
    static final class Fixture implements AutoCloseable {
        final AuthBoundaryGameTests.Fixture f;final Resources account;final UUID epoch;final int food,wood,ore,units;boolean closed;
        Fixture(GameTestHelper h)throws Exception {
            f=new AuthBoundaryGameTests.Fixture(h);OPEN.add(this);
            f.send(new GameModeServerboundPacket(GameMode.SURVIVAL));f.send(AuthExtendedGameTests.start(f));
            account=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(f.name)).findFirst().orElseThrow();
            epoch=account.accountEpoch;food=account.food;wood=account.wood;ore=account.ore;
            for(var e:UnitServerEvents.getAllUnits())if(e instanceof Unit u&&u.getOwnerName().equals(f.name)&&e instanceof Mob mob)mob.setNoAi(true);
            units=count();f.peer.drain();
        }
        int count(){return (int)UnitServerEvents.getAllUnits().stream().filter(e->e instanceof Unit u&&u.getOwnerName().equals(f.name)).count();}
        void check(){
            var h=f.h;h.assertTrue(modes().contains(GameMode.CLASSIC),"incomplete Survival handshake rolls back UI mode to CLASSIC");
            h.assertTrue(PlayerServerEvents.rtsPlayers.size()==1&&PlayerServerEvents.rtsPlayers.get(0).name.equals(f.name),"rollback preserves admitted roster");
            h.assertTrue(ResourcesServerEvents.resourcesList.contains(account)&&account.accountEpoch.equals(epoch)
                    &&account.food==food&&account.wood==wood&&account.ore==ore,"rollback does not reset or debit account");
            h.assertTrue(count()==units,"rollback does not delete/recreate units");
        }
        List<GameMode> modes(){
            FriendlyByteBuf sample=new FriendlyByteBuf(Unpooled.buffer());int discriminator;
            try{PacketHandler.INSTANCE.encodeMessage(new GameModeClientboundPacket(GameMode.NONE),sample);discriminator=sample.readVarInt();}finally{sample.release();}
            List<GameMode> values=new ArrayList<>();
            for(Object packet:f.peer.drain())if(packet instanceof ClientboundCustomPayloadPacket payload){
                FriendlyByteBuf b=new FriendlyByteBuf(payload.getData().copy());try{if(b.readVarInt()==discriminator)values.add(new GameModeClientboundPacket(b).gameMode);}finally{b.release();}}
            return values;
        }
        public void close(){if(closed)return;closed=true;OPEN.remove(this);AuthExtendedGameTests.cleanupUnits(f);f.close();}
    }
    @GameTest(templateNamespace="api65_auth_modesettlement",template="ore_mine_test_arena",batch="auth_mode_disconnect",timeoutTicks=80)
    public static void disconnectRollsBackOnlyUninitializedSurvivalMode(GameTestHelper h)throws Exception {
        try(var f=new Fixture(h)){MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.f.actor));f.check();}h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_modesettlement",template="ore_mine_test_arena",batch="auth_mode_timeout",timeoutTicks=180)
    public static void missingDifficultyTimesOutWithoutResettingMatch(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);h.runAfterDelay(110,()->{try{f.check();h.succeed();}finally{f.close();}});
    }
    @AfterBatch(batch="auth_mode_disconnect") public static void cleanup(ServerLevel level){for(var f:List.copyOf(OPEN))f.close();}
    @AfterBatch(batch="auth_mode_timeout") public static void cleanupTimeout(ServerLevel level){cleanup(level);}
}
