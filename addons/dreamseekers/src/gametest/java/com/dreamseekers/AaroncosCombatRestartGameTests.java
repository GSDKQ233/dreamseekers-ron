package com.dreamseekers;
import com.dreamseekers.aaroncos.ControlledAaroncosHand;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.skill.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;
/** Two actual JVMs; external read-only saved-Time gate MUST show saved Time < releaseLeft.
 * Never synthesizes NBT bodies or modifies clocks, cooldowns or health on verify.
 */
@GameTestHolder("dreamseekers_aaroncos_skills_restart") @PrefixGameTestTemplate(false)
public final class AaroncosCombatRestartGameTests {
 private static final String OWNER="HandRestart",ENEMY="HandRestartEnemy";
 private static final BlockPos ORIGIN=new BlockPos(155648,100,155648);
 private static final File MANIFEST=new File("aaroncos-skills-restart.nbt");
 private static PassiveIncomePeer ownerPeer;
 private static final List<String> KEYS=List.of("left","right","leftTarget","rightTarget");
 @GameTest(templateNamespace="dreamseekers_aaroncos_skills_restart",template="ore_mine_test_arena",timeoutTicks=180)
 public static void originalBothPaidCooldownsPersistAndNeitherPendingChargeReplays(GameTestHelper h)throws Exception {
  switch(System.getProperty("dreamseekers.aaroncos.phase","")){
   case "prepare"->prepare(h);case "verify"->verify(h);default->throw new IllegalArgumentException("prepare/verify required");
  }
 }
 private static void chunks(GameTestHelper h,boolean prepare){
  for(int x=(ORIGIN.getX()>>4)-2;x<=((ORIGIN.getX()+23)>>4)+2;x++)for(int z=(ORIGIN.getZ()>>4)-2;z<=(ORIGIN.getZ()>>4)+2;z++){
   if(prepare)h.getLevel().setChunkForced(x,z,true);
   else h.assertTrue(h.getLevel().getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(x,z)),"original native saved chunk ticket");
   h.getLevel().getChunk(x,z);
  }
 }
 private static <T extends Mob>T owned(GameTestHelper h,T e,String owner,int x){
  ((Unit)e).setOwnerName(owner);e.setNoAi(true);e.setPersistenceRequired();e.moveTo(ORIGIN.getX()+x+.5,100,ORIGIN.getZ()+.5);((Unit)e).setAnchor(e.blockPosition().below());
  e.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);e.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);
  h.assertTrue(h.getLevel().addFreshEntity(e),"original entity published");return e;
 }
 private static SkillResult cast(ControlledAaroncosHand s,Mob target){return SkillService.request(ownerPeer.actor,new SkillRequest(SkillService.skillFor(s),s.mob().getId(),s.mob().getUUID(),s.rts().skills().revision(),target.getId(),target.getUUID()));}
 private static void prepare(GameTestHelper h){
  h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"fresh world and manifest");chunks(h,true);
  for(BlockPos p:BlockPos.betweenClosed(ORIGIN.offset(-8,-1,-8),ORIGIN.offset(32,7,8)))h.getLevel().setBlock(p,p.getY()==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  for(String name:List.of(OWNER,ENEMY)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,1000,1000,1000));}
  var account=ExtendedResourcesSync.account(OWNER);account.extra.put(DreamPassiveEconomy.SHADOW,500);ownerPeer=new PassiveIncomePeer(h.getLevel(),OWNER);
  var left=owned(h,DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(h.getLevel()),OWNER,0);
  var right=owned(h,DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel()),OWNER,20);
  var lt=owned(h,DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel()),ENEMY,5);
  var rt=owned(h,DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel()),ENEMY,23);
  Map<String,Mob> bodies=Map.of("left",left,"right",right,"leftTarget",lt,"rightTarget",rt);
  for(var t:List.of(lt,rt)){t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);t.setHealth(1000);}
  h.startSequence().thenIdle(30).thenWaitUntil(()->{
   for(var body:bodies.values())h.assertTrue(body.tickCount>0&&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body,"original hand/target ready before fixed skill windup and CD starts");
  }).thenExecute(()->{
   long now=h.getLevel().getGameTime();h.assertTrue(cast(left,lt)==SkillResult.SUCCESS&&cast(right,right)==SkillResult.SUCCESS,"both authenticated paid starts");
   h.assertTrue(AaroncosCombat.remainingTicks(left)==12&&AaroncosCombat.remainingTicks(right)==20&&account.extra.get(DreamPassiveEconomy.SHADOW)==450,"original two pending charges and debit50");
   var tag=new CompoundTag();tag.putInt("format",1);tag.putLong("jvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());tag.putUUID("epoch",account.accountEpoch);
   tag.putLong("now",now);tag.putLong("releaseLeft",now+12);tag.putLong("releaseRight",now+20);tag.putLong("deadlineLeft",now+400);tag.putLong("deadlineRight",now+600);tag.putInt("shadow",450);
   bodies.forEach((key,e)->{tag.putUUID(key,e.getUUID());tag.putFloat(key+"HP",e.getHealth());});
   try{NbtIo.writeCompressed(tag,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
   System.out.println("AARONCOS_SKILLS_PREPARED now="+now+" earliestRelease="+(now+12));h.succeed();
   // Retain real owner connection and charges; normal server stop performs actual disk save.
  });
 }
 private static void verify(GameTestHelper h)throws Exception{
  h.assertTrue(MANIFEST.isFile(),"original manifest exists");var tag=NbtIo.readCompressed(MANIFEST);
  h.assertTrue(tag.getInt("format")==1&&tag.getLong("jvm")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"distinct actual JVM");
  h.assertTrue(tag.getLong("releaseLeft")==tag.getLong("now")+12&&tag.getLong("releaseRight")==tag.getLong("now")+20&&tag.getLong("deadlineLeft")==tag.getLong("now")+400&&tag.getLong("deadlineRight")==tag.getLong("now")+600,"unaltered original durations");
  chunks(h,false);ownerPeer=new PassiveIncomePeer(h.getLevel(),OWNER);
  h.startSequence().thenWaitUntil(()->{for(String key:KEYS)h.assertTrue(h.getLevel().getEntity(tag.getUUID(key)) instanceof Mob,"loaded original UUID "+key);})
   .thenExecute(()->{
    long now=h.getLevel().getGameTime();h.assertTrue(now>=tag.getLong("now")&&now<tag.getLong("deadlineLeft"),"original fixed400 cooldown still active on observation");
    var account=ExtendedResourcesSync.account(OWNER);h.assertTrue(account!=null&&account.accountEpoch.equals(tag.getUUID("epoch"))&&account.extra.get(DreamPassiveEconomy.SHADOW)==450,"same original account no refund");
    for(String key:KEYS){var body=(Mob)h.getLevel().getEntity(tag.getUUID(key));h.assertTrue(body.getHealth()==tag.getFloat(key+"HP"),"original HP before containment "+key);body.setNoAi(true);}
    var left=(ControlledAaroncosHand)h.getLevel().getEntity(tag.getUUID("left"));var right=(ControlledAaroncosHand)h.getLevel().getEntity(tag.getUUID("right"));
    h.assertTrue(!AaroncosCombat.active(left)&&!AaroncosCombat.active(right),"no transient charge restored");
    h.assertTrue(left.getAbilities().get().equals(List.of(UnitSkillAbility.AARONCOS_LEFT_PRESS,com.dreamseekers.aaroncos.HandBondAbility.INSTANCE))&&right.getAbilities().get().equals(List.of(UnitSkillAbility.AARONCOS_RIGHT_SHOCK,com.dreamseekers.aaroncos.HandBondAbility.INSTANCE,com.dreamseekers.skill.UnitSkillAbility.AARONCOS_RIGHT_VORTEX)),"loaded original hand UUIDs retain exact own ability without duplicate buttons");
    h.assertTrue(left.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==tag.getLong("deadlineLeft")-now&&right.rts().skills().cooldownTicks(UnitSkill.AARONCOS_RIGHT_SHOCK)==tag.getLong("deadlineRight")-now,"both exact original absolute deadlines");
    h.assertTrue(cast(left,(Mob)h.getLevel().getEntity(tag.getUUID("leftTarget")))==SkillResult.COOLDOWN&&cast(right,right.mob())==SkillResult.COOLDOWN,"both paid skills cannot recast after reload");
   }).thenIdle(21).thenExecute(()->{
    for(String key:KEYS)h.assertTrue(((Mob)h.getLevel().getEntity(tag.getUUID(key))).getHealth()==tag.getFloat(key+"HP"),"no delayed replay "+key);
    ownerPeer.close();ownerPeer=null;h.succeed();
   });
 }
}
