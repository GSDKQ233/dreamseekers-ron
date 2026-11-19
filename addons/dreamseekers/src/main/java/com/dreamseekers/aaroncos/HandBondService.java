// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.aaroncos;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.unit.*;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Explicit owner-authorized paid pairing, separate from the obsolete reserve protocol. */
@Mod.EventBusSubscriber(modid="dreamseekers")
public final class HandBondService {
    public static final boolean AVAILABLE=true;
    public static final int PAIR_SHADOW=40, PAIR_RANGE=16, SHARE_RANGE=24, REPLACEMENT_DELAY=1800, QUIET_TICKS=200;
    public enum Result { PAIRED, UNPAIRED, INVALID_SOURCE, NOT_OWNER, LOCKED, TOO_FAR, ALREADY_PAIRED, INSUFFICIENT_RESOURCES, LIMIT, NOT_PAIRED }
    private record DeathWitness(net.minecraft.world.damagesource.DamageSource source,long tick,String combatOwner) {}
    private static final Map<LivingEntity,DeathWitness> LETHAL=new IdentityHashMap<>();
    private static final Set<net.minecraft.server.MinecraftServer> STOPPING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<net.minecraft.server.MinecraftServer> STOP_TICK_NOTED=Collections.newSetFromMap(new IdentityHashMap<>()),STOP_LEAVE_NOTED=Collections.newSetFromMap(new IdentityHashMap<>());
    static boolean stopping(ServerLevel level){return STOPPING.contains(level.getServer());}
    private HandBondService(){}
    public static boolean researched(String owner){return AVAILABLE&&DreamSpecialistResearch.unlocked(owner,DreamseekersMod.id("hand_pairing"),false);}
    static boolean exact(Entity e){return e!=null&&(e.getClass()==AaroncosLeftHandUnit.class&&e.getType()==DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get()
            ||e.getClass()==AaroncosRightHandUnit.class&&e.getType()==DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get());}
    static boolean body(LivingEntity e){
        return exact(e)&&e.isAlive()&&!e.isRemoved()&&e.isAddedToWorld()&&e.level() instanceof ServerLevel l
            &&l.getServer().isSameThread()&&l.getEntity(e.getId())==e&&l.getEntity(e.getUUID())==e&&l.hasChunkAt(e.blockPosition())
            &&Float.isFinite(e.getHealth())&&Float.isFinite(e.getMaxHealth())&&e.getMaxHealth()>0&&((ControlledAaroncosHand)e).rts().skills().canAdvance()
            &&UnitServerEvents.getAllUnits().contains(e)&&!RtsKillValueClaims.isFree(e)&&!RtsKillValueClaims.isClaimed(e)
            &&!HandBondReplacement.pending(e)&&!com.dreamseekers.summon.BoundedSummons.isTemporary(e)&&!com.solegendary.reignofnether.sandbox.SandboxEconomyFixture.has(e);
    }
    public static Result pair(ServerPlayer sender,UUID leftId,UUID rightId){
        if(sender==null||leftId==null||rightId==null||!sender.serverLevel().getServer().isSameThread())return Result.INVALID_SOURCE;
        ServerLevel level=sender.serverLevel();if(stopping(level))return Result.INVALID_SOURCE;
        if(level.getServer().getPlayerList().getPlayer(sender.getUUID())!=sender||sender.connection==null||!sender.isAlive()||sender.isRemoved())return Result.INVALID_SOURCE;
        if(!(level.getEntity(leftId) instanceof AaroncosLeftHandUnit left)||!(level.getEntity(rightId) instanceof AaroncosRightHandUnit right)
                ||!body(left)||!body(right))return Result.INVALID_SOURCE;
        String owner=sender.getGameProfile().getName();
        if(!owner.equals(left.getOwnerName())||!owner.equals(right.getOwnerName()))return Result.NOT_OWNER;
        if(!researched(owner)||!com.dreamseekers.economy.DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(owner)))return Result.LOCKED;
        if(left.distanceToSqr(right)>PAIR_RANGE*PAIR_RANGE)return Result.TOO_FAR;
        var data=HandBondData.get(level);
        if(!data.supported)return Result.LOCKED;
        if(data.forEntity(leftId)!=null||data.forEntity(rightId)!=null)return Result.ALREADY_PAIRED;
        if(data.size()>=HandBondData.MAX_BONDS)return Result.LIMIT;
        var account=ExtendedResourcesSync.account(owner);
        if(account==null||!ResourceTransaction.exchange(level.getServer(),account,account.accountEpoch,account.revision,
                new ResourceTransaction.Cost(0,0,0,Map.of(DreamPassiveEconomy.SHADOW,PAIR_SHADOW)),ResourceTransaction.Cost.ZERO).applied())return Result.INSUFFICIENT_RESOURCES;
        data.add(new HandBondData.Bond(UUID.randomUUID(),owner,account.accountEpoch,leftId,rightId));
        ExtendedResourcesSync.send(sender);
        return Result.PAIRED;
    }
    public static Result unpair(ServerPlayer sender,UUID handId){
        if(sender==null||handId==null||!sender.serverLevel().getServer().isSameThread())return Result.INVALID_SOURCE;
        if(sender.serverLevel().getServer().getPlayerList().getPlayer(sender.getUUID())!=sender||sender.connection==null||!sender.isAlive()||sender.isRemoved())return Result.INVALID_SOURCE;
        if(stopping(sender.serverLevel()))return Result.INVALID_SOURCE;
        Entity hand=sender.serverLevel().getEntity(handId);
        if(!exact(hand)||!(hand instanceof ControlledAaroncosHand unit))return Result.INVALID_SOURCE;
        if(!sender.getGameProfile().getName().equals(unit.getOwnerName()))return Result.NOT_OWNER;
        var data=HandBondData.get(sender.serverLevel());var bond=data.forEntity(handId);
        if(bond==null)return Result.NOT_PAIRED;
        if(!bond.owner.equals(sender.getGameProfile().getName()))return Result.NOT_OWNER;
        data.remove(bond);return Result.UNPAIRED;
    }
    /** Called before normal ownership mutations; a subsequent return to the old name cannot revive a bond. */
    public static void ownerChanging(Entity entity,String next){
        if(entity.isAddedToWorld()&&entity.level() instanceof ServerLevel level&&!stopping(level)&&entity instanceof ControlledAaroncosHand hand
                &&!Objects.equals(hand.getOwnerName(),next)){
            var data=HandBondData.get(level);var bond=data.forEntity(entity.getUUID());if(bond!=null)data.remove(bond);
        }
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(event.getLevel() instanceof ServerLevel level&&!stopping(level)&&exact(event.getEntity())){
            var data=HandBondData.get(level);
            // Crash/torn-save recovery: a retired UUID can never return beside its paid replacement.
            if(data.retired(event.getEntity().getUUID())){event.setCanceled(true);return;}
            var bond=data.forEntity(event.getEntity().getUUID());
            if(bond!=null){bond.lastCombat=level.getGameTime();data.setDirty();}
        }
    }
    @SubscribeEvent public static void leave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event){
        if(event.getLevel() instanceof ServerLevel level&&exact(event.getEntity())){
            var reason=event.getEntity().getRemovalReason();
            if(stopping(level)){
                if(STOP_LEAVE_NOTED.add(level.getServer()))com.mojang.logging.LogUtils.getLogger().info("Preserving paid hand bonds during shutdown entity leave: reason={}",reason);
                return;
            }
            var data=HandBondData.get(level);var bond=data.forEntity(event.getEntity().getUUID());if(bond==null)return;
            // Tracking loss without a removal reason is not positive administrative removal evidence.
            if(reason==null||reason==Entity.RemovalReason.UNLOADED_TO_CHUNK||reason==Entity.RemovalReason.UNLOADED_WITH_PLAYER)return;
            boolean confirmedDead=bond.missing==1&&bond.left.equals(event.getEntity().getUUID())||bond.missing==2&&bond.right.equals(event.getEntity().getUUID());
            if(reason!=Entity.RemovalReason.KILLED||!confirmedDead)data.remove(bond);
        }
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.LevelTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
        if(stopping(level)){
            var saved=HandBondData.get(level);
            if(saved.size()>0&&STOP_TICK_NOTED.add(level.getServer()))com.mojang.logging.LogUtils.getLogger().info("Preserving {} paid hand bonds during shutdown tick; research registry available={}",saved.size(),saved.bonds.values().stream().allMatch(b->researched(b.owner)));
            return;
        }
        LETHAL.entrySet().removeIf(e->e.getKey().level()==level&&e.getValue().tick()<level.getGameTime());
        var data=HandBondData.get(level);if(!data.supported)return;
        for(var bond:List.copyOf(data.bonds.values())){
            if(!validOwner(bond)){data.remove(bond);continue;}
            Entity left=level.getEntity(bond.left),right=level.getEntity(bond.right);
            if(left!=null&&(!(left instanceof AaroncosLeftHandUnit leftHand)||!leftHand.getOwnerName().equals(bond.owner))
                    ||right!=null&&(!(right instanceof AaroncosRightHandUnit rightHand)||!rightHand.getOwnerName().equals(bond.owner))){data.remove(bond);continue;}
            if(bond.missing!=0){
                Entity dead=bond.missing==1?left:right,remaining=bond.missing==1?right:left;
                // A cancelled death or torn entity/world save must never mint a second live hand.
                if(dead instanceof LivingEntity live&&live.isAlive()){bond.missing=0;data.setDirty();continue;}
                if(remaining instanceof LivingEntity survivor&&body(survivor)){
                    var hand=(ControlledAaroncosHand)survivor;
                    if(hand.isCasting()||((Mob)survivor).getTarget()!=null){bond.lastCombat=level.getGameTime();data.setDirty();}
                    if(dead==null&&level.getGameTime()>=bond.readyAt&&level.getGameTime()-bond.lastCombat>=QUIET_TICKS)
                        HandBondReplacement.attempt(level,data,bond,survivor);
                }
            }
        }
    }
    /** The only sharing boundary: final health write, after armor, shields and Forge callbacks.
     * Both health stores precede death callbacks; no temporary lethal write or restorative heal. */
    public static void writeDamage(LivingEntity victim,net.minecraft.world.damagesource.DamageSource source,float health){
        float before=victim.getHealth(),damage=before-health;
        if(!(victim.level() instanceof ServerLevel level)||!body(victim)||!Float.isFinite(damage)||damage<=0
                ||source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)||source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)){
            writeOwn(victim,source,health);return;
        }
        var data=HandBondData.get(level);var bond=data.forEntity(victim.getUUID());
        if(bond==null||bond.missing!=0||!validOwner(bond)||!(level.getEntity(bond.other(victim.getUUID())) instanceof LivingEntity partner)
                ||!body(partner)||!((ControlledAaroncosHand)partner).getOwnerName().equals(bond.owner)
                ||!((ControlledAaroncosHand)victim).getOwnerName().equals(bond.owner)||victim.distanceToSqr(partner)>SHARE_RANGE*SHARE_RANGE){
            writeOwn(victim,source,health);return;
        }
        float transfer=Math.min(damage/2,partner.getHealth());
        if(!Float.isFinite(transfer)||transfer<=0){writeOwn(victim,source,health);return;}
        touch(victim);touch(partner);
        // Capture existing exact provenance, never manufacture a new attacker/kill entitlement.
        var evidence=com.solegendary.reignofnether.resources.kill.EconomicSharedDamageHooks.capture(victim,source,partner);
        final long partnerOwnerRevision=((ControlledAaroncosHand)partner).rts().ownershipRevision;
        final String combatOwner=com.solegendary.reignofnether.resources.kill.EconomicSharedDamageHooks.combatOwner(victim,source);
        writeOwn(victim,source,before-(damage-transfer));
        writeOwn(partner,source,partner.getHealth()-transfer,combatOwner);
        partner.getCombatTracker().recordDamage(source,transfer);
        if(source.getEntity() instanceof LivingEntity attacker)partner.setLastHurtByMob(attacker);
        com.solegendary.reignofnether.api.HealthDamageWriters.afterReceipt(()->evidence.run(partner,()->{
            partner.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ENTITY_DAMAGE);
            if(partner.getHealth()<=0&&!partner.isRemoved()){
                boolean same=((ControlledAaroncosHand)partner).rts().ownershipRevision==partnerOwnerRevision
                    &&((ControlledAaroncosHand)partner).getOwnerName().equals(bond.owner)&&validOwner(bond);
                partner.die(same?source:partner.damageSources().genericKill());
            }
        }));
    }
    private static void writeOwn(LivingEntity victim,net.minecraft.world.damagesource.DamageSource source,float health){
        writeOwn(victim,source,health,com.solegendary.reignofnether.resources.kill.EconomicSharedDamageHooks.combatOwner(victim,source));
    }
    private static void writeOwn(LivingEntity victim,net.minecraft.world.damagesource.DamageSource source,float health,String combatOwner){
        float before=victim.getHealth();victim.setHealth(health);
        if(before>0&&victim.getHealth()<=0&&Float.isFinite(health)&&victim.level() instanceof ServerLevel level)
            LETHAL.put(victim,new DeathWitness(source,level.getGameTime(),combatOwner));
    }
    private static boolean validOwner(HandBondData.Bond bond){
        var account=ExtendedResourcesSync.account(bond.owner);
        return researched(bond.owner)&&com.dreamseekers.economy.DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(bond.owner))
                &&account!=null&&account.accountEpoch.equals(bond.accountEpoch);
    }
    static void touch(Entity entity){
        if(entity!=null&&entity.level() instanceof ServerLevel level&&!stopping(level)&&exact(entity)){
            var data=HandBondData.get(level);var bond=data.forEntity(entity.getUUID());
            if(bond!=null){bond.lastCombat=level.getGameTime();data.setDirty();}
        }
    }
    @SubscribeEvent public static void combat(net.minecraftforge.event.entity.living.LivingAttackEvent event){
        if(event.getEntity().level().isClientSide())return;
        touch(event.getEntity());touch(event.getSource().getEntity());
    }
    /** Positive enemy combat source, not administrative kill/void/environment or friendly self-sacrifice. */
    private static boolean combatDeath(LivingEntity victim,net.minecraft.world.damagesource.DamageSource source,String witnessedOwner){
        if(source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)||source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD))return false;
        if(victim instanceof ControlledAaroncosHand hand&&witnessedOwner!=null&&!witnessedOwner.isBlank()&&hand.canHarmOwner(witnessedOwner))return true;
        if(!(source.getEntity() instanceof LivingEntity attacker)||attacker==victim||attacker.isRemoved()||attacker.level()!=victim.level())return false;
        String attackerOwner=attacker instanceof com.solegendary.reignofnether.unit.interfaces.Unit u?u.getOwnerName():attacker instanceof ServerPlayer p?p.getGameProfile().getName():"";
        return victim instanceof ControlledAaroncosHand hand&&hand.canHarmOwner(attackerOwner);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent event){
        var victim=event.getEntity();if(!(victim.level() instanceof ServerLevel level)||stopping(level)||!exact(victim))return;
        var data=HandBondData.get(level);var bond=data.forEntity(victim.getUUID());if(bond==null)return;
        var witness=LETHAL.remove(victim);
        if(witness==null||witness.source()!=event.getSource()||witness.tick()!=level.getGameTime()||!validOwner(bond)||!combatDeath(victim,event.getSource(),witness.combatOwner())||bond.missing!=0){data.remove(bond);return;}
        Entity other=level.getEntity(bond.other(victim.getUUID()));
        if(other instanceof LivingEntity live&&!live.isAlive()){data.remove(bond);return;}
        bond.missing=victim.getUUID().equals(bond.left)?1:2;
        bond.readyAt=level.getGameTime()+REPLACEMENT_DELAY;bond.lastCombat=level.getGameTime();data.setDirty();
    }

    /** Core clears research/unit registries during ServerStoppingEvent, before remaining save ticks.
     * Teardown is not a gameplay revocation: preserve the authoritative paid ledger unchanged. */
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event){STOPPING.add(event.getServer());}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){LETHAL.clear();STOPPING.remove(event.getServer());STOP_TICK_NOTED.remove(event.getServer());STOP_LEAVE_NOTED.remove(event.getServer());}
    private static int feedback(net.minecraft.commands.CommandSourceStack source,Result result){
        source.sendSuccess(()->Component.translatable("dreamseekers.hand_bond.result."+result.name().toLowerCase(Locale.ROOT)),false);
        return result==Result.PAIRED||result==Result.UNPAIRED?1:0;
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("rts-hand-pair").requires(s->s.getEntity() instanceof ServerPlayer)
            .then(Commands.argument("left",UuidArgument.uuid()).then(Commands.argument("right",UuidArgument.uuid())
            .executes(c->feedback(c.getSource(),pair(c.getSource().getPlayerOrException(),UuidArgument.getUuid(c,"left"),UuidArgument.getUuid(c,"right")))))));
        event.getDispatcher().register(Commands.literal("rts-hand-unpair").requires(s->s.getEntity() instanceof ServerPlayer)
            .then(Commands.argument("hand",UuidArgument.uuid()).executes(c->feedback(c.getSource(),unpair(c.getSource().getPlayerOrException(),UuidArgument.getUuid(c,"hand"))))));
    }
}
