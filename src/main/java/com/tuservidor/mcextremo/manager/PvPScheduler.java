package com.tuservidor.mcextremo.manager;

import com.tuservidor.mcextremo.MCExtremo;
import com.tuservidor.mcextremo.config.ModConfig;
import com.tuservidor.mcextremo.util.TextUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class PvPScheduler {
    private final MCExtremo mod;
    private boolean pvpEnabled = false;
    private int tickCounter = 0;
    private boolean running = false;

    public PvPScheduler(MCExtremo mod) {
        this.mod = mod;
    }

    public void start(MinecraftServer server) {
        this.running = true;
        this.pvpEnabled = false;
        this.tickCounter = 0;
    }

    public void stop() {
        this.running = false;
    }

    public void tick(MinecraftServer server) {
        if (!running) return;

        tickCounter++;
        ModConfig.PvPProgramado config = ModConfig.get().pvpProgramado;

        int ticksPvp = config.minutosPvp * 60 * 20;
        int ticksNoPvp = config.minutosNoPvp * 60 * 20;

        if (pvpEnabled && tickCounter >= ticksPvp) {
            pvpEnabled = false;
            tickCounter = 0;
            broadcastMessage(server, config.mensajePvpOff);
        } else if (!pvpEnabled && tickCounter >= ticksNoPvp) {
            pvpEnabled = true;
            tickCounter = 0;
            broadcastMessage(server, config.mensajePvpOn);
        }
    }

    public void forzarEstado(MinecraftServer server, boolean enabled) {
        this.pvpEnabled = enabled;
        this.tickCounter = 0;
    }

    private void broadcastMessage(MinecraftServer server, String message) {
        Text text = TextUtil.literal(message);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(text, false);
        }
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public boolean shouldCancelPvpDamage(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        return !pvpEnabled;
    }

    public int getTiempoRestante() {
        ModConfig.PvPProgramado config = ModConfig.get().pvpProgramado;
        int totalTicks = pvpEnabled ? config.minutosPvp * 60 * 20 : config.minutosNoPvp * 60 * 20;
        int remaining = totalTicks - tickCounter;
        return remaining / 20;
    }

    public String getTiempoRestanteFormatted() {
        int seconds = getTiempoRestante();
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
}
