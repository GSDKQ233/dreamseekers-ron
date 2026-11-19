// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.unit.*;
import com.dreamseekers.bonewing.BoneWingBolt;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import java.util.*;
/** Real same-target action dispatch, followed by actual melee/physical ranged damage. */
@GameTestHolder(DreamseekersMod.MOD_ID) @PrefixGameTestTemplate(false)
public final class LightExplicitAttackGameTests {
    // 400 setup/publication ticks + 25 HOLD + unchanged 150 attack samples + 25 margin.
    private static final String BATCH="dreamseekers_light_explicit_attack",PREFIX="LightHeld_";
    private static final List<Entity> ENTITIES=new ArrayList<>();
    private static final Set<ChunkPos> TICKETS=new HashSet<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){
        var owners=PlayerServerEvents.rtsPlayers.stream().map(p->p.name).filter(n->n.startsWith(PREFIX)).toList();
        for(String a:owners)for(String b:owners)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity e:ENTITIES)if(!e.isRemoved())e.discard();ENTITIES.clear();for(ChunkPos c:TICKETS)level.setChunkForced(c.x,c.z,false);TICKETS.clear();
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=600)
    public static void slimeSameEnemyAttackReleasesHoldAndActuallyHits(GameTestHelper h){exercise(h,"Slime",DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel()),6,false);}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=600)
    public static void lightBoneSameEnemyAttackReleasesHoldAndActuallyShoots(GameTestHelper h){exercise(h,"Bone",DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel()),11,true);}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=600)
    public static void windSameEnemyAttackKeepsExistingVerifiedSemantics(GameTestHelper h){exercise(h,"Wind",DreamseekersMod.WIND_KNIGHT_UNIT.get().create(h.getLevel()),6,false);}
    private static void exercise(GameTestHelper h,String kind,TeamSafeCombatUnit source,int distance,boolean ranged){
        room(h);Mob mob=source.mob();source.setOwnerName(owner(kind));source.setHoldPosition(true);place(h,mob,5,8);
        var enemy=fox(h,kind+"Enemy",5+distance,8);var own=fox(h,kind,20,18);var ally=fox(h,kind+"Ally",20,20);AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());
        float hp=enemy.getHealth();Vec3[] initial={null};boolean[] seen={false};double[] travel={0};long[] readyTick={-1},dispatchTick={-1};int[] samples={0};
        h.runAtTickTime(259,()->System.out.println("LIGHT_CLOCK_LEGACY_LIMIT kind="+kind+" helperTick="+h.getTick()+" actorTick="+mob.tickCount+" ready="+readyTick[0]+" dispatch="+dispatchTick[0]+" samples="+samples[0]));
        var seq=h.startSequence().thenWaitUntil(()->{for(Entity e:List.of(mob,enemy,own,ally))h.assertTrue(h.getTick()>=Long.getLong("dreamseekers.test.fixtureReadyDelay",0L)&&e.tickCount>2&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"real published fixtures "+kind);})
            .thenExecute(()->{initial[0]=mob.position();readyTick[0]=h.getTick();System.out.println("LIGHT_CLOCK_READY kind="+kind+" helperTick="+h.getTick()+" actorTick="+mob.tickCount);}).thenIdle(25).thenExecute(()->{
                h.assertTrue(source.getHoldPosition()&&source.getTargetGoal().getTarget()==enemy&&mob.position().distanceToSqr(initial[0])<.15&&enemy.getHealth()==hp,"HOLD selected same enemy without movement or out-of-range damage: "+kind+" pos="+mob.position()+" target="+source.getTargetGoal().getTarget());
                for(var friendly:List.of(own,ally)){source.setUnitAttackTargetForced(friendly);h.assertTrue(source.getHoldPosition()&&source.getTargetGoal().getTarget()==null&&!source.getTargetGoal().forced,"invalid own/allied forced target must not release HOLD");}
                source.setUnitAttackTarget(enemy);h.assertTrue(source.getHoldPosition()&&source.getTargetGoal().getTarget()==enemy&&!source.getTargetGoal().forced,"nonforced guarding selection keeps HOLD");
                new UnitActionItem(source.getOwnerName(),UnitAction.ATTACK,enemy.getId(),new int[]{mob.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
                dispatchTick[0]=h.getTick();System.out.println("LIGHT_SAME_TARGET_ATTACK_DISPATCH helperTick="+h.getTick()+" actorTick="+mob.tickCount+" kind="+kind+" hold="+source.getHoldPosition()+" selected="+(source.getTargetGoal().getTarget()==enemy)+" forced="+source.getTargetGoal().forced);
                h.assertTrue(!source.getHoldPosition()&&source.getTargetGoal().getTarget()==enemy&&source.getTargetGoal().forced,"same-enemy explicit ATTACK releases HOLD: "+kind);
            });
        for(int i=0;i<150;i++)seq.thenIdle(1).thenExecute(()->{
            samples[0]++;if(samples[0]%25==0)System.out.println("LIGHT_CLOCK_SAMPLE kind="+kind+" helperTick="+h.getTick()+" actorTick="+mob.tickCount+" sample="+samples[0]);
            for(BoneWingBolt bolt:h.getLevel().getEntitiesOfClass(BoneWingBolt.class,area(h)))if(bolt.getOwner()==mob){seen[0]=true;travel[0]=Math.max(travel[0],bolt.position().distanceTo(mob.position()));}
            h.assertTrue(own.getHealth()==500&&ally.getHealth()==500,"never hit own/allied witness");
        });
        seq.thenExecute(()->{
            h.assertTrue(mob.position().distanceToSqr(initial[0])>1&&enemy.getHealth()<hp,"actual pursuit and real damage after explicit release: "+kind);
            if(ranged)h.assertTrue(seen[0]&&travel[0]>1&&!enemy.isOnFire(),"actual traveling filtered bolt, no native fire/AOE");
            System.out.println("DREAMSEEKERS_LIGHT_SAME_TARGET_ATTACK_PASS kind="+kind+" hp="+enemy.getHealth()+" travel="+travel[0]);
        }).thenSucceed();
    }
    private static String owner(String suffix){String n=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(n)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(n,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(n,500,500,500));}return n;}
    private static GoldenFoxUnit fox(GameTestHelper h,String name,int x,int z){var f=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());f.setOwnerName(owner(name));f.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);f.setHealth(500);place(h,f,x,z);return f;}
    private static void place(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);ENTITIES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"fixture added normally");}
    private static AABB area(GameTestHelper h){return new AABB(h.absolutePos(BlockPos.ZERO),h.absolutePos(new BlockPos(26,10,26)));}
    private static void room(GameTestHelper h){ServerLevel l=h.getLevel();BlockPos p=h.absolutePos(BlockPos.ZERO);
        for(int x=0;x<26;x++)for(int z=0;z<26;z++){ChunkPos c=new ChunkPos(p.offset(x,0,z));if(!l.getForcedChunks().contains(c.toLong())){l.setChunkForced(c.x,c.z,true);TICKETS.add(c);}}
        for(int x=0;x<26;x++)for(int z=0;z<26;z++)for(int y=0;y<=10;y++)if(y==0||y==10||x==0||x==25||z==0||z==25)l.setBlock(p.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        l.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p,p.offset(26,l.getMaxBuildHeight()-p.getY(),26))).forEach(Entity::discard);
        for(int x=1;x<25;x++)for(int z=1;z<25;z++)for(int y=1;y<10;y++)l.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
    }
}
