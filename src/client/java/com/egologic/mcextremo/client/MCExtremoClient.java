package com.egologic.mcextremo.client;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.network.VersionNetworking;
import com.egologic.mcextremo.util.UpdateChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

public class MCExtremoClient implements ClientModInitializer {
    private boolean blockedSingleplayer;

    @Override
    public void onInitializeClient() {
        SkillTreeClientNetworking.register();
        TrialCinematicClientNetworking.register();
        TrialCinematicController.register();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!ClientPlayNetworking.canSend(VersionNetworking.CLIENT_VERSION)) return;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(UpdateChecker.currentVersion());
            ClientPlayNetworking.send(VersionNetworking.CLIENT_VERSION, buf);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (blockedSingleplayer || !client.isIntegratedServerRunning()) return;
            blockedSingleplayer = true;
            client.disconnect(new DisconnectedScreen(
                new TitleScreen(),
                Text.literal("MCExtremo requiere servidor dedicado"),
                Text.literal("Este mod solo funciona en servidores dedicados con MCExtremo instalado en servidor y cliente.")
            ));
        });
        MCExtremo.LOGGER.info("MCExtremo client initialized");
    }
}
