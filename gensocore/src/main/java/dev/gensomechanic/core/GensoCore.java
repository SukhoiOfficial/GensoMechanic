package dev.gensomechanic.core;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(GensoCore.MOD_ID)
public final class GensoCore {
    public static final String MOD_ID = "gensocore";

    public GensoCore(ModContainer container) {
        LogUtils.getLogger().info("{} {} initialized", MOD_ID, container.getModInfo().getVersion());
    }
}
