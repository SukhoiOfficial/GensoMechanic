package dev.gensomechanic.mining.content.vein;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.content.explosive.ExplosiveContent;
import dev.gensomechanic.mining.content.miner.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Machine integration with fixed query answers, not a vein distribution simulation. */
@GameTestHolder("gensomining_background")
@PrefixGameTestTemplate(false)
public final class RockBackgroundMachineTests {
    private static MechanicalMinerBlockEntity place(GameTestHelper h, BlockPos pos) {
        h.assertTrue(MinerStructure.place(h.getLevel(), pos, Direction.NORTH), "Cannot place miner");
        return (MechanicalMinerBlockEntity) h.getLevel().getBlockEntity(pos);
    }
    private static CreativeMotorBlockEntity motor(GameTestHelper h, MechanicalMinerBlockEntity miner) {
        h.getLevel().setBlock(miner.getBlockPos().above(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.DOWN), 3);
        var motor = (CreativeMotorBlockEntity) h.getLevel().getBlockEntity(miner.getBlockPos().above());
        motor.generatedSpeed.setValue(0);
        return motor;
    }
    /** Controlled query answer and bookkeeping only; production target matching remains unchanged. */
    @SuppressWarnings("unchecked")
    private static void answer(GameTestHelper h, MechanicalMinerBlockEntity miner, Optional<VeinGenerator.Occurrence> value) {
        try {
            var query = VeinManager.query(h.getLevel());
            var field = VeinQuery.class.getDeclaredField("cache"); field.setAccessible(true);
            var cache = (Map<Long, Optional<VeinGenerator.Occurrence>>) field.get(query);
            var chunk = new ChunkPos(miner.getBlockPos());
            var weight = VeinQuery.class.getDeclaredField("memberWeight"); weight.setAccessible(true);
            int old = cache.containsKey(chunk.toLong()) ? cache.get(chunk.toLong()).map(v -> v.chunks().size()).orElse(1) : 0;
            int next = value == null ? 0 : value.map(v -> v.chunks().size()).orElse(1);
            weight.setInt(query, weight.getInt(query) - old + next);
            if (value == null) cache.remove(chunk.toLong()); else cache.put(chunk.toLong(), value);
            var saved = miner.saveWithoutMetadata(h.getLevel().registryAccess());
            miner.loadWithComponents(saved, h.getLevel().registryAccess());
            var planned = MechanicalMinerBlockEntity.class.getDeclaredField("plannedQuery"); planned.setAccessible(true); planned.set(miner, null);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void inventory(GameTestHelper h, MechanicalMinerBlockEntity miner, boolean full) {
        var items = new ItemStackHandler(9);
        if (full) items.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
        var saved = miner.saveWithoutMetadata(h.getLevel().registryAccess());
        saved.put("Output", items.serializeNBT(h.getLevel().registryAccess()));
        miner.loadWithComponents(saved, h.getLevel().registryAccess());
    }

    @GameTest(template = "background_lab", batch = "background_matching")
    public static void pendingQueriesAndRealVeinsNeverFallBack(GameTestHelper h) {
        var miner = place(h, h.absolutePos(new BlockPos(3, 2, 3)));
        answer(h, miner, null); miner.tick();
        h.assertTrue(miner.status() == MechanicalMinerBlockEntity.Status.INCOMPLETE && !miner.rubbleVisible()
                && !miner.cycleStarted(), "Pending query activated background mining");
        answer(h, miner, Optional.empty()); miner.tick();
        h.assertTrue(miner.rubbleVisible() && miner.displayState().is(Blocks.STONE) && miner.miningDuration() == 6400,
                "Confirmed empty chunk did not select rock layer preview");
        var chunk = new ChunkPos(miner.getBlockPos());
        var definition = VeinManager.definitions(h.getLevel().getServer()).get(GensoMining.asResource("bauxite_vein"));
        var occurrence = new VeinGenerator.Occurrence(definition, 4,
                new VeinGenerator.Identity(h.getLevel().dimension().location(), definition.id(), chunk), List.of(chunk), "real-vein-fixture");
        answer(h, miner, Optional.of(occurrence)); miner.tick();
        h.assertTrue(miner.miningDuration() == 3200 && !miner.displayState().is(Blocks.STONE)
                && !MiningTarget.resolve(h.getLevel(), miner.getBlockPos(), Optional.of(occurrence)).background(), "Real vein lost priority");
        var missing = new VeinDefinition(GensoMining.asResource("unconfigured_test_vein"), definition.name(), definition.icon(),
                definition.dimensions(), definition.biomes(), definition.biomeFraction(), definition.memberFraction(),
                definition.capacities(), definition.totalWeight(), null, definition.mapColor(), definition.sizes(), definition.shape());
        var withoutRecipe = new VeinGenerator.Occurrence(missing, 4, occurrence.identity(), List.of(chunk), "no-recipe");
        var target = MiningTarget.resolve(h.getLevel(), miner.getBlockPos(), Optional.of(withoutRecipe));
        h.assertTrue(target != null && !target.background() && MiningRecipes.forServer(h.getLevel().getServer()).get(target.id()) == null,
                "Real vein without recipe selected background");
        var end = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
        h.assertTrue(MiningTarget.resolve(end, miner.getBlockPos(), Optional.empty()) == null, "Background leaked into End");
        h.succeed();
    }

    @GameTest(template = "background_lab", batch = "background_capacity", timeoutTicks = 80)
    public static void activeMachinesShareChunkCapacityAndBlockedMachineReleasesIt(GameTestHelper h) {
        var origin = h.absolutePos(BlockPos.ZERO);
        int x = Math.floorDiv(origin.getX() + 15, 16) * 16 + 3;
        var first = place(h, new BlockPos(x, origin.getY() + 2, origin.getZ() + 3));
        var second = place(h, first.getBlockPos().east(6));
        h.assertTrue(new ChunkPos(first.getBlockPos()).equals(new ChunkPos(second.getBlockPos())), "Fixture machines cross chunk boundary");
        answer(h, first, Optional.empty()); answer(h, second, Optional.empty());
        var a = motor(h, first); var b = motor(h, second);
        h.runAtTickTime(3, () -> { a.generatedSpeed.setValue(256); b.generatedSpeed.setValue(256); });
        h.runAtTickTime(8, () -> {
            h.assertTrue(first.cycleStarted() && second.cycleStarted() && first.efficiency() == 1 && second.efficiency() == 1,
                    "Two background miners were throttled below the four-capacity limit");
            var target = MiningTarget.resolve(h.getLevel(), first.getBlockPos(), Optional.empty());
            var scan = new VeinScan(new ChunkPos(first.getBlockPos()), Optional.empty(), List.of());
            h.assertTrue(ScannerBackgroundReportTests.capacity(ScannerReport.create(h.getLevel(), first.getBlockPos(), scan, 0))
                    .orElseThrow().equals("2 / 4"), "Scanner did not use the active background capacity pool");
            var otherChunk = MiningTarget.resolve(h.getLevel(), first.getBlockPos().east(16), Optional.empty());
            h.assertTrue(!target.identity().equals(otherChunk.identity()), "Background capacity pooled across chunks");
            var sameCoordinatesVein = new VeinCapacityTracker.Key(false, target.identity().dimension(), target.id(), target.identity().centre());
            h.assertTrue(!target.identity().equals(sameCoordinatesVein), "Background capacity shares real-vein identity");
            try {
                var field = MechanicalMinerBlockEntity.class.getDeclaredField("output"); field.setAccessible(true);
                ((ItemStackHandler) field.get(second)).setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        h.runAtTickTime(12, () -> {
            h.assertTrue(second.status() == MechanicalMinerBlockEntity.Status.OUTPUT_FULL && first.efficiency() == 1,
                    "Output-blocked miner still occupies background capacity: " + second.status() + ", efficiency=" + first.efficiency());
            var scan = new VeinScan(new ChunkPos(first.getBlockPos()), Optional.empty(), List.of());
            h.assertTrue(ScannerBackgroundReportTests.capacity(ScannerReport.create(h.getLevel(), first.getBlockPos(), scan, 0))
                    .orElseThrow().equals("1 / 4"), "Scanner retained a blocked miner's occupied capacity");
            a.generatedSpeed.setValue(0); b.generatedSpeed.setValue(0); h.succeed();
        });
    }

    @GameTest(template = "background_lab", batch = "background_cycle", timeoutTicks = 1000)
    public static void paidBackgroundCycleSurvivesSaveReloadAndOutputBlockage(GameTestHelper h) {
        var miner = place(h, h.absolutePos(new BlockPos(3, 2, 3))); answer(h, miner, Optional.empty());
        var motor = motor(h, miner);
        miner.fillLubricant(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE);
        miner.insertExplosive(ExplosiveContent.NITRO_EXPLOSIVE.asStack(), false);
        h.runAtTickTime(3, () -> motor.generatedSpeed.setValue(256));
        h.runAtTickTime(8, () -> {
            h.assertTrue(miner.cycleStarted() && miner.progress() > 0 && miner.miningDuration() == 4800,
                    "Lubricated background cycle did not start");
            h.assertTrue(miner.lubricantFluid().isEmpty() && miner.explosiveStack().getCount() == 1,
                    "Background payment repeated or consumed unconfigured explosive");
            double progress = miner.progress();
            inventory(h, miner, true);
            h.runAfterDelay(4, () -> {
                h.assertTrue(miner.progress() == progress && miner.cycleStarted(), "Blocked saved cycle changed progress/payment");
                motor.generatedSpeed.setValue(0); miner.setSpeed(0); inventory(h, miner, false);
                var server = h.getLevel().getServer();
                var reloaded = server.reloadResources(server.getPackRepository().getSelectedIds()).thenRunAsync(() -> {
                    h.assertTrue(miner.cycleStarted() && miner.progress() == progress, "Reload discarded paid background work");
                    motor.generatedSpeed.setValue(256);
                }, server);
                h.startSequence().thenWaitUntil(() -> h.assertTrue(reloaded.isDone(), "Waiting for resource reload"))
                        .thenExecute(reloaded::join)
                        .thenWaitUntil(() -> h.assertTrue(miner.outputInSlot(0).is(Items.COBBLESTONE), "Waiting for background output"))
                        .thenExecute(() -> {
                            int cobble = 0, andesite = 0;
                            for (int slot = 0; slot < 9; slot++) {
                                var stack = miner.outputInSlot(slot);
                                if (stack.is(Items.COBBLESTONE)) cobble += stack.getCount();
                                else if (stack.is(Items.ANDESITE)) andesite += stack.getCount();
                                else h.assertTrue(stack.isEmpty(), "Unexpected background output");
                            }
                            h.assertTrue(cobble == 2 && andesite <= 1 && miner.explosiveStack().getCount() == 1 && miner.lubricantFluid().isEmpty(),
                                    "Saved/reloaded cycle duplicated output or consumed inputs again");
                            motor.generatedSpeed.setValue(0); miner.setSpeed(0);
                        }).thenSucceed();
            });
        });
    }
}
