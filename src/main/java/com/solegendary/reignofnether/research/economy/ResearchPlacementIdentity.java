package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.data.DataType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public final class ResearchPlacementIdentity {
    private ResearchPlacementIdentity() {}
    public static final DataType<UUID> TYPE = DataType.createRegistered(
        ResourceLocation.fromNamespaceAndPath("reignofnether", "research_placement_identity"),
        (tag, server) -> tag.hasUUID("identity") ? tag.getUUID("identity") : null,
        id -> { CompoundTag t = new CompoundTag(); if(id != null) t.putUUID("identity", id); return t; }, () -> null);
    public static void init() {} // load during normal common registration before saved buildings decode
    public static UUID serverIdentity(BuildingPlacement p) {
        if(p.getLevel().isClientSide()) throw new IllegalStateException("Client cannot mint research identity");
        UUID id = p.getDataStorage().getData(TYPE);
        if(id == null) { id = UUID.randomUUID(); p.getDataStorage().setData(TYPE,id); }
        return id;
    }
}
