package com.dreamseekers;

import com.dreamseekers.economy.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.faction.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.EvokerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Two bounded post-fix controls. Positive fangs/Wind/Bone use real native casts;
 * only the explicit negative hand-created-fang/unrelated-magic controls bypass ordinary birth. */
@GameTestHolder("dreamseekers_audit_fixes") @PrefixGameTestTemplate(false)
public final class EvokerAscendancyPostfixControls {
    private static final String BATCH="reaudit_evoker_controls";
    private static final List<Fixture> ACTIVE=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void clean(ServerLevel level){for(Fixture f:List.copyOf(ACTIVE))f.clean();ACTIVE.clear();}
    public static final class Fixture {
        final GameTestHelper h;final String prefix;final Set<String> owners=new HashSet<>();final List<Entity> entities=new ArrayList<>();
        final Map<UUID,Runnable> damageCallbacks=new HashMap<>();final Map<UUID,Integer> fangs=new HashMap<>();boolean closed;
        Fixture(GameTestHelper h,String prefix){this.h=h;this.prefix=prefix;ACTIVE.add(this);MinecraftForge.EVENT_BUS.register(this);floor(h);}
        String account(String suffix,Faction faction){String n=prefix+suffix;if(owners.add(n)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(n,faction));ResourcesServerEvents.resourcesList.add(new Resources(n,1000,1000,1000));}return n;}
        <T extends Mob>T spawn(EntityType<T> type,String owner,int x,int y,int z){T e=type.create(h.getLevel());((Unit)e).setOwnerName(owner);e.setNoAi(true);position(e,relative(h,x,y,z));entities.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"actual native unit publication");return e;}
        EvokerUnit evoker(String owner,int z){var e=spawn(EntityRegistrar.EVOKER_UNIT.get(),owner,4,1,z);e.setupEquipmentAndUpgradesServer();fangs.put(e.getUUID(),0);return e;}
        Mob victim(String owner,int x,int z){var e=spawn(EntityRegistrar.VILLAGER_UNIT.get(),owner,x,1,z);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);e.setHealth(1000);e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);e.getAttribute(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get()).setBaseValue(0);return e;}
        void research(String owner){ResearchServerEvents.addResearch(owner,DreamEconomyResearch.ASCENDANCY_ID);}
        SkillResult cast(BoneWingUnit source,LivingEntity target){return SkillService.request(player(source.getOwnerName()),new SkillRequest(UnitSkill.COORDINATED_MARK,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID()));}
        net.minecraft.server.level.ServerPlayer player(String owner){return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(owner.getBytes(java.nio.charset.StandardCharsets.UTF_8)),owner));}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent e){Runnable r=damageCallbacks.remove(e.getEntity().getUUID());if(r!=null)r.run();}
        @SubscribeEvent public void joined(EntityJoinLevelEvent e){if(e.getEntity() instanceof EvokerFangs fang&&fang.getOwner()!=null&&fangs.containsKey(fang.getOwner().getUUID())){fangs.compute(fang.getOwner().getUUID(),(k,n)->n+1);entities.add(fang);}}
        void clean(){if(closed)return;closed=true;MinecraftForge.EVENT_BUS.unregister(this);for(String n:owners)ResearchServerEvents.removeAllResearchFor(n);PlayerServerEvents.rtsPlayers.removeIf(p->owners.contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->owners.contains(r.ownerName));entities.forEach(Entity::discard);}
    }
    private static Vec3 relative(GameTestHelper h,int x,int y,int z){BlockPos p=h.absolutePos(new BlockPos(x,y,z));return new Vec3(p.getX()+.5,p.getY(),p.getZ()+.5);}
    private static void position(Entity e,Vec3 p){e.moveTo(p.x,p.y,p.z);e.setDeltaMovement(Vec3.ZERO);}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<.001,why+" actual="+actual+" expected="+expected);}
    private static void floor(GameTestHelper h){BlockPos o=h.absolutePos(BlockPos.ZERO);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(o.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(o.offset(x,7,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<7;y++)h.getLevel().setBlock(o.offset(x,y,z),(x==0||x==23||z==0||z==23?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}}
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="unit_isolation_arena",batch=BATCH,timeoutTicks=100)
    public static void researchedFangHasNoFriendlyOrManualOrOtherFactionLeak(GameTestHelper h){
        Fixture f=new Fixture(h,"AuditFangBoundary_");String asc=f.account("Asc",DreamseekersMod.DREAMSEEKERS),other=f.account("Other",Factions.VILLAGERS),plain=f.account("Plain",DreamseekersMod.DREAMSEEKERS),enemy=f.account("Enemy",Factions.MONSTERS);f.research(asc);f.research(other);
        List<EvokerUnit> casters=List.of(f.evoker(asc,3),f.evoker(other,8),f.evoker(plain,13),f.evoker(asc,18));
        List<Mob> victims=List.of(f.victim(enemy,6,3),f.victim(enemy,6,8),f.victim(enemy,6,13),f.victim(asc,6,18));Mob manual=f.victim(enemy,17,21),raw=f.victim(enemy,17,10);float[] before=new float[4];float[] manualBefore={0};
        h.startSequence().thenIdle(4).thenExecute(()->{for(int i=0;i<4;i++){EvokerUnit caster=casters.get(i);h.assertTrue(caster.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).getBaseValue()==0,"never invent positive native AD");position(victims.get(i),caster.position().add(1.25,0,0));before[i]=victims.get(i).getHealth();caster.isUsingLineFangs=true;caster.performUnitRangedAttack(victims.get(i),1);}manualBefore[0]=manual.getHealth();EvokerFangs fang=new EvokerFangs(h.getLevel(),manual.getX(),manual.getY(),manual.getZ(),0,0,casters.get(0));h.assertTrue(h.getLevel().addFreshEntity(fang),"negative-control hand-created fang actually published");})
          .thenIdle(50).thenExecute(()->{try{double[] expected={6.9,6,6,0};for(int i=0;i<4;i++){h.assertTrue(f.fangs.get(casters.get(i).getUUID())>0,"every native caster actually created a batch");close(h,before[i]-victims.get(i).getHealth(),expected[i],"ordinary research/faction/friend boundary "+i);}close(h,manualBefore[0]-manual.getHealth(),6,"hand-created same-owner fang cannot borrow ordinary research birth");float rawBefore=raw.getHealth();raw.hurt(raw.damageSources().indirectMagic(casters.get(0),casters.get(0)),6);close(h,rawBefore-raw.getHealth(),6,"unrelated same-owner magic stays native");System.out.println("REAUDIT_FANG_BOUNDARY_PASS native6.9 plain6 otherFaction6 friendly0 manual6 rawMagic6");}finally{f.clean();}}).thenSucceed();
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="unit_isolation_arena",batch=BATCH,timeoutTicks=950)
    public static void ordinaryAscendancyWindZealBoneComposeExactlyOnce(GameTestHelper h){
        Fixture f=new Fixture(h,"AuditFangCombined_");String owner=f.account("Owner",DreamseekersMod.DREAMSEEKERS),enemy=f.account("Enemy",Factions.MONSTERS);f.research(owner);
        WindKnightUnit wind=f.spawn(DreamseekersMod.WIND_KNIGHT_UNIT.get(),owner,5,1,21);wind.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);wind.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);wind.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);
        Mob dashTarget=f.victim(enemy,11,21);EvokerUnit line=f.evoker(owner,5),circle=f.evoker(owner,14);Mob v1=f.victim(enemy,6,5),v2=f.victim(enemy,6,14);BoneWingUnit b1=f.spawn(DreamseekersMod.BONE_WING_UNIT.get(),owner,9,2,5),b2=f.spawn(DreamseekersMod.BONE_WING_UNIT.get(),owner,9,2,14);long[] grounded={Long.MIN_VALUE};
        h.startSequence().thenWaitUntil(()->h.assertTrue(wind.tickCount>3&&line.tickCount>3&&b1.tickCount>3,"actual published identity ticks"))
          .thenExecute(()->wind.setNoAi(false)).thenWaitUntil(()->{h.assertTrue(wind.onGround()&&h.getLevel().noCollision(wind),"actual grounded Wind prerequisite");if(grounded[0]==Long.MIN_VALUE)grounded[0]=h.getLevel().getGameTime();h.assertTrue(h.getLevel().getGameTime()>grounded[0],"two real grounded ticks");})
          .thenExecute(()->{for(EvokerUnit caster:List.of(line,circle)){caster.getMainHandItem().enchant(EnchantmentRegistrar.ZEAL.get(),1);h.assertTrue(caster.getAttribute(Attributes.MAX_HEALTH).getModifier(DreamAscendancyAttributes.HEALTH_ID)!=null,"actual owner research applied");}f.damageCallbacks.put(dashTarget.getUUID(),()->{position(line,wind.position().add(0,0,-2));position(circle,wind.position().add(-2,0,0));});h.assertTrue(SkillService.request(f.player(owner),new SkillRequest(UnitSkill.WIND_DASH,wind.getId(),wind.getUUID(),wind.rts().skills().revision(),dashTarget.getId(),dashTarget.getUUID()))==SkillResult.SUCCESS,"real authorized Wind command, no injected buff");})
          .thenWaitUntil(()->h.assertTrue(!WindDash.isActive(wind),"actual Wind resolves"))
          .thenExecute(()->{wind.setNoAi(true);for(EvokerUnit caster:List.of(line,circle))h.assertTrue(SkillEffects.hasNamedBuff(caster,WindDash.COMMAND_KEY,BuffKind.ATTACK_DAMAGE),"real Wind command reached native caster");position(line,relative(h,4,1,5));position(circle,relative(h,4,1,14));position(v1,line.position().add(1.25,0,0));position(v2,circle.position().add(1.05,0,0));h.assertTrue(f.cast(b1,v1)==SkillResult.SUCCESS&&f.cast(b2,v2)==SkillResult.SUCCESS,"two real authorized Bone marks");line.isUsingLineFangs=true;circle.isUsingLineFangs=false;line.performUnitRangedAttack(v1,1);circle.performUnitRangedAttack(v2,1);})
          .thenIdle(50).thenExecute(()->{try{h.assertTrue(f.fangs.get(line.getUUID())>0&&f.fangs.get(circle.getUUID())>0,"both native ordinary goal factories spawned batches");close(h,1000-v1.getHealth(),(6*1.15*1.1+1)*1.12,"line Ascendancy then Wind, native Zeal+1, final Bone12 percent");close(h,1000-v2.getHealth(),(12*1.15*1.1+1)*1.12,"circle same composition exactly once");v1.invulnerableTime=0;float hp=v1.getHealth();v1.hurt(v1.damageSources().indirectMagic(line,line),6);close(h,hp-v1.getHealth(),7,"unrelated raw magic gains no Ascendancy/Wind/Bone but keeps native Zeal");System.out.println("REAUDIT_FANG_COMPOSITION_PASS line="+(1000-v1.getHealth()-7)+" circle="+(1000-v2.getHealth()));}finally{f.clean();}}).thenSucceed();
    }
}
