package com.dreamseekers.economy;
import com.dreamseekers.building.DreamTemplateModel;
import net.minecraft.nbt.CompoundTag;

import com.dreamseekers.building.ReverieGarden;
import com.dreamseekers.building.DreamBuildingSupport;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import com.solegendary.reignofnether.building.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import java.util.ArrayList;

/** New construction never rewrites a footprint. Only the saved-entry constructor migrates old crops. */
public final class DreamGardenPlacement extends FarmPlacement {
    public static final DataType<GardenDecorationState> DECOR_SCHEMA=DataType.createRegistered(
        ResourceLocation.fromNamespaceAndPath("dreamseekers","garden_decoration_schema"),
        (tag,server)->GardenDecorationState.read(tag), GardenDecorationState::write, GardenDecorationState::legacy);

    private enum RestoreStatus { READY, RETRY_LEGACY, OPAQUE }
    private RestoreStatus restoreStatus=RestoreStatus.READY;
    private final DreamTemplateModel.State template;
    /** Used only by old garden decoration migration and its downgrade/quarantine fixtures. */
    public static ArrayList<BuildingBlock> decoratedTemplate() {
        return DreamTemplateModel.GARDEN.blocks(DreamTemplateModel.Revision.ORIGINAL);
    }
    public DreamGardenPlacement(ReverieGarden building,Level level,BlockPos origin,Rotation rotation,String owner) {
        this(building,level,origin,rotation,owner,DreamTemplateModel.GARDEN.fresh(level),
                GardenDecorationState.current(),null);
    }
    public DreamGardenPlacement(ReverieGarden building,Level level,BlockPos origin,Rotation rotation,String owner,DataStorage saved) {
        this(building,level,origin,rotation,owner,DreamTemplateModel.GARDEN.restore(saved),saved.getData(DECOR_SCHEMA),saved);
    }
    private DreamGardenPlacement(ReverieGarden building, Level level, BlockPos origin, Rotation rotation, String owner,
                                 DreamTemplateModel.State template, GardenDecorationState state, DataStorage saved) {
        // Only an original, pre-decoration save counts raw age=7 wheat before its existing migration.
        super(building,level,origin,rotation,owner,state.knownVersion()==0 && template.revision()==DreamTemplateModel.Revision.ORIGINAL
                ? BuildingUtils.getAbsoluteBlockData(DreamBuildingSupport.load(ReverieGarden.STRUCTURE),level,origin,rotation)
                : template.absolute(level,origin,rotation),false);
        this.template=template;
        if(saved!=null && level.isClientSide())throw new IllegalArgumentException("Saved-entry migration is server only");
        template.save(this,super.getDataStorage());
        // Native restore later installs the full DataStorage. The factory result must also be safe on its own.
        getDataStorage().setData(DECOR_SCHEMA,state);
        int schema=state.knownVersion();
        if(schema<0 || (schema==0 && template.revision()!=DreamTemplateModel.Revision.ORIGINAL)){restoreStatus=RestoreStatus.OPAQUE;com.solegendary.reignofnether.ReignOfNether.LOGGER.warn("Preserving unsupported garden decoration at {}; automatic garden work is paused",origin);return;}
        if(schema==0) {
            // The superclass has already read these same positions to initialize placed/health.
            java.util.Map<BlockPos,net.minecraft.world.level.block.state.BlockState> originals=new java.util.LinkedHashMap<>();
            for(BuildingBlock block:blocks)if(block.getBlockState().is(Blocks.WHEAT)) {
                BlockPos pos=block.getBlockPos();var current=level.getBlockState(pos);
                if(current.is(Blocks.WHEAT))originals.put(pos,current);
            }
            for(var entry:originals.entrySet()) {
                BlockPos pos=entry.getKey();var current=entry.getValue();
                {
                    var decorated=GardenDecorationMigration.translate(current,DreamPassiveEconomy.DECORATIVE_WHEAT.get());
                    if(!level.setBlock(pos,decorated,3)) {
                        // Keep the old schema and world on a normal same-task failure; never partly mark migrated.
                        originals.forEach((originalPos,original)->level.setBlock(originalPos,original,3));
                        restoreStatus=RestoreStatus.RETRY_LEGACY;com.solegendary.reignofnether.ReignOfNether.LOGGER.error("Garden decoration migration blocked at {}; retry on reload",pos);return;
                    }
                }
            }
            int oldHighest=highestBlockCountReached;
            blockMap.clear();setBlocks(BuildingUtils.getAbsoluteBlockData(decoratedTemplate(),level,origin,rotation));
            initPlacedBlocks();highestBlockCountReached=oldHighest;
            GardenDecorationState migrated=state.migrated();
            saved.setData(DECOR_SCHEMA,migrated);
            getDataStorage().setData(DECOR_SCHEMA,migrated);
        }
    }
    @Override public DataStorage getDataStorage() {
        var storage = super.getDataStorage();
        if (template != null) template.save(this, storage);
        return storage;
    }
    // Ordinary revisions are fixed at construction; never look up an arbitrary CHANGE_STRUCTURE path.
    @Override public void changeStructure(String name) {
    }
    @Override public CompoundTag structureSnapshot() {
        return template.snapshot(this);
    }
    @Override public boolean acceptsStructureSnapshot(CompoundTag tag) {
        return template.accepts(tag);
    }
    @Override public void applyStructureSnapshot(CompoundTag tag, boolean initial) {
        template.apply(this, tag, initial, this::restoreGeometry, value -> highestBlockCountReached = value);
    }
    @Override public void afterRestoredFromSave() {
        super.afterRestoredFromSave();
        highestBlockCountReached = template.restoredHighest(this);
    }
    @Override public boolean shouldBeDestroyed() {
        // The server checks this before tick(). Counts against a fallback footprint cannot establish damage.
        return restoreStatus!=RestoreStatus.OPAQUE && super.shouldBeDestroyed();
    }
    @Override public boolean isAttackable() {
        // Native unit and area damage also select blocks from the local template, independently of tick().
        return restoreStatus!=RestoreStatus.OPAQUE && super.isAttackable();
    }
    @Override public void tick(Level level){if(migrationReady())super.tick(level);}
    @Override public void onRemoved(RemovalReason reason){
        com.dreamseekers.buildingability.BuildingAbilitySource.detach(this);
        super.onRemoved(reason);
    }
    public boolean migrationReady(){return restoreStatus==RestoreStatus.READY;}
}
