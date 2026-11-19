// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.ecologyworkposts.EcologyWorkPosts;
import com.dreamseekers.skill.CoordinatedMark;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owner-private, current loaded roster. No coordinates, navigation, recruitment or care grants. */
public final class BuildingCodex {
    public static final int PAGE_SIZE=32,MAX_ROWS=256,MAX_PAGE=7;
    public static final Page EMPTY=new Page(0,0,false,List.of());
    private BuildingCodex(){}
    /** careState: 0 unassigned; 1 approaching this court; 2 resting here; 3 another own court. */
    public record Row(int id,UUID uuid,ResourceLocation typeId,float hp,float maxHp,int careState,boolean careEligible){
        public boolean valid(){return id>=0&&uuid!=null&&typeId!=null&&Float.isFinite(hp)&&Float.isFinite(maxHp)
            &&hp>0&&maxHp>0&&hp<=maxHp&&careState>=0&&careState<=3;}
    }
    public record Page(int page,int totalRows,boolean truncated,List<Row> rows){
        public Page{rows=List.copyOf(rows);}
        public boolean valid(){
            if(page<0||page>MAX_PAGE||totalRows<0||totalRows>MAX_ROWS||rows.size()>PAGE_SIZE
                    ||rows.size()>Math.max(0,Math.min(PAGE_SIZE,totalRows-page*PAGE_SIZE)))return false;
            Set<Integer> ids=new HashSet<>();Set<UUID> uuids=new HashSet<>();
            for(var row:rows)if(!row.valid()||!ids.add(row.id())||!uuids.add(row.uuid()))return false;
            return true;
        }
    }
    public static Page query(ServerPlayer actor,BuildingAbilitySource source,int page){
        if(page<0||page>MAX_PAGE||source==null||!source.valid()||actor==null||actor.serverLevel()!=source.level()
                ||!source.owner().equals(actor.getGameProfile().getName())||BuildingAbility.forBuilding(source.host())!=BuildingAbility.ECOLOGY_CODEX)
            return new Page(Math.max(0,Math.min(MAX_PAGE,page)),0,false,List.of());
        Map<UUID,Integer> care=new HashMap<>();
        for(BuildingPlacement court:List.copyOf(BuildingServerEvents.getBuildings())){
            if(court.level!=source.level()||!source.owner().equals(court.ownerName)||!EcologyWorkPosts.live(court))continue;
            for(var occupant:EcologyWorkPosts.snapshot(court))care.put(occupant.uuid(),court==source.host()?(occupant.arrived()?2:1):3);
        }
        // Only the existing loaded RTS index is considered. No chunk or entity lookup can create one.
        List<LivingEntity> units=UnitServerEvents.getAllUnits().stream().filter(e->e instanceof LivingEntity)
            .map(e->(LivingEntity)e).filter(e->BuildingAbilityEffects.permanentOwnUnit(source,e)&&CoordinatedMark.visible(actor,e))
            .sorted(Comparator.comparing((LivingEntity e)->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())
                .thenComparing(e->e.getUUID().toString())).limit(MAX_ROWS+1L).toList();
        int total=Math.min(MAX_ROWS,units.size()),start=page*PAGE_SIZE;
        boolean canCare=EcologyWorkPosts.owns(actor,source.host());
        List<Row> rows=new ArrayList<>();
        for(int i=start;i<Math.min(total,start+PAGE_SIZE);i++){
            var e=units.get(i);
            if(!source.valid()||!BuildingAbilityEffects.permanentOwnUnit(source,e)||!CoordinatedMark.visible(actor,e))continue;
            float hp=e.getHealth(),max=e.getMaxHealth();
            if(!Float.isFinite(hp)||!Float.isFinite(max)||hp<=0||max<=0)continue;
            rows.add(new Row(e.getId(),e.getUUID(),BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()),Math.min(hp,max),max,
                care.getOrDefault(e.getUUID(),0),canCare&&EcologyWorkPosts.eligible(e,source.host())));
        }
        return source.valid()?new Page(page,total,units.size()>MAX_ROWS,rows):new Page(page,0,false,List.of());
    }
}
