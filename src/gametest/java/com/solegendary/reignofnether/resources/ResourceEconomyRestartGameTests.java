package com.solegendary.reignofnether.resources;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/** Two actual JVMs. Sidecar is expectation only: verify never injects accounts, buildings or players. */
@GameTestHolder("reignofnether_resource_economy_restart")
@PrefixGameTestTemplate(false)
public final class ResourceEconomyRestartGameTests {
    private static final String OWNER="ResourceRestart_Original";
    private static final ResourceLocation SHADOW=ResourceLocation.parse("test_resource_restart:shadow");
    private static final BlockPos ORIGIN=new BlockPos(4096,90,4096);
    private static final File MANIFEST=new File("resource-economy-restart-expectations.nbt");
    private static int assertions;
    private static void check(GameTestHelper h,boolean condition,String why){assertions++;h.assertTrue(condition,why);}
    private static Resources account(){return ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(OWNER)).findFirst().orElseThrow();}
    private static CompoundTag snapshot(Resources r){CompoundTag n=new CompoundTag();n.putInt("food",r.food);n.putInt("wood",r.wood);n.putInt("ore",r.ore);n.putInt("emerald",r.emerald);r.writeExtended(n);return n;}
    @GameTest(templateNamespace="reignofnether_resource_economy_restart",template="ore_mine_test_arena",batch="resource_economy_restart",timeoutTicks=180)
    public static void ordinaryWorldRestartRetainsAccountAndOneShotPaidReceipt(GameTestHelper h) throws Exception {
        assertions=0;ResourceExtensionRegistry.register(SHADOW,1000,"resources.test_resource_restart.shadow",ResourceLocation.parse("minecraft:textures/item/amethyst_shard.png"));
        switch(System.getProperty("resource.economy.restart.phase","")){
            case "prepare" -> prepare(h);case "verify" -> verify(h);default -> throw new IllegalArgumentException("resourceEconomyRestartPhase must be prepare or verify");
        }
    }
    private static void prepare(GameTestHelper h){
        check(h,!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&ResourcesServerEvents.resourcesList.stream().noneMatch(r->r.ownerName.equals(OWNER)),"fresh isolated restart fixture, not reusing a prepared account");
        var level=h.getLevel();CompoundTag expected=new CompoundTag();expected.putLong("jvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        var forced=new ArrayList<Long>();for(int cx=256;cx<=257;cx++)for(int cz=256;cz<=257;cz++){long key=ChunkPos.asLong(cx,cz);check(h,!level.getForcedChunks().contains(key),"restart fixture owns only new forced chunks");level.setChunkForced(cx,cz,true);forced.add(key);}expected.putLongArray("forcedChunks",forced);
        for(int x=-1;x<28;x++)for(int z=-1;z<28;z++){level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<20;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(BuildingBlock block:BuildingUtils.getAbsoluteBlockData(Buildings.BLACKSMITH.getRelativeBlockData(level),level,ORIGIN,Rotation.NONE))level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        BuildingPlacement anchor=BuildingUtils.getNewBuildingPlacement(Buildings.BLACKSMITH,level,ORIGIN,Rotation.NONE,OWNER,false);BuildingServerEvents.getBuildings().add(anchor);
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,Factions.VILLAGERS));Resources a=new Resources(OWNER,300,400,500,6);a.extra.put(SHADOW,137);ResourcesServerEvents.resourcesList.add(a);
        h.runAtTickTime(35,()->{
            check(h,anchor.isBuilt&&anchor.getBlocksPlaced()>0,"genuine world structure completed and protects account from defeat");
            var paid=ResourceTransaction.pay(level.getServer(),a,a.accountEpoch,a.revision,"stage-a/restart-pending-receipt",new ResourceTransaction.Cost(11,13,17,2,Map.of(SHADOW,19)));
            check(h,paid.applied()&&paid.receiptId()!=null,"server payment creates genuinely unsettled receipt");
            var income=ResourceTransaction.creditIncome(level.getServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(1,2,3,Map.of(SHADOW,4)),Map.of("stage-a/shadow",9876L));
            check(h,income.applied()&&a.revision>0&&a.conversionRemainders.get("stage-a/shadow")==9876L,"nonzero revision and fraction are authoritative transaction state");
            expected.putUUID("receiptId",paid.receiptId());expected.put("account",snapshot(a));expected.putLong("origin",ORIGIN.asLong());
            // Uses ordinary save APIs; subsequent normal server shutdown must also finish before verify starts.
            PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(level);ResourcesServerEvents.saveResources(level);
            try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
            System.out.println("RESOURCE_ECONOMY_RESTART_PREPARED assertions="+assertions+" epoch="+a.accountEpoch+" revision="+a.revision+" receipt="+paid.receiptId());h.succeed();
        });
    }
    private static void verify(GameTestHelper h) throws Exception {
        check(h,MANIFEST.isFile(),"first JVM left its expectations manifest");CompoundTag expected=NbtIo.readCompressed(MANIFEST);
        check(h,expected.getLong("jvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"verify is a different JVM, not an in-memory reload");
        for(long key:expected.getLongArray("forcedChunks")){check(h,h.getLevel().getForcedChunks().contains(key),"world restored forced chunk state normally");ChunkPos c=new ChunkPos(key);h.getLevel().getChunk(c.x,c.z);}
        check(h,PlayerServerEvents.getRTSPlayer(OWNER)!=null,"ordinary ServerStarted restored RTS identity");
        BuildingPlacement anchor=BuildingServerEvents.getBuildings().stream().filter(p->p.ownerName.equals(OWNER)&&p.originPos.equals(ORIGIN)).findFirst().orElseThrow();
        check(h,anchor.isBuilt&&anchor.getBuilding()==Buildings.BLACKSMITH&&anchor.getBlocksPlaced()>0,"ordinary loader restored the real anchor and saved world blocks");
        Resources restored=account();check(h,snapshot(restored).equals(expected.getCompound("account")),"normal startup retains exact balances, epoch, revision, remainder and unsettled receipt");
        h.runAtTickTime(25,()->{
            check(h,PlayerServerEvents.getRTSPlayer(OWNER)!=null&&account()==restored,"restored account remains live through real server ticks");check(h,snapshot(restored).equals(expected.getCompound("account")),"runtime neither replays payment nor resets account identity");
            UUID epoch=restored.accountEpoch;long before=restored.revision;CompoundTag original=snapshot(restored);UUID receiptId=expected.getUUID("receiptId");
            var fake=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(OWNER.getBytes(StandardCharsets.UTF_8)),OWNER));
            ResourcesServerEvents.onPlayerJoin(new PlayerEvent.PlayerLoggedInEvent(fake));
            check(h,account()==restored&&snapshot(restored).equals(original),"ordinary resource login handler does not create a new epoch or reset balances");
            var refund=ResourceTransaction.refund(h.getLevel().getServer(),restored,epoch,before,receiptId,10000);
            check(h,refund.applied()&&restored.revision==before+1,"restored original payer can settle saved receipt once");
            check(h,restored.food==301&&restored.wood==402&&restored.ore==503&&restored.emerald==6&&restored.extra.get(SHADOW)==141,"refund restores exact originally paid components, not current quote");
            check(h,restored.accountEpoch.equals(epoch)&&restored.conversionRemainders.get("stage-a/shadow")==9876L,"refund preserves account epoch and unrelated conversion remainder");
            CompoundTag settled=snapshot(restored);check(h,!ResourceTransaction.refund(h.getLevel().getServer(),restored,epoch,restored.revision,receiptId,10000).applied(),"fresh-revision replay cannot refund receipt twice");check(h,snapshot(restored).equals(settled),"replayed refund is entirely mutation-free");
            System.out.println("RESOURCE_ECONOMY_PROCESS_RESTART_PASS assertions="+assertions+" epoch="+epoch+" savedRevision="+before+" finalRevision="+restored.revision);
            BuildingServerEvents.getBuildings().remove(anchor);ResourcesServerEvents.removeAccount(OWNER);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));
            for(long key:expected.getLongArray("forcedChunks")){ChunkPos c=new ChunkPos(key);h.getLevel().setChunkForced(c.x,c.z,false);}h.succeed();
        });
    }
}
