// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.building.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.function.*;
import com.dreamseekers.resonance.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.UnitCrossbowAttackGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;

/** Genuine server casts/HP writes/attributes/cached-goal consumers. Research installation is an
 * explicit fixture; paid queue/progression coverage lives in LightResonanceResearchGameTests.
 * No skill cooldown or vanilla hurt immunity is cleared to manufacture a successful cast/hit. */
@GameTestHolder("dreamseekers_light_amplification") @PrefixGameTestTemplate(false)
public final class LightSkillAmplificationGameTests {
    private static final String BATCH="light_amplification";
    private static final List<F> FIXTURES=new ArrayList<>();
    private static final Probe PROBE=new Probe();
    private static boolean listening;
    private static final double EPS=.0003;
    private static final UUID PERMANENT=UUID.fromString("10b63172-00ea-4ad2-bef5-7cbbd9412c94");
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        if(listening){MinecraftForge.EVENT_BUS.unregister(PROBE);listening=false;}
        PROBE.heals.clear();PROBE.damageCallbacks.clear();PROBE.damageEvents.clear();PROBE.deadlines.clear();PROBE.continuousLeaves.clear();
        for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();
    }
    @AfterBatch(batch="light_amplification_expiry") public static void cleanupExpiry(ServerLevel level){cleanup(level);}
    private static final class F implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final String owner;final Resources account;final PassiveIncomePeer peer;
        final BlockPos origin;final BuildingPlacement root,dais;final List<BuildingPlacement> buildings=new ArrayList<>();
        final List<Mob> units=new ArrayList<>();final Set<Long> forced=new HashSet<>();final List<String> names=new ArrayList<>();boolean closed;
        F(GameTestHelper h,String label,int index,int tier) {
            this.h=h;level=h.getLevel();owner="LightAmpGT_"+label;origin=new BlockPos(41000+index*256,100,41000);
            FIXTURES.add(this);account=account(owner);account.extra.put(DreamPassiveEconomy.SHADOW,500);peer=new PassiveIncomePeer(level,owner);
            root=install(DreamseekersMod.DREAM_ANCHOR,origin);dais=install(DreamseekersMod.RESONANCE_DAIS,origin.offset(24,0,0));tier(tier);
        }
        Resources account(String name) {
            h.assertTrue(PlayerServerEvents.getRTSPlayer(name)==null,"distinct owner fixture "+name);names.add(name);
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));
            var r=new Resources(name,5000,5000,5000,123);ResourcesServerEvents.resourcesList.add(r);return r;
        }
        void tier(int tier){tier(owner,tier);}
        void tier(String name,int tier){if(tier>=1)ResearchServerEvents.addResearch(name,LightResonanceResearch.FIRST_ID);if(tier>=2)ResearchServerEvents.addResearch(name,LightResonanceResearch.FINAL_ID);}
        void load(BlockPos p){for(int x=(p.getX()-32)>>4;x<=(p.getX()+48)>>4;x++)for(int z=(p.getZ()-32)>>4;z<=(p.getZ()+48)>>4;z++){
            long c=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(c)){level.setChunkForced(x,z,true);forced.add(c);}level.getChunk(x,z);}}
        void releaseChunks(){for(long c:forced)level.setChunkForced(ChunkPos.getX(c),ChunkPos.getZ(c),false);forced.clear();}
        BuildingPlacement install(Building type,BlockPos p){load(p);for(int x=0;x<22;x++)for(int z=0;z<22;z++){
            level.setBlock(p.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<20;y++)level.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            for(var b:BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(level),level,p,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            var b=BuildingUtils.getNewBuildingPlacement(type,level,p,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(b);buildings.add(b);b.onBuilt();return b;}
        <T extends Mob> T mob(EntityType<T> type,double x,double y,double z){return mob(type,owner,x,y,z);}
        <T extends Mob> T mob(EntityType<T> type,String name,double x,double y,double z){
            T u=type.create(level);h.assertTrue(u!=null,"registered actual unit");((Unit)u).setOwnerName(name);u.setNoAi(true);u.setNoGravity(true);
            move(u,x,y,z);var p=u.blockPosition();load(p);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=0;dy<4;dy++)level.setBlock(p.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);
            h.assertTrue(level.addFreshEntity(u),"actual entity publication");units.add(u);return u;}
        void move(Entity u,double x,double y,double z){u.moveTo(dais.originPos.getX()+.5+x,dais.originPos.getY()+.5+y,dais.originPos.getZ()+.5+z);u.setDeltaMovement(Vec3.ZERO);}
        int eastEdge(){return Math.max(0,dais.maxCorner.getX()-dais.originPos.getX());}
        int southEdge(){return Math.max(0,dais.maxCorner.getZ()-dais.originPos.getZ());}
        PinkSlimeUnit gel(double x,double z){return mob(DreamseekersMod.PINK_SLIME_UNIT.get(),x,1,z);}
        JellyfishUnit jelly(double x,double z){return mob(DreamseekersMod.JELLYFISH_UNIT.get(),x,1,z);}
        GoldenFoxUnit fox(double x,double z){return mob(DreamseekersMod.GOLDEN_FOX_UNIT.get(),x,1,z);}
        PillagerUnit pillager(double x,double z){var u=mob(EntityRegistrar.PILLAGER_UNIT.get(),x,1,z);u.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.CROSSBOW));return u;}
        GameTestSequence ready(){return h.startSequence().thenWaitUntil(()->{for(var u:units)h.assertTrue(u.tickCount>3&&level.getEntity(u.getId())==u&&level.getEntity(u.getUUID())==u&&UnitServerEvents.getAllUnits().contains(u),"real native publication "+u.getUUID());});}
        SkillResult cast(SkillUnit source,UnitSkill skill,LivingEntity target){return SkillService.request(peer.actor,new SkillRequest(skill,source.mob().getId(),source.mob().getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID()));}
        void success(SkillUnit source,UnitSkill skill,LivingEntity target){require(h,cast(source,skill,target),SkillResult.SUCCESS,"genuine "+skill+" cast");}
        void ledger(){h.assertTrue(account.food==5000&&account.wood==5000&&account.ore==5000&&account.emerald==123&&account.extra.get(DreamPassiveEconomy.SHADOW)==500,"ordinary support cast leaves exact account balances unchanged");}
        public void close(){if(closed)return;closed=true;peer.close();for(var u:units){var loaded=level.getEntity(u.getUUID());if(loaded!=null)loaded.discard();if(!u.isRemoved())u.discard();}
            for(var b:buildings){b.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(b);}
            for(String a:names)for(String b:names)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
            for(String n:names){ResearchServerEvents.removeAllResearchFor(n);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(n));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(n));}releaseChunks();}
    }
    private static final class HealRead {int count;final List<Float> requests=new ArrayList<>();final List<Long> times=new ArrayList<>();Runnable once,each;boolean inflate;}
    private static final class DeadlineRead { final PillagerUnit target; final long at; int observations; double actualAps, bonus, actualCooldown; DeadlineRead(PillagerUnit target,long at){this.target=target;this.at=at;} }
    public static final class Probe {
        final Map<UUID,Integer> continuousLeaves=new HashMap<>();
        @SubscribeEvent public void leave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event){UUID id=event.getEntity().getUUID();if(continuousLeaves.containsKey(id))continuousLeaves.merge(id,1,Integer::sum);}
        final Map<UUID,DeadlineRead> deadlines=new HashMap<>();
        @SubscribeEvent public void deadline(LivingEvent.LivingTickEvent event){var read=deadlines.get(event.getEntity().getUUID());if(read==null||event.getEntity().level().getGameTime()!=read.at)return;read.observations++;read.actualAps=read.target.getNonBaseAttacksPerSecond();read.bonus=SkillEffects.bonus(read.target,BuffKind.ATTACK_SPEED);crossbow(read.target).setToMaxAttackCooldown();read.actualCooldown=crossbow(read.target).getAttackCooldown();}
        final Map<UUID,HealRead> heals=new HashMap<>();final Map<UUID,Runnable> damageCallbacks=new HashMap<>();final Map<UUID,Integer> damageEvents=new HashMap<>();
        @SubscribeEvent(priority=EventPriority.LOWEST) public void heal(LivingHealEvent event){var read=heals.get(event.getEntity().getUUID());if(read==null)return;read.count++;read.requests.add(event.getAmount());read.times.add(event.getEntity().level().getGameTime());
            if(read.inflate)event.setAmount(100);Runnable once=read.once;read.once=null;if(once!=null)once.run();if(read.each!=null)read.each.run();}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent event){UUID id=event.getEntity().getUUID();if(!damageEvents.containsKey(id))return;damageEvents.merge(id,1,Integer::sum);Runnable once=damageCallbacks.remove(id);if(once!=null)once.run();}
    }
    private static void listen(){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}}
    private static HealRead watchHeal(LivingEntity target){listen();var read=new HealRead();PROBE.heals.put(target.getUUID(),read);return read;}
    private static void near(GameTestHelper h,double value,double expected,String why){h.assertTrue(Math.abs(value-expected)<EPS,why+": actual="+value+" expected="+expected);}
    private static void require(GameTestHelper h,SkillResult actual,SkillResult expected,String why){h.assertTrue(actual==expected,why+": actual="+actual+" expected="+expected);}
    private static double potency(int tier){return tier==2?1.5:tier==1?1.2:1;}
    private static double aps(LivingEntity e){return e.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get());}
    private static void neutral(LivingEntity e){e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);e.setAbsorptionAmount(0);var r=e.getAttribute(AttributeRegistrar.RANGED_DAMAGE_RESIST.get());if(r!=null)r.setBaseValue(0);}
    private static void damage(LivingEntity e,double amount){e.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);var a=e.getAttribute(Attributes.ATTACK_DAMAGE);if(a!=null)a.setBaseValue(amount);var crit=e.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);}
    private static UnitCrossbowAttackGoal<?> crossbow(PillagerUnit u){return (UnitCrossbowAttackGoal<?>)u.getAttackGoal();}
    private static int cached(PillagerUnit u){try{Field field=UnitCrossbowAttackGoal.class.getDeclaredField("attackCooldownMax");field.setAccessible(true);return field.getInt(crossbow(u));}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    private static int shortened(int original,double amount){return Math.max(1,(int)Math.floor(original/(1+amount)));}
    private static boolean blessed(LivingEntity u){return SkillEffects.hasNamedBuff(u,SkillEffects.FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED);}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void baselineGelUsesRealShieldAndRebound(GameTestHelper h){gelTier(h,0,0);}
    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void middleGelUsesRealShieldAndRebound(GameTestHelper h){gelTier(h,1,1);}
    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void lateGelUsesRealShieldAndRebound(GameTestHelper h){gelTier(h,2,2);}
    private static void gelTier(GameTestHelper h,int tier,int index){var f=new F(h,"Gel"+tier,index,tier);var source=f.gel(-4,-4);var target=f.gel(-1,-4);
        String enemyName=f.owner+"_enemy";f.account(enemyName);var enemy=f.mob(DreamseekersMod.PINK_SLIME_UNIT.get(),enemyName,2,1,-4);double p=potency(tier);double[] base={0};
        f.ready().thenExecute(()->{neutral(target);damage(enemy,12*p+5);base[0]=aps(target);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==tier,"server tier matches research fixture");
            f.success(source,UnitSkill.GEL_GUARD,target);near(h,SkillEffects.shieldRemaining(target),12*p,"real installed shield budget");h.assertTrue(SkillEffects.shieldTicks(target)==160&&source.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==360,"duration and cooldown are not amplified");
            float before=target.getHealth();h.assertTrue(enemy.doHurtTarget(target),"real hostile RoN melee accepted");near(h,target.getHealth(),before-5,"only five overflow HP passes amplified shield");near(h,SkillEffects.shieldRemaining(target),0,"one exhausted shield");
            near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.15*p,"derived rebound uses same snapshot");near(h,aps(target),base[0]*(1+.15*p),"rebound changes real APS once");f.ledger();})
        .thenIdle(61).thenExecute(()->{near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"original sixty-tick rebound lease ends");near(h,aps(target),base[0],"real APS restored");h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)>0,"no refund after original effect lease");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=300)
    public static void baselineJellySixRealPulsesAndTide(GameTestHelper h){jellyTier(h,0,3);}
    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=300)
    public static void middleJellySixRealPulsesAndTide(GameTestHelper h){jellyTier(h,1,4);}
    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=300)
    public static void lateJellySixRealPulsesAndTide(GameTestHelper h){jellyTier(h,2,5);}
    private static void jellyTier(GameTestHelper h,int tier,int index){var f=new F(h,"Jelly"+tier,index,tier);var source=f.jelly(-4,-4);var target=f.gel(-1,-4);var read=watchHeal(target);double p=potency(tier);long[] start={0};double[] movement={0};
        f.ready().thenExecute(()->{target.setHealth(20);movement[0]=target.getAttributeValue(Attributes.MOVEMENT_SPEED);start[0]=f.level.getGameTime();f.success(source,UnitSkill.JELLY_FLOW,target);h.assertTrue(SkillEffects.healTicks(target)==120&&source.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)==240,"six-second flow and twelve-second cooldown unchanged");})
        .thenIdle(21).thenExecute(()->{near(h,target.getHealth(),20+2*p,"first real pulse budget");h.assertTrue(read.count==1,"one first pulse");})
        .thenIdle(100).thenExecute(()->{near(h,target.getHealth(),20+12*p,"six pulses total, never duration-multiplied");h.assertTrue(read.count==6&&SkillEffects.healTicks(target)==0,"exactly six successful native heal callbacks then flow ends");
            for(int i=0;i<6;i++){near(h,read.requests.get(i),2*p,"each exact captured pulse request");h.assertTrue(read.times.get(i)==start[0]+20L*(i+1),"native twenty-tick cadence "+i);}
            near(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),.10*p,"same-cast tide multiplier");near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),movement[0]*(1+.10*p),"actual movement attribute");f.ledger();})
        .thenIdle(80).thenExecute(()->{near(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),0,"original eighty-tick tide expires");near(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),movement[0],"movement restored");h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0,"original cooldown still spent");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=230)
    public static void baselineFoxThreeRealAttributesAndNativeCachedGoal(GameTestHelper h){foxTier(h,0,6);}
    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=230)
    public static void middleFoxThreeRealAttributesAndNativeCachedGoal(GameTestHelper h){foxTier(h,1,7);}
    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=230)
    public static void lateFoxThreeRealAttributesAndNativeCachedGoal(GameTestHelper h){foxTier(h,2,8);}
    private static void foxTier(GameTestHelper h,int tier,int index){var f=new F(h,"Fox"+tier,index,tier);var source=f.fox(-3,-5);var target=f.pillager(-1,-5);var a=f.gel(1,-5);var b=f.gel(3,-5);var fourth=f.gel(4,-3);var rival=f.fox(-3,-7);
        var recipients=List.<LivingEntity>of(target,a,b);double[] base=new double[3];int[] raw={0};double amount=.2*potency(tier);
        f.ready().thenExecute(()->{target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(PERMANENT,"permanent APS fixture",.1,AttributeModifier.Operation.ADDITION));
            for(int i=0;i<3;i++)base[i]=aps(recipients.get(i));raw[0]=cached(target);crossbow(target).setToMaxAttackCooldown();near(h,crossbow(target).getAttackCooldown(),raw[0],"native unbuffed cached baseline");
            f.success(source,UnitSkill.FOX_BLESSING,source);h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==480,"Fox original cooldown");
            for(int i=0;i<3;i++){var u=recipients.get(i);h.assertTrue(blessed(u),"one of exactly three nearest combat units");near(h,SkillEffects.bonus(u,BuffKind.ATTACK_SPEED),amount,"one immutable three-recipient profile");near(h,aps(u),base[i]*(1+amount),"real attribute must not silently truncate late thirty percent");}
            h.assertTrue(!blessed(fourth),"fourth target excluded");for(int i=0;i<5;i++){crossbow(target).setToMaxAttackCooldown();near(h,crossbow(target).getAttackCooldown(),shortened(raw[0],amount),"actual cached goal reset uses exact same bonus once");}
            h.assertTrue(cached(target)==raw[0],"constructor cache never rewritten");
            // Remove only the still-uncovered fourth fixture, so the second real Fox has no eligible gap.
            fourth.discard();require(h,f.cast(rival,UnitSkill.FOX_BLESSING,rival),SkillResult.ALREADY_ACTIVE,"another Fox cannot refresh same named effects");h.assertTrue(rival.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==0,"duplicate losing Fox keeps cooldown");f.ledger();})
        .thenIdle(121).thenExecute(()->{for(int i=0;i<3;i++){h.assertTrue(!blessed(recipients.get(i)),"original six-second blessing ends");near(h,aps(recipients.get(i)),base[i],"all three actual attributes restore");}
            crossbow(target).setToMaxAttackCooldown();near(h,crossbow(target).getAttackCooldown(),raw[0],"actual cached goal returns exact legacy baseline");h.assertTrue(target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(PERMANENT)!=null,"permanent modifier remains");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void exactThreeDimensionalTwelveBlockEligibilityNeverBorrowsAlliedNetwork(GameTestHelper h){var f=new F(h,"Boundary",9,2);var source=f.gel(-3,-4);var patient=f.gel(-1,-4);String ally=f.owner+"_ally";f.account(ally);f.tier(ally,2);AlliancesServerEvents.addAlliance(f.owner,ally);
        f.ready().thenExecute(()->{Set<Long> tickets=Set.copyOf(f.level.getForcedChunks());
            int east=f.eastEdge(),south=f.southEdge(),west=Math.min(0,f.dais.minCorner.getX()-f.dais.originPos.getX());
            h.assertTrue(east>0&&south>0,"fixture uses nonzero reviewed footprint, not a point dais");
            f.move(source,west-12,0,0);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==2,"near-side horizontal twelve blocks accepted");
            f.move(source,east+12,0,0);f.move(patient,east+11,0,0);
            h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==2,"far side exactly twelve beyond footprint (more than twelve from the corner) also gets research");
            f.success(source,UnitSkill.GEL_GUARD,patient);near(h,SkillEffects.shieldRemaining(patient),18,"far-side tier two applies 18 actual shield points, not the 12-point baseline");
            f.move(source,east+12.01,0,0);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0,"just beyond far-side twelve rejected");
            f.move(source,east+12,.01,0);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0,"same horizontal distance outside three-dimensional sphere rejected");
            f.move(source,0,12,0);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==2,"exact vertical twelve blocks accepted");
            f.move(source,0,12.01,0);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0,"vertical outside rejected");
            f.move(source,east+9,0,south+9);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0,"square footprint gaps cannot act as radius");f.move(source,-3,1,-4);
            source.setOwnerName(ally);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0,"fully researched allied owner cannot borrow this owner's live network");source.setOwnerName(f.owner);
            ResourcesServerEvents.resourcesList.remove(f.account);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0,"retired account does not authorize amplification");ResourcesServerEvents.resourcesList.add(f.account);
            CompoundTag good=f.account.addonData.copy();f.account.addonData.putInt("malformed",1);var opaque=f.account.addonData.copy();h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0&&f.account.addonData.equals(opaque),"unsupported account data denied without repair");f.account.addonData=good;
            h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==2,"restored supported live account positive control");
            h.assertTrue(java.util.concurrent.CompletableFuture.supplyAsync(()->SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)).join()==0,"off-thread query fails closed without touching network state");
            h.assertTrue(f.level.getForcedChunks().equals(tickets),"all boundary/ownership queries create no chunk tickets");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void unfinishedDestroyedDetachedOpaqueAndInvalidSourcesStayBaseline(GameTestHelper h){var f=new F(h,"Invalid",10,2);var source=f.gel(-3,-4);
        f.ready().thenExecute(()->{h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==2,"live connected positive control");
            f.dais.isBuilt=false;h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0,"unfinished dais");f.dais.isBuilt=true;
            f.dais.isDestroyedServerside=true;h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0,"destroyed dais");f.dais.isDestroyedServerside=false;
            BuildingServerEvents.getBuildings().remove(f.dais);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0,"detached dais");BuildingServerEvents.getBuildings().add(f.dais);
            BuildingServerEvents.getBuildings().remove(f.root);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0,"unrooted live island is not connected");BuildingServerEvents.getBuildings().add(f.root);
            var original=f.dais.getDataStorage().getData(ResonanceState.TYPE);CompoundTag raw=new CompoundTag();raw.putInt("schema",999);raw.putString("future","unchanged");f.dais.getDataStorage().setData(ResonanceState.TYPE,ResonanceState.read(raw));
            h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0&&f.dais.getDataStorage().getData(ResonanceState.TYPE).write().equals(raw),"future network schema fails closed and remains untouched");f.dais.getDataStorage().setData(ResonanceState.TYPE,original);
            var detached=DreamseekersMod.PINK_SLIME_UNIT.get().create(f.level);detached.setOwnerName(f.owner);f.move(detached,-3,1,-4);h.assertTrue(SkillAmplification.currentTier(detached,UnitSkill.GEL_GUARD)==0,"same-owner unregistered source cannot capture a profile");
            h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==2,"restored actual source remains eligible");source.hurt(source.damageSources().genericKill(),10000);
            h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0,"actual dead source fails closed");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=250)
    public static void successfulCastSnapshotsSurviveUpgradeAndDisconnectButNewCastsDoNot(GameTestHelper h){var f=new F(h,"Snapshot",11,1);var jelly=f.jelly(-5,-6);var patient=f.gel(-2,-6);var gel=f.gel(-5,-2);var shielded=f.gel(-2,-2);var later=f.gel(1,-6);var laterTarget=f.gel(4,-6);var read=watchHeal(patient);
        f.ready().thenExecute(()->{patient.setHealth(20);f.success(jelly,UnitSkill.JELLY_FLOW,patient);f.success(gel,UnitSkill.GEL_GUARD,shielded);near(h,SkillEffects.shieldRemaining(shielded),14.4,"middle cast installed before upgrade");
            f.tier(2);h.assertTrue(SkillAmplification.currentTier(jelly,UnitSkill.JELLY_FLOW)==2,"new query sees final tier");BuildingServerEvents.getBuildings().remove(f.root);h.assertTrue(SkillAmplification.currentTier(jelly,UnitSkill.JELLY_FLOW)==0,"new query sees severed graph");
            f.success(later,UnitSkill.GEL_GUARD,laterTarget);near(h,SkillEffects.shieldRemaining(laterTarget),12,"new disconnected cast is baseline despite final research");near(h,SkillEffects.shieldRemaining(shielded),14.4,"disconnect neither cancels nor rescales old ordinary shield");})
        .thenIdle(121).thenExecute(()->{near(h,patient.getHealth(),34.4,"all six old-cast pulses remain middle tier after upgrade and disconnect");h.assertTrue(read.count==6,"exact six old-cast pulses");for(float amount:read.requests)near(h,amount,2.4,"immutable middle pulse amount");near(h,SkillEffects.bonus(patient,BuffKind.MOVEMENT_SPEED),.12,"derived tide uses original middle snapshot, not new tier or network");near(h,SkillEffects.shieldRemaining(shielded),14.4,"shield retains original lease");f.ledger();})
        .thenIdle(41).thenExecute(()->{near(h,SkillEffects.shieldRemaining(shielded),0,"ordinary shield naturally expires with original duration");near(h,SkillEffects.shieldRemaining(laterTarget),0,"baseline shield also naturally expires");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=250)
    public static void enteringAuraAfterSuccessfulBaselineCastNeverRetroactivelyAmplifies(GameTestHelper h){var f=new F(h,"LaterEntry",12,2);var source=f.jelly(f.eastEdge()+14,-4);var target=f.gel(f.eastEdge()+17,-4);var read=watchHeal(target);
        f.ready().thenExecute(()->{target.setHealth(20);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.JELLY_FLOW)==0,"caster starts outside twelve-block aura");f.success(source,UnitSkill.JELLY_FLOW,target);f.move(source,8,1,-4);f.move(target,11,1,-4);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.JELLY_FLOW)==2,"same caster later enters live final aura");})
        .thenIdle(121).thenExecute(()->{near(h,target.getHealth(),32,"existing baseline cast never gains later amplification");h.assertTrue(read.count==6,"six original pulses");for(float amount:read.requests)near(h,amount,2,"baseline pulse immutable");near(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),.10,"baseline-derived tide not recaptured on completion");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void multipleConnectedDaisesAndBothResearchTiersNeverMultiply(GameTestHelper h){var f=new F(h,"ManyDaises",13,2);var upper=f.install(DreamseekersMod.RESONANCE_DAIS,f.dais.originPos.offset(0,24,0));var source=f.mob(DreamseekersMod.PINK_SLIME_UNIT.get(),0,12,0);var target=f.mob(DreamseekersMod.PINK_SLIME_UNIT.get(),3,12,0);
        f.ready().thenExecute(()->{h.assertTrue(ResonanceNetwork.network(f.level,f.owner).active().containsAll(List.of(f.dais,upper)),"two physically live connected daises");near(h,source.distanceToSqr(f.dais.originPos.getX()+.5,f.dais.originPos.getY()+.5,f.dais.originPos.getZ()+.5),144,"exact lower aura boundary");
            near(h,source.distanceToSqr(upper.originPos.getX()+.5,upper.originPos.getY()+.5,upper.originPos.getZ()+.5),144,"exact upper aura boundary");var profile=SkillAmplification.capture(source,UnitSkill.GEL_GUARD);h.assertTrue(profile.tier()==2,"ordered research returns final tier only");near(h,profile.potency(),1.5,"no 1.2 times 1.5 or two-dais multiplication");
            f.success(source,UnitSkill.GEL_GUARD,target);near(h,SkillEffects.shieldRemaining(target),18,"one actual shield, not27 or21.6");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void narrowLateFoxPermissionNeverRaisesGenericCapAndRevokesToExistingFallback(GameTestHelper h){var f=new F(h,"NarrowCap",14,2);var source=f.fox(-4,-4);var provider=f.gel(-4,-8);var target=f.pillager(-1,-4);var outsider=f.gel(5,-9);double[] base={0};int[] raw={0};
        f.ready().thenExecute(()->{base[0]=aps(target);raw[0]=cached(target);h.assertTrue(SkillEffects.addBuff(provider,target,"ordinary_overcap",BuffKind.ATTACK_SPEED,.80,180),"ordinary provider fixture");near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.25,"generic contribution stays at legacy quarter cap");near(h,aps(target),base[0]*1.25,"actual generic attribute also capped");
            h.assertTrue(SkillEffects.addBuff(provider,outsider,SkillEffects.FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED,.80,180),"forged trusted generic key alone is not a high-Fox capability");near(h,SkillEffects.bonus(outsider,BuffKind.ATTACK_SPEED),.25,"string name cannot grant final-tier permission");
            f.success(source,UnitSkill.FOX_BLESSING,source);near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.30,"legal late Fox wins max, not addition or multiplication");near(h,aps(target),base[0]*1.30,"real attribute honors narrow thirty percent");crossbow(target).setToMaxAttackCooldown();near(h,crossbow(target).getAttackCooldown(),shortened(raw[0],.30),"same late amount reaches cached goal consumer");
            source.setOwnerName(f.owner+"_foreign");source.setOwnerName(f.owner);near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.25,"source owner ABA revokes exact high-Fox lease");near(h,aps(target),base[0]*1.25,"actual attribute falls back, no stale high cap");crossbow(target).setToMaxAttackCooldown();near(h,crossbow(target).getAttackCooldown(),shortened(raw[0],.25),"cached goal falls back to same capped ordinary amount");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void noEligibleTargetFullHealthAndInvalidRequestsNeverSpendCooldown(GameTestHelper h){var f=new F(h,"NoTarget",15,2);var source=f.fox(-4,-4);var jelly=f.jelly(-1,-4);var gel=f.gel(15,-4);var target=f.gel(18,-4);
        f.ready().thenExecute(()->{require(h,f.cast(source,UnitSkill.FOX_BLESSING,source),SkillResult.NO_TARGET,"fully amplified Fox with only support units nearby still has no recipient");h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==0,"empty amplified Fox cast keeps entire cooldown");
            f.move(gel,-4,1,-8);f.move(target,-1,1,-8);require(h,f.cast(jelly,UnitSkill.JELLY_FLOW,target),SkillResult.FULL_HEALTH,"full-health ordinary target never starts amplified flow");h.assertTrue(jelly.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)==0&&!SkillEffects.hasHeal(target),"full health rejection no effect or cooldown");
            require(h,f.cast(gel,UnitSkill.GEL_GUARD,gel),SkillResult.INVALID_TARGET,"self token not a Gel ally");h.assertTrue(gel.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==0,"rejected Gel retains cooldown");f.ledger();}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=230)
    public static void excludedSkillsPulseAndOriginalFireflyExceptionRemainUnamplified(GameTestHelper h){var f=new F(h,"Excluded",16,2);var source=f.fox(-4,-4);var target=f.gel(-1,-4);var worker=f.mob(DreamseekersMod.FIREFLY_WORKER.get(),-4,1,-8);double[] aps={0},move={0};
        f.ready().thenExecute(()->{for(UnitSkill skill:UnitSkill.values())if(skill!=UnitSkill.GEL_GUARD&&skill!=UnitSkill.JELLY_FLOW&&skill!=UnitSkill.FOX_BLESSING){var profile=SkillAmplification.capture(source,skill);h.assertTrue(profile.tier()==0,"excluded skill cannot capture amplified tier: "+skill);near(h,profile.potency(),1,"excluded potency baseline: "+skill);}
            h.assertTrue(UnitSkill.MOTHER_BROOD.durationTicks()==600&&UnitSkill.MOTHER_BROOD.cooldownTicks()==900&&UnitSkill.SHADOW_HAND_GRIP.durationTicks()==20,"summon/hard-control contract constants unchanged");
            aps[0]=aps(target);move[0]=worker.getAttributeValue(Attributes.MOVEMENT_SPEED);f.success(worker,UnitSkill.FIREFLY_RUSH,worker);near(h,SkillEffects.bonus(worker,BuffKind.MOVEMENT_SPEED),.35,"original live Firefly thirty-five exception remains, not multiplied");near(h,worker.getAttributeValue(Attributes.MOVEMENT_SPEED),move[0]*1.35,"original real Firefly attribute cap permission retained");h.assertTrue(worker.rts().skills().cooldownTicks(UnitSkill.FIREFLY_RUSH)==500,"excluded Rush cooldown unchanged");
            var view=ResonanceNetwork.view(f.dais);h.assertTrue(ResonanceNetwork.pulse(f.peer.actor,f.dais,view.instance(),view.ownerRevision(),view.capture(),view.generation()).equals("success"),"real baseline paid building pulse");near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.10,"pulse never inherits unit-skill tier2 amplification");near(h,aps(target),aps[0]*1.10,"actual pulse attribute exactly ten percent");h.assertTrue(f.account.extra.get(DreamPassiveEconomy.SHADOW)==485,"pulse still debits original15S");})
        .thenIdle(101).thenExecute(()->{near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"original100tick pulse duration");near(h,SkillEffects.bonus(worker,BuffKind.MOVEMENT_SPEED),0,"original100tick Rush duration");near(h,worker.getAttributeValue(Attributes.MOVEMENT_SPEED),move[0],"Rush restores original movement");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=230)
    public static void amplifiedHealCallbackReentryCannotRepeatPulsesOrExceedCapturedBudget(GameTestHelper h){var f=new F(h,"HealReentry",17,2);var source=f.jelly(-4,-4);var target=f.gel(-1,-4);var read=watchHeal(target);boolean[] entered={false},reentered={false};
        f.ready().thenExecute(()->{target.setHealth(20);read.inflate=true;read.each=()->{if(entered[0]){reentered[0]=true;return;}entered[0]=true;try{SkillEffects.tick(f.level);}finally{entered[0]=false;}};f.success(source,UnitSkill.JELLY_FLOW,target);})
        .thenIdle(21).thenExecute(()->{near(h,target.getHealth(),23,"positive100HP callback is bounded by this captured3HP budget");h.assertTrue(read.count==1&&!reentered[0],"recursive tick cannot post the same amplified pulse twice");})
        .thenIdle(100).thenExecute(()->{near(h,target.getHealth(),38,"six captured3HP pulses, no reentry or event multiplication");h.assertTrue(read.count==6&&!reentered[0]&&SkillEffects.healTicks(target)==0,"exactly six callbacks and one completion");near(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),.15,"one original final-tier tide");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=230)
    public static void amplifiedHealIdentityAndAllianceAbaInsideEventCannotPublishOrComplete(GameTestHelper h){var f=new F(h,"HealIdentity",18,2);String ally=f.owner+"_ally";f.account(ally);AlliancesServerEvents.addAlliance(f.owner,ally);
        var a=f.jelly(-5,-6);var ta=f.gel(-2,-6);var b=f.jelly(-5,-2);var tb=f.mob(DreamseekersMod.PINK_SLIME_UNIT.get(),ally,-2,1,-2);var ra=watchHeal(ta);var rb=watchHeal(tb);
        f.ready().thenExecute(()->{ta.setHealth(20);tb.setHealth(20);ra.once=()->{ta.setOwnerName(ally);ta.setOwnerName(f.owner);};rb.once=()->{AlliancesServerEvents.removeAlliance(f.owner,ally);AlliancesServerEvents.addAlliance(f.owner,ally);};
            f.success(a,UnitSkill.JELLY_FLOW,ta);f.success(b,UnitSkill.JELLY_FLOW,tb);})
        .thenIdle(121).thenExecute(()->{for(var t:List.of(ta,tb)){near(h,t.getHealth(),20,"mutation during actual heal callback blocks current HP write and all future pulses");h.assertTrue(SkillEffects.healTicks(t)==0,"invalidated flow removed");near(h,SkillEffects.bonus(t,BuffKind.MOVEMENT_SPEED),0,"no tide on interrupted amplified flow");}
            h.assertTrue(ra.count==1&&rb.count==1,"each real first event observed exactly once before revocation");h.assertTrue(a.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0&&b.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0,"identity failures never refund cast cooldown");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void actualHurtCallbackSourceAbaRevokesAmplifiedShieldWithoutRebound(GameTestHelper h){var f=new F(h,"ShieldCallback",19,2);var source=f.gel(-4,-4);var target=f.gel(-1,-4);String enemyName=f.owner+"_enemy";f.account(enemyName);var enemy=f.mob(DreamseekersMod.PINK_SLIME_UNIT.get(),enemyName,2,1,-4);
        f.ready().thenExecute(()->{neutral(target);damage(enemy,10);f.success(source,UnitSkill.GEL_GUARD,target);near(h,SkillEffects.shieldRemaining(target),18,"real final-tier shield before event");listen();PROBE.damageEvents.put(target.getUUID(),0);PROBE.damageCallbacks.put(target.getUUID(),()->{source.setOwnerName(enemyName);source.setOwnerName(f.owner);});
            float hp=target.getHealth();h.assertTrue(enemy.doHurtTarget(target),"genuine hostile hurt triggers original Forge event boundary");h.assertTrue(PROBE.damageEvents.get(target.getUUID())==1,"one real final-damage dispatch");near(h,target.getHealth(),hp-10,"old invalid shield cannot absorb a new owner's damage");near(h,SkillEffects.shieldRemaining(target),0,"source ABA removed original shield");near(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"identity cleanup is not an enemy break and grants no rebound");h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)>0,"successful cast cooldown remains spent");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=230)
    public static void actualCasterDeathLeaveAndOwnerAbaRemoveAmplifiedShortStates(GameTestHelper h){var f=new F(h,"Endpoints",20,2);var gel=f.gel(-6,-7);var shield=f.gel(-3,-7);var jelly=f.jelly(-6,-2);var patient=f.gel(-3,-2);var fox=f.fox(3,-6);var target=f.pillager(6,-6);double[] base={0};
        f.ready().thenExecute(()->{patient.setHealth(20);base[0]=aps(target);f.success(gel,UnitSkill.GEL_GUARD,shield);f.success(jelly,UnitSkill.JELLY_FLOW,patient);f.success(fox,UnitSkill.FOX_BLESSING,fox);h.assertTrue(blessed(target),"real final-tier Fox positive control");near(h,aps(target),base[0]*1.3,"real high-tier attribute before removal");
            gel.hurt(gel.damageSources().genericKill(),10000);jelly.discard();fox.setOwnerName(f.owner+"_foreign");fox.setOwnerName(f.owner);near(h,SkillEffects.shieldRemaining(shield),0,"actual source death cancels shield");h.assertTrue(SkillEffects.healTicks(patient)==0,"actual source leave cancels flow");near(h,aps(target),base[0],"owner ABA clears high-Fox real modifier immediately");})
        .thenIdle(121).thenExecute(()->{near(h,patient.getHealth(),20,"left source never resumes or completes healing");near(h,SkillEffects.bonus(patient,BuffKind.MOVEMENT_SPEED),0,"no canceled tide");near(h,SkillEffects.bonus(shield,BuffKind.ATTACK_SPEED),0,"death is not shield break");near(h,aps(target),base[0],"high-Fox permission never revives");h.assertTrue(fox.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)>0,"surviving source retains cooldown");}).thenSucceed();}

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1000)
    public static void actuallyUnloadedBuildingRegionCannotContributeOrBeLoadedByQuery(GameTestHelper h){var f=new F(h,"ChunkBoundary",21,2);var source=f.gel(-3,-4);var originalPosition=source.blockPosition().immutable();var originalChunk=new ChunkPos(originalPosition);final int originalId=source.getId();final UUID originalUuid=source.getUUID();long[] returnedAt={-1};
        final Set<Long> oldTickets=Set.copyOf(f.forced),destinationTickets=new HashSet<>();var destination=f.dais.originPos.offset(512,1,-4);
        // Only the original building-region fixture tickets are released. Keep this sole source's
        // distant destination loaded, and never enter a FULL-but-not-entity-ticking section.
        f.ready().thenExecute(()->{h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==2,"connected before actual region transition");listen();PROBE.continuousLeaves.put(originalUuid,0);f.load(destination);destinationTickets.addAll(f.forced);destinationTickets.removeAll(oldTickets);h.assertTrue(!destinationTickets.isEmpty(),"separate distant destination has its own fixture tickets");})
        .thenWaitUntil(()->{continuousSource(f,source,originalId,originalUuid);h.assertTrue(f.level.areEntitiesLoaded(new ChunkPos(destination).toLong())&&f.level.isPositionEntityTicking(destination),"real destination entity data and ticking ready before moving the original object");})
        .thenExecute(()->{h.assertTrue(UnitServerEvents.releaseTrackedUnitChunkTicket(source),"release only this exact fixture lifetime's native entity ticket before moving; never another object's ticket");f.move(source,512,1,-4);continuousSource(f,source,originalId,originalUuid);})
        .thenIdle(25).thenExecute(()->{continuousSource(f,source,originalId,originalUuid);for(long c:oldTickets){h.assertTrue(f.forced.remove(c),"release only the captured original-region fixture ticket");f.level.setChunkForced(ChunkPos.getX(c),ChunkPos.getZ(c),false);}for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){var c=new ChunkPos(originalChunk.x+dx,originalChunk.z+dz);f.level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED,c,2,c);}h.assertTrue(f.forced.containsAll(destinationTickets)&&f.level.getForcedChunks().containsAll(destinationTickets),"distant source tickets remain held throughout node unload");continuousSource(f,source,originalId,originalUuid);})
        .thenWaitUntil(()->{continuousSource(f,source,originalId,originalUuid);h.assertTrue(!f.level.hasChunkAt(f.dais.originPos)&&!f.level.hasChunksAt(f.dais.minCorner,f.dais.maxCorner)&&f.level.getChunkSource().getChunkNow(f.dais.originPos.getX()>>4,f.dais.originPos.getZ()>>4)==null,"real node chunks unload after only original-region tickets leave; not a fake removed flag");})
        .thenExecute(()->{continuousSource(f,source,originalId,originalUuid);var tickets=Set.copyOf(f.level.getForcedChunks());h.assertTrue(f.level.getChunkSource().getChunkNow(f.dais.originPos.getX()>>4,f.dais.originPos.getZ()>>4)==null,"direct getChunkNow is null immediately before network query");h.assertTrue(ResonanceNetwork.network(f.level,f.owner).active().isEmpty(),"registered but genuinely unloaded node cannot be active");h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==0,"caster with no loaded own network is baseline");h.assertTrue(!f.level.hasChunkAt(f.dais.originPos)&&f.level.getChunkSource().getChunkNow(f.dais.originPos.getX()>>4,f.dais.originPos.getZ()>>4)==null&&f.level.getForcedChunks().equals(tickets),"network/profile queries do not load or force missing node chunks");f.load(f.dais.originPos);f.load(f.root.originPos);})
        .thenWaitUntil(()->{continuousSource(f,source,originalId,originalUuid);h.assertTrue(f.level.hasChunksAt(f.dais.minCorner,f.dais.maxCorner)&&f.level.hasChunksAt(f.root.minCorner,f.root.maxCorner)&&f.level.areEntitiesLoaded(originalChunk.toLong())&&f.level.isPositionEntityTicking(originalPosition),"native original landing section is entity-loaded and ticking before moving back, not merely FULL");})
        .thenExecute(()->{f.move(source,-3,1,-4);returnedAt[0]=f.level.getGameTime();System.out.println("LIGHT_AMPLIFICATION_RELOAD_IMMEDIATE "+reloadStatus(f,source,originalId,originalUuid));continuousSource(f,source,originalId,originalUuid);})
        .thenWaitUntil(()->{h.assertTrue(f.level.getGameTime()>returnedAt[0],"wait for a real server tick after entering the reloaded chunk");continuousSource(f,source,originalId,originalUuid);
            h.assertTrue(f.level.hasChunksAt(f.root.minCorner,f.root.maxCorner)&&f.level.hasChunksAt(f.dais.minCorner,f.dais.maxCorner)
                &&ExtendedResourcesSync.account(f.owner)==f.account&&com.dreamseekers.unitprogression.DreamSpecialistResearch.ownerSupported(f.owner,false)
                &&LightResonanceResearch.tier(f.owner,false)==2&&ResonanceNetwork.live(f.dais)&&ResonanceNetwork.network(f.level,f.owner).rank(f.dais)>0,
                "original source/account/research/rooted node must remain ready after real reload; "+reloadStatus(f,source,originalId,originalUuid));})
        .thenExecute(()->{continuousSource(f,source,originalId,originalUuid);h.assertTrue(SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD)==2,"same live source and actual reloaded connected building become eligible for future casts; "+reloadStatus(f,source,originalId,originalUuid));System.out.println("LIGHT_AMPLIFICATION_RELOAD_READY "+reloadStatus(f,source,originalId,originalUuid));PROBE.continuousLeaves.remove(originalUuid);}).thenSucceed();}

    private static void continuousSource(F f,PinkSlimeUnit source,int originalId,UUID originalUuid){
        f.h.assertTrue(PROBE.continuousLeaves.getOrDefault(originalUuid,-1)==0&&source.isAlive()&&!source.isRemoved()&&source.isAddedToWorld()
            &&source.getId()==originalId&&source.getUUID().equals(originalUuid)&&f.level.getEntity(originalId)==source&&f.level.getEntity(originalUuid)==source
            &&UnitServerEvents.getAllUnits().contains(source),"original source must never leave while only the building region unloads; leaveEvents="+PROBE.continuousLeaves.get(originalUuid)+" "+reloadStatus(f,source,originalId,originalUuid));
    }

    /** Diagnostic only: never add/recreate entities, force chunks, restore research or repair identity. */
    private static String reloadStatus(F f,PinkSlimeUnit source,int originalId,UUID originalUuid){
        return "time="+f.level.getGameTime()+" sourcePos="+source.position()+" sourceId="+source.getId()+" sourceUuid="+source.getUUID()
            +" expectedId="+originalId+" expectedUuid="+originalUuid+" alive="+source.isAlive()+" removed="+source.isRemoved()+" added="+source.isAddedToWorld()
            +" removalReason="+source.getRemovalReason()+" byIdSame="+(f.level.getEntity(originalId)==source)+" byUuidSame="+(f.level.getEntity(originalUuid)==source)
            +" nativeUnitTracked="+UnitServerEvents.getAllUnits().contains(source)+" sourceChunkLoaded="+f.level.hasChunkAt(source.blockPosition())+" sourceEntityTicking="+f.level.isPositionEntityTicking(source.blockPosition())+" sourceEntitiesLoaded="+f.level.areEntitiesLoaded(source.chunkPosition().toLong())
            +" rootChunks="+f.level.hasChunksAt(f.root.minCorner,f.root.maxCorner)+" daisChunks="+f.level.hasChunksAt(f.dais.minCorner,f.dais.maxCorner)
            +" rootListed="+BuildingServerEvents.getBuildings().contains(f.root)+" rootBuilt="+f.root.isBuilt+" rootHealth="+f.root.getHealth()
            +" rootProduction="+((com.dreamseekers.worldtree.DreamAnchorPlacement)f.root).productionAvailable()+" rootState="+((com.dreamseekers.worldtree.DreamAnchorPlacement)f.root).tree().supported
            +" daisListed="+BuildingServerEvents.getBuildings().contains(f.dais)+" daisLive="+ResonanceNetwork.live(f.dais)+" networkRank="+ResonanceNetwork.network(f.level,f.owner).rank(f.dais)
            +" sourceOwner="+source.getOwnerName()+" rootOwner="+f.root.ownerName+" daisOwner="+f.dais.ownerName+" accountSame="+(ExtendedResourcesSync.account(f.owner)==f.account)
            +" ownerSupported="+com.dreamseekers.unitprogression.DreamSpecialistResearch.ownerSupported(f.owner,false)+" researchTier="+LightResonanceResearch.tier(f.owner,false)
            +" auraTier="+SkillAmplification.currentTier(source,UnitSkill.GEL_GUARD);
    }

    @GameTest(templateNamespace="dreamseekers_light_amplification",template="dreamseekers_test_arena",batch="light_amplification_expiry",timeoutTicks=230)
    public static void exactNaturalFoxDeadlineKeepsNativeAttributeAndCachedCadenceConsistent(GameTestHelper h){var f=new F(h,"ExactDeadline",22,2);var source=f.fox(-4,-4);var target=f.pillager(-1,-4);double[] base={0};int[] raw={0};DeadlineRead[] read={null};
        f.ready().thenExecute(()->{base[0]=target.getNonBaseAttacksPerSecond();raw[0]=cached(target);double nativeCooldown=target.getAttackCooldown(),nativeAttribute=aps(target);f.success(source,UnitSkill.FOX_BLESSING,source);near(h,aps(target),nativeAttribute*1.3,"raw APS positive control honors final-tier thirty percent");near(h,target.getNonBaseAttacksPerSecond(),20/(nativeCooldown/1.3+35),"Pillager native effective APS includes its unchanged35tick charge term");listen();read[0]=new DeadlineRead(target,f.level.getGameTime()+120);PROBE.deadlines.put(target.getUUID(),read[0]);})
        .thenIdle(121).thenExecute(()->{h.assertTrue(read[0].observations==1,"exact natural120thtick LivingTick probe ran before LevelTick.END cleanup");near(h,read[0].bonus,0,"expired contribution already has no semantic bonus");near(h,read[0].actualAps,base[0],"ordinary native APS reader cannot leak old thirty percent during expiry tick");near(h,read[0].actualCooldown,raw[0],"cached goal agrees with native APS at same exact deadline");h.assertTrue(cached(target)==raw[0],"probe never alters constructor cache");}).thenSucceed();}
}
