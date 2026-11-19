package com.solegendary.reignofnether.auth;

import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real PlayerLoggedIn entry and live Unit.getItems observations; no random race oracle. */
@GameTestHolder("api65_auth_sync") @PrefixGameTestTemplate(false)
public final class UnitLoginThreadGameTests {
    static final Set<Fixture> OPEN=Collections.newSetFromMap(new IdentityHashMap<>());
    static final class ObservedUnit extends VillagerUnit {
        volatile boolean armed,offThread;volatile int reads;
        ObservedUnit(ServerLevel level){super(EntityRegistrar.VILLAGER_UNIT.get(),level);}
        @Override public List<ItemStack> getItems(){
            // Other registered login/tick listeners may legitimately snapshot resources too.
            // Observe only the production method whose deferred lifetime/threading is under test.
            boolean loginSnapshot=armed && Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame ->
                    frame.getClassName().equals(PlayerServerEvents.class.getName()) && frame.getMethodName().equals("syncUnits"));
            if(loginSnapshot){reads++;if(!level().getServer().isSameThread())offThread=true;}
            return super.getItems();
        }
    }
    static final class Fixture implements AutoCloseable {
        final AuthBoundaryGameTests.Fixture peer;final ObservedUnit unit;final GameTestHelper h;final long start=System.nanoTime();boolean closed;
        Fixture(GameTestHelper h)throws Exception {
            this.h=h;peer=new AuthBoundaryGameTests.Fixture(h);unit=new ObservedUnit(h.getLevel());unit.setOwnerName(peer.name);
            UnitServerEvents.getAllUnits().add(unit);unit.armed=true;OPEN.add(this);
            h.assertTrue(!h.getLevel().getServer().isDedicatedServer(),"GameTest follows integrated login branch");
        }
        void login(){MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(peer.actor));}
        void afterOldDeadline(Runnable assertion){if(System.nanoTime()-start<1_400_000_000L){h.runAfterDelay(1,()->afterOldDeadline(assertion));return;}try{assertion.run();h.succeed();}finally{close();}}
        public void close(){if(closed)return;closed=true;OPEN.remove(this);unit.armed=false;UnitServerEvents.getAllUnits().remove(unit);peer.close();}
    }
    @GameTest(templateNamespace="api65_auth_sync",template="ore_mine_test_arena",batch="auth_login_thread",timeoutTicks=160)
    public static void loginUnitReadsStayOnOwningServerThread(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);f.login();h.runAfterDelay(25,()->f.afterOldDeadline(()->{
            h.assertTrue(f.unit.reads>0,"real login sync read observed unit resources");
            h.assertTrue(!f.unit.offThread,"unit/entity resource reads must stay on the server thread");}));
    }
    @GameTest(templateNamespace="api65_auth_sync",template="ore_mine_test_arena",batch="auth_login_logout",timeoutTicks=160)
    public static void logoutRevokesPendingUnitSnapshot(GameTestHelper h)throws Exception {
        Fixture f=new Fixture(h);f.login();MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.peer.actor));
        h.assertTrue(UnitServerEvents.getAllUnits().contains(f.unit),"observer remains live after logout; cancellation, not unit removal, is tested");
        h.runAfterDelay(25,()->f.afterOldDeadline(()->h.assertTrue(f.unit.reads==0,"logout cancels deferred live-state reads, not merely their receiver")));
    }
    @AfterBatch(batch="auth_login_thread") public static void cleanup(ServerLevel level){for(var f:List.copyOf(OPEN))f.close();}
    @AfterBatch(batch="auth_login_logout") public static void cleanupLogout(ServerLevel level){cleanup(level);}
}
