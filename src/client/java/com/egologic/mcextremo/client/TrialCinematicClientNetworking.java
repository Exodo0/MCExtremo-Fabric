package com.egologic.mcextremo.client;

import com.egologic.mcextremo.network.TrialCinematicNetworking;
import com.egologic.mcextremo.entity.boss.TrialBossAnimations;
import com.egologic.mcextremo.entity.boss.TrialBossEntity;
import com.egologic.mcextremo.entity.boss.TrialBossState;
import com.egologic.mcextremo.visual.TrialVisualEvent;
import net.minecraft.entity.Entity;
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
                int entityId = buf.readInt();
                String animation = buf.readString(96);
                int durationTicks = buf.readInt();
                client.execute(() -> {
                    if (client.world == null) return;
                    Entity entity = client.world.getEntityById(entityId);
                    if (entity instanceof TrialBossEntity boss) {
                        boss.playBossState(stateForAnimation(animation), durationTicks);
                    }
                });
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

    private static TrialBossState stateForAnimation(String animation) {
        return switch (animation) {
            case TrialBossAnimations.SPAWN_INTRO -> TrialBossState.SPAWNING;
            case TrialBossAnimations.ROAR -> TrialBossState.ROARING;
            case TrialBossAnimations.BASIC_ATTACK -> TrialBossState.ATTACKING;
            case TrialBossAnimations.SPECIAL_ATTACK -> TrialBossState.SPECIAL_ATTACKING;
            case TrialBossAnimations.SUMMON_MINIONS -> TrialBossState.SUMMONING;
            case TrialBossAnimations.PHASE_TRANSITION -> TrialBossState.PHASE_TRANSITION;
            case TrialBossAnimations.STUNNED -> TrialBossState.STUNNED;
            case TrialBossAnimations.DEATH -> TrialBossState.DYING;
            case TrialBossAnimations.RUN -> TrialBossState.CHASING;
            default -> TrialBossState.IDLE;
        };
    }
}
