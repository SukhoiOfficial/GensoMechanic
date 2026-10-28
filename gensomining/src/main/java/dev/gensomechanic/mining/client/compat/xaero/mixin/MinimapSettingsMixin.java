package dev.gensomechanic.mining.client.compat.xaero.mixin;
import java.util.Arrays;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.gui.GuiMinimapMain;
import xaero.lib.client.gui.ISettingEntry;
import dev.gensomechanic.mining.client.compat.xaero.ScannerMapSettings;
@Mixin(value = GuiMinimapMain.class, remap = false)
public abstract class MinimapSettingsMixin {
    @Shadow private ISettingEntry[] mainEntries;
    @Shadow private ISettingEntry[] searchableEntries;
    @Inject(method = "<init>(Lxaero/common/IXaeroMinimap;Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/screens/Screen;ZLxaero/lib/client/gui/config/context/IEditConfigScreenContext;)V", at = @At("RETURN"))
    private void genso$settings(CallbackInfo ci) {
        ISettingEntry entry = ScannerMapSettings.entry();
        mainEntries = genso$append(mainEntries, entry);
        searchableEntries = genso$append(searchableEntries, entry);
    }
    @Unique private static ISettingEntry[] genso$append(ISettingEntry[] old, ISettingEntry entry) {
        ISettingEntry[] result = old == null ? new ISettingEntry[1] : Arrays.copyOf(old, old.length + 1);
        result[result.length - 1] = entry; return result;
    }
}
