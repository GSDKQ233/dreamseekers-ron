package com.dreamseekers;

import com.dreamseekers.aaroncos.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.unit.*;
import com.dreamseekers.worldtree.WorldTreeModel;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real registered peer invokes the same service as the panel command. Initial bodies/research/
 * physical prerequisite buildings are fixtures, NOT evidence of their production/research purchase.
 * Pair fees and replacement fees/births are exclusively performed by the actual product service. */
@GameTestHolder("dreamseekers_hand_bond") @PrefixGameTestTemplate(false)
public final class HandBondGameTests {
    static final String BATCH="dreamseekers_hand_bond";
    static int arena;
    static final List<F> FIXTURES=new ArrayList<>();
    static final class F implements AutoCloseable {
        final GameTestHelper h;final AaroncosTrainingGameTests.F training;
        final String owner,enemy;final Resources account;final PassiveIncomePeer peer;
        final List<Mob> bodies=new ArrayList<>();final List<Object> listeners=new ArrayList<>();final List<PassiveIncomePeer> extraPeers=new ArrayList<>();final BlockPos center;
        F(GameTestHelper h,String suffix,boolean buildings){
            this.h=h;training=new AaroncosTrainingGameTests.F(h,"Bond_"+suffix,180000+512*arena++);
            owner=training.owner;enemy=owner+"_enemy";account=training.r;
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(enemy,DreamseekersMod.DREAMSEEKERS));
            ResourcesServerEvents.resourcesList.add(new Resources(enemy,0,0,0));
            peer=new PassiveIncomePeer(h.getLevel(),owner);
            center=training.origin.offset(0,1,0);
            // Physical Wind branch satisfies the original prerequisite without Shadow ascension passive income.
            // No global earnWind fixture: the current normal recipe remains full400/250/350+100S.
            if(buildings){training.tree(WorldTreeModel.WIND_V4,0);training.lab();training.hall();training.tree(WorldTreeModel.BASE_V3,192);
                h.assertTrue(BuildingServerEvents.getTotalPopulationSupply(owner)>=16,"two physical capitols supply enough for two8pop hands; no population cheat");}
            for(int x=(center.getX()-32)>>4;x<=(center.getX()+32)>>4;x++)for(int z=(center.getZ()-32)>>4;z<=(center.getZ()+32)>>4;z++){
                long key=net.minecraft.world.level.ChunkPos.asLong(x,z);h.getLevel().setChunkForced(x,z,true);training.forced.add(key);}
            // Separate flat, loaded combat floor, outside actual prerequisite geometry.
            centerFloor();FIXTURES.add(this);
        }
        private void centerFloor(){for(BlockPos p:BlockPos.betweenClosed(center.offset(-24,-1,-24),center.offset(24,8,-8)))h.getLevel().setBlock(p,p.getY()==center.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);}
        <T extends Mob>T add(T e,String name,int x){((Unit)e).setOwnerName(name);e.setNoAi(true);e.moveTo(center.getX()+x+.5,center.getY(),center.getZ()-16+.5);
            e.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);e.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);
            e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            h.assertTrue(h.getLevel().addFreshEntity(e),"actual formal body registered");bodies.add(e);return e;}
        AaroncosLeftHandUnit left(){return add(DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(h.getLevel()),owner,0);}
        AaroncosRightHandUnit right(){return add(DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel()),owner,5);}
        GoldenFoxUnit enemy(){var e=add(DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel()),enemy,12);e.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(40);return e;}
        void unlock(){ResearchServerEvents.addResearch(owner,DreamseekersMod.id("hand_pairing"));}
        HandBondService.Result pair(AaroncosLeftHandUnit l,AaroncosRightHandUnit r){return HandBondService.pair(peer.actor,l.getUUID(),r.getUUID());}
        HandBondData.Bond bond(Entity e){return HandBondData.get(h.getLevel()).forEntity(e.getUUID());}
        int shadow(){return account.extra.get(DreamPassiveEconomy.SHADOW);}
        void paired(AaroncosLeftHandUnit l,AaroncosRightHandUnit r){unlock();int before=shadow();h.assertTrue(pair(l,r)==HandBondService.Result.PAIRED&&shadow()==before-40,"actual service exact40shadow pairing");}
        void hit(LivingEntity victim,LivingEntity enemy){victim.invulnerableTime=0;h.assertTrue(victim.hurt(h.getLevel().damageSources().mobAttack(enemy),40),"actual hostile hurt accepted");}
        void kill(AaroncosLeftHandUnit left,LivingEntity enemy){left.setHealth(1);hit(left,enemy);h.assertTrue(!left.isAlive(),"real hostile damage caused death");}
        void hp(LivingEntity e,float value,String why){h.assertTrue(Math.abs(e.getHealth()-value)<.002,why+" expected="+value+" actual="+e.getHealth());}
        long count(){return training.handCount();}
        void scores(int replacements){assertScores(h,owner,replacements); }
        String diagnostics(Entity survivor){var b=bond(survivor);var target=survivor instanceof Mob m?m.getTarget():null;return " actual[count="+count()+",pop="+UnitServerEvents.getCurrentPopulation(owner)+",supply="+BuildingServerEvents.getTotalPopulationSupply(owner)+",F="+account.food+",W="+account.wood+",O="+account.ore+",S="+shadow()+",tick="+h.getLevel().getGameTime()+",missing="+(b==null?-1:b.missing)+",readyAt="+(b==null?-1:b.readyAt)+",lastCombat="+(b==null?-1:b.lastCombat)+",survivor="+survivor.getUUID()+",alive="+survivor.isAlive()+",casting="+(survivor instanceof ControlledAaroncosHand hand&&hand.isCasting())+",target="+(target==null?"null":target.getClass().getSimpleName()+":"+target.getUUID()+":alive="+target.isAlive()+":removed="+target.isRemoved()+":distance="+survivor.distanceTo(target))+"]";}
        public void close(){extraPeers.forEach(PassiveIncomePeer::close);ResearchServerEvents.removeAllResearchFor(enemy);listeners.forEach(net.minecraftforge.common.MinecraftForge.EVENT_BUS::unregister);bodies.forEach(Entity::discard);peer.close();training.close();ResearchServerEvents.removeResearch(owner,DreamseekersMod.id("hand_pairing"));PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(enemy));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(enemy));FIXTURES.remove(this);}
        void done(){close();h.succeed();}
    }
    static void assertScores(GameTestHelper h,String owner,int replacements){
        var player=PlayerServerEvents.getRTSPlayer(owner);
        h.assertTrue(player!=null,"formal score owner exists");
        var scores=player.scores.getScoreList();
        h.assertTrue(scores.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==replacements
            &&scores.get(RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED)==replacements
            &&scores.get(RTSPlayerScoresEnum.WORKER_UNITS_PRODUCED)==0,
            "only successful paid replacement counts once as total+military, never worker; expected="+replacements+" actual="+scores);
    }
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel l){for(F f:List.copyOf(FIXTURES))f.close();}

    @GameTest(templateNamespace="dreamseekers_hand_bond",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void realCommandServiceOwnershipRangeResearchAndExactFee(GameTestHelper h){
        F f=new F(h,"authority",false);var l=f.left();var r=f.right();
        h.startSequence().thenIdle(10).thenExecute(()->{
            h.assertTrue(f.pair(l,r)==HandBondService.Result.LOCKED&&f.shadow()==1000,"research required before any fee");f.unlock();
            r.setOwnerName(f.enemy);h.assertTrue(f.pair(l,r)==HandBondService.Result.NOT_OWNER&&f.shadow()==1000,"same-owner required");r.setOwnerName(f.owner);
            r.setPos(l.getX()+17,l.getY(),l.getZ());h.assertTrue(f.pair(l,r)==HandBondService.Result.TOO_FAR&&f.shadow()==1000,"outside16 rejects before payment");r.setPos(l.getX()+5,l.getY(),l.getZ());
            f.account.extra.put(DreamPassiveEconomy.SHADOW,39);h.assertTrue(f.pair(l,r)==HandBondService.Result.INSUFFICIENT_RESOURCES&&f.shadow()==39,"insufficient shadow atomic rejection");f.account.extra.put(DreamPassiveEconomy.SHADOW,1000);
            int result=h.getLevel().getServer().getCommands().performPrefixedCommand(f.peer.actor.createCommandSourceStack(),"rts-hand-pair "+l.getUUID()+" "+r.getUUID());
            h.assertTrue(result==1&&f.shadow()==960&&f.bond(l)==f.bond(r)&&f.bond(l)!=null,"registered player command creates one actual paid bond");
            h.assertTrue(f.pair(l,r)==HandBondService.Result.ALREADY_PAIRED&&f.shadow()==960,"duplicate cannot charge again");
            h.assertTrue(HandBondService.unpair(f.peer.actor,l.getUUID())==HandBondService.Result.UNPAIRED&&f.shadow()==960&&f.bond(l)==null,"unpair no refund");f.scores(0);f.done();
        });
    }
    @GameTest(templateNamespace="dreamseekers_hand_bond",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void finalActualDamageSplitOnceAfterArmorAndHealingIndependent(GameTestHelper h){
        F f=new F(h,"damage",false);var l=f.left();var r=f.right();var control=f.left();var enemy=f.enemy();
        h.startSequence().thenIdle(10).thenExecute(()->{
            l.getAttribute(Attributes.ARMOR).setBaseValue(10);control.getAttribute(Attributes.ARMOR).setBaseValue(10);r.getAttribute(Attributes.ARMOR).setBaseValue(100);r.setAbsorptionAmount(100);
            float controlHp=control.getHealth(),lh=l.getHealth(),rh=r.getHealth();f.hit(control,enemy);float actual=controlHp-control.getHealth();h.assertTrue(actual>0,"unpaired actual mitigated baseline");
            f.paired(l,r);f.hit(l,enemy);f.hp(l,lh-actual/2,"primary final half");f.hp(r,rh-actual/2,"partner exact half no second100armor mitigation or recursion");h.assertTrue(r.getAbsorptionAmount()==100,"partner absorption not charged again for already-final HP damage");
            float partner=r.getHealth();l.heal(3);f.hp(l,lh-actual/2+3,"ordinary healing only healed hand");f.hp(r,partner,"healing never duplicated to partner");
            r.setPos(l.getX()+25,l.getY(),l.getZ());float before=l.getHealth();f.hit(l,enemy);f.hp(l,before-actual,"outside24 full original damage");f.hp(r,partner,"outside24 no share");f.done();
        });
    }
    @GameTest(templateNamespace="dreamseekers_hand_bond",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void administrativeRemovalOwnershipAndNoncombatDeathCancel(GameTestHelper h){
        F f=new F(h,"lifecycle",false);var l=f.left();var r=f.right();
        h.startSequence().thenIdle(10).thenExecute(()->{f.paired(l,r);r.setOwnerName(f.enemy);r.setOwnerName(f.owner);h.assertTrue(f.bond(l)==null,"owner ABA cannot restore pair");f.paired(l,r);r.discard();h.assertTrue(f.bond(l)==null,"admin discard not combat death");
            var next=f.right();f.paired(l,next);l.kill();h.assertTrue(f.bond(next)==null,"administrative kill cannot request paid combat replacement");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_hand_bond",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void bothCombatDeathsCancelPendingReplacement(GameTestHelper h){
        F f=new F(h,"bothdead",false);var l=f.left();var r=f.right();var enemy=f.enemy();
        h.startSequence().thenIdle(10).thenExecute(()->{f.paired(l,r);f.kill(l,enemy);h.assertTrue(f.bond(r)!=null&&f.bond(r).missing==1,"first real death pending");r.setHealth(1);f.hit(r,enemy);h.assertTrue(!r.isAlive()&&f.bond(r)==null&&f.shadow()==960,"second real death cancels no spawn or refund");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_hand_bond",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=2150)
    public static void naturalNinetySecondsAndContinuousTenSecondsQuietPayFullPrice(GameTestHelper h){
        F f=new F(h,"natural",true);var l=f.left();var r=f.right();var enemy=f.enemy();long[] death={0},combat={0};UUID[] old={l.getUUID()};
        h.runAtTickTime(10,()->{f.paired(l,r);f.kill(l,enemy);death[0]=h.getLevel().getGameTime();
            h.assertTrue(f.bond(r)!=null&&f.bond(r).missing==1&&f.bond(r).readyAt==death[0]+1800,"real death sets exact90second deadline");f.scores(0);});
        h.runAtTickTime(1790,()->{h.assertTrue(f.count()==1&&f.shadow()==960,"no early90second replacement/payment"+f.diagnostics(r));f.scores(0);f.hit(r,enemy);combat[0]=h.getLevel().getGameTime();
            System.out.println("HAND_BOND_BEFORE_REAL_STOP"+f.diagnostics(r));
            enemy.discard(); // End this fixture threat, not a gameplay kill or economic event.
            new UnitActionItem(f.owner,UnitAction.STOP,-1,new int[]{r.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
            h.assertTrue(enemy.isRemoved()&&r.getTarget()==null&&!r.isCasting(),"fixture enemy actually left world and real STOP clears retaliation before continuous quiet clock");});
        h.runAtTickTime(1820,()->h.assertTrue(f.count()==1&&f.shadow()==960&&h.getLevel().getGameTime()>death[0]+1800,"90seconds alone insufficient while recently fighting"));
        h.runAtTickTime(1980,()->h.assertTrue(f.count()==1&&f.shadow()==960&&h.getLevel().getGameTime()<combat[0]+200,"continuous ten quietseconds not yet met"));
        h.runAtTickTime(2030,()->{var b=f.bond(r);h.assertTrue(b!=null&&b.missing==0&&!b.left.equals(old[0])&&b.right.equals(r.getUUID()),"natural recovery replaces only missing UUID in original bond"+f.diagnostics(r));
            var restored=h.getLevel().getEntity(b.left);h.assertTrue(restored instanceof AaroncosLeftHandUnit&&restored.isAlive()&&h.getLevel().hasChunkAt(restored.blockPosition())&&h.getLevel().noCollision(restored),"real safe loaded replacement body");
            h.assertTrue(f.count()==2&&UnitServerEvents.getCurrentPopulation(f.owner)==16,"exact8 replacement population not extra copy");
            h.assertTrue(f.account.food==9600&&f.account.wood==9750&&f.account.ore==9650&&f.shadow()==860,"full normal400F250W350O plus100shadow, pair40 not refunded"+f.diagnostics(r));f.scores(1);});
        h.runAtTickTime(2090,()->{h.assertTrue(f.count()==2&&f.shadow()==860,"no second replacement/payment on later ticks");f.scores(1);f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_hand_bond",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=2100)
    public static void naturalReplacementWaitsForMoneyPopulationAndPhysicalPrerequisites(GameTestHelper h){
        F f=new F(h,"gates",true);var l=f.left();var r=f.right();var enemy=f.enemy();List<Mob> blockers=new ArrayList<>();
        var lab=f.training.hosts.stream().filter(p->p instanceof com.dreamseekers.building.DreamLaboratoryPlacement).findFirst().orElseThrow();
        h.runAtTickTime(10,()->{f.paired(l,r);f.kill(l,enemy);f.account.extra.put(DreamPassiveEconomy.SHADOW,99);});
        h.runAtTickTime(1850,()->{h.assertTrue(f.count()==1&&f.shadow()==99&&f.account.food==10000,"mature pending cannot partially pay insufficient100shadow"+f.diagnostics(r));f.scores(0);
            int cap=Math.min(UnitServerEvents.maxPopulation,BuildingServerEvents.getTotalPopulationSupply(f.owner));
            while(UnitServerEvents.getCurrentPopulation(f.owner)+8<=cap)blockers.add(f.left());
            h.assertTrue(UnitServerEvents.getCurrentPopulation(f.owner)+8>cap,"real bodies fill owned population capacity");f.account.extra.put(DreamPassiveEconomy.SHADOW,960);});
        h.runAtTickTime(1900,()->{h.assertTrue(f.bond(r).missing==1&&f.shadow()==960&&f.account.food==10000,"full actual population cannot pay or spawn");f.scores(0);blockers.forEach(Entity::discard);BuildingServerEvents.getBuildings().remove(lab);});
        h.runAtTickTime(1950,()->{h.assertTrue(f.count()==1&&f.bond(r).missing==1&&f.shadow()==960,"missing original laboratory prerequisite cannot replace");f.scores(0);BuildingServerEvents.getBuildings().add(lab);});
        h.runAtTickTime(2030,()->{h.assertTrue(f.count()==2&&f.bond(r).missing==0&&f.shadow()==860&&f.account.food==9600&&UnitServerEvents.getCurrentPopulation(f.owner)==16,"restored gates perform one fullprice actual replacement"+f.diagnostics(r));f.scores(1);f.done();});
    }

    @GameTest(templateNamespace="dreamseekers_hand_bond",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void finalWritePreventsFalseDeathAndReturnsPartnerOverflowToVictim(GameTestHelper h){
        F f=new F(h,"lethal",false);var l=f.left();var r=f.right();var control=f.left();var enemy=f.enemy();
        h.startSequence().thenIdle(10).thenExecute(()->{
            l.getAttribute(Attributes.ARMOR).setBaseValue(0);control.getAttribute(Attributes.ARMOR).setBaseValue(0);
            float before=control.getHealth();f.hit(control,enemy);float actual=before-control.getHealth();h.assertTrue(actual>10,"actual full hit baseline");
            f.paired(l,r);l.setHealth(actual*.75f);float right=r.getHealth();f.hit(l,enemy);
            h.assertTrue(l.isAlive()&&f.bond(l).missing==0,"shared hit never transiently kills target that survives its half");f.hp(l,actual*.25f,"only final target half committed");f.hp(r,right-actual/2,"partner half exactly once");
            l.setHealth(l.getMaxHealth());r.setHealth(3);float left=l.getHealth();f.hit(l,enemy);
            h.assertTrue(!r.isAlive()&&l.isAlive(),"partner true death with original target alive");f.hp(l,left-(actual-3),"partner absorbs only remaining3 HP; excess returns to original victim");
            h.assertTrue(f.bond(l)!=null&&f.bond(l).missing==2,"shared hostile death records right replacement not original target death");f.done();
        });
    }
    @GameTest(templateNamespace="dreamseekers_hand_bond",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void oneActualHitCanKillBothWithoutRecursiveDamageOrReplacement(GameTestHelper h){
        F f=new F(h,"onehitboth",false);var l=f.left();var r=f.right();var enemy=f.enemy();
        h.startSequence().thenIdle(10).thenExecute(()->{f.paired(l,r);l.setHealth(1);r.setHealth(1);f.hit(l,enemy);
            h.assertTrue(!l.isAlive()&&!r.isAlive()&&f.bond(l)==null&&f.bond(r)==null,"one real hostile hit exhausts both with no pending replacement");
        }).thenIdle(60).thenExecute(()->{h.assertTrue(f.count()==0&&UnitServerEvents.getCurrentPopulation(f.owner)==0&&f.shadow()==960,"both corpse population released and no refund or free body");f.done();});
    }

    @GameTest(templateNamespace="dreamseekers_hand_bond",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void canceledFinalDamageAndOwnershipCallbackCannotShare(GameTestHelper h){
        F f=new F(h,"canceled",false);var l=f.left();var r=f.right();var enemy=f.enemy();boolean[] cancel={true};
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingDamageEvent> listener=e->{if(e.getEntity()==l){if(cancel[0])e.setCanceled(true);else{r.setOwnerName(f.enemy);r.setOwnerName(f.owner);}}};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,listener);f.listeners.add(listener);
        h.startSequence().thenIdle(10).thenExecute(()->{f.paired(l,r);float lh=l.getHealth(),rh=r.getHealth();l.invulnerableTime=0;l.hurt(h.getLevel().damageSources().mobAttack(enemy),40);f.hp(l,lh,"canceled final damage has no target HP loss");f.hp(r,rh,"canceled final damage cannot share");
            cancel[0]=false;l.invulnerableTime=0;l.hurt(h.getLevel().damageSources().mobAttack(enemy),40);h.assertTrue(f.bond(l)==null,"actual final callback owner ABA revokes pairing");f.hp(r,rh,"revoked partner does not receive damage");h.assertTrue(l.getHealth()<lh,"revocation must not swallow original actual hit");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_hand_bond",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void explicitChunkUnloadReasonDoesNotBecomeCombatDeath(GameTestHelper h){
        F f=new F(h,"unload",false);var l=f.left();var r=f.right();var enemy=f.enemy();var nbt=new net.minecraft.nbt.CompoundTag();float[] leftHp={0};
        h.startSequence().thenIdle(10).thenExecute(()->{f.paired(l,r);r.saveWithoutId(nbt);r.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);leftHp[0]=l.getHealth();f.hit(l,enemy);
            h.assertTrue(f.bond(l)!=null&&f.bond(l).missing==0&&f.shadow()==960&&l.getHealth()<leftHp[0],"explicit actual unload removal does not mark death, pay, or swallow unshared damage");
        }).thenIdle(40).thenExecute(()->{h.assertTrue(f.bond(l)!=null&&f.bond(l).missing==0,"absent entity alone never becomes a replacement request");var restored=DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel());restored.load(nbt);h.assertTrue(h.getLevel().addFreshEntity(restored),"original entity NBT reload");f.bodies.add(restored);
            h.assertTrue(f.bond(restored)==f.bond(l)&&f.shadow()==960,"original UUID rejoins paid pair without fee");f.done();});
    }
    public static final class CancelReplacementJoin {
        final String owner;int rejected;CancelReplacementJoin(String owner){this.owner=owner;}
        @net.minecraftforge.eventbus.api.SubscribeEvent public void join(net.minecraftforge.event.entity.EntityJoinLevelEvent e){if(e.getEntity() instanceof Unit u&&u.getOwnerName().equals(owner)&&HandBondReplacement.pending(e.getEntity())){rejected++;e.setCanceled(true);}}
    }
    @GameTest(templateNamespace="dreamseekers_hand_bond",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=2050)
    public static void realReplacementJoinCancellationCannotChargeAndLaterRetriesOnce(GameTestHelper h){
        F f=new F(h,"spawncancel",true);var l=f.left();var r=f.right();var enemy=f.enemy();var listener=new CancelReplacementJoin(f.owner);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listener);f.listeners.add(listener);
        h.runAtTickTime(10,()->{f.paired(l,r);f.kill(l,enemy);});
        h.runAtTickTime(1880,()->{h.assertTrue(listener.rejected>0&&f.count()==1&&f.bond(r).missing==1&&f.shadow()==960&&f.account.food==10000&&UnitServerEvents.getCurrentPopulation(f.owner)==8,"actual rejected birth rolls back body/population without charging"+f.diagnostics(r));f.scores(0);net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);});
        h.runAtTickTime(1980,()->{h.assertTrue(f.count()==2&&f.bond(r).missing==0&&f.shadow()==860&&f.account.food==9600&&UnitServerEvents.getCurrentPopulation(f.owner)==16,"natural retry births exactly one fullprice new hand"+f.diagnostics(r));f.scores(1);});
        h.runAtTickTime(2020,()->{f.scores(1);f.done();});
    }

    @GameTest(templateNamespace="dreamseekers_hand_bond",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=220)
    public static void actualEnemyVortexSharedDeathConfirmsOneOriginalEconomicCredit(GameTestHelper h){
        F f=new F(h,"vortexcredit",false);var l=f.left();var r=f.right();var caster=f.add(DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel()),f.enemy,-8);
        var enemyPeer=new PassiveIncomePeer(h.getLevel(),f.enemy);f.extraPeers.add(enemyPeer);var enemyAccount=ExtendedResourcesSync.account(f.enemy);enemyAccount.extra.put(DreamPassiveEconomy.SHADOW,100);
        ResearchServerEvents.addResearch(f.enemy,com.dreamseekers.unitprogression.ExpansionResearch.AARONCOS_VORTEX_ID);
        var probe=new ActiveSkillEconomicGameTests.ActiveSkillEconomicSubscriber(h);probe.watch(l,r);f.listeners.add(probe);
        boolean[] reentered={false};Object healOnPartnerDeath=new Object(){@net.minecraftforge.eventbus.api.SubscribeEvent public void death(net.minecraftforge.event.entity.living.LivingDeathEvent event){if(event.getEntity()==r){reentered[0]=true;l.heal(3);}}};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(healOnPartnerDeath);f.listeners.add(healOnPartnerDeath);
        h.startSequence().thenIdle(30).thenExecute(()->{f.paired(l,r);r.setHealth(.5f);l.setHealth(100);l.setAbsorptionAmount(0);l.getAttribute(Attributes.ARMOR).setBaseValue(0);l.getAttribute(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get()).setBaseValue(0);
            var request=new com.dreamseekers.skill.SkillGroundRequest(com.dreamseekers.skill.UnitSkill.AARONCOS_RIGHT_VORTEX,caster.getId(),caster.getUUID(),caster.rts().skills().revision(),f.center.offset(0,0,-16));
            h.assertTrue(com.dreamseekers.skill.SkillService.requestGround(enemyPeer.actor,request)==com.dreamseekers.skill.SkillResult.SUCCESS&&enemyAccount.extra.get(DreamPassiveEconomy.SHADOW)==60,"actual authenticated enemy vortex paid40, no ordinary-mob fallback");
        }).thenIdle(23).thenExecute(()->{
            h.assertTrue(!r.isAlive()&&l.isAlive()&&probe.count(r)==1&&probe.count(l)==0,"first actual vortex pulse shared death confirms only partner once on later tick");
            h.assertTrue(reentered[0]&&com.dreamseekers.skill.AaroncosVortex.negative(l)!=null,"real partner death callback healing cannot erase original positive exact HP-write receipt/slow");f.hp(l,101.5f,"original vortex2 minus partner remaining0.5 then separate callback healing3");
            h.assertTrue(com.solegendary.reignofnether.resources.kill.RtsKillValueClaims.isClaimed(r)&&enemyAccount.ore==1,"original exact source claim pays actual enemy account once");
            h.assertTrue(probe.fatal.get(r.getUUID()).getEntity()==caster&&com.dreamseekers.skill.AaroncosVortex.isPulse(probe.fatal.get(r.getUUID())),"fatal sharing retains original dedicated vortex DamageSource identity");
            h.assertTrue(f.bond(l)!=null&&f.bond(l).missing==2,"shared economic kill is also authentic pending combat replacement");
        }).thenIdle(120).thenExecute(()->{h.assertTrue(probe.count(r)==1&&probe.count(l)==0&&enemyAccount.ore==1&&f.shadow()==960,"remaining actual pulses cannot duplicate corpse value or refund pair");f.done();});
    }

}
