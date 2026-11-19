package com.solegendary.reignofnether.scenario;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.resources.Resources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;

// Role that a player can take in a scenario map
public class ScenarioRole {
    public static final int COUNT = 8;

    /** Never share a mutable role, its resource balances, or its tag between worlds. */
    public static ArrayList<ScenarioRole> defaults() {
        ArrayList<ScenarioRole> roles = new ArrayList<>(COUNT);
        for (int index = 0; index < COUNT; index++) roles.add(new ScenarioRole(index));
        return roles;
    }

    public final int index;
    public String name;
    public Faction faction = Factions.NEUTRAL;
    public Resources startingResources = new Resources("", 0,0,0);
    public int teamNumber;
    public boolean isNpc = false;

    CompoundTag nbt = new CompoundTag();

    public ScenarioRole(int index) {
        this.index = index;
        this.name = "Player " + (index + 1);
        this.teamNumber = (index + 1);
        packNbt();
    }

    public static ScenarioRole getFromSave(int index, CompoundTag tag) {
        ScenarioRole role = new ScenarioRole(index);
        role.nbt = tag.copy();
        role.unpackNbt();
        return role;
    }

    public void packNbt() {
        nbt.putInt("index", this.index);
        nbt.putString("name", this.name);
        nbt.putString("faction", this.faction.key.toString());
        nbt.putInt("startingFood", this.startingResources.food);
        nbt.putInt("startingWood", this.startingResources.wood);
        nbt.putInt("startingOre", this.startingResources.ore);
        nbt.putInt("teamNumber", this.teamNumber);
        nbt.putBoolean("isNpc", this.isNpc);
    }

    public void unpackNbt() {
        if (nbt.contains("name", Tag.TAG_STRING)) this.name = nbt.getString("name");
        if (nbt.contains("faction", Tag.TAG_STRING)) {
            String key = nbt.getString("faction");
            // Older saves used enum names instead of registry keys.
            this.faction = switch (key) {
                case "VILLAGERS" -> Factions.VILLAGERS;
                case "MONSTERS" -> Factions.MONSTERS;
                case "PIGLINS" -> Factions.PIGLINS;
                case "NEUTRAL" -> Factions.NEUTRAL;
                default -> {
                    ResourceLocation id = ResourceLocation.tryParse(key);
                    yield id == null ? this.faction : Factions.getFaction(id);
                }
            };
        }
        if (nbt.contains("startingFood", Tag.TAG_INT)) this.startingResources.food = nbt.getInt("startingFood");
        if (nbt.contains("startingWood", Tag.TAG_INT)) this.startingResources.wood = nbt.getInt("startingWood");
        if (nbt.contains("startingOre", Tag.TAG_INT)) this.startingResources.ore = nbt.getInt("startingOre");
        if (nbt.contains("teamNumber", Tag.TAG_INT)) this.teamNumber = nbt.getInt("teamNumber");
        if (nbt.contains("isNpc", Tag.TAG_BYTE)) this.isNpc = nbt.getBoolean("isNpc");
    }
}
