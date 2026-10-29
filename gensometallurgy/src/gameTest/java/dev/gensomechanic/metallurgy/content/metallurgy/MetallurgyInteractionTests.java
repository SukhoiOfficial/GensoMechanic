package dev.gensomechanic.metallurgy.content.metallurgy;

import com.simibubi.create.AllItems;
import dev.gensomechanic.metallurgy.*;
import dev.gensomechanic.metallurgy.content.casting.CastingTableBlockEntity;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("gensometallurgy")
@PrefixGameTestTemplate(false)
public final class MetallurgyInteractionTests {
    @GameTest(template = "metallurgy_lab")
    public static void nativeEmptyHandAndWrench(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(5, 2, 5));
        level.setBlockAndUpdate(pos, MetallurgyBlocks.CASTING_TABLE.getDefaultState());
        var table = (CastingTableBlockEntity) level.getBlockEntity(pos);
        var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        tank.fill(new FluidStack((net.minecraft.world.level.material.Fluid) GensoFluids.get(GensoFluids.MoltenMetal.COPPER).orElseThrow().getSource(), 90), FluidAction.EXECUTE);
        for (int i = 0; i < 110; i++) table.tick();
        h.assertTrue(table.hasOutput(), "Fixture casting did not finish");
        var player = FakePlayerFactory.getMinecraft(level);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.getInventory().clearContent(); player.setShiftKeyDown(false);
        player.setPos(Vec3.atCenterOf(pos.east()));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(!table.hasOutput() && player.getInventory().countItem(Items.COPPER_INGOT) == 1,
                "Real empty-hand dispatch did not collect exactly one output");
        player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(player.getInventory().countItem(Items.COPPER_INGOT) == 1, "Repeated collection duplicated output");
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        AllItems.WRENCH.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        h.assertTrue(level.isEmptyBlock(pos) && player.getInventory().countItem(MetallurgyBlocks.CASTING_TABLE.asItem()) == 1,
                "Native wrench did not return casting table to inventory");
        h.assertTrue(tank.getTanks() == 0, "Wrenched table retained old fluid handler");
        player.setShiftKeyDown(false); player.getInventory().clearContent();
        h.succeed();
    }

    @GameTest(template = "metallurgy_lab")
    public static void fallingItemEntersRealChamber(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(5, 2, 5));
        level.setBlockAndUpdate(pos, MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.getDefaultState());
        var furnace = (IndustrialBlastFurnaceBlockEntity) level.getBlockEntity(pos); furnace.tick();
        var entity = new ItemEntity(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, new ItemStack(Items.DIAMOND, 2));
        level.addFreshEntity(entity);
        level.getBlockState(pos).entityInside(level, pos, entity);
        h.assertTrue(furnace.getLocalItem().is(Items.DIAMOND) && furnace.getLocalItem().getCount() == 1
                && entity.getItem().getCount() == 1, "Dropped-item inlet did not preserve capacity/remainder");
        entity.discard(); h.succeed();
    }

    @GameTest(template = "metallurgy_lab")
    public static void nativeMoltenBucketDispenserAndFallback(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(5, 2, 5));
        level.setBlockAndUpdate(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        var dispenser = (DispenserBlockEntity) level.getBlockEntity(pos);
        var source = new BlockSource(level, pos, level.getBlockState(pos), dispenser);
        var copper = GensoFluids.get(GensoFluids.MoltenMetal.COPPER).orElseThrow();
        var bucket = copper.getBucket().orElseThrow();
        var behavior = DispenserBlock.DISPENSER_REGISTRY.get(bucket);
        var result = behavior.dispense(source, new ItemStack(bucket));
        h.assertTrue(result.is(Items.BUCKET) && level.getFluidState(pos.east()).is((net.minecraft.world.level.material.Fluid) copper.getSource()),
                "Native dispenser failed molten placement or empty-bucket return");
        level.setBlockAndUpdate(pos.east(), Blocks.STONE.defaultBlockState());
        behavior.dispense(source, new ItemStack(bucket));
        h.assertTrue(level.getBlockState(pos.east()).is(Blocks.STONE), "Failed bucket placement overwrote obstacle");
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
        h.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(bucket)), "Failed native dispenser did not eject original filled bucket");
        drops.forEach(ItemEntity::discard); h.succeed();
    }
}
