package com.egologic.mcextremo.manager;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.util.TextUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WorldEventManager {
    private static final int COUNTDOWN_TICKS = 5 * 60 * 20;

    private final MCExtremo mod;
    private WorldEventState state = WorldEventState.IDLE;
    private WorldEventType currentEvent;
    private int timerTicks;
    private int checkTicks;
    private int cooldownTicks;
    private boolean restored;
    private boolean restoredStormPending;
    private final Map<UUID, Integer> pvpKills = new HashMap<>();
    private int groupZombieKills;

    public WorldEventManager(MCExtremo mod) {
        this.mod = mod;
    }

    public void tick(MinecraftServer server) {
        if (!ModConfig.get().eventosMundo.activado) return;
        restoreIfNeeded(server);
        ServerWorld world = server.getOverworld();
        switch (state) {
            case IDLE -> tickIdle(server, world);
            case COUNTDOWN -> tickCountdown(server);
            case ACTIVE -> tickActive(server, world);
            case COOLDOWN -> {
                if (--cooldownTicks <= 0) {
                    state = WorldEventState.IDLE;
                    currentEvent = null;
                    persist();
                }
            }
        }
    }

    public boolean forceStart(MinecraftServer server, String typeName) {
        WorldEventType type = parseType(typeName);
        if (state == WorldEventState.ACTIVE || state == WorldEventState.COUNTDOWN) return false;
        if (type == null || !isAvailable(type, server.getOverworld())) return false;
        startCountdown(server, type);
        return true;
    }

    public void stop(MinecraftServer server) {
        if (state == WorldEventState.IDLE || state == WorldEventState.COOLDOWN) return;
        state = WorldEventState.COOLDOWN;
        currentEvent = null;
        cooldownTicks = 20 * 60;
        timerTicks = 0;
        pvpKills.clear();
        groupZombieKills = 0;
        restoredStormPending = false;
        persist();
        broadcast(server, "&cEvento mundial cancelado.");
    }

    public String status() {
        return currentEvent == null ? state.name() : state.name() + " - " + currentEvent.displayName + " (" + Math.max(0, timerTicks / 20) + "s)";
    }

    public String scoreboardStatus() {
        if (currentEvent == null || state == WorldEventState.IDLE) return "\u00A78Sin evento";
        return switch (state) {
            case COUNTDOWN -> "\u00A7d" + currentEvent.displayName + " \u00A77en " + Math.max(1, timerTicks / 20) + "s";
            case ACTIVE -> "\u00A75" + currentEvent.displayName;
            case COOLDOWN -> "\u00A78Cooldown";
            case IDLE -> "\u00A78Sin evento";
        };
    }

    public boolean isActive(WorldEventType type) {
        return state == WorldEventState.ACTIVE && currentEvent == type;
    }

    public void onPvpKill(ServerPlayerEntity killer) {
        if (!isActive(WorldEventType.HORA_DE_CAZA)) return;
        pvpKills.merge(killer.getUuid(), 1, Integer::sum);
        mod.getRewardManager().giveFragments(killer, 5);
    }

    public void onZombieKilled(ServerPlayerEntity killer) {
        if (!isActive(WorldEventType.LUNA_CORRUPTA)) return;
        groupZombieKills++;
        if (killer.getRandom().nextFloat() < 0.20f) {
            mod.getRewardManager().giveFragments(killer, 1);
        }
    }

    private void tickIdle(MinecraftServer server, ServerWorld world) {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }
        if (++checkTicks < ModConfig.get().eventosMundo.checkCadaMinutos * 60 * 20) return;
        checkTicks = 0;
        if (world.random.nextFloat() > ModConfig.get().eventosMundo.probabilidadEvento) return;
        List<WorldEventType> available = availableEvents(world);
        if (available.isEmpty()) return;
        startCountdown(server, available.get(world.random.nextInt(available.size())));
    }

    private void tickCountdown(MinecraftServer server) {
        timerTicks--;
        if (timerTicks % (60 * 20) == 0 || timerTicks <= 20 * 10) {
            broadcast(server, "&5Evento mundial &d" + currentEvent.displayName + " &7inicia en &e" + Math.max(1, timerTicks / 20) + "s&7.");
            persist();
        }
        if (timerTicks <= 0) startActive(server);
    }

    private void tickActive(MinecraftServer server, ServerWorld world) {
        if (restoredStormPending) {
            restoredStormPending = false;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                mod.getZombieHordeManager().forceStartHorde(player, 1.5);
            }
        }
        timerTicks--;
        if (currentEvent == WorldEventType.ECLIPSE_SANGRIENTO || currentEvent == WorldEventType.LUNA_CORRUPTA) {
            applyAtmosphere(world);
        }
        if (currentEvent == WorldEventType.ECLIPSE_SANGRIENTO && world.getTime() % (2 * 60 * 20) == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.playSound(SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 0.8f, 0.7f);
            }
        }
        if (currentEvent == WorldEventType.HORA_DE_CAZA) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 6, 0, false, true));
            }
        }
        if (timerTicks % (60 * 20) == 0) {
            persist();
        }
        if (timerTicks <= 0) finishEvent(server);
    }

    private void startCountdown(MinecraftServer server, WorldEventType type) {
        state = WorldEventState.COUNTDOWN;
        currentEvent = type;
        timerTicks = COUNTDOWN_TICKS;
        pvpKills.clear();
        groupZombieKills = 0;
        persist();
        broadcast(server, "&5Se aproxima un evento mundial: &d" + type.displayName + "&7.");
    }

    private void startActive(MinecraftServer server) {
        state = WorldEventState.ACTIVE;
        timerTicks = currentEvent.durationTicks;
        persist();
        broadcast(server, "&5&l" + currentEvent.displayName + " &7ha comenzado.");
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.0f, 0.75f);
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(TextUtil.literal("&5" + currentEvent.displayName)));
        }
        if (currentEvent == WorldEventType.TORMENTA_DE_HORDA) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                mod.getZombieHordeManager().forceStartHorde(player, 1.5);
            }
        }
    }

    private void finishEvent(MinecraftServer server) {
        if (currentEvent == WorldEventType.ECLIPSE_SANGRIENTO) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                mod.getRewardManager().giveFragments(player, 5);
                mod.getDailyMissionManager().onWorldEventSurvived(player);
            }
        } else if (currentEvent == WorldEventType.HORA_DE_CAZA && !pvpKills.isEmpty()) {
            UUID top = pvpKills.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).map(Map.Entry::getKey).orElse(null);
            if (top != null) {
                ServerPlayerEntity winner = server.getPlayerManager().getPlayer(top);
                if (winner != null) mod.getRewardManager().giveHeart(winner, 1);
            }
        } else if (currentEvent == WorldEventType.LUNA_CORRUPTA && groupZombieKills >= 75) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                mod.getRewardManager().giveFragments(player, 6);
                mod.getDailyMissionManager().onWorldEventSurvived(player);
            }
        }
        broadcast(server, "&aEvento mundial terminado: &e" + currentEvent.displayName + "&a.");
        currentEvent = null;
        state = WorldEventState.COOLDOWN;
        cooldownTicks = ModConfig.get().eventosMundo.cooldownEntreEventosMinutos * 60 * 20;
        persist();
    }

    private void applyAtmosphere(ServerWorld world) {
        if (world.getTime() % 40 != 0) return;
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            if (!(player.getWorld() instanceof ServerWorld playerWorld)) continue;
            if (currentEvent == WorldEventType.LUNA_CORRUPTA) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20 * 5, 0, false, true));
            }
            playerWorld.spawnParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, player.getX(), player.getY() + 2.2, player.getZ(), 8, 2.0, 0.3, 2.0, 0.02);
            for (HostileEntity hostile : playerWorld.getEntitiesByClass(HostileEntity.class, player.getBoundingBox().expand(32), e -> true)) {
                if (currentEvent == WorldEventType.ECLIPSE_SANGRIENTO) {
                    hostile.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 8, 0, false, true));
                    hostile.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 8, 0, false, true));
                } else if (currentEvent == WorldEventType.LUNA_CORRUPTA) {
                    hostile.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 8, 0, false, true));
                }
            }
        }
    }

    private List<WorldEventType> availableEvents(ServerWorld world) {
        List<WorldEventType> types = new ArrayList<>(EnumSet.allOf(WorldEventType.class));
        types.removeIf(type -> !isAvailable(type, world));
        return types;
    }

    private boolean isAvailable(WorldEventType type, ServerWorld world) {
        ModConfig.EventosMundo config = ModConfig.get().eventosMundo;
        boolean night = world.getTimeOfDay() % 24000L >= 13000L;
        return switch (type) {
            case ECLIPSE_SANGRIENTO -> config.eclipseActivado && night;
            case TORMENTA_DE_HORDA -> config.tormentaActivado;
            case HORA_DE_CAZA -> config.horaCazaActivado && mod.getPvpScheduler().isPvpEnabled();
            case LUNA_CORRUPTA -> config.lunaCorruptaActivado && night;
        };
    }

    private WorldEventType parseType(String typeName) {
        for (WorldEventType type : WorldEventType.values()) {
            if (type.name().equalsIgnoreCase(typeName)) return type;
        }
        return null;
    }

    private void restoreIfNeeded(MinecraftServer server) {
        if (restored) return;
        restored = true;
        DataManager.WorldEventSave save = mod.getDataManager().getWorldEvent();
        if (save == null) return;
        try {
            state = save.state == null ? WorldEventState.IDLE : WorldEventState.valueOf(save.state);
            currentEvent = save.currentEvent == null ? null : WorldEventType.valueOf(save.currentEvent);
            long remainingFromClock = save.endTime > 0L
                ? Math.max(0L, (save.endTime - System.currentTimeMillis() + 49L) / 50L)
                : save.ticksRemaining;
            int restoredTicks = (int) Math.min(Integer.MAX_VALUE, remainingFromClock);
            if (state == WorldEventState.COOLDOWN) {
                cooldownTicks = restoredTicks;
                timerTicks = 0;
            } else {
                timerTicks = restoredTicks;
                cooldownTicks = 0;
            }
            if (state != WorldEventState.IDLE && currentEvent == null) {
                if (state == WorldEventState.COOLDOWN) {
                    currentEvent = null;
                } else {
                    state = WorldEventState.IDLE;
                }
            }
            if (state == WorldEventState.ACTIVE && currentEvent == WorldEventType.TORMENTA_DE_HORDA) {
                restoredStormPending = true;
            }
            if ((state == WorldEventState.ACTIVE || state == WorldEventState.COUNTDOWN) && timerTicks <= 0) {
                state = WorldEventState.COOLDOWN;
                currentEvent = null;
                cooldownTicks = Math.max(20 * 60, ModConfig.get().eventosMundo.cooldownEntreEventosMinutos * 60 * 20);
            }
        } catch (IllegalArgumentException ignored) {
            state = WorldEventState.IDLE;
            currentEvent = null;
            timerTicks = 0;
            cooldownTicks = 0;
        }
    }

    private void persist() {
        DataManager.WorldEventSave save = new DataManager.WorldEventSave();
        save.currentEvent = currentEvent == null ? null : currentEvent.name();
        save.state = state.name();
        save.ticksRemaining = Math.max(0, state == WorldEventState.COOLDOWN ? cooldownTicks : timerTicks);
        save.startTime = System.currentTimeMillis();
        save.endTime = save.startTime + save.ticksRemaining * 50L;
        mod.getDataManager().setWorldEvent(save);
    }

    private void broadcast(MinecraftServer server, String message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(TextUtil.literal(message), false);
        }
    }
}
