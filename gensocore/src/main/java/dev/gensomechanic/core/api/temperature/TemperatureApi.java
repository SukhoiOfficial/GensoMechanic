package dev.gensomechanic.core.api.temperature;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Optional server-side service. Absence means no climate restrictions, never an assumed mild climate. */
public final class TemperatureApi {
    @FunctionalInterface public interface Provider {
        Optional<TemperatureReading> query(ServerLevel level, BlockPos pos);
    }
    private static volatile Provider provider = (level, pos) -> Optional.empty();

    public static void register(Provider implementation) {
        provider = java.util.Objects.requireNonNull(implementation);
    }

    /** Implementations must not load chunks or mutate the queried environment. */
    public static Optional<TemperatureReading> query(ServerLevel level, BlockPos pos) {
        return provider.query(level, pos);
    }

    private TemperatureApi() {}
}
