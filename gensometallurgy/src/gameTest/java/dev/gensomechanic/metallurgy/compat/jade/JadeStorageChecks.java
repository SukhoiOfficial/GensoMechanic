package dev.gensomechanic.metallurgy.compat.jade;

import dev.gensomechanic.metallurgy.GensoFluids;
import dev.gensomechanic.metallurgy.MetallurgyBlocks;
import dev.gensomechanic.metallurgy.content.casting.CastingTableBlockEntity;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlockEntity;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ViewGroup;

/** Loaded only after the generator verifies that Jade is installed. */
final class JadeStorageChecks {
    static void furnace(GameTestHelper h) {
        var parts = furnaceParts(h);
        var lower = parts.getFirst();
        var top = parts.getLast();
        var tank = fluids(h, lower);
        var lowerAccessor = accessor(h, lower);
        h.assertTrue(lower.getTotalBlockCount() == 8 && tank.getTanks() == 9,
                "Expected a connected furnace with nine virtual fluid slots");

        var empty = fluidGroup(h, lowerAccessor);
        assertFluidGroup(h, empty, 8000, 0, 1);
        h.assertTrue(fluid(h, empty.views.getFirst()).isEmpty(), "Empty furnace shows a fluid");
        h.assertTrue(itemViews(lowerAccessor).isEmpty(), "Empty furnace shows items");

        Fluid copper = molten(GensoFluids.MoltenMetal.COPPER);
        Fluid zinc = molten(GensoFluids.MoltenMetal.ZINC);
        h.assertTrue(tank.fill(new FluidStack(copper, 3500), FluidAction.EXECUTE) == 3500, "Copper fixture fill failed");
        var single = fluidGroup(h, lowerAccessor);
        assertFluidGroup(h, single, 8000, 3500, 1);
        h.assertTrue(fluid(h, single.views.getFirst()).getType() == copper, "Single fluid identity lost");

        h.assertTrue(tank.fill(new FluidStack(zinc, 6000), FluidAction.EXECUTE) == 4500, "Shared capacity fixture failed");
        h.assertTrue(ItemHandlerHelper.insertItemStacked(items(h, top), new ItemStack(Items.DIAMOND, 4), false).isEmpty(),
                "Top-layer fixture input failed");
        h.assertTrue(!ItemHandlerHelper.insertItemStacked(items(h, lower), new ItemStack(Items.DIAMOND), false).isEmpty(),
                "Displaying lower storage must not broaden input permissions");

        var before = saved(h, parts);
        var multiple = fluidGroup(h, lowerAccessor);
        assertFluidGroup(h, multiple, 8000, 8000, 2);
        h.assertTrue(multiple.views.stream().map(row -> fluid(h, row)).anyMatch(f -> f.getType() == copper && f.getAmount() == 3500)
                        && multiple.views.stream().map(row -> fluid(h, row)).anyMatch(f -> f.getType() == zinc && f.getAmount() == 4500),
                "Multi-fluid amounts or identities changed");
        CompoundTag reference = fluidPayload(List.of(multiple));
        for (var part : parts) {
            var memberAccessor = accessor(h, part);
            h.assertTrue(reference.equals(fluidPayload(MetallurgyJadePlugin.Fluids.INSTANCE.getGroups(memberAccessor))),
                    "A furnace member shows different shared fluid storage");
            assertDiamonds(h, itemViews(memberAccessor));
        }
        var decoded = ViewGroup.readList(reference, "groups", CompoundTag::copy);
        h.assertTrue(fluidPayload(decoded).equals(reference), "Jade serialization lost shared fluid metadata");

        // Returned item stacks and NBT belong to the display snapshot, never to live storage.
        itemViews(lowerAccessor).getFirst().setCount(1);
        multiple.views.getFirst().putLong("capacity", 1);
        assertDiamonds(h, itemViews(lowerAccessor));
        assertFluidGroup(h, fluidGroup(h, lowerAccessor), 8000, 8000, 2);
        h.assertTrue(before.equals(saved(h, parts)), "Reading or modifying a Jade snapshot changed furnace NBT");

        h.getLevel().removeBlock(lower.getBlockPos(), false);
        h.assertTrue(MetallurgyJadePlugin.Fluids.INSTANCE.getGroups(lowerAccessor).isEmpty()
                        && MetallurgyJadePlugin.Items.INSTANCE.getGroups(lowerAccessor).isEmpty(),
                "A stale Jade target exposes removed furnace storage");
        h.succeed();
    }

    static void casting(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(3, 2, 3));
        h.getLevel().setBlockAndUpdate(pos, MetallurgyBlocks.CASTING_TABLE.getDefaultState());
        var table = (CastingTableBlockEntity) h.getLevel().getBlockEntity(pos);
        var accessor = accessor(h, table);
        var empty = fluidGroup(h, accessor);
        h.assertTrue(empty.views.size() == 1 && empty.views.getFirst().getLong("capacity") == 90
                        && fluid(h, empty.views.getFirst()).isEmpty(), "Empty casting table must show one 90 mB tank");
        h.assertTrue(itemViews(accessor).isEmpty(), "Empty casting table shows output");

        Fluid iron = molten(GensoFluids.MoltenMetal.IRON);
        h.assertTrue(fluids(h, table).fill(new FluidStack(iron, 45), FluidAction.EXECUTE) == 45, "Casting fluid fixture failed");
        var fixtureOutput = new ItemStackHandler(1);
        fixtureOutput.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        CompoundTag fixture = table.saveWithFullMetadata(h.getLevel().registryAccess());
        fixture.put("OutputInventory", fixtureOutput.serializeNBT(h.getLevel().registryAccess()));
        table.loadWithComponents(fixture, h.getLevel().registryAccess());
        CompoundTag before = table.saveWithFullMetadata(h.getLevel().registryAccess());

        var filled = fluidGroup(h, accessor);
        h.assertTrue(filled.views.size() == 1 && filled.views.getFirst().getLong("capacity") == 90
                        && fluid(h, filled.views.getFirst()).getType() == iron
                        && fluid(h, filled.views.getFirst()).getAmount() == 45,
                "Casting display lost its 90 mB capacity or stored fluid");
        h.assertTrue(!filled.getExtraData().contains(MetallurgyJadePlugin.SHARED_CAPACITY), "Casting tank marked as shared");
        var output = itemViews(accessor);
        h.assertTrue(output.size() == 1 && output.getFirst().is(Items.IRON_INGOT) && output.getFirst().getCount() == 1,
                "Casting display lost its single output");
        output.getFirst().setCount(64);
        h.assertTrue(table.getOutput().getCount() == 1 && itemViews(accessor).getFirst().getCount() == 1,
                "Casting display shares a mutable output stack");
        h.assertTrue(before.equals(table.saveWithFullMetadata(h.getLevel().registryAccess())),
                "Jade reads changed casting table NBT");
        h.succeed();
    }

    private static List<IndustrialBlastFurnaceBlockEntity> furnaceParts(GameTestHelper h) {
        BlockPos origin = h.absolutePos(new BlockPos(3, 2, 3));
        var parts = new ArrayList<IndustrialBlastFurnaceBlockEntity>();
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            BlockPos pos = origin.offset(x, y, z);
            h.getLevel().setBlockAndUpdate(pos, MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.getDefaultState());
            parts.add((IndustrialBlastFurnaceBlockEntity) h.getLevel().getBlockEntity(pos));
        }
        for (int round = 0; round < 3; round++) parts.forEach(IndustrialBlastFurnaceBlockEntity::tick);
        return parts;
    }

    private static List<CompoundTag> saved(GameTestHelper h, List<IndustrialBlastFurnaceBlockEntity> parts) {
        return parts.stream().map(part -> part.saveWithFullMetadata(h.getLevel().registryAccess())).toList();
    }

    private static void assertDiamonds(GameTestHelper h, List<ItemStack> views) {
        h.assertTrue(views.size() == 1 && views.getFirst().is(Items.DIAMOND) && views.getFirst().getCount() == 4,
                "Jade must coalesce all four furnace items for every member");
    }

    private static void assertFluidGroup(GameTestHelper h, ViewGroup<CompoundTag> group, long capacity, long amount, int rows) {
        h.assertTrue(group.getExtraData().getLong(MetallurgyJadePlugin.SHARED_CAPACITY) == capacity
                        && group.getExtraData().getLong(MetallurgyJadePlugin.SHARED_AMOUNT) == amount,
                "Shared furnace capacity/amount was multiplied by virtual tank count");
        h.assertTrue(group.views.size() == rows
                        && group.views.stream().allMatch(row -> row.getLong("capacity") == capacity)
                        && group.views.stream().mapToLong(row -> fluid(h, row).getAmount()).sum() == amount,
                "Jade emitted incorrect fluid rows, empty virtual tanks or amounts");
    }

    private static ViewGroup<CompoundTag> fluidGroup(GameTestHelper h, BlockAccessor accessor) {
        var groups = MetallurgyJadePlugin.Fluids.INSTANCE.getGroups(accessor);
        h.assertTrue(groups.size() == 1, "Machine must expose one fluid storage group");
        return groups.getFirst();
    }

    private static CompoundTag fluidPayload(List<ViewGroup<CompoundTag>> groups) {
        var payload = new CompoundTag();
        ViewGroup.saveList(payload, "groups", groups, CompoundTag::copy);
        return payload;
    }

    private static JadeFluidObject fluid(GameTestHelper h, CompoundTag row) {
        return JadeFluidObject.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),
                row.get("fluid")).getOrThrow();
    }

    private static List<ItemStack> itemViews(BlockAccessor accessor) {
        return MetallurgyJadePlugin.Items.INSTANCE.getGroups(accessor).stream().flatMap(group -> group.views.stream()).toList();
    }

    private static IFluidHandler fluids(GameTestHelper h, BlockEntity entity) {
        return java.util.Objects.requireNonNull(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                entity.getBlockPos(), (Direction) null));
    }

    private static IItemHandler items(GameTestHelper h, BlockEntity entity) {
        return java.util.Objects.requireNonNull(h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                entity.getBlockPos(), (Direction) null));
    }

    private static Fluid molten(GensoFluids.MoltenMetal metal) {
        return GensoFluids.get(metal).orElseThrow().getSource();
    }

    private static BlockAccessor accessor(GameTestHelper h, BlockEntity entity) {
        return (BlockAccessor) Proxy.newProxyInstance(BlockAccessor.class.getClassLoader(),
                new Class<?>[]{BlockAccessor.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getLevel" -> h.getLevel();
                    case "getPosition" -> entity.getBlockPos();
                    case "getBlockEntity" -> entity;
                    case "getBlockState" -> h.getLevel().getBlockState(entity.getBlockPos());
                    case "nbtOps" -> h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }
}
