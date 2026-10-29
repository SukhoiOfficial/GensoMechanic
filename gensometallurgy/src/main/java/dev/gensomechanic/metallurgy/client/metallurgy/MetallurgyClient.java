package dev.gensomechanic.metallurgy.client.metallurgy;
import dev.gensomechanic.metallurgy.*;
import net.minecraft.client.renderer.*;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.*;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
public final class MetallurgyClient {
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(MetallurgyBlockEntities.INDUSTRIAL_BLAST_FURNACE.get(), IndustrialBlastFurnaceRenderer::new);
        e.registerBlockEntityRenderer(MetallurgyBlockEntities.CASTING_TABLE.get(), CastingTableRenderer::new);
    }
    public static void extensions(RegisterClientExtensionsEvent e) {
        for (var metal : GensoFluids.MoltenMetal.values()) {
            String path = "fluid/molten_metals/" + metal.serializedName() + "/";
            e.registerFluidType(new IClientFluidTypeExtensions() {
                @Override public net.minecraft.resources.ResourceLocation getStillTexture() { return GensoMetallurgy.asResource(path + "still"); }
                @Override public net.minecraft.resources.ResourceLocation getFlowingTexture() { return GensoMetallurgy.asResource(path + "flowing"); }
            }, GensoFluids.get(metal).orElseThrow().getSource().getFluidType());
        }
    }
    public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            for (var f : GensoFluids.registeredFluids()) {
                ItemBlockRenderTypes.setRenderLayer(f.get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(f.getSource(), RenderType.translucent());
            }
            net.createmod.ponder.foundation.PonderIndex.addPlugin(new dev.gensomechanic.metallurgy.client.ponder.GensoPonderPlugin());
        });
    }
    private MetallurgyClient() {}
}
