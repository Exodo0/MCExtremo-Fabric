package com.egologic.mcextremo.client;

import com.egologic.mcextremo.network.TrialCinematicNetworking;
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
                String title = buf.readString();
                client.execute(() -> TrialCinematicController.startBossIntro(entityId, fallbackPos, durationTicks, title));
            });
        ClientPlayNetworking.registerGlobalReceiver(TrialCinematicNetworking.EVENT_INTRO,
            (client, handler, buf, responseSender) -> {
                Vec3d center = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
                int durationTicks = buf.readInt();
                client.execute(() -> TrialCinematicController.startEventIntro(center, durationTicks));
            });
        ClientPlayNetworking.registerGlobalReceiver(TrialCinematicNetworking.STOP,
            (client, handler, buf, responseSender) ->
                client.execute(TrialCinematicController::stopAll));
    }
}
