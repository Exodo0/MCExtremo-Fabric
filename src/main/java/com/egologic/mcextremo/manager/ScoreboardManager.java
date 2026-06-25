package com.egologic.mcextremo.manager;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreResetS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardManager {
    private static final int UPDATE_INTERVAL_TICKS = 20;
    private static final int FORCE_DISPLAY_INTERVAL_TICKS = 100;

    private final MCExtremo mod;
    private final Map<UUID, ScoreboardObjective> playerObjectives = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> playerLines = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerDisplayTicks = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    private static final String OBJ_NAME = "mcextremo";

    public ScoreboardManager(MCExtremo mod) {
        this.mod = mod;
    }

    public void tick(MinecraftServer server) {
        if (!ModConfig.get().scoreboard.activado) return;

        tickCounter++;
        if (tickCounter % UPDATE_INTERVAL_TICKS != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean forceDisplay = playerDisplayTicks.getOrDefault(player.getUuid(), 0) >= FORCE_DISPLAY_INTERVAL_TICKS;
            updateScoreboard(player, forceDisplay);
        }
    }

    public void updateScoreboard(ServerPlayerEntity player) {
        updateScoreboard(player, false);
    }

    public void forceUpdateScoreboard(ServerPlayerEntity player) {
        playerObjectives.remove(player.getUuid());
        playerLines.remove(player.getUuid());
        playerDisplayTicks.remove(player.getUuid());
        updateScoreboard(player, true);
    }

    private void updateScoreboard(ServerPlayerEntity player, boolean forceDisplay) {
        if (!ModConfig.get().scoreboard.activado) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        Scoreboard scoreboard = server.getScoreboard();

        ScoreboardObjective obj = scoreboard.getNullableObjective(OBJ_NAME);
        boolean created = obj == null;
        if (created) {
            obj = scoreboard.addObjective(
                OBJ_NAME,
                ScoreboardCriterion.DUMMY,
                Text.literal(translateColors(ModConfig.get().scoreboard.titulo)),
                ScoreboardCriterion.RenderType.INTEGER,
                false,
                BlankNumberFormat.INSTANCE
            );
        }

        String title = translateColors(ModConfig.get().scoreboard.titulo);
        obj.setDisplayName(Text.literal(title));

        int vidas = mod.getLivesManager().getVidas(player.getUuid());
        int vidasDefault = mod.getLivesManager().getDefaultLives();
        boolean pvpActivo = mod.getPvpScheduler().isPvpEnabled();
        String timer = mod.getPvpScheduler().getTiempoRestanteFormatted();
        boolean esHardcore = mod.getHardcoreManager() != null && mod.getHardcoreManager().isEsHardcore();

        String colorVidas;
        if (vidas > 2) {
            colorVidas = "\u00A7a";
        } else if (vidas == 2) {
            colorVidas = "\u00A7e";
        } else if (vidas == 1) {
            colorVidas = "\u00A76";
        } else {
            colorVidas = "\u00A7c";
        }

        String estadoPvp = pvpActivo
                ? "\u00A7aACTIVADO"
                : "\u00A7cDESACTIVADO";

        String separador = translateColors(ModConfig.get().scoreboard.lineaSeparador);
        String estadoHardcore = esHardcore
                ? "\u00A74HARDCORE"
                : "\u00A72NORMAL";

        String nombreJugador = player.getName().getString();

        List<String> lines = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        int count = 0;

        lines.add(" ");
        scores.add(8);
        count++;

        lines.add(translateColors("\u00A77\u25B8 \u00A7fJugador: \u00A7e" + nombreJugador));
        scores.add(7);
        count++;

        lines.add(separador);
        scores.add(6);
        count++;

        lines.add(translateColors("\u00A77\u25B8 \u00A7c\u2665 \u00A7fVidas: " + colorVidas + vidas + "\u00A77/\u00A7f" + vidasDefault));
        scores.add(5);
        count++;

        if (ModConfig.get().pvpProgramado.activado) {
            lines.add(translateColors("\u00A77\u25B8 \u00A79\u2694 \u00A7fPvP: " + estadoPvp));
            scores.add(4);
            count++;

            lines.add(translateColors("\u00A77\u25B8 \u00A76\u23F1 \u00A7fTimer: \u00A7e" + timer));
            scores.add(3);
            count++;
        } else {
            lines.add(translateColors("\u00A77\u25B8 \u00A79\u2694 \u00A7fPvP: \u00A7aACTIVADO"));
            scores.add(4);
            count++;
        }

        lines.add(separador + "\u00A7r");
        scores.add(2);
        count++;

        lines.add(translateColors("\u00A77\u25B8 \u00A74\u2620 \u00A7fModo: " + estadoHardcore));
        scores.add(1);
        count++;

        int displayTicks = playerDisplayTicks.getOrDefault(player.getUuid(), 0) + UPDATE_INTERVAL_TICKS;
        if (created || forceDisplay || !playerObjectives.containsKey(player.getUuid())) {
            if (!created && forceDisplay) {
                player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(obj, ScoreboardObjectiveUpdateS2CPacket.REMOVE_MODE));
            }
            player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(obj, ScoreboardObjectiveUpdateS2CPacket.ADD_MODE));
            player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, obj));
            displayTicks = 0;
        } else {
            player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(obj, ScoreboardObjectiveUpdateS2CPacket.UPDATE_MODE));
        }

        List<String> previous = playerLines.getOrDefault(player.getUuid(), List.of());
        Set<String> current = new HashSet<>(lines);
        for (String oldLine : previous) {
            if (!current.contains(oldLine)) {
                player.networkHandler.sendPacket(new ScoreboardScoreResetS2CPacket(oldLine, OBJ_NAME));
            }
        }

        for (int i = 0; i < count; i++) {
            String lineName = lines.get(i);
            if (lineName == null || lineName.isEmpty()) continue;

            player.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket(
                lineName,
                OBJ_NAME,
                scores.get(i),
                Text.literal(lineName),
                BlankNumberFormat.INSTANCE
            ));
        }

        playerObjectives.put(player.getUuid(), obj);
        playerLines.put(player.getUuid(), lines);
        playerDisplayTicks.put(player.getUuid(), displayTicks);
    }

    public void removeScoreboard(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective obj = scoreboard.getNullableObjective(OBJ_NAME);
        if (obj != null && player.networkHandler != null) {
            player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(obj, ScoreboardObjectiveUpdateS2CPacket.REMOVE_MODE));
        }
        playerObjectives.remove(player.getUuid());
        playerLines.remove(player.getUuid());
        playerDisplayTicks.remove(player.getUuid());
    }

    public void removeAll() {
        playerObjectives.clear();
        playerLines.clear();
        playerDisplayTicks.clear();
    }

    private String translateColors(String input) {
        return input
            .replace("&0", "\u00A70").replace("&1", "\u00A71").replace("&2", "\u00A72")
            .replace("&3", "\u00A73").replace("&4", "\u00A74").replace("&5", "\u00A75")
            .replace("&6", "\u00A76").replace("&7", "\u00A77").replace("&8", "\u00A78")
            .replace("&9", "\u00A79").replace("&a", "\u00A7a").replace("&b", "\u00A7b")
            .replace("&c", "\u00A7c").replace("&d", "\u00A7d").replace("&e", "\u00A7e")
            .replace("&f", "\u00A7f").replace("&l", "\u00A7l").replace("&r", "\u00A7r")
            .replace("&m", "\u00A7m");
    }
}
