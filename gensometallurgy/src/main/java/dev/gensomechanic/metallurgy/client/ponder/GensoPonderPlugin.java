// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.client.ponder;

import dev.gensomechanic.metallurgy.GensoMetallurgy;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class GensoPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return GensoMetallurgy.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        MetallurgyPonderScenes.register(helper);
    }
}
