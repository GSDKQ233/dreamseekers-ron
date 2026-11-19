// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.stone;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.fogofwar.*;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Opt-in ranged building order. AttackerUnit ticks this goal once; it is not also scheduled in AI. */
public final class StoneSiegeGoal extends TeamSafeBuildingAttackGoal {
    private final StoneSpiritThrowerUnit stone;
    private String targetOwner;
    private long targetRevision,sourceRevision;
    private String sourceOwner;
    private java.util.UUID accountEpoch;
    private int windup;
    private BlockPos cachedContact;
    private long nextAim;
    private long lastTick=Long.MIN_VALUE;
    public StoneSiegeGoal(StoneSpiritThrowerUnit stone) { super(stone);this.stone=stone; }
    public static boolean researched(StoneSpiritThrowerUnit source) {
        return DreamSpecialistResearch.stoneSiege(source.level().isClientSide(),source.getOwnerName());
    }
    public static boolean accountValid(StoneSpiritThrowerUnit source,java.util.UUID epoch) {
        var account=com.solegendary.reignofnether.resources.ExtendedResourcesSync.account(source.getOwnerName());
        return epoch!=null&&account!=null&&epoch.equals(account.accountEpoch);
    }
    public static boolean sourceValid(StoneSpiritThrowerUnit source) {
        return source.level() instanceof ServerLevel level&&level.getServer().isSameThread()
                &&source.isAlive()&&!source.isRemoved()&&level.getEntity(source.getUUID())==source
                &&level.getEntity(source.getId())==source&&researched(source);
    }
    public static boolean buildingValid(StoneSpiritThrowerUnit source,BuildingPlacement b,String owner,long revision) {
        return sourceValid(source)&&b!=null&&b.getLevel()==source.level()
                &&BuildingServerEvents.getBuildings().contains(b)&&!b.isDestroyedServerside&&!b.shouldBeDestroyed()
                &&b.isAttackable()&&revision>=0&&b.ownerName.equals(owner)&&b.getCaptureRevision()==revision
                &&source.canHarmOwner(owner)&&source.level().isLoaded(b.minCorner)&&source.level().isLoaded(b.maxCorner);
    }
    public static boolean visible(StoneSpiritThrowerUnit source,BlockPos contact) {
        if(!source.level().getWorldBorder().isWithinBounds(contact)||!source.level().isLoaded(contact))return false;
        if(!FogOfWarServerEvents.isEnabled())return true;
        if(source.level().dimension()!=Level.OVERWORLD)return false;
        return FogOfWarServerEvents.isBlockVisibleFor(source.getOwnerName(),contact.getX(),contact.getZ());
    }
    public static boolean clearRay(StoneSpiritThrowerUnit source,BlockPos contact) {
        HitResult hit=source.level().clip(new ClipContext(source.getEyePosition(),Vec3.atCenterOf(contact),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source));
        return hit instanceof BlockHitResult block&&block.getType()==HitResult.Type.BLOCK&&block.getBlockPos().equals(contact);
    }
    @Override public void setBuildingTarget(BlockPos pos) {
        stopAttacking();
        if(pos==null||!researched(stone))return;
        var list=stone.level().isClientSide()?BuildingClientEvents.getBuildings():BuildingServerEvents.getBuildings();
        BuildingPlacement candidate=list.stream().filter(this::valid)
                .filter(b->b.originPos.equals(pos)||b.isPosInsideBuilding(pos)).findFirst().orElse(null);
        if(candidate==null)return;
        buildingTarget=candidate;targetOwner=candidate.ownerName;targetRevision=candidate.getCaptureRevision();
        sourceOwner=stone.getOwnerName();sourceRevision=stone.rts().ownershipRevision;
        var account=com.solegendary.reignofnether.resources.ExtendedResourcesSync.account(sourceOwner);
        accountEpoch=account==null?null:account.accountEpoch;
        windup=StoneThrowerBalance.WINDUP_TICKS;
    }
    private boolean identity() {
        return accountValid(stone,accountEpoch)&&sourceOwner!=null&&sourceOwner.equals(stone.getOwnerName())&&sourceRevision==stone.rts().ownershipRevision
                &&buildingValid(stone,buildingTarget,targetOwner,targetRevision);
    }
    public void invalidate() { if(buildingTarget!=null&&!stone.level().isClientSide()&&!identity())stopAttacking(); }
    @Override public boolean canUse() {return buildingTarget!=null;}
    @Override public boolean canContinueToUse() {return canUse();}
    @Override public void start() { }
    @Override public boolean isAttacking() { return buildingTarget!=null; }
    @Override public void stopAttacking() {super.stopAttacking();windup=StoneThrowerBalance.WINDUP_TICKS;sourceOwner=null;cachedContact=null;nextAim=0;}
    private void approach() {
        if(stone.getHoldPosition()||stone.isGarrisoned()) {stone.getNavigation().stop();return;}
        if(stone.tickCount%10==0) {
            var p=buildingTarget.getClosestGroundPos(stone.getOnPos(),1);
            stone.getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,1);
        }
    }
    @Override public void tick() {
        if(stone.level().isClientSide()||lastTick==stone.level().getGameTime())return;
        lastTick=stone.level().getGameTime();
        if(buildingTarget==null)return;
        if(!identity()){stopAttacking();return;}
        if(stone.isPassenger()||StoneHeavyShot.active(stone)||stone.hasEffect(MobEffectRegistrar.STUN.get())
                ||stone.hasEffect(MobEffectRegistrar.FREEZE.get())||stone.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get())
                ||TerrorbeakDreadRoar.hasDisarm(stone)||stone.getTarget()!=null) {
            windup=StoneThrowerBalance.WINDUP_TICKS;return;
        }
        if(cachedContact==null||stone.level().getGameTime()>=nextAim
                ||!buildingTarget.isPosPartOfBuilding(cachedContact,true)||!visible(stone,cachedContact)||!clearRay(stone,cachedContact)) {
            cachedContact=buildingTarget.getBlocks().stream().filter(b->b.isPlaced(stone.level()))
                    .map(BuildingBlock::getBlockPos).filter(p->visible(stone,p))
                    .sorted(java.util.Comparator.comparingDouble(p->stone.getEyePosition().distanceToSqr(Vec3.atCenterOf(p))))
                    .filter(p->clearRay(stone,p)).findFirst().orElse(null);
            nextAim=stone.level().getGameTime()+10;
        }
        BlockPos contact=cachedContact;
        if(contact==null){
            windup=StoneThrowerBalance.WINDUP_TICKS;
            if(buildingTarget.getBlocks().stream().anyMatch(b->b.isPlaced(stone.level())&&visible(stone,b.getBlockPos())))approach();
            else stone.getNavigation().stop();
            return;
        }
        double distance=stone.getEyePosition().distanceToSqr(Vec3.atCenterOf(contact));
        if(distance>stone.getSiegeAttackRange()*stone.getSiegeAttackRange()) {
            windup=StoneThrowerBalance.WINDUP_TICKS;
            approach();
            return;
        }
        stone.getNavigation().stop();stone.getLookControl().setLookAt(Vec3.atCenterOf(contact).x,Vec3.atCenterOf(contact).y,Vec3.atCenterOf(contact).z);
        var normal=(UnitRangedAttackGoal<?>)stone.getAttackGoal();
        if(normal.getAttackCooldown()>0){windup=StoneThrowerBalance.WINDUP_TICKS;return;}
        if(--windup>0)return;
        windup=StoneThrowerBalance.WINDUP_TICKS;
        var bolt=new StoneProjectile(DreamseekersMod.STONE_SPIRIT_THROWER_BOLT.get(),stone.level());
        bolt.armBuilding(stone,buildingTarget,contact);
        OrdinaryAttackGate.ranged(stone,()->{
            if(OrdinaryAttackGate.publish(stone,bolt,()->stone.level().addFreshEntity(bolt))&&!bolt.isRemoved()) {
                if(!identity()||!bolt.isBuildingShotValid()){bolt.discard();return;}
                normal.setToMaxAttackCooldown();stone.swing(InteractionHand.MAIN_HAND);
                FogOfWarClientboundPacket.revealRangedUnit(targetOwner,stone.getId());
            }
        });
    }
}
