package com.egologic.mcextremo.manager;

public enum DailyMission {
    KILL_ZOMBIES_30("kill_zombies_30", "Mata 30 zombies", 30, 8, ExtraReward.NONE),
    KILL_ZOMBIES_60("kill_zombies_60", "Mata 60 zombies", 60, 15, ExtraReward.NONE),
    SURVIVE_HORDE("survive_horde", "Sobrevive una horda", 1, 12, ExtraReward.HEART_CHANCE_30),
    COMPLETE_HORDE("complete_horde", "Elimina una horda", 1, 10, ExtraReward.NONE),
    EARN_FRAGMENTS_15("earn_fragments_15", "Obten 15 fragmentos", 15, 6, ExtraReward.XP_BOTTLE),
    PLAY_TIME_30MIN("play_time_30min", "Juega 30 minutos online", 30, 5, ExtraReward.NONE),
    NO_DEATH_TODAY("no_death_today", "Sobrevive el dia sin morir", 1, 20, ExtraReward.HEART_CHANCE_50),
    KILL_DURING_PVP("kill_during_pvp", "Mata a un jugador en PvP", 1, 15, ExtraReward.NONE),
    SURVIVE_WORLD_EVENT("survive_world_event", "Sobrevive un evento mundial", 1, 12, ExtraReward.XP_BOTTLE),
    CAPTURE_CONTROL_POINT("capture_control_point", "Captura un punto de control", 1, 12, ExtraReward.NONE);

    public final String id;
    public final String description;
    public final int objective;
    public final int fragmentReward;
    public final ExtraReward extraReward;

    DailyMission(String id, String description, int objective, int fragmentReward, ExtraReward extraReward) {
        this.id = id;
        this.description = description;
        this.objective = objective;
        this.fragmentReward = fragmentReward;
        this.extraReward = extraReward;
    }

    public static DailyMission byId(String id) {
        for (DailyMission mission : values()) {
            if (mission.id.equals(id)) return mission;
        }
        return null;
    }

    public enum ExtraReward {
        NONE,
        HEART_CHANCE_30,
        HEART_CHANCE_50,
        XP_BOTTLE
    }
}
