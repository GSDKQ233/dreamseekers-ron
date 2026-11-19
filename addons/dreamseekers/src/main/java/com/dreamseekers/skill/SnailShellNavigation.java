// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import com.dreamseekers.unit.BasaltSnailUnit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
/** Paid snail only: even the legacy melee continuation cannot navigate during the live formation. */
public final class SnailShellNavigation extends GroundPathNavigation {
    private final BasaltSnailUnit source;
    public SnailShellNavigation(BasaltSnailUnit source,Level level) { super(source,level);this.source=source; }
    public void stopForFormation() {
        super.stop();
        source.getMoveControl().setWantedPosition(source.getX(),source.getY(),source.getZ(),0);
        source.setSpeed(0);source.setZza(0);source.setXxa(0);
        // Preserve gravity, water flow and external knockback; this removes active steering only.
    }
    @Override public boolean moveTo(Path path,double speed) {
        return !SnailShellFormation.active(source)&&super.moveTo(path,speed);
    }
    @Override public boolean moveTo(Entity target,double speed) {
        return !SnailShellFormation.active(source)&&super.moveTo(target,speed);
    }
    @Override public boolean moveTo(double x,double y,double z,double speed) {
        return !SnailShellFormation.active(source)&&super.moveTo(x,y,z,speed);
    }
    @Override public void tick() {
        if(SnailShellFormation.active(source))stopForFormation();
        else super.tick();
    }
}
