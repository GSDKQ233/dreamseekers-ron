// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.gametest;
import java.util.*;
import net.minecraft.world.entity.Entity;
/** Test-only scoped entropy control, never packaged in either release JAR. */
public final class SporeRandomProbe {
    private static final Map<UUID,Double> VALUES=new HashMap<>();
    private static final Map<UUID,Integer> CALLS=new HashMap<>();
    public static void force(Entity e,double value){VALUES.put(e.getUUID(),value);CALLS.put(e.getUUID(),0);}
    public static int calls(Entity e){return CALLS.getOrDefault(e.getUUID(),0);}
    public static double next(Entity e){
        Double value=VALUES.get(e.getUUID());if(value==null)return Math.random();
        CALLS.merge(e.getUUID(),1,Integer::sum);return value;
    }
    public static void clear(){VALUES.clear();CALLS.clear();}
}
