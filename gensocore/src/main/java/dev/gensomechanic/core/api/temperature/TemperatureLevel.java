package dev.gensomechanic.core.api.temperature;

import net.minecraft.util.StringRepresentable;

/** Ordered environmental categories, not degrees Celsius. */
public enum TemperatureLevel implements StringRepresentable {
    FRIGID, COLD, COOL, MILD, WARM, HOT, SCORCHING;

    @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }

    public static TemperatureLevel parse(String name) {
        for (var level : values()) if (level.getSerializedName().equals(name)) return level;
        throw new IllegalArgumentException("Unknown temperature level: " + name);
    }
}
