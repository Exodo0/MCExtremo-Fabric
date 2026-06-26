package com.egologic.mcextremo.network;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.listener.ClientRequirementListener;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

public final class VersionNetworking {
    public static final Identifier CLIENT_VERSION = new Identifier(MCExtremo.MOD_ID, "client_version");

    private VersionNetworking() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(CLIENT_VERSION, (server, player, handler, buf, responseSender) -> {
            String clientVersion = buf.readString(32);
            server.execute(() -> ClientRequirementListener.acceptClientVersion(player, clientVersion));
        });
    }
}
