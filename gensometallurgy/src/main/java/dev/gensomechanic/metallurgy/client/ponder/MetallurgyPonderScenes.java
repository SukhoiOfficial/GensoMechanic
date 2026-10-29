package dev.gensomechanic.metallurgy.client.ponder;

import com.simibubi.create.AllBlocks;
import dev.gensomechanic.metallurgy.client.ponder.scenes.IndustrialBlastFurnaceScenes;
import dev.gensomechanic.metallurgy.MetallurgyBlocks;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class MetallurgyPonderScenes {
    private MetallurgyPonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> scenes =
            helper.withKeyFunction(RegistryEntry::getId);

        scenes.forComponents(MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE)
            .addStoryBoard(
                "industrial_blast_furnace/structure",
                IndustrialBlastFurnaceScenes::structure
            )
            .addStoryBoard(
                "industrial_blast_furnace/item_input",
                IndustrialBlastFurnaceScenes::itemInput
            )
            .addStoryBoard(
                "industrial_blast_furnace/processing",
                IndustrialBlastFurnaceScenes::processing
            )
            .addStoryBoard(
                "industrial_blast_furnace/output",
                IndustrialBlastFurnaceScenes::output
            );
    }
}
