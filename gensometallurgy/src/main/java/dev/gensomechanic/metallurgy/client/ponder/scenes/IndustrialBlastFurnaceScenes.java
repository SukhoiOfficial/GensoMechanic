// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.client.ponder.scenes;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlock;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlockEntity;
import dev.gensomechanic.metallurgy.content.casting.CastingTableBlockEntity;
import dev.gensomechanic.metallurgy.GensoFluids;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public final class IndustrialBlastFurnaceScenes {
    private static final BlockPos FURNACE_CONTROLLER = new BlockPos(7, 2, 7);
    private static final BlockPos CHUTE = new BlockPos(8, 11, 8);
    private static final int TEXT_TIME = 80;
    private static final int STEP_GAP = 10;
    // A 3x3x9 furnace renders fluids across roughly 8.55 blocks of usable inner height.
    private static final int THREE_BLOCK_FLUID_DISPLAY = 28_406;
    private static final int SIX_BLOCK_FLUID_DISPLAY = THREE_BLOCK_FLUID_DISPLAY * 2;

    private IndustrialBlastFurnaceScenes() {
    }

    public static void structure(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = createScene(builder, "industrial_blast_furnace_structure",
            "Building an Industrial Blast Furnace");
        SceneParts parts = sceneParts(util);
        Selection furnaceBlocks = util.select().fromTo(7, 2, 7, 9, 10, 9);
        Selection windowLayers = util.select().fromTo(7, 3, 7, 9, 9, 9);

        scene.world().modifyBlocks(windowLayers, state -> state.hasProperty(IndustrialBlastFurnaceBlock.WINDOW)
            ? state.setValue(IndustrialBlastFurnaceBlock.WINDOW, false)
            : state, false);
        scene.world().showSection(parts.basePlate(), Direction.UP);
        scene.idle(10);
        scene.world().showSection(parts.burners(), Direction.UP);
        scene.idle(10);
        scene.world().showSection(furnaceBlocks, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(TEXT_TIME)
            .pointAt(util.vector().blockSurface(new BlockPos(9, 6, 8), Direction.EAST))
            .placeNearTarget()
            .attachKeyFrame()
            .text("The Industrial Blast Furnace is a multiblock structure for melting and processing metals.");
        scene.idle(TEXT_TIME);
        scene.idle(STEP_GAP);

        Selection footprint = util.select().fromTo(7, 2, 7, 9, 2, 9);
        scene.overlay().showOutline(PonderPalette.GREEN, new Object(), footprint, 40);
        scene.overlay().showText(40)
            .pointAt(util.vector().topOf(8, 2, 8))
            .placeNearTarget()
            .attachKeyFrame()
            .text("Its footprint can be at most 3x3 blocks...");
        scene.idle(40);

        AABB fullFurnace = new AABB(7, 2, 7, 10, 11, 10);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, new Object(), fullFurnace, TEXT_TIME);
        scene.overlay().showText(TEXT_TIME)
            .pointAt(util.vector().blockSurface(new BlockPos(9, 8, 8), Direction.EAST))
            .placeNearTarget()
            .text("...and its height cannot exceed three times the side length of its base.");
        scene.idle(TEXT_TIME);
        scene.idle(STEP_GAP);

        scene.overlay().showOutline(PonderPalette.RED, new Object(), parts.burners(), TEXT_TIME);
        scene.overlay().showText(TEXT_TIME)
            .pointAt(util.vector().topOf(8, 1, 8))
            .placeNearTarget()
            .attachKeyFrame()
            .text("Every heat source beneath it must reach the required heat level before a matching recipe can be processed.");
        scene.idle(TEXT_TIME);
        scene.idle(STEP_GAP);

        BlockPos wrenchTarget = new BlockPos(9, 5, 8);
        scene.overlay().showControls(util.vector().blockSurface(wrenchTarget, Direction.EAST), Pointing.LEFT, 40)
            .rightClick()
            .withItem(AllItems.WRENCH.asStack());
        scene.overlay().showText(TEXT_TIME)
            .pointAt(util.vector().blockSurface(wrenchTarget, Direction.EAST))
            .placeNearTarget()
            .attachKeyFrame()
            .text("Use a Wrench to open the observation windows.");
        scene.idle(20);
        openWindowLayer(scene, util, 3, IndustrialBlastFurnaceBlock.WindowConnection.BOTTOM);
        for (int y = 4; y <= 8; y++) {
            scene.idle(4);
            openWindowLayer(scene, util, y, IndustrialBlastFurnaceBlock.WindowConnection.MIDDLE);
        }
        scene.idle(4);
        openWindowLayer(scene, util, 9, IndustrialBlastFurnaceBlock.WindowConnection.TOP);
        scene.idle(36);
        scene.idle(STEP_GAP);
    }

    public static void itemInput(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = createScene(builder, "industrial_blast_furnace_item_input",
            "Supplying Items to an Industrial Blast Furnace");
        SceneParts parts = sceneParts(util);

        showExistingAssembly(scene, parts, false, false);
        scene.world().showSection(parts.itemInput(), Direction.WEST);
        scene.idle(20);

        ItemStack copper = AllItems.CRUSHED_COPPER.asStack(18);
        BlockPos firstBelt = new BlockPos(11, 1, 1);
        scene.overlay().showText(40)
            .pointAt(util.vector().topOf(CHUTE))
            .placeNearTarget()
            .attachKeyFrame()
            .text("Ingredients must be supplied through the top of the furnace...");
        scene.overlay().showControls(util.vector().topOf(firstBelt.above()), Pointing.DOWN, 30)
            .withItem(copper);
        scene.world().createItemOnBelt(firstBelt, Direction.UP, copper);
        scene.idle(40);

        Vec3 zincSpawn = util.vector().centerOf(CHUTE.north().above(3));
        scene.overlay().showText(TEXT_TIME)
            .pointAt(zincSpawn.add(0, -1, 0))
            .placeNearTarget()
            .text("...or dropped directly in from above.");
        for (int i = 0; i < 6; i++) {
            ItemStack zincBatch = AllItems.CRUSHED_ZINC.asStack(3);
            ElementLink<EntityElement> droppedZinc = scene.world().createItemEntity(
                zincSpawn, util.vector().of(0, -0.18, 0), zincBatch);
            scene.idle(10);
            scene.world().modifyEntity(droppedZinc, Entity::discard);
        }
        scene.idle(20);

        setFurnaceItems(scene, util.select().fromTo(7, 3, 7, 9, 4, 9), AllItems.CRUSHED_COPPER.asStack());
        setFurnaceItems(scene, util.select().fromTo(7, 5, 7, 9, 6, 9), AllItems.CRUSHED_ZINC.asStack());
        scene.effects().indicateSuccess(CHUTE);
        scene.idle(STEP_GAP);
    }

    public static void processing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = createScene(builder, "industrial_blast_furnace_processing",
            "Processing Metals in an Industrial Blast Furnace");
        SceneParts parts = sceneParts(util);
        Selection copperItems = util.select().fromTo(7, 3, 7, 9, 4, 9);
        Selection zincItems = util.select().fromTo(7, 5, 7, 9, 6, 9);

        setFurnaceItems(scene, copperItems, AllItems.CRUSHED_COPPER.asStack());
        setFurnaceItems(scene, zincItems, AllItems.CRUSHED_ZINC.asStack());
        showExistingAssembly(scene, parts, true, false);
        scene.world().showSection(parts.fluidInput(), Direction.WEST);
        scene.idle(20);

        scene.overlay().showOutline(PonderPalette.BLUE, new Object(), parts.fluidTanks(), TEXT_TIME);
        scene.overlay().showText(TEXT_TIME)
            .pointAt(util.vector().blockSurface(new BlockPos(13, 3, 9), Direction.EAST))
            .placeNearTarget()
            .attachKeyFrame()
            .text("Some recipes require a supply of gas.");
        scene.idle(TEXT_TIME);
        scene.idle(STEP_GAP);

        scene.overlay().showText(TEXT_TIME)
            .pointAt(util.vector().topOf(8, 10, 8).add(0, .75, 0))
            .placeNearTarget()
            .attachKeyFrame()
            .text("Wait for processing...");
        ParticleEmitter smoke = scene.effects().simpleParticleEmitter(
            ParticleTypes.CAMPFIRE_COSY_SMOKE, Vec3.ZERO);
        for (int x = 7; x <= 9; x++) {
            for (int z = 7; z <= 9; z++)
                scene.effects().emitParticles(util.vector().topOf(x, 10, z), smoke, .18f, TEXT_TIME);
        }
        scene.idle(TEXT_TIME);
        scene.idle(STEP_GAP);

        setFurnaceItems(scene, copperItems.add(zincItems), ItemStack.EMPTY);
        replaceFurnaceFluids(scene, FURNACE_CONTROLLER,
            molten(GensoFluids.MoltenMetal.COPPER, THREE_BLOCK_FLUID_DISPLAY),
            molten(GensoFluids.MoltenMetal.ZINC, THREE_BLOCK_FLUID_DISPLAY));
        scene.overlay().showText(TEXT_TIME)
            .pointAt(util.vector().blockSurface(new BlockPos(9, 5, 8), Direction.EAST))
            .placeNearTarget()
            .attachKeyFrame()
            .text("Molten metals are stored inside the furnace.");
        scene.idle(TEXT_TIME);
        scene.idle(STEP_GAP);

        replaceFurnaceFluids(scene, FURNACE_CONTROLLER,
            molten(GensoFluids.MoltenMetal.BRASS, SIX_BLOCK_FLUID_DISPLAY));
        scene.effects().indicateSuccess(new BlockPos(9, 5, 8));
        scene.overlay().showText(TEXT_TIME)
            .pointAt(util.vector().blockSurface(new BlockPos(9, 5, 8), Direction.EAST))
            .placeNearTarget()
            .attachKeyFrame()
            .text("Metals that meet the recipe requirements can be fused into an alloy.");
        scene.idle(TEXT_TIME);
        scene.idle(STEP_GAP);
    }

    public static void output(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = createScene(builder, "industrial_blast_furnace_output",
            "Casting Metals from an Industrial Blast Furnace");
        SceneParts parts = sceneParts(util);
        Selection castingTables = castingTables(util);

        replaceFurnaceFluids(scene, FURNACE_CONTROLLER,
            molten(GensoFluids.MoltenMetal.BRASS, SIX_BLOCK_FLUID_DISPLAY));
        showExistingAssembly(scene, parts, true, true);
        scene.world().showSection(parts.output(), Direction.EAST);
        scene.idle(25);

        scene.overlay().showOutline(PonderPalette.OUTPUT, new Object(), castingTables, 160);
        scene.overlay().showText(160)
            .pointAt(util.vector().blockSurface(new BlockPos(4, 2, 7), Direction.UP))
            .placeNearTarget()
            .attachKeyFrame()
            .text("Use Casting Tables to cast molten metal into ingots.");

        BlockPos outputPump = new BlockPos(6, 2, 8);
        scene.effects().rotationSpeedIndicator(outputPump);
        scene.world().propagatePipeChange(outputPump);
        scene.idle(6);

        List<BlockPos> tablePositions = castingTablePositions();
        for (BlockPos table : tablePositions) {
            fillCastingTable(scene, table, molten(GensoFluids.MoltenMetal.BRASS,
                CastingTableBlockEntity.FLUID_CAPACITY));
            scene.idle(2);
        }
        clearFurnaceFluids(scene, FURNACE_CONTROLLER);
        scene.idle(25);

        setCastingOutputs(scene, castingTables, AllItems.BRASS_INGOT.asStack());
        for (BlockPos table : tablePositions)
            drainCastingTable(scene, table);
        scene.idle(20);

        setCastingOutputs(scene, castingTables, ItemStack.EMPTY);
        for (int z = 4; z <= 12; z++) {
            scene.world().createItemOnBelt(new BlockPos(1, 1, z), Direction.UP,
                AllItems.BRASS_INGOT.asStack());
            if (z != 8) {
                scene.world().createItemOnBelt(new BlockPos(5, 1, z), Direction.UP,
                    AllItems.BRASS_INGOT.asStack());
            }
            scene.idle(4);
        }
        scene.idle(39);
        scene.idle(STEP_GAP);
    }

    private static CreateSceneBuilder createScene(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 17);
        scene.scaleSceneView(.52f);
        scene.setSceneOffsetY(-2.25f);
        return scene;
    }

    private static SceneParts sceneParts(SceneBuildingUtil util) {
        Selection basePlate = util.select().fromTo(1, 0, 1, 15, 0, 15);
        Selection burners = util.select().fromTo(7, 1, 7, 9, 1, 9);
        Selection furnace = util.select().fromTo(7, 1, 7, 9, 10, 9);
        Selection fluidTanks = util.select().fromTo(12, 1, 8, 13, 5, 9);
        Selection fluidInput = fluidTanks.copy()
            .add(util.select().position(12, 6, 8))
            .add(util.select().position(12, 6, 9))
            .add(util.select().fromTo(10, 7, 8, 12, 7, 8))
            .add(util.select().fromTo(12, 7, 9, 16, 7, 9));
        Selection output = util.select().fromTo(0, 1, 2, 5, 2, 16)
            .add(util.select().position(6, 1, 8))
            .add(util.select().position(6, 2, 8))
            .add(util.select().position(0, 0, 12))
            .add(util.select().position(3, 0, 16));
        Selection itemInput = util.select().fromTo(6, 0, 1, 16, 13, 12)
            .substract(basePlate)
            .substract(furnace)
            .substract(fluidInput)
            .substract(output);
        return new SceneParts(basePlate, furnace, burners, itemInput, fluidInput, fluidTanks, output);
    }

    private static void showExistingAssembly(CreateSceneBuilder scene, SceneParts parts,
                                             boolean includeItems, boolean includeFluids) {
        scene.world().showSection(parts.basePlate(), Direction.UP);
        scene.idle(5);
        scene.world().showSection(parts.furnace(), Direction.DOWN);
        scene.idle(10);
        if (includeItems) {
            scene.world().showSection(parts.itemInput(), Direction.WEST);
            scene.idle(10);
        }
        if (includeFluids) {
            scene.world().showSection(parts.fluidInput(), Direction.WEST);
            scene.idle(10);
        }
    }

    private static void openWindowLayer(CreateSceneBuilder scene, SceneBuildingUtil util, int y,
                                        IndustrialBlastFurnaceBlock.WindowConnection connection) {
        Selection sideCenters = util.select().position(8, y, 7)
            .add(util.select().position(9, y, 8))
            .add(util.select().position(8, y, 9))
            .add(util.select().position(7, y, 8));
        scene.world().modifyBlocks(sideCenters, state ->
            state.hasProperty(IndustrialBlastFurnaceBlock.WINDOW)
                ? state.setValue(IndustrialBlastFurnaceBlock.WINDOW, true)
                    .setValue(IndustrialBlastFurnaceBlock.WINDOW_CONNECTION, connection)
                : state, false);
    }

    private static void setFurnaceItems(CreateSceneBuilder scene, Selection selection, ItemStack stack) {
        scene.world().modifyBlockEntityNBT(selection, IndustrialBlastFurnaceBlockEntity.class,
            nbt -> nbt.put("ItemInventory", itemInventoryTag(stack)));
    }

    private static void replaceFurnaceFluids(CreateSceneBuilder scene, BlockPos controller,
                                             FluidStack... fluids) {
        scene.world().modifyBlockEntity(controller, IndustrialBlastFurnaceBlockEntity.class, furnace ->
            {
                IFluidHandler handler = furnace.getFluidInventory();
                clearFluidHandler(handler);
                for (FluidStack fluid : fluids)
                    handler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
            });
    }

    private static void clearFurnaceFluids(CreateSceneBuilder scene, BlockPos controller) {
        scene.world().modifyBlockEntity(controller, IndustrialBlastFurnaceBlockEntity.class, furnace ->
            clearFluidHandler(furnace.getFluidInventory()));
    }

    private static void clearFluidHandler(IFluidHandler handler) {
        while (!handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE).isEmpty()) {
            // The furnace supports several fluid types; drain each leading virtual tank in turn.
        }
    }

    private static FluidStack molten(GensoFluids.MoltenMetal metal, int amount) {
        return new FluidStack((net.minecraft.world.level.material.Fluid) GensoFluids.get(metal).orElseThrow().getSource(), amount);
    }

    private static Selection castingTables(SceneBuildingUtil util) {
        return util.select().fromTo(2, 2, 4, 2, 2, 12)
            .add(util.select().fromTo(4, 2, 4, 4, 2, 7))
            .add(util.select().fromTo(4, 2, 9, 4, 2, 12));
    }

    private static List<BlockPos> castingTablePositions() {
        List<BlockPos> positions = new ArrayList<>(17);
        for (int z = 4; z <= 12; z++) {
            positions.add(new BlockPos(2, 2, z));
            if (z != 8)
                positions.add(new BlockPos(4, 2, z));
        }
        return positions;
    }

    private static void fillCastingTable(CreateSceneBuilder scene, BlockPos position, FluidStack fluid) {
        scene.world().modifyBlockEntity(position, CastingTableBlockEntity.class, table ->
            table.getFluidTank().getPrimaryHandler().fill(fluid, IFluidHandler.FluidAction.EXECUTE));
    }

    private static void drainCastingTable(CreateSceneBuilder scene, BlockPos position) {
        scene.world().modifyBlockEntity(position, CastingTableBlockEntity.class, table ->
            table.getFluidTank().getPrimaryHandler().drain(Integer.MAX_VALUE,
                IFluidHandler.FluidAction.EXECUTE));
    }

    private static void setCastingOutputs(CreateSceneBuilder scene, Selection selection, ItemStack stack) {
        scene.world().modifyBlockEntityNBT(selection, CastingTableBlockEntity.class,
            nbt -> nbt.put("OutputInventory", itemInventoryTag(stack)));
    }

    private static net.minecraft.nbt.CompoundTag itemInventoryTag(ItemStack stack) {
        ItemStackHandler inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, stack.copy());
        return inventory.serializeNBT(net.minecraft.client.Minecraft.getInstance().getConnection().registryAccess());
    }

    private record SceneParts(Selection basePlate, Selection furnace, Selection burners,
                              Selection itemInput, Selection fluidInput, Selection fluidTanks,
                              Selection output) {
    }
}
