package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.ability.heroAbilities.wretchedwraith.BitterFrostPassive;
import com.solegendary.reignofnether.ability.heroAbilities.wretchedwraith.Blizzard;
import com.solegendary.reignofnether.registrars.BlockEntityRegistrar;
import com.solegendary.reignofnether.unit.units.monsters.WretchedWraithUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Random;
import java.util.UUID;

public class WraithSnowBlockEntity extends BlockEntity {

    private static final Random RANDOM = new Random();
    // Never share vanilla FORCED or another snow block's lease, even in the same chunk.
    private static final TicketType<UUID> SNOW_TICKET = TicketType.create(
            "reignofnether:wraith_snow", UUID::compareTo);
    private final UUID ticketOwner = UUID.randomUUID();
    private ServerLevel ticketLevel;
    private ChunkPos ticketChunk;

    // Runtime entity IDs are recycled and must never be restored from a save.
    private UUID ownerUuid;
    private int ticksToNextMelt = 200;
    private int tickAge = 0;

    public WraithSnowBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistrar.WRAITH_SNOW_BLOCK_ENTITY.get(), pos, state);
        randomiseLifeTicks();
    }

    public Entity getOwner() {
        if (!(level instanceof ServerLevel serverLevel) || ownerUuid == null) return null;
        Entity owner = serverLevel.getEntity(ownerUuid);
        return owner != null && owner.isAlive() && !owner.isRemoved() ? owner : null;
    }

    /** Compatibility accessor: resolve the persistent identity before returning a runtime ID. */
    public int getOwnerId() {
        Entity owner = getOwner();
        return owner == null ? -1 : owner.getId();
    }

    /** Capture a live identity only at placement, never while loading legacy NBT. */
    public void setOwnerId(int ownerId) {
        Entity owner = level instanceof ServerLevel serverLevel ? serverLevel.getEntity(ownerId) : null;
        setOwner(owner);
    }

    public void setOwner(Entity owner) {
        this.ownerUuid = owner != null && owner.level() == level && owner.isAlive() && !owner.isRemoved()
                && level instanceof ServerLevel serverLevel && serverLevel.getEntity(owner.getUUID()) == owner
                ? owner.getUUID() : null;
        setChanged();
    }

    // melt snow faster the more layers there are
    public void randomiseLifeTicks() {
        int layers = this.getBlockState().getValue(BlockStateProperties.LAYERS);
        this.ticksToNextMelt = RANDOM.nextInt(200 - (20 * layers),300 - (20 * layers));
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WraithSnowBlockEntity be) {
        if (level.isClientSide) return;

        be.tickAge += 1;
        if (be.tickAge > 0 && be.tickAge % 10 == 0) {
            if (!be.cannotMelt()) {
                be.ticksToNextMelt -= 10;
                be.setChanged();
                if (be.ticksToNextMelt <= 0) {
                    be.melt(level, pos, state);
                }
            }
        }
    }

    public boolean cannotMelt() {
        return getOwner() instanceof WretchedWraithUnit wraith &&
                ((wraith.isBlizzardInProgress() && worldPosition.distSqr(wraith.blockPosition()) < Blizzard.RADIUS * Blizzard.RADIUS) ||
                (worldPosition.distSqr(wraith.blockPosition()) < BitterFrostPassive.SNOW_NO_MELT_RANGE * BitterFrostPassive.SNOW_NO_MELT_RANGE));
    }

    private void melt(Level level, BlockPos pos, BlockState state) {
        int layers = BlockUtils.getWraithSnowLayers(level.getBlockState(pos));
        if (layers <= 1) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        } else {
            BlockState newLayers = state.setValue(BlockStateProperties.LAYERS, layers - 1);
            level.setBlockAndUpdate(pos, newLayers);
            randomiseLifeTicks();
        }
    }

    private void forceChunk(ServerLevel level) {
        if (isRemoved() || level.getBlockEntity(worldPosition) != this) return;
        ChunkPos chunkPos = new ChunkPos(worldPosition);
        if (ticketLevel == level && chunkPos.equals(ticketChunk)) return;
        unforceChunk();
        level.getChunkSource().addRegionTicket(SNOW_TICKET, chunkPos, 1, ticketOwner);
        ticketLevel = level;
        ticketChunk = chunkPos;
    }

    private void unforceChunk() {
        ServerLevel oldLevel = ticketLevel;
        ChunkPos oldChunk = ticketChunk;
        ticketLevel = null;
        ticketChunk = null;
        if (oldLevel != null && oldChunk != null)
            oldLevel.getChunkSource().removeRegionTicket(SNOW_TICKET, oldChunk, 1, ticketOwner);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            forceChunk(serverLevel);
        }
    }

    @Override
    public void setRemoved() {
        unforceChunk();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.remove("ownerId");
        tag.remove("ownerUuid");
        if (ownerUuid != null) tag.putUUID("ownerUuid", ownerUuid);
        tag.putInt("ticksToNextMelt", ticksToNextMelt);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // A legacy numeric owner cannot be recovered safely after a restart. Let that
        // snow expire normally instead of granting immunity to a recycled entity ID.
        ownerUuid = tag.hasUUID("ownerUuid") ? tag.getUUID("ownerUuid") : null;
        int savedMeltTicks = tag.contains("ticksToNextMelt", Tag.TAG_INT) ? tag.getInt("ticksToNextMelt") : 0;
        if (savedMeltTicks > 0 && savedMeltTicks <= 300) ticksToNextMelt = savedMeltTicks;
        else randomiseLifeTicks();
        tickAge = 0;
    }
}