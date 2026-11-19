package com.dreamseekers;

import com.dreamseekers.economy.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.EvokerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** One bounded candidate: genuine native ordinary Evoker attack before/after owner research.
 * No manually spawned fang, synthetic hurt, Wind/Bone skill, injected multiplier, or changed damage listener. */
@GameTestHolder("dreamseekers_audit_fixes") @PrefixGameTestTemplate(false)
public final class EvokerAscendancyAuditGameTests {
    private static final List<Probe> ACTIVE=new ArrayList<>();
    @AfterBatch(batch="reaudit_evoker_ascendancy") public static void clean(net.minecraft.server.level.ServerLevel level) {for(Probe p:List.copyOf(ACTIVE))p.clean();ACTIVE.clear();}
    public static final class Probe {
        final GameTestHelper h;final String owner="AuditEvokerDream",enemy="AuditEvokerEnemy";
        final List<Entity> entities=new ArrayList<>();final int[] fangCount={0,0};final float[] firstDamage={0,0};final int[] hits={0,0};
        EvokerUnit caster;Mob victim;int phase;boolean closed;
        Probe(GameTestHelper h){this.h=h;}
        @SubscribeEvent public void joined(EntityJoinLevelEvent e) {if(e.getEntity() instanceof EvokerFangs f&&f.getOwner()==caster){fangCount[phase]++;entities.add(f);}}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent e) {
            if(e.getEntity()==victim&&e.getSource().getEntity()==caster&&e.getAmount()>0){if(hits[phase]++==0)firstDamage[phase]=e.getAmount();}
        }
        <T extends Mob>T spawn(EntityType<T> type,String name,int x,int z){T e=type.create(h.getLevel());((Unit)e).setOwnerName(name);e.setNoAi(true);BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(e),"native unit publication");entities.add(e);return e;}
        void clean(){if(closed)return;closed=true;MinecraftForge.EVENT_BUS.unregister(this);ResearchServerEvents.removeAllResearchFor(owner);ResearchServerEvents.removeAllResearchFor(enemy);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner)||p.name.equals(enemy));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(owner)||r.ownerName.equals(enemy));entities.forEach(Entity::discard);}
        void position(){BlockPos p=h.absolutePos(new BlockPos(4,1,4));caster.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);victim.setPos(caster.position().add(1.25,0,0));}
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="unit_isolation_arena",batch="reaudit_evoker_ascendancy",timeoutTicks=320)
    public static void ordinaryNativeFangMustReceiveOwnerWideAscendancy(GameTestHelper h) {
        Probe p=new Probe(h);ACTIVE.add(p);MinecraftForge.EVENT_BUS.register(p);
        for(String owner:List.of(p.owner,p.enemy)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,owner.equals(p.owner)?DreamseekersMod.DREAMSEEKERS:Factions.VILLAGERS));ResourcesServerEvents.resourcesList.add(new Resources(owner,1000,1000,1000));}
        BlockPos origin=h.absolutePos(BlockPos.ZERO);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(origin.offset(x,7,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<7;y++)h.getLevel().setBlock(origin.offset(x,y,z),(x==0||x==23||z==0||z==23?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}
        p.caster=p.spawn(EntityRegistrar.EVOKER_UNIT.get(),p.owner,4,4);p.caster.setupEquipmentAndUpgradesServer();p.caster.isUsingLineFangs=true;
        p.victim=p.spawn(EntityRegistrar.VILLAGER_UNIT.get(),p.enemy,6,4);p.victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);p.victim.setHealth(200);p.victim.getAttribute(Attributes.ARMOR).setBaseValue(0);p.victim.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);p.victim.getAttribute(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get()).setBaseValue(0);
        h.startSequence().thenIdle(4).thenExecute(()->{p.position();h.assertTrue(p.caster.getMainHandItem().getEnchantmentLevel(EnchantmentRegistrar.ZEAL.get())==0,"no Zeal damage confounder");p.caster.performUnitRangedAttack(p.victim,1);})
            .thenIdle(50).thenExecute(()->{h.assertTrue(p.fangCount[0]>0&&p.hits[0]>0&&p.victim.getHealth()<200,"actual native cast creates fangs and damages victim");h.assertTrue(Math.abs(p.firstDamage[0]-6f)<.001,"unresearched native ordinary line fang baseline6");ResearchServerEvents.addResearch(p.owner,DreamEconomyResearch.ASCENDANCY_ID);})
            // The original7-second ordinary-attack cooldown expires naturally; do not reset/bypass it.
            .thenIdle(150).thenExecute(()->{h.assertTrue(p.caster.getAttribute(Attributes.MAX_HEALTH).getModifier(DreamAscendancyAttributes.HEALTH_ID)!=null,"real owner research reconciled by server living ticks");p.phase=1;p.position();p.victim.setHealth(200);p.caster.performUnitRangedAttack(p.victim,1);})
            .thenIdle(50).thenExecute(()->{try{System.out.println("REAUDIT_EVOKER_ASCENDANCY before="+p.firstDamage[0]+" after="+p.firstDamage[1]+" fangs="+Arrays.toString(p.fangCount)+" hits="+Arrays.toString(p.hits)+" finalHP="+p.victim.getHealth()+" baseAD="+p.caster.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).getBaseValue());h.assertTrue(p.fangCount[1]>0&&p.hits[1]>0&&p.victim.getHealth()<200,"researched ordinary native attack reaches real HP damage");h.assertTrue(Math.abs(p.firstDamage[1]-p.firstDamage[0]*1.15f)<.001,"owner-wide normal attack+15 percent includes real combat Evoker despite vanilla RoN baseAD0");}finally{p.clean();}}).thenSucceed();
    }
}
