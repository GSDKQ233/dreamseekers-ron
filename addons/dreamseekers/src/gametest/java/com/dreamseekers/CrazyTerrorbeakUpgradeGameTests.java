// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.unit.*;
import com.dreamseekers.shadow.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.UUID;
@GameTestHolder(DreamseekersMod.MOD_ID) @PrefixGameTestTemplate(false)
public final class CrazyTerrorbeakUpgradeGameTests {
    static final String BATCH="dreamseekers_crazy_upgrade";
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel l){CrazyTerrorbeakFixture.cleanup(l);}
    static TerrorbeakUnit ordinary(CrazyTerrorbeakFixture f,String owner,int x,int z){var e=DreamseekersMod.TERRORBEAK_UNIT.get().create(f.h.getLevel());e.setOwnerName(CrazyTerrorbeakFixture.account(owner));e.setHoldPosition(true);return f.place(e,x,z);}
    static ServerPlayer sender(GameTestHelper h,String name){return FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));}
    static TerrorbeakUpgradeRequest request(Entity e,long revision){return new TerrorbeakUpgradeRequest(1,UUID.randomUUID(),new TerrorbeakUpgradeRequest.Source(e.getId(),e.getUUID(),revision,e.level().dimension().location()),TerrorbeakUpgradeRequest.UpgradeTarget.CRAZY_TERRORBEAK);}
    @GameTest(template="crazy_unit_arena",batch=BATCH,timeoutTicks=100)
    public static void disconnectedFakePlayerUpgradeNeverChargesOrReplaces(GameTestHelper h){
        var f=new CrazyTerrorbeakFixture(h);var base=ordinary(f,"Upgrade",5,8);var advanced=f.unit("Upgrade",20,8);advanced.setHoldPosition(true);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ready(base,advanced),"real published distinct variants")).thenExecute(()->{
            base.setHealth(63);base.setScenarioRoleIndex(84);base.getPersistentData().putString("upgrade_probe","unchanged");
            var req=request(base,base.rts().ownershipRevision);var player=sender(h,base.getOwnerName());
            new UnitActionItem(base.getOwnerName(),UnitAction.MOVE,-1,new int[]{base.getId()},f.pos(14,0,8),net.minecraft.core.BlockPos.ZERO).action(h.getLevel());
            UnitServerEvents.addActionItem(base.getOwnerName(),UnitAction.MOVE,-1,new int[]{base.getId()},f.pos(14,0,20),net.minecraft.core.BlockPos.ZERO,true);
            var cooldownProbe=new com.solegendary.reignofnether.ability.Ability(UnitAction.STOP,30,0,0,false);
            base.getAbilityCooldowns().put(cooldownProbe,17f);var cooldownBefore=new java.util.HashMap<>(base.getAbilityCooldowns());
            int pendingBefore=UnitServerEvents.getUnitActionSlowQueue().size();
            CompoundTag before=new CompoundTag();base.saveWithoutId(before);var selected=base.getTarget();var move=base.getMoveGoal().getMoveTarget();
            h.assertTrue(move!=null&&pendingBefore>0,"real command and queued second leg precede unavailable request");
            int units=UnitServerEvents.getAllUnits().size(),population=UnitServerEvents.getCurrentPopulation(base.getOwnerName());
            for(int i=0;i<3;i++)h.assertTrue(TerrorbeakUpgradeService.request(player,req)==TerrorbeakUpgradeService.Result.NOT_OWNER,"unconnected FakePlayer is not an authenticated owner; replay cannot convert");
            CompoundTag after=new CompoundTag();base.saveWithoutId(after);
            h.assertTrue(before.equals(after)&&h.getLevel().getEntity(base.getUUID())==base&&base.getTarget()==selected&&java.util.Objects.equals(base.getMoveGoal().getMoveTarget(),move),"source UUID, HP, inventory, command, cooldown and saved fields unchanged in same callback");
            h.assertTrue(cooldownBefore.equals(base.getAbilityCooldowns())&&UnitServerEvents.getUnitActionSlowQueue().size()==pendingBefore,"nonzero cooldown map and queued command not serialized in NBT are separately unchanged");base.getAbilityCooldowns().remove(cooldownProbe);
            h.assertTrue(UnitServerEvents.getAllUnits().size()==units&&UnitServerEvents.getCurrentPopulation(base.getOwnerName())==population,"no replacement, duplicate actor or population reservation");
            var resources=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(base.getOwnerName())).findFirst().orElseThrow();
            h.assertTrue(resources.food==500&&resources.wood==500&&resources.ore==500,"no undefined upgrade charge or refund");
            h.assertTrue(advanced.getType()!=base.getType()&&advanced.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.TERRORBEAK_DREAD_ROAR))&&base.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.TERRORBEAK_DREAD_ROAR,com.dreamseekers.shadow.TerrorbeakUpgradeAbility.INSTANCE)),"exact approved buttons: ordinary roar and paid upgrade; Crazy controlled roar only");
            System.out.println("CRAZY_UPGRADE_VALID_UNAVAILABLE_NO_MUTATION_PASS");
        }).thenSucceed();
    }
    @GameTest(template="crazy_unit_arena",batch=BATCH,timeoutTicks=100)
    public static void malformedIdentitySchemaDimensionAndWrongSubtypeFailClosed(GameTestHelper h){
        var f=new CrazyTerrorbeakFixture(h);var base=ordinary(f,"Malformed",5,8);var advanced=f.unit("Malformed",20,8);advanced.setHoldPosition(true);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ready(base,advanced),"upgrade negative fixtures published")).thenExecute(()->{
            var p=sender(h,base.getOwnerName());var r=request(base,base.rts().ownershipRevision);var s=r.source();
            h.assertTrue(TerrorbeakUpgradeService.request(null,r)==TerrorbeakUpgradeService.Result.INVALID_REQUEST&&TerrorbeakUpgradeService.request(p,null)==TerrorbeakUpgradeService.Result.INVALID_REQUEST,"null sender/request rejected");
            for(var bad:java.util.List.of(new TerrorbeakUpgradeRequest(2,r.requestId(),s,r.target()),new TerrorbeakUpgradeRequest(1,null,s,r.target()),new TerrorbeakUpgradeRequest(1,r.requestId(),s,null),new TerrorbeakUpgradeRequest(1,r.requestId(),new TerrorbeakUpgradeRequest.Source(s.entityId(),s.entityUuid(),-1,s.dimension()),r.target())))h.assertTrue(TerrorbeakUpgradeService.request(p,bad)==TerrorbeakUpgradeService.Result.INVALID_REQUEST,"schema/null/negative revision rejected");
            var wrongId=new TerrorbeakUpgradeRequest(1,r.requestId(),new TerrorbeakUpgradeRequest.Source(s.entityId(),UUID.randomUUID(),s.ownershipRevision(),s.dimension()),r.target());
            h.assertTrue(TerrorbeakUpgradeService.request(p,wrongId)==TerrorbeakUpgradeService.Result.INVALID_SOURCE,"ID/UUID mismatch rejected");
            var wrongWorld=new TerrorbeakUpgradeRequest(1,r.requestId(),new TerrorbeakUpgradeRequest.Source(s.entityId(),s.entityUuid(),s.ownershipRevision(),net.minecraft.world.level.Level.NETHER.location()),r.target());
            h.assertTrue(TerrorbeakUpgradeService.request(p,wrongWorld)==TerrorbeakUpgradeService.Result.WRONG_DIMENSION,"cross-world request rejected");
            h.assertTrue(TerrorbeakUpgradeService.request(p,request(advanced,advanced.rts().ownershipRevision))==TerrorbeakUpgradeService.Result.INVALID_SOURCE,"cannot upgrade already-crazy sibling subtype");
            var missing=DreamseekersMod.TERRORBEAK_UNIT.get().create(h.getLevel());missing.setOwnerName(base.getOwnerName());
            h.assertTrue(TerrorbeakUpgradeService.request(p,request(missing,missing.rts().ownershipRevision))==TerrorbeakUpgradeService.Result.INVALID_SOURCE,"unpublished source rejected");
            base.setHealth(0);h.assertTrue(TerrorbeakUpgradeService.request(p,r)==TerrorbeakUpgradeService.Result.INVALID_SOURCE,"dead source rejected");
        }).thenSucceed();
    }
    @GameTest(template="crazy_unit_arena",batch=BATCH,timeoutTicks=100)
    public static void currentOwnerNotFactionOrAllianceAndObservedAbaBoundAuthority(GameTestHelper h){
        var f=new CrazyTerrorbeakFixture(h);var base=ordinary(f,"Owner",5,8);String foreign=CrazyTerrorbeakFixture.account("Foreign");
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ready(base),"authority source published")).thenExecute(()->{
            var p=sender(h,base.getOwnerName());var other=sender(h,foreign);var r=request(base,base.rts().ownershipRevision);
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(base.getOwnerName(),foreign);
            h.assertTrue(TerrorbeakUpgradeService.request(other,r)==TerrorbeakUpgradeService.Result.NOT_OWNER,"alliance/same faction does not authorize undefined economic upgrade");
            String owner=base.getOwnerName();base.setOwnerName(foreign);base.setOwnerName(owner);
            h.assertTrue(TerrorbeakUpgradeService.request(p,r)==TerrorbeakUpgradeService.Result.STALE_OWNER,"observed owner ABA invalidates source epoch");
            h.assertTrue(TerrorbeakUpgradeService.request(p,request(base,base.rts().ownershipRevision))==TerrorbeakUpgradeService.Result.NOT_OWNER,"fresh source epoch cannot authenticate an unconnected FakePlayer");
            base.setOwnerName("");h.assertTrue(TerrorbeakUpgradeService.request(p,request(base,base.rts().ownershipRevision))==TerrorbeakUpgradeService.Result.NOT_OWNER,"blank owner cannot authorize");base.setOwnerName(owner);
        }).thenSucceed();
    }
    @GameTest(template="crazy_unit_arena",batch=BATCH,timeoutTicks=100)
    public static void nbtFlagsCannotEnableProviderOrPromoteOriginalSource(GameTestHelper h){
        var f=new CrazyTerrorbeakFixture(h);var base=ordinary(f,"NbtUpgrade",5,8);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ready(base),"NBT source published")).thenExecute(()->{
            CompoundTag tag=new CompoundTag();base.saveWithoutId(tag);tag.putBoolean("UpgradeEnabled",true);tag.putString("UpgradeTarget","dreamseekers:crazy_terrorbeak_unit");tag.putInt("UpgradePaid",999);
            var restored=DreamseekersMod.TERRORBEAK_UNIT.get().create(h.getLevel());restored.load(tag);
            h.assertTrue(restored.getType()==DreamseekersMod.TERRORBEAK_UNIT.get()&&restored.getAbilities().get().equals(java.util.List.of(com.dreamseekers.skill.UnitSkillAbility.TERRORBEAK_DREAD_ROAR,com.dreamseekers.shadow.TerrorbeakUpgradeAbility.INSTANCE)),"ordinary NBT cannot convert class or alter its two approved buttons");
            h.assertTrue(TerrorbeakUpgradeService.request(sender(h,base.getOwnerName()),request(restored,restored.rts().ownershipRevision))==TerrorbeakUpgradeService.Result.INVALID_SOURCE,"unpublished duplicate UUID not an authority alias");
            base.getPersistentData().putBoolean("UpgradeEnabled",true);
            h.assertTrue(TerrorbeakUpgradeService.request(sender(h,base.getOwnerName()),request(base,base.rts().ownershipRevision))==TerrorbeakUpgradeService.Result.NOT_OWNER,"persistent tags cannot authenticate an unconnected FakePlayer");
        }).thenSucceed();
    }
}
