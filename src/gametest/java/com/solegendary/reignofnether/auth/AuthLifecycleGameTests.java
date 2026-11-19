package com.solegendary.reignofnether.auth;

import com.solegendary.reignofnether.sandbox.SandboxEditorTestGrant;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
import java.util.UUID;

/** Old public API/schema only: compiles unchanged over API64 for behavior comparison. */
@GameTestHolder("api65_auth_lifecycle") @PrefixGameTestTemplate(false)
public final class AuthLifecycleGameTests {
    @GameTest(templateNamespace="api65_auth_lifecycle",template="ore_mine_test_arena",batch="auth_identity_login")
    public static void legacyHumanSlotRebindsWithoutResettingHistory(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)) {
            var original=f.existing(f.actor.getId()+10000,Factions.VILLAGERS);
            original.beaconOwnerTicks=71;original.creepScore=73;original.itemsDropped=0;
            var data=new RTSPlayerSaveData();data.rtsPlayers.add(original);
            CompoundTag old=data.save(new CompoundTag());old.getList("rtsplayers",Tag.TAG_COMPOUND).getCompound(0).remove("playerUuid");
            var restored=RTSPlayerSaveData.load(old).rtsPlayers.get(0);
            PlayerServerEvents.rtsPlayers.clear();PlayerServerEvents.rtsPlayers.add(restored);
            PlayerServerEvents.rtsSyncingEnabled=false;
            PlayerServerEvents.onPlayerJoin(new PlayerEvent.PlayerLoggedInEvent(f.actor));
            h.assertTrue(restored.id==f.actor.getId(),"authenticated legacy-name login rebinds runtime entity id");
            h.assertTrue(restored.beaconOwnerTicks==71&&restored.creepScore==73,"existing historical values remain unchanged");
            var saved=new RTSPlayerSaveData();saved.rtsPlayers.add(restored);var tag=saved.save(new CompoundTag()).getList("rtsplayers",Tag.TAG_COMPOUND).getCompound(0);
            h.assertTrue(tag.hasUUID("playerUuid")&&tag.getUUID("playerUuid").equals(f.actor.getUUID()),"authenticated UUID persisted only after observed login");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_lifecycle",template="ore_mine_test_arena",batch="auth_identity_codec")
    public static void knownHumanUuidSurvivesCodecAndLegacyAbsentStaysAbsent(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)) {
            var data=new RTSPlayerSaveData();data.rtsPlayers.add(f.existing(f.actor.getId()+10000,Factions.VILLAGERS));
            CompoundTag source=data.save(new CompoundTag());CompoundTag player=source.getList("rtsplayers",Tag.TAG_COMPOUND).getCompound(0);UUID id=UUID.randomUUID();player.putUUID("playerUuid",id);
            CompoundTag round=RTSPlayerSaveData.load(source).save(new CompoundTag()).getList("rtsplayers",Tag.TAG_COMPOUND).getCompound(0);
            h.assertTrue(round.hasUUID("playerUuid")&&round.getUUID("playerUuid").equals(id),"known stable identity round-trips");
            player.remove("playerUuid");CompoundTag legacy=RTSPlayerSaveData.load(source).save(new CompoundTag()).getList("rtsplayers",Tag.TAG_COMPOUND).getCompound(0);
            h.assertTrue(!legacy.hasUUID("playerUuid"),"offline legacy data does not fabricate a UUID");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_lifecycle",template="ore_mine_test_arena",batch="auth_identity_respawn")
    public static void registeredRespawnEventRebindsHumanSlot(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)) {
            int old=f.actor.getId();var slot=f.existing(old,Factions.VILLAGERS);
            try{f.actor.setId(old+10000);MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(f.actor,false));
                h.assertTrue(slot.id==f.actor.getId(),"registered respawn event refreshes current entity id");
                h.assertTrue(PlayerServerEvents.players.stream().filter(p->p==f.actor).count()==1,"one current player entry after respawn");
            }finally{f.actor.setId(old);}
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_lifecycle",template="ore_mine_test_arena",batch="auth_direct_start")
    public static void trustedDirectFirstSandboxStartStillWorks(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h); var grant=new SandboxEditorTestGrant(f.actor, null)) {
            PlayerServerEvents.startRTS(f.actor.getId(),Vec3.atCenterOf(h.absolutePos(new BlockPos(3,2,3))),Factions.NONE);
            h.assertTrue(PlayerServerEvents.rtsPlayers.size()==1&&PlayerServerEvents.rtsPlayers.get(0).faction==Factions.NONE,"existing trusted direct start API does not require a mode packet");
        }h.succeed();
    }
}
