package com.egologic.mcextremo.client;

import com.egologic.mcextremo.MCExtremo;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.text.Text;

public class MCExtremoClient implements ClientModInitializer {
    private boolean blockedSingleplayer;

    @Override
    public void onInitializeClient() {
        SkillTreeClientNetworking.register();
        TrialCinematicClientNetworking.register();
        TrialCinematicController.register();
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
