package dev.gensomechanic.metallurgy.content.metallurgy;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import dev.gensomechanic.metallurgy.*;
import dev.gensomechanic.metallurgy.content.casting.*;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.*;

@GameTestHolder(GensoMetallurgy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MetallurgyGameTests {
    @GameTest(template = "metallurgy_lab", batch = "metallurgy")
    public static void burnerRecognizesFurnaceWithoutChangingNativeTargets(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(5, 2, 5));
        h.getLevel().setBlockAndUpdate(pos, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED));
        var burner = (com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity) h.getLevel().getBlockEntity(pos);
        for (var state : List.of(AllBlocks.BASIN.getDefaultState(), AllBlocks.FLUID_TANK.getDefaultState(), MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.getDefaultState())) {
            h.getLevel().setBlockAndUpdate(pos.above(), state);
            h.assertTrue(burner.isValidBlockAbove(), "Burner does not recognize " + state);
        }
        h.getLevel().setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
        h.assertTrue(!burner.isValidBlockAbove(), "Burner now animates under unrelated blocks");
        h.getLevel().removeBlock(pos.above(), false);
        h.assertTrue(!burner.isValidBlockAbove(), "Burner animation survives furnace removal");
        h.succeed();
    }

    private static final BlockPos ORIGIN = new BlockPos(3, 2, 3);
    private static IndustrialBlastFurnaceBlockEntity furnace(GameTestHelper h, int width, int height) {
        BlockPos origin = h.absolutePos(ORIGIN);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++)
            h.getLevel().setBlockAndUpdate(origin.offset(x, y, z), MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.getDefaultState());
        for (int round = 0; round < 3; round++)
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++)
                ((IndustrialBlastFurnaceBlockEntity) h.getLevel().getBlockEntity(origin.offset(x, y, z))).tick();
        return (IndustrialBlastFurnaceBlockEntity) h.getLevel().getBlockEntity(origin);
    }
    private static IItemHandler items(net.minecraft.world.level.block.entity.BlockEntity be) {
        return java.util.Objects.requireNonNull(be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), Direction.NORTH));
    }
    private static IFluidHandler fluids(net.minecraft.world.level.block.entity.BlockEntity be) {
        return java.util.Objects.requireNonNull(be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), Direction.NORTH));
    }
    private static FluidStack molten(GensoFluids.MoltenMetal metal, int amount) {
        return new FluidStack((net.minecraft.world.level.material.Fluid) GensoFluids.get(metal).orElseThrow().getSource(), amount);
    }
    private static void heat(GameTestHelper h, BlockPos pos, BlazeBurnerBlock.HeatLevel heat) {
        h.getLevel().setBlockAndUpdate(pos.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
    }
    private static CastingTableBlockEntity table(GameTestHelper h, Direction facing) {
        BlockPos pos = h.absolutePos(ORIGIN);
        h.getLevel().setBlockAndUpdate(pos, MetallurgyBlocks.CASTING_TABLE.getDefaultState().setValue(CastingTableBlock.FACING, facing));
        return (CastingTableBlockEntity) h.getLevel().getBlockEntity(pos);
    }

    @GameTest(template = "metallurgy_lab", batch = "metallurgy")
    public static void maximumStructureAndOriginalPorts(GameTestHelper h) {
        var main = furnace(h, 3, 9);
        h.assertTrue(main.getWidth() == 3 && main.getHeight() == 9, "Maximum furnace did not connect");
        h.assertTrue(items(main).getSlots() == 81 && fluids(main).getTankCapacity(0) == 81000, "Original capacity changed");
        var top = h.getLevel().getBlockEntity(main.getBlockPos().above(8));
        h.assertTrue(ItemHandlerHelper.insertItemStacked(items(main), new ItemStack(Items.DIAMOND), false).getCount() == 1, "Lower layers accepted input");
        h.assertTrue(ItemHandlerHelper.insertItemStacked(items(top), new ItemStack(Items.DIAMOND, 64), false).isEmpty(), "Top layer rejected items");
        h.assertTrue(items(main).getStackInSlot(0).getCount() == 1 && items(main).getStackInSlot(63).getCount() == 1, "Items must occupy individual slots");
        h.assertTrue(items(main).extractItem(0, 64, false).getCount() == 1, "Lower layer cannot extract");
        for (Direction side : Direction.values()) h.assertTrue(top.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, top.getBlockPos(), side) != null, "Top-layer side missing");
        main.toggleLayerWindows();
        h.assertTrue(!main.getBlockState().getValue(IndustrialBlastFurnaceBlock.WINDOW), "Bottom windows opened");
        var window = (IndustrialBlastFurnaceBlockEntity) h.getLevel().getBlockEntity(main.getBlockPos().offset(1, 4, 0));
        window.toggleLayerWindows();
        h.assertTrue(window.getBlockState().getValue(IndustrialBlastFurnaceBlock.WINDOW), "Window layer did not toggle");
        h.succeed();
    }

    @GameTest(template = "metallurgy_lab", batch = "metallurgy")
    public static void sharedFluidsSimulationAndPersistence(GameTestHelper h) {
        var main = furnace(h, 2, 2); var tank = fluids(main);
        h.assertTrue(main.getTotalBlockCount() == 8, "2x2 structure did not connect");
        h.assertTrue(tank.fill(molten(GensoFluids.MoltenMetal.COPPER, 4500), FluidAction.SIMULATE) == 4500 && tank.getFluidInTank(0).isEmpty(), "Simulation mutated fluid storage");
        h.assertTrue(tank.fill(molten(GensoFluids.MoltenMetal.COPPER, 4500), FluidAction.EXECUTE) == 4500, "Copper fill failed");
        h.assertTrue(tank.fill(molten(GensoFluids.MoltenMetal.ZINC, 5000), FluidAction.EXECUTE) == 3500, "Shared capacity changed");
        List<IndustrialBlastFurnaceBlockEntity> parts = new ArrayList<>();
        List<CompoundTag> saved = new ArrayList<>();
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            var part = (IndustrialBlastFurnaceBlockEntity) h.getLevel().getBlockEntity(main.getBlockPos().offset(x,y,z));
            parts.add(part); saved.add(part.saveWithFullMetadata(h.getLevel().registryAccess()));
        }
        tank.drain(molten(GensoFluids.MoltenMetal.COPPER, 4500), FluidAction.EXECUTE);
        for (int i = 0; i < parts.size(); i++) parts.get(i).loadWithComponents(saved.get(i), h.getLevel().registryAccess());
        h.assertTrue(fluids(main).drain(molten(GensoFluids.MoltenMetal.COPPER, 5000), FluidAction.EXECUTE).getAmount() == 4500, "Copper was lost during NBT round trip");
        h.assertTrue(fluids(main).drain(Integer.MAX_VALUE, FluidAction.EXECUTE).getAmount() == 3500, "Fluid types mixed during drain");
        h.succeed();
    }

    @GameTest(template = "metallurgy_lab", batch = "metallurgy", timeoutTicks = 80)
    public static void nativeHeatAndFixtureSmeltingTime(GameTestHelper h) {
        var main = furnace(h, 1, 1);
        items(main).insertItem(0, new ItemStack(Items.COPPER_INGOT), false);
        for (int i = 0; i < 220; i++) main.tick();
        h.assertTrue(!items(main).getStackInSlot(0).isEmpty() && fluids(main).getFluidInTank(0).isEmpty(), "Unheated furnace processed");
        heat(h, main.getBlockPos(), BlazeBurnerBlock.HeatLevel.KINDLED);
        h.runAfterDelay(1, () -> {
            // Heat probing caches a world tick, so start after the heater's placement tick.
            for (int i = 0; i < 220; i++) main.tick();
            h.assertTrue(items(main).getStackInSlot(0).isEmpty(), "Smelting did not consume ingot");
            h.assertTrue(fluids(main).drain(Integer.MAX_VALUE, FluidAction.SIMULATE).getAmount() == 90, "Fixture output incorrect");
            h.succeed();
        });
    }

    @GameTest(template = "metallurgy_lab", batch = "metallurgy", timeoutTicks = 80)
    public static void alloyingAndBlockedOutput(GameTestHelper h) {
        var main = furnace(h, 1, 1);
        heat(h, main.getBlockPos(), BlazeBurnerBlock.HeatLevel.KINDLED);
        fluids(main).fill(molten(GensoFluids.MoltenMetal.COPPER, 60), FluidAction.EXECUTE);
        fluids(main).fill(molten(GensoFluids.MoltenMetal.ZINC, 60), FluidAction.EXECUTE);
        h.runAfterDelay(1, () -> {
            for (int i = 0; i < 150; i++) main.tick();
            h.assertTrue(fluids(main).drain(molten(GensoFluids.MoltenMetal.BRASS, 1000), FluidAction.EXECUTE).getAmount() == 120, "Fixture fusing transaction incorrect");
            fluids(main).fill(molten(GensoFluids.MoltenMetal.IRON, 1000), FluidAction.EXECUTE);
            items(main).insertItem(0, new ItemStack(Items.COPPER_INGOT), false);
            for (int i = 0; i < 240; i++) main.tick();
            h.assertTrue(items(main).getStackInSlot(0).is(Items.COPPER_INGOT), "Blocked output consumed ingredient");
            fluids(main).drain(1000, FluidAction.EXECUTE);
            for (int i = 0; i < 230; i++) main.tick();
            h.assertTrue(fluids(main).getFluidInTank(0).getAmount() == 90 && items(main).getStackInSlot(0).isEmpty(), "Unblocking did not resume");
            h.succeed();
        });
    }

    @GameTest(template = "metallurgy_lab", batch = "metallurgy", timeoutTicks = 80)
    public static void splitDropsAndRemainingStorage(GameTestHelper h) {
        var main = furnace(h, 1, 3);
        var top = h.getLevel().getBlockEntity(main.getBlockPos().above(2));
        ItemHandlerHelper.insertItemStacked(items(top), new ItemStack(Items.DIAMOND, 3), false);
        fluids(main).fill(molten(GensoFluids.MoltenMetal.IRON, 1500), FluidAction.EXECUTE);
        BlockPos broken = main.getBlockPos().above();
        h.getLevel().destroyBlock(broken, true);
        h.runAfterDelay(4, () -> {
            int fluid = 0, item = 0;
            for (int y : new int[]{0,2}) {
                var part = (IndustrialBlastFurnaceBlockEntity) h.getLevel().getBlockEntity(main.getBlockPos().above(y));
                h.assertTrue(part != null && part.getTotalBlockCount() == 1, "Split retained invalid structure");
                for (int t = 0; t < fluids(part).getTanks(); t++) fluid += fluids(part).getFluidInTank(t).getAmount();
                item += items(part).getStackInSlot(0).getCount();
            }
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(broken).inflate(2));
            int furnaceDrops = drops.stream().filter(e -> e.getItem().is(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.asItem())).mapToInt(e -> e.getItem().getCount()).sum();
            int diamonds = drops.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(fluid == 1500 && item + diamonds == 3 && furnaceDrops == 1, "Split lost/duplicated surviving contents or drops");
            h.succeed();
        });
    }

    @GameTest(template = "metallurgy_lab", batch = "metallurgy")
    public static void castingAllOrientationsAndLocks(GameTestHelper h) {
        for (Direction facing : new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            var table = table(h, facing); var tank = fluids(table);
            h.assertTrue(tank.fill(molten(GensoFluids.MoltenMetal.COPPER, 1000), FluidAction.EXECUTE) == 90, "Casting capacity changed");
            table.tick();
            h.assertTrue(tank.drain(90, FluidAction.EXECUTE).isEmpty(), "Processing fluid drained");
            CompoundTag saved = table.saveWithFullMetadata(h.getLevel().registryAccess()); table.loadWithComponents(saved, h.getLevel().registryAccess());
            for (int i = 0; i < 100; i++) table.tick();
            h.assertTrue(table.getOutput().is(Items.COPPER_INGOT) && table.getOutput().getCount() == 1, "Casting output wrong");
            h.assertTrue(tank.fill(molten(GensoFluids.MoltenMetal.COPPER, 90), FluidAction.EXECUTE) == 0, "Output did not lock fill");
            h.assertTrue(items(table).insertItem(0, new ItemStack(Items.COPPER_INGOT), false).getCount() == 1, "Casting accepts item input");
            h.assertTrue(table.takeOutput().getCount() == 1 && table.takeOutput().isEmpty(), "Manual extraction duplicated output");
            h.getLevel().removeBlock(table.getBlockPos(), false);
        }
        h.succeed();
    }

    @GameTest(template = "metallurgy_lab", batch = "metallurgy")
    public static void nativeFanDoublesCastingRate(GameTestHelper h) {
        var table = table(h, Direction.NORTH);
        BlockPos fanPos = table.getBlockPos().west(2);
        h.getLevel().setBlockAndUpdate(fanPos, AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        var fan = (EncasedFanBlockEntity) h.getLevel().getBlockEntity(fanPos);
        fan.setSpeed(64); fan.getAirCurrent().rebuild();
        fluids(table).fill(molten(GensoFluids.MoltenMetal.COPPER, 90), FluidAction.EXECUTE);
        table.tick();
        for (int i = 0; i < 49; i++) table.tick();
        h.assertTrue(table.getOutput().isEmpty(), "Casting completed earlier than original fan rate");
        table.tick();
        h.assertTrue(table.getOutput().is(Items.COPPER_INGOT), "Fan-assisted casting did not finish at 50 ticks: fan="+fan.getSpeed()+", dir="+fan.getAirCurrent().direction+", range="+fan.getAirCurrent().maxDistance+", bounds="+fan.getAirCurrent().bounds+", table="+table.saveWithFullMetadata(h.getLevel().registryAccess()));
        fan.setSpeed(0); table.tick();
        h.succeed();
    }

    @GameTest(template = "metallurgy_lab", batch = "metallurgy")
    public static void removedFurnaceRejectsResolvedItemHandler(GameTestHelper h) {
        var main = furnace(h, 1, 1);
        var resolved = items(main);
        var oldFluids = fluids(main);
        h.getLevel().removeBlock(main.getBlockPos(), false);
        h.assertTrue(oldFluids.fill(molten(GensoFluids.MoltenMetal.COPPER, 90), FluidAction.EXECUTE) == 0, "Resolved fluid port remained active");
        h.assertTrue(resolved.insertItem(0, new ItemStack(Items.DIAMOND), false).getCount() == 1,
            "Removed furnace still accepts items through its resolved handler");
        h.succeed();
    }

    @GameTest(template = "metallurgy_lab", batch = "metallurgy", timeoutTicks = 100)
    public static void windowsSurviveGrowthAndOnlyNewBottomCloses(GameTestHelper h) {
        var main = furnace(h, 2, 3);
        BlockPos origin = main.getBlockPos();
        ((IndustrialBlastFurnaceBlockEntity) h.getLevel().getBlockEntity(origin.above())).toggleLayerWindows();
        ((IndustrialBlastFurnaceBlockEntity) h.getLevel().getBlockEntity(origin.above(2))).toggleLayerWindows();
        var oldPort = items(h.getLevel().getBlockEntity(origin.above(2)));
        h.assertTrue(h.getLevel().getBlockState(origin.above()).getValue(IndustrialBlastFurnaceBlock.WINDOW), "Initial window toggle failed");
        for (int x=0;x<2;x++) for (int z=0;z<2;z++)
            h.getLevel().setBlockAndUpdate(origin.offset(x,3,z), MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.getDefaultState());
        h.runAfterDelay(4, () -> {
            settleFurnace(h, origin, 2, 4);
            h.assertTrue(oldPort.getSlots() == 0, "Growth retained old top port");
            for (int x=0;x<2;x++) for (int z=0;z<2;z++) {
                h.assertTrue(h.getLevel().getBlockState(origin.offset(x,1,z)).getValue(IndustrialBlastFurnaceBlock.WINDOW), "Growth closed existing lower window layer: " + h.getLevel().getBlockEntity(origin.offset(x,1,z)).saveWithFullMetadata(h.getLevel().registryAccess()));
                h.assertTrue(h.getLevel().getBlockState(origin.offset(x,2,z)).getValue(IndustrialBlastFurnaceBlock.WINDOW), "Growth closed existing upper window layer");
            }
            for (int x=0;x<2;x++) for (int z=0;z<2;z++) {
                h.getLevel().destroyBlock(origin.offset(x,0,z), false);
            }
            h.runAfterDelay(5, () -> {
                settleFurnace(h, origin.above(), 2, 3);
                for (int x=0;x<2;x++) for (int z=0;z<2;z++) {
                    h.assertTrue(!h.getLevel().getBlockState(origin.offset(x,1,z)).getValue(IndustrialBlastFurnaceBlock.WINDOW), "New bottom retained window");
                    h.assertTrue(h.getLevel().getBlockState(origin.offset(x,2,z)).getValue(IndustrialBlastFurnaceBlock.WINDOW), "Bottom removal closed a surviving upper layer");
                }
                h.succeed();
            });
        });
    }

    private static void settleFurnace(GameTestHelper h, BlockPos origin, int width, int height) {
        // GameTest's remote chunks are loaded, but are not guaranteed to tick block entities.
        for (int round = 0; round < 3; round++)
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++)
                ((IndustrialBlastFurnaceBlockEntity) h.getLevel().getBlockEntity(origin.offset(x, y, z))).tick();
    }

    @GameTest(template = "metallurgy_lab", batch = "metallurgy")
    public static void registryAwareReloadAndRetainedPortRevocation(GameTestHelper h) {
        var main = furnace(h, 1, 2);
        var top = h.getLevel().getBlockEntity(main.getBlockPos().above());
        var oldItems = items(top); var oldFluid = fluids(top);
        oldItems.insertItem(1, new ItemStack(Items.DIAMOND), false);
        oldFluid.fill(molten(GensoFluids.MoltenMetal.COPPER, 300), FluidAction.EXECUTE);
        var saved = top.saveWithFullMetadata(h.getLevel().registryAccess());
        var replacement = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(top.getBlockPos(), top.getBlockState(), saved, h.getLevel().registryAccess());
        h.getLevel().removeBlockEntity(top.getBlockPos()); h.getLevel().setBlockEntity(replacement);
        h.assertTrue(oldItems.getSlots()==0 && oldFluid.getTanks()==0, "Old lease survived entity replacement");
        h.assertTrue(items(replacement).getStackInSlot(1).is(Items.DIAMOND), "Registry-aware reload lost item");
        h.assertTrue(fluids(replacement).drain(1000,FluidAction.SIMULATE).getAmount()==300, "Reload changed fluid total");
        h.succeed();
    }

    @GameTest(template = "metallurgy_lab", batch = "metallurgy")
    public static void recipeCountsAndNetworkRoundTrip(GameTestHelper h) {
        var manager=h.getLevel().getRecipeManager();
        net.minecraft.world.item.crafting.RecipeType<BlastFurnaceSmeltingRecipe> smelting=MetallurgyRecipeTypes.BLAST_FURNACE_SMELTING.getType();
        net.minecraft.world.item.crafting.RecipeType<BlastFurnaceFusingRecipe> fusing=MetallurgyRecipeTypes.BLAST_FURNACE_FUSING.getType();
        net.minecraft.world.item.crafting.RecipeType<CastingRecipe> casting=MetallurgyRecipeTypes.CASTING.getType();
        h.assertTrue(manager.getAllRecipesFor(smelting).size() == 1 && manager.getAllRecipesFor(fusing).size() == 1
            && manager.getAllRecipesFor(casting).size() == 1, "Isolated fixtures not loaded");
        h.assertTrue(manager.getRecipes().stream().noneMatch(r -> r.id().getNamespace().equals(GensoMetallurgy.MOD_ID)),
            "Production module must contain no default recipes");
        for (var holder: manager.getAllRecipesFor(casting)) {
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            net.minecraft.world.item.crafting.RecipeSerializer<CastingRecipe> serializer=MetallurgyRecipeTypes.CASTING.getSerializer();
            serializer.streamCodec().encode(buffer,holder.value());
            var decoded=serializer.streamCodec().decode(buffer); buffer.release();
            h.assertTrue(!decoded.getRollableResults().getFirst().getStack().isEmpty(), "Tag output lost over network");
            h.assertTrue(decoded.getFluidIngredients().getFirst().amount()==90 && decoded.getProcessingDuration()==100, "Casting recipe changed over network");
        }
        h.succeed();
    }
}
