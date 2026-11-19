package com.dreamseekers.economy;

import com.dreamseekers.building.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.data.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.player.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.Map;

/** Fixed automatic income definitions; no packets, purchases, research or account ledger of its own. */
public final class DreamPassiveEconomy {
    public static final ResourceLocation SHADOW=ResourceLocation.fromNamespaceAndPath("dreamseekers","shadow_energy");
    public static final ResourceLocation SHADOW_ICON=ResourceLocation.fromNamespaceAndPath("dreamseekers","textures/icons/shadow_energy.png");
    public static final int SHADOW_CAP=1000;
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"dreamseekers");
    public static final RegistryObject<DecorativeGardenWheatBlock> DECORATIVE_WHEAT=BLOCKS.register("decorative_garden_wheat",DecorativeGardenWheatBlock::new);
    public static final DataType<ClockState> CLOCK=DataType.createRegistered(
        ResourceLocation.fromNamespaceAndPath("dreamseekers","passive_production"),
        (tag,server)->ClockState.read(tag),ClockState::write,ClockState::new);
    private DreamPassiveEconomy(){}
    public static void bootstrap(IEventBus bus){
        BLOCKS.register(bus);
        ResourceExtensionRegistry.register(SHADOW,SHADOW_CAP,"resource.dreamseekers.shadow_energy",SHADOW_ICON);
        // Force registration before ordinary world save decoding.
        var ignored=DreamGardenPlacement.DECOR_SCHEMA;
    }
    public static int interval(Building building) {
        if(building instanceof ReverieGarden)return 40;
        if(building instanceof DreamScaffoldBuilding b)return switch(b.spec()) {
            case SHADOW_MINE -> 40;case SHADOW_FURNACE -> 100;default -> 0;
        };
        return 0;
    }
    /** F/O follows any valid current RTS owner; S remains Dream-only. Capture always revokes old progress. */
    public static boolean supportsIncomeOwner(RTSPlayer player) { return player!=null && player.faction!=null; }
    /** Native sandbox accounts may exercise all Dream content; other real factions remain excluded. */
    public static boolean isDreamOwner(RTSPlayer player) {return DreamFactionAccess.allows(player);}
    /** Server-side convenience; clients must supply their synchronized owner through the overload. */
    public static String factionPauseReason(BuildingPlacement placement) {
        return factionPauseReason(placement.getBuilding(),PlayerServerEvents.getRTSPlayer(placement.ownerName));
    }
    /** Shared presentation policy; no account reads and no income/spending authority. */
    public static String factionPauseReason(Building building, RTSPlayer owner) {
        return building instanceof DreamScaffoldBuilding b&&b.spec()==DreamBuildingSpec.SHADOW_FURNACE
            &&!isDreamOwner(owner)?"dreamseekers.error.shadow_furnace_faction":null;
    }
    public static void tick(Level level,BuildingPlacement placement) {
        int interval=interval(placement.getBuilding());
        if(interval==0 || !(level instanceof ServerLevel server) || level!=placement.getLevel()
                || level.dimension()!=Level.OVERWORLD || !server.getServer().isSameThread()
                || !BuildingServerEvents.getBuildings().contains(placement) || placement.isDestroyedServerside)return;
        ClockState persisted=placement.getDataStorage().getData(CLOCK);
        if(!persisted.supported)return;
        DreamPassiveClock clock=persisted.clock(interval);
        clock.observeOwner(placement.ownerName,placement.getCaptureRevision());
        RTSPlayer player=PlayerServerEvents.getRTSPlayer(placement.ownerName);
        Resources account=ExtendedResourcesSync.account(placement.ownerName);
        if(account!=null)persisted.observeAccount(account.accountEpoch);
        boolean garden=placement.getBuilding() instanceof ReverieGarden;
        boolean shadow=!garden&&((DreamScaffoldBuilding)placement.getBuilding()).spec()==DreamBuildingSpec.SHADOW_FURNACE;
        boolean active=placement.isBuilt&&!placement.shouldBeDestroyed()&&DreamEconomyPolicy.allowsProduction(supportsIncomeOwner(player),isDreamOwner(player),shadow)&&account!=null&&account.extendedStateSupported()
            &&account.revision>=0&&account.revision<Long.MAX_VALUE
            &&account.food>=0&&account.ore>=0&&account.wood>=0&&account.emerald>=0
            &&(shadow?account.extra.getOrDefault(SHADOW,0)>=0&&account.extra.getOrDefault(SHADOW,0)<SHADOW_CAP
                        :garden?account.food<Integer.MAX_VALUE:account.ore<Integer.MAX_VALUE);
        if(!clock.advanceToDue(active))return;
        ResourceTransaction.Cost amount=shadow?new ResourceTransaction.Cost(0,0,0,Map.of(SHADOW,1))
                :garden?new ResourceTransaction.Cost(1,0,0):new ResourceTransaction.Cost(0,0,1);
        var result=ResourceTransaction.creditIncome(server.getServer(),account,account.accountEpoch,account.revision,amount);
        int credited=shadow?result.credited().extra().getOrDefault(SHADOW,0):garden?result.credited().food():result.credited().ore();
        clock.commitIncome(result.applied()&&credited==1);
        if(result.applied()&&credited>0)DreamPassiveIncomePublication.publish(server.getServer(),player,result);
    }
    public static final class ClockState {
        private boolean supported=true;
        private CompoundTag quarantinedPayload;
        private long savedElapsed;
        private String savedOwner="";
        private DreamPassiveClock clock;
        private java.util.UUID accountEpoch;
        public void observeAccount(java.util.UUID epoch){
            if(accountEpoch!=null&&!accountEpoch.equals(epoch)){savedElapsed=0;if(clock!=null)clock.resetProgress();}
            accountEpoch=epoch;
        }
        public DreamPassiveClock clock(int interval){
            if(clock==null)clock=DreamPassiveClock.restore(interval,savedElapsed,savedOwner);
            if(clock.interval()!=interval)throw new IllegalStateException("Producer definition changed");return clock;
        }
        public boolean supported(){return supported;}
        public static ClockState read(CompoundTag tag){
            ClockState s=new ClockState();
            // Preserve an unsupported future/malformed clock verbatim instead of destroying it on save.
            s.quarantinedPayload=tag.copy();
            if(!tag.contains("schema",Tag.TAG_INT)||tag.getInt("schema")!=1
                    ||!tag.contains("owner",Tag.TAG_STRING)||tag.getString("owner").length()>128){s.supported=false;return s;}
            Tag value=tag.get("elapsed");
            if(!(value instanceof ByteTag||value instanceof ShortTag||value instanceof IntTag||value instanceof LongTag)){s.supported=false;return s;}
            s.savedElapsed=((NumericTag)value).getAsLong();s.savedOwner=tag.getString("owner");
            if(tag.contains("accountEpoch")) {
                if(!tag.hasUUID("accountEpoch")){s.supported=false;return s;}
                s.accountEpoch=tag.getUUID("accountEpoch");
            }
            return s;
        }
        public CompoundTag write(){
            if(!supported&&quarantinedPayload!=null)return quarantinedPayload.copy();
            CompoundTag tag=new CompoundTag();tag.putInt("schema",supported?1:-1);
            if(accountEpoch!=null)tag.putUUID("accountEpoch",accountEpoch);
            tag.putString("owner",clock==null?savedOwner:clock.owner());tag.putLong("elapsed",clock==null?savedElapsed:clock.elapsed());return tag;
        }
    }
}
