package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.ResearchClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Predicate;

/** Narrow extension point for the five newly-authorized research purchases, not legacy research. */
public class ReceiptBackedResearchItem extends ProductionItem {
    private final ResourceLocation id,icon;
    private final Map<ResourceLocation,Integer> extensions;
    private final Predicate<ProductionPlacement> prerequisite;
    private final String title,description;
    public ReceiptBackedResearchItem(ResourceLocation id,ResourceCost cost,Map<ResourceLocation,Integer> extensions,
            ResourceLocation icon,String title,String description,Predicate<ProductionPlacement> prerequisite) {
        super(cost,ProdDupeRule.DISALLOW);this.workKind=WorkKind.RESEARCH;this.id=id;this.extensions=Map.copyOf(extensions);
        this.icon=icon;this.title=title;this.description=description;this.prerequisite=prerequisite;
    }
    public ResourceLocation id(){return id;}
    public boolean hasCompleted(String owner){return com.solegendary.reignofnether.research.ResearchServerEvents.playerHasResearch(owner,id);}
    /** Pure same-task precondition and state commit hooks for bounded addon progression. */
    /** Terminal quote/predecessor invalidation only; temporary host prerequisites must not use this hook. */
    public boolean isQueuedPurchaseObsolete(ProductionPlacement p,ActiveProduction a){return false;}
    public boolean acceptsPaidCompletion(ProductionPlacement p,ActiveProduction a){return true;}
    public void commitPaidCompletion(ProductionPlacement p,ActiveProduction a,Resources account){}
    public void publishPaidCompletion(ProductionPlacement p,ActiveProduction a){com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(p.ownerName,id);}

    @Override public String getItemName(){return id.toString();}
    public ResourceTransaction.Cost quote(ProductionPlacement p) {
        var c=getCost(false,p.ownerName);return new ResourceTransaction.Cost(c.food,c.wood,c.ore,c.emerald,extensions);
    }
    public boolean validHostAndPrerequisite(ProductionPlacement p) {
        return p.getBuilding() instanceof ProductionBuilding host && host.productions.get().contains(this)
            &&PlayerServerEvents.getRTSPlayer(p.ownerName)!=null&&prerequisite.test(p);
    }
    @Override public boolean reportsHeadPause(){return true;}
    @Override public boolean tick(ProductionPlacement p,ActiveProduction a) {
        if(p.getLevel().isClientSide()){
            if(!a.pause().paused() && a.ticksLeft>0)ProductionWorkRates.advance(p,a,1);
            return false;
        }
        if(!ReceiptResearchService.bindingValid(p,a))return a.pauseBecause("research.economy.invalid_account");
        if(!p.isBuilt || p.getHealth()<=0 || p.shouldBeDestroyed() || !validHostAndPrerequisite(p))
            return a.pauseBecause("research.economy.prerequisite_pause");
        if(a.ticksLeft>0)ProductionWorkRates.advance(p,a,1);
        if(a.ticksLeft<=0&&!a.completed) {
            if(ReceiptResearchService.complete(p,a))return true;
            // A completed research from another legitimate source must not strand this queue forever.
            ReceiptResearchService.detachAndRefund(p,a);
        }
        return false;
    }
    /** Use registered resource language keys, retaining the original ID if an addon has no translation. */
    public static Component resourceLabel(ResourceLocation id) {
        var definition=ResourceExtensionRegistry.get(id);
        String key=definition!=null?definition.translationKey():"resource."+id.getNamespace()+"."+id.getPath();
        return Component.translatableWithFallback(key,id.toString());
    }
    /** Pure current-client quote/rate rendering: no query, purchase or button rebuild. */
    protected java.util.List<net.minecraft.util.FormattedCharSequence> liveTooltip(ProductionPlacement p) {
        var lines=new ArrayList<Component>();
        lines.add(Component.translatable(title));
        lines.add(Component.translatable(description));
        var c=getCost(true,p.ownerName);
        lines.add(Component.translatable("research.economy.cost",c.food,c.wood,c.ore,ProductionWorkRates.estimatedSeconds(c.ticks,ProductionWorkRates.percent(p,this))));
        for(var e:extensions.entrySet())lines.add(Component.translatable("research.economy.extra_cost",resourceLabel(e.getKey()),e.getValue()));
        lines.add(Component.translatable("research.economy.refund_notice"));
        return wrapTooltip(lines);
    }
    /** Logical-text only. Subclasses may wrap their own additional components without
     * reconstructing or reordering the already-visual lines returned by super.liveTooltip. */
    protected static java.util.List<net.minecraft.util.FormattedCharSequence> wrapTooltip(List<Component> components){
        return ClientTooltip.wrap(components);
    }
    /** Runtime client path only; research registration, quotes and server ticks never load it. */
    private static final class ClientTooltip {
        static java.util.List<net.minecraft.util.FormattedCharSequence> wrap(List<Component> components){
            var mc=net.minecraft.client.Minecraft.getInstance();
            int width=ResearchTooltipLayout.wrapWidth(mc.getWindow().getGuiScaledWidth());
            var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();
            for(Component component:components)lines.addAll(mc.font.split(component,width));
            return lines;
        }
    }
    @Override public StartProductionButton getStartButton(ProductionPlacement p,Keybinding key) {
        ResearchQueueClient.query(p);
        var lines=liveTooltip(p);
        var button=new StartProductionButton(title,icon,key,()->ResearchClient.hasResearch(id),
            ()->p.isBuilt&&ResearchQueueClient.ready(p)&&!itemIsBeingProduced(p.ownerName),lines,this) {
            @Override public void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY) {
                this.tooltipLines=ReceiptBackedResearchItem.this.liveTooltip(p);
                super.renderTooltip(graphics,mouseX,mouseY);
            }
        };
        button.onLeftClick=()->ResearchQueueClient.start(p,this);return button;
    }
    @Override public StopProductionButton getCancelButton(ProductionPlacement p,boolean first) {
        var button=new StopProductionButton(Component.translatable(title).getString(),icon,p,this,first);
        button.tooltipLines=java.util.List.of(Component.translatable(title).getVisualOrderText(),
            Component.translatable("research.economy.refund_notice").getVisualOrderText());
        button.onLeftClick=first && !p.productionQueue.isEmpty() && p.productionQueue.get(0).item==this
                ? ResearchQueueClient.cancelClick(p,this,p.productionQueue.get(0)) : ()->ResearchQueueClient.query(p);
        return button;
    }
    @Override public StopProductionButton getCancelButton(ProductionPlacement p,ActiveProduction row,boolean first) {
        var button=getCancelButton(p,first);
        button.onLeftClick=ResearchQueueClient.cancelClick(p,this,row);
        return button;
    }
}
