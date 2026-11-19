package com.solegendary.reignofnether.sandbox;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.kill.EconomicUnitIdentity;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.SlimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Explicit sandbox-only economic preview provenance. Never an Enemy RTS/resource account.
 * Stored inside economic_value so native hero lineage copies cannot discard this restriction.
 * Only the original online UUID, still NONE and in the original account epoch, may redeem it. */
@Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID)
public final class SandboxEconomyFixture {
    public static final String KEY="sandbox_fixture";
    public static final String ENEMY="Enemy";
    private SandboxEconomyFixture() {}

    public record Proof(String issuer, UUID issuerId, UUID accountEpoch, UUID entityId, UUID valueId) {}

    private static Resources uniqueAccount(String owner) {
        Resources found=null;
        for(Resources candidate:ResourcesServerEvents.resourcesList)if(owner.equals(candidate.ownerName)) {
            if(found!=null)return null;
            found=candidate;
        }
        return found;
    }
    private static RTSPlayer uniqueSandbox(String owner) {
        RTSPlayer found=null;
        for(RTSPlayer candidate:PlayerServerEvents.rtsPlayers)if(owner.equals(candidate.name)) {
            if(found!=null)return null;
            found=candidate;
        }
        return found!=null&&found.faction==Factions.NONE?found:null;
    }
    private static Resources liveAccount(ServerPlayer actor,ServerLevel level) {
        if(actor==null||actor.level()!=level||actor.getServer()!=level.getServer()
                ||!level.getServer().isSameThread()||actor.connection==null
                ||level.getServer().getPlayerList().getPlayer(actor.getUUID())!=actor
                ||level.getServer().getPlayerList().getPlayerByName(actor.getName().getString())!=actor)return null;
        String owner=actor.getName().getString();
        Resources account=uniqueAccount(owner);
        return uniqueSandbox(owner)!=null&&account!=null&&account.extendedStateSupported()?account:null;
    }
    /** True means this request was handled, even if creation/publication failed. Never retry spawning.
     * False means no new economic authority was granted; caller may retain its legacy unmarked path. */
    static boolean spawn(ServerPlayer actor,Connection connection,ServerLevel level,
                         EntityType<? extends Mob> type,String owner,BlockPos pos) {
        if(!ENEMY.equals(owner)||connection==null||!connection.isConnected()
                ||actor==null||connection.getPacketListener()!=actor.connection||level==null)return false;
        Resources account=liveAccount(actor,level);
        if(account==null)return false;
        Entity entity=type.create(level);
        if(entity==null)return true;
        entity.moveTo(pos.above().getX()+.5,pos.above().getY(),pos.above().getZ()+.5);
        if(entity instanceof Unit unit) {
            unit.setOwnerName(owner);
            if(entity instanceof SlimeUnit slime)slime.setSize(2,true);
            unit.setAnchor(pos);
            // Only server-registered RTS faction types may become economic preview targets.
            if(entity instanceof LivingEntity living
                    &&Factions.ENTITY_FACTION.containsKey(EntityType.getKey(type))) {
                UUID value=RtsKillValueClaims.valueId(entity);
                CompoundTag marker=new CompoundTag();
                marker.putInt("schema",1);marker.putBoolean("invalid",false);
                marker.putString("issuer",actor.getName().getString());
                marker.putUUID("issuerId",actor.getUUID());marker.putUUID("accountEpoch",account.accountEpoch);
                marker.putUUID("entityId",entity.getUUID());marker.putUUID("valueId",value);
                entity.getPersistentData().getCompound(RtsKillValueClaims.KEY).put(KEY,marker);
                EconomicUnitIdentity.capture(living);
            }
        }
        level.addFreshEntity(entity);
        return true;
    }
    public static boolean has(Entity entity) {
        return entity.getPersistentData().getCompound(RtsKillValueClaims.KEY).contains(KEY);
    }
    /** Called by the existing central owner epoch observer, only on a real tracked mutation. */
    public static void invalidate(Entity entity) {
        CompoundTag value=entity.getPersistentData().getCompound(RtsKillValueClaims.KEY);
        if(value.contains(KEY,Tag.TAG_COMPOUND))value.getCompound(KEY).putBoolean("invalid",true);
        // Other marker types are already permanently ineligible; never normalize them.
    }
    private static Proof read(Entity entity) {
        CompoundTag value=entity.getPersistentData().getCompound(RtsKillValueClaims.KEY);
        if(!value.contains(KEY,Tag.TAG_COMPOUND))return null;
        CompoundTag m=value.getCompound(KEY);
        if(!m.contains("schema",Tag.TAG_INT)||m.getInt("schema")!=1
                ||!m.contains("invalid",Tag.TAG_BYTE)||m.getBoolean("invalid")
                ||!m.contains("issuer",Tag.TAG_STRING)||m.getString("issuer").isBlank()
                ||!m.hasUUID("issuerId")||!m.hasUUID("accountEpoch")
                ||!m.hasUUID("entityId")||!m.hasUUID("valueId"))return null;
        return new Proof(m.getString("issuer"),m.getUUID("issuerId"),m.getUUID("accountEpoch"),
                m.getUUID("entityId"),m.getUUID("valueId"));
    }
    public static Proof capture(LivingEntity victim,String killerOwner,UUID killerEpoch) {
        Proof proof=read(victim);
        return proof!=null&&valid(victim,killerOwner,killerEpoch,proof)?proof:null;
    }
    public static boolean valid(LivingEntity victim,String killerOwner,UUID killerEpoch,Proof proof) {
        if(proof==null||RtsKillValueClaims.isFree(victim)||!(victim.level() instanceof ServerLevel level)||!(victim instanceof Unit unit)
                ||!proof.equals(read(victim))||!ENEMY.equals(unit.getOwnerName())
                ||!proof.entityId.equals(victim.getUUID())||!proof.issuer.equals(killerOwner)
                ||!proof.accountEpoch.equals(killerEpoch)||!proof.valueId.equals(RtsKillValueClaims.valueId(victim)))return false;
        ServerPlayer issuer=level.getServer().getPlayerList().getPlayer(proof.issuerId);
        Resources account=liveAccount(issuer,level);
        return account!=null&&proof.issuer.equals(issuer.getName().getString())&&proof.accountEpoch.equals(account.accountEpoch);
    }
    /** NBT owner initialization is not a capture. Begin runtime mutation observation at world join;
     * loading never repairs provenance or clears a persisted invalid bit. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void joined(EntityJoinLevelEvent event) {
        if(event.getLevel() instanceof ServerLevel level&&level.getServer().isSameThread()
                &&event.getEntity() instanceof LivingEntity living&&living instanceof Unit&&has(living))
            EconomicUnitIdentity.capture(living);
    }
}
