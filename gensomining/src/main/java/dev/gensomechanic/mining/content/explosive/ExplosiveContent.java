package dev.gensomechanic.mining.content.explosive;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.EntityEntry;
import dev.gensomechanic.mining.GensoCreativeModeTabs;
import dev.gensomechanic.mining.GensoMining;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

public final class ExplosiveContent {
    private static final CreateRegistrate R = GensoMining.registrate();
    static { R.setCreativeTab(GensoCreativeModeTabs.MAIN); }

    public static final BlockEntry<MiningExplosiveBlock> CRUDE_EXPLOSIVE = explosive("crude_explosive", "Crude Explosive", 2);
    public static final BlockEntry<MiningExplosiveBlock> NITRO_EXPLOSIVE = explosive("nitro_explosive", "Nitro Explosive", 4);
    public static final EntityEntry<MiningPrimedExplosive> PRIMED_EXPLOSIVE = R
            .<MiningPrimedExplosive>entity("primed_explosive", MiningPrimedExplosive::new, MobCategory.MISC)
            .properties(p -> p.fireImmune().sized(0.98f, 0.98f).eyeHeight(0.15f).clientTrackingRange(10).updateInterval(10))
            .lang("Primed Mining Explosive").register();

    private static BlockEntry<MiningExplosiveBlock> explosive(String id, String name, float power) {
        return R.block(id, p -> new MiningExplosiveBlock(p, power)).initialProperties(() -> Blocks.TNT)
                .blockstate((c, p) -> p.simpleBlock(c.get(), p.models().cubeBottomTop(id,
                        p.modLoc("block/explosives/explosive_side"), p.modLoc("block/explosives/explosive_bottom"),
                        p.modLoc("block/explosives/" + id + "_top"))))
                .lang(name).simpleItem().register();
    }

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            registerDispenser(CRUDE_EXPLOSIVE);
            registerDispenser(NITRO_EXPLOSIVE);
        });
    }

    private static void registerDispenser(BlockEntry<MiningExplosiveBlock> block) {
        DispenserBlock.registerBehavior(block.asItem(), new DefaultDispenseItemBehavior() {
            @Override protected ItemStack execute(BlockSource source, ItemStack stack) {
                Direction facing = source.state().getValue(DispenserBlock.FACING);
                var pos = source.pos().relative(facing);
                var level = source.level();
                var primed = new MiningPrimedExplosive(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        block.getDefaultState(), null);
                level.addFreshEntity(primed);
                level.playSound(null, primed.getX(), primed.getY(), primed.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1, 1);
                level.gameEvent(null, GameEvent.ENTITY_PLACE, pos);
                stack.shrink(1);
                return stack;
            }
        });
    }

    public static void register() {}
    private ExplosiveContent() {}
}
