package com.egologic.mcextremo.util;

public enum DifficultyPhase {
    START(0, 3, 0, "Inicio", "Zombies casi vanilla", false, false, false, false, false),
    HUNTING(4, 10, 1, "Caceria", "Puertas y mini hordas", true, false, false, false, true),
    COORDINATED(11, 25, 2, "Coordinacion", "Zombies coordinados", true, true, false, false, true),
    SIEGE(26, 45, 4, "Asedio", "Rompen bloques blandos", true, true, true, false, true),
    BUILDERS(46, 70, 5, "Constructores", "Construyen para subir", true, true, true, true, true),
    NIGHTMARE(71, 99, 6, "Pesadilla", "Hordas grandes y persecucion total", true, true, true, true, true),
    EXTREME(100, Integer.MAX_VALUE, 6, "Extremo", "Todo activo con maxima presion", true, true, true, true, true);

    private final int minDay;
    private final int maxDay;
    private final int intelligenceLevel;
    private final String displayName;
    private final String description;
    private final boolean doors;
    private final boolean coordination;
    private final boolean blockBreaking;
    private final boolean building;
    private final boolean hordes;

    DifficultyPhase(int minDay, int maxDay, int intelligenceLevel, String displayName, String description,
                    boolean doors, boolean coordination, boolean blockBreaking, boolean building, boolean hordes) {
        this.minDay = minDay;
        this.maxDay = maxDay;
        this.intelligenceLevel = intelligenceLevel;
        this.displayName = displayName;
        this.description = description;
        this.doors = doors;
        this.coordination = coordination;
        this.blockBreaking = blockBreaking;
        this.building = building;
        this.hordes = hordes;
    }

    public static DifficultyPhase fromDay(int day) {
        int normalizedDay = Math.max(0, day);
        for (DifficultyPhase phase : values()) {
            if (normalizedDay >= phase.minDay && normalizedDay <= phase.maxDay) {
                return phase;
            }
        }
        return EXTREME;
    }

    public DifficultyPhase next() {
        int ordinal = ordinal();
        DifficultyPhase[] phases = values();
        if (ordinal + 1 >= phases.length) return null;
        return phases[ordinal + 1];
    }

    public int getMinDay() {
        return minDay;
    }

    public int getIntelligenceLevel() {
        return intelligenceLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasDoors() {
        return doors;
    }

    public boolean hasCoordination() {
        return coordination;
    }

    public boolean hasBlockBreaking() {
        return blockBreaking;
    }

    public boolean hasBuilding() {
        return building;
    }

    public boolean hasHordes() {
        return hordes;
    }
}
