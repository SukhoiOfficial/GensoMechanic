package dev.gensomechanic.metallurgy.content.metallurgy;

import dev.gensomechanic.metallurgy.*;
import dev.gensomechanic.metallurgy.content.casting.CastingTableBlockEntity;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("gensometallurgy_empty")
@PrefixGameTestTemplate(false)
public final class EmptyRecipeTests {
    @GameTest(template = "metallurgy_lab")
    public static void noDefaultRecipesAndIdleStorage(GameTestHelper h) {
        for (var type : MetallurgyRecipeTypes.values())
            h.assertTrue(h.getLevel().getRecipeManager().getAllRecipesFor(type.getType()).isEmpty(), "Unexpected default " + type);
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        h.getLevel().setBlockAndUpdate(pos, MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.getDefaultState());
        var furnace = (IndustrialBlastFurnaceBlockEntity) h.getLevel().getBlockEntity(pos);
        furnace.tick();
        var items = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        h.assertTrue(items.insertItem(0, new ItemStack(Items.COPPER_INGOT), false).isEmpty(), "No-recipe furnace cannot store items");
        for (int i = 0; i < 250; i++) furnace.tick();
        h.assertTrue(items.extractItem(0, 1, false).is(Items.COPPER_INGOT), "No-recipe furnace consumed input");
        var tablePos = pos.east(3);
        h.getLevel().setBlockAndUpdate(tablePos, MetallurgyBlocks.CASTING_TABLE.getDefaultState());
        var table = (CastingTableBlockEntity) h.getLevel().getBlockEntity(tablePos);
        var tank = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, tablePos, null);
        var copper = GensoFluids.get(GensoFluids.MoltenMetal.COPPER).orElseThrow().getSource();
        h.assertTrue(tank.fill(new FluidStack(copper, 1000), FluidAction.EXECUTE) == 90, "No-recipe casting capacity");
        for (int i = 0; i < 250; i++) table.tick();
        h.assertTrue(table.getOutput().isEmpty() && tank.drain(1000, FluidAction.EXECUTE).getAmount() == 90,
                "No-recipe casting must remain idle and drainable");
        h.succeed();
    }
}
