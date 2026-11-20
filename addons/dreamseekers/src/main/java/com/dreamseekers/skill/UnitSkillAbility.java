// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Presentation/input only: real skill mutation and cooldown are owned by SkillService/SkillState. */
public final class UnitSkillAbility extends Ability {
    public static final UnitSkillAbility GEL_GUARD = new UnitSkillAbility(UnitSkill.GEL_GUARD);
    public static final UnitSkillAbility JELLY_FLOW = new UnitSkillAbility(UnitSkill.JELLY_FLOW);
    public static final UnitSkillAbility FOX_BLESSING = new UnitSkillAbility(UnitSkill.FOX_BLESSING);
    public static final UnitSkillAbility FOX_DREAM_SEED = new UnitSkillAbility(UnitSkill.FOX_DREAM_SEED);
    public static final UnitSkillAbility WIND_DASH = new UnitSkillAbility(UnitSkill.WIND_DASH);
    public static final UnitSkillAbility COORDINATED_MARK = new UnitSkillAbility(UnitSkill.COORDINATED_MARK);
    public static final UnitSkillAbility FIREFLY_RUSH = new UnitSkillAbility(UnitSkill.FIREFLY_RUSH);
    public static final UnitSkillAbility BEETLE_ADVANCE = new UnitSkillAbility(UnitSkill.BEETLE_ADVANCE);
    public static final UnitSkillAbility SNAIL_SHELL_FORMATION = new UnitSkillAbility(UnitSkill.SNAIL_SHELL_FORMATION);
    public static final UnitSkillAbility STONE_HEAVY_SHOT = new UnitSkillAbility(UnitSkill.STONE_HEAVY_SHOT);
    public static final UnitSkillAbility SHADOW_CORROSION_TOUCH = new UnitSkillAbility(UnitSkill.SHADOW_CORROSION_TOUCH);
    public static final UnitSkillAbility SQUEAL_SUPPRESSION_WAVE = new UnitSkillAbility(UnitSkill.SQUEAL_SUPPRESSION_WAVE);
    public static final UnitSkillAbility TERRORBEAK_DREAD_ROAR = new UnitSkillAbility(UnitSkill.TERRORBEAK_DREAD_ROAR);
    public static final UnitSkillAbility FRIENDLY_DEBT_CONTRACT = new UnitSkillAbility(UnitSkill.FRIENDLY_DEBT_CONTRACT);
    public static final UnitSkillAbility SHADOW_HAND_GRIP = new UnitSkillAbility(UnitSkill.SHADOW_HAND_GRIP);
    public static final UnitSkillAbility ASH_EMBER_LANDING = new UnitSkillAbility(UnitSkill.ASH_EMBER_LANDING);
    public static final UnitSkillAbility SHADOW_GOLEM_STRIKE = new UnitSkillAbility(UnitSkill.SHADOW_GOLEM_STRIKE);
    public static final UnitSkillAbility MOTHER_NEST = new UnitSkillAbility(UnitSkill.MOTHER_NEST);
    public static final UnitSkillAbility MOTHER_BROOD = new UnitSkillAbility(UnitSkill.MOTHER_BROOD);
    public static final UnitSkillAbility WEEPING_ECHOES = new UnitSkillAbility(UnitSkill.WEEPING_ECHOES);
    public static final UnitSkillAbility AARONCOS_LEFT_PRESS = new UnitSkillAbility(UnitSkill.AARONCOS_LEFT_PRESS);
    public static final UnitSkillAbility AARONCOS_RIGHT_SHOCK = new UnitSkillAbility(UnitSkill.AARONCOS_RIGHT_SHOCK);
    public static final UnitSkillAbility AARONCOS_RIGHT_VORTEX = new UnitSkillAbility(UnitSkill.AARONCOS_RIGHT_VORTEX);
    private final UnitSkill skill;
    private UnitSkillAbility(UnitSkill skill) {
        super(null, skill.cooldownTicks(), skill.range(), 0, true, true);
        this.skill=skill;showRangeCircle=false;showRangeLine=false;showRadiusCircle=false;
    }
    public UnitSkill skill() { return skill; }
    @Override public boolean shouldResetBehaviours() { return false; }
    @Override public float getCooldown(Unit unit) {
        if(!(unit instanceof SkillUnit source)||!SkillService.supports(source,skill))return cooldownMax;
        return source.mob().level().isClientSide()?SkillClient.cooldown(source,skill):source.rts().skills().cooldownTicks(skill);
    }
    @Override public boolean isAutocasting(Unit unit) {
        if(!(unit instanceof SkillUnit source)||!SkillService.supports(source,skill))return false;
        return source.mob().level().isClientSide()?SkillClient.autocastEnabled(source,skill):source.rts().skills().autocastEnabled(skill);
    }
    private java.util.List<net.minecraft.util.FormattedCharSequence> tooltip(SkillUnit source) {
        String prefix="ability.dreamseekers."+skill.id();
        var potency=new SkillPotencyView(SkillClient.amplificationTier(source,skill));
        String primary=skill==UnitSkill.FOX_BLESSING?potency.foxPercent():potency.shieldOrHealingTotal();
        String derived=skill==UnitSkill.GEL_GUARD?potency.reboundPercent():potency.tidePercent();
        boolean extended=SkillClient.lightExtension(source,skill);
        double duration=(extended&&(skill==UnitSkill.GEL_GUARD||skill==UnitSkill.FOX_BLESSING)?skill.durationTicks()*1.2:skill.durationTicks())/20.0;
        double cooldown=(extended?skill.cooldownTicks()*.9:skill.cooldownTicks())/20.0;
        int range=skill==UnitSkill.STONE_HEAVY_SHOT&&source instanceof com.dreamseekers.unit.StoneSpiritThrowerUnit stone?StoneHeavyShot.range(stone):skill.range();
        java.util.List<Component> tooltip=new java.util.ArrayList<>(List.of(
                Component.translatable(prefix).withStyle(Style.EMPTY.withBold(true)),
                Component.translatable(prefix+".tooltip1",skill==UnitSkill.FIREFLY_RUSH?35:skill==UnitSkill.BEETLE_ADVANCE?25:range,duration,cooldown,primary,potency.healingPulse()),
                Component.translatable(prefix+".tooltip2",derived,extended?4.8:4.0),
                Component.translatable(prefix+".tooltip3")));
        if(skill==UnitSkill.MOTHER_BROOD||skill==UnitSkill.AARONCOS_RIGHT_SHOCK||skill==UnitSkill.AARONCOS_RIGHT_VORTEX)
            for(int line=4;line<=5;line++)tooltip.add(Component.translatable(prefix+".tooltip"+line));
        if(skill==UnitSkill.FOX_DREAM_SEED)for(int line=4;line<=8;line++)tooltip.add(Component.translatable(prefix+".tooltip"+line));
        if(skill==UnitSkill.BEETLE_ADVANCE||skill==UnitSkill.SNAIL_SHELL_FORMATION||skill==UnitSkill.STONE_HEAVY_SHOT||skill==UnitSkill.SHADOW_CORROSION_TOUCH||skill==UnitSkill.SQUEAL_SUPPRESSION_WAVE||skill==UnitSkill.TERRORBEAK_DREAD_ROAR||skill==UnitSkill.FRIENDLY_DEBT_CONTRACT||skill==UnitSkill.SHADOW_HAND_GRIP||skill==UnitSkill.ASH_EMBER_LANDING||skill==UnitSkill.SHADOW_GOLEM_STRIKE)for(int line=4;line<=6;line++)tooltip.add(Component.translatable(prefix+".tooltip"+line));
        if(SkillPotencyView.supports(skill)) {
            String key=!SkillClient.hasCurrentState(source,skill)?"unavailable":potency.tier()==0?"baseline":"active";
            tooltip.add(Component.translatable("ability.dreamseekers.unit_skill.amplification."+key,potency.tier(),potency.multiplier()));
        }
        if(SkillPotencyView.supports(skill))tooltip.add(Component.translatable("ability.dreamseekers.unit_skill.extension."+(!SkillClient.hasCurrentState(source,skill)?"unknown":extended?"active":"baseline"),skill==UnitSkill.JELLY_FLOW?(extended?4.8:4.0):duration,cooldown));
        if(skill==UnitSkill.MOTHER_NEST||skill==UnitSkill.MOTHER_BROOD)tooltip.add(Component.translatable("ability.dreamseekers.mother_nest.state."+(!SkillClient.hasCurrentState(source,UnitSkill.MOTHER_NEST)?"unknown":SkillClient.motherNestActive(source)?"active":"mobile")));
        if(skill==UnitSkill.AARONCOS_LEFT_PRESS||skill==UnitSkill.AARONCOS_RIGHT_SHOCK)tooltip.add(Component.translatable("ability.dreamseekers.hand_harmony."+(!SkillClient.hasCurrentState(source,skill)?"unknown":SkillClient.handHarmonyActive(source,skill)?"active":"inactive")));
        return ClientTooltip.wrap(tooltip);
    }
    /** Logical components are split exactly once; no already-visual FCS is reconstructed.
     * Only the client button/hover path invokes this nested helper, never static ability setup. */
    private static final class ClientTooltip {
        static java.util.List<net.minecraft.util.FormattedCharSequence> wrap(List<Component> components){
            var mc=net.minecraft.client.Minecraft.getInstance();
            int width=SkillTooltipLayout.wrapWidth(mc.getWindow().getGuiScaledWidth());
            var lines=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
            for(Component component:components)lines.addAll(mc.font.split(component,width));
            return lines;
        }
    }
    @Override public AbilityButton getButton(Keybinding key,Unit unit) {
        SkillUnit source=(SkillUnit)unit;
        ResourceLocation icon = skill==UnitSkill.AARONCOS_LEFT_PRESS ? com.dreamseekers.DreamseekersMod.AARONCOS_LEFT_HAND_ICON
                : skill==UnitSkill.AARONCOS_RIGHT_SHOCK||skill==UnitSkill.AARONCOS_RIGHT_VORTEX ? com.dreamseekers.DreamseekersMod.AARONCOS_RIGHT_HAND_ICON
                : skill==UnitSkill.MOTHER_BROOD||skill==UnitSkill.MOTHER_NEST ? com.dreamseekers.DreamseekersMod.BEETLE_MOTHER_ICON
                : skill==UnitSkill.WEEPING_ECHOES ? com.dreamseekers.DreamseekersMod.WEEPING_SQUEAL_GHOST_ICON
                : ResourceLocation.fromNamespaceAndPath("dreamseekers","textures/icons/skill_"+skill.id()+".png");
        return SnapshotSkillButton.bind(new AbilityButton(skill.id(),icon,
                key,()->SkillClient.isArmed(source,skill)||isAutocasting(unit)||skill==UnitSkill.MOTHER_NEST&&SkillClient.motherNestActive(source),()->false,()->(skill!=UnitSkill.MOTHER_NEST||com.dreamseekers.unitprogression.AdvancedUnitResearch.unlocked(source.getOwnerName(),com.dreamseekers.unitprogression.AdvancedUnitResearch.MOTHER_NEST_ID,true))&&(skill!=UnitSkill.AARONCOS_RIGHT_VORTEX||com.dreamseekers.unitprogression.ExpansionResearch.unlocked(source.getOwnerName(),com.dreamseekers.unitprogression.ExpansionResearch.AARONCOS_VORTEX_ID,true)),
                ()->SkillClient.arm(source,skill),()->SkillClient.rightClick(source,skill),
                tooltip(source),this,unit) {
            @Override public void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY) {
                tooltipLines=UnitSkillAbility.this.tooltip(source);super.renderTooltip(graphics,mouseX,mouseY);
            }
        },()->SkillClient.readiness(source,skill));
    }
}
