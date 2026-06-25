package com.tuservidor.mcextremo.manager;

import com.tuservidor.mcextremo.MCExtremo;
import com.tuservidor.mcextremo.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

public class HardcoreManager {
    private final MCExtremo mod;
    private boolean esHardcore = false;

    public HardcoreManager(MCExtremo mod) {
        this.mod = mod;
    }

    public void apply(MinecraftServer server) {
        apply(server, false);
    }

    public void reapply(MinecraftServer server) {
        apply(server, true);
    }

    private void apply(MinecraftServer server, boolean force) {
        ModConfig.Hardcore config = ModConfig.get().hardcore;
        if (!config.activado) return;

        if (config.primeraEjecucion) {
            if (!force && mod.getDataManager().isHardcoreConfigured()) {
                MCExtremo.LOGGER.info("Hardcore ya configurado previamente.");
                esHardcore = true;
                return;
            }
            if (!mod.getDataManager().isHardcoreConfigured()) {
                mod.getDataManager().setHardcoreConfigured(true);
            }
        }

        Difficulty difficulty = switch (config.difficulty.toUpperCase()) {
            case "PEACEFUL" -> Difficulty.PEACEFUL;
            case "EASY" -> Difficulty.EASY;
            case "NORMAL" -> Difficulty.NORMAL;
            default -> Difficulty.HARD;
        };

        for (var world : server.getWorlds()) {
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(config.doDaylightCycle, server);
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(config.doWeatherCycle, server);
            world.getGameRules().get(GameRules.KEEP_INVENTORY).set(config.keepInventory, server);
        }

        server.setDifficulty(difficulty, true);

        GameMode gamemode = switch (config.gamemode.toLowerCase()) {
            case "creative" -> GameMode.CREATIVE;
            case "adventure" -> GameMode.ADVENTURE;
            case "spectator" -> GameMode.SPECTATOR;
            default -> GameMode.SURVIVAL;
        };
        server.setDefaultGameMode(gamemode);

        mod.getPvpScheduler().forzarEstado(server, config.pvp);

        esHardcore = true;
        MCExtremo.LOGGER.info("=== MODO HARDCORE ACTIVADO ===");
        MCExtremo.LOGGER.info("Dificultad: " + config.difficulty);
        MCExtremo.LOGGER.info("PvP: " + config.pvp);
        MCExtremo.LOGGER.info("Gamemode: " + config.gamemode);

        for (var world : server.getWorlds()) {
            MCExtremo.LOGGER.info("Mundo '" + world.getRegistryKey().getValue() + "': difficulty=" +
                world.getDifficulty());
        }
    }

    public boolean isEsHardcore() {
        return esHardcore;
    }
}
