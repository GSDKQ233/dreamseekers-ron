// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import com.dreamseekers.unit.BasaltSnailUnit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
/** Paid snail only: even the legacy melee continuation cannot navigate during the live formation. */
public final class SnailShellNavigation extends GroundPathNavigation {
    private final BasaltSnailUnit source;
    private record GroundCoast(Path path,long commandRevision,Vec3 velocity) { }
    private GroundCoast groundCoast;

    public SnailShellNavigation(BasaltSnailUnit source,Level level) { super(source,level);this.source=source; }

    private boolean dryGroundWithoutImpulse() {
        return source.onGround()&&!source.isNoAi()&&!source.isPassenger()&&!source.isVehicle()
                &&!source.isInWaterOrBubble()&&!source.isInFluidType()&&!source.isInLava()
                &&source.hurtTime==0&&!source.hurtMarked&&!source.hasImpulse;
    }
    private static double afterNativeDeadZone(double component) {
        // LivingEntity.aiStep re-creates the vector and clamps each axis before travel.
        return Math.abs(component)<.003?0:component;
    }
    /** Keep proof only for ordinary dry path travel from rest or from previously proven walking.
     * A push can outlast the native impulse flag; a later tick must not claim that momentum. */
    @Nullable public Path beginGroundTravel(Vec3 input) {
        GroundCoast prior=groundCoast;groundCoast=null;
        Path path=getPath();
        if(path==null||path.isDone()||input.x!=0||input.y!=0||!Double.isFinite(input.z)||input.z<0
                ||!dryGroundWithoutImpulse())return null;
        Vec3 from=source.getDeltaMovement();
        if(!(from.y<=0))return null;
        if(from.x==0&&from.z==0)return input.z>0?path:null;
        return prior!=null&&from.x==afterNativeDeadZone(prior.velocity.x)
                &&from.z==afterNativeDeadZone(prior.velocity.z)?path:null;
    }
    public void endGroundTravel(@Nullable Path path,long commandRevision) {
        if(path==null||path!=getPath()||path.isDone()||source.rts().commandRevision!=commandRevision
                ||!dryGroundWithoutImpulse())return;
        Vec3 velocity=source.getDeltaMovement();
        if(Double.isFinite(velocity.x)&&Double.isFinite(velocity.y)&&Double.isFinite(velocity.z)&&velocity.y<=0)
            groundCoast=new GroundCoast(path,commandRevision,velocity);
    }
    @Nullable Vec3 captureOrderCoast() {
        GroundCoast current=groundCoast;
        return current!=null&&current.path==getPath()&&!current.path.isDone()
                &&current.commandRevision==source.rts().commandRevision&&dryGroundWithoutImpulse()
                &&current.velocity==source.getDeltaMovement()?current.velocity:null;
    }
    /** Only after successful manual deployment. Do not brake a replacement velocity from reset callbacks. */
    void brakeOrderCoast(@Nullable Vec3 captured) {
        groundCoast=null;
        if(captured!=null&&captured==source.getDeltaMovement()&&dryGroundWithoutImpulse())
            source.setDeltaMovement(0,captured.y,0);
    }

    public void stopForFormation() {
        super.stop();
        source.getMoveControl().setWantedPosition(source.getX(),source.getY(),source.getZ(),0);
        source.setSpeed(0);source.setZza(0);source.setXxa(0);
        // Per-tick steering lock never changes velocity, so later physics impulses are untouched.
    }
    @Override public boolean moveTo(Path path,double speed) {
        return !SnailShellFormation.occupiesOrders(source)&&super.moveTo(path,speed);
    }
    @Override public boolean moveTo(Entity target,double speed) {
        return !SnailShellFormation.occupiesOrders(source)&&super.moveTo(target,speed);
    }
    @Override public boolean moveTo(double x,double y,double z,double speed) {
        return !SnailShellFormation.occupiesOrders(source)&&super.moveTo(x,y,z,speed);
    }
    @Override public void tick() {
        if(SnailShellFormation.occupiesOrders(source))stopForFormation();
        else super.tick();
    }
}
