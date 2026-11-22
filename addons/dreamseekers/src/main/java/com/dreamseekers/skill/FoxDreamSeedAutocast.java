// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.spore.JungleSporeCarrier;
import com.dreamseekers.spore.SporeEffects;
import com.dreamseekers.spore.SporeLease;
import com.dreamseekers.unit.GoldenFoxUnit;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** A cleanse, not an order to scatter spores over the battlefield. Neither target discovery nor
 * ground preview creates entities, extends vision, moves the fox or registers lasting work. */
public final class FoxDreamSeedAutocast {
    private FoxDreamSeedAutocast() { }
    private static final int PATIENT_RANGE=SporeLease.CAST_RANGE+(int)SporeLease.RADIUS;
    private static final int ENGAGEMENT_RANGE=12, RECENT_HIT_RANGE=10, MAX_PATIENTS=4, MAX_CELL_TESTS=24;
    private static final int[][] NEAR={{0,0},{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};

    private enum Combat { FIGHTING, HIT, TARGETED }

    /** Only this tick's particular debuff, patient, enemy and chosen cell can authorize this birth.
     * A cancelled Join cannot leave behind a queued cast for a replacement patient or enemy. */
    public static final class Plan {
        private final GoldenFoxUnit source;
        private final ServerPlayer viewer;
        private final SporeEffects.Need need;
        private final SkillIdentity.Stamp enemy;
        private final Combat reason;
        private final BlockPos point;
        private final long tick;
        private Plan(GoldenFoxUnit source,ServerPlayer viewer,Patient patient,BlockPos point) {
            this.source=source;this.viewer=viewer;need=patient.need;enemy=patient.enemy;reason=patient.reason;
            this.point=point.immutable();tick=viewer.serverLevel().getGameTime();
        }
        public LivingEntity ally() { return need.ally(); }
        public BlockPos point() { return point; }
        public boolean belongsTo(ServerPlayer owner,GoldenFoxUnit caster) { return viewer==owner&&source==caster; }
        public boolean current() {
            return source.level()==viewer.serverLevel()&&viewer.serverLevel().getGameTime()==tick
                    &&SkillService.currentCommander(source,viewer)&&SkillAutocastPolicy.observable(viewer,source)
                    &&need.current()&&enemy.valid()&&observedPatient(source,viewer,ally())
                    &&engaged(source,viewer,ally(),enemy.entity(),reason)&&withinRadii(source,ally(),point)
                    &&groundSight(source,ally(),point);
        }
        /** The preview is never the final radius/LOS authority. Ask the actual registered mob
         * (including any Forge changes to its dimensions/eye height) before publishing and paying. */
        public boolean reaches(JungleSporeCarrier carrier) {
            return current()&&carrier.level()==source.level()&&CoordinatedMark.visible(viewer,carrier)
                    &&carrier.position().equals(Vec3.atBottomCenterOf(point))
                    &&carrier.distanceToSqr(ally())<=SporeLease.RADIUS*SporeLease.RADIUS
                    &&BeetleMotherCommandAura.hasLoadedLineOfSight(viewer.serverLevel(),source,source.getEyePosition(),carrier.getEyePosition())
                    &&SkillAutocastPolicy.sees(carrier,ally());
        }
    }
    private record Patient(SporeEffects.Need need,SkillIdentity.Stamp enemy,Combat reason,List<BlockPos> cells) { }

    @Nullable public static Plan select(GoldenFoxUnit source,ServerPlayer viewer) {
        if(source==null||viewer==null||source.level()!=viewer.serverLevel()||!viewer.serverLevel().getServer().isSameThread()
                ||source.isCasting()||!SkillService.currentCommander(source,viewer)||!SkillAutocastPolicy.observable(viewer,source))return null;
        // One capped native section query, including actual attackers of nearby allies so a stunned
        // patient need not be capable of attacking, or still have a target goal, to be rescued.
        List<LivingEntity> local=SkillAutocastPolicy.local(source,PATIENT_RANGE+ENGAGEMENT_RANGE,t-> {
            if(t==source)return false;
            if(SkillEffects.friendly(source,t))
                return nearPatient(source,t)&&SkillAutocastPolicy.observable(viewer,t);
            if(!(t instanceof AttackerUnit)||!CoordinatedMark.enemy(source,t))return false;
            var recipient=SkillAutocastPolicy.focus(t);
            return recipient!=null&&recipient!=source&&nearPatient(source,recipient)
                    &&SkillEffects.friendly(source,recipient)&&SkillAutocastPolicy.observable(viewer,recipient)
                    &&SkillAutocastPolicy.observable(viewer,t);
        }).stream().sorted(Comparator.comparingDouble((LivingEntity t)->source.distanceToSqr(t))
                .thenComparing(t->t.getUUID().toString())).toList();
        Map<LivingEntity,List<LivingEntity>> incoming=new IdentityHashMap<>();
        for(var t:local)if(!SkillEffects.friendly(source,t)&&t instanceof AttackerUnit) {
            var victim=SkillAutocastPolicy.focus(t);
            if(victim!=null)incoming.computeIfAbsent(victim,k->new ArrayList<>()).add(t);
        }
        List<Patient> patients=new ArrayList<>();
        for(var ally:local) {
            if(!observedPatient(source,viewer,ally))continue;
            Patient patient=patient(source,viewer,ally,SkillAutocastPolicy.focus(ally),Combat.FIGHTING);
            if(patient==null)patient=patient(source,viewer,ally,SkillAutocastPolicy.recentAttacker(ally),Combat.HIT);
            if(patient==null)for(var attacker:incoming.getOrDefault(ally,List.of())) {
                patient=patient(source,viewer,ally,attacker,Combat.TARGETED);
                if(patient!=null)break;
            }
            if(patient!=null)patients.add(patient);
            if(patients.size()>=MAX_PATIENTS)break;
        }
        // Try each patient's nearest plausible cell before spending the remaining small terrain
        // budget on alternatives. An inaccessible front-line ally cannot monopolize the search.
        int tested=0;
        for(int i=0;tested<MAX_CELL_TESTS;i++) {
            boolean any=false;
            for(var p:patients)if(i<p.cells.size()) {
                any=true;
                var cell=p.cells.get(i);
                if(++tested>MAX_CELL_TESTS)return null;
                if(!SporeLease.previewGround(viewer,source,cell)||!groundSight(source,p.need.ally(),cell))continue;
                var plan=new Plan(source,viewer,p,cell);
                if(plan.current())return plan;
            }
            if(!any)break;
        }
        return null;
    }
    @Nullable private static Patient patient(GoldenFoxUnit source,ServerPlayer viewer,LivingEntity ally,
                                              @Nullable LivingEntity enemy,Combat reason) {
        if(enemy==null||!engaged(source,viewer,ally,enemy,reason))return null;
        var need=SporeEffects.need(ally,((Unit)enemy).getOwnerName());
        if(need==null)return null;
        var cells=cells(source,ally);
        return cells.isEmpty()?null:new Patient(need,SkillIdentity.capture(enemy),reason,cells);
    }
    private static boolean nearPatient(GoldenFoxUnit source,LivingEntity ally) {
        return ally.level()==source.level()&&source.distanceToSqr(ally)<=PATIENT_RANGE*PATIENT_RANGE;
    }
    private static boolean observedPatient(GoldenFoxUnit source,ServerPlayer viewer,LivingEntity ally) {
        return ally!=source&&nearPatient(source,ally)&&SkillEffects.friendly(source,ally)
                &&SkillAutocastPolicy.observable(viewer,ally)&&SkillAutocastPolicy.sees(source,ally);
    }
    private static boolean ordinaryContact(LivingEntity fighter,LivingEntity target) {
        return fighter instanceof AttackerUnit attacker&&SkillService.blessingCombatUnit(fighter)
                &&SkillAutocastPolicy.attackReach(fighter,attacker,target);
    }
    private static boolean engaged(GoldenFoxUnit fox,ServerPlayer viewer,LivingEntity ally,LivingEntity enemy,Combat reason) {
        if(!CoordinatedMark.enemy(ally,enemy)||!CoordinatedMark.enemy(fox,enemy)
                ||!SkillAutocastPolicy.observable(viewer,enemy))return false;
        boolean evidence=switch(reason) {
            // Disarm/stun can interrupt the patient's own attacks: do not require its ability
            // to complete a hit as a prerequisite for cleansing the very effect that stopped it.
            case FIGHTING -> SkillAutocastPolicy.focus(ally)==enemy&&ordinaryContact(ally,enemy);
            case HIT -> SkillAutocastPolicy.recentAttacker(ally)==enemy&&ally.distanceToSqr(enemy)<=RECENT_HIT_RANGE*RECENT_HIT_RANGE;
            case TARGETED -> SkillAutocastPolicy.focus(enemy)==ally&&ordinaryContact(enemy,ally)&&!SkillAutocastPolicy.disabledFighter(enemy);
        };
        return evidence&&SkillAutocastPolicy.sees(fox,enemy)&&SkillAutocastPolicy.sees(ally,enemy);
    }
    private static boolean withinRadii(GoldenFoxUnit source,LivingEntity ally,BlockPos pos) {
        var feet=Vec3.atBottomCenterOf(pos);
        return source.position().distanceToSqr(feet)<=SporeLease.CAST_RANGE*SporeLease.CAST_RANGE
                &&ally.position().distanceToSqr(feet)<=SporeLease.RADIUS*SporeLease.RADIUS;
    }
    private static List<BlockPos> cells(GoldenFoxUnit source,LivingEntity ally) {
        var around=ally.blockPosition();
        var flat=new LinkedHashSet<BlockPos>();
        for(var offset:NEAR)flat.add(around.offset(offset[0],0,offset[1]));
        // An ally can be 10+3 blocks away. Try an inward point instead of requiring its own
        // possibly occupied feet to be inside the fox's cast range.
        var toward=new Vec3(source.getX()-ally.getX(),0,source.getZ()-ally.getZ()).normalize();
        for(double n:new double[]{2,2.75})flat.add(BlockPos.containing(ally.position().add(toward.scale(n))));
        var cells=new ArrayList<BlockPos>(flat.size()*3);
        for(var pos:flat)for(int y:new int[]{0,-1,1})if(withinRadii(source,ally,pos.offset(0,y,0)))cells.add(pos.offset(0,y,0));
        cells.sort(Comparator.comparingDouble((BlockPos p)->ally.position().distanceToSqr(Vec3.atBottomCenterOf(p)))
                .thenComparingDouble(p->source.position().distanceToSqr(Vec3.atBottomCenterOf(p))).thenComparing(BlockPos::compareTo));
        return cells;
    }
    private static boolean groundSight(GoldenFoxUnit source,LivingEntity ally,BlockPos point) {
        var level=(ServerLevel)source.level();
        // Normal standing eye height for the registered .2 x .2 spore; exact carrier is checked in reaches().
        var eye=Vec3.atBottomCenterOf(point).add(0,DreamseekersMod.JUNGLE_SPORE_CARRIER.get().getDimensions().height*.85F,0);
        return BeetleMotherCommandAura.hasLoadedLineOfSight(level,source,source.getEyePosition(),eye)
                &&BeetleMotherCommandAura.hasLoadedLineOfSight(level,source,eye,ally.getEyePosition());
    }
    /** Paid auto spores don't need another combat trigger. Every recipient of the initial cleanse
     * or later duration modifier does need to be observable now; a manual spore never calls this. */
    public static boolean observableRecipient(ServerPlayer viewer,JungleSporeCarrier spore,SkillIdentity.Stamp caster,LivingEntity target) {
        return caster.entity() instanceof GoldenFoxUnit source&&SkillService.currentCommander(source,viewer)
                &&SkillAutocastPolicy.observable(viewer,target)&&CoordinatedMark.visible(viewer,spore)
                &&SkillAutocastPolicy.sees(spore,target);
    }
}
