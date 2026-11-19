package com.solegendary.reignofnether.research.economy;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;
import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.*;
import java.util.Set;

/** Optional post-fix controls. Test-only; same real legacy codec/authenticated handler as baseline. */
@GameTestHolder("dreamseekers_audit_fixes")
@PrefixGameTestTemplate(false)
public final class CombatEconomyPostfixControls {
 private static Resources account(String name,int amount){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,Factions.MONSTERS));var r=new Resources(name,amount,amount,amount,amount);ResourcesServerEvents.resourcesList.add(r);return r;}
 private static void clear(String... names){var all=Set.of(names);PlayerServerEvents.rtsPlayers.removeIf(p->all.contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->all.contains(r.ownerName));}
 private static void send(ResearchEconomyFixtures.Peer peer,ResourcesServerboundPacket packet){var b=new FriendlyByteBuf(Unpooled.buffer());try{packet.encode(b);new ResourcesServerboundPacket(b).handle(()->peer.context);}finally{b.release();}peer.channel.runPendingTasks();}
 private static boolean amounts(Resources r,int f,int w,int o,int e){return r.food==f&&r.wood==w&&r.ore==o&&r.emerald==e;}
 @GameTest(templateNamespace="dreamseekers_audit_fixes",template="core_economy_arena",batch="reaudit_transfer_controls",timeoutTicks=40)
 public static void genuinePositiveTransferAndForgedSender(GameTestHelper h)throws Exception{
  String a="AuditGoodA",b="AuditGoodB";var from=account(a,100);var to=account(b,0);
  try(var peer=new ResearchEconomyFixtures.Peer(h.getLevel(),a)){
   send(peer,new ResourcesServerboundPacket(ResourcesAction.SEND_RESOURCES,a,b,1,2,3,4));
   h.assertTrue(amounts(from,99,98,97,96)&&amounts(to,1,2,3,4),"normal four-resource request remains functional");
   send(peer,new ResourcesServerboundPacket(ResourcesAction.SEND_RESOURCES,a,b,0,0,0,5));
   h.assertTrue(from.emerald==91&&to.emerald==9,"standalone positive emerald remains functional");
   send(peer,new ResourcesServerboundPacket(ResourcesAction.SEND_RESOURCES,b,a,1,1,1,1));
   h.assertTrue(amounts(from,99,98,97,91)&&amounts(to,1,2,3,9),"authenticated sender cannot impersonate another ledger owner");
   send(peer,new ResourcesServerboundPacket(ResourcesAction.SEND_RESOURCES,a,b,1000,0,0,0));
   h.assertTrue(from.food==0&&to.food==100,"legacy send-any clips to actual sender balance, not negative/overflow");
  }finally{clear(a,b);}h.succeed();
 }
 @GameTest(templateNamespace="dreamseekers_audit_fixes",template="core_economy_arena",batch="reaudit_transfer_controls",timeoutTicks=40)
 public static void anyNegativeFieldRejectsWholeRequest(GameTestHelper h)throws Exception{
  String a="AuditBadA",b="AuditBadB";var from=account(a,100);var to=account(b,0);
  try(var peer=new ResearchEconomyFixtures.Peer(h.getLevel(),a)){
   for(int field=0;field<4;field++){int[] v={1,1,1,1};v[field]=-1;send(peer,new ResourcesServerboundPacket(ResourcesAction.SEND_RESOURCES,a,b,v[0],v[1],v[2],v[3]));h.assertTrue(amounts(from,100,100,100,100)&&amounts(to,0,0,0,0),"negative component rejects before ANY resource mutation: "+field);}
   for(ResourceName type:ResourceName.values())for(int value:new int[]{0,-1,Integer.MIN_VALUE})h.assertTrue(ResourcesServerEvents.trySendingResources(a,b,type,value)==0&&amounts(from,100,100,100,100)&&amounts(to,0,0,0,0),"direct public helper rejects non-positive amount: "+type+"/"+value);
  }finally{clear(a,b);}h.succeed();
 }
 @GameTest(templateNamespace="dreamseekers_audit_fixes",template="core_economy_arena",batch="reaudit_transfer_controls",timeoutTicks=40)
 public static void fullReceiverNoDebitAndDuplicateAccountRefused(GameTestHelper h){
  String a="AuditCapA",b="AuditCapB";var from=account(a,100);var to=account(b,Integer.MAX_VALUE);
  try{
   for(ResourceName type:ResourceName.values())h.assertTrue(ResourcesServerEvents.trySendingResources(a,b,type,1)==0&&amounts(from,100,100,100,100)&&amounts(to,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE),"full recipient cannot wrap or charge sender: "+type);
   to.food=0;Resources duplicate=new Resources(b,0,0,0);ResourcesServerEvents.resourcesList.add(duplicate);
   h.assertTrue(ResourcesServerEvents.trySendingResources(a,b,ResourceName.FOOD,1)==0&&from.food==100&&to.food==0&&duplicate.food==0,"ambiguous recipient ledger identity must not multiply credits");
  }finally{clear(a,b);}h.succeed();
 }
 private static Mob body(GameTestHelper h,boolean bee){return bee?EntityRegistrar.BEE_UNIT.get().create(h.getLevel()):EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());}
 private static void flag(Mob m,boolean value){if(m instanceof BeeUnit b)b.setIsSummoned(value);else ((ZombieUnit)m).setIsSummoned(value);}
 private static boolean flag(Mob m){return m instanceof BeeUnit b?b.isSummoned():((ZombieUnit)m).isSummoned();}
 @GameTest(templateNamespace="dreamseekers_audit_fixes",template="core_economy_arena",batch="reaudit_summon_controls",timeoutTicks=40)
 public static void explicitAndLegacySummonedNbtPolicy(GameTestHelper h){
  for(boolean bee:new boolean[]{false,true})for(int variant=0;variant<6;variant++){
   Mob original=body(h,bee);((Unit)original).setOwnerName("AuditLegacy");flag(original,variant==0||variant==2);
   if(variant==2||variant==5)original.addEffect(new MobEffectInstance(MobEffectRegistrar.LIMITED_LIFESPAN.get(),100,0,true,false));
   CompoundTag nbt=new CompoundTag();original.saveWithoutId(nbt);
   // v0 explicit true; v1 explicit false; v2 old missing + native marker; v3 old missing without evidence;
   // v4 malformed typed marker; v5 explicit false is authoritative even if an unrelated effect is present.
   if(variant==2||variant==3)nbt.remove("reignofnetherSummoned");
   if(variant==4)nbt.putString("reignofnetherSummoned","invalid");
   Mob loaded=body(h,bee);loaded.load(nbt);boolean expected=variant==0||variant==2||variant==4;
   h.assertTrue(flag(loaded)==expected,"fresh NBT policy "+(bee?"bee":"zombie")+" variant="+variant+" expected="+expected);
   if(!bee)h.assertTrue((((Unit)loaded).getCost().food==0)==expected,"summon-vs-normal Zombie configured cost follows supported flag");
  }
  h.succeed();
 }
}
