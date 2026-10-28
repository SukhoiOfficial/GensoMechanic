package dev.gensomechanic.mining.content.vein;

import dev.gensomechanic.mining.content.miner.MiningContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.fml.ModList;

public final class ExplorationArchiveItem extends Item {
    public static Runnable openMap = () -> {};
    public ExplorationArchiveItem(Properties properties) { super(properties); }
    public static boolean mapsAvailable() { return ModList.get().isLoaded("xaerominimap") && ModList.get().isLoaded("xaeroworldmap"); }
    public static ItemStack held(Player player) {
        if (player.getMainHandItem().is(MiningContent.ARCHIVE.get())) return player.getMainHandItem();
        return player.getOffhandItem().is(MiningContent.ARCHIVE.get()) ? player.getOffhandItem() : ItemStack.EMPTY;
    }
    @Override public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
        if (level.isClientSide && !mapsAvailable()) return InteractionResultHolder.pass(player.getItemInHand(hand));
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer server) ScannerService.openArchive(server, hand);
        } else if (level.isClientSide) openMap.run();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) { return useOn(context); }
    @Override public InteractionResult useOn(UseOnContext context) {
        return context.getPlayer() == null ? InteractionResult.PASS : use(context.getLevel(), context.getPlayer(), context.getHand()).getResult();
    }
}
