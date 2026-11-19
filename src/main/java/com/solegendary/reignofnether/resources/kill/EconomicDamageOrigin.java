package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;

/** Launch-time server authority, retaining a dead source without reviving its account. */
final class EconomicDamageOrigin {
    private static UUID session=UUID.randomUUID();
    final LivingEntity source;
    final EconomicUnitIdentity.Stamp identity;
    private final RTSPlayer player;
    private final Resources account;
    private final UUID accountEpoch, launchSession;
    private final Faction faction;
    private final boolean selfExplosion;
    private final EconomicSourceLifecycle.State lifecycle;
    private EconomicDamageOrigin(LivingEntity source,RTSPlayer player,Resources account,boolean selfExplosion) {
        this.selfExplosion=selfExplosion;
        this.source=source;this.identity=EconomicUnitIdentity.capture(source);
        this.player=player;this.account=account;this.accountEpoch=account.accountEpoch;
        this.faction=player.faction;this.launchSession=session;
        this.lifecycle=EconomicSourceLifecycle.track(source);
    }
    static EconomicDamageOrigin captureRegistered(LivingEntity source) {
        if(!(source.level() instanceof ServerLevel level)||!level.getServer().isSameThread()
                ||!(source instanceof Unit unit)||!source.isAlive()||source.isRemoved()
                ||!RtsProjectileProvenance.registered(source)
                ||unit.getOwnerName()==null||unit.getOwnerName().isBlank())return null;
        RTSPlayer player=PlayerServerEvents.getRTSPlayer(unit.getOwnerName());
        Resources account=RtsProjectileProvenance.account(unit.getOwnerName());
        return player==null||player.faction==null||player.faction.key==null||account==null||!account.extendedStateSupported()
                ?null:new EconomicDamageOrigin(source,player,account,false);
    }
    static EconomicDamageOrigin captureSelfExplosion(com.solegendary.reignofnether.unit.units.monsters.CreeperUnit source) {
        if(!(source.level() instanceof ServerLevel level)||!level.getServer().isSameThread()||source.isRemoved()
                ||!RtsProjectileProvenance.registered(source)||source.getOwnerName()==null||source.getOwnerName().isBlank())return null;
        RTSPlayer player=PlayerServerEvents.getRTSPlayer(source.getOwnerName());
        Resources account=RtsProjectileProvenance.account(source.getOwnerName());
        return player==null||player.faction==null||player.faction.key==null||account==null||!account.extendedStateSupported()
                ?null:new EconomicDamageOrigin(source,player,account,true);
    }
    private boolean selfActionValid() {
        return selfExplosion
                &&(!source.isRemoved()||source.getRemovalReason()==net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
    }
    boolean terminal() {return lifecycle.terminal(source);}
    boolean valid() {
        return launchSession.equals(session)&&identity.sameOwner()&&(!lifecycle.invalidRemoval||selfActionValid())
                &&PlayerServerEvents.getRTSPlayer(identity.owner())==player&&player.faction==faction
                &&RtsProjectileProvenance.account(identity.owner())==account&&account.extendedStateSupported()
                &&accountEpoch.equals(account.accountEpoch)
                &&((source.isAlive()&&!source.isRemoved()&&RtsProjectileProvenance.registered(source))||terminal()||selfActionValid());
    }
    static void clear(){session=UUID.randomUUID();EconomicSourceLifecycle.clear();}
}
