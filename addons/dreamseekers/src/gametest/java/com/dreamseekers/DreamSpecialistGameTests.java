package com.dreamseekers;

import com.dreamseekers.unitprogression.*;
import com.dreamseekers.economy.*;
import com.dreamseekers.unit.*;
import com.dreamseekers.progression.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.faction.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Actual registered production, natural clocks and live owner-derived effects. Direct server calls,
 * not socket/GUI proof; no shortening of the four actual research durations. */
@GameTestHolder("dreamseekers_specialist")
@PrefixGameTestTemplate(false)
public final class DreamSpecialistGameTests {
    private static final class Fixture {
        final GameTestHelper h; final List<String> owners=new ArrayList<>();
        final List<BuildingPlacement> buildings=new ArrayList<>(); final List<Entity> entities=new ArrayList<>();
        final List<Long> forced=new ArrayList<>();
        Fixture(GameTestHelper h){this.h=h;}
        Resources account(String owner,Faction faction){owners.add(owner);PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,faction));
            var r=new Resources(owner,5000,5000,5000);r.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(r);return r;}
        ServerPlayer actor(String owner){return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(owner.getBytes(StandardCharsets.UTF_8)),owner));}
        BuildingPlacement install(Building definition,String owner,BlockPos pos){
            var level=h.getLevel();
            for(int x=pos.getX()>>4;x<=(pos.getX()+24)>>4;x++)for(int z=pos.getZ()>>4;z<=(pos.getZ()+24)>>4;z++){
                long key=net.minecraft.world.level.ChunkPos.asLong(x,z);
                if(!level.getForcedChunks().contains(key)){level.setChunkForced(x,z,true);forced.add(key);}}
            for(int x=0;x<24;x++)for(int z=0;z<24;z++){level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<20;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            for(var b:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,pos,net.minecraft.world.level.block.Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            var p=BuildingUtils.getNewBuildingPlacement(definition,level,pos,net.minecraft.world.level.block.Rotation.NONE,owner,false);
            BuildingServerEvents.getBuildings().add(p);buildings.add(p);return p;
        }
        <T extends Mob> T spawn(EntityType<T> type,String owner,int x){T body=type.create(h.getLevel());((Unit)body).setOwnerName(owner);body.setNoAi(true);
            var p=h.absolutePos(new BlockPos(x,3,4));body.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(h.getLevel().addFreshEntity(body),"fixture actual entity published");entities.add(body);return body;}
        void at(int tick,Runnable action){h.runAtTickTime(tick,()->{try{action.run();}catch(RuntimeException|Error error){cleanup();throw error;}});}
        void cleanup(){for(String owner:owners)ResearchServerEvents.removeAllResearchFor(owner);
            BuildingServerEvents.getBuildings().removeAll(buildings);entities.forEach(Entity::discard);
            PlayerServerEvents.rtsPlayers.removeIf(p->owners.contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->owners.contains(r.ownerName));
            for(long c:forced)h.getLevel().setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);}
        void success(){cleanup();h.succeed();}
    }
    private static String start(ServerPlayer actor,ProductionPlacement p,ReceiptBackedResearchItem item,Resources r){
        return ReceiptResearchService.start(actor,p,item,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),r.accountEpoch,r.revision);}
    private static String cancel(ServerPlayer actor,ProductionPlacement p,UUID receipt,Resources r){
        return ReceiptResearchService.cancel(actor,p,receipt,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),r.accountEpoch,r.revision);}
    private static void near(GameTestHelper h,double actual,double expected,String message){h.assertTrue(Math.abs(actual-expected)<0.001,message+": "+actual+" != "+expected);}
    @GameTest(templateNamespace="dreamseekers_specialist",template="ore_mine_test_arena",timeoutTicks=1320,batch="specialist_queues")
    public static void fourRegisteredPaidResearchNaturalDurations(GameTestHelper h){
        var f=new Fixture(h);String owner="Specialist_Queue",other="Specialist_Other";
        var account=f.account(owner,DreamseekersMod.DREAMSEEKERS);f.account(other,Factions.VILLAGERS);
        var actor=f.actor(owner);var stranger=f.actor(other);BlockPos origin=new BlockPos(22000,100,22000);
        var tree=f.install(DreamseekersMod.DREAM_ANCHOR,owner,origin);
        var ecology=f.install(DreamseekersMod.CONTRACT_ECOLOGY,owner,origin.offset(32,0,0));
        var labs=new ArrayList<ProductionPlacement>();for(int i=0;i<4;i++)labs.add((ProductionPlacement)f.install(DreamseekersMod.RESONANCE_LABORATORY,owner,origin.offset(64+32*i,0,0)));
        var items=List.of(DreamSpecialistResearch.PRECISION_TOOLS,DreamSpecialistResearch.SHELL_WEAVING,DreamSpecialistResearch.CONTRACT_CARE,DreamSpecialistResearch.STONE_SIEGE);
        int[] times={600,800,800,1200};
        f.at(20,()->{
            h.assertTrue(tree.isBuilt&&ecology.isBuilt&&labs.stream().allMatch(p->p.isBuilt),"actual structures completed");
            h.assertTrue(!DreamWindProgression.server(owner).wind(),"no wind upgrade prerequisite bypass");
            for(int i=0;i<4;i++){
                var item=items.get(i);h.assertTrue(ReignOfNetherRegistries.PRODUCTION_ITEM.get(item.id())==item&&item.defaultCost.ticks==times[i],"registered real duration "+item.id());
                h.assertTrue(DreamseekersMod.RESONANCE_LABORATORY.productions.get().contains(item),"actual host whitelist contains "+item.id());
                long revision=account.revision;
                h.assertTrue(start(stranger,labs.get(i),item,account).equals("forbidden")&&account.revision==revision,"wrong actor cannot debit "+item.id());
                h.assertTrue(start(actor,(ProductionPlacement)tree,item,account).equals("forbidden")&&account.revision==revision,"wrong host cannot debit "+item.id());
                h.assertTrue(start(actor,labs.get(i),item,account).equals("started"),"real paid start "+item.id());
                var receipt=labs.get(i).productionQueue.get(0).receiptResearch.receiptId();
                h.assertTrue(cancel(actor,labs.get(i),receipt,account).equals("cancelled_refunded"),"original receipt refunded "+item.id());
                revision=account.revision;
                h.assertTrue(cancel(actor,labs.get(i),receipt,account).equals("unknown_receipt")&&account.revision==revision,"duplicate refund cannot mint");
            }
            h.assertTrue(account.food==5000&&account.wood==5000&&account.ore==5000&&account.extra.get(DreamPassiveEconomy.SHADOW)==500&&account.paidReceipts.isEmpty(),"all four cancellation costs conserved");
            BuildingServerEvents.getBuildings().remove(ecology);
            h.assertTrue(start(actor,labs.get(2),items.get(2),account).equals("forbidden"),"care requires current live owned ecology");
            BuildingServerEvents.getBuildings().add(ecology);
            int old=account.food;account.food=74;long rev=account.revision;
            h.assertTrue(start(actor,labs.get(0),items.get(0),account).equals("insufficient_resources")&&account.revision==rev&&account.food==74,"insufficient payment no partial debit");account.food=old;
            for(int i=0;i<4;i++)h.assertTrue(start(actor,labs.get(i),items.get(i),account).equals("started"),"four independent native queues started");
            h.assertTrue(account.food==4725&&account.wood==4625&&account.ore==4700&&account.extra.get(DreamPassiveEconomy.SHADOW)==480,"exact aggregate four-tech costs");
            long revision=account.revision;
            h.assertTrue(start(actor,labs.get(1),items.get(0),account).equals("duplicate")&&account.revision==revision,"owner-wide duplicate denies a second lab");
            h.assertTrue(DreamseekersMod.RESONANCE_LABORATORY.productions.get().stream().filter(x->x instanceof DreamWindResearch.Tier).count()==15,"all fifteen wind identities retained");
        });
        f.at(610,()->h.assertTrue(!DreamSpecialistResearch.unlocked(owner,DreamSpecialistResearch.PRECISION_TOOLS_ID,false),"600 natural work ticks not completed early"));
        f.at(630,()->h.assertTrue(DreamSpecialistResearch.unlocked(owner,DreamSpecialistResearch.PRECISION_TOOLS_ID,false)&&labs.get(0).productionQueue.isEmpty(),"precision completed by natural clock"));
        f.at(810,()->h.assertTrue(!DreamSpecialistResearch.careUnlocked(owner,false)&&!DreamSpecialistResearch.unlocked(owner,DreamSpecialistResearch.SHELL_WEAVING_ID,false),"800 work ticks not completed early"));
        f.at(830,()->h.assertTrue(DreamSpecialistResearch.careUnlocked(owner,false)&&DreamSpecialistResearch.unlocked(owner,DreamSpecialistResearch.SHELL_WEAVING_ID,false),"shell andcare natural completions"));
        f.at(1210,()->h.assertTrue(!DreamSpecialistResearch.stoneSiege(false,owner),"siege full 1200 ticks not shortened"));
        f.at(1240,()->{
            h.assertTrue(DreamSpecialistResearch.stoneSiege(false,owner)&&labs.stream().allMatch(p->p.productionQueue.isEmpty())&&account.paidReceipts.isEmpty(),"all four completed and receipts consumed");
            h.assertTrue(account.food==4725&&account.wood==4625&&account.ore==4700&&account.extra.get(DreamPassiveEconomy.SHADOW)==480,"no second debit or completion refund");
            long rev=account.revision;for(int i=0;i<4;i++)h.assertTrue(start(actor,labs.get(i),items.get(i),account).equals("duplicate"),"completed item cannot repurchase");
            h.assertTrue(account.revision==rev,"completed duplicate leaves ledger unchanged");f.success();
        });
    }
    @GameTest(templateNamespace="dreamseekers_specialist",template="ore_mine_test_arena",timeoutTicks=180,batch="specialist_effects")
    public static void liveCargoShellCaptureAndUnknownAccount(GameTestHelper h){
        var f=new Fixture(h);String owner="Specialist_Effects",other="Specialist_NoTech";
        var account=f.account(owner,DreamseekersMod.DREAMSEEKERS);f.account(other,DreamseekersMod.DREAMSEEKERS);
        var worker=f.spawn(DreamseekersMod.FIREFLY_WORKER.get(),owner,2);var beetle=f.spawn(DreamseekersMod.BEETLE.get(),owner,7);var snail=f.spawn(DreamseekersMod.BASALT_SNAIL.get(),owner,12);
        double[] base={beetle.getMaxHealth(),snail.getMaxHealth()};beetle.setHealth((float)base[0]/2);snail.setHealth((float)base[1]/4);
        worker.getItems().add(new ItemStack(Items.STICK,62));
        f.at(5,()->{
            h.assertTrue(worker.getMaxResources()==100&&Unit.atThresholdResources(worker),"unresearched 50 threshold sees 62 real wood value");
            ResearchServerEvents.addResearch(owner,DreamSpecialistResearch.PRECISION_TOOLS_ID);ResearchServerEvents.addResearch(owner,DreamSpecialistResearch.SHELL_WEAVING_ID);
        });
        f.at(10,()->{
            h.assertTrue(worker.getMaxResources()==125&&!Unit.atThresholdResources(worker),"real static threshold hook sees research and62 is below63");
            worker.getItems().get(0).grow(1);h.assertTrue(Unit.atThresholdResources(worker),"actual63 cargo triggersreturn threshold");
            h.assertTrue(worker.getResourceReturnThreshold(100)==100,"carrybags larger100 threshold not reduced");
            near(h,beetle.getMaxHealth(),base[0]*1.1,"beetle actual hp modifier");near(h,beetle.getHealth()/beetle.getMaxHealth(),.5,"beetle health ratio preserved");
            near(h,snail.getMaxHealth(),base[1]*1.1,"snail actual hp modifier");near(h,snail.getHealth()/snail.getMaxHealth(),.25,"snail health ratio preserved");
        });
        f.at(70,()->{
            near(h,beetle.getMaxHealth(),base[0]*1.1,"no per tick cumulative hp");
            worker.setOwnerName(other);beetle.setOwnerName(other);snail.setOwnerName(other);
            h.assertTrue(worker.getMaxResources()==100&&worker.getItems().get(0).getCount()==63,"capture immediately removescapacity without deletingcargo");
        });
        f.at(75,()->{
            near(h,beetle.getMaxHealth(),base[0],"capture removesold modifier");near(h,beetle.getHealth()/beetle.getMaxHealth(),.5,"capture keepsratio");
            worker.setOwnerName(owner);beetle.setOwnerName(owner);snail.setOwnerName(owner);
        });
        f.at(80,()->{
            near(h,beetle.getMaxHealth(),base[0]*1.1,"ABA correctly requalifies currentowner once");
            h.assertTrue(worker.getMaxResources()==125,"ABA current research restored without addingcargo");
            CompoundTag parent=new CompoundTag(),future=new CompoundTag();future.putInt("version",999);future.putString("sentinel","preserve");parent.put(ResourceAccountCodec.KEY,future);
            account.readExtended(parent);
            h.assertTrue(!account.extendedStateSupported()&&!DreamSpecialistResearch.ownerSupported(owner,false),"unknown account version failsclosed");
            h.assertTrue(worker.getMaxResources()==100,"unknown account suppressescapacity");
            CompoundTag saved=new CompoundTag();account.writeExtended(saved);h.assertTrue(saved.getCompound(ResourceAccountCodec.KEY).equals(future),"unknown payload remains verbatim");
        });
        f.at(85,()->{near(h,beetle.getMaxHealth(),base[0],"unknown account cannot retainnew hp effect");h.assertTrue(worker.getItems().get(0).getCount()==63,"all transitions conserve actualcargo");f.success();});
    }
    @GameTest(templateNamespace="dreamseekers_specialist",template="ore_mine_test_arena",timeoutTicks=120,batch="specialist_prerequisites")
    public static void carePauseResumeSandboxAndFactionGate(GameTestHelper h){
        var f=new Fixture(h);String owner="Specialist_Sandbox";var account=f.account(owner,Factions.NONE);var actor=f.actor(owner);
        BlockPos origin=new BlockPos(23000,100,23000);
        var tree=f.install(DreamseekersMod.DREAM_ANCHOR,owner,origin);
        var ecology=f.install(DreamseekersMod.CONTRACT_ECOLOGY,owner,origin.offset(32,0,0));
        var lab=(ProductionPlacement)f.install(DreamseekersMod.RESONANCE_LABORATORY,owner,origin.offset(64,0,0));
        float[] remaining={0};UUID[] receipt={null};long[] revision={0}, pauseRevision={0};
        f.at(20,()->{
            h.assertTrue(tree.isBuilt&&ecology.isBuilt&&lab.isBuilt,"sandbox fixtures completed");
            PlayerServerEvents.getRTSPlayer(owner).faction=Factions.VILLAGERS;
            long rev=account.revision;
            h.assertTrue(start(actor,lab,DreamSpecialistResearch.CONTRACT_CARE,account).equals("forbidden")&&account.revision==rev,"normal wrong faction denied despite own structures");
            PlayerServerEvents.getRTSPlayer(owner).faction=Factions.NONE;
            h.assertTrue(start(actor,lab,DreamSpecialistResearch.CONTRACT_CARE,account).equals("started"),"real sandbox NONE may pay research");
            remaining[0]=lab.productionQueue.get(0).ticksLeft;receipt[0]=lab.productionQueue.get(0).receiptResearch.receiptId();revision[0]=account.revision;
            BuildingServerEvents.getBuildings().remove(ecology);
        });
        f.at(40,()->{
            h.assertTrue(lab.productionQueue.size()==1&&lab.productionQueue.get(0).ticksLeft==remaining[0]
                    &&account.revision==revision[0]&&account.paidReceipts.containsKey(receipt[0])&&!DreamSpecialistResearch.careUnlocked(owner,false),"lost ecology pauses native timer without refund orgrant");
            var head=lab.productionQueue.get(0);
            h.assertTrue(head.item.reportsHeadPause() && "research.economy.prerequisite_pause".equals(head.pause().reasonKey()),
                    "server projects the research hold instead of letting the HUD guess");
            pauseRevision[0]=head.pause().revision();
            BuildingServerEvents.getBuildings().add(ecology);
        });
        f.at(60,()->{
            h.assertTrue(lab.productionQueue.get(0).ticksLeft<remaining[0]&&account.revision==revision[0],"restored exact prerequisite resumes withoutnew payment");
            var head=lab.productionQueue.get(0);
            h.assertTrue(!head.pause().paused() && head.pause().revision()>pauseRevision[0]
                            && head.receiptResearch.receiptId().equals(receipt[0]),"resumed projection keeps the original paid row");
            remaining[0]=head.ticksLeft;BuildingServerEvents.getBuildings().remove(tree);
        });
        f.at(80,()->{
            h.assertTrue(lab.productionQueue.get(0).ticksLeft==remaining[0] && lab.productionQueue.get(0).pause().paused(),
                    "missing main tree also pauses the projected row");
            h.assertTrue(cancel(actor,lab,receipt[0],account).equals("cancelled_refunded"),"pausedresearch can refund without prerequisite");
            // Native sandbox grants shadow only to online players; this direct service bot fixture has no auto credit.
            h.assertTrue(account.food==5000&&account.wood==5000&&account.ore==5000&&account.extra.get(DreamPassiveEconomy.SHADOW)==500
                    &&account.paidReceipts.isEmpty()&&lab.productionQueue.isEmpty(),"paused full refund preserves all four resources");
            h.assertTrue(!DreamSpecialistResearch.careUnlocked(owner,false),"canceled research grants no care");f.success();
        });
    }

}
