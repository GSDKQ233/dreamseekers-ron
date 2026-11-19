package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;
import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

/** New audit assertions: intentionally expected to FAIL on clean 48ece07.
 * Test-only; does not patch production handlers, ledger or eligibility.
 * Embedded authenticated handler test is NOT a real socket/client handshake.
 * Fresh entity native NBT roundtrip is NOT a separate-JVM restart test.
 */
@GameTestHolder("dreamseekers_audit_fixes")
@PrefixGameTestTemplate(false)
public final class CombatEconomyAuditGameTests {
    private static Resources account(String owner) {
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,Factions.MONSTERS));
        Resources r=new Resources(owner,1000,1000,1000,0);ResourcesServerEvents.resourcesList.add(r);return r;
    }
    private static void cleanup(String... owners) {
        for(String owner:owners)ResearchServerEvents.removeAllResearchFor(owner);
        var names=Set.of(owners);
        PlayerServerEvents.rtsPlayers.removeIf(p->names.contains(p.name));
        ResourcesServerEvents.resourcesList.removeIf(r->names.contains(r.ownerName));
    }
    private static void send(ResearchEconomyFixtures.Peer peer,ResourcesServerboundPacket packet) {
        FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());
        try {packet.encode(b);var decoded=new ResourcesServerboundPacket(b);
            if(b.readableBytes()!=0)throw new AssertionError("full legacy resource packet decoded");
            decoded.handle(()->peer.context);
        } finally {b.release();}
        peer.channel.runPendingTasks();
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="core_economy_arena",batch="reaudit_legacy_transfer",timeoutTicks=40)
    public static void negativeEmeraldPacketMustNotReverseTransfer(GameTestHelper h)throws Exception {
        String a="AuditNegA",b="AuditNegB";Resources sender=account(a),victim=account(b);
        try(var peer=new ResearchEconomyFixtures.Peer(h.getLevel(),a)) {
            h.assertTrue(!peer.actor.hasPermissions(4),"ordinary non-op sender");
            send(peer,new ResourcesServerboundPacket(ResourcesAction.SEND_RESOURCES,a,b,0,0,1,-100));
            System.out.println("REAUDIT_NEGATIVE_EMERALD senderOre="+sender.ore+" senderEmerald="+sender.emerald+" victimOre="+victim.ore+" victimEmerald="+victim.emerald);
            h.assertTrue(sender.emerald==0&&victim.emerald==0,"negative emerald cannot credit sender/debit another RTS player");
        } finally {cleanup(a,b);}
        h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="core_economy_arena",batch="reaudit_legacy_transfer",timeoutTicks=40)
    public static void positiveEmeraldOnlyPacketMustTransfer(GameTestHelper h)throws Exception {
        String a="AuditPosA",b="AuditPosB";Resources sender=account(a),recipient=account(b);sender.emerald=100;
        try(var peer=new ResearchEconomyFixtures.Peer(h.getLevel(),a)) {
            send(peer,new ResourcesServerboundPacket(ResourcesAction.SEND_RESOURCES,a,b,0,0,0,25));
            System.out.println("REAUDIT_POSITIVE_EMERALD sender="+sender.emerald+" recipient="+recipient.emerald);
            h.assertTrue(sender.emerald==75&&recipient.emerald==25,"positive emerald transfer does not require an unrelated ore transfer");
        } finally {cleanup(a,b);}
        h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="core_economy_arena",batch="reaudit_legacy_transfer",timeoutTicks=40)
    public static void fullRecipientCannotWrapBalance(GameTestHelper h)throws Exception {
        String a="AuditMaxA",b="AuditMaxB";Resources sender=account(a),recipient=account(b);recipient.ore=Integer.MAX_VALUE;
        try(var peer=new ResearchEconomyFixtures.Peer(h.getLevel(),a)) {
            send(peer,new ResourcesServerboundPacket(ResourcesAction.SEND_RESOURCES,a,b,0,0,1,0));
            System.out.println("REAUDIT_TRANSFER_OVERFLOW sender="+sender.ore+" recipient="+recipient.ore);
            h.assertTrue(sender.ore==1000&&recipient.ore==Integer.MAX_VALUE,"reject unrepresentable recipient credit atomically without debit");
        } finally {cleanup(a,b);}
        h.succeed();
    }
    private static <T extends Mob> T spawn(GameTestHelper h,List<Entity> entities,EntityType<T> type,String owner,int x) {
        T body=type.create(h.getLevel());((Unit)body).setOwnerName(owner);body.setNoAi(true);
        BlockPos pos=h.absolutePos(new BlockPos(x,2,3));h.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);
        body.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(body),"native unit joins level");entities.add(body);return body;
    }
    private static void summonRoundtrip(GameTestHelper h,boolean bee) {
        String killer=bee?"AuditBeeKill":"AuditZomKill",owner=bee?"AuditBeeOwn":"AuditZomOwn";
        Resources ledger=account(killer);account(owner);ResearchServerEvents.addResearch(killer,CoreEconomyResearch.id("research_global_salvage"));
        List<Entity> entities=new ArrayList<>();
        Runnable finish=()->{cleanup(killer,owner);entities.forEach(Entity::discard);};
        try {
            ZombieUnit attacker=spawn(h,entities,EntityRegistrar.ZOMBIE_UNIT.get(),killer,2);
            // Keep a genuine surviving owner unit so discarding the pre-load object cannot end the match.
            spawn(h,entities,EntityRegistrar.VILLAGER_UNIT.get(),owner,20);
            Mob original=bee?spawn(h,entities,EntityRegistrar.BEE_UNIT.get(),owner,6):spawn(h,entities,EntityRegistrar.ZOMBIE_UNIT.get(),owner,6);
            // These are the actual public setters used by Bee-nest armor / Necromancer.raiseDead.
            if(original instanceof BeeUnit body)body.setIsSummoned(true);else ((ZombieUnit)original).setIsSummoned(true);
            CompoundTag saved=new CompoundTag();original.saveWithoutId(saved);
            original.discard();
            Mob restored=bee?EntityRegistrar.BEE_UNIT.get().create(h.getLevel()):EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());
            restored.load(saved);restored.setNoAi(true);
            h.assertTrue(restored!=original&&restored.getUUID().equals(original.getUUID()),"fresh native entity with saved identity, not loading into retained SynchedEntityData");
            h.assertTrue(h.getLevel().addFreshEntity(restored),"fresh native NBT-loaded unit joins world");entities.add(restored);
            boolean stillSummoned=restored instanceof BeeUnit body?body.isSummoned():((ZombieUnit)restored).isSummoned();
            ResourceCost cost=((Unit)restored).getCost();int before=ledger.food;
            System.out.println("REAUDIT_SUMMON_NBT kind="+(bee?"bee":"zombie")+" flag="+stillSummoned+" F="+cost.food+" W="+cost.wood+" O="+cost.ore+" owner="+((Unit)restored).getOwnerName());
            restored.setHealth(1);h.assertTrue(restored.hurt(h.getLevel().damageSources().mobAttack(attacker),1000),"actual fatal hurt after fresh NBT load");
            h.runAtTickTime(5,()->{
                boolean claimed=RtsKillValueClaims.isClaimed(restored);int delta=ledger.food-before;
                System.out.println("REAUDIT_SUMMON_KILL kind="+(bee?"bee":"zombie")+" claimed="+claimed+" foodDelta="+delta+" remainder="+ledger.conversionRemainders);
                finish.run();
                h.assertTrue(stillSummoned&&!claimed&&delta==0,"free summoned provenance must survive fresh native NBT load and remain ineligible for economic rewards");
                h.succeed();
            });
        } catch(RuntimeException|Error failure) {finish.run();throw failure;}
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="core_economy_arena",batch="reaudit_summon_nbt",timeoutTicks=40)
    public static void zombieSummonRoundtripMustStayValueless(GameTestHelper h){summonRoundtrip(h,false);}
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="core_economy_arena",batch="reaudit_summon_nbt",timeoutTicks=40)
    public static void beeSummonRoundtripMustStayValueless(GameTestHelper h){summonRoundtrip(h,true);}
}
