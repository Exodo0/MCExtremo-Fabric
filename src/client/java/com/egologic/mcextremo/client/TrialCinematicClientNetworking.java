package com.egologic.mcextremo.client;

import com.egologic.mcextremo.network.TrialCinematicNetworking;
import com.egologic.mcextremo.visual.TrialVisualEvent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.Vec3d;

public final class TrialCinematicClientNetworking {
    private TrialCinematicClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TrialCinematicNetworking.BOSS_INTRO,
            (client, handler, buf, responseSender) -> {
                int entityId = buf.readInt();
                Vec3d fallbackPos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
                int durationTicks = buf.readInt();
                String title = buf.readableBytes() > 0 ? buf.readString() : "El Coloso desciende";
                client.execute(() -> TrialCinematicController.startBossIntro(entityId, fallbackPos, durationTicks, title));
            });
        ClientPlayNetworking.registerGlobalReceiver(TrialCinematicNetworking.EVENT_INTRO,
            (client, handler, buf, responseSender) -> {
                Vec3d center = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
                int durationTicks = buf.readInt();
                String title = buf.readableBytes() > 0 ? buf.readString() : "Entrando al Event Trial";
                String subtitle = buf.readableBytes() > 0 ? buf.readString() : "La arena te reclama";
                client.execute(() -> TrialCinematicController.startEventIntro(center, durationTicks, title, subtitle));
            });
        ClientPlayNetworking.registerGlobalReceiver(TrialCinematicNetworking.STOP,
            (client, handler, buf, responseSender) ->
                client.execute(TrialCinematicController::stopAll));
        ClientPlayNetworking.registerGlobalReceiver(TrialCinematicNetworking.BOSS_ANIMATION,
            (client, handler, buf, responseSender) -> {
                buf.readInt();
                buf.readString(96);
                buf.readInt();
            });
        ClientPlayNetworking.registerGlobalReceiver(TrialCinematicNetworking.VISUAL_EVENT,
            (client, handler, buf, responseSender) -> {
                TrialVisualEvent event = buf.readEnumConstant(TrialVisualEvent.class);
                Vec3d pos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
                int durationTicks = buf.readInt();
                String title = buf.readString();
                String subtitle = buf.readString();
                client.execute(() -> TrialVisualClientController.start(event, pos, durationTicks, title, subtitle));
            });
    }
}
