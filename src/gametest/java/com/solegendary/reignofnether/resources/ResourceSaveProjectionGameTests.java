package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("reignofnether_resource_economy")
@PrefixGameTestTemplate(false)
public final class ResourceSaveProjectionGameTests {
    @GameTest(templateNamespace="reignofnether_resource_economy",template="ore_mine_test_arena",batch="resource_economy_save_projection",timeoutTicks=80)
    public static void realWorkerCargoCannotOverflowSavedFullAccount(GameTestHelper h){
        String owner="ResourceSaveProjectionTest_Cargo";
        h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null&&ResourcesServerEvents.resourcesList.stream().noneMatch(r->r.ownerName.equals(owner)),"fresh cargo fixture account");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,Factions.VILLAGERS));
        Resources account=new Resources(owner,Integer.MAX_VALUE,10,20);ResourcesServerEvents.resourcesList.add(account);
        var worker=EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());h.assertTrue(worker!=null,"registered real worker");
        try {
            worker.setOwnerName(owner);worker.setNoAi(true);BlockPos position=h.absolutePos(new BlockPos(3,2,3));worker.moveTo(position.getX()+.5,position.getY(),position.getZ()+.5);
            worker.getItems().add(new ItemStack(Items.WHEAT,1));h.assertTrue(h.getLevel().addFreshEntity(worker),"real worker joins server level");
            h.assertTrue(UnitServerEvents.getAllUnits().contains(worker),"ordinary unit roster includes carried resources");
            int carried=Resources.getTotalResourcesFromItems(worker.getItems()).food;h.assertTrue(carried>0,"real original wheat source contributes positive cargo");
            ResourcesServerEvents.saveResources(h.getLevel());ResourcesSaveData data=ResourcesSaveData.getInstance(h.getLevel());
            Resources saved=data.resources.stream().filter(r->r.ownerName.equals(owner)).findFirst().orElseThrow();
            h.assertTrue(saved.food==Integer.MAX_VALUE,"saved max account plus real carried food saturates rather than wraps negative");
            h.assertTrue(account.food==Integer.MAX_VALUE&&Resources.getTotalResourcesFromItems(worker.getItems()).food==carried,"save projection leaves live balance and worker cargo unchanged");
            Resources restored=ResourcesSaveData.load(data.save(new CompoundTag())).resources.stream().filter(r->r.ownerName.equals(owner)).findFirst().orElseThrow();
            h.assertTrue(restored.food==Integer.MAX_VALUE,"normal saved-data reload keeps nonnegative saturated balance");
            System.out.println("RESOURCE_SAVE_CARGO_SATURATION_PASS assertions=8 carried="+carried);h.succeed();
        }finally{PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.remove(account);worker.discard();}
    }
}
