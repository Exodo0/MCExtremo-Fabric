package com.egologic.mcextremo.listener;

import com.egologic.mcextremo.network.SkillTreeNetworking;
import com.egologic.mcextremo.network.VersionNetworking;
import com.egologic.mcextremo.util.TextUtil;
import com.egologic.mcextremo.util.UpdateChecker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientRequirementListener {
    private static final int CHECK_DELAY_TICKS = 200;
    private static final Map<UUID, PendingCheck> pendingChecks = new ConcurrentHashMap<>();

    private record PendingCheck(ServerPlayerEntity player, int ticks, String clientVersion) {
        PendingCheck tick() {
            return new PendingCheck(player, ticks - 1, clientVersion);
        }

        PendingCheck withVersion(String version) {
            return new PendingCheck(player, ticks, version);
        }
    }

    private ClientRequirementListener() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (server.isHost(handler.getPlayer().getGameProfile())) return;
            pendingChecks.put(handler.getPlayer().getUuid(), new PendingCheck(handler.getPlayer(), CHECK_DELAY_TICKS, null));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            pendingChecks.remove(handler.getPlayer().getUuid()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Map.Entry<UUID, PendingCheck> entry : pendingChecks.entrySet()) {
                PendingCheck check = entry.getValue().tick();
                if (check.ticks() > 0) {
                    pendingChecks.put(entry.getKey(), check);
                    continue;
                }

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null) {
                    if (!ServerPlayNetworking.canSend(player, SkillTreeNetworking.OPEN_SKILL_TREE)) {
                        player.networkHandler.disconnect(TextUtil.literal(
                            "&cNecesitas instalar MCExtremo en tu cliente para jugar en este servidor."
                        ));
                    } else if (VersionNetworking.canReceiveCurrentServerVersion(player)) {
                        pendingChecks.remove(entry.getKey());
                        continue;
                    } else if (check.clientVersion() == null) {
                        player.networkHandler.disconnect(versionMismatchMessage(VersionNetworking.currentVersionOrUnknown()));
                    } else if (!isCompatible(player, check.clientVersion())) {
                        // isCompatible already disconnects with the exact reason.
                    }
                }
                pendingChecks.remove(entry.getKey());
            }
        });
    }

    public static void acceptClientVersion(ServerPlayerEntity player, String clientVersion) {
        PendingCheck check = pendingChecks.get(player.getUuid());
        if (check == null) {
            if (!isCompatible(player, clientVersion)) return;
            return;
        }
        if (!isCompatible(player, clientVersion)) {
            pendingChecks.remove(player.getUuid());
            return;
        }
        pendingChecks.remove(player.getUuid());
    }

    private static boolean isCompatible(ServerPlayerEntity player, String clientVersion) {
        String serverVersion = VersionNetworking.currentVersionOrUnknown();
        String safeClientVersion = clientVersion == null || clientVersion.isBlank() ? VersionNetworking.UNKNOWN_VERSION : clientVersion.trim();
        if (!VersionNetworking.isKnownVersion(serverVersion) || !VersionNetworking.isKnownVersion(safeClientVersion)) {
            player.networkHandler.disconnect(versionUnknownMessage(serverVersion, safeClientVersion));
            return false;
        }
        int compare = UpdateChecker.compareVersions(safeClientVersion, serverVersion);
        if (compare == 0) {
            return true;
        }
        if (compare > 0) {
            player.networkHandler.disconnect(serverOutdatedMessage(serverVersion, safeClientVersion));
        } else {
            player.networkHandler.disconnect(clientOutdatedMessage(serverVersion, safeClientVersion));
        }
        return false;
    }

    private static Text clientOutdatedMessage(String serverVersion, String clientVersion) {
        return TextUtil.literal("&cTu mod de cliente no esta actualizado.\n"
            + "&7Servidor: &ev" + serverVersion + " &8| &7Tu cliente: &ev" + clientVersion + "\n"
            + "&7Actualiza MCExtremo en tu carpeta de mods para poder unirte.");
    }

    private static Text serverOutdatedMessage(String serverVersion, String clientVersion) {
        return TextUtil.literal("&cEl servidor esta desactualizado.\n"
            + "&7Servidor: &ev" + serverVersion + " &8| &7Tu cliente: &ev" + clientVersion + "\n"
            + "&7Pide a un admin actualizar MCExtremo en el servidor.");
    }

    private static Text versionMismatchMessage(String serverVersion) {
        return TextUtil.literal("&cVersion incompatible de MCExtremo.\n"
            + "&7Servidor: &ev" + serverVersion + "\n"
            + "&7Instala exactamente la misma version del mod en cliente y servidor.");
    }

    private static Text versionUnknownMessage(String serverVersion, String clientVersion) {
        return TextUtil.literal("&cNo se pudo verificar la version de MCExtremo.\n"
            + "&7Servidor: &ev" + serverVersion + " &8| &7Tu cliente: &ev" + clientVersion + "\n"
            + "&7Instala el jar oficial de MCExtremo en cliente y servidor.");
    }
}
