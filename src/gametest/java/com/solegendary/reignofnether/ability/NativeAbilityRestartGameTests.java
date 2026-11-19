package com.solegendary.reignofnether.ability;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.NecromancerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** True normal save/reload. The observer only records native loaded values, never writes entity state. */
@GameTestHolder("api63_ability_restart") @PrefixGameTestTemplate(false)
public final class NativeAbilityRestartGameTests {
    static final File FILE=new File("api63-ability-restart-expectations.nbt");
    static final String PHASE="api63.ability.restart.phase";
    static Mob ordinary,hero;
    static final Map<UUID,CompoundTag> JOINED=new HashMap<>();
    static CompoundTag record(Mob body){
        Unit unit=(Unit)body;Ability ability=body instanceof NecromancerUnit necro?NativeAbilitySaveGameTests.curse(necro):NativeAbilitySaveGameTests.first(unit);
        var tag=new CompoundTag();tag.putUUID("uuid",body.getUUID());tag.putFloat("cooldown",unit.getCooldown(ability));tag.putInt("charges",unit.getAbilityCharges(ability));tag.putBoolean("autocast",unit.hasAutocast(ability));
        if(body instanceof NecromancerUnit necro)tag.putInt("rank",necro.getHeroAbilityRank((HeroAbility)ability));return tag;
    }
    @Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID)
    public static final class NativeLoadObserver {
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public static void joined(EntityJoinLevelEvent event)throws Exception{
            if(!"verify".equals(System.getProperty(PHASE))||event.getLevel().isClientSide()||!FILE.exists()||!(event.getEntity() instanceof Mob body)||!(body instanceof Unit))return;
            var expected=NbtIo.readCompressed(FILE);
            for(String key:List.of("ordinary","hero"))if(body.getUUID().equals(expected.getCompound(key).getUUID("uuid")))JOINED.put(body.getUUID(),record(body));
        }
    }
    public static final class StopWitness {
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public void stopped(ServerStoppingEvent event)throws Exception{
            var expected=new CompoundTag();expected.putLong("jvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());expected.put("ordinary",record(ordinary));expected.put("hero",record(hero));
            NbtIo.writeCompressed(expected,FILE);System.out.println("API63_NATIVE_ABILITIES_NORMAL_STOP_WITNESS "+expected);
        }
    }
    @GameTest(templateNamespace="api63_ability_restart",template="ore_mine_test_arena",batch="ability_restart",timeoutTicks=600)
    public static void nativeAbilityStateCrossesTwoNormalJvms(GameTestHelper h)throws Exception{
        var level=h.getLevel();
        switch(System.getProperty(PHASE,"")){
            case "prepare" -> {
                h.assertTrue(!FILE.exists(),"fresh isolated manifest");BlockPos pos=new BlockPos(4096,100,4096);
                for(int x=-2;x<=3;x++)for(int z=-2;z<=3;z++)level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                ordinary=EntityRegistrar.WRAITH_UNIT.get().create(level);hero=EntityRegistrar.NECROMANCER_UNIT.get().create(level);
                for(Mob body:List.of(ordinary,hero)){body.setNoAi(true);body.setPersistenceRequired();body.setInvulnerable(true);((Unit)body).setOwnerName("Api63NativeRestart");body.moveTo(pos.getX()+.5+(body==hero?2:0),pos.getY(),pos.getZ()+.5);h.assertTrue(level.addFreshEntity(body),"native persistent body published");}
                var ordinaryAbility=NativeAbilitySaveGameTests.first((Unit)ordinary);((Unit)ordinary).setCooldown(ordinaryAbility,300.5f);((Unit)ordinary).setAutocast(null);
                var necro=(NecromancerUnit)hero;var curse=NativeAbilitySaveGameTests.curse(necro);necro.setHeroAbilityRank(curse,3);curse.updateStatsForRank(necro);necro.setCooldown(curse,250.5f);necro.setCharges(curse,1);necro.setAutocast(curse);
                MinecraftForge.EVENT_BUS.register(new StopWitness());h.runAfterDelay(5,()->{h.assertTrue(((Unit)ordinary).getCooldown(ordinaryAbility)>0&&necro.getAbilityCharges(curse)==1,"real cooldown remains spent before native shutdown");h.succeed();});
            }
            case "verify" -> {
                var expected=NbtIo.readCompressed(FILE);h.assertTrue(expected.getLong("jvm")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different actual JVM");
                h.startSequence().thenWaitUntil(()->{
                    for(String key:List.of("ordinary","hero")){UUID id=expected.getCompound(key).getUUID("uuid");h.assertTrue(level.getEntity(id)!=null&&level.getEntity(id).isAlive()&&JOINED.containsKey(id),"native forced chunk load published original UUID and observed initial state");}
                }).thenExecute(()->{
                    for(String key:List.of("ordinary","hero")){
                        var before=expected.getCompound(key);var observed=JOINED.get(before.getUUID("uuid"));
                        h.assertTrue(observed.equals(before),"first native join exactly retains remaining cooldown/charges/autocast/rank, no offline refill: "+key+" observed="+observed+" before="+before);
                        var body=(Mob)level.getEntity(before.getUUID("uuid"));var unit=(Unit)body;var ability=body instanceof NecromancerUnit necro?NativeAbilitySaveGameTests.curse(necro):NativeAbilitySaveGameTests.first(unit);
                        h.assertTrue(unit.getCooldown(ability)>0&&unit.getCooldown(ability)<=before.getFloat("cooldown"),"live ticks advance remaining cooldown instead of clearing it");
                        h.assertTrue(unit.getAbilityCharges(ability)==before.getInt("charges")&&unit.hasAutocast(ability)==before.getBoolean("autocast"),"no free charges or changed preference after restart");body.discard();
                    }
                    System.out.println("API63_NATIVE_ABILITIES_TWO_JVM_PASS");
                }).thenSucceed();
            }
            default -> throw new IllegalArgumentException("prepare|verify required");
        }
    }
}
