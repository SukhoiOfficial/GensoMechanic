package dev.gensomechanic.mining;

import dev.gensomechanic.mining.content.miner.*;
import dev.gensomechanic.mining.content.explosive.ExplosiveContent;
import dev.gensomechanic.mining.content.vein.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder(GensoMining.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiningMachineTests {
    private static final BlockPos CENTER = new BlockPos(1, 1, 1);
    private static MechanicalMinerBlockEntity place(GameTestHelper h, Direction facing) {
        BlockPos pos = h.absolutePos(CENTER);
        h.assertTrue(MinerStructure.place(h.getLevel(), pos, facing), "Cannot place complete miner");
        return (MechanicalMinerBlockEntity) h.getLevel().getBlockEntity(pos);
    }

    @GameTest(template = "empty")
    public static void upperTankSharesInventoryAndRevokesAllLeases(GameTestHelper h) {
        var level = h.getLevel();
        var miner = place(h, Direction.NORTH);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Direction previous = MinerStructure.facing(miner.getBlockState());
            if (previous != facing) h.assertTrue(MinerStructure.rotate(level, miner.getBlockPos(), previous, facing), "Rotation failed");
            BlockPos upper = miner.getBlockPos().offset(MinerStructure.offset(MinerStructure.LUBRICANT_TANK, facing));
            BlockPos lower = miner.getBlockPos().offset(MinerStructure.offset(MinerStructure.LUBRICANT, facing));
            for (Direction side : Direction.values()) {
                h.assertTrue((level.getCapability(Capabilities.FluidHandler.BLOCK, upper, side) != null)
                        == (side == Direction.UP || side == facing.getClockWise()), "Wrong upper tank face: " + facing + "/" + side);
                h.assertTrue((level.getCapability(Capabilities.FluidHandler.BLOCK, lower, side) != null)
                        == (side == facing.getClockWise()), "Lower inlet face changed");
                h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, upper, side) == null, "Fluid tank exposes item inventory");
            }
            h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, upper, null) == null, "Unsided fluid access leaked");
            var top = level.getCapability(Capabilities.FluidHandler.BLOCK, upper, Direction.UP);
            var side = level.getCapability(Capabilities.FluidHandler.BLOCK, upper, facing.getClockWise());
            var bottom = level.getCapability(Capabilities.FluidHandler.BLOCK, lower, facing.getClockWise());
            h.assertTrue(top.fill(new FluidStack(Fluids.WATER, 600), FluidAction.SIMULATE) == 600 && miner.lubricantFluid().isEmpty(), "Simulation modified inventory");
            h.assertTrue(top.fill(new FluidStack(Fluids.WATER, 600), FluidAction.EXECUTE) == 600, "Top inlet cannot fill");
            h.assertTrue(side.fill(new FluidStack(Fluids.WATER, 600), FluidAction.EXECUTE) == 400, "Side inlet has extra capacity");
            h.assertTrue(bottom.getFluidInTank(0).getAmount() == 1000 && bottom.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0, "Lower/upper inventories differ");
            h.assertTrue(top.fill(new FluidStack(Fluids.LAVA, 100), FluidAction.EXECUTE) == 0, "Invalid lubricant accepted");
            bottom.drain(1000, FluidAction.EXECUTE);
            miner.revokePorts();
            h.assertTrue(top.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0
                    && side.drain(1, FluidAction.EXECUTE).isEmpty(), "Old tank lease survived revocation");
        }
        var facing = MinerStructure.facing(miner.getBlockState());
        var upper = miner.getBlockPos().offset(MinerStructure.offset(MinerStructure.LUBRICANT_TANK, facing));
        var old = level.getCapability(Capabilities.FluidHandler.BLOCK, upper, Direction.UP);
        h.assertTrue(MinerStructure.rotate(level, miner.getBlockPos(), facing, facing.getClockWise()), "Final rotation failed");
        h.assertTrue(old.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0, "Tank lease survived orientation change");
        var current = level.getCapability(Capabilities.FluidHandler.BLOCK,
                miner.getBlockPos().offset(MinerStructure.offset(MinerStructure.LUBRICANT_TANK, facing.getClockWise())), Direction.UP);
        miner.onChunkUnloaded(); miner.setRemoved();
        h.assertTrue(current.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0, "Tank lease survived unload");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void obstructedPlacementLeavesNoPartialMachine(GameTestHelper h) {
        var level = h.getLevel(); var player = h.makeMockPlayer(GameType.SURVIVAL);
        var bottom = h.absolutePos(new BlockPos(1, 0, 1));
        ItemStack stack = MiningContent.MECHANICAL_MINER.asStack(); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        h.setBlock(new BlockPos(0, 1, 0), Blocks.OBSIDIAN);
        var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(bottom), Direction.UP, bottom, false));
        h.assertTrue(((MechanicalMinerItem) stack.getItem()).place(context) == InteractionResult.FAIL && stack.getCount() == 1, "Obstructed placement consumed machine");
        h.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(0, 1, 0));
        h.assertTrue(!(level.getBlockEntity(h.absolutePos(CENTER)) instanceof MechanicalMinerBlockEntity), "Failed placement left controller");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tankRemovalDropsOneMachineAndInventory(GameTestHelper h) {
        var miner = place(h, Direction.EAST);
        miner.insertExplosive(ExplosiveContent.NITRO_EXPLOSIVE.asStack(3), false);
        var upper = miner.getBlockPos().offset(MinerStructure.offset(MinerStructure.LUBRICANT_TANK, Direction.EAST));
        var lease = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, upper, Direction.UP);
        h.getLevel().destroyBlock(upper, true);
        for (int i = 0; i < 18; i++) h.assertTrue(h.getLevel().getBlockState(miner.getBlockPos().offset(MinerStructure.offset(i, Direction.EAST))).isAir(), "Orphaned cell " + i);
        h.assertItemEntityCountIs(MiningContent.MECHANICAL_MINER.asItem(), CENTER, 3, 1);
        h.assertItemEntityCountIs(ExplosiveContent.NITRO_EXPLOSIVE.asItem(), CENTER, 3, 3);
        h.assertTrue(lease.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0, "Dismantled lease remained live");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void trayCollectionUsesAnimationLockAndOnlyOutputPort(GameTestHelper h) {
        var miner = place(h, Direction.NORTH);
        var inventory = new ItemStackHandler(9);
        inventory.setStackInSlot(0, new ItemStack(Items.ANDESITE, 12));
        inventory.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 7));
        var saved = miner.saveWithoutMetadata(h.getLevel().registryAccess());
        saved.put("Output", inventory.serializeNBT(h.getLevel().registryAccess()));
        saved.getCompound("Presentation").putLong("OutputStart", miner.animationTime());
        miner.loadWithComponents(saved, h.getLevel().registryAccess());
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var output = miner.getBlockPos().offset(MinerStructure.offset(MinerStructure.OUTPUT, Direction.NORTH));
        var upper = miner.getBlockPos().offset(MinerStructure.offset(MinerStructure.LUBRICANT_TANK, Direction.NORTH));
        h.getLevel().getBlockState(output).useWithoutItem(h.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(output), Direction.UP, output, false));
        h.assertTrue(miner.outputInSlot(0).getCount() == 12, "Click bypassed tray animation lock");
        h.runAtTickTime(21, () -> {
            h.getLevel().getBlockState(upper).useWithoutItem(h.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(upper), Direction.UP, upper, false));
            h.assertTrue(miner.outputInSlot(0).getCount() == 12, "Tank click extracted output");
            h.getLevel().getBlockState(output).useWithoutItem(h.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(output), Direction.UP, output, false));
            h.assertTrue(miner.outputInSlot(0).isEmpty() && miner.outputInSlot(1).isEmpty(), "Output click did not collect all kinds");
            h.assertTrue(player.getInventory().countItem(Items.ANDESITE) == 12 && player.getInventory().countItem(Items.COBBLESTONE) == 7, "Output was lost or duplicated");
            h.succeed();
        });
    }

    /** Only isolates machine payment/persistence from generation; no production planner is copied. */
    @SuppressWarnings("unchecked")
    private static void bindCycleFixture(GameTestHelper h, BlockPos pos) {
        try {
            var query = VeinManager.query(h.getLevel());
            var cacheField = VeinQuery.class.getDeclaredField("cache"); cacheField.setAccessible(true);
            var cache = (Map<Long, Optional<VeinGenerator.Occurrence>>) cacheField.get(query);
            var definition = VeinManager.definitions(h.getLevel().getServer()).get(GensoMining.asResource("andesite_layer"));
            var chunk = new ChunkPos(pos);
            var identity = new VeinGenerator.Identity(h.getLevel().dimension().location(), definition.id(), chunk);
            var weight = VeinQuery.class.getDeclaredField("memberWeight"); weight.setAccessible(true);
            weight.setInt(query, weight.getInt(query) + 1 - cache.getOrDefault(chunk.toLong(), Optional.empty())
                    .map(v -> v.chunks().size()).orElse(cache.containsKey(chunk.toLong()) ? 1 : 0));
            cache.put(chunk.toLong(), Optional.of(new VeinGenerator.Occurrence(definition, 100000, identity, List.of(chunk), "machine-cycle-fixture")));
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void paidCycleRetainsEffectsAcrossSaveAndCompletesOnce(GameTestHelper h) {
        var miner = place(h, Direction.NORTH); bindCycleFixture(h, miner.getBlockPos());
        var operation = MiningRecipes.forServer(h.getLevel().getServer()).get(GensoMining.asResource("andesite_layer")).value()
                .modifiers().select(new FluidStack(Fluids.WATER, 100), ExplosiveContent.NITRO_EXPLOSIVE.asStack());
        h.getLevel().setBlock(miner.getBlockPos().above(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.DOWN), 3);
        var motor = (com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) h.getLevel().getBlockEntity(miner.getBlockPos().above());
        miner.fillLubricant(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE); miner.insertExplosive(ExplosiveContent.NITRO_EXPLOSIVE.asStack(), false);
        h.runAtTickTime(3, () -> motor.generatedSpeed.setValue(256));
        h.runAtTickTime(8, () -> {
            h.assertTrue(miner.cycleStarted() && miner.progress() > 0, "Paid cycle did not start");
            h.assertTrue(miner.lubricantFluid().isEmpty() && miner.explosiveStack().isEmpty(), "Payment was not consumed once");
            motor.generatedSpeed.setValue(0); miner.setSpeed(0);
            var saved = miner.saveWithoutMetadata(h.getLevel().registryAccess()); double progress = miner.progress();
            miner.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(miner.cycleStarted() && miner.progress() == progress, "Save lost paid cycle");
        });
        h.runAtTickTime(12, () -> {
            h.assertTrue(miner.miningDuration() == operation.duration() && miner.calculateStressApplied() == operation.stress(), "Empty inventory removed paid effects");
            motor.generatedSpeed.setValue(256);
        });
        h.runAtTickTime(13, () -> h.succeedWhen(() -> {
            int count = 0; for (int i = 0; i < 9; i++) count += miner.outputInSlot(i).getCount();
            h.assertTrue(count > 0, "Cycle has not produced output");
            h.assertTrue(count <= operation.outputs().stream().mapToInt(o -> o.stack().getCount()).sum(), "Cycle duplicated output");
            motor.generatedSpeed.setValue(0); miner.setSpeed(0);
            h.assertTrue(miner.lubricantFluid().isEmpty() && miner.explosiveStack().isEmpty(), "Paid inputs resurrected");
        }));
    }
}
