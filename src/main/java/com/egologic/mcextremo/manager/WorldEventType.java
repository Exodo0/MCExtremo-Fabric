package com.egologic.mcextremo.manager;

public enum WorldEventType {
    ECLIPSE_SANGRIENTO("Eclipse Sangriento", 15 * 60 * 20),
    TORMENTA_DE_HORDA("Tormenta de Horda", 15 * 60 * 20),
    HORA_DE_CAZA("Hora de Caza", 10 * 60 * 20),
    LUNA_CORRUPTA("Luna Corrupta", 15 * 60 * 20);

    public final String displayName;
    public final int durationTicks;

    WorldEventType(String displayName, int durationTicks) {
        this.displayName = displayName;
        this.durationTicks = durationTicks;
    }
}
