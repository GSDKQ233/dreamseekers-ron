// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.DreamseekersMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/** Authenticated inputs, owner-private cooldowns and tracked-visible-only cleanse state. No client amounts. */
public final class SkillNetwork {
    public static final String VERSION = "19";
    public static boolean acceptsProtocol(String version){return VERSION.equals(version);}
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            DreamseekersMod.id("unit_skills"), () -> VERSION, SkillNetwork::acceptsProtocol, SkillNetwork::acceptsProtocol);
    private static boolean registered;

    private SkillNetwork() { }

    public static void init() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Cast.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Cast::encode).decoder(Cast::decode).consumerMainThread(Cast::handle).add();
        CHANNEL.messageBuilder(Sync.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sync::encode).decoder(Sync::decode).consumerMainThread(Sync::handle).add();
        CHANNEL.messageBuilder(Toggle.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Toggle::encode).decoder(Toggle::decode).consumerMainThread(Toggle::handle).add();
        CHANNEL.messageBuilder(GroundCast.class,3,NetworkDirection.PLAY_TO_SERVER)
                .encoder(GroundCast::encode).decoder(GroundCast::decode).consumerMainThread(GroundCast::handle).add();
        CHANNEL.messageBuilder(EffectRemoved.class,4,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EffectRemoved::encode).decoder(EffectRemoved::decode).consumerMainThread(EffectRemoved::handle).add();
        CHANNEL.messageBuilder(BuildingCast.class,5,NetworkDirection.PLAY_TO_SERVER)
                .encoder(BuildingCast::encode).decoder(BuildingCast::decode).consumerMainThread(BuildingCast::handle).add();
    }

    public static void request(Cast packet) { CHANNEL.sendToServer(packet); }
    public static void request(Toggle packet) { CHANNEL.sendToServer(packet); }
    public static void request(GroundCast packet) { CHANNEL.sendToServer(packet); }
    public static void request(BuildingCast packet) { CHANNEL.sendToServer(packet); }

    /** Regular state goes only to the current exact owner. Clears contain no target/effect data. */
    public static void send(ServerPlayer recipient, Sync packet) {
        if (recipient == null || recipient.connection == null || packet == null) return;
        if (packet.revision() >= 0) {
            Entity entity = recipient.serverLevel().getEntity(packet.sourceId());
            if (!(entity instanceof SkillUnit source) || !SkillService.supports(source,packet.skill()) || !source.mob().getUUID().equals(packet.sourceUuid())
                    || !source.getOwnerName().equals(recipient.getGameProfile().getName())) return;
            // Even an internal caller cannot publish a claimed tier: qualify this exact source at send time.
            packet=new Sync(packet.skill(),packet.sourceId(),packet.sourceUuid(),packet.revision(),packet.cooldownTicks(),
                    packet.autocastEnabled(),SkillAmplification.currentTier(source,packet.skill()),
                    SkillAmplification.extensionActive(source,packet.skill()),MotherNestStance.active(source.mob()),
                    showsHandHarmony(packet.skill()) && AaroncosHarmony.active(source));
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> recipient), packet);
    }

    // Harmony belongs to a unit, but only these individual skill snapshots display it.
    private static boolean showsHandHarmony(UnitSkill skill) {
        return skill == UnitSkill.AARONCOS_LEFT_PRESS || skill == UnitSkill.AARONCOS_RIGHT_SHOCK;
    }

    public record Cast(SkillRequest request) {
        public Cast(UnitSkill skill, int sourceId, UUID sourceUuid, long revision, int targetId, UUID targetUuid) {
            this(new SkillRequest(skill, sourceId, sourceUuid, revision, targetId, targetUuid));
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeEnum(request.skill());
            buffer.writeVarInt(request.sourceId());
            buffer.writeUUID(request.sourceUuid());
            buffer.writeLong(request.revision());
            buffer.writeVarInt(request.targetId());
            buffer.writeUUID(request.targetUuid());
        }

        public static Cast decode(FriendlyByteBuf buffer) {
            return new Cast(buffer.readEnum(UnitSkill.class), buffer.readVarInt(), buffer.readUUID(),
                    buffer.readLong(), buffer.readVarInt(), buffer.readUUID());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context network = context.get();
            ServerPlayer sender = network.getSender();
            network.setPacketHandled(true);
            if (sender == null) return;
            SkillResult result = execute(sender);
            if (request == null) return;
            Entity entity = sender.serverLevel().getEntity(request.sourceId());
            if (entity instanceof SkillUnit source && source.mob().getUUID().equals(request.sourceUuid())
                    && source.getOwnerName().equals(sender.getGameProfile().getName())
                    && source.rts().skills().allowFeedback()) {
                sender.displayClientMessage(Component.translatable("ability.dreamseekers.unit_skill.result."
                        + result.name().toLowerCase(Locale.ROOT)), true);
            }
        }

        /** Codec and authority tests call the same service as the authenticated network handler. */
        public SkillResult execute(ServerPlayer authenticatedSender) {
            return SkillService.request(authenticatedSender, request);
        }
    }

    public static void sendCleansed(net.minecraft.world.entity.LivingEntity target,net.minecraft.world.effect.MobEffect effect) {
        var key=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect);if(key==null)return;
        var packet=new EffectRemoved(target.getId(),target.getUUID(),key);
        for(ServerPlayer player:com.dreamseekers.spore.SporeEffectAudience.visibleTracking(target))
            CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);
    }
    public record EffectRemoved(int targetId,UUID targetUuid,net.minecraft.resources.ResourceLocation effect) {
        public void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(targetId);buffer.writeUUID(targetUuid);buffer.writeResourceLocation(effect); }
        public static EffectRemoved decode(FriendlyByteBuf buffer) {
            return new EffectRemoved(buffer.readVarInt(),buffer.readUUID(),buffer.readResourceLocation());
        }
        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->SkillClient.acceptRemoval(this));
        }
    }

    public record BuildingCast(SkillBuildingRequest request) {
        public BuildingCast(UnitSkill skill,int sourceId,UUID sourceUuid,long revision,
                            net.minecraft.core.BlockPos origin,net.minecraft.core.BlockPos contact) {
            this(new SkillBuildingRequest(skill,sourceId,sourceUuid,revision,origin,contact));
        }
        public void encode(FriendlyByteBuf b) {
            b.writeEnum(request.skill());b.writeVarInt(request.sourceId());b.writeUUID(request.sourceUuid());
            b.writeLong(request.revision());b.writeBlockPos(request.origin());b.writeBlockPos(request.contact());
        }
        public static BuildingCast decode(FriendlyByteBuf b) {
            return new BuildingCast(b.readEnum(UnitSkill.class),b.readVarInt(),b.readUUID(),b.readLong(),b.readBlockPos(),b.readBlockPos());
        }
        public SkillResult execute(ServerPlayer sender) { return SkillService.requestBuilding(sender,request); }
        public void handle(Supplier<NetworkEvent.Context> context) {
            var network=context.get();network.setPacketHandled(true);ServerPlayer sender=network.getSender();
            if(sender==null)return;SkillResult result=execute(sender);if(request==null)return;
            Entity e=sender.serverLevel().getEntity(request.sourceId());
            if(e instanceof SkillUnit source&&e.getUUID().equals(request.sourceUuid())
                    &&source.getOwnerName().equals(sender.getGameProfile().getName())&&source.rts().skills().allowFeedback())
                sender.displayClientMessage(Component.translatable("ability.dreamseekers.unit_skill.result."
                        +result.name().toLowerCase(Locale.ROOT)),true);
        }
    }
    public record GroundCast(SkillGroundRequest request) {
        public GroundCast(UnitSkill skill,int sourceId,UUID sourceUuid,long revision,net.minecraft.core.BlockPos position) {
            this(new SkillGroundRequest(skill,sourceId,sourceUuid,revision,position));
        }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeEnum(request.skill());buffer.writeVarInt(request.sourceId());buffer.writeUUID(request.sourceUuid());
            buffer.writeLong(request.revision());buffer.writeBlockPos(request.position());
        }
        public static GroundCast decode(FriendlyByteBuf buffer) {
            return new GroundCast(buffer.readEnum(UnitSkill.class),buffer.readVarInt(),buffer.readUUID(),buffer.readLong(),buffer.readBlockPos());
        }
        public SkillResult execute(ServerPlayer sender) { return SkillService.requestGround(sender,request); }
        public void handle(Supplier<NetworkEvent.Context> context) {
            var network=context.get();ServerPlayer sender=network.getSender();network.setPacketHandled(true);
            if(sender==null)return;
            SkillResult result=execute(sender);if(request==null)return;
            Entity entity=sender.serverLevel().getEntity(request.sourceId());
            if(entity instanceof SkillUnit source&&source.mob().getUUID().equals(request.sourceUuid())
                    &&source.getOwnerName().equals(sender.getGameProfile().getName())&&source.rts().skills().allowFeedback())
                sender.displayClientMessage(Component.translatable("ability.dreamseekers.unit_skill.result."
                        +result.name().toLowerCase(Locale.ROOT)),true);
        }
    }
    /** An input preference, authenticated identically to a manual cast; never an effect toggle. */
    public record Toggle(UnitSkill skill,int sourceId,UUID sourceUuid,long revision,boolean enabled) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeEnum(skill);buffer.writeVarInt(sourceId);buffer.writeUUID(sourceUuid);
            buffer.writeLong(revision);buffer.writeBoolean(enabled);
        }
        public static Toggle decode(FriendlyByteBuf buffer) {
            return new Toggle(buffer.readEnum(UnitSkill.class),buffer.readVarInt(),buffer.readUUID(),buffer.readLong(),buffer.readBoolean());
        }
        public SkillResult execute(ServerPlayer sender) {
            return SkillService.setAutocast(sender,skill,sourceId,sourceUuid,revision,enabled);
        }
        public void handle(Supplier<NetworkEvent.Context> context) {
            var network=context.get();ServerPlayer sender=network.getSender();network.setPacketHandled(true);
            // A queued preference belongs to this live connection, not a later login with the same name.
            if(!com.solegendary.reignofnether.player.ServerPlayerRequest.isCurrent(network))return;
            SkillResult result=execute(sender);Entity entity=sender.serverLevel().getEntity(sourceId);
            if(entity instanceof SkillUnit source&&source.mob().getUUID().equals(sourceUuid)
                    &&source.getOwnerName().equals(sender.getGameProfile().getName())&&source.rts().skills().allowFeedback()) {
                String key=result==SkillResult.SUCCESS?"ability.dreamseekers.unit_skill.autocast."+(enabled?"enabled":"disabled")
                        :"ability.dreamseekers.unit_skill.result."+result.name().toLowerCase(Locale.ROOT);
                sender.displayClientMessage(Component.translatable(key),true);
            }
        }
    }

    /** No target identity/position, shield health or allied state is broadcast by this packet. */
    public record Sync(UnitSkill skill, int sourceId, UUID sourceUuid, long revision, int cooldownTicks, boolean autocastEnabled, int amplificationTier, boolean lightExtension, boolean motherNestActive, boolean handHarmonyActive) {
        public Sync {
            if(amplificationTier<0||amplificationTier>2||amplificationTier!=0&&(!SkillPotencyView.supports(skill)||revision<0))
                throw new IllegalArgumentException("Invalid owner-private amplification tier");
            if(lightExtension&&(amplificationTier<1||!SkillPotencyView.supports(skill)||revision<0)
                ||motherNestActive&&(skill!=UnitSkill.MOTHER_NEST&&skill!=UnitSkill.MOTHER_BROOD||revision<0)
                ||handHarmonyActive&&(!showsHandHarmony(skill)||revision<0))
                throw new IllegalArgumentException("Invalid owner-private advanced skill state");
        }
        public Sync(UnitSkill skill,int sourceId,UUID sourceUuid,long revision,int cooldownTicks,boolean autocastEnabled,int amplificationTier) {
            this(skill,sourceId,sourceUuid,revision,cooldownTicks,autocastEnabled,amplificationTier,false,false,false);
        }
        public Sync(UnitSkill skill,int sourceId,UUID sourceUuid,long revision,int cooldownTicks,boolean autocastEnabled) {
            this(skill,sourceId,sourceUuid,revision,cooldownTicks,autocastEnabled,0);
        }
        /** Source-compatible constructor for the original non-autocasting gel skill. */
        public Sync(UnitSkill skill,int sourceId,UUID sourceUuid,long revision,int cooldownTicks) {
            this(skill,sourceId,sourceUuid,revision,cooldownTicks,false);
        }
        public static Sync clear(SkillUnit source) {
            UnitSkill skill=SkillService.skillFor(source);
            return new Sync(skill==null?UnitSkill.GEL_GUARD:skill, source.mob().getId(), source.mob().getUUID(), -1, 0,false);
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeEnum(skill);
            buffer.writeVarInt(sourceId);
            buffer.writeUUID(sourceUuid);
            buffer.writeLong(revision);
            buffer.writeVarInt(cooldownTicks);
            buffer.writeBoolean(autocastEnabled);
            buffer.writeVarInt(amplificationTier);
            buffer.writeBoolean(lightExtension);buffer.writeBoolean(motherNestActive);buffer.writeBoolean(handHarmonyActive);
        }

        public static Sync decode(FriendlyByteBuf buffer) {
            return new Sync(buffer.readEnum(UnitSkill.class), buffer.readVarInt(), buffer.readUUID(),
                    buffer.readLong(), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(),buffer.readBoolean(),buffer.readBoolean(),buffer.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkillClient.accept(this));
        }
    }
}
