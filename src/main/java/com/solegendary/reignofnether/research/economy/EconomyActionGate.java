package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.function.Function;

/** Connection + owner + epoch authority. Future Piglin/tree actions MUST share this gate/sequence space. */
@Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID)
public final class EconomyActionGate {
    public record Token(UUID session,UUID epoch) {}
    public record Fingerprint(String kind,Object payload) {}
    private static final EconomyRequestSessions<Connection,Fingerprint> states=new EconomyRequestSessions<>();
    private EconomyActionGate() {}
    private static EconomyRequestSessions.Entry<Fingerprint> state(ServerPlayer actor,Connection connection) {
        if(actor==null||!actor.getServer().isSameThread()||!connection.isConnected()
                ||connection.getPacketListener()!=actor.connection)return null;
        String owner=actor.getName().getString();Resources r=ExtendedResourcesSync.account(owner);
        if(r==null||!r.extendedStateSupported()||PlayerServerEvents.getRTSPlayer(owner)==null){states.remove(connection);return null;}
        states.removeIf(c->!c.isConnected());
        var s=states.current(connection,owner,r.accountEpoch);
        return s;
    }
    public static Connection connection(ServerPlayer actor) {
        for(var c:states.connections())if(c.isConnected()&&c.getPacketListener()==actor.connection)return c;
        return null;
    }
    public static Token query(ServerPlayer actor,Connection connection) {
        var s=state(actor,connection);return s==null?null:new Token(s.session,s.epoch);
    }
    public static String execute(ServerPlayer actor,Connection connection,UUID session,UUID epoch,long sequence,
            Fingerprint payload,Function<Resources,String> action) {
        var s=state(actor,connection);
        // Check owner/epoch/session BEFORE any cached status can be returned.
        if(s==null||!s.epoch.equals(epoch)||!s.session.equals(session))return "stale_session";
        var admission=s.admit(session,epoch,sequence,payload);
        if(!admission.execute())return admission.status();
        String result;
        try { result=Objects.requireNonNull(action.apply(ExtendedResourcesSync.account(s.owner))); }
        catch(RuntimeException e) {
            ReignOfNether.LOGGER.error("New economy action failed closed after nonce consumption",e);
            result="failed_closed";
        }
        s.window.finish(sequence,result);return result;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if(e.getEntity() instanceof ServerPlayer p)states.removeIf(c->!c.isConnected()||c.getPacketListener()==p.connection);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e){states.clear();}
}
