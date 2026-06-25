package com.tuservidor.mcextremo.listener;

import com.tuservidor.mcextremo.network.SkillTreeNetworking;
import com.tuservidor.mcextremo.util.TextUtil;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ClientRequirementListener {
    private static final int CHECK_DELAY_TICKS = 60;
    private static final Map<UUID, PendingCheck> pendingChecks = new HashMap<>();

    private record PendingCheck(ServerPlayerEntity player, int ticks) {
        PendingCheck tick() {
            return new PendingCheck(player, ticks - 1);
        }
    }

    private ClientRequirementListener() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (server.isHost(handler.getPlayer().getGameProfile())) return;
            pendingChecks.put(handler.getPlayer().getUuid(), new PendingCheck(handler.getPlayer(), CHECK_DELAY_TICKS));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            pendingChecks.remove(handler.getPlayer().getUuid()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Map.Entry<UUID, PendingCheck>> iterator = pendingChecks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, PendingCheck> entry = iterator.next();
                PendingCheck check = entry.getValue().tick();
                if (check.ticks() > 0) {
                    entry.setValue(check);
                    continue;
                }

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null && !ServerPlayNetworking.canSend(player, SkillTreeNetworking.OPEN_SKILL_TREE)) {
                    player.networkHandler.disconnect(TextUtil.literal(
                        "&cNecesitas instalar MCExtremo en tu cliente para jugar en este servidor."
                    ));
                }
                iterator.remove();
            }
        });
    }
}
