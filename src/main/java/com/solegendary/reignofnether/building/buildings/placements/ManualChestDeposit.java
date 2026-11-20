package com.solegendary.reignofnether.building.buildings.placements;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Each real chest settles to one owner account. Notifications describe only committed credit. */
final class ManualChestDeposit {
    private ManualChestDeposit() { }

    static void collect(BuildingPlacement host, boolean acceptEmeralds) {
        if (!(host.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        Resources account = ExtendedResourcesSync.account(host.ownerName);
        if (account == null) return; // absent or ambiguous recipient: leave every slot alone
        for (var block : host.getBlocks()) {
            if (!block.getBlockState().is(Blocks.CHEST)) continue;
            BlockPos pos = block.getBlockPos();
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) continue;
            Resources credit = account.depositChestItems(chest, acceptEmeralds);
            if (credit == null) continue;
            ResourcesClientboundPacket.addSubtractResources(credit);
            ResourcesClientboundPacket.showFloatingText(credit, pos.below(2));
        }
    }
}
