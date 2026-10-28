package dev.gensomechanic.mining.content.miner;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.gensomechanic.mining.content.explosive.ExplosiveContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Direct checks for the added upper item access; no vein generation study is involved. */
@GameTestHolder("gensomining_background")
@PrefixGameTestTemplate(false)
public final class MinerTopPortTests {
    private static final BlockPos CENTER = new BlockPos(1, 1, 1);

    private static MechanicalMinerBlockEntity place(GameTestHelper helper) {
        BlockPos main = helper.absolutePos(CENTER);
        helper.assertTrue(MinerStructure.place(helper.getLevel(), main, Direction.NORTH), "Cannot place miner");
        return (MechanicalMinerBlockEntity) helper.getLevel().getBlockEntity(main);
    }

    @GameTest(template = "background_lab", batch = "miner_shapes")
    public static void coarseShellCanBeTargetedFromAboveAndOutsideInEveryFacing(GameTestHelper h) {
        var level = h.getLevel();
        var main = h.absolutePos(new BlockPos(4, 2, 4));
        h.assertTrue(MinerStructure.place(level, main, Direction.NORTH), "Cannot place miner");
        var miner = (MechanicalMinerBlockEntity) level.getBlockEntity(main);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Direction before = MinerStructure.facing(miner.getBlockState());
            if (before != facing) h.assertTrue(MinerStructure.rotate(level, miner.getBlockPos(), before, facing), "Rotation failed");
            for (int part : new int[] {MinerStructure.OUTPUT, MinerStructure.EXPLOSIVE}) {
                var pos = miner.getBlockPos().offset(MinerStructure.offset(part, facing));
                var center = Vec3.atCenterOf(pos);
                var top = level.clip(new net.minecraft.world.level.ClipContext(center.add(0, 2, 0), center.add(0, -.5, 0),
                        net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE,
                        net.minecraft.world.phys.shapes.CollisionContext.empty()));
                h.assertTrue(top.getBlockPos().equals(pos) && top.getDirection() == Direction.UP
                        && Math.abs(top.getLocation().y - pos.getY() - 14.0 / 16) < .0001,
                        "Top targeting missed the port shell: " + facing + "/" + part);
                var outward = Vec3.atLowerCornerOf((part == MinerStructure.OUTPUT ? facing : facing.getOpposite()).getNormal());
                var side = level.clip(new net.minecraft.world.level.ClipContext(center.add(outward.scale(2)), center,
                        net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE,
                        net.minecraft.world.phys.shapes.CollisionContext.empty()));
                h.assertTrue(side.getBlockPos().equals(pos) && side.getLocation().distanceTo(center.add(outward.scale(.5))) < .0001,
                        "Side targeting missed port " + facing + "/" + part + ": " + side.getBlockPos()
                                + " at " + side.getLocation() + ", expected " + center.add(outward.scale(.5)));
                h.assertTrue(level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(),
                        "Invisible lower tray still blocks movement");
                h.assertTrue(level.getBlockState(pos).getCollisionShape(level, pos).bounds().maxY == 14.0 / 16,
                        "Collision and selected port shell differ");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void topInputSharesSideInventoryAndLeaseRules(GameTestHelper helper) {
        var level = helper.getLevel();
        var miner = place(helper);
        IItemHandler previousTop = null;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Direction before = MinerStructure.facing(miner.getBlockState());
            if (before != facing) {
                helper.assertTrue(MinerStructure.rotate(level, miner.getBlockPos(), before, facing), "Rotation failed");
                if (previousTop != null) helper.assertTrue(previousTop.insertItem(0, ExplosiveContent.NITRO_EXPLOSIVE.asStack(), false).getCount() == 1,
                        "Old upper handler survived rotation");
            }
            BlockPos input = miner.getBlockPos().offset(MinerStructure.offset(MinerStructure.EXPLOSIVE, facing));
            BlockPos output = miner.getBlockPos().offset(MinerStructure.offset(MinerStructure.OUTPUT, facing));
            for (Direction access : Direction.values()) {
                helper.assertTrue((level.getCapability(Capabilities.ItemHandler.BLOCK, input, access) != null)
                        == (access == Direction.UP || access == facing.getOpposite()), "Incorrect input face " + facing + "/" + access);
                helper.assertTrue((level.getCapability(Capabilities.ItemHandler.BLOCK, output, access) != null)
                        == (access == Direction.UP || access == facing), "Incorrect output face " + facing + "/" + access);
            }
            helper.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, input, null) == null
                    && level.getCapability(Capabilities.ItemHandler.BLOCK, output, null) == null, "Unsided inventory access leaked");
            var top = level.getCapability(Capabilities.ItemHandler.BLOCK, input, Direction.UP);
            var side = level.getCapability(Capabilities.ItemHandler.BLOCK, input, facing.getOpposite());
            helper.assertTrue(top.insertItem(0, ExplosiveContent.NITRO_EXPLOSIVE.asStack(3), true).isEmpty()
                    && miner.explosiveStack().isEmpty(), "Top insertion simulation changed inventory");
            helper.assertTrue(top.insertItem(0, ExplosiveContent.NITRO_EXPLOSIVE.asStack(3), false).isEmpty()
                    && side.getStackInSlot(0).getCount() == 3, "Top and side do not share input inventory");
            helper.assertTrue(top.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1, "Top accepted an invalid explosive");
            helper.assertTrue(side.extractItem(0, 1, true).getCount() == 1 && top.getStackInSlot(0).getCount() == 3,
                    "Input extraction simulation changed inventory");
            helper.assertTrue(top.extractItem(0, 2, false).getCount() == 2 && side.getStackInSlot(0).getCount() == 1,
                    "Top input extraction differs from side");

            var direct = BlockEntityBehaviour.get(level, input, DirectBeltInputBehaviour.TYPE);
            helper.assertTrue(direct != null && direct.canInsertFromSide(Direction.DOWN) && !direct.canInsertFromSide(Direction.UP),
                    "Vertical direct transfer direction is incorrect");
            helper.assertTrue(direct.handleInsertion(ExplosiveContent.NITRO_EXPLOSIVE.asStack(), Direction.DOWN, true).isEmpty()
                    && side.getStackInSlot(0).getCount() == 1, "Direct transfer simulation changed inventory");
            helper.assertTrue(direct.handleInsertion(ExplosiveContent.NITRO_EXPLOSIVE.asStack(), Direction.DOWN, false).isEmpty()
                    && top.getStackInSlot(0).getCount() == 2, "Downward direct transfer failed");
            var outputDirect = BlockEntityBehaviour.get(level, output, DirectBeltInputBehaviour.TYPE);
            helper.assertTrue(!outputDirect.canInsertFromSide(Direction.DOWN)
                    && outputDirect.handleInsertion(ExplosiveContent.NITRO_EXPLOSIVE.asStack(), Direction.DOWN, false).getCount() == 1,
                    "Output became an insertion port");

            miner.revokePorts();
            helper.assertTrue(top.insertItem(0, ExplosiveContent.NITRO_EXPLOSIVE.asStack(), false).getCount() == 1
                    && side.extractItem(0, 1, false).isEmpty(), "Revoked item handlers remained active");
            previousTop = level.getCapability(Capabilities.ItemHandler.BLOCK, input, Direction.UP);
            helper.assertTrue(previousTop.extractItem(0, 64, false).getCount() == 2, "Fresh top handler lost input contents");
        }
        var retained = previousTop;
        level.destroyBlock(miner.getBlockPos(), true);
        helper.assertTrue(retained.insertItem(0, ExplosiveContent.NITRO_EXPLOSIVE.asStack(), false).getCount() == 1,
                "Top item handler survived dismantling");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void topOutputPreservesTrayLockAndManualCollection(GameTestHelper helper) {
        var level = helper.getLevel();
        var miner = place(helper);
        var inventory = new ItemStackHandler(9);
        inventory.setStackInSlot(0, new ItemStack(Items.ANDESITE, 12));
        inventory.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 7));
        var saved = miner.saveWithoutMetadata(level.registryAccess());
        saved.put("Output", inventory.serializeNBT(level.registryAccess()));
        saved.getCompound("Presentation").putLong("OutputStart", miner.animationTime());
        miner.loadWithComponents(saved, level.registryAccess());
        BlockPos output = miner.getBlockPos().offset(MinerStructure.offset(MinerStructure.OUTPUT, Direction.NORTH));
        var top = level.getCapability(Capabilities.ItemHandler.BLOCK, output, Direction.UP);
        helper.assertTrue(top.extractItem(0, 1, false).isEmpty(), "Upper output bypassed tray lock");
        helper.runAtTickTime(21, () -> {
            var unlockedTop = level.getCapability(Capabilities.ItemHandler.BLOCK, output, Direction.UP);
            var side = level.getCapability(Capabilities.ItemHandler.BLOCK, output, Direction.NORTH);
            helper.assertTrue(unlockedTop.insertItem(0, new ItemStack(Items.ANDESITE), false).getCount() == 1
                    && !unlockedTop.isItemValid(0, new ItemStack(Items.ANDESITE)), "Upper output accepted insertion");
            helper.assertTrue(unlockedTop.extractItem(0, 5, true).getCount() == 5 && side.getStackInSlot(0).getCount() == 12,
                    "Upper output simulation changed inventory");
            helper.assertTrue(unlockedTop.extractItem(0, 5, false).getCount() == 5 && side.extractItem(0, 64, false).getCount() == 7,
                    "Top and side do not share output inventory");
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            level.getBlockState(output).useWithoutItem(level, player,
                    new BlockHitResult(Vec3.atCenterOf(output), Direction.UP, output, false));
            helper.assertTrue(miner.outputInSlot(1).isEmpty() && player.getInventory().countItem(Items.COBBLESTONE) == 7,
                    "Top empty-hand collection lost or duplicated output");
            helper.succeed();
        });
    }
}
