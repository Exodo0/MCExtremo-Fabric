package com.tuservidor.mcextremo.listener;

import com.tuservidor.mcextremo.MCExtremo;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class JoinListener {

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            MCExtremo mod = MCExtremo.getInstance();
            var player = handler.getPlayer();

            if (!mod.getLivesManager().handleJoin(player)) return;
            mod.getEventTrialManager().recoverInterruptedEvent(player);
            mod.getScoreboardManager().forceUpdateScoreboard(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            MCExtremo mod = MCExtremo.getInstance();
            mod.getEventTrialManager().handleDisconnect(handler.getPlayer());
            mod.getReviveTrialManager().handleDisconnect(handler.getPlayer());
            mod.getScoreboardManager().removeScoreboard(handler.getPlayer());
            mod.getZombieHordeManager().removePlayer(handler.getPlayer());
        });
    }
}
