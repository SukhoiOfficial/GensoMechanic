package dev.gensomechanic.metallurgy.content.metallurgy;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import dev.gensomechanic.metallurgy.*;
import dev.gensomechanic.metallurgy.content.casting.CastingTableBlockEntity;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("gensometallurgy")
@PrefixGameTestTemplate(false)
public final class MetallurgyBoundaryTests {
    @GameTest(template = "metallurgy_lab")
    public static void nativeFanConditions(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(6, 2, 5));
        level.setBlockAndUpdate(pos, MetallurgyBlocks.CASTING_TABLE.getDefaultState());
        var table = (CastingTableBlockEntity) level.getBlockEntity(pos);
        var fanPos = pos.west(2);
        level.setBlockAndUpdate(fanPos, AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        var fan = (EncasedFanBlockEntity) level.getBlockEntity(fanPos);
        fan.setSpeed(64);
        fan.getAirCurrent().rebuild(); table.tick();
        assisted(h, table, true, "Ordinary air");
        level.setBlockAndUpdate(pos.west(), Blocks.WATER.defaultBlockState());
        fan.getAirCurrent().rebuild(); table.tick();
        assisted(h, table, true, "Splashing air");
        level.setBlockAndUpdate(pos.west(), Blocks.LAVA.defaultBlockState());
        fan.getAirCurrent().rebuild(); table.tick();
        assisted(h, table, false, "Blasting air");
        level.setBlockAndUpdate(pos.west(), Blocks.STONE.defaultBlockState());
        fan.getAirCurrent().rebuild(); table.tick();
        assisted(h, table, false, "Blocked air");
        level.removeBlock(pos.west(), false);
        fan.setSpeed(0); fan.getAirCurrent().rebuild(); table.tick();
        assisted(h, table, false, "Stopped fan");
        level.setBlockAndUpdate(fanPos, level.getBlockState(fanPos).setValue(BlockStateProperties.FACING, Direction.WEST));
        fan.setSpeed(64); fan.getAirCurrent().rebuild(); table.tick();
        assisted(h, table, false, "Wrong-facing fan");
        h.succeed();
    }

    private static void assisted(GameTestHelper h, CastingTableBlockEntity table, boolean expected, String state) {
        h.assertTrue(table.saveWithFullMetadata(h.getLevel().registryAccess()).getBoolean("FanAssisted") == expected,
                state + " has incorrect casting acceleration");
    }

    @GameTest(template = "metallurgy_lab")
    public static void nativeHopperAndFluidFaces(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(5, 3, 5));
        for (int y = 0; y < 2; y++) level.setBlockAndUpdate(pos.above(y), MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.getDefaultState());
        for (int i = 0; i < 3; i++) for (int y = 0; y < 2; y++) ((IndustrialBlastFurnaceBlockEntity) level.getBlockEntity(pos.above(y))).tick();
        var inputPos = pos.above(2);
        level.setBlockAndUpdate(inputPos, Blocks.HOPPER.defaultBlockState());
        var input = (HopperBlockEntity) level.getBlockEntity(inputPos);
        input.setItem(0, new ItemStack(Items.DIAMOND, 2));
        for (int i = 0; i < 16; i++) HopperBlockEntity.pushItemsTick(level, inputPos, level.getBlockState(inputPos), input);
        h.assertTrue(input.isEmpty(), "Real downward hopper could not insert into the top layer");
        var bottomPort = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.EAST);
        h.assertTrue(bottomPort.getStackInSlot(0).is(Items.DIAMOND) && bottomPort.getStackInSlot(1).is(Items.DIAMOND), "Hopper lost furnace input");
        var outputPos = pos.below();
        level.setBlockAndUpdate(outputPos, Blocks.HOPPER.defaultBlockState());
        var output = (HopperBlockEntity) level.getBlockEntity(outputPos);
        h.assertTrue(HopperBlockEntity.suckInItems(level, output) && output.getItem(0).is(Items.DIAMOND), "Real hopper failed lower-layer extraction");
        var deniedPos = pos.east();
        level.setBlockAndUpdate(deniedPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
        var denied = (HopperBlockEntity) level.getBlockEntity(deniedPos);
        denied.setItem(0, new ItemStack(Items.EMERALD));
        HopperBlockEntity.pushItemsTick(level, deniedPos, level.getBlockState(deniedPos), denied);
        h.assertTrue(denied.getItem(0).is(Items.EMERALD), "Lower-layer hopper broadened insertion permissions");
        var fluid = new FluidStack((net.minecraft.world.level.material.Fluid) GensoFluids.get(GensoFluids.MoltenMetal.IRON).orElseThrow().getSource(), 10);
        for (int y = 0; y < 2; y++) {
            for (Direction side : Direction.values()) {
                var port = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.above(y), side);
                h.assertTrue(port != null && port.fill(fluid, FluidAction.EXECUTE) == 10, "Missing fluid face: " + side);
            }
            var port = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.above(y), null);
            h.assertTrue(port != null && port.fill(fluid, FluidAction.EXECUTE) == 10, "Missing unsided fluid port");
        }
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null).drain(1000, FluidAction.SIMULATE).getAmount() == 140,
                "Member fluid faces do not share one storage");
        h.succeed();
    }

    @GameTest(template = "metallurgy_lab")
    public static void nativeLayerPlacementAndObstacle(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(4, 2, 4));
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) level.setBlockAndUpdate(pos.offset(x, 0, z), MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.getDefaultState());
        for (int i = 0; i < 3; i++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) ((IndustrialBlastFurnaceBlockEntity) level.getBlockEntity(pos.offset(x, 0, z))).tick();
        var player = FakePlayerFactory.getMinecraft(level);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); player.setShiftKeyDown(false);
        player.getInventory().clearContent(); player.setPos(Vec3.atCenterOf(pos.east(4)));
        var stack = new ItemStack(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.asItem(), 12);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var item = (BlockItem) stack.getItem();
        item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false)));
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) h.assertTrue(level.getBlockState(pos.offset(x, 1, z)).is(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.get()), "Native item did not place full layer");
        h.assertTrue(stack.getCount() == 8, "Layer placement consumed wrong item count");
        for (int i = 0; i < 3; i++) for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) ((IndustrialBlastFurnaceBlockEntity) level.getBlockEntity(pos.offset(x, y, z))).tick();
        level.setBlockAndUpdate(pos.offset(1, 2, 1), Blocks.STONE.defaultBlockState());
        var top = pos.above();
        item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(top).add(0, .5, 0), Direction.UP, top, false)));
        h.assertTrue(stack.getCount() == 7 && level.getBlockState(pos.offset(1, 2, 1)).is(Blocks.STONE)
                && level.isEmptyBlock(pos.offset(1, 2, 0)) && level.isEmptyBlock(pos.offset(0, 2, 1)),
                "Blocked layer must retain only normal initial placement without bulk consumption");
        player.getInventory().clearContent(); h.succeed();
    }
}
