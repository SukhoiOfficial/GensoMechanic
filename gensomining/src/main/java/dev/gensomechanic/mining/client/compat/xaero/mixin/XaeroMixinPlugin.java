package dev.gensomechanic.mining.client.compat.xaero.mixin;

import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.LoadingModList;

/** Never resolve optional Xaero or Minecraft client types on a dedicated server. */
public final class XaeroMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (FMLEnvironment.dist != Dist.CLIENT) return false;
        var mods = LoadingModList.get();
        String id = mixinClassName.contains("WorldMap") ? "xaeroworldmap" : "xaerominimap";
        return mods != null && mods.getModFileById(id) != null;
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
