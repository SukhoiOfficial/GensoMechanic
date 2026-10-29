package dev.gensomechanic.metallurgy.compat.jade;

import java.util.Collection;
import java.util.List;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The generator has no Jade API signatures, so the ordinary no-Jade run can load it. */
@GameTestHolder("gensometallurgy_jade")
@PrefixGameTestTemplate(false)
public final class JadeStorageTests {
    @GameTestGenerator
    public static Collection<TestFunction> storageChecks() {
        if (!ModList.get().isLoaded("jade")) return List.of();
        return List.of(
                new TestFunction("metallurgy_jade", "furnace_storage", "gensometallurgy_jade:metallurgy_lab",
                        Rotation.NONE, 100, 0, true, false, 1, 1, false, JadeStorageChecks::furnace),
                new TestFunction("metallurgy_jade", "casting_storage", "gensometallurgy_jade:metallurgy_lab",
                        Rotation.NONE, 100, 0, true, false, 1, 1, false, JadeStorageChecks::casting));
    }
}
