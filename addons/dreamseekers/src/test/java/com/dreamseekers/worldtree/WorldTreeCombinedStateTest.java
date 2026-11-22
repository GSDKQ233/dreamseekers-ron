// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.worldtree;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static com.dreamseekers.worldtree.WorldTreeModel.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldTreeCombinedStateTest {
    private static WorldTreeState pending(WorldTreeModel model,WorldTreeModel target,WorldTreeState.Phase phase){
        var s=new WorldTreeState();
        s.model=model;s.ascended=model.hasShadow();s.target=target;s.phase=phase;
        s.payer="BranchBuyer";s.payerEpoch=UUID.randomUUID();s.receipt=UUID.randomUUID();s.remaining=547;
        s.geometryHighest=model.count;
        return s;
    }
    private static void inert(CompoundTag raw){
        var restored=WorldTreeState.read(raw);
        assertFalse(restored.supported);assertFalse(restored.hasStandingUpgrade());assertEquals(raw,restored.write());
    }

    @Test void eitherOrderHasOneCanonicalResultAndNoDirectOrRepeatedPurchase(){
        for(var source:WorldTreeModel.values()) {
            assertNull(nextUpgrade(source,CONFLUENCE_V6)); // can't buy the both-form for one branch price
            for(var requested:new WorldTreeModel[]{CURRENT_SHADOW,CURRENT_WIND}){
                var next=nextUpgrade(source,requested);
                boolean already=requested==CURRENT_SHADOW?source.hasShadow():source.hasWind();
                if(already){assertNull(next);continue;}
                assertEquals(source.branch==Branch.BASE?requested:CONFLUENCE_V6,next);
                assertTrue(supportsSavedTarget(source,next));
            }
        }
        assertFalse(supportsSavedTarget(CURRENT_BASE,CONFLUENCE_V6));
        assertFalse(supportsSavedTarget(CURRENT_WIND,CURRENT_SHADOW));
        assertTrue(CONFLUENCE_V6.hasShadow()&&CONFLUENCE_V6.hasWind());
        assertTrue(supportsSavedTarget(LEGACY_BASE,LEGACY_SHADOW));
        assertTrue(supportsSavedTarget(BASE_V3,WIND_V4));
    }
    @Test void paidSecondBranchRoundTripsAtEveryPendingPhaseWithoutEarlyOrLostGrants(){
        for(var phase:WorldTreeState.Phase.values())if(phase.pending()){
            for(var source:new WorldTreeModel[]{LEGACY_SHADOW,SHADOW_V3,SHADOW_V5,WIND_V4,WIND_V5}){
                var s=pending(source,CONFLUENCE_V6,phase);s.incomeElapsed=39;
                var raw=s.write();assertEquals(3,raw.getInt("schema"));
                var restored=WorldTreeState.read(raw);
                assertTrue(restored.supported&&restored.hasStandingUpgrade());
                assertEquals(source,restored.model);assertEquals(CONFLUENCE_V6,restored.target);
                assertEquals(source.hasShadow(),restored.ascended);assertEquals(raw,restored.write());
                restored.clearTask(); // refund: leave first upgrade/identity/progress untouched
                assertEquals(WorldTreeState.Phase.COMPLETE,restored.phase);assertEquals(source,restored.model);
                assertNull(restored.receipt);assertNull(restored.target);assertEquals(s.instance,restored.instance);
                assertEquals(39,restored.incomeElapsed);assertEquals(2,restored.write().getInt("schema"));
            }
        }
        var combined=pending(CONFLUENCE_V6,null,WorldTreeState.Phase.COMPLETE);combined.clearTask();
        var raw=combined.write();
        assertTrue(WorldTreeState.read(raw).hasStandingUpgrade());assertEquals(raw,WorldTreeState.read(raw).write());
    }
    @Test void oldPaidTasksRemainExactAndInvalidOrFutureDescriptorsRemainOpaque(){
        var old=pending(LEGACY_BASE,LEGACY_SHADOW,WorldTreeState.Phase.RUNNING).write();
        old.putInt("schema",1);old.remove("model");old.remove("target");
        var restored=WorldTreeState.read(old);
        assertTrue(restored.supported);assertFalse(restored.hasStandingUpgrade());assertEquals(LEGACY_SHADOW,restored.target);
        var oldV2=pending(BASE_V3,WIND_V4,WorldTreeState.Phase.RUNNING).write();
        assertEquals(2,oldV2.getInt("schema"));assertEquals(oldV2,WorldTreeState.read(oldV2).write());
        var second=pending(CURRENT_SHADOW,CONFLUENCE_V6,WorldTreeState.Phase.RUNNING).write();
        var future=second.copy();future.getCompound("target").putInt("revision",99);inert(future);
        var oldImpossible=second.copy();oldImpossible.putInt("schema",2);inert(oldImpossible);
        var mismatch=second.copy();mismatch.putBoolean("ascended",false);inert(mismatch);
        var noClaim=second.copy();noClaim.remove("receipt");inert(noClaim);
        var wrongSource=second.copy();CONFLUENCE_V6.write(wrongSource,"model");inert(wrongSource);
        var skippedStage=second.copy();CURRENT_BASE.write(skippedStage,"model");skippedStage.putBoolean("ascended",false);inert(skippedStage);
    }

    @Test void combinedTemplateKeepsBothReviewedCanopiesAndOneClearEntrance()throws Exception{
        var shadow=structure(CURRENT_SHADOW);var wind=structure(CURRENT_WIND);var both=structure(CONFLUENCE_V6);
        assertEquals(wind.size(),both.size());
        assertEquals(CONFLUENCE_V6.count,both.values().stream().filter(s->!s.equals("minecraft:air")).count());
        assertEquals(1,both.values().stream().filter(s->s.equals("dreamseekers:meltdream_crown_crystal")).count());
        assertTrue(both.containsValue("minecraft:crying_obsidian")&&both.containsValue("pasterdream:windrunner_crystal_block"));
        for(var e:both.entrySet()){
            var windBlock=wind.get(e.getKey());var shadowBlock=shadow.getOrDefault(e.getKey(),"minecraft:air");
            String expected=(!windBlock.equals("minecraft:air")||shadowBlock.equals("dreamseekers:meltdream_crown_crystal"))?windBlock:shadowBlock;
            assertEquals(expected,e.getValue(),e.getKey().toString());
        }
        for(int x=4;x<=6;x++)for(int y=1;y<=3;y++)for(int z=0;z<=5;z++)
            assertEquals("minecraft:air",both.get(new Cell(x,y,z)));
    }
    private record Cell(int x,int y,int z){}
    private static Map<Cell,String> structure(WorldTreeModel model)throws Exception{
        try(var in=WorldTreeCombinedStateTest.class.getResourceAsStream("/data/dreamseekers/structures/"+model.structure+".nbt")){
            assertNotNull(in);var root=NbtIo.readCompressed(in);var palette=root.getList("palette",Tag.TAG_COMPOUND);
            Map<Cell,String> result=new HashMap<>();
            for(var raw:root.getList("blocks",Tag.TAG_COMPOUND)){
                var block=(CompoundTag)raw;var at=block.getList("pos",Tag.TAG_INT);
                assertNull(result.put(new Cell(at.getInt(0),at.getInt(1),at.getInt(2)),palette.getCompound(block.getInt("state")).getString("Name")));
            }
            return result;
        }
    }
}
