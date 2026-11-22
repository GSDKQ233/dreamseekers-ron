// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.EnumMap;
import java.util.UUID;

/** Persistent cooldown/replay counters; short effects intentionally are never serialized. */
public final class SkillState {
    private final SkillUnit source;
    private final EnumMap<UnitSkill,Long> cooldowns=new EnumMap<>(UnitSkill.class);
    private final EnumMap<UnitSkill,Boolean> autocast=new EnumMap<>(UnitSkill.class);
    private long revision;
    private long lastFeedback=Long.MIN_VALUE, lastSync=Long.MIN_VALUE;
    // No decision, target or timer is saved. One native entity tick owns each attempted decision.
    private long lastDecision=Long.MIN_VALUE, observedCommand=Long.MIN_VALUE, commandChangedAt=Long.MIN_VALUE;
    private static final int DECISION_INTERVAL=10;
    private UUID lastRecipient;
    public SkillState(SkillUnit source) { this.source=source; }
    private long now() { return source.mob().level().getGameTime(); }
    public long revision() { return revision; }
    public int cooldownTicks(UnitSkill skill) {
        return (int)Math.max(0,Math.min(skill.cooldownTicks(),cooldowns.getOrDefault(skill,0L)-now()));
    }
    public boolean autocastEnabled(UnitSkill skill) {
        return skill!=null&&SkillService.supports(source,skill)&&skill.supportsAutocast()
                &&autocast.getOrDefault(skill,skill.defaultAutocast());
    }
    void changeAutocast(UnitSkill skill,boolean enabled) { autocast.put(skill,enabled);revision++; }
    public boolean canAdvance() { return revision<Long.MAX_VALUE; }
    void commit(UnitSkill skill) {
        cooldowns.put(skill,now()+skill.cooldownTicks());revision++;
        SkillCastAnimation.play(source.mob(),skill);
    }
    /** Only a matching server-created successful-cast profile can shorten this new cooldown. */
    void commit(UnitSkill skill,SkillAmplification.Profile profile) {
        int ticks=profile!=null&&profile.matches(source,skill)?profile.cooldownTicks():skill.cooldownTicks();
        cooldowns.put(skill,now()+ticks);revision++;
        SkillCastAnimation.play(source.mob(),skill);
    }
    public boolean allowFeedback() {
        if (lastFeedback!=Long.MIN_VALUE && now()-lastFeedback<5) return false;
        lastFeedback=now();return true;
    }
    public void ownershipChanged() {
        if (source.mob().level().isClientSide()) return;
        if (revision<Long.MAX_VALUE) revision++;
        commandChangedAt=now();
        SkillEffects.clearForOwnerChange(source.mob()); // no cooldown refund or effect completion callback
        detach();
    }
    public void tick() {
        if (!(source.mob().level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        UnitSkill primary=SkillService.skillFor(source);
        if(primary==null)return;
        ServerPlayer owner=level.getServer().getPlayerList().getPlayerByName(source.getOwnerName());
        boolean autoCast=decisionDue(primary,level.getGameTime())&&SkillService.autocast(source,owner);
        // A skill may have dispatched Forge callbacks. Never send an old connection its replacement's state.
        if (owner==null || owner.serverLevel()!=level || level.getServer().getPlayerList().getPlayer(owner.getUUID())!=owner
                ||!source.getOwnerName().equals(owner.getGameProfile().getName()) || !source.mob().isAlive()) { detach();return; }
        if (lastRecipient!=null && !lastRecipient.equals(owner.getUUID())) detach();
        if (autoCast || lastRecipient==null || now()-lastSync>=10) sync(owner);
    }
    private boolean decisionDue(UnitSkill primary,long time) {
        long command=source.rts().commandRevision;
        if(observedCommand!=Long.MIN_VALUE && observedCommand!=command)commandChangedAt=time;
        observedCommand=command;
        if(lastDecision==time)return false; // also closes same-tick reentry during a Forge callback
        // Jelly's established life-saving reaction is per-tick and compatible with movement/Shift.
        // All new policies yield to even an already-dispatched STOP on this tick; large groups
        // spread their subsequent scans over half a second instead of scanning in lockstep.
        if(primary!=UnitSkill.JELLY_FLOW && (commandChangedAt==time
                ||Math.floorMod(time,DECISION_INTERVAL)!=Math.floorMod(source.mob().getId(),DECISION_INTERVAL)))return false;
        lastDecision=time;
        return true;
    }
    public void sync(ServerPlayer owner) {
        if (owner==null || owner.serverLevel()!=source.mob().level()
                || !source.getOwnerName().equals(owner.getGameProfile().getName())) return;
        for(UnitSkill skill:SkillService.skillsFor(source))SkillNetwork.send(owner,new SkillNetwork.Sync(skill,source.mob().getId(),
                source.mob().getUUID(),revision,cooldownTicks(skill),autocastEnabled(skill)));
        lastRecipient=owner.getUUID();lastSync=now();
    }
    public void detach() {
        if (source.mob().level() instanceof ServerLevel level && lastRecipient!=null) {
            ServerPlayer old=level.getServer().getPlayerList().getPlayer(lastRecipient);
            if (old!=null) SkillNetwork.send(old,SkillNetwork.Sync.clear(source));
        }
        lastRecipient=null;lastSync=Long.MIN_VALUE;
    }
    public void save(CompoundTag root) {
        CompoundTag tag=new CompoundTag();tag.putLong("Revision",revision);
        for (UnitSkill skill:UnitSkill.values()) tag.putLong(skill.name(),cooldowns.getOrDefault(skill,0L));
        CompoundTag preferences=new CompoundTag();
        // Absence means the declared default. In particular, never turn an explicit false into
        // a missing key. Older releases wrote all supported keys; those values remain authoritative.
        for(var preference:autocast.entrySet())preferences.putBoolean(preference.getKey().name(),preference.getValue());
        tag.put("Autocast",preferences);
        root.put("DreamseekersUnitSkills",tag);
    }
    public void load(CompoundTag root) {
        SkillEffects.clear(source.mob());
        CompoundTag tag=root.getCompound("DreamseekersUnitSkills");
        // Reloads can also target a still-live object. Never make an old preference packet fresh
        // again by reading an older saved revision into the same id/UUID.
        long previous=Math.max(revision,tag.getLong("Revision"));
        revision=previous==Long.MAX_VALUE?previous:previous+1;
        cooldowns.clear();
        for (UnitSkill skill:UnitSkill.values()) cooldowns.put(skill,
                Math.max(now(),Math.min(now()+skill.cooldownTicks(),tag.getLong(skill.name()))));
        autocast.clear();CompoundTag preferences=tag.getCompound("Autocast");
        for(UnitSkill skill:SkillService.skillsFor(source))if(skill.supportsAutocast()&&preferences.contains(skill.name(),Tag.TAG_BYTE))
            autocast.put(skill,preferences.getBoolean(skill.name()));
        observedCommand=source.rts().commandRevision;commandChangedAt=now();
        lastRecipient=null;lastSync=Long.MIN_VALUE;lastFeedback=Long.MIN_VALUE;
        SkillEffects.removeModifiers(source.mob());
    }
}
