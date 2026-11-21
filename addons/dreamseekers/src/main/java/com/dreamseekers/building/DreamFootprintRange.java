// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import com.solegendary.reignofnether.building.BuildingPlacement;

/**
 * Server geometry for building ranges. A placement's origin is one corner of a 9-13 block wide
 * structure, so ranges are measured horizontally from the whole footprint; the vertical rule stays
 * relative to the origin layer. The origin always belongs to the footprint used here, which makes
 * every range a superset of the earlier origin-only measurement.
 */
public final class DreamFootprintRange {
    private DreamFootprintRange() {}

    /** Distance along one axis between two inclusive block spans; zero when they overlap. */
    public static long spanGap(int aMin,int aMax,int bMin,int bMax) {
        if((long)bMin>aMax)return (long)bMin-aMax;
        if((long)aMin>bMax)return (long)aMin-bMax;
        return 0;
    }
    /** Distance along one axis from a point to the block-centre span of an inclusive block span. */
    public static double pointGap(double point,int min,int max) {
        double lo=min+.5,hi=max+.5;
        return point<lo?lo-point:point>hi?point-hi:0;
    }
    public static boolean footprintsWithin(long gapX,long gapZ,long originDy,int horizontal,int vertical) {
        return gapX<=horizontal&&gapZ<=horizontal&&gapX*gapX+gapZ*gapZ<=(long)horizontal*horizontal
                &&Math.abs(originDy)<=vertical;
    }
    public static boolean pointWithin(double gapX,double dy,double gapZ,int radius) {
        return gapX<=radius&&Math.abs(dy)<=radius&&gapZ<=radius&&gapX*gapX+dy*dy+gapZ*gapZ<=(double)radius*radius;
    }

    /** Sphere rule between two footprints: horizontal footprint gaps plus the origin-layer height difference. */
    public static boolean footprintsWithinSphere(long gapX,long originDy,long gapZ,int range) {
        return gapX<=range&&Math.abs(originDy)<=range&&gapZ<=range&&gapX*gapX+originDy*originDy+gapZ*gapZ<=(long)range*range;
    }
    public static boolean footprintsWithinSphere(BuildingPlacement a,BuildingPlacement b,int range) {
        return footprintsWithinSphere(spanGap(minX(a),maxX(a),minX(b),maxX(b)),(long)a.originPos.getY()-b.originPos.getY(),
                spanGap(minZ(a),maxZ(a),minZ(b),maxZ(b)),range);
    }
    public static boolean footprintsWithin(BuildingPlacement a,BuildingPlacement b,int horizontal,int vertical) {
        return footprintsWithin(spanGap(minX(a),maxX(a),minX(b),maxX(b)),spanGap(minZ(a),maxZ(a),minZ(b),maxZ(b)),
                (long)a.originPos.getY()-b.originPos.getY(),horizontal,vertical);
    }
    public static boolean pointWithin(BuildingPlacement p,double x,double y,double z,int radius) {
        return pointWithin(pointGap(x,minX(p),maxX(p)),y-(p.originPos.getY()+.5),pointGap(z,minZ(p),maxZ(p)),radius);
    }
    private static int minX(BuildingPlacement p){return Math.min(p.minCorner.getX(),p.originPos.getX());}
    private static int maxX(BuildingPlacement p){return Math.max(p.maxCorner.getX(),p.originPos.getX());}
    private static int minZ(BuildingPlacement p){return Math.min(p.minCorner.getZ(),p.originPos.getZ());}
    private static int maxZ(BuildingPlacement p){return Math.max(p.maxCorner.getZ(),p.originPos.getZ());}
}
