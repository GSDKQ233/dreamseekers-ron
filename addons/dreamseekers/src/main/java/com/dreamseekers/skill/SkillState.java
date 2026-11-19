// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import net.minecraft.nbt.CompoundTag;
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
    }
    /** Only a matching server-created successful-cast profile can shorten this new cooldown. */
    void commit(UnitSkill skill,SkillAmplification.Profile profile) {
        int ticks=profile!=null&&profile.matches(source,skill)?profile.cooldownTicks():skill.cooldownTicks();
        cooldowns.put(skill,now()+ticks);revision++;
    }
    public boolean allowFeedback() {
        if (lastFeedback!=Long.MIN_VALUE && now()-lastFeedback<5) return false;
        lastFeedback=now();return true;
    }
    public void ownershipChanged() {
        if (source.mob().level().isClientSide()) return;
        if (revision<Long.MAX_VALUE) revision++;
        SkillEffects.clearForOwnerChange(source.mob()); // no cooldown refund or effect completion callback
        detach();
    }
    public void tick() {
        if (!(source.mob().level() instanceof ServerLevel level) || !SkillService.supports(source)) return;
        boolean autoCast=SkillService.autocast(source);
        ServerPlayer owner=level.getServer().getPlayerList().getPlayerByName(source.getOwnerName());
        if (owner==null || owner.serverLevel()!=level || !source.mob().isAlive()) { detach();return; }
        if (lastRecipient!=null && !lastRecipient.equals(owner.getUUID())) detach();
        if (autoCast || lastRecipient==null || now()-lastSync>=10) sync(owner);
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
        for(UnitSkill skill:UnitSkill.values())if(SkillService.supports(source,skill)&&skill.supportsAutocast())preferences.putBoolean(skill.name(),autocastEnabled(skill));
        tag.put("Autocast",preferences);
        root.put("DreamseekersUnitSkills",tag);
    }
    public void load(CompoundTag root) {
        SkillEffects.clear(source.mob());
        CompoundTag tag=root.getCompound("DreamseekersUnitSkills");
        long saved=Math.max(0,tag.getLong("Revision"));revision=saved==Long.MAX_VALUE?saved:saved+1;
        cooldowns.clear();
        for (UnitSkill skill:UnitSkill.values()) cooldowns.put(skill,
                Math.max(now(),Math.min(now()+skill.cooldownTicks(),tag.getLong(skill.name()))));
        autocast.clear();CompoundTag preferences=tag.getCompound("Autocast");
        for(UnitSkill skill:UnitSkill.values())if(SkillService.supports(source,skill)&&skill.supportsAutocast())autocast.put(skill,
                preferences.contains(skill.name(),net.minecraft.nbt.Tag.TAG_BYTE)?preferences.getBoolean(skill.name()):skill.defaultAutocast());
        lastRecipient=null;lastSync=Long.MIN_VALUE;lastFeedback=Long.MIN_VALUE;
        SkillEffects.removeModifiers(source.mob());
    }
}
