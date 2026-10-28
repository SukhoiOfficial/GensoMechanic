package dev.gensomechanic.mining.content.explosive;

import dev.gensomechanic.mining.MiningMachineTests;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in namespace: no terrain study or old distribution targets are run by these tests. */
@GameTestHolder("gensomining_explosives")
@PrefixGameTestTemplate(false)
public final class ExplosiveTests {
    private static final BlockPos POS = new BlockPos(4, 2, 4);

    private static MiningPrimedExplosive onlyPrimed(GameTestHelper h, BlockPos pos, BlockState expected) {
        var entities = h.getLevel().getEntitiesOfClass(MiningPrimedExplosive.class, new AABB(pos).inflate(2));
        h.assertTrue(entities.size() == 1, "Expected exactly one custom primed entity, found " + entities.size());
        var primed = entities.getFirst();
        h.assertTrue(primed.getType() == ExplosiveContent.PRIMED_EXPLOSIVE.get() && primed.getBlockState().is(expected.getBlock()),
                "Ignition changed entity identity or texture block state");
        return primed;
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nativeIgnitionAndSavedEntity(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(POS);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        for (var block : List.of(ExplosiveContent.CRUDE_EXPLOSIVE, ExplosiveContent.NITRO_EXPLOSIVE)) {
            var state = block.getDefaultState();
            for (var item : List.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)) {
                level.setBlockAndUpdate(pos, state);
                var tool = new ItemStack(item, item == Items.FIRE_CHARGE ? 2 : 1);
                player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                state.useItemOn(tool, level, player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                h.assertTrue(level.isEmptyBlock(pos), "Ignition left a duplicate placeable block");
                var primed = onlyPrimed(h, pos, state);
                h.assertTrue(primed.getFuse() == 80 && primed.getOwner() == player, "Native fuse/igniter was lost");
                h.assertTrue(item == Items.FIRE_CHARGE ? tool.getCount() == 1 : tool.getDamageValue() == 1, "Ignition tool cost differs from TNT");
                primed.setFuse(37);
                var tag = new CompoundTag(); primed.save(tag);
                var loaded = ExplosiveContent.PRIMED_EXPLOSIVE.get().create(level);
                loaded.load(tag);
                h.assertTrue(loaded.getFuse() == 37 && loaded.getBlockState().equals(state)
                        && loaded.explosionPower() == block.get().explosionPower()
                        && tag.getUUID("Igniter").equals(player.getUUID()), "NBT lost fuse, material, power or igniter");
                primed.discard();
            }
            // Powered placement and a later neighbor update use two different native entry points.
            level.setBlockAndUpdate(pos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(pos, state);
            h.assertTrue(level.isEmptyBlock(pos), "Powered placement did not ignite");
            onlyPrimed(h, pos, state).discard();
            level.removeBlock(pos.west(), false);
            level.setBlockAndUpdate(pos, state);
            level.setBlockAndUpdate(pos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.assertTrue(level.isEmptyBlock(pos), "Redstone update did not ignite");
            onlyPrimed(h, pos, state).discard();
            level.removeBlock(pos.west(), false);

            level.setBlockAndUpdate(pos, state);
            var arrow = new Arrow(EntityType.ARROW, level); arrow.setOwner(player); arrow.igniteForSeconds(2);
            state.onProjectileHit(level, state, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false), arrow);
            h.assertTrue(level.isEmptyBlock(pos), "Burning projectile did not ignite");
            h.assertTrue(onlyPrimed(h, pos, state).getOwner() == player, "Projectile igniter attribution was lost");
            onlyPrimed(h, pos, state).discard();

            block.get().wasExploded(level, pos, new Explosion(level, player, pos.getX(), pos.getY(), pos.getZ(), 4,
                    false, Explosion.BlockInteraction.DESTROY));
            var chain = onlyPrimed(h, pos, state);
            h.assertTrue(chain.getFuse() >= 10 && chain.getFuse() <= 29 && chain.getOwner() == player,
                    "Chain ignition did not keep native shortened fuse and owner");
            chain.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fireAndRealDetonation(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(POS); var firePos = pos.east();
        boolean oldFireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
        level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
        try {
            for (var block : List.of(ExplosiveContent.CRUDE_EXPLOSIVE, ExplosiveContent.NITRO_EXPLOSIVE)) {
                level.setBlockAndUpdate(pos, block.getDefaultState());
                level.setBlockAndUpdate(firePos.below(), Blocks.NETHERRACK.defaultBlockState());
                level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                var random = RandomSource.create(12012);
                for (int attempt = 0; attempt < 128 && level.getBlockState(pos).is(block.get()); attempt++)
                    level.getBlockState(firePos).tick(level, firePos, random);
                h.assertTrue(!level.getBlockState(pos).is(block.get()), "Native fire did not consume the explosive");
                onlyPrimed(h, pos, block.getDefaultState()).discard();
                level.removeBlock(pos, false); level.removeBlock(firePos, false);
            }
        } finally {
            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(oldFireTick, level.getServer());
        }

        // Contain the two actual explosions inside this test's template.
        for (int x = 1; x <= 9; x++) for (int y = 0; y <= 6; y++) for (int z = 1; z <= 9; z++)
            if (x == 1 || x == 9 || y == 0 || y == 6 || z == 1 || z == 9) h.setBlock(new BlockPos(x,y,z), Blocks.OBSIDIAN);
        var blast = h.absolutePos(new BlockPos(5, 2, 5));
        var target = blast.east();
        List<Float> observed = new ArrayList<>();
        Consumer<ExplosionEvent.Start> listener = event -> {
            if (event.getLevel() != level || !(event.getExplosion().getDirectSourceEntity() instanceof MiningPrimedExplosive entity)
                    || entity.position().distanceToSqr(Vec3.atCenterOf(blast)) > 4) return;
            try {
                var field = Explosion.class.getDeclaredField("radius"); field.setAccessible(true);
                observed.add(field.getFloat(event.getExplosion()));
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            for (var block : List.of(ExplosiveContent.CRUDE_EXPLOSIVE, ExplosiveContent.NITRO_EXPLOSIVE)) {
                level.setBlockAndUpdate(target, Blocks.GLASS.defaultBlockState());
                var primed = new MiningPrimedExplosive(level, blast.getX() + 0.5, blast.getY(), blast.getZ() + 0.5,
                        block.getDefaultState(), null);
                level.addFreshEntity(primed); primed.setFuse(1); primed.tick();
                h.assertTrue(primed.isRemoved() && level.isEmptyBlock(target), "Fuse expiry did not detonate and destroy the nearby block");
            }
        } finally { NeoForge.EVENT_BUS.unregister(listener); }
        h.assertTrue(observed.equals(List.of(2f, 4f)), "Actual explosion radii differ from 2/4: " + observed);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void normalDropsAndDispenser(GameTestHelper h) {
        for (int index = 0; index < 2; index++) {
            var block = index == 0 ? ExplosiveContent.CRUDE_EXPLOSIVE : ExplosiveContent.NITRO_EXPLOSIVE;
            var placed = new BlockPos(2 + index * 5, 2, 2);
            h.setBlock(placed, block.get());
            h.getLevel().destroyBlock(h.absolutePos(placed), true);
            h.assertItemEntityCountIs(block.asItem(), placed, 2, 1);
            var dispenser = placed.south(4);
            h.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
            ((DispenserBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(dispenser))).setItem(0, block.asStack(2));
            h.setBlock(dispenser.west(), Blocks.REDSTONE_BLOCK);
        }
        h.runAtTickTime(8, () -> {
            for (int index = 0; index < 2; index++) {
                var block = index == 0 ? ExplosiveContent.CRUDE_EXPLOSIVE : ExplosiveContent.NITRO_EXPLOSIVE;
                var dispenser = h.absolutePos(new BlockPos(2 + index * 5, 2, 6));
                var inventory = (DispenserBlockEntity) h.getLevel().getBlockEntity(dispenser);
                h.assertTrue(inventory.getItem(0).getCount() == 1, "Dispenser did not consume exactly one explosive");
                onlyPrimed(h, dispenser.east(), block.getDefaultState()).discard();
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void newExplosiveDropsOnceFromMiner(GameTestHelper h) {
        MiningMachineTests.tankRemovalDropsOneMachineAndInventory(h);
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void newExplosivePaidCycleSurvivesSave(GameTestHelper h) {
        MiningMachineTests.paidCycleRetainsEffectsAcrossSaveAndCompletesOnce(h);
    }
}
