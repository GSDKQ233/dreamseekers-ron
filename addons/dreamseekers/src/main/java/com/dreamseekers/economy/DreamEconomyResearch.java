// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.economy;

import com.dreamseekers.building.DreamAnchor;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem;
import com.solegendary.reignofnether.research.economy.ResearchQueueClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Predicate;

/** Independent global research lines bought at the capital, not a grant for each capital. */
public final class DreamEconomyResearch {
    public enum Line {
        CONVERSION("shadow_conversion_efficiency","shadow_conversion",0,100,150,30,60),
        ASCENDANCY("world_tree_ascendancy","world_tree_ascendancy",300,0,300,100,90);

        private final ResourceLocation[] ids = new ResourceLocation[CapitalResearchRanks.MAX_RANK];
        private final String title;
        private final int food,wood,ore,shadow,seconds;

        Line(String path,String titlePath,int food,int wood,int ore,int shadow,int seconds) {
            this.title="research.dreamseekers."+titlePath;
            this.food=food;this.wood=wood;this.ore=ore;this.shadow=shadow;this.seconds=seconds;
            for(int i=0;i<ids.length;i++)
                ids[i]=ResourceLocation.fromNamespaceAndPath("dreamseekers",i==0?path:path+"_"+(i+1));
        }

        public ResourceLocation id(int rank) { return ids[rank-1]; }
        public int level(Predicate<ResourceLocation> earned) {
            return CapitalResearchRanks.completed(rank->earned.test(id(rank)));
        }
        public int serverLevel(String owner) { return level(id->ResearchServerEvents.playerHasResearch(owner,id)); }
        private int percent(int rank) {
            return this==CONVERSION?CapitalResearchRanks.conversionPercent(rank):CapitalResearchRanks.ascendancyPercent(rank);
        }
    }

    // Keep original public aliases, registry keys, prices and saved/receipt identities at rank one.
    public static final ResourceLocation CONVERSION_ID=Line.CONVERSION.id(1), ASCENDANCY_ID=Line.ASCENDANCY.id(1);
    public static ReceiptBackedResearchItem CONVERSION,ASCENDANCY;
    private DreamEconomyResearch(){}

    public static void register(DreamAnchor host) {
        if(CONVERSION!=null)throw new IllegalStateException("Dream economy research already registered");
        for(Line line:Line.values())for(int rank=1;rank<=CapitalResearchRanks.MAX_RANK;rank++) {
            var item=new DreamResearchItem(line,rank);
            Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,item.id(),item);
            host.productions.add(item,line==Line.CONVERSION?Keybindings.abilitySlot2:Keybindings.abilitySlot3);
            if(rank==1) {
                if(line==Line.CONVERSION)CONVERSION=item;else ASCENDANCY=item;
            }
        }
    }

    public static boolean eligibleHost(ProductionPlacement p,boolean ascended) {
        return p.getBuilding() instanceof DreamAnchor tree
            &&DreamPassiveEconomy.isDreamOwner(PlayerServerEvents.getRTSPlayer(p.ownerName))
            &&(!ascended||tree.getUpgradeLevel(p)==1);
    }

    private static final class DreamResearchItem extends ReceiptBackedResearchItem {
        private final Line line;
        private final int rank;

        DreamResearchItem(Line line,int rank) {
            super(line.id(rank),ResourceCost.Research(line.food*rank,line.wood*rank,line.ore*rank,line.seconds*rank),
                Map.of(DreamPassiveEconomy.SHADOW,line.shadow*rank),DreamPassiveEconomy.SHADOW_ICON,
                (rank==1?line.title:"research.dreamseekers."+line.id(rank).getPath()),line.title+".desc",p->eligibleHost(p,line==Line.ASCENDANCY));
            this.line=line;this.rank=rank;
        }

        // A spent rank is still a valid *host* so the shared service returns its normal duplicate status.
        @Override public boolean validHostAndPrerequisite(ProductionPlacement p) {
            return super.validHostAndPrerequisite(p) && line.serverLevel(p.ownerName)>=rank-1;
        }
        @Override public boolean acceptsPaidCompletion(ProductionPlacement p,ActiveProduction a) {
            return CapitalResearchRanks.isNext(line.serverLevel(p.ownerName),rank);
        }
        @Override public boolean isQueuedPurchaseObsolete(ProductionPlacement p,ActiveProduction a) {
            return !acceptsPaidCompletion(p,a);
        }
        // Reserve this family across all of the owner's capitals, not the other independent family.
        @Override public boolean itemIsBeingProduced(boolean client,String owner) {
            var buildings=client?BuildingClientEvents.getBuildings():BuildingServerEvents.getBuildings();
            for(var host:buildings)if(host instanceof ProductionPlacement p&&owner.equals(p.ownerName))
                for(var active:p.productionQueue)
                    if(!active.completed && active.item instanceof DreamResearchItem other && other.line==line)return true;
            return false;
        }

        private int clientLevel(ProductionPlacement p) {
            return ClientRank.forOwner(line,p.ownerName);
        }

        @Override protected java.util.List<net.minecraft.util.FormattedCharSequence> liveTooltip(ProductionPlacement p) {
            var lines=new ArrayList<Component>();
            int current=clientLevel(p);
            lines.add(Component.translatable(line.title));
            lines.add(Component.translatable("research.dreamseekers.capital_level",current,CapitalResearchRanks.MAX_RANK));
            lines.add(Component.translatable(line.title+".desc"));
            if(current==CapitalResearchRanks.MAX_RANK) {
                lines.add(Component.translatable("research.dreamseekers.wind_max"));
            } else {
                lines.add(Component.translatable("research.dreamseekers.capital_effect",line.percent(current),line.percent(rank)));
                var cost=getCost(true,p.ownerName);
                lines.add(Component.translatable("research.economy.cost",cost.food,cost.wood,cost.ore,
                    ProductionWorkRates.estimatedSeconds(cost.ticks,ProductionWorkRates.percent(p,this))));
                lines.add(Component.translatable("research.economy.extra_cost",resourceLabel(DreamPassiveEconomy.SHADOW),line.shadow*rank));
            }
            lines.add(Component.translatable(line==Line.ASCENDANCY?"research.dreamseekers.requires_ascended_tree":"research.dreamseekers.requires_dream_owner"));
            if(current<CapitalResearchRanks.MAX_RANK)lines.add(Component.translatable("research.economy.refund_notice"));
            return wrapTooltip(lines);
        }

        @Override public StartProductionButton getStartButton(ProductionPlacement p,Keybinding key) {
            var button=super.getStartButton(p,key);
            var nativeEnabled=button.isEnabled;
            // The client caches the buttons, not their visibility: a completion or reconnect promotes
            // the existing slot without reselecting/rebuilding. A stale click cannot buy the next rank.
            button.isHidden=()->!CapitalResearchRanks.displayed(clientLevel(p),rank);
            button.isEnabled=()->{
                if(!ResearchQueueClient.ready(p))ResearchQueueClient.query(p); // rate-limited bootstrap/reconnect
                return CapitalResearchRanks.isNext(clientLevel(p),rank)&&nativeEnabled.get()
                    &&DreamPassiveEconomy.isDreamOwner(PlayerClientEvents.getRTSPlayer(p.ownerName))
                    &&(line!=Line.ASCENDANCY||p.getBuilding().getUpgradeLevel(p)==1);
            };
            button.bottomLeftText=()->clientLevel(p)+"/"+CapitalResearchRanks.MAX_RANK;
            button.onLeftClick=()->{if(button.isEnabled.get())ResearchQueueClient.start(p,this);};
            return button;
        }
    }

    /** The dedicated server can register/tick research without loading LocalPlayer or Minecraft. */
    private static final class ClientRank {
        static int forOwner(Line line,String owner) {
            var local=net.minecraft.client.Minecraft.getInstance().player;
            return local!=null&&local.getName().getString().equals(owner)?line.level(ResearchClient::hasResearch):0;
        }
    }
}
