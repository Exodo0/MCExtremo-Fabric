package com.egologic.mcextremo.client;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.client.render.TrialBossRenderer;
import com.egologic.mcextremo.client.render.TrialGuardianSpiderRenderer;
import com.egologic.mcextremo.entity.ModEntities;
import com.egologic.mcextremo.network.VersionNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

public class MCExtremoClient implements ClientModInitializer {
    private static final int VERSION_HANDSHAKE_RETRY_TICKS = 200;

    private boolean blockedSingleplayer;
    private int versionHandshakeTicks;
    private boolean versionHandshakeSent;

    @Override
    public void onInitializeClient() {
        SkillTreeClientNetworking.register();
        TrialCinematicClientNetworking.register();
        TrialCinematicController.register();
        TrialVisualClientController.register();
        EntityRendererRegistry.register(ModEntities.TRIAL_BOSS, TrialBossRenderer::new);
        EntityRendererRegistry.register(ModEntities.TRIAL_GUARDIAN_SPIDER, TrialGuardianSpiderRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(VersionNetworking.VERSION_MATCH,
            (client, handler, buf, responseSender) -> {
            });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            versionHandshakeTicks = VERSION_HANDSHAKE_RETRY_TICKS;
            versionHandshakeSent = false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            trySendVersionHandshake();
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

    private void trySendVersionHandshake() {
        if (versionHandshakeSent || versionHandshakeTicks <= 0) return;
        versionHandshakeTicks--;
        if (!ClientPlayNetworking.canSend(VersionNetworking.CLIENT_VERSION)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(VersionNetworking.currentVersionOrUnknown());
        ClientPlayNetworking.send(VersionNetworking.CLIENT_VERSION, buf);
        versionHandshakeSent = true;
    }
}
