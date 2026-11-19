package com.solegendary.reignofnether.resources.kill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

/** Actual carrier owner changes, not natural getOwner cache invalidation after normal death. */
public final class EconomicCarrierIdentity {
    private EconomicCarrierIdentity() {}
    private static final Map<Projectile,Epoch> TRACKED=new WeakHashMap<>();
    private static final class Epoch {
        Entity owner;UUID ownerUUID;long value;
        Epoch(Entity owner,UUID uuid){this.owner=owner;this.ownerUUID=uuid;}
    }
    static final class Stamp {
        final java.lang.ref.WeakReference<Projectile> carrier;
        final ServerLevel level;
        final UUID uuid,ownerUUID;
        final Entity owner;
        final long epoch;
        Stamp(Projectile p,Epoch e) {carrier=new java.lang.ref.WeakReference<>(p);level=(ServerLevel)p.level();uuid=p.getUUID();owner=e.owner;ownerUUID=e.ownerUUID;epoch=e.value;}
        boolean valid(EconomicDamageOrigin origin) {
            Projectile carrier=this.carrier.get();
            if(carrier==null)return false;
            Epoch now=TRACKED.get(carrier);
            if(now==null||epoch<0||now.value!=epoch||now.owner!=owner||owner!=origin.source
                    ||carrier.level()!=level||!carrier.getUUID().equals(uuid)
                    ||!Objects.equals(ownerUUID,rawUUID(carrier))||!Objects.equals(ownerUUID,origin.source.getUUID()))return false;
            Entity replacement=level.getEntity(uuid);
            if(replacement!=null&&replacement!=carrier)return false;
            Entity resolved=carrier.getOwner();
            return resolved==owner||(resolved==null&&origin.terminal());
        }
    }
    private static UUID rawUUID(Projectile p) {
        return p instanceof EconomicProjectileOwnerView view?view.reignofnether$economicOwnerUUID():null;
    }
    static Stamp capture(Projectile p,Entity owner) {
        if(!(p.level() instanceof ServerLevel)||owner==null||!owner.getUUID().equals(rawUUID(p)))return null;
        Epoch e=TRACKED.computeIfAbsent(p,k->new Epoch(owner,rawUUID(p)));return new Stamp(p,e);
    }
    public static void ownerSet(Projectile p,Entity requestedOwner) {
        if(p.level().isClientSide())return;
        Epoch e=TRACKED.get(p);if(e==null)return;
        // Vanilla setOwner(null) changes no fields. Do not revoke a valid launch for a no-op.
        if(requestedOwner==null)return;
        UUID actualUUID=rawUUID(p);
        if(e.owner!=requestedOwner||!Objects.equals(e.ownerUUID,actualUUID)) {
            e.owner=requestedOwner;e.ownerUUID=actualUUID;
            e.value=e.value<0||e.value==Long.MAX_VALUE?-1:e.value+1;
        }
    }
    static void clear(){TRACKED.clear();}
}
