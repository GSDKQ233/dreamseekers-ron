// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.BuildingAbilitySource;
import com.dreamseekers.buildingability.BuildingCodex;
import com.dreamseekers.ecologyworkposts.EcologyPackets;
import com.dreamseekers.ecologyworkposts.EcologyWorkPosts;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.economy.DreamShadowKillIncome;
import com.dreamseekers.neutralcontract.CommittedContractUnits;
import com.dreamseekers.neutralcontract.ContractLedger;
import com.dreamseekers.neutralcontract.NeutralContracts;
import com.dreamseekers.unit.BasaltSnailUnit;
import com.dreamseekers.unit.BeetleUnit;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.ResearchPlacementIdentity;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.kill.ConfirmedRtsKillEvent;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Real W conversion -> native walk/hold -> research-gated care -> native enemy death.
 * Positive contracts are never manufactured by copying tags or directly committing the ledger.
 */
@GameTestHolder("dreamseekers_building_abilities") @PrefixGameTestTemplate(false)
public final class BuildingContractCareGameTests {
    private static final String BATCH="building_contract_care";
    private static final List<NeutralContractGameTests.F> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();}
    private static NeutralContractGameTests.F fixture(GameTestHelper h,String suffix){var f=new NeutralContractGameTests.F(h,"API53_Care_"+suffix);FIXTURES.add(f);return f;}
    private static String assign(NeutralContractGameTests.F f,Mob body){return EcologyWorkPosts.assign(f.peer.actor,f.court,
        ResearchPlacementIdentity.serverIdentity(f.court),f.court.getCaptureRevision(),List.of(new EcologyPackets.Target(body.getId(),body.getUUID())));}
    private static EcologyWorkPosts.Occupant entry(NeutralContractGameTests.F f,Mob body){return EcologyWorkPosts.snapshot(f.court).stream()
        .filter(row->row.uuid().equals(body.getUUID())).findFirst().orElseThrow();}
    private static void fatal(GameTestHelper h,Mob victim,Mob killer){
        victim.setNoAi(true);victim.invulnerableTime=0;victim.setHealth(1);
        h.assertTrue(victim.hurt(victim.damageSources().mobAttack(killer),100000)&&victim.isDeadOrDying()
            &&victim.getLastDamageSource()!=null&&victim.getLastDamageSource().getEntity()==killer,"real hostile native hurt kills exact body");
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1500)
    public static void realPaidBeetleWalksReceivesResearchedCareAndRemainsEconomicallyFree(GameTestHelper h){
        var f=fixture(h,"beetle");var worker=f.worker(3,-10,false);var nativeBody=f.nativeBody(false,5,-10);nativeBody.setHealth(nativeBody.getMaxHealth()/2);
        Mob[] body={null},ordinary={null},killer={null};Tag[] free={null};float[] injured={0};long[] arrived={0},enemyRevision={0};
        String enemy=f.owner+"_enemy";PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(enemy,DreamseekersMod.DREAMSEEKERS));
        var bank=new Resources(enemy,1000,1000,1000);bank.extra.put(DreamPassiveEconomy.SHADOW,0);ResourcesServerEvents.resourcesList.add(bank);
        List<ConfirmedRtsKillEvent> deaths=new ArrayList<>();Consumer<ConfirmedRtsKillEvent> observer=e->{if(e.snapshot().victimOwner().equals(f.owner))deaths.add(e);};
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,observer);
        f.cleanupActions.add(()->{MinecraftForge.EVENT_BUS.unregister(observer);if(killer[0]!=null)killer[0].discard();ResearchServerEvents.removeAllResearchFor(enemy);
            ResourcesServerEvents.resourcesList.remove(bank);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(enemy));});
        h.startSequence().thenWaitUntil(()->f.ready(worker,nativeBody))
          .thenExecute(()->h.assertTrue(f.start(worker,nativeBody).equals("approaching"),"actual W request starts natural160tick contract"))
          .thenWaitUntil(()->h.assertTrue(f.converted().size()==1,"one real paid contract commits"))
          .thenExecute(()->{
              body[0]=f.converted().get(0);injured[0]=body[0].getHealth();free[0]=body[0].getPersistentData().get(RtsKillValueClaims.FREE_ORIGIN_KEY).copy();
              h.assertTrue(body[0] instanceof BeetleUnit&&nativeBody.isRemoved()&&CommittedContractUnits.isCommittedPermanent(body[0],f.owner),"real original native was converted and proof matches exact live ledger");
              h.assertTrue(f.account.food==950&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==485,"actual50F15S contract cost, no synthetic free source");
              h.assertTrue(EcologyWorkPosts.eligible(body[0],f.court)&&assign(f,body[0]).equals("assigned"),"committed permanent economic-free beetle can reserve original care post");
              h.assertTrue(!entry(f,body[0]).arrived(),"distant original converted body does not teleport to its post");
          }).thenWaitUntil(()->h.assertTrue(entry(f,body[0]).arrived(),"actual native movement reaches legal outdoor post"))
          .thenExecute(()->{arrived[0]=f.level.getGameTime();h.assertTrue(f.level.noCollision(body[0]),"real body fits reserved position");})
          .thenExecuteAfter(240,()->{
              h.assertTrue(body[0].getHealth()==injured[0],"existing research requirement retained throughout real rest interval");
              var page=BuildingCodex.query(f.peer.actor,BuildingAbilitySource.capture(f.peer.actor,f.court),0);
              h.assertTrue(page.rows().stream().anyMatch(row->row.uuid().equals(body[0].getUUID())&&row.careState()==2&&row.careEligible()),"live codex lists same committed body and actual arrived care status");
              ResearchServerEvents.addResearch(f.owner,DreamSpecialistResearch.CONTRACT_CARE_ID);
          }).thenWaitUntil(()->h.assertTrue(body[0].getHealth()>injured[0],"existing research consumer performs actual heal pulse"))
          .thenExecute(()->{
              h.assertTrue(body[0].getHealth()==injured[0]+1&&f.level.getGameTime()-arrived[0]>=200,"actual one HP budget and full rest gate");
              h.assertTrue(free[0].equals(body[0].getPersistentData().get(RtsKillValueClaims.FREE_ORIGIN_KEY))&&RtsKillValueClaims.isFree(body[0]),"care never removes or changes economic-free lineage");
              var attack=DreamseekersMod.BEETLE.get().create(f.level);attack.setOwnerName(enemy);attack.setNoAi(true);attack.moveTo(f.origin.getX()-18+.5,f.origin.getY()+1,f.origin.getZ()-18+.5);h.assertTrue(f.level.addFreshEntity(attack),"exact hostile killer published");killer[0]=attack;
              var normal=DreamseekersMod.BEETLE.get().create(f.level);normal.setOwnerName(f.owner);normal.setNoAi(true);normal.moveTo(f.origin.getX()-14+.5,f.origin.getY()+1,f.origin.getZ()-18+.5);h.assertTrue(f.level.addFreshEntity(normal),"same registered nonfree cost comparator published");ordinary[0]=normal;f.bodies.add(normal);
          }).thenExecuteAfter(5,()->{enemyRevision[0]=bank.revision;fatal(h,body[0],killer[0]);})
          .thenExecuteAfter(5,()->{
              h.assertTrue(bank.revision==enemyRevision[0]&&bank.extra.get(DreamPassiveEconomy.SHADOW)==0&&bank.conversionRemainders.isEmpty(),"real cared-for contract death yields zero integer/fractional income");
              h.assertTrue(deaths.isEmpty()&&!RtsKillValueClaims.isClaimed(body[0])&&free[0].equals(body[0].getPersistentData().get(RtsKillValueClaims.FREE_ORIGIN_KEY)),"free death emits no redeemable confirmed event and preserves free provenance");
              h.assertTrue(ContractLedger.get(f.level.getServer()).count(f.owner,f.account.accountEpoch)==0&&EcologyWorkPosts.snapshot(f.court).isEmpty(),"actual death releases original contract slot and care lease");
              h.assertTrue(!RtsKillValueClaims.isFree(ordinary[0]),"same-type ordinary positive control genuinely not free");fatal(h,ordinary[0],killer[0]);
          }).thenExecuteAfter(5,()->{
              h.assertTrue(deaths.size()==1&&deaths.get(0).snapshot().victimId().equals(ordinary[0].getUUID())&&RtsKillValueClaims.isClaimed(ordinary[0]),"same native enemy damage path publishes exactly ordinary comparator economic death");
              h.assertTrue(bank.extra.get(DreamPassiveEconomy.SHADOW)==4&&bank.conversionRemainders.getOrDefault(DreamShadowKillIncome.REMAINDER,0L)==7500,"live5% income positive control yields4.75 for ordinary95 cost");
              h.assertTrue(f.account.food==950&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==485,"care/death never refund original paid contract");f.close();h.succeed();
          });
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1000)
    public static void realSnailProofRejectsPendingForgedFreeOtherOriginAndReplacedAccount(GameTestHelper h){
        var f=fixture(h,"snailproof");var worker=f.worker(3,-10,false);var nativeBody=f.nativeBody(true,5,-10);Mob[] body={null};
        var forged=DreamseekersMod.BASALT_SNAIL.get().create(f.level);forged.setOwnerName(f.owner);forged.setNoAi(true);f.place(forged,12,-12);
        // Observe real damage/contract wire only; the comparator uses F's natural landing then NoAI freeze, never damage interception.
        long[] diagnosticAt={Long.MIN_VALUE};
        Consumer<net.minecraftforge.event.entity.living.LivingDamageEvent> diagnostic=event->{
            if(event.getEntity()==nativeBody||event.getEntity()==worker)
                System.out.println("BUILDING_CARE_DAMAGE time="+f.level.getGameTime()+" target="+event.getEntity().getUUID()+" amount="+event.getAmount()+" attacker="+event.getSource().getEntity()+" forgedComparator="+(event.getSource().getEntity()==forged));
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,diagnostic);f.cleanupActions.add(()->MinecraftForge.EVENT_BUS.unregister(diagnostic));
        h.startSequence().thenWaitUntil(()->{f.ready(worker,nativeBody,forged);h.assertTrue(forged.isNoAi(),"unrelated provenance comparator naturally landed then froze before real W channel");})
          .thenExecute(()->h.assertTrue(f.start(worker,nativeBody).equals("approaching"),"actual snail W request"))
          .thenWaitUntil(()->{
              for(var outgoing:f.peer.drain())if(outgoing instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket packet
                    &&packet.getIdentifier().equals(DreamseekersMod.id("neutral_contract"))){
                  var bytes=new net.minecraft.network.FriendlyByteBuf(packet.getData().copy());
                  try{if(bytes.readVarInt()==1){var view=new com.dreamseekers.neutralcontract.ContractPackets.Snapshot(bytes);
                      System.out.println("BUILDING_CARE_CONTRACT time="+f.level.getGameTime()+" status="+view.status()+" remaining="+view.remaining()+" active="+view.active());}}
                  finally{bytes.release();}
              }
              long now=f.level.getGameTime();if(diagnosticAt[0]==Long.MIN_VALUE||now-diagnosticAt[0]>=20){diagnosticAt[0]=now;
                  System.out.println("BUILDING_CARE_WAIT time="+now+" converted="+f.converted().size()+" active="+NeutralContracts.active(worker)+" remaining="+NeutralContracts.remaining(worker)
                      +" nativeHp="+nativeBody.getHealth()+" nativeAlive="+nativeBody.isAlive()+" nativePos="+nativeBody.position()+" workerPos="+worker.position()
                      +" comparatorNoAi="+forged.isNoAi()+" comparatorPos="+forged.position()+" comparatorTarget="+forged.getTargetGoal().getTarget());}
              h.assertTrue(f.converted().size()==1,"natural full channel commits one snail");
          })
          .thenExecute(()->{
              body[0]=f.converted().get(0);var real=body[0];
              h.assertTrue(real instanceof BasaltSnailUnit&&CommittedContractUnits.isCommittedPermanent(real,f.owner),"strict helper accepts genuine published snail");
              var originalFree=real.getPersistentData().get(RtsKillValueClaims.FREE_ORIGIN_KEY).copy();var originalProvenance=real.getPersistentData().get(NeutralContracts.ORIGIN).copy();
              // v1.2: any presence of either lineage key requires both strict committed proofs.
              h.assertTrue(!forged.getPersistentData().contains(NeutralContracts.ORIGIN)&&!forged.getPersistentData().contains(RtsKillValueClaims.FREE_ORIGIN_KEY)
                  &&EcologyWorkPosts.eligible(forged,f.court),"double-absent ordinary published snail remains eligible");
              forged.getPersistentData().put(NeutralContracts.ORIGIN,originalProvenance.copy());
              h.assertTrue(!CommittedContractUnits.isCommittedPermanent(forged,f.owner)&&!EcologyWorkPosts.eligible(forged,f.court)&&assign(f,forged).equals("no_post"),"origin-only copied provenance cannot bypass strict proof when FREE key is absent");
              forged.getPersistentData().remove(NeutralContracts.ORIGIN);forged.getPersistentData().putInt(RtsKillValueClaims.FREE_ORIGIN_KEY,1);
              h.assertTrue(!EcologyWorkPosts.eligible(forged,f.court)&&assign(f,forged).equals("no_post"),"malformed noncompound FREE key is not an ordinary-body bypass");
              forged.getPersistentData().remove(RtsKillValueClaims.FREE_ORIGIN_KEY);forged.getPersistentData().putString(NeutralContracts.ORIGIN,"malformed");
              h.assertTrue(!EcologyWorkPosts.eligible(forged,f.court)&&assign(f,forged).equals("no_post"),"malformed noncompound ORIGIN key is also strict failclosed");forged.getPersistentData().remove(NeutralContracts.ORIGIN);
              h.assertTrue(EcologyWorkPosts.eligible(forged,f.court),"restoring both absent keys restores ordinary eligibility, not contract proof");
              real.getPersistentData().remove(RtsKillValueClaims.FREE_ORIGIN_KEY);
              h.assertTrue(!CommittedContractUnits.isCommittedPermanent(real,f.owner)&&!EcologyWorkPosts.eligible(real,f.court)&&assign(f,real).equals("no_post"),"genuine committed body with removed economic-free proof cannot masquerade as ordinary");
              real.getPersistentData().put(RtsKillValueClaims.FREE_ORIGIN_KEY,originalFree.copy());
              h.assertTrue(CommittedContractUnits.isCommittedPermanent(real,f.owner)&&EcologyWorkPosts.eligible(real,f.court)&&RtsKillValueClaims.isFree(real)
                  &&originalFree.equals(real.getPersistentData().get(RtsKillValueClaims.FREE_ORIGIN_KEY)),"restored genuine original free proof regains care eligibility without economic marker rewrite");
              real.getPersistentData().putBoolean(NeutralContracts.PENDING,true);
              h.assertTrue(!CommittedContractUnits.isCommittedPermanent(real,f.owner)&&assign(f,real).equals("no_post"),"pending body never gets care despite original lineage");real.getPersistentData().remove(NeutralContracts.PENDING);
              var otherFree=new CompoundTag();otherFree.putInt("schema",1);otherFree.putString("origin","NATIVE_SUMMON");real.getPersistentData().put(RtsKillValueClaims.FREE_ORIGIN_KEY,otherFree);
              h.assertTrue(!CommittedContractUnits.isCommittedPermanent(real,f.owner)&&assign(f,real).equals("no_post"),"other free origin does not gain exception");real.getPersistentData().put(RtsKillValueClaims.FREE_ORIGIN_KEY,originalFree.copy());
              var future=((CompoundTag)originalFree).copy();future.putInt("schema",999);real.getPersistentData().put(RtsKillValueClaims.FREE_ORIGIN_KEY,future);
              h.assertTrue(!CommittedContractUnits.isCommittedPermanent(real,f.owner)&&assign(f,real).equals("no_post"),"unknown free metadata remains inert");real.getPersistentData().put(RtsKillValueClaims.FREE_ORIGIN_KEY,originalFree.copy());
              var changed=((CompoundTag)originalProvenance).copy();changed.putUUID("account",UUID.randomUUID());real.getPersistentData().put(NeutralContracts.ORIGIN,changed);
              h.assertTrue(!CommittedContractUnits.isCommittedPermanent(real,f.owner)&&assign(f,real).equals("no_post"),"mismatched provenance epoch cannot borrow ledger");real.getPersistentData().put(NeutralContracts.ORIGIN,originalProvenance.copy());
              ResourcesServerEvents.resourcesList.remove(f.account);var replaced=new Resources(f.owner,1000,1000,1000);ResourcesServerEvents.resourcesList.add(replaced);
              try{h.assertTrue(!CommittedContractUnits.isCommittedPermanent(real,f.owner)&&assign(f,real).equals("no_post"),"new current account epoch cannot inherit old contract care authority");}
              finally{ResourcesServerEvents.resourcesList.remove(replaced);ResourcesServerEvents.resourcesList.add(f.account);}
              forged.getPersistentData().put(RtsKillValueClaims.FREE_ORIGIN_KEY,originalFree.copy());forged.getPersistentData().put(NeutralContracts.ORIGIN,originalProvenance.copy());
              h.assertTrue(!CommittedContractUnits.isCommittedPermanent(forged,f.owner)&&assign(f,forged).equals("no_post"),"copying exact valid tags onto a different body does not create original ledger commit");
              h.assertTrue(CommittedContractUnits.isCommittedPermanent(real,f.owner)&&assign(f,real).equals("assigned"),"genuine original source remains usable with original account");
              h.assertTrue(f.account.food==940&&f.account.ore==980&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==480,"negative care probes never charge or refund actual60F20O20S contract");
          }).thenWaitUntil(()->h.assertTrue(entry(f,body[0]).arrived(),"genuine large snail naturally fits and reaches legal post"))
          .thenExecute(()->{h.assertTrue(RtsKillValueClaims.isFree(body[0]),"snail free economic source retained at arrival");f.close();h.succeed();});
    }
}
