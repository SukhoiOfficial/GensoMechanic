package dev.gensomechanic.mining.content.miner;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** One stack per item and components, shared by output reservation and actual insertion. */
class MinerOutputInventory extends ItemStackHandler {
    MinerOutputInventory() { super(9); }

    @Override
    public int getSlotLimit(int slot) { return 64; }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        for (int other = 0; other < getSlots(); other++) {
            if (other != slot && ItemStack.isSameItemSameComponents(getStackInSlot(other), stack))
                return false;
        }
        // ItemStackHandler combines our slot limit with the item's own stack limit.
        return super.isItemValid(slot, stack);
    }
}
