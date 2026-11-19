package com.dreamseekers.unitprogression;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.*;
import com.dreamseekers.economy.*;
import com.dreamseekers.progression.DreamWindResearch;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.research.*;
import com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Four one-time paid technologies; no new mutable progression schema or free unlock packet. */
public final class DreamSpecialistResearch {
    public static final ResourceLocation PRECISION_TOOLS_ID = DreamseekersMod.id("precision_tools");
    public static final ResourceLocation SHELL_WEAVING_ID = DreamseekersMod.id("shell_weaving");
    public static final ResourceLocation CONTRACT_CARE_ID = DreamseekersMod.id("contract_care");
    public static final ResourceLocation STONE_SIEGE_ID = DreamseekersMod.id("stone_siege");
    public static ReceiptBackedResearchItem PRECISION_TOOLS, SHELL_WEAVING, CONTRACT_CARE, STONE_SIEGE;
    private DreamSpecialistResearch() {}

    public static void register(DreamLaboratory host) {
        if (PRECISION_TOOLS != null) throw new IllegalStateException("Specialist research already registered");
        PRECISION_TOOLS = register(host, PRECISION_TOOLS_ID, ResourceCost.Research(75,75,0,30),0, false,
                DreamseekersMod.id("textures/icons/firefly_worker.png"));
        SHELL_WEAVING = register(host, SHELL_WEAVING_ID, ResourceCost.Research(100,50,75,40),0, false,
                DreamseekersMod.id("textures/icons/skill_snail_shell_formation.png"));
        CONTRACT_CARE = register(host, CONTRACT_CARE_ID, ResourceCost.Research(100,100,75,40),20, true,
                DreamseekersMod.id("textures/icons/contract_ecology.png"));
        STONE_SIEGE = register(host, STONE_SIEGE_ID, ResourceCost.Research(0,150,150,60),0, false,
                DreamseekersMod.id("textures/icons/stone_spirit_thrower.png"));
    }
    private static ReceiptBackedResearchItem register(DreamLaboratory host, ResourceLocation id,
            ResourceCost cost,int shadow,boolean ecology,ResourceLocation icon) {
        var item = Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,id,
                new SpecialistItem(id,cost,shadow,ecology,icon));
        host.productions.add(item,null);
        return item;
    }
    public static boolean ownerSupported(String owner,boolean client) {
        if (owner == null || owner.isBlank()) return false;
        if (client) return ClientAccess.ownedDreamAccount(owner);
        var account = ExtendedResourcesSync.account(owner);
        return DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(owner)) && account != null
                && account.extendedStateSupported() && account.addonDataSupported()
                && ResourceAddonData.valid(account.addonData);
    }
    public static boolean unlocked(String owner,ResourceLocation id,boolean client) {
        return ownerSupported(owner,client) && (client ? ResearchClient.hasResearch(id)
                : ResearchServerEvents.playerHasResearch(owner,id));
    }
    public static boolean stoneSiege(boolean client,String owner) { return unlocked(owner,STONE_SIEGE_ID,client); }
    public static boolean careUnlocked(String owner,boolean client) { return unlocked(owner,CONTRACT_CARE_ID,client); }
    public static boolean hasEcology(ProductionPlacement host,boolean client) {
        var buildings = client ? BuildingClientEvents.getBuildings() : BuildingServerEvents.getBuildings();
        return buildings.stream().anyMatch(b -> b.getBuilding()==DreamseekersMod.CONTRACT_ECOLOGY
                && b.getLevel()==host.getLevel() && b.ownerName.equals(host.ownerName) && b.isBuilt
                && !b.isDestroyedServerside && !b.shouldBeDestroyed() && b.getHealth()>0
                && b.getLevel().hasChunksAt(b.minCorner,b.maxCorner));
    }
    private static boolean prerequisites(ProductionPlacement host,boolean client,boolean ecology) {
        return host.getBuilding()==DreamseekersMod.RESONANCE_LABORATORY && ownerSupported(host.ownerName,client)
                && DreamWindResearch.hasAnchor(host,client) && (!ecology || hasEcology(host,client));
    }
    private static final class SpecialistItem extends ReceiptBackedResearchItem {
        private final boolean ecology;
        SpecialistItem(ResourceLocation id,ResourceCost cost,int shadow,boolean ecology,ResourceLocation icon) {
            super(id,cost,shadow==0 ? Map.of() : Map.of(DreamPassiveEconomy.SHADOW,shadow),icon,
                    "research.dreamseekers."+id.getPath(),"research.dreamseekers."+id.getPath()+".desc",p->true);
            this.ecology=ecology;
        }
        @Override public boolean validHostAndPrerequisite(ProductionPlacement p) {
            return super.validHostAndPrerequisite(p) && p instanceof DreamLaboratoryPlacement lab
                    && lab.available() && prerequisites(p,false,ecology);
        }
        @Override public StartProductionButton getStartButton(ProductionPlacement p,Keybinding key) {
            var button=super.getStartButton(p,key);
            var base=button.isEnabled;
            button.isEnabled=()->base.get() && prerequisites(p,true,ecology);
            return button;
        }
    }
    /** Keep physical-client classes off dedicated-server resolution paths. */
    private static final class ClientAccess {
        static boolean ownedDreamAccount(String owner) {
            var mc=net.minecraft.client.Minecraft.getInstance();
            return mc.player!=null && mc.level!=null && mc.getConnection()!=null
                    && ExtendedResourcesClient.ready() && owner.equals(mc.player.getName().getString())
                    && DreamFactionAccess.allows(PlayerClientEvents.getRTSPlayer(owner));
        }
    }
}
