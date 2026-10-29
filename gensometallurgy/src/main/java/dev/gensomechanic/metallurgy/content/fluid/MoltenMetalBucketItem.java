package dev.gensomechanic.metallurgy.content.fluid;
import dev.gensomechanic.metallurgy.GensoFluids.MoltenMetal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
public final class MoltenMetalBucketItem extends BucketItem {
    private final MoltenMetal metal;
    public MoltenMetalBucketItem(Fluid fluid, Properties p, MoltenMetal metal) { super(fluid,p); this.metal=metal; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return metal.isAvailable() ? super.use(level,player,hand) : InteractionResultHolder.fail(player.getItemInHand(hand));
    }
    @Override public boolean emptyContents(Player player, Level level, BlockPos pos, BlockHitResult hit, ItemStack container) {
        return metal.isAvailable() && super.emptyContents(player,level,pos,hit,container);
    }
}
