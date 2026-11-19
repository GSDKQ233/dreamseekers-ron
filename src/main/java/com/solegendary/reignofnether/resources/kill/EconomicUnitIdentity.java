package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

/** Runtime economic identity only; no skill state, no ownership mutation, no permanent UUID set. */
public final class EconomicUnitIdentity {
    private static final Map<Entity,Epoch> TRACKED=new WeakHashMap<>();
    private EconomicUnitIdentity() {}
    private static final class Epoch {
        String owner, previousOwner;
        long value;
        Epoch(String owner){this.owner=owner;}
    }
    public record Stamp(LivingEntity entity,ServerLevel level,UUID uuid,String owner,long epoch) {
        private Epoch current() {
            observeOwnerMutation(entity);
            if(entity.level()!=level || !entity.getUUID().equals(uuid) || !(entity instanceof Unit))return null;
            Entity replacement=level.getEntity(uuid);
            if(replacement!=null&&replacement!=entity)return null;
            return TRACKED.get(entity);
        }
        public boolean sameOwner() {
            Epoch now=current();
            return now!=null&&epoch>=0&&now.value==epoch&&Objects.equals(owner,now.owner);
        }
        /** Exactly one observed old-owner -> unowned transition, not A -> B -> A/empty. */
        public boolean clearedOnceByDefeat() {
            Epoch now=current();
            return now!=null&&epoch>=0&&epoch<Long.MAX_VALUE&&now.value==epoch+1
                    &&Objects.equals(owner,now.previousOwner)&&(now.owner==null||now.owner.isBlank());
        }
    }
    public static Stamp capture(LivingEntity entity) {
        if(!(entity.level() instanceof ServerLevel level)||!(entity instanceof Unit unit))
            throw new IllegalArgumentException("Server RTS unit required");
        observeOwnerMutation(entity);
        Epoch epoch=TRACKED.computeIfAbsent(entity,e->new Epoch(unit.getOwnerName()));
        return new Stamp(entity,level,entity.getUUID(),unit.getOwnerName(),epoch.value);
    }
    /** SynchedEntityData RETURN hook; all current core/addon Unit owner setters use that path.
     * Untracked entities and client state are strict no-ops. */
    public static void observeOwnerMutation(Entity entity) {
        if(entity.level().isClientSide()||!(entity instanceof Unit unit))return;
        Epoch epoch=TRACKED.get(entity);
        if(epoch!=null&&!Objects.equals(epoch.owner,unit.getOwnerName())) {
            com.solegendary.reignofnether.sandbox.SandboxEconomyFixture.invalidate(entity);
            epoch.previousOwner=epoch.owner;epoch.owner=unit.getOwnerName();
            epoch.value=epoch.value<0||epoch.value==Long.MAX_VALUE ? -1 : epoch.value+1;
        }
    }
    public static void clear(){TRACKED.clear();}
}
