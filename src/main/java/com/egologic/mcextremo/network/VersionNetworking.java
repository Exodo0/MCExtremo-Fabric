package com.egologic.mcextremo.network;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.listener.ClientRequirementListener;
import com.egologic.mcextremo.util.UpdateChecker;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class VersionNetworking {
    public static final String UNKNOWN_VERSION = "unknown";
    public static final Identifier CLIENT_VERSION = new Identifier(MCExtremo.MOD_ID, "client_version");
    public static final Identifier VERSION_MATCH = new Identifier(MCExtremo.MOD_ID, "version_" + networkSafeVersion(currentVersionOrUnknown()));

    private VersionNetworking() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(CLIENT_VERSION, (server, player, handler, buf, responseSender) -> {
            String clientVersion = buf.readString(32);
            server.execute(() -> ClientRequirementListener.acceptClientVersion(player, clientVersion));
        });
    }

    public static boolean canReceiveCurrentServerVersion(ServerPlayerEntity player) {
        return hasKnownCurrentVersion() && ServerPlayNetworking.canSend(player, VERSION_MATCH);
    }

    public static String currentVersionOrUnknown() {
        return normalizeVersion(UpdateChecker.currentVersion());
    }

    public static boolean isKnownVersion(String version) {
        String normalized = normalizeVersion(version);
        return !UNKNOWN_VERSION.equals(normalized);
    }

    private static boolean hasKnownCurrentVersion() {
        return isKnownVersion(UpdateChecker.currentVersion());
    }

    private static String networkSafeVersion(String version) {
        return normalizeVersion(version).replaceAll("[^a-zA-Z0-9_.-]", "_").replace('.', '_');
    }

    private static String normalizeVersion(String version) {
        if (version == null || version.isBlank()) return UNKNOWN_VERSION;
        String clean = version.trim();
        if ("null".equalsIgnoreCase(clean) || UNKNOWN_VERSION.equalsIgnoreCase(clean)) return UNKNOWN_VERSION;
        return clean;
    }
}
