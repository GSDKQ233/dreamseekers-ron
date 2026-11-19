// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.unit.ShadowGolemUnit;
import com.solegendary.reignofnether.building.BuildingPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Read-only targeting shared by the client and native-world tests. This never casts,
 * moves a unit, changes a placement, or replaces the server's authoritative validation. */
public final class GolemStrikeTargetSelection {
    private GolemStrikeTargetSelection() { }
    public record Selection<U,B>(@Nullable U unit, @Nullable B building, @Nullable BlockPos contact) { }

    /** The caller supplies an already eligible hovered unit. Do not even inspect a building
     * when that unit wins; this same lazy policy is exercised by the pure JUnit tests. */
    @Nullable public static <U,B> Selection<U,B> choose(@Nullable U eligibleUnit,
            Supplier<Selection<U,B>> building) {
        return eligibleUnit != null ? new Selection<>(eligibleUnit,null,null) : building.get();
    }

    /** Only registry membership and viewer-specific fog differ between the real client and
     * server-world evidence. All spatial, placed-block, owner and collider checks are real. */
    @Nullable public static Selection<LivingEntity,BuildingPlacement> building(ShadowGolemUnit source,
            @Nullable BuildingPlacement selected, @Nullable BlockPos clicked,
            Predicate<BuildingPlacement> registered, Predicate<BlockPos> visible) {
        if (selected == null) return null;
        var level=source.level();
        var origin=selected.originPos.immutable();
        String owner=selected.ownerName;
        long revision=selected.getCaptureRevision();
        BooleanSupplier current=()->source.isAlive()&&!source.isRemoved()
                &&source.level()==level&&level.getEntity(source.getId())==source
                &&selected.getLevel()==level&&registered.test(selected)
                &&selected.originPos.equals(origin)&&selected.ownerName.equals(owner)
                &&selected.getCaptureRevision()==revision&&revision>=0
                &&!selected.isDestroyedServerside&&selected.isAttackable()&&source.canHarmOwner(owner);
        Predicate<BlockPos> legal=p->{
            if(!selected.isPosPartOfBuilding(p,true)||!level.getWorldBorder().isWithinBounds(p)
                    ||!visible.test(p))return false;
            // Match server collision context exactly; a context-free shape precheck could
            // incorrectly discard blocks whose collider depends on the source entity.
            var hit=level.clip(new ClipContext(source.getEyePosition(),Vec3.atCenterOf(p),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source));
            return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(p);
        };
        BlockPos contact=contact(clicked,()->selected.getBlocks().stream().map(b->b.getBlockPos()).toList(),
                source.position(),current,legal);
        return contact==null?null:new Selection<>(null,selected,contact);
    }

    /** Pure policy: the candidate collection belongs to ONE selected placement, never all
     * nearby buildings. The actual-world adapter above provides the legality predicate. */
    @Nullable static BlockPos contact(@Nullable BlockPos clicked, Supplier<? extends Iterable<BlockPos>> candidates,
            Vec3 feet, BooleanSupplier current, Predicate<BlockPos> legal) {
        if(!current.getAsBoolean())return null;
        var positions=new LinkedHashSet<BlockPos>();
        for(BlockPos p:candidates.get())if(p!=null)positions.add(p.immutable());
        Predicate<BlockPos> valid=p->{
            if(p==null||!positions.contains(p))return false;
            double distance=feet.distanceToSqr(Vec3.atCenterOf(p));
            return Double.isFinite(distance)&&distance<=ShadowGolemStrike.RANGE*ShadowGolemStrike.RANGE&&legal.test(p);
        };
        var order=Comparator.<BlockPos>comparingDouble(p->feet.distanceToSqr(Vec3.atCenterOf(p)))
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);
        BlockPos chosen=valid.test(clicked)?clicked:positions.stream().filter(valid).min(order).orElse(null);
        return chosen!=null&&valid.test(chosen)&&current.getAsBoolean()?chosen.immutable():null;
    }
}
