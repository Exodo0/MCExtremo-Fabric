package com.egologic.mcextremo.manager;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.util.TextUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DailyMissionManager {
    private final MCExtremo mod;
    private int lastSeenDay = -1;
    private int tickCounter;

    public DailyMissionManager(MCExtremo mod) {
        this.mod = mod;
    }

    public void registerEvents() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            onKill(player, killedEntity);
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                onPlayerDeath(player);
            }
            return true;
        });
    }

    public void tick(MinecraftServer server) {
        if (!ModConfig.get().misionesDiarias.activado) return;
        int day = mod.getDataManager().getRealDay();
        if (lastSeenDay < 0) lastSeenDay = day;
        if (day != lastSeenDay) {
            lastSeenDay = day;
            onDayChange(server);
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ensureMissions(player, day);
        }
        if (++tickCounter >= 20 * 60) {
            tickCounter = 0;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                increment(player, DailyMission.PLAY_TIME_30MIN, 1);
            }
        }
    }

    public void onJoin(ServerPlayerEntity player) {
        ensureMissions(player, mod.getDataManager().getRealDay());
    }

    public void onDayChange(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            completeNoDeathIfEligible(player);
            if (ModConfig.get().misionesDiarias.resetAlCambioDia) {
                ensureMissions(player, mod.getDataManager().getRealDay());
            }
        }
    }

    public void onKill(ServerPlayerEntity player, Entity killedEntity) {
        if (killedEntity instanceof ZombieEntity) {
            increment(player, DailyMission.KILL_ZOMBIES_30, 1);
            increment(player, DailyMission.KILL_ZOMBIES_60, 1);
            mod.getWorldEventManager().onZombieKilled(player);
        }
        if (killedEntity instanceof ServerPlayerEntity && mod.getPvpScheduler().isPvpEnabled()) {
            increment(player, DailyMission.KILL_DURING_PVP, 1);
            mod.getWorldEventManager().onPvpKill(player);
        }
    }

    public void onPlayerDeath(ServerPlayerEntity player) {
        DataManager.DailyMissionState state = getState(player);
        if (state == null) return;
        state.progress.put(DailyMission.NO_DEATH_TODAY.id, -999);
        mod.getDataManager().setDailyMissionState(player.getUuid(), state);
    }

    public void onHordeComplete(ServerPlayerEntity player, boolean timeout) {
        increment(player, DailyMission.SURVIVE_HORDE, 1);
        if (!timeout) increment(player, DailyMission.COMPLETE_HORDE, 1);
    }

    public void onFragmentsEarned(ServerPlayerEntity player, int amount) {
        increment(player, DailyMission.EARN_FRAGMENTS_15, amount);
    }

    public void onWorldEventSurvived(ServerPlayerEntity player) {
        increment(player, DailyMission.SURVIVE_WORLD_EVENT, 1);
    }

    public void onControlPointCaptured(ServerPlayerEntity player) {
        increment(player, DailyMission.CAPTURE_CONTROL_POINT, 1);
    }

    public void showMissions(ServerPlayerEntity player) {
        ensureMissions(player, mod.getDataManager().getRealDay());
        DataManager.DailyMissionState state = getState(player);
        player.sendMessage(TextUtil.literal("&6=== Misiones Diarias ==="), false);
        if (state == null || state.missions.isEmpty()) {
            player.sendMessage(TextUtil.literal("&7No tienes misiones activas."), false);
            return;
        }
        for (String id : state.missions) {
            DailyMission mission = DailyMission.byId(id);
            if (mission == null) continue;
            int progress = Math.max(0, state.progress.getOrDefault(id, 0));
            boolean done = state.completed.contains(id);
            String color = done ? "&a" : "&e";
            player.sendMessage(TextUtil.literal(color + mission.description + " &7[" + Math.min(progress, mission.objective) + "/" + mission.objective + "]"), false);
        }
    }

    private void increment(ServerPlayerEntity player, DailyMission mission, int amount) {
        if (!ModConfig.get().misionesDiarias.activado || amount <= 0) return;
        ensureMissions(player, mod.getDataManager().getRealDay());
        DataManager.DailyMissionState state = getState(player);
        if (state == null || !state.missions.contains(mission.id) || state.completed.contains(mission.id)) return;
        int next = state.progress.getOrDefault(mission.id, 0) + amount;
        state.progress.put(mission.id, next);
        if (next >= mission.objective) {
            completeMission(player, state, mission);
        } else {
            mod.getDataManager().setDailyMissionState(player.getUuid(), state);
        }
    }

    private void completeMission(ServerPlayerEntity player, DataManager.DailyMissionState state, DailyMission mission) {
        if (!state.completed.contains(mission.id)) {
            state.completed.add(mission.id);
        }
        mod.getRewardManager().giveFragments(player, mission.fragmentReward);
        switch (mission.extraReward) {
            case HEART_CHANCE_30 -> {
                if (player.getRandom().nextFloat() < 0.30f) mod.getRewardManager().giveHeart(player, 1);
            }
            case HEART_CHANCE_50 -> {
                if (player.getRandom().nextFloat() < 0.50f) mod.getRewardManager().giveHeart(player, 1);
            }
            case XP_BOTTLE -> mod.getRewardManager().giveXpBottles(player, 8);
            case NONE -> {
            }
        }
        mod.getDataManager().setDailyMissionState(player.getUuid(), state);
        player.sendMessage(TextUtil.literal("&a\u2714 Mision completada: &e" + mission.description + " &7+" + mission.fragmentReward + " fragmentos"), false);
    }

    private void completeNoDeathIfEligible(ServerPlayerEntity player) {
        DataManager.DailyMissionState state = getState(player);
        DailyMission mission = DailyMission.NO_DEATH_TODAY;
        if (state == null || !state.missions.contains(mission.id) || state.completed.contains(mission.id)) return;
        if (state.progress.getOrDefault(mission.id, 0) < 0) return;
        state.progress.put(mission.id, 1);
        completeMission(player, state, mission);
    }

    private void ensureMissions(ServerPlayerEntity player, int day) {
        DataManager.DailyMissionState state = getState(player);
        boolean resetByDay = ModConfig.get().misionesDiarias.resetAlCambioDia;
        if (state != null
            && (!resetByDay || state.day == day)
            && state.missions.size() >= ModConfig.get().misionesDiarias.misionesPorDia) return;
        DataManager.DailyMissionState next = new DataManager.DailyMissionState();
        next.day = day;
        List<DailyMission> pool = new ArrayList<>(List.of(DailyMission.KILL_ZOMBIES_30, DailyMission.KILL_ZOMBIES_60,
            DailyMission.SURVIVE_HORDE, DailyMission.COMPLETE_HORDE, DailyMission.EARN_FRAGMENTS_15,
            DailyMission.PLAY_TIME_30MIN, DailyMission.NO_DEATH_TODAY, DailyMission.KILL_DURING_PVP,
            DailyMission.SURVIVE_WORLD_EVENT, DailyMission.CAPTURE_CONTROL_POINT));
        Collections.shuffle(pool, new java.util.Random(player.getUuid().getMostSignificantBits() ^ day));
        int count = Math.min(ModConfig.get().misionesDiarias.misionesPorDia, pool.size());
        for (int i = 0; i < count; i++) {
            DailyMission mission = pool.get(i);
            next.missions.add(mission.id);
            next.progress.put(mission.id, mission == DailyMission.NO_DEATH_TODAY ? 0 : 0);
        }
        mod.getDataManager().setDailyMissionState(player.getUuid(), next);
        player.sendMessage(TextUtil.literal("&6Nuevas misiones diarias asignadas. &7Usa &e/mce misiones&7."), false);
    }

    private DataManager.DailyMissionState getState(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        return mod.getDataManager().getDailyMissionState(uuid);
    }
}
