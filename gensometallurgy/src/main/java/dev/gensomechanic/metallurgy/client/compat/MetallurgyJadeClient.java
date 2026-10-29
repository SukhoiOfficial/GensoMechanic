package dev.gensomechanic.metallurgy.client.compat;

import dev.gensomechanic.metallurgy.compat.jade.MetallurgyJadePlugin;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;
import snownee.jade.util.FluidTextHelper;

/** Keep Jade client rendering types out of the server provider's implementation. */
public final class MetallurgyJadeClient {
    public static void register(IWailaClientRegistration registration) {
        registration.registerItemStorageClient(Items.INSTANCE);
        registration.registerFluidStorageClient(Fluids.INSTANCE);
    }

    public enum Items implements IClientExtensionProvider<ItemStack, ItemView> {
        INSTANCE;
        @Override public ResourceLocation getUid() { return MetallurgyJadePlugin.ITEMS; }
        @Override public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor,
                List<ViewGroup<ItemStack>> groups) {
            return ClientViewGroup.map(groups, ItemView::new, null);
        }
    }

    public enum Fluids implements IClientExtensionProvider<CompoundTag, FluidView> {
        INSTANCE;
        @Override public ResourceLocation getUid() { return MetallurgyJadePlugin.FLUIDS; }
        @Override public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor,
                List<ViewGroup<CompoundTag>> groups) {
            return ClientViewGroup.map(groups, tag -> FluidView.readDefault(tag, accessor.nbtOps()),
                    (source, display) -> {
                        long capacity = source.getExtraData().getLong(MetallurgyJadePlugin.SHARED_CAPACITY);
                        if (capacity <= 0 || display.views.size() < 2) return;
                        var theme = IThemeHelper.get();
                        long amount = source.getExtraData().getLong(MetallurgyJadePlugin.SHARED_AMOUNT);
                        var total = Component.translatable("jade.fluid.with_capacity",
                                theme.info(FluidTextHelper.getUnicodeMillibuckets(amount, true)),
                                FluidTextHelper.getUnicodeMillibuckets(capacity, true));
                        display.title = Component.translatable(
                                "gensometallurgy.goggles.blast_furnace.fluid_container", total);
                        // One native group reports the shared capacity. Native fluid rows
                        // show each amount, not several apparently independent full tanks.
                        for (FluidView view : display.views) {
                            view.overrideText = Component.translatable("jade.fluid",
                                    theme.info(IDisplayHelper.get().stripColor(view.fluidName)),
                                    theme.info(view.current));
                        }
                    });
        }
    }

    private MetallurgyJadeClient() {}
}
