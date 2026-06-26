package com.egologic.mcextremo.network;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.listener.ClientRequirementListener;
import com.egologic.mcextremo.util.UpdateChecker;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class VersionNetworking {
    public static final Identifier CLIENT_VERSION = new Identifier(MCExtremo.MOD_ID, "client_version");
    public static final Identifier VERSION_MATCH = new Identifier(MCExtremo.MOD_ID, "version_" + networkSafeVersion(UpdateChecker.currentVersion()));

    private VersionNetworking() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(CLIENT_VERSION, (server, player, handler, buf, responseSender) -> {
            String clientVersion = buf.readString(32);
            server.execute(() -> ClientRequirementListener.acceptClientVersion(player, clientVersion));
        });
    }

    public static boolean canReceiveCurrentServerVersion(ServerPlayerEntity player) {
        return ServerPlayNetworking.canSend(player, VERSION_MATCH);
    }

    private static String networkSafeVersion(String version) {
        return version == null ? "unknown" : version.trim().replaceAll("[^a-zA-Z0-9_.-]", "_").replace('.', '_');
    }
}
